import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "PLAY_DEPLOY_SECRETS_RUNBOOK.md"
ANDROID_CI = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_QA = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
RELEASE_BUILD = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOY = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
VERSION_GUARD = REPO_ROOT / ".github" / "workflows" / "version-guard.yml"
FUNCTIONS_README = REPO_ROOT / "functions" / "README.md"
FUNCTIONS_AGENTS = REPO_ROOT / "functions" / "AGENTS.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
STOPIT_CONTEXT_README = REPO_ROOT / "docs" / "ops" / "stopit" / "README.md"


class PlayDeploySecretContractRunbookTest(unittest.TestCase):
    def test_runbook_records_current_workflow_secret_restore_matrix(self):
        runbook = RUNBOOK.read_text(encoding="utf-8")
        android_ci = ANDROID_CI.read_text(encoding="utf-8")
        release_qa = RELEASE_QA.read_text(encoding="utf-8")
        release_build = RELEASE_BUILD.read_text(encoding="utf-8")
        play_deploy = PLAY_DEPLOY.read_text(encoding="utf-8")
        version_guard = VERSION_GUARD.read_text(encoding="utf-8")

        self.assertIn(
            "| `android-ci.yml` | `app/src/dev/google-services.json`, `app/src/prod/google-services.json` 둘 다 복원 | 없음 |",
            runbook,
        )
        self.assertIn(
            "| `release-qa.yml` | `app/src/dev/google-services.json`, `app/src/prod/google-services.json` 둘 다 복원 | 없음 |",
            runbook,
        )
        self.assertIn(
            "| `release-build.yml` | `app/src/prod/google-services.json`만 복원 | `ANDROID_*` |",
            runbook,
        )
        self.assertIn(
            "| `play-deploy.yml` | `app/src/prod/google-services.json`만 복원 | `ANDROID_*`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID` |",
            runbook,
        )
        self.assertIn(
            "| `version-guard.yml` | 사용 안 함 | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` |",
            runbook,
        )

        for workflow in (android_ci, release_qa):
            self.assertIn("printf '%s' \"$GOOGLE_SERVICES_JSON\" > app/src/dev/google-services.json", workflow)
            self.assertIn("printf '%s' \"$GOOGLE_SERVICES_JSON\" > app/src/prod/google-services.json", workflow)

        for workflow in (release_build, play_deploy):
            self.assertIn("printf '%s' \"$GOOGLE_SERVICES_JSON\" > app/src/prod/google-services.json", workflow)
            self.assertNotIn("app/src/dev/google-services.json", workflow)

        self.assertIn("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", version_guard)
        self.assertIn("DISCORD_BOT_TOKEN", play_deploy)
        self.assertIn("DISCORD_DEPLOY_CHANNEL_ID", play_deploy)

    def test_runbook_and_functions_readme_keep_secret_ownership_boundary_explicit(self):
        runbook = RUNBOOK.read_text(encoding="utf-8")
        functions_readme = FUNCTIONS_README.read_text(encoding="utf-8")
        functions_agents = FUNCTIONS_AGENTS.read_text(encoding="utf-8")
        docs_agents = DOCS_AGENTS.read_text(encoding="utf-8")
        stopit_context_readme = STOPIT_CONTEXT_README.read_text(encoding="utf-8")

        self.assertIn("scripts/setup-play-deploy-secrets.sh`는 Android/Play 배포용 helper", runbook)
        self.assertIn("scripts/setup-discord-deploy-secrets.sh", runbook)
        self.assertIn("Firebase Functions **양쪽에 모두 필요**", runbook)
        self.assertIn("GITHUB_ACTIONS_DISPATCH_TOKEN", runbook)

        self.assertIn(
            "GitHub Actions repo secrets for Android/Play build/upload and Discord deploy notifications are documented in `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`.",
            functions_readme,
        )
        self.assertIn(
            "This `functions/` setup only covers Firebase Functions secrets used by Crashlytics alerts and production-promotion interactions.",
            functions_readme,
        )
        self.assertIn("../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", functions_agents)
        self.assertIn("PLAY_DEPLOY_SECRETS_RUNBOOK.md", docs_agents)
        self.assertIn("../PLAY_DEPLOY_SECRETS_RUNBOOK.md", stopit_context_readme)


if __name__ == "__main__":
    unittest.main()
