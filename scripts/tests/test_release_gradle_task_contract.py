import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_HELPERS = [
    REPO_ROOT / "scripts" / "check-release-readiness.sh",
    REPO_ROOT / "scripts" / "bump-version.sh",
]


class ReleaseGradleTaskContractTest(unittest.TestCase):
    def test_release_helper_dry_runs_use_app_qualified_tasks(self):
        expected = ":app:testProdReleaseUnitTest :app:bundleProdRelease --dry-run"
        for path in RELEASE_HELPERS:
            with self.subTest(path=path.name):
                text = path.read_text()
                self.assertIn(expected, text)
                self.assertIsNone(
                    re.search(r"\.\/gradlew\s+testProdReleaseUnitTest\s+bundleProdRelease\s+--dry-run", text),
                    f"{path} should not rely on root task-name inference for app release tasks",
                )


if __name__ == "__main__":
    unittest.main()
