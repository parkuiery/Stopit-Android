import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPOSE_MODULE_BUILD_FILES = (
    REPO_ROOT / "app/build.gradle.kts",
    REPO_ROOT / "core/kds/build.gradle.kts",
)
COMPOSE_COMPILER_PLUGIN_LINE = 'alias(libs.plugins.compose.compiler)'
LEGACY_EXTENSION_PIN = 'kotlinCompilerExtensionVersion = "1.5.1"'


class ComposeCompilerGradleContractTest(unittest.TestCase):
    def test_compose_modules_use_plugin_and_not_legacy_extension_pin(self):
        for build_file in COMPOSE_MODULE_BUILD_FILES:
            with self.subTest(build_file=build_file.relative_to(REPO_ROOT).as_posix()):
                contents = build_file.read_text()
                self.assertIn(COMPOSE_COMPILER_PLUGIN_LINE, contents)
                self.assertNotIn(LEGACY_EXTENSION_PIN, contents)


if __name__ == "__main__":
    unittest.main()
