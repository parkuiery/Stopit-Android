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
| CI | `.github/workflows/android-ci.yml` | PR to `develop`/`main`, push to `develop`/`main`, manual | Fast verification (`:app:testDevDebugUnitTest`, `:app:lintDevDebug`, `:app:assembleProdDebug`) plus `scripts.tests.test_android_manifest_contract`로 manifest/backup static policy를, `scripts/verify_lint_registry.py`로 devDebug HTML lint report의 navigation common/compose/runtime registry와 핵심 issue id를 강제 확인하고, PR/manual focused runtime smoke를 수행한다. No signed release, no Play upload. |
| Ops CI | `.github/workflows/ops-ci.yml` | PR/push touching `functions/`, `scripts/promote-google-play-track.js`, `scripts/notify-discord-deploy.py`, release-helper guardrail scripts (`scripts/check-release-readiness.sh`, `scripts/check-latest-production-deployed.sh`, `scripts/release-start.sh`, `scripts/bump-version.sh`, `scripts/validate-play-deploy-ref.sh`, `scripts/validate-play-rollout-inputs.js`, `scripts/release-tag.sh`, `scripts/check-play-deploy-secret-contract.sh`, `scripts/setup-play-deploy-secrets.sh`, `scripts/setup-discord-deploy-secrets.sh`, `scripts/play_version_code_guard.py`, `scripts/release_provenance_manifest.py`, `scripts/verify_lint_registry.py`, `scripts/check_workflow_gradle_tasks.py`), `scripts/tests/**`, `tools/aso-screenshots/**`, `.github/workflows/**`, `docs/**`, `**/*.md`, or manual | `Workflow syntax lint` runs `actionlint` for every `.github/workflows/**` change. `tools/aso-screenshots/**` changes materialize `ASO screenshots build`, which runs `bun install --frozen-lockfile` and `bun run build` in `tools/aso-screenshots`; this is Android 앱 빌드와 분리된 Next/Bun generator gate with no Gradle, Firebase signing, or Play deploy secret usage. For release/CI/CD workflow 변경 PR (`android-ci.yml`, `release-qa.yml`, `release-build.yml`, `play-deploy.yml`, `version-guard.yml`), Ops CI must also materialize `Docs/runbook contract tests`; actionlint-only green은 YAML 문법 확인일 뿐이고 contract-test green이 operator docs/runbook/source-of-truth drift까지 검증한다. `Docs/runbook contract tests` is a lightweight gate for runbook/source-of-truth drift and runs selected Python contract tests (`scripts.tests.test_play_deploy_secret_contract_runbook`, `scripts.tests.test_release_build_workflow_scope`, `scripts.tests.test_release_qa_runtime_gate_docs`, `scripts.tests.test_android_ci_runtime_smoke_docs`, `scripts.tests.test_release_guard_hotfix_sync`, `scripts.tests.test_release_provenance_workflow_contract`, `scripts.tests.test_acquisition_attribution_docs_contract`, `scripts.tests.test_signed_aab_lint_gate`, `scripts.tests.test_review_prompt_post_release_followthrough_docs`, `scripts.tests.test_workflow_gradle_task_guard`, `scripts.tests.test_release_gradle_task_contract`, `scripts.tests.test_aso_screenshots_ci_contract`, `scripts.tests.test_branch_hygiene_policy`, `scripts.tests.test_ops_ci_workflow`, `scripts.tests.test_actionlint_gate`) without `npm ci`, Gradle, or emulator work. Functions and release-helper jobs remain path-classified: Firebase Functions runs Node 22 `npm ci` / `npm run lint` / `npm test`; release-helper scripts run Play promotion/staged rollout checks, release-helper Python discover (`python3 -m unittest discover -s scripts/tests -p 'test_*.py'`), shell syntax, and Python compile gates. No Android build, signed release artifact, or Play upload. |
| Release QA | `.github/workflows/release-qa.yml` | `release/* -> main`, `hotfix/* -> main`, manual | Full release JVM/build gate first runs static policy tests including `scripts.tests.test_android_manifest_contract` for sensitive permissions, exported components, AccessibilityService metadata, and backup/data-extraction XML scope, then `scripts/verify_lint_registry.py`로 prodRelease HTML lint report의 navigation common/compose/runtime registry 포함 여부를 재검증하고, focused UI smoke + exact alarm deny/allow gate(저장/enable + boot 복구 + receiver 재예약) + remaining connected Android suite를 수행한다. |
| Release Build | `.github/workflows/release-build.yml` | `release/* -> main`, `hotfix/* -> main`, or manual dispatch from `main`/`release/*`/`hotfix/*`/SemVer tag refs | Signed prod release AAB artifact. Before the signed AAB is built it runs `:app:lintProdRelease` plus `scripts/verify_lint_registry.py` against the prodRelease lint report. No Play upload. Direct push to `main` does not trigger signed artifact generation; use release/hotfix PR gates or explicit manual dispatch from an allowed release ref. Manual dispatch from feature/docs/automation branches fails before signing secrets are decoded. |
| CD | `.github/workflows/play-deploy.yml` | `v*.*.*` tag, manual | Non-production build/upload runs signed AAB build + Google Play upload, after `:app:lintProdRelease` and prodRelease lint registry verification. Production promotion uses an existing internal release and does not run `:app:lintProdRelease`, build, or upload a new AAB. |
| Governance | `branch-hygiene.yml`, `version-guard.yml` | PR | Branch routing and Play-safe versionCode checks. |

