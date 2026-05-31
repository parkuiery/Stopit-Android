import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ROUTINE_SCHEDULER = REPO_ROOT / "app/src/main/java/com/uiery/keep/notification/RoutineScheduler.kt"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs/QA_RUNTIME_CHECKLIST.md"


class SensitiveLoggingPolicyTest(unittest.TestCase):
    def test_routine_scheduler_does_not_log_full_routine_model(self):
        source = ROUTINE_SCHEDULER.read_text()

        self.assertNotIn('Log.d("TEST", "scheduleRoutine: $routine")', source)
        self.assertNotRegex(
            source,
            re.compile(r"Log\.\w+\([^\n]*routine", re.IGNORECASE),
            "RoutineScheduler must not log raw RoutineModel values; names, times, and app packages are sensitive.",
        )

    def test_qa_checklist_warns_against_sensitive_logcat_values(self):
        checklist = QA_RUNTIME_CHECKLIST.read_text()

        self.assertIn("RoutineModel.toString()", checklist)
        self.assertIn("루틴명", checklist)
        self.assertIn("차단 앱 package", checklist)
        self.assertIn("토큰", checklist)


if __name__ == "__main__":
    unittest.main()
