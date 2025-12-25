#!/bin/bash

# MystiCanvas Launch Script for Linux

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/build"
SERVER_EXE="$BUILD_DIR/bin/mysti_server"

if [ ! -f "$SERVER_EXE" ]; then
    echo "Error: mysti_server not found at $SERVER_EXE"
    echo "Please build the project first."
    exit 1
fi

# --- Configuration ---
# IMPORTANT: Replace this with the actual path to your models directory
MODEL_BASE="/home/sebastian/Development/models" 

# Check if the model base directory exists
if [ "$MODEL_BASE" == "/path/to/your/models" ] || [ ! -d "$MODEL_BASE" ]; then
    echo "Warning: Model directory not configured or does not exist."
    echo "Please edit scripts/run.sh and set the MODEL_BASE variable."
fi

LLM_PATH="$MODEL_BASE/llm/Ministral-3-3B-Instruct-2512-Q8_0.gguf"
SD_PATH="$MODEL_BASE/stable-diffusion/z_image_turbo-Q8_0.gguf" 

IDLE_TIMEOUT=600  # 10 minutes

echo "Starting MystiCanvas Server..."
echo "Executable: $SERVER_EXE"
echo "Model Directory: $MODEL_BASE"
echo "SD Model: $SD_PATH"
echo "LLM Model: $LLM_PATH"
echo "-------------------------------------------"

# Change to the project root so the server can find assets (e.g., ./public)
cd "$PROJECT_ROOT"

# Execute the server
"$SERVER_EXE" \
    --model-dir "$MODEL_BASE" \
    --diffusion-model "$SD_PATH" \
    --llm-model "$LLM_PATH" \
    --llm-idle-timeout "$IDLE_TIMEOUT" \
    --listen-port 1234 \
    --verbose
