# Keep date and time picker

Source: `KeepDateTimePicker.kt`

## SEED references

- [Field](https://seed-design.io/llms/components/field.txt)
- [Input Button](https://seed-design.io/llms/components/input-button.txt)

## Usage

날짜 선택 dialog와 직접 시간 입력에 사용합니다. 값 선택 화면을 여는 entry는 input button
형태로 제공하고 선택된 값을 label에 표시합니다.

## Anatomy

date dialog는 calendar와 confirm/dismiss action, time input은 hour/minute field와 label로 구성합니다.

## Properties and states

initial/selected value, validation error, enabled/disabled와 confirm/dismiss 상태를 검토합니다.

## StopIt adaptation

플랫폼 date picker 동작은 유지하되 action, color, field surface를 KDS semantic roles로 감쌉니다.

## Accessibility

현지화된 날짜·시간 읽기, 12/24시간 설정, error 설명과 logical focus order를 제공합니다.

## Verification

locale, 12/24시간, invalid value, font scale, dialog action과 TalkBack reading을 검사합니다.

