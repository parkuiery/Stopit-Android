# Stopit Runtime QA Checklist

이 문서는 리시버/서비스 계층의 Android 런타임 검증을 반복 가능하게 만들기 위한 수동 QA 기준이다.

범위:
- `BootReceiver`
- `RoutineAlarmReceiver`
- `KeepAccessibilityService`
- 긴급해제 만료/차단 복귀
- Usage Access 선택형 개인화 discovery / 권한 복귀 QA
- release 전 device/emulator 검증 순서

백업/복원 정책 자체는 `docs/BACKUP_RESTORE_POLICY.md`를 source of truth로 본다. 현재 정책은 `keep-database`만 복원하고 `keep-datastore`는 통째로 제외하는 보수적 계약이다. 이 문서는 복원 이후에도 receiver/service/runtime 상태가 안전하게 동작하는지 확인하는 실행 체크리스트다.

Usage Access 기반 개인화 리포트/추천은 `docs/USAGE_STATS_PERSONALIZATION_MVP.md`를 source of truth로 본다. 현재 #119는 구현 착수용 `ready`가 아니라 discovery gate이며, 이 체크리스트는 향후 child issue가 생겼을 때 권한 허용/거절/fallback과 privacy analytics guardrail을 반복 검증하기 위한 evidence 표면만 미리 고정한다.

비범위:
- Room migration 세부 검증
- Play Console 수동 프로모션 절차
- 대규모 instrumented test 구현

> 현재 저장소의 `androidTest` 자동화는 release 전체를 대체하지는 않지만, 기본 Android CI focused runtime smoke가 이미 핵심 런타임 계약을 자동 검증한다: `StopitReleaseSmokeTest`(앱 기동 smoke), `BackupRestoreRuntimeResetIntegrationTest`(복원 후 reset-only state 미복원), `HomeAccessibilityPermissionIntegrationTest`(홈 접근성 권한 경고 재동기화 + substring false positive 방지), focused `ReceiverRuntimeIntegrationTest` 메서드들(boot/package/time/timezone 재수화, multi-day 반복요일, 루틴 시작 재예약), Android CI exact-alarm 최소 smoke(`android_ci_exact_alarm_default`, `android_ci_exact_alarm_denied`, `android_ci_exact_alarm_allowed`), 별도 `POST_NOTIFICATION ignore` receiver fallback notice 메서드, `EmergencyUnlockExpiryIntegrationTest`(긴급해제 만료 cleanup + 재차단 대상), `KeepMessagingServiceIntegrationTest`(stale FCM token overwrite), `KeepAccessibilityServiceIntegrationTest`(cross-app foreground 차단 + emergency unlock 우회 + self-uninstall interception safety). 이 체크리스트는 그 자동화가 아직 덮지 못하는 cold boot, 실제 사용자 앱 조합별 foreground 전환 같은 수동 증거를 release 전에 반복하기 위한 최소 기준이다.
>
> Android CI runtime smoke와 Release instrumentation QA는 `retention-days: 7`의 non-blocking 진단 artifact를 남긴다. Android CI는 `scripts/android_runtime_suites.py run-android-ci` aggregate mode로 `android_ci_focused_runtime_smoke`가 실패해도 `android_ci_exact_alarm_default`, `android_ci_exact_alarm_denied`, `android_ci_exact_alarm_allowed`, `notification_denied_receiver`, `notification_denied_emergency_unlock`, `notification_channel_disabled`까지 가능한 한 실행한 뒤 최종 non-zero로 실패한다. Release QA는 suite별 fail-fast 경계를 유지한다. Android CI triage는 `stopit-runtime-smoke-diagnostics`, Release QA는 `stopit-release-instrumentation-diagnostics`를 먼저 확인한다. triage 순서는 `app/build/reports/androidTests` HTML/XML report → `app/build/outputs/androidTest-results` raw result → `runtime-diagnostics/**`의 `logcat`, `dumpsys alarm`, `dumpsys accessibility` 순서다. Artifact upload 자체는 실패 원인을 가리지 않도록 non-blocking이며, quota failure는 코드 회귀가 아니라 GitHub Actions artifact storage boundary로 분리한다.
>
> Signed release build / non-production Play deploy 실패 triage는 runtime smoke artifact와 분리한다. Android Release Build는 `stopit-release-build-diagnostics`, non-production Android Play Deploy는 `stopit-play-deploy-release-diagnostics`를 `retention-days: 7`로 남긴다. 이 artifact upload도 non-blocking이며 `if-no-files-found: ignore`로 report가 아직 생성되지 않은 초반 실패를 가리지 않는다. 확인 순서는 `app/build/reports`의 prodRelease lint/test report → `app/build/test-results` → `app/build/outputs/logs` → `app/build/outputs/mapping/prodRelease`다. Production promotion은 새 AAB를 빌드하지 않으므로 release diagnostics artifact 대상이 아니다.

## 1. 사전 준비

### 로컬 필수 조건

- Android Studio 또는 Android SDK/ADB 사용 가능
- `local.properties`가 현재 worktree에 존재
- 필요 flavor의 `google-services.json`이 현재 worktree에 복원되어 있음
- 테스트 기기 또는 에뮬레이터 1대 이상 연결

`google-services.json` 준비를 수동으로 할 때도 secret 의미를 `공용 파일`이나 `prod 전용 파일`로 오해하지 말고, workflow별 dev+prod/prod-only/production-promotion-unused restore matrix는 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`를 source of truth로 확인한다. Android CI / Release QA에서는 `GOOGLE_SERVICES_JSON_DEV`를 `app/src/dev`에, `GOOGLE_SERVICES_JSON`를 `app/src/prod`에 복원하고, Release Build / Play Deploy non-production build/upload에서는 `app/src/prod`에만 `GOOGLE_SERVICES_JSON`를 복원한다. Play Deploy production promotion은 Firebase config와 Android signing을 복원하지 않고 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`로 matching internal release를 승격하는 경로다.

