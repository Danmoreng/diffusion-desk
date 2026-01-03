#!/bin/bash
set -e

# Benchmark Maximum Resolution with sd-cli
# Goal: Find the maximum 3:2 resolution possible on this GPU with optimizations.

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
SD_CLI_EXE="/home/sebastian/Development/benchmark_sd_backends/sdcp/build/bin/sd-cli"
MODEL_BASE="/home/sebastian/Development/models"
OUTPUT_DIR="$SCRIPT_DIR/max_res_test"

# Models
DIFFUSION_MODEL="$MODEL_BASE/stable-diffusion/z_image_turbo-Q8_0.gguf"
LLM_MODEL="$MODEL_BASE/text-encoder/Qwen3-4B-Instruct-2507-Q8_0.gguf"
VAE_MODEL="$MODEL_BASE/vae/ae.safetensors"

# Prompt
PROMPT="A nice beach from the german Nordsee in oil painting, rough waves, cloudy sky, detailed texture, cinematic lighting"

# Optimizations
# --diffusion-fa: Flash Attention (Speed + Memory)
# --vae-tiling: Essential for large images (Memory)
# --clip-on-cpu: Keep Text Encoder on CPU (Save VRAM)
# --vae-on-cpu: Removed to improve speed (uses VRAM for VAE)
# --offload-to-cpu:  Offload weights to CPU when not used (Aggressive VRAM saving). 
#                    We include this to test the ABSOLUTE limit of the card's ability to compute, 
#                    trading off PCIe bandwidth for VRAM capacity.
OPTIMIZATIONS="--diffusion-fa --vae-tiling --clip-on-cpu --offload-to-cpu"

mkdir -p "$OUTPUT_DIR"

echo "Starting Maximum Resolution Benchmark..."
echo "Optimizations: $OPTIMIZATIONS"

# Start at ~2.5MP (2112x1408) to save time
H=1408
STEP=128

while true; do
    # Calculate Width (Aspect Ratio 3:2 -> W = 1.5 * H)
    W=$(( (H * 3) / 2 ))
    
    # Ensure divisible by 64 (standard for SD/GGML to avoid alignment issues)
    W=$(( (W + 63) / 64 * 64 ))
    H=$(( (H + 63) / 64 * 64 ))
    
    MP=$(echo "scale=2; ($W * $H) / 1000000" | bc)
    LABEL="${W}x${H}"
    
    echo "----------------------------------------------------------------"
echo "Testing Resolution: $LABEL (${MP} MP)"
    
    LOG_FILE="$OUTPUT_DIR/test_${W}x${H}.log"
    IMG_FILE="$OUTPUT_DIR/test_${W}x${H}.png"
    
    START_TIME=$(date +%s)
    
    set +e # Allow failure
    "$SD_CLI_EXE" \
        --diffusion-model "$DIFFUSION_MODEL" \
        --llm "$LLM_MODEL" \
        --vae "$VAE_MODEL" \
        -p "$PROMPT" \
        -W $W -H $H \
        --steps 4 \
        --cfg-scale 1.0 \
        --sampling-method euler \
        --seed 42 \
        $OPTIMIZATIONS \
        -o "$IMG_FILE" \
        -v > "$LOG_FILE" 2>&1
    
    EXIT_CODE=$?
    set -e
    
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    if [ $EXIT_CODE -eq 0 ]; then
        echo "SUCCESS: Generated $LABEL in ${DURATION}s"
    else
        echo "FAILURE: Crashed/Failed at $LABEL (Exit Code: $EXIT_CODE)"
        echo "Last 5 lines of log:"
        tail -n 5 "$LOG_FILE"
        echo "----------------------------------------------------------------"
echo "Benchmark Stopped."
        echo "Maximum Successful Resolution: Previous step"
        exit 0
    fi
    
    # Increase resolution
    H=$((H + STEP))
done
