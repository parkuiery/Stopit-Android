# KeepInputButton

Source: `KeepInputButton.kt`

## SEED references

- [Input Button](https://seed-design.io/llms/components/input-button.txt)
- [Field](https://seed-design.io/llms/components/field.txt)

## Usage

선택 목록, 바텀시트, 날짜·시간 picker를 여는 입력형 버튼입니다. 직접 타이핑하는 값에는
`KeepTextInput`을 사용하며 input button을 단독으로 사용하지 않습니다.

## Anatomy

52dp container, value 또는 placeholder, optional leading/trailing slot으로 구성합니다.
필요한 경우 `KeepField`의 Input slot에 넣어 label과 helper/error를 제공합니다.

## Properties and states

- large mobile size: 높이 52dp, 좌우 padding 16dp, radius 12dp
- enabled, pressed, error, disabled, read-only
- value가 없으면 `foreground.placeholder`, 있으면 `foreground.neutral`
- 기본 stroke는 `neutralWeak`, error는 2dp `criticalSolid`

## StopIt adaptation

StopIt semantic palette를 사용하고 picker·앱 선택·루틴 보호 시간처럼 직접 편집하지 않는
값에 공통 적용합니다. Material Button의 채움 배경은 사용하지 않습니다.

## Accessibility

버튼 role과 현지화된 label/value를 함께 읽을 수 있어야 합니다. trailing 화살표는
장식이므로 중복 content description을 제공하지 않습니다.

## Verification

52dp geometry, empty/value, pressed/error/disabled/read-only, 긴 현지화 label, TalkBack
button role과 picker focus 복귀를 검사합니다.
