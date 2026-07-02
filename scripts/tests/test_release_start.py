import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_SOURCE = REPO_ROOT / "scripts" / "release-start.sh"


class ReleaseStartScriptTest(unittest.TestCase):
    def test_rejects_no_dry_run_because_release_start_must_verify_release_tasks(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)

            result = self._run_script(
                repo,
                "1.7.8",
                "--no-dry-run",
                "--fallback-play-max-version-code",
                "42",
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("release-start always runs release task dry-run verification", result.stderr)
            self.assertFalse((repo / "bump-version-args.txt").exists())

    def test_forwards_guard_arguments_to_bump_version_when_release_dry_run_is_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)

            result = self._run_script(
                repo,
                "1.7.8",
                "--code",
                "43",
                "--fallback-play-max-version-code",
                "42",
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                "1.7.8 --code 43 --fallback-play-max-version-code 42",
                (repo / "bump-version-args.txt").read_text().strip(),
            )

    def _write_repo_fixture(self, repo: pathlib.Path) -> None:
        (repo / "scripts").mkdir(parents=True, exist_ok=True)
        shutil.copy2(SCRIPT_SOURCE, repo / "scripts" / "release-start.sh")
        script_path = repo / "scripts" / "release-start.sh"
        script_path.chmod(script_path.stat().st_mode | stat.S_IXUSR)
        self._write_git_stub(repo)
        self._write_check_latest_production_stub(repo)
        self._write_bump_version_stub(repo)

    def _write_git_stub(self, repo: pathlib.Path) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        git_stub = bin_dir / "git"
        git_stub.write_text(
            textwrap.dedent(
                r"""
                #!/usr/bin/env bash
                set -euo pipefail
                case "$*" in
                  "status --porcelain") exit 0 ;;
                  "fetch origin main develop") exit 0 ;;
                  "checkout develop") exit 0 ;;
                  "pull --ff-only origin develop") exit 0 ;;
                  checkout\ -b\ release/*) exit 0 ;;
                  "add app/build.gradle.kts README.md") exit 0 ;;
                  commit\ -m\ chore:\ bump\ version\ to\ *) exit 0 ;;
                  *) echo "unexpected git args: $*" >&2; exit 99 ;;
                esac
                """
            ).lstrip()
        )
        git_stub.chmod(git_stub.stat().st_mode | stat.S_IXUSR)

    def _write_check_latest_production_stub(self, repo: pathlib.Path) -> None:
        stub = repo / "scripts" / "check-latest-production-deployed.sh"
        stub.write_text("#!/usr/bin/env bash\nexit 0\n")
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)

    def _write_bump_version_stub(self, repo: pathlib.Path) -> None:
        stub = repo / "scripts" / "bump-version.sh"
        stub.write_text(
            textwrap.dedent(
                """
                #!/usr/bin/env bash
                set -euo pipefail
                printf '%s\n' "$*" > bump-version-args.txt
                exit 0
                """
            ).lstrip()
        )
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)

    def _run_script(self, repo: pathlib.Path, *args: str) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["PATH"] = f"{repo / 'bin'}:{env['PATH']}"
        return subprocess.run(
            ["bash", "scripts/release-start.sh", *args],
            cwd=repo,
            env=env,
            text=True,
            capture_output=True,
        )


if __name__ == "__main__":
    unittest.main()
