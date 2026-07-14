# 온보딩 사용시간 기반 약속 코치 설계

## 문서 상태

- 상태: 핵심 경험 승인, 상세 구현 계획 전 설계 기준
- 작성일: 2026-07-15
- 기준 브랜치: `develop`
- 참고 브랜치: `feature/onboarding-usage-aha`
- 다음 단계: 문서 리뷰와 사용자 확인 후 별도 구현 계획 작성

이 문서는 신규 사용자가 Usage Access로 자신의 사용 패턴을 이해하고, 그 결과를 기존 루틴으로 변환해 첫 약속을 지키도록 돕는 온보딩을 정의한다. 참고 브랜치의 코드를 그대로 병합하는 계약이 아니라, 검증된 부분을 재사용하고 제품 흐름과 표현을 다시 설계하는 계약이다.

## 배경과 기준선

2026-07-07부터 2026-07-13까지 완료된 7일의 Amplitude 퍼널은 다음과 같다.

| 단계 | 사용자 | 직전 단계 이탈 |
| --- | ---: | ---: |
| 첫 실행 | 53 | - |
| 인트로 완료 | 52 | 1명, 1.9% |
| 접근성 권한 완료 | 47 | 5명, 9.6% |
| 알림 설정 완료 | 47 | 0명 |
| 앱 선택 완료 | 46 | 1명, 2.1% |
| 첫 핵심 행동 완료 | 36 | 앱 선택/첫 잠금 이후 10명, 21.7% |

현재 온보딩 완료율은 86.8%로, 온보딩 자체보다 첫 잠금 설정 이후 실제 가치 경험까지의 손실이 더 크다. 표본은 53명으로 작으므로 절대적인 제품 결론이 아니라 설계 가드레일로 사용한다.

따라서 새 온보딩은 화면 수나 권한 허용률을 최대화하는 것이 아니라 다음 연결을 강화해야 한다.

> 사용 패턴 발견 → 지킬 수 있는 루틴 생성 → 첫 실행 → 실제 차단 가치 경험

## 목표

### 제품 목표

신규 사용자가 자신의 반복 사용 패턴 하나를 발견하고, 그 패턴에 맞는 첫 약속을 만들며, 약속 생성 후 24시간 안에 실제 차단 가치 경험에 도달하도록 한다.

### 사용자 목표

- 의지 부족이 아니라 반복되는 앱과 시간대가 문제임을 이해한다.
- 복잡한 설정 없이 앱 하나와 짧은 시간대로 시작한다.
- 수동 설정이나 10분 연습을 건너뛰어도 실패로 낙인찍히지 않는다.
- 원시 사용 기록이 외부로 전송되지 않는다는 신뢰를 얻는다.

### 핵심 지표

Treatment의 `first_promise_created` 사용자 중 24시간 안에 해당 약속으로 `app_block_intercepted`까지 도달한 고유 사용자 비율을 핵심 실험 지표로 둔다.

`first_lock_configured`는 준비 완료이고 `first_core_action_completed`와 `app_block_intercepted`는 실제 가치 경험에 가까운 신호라는 기존 퍼널 계약을 유지한다.

## 비목표

- 사용자를 중독 수준이나 의지력 점수로 평가하지 않는다.
- 나이와 기대수명을 이용한 평생 손실 계산을 하지 않는다.
- 메시지, 브라우징 내용, 입력 내용, 알림 내용을 읽지 않는다.
- 원시 앱 사용 이력을 서버나 Analytics로 전송하지 않는다.
- 사용량 기반 일일 쿼터 차단을 새로 만들지 않는다. 기존 루틴의 시간대 차단을 사용한다.
- AI 모델, 소셜 비교, 랭킹, 포인트, 배지, 공유 기능을 추가하지 않는다.
- 여러 앱과 여러 시간대를 동시에 최적화하지 않는다.
- 온보딩에서 월간 리포트나 복잡한 대시보드를 제공하지 않는다.

## 대안과 결정

### 대안 A: 참고 브랜치를 그대로 확장

자가 추정, 나이 입력, 평생 손실, Usage Access, 예상과 실제 비교를 순서대로 보여준다. 구현 자산을 가장 많이 재사용할 수 있지만 화면이 길고, 죄책감이 커질수록 사용자가 통제감을 잃을 수 있다.

### 대안 B: 기존 온보딩을 유지하고 홈에서 분석

현재 86.8% 완료율을 보호하기 쉽다. 반면 사용자가 앱의 개인화 가치를 보기 전에 수동 설정을 마쳐야 하고, 현재 더 큰 병목인 첫 설정 이후 가치 경험을 충분히 개선하지 못한다.

### 결정: 대안 C, 사용 패턴 기반 약속 코치

Usage Access로 얻은 패턴을 리포트로 끝내지 않고, 한 개의 작은 루틴 제안으로 즉시 변환한다. 분석을 원하지 않는 사용자는 기존 수동 선택 흐름으로 진행할 수 있다.

## 용어 모델

| 용어 | 사용자 의미 | 제품/기술 의미 |
| --- | --- | --- |
| 약속 | 되찾고 싶은 시간과 지키려는 행동 | 첫 루틴을 만드는 사용자 경험의 언어 |
| 루틴 | 약속을 자동으로 실행하는 규칙 | 기존 `RoutineModel` 및 스케줄링 |
| 실행 | 오늘 예약된 약속이 동작함 | 루틴으로 시작한 잠금 세션 |
| 가치 경험 | 선택 앱을 열었을 때 실제로 차단됨 | `first_core_action_completed`, `app_block_intercepted` |

기존 루틴에는 종료 날짜가 없으므로 Phase 1은 약속을 7일 체험이나 자동 만료로 표현하지 않는다. 7일 리뷰와 포기 방지 경험은 별도 제품·기술 설계로 분리한다. 이 문서의 루틴은 사용자가 끄기 전까지 기존 규칙대로 유지된다.

## 제품 원칙

1. 한 번에 패턴 하나와 약속 하나만 제안한다.
2. 분석보다 행동 선택을 더 크게 보여준다.
3. 추천 이유를 사용자가 이해할 수 있어야 한다.
4. 사용자는 추천 앱, 시간, 요일을 수정하거나 개인화를 건너뛸 수 있다.
5. 권한은 얻기 위한 대상이 아니라 이미 선택한 가치를 실행하기 위한 수단이다.
6. Phase 1은 streak나 실패 점수를 만들지 않는다.
7. 다른 사용자와 비교하지 않는다.

## 온보딩 정보 구조

### 권장 경로

1. `GoalSelect`: 되찾고 싶은 시간 선택
2. `UsageAccess`: 사용정보 접근 가치와 범위 설명
3. 시스템 Usage Access 설정
4. `UsageAnalysis`: 로컬 분석
5. `PromiseProposal`: 발견한 패턴과 첫 약속 제안
6. `PermissionSetting`: 선택한 약속을 실행하기 위한 접근성 권한
7. `NotificationSetting`: 실행 알림 설정
8. 루틴 저장 및 exact-alarm 조정
9. `PromiseResult`: 저장/예약 상태 확인과 10분 연습 선택
10. Home

