import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAY_STORE_ASO = REPO_ROOT / "docs" / "PLAY_STORE_ASO.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
REVIEW_PROMPT_FOLLOWTHROUGH = REPO_ROOT / "docs" / "REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md"

LATEST_TIMESTAMP = "2026-06-04T05:23:00Z"
LATEST_VALUES = [
    "464",
    "285",
    "179",
    "61.4%",
    "+28.9%",
    "-23.5%",
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

        self.assertIn("61.4%", product_context)
        self.assertIn("Play Console Search/Explore", product_context)
        self.assertIn("external/campaign", product_context)
        self.assertIn("ASO 회복", product_context)

        self.assertIn(LATEST_TIMESTAMP, review_prompt_followthrough)
        self.assertIn("Organic Search` 신규 사용자 | 179", review_prompt_followthrough)
        self.assertIn("Direct` 신규 사용자 | 285", review_prompt_followthrough)
        self.assertIn("61.4%", review_prompt_followthrough)
        self.assertIn("Play Console Search/Explore", review_prompt_followthrough)
        self.assertIn("external/campaign", review_prompt_followthrough)
        self.assertIn("ASO 회복으로 표현하지 않음", review_prompt_followthrough)

    def test_play_store_aso_keeps_manual_attribution_boundary_explicit(self):
        play_store_aso = PLAY_STORE_ASO.read_text()

        self.assertIn("2026-06-04 live readback", play_store_aso)
        self.assertIn("Direct 61.4% 유지", play_store_aso)
        self.assertIn("신규 유입 반등을 ASO 효과로 표현 금지", play_store_aso)
        self.assertIn("TODO: Play Console 수동 확인", play_store_aso)
        self.assertIn("TODO: 캠페인 운영 확인", play_store_aso)
        self.assertIn("활성 19명·세션 202회는 신규 유입 성과가 아니라", play_store_aso)


if __name__ == "__main__":
    unittest.main()
