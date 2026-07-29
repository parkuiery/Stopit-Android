# KeepSelectableCard

Source: `KeepSelectableCard.kt`

## SEED references

- [Select Box](https://seed-design.io/llms/components/select-box.txt)
- [Radio](https://seed-design.io/llms/components/radio.txt)

## Usage

설명과 보조 콘텐츠가 있는 단일 선택 항목에 사용합니다. 다중 선택에는 checkbox를 사용합니다.

## Anatomy

selectable card surface, radio indicator, title, description, optional supporting content입니다.

## Properties and states

selected/unselected, enabled/disabled 상태를 지원하며 선택 시 `BrandWeak`, 기본은
`LayerDefault` surface를 사용합니다.

## StopIt adaptation

SEED Select Box 구조를 StopIt의 12dp card와 KDS radio semantics로 조합합니다.

## Accessibility

전체 card가 하나의 radio target이 되며 title과 description이 의미 있는 순서로 읽혀야 합니다.

## Verification

selection role/state, 전체 터치 영역, disabled content, 긴 description과 font scale을 검사합니다.

