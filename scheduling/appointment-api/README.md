# appointment-api

WebFlux REST API 모듈입니다.

---

## 상태

현재 빈 모듈 — **Phase 3+에서 구현 예정**.

---

## 계획

- Spring WebFlux + Coroutine 기반 REST API
- 예약 CRUD 엔드포인트
  - `GET /appointments/{id}` — 예약 조회
  - `POST /appointments` — 예약 생성
  - `PATCH /appointments/{id}/status` — 상태 변경
  - `DELETE /appointments/{id}` — 예약 취소
- 슬롯 조회 API
  - `GET /clinics/{clinicId}/slots` — 병원별 가용 슬롯 조회
- 재배정 관리 API
  - `POST /appointments/{id}/reschedule` — 재배정 처리
  - `GET /appointments/{id}/reschedule-candidates` — 재배정 후보 조회
- 요청/응답 DTO (웹 계층용)
- 에러 처리 및 통일된 응답 포맷

---

## 향후 추가 예정

- OpenAPI/Swagger 문서화
- 인증/인가 (JWT)
- 페이징 및 필터링
- 캐싱 전략
