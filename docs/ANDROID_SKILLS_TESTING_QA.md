# Stopit Android Skills Testing QA

Stopit QA는 설치된 Android 공식 skills와 `skydoves/android-testing-skills` 커뮤니티 테스트 skills를 함께 기준으로 운영한다.

## 사용 skill

로컬 설치 위치:

- `/Users/uiel/.agents/skills/testing-setup/SKILL.md`
- `/Users/uiel/.agents/skills/android-cli/SKILL.md`
- `/Users/uiel/.agents/skills/structuring-a-compose-test/SKILL.md`
- `/Users/uiel/.agents/skills/finding-nodes-by-tag-text-content/SKILL.md`
- `/Users/uiel/.agents/skills/cross-app-tests-with-uiautomator/SKILL.md`
- `/Users/uiel/.agents/skills/running-instrumented-tests-via-adb/SKILL.md`
- `/Users/uiel/.agents/skills/capturing-screenshots-and-screenrecord/SKILL.md`

작업자가 테스트 전략, UI 테스트, screenshot/evidence, end-to-end/runtime QA를 다룰 때는 먼저 관련 skill 파일을 읽고 현재 앱 구조에 맞춰 적용한다.

`skydoves/android-testing-skills`는 전체 54개 skill 카탈로그이므로 새 QA 작업을 만들 때는 증상/목표별로 필요한 skill만 추가로 읽는다. 원본/업데이트 위치는 `/Users/uiel/.agents/skills-sources/android-testing-skills`이다.

## 현재 Stopit 테스트 전략

Android `testing-setup` skill 기준으로 Stopit은 다음 계층을 사용한다.

1. Unit/JVM tests
   - business logic, ViewModel, policy, repository/DAO 성격 로직을 빠르게 검증한다.
   - 명령: `./gradlew :app:testDevDebugUnitTest`
   - release 명령: `./gradlew :app:testProdReleaseUnitTest`

2. Compose UI behavior smoke
   - 복잡한 matcher 대신 stable semantics tag를 우선 사용한다.
   - 현재 release smoke anchor: `stopit_app_nav_host`
   - 테스트 파일: `app/src/androidTest/java/com/uiery/keep/qa/StopitReleaseSmokeTest.kt`

3. Device/emulator end-to-end/runtime tests
   - Android runtime이 필요한 흐름은 `androidTest`에서 실행한다.
   - 명령: `./gradlew :app:connectedDevDebugAndroidTest`
   - release focused smoke:

```bash
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest
```

4. Runtime/manual evidence
   - 접근성 서비스, 실제 앱 차단, 긴급해제 만료, boot/cold boot, notification/alarm evidence는 `docs/QA_RUNTIME_CHECKLIST.md`를 따른다.
   - 자동화로 덮기 어려운 항목은 adb/logcat/dumpsys/screenshot evidence를 PR에 남긴다.

## Release QA gate

Release QA의 세부 단계 source of truth는 `.github/workflows/release-qa.yml`, `scripts/android_runtime_suites.py`, `docs/ops/stopit/release-context.md`다. Workflow는 install/appops sequencing을 소유하고, `scripts/android_runtime_suites.py`는 instrumentation selector fragment만 소유한다. 이 문서는 release evidence 작성자와 QA 작업자가 그 계약을 사람 기준으로 빠르게 해석할 수 있게 같은 계약을 풀어쓴 운영 가이드다.

현재 `Release instrumentation QA` job은 GitHub-hosted Android emulator에서 아래 suite 순서로 실행된다: `release_focused_ui_smoke` → `release_exact_alarm_default` → `release_exact_alarm_denied` → `release_exact_alarm_allowed` → `release_remaining_runtime` → `notification_denied_receiver` → `notification_denied_emergency_unlock` → `notification_channel_disabled`.

1. static manifest/policy gate
   - `python3 -m unittest scripts.tests.test_android_manifest_contract`
   - 목적: emulator 기동 전에 `QUERY_ALL_PACKAGES` 목적 주석, notification/exact-alarm/boot permission, receiver/service/activity exported 계약, AccessibilityService binding/metadata, backup/data-extraction XML의 DB-only include scope를 고정한다.
   - 책임 분리: 이 정적 테스트는 XML shape drift를 빠르게 막고, `ManifestContractIntegrationTest`는 PackageManager가 실제 설치 package에서 receiver/service를 resolve하는 runtime 계약만 확인한다.
