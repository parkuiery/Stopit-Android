import importlib.util
import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
NOTIFIER_PATH = REPO_ROOT / "scripts" / "notify-discord-deploy.py"
PLAY_DOC_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"


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

    def test_play_deployment_doc_explains_discord_release_status_boundary(self):
        doc = PLAY_DOC_PATH.read_text()

        self.assertIn("Discord production 알림", doc)
        self.assertIn("`release_status`", doc)
        self.assertIn("`completed`일 때만 production 완료 marker", doc)
        self.assertIn("`draft`, `inProgress`, `halted`", doc)


if __name__ == "__main__":
    unittest.main()
