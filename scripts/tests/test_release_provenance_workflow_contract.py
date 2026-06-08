import pathlib
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_BUILD = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOY = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
PLAY_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


def step_block(text: str, step_name: str) -> str:
    marker = f"- name: {step_name}"
    start = text.index(marker)
    next_step = text.find("\n      - name:", start + len(marker))
    if next_step == -1:
        return text[start:]
    return text[start:next_step]


class ReleaseProvenanceWorkflowContractTest(unittest.TestCase):
    def test_release_build_generates_verifies_and_uploads_provenance_with_signed_aab(self):
        workflow = RELEASE_BUILD.read_text(encoding="utf-8")
        self.assertLess(
            workflow.index("- name: Build signed prod release bundle"),
            workflow.index("- name: Generate release provenance manifest"),
        )
        self.assertLess(
            workflow.index("- name: Generate release provenance manifest"),
            workflow.index("- name: Verify release provenance manifest"),
        )
        self.assertLess(
            workflow.index("- name: Verify release provenance manifest"),
            workflow.index("- name: Upload signed AAB artifact"),
        )

        generate_step = step_block(workflow, "Generate release provenance manifest")
        self.assertIn("python3 scripts/release_provenance_manifest.py generate", generate_step)
        self.assertIn("--aab-glob 'app/build/outputs/bundle/prodRelease/*.aab'", generate_step)
        self.assertIn("--output app/build/outputs/bundle/prodRelease/release-provenance.json", generate_step)
        self.assertIn("--artifact-name stopit-prod-release-signed-aab", generate_step)
        self.assertIn("--upload-mode none", generate_step)

        verify_step = step_block(workflow, "Verify release provenance manifest")
        self.assertIn("python3 scripts/release_provenance_manifest.py verify", verify_step)
        self.assertIn("--aab-glob 'app/build/outputs/bundle/prodRelease/*.aab'", verify_step)
        self.assertIn("--manifest app/build/outputs/bundle/prodRelease/release-provenance.json", verify_step)
        self.assertIn("--artifact-name stopit-prod-release-signed-aab", verify_step)
        self.assertIn("--package-name com.uiery.keep", verify_step)
        self.assertIn("--upload-mode none", verify_step)
        self.assertIn("--track ''", verify_step)
        self.assertIn("--release-status ''", verify_step)
        self.assertIn("--rollout-fraction ''", verify_step)

        upload_step = step_block(workflow, "Upload signed AAB artifact")
        self.assertIn("app/build/outputs/bundle/prodRelease/*.aab", upload_step)
        self.assertIn("app/build/outputs/bundle/prodRelease/release-provenance.json", upload_step)

    def test_play_deploy_generates_verifies_and_uploads_provenance_before_google_play(self):
        workflow = PLAY_DEPLOY.read_text(encoding="utf-8")
        self.assertLess(
            workflow.index("- name: Build signed prod release bundle"),
            workflow.index("- name: Generate Play upload provenance manifest"),
        )
        self.assertLess(
            workflow.index("- name: Generate Play upload provenance manifest"),
            workflow.index("- name: Verify Play upload provenance manifest"),
        )
        self.assertLess(
            workflow.index("- name: Verify Play upload provenance manifest"),
            workflow.index("- name: Upload signed AAB artifact"),
        )
        self.assertLess(
            workflow.index("- name: Upload signed AAB artifact"),
            workflow.index("- name: Upload to Google Play"),
        )

        generate_step = step_block(workflow, "Generate Play upload provenance manifest")
        verify_step = step_block(workflow, "Verify Play upload provenance manifest")
        for block in (generate_step, verify_step):
            self.assertIn("if: env.DEPLOY_TRACK != 'production'", block)
            self.assertIn("release-provenance.json", block)
            self.assertIn("stopit-prod-release-signed-aab", block)
            self.assertIn('"$DEPLOY_TRACK"', block)
            self.assertIn('"$RELEASE_STATUS"', block)
            self.assertIn('"$ROLLOUT_FRACTION"', block)
        self.assertIn("python3 scripts/release_provenance_manifest.py generate", generate_step)
        self.assertIn("python3 scripts/release_provenance_manifest.py verify", verify_step)

        upload_step = step_block(workflow, "Upload signed AAB artifact")
        self.assertIn("app/build/outputs/bundle/prodRelease/*.aab", upload_step)
        self.assertIn("app/build/outputs/bundle/prodRelease/release-provenance.json", upload_step)

    def test_production_promotion_downloads_and_verifies_prior_internal_provenance_before_secrets(self):
        workflow = PLAY_DEPLOY.read_text(encoding="utf-8")
        self.assertIn("actions: read", workflow)
        self.assertLess(
            workflow.index("- name: Resolve production promotion versionCode from selected tag"),
            workflow.index("- name: Download prior internal provenance artifact"),
        )
        self.assertLess(
            workflow.index("- name: Download prior internal provenance artifact"),
            workflow.index("- name: Verify prior internal provenance before production promotion"),
        )
        self.assertLess(
            workflow.index("- name: Verify prior internal provenance before production promotion"),
            workflow.index("- name: Validate production promotion secrets"),
        )
        self.assertLess(
            workflow.index("- name: Verify prior internal provenance before production promotion"),
            workflow.index("- name: Decode Play credentials for production promotion"),
        )
        self.assertLess(
            workflow.index("- name: Verify prior internal provenance before production promotion"),
            workflow.index("- name: Promote internal release to production"),
        )

        download_step = step_block(workflow, "Download prior internal provenance artifact")
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", download_step)
        self.assertIn("GH_TOKEN: ${{ github.token }}", download_step)
        self.assertIn("gh run list", download_step)
        self.assertIn("--event push", download_step)
        self.assertIn("gh run download", download_step)
        self.assertIn("--name stopit-prod-release-signed-aab", download_step)
        self.assertIn("release-provenance.json", download_step)

        verify_step = step_block(workflow, "Verify prior internal provenance before production promotion")
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", verify_step)
        self.assertIn("python3 scripts/release_provenance_manifest.py verify", verify_step)
        self.assertIn("--track internal", verify_step)
        self.assertIn("--release-status completed", verify_step)
        self.assertIn("--rollout-fraction ''", verify_step)
        self.assertIn('"$VERSION_CODE"', verify_step)
        self.assertIn('"$GITHUB_SHA"', verify_step)
        self.assertIn('"$GITHUB_REF"', verify_step)
        self.assertIn('"$GITHUB_REF_NAME"', verify_step)

    def test_docs_define_production_promotion_provenance_boundary(self):
        docs = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (PLAY_DOC, RELEASE_CHECKLIST, RELEASE_CONTEXT)
        )
        for required in (
            "release-provenance.json",
            "artifact_name",
            "run_id",
            "sha256",
            "versionCode",
            "workflow run URL",
            "production promotion",
            "prior non-production",
            "internal release",
            "fail-fast before production secrets",
            "prior internal provenance gate",
            "before `Upload signed AAB artifact`",
        ):
            self.assertIn(required, docs)

    def test_docs_keep_provenance_manifest_secret_free(self):
        docs = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (PLAY_DOC, RELEASE_CONTEXT)
        )
        self.assertIn("does not include secrets", docs)
        self.assertIn("keystore", docs)
        self.assertIn("service account JSON", docs)

    def test_docs_define_release_readiness_quick_preflight_boundary(self):
        git_workflow = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
        docs = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (git_workflow, PLAY_DOC, RELEASE_CHECKLIST)
        )
        for required in (
            "scripts/check-release-readiness.sh",
            "quick preflight",
            ":app:testProdReleaseUnitTest",
            ":app:lintProdRelease",
            "scripts/verify_lint_registry.py",
            ":app:bundleProdRelease --dry-run",
            "Signed AAB provenance",
            "Android Release Build workflow",
        ):
            self.assertIn(required, docs)


if __name__ == "__main__":
    unittest.main()
