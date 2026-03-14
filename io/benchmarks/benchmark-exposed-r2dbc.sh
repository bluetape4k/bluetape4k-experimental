#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

TAG="${1:-exposed-r2dbc}"
TITLE="${BENCHMARK_TITLE:-Exposed R2DBC Benchmark}"
RESULT_DIR="$SCRIPT_DIR/benchmark-results"
JSON_FILE="$RESULT_DIR/exposed-r2dbc-$TAG.json"
MD_FILE="$RESULT_DIR/exposed-r2dbc-$TAG.md"

mkdir -p "$RESULT_DIR"

./gradlew :benchmarks:benchmarkCustomReport \
  -Pbenchmark.include='.*SimpleSuspendExposedRepository.*Benchmark.*' \
  -Pbenchmark.tag="$TAG" \
  -Pbenchmark.resultFile="$JSON_FILE" \
  -Pbenchmark.outputMd="$MD_FILE" \
  -Pbenchmark.title="$TITLE"
