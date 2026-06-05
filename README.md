# Keep (StopIt · 스탑잇)

> **스탑잇 - 집중 앱 차단, 루틴 잠금**
> 유혹 앱을 선택하고, 타이머·루틴으로 잠그고, 필요할 때만 긴급 해제하세요.

- **Package**: `com.uiery.keep`
- **사용자 노출명**: StopIt / 스탑잇
- **Platform**: Android (minSdk 33, targetSdk 35)
- **현재 버전**: 1.7.6 (versionCode 28)

## 서비스 소개

**스탑잇(StopIt)** 은 공부, 업무, 수면 전처럼 집중이 필요한 시간에 유혹 앱 사용을 바로 막아주는 Android 앱입니다.

단순히 사용 시간을 알려주는 알림형 앱과 달리, 스탑잇은 사용자가 **직접 선택한 앱이 실행되는 순간을 감지해 즉시 차단 화면을 표시**합니다. "설정만 해두고 실제로는 안 막히는" 앱이 아니라, 집중이 깨지는 바로 그 순간에 앱 사용을 끊어주는 데 초점을 맞췄습니다.

의지력에만 기대는 대신 타이머·루틴 같은 자동 잠금과 잠금 기록을 통해 집중 습관을 쌓도록 설계되었으며, "무조건 막기"의 부담을 덜기 위해 제한된 **긴급 해제**라는 안전장치를 함께 제공합니다.

### 이런 분에게 맞아요

- 공부를 시작하면 SNS·영상 앱부터 열게 되는 분
- 업무 시간에 메신저·커뮤니티·쇼핑 앱 사용을 줄이고 싶은 분
- 수면 전 특정 앱 사용을 제한하고 싶은 분
- 완전 삭제 대신, 필요한 시간에만 확실하게 차단하고 싶은 분

### 사용 흐름

1. 자주 열어버리는 **유혹 앱을 선택**합니다.
2. **타이머 잠금** 또는 **루틴 잠금**을 설정합니다.
3. 실제로 앱을 열었을 때 **차단 화면이 동작**하는지 확인합니다.
4. **잠금 기록**을 보며 집중 시간을 점차 늘려갑니다.

### 스탑잇이 다른 점

- 단순 알림 앱이 아니라 **실제 차단 동작**에 집중합니다.
- "무조건 막기"보다 **긴급 해제** 같은 안전장치를 함께 제공합니다.
- 루틴, 기록, 차단 성공 경험을 통해 **집중 습관**을 쌓도록 설계했습니다.

## 주요 기능

- **유혹 앱 선택 차단** – `KeepAccessibilityService`가 윈도우 전환을 감지해, 선택한 차단 대상 앱이 실행되면 즉시 차단 화면을 띄웁니다.
- **타이머 잠금** – 홈 화면에서 지금부터 몇 분/몇 시간 동안 앱 사용을 막는 집중 세션을 바로 시작합니다.
- **루틴 잠금** – 요일과 시간대를 정해 반복 잠금 스케줄을 자동화합니다.
- **긴급 잠금 해제** – 정말 필요할 때만 제한된 횟수·시간으로 임시 해제하며, 카운트다운 알림으로 진행 상태를 안내합니다.
- **잠금 기록(History)** – 얼마나 자주, 얼마나 오래 집중을 유지했는지 이력으로 확인합니다.
- **루틴 보호 / 삭제 방지** – 잠금 중 앱 삭제 시도를 감지해 잠금 우회를 줄이는 보호 장치를 제공합니다.
- **온보딩** – 인트로, 알림 권한, 앱 선택, 접근성 권한까지의 다단계 진입 플로우.

## 권한 및 개인정보 정책

스탑잇은 사용자가 직접 선택한 앱의 실행을 감지하고, 수동·타이머·루틴 잠금이 활성화된 동안 차단 화면을 표시하기 위해 **접근성(Accessibility) 권한**을 사용합니다. 잠금 우회를 줄이기 위해 잠금 중 앱 삭제 시도를 감지할 수 있습니다.

이 권한은 **앱 차단 및 잠금 유지라는 핵심 기능에만** 사용되며, 광고·프로파일링·데이터 판매·제3자 공유 목적으로는 사용되지 않습니다.

## 기술 스택

| 영역 | 사용 기술 |
|------|-----------|
| 언어 | Kotlin 2.1.0 (JVM target 17) |
| UI | Jetpack Compose + Material 3 |
| 아키텍처 | MVI (Orbit MVI 9.0.0) |
| 내비게이션 | Navigation Compose (type-safe routes) |
| DI | Hilt 2.56.1 |
| 로컬 DB | Room 2.7.1 |
| 환경설정 저장 | DataStore 1.1.2 |
| 백엔드/서비스 | Firebase (Analytics, Crashlytics, Messaging) |

> 1st-party Retrofit/OkHttp API 레이어는 제거되었으며, 현재 빌드에 `BASE_URL`은 필요하지 않습니다.

## 모듈 구조

```text
Keep-Android/
├── app/          # 메인 애플리케이션 모듈
└── core/
    └── kds/      # 디자인 시스템 (KeepButton, KeepCheckbox, KeepSnackBar 등 + KeepTheme)
```

### 빌드 변형 (Flavors)

`server` 디멘션 아래 두 가지 플레이버를 제공합니다.

- `dev` – 개발용
- `prod` – 프로덕션 / CI smoke 아티팩트

