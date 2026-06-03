import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAY_STORE_ASO = REPO_ROOT / "docs" / "PLAY_STORE_ASO.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"

LATEST_TIMESTAMP = "2026-06-03T19:07:42Z"
LATEST_VALUES = [
    "461",
    "284",
    "177",
    "61.6%",
    "+28.1%",
    "-24.3%",
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

        for document in detailed_documents:
            self.assertIn(LATEST_TIMESTAMP, document)
            self.assertIn("Play Console Search/Explore", document)
            self.assertIn("external/campaign", document)
            self.assertIn("ASO", document)
            for value in LATEST_VALUES:
                self.assertIn(value, document)

        self.assertIn("61.6%", product_context)
        self.assertIn("Play Console Search/Explore", product_context)
        self.assertIn("external/campaign", product_context)
        self.assertIn("ASO 회복", product_context)

    def test_play_store_aso_keeps_manual_attribution_boundary_explicit(self):
        play_store_aso = PLAY_STORE_ASO.read_text()

        self.assertIn("2026-06-03 third live readback", play_store_aso)
        self.assertIn("Direct 61.6% 유지", play_store_aso)
        self.assertIn("신규 유입 반등을 ASO 효과로 표현 금지", play_store_aso)
        self.assertIn("TODO: Play Console 수동 확인", play_store_aso)
        self.assertIn("TODO: 캠페인 운영 확인", play_store_aso)
        self.assertIn("활성 19명·세션 201회는 신규 유입 성과가 아니라", play_store_aso)


if __name__ == "__main__":
    unittest.main()
