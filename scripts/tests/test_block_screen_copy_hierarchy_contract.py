import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class BlockScreenCopyHierarchyContractTest(unittest.TestCase):
    def test_source_of_truth_document_defines_copy_action_and_safety_scope(self):
        doc = read("docs/BLOCK_SCREEN_COPY_HIERARCHY.md")

        required_phrases = [
            "Issue: #464",
            "BlockScreen.kt",
            "잠깐 멈춤 + 자기 통제 보조",
            "코칭 톤",
            "하던 일로 돌아가기",
            "긴급해제는 보조 안전 장치",
            "광고 간섭 제한",
            "first_core_action_completed",
            "emergencyUnlockActionUiState",
            "reason analytics 값은 기존 enum/저장 의미를 유지",
            "앱 이름, package, raw blocked app list, raw session timestamp, raw history",
            "PR body에는 `Refs #464`를 사용한다",
            "Closes #464",
            "python3 -m unittest scripts.tests.test_block_screen_copy_hierarchy_contract -v",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_high_traffic_docs_link_block_screen_contract(self):
        files = [
            "docs/AGENTS.md",
            "docs/PRODUCT_METRICS_DASHBOARD.md",
            "docs/METRICS_ANALYSIS.md",
            "docs/ops/stopit/product-context.md",
            "docs/ops/stopit/metrics-context.md",
        ]
        for relative_path in files:
            text = read(relative_path)
            self.assertIn("BLOCK_SCREEN_COPY_HIERARCHY.md", text, relative_path)
            self.assertIn("#464", text, relative_path)

    def test_event_dictionary_keeps_existing_analytics_order_and_no_new_payload_claim(self):
        dictionary = read("docs/ANALYTICS_EVENT_DICTIONARY.md")

        self.assertIn("docs/BLOCK_SCREEN_COPY_HIERARCHY.md", dictionary)
        self.assertIn("#464", dictionary)
        self.assertIn("app_block_intercepted", dictionary)
        self.assertIn("first_core_action_completed", dictionary)
        self.assertIn("core_action_completed", dictionary)
        self.assertIn("emergency_unlock_used", dictionary)
        self.assertIn("emergency_unlock_completed", dictionary)
        self.assertIn("새 이벤트를 요구하지 않는다", dictionary)
        self.assertIn("앱 이름/package/raw history", dictionary)

    def test_runtime_qa_checklist_has_repeatable_evidence_template(self):
        checklist = read("docs/QA_RUNTIME_CHECKLIST.md")

        required_phrases = [
            "Block screen copy/action hierarchy QA baseline",
            "Issue: #464",
            "Normal blocked state",
            "First core action state",
            "Emergency unlock available",
            "Emergency unlock disabled/limit reached",
            "banner ad does not outrank CTA/emergency status",
            "color-only state avoided",
            "python3 -m unittest scripts.tests.test_block_screen_copy_hierarchy_contract -v",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, checklist)

    def test_contract_does_not_claim_docs_lane_implemented_ui(self):
        source = read("docs/BLOCK_SCREEN_COPY_HIERARCHY.md")
        high_traffic = "\n".join(
            read(path)
            for path in [
                "docs/PRODUCT_METRICS_DASHBOARD.md",
                "docs/METRICS_ANALYSIS.md",
                "docs/ops/stopit/product-context.md",
                "docs/ops/stopit/metrics-context.md",
            ]
        )

        self.assertIn("이 docs-lane 산출물은 구현 완료가 아니다", source)
        self.assertIn("구현 완료가 아니라", high_traffic)
        self.assertIn("release/tag/Play deploy 후 14일", high_traffic)
        self.assertNotIn("Closes #464를 사용한다", source)


if __name__ == "__main__":
    unittest.main()
