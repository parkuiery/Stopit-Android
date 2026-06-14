import pathlib
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_BUILD = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOY = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
PLAY_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
GIT_WORKFLOW = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
PLAY_SECRETS_RUNBOOK = REPO_ROOT / "docs" / "PLAY_DEPLOY_SECRETS_RUNBOOK.md"
PINNED_UPLOAD_GOOGLE_PLAY_SHA = "eb49699984a39f23558439581660aa6f088acfd6"
FLOATING_UPLOAD_GOOGLE_PLAY_REF = "r0adkll/upload-google-play@v1"
PINNED_UPLOAD_GOOGLE_PLAY_REF = f"r0adkll/upload-google-play@{PINNED_UPLOAD_GOOGLE_PLAY_SHA}"


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

    def test_play_deploy_upload_google_play_action_is_sha_pinned(self):
        workflow = PLAY_DEPLOY.read_text(encoding="utf-8")
        upload_step = step_block(workflow, "Upload to Google Play")

        self.assertIn(PINNED_UPLOAD_GOOGLE_PLAY_REF, upload_step)
        self.assertNotIn(FLOATING_UPLOAD_GOOGLE_PLAY_REF, upload_step)
        self.assertIn("Release-critical deploy action", upload_step)
        self.assertIn("reviewed provenance PR", upload_step)

        for line in workflow.splitlines():
            stripped = line.strip()
            if stripped.startswith("uses: r0adkll/upload-google-play@"):
                ref = stripped.split("@", 1)[1]
                self.assertRegex(ref, r"^[0-9a-f]{40}$")
                self.assertEqual(PINNED_UPLOAD_GOOGLE_PLAY_SHA, ref)

    def test_internal_completed_tag_deploy_publishes_durable_release_provenance_fallback(self):
        workflow = PLAY_DEPLOY.read_text(encoding="utf-8")
        self.assertIn("contents: write", workflow)
        self.assertLess(
            workflow.index("- name: Verify Play upload provenance manifest"),
            workflow.index("- name: Upload to Google Play"),
        )
        self.assertLess(
            workflow.index("- name: Upload to Google Play"),
            workflow.index("- name: Publish durable provenance fallback to GitHub Release"),
        )

        publish_step = step_block(workflow, "Publish durable provenance fallback to GitHub Release")
        self.assertIn("if: env.DEPLOY_TRACK == 'internal' && env.RELEASE_STATUS == 'completed'", publish_step)
        self.assertNotIn("if: env.DEPLOY_TRACK != 'production'", publish_step)
        self.assertIn("GH_TOKEN: ${{ github.token }}", publish_step)
        self.assertIn("release-provenance.json", publish_step)
        self.assertIn("gh release view \"$GITHUB_REF_NAME\"", publish_step)
        self.assertIn("gh release create \"$GITHUB_REF_NAME\"", publish_step)
        self.assertIn("python3 scripts/release_provenance_manifest.py compare", publish_step)
        self.assertIn("--existing-manifest \"$existing_fallback_manifest\"", publish_step)
        self.assertIn("--current-manifest app/build/outputs/bundle/prodRelease/release-provenance.json", publish_step)
        self.assertLess(
            publish_step.index("python3 scripts/release_provenance_manifest.py compare"),
            publish_step.index("gh release upload \"$GITHUB_REF_NAME\" app/build/outputs/bundle/prodRelease/release-provenance.json --clobber"),
        )
        self.assertIn("Existing durable fallback identity mismatch; refusing to clobber", publish_step)
        self.assertIn("Existing durable internal fallback identity matches this internal completed manifest; clobber is safe", publish_step)
        self.assertIn("gh release upload \"$GITHUB_REF_NAME\" app/build/outputs/bundle/prodRelease/release-provenance.json --clobber", publish_step)
        self.assertIn("post_upload_failure()", publish_step)
        self.assertIn("Post-upload durable internal provenance publish failure", publish_step)
        self.assertIn("evidence-publish failure, not as proof that the Play upload failed", publish_step)
        self.assertIn("Do not blindly re-upload the same versionCode", publish_step)
        self.assertIn("same-tag internal completed Play Deploy", publish_step)
        self.assertIn("alpha/beta deploys must not clobber the internal durable fallback", publish_step)
        self.assertIn("|| post_upload_failure \"GitHub Release create failed", publish_step)
        self.assertIn("|| post_upload_failure \"GitHub Release upload failed", publish_step)

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
        self.assertIn("try_candidate_run()", download_step)
        self.assertIn("for event_name in push workflow_dispatch", download_step)
        self.assertIn("--event \"$event_name\"", download_step)
        self.assertIn("PRIOR_PROVENANCE_RUN_EVENT=$candidate_event", download_step)
        self.assertIn("Selected prior internal Play Deploy run", download_step)
        self.assertIn("after manifest track/status verification", download_step)
        self.assertIn("gh run download", download_step)
        self.assertIn("--name stopit-prod-release-signed-aab", download_step)
        self.assertIn("python3 scripts/release_provenance_manifest.py verify", download_step)
        self.assertIn("--track internal", download_step)
        self.assertIn("--release-status completed", download_step)
        self.assertIn("--prior-run", download_step)
        self.assertIn("prior internal track mismatch or provenance mismatch", download_step)
        self.assertIn("alpha/beta/production or mismatched candidates are not valid prior internal evidence", download_step)
        self.assertIn("with track=internal and release_status=completed", download_step)
        self.assertIn("gh release download \"$GITHUB_REF_NAME\"", download_step)
        self.assertIn("--pattern release-provenance.json", download_step)
        self.assertIn("release-provenance.json", download_step)
        self.assertIn("PROVENANCE_VERIFY_MODE=metadata-only", download_step)
        self.assertIn("artifact expired/missing", download_step)
        self.assertIn("durable fallback missing", download_step)
        self.assertIn("rerun a non-production Play Deploy with track=internal", download_step)

        verify_step = step_block(workflow, "Verify prior internal provenance before production promotion")
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", verify_step)
        self.assertIn("python3 scripts/release_provenance_manifest.py \"${args[@]}\"", verify_step)
        self.assertIn("args=(", verify_step)
        self.assertIn("verify", verify_step)
        self.assertIn("--metadata-only", verify_step)
        self.assertIn("--track internal", verify_step)
        self.assertIn("--release-status completed", verify_step)
        self.assertIn("--rollout-fraction ''", verify_step)
        self.assertIn("--prior-run", verify_step)
        self.assertIn('"$VERSION_CODE"', verify_step)
        self.assertIn('"$GITHUB_SHA"', verify_step)
        self.assertIn('"$GITHUB_REF"', verify_step)
        self.assertIn('"$GITHUB_REF_NAME"', verify_step)

    def test_docs_define_production_promotion_provenance_boundary(self):
        docs = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (PLAY_DOC, RELEASE_CHECKLIST, RELEASE_CONTEXT, GIT_WORKFLOW, PLAY_SECRETS_RUNBOOK)
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
            "tag push artifact",
            "manual deploy artifact",
            "workflow_dispatch",
            "track=internal",
            "release_status=completed",
            "prior internal track mismatch",
            "alpha",
            "beta",
            "before `Upload signed AAB artifact`",
            "30-day evidence surface",
            "durable fallback",
            "artifact expired/missing",
            "durable fallback missing",
            "post-upload durable internal provenance publish failure",
            "evidence-publish failure",
            "do not blindly re-upload the same `versionCode`",
            "internal completed Play Deploy",
            "must not clobber",
            "scripts/release_provenance_manifest.py compare",
            "gh release upload --clobber",
            "existing durable fallback identity mismatch",
            "refuse to clobber",
            "run_id",
            "run_attempt",
            "run_url",
            "provenance mismatch",
            "r0adkll/upload-google-play@eb49699984a39f23558439581660aa6f088acfd6",
            "floating major tag",
            "reviewed release-provenance PR",
            "repo-owned promotion helper",
            "same-run self-verification",
            "cross-run",
            "prior-run identity semantics",
            "current production-promotion run",
            "current-run metadata drift",
        ):
            self.assertIn(required, docs)

    def test_play_deployment_retention_boundary_names_prior_internal_selection_issues(self):
        play_doc = PLAY_DOC.read_text(encoding="utf-8")
        retention_section = play_doc.split("## Production promotion provenance retention / recovery", 1)[1]

        self.assertIn("#680/#743/#819/#830/#850", retention_section)
        self.assertIn("prior internal track mismatch", retention_section)
        self.assertIn("track=internal", retention_section)
        self.assertIn("release_status=completed", retention_section)
        self.assertIn("same-tag GitHub Release asset", retention_section)
        self.assertIn("durable fallback", retention_section)

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
