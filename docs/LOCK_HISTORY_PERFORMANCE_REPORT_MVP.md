# 잠금 기록 성과 리포트 UX 계약

Issue: #465
상태: **docs-lane 제품/analytics/QA 계약 고정 / PR #485 read-model·UI 구현 develop 반영 / code-lane instrumentation 추가 / PR #566 summary/top apps TalkBack baseline + PR #579 Top Apps 세부 contentDescription baseline develop 반영 / release·GA4·14일·30일 readback 전**

이 문서는 Stopit의 `LockHistory` 화면을 단순 로그가 아니라 사용자가 “내가 지킨 기록”을 이해하는 성과 리포트 경험으로 개선하기 위한 source of truth다. #211 집중 요약 공유와 같은 화면을 쓰지만, 이 이슈의 1차 목표는 외부 공유가 아니라 **개인 성과 해석과 재방문 동기 강화**다.

## 한 줄 목표

잠금 기록 화면이 `몇 분/몇 회`를 나열하는 데서 멈추지 않고, 사용자가 이번 주·이번 달에 무엇을 지켰는지 긍정적으로 해석해 리텐션과 자기효능감을 강화한다.

## 왜 지금 이 기능인가

- `LockHistory`는 이미 총 잠금 시간, 세션 수, 주/월 탭, calendar, session list, top apps를 제공한다.
- 현재 정보 구조는 기록 확인에는 충분하지만, 낮은 데이터 상태에서 “잘하고 있다”는 해석을 주지 못한다.
- #211 공유 루프, #407 루틴 템플릿 공유, #455 루틴 CTA 같은 성장 실험은 사용자가 먼저 본인의 성과를 납득할 때 더 자연스럽다.
- 기록은 민감할 수 있으므로, 실패/중독 프레이밍보다 “지킨 시간”, “막아낸 앱”, “다음 기록” 같은 비판 없는 언어가 필요하다.

## MVP 범위

### 포함

- `LockHistoryScreen` 상단 summary card의 성취형 headline / supporting copy.
- 기록 없음 / low-data 상태의 격려 카피와 다음 행동 안내.
- Top apps 영역을 `위험 앱 목록`보다 `잘 막아낸 앱`에 가깝게 프레이밍.
- week/month 탭, calendar, session list의 기존 동작 유지.
- 한국어/영어 string parity와 TalkBack 의미 확인.
- 향후 streak, 전주 대비, 공유 CTA와 연결될 수 있도록 read model/test seam을 남긴다.

### 제외

- #211의 공유 CTA / Android share sheet 구현.
- 친구 비교, 랭킹, 챌린지, 공개 피드.
- Usage Access 기반 실제 사용 시간 리포트. 이 범위는 #119 discovery gate를 따른다.
- 앱 이름/package/raw session history를 analytics payload에 넣는 작업.
- 수치가 적은 사용자를 실패로 평가하거나 긴급해제 사용을 질책하는 copy.

## UX / 카피 계약

### Summary card 상태별 계약

| 상태 | 표시 방향 | 예시 copy |
| --- | --- | --- |
| 기록 없음 | 시작 격려 + 다음 행동 | `첫 기록을 시작해볼까요?` / `차단을 한 번 완료하면 여기에서 지킨 시간이 보여요.` |
| low-data (`sessionCount=1` 또는 짧은 duration) | 작은 성공 인정 | `첫 기록이 시작됐어요` / `오늘 지킨 시간을 계속 쌓아볼 수 있어요.` |
| 기록 있음 | 성취형 headline | `이번 주 {durationText}을 지켰어요` / `{sessionCount}번 유혹을 막아냈어요` |
| 월간 탭 | 기간만 바꾼 같은 긍정 프레임 | `이번 달 {durationText}을 지켰어요` |

카피 원칙:

- `낭비`, `중독`, `실패`, `못 참음`, `위험 사용자` 같은 수치심 유발 표현을 쓰지 않는다.
- `많이 차단된 앱`은 가능하면 `가장 잘 막아낸 앱` 또는 `많이 막아낸 앱`처럼 행동 성과로 번역한다.
- 숫자가 작아도 “부족하다”가 아니라 “시작됐다”는 신호로 해석한다.
- 긴급해제/해제/실패 데이터를 summary headline에 섞지 않는다. 필요하면 guardrail로 별도 해석한다.

### 화면 구조 유지 원칙

