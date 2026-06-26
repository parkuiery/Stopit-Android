# Test Source Lint Policy

이 문서는 #1091의 source of truth다. Stopit은 runtime/release evidence를 `app/src/test`와 `app/src/androidTest` helper에 크게 의존하지만, 현재 `app/build.gradle.kts`의 Android lint 설정은 `checkTestSources = false`로 test/androidTest source lint를 기본 lint gate에서 제외한다. 이 상태를 암묵적 예외로 두지 않고, 왜 제외되어 있는지와 어떤 대체 guard가 필요한지 명시한다.

## 현재 결정

현재 결정은 **즉시 전체 test source lint를 켜지 않고, 명시적 예외 + 대체 검증 + 재검토 게이트로 운영**하는 것이다.

이유:

1. `app/src/androidTest`는 실제 Android framework, emulator, AppOps, notification channel, AccessibilityService, Room migration, Firebase/Firebase Messaging 경계를 많이 건드린다. 전체 test source lint를 바로 켜면 제품 회귀가 아니라 test harness/host setup 성격 warning이 대량으로 드러날 수 있다.
2. Release QA와 Android CI는 이미 테스트 소스의 **실행 가능성**을 여러 계층으로 검증한다. 단, 실행 가능성과 lint 정합성은 같은 것이 아니므로 `checkTestSources = false`가 영구 면제가 되어서는 안 된다.
3. test/androidTest source lint 활성화는 별도 QA/code package에서 warning baseline을 캡처하고, release/runtime 신뢰도에 영향을 주는 warning부터 줄인 뒤 켜야 한다.

따라서 현 시점의 정책은 다음과 같다.

- 기본 `checkTestSources = false`는 유지할 수 있지만, 반드시 이 문서와 `scripts.tests.test_test_source_lint_policy`가 함께 존재해야 한다.
- QA/code lane에서 baseline을 캡처할 때는 기본 CI gate를 바꾸지 않고 `-Pstopit.lint.checkTestSources=true`를 붙여 test/androidTest source lint를 opt-in으로 켠다.
- test/androidTest source lint를 계속 제외하는 동안에는 아래 대체 guard를 유지한다.
- 이 정책을 바꾸는 PR은 `app/build.gradle.kts`, 이 문서, `docs/ANDROID_SKILLS_TESTING_QA.md`, `docs/ops/stopit/release-context.md`, 관련 static contract test를 함께 갱신해야 한다.

## 대체 guard

### 1. Test source 실행 가능성

Android CI / Release QA는 test source를 lint하지 않더라도 아래 실행 경계로 test helper drift를 잡는다.

- JVM/unit test source: `:app:testDevDebugUnitTest`, `:app:testProdReleaseUnitTest`
- Android instrumentation source: `:app:connectedDevDebugAndroidTest` 기반 Android CI runtime smoke와 Release instrumentation QA
- Runtime suite manifest: `scripts.tests.test_android_runtime_suites_manifest`
  - 새 `app/src/androidTest` class가 runtime suite 또는 명시적 제외 목록 없이 추가되면 실패해야 한다.

### 2. Static policy tests

Android CI `Fast verification`와 Release QA `Full release QA`는 Gradle lint와 별개로 Python/static policy tests를 먼저 실행한다.

- `scripts.tests.test_android_manifest_contract`
- `scripts.tests.test_sensitive_logging_policy`
- `scripts.tests.test_compose_icon_button_accessibility`
- `scripts.tests.test_locale_string_parity`
- `scripts.tests.test_locale_string_quality_contract`
- `scripts.tests.test_lint_registry_workflows`
- `scripts.tests.test_test_source_lint_policy`

이 묶음은 test source lint의 완전한 대체가 아니라, release/runtime 신뢰도에 중요한 manifest/locale/accessibility/logging/workflow policy drift를 빠르게 차단하는 최소 guard다.

### 3. Lint registry 검증

`checkTestSources = false`가 있더라도 앱 source lint가 Navigation/Compose custom lint registry를 실제로 포함하는지는 계속 확인한다.

- Android CI: `:app:lintDevDebug` + `scripts/verify_lint_registry.py --report app/build/reports/lint-results-devDebug.html`
- Release QA / Release Build: `:app:lintProdRelease` + prodRelease lint registry verification

이 검증은 production source lint registry coverage를 보장하지만, test/androidTest source warning coverage는 보장하지 않는다.

## 재검토 트리거

아래 중 하나가 발생하면 #1091 후속 QA/code package에서 `checkTestSources` 활성화를 다시 검토한다.

