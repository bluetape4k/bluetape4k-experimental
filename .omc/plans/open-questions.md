# Open Questions

## graph-library - 2026-03-24

- [ ] Apache AGE Docker 이미지 `apache/age:PG17_latest`가 ARM64 (Apple Silicon) 지원하는지 확인 필요 -- Testcontainers 실행 가능 여부에 영향
- [ ] Neo4j Java Driver 5.x의 `executeReadAsync` vs `rxSession` 중 Coroutine 브릿지에 적합한 API 선택 필요 -- reactive vs async 성능 차이
- [ ] graph-core의 `GraphVertex.id` 타입: AGE는 `Long` (agtype), Neo4j 5.x는 element ID(`String`)로 전환 중 -- `Any` 또는 제네릭
  `ID` 타입 검토 필요
- [ ] AGE의 agtype 파싱: JDBC ResultSet에서 agtype을 직접 파싱할지, `ag_catalog` 함수를 사용할지 결정 필요

## graph-expansion - 2026-03-24

- [ ] Memgraph `elementId()` 호환성: Memgraph 최신 버전이 Neo4j 5.x의 `elementId()` API를 완전히 지원하는지 런타임 확인 필요 -- 미지원 시 `id()` (Long) 기반으로 폴백 로직 추가
- [ ] TinkerPop 3.7.3 + Java 25 호환성: TinkerPop 3.7.x가 Java 25에서 동작하는지 빌드 시 확인 필요 -- 미호환 시 3.7.4+ 또는 4.0.x 검토
- [ ] Neo4j 예제에서 Service 클래스 소스 복사 vs 공통 모듈 분리: 현재는 소스 복사 방식. 향후 예제가 3개 이상이면 공통 모듈(`graph-examples-common`) 분리 검토
- [ ] Memgraph Testcontainers: `memgraph/memgraph` Docker 이미지 ARM64 지원 여부 (Apple Silicon) -- 미지원 시 `--platform linux/amd64` 옵션 필요
