# Stopit Git Workflow

Stopit은 `develop`을 일상 개발 기본 브랜치로, `main`을 릴리즈/프로덕션 브랜치로 사용한다.

## Branch Strategy

```text
main                    # Play Store 릴리즈 기준선. 태그는 여기에서만 생성.
└── hotfix/*            # 운영 긴급 수정. main에서 분기 후 main + develop에 반영.
└── release/x.y.z       # 릴리즈 후보. develop에서 분기, 버전 bump와 최종 검증만 수행.
    └── develop         # 개발 통합 브랜치. 기본 PR 대상.
        └── feature/*   # 기능 개발
        └── fix/*       # 버그 수정
        └── chore/*     # 설정/빌드/운영성 작업
        └── docs/*      # 문서 작업
        └── refactor/*  # 리팩터링
        └── test/*      # 테스트 보강
        └── ci/*        # CI/CD 변경
```

## CI / Release Build / CD Split

| Layer | Workflow | Trigger | Responsibility |
| --- | --- | --- | --- |
| CI | `.github/workflows/android-ci.yml` | PR to `develop`/`main`, push to `develop`/`main`, manual | Fast verification (`:app:testDevDebugUnitTest`, `:app:lintDevDebug`, `:app:assembleProdDebug`) plus `scripts/verify_lint_registry.py`로 devDebug HTML lint report의 navigation common/compose/runtime registry와 핵심 issue id를 강제 확인하고, PR/manual focused runtime smoke를 수행한다. No signed release, no Play upload. |
| Ops CI | `.github/workflows/ops-ci.yml` | PR/push touching `functions/`, `scripts/promote-google-play-track.js`, `scripts/notify-discord-deploy.py`, `scripts/tests/**`, or manual | Firebase Functions `npm ci`/`npm run lint`/`npm test`, Google Play promotion helper `node --test scripts/tests/test_promote_google_play_track.js`, and Discord deploy notification script `python3 -m py_compile scripts/notify-discord-deploy.py`. |
| Release QA | `.github/workflows/release-qa.yml` | `release/* -> main`, `hotfix/* -> main`, manual | Full release JVM/build gate plus `scripts/verify_lint_registry.py`로 prodRelease HTML lint report의 navigation common/compose/runtime registry 포함 여부를 재검증하고, focused UI smoke + exact alarm deny/allow gate(저장/enable + boot 복구 + receiver 재예약) + remaining connected Android suite를 수행한다. |
| Release Build | `.github/workflows/release-build.yml` | PR to `main`, push to `main`, manual | Signed prod release AAB artifact. No Play upload. |
| CD | `.github/workflows/play-deploy.yml` | `v*.*.*` tag, manual | Signed AAB build + Google Play upload. Tag/manual only. |
| Governance | `branch-hygiene.yml`, `version-guard.yml` | PR | Branch routing and Play-safe versionCode checks. |

This separation keeps code quality failures, release artifact failures, and Play Console/API failures easy to distinguish.

## Analytics / Release Handoff Boundary

릴리즈/핫픽스 PR에서 Android runtime / release QA가 green이어도, 그것만으로 GA4 `customEvent:*` queryability가 해결됐다고 보면 안 된다.

- analytics payload, screen name, activation/review/monetization 파라미터 계약이 바뀐 PR이면 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 같이 확인한다.
- PR 본문에는 **repo 코드·문서 반영 완료**와 **GA4 Admin 수동 등록 / metadata 재확인 / 배포 후 14일 재측정**을 분리해서 적는다.
- `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`은 no-data보다 **GA4 Admin registration gap**으로 먼저 해석한다.
- live metadata에 `customUser:routines_count`만 보인다고 해서 activation/review/monetization용 `customEvent:*` 축까지 queryable하다고 과대해석하지 않는다.

## Branch Naming

| 목적 | 브랜치 형식 | 예시 |
| --- | --- | --- |
| 기능 개발 | `feature/<short-kebab-case>` | `feature/emergency-unlock-settings` |
| 버그 수정 | `fix/<short-kebab-case>` | `fix/countdown-crash` |
| 리팩터링 | `refactor/<short-kebab-case>` | `refactor/usage-stats-store` |
| 문서 | `docs/<short-kebab-case>` | `docs/release-checklist` |
| CI/운영 | `ci/<short-kebab-case>` | `ci/play-deploy` |
| 잡무 | `chore/<short-kebab-case>` | `chore/bump-deps` |
| 테스트 | `test/<short-kebab-case>` | `test/routine-viewmodel` |
| 릴리즈 | `release/<version>` | `release/1.7.1` |
| 핫픽스 | `hotfix/<short-kebab-case>` | `hotfix/block-screen-crash` |

