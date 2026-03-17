# Code Review Fix Spec

## Objective
리뷰에서 드러난 실제 결함과 회귀 위험을 코드로 수정하고, 각 수정이 테스트로 고정되도록 만든다.

## Target Fixes
- Hibernate near-cache storage release가 공유 cache를 닫지 않도록 수정
- Exposed PartTree query가 정적 `OrderBy`와 limiting semantics를 지키도록 수정
- Lettuce near-cache bulk write/read 경로의 비효율과 실패 semantics를 개선
- Benchmark snapshot CLI가 파일명만 전달돼도 안전하게 동작하도록 수정
- 예제 모듈의 명확한 계약 위반/트랜잭션 경계 문제 수정
- WebFlux demo startup의 blocking 초기화 제거

## Validation
- 관련 모듈별 단위/통합 test 추가
- 대상 모듈 test + 가능하면 루트 regression test 실행
