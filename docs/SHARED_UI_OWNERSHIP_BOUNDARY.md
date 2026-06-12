# 공유 UI 소유권 / feature-private import 경계

Issue: #492 (closed)

이 문서는 Stopit Android에서 feature-private Compose component를 다른 feature가 직접 가져다 쓰는 구조를 방지하기 위한 docs/ops source of truth다. 목표는 모든 UI를 무조건 KDS로 옮기는 것이 아니라, **재사용 범위에 맞는 소유권 계층**을 고정해 화면 간 결합과 drift를 줄이는 것이다.

#492의 repo-internal 정리는 완료됐다. `PermissionSettingDialog`와 `TimerPicker`는 현재 미해결 handoff가 아니라 app shared UI baseline이며, 후속 shared UI drift가 발견되면 #492를 재사용하지 말고 새 실행 단위 issue로 다룬다.

## 왜 중요한가

- feature-private UI를 다른 feature가 import하면 원래 feature의 UX 변경이 소비 feature를 의도치 않게 깨뜨릴 수 있다.
- 같은 의미의 picker/dialog/button이 두 벌로 남으면 accessibility label, haptic, color/typography, QA evidence가 화면별로 어긋난다.
- shared UI 이동은 코드 이동만으로 끝나지 않는다. call site, string/resource parity, test/architecture guard, KDS/app-shared 문서까지 함께 맞아야 한다.

## 소유권 계층

| 계층 | 경로 | 사용 기준 | 예시 |
| --- | --- | --- | --- |
| KDS primitive | `core/kds/src/main/java/com/uiery/kds` | app 리소스/도메인 모델 없이 재사용 가능한 디자인 시스템 primitive | `KeepButton`, `KeepCheckbox`, `KeepSwitch` |
| app shared UI | `app/src/main/java/com/uiery/keep/ui/component` | 여러 app feature에서 공유하지만 app string/resource/domain boundary에 의존하는 UI | `CategoryButton`, `CategoryBottomSheetContent`, `PermissionSettingDialog`, `TimerPicker` |
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

예외는 code-lane PR에서 명시적인 사유와 제거 계획을 남긴 경우에만 허용한다. 새 예외는 `scripts/tests/test_shared_ui_component_boundaries.py`에 allowlist로 묵히지 말고, 가능하면 같은 PR에서 shared boundary로 이동한다.

## #492 완료 baseline

### `PermissionSettingDialog`

완료 상태:

- `app/src/main/java/com/uiery/keep/ui/component/PermissionSettingDialog.kt`가 app shared UI 소유권을 가진다.
- Home과 Onboarding은 `com.uiery.keep.ui.component.PermissionSettingDialog`를 import한다.
- `app/src/main/java/com/uiery/keep/feature/onboarding/permission/component/PermissionSettingDialog.kt` duplicate/stub은 없어야 한다.
- `app/src/main/java/com/uiery/keep/feature/onboarding/permission/component/AGENTS.md`는 해당 package가 #492 이후 비어 있고 shared dialog가 app shared UI에 있음을 안내한다.

회귀 방지:

- `test_permission_setting_dialog_lives_in_app_shared_ui`
- `test_features_do_not_import_other_feature_private_components`
- `test_app_shared_ui_does_not_import_feature_private_packages`

### `TimerPicker`

완료 상태:

- `app/src/main/java/com/uiery/keep/ui/component/TimerPicker.kt`가 app shared UI 소유권을 가진다.
- Home과 Routine은 shared `ui.component.TimerPicker`를 사용한다.
- `app/src/main/java/com/uiery/keep/feature/home/component/TimerPicker.kt` duplicate/stub은 없어야 한다.
- `TimerPickerContractTest`, `TimerPickerIntegrationTest`, runtime suite manifest가 shared picker contract를 고정한다.

회귀 방지:

- `test_timer_picker_has_no_home_private_duplicate`
- `test_app_shared_ui_package_documents_resource_bound_components`
- `scripts.tests.test_timer_picker_contract`
- `scripts.tests.test_android_runtime_suites_manifest`

### 기존 shared component baseline

아래 component도 이미 shared/KDS boundary 기준으로 관리한다.

- `CategoryButton`, `CategoryBottomSheetContent`, `AppItem`, `SearchTextField`: app shared UI.
- `KeepSwitch`: KDS primitive.
- `InstalledAppRepository`, `SelectableAppPolicy`: app-level app selection boundary.

## Static guard 계약

`python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`는 최소한 아래를 고정해야 한다.

- app shared UI가 feature-private package를 import하지 않는다.
- feature-private component package를 다른 feature가 직접 import하지 않는다.
- `PermissionSettingDialog`는 app shared UI에 있고 onboarding-private duplicate/stub이 없다.
- `TimerPicker`는 app shared UI에 있고 home-private duplicate/stub이 없다.
- Home component package에 이미 shared로 이동한 `CategoryButton`, `CategoryBottomSheetContent`, `SearchTextField`, `AppItem`, `TimerPicker`, `KeepSwitch` duplicate/stub이 없다.
- KDS로 승격된 `KeepSwitch`는 KDS README에 소유권이 문서화되어 있다.
- `CategoryButton`, `CategoryBottomSheetContent`, `PermissionSettingDialog`, `TimerPicker` 같은 app resource-bound shared component는 `app/src/main/java/com/uiery/keep/ui/component/AGENTS.md`에 문서화되어 있다.
- 이 runbook은 #492를 미해결 handoff처럼 설명하지 않고, closed baseline과 future drift 기준을 설명한다.

## Future drift 처리 기준

새 shared UI drift가 의심되면 아래 순서로 판단한다.

1. 현재 import가 feature-private cross-import인지 확인한다.
2. 재사용 UI가 app string/resource/domain boundary에 의존하면 `com.uiery.keep.ui.component` 후보로 둔다.
3. app 리소스/도메인에 의존하지 않는 순수 primitive면 `core:kds` 후보로 둔다.
4. 해당 feature 안에서만 의미가 있으면 feature-private으로 유지하되, 다른 feature가 import하지 않도록 guard를 유지한다.
5. 이미 닫힌 #492를 재개하지 말고 새 이슈/PR에서 변경 파일, static guard, focused test/build evidence를 남긴다.

## Evidence template

```md
## shared UI boundary evidence

- Issue / PR:
- Head SHA:
- 변경 범위:
  - [ ] component ownership
  - [ ] call-site migration
  - [ ] duplicate/stub removal
  - [ ] static guard
  - [ ] AGENTS/KDS/docs sync
- 검증:
  - [ ] `python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`
  - [ ] focused JVM/Compose test if behavior changed
  - [ ] `./gradlew :app:assembleProdDebug` or documented docs-only alternative
- 남은 경계:
```
