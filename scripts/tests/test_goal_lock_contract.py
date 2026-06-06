import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "GOAL_LOCK_MVP.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
GOAL_LOCK_CREATION_SCREEN = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "feature" / "goallock" / "GoalLockCreationScreen.kt"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class GoalLockContractTest(unittest.TestCase):
    def test_runbook_locks_goal_lock_mvp_scope(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #417",
            "목표 잠금",
            "하루종일 잠금",
            "특정 시간만 잠금",
            "preset_days",
            "custom_days",
            "end_date",
            "홈 진행 카드/섹션",
            "강력 목표 잠금",
            "MVP에서는 제외",
            "GoalLockPolicy",
            "app/src/main/java/com/uiery/keep/feature/goallock/GoalLockPolicy.kt",
            "GoalLockDao",
            "GoalLockEntity",
            "MIGRATION_4_5",
            "GoalLockCreationScreen",
            "GoalLockCreationRoute",
            "BlockingStateStore",
            "목표별 선택 앱 편집",
            "CategoryBottomSheetContent",
            "full picker UX",
            "직접 일수(`custom_days`) 입력",
            "ISO 종료 날짜(`end_date`) 입력",
            "Home expiration completion foothold",
            "PR #489",
            "goal_lock_completed",
            "Accessibility runtime QA foothold",
            "KeepAccessibilityServiceIntegrationTest.activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution",
            "block_source",
            "goal_lock_id",
            "Closes #417",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        forbidden_guidance = [
            "긴급 해제 횟수 자동 회복 OFF를 MVP에 포함",
            "목표 이름 원문을 analytics",
            "app package를 analytics",
            "사용자를 가두",
        ]
        for phrase in forbidden_guidance:
            self.assertNotIn(phrase, runbook)

    def test_analytics_dictionary_contains_goal_lock_events_and_safe_parameters(self):
        analytics = ANALYTICS_DICTIONARY.read_text()

        for event_name in [
            "goal_lock_create_started",
            "goal_lock_created",
            "goal_lock_completed",
            "goal_lock_ended_early",
            "goal_lock_updated",
        ]:
            self.assertIn(event_name, analytics)

        for parameter in [
            "duration_selection_type",
            "lock_mode",
            "selected_app_count_bucket",
            "goal_name_type",
            "duration_days_bucket",
            "elapsed_days_bucket",
        ]:
            self.assertIn(parameter, analytics)

        self.assertIn("GOAL_LOCK_MVP.md", analytics)
        self.assertIn("목표 이름 원문/app package/app label 금지", analytics)

    def test_high_traffic_docs_link_to_goal_lock_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]

        for document in documents:
            self.assertIn("GOAL_LOCK_MVP.md", document)
            self.assertIn("#417", document)

    def test_ga4_registration_runbook_tracks_goal_lock_parameters(self):
        ga4_runbook = GA4_RUNBOOK.read_text()

        for parameter in [
            "customEvent:duration_selection_type",
            "customEvent:lock_mode",
            "customEvent:selected_app_count_bucket",
            "customEvent:goal_name_type",
            "customEvent:duration_days_bucket",
            "customEvent:elapsed_days_bucket",
        ]:
            self.assertIn(parameter, ga4_runbook)

        self.assertIn("목표 잠금 조회성", ga4_runbook)
        self.assertIn("goal lock check", ga4_runbook)
        self.assertIn("목표 이름 원문/app package/app label", ga4_runbook)

    def test_qa_checklist_defines_goal_lock_runtime_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        self.assertIn("목표 잠금 runtime QA baseline", qa_checklist)
        self.assertIn("GoalLockPolicyTest", qa_checklist)
        self.assertIn("FirebaseKeepAnalyticsTest.goalLockCreatedUsesSafeBucketedParamsOnly", qa_checklist)
        self.assertIn("GoalLockPersistenceMapperTest", qa_checklist)
        self.assertIn("GoalLockCreationViewModelTest", qa_checklist)
        self.assertIn("GoalLockSelectedAppUiItemTest", qa_checklist)
        self.assertIn("custom days/end date 기간 선택", qa_checklist)
        self.assertIn("KeepAppNavigationPolicyTest", qa_checklist)
        self.assertIn("GoalLockCreationRoute", qa_checklist)
        self.assertIn("목표별 선택 앱 편집", qa_checklist)
        self.assertIn("HomeViewModelActivationAnalyticsTest.activeGoalLockExposesHomeProgressCardState", qa_checklist)
        self.assertIn("HomeViewModelActivationAnalyticsTest.expiredActiveGoalLockIsCompletedFromHomeCardLoadAndTrackedOnce", qa_checklist)
        self.assertIn("GoalLockDetailViewModelTest", qa_checklist)
        self.assertIn("FirebaseKeepAnalyticsTest.goalLockEndedEarlyUsesSafeBucketedParamsOnly", qa_checklist)
        self.assertIn("Goal lock QA evidence", qa_checklist)
        self.assertIn("all-day / scheduled / expiration", qa_checklist)
        self.assertIn("KeepAccessibilityServiceIntegrationTest.activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution", qa_checklist)
        self.assertIn("block_source=goal_lock", qa_checklist)

    def test_context_pack_does_not_describe_goal_lock_as_pre_implementation_only(self):
        product_context = PRODUCT_CONTEXT.read_text()
        metrics_context = METRICS_CONTEXT.read_text()

        for document in [product_context, metrics_context]:
            self.assertIn("policy/persistence/creation UI/navigation/Home/Accessibility blocking/detail/early-end/Home completion foothold", document)
            self.assertIn("device/emulator runtime QA evidence", document)
            self.assertNotIn("#417 목표 잠금 MVP 계약. 기간 기반 `all_day`/`scheduled` 장기 잠금, Home 진행 카드/섹션, privacy-safe analytics, runtime QA baseline을 구현 전 handoff", document)

        dashboard = PRODUCT_DASHBOARD.read_text()
        self.assertIn("Home completion foothold", dashboard)
        self.assertIn("`develop` 반영 상태", dashboard)

    def test_goal_lock_creation_uses_picker_style_app_selection(self):
        screen = GOAL_LOCK_CREATION_SCREEN.read_text()

        self.assertIn("CategoryBottomSheetContent", screen)
        self.assertIn("ModalBottomSheet", screen)
        self.assertIn("onSelectApps", screen)
        self.assertIn("viewModel.setSelectedApps(selectedApps)", screen)
        self.assertNotIn("패키지 직접 추가", screen)
        self.assertNotIn("onAddSelectedAppPackage", screen)

    def test_runbook_points_future_lanes_to_contract_regression(self):
        runbook = RUNBOOK.read_text()

        self.assertIn(
            "python3 -m unittest scripts.tests.test_goal_lock_contract -v",
            runbook,
        )
        self.assertIn("goal-lock contract regression", runbook)


if __name__ == "__main__":
    unittest.main()
