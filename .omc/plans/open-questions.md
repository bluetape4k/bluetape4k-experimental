# Open Questions

## graph-library - 2026-03-24

- [ ] Apache AGE Docker 이미지 `apache/age:PG17_latest`가 ARM64 (Apple Silicon) 지원하는지 확인 필요 -- Testcontainers 실행 가능 여부에 영향
- [ ] Neo4j Java Driver 5.x의 `executeReadAsync` vs `rxSession` 중 Coroutine 브릿지에 적합한 API 선택 필요 -- reactive vs async 성능 차이
- [ ] graph-core의 `GraphVertex.id` 타입: AGE는 `Long` (agtype), Neo4j 5.x는 element ID(`String`)로 전환 중 -- `Any` 또는 제네릭
  `ID` 타입 검토 필요
- [ ] AGE의 agtype 파싱: JDBC ResultSet에서 agtype을 직접 파싱할지, `ag_catalog` 함수를 사용할지 결정 필요
