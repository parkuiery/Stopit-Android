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
    def test_release_build_generates_and_uploads_provenance_with_signed_aab(self):
        workflow = RELEASE_BUILD.read_text(encoding="utf-8")
        self.assertLess(
            workflow.index("- name: Build signed prod release bundle"),
            workflow.index("- name: Generate release provenance manifest"),
        )
        self.assertLess(
            workflow.index("- name: Generate release provenance manifest"),
            workflow.index("- name: Upload signed AAB artifact"),
        )

        generate_step = step_block(workflow, "Generate release provenance manifest")
        self.assertIn("python3 scripts/release_provenance_manifest.py generate", generate_step)
        self.assertIn("--aab-glob 'app/build/outputs/bundle/prodRelease/*.aab'", generate_step)
        self.assertIn("--output app/build/outputs/bundle/prodRelease/release-provenance.json", generate_step)
        self.assertIn("--artifact-name stopit-prod-release-signed-aab", generate_step)
        self.assertIn("--upload-mode none", generate_step)

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
            workflow.index("- name: Upload signed AAB artifact"),
        )
        self.assertLess(
            workflow.index("- name: Upload signed AAB artifact"),
            workflow.index("- name: Verify Play upload provenance manifest"),
        )
        self.assertLess(
            workflow.index("- name: Verify Play upload provenance manifest"),
            workflow.index("- name: Upload to Google Play"),
        )

        generate_step = step_block(workflow, "Generate Play upload provenance manifest")
        verify_step = step_block(workflow, "Verify Play upload provenance manifest")
        for block in (generate_step, verify_step):
            self.assertIn("if: env.DEPLOY_TRACK != 'production'", block)
            self.assertIn("release-provenance.json", block)
            self.assertIn('"$DEPLOY_TRACK"', block)
            self.assertIn('"$RELEASE_STATUS"', block)
            self.assertIn('"$ROLLOUT_FRACTION"', block)
        self.assertIn("python3 scripts/release_provenance_manifest.py generate", generate_step)
        self.assertIn("python3 scripts/release_provenance_manifest.py verify", verify_step)

        upload_step = step_block(workflow, "Upload signed AAB artifact")
        self.assertIn("app/build/outputs/bundle/prodRelease/*.aab", upload_step)
        self.assertIn("app/build/outputs/bundle/prodRelease/release-provenance.json", upload_step)

    def test_docs_define_production_promotion_provenance_boundary(self):
        docs = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (PLAY_DOC, RELEASE_CHECKLIST, RELEASE_CONTEXT)
        )
        for required in (
            "release-provenance.json",
            "sha256",
            "versionCode",
            "workflow run URL",
            "production promotion",
            "prior non-production",
            "internal release",
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


if __name__ == "__main__":
    unittest.main()
