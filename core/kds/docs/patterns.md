# KDS patterns

## SEED references

- [Patterns index](https://seed-design.io/patterns/llms.txt)
- [Loading](https://seed-design.io/llms/patterns/loading.txt)
- [Result Section](https://seed-design.io/llms/components/result-section.txt)
- [Skeleton](https://seed-design.io/llms/components/skeleton.txt)
- [Snackbar](https://seed-design.io/llms/components/snackbar.txt)

## Loading

- 짧은 로컬 작업은 화면 구조를 유지하고 필요한 액션에만 진행 상태를 표시합니다.
- 시간이 예측되지 않는 작업은 indeterminate progress semantics를 제공합니다.
- 목록/카드가 원격 데이터에 의존할 때만 실제 콘텐츠 구조를 닮은 Skeleton을 고려합니다.
- 로딩 중 중복 제출을 막되 사용자가 현재 상태와 취소 가능 여부를 이해할 수 있어야 합니다.

## Empty, result, and error

- 빈 상태는 제목, 원인 또는 맥락, 가능한 다음 행동 순서로 구성합니다.
- 성공·완료 화면은 결과와 다음 행동을 먼저 보여주며 장식 자산은 선택 사항입니다.
- 오류는 재시도 가능 여부와 해결 방법을 명시하고 snackbar 하나에 복구 절차를 숨기지 않습니다.

## Feedback selection

- 현재 작업을 막고 명시적 결정이 필요하면 `KeepAlertDialog`를 사용합니다.
- 현재 맥락을 유지한 추가 설정/선택은 `KeepModalBottomSheet`를 사용합니다.
- 현재 화면의 보조 액션 목록은 `KeepMenu`를 사용하고, 삭제 항목은 Critical tone과
  확인 다이얼로그를 연결합니다.
- 작업 결과를 잠시 알리는 경우 `KeepSnackbarHost`를 사용합니다.
- 화면 전체 상태나 지속 안내는 feature-owned section으로 구성하되 KDS primitives를 사용합니다.

## Form bottom sheet

- [SEED Bottom Sheet](https://seed-design.io/llms/components/bottom-sheet.txt)의
  Header/Content/Footer 구조를 사용합니다.
- 여러 값을 작성하고 마지막 CTA에서 저장하는 form은 제목·설명·닫기 action을 제공하고
  실수로 닫힐 수 있는 drag handle은 제거합니다.
- 본문만 스크롤하며 header와 footer CTA는 고정합니다. 키보드가 열리면 footer는 IME
  위에 유지되어야 합니다.
- 직접 입력은 `KeepTextField`, picker나 선택 화면 진입은 `KeepInputButton`을
  `KeepField` 안에서 사용합니다.
- 시간처럼 스크롤 중 값이 계속 바뀌는 picker는 임시 선택값을 유지하고 `Apply` 이후에만
  form state를 갱신합니다.
- 제출 시 반영되는 선택값에는 즉시 적용을 뜻하는 switch를 사용하지 않습니다.
