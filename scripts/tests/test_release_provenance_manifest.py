import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "scripts" / "release_provenance_manifest.py"


class ReleaseProvenanceManifestTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        (self.root / "app/build/outputs/bundle/prodRelease").mkdir(parents=True)
        (self.root / "app").mkdir(exist_ok=True)
        (self.root / "app/build.gradle.kts").write_text(
            """
android {
    defaultConfig {
        versionCode = 42
        versionName = "1.2.3"
    }
}
""".strip()
            + "\n",
            encoding="utf-8",
        )
        self.aab = self.root / "app/build/outputs/bundle/prodRelease/app-prod-release.aab"
        self.aab.write_bytes(b"fake signed aab bytes")
        self.manifest = self.root / "app/build/outputs/bundle/prodRelease/release-provenance.json"
        self.env = os.environ.copy()
        self.env.update(
            {
                "GITHUB_SHA": "abc123",
                "GITHUB_REF": "refs/tags/v1.2.3",
                "GITHUB_REF_NAME": "v1.2.3",
                "GITHUB_REF_TYPE": "tag",
                "GITHUB_WORKFLOW": "Android Play Deploy",
                "GITHUB_RUN_ID": "123456",
                "GITHUB_RUN_ATTEMPT": "2",
                "GITHUB_SERVER_URL": "https://github.com",
                "GITHUB_REPOSITORY": "parkuiery/Stopit-Android",
            }
        )

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, *args, check=True):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), *args],
            cwd=self.root,
            env=self.env,
            text=True,
            capture_output=True,
            check=False,
        )
        if check and result.returncode != 0:
            self.fail(f"command failed: {result.returncode}\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}")
        return result

    def generate(self, **overrides):
        args = [
            "generate",
            "--aab-glob",
            "app/build/outputs/bundle/prodRelease/*.aab",
            "--output",
            "app/build/outputs/bundle/prodRelease/release-provenance.json",
            "--artifact-name",
            "stopit-prod-release-signed-aab",
            "--package-name",
            "com.uiery.keep",
            "--upload-mode",
            overrides.get("upload_mode", "play-upload"),
            "--track",
            overrides.get("track", "internal"),
            "--release-status",
            overrides.get("release_status", "completed"),
            "--rollout-fraction",
            overrides.get("rollout_fraction", ""),
        ]
        return self.run_script(*args)

    def verify(self, **overrides):
        args = [
            "verify",
            "--aab-glob",
            "app/build/outputs/bundle/prodRelease/*.aab",
            "--manifest",
            "app/build/outputs/bundle/prodRelease/release-provenance.json",
            "--artifact-name",
            overrides.get("artifact_name", "stopit-prod-release-signed-aab"),
            "--package-name",
            "com.uiery.keep",
            "--upload-mode",
            overrides.get("upload_mode", "play-upload"),
            "--track",
            overrides.get("track", "internal"),
            "--release-status",
            overrides.get("release_status", "completed"),
            "--rollout-fraction",
            overrides.get("rollout_fraction", ""),
        ]
        if "expected_version_code" in overrides:
            args.extend(["--expected-version-code", str(overrides["expected_version_code"])])
        if "expected_git_sha" in overrides:
            args.extend(["--expected-git-sha", overrides["expected_git_sha"]])
        if "expected_git_ref" in overrides:
            args.extend(["--expected-git-ref", overrides["expected_git_ref"]])
        if "expected_git_ref_name" in overrides:
            args.extend(["--expected-git-ref-name", overrides["expected_git_ref_name"]])
        return self.run_script(*args, check=overrides.get("check", True))

    def compare(self, existing_manifest, current_manifest=None, check=True):
        return self.run_script(
            "compare",
            "--existing-manifest",
            str(existing_manifest),
            "--current-manifest",
            str(current_manifest or self.manifest),
            check=check,
        )

    def test_generate_writes_secret_free_manifest_with_artifact_version_and_run_metadata(self):
        self.generate()

        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        self.assertEqual(manifest["schema_version"], 1)
        self.assertEqual(manifest["package_name"], "com.uiery.keep")
        self.assertEqual(manifest["artifact_name"], "stopit-prod-release-signed-aab")
        self.assertEqual(manifest["artifact"]["path"], self.aab.relative_to(self.root).as_posix())
        self.assertEqual(manifest["artifact"]["file_name"], "app-prod-release.aab")
        self.assertEqual(manifest["artifact"]["size_bytes"], len(b"fake signed aab bytes"))
        self.assertEqual(
            manifest["artifact"]["sha256"],
            hashlib.sha256(b"fake signed aab bytes").hexdigest(),
        )
        self.assertEqual(manifest["android"], {"variant": "prodRelease", "version_name": "1.2.3", "version_code": 42})
        self.assertEqual(manifest["git"]["sha"], "abc123")
        self.assertEqual(manifest["github_actions"]["run_url"], "https://github.com/parkuiery/Stopit-Android/actions/runs/123456")
        self.assertEqual(
            manifest["play"],
            {
                "upload_mode": "play-upload",
                "track": "internal",
                "release_status": "completed",
                "rollout_fraction": None,
            },
        )
        rendered = json.dumps(manifest)
        self.assertNotIn("ANDROID_KEYSTORE", rendered)
        self.assertNotIn("GOOGLE_PLAY_SERVICE_ACCOUNT", rendered)
        self.assertNotIn("google-services.json", rendered)

    def test_verify_accepts_matching_manifest(self):
        self.generate(track="beta", release_status="inProgress", rollout_fraction="0.25")
        self.verify(track="beta", release_status="inProgress", rollout_fraction="0.25")

    def test_verify_accepts_recursive_aab_glob_for_downloaded_artifact_tree(self):
        self.generate()
        nested_dir = self.root / "downloaded" / "app" / "build" / "outputs" / "bundle" / "prodRelease"
        nested_dir.mkdir(parents=True)
        nested_aab = nested_dir / "app-prod-release.aab"
        nested_manifest = nested_dir / "release-provenance.json"
        nested_aab.write_bytes(self.aab.read_bytes())
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        nested_manifest.write_text(json.dumps(manifest), encoding="utf-8")

        result = self.run_script(
            "verify",
            "--aab-glob",
            str(self.root / "downloaded" / "**" / "*.aab"),
            "--manifest",
            str(nested_manifest),
            "--artifact-name",
            "stopit-prod-release-signed-aab",
            "--package-name",
            "com.uiery.keep",
            "--upload-mode",
            "play-upload",
            "--track",
            "internal",
            "--release-status",
            "completed",
            "--rollout-fraction",
            "",
            "--allow-artifact-path-relocation",
        )

        self.assertIn("Verified release provenance manifest", result.stdout)

    def test_verify_metadata_only_accepts_durable_manifest_without_downloaded_aab(self):
        self.generate()
        self.aab.unlink()

        result = self.run_script(
            "verify",
            "--aab-glob",
            "app/build/outputs/bundle/prodRelease/*.aab",
            "--manifest",
            "app/build/outputs/bundle/prodRelease/release-provenance.json",
            "--artifact-name",
            "stopit-prod-release-signed-aab",
            "--package-name",
            "com.uiery.keep",
            "--upload-mode",
            "play-upload",
            "--track",
            "internal",
            "--release-status",
            "completed",
            "--rollout-fraction",
            "",
            "--expected-version-code",
            "42",
            "--expected-git-sha",
            "abc123",
            "--expected-git-ref",
            "refs/tags/v1.2.3",
            "--expected-git-ref-name",
            "v1.2.3",
            "--metadata-only",
        )

        self.assertIn("Verified release provenance manifest metadata", result.stdout)

    def test_verify_metadata_only_rejects_missing_artifact_checksum_metadata(self):
        self.generate()
        self.aab.unlink()
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        manifest["artifact"].pop("sha256")
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

        result = self.run_script(
            "verify",
            "--aab-glob",
            "app/build/outputs/bundle/prodRelease/*.aab",
            "--manifest",
            "app/build/outputs/bundle/prodRelease/release-provenance.json",
            "--artifact-name",
            "stopit-prod-release-signed-aab",
            "--package-name",
            "com.uiery.keep",
            "--upload-mode",
            "play-upload",
            "--track",
            "internal",
            "--release-status",
            "completed",
            "--rollout-fraction",
            "",
            "--metadata-only",
            check=False,
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("artifact.sha256 is required for metadata-only verification", result.stderr)

    def test_verify_rejects_checksum_drift(self):
        self.generate()
        self.aab.write_bytes(b"changed bytes")

        result = self.verify(check=False)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("artifact.sha256 mismatch", result.stderr)

    def test_verify_rejects_version_drift(self):
        self.generate()
        (self.root / "app/build.gradle.kts").write_text(
            'android { defaultConfig { versionCode = 43\nversionName = "1.2.4" } }\n',
            encoding="utf-8",
        )

        result = self.verify(check=False)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("android.version_name mismatch", result.stderr)

    def test_verify_production_promotion_rejects_version_code_sha_ref_or_tag_mismatch(self):
        self.generate()

        result = self.verify(expected_version_code=43, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("android.version_code mismatch", result.stderr)

        result = self.verify(expected_git_sha="different-sha", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("git.sha mismatch", result.stderr)

        result = self.verify(expected_git_ref="refs/tags/v9.9.9", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("git.ref mismatch", result.stderr)

        result = self.verify(expected_git_ref_name="v9.9.9", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("git.ref_name mismatch", result.stderr)

    def test_verify_production_promotion_accepts_matching_tag_identity(self):
        self.generate()

        self.verify(
            expected_version_code=42,
            expected_git_sha="abc123",
            expected_git_ref="refs/tags/v1.2.3",
            expected_git_ref_name="v1.2.3",
        )

    def test_verify_rejects_artifact_name_drift(self):
        self.generate()
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        manifest["artifact_name"] = "tampered-artifact"
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

        result = self.verify(check=False)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("artifact_name mismatch", result.stderr)

    def test_verify_rejects_git_and_github_actions_metadata_drift(self):
        self.generate()
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        manifest["git"]["sha"] = "evil"
        manifest["github_actions"]["run_url"] = None
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

        result = self.verify(check=False)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("git.sha mismatch", result.stderr)

    def test_verify_rejects_missing_github_actions_run_identity(self):
        self.generate()
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        manifest["github_actions"].pop("run_id")
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

        result = self.verify(check=False)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("github_actions.run_id mismatch", result.stderr)

    def test_compare_accepts_same_internal_identity_with_new_run_instance(self):
        self.generate()
        existing = self.root / "existing-release-provenance.json"
        existing.write_text(self.manifest.read_text(encoding="utf-8"), encoding="utf-8")

        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        manifest["github_actions"]["run_id"] = "789012"
        manifest["github_actions"]["run_attempt"] = "1"
        manifest["github_actions"]["run_url"] = "https://github.com/parkuiery/Stopit-Android/actions/runs/789012"
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

        result = self.compare(existing)

        self.assertIn("Verified durable internal provenance fallback identity before clobber", result.stdout)

    def test_compare_rejects_track_status_git_or_artifact_identity_drift(self):
        self.generate()
        existing = self.root / "existing-release-provenance.json"
        existing.write_text(self.manifest.read_text(encoding="utf-8"), encoding="utf-8")

        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        manifest["play"]["track"] = "alpha"
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

        result = self.compare(existing, check=False)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("play.track mismatch", result.stderr)

        manifest["play"]["track"] = "internal"
        manifest["artifact"]["sha256"] = "0" * 64
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")
        result = self.compare(existing, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("artifact.sha256 mismatch", result.stderr)

    def test_compare_rejects_missing_existing_run_identity(self):
        self.generate()
        existing_manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        existing_manifest["github_actions"].pop("run_url")
        existing = self.root / "existing-release-provenance.json"
        existing.write_text(json.dumps(existing_manifest), encoding="utf-8")

        result = self.compare(existing, check=False)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("existing.github_actions.run_url is required", result.stderr)

    def test_generate_rejects_missing_or_ambiguous_aab_glob(self):
        self.aab.unlink()
        result = self.generate_result(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Expected exactly one AAB", result.stderr)

        self.aab.write_bytes(b"one")
        (self.root / "app/build/outputs/bundle/prodRelease/second.aab").write_bytes(b"two")
        result = self.generate_result(check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Expected exactly one AAB", result.stderr)

    def generate_result(self, check=True):
        return self.run_script(
            "generate",
            "--aab-glob",
            "app/build/outputs/bundle/prodRelease/*.aab",
            "--output",
            "app/build/outputs/bundle/prodRelease/release-provenance.json",
            "--artifact-name",
            "stopit-prod-release-signed-aab",
            "--package-name",
            "com.uiery.keep",
            "--upload-mode",
            "none",
            "--track",
            "",
            "--release-status",
            "",
            "--rollout-fraction",
            "",
            check=check,
        )


if __name__ == "__main__":
    unittest.main()
