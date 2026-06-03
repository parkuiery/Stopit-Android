import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "ROUTINE_TEMPLATE_SHARE_MVP.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class RoutineTemplateShareContractTest(unittest.TestCase):
    def test_runbook_locks_privacy_safe_mvp_scope(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #407",
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

    def test_high_traffic_docs_link_to_routine_template_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]

        for document in documents:
            self.assertIn("ROUTINE_TEMPLATE_SHARE_MVP.md", document)
            self.assertIn("#407", document)

    def test_runbook_points_future_lanes_to_contract_regression(self):
        runbook = RUNBOOK.read_text()

        self.assertIn(
            "python3 -m unittest scripts.tests.test_routine_template_share_contract -v",
            runbook,
        )
        self.assertIn("routine-template share contract regression", runbook)


if __name__ == "__main__":
    unittest.main()
