# RoutineStore legacy compatibility cache 계약

Issue: #511
상태: **docs-lane 계약 고정 / code-lane 구현 전**

이 문서는 `PreferencesKey.ROUTINES`와 `RoutineStore`를 언제 유지하고 언제 퇴역시킬지 판단하기 위한 source of truth다. 현재 Stopit의 루틴 정의는 Room `routine` 테이블이 authoritative source이고, DataStore의 `routines` 값은 boot / package-replaced / routine alarm / restore-aftercare 경로에서 오래된 receiver 호환성을 보강하기 위한 legacy compatibility cache다.

## 현재 확인한 코드 경계

| 경계 | 현재 역할 | 계약 |
| --- | --- | --- |
| `RoutineStore` | `PreferencesKey.ROUTINES` JSON cache의 typed read/write wrapper | 새 코드가 raw `PreferencesKey.ROUTINES`를 직접 만지지 않도록 중앙화한다. |
| `RoutineReceiverPolicy.resolveRoutines(...)` | stored cache와 Room routine이 동시에 있을 때 사용할 루틴 집합 결정 | 현재는 **항상 Room routine이 이긴다**. cache는 primary read path가 아니다. |
| `BootReceiver.restoreRoutinesForBoot(...)` | boot / package-replaced / time 변경 후 Room 루틴을 읽고 enabled routine을 재스케줄 | Room 기준으로 스케줄하고, cache가 Room과 다르거나 exact-alarm 실패로 enabled 상태가 바뀌면 cache를 Room/result 기준으로 다시 쓴다. |
| `RoutineAlarmReceiver.handleRoutineAlarm(...)` | routine alarm 진입 시 notification, reschedule, fallback notice 처리 | trigger 자체는 intent extra를 쓰지만, 후속 reschedule 대상은 Room 기준으로 resolve한다. |
| `RoutineRestoreAftercare.rescheduleRestoredEnabledRoutinesFromRoom()` | 복원 직후 앱 실행/Splash/Routine 화면 진입에서 Room 루틴 재스케줄 | restored Room routine을 재스케줄하고 `RoutineStore` cache를 Room/result 기준으로 채운다. |
| `BackupRestoreDataStoreKeyPolicy` | backup/restore에서 DataStore key 분류 | `PreferencesKey.ROUTINES`는 restore하지 않는 `rehydratedCompatibilityCacheKeys` 예외이며, Room에서 재수화 가능해야 한다. |

## 유지/퇴역 결정

### 현재 결정: 유지하되 non-authoritative cache로만 유지

#511의 첫 repo-internal 계약은 cache를 즉시 제거하지 않는 것이다. 이유는 다음과 같다.

1. boot / package-replaced / routine alarm / restore-aftercare 경로가 이미 `RoutineStore`를 통해 cache rehydrate를 수행한다.
2. backup/restore 정책은 DataStore 전체를 복원하지 않는 대신 Room DB 복원 후 cache를 재작성하는 shape로 고정되어 있다.
3. exact alarm permission 실패 시 enabled routine을 `enabled=false`로 내리는 결과도 receiver/aftercare가 cache에 반영해 runtime 호환성을 유지한다.
4. 제거하려면 receiver/alarm/startup 경로가 Room-only로 동작한다는 device/emulator evidence와 fallback notice/notification side effect 검증이 먼저 필요하다.

따라서 code-lane 구현 전까지 `RoutineStore`는 아래처럼 해석한다.

- **authoritative source:** Room `routine` table
- **compatibility cache:** `PreferencesKey.ROUTINES`
- **cache writer:** `RoutineStore.writeCachedRoutines(...)`
- **cache reader:** `RoutineStore.readCachedRoutines(...)`, 단 primary decision은 `RoutineReceiverPolicy.resolveRoutines(...)`가 Room 우선으로 결정
- **cache invalidation/rehydration trigger:** boot/package-replaced/time change, routine alarm, restore-aftercare, Routine 화면 aftercare, exact alarm scheduling result update

