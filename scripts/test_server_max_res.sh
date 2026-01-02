#!/bin/bash
set -e

# Test Server Max Resolution
# Goal: Verify if mysti_server can generate 2880x1920 (5.52 MP)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/build"
SERVER_EXE="$BUILD_DIR/bin/mysti_server"

# --- Configuration ---
MODEL_BASE="/home/sebastian/Development/models"
LLM_PATH="$MODEL_BASE/text-encoder/Qwen3-4B-Instruct-2507-Q8_0.gguf"
SD_PATH="$MODEL_BASE/stable-diffusion/z_image_turbo-Q8_0.gguf"

PORT=5588
URL="http://127.0.0.1:$PORT"
LOG_OUT="$SCRIPT_DIR/test_server_max_res.out.log"
LOG_ERR="$SCRIPT_DIR/test_server_max_res.err.log"

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

cleanup() {
    echo "Cleaning up..."
    pkill -f "mysti_server" || true
    pkill -f "mysti_sd_worker" || true
    pkill -f "mysti_llm_worker" || true
}

trap cleanup EXIT
cleanup
sleep 2

rm -f "$LOG_OUT" "$LOG_ERR"

echo -e "${BLUE}Starting Mysti Server on port $PORT...${NC}"
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
    tail -n 20 "$LOG_OUT"
    tail -n 20 "$LOG_ERR"
    exit 1
fi

echo -e "${GREEN}Server is up.${NC}"

# Warmup
echo "Waiting for worker to initialize (Warmup)..."
WARMUP_RETRIES=20
while [ $WARMUP_RETRIES -gt 0 ]; do
        HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL/v1/images/generations" \
        -H "Content-Type: application/json" \
        -d '{"prompt":"warmup","width":64,"height":64,"sample_steps":1,"n":1,"no_base64":true}' \
        --max-time 10)
    
    if [ "$HTTP_CODE" -eq 200 ]; then
        echo "Worker is ready."
        break
    else
        echo "Worker not ready yet (Status: $HTTP_CODE). Retrying in 5s..."
        sleep 5
        WARMUP_RETRIES=$((WARMUP_RETRIES-1))
    fi
done

if [ $WARMUP_RETRIES -eq 0 ]; then
    echo -e "${RED}Worker failed to come online.${NC}"
    exit 1
fi

# --- Test Parameters ---
PROMPT="A nice beach from the german Nordsee in oil painting, rough waves, cloudy sky, detailed texture, cinematic lighting"
WIDTH=2880
HEIGHT=1920
STEPS=4
CFG=1.0
SEED=42
SAMPLER="euler"

echo -e "${BLUE}--- Requesting High Resolution Generation ($WIDTH x $HEIGHT) ---${NC}"

JSON_DATA=$(cat <<EOF
{
  "prompt": "$PROMPT",
  "width": $WIDTH,
  "height": $HEIGHT,
  "sample_steps": $STEPS,
  "cfg_scale": $CFG,
  "sampling_method": "$SAMPLER",
  "seed": $SEED,
  "save_image": true,
  "n": 1,
  "no_base64": false
}
EOF
)

START_TIME=$(date +%s)
# Request generation and capture response
RESPONSE=$(curl -s -X POST "$URL/v1/images/generations" \
    -H "Content-Type: application/json" \
    -d "$JSON_DATA" \
    --max-time 600)
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Extract URL from response data[0].url
IMG_URL=$(echo "$RESPONSE" | sed -n 's/.*"url": "\([^"]*\)".*/\1/p')

if [ -n "$IMG_URL" ]; then
    OUTPUT_FILE="$SCRIPT_DIR/server_max_res.png"
    echo "Downloading image from $URL$IMG_URL..."
    curl -s -o "$OUTPUT_FILE" "$URL$IMG_URL"
    echo -e "${GREEN}[SUCCESS] Max Res Generation complete in ${DURATION}s: $OUTPUT_FILE${NC}"
else
    echo -e "${RED}[FAILURE] Generation failed or no URL returned.${NC}"
    echo "Response: $RESPONSE"
    echo -e "\n--- Log Tail ---"
    tail -n 20 "$LOG_OUT"
    tail -n 20 "$LOG_ERR"
    exit 1
fi
