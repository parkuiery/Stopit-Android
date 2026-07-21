# Onboarding Usage Promise Coach Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 신규 사용자의 최근 7일 앱 사용정보를 기기 안에서 분석해 수정 가능한 첫 약속을 제안하고, 기존 루틴과 실제 차단 경험까지 안전하게 연결한다.

**Architecture:** 기존 온보딩 앞에 `Entry` 라우트를 두어 sticky Control/Treatment 배정과 phase 복구를 결정한다. Treatment의 화면별 ViewModel은 `FirstPromiseDraftStore`를 공유하고, 사용 분석은 순수 정책으로 분리하며, 루틴·매핑·분석 outbox는 Room transaction으로 idempotent하게 저장한다. 기존 차단 UI는 유지하고 `BlockAnalyticsCoordinator`가 첫 약속 attribution과 canonical 이벤트 순서만 담당한다.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Navigation Compose typed routes, Orbit MVI, Hilt, Preferences DataStore, Room 2.7, Firebase Remote Config/Analytics, Amplitude backend, JUnit4, AndroidX Room MigrationTestHelper

**Design spec:** `docs/superpowers/specs/2026-07-15-onboarding-usage-promise-coach-design.md`

**Branch:** `codex/onboarding-usage-promise-coach-spec`

**Constraints:** 새 dependency를 추가하지 않는다. `feature/onboarding-usage-aha`는 코드 참고만 하고 merge/cherry-pick하지 않는다. `RoutineEntity`와 Block UI는 바꾸지 않는다. Home은 disabled 첫 약속을 다시 켜는 작은 복구 카드만 추가하고 기존 핵심 CTA 구조는 유지한다. package name, 앱 라벨, 정확한 관측 사용시간·시각·routine/draft id를 Analytics payload에 넣지 않는다.

---

## File structure and ownership

### New production files

- `app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseModels.kt` — 목적, phase, typed source, draft, 설명 가능한 proposal을 표현하는 순수 모델.
- `app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseStatePolicy.kt` — 정상 phase 전이와 emergency 전이를 표 기반으로 결정.
- `app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseRecommendationPolicy.kt` — 목적/사용 프로필에서 30분 약속 하나를 결정하는 순수 정책.
- `app/src/main/java/com/uiery/keep/domain/usageinsight/OnboardingUsageProfile.kt` — interval, aggregate, coverage와 profile result를 framework-free 값으로 표현하고 Task 1의 typed quality/pattern enums를 재사용.
- `app/src/main/java/com/uiery/keep/domain/usageinsight/OnboardingUsageProfilePolicy.kt` — coverage, 30분 bucket, 후보 앱, 동률 규칙을 결정.
- `app/src/main/java/com/uiery/keep/data/usageinsight/OnboardingUsageStatsReader.kt` — 날짜별 `queryUsageStats(INTERVAL_DAILY, ...)` 호출과 framework 결과 정규화.
- `app/src/main/java/com/uiery/keep/data/usageinsight/UsageEventIntervalPairer.kt` — UsageEvents를 clamp된 foreground interval로 변환하는 순수 경계.
- `app/src/main/java/com/uiery/keep/data/usageinsight/OnboardingUsageProfileRepository.kt` — 완료된 최근 7일 조회와 active routine 제외를 조율.
- `app/src/main/java/com/uiery/keep/datastore/FirstPromiseDraftStore.kt` — sticky variant, phase, draft, pending action, permission attempt를 JSON으로 원자 저장.
- `app/src/main/java/com/uiery/keep/datastore/FirstPromisePracticeStore.kt` — 10분 practice token을 복구.
- `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentConfig.kt` — Remote Config에서 읽는 immutable snapshot/interface.
- `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfig.kt` — Firebase adapter와 Control-safe defaults.
- `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentPolicy.kt` — 순수 배정/kill-switch 정책.
- `app/src/main/java/com/uiery/keep/feature/onboarding/entry/OnboardingEntryNavigation.kt` — 배정·복구 결과를 typed route로 전환.
- `app/src/main/java/com/uiery/keep/feature/onboarding/entry/OnboardingEntryViewModel.kt` — 첫 route 결정과 emergency transition.
- `app/src/main/java/com/uiery/keep/feature/onboarding/goal/GoalSelectNavigation.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/goal/GoalSelectScreen.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/goal/GoalSelectViewModel.kt` — 목적 선택과 manual 분기.
- `app/src/main/java/com/uiery/keep/feature/onboarding/usageaccess/UsageAccessNavigation.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/usageaccess/UsageAccessScreen.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/usageaccess/UsageAccessViewModel.kt` — attempt별 Usage Access 왕복.
- `app/src/main/java/com/uiery/keep/feature/onboarding/usageanalysis/UsageAnalysisNavigation.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/usageanalysis/UsageAnalysisScreen.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/usageanalysis/UsageAnalysisViewModel.kt` — 5초 timeout과 late-result 무효화.
- `app/src/main/java/com/uiery/keep/feature/onboarding/proposal/PromiseProposalNavigation.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/proposal/PromiseProposalScreen.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/proposal/PromiseProposalViewModel.kt` — 앱·시간·요일 편집과 draft 확정.
- `app/src/main/java/com/uiery/keep/feature/onboarding/result/PromiseResultNavigation.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/result/PromiseResultScreen.kt`
- `app/src/main/java/com/uiery/keep/feature/onboarding/result/PromiseResultViewModel.kt` — enabled/disabled 결과와 10분 practice.
- `app/src/main/java/com/uiery/keep/database/entity/FirstPromiseEntity.kt` — `draft_id → routine_id` attribution mapping.
- `app/src/main/java/com/uiery/keep/database/entity/FirstPromiseAnalyticsOutboxEntity.kt` — draft별 ordered at-least-once delivery row.
- `app/src/main/java/com/uiery/keep/database/dao/FirstPromiseDao.kt`
- `app/src/main/java/com/uiery/keep/database/dao/FirstPromiseAnalyticsOutboxDao.kt`
- `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseRepository.kt` — Room idempotency transaction.
- `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseCreationCoordinator.kt` — exact-alarm 결과, outbox ready, first-lock 순서를 조율.
- `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseAnalyticsDispatcher.kt` — sequence 직렬 drain과 30일 정리.
- `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseOutboxEvent.kt` — sequence 10/20/30/40의 typed payload와 privacy-validating codec.
- `app/src/main/java/com/uiery/keep/data/lock/TimedLockSessionController.kt` — Home과 첫 약속 연습이 공유하는 timed-lock 시작 경계.
- `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromisePracticeController.kt` — shared timed-lock controller를 고정 10분 practice로 제한.
- `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseModule.kt` — repository/config binding.
- `app/src/main/java/com/uiery/keep/analytics/BlockAnalyticsCoordinator.kt` — 첫 약속 sequence 30/40과 일반 block path를 통합.
- `app/src/main/java/com/uiery/keep/analytics/FirstCoreActionDeliveryCoordinator.kt` — 전역 first-core reservation과 DataStore reconciliation.
- `app/src/main/java/com/uiery/keep/feature/home/component/FirstPromiseResumeCard.kt` — disabled 첫 약속에만 보이는 exact-alarm 복구 카드.

### Existing files to modify

- `app/src/main/java/com/uiery/keep/KeepApp.kt`, `feature/onboarding/OnboardingNavigation.kt` — Entry와 Treatment graph wiring.
- 기존 permission/notification/select 화면과 ViewModel — Control 계약을 유지하면서 Treatment callback/phase를 지원.
- `app/src/main/java/com/uiery/keep/ui/component/CategoryBottomSheetContent.kt` — `Multiple`/`Single` selection mode.
- `app/src/main/java/com/uiery/keep/data/usageinsight/UsageStatsGateway.kt`, `AndroidUsageStatsGateway.kt` — interval/coverage 입력 조회.
- `app/src/main/java/com/uiery/keep/database/KeepDatabase.kt`, `app/src/main/java/com/uiery/keep/database/di/DatabaseModule.kt`, `app/schemas/com.uiery.keep.database.KeepDatabase/7.json` — Room 6→7.
- `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`, `FirebaseKeepAnalytics.kt`, Amplitude allowlist — 신규 privacy-safe schema.
- `app/src/main/java/com/uiery/keep/BlockViewModel.kt`, `KeepApplication.kt` — coordinator 위임과 startup drain.
- `app/src/main/java/com/uiery/keep/feature/home/HomeScreen.kt`, `HomeViewModel.kt` — timed-lock controller 재사용과 disabled promise 복구 카드.
- `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt` — timed-lock 종료 시 practice attribution token 정리.
- `app/src/main/java/com/uiery/keep/datastore/DataStore.kt`, `BackupRestoreDataStoreKeyPolicy.kt` — reset-only state keys.
- 모든 shipped `values*/strings.xml` — 신규 화면 문자열.
- `docs/ANALYTICS_EVENT_DICTIONARY.md`, `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`, `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`, `docs/USAGE_STATS_PERSONALIZATION_MVP.md` — code/schema/운영 계약 동기화.

## Chunk 1: Experiment, durable state, and deterministic recommendation

