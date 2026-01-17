#!/bin/bash

# DiffusionDesk Launch Script for Linux

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/build"
SERVER_EXE="$BUILD_DIR/bin/diffusion_desk_server"

if [ ! -f "$SERVER_EXE" ]; then
    echo "Error: diffusion_desk_server not found at $SERVER_EXE"
    echo "Please build the project first."
    exit 1
fi

# --- Configuration ---
MODEL_BASE="$PROJECT_ROOT/models"

# Check if the model base directory exists
if [ ! -d "$MODEL_BASE" ]; then
    echo "Creating models directory at $MODEL_BASE..."
    mkdir -p "$MODEL_BASE"
fi

# Optional: Override these to load specific models on startup
# LLM_PATH="$MODEL_BASE/llm/your-model.gguf"
# SD_PATH="$MODEL_BASE/stable-diffusion/your-model.gguf" 
LLM_PATH=""
SD_PATH=""

IDLE_TIMEOUT=600  # 10 minutes

echo "Starting DiffusionDesk Server..."
echo "Executable: $SERVER_EXE"
echo "Model Directory: $MODEL_BASE"
echo "-------------------------------------------"

# Change to the project root so the server can find assets (e.g., ./public)
cd "$PROJECT_ROOT"

# Build arguments
ARGS=(
    --model-dir "$MODEL_BASE"
    --llm-idle-timeout "$IDLE_TIMEOUT"
    --listen-port 1234
    --verbose
)

if [ -n "$SD_PATH" ]; then
    ARGS+=(--diffusion-model "$SD_PATH")
fi

if [ -n "$LLM_PATH" ]; then
    ARGS+=(--llm-model "$LLM_PATH")
fi

# Execute the server
"$SERVER_EXE" "${ARGS[@]}"
