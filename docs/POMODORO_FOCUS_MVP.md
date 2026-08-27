# 뽀모도로 집중 세션 MVP

Issue: (미발행 — 아이데이션 단계. `docs/ops/stopit/recent-decisions.md`의 "기능/실험 아이디어는 먼저 Discord 아이데이션 채널로 보내고, 실행 단위가 명확할 때만 GitHub Issue로 전환한다"를 따른다.)

이 문서는 **뽀모도로 집중 세션**의 제품/도메인/analytics/QA/배포 경계 계약을 고정한다.

이 기능은 `docs/RETENTION_DIAGNOSIS_2026_08.md`의 단계 1·2와 **다른 종류의 베팅**이다. 단계 1이 이미 있는 사용자를 넛지로 붙잡는 리텐션 패치라면, 이것은 앱을 여는 이유 자체를 새로 만드는 카테고리 베팅이다. 따라서 판정 지표와 배포 순서를 단계 1과 분리해서 관리한다. 이 구분을 잃고 "리텐션 개선 기능" 하나로 묶으면 성공/실패 판독이 불가능해진다.

## 한 줄 목표

사용자가 앱을 열어 `집중 25분 → 휴식 5분` 사이클을 돌리고, **그 사이클 전 구간에서 방해 앱이 실제로 차단되며**, 오늘 몇 번 집중했는지가 남게 한다.

## 제품 의도

### 왜 지금 이 기능인가

`docs/RETENTION_DIAGNOSIS_2026_08.md` 2.3이 "재방문 트리거가 0개"라고 진단했다. 그런데 `block_source` 분해를 보면 차단 가치의 60.0%(`routine` 14,450 / 24,070)를 루틴이 만든다. **루틴은 차단을 만들지만 앱을 열 이유를 만들지 않는다.** 한 번 설정하면 배경에서 조용히 돈다.

그래서 단계 1의 세 항목이 전부 *바깥에서 안으로 부르는* 구조다 — 아침 알림, 홈 상단 카드, 수정 유도. 뽀모도로는 반대로 **사용자가 스스로 앱을 여는 것에서 시작**한다. 공부를 시작할 때 열고, 4사이클이면 하루에 네 번 연다.

같은 문서 4장이 남긴 비대칭도 같은 방향을 가리킨다.

| 차단 화면에서의 선택 | D7 | D14 |
| --- | ---: | ---: |
| 순응 (`first_core_action_completed`) | `27/281 = 9.6%` | `15/215 = 7.0%` |
| 우회 (`emergency_unlock_used`) | `68/236 = 28.8%` | `44/181 = 24.3%` |

4장이 명시한 대로 이 수치는 인과로 읽으면 안 된다(반복 이벤트 코호트에 기존 유저가 섞인다). 다만 방향만 취하면 **앱 안에서 무언가를 하는 사람이 남는다**이고, 뽀모도로는 정확히 그 행동을 매일 만드는 장치다.

### 가장 큰 리스크 — 새 모드의 기저율

이 앱에서 "새 모드"를 만들었을 때의 실측치가 이미 두 개 있다.

| 기능 | `block_source` users |
| --- | ---: |
| 목표 잠금 | 30 |
| 부모 모드 | 15 |

`docs/RETENTION_DIAGNOSIS_2026_08.md` 3장이 "신규 기능 확장"을 "지금 하지 않는 것"에 넣은 근거가 이 두 숫자다. **뽀모도로가 왜 다를 것인지에 대한 답이 없으면 이 문서를 실행으로 옮기지 않는다.**

현재 가진 답은 하나뿐이다. 목표 잠금과 부모 모드는 *기존 사용자의 기존 의도*를 깊게 판 기능이고, 뽀모도로는 *다른 의도를 가진 다른 사용자*를 데려온다. 그렇다면 결론이 하나 따라온다 — **메뉴에 항목 하나 추가하는 방식으로 출시하면 반드시 30명이 된다.** 스토어 등록정보·스크린샷·키워드까지 포지셔닝을 함께 바꿔야 효과가 나온다. 이건 코드 범위 밖이고 되돌리기 어려우므로 아래 "결정 대기 항목"에서 별도로 다룬다.

유입이 `google-play organic 540 / direct 65 / google organic 1`로 사실상 100% 오가닉이라, 리뷰 프롬프트(단계 0) 말고 남은 성장 레버가 카테고리 확장뿐이라는 점이 이 베팅의 배경이다.

## MVP 범위

### 포함

