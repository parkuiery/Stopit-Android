# 리뷰 프롬프트 라이프사이클 운영 문서

이 문서는 GitHub issue #17 `리뷰 프롬프트 생애주기 개선으로 신뢰 리뷰 확보`의 문서 slice다.

목적은 다음 세 가지를 코드 기준으로 고정하는 것이다.

1. 언제 `review_prompt_eligible` / `shown` / `skipped` / `failed`가 발생하는지
2. 어떤 DataStore 상태가 리뷰 요청을 arm/drain 하는지
3. GA4와 Play Console에서 무엇을 볼 수 있고, 무엇은 볼 수 없는지

> 핵심 주의: Play In-App Review는 앱 코드에서 사용자가 실제로 리뷰를 남겼는지, 취소했는지, 별점을 몇 점 줬는지를 알려주지 않는다. 따라서 `accepted` / `dismissed`를 앱 계측 이벤트로 가정하면 안 된다. 실제 후행 결과는 Play Console의 rating count / 평균 평점 변화로만 추적한다.

## 소스 오브 트루스

앱 코드 기준 파일:

- `app/src/main/java/com/uiery/keep/feature/review/ReviewEligibilityDecision.kt`
- `app/src/main/java/com/uiery/keep/feature/review/ReviewEligibilityEvaluator.kt`
- `app/src/main/java/com/uiery/keep/feature/review/InAppReviewManager.kt`
- `app/src/main/java/com/uiery/keep/feature/review/AppLifecycleTracker.kt`
- `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt`
- `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt`
- `app/src/main/java/com/uiery/keep/datastore/DataStore.kt`

테스트 기준 파일:

- `app/src/test/java/com/uiery/keep/feature/review/ReviewEligibilityEvaluatorTest.kt`
- `app/src/test/java/com/uiery/keep/feature/review/InAppReviewManagerTest.kt`
- `app/src/test/java/com/uiery/keep/feature/home/HomeViewModelReviewTest.kt`
- `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`

운영/조회성 기준 문서:

- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/METRICS_ANALYSIS.md`

## 전체 흐름

### 1. 성공 세션 종료 시 arm 판단

리뷰 프롬프트는 잠금 세션이 정상 종료될 때 바로 띄우는 구조가 아니다.

현재 흐름:

1. `LockViewModel`이 성공 세션 종료 시 `SUCCESSFUL_SESSION_COUNT`를 증가시킨다.
2. `ReviewEligibilityEvaluator.evaluate(...)`가 사전 조건을 검사한다.
3. 결과가 `Eligible`이면:
   - `REVIEW_PENDING = true`
   - analytics: `review_prompt_eligible`
4. 결과가 `Ineligible`이면:
   - analytics: `review_prompt_skipped(reason=<SkipReason.name>)`
   - `REVIEW_PENDING`은 켜지지 않는다.

즉, `review_prompt_eligible`은 "즉시 노출 완료"가 아니라 "다음 홈 진입에서 노출 시도를 할 수 있게 arm 됨"을 뜻한다.

### 2. 홈 루트에서 drain 시도

실제 노출 시도는 `HomeViewModel.maybeDrainReviewFlag(activity)`에서 일어난다.

동작 순서:

1. `REVIEW_PENDING`이 false면 아무 것도 하지 않는다.
2. 홈에서 bottom sheet가 열린 상태(`sheetVisible`)면:
   - analytics: `review_prompt_skipped(reason=NotHomeRoot)`
   - `REVIEW_PENDING`을 유지한다.
   - bottom sheet가 닫힌 뒤 다음 drain에서 다시 live eligibility를 평가한다.
3. `reviewEligibility.evaluateLive()`가 실패하면:
   - analytics: `review_prompt_skipped(reason=<SkipReason.name>)`
   - `REVIEW_PENDING = false`
4. `activity == null`이면:
   - analytics: `review_prompt_skipped(reason=NoActivity)`
   - `REVIEW_PENDING`을 유지한다.
   - 다음 홈 루트 진입에서 다시 시도한다.
5. 위 조건을 모두 통과하면:
   - 먼저 `REVIEW_PENDING = false`
   - 그 다음 `InAppReviewManager.launchIfReady(activity)` 호출

`sheetVisible`과 `activity == null`에서 pending을 유지하는 이유는, eligibility는 확보됐지만 일시적 UI/Activity 문맥 때문에 지금 노출할 수 없는 순간의 재시도를 허용하기 위해서다. 반대로 `evaluateLive()`가 실패한 경우는 현재 노출 조건이 실제로 깨진 것이므로 pending을 삭제하고 명시적인 skip reason을 기록한다.

### 3. 실제 launch 결과 기록

`InAppReviewManager`는 중복 실행을 막기 위해 `AtomicBoolean inFlight`를 사용한다.

결과별 기록:

- `ReviewLaunchResult.Success`
  - `LAST_REVIEW_PROMPT_AT_MS = now`
  - analytics: `review_prompt_shown`
- `ReviewLaunchResult.Failure(error)`
  - analytics: `review_prompt_failed(error=<error>)`
  - cooldown timestamp는 기록하지 않는다.

여기서 `shown`은 Play review sheet launch 성공을 뜻한다. 사용자가 실제로 리뷰를 남겼는지는 앱에서 관측할 수 없다.

## Eligibility 규칙

`ReviewEligibilityEvaluator.evaluate(...)` 기준 현재 규칙은 아래 순서대로 short-circuit 된다.

| 순서 | 조건 | 실패 시 reason |
| --- | --- | --- |
| 1 | remote config kill switch가 꺼져 있음 | `KillSwitch` |
| 2 | debug build | `Debug` |
| 3 | `dev` flavor | `DevFlavor` |
| 4 | 접근성 권한/상태 미충족 | `AccessibilityOff` |
| 5 | quiet hours (로컬 시간 01:00~05:59) | `QuietHours` |
| 6 | `SUCCESSFUL_SESSION_COUNT < 3` | `BelowSessionThreshold` |
| 7 | 최근 90일 내 prompt shown 기록 존재 | `WithinCooldown` |
| 8 | background 이력 없음 | `NoBackgroundingObserved` |
| 9 | 마지막 background 후 1.5초 이내 같은 세션으로 판단 | `WithinSameSession` |
| 10 | 최근 7일 긴급해제 2회 이상 | `RecentEmergencyUnlock` |
| 11 | 최근 24시간 성공 세션 1회 미만 | `NoRecentSuccess` |

`NoRecentSuccess` 판단은 홈 복귀 직전에 arm 평가를 트리거한 **방금 끝난 성공 세션도 포함**한다. 즉, 아직 `lock_history`에 current session insert가 반영되기 전이라도 현재 성공 세션 자체 때문에 최근 성공 조건을 만족할 수 있다.

모든 조건을 통과하면 `Eligible`이다.

### evaluateLive()와의 차이

`HomeViewModel`이 drain 단계에서 호출하는 `evaluateLive()`는 더 가벼운 재검증이다.

재검증 항목:

- `KillSwitch`
- `AccessibilityOff`
- `QuietHours`

즉, 세션 수, cooldown, background 이력, 최근 성공 세션 수 같은 조건은 arm 시점에서 이미 통과했다고 보고 다시 확인하지 않는다.

## DataStore 상태 계약

| Key | 역할 |
| --- | --- |
| `REVIEW_PENDING` | 홈 루트에서 리뷰 노출 시도를 해야 하는지 나타내는 arm flag |
| `LAST_REVIEW_PROMPT_AT_MS` | 마지막 `review_prompt_shown` 시각. 90일 cooldown 계산에 사용 |
| `SUCCESSFUL_SESSION_COUNT` | 누적 성공 세션 수. 최소 3회 이상부터 eligibility 가능 |
| `LAST_BACKGROUNDED_AT_MS` | 같은 세션 즉시 재노출 방지용 background 시각 |

관련 참고:

- `AppLifecycleTracker`가 앱 background 시 `LAST_BACKGROUNDED_AT_MS`를 기록한다.
- `LockViewModel`이 성공 세션 종료 시 `SUCCESSFUL_SESSION_COUNT`를 증가시킨다.

## Analytics 이벤트 계약

| 이벤트 | 파라미터 | 의미 |
| --- | --- | --- |
| `review_prompt_eligible` | 없음 | 리뷰 프롬프트가 arm 됨 (`REVIEW_PENDING = true`) |
| `review_prompt_shown` | 없음 | Play review sheet launch 성공 |
| `review_prompt_skipped` | `reason` | eligibility 실패 또는 홈 drain 단계에서 노출 보류/중단 |
| `review_prompt_failed` | `error` | Play review launcher/API 실패 |

### `reason` 값 해석

현재 코드상 `reason`은 `SkipReason.name` 문자열이다.

문서상 특히 자주 볼 가능성이 높은 값:

- `BelowSessionThreshold`
- `WithinCooldown`
- `AccessibilityOff`
- `QuietHours`
- `NoBackgroundingObserved`
- `WithinSameSession`
- `RecentEmergencyUnlock`
- `NoRecentSuccess`
- `NotHomeRoot`
- `NoActivity`

주의:

- `SkipReason` enum에는 `NoGooglePlay`, `AlreadyToday`, `NotificationOff`도 정의돼 있다.
- `ReviewEligibilityEvaluator` / `HomeViewModel` / `InAppReviewManager` 경로만 보면 이 값들이 실제로 emitted 되는 코드는 확인되지 않는다.
- 따라서 대시보드나 분석 쿼리에서 이 값들을 기대값으로 고정하지 말고, "정의됨"과 "현재 실제 사용 중"을 구분해서 본다.

### 현재 #13 queryability 경계

2026-05-29 live 확인 기준으로 review 해석에 필요한 `customEvent:*` 축은 아직 GA4 Admin에 materialize되지 않았다.

- metadata 결과: `customUser:routines_count`만 확인, `customEvent:*`는 없음
- review smoke (`review_prompt_skipped` by `customEvent:reason`):
  - `400 INVALID_ARGUMENT`
  - `Field customEvent:reason is not a valid dimension.`

따라서 현재는 `review_prompt_eligible` / `shown` / `skipped` / `failed`의 상위 event count 자체는 볼 수 있어도, skip/failure 사유 분포를 GA4에서 안정적으로 분해하는 작업은 아직 낮은 confidence 상태다.

추가 주의:

- 이번 live smoke는 `customEvent:reason` 기준의 대표 실패 증거를 남긴 것이다.
- `review_prompt_failed.error` 같은 failure 세부 축도 별도로 조회 가능하다고 가정하지 않는다. `reason`이 막혀 있으면 `error`도 동일하게 GA4 Admin 등록/metadata 확인 전에는 외부 경계로 둔다.

운영 원칙:

- 리뷰 지표를 해석할 때 `reason` / `error` 축이 실제로 등록됐는지 먼저 확인한다.
- 등록 전에는 "특정 reason이 많다"는 결론을 대시보드 전제로 단정하지 않는다.
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 trust/review 등록 순서와 metadata 확인 절차를 선행한 뒤 세부 reason 분석을 한다.

## 무엇을 측정할 수 있고 없는가

### 앱/GA4에서 직접 측정 가능

- `review_prompt_eligible` 발생량
- `review_prompt_shown` 발생량
- `review_prompt_skipped` 발생량
- `review_prompt_failed` 발생량

### GA4 Admin 등록 후에만 안정적으로 분해 가능한 것

- `review_prompt_skipped`의 skip reason 분포
- `review_prompt_failed`의 error / failure reason 분포

### 앱/GA4에서 직접 측정 불가

- 사용자가 실제로 리뷰를 남겼는지
- 별점을 몇 점 줬는지
- review sheet를 열고 닫았는지

### 후행 결과는 어디서 보나

Play Console에서 다음을 후행 지표로 본다.

- rating count
- 평균 평점
- Organic Search 신규 사용자 추이
- listing 변경 전후 설치 전환율

## 운영 해석 가이드

### 좋은 신호

- `eligible`는 꾸준히 발생하지만 `skipped` 비중이 과도하지 않다.
- `shown`이 완전히 0이 아니다.
- 후행 14일/30일 창에서 rating count 또는 평균 평점이 악화되지 않는다.

### 나쁜 신호

- `eligible`는 있는데 `shown`이 거의 없다.
- `skipped`가 대부분이다.
- GA4 Admin 등록이 끝난 뒤에도 skip reason이 `AccessibilityOff`, `QuietHours`, `NotHomeRoot`, `NoActivity`에 과도하게 몰린다.
- GA4 Admin 등록이 끝난 뒤에도 `failed`가 특정 error로 반복된다.

### 대표적인 해석 실수

- `shown`을 리뷰 작성 완료로 해석하는 것
- Play Console 지표 없이 앱 이벤트만으로 리뷰 개선 성과를 결론내리는 것
- enum에 정의된 모든 `SkipReason`이 실제 프로덕션에서 방출된다고 가정하는 것

## 검증 명령

```bash
cd <repo-root>
git diff -- docs/REVIEW_PROMPT_LIFECYCLE.md docs/ANALYTICS_EVENT_DICTIONARY.md docs/METRICS_ANALYSIS.md docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md
./gradlew :app:testDevDebugUnitTest \
  --tests com.uiery.keep.feature.review.ReviewEligibilityEvaluatorTest \
  --tests com.uiery.keep.feature.review.InAppReviewManagerTest \
  --tests com.uiery.keep.feature.home.HomeViewModelReviewTest \
  --tests com.uiery.keep.analytics.FirebaseKeepAnalyticsTest
rg -n "review_prompt_|REVIEW_PENDING|LAST_REVIEW_PROMPT_AT_MS|SUCCESSFUL_SESSION_COUNT|LAST_BACKGROUNDED_AT_MS|accepted|dismissed" docs app/src/main app/src/test
```

## 문서 갱신 규칙

- 리뷰 eligibility 규칙이 바뀌면 이 문서와 `docs/ANALYTICS_EVENT_DICTIONARY.md`를 같이 업데이트한다.
- review queryability 상태가 바뀌면 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 trust/review ledger와 `docs/METRICS_ANALYSIS.md`의 조회 가이드도 같이 갱신한다.
- Play In-App Review 한계 때문에 `accepted` / `dismissed` 같은 용어를 새 문서에 다시 도입하지 않는다.
- 실제 리뷰 개선 판단은 항상 14일/30일 후행 창으로 다시 확인한다.
