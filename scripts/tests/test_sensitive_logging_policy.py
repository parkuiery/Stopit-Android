import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN_SOURCE_ROOT = REPO_ROOT / "app/src/main/java"
ROUTINE_SCHEDULER = REPO_ROOT / "app/src/main/java/com/uiery/keep/notification/RoutineScheduler.kt"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs/QA_RUNTIME_CHECKLIST.md"

LOG_USAGE_PATTERN = re.compile(r"\b(?:android\.util\.Log|Log\.)")
LOG_ALLOWLIST = {
    pathlib.Path("com/uiery/keep/util/AppLogger.kt"): "Central debug-only logging wrapper; app code must not call android.util.Log directly.",
}


class SensitiveLoggingPolicyTest(unittest.TestCase):
    def test_main_source_uses_only_central_logging_wrapper(self):
        violations: list[str] = []
        for source_path in sorted(MAIN_SOURCE_ROOT.rglob("*.kt")):
            relative_path = source_path.relative_to(MAIN_SOURCE_ROOT)
            if relative_path in LOG_ALLOWLIST:
                continue

            for line_number, line in enumerate(source_path.read_text().splitlines(), start=1):
                if LOG_USAGE_PATTERN.search(line):
                    violations.append(f"{relative_path}:{line_number}: {line.strip()}")

        self.assertEqual(
            [],
            violations,
            "Production app code must route logging through AppLogger instead of raw android.util.Log calls.",
        )

    def test_logging_allowlist_is_documented_and_existing(self):
        for relative_path, reason in LOG_ALLOWLIST.items():
            with self.subTest(path=str(relative_path)):
                self.assertTrue((MAIN_SOURCE_ROOT / relative_path).exists(), reason)
                self.assertGreater(len(reason), 20)

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
