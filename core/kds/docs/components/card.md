# KeepCard

Source: `KeepCard.kt`

## SEED references

- [Color roles](https://seed-design.io/llms/foundations/color/color-role.txt)
- [State](https://seed-design.io/llms/foundations/state.txt)

## Usage

관련 콘텐츠를 하나의 표면으로 묶을 때 사용합니다. 선택 컨트롤은 `KeepSelectableCard`를
우선 사용하고 card를 중첩하지 않습니다.

## Anatomy

semantic container, optional stroke, content column으로 구성합니다. clickable overload가
상호작용 semantics와 pressed feedback을 소유합니다.

## Properties and states

Variants: `LayerDefault`, `NeutralWeak`, `NeutralMuted`, `BrandWeak`, `BrandSolid`,
`CriticalWeak`. 기본 radius 12dp, elevation 0dp, optional neutral stroke입니다.

## StopIt adaptation

SEED v3에는 범용 Card 항목이 없으므로 layer/color 역할로 구성한 KDS 전용 surface입니다.

## Accessibility

클릭 카드는 `onClick` overload를 사용하고 `Modifier.clickable`을 중복 적용하지 않습니다.

## Verification

basement와의 분리, content 대비, clickable/disabled semantics 및 중첩 여부를 검사합니다.
