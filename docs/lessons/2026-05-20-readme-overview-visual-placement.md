# 2026-05-20 — README 개요 시각 배치

## 배경

README 다이어그램과 차트는 장식용으로 생성한 asset이 아니라 소스가 뒷받침하는
문서로 다뤄야 한다. 이번 작업에서는 2026년 기준 문서와 공유 README
다이어그램 스타일 가이드를 사용했지만, 모듈 이름과 그룹화의 기준은 여전히
소스 코드와 빌드 레이아웃으로 삼았다.

## 결정

root README에 English-only SVG+PNG 개요 시각 자료를 추가하고, 설치·사용·빌드
지침보다 개요 다이어그램을 먼저 배치한다. 기존 Architecture/Diagram 섹션이
사용 예제 뒤에 추가되어 있다면 위로 이동한다.

## 결과

`bluetape4k-experimental`에는 이제 root README 개요 다이어그램과 모듈 구성
차트가 있으며, README 시각 자료 배치는 개요 우선 규칙을 따른다. 생성한
라벨에는 이미지 내부의 현지화 텍스트를 사용하지 않는다.

## 검증

- 생성한 SVG 파일을 `xmllint --noout`으로 파싱했다.
- 생성한 PNG 파일을 `rsvg-convert`로 렌더링했다.
- 작업 공간 README 이미지 링크 검사에서 누락된 로컬 이미지가 0건으로
  보고되었다.
- 작업 공간 Architecture/Diagram 순서 검사에서 Installation, Usage,
  Examples, Build heading 뒤에 남은 섹션이 0건으로 보고되었다.
- 생성한 root overview SVG 텍스트에 non-ASCII 문자가 없었다.

## 향후 참고 사항

README 파일 끝에 아키텍처 다이어그램을 덧붙이지 않는다. 개요 또는
아키텍처 다이어그램을 상단 가까이에 배치한 뒤, 클래스·시퀀스·ERD·flow
다이어그램은 설명하는 섹션 옆에 둔다.

root 개요 다이어그램과 구성 차트는 BOM이 있으면 BOM을 앞에 배치하고,
Examples 또는 Additional examples가 있으면 마지막에 배치한다. 중간 그룹은
저장소별 README에서 알파벳순 그룹화를 요구하지 않는 한 소스가 뒷받침하는
방향 순서를 유지한다.
