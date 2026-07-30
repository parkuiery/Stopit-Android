# Menu

Source: `KeepMenu.kt`

## SEED references

- [SEED Menu](https://seed-design.io/components/menu)

## Usage

- 화면의 현재 맥락을 유지한 채 보조 액션 목록을 제공할 때 사용합니다.
- 모바일에서는 `KeepMenu`의 Medium 규격을 사용합니다.
- 삭제처럼 되돌릴 수 없는 액션은 `KeepMenuItemTone.Critical`로 표시하고 확인
  다이얼로그를 이어서 제공합니다.

## Anatomy

- `KeepMenu`: trigger에 연결되는 floating container
- `KeepMenuItem`: label, optional description, optional prefix content
- 앱 화면의 `KeepIconButton` 등이 trigger를 소유합니다.

## Properties and states

- Medium width는 240dp, radius는 16dp입니다.
- Item padding은 가로 16dp, 세로 12dp이며 prefix와 본문 간격은 12dp입니다.
- Item tone은 `Neutral`, `Critical`을 제공합니다.
- Enabled, pressed, disabled 상태는 semantic token으로 표현합니다.

## StopIt adaptation

- Compose `DropdownMenu`는 위치 계산과 화면 경계 보정을 담당하지만 앱 feature에서는
  Material Menu API를 직접 사용하지 않습니다.
- 단일 삭제 액션도 직접 노출해 폼 CTA와 경쟁시키지 않고, 더보기 trigger 아래의
  Critical menu item으로 제공합니다.

## Accessibility

- 각 menu item은 Button role과 최소 콘텐츠 padding을 제공합니다.
- 아이콘은 label과 함께 사용하며 장식 아이콘의 content description은 비웁니다.
- Critical 의미를 색상에만 의존하지 않고 명확한 동사형 label로 전달합니다.

## Verification

- `./gradlew :core:kds:testDebugUnitTest`
- `./gradlew :core:kds:assembleDebug`
- 큰 글꼴, 다국어 label, trigger 주변 배치, 화면 가장자리에서의 위치 보정을 확인합니다.
