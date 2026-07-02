import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
PLAY_DEPLOYMENT_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class ReleaseSigningGradleContractTest(unittest.TestCase):
    def test_prod_release_artifact_tasks_fail_without_release_signing_env(self):
        build_gradle = APP_BUILD_GRADLE.read_text()

        self.assertIn("requiredReleaseSigningEnvVars", build_gradle)
        self.assertIn("ensureReleaseSigningForProdReleaseArtifacts", build_gradle)
        self.assertIn("gradle.taskGraph.whenReady", build_gradle)
        self.assertIn("ANDROID_KEYSTORE_PATH", build_gradle)
        self.assertIn("ANDROID_KEYSTORE_PASSWORD", build_gradle)
        self.assertIn("ANDROID_KEY_ALIAS", build_gradle)
        self.assertIn("ANDROID_KEY_PASSWORD", build_gradle)
        self.assertIn("Debug signing fallback is not allowed for prodRelease artifacts", build_gradle)
        self.assertRegex(
            build_gradle,
            r"Regex\(\"\^\(bundle\|assemble\|sign\)ProdRelease",
            "prodRelease bundle/assemble/sign artifact tasks should be guarded",
        )
        self.assertIn("packageProdReleaseBundle", build_gradle)
        self.assertIn("gradle.startParameter.taskNames", build_gradle)

    def test_prod_release_uses_release_signing_config_not_debug_fallback(self):
        build_gradle = APP_BUILD_GRADLE.read_text()

        self.assertIn('signingConfig = signingConfigs.getByName("release")', build_gradle)
        self.assertNotIn('signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")', build_gradle)
        self.assertNotIn('else "debug"', build_gradle)

    def test_operator_docs_describe_prod_release_debug_signing_ban(self):
        docs = [PLAY_DEPLOYMENT_DOC.read_text(), RELEASE_CONTEXT_DOC.read_text()]

        for doc in docs:
            normalized = re.sub(r"\s+", " ", doc)
            self.assertIn("prodRelease", normalized)
            self.assertIn("debug signing fallback", normalized)
            self.assertIn("ANDROID_KEYSTORE_PATH", normalized)
            self.assertIn("ANDROID_KEYSTORE_PASSWORD", normalized)
            self.assertIn("ANDROID_KEY_ALIAS", normalized)
            self.assertIn("ANDROID_KEY_PASSWORD", normalized)
            self.assertIn(":app:assembleProdDebug", normalized)


if __name__ == "__main__":
    unittest.main()
