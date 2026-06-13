import pathlib
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
DESIGN = REPO_ROOT / "DESIGN.md"
KDS_README = REPO_ROOT / "core/kds/README.md"
HIERARCHY_DOC = REPO_ROOT / "docs/DESIGN_PRIMARY_COLOR_HIERARCHY.md"
DOCS_AGENTS = REPO_ROOT / "docs/AGENTS.md"


class DesignPrimaryColorHierarchyTest(unittest.TestCase):
    def test_root_design_defines_primary_hierarchy(self):
        text = DESIGN.read_text()

        self.assertIn("### Primary Color Hierarchy", text)
        self.assertIn("Primary is a scarce emphasis token", text)
        self.assertIn("single primary CTA", text)
        self.assertIn("TopAppBar back, menu, close", text)
        self.assertIn("do not rely on color alone", text)
        self.assertIn("docs/DESIGN_PRIMARY_COLOR_HIERARCHY.md", text)

    def test_kds_readme_matches_primary_usage_contract(self):
        text = KDS_README.read_text()

        self.assertIn("### Primary color 사용 위계", text)
        self.assertIn("주요 CTA, 선택/활성 상태", text)
        self.assertIn("기본 icon/text 색이 아닙니다", text)
        self.assertIn("TopAppBar 뒤로가기/메뉴/닫기 icon", text)
        self.assertIn("색상만으로 전달하지 말고", text)
        self.assertIn("docs/DESIGN_PRIMARY_COLOR_HIERARCHY.md", text)

    def test_audit_doc_records_current_post_implementation_surface(self):
        text = HIERARCHY_DOC.read_text()

        for expected in [
            "HomeScreen.kt",
            "LockHistoryScreen.kt",
            "BlockedAppsScreen.kt",
            "RoutineScreen.kt",
            "EmergencyUnlockSettingsScreen.kt",
            "GoalLockCreationScreen.kt",
            "ParentModeSetupScreen.kt",
            "SetupComponents.kt",
            "MenuScreen.kt",
        ]:
            self.assertIn(expected, text)

        self.assertIn("PR #546", text)
        self.assertIn("PR #804", text)
        self.assertIn("더 이상 “문서 계약만 있고 구현 전” 상태가 아니다", text)
        self.assertIn("KDS 적용", text)
        self.assertIn("navigation icon", text)
        self.assertIn("색상 단독 금지", text)
        self.assertIn("Refs #468", text)
        self.assertIn("visual QA", text)

    def test_visual_qa_closure_template_keeps_external_boundaries_explicit(self):
        text = HIERARCHY_DOC.read_text()

        for expected in [
            "## Visual QA / release closure evidence template",
            "Home / TopAppBar",
            "LockHistory / BlockedApps",
            "Routine / Routine bottom sheet",
            "Emergency Unlock settings / bottom sheet",
            "Goal Lock creation/detail",
            "Parent Mode setup/active controls",
            "light/dark mode",
            "TalkBack/contentDescription spot-check",
            "release/tag/Play deploy",
            "사용자 노출 후 확인",
            "Closes #468",
        ]:
            self.assertIn(expected, text)

        self.assertIn("visual QA와 release evidence가 없으면 PR/이슈 코멘트는 `Refs #468`로 유지한다", text)

    def test_docs_agents_links_primary_color_hierarchy(self):
        text = DOCS_AGENTS.read_text()

        self.assertIn("DESIGN_PRIMARY_COLOR_HIERARCHY.md", text)
        self.assertIn("#468", text)

    def test_top_app_bar_navigation_icons_do_not_use_primary(self):
        forbidden_snippets = {
            "app/src/main/java/com/uiery/keep/feature/home/HomeScreen.kt": [
                "contentDescription = stringResource(R.string.cd_open_menu),\n                            tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/lockhistory/LockHistoryScreen.kt": [
                "contentDescription = stringResource(R.string.cd_navigate_back),\n                            tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/lockhistory/blockedapps/BlockedAppsScreen.kt": [
                "contentDescription = stringResource(R.string.cd_navigate_back),\n                            tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/routine/RoutineScreen.kt": [
                "contentDescription = stringResource(R.string.cd_navigate_back),\n                            tint = KeepTheme.colors.primary,",
                "contentDescription = stringResource(R.string.cd_delete_routine),\n                            tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsScreen.kt": [
                "contentDescription = stringResource(R.string.cd_navigate_back),\n                            tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/goallock/GoalLockCreationScreen.kt": [
                "contentDescription = \"뒤로 가기\",\n                            tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/goallock/GoalLockDetailScreen.kt": [
                "contentDescription = \"뒤로 가기\",\n                            tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/parentmode/ParentModeSetupScreen.kt": [
                "contentDescription = stringResource(id = R.string.cd_navigate_back),\n                            tint = KeepTheme.colors.primary,",
                "contentDescription = stringResource(id = R.string.cd_navigate_back),\n                            tint = Color(0xFFFE9E0B),",
            ],
            "app/src/main/java/com/uiery/keep/feature/routine/component/RoutineBottomSheetContent.kt": [
                "contentDescription = stringResource(R.string.cd_navigate_back),\n                    tint = KeepTheme.colors.primary,",
            ],
            "app/src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt": [
                "contentDescription = stringResource(R.string.cd_navigate_back),\n                            tint = Color(0xFFFE9E0B),",
            ],
            "app/src/main/java/com/uiery/keep/feature/devtool/DevToolScreen.kt": [
                "contentDescription = stringResource(R.string.cd_navigate_back),\n                            tint = Color(0xFFFE9E0B),",
            ],
        }

        for relative_path, snippets in forbidden_snippets.items():
            text = (REPO_ROOT / relative_path).read_text()
            for snippet in snippets:
                self.assertNotIn(snippet, text, f"{relative_path} keeps primary on a navigation icon")


if __name__ == "__main__":
    unittest.main()
