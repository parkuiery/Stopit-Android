# Keep top app bars

Source: `KeepTopAppBar.kt`

## SEED references

- [Top Navigation](https://seed-design.io/llms/components/top-navigation.txt)

## Usage

화면 제목, navigation action과 소수의 utility action을 제공하는 screen-level header에 사용합니다.

## Anatomy

navigation slot, title, action slots로 구성하며 centered title이 필요할 때 별도 API를 사용합니다.

## Properties and states

`KeepTopAppBar`, `KeepCenterAlignedTopAppBar`, default/scrolled 상태를 제공합니다.

## StopIt adaptation

모든 top bar는 `layerBasement`를 사용해 화면 canvas와 이어지며 icon/title은 neutral foreground입니다.

## Accessibility

navigation과 action icon에 localized description을 제공하고 title을 명확한 화면명으로 작성합니다.

## Verification

뒤로가기/메뉴/닫기, 긴 title, action 0~2개, scrolled state, inset과 TalkBack 순서를 검사합니다.
