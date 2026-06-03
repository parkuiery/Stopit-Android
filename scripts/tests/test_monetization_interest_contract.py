import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
KEEP_ANALYTICS = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "analytics" / "KeepAnalytics.kt"
FIREBASE_ANALYTICS = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "analytics" / "FirebaseKeepAnalytics.kt"
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
ADMOB_RUNBOOK = REPO_ROOT / "docs" / "ADMOB_MONETIZATION_RUNBOOK.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"


class MonetizationInterestContractTest(unittest.TestCase):
    def test_interest_events_are_implemented_and_documented_as_code_contract(self):
        keep_analytics = KEEP_ANALYTICS.read_text()
        firebase_analytics = FIREBASE_ANALYTICS.read_text()
        event_dictionary = EVENT_DICTIONARY.read_text()
        ga4_runbook = GA4_RUNBOOK.read_text()
        admob_runbook = ADMOB_RUNBOOK.read_text()
        metrics_analysis = METRICS_ANALYSIS.read_text()
        product_dashboard = PRODUCT_DASHBOARD.read_text()
        docs_agents = DOCS_AGENTS.read_text()
        product_context = PRODUCT_CONTEXT.read_text()
        metrics_context = METRICS_CONTEXT.read_text()

        for event in ["MONETIZATION_INTEREST_SHOWN", "MONETIZATION_INTEREST_CLICKED"]:
            self.assertIn(event, keep_analytics)
            self.assertIn(event, firebase_analytics)

        for param in ["INTEREST_CONTEXT", "INTEREST_SURFACE", "INTEREST_VARIANT", "PURCHASE_AVAILABLE"]:
            self.assertIn(param, keep_analytics)
            self.assertIn(param, firebase_analytics)

        self.assertNotIn("광고 제거 관심도 실험 이벤트는 아직 구현 전 문서 계약", event_dictionary)
        self.assertIn("KeepAnalytics.kt` / `FirebaseKeepAnalytics.kt` / `FirebaseKeepAnalyticsTest.kt`", event_dictionary)
        self.assertIn("2026-06-03 코드 계약 추가", ga4_runbook)
        self.assertIn("2026-06-03 QA/code contract", admob_runbook)
        self.assertIn("PR #362", product_context)
        self.assertIn("PR #362", metrics_context)
        self.assertIn("PR #362", metrics_analysis)
        self.assertIn("PR #362", product_dashboard)
        self.assertIn("monetization_interest_*", docs_agents)
        self.assertIn("관심도 실험 준비", metrics_context)
        self.assertIn("실험 시작", admob_runbook)
        self.assertIn("광고 제거 관심도 측정 handoff", admob_runbook)
        self.assertIn("CTA UI 배치 전", ga4_runbook)
        self.assertIn("post-release 창 전에는 event 0을 수요 없음으로 해석 금지", admob_runbook)
        self.assertNotIn("| `interest_context` | Required dimension | `TODO`", ga4_runbook)


if __name__ == "__main__":
    unittest.main()
