import pathlib
import re
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_MAIN = REPO_ROOT / "app/src/main/java/com/uiery/keep"
KDS_MAIN = REPO_ROOT / "core/kds/src/main/java/com/uiery/kds"
KDS_README = REPO_ROOT / "core/kds/README.md"
APP_SHARED_UI_AGENTS = APP_MAIN / "ui/component/AGENTS.md"
SHARED_UI_RUNBOOK = REPO_ROOT / "docs/SHARED_UI_OWNERSHIP_BOUNDARY.md"


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

    def test_shared_ui_runbook_documents_issue_492_handoff(self):
        runbook = SHARED_UI_RUNBOOK.read_text()
        for expected in (
            "Issue: #492",
            "PermissionSettingDialog",
            "TimerPicker",
            "feature A → feature B",
            "Refs #492",
            "Closes #492",
            "python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v",
        ):
            self.assertIn(expected, runbook)

    def test_app_shared_ui_agents_links_issue_492_source_of_truth(self):
        doc = APP_SHARED_UI_AGENTS.read_text()
        self.assertIn("SHARED_UI_OWNERSHIP_BOUNDARY.md", doc)
        self.assertIn("#492", doc)


if __name__ == "__main__":
    unittest.main()
