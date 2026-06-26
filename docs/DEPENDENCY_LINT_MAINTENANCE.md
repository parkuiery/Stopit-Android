# Stopit 의존성 업그레이드 / lint 유지보수 런북

이 문서는 GitHub issue #28 `[maintenance] Gradle·AndroidX·Firebase 의존성 업그레이드와 lint 경고 기준선 정리`를 위한 **문서/운영 slice**다.

목표는 한 번에 모든 버전을 올리는 것이 아니라, Stopit 저장소에서 의존성 드리프트와 lint 경고를 **같은 방식으로 반복 점검하고 작은 배치로 처리하는 기준**을 만드는 것이다.

> 이 문서만으로 issue #28이 닫히지는 않는다. 실제 버전 업그레이드와 lint 경고 감소는 후속 code/maintenance PR에서 수행한다.

## 언제 이 문서를 쓰는가

다음 상황에서 기본 런북으로 사용한다.

- `:app:lintDevDebug`가 `NewerVersionAvailable` 또는 `ObsoleteLintCustomCheck`를 보고할 때
- AGP / Kotlin / Compose / Firebase / Room / Ads 버전 드리프트를 정리할 때
- 버전 카탈로그(`gradle/libs.versions.toml`)와 실제 앱 의존성 선언이 따로 놀기 시작할 때
- “어떤 업그레이드를 한 PR에 묶고, 무엇을 다음 배치로 미룰지” 결정해야 할 때

## Source of truth

현재 저장소에서 먼저 보는 파일:

- 버전 카탈로그: `gradle/libs.versions.toml`
- 앱 의존성 선언: `app/build.gradle.kts`
- 루트 Gradle plugin 선언: `build.gradle.kts`
- KDS 의존성 선언: `core/kds/build.gradle.kts`
- lint 보고서 출력 위치: `app/build/reports/lint-results-devDebug.txt`
- flavor-aware 기본 검증 명령: `docs/GIT_WORKFLOW.md`
- ops 컨텍스트: `docs/ops/stopit/engineering-context.md`

주의:

- 이 저장소는 버전 카탈로그를 **원칙적인 source of truth**로 둔다.
- dependency와 Gradle plugin 드리프트를 함께 봐야 한다.
- Room library/runtime/compiler/testing과 Room Gradle plugin은 같은 `room` version ref를 쓴다.
- Firebase Crashlytics Gradle plugin은 `firebaseCrashlytics` version ref를 쓴다. `scripts.tests.test_gradle_version_catalog_contract`는 key/alias/ref 계약을 고정하되 `3.0.6` 같은 특정 patch 값은 고정하지 않는다. Dependabot patch bump는 catalog alias/ref가 유지되면 통과해야 한다.
- 앱/루트 Gradle 파일에 새 직접 버전 문자열이 들어오면, 먼저 `gradle/libs.versions.toml` alias로 이동할 수 있는지 확인한다.
- 2026-06-01 기준 `core/kds/build.gradle.kts`의 기존 direct version drift는 catalog alias로 이동됐다.
  - `org.jetbrains.kotlinx:kotlinx-datetime:0.6.1` → `libs.kotlinx.datetime`
- 2026-06-07 기준 AdMob SDK 런타임 의존(`libs.google.play.services.ads`)과 banner lifecycle code는 KDS에서 제거하고 앱 monetization/analytics 경계로 고정했다.
- 2026-06-02 기준 KDS modal bottom sheet의 deprecated Accompanist `SystemUiController` 의존성은 제거됐다.
  - 앱 entry point의 `androidx.activity.enableEdgeToEdge()`와 Material3 `ModalBottomSheet`/insets 계약을 source of truth로 둔다.
  - `app`/`core:kds`에 `libs.accompanist.systemuicontroller`, `rememberSystemUiController`, 또는 `accompanist-systemuicontroller`가 재유입되면 `scripts.tests.test_kds_dependency_catalog_contract`가 실패해야 한다.
- `gradle/libs.versions.toml`에는 앱/KDS에서 쓰는 의존성 alias가 남아 있어야 하며, `scripts.tests.test_kds_dependency_catalog_contract`가 direct-version drift와 deprecated Accompanist 재유입을 막는다.
- KDS가 AdMob SDK를 다시 소유하지 않도록 `scripts.tests.test_kds_admob_boundary`가 `core/kds/src/main`과 `core/kds/build.gradle.kts`를 감시한다.
- 따라서 드리프트 점검은 `libs.versions.toml`만 읽고 끝내면 안 되고, `build.gradle.kts`, `app/build.gradle.kts`, `core/kds/build.gradle.kts`, `core/kds/src/main/java`를 같이 확인해야 한다.

### version catalog 정책 메모

- 기본 규칙: **라이브러리/플러그인 버전은 가능하면 `gradle/libs.versions.toml`을 source of truth로 둔다.**
- 예외가 남아 있으면 direct version을 유지하는 이유(예: alias 부재, stack 정렬 보류, 런타임 리스크)를 문서나 이슈에 남긴다.
- `app`만 보고 source-of-truth drift를 판단하지 않는다. `:core:kds` 같은 공유 모듈도 같은 규칙으로 본다.

## 유지보수 원칙

1. **제품 lint와 버전 드리프트를 분리한다.**
   - 실제 버그 가능성이 있는 lint와 “더 최신 버전이 있음” 경고는 같은 우선순위가 아니다.
2. **한 PR에 한 종류의 위험만 묶는다.**
   - 예: Firebase BoM patch + 관련 plugin patch
   - 비추천: AGP, Kotlin, Compose, Room, Ads를 한 번에 모두 올리기
3. **stack upgrade는 순서를 지킨다.**
   - AGP / Kotlin / Compose compiler 조합은 먼저 호환성을 확인한다.
4. **문서 PR은 기준을 만들고, code PR은 실제 버전을 올린다.**
   - 이 문서는 runbook이고, 실제 업그레이드 증거는 후속 PR에서 남긴다.
5. **Stopit은 flavor가 있으므로 lint/test/build 명령도 variant-specific로 유지한다.**
   - `testDebugUnitTest`, `lintDebug`, `assembleDebug` 같은 flavor-less 명령을 기본 예시로 쓰지 않는다.

## 경고 분류 기준

### 1) Safe patch / low-risk batch

작고 독립적인 patch/minor 범위. 별도 기능 수정 없이 유지보수 PR로 처리해도 되는 경우.

예시:

- Firebase BoM patch
- AndroidX patch
- DataStore patch
- Room patch
- Google Services plugin patch

