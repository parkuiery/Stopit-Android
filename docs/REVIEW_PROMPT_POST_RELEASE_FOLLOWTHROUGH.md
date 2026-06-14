# 리뷰 프롬프트 post-release 재측정 런북

이 문서는 GitHub issue #307 `리뷰 프롬프트 shown 0 지속 원인 재측정 및 14일 평점 추적`의 운영 런북이다.

`docs/REVIEW_PROMPT_LIFECYCLE.md`가 코드 기준 eligibility/drain 계약의 source of truth라면, 이 문서는 **배포 후 지표를 어떻게 다시 읽고 다음 실행을 어떻게 나눌지**를 고정한다.

## 현재 기준선

2026-06-02T18:06:45Z GA4 snapshot (`30daysAgo..yesterday`, property `502544175`) 기준이다. 리뷰 프롬프트 lifecycle 이벤트는 이 시점의 baseline을 유지하고, ASO/Play Console 후행 판단에 쓰는 획득 채널 보조 지표는 #242/#65와 같은 2026-06-14T00:09:03Z live readback으로 최신화한다.

2026-06-04T21:24:42Z / 2026-06-05 06:24:42 KST repo ancestry 재확인 기준으로도 `origin/main` `20b8ff4a`와 최신 SemVer tag `v1.7.7` `f49e7de9`는 PR #308 merge commit `cfff411898fbaac43a5c5bbafb48651091e66be2`와 PR #312 merge commit `e920ea3049bb0a3e192de29d0011298ae9b0a2b5`를 포함하지 않는다. 따라서 이 문서의 현재 수치는 여전히 **PR #308/#312 포함 release 전 baseline**이며, 14일/30일 post-release 판단 창은 아직 시작하지 않는다.

| 지표 | 최근 30일 사용자 수 | 해석 |
| --- | ---: | --- |
| `review_prompt_shown` | 0 | Play review sheet launch 성공이 아직 관측되지 않음 |
| `review_prompt_skipped` | 27 | eligibility 또는 drain 단계에서 중단/보류가 발생 |
| `app_block_intercepted` | 343 | 리뷰 요청 후보가 될 수 있는 성공 사용 신호는 존재 |
| `lock_session_start` | 209 | 잠금 세션 시작 신호도 존재 |
| `first_core_action_completed` | 336 | 첫 핵심 행동 완료 신호는 충분히 관측됨 |
| `activeUsers` | 681 | 분모 기준 |
| `newUsers` | 578 | 2026-06-14T00:09:03Z acquisition readback 기준. 직전 30일 273 대비 +111.7% |
| `Organic Search` 신규 사용자 | 243 | ASO/리뷰 후행 효과 판단 보조 지표. #65 baseline 178을 넘었지만 단독 회복 근거로 승격하지 않음 |
| `Direct` 신규 사용자 | 335 | `335 / 578 = 58.0%`라 Play Console Search/Explore와 external/campaign 확인 전까지 어트리뷰션 누락/외부 유입 가능성을 분리해야 함 |

이 기준선은 PR #226 / tag `v1.7.7` 이후 흐름이 30일 합산 안에 충분히 반영되기 전의 혼합 지표다. 따라서 `shown = 0`만 보고 바로 eligibility 설계를 바꾸지 않는다. 먼저 버전별·배포 후 14일 창으로 다시 분해한다.

### 2026-06-02T18:06:45Z live review lifecycle breakdown

추가 GA4 `runReport`로 `review_prompt_eligible`, `review_prompt_shown`, `review_prompt_skipped`, `review_prompt_failed`를 `eventName × appVersion`으로 재조회했다.

결과:

| eventName | appVersion | users | eventCount | 해석 |
| --- | --- | ---: | ---: | --- |
| `review_prompt_skipped` | `1.7.0` | 18 | 25 | PR #308/#312 이전 cohort. 현재 blocker로 되살리지 않는다. |
| `review_prompt_skipped` | `1.7.3` | 3 | 3 | PR #308/#312 이전 cohort. 현재 blocker로 되살리지 않는다. |
| `review_prompt_skipped` | `1.7.6` | 7 | 12 | PR #308/#312 이전 또는 미포함 cohort로 본다. |
| `review_prompt_eligible` | - | 0 | 0 | 최근 30일 재조회에서 행 없음 |
| `review_prompt_shown` | - | 0 | 0 | 최근 30일 재조회에서 행 없음 |
| `review_prompt_failed` | - | 0 | 0 | 최근 30일 재조회에서 행 없음 |

