#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/build"
SD_WORKER_BUILD_DIR="$PROJECT_ROOT/build-sd-worker"
SD_WORKER_SOURCE_DIR="$PROJECT_ROOT/cmake/sd-worker"
SKIP_WEBUI=0
CLEAN=0

usage() {
    cat <<EOF
Usage: $0 [--clean] [--skip-webui]

Builds the Diffusion Desk native backend and workers for Linux.

Options:
  --clean       Remove native build directories before configuring.
  --skip-webui  Skip the legacy Vue WebUI build.
  -h, --help    Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean)
            CLEAN=1
            ;;
        --skip-webui)
            SKIP_WEBUI=1
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
    shift
done

echo "Build script started..."

if [[ "$CLEAN" -eq 1 ]]; then
    echo "Cleaning native build directories..."
    rm -rf "$BUILD_DIR" "$SD_WORKER_BUILD_DIR"
fi

# --- WebUI Build ---
WEBUI_DIR="$PROJECT_ROOT/webui"
if [[ "$SKIP_WEBUI" -eq 1 ]]; then
    echo "Skipping legacy WebUI build."
elif [ -d "$WEBUI_DIR" ]; then
    echo "Building WebUI..."
    cd "$WEBUI_DIR"
    if [ ! -d "node_modules" ]; then
        echo "Installing NPM dependencies..."
        npm install
    fi
    echo "Compiling Vue app..."
    npm run build
else
    echo "WebUI directory not found at $WEBUI_DIR, skipping..."
fi

# --- C++ Build ---
echo "Configuring CMake..."
mkdir -p "$BUILD_DIR"

GENERATOR_ARGS=()
if command -v ninja >/dev/null 2>&1; then
    echo "Using Ninja generator..."
    GENERATOR_ARGS=(-G Ninja)
fi

cmake -S "$PROJECT_ROOT" -B "$BUILD_DIR" "${GENERATOR_ARGS[@]}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DGGML_CUDA=ON \
    -DSD_CUDA=ON \
    -DGGML_CUDA_GRAPHS=OFF \
    -DCMAKE_CXX_STANDARD=17 \
    -DSD_BUILD_EXTERNAL_GGML=ON

echo "Building Llama.cpp..."
cmake --build "$BUILD_DIR" --config Release --target llama --parallel "$(nproc)"

echo "Building Stable Diffusion..."
cmake --build "$BUILD_DIR" --config Release --target stable-diffusion --parallel "$(nproc)"

echo "Building Main Server (Orchestrator)..."
cmake --build "$BUILD_DIR" --config Release --target diffusion_desk_server --parallel "$(nproc)"

echo "Building standalone SD Worker with stable-diffusion.cpp GGML..."
cmake -S "$SD_WORKER_SOURCE_DIR" -B "$SD_WORKER_BUILD_DIR" "${GENERATOR_ARGS[@]}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DGGML_CUDA=ON \
    -DSD_CUDA=ON \
    -DGGML_CUDA_GRAPHS=OFF \
    -DCMAKE_CXX_STANDARD=17
cmake --build "$SD_WORKER_BUILD_DIR" --config Release --target diffusion_desk_sd_worker --parallel "$(nproc)"

echo "Building LLM Worker..."
cmake --build "$BUILD_DIR" --config Release --target diffusion_desk_llm_worker --parallel "$(nproc)"

echo "Build complete!"
