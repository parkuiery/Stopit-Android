import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
RELEASE_BUILD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
ANDROID_SKILLS_TESTING_QA = REPO_ROOT / "docs" / "ANDROID_SKILLS_TESTING_QA.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"


class RuntimeDiagnosticArtifactsTest(unittest.TestCase):
    def test_android_ci_fast_verification_uploads_diagnostics_even_after_failure(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()
        scripts_regression_step = self._step_block(workflow, "Run scripts regression tests")
        static_policy_step = self._step_block(workflow, "Run static policy unit tests")
        run_step = self._step_block(workflow, "Run Gradle verification")
        upload_step = self._step_block(workflow, "Upload Android CI fast verification diagnostics")

        self.assertIn("python3 -m unittest", scripts_regression_step)
        self.assertIn("mkdir -p ci-diagnostics", scripts_regression_step)
        self.assertIn("set -o pipefail", scripts_regression_step)
        self.assertIn("tee ci-diagnostics/scripts-regression-tests.log", scripts_regression_step)
        self.assertIn("python3 -m unittest", static_policy_step)
        self.assertIn("mkdir -p ci-diagnostics", static_policy_step)
        self.assertIn("set -o pipefail", static_policy_step)
        self.assertIn("tee ci-diagnostics/static-policy-unit-tests.log", static_policy_step)
        self.assertIn(":app:testDevDebugUnitTest", run_step)
        self.assertIn("--continue", run_step)
        self.assertIn("if: always()", upload_step)
        self.assertNotIn("steps.firebase-config.outputs.available", upload_step)
        self.assertNotIn("steps.firebase-config.outputs.dummy_firebase_config", upload_step)
        self.assertIn("continue-on-error: true", upload_step)
        self.assertIn("uses: actions/upload-artifact@v7", upload_step)
        self.assertIn("name: stopit-android-ci-fast-verification-diagnostics", upload_step)
        self.assertIn("retention-days: 7", upload_step)
        self.assertIn("if-no-files-found: ignore", upload_step)
        self.assertIn("ci-diagnostics/**", upload_step)
        self.assertIn("app/build/reports/tests/**", upload_step)
        self.assertIn("app/build/test-results/**", upload_step)
        self.assertIn("app/build/reports/lint-results-devDebug.*", upload_step)
        self.assertIn("app/build/reports/problems/**", upload_step)
        self.assertIn("app/build/outputs/logs/**", upload_step)


    def test_android_ci_no_longer_runs_an_emulator(self):
        # Runtime smoke moved to scripts/runtime-gate.sh; failures are triaged from
        # the local app/build/reports/androidTests run, not a CI artifact.
        workflow = ANDROID_CI_WORKFLOW.read_text()
        self.assertNotIn("android-emulator-runner", workflow)
        self.assertNotIn("stopit-runtime-smoke-diagnostics", workflow)
        self.assertIn("check-evidence", workflow)

    def test_full_release_qa_uploads_jvm_lint_build_diagnostics_even_after_failure(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()
        static_policy_step = self._step_block(workflow, "Run static policy unit tests")
        upload_step = self._step_block(workflow, "Upload full release QA diagnostics")

        self.assertIn("mkdir -p release-qa-diagnostics", static_policy_step)
        self.assertIn("set -o pipefail", static_policy_step)
        self.assertIn("tee release-qa-diagnostics/static-policy-unit-tests.log", static_policy_step)
        self.assertIn("if: always()", upload_step)
        self.assertIn("continue-on-error: true", upload_step)
        self.assertIn("uses: actions/upload-artifact@v7", upload_step)
        self.assertIn("name: stopit-release-qa-build-diagnostics", upload_step)
        self.assertIn("retention-days: 7", upload_step)
        self.assertIn("if-no-files-found: ignore", upload_step)
        self.assertIn("release-qa-diagnostics/**", upload_step)
        self.assertIn("app/build/reports/**", upload_step)
        self.assertIn("app/build/test-results/**", upload_step)
        self.assertIn("app/build/outputs/logs/**", upload_step)
        self.assertIn("app/build/outputs/mapping/prodRelease/**", upload_step)

    def test_release_qa_no_longer_runs_an_emulator(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()
        self.assertNotIn("android-emulator-runner", workflow)
        self.assertNotIn("stopit-release-instrumentation-diagnostics", workflow)
        self.assertIn("--require-sequence release", workflow)

    def test_release_build_uploads_release_diagnostics_even_after_failure(self):
        workflow = RELEASE_BUILD_WORKFLOW.read_text()
        upload_step = self._step_block(workflow, "Upload release build diagnostics")

        self.assertIn("if: always()", upload_step)
        self.assertIn("continue-on-error: true", upload_step)
        self.assertIn("uses: actions/upload-artifact@v7", upload_step)
        self.assertIn("name: stopit-release-build-diagnostics", upload_step)
        self.assertIn("retention-days: 7", upload_step)
        self.assertIn("if-no-files-found: ignore", upload_step)
        self.assertIn("app/build/reports/**", upload_step)
        self.assertIn("app/build/test-results/**", upload_step)
        self.assertIn("app/build/outputs/logs/**", upload_step)
        self.assertIn("app/build/outputs/mapping/prodRelease/**", upload_step)

    def test_play_deploy_uploads_non_production_release_diagnostics_even_after_failure(self):
        workflow = PLAY_DEPLOY_WORKFLOW.read_text()
        upload_step = self._step_block(workflow, "Upload Play deploy release diagnostics")

        self.assertIn("if: always() && env.DEPLOY_TRACK != 'production'", upload_step)
        self.assertIn("continue-on-error: true", upload_step)
        self.assertIn("uses: actions/upload-artifact@v7", upload_step)
        self.assertIn("name: stopit-play-deploy-release-diagnostics", upload_step)
        self.assertIn("retention-days: 7", upload_step)
        self.assertIn("if-no-files-found: ignore", upload_step)
        self.assertIn("app/build/reports/**", upload_step)
        self.assertIn("app/build/test-results/**", upload_step)
        self.assertIn("app/build/outputs/logs/**", upload_step)
        self.assertIn("app/build/outputs/mapping/prodRelease/**", upload_step)

    def test_operator_docs_explain_runtime_diagnostic_artifact_triage_order(self):
        required = [
            "stopit-android-ci-fast-verification-diagnostics",
            "ci-diagnostics/scripts-regression-tests.log",
            "ci-diagnostics/static-policy-unit-tests.log",
            "app/build/reports/tests",
            "app/build/test-results",
            "app/build/reports/lint-results-devDebug",
            "app/build/reports/problems",
            "app/build/outputs/logs",
            # The emulator jobs that produced these two artifacts are gone; runtime
            # triage now happens on the developer machine that ran the gate.
            "scripts/runtime-gate.sh",
            "stopit-release-qa-build-diagnostics",
            "release-qa-diagnostics/static-policy-unit-tests.log",
            "stopit-release-build-diagnostics",
            "stopit-play-deploy-release-diagnostics",
            "app/build/reports/androidTests",
            "app/build/outputs/androidTest-results",
            "app/build/reports",
            "app/build/test-results",
            "app/build/outputs/logs",
            "app/build/outputs/mapping/prodRelease",
            "logcat",
            "dumpsys alarm",
            "dumpsys accessibility",
            "retention-days: 7",
            "non-blocking",
        ]
        for path in [QA_RUNTIME_CHECKLIST, ANDROID_SKILLS_TESTING_QA, RELEASE_CHECKLIST]:
            text = path.read_text()
            for phrase in required:
                with self.subTest(doc=path.name, phrase=phrase):
                    self.assertIn(phrase, text)

    def _step_block(self, workflow: str, step_name: str) -> str:
        pattern = rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare step {step_name}")
        if match is None:
            self.fail(f"workflow should declare step {step_name}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
