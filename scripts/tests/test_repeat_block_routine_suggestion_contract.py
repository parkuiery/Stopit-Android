import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "REPEAT_BLOCK_ROUTINE_SUGGESTION.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class RepeatBlockRoutineSuggestionContractTest(unittest.TestCase):
    def test_runbook_locks_issue_scope_privacy_and_external_boundaries(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #531",
            "Refs #531",
            "반복 차단 패턴",
            "로컬에서 해석",
            "first_core_action_completed",
            "app_block_intercepted",
            "기존 활성 루틴과 겹치는 추천을 노출하지 않는다",
            "Usage Access 권한을 새 필수 전제로 요구하지 않는다",
            "origin/main",
            "SemVer tag",
            "Play deploy",
            "14일 체크",
            "30일 체크",
            "RepeatBlockRoutineSuggestionStore",
            "Home과 LockHistory 표면에서 추천 카드를 실제로 노출",
            "apply는 루틴 prefill navigation",
            "dismiss는 privacy-safe store",
            "raw app name/package/list/history/timestamp를 저장·전송하지 않는다",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        forbidden_claims = [
            "Closes #531",
            "서버/LLM",
            "raw 앱 이름을 GA4",
            "package name을 GA4",
        ]
        for phrase in forbidden_claims:
            self.assertNotIn(phrase, runbook)

    def test_runbook_defines_privacy_safe_events_parameters_and_buckets(self):
        runbook = RUNBOOK.read_text()

        for event_name in [
            "repeat_block_routine_suggestion_shown",
            "repeat_block_routine_suggestion_clicked",
            "repeat_block_routine_suggestion_dismissed",
            "repeat_block_routine_suggestion_applied",
        ]:
            self.assertIn(event_name, runbook)

        for parameter in [
            "surface",
            "suggestion_reason",
            "time_bucket",
            "day_type",
            "category_bucket",
            "repeat_count_bucket",
            "routine_coverage_state",
            "suggestion_variant",
        ]:
            self.assertIn(parameter, runbook)

        for phrase in [
            "morning",
            "afternoon",
            "evening",
            "night",
            "overnight",
            "weekday",
            "weekend",
            "social",
            "video",
            "game",
            "3_5",
            "6_10",
            "10_plus",
            "not_covered",
            "partially_covered",
        ]:
            self.assertIn(phrase, runbook)

        for privacy_phrase in [
            "앱 이름",
            "package name",
            "lockApplications",
            "raw session history",
            "raw timestamp",
            "raw retry count",
        ]:
            self.assertIn(privacy_phrase, runbook)

    def test_analytics_dictionary_and_ga4_runbook_track_repeat_block_suggestion_contract(self):
        analytics = ANALYTICS_DICTIONARY.read_text()
        ga4_runbook = GA4_RUNBOOK.read_text()

        for event_name in [
            "repeat_block_routine_suggestion_shown",
            "repeat_block_routine_suggestion_clicked",
            "repeat_block_routine_suggestion_dismissed",
            "repeat_block_routine_suggestion_applied",
        ]:
            self.assertIn(event_name, analytics)
            self.assertIn(event_name, ga4_runbook)

        for parameter in [
            "customEvent:suggestion_reason",
            "customEvent:time_bucket",
            "customEvent:day_type",
            "customEvent:category_bucket",
            "customEvent:repeat_count_bucket",
            "customEvent:routine_coverage_state",
            "customEvent:suggestion_variant",
        ]:
            self.assertIn(parameter, ga4_runbook)

        self.assertIn("반복 차단 기반 루틴 제안 조회성", ga4_runbook)
        self.assertIn("repeat block routine suggestion check", ga4_runbook)
        self.assertIn("PR #552", ga4_runbook)
        self.assertIn("PR #555", ga4_runbook)
        self.assertIn("RepeatBlockRoutineSuggestionStore", ga4_runbook)
        self.assertIn("Home/LockHistory", ga4_runbook)
        self.assertIn("REPEAT_BLOCK_ROUTINE_SUGGESTION.md", analytics)

    def test_high_traffic_docs_link_to_repeat_block_suggestion_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]

        for document in documents:
            self.assertIn("REPEAT_BLOCK_ROUTINE_SUGGESTION.md", document)
            self.assertIn("#531", document)

    def test_high_traffic_docs_reflect_prefill_and_dismiss_footholds_without_claiming_full_ui_release(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
        ]

        for document in documents:
            self.assertIn("PR #537", document)
            self.assertIn("local policy + analytics", document)
            self.assertIn("PR #552", document)
            self.assertIn("prefill", document)
            self.assertIn("PR #555", document)
            self.assertIn("RepeatBlockRoutineSuggestionStore", document)
            self.assertIn("Home/LockHistory", document)
            self.assertIn("release", document)
            self.assertIn("GA4", document)
            self.assertIn("수요 없음", document)

    def test_qa_checklist_defines_non_shaming_repeat_block_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        for phrase in [
            "반복 차단 기반 자동 루틴 제안 QA baseline",
            "Repeat block routine suggestion QA evidence",
            "RoutineNavigationTest",
            "RoutineBottomSheetViewModelTest",
            "RepeatBlockRoutineSuggestionStoreTest",
            "onboarding / pre-first-lock 사용자에게 미노출",
            "기존 활성 루틴과 겹치면 미노출",
            "raw app name / package / history / timestamp absent",
            "repeat_block_routine_suggestion_shown",
            "비난형 copy 금지",
        ]:
            self.assertIn(phrase, qa_checklist)


if __name__ == "__main__":
    unittest.main()
