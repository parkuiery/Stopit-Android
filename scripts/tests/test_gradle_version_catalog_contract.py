import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
ROOT_BUILD = REPO_ROOT / "build.gradle.kts"
VERSION_CATALOG = REPO_ROOT / "gradle" / "libs.versions.toml"


class GradleVersionCatalogContractTest(unittest.TestCase):
    def test_room_and_crashlytics_plugins_use_version_catalog_aliases(self):
        root_build = ROOT_BUILD.read_text()
        catalog = VERSION_CATALOG.read_text()

        self.assertIn(
            'alias(libs.plugins.androidx.room) apply false',
            root_build,
        )
        self.assertIn(
            'alias(libs.plugins.firebase.crashlytics) apply false',
            root_build,
        )
        self.assertNotRegex(root_build, r'id\("androidx\.room"\)\s+version\s+"[^"]+"')
        self.assertNotRegex(root_build, r'id\("com\.google\.firebase\.crashlytics"\)\s+version\s+"[^"]+"')

        self.assertIsNotNone(
            re.search(r'^firebaseCrashlytics\s*=\s*"3\.0\.6"$', catalog, re.MULTILINE),
            msg="Crashlytics Gradle plugin version must live in the catalog.",
        )
        self.assertIsNotNone(
            re.search(
                r'^androidx-room\s*=\s*\{\s*id\s*=\s*"androidx\.room",\s*version\.ref\s*=\s*"room"\s*\}$',
                catalog,
                re.MULTILINE,
            ),
            msg="Room Gradle plugin must reuse the existing room version ref.",
        )
        self.assertIsNotNone(
            re.search(
                r'^firebase-crashlytics\s*=\s*\{\s*id\s*=\s*"com\.google\.firebase\.crashlytics",\s*version\.ref\s*=\s*"firebaseCrashlytics"\s*\}$',
                catalog,
                re.MULTILINE,
            ),
            msg="Crashlytics Gradle plugin alias must use the catalog version ref.",
        )


if __name__ == "__main__":
    unittest.main()
