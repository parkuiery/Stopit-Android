import contextlib
import importlib.util
import io
import os
import pathlib
import unittest
from unittest import mock


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
NOTIFIER_PATH = REPO_ROOT / "scripts" / "notify-discord-deploy.py"
PLAY_DOC_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
PLAY_SECRETS_RUNBOOK_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOY_SECRETS_RUNBOOK.md"
PLAY_DEPLOY_WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"


def load_notifier_module():
    spec = importlib.util.spec_from_file_location("notify_discord_deploy", NOTIFIER_PATH)
    assert spec is not None
    assert spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class NotifyDiscordDeployTest(unittest.TestCase):
    def setUp(self):
        self.notifier = load_notifier_module()

    def test_completed_production_payload_marks_gate_unlock(self):
        payload = self.notifier.production_payload("v1.8.0", "https://example.test/run", release_status="completed")
        content = payload["content"]

        self.assertIn("release_status: `completed`", content)
        self.assertIn("production 완료 marker를 작성합니다", content)
        self.assertIn("다음 release gate가 열립니다", content)
        self.assertNotIn("완료 marker를 작성하지 않습니다", content)

    def test_non_completed_production_payload_does_not_claim_gate_unlock(self):
        for status in ("draft", "inProgress", "halted"):
            with self.subTest(status=status):
                payload = self.notifier.production_payload("v1.8.0", "https://example.test/run", release_status=status)
                content = payload["content"]

                self.assertIn(f"release_status: `{status}`", content)
                self.assertIn("production 완료 marker를 작성하지 않습니다", content)
                self.assertIn("다음 release gate는 아직 열리지 않습니다", content)
                self.assertNotIn("다음 release gate가 열립니다", content)

    def test_main_skips_missing_discord_secrets_without_failing_deploy(self):
        with mock.patch.dict(os.environ, {"DEPLOY_TRACK": "internal", "GITHUB_REF_NAME": "v1.8.0"}, clear=True):
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                result = self.notifier.main()

        self.assertEqual(result, 0)
        self.assertIn("skipping Discord deploy notification", stdout.getvalue())

    def test_main_warns_but_does_not_fail_when_discord_post_fails(self):
        env = {
            "DISCORD_BOT_TOKEN": "token",
            "DISCORD_DEPLOY_CHANNEL_ID": "123456",
            "DEPLOY_TRACK": "internal",
            "GITHUB_REF_NAME": "v1.8.0",
            "GITHUB_SERVER_URL": "https://github.com",
            "GITHUB_REPOSITORY": "parkuiery/Stopit-Android",
            "GITHUB_RUN_ID": "999",
        }
        with mock.patch.dict(os.environ, env, clear=True), mock.patch.object(
            self.notifier,
            "post_discord_message",
            side_effect=RuntimeError("Discord returned HTTP 403: missing permissions"),
        ):
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr):
                result = self.notifier.main()

        self.assertEqual(result, 0)
        warning = stderr.getvalue()
        self.assertIn("::warning::Discord deploy notification failed", warning)
        self.assertIn("Play deploy result is unchanged", warning)
        self.assertIn("HTTP 403", warning)

    def test_main_posts_internal_notification_when_discord_secrets_exist(self):
        env = {
            "DISCORD_BOT_TOKEN": "token",
            "DISCORD_DEPLOY_CHANNEL_ID": "123456",
            "DEPLOY_TRACK": "internal",
            "GITHUB_REF_NAME": "v1.8.0",
            "GITHUB_SERVER_URL": "https://github.com",
            "GITHUB_REPOSITORY": "parkuiery/Stopit-Android",
            "GITHUB_RUN_ID": "999",
        }
        with mock.patch.dict(os.environ, env, clear=True), mock.patch.object(
            self.notifier,
            "post_discord_message",
        ) as post_message:
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                result = self.notifier.main()

        self.assertEqual(result, 0)
        post_message.assert_called_once()
        token, channel_id, payload = post_message.call_args.args
        self.assertEqual(token, "token")
        self.assertEqual(channel_id, "123456")
        self.assertIn("Stopit internal 배포 완료", payload["content"])
        self.assertIn("Posted internal deploy notification", stdout.getvalue())

    def test_play_deploy_workflow_keeps_discord_notification_non_blocking(self):
        workflow = PLAY_DEPLOY_WORKFLOW_PATH.read_text()

        self.assertIn("name: Notify Discord deploy channel", workflow)
        self.assertIn("continue-on-error: true", workflow)
        self.assertIn("python3 scripts/notify-discord-deploy.py", workflow)

    def test_play_deployment_doc_explains_discord_release_status_boundary(self):
        doc = PLAY_DOC_PATH.read_text()

        self.assertIn("Discord production 알림", doc)
        self.assertIn("`release_status`", doc)
        self.assertIn("`completed`일 때만 production 완료 marker", doc)
        self.assertIn("`draft`, `inProgress`, `halted`", doc)
        self.assertIn("Discord 알림 실패는 Play 업로드/승격 실패가 아니다", doc)

    def test_play_secret_runbook_explains_notification_failure_boundary(self):
        runbook = PLAY_SECRETS_RUNBOOK_PATH.read_text()

        self.assertIn("Discord deploy notification failure boundary", runbook)
        self.assertIn("알림 실패 != Play 배포 실패", runbook)
        self.assertIn("production completion marker", runbook)


if __name__ == "__main__":
    unittest.main()
