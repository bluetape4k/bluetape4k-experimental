# bt4k 버전 카탈로그 사용

## 배경

`bluetape4k-experimental`은 `bluetape4k-dependencies`에서 이미 관리하는 여러
의존성 버전을 로컬 카탈로그에 유지했다.

## 결정

`io.github.bluetape4k:bluetape4k-version-catalog`를 `bt4k`로 import하고,
dependency management 안에서 `bt4kVersion(alias)`를 사용해 공유 leaf
dependency 버전을 관리한다. 저장소 좌표와 로컬 plugin resolution이 여전히
필요한 plugin/BOM train 버전은 로컬 alias로 유지한다.

## 결과

`libs.versions.toml`에서 선택한 direct dependency alias는 이제 버전 없이
정의하며, 제약 조건은 공유 `bt4k` 카탈로그에서 해석한다. 생성된 중복 제약
조건을 제거해 빌드 스크립트가 공유 좌표마다 하나의 제약 조건만 갖도록
했다.

## 검증

- `git diff --check`
- `./gradlew help --no-daemon --no-configuration-cache`
- `./gradlew compileKotlin --no-daemon --no-configuration-cache`

## 향후 지침

`bluetape4k-dependencies`가 이미 제공하는 의존성에 로컬 버전을 다시 추가하지
않는다. 중앙 카탈로그에서 필요한 plugin alias를 공개하거나 로컬 빌드
스크립트를 해당 alias를 안전하게 사용하도록 마이그레이션한 경우에만 남은
plugin/BOM train 중복을 이동한다.
