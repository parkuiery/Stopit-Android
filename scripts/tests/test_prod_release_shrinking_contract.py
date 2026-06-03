import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
PROGUARD_RULES = REPO_ROOT / "app" / "proguard-rules.pro"
RELEASE_DOCS = [
    REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md",
    REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md",
]


def _release_block(text: str) -> str:
    match = re.search(r"release\s*\{(?P<body>.*?)\n\s*}\n\s*\n\s*debug\s*\{", text, re.S)
    if not match:
        raise AssertionError("app/build.gradle.kts must define a release buildType before debug")
    return match.group("body")


class ProdReleaseShrinkingContractTest(unittest.TestCase):
    def test_prod_release_enables_r8_and_resource_shrinking(self):
        release = _release_block(APP_BUILD_GRADLE.read_text())

        self.assertRegex(release, r"isMinifyEnabled\s*=\s*true")
        self.assertRegex(release, r"isShrinkResources\s*=\s*true")
        self.assertIn("proguard-android-optimize.txt", release)
        self.assertIn("proguard-rules.pro", release)

    def test_proguard_rules_preserve_release_crash_mapping_context(self):
        rules = PROGUARD_RULES.read_text()

        self.assertIn("-keepattributes SourceFile,LineNumberTable", rules)
        self.assertIn("-renamesourcefileattribute SourceFile", rules)
        self.assertIn("@android.webkit.JavascriptInterface", rules)

    def test_release_operator_docs_record_shrinking_evidence_gate(self):
        for path in RELEASE_DOCS:
            with self.subTest(path=path.name):
                text = path.read_text()
                self.assertIn(":app:bundleProdRelease", text)
                self.assertRegex(text, r"R8|shrinking|resource shrinking")


if __name__ == "__main__":
    unittest.main()