`customEvent:reason`은 2026-06-02 재조회 기준 GA4 metadata에 등록되어 있고 breakdown도 가능했다. 반면 `customEvent:error`는 아직 metadata에 없으며 `Field customEvent:error is not a valid dimension`으로 실패한다.

| reason | appVersion | users | eventCount | 해석 |
| --- | --- | ---: | ---: | --- |
| `(not set)` | `1.7.0` | 17 | 24 | pre-fix cohort의 주된 noise. 현재 PR #308/#312 후속 판단에 직접 쓰지 않는다. |
| `BelowSessionThreshold` | `1.7.6` | 4 | 5 | 성공 세션 기준 미달. post-PR-308/#312 배포 후에도 지속되면 eligibility threshold를 본다. |
| `RecentEmergencyUnlock` | `1.7.6` | 3 | 3 | 안전 guardrail에 의한 정상 보류 가능성. 완화 후보로 보지 않는다. |
| `(not set)` | `1.7.3` | 2 | 2 | pre-fix cohort noise |
| `(not set)` | `1.7.6` | 2 | 2 | post-release 재측정 때 source를 다시 확인 |
| `AccessibilityOff` | `1.7.6` | 1 | 1 | 접근성 off guardrail |
| `BelowSessionThreshold` | `1.7.0` | 1 | 1 | pre-fix cohort |
| `BelowSessionThreshold` | `1.7.3` | 1 | 1 | pre-fix cohort |
| `QuietHours` | `1.7.6` | 1 | 1 | quiet-hours guardrail |

운영 해석:

- `customEvent:reason`은 더 이상 무조건 GA4 Admin 미등록 경계로 반복 보고하지 않는다. 현재는 조회 가능하므로 #307의 skip reason 판단에는 이 축을 쓸 수 있다.
- `customEvent:error`는 아직 GA4 Admin 등록/metadata 경계다. `review_prompt_failed`가 실제로 발생했을 때 failure 원인 breakdown은 #13 registration follow-through와 연결한다.
- 현재 `review_prompt_skipped`는 모두 PR #308/#312 포함 release가 아닌 버전에서 관측됐다. 따라서 다음 실행은 같은 코드 수정을 다시 만드는 것이 아니라, PR #308/#312 포함 release/tag/track 배포 후 14일 창에서 `eligible/shown/skipped/failed`를 다시 채우는 것이다.

### 2026-06-14T06:07:23Z metrics snapshot smoke

`stopit_metrics_snapshot.py`의 최신 30일 aggregate transport smoke에서는 `review_prompt_shown`이 여전히 `0`이고 `review_prompt_skipped`는 `45 users / 69 events`로 증가했다. 같은 snapshot의 성공 사용 신호는 `app_block_intercepted 490 users`, `lock_session_start 325 users`, `first_core_action_completed 443 users`이며, 최신 관측 version `1.7.7` active share는 `317 / 841 = 37.7%`로 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 `충분`이다.

다만 이 aggregate smoke는 `eventName × appVersion × customEvent:reason` breakdown을 대체하지 않는다. 2026-06-14 재확인 기준 PR #308(`cfff411898fbaac43a5c5bbafb48651091e66be2`)과 PR #312(`e920ea3049bb0a3e192de29d0011298ae9b0a2b5`)는 `origin/develop`에는 포함되어 있지만 `origin/main` `20b8ff4a`와 최신 SemVer tag `v1.7.7`에는 아직 포함되지 않았다. 따라서 `shown = 0` / `skipped 증가`는 **post-PR-308/#312 회귀가 아니라 release/tag/Play deploy 전 baseline smoke**로만 기록하고, D+14 표를 채우기 전까지 #307을 닫지 않는다.

## 관련 source of truth

