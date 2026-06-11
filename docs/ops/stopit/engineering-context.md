# Stopit Engineering Context

## 저장소와 앱 구조

저장소:
- GitHub: `parkuiery/Stopit-Android`
- 로컬 경로: `/Users/uiel/Desktop/git/Keep-Android`

앱:
- Android package: `com.uiery.keep`
- Kotlin Android app for screen-time management and app blocking
- 주요 모듈:
  - `app/`: main Android app
  - `core/kds/`: Compose design system
  - `functions/`: Firebase Functions TypeScript integrations
  - `docs/`: 운영/제품/릴리즈 문서

아키텍처:
- Kotlin, JVM target 17
- Jetpack Compose + Material 3
- Orbit MVI
- Hilt
- Room
- DataStore
- Firebase Analytics, Crashlytics, Messaging

주요 feature path:
- `app/src/main/java/com/uiery/keep/feature/onboarding/`
- `app/src/main/java/com/uiery/keep/feature/home/`
- `app/src/main/java/com/uiery/keep/feature/routine/`
- `app/src/main/java/com/uiery/keep/feature/lock/`
- `app/src/main/java/com/uiery/keep/feature/history/`
- `app/src/main/java/com/uiery/keep/feature/menu/`
- `app/src/main/java/com/uiery/keep/analytics/`

주요 서비스:
- `KeepAccessibilityService`: 앱 차단 트리거
- `KeepMessagingService`: FCM 처리
- `EmergencyUnlockNotificationHelper`: 긴급해제 카운트다운 알림
- `EmergencyUnlockState`: AccessibilityService 동기화를 위한 인메모리 상태

## Flavor와 Gradle 주의사항

Flavor dimension: `server`
- `dev`
- `prod`

중요:
- flavorless Gradle task는 애매하거나 실패할 수 있다.
- 가능하면 variant-specific task를 사용한다.
- focused JVM test 예: `./gradlew :app:testDevDebugUnitTest --tests '...'`
- release 검증 예: `./gradlew :app:testProdReleaseUnitTest`

주의할 문서 drift:
- 일부 오래된 AGENTS/docs에 `testDebugUnitTest` 같은 flavorless 명령이 남아 있을 수 있다.
- 새 운영 문서/cron은 variant-specific 명령을 우선한다.

## 테스트 전략

작은 코드 변경:
1. 가능하면 failing test 먼저 작성한다.
2. 좁은 variant-specific test로 실패를 확인한다.
3. 최소 구현 후 같은 test를 재실행한다.
4. 변경 범위가 넓으면 관련 broader task를 추가한다.

Android framework, Room migrations, services, receivers, permissions 영향:
- 가능하면 `connectedAndroidTest` 또는 관련 instrumentation test를 고려한다.
- 시간이 오래 걸리면 이슈/PR에 수동 검증 필요성을 명시한다.

Docs-only/PM-ops 변경:
- 억지로 앱 테스트를 만들지 않는다.
- 문서 read-back, 링크 검증, 저렴한 sanity check를 사용한다.

## 건강도 분석 신호

Bug Scout / QA Analyst가 우선 볼 신호:
- `TODO|FIXME|HACK`
- 최근 GitHub Actions 실패
- lint warnings
- manifest-declared service/receiver/provider와 test coverage gap
- AccessibilityService, lock screen, emergency unlock, permissions, notification, DataStore/Room migration 영향
- crash-prone null/state transition paths

Tech Debt / Architecture Analyst가 우선 볼 신호:
- 중복된 ViewModel/UI state pattern
- feature boundary 위반
- KDS로 내려갈 수 있는 반복 UI
- 공유 UI 소유권 drift: feature A가 feature B의 `component` package를 직접 import하거나, app shared UI/KDS로 승격된 component가 feature-private duplicate로 남는 경우. #492 source of truth는 `docs/SHARED_UI_OWNERSHIP_BOUNDARY.md`이며, `PermissionSettingDialog`와 `TimerPicker` 현 정리 대상은 code-lane에서 shared boundary + static guard로 닫아야 한다.
- feature domain boundary drift: `database/`, `service/`, `receiver/`, `analytics/` production source가 `feature.*` domain/repository/read-model 타입을 직접 import하는 경우. #651 source of truth는 `docs/FEATURE_DOMAIN_BOUNDARY.md`이며, #520의 `docs/DAO_BOUNDARY_MAINTENANCE.md` DAO 직접 의존 정리 다음 단계로 shared domain/data boundary migration + static inventory guard를 따른다.
  - 현재 장기 경계: analytics는 `RepeatBlockRoutineSuggestionAnalyticsPayload` DTO를 통해 feature-local suggestion object를 받지 않고, LockHistory runtime recording은 `LockHistorySessionWriter` data boundary가 소유한다. ParentMode runtime session/state와 block decision policy는 `domain.parentmode` boundary가 소유한다. 이 경계들은 새 PR에서 다시 feature-private 모델/저장소로 되돌리지 않는다.
  - 남은 migration 축은 `docs/FEATURE_DOMAIN_BOUNDARY.md`의 current inventory를 기준으로 판단한다. GoalLock shared repository/data boundary, ParentMode session store migration은 code-lane/merge-controller가 fresh-base로 줄여야 하며, docs-lane은 open code PR이 같은 source-of-truth 문서를 만지고 있으면 중복 docs PR을 만들지 않는다.
- 오래된 dependency/lint baseline drift
- DataStore/Room/analytics contract drift
- 너무 큰 리팩터링은 작은 실행 단위로 쪼갠다.

Build/Release Maintenance Analyst가 우선 볼 신호:
- Gradle/AGP/Kotlin/Firebase/Room 업데이트 리스크
- flavorless command drift
- dev/prod `applicationId` / package identity drift; #314 계열 작업은 `docs/FLAVOR_APPLICATION_ID_CONTRACT.md`를 먼저 확인한다.
- Play deploy secret/config 문서와 workflow 일치 여부
- helper 범위(`scripts/setup-play-deploy-secrets.sh` vs `scripts/setup-discord-deploy-secrets.sh`)와 `GOOGLE_SERVICES_JSON` restore matrix가 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`와 어긋나지 않는지
- versionCode/versionName guardrail
- CI/CD workflow separation 유지 여부

## 위험 영역

변경 시 QA 기준을 높인다:
- 접근성 서비스와 앱 차단 동작
- 긴급해제, countdown, notification
- 권한 요청/권한 거절 흐름
- Room migration and schemas
- DataStore에 저장되는 lock/emergency/runtime state
- RoutineStore / `PreferencesKey.ROUTINES` legacy compatibility cache drift. Room is the authoritative routine source; #511 source of truth is `docs/ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md`.
- backup/restore policy (`allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml`)
- analytics event names/parameters used by dashboard
- release signing and Play upload workflows

## Secret / 로컬 파일 규칙

절대 커밋/출력하지 않는다:
- `local.properties` 민감 변경
- service account JSON private key
- keystore/password
- `google-services.json` secret-sensitive content unless explicitly intended
- generated AAB/APK artifacts

## 관련 문서

- root `AGENTS.md`
- `app/AGENTS.md`
- `docs/DEPENDENCY_LINT_MAINTENANCE.md`
- `docs/GIT_WORKFLOW.md`
- `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`
- `docs/PLAY_DEPLOYMENT.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md`
