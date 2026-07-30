# KeepDivider

Source: `KeepDivider.kt`

## SEED references

- [Divider](https://seed-design.io/llms/components/divider.txt)

## Usage

spacing만으로 구분이 부족한 동급 콘텐츠 영역 사이에 사용합니다. 모든 row 사이에 습관적으로
넣거나 card를 중첩하는 대신 사용하지 않습니다.

## Anatomy

한 개의 semantic stroke line으로 구성합니다.

## Properties and states

방향과 두께는 현재 horizontal 1dp 기본이며 색상은 `stroke.neutralWeak`로 고정합니다.

## StopIt adaptation

feature는 divider color를 재정의하지 않고 spacing과 placement만 결정합니다.

## Accessibility

장식 요소로 취급하며 별도 TalkBack node를 만들지 않습니다.

## Verification

light/dark 대비, 인접 spacing과 불필요한 중복 divider를 검사합니다.
