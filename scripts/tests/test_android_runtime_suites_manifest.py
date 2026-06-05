import pathlib
import unittest

from scripts import android_runtime_suites


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"


class AndroidRuntimeSuitesManifestTest(unittest.TestCase):
    def test_required_suites_are_defined(self):
        required = {
            "android_ci_focused_runtime_smoke",
            "release_focused_ui_smoke",
            "release_exact_alarm_default",
            "release_exact_alarm_denied",
            "release_exact_alarm_allowed",
            "release_remaining_runtime",
            "notification_denied_receiver",
            "notification_denied_emergency_unlock",
        }

        self.assertEqual(required, set(android_runtime_suites.SUITES))
        for suite_name in required:
            with self.subTest(suite=suite_name):
                self.assertTrue(android_runtime_suites.SUITES[suite_name])

    def test_all_selectors_exist_in_android_test_sources(self):
        self.assertEqual([], android_runtime_suites.validate_sources())

    def test_cli_class_arg_preserves_comma_separated_selector_contract(self):
        class_arg = android_runtime_suites.class_arg([
            "notification_denied_receiver",
            "notification_denied_emergency_unlock",
        ])

        self.assertIn(
            "ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine",
            class_arg,
        )
        self.assertIn(
            "EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
            class_arg,
        )
        self.assertNotIn("\n", class_arg)
        self.assertEqual(1, class_arg.count(","))

    def test_workflows_use_manifest_instead_of_hardcoded_selector_lists(self):
        android_ci = ANDROID_CI_WORKFLOW.read_text()
        release_qa = RELEASE_QA_WORKFLOW.read_text()

        self.assertIn("scripts/android_runtime_suites.py lines android_ci_focused_runtime_smoke", android_ci)
        self.assertIn("scripts/android_runtime_suites.py lines notification_denied_receiver notification_denied_emergency_unlock", android_ci)
        self.assertIn("scripts/android_runtime_suites.py lines release_exact_alarm_denied", release_qa)
        self.assertIn("scripts/android_runtime_suites.py lines release_exact_alarm_allowed", release_qa)
        self.assertIn("scripts/android_runtime_suites.py lines release_remaining_runtime", release_qa)
        self.assertIn("scripts/android_runtime_suites.py selector notification_denied_receiver 0", release_qa)
        self.assertIn("scripts/android_runtime_suites.py selector notification_denied_emergency_unlock 0", release_qa)

    def test_runtime_suite_manifest_changes_materialize_ci_scopes(self):
        android_ci = ANDROID_CI_WORKFLOW.read_text()
        self.assertIn("scripts/android_runtime_suites.py", android_ci)
        self.assertIn("scripts/tests/test_android_runtime_suites_manifest.py", android_ci)

    def test_workflow_selector_loops_do_not_use_xargs(self):
        workflows = {
            "Android CI": ANDROID_CI_WORKFLOW.read_text(),
            "Release QA": RELEASE_QA_WORKFLOW.read_text(),
        }

        for name, content in workflows.items():
            with self.subTest(workflow=name):
                self.assertNotIn("xargs", content)
                self.assertIn("while IFS= read -r selector", content)


if __name__ == "__main__":
    unittest.main()