### Task 1: Lock typed domain, sticky experiment, and analytics boundaries

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseModels.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentConfig.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfig.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentPolicy.kt`
- Modify: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- Modify: `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`
- Test: `app/src/test/java/com/uiery/keep/domain/firstpromise/FirstPromiseModelsTest.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentPolicyTest.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfigTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`

- [ ] **Step 1: Write failing type and assignment tests**

Assert exact analytics values for every goal, phase-independent `FirstPromiseSource.Personalized|GoalTemplate|Manual`, `FirstPromiseOrigin`, `UsagePatternType`, `UsageDataQuality`, coverage/latency bucket, edit field, assignment version, typed first-promise schedule state, and practice outcome enum. These analytics-facing enums are created in `FirstPromiseModels.kt` now and reused by the usage/outbox domains. Assert Remote Config read failure chooses Control and rollout boundaries are deterministic. Do not put transition logic on the phase enum; Task 2 owns the complete state table.

- [ ] **Step 2: Run model/policy tests and verify missing symbols**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirstPromiseModelsTest' --tests '*OnboardingExperimentPolicyTest'`

Expected: FAIL because the typed models and assignment policy do not exist.

- [ ] **Step 3: Implement typed models and assignment policy**

```kotlin
@Serializable enum class FirstPromiseSource(val analyticsValue: String) {
    Personalized("personalized"), GoalTemplate("goal_template"), Manual("manual")
}
@Serializable enum class FirstPromiseOrigin(val analyticsValue: String) {
    FirstPromiseRoutine("first_promise_routine"), FirstPromisePractice("first_promise_practice")
}

@Serializable enum class UsageDataQuality(val analyticsValue: String) {
    Full("full"), UsageOnly("usage_only"), Insufficient("insufficient")
}
@Serializable enum class UsagePatternType(val analyticsValue: String) {
    Night("night"), PeakWindow("peak_window"), TopApp("top_app"), Manual("manual")
}
@Serializable enum class UsageCoverageBucket(val analyticsValue: String) { Zero("0"), OneTwo("1_2"), ThreeSix("3_6"), Seven("7") }
@Serializable enum class AnalysisLatencyBucket(val analyticsValue: String) { UnderOneSecond("under_1s"), OneToThreeSeconds("1_3s"), ThreeToFiveSeconds("3_5s"), Timeout("timeout") }
@Serializable enum class PromiseEditField(val analyticsValue: String) { App("app"), StartTime("start_time"), RepeatDays("repeat_days") }
@Serializable enum class FirstPromisePracticeOutcome(val analyticsValue: String) { Started("started"), Skipped("skipped"), StartFailed("start_failed") }
@Serializable enum class OnboardingAssignmentVersion(val analyticsValue: String) { V1("v1") }
@Serializable enum class FirstPromiseScheduleState(val analyticsValue: String) {
    Enabled("enabled"),
    DisabledExactAlarmMissing("disabled_exact_alarm_missing"),
    DisabledUserChoice("disabled_user_choice"),
    DisabledUnknown("disabled_unknown"),
}

@Serializable data class FirstPromiseDraft(
    val draftId: String,
    val goal: FirstPromiseGoal,
    val packageName: String,
    val appLabel: String,
    val startMinutes: Int,
    val repeatDays: Set<Int>,
    val source: FirstPromiseSource,
)

sealed interface RecommendationReason {
    val patternType: UsagePatternType
    data class ObservedPeak(
        override val patternType: UsagePatternType,
        val usageCoverageDays: Int,
        val eventCoverageDays: Int,
        val startMinutes: Int,
    ) : RecommendationReason
    data class GoalDefault(
        override val patternType: UsagePatternType = UsagePatternType.TopApp,
        val goal: FirstPromiseGoal,
        val startMinutes: Int,
        val usageCoverageDays: Int,
    ) : RecommendationReason
    data object Manual : RecommendationReason { override val patternType = UsagePatternType.Manual }
}

data class FirstPromiseProposal(
    val draft: FirstPromiseDraft,
    val reason: RecommendationReason,
)
```

`OnboardingExperimentSnapshot` has exact fields `treatmentPercent: Int = 0`, `newAssignmentEnabled: Boolean = false`, `emergencyDisabled: Boolean = false`, and `remoteReadable: Boolean = false`. `OnboardingExperimentPolicy.assign(snapshot, bucket)` clamps percent to `0..100` and requires both readable and new-assignment enabled.

- [ ] **Step 4: Write failing Remote Config adapter tests before implementation**

Assert the exact keys `onboarding_promise_coach_percent`, `onboarding_promise_coach_new_assignment_enabled`, and `onboarding_promise_coach_emergency_disabled`; defaults `0/false/false`; successful getter mapping; and any getter exception returning the complete unreadable Control-safe snapshot.

- [ ] **Step 5: Run the adapter test and verify RED, then implement it**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirebaseOnboardingExperimentConfigTest'`

Expected before implementation: FAIL. Inject the existing singleton `FirebaseRemoteConfig` from `feature/review/ReviewModule.kt`; do not add another provider or dependency. After implementation the same command must PASS.

- [ ] **Step 6: Write failing typed Analytics backend tests**

Add these new `KeepAnalytics` methods with default no-op bodies so existing fakes do not break, then override them in `FirebaseKeepAnalytics`:

```kotlin
fun trackOnboardingExperimentExposed(variant: OnboardingVariant, assignmentVersion: OnboardingAssignmentVersion) = Unit
fun trackUsageAnalysisCompleted(dataQuality: UsageDataQuality, patternType: UsagePatternType, coverageDaysBucket: UsageCoverageBucket, latencyBucket: AnalysisLatencyBucket) = Unit
fun trackPromiseRecommendationShown(goalType: FirstPromiseGoal, patternType: UsagePatternType, source: FirstPromiseSource) = Unit
fun trackPromiseRecommendationEdited(fieldName: PromiseEditField) = Unit
fun trackFirstPromiseCreated(goalType: FirstPromiseGoal, source: FirstPromiseSource, scheduleState: FirstPromiseScheduleState) = Unit
fun trackFirstPromisePracticeOutcome(outcome: FirstPromisePracticeOutcome) = Unit
```

Tests assert exact event names, key sets, and enum-to-wire values from the design spec; schedule-state tests also assert equality with the existing `RoutineSavedScheduleState` constants. `FirebaseKeepAnalytics` alone converts typed values to `.analyticsValue`. Package, label, exact observed time/minutes, routine id, draft id, or arbitrary strings cannot be supplied through these entry points. Add a compile-time API-shape/reflection assertion that no new method parameter is raw `String`.

- [ ] **Step 7: Run Analytics test RED, implement constants/overrides, then run all focused tests**

Run before implementation: `./gradlew :app:testDevDebugUnitTest --tests '*FirebaseKeepAnalyticsTest'`

Expected: the new assertions FAIL. Implement only the tested constants and overrides, then run:

`./gradlew :app:testDevDebugUnitTest --tests '*FirstPromiseModelsTest' --tests '*OnboardingExperimentPolicyTest' --tests '*FirebaseOnboardingExperimentConfigTest' --tests '*FirebaseKeepAnalyticsTest'`

Expected: PASS. Tasks 7–13 use these typed methods and must not add raw `logEvent` strings.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/uiery/keep/domain/firstpromise app/src/main/java/com/uiery/keep/feature/onboarding/experiment app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt app/src/test/java/com/uiery/keep/domain/firstpromise app/src/test/java/com/uiery/keep/feature/onboarding/experiment app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt
git commit -m "Keep onboarding assignment stable across restarts" -m "Define typed treatment, rollout, and measurement boundaries before UI work begins.\n\nConstraint: Remote Config read failures must choose Control\nConfidence: high\nScope-risk: narrow\nTested: Model, config adapter, policy, and Firebase backend JVM tests"
```

### Task 2: Persist state with exhaustive normal and emergency transitions

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseStatePolicy.kt`
- Modify: `app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseModels.kt`
- Create: `app/src/main/java/com/uiery/keep/datastore/FirstPromiseDraftStore.kt`
- Create: `app/src/main/java/com/uiery/keep/datastore/FirstPromisePracticeStore.kt`
- Modify: `app/src/main/java/com/uiery/keep/datastore/DataStore.kt`
- Modify: `app/src/main/java/com/uiery/keep/datastore/BackupRestoreDataStoreKeyPolicy.kt`
- Test: `app/src/test/java/com/uiery/keep/domain/firstpromise/FirstPromiseStatePolicyTest.kt`
- Test: `app/src/test/java/com/uiery/keep/datastore/FirstPromiseDraftStoreTest.kt`
- Test: `app/src/test/java/com/uiery/keep/datastore/FirstPromisePracticeStoreTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/datastore/BackupRestoreDataStoreKeyPolicyTest.kt`

- [ ] **Step 1: Write a table-driven failing test for every normal transition**

Lock this entire allowlist and assert every other phase pair is rejected: `GoalPending→UsageAccessPending|ManualSelectPending`; `UsageAccessPending→Analyzing|ManualSelectPending`; `Analyzing→DraftReady|ManualSelectPending`; `ManualSelectPending→ManualSelectPending|DraftReady`; `DraftReady→DraftReady|AccessibilityPending`; `AccessibilityPending→AccessibilityPending|DraftReady|NotificationPending`; `NotificationPending→NotificationPending|Persisting`; `Persisting→Persisting|PersistFailed|SchedulePermissionRequired|ResultEnabled`; `PersistFailed→PersistFailed|DraftReady|Persisting`; `SchedulePermissionRequired→SchedulePermissionRequired|ResultEnabled|ResultDisabled`; `ResultEnabled→ResultEnabled|CompletedEnabled`; `ResultDisabled→ResultDisabled|CompletedDisabled`; completed phases self-transition only. Self-transitions are no-op writes, not duplicate Analytics.

- [ ] **Step 2: Write a table-driven failing test for the complete emergency matrix**

