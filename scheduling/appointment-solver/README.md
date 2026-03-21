# appointment-solver

Timefold Solver 기반 자동 스케줄 최적화 모듈입니다.

---

## 상태

현재 빈 모듈 — **Phase 3+에서 구현 예정**.

---

## 계획

- Timefold (OptaPlanner 후속) 기반 스케줄 최적화
- 제약 조건 기반 자동 배정
  - Hard Constraints (필수): 의사 가용성, 시간 충돌, 장비 충돌
  - Soft Constraints (최적화): 의사 부하 균등화, 환자 선호도
- 의사/장비/시간 자원 최적 배분
- 배정 품질 점수 계산

---

## 향후 추가 예정

- Solver 설정 및 제약 조건 정의
- REST API를 통한 최적화 요청
- 배정 결과 저장 및 예약 생성
