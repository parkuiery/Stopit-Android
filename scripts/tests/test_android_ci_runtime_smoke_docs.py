import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
DOCS_THAT_MIRROR_ANDROID_CI_SMOKE = {
    "runtime QA checklist": REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md",
}


def _android_ci_runtime_smoke_entries():
    workflow = ANDROID_CI_WORKFLOW.read_text()
    class_args = re.findall(
        r"-Pandroid\.testInstrumentationRunnerArguments\.class=([^\s]+)",
        workflow,
    )
    if len(class_args) != 2:
        raise AssertionError(
            f"Expected Android CI to have 2 focused runtime smoke class args; got {len(class_args)}"
        )
    return [entry for class_arg in class_args for entry in class_arg.split(",")]


class AndroidCiRuntimeSmokeDocsTest(unittest.TestCase):
    def test_android_ci_runtime_smoke_entries_are_documented(self):
        entries = _android_ci_runtime_smoke_entries()
        self.assertIn(
            "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm",
            entries,
        )
        self.assertIn(
            "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
            entries,
        )

        for doc_name, path in DOCS_THAT_MIRROR_ANDROID_CI_SMOKE.items():
            text = path.read_text()
            with self.subTest(doc=doc_name):
                self.assertIn("Android CI", text)
                self.assertIn("focused runtime smoke", text)
            for entry in entries:
                with self.subTest(doc=doc_name, entry=entry):
                    self.assertIn(entry, text)

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
            "EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
            checklist,
        )


if __name__ == "__main__":
    unittest.main()
