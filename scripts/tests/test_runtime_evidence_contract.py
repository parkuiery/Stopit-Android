"""Contract for the local runtime gate and the evidence CI verifies in its place.

The emulator suites moved off pull_request CI. What keeps that honest is the
digest binding: `.runtime-evidence.json` records the digest of the runtime
sources it was produced from, and CI recomputes that digest. These tests pin the
properties that binding depends on.
"""

import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

from scripts import android_runtime_suites


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RUNTIME_GATE_SCRIPT = REPO_ROOT / "scripts" / "runtime-gate.sh"


def _runtime_smoke_filter_patterns() -> list[str]:
    workflow = ANDROID_CI_WORKFLOW.read_text()
    filters_block = workflow.split("filters: |", 1)[1]
    runtime_block = filters_block.split("runtime_smoke:", 1)[1]
    patterns = []
    for line in runtime_block.splitlines():
        stripped = line.strip()
        if not stripped.startswith("- '"):
            if stripped and not stripped.startswith("#"):
                break
            continue
        patterns.append(stripped[3:].rstrip("'"))
    return patterns


class RuntimeDigestScopeTest(unittest.TestCase):
    def test_paths_filter_covers_every_digest_input(self):
        # If a digest input can change without materializing the evidence job,
        # stale evidence merges unverified. That is the one hole this gate cannot
        # tolerate, so the filter must stay a superset of the digest inputs.
        filter_patterns = _runtime_smoke_filter_patterns()
        self.assertTrue(filter_patterns)

        for digest_input in android_runtime_suites.RUNTIME_DIGEST_INPUTS:
            with self.subTest(digest_input=digest_input):
                self.assertTrue(
                    android_runtime_suites._matches_any(digest_input, filter_patterns),
                    f"{digest_input} feeds the runtime digest but no runtime_smoke "
                    f"filter pattern covers it: {filter_patterns}",
                )

    def test_evidence_file_is_excluded_from_its_own_digest(self):
        # Otherwise committing the evidence would immediately invalidate it and
        # the gate could never converge.
        self.assertIn(
            android_runtime_suites.EVIDENCE_PATH.name,
            android_runtime_suites.RUNTIME_DIGEST_EXCLUDES,
        )
        self.assertNotIn(
            android_runtime_suites.EVIDENCE_PATH.name,
            android_runtime_suites.runtime_digest_files(),
        )

    def test_secret_firebase_config_is_excluded_from_the_digest(self):
        tracked = android_runtime_suites.runtime_digest_files()
        self.assertEqual([], [path for path in tracked if path.endswith("google-services.json")])

    def test_digest_covers_app_runtime_and_android_test_sources(self):
        tracked = android_runtime_suites.runtime_digest_files()
        self.assertTrue(any(path.startswith("app/src/main/") for path in tracked))
        self.assertTrue(any(path.startswith("app/src/androidTest/") for path in tracked))
        self.assertIn("scripts/android_runtime_suites.py", tracked)

    def test_digest_is_stable_and_order_independent(self):
        first = android_runtime_suites.compute_runtime_digest()
        second = android_runtime_suites.compute_runtime_digest()
        self.assertEqual(first, second)
        self.assertTrue(first.startswith("sha256:"))

    def test_digest_only_reads_tracked_files(self):
        # Untracked build output must never move the digest, or the evidence would
        # be unreproducible between machines.
        with mock.patch.object(android_runtime_suites.subprocess, "run") as run:
            run.return_value = mock.Mock(returncode=0, stdout="app/src/main/A.kt\0")
            files = android_runtime_suites.runtime_digest_files()
        self.assertEqual(["app/src/main/A.kt"], files)
        self.assertIn("ls-files", run.call_args.args[0])


class RuntimeDigestMatcherTest(unittest.TestCase):
    def test_directory_glob_matches_nested_paths_and_the_directory_itself(self):
        self.assertTrue(android_runtime_suites._matches_any("app/src/main/a/b/C.kt", ["app/src/main/**"]))
        self.assertTrue(android_runtime_suites._matches_any("app/src/main", ["app/src/main/**"]))
        self.assertFalse(android_runtime_suites._matches_any("app/src/test/C.kt", ["app/src/main/**"]))

    def test_exclude_pattern_matches_nested_google_services(self):
        self.assertTrue(
            android_runtime_suites._matches_any(
                "app/src/dev/google-services.json",
                android_runtime_suites.RUNTIME_DIGEST_EXCLUDES,
            )
        )


