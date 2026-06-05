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

    def test_audit_doc_records_current_top_app_bar_follow_up_surface(self):
        text = HIERARCHY_DOC.read_text()

        for expected in [
            "HomeScreen.kt",
            "LockHistoryScreen.kt",
            "BlockedAppsScreen.kt",
            "RoutineScreen.kt",
            "EmergencyUnlockSettingsScreen.kt",
        ]:
            self.assertIn(expected, text)

        self.assertIn("navigation icon", text)
        self.assertIn("색상 단독 금지", text)
        self.assertIn("Refs #468", text)

    def test_docs_agent_index_points_to_primary_hierarchy_doc(self):
        text = DOCS_AGENTS.read_text()

        self.assertIn("DESIGN_PRIMARY_COLOR_HIERARCHY.md", text)
        self.assertIn("#468", text)


if __name__ == "__main__":
    unittest.main()
