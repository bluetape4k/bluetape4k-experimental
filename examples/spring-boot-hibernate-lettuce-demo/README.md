# spring-boot-hibernate-lettuce-demo

Spring Boot 4 + Hibernate 7 **2nd Level Cache (2LC)** with **Lettuce Near Cache** 데모 애플리케이션.

`spring-boot-hibernate-lettuce` 모듈의 auto-configuration을 사용해 zero-code로 Hibernate 2LC를 활성화하는 예제이다.

## 아키텍처

```
HTTP Request
    │
ProductController (REST API)
    │
ProductRepository (Spring Data JPA)
    │
Hibernate (JPA)
    │
    ├── [2nd Level Cache ON] ────> LettuceNearCacheRegionFactory
    │                                   │
    │                           ┌───────┴────────┐
    │                           │                │
    │                       Caffeine (L1)    Redis (L2)
    │                       로컬 인메모리     분산 캐시
    │
    └── [Cache Miss] ─────> H2 Database
```

## 실행 방법

### 사전 조건

Redis 서버가 실행 중이어야 한다.

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 애플리케이션 실행

```bash
./gradlew :spring-boot-hibernate-lettuce-demo:bootRun
```

## 엔티티 설정

```kotlin
@Entity
@Table(name = "products")
@Cacheable                                                       // JPA 2LC 활성화
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE,  // Hibernate 캐시 전략
       region = "product")                                       // 캐시 리전 이름
data class Product(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String,
    val description: String? = null,
    val price: Double = 0.0,
)
```

## application.yml 설정

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          region.factory_class: io.bluetape4k.hibernate.cache.lettuce.LettuceNearCacheRegionFactory

bluetape4k:
  cache:
    lettuce-near:
      redis-uri: redis://localhost:6379
      codec: lz4fory                        # 직렬화 코덱
      use-resp3: true                       # RESP3 + CLIENT TRACKING 활성화
      local:
        max-size: 10000
        expire-after-write: 30m
      redis-ttl:
        default: 10m
        regions:
          product: 5m                       # product 리전 TTL 개별 설정
```

## REST API

### 상품 API (`/api/products`)

| 메서드      | 경로                   | 설명                 |
|----------|----------------------|--------------------|
| `GET`    | `/api/products`      | 전체 상품 조회           |
| `GET`    | `/api/products/{id}` | ID로 상품 조회 (2LC 적용) |
| `POST`   | `/api/products`      | 상품 생성              |
| `PUT`    | `/api/products/{id}` | 상품 수정 (캐시 갱신)      |
| `DELETE` | `/api/products/{id}` | 상품 삭제 (캐시 제거)      |

### 캐시 관리 API (`/api/cache`)

| 메서드      | 경로                          | 설명                            |
|----------|-----------------------------|-------------------------------|
| `GET`    | `/api/cache/stats`          | 리전별 캐시 통계 (L1 크기, hit/miss 수) |
| `DELETE` | `/api/cache/evict/{region}` | 특정 리전 L1 캐시 비우기 (L2 Redis 유지) |
| `DELETE` | `/api/cache/evict`          | 전체 리전 L1 캐시 비우기               |

> **캐시 eviction 주의**: `/api/cache/evict`는 L1(Caffeine)만 비운다. Redis L2는 영향받지 않는다.

### Actuator 엔드포인트

`spring-boot-hibernate-lettuce` auto-configuration에서 제공하는 Actuator 통계.

```bash
# Near Cache 리전 통계
GET /actuator/nearcache
```

### 통계 응답 예시

```json
{
  "product": {
    "regionName": "product",
    "localSize": 42,
    "localHitCount": 1830,
    "localMissCount": 42,
    "localHitRate": 0.977
  }
}
```

## 테스트

```bash
./gradlew :spring-boot-hibernate-lettuce-demo:test
```