`dev` / `prod` applicationId 분리 작업(#314) 또는 package identity와 관련된 runtime QA는 `docs/FLAVOR_APPLICATION_ID_CONTRACT.md`를 먼저 확인한다. dev package가 `com.uiery.keep.dev`로 분리되면 host-side `adb shell appops set ...` 명령도 dev runtime smoke는 `com.uiery.keep.dev`, production/release evidence는 `com.uiery.keep` 대상으로 분리해서 기록해야 한다.

### 권장 사전 명령

```bash
cd <repo-root>
./gradlew -q help --task :app:testDevDebugUnitTest
./gradlew -q help --task :app:connectedDevDebugAndroidTest
```

### 자동화 기본선

작은 코드 변경이 함께 있는 PR이라면 최소한 아래 중 하나를 같이 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest
./gradlew :app:connectedDevDebugAndroidTest
```

- `:app:testDevDebugUnitTest`: 빠른 JVM 회귀 확인
- `:app:connectedDevDebugAndroidTest`: device/emulator 기반 Android 통합 검증
- 로컬 prerequisite 부족으로 instrumentation을 못 돌리면, 막힌 이유를 PR 본문에 명시하고 아래 수동 QA evidence를 남긴다.

### 지원 문의 fallback baseline

issue #695 계열 PR은 메뉴의 문의/광고 제거 관심도 문의 진입점에서 이메일 앱이 없을 때 지원 주소와 privacy-safe 진단 템플릿이 클립보드에 남는지 확인한다. 진단 템플릿에는 앱 version, Android OS/SDK, device model만 포함하고 앱 package list, 루틴 이름/ID, 잠금 기록, 긴급해제 reason 원문은 넣지 않는다.

자동 baseline:

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.menu.SupportContactFallbackTest'
python3 -m unittest scripts.tests.test_locale_string_parity -v
```

수동 QA evidence:
- 이메일 앱이 설치된 기기: 메뉴 `Contact Us`가 기존 메일 작성 화면을 열고 제목/진단 템플릿이 채워진다.
- 이메일 앱이 없는 기기/프로필: Toast가 fallback 복사를 안내하고 클립보드에 지원 이메일 + version/OS/device model 진단 템플릿이 들어간다.
- 클립보드/analytics 확인: `support_contact_fallback_used(surface=menu, fallback_type=clipboard)`만 기록되고 진단 payload 원문이나 사용자 민감 정보는 analytics에 기록되지 않는다.
- 모든 shipped `values*/strings.xml`에 `support_contact_fallback_copied` string parity가 유지된다.

### 기능성 control stateDescription locale baseline

issue #570/#628 계열 PR은 TalkBack이 읽는 상태 문구와 요일/날짜 라벨도 화면 locale을 따라야 한다. 기능성 control의 `stateDescription`에는 영어 리터럴(`"On"`, `"Off"`, `"Selected"`, `"Not selected"`, `"Today"`)을 직접 넣지 말고 `stringResource(R.string...)` 기반 리소스를 사용한다. Android 13+ per-app language를 켠 경우 `Locale.getDefault()`가 시스템 언어를 가리킬 수 있으므로, 루틴 요일/잠금 기록 주간 캘린더는 `LocalConfiguration.current.locales[0]` 또는 동등한 앱 configuration locale을 사용한다.

자동 gate / baseline:

- Android CI `Fast verification`와 Release QA `Full release QA`의 `Run static policy unit tests` step이 `scripts.tests.test_compose_icon_button_accessibility`와 `scripts.tests.test_locale_string_parity`를 자동 실행한다. 즉 PR에서는 접근성 icon-only/stateDescription 정적 정책과 shipped locale key/placeholder parity가 CI gate로 먼저 막혀야 한다.
- 아래 명령은 로컬에서 같은 정책을 재현하거나 수동 QA 전에 빠르게 선검증할 때 사용한다.

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_compose_icon_button_accessibility scripts.tests.test_locale_string_parity -v
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.lockhistory.LockHistoryLocaleFormatterTest'
./gradlew --console=plain :app:lintProdRelease
```

수동 QA evidence:
- 메뉴 설정 토글: TalkBack 상태가 현재 앱 언어로 `켜짐/꺼짐` 또는 해당 locale 번역으로 읽힌다.
- 잠금 기록 주/월 탭: TalkBack 상태가 현재 앱 언어로 `선택됨/선택되지 않음` 또는 해당 locale 번역으로 읽힌다.
- 잠금 기록 주간 캘린더: 앱 언어를 한국어/일본어 등으로 바꾸고 시스템 언어를 영어로 둬도 요일/월 라벨과 `오늘/선택됨/선택되지 않음` 상태가 앱 언어 기준으로 읽힌다.
- 루틴 반복 요일 버튼: 앱 언어와 시스템 언어가 다를 때도 요일 라벨이 앱 언어 기준으로 표시된다.
- 새 stateDescription string key를 추가하면 모든 shipped `values*/strings.xml`에 parity가 맞아야 한다.

### 접근성 권한 copy / Android permission wording baseline

issue #642 계열 PR은 `docs/ACCESSIBILITY_PERMISSION_COPY_CONTRACT.md`를 source of truth로 보고, 온보딩 접근성 권한 화면이 Android Accessibility Service 맥락을 정확히 설명하는지 확인한다. `Screen Time permission` / `스크린타임 권한` / `화면 시간 권한`처럼 Android 권한명이 아닌 표현은 접근성 권한 copy에 재유입되면 안 된다.

자동 baseline:

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_accessibility_permission_copy_contract -v
python3 -m unittest scripts.tests.test_locale_string_parity -v
./gradlew --console=plain :app:lintProdRelease
```

수동 QA evidence:
- Locale(s): ko / en / es / ja / other changed locale
- 온보딩 접근성 권한 제목이 현재 locale에서 Android Accessibility/접근성 권한으로 읽힌다.
- 설명이 “사용자가 선택한 앱을 Stopit이 차단하기 위한 권한”으로 이해된다.
- `Screen Time permission` 또는 같은 의미의 권한명 오해 표현이 보이지 않는다.
- TalkBack에서도 권한명과 설명이 같은 의미로 전달된다.
- Play Console Accessibility declaration의 사용 목적과 충돌하지 않는다.

### Long countdown locale QA evidence

issue #596 계열 PR은 Lock 화면의 24시간 이상 countdown day prefix가 Kotlin hardcoded Korean suffix가 아니라 locale/plural resource를 따라야 한다. 1일/2일 이상 경계와 24시간 미만 경계를 함께 확인해서 장기 잠금/목표 잠금 화면이 모든 shipped locale에서 자연스럽게 보이는지 기록한다.

자동 baseline:

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_countdown_day_locale_contract -v
./gradlew --console=plain :app:testDevDebugUnitTest
./gradlew --console=plain :app:assembleProdDebug
```

수동 QA evidence:
- Locale(s): ko / en / ja / zh / other changed locale
- 24시간 미만 countdown: day prefix 없이 `HH:mm:ss` 또는 `mm:ss` + remaining copy가 보인다.
- 1 day / 2 days 이상 countdown: 현재 locale의 day unit/plural이 보이고 한국어 `일` hardcoded suffix가 다른 locale에 노출되지 않는다.
- TalkBack: countdown 숫자와 남은 시간 문맥이 화면 locale과 충돌하지 않는다.
- #464 차단 화면 copy/action hierarchy와 visual hierarchy가 충돌하지 않는다.

### Lock countdown expired-display QA evidence

issue #679 계열 PR은 Lock 화면 countdown이 deadline 도달 이후 `-1`, `00:-1`, `-1:-01` 같은 음수 표시로 흐르지 않고 `00:00`에 고정되는지 확인한다. ViewModel의 `MoveToHome` side effect가 지연되거나 Activity/Compose가 resume되더라도 화면 표시 helper와 formatter가 모두 음수 입력을 0으로 clamp해야 한다.

자동 baseline:

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.util.CountdownFormatTest' \
  --tests 'com.uiery.keep.feature.lock.component.CountDownContentTest'
```

수동 QA evidence:
- 이미 지난 `lockTime` 또는 만료 직전 Lock 화면 resume 상태에서도 countdown 숫자는 `00:00` 아래로 내려가지 않는다.
- 1초 남은 상태는 `00:01`로 보이고 다음 tick에서 `00:00`으로 고정된다.
- 24시간 이상 countdown day prefix QA와 혼동하지 않는다. day prefix는 locale/plural 계약이고, expired-display 계약은 음수 방지/0 clamp 계약이다.
- `MoveToHome` 전환이 늦더라도 사용자는 음수 countdown을 보지 않는다.

### 홈 타이머 CTA duration baseline

issue #187 계열 PR에서는 홈 타이머 바텀시트가 실제 `현재 시각 -> 목표 시각` 차이와 같은 값을 CTA에 표시하는지 JVM 계약 테스트를 기본 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.home.component.TimerTimeContractTest'
```

검증 범위:
- `10:50 -> 11:10`처럼 시/분 경계가 걸린 20분 타이머가 `1시간 20분`으로 과장되지 않는다.
- `23:50 -> 00:10`처럼 자정을 넘는 타이머가 20분으로 계산된다.
- 목표 시각이 현재 시각과 같으면 버튼 활성화에 쓰는 duration이 `0시간 0분`으로 남는다.
- 12시간제 picker는 `0시` 대신 `12시`를 표시하고, AM/PM `12` 선택을 각각 `00:xx` / `12:xx`로 변환한다.

수동 QA가 필요하면 홈 → 시간 설정 → 타이머 탭에서 위 경계 시각을 맞춘 뒤 CTA 문구와 실제 잠금 종료 시각이 같은 duration을 가리키는지 기록한다.

### Home status / CTA hierarchy QA evidence

issue #463 계열 PR은 `docs/HOME_STATUS_CTA_STRUCTURE.md`를 source of truth로 보고, 홈 화면이 텍스트만으로도 현재 상태와 다음 행동을 구분하는지 확인한다. 이 docs-lane 계약 자체는 구현 완료가 아니며, Home code/resource/locale/test/visual QA가 들어가기 전에는 PR body에 `Refs #463`를 사용한다.

자동 baseline:

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_home_status_cta_structure_contract -v
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.home.HomeStatusCtaReadModelTest'
./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.home.HomeStatusCtaCardIntegrationTest
./gradlew --console=plain :app:lintProdRelease
```

수동 QA matrix:
- 꺼짐 + 선택 앱 없음: `차단 앱 선택`이 primary action이고 Keep/타이머 성공처럼 보이지 않는다.
- 꺼짐 + 선택 앱 있음 + 첫 잠금 전: 선택 앱 수와 `지금 차단 시작` 계열 primary CTA가 가장 강하다.
- 켜짐: `N개 앱을 막고 있어요`처럼 현재 보호 상태가 텍스트로 보인다.
- 타이머 예약/실행 중: 즉시 차단과 타이머의 역할이 문구로 구분된다.
- 목표 잠금 진행 중: `GoalLockProgressCard`류 상태 표면이 Home primary status와 충돌하지 않는다.

### Routine creation CTA QA evidence

issue #455 계열 PR은 `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`를 source of truth로 본다. CTA는 onboarding/pre-first-lock이 아니라 첫 핵심 행동 이후(`post_first_core_action`) 루틴 0개 사용자에게만 Home 보조 slot으로 노출되어야 하며, 클릭하면 Routine 생성 흐름으로 이동해야 한다.

자동 baseline:

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.home.HomeStatusCtaReadModelTest' \
  --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest' \
  --tests 'com.uiery.keep.analytics.RoutineCreationCtaAnalyticsTest'
./gradlew --console=plain :app:lintProdRelease
```

수동 QA matrix:
- 루틴 0개 + 선택 앱 있음 + 첫 핵심 행동 완료: Home 보조 CTA가 보이고 문구가 처벌/감시가 아니라 반복 자동화 도움으로 읽힌다.
- pre-first-lock / 첫 핵심 행동 전: 루틴 생성 CTA가 보이지 않는다.
- 루틴 1개 이상: 루틴 생성 CTA가 보이지 않는다.
- CTA 클릭: Routine 화면/생성 흐름으로 이동한다.
- analytics debug/log evidence가 가능하면 `routine_creation_cta_shown` / `routine_creation_cta_clicked` payload에 `surface=home_secondary`, `activation_stage=post_first_core_action`, `has_routine=false`, `cta_variant=soft_default`만 포함되는지 확인한다.

```md
## Home status/CTA QA evidence
- Issue: #463
- Build / variant:
- Device / Android version:
- Theme: light / dark
- User state:
  - selected app count: 0 / 1 / many
  - first lock recorded: yes / no
  - keep mode: on / off
  - timer: none / scheduled / running
  - goal lock: none / active / completed
- Text-only state clarity: pass / fail
- Primary CTA is visually strongest: pass / fail
- App selection/change entry visible: pass / fail
- Timer vs immediate lock copy separated: pass / fail
- Primary color not overused: pass / fail
- Commands:
  - `python3 -m unittest scripts.tests.test_home_status_cta_structure_contract -v`
  - `<focused Home test or :app:testDevDebugUnitTest>`
- Screenshot/video evidence:
- Notes:
```

### KDS modal bottom sheet edge-to-edge visual QA

issue #325 계열 PR은 `KeepModalBottomSheet`에서 deprecated Accompanist `SystemUiController` 의존성을 제거한 뒤에도 edge-to-edge 표시가 실제 기기에서 깨지지 않는지 별도 시각 증거를 남긴다. JVM/CI 계약은 재유입을 막지만, navigation bar / status bar의 색·scrim·inset 처리는 device/OEM 조합에서 screenshot evidence로 한 번 더 확인해야 한다.

자동 baseline:

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_kds_dependency_catalog_contract -v
./gradlew -q help --task :app:assembleProdDebug
```

수동 visual QA matrix:
- Variant: `devDebug` 또는 release 후보에서 요구하는 prod-like artifact를 명시한다.
- Device/OS: Android version, OEM skin, light/dark mode를 기록한다.
- Navigation mode:
  - gesture navigation
  - 3-button navigation
- Screen entry points:
  - 홈 앱 선택 bottom sheet
  - 홈 타이머/시간 설정 bottom sheet
  - 루틴 생성/수정 bottom sheet
  - 긴급해제 대상/설정 bottom sheet가 변경 범위와 관련 있으면 함께 확인한다.
- Visual checks:
  - sheet scrim이 system bar 뒤까지 자연스럽게 이어진다.
  - navigation bar 영역이 과도하게 투명/검정/흰색으로 튀지 않는다.
  - status bar icon contrast가 light/dark mode에서 읽힌다.
  - sheet content와 CTA가 gesture handle 또는 3-button navigation bar에 가려지지 않는다.
  - IME가 올라오는 입력형 sheet에서는 하단 CTA가 키보드/시스템 bar와 겹치지 않는다.

```md
## KDS modal bottom sheet visual QA evidence
- Issue: issue #325
- Build / variant:
- Device / Android version / OEM:
- Theme: light / dark
- Navigation mode: gesture navigation / 3-button navigation
- Entry point:
- Commands:
  - `python3 -m unittest scripts.tests.test_kds_dependency_catalog_contract -v`
  - `./gradlew -q help --task :app:assembleProdDebug`
- Screenshot evidence:
  - Home app selection bottom sheet:
  - Home timer bottom sheet:
  - Routine bottom sheet:
- Observed navigation bar:
- Observed status bar:
- Insets/CTA overlap:
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #325는 repo-internal dependency/test 계약이 완료됐더라도 manual visual QA 경계가 남은 상태로 본다.

### StopIt user-facing brand copy QA evidence

issue #404 계열 PR은 사용자 노출 문자열에서 legacy `Keep` 브랜드가 다시 보이지 않는지 자동 계약과 실제 화면 evidence를 함께 남긴다. 리소스 key 이름(`keep_*`)은 내부 legacy identifier로 남을 수 있지만, 화면에 보이는 copy는 StopIt/스탑잇 기준으로 통일한다.

### Locale string quality / high-traffic Home status copy

Issue: #729. `docs/LOCALE_STRING_QUALITY.md`는 shipped locale의 브랜드 표기, 한국어 오타, Home high-traffic 상태 설명 fallback guard의 source of truth다. Home 상태 copy는 권한/잠금 신뢰와 연결되므로 `home_status_*_description`을 non-default locale에서 default English 그대로 두지 않는다.

자동 baseline:

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_locale_string_quality_contract -v
python3 -m unittest scripts.tests.test_locale_string_parity scripts.tests.test_user_facing_brand_strings scripts.tests.test_korean_brand_copy_contract -v
./gradlew -q help --task :app:assembleProdDebug
```

검증 범위:
- `home_status_no_selected_apps_description`, `home_status_first_lock_ready_description`, `home_status_ready_description`, `home_status_keep_active_description`가 shipped `values-*`에서 default English 복사본으로 남지 않는다.
- `values-ko/strings.xml`에는 확인된 오타 `함꼐`, `잠궈줘요`가 재유입되지 않는다.
- 한국어 사용자 노출 브랜드는 `스탑잇`, 영문/비한국어 제품명은 `StopIt`을 기본으로 한다.
- 정책 민감 copy의 의미(최소 1개 앱 선택, 첫 실제 차단 확인, 지금 차단/앱 변경/타이머, 보호 활성 상태)는 번역 후에도 보존한다.

자동 baseline:

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_user_facing_brand_strings -v
./gradlew -q help --task :app:assembleProdDebug
```

검증 범위:
- `scripts.tests.test_user_facing_brand_strings`는 `app/src/main/res/values*/strings.xml`의 user-visible string value에 legacy `Keep` 브랜드가 남아 있지 않은지 확인한다.
- `notification_permission_request`는 알림 권한 요청에서 StopIt/스탑잇 브랜드만 보여야 한다.
- `block_screen_first_core_action_feedback`는 첫 차단 성공 피드백에서 StopIt/스탑잇 브랜드만 보여야 한다.
- 신규 사용자-facing string이 의도적으로 `Keep`을 제품명/모드명으로 보여줘야 한다면 allowlist에 이유를 남겨야 하며, 기본값은 `legacy Keep brand absent`다.

수동 QA evidence template:

```md
## StopIt user-facing brand copy QA evidence
- Issue: #404
- Build / variant:
- Device / Android version / OEM:
- Locale(s): default / ko / ja / zh / other changed locale
- Commands:
  - `python3 -m unittest scripts.tests.test_user_facing_brand_strings -v`
  - `./gradlew -q help --task :app:assembleProdDebug`
- Screens / copy checked:
  - notification_permission_request:
    - screenshot/evidence:
    - legacy Keep brand absent: pass / fail
  - block_screen_first_core_action_feedback:
    - screenshot/evidence:
    - legacy Keep brand absent: pass / fail
  - first-lock/home guidance if touched:
    - screenshot/evidence:
    - legacy Keep brand absent: pass / fail
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #404는 repo-internal string cleanup과 static regression이 완료됐더라도 실제 권한 요청/첫 차단 성공 화면의 device/manual QA 경계가 남은 상태로 본다.

### Block screen copy/action hierarchy QA baseline

Source of truth: `docs/BLOCK_SCREEN_COPY_HIERARCHY.md`
Issue: #464

Use this after PR #487(`8fb1911c`) + PR #588(`025f9326`) or a later release candidate is installed. PR #487 changed `BlockScreen.kt`, `EmergencyUnlockActionUiPolicy`, and `block_screen_*` / `emergency_unlock_*` resources on `develop`; PR #588 added the repeatable `BlockScreenContentIntegrationTest` Compose runtime baseline for copy area, emergency unlock helper/disabled reason, primary CTA, and repeated Back blocking. This checklist now collects the remaining release-candidate screenshot/TalkBack evidence and release/readback proof instead of treating the UI copy or automated UI regression as unimplemented.

```md
## Block screen copy/action hierarchy QA evidence
- Issue: #464
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / changed locales:
- Entry path:
  - manual Keep block / timer block / routine block / goal lock block:
- Commands:
  - `python3 -m unittest scripts.tests.test_block_screen_copy_hierarchy_contract -v`
  - `./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.BlockScreenContentIntegrationTest`
  - `./gradlew --console=plain :app:lintProdRelease`
  - `./gradlew --console=plain :app:assembleProdDebug`
- Automated UI regression:
  - `BlockScreenContentIntegrationTest` renders the copy area, secondary emergency-unlock action/helper, disabled reason, and primary return CTA without requiring a full AccessibilityService foreground transition.
  - `BlockScreenContentIntegrationTest#repeatedSystemBackDoesNotDismissTheProtectionScreen` verifies repeated system Back input is consumed and does not invoke the allowed close path.
- Normal blocked state:
  - title/message coaching tone: pass / fail
  - primary CTA means return to previous work: pass / fail
  - Android system Back 1회/연타가 차단 화면을 dismiss하지 않는다: pass / fail
  - banner ad does not outrank CTA/emergency status: pass / fail
- First core action state:
  - first success feedback appears once: pass / fail
  - repeated block does not repeat first-success copy: pass / fail
- Emergency unlock available:
  - remaining count visible: pass / fail
  - action is secondary, not hidden: pass / fail
- Emergency unlock disabled/limit reached:
  - disabled reason understandable: pass / fail
  - color-only state avoided: pass / fail
- Accessibility/TalkBack:
  - blocked app and main action meaning understandable: pass / fail
- Decision: pass / fail / needs follow-up
- Notes:
```

### Emergency unlock flow copy/step QA baseline

Source of truth: `docs/EMERGENCY_UNLOCK_FLOW_COPY.md`
Issue: #467

Use this after PR #517(`572eb559`) + PR #575(`1a7c677`) + PR #593(`79fdee8`) + PR #604(`3e97f548`) + PR #675(`d2fab054`) or a later release candidate is installed. PR #517 already changed `EmergencyUnlockBottomSheetContent.kt`, `EmergencyUnlockBottomSheetState`, and `emergency_unlock_*` resources on `develop`; PR #575 added the repeatable Compose UI baseline for reason-required ON/OFF. PR #593 added the focused countdown TalkBack content-description baseline so the waiting copy, remaining seconds, and cancel affordance are exposed together in Compose UI tests. PR #604 added the selected reason reflection helper baseline while preserving existing reason enum payload keys. PR #675 added visible reason/app/duration step intent copy under the progress indicator while preserving the countdown TalkBack baseline. This checklist now collects the remaining device/screenshot/TalkBack evidence and release inclusion proof instead of treating the UI copy, automatic flow coverage, countdown accessibility coverage, reflection helper, or step purpose copy as unimplemented.

```md
## Emergency unlock flow copy QA evidence
- Issue: #467
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / changed locales:
- Settings state:
  - reason required: on / off
  - daily limit / remaining:
  - duration options:
- Reason required ON:
  - short reason labels scan quickly: pass / fail
  - step purpose explains why this exception is needed before app selection: pass / fail
  - selected reason reflection helper reinforces intentional use without changing enum key: pass / fail
  - disabled Next explains missing reason/custom reason: pass / fail
  - selected reason maps to existing enum key: pass / fail
- Reason required OFF:
  - app selection starts naturally without reason step: pass / fail
  - helper copy still limits scope to needed apps only: pass / fail
  - app/duration step purpose keeps the exception bounded to needed apps and short time: pass / fail
- App selection/duration/countdown:
  - selected apps are explicit and zero-selection helper is visible: pass / fail
  - duration options are clear and bounded: pass / fail
  - countdown copy feels like a short reconsideration window, not punishment: pass / fail
  - countdown TalkBack includes waiting copy, remaining seconds, and cancel affordance together: pass / fail
  - cancel path remains visible: pass / fail
- Analytics/privacy:
  - `emergency_unlock_completed.reason` keeps enum key, not display label/custom text: pass / fail
  - no app name/package/custom reason/raw history added to analytics: pass / fail
- Accessibility/TalkBack:
  - reason/app/duration selection and disabled helper are understandable: pass / fail
  - countdown reads as one reconsideration state with remaining seconds and cancel option: pass / fail
- Verification:
  - PR #517 merge commit included in tested build: yes / no / unknown
  - PR #575 UI QA baseline included in tested build: yes / no / unknown
  - PR #593 countdown TalkBack baseline included in tested build: yes / no / unknown
  - PR #604 selected reason reflection helper baseline included in tested build: yes / no / unknown
  - PR #675 step purpose copy baseline included in tested build: yes / no / unknown
  - `python3 -m unittest scripts.tests.test_emergency_unlock_flow_copy_contract -v`
  - `./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContentIntegrationTest`
  - `./gradlew --console=plain :app:lintProdRelease`
- Decision: pass / fail / needs follow-up
```

### Emergency unlock settings analytics QA baseline

Source of truth: `docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md`
Issue: #694

Use this after the Android analytics wiring PR for #694 or a later release candidate is installed. The Android wiring is repo-internal, but until the commit is included in a release/tag/Play deploy and GA4 Admin dimensions are registered, do not interpret missing `emergency_unlock_settings_changed` / `emergency_unlock_manual_reset_requested` rows as adoption absence.

```md
## Emergency unlock settings analytics QA evidence
- Issue: #694
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / changed locales:
- Release ancestry:
  - #694 Android analytics wiring PR included in tested build: yes / no / unknown
  - SemVer tag / Play track:
- Setting changes exercised:
  - enabled ON/OFF: pass / fail / n/a
  - daily limit bucket (`1`, `2`, `3`, `4_plus`): pass / fail / n/a
  - duration option count bucket (`0`, `1`, `2_3`, `4_plus`): pass / fail / n/a
  - reason required ON/OFF: pass / fail / n/a
  - refill mode daily/manual: pass / fail / n/a
  - manual reset request: pass / fail / n/a
- Analytics payload contract:
  - `setting_name` uses only `enabled`, `daily_limit`, `duration_options`, `reason_required`, `refill_mode`: pass / fail
  - `value_bucket`, `duration_count_bucket`, `remaining_unlocks_bucket`, `refill_mode`, `source=menu` use documented enum/bucket values: pass / fail
  - no custom reason, display label/custom text, app package/name/list, raw timestamp/history, `manualResetAtMillis`, or full settings snapshot is sent: pass / fail
- GA4/Admin:
  - `customEvent:setting_name`: registered / missing / unknown
  - `customEvent:value_bucket`: registered / missing / unknown
  - `customEvent:refill_mode`: registered / missing / unknown
  - `customEvent:duration_count_bucket`: registered / missing / unknown
  - `customEvent:remaining_unlocks_bucket`: registered / missing / unknown
  - `customEvent:reset_result`: registered / missing / unknown / not used
- Verification:
  - `python3 -m unittest scripts.tests.test_emergency_unlock_settings_analytics_contract -v`
  - Android focused analytics tests, if present:
  - GA4 metadata/runReport evidence URL or command output:
- Decision: pass / fail / needs follow-up
```

### LockHistory 성과 리포트 QA baseline

issue #465 계열 구현 PR은 `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`를 source of truth로 삼고, `LockHistory`가 단순 로그가 아니라 긍정적인 성과 리포트로 읽히는지 자동/수동 증거를 함께 남긴다. 이 기능은 #211 공유 CTA와 같은 화면을 쓰더라도 1차 목표가 외부 공유가 아니라 개인 성과 해석과 재방문 동기 강화다.

자동 baseline:

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest \\
  --tests '*LockHistory*Performance*' \\
  --tests '*LockHistoryViewModel*'
./gradlew --console=plain :app:connectedDevDebugAndroidTest \\
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lockhistory.component.LockHistoryPerformanceReportAccessibilityTest
python3 -m unittest scripts.tests.test_lock_history_performance_report_contract -v
```

검증 범위:
- 기록 없음은 `empty` 상태로 시작 격려/다음 행동 안내를 보여주고 실패·중독·질책 copy를 쓰지 않는다.
- 세션 1개 또는 짧은 duration은 `low_data` 상태로 작은 성공을 인정한다.
- 기록 있음은 `has_history` 상태로 주/월 기간에 맞는 성취형 headline을 보여준다.
- top apps heading/supporting copy는 `위험 앱`이 아니라 `막아낸 성과`로 읽힌다.
- Top apps count는 앱이 차단된 session 수 기준이다. 같은 session 안에 duplicate lockedApps entry가 있어도 per-session dedupe로 앱별 한 번만 세고, 중복 원문 리스트를 성과로 과대계상하지 않는다.
- summary card와 top apps card는 TalkBack에서 성과형 headline/supporting copy가 하나의 content description으로 전달되는지 focused Compose instrumentation으로 확인한다. PR #579 이후 Top apps card는 실제 표시되는 top app rank/app label/block count/duration까지 같은 content description에 포함해야 한다.
- 새 analytics를 추가할 경우 `period_type`, `report_state`, `session_count_bucket`, `duration_minutes_bucket`, `top_apps_count_bucket` 같은 enum/bucket만 전송하고 앱 이름/package/raw session/raw timestamp/raw duration은 전송하지 않는다.

수동 QA evidence template:

```md
## LockHistory performance report QA evidence
- Issue: #465
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / other changed locale
- Commands:
  - `./gradlew --console=plain :app:testDevDebugUnitTest --tests '*LockHistory*Performance*' --tests '*LockHistoryViewModel*'`
  - `./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lockhistory.component.LockHistoryPerformanceReportAccessibilityTest`
  - `python3 -m unittest scripts.tests.test_lock_history_performance_report_contract -v`
- Empty state:
  - copy:
  - shame/friction wording absent: pass / fail
- Low-data state:
  - seed/session condition:
  - copy:
- Has-history weekly/monthly state:
  - summary headline:
  - week/month period correct: pass / fail
- Top apps section:
  - positive framing: pass / fail
  - per-session dedupe for duplicate lockedApps entry: pass / fail
  - app package/raw history absent from analytics spot-check: pass / fail
- TalkBack summary/top apps meaning:
  - focused contentDescription regression passed: pass / fail
  - actual screen reader/screenshot spot-check: pass / fail / not collected
- #211 share CTA remains optional and not pressured: pass / fail / not applicable
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #465는 repo-internal 문서/계약이 완료됐더라도 실제 UI copy, locale/TalkBack, analytics payload spot-check, release 후 14일·30일 성과 판단 경계가 남은 상태로 본다.

### 집중 요약 공유 localization QA baseline

issue #597 계열 구현 PR은 `docs/FOCUS_SUMMARY_SHARE_MVP.md`를 source of truth로 삼고, 이미 들어간 #211 공유 CTA/analytics/privacy guardrail을 깨지 않으면서 share payload body와 duration text를 locale resource/template 계약으로 옮겼는지 증거를 남긴다. CTA/share sheet title이 Android string resource에 있다는 사실만으로 payload body locale-ready를 완료 처리하지 않는다.

자동 baseline:

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.lockhistory.FocusSummarySharePayloadTest' \
  --tests 'com.uiery.keep.feature.lockhistory.LockHistoryViewModelShareTest'
python3 -m unittest scripts.tests.test_focus_summary_share_contract -v
```

검증 범위:
- payload body locale resource/template은 session count, duration text, Play Store URL placeholder만 입력으로 받는다.
- duration grammar는 Kotlin hardcoded `시간`/`분` 조합이 아니라 resource/plural-backed contract를 따른다.
- share payload에는 app/package/topApps/raw session/raw timestamp absent가 유지된다.
- `focus_summary_share_tapped`, `focus_summary_share_sheet_opened`, `focus_summary_share_failed` 이벤트와 `period_type`, `session_count_bucket`, `duration_minutes_bucket`, `reason` bucket 계약은 바꾸지 않는다.
- `FocusSummarySharePayloadTest`는 Korean literal 하나만 canonical으로 고정하지 않고 provider/resource injection과 privacy guardrail을 검증한다.

수동 QA evidence template:

```md
## Focus summary share localization QA evidence
- Issue: #597
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / ja / changed locale
- Commands:
  - `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.lockhistory.FocusSummarySharePayloadTest' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryViewModelShareTest'`
  - `python3 -m unittest scripts.tests.test_focus_summary_share_contract -v`
- Payload body locale resource/template:
  - body text follows current locale: pass / fail
  - duration grammar follows current locale: pass / fail
  - Play Store URL remains included: pass / fail
- Privacy guardrail:
  - app/package/topApps/raw session/raw timestamp absent: pass / fail
  - shame/comparison wording absent: pass / fail
- Analytics compatibility:
  - `focus_summary_share_*` event names unchanged: pass / fail
  - no raw rendered text/raw duration string sent to analytics: pass / fail
- Decision: pass / fail / needs follow-up
```

이 증거가 없으면 #597는 docs/ops/static-contract가 정리됐더라도 실제 runtime locale 전환, locale parity, formatter/privacy tests, release 후 spot-check 경계가 남은 상태로 본다.

### 루틴 카드 상태/반복/다음 실행 visual QA evidence

issue #466 계열 PR은 루틴 카드가 단순 목록이 아니라 반복 스케줄 카드로 읽히는지 자동 read model 계약과 실제 화면 evidence를 함께 남긴다. Room 저장 구조, `RoutineScheduler`, exact alarm 정책은 변경 범위가 아니며, 카드 표시/계산/카피만 검증한다.

자동 baseline:

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.routine.*'
./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.component.RoutineListContentIntegrationTest#routineCardsRenderStatusRepeatAndNextRunLabels
./gradlew --console=plain :app:assembleProdDebug
```

검증 범위:
- `RoutineCardReadModelTest`는 활성 루틴의 반복 요일과 다음 실행 시각, 실행 중 루틴의 다음 회차 계산, 비활성 루틴의 `nextRunAt = null`, overnight 루틴을 고정한다.
- `RoutineListContentIntegrationTest#routineCardsRenderStatusRepeatAndNextRunLabels`는 실제 Compose 렌더링에서 status badge / repeat days / next run 라벨이 함께 보이는지 고정한다.
- 루틴 카드에는 `실행중` / `활성` / `비활성` 텍스트 배지가 색상과 별도로 보여야 한다.
- 반복 요일 line과 `다음 실행: ...` line이 카드에서 읽혀야 하며, 비활성 루틴은 다음 실행을 표시하지 않는다.
- 공유/편집/토글 동작은 기존 콜백을 유지한다. 실행 중 또는 routine change-lock 상태의 disabled 처리도 기존 정책을 유지한다.

수동 visual QA evidence template:

```md
## Routine card schedule visibility QA evidence
- Issue: #466
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / default / other changed locale
- Commands:
  - `./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.routine.*'`
  - `./gradlew --console=plain :app:assembleProdDebug`
- Screens checked:
  - enabled routine card: status badge / repeat days / next run visible
  - disabled routine card: disabled badge visible / no misleading next run
  - running routine card: running badge emphasized / next scheduled occurrence visible
  - routine change-lock card if applicable: protection badge still visible, edit/toggle disabled
- Existing actions:
  - edit detail opens for non-blocked routine: pass / fail
  - share button works for non-blocked routine: pass / fail
  - switch toggles enabled state for non-blocked routine: pass / fail
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #466은 repo-internal read model/UI 계약이 완료됐더라도 실제 기기 시각 확인 경계가 남은 상태로 본다.

### 루틴 템플릿 공유 privacy-safe QA baseline

issue #407 계열 구현 PR은 `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`를 source of truth로 삼고, Android share sheet 텍스트 공유가 민감 정보를 노출하지 않는지 자동/수동 증거를 함께 남긴다. 이 기능은 성장 루프 후보지만, 앱 사용 문제나 차단 앱 목록을 외부에 드러내면 제품 신뢰를 해칠 수 있으므로 privacy guardrail을 release evidence와 같은 수준으로 기록한다. #778 계열 현지화 PR은 share sheet chooser title resource화만으로 payload body localization이 완료됐다고 보지 않고, 실제 외부 공유 본문·category/repeat/time-window/duration label이 resource-backed template/provider를 따르는지 별도 증거를 남긴다.

자동 baseline:

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \\
  --tests 'com.uiery.keep.feature.routine.RoutineTemplateSharePayloadTest' \\
  --tests 'com.uiery.keep.feature.routine.RoutineViewModelTemplateShareTest' \\
  --tests 'com.uiery.keep.analytics.RoutineTemplateShareAnalyticsTest'
python3 -m unittest scripts.tests.test_routine_template_share_contract -v
```

검증 범위:
- share payload에는 category/repeat/time window/Play Store 링크만 포함되고 `lockApplications`, package name, 앱 이름, raw session history, raw usage time은 포함되지 않는다.
- 루틴 이름은 기본 제외이며, opt-in variant가 있더라도 analytics에는 원문 대신 `routine_name_included=true/false`만 남긴다.
- `routine_template_share_tapped`, `routine_template_share_sheet_opened`, `routine_template_share_failed`는 `template_category`, `repeat_days_bucket`, `time_window_bucket`, `routine_name_included` 같은 enum/bucket/boolean 파라미터만 사용한다.
- invalid routine에서는 CTA가 숨겨지거나 payload 생성이 실패하고, 실패 reason은 `activity_not_found` / `invalid_template` 같은 enum으로만 기록된다.
- #778 현지화 PR에서는 payload title/body, category/repeat/time-window label, duration grammar가 Android string/plural resource 또는 resource-backed provider에서 나오며, `RoutineTemplateSharePayload.kt`의 한국어 literal은 canonical runtime source가 아니다.
- #778은 analytics schema 변경이 아니므로 raw rendered share text, raw duration string, locale-specific body를 GA4 payload/custom dimension으로 추가하지 않는다.

수동 QA evidence template:

```md
## Routine template share QA evidence
- Issue: #407 / #778 when payload localization is touched
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / ja / zh / other changed locale
- Entry point: routine list / routine detail
- Commands:
  - `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.routine.RoutineTemplateSharePayloadTest' --tests 'com.uiery.keep.feature.routine.RoutineViewModelTemplateShareTest' --tests 'com.uiery.keep.analytics.RoutineTemplateShareAnalyticsTest'`
  - `python3 -m unittest scripts.tests.test_routine_template_share_contract -v`
- Shared text preview:
  - payload body follows current locale, not only chooser title:
  - category / repeat / time window / duration grammar present:
  - Play Store link present:
  - app names / package names / lockApplications absent:
  - raw history / raw usage time absent:
- Share sheet behavior:
  - share target available: pass / fail
  - no target / failed intent fallback: pass / fail
- Accessibility label:
- Analytics payload spot-check:
  - enum/bucket/boolean only; no raw rendered text / raw duration string / locale-specific body:
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #407은 문서 계약이 있더라도 구현/QA 경계가 남은 상태로 본다. GA4 Admin 등록과 14일/30일 성과 판단은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`와 `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`의 외부/manual 경계를 따른다.

### 루틴 생성 CTA soft experiment QA baseline

issue #455 계열 구현 PR은 `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`를 source of truth로 삼고, 첫 차단 성공 이후 + 루틴 0개 사용자에게만 루틴 생성 CTA가 부드럽게 노출되는지 자동/수동 증거를 함께 남긴다. 이 CTA는 Routine empty state / 광고 배너 / 루틴 템플릿 공유 CTA 충돌 없음이 핵심 guardrail이며, onboarding / pre-first-lock 사용자에게 미노출되어야 한다.

자동 baseline(구현 PR에서 추가/확장할 테스트):

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.home.HomeViewModelRoutineCreationCtaTest' \
  --tests 'com.uiery.keep.analytics.RoutineCreationCtaAnalyticsTest'
python3 -m unittest scripts.tests.test_routine_creation_cta_contract -v
```

검증 범위:
- 첫 차단 성공 이후 + 루틴 0개 사용자에게만 노출된다.
- onboarding / pre-first-lock 사용자에게 미노출된다.
- 루틴 보유자(`has_routine=true` 또는 로컬 루틴 목록 1개 이상)에게 미노출된다.
- `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed`는 `surface`, `activation_stage`, `has_routine`, `cta_variant` 같은 enum/boolean 파라미터만 사용한다.
- 앱 이름/package/lockApplications/raw session history/raw timestamp/routine_id가 CTA payload에 포함되지 않는다.
- Routine empty state / 광고 배너 / 루틴 템플릿 공유 CTA 충돌 없음이 화면 QA에서 확인된다.

수동 QA evidence template:

```md
## Routine creation CTA QA evidence
- Issue: #455
- Build / variant:
- Device / Android version / OEM:
- Entry point: home / lock_history / post_block_success
- Commands:
  - `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.home.HomeViewModelRoutineCreationCtaTest' --tests 'com.uiery.keep.analytics.RoutineCreationCtaAnalyticsTest'`
  - `python3 -m unittest scripts.tests.test_routine_creation_cta_contract -v`
- Eligibility:
  - first_core_action_completed or app_block_intercepted already happened: pass / fail
  - routines_count/local routine list is 0: pass / fail
  - onboarding / pre-first-lock user hidden: pass / fail
  - routine owner hidden: pass / fail
- UI conflict checks:
  - Routine empty state / 광고 배너 / 루틴 템플릿 공유 CTA 충돌 없음: pass / fail
  - CTA tone is soft/non-punitive: pass / fail
- Analytics payload spot-check:
  - routine_creation_cta_shown:
  - routine_creation_cta_clicked:
  - routine_creation_cta_dismissed:
  - app names / package names / lockApplications / raw session history absent:
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #455는 문서 계약이 있더라도 구현/QA 경계가 남은 상태로 본다. GA4 Admin 등록, CTA 포함 release/tag/Play deploy, 14일/30일 성과 판단은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`와 `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`의 외부/manual 경계를 따른다.

### 반복 차단 기반 자동 루틴 제안 QA baseline

issue #531 계열 구현 PR은 `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`를 source of truth로 삼고, 최근 LockHistory/차단 기록에서 반복되는 시간대·요일·앱 카테고리 신호가 있을 때만 루틴 생성 prefill을 부드럽게 제안하는지 자동/수동 증거를 함께 남긴다. 이 제안은 onboarding / pre-first-lock 사용자에게 미노출되어야 하며, 기존 활성 루틴과 겹치면 미노출되고, 비난형 copy 금지와 raw app/package/history/timestamp analytics 금지가 핵심 guardrail이다.

자동 baseline(구현 PR에서 추가/확장할 테스트):

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionPolicyTest' \
  --tests 'com.uiery.keep.feature.routine.RoutineNavigationTest' \
  --tests 'com.uiery.keep.feature.routine.RoutineBottomSheetViewModelTest' \
  --tests 'com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionStoreTest' \
  --tests 'com.uiery.keep.analytics.RepeatBlockRoutineSuggestionAnalyticsTest'
python3 -m unittest scripts.tests.test_repeat_block_routine_suggestion_contract -v
```

검증 범위:
- 반복 차단 패턴이 충분하고 해당 패턴을 커버하는 활성 루틴이 없을 때만 추천된다.
- onboarding / pre-first-lock 사용자에게 미노출된다.
- 기존 활성 루틴과 겹치면 미노출된다.
- 추천은 최대 1개만 노출되고 dismiss 후 최소 7일 재노출 제한을 지킨다.
- 추천 copy는 방어 성공/도움 제안 톤이며 비난형 copy 금지다.
- `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied`는 enum/bucket 파라미터만 사용한다.
- raw app name / package / history / timestamp absent 상태가 analytics payload spot-check에서 확인된다.
- #455 일반 루틴 CTA / #407 루틴 템플릿 공유 CTA / 광고 CTA / active goal lock / emergency unlock 상태와 slot·문맥 충돌이 없다.

수동 QA evidence template:

```md
## Repeat block routine suggestion QA evidence
- Issue: #531
- Build / variant:
- Device / Android version / OEM:
- Entry point: home / post_block_success / lock_history / performance_report
- Commands:
  - `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionPolicyTest' --tests 'com.uiery.keep.feature.routine.RoutineNavigationTest' --tests 'com.uiery.keep.feature.routine.RoutineBottomSheetViewModelTest' --tests 'com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionStoreTest' --tests 'com.uiery.keep.analytics.RepeatBlockRoutineSuggestionAnalyticsTest'`
  - `python3 -m unittest scripts.tests.test_repeat_block_routine_suggestion_contract -v`
- Eligibility:
  - first_core_action_completed or app_block_intercepted already happened: pass / fail
  - repeat pattern exists: pass / fail
  - existing active routine covers same pattern: pass / fail / n/a
  - onboarding / pre-first-lock user hidden: pass / fail
  - dismiss cooldown respected: pass / fail
- Suggested pattern:
  - time_bucket:
  - day_type:
  - category_bucket:
  - repeat_count_bucket:
  - routine_coverage_state:
- UI/copy conflict checks:
  - 비난형 copy 금지: pass / fail
  - #455/#407/광고 CTA/goal lock/emergency unlock 충돌 없음: pass / fail
  - prefill is user-editable before save: pass / fail
- Analytics payload spot-check:
  - repeat_block_routine_suggestion_shown:
  - repeat_block_routine_suggestion_clicked:
  - repeat_block_routine_suggestion_dismissed:
  - repeat_block_routine_suggestion_applied:
  - raw app name / package / history / timestamp absent:
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #531은 문서 계약이 있더라도 구현/QA 경계가 남은 상태로 본다. GA4 Admin 등록, 추천 포함 release/tag/Play deploy, 14일/30일 성과 판단은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`와 `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`의 외부/manual 경계를 따른다.

### 활성 루틴 보호 UX QA baseline

issue #609 계열 PR은 실행 중인 루틴이 수정/삭제/끄기 동작을 조용히 무시하지 않고, 사용자가 왜 막혔는지 즉시 이해할 수 있는 피드백을 남기는지 확인한다. 이 계약은 루틴 실행 중 우회 방지와 비징벌적 안내를 함께 다룬다.

자동 baseline:

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.routine.RoutineViewModelActiveRoutineGuardTest' \
  --tests 'com.uiery.keep.feature.routine.RoutineBottomSheetViewModelTest'
./gradlew --console=plain :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution
python3 -m unittest scripts.tests.test_active_routine_enforcement_contract -v
./gradlew --console=plain :app:lintProdRelease
```

검증 범위:
- 실행 중인 루틴을 탭해 상세/수정 bottom sheet를 열려고 하면 `RoutineSideEffect.ShowActiveRoutineBlocked`가 발생하고 edit sheet가 열리지 않는다.
- 활성 루틴 대상 앱이 이미 foreground에 있거나 foreground로 전환될 때 `KeepAccessibilityServiceIntegrationTest#activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution`가 `block_source=routine`과 `routine_id` attribution으로 `BlockActivity` 요청을 고정한다.
- 실행 중인 루틴 삭제는 repository delete/cancel 경로로 들어가지 않고 같은 안내 side effect를 발생시킨다.
- 실행 중인 루틴 OFF 전환은 enabled 상태를 변경하지 않고 같은 안내 side effect를 발생시킨다.
- 루틴 목록 state가 잠시 stale이어도 삭제/OFF action 직전에 repository의 최신 routine을 다시 읽어, 그 사이 활성/변경잠금 상태가 된 루틴이면 delete/update/cancel/reschedule을 수행하지 않는다.
- edit sheet가 열린 뒤 루틴 시간이 시작된 경우에도 저장 직전에 Room의 최신 routine 상태를 다시 확인하고, 활성/변경잠금 상태면 `RoutineBottomSheetSideEffect.ShowActiveRoutineBlocked`만 발생하며 update/cancel/reschedule을 수행하지 않는다.
- Routine 화면은 side effect를 `routine_active_action_blocked_message` snackbar로 표시한다.
- 안내 문구는 긴급 해제를 안전한 임시 예외로 안내하되 사용자를 비난하거나 처벌하는 톤을 쓰지 않는다.
- locale release gate를 위해 `routine_active_action_blocked_message`는 모든 shipped `values*/strings.xml`에 존재해야 하며, localized resources에 default English copy가 그대로 남으면 `scripts.tests.test_active_routine_enforcement_contract`가 실패해야 한다.

수동 QA evidence template:

```md
## Active routine guard QA evidence
- Issue: #609
- Build / variant:
- Device / Android version / OEM:
- Active routine setup: repeat day / start time / end time / selected apps:
- Foreground block runtime:
  - focused command run: pass / fail
  - target app foreground observed before block: pass / fail
  - `block_source=routine` and `routine_id` attribution recorded: pass / fail
- Edit attempt while active:
  - edit sheet not opened: pass / fail
  - snackbar visible: pass / fail
- Delete attempt while active:
  - routine remains: pass / fail
  - snackbar visible: pass / fail
- OFF attempt while active:
  - routine stays enabled: pass / fail
  - snackbar visible: pass / fail
- Emergency unlock remains available for temporary exception: pass / fail / n/a
- Copy tone is non-punitive: pass / fail
- Notes:
```

이 증거가 없으면 #609는 JVM 계약과 release lint가 green이어도 실제 기기 UX 확인 경계가 남은 상태로 본다.

### 목표 잠금 runtime QA baseline

issue #417 계열 구현 PR은 `docs/GOAL_LOCK_MVP.md`를 source of truth로 삼고, 기간 기반 장기 잠금이 `all_day`와 `scheduled` 두 방식 모두에서 실제 차단/홈 상태/종료 경계를 지키는지 증거를 남긴다. 이 기능은 자기통제 강도가 높은 흐름이므로 강압적 문구, 원문 목표명 analytics, app package/app label analytics, raw 날짜 query 축을 금지한다.

자동 baseline(현재 repo foothold + 구현 PR에서 계속 확장할 테스트):

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.goallock.GoalLockPolicyTest' \
  --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.goalLockCreatedUsesSafeBucketedParamsOnly' \
  --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.goalLockEndedEarlyUsesSafeBucketedParamsOnly' \
  --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.goalLockUpdatedUsesSafeChangedFieldOnly' \
  --tests 'com.uiery.keep.feature.goallock.GoalLockPersistenceMapperTest' \
  --tests 'com.uiery.keep.feature.goallock.GoalLockCreationViewModelTest' \
  --tests 'com.uiery.keep.feature.goallock.GoalLockDetailViewModelTest' \
  --tests 'com.uiery.keep.feature.goallock.GoalLockAccessibilityDescriptionTest' \
  --tests 'com.uiery.keep.KeepAppNavigationPolicyTest' \
  --tests 'com.uiery.keep.service.GoalLockStartReevaluationPolicyTest' \
  --tests 'com.uiery.keep.feature.menu.MenuViewModelTest.isBlockingIncludesActiveAllDayGoalLock' \
  --tests 'com.uiery.keep.feature.menu.MenuViewModelTest.isBlockingIgnoresGoalLocksThatAreNotCurrentlyBlocking' \
  --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest.activeGoalLockExposesHomeProgressCardState' \
  --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest.expiredActiveGoalLockIsCompletedFromHomeCardLoadAndTrackedOnce'
python3 -m unittest scripts.tests.test_goal_lock_contract -v
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeScheduledGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#expiredGoalLockWithoutManualKeep_keepsTargetForegroundWithoutGoalLockAttribution
```

검증 범위:
- `GoalLockPolicyTest`는 기간 전/기간 내/기간 후, `all_day`, `scheduled`, normal window, overnight window, `startTime == endTime` 동일 시각 invalid, 반복 요일 없음 invalid, 시작일 당일 새벽의 전날 spillover 차단 금지, 종료일 밤에 시작된 scheduled window의 익일 새벽 spillover, 종료일 이후 자동 완료, selected app count 0 validation을 검증한다.
- `FirebaseKeepAnalyticsTest.goalLockCreatedUsesSafeBucketedParamsOnly`는 `goal_lock_created`가 enum/bucket 파라미터만 보내고 원문 목표명/app package/app label을 보내지 않는지 검증한다.
- `GoalLockPersistenceMapperTest`와 `KeepDatabaseMigrationTest`는 Room v5 `goal_lock` 저장/마이그레이션 계약을 검증한다.
- `GoalLockCreationViewModelTest`는 유효한 all-day/scheduled 저장, custom days/end date 기간 선택, 목표별 선택 앱 편집에서 `CategoryBottomSheetContent` 기반 picker 선택 replace, package trim/dedupe/remove + 0개 validation, invalid date/app/name selection 거절, 동일 시작/종료 scheduled 생성 disabled + 안내 상태, `Created(goalLockId)` side effect, `goal_lock_created` 호출을 검증한다.
- `GoalLockSelectedAppUiItemTest`는 생성 화면의 선택 앱 목록이 package raw text만 보여주지 않고 shared display metadata resolver의 앱 이름을 우선 표시하며, fallback package와 remove payload를 안정적으로 유지하는지 검증한다.
- `GoalLockAccessibilityDescriptionTest`는 생성/상세 화면 summary content description이 목표 이름, 기간/요약, 잠금 방식, 선택 앱 수, 진행 상태를 누락하지 않는지 빠른 JVM 계약으로 검증한다.
- `GoalLockCreationContentIntegrationTest`는 작은 화면 높이(compact-height)에서도 생성 플로우가 스크롤되어 하단 `목표 잠금 시작` CTA까지 접근 가능한지, 생성 summary card가 목표 이름/기간/잠금 방식/선택 앱 수를 하나의 content description으로 노출하는지 실제 Compose instrumentation으로 검증한다.
- `GoalLockDetailContentIntegrationTest`는 진행 중인 상세 화면에서 `목표 이름` 입력, `차단 앱 변경` CTA, 앱 변경 확인 copy, `변경 저장` 액션이 노출되는지, compact-height 상세 화면이 하단 `목표 잠금 종료` CTA까지 스크롤되는지, 상세 summary card가 목표 이름/요약/진행 상태를 하나의 content description으로 노출하는지 실제 Compose instrumentation surface로 검증한다.
- `KeepAppNavigationPolicyTest`는 `GoalLockCreationRoute`가 전용 top-level entry route로 등록되고 Menu의 목표 잠금 entrypoint가 생성 화면으로 연결되는 navigation 계약을 검증한다.
- `GoalLockDetailViewModelTest`, `FirebaseKeepAnalyticsTest.goalLockEndedEarlyUsesSafeBucketedParamsOnly`, `FirebaseKeepAnalyticsTest.goalLockUpdatedUsesSafeChangedFieldOnly`는 상세 화면 상태, 종료 확인/취소, 앱 변경 저장/빈 선택 거절, 이름 변경 저장/빈·동일 이름 거절, duration update recalculates end date, lock mode update tracks lock_mode vs schedule changed_field, 동일 시작/종료 scheduled update 저장/analytics 거절, `ended_early` 저장, `goal_lock_ended_early` enum/bucket payload, `goal_lock_updated(changed_field=apps|name|duration|lock_mode|schedule)` privacy-safe payload를 검증한다.
- `HomeViewModelActivationAnalyticsTest.activeGoalLockExposesHomeProgressCardState`는 active/pending/ended_early 목표 잠금이 Home progress card state로 노출되는지 검증한다.
- `HomeViewModelActivationAnalyticsTest.activeGoalLockTakesPriorityOverFuturePendingGoalLockOnHomeCard`, `nearestPendingGoalLockIsShownWhenNoGoalLockIsCurrentlyActive`, `completedGoalLockDoesNotHideActiveOrPendingHomeCardCandidate`는 다중 목표 잠금이 공존할 때 Home card가 `Active > Pending > Completed > EndedEarly` 사용자 안전 우선순위를 지키고, pending 후보끼리는 가장 가까운 시작일을 먼저 보여주는지 검증한다.
- `HomeViewModelActivationAnalyticsTest.expiredActiveGoalLockIsCompletedFromHomeCardLoadAndTrackedOnce`는 종료일이 지난 active 목표 잠금을 Home card load 경로에서 `completed`로 정규화하고 `goal_lock_completed`를 1회만 기록하는지 검증한다.
- Home card/section은 active/completed/ended_early 상태, 남은 기간/종료일, lock mode, 선택 앱 수, 상세 CTA를 표시하고 상세 화면으로 이동한다. 여러 목표 잠금이 동시에 존재하면 실제 보호 중인 active 잠금을 미래 pending 잠금보다 우선 표시한다.
- `KeepAccessibilityServiceIntegrationTest.activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution`는 실제 AccessibilityService bind 후 수동 Keep이 꺼진 상태에서도 active all-day 목표 잠금이 선택 앱 foreground 전환을 `BlockActivity`로 연결하고, instrumentation debug state가 `block_source=goal_lock` / `goal_lock_id`를 남기는지 검증한다.
- `KeepAccessibilityServiceIntegrationTest.activeScheduledGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution`는 같은 실제 AccessibilityService bind 경로에서 현재 요일 scheduled window의 active 목표 잠금도 수동 Keep 없이 선택 앱 foreground 전환을 `BlockActivity`로 연결하고 동일한 `block_source=goal_lock` / `goal_lock_id` attribution을 남기는지 검증한다.
- `KeepAccessibilityServiceIntegrationTest.expiredGoalLockWithoutManualKeep_keepsTargetForegroundWithoutGoalLockAttribution`는 저장 상태가 `active`로 남아 있더라도 종료일이 지난 목표 잠금이 수동 Keep 없이 선택 앱 foreground 전환을 `BlockActivity`로 보내지 않고, debug state에 `block_source=goal_lock` attribution을 남기지 않는지 검증한다.
- 위 Goal Lock Accessibility smoke는 launch 가능한 첫 후보가 launcher나 다른 foreground로 되돌아가는 emulator image에서도 다음 후보를 재설정/재시도해야 하며, 모든 후보가 실패하면 후보별 `am start -W` 결과, `KeepAccessibilityServiceDebugState`, resumed activity, focused window를 실패 메시지에 남겨 launch 후보 flake와 실제 차단 회귀를 구분한다.
- `GoalLockStartReevaluationPolicyTest`는 #691 회귀 baseline이다. Goal Lock이 아직 foreground 이벤트를 만들지 않은 상태에서 `all_day` 시작일 자정, same-day scheduled 시작시간, overnight scheduled 시작시간, 다음 반복요일, 루틴+Goal Lock 조합 중 가장 빠른 시작 시각을 `KeepAccessibilityService`의 foreground 재평가 예약 대상으로 계산하는지 검증한다. 완료/invalid/expired/선택 앱 0개 Goal Lock은 예약 대상에서 제외되어야 한다.
- `MenuViewModelTest.isBlockingIncludesActiveAllDayGoalLock` / `isBlockingIgnoresGoalLocksThatAreNotCurrentlyBlocking`는 메뉴/설정의 active blocking 판단이 manual Keep·루틴뿐 아니라 현재 실제로 차단 중인 Goal Lock과도 일치하는지 검증한다. 이 값이 false로 남으면 `prevent_uninstall` 토글 가능 여부가 Goal Lock 강제 잠금 상태와 어긋날 수 있다.
- Accessibility/blocking runtime은 expiration 경계까지 선택 앱 차단 여부가 정책 helper와 일치해야 한다.

수동 QA evidence template:

```md
## Goal lock QA evidence
- Issue: #417
- Build / variant:
- Device / Android version / OEM:
- Entry point: home / routine / menu
- Commands:
  - `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.goallock.GoalLockPolicyTest' --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.goalLockCreatedUsesSafeBucketedParamsOnly' --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.goalLockEndedEarlyUsesSafeBucketedParamsOnly' --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.goalLockUpdatedUsesSafeChangedFieldOnly' --tests 'com.uiery.keep.feature.goallock.GoalLockPersistenceMapperTest' --tests 'com.uiery.keep.feature.goallock.GoalLockCreationViewModelTest' --tests 'com.uiery.keep.feature.goallock.GoalLockDetailViewModelTest' --tests 'com.uiery.keep.feature.goallock.GoalLockAccessibilityDescriptionTest' --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest.activeGoalLockExposesHomeProgressCardState' --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest.expiredActiveGoalLockIsCompletedFromHomeCardLoadAndTrackedOnce'`
  - `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.service.GoalLockStartReevaluationPolicyTest' --tests 'com.uiery.keep.feature.menu.MenuViewModelTest.isBlockingIncludesActiveAllDayGoalLock' --tests 'com.uiery.keep.feature.menu.MenuViewModelTest.isBlockingIgnoresGoalLocksThatAreNotCurrentlyBlocking'`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeScheduledGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#expiredGoalLockWithoutManualKeep_keepsTargetForegroundWithoutGoalLockAttribution`
  - `python3 -m unittest scripts.tests.test_goal_lock_contract -v`
- all-day / scheduled / expiration:
  - all-day blocks selected apps through date boundary: pass / fail
  - scheduled normal window blocks selected apps inside selected windows: pass / fail
  - scheduled overnight window blocks after start time and before next-day end time: pass / fail
  - scheduled same start/end time is rejected in creation and detail update, with user-facing guidance visible: pass / fail
  - scheduled without repeat days is rejected: pass / fail
  - scheduled does not block outside selected windows: pass / fail
  - expiration stops blocking after end date: pass / fail
- Home card/section:
  - goal name / remaining period / lock mode / selected app count visible:
  - active / completed / ended_early status correct:
  - TalkBack label understandable:
- Creation/detail summary TalkBack:
  - creation summary contentDescription includes goal name / date range / lock mode / selected app count: pass / fail
  - detail summary contentDescription includes goal name / mode+app count summary / active-completed-ended state: pass / fail
  - actual screen reader/screenshot spot-check: pass / fail / not collected
- End/update confirmation copy:
  - non-punitive tone: pass / fail
  - app update confirmation shows changed app count and explicit save/cancel actions: pass / fail
  - duration update confirmation shows changed day count and explicit save/cancel actions: pass / fail
  - lock mode update confirmation distinguishes all-day vs scheduled and explicit save/cancel actions: pass / fail
- Analytics payload spot-check:
  - enum/bucket only:
  - raw goal name / app package / app label / raw date absent:
- Decision: pass / fail / needs follow-up
- Notes:
```

이 증거가 없으면 #417은 `docs/GOAL_LOCK_MVP.md` 문서 계약이 있더라도 구현/runtime QA 경계가 남은 상태로 본다. GA4 Admin 등록과 14일/30일 성과 판단은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`와 `docs/GOAL_LOCK_MVP.md`의 외부/manual 경계를 따른다.

### develop/main 기본 CI gate

`Android CI`는 release 전용 `release-qa.yml`보다 가벼운 기본 PR gate로 아래를 자동 실행한다.

- `./gradlew :app:testDevDebugUnitTest`
- `./gradlew :app:lintDevDebug`
- `./gradlew :app:assembleProdDebug`
- focused runtime smoke class/method set은 `scripts/android_runtime_suites.py`가 source of truth다. 문서가 selector를 복붙하지 말고 suite 이름과 run URL을 기록한다.
  - `android_ci_focused_runtime_smoke`
  - exact-alarm 최소 smoke: `android_ci_exact_alarm_default` + `android_ci_exact_alarm_denied` + `android_ci_exact_alarm_allowed`
  - 별도 host-side appops run: `notification_denied_receiver` + `notification_denied_emergency_unlock`
  - channel-disabled run: `notification_channel_disabled` (앱 전체 알림/`POST_NOTIFICATIONS`는 허용, 루틴·긴급해제 channel만 `IMPORTANCE_NONE`)
  - 현재 selector 출력:
    - `python3 scripts/android_runtime_suites.py markdown android_ci_focused_runtime_smoke`
    - `python3 scripts/android_runtime_suites.py markdown android_ci_exact_alarm_default android_ci_exact_alarm_denied android_ci_exact_alarm_allowed`
    - `python3 scripts/android_runtime_suites.py markdown notification_denied_receiver notification_denied_emergency_unlock notification_channel_disabled`
- exact-alarm host-side appops run:
  - `./gradlew :app:installDevDebug`
  - `adb shell cmd appops reset com.uiery.keep.dev`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$(python3 scripts/android_runtime_suites.py class-arg android_ci_exact_alarm_default)"`
  - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$(python3 scripts/android_runtime_suites.py class-arg android_ci_exact_alarm_denied)"`
  - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$(python3 scripts/android_runtime_suites.py class-arg android_ci_exact_alarm_allowed)"`
- separate host-side appops run:
  - `./gradlew :app:installDevDebug`
  - `adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$(python3 scripts/android_runtime_suites.py class-arg notification_denied_receiver notification_denied_emergency_unlock)"`
- channel-disabled runtime run:
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$(python3 scripts/android_runtime_suites.py class-arg notification_channel_disabled)"`

이 gate는 develop/main PR 단계에서 lint·핵심 runtime 계약을 먼저 막는 역할이다. Backup/restore DataStore key 분류처럼 Android framework 없이 잡을 수 있는 정책 drift는 JVM static contract를 먼저 남긴다: `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.datastore.BackupRestoreDataStoreKeyPolicyTest'`.

- `StopitReleaseSmokeTest`: 앱 기동 + Compose navigation host smoke
- `BackupRestoreDataStoreKeyPolicyTest`: 모든 `PreferencesKey`가 backup/restore 분류 allowlist에 들어 있고, `PreferencesKey.ROUTINES`만 Room 재수화 compatibility cache 예외인지 확인
- `BackupRestoreRuntimeResetIntegrationTest`: 복원된 Room + 비어 있는 DataStore shape에서 reset-only state 미복원
- `HomeAccessibilityPermissionIntegrationTest`: 홈 접근성 권한 경고가 substring false positive 없이 실제 service state와 settings-resume 복귀를 따라 즉시 재동기화되는지
- `EmergencyUnlockBottomSheetContentIntegrationTest`: 긴급해제 bottom sheet reason-required ON/OFF flow의 reason/custom reason validation → 앱 선택 → duration → countdown → cancel/submit click-through가 실제 Compose 렌더링에서도 유지되는지
- focused `ReceiverRuntimeIntegrationTest`: Boot/package-replaced/time/timezone 변경 후 Room 재수화, 단일·다중 요일 루틴 exact alarm 재예약, 루틴 시작 재예약, notification-denied fallback notice contract
- `NotificationChannelDisabledIntegrationTest`: 앱 전체 알림과 `POST_NOTIFICATIONS`는 허용된 상태에서 `ROUTINE_CHANNEL` / `emergency_unlock` channel importance가 `IMPORTANCE_NONE`일 때 루틴 fallback notice와 긴급해제 stale notification cancel 계약
- `RoutineStartNotificationTapIntegrationTest`: 루틴 시작 알림 builder가 `contentIntent`를 포함하고 `ACTION_ROUTINE_START_NOTIFICATION_TAP`, `routineId`, `NOTIFICATION_SOURCE_ROUTINE_START`를 `MainActivity` 복귀 intent로 전달하는 계약. 실제 posted-path는 channel-disabled suite와 섞이면 Android가 동일 channel을 다시 올릴 수 없어 순서 의존 skip이 생길 수 있으므로, device QA에서는 fresh install/notification settings reset 상태에서 별도로 탭 동작을 확인한다.
- `EmergencyUnlockExpiryIntegrationTest`: 긴급해제 만료 state cleanup + 재차단 대상 판정 + stale notification cleanup, 별도 deny focused 메서드로 `POST_NOTIFICATION` guard 계약
- `EmergencyUnlockPolicyTest`: `EMERGENCY_UNLOCK_EXPIRE_TIME`에 저장된 만료 시각만으로 남은 초를 재계산하고 countdown notification tick을 재예약하는 JVM 계약. Lock 화면/ViewModel coroutine이 사라져도 AccessibilityService가 DataStore snapshot 기준으로 countdown 알림을 계속 동기화해야 한다.
- `KeepMessagingServiceIntegrationTest`: FCM token regeneration storage wiring
- `KeepAccessibilityServiceIntegrationTest`: 실제 AccessibilityService bind 후 cross-app foreground 전환, emergency unlock 우회, self-uninstall interception safety 계약. `emergencyUnlockStoredExpiry_syncsCountdownNotificationAfterServiceSnapshot`는 Lock 화면/ViewModel coroutine 없이도 service snapshot 경로가 stored `expireTimeMillis`를 읽어 countdown notification sync를 `Posted` 결과까지 재생성하는지 별도 focused evidence로 고정한다.
- `RoutineStartReevaluationPolicyTest`: 루틴 목록 snapshot이 갱신된 뒤 다음 활성 루틴 시작 시각에 AccessibilityService가 현재 foreground를 재평가하도록 예약하는 JVM 계약. 이미 보호 대상 앱이 foreground인 상태에서 루틴 시간이 도래해도 다음 window-state event를 기다리지 않는 #609 회귀 방지 baseline이다.

Receiver async 예외 containment는 JVM baseline `./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.receiver.ReceiverCoroutineRunnerTest"`로 먼저 확인한다. 이 baseline은 `BootReceiver` / `RoutineAlarmReceiver`의 `goAsync()` 작업이 내부 dependency 예외를 만나도 `PendingResult.finish()`를 1회 호출하고 sibling receiver coroutine을 취소하지 않으며, 실패 receiver 이름과 원인 예외가 Crashlytics non-fatal 기록 경계(`receiver_name` custom key + `ReceiverCoroutineException`)로 전달되는 계약을 고정한다. Runtime smoke는 정상/권한/fallback 경로를 검증하고, dependency 예외 주입 경계는 이 JVM baseline을 PR evidence에 함께 남긴다.

### clickable UI accessibility semantics baseline

Issue #443 계열 PR에서는 IconButton 내부 아이콘 label만 보지 말고, non-IconButton `.clickable` 표면이 role/state semantics를 갖는지도 같이 확인한다.

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_compose_icon_button_accessibility -v
./gradlew :app:compileDevDebugKotlin
```

검증 기준:
- Lock History 주/월 tab은 `Role.Tab`, `selected`, `stateDescription`을 semantics tree에 노출한다.
- Lock History 주간 날짜 cell은 날짜/요일/누적 시간 label과 오늘/선택 상태를 TalkBack이 읽을 수 있게 노출한다.
- Menu row/card/toggle은 decorative icon의 `contentDescription = null`을 유지하되, 조작 가능한 컨테이너가 `Role.Button` 또는 `Role.Switch`와 상태 설명을 소유한다.
- `scripts/check_compose_icon_button_accessibility.py`의 guarded path 목록은 이 핵심 표면에 대한 static regression gate이며, 새 핵심 clickable 표면이 추가되면 목록/정책을 함께 갱신한다.

exact alarm 권한 deny/allow 전환과 release-only remaining connected suite는 여전히 release/hotfix 대상 `Android Release QA`가 담당한다.

### notification onboarding permission baseline

issue #172/#313 계열 PR에서는 현재 지원 범위는 minSdk 33 / Android 13+ `POST_NOTIFICATIONS` runtime permission임을 전제로, 알림 권한 온보딩이 runtime permission 거절 후에도 앱 선택 단계 진행을 막지 않는지 아래처럼 남긴다. Android 12L 이하 legacy 설정 왕복은 historical / out of scope이며, minSdk를 다시 낮출 때만 현재 검증 대상으로 복원한다.

- 자동 baseline

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.feature.onboarding.notification.PostNotificationPermissionResultActionTest" \
  --tests "com.uiery.keep.feature.onboarding.OnboardingAnalyticsViewModelTest"
```

- 검증 범위:
  - Android 13+ runtime permission 경로에서 허용은 `granted + onboarding_step_complete(step_name=notification)` 후 앱 선택으로 이동하는지
  - Android 13+ runtime permission 경로에서 거절도 `denied + onboarding_step_complete(step_name=notification)`를 남기고 앱 선택으로 이동해 첫 잠금 설정을 계속할 수 있는지
  - notification-denied 상태의 루틴 시작 안내는 별도 `POST_NOTIFICATION ignore` receiver fallback baseline으로 계속 검증되는지
  - Historical / out of scope: `settings_opened`와 Android 12L 이하 legacy 설정 왕복은 minSdk 33 유지 상태에서는 현재 검증 대상이 아니다.

- 추가 manual evidence가 필요하면 아래 형식으로 남긴다.

```md
## Notification onboarding permission evidence
- Device/Emulator:
- Android version:
- Variant:
- Flow: Android 13+ runtime permission
- Commands:
  - `./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.onboarding.notification.PostNotificationPermissionResultActionTest" --tests "com.uiery.keep.feature.onboarding.OnboardingAnalyticsViewModelTest"`
- Observed analytics/order:
  - Android 13+ runtime allow: `granted` + `onboarding_step_complete(step_name=notification)` before continuing to app selection
  - Android 13+ runtime deny: `denied` + `onboarding_step_complete(step_name=notification)` before continuing to app selection
  - Historical / out of scope: `settings_opened` and legacy settings return order are not current minSdk 33 QA targets; restore only if minSdk is lowered again.
- Observed UI:
  - Android 13+ system dialog에서 거절해도 앱 선택 화면으로 이동하는지
  - Android 13+ system dialog에서 허용해도 앱 선택 화면으로 이동하는지
- Notes:
```

### Crashlytics startup ANR / SDK background crash baseline

Issue #101 계열 Crashlytics ANR 샘플(`e14bf5e28f9983aebd0e3ef2601c691d`, `77fafc0d6ce7c7a75c8b13d20ed2bb2c`, `4c1ed3a5d227234e314f386a5b9a1d97`, `0864599aefbd42499c770e81e4426ddf`)은 모두 `KeepApplication.onCreate` 또는 `BlockActivity` 시작 근처로 blame되지만 sample thread는 실제로 Chromium/System WebView 또는 Play services Ads 초기화가 main thread에서 binder/IO를 기다린 형태다. 앱 시작 critical path에 광고 SDK 초기화를 다시 inline으로 넣지 않도록 아래 JVM 계약을 PR evidence에 남긴다.

Issue #101의 최근 fatal topIssues에는 앱 코드 직접 line이 아니라 Google/Firebase/AndroidX SDK background thread에서 플랫폼 API mismatch가 process fatal로 승격되는 샘플도 있다. 대표 케이스:
- `d1369c1905b65f09a031309198552d10`: `ScionFrontendApi` background thread, `play-services-base@@18.9.0` / `Firebase measurement`, `getAttributionSource()` `NoSuchMethodError`, lastSeen `1.7.7`.
- `8a2cfe07f945b5bcc4e7cbd4928d42a6`: `androidx.profileinstaller.ProfileVerifier$Api33Impl.getPackageInfo`, `PackageInfoFlags.of` `NoSuchMethodError`, lastSeen `1.7.7`.
- `5c3f76729005f60fffa2beae30e770c7`: Compose font resolver `fontWeightAdjustment`, `NoSuchFieldError`, lastSeen `1.7.7`.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.MobileAdsStartupPolicyTest'
./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.BackgroundSdkCrashPolicyTest'
```

검증 기준:
- `MainActivity.onCreate`에서 `MobileAds.initialize(...)`를 즉시 호출하지 않는다.
- 광고 SDK 초기화는 첫 frame/post 이후 최소 1초 이상 지연된 lifecycle coroutine에서 실행한다.
- Activity가 이미 `finishing` 또는 `destroyed` 상태면 지연된 초기화를 생략한다.
- known SDK/platform mismatch는 main thread crash가 아닐 때만 containment 대상이다. 앱 코드 crash 또는 main thread crash는 기존 platform/Crashlytics handler로 위임한다.
- Crashlytics MCP/Console에서 같은 ANR/fatal issue가 새 버전에 재발하는지는 release 후 별도 모니터링 경계로 남긴다.

#### #101 release 후 Crashlytics recurrence evidence template

#101 계열 PR이 release 후보에 포함되면 release PR/이슈 코멘트에 아래 템플릿을 붙여 **코드 방어 완료**와 **live Crashlytics 재발 관측**을 분리한다. Crashlytics 이슈가 `OPEN`이어도 새 버전에서 event가 더 이상 늘지 않으면 코드 회귀와 운영 관측 상태를 다르게 해석한다.

```md
## Crashlytics #101 post-release recurrence evidence
- Release/tag:
- Version code/name:
- Included fixes:
  - PR #143 fatal analytics backend fallback:
  - PR #304 MobileAds startup deferral:
  - PR #320 FCM token fetch deferral:
  - PR #322 background SDK crash containment:
- Crashlytics source:
  - Firebase Console / Crashlytics MCP / Discord alert payload:
- Observation window:
  - start:
  - end:
- Issue IDs checked:
  - `d1369c1905b65f09a031309198552d10` (`getAttributionSource` fatal): last seen in this release? yes/no, events/users:
  - `e14bf5e28f9983aebd0e3ef2601c691d` (startup ANR): last seen in this release? yes/no, events/users:
  - `77fafc0d6ce7c7a75c8b13d20ed2bb2c` (startup ANR): last seen in this release? yes/no, events/users:
  - `4c1ed3a5d227234e314f386a5b9a1d97` (startup ANR): last seen in this release? yes/no, events/users:
  - `0864599aefbd42499c770e81e4426ddf` (BlockActivity/startup ANR): last seen in this release? yes/no, events/users:
  - `8a2cfe07f945b5bcc4e7cbd4928d42a6` (`PackageInfoFlags` fatal): last seen in this release? yes/no, events/users:
  - `5c3f76729005f60fffa2beae30e770c7` (`fontWeightAdjustment` fatal): last seen in this release? yes/no, events/users:
- New fatal/ANR alerts during window:
  - none / issue IDs:
- Closure decision:
  - close #101 / keep open because:
```

판단 기준:
- 위 PR들이 포함된 release/tag가 실제 internal/production 배포되지 않았으면 #101을 닫지 않는다.
- 새 버전에서 동일 issueId가 재발하면 해당 issueId, affected version, affected users/events, sample stack을 먼저 기록하고 root cause를 다시 분류한다.
- 기존 issueId는 조용하지만 새로운 fatal/ANR alert가 생기면 #101에 무리하게 흡수하지 말고 Discord alert payload의 duplicate-search 링크로 기존/신규 작업 경계를 확인한다.

### DevTool production graph baseline

DevTool은 `Device ID`/`FCM Token` 같은 내부 진단값을 표시하므로 production graph에 등록되지 않아야 한다. dev/debug 진단 접근은 유지하되, prod flavor에서는 debug/release 여부와 무관하게 route 등록 자체가 막혀야 한다.

Issue #208 계열 PR은 아래 JVM 정책 테스트와 prod-like artifact build를 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.KeepAppNavigationPolicyTest"
./gradlew :app:assembleProdDebug
```

검증 기준:
- `shouldRegisterDevToolRoute(flavor = "dev", isDebug = true)`만 `true`다.
- `prodDebug`/`prodRelease` 조합에서는 DevTool route가 `NavHost`에 등록되지 않는다.
- prod 사용자 화면에서 `Device ID`/`FCM Token` 진단값으로 이동할 수 있는 메뉴/graph 경로가 남지 않는다.

### 앱 선택 package visibility baseline

Issue #249 계열 PR은 `QUERY_ALL_PACKAGES`를 UI에서 직접 소비하지 않고 앱 선택 데이터 소스 계약 뒤에 격리해야 한다. 권한의 목적은 사용자가 차단 대상을 고르는 데 필요한 launchable app 목록 구성으로 제한한다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.appselection.SelectableAppPolicyTest'
./gradlew :app:assembleProdDebug
```

검증 기준:
- `CategoryBottomSheetContent`는 `PackageManager.getInstalledApplications(...)`를 직접 호출하지 않는다.
- broad package visibility query는 `InstalledAppRepository`에서만 수행한다.
- `SelectableAppPolicyTest`가 launch intent 없는 앱 제외, Stopit 자기 package 제외, picker 정렬 안정성을 고정한다.
- Manifest/Play 정책 설명은 “앱 차단 대상 선택” 목적과 충돌하지 않아야 한다.

### 첫 차단 성공 피드백 baseline

Issue #14 계열 PR에서 차단 화면의 첫 가치 경험 피드백을 바꿀 때는 `first_lock_configured`를 실제 차단 완료로 과장하지 않고, 실제 차단 화면 진입에서만 첫 성공 피드백이 노출되는지 확인한다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.BlockViewModelTest'
```

검증 기준:
- 최초 차단 진입은 `app_block_intercepted`를 먼저 기록하고 이어서 `first_core_action_completed`를 1회 기록한다.
- `BlockUiState.showFirstCoreActionFeedback`은 최초 차단에서만 `true`다.
- 이미 `HAS_TRACKED_FIRST_CORE_ACTION=true`인 반복 차단은 `core_action_completed`만 기록하고 첫 성공 피드백을 반복 노출하지 않는다.
- 수동 QA에서는 차단 화면의 긴급해제/닫기 동작이 첫 성공 피드백 카드에 가려지지 않는지 함께 확인한다.

### 앱 표시 메타데이터 경계 QA baseline

issue #432 계열 PR은 사용자에게 차단 대상 앱을 보여주는 화면이 동일한 `AppDisplayMetadataResolver` 계약을 쓰는지 증거를 남긴다. 삭제된 앱, package visibility 제한, label/icon 조회 실패가 섞여도 화면마다 다른 fallback을 만들지 않는 것이 목적이다.

자동 baseline:

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.AppDisplayMetadataBoundaryTest" \
  --tests "com.uiery.keep.util.AppDisplayMetadataResolverTest" \
  --tests "com.uiery.keep.appselection.InstalledAppRepositoryTest"
./gradlew :app:compileDevDebugKotlin
```

검증 범위:
- `BlockScreen`, 긴급해제 앱 선택 sheet, 차단 앱 이력, Top Apps 카드가 Compose surface 안에서 `PackageManager` 또는 `AppDisplayMetadataResolver(...)`를 직접 소유하지 않고 shared `rememberAppDisplayMetadataResolver()` 경계를 쓴다.
- `AppDisplayMetadataResolver`가 package lookup / label lookup / icon lookup 실패 시 package name label과 `PackageManager.defaultActivityIcon` placeholder를 일관되게 반환한다.
- `InstalledAppRepository`의 설치 앱 스캔도 같은 resolver fallback을 사용하면서 자기 앱과 launch intent 없는 앱을 계속 제외한다.

수동 QA가 필요하면 차단 화면, 긴급해제 대상 선택, 차단 앱 이력, Top Apps에서 같은 삭제/숨김 앱 package가 동일한 이름/placeholder로 보이는지만 추가로 기록한다.

### Android 공식 testing skill 기반 UI smoke baseline

Android skills가 설치된 환경에서는 `testing-setup`과 `android-cli` skill을 먼저 읽고 QA 범위를 잡는다.

- `/Users/uiel/.agents/skills/testing-setup/SKILL.md`
- `/Users/uiel/.agents/skills/android-cli/SKILL.md`
- 운영 문서: `docs/ANDROID_SKILLS_TESTING_QA.md`

release/hotfix PR은 `Release instrumentation QA`에서 아래 순서로 release runtime gate를 실행한다. 세부 단계 source of truth는 `.github/workflows/release-qa.yml`, `scripts/android_runtime_suites.py`, `docs/ops/stopit/release-context.md`이며, 이 문서는 그 순서를 사람이 반복 실행하기 쉬운 checklist 형태로 풀어쓴 것이다. Android CI의 focused runtime smoke 목록과 섞지 말고, main-target release evidence에는 아래 Release QA 목록을 그대로 기록한다.

Suite sequence: `release_focused_ui_smoke` → `release_exact_alarm_default` → `release_exact_alarm_denied` → `release_exact_alarm_allowed` → `release_remaining_runtime` → `notification_denied_receiver` → `notification_denied_emergency_unlock` → `notification_channel_disabled`.

```bash
cd <repo-root>
./gradlew :app:installDevDebug
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest
./gradlew :app:installDevDebug
adb shell cmd appops reset com.uiery.keep.dev
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm,com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm,com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent,com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEnabledRoutineFromRoom,com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEveryRepeatDayAlarm
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.manifest.ManifestContractIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification
./gradlew :app:installDevDebug
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.notification.NotificationChannelDisabledIntegrationTest
```

즉, release candidate baseline은 `focused UI smoke -> exact alarm default(MODE_DEFAULT) -> exact alarm deny(8개, multi-day 포함) -> exact alarm allow/cancel/permission-change restore(5개) -> remaining connected suite -> notification-denied receiver gate -> notification-denied emergency-unlock gate -> notification-channel-disabled gate` 순서다. exact alarm/notification appops 전환은 target app 프로세스를 죽일 수 있으므로, 권한 상태 변경은 테스트 메서드 안이 아니라 **host ADB 명령 → focused instrumentation 실행** 순서로 유지해야 한다.

issue #580 계열 exact alarm 권한 안내 QA에서는 루틴 생성/활성화가 `ShowAlarmPermission`을 발생시켜 sheet가 보였다는 사실과, 사용자가 설정 이동 버튼을 명시적으로 눌러 OS 설정으로 나가려 했다는 사실을 분리해 기록한다. 단순 dismiss만 한 경우 `HAS_SHOWN_ALARM_PERMISSION`을 영구 true로 저장하면 안 되며, 이후 권한이 여전히 없으면 화면 재진입/루틴 활성화 경로에서 다시 안내될 수 있어야 한다. 설정 Activity가 OEM/프로필 환경에서 열리지 않으면 앱 상세 설정 fallback으로 이동하고, 그마저 실패해도 crash 없이 권한 없음/disabled routine 상태를 유지해야 한다.


## analytics / queryability handoff 경계

receiver/service 런타임 QA와 analytics queryability는 다른 층위다. release evidence를 남길 때 아래를 같이 분리한다.

- Android runtime smoke / release instrumentation이 green이라고 해서 GA4 `customEvent:*` queryability가 해결된 것은 아니다.
- analytics payload, screen name, review/ad/activation 파라미터 계약이 바뀌었다면 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 같이 확인해 **repo 코드·문서 반영**과 **GA4 Admin 수동 등록 / metadata 재확인 / 배포 후 14일 재측정**을 분리해 기록한다.
- live metadata에 `customUser:routines_count`만 보이는 상태라면 activation/review/monetization `customEvent:*` 축까지 queryable하다고 과대해석하지 않는다.
- `runReport`가 `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`을 반환하면, release PR/issue evidence에는 no-data가 아니라 registration gap으로 적는다.

### receiver/service instrumentation baseline

issue #27 계열 PR에서는 아래 focused Android 통합 테스트를 기본 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest
```

- `bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm`
- `manifestMarksBootReceiverNotExported`
- `manifestRegistersBootReceiverForPackageAndClockChangeActions`
- `timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm`
- `timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms`
- `packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm`
- `routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine`
- `routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`

이 baseline은 BootReceiver/RoutineAlarmReceiver의 핵심 재수화·재예약 contract를 검증한다. 특히 `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`까지 포함해 앱 업데이트나 기기 wall-clock/timezone 변경 후에도 활성 루틴 복구 경로가 manifest와 런타임 로직 양쪽에서 유지되는지 확인하고, `BootReceiver`가 `exported=false`로 고정되어 외부 앱의 explicit broadcast만으로 루틴 복원·비활성화 부작용을 만들 수 없도록 노출 계약을 검증한다. 시간/시간대 변경 경로는 exact alarm 권한 회수(#137/#149)와 별개로, 사용자가 설정한 로컬 시각 기준으로 단일 요일·다중 요일 루틴 `PendingIntent`가 다시 생성되는지를 본다. 루틴 알람이 도착했을 때는 먼저 Room source of truth 기준으로 해당 루틴 ID가 존재하고 `enabled=true`인지 확인한 뒤에만 루틴 시작 알림 또는 Home fallback notice를 노출한다. 삭제된 루틴, 비활성화된 루틴, stale `PendingIntent`는 “루틴 시작” 표면을 만들지 않아야 하며, 이 유효성 계약은 JVM `RoutineReceiverPolicyTest.shouldShowRoutineStartNoticeReturnsTrueOnlyForMatchingEnabledRoutine`으로 먼저 고정한다. 또한 Android 13+에서 `POST_NOTIFICATION`이 꺼진 상태에서도 **유효한 enabled 루틴**의 시작이 조용히 사라지지 않고 앱 내 fallback notice로 이어지는지 확인한다. 현재 focused test는 두 개 루틴이 연달아 시작돼도 pending notice queue가 FIFO 순서로 보존되고 마지막 메시지로 덮이지 않는지까지 검증한다. 다만 protected broadcast 기반 실제 cold boot와 AccessibilityService의 cross-app 차단 진입은 아래 수동 시나리오 evidence가 여전히 필요하다.

`POST_NOTIFICATION` deny focused test는 exact alarm appops와 비슷하게 **호스트 ADB/appops에서 먼저 상태를 바꾸고 그 다음 focused instrumentation을 실행**해야 한다. 테스트 메서드 안에서 notification appops를 바꾸면 target process가 죽어 flaky/crash가 날 수 있다. 이 deny/channel-disabled fallback baseline은 notification delivery 실패 처리이고, stale/deleted/disabled routine baseline은 알림 노출 전 루틴 유효성 검사이므로 실패 원인을 분리해서 기록한다.

추가 수동 확인 포인트: 홈의 category/time 바텀시트가 이미 열린 상태에서 루틴이 시작된 경우, 시트가 열려 있을 때는 fallback notice가 바로 보이지 않아도 된다. 대신 시트를 닫은 직후 홈 snackbar로 루틴 시작 안내가 **정확히 한 번** 노출되어야 하며, 같은 홈 복귀에서 중복 반복되면 안 된다.

```bash
cd <repo-root>
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine
adb shell appops set com.uiery.keep.dev POST_NOTIFICATION allow
```

### backup/restore app-open routine reschedule baseline

issue #490 계열 PR에서는 BootReceiver/package-replaced/routine-alarm 이벤트 없이 사용자가 복원 직후 앱을 여는 경로도 별도 evidence로 남긴다. 이 경로의 owner는 공통 `RoutineRestoreAftercare`이며, `SplashViewModel` 앱 시작 경로와 `RoutineViewModel` 루틴 화면 진입 경로가 모두 Room enabled routine을 즉시 재스케줄하고 `RoutineStore` compatibility cache를 Room 기준으로 다시 쓴다. exact alarm 권한/스케줄 실패가 확인되면 receiver 경로와 같이 `enabled=false` downgrade + 권한 prompt reset/side effect가 발생해야 한다.

issue #511 계열 PR에서는 `docs/ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md`를 source of truth로 보고, `PreferencesKey.ROUTINES` cache가 Room 루틴보다 우선하지 않는다는 증적을 남긴다. 최소 evidence는 Room-vs-cache 불일치 시 Room wins, blank/malformed cache decode safe, boot/package-replaced/routine alarm/restore-aftercare 후 cache rewrite, exact alarm 실패 시 Room `enabled=false`와 cache 결과 일치 여부를 포함한다. cache를 제거하는 PR은 `BackupRestoreDataStoreKeyPolicy.rehydratedCompatibilityCacheKeys` 예외 변경과 focused receiver/restore runtime command를 같은 PR body에 기록한다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.routine.RoutineViewModelRestoreSchedulingTest' \
  --tests 'com.uiery.keep.feature.splash.SplashViewModelRestoreSchedulingTest'
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest#appOpenRoutineEntryRehydratesRoomRoutineCacheAndSchedulesAlarmWithoutRevivingResetOnlyDataStoreState
```

검증 범위:
- restored-device shape처럼 DataStore runtime key가 비어 있어도 Room enabled routine이 앱 시작/Splash 및 Routine 화면 진입 시 `scheduleRoutine(...)`으로 재예약된다.
- 같은 진입에서 `PreferencesKey.ROUTINES` compatibility cache가 Room 목록 기준으로 다시 채워진다.
- scheduler가 `MissingExactAlarmPermission`을 반환하면 해당 routine은 `enabled=false`로 내려가고 `HAS_SHOWN_ALARM_PERMISSION=false` reset과 `ShowAlarmPermission` side effect가 함께 남는다.
- 실제 `PendingIntent` 존재 여부는 receiver/runtime instrumentation 또는 수동 `dumpsys alarm` evidence로 별도 확인한다.

### exact alarm permission baseline

issue #77 / #137 / #394 계열 PR에서는 Android 12+ exact alarm 권한 거절/허용 경로를 각각 분리해서 남긴다. `appops set`은 target app 프로세스를 죽일 수 있으므로, 권한 상태 변경은 테스트 메서드 안이 아니라 **host ADB 명령 → focused instrumentation 실행** 순서로 기록한다. 루틴 추가/수정/활성화의 권한 부족/스케줄 실패 해석은 `RoutineExactAlarmOrchestrator`가 단일 계약으로 소유하고, Compose 화면은 `ShowAlarmPermission` side effect 표시와 `createExactAlarmSettingsIntent(...)` 실행 경계만 맡는다.

```bash
cd <repo-root>
./gradlew :app:installDevDebug

# 거절 상태: 활성 루틴이 조용히 성공 상태로 남지 않아야 한다.
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt

./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent

./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent

./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent

# 허용 상태: 동일 경로에서 실제 PendingIntent 예약이 생겨야 한다.
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm
```

- 거절 경로 검증 범위:
  - `RoutineExactAlarmOrchestrator.resolveBeforePersist(...)`가 저장 전 permission pre-check를 적용하고, scheduler가 race/거짓 양성으로 `MissingExactAlarmPermission`을 반환해도 동일하게 `enabled=false` + prompt 계약으로 수렴하는지
  - `RoutineBottomSheetViewModel` 저장 시 side effect로 권한 안내를 띄우는지
  - `RoutineViewModel.addRoutine/updateRoutine/changeEnabled(...)`도 bottom-sheet 경로와 같은 orchestration을 사용해 권한 부족 시 enabled 상태를 조용히 유지하지 않는지
  - DB에 저장된 루틴이 `enabled=false`로 안전하게 내려가는지
  - 동일 루틴 ID의 `PendingIntent`가 남지 않는지
  - 권한 부족/스케줄 실패 경로에서는 성공 예약 analytics인 `lock_scheduled(schedule_type=routine)`을 남기지 않는지
  - `BootReceiver`가 부팅/패키지 교체 복구 중 exact alarm 재예약 실패를 만나도 해당 루틴을 `enabled=false`로 내리고 `HAS_SHOWN_ALARM_PERMISSION=false`로 되돌리는지
  - `MY_PACKAGE_REPLACED` 경로에서도 동일한 downgrade/no-pending-intent 계약이 유지되는지
  - `RoutineAlarmReceiver`가 루틴 시작 알림은 현재 시점에 계속 보여주되, 다음 exact alarm 재예약 실패 시 루틴을 `enabled=false`로 내리고 다음 `PendingIntent`를 남기지 않는지
- 허용 경로 검증 범위:
  - `RoutineViewModel.changeEnabled(...)`가 루틴을 다시 `enabled=true`로 올리는지
  - exact alarm `PendingIntent`가 실제로 예약되는지

### exact alarm permission 수동 evidence 템플릿

Android 12+ 실기기/에뮬레이터에서 추가 스크린샷 evidence가 필요하면 아래 형식으로 남긴다.

```md
## Exact alarm permission evidence
- Device/Emulator:
- Android version:
- Variant:
- Routine name / id:
- Permission state before save: allow / deny
- Commands:
  - `adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny|allow`
  - `adb shell dumpsys alarm | grep com.uiery.keep`
- Observed UI:
  - 권한 안내 바텀시트 노출 여부
  - 루틴 enabled/disabled 상태
- PendingIntent evidence:
- Notes:
```

### FCM token 재생성 baseline

issue #68 계열 PR에서는 아래 focused Android 통합 테스트를 기본 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepMessagingServiceIntegrationTest
```

- `persistNewTokenForContext_overwritesExistingStoredTokenViaEntryPoint`
- 검증 범위: `KeepMessagingService -> EntryPointAccessors -> DeviceTokenManager -> DataStore` 저장 wiring
- 이 baseline은 실제 FCM 서버 콜백을 대체하지 않지만, 새 기기/복원 후 토큰 재생성 시 앱 내부 저장 경로가 끊기지 않았는지 release 전에 반복 검증할 수 있게 한다.

### 부모 모드 runtime QA baseline

issue #471 구현 PR에서는 `docs/PARENT_MODE_MVP.md`를 source of truth로 두고 same-device / PIN / bypass 경계를 evidence로 남긴다. PR #519로 policy/analytics, PR #584로 session persistence와 Accessibility decision foothold, 2026-06-09 code-lane PR로 `ParentModeSessionController` commit boundary가 들어갔고, QA-lane runtime baseline은 `KeepAccessibilityServiceIntegrationTest#activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution` 및 `#expiredActiveParentModeWithoutManualKeep_blocksPreviouslyAllowedAppWithExpiredEvidence`로 active/expired Parent Mode session을 AccessibilityService가 실제로 관찰해 `block_source=parent_mode` 차단을 요청하는 device/emulator evidence를 고정한다. 부모 모드는 기존 긴급해제와 분리된 보호자 확인 flow이므로, 보호자 PIN 해제 성공을 `emergency_unlock_completed`로 기록하지 않는다.

권장 JVM/policy baseline:

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.feature.parentmode.ParentModePolicyTest" \
  --tests "com.uiery.keep.feature.parentmode.ParentModePinPolicyTest" \
  --tests "com.uiery.keep.feature.parentmode.ParentModeSessionStoreTest" \
  --tests "com.uiery.keep.feature.parentmode.ParentModeSessionControllerTest" \
  --tests "com.uiery.keep.service.KeepAccessibilityServiceBlockDecisionTest" \
  --tests "com.uiery.keep.service.GoalLockStartReevaluationPolicyTest.nextParentModeExpirationReevaluationDelayReturnsDelayUntilActiveSessionExpiry" \
  --tests "com.uiery.keep.service.GoalLockStartReevaluationPolicyTest.nextParentModeExpirationReevaluationDelaySkipsExpiredOrInactiveSessions" \
  --tests "com.uiery.keep.service.GoalLockStartReevaluationPolicyTest.nextTimeBasedBlockingStartReevaluationDelayIncludesParentModeExpiry" \
  --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.parentModeStartedUsesSafeBucketedParamsOnly" \
  --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.parentModeCompletedDoesNotSendRawTimestampsOrPackages"
```

권장 runtime baseline:

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#expiredActiveParentModeWithoutManualKeep_blocksPreviouslyAllowedAppWithExpiredEvidence
```

검증 범위:

- duration preset/custom validation
- 허용 앱 1개 이상 선택 validation
- 보호자 PIN 미설정/성공/실패 policy
- DataStore session persistence와 restore-reset-only key boundary
- `ParentModeSessionController`가 setup validation 실패 시 저장/analytics를 하지 않고, 성공 시 session 저장과 `parent_mode_started` bucket event를 함께 commit하는지
- `ParentModeSessionController`가 PIN 성공 후 연장/즉시 종료만 저장하고 PIN 실패/미설정 상태에서는 session과 analytics를 바꾸지 않는지
- `ParentModeSessionController.markExpiredIfNeeded(...)`가 active session의 시간 만료를 `expired` state와 `parent_mode_completed(end_reason=time_expired)`로 한 번만 commit하고, 재호출/비활성 state에서는 no-op인지
- 부모 모드 active/expired/extended/cancelled state transition
- 보호자 PIN 성공 후에도 0분/음수 extension은 거부하고, 양수 extension만 만료 시각을 늘리는 parent-action guard
- `parent_mode_*` analytics payload가 `duration_minutes_bucket`, `allowed_app_count_bucket`, `pin_result`, `end_reason`, `extension_minutes_bucket`, `block_context` 같은 enum/bucket만 사용하고 아이 이름/앱 이름/package/raw session history/허용 앱 원문 목록/PIN 원문을 보내지 않는지
- 접근성 차단 판단이 허용 앱과 비허용 앱을 구분하고, 시간이 끝난 뒤 허용 앱도 계속 사용할 수 없게 하는지
- AccessibilityService가 active Parent Mode session의 `expiresAtMillis`에 맞춰 foreground 재평가를 예약해 같은 앱이 foreground에 남아 있어도 만료 후 차단으로 전환하는지
- active Parent Mode session을 `KeepAccessibilityService`가 device/emulator에서 관찰하고, 허용되지 않은 foreground 앱에 대해 `block_source=parent_mode` BlockActivity 요청을 남기는지
- expired Parent Mode session을 `KeepAccessibilityService`가 device/emulator에서 resolved `observedParentModeState=expired` evidence로 남기고, 기존 허용 앱도 `block_source=parent_mode`로 차단하는지
- Parent Mode Accessibility smoke는 첫 launch 후보가 launcher나 다른 foreground로 튕기면 다음 launchable 후보에 대해 session state를 다시 설정하고 재시도해야 한다. 모든 후보가 실패한 경우 후보별 launch 결과, debug snapshot, resumed activity, focused window를 기록해 환경 flake와 parent-mode 차단 회귀를 분리한다.
- Stopit 앱처럼 보호자 PIN/종료/연장 진입에 필요한 부모 제어 surface를 부모 모드 차단으로 막지 않는지

### Parent mode QA evidence

```md
## Parent mode QA evidence
- Device/Emulator:
- Android version:
- Variant:
- Entry point: home / menu
- Duration preset/custom:
- Allowed app count bucket: 1 / 2_3 / 4_6 / 7_plus
- PIN state before start: not_configured / configured
- Commands:
  - `./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.feature.parentmode.ParentModePolicyTest" --tests "com.uiery.keep.feature.parentmode.ParentModePinPolicyTest" --tests "com.uiery.keep.feature.parentmode.ParentModeSessionStoreTest" --tests "com.uiery.keep.feature.parentmode.ParentModeSessionControllerTest" --tests "com.uiery.keep.service.KeepAccessibilityServiceBlockDecisionTest" --tests "com.uiery.keep.service.GoalLockStartReevaluationPolicyTest.nextParentModeExpirationReevaluationDelayReturnsDelayUntilActiveSessionExpiry" --tests "com.uiery.keep.service.GoalLockStartReevaluationPolicyTest.nextParentModeExpirationReevaluationDelaySkipsExpiredOrInactiveSessions" --tests "com.uiery.keep.service.GoalLockStartReevaluationPolicyTest.nextTimeBasedBlockingStartReevaluationDelayIncludesParentModeExpiry" --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.parentModeStartedUsesSafeBucketedParamsOnly" --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.parentModeCompletedDoesNotSendRawTimestampsOrPackages"`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#expiredActiveParentModeWithoutManualKeep_blocksPreviouslyAllowedAppWithExpiredEvidence`
- same-device / PIN / bypass checks:
  - [ ] 보호자 PIN 확인 후에만 부모 모드가 시작된다.
  - [ ] 선택한 허용 앱은 시간 안에서 열 수 있다.
  - [ ] 허용되지 않은 앱은 차단된다.
  - [ ] AccessibilityService debug evidence가 `observedParentModeState=active`, 허용 앱 count, `lastLaunchedBlockSource=parent_mode`를 남긴다.
  - [ ] 시간이 끝나면 허용 앱도 계속 사용할 수 없다.
  - [ ] AccessibilityService debug evidence가 `observedParentModeState=expired`와 `lastLaunchedBlockSource=parent_mode`를 남긴다.
  - [ ] 같은 앱이 foreground에 남아 있어도 `expiresAtMillis` 기반 time-based 재평가로 만료 후 차단 전환이 예약된다.
  - [ ] PIN 없이 시간 연장/종료가 되지 않는다.
  - [ ] PIN 성공 후에도 0분/음수 extension은 거부되고 양수 extension만 만료 시각을 늘린다.
  - [ ] PIN 성공 시 즉시 종료가 된다.
  - [ ] 최근 앱, 설정, 알림 surface로 쉽게 우회되지 않는다.
  - [ ] 긴급 전화/필수 시스템 safety path를 부적절하게 막지 않는다.
- Privacy checks:
  - 아이 이름, 앱 이름/package, raw session history, 허용 앱 원문 목록, PIN 원문/길이/세부값이 analytics/log/share payload에 없음
- Notes / screenshots:
```

검증 원칙:

- 원격 자녀 기기 관리, 가족 계정, 서버 동기화, FCM 기반 원격 연장/해제는 #471 MVP runtime QA의 pass/fail 기준이 아니라 후속 gate다.
- 부모 모드 PIN과 긴급해제 quota/analytics를 섞지 않는다.
- GA4 Admin 등록/metadata 확인 전에는 `parent_mode_*` 세부 breakdown을 제품 결론으로 과대해석하지 않는다.
- PR #519/#584 이후 `develop`에 반영된 repo-internal foothold를 “구현 전”으로 되돌리지 않는다. 남은 실제 경계는 MVP 전체 UX(entrypoint/setup/active/expired/PIN runtime flow), device/emulator evidence, release/tag/Play deploy, GA4 Admin metadata/readback이다.

### Usage Access 개인화 discovery QA baseline

issue #119는 아직 구현 `ready`가 아니지만, discovery/contract child issue 또는 MVP implementation child issue가 생기면 아래 evidence를 PR 본문에 남긴다.

```md
## Usage Access discovery/QA evidence
- Build / appVersion:
- Device / Android version / OEM:
- Entry point: report_card / recommendation_cta / post_success_soft_prompt / settings
- Activation stage before prompt: pre_first_core_action / post_first_core_action / returning_user
- Permission state before test: not_allowed / allowed / unknown
- Steps:
  1. 사전 설명 노출 확인
  2. 시스템 설정 이동 확인
  3. 허용/거절/뒤로가기 후 앱 복귀 확인
  4. fallback 또는 리포트 카드 노출 확인
- Expected analytics without sensitive payload:
  - `usage_access_explainer_viewed(entry_point=..., activation_stage=...)`
  - `usage_access_settings_opened(entry_point=..., activation_stage=...)`
  - `usage_access_permission_result(result=granted|denied|unknown, entry_point=..., activation_stage=...)`
- Privacy checks:
  - 앱 이름/package/raw usage history가 analytics/log/share payload에 없음
  - 권한 거절 후에도 앱 차단/타이머/루틴/긴급해제 진입 가능
- Notes / screenshots:
```

검증 원칙:

- Usage Access는 핵심 차단 기능의 필수 권한이 아니다. `pre_first_core_action` 사용자의 첫 잠금 CTA보다 먼저 blocking prompt로 노출되면 실패로 본다.
- 권한 설정 화면 이동 후 복귀 상태는 `granted / denied / unknown`으로 기록한다. OEM 설정 화면 차이로 판별이 애매한 경우를 오류로 과대해석하지 않는다.
- analytics payload에는 앱 이름, package name, 설치 앱 전체 목록, raw usage history, 정확한 분 단위 원문을 넣지 않는다. bucket과 entry point만 사용한다.
- 로컬/CI 자동화가 없으면 manual evidence로 남기되, 구현 PR에서는 formatter/policy 단위 테스트와 event dictionary sync를 같이 요구한다.

### FCM token 재생성 수동 evidence 템플릿

새 기기/복원 시나리오에서 자동화 외 evidence가 필요하면 아래 형식으로 남긴다.

```md
## FCM token regeneration evidence
- Device/Emulator:
- Variant:
- Previous stored token:
- Trigger: fresh install / device transfer / restore after backup
- Commands:
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
- Result token after regeneration:
- Notes:
```

### Emergency unlock step analytics QA baseline

Issue: #779 계열 PR은 `docs/EMERGENCY_UNLOCK_STEP_ANALYTICS.md`를 source of truth로 두고 reason/app selection/duration/countdown 단계 노출, validation blocked, cancel source가 privacy-safe enum-only payload로 기록되는지 확인한다. 이 baseline은 #467 copy/step QA와 연결되지만, #694 설정 변경 analytics와 섞지 않는다.

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_emergency_unlock_step_analytics_contract -v
./gradlew --console=plain :app:testDevDebugUnitTest --tests '*EmergencyUnlock*'
./gradlew --console=plain :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContentIntegrationTest
```

확인:

- `emergency_unlock_step_viewed`: reason-required ON/OFF에 맞게 `step_name=reason|app_selection|duration|countdown`, `reason_required_enabled`, `entry_surface`만 기록한다.
- `emergency_unlock_validation_blocked`: `validation_reason=missing_reason|missing_custom_reason|missing_app_selection|missing_duration|duration_options_unavailable|unknown` enum만 기록한다.
- `emergency_unlock_cancelled`: sheet dismiss/back/cancel button/outside/system을 `cancel_source` enum으로만 기록하고, countdown cancel은 `emergency_unlock_completed`로 과장하지 않는다.
- reason-required ON/OFF: OFF flow에서는 reason step viewed나 missing reason/custom reason validation이 나오지 않아야 한다.
- Privacy: no custom reason raw text, no app name/package/list, no raw timestamp/history/duration list/settings snapshot.
- GA4: `customEvent:step_name`, `customEvent:validation_reason`, `customEvent:reason_required_enabled`, `customEvent:entry_surface`, `customEvent:cancel_source` GA4 Admin metadata 확인 전에는 세부 breakdown을 제품 결론으로 쓰지 않는다.
- Readback: Android 구현 포함 release/tag/Play deploy 후 14-day readback을 예약하고, 30-day window에서 reason-required ON/OFF와 entry surface별 guardrail을 재확인한다.

### 긴급해제 완료/만료 scriptable baseline

issue #204/#67 계열 PR에서는 아래 focused JVM + Android 통합 테스트를 기본 evidence로 남긴다. issue #424/#779 계열처럼 bottom sheet 단계/선택 상태 또는 step analytics를 건드리는 PR은 같은 묶음에서 `EmergencyUnlockBottomSheetStateTest`와 `test_emergency_unlock_step_analytics_contract`를 먼저 실행해 UI state machine과 privacy-safe analytics 계약을 고정하고, 실제 Compose bottom sheet click-through는 focused instrumentation baseline으로 확인한다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetStateTest" \
  --tests "com.uiery.keep.feature.lock.LockViewModelTest.emergencyUnlockCompletionPostsUnlockCompletedSideEffect"
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContentIntegrationTest
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest
```

- `EmergencyUnlockBottomSheetStateTest`: reason enabled/disabled, custom reason, 앱 선택 없음, duration fallback, countdown cancel/complete가 Composable local state에 숨지 않고 순수 JVM 계약으로 유지되는지 고정한다.
- `EmergencyUnlockBottomSheetContentIntegrationTest`: bottom sheet를 실제 Compose test rule에서 렌더링해 reason-required ON flow의 `기타` custom reason validation → 앱 선택 → duration → countdown 완료 payload(`reason=other`, duration, selected app set)와 reason-required OFF flow의 앱 선택 → duration → countdown/cancel 경로가 유지되는지 device/emulator에서 고정한다. 로컬 dev flavor Firebase prerequisite이 막히면 같은 class를 `:app:connectedProdDebugAndroidTest`로 실행해 prod google-services 경로에서 증거를 남기고, dev 경계는 PR 본문에 분리한다.
- `LockViewModelTest.emergencyUnlockCompletionPostsUnlockCompletedSideEffect`: LockScreen 진입점에서 긴급해제 완료 후 `UnlockCompleted` side effect가 발생해 화면 이탈 계약이 끊기지 않는지 고정한다.
- `EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage`: 만료 시각 도달 시 `EmergencyUnlockState`와 DataStore의 `EMERGENCY_UNLOCK_*` state를 제거하고, 전면 앱이 만료된 예외 앱이면 재차단 대상으로 되돌리며, 기존 ongoing 긴급해제 notification도 함께 정리하는지 검증한다.
- 이 baseline은 실제 cross-app Accessibility 진입 전체를 대체하지는 않지만, 긴급해제 bottom sheet UI click-through, 완료 후 Lock 화면 고착, 만료 후 우회 지속 회귀를 각각 device-emulator/JVM 레벨에서 반복 가능하게 고정한다.

`POST_NOTIFICATION` guard는 루틴 알림 fallback baseline과 같은 패턴으로 **호스트 ADB/appops에서 먼저 상태를 deny로 바꾼 뒤** focused instrumentation을 실행한다. 긴급해제 helper 내부에서 appops를 직접 토글하지 않는다.

```bash
cd <repo-root>
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification
adb shell appops set com.uiery.keep.dev POST_NOTIFICATION allow
```

- `emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification`
- deny 검증 범위: `EmergencyUnlockNotificationHelper`가 `areNotificationsEnabled()` / `POST_NOTIFICATIONS` guard 없이 무가드 `notify(...)`를 호출하지 않고, permission denied 결과를 돌려주며 stale notification을 남기지 않는지

### receiver/service QA용 권장 focused JVM baseline

issue #27 계열처럼 receiver/service runtime 리스크를 다루지만 `connectedDevDebugAndroidTest`까지 즉시 돌리기 어려운 PR이라면, 최소한 아래 focused JVM baseline은 함께 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.receiver.RoutineReceiverPolicyTest" \
  --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest"
```

- `RoutineReceiverPolicyTest`: 저장된 루틴 JSON decode, enabled routine 재예약 선택 로직의 회귀를 빠르게 잡는다.
- `EmergencyUnlockPolicyTest`: 긴급해제 만료/허용 판단의 순수 로직 회귀를 빠르게 잡는다.
- 이 baseline은 Android runtime 자체를 대체하지 않는다. 아래 시나리오 evidence 또는 `:app:connectedDevDebugAndroidTest`와 함께 해석한다.

### 공통 evidence 수집 팁

가능하면 각 시나리오 전후로 아래를 같이 남긴다.

```bash
adb shell dumpsys alarm | grep com.uiery.keep
adb logcat -d | grep -E "RoutineAlarmReceiver|BootReceiver|KeepAccessibilityService|EmergencyUnlock"
```

- `dumpsys alarm`: receiver 이후 다음 알람/루틴 재예약 여부를 남길 때 유용하다.
- `logcat`: 런타임 크래시/경고를 함께 남길 때 유용하다.
- 민감 로그 금지: `RoutineModel.toString()`처럼 루틴명, 시간대, 반복 요일, 차단 앱 package를 한 번에 노출하는 raw 모델 로그를 남기지 않는다. 토큰, device id, 긴급해제 상태도 raw logcat evidence에 남기기 전에 마스킹한다.
- 로그 태그나 출력은 빌드에 따라 충분하지 않을 수 있으므로, 스크린샷/시각/루틴 이름 같은 사용자 관찰 evidence를 같이 보관한다.

## 2. BootReceiver 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/receiver/BootReceiver.kt`
- `app/src/main/AndroidManifest.xml`

### 목적

부팅 후 저장된 루틴이 다시 스케줄링되어야 한다. 동일한 복구 계약은 앱 업데이트로 패키지가 교체된 뒤(`MY_PACKAGE_REPLACED`)에도 유지되어야 한다.

### 시나리오 A — cold boot / reboot

1. 루틴이 1개 이상 활성화된 상태를 만든다.
2. 앱을 완전히 종료한다.
3. 기기를 재부팅하거나, 에뮬레이터를 cold boot한다.
4. 부팅 후 앱을 열지 않은 상태에서도 다음 루틴 알림/스케줄이 유지되는지 확인한다.

> `BOOT_COMPLETED`는 protected broadcast라서 `adb shell am broadcast ...`만으로 안정적으로 재현되지 않을 수 있다. BootReceiver 검증은 실제 reboot/cold boot를 기준으로 남긴다.

### 시나리오 B — 앱 업데이트 후 복구

1. 활성 루틴 1개 이상을 만든다.
2. 업데이트 전 `adb shell dumpsys alarm | grep com.uiery.keep`로 예약 상태를 남긴다.
3. 같은 variant를 `adb install -r <apk>`로 덮어쓴다.
4. 업데이트 직후 앱을 열지 않은 상태에서 다음 루틴 예약이 유지되거나 즉시 재복구되는지 확인한다.
5. 필요하면 앱 실행 후 홈/루틴 화면에서 상태가 초기화되지 않았는지 확인한다.

### 확인 포인트

- [ ] `BOOT_COMPLETED` 이후 앱 크래시가 없다.
- [ ] `MY_PACKAGE_REPLACED` 이후에도 활성 루틴이 사라지지 않는다.
- [ ] 다음 루틴 시각에 맞는 알림/동작이 다시 예약된다.
- [ ] 재부팅/업데이트 직후 열어본 홈/루틴 화면에서 루틴 상태가 비정상으로 초기화되지 않는다.

### 실패 시 남길 evidence

- 기기/에뮬레이터 정보
- 재부팅 또는 업데이트 전후 루틴 이름/시간
- 전후 `adb shell dumpsys alarm | grep com.uiery.keep` 차이
- logcat 핵심 라인
- 실제 누락된 알림 또는 스케줄 증상

## 3. RoutineAlarmReceiver 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/receiver/RoutineAlarmReceiver.kt`

### 목적

루틴 시작 시 알림을 띄우고, 활성 루틴이면 다음 주차로 다시 예약해야 한다.

### 시나리오

1. 가까운 미래 시각에 반복 루틴을 만든다.
2. 대상 앱이 선택되어 있는지 확인한다.
3. 알림 시각까지 대기하거나 테스트 시간을 앞당겨 수신을 유도한다.
4. 알림 수신 직후 루틴이 다음 주차 기준으로 다시 예약되는지 확인한다.

권장 준비:

```bash
adb shell dumpsys alarm | grep com.uiery.keep
```

수신 전후의 예약 상태를 비교해 다음 회차가 실제로 다시 등록되었는지 남긴다.

### 확인 포인트

- [ ] 루틴 시작 알림이 정확한 루틴 이름으로 노출된다.
- [ ] 루틴이 enabled 상태면 다음 회차가 다시 예약된다.
- [ ] 루틴이 disabled 상태면 재예약되지 않는다.
- [ ] receiver 실행 후 중복 알림이 연속으로 뜨지 않는다.

### 실패 시 남길 evidence

- 루틴 ID/이름
- enabled 여부
- 기대한 알림 시각 vs 실제 시각
- 수신 전후 `adb shell dumpsys alarm | grep com.uiery.keep` 출력 차이
- 재예약 여부 스크린샷 또는 로그

### exact alarm permission 확인 포인트 (Android 12+)

1. exact alarm 권한을 `deny`로 둔 상태에서 활성 루틴 저장 또는 enable 시도를 한다.
2. 권한 안내 UI가 즉시 노출되는지 확인한다.
3. 루틴이 화면/DB 기준 `enabled=false`로 남고, `dumpsys alarm`에도 새 예약이 생기지 않는지 확인한다.
4. exact alarm 권한을 `allow`로 변경한다.
5. 같은 루틴을 다시 enable 하거나 새 활성 루틴을 저장한다.
6. 이번에는 `enabled=true` 상태와 실제 예약이 함께 생기는지 확인한다.

체크리스트:

- [ ] 권한 거절 상태에서 활성 루틴이 "성공처럼 보이지만 실제 미예약" 상태로 남지 않는다.
- [ ] 권한 거절 상태에서 사용자가 원인을 UI로 인지할 수 있다.
- [ ] 권한 허용 후에는 동일 루틴이 정상적으로 다시 예약된다.

## 4. KeepAccessibilityService 차단 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt`

### 목적

접근성 서비스가 저장된 잠금 상태와 루틴 상태를 반영해 실제 차단을 수행해야 한다.

### 현재 자동화 baseline

이 영역은 현재 두 층으로 evidence를 쌓는다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.service.KeepAccessibilityServiceBlockDecisionTest' \
  --tests 'com.uiery.keep.service.KeepAccessibilityServiceUninstallDetectionTest' \
  --tests 'com.uiery.keep.feature.menu.MenuViewModelTest'
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest
```

- `KeepAccessibilityServiceBlockDecisionTest`: manual keep / timed lock / routine / duplicate / emergency unlock 우회 판단을 순수 JVM 회귀로 빠르게 고정한다.
- `KeepAccessibilityServiceUninstallDetectionTest`: 앱 삭제 방지(`prevent_uninstall`)가 켜진 상태에서 self-uninstall surface를 가로채야 하는 package/text 판별 규칙을 JVM 회귀로 고정한다.
- `KeepAccessibilityServiceIntegrationTest`: 실제 AccessibilityService bind 이후 cross-app foreground 전환과 self-uninstall interception이 런타임에서 유지되는지 검증하는 focused runtime harness다.
- 현재 Android 15 emulator baseline은 실제 bind 후 다섯 핵심 시나리오를 반복 가능하게 검증한다: `selectedAppWithManualKeep_launchesBlockActivity`, `emergencyUnlockActive_keepsSelectedAppForegroundInsteadOfLaunchingBlockActivity`, `appInfoScreenWithPreventUninstallEnabled_staysVisibleBeforeDeleteConfirmation`, `uninstallAttemptWithPreventUninstallEnabled_dismissesDeleteSurface`, `uninstallAttemptWithPreventUninstallDisabled_keepsDeleteSurfaceVisible`.
- uninstall interception baseline은 **앱 정보 화면 단계에서는 너무 이르게 개입하지 않고**, 사용자가 실제 삭제 확인 surface를 연 뒤에만 dismissal이 일어나야 한다는 경계도 함께 고정한다.
- 이 harness는 `setUp()`에서 접근성 서비스의 초기 활성 상태를 저장하고, `tearDown()`에서 **원래 꺼져 있던 경우 다시 disabled 상태로 원복**해야 한다. 후속 instrumentation/수동 QA가 접근성 서비스 잔여 상태에 오염되지 않도록 이 cleanup 계약을 유지한다.

### Android 15 emulator instrumentation 메모

`KeepAccessibilityServiceIntegrationTest`를 Android 15 emulator에서 돌릴 때는 "토글은 켜졌지만 서비스 bind가 실제로 일어났는지"를 설정 값만 보고 추정하지 말고 아래 4가지를 같이 남긴다. 현재 baseline은 이 관측을 assertion message에 포함한 상태에서 실제 bind 성공까지 확인되도록 보강되었다.

```bash
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -n 'Bound services\|Enabled services\|Binding services\|Crashed services' -A1 -B1
adb logcat -d | grep -E 'KeepAccessibilityService|TestRunner|IPCThreadState|frozen process'
```

최근 qa-lane에서는 초기 run에서 `enabled_accessibility_services` 반영 뒤에도 `Bound services:{}` 상태와 `IPCThreadState: Sending oneway calls to frozen process.`가 보이는 bind 경계를 먼저 고정했고, 이후 harness를 보강해 **첫 테스트에서 실제 bind 성공, 후속 테스트에서는 연결 플래그를 유지한 채 emergency unlock safety 시나리오까지 연속 검증**하도록 안정화했다. 다시 유사 실패가 재발하면 PR/이슈 보고에는 **토글 반영과 실제 service bind를 분리해서** 적고, instrumentation assertion/message에도 동일 진단 정보를 남긴다.

### 홈 접근성 권한 경고 재동기화

정확한 권한 판별과 홈 화면 경고 다이얼로그 재동기화는 **자동 exact-match/unit baseline + 자동 settings-resume instrumentation + 필요 시 수동 shell evidence**로 확인한다.

자동 baseline:

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.util.ContextExtTest'
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest
```

- `ContextExtTest`: `enabled_accessibility_services`가 패키지 substring만 포함할 때는 실패하고, 실제 `KeepAccessibilityService` component exact match일 때만 통과해야 하며, Android 설정이 short class name(`com.uiery.keep/.service.KeepAccessibilityService`)으로 저장돼도 같은 서비스로 인식해야 함을 고정한다.
- `HomeAccessibilityPermissionIntegrationTest#fakePackageSubstringStillShowsAccessibilityPermissionDialogOnHome`: returning-user 홈 진입 상태에서 **가짜 package substring 서비스 문자열만 있는 경우에도** 홈 접근성 권한 경고 다이얼로그가 다시 보여야 함을 검증하는 초기 진입 baseline이다.
- `HomeAccessibilityPermissionIntegrationTest#returningFromAccessibilitySettingsResyncsHomePermissionDialogOnResume`: 접근성 설정 화면으로 나갔다가 `KeepAccessibilityService`를 끄고 돌아오면 홈 `ON_RESUME`에서 경고 다이얼로그가 즉시 다시 나타나야 함을 자동 검증한다.
- `HomeAccessibilityPermissionIntegrationTest#returningFromAccessibilitySettingsClearsHomePermissionDialogAfterReEnablingService`: 같은 설정 왕복에서 접근성 서비스를 다시 켠 뒤 앱으로 돌아오면 홈 `ON_RESUME`에서 경고 다이얼로그가 즉시 사라져야 함을 자동 검증한다.

필요 시 수동/shell evidence:

1. `IS_NEW=false` 상태(기존 사용자 홈 진입)로 앱을 연다.
2. Stopit 접근성 권한을 실제로 켠 뒤 홈 화면에서 경고 다이얼로그가 사라진 상태를 확인한다.
3. 홈에서 접근성 서비스 상세 설정으로 이동한다.
4. `Stopit/KeepAccessibilityService`를 끄고 앱으로 되돌아온다.
5. 다시 설정으로 이동해 `Stopit/KeepAccessibilityService`를 켜고 앱으로 되돌아온다.

확인:
- [ ] 홈으로 복귀한 직후 접근성 권한 경고 다이얼로그가 다시 나타난다.
- [ ] 접근성 서비스를 다시 켠 뒤 홈으로 복귀하면 경고 다이얼로그가 즉시 사라진다.
- [ ] `enabled_accessibility_services`에 `com.uiery.keep` substring이 들어 있더라도 실제 component exact match가 아니면 경고가 숨겨지지 않는다.
- [ ] short class name 형식(`com.uiery.keep/.service.KeepAccessibilityService`)도 동일 서비스로 인식한다.
- [ ] 홈 접근성 권한 경고가 권한 해제/재허용을 반영해 복귀 직후 최신 상태로 재동기화된다.

권장 evidence:

```bash
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -n 'Enabled services\|Bound services' -A1 -B1
```

- PR/이슈에는 가능하면 홈 복귀 직후 스크린샷 1장과 위 3개 명령의 출력 일부를 함께 남긴다.

### 시나리오 A — 수동 잠금

1. 접근성 권한을 켠다.
2. 차단 대상 앱을 1개 이상 선택한다.
3. 수동 잠금을 활성화한다.
4. 대상 앱을 연다.

확인:
- [ ] `BlockActivity`가 즉시 표시된다.
- [ ] 비대상 앱에서는 차단이 발생하지 않는다.
- [ ] 같은 앱 재진입 시 과도한 중복 차단/깜빡임이 없다.

### 시나리오 B — 시간 잠금

1. 가까운 미래까지 유지되는 timed lock을 설정한다.
2. 잠금 예약 직후, 아직 Lock 화면 종료 시점이 오기 전에 history/누적 시간이 증가하지 않았는지 확인한다.
3. 잠금 중 대상 앱을 연다.
4. 잠금 종료 후 같은 앱을 다시 연다.
5. 종료 후 history 상세와 누적 시간이 실제 종료된 세션 기준으로 한 번만 증가했는지 확인한다.

확인:
- [ ] 예약 직후에는 `lock_history`, `TOTAL_BLOCK_TIME`, `LONG_BLOCK_TIME`가 완료 세션처럼 선반영되지 않는다.
- [ ] Home timer 예약 시 `LOCK_TIME`에 저장된 deadline과 Lock 화면 route/countdown deadline이 동일하다. Bottom sheet hide / navigation 지연 또는 timer state 변경 때문에 `moveToLock()`이 deadline을 재계산하지 않는다.
- [ ] 잠금 시간 내에는 차단된다.
- [ ] 잠금 만료 후에는 정상 진입된다.
- [ ] 만료 직전/직후에 차단 상태가 뒤집히는 이상 동작이 없다.
- [ ] 만료 후에는 home timer 세션이 `isRoutine=false`로 한 번만 기록되고 duration은 실제 시작~종료 구간과 일치한다.

자동/scriptable baseline:

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest.moveToLockUsesTheSameDeadlinePersistedByLockTimeEvenIfTimerStateChangesBeforeNavigation" \
  --tests "com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest.lockTimeDoesNotPreRecordFutureTimerSessionInHistoryLedger" \
  --tests "com.uiery.keep.feature.lock.LockViewModelTest.completedHomeTimerRecordsHistoryLedgerAtLockCompletion"
```

### 시나리오 C — 루틴 차단

1. 현재 요일/시간에 활성화되도록 루틴을 만든다.
2. 대상 앱을 연다.

확인:
- [ ] 루틴 활성 구간에서만 차단된다.
- [ ] 루틴 비활성 구간에서는 차단되지 않는다.

### 시나리오 D — 다중 활성 루틴 겹침

1. 현재 시점에 동시에 활성화되도록 루틴 2개 이상을 만든다.
   - 예시:
     - 루틴 A: `com.instagram`, `com.youtube`
     - 루틴 B: `com.youtube`, `com.discord`
2. 접근성 차단이 켜진 상태에서 각 대상 앱을 순서대로 연다.
3. Lock 화면의 루틴 안내 문구와 긴급해제 바텀시트의 대상 앱 목록을 확인한다.
4. 루틴 종료 또는 긴급해제 후 잠금 기록을 확인한다.

확인:
- [ ] Lock 화면이 첫 번째 루틴 이름 하나만 잘못 보여주지 않고, 단일 활성 루틴이면 이름을, 다중 활성 루틴이면 개수 기반 문구를 보여준다.
- [ ] 실제 차단 대상 앱 집합이 활성 루틴들의 합집합(`com.instagram`, `com.youtube`, `com.discord`)과 일치한다.
- [ ] 긴급해제 대상 앱 목록이 첫 번째 루틴 기준으로 잘리지 않고 실제 차단 대상과 동일하다.
- [ ] 루틴 종료 후 `lock_history`의 `lockedApps`가 실제 차단 대상 합집합과 일치한다.
- [ ] 공통 앱(`com.youtube`)은 중복 없이 한 번만 취급된다.

권장 evidence:

```bash
adb logcat -d | grep -E "KeepAccessibilityService|LockViewModel|EmergencyUnlock"
```

- 가능하면 Lock 화면 스크린샷, 긴급해제 바텀시트 스크린샷, 종료 후 잠금 기록 스크린샷을 한 세트로 남긴다.

## 5. 긴급해제 만료 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt`
- `app/src/main/java/com/uiery/keep/service/EmergencyUnlockState.kt`

### 목적

긴급해제가 활성 앱에는 일시적으로 통과를 허용하되, 만료 후에는 차단이 복구되어야 한다.

### 시나리오

1. 차단 중인 앱에서 긴급해제를 실행한다.
2. 긴급해제 유효 시간 동안 대상 앱을 사용한다.
3. 만료 시각이 지난 뒤 같은 앱을 다시 전면으로 가져온다.

자동/scriptable baseline:

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest
```

- 위 baseline은 DataStore + `EmergencyUnlockState` + 재차단 대상 판정 contract를 실제 device/emulator에서 검증한다.
- 실제 blocked third-party app foreground 전환까지 포함한 end-to-end Accessibility 진입은 아래 수동 evidence로 따로 남긴다.

### 확인 포인트

- [ ] 긴급해제 유효 시간 동안 대상 앱이 차단되지 않는다.
- [ ] 만료 후 다시 앱 전면 진입 시 차단이 복구된다.
- [ ] 만료 후 데이터가 남아 차단이 계속 우회되지 않는다.
- [ ] 긴급해제와 무관한 다른 대상 앱은 계속 차단된다.

## 6. Backup / restore 후 런타임 상태 검증

관련 문서:
- `docs/BACKUP_RESTORE_POLICY.md`

### 목적

기기 이전/클라우드 복원 뒤에도 이전 기기의 DataStore 기반 잠금/긴급해제/리뷰/토큰 상태가 그대로 살아나지 않아야 한다.

### 시나리오

1. 기존 기기에서 아래를 모두 만든다.
   - 차단 앱 선택
   - 수동 잠금 또는 timed lock 활성화
   - 긴급해제 설정 변경
   - 가능하면 긴급해제 1회 실행
2. 백업/기기 이전 수행
3. 새 기기에서 앱 실행 직후 대상 앱과 루틴 화면을 확인한다.

### 확인 포인트

- [ ] 루틴은 필요 시 복원되지만, DataStore 기반 현재 잠금 상태는 그대로 살아나지 않는다.
- [ ] boot 또는 routine alarm 재진입 후 복원된 Room routine이 필요 시 비권위 `PreferencesKey.ROUTINES` 캐시로 다시 채워지더라도, 후속 스케줄링/차단 판단의 source of truth는 계속 Room이다.
- [ ] 이전 기기의 긴급해제 진행 상태가 복원되어 차단이 계속 우회되지 않는다.
- [ ] 선택 앱 목록/긴급해제 설정은 새 기기 기준으로 다시 설정해야 하는 상태다.
- [ ] 리뷰 프롬프트/토큰/세션성 플래그가 복원 직후 부자연스럽게 이어지지 않는다.

### 자동 baseline 명령

- `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest`

자동 baseline이 확인하는 것:
- `BackupRestoreRuntimeResetIntegrationTest`: restored-device shape(복원된 Room + 비어 있는 DataStore)에서 Boot/Routine alarm 진입 후 `PreferencesKey.ROUTINES` 재수화 + reset-only DataStore key 부재
- `ReceiverRuntimeIntegrationTest`: alarm/notification/reschedule contract
- `EmergencyUnlockExpiryIntegrationTest`: 긴급해제 만료 state cleanup + 재차단 대상 결정
- `KeepMessagingServiceIntegrationTest`: stale FCM token overwrite

## 7. Release 전 최소 QA 게이트

release PR 또는 internal 배포 전에는 아래를 모두 체크한다.

- [ ] `Branch Hygiene`
- [ ] `Version Guard`
- [ ] `Android CI`
- [ ] `Android Release Build`
- [ ] `:app:testDevDebugUnitTest` 또는 해당 PR의 focused JVM test 결과
- [ ] 가능하면 `:app:connectedDevDebugAndroidTest`, 불가하면 사유 기록
- [ ] 최소 focused automation evidence
  - [ ] `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - [ ] `com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest`
  - [ ] `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest`
  - [ ] `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest`
  - [ ] `com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
- [ ] backup/restore 정책을 건드린 PR이면 `docs/BACKUP_RESTORE_POLICY.md` 기준으로 restore/reset evidence 기록
- [ ] 아래 수동 runtime 시나리오 evidence
  - [ ] BootReceiver
  - [ ] RoutineAlarmReceiver
  - [ ] Accessibility 차단
  - [ ] 긴급해제 만료 end-to-end foreground 복귀
  - [ ] Backup / restore 후 runtime reset

## 8. PR에 남길 검증 기록 템플릿

```md
## Runtime QA evidence
- Device/Emulator:
- Build variant:
- Commands:
  - `./gradlew :app:testDevDebugUnitTest`
  - `./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.receiver.RoutineReceiverPolicyTest" --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest"`
  - `./gradlew :app:connectedDevDebugAndroidTest` (or blocked: reason)
- Manual scenarios:
  - BootReceiver: pass/fail
  - RoutineAlarmReceiver: pass/fail
  - Accessibility blocking: pass/fail
  - Emergency unlock expiry: pass/fail
  - Backup / restore runtime reset: pass/fail
- Notes:
```

## 9. 현재 한계

- 이 문서는 수동/반수동 기준선이다.
- `BootReceiver`와 `RoutineAlarmReceiver`는 `app/src/androidTest/java/com/uiery/keep/receiver/ReceiverRuntimeIntegrationTest.kt`로 최소 재수화/재예약 contract가 자동 검증된다.
- `app/src/androidTest/java/com/uiery/keep/qa/BackupRestoreRuntimeResetIntegrationTest.kt`는 복원된 Room + 비어 있는 DataStore shape에서 reset-only state가 되살아나지 않는 baseline을 고정한다.
- 여전히 실제 cold boot와 더 넓은 device/OEM별 Accessibility surface는 수동 또는 추가 automation 전략이 필요하다. 다만 Android CI focused runtime smoke는 `KeepAccessibilityServiceIntegrationTest`로 대표적인 cross-app foreground 전환과 self-uninstall interception safety baseline을 이미 자동 검증한다.
- 긴급해제 만료는 `app/src/androidTest/java/com/uiery/keep/service/EmergencyUnlockExpiryIntegrationTest.kt`로 state 정리와 재차단 대상 판정을 scriptable하게 검증하지만, 실제 third-party app foreground 전환까지 포함한 end-to-end evidence는 수동 시나리오를 함께 남기는 것이 안전하다.
- issue #27이 완전히 닫히려면 위 통합 테스트와 수동 QA 기준이 함께 유지되어야 한다.
