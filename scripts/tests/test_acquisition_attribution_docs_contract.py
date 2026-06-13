import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAY_STORE_ASO = REPO_ROOT / "docs" / "PLAY_STORE_ASO.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
REVIEW_PROMPT_FOLLOWTHROUGH = REPO_ROOT / "docs" / "REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md"

LATEST_TIMESTAMP = "2026-06-12T23:07:41Z"
LATEST_VALUES = [
    "573",
    "335",
    "238",
    "58.5%",
    "+110.7%",
    "-2.1%",
]
SCREEN_QUALITY_VALUES = [
    "48,361",
    "28,933",
    "59.8%",
    "294 / 830 = 35.4%",
]
STORE_PERFORMANCE_VALUES = [
    "2026-06-11",
    "store_performance",
    "1,112 -> 521",
    "-53.1%",
    "319 -> 154",
    "-51.7%",
    "28.7% -> 29.6%",
    "+0.9p",
    "97.0%",
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

        self.assertIn(LATEST_TIMESTAMP, product_context)
        self.assertIn("Play Console Search/Explore", product_context)
        self.assertIn("external/campaign", product_context)
        self.assertIn("ASO 회복", product_context)
        for value in LATEST_VALUES:
            self.assertIn(value, product_context)

        self.assertIn(LATEST_TIMESTAMP, review_prompt_followthrough)
        self.assertIn("Organic Search` 신규 사용자 | 238", review_prompt_followthrough)
        self.assertIn("Direct` 신규 사용자 | 335", review_prompt_followthrough)
        self.assertIn("58.5%", review_prompt_followthrough)
        self.assertIn("| baseline | TODO | TODO | TODO | 238 | TODO |", review_prompt_followthrough)
        self.assertNotIn("| baseline | TODO | TODO | TODO | 170 | TODO |", review_prompt_followthrough)
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
        self.assertIn("294 / 830 = 35.4%", version_gate)
        self.assertIn("충분", version_gate)
        self.assertIn("main/tag/Play 포함 여부", version_gate)

    def test_play_store_aso_keeps_manual_attribution_boundary_explicit(self):
        play_store_aso = PLAY_STORE_ASO.read_text()

        self.assertIn("2026-06-13 live readback", play_store_aso)
        self.assertIn("Direct 58.5% 과다 상태 유지", play_store_aso)
        self.assertIn("신규 유입 반등을 ASO 효과로 표현 금지", play_store_aso)
        self.assertIn("TODO: Play Console 수동 확인", play_store_aso)
        self.assertIn("TODO: 캠페인 운영 확인", play_store_aso)
        self.assertIn("활성 18명·세션 138회는 신규 획득 성과로 계산하지 않음", play_store_aso)

    def test_play_store_performance_readback_is_consistent_across_pm_context_docs(self):
        documents = [
            PLAY_STORE_ASO.read_text(),
            METRICS_ANALYSIS.read_text(),
            PRODUCT_DASHBOARD.read_text(),
            PRODUCT_CONTEXT.read_text(),
            METRICS_CONTEXT.read_text(),
        ]

        for document in documents:
            for value in STORE_PERFORMANCE_VALUES:
                self.assertIn(value, document)
            self.assertIn("전환율 하락", document)
            self.assertIn("방문자", document)
            self.assertIn("Search/Explore", document)
            self.assertIn("external", document)

    def test_play_store_aso_records_cloud_storage_store_performance_readback(self):
        play_store_aso = PLAY_STORE_ASO.read_text()

        self.assertIn("2026-06-11 Play Console Store performance Cloud Storage readback", play_store_aso)
        self.assertIn("gs://pubsite_prod_4966532873904693612/stats/store_performance", play_store_aso)
        self.assertIn("방문자 `1,112 -> 521` (`-53.1%`)", play_store_aso)
        self.assertIn("등록정보 획득 `319 -> 154` (`-51.7%`)", play_store_aso)
        self.assertIn("전환율 `28.7% -> 29.6%` (`+0.9p`)", play_store_aso)
        self.assertIn("전환율 하락이 아니라 store listing visitor 급감", play_store_aso)
        self.assertIn("Traffic source CSV는 `Other`가 전체 visitors의 `97.0%`", play_store_aso)
        self.assertIn("KR CVR `24.1% -> 27%+`", play_store_aso)
        self.assertIn("1차 목표 `30 visitors/day`", play_store_aso)


if __name__ == "__main__":
    unittest.main()
