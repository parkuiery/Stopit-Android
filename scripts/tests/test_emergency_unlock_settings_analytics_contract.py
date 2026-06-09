import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class EmergencyUnlockSettingsAnalyticsContractTest(unittest.TestCase):
    def test_source_of_truth_document_defines_events_parameters_and_boundaries(self):
        doc = read("docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md")

        required_phrases = [
            "Issue: #694",
            "emergency_unlock_settings_changed",
            "emergency_unlock_manual_reset_requested",
            "setting_name",
            "value_bucket",
            "duration_count_bucket",
            "remaining_unlocks_bucket",
            "refill_mode",
            "source",
            "reset_result",
            "EmergencyUnlockSettingsViewModel",
            "EmergencyUnlockSettingsStore",
            "GA4 Admin 등록",
            "release/tag/Play deploy",
            "14일",
            "30일",
            "Refs #694",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_contract_is_privacy_safe_and_forbids_raw_sensitive_payloads(self):
        doc = read("docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md")

        required_guardrails = [
            "custom reason 원문",
            "display label/custom text",
            "app package",
            "앱 이름",
            "앱 목록",
            "raw timestamp",
            "raw lock/session history",
            "manualResetAtMillis",
            "설정 snapshot dump",
        ]
        for phrase in required_guardrails:
            self.assertIn(phrase, doc)

        self.assertIn("금지", doc)
        self.assertIn("enum/bucket", doc)

    def test_event_dictionary_and_ga4_runbook_register_required_parameters(self):
        dictionary = read("docs/ANALYTICS_EVENT_DICTIONARY.md")
        ga4 = read("docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md")

        for text in [dictionary, ga4]:
            self.assertIn("docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md", text)
            self.assertIn("#694", text)
            self.assertIn("emergency_unlock_settings_changed", text)
            self.assertIn("emergency_unlock_manual_reset_requested", text)
            for parameter in [
                "setting_name",
                "value_bucket",
                "refill_mode",
                "duration_count_bucket",
                "remaining_unlocks_bucket",
            ]:
                self.assertIn(parameter, text)

        self.assertIn("customEvent:setting_name", ga4)
        self.assertIn("customEvent:value_bucket", ga4)
        self.assertIn("customEvent:refill_mode", ga4)
        self.assertIn("customEvent:duration_count_bucket", ga4)
        self.assertIn("customEvent:remaining_unlocks_bucket", ga4)
        self.assertIn("customEvent:reset_result", ga4)

    def test_high_traffic_product_and_ops_docs_link_contract(self):
        files = [
            "docs/AGENTS.md",
            "docs/PRODUCT_METRICS_DASHBOARD.md",
            "docs/METRICS_ANALYSIS.md",
            "docs/ops/stopit/product-context.md",
            "docs/ops/stopit/metrics-context.md",
        ]
        for relative_path in files:
            text = read(relative_path)
            self.assertIn("EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md", text, relative_path)
            self.assertIn("#694", text, relative_path)

    def test_runtime_qa_checklist_has_repeatable_evidence_template(self):
        checklist = read("docs/QA_RUNTIME_CHECKLIST.md")

        required_phrases = [
            "Emergency unlock settings analytics QA baseline",
            "Source of truth: `docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md`",
            "Issue: #694",
            "enabled ON/OFF",
            "daily limit bucket",
            "duration option count bucket",
            "reason required ON/OFF",
            "refill mode daily/manual",
            "manual reset request",
            "customEvent:setting_name",
            "customEvent:value_bucket",
            "customEvent:refill_mode",
            "customEvent:duration_count_bucket",
            "customEvent:remaining_unlocks_bucket",
            "python3 -m unittest scripts.tests.test_emergency_unlock_settings_analytics_contract -v",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, checklist)

    def test_contract_does_not_over_claim_implementation_or_live_adoption(self):
        doc = read("docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md")
        high_traffic = "\n".join(
            read(path)
            for path in [
                "docs/METRICS_ANALYSIS.md",
                "docs/ops/stopit/product-context.md",
                "docs/ops/stopit/metrics-context.md",
            ]
        )

        self.assertIn("현재는 문서 계약 단계", doc)
        self.assertIn("Android wiring 전", doc)
        self.assertIn("live 0건은 수요 없음으로 해석하지 않는다", doc)
        self.assertIn("live event 0건은 미계측으로 본다", high_traffic)
        self.assertNotIn("Closes #694", doc)


if __name__ == "__main__":
    unittest.main()
