# 의존성 카탈로그 동기화

## 배경

중앙 카탈로그 갱신의 일환으로 `bluetape4k-dependencies`가 Timefold Solver를
2.1.0으로, Apache Fory를 0.17.0으로 승격했다.

## 결정

공유 카탈로그 변경을 로컬에 반영하고 이 실험 저장소에서 테스트를 제외한
빌드 검사를 실행한다.

## 결과

`gradle/libs.versions.toml`은 이제 중앙 카탈로그의 Timefold Solver 2.1.0 및
Apache Fory 0.17.0 버전을 반영한다.

## 검증

- `./gradlew build -x test --no-daemon`

빌드는 기존의 관련 없는 deprecation warning을 출력한 채 완료되었다.
