#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/build"

echo "Build script started..."

# --- WebUI Build ---
WEBUI_DIR="$PROJECT_ROOT/webui"
if [ -d "$WEBUI_DIR" ]; then
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
if [ ! -d "$BUILD_DIR" ]; then
    mkdir -p "$BUILD_DIR"
fi
cd "$BUILD_DIR"

# Force CUDA options just in case, though they are now defaults in CMakeLists.txt
cmake "$PROJECT_ROOT" \
    -DCMAKE_BUILD_TYPE=Release \
    -DGGML_CUDA=ON \
    -DSD_CUDA=ON

echo "Building project..."
# Build everything
cmake --build . --config Release --parallel $(nproc)

echo "Build complete!"
