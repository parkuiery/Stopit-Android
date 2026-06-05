# 공유 UI 소유권 / feature-private import 경계

Issue: #492

이 문서는 Stopit Android에서 feature-private Compose component를 다른 feature가 직접 가져다 쓰는 구조를 정리하기 위한 docs/ops source of truth다. 목표는 모든 UI를 무조건 KDS로 옮기는 것이 아니라, **재사용 범위에 맞는 소유권 계층**을 고정해 화면 간 결합과 drift를 줄이는 것이다.

## 왜 중요한가

- feature-private UI를 다른 feature가 import하면 원래 feature의 UX 변경이 소비 feature를 의도치 않게 깨뜨릴 수 있다.
- 같은 의미의 picker/dialog/button이 두 벌로 남으면 accessibility label, haptic, color/typography, QA evidence가 화면별로 어긋난다.
- shared UI 이동은 코드 이동만으로 끝나지 않는다. call site, string/resource parity, test/architecture guard, KDS/app-shared 문서까지 함께 맞아야 한다.

## 소유권 계층

| 계층 | 경로 | 사용 기준 | 예시 |
| --- | --- | --- | --- |
| KDS primitive | `core/kds/src/main/java/com/uiery/kds` | app 리소스/도메인 모델 없이 재사용 가능한 디자인 시스템 primitive | `KeepButton`, `KeepCheckbox`, `KeepSwitch` |
| app shared UI | `app/src/main/java/com/uiery/keep/ui/component` | 여러 app feature에서 공유하지만 app string/resource/domain boundary에 의존하는 UI | `CategoryButton`, `CategoryBottomSheetContent`, `TimerPicker` |
| app-level domain boundary | `app/src/main/java/com/uiery/keep/appselection` 등 | UI가 아니라 여러 feature가 공유하는 repository/policy/model boundary | `InstalledAppRepository`, `SelectableAppPolicy` |
| feature-private UI | `app/src/main/java/com/uiery/keep/feature/<feature>/component` | 해당 feature screen/use case 안에서만 쓰는 UI | emergency unlock duration chip, feature-local row/card |

## Import 규칙

허용:

- feature screen → 같은 feature의 `component` package.
- feature screen → `com.uiery.keep.ui.component` app shared UI.
- feature screen → `com.uiery.kds` KDS primitive.
- app shared UI → app-level domain/resource boundary.

금지:

- feature A → feature B의 `component` package 직접 import.
- `app/src/main/java/com/uiery/keep/ui/component` → `com.uiery.keep.feature.*` import.
- feature-private component의 move stub / duplicate copy를 남겨 재도입 여지를 만드는 것.

예외는 code-lane PR에서 명시적인 사유와 제거 계획을 남긴 경우에만 허용한다. 새 예외는 `scripts/tests/test_shared_ui_component_boundaries.py`에 allowlist 대신 문서화된 failure로 추가하는 것이 아니라, 가능하면 같은 PR에서 shared boundary로 이동한다.

## 현재 #492 정리 대상

### 1. `PermissionSettingDialog`

현재 문제:

- `HomeScreen.kt`가 `com.uiery.keep.feature.onboarding.permission.component.PermissionSettingDialog`를 직접 import한다.
- Onboarding permission UI가 Home accessibility permission 안내에도 재사용되어 feature 간 결합이 생겼다.

권장 code-lane 방향:

1. dialog의 app 공통 의미를 `app/src/main/java/com/uiery/keep/ui/component`로 승격한다.
2. Onboarding/Home copy 차이가 필요하면 title/message/action label/permission type을 파라미터로 둔다.
3. Home/Onboarding call site를 새 shared contract로 교체한다.
4. 기존 onboarding-private component 경로에는 duplicate/stub을 남기지 않는다.
5. static guard가 Home → Onboarding component import 재도입을 잡게 한다.

검증 후보:

- `python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`
- `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.home.*' --tests 'com.uiery.keep.feature.onboarding.*'`
- `./gradlew :app:assembleProdDebug`

### 2. `TimerPicker`

현재 문제:

- `app/src/main/java/com/uiery/keep/feature/home/component/TimerPicker.kt`
- `app/src/main/java/com/uiery/keep/ui/component/TimerPicker.kt`
- Routine은 shared `ui.component.TimerPicker`를 사용하고, Home은 home-private picker를 사용한다.

권장 code-lane 방향:

1. 두 picker의 표시 copy, haptic, min/max/step, accessibility semantics 차이를 먼저 inventory한다.
2. Home/Routine이 같은 behavior contract를 가져도 되면 app shared `TimerPicker` 하나로 수렴한다.
3. Home만의 product behavior가 남아야 한다면 차이를 이 문서/코드 주석/테스트에 명시하고 duplicate UI가 아닌 policy parameter로 분리한다.
4. home-private `TimerPicker` 제거 후 재도입 방지 guard를 강화한다.

검증 후보:

- `python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`
- Home/Routine ViewModel/UI 주변 focused JVM 또는 Compose 가능한 검증
- `./gradlew :app:assembleProdDebug`

## Static guard 계약

`python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`는 최소한 아래를 고정해야 한다.

- app shared UI가 feature-private package를 import하지 않는다.
- Home component package에 이미 shared로 이동한 `CategoryButton`, `CategoryBottomSheetContent`, `SearchTextField`, `AppItem`, `KeepSwitch` duplicate/stub이 없다.
- KDS로 승격된 `KeepSwitch`는 KDS README에 소유권이 문서화되어 있다.
- `CategoryButton`, `CategoryBottomSheetContent`, `TimerPicker` 같은 app resource-bound shared component는 `app/src/main/java/com/uiery/keep/ui/component/AGENTS.md`에 문서화되어 있다.
- #492 code-lane에서는 feature A → feature B component import 금지 guard를 추가하고, `PermissionSettingDialog` / `TimerPicker` 현 위반을 제거해야 한다.

## PR / Issue closure rule

- 이 문서/계약 PR은 구현 전 handoff이므로 `Refs #492`가 맞다.
- `Closes #492`는 아래가 모두 만족될 때만 사용한다.
  - Home이 Onboarding permission component를 직접 import하지 않는다.
  - Home/Routine timer picker가 하나의 shared contract로 수렴하거나 명시적인 분리 사유와 guard를 가진다.
  - feature-private cross-import static test가 실제 source tree를 검사한다.
  - 접근성 label/string parity, haptic, color/typography 회귀 검증을 통과한다.
  - 관련 variant-specific test/build가 통과한다.

## Handoff evidence template

```md
## #492 shared UI boundary evidence

- PR:
- Head SHA:
- 변경 범위:
  - [ ] PermissionSettingDialog ownership
  - [ ] TimerPicker ownership
  - [ ] static guard
  - [ ] AGENTS/KDS/docs sync
- 검증:
  - [ ] `python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`
  - [ ] `./gradlew :app:testDevDebugUnitTest ...`
  - [ ] `./gradlew :app:assembleProdDebug`
- 남은 경계:
```
