import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_BUILD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
PLAY_DEPLOYMENT_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CHECKLIST_DOC = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


def _workflow_step(workflow_text: str, step_name: str) -> str:
    marker = f"- name: {step_name}"
    if marker not in workflow_text:
        raise AssertionError(f"workflow is missing step: {step_name}")
    step = workflow_text.split(marker, 1)[1]
    return step.split("- name:", 1)[0]


def _index(workflow_text: str, step_name: str) -> int:
    marker = f"- name: {step_name}"
    index = workflow_text.find(marker)
    if index == -1:
        raise AssertionError(f"workflow is missing step: {step_name}")
    return index


def _assert_signed_upload_lint_gate(testcase: unittest.TestCase, workflow_text: str, *, production_skip: bool) -> None:
    lint_step = _workflow_step(workflow_text, "Run prod release lint")
    registry_step = _workflow_step(workflow_text, "Verify Navigation/Compose lint registry coverage")

    testcase.assertIn(":app:lintProdRelease", lint_step)
    testcase.assertIn("scripts/verify_lint_registry.py", registry_step)
    testcase.assertIn("app/build/reports/lint-results-prodRelease.html", registry_step)
    testcase.assertIn("--require-section \"Included Additional Checks\"", registry_step)
    testcase.assertIn("--require-identifier androidx.navigation.common", registry_step)
    testcase.assertIn("--require-identifier androidx.navigation.compose", registry_step)
    testcase.assertIn("--require-identifier androidx.navigation.runtime", registry_step)
    testcase.assertIn("--require-issue-id MissingSerializableAnnotation", registry_step)
    testcase.assertIn("--require-issue-id MissingKeepAnnotation", registry_step)
    testcase.assertIn("--require-issue-id WrongNavigateRouteType", registry_step)
    testcase.assertIn("--forbid-text \"Requires newer lint; these checks will be skipped!\"", registry_step)
    testcase.assertIn("--forbid-text ObsoleteLintCustomCheck", registry_step)

    if production_skip:
        testcase.assertIn("if: env.DEPLOY_TRACK != 'production'", lint_step)
        testcase.assertIn("if: env.DEPLOY_TRACK != 'production'", registry_step)

    testcase.assertLess(_index(workflow_text, "Run prod release lint"), _index(workflow_text, "Build signed prod release bundle"))
    testcase.assertLess(
        _index(workflow_text, "Verify Navigation/Compose lint registry coverage"),
        _index(workflow_text, "Build signed prod release bundle"),
    )


class SignedAabLintGateTest(unittest.TestCase):
    def test_release_build_runs_prod_lint_and_registry_before_signed_bundle(self):
        workflow = RELEASE_BUILD_WORKFLOW.read_text()

        _assert_signed_upload_lint_gate(self, workflow, production_skip=False)
        self.assertLess(_index(workflow, "Run release unit tests"), _index(workflow, "Run prod release lint"))

    def test_play_deploy_non_production_runs_prod_lint_and_registry_before_upload(self):
        workflow = PLAY_DEPLOY_WORKFLOW.read_text()

        _assert_signed_upload_lint_gate(self, workflow, production_skip=True)
        self.assertLess(_index(workflow, "Verify Navigation/Compose lint registry coverage"), _index(workflow, "Upload to Google Play"))

    def test_play_deploy_production_promotion_still_skips_android_build_lint_gate(self):
        workflow = PLAY_DEPLOY_WORKFLOW.read_text()

        for step_name in (
            "Run prod release lint",
            "Verify Navigation/Compose lint registry coverage",
            "Build signed prod release bundle",
            "Upload signed AAB artifact",
            "Upload to Google Play",
        ):
            step = _workflow_step(workflow, step_name)
            self.assertIn("if: env.DEPLOY_TRACK != 'production'", step, step_name)

        promote_step = _workflow_step(workflow, "Promote internal release to production")
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", promote_step)
        self.assertNotIn(":app:lintProdRelease", promote_step)

    def test_operator_docs_describe_signed_aab_lint_gate(self):
        docs = "\n--- GIT_WORKFLOW.md ---\n" + GIT_WORKFLOW_DOC.read_text()
        docs += "\n--- PLAY_DEPLOYMENT.md ---\n" + PLAY_DEPLOYMENT_DOC.read_text()
        docs += "\n--- RELEASE_CHECKLIST.md ---\n" + RELEASE_CHECKLIST_DOC.read_text()
        docs += "\n--- release-context.md ---\n" + RELEASE_CONTEXT_DOC.read_text()

        self.assertIn("signed AAB", docs)
        self.assertIn(":app:lintProdRelease", docs)
        self.assertIn("scripts/verify_lint_registry.py", docs)
        self.assertIn("build/upload", docs)
        self.assertIn("production promotion", docs)
        self.assertIn("does not run `:app:lintProdRelease`", docs)


if __name__ == "__main__":
    unittest.main()
