import pathlib
import stat
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "PLAY_DEPLOY_SECRETS_RUNBOOK.md"
ANDROID_CI = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_QA = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
RELEASE_BUILD = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOY = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
VERSION_GUARD = REPO_ROOT / ".github" / "workflows" / "version-guard.yml"
PLAY_DEPLOYMENT = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
GIT_WORKFLOW = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
FUNCTIONS_README = REPO_ROOT / "functions" / "README.md"
FUNCTIONS_AGENTS = REPO_ROOT / "functions" / "AGENTS.md"
FUNCTIONS_SRC_AGENTS = REPO_ROOT / "functions" / "src" / "AGENTS.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
STOPIT_CONTEXT_README = REPO_ROOT / "docs" / "ops" / "stopit" / "README.md"
AGENT_ROLES = REPO_ROOT / "docs" / "ops" / "stopit" / "agent-roles.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
ENGINEERING_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "engineering-context.md"
APP_SOURCE_AGENTS = REPO_ROOT / "app" / "src" / "AGENTS.md"
DEV_SOURCE_AGENTS = REPO_ROOT / "app" / "src" / "dev" / "AGENTS.md"
PROD_SOURCE_AGENTS = REPO_ROOT / "app" / "src" / "prod" / "AGENTS.md"
PR_TEMPLATE = REPO_ROOT / ".github" / "pull_request_template.md"
ROOT_AGENTS = REPO_ROOT / "AGENTS.md"
APP_AGENTS = REPO_ROOT / "app" / "AGENTS.md"
CONTEXT_BUNDLE_PROTOCOL = REPO_ROOT / "docs" / "ops" / "stopit" / "context-bundle-protocol.md"
RECENT_DECISIONS = REPO_ROOT / "docs" / "ops" / "stopit" / "recent-decisions.md"
DISCORD_NOTIFIER = REPO_ROOT / "scripts" / "notify-discord-deploy.py"
FUNCTIONS_INDEX = REPO_ROOT / "functions" / "src" / "index.ts"
CHECK_SECRET_CONTRACT = REPO_ROOT / "scripts" / "check-play-deploy-secret-contract.sh"
SETUP_PLAY_SECRETS = REPO_ROOT / "scripts" / "setup-play-deploy-secrets.sh"
SETUP_DISCORD_SECRETS = REPO_ROOT / "scripts" / "setup-discord-deploy-secrets.sh"


