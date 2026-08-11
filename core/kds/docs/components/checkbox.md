# KeepCheckbox

Source: `KeepCheckbox.kt`

## SEED references

- [Checkbox](https://seed-design.io/llms/components/checkbox.txt)
- [Switch](https://seed-design.io/llms/components/switch.txt)

## Usage

여러 항목 중 하나 이상을 선택하거나 제출 시 함께 저장되는 선택에 사용합니다. 즉시 설정이
적용되는 독립 항목에는 `KeepSwitch`를 사용합니다.

## Anatomy

control container와 check mark로 구성하며 label은 부모 row/field가 제공합니다.

## Properties and states

checked/unchecked, enabled/disabled, pressed/focused 상태를 지원합니다.

## StopIt adaptation

checked container는 StopIt brand solid, mark는 on-brand를 사용합니다.

## Accessibility

label과 하나의 target으로 묶고 checked state와 disabled reason을 TalkBack에서 이해할 수 있어야 합니다.

## Verification

role/state, label click, disabled contrast, 48dp row target과 font scaling을 검사합니다.
