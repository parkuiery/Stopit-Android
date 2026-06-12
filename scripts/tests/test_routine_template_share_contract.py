import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "ROUTINE_TEMPLATE_SHARE_MVP.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
LOCALE_STRING_QUALITY = REPO_ROOT / "docs" / "LOCALE_STRING_QUALITY.md"


class RoutineTemplateShareContractTest(unittest.TestCase):
    def test_runbook_locks_privacy_safe_mvp_scope(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #407",
            "repo-internal 구현/검증 완료",
            "PR #428",
            "Android share sheet",
            "deep link/import는 별도 결정 게이트",
            "lockApplications",
            "package name",
            "앱 이름",
            "raw session history",
            "루틴 이름은 사용자가 직접 붙인 텍스트이므로",
            "공부/업무/야간 집중",
            "RoutineTemplateSharePayload",
            "Closes #407",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        forbidden_guidance = [
            "package name을 공유",
            "앱 목록을 공유",
            "raw usage history를 공유",
        ]
        for phrase in forbidden_guidance:
            self.assertNotIn(phrase, runbook)

    def test_runbook_locks_payload_locale_resource_template_followup(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Follow-up debt: #778",
            "공유문 locale/resource-template 계약 (#778)",
            "resource-backed text provider",
            "RoutineTemplateSharePayload.kt",
            "routine_template_share_chooser_title",
            "string/plural resource",
            "raw rendered share text",
            "raw duration string",
            "문서/ops/static-contract PR은 `Refs #778`",
            "code-lane PR에서만 `Closes #778`",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

    def test_analytics_dictionary_contains_routine_template_events_and_safe_parameters(self):
        analytics = ANALYTICS_DICTIONARY.read_text()

        for event_name in [
            "routine_template_share_tapped",
            "routine_template_share_sheet_opened",
            "routine_template_share_failed",
        ]:
            self.assertIn(event_name, analytics)

        for parameter in [
            "template_category",
            "repeat_days_bucket",
            "time_window_bucket",
            "routine_name_included",
        ]:
            self.assertIn(parameter, analytics)

        self.assertIn("앱 이름/package/lockApplications/raw session history 금지", analytics)
        self.assertIn("ROUTINE_TEMPLATE_SHARE_MVP.md", analytics)
        self.assertIn("#778은 공유 payload body/label/duration의 locale resource-template debt", analytics)
        self.assertIn("analytics schema 변경 이슈가 아니다", analytics)
        self.assertIn("raw rendered share text", analytics)
        self.assertIn("raw duration string", analytics)

    def test_high_traffic_docs_link_to_routine_template_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            GA4_RUNBOOK.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]

        for document in documents:
            self.assertIn("ROUTINE_TEMPLATE_SHARE_MVP.md", document)
            self.assertIn("#407", document)

        high_traffic_docs = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
            LOCALE_STRING_QUALITY.read_text(),
        ]
        for document in high_traffic_docs:
            self.assertIn("#778", document)

    def test_runbook_points_future_lanes_to_contract_regression(self):
        runbook = RUNBOOK.read_text()

        self.assertIn(
            "python3 -m unittest scripts.tests.test_routine_template_share_contract -v",
            runbook,
        )
        self.assertIn("routine-template share contract regression", runbook)

    def test_ga4_registration_runbook_tracks_routine_template_parameters(self):
        ga4_runbook = GA4_RUNBOOK.read_text()

        for parameter in [
            "customEvent:template_category",
            "customEvent:repeat_days_bucket",
            "customEvent:time_window_bucket",
            "customEvent:routine_name_included",
        ]:
            self.assertIn(parameter, ga4_runbook)

        self.assertIn("루틴 템플릿 공유 루프 조회성", ga4_runbook)
        self.assertIn("routine template share check", ga4_runbook)
        self.assertIn("앱 이름/package/lockApplications/raw session history", ga4_runbook)
        self.assertIn("#778 payload body/label/duration resource-template 전환은 새 GA4 registration 항목을 만들지 않는다", ga4_runbook)
        self.assertIn("raw rendered share text", ga4_runbook)

    def test_qa_checklist_defines_privacy_safe_routine_share_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        self.assertIn("루틴 템플릿 공유 privacy-safe QA baseline", qa_checklist)
        self.assertIn("RoutineTemplateSharePayloadTest", qa_checklist)
        self.assertIn("RoutineTemplateShareAnalyticsTest", qa_checklist)
        self.assertIn("Routine template share QA evidence", qa_checklist)
        self.assertIn("app names / package names / lockApplications absent", qa_checklist)
        self.assertIn("#778 계열 현지화 PR", qa_checklist)
        self.assertIn("payload body follows current locale", qa_checklist)
        self.assertIn("raw rendered text / raw duration string / locale-specific body", qa_checklist)


if __name__ == "__main__":
    unittest.main()
