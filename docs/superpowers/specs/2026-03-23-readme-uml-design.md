# README UML 다이어그램 추가 설계

**날짜**: 2026-03-23
**형식**: Mermaid (`\`\`\`mermaid` 코드블록)
**범위**: 13개 모듈 전체, 총 22개 다이어그램

---

## 모듈별 다이어그램 계획

### 복잡한 인프라 모듈 (아키텍처 + 시퀀스 2-3개)

#### `infra/cache-lettuce` — 3개
1. **아키텍처** (`graph TD`): L1/L2 2계층 구성요소 (기존 ASCII 대체)
2. **시퀀스**: Read-Through 흐름 (L1 hit → 즉시 반환 / L1 miss → Redis → L1 populate)
3. **시퀀스**: Write-Through + CLIENT TRACKING 무효화 (인스턴스A → Redis → push → 인스턴스B L1 evict)

#### `infra/hibernate-cache-lettuce` — 2개
1. **아키텍처** (`graph TD`): Hibernate ORM → RegionFactory → StorageAccess → NearCache → L1/L2 (기존 ASCII 대체)
2. **시퀀스**: `getFromCache` / `putIntoCache` 동작 흐름

#### `data/exposed-lettuce` — 3개
1. **아키텍처** (`graph TD`): Repository → LettuceLoadedMap → MapLoader/MapWriter → Redis/DB (기존 ASCII 대체)
2. **시퀀스**: Read-Through 흐름
3. **플로우차트**: WriteMode 분기 (NONE / WRITE_THROUGH / WRITE_BEHIND)

---

### 통합 모듈 (컴포넌트 + 클래스 1-2개)

#### `spring-boot/hibernate-lettuce` — 2개
1. **컴포넌트** (`graph LR`): 3개 AutoConfig 클래스 → 조건 → 등록 빈
2. **플로우차트**: `application.yml` → HibernatePropertiesCustomizer → Hibernate properties 매핑

#### `spring-data/exposed-jdbc-spring-data` — 2개
1. **클래스** (`classDiagram`): CrudRepository → ListCrudRepository → ExposedRepository 계층
2. **플로우차트**: PartTree 쿼리 파생 흐름 (메서드명 → 키워드 → Exposed Op)

#### `spring-data/exposed-r2dbc-spring-data` — 2개
1. **클래스** (`classDiagram`): SuspendExposedCrudRepository + SuspendExposedPagingRepository 계층
2. **시퀀스**: `save` / `findByIdOrNull` suspend 호출 흐름

---

### 유틸/예제 (1-2개)

#### `utils/ulid` — 2개
1. **클래스** (`classDiagram`): ULID / Factory / Monotonic / StatefulMonotonic 관계
2. **플로우차트**: 생성기 선택 흐름

#### `examples/spring-boot-hibernate-lettuce-demo` — 1개
- **시퀀스**: HTTP → Controller → JPA → Hibernate 2LC → Redis/DB

#### `examples/exposed-r2dbc-spring-data-webflux-demo` — 1개
- **시퀀스**: HTTP → WebFlux → SuspendRepository → R2DBC Exposed → DB

#### `examples/exposed-jdbc-spring-data-mvc-demo` — 1개
- **시퀀스**: HTTP → MVC Controller → ExposedRepository → transaction → DB

#### `examples/exposed-jpa-benchmark` — 1개
- **아키텍처** (`graph LR`): Exposed Layer vs JPA Layer 병렬 구조

---

### 최소 모듈 (1개)

#### `shared` — 1개
- **컴포넌트** (`graph LR`): shared ← 모든 모듈 의존 관계

#### `io/benchmarks` — 1개
- **플로우차트**: 벤치마크 실행 파이프라인 (Quick → Custom → Suite → Markdown)

---

## 구현 원칙

- 기존 ASCII 아트 다이어그램은 Mermaid로 교체 (중복 제거)
- 기존 텍스트 설명과 내용 중복 최소화 — 다이어그램은 글로 설명하기 어려운 구조/흐름만 표현
- 각 다이어그램 앞에 1줄 설명 추가 (무슨 흐름인지 맥락 제공)
- scheduling 모듈은 README 없어 제외

## 총계

- 대상 모듈: 13개
- 총 다이어그램: 22개
