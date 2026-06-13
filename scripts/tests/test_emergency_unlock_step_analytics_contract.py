import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class EmergencyUnlockStepAnalyticsContractTest(unittest.TestCase):
    def test_source_of_truth_defines_privacy_safe_events_and_boundaries(self):
        doc = read("docs/EMERGENCY_UNLOCK_STEP_ANALYTICS.md")

        required_phrases = [
            "Issue: #779",
            "emergency_unlock_step_viewed",
            "emergency_unlock_validation_blocked",
            "emergency_unlock_cancelled",
            "step_name",
            "validation_reason",
            "reason_required_enabled",
            "entry_surface",
            "cancel_source",
            "reason → app selection → duration → countdown",
            "reason-required OFF",
            "PR #783(`12c47108`)",
            "repo-internal Android wiring은 완료 상태",
            "PR #783 / merge commit `12c4710815746e79bde1a94fd5ad5f5d52fb81b7`",
            "GA4 Admin 등록 또는 release/tag/Play deploy 전의 0건은 adoption/UX 문제로 해석하지 않는다",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_privacy_forbidden_payloads_are_locked(self):
        doc = read("docs/EMERGENCY_UNLOCK_STEP_ANALYTICS.md")

        forbidden_phrases = [
            "custom reason 원문",
            "앱 이름",
            "앱 package",
            "선택 앱 list",
            "raw timestamp",
            "raw history/session snapshot",
            "raw duration option list",
            "설정 snapshot dump",
            "manualResetAtMillis",
        ]
        for phrase in forbidden_phrases:
            self.assertIn(phrase, doc)
        self.assertIn("허용되는 값은 enum/bool/bucket뿐", doc)

    def test_event_dictionary_and_ga4_runbook_include_step_contract(self):
        dictionary = read("docs/ANALYTICS_EVENT_DICTIONARY.md")
        ga4 = read("docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md")

        for text in [dictionary, ga4]:
            self.assertIn("EMERGENCY_UNLOCK_STEP_ANALYTICS.md", text)
            self.assertIn("#779", text)
            self.assertIn("emergency_unlock_step_viewed", text)
            self.assertIn("emergency_unlock_validation_blocked", text)
            self.assertIn("emergency_unlock_cancelled", text)
            self.assertIn("validation_reason", text)
            self.assertIn("reason_required_enabled", text)
            self.assertIn("entry_surface", text)
            self.assertIn("cancel_source", text)
            self.assertIn("custom reason 원문", text)
            self.assertIn("raw timestamp", text)
            self.assertIn("PR #783", text)
            self.assertIn("Android analytics wiring 완료", text)

    def test_high_traffic_docs_link_step_analytics_contract(self):
        files = [
            "docs/AGENTS.md",
            "docs/PRODUCT_METRICS_DASHBOARD.md",
            "docs/METRICS_ANALYSIS.md",
            "docs/ops/stopit/product-context.md",
            "docs/ops/stopit/metrics-context.md",
        ]
        for relative_path in files:
            text = read(relative_path)
            self.assertIn("EMERGENCY_UNLOCK_STEP_ANALYTICS.md", text, relative_path)
            self.assertIn("#779", text, relative_path)

    def test_qa_checklist_contains_repeatable_evidence_template(self):
        checklist = read("docs/QA_RUNTIME_CHECKLIST.md")

        required_phrases = [
            "Emergency unlock step analytics QA baseline",
            "Issue: #779",
            "python3 -m unittest scripts.tests.test_emergency_unlock_step_analytics_contract -v",
            "emergency_unlock_step_viewed",
            "emergency_unlock_validation_blocked",
            "emergency_unlock_cancelled",
            "reason-required ON/OFF",
            "no custom reason raw text",
            "no app name/package/list",
            "GA4 Admin metadata",
            "14-day readback",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, checklist)


if __name__ == "__main__":
    unittest.main()