Treatment에서는 `GoalSelect`가 기존 `Intro`를 대체한다. Control은 현재 `Intro → PermissionSetting → NotificationSetting → SelectedApp` 경로를 그대로 사용한다. 접근성 권한이 이미 허용된 경우 6번을 건너뛴다. 알림 권한을 거절해도 약속 생성과 차단은 계속할 수 있어야 한다.

### 개인화 건너뛰기 경로

1. `GoalSelect` 또는 `UsageAccess`에서 `직접 설정할게요`
2. 기존 앱 선택 UI를 단일 선택 모드로 사용
3. 선택한 목적에 맞는 기본 시간 제안. 목적이 없으면 21:00 사용
4. 접근성/알림 권한
5. 루틴 저장 및 `PromiseResult`

Usage Access 거절이나 데이터 없음은 온보딩 실패가 아니다. 기본 앱 차단, 타이머, 루틴, 긴급해제 기능은 모두 유지한다.

### 뒤로가기와 프로세스 복구

- 시스템 설정을 열기 전에 `pendingSystemAction`을 `usage_access`, `accessibility`, `exact_alarm` 중 하나로 저장한다. 복귀 또는 process recreation 시 시스템 권한을 다시 읽고 저장된 phase와 함께 다음 route를 결정한다.
- 사용자가 선택한 목적, `path=personalized|manual`, 현재 phase, `draftId`, 아직 저장되지 않은 `PromiseDraft`, `pendingSystemAction`을 프로세스 종료 후에도 복구할 수 있게 저장한다.
- `UsageAnalysis` 중 process death가 발생하면 결과를 복구하지 않고 동일 입력으로 새 분석을 시작한다.
- 접근성 권한 화면에서 이탈하면 초안을 유지하고 다음 앱 실행에서 `AccessibilityPending`으로 복귀한다. 뒤로가기는 `DraftReady`로 돌아가 추천을 편집하게 하며 Home으로 우회하지 않는다. 활성 루틴이나 `first_lock_configured`를 기록하지 않는다.
- exact-alarm 권한이 없어 disabled 상태로 저장된 경우 routine id를 유지하고 Home의 `약속 켜기` 진입점에서 권한 확인과 활성화를 재개한다.
- `PromiseResult`에서 enabled 예약 또는 disabled 저장 결과를 확인하고 Home으로 진행한 뒤에는 온보딩 화면이 back stack에 다시 나타나지 않는다.
- 수동 경로와 개인화 경로 모두 기존 canonical `app_selection_completed` 의미를 유지한다.

Treatment 상태 전이는 다음으로 고정한다.

| phase | 진입 조건 | 성공 전이 | 이탈/오류 전이 |
| --- | --- | --- | --- |
| `GoalPending` | Treatment 최초 진입 | `UsageAccessPending` | `ManualSelectPending` |
| `UsageAccessPending` | 목적 선택 완료 | `Analyzing` | `ManualSelectPending` |
| `Analyzing` | Usage Access 허용 | `DraftReady` | `ManualSelectPending` |
| `ManualSelectPending` | 개인화 건너뜀/분석 부족 | `DraftReady` | 같은 phase 유지 |
| `DraftReady` | 제안 또는 수동 draft 확정 | `AccessibilityPending` | 같은 phase 유지 |
| `AccessibilityPending` | 접근성 허용 | `NotificationPending` | draft 유지, 다음 실행에 같은 phase 복구 |
| `NotificationPending` | 허용 또는 건너뜀 | `Persisting` | 해당 phase 재시도 |
| `Persisting` | 저장 시작 | `SchedulePermissionRequired` 또는 `ResultEnabled` | `PersistFailed` |
| `PersistFailed` | 저장/예약 실패 | 재시도 시 `Persisting`, 편집 시 `DraftReady` | 같은 phase 유지 |
| `SchedulePermissionRequired` | disabled routine 저장됨 | 권한 허용 시 `ResultEnabled`, 나중에 선택 시 `ResultDisabled` | 같은 phase 유지 |
| `ResultEnabled` | enabled routine 저장/예약 성공 | `CompletedEnabled` | 같은 phase 유지 |
| `ResultDisabled` | disabled routine 저장 후 나중에 선택 | `CompletedDisabled` | Home 재진입점 유지 |

각 phase 전이는 단조롭게 진행하되 사용자가 명시적으로 편집을 누르면 `DraftReady` 안에서만 앱·시간·요일을 바꾼다. 이미 routine id가 있는 상태에서는 다시 insert하지 않는다. `CompletedEnabled` 또는 `CompletedDisabled`가 되면 variant와 exposure 기록은 유지하고 draft, pending action, 임시 phase를 지운다. `FirstPromiseEntity` mapping은 attribution을 위해 routine이 삭제될 때까지 유지한다.

## 화면 설계와 카피 계약

### 1. GoalSelect

목적은 동기와 추천 기본값을 얻는 것이다. 나이, 하루 예상 사용시간, 자기평가 점수는 받지 않는다.

- 제목: `휴대폰을 내려놓고 어떤 시간을 되찾고 싶나요?`
- 선택지: `숙면`, `집중`, `공부`, `여유`
- 단일 선택
- 기본 미선택
- CTA: `내 패턴 확인하기`
- 보조 CTA: `직접 설정할게요`

주 CTA는 목적을 선택해야 활성화한다. 보조 CTA는 목적 없이 수동 경로로 갈 수 있으며 이때 `goal_type=unspecified`와 21:00 기본 시간을 사용한다. 목적 값은 enum으로만 Analytics에 전송하고 자유 입력은 받지 않는다.

### 2. UsageAccess

- 제목: `자주 손이 가는 앱과 시간대를 찾아드릴게요`
- 가치 설명: `최근 사용 기록을 기기 안에서 분석해 지킬 수 있는 첫 약속을 제안해요.`
- 보는 정보: 앱별 사용시간, 실행 시점, 반복 시간대
- 보지 않는 정보: 메시지 내용, 입력 내용, 브라우징 내용
- 처리 위치: 기기 내 로컬
- CTA: `내 패턴 확인하기`
- 보조 CTA: `직접 설정할게요`

`허용하러 가기`처럼 권한 자체를 목표로 표현하지 않는다. 시스템 설정을 열지 못하면 조용히 실패하지 말고, 다시 시도와 수동 설정을 제공한다.

### 3. UsageAnalysis

- 5초 안에 완료되면 별도 화면이 아닌 전환 상태로 처리할 수 있다.
- 제목: `최근 사용 패턴을 확인하고 있어요`
- 진행률을 거짓으로 표시하지 않는다.
- ViewModel은 분석마다 증가하는 `analysisAttemptId`를 부여하고 `withTimeout(5 seconds)`로 실행한다.
- timeout 시 해당 attempt를 무효화하고 `ManualSelectPending`으로 한 번만 전이한다. 하위 Android 호출이 취소에 협조하지 않아 결과가 늦게 돌아와도 현재 attempt id와 다르면 결과와 Analytics를 모두 버린다.
- 재시도는 새 attempt id로 시작한다.

### 4. PromiseProposal

한 화면에서 사실 하나와 행동 하나를 보여준다.

예시:

> 지난 7일 유튜브를 하루 평균 1시간 42분 사용했어요.
>
> 특히 밤 11시 이후에 자주 열었어요.