## 아키텍처

각 기능은 Orbit MVI 패턴을 따릅니다.

- `ViewModel`은 `ContainerHost`를 확장한 MVI 컨테이너입니다.
- `UiState` data class로 상태를 표현합니다.
- `SideEffect` sealed class로 일회성 이벤트를 처리합니다.
- intent 메서드에서 `intent { reduce { } }`로 상태를 변경하고 `postSideEffect()`로 사이드 이펙트를 발행합니다.

기능 단위 코드는 `app/src/main/java/com/uiery/keep/feature/` 아래에 위치하며, 각 기능은 보통
`{Feature}Screen.kt`, `{Feature}ViewModel.kt`, `{Feature}Navigation.kt`, `component/` 구조를 가집니다.

### 데이터 레이어

- **Room** (`database/`) – `KeepDatabase` (version 4), `RoutineEntity` / `LockHistoryEntity` / `EmergencyUnlockEntity` 및 DAO, 타입 컨버터.
- **DataStore** (`datastore/`) – 세션 데이터 저장 (IS_KEEP, LOCK_TIME, FCM_TOKEN 등). 키는 `PreferencesKey.kt`에 정의.

### 핵심 서비스

- `KeepAccessibilityService` – 윈도우 변경 감지 및 앱 차단 트리거
- `KeepMessagingService` – FCM 푸시 알림 처리
- `EmergencyUnlockNotificationHelper` – 긴급 잠금 해제 카운트다운 알림
- `EmergencyUnlockState` – AccessibilityService 즉시 동기화를 위한 in-memory 싱글톤

## 빌드 및 실행

> 앱 모듈에 `dev` / `prod` 플레이버가 정의되어 있으므로, `assembleDebug`처럼 플레이버 없는 명령은 모호하여 사용하지 않습니다.

```bash
# 빌드
./gradlew :app:assembleDevDebug       # 로컬 dev debug APK
./gradlew :app:assembleProdDebug      # prod debug APK / CI smoke 아티팩트
./gradlew :app:bundleProdRelease      # 프로덕션 App Bundle

# 테스트
./gradlew :app:testDevDebugUnitTest         # 기본 로컬 JVM 테스트
./gradlew :app:testProdReleaseUnitTest      # 릴리즈 경로 JVM 테스트
./gradlew :app:connectedDevDebugAndroidTest # 계측(instrumented) 테스트

# 클린 빌드
./gradlew clean :app:testDevDebugUnitTest :app:assembleProdDebug
```

### 릴리즈 서명

릴리즈 서명은 다음 환경 변수로 구성됩니다. 모두 설정된 경우에만 release 서명 설정이 적용되며, 그렇지 않으면 debug 서명으로 폴백합니다.

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`local.properties`는 로컬 Android/Gradle 환경 값 전용이며, 백엔드 URL이나 모니터링 값은 필요하지 않습니다.

## 빠른 문서 진입점

### 제품 / 지표 / 운영

- `docs/PRODUCT_METRICS_DASHBOARD.md` — North Star, 입력/건강/비즈니스 지표, 우선순위 기준
- `docs/METRICS_ANALYSIS.md` — GA4/Play/수익 지표 해석과 이슈화 절차
- `docs/ANALYTICS_EVENT_DICTIONARY.md` — 이벤트/파라미터 canonical 계약
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` — GA4 Admin custom definition 등록, metadata 확인, 14일 재측정 운영 런북
- `docs/ops/stopit/README.md` — Stopit cron / lane / context pack 진입점

### 활성화 / 리뷰 / 수익화 후속 문서

- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md` — 첫 잠금 활성화 퍼널 계약과 CTA/측정 가드레일
- `docs/REVIEW_PROMPT_LIFECYCLE.md` — 리뷰 프롬프트 eligibility / analytics / post-release 확인 절차
- `docs/ADMOB_MONETIZATION_RUNBOOK.md` — 광고 단위 감사, guardrail, 안전한 수익화 실험 런북

### 릴리즈 / QA / 배포

- `docs/GIT_WORKFLOW.md` — 브랜치/커밋/릴리즈 작업 흐름
- `docs/RELEASE_CHECKLIST.md` — release/hotfix PR 체크리스트
- `docs/PLAY_DEPLOYMENT.md` — Play internal/production 운영 기준
- `docs/QA_RUNTIME_CHECKLIST.md` — Android runtime / receiver / accessibility / notification QA 기준
- [DESIGN.md](DESIGN.md) – UI/디자인 컨트랙트
- [KDS 디자인 시스템](core/kds/README.md) – 컴포넌트 및 테마
- [`docs/`](docs/) – 분석, 메트릭, ASO, 운영 런북 등 추가 문서

## 현재 analytics 해석 주의

- `customUser:routines_count` 조회 가능만으로 GA4 queryability가 해결된 것으로 보면 안 됩니다.
- `routines_count` 자체도 조회 가능성과 coverage를 분리합니다. #479 / `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md` 완료 전에는 `(not set)` activeUsers를 무루틴 사용자로 합산하지 않습니다.
- `customEvent:*` 차원/지표가 GA4 Admin에 실제 등록되기 전까지 activation / review / monetization 세부 파라미터 해석 confidence는 낮게 유지해야 합니다.
- `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`은 기본적으로 no-data가 아니라 **GA4 Admin registration gap** 신호로 해석합니다.
- queryability follow-through의 현재 source of truth는 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`입니다.
