# README 다이어그램 인포그래픽

## 배경

README 파일은 아키텍처, 클래스, 시퀀스, ERD 및 기타 다이어그램에 Mermaid
코드 블록을 사용했다. 작업 공간 전체의 시각적 방향이 검토된 파스텔
인포그래픽 PNG와 재사용을 위해 보관하는 SVG 소스 asset으로 변경되었다.

## 결정

README의 Mermaid 블록을 생성한 PNG 이미지 링크로 바꾸고, 대응하는 SVG
소스를 PNG 파일 옆에 저장한다. 다이어그램 텍스트는 English만 사용하고,
큰 라벨에는 Architects Daughter, 상세 텍스트에는 Comic Mono를 사용하며,
아키텍처·클래스·시퀀스·ERD 다이어그램에는 다이어그램별 레이아웃을
적용한다.

## 결과

bluetape4k.github.io/docs/readme-diagram-samples의 공유 2026-05-19 스타일
가이드에 따라 README 다이어그램을 렌더링했다. root README asset은 존재하는
경우 저장소 로컬 asset 배치 규칙을 따른다.

## 검증

저장소 간 변환 과정에서 rsvg-convert로 PNG/SVG asset을 생성하고 README
링크를 확인했다.

## 향후 지침

README 다이어그램은 PNG를 embed하고 편집을 위한 SVG 소스를 함께 유지한다.
시각적 일관성이 중요할 때 raw Mermaid나 단순한 Mermaid 테마 색상 변경으로
되돌리지 않는다.
