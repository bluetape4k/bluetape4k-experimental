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
│   └── hibernate-cache-lettuce-near/   # Hibernate Near Cache Auto-Configuration
├── spring-data/     # Spring Data 실험
├── coroutines/      # Coroutines 실험
├── ai/              # AI/LLM 통합 실험
├── data/            # 데이터 계층 실험
├── io/              # I/O, 직렬화 실험
├── infra/           # 인프라(Redis, Kafka 등) 실험
│   ├── cache-lettuce-near/             # Lettuce Near Cache (Caffeine + Redis + CLIENT TRACKING)
│   └── hibernate-cache-lettuce-near/   # Hibernate 7 2nd Level Cache (Near Cache 기반)
└── examples/        # 예제 애플리케이션
    └── hibernate-cache-lettuce-near-demo/  # Near Cache Spring Boot 데모 앱
```

## 주요 모듈

### infra/cache-lettuce-near
Caffeine(L1) + Redis(L2) 2-tier Near Cache 구현체. RESP3 CLIENT TRACKING으로 분산 환경에서 로컬 캐시 자동 무효화를 지원한다.
→ [README](infra/cache-lettuce-near/README.md)

### infra/hibernate-cache-lettuce-near
Hibernate 7 Second Level Cache를 Near Cache로 구현. `hibernate.cache.lettuce.*` properties로 설정.
→ [README](infra/hibernate-cache-lettuce-near/README.md)

### spring-boot/hibernate-cache-lettuce-near
Spring Boot 4 Auto-Configuration. `application.yml`의 `bluetape4k.cache.lettuce-near.*` 설정만으로 Hibernate 2nd Level Cache 자동 활성화. Actuator 엔드포인트 및 Micrometer 메트릭 제공.
→ [README](spring-boot/hibernate-cache-lettuce-near/README.md)

### examples/hibernate-cache-lettuce-near-demo
Near Cache를 활용한 Spring Boot REST API 데모 애플리케이션.

## 빌드

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 테스트
./gradlew :<module-name>:test

# Near Cache 관련 모듈 테스트
./gradlew :cache-lettuce-near:test
./gradlew :hibernate-cache-lettuce-near:test
./gradlew :hibernate-redis-near:test

# 사용 가능한 태스크 목록
./gradlew tasks
```

## 모듈 추가

카테고리 디렉토리 아래에 새 폴더를 만들면 자동으로 Gradle 모듈로 인식됩니다.

예시: `kotlin/context-parameters/` → `:context-parameters` 모듈

의존성은 `buildSrc/src/main/kotlin/Libs.kt`에서 관리합니다.