자동 검증: `.github/workflows/branch-hygiene.yml`가 PR 브랜치 이름과 PR 대상 브랜치를 검사한다.

## PR Routing Rules

| Head branch | Base branch | 이유 |
| --- | --- | --- |
| `feature/*`, `fix/*`, `refactor/*`, `docs/*`, `test/*`, `ci/*`, `chore/*` | `develop` | 일반 개발 통합 |
| `release/*` | `main` | 릴리즈 후보를 프로덕션 기준선으로 승격 |
| `hotfix/*` | `main` | 긴급 수정 우선 배포 |

릴리즈/핫픽스가 `main`에 들어간 뒤에는 반드시 `main -> develop` 역머지를 해서 두 브랜치를 동기화한다.

## Commit Convention

Conventional Commits 스타일을 사용한다. 본문은 한국어/영어 모두 가능하다.

| 접두사 | 용도 | 예시 |
| --- | --- | --- |
| `feat:` | 새 기능 | `feat: add emergency unlock settings` |
| `fix:` | 버그 수정 | `fix: prevent countdown crash` |
| `style:` | UI/스타일 | `style: polish routine menu` |
| `refactor:` | 구조 변경 | `refactor: extract usage stats repository` |
| `docs:` | 문서 | `docs: update release checklist` |
| `chore:` | 설정/잡무 | `chore: bump version to 1.7.1` |
| `test:` | 테스트 | `test: add routine viewmodel tests` |
| `ci:` | CI/CD | `ci: validate branch hygiene` |

## Version Management

Android 버전은 `app/build.gradle.kts`의 두 값으로 관리한다.

- `versionName`: 사용자에게 보이는 SemVer. 예: `1.7.1`
- `versionCode`: Google Play가 요구하는 단조 증가 정수. 이미 업로드된 값은 재사용 불가.

규칙:

1. `versionName`은 `MAJOR.MINOR.PATCH` 형식만 사용한다.
2. `versionCode`는 모든 Play 업로드마다 반드시 증가한다.
3. `main`으로 들어가는 `release/*`, `hotfix/*` PR은 `versionCode`가 기존 `main`보다 크고, Google Play tracks에서 보이는 최고 사용 `versionCode`보다도 커야 한다.
4. 태그 형식은 `v{versionName}`이다. 예: `v1.7.1`

자동 검증: `.github/workflows/version-guard.yml`는 `main` 대상 PR마다 항상 실행된다. 정상적인 `release/*` / `hotfix/*` PR에서는 `versionCode`가 `main`과 Google Play visible max보다 모두 큰지, `versionName`이 SemVer인지 검사하고, 다른 브랜치가 실수로 `main`을 향하면 governance gate로 즉시 실패시킨다.

워크플로 유지보수 기준: `version-guard.yml`의 `actions/checkout` major version은 저장소의 다른 governance/release workflow와 같은 현재 표준(v6)으로 맞춘다. 그래야 릴리즈 안전장치만 오래된 checkout runtime에 머무르는 drift를 막을 수 있다.

## Harness Scripts

모든 스크립트는 repo root에서 실행한다.

### 새 작업 브랜치 시작

```bash
scripts/branch-start.sh feature emergency-unlock-settings
scripts/branch-start.sh fix countdown-crash
```

동작:
- `develop` 기준 최신화
- `feature/<name>` 또는 `fix/<name>` 브랜치 생성

### 버전 bump

```bash
scripts/bump-version.sh 1.7.2 --service-account-json /path/to/play-service-account.json
scripts/bump-version.sh 1.7.2 --code 24 --fallback-play-max-version-code 23
```

동작:
- `app/build.gradle.kts`의 `versionName` 변경
- `versionCode` 자동 +1 또는 지정 값으로 변경
- Google Play visible max guard (`scripts/play_version_code_guard.py`) 검증
- Gradle release task dry-run으로 task 존재 확인

`bump-version.sh`는 저수준 helper이므로 maintenance/debug 상황에서만 `--no-dry-run`을 직접 사용할 수 있다. 일반 릴리즈 브랜치 준비는 아래 `release-start.sh`를 사용하고 dry-run 검증을 건너뛰지 않는다.

