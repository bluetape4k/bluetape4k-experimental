#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

TAG="${1:-serializer-compressor}"
TITLE="${BENCHMARK_TITLE:-Binary Serializer Compressor Benchmark}"
RESULT_DIR="$SCRIPT_DIR/benchmark-results"
JSON_FILE="$RESULT_DIR/combo-$TAG.json"
MD_FILE="$RESULT_DIR/combo-$TAG.md"

mkdir -p "$RESULT_DIR"

./gradlew :benchmarks:benchmarkCustomReport \
  -Pbenchmark.include='.*BinarySerializerCompressorBenchmark.*' \
  -Pbenchmark.tag="$TAG" \
  -Pbenchmark.resultFile="$JSON_FILE" \
  -Pbenchmark.outputMd="$MD_FILE" \
  -Pbenchmark.title="$TITLE"
