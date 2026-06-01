import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_BUILD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOYMENT_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class ReleaseBuildWorkflowScopeTest(unittest.TestCase):
    def test_release_build_job_skips_non_release_main_pull_requests_before_secret_steps(self):
        workflow = RELEASE_BUILD_WORKFLOW.read_text()

        job_header = re.search(
            r"(?ms)^  release-build:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|\Z)",
            workflow,
        )
        self.assertIsNotNone(job_header, "release-build job should exist")
        if job_header is None:
            self.fail("release-build job should exist")
        job_body = job_header.group("body")

        validate_secret_index = job_body.find("Validate release build secrets")
        guard_index = job_body.find("github.event_name != 'pull_request'")

        self.assertNotEqual(validate_secret_index, -1, "release-build should still validate signing secrets when it runs")
        self.assertNotEqual(guard_index, -1, "release-build needs a job-level pull_request scope guard")
        self.assertLess(
            guard_index,
            validate_secret_index,
            "non-release main PRs must be skipped before signing/Firebase secrets are read",
        )
        self.assertIn("startsWith(github.head_ref, 'release/')", job_body)
        self.assertIn("startsWith(github.head_ref, 'hotfix/')", job_body)
        self.assertIn("github.event_name == 'workflow_dispatch'", job_body)

    def test_operator_docs_describe_release_build_scope_consistently(self):
        play_deployment = PLAY_DEPLOYMENT_DOC.read_text()
        release_context = RELEASE_CONTEXT_DOC.read_text()

        expected_scope = "release/* -> main, hotfix/* -> main, manual, or post-merge main push"
        for doc in (play_deployment, release_context):
            self.assertIn(expected_scope, doc)
            self.assertIn("non-release main PR", doc)
            self.assertIn("signed release artifact", doc)


if __name__ == "__main__":
    unittest.main()