추천:

> 이번 주부터 밤 11시에 유튜브를 30분 쉬어볼까요?

- CTA: `첫 약속 시작하기`
- 보조 동작: `시간 바꾸기`, `요일 바꾸기`, `앱 바꾸기`
- 추천 이유와 실제 제안은 같은 화면에서 보여준다.
- `실제로는 더 많이 사용했어요`, `중독`, `의지 부족` 표현을 사용하지 않는다.
- 사용량은 분 단위 추정치이며 `최근 사용 기록 기준`임을 보조 문구로 표시한다.

### 5. PermissionSetting과 NotificationSetting

권한 설명은 이미 선택한 약속과 연결한다.

> 밤 11시 약속을 실행하려면 앱 제한 권한이 필요해요.

- 접근성 권한을 Usage Access와 같은 권한으로 표현하지 않는다.
- `Screen Time permission` 같은 잘못된 명칭을 사용하지 않는다.
- 접근성 권한이 실제 차단에 필요한 이유를 설명한다.
- 알림 권한은 선택으로 두고 거절해도 진행한다.

### 6. PromiseResult

- enabled 저장/예약 성공 제목: `첫 약속이 준비됐어요`
- exact-alarm 권한 부족으로 disabled 저장된 제목: `첫 약속을 저장했어요`
- 요약: 앱, 시작 시간, 30분, 반복 요일
- enabled CTA: `지금 10분 연습하기`
- enabled 보조 CTA: `예약한 시간에 시작하기`
- disabled CTA: `약속 켜기`
- disabled 보조 CTA: `나중에 홈에서 계속하기`

10분 연습은 루틴 일정을 변경하지 않는 기존 timed-lock 기반 일회성 세션이다. 접근성 권한이 있고 다른 잠금 세션이 실행 중이지 않으며 routine이 enabled일 때만 노출한다. 사용자가 `지금 10분 연습하기`를 눌러 기존 세션 controller가 성공을 반환하면 `started`를 기록하고, 실패하면 해당 클릭에 `start_failed`를 기록한 뒤 같은 화면에서 오류와 재시도 CTA를 보여준다. 사용자가 아직 시작하지 않은 상태에서 `예약한 시간에 시작하기`를 누르면 최초 한 번만 `skipped`를 기록한다. disabled 결과 화면은 연습 대상이 아니므로 practice outcome을 기록하지 않는다. 이미 세션이 실행 중이면 CTA를 숨기고 예약 시간 안내만 보여준다. 사용자가 연습을 시작한 뒤 추천 앱을 실제로 열어 차단되면 canonical `app_block_intercepted(block_source=timed_lock)`에 로컬 판정한 `promise_origin=first_promise_practice`를 추가한다. 앱을 열지 않은 것은 실패로 기록하지 않는다.

## 데이터 계약

### 로컬 입력

Usage Access 허용 후 다음 데이터만 사용한다.

| 데이터 | 용도 |
| --- | --- |
| 패키지명 | 로컬 앱 식별과 루틴 prefill |
| 앱 라벨/아이콘 | 사용자 표시 |
| 최근 7일 앱별 총 포그라운드 시간 | 상위 앱 선택 |
| 일평균 포그라운드 시간 | 사실 카드 표시 |
| 최근 사용 이벤트 시각 | 반복 시간대 계산 |
| 실행 횟수 | 무의식적 반복 사용 보조 신호 |
| 00:00~06:00 사용량 | 심야 패턴 판정 |
| 데이터 커버리지 일수 | 결과 신뢰도와 fallback 판정 |

Keep 자신, 시스템 설정 앱, 실행 불가능 패키지는 기존 게이트웨이처럼 제외한다. 제거된 앱이나 라벨을 확인할 수 없는 앱은 추천 후보에서 제외하되, 전체 분석을 실패시키지 않는다.

### 금지 데이터

- 앱별 원시 사용 이벤트의 Analytics 전송
- 패키지명, 앱 라벨, 앱 아이콘의 Analytics 전송
- 정확한 사용시간, 정확한 시각, 사용자 선택 앱 목록의 Analytics 전송
- 나이, 메시지, 검색어, 알림 내용, 자유 입력 동기

### 데이터 품질

`UsageProfileSnapshot`은 결과와 함께 품질 상태를 반환해야 한다.

| 상태 | 조건 | UX |
| --- | --- | --- |
| `Full` | `usageCoverageDays >= 3`, `eventCoverageDays >= 3`, 유효한 추천 앱과 peak bucket이 있음 | 앱과 시간대를 모두 개인화 |
| `UsageOnly` | `usageCoverageDays >= 3`과 유효한 추천 앱은 있으나 `eventCoverageDays < 3` 또는 peak bucket이 없음 | 앱은 개인화하고 목적 기반 기본 시간을 사용 |
| `Insufficient` | `usageCoverageDays < 3`, 유효한 추천 앱 없음, 조회 실패 중 하나 | 수동 앱 선택과 목적 기반 템플릿 |

분석 window는 오늘의 부분일을 제외한 직전 7개 로컬 calendar day다. 게이트웨이는 각 날짜의 local day start/end로 `queryUsageStats(INTERVAL_DAILY, dayStart, nextDayStart)`를 따로 호출하고 반환 interval을 해당 날짜 경계로 clamp한다. `usageCoverageDays`는 이 window에서 필터를 통과한 launchable 앱의 `totalTimeInForeground > 0` 집계가 하나 이상 존재하는 서로 다른 날짜 수다. `eventCoverageDays`는 후보 앱의 유효한 foreground interval이 하나 이상 존재하는 서로 다른 날짜 수다.

앱별 일평균은 7로 고정해서 나누지 않고 `totalUsage / usageCoverageDays`로 계산한다. `usageCoverageDays == 7`일 때만 `지난 7일 하루 평균`이라고 표시하며, 3~6일이면 `최근 기록된 N일 기준 하루 평균`이라고 표시한다. 재설치나 OEM 공백은 별도로 추측하지 않고 이 coverage 규칙으로 `UsageOnly` 또는 `Insufficient` 처리한다.

Android가 개별 사용 이벤트를 며칠만 유지할 수 있으므로 “7일 전체의 정확한 시간대”라고 단정하지 않는다. 주간 합계는 `queryUsageStats` 기반으로 계산하고, 시간대는 사용 가능한 최근 이벤트의 커버리지와 함께 해석한다.

## 패턴 판정 규칙 v1

v1은 로컬의 결정적 규칙으로 구현하며 서버 모델이나 확률 점수를 사용하지 않는다.

### 후보 앱

1. 분석 window 내 총사용시간 내림차순으로 정렬한다. 동률이면 사용이 관측된 distinct day 수 내림차순, 마지막 사용 epoch 내림차순, package name 오름차순으로 정렬한다. Package name은 로컬 동률 해소에만 사용하고 외부로 전송하지 않는다.
2. coverage 기반 하루 평균 15분 미만인 앱은 자동 추천에서 제외한다.
3. 각 후보에 대해 peak/default 시간을 만든 뒤, 해당 패키지를 포함한 enabled routine이 제안한 모든 반복 요일에서 제안 30분 window를 완전히 덮으면 후보에서 제외한다.
4. 남은 후보 중 첫 앱을 기본 추천 앱으로 사용한다.
5. 후보가 없으면 `Insufficient`로 수동 설정한다.

