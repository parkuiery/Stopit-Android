import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_BUILD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
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

    def test_release_build_workflow_does_not_run_on_main_push(self):
        workflow = RELEASE_BUILD_WORKFLOW.read_text()

        on_block = re.search(
            r"(?ms)^on:\n(?P<body>.*?)(?=^permissions:|^concurrency:|^env:|^jobs:|\Z)",
            workflow,
        )
        self.assertIsNotNone(on_block, "release-build workflow should declare triggers")
        if on_block is None:
            self.fail("release-build workflow should declare triggers")

        self.assertNotIn(
            "push:",
            on_block.group("body"),
            "signed release artifacts must not be built from direct pushes to main; use release/hotfix PR gates or manual dispatch from allowed release refs",
        )

    def test_manual_dispatch_validates_allowed_release_refs_before_secret_steps(self):
        workflow = RELEASE_BUILD_WORKFLOW.read_text()
        job_header = re.search(
            r"(?ms)^  release-build:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|\Z)",
            workflow,
        )
        self.assertIsNotNone(job_header, "release-build job should exist")
        if job_header is None:
            self.fail("release-build job should exist")
        job_body = job_header.group("body")

        manual_ref_guard_index = job_body.find("Validate manual release build ref")
        validate_secret_index = job_body.find("Validate release build secrets")

        self.assertNotEqual(manual_ref_guard_index, -1, "manual dispatch must validate the selected ref before signing/Firebase secrets are read")
        self.assertNotEqual(validate_secret_index, -1, "release-build should still validate signing secrets when it runs")
        self.assertLess(
            manual_ref_guard_index,
            validate_secret_index,
            "manual dispatch ref guard must run before signing/Firebase secrets are read",
        )
        self.assertIn("github.event_name == 'workflow_dispatch'", job_body)
        self.assertIn("GITHUB_REF_TYPE", job_body)
        self.assertIn("GITHUB_REF_NAME", job_body)
        self.assertIn('"main"', job_body)
        self.assertIn("release/*", job_body)
        self.assertIn("hotfix/*", job_body)
        self.assertIn("v[0-9]+\\.[0-9]+\\.[0-9]+", job_body)
        self.assertIn("will not decode signing secrets or build a signed AAB", job_body)

    def test_operator_docs_describe_release_build_scope_consistently(self):
        git_workflow = GIT_WORKFLOW_DOC.read_text()
        play_deployment = PLAY_DEPLOYMENT_DOC.read_text()
        release_context = RELEASE_CONTEXT_DOC.read_text()

        expected_scope = "release/* -> main, hotfix/* -> main, or manual dispatch from main/release/*/hotfix/*/semver tag refs"
        for doc in (git_workflow, play_deployment, release_context):
            normalized_doc = doc.replace("`", "").lower()
            self.assertIn(expected_scope, normalized_doc)
            self.assertIn("direct push to main", normalized_doc)
            self.assertIn("signed release artifact", doc)
            self.assertIn("signing secret", normalized_doc)
            self.assertIn("semver tag", normalized_doc)
            self.assertNotIn("post-merge main push", doc)


if __name__ == "__main__":
    unittest.main()
