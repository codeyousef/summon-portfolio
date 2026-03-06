#!/bin/bash
# Prepares seen-tools/ directory for Docker build.
# Run this before `docker build` to bundle the Seen compiler into the image.
#
# Usage:
#   ./scripts/prepare-seen-tools.sh [SEENLANG_ROOT]
#
# SEENLANG_ROOT defaults to ../seenlang (sibling directory) or SEENLANG_ROOT env var.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PORTFOLIO_ROOT="$(dirname "$SCRIPT_DIR")"
TOOLS_DIR="$PORTFOLIO_ROOT/seen-tools"

# Resolve seenlang root
SEENLANG="${1:-${SEENLANG_ROOT:-$PORTFOLIO_ROOT/../seenlang}}"
SEENLANG="$(cd "$SEENLANG" 2>/dev/null && pwd)" || {
    echo "Error: Seen language repo not found at: $SEENLANG"
    echo "Usage: $0 /path/to/seenlang"
    exit 1
}

SEEN_BINARY="$SEENLANG/compiler_seen/target/seen"
SEEN_RUNTIME="$SEENLANG/seen_runtime"
SEEN_LANGUAGES="$SEENLANG/languages"

if [ ! -f "$SEEN_BINARY" ]; then
    echo "Error: Seen compiler binary not found at: $SEEN_BINARY"
    echo "Build it first: cd $SEENLANG && ./scripts/safe_rebuild.sh"
    exit 1
fi

if [ ! -d "$SEEN_RUNTIME" ]; then
    echo "Error: seen_runtime/ not found at: $SEEN_RUNTIME"
    exit 1
fi

if [ ! -d "$SEEN_LANGUAGES" ]; then
    echo "Error: languages/ not found at: $SEEN_LANGUAGES"
    exit 1
fi

echo "Preparing seen-tools/ from: $SEENLANG"

# Clean and recreate
rm -rf "$TOOLS_DIR"
mkdir -p "$TOOLS_DIR"

# Copy compiler binary
cp "$SEEN_BINARY" "$TOOLS_DIR/seen"
chmod +x "$TOOLS_DIR/seen"

# Copy runtime (C source + headers + precompiled objects)
cp -r "$SEEN_RUNTIME" "$TOOLS_DIR/seen_runtime"

# Copy language definitions (keyword/builtin mappings for multilingual support)
cp -r "$SEEN_LANGUAGES" "$TOOLS_DIR/languages"

echo "Done. Contents of seen-tools/:"
ls -lh "$TOOLS_DIR/seen"
echo "seen_runtime/: $(ls "$TOOLS_DIR/seen_runtime/" | wc -l) files"
echo "languages/: $(ls -d "$TOOLS_DIR/languages"/*/ | wc -l) languages"
echo ""
echo "Now run: docker build -t portfolio ."