15분은 제품 기본값이며 원격으로 바꾸는 실험값이 아니다. 구현 전에 fixture를 이용해 추천 분포가 지나치게 비거나 시스템 앱으로 치우치지 않는지 검증한다.

### 반복 시간대

- 완료된 날은 분석 기준 시각의 로컬 날짜보다 이전인 calendar day다. 오늘은 제외한다.
- 후보 앱의 foreground interval을 window로 clamp한 뒤, 해당 interval과 겹치는 로컬 30분 bucket 각각에 겹친 milliseconds를 배분한다. 자정을 넘는 interval은 날짜별로 먼저 분할한다.
- 최소 3개 `eventCoverageDays`가 있을 때만 데이터 기반 시간대를 사용한다.
- 후보 앱의 누적 사용 milliseconds가 가장 큰 30분 bucket을 peak bucket으로 선택한다.
- 동률이면 사용이 관측된 distinct day 수가 많은 bucket, 마지막 관측 epoch가 최근인 bucket, bucket 시작 시각이 이른 순으로 선택한다.
- peak bucket 시작이 22:00~05:30이면 `Night`, 그 외에는 `PeakWindow` 패턴으로 분류한다.
- 이벤트 커버리지가 부족하면 선택 목적에 따른 기본 시간을 사용한다.

### 목적 기반 기본 시간

| 목적 | 기본 시작 | 기본 길이 |
| --- | ---: | ---: |
| 숙면 | 23:00 | 30분 |
| 집중 | 21:00 | 30분 |
| 공부 | 19:00 | 30분 |
| 여유 | 20:00 | 30분 |
| 미지정 | 21:00 | 30분 |

목적 기반 시간은 사용자가 반드시 수정할 수 있어야 한다. 현지 시간대 변경 후에는 다음 실행부터 기존 루틴 스케줄러 규칙으로 재계산한다.

### 약속 생성

- 앱: 추천 후보 1개
- 시작 시간: peak 30분 bucket 시작 또는 목적 기본값
- 길이: 30분
- 반복 요일: 최근 사용 이력과 무관하게 기본은 매일로 제안하되 수정 가능
- 이름: 목적과 앱 라벨을 결합한 로컬 표시명
- 활성 상태: 접근성 권한, 저장 성공, 기존 exact-alarm 조정 결과를 함께 따른다.

`PromiseDraft`는 추천 결과이고 아직 루틴이 아니다. 저장은 기존 exact-alarm orchestrator와 routine repository를 사용한다. exact-alarm 권한이 없으면 기존 계약대로 disabled routine을 저장하고 `routine_saved.schedule_state=disabled_exact_alarm_missing`를 기록한다. 이 상태에서는 준비 완료나 `first_lock_configured`를 주장하지 않는다.

## 상태와 컴포넌트 경계

### `UsageStatsGateway`

- Android 프레임워크 조회를 캡슐화한다.
- 주간 앱별 합계와 시간대 이벤트 집계를 제공한다.
- UI 카피나 추천 결정을 소유하지 않는다.
- IO dispatcher에서 실행한다.

### `UsageProfilePolicy`

- 게이트웨이 결과를 `UsageProfileSnapshot`으로 정규화한다.
- 제외 앱, 데이터 커버리지, peak block, night pattern을 판정한다.
- Android 타입에 의존하지 않는 순수 Kotlin 로직으로 둔다.

### `PromiseRecommendationPolicy`

- 목적, 사용 프로필, 기존 활성 루틴을 입력받는다.
- 설명 가능한 `PromiseDraft` 하나를 반환한다.
- 추천 이유와 루틴 prefill에 동일한 근거를 사용한다.

### 온보딩 ViewModel

- 각 화면의 UI 상태와 비동기 작업, Analytics, navigation side effect를 소유한다.
- 프레임워크 조회나 추천 수식을 직접 구현하지 않는다.
- 중복 탭, 설정 화면 중복 진입, 중복 루틴 저장을 방지한다.

### `FirstPromiseDraftStore`

DataStore에는 저장 전 복구에 필요한 최소 상태만 둔다.

- sticky experiment variant와 assignment version
- 현재 phase와 `path=personalized|manual`
- 선택한 목적
- `draftId`
- 저장 전 `PromiseDraft`
- `pendingSystemAction`
- Usage Access의 local `permissionAttemptId`, 설정 왕복 상태, attempt별 terminal outcome 기록 여부
- `trackedMilestones=exposure|app_selection` 집합

원시 사용 이벤트나 패키지별 사용시간은 복제하지 않는다. 기존 usage cache가 source of truth다.

### `FirstPromiseRepository`

Room에 `FirstPromiseEntity`를 추가해 저장 idempotency와 실제 차단 attribution을 소유한다.

| 필드 | 계약 |
| --- | --- |
| `draft_id` | UUID 문자열 primary key, 외부 전송 금지 |
| `routine_id` | 생성된 routine id unique, 외부 전송 금지 |
| `goal_type` | `sleep`, `focus`, `study`, `free_time`, `unspecified` |
| `source` | `personalized`, `goal_template`, `manual` |
| `created_at_millis` | 로컬 24시간 attribution window 계산용, 외부 전송 금지 |

`FirstPromiseEntity.routine_id`는 Routine row를 참조하는 foreign key이며 routine 삭제 시 mapping도 cascade 삭제한다. `createFirstPromise(draftId, routine)`는 한 Room transaction에서 기존 `draft_id`를 조회하고, 없을 때만 routine insert와 mapping insert를 실행한다. 같은 `draft_id` 재시도는 기존 routine id를 반환한다. 이렇게 하면 routine insert 직후 process death가 발생해도 중복 루틴이 생기지 않는다.

`source=personalized`는 Usage profile이 앱을 선택한 경우, `goal_template`은 Usage Access를 건너뛰거나 데이터가 부족하지만 선택 목적이 기본 시간을 만든 경우, `manual`은 목적 없이 앱과 시간을 직접 선택한 경우다.

Block 경로는 전달받은 local routine id가 `FirstPromiseEntity.routine_id`와 일치하는지 로컬에서 확인해 `promise_origin=first_promise_routine`을 계산한다. routine id 자체는 Analytics로 내보내지 않는다. 10분 연습 attribution은 아래의 durable practice store로 판정한다.

### `FirstPromisePracticeStore`

10분 연습의 attribution은 메모리가 아니라 DataStore에 둔다. timed-lock 시작 성공 후 `active=true`, local `draft_id`, `started_at_millis`, `expires_at_millis=started+10분`을 저장한다. 다른 잠금이 실행 중이면 token을 만들지 않는다. `block_source=timed_lock` interception 시 token이 active이고 만료 전이면 `first_promise_practice`로 판정하고, local `draft_id`로 ordered outbox row를 enqueue한다. timed-lock 종료 또는 만료 시 token을 지우며, app/process recreation 후에도 같은 규칙으로 복구한다. 패키지명이나 routine id는 이 token에 저장하지 않고 `draft_id`도 외부로 보내지 않는다.

