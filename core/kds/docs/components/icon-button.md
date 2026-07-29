# KeepIconButton

Source: `KeepIconButton.kt`

## SEED references

- [Action Button](https://seed-design.io/llms/components/action-button.txt)
- [Iconography usage](https://seed-design.io/llms/foundations/iconography/usage.txt)

## Usage

뒤로가기, 닫기, 메뉴, 공유처럼 아이콘만으로 충분히 익숙한 단일 동작에 사용합니다.

## Anatomy

48dp 이상 interaction container와 중앙 icon으로 구성합니다.

## Properties and states

enabled, pressed, disabled 상태를 지원합니다. 기능의 위계는 icon tint가 아닌 주변
context와 적절한 action variant로 표현합니다.

## StopIt adaptation

navigation과 일반 utility icon은 neutral foreground를 사용하며 기본 amber tint를 금지합니다.

## Accessibility

기능을 설명하는 localized `contentDescription`을 icon에 반드시 제공합니다.

## Verification

터치 영역, ripple, TalkBack label, disabled tint 및 light/dark 대비를 검사합니다.

