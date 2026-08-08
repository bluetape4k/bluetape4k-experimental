# README 히어로 및 아키텍처 새로 고침

## 배경

실험 저장소의 README가 한국어로만 작성되어 있어 안정화된 사용자 대상
English README와 한국어 현지화 문서를 명확히 구분하지 못했다.

## 결정

생성한 experimental workbench 이미지를
`docs/assets/experimental-workbench.png`에 저장하고 `README.md`는 English로
작성하며, 같은 구조의 `README.ko.md`를 추가한다.

## 결과

root README는 이제 두 언어에서 실험 검증장으로서의 역할, 모듈 그룹,
아키텍처, 모듈 범위 검증 규칙을 설명한다.

## 검증

- 생성한 asset이 `docs/assets` 아래 PNG로 존재하는지 확인했다.
- 두 README locale이 공유 이미지 경로를 참조하는지 검증했다.

## 향후 지침

실험 작업은 이슈와 모듈 범위에 맞춰 진행하고, 문서화된 동작만 안정화된
bluetape4k 저장소로 승격한다.
