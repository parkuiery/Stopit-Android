# KeepSwitch

Source: `KeepSwitch.kt`

## SEED references

- [Switch](https://seed-design.io/llms/components/switch.txt)

## Usage

별도 저장 동작 없이 즉시 적용되는 독립 설정에 사용합니다. 제출 시 반영되는 복수 선택에는
`KeepCheckbox`를 사용합니다.

## Anatomy

capsule track과 animated thumb로 구성하며, interaction root는 48dp 이상이면서 track보다
좁지 않습니다. root를 48dp로 고정하면 그보다 넓은 `Large` track(52dp)이 눌리는데, thumb
위치는 눌리기 전 track 폭으로 계산되어 선택 상태에서 thumb가 오른쪽 끝에 붙습니다.

## Properties and states

- Sizes: `Small` 26×16, `Medium` 38×24, `Large` 52×32
- selected/unselected, enabled/disabled, pressed/focused 상태
- thumb 이동 150ms, track color 전환 50ms와 20ms delay
- thumb는 SEED Switchmark의 `scale` 토큰을 따라 unselected에서 0.8배로 그립니다. 크기가
  아니라 배율만 바뀌므로 track과의 시각 여백은 unselected가 selected보다 넓습니다
  (`Large` 기준 약 5.6dp 대 3dp).

## StopIt adaptation

selected track은 StopIt brand solid를 사용합니다. 흰 thumb, 무테 track, disabled opacity 38%는
SEED Switchmark 규격을 유지합니다.

## Accessibility

`Role.Switch`와 checked state를 노출하며 시각 크기와 관계없이 48dp touch target을 유지합니다.

## Verification

세 size의 치수 contract, 선택 애니메이션, disabled opacity, TalkBack state와 light/dark를 검사합니다.
