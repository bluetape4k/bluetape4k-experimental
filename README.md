# bluetape4k-experimental

Kotlin/Gradle 기반의 실험적 기능 프로토타이핑 프로젝트입니다.
새로운 아이디어나 기술을 검증하고 bluetape4k 메인 라이브러리에 편입하기 전에 탐색하는 공간입니다.

## 기술 스택

- **Kotlin** 2.3.0
- **Java** 25
- **Spring Boot** 4.0.x
- **Gradle** 9.3.1 (Kotlin DSL)

## 모듈 구조

```
bluetape4k-experimental/
├── shared/          # 공통 유틸리티
├── kotlin/          # Kotlin 언어 기능 실험
├── spring-boot/     # Spring Boot 4 실험
│   └── hibernate-lettuce/              # Hibernate Lettuce Cache Auto-Configuration
├── spring-data/     # Spring Data 실험
├── coroutines/      # Coroutines 실험
├── ai/              # AI/LLM 통합 실험
├── data/            # 데이터 계층 실험
├── io/              # I/O, 직렬화 실험
├── infra/           # 인프라(Redis, Kafka 등) 실험
│   └── hibernate-cache-lettuce/        # Hibernate 7 2nd Level Cache (bluetape4k-cache-lettuce 기반)
└── examples/        # 예제 애플리케이션
    └── spring-boot-hibernate-lettuce-demo/ # Spring Boot Hibernate Lettuce 데모 앱
```

## 주요 모듈

### io/benchmarks

직렬화 및 압축 조합의 성능, serialized byte size, allocation 부하를 검증하는 benchmark 모듈.

- serializer 비교 결과:
  [BINARY_SERIALIZER_BENCHMARK_RESULTS.md](io/benchmarks/BINARY_SERIALIZER_BENCHMARK_RESULTS.md)
- serializer + compressor 조합 비교 결과:
  [BINARY_SERIALIZER_COMPRESSOR_RESULTS.md](io/benchmarks/BINARY_SERIALIZER_COMPRESSOR_RESULTS.md)

현재 benchmark 기준 추천:

- 기본 직렬화기: `BinarySerializers.Fory`
- 압축 포함 기본 조합: `Fory + Snappy`
- 크기 최우선 조합:
    - small/medium: `Kryo + Deflate`
    - large: `Kryo + Zstd`

### bluetape4k-cache-lettuce
Caffeine(L1) + Redis(L2) 2-tier Near Cache 구현체는 `bluetape4k-projects` 의 `bluetape4k-cache-lettuce` 모듈로 승격되었다.
이 저장소에서는 해당 외부 모듈을 참조해 Hibernate / Spring Boot 통합을 검증한다.

### infra/hibernate-cache-lettuce
Hibernate 7 Second Level Cache를 Near Cache로 구현. `hibernate.cache.lettuce.*` properties로 설정.
→ [README](infra/hibernate-cache-lettuce/README.md)

### spring-boot/hibernate-lettuce
Spring Boot 4 Auto-Configuration. `application.yml`의 `bluetape4k.cache.lettuce-near.*` 설정만으로 Hibernate 2nd Level Cache 자동 활성화. Actuator 엔드포인트 및 Micrometer 메트릭 제공.
→ [README](spring-boot/hibernate-lettuce/README.md)

### examples/spring-boot-hibernate-lettuce-demo
Lettuce 기반 Hibernate 2nd Level Cache를 활용한 Spring Boot REST API 데모 애플리케이션.

## 빌드

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 테스트
./gradlew :<module-name>:test

# Lettuce Cache 관련 모듈 테스트
./gradlew :hibernate-cache-lettuce:test
./gradlew :spring-boot-hibernate-lettuce:test
./gradlew :benchmarks:test

# 사용 가능한 태스크 목록
./gradlew tasks
```

## 모듈 추가

카테고리 디렉토리 아래에 새 폴더를 만들면 자동으로 Gradle 모듈로 인식됩니다.

예시: `kotlin/context-parameters/` → `:context-parameters` 모듈

의존성은 `buildSrc/src/main/kotlin/Libs.kt`에서 관리합니다.
