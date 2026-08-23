import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_BUILD_GRADLE = REPO_ROOT / "app/build.gradle.kts"
POLICY_DOC = REPO_ROOT / "docs/TEST_SOURCE_LINT_POLICY.md"
ANDROID_QA_DOC = REPO_ROOT / "docs/ANDROID_SKILLS_TESTING_QA.md"
RELEASE_CONTEXT = REPO_ROOT / "docs/ops/stopit/release-context.md"
DOCS_AGENTS = REPO_ROOT / "docs/AGENTS.md"
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github/workflows/android-ci.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github/workflows/release-qa.yml"


class TestSourceLintPolicyContractTest(unittest.TestCase):
    def test_current_check_test_sources_false_is_documented_policy_exception(self):
        gradle = APP_BUILD_GRADLE.read_text()
        policy = POLICY_DOC.read_text()

        self.assertRegex(
            gradle,
            r"stopit\.lint\.checkTestSources[^}]*getOrElse\(false\)",
            "app/build.gradle.kts should keep the normal lint gate excluding test/androidTest sources by default while exposing an opt-in baseline property",
        )
        self.assertIn("#1091", policy)
        self.assertIn("checkTestSources = false", policy)
        self.assertIn("-Pstopit.lint.checkTestSources=true", policy)
        self.assertIn("즉시 전체 test source lint를 켜지 않고", policy)
        self.assertIn("명시적 예외 + 대체 검증 + 재검토 게이트", policy)
        self.assertIn("## 현재 남은 경계", policy)

    def test_policy_names_required_substitute_guards_and_activation_requirements(self):
        policy = POLICY_DOC.read_text()

        required_phrases = [
            ":app:testDevDebugUnitTest",
            ":app:testProdReleaseUnitTest",
            ":app:connectedDevDebugAndroidTest",
            "scripts.tests.test_android_runtime_suites_manifest",
            "scripts.tests.test_test_source_lint_policy",
            "scripts/verify_lint_registry.py",
            "Warning triage",
            "checkTestSources = true",
            "-Pstopit.lint.checkTestSources=true",
            "allowlist",
        ]
        for phrase in required_phrases:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, policy)

        forbidden_patterns = [
            r"flavorless lintDebug",
            r"(?<!:)\btestDebugUnitTest\b",
            r"(?<!:)\blintDebug\b",
            r"(?<!:)\bassembleDebug\b",
        ]
        for pattern in forbidden_patterns:
            with self.subTest(forbidden_pattern=pattern):
                self.assertIsNone(re.search(pattern, policy))

    def test_high_traffic_docs_link_policy(self):
        expected_link = "docs/TEST_SOURCE_LINT_POLICY.md"
        policy_name = "TEST_SOURCE_LINT_POLICY.md"

        for path in (ANDROID_QA_DOC, RELEASE_CONTEXT, DOCS_AGENTS):
            text = path.read_text()
            with self.subTest(path=path):
                self.assertTrue(
                    expected_link in text or policy_name in text,
                    f"{path} should point operators to the test source lint policy",
                )

    def test_static_policy_workflows_include_this_contract(self):
        module_name = "scripts.tests.test_test_source_lint_policy"
        for path in (ANDROID_CI_WORKFLOW, RELEASE_QA_WORKFLOW):
            workflow = path.read_text()
            with self.subTest(path=path):
                self.assertIn(module_name, workflow)


if __name__ == "__main__":
    unittest.main()
