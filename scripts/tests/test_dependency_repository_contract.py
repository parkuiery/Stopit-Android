import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SETTINGS_GRADLE = REPO_ROOT / "settings.gradle.kts"
VERSION_CATALOG = REPO_ROOT / "gradle" / "libs.versions.toml"
APP_BUILD = REPO_ROOT / "app" / "build.gradle.kts"
MAIN_SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"


class DependencyRepositoryContractTest(unittest.TestCase):
    def test_jitpack_and_utilcodex_are_not_in_the_app_dependency_graph(self):
        settings_gradle = SETTINGS_GRADLE.read_text()
        version_catalog = VERSION_CATALOG.read_text()
        app_build = APP_BUILD.read_text()
        main_sources = "\n".join(
            path.read_text()
            for path in MAIN_SOURCE_ROOT.rglob("*.kt")
        )

        self.assertNotIn("jitpack.io", settings_gradle)
        self.assertNotIn("utilcodex", version_catalog)
        self.assertNotIn("com.blankj", version_catalog)
        self.assertNotIn("libs.utilcodex", app_build)
        self.assertNotIn("com.blankj.utilcode", main_sources)
        self.assertNotIn("DeviceUtils.getUniqueDeviceId", main_sources)


if __name__ == "__main__":
    unittest.main()