- 코드 계약: `docs/REVIEW_PROMPT_LIFECYCLE.md`
- 이벤트/파라미터 사전: `docs/ANALYTICS_EVENT_DICTIONARY.md`
- GA4 Admin 등록/metadata 증적: `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- 제품 지표 해석: `docs/METRICS_ANALYSIS.md`, `docs/PRODUCT_METRICS_DASHBOARD.md`
- 버전 채택률/최신 cohort 판독: `docs/VERSION_ADOPTION_METRICS_GATE.md`
- ASO/Play Console 후행 지표: `docs/PLAY_STORE_ASO.md`

## 재측정 전제

### 1. 버전 경계

최소 세 집단으로 나눈다.

| cohort | 기준 | 목적 |
| --- | --- | --- |
| pre-fix | `appVersion < v1.7.7` 또는 PR #226 미포함 버전 | 기존 `REVIEW_PENDING` 소거/launch 실패 문제가 섞인 기준선 |
| post-fix | `appVersion >= v1.7.7` | pending 소거 방지 수정 포함 후 lifecycle 재측정 |
| post-PR-308/#312 | PR #308과 PR #312가 모두 포함된 릴리즈가 배포된 뒤의 버전 | launch failure 후 재시도 계약과 Home `ContextWrapper` activity unwrap 계약까지 포함한 재측정 |

2026-06-02 repo 기준 PR #308과 PR #312는 모두 `develop`에 merge됐다.

| PR | merge commit | repo 내부 의미 | 남은 경계 |
| --- | --- | --- | --- |
| #308 `fix: preserve review pending after launch failure` | `cfff411898fbaac43a5c5bbafb48651091e66be2` | launch 실패 또는 in-flight short-circuit 시 `REVIEW_PENDING`을 유지해 다음 홈 루트 진입에서 재시도하는 코드/테스트/문서 계약 반영 완료 | PR #308 포함 버전 release/internal/production 배포 후 14일·30일 재측정 |
| #310 `test: stabilize home accessibility permission smoke` | `7ec28adc1355c59ee770fc6ec2cedb0275ab0a7d` | PR #308을 막던 runtime smoke gate blocker 해소 완료 | 없음. #307의 남은 blocker로 다시 취급하지 않는다. |
| #312 `fix: unwrap home activity for review prompt drain` | `e920ea3049bb0a3e192de29d0011298ae9b0a2b5` | Home `LocalContext.current`가 `ContextWrapper`로 들어와도 Activity를 unwrap해 `NoActivity` skip이 과대 집계되지 않도록 방어 | PR #312 포함 버전 release/internal/production 배포 후 `NoActivity` 비중과 `shown/skipped/failed` 14일·30일 재측정 |

따라서 다음 docs/metrics run은 “PR #308/#312 merge 여부”를 다시 묻지 말고, **PR #308과 PR #312가 모두 포함된 버전이 실제로 어느 release/tag/track에 배포됐는지**부터 확인한다. 2026-06-02 확인 기준 최신 SemVer tag `v1.7.7`와 `origin/main`에는 두 merge commit이 아직 포함되지 않았고, 2026-06-04T21:24:42Z 재확인에서도 `origin/main` `20b8ff4a`와 최신 tag `v1.7.7` `f49e7de9`는 여전히 두 merge commit을 포함하지 않았다. 아직 배포되지 않았거나 14일 창이 차지 않았으면 issue #307을 닫지 않고 “release/main 반영 → internal/production 배포 → 14일 관측 대기”를 외부 경계로 둔다. 배포 후에도 최신 버전 active share가 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 `보류`이면 `shown = 0`이나 skip reason을 최신 코드 회귀로 단정하지 않는다.

### 2. GA4 queryability 경계

상위 이벤트 count와 breakdown queryability를 분리해서 기록한다. 2026-06-02T18:06:45Z live readback 이후에는 두 축의 상태가 다르다.

- `review_prompt_skipped` by `customEvent:reason`: 조회 가능. #307 skip reason 표와 post-release D+14/D+30 재측정에 사용한다.
- `review_prompt_failed` by `customEvent:error`: 아직 GA4 Admin/metadata 미등록 경계. 실패 이벤트가 생겨도 원인 breakdown은 #13 registration follow-through와 연결한다.

`customEvent:reason`이 다시 `400 INVALID_ARGUMENT` / `Field customEvent:reason is not a valid dimension`로 실패하면 metadata 회귀로 기록한다. `customEvent:error` 실패는 현재 기준 expected external/manual boundary이므로 repo 코드 결론으로 과대해석하지 않는다.

### 3. Play In-App Review 한계

앱/GA4가 알 수 있는 것은 다음뿐이다.

- `review_prompt_eligible`: 리뷰 요청이 arm 됨
- `review_prompt_shown`: Play review sheet launch 성공
- `review_prompt_skipped`: eligibility 또는 drain 단계에서 보류/중단
- `review_prompt_failed`: API/launcher 실패

사용자가 실제로 리뷰를 작성했는지, 취소했는지, 별점을 몇 점 줬는지는 앱 이벤트로 알 수 없다. 후행 결과는 Play Console의 rating count / 평균 평점 / 최근 리뷰 톤으로 본다.

## 재측정 표준 표

### 앱 내부 lifecycle 표

| 기간 | cohort/version | activeUsers | eligible users / events | shown users / events | skipped users / events | failed users / events | shown / eligible | 비고 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| baseline | 전체 최근 30일 | 681 | 0 users / 0 events | 0 users / 0 events | 27 users / 40 events | 0 users / 0 events | N/A | 2026-06-02T18:06:45Z snapshot; skip은 `1.7.0`, `1.7.3`, `1.7.6`에서만 관측 |
| D+14 | `v1.7.7+` | TODO | TODO | TODO | TODO | TODO | TODO | PR #226 포함 후 14일 |
| D+14 | PR #308/#312 포함 버전 | TODO | TODO | TODO | TODO | TODO | TODO | launch failure 재시도 + Activity unwrap 계약 포함 후 14일 |
| D+30 | PR #308/#312 포함 버전 | TODO | TODO | TODO | TODO | TODO | TODO | 30일 후행 확인 |

### skip/failure breakdown 표

`customEvent:reason`은 2026-06-02T18:06:45Z 기준 조회 가능해 baseline을 채운다. `customEvent:error`는 GA4 Admin 등록이 확인된 뒤에만 failure 원인 표를 채운다.

| 기간 | cohort/version | dimension | 값 | users | eventCount | 해석 |
| --- | --- | --- | --- | ---: | ---: | --- |
| baseline | 전체 최근 30일 | `customEvent:reason` | `(not set)` | 21 | 28 | pre-fix cohort noise가 대부분. post-PR-308/#312 판단에 직접 쓰지 않음 |
| baseline | 전체 최근 30일 | `customEvent:reason` | `BelowSessionThreshold` | 6 | 7 | 성공 세션 기준 미달. post-release 후에도 반복되면 threshold 확인 |
| baseline | 전체 최근 30일 | `customEvent:reason` | `RecentEmergencyUnlock` | 3 | 3 | 안전 guardrail 정상 보류 가능성 |
| baseline | 전체 최근 30일 | `customEvent:reason` | `AccessibilityOff` | 1 | 1 | 접근성 off guardrail |
| baseline | 전체 최근 30일 | `customEvent:reason` | `QuietHours` | 1 | 1 | quiet-hours guardrail |
| D+14 | TODO | `customEvent:error` | TODO | TODO | TODO | `customEvent:error`는 2026-06-02 기준 GA4 metadata 미등록. 반복되면 #13 registration 경계와 launcher/API 실패 원인 조사 |

### Play Console 후행 지표 표

| 기간 | rating count | 평균 평점 | 최근 리뷰 톤 | Organic Search 신규 사용자 | listing conversion | 해석 |
| --- | ---: | ---: | --- | ---: | ---: | --- |
| baseline | TODO | TODO | TODO | 243 | TODO | Play Console 수동 기록 필요. `Direct` 신규 335명(58.0%)이라 #242 attribution gate 적용. `Organic Search`가 #65 baseline 178을 넘었더라도 Play Console Search/Explore와 external/campaign 확인 전까지 ASO 회복으로 표현하지 않음 |
| D+14 | TODO | TODO | TODO | TODO | TODO | 앱 내부 shown/skipped와 함께 비교 |
| D+30 | TODO | TODO | TODO | TODO | TODO | 리뷰 성과 최종 판단 |

## 판단 규칙

### A. `eligible > 0`, `shown = 0`, `failed > 0`

우선순위: code-lane follow-through.

- `review_prompt_failed.error` breakdown이 가능하면 반복 error를 확인한다.
- PR #308 포함 여부를 확인한다.
- PR #308 미포함이면 먼저 배포 후 14일 재측정한다.
- PR #308 포함 후에도 failure가 반복되면 `InAppReviewManager` / Play Core launcher 경로를 코드 이슈로 본다.

### B. `eligible > 0`, `shown = 0`, `skipped`가 대부분

우선순위: lifecycle/drain 해석.

- `NotHomeRoot` / `NoActivity` 비중이 높으면 일시 보류가 pending을 유지하고 다음 홈 루트에서 재시도되는지 확인한다. 특히 PR #312 포함 버전 이후에도 `NoActivity`가 높다면 정상 `ContextWrapper` Compose 경로가 아니라 실제 Activity 부재/수명주기 경계인지 다시 본다.
- `AccessibilityOff` / `QuietHours` / `KillSwitch` 비중이 높으면 제품 조건상 노출이 막힌 것이므로 노출 UX를 강제로 완화하지 않는다.
- `NoRecentSuccess`가 높으면 성공 세션 arm 타이밍을 다시 본다. 단, #17에서 “방금 끝난 성공 세션 포함” 계약이 이미 들어간 상태이므로 같은 버그로 되돌리지 않는다.

### C. `eligible = 0`, 성공 사용 신호는 충분함

우선순위: eligibility threshold/remote config 확인.

- `SUCCESSFUL_SESSION_COUNT`, background 관측, 접근성 상태, quiet hours 조건을 코드/지표 기준으로 확인한다.
- debug/dev flavor 지표가 섞였는지 확인한다.
- remote config kill switch가 꺼져 있는지 확인한다.

### D. `shown > 0`, Play rating/review 개선 없음

우선순위: PM/ASO follow-through.

- `shown`은 review 작성 완료가 아니므로 앱 코드 결론으로 과해석하지 않는다.
- rating count / 평균 평점 / 최근 리뷰 톤이 유지 또는 악화되면 문구/타이밍/ASO 품질을 제품 실험 후보로 둔다.
- 긴급해제/안전/핵심 차단 흐름에서 리뷰를 압박하는 실험은 금지한다.

## GitHub Issue / PR handoff 규칙

- repo 내부 문서/계약 정리만 완료되고 배포·14일 관측·Play Console 수동 기록이 남아 있으면 PR은 `Refs #307`을 사용한다.
- PR #308과 PR #312는 이제 merge 완료 상태이므로, 이전 runtime-smoke blocker나 PR #312 merge 대기를 현재 blocker로 반복 보고하지 않는다.
- `review_prompt_skipped.reason`은 2026-06-02T18:06:45Z 기준 조회 가능하므로 #307 skip reason 분석에 사용한다. `review_prompt_failed.error`가 GA4 Admin 미등록으로 조회 불가하면 #13 외부/manual boundary로 명시한다.
- PR #308/#312 포함 버전 배포 후 14일 재측정에서 lifecycle 단계가 정상이고 Play Console 후행 지표까지 기록됐을 때만 issue #307 closure를 검토한다.

