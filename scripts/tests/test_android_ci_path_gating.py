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

EXPECTED_STATIC_POLICY_HELPERS = {
    "scripts/check_compose_icon_button_accessibility.py": "scripts.tests.test_compose_icon_button_accessibility",
    "scripts/check_locale_string_parity.py": "scripts.tests.test_locale_string_parity",
}


class AndroidCiPathGatingTest(unittest.TestCase):
    def test_android_ci_uses_current_paths_filter_major(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("uses: dorny/paths-filter@v4", workflow)
        self.assertNotIn("dorny/paths-filter@v3", workflow)

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

    def test_static_policy_helper_changes_materialize_fast_verification(self):
        workflow = WORKFLOW_PATH.read_text()

        filters_block = workflow.split("filters: |", 1)[1]
        android_ci_block = filters_block.split("runtime_smoke:", 1)[0]
        static_policy_step = workflow.split("- name: Run static policy unit tests", 1)[1].split("\n\n", 1)[0]

        for helper_path, test_module in EXPECTED_STATIC_POLICY_HELPERS.items():
            with self.subTest(helper_path=helper_path):
                self.assertIn(f"- '{helper_path}'", android_ci_block)
                self.assertIn(test_module, static_policy_step)

    def test_play_deployment_doc_mentions_wrapper_path_gating_contract(self):
        doc = DOC_PATH.read_text()

        self.assertIn("`gradlew` / `gradlew.bat`", doc)
        self.assertIn("build-critical", doc)

    def test_git_workflow_doc_mentions_build_critical_root_inputs(self):
        doc = WORKFLOW_DOC_PATH.read_text()

        self.assertIn("build-critical", doc)
        self.assertIn("`gradlew` / `gradlew.bat`", doc)
        self.assertIn("wrapper-only", doc)
        self.assertIn("static-policy-helper-only", doc)
        for helper_path, test_module in EXPECTED_STATIC_POLICY_HELPERS.items():
            self.assertIn(helper_path, doc)
            self.assertIn(test_module, doc)

    def test_release_context_mentions_build_critical_root_inputs(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("build-critical", doc)
        self.assertIn("`gradlew` / `gradlew.bat`", doc)
        self.assertIn("Fast verification", doc)
        self.assertIn("static-policy-helper-only", doc)
        for helper_path, test_module in EXPECTED_STATIC_POLICY_HELPERS.items():
            self.assertIn(helper_path, doc)
            self.assertIn(test_module, doc)

    def test_release_context_explains_fast_verification_gate_contract(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("workflow_dispatch", doc)
        self.assertIn("android_ci=true", doc)

    def test_android_ci_workflow_executes_scripts_unittests(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", workflow)

    def test_android_ci_fast_verification_runs_kds_module_local_checks(self):
        workflow = WORKFLOW_PATH.read_text()

        # KDS module-local verification is no longer its own Gradle invocation: it
        # shares the single merged verification step so configuration and dependency
        # resolution are paid once. The task coverage contract is unchanged.
        gradle_step = workflow.split("- name: Run Gradle verification", 1)[1].split("- name:", 1)[0]

        self.assertIn(":core:kds:assembleDebug", gradle_step)
        self.assertIn(":core:kds:lintDebug", gradle_step)
        self.assertIn(":core:kds:testDebugUnitTest", gradle_step)

    def test_android_ci_fast_verification_uses_one_merged_gradle_invocation(self):
        workflow = WORKFLOW_PATH.read_text()
        verify_job = workflow.split("  verify:", 1)[1].split("\n  runtime-", 1)[0]

        gradle_invocations = [
            line for line in verify_job.splitlines() if "./gradlew" in line and "chmod" not in line
        ]
        self.assertEqual(1, len(gradle_invocations), gradle_invocations)

        gradle_step = verify_job.split("- name: Run Gradle verification", 1)[1].split("- name:", 1)[0]
        for task in (
            ":core:kds:testDebugUnitTest",
            ":core:kds:lintDebug",
            ":core:kds:assembleDebug",
            ":app:testDevDebugUnitTest",
            ":app:lintDevDebug",
            ":app:assembleProdDebug",
        ):
            with self.subTest(task=task):
                self.assertIn(task, gradle_step)
        # --continue keeps one failing task from hiding the rest of the report.
        self.assertIn("--continue", gradle_step)

    def test_android_ci_keeps_dependabot_firebase_secret_boundary_neutral(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("Check Firebase config availability", workflow)
        self.assertIn("id: firebase-config", workflow)
        # The runtime job no longer restores Firebase secrets -- it runs no emulator
        # and no Gradle -- so the boundary now lives in the evidence job's summary.
        self.assertNotIn("id: runtime-firebase-config", workflow)
        self.assertIn("${{ github.actor }}\" = 'dependabot[bot]'", workflow)
        self.assertIn("Dependabot PR: Firebase secrets are unavailable, so runtime smoke is deferred", workflow)
        self.assertIn("GOOGLE_SERVICES_JSON_DEV secret is missing", workflow)
        self.assertIn("GOOGLE_SERVICES_JSON secret is missing", workflow)

    def test_dependabot_app_gradle_verification_uses_dummy_firebase_config(self):
        workflow = WORKFLOW_PATH.read_text()
        dependabot_summary = "Dependabot PR: Firebase secrets are unavailable, so app Gradle verification uses dummy Firebase config."

        self.assertIn(dependabot_summary, workflow)
        self.assertNotIn(
            "Dependabot PR: Firebase secrets are unavailable, so app Gradle verification is deferred",
            workflow,
        )
        self.assertIn("dummy_firebase_config=true", workflow)
        self.assertIn("Write dummy Firebase google-services.json for Dependabot app verification", workflow)
        self.assertIn("steps.firebase-config.outputs.available == 'true' || steps.firebase-config.outputs.dummy_firebase_config == 'true'", workflow)
        self.assertIn("app/src/dev/google-services.json", workflow)
        self.assertIn("\"package_name\":\"com.uiery.keep.dev\"", workflow)
        self.assertIn("\"package_name\":\"com.uiery.keep\"", workflow)

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
