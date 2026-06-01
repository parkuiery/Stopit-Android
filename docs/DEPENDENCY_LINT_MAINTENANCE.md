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
- KDS 의존성 선언: `core/kds/build.gradle.kts`
- lint 보고서 출력 위치: `app/build/reports/lint-results-devDebug.txt`
- flavor-aware 기본 검증 명령: `docs/GIT_WORKFLOW.md`
- ops 컨텍스트: `docs/ops/stopit/engineering-context.md`

주의:

- 이 저장소는 버전 카탈로그를 **원칙적인 source of truth**로 두지만, 아직 모든 의존성이 TOML alias로 완전히 통일된 것은 아니다.
- 2026-05-29 기준으로 direct version drift의 실제 잔여 위치는 `app`보다 `core:kds` 쪽이 더 중요하다.
  - `core/kds/build.gradle.kts`
    - `org.jetbrains.kotlinx:kotlinx-datetime:0.6.1`
    - `com.google.android.gms:play-services-ads:23.0.0`
    - `androidx.lifecycle:lifecycle-runtime-compose:2.9.3`
- 반면 `gradle/libs.versions.toml`에는 이미 아래 alias가 존재한다.
  - `kotlinx-datetime`
  - `google-play-services-ads`
- 즉 `kotlinx-datetime` / `play-services-ads`는 catalog entry가 있는데도 `core/kds/build.gradle.kts`에 direct string이 남은 상태다.
- `lifecycle-runtime-compose`는 현재 catalog alias 자체가 없으므로, direct version을 유지할지 아니면 새 alias를 추가할지 maintenance batch에서 함께 판단해야 한다.
- 따라서 드리프트 점검은 `libs.versions.toml`만 읽고 끝내면 안 되고, 최소한 `app/build.gradle.kts`와 `core/kds/build.gradle.kts`를 같이 확인해야 한다.

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
- `core:kds`의 `play-services-ads 23.0.0 -> catalog alias 전환 (+ 필요 시 버전 업그레이드 분리)`: 수익화/런타임 QA 범위가 커서 별도 검토 권장
- `core:kds`의 `lifecycle-runtime-compose 2.9.3`: catalog alias 신설 여부와 Compose/Lifecycle stack 정합성 검토 필요
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

## docs-lane에서 허용되는 작은 slice 예시

이 lane에서는 아래처럼 **운영 문서만** 다루는 것이 안전하다.

- lint/업그레이드 runbook 추가
- dependency grouping 기준 문서화
- release checklist에 dependency-maintenance evidence 요구사항 연결
- flavor-aware verification 예시 정리

이 lane에서 하지 않는 것:

- 실제 Gradle/plugin/library 버전 bump
- CI workflow 동작 변경
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