## 문서 계약 회귀 테스트

#307 문서 lane이 이 런북을 다시 만질 때는 다음 review prompt post-release boundary regression을 먼저 실행한다.

```bash
python3 -m unittest scripts.tests.test_review_prompt_post_release_followthrough_docs -v
```

이 테스트는 다음 계약을 고정한다.

- PR #308/#312가 이미 `develop`에 merge됐더라도 `origin/main`, SemVer tag, Play track 배포, D+14/D+30 관측 전에는 `shown = 0`을 최신 코드 회귀로 단정하지 않는다.
- 2026-06-04T21:24:42Z repo ancestry 재확인 기준 `origin/main` `20b8ff4a`와 `v1.7.7` `f49e7de9`는 PR #308/#312 merge commit을 포함하지 않으므로, 현재 baseline은 post-PR-308/#312 성과 판독 창이 아니다.
- `customEvent:reason`은 `review_prompt_skipped` breakdown에 사용할 수 있지만, `customEvent:error`는 `review_prompt_failed` breakdown의 GA4 Admin/metadata 외부 경계로 유지한다.
- Play In-App Review API는 사용자가 실제로 리뷰를 작성했는지 알려주지 않으므로, Play Console rating count / 평균 평점 / 최근 리뷰 톤을 후행 지표로 별도 기록한다.
- repo 내부 문서/계약만 갱신한 PR은 `Refs #307`을 사용하고, 배포·14일 관측·Play Console 수동 기록까지 끝나기 전에는 `Closes #307`로 승격하지 않는다.

