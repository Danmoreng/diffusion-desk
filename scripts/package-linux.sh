#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_APP_DIR="$REPO_ROOT/composeApp"
GRADLE_WRAPPER="$REPO_ROOT/gradlew"
PACKAGE_NAME="diffusion-desk"
PACKAGE_VERSION="${APP_VERSION:-1.0.0}"
SKIP_NATIVE_BUILD=0
CLEAN=0
BUILD_DEB=0
REQUIRE_DEB=0
GRADLE_RETRIES=3

usage() {
    cat <<EOF
Usage: $0 [options]

Build a Linux portable Compose app package for Diffusion Desk.

Options:
  --skip-native-build  Reuse existing native binaries under build/bin.
  --clean              Clean native build directories before building.
  --build-deb          Also ask Compose/JPackage to create a .deb package.
  --require-deb        Fail if .deb packaging fails.
  --gradle-retries N   Retry Gradle tasks N times (default: 3).
  -h, --help           Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-native-build)
            SKIP_NATIVE_BUILD=1
            ;;
        --clean)
            CLEAN=1
            ;;
        --build-deb)
            BUILD_DEB=1
            ;;
        --require-deb)
            BUILD_DEB=1
            REQUIRE_DEB=1
            ;;
        --gradle-retries)
            shift
            if [[ $# -eq 0 || ! "$1" =~ ^[0-9]+$ ]]; then
                echo "--gradle-retries requires a numeric value." >&2
                exit 2
            fi
            GRADLE_RETRIES="$1"
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

if [[ "$(uname -s)" != "Linux" ]]; then
    echo "Linux packages must be built on Linux." >&2
    exit 1
fi

if [[ ! -f "$GRADLE_WRAPPER" ]]; then
    echo "Gradle wrapper not found at $GRADLE_WRAPPER" >&2
    exit 1
fi

run_gradle() {
    local attempt=1
    local max_attempts="$GRADLE_RETRIES"
    while [[ "$attempt" -le "$max_attempts" ]]; do
        echo "Running Gradle (attempt $attempt/$max_attempts): $*"
        if (cd "$REPO_ROOT" && bash "$GRADLE_WRAPPER" \
            -Dorg.gradle.internal.http.connectionTimeout=120000 \
            -Dorg.gradle.internal.http.socketTimeout=180000 \
            "$@"); then
            return 0
        fi

        if [[ "$attempt" -lt "$max_attempts" ]]; then
            sleep $((5 * attempt))
        fi
        attempt=$((attempt + 1))
    done
    return 1
}

find_jpackage() {
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage" ]]; then
        echo "$JAVA_HOME/bin/jpackage"
        return 0
    fi
    if command -v jpackage >/dev/null 2>&1; then
        command -v jpackage
        return 0
    fi
    return 1
}

if [[ "$SKIP_NATIVE_BUILD" -eq 0 ]]; then
    echo "Step 1/4: Build native backend..."
    build_args=(--skip-webui)
    if [[ "$CLEAN" -eq 1 ]]; then
        build_args+=(--clean)
    fi
    bash "$SCRIPT_DIR/build.sh" "${build_args[@]}"
else
    echo "Step 1/4: Skipping native build."
fi

BACKEND_BIN="$REPO_ROOT/build/bin"
for required in diffusion_desk_sd_worker diffusion_desk_llm_worker; do
    if [[ ! -f "$BACKEND_BIN/$required" ]]; then
        echo "Missing $required under $BACKEND_BIN. Run scripts/build.sh first or omit --skip-native-build." >&2
        exit 1
    fi
done

echo "Step 2/4: Build Compose portable app image..."
run_gradle ":composeApp:createDistributable"

APP_ROOT="$COMPOSE_APP_DIR/build/compose/binaries/main/app/$PACKAGE_NAME"
if [[ ! -d "$APP_ROOT" ]]; then
    echo "Could not find packaged app root: $APP_ROOT" >&2
    exit 1
fi

echo "Step 3/4: Copy backend runtime into packaged app..."
TARGET_BIN="$APP_ROOT/build/bin"
mkdir -p "$TARGET_BIN"

while IFS= read -r -d '' file; do
    cp -a "$file" "$TARGET_BIN/"
done < <(find "$BACKEND_BIN" -maxdepth 1 -type f \( \
    -name 'diffusion_desk_*' -o \
    -name '*.so' -o \
    -name '*.so.*' \
\) -print0)

chmod +x "$TARGET_BIN"/diffusion_desk_* 2>/dev/null || true

mkdir -p "$APP_ROOT/models" "$APP_ROOT/outputs"
if [[ -f "$REPO_ROOT/config.default.json" ]]; then
    cp "$REPO_ROOT/config.default.json" "$APP_ROOT/config.default.json"
fi

echo "Step 4/4: Create portable tarball..."
PORTABLE_DIR="$COMPOSE_APP_DIR/build/compose/binaries/main/portable"
mkdir -p "$PORTABLE_DIR"
TAR_PATH="$PORTABLE_DIR/$PACKAGE_NAME-linux-portable.tar.gz"
rm -f "$TAR_PATH"
tar -C "$(dirname "$APP_ROOT")" -czf "$TAR_PATH" "$(basename "$APP_ROOT")"

if [[ "$BUILD_DEB" -eq 1 ]]; then
    echo "Building DEB package from packaged app image..."
    JPACKAGE_BIN="$(find_jpackage || true)"
    if [[ -z "$JPACKAGE_BIN" ]]; then
        if [[ "$REQUIRE_DEB" -eq 1 ]]; then
            echo "No jpackage executable found. Set JAVA_HOME to a full Java 25+ JDK/JBR." >&2
            exit 1
        fi
        echo "Warning: no jpackage executable found. Portable tarball is still usable." >&2
    else
        DEB_DIR="$COMPOSE_APP_DIR/build/compose/binaries/main/deb"
        mkdir -p "$DEB_DIR"
        rm -f "$DEB_DIR"/*.deb
        if ! "$JPACKAGE_BIN" \
            --type deb \
            --name "$PACKAGE_NAME" \
            --app-image "$APP_ROOT" \
            --dest "$DEB_DIR" \
            --app-version "$PACKAGE_VERSION" \
            --linux-shortcut; then
            if [[ "$REQUIRE_DEB" -eq 1 ]]; then
                echo "DEB packaging failed." >&2
                exit 1
            fi
            echo "Warning: DEB packaging failed. Portable tarball is still usable." >&2
        fi
    fi
fi

echo "Packaging complete."
echo "Portable app folder: $APP_ROOT"
echo "Portable tarball: $TAR_PATH"
if [[ "$BUILD_DEB" -eq 1 ]]; then
    echo "DEB output is under: $COMPOSE_APP_DIR/build/compose/binaries/main/deb"
fi