This separation keeps code quality failures, release artifact failures, and Play Console/API failures easy to distinguish.

Android CI path gating contract:
- `gradlew` / `gradlew.bat`, root Gradle config files, `scripts/verify_lint_registry.py`, and `.github/workflows/android-ci.yml` are treated as **build-critical** root/script inputs.
- wrapper-only, Gradle-launcher-only, or lint-registry-verifier-only PRs must still materialize `Fast verification`; they should not look green because Android CI was skipped.
- Dependabot PR은 repository Firebase secret을 사용할 수 없는 보안 경계가 있을 수 있다. `Fast verification`과 `Runtime smoke gate`는 각각 Firebase config availability를 먼저 확인하고, actor가 `dependabot[bot]`이며 `GOOGLE_SERVICES_JSON_DEV` / `GOOGLE_SERVICES_JSON`가 비어 있으면 job summary에 `Dependabot Firebase secret boundary`를 남긴 뒤 Gradle app tasks / emulator runtime smoke를 neutral-deferred 처리한다. 내부 브랜치 PR과 `workflow_dispatch`에서는 같은 secret 누락을 계속 hard fail로 유지하며, Dependabot dependency PR은 리뷰 후 trusted branch 또는 수동 `workflow_dispatch`에서 runtime smoke를 재실행한다.
- `stopit-prod-debug-apk` is a short-lived PR/smoke artifact. Android CI keeps it at `retention-days: 7`, while signed release artifacts remain longer-lived (`30` days in Release Build / non-production Play Deploy). The upload step is `non-blocking` (`continue-on-error: true`) because the artifact is optional after build/test success. If a run reports `Upload prod debug APK` with `Artifact storage quota has been hit`, treat it as GitHub Actions storage/quota exhaustion rather than an app/test regression; clean or wait for artifact expiry and GitHub's 6–12 hour quota recalculation, then rerun the same current-head checks when artifact readback is needed.

