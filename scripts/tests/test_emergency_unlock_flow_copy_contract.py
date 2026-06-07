import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class EmergencyUnlockFlowCopyContractTest(unittest.TestCase):
    def test_source_of_truth_document_defines_flow_copy_and_safety_scope(self):
        doc = read("docs/EMERGENCY_UNLOCK_FLOW_COPY.md")

        required_phrases = [
            "Issue: #467",
            "PR #517(`572eb559`)",
            "PR #575(`1a7c677`)",
            "PR #593(`79fdee8`)",
            "EmergencyUnlockBottomSheetContent.kt",
            "reason → app selection → duration → countdown",
            "필요하면 빠르게, 습관이면 한 번 멈춤",
            "짧은 label + 보조 설명",
            "selected reason reflection helper",
            "disabled reason은 보이는 copy",
            "reasonStepEnabled=false",
            "Countdown은 안전한 유예",
            "custom reason 원문, 앱 이름, package list, raw timestamp/history",
            "emergency_unlock_completed(reason, duration_minutes, remaining_unlocks)",
            "reason distribution confidence",
            "PR body에는 `Refs #467`를 사용한다",
            "Closes #467",
            "python3 -m unittest scripts.tests.test_emergency_unlock_flow_copy_contract -v",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_reason_taxonomy_preserves_existing_payload_keys(self):
        doc = read("docs/EMERGENCY_UNLOCK_FLOW_COPY.md")

        for key in ["work", "contact", "info", "habit", "boredom", "other"]:
            self.assertIn(f"`{key}`", doc)
        self.assertIn("기존 payload key는 유지한다", doc)
        self.assertIn("enum meaning을 바꾸면 안 된다", doc)

    def test_high_traffic_docs_link_emergency_unlock_contract(self):
        files = [
            "docs/AGENTS.md",
            "docs/PRODUCT_METRICS_DASHBOARD.md",
            "docs/METRICS_ANALYSIS.md",
            "docs/ops/stopit/product-context.md",
            "docs/ops/stopit/metrics-context.md",
        ]
        for relative_path in files:
            text = read(relative_path)
            self.assertIn("EMERGENCY_UNLOCK_FLOW_COPY.md", text, relative_path)
            self.assertIn("#467", text, relative_path)

    def test_event_dictionary_keeps_existing_analytics_contract_and_privacy_guardrails(self):
        dictionary = read("docs/ANALYTICS_EVENT_DICTIONARY.md")

        self.assertIn("docs/EMERGENCY_UNLOCK_FLOW_COPY.md", dictionary)
        self.assertIn("#467", dictionary)
        self.assertIn("emergency_unlock_used", dictionary)
        self.assertIn("emergency_unlock_completed", dictionary)
        self.assertIn("새 이벤트를 요구하지 않는다", dictionary)
        self.assertIn("existing enum key", dictionary)
        self.assertIn("custom reason 원문", dictionary)
        self.assertIn("앱 이름", dictionary)
        self.assertIn("package", dictionary)
        self.assertIn("raw history", dictionary)

    def test_runtime_qa_checklist_has_repeatable_evidence_template(self):
        checklist = read("docs/QA_RUNTIME_CHECKLIST.md")

        required_phrases = [
            "Emergency unlock flow copy/step QA baseline",
            "Issue: #467",
            "Reason required ON",
            "Reason required OFF",
            "App selection/duration/countdown",
            "selected reason maps to existing enum key",
            "selected reason reflection helper reinforces intentional use",
            "no app name/package/custom reason/raw history added to analytics",
            "python3 -m unittest scripts.tests.test_emergency_unlock_flow_copy_contract -v",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, checklist)

    def test_contract_does_not_claim_docs_lane_implemented_ui(self):
        source = read("docs/EMERGENCY_UNLOCK_FLOW_COPY.md")
        high_traffic = "\n".join(
            read(path)
            for path in [
                "docs/PRODUCT_METRICS_DASHBOARD.md",
                "docs/METRICS_ANALYSIS.md",
                "docs/ops/stopit/product-context.md",
                "docs/ops/stopit/metrics-context.md",
            ]
        )

        self.assertIn("PR #517(`572eb559`)", source)
        self.assertIn("PR #575(`1a7c677`)", source)
        self.assertIn("PR #593(`79fdee8`)", source)
        self.assertIn("countdown TalkBack baseline", high_traffic)
        self.assertIn("Compose UI flow baseline", high_traffic)
        self.assertIn("`develop`에 반영", high_traffic)
        self.assertIn("release/tag/Play deploy", high_traffic)
        self.assertNotIn("Closes #467를 사용한다", source)

    def test_runtime_qa_checklist_tracks_post_implementation_evidence_boundary(self):
        checklist = read("docs/QA_RUNTIME_CHECKLIST.md")

        self.assertIn("PR #517(`572eb559`)", checklist)
        self.assertIn("PR #575(`1a7c677`)", checklist)
        self.assertIn("PR #593(`79fdee8`)", checklist)
        self.assertIn("device/screenshot/TalkBack evidence", checklist)
        self.assertIn("PR #517 merge commit included in tested build", checklist)
        self.assertIn("PR #575 UI QA baseline included in tested build", checklist)
        self.assertIn(
            "countdown TalkBack includes waiting copy, remaining seconds, and cancel affordance together",
            checklist,
        )
        self.assertIn("python3 -m unittest scripts.tests.test_emergency_unlock_flow_copy_contract -v", checklist)

if __name__ == "__main__":
    unittest.main()
