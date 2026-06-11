import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
KEEP_ANALYTICS = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "analytics" / "KeepAnalytics.kt"
FIREBASE_ANALYTICS = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "analytics" / "FirebaseKeepAnalytics.kt"
MENU_VIEW_MODEL = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "feature" / "menu" / "MenuViewModel.kt"
MENU_SCREEN = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "feature" / "menu" / "MenuScreen.kt"
STRINGS = REPO_ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"
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
        menu_view_model = MENU_VIEW_MODEL.read_text()
        menu_screen = MENU_SCREEN.read_text()
        strings = STRINGS.read_text()
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
        self.assertIn("onMonetizationInterestCardShown", menu_view_model)
        self.assertIn("onMonetizationInterestCardClicked", menu_view_model)
        self.assertIn("monetization_interest_menu_title", menu_screen)
        self.assertIn("monetization_interest_menu_message", strings)
        self.assertIn("CTA UI | 완료됨", admob_runbook)
        self.assertIn("MenuScreen.kt", admob_runbook)
        self.assertIn("2026-06-04 메뉴/설정 CTA", ga4_runbook)
        self.assertIn("관심도 실험/placement measurement 준비", metrics_context)
        self.assertIn("실험 시작", admob_runbook)
        self.assertIn("광고 제거 관심도 측정 handoff", admob_runbook)
        self.assertIn("PR #402 release boundary snapshot", admob_runbook)
        self.assertIn("de142bd34a2729bcbb1e932db70b34d6459ce3b0", admob_runbook)
        self.assertIn("de142bd34a2729bcbb1e932db70b34d6459ce3b0", ga4_runbook)
        self.assertIn("de142bd34a2729bcbb1e932db70b34d6459ce3b0", product_context)
        self.assertIn("de142bd34a2729bcbb1e932db70b34d6459ce3b0", metrics_context)
        self.assertIn("de142bd34a2729bcbb1e932db70b34d6459ce3b0", product_dashboard)
        self.assertIn("PR #402가 `main`/SemVer tag/Play deploy에 포함되기 전", admob_runbook)
        self.assertIn("CTA 포함 버전 배포 전후", ga4_runbook)
        self.assertIn("post-release 창 전에는 event 0을 수요 없음으로 해석 금지", admob_runbook)
        self.assertNotIn("| `interest_context` | Required dimension | `TODO`", ga4_runbook)

    def test_admob_latest_readback_keeps_smoke_separate_from_production_measurement(self):
        admob_runbook = ADMOB_RUNBOOK.read_text()
        metrics_analysis = METRICS_ANALYSIS.read_text()
        product_dashboard = PRODUCT_DASHBOARD.read_text()
        metrics_context = METRICS_CONTEXT.read_text()
        product_context = PRODUCT_CONTEXT.read_text()

        for doc in [admob_runbook, metrics_analysis, product_dashboard, metrics_context, product_context]:
            self.assertIn("2026-06-11", doc)
            self.assertIn("source-split queryability smoke", doc)
            self.assertIn("13,459", doc)
            self.assertIn("48.2%", doc)
            self.assertIn("release/tag/Play", doc)

        self.assertIn("`ad_banner_impression` 125건", metrics_analysis)
        self.assertIn("`ad_banner_revenue` 124건", metrics_analysis)
        self.assertIn("appVersion = 1.7.5", admob_runbook)
        self.assertIn("20260602..20260605", admob_runbook)
        self.assertIn("원인 분리", admob_runbook)
        self.assertIn("placement 실험보다", metrics_context)

    def test_banner_placement_contract_is_documented(self):
        admob_runbook = ADMOB_RUNBOOK.read_text()
        ad_placement = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "analytics" / "AdPlacement.kt").read_text()
        contract_test = (REPO_ROOT / "app" / "src" / "test" / "java" / "com" / "uiery" / "keep" / "analytics" / "AdPlacementContractTest.kt").read_text()

        self.assertIn("fun AdPlacement.toMetadata", ad_placement)
        self.assertIn("ad placements have unique non-empty snake case analytics names", contract_test)
        self.assertIn("`AdPlacement.toMetadata(...)`", admob_runbook)
        self.assertIn("`AdPlacementContractTest`", admob_runbook)
        self.assertIn("placement/ad unit pair", admob_runbook)
        self.assertIn("e6d4d70ada739c545672e95950fb6f82409fd10f", PRODUCT_DASHBOARD.read_text())
        self.assertIn("e6d4d70ada739c545672e95950fb6f82409fd10f", PRODUCT_CONTEXT.read_text())
        self.assertIn("e6d4d70ada739c545672e95950fb6f82409fd10f", METRICS_CONTEXT.read_text())
        self.assertIn("AdPlacement.toMetadata(...)", METRICS_ANALYSIS.read_text())
        self.assertIn("CTA/placement helper 포함 release/tag/Play deploy", PRODUCT_CONTEXT.read_text())
        self.assertIn("CTA/placement helper 포함 release/tag/Play deploy", METRICS_CONTEXT.read_text())


if __name__ == "__main__":
    unittest.main()
