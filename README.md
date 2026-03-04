# bluetape4k-experimental

Kotlin/Gradle 기반의 실험적 기능 프로토타이핑 프로젝트입니다.
새로운 아이디어나 기술을 검증하기 위한 실험 모듈을 포함합니다.

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
├── spring-data/     # Spring Data 실험
├── coroutines/      # Coroutines 실험
├── ai/              # AI/LLM 통합 실험
├── data/            # 데이터 계층 실험
├── io/              # I/O, 직렬화 실험
└── infra/           # 인프라(Redis, Kafka 등) 실험
```

## 빌드

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 테스트
./gradlew :<module-name>:test

# 사용 가능한 태스크 목록
./gradlew tasks
```

## 모듈 추가

카테고리 디렉토리 아래에 새 폴더를 만들면 자동으로 Gradle 모듈로 인식됩니다.

예시: `kotlin/context-parameters/` → `:context-parameters` 모듈