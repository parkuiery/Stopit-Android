import pathlib
import unittest

from scripts import android_runtime_suites


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
DOCS_THAT_DESCRIBE_ANDROID_CI_SMOKE = {
    "runtime QA checklist": REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md",
}


def _android_ci_runtime_smoke_entries():
    return android_runtime_suites.selectors_for(android_runtime_suites.ANDROID_CI_SEQUENCE)


class AndroidCiRuntimeSmokeDocsTest(unittest.TestCase):
    def test_android_ci_runtime_smoke_entries_are_manifested_and_documented_by_suite(self):
        entries = _android_ci_runtime_smoke_entries()
        self.assertIn(
            "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm",
            entries,
        )
        self.assertIn(
            "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
            entries,
        )

        workflow = ANDROID_CI_WORKFLOW.read_text()
        self.assertIn("scripts/android_runtime_suites.py run-connected android_ci_focused_runtime_smoke", workflow)
        self.assertIn("scripts/android_runtime_suites.py run-connected notification_denied_receiver notification_denied_emergency_unlock", workflow)

        for doc_name, path in DOCS_THAT_DESCRIBE_ANDROID_CI_SMOKE.items():
            text = path.read_text()
            with self.subTest(doc=doc_name):
                self.assertIn("Android CI", text)
                self.assertIn("focused runtime smoke", text)
                self.assertIn("scripts/android_runtime_suites.py", text)
                self.assertIn("android_ci_focused_runtime_smoke", text)
                self.assertIn("notification_denied_receiver", text)
                self.assertIn("notification_denied_emergency_unlock", text)

    def test_release_checklist_separates_android_ci_smoke_from_release_qa_evidence(self):
        checklist = (REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md").read_text()
        section = checklist.split("Automated runtime evidence is explicit in the PR body:", 1)[1]
        section = section.split("Android Release QA exact alarm evidence", 1)[0]

        self.assertIn("Android CI focused runtime smoke", section)
        self.assertIn("cite the current `.github/workflows/android-ci.yml` run URL", section)
        self.assertIn("Release/hotfix runtime evidence comes from", section)
        self.assertNotIn(
            "ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForPackageAndClockChangeActions",
            section,
        )
        self.assertNotIn(
            "EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage",
            section,
        )
        self.assertIn(
            "notification_denied_emergency_unlock",
            checklist,
        )


if __name__ == "__main__":
    unittest.main()
