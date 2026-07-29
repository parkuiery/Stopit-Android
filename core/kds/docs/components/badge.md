# KeepBadge

Source: `KeepBadge.kt`

## SEED references

- [Badge](https://seed-design.io/llms/components/badge.txt)

## Usage

객체의 속성이나 상태를 짧게 표시합니다. action이나 toggle 용도로 사용하지 않습니다.

## Anatomy

container, optional leading content, 한 줄 label로 구성합니다.

## Properties and states

- Tones: `Neutral`, `Brand`, `Critical`
- Variants: `Weak`, `Solid`, `Outline`
- Sizes: `Medium`, `Large`
- display-only이며 clickable state를 제공하지 않습니다.

## StopIt adaptation

Brand tone은 StopIt amber 역할을 사용하고 잠금·루틴 상태는 label과 함께 표시합니다.

## Accessibility

색상만으로 상태를 구분하지 않으며 label이 주변 문맥과 합쳐져 의미를 전달해야 합니다.

## Verification

tone/variant 대비, 한 줄 ellipsis, leading icon 색상 및 긴 현지화 label을 검사합니다.

