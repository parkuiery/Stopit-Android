# KeepTextButton

Source: `KeepTextButton.kt`

## SEED references

- [Action Button](https://seed-design.io/llms/components/action-button.txt)

## Usage

toolbar, inline, 저강조 보조 동작에 사용합니다. 화면의 primary CTA 대체로 사용하지 않습니다.

## Anatomy

투명 container와 text/icon content로 구성합니다.

## Properties and states

- Variants: `Neutral`, `Muted`, `Brand`, `Critical`
- `Muted`는 primary CTA와 나란히 놓이는 보조 동작에 사용합니다. `foreground.muted`로 위계를
  낮추되 본문 대비를 유지합니다.
- States: enabled, pressed, disabled
- 최소 높이 44dp와 수평 12dp padding을 유지합니다.

## StopIt adaptation

SEED Ghost action 역할을 Compose `TextButton` 기반 KDS API로 제공합니다.

## Accessibility

아이콘만 제공하지 말고 읽을 수 있는 label 또는 content description을 포함합니다.

## Verification

variant별 대비, disabled 상태, 긴 label과 44dp 이상 터치 영역을 검사합니다.
