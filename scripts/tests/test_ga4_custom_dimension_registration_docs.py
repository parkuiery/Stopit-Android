import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"


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


if __name__ == "__main__":
    unittest.main()
