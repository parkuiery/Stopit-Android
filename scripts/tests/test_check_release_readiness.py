import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_SOURCE = REPO_ROOT / "scripts" / "check-release-readiness.sh"
GUARD_SOURCE = REPO_ROOT / "scripts" / "play_version_code_guard.py"


class CheckReleaseReadinessScriptTest(unittest.TestCase):
    def test_fetches_origin_main_before_reading_main_version_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)
            self._write_git_stub(repo, fetch_succeeds=True)

            result = self._run_script(repo)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("origin/main versionCode=20", result.stdout)
            self.assertIn(
                "versionCode must exceed main branch: main=20, required>=21, candidate=11",
                result.stdout + result.stderr,
            )

    def test_reports_clear_message_when_fetch_origin_main_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)
            self._write_git_stub(repo, fetch_succeeds=False)

            result = self._run_script(repo)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Failed to fetch origin/main before release readiness validation.", result.stderr)
            self.assertIn("Check network/remote access and retry.", result.stderr)

    def _write_repo_fixture(self, repo: pathlib.Path) -> None:
        (repo / "app").mkdir(parents=True, exist_ok=True)
        (repo / "scripts").mkdir(parents=True, exist_ok=True)
        (repo / "app" / "build.gradle.kts").write_text(
            textwrap.dedent(
                """
                android {
                    defaultConfig {
                        versionCode = 11
                        versionName = "1.7.4"
                    }
                }
                """
            ).strip()
        )
        shutil.copy2(SCRIPT_SOURCE, repo / "scripts" / "check-release-readiness.sh")
        shutil.copy2(GUARD_SOURCE, repo / "scripts" / "play_version_code_guard.py")
        script_path = repo / "scripts" / "check-release-readiness.sh"
        script_path.chmod(script_path.stat().st_mode | stat.S_IXUSR)
        self._write_actionlint_stub(repo)
        self._write_gradlew_stub(repo)

    def _write_git_stub(self, repo: pathlib.Path, *, fetch_succeeds: bool) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        state_file = repo / ".git-fetch-state"
        git_stub = bin_dir / "git"
        fetch_exit = 0 if fetch_succeeds else 1
        git_stub.write_text(
            textwrap.dedent(
                f"""
                #!/usr/bin/env python3
                import pathlib
                import sys

                state_file = pathlib.Path(r"{state_file}")
                args = sys.argv[1:]

                if args == ["branch", "--show-current"]:
                    print("release/1.7.4")
                    raise SystemExit(0)

                if args == ["status", "--porcelain"]:
                    raise SystemExit(0)

                if args == ["fetch", "origin", "main"]:
                    state_file.write_text("fetched=1")
                    if {fetch_exit} != 0:
                        print("fatal: unable to reach origin/main", file=sys.stderr)
                    raise SystemExit({fetch_exit})

                if args == ["show", "origin/main:app/build.gradle.kts"]:
                    fetched = state_file.exists() and state_file.read_text() == "fetched=1"
                    version_code = 20 if fetched else 10
                    print('android {{\\n    defaultConfig {{\\n        versionCode = %d\\n        versionName = "1.7.3"\\n    }}\\n}}' % version_code)
                    raise SystemExit(0)

                print(f"unexpected git args: {{args}}", file=sys.stderr)
                raise SystemExit(99)
                """
            ).lstrip()
        )
        git_stub.chmod(git_stub.stat().st_mode | stat.S_IXUSR)

    def _write_actionlint_stub(self, repo: pathlib.Path) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        stub = bin_dir / "actionlint"
        stub.write_text("#!/usr/bin/env bash\nexit 0\n")
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)

    def _write_gradlew_stub(self, repo: pathlib.Path) -> None:
        stub = repo / "gradlew"
        stub.write_text("#!/usr/bin/env bash\nexit 0\n")
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)

    def _run_script(self, repo: pathlib.Path) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["PATH"] = f"{repo / 'bin'}:{env['PATH']}"
        env["STOPIT_PLAY_MAX_VERSION_CODE"] = "0"
        return subprocess.run(
            ["bash", "scripts/check-release-readiness.sh"],
            cwd=repo,
            env=env,
            text=True,
            capture_output=True,
        )


if __name__ == "__main__":
    unittest.main()
