import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
KDS_BUILD_FILE = REPO_ROOT / "core/kds/build.gradle.kts"
VERSION_CATALOG_FILE = REPO_ROOT / "gradle/libs.versions.toml"

EXPECTED_KDS_ALIASES = (
    "libs.kotlinx.datetime",
    "libs.google.play.services.ads",
    "libs.androidx.lifecycle.runtime.compose",
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


if __name__ == "__main__":
    unittest.main()
