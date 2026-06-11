import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github/workflows/android-ci.yml"
OPS_CI_WORKFLOW = REPO_ROOT / ".github/workflows/ops-ci.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github/workflows/release-qa.yml"
VERIFY_LINT_REGISTRY_SCRIPT = "scripts/verify_lint_registry.py"
REQUIRED_STEP_NAME = "Verify Navigation/Compose lint registry coverage"
REQUIRED_TEST_STEP_NAME = "Run static policy unit tests"
REQUIRED_TEST_COMMAND_PREFIX = "python3 -m unittest"
REQUIRED_STATIC_POLICY_MODULES = (
    "scripts.tests.test_compose_compiler_gradle_contract",
    "scripts.tests.test_kds_dependency_catalog_contract",
    "scripts.tests.test_sensitive_logging_policy",
    "scripts.tests.test_android_manifest_contract",
    "scripts.tests.test_compose_icon_button_accessibility",
    "scripts.tests.test_locale_string_parity",
    "scripts.tests.test_locale_string_quality_contract",
    "scripts.tests.test_verify_lint_registry",
    "scripts.tests.test_lint_registry_workflows",
)
REQUIRED_FLAGS = (
    '--require-section "Included Additional Checks"',
    "--require-identifier androidx.navigation.common",
    "--require-identifier androidx.navigation.compose",
    "--require-identifier androidx.navigation.runtime",
    "--require-issue-id MissingSerializableAnnotation",
    "--require-issue-id MissingKeepAnnotation",
    "--require-issue-id WrongNavigateRouteType",
    '--forbid-text "Requires newer lint; these checks will be skipped!"',
    "--forbid-text ObsoleteLintCustomCheck",
)


class LintRegistryWorkflowTest(unittest.TestCase):
    def assert_workflow_verifier_contract(self, workflow: str, report_path: str) -> None:
        self.assertIn(REQUIRED_TEST_STEP_NAME, workflow)
        self.assertIn(REQUIRED_TEST_COMMAND_PREFIX, workflow)
        for required_module in REQUIRED_STATIC_POLICY_MODULES:
            with self.subTest(required_module=required_module, report_path=report_path):
                self.assertIn(required_module, workflow)
        self.assertIn(REQUIRED_STEP_NAME, workflow)
        self.assertIn(f"--report {report_path}", workflow)

        for required_flag in REQUIRED_FLAGS:
            with self.subTest(required_flag=required_flag, report_path=report_path):
                self.assertIn(required_flag, workflow)

    def test_android_ci_verifies_dev_lint_registry_report(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()

        self.assert_workflow_verifier_contract(
            workflow,
            "app/build/reports/lint-results-devDebug.html",
        )

    def test_verify_lint_registry_script_changes_materialize_android_ci_fast_verification(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()

        self.assertIn(VERIFY_LINT_REGISTRY_SCRIPT, workflow)
        self.assertIn("android_ci:", workflow)
        self.assertIn(
            f"- '{VERIFY_LINT_REGISTRY_SCRIPT}'",
            workflow,
            "Changing the lint-registry verifier script must materialize Android CI fast verification",
        )

    def test_verify_lint_registry_script_changes_materialize_ops_ci_release_helper_checks(self):
        workflow = OPS_CI_WORKFLOW.read_text()

        for trigger in ("pull_request", "push"):
            with self.subTest(trigger=trigger):
                self.assertIn(f"- '{VERIFY_LINT_REGISTRY_SCRIPT}'", workflow)

        self.assertIn("release_helpers:", workflow)
        self.assertIn(
            f"- '{VERIFY_LINT_REGISTRY_SCRIPT}'",
            workflow,
            "Changing the lint-registry verifier script must materialize Ops CI release-helper regression tests",
        )

    def test_release_qa_verifies_prod_release_lint_registry_report(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()

        self.assert_workflow_verifier_contract(
            workflow,
            "app/build/reports/lint-results-prodRelease.html",
        )


if __name__ == "__main__":
    unittest.main()
