# KeepSnackBar

Source: `KeepSnackBar.kt`

## SEED references

- [Snackbar](https://seed-design.io/llms/components/snackbar.txt)

## Usage

사용자 행동의 짧은 결과나 복구 가능한 상태를 일시적으로 알립니다. 필수 결정이나 긴 설명에는
사용하지 않습니다.

## Anatomy

neutral-inverted container와 한 줄 또는 짧은 message로 구성합니다.

## Properties and states

showing/dismissed 상태는 host가 관리합니다. 현재 KDS는 action 없는 message variant를 제공합니다.

## StopIt adaptation

8dp radius, 10dp padding, 14sp/19sp message와 inverted semantic colors를 사용합니다.

## Accessibility

message가 자동으로 announcement되고 읽기 전에 사라지지 않도록 적절한 duration을 선택합니다.

## Verification

긴 message wrap, 여러 snackbar queue, TalkBack announcement, light/dark 대비를 검사합니다.

