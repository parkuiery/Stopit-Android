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
FUNCTIONS_SRC_AGENTS = REPO_ROOT / "functions" / "src" / "AGENTS.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
STOPIT_CONTEXT_README = REPO_ROOT / "docs" / "ops" / "stopit" / "README.md"
AGENT_ROLES = REPO_ROOT / "docs" / "ops" / "stopit" / "agent-roles.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
ENGINEERING_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "engineering-context.md"
DEV_SOURCE_AGENTS = REPO_ROOT / "app" / "src" / "dev" / "AGENTS.md"
PROD_SOURCE_AGENTS = REPO_ROOT / "app" / "src" / "prod" / "AGENTS.md"
PR_TEMPLATE = REPO_ROOT / ".github" / "pull_request_template.md"
ROOT_AGENTS = REPO_ROOT / "AGENTS.md"
APP_AGENTS = REPO_ROOT / "app" / "AGENTS.md"
CONTEXT_BUNDLE_PROTOCOL = REPO_ROOT / "docs" / "ops" / "stopit" / "context-bundle-protocol.md"
RECENT_DECISIONS = REPO_ROOT / "docs" / "ops" / "stopit" / "recent-decisions.md"
DISCORD_NOTIFIER = REPO_ROOT / "scripts" / "notify-discord-deploy.py"
FUNCTIONS_INDEX = REPO_ROOT / "functions" / "src" / "index.ts"


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
        functions_src_agents = FUNCTIONS_SRC_AGENTS.read_text(encoding="utf-8")
        docs_agents = DOCS_AGENTS.read_text(encoding="utf-8")
        stopit_context_readme = STOPIT_CONTEXT_README.read_text(encoding="utf-8")
        agent_roles = AGENT_ROLES.read_text(encoding="utf-8")
        qa_runtime_checklist = QA_RUNTIME_CHECKLIST.read_text(encoding="utf-8")
        engineering_context = ENGINEERING_CONTEXT.read_text(encoding="utf-8")
        dev_source_agents = DEV_SOURCE_AGENTS.read_text(encoding="utf-8")
        prod_source_agents = PROD_SOURCE_AGENTS.read_text(encoding="utf-8")
        pr_template = PR_TEMPLATE.read_text(encoding="utf-8")
        root_agents = ROOT_AGENTS.read_text(encoding="utf-8")
        app_agents = APP_AGENTS.read_text(encoding="utf-8")
        context_bundle_protocol = CONTEXT_BUNDLE_PROTOCOL.read_text(encoding="utf-8")
        recent_decisions = RECENT_DECISIONS.read_text(encoding="utf-8")
        discord_notifier = DISCORD_NOTIFIER.read_text(encoding="utf-8")
        functions_index = FUNCTIONS_INDEX.read_text(encoding="utf-8")

        self.assertIn("scripts/setup-play-deploy-secrets.sh`는 Android/Play 배포용 helper", runbook)
        self.assertIn("scripts/check-play-deploy-secret-contract.sh", runbook)
        self.assertIn("STOPIT_SKIP_GH_SECRET_LIST=1 scripts/check-play-deploy-secret-contract.sh", runbook)
        self.assertIn("scripts/setup-discord-deploy-secrets.sh", runbook)
        self.assertIn("Firebase Functions **양쪽에 모두 필요**", runbook)
        self.assertIn("GITHUB_ACTIONS_DISPATCH_TOKEN", runbook)
        self.assertIn("scripts/notify-discord-deploy.py", runbook)
        self.assertIn("functions/src/index.ts", runbook)

        self.assertIn(
            "GitHub Actions repo secrets for Android/Play build/upload and Discord deploy notifications are documented in `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`.",
            functions_readme,
        )
        self.assertIn(
            "This `functions/` setup only covers Firebase Functions secrets used by Crashlytics alerts and production-promotion interactions.",
            functions_readme,
        )
        self.assertIn("../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", functions_agents)
        self.assertIn("../../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", functions_src_agents)
        self.assertIn("DISCORD_PUBLIC_KEY", functions_src_agents)
        self.assertIn("GITHUB_ACTIONS_DISPATCH_TOKEN", functions_src_agents)
        self.assertIn("python3 -m unittest scripts.tests.test_play_deploy_secret_contract_runbook -v", functions_src_agents)
        self.assertIn("PLAY_DEPLOY_SECRETS_RUNBOOK.md", docs_agents)
        self.assertIn("../PLAY_DEPLOY_SECRETS_RUNBOOK.md", stopit_context_readme)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", agent_roles)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", qa_runtime_checklist)
        self.assertIn("Android CI / Release QA에서는 `app/src/dev`+`app/src/prod` 둘 다에", qa_runtime_checklist)
        self.assertIn("Release Build / Play Deploy에서는 `app/src/prod`에만", qa_runtime_checklist)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", engineering_context)
        self.assertIn("scripts/setup-play-deploy-secrets.sh` vs `scripts/setup-discord-deploy-secrets.sh`", engineering_context)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", dev_source_agents)
        self.assertIn("workflow-specific restore matrix", dev_source_agents)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", prod_source_agents)
        self.assertIn("Android CI/Release QA may restore the same secret to both dev and prod", prod_source_agents)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", pr_template)
        self.assertIn("workflow secret restore", pr_template)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", root_agents)
        self.assertIn("GitHub Actions repo secrets, workflow별 `GOOGLE_SERVICES_JSON` restore matrix, Firebase Functions secret 경계", root_agents)
        self.assertIn("../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", app_agents)
        self.assertIn("Android CI / Release QA / Release Build / Play Deploy의 restore 차이", app_agents)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", context_bundle_protocol)
        self.assertIn("secret ownership / helper scope / Firebase Functions 경계", context_bundle_protocol)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", recent_decisions)
        self.assertIn("helper scope / `GOOGLE_SERVICES_JSON` restore matrix / Firebase Functions promotion secret boundary", recent_decisions)
        self.assertIn("docs/PLAY_DEPLOYMENT.md", runbook)
        self.assertIn("docs/GIT_WORKFLOW.md", runbook)
        self.assertIn("docs/RELEASE_CHECKLIST.md", runbook)
        self.assertIn("docs/ops/stopit/release-context.md", runbook)
        self.assertIn("Android/Play build-upload secrets만", runbook)
        self.assertIn("workflow별 restore matrix", runbook)
        self.assertIn("Play Deploy workflow는 `DISCORD_BOT_TOKEN`, `DISCORD_DEPLOY_CHANNEL_ID`를 `scripts/notify-discord-deploy.py`에만 전달한다.", runbook)
        self.assertIn(
            "Firebase Functions는 `functions/src/index.ts`에서 `DISCORD_PUBLIC_KEY`, `DISCORD_DEPLOY_CHANNEL_ID`, `DISCORD_DEPLOY_ALLOWED_ROLE_IDS`, `DISCORD_DEPLOY_ALLOWED_USER_IDS`, `GITHUB_ACTIONS_DISPATCH_TOKEN`을 별도 secret으로 정의한다.",
            runbook,
        )
        self.assertIn("python3 -m unittest scripts.tests.test_check_play_deploy_secret_contract -v", runbook)

        self.assertIn('env("DISCORD_BOT_TOKEN")', discord_notifier)
        self.assertIn('env("DISCORD_DEPLOY_CHANNEL_ID")', discord_notifier)
        self.assertIn('defineSecret("DISCORD_PUBLIC_KEY")', functions_index)
        self.assertIn('defineSecret("DISCORD_DEPLOY_CHANNEL_ID")', functions_index)
        self.assertIn('defineSecret("DISCORD_DEPLOY_ALLOWED_ROLE_IDS")', functions_index)
        self.assertIn('defineSecret("DISCORD_DEPLOY_ALLOWED_USER_IDS")', functions_index)
        self.assertIn('defineSecret("GITHUB_ACTIONS_DISPATCH_TOKEN")', functions_index)


if __name__ == "__main__":
    unittest.main()
