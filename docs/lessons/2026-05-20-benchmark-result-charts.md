# 2026-05-20 — Benchmark result charts

## Context

The experimental benchmark documents had useful numeric tables, but readers had
to compare serializers, compressors, TTL modes, and Exposed/JPA endpoint latency
manually.

## Decision

Add static chart assets under `docs/images/readme-charts/` and keep the tables
unchanged. Use lower-is-better latency charts for round-trip and p99 results.

## Outcome

Charts were added for Exposed vs JPA p99 latency, binary serializer round-trip
latency, serializer + compressor round-trip latency, and Lettuce near suspend
TTL path latency.

## Verification

- `xmllint --noout docs/images/readme-charts/*.svg`
- `identify docs/images/readme-charts/*.png`
- Markdown links were checked against local files.

## Future

Do not replace measured tables with only images. Add the chart as a reading aid
and keep units visible in both the chart and the table.