### `FirstPromiseAnalyticsOutbox`

Room에 `FirstPromiseAnalyticsOutboxEntity`를 추가한다. `(draft_id, event_name)`을 composite primary key로 사용하고, `sequence`, `delivery_state`, privacy-safe payload, 로컬 전용 `occurred_at_millis`를 보관한다. `routine_saved`와 `first_promise_created`는 routine/mapping transaction 안에서 각각 sequence 10과 20으로 함께 enqueue한다. payload에는 이 문서에서 허용한 enum/bucket만 저장하고 local id나 정확한 관측 사용정보를 넣지 않는다.

dispatcher는 draft별 최소 pending sequence 하나만 골라 직렬 전송하고, 앞 sequence가 sent가 된 뒤에만 다음 row를 보낸다. 저장 직후와 앱 시작 시 drain하며 Analytics 호출이 반환되면 sent로 표시한다. `first_lock_configured`도 같은 draft의 sequence 10·20이 sent가 된 뒤에만 기록한다.

첫 약속 mapping 또는 활성 practice token으로 판정된 최초 차단은 Analytics를 직접 호출하지 않고 해당 local `draft_id`의 outbox에 두 row를 한 transaction으로 enqueue한다.

- sequence 30, `event_name=first_promise_value_intercepted`: canonical `app_block_intercepted`
- sequence 40, `event_name=first_promise_core_action`: 기존 전역 상태에 따라 canonical `first_core_action_completed` 또는 `core_action_completed`

dispatcher는 sequence 30을 sent로 만든 뒤에만 40을 전송해 현재 `app_block_intercepted → first_core_action_completed|core_action_completed` 계약을 유지한다. payload는 기존 privacy-safe `block_source`, `blocking_mode`, category bucket, `elapsed_since_first_open_seconds`와 계산된 `promise_origin`만 사용한다.

sequence 40이 `first_core_action_completed`를 예약한 경우 Room outbox reservation을 전역 최초 이벤트의 durable source로 취급한다. 모든 block 경로의 `FirstCoreActionDeliveryCoordinator`는 기존 `BlockingStateStore`뿐 아니라 이 reservation도 확인해 다른 진입이 두 번째 `first_core_action_completed`를 기록하지 못하게 한다. enqueue 직후 process death가 나더라도 재실행은 저장된 canonical event 선택을 재사용하고, sequence 40이 sent가 되면 기존 `HAS_TRACKED_FIRST_CORE_ACTION` marker를 reconcile한다. UI의 최초 성공 피드백도 같은 coordinator 결과를 사용한다.

따라서 AccessibilityService가 앱 본체보다 먼저 재시작해도 `routine_saved → first_promise_created → app_block_intercepted → first_core_action_completed|core_action_completed` 호출 순서가 유지된다. 이 barrier는 Analytics 호출만 직렬화하며 실제 앱 차단은 기다리게 하지 않는다. sequence 30·40이 sent가 된 뒤의 interception은 기존 경로로 직접 기록한다. 24시간 자격은 enqueue 시 `FirstPromiseEntity.created_at_millis`와 로컬 발생 시각으로 판정하며, 정확한 시각은 Analytics payload로 보내지 않는다.

외부 Analytics SDK가 수신 acknowledgment를 제공하지 않으므로 전달 보장은 **at-least-once**다. 전송 직후 sent 표시 전에 process death가 발생하면 중복될 수 있으나 누락보다 재전송을 선택한다. 따라서 실험 판정은 event totals가 아니라 고유 사용자 funnel을 사용한다. 일반 재시도는 outbox primary key로 중복 enqueue하지 않는다.

sent row는 재시도 deduplication과 실험 감사를 위해 30일간 유지한 뒤 정리한다. mapping이 이미 존재하는 `draft_id`에는 outbox를 다시 생성하지 않으므로 sent row 정리 뒤에도 일반 저장 재시도가 새 이벤트를 만들지 않는다.

### 루틴

기존 `RoutineModel`, exact-alarm orchestrator, repository, enforcement 경로를 재사용하며 Routine schema에는 날짜 제한을 추가하지 않는다. 새 Room migration 대상은 `FirstPromiseEntity`와 `FirstPromiseAnalyticsOutboxEntity` 두 개다.

저장과 계측 순서는 다음으로 고정한다.

1. exact-alarm orchestrator가 draft의 최종 enabled/disabled 상태를 결정한다.
2. `createFirstPromise` transaction이 routine과 mapping을 한 번만 저장한다.
3. 저장된 최종 값으로 기존 `routine_saved`와 `first_promise_created` outbox row를 각각 한 번 enqueue한다. `routine_saved`는 `entry_surface=onboarding`, `creation_source=onboarding_promise`, 기존 bucket과 `schedule_state`를 사용한다.
4. outbox dispatcher가 sequence 10, 20 순서로 두 이벤트를 at-least-once로 전달한다.
5. enabled이며 예약 등록까지 성공하고 두 선행 row가 sent인 경우에만 `markFirstLockConfiguredIfNeeded()` 후 `first_lock_configured(source=onboarding)`를 기록한다. process death 후 복구도 선행 outbox drain을 먼저 수행한다.
6. enabled는 `ResultEnabled`, disabled는 `SchedulePermissionRequired`로 전이한다.

재시도에서 기존 `draft_id`가 발견되면 DB insert와 outbox enqueue를 반복하지 않는다. pending outbox만 재전송한다. disabled routine이 나중에 exact-alarm 권한을 얻어 enabled가 될 때는 기존 routine을 갱신·예약하고 최초 한 번만 `first_lock_configured`를 기록한다.

## 별도 후속 설계

조기 종료 복구 카드, 7일 리뷰, 성공률, 되찾은 시간, 5분 미루기, 앱 임시 허용은 Phase 1과 독립적인 포기 방지 기능이다. 시도·정상 완료·조기 종료·calendar window 계산과 잠금 우회 정책을 별도 스펙에서 정의한 뒤 구현한다. Phase 1의 store, Analytics, 승인 기준에는 이 기능을 포함하지 않는다.

## 오류와 fallback

| 상황 | 처리 |
| --- | --- |
| Usage Access 미허용 | 수동 앱 선택과 목적 기반 시간 제공 |
| 설정 Activity 실행 불가 | 오류 안내, 다시 시도, 수동 설정 제공 |
| 조회 결과 없음 | 실제 사용시간을 0으로 표시하지 않고 수동 설정 전환 |
| 시간대 이벤트 부족 | `UsageOnly`로 앱만 개인화 |
| 분석 5초 초과 | attempt를 무효화하고 수동 설정 전환, late result 무시 |
| 앱 라벨/아이콘 해석 실패 | 해당 앱을 후보에서 제외 |
| 접근성 권한 미허용 | 초안 유지, 루틴 생성 금지, 다음 실행에 접근성 단계 복구 |
| 알림 권한 미허용 | 루틴 생성과 온보딩 완료 허용 |
| exact-alarm 권한 미허용 | disabled routine 저장, 준비 완료 표현 금지, Home 활성화 진입점 제공 |
| 루틴 저장 실패 | `draftId`와 초안 유지, idempotent 재시도 안내 |
| 권한이 나중에 철회됨 | 기존 수동 기능 유지, 분석 카드는 permission-needed 상태 |
| 프로세스 종료 | 저장 phase와 pending action을 복구하고 시스템 권한·routine mapping을 다시 확인 |

