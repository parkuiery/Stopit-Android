import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "PARENT_MODE_MVP.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class ParentModeContractTest(unittest.TestCase):
    def test_runbook_locks_same_device_mvp_scope(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #471",
            "부모 모드",
            "아이에게 폰 주기",
            "same-device MVP",
            "보호자 PIN",
            "허용 앱",
            "시간 만료",
            "원격 자녀 기기 관리",
            "MVP에서는 제외",
            "기존 긴급해제와 분리",
            "Refs #471",
            "Closes #471",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        forbidden_guidance = [
            "원격 제어를 MVP에 포함",
            "아이 이름 원문을 analytics",
            "앱 이름/package를 analytics",
            "긴급해제를 광고 뒤에",
        ]
        for phrase in forbidden_guidance:
            self.assertNotIn(phrase, runbook)

    def test_analytics_dictionary_contains_parent_mode_events_and_safe_parameters(self):
        analytics = ANALYTICS_DICTIONARY.read_text()

        for event_name in [
            "parent_mode_duration_selected",
            "parent_mode_allowed_apps_selected",
            "parent_mode_started",
            "parent_mode_completed",
            "parent_mode_unlocked_by_pin",
            "parent_mode_extended",
            "parent_mode_block_intercepted",
            "parent_mode_cancelled",
        ]:
            self.assertIn(event_name, analytics)

        for parameter in [
            "duration_minutes_bucket",
            "allowed_app_count_bucket",
            "pin_result",
            "end_reason",
            "extension_minutes_bucket",
            "block_context",
        ]:
            self.assertIn(parameter, analytics)

        self.assertIn("PARENT_MODE_MVP.md", analytics)
        self.assertIn("아이 이름/앱 이름/package/raw session history 금지", analytics)
        self.assertIn("manual_keep / timed_lock / routine / goal_lock / parent_mode", analytics)

    def test_runbook_tracks_session_store_and_accessibility_runtime_foothold(self):
        runbook = RUNBOOK.read_text()

        for phrase in [
            "ParentModeSessionStore",
            "PreferencesKey.PARENT_MODE_STARTED_AT",
            "BackupRestoreDataStoreKeyPolicy",
            "KeepAccessibilityServiceBlockDecisionTest",
            "block_source=parent_mode",
            "시간 만료 후 허용 앱도 차단",
            "부모 제어 surface는 차단하지 않는다",
            "PR #519",
            "PR #584",
            "repo-internal foothold",
            "남은 범위는 MVP 전체 UX/릴리스/실측 검증",
        ]:
            self.assertIn(phrase, runbook)

        stale_pre_implementation_phrases = [
            "이 표는 구현 전 계약이다",
            "이 문서/계약 PR은 구현 전 handoff",
        ]
        for phrase in stale_pre_implementation_phrases:
            self.assertNotIn(phrase, runbook)

    def test_runbook_tracks_session_controller_commit_boundary(self):
        runbook = RUNBOOK.read_text()
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        for phrase in [
            "ParentModeSessionController",
            "setup validation → session 저장 → privacy-safe analytics commit",
            "시작·연장·종료",
            "invalid setup",
            "PIN 없는 연장 거부",
            "PIN 성공 즉시 종료",
            "ParentModeSessionControllerTest",
        ]:
            self.assertIn(phrase, runbook)

        for phrase in [
            "ParentModeSessionControllerTest",
            "setup validation 실패 시 저장/analytics를 하지 않고",
            "PIN 성공 후 연장/즉시 종료만 저장",
        ]:
            self.assertIn(phrase, qa_checklist)

    def test_product_context_tracks_parent_mode_foothold_not_pre_implementation_handoff(self):
        product_context = PRODUCT_CONTEXT.read_text()

        self.assertIn("PR #519", product_context)
        self.assertIn("PR #584", product_context)
        self.assertIn("policy/analytics/session/Accessibility foothold", product_context)
        self.assertIn("남은 경계는 MVP 전체 UX", product_context)
        self.assertNotIn("원격 자녀 기기 관리 후속 gate를 구현 전 handoff로 고정한다", product_context)

    def test_high_traffic_docs_link_to_parent_mode_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]

        for document in documents:
            self.assertIn("PARENT_MODE_MVP.md", document)
            self.assertIn("#471", document)

    def test_ga4_registration_runbook_tracks_parent_mode_parameters(self):
        ga4_runbook = GA4_RUNBOOK.read_text()

        for parameter in [
            "customEvent:duration_minutes_bucket",
            "customEvent:allowed_app_count_bucket",
            "customEvent:pin_result",
            "customEvent:end_reason",
            "customEvent:extension_minutes_bucket",
            "customEvent:block_context",
        ]:
            self.assertIn(parameter, ga4_runbook)

        self.assertIn("부모 모드 조회성", ga4_runbook)
        self.assertIn("parent mode check", ga4_runbook)
        self.assertIn("아이 이름/앱 이름/package/raw session history", ga4_runbook)

    def test_qa_checklist_defines_parent_mode_runtime_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        self.assertIn("부모 모드 runtime QA baseline", qa_checklist)
        self.assertIn("ParentModePolicyTest", qa_checklist)
        self.assertIn("ParentModePinPolicyTest", qa_checklist)
        self.assertIn("FirebaseKeepAnalyticsTest.parentModeStartedUsesSafeBucketedParamsOnly", qa_checklist)
        self.assertIn("ParentModeAccessibilityIntegrationTest", qa_checklist)
        self.assertIn("0분/음수 extension은 거부", qa_checklist)
        self.assertIn("Parent mode QA evidence", qa_checklist)
        self.assertIn("same-device / PIN / bypass", qa_checklist)

    def test_runbook_points_future_lanes_to_contract_regression(self):
        runbook = RUNBOOK.read_text()

        self.assertIn(
            "python3 -m unittest scripts.tests.test_parent_mode_contract -v",
            runbook,
        )
        self.assertIn("parent-mode contract regression", runbook)


if __name__ == "__main__":
    unittest.main()