Play deploy secret/setup contract:
- `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md` is the source of truth for Play deploy secret ownership, helper scope, and the `GOOGLE_SERVICES_JSON` restore matrix.
- Dev/prod `applicationId` or package identity changes follow `docs/FLAVOR_APPLICATION_ID_CONTRACT.md`: dev runtime identity may split, but release/Play deploy must keep production package `com.uiery.keep`.
- `scripts/setup-play-deploy-secrets.sh` only configures Android/Play build-upload secrets; Discord deploy notification secrets use `scripts/setup-discord-deploy-secrets.sh` or direct `gh secret set`.
- `DISCORD_DEPLOY_CHANNEL_ID` exists in two stores when Discord production approval is enabled: GitHub Actions repo secret for deploy notification, and Firebase Functions secret for interaction channel verification.
- Production Play deploy approval is enforced in two layers: Discord verifies channel/user/role before dispatching, and `.github/workflows/play-deploy.yml` routes `track=production` runs into the GitHub Environment named `production`, which must have required reviewer approval configured in repository settings. Direct GitHub `workflow_dispatch` and Discord dispatches share that same final Environment gate.
- Release/operator evidence should run or reference `scripts/check-play-deploy-secret-contract.sh` when Play deploy, Discord deploy, workflow secret restore, or Firebase Functions promotion wiring changed.

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
| 자동 의존성 PR | `dependabot/<ecosystem>/<path>/<group>` | `dependabot/npm_and_yarn/functions/functions-npm-patch-minor-...` |
| 릴리즈 | `release/<version>` | `release/1.7.1` |
| 핫픽스 | `hotfix/<short-kebab-case>` | `hotfix/block-screen-crash` |

자동 검증: `.github/workflows/branch-hygiene.yml`가 PR 브랜치 이름과 PR 대상 브랜치를 검사한다.

Automation lane 브랜치인 `automation/*`는 PR head prefix가 아니라 **로컬 lane/worktree 안정 브랜치 전용(local lane stable branch)**이다. 예를 들어 docs lane은 `automation/stopit-docs-lane`에서 최신 `origin/develop`을 따라가다가, reviewable 작업을 만들 때는 `docs/issue-629-branch-hygiene-policy`처럼 `docs/issue-...` 또는 workflow/운영 변경이면 `ci/issue-...` 브랜치를 새로 만든다. `automation/*`를 PR head로 열면 Branch Hygiene가 의도적으로 실패해야 한다.

## PR Routing Rules

| Head branch | Base branch | 이유 |
| --- | --- | --- |
| `feature/*`, `fix/*`, `refactor/*`, `docs/*`, `test/*`, `ci/*`, `chore/*` | `develop` | 일반 개발 통합 |
| `dependabot/*` | `develop` | `.github/dependabot.yml`에서 생성되는 자동 의존성 PR |
| `release/*` | `main` | 릴리즈 후보를 프로덕션 기준선으로 승격 |
| `hotfix/*` | `main` | 긴급 수정 우선 배포 |

로컬 lane/worktree 안정 브랜치(`automation/stopit-docs-lane`, `automation/stopit-qa-lane`, `automation/stopit-code-lane`, `automation/stopit-merge-lane`, `automation/stopit-release-lane`)는 위 PR routing table에 포함하지 않는다. lane cron은 stable automation branch를 기준선으로만 사용하고, 실제 PR은 변경 성격에 맞는 `docs/*`, `test/*`, `fix/*`, `feature/*`, `ci/*`, `chore/*` head branch에서 만든다.

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

자동 검증: `.github/workflows/version-guard.yml`는 `main` 대상 PR마다 항상 실행된다. 정상적인 `release/*` / `hotfix/*` PR에서는 `versionCode`가 `main`과 Google Play visible max보다 모두 큰지, `versionName`이 SemVer인지 검사하고, 다른 브랜치가 실수로 `main`을 향하면 governance gate로 즉시 실패시킨다. app/runtime/build-critical main-target PR(`app/**`, `core/**`, Gradle wrapper/root Gradle 설정 등)은 `app/build.gradle.kts`가 직접 바뀌지 않았더라도 `Version Guard`가 Play service account 복원과 versionCode API 검증을 실행하므로, versionCode bump 누락 상태로 통과할 수 없다. workflow-only / governance-only / docs-only main-target hotfix는 `Version Guard` job을 계속 표시하되 `Classify Version Guard scope` 결과로 Play service account 복원과 versionCode API 검증을 skip한다.