1. test helper나 instrumentation code의 deprecated/unsafe API 사용이 release/runtime failure의 원인으로 확인된다.
2. 새 AndroidTest helper가 runtime suite manifest에는 잡히지만 lint/static policy가 놓친 Android resource/API misuse를 만든다.
3. AGP/Android lint 업그레이드 후 test source lint warning baseline이 작아져 한 PR에서 정리 가능해진다.
4. release/hotfix PR에서 test source drift 때문에 runtime evidence를 신뢰하기 어렵다는 blocker가 반복된다.

## 활성화 패키지 요구사항

`checkTestSources`를 켜는 PR은 단순 토글로 끝내지 않는다. 최소 요구사항:

1. RED baseline: 현재 branch에서 `./gradlew --console=plain :app:lintDevDebug -Pstopit.lint.checkTestSources=true`로 test/androidTest source warning을 캡처한다.
2. Warning triage: release/runtime 신뢰도에 영향을 주는 warning과 harmless/test-harness warning을 분리한다.
3. GREEN: `checkTestSources = true` 또는 좁은 allowlist/별도 test-source lint task를 도입하고, 발생 warning을 정리하거나 명시적으로 제외한다.
4. CI: Android CI 또는 Release QA에서 의도한 gate가 materialize되는지 static contract test로 고정한다.
5. 문서: 이 문서, `docs/ANDROID_SKILLS_TESTING_QA.md`, `docs/ops/stopit/release-context.md`의 경계를 동시에 갱신한다.

## 운영 증거 템플릿

PR/이슈 코멘트에는 아래 형식으로 남긴다.

```md
## test source lint policy evidence
- `app/build.gradle.kts` lint setting: `checkTestSources = false|true`
- 선택한 정책: `explicit-exclusion-with-guards` / `enabled` / `separate-test-lint-task`
- 실행한 guard:
  - `python3 -m unittest scripts.tests.test_test_source_lint_policy -v`
  - `python3 -m unittest scripts.tests.test_lint_registry_workflows -v`
  - `./gradlew -q help --task :app:lintDevDebug`
  - `./gradlew --console=plain :app:lintDevDebug -Pstopit.lint.checkTestSources=true`
- 남은 경계:
  - test/androidTest source lint warning baseline 캡처
  - warning triage 및 활성화/allowlist 결정
```

## 현재 남은 경계

이 문서/contract PR은 `checkTestSources = false`를 **의식적인 정책 예외**로 고정하고, 관련 운영 문서와 static guard를 연결한다. 후속 QA/code lane은 `-Pstopit.lint.checkTestSources=true` opt-in baseline으로 warning을 캡처한 뒤, 활성화 또는 allowlist 정책을 구현해야 한다.

## 2026-06-27 opt-in baseline (#1091)

QA lane에서 `-Pstopit.lint.checkTestSources=true`를 실제로 켠 뒤 `:app:lintDevDebug`를 실행해 baseline을 캡처했다.

- 명령: `./gradlew --no-daemon --console=plain :app:lintDevDebug -Pstopit.lint.checkTestSources=true`
- 환경: local qa-lane, Corretto 17, `GRADLE_OPTS=-XX:-TieredCompilation`
- 결과: `BUILD SUCCESSFUL`, `app/build/reports/lint-results-devDebug.html` 기준 `232 warnings`
- 상위 warning group:
  - `StringFormatCount`: 50
  - `PluralsCandidate`: 36
  - `UnusedResources`: 36
  - `MissingQuantity`: 25
  - `GradleDependency`: 21
  - `AutoboxingStateValueProperty`: 8
  - `UseKtx`: 7
- 런타임/릴리즈 신뢰도에 직접 가까운 후속 triage 후보:
  - `HardwareIds` 1건: test/androidTest helper인지 production source인지 먼저 분리한다.
  - locale/plural 계열(`StringFormatCount`, `PluralsCandidate`, `MissingQuantity`, `UnusedQuantity`): shipped locale 품질 guard와 겹치므로 `docs/LOCALE_STRING_QUALITY.md` 기준으로 실제 사용자 copy 영향 여부를 분리한다.
  - `ConfigurationScreenWidthHeight`, `LocalContextResourcesRead`, `ModifierParameter`: Compose UI/test helper warning인지 production UI warning인지 분리한다.

주의: 같은 lint 경로가 macOS/Corretto 환경에서 `org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService::getArgumentForParameter` JIT crash를 낸 적이 있다. 이 경우 결과를 코드 회귀로 보지 말고 Corretto 17 + `GRADLE_OPTS=-XX:-TieredCompilation`으로 재실행해 baseline을 캡처한다.
