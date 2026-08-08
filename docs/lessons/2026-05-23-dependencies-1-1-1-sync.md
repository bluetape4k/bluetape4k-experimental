# 의존성 1.1.1 동기화

## 배경

`bluetape4k-dependencies` 1.1.0은 artifact availability audit에서 배포되지
않은 mock web application 모듈의 생성 alias를 발견한 뒤 1.1.1로 대체되었다.
이 저장소는 공유 카탈로그를 사용하므로 BOM을 우회하도록 로컬 버전을
고정해서는 안 된다.

## 결정

표준 shared-version sync 경로를 통해 `bluetape4k-dependencies = "1.1.1"`을
사용한다. 저장소별 제외 항목을 추가하는 대신 로컬 카탈로그를 기준 데이터
원본의 반영본으로 유지한다.

## 결과

PR #57에서 이 저장소를 1.1.1 카탈로그에 맞췄고 CI 통과 후 병합했다.

## 검증

- 병합 전에 GitHub PR #57 status checks가 통과했다.
- 다운스트림 PR을 병합한 뒤 작업 공간 수준의
  `scripts/sync-shared-versions.py --workspace .. --check --summary`가
  통과했다.

## 향후 지침

공유 카탈로그 patch로 publication availability 문제가 해결되면 Maven
Central `repo1`에서 새 버전을 resolve할 때까지 기다린 뒤 다운스트림 CI를
다시 실행한다. 이후 CI가 실패하면 catalog availability가 아니라 저장소별
호환성 문제로 판단한다.