워크플로 유지보수 기준: `Version Guard`, `Release QA`, `Release Build`, `Play Deploy`, `Ops CI`, `Android CI`, `Branch Hygiene`처럼 릴리즈/거버넌스 신호를 만드는 workflow의 `actions/checkout` major version은 저장소의 현재 표준(v6)으로 함께 맞춘다. 그래야 릴리즈 안전장치나 PR routing gate만 오래된 checkout runtime에 머무르는 drift를 막을 수 있다. Gradle을 실행하거나 release artifact/Play 배포 secret을 다루는 `Android CI`, `Release QA`, `Release Build`, `Play Deploy` workflow는 checkout 직후 `Gradle Wrapper`를 `gradle/actions/wrapper-validation@v6`로 검증해야 하며, 이 단계는 `Set up Gradle`, signing/Firebase/Play secret 검증·decode보다 앞에 있어야 한다. `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` 변경 PR은 이 wrapper-validation 증적과 contract-test green을 PR 본문에 남긴다. 이 순서는 `scripts.tests.test_actionlint_gate`가 회귀 방지한다. `play-deploy.yml`의 tag-push guard는 `scripts/validate-play-deploy-ref.sh`가 GitHub release/deployment state를 `gh`로 조회하므로 Actions 안에서 `GH_TOKEN: ${{ github.token }}`를 반드시 전달해야 한다. main에서 고친 이 계약은 다음 릴리즈 sync 전에 `develop`에도 유지되어야 한다.

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
- `README.md` 상단의 `현재 버전` 라인을 같은 `versionName/versionCode`로 동기화
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
- 버전 bump (`app/build.gradle.kts`와 `README.md` 현재 버전 라인을 함께 갱신)
- release dry-run 검증(상위 release-start에서는 `--no-dry-run`을 허용하지 않음)
- release branch 생성 후 `scripts/check-release-readiness.sh` quick preflight로 `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, `scripts/verify_lint_registry.py`, `:app:bundleProdRelease --dry-run`까지 확인
- `chore: bump version to 1.7.2` 커밋 생성

### 릴리즈 production 완료 게이트

```bash
scripts/check-latest-production-deployed.sh
```

동작:
- 최신 기존 SemVer 태그(`vX.Y.Z`)를 찾는다.
- 해당 태그에 GitHub Deployment `environment=production` + `success` 상태가 있거나, GitHub Release 본문에 `<!-- stopit-production-deployed: vX.Y.Z -->` marker가 있으면 통과한다.
- 이 marker는 Play Deploy가 `track=production` + `release_status=completed`로 성공했을 때만 production 완료로 기록한다. `draft`, `inProgress`, `halted` 상태는 production 완료 marker를 쓰지 않는다.
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
- tag 생성 전 `README.md 현재 버전 라인`이 `app/build.gradle.kts`의 `versionName/versionCode`와 일치하는지 최종 확인
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
- `ACTIONLINT_VERSION=1.7.12`에 맞춘 `actionlint` workflow 문법 확인. `actionlint`가 없거나 설치된 `actionlint --version`이 pinned `1.7.12`와 다르면 release readiness는 skip하지 않고 중단하므로, 로컬 preflight 전에 같은 pinned version을 설치한 뒤 재시도한다.
- quick preflight로 `:app:testProdReleaseUnitTest`, `:app:lintProdRelease`, `scripts/verify_lint_registry.py`, `:app:bundleProdRelease --dry-run` 실행
- Signed AAB provenance 생성/검증은 로컬 quick preflight가 아니라 Android Release Build workflow가 실제 signed artifact 옆의 `release-provenance.json`으로 검증한다.
- production promotion은 prior internal Play Deploy artifact의 `release-provenance.json`을 검증한다. 해당 `stopit-prod-release-signed-aab` artifact는 30-day evidence surface이므로, 그 이후 승격은 same-tag GitHub Release asset의 secret-free `release-provenance.json` durable fallback을 metadata-only로 검증하거나 같은 SemVer tag의 non-production Play Deploy 재실행으로 prior provenance를 복구한 뒤 진행한다.

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

`app` 모듈은 `dev` / `prod` flavor를 사용하므로 flavor-less 명령(`testDebugUnitTest`, `lintDebug`, `assembleDebug`)은 모호합니다. 기본 검증은 아래처럼 variant를 명시합니다. GitHub Actions workflow 안에서는 variant만 명시한 root task inference(`testDevDebugUnitTest`, `lintDevDebug`, `assembleProdDebug`, `connectedDevDebugAndroidTest` 등)도 금지하고, 반드시 `:app:` module-qualified task를 사용합니다.

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

Dependabot 자동 업데이트 정책(#693)은 `.github/dependabot.yml`이 source of truth입니다. Dependabot은 Gradle(`/`), GitHub Actions(`/`), Firebase Functions npm(`/functions`), ASO screenshot Bun(`/tools/aso-screenshots`) 생태계를 weekly로 감지하고, PR에는 `maintenance`, `automation`, `dependencies` labels를 붙입니다. patch/minor는 weekly group으로 묶되, **major update**는 자동 batch에서 제외하고 수동 검토 대상으로 남깁니다. Dependabot PR head는 `dependabot/*`이며 Branch Hygiene는 이를 `develop` 대상으로 허용합니다. Dependabot PR은 Play deploy, release secret, signing secret 변경 경계가 아니다. Android CI에서 repository Firebase secret이 비어 있으면 Dependabot PR의 app Gradle verification / runtime smoke는 `Dependabot Firebase secret boundary`로 neutral-deferred 처리하고, trusted branch 또는 수동 `workflow_dispatch`에서 `GOOGLE_SERVICES_JSON_DEV` / `GOOGLE_SERVICES_JSON`가 있는 상태로 다시 실행해 runtime-sensitive dependency evidence를 채운다. runtime-sensitive dependency가 포함되면 `docs/QA_RUNTIME_CHECKLIST.md` evidence 요구사항을 별도로 확인합니다.

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
# 선택 tag도 origin/main reachable + 직전 production marker guard를 통과해야 함

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
- Release Build는 signed AAB artifact만 만들고 Play 업로드는 하지 않는다. artifact 생성 전 `:app:lintProdRelease`와 `scripts/verify_lint_registry.py`로 prodRelease lint registry를 재확인한다. Manual dispatch는 `main`, `release/*`, `hotfix/*`, 또는 SemVer tag ref에서만 signing secrets와 signed release artifact 경로에 도달한다.
- CD는 태그 또는 수동 실행에서만 Google Play에 업로드한다. non-production build/upload 경로는 signed AAB 업로드 전 같은 prod lint/registry gate를 실행하고, production promotion은 기존 internal release 승격만 하므로 `:app:lintProdRelease`를 실행하지 않는다.
- manual `workflow_dispatch`도 SemVer tag ref에서만 허용되며, branch ref는 internal/alpha/beta/production 모두 거부한다. 선택한 tag 역시 `scripts/validate-play-deploy-ref.sh`로 `origin/main` ancestry와 직전 production marker gate를 통과해야 하므로 tag-push CD와 같은 release provenance/sequence guard를 공유한다.
- 자동 태그 배포는 Google Play `internal` track으로만 간다.
- tag-push와 manual dispatch 모두 `scripts/validate-play-deploy-ref.sh`를 통과해야 하므로, `scripts/release-tag.sh`를 우회해 만든 SemVer tag는 `origin/main` ancestry 또는 직전 production marker gate에서 차단된다.
- `production` 배포는 수동 workflow dispatch로만 실행한다.
- secret 파일은 GitHub Secrets에서 복원하고 repo에 커밋하지 않는다.
- `versionCode`는 절대 재사용하지 않는다.