권장 처리:

- 하나의 maintenance PR로 묶되, 관련 계열만 같이 올린다.
- 예: `Firebase BoM + firebase-messaging`, `Room runtime/compiler/testing`, `DataStore + lifecycle`.

### 2) Coordinated stack upgrade

호환성 검증이 먼저 필요한 상위 스택.

예시:

- AGP
- Kotlin
- Compose compiler plugin
- Compose BOM
- KSP
- Hilt major/minor jump

권장 처리:

- 단독 PR 또는 매우 좁은 배치로 처리한다.
- release build, CI, lint 결과를 함께 확인한다.
- build script/plugin 변경과 일반 라이브러리 업데이트를 분리한다.

### 3) Deferred / product-risk review needed

업데이트 자체보다 회귀 가능성 검토가 더 중요한 경우.

예시:

- AdMob / Play Services Ads
- lifecycle/permission/runtime behavior에 영향을 줄 수 있는 라이브러리
- receiver/service/notification 동작에 간접 영향을 줄 수 있는 의존성

권장 처리:

- QA 영향 범위를 먼저 적는다.
- 필요하면 `docs/QA_RUNTIME_CHECKLIST.md`까지 같이 참조한다.
- 수익화/런타임 신뢰와 연결되면 별도 follow-up 이슈로 나눈다.

## 2026-05 issue #66 maintenance batch 메모

현재 저장소에서 issue #66의 첫 실제 maintenance batch로 반영한 내용:

- `androidx.hilt:hilt-navigation-compose`: `1.2.0 -> 1.3.0`
- `androidx.appcompat:appcompat`: `1.7.0 -> 1.7.1`
- `androidx.test.ext:junit`: `1.2.1 -> 1.3.0`
- `androidx.test.espresso:espresso-core`: `3.6.1 -> 3.7.0`
- `Room` 의존성 선언을 `app/build.gradle.kts`의 direct version 문자열에서 `gradle/libs.versions.toml`로 이동
- 이후 direct version drift의 주요 잔여 위치가 `core/kds/build.gradle.kts`로 좁혀졌음을 확인

이 배치의 의도:

- **직접 버전 문자열 정리**로 `UseTomlInstead` 경고를 줄이고, 이후 잔여 드리프트 위치를 `core:kds`까지 좁혀서 추적 가능하게 만든다.
- 런타임 영향이 비교적 작은 patch(`appcompat`, `hilt-navigation-compose`)만 먼저 올린다.
- Room / Ads / Kotlin / AGP / Compose 같은 더 큰 드리프트는 한 번에 밀어 넣지 않고 후속 배치로 남긴다.

이번 배치 후에도 남겨둔 defer 항목:

- `Room 2.7.1 -> 2.8.4`: KSP/annotation processing 회귀 확인이 필요하므로 별도 좁은 배치 권장
- KDS의 AdMob runtime 의존은 2026-06-07 #557 package에서 앱 monetization/analytics 경계로 이동했다. 앞으로 Ads SDK 업그레이드는 `app/build.gradle.kts`, `TrackedBannerAd`, #16 runbook/QA evidence와 함께 검토한다.
- `AGP`, `Kotlin`, `Compose`, `Lifecycle`, `Activity`, `Material`, `Navigation` 등 coordinated stack 계열

## 권장 업그레이드 순서

기본 순서:

1. **lint 기준선 확보**
   - `:app:lintDevDebug`를 돌려 현재 경고 목록을 저장한다.
2. **버전 선언 위치 정리**
   - `libs.versions.toml`, `app/build.gradle.kts`, `core/kds/build.gradle.kts`의 direct version을 함께 확인한다.
3. **safe patch batch부터 처리**
   - Firebase / AndroidX / DataStore / Room patch처럼 비교적 독립적인 것부터.
4. **stack upgrade 분리 처리**
   - AGP / Kotlin / Compose / KSP는 별도 배치.
5. **고위험 런타임/수익화 계열은 마지막**
   - Ads, notification/service 간접 영향, permission flow 관련 계열.

## PR 한 개에 담을 권장 범위

### 좋은 예

- `docs`: lint 유지보수 런북 추가
- `chore`: Firebase BoM + firebase-messaging patch
- `chore`: Room runtime/compiler/testing patch
- `chore`: AGP + Kotlin + Compose compiler 호환성 정리

### 나쁜 예

- AGP + Kotlin + Compose + Room + Ads + lint suppressions를 한 PR에 모두 넣기
- lint가 지적한 버전 경고와 실제 product lint fix를 섞기
- 기준선 기록 없이 “최신 버전으로 올림”만 남기기

## Dependabot semver-major 수동 감사 lane (#905)

`.github/dependabot.yml`은 Gradle, GitHub Actions, Firebase Functions npm, ASO screenshots Bun ecosystem 모두에서 `version-update:semver-major`를 ignore한다. 이 정책은 자동 major PR 소음을 막기 위한 안전장치이지, major upgrade를 영구 보류한다는 뜻이 아니다. #905의 운영 기준은 **patch/minor는 weekly 자동화, semver-major는 월 1회 또는 release train 전 수동 감사**로 분리하는 것이다.

감사 주기:

- 정기: 매월 첫 번째 월요일 KST 업무 시간대에 `maintenance` backlog review와 함께 확인한다.
- 릴리즈 전: `release/*` 후보를 만들기 전에 AGP/Kotlin/Gradle wrapper, GitHub Actions runtime, Functions runtime, Play/AdMob/AndroidX major 후보가 release blocker가 될 수 있는지 한 번 더 확인한다.
- 긴급: Play 정책, GitHub Actions runtime deprecation, Firebase Functions runtime deprecation, 보안 advisory가 major upgrade를 요구할 때 즉시 별도 issue/PR로 전환한다.

감사 범위:

| Ecosystem | Source | major 후보 분리 기준 | 기본 분류 |
| --- | --- | --- | --- |
| Gradle / Android stack | `.github/dependabot.yml`, `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `core/kds/build.gradle.kts` | AGP, Kotlin, Compose compiler/BOM, KSP, Room, Hilt, Play Services Ads처럼 build/runtime 영향이 큰 축은 한 PR에 섞지 않는다. | `hold` 또는 별도 `ready` issue |
| GitHub Actions | `.github/workflows/**`, `.github/dependabot.yml` | checkout/setup-java/setup-gradle/wrapper-validation/actionlint 같은 governance action은 release/CI gate와 같이 검증한다. | `ready` if workflow contract test 범위가 작음 |
| Firebase Functions npm | `functions/package.json`, `functions/package-lock.json` | Node runtime, Firebase Admin/Functions major는 deploy/runtime verification이 필요하다. | `hold` until local build/test + deploy plan |
| ASO screenshots Bun | `tools/aso-screenshots/package.json`, lockfile | Next/Bun/tooling major는 Play deploy와 분리하되 screenshot generator build evidence가 필요하다. | `backlog` 또는 `ready` |

분류 기준:

- `ready`: 영향 범위가 한 ecosystem 안에 있고, 검증 명령과 rollback 경계가 명확하며, 같은 PR에서 contract test/docs를 함께 업데이트할 수 있다.
- `backlog`: 당장 정책/보안/릴리즈 blocker는 아니지만 다음 maintenance batch 후보로 추적해야 한다.
- `hold`: release/runtime QA, Play deploy, Firebase deploy, KSP/AGP/Kotlin 호환성, 또는 대표님 승인 없이는 안전하게 진행할 수 없다.

감사 산출물:

```md
## Dependabot semver-major audit (#905)
- Audit date:
- Source config: `.github/dependabot.yml`
- Existing automated PRs: patch/minor only? yes/no
- Major candidates reviewed:
  - Gradle / Android stack:
  - GitHub Actions:
  - Firebase Functions npm:
  - ASO screenshots Bun:
- Classification:
  - ready:
  - backlog:
  - hold:
- Required follow-up:
  - issue / PR / external approval / release train note
```

운영 원칙:

- major 후보를 발견해도 바로 `.github/dependabot.yml`의 ignore를 제거하지 않는다. 먼저 위 감사 산출물로 `ready`/`backlog`/`hold`를 분류한다.
- `ready`로 승격한 major 후보만 별도 이슈 또는 좁은 PR로 전환한다. AGP/Kotlin/Compose/KSP 같은 stack upgrade는 같은 PR 안에서 compatibility matrix와 release/build verification을 요구한다.
- `hold` 후보는 보류 이유를 issue comment나 maintenance report에 남기고, Play Console / Firebase deploy / 대표님 승인 / release train 같은 외부 경계를 명시한다.
- patch/minor Dependabot PR(#693 정책)은 계속 `dependabot/* -> develop` 자동 PR로 운영한다. #905 감사는 그 자동 PR을 대체하지 않고, semver-major만 별도 수동 lane으로 다룬다.
- 예외: `r0adkll/upload-google-play`는 patch/minor라도 자동 Dependabot PR 대상이 아니다. 이 action은 Google Play 업로드 side effect와 signed AAB provenance를 잇는 release-critical boundary이므로 `.github/dependabot.yml`에서 ignore하고, 새 SHA 검토가 필요할 때만 별도 release-governance PR에서 `.github/workflows/play-deploy.yml`, `scripts.tests.test_release_provenance_workflow_contract`, `docs/PLAY_DEPLOYMENT.md`, `docs/GIT_WORKFLOW.md`, `docs/RELEASE_CHECKLIST.md`, `docs/ops/stopit/release-context.md`를 함께 갱신한다.
- Play deploy, release secret, signing secret, Firebase service account secret은 semver-major 감사의 산출물이 아니다. major upgrade PR이 이런 secret 경계를 요구하면 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`와 release-governance issue로 분리한다.

## 2026-06-26 Dependabot semver-major audit (#1069)

Audit date: `2026-06-26 12:12 KST` / `2026-06-26T03:12:45Z`

확인 소스:

- GitHub open PR queue: #1065, #1064, #1061, #1060, #1046, #1044, #1041, #918은 모두 patch/minor Dependabot PR이며, 이번 감사의 semver-major 승격 대상 자체는 아니다.
- Repo config/docs: `.github/dependabot.yml`, `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `core/kds/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, `functions/package.json`, `functions/package-lock.json`, `tools/aso-screenshots/package.json`, `tools/aso-screenshots/bun.lock`, `.github/workflows/*.yml`.
- Tooling snapshot: Maven/Google Maven metadata lookup, `npm outdated --json --long --include=dev` under `functions/`, `bun outdated` under `tools/aso-screenshots/`.
- Local caveat: Functions local shell reported Node `v20.18.0` while `functions/package.json` requires Node `22`; Functions major verification must run in Node 22/CI before execution PRs are promoted.

### 현재 감사 결론

| Classification | 후보 | 다음 처리 |
| --- | --- | --- |
| `ready` | `actions/setup-node v5 -> v6`, `dorny/paths-filter v3 -> v4` | workflow/runtime governance 영향만 있는 좁은 `ci/issue-*` PR 후보. `actionlint`, Ops CI docs-contract, path-gate/static contract 검증을 요구한다. Play deploy secret, signing, production promotion 경계는 건드리지 않는다. |
| `ready` | Firebase BoM `34.14.1 -> 34.15.0`, Google Services plugin `4.4.4 -> 4.5.0`, Mockito `5.14.2 -> 5.23.0`, Lottie `6.6.3 -> 6.7.1` | semver-major가 아니라 patch/minor 유지보수 후보다. 기존 weekly Dependabot PR queue에서 각각 Firebase/Google, test-tooling, runtime-libraries lane으로 처리한다. |
| `backlog` | Orbit MVI `9.x -> 10/11`, TypeScript `5.x -> 6` in `functions/`, TypeScript `5.x -> 6` and `@types/node 20 -> 26` in ASO screenshots | 별도 좁은 maintenance issue/PR 후보. Orbit은 앱 state/runtime 회귀, Functions/ASO TS major는 build/test/type 결과를 먼저 요구한다. |
| `hold` | AGP `8.10.1 -> 9.x`, Gradle wrapper, Kotlin `2.1.x -> 2.3+/2.4`, KSP `2.2+`, Hilt `2.59+`, Compose BOM `2025.11+ / 2026.x`, AndroidX compileSdk-bound batch, kotlinx serialization `1.9+`, kotlinx datetime `0.8+` | Android toolchain lane에서 compatibility matrix, compileSdk/AGP/Kotlin/KSP/Compose/Hilt 정합성, Android CI, Release Build evidence를 함께 요구한다. Dependabot ignore를 제거하지 않는다. |
| `hold` | Play Services Ads `23.x -> 24/25` | #16 수익화/runtime-sensitive 후보. 광고 SDK major는 AdMob/analytics/placement guardrail과 device/runtime QA 없이는 자동 승격하지 않는다. |
| `hold` | `firebase-admin 13 -> 14`, `@types/node 22 -> 26` in `functions/` | `firebase-functions 7.2.5` peer가 `firebase-admin ^13`까지만 허용하고, runtime은 Node 22다. Functions major는 Firebase deploy/runtime verification 계획이 생길 때까지 보류한다. |
| `hold` | `r0adkll/upload-google-play` SHA pin refresh | semver-major 후보가 아니라 release-critical provenance boundary다. Dependabot 자동 PR 대상이 아니며 release-governance PR에서 workflow + provenance contract + operator docs를 함께 갱신할 때만 다룬다. |
| `no-op/current` | Firebase Crashlytics plugin, Play Review, Install Referrer, `actions/setup-java`, `actions/upload-artifact`, `oven-sh/setup-bun`, `reactivecircus/android-emulator-runner`, ASO `next`/`react`/`react-dom`/`html-to-image` major line | 이번 #1069 감사에서 별도 major issue로 승격하지 않는다. |

### Ecosystem별 상세 판단

#### Gradle / Android stack

| 후보 | 현재 기준 | 감사 분류 | 근거 |
| --- | --- | --- | --- |
| AGP / Gradle wrapper / Kotlin / KSP / Hilt / Compose | AGP `8.10.1`, Gradle `8.11.1`, Kotlin `2.1.10`, KSP `2.1.10-1.0.31`, Hilt `2.56.1` | `hold` | #925/#984/#1008/#1051 guard와 동일하다. Hilt `2.59+`는 AGP 9, Kotlin `2.3+`/KSP `2.2+`는 별도 Kotlin/toolchain lane 검증이 필요하다. |
| AndroidX compileSdk-bound batch | app/KDS `compileSdk 35` | `hold` | `core-ktx 1.17+`, Navigation `2.9+`, Lifecycle `2.10+`, Activity `1.11+`, Compose BOM `2025.11+`는 compileSdk 36/37 또는 AGP 9.1+ 경계다. |
| Play Services Ads | `23.0.0` | `hold` | #16 광고/수익화 runtime-sensitive 후보. event-source split/release/readback 경계와 같이 판단한다. |
| Orbit MVI | `9.0.0` | `backlog` | 실제 semver-major 후보지만 앱 state flow 영향이 커서 별도 MVI migration issue/PR로 분리한다. |
| Room `2.7.1 -> 2.8.x`, Material/Lottie patch-minor, Firebase/Google patch-minor, Mockito patch-minor | 현재 Gradle split PR queue에 일부 존재 | `ready` 또는 `backlog` | semver-major 감사 대상이 아니라 기존 Dependabot patch/minor risk lane에서 처리한다. |

#### GitHub Actions

| 후보 | 현재 기준 | 감사 분류 | 근거 |
| --- | --- | --- | --- |
| `actions/setup-node v5 -> v6` | Ops CI / Play Deploy helper setup | `ready` | Node setup action만 바꾸는 좁은 workflow-governance PR 가능. `actionlint`와 docs/static contract를 함께 요구한다. |
| `dorny/paths-filter v3 -> v4` | Android/Ops CI path classification | `ready` | path-gating 영향은 있지만 scope가 CI classification에 한정된다. path filter materialization contract를 반드시 확인한다. |
| `actions/checkout v6 -> v7` | repo 표준은 checkout v6 | `backlog` | upstream major가 있어도 현재 release-context 표준은 v6 정렬이다. 전체 workflow standard migration으로만 다룬다. |
| `gradle/actions/* v6`, `setup-java v5`, `upload-artifact v7`, `setup-bun v2`, emulator runner v2 | 현재 major line 유지 | `no-op/current` | 이번 감사에서 major 승격 후보 없음. |

#### Firebase Functions npm

| 후보 | 현재 기준 | 감사 분류 | 근거 |
| --- | --- | --- | --- |
| `firebase-admin 13.10.0 -> 14.x` | `firebase-functions 7.2.5` | `hold` | `firebase-functions` peer range가 Admin 14를 아직 허용하지 않는다. Admin/Functions major는 deploy/runtime verification까지 묶는다. |
| `typescript 5.8.2 -> 6.x` | Functions TS build/test | `backlog` | runtime 직접 영향은 작지만 compiler major다. Node 22에서 `npm ci`, `npm run build`, `npm test` green이 필요하다. |
| `@types/node 22 -> 26` | runtime engine Node 22 | `hold` | runtime/type major를 불일치시키지 않는다. Node runtime 전환 후보가 생길 때 재검토한다. |

#### ASO screenshots Bun

| 후보 | 현재 기준 | 감사 분류 | 근거 |
| --- | --- | --- | --- |
| `typescript 5.9.3 -> 6.x`, `@types/node 20 -> 26` | `tools/aso-screenshots` local build tool | `backlog` | Play deploy와 분리된 screenshot generator 전용 후보다. `bun install --frozen-lockfile`, `bun run build` 증거가 필요하다. |
| `next 16`, `react 19`, `react-dom 19`, `html-to-image 1.x`, Tailwind/PostCSS patch | current major line | `no-op/current` | 이번 semver-major 감사에서 별도 승격 없음. |

### Open Dependabot PR queue 처리 권고

- #1065 `android-gradle-runtime-libraries-patch-minor`: runtime library patch/minor lane이다. Kotlin metadata/datetime hold guard를 먼저 확인하고, Orbit/Ads major와 섞지 않는다.
- #1064 `android-gradle-toolchain-held-patch-minor`: toolchain-held lane이므로 Hilt/AGP/Kotlin known-incompatible가 보이면 merge하지 말고 hold/close 또는 별도 toolchain lane으로 전환한다.
- #1061 `android-gradle-room-ksp-patch-minor`: Room/KSP 전용 verification이 필요하다. KSP `2.2+`가 포함되면 Kotlin/toolchain boundary로 hold한다.
- #1060 `android-gradle-androidx-ui-runtime-patch-minor`: compileSdk/AGP boundary guard를 먼저 확인한다. `requires compileSdk 36+` 또는 AGP 9.1+ metadata가 보이면 app-code regression이 아니라 known-incompatible hold다.
- #1046 ASO Bun, #1044 test tooling, #1041 Firebase/Google, #918 Functions npm은 각 ecosystem의 patch/minor lane으로 유지한다. major 후보는 이 PR들에 억지로 섞지 않는다.

### #1069 다음 재검토 조건

- 다음 정기 감사: 다음 월간 maintenance backlog review 또는 `release/*` 후보 생성 전.
- 즉시 재감사 조건: GitHub Actions runtime deprecation, Firebase Functions runtime deprecation, Play/AdMob SDK 정책 변경, AGP 9/compileSdk 36+ 전환 승인, Firebase Functions가 `firebase-admin 14` peer를 허용하는 새 major/minor release.
- 이번 감사에서 바로 실행할 수 있는 repo-internal 후속은 `actions/setup-node v6`와 `dorny/paths-filter v4`를 좁은 workflow-governance PR로 분리하는 것이다. Android toolchain/Ads/Functions Admin major는 외부 runtime/release/deploy 경계가 있어 docs-lane에서 즉시 버전 변경하지 않는다.

## Gradle Dependabot risk-lane split (#1034)

#1034 기준으로 Gradle patch/minor 자동 PR은 더 이상 broad `patterns: ["*"]` 한 그룹으로 묶지 않는다. #1013처럼 29개 업데이트가 한 PR에 섞이면 AndroidX/compileSdk, Firebase/Google, Room/KSP, test tooling, runtime library, toolchain-held 경계가 모두 같은 실패처럼 보이고 lane이 무엇을 되돌려야 하는지 매번 재분석하게 된다.

현재 `.github/dependabot.yml`의 Gradle group 정책:

| Group | 포함 예시 | 기본 triage |
| --- | --- | --- |
| `android-gradle-firebase-google-patch-minor` | Firebase BoM/modules, Google Services plugin, Crashlytics plugin, Play Services Ads, Play Review, Install Referrer | Firebase/Google 계열만 좁게 검증한다. Ads/수익화 런타임 영향이 있으면 #16 runbook과 QA evidence를 같이 본다. |
| `android-gradle-androidx-ui-runtime-patch-minor` | `androidx.core`, lifecycle, activity, compose, navigation, appcompat, datastore, hilt-navigation-compose | UI/runtime AndroidX 영향 범위로 본다. compileSdk/AGP metadata 오류가 보이면 아래 #1008 guard로 hold한다. |
| `android-gradle-room-ksp-patch-minor` | Room runtime/compiler/testing/plugin, KSP plugin | annotation processing / schema / migration 영향이 있으므로 Room/KSP 전용 verification과 runtime migration risk를 분리한다. |
| `android-gradle-test-tooling-patch-minor` | JUnit, AndroidX Test, Espresso, UIAutomator, Mockito | 제품 런타임이 아니라 test harness drift로 우선 분류한다. CI/test infra 실패와 app-code regression을 섞지 않는다. |
| `android-gradle-runtime-libraries-patch-minor` | kotlinx serialization/datetime, Lottie, Material, Orbit | 앱 런타임 라이브러리로 작게 검증하되 release/runtime QA 필요 여부를 PR 본문에 적는다. |
| `android-gradle-toolchain-held-patch-minor` | AGP plugin, Kotlin plugins, Hilt plugin/runtime/compiler | 자동 merge 후보가 아니라 compatibility matrix가 필요한 toolchain lane이다. 대부분 ignore guard가 먼저 적용되며, 새 후보는 별도 `ready`/`hold` 판단을 남긴다. |

운영 규칙:

- `open-pull-requests-limit`는 Gradle split로 PR이 여러 개 생길 수 있음을 감안하되, weekly 소음이 과도하지 않도록 제한을 유지한다.
- 같은 주기에 여러 Gradle PR이 생기면 `test-tooling` / 명확한 Firebase patch처럼 blast radius가 작은 PR을 먼저 보고, AndroidX/runtime/toolchain 계열은 실패 로그와 known guard를 확인한다.
- 기존 broad PR #1013은 새 정책 기준으로는 그대로 merge하지 않는다. 다음 Dependabot 주기에서 분할 재생성하거나, 필요한 경우 lane owner가 안전한 group만 수동 cherry-pick하고 broad PR은 close/hold로 정리한다.
- 이 정책은 `scripts.tests.test_dependabot_policy_contract`가 고정한다. Gradle group이 다시 broad `patterns: ["*"]`로 돌아가거나 docs에서 group별 triage 기준이 사라지면 Ops CI docs-contract가 실패해야 한다.

## 기본 검증 명령

문서/운영에서 참조하는 기본 명령:

```bash
cd <repo-root>
./gradlew :app:lintDevDebug
./gradlew :app:testDevDebugUnitTest
./gradlew :app:assembleProdDebug
./gradlew :app:testProdReleaseUnitTest
./gradlew :app:bundleProdRelease
```

용도:

- `:app:lintDevDebug`: 버전 드리프트 + 일반 lint 신호 확인
- `:app:testDevDebugUnitTest`: 빠른 JVM 회귀 확인
- `:app:assembleProdDebug`: prod flavor debug 빌드 smoke
- `:app:testProdReleaseUnitTest`: release variant 경로 확인
- `:app:bundleProdRelease`: 실제 Play 업로드와 맞는 release bundle 경로 확인

문서-only slice에서 최소 sanity check만 필요하면 아래처럼 task 존재 확인으로도 충분하다.

```bash
cd <repo-root>
./gradlew -q help --task :app:lintDevDebug
./gradlew -q help --task :app:testDevDebugUnitTest
./gradlew -q help --task :app:assembleProdDebug
./gradlew -q help --task :app:testProdReleaseUnitTest
./gradlew -q help --task :app:bundleProdRelease
```

## lint 결과를 읽는 방법

`app/build/reports/lint-results-devDebug.txt`를 확인할 때는 먼저 아래처럼 나눈다.

1. **업그레이드 후보**
   - `NewerVersionAvailable`
2. **lint check runtime/plugin 노후화**
   - `ObsoleteLintCustomCheck`
3. **실제 제품/리소스/코드 위험**
   - locale, permissions, API misuse, resources 등

운영 원칙:

- `NewerVersionAvailable`는 “당장 버그”가 아니라 유지보수 backlog 신호다.
- `ObsoleteLintCustomCheck`는 lint check JAR/runtime 조합 점검 이슈로 본다.
- 제품 위험 lint는 dependency batch와 분리해서 우선순위를 다시 매긴다.

### Navigation/Compose custom lint 복구 절차 (`#156` 유형)

Navigation Compose custom lint가 `ObsoleteLintCustomCheck` 또는 `Requires newer lint; these checks will be skipped!`로 빠질 때는, lint report green 자체를 신뢰하지 말고 **lint runtime 복구 → 실제 rule 발화 확인 → 제품 lint 정리** 순서로 본다.

현재 Stopit에서 재현/복구가 확인된 조합은 아래다.

- AGP: `8.10.1`
- Gradle wrapper: `8.11.1`
- Kotlin: `2.1.10` 유지
- Navigation Compose: `2.8.9` 유지

검증 순서:

1. baseline RED
   `./gradlew :app:lintDevDebug` 후 `app/build/reports/lint-results-devDebug.txt`에서 아래 문자열이 있는지 확인한다.
   - `ObsoleteLintCustomCheck`
   - `Requires newer lint; these checks will be skipped!`
   - `MissingSerializableAnnotation`, `MissingKeepAnnotation`, `WrongNavigateRouteType`가 “skipped issue 목록”에만 있고 실제 오류/경고로는 안 잡히는지
2. runtime 복구 후 GREEN
   같은 명령을 다시 돌린 뒤 아래 자동 verifier를 통과시켜 “skip 문자열 없음 + navigation registry/issue id 포함”을 함께 확인한다.

   ```bash
   cd <repo-root>
   ./gradlew :app:lintDevDebug
   python3 scripts/verify_lint_registry.py \
     --report app/build/reports/lint-results-devDebug.html \
     --require-section "Included Additional Checks" \
     --require-identifier androidx.navigation.common \
     --require-identifier androidx.navigation.compose \
     --require-identifier androidx.navigation.runtime \
     --require-issue-id MissingSerializableAnnotation \
     --require-issue-id MissingKeepAnnotation \
     --require-issue-id WrongNavigateRouteType \
     --forbid-text "Requires newer lint; these checks will be skipped!" \
     --forbid-text ObsoleteLintCustomCheck
   ```

   이 verifier는 `scripts/tests/test_verify_lint_registry.py` fixture RED/GREEN과 함께 유지한다. PR fast verification에서는 `app/build/reports/lint-results-devDebug.html`을, release QA에서는 `app/build/reports/lint-results-prodRelease.html`을 같은 기준으로 검사해 dev green과 release green이 모두 "navigation lint registry 포함 green"인지 확인한다.
3. 실제 rule 발화 probe (선택적 심화 검증)
   type-safe destination 하나에서 `@Serializable`을 **임시로 제거한 뒤** `./gradlew :app:lintDevDebug`를 다시 돌려 `MissingSerializableAnnotation from androidx.navigation.compose`가 실제 에러로 잡히는지 확인하고, 즉시 원복한다.
4. 제품 lint 정리
   runtime 복구 후 새로 surfaced 되는 Compose/Android lint를 해결한다. 이번 복구에서는 `LocalContextConfigurationRead`가 새로 드러났고, `LocalConfiguration.current`로 바꿔 lint green을 회복했다.

이 순서를 거치지 않으면 “skip warning만 줄었다”와 “실제로 navigation lint가 복구됐다”를 구분할 수 없다. 이제 Android CI fast verification과 release QA full-release gate가 모두 같은 verifier를 실행하므로, 향후 회귀가 나면 PR 단계와 release gate 양쪽에서 바로 막히는 형태를 기본값으로 본다.

## 후속 maintenance PR에 남겨야 할 evidence

최소한 아래를 PR 본문이나 체크리스트에 남긴다.

```md
## Dependency / lint maintenance evidence
- Baseline command:
  - `./gradlew :app:lintDevDebug`
- Changed coordinates:
  - `libs.versions.toml`: ...
  - `app/build.gradle.kts`: ...
  - `core/kds/build.gradle.kts`: ...
- Verification:
  - `./gradlew :app:testDevDebugUnitTest`
  - `./gradlew :app:assembleProdDebug`
  - `./gradlew :app:testProdReleaseUnitTest`
  - `./gradlew :app:bundleProdRelease` (or reason skipped)
- Lint delta:
  - removed warnings:
  - remaining warnings:
  - deferred items:
```

## Dependabot 자동 업데이트 정책 (#693)

Stopit은 `.github/dependabot.yml`을 dependency update automation의 기본 설정으로 사용한다. 설정 목적은 “최신 버전을 무조건 빨리 올리는 것”이 아니라, Android/Functions/ASO/GitHub Actions 드리프트를 **weekly**로 감지하고 PR 소음을 제한하면서 사람이 triage할 수 있는 evidence를 만들기 위함이다.

등록 ecosystem:

| Ecosystem | Directory | 기대 CI / 검토 포인트 |
| --- | --- | --- |
| Gradle version catalog / wrapper | `/` | Android CI fast verification, lint baseline, release-impact 검토 |
| GitHub Actions | `/` | Branch Hygiene / Ops CI workflow syntax + docs-contract drift 검토 |
| Firebase Functions npm | `/functions` | Ops CI Functions job: Node 22 `npm ci`, lint, test |
| ASO screenshot tool Bun lockfile | `/tools/aso-screenshots` | 도구 빌드/스크린샷 workflow 영향 검토 |

운영 기준:

- Dependabot PR에는 `maintenance`, `automation`, `dependencies` labels를 붙인다.
- Dependabot PR head는 `dependabot/*` 형태가 정상이며, Branch Hygiene는 이 자동화 브랜치를 `develop` 대상으로 허용해야 한다. `dependabot/*` 실패는 dependency 자체 문제가 아니라 branch-routing 정책 drift로 먼저 분류한다.
- patch/minor update는 ecosystem별 weekly group으로 묶어 backlog 소음을 제한한다.
- **major update**는 자동 그룹 PR로 밀지 않고 `version-update:semver-major` ignore로 막아 별도 수동 검토 대상으로 남긴다.
- major update가 필요하면 이 런북의 “Coordinated stack upgrade” 또는 “Deferred / product-risk review needed” 분류에 따라 별도 이슈/PR로 처리한다.
- Dependabot PR이 Play deploy, release secret, signing secret, Firebase service account secret을 변경하거나 요구하는 형태로 확장되면 안 된다. 의존성 PR의 기본 경계는 코드/빌드 검증이며, Play deploy/release secret 설정은 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`와 릴리즈 workflow가 별도로 소유한다.
- `.github/dependabot.yml` 변경은 Ops CI top-level trigger와 `docs_contract` filter에서 `Docs/runbook contract tests`를 materialize해야 한다. 이 경로는 `scripts.tests.test_dependabot_policy_contract`를 실행해 ecosystem/schedule/noise/major-update/manual-review/release-secret boundary drift를 잡고, Functions/Android build/Play deploy secret 작업은 실행하지 않는다.
- Android/runtime-sensitive dependency가 포함되면 PR 본문에 `docs/QA_RUNTIME_CHECKLIST.md`에서 필요한 device/emulator evidence를 명시한다.

### Android Gradle stack compatibility guard (#925)

`android-gradle-toolchain-held-patch-minor`는 backlog 소음을 줄이기 위한 weekly 감지 lane이지, AGP/Kotlin/KSP/Hilt/Compose 같은 build stack을 무조건 한 번에 올려도 된다는 승인 신호가 아니다. #914처럼 `Hilt 2.59+`와 `AGP 8.x`가 함께 들어와 Gradle configuration 단계에서 `The Hilt Android Gradle plugin is only compatible with Android Gradle plugin (AGP) version 9.0.0 or higher`로 실패하면 앱/KDS 테스트가 시작되기 전 known-incompatible 조합으로 본다.

현재 정책:

- Stopit이 AGP 8.x에 머무는 동안 `.github/dependabot.yml`은 `com.google.dagger.hilt.android`, `com.google.dagger:hilt-android`, `com.google.dagger:hilt-compiler`의 `[2.59,)` 범위를 ignore한다.
- 이 hold는 Hilt를 영구 보류한다는 뜻이 아니라, `AGP 9` 전환이 필요한 Android stack upgrade를 별도 toolchain lane에서 다루기 위한 안전장치다.
- #928/#939/#984처럼 Kotlin plugin이 `Kotlin 2.3.21` 이상으로 올라가면서 기존 `kotlinOptions.jvmTarget` 사용이 `Using 'jvmTarget: String' is an error`로 실패하는 경우도 같은 guard 대상이다. #1009 이후 `app/build.gradle.kts`와 `core/kds/build.gradle.kts`는 `compilerOptions DSL`의 `JvmTarget.JVM_17`로 JVM target을 고정한다.
- Kotlin 2.3+ 전환은 단순 Dependabot rerun이나 broad Gradle Dependabot group merge가 아니라, #1009 build-script `compilerOptions DSL` migration을 baseline으로 삼고 KSP/Compose 호환성 확인, Android CI/Release Build 검증을 포함한 별도 Kotlin/toolchain lane에서 처리한다. 따라서 `.github/dependabot.yml`의 `[2.3,)` hold는 “legacy `kotlinOptions.jvmTarget`가 아직 남아서”가 아니라, 실제 Kotlin 2.3+ toolchain validation 전까지 broad weekly group이 Kotlin/Compose/serialization/JVM plugin을 자동 병합하지 못하게 하는 안전장치다.
- AGP 9 전환 후보는 Gradle wrapper, AGP plugin, Kotlin, Compose compiler/BOM, KSP, Hilt plugin/runtime/compiler를 한 PR에 섞어 자동 merge하지 않고, compatibility matrix와 release/build verification을 명시한 `ready` issue/PR로 승격한다.
- #914/#928/#984류 PR이 같은 Gradle configuration 단계 오류를 재현하면 단순 rerun하지 않는다. 해당 Dependabot PR은 close/hold 또는 별도 toolchain lane으로 전환하고, PR/이슈 코멘트에 `known-incompatible: Hilt 2.59+ requires AGP 9 while Stopit is on AGP 8.x` 또는 `known-incompatible: Kotlin 2.3+ requires dedicated Kotlin/toolchain lane validation before broad Dependabot merge`를 남긴다.
- `scripts.tests.test_dependabot_policy_contract`가 이 guard를 고정한다. `.github/dependabot.yml`에서 Hilt 2.59+ / Kotlin 2.3+ ignore가 빠지거나 이 문서가 #914/#928/#984 처리 기준을 잃으면 Ops CI docs-contract가 실패해야 한다.

### AndroidX compileSdk / AGP boundary guard (#1008, #1051)

PR #989는 앱/KDS 코드 변경 전 단계에서 known-incompatible AndroidX metadata boundary를 드러냈다. 이후 PR #1042와 PR #1056은 같은 `android-gradle-androidx-ui-runtime-patch-minor` lane 안에서 더 낮은 버전군도 현재 Stopit toolchain과 충돌한다는 점을 확인했다. 현재 Stopit은 `app`과 `core:kds` 모두 `compileSdk 35`, AGP `8.10.1` 기준이며, 아래 artifacts는 broad weekly UI/runtime lane에서 자동 병합하지 않는다.

- `androidx.core:core-ktx 1.17.0` / `androidx.core:core 1.17.0` → 현재 `compileSdk 35` baseline에서 AndroidX metadata check가 `requires compileSdk 36+`로 실패한다. `androidx.core:core-ktx 1.18.0` 이상도 같은 AndroidX metadata 경계에 있고, `androidx.core:core:1.19.0` / `androidx.core:core-ktx 1.19.0`는 더 강하게 `compileSdk 37+` 및 `AGP 9.1.0 or higher`를 요구한다.
- `androidx.navigationevent:*:1.0.0` → `compileSdk 36+` 요구. `androidx.navigation:navigation-compose 2.9.x`가 이 축을 끌고 들어온다.
- `androidx.activity:activity-compose 1.11.0` / `androidx.activity:activity 1.11.0` / `androidx.activity:activity-ktx 1.11.0` → 현재 `compileSdk 35` baseline에서 `requires compileSdk 36+`로 실패한다.
- `androidx.lifecycle:* 2.10.x`, `androidx.activity:activity-compose 1.12.4`, `androidx.compose:compose-bom 2025.11.x`는 PR #1042에서 같은 compileSdk/AGP-bound AndroidX batch로 확인됐으므로 같이 hold한다.

현재 정책:

- Stopit이 `compileSdk 35` / AGP 8.x에 머무는 동안 `.github/dependabot.yml`은 아래 범위를 `android-gradle-androidx-ui-runtime-patch-minor` lane 안에서 hold한다.
  - `androidx.core:core-ktx [1.17,)`
  - `androidx.navigation:navigation-compose [2.9,)`
  - `androidx.lifecycle:lifecycle-runtime-ktx [2.10,)`
  - `androidx.lifecycle:lifecycle-process [2.10,)`
  - `androidx.lifecycle:lifecycle-runtime-compose [2.10,)`
  - `androidx.activity:activity-compose [1.11,)`
  - `androidx.compose:compose-bom [2025.11,)`
- 이 hold는 AndroidX 업데이트를 영구 보류한다는 뜻이 아니다. `compileSdk 36/37`, AGP 9.1+, Gradle wrapper, Kotlin/KSP/Compose 호환성, Android CI/Release Build 검증을 포함한 **별도 Android toolchain lane**에서 한 번에 승격해야 한다.
- PR #989, PR #1042, PR #1056처럼 실패 로그가 `requires compileSdk ...` / `:app is currently compiled against android-35` / `AGP ... or higher` metadata 오류를 보이면 app-code regression으로 디버깅하지 않는다. 해당 PR은 close/hold하고, `known-incompatible: AndroidX compileSdk / AGP boundary while Stopit is compileSdk 35 / AGP 8.x`로 분류한다.
- `scripts.tests.test_dependabot_policy_contract`가 이 guard를 고정한다. `.github/dependabot.yml`에서 위 AndroidX hold가 빠지거나 이 문서/Git workflow가 #989/#1042/#1056 처리 기준을 잃으면 Ops CI docs-contract가 실패해야 한다.

### KSP / kotlinx metadata Kotlin toolchain guard (#1051)

PR #1043/#1045와 PR #1057/#1058/#1062는 Gradle group split 이후에도 Kotlin/runtime library toolchain 경계가 Room/KSP lane과 runtime-libraries lane을 통해 우회될 수 있음을 보여줬다.

- PR #1043: `com.google.devtools.ksp 2.3.9`가 Kotlin 2.3 계열 KSP API를 가져오며 Stopit의 Kotlin `2.1.x` / KSP `2.1.10-1.0.31` baseline에서 Gradle/Kotlin API 오류(`KotlinJvmCompilerOptions.getJvmDefault()` 등)를 만든다.
- PR #1045: `org.jetbrains.kotlinx:kotlinx-serialization-json 1.11.x`는 Kotlin metadata 2.3.x artifact를 끌어와 Kotlin 2.1.x toolchain으로 소비할 수 없는 실패를 만든다.
- PR #1057: `com.google.devtools.ksp 2.2.21-2.0.5`도 Stopit의 Kotlin `2.1.x` baseline에서 같은 `KotlinJvmCompilerOptions.getJvmDefault()` API drift를 재현했다.
- PR #1058: `org.jetbrains.kotlinx:kotlinx-serialization-json 1.10.0` (`kotlinx-serialization-json 1.10.x` line)도 Kotlin stdlib/core/json metadata `2.3.0` artifact를 끌어와 Kotlin 2.1.x compiler가 소비하지 못했다.
- PR #1062: `org.jetbrains.kotlinx:kotlinx-serialization-json 1.9.0`은 Kotlin 2.2+ API를 요구하고, `org.jetbrains.kotlinx:kotlinx-datetime 0.8.0`은 기존 `Clock.System` / `Instant` contract를 깨뜨려 `RoutineScheduler`와 `TimeExt` compile error를 만들었다.

현재 정책:

- `.github/dependabot.yml`은 `com.google.devtools.ksp [2.2,)`를 hold한다. Room runtime/compiler/testing patch는 계속 볼 수 있지만, KSP 2.2.x / KSP 2.3.x 이상은 Room patch lane이 아니라 Kotlin/KSP toolchain lane에서 검증한다.
- `.github/dependabot.yml`은 `org.jetbrains.kotlinx:kotlinx-serialization-json [1.9,)`를 hold한다. 다른 runtime-library patch/minor 후보는 별도로 검토할 수 있지만, Kotlin 2.2+ / metadata 2.3.x를 요구하는 serialization json은 Kotlin/toolchain lane 경계다.
- `.github/dependabot.yml`은 `org.jetbrains.kotlinx:kotlinx-datetime [0.8,)`를 hold한다. `Clock.System` / `Instant` migration은 단순 runtime-library patch가 아니라 코드/API migration과 Kotlin/toolchain 검증을 요구한다.
- 이 hold도 영구 보류가 아니다. Kotlin 2.2/2.3+, KSP, Compose compiler, serialization/datetime runtime, Android CI, Release Build를 함께 검증하는 별도 Kotlin/toolchain PR에서 승격한다.
- PR #1043/#1045/#1057/#1058/#1062처럼 실패 로그가 Kotlin API/metadata 소비 실패 또는 kotlinx datetime API drift를 보이면 app-code regression으로 디버깅하지 않고 `known-incompatible: Kotlin/KSP/serialization/datetime toolchain boundary while Stopit is Kotlin 2.1.x`로 분류한다.
- `scripts.tests.test_dependabot_policy_contract`가 이 guard를 고정한다. `.github/dependabot.yml`에서 KSP 2.2.x, kotlinx-serialization-json 1.9.x, 또는 kotlinx-datetime 0.8.x hold가 빠지거나 문서가 PR #1043/#1045/#1057/#1058/#1062 경계를 잃으면 Ops CI docs-contract가 실패해야 한다.

PR triage checklist:

```md
## Dependabot / dependency automation triage
- Source PR: Dependabot / manual
- Ecosystem: gradle / github-actions / npm(functions) / bun(aso)
- Update type: patch / minor / major
- Group: weekly grouped / single manual review
- Labels present: maintenance, automation, dependencies
- Verification:
  - Android / Ops CI materialized: yes / no / n/a
  - Required checks green: yes / no
  - Runtime/release manual QA needed: yes / no
- Release boundary:
  - Play deploy touched: no
  - release secret touched: no
  - signed artifact / production path touched: no
```

## docs-lane에서 허용되는 작은 slice 예시

이 lane에서는 아래처럼 **운영 문서만** 다루는 것이 안전하다.

- lint/업그레이드 runbook 추가
- dependency grouping 기준 문서화
- release checklist에 dependency-maintenance evidence 요구사항 연결
- flavor-aware verification 예시 정리

이 lane에서 하지 않는 것:

- 실제 Gradle/plugin/library 버전 bump
- CI workflow 동작 변경(단, #889처럼 Ops CI docs-contract materialization 자체가 명시된 운영 이슈는 `ci/issue-*` PR로 workflow+문서+meta-contract를 함께 수정한다)
- 앱 동작/런타임/수익화 로직 수정

## 흔한 실수

- `libs.versions.toml`만 보고 `core/kds/build.gradle.kts`에 남은 direct dependency version을 놓치기
- AGP/Kotlin/Compose/KSP를 일반 patch와 한 배치에 섞기
- lint 경고 감소를 확인하지 않고 “업그레이드 완료”라고 쓰기
- flavor-less 명령 예시를 다시 문서에 넣기
- Ads/receiver/service 영향 가능성이 있는 변경을 QA 언급 없이 올리기

## 관련 문서

- `docs/GIT_WORKFLOW.md`
- `docs/PLAY_DEPLOYMENT.md`
- `docs/QA_RUNTIME_CHECKLIST.md`
- `docs/ops/stopit/engineering-context.md`