Assert: Goal/UsageAccess/Analyzing invalidate `analysisAttemptId`, clear usage proposal/pending action, preserve goal, and enter ManualSelect; ManualSelect stays unchanged; DraftReady/Accessibility/Notification clear proposal/pending action, preserve goal, and enter ManualSelect; Persisting returns `WaitForPersistence` and forbids navigation, then mapping success resolves to `ResultEnabled` or `SchedulePermissionRequired` while failure clears proposal and resolves ManualSelect; PersistFailed clears proposal and enters ManualSelect; SchedulePermissionRequired/Result/Completed preserve routine mapping and phase and return `DisableFutureAnalysis`. Late invalidated analysis writes and Analytics are rejected by attempt id. No emergency action may delete a mapping or create another routine.

- [ ] **Step 3: Write failing permission round-trip and persistence tests**

Use these typed states, not a `Map<Long, String>`:

```kotlin
@Serializable enum class UsagePermissionLaunchState { NotLaunched, Opened, LaunchFailed, UnresolvedAfterRecreation }
@Serializable enum class UsagePermissionOutcome { Granted, Denied, Skipped, Unknown }
@Serializable data class UsagePermissionAttempt(
    val id: Long,
    val launchState: UsagePermissionLaunchState,
    val terminalOutcome: UsagePermissionOutcome? = null,
)
```

Cover: first assignment wins; phase/draft/non-sensitive reason reference/pending action survive recreation; an opened attempt recreated before same-process resume becomes `UnresolvedAfterRecreation` and emits no terminal outcome; launch exception becomes `LaunchFailed+Unknown`; same-process false resume becomes `Denied`; a user choosing manual setup closes its newly created attempt with `Skipped`; each attempt accepts one terminal result; a later attempt may become Granted; stale callback ids are ignored; completion clears draft/reason reference but retains assignment/exposure/routine id.

- [ ] **Step 4: Write failing practice expiry and backup-policy tests**

Assert `FirstPromisePracticeToken(draftId, startedAtMillis, expiresAtMillis)` round-trips with its local draft id, survives store recreation until the exclusive deadline, can be explicitly cleared when timed lock ends, and is atomically removed at expiry. Serialize it in the test and assert JSON contains neither package name nor routine id fields. Assert both new JSON keys are reset-only and the reflection completeness test detects any unclassified key.

- [ ] **Step 5: Run focused tests and verify RED**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirstPromiseStatePolicyTest' --tests '*FirstPromise*StoreTest' --tests '*BackupRestoreDataStoreKeyPolicyTest'`

Expected: FAIL because policy, keys, and stores are absent.

- [ ] **Step 6: Implement one atomic onboarding JSON state and one practice token**

`FirstPromiseOnboardingState` stores assignment/version/exposure, phase/path/goal, `draft: FirstPromiseDraft?`, a serializable non-sensitive `RecommendationReasonRef` containing only pattern type, coverage counts, goal-default flag, and the already-selected start time, routine id plus final typed schedule state, pending action, current typed permission attempt, `analysisAttemptId`, and typed milestone flags. Completion clears temporary draft/reason/pending action but retains routine id and schedule state for disabled-promise Home recovery. The transient `RecommendationReason`/`FirstPromiseProposal` from Task 1 are deliberately not serializable. State must not serialize `averageDailyMinutes`, raw intervals, last-used epochs, or other package-specific usage amounts. Proposal screens rehydrate the exact average from the local usage source and fall back to the categorical reason when unavailable. Use one `dataStore.edit` for each accepted policy transition. `FirstPromiseStatePolicy` is the only normal/emergency mutation authority. `FIRST_PROMISE_ONBOARDING_STATE` and `FIRST_PROMISE_PRACTICE_TOKEN` are `stringPreferencesKey` values and reset-only on restore.

- [ ] **Step 7: Run focused tests and commit**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirstPromiseStatePolicyTest' --tests '*FirstPromise*StoreTest' --tests '*BackupRestoreDataStoreKeyPolicyTest'`

Expected: PASS.

```bash
git add app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseModels.kt app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseStatePolicy.kt app/src/main/java/com/uiery/keep/datastore app/src/test/java/com/uiery/keep/domain/firstpromise/FirstPromiseStatePolicyTest.kt app/src/test/java/com/uiery/keep/datastore
git commit -m "Recover the first promise without changing experiment identity" -m "Persist typed attempts and enforce complete normal and emergency transition tables.\n\nConstraint: DataStore is reset-only during restore\nConfidence: high\nScope-risk: moderate\nTested: Exhaustive transition, attempt, expiry, round-trip, and backup classification JVM tests"
```

### Task 3: Add per-calendar-day UsageStats aggregates without changing Home insight

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/data/usageinsight/OnboardingUsageStatsReader.kt`
- Modify: `app/src/main/java/com/uiery/keep/data/usageinsight/UsageStatsGateway.kt`
- Modify: `app/src/main/java/com/uiery/keep/data/usageinsight/AndroidUsageStatsGateway.kt`
- Create: `app/src/test/java/com/uiery/keep/data/usageinsight/OnboardingUsageStatsReaderTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/data/usageinsight/UsageInsightRepositoryTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/home/HomeUsageInsightTestSupport.kt`

- [ ] **Step 1: Write failing daily-window, filtering, and clamping tests**

Inject a fake `DailyUsageStatsSource` that records calls. Assert exactly seven calls, one for each `today.minusDays(7)..today.minusDays(1)`, with local day start and next-day start in the supplied `ZoneId`; today is never queried; DST days use calendar boundaries rather than fixed 24-hour math. Assert results clamp framework timestamps to their requested day, discard non-positive foreground time, own/settings/non-launchable/excluded packages, and retain package, total foreground, last-used epoch, and local date only.

- [ ] **Step 2: Run the reader test and verify RED**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*OnboardingUsageStatsReaderTest'`

Expected: FAIL because the reader/source contract does not exist.

- [ ] **Step 3: Implement a separate onboarding aggregate method**

Add `queryOnboardingDailyAggregates(days: ClosedRange<LocalDate>, zoneId: ZoneId): List<AppUsageAggregateDay>` to `UsageStatsGateway`. `AndroidUsageStatsGateway` delegates to the reader, whose Android source calls `UsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, nextDayStart)` once per date. Keep existing `queryDailyUsage` unchanged for Home; onboarding code must never derive weekly totals from its UsageEvents reconstruction.

- [ ] **Step 4: Update every gateway fake and run Home regressions**

Add the new method to fakes in `UsageInsightRepositoryTest.kt` and `HomeUsageInsightTestSupport.kt`. Prefer a default empty implementation on the interface only if that matches the existing gateway style; either way, explicitly compile and run both suites.

Run: `./gradlew :app:testDevDebugUnitTest --tests '*OnboardingUsageStatsReaderTest' --tests '*UsageInsightRepositoryTest' --tests '*HomeViewModel*UsageInsight*'`

Expected: PASS, with exact seven-call assertions and existing Home insight behavior unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uiery/keep/data/usageinsight app/src/test/java/com/uiery/keep/data/usageinsight app/src/test/java/com/uiery/keep/feature/home/HomeUsageInsightTestSupport.kt
git commit -m "Measure onboarding usage from complete local days" -m "Add a daily UsageStats aggregate path while preserving the event-based Home insight contract.\n\nConstraint: Each completed calendar day is queried separately\nConfidence: high\nScope-risk: moderate\nTested: Seven-day, DST, clamp, filter, gateway fake, and Home insight JVM tests"
```

### Task 4: Add clamp-safe foreground intervals behind the gateway

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/data/usageinsight/UsageEventIntervalPairer.kt`
- Modify: `app/src/main/java/com/uiery/keep/data/usageinsight/UsageStatsGateway.kt`
- Modify: `app/src/main/java/com/uiery/keep/data/usageinsight/AndroidUsageStatsGateway.kt`
- Create: `app/src/test/java/com/uiery/keep/data/usageinsight/UsageEventIntervalPairerTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/data/usageinsight/UsageInsightRepositoryTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/home/HomeUsageInsightTestSupport.kt`

- [ ] **Step 1: Write failing interval pairing tests**

From framework-free resume/pause samples, assert normal pairing; duplicate resume keeps the earliest unmatched resume, matching current `putIfAbsent`; the first pause/stop without an in-day resume creates one interval from day start for a session crossing midnight; later orphan close events are ignored; open interval closes at day end; request-boundary clamp; zero/negative interval removal; midnight split; DST calendar boundary; and own/settings/non-launchable/excluded package filtering. The output is `AppUsageInterval(packageName, startMillis, endMillis, localDate, countsAsLaunch)`: only an interval opened by an in-day resume has `countsAsLaunch=true`; an orphan carried from the prior day is false. It never exposes `UsageEvents`.

- [ ] **Step 2: Run the pairer test and verify RED**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*UsageEventIntervalPairerTest'`

Expected: FAIL because the pairer and interval gateway method are absent.

- [ ] **Step 3: Extract current event reconstruction and add the onboarding interval query**

Move the existing earliest-resume/orphan-close rules from `AndroidUsageStatsGateway` into `UsageEventIntervalPairer`; do not invent a second pairing semantics. Add `queryOnboardingUsageIntervals(days, zoneId)` to `UsageStatsGateway`; Android performs one bounded event query per requested calendar day and applies the same launchable/exclusion policy as Task 3. Have existing `queryDailyUsage` consume durations and `countsAsLaunch` while preserving total usage, launch count, and night usage through regression fixtures.

- [ ] **Step 4: Update gateway fakes, run RED→GREEN plus Home regressions**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*UsageEventIntervalPairerTest' --tests '*UsageInsightRepositoryTest' --tests '*HomeViewModel*UsageInsight*'`

