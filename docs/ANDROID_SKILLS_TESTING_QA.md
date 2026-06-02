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

Release QA의 세부 단계 source of truth는 `.github/workflows/release-qa.yml`과 `docs/ops/stopit/release-context.md`다. 이 문서는 release evidence 작성자와 QA 작업자가 그 workflow를 사람 기준으로 빠르게 해석할 수 있게 같은 계약을 풀어쓴 운영 가이드다.

현재 `Release instrumentation QA` job은 GitHub-hosted Android emulator에서 아래 순서로 실행된다.

1. Android testing skill 기반 focused release UI smoke
   - `com.uiery.keep.qa.StopitReleaseSmokeTest`
   - 목적: 앱 기동과 Compose navigation host 기본 smoke 확인
2. exact alarm deny gate 1
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt`
   - 목적: 루틴 저장/enable 시 exact alarm 권한 부재를 조용한 성공 상태로 남기지 않는지 확인
3. exact alarm deny gate 2
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
   - 목적: boot 복구 경로가 권한 회수 상태에서도 enabled 루틴/알람 불일치를 남기지 않는지 확인
4. exact alarm deny gate 3
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent`
   - 목적: `MY_PACKAGE_REPLACED` 재진입 경로가 같은 fail-safe 계약을 지키는지 확인
5. exact alarm deny gate 4
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
   - `ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent`
   - 목적: 루틴 alarm 재예약 경로가 권한 부재를 조용히 성공으로 남기지 않는지 확인
6. exact alarm allow gate
   - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow`
   - `RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm`
   - 목적: 허용 상태에서는 같은 루틴이 실제 PendingIntent 예약까지 정상 복구되는지 확인
7. remaining connected Android suite
   - `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
   - 목적: release smoke, backup/restore runtime reset, receiver 재수화/재예약, emergency unlock expiry, FCM token wiring, AccessibilityService cross-app block safety를 한 묶음으로 검증
8. notification-denied fallback gate
   - `./gradlew :app:installDevDebug && adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`
   - 목적: Android 13+에서 알림 권한이 꺼져 있어도 루틴 시작 안내가 앱 내 fallback notice로 이어지는지 분리 검증

정리하면 release candidate runtime baseline은 `focused UI smoke -> exact alarm deny(4개) -> exact alarm allow(1개) -> remaining connected suite -> notification-denied fallback` 순서다. exact alarm/notification appops 전환은 target app 프로세스를 죽일 수 있으므로, 권한 상태 변경은 테스트 메서드 안이 아니라 **host ADB 명령 → focused instrumentation 실행** 순서를 유지해야 한다.

`main` 대상 PR에서는 `Version Guard`가 항상 보여야 하며, 정상적인 release/hotfix PR은 `Full release QA`, `Release instrumentation QA`, `Android Release Build`, `Version Guard`, `Branch Hygiene`가 모두 green이 되기 전 `main`으로 merge하지 않는다.

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
