import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
FLAVOR_CONTRACT_DOC = REPO_ROOT / "docs" / "FLAVOR_APPLICATION_ID_CONTRACT.md"
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
PLAY_DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
VERSION_GUARD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "version-guard.yml"


def _flavor_block(build_gradle_text: str, flavor_name: str) -> str:
    match = re.search(
        rf'create\("{flavor_name}"\)\s*\{{(?P<body>.*?)\n\s*\}}\n\s*(?:create\("|\}})',
        build_gradle_text,
        re.DOTALL,
    )
    if not match:
        raise AssertionError(f"Missing {flavor_name} product flavor block")
    return match.group("body")


class FlavorApplicationIdContractTest(unittest.TestCase):
    def test_dev_flavor_uses_separate_application_id_suffix(self):
        text = APP_BUILD_GRADLE.read_text()
        dev_block = _flavor_block(text, "dev")

        self.assertRegex(text, r'defaultConfig\s*\{[^}]*applicationId\s*=\s*"com\.uiery\.keep"')
        self.assertIn('applicationIdSuffix = ".dev"', dev_block)
        self.assertNotIn('//applicationIdSuffix = ".dev"', dev_block)

    def test_prod_release_package_remains_play_package(self):
        build_gradle = APP_BUILD_GRADLE.read_text()
        prod_block = _flavor_block(build_gradle, "prod")

        self.assertNotIn("applicationIdSuffix", prod_block)
        self.assertRegex(PLAY_DEPLOY_WORKFLOW.read_text(), r"PACKAGE_NAME:\s*com\.uiery\.keep\b")
        self.assertRegex(VERSION_GUARD_WORKFLOW.read_text(), r"PACKAGE_NAME:\s*com\.uiery\.keep\b")

    def test_dev_debug_workflow_appops_target_dev_package(self):
        for path in [ANDROID_CI_WORKFLOW, RELEASE_QA_WORKFLOW]:
            with self.subTest(path=path.name):
                text = path.read_text()
                self.assertIn("adb shell appops set com.uiery.keep.dev", text)
                self.assertNotIn("adb shell appops set com.uiery.keep POST_NOTIFICATION", text)
                self.assertNotIn("adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM", text)

    def test_dev_debug_workflows_restore_dedicated_dev_firebase_config(self):
        for path in [ANDROID_CI_WORKFLOW, RELEASE_QA_WORKFLOW]:
            with self.subTest(path=path.name):
                text = path.read_text()
                self.assertRegex(text, r"GOOGLE_SERVICES_JSON_DEV:\s*\$\{\{ secrets\.GOOGLE_SERVICES_JSON_DEV \}\}")
                self.assertIn('test -n "$GOOGLE_SERVICES_JSON_DEV"', text)
                self.assertIn('printf \'%s\' "$GOOGLE_SERVICES_JSON_DEV" > app/src/dev/google-services.json', text)
                self.assertIn('printf \'%s\' "$GOOGLE_SERVICES_JSON" > app/src/prod/google-services.json', text)
                self.assertNotIn('printf \'%s\' "$GOOGLE_SERVICES_JSON" > app/src/dev/google-services.json', text)

    def test_release_qa_runs_flavor_application_id_contract(self):
        release_qa = RELEASE_QA_WORKFLOW.read_text()

        self.assertIn(
            "scripts.tests.test_flavor_application_id_contract",
            release_qa,
        )

    def test_contract_doc_records_split_as_current_state(self):
        doc = FLAVOR_CONTRACT_DOC.read_text()

        self.assertIn('`dev` flavor는 `applicationIdSuffix = ".dev"`를 활성화한다.', doc)
        self.assertIn("`devDebug` 설치 identity는 `com.uiery.keep.dev`", doc)
        self.assertIn("`prod` / release / Play deploy 경로는 계속", doc)


if __name__ == "__main__":
    unittest.main()
