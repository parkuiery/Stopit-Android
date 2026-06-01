import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
DOCS = {
    "release checklist": REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md",
    "play deployment": REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md",
    "release context": REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md",
}

REQUIRED_RELEASE_QA_GATES = [
    "RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt",
    "ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
    "ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
    "ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
    "RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm",
    "RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent",
    "ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm",
    "ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm",
    "ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine",
    "ManifestContractIntegrationTest",
    "EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
]


class ReleaseQaRuntimeGateDocsTest(unittest.TestCase):
    def test_guarded_release_qa_runtime_gates_exist_in_workflow(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()
        for gate in REQUIRED_RELEASE_QA_GATES:
            with self.subTest(gate=gate):
                self.assertIn(gate, workflow)

    def test_release_operator_docs_mirror_guarded_release_qa_runtime_gates(self):
        for doc_name, path in DOCS.items():
            text = path.read_text()
            with self.subTest(doc=doc_name):
                self.assertIn("Release instrumentation QA", text)
                self.assertIn("multi-day", text)
                self.assertIn("ManifestContractIntegrationTest", text)
                self.assertIn("POST_NOTIFICATION ignore", text)
            for gate in REQUIRED_RELEASE_QA_GATES:
                with self.subTest(doc=doc_name, gate=gate):
                    self.assertIn(gate, text)


if __name__ == "__main__":
    unittest.main()
