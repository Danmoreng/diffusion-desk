#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
GRADLE_WRAPPER="$PROJECT_ROOT/gradlew"
MIN_GRADLE_JAVA_MAJOR=17
REQUIRED_COMPOSE_JAVA_MAJOR=25
JAVA_HOME_ARG=""
DRY_RUN=0

usage() {
    cat <<EOF
Usage: $0 [--java-home PATH] [--dry-run]

Run the Diffusion Desk Compose desktop application on Linux.

Options:
  --java-home PATH  Use a specific Java runtime to start Gradle.
  --dry-run         Print the resolved command without running it.
  -h, --help        Show this help.
EOF
}

java_major_from_home() {
    local java_home="$1"
    local release_file="$java_home/release"
    local java_bin="$java_home/bin/java"

    if [[ -f "$release_file" ]]; then
        local version
        version="$(sed -n 's/^JAVA_VERSION="\([0-9][0-9]*\).*/\1/p' "$release_file" | head -n 1)"
        if [[ -n "$version" ]]; then
            echo "$version"
            return 0
        fi
    fi

    if [[ -x "$java_bin" ]]; then
        "$java_bin" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1
        return 0
    fi

    return 1
}

test_java_home() {
    local java_home="${1:-}"
    local minimum_major="${2:-$MIN_GRADLE_JAVA_MAJOR}"
    if [[ -z "$java_home" || ! -x "$java_home/bin/java" ]]; then
        return 1
    fi

    local major
    major="$(java_major_from_home "$java_home" || true)"
    [[ -n "$major" && "$major" -ge "$minimum_major" ]]
}

java_home_from_path() {
    if command -v java >/dev/null 2>&1; then
        local java_path java_home_from_path
        java_path="$(readlink -f "$(command -v java)")"
        java_home_from_path="$(dirname "$(dirname "$java_path")")"
        echo "$java_home_from_path"
    fi
}

java_home_candidates() {
    printf '%s\n' "${1:-}"
    printf '%s\n' "${JAVA_HOME:-}"
    java_home_from_path

    local candidate
    for candidate in "$HOME/.gradle/jdks"/* "/opt/jbr" "/opt/jdk-25" "/usr/lib/jvm"/*; do
        printf '%s\n' "$candidate"
    done
}

resolve_java_home() {
    local requested="${1:-}"
    local candidate

    while IFS= read -r candidate; do
        if test_java_home "$candidate" "$REQUIRED_COMPOSE_JAVA_MAJOR"; then
            echo "$candidate"
            return 0
        fi
    done < <(java_home_candidates "$requested")

    if [[ -n "$requested" ]]; then
        return 1
    fi

    while IFS= read -r candidate; do
        if test_java_home "$candidate" "$MIN_GRADLE_JAVA_MAJOR"; then
            echo "$candidate"
            return 0
        fi
    done < <(java_home_candidates "$requested")

    return 2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --java-home)
            shift
            if [[ $# -eq 0 ]]; then
                echo "--java-home requires a path." >&2
                exit 2
            fi
            JAVA_HOME_ARG="$1"
            ;;
        --dry-run)
            DRY_RUN=1
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
    echo "This launcher is intended for Linux. Use scripts/run-compose.ps1 on Windows." >&2
    exit 1
fi

if [[ ! -f "$GRADLE_WRAPPER" ]]; then
    echo "Gradle wrapper not found at $GRADLE_WRAPPER" >&2
    exit 1
fi

RESOLVED_JAVA_HOME="$(resolve_java_home "$JAVA_HOME_ARG" || true)"
if [[ -z "$RESOLVED_JAVA_HOME" ]]; then
    echo "Error: Java runtime not found." >&2
    echo "Diffusion Desk Compose runs on Java $REQUIRED_COMPOSE_JAVA_MAJOR." >&2
    echo "Set JAVA_HOME or pass a JDK/JBR path with: scripts/run-compose.sh --java-home /path/to/jdk-25" >&2
    exit 1
fi

export JAVA_HOME="$RESOLVED_JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Starting Diffusion Desk Compose Desktop..."
echo "Project: $PROJECT_ROOT"
echo "Java: $JAVA_HOME"
echo "Compose Java runtime: Java $REQUIRED_COMPOSE_JAVA_MAJOR via Gradle toolchain."
echo "-------------------------------------------"

if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "Dry run: would execute bash $GRADLE_WRAPPER :composeApp:run"
    exit 0
fi

cd "$PROJECT_ROOT"
bash "$GRADLE_WRAPPER" ":composeApp:run"