- 주/월 segmented control, calendar, session list는 기존 사용 습관을 깨지 않는다.
- 상단 summary card copy/read model을 먼저 바꾸고, session row나 calendar semantics는 필요한 만큼만 보강한다.
- Top apps가 비어 있으면 빈 목록을 강조하지 말고 다음 차단 완료 후 채워진다는 안내를 제공한다.
- #211 공유 CTA가 이미 있거나 후속으로 붙을 경우, CTA는 성과 해석 아래의 선택형 행동이어야 하며 리뷰/광고/공유를 압박하지 않는다.

## Privacy / trust guardrail

| 항목 | 허용 | 금지 |
| --- | --- | --- |
| 표시 copy | 집계된 기간, 총 시간, 세션 수, top app display name(이미 화면에 표시되는 범위) | 실패/중독/감시/비교 프레이밍 |
| analytics | enum/bucket/period/read model state | 앱 이름, package, raw session timestamp, raw app list |
| 공유 연계 | 사용자가 명시적으로 탭한 optional share | 자동 공유, 공개 랭킹, 친구 비교 |
| 긴급해제 | guardrail 지표로 별도 모니터링 | summary headline에서 사용자를 질책하는 소재로 사용 |

## Analytics 계약 초안

이 MVP의 핵심 변경은 UI copy/read model이므로 analytics 추가는 구현 판단에 따라 최소화한다. 새 이벤트를 추가한다면 `KeepAnalytics.kt`, Firebase 구현, 테스트, `docs/ANALYTICS_EVENT_DICTIONARY.md`, GA4 Admin runbook을 함께 업데이트한다.

| 이벤트 | 트리거 | 파라미터 | 민감 정보 정책 |
| --- | --- | --- | --- |
| `lock_history_performance_summary_viewed` | LockHistory summary card가 성과 리포트 read model로 표시됨 | `period_type`, `report_state`, `session_count_bucket`, `duration_minutes_bucket` | 앱 이름/package/raw session 금지 |
| `lock_history_top_apps_viewed` | top apps 성과 섹션이 표시됨 | `period_type`, `top_apps_count_bucket` | 앱 이름/package 금지 |

권장 enum/bucket:

- `period_type`: `week`, `month`
- `report_state`: `empty`, `low_data`, `has_history`
- `session_count_bucket`: `0`, `1`, `2_3`, `4_6`, `7_plus`
- `duration_minutes_bucket`: `0`, `1_29`, `30_59`, `60_119`, `120_239`, `240_plus`
- `top_apps_count_bucket`: `0`, `1`, `2_3`, `4_plus`

이벤트를 추가하지 않는 code-lane PR도 유효할 수 있다. 다만 2026-06-05 code-lane instrumentation 이후에는 `LockHistoryViewModel`이 표시 중인 성과 리포트 read model을 기준으로 `lock_history_performance_summary_viewed`와 `lock_history_top_apps_viewed`를 privacy-safe enum/bucket만 전송한다. GA4 Admin 등록·metadata 확인·release/tag/Play deploy 전에는 이 이벤트의 0건을 UX 실패나 수요 없음으로 해석하지 않는다.

## 측정 계획

### 배포 후 14일 확인

- 기간: 개선 포함 버전 Play 배포 후 14일 vs 배포 전 동기간.
- 확인 지표:
  - `LockHistoryScreen` users / active blocked users
  - `LockHistoryScreen` repeat users / `LockHistoryScreen` users
  - `app_block_intercepted` users / active blocked users
  - `lock_history_performance_summary_viewed` users / `LockHistoryScreen` users(이벤트 추가 시)
  - crash-free users, Play rating/review 키워드
- 판단:
  - LockHistory 재방문이 늘고 guardrail이 안정적이면 성과 리포트 copy/read model은 유지한다.
  - 화면 진입은 늘었지만 차단 행동이 늘지 않으면 CTA/다음 행동 안내를 점검한다.
  - 부정 리뷰/불편 키워드가 늘면 성취 copy가 과장·압박으로 읽히는지 확인한다.

### 배포 후 30일 확인

- 기간: 개선 포함 버전 Play 배포 후 30일 vs 배포 전 30일.
- 확인 지표:
  - LockHistory cohort의 D7/D30 repeat block/session 여부
  - `LockHistoryScreen` users 중 루틴 생성/공유 CTA/리뷰 prompt 전환(해당 기능이 배포된 경우)
  - `emergency_unlock_completed` users / active blocked users guardrail
  - Play rating/review 및 Crashlytics
