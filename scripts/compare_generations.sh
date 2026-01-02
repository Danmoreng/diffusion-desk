#!/bin/bash
set -e

# Script to compare mysti_server vs sd-cli generation
# Goal: Reproduce failing pattern at 1152x1728

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/build"
SERVER_EXE="$BUILD_DIR/bin/mysti_server"
SD_CLI_EXE="/home/sebastian/Development/benchmark_sd_backends/sdcp/build/bin/sd-cli"

# --- Configuration ---
MODEL_BASE="/home/sebastian/Development/models"
# User specified correct LLM/Text Encoder path
LLM_PATH="$MODEL_BASE/text-encoder/Qwen3-4B-Instruct-2507-Q8_0.gguf" 
SD_PATH="$MODEL_BASE/stable-diffusion/z_image_turbo-Q8_0.gguf"
VAE_PATH="$MODEL_BASE/vae/ae.safetensors"

PORT=5577
URL="http://127.0.0.1:$PORT"
LOG_OUT="$SCRIPT_DIR/compare_server.out.log"
LOG_ERR="$SCRIPT_DIR/compare_server.err.log"
OUTPUT_DIR="$SCRIPT_DIR/comparison_results"

mkdir -p "$OUTPUT_DIR"

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

# Note: Passing VAE if supported by server args, or assuming it picks it up/defaults.
# Looking at previous scripts, --vae-model isn't explicitly passed to server, 
# but we'll assume the server handles it or uses the one in the folder if we point model-dir correctly.
# However, sd-cli definitely needs it.

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
    exit 1
fi

# --- Test Parameters ---
PROMPT="A beautiful landscape painting of a mystical forest with bioluminescent plants, cinematic lighting, high detail"
WIDTH=1152
HEIGHT=1728
STEPS=4
CFG=1.0
SEED=42
SAMPLER="euler"

echo -e "${BLUE}--- Running mysti_server generation ---${NC}"
SERVER_OUTPUT="$OUTPUT_DIR/mysti_server_output.png" 

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

# Request generation and capture response
RESPONSE=$(curl -s -X POST "$URL/v1/images/generations" \
    -H "Content-Type: application/json" \
    -d "$JSON_DATA" \
    --max-time 300)

# Extract URL from response data[0].url
IMG_URL=$(echo "$RESPONSE" | sed -n 's/.*"url": "\([^"]*\)".*/\1/p')

if [ -n "$IMG_URL" ]; then
    echo "Downloading image from $URL$IMG_URL..."
    curl -s -o "$SERVER_OUTPUT" "$URL$IMG_URL"
    echo -e "${GREEN}[SUCCESS] mysti_server generation complete: $SERVER_OUTPUT${NC}"
else
    echo -e "${RED}[FAILURE] mysti_server generation failed or no URL returned.${NC}"
    echo "Response: $RESPONSE"
    # Don't exit yet, run CLI tests for comparison
fi

# Stop server to free up resources for CLI tests
echo "Stopping server to free VRAM for CLI tests..."
cleanup
sleep 5

echo -e "${BLUE}--- Running sd-cli generations ---${NC}"

# 1. Baseline
echo "1. Baseline (Default)"
$SD_CLI_EXE \
    --diffusion-model "$SD_PATH" \
    --llm "$LLM_PATH" \
    --vae "$VAE_PATH" \
    -p "$PROMPT" \
    -W $WIDTH -H $HEIGHT \
    --steps $STEPS \
    --cfg-scale $CFG \
    --sampling-method $SAMPLER \
    --seed $SEED \
    -o "$OUTPUT_DIR/sd_cli_baseline.png" \
    -v > "$OUTPUT_DIR/sd_cli_baseline.log" 2>&1

# 2. VAE Tiling
echo "2. VAE Tiling"
$SD_CLI_EXE \
    --diffusion-model "$SD_PATH" \
    --llm "$LLM_PATH" \
    --vae "$VAE_PATH" \
    -p "$PROMPT" \
    -W $WIDTH -H $HEIGHT \
    --steps $STEPS \
    --cfg-scale $CFG \
    --sampling-method $SAMPLER \
    --seed $SEED \
    --vae-tiling \
    -o "$OUTPUT_DIR/sd_cli_vae_tiling.png" \
    -v > "$OUTPUT_DIR/sd_cli_vae_tiling.log" 2>&1

# 3. Text Encoder on CPU
echo "3. Text Encoder on CPU"
$SD_CLI_EXE \
    --diffusion-model "$SD_PATH" \
    --llm "$LLM_PATH" \
    --vae "$VAE_PATH" \
    -p "$PROMPT" \
    -W $WIDTH -H $HEIGHT \
    --steps $STEPS \
    --cfg-scale $CFG \
    --sampling-method $SAMPLER \
    --seed $SEED \
    --clip-on-cpu \
    -o "$OUTPUT_DIR/sd_cli_cpu_enc.png" \
    -v > "$OUTPUT_DIR/sd_cli_cpu_enc.log" 2>&1

# 4. Offload to CPU (Force VRAM management)
echo "4. Offload to CPU"
$SD_CLI_EXE \
    --diffusion-model "$SD_PATH" \
    --llm "$LLM_PATH" \
    --vae "$VAE_PATH" \
    -p "$PROMPT" \
    -W $WIDTH -H $HEIGHT \
    --steps $STEPS \
    --cfg-scale $CFG \
    --sampling-method $SAMPLER \
    --seed $SEED \
    --offload-to-cpu \
    -o "$OUTPUT_DIR/sd_cli_offload.png" \
    -v > "$OUTPUT_DIR/sd_cli_offload.log" 2>&1

echo -e "\n${BLUE}Comparison Finished.${NC}"
ls -l "$OUTPUT_DIR"
