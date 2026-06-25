import pathlib
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
QA_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
SOURCE_DOC = REPO_ROOT / "docs" / "ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md"
ROUTINESTORE_CONTRACT = REPO_ROOT / "docs" / "ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
METRICS_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
ANDROID_SKILLS_QA = REPO_ROOT / "docs" / "ANDROID_SKILLS_TESTING_QA.md"
PLAY_DEPLOYMENT = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
DEFAULT_STRINGS = REPO_ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"
LOCALIZED_STRING_DIRS = sorted((REPO_ROOT / "app" / "src" / "main" / "res").glob("values-*"))
ACCESSIBILITY_RUNTIME_TEST = (
    REPO_ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "com"
    / "uiery"
    / "keep"
    / "service"
    / "KeepAccessibilityServiceIntegrationTest.kt"
)
ROUTINE_LIST_CONTENT_TEST = (
    REPO_ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "com"
    / "uiery"
    / "keep"
    / "feature"
    / "routine"
    / "component"
    / "RoutineListContentIntegrationTest.kt"
)
ROUTINE_LIST_POLICY_TEST = (
    REPO_ROOT
    / "app"
    / "src"
    / "test"
    / "java"
    / "com"
    / "uiery"
    / "keep"
    / "feature"
    / "routine"
    / "RoutineListActionPolicyTest.kt"
)
BLOCK_SCREEN_CONTENT_TEST = (
    REPO_ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "com"
    / "uiery"
    / "keep"
    / "BlockScreenContentIntegrationTest.kt"
)


