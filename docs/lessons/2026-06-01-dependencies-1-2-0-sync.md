# 의존성 1.2.0 동기화

## 배경

최종 upstream BOM matrix가 Maven Central에서 확인된 뒤
`bluetape4k-dependencies:1.2.0`이 publish되었다.

## 결정

실험 저장소의 공유 카탈로그를 `1.1.4`에서 `1.2.0`으로 이동한다.

## 결과

실험 모듈은 이제 publish된 1.2.0 생태계 BOM과 동일한 의존성 거버넌스
기준선을 resolve한다.

## 검증

- `sync-shared-versions.py --workspace .. --write --check --summary`가
  카탈로그 행을 갱신했다.
- Maven Central이
  `io.github.bluetape4k:bluetape4k-dependencies:1.2.0`에 HTTP 200을
  반환했다.
