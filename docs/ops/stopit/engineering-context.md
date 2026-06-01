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
- 오래된 dependency/lint baseline drift
- DataStore/Room/analytics contract drift
- 너무 큰 리팩터링은 작은 실행 단위로 쪼갠다.

Build/Release Maintenance Analyst가 우선 볼 신호:
- Gradle/AGP/Kotlin/Firebase/Room 업데이트 리스크
- flavorless command drift
- Play deploy secret/config 문서와 workflow 일치 여부
- versionCode/versionName guardrail
- CI/CD workflow separation 유지 여부

## 위험 영역

변경 시 QA 기준을 높인다:
- 접근성 서비스와 앱 차단 동작
- 긴급해제, countdown, notification
- 권한 요청/권한 거절 흐름
- Room migration and schemas
- DataStore에 저장되는 lock/emergency/runtime state
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
- `docs/PLAY_DEPLOYMENT.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
