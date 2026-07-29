# KeepModalBottomSheet

Source: `KeepModalBottomSheet.kt`

## SEED references

- [Bottom Sheet](https://seed-design.io/llms/components/bottom-sheet.txt)

## Usage

현재 화면의 맥락을 유지하면서 추가 선택, 설정 또는 짧은 상세 정보를 제공할 때 사용합니다.
확인이 강제되는 경고에는 Alert Dialog를 사용합니다.

## Anatomy

scrim, sheet container, drag handle, caller-owned header/content, safe-area inset으로 구성합니다.

## Properties and states

expanded/partially expanded/dismissed 상태, drag/back/outside dismiss와 content scrolling을 검토합니다.

## StopIt adaptation

24dp top radius, 0dp tonal elevation, `layerSheet`, semantic overlay와 최대 폭을 KDS가 고정합니다.

## Accessibility

drag handle에 설명을 제공하고 동일한 닫기 동작을 back 또는 명시적 action으로도 수행할 수 있어야 합니다.

## Verification

긴 content scrolling, IME, system inset, light/dark, dismiss 경로와 TalkBack focus를 검사합니다.

