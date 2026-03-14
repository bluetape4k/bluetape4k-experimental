#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SUFFIX="${1:-$(date +%Y%m%d-%H%M%S)}"

"$SCRIPT_DIR/benchmark-serializer.sh" "serializer-$SUFFIX"
"$SCRIPT_DIR/benchmark-combo.sh" "combo-$SUFFIX"
"$SCRIPT_DIR/benchmark-near-cache.sh" "near-cache-$SUFFIX"
"$SCRIPT_DIR/benchmark-near-suspend.sh" "near-suspend-$SUFFIX"
"$SCRIPT_DIR/benchmark-exposed-r2dbc.sh" "exposed-r2dbc-$SUFFIX"