## 다음 run 체크리스트

- [x] PR #308 상태와 head SHA를 확인한다. 2026-06-02 기준 merged: `cfff411898fbaac43a5c5bbafb48651091e66be2`.
- [x] PR #312 상태와 head SHA를 확인한다. 2026-06-02 기준 merged: `e920ea3049bb0a3e192de29d0011298ae9b0a2b5`.
- [x] PR #308/#312 포함 버전이 release/internal/production 어디까지 배포됐는지 확인한다. 2026-06-04T21:24:42Z 기준 `origin/main` `20b8ff4a`와 최신 SemVer tag `v1.7.7` `f49e7de9` 모두 두 merge commit을 포함하지 않아, release/main 반영 전 경계로 확인됨.
- [ ] PR #308/#312 포함 버전이 `origin/main`/SemVer tag/Play internal 또는 production track에 실제 반영된 뒤 D+14 관측 시작일을 기록한다.
- [ ] `review_prompt_eligible/shown/skipped/failed`를 version별로 재조회한다.
- [x] `customEvent:reason` metadata 등록 여부를 확인한다. 2026-06-02T18:06:45Z 기준 등록/조회 가능.
- [ ] `customEvent:error` metadata 등록 여부를 확인한다. 2026-06-02T18:06:45Z 기준 미등록 / `Field customEvent:error is not a valid dimension`.
- [ ] Play Console rating count / 평균 평점 / 최근 리뷰 톤 baseline을 수동 기록한다.
- [ ] D+14 / D+30 표를 갱신하고, code-lane/PM-lane 후속이 필요한지 판단한다.
