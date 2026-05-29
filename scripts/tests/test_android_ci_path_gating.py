import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
DOC_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"


class AndroidCiPathGatingTest(unittest.TestCase):
    def test_android_ci_filter_includes_wrapper_launchers(self):
        workflow = WORKFLOW_PATH.read_text()

        filters_block = workflow.split("filters: |", 1)[1]
        android_ci_block = filters_block.split("runtime_smoke:", 1)[0]

        self.assertIn("- 'gradlew'", android_ci_block)
        self.assertIn("- 'gradlew.bat'", android_ci_block)

    def test_play_deployment_doc_mentions_wrapper_path_gating_contract(self):
        doc = DOC_PATH.read_text()

        self.assertIn("`gradlew` / `gradlew.bat`", doc)
        self.assertIn("build-critical", doc)


if __name__ == "__main__":
    unittest.main()