def _strings(path: pathlib.Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {
        element.attrib["name"]: (element.text or "")
        for element in root.findall("string")
    }


class ActiveRoutineEnforcementContractTest(unittest.TestCase):
    def test_source_doc_is_linked_from_high_traffic_docs(self):
        source = SOURCE_DOC.read_text()
        checklist = QA_CHECKLIST.read_text()
        docs_agents = DOCS_AGENTS.read_text()
        dashboard = METRICS_DASHBOARD.read_text()
        metrics_context = METRICS_CONTEXT.read_text()

        self.assertIn("Issue: #609", source)
        self.assertIn("foreground 즉시 차단", source)
        self.assertIn("수정/삭제/OFF 우회", source)
        self.assertIn("비징벌적 안내", source)
        self.assertIn("release/tag/Play deploy", source)
        self.assertIn("14일", source)
        self.assertIn("docs/ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md", checklist)
        self.assertIn("ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md", docs_agents)
        self.assertIn("ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md", dashboard)
        self.assertIn("ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md", metrics_context)

    def test_qa_checklist_pins_active_routine_foreground_runtime_evidence(self):
        checklist = QA_CHECKLIST.read_text()

        self.assertIn("Issue: #609", checklist)
        self.assertIn(
            "KeepAccessibilityServiceIntegrationTest#activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution",
            checklist,
        )
        self.assertIn("block_source=routine", checklist)
        self.assertIn("routine_id", checklist)
        self.assertIn("foreground", checklist)
        self.assertIn("release/tag/Play deploy", checklist)

    def test_accessibility_runtime_test_asserts_active_routine_attribution(self):
        source = ACCESSIBILITY_RUNTIME_TEST.read_text()

        self.assertIn(
            "fun activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution()",
            source,
        )
        self.assertIn("AnalyticsBlockSource.ROUTINE", source)
        self.assertIn("lastLaunchedRoutineId", source)
        self.assertIn("ROUTINE_RUNTIME_TEST_ID", source)

    def test_accessibility_runtime_test_covers_foreground_when_routine_starts_later(self):
        source = ACCESSIBILITY_RUNTIME_TEST.read_text()
        checklist = QA_CHECKLIST.read_text()
        test_name = "foregroundAppBecomesBlockedWhenRoutineStartTimeArrives"

        self.assertIn(f"fun {test_name}()", source)
        self.assertIn("configureFutureRoutineBlock", source)
        self.assertIn("waitForPackageForeground", source)
        self.assertIn("lastLaunchedBlockSource == AnalyticsBlockSource.ROUTINE", source)
        self.assertIn(f"KeepAccessibilityServiceIntegrationTest#{test_name}", checklist)
        self.assertIn("루틴 시간이 도래", checklist)

    def test_routine_list_switch_tap_surfaces_blocked_feedback(self):
        policy_test = ROUTINE_LIST_POLICY_TEST.read_text()
        content_test = ROUTINE_LIST_CONTENT_TEST.read_text()
        checklist = QA_CHECKLIST.read_text()

        self.assertIn("runningRoutineSwitchTapSurfacesBlockedActionFeedbackWithoutChangingEnabledState", content_test)
        self.assertIn("routine-enabled-switch-609", content_test)
        self.assertIn("resolveRoutineEnabledSwitchAction", policy_test)
        self.assertIn("RoutineListAction.Blocked", policy_test)
        self.assertIn("RoutineListActionPolicyTest", checklist)
        self.assertIn("RoutineListContentIntegrationTest#runningRoutineSwitchTapSurfacesBlockedActionFeedbackWithoutChangingEnabledState", checklist)

    def test_routine_alarm_reschedule_failure_preserves_current_enforcement_contract(self):
        source = SOURCE_DOC.read_text()
        policy_test = (
            REPO_ROOT
            / "app"
            / "src"
            / "test"
            / "java"
            / "com"
            / "uiery"
            / "keep"
            / "receiver"
            / "RoutineReceiverPolicyTest.kt"
        ).read_text()

        self.assertIn("routine-start reschedule", source)
        self.assertIn("MissingExactAlarmPermission", source)
        self.assertIn(
            "RoutineReceiverPolicyTest#applyRoutineAlarmRescheduleResultKeepsTriggeredRoutineEnabledWhenExactAlarmPermissionMissing",
            QA_CHECKLIST.read_text(),
        )
        self.assertIn("InvalidRoutine", QA_CHECKLIST.read_text())
        self.assertIn("applyRoutineAlarmRescheduleResultKeepsTriggeredRoutineEnabledWhenExactAlarmPermissionMissing", policy_test)
        self.assertIn("applyRoutineAlarmRescheduleResultDisablesInvalidTriggeredRoutineWithoutResettingPrompt", policy_test)

        routine_store_contract = ROUTINESTORE_CONTRACT.read_text()
        self.assertIn("#609 활성 루틴 보호 계약", routine_store_contract)
        self.assertIn("현재 triggered routine은 enabled/enforced 상태를 유지한다", routine_store_contract)

    def test_release_exact_alarm_docs_separate_current_alarm_exception_from_downgrade_gates(self):
        expected_boundary = "routine alarm이 이미 발화한 뒤 next-reschedule에서 MissingExactAlarmPermission이 발생하는 #609 예외"
        expected_legacy_gate = "legacy release instrumentation selector"

        for path in [ANDROID_SKILLS_QA, PLAY_DEPLOYMENT, RELEASE_CONTEXT, QA_CHECKLIST]:
            document = path.read_text()
            self.assertIn(expected_boundary, document, f"{path} must name the current-triggered routine exception")
            self.assertIn(expected_legacy_gate, document, f"{path} must keep legacy receiver selector interpretation explicit")

    def test_active_routine_blocked_message_is_localized_in_shipped_locales(self):
        key = "routine_active_action_blocked_message"
        default_text = _strings(DEFAULT_STRINGS)[key]
        offenders = []

        for values_dir in LOCALIZED_STRING_DIRS:
            strings_path = values_dir / "strings.xml"
            if not strings_path.exists():
                continue
            localized_text = _strings(strings_path).get(key)
            if localized_text == default_text:
                offenders.append(values_dir.name)

        self.assertEqual(
            [],
            offenders,
            "Active routine block guidance is high-trust #609 copy and must not ship as copied default English in localized resources.",
        )

    def test_routine_block_screen_reason_is_localized_and_covered_by_compose_baseline(self):
        key = "block_screen_routine_active_reason"
        default_text = _strings(DEFAULT_STRINGS)[key]
        offenders = []

        for values_dir in LOCALIZED_STRING_DIRS:
            strings_path = values_dir / "strings.xml"
            if not strings_path.exists():
                continue
            localized_text = _strings(strings_path).get(key)
            if localized_text == default_text:
                offenders.append(values_dir.name)

        content_test = BLOCK_SCREEN_CONTENT_TEST.read_text()
        source_doc = SOURCE_DOC.read_text()
        checklist = QA_CHECKLIST.read_text()

        self.assertEqual(
            [],
            offenders,
            "Active routine Block screen reason copy is high-trust #609 copy and must not ship as copied default English in localized resources.",
        )
        self.assertIn("activeRoutineBlockExplainsRoutineReasonWhileKeepingEmergencyUnlockSecondary", content_test)
        self.assertIn("block_screen_routine_active_reason", content_test)
        self.assertIn("block_screen_routine_active_reason", source_doc)
        self.assertIn(
            "BlockScreenContentIntegrationTest#activeRoutineBlockExplainsRoutineReasonWhileKeepingEmergencyUnlockSecondary",
            checklist,
        )


if __name__ == "__main__":
    unittest.main()
