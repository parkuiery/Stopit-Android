import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_SOURCE = REPO_ROOT / "scripts" / "validate-play-deploy-ref.sh"


class ValidatePlayDeployRefScriptTest(unittest.TestCase):
    def test_tag_push_accepts_semver_tag_that_is_reachable_from_origin_main_and_checks_previous_production_marker(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo, merge_base_exit=0, marker_exit=0)

            result = self._run_script(repo, event_name="push", ref="refs/tags/v1.7.4", ref_name="v1.7.4")

            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            self.assertIn("Validated Play deploy tag v1.7.4 on origin/main", result.stdout)
            marker_env = (repo / "marker-env.txt").read_text()
            self.assertIn("STOPIT_RELEASE_GATE_EXCLUDE_TAG=v1.7.4", marker_env)

    def test_tag_push_rejects_semver_tag_that_is_not_reachable_from_origin_main(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo, merge_base_exit=1, marker_exit=0)

            result = self._run_script(repo, event_name="push", ref="refs/tags/v1.7.4", ref_name="v1.7.4")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Play deploy tag v1.7.4 must point to a commit reachable from origin/main", result.stderr)
            self.assertFalse((repo / "marker-env.txt").exists(), "marker gate should not run after ancestry failure")

    def test_tag_push_rejects_when_previous_production_marker_gate_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo, merge_base_exit=0, marker_exit=1)

            result = self._run_script(repo, event_name="push", ref="refs/tags/v1.7.4", ref_name="v1.7.4")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("previous production marker missing", result.stderr)

    def test_manual_dispatch_accepts_semver_tag_that_is_reachable_from_origin_main_and_checks_previous_production_marker(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo, merge_base_exit=0, marker_exit=0)

            result = self._run_script(repo, event_name="workflow_dispatch", ref="refs/tags/v1.7.4", ref_name="v1.7.4")

            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            self.assertIn("Validated Play deploy tag v1.7.4 on origin/main", result.stdout)
            marker_env = (repo / "marker-env.txt").read_text()
            self.assertIn("STOPIT_RELEASE_GATE_EXCLUDE_TAG=v1.7.4", marker_env)

    def test_manual_dispatch_rejects_semver_tag_that_is_not_reachable_from_origin_main(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo, merge_base_exit=1, marker_exit=0)

            result = self._run_script(repo, event_name="workflow_dispatch", ref="refs/tags/v1.7.4", ref_name="v1.7.4")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Play deploy tag v1.7.4 must point to a commit reachable from origin/main", result.stderr)
            self.assertFalse((repo / "marker-env.txt").exists(), "marker gate should not run after ancestry failure")

    def _write_repo_fixture(self, repo: pathlib.Path, *, merge_base_exit: int, marker_exit: int) -> None:
        (repo / "scripts").mkdir(parents=True, exist_ok=True)
        shutil.copy2(SCRIPT_SOURCE, repo / "scripts" / "validate-play-deploy-ref.sh")
        script_path = repo / "scripts" / "validate-play-deploy-ref.sh"
        script_path.chmod(script_path.stat().st_mode | stat.S_IXUSR)
        self._write_git_stub(repo, merge_base_exit=merge_base_exit)
        self._write_marker_stub(repo, marker_exit=marker_exit)

    def _write_git_stub(self, repo: pathlib.Path, *, merge_base_exit: int) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        git_stub = bin_dir / "git"
        calls_file = repo / "git-calls.txt"
        git_stub.write_text(
            textwrap.dedent(
                f"""
                #!/usr/bin/env python3
                import pathlib
                import sys

                calls = pathlib.Path(r"{calls_file}")
                args = sys.argv[1:]
                calls.write_text((calls.read_text() if calls.exists() else "") + " ".join(args) + "\\n")

                if args == ["fetch", "origin", "main", "--tags"]:
                    raise SystemExit(0)
                if args == ["rev-parse", "v1.7.4^{{commit}}"]:
                    print("tag-commit")
                    raise SystemExit(0)
                if args == ["merge-base", "--is-ancestor", "tag-commit", "origin/main"]:
                    raise SystemExit({merge_base_exit})

                print(f"unexpected git args: {{args}}", file=sys.stderr)
                raise SystemExit(99)
                """
            ).lstrip()
        )
        git_stub.chmod(git_stub.stat().st_mode | stat.S_IXUSR)

    def _write_marker_stub(self, repo: pathlib.Path, *, marker_exit: int) -> None:
        marker = repo / "scripts" / "check-latest-production-deployed.sh"
        marker.write_text(
            textwrap.dedent(
                f"""
                #!/usr/bin/env bash
                set -euo pipefail
                printf 'STOPIT_RELEASE_GATE_EXCLUDE_TAG=%s\n' "${{STOPIT_RELEASE_GATE_EXCLUDE_TAG:-}}" > marker-env.txt
                if [[ {marker_exit} -ne 0 ]]; then
                  echo 'previous production marker missing' >&2
                  exit {marker_exit}
                fi
                exit 0
                """
            ).lstrip()
        )
        marker.chmod(marker.stat().st_mode | stat.S_IXUSR)

    def _run_script(self, repo: pathlib.Path, *, event_name: str, ref: str, ref_name: str) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["PATH"] = f"{repo / 'bin'}:{env['PATH']}"
        env["GITHUB_EVENT_NAME"] = event_name
        env["GITHUB_REF"] = ref
        env["GITHUB_REF_NAME"] = ref_name
        return subprocess.run(
            ["bash", "scripts/validate-play-deploy-ref.sh"],
            cwd=repo,
            env=env,
            text=True,
            capture_output=True,
        )


if __name__ == "__main__":
    unittest.main()
