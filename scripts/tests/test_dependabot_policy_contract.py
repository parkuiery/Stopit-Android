import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
DEPENDABOT_CONFIG = REPO_ROOT / ".github" / "dependabot.yml"
DEPENDENCY_RUNBOOK = REPO_ROOT / "docs" / "DEPENDENCY_LINT_MAINTENANCE.md"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"


class DependabotPolicyContractTest(unittest.TestCase):
    def test_dependabot_policy_covers_stopit_dependency_ecosystems(self):
        self.assertTrue(
            DEPENDABOT_CONFIG.exists(),
            "#693 requires a Dependabot policy file for dependency update automation",
        )
        config = DEPENDABOT_CONFIG.read_text()

        self.assertIn("version: 2", config)
        expected_ecosystems = {
            "gradle": "/",
            "github-actions": "/",
            "npm": "/functions",
            "bun": "/tools/aso-screenshots",
        }
        for ecosystem, directory in expected_ecosystems.items():
            with self.subTest(ecosystem=ecosystem, directory=directory):
                pattern = (
                    rf"package-ecosystem:\s*[\"']?{re.escape(ecosystem)}[\"']?[\s\S]*?"
                    rf"directory:\s*[\"']?{re.escape(directory)}[\"']?"
                )
                self.assertRegex(config, pattern)

    def test_dependabot_policy_limits_noise_and_marks_manual_major_review(self):
        config = DEPENDABOT_CONFIG.read_text()

        self.assertIn("schedule:", config)
        self.assertGreaterEqual(config.count("interval: weekly"), 4)
        self.assertIn("open-pull-requests-limit:", config)
        self.assertIn('"maintenance"', config)
        self.assertIn('"automation"', config)
        self.assertIn('"dependencies"', config)
        self.assertIn("groups:", config)
        self.assertIn("patterns:", config)
        self.assertIn("ignore:", config)
        self.assertIn("update-types:", config)
        self.assertIn("version-update:semver-major", config)

    def test_release_critical_play_upload_action_is_not_dependabot_auto_bumped(self):
        config = DEPENDABOT_CONFIG.read_text()
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        self.assertRegex(
            config,
            r"dependency-name:\s*[\"']r0adkll/upload-google-play[\"']",
            "The Play upload action is release-critical and should not be auto-bumped by Dependabot",
        )
        for required in [
            "r0adkll/upload-google-play",
            "release-critical boundary",
            "자동 Dependabot PR 대상이 아니다",
            "release-governance PR",
            "scripts.tests.test_release_provenance_workflow_contract",
            "docs/PLAY_DEPLOYMENT.md",
            "docs/RELEASE_CHECKLIST.md",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)

    def test_operator_docs_explain_dependabot_triage_and_release_boundary(self):
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        for required in [
            "Dependabot",
            "#693",
            "#905",
            "weekly",
            "월 1회",
            "release train",
            "maintenance",
            "automation",
            "major update",
            "semver-major",
            "수동 검토",
            "ready",
            "backlog",
            "hold",
            "Play deploy",
            "release secret",
            ".github/dependabot.yml",
            "dependabot/*",
            "Branch Hygiene",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)

    def test_semver_major_audit_contract_keeps_major_manual_and_classified(self):
        config = DEPENDABOT_CONFIG.read_text()
        runbook = DEPENDENCY_RUNBOOK.read_text()

        self.assertGreaterEqual(
            config.count("version-update:semver-major"),
            4,
            "Every Dependabot ecosystem should keep semver-major ignored for manual #905 audit",
        )
        for required in [
            "Dependabot semver-major 수동 감사 lane (#905)",
            "매월 첫 번째 월요일",
            "release train 전",
            "Gradle / Android stack",
            "GitHub Actions",
            "Firebase Functions npm",
            "ASO screenshots Bun",
            "Classification:",
            "ready:",
            "backlog:",
            "hold:",
            "Play deploy, release secret, signing secret, Firebase service account secret은 semver-major 감사의 산출물이 아니다",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, runbook)

    def test_android_gradle_stack_known_incompatible_hilt_is_held(self):
        config = DEPENDABOT_CONFIG.read_text()
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        for dependency in [
            "com.google.dagger.hilt.android",
            "com.google.dagger:hilt-android",
            "com.google.dagger:hilt-compiler",
        ]:
            with self.subTest(dependency=dependency):
                self.assertRegex(
                    config,
                    rf"dependency-name:\s*[\"']{re.escape(dependency)}[\"'][\s\S]*?versions:\s*\n\s*-\s*[\"']\[2\.59,\)[\"']",
                    "#925 requires holding Hilt 2.59+ while Stopit remains on AGP 8.x",
                )

        for required in [
            "Android Gradle stack compatibility guard (#925)",
            "Hilt 2.59+",
            "AGP 9",
            "AGP 8.x",
            "android-gradle-patch-minor",
            "known-incompatible",
            "#914",
            "Gradle configuration 단계",
            "별도 toolchain lane",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)

    def test_android_gradle_stack_known_incompatible_kotlin_23_is_held(self):
        config = DEPENDABOT_CONFIG.read_text()
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        for dependency in [
            "org.jetbrains.kotlin.android",
            "org.jetbrains.kotlin.plugin.compose",
            "org.jetbrains.kotlin.plugin.serialization",
            "org.jetbrains.kotlin.jvm",
        ]:
            with self.subTest(dependency=dependency):
                self.assertRegex(
                    config,
                    rf"dependency-name:\s*[\"']{re.escape(dependency)}[\"'][\s\S]*?versions:\s*\n\s*-\s*[\"']\[2\.3,\)[\"']",
                    "#984 requires holding Kotlin 2.3+ until Stopit migrates build scripts to compilerOptions DSL",
                )

        for required in [
            "Kotlin 2.3+",
            "Kotlin 2.3.21",
            "compilerOptions DSL",
            "kotlinOptions.jvmTarget",
            "#928",
            "#939",
            "#984",
            "org.jetbrains.kotlin.jvm",
            "Using 'jvmTarget: String' is an error",
            "별도 Kotlin/toolchain lane",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)


if __name__ == "__main__":
    unittest.main()
