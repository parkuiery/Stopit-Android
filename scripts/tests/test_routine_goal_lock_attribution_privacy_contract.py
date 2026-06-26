import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
FIREBASE_ANALYTICS = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "uiery"
    / "keep"
    / "analytics"
    / "FirebaseKeepAnalytics.kt"
)
KEEP_ANALYTICS = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "uiery"
    / "keep"
    / "analytics"
    / "KeepAnalytics.kt"
)
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
PRIVACY_CONTRACT = REPO_ROOT / "docs" / "BLOCKED_APP_ANALYTICS_PRIVACY_CONTRACT.md"


class RoutineGoalLockAttributionPrivacyContractTest(unittest.TestCase):
    def test_firebase_payload_does_not_export_routine_or_goal_lock_row_ids(self):
        source = FIREBASE_ANALYTICS.read_text()

        self.assertNotIn("put(KeepAnalyticsParam.ROUTINE_ID", source)
        self.assertNotIn("put(KeepAnalyticsParam.GOAL_LOCK_ID", source)
        self.assertIn("routineId/goalLockId remain local/debug attribution inputs", source)
        self.assertIn("external analytics on privacy-safe source/category/bucket dimensions only", source)

    def test_row_id_constants_are_marked_local_only_for_external_analytics(self):
        source = KEEP_ANALYTICS.read_text()

        self.assertIn("Do not export routine row IDs to GA4", source)
        self.assertIn("Do not export goal-lock row IDs to GA4", source)
        self.assertIn('const val ROUTINE_ID = "routine_id"', source)
        self.assertIn('const val GOAL_LOCK_ID = "goal_lock_id"', source)

    def test_high_traffic_docs_mark_row_ids_as_non_registration_targets(self):
        dictionary = EVENT_DICTIONARY.read_text()
        ga4_runbook = GA4_RUNBOOK.read_text()
        privacy_contract = PRIVACY_CONTRACT.read_text()

        for text in [dictionary, ga4_runbook, privacy_contract]:
            with self.subTest(doc=text[:40]):
                self.assertIn("#1079", text)
                self.assertIn("GA4 payload", text)
                self.assertIn("routine_id", text)
                self.assertIn("goal_lock_id", text)

        self.assertIn("Deprecated / 외부 GA4 전송 금지", dictionary)
        self.assertIn("GA4 custom dimension 신규 등록 대상이 아니다", dictionary)
        self.assertIn("`routine_id` | Deprecated / 금지", ga4_runbook)
        self.assertIn("`goal_lock_id` | Deprecated / 금지", ga4_runbook)
        self.assertIn("등록하지 않음", ga4_runbook)
        self.assertIn("repo-internal debug-state/instrumentation attribution만 허용", ga4_runbook)
        self.assertIn("routine/goal-lock row id도 외부 GA4 payload에서 제거", privacy_contract)

    def test_event_contracts_no_longer_list_row_ids_as_payload_params(self):
        dictionary = EVENT_DICTIONARY.read_text()

        forbidden_event_rows = [
            "| `first_core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_category_bucket`, `routine_id?`, `goal_lock_id?` |",
            "| `core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_category_bucket`, `routine_id?`, `goal_lock_id?` |",
            "| `app_block_intercepted` | `block_source`, `blocked_app_category_bucket`, `routine_id?`, `goal_lock_id?` |",
            "| Recommended | `routine_id` | `routine_id` |",
            "| Recommended | `goal_lock_id` | `goal_lock_id` |",
        ]
        for row in forbidden_event_rows:
            self.assertNotIn(row, dictionary)


if __name__ == "__main__":
    unittest.main()
