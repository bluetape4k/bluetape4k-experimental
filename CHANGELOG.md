# 변경 기록

`bluetape4k-experimental`의 모든 주요 변경 사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)를 따릅니다.
이 저장소는 실험 모듈을 포함하며 안정적인 라이브러리 계열로 공개하지
않습니다.

## [Unreleased]

### 추가

- Root README hero image, 한국어 README, 프로젝트 목적·기능·아키텍처 문서를 추가했습니다.
- 현재 할당된 open Issue가 없음을 보여 주는 `WIP.md` 스냅샷을 추가했습니다.
- Lettuce 기반 read-through, write-through, write-behind cache 전략 실험을
  추가했습니다 ([PR #1](https://github.com/bluetape4k/bluetape4k-experimental/pull/1)).
- 병원 appointment scheduling system 실험을 추가했습니다
  ([PR #3](https://github.com/bluetape4k/bluetape4k-experimental/pull/3)).
- Graph extraction 전에 graph repository sync/suspend 이중 API 실험을
  추가했습니다 ([PR #5](https://github.com/bluetape4k/bluetape4k-experimental/pull/5)).
- Exposed CockroachDB JDBC support 모듈과 CockroachDB v26.1+ `WINDOW FRAME
  GROUPS` 지원을 추가했습니다 ([PR #7](https://github.com/bluetape4k/bluetape4k-experimental/pull/7),
  [PR #8](https://github.com/bluetape4k/bluetape4k-experimental/pull/8)).
- CI와 Nightly workflow를 추가했습니다
  ([PR #12](https://github.com/bluetape4k/bluetape4k-experimental/pull/12)).

### 변경

- 공유 `bluetape4k-dependencies` catalog line을 공개된 `1.2.0` BOM에 맞췄습니다.
- Root README 언어 정책을 `README.md` 영어, `README.ko.md` 한국어로 맞췄습니다.
- Dependency governance, compatibility guard, Kover 정책, Dependabot 유지보수를
  PR #18부터 PR #27까지 반영했습니다.
- `buildSrc` dependency declaration을 `gradle/libs.versions.toml`로 옮기고
  Gradle wrapper를 9.5.0으로 올렸습니다
  ([PR #10](https://github.com/bluetape4k/bluetape4k-experimental/pull/10),
  [PR #11](https://github.com/bluetape4k/bluetape4k-experimental/pull/11)).
- Graph 모듈을 독립 `bluetape4k-graph` 프로젝트로 옮겼습니다
  ([PR #6](https://github.com/bluetape4k/bluetape4k-experimental/pull/6)).
- Exposed dependency group을 `bluetape4k-exposed`로 바꾸고
  `bluetape4k-dependencies` BOM을 통합했습니다
  ([PR #14](https://github.com/bluetape4k/bluetape4k-experimental/pull/14)).
- Test code를 Kluent에서 `bluetape4k-junit5`를 통한
  `bluetape4k-assertions`로 옮겼습니다
  ([PR #16](https://github.com/bluetape4k/bluetape4k-experimental/pull/16)).

### 버그 수정

- 초기 실험 모듈의 IDE diagnostics와 code quality 문제를 해결했습니다
  ([PR #2](https://github.com/bluetape4k/bluetape4k-experimental/pull/2)).
- Build configuration에서 `mavenLocal()`을 제거했습니다
  ([PR #15](https://github.com/bluetape4k/bluetape4k-experimental/pull/15)).