## conflict-winner 계약

Room과 cache가 불일치하면 Room이 이긴다.

필수 규칙:

1. `databaseRoutines`가 비어 있고 `storedRoutines`만 있으면, receiver/startup은 stale cache를 authoritative routine으로 승격하지 않는다.
2. `databaseRoutines`와 `storedRoutines`가 다르면, `RoutineReceiverPolicy.resolveRoutines(...)`는 Room 결과를 반환한다.
3. enabled routine 스케줄 중 `MissingExactAlarmPermission`이 나오면, Room `enabled=false` 업데이트와 cache rewrite가 같은 결과 집합을 따라야 한다.
4. cache JSON이 null/blank/malformed이면 empty cache로 취급하고 crash 없이 Room 기준으로 재수화한다.
5. user-facing routine list, routine card, routine edit/delete flow는 cache가 아니라 Room/repository state를 기준으로 한다.

## code-lane 구현/테스트 handoff

#511 code-lane은 둘 중 하나를 명시적으로 선택해야 한다.

### 선택 A — cache 유지 hardening

- `RoutineReceiverPolicy.resolveRoutines(...)` / `shouldRehydrateStoredRoutines(...)`의 Room-wins 계약을 유지한다.
- `RoutineStore` 외부 raw `PreferencesKey.ROUTINES` production read/write를 금지하거나 최소화한다.
- boot/package-replaced/routine alarm/restore-aftercare 충돌 시나리오를 JVM 또는 instrumentation test로 고정한다.
- docs에는 cache retirement criteria를 남긴다.

권장 focused 검증:

```bash
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.receiver.RoutineReceiverPolicyTest' --tests 'com.uiery.keep.datastore.RoutineStoreTest' --tests 'com.uiery.keep.feature.routine.RoutineViewModelRestoreSchedulingTest'
./gradlew --console=plain :app:testDevDebugUnitTest
python3 -m unittest scripts.tests.test_routinestore_compatibility_cache_contract -v
git diff --check
```

### 선택 B — cache 제거 / Room-only 수렴

- `RoutineStore`와 `PreferencesKey.ROUTINES` production dependency를 제거한다.
- backup/restore policy에서 `rehydratedCompatibilityCacheKeys` 예외를 제거하거나 빈 set 계약으로 바꾼다.
- boot/package-replaced/routine alarm/restore-aftercare가 Room-only로 같은 runtime 결과를 내는지 device/emulator evidence를 남긴다.
- legacy migration/rollback 리스크 때문에 docs-only PR이 아니라 code + runtime QA PR로 다룬다.

권장 추가 검증:

```bash
./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest
./gradlew --console=plain :app:assembleProdDebug
```

## 문서/운영 해석 경계

- `docs/BACKUP_RESTORE_POLICY.md`는 DataStore 전체 제외 + Room DB 복원 + `PreferencesKey.ROUTINES` Room 재수화 예외를 설명한다.
- `docs/QA_RUNTIME_CHECKLIST.md`는 release/runtime evidence에서 Room-vs-cache conflict winner와 cache rewrite evidence를 따로 기록해야 한다.
- `app/src/main/java/com/uiery/keep/datastore/AGENTS.md`는 새 DataStore 작업자가 `RoutineStore` / `PreferencesKey.ROUTINES`를 primary state로 오해하지 않도록 한다.
- `docs/ops/stopit/engineering-context.md`는 Stopit cron/subagent가 DataStore/Room drift를 볼 때 이 문서를 확인하도록 링크한다.

## PR/이슈 연결 규칙

이 docs-lane 패키지는 cache 유지/퇴역의 운영 계약과 code-lane handoff를 고정하므로 PR body는 `Refs #511`를 사용한다. #511을 닫으려면 code-lane에서 cache 유지 hardening 또는 Room-only 제거 중 하나를 구현하고, conflict-winner 테스트와 필요한 receiver/restore focused 검증이 통과해야 한다.
