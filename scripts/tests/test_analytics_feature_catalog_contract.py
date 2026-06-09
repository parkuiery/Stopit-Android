import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
ANALYTICS_DIR = REPO_ROOT / "app/src/main/java/com/uiery/keep/analytics"
KEEP_ANALYTICS = ANALYTICS_DIR / "KeepAnalytics.kt"
FIREBASE_KEEP_ANALYTICS = ANALYTICS_DIR / "FirebaseKeepAnalytics.kt"
ROUTINE_CATALOG = ANALYTICS_DIR / "routine/RoutineAnalyticsEvents.kt"

ROUTINE_EVENT_TOKENS = (
    "ROUTINE_TEMPLATE_SHARE_TAPPED",
    "ROUTINE_TEMPLATE_SHARE_SHEET_OPENED",
    "ROUTINE_TEMPLATE_SHARE_FAILED",
    "REPEAT_BLOCK_ROUTINE_SUGGESTION_SHOWN",
    "REPEAT_BLOCK_ROUTINE_SUGGESTION_CLICKED",
    "REPEAT_BLOCK_ROUTINE_SUGGESTION_DISMISSED",
    "REPEAT_BLOCK_ROUTINE_SUGGESTION_APPLIED",
)

ROUTINE_PARAM_TOKENS = (
    "TEMPLATE_CATEGORY",
    "REPEAT_DAYS_BUCKET",
    "TIME_WINDOW_BUCKET",
    "ROUTINE_NAME_INCLUDED",
    "SUGGESTION_REASON",
    "TIME_BUCKET",
    "DAY_TYPE",
    "CATEGORY_BUCKET",
    "REPEAT_COUNT_BUCKET",
    "ROUTINE_COVERAGE_STATE",
    "SUGGESTION_VARIANT",
)


class AnalyticsFeatureCatalogContractTest(unittest.TestCase):
    def test_routine_event_schema_lives_in_routine_catalog_not_central_contract(self):
        keep_source = KEEP_ANALYTICS.read_text()
        routine_source = ROUTINE_CATALOG.read_text()

        for token in ROUTINE_EVENT_TOKENS + ROUTINE_PARAM_TOKENS:
            declaration = f"const val {token}"
            with self.subTest(token=token):
                self.assertNotIn(declaration, keep_source)
                self.assertIn(declaration, routine_source)

    def test_firebase_adapter_delegates_routine_schema_to_catalog(self):
        firebase_source = FIREBASE_KEEP_ANALYTICS.read_text()

        self.assertIn("RoutineAnalyticsEvents", firebase_source)
        forbidden_direct_schema = (
            r"KeepAnalyticsEvent\.ROUTINE_TEMPLATE_SHARE_",
            r"KeepAnalyticsEvent\.REPEAT_BLOCK_ROUTINE_SUGGESTION_",
            r"KeepAnalyticsParam\.TEMPLATE_CATEGORY",
            r"KeepAnalyticsParam\.SUGGESTION_REASON",
        )
        for pattern in forbidden_direct_schema:
            with self.subTest(pattern=pattern):
                self.assertIsNone(re.search(pattern, firebase_source))

    def test_keep_analytics_exposes_generic_event_sink_for_feature_catalogs(self):
        keep_source = KEEP_ANALYTICS.read_text()
        self.assertIn("fun log(event: AnalyticsEvent)", keep_source)
        self.assertIn("logEvent(name = event.name, params = event.params)", keep_source)


if __name__ == "__main__":
    unittest.main()
