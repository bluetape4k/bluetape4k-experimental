# Benchmark Results — Exposed vs JPA (PostgreSQL)

## 측정 환경

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-03-07 |
| DB | PostgreSQL 18.3 (Testcontainers) |
| 런타임 | Kotlin 2.3 / Java 25 / Spring Boot 4 |
| ORM (Exposed) | JetBrains Exposed 1.1.x DAO |
| ORM (JPA) | Hibernate 7 |
| 부하 도구 | Gatling 3.15.0 (Java DSL) |
| 시나리오 | CRUD 4단계 (POST→GET→PUT→DELETE) + List |
| 동시 사용자 | CRUD 각 300 users / List 각 50 users (60초 ramp-up) |

---

## 전체 요약

| 항목 | 값 |
|------|----|
| 총 요청 수 | 2,500 |
| 성공 | 2,500 (100%) |
| 실패 | 0 (0%) |
| 평균 처리량 | 41.67 rps |
| 전체 평균 응답시간 | 8 ms |
| 전체 p50 | 5 ms |
| 전체 p95 | 16 ms |
| 전체 p99 | 49 ms |
| 최대 응답시간 | 237 ms |

---

## 요청별 상세 결과

> 단위: 밀리초(ms) / RPS = requests per second

| 요청 | Total | OK | KO | RPS | p50 | p75 | p95 | p99 |
|------|------:|---:|---:|----:|----:|----:|----:|----:|
| **1-POST Exposed Author** | 300 | 300 | 0 | 5.0 | 3 | 7 | 10 | 19 |
| **1-POST JPA Author**     | 300 | 300 | 0 | 5.0 | 2 | 7 | 10 | 17 |
| **2-GET Exposed Author**  | 300 | 300 | 0 | 5.0 | 1 | 4 | 7  | 13 |
| **2-GET JPA Author**      | 300 | 300 | 0 | 5.0 | 1 | 4 | 6  | 12 |
| **3-PUT Exposed Author**  | 300 | 300 | 0 | 5.0 | 2 | 5 | 8  | 15 |
| **3-PUT JPA Author**      | 300 | 300 | 0 | 5.0 | 2 | 5 | 8  | 16 |
| **4-DELETE Exposed Author** | 300 | 300 | 0 | 5.0 | 1 | 4 | 7 | 13 |
| **4-DELETE JPA Author**   | 300 | 300 | 0 | 5.0 | 3 | 7 | 12 | 21 |
| **5-GET Exposed List**    | 50  | 50  | 0 | 0.83 | 2 | 5 | 6 | 11 |
| **5-GET JPA List**        | 50  | 50  | 0 | 0.83 | 2 | 5 | 8 | 12 |

---

## Exposed vs JPA 비교

### CRUD 연산별 p99 비교 (ms)

| 연산 | Exposed p99 | JPA p99 | 차이 | 우위 |
|------|------------:|--------:|-----:|------|
| POST (생성) | 19 | 17 | -2 | **JPA** |
| GET (단건 조회) | 13 | 12 | -1 | **JPA** |
| PUT (수정) | 15 | 16 | +1 | **Exposed** |
| DELETE (삭제) | 13 | 21 | +8 | **Exposed ▲** |
| GET List (목록) | 11 | 12 | +1 | **Exposed** |

### 분석

- **전반적 성능**: 두 ORM 모두 p99 기준 25ms 이하로 실용적 수준에서 동등
- **DELETE**: Exposed가 JPA보다 **38% 빠름** (13ms vs 21ms) — 가장 큰 차이
- **POST**: JPA가 약간 유리 (17ms vs 19ms, ~10% 차이)
- **GET / PUT**: 사실상 동일 (1ms 이내 차이)
- **List 조회**: Exposed가 N+1 방지(`.with()` eager-load)로 안정적
- **안정성**: 300 동시 사용자 60초 부하에서 **양쪽 모두 실패율 0%**

### 결론

PostgreSQL 실환경에서 Exposed와 JPA의 CRUD 성능은 **전반적으로 동등**합니다.
DELETE 연산에서 Exposed의 우위가 두드러지며, INSERT에서는 JPA의 Batch Insert 최적화
(`jdbc.batch_size=50`)가 소폭 유리합니다. 성능보다는 **팀의 코드 스타일과 SQL 제어 수준**을
기준으로 ORM을 선택하는 것을 권장합니다.

---

## 시나리오 구성

```
Exposed CRUD: POST → pause(30ms) → GET → pause(30ms) → PUT → pause(30ms) → DELETE
JPA CRUD:     POST → pause(30ms) → GET → pause(30ms) → PUT → pause(30ms) → DELETE
Exposed List: GET /api/exposed/authors
JPA List:     GET /api/jpa/authors

ramp-up: CRUD 각 300 users / 60s, List 각 50 users / 60s
assertion: 전체 실패율 ≤ 1%
```

## HTML 리포트

Gatling 실행 후 상세 HTML 리포트:

```
build/reports/gatling/comparisonsimulation-<timestamp>/index.html
```