### 릴리즈 브랜치 준비

```bash
scripts/release-start.sh 1.7.2 --service-account-json /path/to/play-service-account.json
# 또는 live Play API 접근이 불가능하면 명시적 override
scripts/release-start.sh 1.7.2 --fallback-play-max-version-code 23
```

동작:
- 최신 기존 SemVer 태그가 production 배포 완료 marker를 가지고 있는지 먼저 확인
- `develop`에서 `release/1.7.2` 생성
- 버전 bump
- release dry-run 검증(상위 release-start에서는 `--no-dry-run`을 허용하지 않음)
- `chore: bump version to 1.7.2` 커밋 생성

### 릴리즈 production 완료 게이트

```bash
scripts/check-latest-production-deployed.sh
```

동작:
- 최신 기존 SemVer 태그(`vX.Y.Z`)를 찾는다.
- 해당 태그에 GitHub Deployment `environment=production` + `success` 상태가 있거나, GitHub Release 본문에 `<!-- stopit-production-deployed: vX.Y.Z -->` marker가 있으면 통과한다.
- marker가 없으면 새 릴리즈 시작/태그 생성을 중단한다.
- 긴급 상황에서만 명시 승인 후 `STOPIT_RELEASE_GATE_BYPASS=1`로 우회한다.

### 릴리즈 태그 배포

```bash
scripts/release-tag.sh 1.7.2
```

동작:
- 현재 브랜치가 `main`인지 확인
- 최신 기존 SemVer 태그가 production 배포 완료 marker를 가지고 있는지 다시 확인
- `versionName`과 태그 버전 일치 확인
- `v1.7.2` 태그 생성 및 push
- GitHub Actions CD가 `scripts/validate-play-deploy-ref.sh`로 태그가 `origin/main`에서 온 SemVer release tag인지, 직전 SemVer production marker가 있는지 다시 검증한 뒤 Google Play internal track 업로드 실행

### 릴리즈 준비 상태 점검

```bash
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH=/path/to/play-service-account.json scripts/check-release-readiness.sh
# 또는
STOPIT_PLAY_MAX_VERSION_CODE=23 scripts/check-release-readiness.sh
```

동작:
- `origin/main` 기준 검증 전에 먼저 `git fetch origin main`으로 remote ref를 최신화하고, fetch 실패 시 즉시 중단
- 현재 브랜치의 git working tree clean 여부 확인
- 버전 형식 확인
- Google Play visible max guard (`scripts/play_version_code_guard.py`) 검증
- `actionlint`가 있으면 workflow 문법 확인
- `:app:testProdReleaseUnitTest :app:bundleProdRelease --dry-run` 실행

## Standard Development Flow

```bash
# 1. 작업 브랜치 생성
scripts/branch-start.sh feature my-feature

# 2. 개발 + 로컬 검증
./gradlew :app:testDevDebugUnitTest
./gradlew :app:assembleProdDebug

# 3. 커밋/푸시/PR
git add <files>
git commit -m "feat: add my feature"
git push -u origin HEAD
gh pr create --base develop --fill

# 4. Android CI 통과 후 squash merge
```

## Flavor-aware Gradle Verification Matrix

`app` 모듈은 `dev` / `prod` flavor를 사용하므로 flavor-less 명령(`testDebugUnitTest`, `lintDebug`, `assembleDebug`)은 모호합니다. 기본 검증은 아래처럼 variant를 명시합니다.

| 상황 | 권장 명령 | 비고 |
| --- | --- | --- |
| 로컬 기본 JVM 검증 | `./gradlew :app:testDevDebugUnitTest` | 가장 빠른 기본 단위 테스트 |
| 로컬 lint 검증 | `./gradlew :app:lintDevDebug` | 개발 중 UI/리소스/lint 확인 |
| CI 스모크 빌드 | `./gradlew :app:assembleProdDebug` | prod flavor debug APK 생성 |
| 릴리즈 경로 JVM 검증 | `./gradlew :app:testProdReleaseUnitTest` | release variant 기준 테스트 |
| 릴리즈 번들 검증 | `./gradlew :app:bundleProdRelease` | 실제 Play 업로드 경로와 맞는 AAB |
| Android 프레임워크 검증 | `./gradlew :app:connectedDevDebugAndroidTest` | 서비스/리시버/권한/Room migration 등 |