- 판단:
  - 긍정적 기록 경험이 retention에는 좋지만 공유/CTA 전환이 낮으면, 이 이슈는 성장 루프가 아니라 개인 동기 강화로 분류한다.
  - guardrail이 나쁘면 성과 copy를 낮추거나 empty/low-data copy부터 재조정한다.

## QA / 테스트 체크리스트

### 단위/JVM 테스트 후보

- `LockHistoryPerformanceReportReadModelTest` 또는 동등한 helper test:
  - 기록 없음 → `report_state=empty`, 격려 copy, CTA/Top apps 비활성 상태.
  - 세션 1개 또는 짧은 duration → `low_data`, 첫 기록 copy.
  - 기록 있음 → 기간별 duration/session headline.
  - week/month `period_type` copy가 서로 섞이지 않는다.
  - top apps count bucket이 앱 이름/package를 analytics parameter로 노출하지 않는다.
- `LockHistoryViewModel`/state test:
  - 기존 week/month/calendar/session list state를 유지하면서 summary read model만 추가된다.
  - 기존 #211 공유 state가 있다면 공유 CTA 노출 조건과 충돌하지 않는다.
- `FirebaseKeepAnalyticsTest`(이벤트 추가 시):
  - `lock_history_performance_summary_viewed`가 enum/bucket parameter만 전송한다.
  - 앱 이름/package/raw timestamp/raw duration을 전송하지 않는다.

### Android/수동 QA

- 기록 없음 fresh install 또는 데이터 clear 상태에서 empty copy가 허전하거나 질책처럼 보이지 않는다.
- 한 번의 짧은 차단 세션 후 low-data copy가 표시된다.
- 주간/월간 탭 전환 시 headline 기간이 바뀐다.
- Top apps 문구가 “위험 앱”이 아니라 “막아낸 성과”로 읽힌다.
- TalkBack에서 summary headline과 top apps 섹션 의미가 전달된다. QA-lane accessibility regression은 summary card와 top apps card가 성과형 headline/supporting copy를 merged content description으로 노출하고, PR #579 이후 Top apps card는 rank/app label/block count/duration까지 같은 description에 포함하는지 확인한다.
- 한국어/영어 string resource parity를 확인한다.

## 구현 상태와 남은 패키지 경계

PR #485(`feat(lockhistory): 성과 리포트 read model 추가`, merge commit `b6bb3b369e20c693964d8490f829a148312bc448`)로 아래 repo-internal 구현이 `develop`에 반영됐다.

1. `LockHistoryPerformanceReportReadModel` helper와 focused JVM regression이 추가됐다.
2. empty / low-data / has-history / week-month / top-apps copy 계약이 read model 테스트로 고정됐다.
3. `LockHistoryViewModel`과 선택 날짜 필터가 현재 표시 중인 세션 기준으로 summary read model을 계산하도록 연결됐다.
4. `LockHistoryScreen` 상단 summary card와 top apps heading/supporting copy가 성취형/긍정 프레이밍으로 바뀌었다.
5. 유지 locale string parity와 `:app:lintProdRelease` 검증이 완료됐다.
6. 2026-06-05 code-lane instrumentation으로 `LockHistoryViewModel`이 summary 노출 시 `lock_history_performance_summary_viewed`를 기록하고, Top apps 섹션이 실제 표시되는 상태에서만 `lock_history_top_apps_viewed`를 기록한다. payload는 `period_type`, `report_state`, `session_count_bucket`, `duration_minutes_bucket`, `top_apps_count_bucket` 같은 enum/bucket만 사용한다.
7. `docs/QA_RUNTIME_CHECKLIST.md`의 LockHistory performance report evidence template은 구현 PR/QA lane이 실제 evidence를 붙일 수 있는 기준으로 유지한다.
8. PR #566(`test(lockhistory): 성과 리포트 접근성 baseline 보강`, merge commit `48167aef35682d4d84c02b462c94e1901797f04d`)로 `LockHistoryPerformanceReportAccessibilityTest`가 `develop`에 반영되어 summary/top apps 성과 copy가 TalkBack content description으로 합쳐져 전달되는지 device Compose test로 고정했다. PR #579(`test(lockhistory): Top Apps TalkBack 세부 정보 보강`, merge commit `f4b499baf9ccb42102fe29be71ee386a310e6fb3`)는 Top Apps card의 rank/app label/block count/duration까지 같은 merged content description에 포함하도록 회귀 범위를 확장했다.

