# KeepSwitch

Source: `KeepSwitch.kt`

## SEED references

- [Switch](https://seed-design.io/llms/components/switch.txt)

## Usage

별도 저장 동작 없이 즉시 적용되는 독립 설정에 사용합니다. 제출 시 반영되는 복수 선택에는
`KeepCheckbox`를 사용합니다.

## Anatomy

48dp interaction root 안의 capsule track과 animated thumb로 구성합니다.

## Properties and states

- Sizes: `Small` 26×16, `Medium` 38×24, `Large` 52×32
- selected/unselected, enabled/disabled, pressed/focused 상태
- thumb 이동 150ms, track color 전환 50ms와 20ms delay

## StopIt adaptation

selected track은 StopIt brand solid를 사용합니다. 흰 thumb, 무테 track, disabled opacity 38%는
SEED Switchmark 규격을 유지합니다.

## Accessibility

`Role.Switch`와 checked state를 노출하며 시각 크기와 관계없이 48dp touch target을 유지합니다.

## Verification

세 size의 치수 contract, 선택 애니메이션, disabled opacity, TalkBack state와 light/dark를 검사합니다.

