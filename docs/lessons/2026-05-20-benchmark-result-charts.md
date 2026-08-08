# 2026-05-20 — 벤치마크 결과 차트

## 배경

실험 벤치마크 문서에는 유용한 수치 표가 있었지만, 독자가 serializer,
compressor, TTL 모드, Exposed/JPA endpoint latency를 직접 비교해야 했다.

## 결정

`docs/images/readme-charts/` 아래에 정적 차트 asset을 추가하고 표는 그대로
유지한다. round-trip과 p99 결과에는 낮을수록 좋은 latency 차트를 사용한다.

## 결과

Exposed와 JPA의 p99 latency, binary serializer round-trip latency,
serializer + compressor round-trip latency, Lettuce near suspend TTL 경로
latency에 대한 차트를 추가했다.

## 검증

- `xmllint --noout docs/images/readme-charts/*.svg`
- `identify docs/images/readme-charts/*.png`
- Markdown 링크를 로컬 파일과 대조해 확인했다.

## 향후 지침

측정된 표를 이미지만으로 대체하지 않는다. 차트는 읽기 보조 자료로
추가하고, 차트와 표 모두에서 단위를 확인할 수 있게 유지한다.
