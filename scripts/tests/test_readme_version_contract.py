import os
import pathlib
import re
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
BUMP_SCRIPT_SOURCE = REPO_ROOT / "scripts" / "bump-version.sh"
README_SOURCE = REPO_ROOT / "README.md"
BUILD_GRADLE_SOURCE = REPO_ROOT / "app" / "build.gradle.kts"


README_VERSION_RE = re.compile(
    r"^- \*\*현재 버전\*\*: (?P<name>\d+\.\d+\.\d+) \(versionCode (?P<code>\d+)\)$",
    re.MULTILINE,
)
GRADLE_VERSION_NAME_RE = re.compile(r'versionName\s*=\s*"(?P<name>[^"]+)"')
GRADLE_VERSION_CODE_RE = re.compile(r"versionCode\s*=\s*(?P<code>\d+)")


class ReadmeVersionContractTest(unittest.TestCase):
    def test_readme_current_version_matches_gradle_version(self):
        readme = README_SOURCE.read_text()
        gradle = BUILD_GRADLE_SOURCE.read_text()

        readme_match = README_VERSION_RE.search(readme)
        self.assertIsNotNone(readme_match, "README.md must expose one '- **현재 버전**: x.y.z (versionCode n)' line")
        assert readme_match is not None

        gradle_name_match = GRADLE_VERSION_NAME_RE.search(gradle)
        gradle_code_match = GRADLE_VERSION_CODE_RE.search(gradle)
        self.assertIsNotNone(gradle_name_match, "app/build.gradle.kts must define versionName")
        self.assertIsNotNone(gradle_code_match, "app/build.gradle.kts must define versionCode")
        assert gradle_name_match is not None
        assert gradle_code_match is not None

        self.assertEqual(gradle_name_match.group("name"), readme_match.group("name"))
        self.assertEqual(gradle_code_match.group("code"), readme_match.group("code"))

    def test_bump_version_updates_readme_current_version_line(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_bump_fixture(repo)

            env = {**os.environ, "PATH": f"{repo / 'bin'}:{os.environ['PATH']}"}
            result = subprocess.run(
                [
                    "bash",
                    "scripts/bump-version.sh",
                    "1.7.8",
                    "--code",
                    "43",
                    "--fallback-play-max-version-code",
                    "42",
                    "--no-dry-run",
                ],
                cwd=repo,
                env=env,
                text=True,
                capture_output=True,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('- **현재 버전**: 1.7.8 (versionCode 43)', (repo / "README.md").read_text())
            self.assertIn('versionName = "1.7.8"', (repo / "app" / "build.gradle.kts").read_text())
            self.assertIn('versionCode = 43', (repo / "app" / "build.gradle.kts").read_text())

    def _write_bump_fixture(self, repo: pathlib.Path) -> None:
        (repo / "scripts").mkdir(parents=True, exist_ok=True)
        (repo / "app").mkdir(parents=True, exist_ok=True)
        shutil.copy2(BUMP_SCRIPT_SOURCE, repo / "scripts" / "bump-version.sh")
        shutil.copy2(README_SOURCE, repo / "README.md")
        shutil.copy2(BUILD_GRADLE_SOURCE, repo / "app" / "build.gradle.kts")
        (repo / "scripts" / "bump-version.sh").chmod((repo / "scripts" / "bump-version.sh").stat().st_mode | stat.S_IXUSR)
        self._write_git_stub(repo)
        self._write_play_guard_stub(repo)
        self._write_gradlew_stub(repo)

    def _write_git_stub(self, repo: pathlib.Path) -> None:
        git_stub = repo / "git"
        git_stub.write_text(
            textwrap.dedent(
                r"""
                #!/usr/bin/env bash
                set -euo pipefail
                case "$*" in
                  "fetch origin main") exit 0 ;;
                  "show origin/main:app/build.gradle.kts")
                    cat app/build.gradle.kts
                    exit 0
                    ;;
                  *) echo "unexpected git args: $*" >&2; exit 99 ;;
                esac
                """
            ).lstrip()
        )
        git_stub.chmod(git_stub.stat().st_mode | stat.S_IXUSR)
        bin_dir = repo / "bin"
        bin_dir.mkdir()
        shutil.move(str(git_stub), bin_dir / "git")

    def _write_play_guard_stub(self, repo: pathlib.Path) -> None:
        guard = repo / "scripts" / "play_version_code_guard.py"
        guard.write_text("#!/usr/bin/env python3\nimport sys\nsys.exit(0)\n")
        guard.chmod(guard.stat().st_mode | stat.S_IXUSR)

    def _write_gradlew_stub(self, repo: pathlib.Path) -> None:
        gradlew = repo / "gradlew"
        gradlew.write_text("#!/usr/bin/env bash\nexit 0\n")
        gradlew.chmod(gradlew.stat().st_mode | stat.S_IXUSR)


if __name__ == "__main__":
    unittest.main()
