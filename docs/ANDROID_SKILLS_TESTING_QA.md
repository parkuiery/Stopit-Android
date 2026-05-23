# Stopit Android Skills Testing QA

Stopit QA는 설치된 Android 공식 skills를 기준으로 운영한다.

## 사용 skill

로컬 설치 위치:

- `/Users/uiel/.agents/skills/testing-setup/SKILL.md`
- `/Users/uiel/.agents/skills/android-cli/SKILL.md`

작업자가 테스트 전략, UI 테스트, screenshot/evidence, end-to-end/runtime QA를 다룰 때는 먼저 위 두 파일을 읽고 현재 앱 구조에 맞춰 적용한다.

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

`.github/workflows/release-qa.yml`의 `Release instrumentation QA` job은 GitHub-hosted Android emulator에서 다음을 실행한다.

1. Android testing skill 기반 focused release UI smoke
2. 전체 `:app:connectedDevDebugAndroidTest`

release/hotfix PR은 이 job과 `Full release QA`, `Android Release Build`, `Version Guard`, `Branch Hygiene`가 green이 되기 전 `main`으로 merge하지 않는다.

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

- Compose UI behavior test는 semantics matcher를 우선 사용한다.
- matcher가 복잡해지면 production UI에 명시적 `testTag`를 작게 추가한다.
- platform/system UI, notification, settings, accessibility permission이 필요한 journey는 UIAutomator를 사용한다.
- end-to-end test는 적은 수의 핵심 사용자 journey만 유지한다.
- screenshot test는 behavior 검증을 대체하지 않는다. 화면 회귀/evidence 용도로 분리한다.
