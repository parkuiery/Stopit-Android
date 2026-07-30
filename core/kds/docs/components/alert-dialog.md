# KeepAlertDialog and KeepConfirmationDialog

Source: `KeepAlertDialog.kt`

## SEED references

- [Alert Dialog](https://seed-design.io/llms/components/alert-dialog.txt)

## Usage

사용자의 명시적 결정이 필요하고 현재 작업을 중단해야 할 때만 사용합니다. 정보 탐색이나 복잡한
입력에는 bottom sheet 또는 screen을 사용합니다.

## Anatomy

optional icon, title, description, 최대 두 action으로 구성합니다.

## Properties and states

confirm/dismiss action, optional content, `Brand`/`Neutral`/`Critical` confirm tone을 제공합니다.
긴 action label은 세로로 stack합니다.

## StopIt adaptation

272dp 최대 폭, 20dp radius/padding, action gap 8dp, 기본 outside-dismiss 비활성 규칙을 사용합니다.

## Accessibility

title과 description을 명확히 제공하고 초기 focus와 action 순서를 안전한 선택부터 구성합니다.

## Verification

한/두 action, 긴 번역 stack, critical tone, back/outside dismiss 및 TalkBack focus를 검사합니다.