Expected: PASS, including filtering/clamping and existing Home usage insight tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uiery/keep/data/usageinsight app/src/test/java/com/uiery/keep/data/usageinsight app/src/test/java/com/uiery/keep/feature/home/HomeUsageInsightTestSupport.kt
git commit -m "Interpret foreground events within local-day boundaries" -m "Extract deterministic interval pairing for onboarding while keeping Home summaries stable.\n\nConstraint: Raw UsageEvents never escape the gateway\nConfidence: high\nScope-risk: moderate\nTested: Pairing, clamp, midnight, DST, filtering, and Home regression JVM tests"
```

### Task 5: Build the deterministic usage profile and exact routine-overlap policy

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/domain/usageinsight/OnboardingUsageProfile.kt`
- Create: `app/src/main/java/com/uiery/keep/domain/usageinsight/OnboardingUsageProfilePolicy.kt`
- Create: `app/src/main/java/com/uiery/keep/data/usageinsight/OnboardingUsageProfileRepository.kt`
- Test: `app/src/test/java/com/uiery/keep/domain/usageinsight/OnboardingUsageProfilePolicyTest.kt`
- Test: `app/src/test/java/com/uiery/keep/data/usageinsight/OnboardingUsageProfileRepositoryTest.kt`

- [ ] **Step 1: Write failing framework-free policy tests**

Cover separate `usageCoverageDays` and `eventCoverageDays`; 0/1–2/3–6/7 buckets; coverage divisor rather than `/7`; minimum 15-minute average; candidate sort by total usage, distinct usage days, last-use epoch, then package name; interval distribution across 30-minute buckets; midnight/DST; peak ties by total milliseconds, distinct days, last epoch, then earlier bucket; Night at 22:00–05:30; and exact `Full|UsageOnly|Insufficient` rules.

Define outcomes so insufficient data is not represented as a fake profile:

```kotlin
sealed interface OnboardingUsageProfileResult {
    data class Ready(val profile: OnboardingUsageProfile) : OnboardingUsageProfileResult
    data class Insufficient(
        val usageCoverageDays: Int,
        val eventCoverageDays: Int,
        val reason: InsufficientReason,
    ) : OnboardingUsageProfileResult
}

data class OnboardingUsageProfile(
    val packageName: String,
    val appLabel: String,
    val averageDailyMinutes: Long,
    val suggestedStartMinutes: Int,
    val usageCoverageDays: Int,
    val eventCoverageDays: Int,
    val dataQuality: UsageDataQuality,
    val patternType: UsagePatternType,
)
```

- [ ] **Step 2: Write failing exact active-routine overlap tests**

Use `EnabledRoutineCoverage(packageNames, startMinutes, endMinutes, repeatDays)`. Do not exclude a package merely because any routine contains it. Exclude a candidate only when an enabled routine containing it fully covers that candidate's proposed 30-minute window on every proposed repeat day. Test partial time overlap, only some weekdays, disabled routine, unrelated package, midnight-spanning routine, and full all-day-set coverage. Pass the selected goal's default start to the policy so a UsageOnly candidate can be checked before selection; proposed repeat days are all seven.

- [ ] **Step 3: Run policy tests and verify RED, then implement the pure policy**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*OnboardingUsageProfilePolicyTest'`

Expected before implementation: FAIL. Implement the rules exactly. `Full` requires usage coverage ≥3, event coverage ≥3, a valid app, and a peak bucket. `UsageOnly` requires usage coverage ≥3 and a valid app but lacks event coverage/peak, so it uses the goal default. Query failure, usage coverage <3, or no candidate returns typed `Insufficient`.

- [ ] **Step 4: Run the complete policy test and require GREEN**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*OnboardingUsageProfilePolicyTest'`

Expected: PASS before repository work begins.

- [ ] **Step 5: Write failing repository orchestration tests before implementation**

Fake the two Task 3/4 gateway outputs and `RoutineRepository`. Assert the repository requests exactly the prior seven local dates, passes enabled routine time/day/package coverage rather than a package set, divides by `usageCoverageDays`, maps framework exceptions to `Insufficient(QueryFailed)`, and keeps aggregate/event coverage independent. If `appLabel()` is null/blank, exclude that candidate and continue deterministically to the next ranked candidate; return Insufficient only when no labeled candidate remains, and never display a package name as fallback.

- [ ] **Step 6: Run repository RED, implement, then run all usage tests**

Run before implementation: `./gradlew :app:testDevDebugUnitTest --tests '*OnboardingUsageProfileRepositoryTest'`

Expected: FAIL. Implement only the orchestration above, then run:

`./gradlew :app:testDevDebugUnitTest --tests '*OnboardingUsageProfile*' --tests '*UsageEventIntervalPairerTest' --tests '*OnboardingUsageStatsReaderTest' --tests '*UsageInsightRepositoryTest' --tests '*HomeViewModel*UsageInsight*'`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/uiery/keep/data/usageinsight app/src/main/java/com/uiery/keep/domain/usageinsight app/src/test/java/com/uiery/keep/data/usageinsight app/src/test/java/com/uiery/keep/domain/usageinsight
git commit -m "Choose a promise only from sufficient local evidence" -m "Separate usage and event coverage, deterministic ranking, and exact active-routine overlap.\n\nConstraint: Raw usage observations remain on-device\nRejected: Exclude every app found in any routine | partial schedules do not cover the proposed promise\nConfidence: high\nScope-risk: moderate\nTested: Policy, repository, coverage, tie, overlap, midnight, DST, and Home regressions"
```

### Task 6: Turn the same evidence into one editable 30-minute proposal

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/domain/firstpromise/FirstPromiseRecommendationPolicy.kt`
- Test: `app/src/test/java/com/uiery/keep/domain/firstpromise/FirstPromiseRecommendationPolicyTest.kt`

- [ ] **Step 1: Write failing default, precedence, source, and explanation tests**

Lock goal defaults exactly: sleep 23:00, focus 21:00, study 19:00, free time 20:00, unspecified 21:00. `Full` peak/night wins over the goal default and yields `Personalized+ObservedPeak`; `UsageOnly` personalizes the app, uses the goal time, and yields `Personalized+GoalDefault` with `TopApp`; insufficient data plus a selected goal/app yields `GoalTemplate+GoalDefault` with `Manual` pattern so no observation is implied; no goal plus direct app/time yields `Manual+Manual`. Duration is always 30 minutes and repeat days all seven until edited.

Assert `FirstPromiseProposal.reason` and `proposal.draft` use the same package-independent evidence: the displayed reason's start time equals draft start, coverage text derives from the same coverage fields, and editing the draft does not mutate the original observation. Exact `averageDailyMinutes` is a transient display fact supplied alongside the initial proposal and is not part of the serializable proposal/reason contract; UI never independently invents a reason.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirstPromiseRecommendationPolicyTest'`

Expected: FAIL because policy is absent.

- [ ] **Step 3: Implement the pure proposal policy**

Use an injected `draftId` in tests and `UUID.randomUUID().toString()` only at the production caller. Validate start in `0..1439`; reject an empty package or repeat-day set. Keep label/package in the local draft only. The policy returns `FirstPromiseProposal`, not a bare draft, so Proposal copy and later `RoutineModel` prefill share one immutable decision.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirstPromiseRecommendationPolicyTest'`

Expected: PASS.

```bash
git add app/src/main/java/com/uiery/keep/domain/firstpromise app/src/test/java/com/uiery/keep/domain/firstpromise
git commit -m "Explain the same promise that will be scheduled" -m "Translate profile evidence or manual input into one typed, editable proposal.\n\nConstraint: Phase 1 duration stays at thirty minutes\nConfidence: high\nScope-risk: narrow\nTested: Goal defaults, source, explanation, duration, coverage copy, and precedence JVM tests"
```

## Chunk 2: Treatment screens, permission flow, and routine creation

### Task 7: Add Entry routing, sticky exposure, and emergency recovery

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/entry/OnboardingEntryNavigation.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/entry/OnboardingEntryViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/OnboardingNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/KeepApp.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/intro/IntroViewModel.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/OnboardingEntryViewModelTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/KeepAppNavigationPolicyTest.kt`

- [ ] **Step 1: Write failing route tests**

Cover Control→Intro, new Treatment→GoalSelect, all persisted phases→their canonical route, already exposed assignment not changing after Remote Config changes, general kill switch affecting only unassigned users, and every emergency transition in the spec table.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*OnboardingEntryViewModelTest' --tests '*KeepAppNavigationPolicyTest'`

Expected: FAIL with missing Entry route.

- [ ] **Step 3: Make `Onboarding.Route.Entry` the graph start destination**

Entry is a zero-content `LaunchedEffect` route. It reads or creates assignment, applies emergency policy, then emits exactly one typed navigation side effect with `popUpTo(Entry) { inclusive = true }`. Keep `Onboarding.Route.Intro` and current Control callbacks unchanged.

- [ ] **Step 4: Track exposure at the first visible screen, not Entry**

Expose `FirstPromiseDraftStore.markExposedIfNeeded()`; Intro calls it with Control and GoalSelect calls it with Treatment. Only the boolean transition emits `onboarding_experiment_exposed`.