오류를 권한 거부로 오해해 Analytics에 기록하지 않는다. 성공 이벤트는 실제 저장과 상태 전이가 끝난 뒤에만 보낸다.

## Analytics 계약

기존 canonical 이벤트를 보존하고 필요한 이벤트만 추가한다.

### 기존 이벤트

- `onboarding_step_view`, `onboarding_step_complete`
- `permission_outcome`
- `app_selection_completed`
- `first_lock_configured`
- `first_core_action_completed`
- `app_block_intercepted`

Usage Access를 위해 기존 `permission_outcome` 계약을 다음처럼 확장한다.

- `permission_name`: 기존 `accessibility`, `notifications`에 `usage_access` 추가
- `outcome`: 기존 `granted`, `denied`, `settings_opened`에 `skipped`, `unknown` 추가
- `settings_opened`: Usage Access settings Intent가 실제로 launch된 각 시도
- `granted`: 앱 복귀/재실행 시 `UsageStatsGateway.isPermissionGranted()==true`
- `denied`: settings Activity로 나간 것이 확인되고 같은 process의 resume에서 권한이 false
- `skipped`: 사용자가 `직접 설정할게요`를 선택
- `unknown`: 해당 attempt의 settings Intent 실행 실패

`granted`, `denied`, `skipped`, `unknown`은 **한 Usage Access attempt**의 terminal outcome이며 attempt별 최초 하나만 기록한다. `settings_opened`는 terminal outcome이 아니며 Intent가 실제 launch된 attempt마다 기록한다. 재시도는 local `permissionAttemptId`를 증가시킨 새 attempt이므로 앞선 `denied`나 `unknown` 뒤에도 나중의 `granted`를 기록할 수 있다. attempt id는 Analytics로 보내지 않으며 수락률은 한 번이라도 `granted`가 발생한 고유 사용자로 계산한다.

process recreation 시 pending action은 있으나 왕복 완료를 판별할 수 없고 권한도 false라면 outcome을 추측하지 않는다. 해당 attempt를 unresolved로 닫고 설명·재시도·수동 설정을 보여주며, 재시도는 새 attempt로 시작한다. 수동 설정을 선택하면 그 새 사용자 선택 attempt에 `skipped`를 기록한다. Intent 실행 오류를 `denied`로 기록하지 않는다.

Treatment step name은 다음으로 확정한다.

- `goal_select`
- `usage_access`
- `promise_proposal`
- `promise_result`

기존 `permission`, `notification`, `select_app`은 의미를 바꾸지 않는다. `select_app` complete와 `app_selection_completed`는 개인화 경로의 `첫 약속 시작하기` 또는 수동 경로의 앱 선택 확정으로 패키지 1개가 draft에 고정되는 시점에 최초 한 번 발생한다. 이후 앱 편집은 `promise_recommendation_edited(field_name=app)`만 기록한다. Treatment의 terminal completion은 enabled/disabled 상태와 무관하게 사용자가 결과를 확인하고 Home으로 진행할 때 발생하는 `onboarding_step_complete(step_name=promise_result)`이고, Control의 terminal completion은 기존 `onboarding_step_complete(step_name=select_app)`이다.

### 신규 이벤트

| 이벤트 | 시점 | 허용 파라미터 |
| --- | --- | --- |
| `onboarding_experiment_exposed` | 배정 variant의 첫 화면이 실제 노출될 때 최초 1회 | `variant`, `assignment_version` |
| `usage_analysis_completed` | 성공, 부족, timeout을 포함한 로컬 분석 종료 | `data_quality`, `pattern_type`, `coverage_days_bucket`, `latency_bucket` |
| `promise_recommendation_shown` | 제안 노출 | `goal_type`, `pattern_type`, `source` |
| `promise_recommendation_edited` | 추천 수정 | `field_name` |
| `first_promise_created` | routine과 mapping 저장 성공 | `goal_type`, `source`, `schedule_state` |
| `first_promise_practice_outcome` | 10분 연습 결과 | `outcome` |

기존 `app_block_intercepted`에는 선택적 `promise_origin`을 추가한다. local routine id가 첫 약속 mapping과 일치할 때 `first_promise_routine`, 활성 연습 token과 일치할 때 `first_promise_practice`를 전송하고 그 외에는 파라미터를 생략한다.

허용 값은 다음으로 고정한다.

| 파라미터 | 허용 값 |
| --- | --- |
| `variant` | `control`, `promise_coach_v1` |
| `assignment_version` | `v1` |
| `goal_type` | `sleep`, `focus`, `study`, `free_time`, `unspecified` |
| `data_quality` | `full`, `usage_only`, `insufficient` |
| `pattern_type` | `night`, `peak_window`, `top_app`, `manual` |
| `source` | `personalized`, `goal_template`, `manual` |
| `coverage_days_bucket` | `0`, `1_2`, `3_6`, `7` |
| `latency_bucket` | `under_1s`, `1_3s`, `3_5s`, `timeout` |
| `field_name` | `app`, `start_time`, `repeat_days` |
| `outcome` (`first_promise_practice_outcome`) | `started`, `skipped`, `start_failed` |
| `promise_origin` | `first_promise_routine`, `first_promise_practice` |
| `schedule_state` | 기존 `routine_saved`의 `enabled`, `disabled_exact_alarm_missing`, `disabled_user_choice`, `disabled_unknown` |

패키지명, 앱 라벨, 관측된 정확한 사용 분 수, 관측된 정확한 사용 시각, 루틴 이름은 금지한다. 사용자가 설정한 약속 길이는 Phase 1에서 항상 30분이고 연습은 항상 10분이므로 별도 Analytics 파라미터로 보내지 않는다. 새 파라미터는 `ANALYTICS_EVENT_DICTIONARY.md`와 GA4 custom dimension 등록 경계를 함께 갱신해야 한다.

## 실험과 출시 기준

### 실험군

- Control: 현재 온보딩
- Treatment: 이 문서의 약속 코치 온보딩
- `feature/onboarding-usage-aha` 흐름을 별도 실험군으로 운영하지 않는다.

### 배정과 중단

- 대상은 첫 온보딩 route를 아직 보지 않은 신규 사용자다. 기존 사용자와 이미 Control/Treatment 화면을 본 사용자는 새로 배정하지 않는다.
- 첫 온보딩 route 결정 전에 `control` 또는 `promise_coach_v1`을 한 번 배정하고 DataStore에 `assignment_version=v1`과 함께 저장한다.
- 배정은 설치 단위로 sticky하며 rollout 비율, Remote Config fetch 결과, process death가 바뀌어도 전환하지 않는다.
- Remote Config를 읽지 못한 미배정 사용자는 Control에 배정한다.
- `onboarding_experiment_exposed`는 배정 시점이 아니라 해당 variant의 첫 화면이 실제 composition된 시점에 한 번만 기록한다.
- 일반 kill switch는 새 Treatment 배정만 중단하고 이미 시작한 사용자는 저장된 route를 완료한다.
- crash/privacy 위험용 emergency kill switch는 variant 값을 바꾸거나 Control의 Intro로 되돌리지 않는다. phase별 전이는 다음으로 고정한다.

