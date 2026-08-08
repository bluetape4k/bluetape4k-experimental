# Snapshot 캐시 조치

## 배경

root Gradle 설정은 changing-module cache TTL을 0초로 사용하고 있어 매번
구성할 때 SNAPSHOT metadata를 다시 검증하도록 강제했다.

## 결정

root changing-module cache TTL을 0초에서 1일로 변경한다.

## 결과

Gradle은 이제 일반 빌드에서 변경 가능한 SNAPSHOT metadata를 재사용하면서도
매일 갱신할 수 있다.

## 검증

- `actionlint .github/workflows/*.yml`
- `rg -n -- '--refresh-dependencies|cache-disabled: true' .github/workflows` -> no matches
- `./gradlew help --no-daemon`
- `git diff --check`

## 향후 지침

명시적인 의존성 갱신은 전용 post-publish freshness check에서만 사용한다.
일반 CI, Nightly, Examples workflow는 캐시된 changing-module metadata를
사용하고, 테스트 전용 SNAPSHOT 의존성에 필요할 때만 대상 warm-up을
수행한다.
