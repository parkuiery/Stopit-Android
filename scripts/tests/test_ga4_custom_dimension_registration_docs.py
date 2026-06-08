import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"


class Ga4CustomDimensionRegistrationDocsTest(unittest.TestCase):
    def test_metadata_summary_does_not_collapse_custom_user_and_custom_event_queryability(self):
        dictionary = EVENT_DICTIONARY.read_text()

        self.assertIn("`routines_count`는 live metadata에서 확인된 `customUser` dimension", dictionary)
        self.assertIn("activation/review/ad 관련 `customEvent:*` 조회성", dictionary)
        self.assertNotIn("custom dimension은 `customUser:routines_count` 하나뿐", dictionary)

    def test_runbook_summary_splits_activation_review_reason_and_review_error_boundaries(self):
        runbook = GA4_RUNBOOK.read_text()

        self.assertIn("활성화용 `customEvent:*`와 review failure `customEvent:error`", runbook)
        self.assertIn("review skip `customEvent:reason`은 2026-06-02T18:06:45Z에 등록/조회 가능", runbook)
        self.assertNotIn("활성화/리뷰용 `customEvent:*` 차원/지표는 아직 보이지 않음", runbook)

    def test_blocked_app_package_is_not_a_new_registration_target(self):
        runbook = GA4_RUNBOOK.read_text()

        self.assertIn("`blocked_app_category_bucket` | Required dimension", runbook)
        self.assertIn("`blocked_app_package` | Deprecated / 금지", runbook)
        self.assertIn("#611 privacy 계약", runbook)
        self.assertNotIn("`blocked_app_package` | Required dimension", runbook)

    def test_screen_quality_boundary_mentions_docs_sync_pr_across_high_traffic_surfaces(self):
        required_snippets = [
            "PR #296/#318/#358",
            "6ceaecc4",
            "post-fix 성과가 아니라 release boundary 전 중간 smoke",
            "D+14 screen quality 재측정",
        ]
        for path in [GA4_RUNBOOK, EVENT_DICTIONARY, PRODUCT_DASHBOARD, METRICS_ANALYSIS, METRICS_CONTEXT]:
            text = path.read_text()
            with self.subTest(path=path.name):
                for snippet in required_snippets:
                    self.assertIn(snippet, text)


if __name__ == "__main__":
    unittest.main()