| 현재 phase | emergency 전이 |
| --- | --- |
| `GoalPending`, `UsageAccessPending`, `Analyzing` | active analysis attempt 무효화, usage-derived 결과 삭제, 목적만 보존하고 `ManualSelectPending` |
| `ManualSelectPending` | phase 유지 |
| `DraftReady`, `AccessibilityPending`, `NotificationPending` | usage-derived app/time과 pending system action 삭제, 목적만 보존하고 `ManualSelectPending` |
| `Persisting` | 새 navigation을 막고 in-flight transaction 결과를 기다림. 성공해 mapping이 생기면 해당 `ResultEnabled`/`SchedulePermissionRequired`, 실패하면 목적만 보존하고 `ManualSelectPending` |
| `PersistFailed` | 저장 전 draft를 삭제하고 목적만 보존한 `ManualSelectPending` |
| `SchedulePermissionRequired`, `ResultEnabled`, `ResultDisabled`, `CompletedEnabled`, `CompletedDisabled` | 저장된 routine과 phase를 변경하지 않고 향후 Usage 분석만 비활성화 |

무효화된 analysis attempt의 late result와 Analytics는 기존 attempt-id 규칙에 따라 버린다. 이미 mapping이 있는 사용자를 수동 선택으로 되돌리거나 두 번째 routine을 만들지 않는다.

### 핵심 전환

| 지표 | 분자 | 분모 |
| --- | --- | --- |
| terminal 온보딩 완료율 | Control `step_complete(select_app)` 또는 Treatment `step_complete(promise_result)` users | 해당 variant exposure users |
| 앱 선택 완료율 | `app_selection_completed` users | 해당 variant exposure users |
| 첫 약속 생성률 | `first_promise_created` users | Treatment exposure users |
| 24시간 약속 가치 도달률 | 아래 ordered 24시간 funnel을 완료한 users | `first_promise_created` users |
| Usage Access 수락률 | `permission_outcome(granted, usage_access)` users | Usage Access step users |

24시간 약속 가치 도달 funnel은 `first_promise_created` 뒤 86,400초 안에 발생한 다음 이벤트만 인정한다.

- `app_block_intercepted(promise_origin=first_promise_routine, block_source=routine)` 또는
- `app_block_intercepted(promise_origin=first_promise_practice, block_source=timed_lock)`

약속 생성 전 interception, `promise_origin`이 없는 interception, manual Keep·goal lock·parent mode block은 분자에서 제외한다. `first_core_action_completed`는 기존 전역 최초 가치 이벤트 의미를 유지하며 이 약속 전용 funnel의 대체 이벤트로 사용하지 않는다.

Control과 Treatment를 공통으로 비교하는 24시간 first-value guardrail은 `onboarding_experiment_exposed → app_block_intercepted(any canonical block_source)` ordered funnel이다. conversion window는 86,400초이며, variant별 exposure를 분모로 사용한다.

### 가드레일

- 온보딩 완료율이 기존 86.8% 기준선보다 5%p 이상 하락하지 않을 것
- 접근성 권한 단계 이탈이 Control보다 악화되지 않을 것
- 첫 잠금 후 조기 종료율과 긴급해제율이 5%p 이상 악화되지 않을 것
- Crash-free users와 ANR이 악화되지 않을 것
- D1과 D7 유지율을 방향성 지표로 함께 볼 것

Phase 1 가드레일 계산은 기존 이벤트만 사용한다.

- 조기 종료율: variant exposure 후 7일 안의 `lock_session_end(end_reason=user_toggle_off)` event 수 / 같은 창의 `lock_session_end(end_reason in [user_toggle_off, timer_elapsed])` event 수
- BlockScreen 긴급해제 사용자율: variant exposure 후 7일 안에 `app_block_intercepted` 뒤 `emergency_unlock_used(source=block_screen)`까지 도달한 unique users / 같은 창의 `app_block_intercepted` unique users

긴급해제율 분자는 분모 cohort와 교집합인 사용자만 인정하며 `source=lock_screen`과 interception 선행 기록이 없는 해제는 제외한다. 따라서 분자는 항상 분모의 부분집합이다. 두 지표는 조기 종료 복구 기능을 요구하지 않으며 Control/Treatment cohort의 기존 잠금 경험 악화만 감시한다. 어느 군이든 분모가 20 미만이면 50% 이후 승격 판정을 보류한다.

현재 주간 신규 사용자가 약 53명 수준이므로 단기 수치로 우열을 확정하지 않는다. 출시 단계는 다음으로 고정한다.

| 단계 | 최소 관측 | 승격 조건 |
| --- | --- | --- |
| 내부 QA | 지정 기기/계정 | acceptance test 통과, 금지 Analytics payload 0건 |
| production 사전 gate | 사용자 노출 전 | 개인정보 처리방침·Play listing 변경 필요성 검토 완료, 필요하면 변경 배포 완료, 신규 Analytics schema 등록 계획 승인 |
| 10% Treatment | 완료된 7일 이상, Treatment exposure 30명 이상 | 신규 attributable fatal/ANR signature 0건, 분석 attempt 중 timeout+exception 5% 이하, terminal 완료율 Control 대비 -10%p 이내 |
| 50% Treatment | 완료된 14일 이상, 각 군 exposure 100명 이상 | terminal 완료율 기준선 대비 -5%p 이내, 조기 종료·긴급해제 +5%p 이내, 공통 24시간 first-value guardrail이 Control보다 악화되지 않음 |
| 100% 후보 | 50% 단계 통과 및 제품 승인 | 개인정보/Play 문구 최종 확인, GA4 dimension queryability 확인 |

어느 단계든 신규 attributable fatal/ANR signature, 원시 사용정보 전송, terminal 완료율 Control 대비 -10%p 초과가 확인되면 새 Treatment 배정을 즉시 중단한다. 표본을 채우기 위해 안전 가드레일 위반을 무시하지 않는다.

이 실험이 활성화되는 cohort에 한해 `USAGE_STATS_PERSONALIZATION_MVP.md`의 “첫 실행에서 요청하지 않는다” 타이밍 원칙을 실험적으로 대체한다. Control과 미허용 fallback은 기존 계약을 유지하며, 승격 후에는 두 문서의 타이밍 계약을 하나로 정리한다.

## 개인정보와 신뢰

- 원시 사용 이벤트와 앱별 집계는 기기 안에서 처리한다.
- Analytics에는 enum과 bucket만 전송한다.
- 권한 화면에서 무엇을 보고 무엇을 보지 않는지 같은 시각적 위계로 설명한다.
- 민감할 수 있는 앱 이름을 알림, 잠금 화면, 공유 화면에 기본 노출하지 않는다.
- 사용자가 Usage Access를 철회해도 기존 차단 기능과 저장된 루틴을 사용할 수 있다.
- 개인정보 처리방침과 Play listing의 실제 변경은 별도 외부 승인/배포 작업으로 추적한다.

## 접근성·현지화

