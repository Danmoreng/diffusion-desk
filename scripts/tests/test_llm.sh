#!/bin/bash

# Test LLM Worker: Chat Completion
# Automates the verification of the LLM generation pipeline.

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
RUN_SCRIPT="$PROJECT_ROOT/scripts/run.sh"

# cleanup function
cleanup() {
    echo "Stopping Server..."
    # Kill the background run script process group
    if [ ! -z "$SERVER_PID" ]; then
        kill -SIGTERM -$SERVER_PID 2>/dev/null || true
    fi
    # Also explicitly kill binaries in case they detached or were spawned independently
    pkill -f "diffusion_desk_server" || true
    pkill -f "diffusion_desk_llm_worker" || true
    pkill -f "diffusion_desk_sd_worker" || true
}
trap cleanup EXIT

echo "Starting Server (LLM Only)..."
# Run in background, redirect to log for debugging
SERVER_LOG="$PROJECT_ROOT/server_test.log"
SERVER_EXE="$PROJECT_ROOT/build/bin/diffusion_desk_server"
MODEL_BASE="/home/sebastian/Development/models"
LLM_PATH="$MODEL_BASE/llm/Ministral-3-3B-Instruct-2512-Q8_0.gguf"

if [ ! -f "$SERVER_EXE" ]; then
    echo "Server executable not found at $SERVER_EXE"
    exit 1
fi

setsid "$SERVER_EXE" \
    --model-dir "$MODEL_BASE" \
    --llm-model "$LLM_PATH" \
    --listen-port 1234 \
    --verbose > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!

# Wait for Orchestrator
MAX_RETRIES=60
ORCHESTRATOR_URL="http://localhost:1234"
SERVER_READY=false

echo "Waiting for Orchestrator on $ORCHESTRATOR_URL..."
for ((i=0; i<MAX_RETRIES; i++)); do
    if curl -s "$ORCHESTRATOR_URL/health" | grep -q "ok"; then
        SERVER_READY=true
        echo "Orchestrator is UP."
        break
    fi
    sleep 1
done

if [ "$SERVER_READY" = false ]; then
    echo "Orchestrator failed to start. Check $SERVER_LOG"
    cat "$SERVER_LOG"
    exit 1
fi

# Wait for LLM Worker (Model Load)
echo "Waiting for LLM Worker (Model Load)..."
MODEL_READY=false
# Give it more time for LLM load (can be large)
for ((i=0; i<120; i++)); do
    # Check /v1/models for the LLM model
    RESPONSE=$(curl -s "$ORCHESTRATOR_URL/v1/models")
    
    # Check if the specific LLM model is present and active
    # We look for "Ministral" which is part of the LLM filename in run.sh
    if echo "$RESPONSE" | grep -i "Ministral" | grep -q '"active":true'; then
        MODEL_READY=true
        echo "LLM Model Loaded."
        break
    fi
    
    echo -n "."
    sleep 1
done
echo ""

if [ "$MODEL_READY" = false ]; then
    echo "LLM Worker failed to load model (or timed out)."
    echo "Last /v1/models response: $RESPONSE"
    echo "Server Log:"
    tail -n 50 "$SERVER_LOG"
    exit 1
fi

# Generate Text
echo "Testing Chat Completion..."
PAYLOAD='{
  "model": "gpt-3.5-turbo",
  "messages": [
    {"role": "user", "content": "What is 2+2? Answer briefly."}
  ],
  "temperature": 0.7
}'

RESPONSE_FILE=$(mktemp)
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w "%{http_code}" -X POST "$ORCHESTRATOR_URL/v1/chat/completions" \
     -H "Content-Type: application/json" \
     -d "$PAYLOAD")

echo "HTTP Status: $HTTP_CODE"
cat "$RESPONSE_FILE"
echo ""

if [ "$HTTP_CODE" -eq 200 ]; then
    # Check if response contains "choices"
    if grep -q "choices" "$RESPONSE_FILE"; then
        echo "✅ Chat Completion Success!"
    else
        echo "❌ Chat Completion Failed (Invalid Response)."
        exit 1
    fi
else
    echo "❌ Chat Completion Request Failed."
    exit 1
fi

rm "$RESPONSE_FILE"
