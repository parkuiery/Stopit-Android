import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
EXACT_ALARM_TEST = REPO_ROOT / "app/src/androidTest/java/com/uiery/keep/receiver/ReceiverExactAlarmPermissionIntegrationTest.kt"
RUNTIME_TEST = REPO_ROOT / "app/src/androidTest/java/com/uiery/keep/receiver/ReceiverRuntimeIntegrationTest.kt"
RUNTIME_SUITES = REPO_ROOT / "scripts/android_runtime_suites.py"
QA_CHECKLIST = REPO_ROOT / "docs/QA_RUNTIME_CHECKLIST.md"
ANDROID_SKILLS_QA = REPO_ROOT / "docs/ANDROID_SKILLS_TESTING_QA.md"
PLAY_DEPLOYMENT = REPO_ROOT / "docs/PLAY_DEPLOYMENT.md"
RELEASE_CONTEXT = REPO_ROOT / "docs/ops/stopit/release-context.md"


class ReceiverAppOpsIsolationContractTest(unittest.TestCase):
    def test_exact_alarm_receiver_denied_tests_remain_host_appops_gated(self):
        text = EXACT_ALARM_TEST.read_text()
        deny_tests = [
            "bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent",
            "bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
            "packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent",
            "packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
            "routineAlarmReceiverWithExactAlarmPermissionDeniedKeepsTriggeredRoutineEnabledAndLeavesNoNextPendingIntent",
            "routineAlarmReceiverWithExactAlarmPermissionDeniedKeepsTriggeredMultiDayRoutineEnabledAndRevokesEveryRepeatDayAlarm",
        ]
        for test_name in deny_tests:
            body = self._kotlin_test_body(text, test_name)
            with self.subTest(test=test_name):
                self.assertIn(
                    "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
                    body,
                )
                self.assertNotIn("setExactAlarmPermissionDenied()", body)

    def test_runtime_receiver_appops_sensitive_tests_remain_host_appops_gated(self):
        text = RUNTIME_TEST.read_text()
        expectations = {
            "bootReceiverWithoutExactAlarmPermissionDisablesEnabledRoutineAndLeavesNoPendingIntent": "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            "packageReplacedWithoutExactAlarmPermissionDisablesEnabledRoutineAndLeavesNoPendingIntent": "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            "routineAlarmReceiverWithoutExactAlarmPermissionKeepsTriggeredRoutineEnabledAndDoesNotReschedule": "Disable SCHEDULE_EXACT_ALARM with host adb/appops before running this focused test",
            "routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine": "Disable POST_NOTIFICATION with host adb/appops before running this focused test",
        }
        for test_name, message in expectations.items():
            body = self._kotlin_test_body(text, test_name)
            with self.subTest(test=test_name):
                self.assertIn(message, body)
                self.assertNotIn("denyExactAlarmPermission()", body)
                self.assertNotIn("denyPostNotificationsPermission()", body)

    def test_runtime_suite_keeps_appops_sensitive_receiver_selectors_in_named_host_gated_suites(self):
        suites = RUNTIME_SUITES.read_text()
        self.assertIsNotNone(
            re.search(
                r'"release_exact_alarm_denied": \[.*ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent.*ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedKeepsTriggeredMultiDayRoutineEnabledAndRevokesEveryRepeatDayAlarm.*\]',
                suites,
                re.S,
            )
        )
        self.assertIsNotNone(
            re.search(
                r'"notification_denied_receiver": \[.*ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine.*\]',
                suites,
                re.S,
            )
        )

    def test_operator_docs_explain_host_appops_boundary_and_platform_reason(self):
        expected_phrases = [
            "host ADB appops",
            "target app 프로세스를 죽일 수",
            "테스트 내부에서 전환하지 않는다",
        ]
        for path in [QA_CHECKLIST, ANDROID_SKILLS_QA, PLAY_DEPLOYMENT, RELEASE_CONTEXT]:
            text = path.read_text()
            for phrase in expected_phrases:
                with self.subTest(path=path.name, phrase=phrase):
                    self.assertIn(phrase, text)

    @staticmethod
    def _kotlin_test_body(text: str, test_name: str) -> str:
        pattern = re.compile(rf"fun {re.escape(test_name)}\(\) = runBlocking \{{(?P<body>.*?)\n    \}}", re.S)
        match = pattern.search(text)
        if match is None:
            raise AssertionError(f"Could not find Kotlin test body for {test_name}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
