import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "ROUTINE_CREATION_CTA_EXPERIMENT.md"
RETENTION_BASELINE = REPO_ROOT / "docs" / "ROUTINE_RETENTION_COHORT_BASELINE.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class RoutineCreationCtaContractTest(unittest.TestCase):
    def test_runbook_locks_post_first_core_action_scope_and_guardrails(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #455",
            "post-first-core-action",
            "first_core_action_completed",
            "app_block_intercepted",
            "routines_count = 0",
            "routines_count = (not set)",
            "onboarding / pre-first-lock 사용자는 제외",
            "soft CTA",
            "Routine empty state",
            "광고 배너",
            "#407 루틴 템플릿 공유 CTA",
            "PR #533 / merge commit `b7cf06f20aaf551f513e0684142577149b1c4550`",
            "Home CTA·navigation·analytics 구현 완료",
            "Refs #455",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        forbidden_claims = [
            "Closes #455",
            "전체 retention 결론",
            "강제 CTA",
            "modal gate",
            "code-lane 구현 진행",
        ]
        for phrase in forbidden_claims:
            self.assertNotIn(phrase, runbook)

    def test_runbook_defines_privacy_safe_events_and_measurement_window(self):
        runbook = RUNBOOK.read_text()

        for event_name in [
            "routine_creation_cta_shown",
            "routine_creation_cta_clicked",
            "routine_creation_cta_dismissed",
        ]:
            self.assertIn(event_name, runbook)

        for parameter in [
            "surface",
            "activation_stage",
            "has_routine",
            "cta_variant",
        ]:
            self.assertIn(parameter, runbook)

        for phrase in [
            "14일 체크",
            "30일 체크",
            "routine_creation_cta_clicked users / routine_creation_cta_shown users",
            "routine_saved users / routine_creation_cta_clicked users",
            "emergency_unlock_completed users / active blocked users",
            "Play Console rating/review",
        ]:
            self.assertIn(phrase, runbook)

    def test_high_traffic_docs_link_to_routine_creation_cta_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            RETENTION_BASELINE.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]

        for document in documents:
            self.assertIn("ROUTINE_CREATION_CTA_EXPERIMENT.md", document)
            self.assertIn("#455", document)

    def test_product_dashboard_does_not_overclaim_routine_created_event(self):
        dashboard = PRODUCT_DASHBOARD.read_text()

        self.assertIn("첫 차단 후 루틴 CTA 전환", dashboard)
        self.assertIn("노출 cohort의 `routines_count >= 1` users", dashboard)
        self.assertIn("#810 저장 완료 계측", dashboard)
        self.assertIn("PR #813 Android wiring 이후에는 `routine_saved`를 CTA click → 실제 저장 완료 전환으로 본다", dashboard)
        self.assertNotIn("`routine_created` users / clicked users", dashboard)

    def test_analytics_dictionary_and_ga4_runbook_track_routine_cta_events(self):
        analytics = ANALYTICS_DICTIONARY.read_text()
        ga4_runbook = GA4_RUNBOOK.read_text()

        for event_name in [
            "routine_creation_cta_shown",
            "routine_creation_cta_clicked",
            "routine_creation_cta_dismissed",
        ]:
            self.assertIn(event_name, analytics)
            self.assertIn(event_name, ga4_runbook)

        for parameter in [
            "customEvent:surface",
            "customEvent:activation_stage",
            "customEvent:has_routine",
            "customEvent:cta_variant",
        ]:
            self.assertIn(parameter, ga4_runbook)

        self.assertIn("루틴 생성 CTA 조회성", ga4_runbook)
        self.assertIn("routine creation CTA check", ga4_runbook)
        self.assertIn("#455 PR #533 Home CTA UI/navigation/analytics 구현 완료", ga4_runbook)
        self.assertIn("#531 CTA UI wiring·공통 release·GA4 등록 전", ga4_runbook)
        self.assertIn("post_first_core_action", analytics)

    def test_qa_checklist_defines_non_intrusive_routine_cta_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        for phrase in [
            "루틴 생성 CTA soft experiment QA baseline",
            "Routine creation CTA QA evidence",
            "onboarding / pre-first-lock 사용자에게 미노출",
            "첫 차단 성공 이후 + 루틴 0개 사용자에게만 노출",
            "Routine empty state / 광고 배너 / 루틴 템플릿 공유 CTA 충돌 없음",
            "routine_creation_cta_shown",
        ]:
            self.assertIn(phrase, qa_checklist)


if __name__ == "__main__":
    unittest.main()
