# KeepLabel

Source: `KeepLabel.kt`

## SEED references

- [Field](https://seed-design.io/llms/components/field.txt)
- [Typography](https://seed-design.io/llms/foundations/typography.txt)

## Usage

field label, metadata, caption, validation label처럼 반복되는 작은 텍스트 위계에 사용합니다.
일반 본문과 화면 제목에는 Material typography role을 직접 사용합니다.

## Anatomy

한 줄 또는 caller-defined line count의 semantic text로 구성합니다.

## Properties and states

- Tones: `Neutral`, `Muted`, `Brand`, `Critical`
- Sizes: `Small`, `Medium`, `Large`
- Weights: `Regular`, `Strong`

## StopIt adaptation

SEED text role을 StopIt의 Compose typography와 semantic foreground에 매핑합니다.

## Accessibility

작은 글꼴은 충분한 대비와 strong weight를 사용하고 validation은 색상 외 문구로 전달합니다.

## Verification

tone/size/weight, font scale, 긴 label wrapping, disabled/error context 대비를 검사합니다.