- [ ] **Step 5: Run tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*OnboardingEntryViewModelTest' --tests '*KeepAppNavigationPolicyTest' --tests '*OnboardingAnalyticsViewModelTest'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/uiery/keep/KeepApp.kt app/src/main/java/com/uiery/keep/feature/onboarding app/src/test/java/com/uiery/keep/KeepAppNavigationPolicyTest.kt app/src/test/java/com/uiery/keep/feature/onboarding
git commit -m "Protect the existing onboarding while routing the promise experiment" -m "Resolve sticky assignment and phase recovery before any visible route is shown.\n\nConstraint: Control retains the current Intro flow\nConfidence: high\nScope-risk: moderate\nTested: Entry, recovery, kill-switch, exposure, and navigation JVM tests"
```

### Task 8: Build GoalSelect and attempt-safe Usage Access/analysis

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/goal/GoalSelectNavigation.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/goal/GoalSelectScreen.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/goal/GoalSelectViewModel.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/usageaccess/UsageAccessNavigation.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/usageaccess/UsageAccessScreen.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/usageaccess/UsageAccessViewModel.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/usageanalysis/UsageAnalysisNavigation.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/usageanalysis/UsageAnalysisScreen.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/usageanalysis/UsageAnalysisViewModel.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/goal/GoalSelectViewModelTest.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/usageaccess/UsageAccessViewModelTest.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/usageanalysis/UsageAnalysisViewModelTest.kt`
- Modify all shipped `app/src/main/res/values*/strings.xml`.

- [ ] **Step 1: Write failing ViewModel tests**

Cover disabled CTA until goal selection; direct manual path with `Unspecified`; `settings_opened` only after launch success; attempt-local unknown on `ActivityNotFoundException`/`SecurityException`; same-process false resume→denied; later retry→granted; process-recreated unresolved attempt emits no guessed outcome; analysis success/insufficient/exception/5-second timeout; late completion ignored by attempt id. Extend existing permission Analytics enums with exact `permission_name=usage_access` and outcomes `skipped|unknown`, and assert attempt ids never enter payloads. Lock step view/complete exactly once for `goal_select` and `usage_access`; timeout emits `usage_analysis_completed(insufficient, manual, 0, timeout)`, while successful paths use the returned typed quality/pattern/coverage and measured latency bucket.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*GoalSelectViewModelTest' --tests '*UsageAccessViewModelTest' --tests '*UsageAnalysisViewModelTest'`

Expected: FAIL.

- [ ] **Step 3: Implement screens with the approved copy and no fake progress**

GoalSelect renders four single-select cards, primary `내 패턴 확인하기`, secondary `직접 설정할게요`. UsageAccess renders the allowed/not-seen/local-processing copy and uses a launcher function returning `Opened|Unavailable`; only `Opened` records `settings_opened`. UsageAnalysis displays an indeterminate indicator and runs `withTimeout(5.seconds)` on `Dispatchers.IO`.

- [ ] **Step 4: Implement exact analysis result handling**

`Full`/`UsageOnly` calls `FirstPromiseRecommendationPolicy`, stores only its draft plus non-sensitive reason reference, keeps the exact average transient, then routes Proposal. `Insufficient`, timeout, or exception stores `ManualSelectPending` without fabricating a proposal and routes single app selection. Increment `analysisAttemptId` before each run and compare it again before state, Analytics, or navigation writes.

- [ ] **Step 5: Add localized keys to every shipped locale file**

Add the same key set to default, ko, de, es, fr, it, ja, nl, pt, pt-rBR, ru, zh. Korean/default copy must match the spec; every other shipped locale receives reviewed native-language copy in the same commit. Do not leave missing keys or English fallback text in non-English resources.

- [ ] **Step 6: Run tests and resource compilation**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*GoalSelectViewModelTest' --tests '*UsageAccessViewModelTest' --tests '*UsageAnalysisViewModelTest' :app:assembleDevDebug`

Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/uiery/keep/feature/onboarding/goal app/src/main/java/com/uiery/keep/feature/onboarding/usageaccess app/src/main/java/com/uiery/keep/feature/onboarding/usageanalysis app/src/test/java/com/uiery/keep/feature/onboarding app/src/main/res/values*/strings.xml
git commit -m "Explain local usage analysis before asking for access" -m "Add the goal, permission round-trip, timeout, and manual fallback screens with attempt-safe recovery.\n\nConstraint: Usage Access is optional and content is never inspected\nConfidence: high\nScope-risk: moderate\nTested: ViewModel permission/timeout tests and dev debug resource build"
```

### Task 9: Reuse app selection in single-choice mode without changing Control

**Use:** `@superpowers:test-driven-development`

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/ui/component/CategoryBottomSheetContent.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/select/SelectAppNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/select/SelectAppScreen.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/select/SelectAppViewModel.kt`
- Test: `app/src/test/java/com/uiery/keep/ui/component/CategoryBottomSheetSelectionPolicyTest.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/select/SelectAppViewModelTest.kt`

- [ ] **Step 1: Add failing single-selection tests**

`Multiple` retains select-all and toggling. `Single` hides select-all, replacing selection with the last clicked package. Control completion still stores all selected packages, marks `IS_NEW=false`, and tracks existing completion. Treatment manual selection passes exactly one package through `FirstPromiseRecommendationPolicy`, stores its draft plus safe reason reference, and routes Proposal without completing onboarding or claiming first lock. Its first successful confirmation atomically marks the shared app-selection milestone and emits existing `onboarding_step_complete(select_app)` plus `app_selection_completed` once; retries/edits do not repeat them.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*CategoryBottomSheetSelectionPolicyTest' --tests '*SelectAppViewModelTest'`

Expected: FAIL.

- [ ] **Step 3: Add `AppSelectionMode.Multiple|Single`**

Default every existing call to `Multiple`. Add a distinct `Onboarding.Route.ManualAppSelect`; it renders the same screen with `Single` and routes to Proposal. Do not change `Onboarding.Route.SelectedApp`, its analytics timing, or Home/Routine callers.

- [ ] **Step 4: Run component boundary and tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*CategoryBottomSheetSelectionPolicyTest' --tests '*SelectAppViewModelTest' && python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uiery/keep/ui/component/CategoryBottomSheetContent.kt app/src/main/java/com/uiery/keep/feature/onboarding/select app/src/test/java/com/uiery/keep/ui/component/CategoryBottomSheetSelectionPolicyTest.kt app/src/test/java/com/uiery/keep/feature/onboarding/select
git commit -m "Let the first promise choose one app without changing existing pickers" -m "Add an opt-in single-selection mode and a separate manual Treatment route.\n\nConstraint: Existing picker callers remain multiple-select\nConfidence: high\nScope-risk: narrow\nTested: Selection policy, onboarding ViewModel, and shared UI boundary tests"
```

### Task 10: Build PromiseProposal and connect contextual accessibility/notification steps

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/proposal/PromiseProposalNavigation.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/proposal/PromiseProposalScreen.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/proposal/PromiseProposalViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/permission/PermissionSettingNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/permission/PermissionSettingScreen.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/permission/PermissionSettingViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/notification/NotificationSettingNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/notification/NotificationSettingScreen.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/notification/NotificationSettingViewModel.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/proposal/PromiseProposalViewModelTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/onboarding/OnboardingAnalyticsViewModelTest.kt`
- Modify: every shipped `app/src/main/res/values*/strings.xml`

- [ ] **Step 1: Write failing proposal/edit tests**

Test one fact/one action rendering state, same evidence feeding explanation and draft, exact `app|start_time|repeat_days` edit events, no duration editing, `promise_recommendation_shown` and `onboarding_step_view(promise_proposal)` once per milestone, personalized-path app-selection milestone emitted only on the first `첫 약속 시작하기`, `onboarding_step_complete(promise_proposal)` once, accessibility already-granted skip, notification denial continuation, and draft retention on back/process recreation. The manual path's app-selection milestone was already consumed in Task 9 and must not repeat here.

- [ ] **Step 2: Run focused tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*PromiseProposalViewModelTest' --tests '*OnboardingAnalyticsViewModelTest'`

Expected: FAIL.

- [ ] **Step 3: Implement Proposal state and UI**

State contains only display-safe local values: app label, transient average-minutes copy rehydrated from `OnboardingUsageProfileRepository`, pattern copy, start time, seven day toggles, and picker visibility. If rehydration is unavailable after recreation, show the stored categorical reason/coverage copy rather than a fabricated number. `첫 약속 시작하기` writes `AccessibilityPending`; edits remain `DraftReady`. Never persist the formatted sentence or exact average—only draft plus the non-sensitive reason reference.

- [ ] **Step 4: Parameterize existing permission screens by flow context**

Add `OnboardingPermissionContext.Control|FirstPromise`. Control copy/navigation remains byte-for-byte equivalent. FirstPromise copy names the selected start time, skips accessibility if already enabled, writes phase, then routes notification. Notification remains optional and both grant/deny route `Persisting` for Treatment. Add contextual strings in every shipped locale with reviewed native-language values; do not reuse the Usage Access wording for accessibility.

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*PromiseProposalViewModelTest' --tests '*OnboardingAnalyticsViewModelTest' :app:assembleDevDebug && python3 -m unittest scripts.tests.test_locale_string_parity scripts.tests.test_locale_string_quality_contract scripts.tests.test_accessibility_permission_copy_contract -v`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/uiery/keep/feature/onboarding/proposal app/src/main/java/com/uiery/keep/feature/onboarding/permission app/src/main/java/com/uiery/keep/feature/onboarding/notification app/src/test/java/com/uiery/keep/feature/onboarding app/src/main/res/values*/strings.xml
git commit -m "Connect permission requests to the promise the user chose" -m "Add the editable proposal and reuse existing permission screens with explicit Control/Treatment contexts.\n\nConstraint: Notification denial cannot block promise creation\nConfidence: high\nScope-risk: moderate\nTested: Proposal, permission analytics, recovery, and dev debug build"
```

### Task 11: Add idempotent Room 6→7 schema before creation logic

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/database/entity/FirstPromiseEntity.kt`
- Create: `app/src/main/java/com/uiery/keep/database/entity/FirstPromiseAnalyticsOutboxEntity.kt`
- Create: `app/src/main/java/com/uiery/keep/database/dao/FirstPromiseDao.kt`
- Create: `app/src/main/java/com/uiery/keep/database/dao/FirstPromiseAnalyticsOutboxDao.kt`
- Modify: `app/src/main/java/com/uiery/keep/database/KeepDatabase.kt`
- Modify: `app/src/main/java/com/uiery/keep/database/di/DatabaseModule.kt`
- Modify: `app/src/androidTest/java/com/uiery/keep/database/KeepDatabaseMigrationTest.kt`
- Generate: `app/schemas/com.uiery.keep.database.KeepDatabase/7.json`

