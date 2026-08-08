# WIP - bluetape4k-experimental

스냅샷: 2026-06-02 KST
범위: 2026-01-01 이후 생성되고 `debop`에 할당된 열린 GitHub Issue.
열린 Issue 수: 1개.

## 최근 완료

- CI/Nightly workflow, Gradle 9.5.0 wrapper, version catalog migration,
  Spring Boot 4 dependency 정렬을 병합했습니다.
- Graph 모듈을 독립 `bluetape4k-graph` 저장소로 옮겼습니다.
- Exposed CockroachDB 실험과 dependency/BOM 정렬을 병합했습니다.
- Kluent test를 `bluetape4k-assertions`로 옮겼습니다.
- Dependency governance, compatibility guard, Kover 정책, Dependabot 유지보수를
  PR #18부터 PR #27까지 병합했습니다.
- Shared-version drift와 중앙 dependency governance 변경을 2026-05-18에
  병합했습니다.
- `exposed` artifactId rename tracking (#31)을 닫았습니다.

## 현재 방향

Java 25 workflow 계약 정렬.

이 저장소는 Kotlin 2.3 / Java 25 / Spring Boot 4 검증용입니다. CI와 Nightly는
JDK 25에서 실행하거나, 다른 실험 작업을 승격하기 전에 Java 25 검증 lane을
명시적으로 포함해야 합니다.

## 우선순위 큐

| 우선순위 | Issue | 난이도 | 비고 |
|---|---|---:|---|
| P1 | [#45](https://github.com/bluetape4k/bluetape4k-experimental/issues/45) CI와 Nightly는 repository 계약이 Java 25인데 JDK 21에서 실행 | S | Workflow runtime이 Java 25를 검증하거나 runtime/toolchain 범위를 명확히 분리해야 함 |

## WIP 한도

| 작업 lane | 한도 | 다음 작업 |
|---|---:|---|
| Build/CI 유지보수 | 1 | `#45` |
| 실험 feature | 1 | Java 25 workflow 계약이 명확해진 뒤 할당된 Issue 대기 |
| 승격 작업 | 1 | 동작과 migration 경로를 문서화한 뒤에만 승격 |
