#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

TAG="${1:-near-suspend}"
TITLE="${BENCHMARK_TITLE:-Lettuce Near Suspend Cache Benchmark}"
RESULT_DIR="$SCRIPT_DIR/benchmark-results"
JSON_FILE="$RESULT_DIR/near-suspend-$TAG.json"
MD_FILE="$RESULT_DIR/near-suspend-$TAG.md"

mkdir -p "$RESULT_DIR"

./gradlew :benchmarks:benchmarkCustomReport \
  -Pbenchmark.include='.*LettuceNearSuspendCacheBenchmark.*' \
  -Pbenchmark.tag="$TAG" \
  -Pbenchmark.resultFile="$JSON_FILE" \
  -Pbenchmark.outputMd="$MD_FILE" \
  -Pbenchmark.title="$TITLE"
