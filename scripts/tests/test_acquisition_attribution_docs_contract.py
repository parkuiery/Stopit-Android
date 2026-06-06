import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAY_STORE_ASO = REPO_ROOT / "docs" / "PLAY_STORE_ASO.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
REVIEW_PROMPT_FOLLOWTHROUGH = REPO_ROOT / "docs" / "REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md"

LATEST_TIMESTAMP = "2026-06-06T03:37:57Z"
LATEST_VALUES = [
    "537",
    "335",
    "202",
    "62.4%",
    "+39.8%",
    "-21.8%",
]
SCREEN_QUALITY_VALUES = [
    "38,338",
    "24,645",
    "64.3%",
    "145 / 767 = 18.9%",
]


class AcquisitionAttributionDocsContractTest(unittest.TestCase):
    def test_latest_acquisition_snapshot_is_consistent_across_high_traffic_docs(self):
        detailed_documents = [
            PLAY_STORE_ASO.read_text(),
            METRICS_ANALYSIS.read_text(),
            PRODUCT_DASHBOARD.read_text(),
            METRICS_CONTEXT.read_text(),
        ]
        product_context = PRODUCT_CONTEXT.read_text()
        review_prompt_followthrough = REVIEW_PROMPT_FOLLOWTHROUGH.read_text()

        for document in detailed_documents:
            self.assertIn(LATEST_TIMESTAMP, document)
            self.assertIn("Play Console Search/Explore", document)
            self.assertIn("external/campaign", document)
            self.assertIn("ASO", document)
            for value in LATEST_VALUES:
                self.assertIn(value, document)

        self.assertIn("62.4%", product_context)
        self.assertIn("Play Console Search/Explore", product_context)
        self.assertIn("external/campaign", product_context)
        self.assertIn("ASO 회복", product_context)

        self.assertIn(LATEST_TIMESTAMP, review_prompt_followthrough)
        self.assertIn("Organic Search` 신규 사용자 | 202", review_prompt_followthrough)
        self.assertIn("Direct` 신규 사용자 | 335", review_prompt_followthrough)
        self.assertIn("62.4%", review_prompt_followthrough)
        self.assertIn("Play Console Search/Explore", review_prompt_followthrough)
        self.assertIn("external/campaign", review_prompt_followthrough)
        self.assertIn("ASO 회복으로 표현하지 않음", review_prompt_followthrough)

    def test_screen_quality_and_version_guardrails_are_consistent_across_docs(self):
        screen_quality_documents = [
            (REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md").read_text(),
            (REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md").read_text(),
            METRICS_ANALYSIS.read_text(),
            PRODUCT_DASHBOARD.read_text(),
            METRICS_CONTEXT.read_text(),
        ]
        version_gate = (REPO_ROOT / "docs" / "VERSION_ADOPTION_METRICS_GATE.md").read_text()

        for document in screen_quality_documents:
            self.assertIn(LATEST_TIMESTAMP, document)
            for value in SCREEN_QUALITY_VALUES:
                self.assertIn(value, document)
            self.assertIn("release/tag/Play deploy", document)

        self.assertIn(LATEST_TIMESTAMP, version_gate)
        self.assertIn("145 / 767 = 18.9%", version_gate)
        self.assertIn("주의", version_gate)
        self.assertIn("30% 미만", version_gate)

    def test_play_store_aso_keeps_manual_attribution_boundary_explicit(self):
        play_store_aso = PLAY_STORE_ASO.read_text()

        self.assertIn("2026-06-06 live readback", play_store_aso)
        self.assertIn("Direct 62.4% 과다 상태 유지", play_store_aso)
        self.assertIn("신규 유입 반등을 ASO 효과로 표현 금지", play_store_aso)
        self.assertIn("TODO: Play Console 수동 확인", play_store_aso)
        self.assertIn("TODO: 캠페인 운영 확인", play_store_aso)
        self.assertIn("활성 18명·세션 180회는 신규 유입 성과가 아니라", play_store_aso)


if __name__ == "__main__":
    unittest.main()
