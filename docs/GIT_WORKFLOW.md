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
| CI | `.github/workflows/android-ci.yml` | PR to `develop`/`main`, push to `develop`/`main`, manual | Fast correctness check: unit tests + prod debug APK artifact. No signed release, no Play upload. |
| Release Build | `.github/workflows/release-build.yml` | PR to `main`, push to `main`, manual | Signed prod release AAB artifact. No Play upload. |
| CD | `.github/workflows/play-deploy.yml` | `v*.*.*` tag, manual | Signed AAB build + Google Play upload. Tag/manual only. |
| Governance | `branch-hygiene.yml`, `version-guard.yml` | PR | Branch routing and Play-safe versionCode checks. |

This separation keeps code quality failures, release artifact failures, and Play Console/API failures easy to distinguish.

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
3. `main`으로 들어가는 `release/*`, `hotfix/*` PR은 `versionCode`가 기존 `main`보다 커야 한다.
4. 태그 형식은 `v{versionName}`이다. 예: `v1.7.1`

자동 검증: `.github/workflows/version-guard.yml`는 `main` 대상 PR마다 항상 실행된다. 정상적인 `release/*` / `hotfix/*` PR에서는 `versionCode` 증가와 `versionName` SemVer 형식을 검사하고, 다른 브랜치가 실수로 `main`을 향하면 governance gate로 즉시 실패시킨다.

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
scripts/bump-version.sh 1.7.2
scripts/bump-version.sh 1.7.2 --code 24
```

동작:
- `app/build.gradle.kts`의 `versionName` 변경
- `versionCode` 자동 +1 또는 지정 값으로 변경
- Gradle release task dry-run으로 task 존재 확인

### 릴리즈 브랜치 준비

```bash
scripts/release-start.sh 1.7.2
```

동작:
- 최신 기존 SemVer 태그가 production 배포 완료 marker를 가지고 있는지 먼저 확인
- `develop`에서 `release/1.7.2` 생성
- 버전 bump
- release dry-run 검증
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
- GitHub Actions CD가 Google Play internal track 업로드 실행

### 릴리즈 준비 상태 점검

```bash
scripts/check-release-readiness.sh
```

동작:
- git working tree clean 여부 확인
- 버전 형식 확인
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

의존성 업그레이드나 lint 기준선 정리 같은 maintenance slice는 `docs/DEPENDENCY_LINT_MAINTENANCE.md`를 source of truth로 보고, version catalog와 `app/build.gradle.kts`의 direct dependency version을 함께 확인합니다.

## Standard Release Flow

```bash
# 1. develop에서 릴리즈 브랜치 생성 + version bump
scripts/release-start.sh 1.7.2

# 2. 릴리즈 브랜치 push + main 대상 PR
git push -u origin HEAD
gh pr create --base main --title "release: 1.7.2" --body-file docs/RELEASE_CHECKLIST.md

# 3. PR에서 branch-hygiene, version-guard, Android CI, Android Release Build 통과 확인

# 4. main에 squash merge

# 5. main 최신화 후 태그 배포. 이때만 CD가 Google Play에 업로드한다.
git checkout main
git pull origin main
scripts/release-tag.sh 1.7.2

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
scripts/bump-version.sh 1.7.3
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
- 자동 태그 배포는 Google Play `internal` track으로만 간다.
- `production` 배포는 수동 workflow dispatch로만 실행한다.
- secret 파일은 GitHub Secrets에서 복원하고 repo에 커밋하지 않는다.
- `versionCode`는 절대 재사용하지 않는다.
