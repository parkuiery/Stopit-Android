import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SERVICE = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "service" / "KeepAccessibilityService.kt"
RECOVERY = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "service" / "AccessibilityRuntimeFlowRecovery.kt"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"


class AccessibilityRuntimeRecoveryContractTest(unittest.TestCase):
    def test_blocking_snapshot_flow_uses_runtime_recovery_wrapper(self):
        service = SERVICE.read_text()
        snapshot_block = re.search(
            r"entryPoint\.blockingStateStore\(\)\.accessibilitySnapshot(?P<body>.*?)\.collect \{ snapshot ->",
            service,
            flags=re.S,
        )
        self.assertIsNotNone(snapshot_block, "KeepAccessibilityService should collect accessibilitySnapshot")
        body = snapshot_block.group("body") if snapshot_block else ""

        self.assertIn(".withAccessibilityRuntimeRecovery(", body)
        self.assertIn("source = AccessibilityRuntimeFlowSource.BlockingState", body)
        self.assertIn("onRecoveryEvent = ::recordRuntimeFlowRecovery", body)

        collect_block = self._collect_block(service, "entryPoint.blockingStateStore().accessibilitySnapshot")
        self.assertIn("cachedPrefs = snapshot", collect_block)
        self.assertIn("scheduleEmergencyUnlockExpiryCheck", collect_block)
        self.assertIn("syncEmergencyUnlockCountdownNotification", collect_block)
        self.assertIn("reevaluateCurrentForegroundAfterStateUpdate()", collect_block)

    def test_blocking_state_has_debug_source_and_operator_docs(self):
        recovery = RECOVERY.read_text()
        self.assertIn('BlockingState("blocking_state")', recovery)

        checklist = QA_RUNTIME_CHECKLIST.read_text()
        for phrase in [
            "blocking_state",
            "accessibilitySnapshot",
            "lastRuntimeFlowErrorSource",
            "withAccessibilityRuntimeRecovery",
        ]:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, checklist)

    def _collect_block(self, source: str, flow_start: str) -> str:
        start = source.index(flow_start)
        collect_start = source.index(".collect { snapshot ->", start)
        block_start = source.index("{", collect_start)
        depth = 0
        for index in range(block_start, len(source)):
            char = source[index]
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return source[block_start:index]
        self.fail(f"could not find collect block for {flow_start}")


if __name__ == "__main__":
    unittest.main()
