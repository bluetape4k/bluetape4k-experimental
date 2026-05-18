# Shared version drift 정리

## Context

`bluetape4k-dependencies:1.0.0` publishing 이후 `bluetape4k-experimental`의 local catalog가 아직 `1.0.0-SNAPSHOT`을 가리켜 중앙 source-of-truth 검증에서 drift로 잡혔다.

## Decision

Catalog의 `bluetape4k-dependencies` alias를 release 버전 `1.0.0`으로 맞춘다. 이미 `exposed=1.3.0`, `dokka=2.2.0`은 중앙 기준과 일치하므로 버전은 건드리지 않는다. `bluetape4k-dependencies:1.0.0`이 관리하는 published artifact 이름에 맞춰 `io.github.bluetape4k.exposed:bluetape4k-exposed-*` 좌표도 함께 정렬한다.

## Outcome

Fresh source-of-truth 검증에서 experimental repo가 snapshot BOM을 참조하지 않도록 정리했다.

## Verification

- `../bluetape4k-dependencies/.worktrees/feat/dependency-governance/scripts/sync-shared-versions.py --workspace <symlink-workspace> --repo bluetape4k-experimental --check --summary`
- `./gradlew build --no-daemon`
- `git diff --check`

## Future Guard

Publishing 전 임시 snapshot은 PR 기준으로 남기지 않는다. release artifact가 올라온 뒤 source-of-truth 검증을 다시 돌려 release 버전으로 고정한다.