2. Android testing skill 기반 focused release UI smoke
   - `com.uiery.keep.qa.StopitReleaseSmokeTest`
   - 목적: 앱 기동과 Compose navigation host 기본 smoke 확인
3. exact alarm default gate — fresh/default AppOps
   - `adb shell cmd appops reset com.uiery.keep.dev`
   - `RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability`
   - 목적: AppOps `MODE_DEFAULT`에서는 `AlarmManager.canScheduleExactAlarms()` 결과를 authoritative default 상태로 따르며, fresh install/OEM 기본 상태를 권한 없음으로 오판하지 않는지 확인
4. exact alarm deny gate 1 — 루틴 생성
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
   - `RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
   - 목적: 단일·multi-day 루틴 저장/enable 시 exact alarm 권한 부재를 조용한 성공 상태로 남기지 않는지 확인
5. exact alarm deny gate 2 — boot 복구
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
   - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
   - 목적: boot 복구 경로가 권한 회수 상태에서도 enabled 루틴/알람 불일치를 남기지 않는지 확인
6. exact alarm deny gate 3 — package replaced
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
   - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
   - 목적: `MY_PACKAGE_REPLACED` 재진입 경로가 같은 fail-safe 계약을 지키는지 확인
7. exact alarm deny gate 4 — routine alarm receiver
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
   - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm`
   - 목적: 루틴 alarm 재예약 경로가 권한 부재를 조용히 성공으로 남기지 않는지 확인
8. exact alarm allow/cancel/permission-change restore gate
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow`
   - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
   - `RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm`
   - `RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent`
   - `ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEnabledRoutineFromRoom`
   - `ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEveryRepeatDayAlarm`
   - 목적: 허용 상태에서는 단일·multi-day 루틴이 실제 PendingIntent 예약까지 정상 복구되고 cancel이 모든 반복일 알람을 제거하며, OS 정확 알람 권한 변경 broadcast 후 Room 기준 enabled 루틴이 다시 예약되는지 확인
9. release remaining connected Android suite
   - Release QA source of truth: `.github/workflows/release-qa.yml` `Release instrumentation QA`
   - `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
   - 목적: release smoke, backup/restore runtime reset, accessibility permission resume, receiver 단일·multi-day 재수화/재예약, manifest contract, emergency unlock expiry, FCM token wiring, AccessibilityService cross-app block safety를 한 묶음으로 검증
10. notification-denied receiver fallback gate
   - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
   - 목적: 현재 지원 범위는 minSdk 33 / Android 13+ `POST_NOTIFICATIONS` runtime permission이므로, 알림 권한이 꺼져 있어도 dev flavor package(`com.uiery.keep.dev`)에서 루틴 시작 안내가 앱 내 fallback notice로 이어지는지 분리 검증
