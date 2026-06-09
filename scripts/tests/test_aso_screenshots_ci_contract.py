import json
import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
OPS_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "ops-ci.yml"
ASO_PACKAGE_JSON = REPO_ROOT / "tools" / "aso-screenshots" / "package.json"
ASO_LOCKFILE = REPO_ROOT / "tools" / "aso-screenshots" / "bun.lock"
ASO_README = REPO_ROOT / "tools" / "aso-screenshots" / "README.md"
PLAY_STORE_ASO_DOC = REPO_ROOT / "docs" / "PLAY_STORE_ASO.md"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class AsoScreenshotsCiContractTest(unittest.TestCase):
    def test_tool_has_lockfile_backed_next_build_script(self):
        self.assertTrue(ASO_PACKAGE_JSON.exists(), "ASO screenshots package.json should exist")
        self.assertTrue(ASO_LOCKFILE.exists(), "ASO screenshots Bun lockfile should exist")

        package = json.loads(ASO_PACKAGE_JSON.read_text())
        self.assertEqual(package["scripts"]["build"], "next build")
        self.assertIn("next", package["dependencies"])

    def test_ops_ci_materializes_aso_screenshots_build_for_tool_changes(self):
        workflow = OPS_CI_WORKFLOW.read_text()

        for trigger in ("pull_request", "push"):
            with self.subTest(trigger=trigger):
                trigger_block = self._trigger_block(workflow, trigger)
                self.assertIn("'tools/aso-screenshots/**'", trigger_block)

        self.assertIn("aso_screenshots: ${{ steps.filter.outputs.aso_screenshots }}", workflow)

        filter_block = self._filter_block(workflow, "aso_screenshots")
        self.assertIn("'tools/aso-screenshots/**'", filter_block)

        job_block = self._job_block(workflow, "aso-screenshots")
        self.assertIn("name: ASO screenshots build", job_block)
        self.assertIn("needs.classify_ops_ci.outputs.aso_screenshots", job_block)
        self.assertIn("oven-sh/setup-bun", job_block)
        self.assertIn("working-directory: tools/aso-screenshots", job_block)
        self.assertIn("bun install --frozen-lockfile", job_block)
        self.assertIn("bun run build", job_block)
        self.assertNotRegex(
            job_block,
            r"(?ms)(gradlew|setup-java|GOOGLE_SERVICES_JSON|ANDROID_KEYSTORE|GOOGLE_PLAY_SERVICE_ACCOUNT_JSON)",
            "ASO screenshots build should stay separate from Android/release secrets",
        )

    def test_docs_name_local_and_ci_aso_screenshot_boundaries(self):
        docs = "\n".join(
            path.read_text()
            for path in (ASO_README, PLAY_STORE_ASO_DOC, GIT_WORKFLOW_DOC, RELEASE_CONTEXT_DOC)
        )

        for expected in [
            "tools/aso-screenshots",
            "bun install --frozen-lockfile",
            "bun run build",
            "ASO screenshots build",
            "Android 앱 빌드와 분리",
        ]:
            with self.subTest(expected=expected):
                self.assertIn(expected, docs)

    def _trigger_block(self, workflow: str, trigger: str) -> str:
        pattern = rf"(?ms)^  {trigger}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|^permissions:|^concurrency:|^jobs:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare on.{trigger}")
        if match is None:
            self.fail(f"workflow should declare on.{trigger}")
        return match.group("body")

    def _filter_block(self, workflow: str, filter_name: str) -> str:
        pattern = rf"(?ms)^            {re.escape(filter_name)}:\n(?P<body>.*?)(?=^            [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare paths-filter entry {filter_name}")
        if match is None:
            self.fail(f"workflow should declare paths-filter entry {filter_name}")
        return match.group("body")

    def _job_block(self, workflow: str, job_id: str) -> str:
        pattern = rf"(?ms)^  {re.escape(job_id)}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare jobs.{job_id}")
        if match is None:
            self.fail(f"workflow should declare jobs.{job_id}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
