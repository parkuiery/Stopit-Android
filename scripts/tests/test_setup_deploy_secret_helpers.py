import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAY_HELPER_SOURCE = REPO_ROOT / "scripts" / "setup-play-deploy-secrets.sh"
DISCORD_HELPER_SOURCE = REPO_ROOT / "scripts" / "setup-discord-deploy-secrets.sh"


class SetupDeploySecretHelpersTest(unittest.TestCase):
    def test_setup_play_helper_mentions_discord_helper_and_sets_only_build_upload_secrets(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)
            self._write_gh_stub(repo)

            keystore = repo / "upload-key.jks"
            service_account = repo / "play-service-account.json"
            google_services = repo / "google-services.json"
            keystore.write_bytes(b"fake-keystore")
            service_account.write_text('{"client_email":"robot@example.com"}')
            google_services.write_text('{"project_info":{"project_id":"stopit"}}')

            result = subprocess.run(
                [
                    "bash",
                    "scripts/setup-play-deploy-secrets.sh",
                    "--keystore",
                    str(keystore),
                    "--service-account",
                    str(service_account),
                    "--alias",
                    "upload",
                    "--google-services",
                    str(google_services),
                ],
                cwd=repo,
                env=self._env(
                    repo,
                    {
                        "ANDROID_KEYSTORE_PASSWORD": "store-pass",
                        "ANDROID_KEY_PASSWORD": "key-pass",
                    },
                ),
                text=True,
                capture_output=True,
            )

            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn(
                "Discord deploy notification secrets are managed separately via scripts/setup-discord-deploy-secrets.sh.",
                result.stdout,
            )

            log = (repo / "gh.log").read_text()
            self.assertIn("secret:ANDROID_KEYSTORE_BASE64:", log)
            self.assertIn("secret:ANDROID_KEYSTORE_PASSWORD:store-pass", log)
            self.assertIn("secret:ANDROID_KEY_ALIAS:upload", log)
            self.assertIn("secret:ANDROID_KEY_PASSWORD:key-pass", log)
            self.assertIn('secret:GOOGLE_PLAY_SERVICE_ACCOUNT_JSON:{"client_email":"robot@example.com"}', log)
            self.assertIn('secret:GOOGLE_SERVICES_JSON:{"project_info":{"project_id":"stopit"}}', log)
            self.assertNotIn("secret:DISCORD_BOT_TOKEN", log)
            self.assertNotIn("secret:DISCORD_DEPLOY_CHANNEL_ID", log)

    def test_setup_discord_helper_sets_only_github_discord_notification_secrets(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = pathlib.Path(tmp)
            self._write_repo_fixture(repo)
            self._write_gh_stub(repo)

            result = subprocess.run(
                ["bash", "scripts/setup-discord-deploy-secrets.sh"],
                cwd=repo,
                env=self._env(
                    repo,
                    {
                        "DISCORD_BOT_TOKEN": "bot-token-123",
                        "DISCORD_DEPLOY_CHANNEL_ID": "987654321",
                    },
                ),
                text=True,
                capture_output=True,
            )

            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn(
                "Firebase Functions promotion secrets remain a separate step; see functions/README.md and docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md.",
                result.stdout,
            )

            log = (repo / "gh.log").read_text()
            self.assertIn("secret:DISCORD_BOT_TOKEN:bot-token-123", log)
            self.assertIn("secret:DISCORD_DEPLOY_CHANNEL_ID:987654321", log)
            self.assertNotIn("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", log)
            self.assertNotIn("GOOGLE_SERVICES_JSON", log)

    def _write_repo_fixture(self, repo: pathlib.Path) -> None:
        (repo / "scripts").mkdir(parents=True, exist_ok=True)
        shutil.copy2(PLAY_HELPER_SOURCE, repo / "scripts" / "setup-play-deploy-secrets.sh")
        shutil.copy2(DISCORD_HELPER_SOURCE, repo / "scripts" / "setup-discord-deploy-secrets.sh")
        for path in [repo / "scripts" / "setup-play-deploy-secrets.sh", repo / "scripts" / "setup-discord-deploy-secrets.sh"]:
            path.chmod(path.stat().st_mode | stat.S_IXUSR)

    def _write_gh_stub(self, repo: pathlib.Path) -> None:
        bin_dir = repo / "bin"
        bin_dir.mkdir(parents=True, exist_ok=True)
        gh_stub = bin_dir / "gh"
        log_path = repo / "gh.log"
        gh_stub.write_text(
            textwrap.dedent(
                f"""
                #!/usr/bin/env python3
                import pathlib
                import sys

                log_path = pathlib.Path(r"{log_path}")
                args = sys.argv[1:]

                if args == ["auth", "status"]:
                    raise SystemExit(0)

                if args == ["repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"]:
                    print("parkuiery/Stopit-Android")
                    raise SystemExit(0)

                if len(args) >= 3 and args[0] == "secret" and args[1] == "set":
                    name = args[2]
                    value = sys.stdin.read()
                    with log_path.open("a", encoding="utf-8") as fh:
                        fh.write(f"secret:{{name}}:{{value}}\\n")
                    raise SystemExit(0)

                print(f"unexpected gh args: {{args}}", file=sys.stderr)
                raise SystemExit(99)
                """
            ).lstrip()
        )
        gh_stub.chmod(gh_stub.stat().st_mode | stat.S_IXUSR)

    def _env(self, repo: pathlib.Path, extra: dict[str, str]) -> dict[str, str]:
        env = os.environ.copy()
        env["PATH"] = f"{repo / 'bin'}:{env['PATH']}"
        env.update(extra)
        return env


if __name__ == "__main__":
    unittest.main()
