# 의존성 1.1.4 동기화

## 배경

`bluetape4k-dependencies` 1.2.0 release preparation을 위해서는 중앙 BOM CI가
`develop`에서 통과하기 전에 다운스트림 카탈로그 소비자가 shared-version
drift 없이 정리되어 있어야 한다.

## 결정

로컬 `bluetape4k-dependencies` 카탈로그 참조를 최신 published `1.1.4`
기준선에 맞춘다. 해당 BOM이 publish되어 Maven Central에서 확인될 때까지
`1.2.0`을 사용하지 않는다.

## 결과

이 저장소는 이제 현재 release-train preflight에 대한 중앙 shared-version
기준 데이터 원본과 일치한다.

## 검증

`bluetape4k-dependencies`에서
`sync-shared-versions.py --workspace /Users/debop/work/bluetape4k --write --check --summary`로
검증했다.
