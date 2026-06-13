import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
ROUTINE_CTA_RUNBOOK = REPO_ROOT / "docs" / "ROUTINE_CREATION_CTA_EXPERIMENT.md"
REPEAT_BLOCK_RUNBOOK = REPO_ROOT / "docs" / "REPEAT_BLOCK_ROUTINE_SUGGESTION.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"


class RoutineSavedAnalyticsContractTest(unittest.TestCase):
    def test_event_dictionary_defines_generic_routine_saved_contract(self):
        dictionary = EVENT_DICTIONARY.read_text()

        self.assertIn("### 루틴 저장 완료 이벤트", dictionary)
        self.assertIn("Issue: #810", dictionary)
        self.assertIn("`routine_saved`", dictionary)
        self.assertIn("`entry_surface`, `creation_source`, `selected_app_count_bucket`, `repeat_days_bucket`, `time_window_bucket`, `schedule_state`", dictionary)

        for phrase in [
            "수동 루틴 생성과 CTA/추천 prefill 저장 완료를 같은 저장 완료 분모로 묶는다",
            "`repeat_block_routine_suggestion_applied`는 추천 적용 이벤트로 유지하고, `routine_saved`는 generic 저장 완료 이벤트로 함께 전송한다",
            "`lock_scheduled`는 예약 성공 이벤트이므로 exact alarm 권한 부족으로 disabled 저장된 루틴의 저장 완료 분모로 쓰지 않는다",
            "raw start/end time",
            "routine name",
            "app package/name/list",
            "routine id",
            "raw history",
        ]:
            self.assertIn(phrase, dictionary)

    def test_ga4_runbook_tracks_required_dimensions_and_release_boundary(self):
        runbook = GA4_RUNBOOK.read_text()

        for row_fragment in [
            "`entry_surface` | `routine_saved`",
            "`creation_source` | `routine_saved`",
            "`selected_app_count_bucket` | `routine_saved`",
            "`repeat_days_bucket` | `routine_saved`",
            "`time_window_bucket` | `routine_saved`",
            "`schedule_state` | `routine_saved`",
            "#810 docs 계약 + PR #813 Android wiring 완료 / GA4 등록·release 전",
            "routine saved completion check",
            "routine_saved(creation_source=post_first_block_cta) users / routine_creation_cta_clicked users",
            "routine_saved(creation_source=repeat_block_prefill) users / repeat_block_routine_suggestion_clicked users",
        ]:
            self.assertIn(row_fragment, runbook)

    def test_dictionary_registers_routine_saved_dimension_meanings(self):
        dictionary = EVENT_DICTIONARY.read_text()

        for phrase in [
            "`routine/home_secondary/home/lock_history/post_block_success/performance_report/repeat_block_suggestion`",
            "루틴 저장 완료가 수동 생성, post-first-block CTA, repeat-block prefill 중 어디서 온 전환인지 분리",
            "`routine_saved`는 반복 요일 수(`1/2_3/4_6/7`)",
            "`routine_saved`는 `morning/afternoon/evening/night/overnight/all_day/custom`",
            "exact alarm 권한 부족 disabled 저장은 `disabled_exact_alarm_missing`",
        ]:
            self.assertIn(phrase, dictionary)

    def test_high_traffic_docs_use_routine_saved_without_overclaiming_code(self):
        documents = [
            ROUTINE_CTA_RUNBOOK.read_text(),
            REPEAT_BLOCK_RUNBOOK.read_text(),
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
            METRICS_CONTEXT.read_text(),
        ]

        for document in documents:
            self.assertIn("routine_saved", document)
            self.assertIn("#810", document)
            self.assertIn("GA4 Admin", document)
            self.assertIn("release/tag/Play deploy", document)

        dashboard = PRODUCT_DASHBOARD.read_text()
        self.assertIn("`routine_saved(creation_source=post_first_block_cta)` users / `routine_creation_cta_clicked` users", dashboard)
        self.assertIn("PR #813 Android wiring 이후에는 `routine_saved`를 CTA click → 실제 저장 완료 전환으로 본다", dashboard)

        cta_runbook = ROUTINE_CTA_RUNBOOK.read_text()
        self.assertIn("filtered by `creation_source=post_first_block_cta`", cta_runbook)

        repeat_runbook = REPEAT_BLOCK_RUNBOOK.read_text()
        self.assertIn("`routine_saved(creation_source=repeat_block_prefill)` users / `repeat_block_routine_suggestion_clicked` users", repeat_runbook)
        self.assertIn("`entry_surface=repeat_block_suggestion|home|lock_history|performance_report`", repeat_runbook)

    def test_runtime_qa_checklist_names_forbidden_payload_and_evidence_template(self):
        checklist = QA_RUNTIME_CHECKLIST.read_text()

        for phrase in [
            "Routine saved analytics QA baseline",
            "manual routine creation",
            "post_first_block_cta",
            "repeat_block_prefill",
            "disabled_exact_alarm_missing",
            "raw routine name / app package / app list / raw time / routine id absent",
            "python3 -m unittest scripts.tests.test_routine_saved_analytics_contract -v",
        ]:
            self.assertIn(phrase, checklist)


if __name__ == "__main__":
    unittest.main()
