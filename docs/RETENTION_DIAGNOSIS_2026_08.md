# 2026-08 리텐션 병목 진단 및 실행 계획

이 문서는 2026-08-21 GA4 + Amplitude live readback의 source of truth다. `docs/METRICS_ANALYSIS.md`의 표준 절차(계기판 먼저 의심 → 병목 특정 → 실행 단위로 묶기 → 14/30일 재측정)를 따른다.

이 readback은 두 개의 밀린 재측정 창을 동시에 닫는다.

- `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`(#380)의 30일 체크(`2026-07-03 이후`)
- `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`(#307)의 post-release 재측정

## 조회 조건

| 항목 | 값 |
| --- | --- |
| 조회 시각 | `2026-08-21` |
| GA4 property | `properties/502544175` (TZ `Etc/GMT-9`) |
| GA4 창 | `30daysAgo..yesterday` = `2026-07-22..2026-08-20` |
| Amplitude project | `Stopit-Prod` (appId `836067`, TZ 미설정 = UTC) |
| 코호트 기준 | GA4 `firstSessionDate` `2026-08-04..08-10`, n=123 |
| repo 기준 | `develop` `2f0ea2aa` |

## 현재 판정

**활성화 병목이 아니라 리텐션 루프 부재다.** 신규 `606` / `app_remove` `434` = `71.6%`이고, 활성화 단계를 아무리 밀어도 D7 잔존은 `10%` 위로 올라가지 않는다. 실행은 D0가 아니라 **D1–D7 구간**을 겨냥해야 한다.

`docs/PRODUCT_METRICS_DASHBOARD.md`의 NSM(`주간 활성 차단 사용자 수`)은 유지한다. 이 문서는 NSM을 바꾸지 않고, **NSM을 올리는 병목의 위치를 D0에서 D1–D7로 옮긴다.**

---

## 1. 계기판 점검 — 먼저 의심한 것

### 1.1 Amplitude `interval: 30` 함정에 걸렸다 (이번 readback의 자체 오류)

`docs/analytics/GA4_AMPLITUDE_JOINT_ANALYSIS.md`가 이미 경고한 항목이다. `eventsSegmentation`에 `interval: 30`을 주면 Amplitude가 월 경계로 스냅해 요청 창보다 넓은 구간을 반환한다. 이번 readback의 Amplitude **절대 수치 일부가 이 경로로 나왔다.**

| 지표 | Amplitude (`interval: 30`, 창 부풀림) | GA4 (`30daysAgo..yesterday`) | 처리 |
| --- | ---: | ---: | --- |
| `emergency_unlock_used` eventCount | 4,042 | 3,565 | **GA4를 채택** |
| `lock_session_start` eventCount | 2,871 | 2,466 | **GA4를 채택** |
| `lock_session_end` user_toggle_off | 2,160 | 1,975 | **GA4를 채택** |
| `lock_session_end` timer_elapsed | 131 | 79 | **GA4를 채택** |
| MAU | 912 | 894 | **GA4를 채택** |

**같은 쿼리 안의 비율은 창이 같으므로 유효하다.** 두 도구가 독립적으로 같은 비율을 냈다.

- 세션 완주율: Amplitude `131 / 2,291 = 5.7%`, GA4 `79 / 2,054 = 3.8%`
- 비상해제 / 잠금 시작: Amplitude `4,042 / 2,871 = 1.41`, GA4 `3,565 / 2,466 = 1.45`

**규칙:** Amplitude 절대 수치가 필요하면 `interval: 1`로 일 단위 시계열을 받아 직접 합산한다. 아래 모든 결론의 절대값은 GA4 기준이고, Amplitude는 `interval: 1` 기반 리텐션 곡선에만 썼다.

### 1.2 Amplitude를 규모 지표로 쓰면 안 되는 이유 (계약대로 동작 중)

| 지표 | Amplitude | GA4 | 차이의 원인 |
| --- | ---: | ---: | --- |
| DAU 평균 | 82 | 130 | autocapture 전면 OFF + allowlist 23종 + `v1.7.9 이상 prod flavor`만 수집 |
| D1 리텐션 | 24.4% | 39.0% | 같은 원인. Amplitude 리텐션은 **구조적으로 과소 추정** |
| D7 리텐션 | 7.8% | 15.4% | 같은 원인 |

이건 버그가 아니라 `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md`의 설계대로다. **역할을 분리한다.**

- **GA4** — 규모, 유입 채널, 삭제, 실제 차단, 광고. 제품/성장 의사결정의 기준.
- **Amplitude** — 퍼널, 코호트 잔존 곡선의 **상대 비교**. 절대 규모 판단에 쓰지 않는다.

---

## 2. 병목 특정

### 2.0 활성화 퍼널 자체는 정상 작동한다

먼저 퍼널을 확인했다. Amplitude funnel, 최근 30일. `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`(#14)의 canonical 단계 정의를 따른다.

| 단계 | users | 직전 대비 | 첫 실행 대비 |
| --- | ---: | ---: | ---: |
| `app_first_open` | 525 | — | 100% |
| `onboarding_step_complete` | 503 | `-22 = -4.2%` | 95.8% |
| `app_selection_completed` | 437 | `-66 = -13.1%` | 83.2% |
| `first_lock_configured` | 371 | `-66 = -15.1%` | 70.7% |
| `first_core_action_completed` | 274 | `-97 = -26.1%` | **52.2%** |

최대 이탈은 마지막 구간(`first_lock_configured → first_core_action_completed`)의 97명이다. 이 구간의 소요시간은 **중앙값 120초, 평균 7,955초(2.2시간)**로 분포가 둘로 갈린다 — 바로 쓰거나, 몇 시간 뒤에 겨우 돌아오거나.

**전환율 52.2%는 그 자체로 나쁘지 않다.** 문제는 이 숫자가 잔존과 연결되지 않는다는 것이고, 그게 2.1이다.

> Amplitude funnel은 `interval`을 쓰지 않으므로 1.1의 창 스냅 문제와 무관하다. 다만 Amplitude 수집 경계(`v1.7.9 이상 prod flavor`)는 그대로 적용되므로, 이 분모 525는 GA4 `newUsers 606`과 다르다. **퍼널 내부의 단계별 비율로만 읽는다.**

### 2.1 활성화는 이탈을 막지 못한다 (이번 readback의 핵심 발견)

Amplitude N-day 리텐션, `startEvent`별 신규 유저 코호트. 분모는 각 일차의 `outof`다.

| 코호트 | n | D1 | D3 | D7 | D14 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 루틴 미생성 신규 (`routine_saved = 0`) | 333 | `48/322 = 14.9%` | `23/291 = 7.9%` | `15/262 = 5.7%` | `9/185 = 4.9%` |
| 전체 신규 (`_new`, 기준선) | 631 | `149/611 = 24.4%` | `72/566 = 12.7%` | `39/502 = 7.8%` | `24/370 = 6.5%` |
| `first_lock_configured` | 407 | `117/394 = 29.7%` | `59/368 = 16.0%` | `25/324 = 7.7%` | `19/246 = 7.7%` |
| `first_core_action_completed` | 358 | `119/346 = 34.4%` | `62/320 = 19.4%` | `27/281 = 9.6%` | `15/215 = 7.0%` |
| 루틴 생성 신규 (`routine_saved >= 1`) | 298 | `101/289 = 34.9%` | `49/275 = 17.8%` | `24/240 = 10.0%` | `15/185 = 8.1%` |

읽는 법:

1. **첫 잠금 설정 완료자 D7 `7.7%` vs 전체 신규 `7.8%` — 차이가 없다.** `first_lock_configured`는 잔존을 전혀 예측하지 못한다. `docs/HOME_STATUS_CTA_STRUCTURE.md`(#463)가 이미 "실제 차단 완료로 과장하지 않는다"고 고정한 경계가 리텐션 축에서도 확인됐다.
2. 현재 활성화 정의(`first_core_action_completed`) 완료자도 D7 `9.6%`로 기준선 대비 **+1.8%p**에 그친다. 즉 활성화율을 `52.2% → 100%`로 만들어도 D7은 `9.6%` 수준이다.
3. **D1은 확실히 갈린다(`14.9% → 34.9%`). 그 격차가 D7까지 살아남지 못한다.** D0에 무엇을 심어도 일주일을 못 간다.

> **해석 주의.** 생존 편향이 있다 — 오래 남은 유저일수록 루틴을 만들 기회도 많다. 다만 편향은 격차를 **과대** 추정하는 방향이므로, "그럼에도 D7 격차가 `4.3%p`뿐"이라는 결론은 편향에 강하다.

### 2.2 삭제자의 절반은 루틴을 가진 채로 떠났다

`app_remove` 434건을 `customUser:routines_count`로 분해했다.

| 보유 루틴 | activeUsers | `app_remove` | 대략 비율 | 해석 |
| --- | ---: | ---: | ---: | --- |
| 0개 | 714 | 192 | `192/714 = 26.9%` | 써보지도 않고 떠남 |
| **1개** | 356 | **150** | `150/356 = 42.1%` | **가장 많이 샌다** |
| 2개 이상 | 267 | 67 | `67/267 = 25.1%` | 루틴을 자기 것으로 만든 층 |
| `(not set)` | 657 | 25 | — | `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`(#479) 미해결. 합산 금지 |

**비율이 `0 → 1 → 2+` 순으로 오르지 않고 1에서 꺾인다.** 단순 편향이면 단조롭게 나온다.

> **해석 주의.** 분모가 깨끗하지 않다. `routines_count`는 30일 동안 값이 변해 한 유저가 여러 구간에 잡히므로 activeUsers 합(2,051)이 MAU(894)를 넘는다. **비율의 절대값이 아니라 비단조 패턴만 읽는다.** #479가 닫히기 전까지 `(not set)`을 `0`과 합산하지 않는다.

2.1과 합치면: **루틴을 만드는 것은 잔존을 만들지 않는다. 정착시키는 것이 만든다.**

### 2.3 재방문 트리거가 0개다

| 확인 항목 | 현재 상태 | 근거 |
| --- | --- | --- |
| 알림 권한 | 허용 `423` / 거부 `127` = **77%** | `permission_outcome` |
| 앱이 보낼 수 있는 알림 | 루틴 시작 알림, 비상해제 카운트다운 **둘뿐** | `NotificationHelper.kt`(`ROUTINE_CHANNEL_ID` 단일 채널), `EmergencyUnlockNotificationHelper.kt` |
| 리마인더/복귀/성과 알림 채널 | **없음** | 위와 동일 |
| 푸시 발송 | **불가능** | first-party backend 제거됨. `docs/FCM_DEVICE_REGISTRATION_CONTRACT.md` |
| 성과 요약 도달률 | `185 / 894 = 20.7%` | `lock_history_performance_summary_viewed`. `LockHistoryViewModel.kt:150`, 히스토리 화면 안쪽 |

허용받은 알림 권한 77%가 리텐션 목적으로 전혀 쓰이지 않고 있다. `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`(#465)가 "개인 성과 해석/재방문 동기"를 목적으로 두었는데, 그 지면에 MAU의 1/5만 도달한다.

### 2.4 리뷰 프롬프트 — #307의 판단 조건이 충족됐다

`docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`는 "post-PR-308/#312 배포 후에도 `BelowSessionThreshold`가 지속되면 eligibility threshold를 본다"고 결정 규칙을 고정했다. **지속됐다.**

| 이벤트 | 30일 | 2026-06-02 baseline |
| --- | ---: | ---: |
| `review_prompt_shown` | `3` (users 3) | 0 |
| `review_prompt_eligible` | `5` (users 5) | 0 |
| `review_prompt_skipped` | `76` (users 61) | 27 |

`review_prompt_skipped.reason` 분해:

| reason | eventCount | 비중 |
| --- | ---: | ---: |
| `BelowSessionThreshold` | 55 | `55/76 = 72.4%` |
| `QuietHours` | 9 | 11.8% |
| `RecentEmergencyUnlock` | 6 | 7.9% |
| `WithinCooldown` | 5 | 6.6% |
| `AccessibilityOff` | 1 | 1.3% |

원인 사슬이 코드까지 확인됐다.

1. `LockViewModel.kt:191` — `maybeArmReviewPrompt(...)`는 **`TIMER_ELAPSED` 경로에서만** 호출된다.
2. GA4 기준 완주 세션은 `79 / 2,054 = 3.8%`. 즉 전체 세션의 3.8%만 arm 평가 대상이 된다.
3. `ReviewEligibilityEvaluator.kt:12` — 그 3.8% 안에서 다시 `SESSION_THRESHOLD = 3`(`docs/REVIEW_PROMPT_LIFECYCLE.md` 조건 6). 도달이 사실상 불가능하다.

유입은 `google-play organic 540 / direct 65 / google organic 1`로 **100% 오가닉**이다. 리뷰가 사실상 유일한 성장 엔진인데 막혀 있다.

### 2.5 루틴 제안은 스팸이 됐다

| 단계 | eventCount | users | 전환 |
| --- | ---: | ---: | ---: |
| `repeat_block_routine_suggestion_shown` | 7,635 | 272 | 1인당 **28.1회 노출** |
| `repeat_block_routine_suggestion_clicked` | 211 | 54 | `211/7,635 = 2.8%` |
| `repeat_block_routine_suggestion_applied` | 11 | 10 | `11/7,635 = 0.14%` |

`docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`(#531)의 계약 표면이다. 클릭 211건 중 200건이 프리필 생성 화면에서 죽는다.

한편 루틴은 차단 가치의 대부분을 만든다 (`app_block_intercepted`, `customEvent:block_source`):

| block_source | eventCount | users | 1인당 |
| --- | ---: | ---: | ---: |
| `routine` | 14,450 | 289 | **50.0** |
| `timed_lock` | 4,067 | 126 | 32.3 |
| `manual_keep` | 3,477 | 247 | 14.1 |
| `parent_mode` | 1,310 | 15 | 87.3 |
| `goal_lock` | 766 | 30 | 25.5 |

`routine`이 전체 24,070건의 **60.0%**다. `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`의 "루틴 보유자의 차단 강도가 높다"가 `block_source` 축에서 재확인됐다.

---

## 3. 실행 계획

한 번에 하나만 바꾼다. 여러 개를 동시에 건드리면 D7이 움직여도 원인을 분리할 수 없고, 2.1이 보여주듯 **현재 팀은 무엇이 잔존을 만드는지 모르는 상태**다.

### 단계 0 — 측정 정상화 (선행, 반나절)

이걸 먼저 하지 않으면 단계 1의 결과를 판독할 수 없다.

| 작업 | 대상 | 값 |
| --- | --- | --- |
| Amplitude 캡 상향 | `app/build.gradle.kts` `AMPLITUDE_MONTHLY_EVENT_CAP` | `180 → 950` |
| 리뷰 arm 임계값 완화 | `ReviewEligibilityEvaluator.kt:12` `SESSION_THRESHOLD` | `3 → 1` |
| 리뷰 arm 경로 확장 | `LockViewModel.kt:191` `maybeArmReviewPrompt` 호출 조건 | 일정 시간 이상 지속된 `user_toggle_off` 세션도 성공 세션으로 인정 |

캡 값 `950`의 근거는 `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md`의 튜닝 표를 그대로 따른 것이다.

| Expected max MTU | Safe cap | 현재 상황 |
| ---: | ---: | --- |
| 10,000 | 180 | 현재 값. MAU 894 대비 11배 과보수 |
| 2,000 | **950** | **채택.** MAU 894 기준 2.2배 성장 여유 |
| 1,000 | 1,800 | 여유가 너무 얇다 |

`cap × maxMTU = 950 × 2,000 = 1,900,000 < 2,000,000`으로 하드 보장은 유지된다.

> **주의.** `docs/ANALYTICS_EVENT_DICTIONARY.md:196`이 지적하듯 `website_blocking_status_changed`는 불안정 회선에서 한 사용자가 캡을 혼자 소진할 수 있다. 이 이벤트는 Amplitude allowlist에 없으므로 캡에 영향을 주지 않지만, allowlist에 추가하는 순간 이 계산을 다시 한다.

두 리뷰 관련 변경은 `docs/REVIEW_PROMPT_LIFECYCLE.md`의 조건 6과 `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`의 결정 규칙을 같이 갱신해야 한다. 문서 갱신 없이 상수만 바꾸지 않는다.

**확인 (배포 +7일):** `review_prompt_shown`이 `3`에서 두 자릿수로 올라왔는지. 안 올라왔으면 남은 스킵 사유(`QuietHours` 9, `NotHomeRoot`)를 마저 본다.

### 배포 게이트 — 단계 0이 라이브로 확인되기 전에 단계 1을 배포하지 않는다

**개발과 배포를 분리한다.** 직렬이어야 하는 것은 측정이지 코딩이 아니다.

| 활동 | 단계 0 배포 전 | 단계 0 +7일 확인 후 |
| --- | --- | --- |
| 단계 1 **구현** | 진행해도 된다. 단계 0의 결과가 단계 1의 설계를 바꾸지 않는다 | — |
| 단계 1 **배포** | **금지** | 가능 |
| 단계 1 baseline 재측정 | — | **필수** (아래) |

대기 기준은 달력이 아니라 **버전 채택률**이다. `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준으로 단계 0 포함 버전이 `충분`에 도달한 뒤 +7일을 센다. 참고로 1.9.x는 릴리즈 후 7일에 WAU의 63.9%에 도달했으므로 실무상 1–2주로 본다.

#### 함정 1 · 캡 상향만으로 Amplitude 리텐션이 좋아 보인다

`AMPLITUDE_MONTHLY_EVENT_CAP`을 `180 → 950`으로 올리면 그동안 잘려 있던 헤비 유저의 이벤트가 다시 들어온다. 그 결과 **아무것도 개선되지 않아도 Amplitude D1/D7이 올라간다.** 1.2의 `24.4% / 7.8%`은 옛 측정 체계의 숫자이므로 단계 1 판정의 비교 대상으로 쓰면 안 된다.

따라서 **단계 1의 성공 판정은 GA4 지표로만 한다.** GA4 코호트 잔존과 `app_remove`는 Amplitude 캡과 무관하다.

#### 함정 2 · 리뷰 프롬프트 변경은 측정 중립이 아니다

단계 0은 순수한 계측 변경이 아니다. `review_prompt_shown`이 3건에서 두 자릿수로 늘면 Play 리뷰/평점이 움직이고, 유입이 100% 오가닉이므로 **신규 유저의 구성 자체가 바뀔 수 있다.** 그 위에서 단계 1의 코호트를 재면 두 변경이 섞인다.

그러므로 단계 0 안정화 후 **`15.4%` / `71.6%` baseline을 같은 방법으로 다시 뜨고**, 단계 1은 그 새 baseline과 비교한다. 아래 성공 판정표의 "현재" 열은 단계 0 이전 값이므로 그때 갱신한다.

### 단계 1 — 첫 루틴 정착 루프 (2주, 유일한 베팅)

2.2와 2.3이 같은 공백을 가리킨다. **루틴을 만든 다음 날 아무 일도 일어나지 않는다.** 세 가지를 한 덩어리로 붙인다. 셋 다 서버 없이 된다.

| # | 작업 | 대상 | 근거 |
| --- | --- | --- | --- |
| 1 | 로컬 알림 채널 신설. 루틴이 처음 돈 다음 날 아침 "어제 루틴이 N번 막았어요" | `NotificationHelper.kt`에 채널 추가 | 2.3 — 권한 77% 확보, 리마인더 채널 부재 |
| 2 | 성과 요약을 히스토리 안쪽에서 홈 상단 카드로 | `LockHistoryViewModel` → 홈. `docs/HOME_STATUS_CTA_STRUCTURE.md` 위계 준수 | 2.3 — 도달률 `20.7% → 100%` |
| 3 | 첫 루틴이 2–3회 돈 뒤 1회만 "이 시간대 맞았나요?", 수정은 원탭 | 루틴 편집 진입 | 2.2 — 루틴 1개 층이 `42.1%`로 가장 많이 샘 |

목표는 **두 번째 루틴까지 밀어주는 것**이다. 2개 이상 보유 층은 `25.1%`로 이탈이 가장 낮다.

> **guardrail.** `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`가 "루틴 CTA/템플릿 실험 시 crash-free users, review/rating, emergency unlock 비율을 별도 guardrail로 기록한다"고 고정했다. 알림 추가는 특히 **알림 차단률과 `app_remove`**를 같이 봐야 한다. 리마인더가 역효과를 낼 수 있다.

**성공 판정 (배포 +4주):**

| 지표 | 단계 0 이전 (2026-08-21) | 목표 |
| --- | ---: | ---: |
| GA4 D7 코호트 잔존 | `19/123 = 15.4%` | `20%` |
| `app_remove` / `newUsers` | `434/606 = 71.6%` | `60%` 이하 |
| NSM (주간 활성 차단 사용자) | `530` (30일) | 상승 |

**"단계 0 이전" 열은 그대로 쓰지 않는다.** 배포 게이트의 함정 2에 따라, 단계 0이 안정화된 뒤 같은 방법으로 baseline을 다시 뜨고 그 값과 비교한다. Amplitude 리텐션은 캡 상향만으로 올라가므로 판정에 쓰지 않는다(함정 1).

**둘 다 안 움직이면 여기서 멈춘다.** 가설이 틀린 것이므로 단계 2로 넘어가지 않고, "D7에 남은 사람이 D0–D1에 무엇을 했는가" 코호트 분석부터 다시 한다.

### 단계 2 — 루틴 제안 UI 수리 (조건부)

단계 1이 "루틴 정착이 잔존을 만든다"를 증명한 뒤에만 착수한다. 순서를 지켜야 이 수리의 가치도 같이 검증된다.

| 작업 | 근거 |
| --- | --- |
| 노출 빈도 캡 — 1인 `28.1회 → 주 1–2회` | 2.5 |
| 제안 카드에서 원탭 루틴 생성 (프리필 화면 경유 제거) | 2.5 — 클릭 211 중 200이 생성 화면에서 소실 |

**성공 판정:** 제안 → 적용 전환 `0.14% → 3%`, 그리고 `routines_count >= 2` 유저 비율 상승.

### 지금 하지 않는 것

| 항목 | 이유 |
| --- | --- |
| **잠금 마찰 강화** | 4장 참조. 근거가 뒤집혔다. 하더라도 A/B로만 하고, 결과 지표는 완주율이 아니라 `app_remove`와 D7 |
| **활성화 퍼널 최적화** | 2.1에서 천장 확인. `52.2% → 100%`로 만들어도 D7은 `9.6%` |
| **신규 기능 확장** (목표 잠금 / 부모 모드) | 각각 32명, 15명 규모. 잔존 근거 없이 면적을 넓히면 단계 1의 판독만 어려워진다 |
| **본격 수익화** | MAU 894로는 이르다. 광고가 이미 `580명 / 22,056 노출`로 돌고 있다. 잔존이 먼저 |
| **`device_registration` 이벤트 제거** | 5장 참조. 계약 위반이며, 실익도 거의 없다 |

---

## 4. 잠금 마찰 강화를 보류하는 이유

초판 진단에서 이 항목을 최우선으로 올렸다. **근거가 뒤집혀 보류로 내린다.** 판단 근거를 남긴다.

올렸던 이유:

- 세션 완주율 `79 / 2,054 = 3.8%` — 96%가 사용자 손으로 꺼진다
- 비상해제 `3,565` > 잠금 시작 `2,466` — 세션당 `1.45회` 탈출
- 비상해제 시도 대비 완료 `3,558 / 3,565 = 99.8%` — 마찰이 사실상 0
- 탈출 경로의 `3,426 / 3,565 = 96.1%`가 `block_screen`

뒤집은 근거 — 같은 차단 화면에서의 두 선택지를 코호트로 비교했다.

| 차단 화면에서의 선택 | startEvent | D7 | D14 |
| --- | --- | ---: | ---: |
| 순응 | `first_core_action_completed` | `27/281 = 9.6%` | `15/215 = 7.0%` |
| 우회 | `emergency_unlock_used` | `68/236 = 28.8%` | `44/181 = 24.3%` |

**우회한 유저가 3배 잘 남는다.** 우회는 이탈의 원인이 아니라 관여의 신호에 가깝다 — 앱을 지우지 않고 앱 안에서 문제를 해결한 사람들이다. 마찰을 붙이면 탈출구가 `app_remove`만 남고, 이미 월 434건이다.

`docs/ROUTINE_RETENTION_COHORT_BASELINE.md`가 남긴 "루틴 보유자는 긴급해제 사용자 비율도 높다... 차단 강도가 부담으로 이어지는 신호일 수도 있다"는 주의와 같은 방향이다.

> **해석 주의.** 이 비교에는 편향이 있다. `emergency_unlock_used`는 반복 이벤트라 startEvent 코호트에 기존 유저가 섞이는 반면 `first_core_action_completed`는 정의상 신규 유저다. 따라서 **`28.8%`를 인과 효과로 읽으면 안 된다.** 다만 "우회가 이탈을 만든다"는 반대 방향의 증거는 이 데이터셋 어디에도 없다. 관측 데이터로는 여기까지이고, 확정하려면 실험이 필요하다.

별개 항목: `emergency_unlock_manual_reset_requested`가 **16명에게서 303회** 발생했다 (1인당 18.9회). 잔여 횟수 리셋이 무제한 우회로로 쓰이고 있는지 확인이 필요하다. 이건 잔존과 무관한 정상 동작 점검이다.

---

## 5. 초판 진단에서 철회한 항목 — `device_registration` 이벤트 제거

초판에서 "`device_registration_attempted` / `device_registration_skipped` / `fcm_token_captured` 각 6,790건(전체 이벤트의 6.1%)이 100% 스킵되므로 삭제"를 제안했다. **철회한다.** 두 가지가 틀렸다.

**1. 계약 위반이다.** `docs/FCM_DEVICE_REGISTRATION_CONTRACT.md`(#194)가 이 세 이벤트를 "현재 실제 발생 가능한 이벤트"로 명시적으로 고정하고 있고, `KeepAnalyticsEvent.ACTIVE_DEVICE_REGISTRATION_EVENTS`와 `FirebaseKeepAnalyticsTest`가 이 집합을 테스트로 지킨다. `device_registration_skipped(reason=backend_removed)`는 **현재 정상 상태를 명시하는 marker**이고, `reason=missing_fcm_token` 비중은 token 저장 건강도 지표다. 실제로 #1090이 이 신호를 쓰는 중이다.

**2. 실익이 거의 없다.** 이 세 이벤트는 Amplitude allowlist에 없어 **Firebase/GA4 전용**이다. Amplitude 캡과 무관하고, GA4 무료 티어는 월 335,986건을 문제없이 처리한다. 절약되는 비용이 사실상 0이다.

남는 사실은 "1인당 8회는 많다"뿐이다(`6,790 / 850 = 8.0`, 세션 `11.7회/유저`와 비례 — 앱 기동마다 `saveDeviceToken()` 호출). 줄이고 싶다면 **이벤트 삭제가 아니라 동일 token 재저장 시 중복 기록을 건너뛰는 dedupe**이고, 그건 위 계약의 "`saveDeviceToken()` 호출 후 항상"을 바꾸는 일이므로 **별도 이슈에서 계약 개정과 함께** 다뤄야 한다. 우선순위는 낮다.

---

## 6. 확인했지만 문제 아닌 것

| 항목 | 첫인상 | 실제 |
| --- | --- | --- |
| 버전 채택률 | 30일 기준 `1.7.11`이 활성 530명으로 1위 | **정상.** 최근 7일 기준 `1.9.x 358/560 = 63.9%`, `1.8.1 30.9%`, `1.7.x 5.2%`. 30일 창이 만든 착시. `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 `충분` |
| 크래시 | `app_exception` 100건 / 27명 | 최근 7일 `48건 / 14명 = WAU의 2.5%`. 특정 버전 편중 없음. 모니터링 유지 |
| 온보딩 단계 역전 | `intro` 268명 < `permission` 472명 | **정상.** `intro/goal_select/promise_*`는 신규 약속 온보딩 단계라 최신 버전에만 노출. `onboarding_experiment_exposed` 510명으로 A/B 진행 중 |
| 약속 온보딩 | — | **건강함.** `promise_recommendation_shown` 238명 → `first_promise_created` 185명 = `77.7%`. 퍼널 중 가장 좋다 |

---

## 7. 재측정 기준

단계 0 배포 후 +7일, 단계 1 배포 후 +14일 / +30일에 아래를 같은 분자/분모로 다시 조회한다.

- GA4 D7/D14 코호트 잔존 (`firstSessionDate` 주간 코호트)
- `app_remove` / `newUsers`
- `app_remove` × `customUser:routines_count` 분해 (2.2 표)
- `review_prompt_shown` / `review_prompt_eligible` / `review_prompt_skipped.reason`
- `repeat_block_routine_suggestion_shown → clicked → applied`
- `lock_history_performance_summary_viewed` users / activeUsers
- NSM: `app_block_intercepted` 7일 유니크 사용자
- guardrail: `app_exception` users / activeUsers, Crashlytics crash-free, Play Console rating, 알림 차단률
- `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 최신 버전 cohort confidence

표준 readback은 `scripts/metrics_read.py`로 시작한다.

```bash
cd <repo-root>
python3 scripts/metrics_read.py --days 30 --json-out .tmp/readback-30d.json
```

이 스크립트가 다루지 않는 축(코호트 잔존 곡선, `app_remove × routines_count`)은 GA4 `runReport`와 Amplitude MCP로 직접 조회한다. **Amplitude를 다시 쓸 때는 반드시 `interval: 1`**로 받아 직접 합산한다 (1.1 참조).

## 부록 · 30일 주요 수치

GA4 `2026-07-22..2026-08-20`, property `502544175`. 재측정 시 같은 분자/분모로 다시 채운다.

### 규모

| 지표 | eventCount | users |
| --- | ---: | ---: |
| 활성 유저 (MAU) | — | 894 |
| 신규 유저 | — | 606 |
| 앱 삭제 (`app_remove`) | 434 | 434 |
| 세션 | 10,431 | 892 |
| 화면 조회 (`screen_view`) | 81,660 | 891 |
| 총 이벤트 | 335,986 | — |

파생: DAU 평균 `130` · `DAU/MAU = 14.5%` · 세션/유저 `11.7` · `app_remove / newUsers = 71.6%`

### 핵심 가치

| 지표 | eventCount | users |
| --- | ---: | ---: |
| `app_block_intercepted` (**NSM 원천**) | 24,070 | 530 |
| `core_action_completed` | 23,707 | 500 |
| `lock_session_start` | 2,466 | 461 |
| `lock_session_end` | 2,054 | 399 |
| `first_lock_configured` | 412 | 400 |
| `first_core_action_completed` | 363 | 354 |

### 탈출 경로

| 지표 | eventCount | users |
| --- | ---: | ---: |
| `emergency_unlock_used` | 3,565 | 295 |
| `emergency_unlock_completed` | 3,558 | 295 |
| `emergency_unlock_step_viewed` | 15,320 | 388 |
| `emergency_unlock_validation_blocked` | 472 | 147 |
| `emergency_unlock_cancelled` | 156 | 113 |
| `emergency_unlock_manual_reset_requested` | 303 | **16** |

### 루틴 / 인사이트 / 수익

| 지표 | eventCount | users |
| --- | ---: | ---: |
| `routine_saved` | 521 | 314 |
| `usage_insight_card_shown` | 3,826 | 640 |
| `usage_insight_card_cta` | 539 | 245 |
| `usage_insight_card_dismissed` | 247 | 201 |
| `lock_history_performance_summary_viewed` | 727 | 185 |
| `ad_banner_impression` | 21,751 | 576 |
| `ad_banner_click` | 53 | 24 |
| `monetization_interest_shown` | 5,068 | 457 |
| `monetization_interest_clicked` | 73 | 54 |
| `support_contact_started` | 144 | 87 |

`monetization_interest_clicked`의 `interest_surface`는 **`menu` 단일 지면 100%**다. 다른 지면이 없어 모수 자체가 메뉴 진입자로 한정된다.

### 권한

| 권한 | 허용 | 거부 | 건너뜀 | 설정 진입 |
| --- | ---: | ---: | ---: | ---: |
| 접근성 | 546 (**93%**) | — | — | 586 |
| 알림 | 423 (**77%**) | 127 | — | — |
| 사용 기록 | 152 (**28%**) | 47 | 74 | 185 |

사용 기록 권한 분모는 권한 단계 도달자 546명이다. Usage Insight 카드는 640명에게 노출되지만 실데이터는 152명분이다.

### 유입 / 지역 / 버전

| 축 | 값 |
| --- | --- |
| 유입 | `google-play organic 540` · `direct 65` · `google organic 1` — **100% 오가닉** |
| 지역 | 대한민국 812 (`812/894 = 90.8%`) · 그 외 82 |
| 버전 (최근 7일) | `1.9.x 358/560 = 63.9%` · `1.8.1 30.9%` · `1.7.x 5.2%` |

## 갱신이 필요한 기존 문서

단계 0을 실행할 때 같이 고친다. 상수만 바꾸고 문서를 두면 계약이 어긋난다.

| 문서 | 갱신 내용 |
| --- | --- |
| `docs/REVIEW_PROMPT_LIFECYCLE.md` | 조건 6 `SUCCESSFUL_SESSION_COUNT < 3` → 새 임계값. arm 트리거 경로 확장 반영 |
| `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md` | 2026-08-21 readback 추가 (본 문서 2.4). #307 결정 규칙 충족 기록 |
| `docs/ROUTINE_RETENTION_COHORT_BASELINE.md` | 밀린 30일 재측정 추가 (본 문서 2.2) |
| `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md` | 캡 `180 → 950` 및 근거 MTU 가정 변경 |
| `docs/analytics/GA4_AMPLITUDE_JOINT_ANALYSIS.md` | `interval: 30` 함정 실제 사례 추가 (본 문서 1.1) |
| `docs/PRODUCT_METRICS_DASHBOARD.md` | 본 문서를 리텐션 병목 source of truth로 링크 |

## 관련 문서

- `docs/METRICS_ANALYSIS.md` — 지표 분석 표준 절차
- `docs/PRODUCT_METRICS_DASHBOARD.md` — NSM / 지표 정의
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`(#14) — 활성화 퍼널 canonical 계약
- `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`(#380) — 루틴 코호트 기준선
- `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`(#479) — `routines_count` coverage
- `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`(#531) — 루틴 제안 계약
- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`(#455) — 루틴 생성 CTA 실험
- `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`(#465) — 성과 리포트 UX
- `docs/REVIEW_PROMPT_LIFECYCLE.md` / `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`(#307)
- `docs/FCM_DEVICE_REGISTRATION_CONTRACT.md`(#194, #1090)
- `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md` / `docs/analytics/GA4_AMPLITUDE_JOINT_ANALYSIS.md`
- `docs/VERSION_ADOPTION_METRICS_GATE.md`