루트 수준의 `./gradlew test`는 전체 JVM 테스트 집합을 돌릴 때 쓸 수 있지만, 문서/PR 템플릿/자동화의 대표 예시는 위 variant-specific `:app:` 태스크를 사용합니다.

의존성 업그레이드나 lint 기준선 정리 같은 maintenance slice는 `docs/DEPENDENCY_LINT_MAINTENANCE.md`를 source of truth로 보고, `gradle/libs.versions.toml`과 `app/build.gradle.kts`, `core/kds/build.gradle.kts`의 dependency source-of-truth drift를 함께 확인합니다. 특히 #175 계열 작업은 app 모듈만 보지 말고 `:core:kds`의 direct version 문자열까지 확인합니다.

Navigation/Compose custom lint 복구(`issue #156` 유형)에서는 `:app:lintDevDebug` / `:app:lintProdRelease` green만으로 충분하다고 보지 않습니다. Android CI와 Release QA는 둘 다 `scripts/verify_lint_registry.py`로 HTML lint report를 다시 읽어 `androidx.navigation.common`, `androidx.navigation.compose`, `androidx.navigation.runtime` registry와 `MissingSerializableAnnotation`, `MissingKeepAnnotation`, `WrongNavigateRouteType` issue id가 실제 report에 포함되고, `Requires newer lint; these checks will be skipped!` / `ObsoleteLintCustomCheck`가 없는지까지 확인합니다.

## Standard Release Flow

```bash
# 1. develop에서 릴리즈 브랜치 생성 + version bump
scripts/release-start.sh 1.7.2 --service-account-json /path/to/play-service-account.json
# 또는
scripts/release-start.sh 1.7.2 --fallback-play-max-version-code 23

# 2. 릴리즈 브랜치 push + main 대상 PR
git push -u origin HEAD
gh pr create --base main --title "release: 1.7.2" --body-file docs/RELEASE_CHECKLIST.md

# 3. PR에서 Branch Hygiene, Version Guard, Android CI, Android Release QA, Android Release Build 통과 확인
#    + analytics payload/queryability 영향이 있으면 PR 본문에 GA4 handoff(runbook 기준)까지 함께 기록

# 4. main에 squash merge

# 5. main 최신화 후 태그 배포. CD도 태그 ancestry + 직전 production marker를 재검증한 뒤에만 Google Play에 업로드한다.
git checkout main
git pull origin main
scripts/release-tag.sh 1.7.2

# 5-1. manual `workflow_dispatch`가 필요해도 같은 SemVer tag ref에서만 실행
# branch ref로 internal/alpha/beta/production 업로드 우회 금지

# 6. main -> develop 역머지
git checkout develop
git pull origin develop
git merge origin/main
git push origin develop
```

## Hotfix Flow

```bash
git checkout main
git pull origin main
git checkout -b hotfix/block-screen-crash

# 수정 + 버전 patch bump
scripts/bump-version.sh 1.7.3 --service-account-json /path/to/play-service-account.json
# 또는
scripts/bump-version.sh 1.7.3 --fallback-play-max-version-code 23
./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease

git add <files>
git commit -m "fix: prevent block screen crash"
git push -u origin HEAD
gh pr create --base main --fill
```

핫픽스가 main에 merge되고 태그 배포된 뒤에는 `main -> develop` 역머지를 한다.

## Safety Defaults

- 일반 CI는 Play 업로드도 signed release artifact 생성도 하지 않는다.
- Release Build는 signed AAB artifact만 만들고 Play 업로드는 하지 않는다.
- CD는 태그 또는 수동 실행에서만 Google Play에 업로드한다.
- manual `workflow_dispatch`도 SemVer tag ref에서만 허용되며, branch ref는 internal/alpha/beta/production 모두 거부한다.
- 자동 태그 배포는 Google Play `internal` track으로만 간다.
- 자동 태그 배포도 `scripts/validate-play-deploy-ref.sh`를 통과해야 하므로, `scripts/release-tag.sh`를 우회해 만든 SemVer tag는 `origin/main` ancestry 또는 직전 production marker gate에서 차단된다.
- `production` 배포는 수동 workflow dispatch로만 실행한다.
- secret 파일은 GitHub Secrets에서 복원하고 repo에 커밋하지 않는다.
- `versionCode`는 절대 재사용하지 않는다.
