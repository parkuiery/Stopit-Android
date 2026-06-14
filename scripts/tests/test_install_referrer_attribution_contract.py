import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
CONTRACT = REPO_ROOT / "docs" / "INSTALL_REFERRER_ATTRIBUTION_CONTRACT.md"
PLAY_STORE_ASO = REPO_ROOT / "docs" / "PLAY_STORE_ASO.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class InstallReferrerAttributionContractTest(unittest.TestCase):
    def test_contract_defines_privacy_safe_event_and_parameters(self):
        contract = CONTRACT.read_text()

        self.assertIn("Issue: #581", contract)
        self.assertIn("install_referrer_attribution_checked", contract)
        for parameter in [
            "referrer_status",
            "utm_source_type",
            "utm_medium_type",
            "campaign_bucket",
            "link_surface",
            "lookup_latency_bucket",
        ]:
            self.assertIn(parameter, contract)

        for forbidden in [
            "raw referrer URL",
            "검색어/search term",
            "raw URL/path",
            "arbitrary query key-value",
        ]:
            self.assertIn(forbidden, contract)

    def test_contract_is_linked_from_high_traffic_docs(self):
        for path in [
            PLAY_STORE_ASO,
            ANALYTICS_DICTIONARY,
            GA4_RUNBOOK,
            METRICS_ANALYSIS,
            PRODUCT_DASHBOARD,
            PRODUCT_CONTEXT,
            METRICS_CONTEXT,
            DOCS_AGENTS,
        ]:
            document = path.read_text()
            self.assertIn("docs/INSTALL_REFERRER_ATTRIBUTION_CONTRACT.md", document, path)
            self.assertIn("#581", document, path)

    def test_play_store_aso_names_landed_foothold_and_external_boundaries(self):
        play_store_aso = PLAY_STORE_ASO.read_text()

        self.assertIn("#581", play_store_aso)
        self.assertIn("PR #586", play_store_aso)
        self.assertIn("PR #590", play_store_aso)
        self.assertIn("parser/helper/analytics foothold", play_store_aso)
        self.assertIn("SDK provider/첫 실행 one-shot lookup", play_store_aso)
        self.assertIn("GA4 Admin", play_store_aso)
        self.assertIn("release/tag/Play deploy", play_store_aso)
        self.assertIn("14일/30일 readback", play_store_aso)

    def test_ga4_runbook_registers_install_referrer_dimensions_without_claiming_queryability(self):
        ga4_runbook = GA4_RUNBOOK.read_text()

        self.assertIn("Install Referrer / UTM attribution 조회성", ga4_runbook)
        for dimension in [
            "customEvent:referrer_status",
            "customEvent:utm_source_type",
            "customEvent:utm_medium_type",
            "customEvent:campaign_bucket",
            "customEvent:link_surface",
            "customEvent:lookup_latency_bucket",
        ]:
            self.assertIn(dimension, ga4_runbook)
        self.assertIn("PR #586의 parser/helper/analytics foothold", ga4_runbook)
        self.assertIn("PR #590", ga4_runbook)
        self.assertIn("SDK provider/첫 실행 one-shot lookup wiring", ga4_runbook)
        self.assertIn("metadata 확인", ga4_runbook)
        self.assertIn("release/tag/Play deploy", ga4_runbook)

    def test_contract_names_pr_586_landed_foothold_and_remaining_runtime_boundary(self):
        contract = CONTRACT.read_text()
        product_context = PRODUCT_CONTEXT.read_text()
        metrics_context = METRICS_CONTEXT.read_text()

        self.assertIn("PR #586(`7100a45c`)", contract)
        self.assertIn("PR #590(`ae26293a`)", contract)
        self.assertIn("AcquisitionAttributionParser", contract)
        self.assertIn("CampaignLinkBuilder", contract)
        self.assertIn("KeepAnalytics.trackInstallReferrerAttributionChecked", contract)
        self.assertIn("InstallReferrerAttributionReporter", contract)
        self.assertIn("PlayInstallReferrerLookup", contract)
        self.assertIn("PreferencesKey.HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION", contract)
        self.assertIn("문서 계약 + parser/helper/analytics foothold + SDK provider/runtime one-shot wiring 완료", contract)
        self.assertIn("PR #590(`ae26293a`)의 provider/first-run one-shot lookup wiring", product_context)
        self.assertIn("PR #590(`ae26293a`)으로 SDK provider/첫 실행 one-shot lookup wiring", metrics_context)
        self.assertNotIn("이번 QA package", contract)

    def test_contract_uses_latest_acquisition_readback_values(self):
        contract = CONTRACT.read_text()

        for expected in [
            "2026-06-14T00:09:03Z",
            "newUsers = 578",
            "Direct = 335 / 578 = 58.0%",
            "Organic Search = 243 / 578 = 42.0%",
            "Paid Search",
            "신규 0명",
            "활성 19명·세션 142회",
        ]:
            self.assertIn(expected, contract)

        for stale_value in [
            "Organic Search = 242 / 577 = 41.9%",
            "242 / 577",
        ]:
            self.assertNotIn(stale_value, contract)


if __name__ == "__main__":
    unittest.main()
