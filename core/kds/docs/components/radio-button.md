# KeepRadioButton

Source: `KeepRadioButton.kt`

## SEED references

- [Radio](https://seed-design.io/llms/components/radio.txt)

## Usage

서로 배타적인 여러 옵션 중 하나를 선택할 때 사용합니다. 독립적인 on/off 설정에는 사용하지 않습니다.

## Anatomy

outer control, selected indicator로 구성하며 label과 group은 부모가 제공합니다.

## Properties and states

selected/unselected, enabled/disabled, pressed/focused 상태를 지원합니다.

## StopIt adaptation

selected indicator와 stroke는 StopIt brand semantic role을 사용합니다.

## Accessibility

동일 그룹의 radio가 논리적으로 묶이고 현재 selected state와 label이 함께 읽혀야 합니다.

## Verification

role/state, group 탐색, label target, disabled 대비 및 48dp interaction row를 검사합니다.

