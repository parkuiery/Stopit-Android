# STO-437 Lock-State Evidence Packet

- Prepared for: CEO action support via STO-469
- Prepared by: Engineering Execution Lead (agent `67cd3afb-ce60-4cf9-86b2-013295e45d61`)
- Snapshot date: 2026-04-29 00:40:10 KST (+0900)
- Code snapshot: `b572f46`

## 1) Objective
Provide verifiable evidence for how lock-state is created, enforced, bypassed (emergency unlock), and recorded in the current Android codebase.

## 2) Evidence Summary (Lock-State Chain)

### A. Lock-state sources are persisted in DataStore
- `IS_KEEP`, `LOCK_TIME`, emergency unlock app set/expiry, and selected apps are persisted as canonical keys.
- Source:
  - `app/src/main/java/com/uiery/keep/datastore/DataStore.kt:15-27`

### B. Manual and timed lock activation are explicitly written by HomeViewModel
- Keep switch toggle writes `IS_KEEP` and records start/end analytics and block duration.
- Timer lock writes `LOCK_TIME`, emits lock start analytics, and records lock history duration.
- Source:
  - `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt:47-66`
  - `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt:174-179`
  - `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt:233-253`
  - `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt:131-166`

### C. Runtime enforcement is centralized in AccessibilityService
- Service continuously caches lock-related prefs from DataStore.
- On foreground app change, it blocks when any lock condition is true:
  - manual keep (`isKeep`)
  - active timer (`lockTime` in future)
  - active routine match
- If blocked and app is in selected set (or routine-targeted), it launches `BlockActivity`.
- Source:
  - `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt:57-70`
  - `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt:74-103`

### D. Emergency unlock has in-memory + persisted state, with expiry cleanup
- Unlock action writes both singleton runtime state and DataStore state, then logs Room history.
- Service checks both singleton and persisted unlock state to allow temporary bypass.
- Expired unlock state is actively cleaned from singleton and DataStore.
- Source:
  - `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt:185-236`
  - `app/src/main/java/com/uiery/keep/service/EmergencyUnlockState.kt:12-14`
  - `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt:154-181`

### E. Lock/e-unlock history and analytics are recorded for auditability
- Lock sessions are persisted to Room lock history.
- Emergency unlock events are persisted to Room emergency unlock table.
- Analytics tracks lock start/end and emergency unlock usage.
- Source:
  - `app/src/main/java/com/uiery/keep/database/entity/LockHistoryEntity.kt:7-15`
  - `app/src/main/java/com/uiery/keep/database/entity/EmergencyUnlockEntity.kt:7-15`
  - `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt:155-166`
  - `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt:207-222`

## 3) Verification Evidence (Executed)

### Command run
```bash
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest" \
  --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest"
```

### Result
- Gradle result: `BUILD SUCCESSFUL in 9s`
- Report files:
  - `app/build/test-results/testDevDebugUnitTest/TEST-com.uiery.keep.service.EmergencyUnlockPolicyTest.xml`
  - `app/build/test-results/testDevDebugUnitTest/TEST-com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.xml`

### Test XML evidence
- `EmergencyUnlockPolicyTest`: `tests="2" failures="0" errors="0"`
- `FirebaseKeepAnalyticsTest`: `tests="3" failures="0" errors="0"`

## 4) Known Gaps / Limits
- This packet provides code-level and JVM-test evidence only.
- Device-level validation of AccessibilityService behavior (`connectedAndroidTest`) was not executed in this heartbeat.
- No production telemetry export is included in this packet.

## 5) Next Action
If CEO action requires runtime proof on device, execute a focused instrumented scenario run (`connectedAndroidTest` + screen recording/logcat evidence) and append a second packet revision.
