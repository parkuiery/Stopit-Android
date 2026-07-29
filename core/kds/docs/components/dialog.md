# KeepDialog

Source: `KeepDialog.kt`

## SEED references

- [Alert Dialog](https://seed-design.io/llms/components/alert-dialog.txt)
- [Color roles](https://seed-design.io/llms/foundations/color/color-role.txt)

## Usage

wheel picker처럼 alert보다 구조화된 modal content가 필요할 때 사용하는 low-level shell입니다.
단순 확인은 `KeepConfirmationDialog`를 우선합니다.

## Anatomy

scrim, floating container, caller-owned content로 구성합니다.

## Properties and states

onDismiss, properties, shape, width constraint와 modal focus state를 관리합니다.

## StopIt adaptation

`layerFloating`, 20dp radius와 272dp 기본 최대 폭을 Alert Dialog와 공유합니다.

## Accessibility

modal 밖 탐색을 차단하고 닫기 경로, title/label 및 예측 가능한 focus 순서를 제공합니다.

## Verification

작은/큰 화면 폭, back/outside dismiss 정책, TalkBack modal focus와 dark surface를 검사합니다.

