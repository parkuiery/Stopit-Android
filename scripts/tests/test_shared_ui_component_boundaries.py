import pathlib
import re
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_MAIN = REPO_ROOT / "app/src/main/java/com/uiery/keep"
KDS_MAIN = REPO_ROOT / "core/kds/src/main/java/com/uiery/kds"
KDS_README = REPO_ROOT / "core/kds/README.md"
APP_SHARED_UI_AGENTS = APP_MAIN / "ui/component/AGENTS.md"
SHARED_UI_RUNBOOK = REPO_ROOT / "docs/SHARED_UI_OWNERSHIP_BOUNDARY.md"
METRICS_ANALYSIS = REPO_ROOT / "docs/METRICS_ANALYSIS.md"
ENGINEERING_CONTEXT = REPO_ROOT / "docs/ops/stopit/engineering-context.md"
DOCS_AGENTS = REPO_ROOT / "docs/AGENTS.md"


class SharedUiComponentBoundariesTest(unittest.TestCase):
    def kotlin_sources(self, root: pathlib.Path):
        return sorted(root.rglob("*.kt"))

    def test_non_home_features_do_not_import_home_private_components(self):
        offenders: list[str] = []
        for source in self.kotlin_sources(APP_MAIN / "feature"):
            relative = source.relative_to(REPO_ROOT)
            if str(relative).startswith("app/src/main/java/com/uiery/keep/feature/home/"):
                continue
            text = source.read_text()
            if "com.uiery.keep.feature.home.component" in text:
                offenders.append(str(relative))

        self.assertEqual(
            [],
            offenders,
            "home component package must stay feature-private; promote cross-feature UI to KDS or app shared UI",
        )

    def test_cross_feature_switch_lives_in_kds(self):
        kds_sources = "\n".join(path.read_text() for path in self.kotlin_sources(KDS_MAIN))
        self.assertIn("fun KeepSwitch(", kds_sources)
        self.assertIn("object KeepSwitchDefaults", kds_sources)

        home_switch_files = [
            path.relative_to(REPO_ROOT)
            for path in self.kotlin_sources(APP_MAIN / "feature/home/component")
            if "fun KeepSwitch(" in path.read_text() or "object KeepSwitchDefaults" in path.read_text()
        ]
        self.assertEqual([], home_switch_files)

    def test_app_shared_category_button_contract_is_not_home_private(self):
        shared_sources = "\n".join(
            path.read_text()
            for path in self.kotlin_sources(APP_MAIN / "ui/component")
        )
        self.assertIn("fun CategoryButton(", shared_sources)

        home_category_button_files = [
            path.relative_to(REPO_ROOT)
            for path in self.kotlin_sources(APP_MAIN / "feature/home/component")
            if re.search(r"fun\s+CategoryButton\s*\(", path.read_text())
        ]
        self.assertEqual([], home_category_button_files)

    def test_app_shared_ui_does_not_import_feature_private_packages(self):
        offenders: list[str] = []
        for path in self.kotlin_sources(APP_MAIN / "ui/component"):
            relative = path.relative_to(REPO_ROOT)
            text = path.read_text()
            if "com.uiery.keep.feature." in text:
                offenders.append(str(relative))

        self.assertEqual(
            [],
            offenders,
            "app shared UI must depend on app-level/domain boundaries, not feature-private packages",
        )

    def test_features_do_not_import_other_feature_private_components(self):
        offenders: list[str] = []
        feature_root = APP_MAIN / "feature"
        import_pattern = re.compile(r"^import\s+com\.uiery\.keep\.feature\.([^.]+)\..*\.component\.", re.MULTILINE)

        for source in self.kotlin_sources(feature_root):
            relative = source.relative_to(REPO_ROOT)
            parts = source.relative_to(feature_root).parts
            if not parts:
                continue
            owning_feature = parts[0]
            imported_features = sorted(
                match.group(1)
                for match in import_pattern.finditer(source.read_text())
                if match.group(1) != owning_feature
            )
            if imported_features:
                offenders.append(f"{relative}: {', '.join(imported_features)}")

        self.assertEqual(
            [],
            offenders,
            "feature-private component packages must not be imported across feature boundaries; promote reusable UI to app shared UI or KDS",
        )

    def test_app_root_blocking_surface_private_component_import_inventory(self):
        import_pattern = re.compile(
            r"^import\s+(com\.uiery\.keep\.feature\.[^.]+\.component\.[A-Za-z0-9_]+)",
            re.MULTILINE,
        )
        actual: dict[str, list[str]] = {}
        for source in sorted(APP_MAIN.glob("*.kt")):
            imports = sorted(import_pattern.findall(source.read_text()))
            if imports:
                actual[str(source.relative_to(REPO_ROOT))] = imports

        expected = {
            "app/src/main/java/com/uiery/keep/BlockScreen.kt": [
                "com.uiery.keep.feature.lock.component.CountDownContent",
                "com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContent",
            ],
        }
        self.assertEqual(
            expected,
            actual,
            "app root blocking surfaces must not add feature-private component imports; #876 cleanup should reduce this inventory to empty",
        )

    def test_permission_setting_dialog_lives_in_app_shared_ui(self):
        shared_source = APP_MAIN / "ui/component/PermissionSettingDialog.kt"
        private_source = APP_MAIN / "feature/onboarding/permission/component/PermissionSettingDialog.kt"

        self.assertTrue(shared_source.exists(), "PermissionSettingDialog should be app shared UI")
        self.assertFalse(private_source.exists(), "onboarding-private PermissionSettingDialog duplicate must be removed")
        self.assertIn("fun PermissionSettingDialog(", shared_source.read_text())

    def test_timer_picker_has_no_home_private_duplicate(self):
        shared_source = APP_MAIN / "ui/component/TimerPicker.kt"
        private_source = APP_MAIN / "feature/home/component/TimerPicker.kt"

        self.assertTrue(shared_source.exists(), "TimerPicker should be owned by app shared UI")
        self.assertFalse(private_source.exists(), "home-private TimerPicker duplicate must be removed")
        self.assertIn("fun TimerPicker(", shared_source.read_text())

    def test_app_selection_repository_is_app_level_not_home_private(self):
        home_app_selection_sources = [
            path.relative_to(REPO_ROOT)
            for path in self.kotlin_sources(APP_MAIN / "feature/home/appselection")
        ]
        self.assertEqual([], home_app_selection_sources)

        app_selection_sources = "\n".join(
            path.read_text()
            for path in self.kotlin_sources(APP_MAIN / "appselection")
        )
        self.assertIn("class InstalledAppRepository", app_selection_sources)
        self.assertIn("object SelectableAppPolicy", app_selection_sources)

    def test_home_component_package_has_no_stale_shared_ui_copies_or_move_stubs(self):
        stale_files = [
            path.relative_to(REPO_ROOT)
            for path in self.kotlin_sources(APP_MAIN / "feature/home/component")
            if "moved to app shared UI" in path.read_text()
            or "moved to KDS" in path.read_text()
            or re.search(r"fun\s+(AppItem|CategoryBottomSheetContent|SearchTextField)\s*\(", path.read_text())
        ]
        self.assertEqual([], stale_files)

    def test_kds_readme_documents_keep_switch_shared_ownership(self):
        readme = KDS_README.read_text()
        self.assertIn("### KeepSwitch", readme)
        self.assertIn("cross-feature", readme)

    def test_app_shared_ui_package_documents_resource_bound_components(self):
        doc = APP_SHARED_UI_AGENTS.read_text()
        self.assertIn("CategoryButton", doc)
        self.assertIn("CategoryBottomSheetContent", doc)
        self.assertIn("TimerPicker", doc)
        self.assertIn("app resources", doc)
        self.assertIn("feature.home.component", doc)

    def test_emergency_unlock_selection_controls_stay_feature_private_for_now(self):
        source = APP_MAIN / "feature/emergencyunlocksettings/EmergencyUnlockSettingsScreen.kt"
        text = source.read_text()
        self.assertNotIn("FilterChip", text)
        self.assertNotIn("RadioButton", text)
        self.assertIn("private fun DurationChip", text)

    def test_shared_ui_runbook_documents_issue_492_closed_baseline(self):
        runbook = SHARED_UI_RUNBOOK.read_text()
        for expected in (
            "Issue: #492 (closed)",
            "Open drift issue: #876",
            "#492의 repo-internal 정리는 완료됐다",
            "#876은 #492 재오픈이 아니라 별도 drift다",
            "app root blocking surface",
            "BlockScreen.kt",
            "PermissionSettingDialog",
            "TimerPicker",
            "feature A → feature B",
            "Future drift 처리 기준",
            "python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v",
        ):
            self.assertIn(expected, runbook)

        for stale in (
            "현재 #492 정리 대상",
            "Refs #492",
            "Closes #492",
            "구현 전 handoff",
            "code-lane 정리 전까지는 구현 완료로 해석하지 않는다",
        ):
            self.assertNotIn(stale, runbook)

    def test_app_shared_ui_agents_links_issue_492_source_of_truth(self):
        doc = APP_SHARED_UI_AGENTS.read_text()
        self.assertIn("SHARED_UI_OWNERSHIP_BOUNDARY.md", doc)
        self.assertIn("#492", doc)
        self.assertIn("closed baseline", doc)

    def test_downstream_docs_describe_issue_492_as_closed_baseline(self):
        documents = {
            "docs/METRICS_ANALYSIS.md": METRICS_ANALYSIS.read_text(),
            "docs/ops/stopit/engineering-context.md": ENGINEERING_CONTEXT.read_text(),
            "docs/AGENTS.md": DOCS_AGENTS.read_text(),
        }

        for path, text in documents.items():
            self.assertIn("#492", text, path)
            self.assertNotIn("PermissionSettingDialog/TimerPicker code-lane handoff", text, path)
            self.assertNotIn("code-lane 정리 전까지는 구현 완료로 해석하지 않는다", text, path)
            self.assertNotIn("현 정리 대상은 code-lane", text, path)

        self.assertIn("#492는 closed 상태", documents["docs/METRICS_ANALYSIS.md"])
        self.assertIn("#492 closed 이후 app shared UI baseline", documents["docs/ops/stopit/engineering-context.md"])
        self.assertIn("#492 closed 이후 PermissionSettingDialog/TimerPicker app shared UI baseline", documents["docs/AGENTS.md"])
        self.assertIn("#876", documents["docs/ops/stopit/engineering-context.md"])
        self.assertIn("#876 app root `BlockScreen` → lock feature-private component open drift", documents["docs/AGENTS.md"])


if __name__ == "__main__":
    unittest.main()