class PlayDeploySecretContractRunbookTest(unittest.TestCase):
    def test_runbook_entrypoint_scripts_are_executable_for_direct_invocation_examples(self):
        for script in (CHECK_SECRET_CONTRACT, SETUP_PLAY_SECRETS, SETUP_DISCORD_SECRETS):
            self.assertTrue(script.exists(), f"missing script: {script}")
            self.assertTrue(
                script.stat().st_mode & stat.S_IXUSR,
                f"runbook-documented direct invocation requires executable bit: {script}",
            )

    def test_runbook_records_current_workflow_secret_restore_matrix(self):
        runbook = RUNBOOK.read_text(encoding="utf-8")
        android_ci = ANDROID_CI.read_text(encoding="utf-8")
        release_qa = RELEASE_QA.read_text(encoding="utf-8")
        release_build = RELEASE_BUILD.read_text(encoding="utf-8")
        play_deploy = PLAY_DEPLOY.read_text(encoding="utf-8")
        version_guard = VERSION_GUARD.read_text(encoding="utf-8")

        self.assertIn(
            "| `android-ci.yml` | `GOOGLE_SERVICES_JSON_DEV` -> `app/src/dev/google-services.json`, `GOOGLE_SERVICES_JSON` -> `app/src/prod/google-services.json` | 없음 |",
            runbook,
        )
        self.assertIn(
            "| `release-qa.yml` | `GOOGLE_SERVICES_JSON_DEV` -> `app/src/dev/google-services.json`, `GOOGLE_SERVICES_JSON` -> `app/src/prod/google-services.json` | 없음 |",
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
            self.assertIn("printf '%s' \"$GOOGLE_SERVICES_JSON_DEV\" > app/src/dev/google-services.json", workflow)
            self.assertIn("printf '%s' \"$GOOGLE_SERVICES_JSON\" > app/src/prod/google-services.json", workflow)

        for workflow in (release_build, play_deploy):
            self.assertIn("printf '%s' \"$GOOGLE_SERVICES_JSON\" > app/src/prod/google-services.json", workflow)
            self.assertNotIn("app/src/dev/google-services.json", workflow)

        self.assertIn("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", version_guard)
        self.assertIn("DISCORD_BOT_TOKEN", play_deploy)
        self.assertIn("DISCORD_DEPLOY_CHANNEL_ID", play_deploy)

    def test_runbook_and_functions_readme_keep_secret_ownership_boundary_explicit(self):
        runbook = RUNBOOK.read_text(encoding="utf-8")
        play_deployment = PLAY_DEPLOYMENT.read_text(encoding="utf-8")
        git_workflow = GIT_WORKFLOW.read_text(encoding="utf-8")
        release_checklist = RELEASE_CHECKLIST.read_text(encoding="utf-8")
        functions_readme = FUNCTIONS_README.read_text(encoding="utf-8")
        functions_agents = FUNCTIONS_AGENTS.read_text(encoding="utf-8")
        functions_src_agents = FUNCTIONS_SRC_AGENTS.read_text(encoding="utf-8")
        docs_agents = DOCS_AGENTS.read_text(encoding="utf-8")
        stopit_context_readme = STOPIT_CONTEXT_README.read_text(encoding="utf-8")
        agent_roles = AGENT_ROLES.read_text(encoding="utf-8")
        qa_runtime_checklist = QA_RUNTIME_CHECKLIST.read_text(encoding="utf-8")
        engineering_context = ENGINEERING_CONTEXT.read_text(encoding="utf-8")
        app_source_agents = APP_SOURCE_AGENTS.read_text(encoding="utf-8")
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
        self.assertIn("Android CI / Release QA에서는 `GOOGLE_SERVICES_JSON_DEV`를 `app/src/dev`에, `GOOGLE_SERVICES_JSON`를 `app/src/prod`에", qa_runtime_checklist)
        self.assertIn("Release Build / Play Deploy에서는 `app/src/prod`에만", qa_runtime_checklist)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", engineering_context)
        self.assertIn("scripts/setup-play-deploy-secrets.sh` vs `scripts/setup-discord-deploy-secrets.sh`", engineering_context)
        self.assertIn("../../docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", app_source_agents)
        self.assertIn("Android CI / Release QA는 `GOOGLE_SERVICES_JSON_DEV`를 dev에, `GOOGLE_SERVICES_JSON`를 prod에 복원", app_source_agents)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", dev_source_agents)
        self.assertIn("workflow-specific restore matrix", dev_source_agents)
        self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", prod_source_agents)
        self.assertIn("Android CI/Release QA restore `GOOGLE_SERVICES_JSON` to prod while `GOOGLE_SERVICES_JSON_DEV` owns dev", prod_source_agents)
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

        for canonical_doc in (play_deployment, git_workflow, release_checklist, engineering_context):
            self.assertIn("docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md", canonical_doc)
        self.assertIn("scripts/check-play-deploy-secret-contract.sh", play_deployment)
        self.assertIn("scripts/check-play-deploy-secret-contract.sh", release_checklist)
        self.assertIn("scripts/setup-discord-deploy-secrets.sh", play_deployment)
        self.assertIn("scripts/setup-discord-deploy-secrets.sh", git_workflow)
        self.assertIn("Android / Play build-upload secrets", play_deployment)
        self.assertIn("Android/Play build-upload secrets만", runbook)
        self.assertIn("Android CI / Release QA write `GOOGLE_SERVICES_JSON_DEV` to `app/src/dev` and `GOOGLE_SERVICES_JSON` to `app/src/prod`", play_deployment)
        self.assertIn("Release Build / non-production Play Deploy write it only to `app/src/prod`", play_deployment)
        self.assertIn("Production promotion does not restore it", play_deployment)
        self.assertIn("Firebase Functions secret", play_deployment)
        self.assertIn("DISCORD_DEPLOY_CHANNEL_ID` exists in two stores", git_workflow)
        self.assertIn("Firebase Functions secret for interaction channel verification", git_workflow)
        self.assertIn("workflow별 restore matrix", runbook)
        self.assertIn("Canonical 문서 동기화 상태", runbook)

        self.assertIn('env("DISCORD_BOT_TOKEN")', discord_notifier)
        self.assertIn('env("DISCORD_DEPLOY_CHANNEL_ID")', discord_notifier)
        self.assertIn('defineSecret("DISCORD_PUBLIC_KEY")', functions_index)
        self.assertIn('defineSecret("DISCORD_DEPLOY_CHANNEL_ID")', functions_index)
        self.assertIn('defineSecret("DISCORD_DEPLOY_ALLOWED_ROLE_IDS")', functions_index)
        self.assertIn('defineSecret("DISCORD_DEPLOY_ALLOWED_USER_IDS")', functions_index)
        self.assertIn('defineSecret("GITHUB_ACTIONS_DISPATCH_TOKEN")', functions_index)


if __name__ == "__main__":
    unittest.main()
