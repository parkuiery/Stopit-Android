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

2026-06-05 docs-lane 감사(PR #474)에서는 `KeepTheme.colors.primary`가 제품 상태/선택과 navigation action에 섞여 있었다. 이후 2026-06-06 code-lane PR #546(`9b99cafc`)이 `develop`에 merge되어 핵심 navigation/action icon의 primary 과강조를 실제 코드에서 낮췄다. 2026-06-12 PR #804(`a5cb5fb4`)는 Home/Menu UI와 Goal Lock·Parent Mode setup 화면을 KDS 기반 컴포넌트/아이콘/카드 구조로 한 번 더 정리해, primary 위계 계약이 새 등록 화면에도 적용된 repo-internal 상태로 전진했다.

| 화면/파일 | PR #474 감사 당시 표면 | PR #546 / PR #804 이후 상태 | 남은 경계 |
| --- | --- | --- | --- |
| `HomeScreen.kt` | 메뉴 IconButton tint가 primary | PR #546에서 메뉴 icon을 lower-emphasis token으로 낮췄고, PR #804에서 Home status/CTA 카드가 KDS 기반 구조로 정리됐다. Home의 시작/타이머/선택 CTA primary는 유지 | 실제 기기 visual QA에서 Home primary CTA가 충분히 강한지 확인 |
| `LockHistoryScreen.kt` | 뒤로가기 tint가 primary | back icon을 lower-emphasis token으로 낮추고 선택 날짜/성과 숫자 primary는 유지 | 성과 리포트(#465) UI와 함께 visual QA 확인 |
| `BlockedAppsScreen.kt` | 뒤로가기 tint가 primary | back icon을 낮추고 rank/성과 강조는 제품 가치 강조로 유지 | history 하위 화면 QA에서 rank 강조가 navigation보다 강한지 확인 |
| `RoutineScreen.kt` | 뒤로가기와 추가 icon이 모두 primary | back icon은 낮추고 add icon은 화면의 주요 생성 action으로 primary 유지 | add가 icon-only라 비색상 cue/contentDescription이 유지되는지 확인 |
| `EmergencyUnlockSettingsScreen.kt` | 뒤로가기 tint가 primary | back icon을 낮추고 설정 값/선택 상태 primary는 유지 | emergency unlock 설정 화면 visual/TalkBack QA 확인 |
| `GoalLockCreationScreen.kt`, `GoalLockDetailScreen.kt` | 이후 기능 화면에서도 primary/raw orange navigation icon drift 가능 | PR #546 정적 회귀 테스트가 navigation icon primary/raw orange 금지 snippet을 포함했고, PR #804가 Goal Lock 생성 화면을 KDS card/choice/CTA 구조로 정리 | Goal Lock 생성/상세 light/dark visual QA와 release 포함 확인 |
| `ParentModeSetupScreen.kt`, `SetupComponents.kt` | PR #474 당시 미존재/미감사 surface | PR #804가 Parent Mode setup 화면과 공용 setup 컴포넌트를 KDS 기반 표면으로 추가했다. duration/app/PIN 선택은 카드·라벨·설명과 함께 표시하고, start CTA만 primary action으로 남긴다 | Parent Mode setup/active controls visual QA와 TalkBack spot-check, release 포함 확인 |
| `MenuScreen.kt`, `DevToolScreen.kt` | 이후 기능/진단 화면에서도 primary/raw orange navigation icon drift 가능 | PR #546의 정적 회귀 테스트가 navigation icon primary/raw orange 금지 snippet을 포함했고, PR #804가 Menu 주요 진입 카드를 KDS/아이콘 기준으로 정리 | 새 화면 추가 시 `scripts.tests.test_design_primary_color_hierarchy`에 금지 snippet을 확장 |
| `EmergencyUnlockBottomSheetContent.kt` | duration/active/selected/progress primary | 선택/진행 상태라 유지 | 색상 외 chip/background/label 보완이 유지되는지 QA |
| `RoutineDayContent.kt`, `LockHistoryTab.kt`, `LockHistoryWeekCalendar.kt` | 선택 상태 primary | 선택 상태라 유지 | 선택 shape/border/text/semantics가 함께 유지되는지 QA |

따라서 #468은 더 이상 “문서 계약만 있고 구현 전” 상태가 아니다. repo-internal 기준은 `DESIGN.md`/KDS 계약 + PR #546 navigation/action icon 정리 + PR #804 KDS 적용 + 정적 회귀 테스트까지 들어온 상태이며, 남은 것은 실제 기기 visual QA, 릴리즈 포함 여부, 사용자 노출 후 확인이다.

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

## Visual QA / release closure evidence template

#468은 PR #546 이후 repo-internal code/docs/정적 회귀 기준까지는 갖췄지만, 이슈를 닫으려면 실제 사용자가 보는 화면에서 primary 위계가 유지되는지 별도 evidence가 필요하다. 아래 template은 QA lane, release lane, 또는 수동 visual QA가 #468 closure 판단을 할 때 남겨야 하는 최소 증거다.

| 화면 묶음 | visual QA 확인 항목 | evidence |
| --- | --- | --- |
| Home / TopAppBar | menu icon이 primary CTA보다 낮은 위계인지, 시작/타이머/선택 CTA가 화면에서 가장 강한 action인지 | light/dark screenshot 또는 짧은 영상 |
| LockHistory / BlockedApps | back icon이 성과 숫자·선택 날짜·rank 강조보다 낮은 위계인지, 성과 강조가 navigation처럼 보이지 않는지 | light/dark screenshot, 선택 날짜/empty/has-history 상태 |
| Routine / Routine bottom sheet | back/delete icon은 lower-emphasis 또는 error token이고, add/save/selected day는 비색상 cue를 함께 갖는지 | routine list + add/edit sheet screenshot |
| Emergency Unlock settings / bottom sheet | back icon은 낮고 reason/duration/active selection/progress는 chip/background/label과 함께 전달되는지 | settings + bottom sheet screenshot, TalkBack/contentDescription spot-check |
| Goal Lock creation/detail | back icon primary drift가 없고 기간/상태/progress 강조가 CTA와 충돌하지 않는지 | creation/detail screenshot, active/completed 상태 |
| Parent Mode setup/active controls | duration/app/PIN 선택 카드와 시작/연장/종료 CTA의 위계가 KDS 표면에서 유지되는지, primary가 보조 설명/아이콘으로 번지지 않는지 | setup/active/expired screenshot, TalkBack/contentDescription spot-check |
| Menu / DevTool | debug/internal navigation icon에 raw orange 또는 primary tint가 재도입되지 않았는지 | source/static test 결과 또는 screenshot |

Closure comment에 포함할 항목:

- 확인 ref: PR/commit SHA, 포함 release tag 또는 배포 후보.
- visual evidence: light/dark mode screenshot 또는 영상 링크/파일명.
- accessibility evidence: 선택/활성 상태가 색상 외 텍스트, shape, badge, border, semantics, contentDescription 중 하나 이상으로 전달되는지.
- release evidence: release/tag/Play deploy 포함 여부. 아직 release/tag/Play deploy 전이면 사용자 노출 후 확인이 남은 것으로 기록한다.
- 사용자 노출 후 확인: 다음 릴리즈 반영 뒤 대표 화면에서 navigation icon이 다시 primary로 보인다는 피드백/QA finding이 없는지 확인한다.

visual QA와 release evidence가 없으면 PR/이슈 코멘트는 `Refs #468`로 유지한다. `Closes #468`는 위 template의 visual QA, accessibility spot-check, release/tag/Play deploy 또는 동등한 사용자 노출 후 확인이 모두 기록됐을 때만 사용한다.

## 완료/검증 기준

#468 repo-internal 기준은 PR #474 + PR #546 + PR #804 이후 아래 상태까지 충족됐다.

- `DESIGN.md`와 `core/kds/README.md`가 primary 사용/비사용 기준을 동일하게 설명한다.
- 주요 화면의 navigation icon과 primary CTA/선택/활성 상태 위계가 실제 코드에서 분리됐다.
- Home/Menu/Goal Lock/Parent Mode setup은 KDS 기반 카드·아이콘·CTA 구조로 정리되어 새 등록 화면에서도 같은 primary 위계 계약을 따른다.
- 선택/활성 상태는 색상 외 텍스트, 배지, shape, border, contentDescription 중 하나 이상과 함께 유지한다.
- raw `#FFA927`/raw `Color(0xFFFFA927)`를 새로 추가하지 않고 `KeepTheme.colors` 또는 KDS token을 사용한다.
- PR #546 current-head에서 `Fast verification`, `Runtime smoke gate`, `Branch Hygiene`가 통과했고, PR #804도 Home/Menu/Goal Lock/Parent Mode KDS 적용 검증을 통과한 뒤 `develop`에 반영됐다.
- `scripts/tests/test_design_primary_color_hierarchy.py`가 핵심 navigation icon primary/raw orange drift와 PR #804 이후 KDS 적용 surface 누락을 정적으로 막는다.

남은 경계는 repo-internal 문서/코드가 아니라 실제 기기 visual QA, 다음 릴리즈 반영, 사용자 노출 후 확인이다. 따라서 추가 docs-only PR은 `Refs #468`로 두고, visual QA/릴리즈 evidence까지 확보한 뒤 이슈 closure를 판단한다.