- [ ] **Step 1: Add failing migration assertions**

Create v6 DB with existing routine, goal-lock, usage-cache data; migrate; assert all survive. Assert new tables/indices, insert mapping, insert sequence 10/20/30/40 rows, query order, delete routine and verify mapping cascades. Verify outbox rows remain until retention cleanup because they have no FK to routine.

- [ ] **Step 2: Implement entities and DAOs**

```kotlin
@Entity(
    tableName = "first_promise",
    foreignKeys = [ForeignKey(
        entity = RoutineEntity::class,
        parentColumns = ["id"], childColumns = ["routine_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["routine_id"], unique = true)],
)
data class FirstPromiseEntity(
    @PrimaryKey @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "goal_type") val goalType: String,
    val source: String,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

@Entity(tableName = "first_promise_analytics_outbox", primaryKeys = ["draft_id", "event_name"])
data class FirstPromiseAnalyticsOutboxEntity(
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "event_name") val eventName: String,
    val sequence: Int,
    @ColumnInfo(name = "canonical_event_name") val canonicalEventName: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "occurred_at_millis") val occurredAtMillis: Long,
    @ColumnInfo(name = "delivery_state") val deliveryState: String,
    @ColumnInfo(name = "sent_at_millis") val sentAtMillis: Long? = null,
)
```

Room stores `source` as the stable string because it is an on-disk schema field, but creation accepts only `FirstPromiseSource` and writes `proposal.draft.source.analyticsValue`; no caller passes an arbitrary string.

- [ ] **Step 3: Add exact migration SQL and providers**

Bump database to 7. `MIGRATION_6_7` creates both tables, unique routine index, and `first_promise_analytics_outbox(draft_id, sequence)` index. Register migration and both DAO providers in `DatabaseModule`.

- [ ] **Step 4: Generate schema and run migration test**

Run: `./gradlew :app:assembleDevDebug` then `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.database.KeepDatabaseMigrationTest`

Expected: schema `7.json` generated and all migration cases PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uiery/keep/database app/src/androidTest/java/com/uiery/keep/database app/schemas/com.uiery.keep.database.KeepDatabase/7.json
git commit -m "Preserve one routine and its ordered analytics identity" -m "Add first-promise mapping and outbox tables with an explicit 6-to-7 migration.\n\nConstraint: Routine schema remains unchanged\nConfidence: high\nScope-risk: moderate\nTested: Instrumented Room migration, cascade, sequence, and existing-data preservation"
```

### Task 12: Create, schedule, and recover exactly one first-promise routine

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseRepository.kt`
- Create: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseCreationCoordinator.kt`
- Create: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseAnalyticsDispatcher.kt`
- Create: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseOutboxEvent.kt`
- Create: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromisePracticeController.kt`
- Create: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseModule.kt`
- Create: `app/src/main/java/com/uiery/keep/data/lock/TimedLockSessionController.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/KeepApplication.kt`
- Create: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseRepositoryTest.kt`
- Create: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseCreationCoordinatorTest.kt`
- Create: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseAnalyticsDispatcherTest.kt`
- Create: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseOutboxEventTest.kt`
- Create: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromisePracticeControllerTest.kt`
- Create: `app/src/test/java/com/uiery/keep/data/lock/TimedLockSessionControllerTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/home/HomeViewModelActivationAnalyticsTest.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/lock/LockViewModelTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/MainActivityTest.kt`

- [ ] **Step 1: Lock existing Home timed-lock behavior before extraction**

Extend `HomeViewModelActivationAnalyticsTest` to assert selected apps, encoded deadline/start time, `lock_scheduled`, `lock_session_start`, and first-lock behavior for the current countdown path. Then write `TimedLockSessionControllerTest` for success, zero duration, empty apps, already-active rejection, and source-specific first-lock handling so practice cannot emit a duplicate first-lock event.

- [ ] **Step 2: Write failing repository/idempotency tests**

Same draft returns same routine id; concurrent duplicate taps produce one routine/mapping and one sequence 10/20 pair; exact-alarm resolution occurs before insert; missing exact alarm persists disabled with `disabled_exact_alarm_missing`; enabled scheduling success persists enabled; invalid/missing-permission scheduling outcome persists the returned disabled routine and final typed schedule state; any transaction failure retains the draft and moves `PersistFailed`. Inject a failure after scheduling and assert the coordinator cancels the just-created alarm before surfacing failure.

- [ ] **Step 3: Write failing typed outbox codec and dispatcher tests**

Define a sealed `FirstPromiseOutboxEvent` with fixed variants and sequences: `RoutineSaved(10)`, `FirstPromiseCreated(20)`, `AppBlockIntercepted(30)`, and `CoreAction(40)`. Each variant accepts existing typed enums/buckets only; local ids and observed usage facts are not fields. `FirstPromiseOutboxEventTest` asserts exact event name/sequence/key allowlist, round-trip encoding, unknown/mismatched sequence rejection, and JSON absence of package/app label/exact observation/routine/draft ids. Repository/DAO callers accept typed events and the codec alone creates raw entity strings.

Only lowest pending sequence sends; 20 waits for sent 10; Analytics return then sent marker; crash before marker retries (duplicate allowed); invalid/unknown payload is quarantined and reported rather than sent; sent rows older than 30 days delete; existing mapping prevents recreated rows after cleanup. App-start drain processes every draft in sequence order and a second startup is idempotent.

- [ ] **Step 4: Run repository/codec/dispatcher tests and require RED**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirstPromiseRepositoryTest' --tests '*FirstPromiseOutboxEventTest' --tests '*FirstPromiseAnalyticsDispatcherTest'`

Expected: FAIL because the repository transaction, typed codec, and dispatcher do not exist.

- [ ] **Step 5: Implement atomic creation without exposing provisional payloads**

Call `RoutineExactAlarmOrchestrator.resolveBeforePersist(draftRoutine)` first. `FirstPromiseRepository.createFirstPromise` uses one `KeepDatabase.withTransaction`: re-check draft id; insert the resolved routine to obtain its id; call `scheduleEnabledRoutine(routineWithId)` when enabled; persist any returned disabled state; then insert mapping and finalized typed `RoutineSaved`/`FirstPromiseCreated` events through the codec before commit. Entity `canonical_event_name`/`payload_json` are never constructed by feature callers. There is no provisional `BLOCKED→READY` protocol. If an exception occurs after scheduling but before commit, cancel that routine alarm and let Room roll back. A retry finds either the committed mapping and only drains its pending rows, or no mapping and repeats creation; it never creates a second committed routine. Dispatcher decodes and drains 10 then 20; only after both are sent and scheduling succeeded may the coordinator call `markFirstLockConfiguredIfNeeded()` and existing `trackFirstLockConfigured` for enabled routines.

- [ ] **Step 6: Extract and reuse the timed-lock session boundary**

Move deadline/start persistence plus `lock_scheduled` and `lock_session_start` into `TimedLockSessionController.start(packages, durationMinutes, origin)`. Use a local typed `TimedLockStartOrigin.Home|FirstPromisePractice`, but map both to the existing timed-lock analytics/scheduler contract; this value is never sent as `block_source`. Update `HomeViewModel.lockTime()` to call it and keep its current UI state/first-lock snackbar behavior. `FirstPromisePracticeController` calls the same boundary with one draft package, 10 minutes, and `FirstPromisePractice`; it adds/removes only the attribution token. A later interception still emits canonical `block_source=timed_lock`; Chunk 3 alone adds `promise_origin=first_promise_practice`.

- [ ] **Step 7: Implement practice outcome behavior**

Reject if accessibility is off, an active timed lock exists, or routine is disabled. On success the shared `TimedLockSessionController` writes selected package, start time, and deadline through `BlockingStateStore`; only after that succeeds does `FirstPromisePracticeController` write its attribution token and record `started`. Controller failure records `start_failed` and leaves CTA retryable. Secondary result action records one `skipped`; disabled result records no practice outcome. Extend `LockViewModelTest` first, then clear the token when the timed lock ends; `activeAt(now)` also atomically removes expired tokens, so app/process recreation cannot retain stale attribution. Wire `KeepApplication` to drain all pending outbox rows and 30-day cleanup on its existing IO application scope; `MainActivityTest`/dispatcher fakes verify startup invocation without blocking launch.