- 목적 카드와 추천 수정 동작은 TalkBack에서 선택 상태와 동작을 명확히 읽는다.
- 앱 아이콘만으로 후보를 구분하지 않고 앱 라벨을 제공한다.
- 시간과 기간은 locale-aware formatter를 사용한다.
- 200% 글꼴 크기에서도 CTA와 보조 동작이 가려지지 않아야 한다.
- 색상만으로 권한 상태, 추천 상태, 성공 상태를 구분하지 않는다.
- 모든 shipped locale에 새 문자열을 추가하고 한국어 문장을 source of truth로 검수한다.

## 테스트 설계

### 순수 정책 테스트

- 후보 앱 제외, 정렬, 최소 사용량 경계
- interval의 30분 bucket 분할, 자정/DST 교차, peak 동률, 심야 판정, 커버리지 부족
- 오늘 제외, 3/6/7일 coverage, clamp, coverage 기반 일평균
- 목적별 기본 시간
- 기존 루틴과 겹치는 앱 제외
- `Full`, `UsageOnly`, `Insufficient` 판정
- 추천 이유와 `PromiseDraft`가 같은 입력을 반영함

### ViewModel 테스트

- Usage Access의 settings_opened와 attempt별 granted, denied, skipped, unknown 최초 1회
- denied/unknown 뒤 재시도에서 granted를 기록하고 unique-user 수락률에 포함
- 접근성/알림 허용·거절, 설정 미복귀, 설정 Activity 실패
- 분석 성공, 빈 결과, timeout, 예외
- timeout 후 late result 무시와 retry attempt 교체
- 중복 탭과 중복 루틴 생성 방지
- 접근성 권한 전 초안 유지와 활성 루틴 미생성
- 알림 거절 후 정상 완료
- phase/pending action별 프로세스 복구와 back stack
- 일반·emergency kill switch route
- Analytics가 성공 상태 이후 한 번만 발생함
- Analytics payload에 금지 필드가 없음

### 저장소와 스케줄 테스트

- 추천 루틴 저장 후 기존 exact-alarm/repository 경로 사용
- 앱·시간·요일 수정값 보존
- 저장 실패 시 초안 유지
- 동일 `draftId` 재시도 시 routine/mapping/outbox row 중복 없음
- outbox 전송 전 crash는 누락 없이 재전송되고, 전송 직후 crash는 허용된 at-least-once 중복 가능성을 따름
- draft별 sequence 10 → 20 → 30 → 40 직렬 전송과 앱/AccessibilityService 재시작 시 startup barrier
- sequence 30 pending/replay 중에도 `app_block_intercepted → first_core_action_completed|core_action_completed` 순서가 유지됨
- pending first-core reservation이 다른 block 경로의 중복 최초 이벤트와 성공 피드백을 막고 sent 후 DataStore marker를 복구함
- sent row 30일 정리 후 기존 mapping 저장 재시도가 outbox를 다시 만들지 않음
- practice token이 process recreation과 10분 만료를 정확히 처리함
- practice 시작 성공, 예약 선택 skip, start_failed 후 재시도
- enabled와 `disabled_exact_alarm_missing`의 화면·계측·재활성화
- local routine id 기반 `promise_origin` 판정과 외부 routine id 미전송
- `routine_saved → first_promise_created → first_lock_configured` 조건과 최초 1회 계약

### UI와 기기 검증

- Usage Access 시스템 설정 왕복
- OEM에서 설정 Intent를 열 수 없는 경우
- 데이터가 있는 기기와 없는 기기
- 접근성/알림 권한 조합
- 한국어 긴 문장, 모든 shipped locale, dark theme, 큰 글꼴, TalkBack
- 자정 교차와 timezone 변경
- 신규 설치, 재설치, 권한 철회, 프로세스 종료

### 기본 검증 명령

- `./gradlew :app:testDevDebugUnitTest`
- `./gradlew :app:lintDevDebug`
- `./gradlew :app:assembleProdDebug`
- 권한, Room, service/receiver가 변경되면 `./gradlew :app:connectedDevDebugAndroidTest`

## 구현 범위

- GoalSelect
- Usage Access 설명과 시스템 설정 왕복
- 로컬 사용 프로필과 결정적 추천 정책
- PromiseProposal
- 권한 순서 재구성
- 기존 루틴 생성과 PromiseResult
- 수동 fallback
- Analytics와 실험 플래그
- idempotent `FirstPromiseEntity` mapping, Analytics outbox와 Room migration
- durable 10분 연습 attribution token

조기 종료 복구와 7일 리뷰는 이 구현 범위에 포함하지 않는다.

## 구현 영향 범위

- `feature/onboarding`: 새 목적·Usage Access·제안·준비 화면과 navigation 재구성
- `data/usageinsight`: 주간 합계, 시간대, 데이터 품질 게이트웨이
- `domain/usageinsight`: 프로필 및 추천 순수 정책
- `datastore`: variant, phase, pending action, Usage Access 왕복, 저장 전 draft, 연습 token 복구
- `feature/routine`: 기존 prefill/저장 경로 재사용, 필요한 최소 진입점만 추가
- `analytics`: privacy-safe 이벤트와 enum
- `database`: 기존 usage cache 재사용, `FirstPromiseEntity` mapping과 analytics outbox 추가, Routine schema 변경 없음
- `core:kds`: 기존 컴포넌트를 우선 사용하며 온보딩 전용 컴포넌트는 app feature에 둠

새 의존성을 추가하지 않는다. `feature/onboarding-usage-aha`의 Usage Access 설정 왕복, preselect 전달과 테스트는 재검토 가능한 자산이다. `queryWeeklySummary`는 현재 총사용량을 고정 7일로 나누므로 coverage 계약을 반영해 수정한 뒤에만 재사용한다. `LifetimeProjectionPolicy`, 나이 저장, 손실/이득 다단계 화면은 채택하지 않는다.

## 승인 기준

- 사용자는 메시지나 콘텐츠가 아니라 앱 사용시간과 시점만 분석된다는 점을 이해한다.
- Usage Access를 거절하거나 데이터가 없어도 수동으로 온보딩을 완료할 수 있다.
- 개인화 결과는 패턴 하나와 수정 가능한 약속 하나만 보여준다.
- 약속 저장 전 접근성 권한을 확인하고, 저장 성공 뒤에만 준비 완료라고 표시한다.
- 생성된 약속은 기존 루틴과 스케줄러를 사용한다.
- exact-alarm 부족으로 disabled 저장된 루틴은 준비 완료로 표현하지 않는다.
- 같은 draft 재시도는 루틴과 outbox row를 중복 생성하지 않으며, Analytics 전달은 명시한 at-least-once 계약을 따른다.
- Analytics에는 패키지명, 정확한 사용량, 정확한 시각이 포함되지 않는다.
- 기존 canonical 활성화 퍼널의 의미가 유지된다.
- 실험 플래그와 fallback으로 기존 86.8% 온보딩 완료율을 보호할 수 있다.

## 남은 외부 경계

- 개인정보 처리방침과 Play listing 문구 최종 승인
- GA4 custom dimension 등록과 metadata 확인
- production rollout 비율 변경
- 각 실험군 최소 표본 확보 후 승격 판단

이 경계들은 구현 완료만으로 자동 승인된 것으로 보지 않는다.
