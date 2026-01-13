#!/bin/bash
set -e

# Test Script for VRAM Arbitration Logic (Linux/Bash Port)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
BUILD_DIR="$PROJECT_ROOT/build"
SERVER_EXE="$BUILD_DIR/bin/diffusion_desk_server"

# --- Configuration ---
# Matching scripts/run.sh defaults
MODEL_BASE="/home/sebastian/Development/models" 
# Use the models defined in run.sh if available, otherwise these are placeholders
# Note: The test requires specific models or at least *some* models to load.
# We will use the ones from run.sh
LLM_PATH="$MODEL_BASE/llm/Ministral-3-3B-Instruct-2512-Q8_0.gguf"
SD_PATH="$MODEL_BASE/stable-diffusion/z_image_turbo-Q8_0.gguf" 

PORT=5555
URL="http://127.0.0.1:$PORT"
LOG_OUT="$SCRIPT_DIR/test_vram_server.out.log"
LOG_ERR="$SCRIPT_DIR/test_vram_server.err.log"

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo "Build dir: $BUILD_DIR"
echo "Server exe: $SERVER_EXE"

if [ ! -f "$SERVER_EXE" ]; then
    echo -e "${RED}Error: diffusion_desk_server not found at $SERVER_EXE${NC}"
    exit 1
fi

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    pkill -f "diffusion_desk_server" || true
    pkill -f "diffusion_desk_sd_worker" || true
    pkill -f "diffusion_desk_llm_worker" || true
}

# Trap cleanup on exit
trap cleanup EXIT

cleanup
sleep 2

rm -f "$LOG_OUT" "$LOG_ERR"
rm -f "$PROJECT_ROOT/sd_worker.log" "$PROJECT_ROOT/llm_worker.log"

echo "Starting Server on port $PORT..."
# Run from project root
cd "$PROJECT_ROOT"

"$SERVER_EXE" \
    --model-dir "$MODEL_BASE" \
    --diffusion-model "$SD_PATH" \
    --llm-model "$LLM_PATH" \
    --listen-port "$PORT" \
    --verbose > "$LOG_OUT" 2> "$LOG_ERR" &

SERVER_PID=$!

echo "Waiting for server health..."
RETRIES=30
HEALTHY=false

while [ $RETRIES -gt 0 ]; do
    if curl -s "$URL/health" | grep -q "ok"; then
        HEALTHY=true
        break
    fi
    sleep 1
    RETRIES=$((RETRIES-1))
done

if [ "$HEALTHY" = false ]; then
    echo -e "${RED}Server failed to start.${NC}"
    echo "Tail of stdout:"
    tail -n 20 "$LOG_OUT"
    echo "Tail of stderr:"
    tail -n 20 "$LOG_ERR"
    exit 1
fi

echo "Server is up."

# Helper functions
check_log_for() {
    PATTERN=$1
    NAME=$2
    FOUND=false
    
    if grep -q "$PATTERN" "$LOG_OUT"; then FOUND=true; fi
    if [ "$FOUND" = false ] && grep -q "$PATTERN" "$LOG_ERR"; then FOUND=true; fi
    
    if [ "$FOUND" = true ]; then
        echo -e "${GREEN}[PASS] $NAME detected.${NC}"
        return 0
    else
        echo -e "${RED}[FAIL] $NAME NOT detected.${NC}"
        return 1
    fi
}

generate_image() {
    WIDTH=$1
    HEIGHT=$2
    NAME=$3
    
    echo "Requesting Generation: $NAME (${WIDTH}x${HEIGHT})..."
    
    # Construct JSON payload
    JSON_DATA=$(cat <<EOF
{
  "prompt": "test image",
  "width": $WIDTH,
  "height": $HEIGHT,
  "sample_steps": 1,
  "n": 1,
  "no_base64": true
}
EOF
)

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/v1/images/generations" \
        -H "Content-Type: application/json" \
        -d "$JSON_DATA" \
        --max-time 300)

    if [ "$HTTP_CODE" -eq 200 ]; then
        echo -e "${GREEN}Generation '$NAME' completed successfully.${NC}"
        return 0
    else
        echo -e "${RED}Generation failed with status $HTTP_CODE${NC}"
        return 1
    fi
}

# 0. Wakeup
echo "Warming up worker connection..."
WARMUP_RETRIES=10
while [ $WARMUP_RETRIES -gt 0 ]; do
    if generate_image 64 64 "Warmup"; then
        break
    fi
    echo "Worker not ready, retrying..."
    sleep 2
    WARMUP_RETRIES=$((WARMUP_RETRIES-1))
done

# 1. Baseline
generate_image 512 512 "Baseline (Small)"
sleep 2

# 2. Large Generation
echo "Checking Medium/Large Load..."
generate_image 1280 1280 "Large (Potential LLM Unload)"
sleep 5

# 3. Very Large Generation
echo "Checking Huge Load..."
generate_image 1408 1408 "Huge (Trigger Offload/Tiling/Unload)"
sleep 5

echo -e "\n--- Final Verification ---"
check_log_for "Requesting LLM unload" "LLM Unload (At any stage)" || true
check_log_for "Recommending CLIP offload" "CLIP Offload" || true
check_log_for "Recommending VAE tiling" "VAE Tiling" || true

echo "Test Complete."