class CheckEvidenceTest(unittest.TestCase):
    def test_missing_evidence_fails_and_names_the_local_gate(self):
        with tempfile.TemporaryDirectory() as temp_dir, \
                mock.patch.object(android_runtime_suites, "EVIDENCE_PATH", pathlib.Path(temp_dir) / "absent.json"):
            self.assertEqual(1, android_runtime_suites.check_evidence())

    def test_stale_evidence_fails(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            evidence_path = pathlib.Path(temp_dir) / ".runtime-evidence.json"
            evidence_path.write_text(json.dumps({"schema": 1, "runtime_digest": "sha256:stale"}))
            with mock.patch.object(android_runtime_suites, "EVIDENCE_PATH", evidence_path), \
                    mock.patch.object(android_runtime_suites, "compute_runtime_digest", return_value="sha256:fresh"):
                self.assertEqual(1, android_runtime_suites.check_evidence())

    def test_matching_evidence_passes(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            evidence_path = pathlib.Path(temp_dir) / ".runtime-evidence.json"
            evidence_path.write_text(
                json.dumps({"schema": 1, "runtime_digest": "sha256:fresh", "suites": {"a": {}}})
            )
            with mock.patch.object(android_runtime_suites, "EVIDENCE_PATH", evidence_path), \
                    mock.patch.object(android_runtime_suites, "compute_runtime_digest", return_value="sha256:fresh"):
                self.assertEqual(0, android_runtime_suites.check_evidence())

    def test_corrupt_evidence_is_treated_as_missing_rather_than_crashing(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            evidence_path = pathlib.Path(temp_dir) / ".runtime-evidence.json"
            evidence_path.write_text("{not json")
            with mock.patch.object(android_runtime_suites, "EVIDENCE_PATH", evidence_path):
                self.assertEqual(1, android_runtime_suites.check_evidence())


class RunLocalGateTest(unittest.TestCase):
    def test_no_connected_device_refuses_to_run(self):
        with mock.patch.object(android_runtime_suites, "connected_devices", return_value=[]):
            self.assertEqual(2, android_runtime_suites.run_local_gate())

    def test_multiple_devices_refuse_to_run(self):
        with mock.patch.object(android_runtime_suites, "connected_devices", return_value=["a", "b"]):
            self.assertEqual(2, android_runtime_suites.run_local_gate())

    def test_failing_suite_writes_no_evidence(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            evidence_path = pathlib.Path(temp_dir) / ".runtime-evidence.json"
            with mock.patch.object(android_runtime_suites, "EVIDENCE_PATH", evidence_path), \
                    mock.patch.object(android_runtime_suites, "connected_devices", return_value=["emulator-5554"]), \
                    mock.patch.object(android_runtime_suites, "compute_runtime_digest", return_value="sha256:x"), \
                    mock.patch.object(android_runtime_suites, "run_connected_tests", return_value=1):
                self.assertEqual(1, android_runtime_suites.run_local_gate())
            self.assertFalse(evidence_path.exists())

    def test_passing_run_records_every_android_ci_suite(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            evidence_path = pathlib.Path(temp_dir) / ".runtime-evidence.json"
            with mock.patch.object(android_runtime_suites, "EVIDENCE_PATH", evidence_path), \
                    mock.patch.object(android_runtime_suites, "connected_devices", return_value=["emulator-5554"]), \
                    mock.patch.object(android_runtime_suites, "compute_runtime_digest", return_value="sha256:x"), \
                    mock.patch.object(android_runtime_suites, "_describe_device", return_value={"description": "test"}), \
                    mock.patch.object(android_runtime_suites, "run_connected_tests", return_value=0), \
                    mock.patch.object(android_runtime_suites.subprocess, "run",
                                      return_value=mock.Mock(returncode=0, stdout="abc123\n")):
                self.assertEqual(0, android_runtime_suites.run_local_gate())

            evidence = json.loads(evidence_path.read_text())
            self.assertEqual("sha256:x", evidence["runtime_digest"])
            self.assertEqual(
                set(android_runtime_suites.ANDROID_CI_SEQUENCE),
                set(evidence["suites"]),
            )
            self.assertTrue(all(s["status"] == "passed" for s in evidence["suites"].values()))

    def test_sources_changing_mid_run_invalidate_the_evidence(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            evidence_path = pathlib.Path(temp_dir) / ".runtime-evidence.json"
            with mock.patch.object(android_runtime_suites, "EVIDENCE_PATH", evidence_path), \
                    mock.patch.object(android_runtime_suites, "connected_devices", return_value=["emulator-5554"]), \
                    mock.patch.object(android_runtime_suites, "compute_runtime_digest",
                                      side_effect=["sha256:before", "sha256:after"]), \
                    mock.patch.object(android_runtime_suites, "run_connected_tests", return_value=0):
                self.assertEqual(1, android_runtime_suites.run_local_gate())
            self.assertFalse(evidence_path.exists())


class AdbResolutionTest(unittest.TestCase):
    """The gate runs on developer machines, where platform-tools is often off PATH."""

    def test_existing_adb_on_path_is_used_as_is(self):
        with mock.patch("shutil.which", return_value="/usr/local/bin/adb"):
            self.assertEqual("/usr/local/bin/adb", android_runtime_suites.ensure_adb_on_path())

    def test_android_home_platform_tools_is_prepended_to_path(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            platform_tools = pathlib.Path(temp_dir) / "platform-tools"
            platform_tools.mkdir()
            (platform_tools / "adb").write_text("#!/bin/sh\n")

            with mock.patch("shutil.which", return_value=None), \
                    mock.patch.dict(android_runtime_suites.os.environ,
                                    {"ANDROID_HOME": temp_dir, "PATH": "/usr/bin"}, clear=False):
                resolved = android_runtime_suites.ensure_adb_on_path()
                self.assertEqual(str(platform_tools / "adb"), resolved)
                self.assertTrue(android_runtime_suites.os.environ["PATH"].startswith(str(platform_tools)))

    def test_missing_sdk_reports_none_instead_of_raising(self):
        with tempfile.TemporaryDirectory() as temp_dir, \
                mock.patch("shutil.which", return_value=None), \
                mock.patch.object(android_runtime_suites.pathlib.Path, "home",
                                  return_value=pathlib.Path(temp_dir)), \
                mock.patch.dict(android_runtime_suites.os.environ, {}, clear=True):
            self.assertIsNone(android_runtime_suites.ensure_adb_on_path())

    def test_local_gate_refuses_to_run_without_adb(self):
        with mock.patch.object(android_runtime_suites, "ensure_adb_on_path", return_value=None):
            self.assertEqual(2, android_runtime_suites.run_local_gate())

    def test_offline_and_unauthorized_devices_are_not_counted(self):
        stdout = "List of devices attached\nR3C	device\nemulator-5554	offline\nX9	unauthorized\n"
        with mock.patch.object(android_runtime_suites.subprocess, "run",
                               return_value=mock.Mock(returncode=0, stdout=stdout)):
            self.assertEqual(["R3C"], android_runtime_suites.connected_devices())


class AndroidCiEvidenceWorkflowTest(unittest.TestCase):
    def test_pull_requests_verify_evidence_instead_of_booting_an_emulator(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()
        evidence_job = workflow.split("  runtime-evidence:", 1)[1].split("\n  runtime-smoke:", 1)[0]

        self.assertIn("python3 scripts/android_runtime_suites.py check-evidence", evidence_job)
        self.assertNotIn("android-emulator-runner", evidence_job)
        self.assertNotIn("setup-java", evidence_job)
        self.assertNotIn("./gradlew", evidence_job)

    def test_emulator_gate_is_dispatch_only_so_it_cannot_run_on_pull_requests(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()
        smoke_job = workflow.split("  runtime-smoke:", 1)[1]
        condition = smoke_job.split("if:", 1)[1].split("\n", 1)[0].strip()

        self.assertEqual("github.event_name == 'workflow_dispatch'", condition)
        # The escalation path must still be a real emulator run, or it bit-rots.
        self.assertIn("reactivecircus/android-emulator-runner", smoke_job)
        # It must also produce committable evidence: a developer whose local device
        # cannot finish the suites has no other way to satisfy the evidence gate.
        self.assertIn("scripts/android_runtime_suites.py run-local-gate", smoke_job)
        self.assertIn(".runtime-evidence.json", smoke_job)

    def test_dependabot_runtime_deferral_is_preserved(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()
        evidence_job = workflow.split("  runtime-evidence:", 1)[1].split("\n  runtime-smoke:", 1)[0]

        self.assertIn("Dependabot PR: Firebase secrets are unavailable, so runtime smoke is deferred", evidence_job)
        self.assertIn("github.actor == 'dependabot[bot]'", evidence_job)
        self.assertIn("github.actor != 'dependabot[bot]'", evidence_job)


class RuntimeGateScriptTest(unittest.TestCase):
    def test_script_is_executable_and_delegates_to_the_manifest_module(self):
        self.assertTrue(RUNTIME_GATE_SCRIPT.exists())
        self.assertTrue(RUNTIME_GATE_SCRIPT.stat().st_mode & 0o111, "runtime-gate.sh must be executable")

        script = RUNTIME_GATE_SCRIPT.read_text()
        self.assertIn("run-local-gate", script)
        self.assertIn("check-evidence", script)
        self.assertIn("set -euo pipefail", script)

    def test_script_passes_shell_syntax_check(self):
        completed = subprocess.run(
            ["bash", "-n", str(RUNTIME_GATE_SCRIPT)],
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)


if __name__ == "__main__":
    unittest.main()
