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
        return self.run_script(*args, check=overrides.get("check", True))

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
