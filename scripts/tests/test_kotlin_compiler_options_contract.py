import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_BUILD_FILE = REPO_ROOT / "app/build.gradle.kts"
KDS_BUILD_FILE = REPO_ROOT / "core/kds/build.gradle.kts"
DEPENDENCY_RUNBOOK = REPO_ROOT / "docs/DEPENDENCY_LINT_MAINTENANCE.md"
DEPENDABOT_CONFIG = REPO_ROOT / ".github/dependabot.yml"


class KotlinCompilerOptionsContractTest(unittest.TestCase):
    def test_android_modules_use_compiler_options_dsl_for_jvm_target(self):
        for build_file in (APP_BUILD_FILE, KDS_BUILD_FILE):
            contents = build_file.read_text()
            with self.subTest(build_file=build_file.relative_to(REPO_ROOT)):
                self.assertNotIn("kotlinOptions", contents)
                self.assertNotIn("jvmTarget = \"17\"", contents)
                self.assertIn("import org.jetbrains.kotlin.gradle.dsl.JvmTarget", contents)
                self.assertRegex(
                    contents,
                    re.compile(
                        r"kotlin\s*\{[\s\S]*?compilerOptions\s*\{[\s\S]*?jvmTarget\.set\(JvmTarget\.JVM_17\)",
                        re.MULTILINE,
                    ),
                    "Kotlin 2.3+ readiness requires the typed compilerOptions DSL with JVM_17",
                )

    def test_operator_docs_and_dependabot_policy_reflect_post_migration_boundary(self):
        runbook = DEPENDENCY_RUNBOOK.read_text()
        dependabot = DEPENDABOT_CONFIG.read_text()

        for required in (
            "#1009",
            "compilerOptions DSL",
            "JvmTarget.JVM_17",
            "Kotlin 2.3+",
            "Kotlin/toolchain lane",
        ):
            with self.subTest(required=required):
                self.assertIn(required, runbook + "\n" + dependabot)

        self.assertNotIn(
            "until app/core:kds migrate to compilerOptions",
            dependabot,
            "Dependabot comments should not describe the completed #1009 migration as still pending",
        )


if __name__ == "__main__":
    unittest.main()
