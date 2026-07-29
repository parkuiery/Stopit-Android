# KeepChip

Source: `KeepChip.kt`

## SEED references

- [Chip](https://seed-design.io/llms/components/chip.txt)

## Usage

짧은 preset, filter, compact action 또는 선택값을 표현합니다. 긴 문장이나 primary CTA에는
사용하지 않습니다.

## Anatomy

container, optional leading content, 한 줄 label로 구성합니다.

## Properties and states

- Variants: `Solid`, `OutlineStrong`, `OutlineWeak`
- Sizes: `Small`, `Medium`, `Large`
- Roles: `Action`, `Toggle`, `Radio`
- enabled/disabled, selected/unselected, pressed 상태를 검토합니다.

## StopIt adaptation

선택 강조는 StopIt brand 역할을 사용하되 role에 맞는 semantics를 함께 제공합니다.

## Accessibility

`Toggle`은 selected state, `Radio`는 radio role을 노출하고 색상만으로 선택을 표현하지 않습니다.

## Verification

role별 semantics, 긴 label, leading content, selected/disabled 조합 및 그룹 간격 8dp를 검사합니다.

