import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_BUILD_FILE = REPO_ROOT / "app/build.gradle.kts"
KDS_BUILD_FILE = REPO_ROOT / "core/kds/build.gradle.kts"
VERSION_CATALOG_FILE = REPO_ROOT / "gradle/libs.versions.toml"

EXPECTED_KDS_ALIASES = (
    "libs.kotlinx.datetime",
)
DIRECT_VERSION_DEPENDENCY = re.compile(r"implementation\(\s*\"[^\"]+:[0-9][^\"]*\"\s*\)")


class KdsDependencyCatalogContractTest(unittest.TestCase):
    def test_kds_dependencies_use_catalog_aliases_instead_of_direct_versions(self):
        contents = KDS_BUILD_FILE.read_text()

        self.assertNotRegex(contents, DIRECT_VERSION_DEPENDENCY)
        for alias in EXPECTED_KDS_ALIASES:
            with self.subTest(alias=alias):
                self.assertIn(f"implementation({alias})", contents)

    def test_version_catalog_contains_kds_dependency_aliases(self):
        catalog = VERSION_CATALOG_FILE.read_text()

        self.assertIn("kotlinx-datetime =", catalog)
        self.assertIn("google-play-services-ads =", catalog)
        self.assertIn("androidx-lifecycle-runtime-compose =", catalog)

    def test_kds_no_longer_depends_on_deprecated_accompanist_system_ui_controller(self):
        app_build = APP_BUILD_FILE.read_text()
        kds_build = KDS_BUILD_FILE.read_text()
        catalog = VERSION_CATALOG_FILE.read_text()
        kds_sources = "\n".join(
            path.read_text()
            for path in (REPO_ROOT / "core/kds/src/main/java").rglob("*.kt")
        )

        for contents in (app_build, kds_build, catalog, kds_sources):
            with self.subTest():
                self.assertNotIn("libs.accompanist.systemuicontroller", contents)
                self.assertNotIn("accompanist-systemuicontroller", contents)
                self.assertNotIn("accompanistSystemuicontroller", contents)
                self.assertNotIn("rememberSystemUiController", contents)
                self.assertNotIn("SystemUiController", contents)
                self.assertNotIn("ModalBottomSheetDefaults.properties", contents)

    def test_runtime_qa_checklist_tracks_bottom_sheet_visual_evidence_boundary(self):
        checklist = (REPO_ROOT / "docs/QA_RUNTIME_CHECKLIST.md").read_text()

        required_phrases = (
            "KDS modal bottom sheet edge-to-edge visual QA",
            "KeepModalBottomSheet",
            "navigation bar",
            "status bar",
            "gesture navigation",
            "3-button navigation",
            "screenshot evidence",
            "issue #325",
        )
        for phrase in required_phrases:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, checklist)


if __name__ == "__main__":
    unittest.main()
