import pathlib
import unittest

from scripts import android_runtime_suites


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
DOCS = {
    "release checklist": REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md",
    "play deployment": REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md",
    "runtime QA checklist": REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md",
    "Android skills testing QA": REPO_ROOT / "docs" / "ANDROID_SKILLS_TESTING_QA.md",
    "release context": REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md",
}
CRASHLYTICS_RECURRENCE_DOCS = {
    "release checklist": REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md",
    "runtime QA checklist": REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md",
    "release context": REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md",
}

REQUIRED_RELEASE_QA_SUITES = android_runtime_suites.RELEASE_QA_SEQUENCE
REQUIRED_RELEASE_QA_GATES = android_runtime_suites.selectors_for(REQUIRED_RELEASE_QA_SUITES)

STALE_RELEASE_QA_GATES = [
    "ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForPackageAndClockChangeActions",
    "ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced",
    "ReceiverRuntimeIntegrationTest#timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm",
    "ReceiverRuntimeIntegrationTest#timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms",
]

RELEASE_FACING_DOCS = {
    "release checklist": REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md",
    "play deployment": REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md",
    "Android skills testing QA": REPO_ROOT / "docs" / "ANDROID_SKILLS_TESTING_QA.md",
}


class ReleaseQaRuntimeGateDocsTest(unittest.TestCase):
    def test_release_qa_instrumentation_selectors_exist_in_android_test_sources(self):
        self.assertEqual([], android_runtime_suites.validate_sources())

    def test_guarded_release_qa_runtime_suites_exist_in_workflow(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()
        self.assertIn("scripts/android_runtime_suites.py", workflow)
        for suite in REQUIRED_RELEASE_QA_SUITES:
            with self.subTest(suite=suite):
                self.assertIn(suite, workflow)

    def test_notification_channel_disabled_is_release_qa_gate(self):
        self.assertIn("notification_channel_disabled", android_runtime_suites.RELEASE_QA_SEQUENCE)
        workflow = RELEASE_QA_WORKFLOW.read_text()
        self.assertIn("run-connected notification_channel_disabled", workflow)

    def test_release_remaining_runtime_docs_name_migration_and_notification_tap_gates(self):
        required_phrases = [
            "KeepDatabaseMigrationTest",
            "RoutineStartNotificationTapIntegrationTest",
        ]
        for doc_name, path in DOCS.items():
            text = path.read_text()
            for phrase in required_phrases:
                with self.subTest(doc=doc_name, phrase=phrase):
                    self.assertIn(phrase, text)

    def test_release_qa_keeps_notification_denied_methods_out_of_normal_batch(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()
        normal_batch, notification_denied_batch = workflow.split(
            "run-connected notification_denied_receiver notification_denied_emergency_unlock",
            maxsplit=1,
        )

        self.assertNotIn("notification_denied_emergency_unlock", normal_batch)
        self.assertIn("POST_NOTIFICATION ignore", notification_denied_batch)
        self.assertIn("run-connected notification_denied_receiver notification_denied_emergency_unlock", workflow)

    def test_release_operator_docs_reference_manifest_suites_instead_of_workflow_mirror(self):
        for doc_name, path in DOCS.items():
            text = path.read_text()
            with self.subTest(doc=doc_name):
                self.assertIn("Release instrumentation QA", text)
                self.assertIn("scripts/android_runtime_suites.py", text)
                self.assertIn("multi-day", text)
                self.assertIn("POST_NOTIFICATION ignore", text)
            for suite in REQUIRED_RELEASE_QA_SUITES:
                with self.subTest(doc=doc_name, suite=suite):
                    self.assertIn(suite, text)

    def test_release_context_names_the_same_release_qa_suite_sequence_as_manifest(self):
        release_context = DOCS["release context"].read_text()
        last_index = -1
        for suite in REQUIRED_RELEASE_QA_SUITES:
            index = release_context.find(suite)
            with self.subTest(suite=suite):
                self.assertNotEqual(-1, index)
                self.assertGreater(index, last_index)
            last_index = index

    def test_release_facing_docs_do_not_mix_stale_android_ci_smoke_as_release_qa(self):
        for doc_name, path in RELEASE_FACING_DOCS.items():
            text = path.read_text()
            for gate in STALE_RELEASE_QA_GATES:
                with self.subTest(doc=doc_name, gate=gate):
                    self.assertNotIn(gate, text)
        release_context = DOCS["release context"].read_text()
        self.assertIn("Android CI PR gate is intentionally separate", release_context)
        self.assertIn("use the exact Release QA suite sequence below", release_context)

    def test_release_facing_docs_use_dev_package_for_dev_runtime_appops(self):
        stale_appops = [
            "adb shell appops set com.uiery.keep POST_NOTIFICATION",
            "adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM",
        ]
        for doc_name, path in DOCS.items():
            text = path.read_text()
            with self.subTest(doc=doc_name):
                self.assertIn("adb shell appops set com.uiery.keep.dev", text)
            for stale in stale_appops:
                with self.subTest(doc=doc_name, stale=stale):
                    self.assertNotIn(stale, text)

    def test_crashlytics_recurrence_handoff_is_release_documented(self):
        required_phrases = [
            "Crashlytics #101 post-release recurrence evidence",
            "PR #143",
            "PR #304",
            "PR #320",
            "PR #322",
            "d1369c1905b65f09a031309198552d10",
            "release 후",
            "#101",
        ]
        for doc_name, path in CRASHLYTICS_RECURRENCE_DOCS.items():
            text = path.read_text()
            for phrase in required_phrases:
                with self.subTest(doc=doc_name, phrase=phrase):
                    self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
