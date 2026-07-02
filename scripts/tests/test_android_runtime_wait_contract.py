import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_TEST_ROOT = REPO_ROOT / "app" / "src" / "androidTest" / "java"
WAIT_HELPER = ANDROID_TEST_ROOT / "com" / "uiery" / "keep" / "testing" / "AndroidTestConditionWaiter.kt"
QA_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"


class AndroidRuntimeWaitContractTest(unittest.TestCase):
    def test_raw_thread_sleep_is_confined_to_android_test_wait_helper(self) -> None:
        offenders = []
        for kotlin_file in sorted(ANDROID_TEST_ROOT.rglob("*.kt")):
            text = kotlin_file.read_text()
            if "Thread.sleep" not in text:
                continue
            if kotlin_file == WAIT_HELPER:
                continue
            offenders.append(str(kotlin_file.relative_to(REPO_ROOT)))

        self.assertEqual(
            [],
            offenders,
            "androidTest code should use AndroidTestConditionWaiter.waitUntil/pause instead of raw Thread.sleep",
        )

    def test_wait_helper_exposes_condition_message_and_timeout_contract(self) -> None:
        helper = WAIT_HELPER.read_text()

        self.assertIn("fun waitUntil(", helper)
        self.assertIn("message: String", helper)
        self.assertIn("timeoutMs: Long", helper)
        self.assertIn("pollIntervalMs: Long", helper)
        self.assertIn("Thread.sleep", helper)
        self.assertIn("throw AssertionError", helper)

    def test_runtime_qa_checklist_documents_condition_based_wait_rule(self) -> None:
        checklist = QA_CHECKLIST.read_text()

        self.assertIn("조건 기반 wait helper", checklist)
        self.assertIn("AndroidTestConditionWaiter", checklist)
        self.assertIn("raw `Thread.sleep`", checklist)
        self.assertIn("scripts.tests.test_android_runtime_wait_contract", checklist)


if __name__ == "__main__":
    unittest.main()
