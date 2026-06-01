import os
import pathlib
import shutil
import stat
import subprocess
import sys
import tempfile
import textwrap
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_SOURCE = REPO_ROOT / "scripts" / "check-play-deploy-secret-contract.sh"

REQUIRED_SECRET_NAMES = [
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
    "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON",
    "GOOGLE_SERVICES_JSON",
    "DISCORD_BOT_TOKEN",
    "DISCORD_DEPLOY_CHANNEL_ID",
]


class CheckPlayDeploySecretContractScriptTest(unittest.TestCase):
    def test_audit_script_runs_all_checks_when_required_secret_names_exist(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)
            self._write_gh_stub(repo, secret_names=REQUIRED_SECRET_NAMES)
            self._write_rg_stub(repo)
            self._write_python3_stub(repo)

            result = self._run_script(repo)

            self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
            self.assertIn("[ok] GitHub secret names present:", result.stdout)
            self.assertIn("[run] workflow / notifier / functions secret consumer audit", result.stdout)
            self.assertIn("[run] helper syntax + contract regression tests", result.stdout)
            self.assertIn("[ok] play deploy secret contract audit finished", result.stdout)
            invocation_log = (repo / ".invocations.log").read_text(encoding="utf-8")
            self.assertIn("gh auth status", invocation_log)
            self.assertIn("gh secret list --json name --jq .[].name", invocation_log)
            self.assertIn(
                "rg -n GOOGLE_SERVICES_JSON|DISCORD_BOT_TOKEN|DISCORD_DEPLOY_CHANNEL_ID|GOOGLE_PLAY_SERVICE_ACCOUNT_JSON .github/workflows",
                invocation_log,
            )
            self.assertIn(
                "python3 -m unittest scripts.tests.test_setup_deploy_secret_helpers scripts.tests.test_play_deploy_secret_contract_runbook scripts.tests.test_check_play_deploy_secret_contract -v",
                invocation_log,
            )

    def test_audit_script_fails_when_required_github_secret_is_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)
            self._write_gh_stub(repo, secret_names=REQUIRED_SECRET_NAMES[:-1])
            self._write_rg_stub(repo)
            self._write_python3_stub(repo)

            result = self._run_script(repo)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("missing GitHub repo secrets: DISCORD_DEPLOY_CHANNEL_ID", result.stderr)
            invocation_log = (repo / ".invocations.log").read_text(encoding="utf-8")
            self.assertIn("gh secret list --json name --jq .[].name", invocation_log)
            self.assertNotIn("python3 -m unittest", invocation_log)

    def test_skip_gh_secret_list_does_not_leak_into_contract_unittests(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)
            self._write_gh_stub(repo, secret_names=REQUIRED_SECRET_NAMES)
            self._write_rg_stub(repo)
            self._write_python3_stub(repo, fail_if_env={"STOPIT_SKIP_GH_SECRET_LIST": "1"})

            result = self._run_script(repo, extra_env={"STOPIT_SKIP_GH_SECRET_LIST": "1"})

            self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
            self.assertIn("[skip] gh secret list check skipped via STOPIT_SKIP_GH_SECRET_LIST=1", result.stdout)
            self.assertIn("[ok] play deploy secret contract audit finished", result.stdout)

    def _write_repo_fixture(self, repo: pathlib.Path) -> None:
        (repo / "scripts").mkdir(parents=True, exist_ok=True)
        (repo / "functions" / "src").mkdir(parents=True, exist_ok=True)
        (repo / ".github" / "workflows").mkdir(parents=True, exist_ok=True)

        shutil.copy2(SCRIPT_SOURCE, repo / "scripts" / "check-play-deploy-secret-contract.sh")
        for path in [
            repo / "scripts" / "check-play-deploy-secret-contract.sh",
            repo / "scripts" / "setup-play-deploy-secrets.sh",
            repo / "scripts" / "setup-discord-deploy-secrets.sh",
        ]:
            if not path.exists():
                path.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            path.chmod(path.stat().st_mode | stat.S_IXUSR)

        (repo / "scripts" / "notify-discord-deploy.py").write_text("print('ok')\n", encoding="utf-8")
        (repo / "functions" / "src" / "index.ts").write_text("export const ok = true;\n", encoding="utf-8")

    def _write_gh_stub(self, repo: pathlib.Path, *, secret_names: list[str]) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        log_path = repo / ".invocations.log"
        gh_stub = bin_dir / "gh"
        gh_stub.write_text(
            textwrap.dedent(
                f"""
                #!{sys.executable}
                import pathlib
                import sys

                log_path = pathlib.Path(r"{log_path}")
                args = sys.argv[1:]
                log_path.write_text(log_path.read_text() + "gh " + " ".join(args) + "\\n" if log_path.exists() else "gh " + " ".join(args) + "\\n")

                if args == ["auth", "status"]:
                    raise SystemExit(0)
                if args == ["secret", "list", "--json", "name", "--jq", ".[ ].name"]:
                    print("unexpected jq token formatting", file=sys.stderr)
                    raise SystemExit(99)
                if args == ["secret", "list", "--json", "name", "--jq", ".[].name"]:
                    print("\\n".join({secret_names!r}))
                    raise SystemExit(0)

                print(f"unexpected gh args: {{args}}", file=sys.stderr)
                raise SystemExit(99)
                """
            ).lstrip(),
            encoding="utf-8",
        )
        gh_stub.chmod(gh_stub.stat().st_mode | stat.S_IXUSR)

    def _write_rg_stub(self, repo: pathlib.Path) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        log_path = repo / ".invocations.log"
        rg_stub = bin_dir / "rg"
        rg_stub.write_text(
            textwrap.dedent(
                f"""
                #!{sys.executable}
                import pathlib
                import sys

                log_path = pathlib.Path(r"{log_path}")
                args = sys.argv[1:]
                log_path.write_text(log_path.read_text() + "rg " + " ".join(args) + "\\n" if log_path.exists() else "rg " + " ".join(args) + "\\n")
                print("stub match")
                raise SystemExit(0)
                """
            ).lstrip(),
            encoding="utf-8",
        )
        rg_stub.chmod(rg_stub.stat().st_mode | stat.S_IXUSR)

    def _write_python3_stub(self, repo: pathlib.Path, *, fail_if_env: dict[str, str] | None = None) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        log_path = repo / ".invocations.log"
        python_stub = bin_dir / "python3"
        fail_if_env = fail_if_env or {}
        python_stub.write_text(
            textwrap.dedent(
                f"""
                #!{sys.executable}
                import os
                import pathlib
                import sys

                log_path = pathlib.Path(r"{log_path}")
                args = sys.argv[1:]
                log_path.write_text(log_path.read_text() + "python3 " + " ".join(args) + "\\n" if log_path.exists() else "python3 " + " ".join(args) + "\\n")
                fail_if_env = {fail_if_env!r}
                for key, value in fail_if_env.items():
                    if os.environ.get(key) == value:
                        print(f"unexpected env leakage: {{key}}={{value}}", file=sys.stderr)
                        raise SystemExit(91)
                raise SystemExit(0)
                """
            ).lstrip(),
            encoding="utf-8",
        )
        python_stub.chmod(python_stub.stat().st_mode | stat.S_IXUSR)

    def _run_script(
        self,
        repo: pathlib.Path,
        *,
        extra_env: dict[str, str] | None = None,
    ) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["PATH"] = f"{repo / 'bin'}:{env['PATH']}"
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            ["bash", "scripts/check-play-deploy-secret-contract.sh"],
            cwd=repo,
            env=env,
            text=True,
            capture_output=True,
        )


if __name__ == "__main__":
    unittest.main()
