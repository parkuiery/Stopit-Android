# Primary Color Hierarchy Audit

이 문서는 #468의 docs/ops 기준선이다. 목표는 `#FFA927` / `KeepTheme.colors.primary`를 없애는 것이 아니라, 사용자가 화면에서 “지금 가장 중요한 행동”을 더 빨리 찾도록 primary의 역할을 좁히는 것이다.

## 판단 원칙

### Primary를 유지하는 경우

Primary는 아래처럼 사용자 가치나 현재 상태를 직접 설명하는 표면에 집중한다.

- 단일 primary CTA: 시작, 저장, 계속, 확인, 선택 완료
- 현재 선택 상태: 선택된 탭, 날짜, 요일, chip, filter
- 활성/진행 상태: 현재 잠금, 루틴 실행 중, 카운트다운, 진행률
- 성과 강조: 차단 시간, 상위 순위, 집중/반복 성과 중 제품적으로 중요한 숫자
- 로딩/처리 중 상태: primary action과 연결된 progress indicator

### Primary를 낮춰야 하는 경우

아래 표면은 기본적으로 `onSurface`, `surfaceVariant`, `onTertiaryContainer`, 또는 container/border/shape 중심 표현을 우선한다.

- TopAppBar 뒤로가기, 메뉴, 닫기 같은 navigation icon
- 단순 추가/삭제/편집 icon button
- secondary navigation action
- 보조 설명/metadata 텍스트
- 위험/긴급/파괴 동작: `error` 또는 별도 confirmation 패턴을 사용하고 primary로 위장하지 않는다

### 색상 단독 금지

선택/활성 상태는 색상만으로 전달하지 않는다. 다음 중 하나 이상을 함께 둔다.

- 텍스트 라벨 또는 count
- badge / chip / pill shape
- border / background container
- contentDescription 또는 semantics가 있는 상태 설명
- 선택된 항목의 위치/레이아웃 차이

## 현재 코드 상태

2026-06-05 docs-lane 감사(PR #474)에서는 `KeepTheme.colors.primary`가 제품 상태/선택과 navigation action에 섞여 있었다. 이후 2026-06-06 code-lane PR #546(`9b99cafc`)이 `develop`에 merge되어 핵심 navigation/action icon의 primary 과강조를 실제 코드에서 낮췄다.

| 화면/파일 | PR #474 감사 당시 표면 | PR #546 이후 상태 | 남은 경계 |
| --- | --- | --- | --- |
| `HomeScreen.kt` | 메뉴 IconButton tint가 primary | 메뉴 icon을 lower-emphasis token으로 낮추고 Home의 시작/타이머/선택 CTA primary는 유지 | 실제 기기 visual QA에서 Home primary CTA가 충분히 강한지 확인 |
| `LockHistoryScreen.kt` | 뒤로가기 tint가 primary | back icon을 lower-emphasis token으로 낮추고 선택 날짜/성과 숫자 primary는 유지 | 성과 리포트(#465) UI와 함께 visual QA 확인 |
| `BlockedAppsScreen.kt` | 뒤로가기 tint가 primary | back icon을 낮추고 rank/성과 강조는 제품 가치 강조로 유지 | history 하위 화면 QA에서 rank 강조가 navigation보다 강한지 확인 |
| `RoutineScreen.kt` | 뒤로가기와 추가 icon이 모두 primary | back icon은 낮추고 add icon은 화면의 주요 생성 action으로 primary 유지 | add가 icon-only라 비색상 cue/contentDescription이 유지되는지 확인 |
| `EmergencyUnlockSettingsScreen.kt` | 뒤로가기 tint가 primary | back icon을 낮추고 설정 값/선택 상태 primary는 유지 | emergency unlock 설정 화면 visual/TalkBack QA 확인 |
| `GoalLockCreationScreen.kt`, `GoalLockDetailScreen.kt`, `RoutineBottomSheetContent.kt`, `MenuScreen.kt`, `DevToolScreen.kt` | 이후 기능/진단 화면에서도 primary/raw orange navigation icon drift 가능 | PR #546의 정적 회귀 테스트가 navigation icon primary/raw orange 금지 snippet을 포함 | 새 화면 추가 시 `scripts.tests.test_design_primary_color_hierarchy`에 금지 snippet을 확장 |
| `EmergencyUnlockBottomSheetContent.kt` | duration/active/selected/progress primary | 선택/진행 상태라 유지 | 색상 외 chip/background/label 보완이 유지되는지 QA |
| `RoutineDayContent.kt`, `LockHistoryTab.kt`, `LockHistoryWeekCalendar.kt` | 선택 상태 primary | 선택 상태라 유지 | 선택 shape/border/text/semantics가 함께 유지되는지 QA |

따라서 #468은 더 이상 “문서 계약만 있고 구현 전” 상태가 아니다. repo-internal 기준은 `DESIGN.md`/KDS 계약 + PR #546 code diff + 정적 회귀 테스트까지 들어온 상태이며, 남은 것은 실제 기기 visual QA, 릴리즈 포함 여부, 사용자 노출 후 확인이다.

## 화면별 적용 순서

1. **TopAppBar navigation/action icon 정리**
   - back/menu/close/delete는 기본적으로 lower-emphasis icon color로 낮춘다.
   - 화면의 주 action이 icon-only add라면 primary 유지 여부를 화면별로 판단하고, contentDescription/empty-state CTA와 충돌하지 않게 한다.
2. **선택/활성 상태 보조 신호 확인**
   - tab/day/chip/selected row는 primary + shape/border/background/text 중 하나 이상을 유지한다.
3. **성과/진행 강조 정리**
   - Lock/History의 기간, rank, active routine 상태는 제품 가치를 설명할 때만 primary를 유지한다.
4. **KDS와 app 문서 동기화**
   - KDS README는 token의 의미와 사용 금지 표면을 설명한다.
   - 루트 `DESIGN.md`는 app 화면 판단 원칙과 변경 승인 기준을 설명한다.

## 완료/검증 기준

#468 repo-internal 기준은 PR #474 + PR #546 이후 아래 상태까지 충족됐다.

- `DESIGN.md`와 `core/kds/README.md`가 primary 사용/비사용 기준을 동일하게 설명한다.
- 주요 화면의 navigation icon과 primary CTA/선택/활성 상태 위계가 실제 코드에서 분리됐다.
- 선택/활성 상태는 색상 외 텍스트, 배지, shape, border, contentDescription 중 하나 이상과 함께 유지한다.
- raw `#FFA927`/raw `Color(0xFFFFA927)`를 새로 추가하지 않고 `KeepTheme.colors` 또는 KDS token을 사용한다.
- PR #546 current-head에서 `Fast verification`, `Runtime smoke gate`, `Branch Hygiene`가 통과했다.
- `scripts/tests/test_design_primary_color_hierarchy.py`가 핵심 navigation icon primary/raw orange drift를 정적으로 막는다.

남은 경계는 repo-internal 문서/코드가 아니라 실제 기기 visual QA, 다음 릴리즈 반영, 사용자 노출 후 확인이다. 따라서 추가 docs-only PR은 `Refs #468`로 두고, visual QA/릴리즈 evidence까지 확보한 뒤 이슈 closure를 판단한다.