11. notification-denied emergency-unlock gate
   - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`
   - 목적: 현재 지원 범위는 minSdk 33 / Android 13+ `POST_NOTIFICATIONS` runtime permission이므로, dev flavor package(`com.uiery.keep.dev`)에서 긴급해제 만료 알림 helper가 permission-denied로 안전하게 종료되는지 분리 검증
12. notification-channel-disabled gate
   - `./gradlew :app:installDevDebug && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.notification.NotificationChannelDisabledIntegrationTest`
   - 목적: 앱 전체 알림/`POST_NOTIFICATIONS`는 허용된 상태에서 루틴·긴급해제 notification channel만 `IMPORTANCE_NONE`인 경우 fallback/cancel 계약을 release baseline에서도 검증

정리하면 release candidate runtime baseline은 `focused UI smoke -> exact alarm default(MODE_DEFAULT) -> exact alarm deny(8개, multi-day 포함) -> exact alarm allow/cancel/permission-change restore(5개) -> remaining connected suite -> notification-denied receiver gate -> notification-denied emergency-unlock gate -> notification-channel-disabled gate` 순서다. Android CI focused runtime smoke는 별도 PR gate이므로 release/hotfix 증거에는 `.github/workflows/release-qa.yml`의 Release instrumentation QA 목록을 기준으로 기록한다. exact alarm/notification appops 전환은 target app 프로세스를 죽일 수 있으므로, 권한 상태 변경은 테스트 메서드 안이 아니라 **host ADB 명령 → focused instrumentation 실행** 순서를 유지해야 한다. Android 12L 이하 legacy 설정 왕복과 `settings_opened` 기반 notification onboarding 검증은 historical / out of scope이며, minSdk를 다시 낮출 때만 현재 검증 대상으로 복원한다.

`main` 대상 PR에서는 `Version Guard`가 항상 보여야 하며, 정상적인 release/hotfix PR은 `Full release QA`, `Release instrumentation QA`, `Android Release Build`, `Version Guard`, `Branch Hygiene`가 모두 green이 되기 전 `main`으로 merge하지 않는다.

Runtime smoke 실패 triage artifact는 두 workflow가 같은 경계로 남긴다. Android CI focused runtime smoke는 `stopit-runtime-smoke-diagnostics`, Release instrumentation QA는 `stopit-release-instrumentation-diagnostics` artifact를 `retention-days: 7`로 업로드한다. 이 artifact upload는 테스트 실패를 가리지 않는 non-blocking 단계이며, 먼저 `app/build/reports/androidTests` report를 보고, 다음으로 `app/build/outputs/androidTest-results` raw result, 마지막으로 `runtime-diagnostics/**`의 `logcat`, `dumpsys alarm`, `dumpsys accessibility`를 확인한다.

Signed release build / non-production Play deploy 실패 triage artifact는 runtime smoke와 분리한다. Android Release Build는 `stopit-release-build-diagnostics`, non-production Android Play Deploy는 `stopit-play-deploy-release-diagnostics` artifact를 `retention-days: 7`로 업로드한다. 이 upload는 `if-no-files-found: ignore`와 `continue-on-error: true`를 쓰는 non-blocking 진단 단계이며, 먼저 `app/build/reports`의 prodRelease lint/test report, 다음으로 `app/build/test-results`, 그 다음 `app/build/outputs/logs`, 마지막으로 `app/build/outputs/mapping/prodRelease`를 확인한다. Production promotion은 새 AAB를 빌드하지 않으므로 이 release diagnostics artifact 대상이 아니다.

## Android CLI 활용

로컬 기기/에뮬레이터 evidence 수집 시 `android-cli` skill을 기준으로 다음 도구를 우선 사용한다.

```bash
android layout --pretty -o qa-artifacts/layout.json
android screenshot -o qa-artifacts/screenshot.png
android run --apks app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

필요 시 기존 adb evidence도 함께 남긴다.

```bash
adb shell dumpsys alarm | grep com.uiery.keep
adb logcat -d | grep -E "RoutineAlarmReceiver|BootReceiver|KeepAccessibilityService|EmergencyUnlock"
```

## 새 QA 작업 추가 기준

- [ ] Compose UI behavior test는 semantics matcher를 우선 사용한다. `structuring-a-compose-test`와 `finding-nodes-by-tag-text-content`를 읽고 테스트 구조와 finder를 정한다.
- [ ] matcher가 복잡해지면 production UI에 명시적 `testTag`를 작게 추가한다.
- [ ] platform/system UI, notification, settings, accessibility permission이 필요한 journey는 `cross-app-tests-with-uiautomator` 기준으로 UIAutomator를 사용한다.
- [ ] adb 직접 실행, sharding, runner argument가 필요한 경우 `running-instrumented-tests-via-adb`를 기준으로 명령을 만든다.
- [ ] 실패 evidence가 필요한 release/runtime QA는 `capturing-screenshots-and-screenrecord` 기준으로 screenshot/logcat artifact를 남긴다.
- [ ] end-to-end test는 적은 수의 핵심 사용자 journey만 유지한다.
- [ ] screenshot test는 behavior 검증을 대체하지 않는다. 화면 회귀/evidence 용도로 분리한다.
