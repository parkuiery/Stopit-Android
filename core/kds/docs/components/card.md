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

- `readOnly`는 누를 수 없지만 내용은 계속 읽혀야 하는 카드에 사용합니다. variant container를 유지해
  카드가 화면 캔버스에 묻히지 않게 하고, 조작 불가는 `foreground.muted`와 주변 아이콘으로 전달합니다.
  `disabled`는 카드가 담은 정보까지 물러나도 되는 경우에만 사용합니다.

Variants: `LayerDefault`, `NeutralWeak`, `NeutralMuted`, `BrandWeak`, `BrandSolid`,
`CriticalWeak`. 기본 radius 12dp, elevation 0dp, optional neutral stroke입니다.

## StopIt adaptation

SEED v3에는 범용 Card 항목이 없으므로 layer/color 역할로 구성한 KDS 전용 surface입니다.

## Accessibility

클릭 카드는 `onClick` overload를 사용하고 `Modifier.clickable`을 중복 적용하지 않습니다.

## Verification

basement와의 분리, content 대비, clickable/disabled semantics 및 중첩 여부를 검사합니다.
