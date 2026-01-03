#!/bin/bash
set -e

# Test Script for Grey Image/VAE Failure Retry Logic

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
BUILD_DIR="$PROJECT_ROOT/build"
SERVER_EXE="$BUILD_DIR/bin/mysti_server"

# --- Configuration ---
MODEL_BASE="/home/sebastian/Development/models" 
LLM_PATH="$MODEL_BASE/llm/Ministral-3-3B-Instruct-2512-Q8_0.gguf"
SD_PATH="$MODEL_BASE/stable-diffusion/z_image_turbo-Q8_0.gguf" 

PORT=5566
URL="http://127.0.0.1:$PORT"
LOG_OUT="$SCRIPT_DIR/test_grey_server.out.log"
LOG_ERR="$SCRIPT_DIR/test_grey_server.err.log"

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
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

echo "Starting Server on port $PORT..."
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

echo "Server is up."

# Helper to check logs
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
        echo -e "${RED}[INFO] $NAME NOT detected (This is okay if no error occurred).${NC}"
        return 1
    fi
}

# Warmup / Wait for worker
echo "Waiting for worker to initialize (Warmup)..."
WARMUP_RETRIES=20
while [ $WARMUP_RETRIES -gt 0 ]; do
    # Try a very small generation to check if worker is listening
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/v1/images/generations" \
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
    tail -n 20 "$LOG_OUT"
    tail -n 20 "$LOG_ERR"
    exit 1
fi

sleep 2

echo "Requesting Generation Loop (Stress Test with Multiple Aspect Ratios)..."

# Resolutions to test across different aspect ratios and MP targets
# Format: "Width Height Label"
RESOLUTIONS=(
    "1024 1024 1MP_Square"
    "864 1152 1MP_3:4_Portrait"
    "1152 864 1MP_4:3_Landscape"
    "832 1248 1MP_2:3_Portrait"
    "1248 832 1MP_3:2_Landscape"
    
    "1224 1632 2MP_3:4_Portrait"
    "1632 1224 2MP_4:3_Landscape"
    "1152 1728 2MP_2:3_Portrait"
    "1728 1152 2MP_3:2_Landscape"
    
    "1500 2000 3MP_3:4_Portrait"
    "2000 1500 3MP_4:3_Landscape"
    "1416 2124 3MP_2:3_Portrait"
    "2124 1416 3MP_3:2_Landscape"
)

for RES_ENTRY in "${RESOLUTIONS[@]}"; do
    W=$(echo $RES_ENTRY | cut -d' ' -f1)
    H=$(echo $RES_ENTRY | cut -d' ' -f2)
    LABEL=$(echo $RES_ENTRY | cut -d' ' -f3)
    
    echo "------------------------------------------------"
    echo "Testing: $LABEL (${W}x${H})"
    
    JSON_DATA=$(cat <<EOF
{
  "prompt": "A beautiful landscape painting of a mystical forest with bioluminescent plants, cinematic lighting, high detail",
  "width": $W,
  "height": $H,
  "sample_steps": 4,
  "cfg_scale": 1.0,
  "sampling_method": "euler",
  "vae_tiling": false,
  "save_image": true,
  "n": 1,
  "no_base64": true
}
EOF
)

    START_TIME=$(date +%s)
    # Using a long timeout for 3MP images
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/v1/images/generations" \
        -H "Content-Type: application/json" \
        -d "$JSON_DATA" \
        --max-time 900)
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    if [ "$HTTP_CODE" -eq 200 ]; then
        echo -e "${GREEN}[SUCCESS] $LABEL completed in ${DURATION}s.${NC}"
    else
        echo -e "${RED}[FAILURE] $LABEL failed with status $HTTP_CODE${NC}"
        # Print relevant log lines for failure
        grep -E "ResourceManager|ERROR|WARN" "$LOG_OUT" | tail -n 10
    fi
    
    sleep 3
done

echo -e "\n--- Log Analysis (Cumulative) ---"
check_log_for "Retrying with VAE tiling" "Retry Mechanism Triggered"
check_log_for "Flat color detected" "Flat Grey Image Detection"

echo "Test Complete."