- [ ] **Step 8: Run focused tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.data.firstpromise.*' --tests '*TimedLockSessionControllerTest' --tests '*HomeViewModelActivationAnalyticsTest' --tests '*LockViewModelTest' --tests '*RoutineExactAlarmOrchestratorTest' --tests '*MainActivityTest'`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/uiery/keep/data/firstpromise app/src/main/java/com/uiery/keep/data/lock app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt app/src/main/java/com/uiery/keep/KeepApplication.kt app/src/test/java/com/uiery/keep/data/firstpromise app/src/test/java/com/uiery/keep/data/lock app/src/test/java/com/uiery/keep/feature/home/HomeViewModelActivationAnalyticsTest.kt app/src/test/java/com/uiery/keep/feature/lock/LockViewModelTest.kt app/src/test/java/com/uiery/keep/MainActivityTest.kt
git commit -m "Create the first promise once across scheduling failures" -m "Coordinate Room idempotency, exact-alarm finalization, ordered analytics readiness, and timed practice recovery.\n\nConstraint: Analytics cannot dispatch provisional schedule state\nConfidence: high\nScope-risk: broad\nTested: Repository, crash recovery, dispatcher, scheduler, and practice JVM tests"
```

### Task 13: Build PromiseResult and complete Treatment only after showing saved state

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/result/PromiseResultNavigation.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/result/PromiseResultScreen.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/onboarding/result/PromiseResultViewModel.kt`
- Create: `app/src/main/java/com/uiery/keep/feature/home/component/FirstPromiseResumeCard.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/home/HomeStatusCtaReadModelTest.kt`
- Create: `app/src/test/java/com/uiery/keep/feature/home/HomeViewModelFirstPromiseResumeTest.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/result/PromiseResultViewModelTest.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/OnboardingNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/KeepApp.kt`
- Modify: all shipped `app/src/main/res/values*/strings.xml` files.

- [ ] **Step 1: Write failing result tests**

Enabled shows practice only when accessibility is granted and no timed lock is active, otherwise shows the scheduled-time guidance; disabled never offers practice and shows `약속 켜기` plus later action; PersistFailed keeps edit/retry; exact-alarm return reuses routine id; `onboarding_step_view(promise_result)` and terminal `onboarding_step_complete(promise_result)` each fire once; Home completion sets `IS_NEW=false`, clears temporary draft but retains the disabled routine/mapping, and pops onboarding; Home shows the resume card only for that disabled mapping; practice started/skipped/start_failed follow Task 12 contract.

- [ ] **Step 2: Run and verify failure**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*PromiseResultViewModelTest' --tests '*HomeViewModelFirstPromiseResumeTest'`

Expected: FAIL.

- [ ] **Step 3: Implement enabled/disabled result content**

Render app label, start time, fixed 30 minutes, repeat days. Never say “준비됐어요” for disabled. Exact-alarm CTA uses existing `createExactAlarmSettingsIntent` plus app-details fallback, stores `PendingSystemAction.ExactAlarm`, and asks the coordinator to finalize the same routine after resume.

- [ ] **Step 4: Wire terminal navigation**

Both completed states navigate Home with onboarding inclusive pop. Keep a disabled routine in Room/mapping and clear only temporary draft/pending action; the routine remains visible in existing Routine UI.

- [ ] **Step 5: Add the minimal Home recovery surface required by the spec**

`HomeViewModel` queries the retained first-promise routine id/schedule state, mapping, and routine. Show the card while that mapped routine remains disabled because its saved state is `DisabledExactAlarmMissing`, regardless of current permission. If exact alarm is still unavailable, CTA opens the existing exact-alarm/app-details launcher and `ON_RESUME` finalizes the same routine id. If permission is already available on load/click/resume, finalize directly without opening settings. Hide only after activation succeeds or routine/mapping is deleted; a transient scheduling failure leaves retry visible. `HomeViewModelFirstPromiseResumeTest` covers unavailable→launcher→resume, already available→direct finalize, failure→visible retry, same routine id, deletion, and success hiding. Do not alter existing main Keep, timer, routine-creation, or Goal Lock CTA priority.

- [ ] **Step 6: Run tests and build**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*PromiseResultViewModelTest' --tests '*HomeStatusCtaReadModelTest' --tests '*HomeViewModelFirstPromiseResumeTest' :app:assembleDevDebug`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/uiery/keep/feature/onboarding/result app/src/main/java/com/uiery/keep/feature/onboarding/OnboardingNavigation.kt app/src/main/java/com/uiery/keep/KeepApp.kt app/src/main/java/com/uiery/keep/feature/home app/src/test/java/com/uiery/keep/feature/onboarding/result app/src/test/java/com/uiery/keep/feature/home/HomeStatusCtaReadModelTest.kt app/src/test/java/com/uiery/keep/feature/home/HomeViewModelFirstPromiseResumeTest.kt app/src/main/res/values*/strings.xml
git commit -m "Show the real promise state before completing onboarding" -m "Add enabled, disabled, retry, exact-alarm recovery, and ten-minute practice result paths.\n\nConstraint: Disabled routines are saved but never described as ready\nConfidence: high\nScope-risk: moderate\nTested: Result ViewModel tests and dev debug build"
```

## Chunk 3: Privacy-safe analytics, block attribution, and rollout verification

### Task 14: Add the product analytics contract and backend tests

**Use:** `@superpowers:test-driven-development`

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- Modify: `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`
- Modify: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseOutboxEvent.kt`
- Modify: `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/analytics/amplitude/AmplitudeEventAllowlistTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseOutboxEventTest.kt`
- Modify: `docs/ANALYTICS_EVENT_DICTIONARY.md`
- Modify: `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- Modify: `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md`

- [ ] **Step 1: Write failing event/payload tests**

Assert exact names and allowed enum values for exposure, usage analysis, recommendation shown/edited, first promise created, practice outcome, and optional typed `FirstPromiseOrigin.FirstPromiseRoutine|FirstPromisePractice`. Extend the typed outbox codec assertions for sequence 30/40 to the exact approved block/core key sets and backend mapping. Explicitly assert direct and durable payloads contain no package, app label, exact observed minutes/time, draft id, routine id, arbitrary origin strings, or unapproved keys.

- [ ] **Step 2: Complete the block attribution API and audit typed methods**

Keep the Task 1 typed onboarding methods as the only feature entry points. Add optional `promiseOrigin: FirstPromiseOrigin?` to `trackAppBlockIntercepted`, while still converting package to category bucket locally. Phase 1's ordered value funnel remains Firebase/GA4 because `app_block_intercepted` is intentionally Firebase-only; do not add any of the six new events to the Amplitude allowlist. Extend `AmplitudeEventAllowlistTest` to assert each remains filtered and the allowlist stays ≤30, and document that backend boundary in the Amplitude schema.

- [ ] **Step 3: Synchronize docs and registration ledger**

Document required GA4 dimensions and mark them `planned`, not registered. Add an explicit rule: missing queryability for any 50% rollout guardrail dimension automatically holds rollout. Do not claim live Admin registration.

- [ ] **Step 4: Run analytics tests and doc guards**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*FirebaseKeepAnalyticsTest' --tests '*AmplitudeEventAllowlistTest' --tests '*FirstPromiseOutboxEventTest' && python3 -m unittest scripts.tests.test_usage_stats_personalization_contract scripts.tests.test_analytics_feature_catalog_contract scripts.tests.test_ga4_custom_dimension_registration_docs scripts.tests.test_blocked_app_analytics_privacy_contract scripts.tests.test_first_lock_activation_contract scripts.tests.test_routine_saved_analytics_contract -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseOutboxEvent.kt app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt app/src/test/java/com/uiery/keep/analytics/amplitude/AmplitudeEventAllowlistTest.kt app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseOutboxEventTest.kt docs/ANALYTICS_EVENT_DICTIONARY.md docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md docs/analytics/AMPLITUDE_EVENT_SCHEMA.md
git commit -m "Measure promise activation without exporting usage identity" -m "Add the approved enum/bucket schema and keep GA4 registration status explicit.\n\nConstraint: Package, routine, draft, and observed timestamps stay local\nConfidence: high\nScope-risk: moderate\nTested: Firebase payload, Amplitude allowlist, and personalization contract tests\nNot-tested: GA4 Admin registration"
```

### Task 15: Preserve canonical block/core ordering with first-promise attribution

**Use:** `@superpowers:test-driven-development`

**Files:**
- Create: `app/src/main/java/com/uiery/keep/analytics/BlockAnalyticsCoordinator.kt`
- Create: `app/src/main/java/com/uiery/keep/analytics/FirstCoreActionDeliveryCoordinator.kt`
- Modify: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseAnalyticsDispatcher.kt`
- Modify: `app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseRepository.kt`
- Modify: `app/src/main/java/com/uiery/keep/database/dao/FirstPromiseAnalyticsOutboxDao.kt`
- Modify: `app/src/main/java/com/uiery/keep/BlockViewModel.kt`
- Modify: `app/src/test/java/com/uiery/keep/BlockViewModelTest.kt`
- Create: `app/src/test/java/com/uiery/keep/analytics/BlockAnalyticsCoordinatorTest.kt`
- Create: `app/src/test/java/com/uiery/keep/analytics/FirstCoreActionDeliveryCoordinatorTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseAnalyticsDispatcherTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseRepositoryTest.kt`

- [ ] **Step 1: Lock current behavior before refactoring**

Add regression assertions that ordinary paths emit `app_block_intercepted` before exactly one `first_core_action_completed`, then `core_action_completed` on repeat; parent mode dedicated event and first-success UI flag remain unchanged.

- [ ] **Step 2: Add failing first-promise ordering tests**

Routine id mapping→`first_promise_routine`; active practice token→`first_promise_practice`; unrelated paths omit origin. A first attributable block within `createdAtMillis + 86_400_000` atomically enqueues typed sequence 30 app-block and 40 selected core event; the exact deadline is exclusive. A first block outside that window creates no first-promise value rows, but its analytics job must first drain/await that draft's pending 10/20 barrier before using the ordinary direct canonical path with typed origin. Sequence 40 cannot send before 30. Pending first-core reservation makes all other paths choose repeat core and suppress first-success feedback. After 40 sent, dispatcher reconciles `HAS_TRACKED_FIRST_CORE_ACTION` before advancing. Once 30/40 are sent, later blocks do not recreate them. Test pending-10/20 at the 24-hour boundary, process restart, dispatcher failure/retry, and direct-path non-overtaking; enforcement UI remains immediate while only analytics waits.

- [ ] **Step 3: Run and verify failure**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*BlockAnalyticsCoordinatorTest' --tests '*FirstCoreActionDeliveryCoordinatorTest' --tests '*FirstPromiseAnalyticsDispatcherTest' --tests '*FirstPromiseRepositoryTest' --tests '*BlockViewModelTest'`

