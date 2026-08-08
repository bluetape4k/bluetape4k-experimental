# 2026-05-18 — Experimental WIP 감사 및 Java 25 CI 계약

## 배경

최신 의존성 거버넌스 갱신 이후에도 WIP 파일에는 할당된 open issue가
없었다. 이 저장소는 스스로를 Kotlin 2.3, Java 25, Spring Boot 4 검증장으로
설명한다.

## 결정

workflow 계약의 공백을 추적하기 위해 #45를 등록한다. CI와 Nightly는 JDK 21을
설치하지만 README, AGENTS.md, Gradle toolchain은 모두 Java 25를 대상
runtime/toolchain으로 설명한다.

## 결과

`WIP.md`는 이제 새로운 실험 기능이나 승격 작업보다 먼저 처리할
build/CI 유지보수 항목으로 #45를 나열한다.

## 검증

- `gh issue list --state open --assignee debop`이 open issue 하나를 반환했다.
- `gh issue view 45`로 #45가 open 상태이고 `bug`, `github_actions`, `java`
  라벨이 있으며 `debop`에 할당된 것을 확인했다.
- `rg`로 workflow의 JDK 21 설정과 Gradle의 Java 25 toolchain 설정을
  확인했다.

## 향후 에이전트 지침

실험 모듈에서는 workflow runtime JDK, Gradle toolchain JDK,
README/AGENTS 사전 조건을 함께 확인한다. toolchain 컴파일만으로는 workflow
runtime 불일치를 발견하지 못할 수 있다.
