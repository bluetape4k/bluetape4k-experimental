# 중앙 의존성 거버넌스 동기화

## 배경

다운스트림 Dependabot PR이 공유 의존성 버전을 저장소별로 갱신하면서
bluetape4k 조직 전체에 버전 드리프트가 발생했다.

## 결정

공유 의존성 버전은 먼저 `bluetape4k-dependencies`에서 변경한 뒤
`sync-shared-versions.py`로 이 저장소에 반영한다. 또한 이 저장소의 Dependabot은
중앙에서 관리하는 의존성 이름을 무시하도록 설정해 이후 PR이 중앙 기준
데이터 원본으로 향하도록 한다.

## 결과

로컬 버전 카탈로그와 `.github/dependabot.yml`이 이제 중앙 의존성 거버넌스
정책을 따른다.

## 검증

- 이 저장소에서 `sync-shared-versions.py --write --check --summary` 실행
- 이 저장소에서 `sync-dependabot-ignores.py --write --check --summary` 실행
- `git diff --check`

## 향후 지침

중앙에서 관리하는 의존성에 대한 저장소 로컬 Dependabot PR은 병합하지 않는다.
`bluetape4k-dependencies`를 먼저 갱신한 다음 이 저장소를 동기화한다.