Expected: new tests FAIL while legacy regression tests PASS.

- [ ] **Step 4: Implement singleton coordinator and thin ViewModel delegation**

Serialize in-process decisions with a `Mutex`, but make the Room transaction that inserts the unique sequence-40 row and checks any existing pending reservation the durable concurrency source of truth. Test two concurrent attributable calls and one concurrent ordinary call: exactly one reserves `first_core_action_completed`; all others choose repeat core and suppress first-success feedback. Return `BlockAnalyticsResult(showFirstCoreActionFeedback: Boolean)` to the ViewModel. Never delay the actual block screen; only analytics dispatch waits. Preserve parent-mode and repeat-suggestion code outside the coordinator.

- [ ] **Step 5: Preserve the startup drain added with the dispatcher**

Do not add a second startup job: Task 12 already injects `FirstPromiseAnalyticsDispatcher` into `KeepApplication` and calls `drainAllReady()` plus 30-day cleanup in the existing IO application scope. Keep that wiring unchanged while creation and Block coordinators perform immediate draft-specific drains; rerun the startup idempotency test after coordinator integration.

- [ ] **Step 6: Run focused and startup tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests '*BlockAnalyticsCoordinatorTest' --tests '*FirstCoreActionDeliveryCoordinatorTest' --tests '*FirstPromiseAnalyticsDispatcherTest' --tests '*FirstPromiseRepositoryTest' --tests '*BlockViewModelTest' --tests '*MainActivityTest'`

Expected: PASS and exact ordered event assertions.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/uiery/keep/analytics/BlockAnalyticsCoordinator.kt app/src/main/java/com/uiery/keep/analytics/FirstCoreActionDeliveryCoordinator.kt app/src/main/java/com/uiery/keep/BlockViewModel.kt app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseAnalyticsDispatcher.kt app/src/main/java/com/uiery/keep/data/firstpromise/FirstPromiseRepository.kt app/src/main/java/com/uiery/keep/database/dao/FirstPromiseAnalyticsOutboxDao.kt app/src/test/java/com/uiery/keep/analytics/BlockAnalyticsCoordinatorTest.kt app/src/test/java/com/uiery/keep/analytics/FirstCoreActionDeliveryCoordinatorTest.kt app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseAnalyticsDispatcherTest.kt app/src/test/java/com/uiery/keep/data/firstpromise/FirstPromiseRepositoryTest.kt app/src/test/java/com/uiery/keep/BlockViewModelTest.kt
git commit -m "Keep first-promise value events ordered through process recovery" -m "Route attributable blocks through durable sequence 30/40 while preserving ordinary block behavior and feedback.\n\nConstraint: Analytics barriers must never delay enforcement UI\nConfidence: high\nScope-risk: broad\nTested: Block ordering, reservation, replay, attribution, and legacy ViewModel tests"
```

### Task 16: Complete integration, operational docs, and release gates

**Use:** `@superpowers:verification-before-completion`

**Files:**
- Modify: `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
- Modify: `docs/USAGE_STATS_PERSONALIZATION_MVP.md`
- Create: `app/src/androidTest/java/com/uiery/keep/feature/onboarding/PromiseCoachOnboardingIntegrationTest.kt`

- [ ] **Step 1: Add end-to-end navigation/device cases**

Cover Control unchanged; Treatment grant; Usage denial/manual; insufficient data; accessibility already enabled; notification denied; exact-alarm missing; process death at every pending system action; OEM Usage settings unavailable; enabled/disabled result; practice start and actual block attribution. Add deterministic device cases for midnight crossing, timezone change followed by scheduler recomputation, Usage Access revocation after setup, and clean reinstall/reset-only state.

- [ ] **Step 2: Update source-of-truth docs**

Record the experimental override to “do not ask on first run”, terminal completion by variant, ordered 24-hour funnel, matched BlockScreen emergency denominator, privacy gate, minimum samples, general/emergency kill behavior, and external boundaries. Do not mark privacy/Play approval, GA4 registration, rollout changes, or production impact complete.

- [ ] **Step 3: Run the full local verification sequence**

Run in order:

```bash
python3 -m unittest scripts.tests.test_usage_stats_personalization_contract -v
python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v
python3 -m unittest scripts.tests.test_locale_string_parity -v
python3 -m unittest scripts.tests.test_locale_string_quality_contract -v
python3 -m unittest scripts.tests.test_accessibility_permission_copy_contract -v
python3 -m unittest scripts.tests.test_user_facing_brand_strings -v
python3 -m unittest scripts.tests.test_korean_brand_copy_contract -v
python3 -m unittest scripts.tests.test_analytics_feature_catalog_contract -v
python3 -m unittest scripts.tests.test_ga4_custom_dimension_registration_docs -v
python3 -m unittest scripts.tests.test_blocked_app_analytics_privacy_contract -v
python3 -m unittest scripts.tests.test_first_lock_activation_contract -v
python3 -m unittest scripts.tests.test_routine_saved_analytics_contract -v
./gradlew :app:testDevDebugUnitTest
./gradlew :app:lintDevDebug
./gradlew :app:assembleProdDebug
./gradlew :app:connectedDevDebugAndroidTest
```

Expected: every command PASS. If no emulator/device is available, do not claim instrumented completion; preserve the command and report it as an explicit external verification gap.

- [ ] **Step 4: Perform privacy and payload inspection**

Use fake backend tests plus a dev-device DebugView session to verify zero package/app-label/exact usage/time/routine/draft values in new events. Confirm Remote Config defaults yield Control on a clean install and emergency kill moves only the documented phases. On at least one supported device, record screenshots/check results for light and dark theme, 100% and 200% font scale, TalkBack focus order/labels/actions, every shipped locale with truncation checks, midnight/timezone change, Usage Access revocation, and clean reinstall. Treat unavailable device/OEM/locale combinations as explicit external gaps, never as PASS.

- [ ] **Step 5: Review the final diff against the spec**

Check that there is no age/lifetime-loss UI, no 7-day auto-expiry, no streak/score/social feature, no Home visual redesign beyond the required disabled-promise recovery card, no Block visual redesign, no new dependency, and no unrelated code from `feature/onboarding-usage-aha`.

- [ ] **Step 6: Commit final integration evidence**

```bash
git add docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md docs/USAGE_STATS_PERSONALIZATION_MVP.md app/src/androidTest/java/com/uiery/keep/feature/onboarding/PromiseCoachOnboardingIntegrationTest.kt
git commit -m "Hold rollout until the promise flow is measurable and safe" -m "Finish integration coverage and align activation, privacy, and Usage Access operating contracts.\n\nConstraint: Production policy approval and GA4 Admin changes remain external\nConfidence: medium\nScope-risk: moderate\nTested: Static contracts, JVM, lint, prod debug build, and connected tests when available\nNot-tested: Production conversion impact and OEM matrix beyond available devices"
```

### Task 17: Final branch verification and handoff

**Use:** `@superpowers:requesting-code-review`, then `@superpowers:finishing-a-development-branch`

- [ ] **Step 1: Request code review against the design spec and this plan**

Require reviewers to check privacy, process-death idempotency, Room migration, scheduler races, Control regression, canonical Analytics order, accessibility, and exact-alarm disabled copy.

- [ ] **Step 2: Resolve findings and rerun affected tests**

Every fix follows failing regression test → minimal fix → focused verification. Do not batch unrelated cleanup.

- [ ] **Step 3: Commit review fixes separately when any exist**

Stage only files changed for an accepted finding and use a Lore message that names the risk and focused test. If review produces no code change, do not create an empty commit.

```bash
git commit -m "Close first-promise review risks before rollout" -m "Resolve accepted privacy, recovery, scheduler, or Control-regression findings without unrelated cleanup.\n\nConfidence: high\nScope-risk: narrow\nTested: Affected regression suites"
```

- [ ] **Step 4: Rerun the complete verification sequence after the final fix**

Repeat every static, JVM, lint, prod-debug, and connected command from Task 16 Step 3 after the last review fix. Capture each command and exit status. If connected/device verification is unavailable, record the exact skipped command and reason; do not substitute an earlier run.

- [ ] **Step 5: Verify branch state**

Run: `git status --short --branch && git log --oneline --decorate origin/develop..HEAD`

Expected: clean worktree; only intentional feature commits on `codex/onboarding-usage-promise-coach-spec`.

- [ ] **Step 6: Record evidence and remaining gates in the handoff**

Report changed files grouped by domain, simplifications/reuse decisions, the exact verification commands and outcomes, accepted/rejected review findings, and remaining risks. List privacy/Play copy approval, GA4 Admin registration/readback, Remote Config rollout percentage, connected OEM/device/accessibility/locale matrix, and production sample windows. None may be silently converted to “done”.
