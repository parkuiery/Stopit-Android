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

    def test_gradle_dependabot_patch_minor_groups_are_split_by_risk_lane(self):
        config = DEPENDABOT_CONFIG.read_text()
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        gradle_update = re.search(
            r"package-ecosystem:\s*[\"']gradle[\"'][\s\S]*?(?=\n\s*-\s*package-ecosystem:|\Z)",
            config,
        )
        if gradle_update is None:
            self.fail("Gradle Dependabot update block must exist")
        gradle_block = gradle_update.group(0)

        self.assertNotRegex(
            gradle_block,
            r"android-gradle-patch-minor:[\s\S]*?patterns:\s*\n\s*-\s*[\"']\*[\"']",
            "#1034 forbids one broad Gradle patch/minor group that mixes every Android dependency risk lane",
        )
        for group_name in [
            "android-gradle-firebase-google-patch-minor",
            "android-gradle-androidx-ui-runtime-patch-minor",
            "android-gradle-room-ksp-patch-minor",
            "android-gradle-test-tooling-patch-minor",
            "android-gradle-runtime-libraries-patch-minor",
            "android-gradle-toolchain-held-patch-minor",
        ]:
            with self.subTest(group_name=group_name):
                self.assertIn(f"{group_name}:", gradle_block)

        expected_patterns = [
            "com.google.firebase:*",
            "com.google.gms:google-services",
            "com.google.firebase.crashlytics",
            "com.google.android.gms:play-services-ads",
            "androidx.core:*",
            "androidx.lifecycle:*",
            "androidx.activity:*",
            "androidx.compose:*",
            "androidx.navigation:*",
            "androidx.appcompat:*",
            "androidx.datastore:*",
            "androidx.hilt:*",
            "com.google.devtools.ksp",
            "androidx.room:*",
            "junit:*",
            "androidx.test*:*",
            "org.mockito:*",
            "org.jetbrains.kotlinx:*",
            "com.airbnb.android:*",
            "com.google.android.material:*",
            "org.orbit-mvi:*",
        ]
        for pattern in expected_patterns:
            with self.subTest(pattern=pattern):
                self.assertIn(pattern, gradle_block)

        for required in [
            "Gradle Dependabot risk-lane split (#1034)",
            "android-gradle-firebase-google-patch-minor",
            "android-gradle-androidx-ui-runtime-patch-minor",
            "android-gradle-room-ksp-patch-minor",
            "android-gradle-test-tooling-patch-minor",
            "android-gradle-runtime-libraries-patch-minor",
            "android-gradle-toolchain-held-patch-minor",
            "broad `patterns: [\"*\"]`",
            "#1013",
            "분할 재생성",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)

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
            "android-gradle-toolchain-held-patch-minor",
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

    def test_androidx_compile_sdk_agp_boundary_updates_are_held(self):
        config = DEPENDABOT_CONFIG.read_text()
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        expected_holds = {
            "androidx.core:core-ktx": r"\[1\.18,\)",
            "androidx.navigation:navigation-compose": r"\[2\.9,\)",
            "androidx.lifecycle:lifecycle-runtime-ktx": r"\[2\.10,\)",
            "androidx.lifecycle:lifecycle-process": r"\[2\.10,\)",
            "androidx.lifecycle:lifecycle-runtime-compose": r"\[2\.10,\)",
            "androidx.activity:activity-compose": r"\[1\.12,\)",
            "androidx.compose:compose-bom": r"\[2025\.11,\)",
        }
        for dependency, version_range in expected_holds.items():
            with self.subTest(dependency=dependency):
                self.assertRegex(
                    config,
                    rf"dependency-name:\s*[\"']{re.escape(dependency)}[\"'][\s\S]*?versions:\s*\n\s*-\s*[\"']{version_range}[\"']",
                    "#1008 requires holding known AndroidX updates that need compileSdk/AGP above the current stack",
                )

        for required in [
            "AndroidX compileSdk / AGP boundary guard (#1008, #1051)",
            "core-ktx 1.18.0",
            "activity-compose 1.12.4",
            "androidx.lifecycle:* 2.10.x",
            "androidx.compose:compose-bom 2025.11.x",
            "compileSdk 35",
            "compileSdk 36+",
            "compileSdk 37+",
            "AGP 9.1.0 or higher",
            "androidx.navigationevent:*:1.0.0",
            "androidx.core:core:1.19.0",
            "known-incompatible",
            "별도 Android toolchain lane",
            "PR #989",
            "PR #1042",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)

    def test_ksp_and_kotlinx_metadata_updates_stay_in_kotlin_toolchain_lane(self):
        config = DEPENDABOT_CONFIG.read_text()
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        expected_holds = {
            "com.google.devtools.ksp": r"\[2\.3,\)",
            "org.jetbrains.kotlinx:kotlinx-serialization-json": r"\[1\.11,\)",
        }
        for dependency, version_range in expected_holds.items():
            with self.subTest(dependency=dependency):
                self.assertRegex(
                    config,
                    rf"dependency-name:\s*[\"']{re.escape(dependency)}[\"'][\s\S]*?versions:\s*\n\s*-\s*[\"']{version_range}[\"']",
                    "#1051 requires holding KSP/Kotlin metadata 2.3.x artifacts until a dedicated Kotlin toolchain lane validates them",
                )

        for required in [
            "KSP 2.3.x",
            "com.google.devtools.ksp 2.3.9",
            "kotlinx-serialization-json 1.11.x",
            "Kotlin metadata 2.3.x",
            "Kotlin 2.1.x",
            "PR #1043",
            "PR #1045",
            "별도 Kotlin/toolchain lane",
            "known-incompatible",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)


if __name__ == "__main__":
    unittest.main()
