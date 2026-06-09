import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
DOC_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
WORKFLOW_DOC_PATH = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CONTEXT_PATH = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"

EXPECTED_BUILD_CRITICAL_ROOT_INPUTS = {
    "gradlew",
    "gradlew.bat",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradle/**",
    ".github/workflows/android-ci.yml",
}


class AndroidCiPathGatingTest(unittest.TestCase):
    def test_android_ci_filter_includes_wrapper_launchers(self):
        workflow = WORKFLOW_PATH.read_text()

        filters_block = workflow.split("filters: |", 1)[1]
        android_ci_block = filters_block.split("runtime_smoke:", 1)[0]

        self.assertIn("- 'gradlew'", android_ci_block)
        self.assertIn("- 'gradlew.bat'", android_ci_block)

    def test_android_ci_filter_keeps_expected_build_critical_root_inputs(self):
        workflow = WORKFLOW_PATH.read_text()

        filters_block = workflow.split("filters: |", 1)[1]
        android_ci_block = filters_block.split("runtime_smoke:", 1)[0]

        for expected_input in EXPECTED_BUILD_CRITICAL_ROOT_INPUTS:
            self.assertIn(f"- '{expected_input}'", android_ci_block)

    def test_play_deployment_doc_mentions_wrapper_path_gating_contract(self):
        doc = DOC_PATH.read_text()

        self.assertIn("`gradlew` / `gradlew.bat`", doc)
        self.assertIn("build-critical", doc)

    def test_git_workflow_doc_mentions_build_critical_root_inputs(self):
        doc = WORKFLOW_DOC_PATH.read_text()

        self.assertIn("build-critical", doc)
        self.assertIn("`gradlew` / `gradlew.bat`", doc)
        self.assertIn("wrapper-only", doc)

    def test_release_context_mentions_build_critical_root_inputs(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("build-critical", doc)
        self.assertIn("`gradlew` / `gradlew.bat`", doc)
        self.assertIn("Fast verification", doc)

    def test_release_context_explains_fast_verification_gate_contract(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("workflow_dispatch", doc)
        self.assertIn("android_ci=true", doc)

    def test_android_ci_workflow_executes_scripts_unittests(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", workflow)

    def test_android_ci_fast_verification_runs_kds_module_local_checks(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("Run KDS module-local verification", workflow)
        self.assertIn(":core:kds:assembleDebug", workflow)
        self.assertIn(":core:kds:lintDebug", workflow)
        self.assertIn(":core:kds:testDebugUnitTest", workflow)

    def test_android_ci_keeps_dependabot_firebase_secret_boundary_neutral(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("Check Firebase config availability", workflow)
        self.assertIn("id: firebase-config", workflow)
        self.assertIn("id: runtime-firebase-config", workflow)
        self.assertIn("${{ github.actor }}\" = 'dependabot[bot]'", workflow)
        self.assertIn("Dependabot PR: Firebase secrets are unavailable, so app Gradle verification is deferred", workflow)
        self.assertIn("Dependabot PR: Firebase secrets are unavailable, so runtime smoke is deferred", workflow)
        self.assertIn("steps.firebase-config.outputs.available == 'true'", workflow)
        self.assertIn("steps.runtime-firebase-config.outputs.available == 'true'", workflow)
        self.assertIn("GOOGLE_SERVICES_JSON_DEV secret is missing", workflow)
        self.assertIn("GOOGLE_SERVICES_JSON secret is missing", workflow)

    def test_docs_explain_dependabot_firebase_secret_boundary(self):
        docs = [
            WORKFLOW_DOC_PATH.read_text(),
            (REPO_ROOT / "docs" / "PLAY_DEPLOY_SECRETS_RUNBOOK.md").read_text(),
            RELEASE_CONTEXT_PATH.read_text(),
        ]

        for doc in docs:
            with self.subTest():
                self.assertIn("Dependabot", doc)
                self.assertIn("Firebase secret", doc)
                self.assertIn("runtime smoke", doc)
                self.assertIn("workflow_dispatch", doc)

    def test_release_context_documents_kds_module_local_ci_gate(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("KDS module-local", doc)
        self.assertIn(":core:kds:assembleDebug", doc)
        self.assertIn(":core:kds:lintDebug", doc)
        self.assertIn(":core:kds:testDebugUnitTest", doc)


if __name__ == "__main__":
    unittest.main()
