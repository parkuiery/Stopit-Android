# targetSdk 36 (Android 16) 이관

Google Play는 **2026-08-31**부터 신규 앱과 앱 업데이트에 `targetSdk 36`을 요구한다
(연장 신청 시 2026-11-01). Stopit은 그 전에 `targetSdk 35 -> 36`으로 올렸다.

이 문서는 **무엇을 바꿨고, 무엇을 확인했고, 무엇이 남았는지**를 기록한다. 특히
`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`는 API 37에서 사라지는 한시적 opt-out이라
제거 조건을 여기에 명시해 둔다.

## 바꾼 것

| 파일 | 변경 |
| --- | --- |
| `app/build.gradle.kts` | `compileSdk 35 -> 36`, `targetSdk 35 -> 36` |
| `core/kds/build.gradle.kts` | `compileSdk 35 -> 36` |
| `app/src/main/AndroidManifest.xml` | `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY = true` 추가 |

`minSdk 33`, AGP `8.10.1`, Kotlin `2.1.10`, Gradle wrapper는 건드리지 않았다. 이 이관은
Play 요구사항을 맞추는 최소 변경이고, AndroidX/AGP/Kotlin 승격은 별도 lane이다.
compileSdk 36으로 hold 사유가 풀린 AndroidX batch의 재승격은 **#1173**에서 다룬다
(`docs/DEPENDENCY_LINT_MAINTENANCE.md`의 AndroidX compileSdk / AGP boundary guard 참조).

## Android 16 동작 변경 대조

`targetSdk 36` 이상에만 적용되는 항목을 Stopit 코드 기준으로 확인한 결과다.

| 동작 변경 | Stopit 영향 | 처리 |
| --- | --- | --- |
| Edge-to-edge opt-out 제거 (`windowOptOutEdgeToEdgeEnforcement` 무효화) | 없음 | `MainActivity` / `BlockActivity`가 이미 `enableEdgeToEdge()`를 호출하고, opt-out 속성을 쓴 적이 없다. 인셋은 `statusBarsPadding` / `navigationBarsPadding` / `imePadding`로 화면별 처리 중. |
| Predictive back 기본 활성화 (`onBackPressed()` 미호출, `KEYCODE_BACK` 미전달) | 없음 | 뒤로가기는 전부 AndroidX `BackHandler`(= `OnBackPressedDispatcher`)로만 처리한다. `onBackPressed()` 오버라이드나 `KeyEvent` 직접 처리는 없다. |
| 대형 화면에서 orientation / resizability / aspect ratio 제한 무시 (sw>=600dp) | **있음** | `MainActivity`가 `screenOrientation="portrait"`다. 아래 opt-out으로 API 36 동안 유지. |
| `elegantTextHeight` 무효화 | 사실상 없음 | 영향받는 스크립트(아랍/라오/미얀마/타밀)를 쓰지 않고, 텍스트는 Compose 타이포그래피로 그린다. |
| `scheduleAtFixedRate` 누락 실행 1회로 축소 | 없음 | `java.util.Timer` / `ScheduledExecutor` 미사용. 루틴 예약은 `AlarmManager` 기반. |
| Health/Fitness 권한 세분화 (`BODY_SENSORS` 대체) | 없음 | 센서 권한 미사용. |
| MediaStore 버전 잠금, Safer Intents(`intentMatchingFlags`), GPU syscall 필터링 | 없음 | 해당 API 미사용. Safer Intents는 opt-in이라 별도 조치 없음. |
| Local network 제한(`RESTRICT_LOCAL_NETWORK`) | 지금은 없음, **감시 대상** | 25Q2~26Q2 구간에서는 opt-in이라 `targetSdk 36`만으로 적용되지 않는다. 다만 `KeepDnsVpnService`가 로컬 DNS를 다루므로, 이 제한이 기본 적용으로 바뀌는 플랫폼 릴리즈에서 재검토한다. |

## `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` 제거 조건

```xml
<property
    android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
    android:value="true" />
```

- **왜 넣었나.** Stopit 화면은 세로 기준으로만 설계·검증돼 있다. API 36에서 sw>=600dp 화면의
  `screenOrientation="portrait"`가 무시되면 태블릿·폴더블에서 가로 레이아웃이 그대로 노출되는데,
  그 상태의 근거가 없다. Play 데드라인을 맞추면서 검증 안 된 레이아웃을 내보내지 않기 위한
  한시적 opt-out이다.
- **언제 사라지나.** 이 property는 **API 37에서 제거된다.** 그때는 opt-out 자체가 무효가 된다.
- **제거 전 충족 조건.**
  1. 대형 화면(sw>=600dp) 가로 레이아웃 대응. 최소한 홈 타이머, 카테고리 선택, 루틴 편집,
     잠금/차단 화면이 가로에서 잘리지 않아야 한다.
  2. sw>=600dp 에뮬레이터 또는 실기기에서 가로/세로 회전 및 폴더블 접기·펼치기 검증 증적.
  3. 잠금 중 회전이 `BackHandler` 기반 차단 화면의 잠금 유지를 깨지 않는지 확인.