현재 구현/문서 계약 검증 명령:

```bash
./gradlew --console=plain :app:testDevDebugUnitTest --tests '*LockHistory*Performance*' --tests '*LockHistoryViewModel*'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.lockHistoryPerformanceEventsUsePrivacySafeBuckets' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryViewModelShareTest.weeklyHistoryBuildsSharePayloadAndTracksTappedEventWithBuckets' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryViewModelShareTest.emptyHistoryTracksOnlySummaryPerformanceEventWithoutTopApps'
./gradlew --console=plain :app:testDevDebugUnitTest
./gradlew --console=plain :app:assembleProdDebug
./gradlew --console=plain :app:lintProdRelease
./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lockhistory.component.LockHistoryPerformanceReportAccessibilityTest
python3 -m unittest scripts.tests.test_lock_history_performance_report_contract -v
git diff --check
```

정확한 test class 이름은 구현 PR에서 만든 계약 테스트 이름에 맞춘다. flavorless `testDebugUnitTest`는 사용하지 않는다. PR #485 이후 문서/ops lane은 “구현 전”으로 되돌리지 말고, `develop` 구현 완료와 release/GA4/readback 미완료 경계를 분리해서 기록한다.

## 외부/manual 경계

- 성과 리포트 UI가 실제 retention을 개선했는지는 release/tag/Play deploy 후 14일/30일 window가 필요하다.
- 새 analytics event를 추가하면 GA4 Admin custom dimension 등록과 metadata 확인은 별도 수동/운영 단계다.
- 실제 스크린샷 evidence, release/tag/Play deploy 후 사용자 노출, 14일/30일 readback은 계속 외부/manual 경계다. TalkBack 의미 전달의 repo-internal baseline은 PR #566/#579의 `LockHistoryPerformanceReportAccessibilityTest`가 summary/top apps content description으로 자동화한다. 다만 이 자동 baseline은 실제 기기 스크린샷·운영 TalkBack spot-check를 대체하지 않는다.

## 중복/연계 이슈

- #211: 집중 요약 공유 MVP. 같은 LockHistory 화면이지만 #465는 개인 성과 해석이 먼저이며 공유 CTA 구현과 섞지 않는다.
- #119: Usage Access 개인화 리포트 discovery. 실제 앱 사용 시간/추천은 별도 권한·privacy gate가 필요하다.
- #13: GA4 queryability / custom dimension 등록 경계. 새 event/parameter를 추가해도 Admin 등록과 metadata 확인이 필요하다.
- #14/#455: 첫 가치 피드백과 루틴 CTA. #465의 성과 copy는 첫 차단 성공 이후 재방문 동기를 강화하지만 onboarding/pre-first-lock 흐름에 끼워 넣지 않는다.

## PR/이슈 연결 규칙

PR #485로 LockHistory summary/top apps UI, string parity, focused tests/build는 `develop`에 반영됐고, 2026-06-05 code-lane instrumentation으로 `lock_history_performance_summary_viewed` / `lock_history_top_apps_viewed` 코드 계약과 focused JVM tests가 추가됐다. PR #566으로 summary/top apps TalkBack contentDescription regression과 QA evidence template이 `develop`에 반영됐고, PR #579로 Top Apps TalkBack contentDescription이 rank/app label/block count/duration까지 확장됐다. #465 acceptance에는 아직 release/tag/Play deploy, GA4 Admin/metadata 확인, 실제 스크린샷/TalkBack spot-check, 14일/30일 readback 경계가 남아 있다. 따라서 문서/ops follow-through PR body는 계속 `Refs #465`를 사용한다. `Closes #465`는 위 외부/manual/post-release 경계까지 확인해 이슈 acceptance가 실제로 충족됐을 때만 사용한다.

## 계약 회귀 테스트

이 `lock-history performance report contract regression`은 `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`, analytics dictionary, GA4 runbook, product/metrics docs, runtime QA checklist, docs AGENTS/context pack 링크가 함께 유지되는지 확인한다.

```bash
python3 -m unittest scripts.tests.test_lock_history_performance_report_contract -v
```