1. **사이클 프리셋** — `25/5`, `50/10` 두 가지. 4번째 집중 후 긴 휴식(각각 15분 / 20분).
2. **세션 실행과 복원** — 페이즈(집중/짧은 휴식/긴 휴식), 사이클 인덱스, 페이즈 만료 시각을 저장하고 앱이 죽거나 재부팅돼도 벽시계 기준으로 복원한다.
3. **사이클 전 구간 차단 연속성** — 아래 "차단 판단 정책"의 핵심 계약. 휴식 중에도 차단을 풀지 않는다.
4. **앱 내 휴식 화면** — 남은 휴식 시간, 다음 사이클 번호, 지금 종료 진입점.
5. **오늘 완료 사이클 수** — 세션 화면과 종료 화면에서 확인. 별도 통계 지면은 만들지 않는다.
6. **언제든 종료** — 확인 1회, 비난 없는 톤.
7. **차단 앱 선택 재사용** — 기존 앱 선택 도메인/UI를 그대로 쓴다. 뽀모도로 전용 앱 목록을 새로 만들지 않는다.
8. **analytics 계약** — 아래 표.

### 제외

| 항목 | 이유 |
| --- | --- |
| 태스크/할 일 목록 연동 | 차단 정체성 밖이고, 사용자 입력 문구가 analytics·공유 privacy 경계를 넓힌다 |
| 주간 그래프 / 통계 대시보드 | `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`(#465) 지면과 중복. 필요해지면 그쪽에 붙인다 |
| 사운드 / 화이트노이즈 / 앰비언스 | 범위가 크고 이 베팅의 가설과 무관 |
| 스트릭 · 나무 심기류 게이미피케이션 | `docs/RETENTION_DIAGNOSIS_2026_08.md` 4장이 마찰 강화를 보류시켰다. 연속 기록을 끊는 압박은 "집중 실패" 뉘앙스를 노출하지 말라는 제품 원칙과도 충돌한다 |
| 커스텀 사이클 길이 | 2차 후보. MVP는 프리셋 2종으로 채택률부터 본다 |
| 뽀모도로 리마인더 알림 | 단계 1의 신규 알림 채널과 같은 지면을 두고 경쟁한다. 순서가 정해지기 전에는 넣지 않는다 |
| 위젯 / 퀵세팅 타일 | 진입점 확장은 채택률 확인 후 |

## 상태/도메인 계약

구현 이름은 code lane이 확정하되, 정책 테스트는 아래 개념을 보존해야 한다.

| 필드 | 의미 | 주의 |
| --- | --- | --- |
| `preset` | `25_5`, `50_10` | analytics는 enum만. 분 단위 원문 전송 금지 |
| `phase` | `focus`, `short_break`, `long_break` | 휴식도 세션의 일부이며 차단 활성 구간이다 |
| `cycleIndex` | 현재 사이클 번호(1-based) | analytics는 bucket으로만 |
| `phaseDeadline` | 현재 페이즈 만료 `Instant` | `docs/MANUAL_TIMER_LOCK_DEADLINE_CONTRACT.md`와 같은 ISO-8601 `Instant` 규약을 따른다. `LocalDateTime` 저장 금지 |
| `sessionStartedAt` | 세션 시작 `Instant` | raw 값은 analytics 금지. 경과 시간 bucket만 |
| `completedFocusCount` | 완료한 집중 페이즈 수 | 휴식은 세지 않는다 |
| `status` | `active`, `completed`, `ended_early` | 종료 사유를 구분해서 기록한다 |

`phaseDeadline`을 `Instant`로 고정하는 이유는 기존 계약과 같다 — timezone이 바뀌어도 같은 실제 시각에 만료되어야 한다.

### 차단 판단 정책

정책 helper는 Android framework 없이 JVM 테스트 가능한 순수 함수로 먼저 만든다. `ManualLockTimePolicy`가 선례다.

**핵심 계약 — 휴식 중에 차단을 풀지 않는다.**

휴식 5분에 차단을 해제하면 `KeepAccessibilityService`의 차단 판정에 "의도적 비차단" 상태가 하나 생기고, 긴급해제·부모 모드·목표 잠금·루틴과의 우선순위를 전부 다시 정의해야 한다. #1177급 작업이 다시 발생한다. 그보다 큰 문제는 제품 쪽이다 — 사용자가 앱에서 이탈하는 지점이 정확히 그 휴식 구간이다.

대신 **휴식 화면을 앱 안에 둔다.** 앱 자체가 휴식이 된다. 차단 상태는 세션 내내 연속이라 상태 기계가 늘지 않고, 휴식 시간이 그대로 인앱 체류로 바뀐다.

필수 정책 케이스:

1. `focus` 진행 중 — 선택 앱 차단.
2. `short_break` / `long_break` 진행 중 — **차단 유지.** 이 케이스가 깨지면 계약 위반이다.
3. 세션 완료 또는 사용자 종료 이후 — 차단 없음.
4. 앱 종료 후 재실행 — 벽시계 기준으로 현재 페이즈를 재계산한다. 여러 페이즈가 지났으면 지난 집중 페이즈만 `completedFocusCount`에 반영하고 휴식은 소비된 것으로 본다.
5. timezone 변경 — `Instant` 기준이므로 만료 시각이 이동하지 않는다.
6. 부모 모드 활성 중 — 뽀모도로 세션을 시작할 수 없다. 부모 모드가 최상위 계약이다.
7. 루틴/목표 잠금이 이미 차단 중인 앱 — 뽀모도로 세션이 기존 차단을 **약화시키지 않는다.** 세션이 끝나도 루틴/목표 잠금 차단은 그대로 유지된다.
8. 긴급해제 — 뽀모도로 차단은 열 수 있다. 자기통제 잠금이므로 #1177에서 부모 모드를 막은 것과 반대 방향이다.

`block_source` 우선순위 확정은 code lane 몫이되, 6·7번은 정책 테스트로 고정한다.

### Analytics block source

`AnalyticsBlockSource`에 `pomodoro`를 추가한다. 기존 5종(`manual_keep`, `timed_lock`, `routine`, `goal_lock`, `parent_mode`)의 의미는 바꾸지 않는다. 뽀모도로 세션 중 차단은 `timed_lock`으로 합산하지 않는다 — 합치면 이 베팅의 성과를 기존 타이머와 분리할 수 없다.

## UX / 카피 계약

### 진입점

**홈 상단에 카드를 새로 추가하지 않는다.** `docs/HOME_STATUS_CTA_STRUCTURE.md`가 superseded되면서 남긴 경고 그대로다 — 상단에 카드를 더 붙이면 #1151(큰 글꼴에서 잠금 스위치가 스크롤 밖으로 밀림)이 악화된다. 카드 한 장 선택은 `HomeCardArbiter`가 소유하고 있고, 여기에 `HomeCard` variant를 늘리는 것도 MVP 범위 밖이다.

MVP 진입점은 홈의 기존 보조 진입점 계열(타이머 설정과 같은 위계)과 메뉴 두 곳으로 한정한다. 세션 진행 중 상태 표시는 새 카드가 아니라 기존 active lock status 표면을 재사용한다.

### 톤

`docs/BLOCK_SCREEN_COPY_HIERARCHY.md`(#464)의 코칭 톤을 그대로 따른다. 이 기능에서 특히 금지할 것:

- 휴식을 건너뛰라고 부추기는 문구.
- 중단·종료를 실패로 그리는 문구("포기하시겠어요?", "아쉽네요" 류).
- 연속 기록 단절을 경고하는 문구.
- 오늘 사이클 수가 0인 상태를 부정적으로 묘사하는 문구.

`docs/RETENTION_DIAGNOSIS_2026_08.md` 4장이 완주 압박의 근거를 뒤집었다는 점을 기억한다. 완주율을 올리는 카피가 `app_remove`를 올리면 순손실이다.

### 종료

종료 확인은 1회. 되돌리기 어려운 동작이 아니므로 마찰을 더 넣지 않는다.

## Analytics 계약

| 이벤트 | 트리거 | 파라미터 | 민감 정보 정책 |
| --- | --- | --- | --- |
| `pomodoro_session_started` | 세션 시작 | `preset`, `entry_surface`, `selected_app_count_bucket` | 앱 이름/package 금지 |
| `pomodoro_focus_completed` | 집중 페이즈 만료 | `preset`, `cycle_index_bucket` | raw 사이클 번호 금지 |
| `pomodoro_break_started` | 휴식 페이즈 진입 | `preset`, `break_type` | — |
| `pomodoro_session_ended` | 세션 종료(전체 완료·사용자 종료·복원 만료) | `preset`, `end_reason`, `completed_focus_count_bucket`, `elapsed_minutes_bucket` | raw timestamp 금지 |
| `app_block_intercepted` | 기존 이벤트 | `block_source=pomodoro` 추가 | 기존 계약 유지 |

권장 enum/bucket:

- `preset`: `25_5`, `50_10`
- `entry_surface`: `home`, `menu`
- `break_type`: `short`, `long`
- `end_reason`: `all_cycles_completed`, `user_ended`, `expired_recovery`
- `cycle_index_bucket`: `1`, `2_3`, `4_6`, `7_plus`
- `completed_focus_count_bucket`: `0`, `1`, `2_3`, `4_6`, `7_plus`
- `elapsed_minutes_bucket`: `0_9`, `10_29`, `30_59`, `60_119`, `120_plus`
- `selected_app_count_bucket`: 기존 `1`, `2_3`, `4_6`, `7_plus` 재사용

금지: 앱 이름·package·목록, raw timestamp, raw 사이클/분 값, 사용자 입력 문구.

### Amplitude allowlist

`AmplitudeEventAllowlist.kt`는 opt-in이므로 새 이벤트는 추가하지 않으면 Amplitude에 도달하지 않는다. 추가 후보는 `pomodoro_session_started`와 `pomodoro_session_ended` **두 개만**이다. 페이즈 단위 이벤트는 사용자당 발생량이 커서 `AMPLITUDE_MONTHLY_EVENT_CAP`(현재 `950`)을 흔든다.

allowlist에 추가할 때는 `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md`의 `cap × maxMTU < 2,000,000` 계산을 다시 한다. 예상 이벤트 증가량을 계산하지 않고 allowlist만 늘리지 않는다.

## 측정 계획

**이 기능의 성공 판정에 `docs/RETENTION_DIAGNOSIS_2026_08.md` 단계 1의 지표표를 그대로 쓰지 않는다.** 카테고리 베팅은 다른 축으로 본다.

### 14일 확인

| 분자 / 분모 | 의미 | 참고 기준선 |
| --- | --- | --- |
| `pomodoro_session_started` users / active users | 채택률 | 목표 잠금 30명 / 부모 모드 15명이 새 모드의 기저율 |
| `pomodoro_focus_completed` ≥1 users / `pomodoro_session_started` users | 첫 집중 완주 | — |
| 완료 집중 페이즈 수 / 시작 집중 페이즈 수 | 사이클 완주율 | 기존 세션 완주율 `79 / 2,054 = 3.8%`와 **직접 비교하지 않는다.** 단위가 다르다(사이클 vs 세션). 별도 지표로 따로 추적한다 |
| 뽀모도로 사용자의 일간 앱 오픈 수 | 이 베팅의 핵심 가설 | 전체 세션/유저 `11.7` |
| `app_block_intercepted` where `block_source=pomodoro` | 실제 차단 가치 발생 | NSM 원천에 합산됨 |

### 30일 확인

- 뽀모도로 사용자 코호트의 GA4 D7 / D30 잔존 vs 전체 신규.
- `newUsers` 추이와 유입 채널 구성 변화 — 포지셔닝을 함께 바꿨을 때만 유효하다.
- Play Console 스토어 리스팅 visitors / acquisitions / CVR. `docs/PLAY_STORE_ASO.md`의 attribution gate를 따른다.
- 기존 루틴/타이머 cannibalization 여부.

### Guardrail

- crash-free users, `app_exception` users / activeUsers.
- `app_remove` / `newUsers` — 현재 `434 / 606 = 71.6%`. 이 값이 나빠지면 기능 성과와 무관하게 멈춘다.
- 접근성 권한 이탈.
- 긴급해제 비율.
- Play Console rating / review 톤 — "강압적", "휴식인데 안 풀린다" 류가 생기는지. 휴식 중 차단 유지는 의도된 설계지만 사용자가 버그로 읽을 수 있다.

## 배포 게이트

**단계 1과 같은 릴리즈에 태우지 않는다.** `docs/RETENTION_DIAGNOSIS_2026_08.md`가 "한 번에 하나만 바꾼다"고 고정했고, 두 변경이 섞이면 D7이 움직여도 원인을 분리할 수 없다.

| 활동 | 단계 1 리드백 전 | 리드백 후 |
| --- | --- | --- |
| 구현 | 진행 가능 | — |
| 배포 | **금지** | 가능 |

대안 경로가 하나 있다 — 단계 1을 명시적으로 접고 이 기능을 그 자리에 놓는 것. 둘 다 유효한 선택이지만, **둘 다 열어둔 채 동시에 배포하는 것만은 하지 않는다.** 어느 쪽을 고르든 결정은 문서로 남긴다.

단계 0(`SESSION_THRESHOLD = 1`, `AMPLITUDE_MONTHLY_EVENT_CAP = 950`)은 이미 코드에 반영되어 있다. 다만 `origin/main` / production tag 포함 여부와 라이브 확인은 별개 경계다.

## QA / 테스트 체크리스트

### JVM 정책 테스트

- 집중 페이즈 진행 중 차단 활성.
- **휴식 페이즈 진행 중 차단 활성** — 핵심 계약.
- 세션 종료 후 차단 비활성.
- 앱 재실행 시 벽시계 기준 페이즈 복원. 여러 페이즈 경과 시 집중만 완료 카운트에 반영.
- timezone 변경 후에도 `phaseDeadline` 실제 시각 유지.
- 부모 모드 활성 중 세션 시작 차단.
- 루틴/목표 잠금 차단이 뽀모도로 세션 종료로 해제되지 않음.
- `pomodoro_session_ended`의 `end_reason` 3종 분기.
- bucket 경계값(`cycle_index_bucket`, `elapsed_minutes_bucket`).
- analytics payload에 raw 값/앱 이름이 포함되지 않음.

### Compose / UI 테스트

- 집중·짧은 휴식·긴 휴식 각 상태의 화면 텍스트.
- 종료 확인 다이얼로그.
- 오늘 완료 사이클 수 표시.
- 접근성 — 남은 시간과 현재 페이즈가 색상이 아닌 텍스트로 전달되는지. TalkBack content description.
- 큰 글꼴(#1151 관련) 레이아웃에서 진입점과 종료 CTA가 스크롤 밖으로 밀리지 않는지.

### 실기기 / 수동 QA

1. 앱 선택 후 `25/5` 세션 시작.
2. 집중 중 대상 앱 실행 → 차단 확인, `block_source=pomodoro` 확인.
3. 휴식 진입 → **대상 앱 실행 시 여전히 차단되는지 확인.**
4. 휴식 중 앱 강제 종료 → 재실행 시 페이즈/남은 시간 복원 확인.
5. 세션 중 timezone 변경 → 만료 시각이 이동하지 않는지 확인.
6. 4사이클 완주 → 긴 휴식 진입 확인.
7. 세션 도중 종료 → `end_reason=user_ended` 확인.
8. 루틴이 동시에 도는 시간대에 세션 시작/종료 → 루틴 차단 유지 확인.
9. 부모 모드 활성 중 세션 시작 시도 → 차단 확인.
10. TalkBack으로 집중/휴식 화면 순회.

## 외부 / manual 경계

아래는 구현 완료와 별개다. 이 경계가 끝나기 전에 성과를 판단하지 않는다.

- GA4 Admin custom dimension 등록 및 metadata readback (`docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`).
- `docs/ANALYTICS_EVENT_DICTIONARY.md` 갱신.
- Amplitude allowlist 추가 시 캡 재계산.
- release / tag / Play deploy. tag-triggered CD는 기본적으로 internal track이다.
- Play Console 스토어 등록정보·스크린샷·키워드 반영 (포지셔닝을 바꾸기로 결정한 경우).
- D+14 / D+30 readback.

## 결정 대기 항목

실행으로 옮기기 전에 답이 필요한 것들이다. 코드 lane이 임의로 정하지 않는다.

1. **포지셔닝을 바꾸는가.** 메뉴 항목으로만 출시하면 기저율(30명)을 벗어날 근거가 없다. 스토어 등록정보·스크린샷·키워드까지 바꿀 의사가 없다면 이 문서는 실행하지 않는 것이 낫다.
2. **단계 1과의 순서.** 리드백 후에 넣을 것인가, 단계 1을 접고 대체할 것인가.
3. **커스텀 사이클 길이를 MVP에 넣을 것인가.** 현재 문서는 제외로 두었다.
4. **휴식 중 차단 유지를 사용자에게 어떻게 설명할 것인가.** 설계 의도지만 버그로 읽힐 수 있다. 카피 초안이 필요하다.

## 연관 문서

- `docs/RETENTION_DIAGNOSIS_2026_08.md` — 병목 위치, 단계 0/1/2, 마찰 강화 보류 근거. 이 문서의 전제.
- `docs/MANUAL_TIMER_LOCK_DEADLINE_CONTRACT.md` — `Instant` deadline 저장/해석 규약.
- `docs/BLOCK_SCREEN_COPY_HIERARCHY.md` — 코칭 톤 계약.
- `docs/GOAL_LOCK_MVP.md`, `docs/PARENT_MODE_MVP.md` — 새 모드의 선례와 기저율.
- `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md` — allowlist / 캡 계산.
- `docs/PLAY_STORE_ASO.md` — 유입 attribution gate.
- `docs/ANALYTICS_EVENT_DICTIONARY.md`, `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` — 등록 경계.