- 조건을 채우면 이 property와 `MainActivity`의 `screenOrientation="portrait"`를 같이 걷어낸다.

## 검증 증적

### JVM / lint / 정적 게이트 (로컬)

- `./gradlew :app:testDevDebugUnitTest :app:lintDevDebug :app:assembleProdDebug` -> BUILD SUCCESSFUL
- `./gradlew :app:testProdReleaseUnitTest :app:lintProdRelease` -> BUILD SUCCESSFUL
- `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` -> 568 tests OK
- `python3 scripts/verify_lint_registry.py --report app/build/reports/lint-results-devDebug.html ...` -> passed
- 병합 매니페스트에 `android:targetSdkVersion="36"`과 opt-out property가 모두 존재
- `zipalign -c -P 16 -v 4 app-prod-debug.apk` -> Verification successful (16 KB 페이지 정렬 유지)

### 런타임 검증 (Android 16 / API 36 에뮬레이터)

기기: 새로 만든 AVD `medium_phone` (android-36 `google_apis_playstore` arm64, 1080x2400 @420dpi).
기존 `Pixel_6a` AVD는 `/data`가 94% 차서 `INSTALL_FAILED_INSUFFICIENT_STORAGE`로 설치가 되지 않아
별도 AVD를 만들어 사용했다. Android 16 실기기는 없었으므로 아래는 전부 에뮬레이터 증적이다.

- `android_ci_focused_runtime_smoke` 24개 selector 완주. 23개 즉시 통과.
  `KeepAccessibilityServiceIntegrationTest#selectedAppWithManualKeep_launchesBlockActivity`가
  1회 실패했으나, 같은 기기에서 **단독 재실행 통과** + **클래스 전체 16/16 통과**로 flake로 확인했다. 같은 현상이 이미 **#1147**에 기록돼 있고, Android 16 재현 증적도 그쪽에 남겼다.
  실패 지점은 DataStore 상태가 AccessibilityService로 전파되기를 기다리는
  `waitForServiceToObserveSelectedPackage`이며 targetSdk 36 고유 동작과 무관하다.
- `BlockScreenContentIntegrationTest` + `LockScreenLayoutTest` 7/7 통과.
- PackageManager 기준 설치 상태: `versionCode=39 minSdk=33 targetSdk=36`.

#### 대형 화면 orientation opt-out (직접 확인)

`wm density 240`으로 화면을 sw=720dp(가로 1600dp)로 만든 뒤 `user_rotation 1`로 가로 고정.

- 앱이 **세로 레터박스를 유지**했고, 시스템이 "Double-tap to move this app" 레터박스 안내를 좌우에 표시했다.
- opt-out이 없었다면 API 36은 activity가 가로 창을 채우도록 강제했을 것이므로, property가 실제로 먹고 있다.
- 확인 후 `wm density reset` / `user_rotation 0` / `accelerometer_rotation 1`로 복구했다.

#### edge-to-edge

세로 기본 밀도에서 온보딩 화면 캡처. 콘텐츠가 상태 바 뒤까지 그려지고, 하단 액션 바는
제스처 내비게이션 바를 침범하지 않는다.

#### predictive back

- logcat: `CoreBackPreview ... com.uiery.keep.dev/androidx.activity.ComponentActivity: Setting back callback OnBackInvokedCallbackInfo{...}`
  -> 앱이 플랫폼 `OnBackInvokedCallback`을 실제로 등록한다.
- 온보딩에서 한 단계 전진 후 `input keyevent KEYCODE_BACK` -> 이전 화면으로 복귀,
  `MainActivity`가 계속 resumed. `onBackPressed()` 미호출/`KEYCODE_BACK` 미전달 환경에서도
  AndroidX `BackHandler` 경로가 살아 있다.

### 런타임 검증 (Android 15 / API 35 실기기)

기기: Samsung Galaxy S21 (`SM-G991N`), Android 15 / API 35. targetSdk 36 고유 동작은
Android 16에서만 나타나므로 이 기기는 **회귀 없음 확인용**이다.

- dev 플레이버 설치 성공, PackageManager 기준 `targetSdk=36`.
- 앱 실행 후 온보딩 화면 정상 렌더링, crash 버퍼 비어 있음.
- edge-to-edge 인셋 정상(하단 액션 바가 내비게이션 바를 침범하지 않음).

### 남은 실기기 검증

에뮬레이터로는 신뢰도가 낮거나 재현할 수 없는 항목이다. Android 16 실기기가 확보되면 확인한다.

- 실제 잠금/차단 상태에서 predictive back 제스처(스와이프)로 빠져나갈 수 없는지 (`LockScreen`, `BlockScreen`)
- `KeepAccessibilityService` 기반 앱 차단이 Android 16 실기기에서 그대로 동작하는지
- 웹사이트 차단 VPN(`KeepDnsVpnService`) 포그라운드 서비스가 Android 16 실기기에서 유지되는지
- 실제 태블릿/폴더블에서 레터박스 표시가 수용 가능한지 (opt-out 제거 판단 근거)
