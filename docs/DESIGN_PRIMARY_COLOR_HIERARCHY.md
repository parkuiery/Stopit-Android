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

## 현재 코드 감사 기준선

2026-06-05 docs-lane에서 `KeepTheme.colors.primary` 사용처를 점검한 결과, primary가 제품 상태/선택과 navigation action에 섞여 있다. 아래 후보는 code-lane에서 색상 위계 조정을 할 때 우선 본다.

| 화면/파일 | 현재 표면 | #468 판단 | 권장 후속 |
| --- | --- | --- | --- |
| `HomeScreen.kt` | 메뉴 IconButton tint가 primary | navigation action이라 CTA와 경쟁 | `onSurface`/`surfaceVariant` 계열로 낮추고 Home의 시작/타이머/선택 CTA를 primary로 유지 |
| `LockHistoryScreen.kt` | 뒤로가기 tint가 primary | navigation action이라 낮은 위계가 적절 | `onSurface` 계열로 낮추고 선택 날짜/성과 숫자 primary는 유지 |
| `BlockedAppsScreen.kt` | 뒤로가기 tint가 primary | navigation action | back icon은 낮추고 rank/성과 강조는 유지 여부를 화면 문맥으로 판단 |
| `RoutineScreen.kt` | 뒤로가기와 추가 icon이 모두 primary | back은 낮추고 add는 primary CTA인지 화면별 판단 필요 | add가 화면의 주요 action이면 primary 가능, back은 낮춤. add도 텍스트 CTA/label 보완을 검토 |
| `EmergencyUnlockSettingsScreen.kt` | 뒤로가기 tint가 primary | navigation action | back icon은 낮추고 설정 값/선택 상태 primary는 유지 |
| `EmergencyUnlockBottomSheetContent.kt` | duration/active/selected/progress primary | 선택/진행 상태 | 색상 외 chip/background/label 보완 여부만 확인 |
| `RoutineDayContent.kt`, `LockHistoryTab.kt`, `LockHistoryWeekCalendar.kt` | 선택 상태 primary | 선택 상태 | 선택 shape/border/text 보완이 유지되면 primary 사용 가능 |

이 표는 구현 완료 선언이 아니다. 구현 PR에서는 실제 diff와 스크린샷/QA 증적을 별도로 남긴다.

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

#468을 닫으려면 repo 내부에서 아래를 모두 만족해야 한다.

- `DESIGN.md`와 `core/kds/README.md`가 primary 사용/비사용 기준을 동일하게 설명한다.
- 주요 화면의 navigation icon과 primary CTA/선택/활성 상태 위계가 실제 코드에서 분리된다.
- 선택/활성 상태가 색상 외 텍스트, 배지, shape, border, contentDescription 중 하나 이상으로 보완된다.
- raw `#FFA927`/raw `Color(0xFFFFA927)`를 새로 추가하지 않고 `KeepTheme.colors` 또는 KDS token을 사용한다.
- 시각 변경 PR은 `:core:kds:assembleDebug` 또는 `:app:assembleProdDebug` 등 영향 범위에 맞는 검증을 남긴다.
- 구현이 아직 없고 문서 계약만 완료된 PR은 `Refs #468`로 둔다.
