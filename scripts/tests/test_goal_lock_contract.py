import pathlib
import re
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "GOAL_LOCK_MVP.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
GOAL_LOCK_CREATION_SCREEN = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "feature" / "goallock" / "GoalLockCreationScreen.kt"
GOAL_LOCK_DETAIL_SCREEN = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "feature" / "goallock" / "GoalLockDetailScreen.kt"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
RES_ROOT = REPO_ROOT / "app" / "src" / "main" / "res"


def _string_resource_names(strings_file: pathlib.Path) -> set[str]:
    tree = ET.parse(strings_file)
    return {element.attrib["name"] for element in tree.getroot().findall("string")}


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
            "detail duration / lock-mode update foothold",
            "goal_lock_updated(changed_field=duration)",
            "goal_lock_updated(changed_field=schedule)",
            "goal_lock_updated(changed_field=lock_mode)",
            "Accessibility runtime QA foothold",
            "KeepAccessibilityServiceIntegrationTest.activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution",
            "KeepAccessibilityServiceIntegrationTest.activeScheduledGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution",
            "KeepAccessibilityServiceIntegrationTest.expiredGoalLockWithoutManualKeep_keepsTargetForegroundWithoutGoalLockAttribution",
            "block_source",
            "goal_lock_id",
            "Closes #417",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        self.assertIn("시작일 당일 새벽의 전날 spillover 구간", runbook)
        self.assertIn("종료일 다음날 새벽의 spillover 구간", runbook)
        self.assertIn("window 종료 시각부터 차단을 멈춘다", runbook)

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
        self.assertIn("locale-independent preset key", analytics)
        self.assertIn("직접 입력한 경우는 `custom`", analytics)
        self.assertIn("목표 잠금 코드 계약", analytics)
        self.assertIn("app/src/main/java/com/uiery/keep/domain/goallock/GoalLockPolicy.kt", analytics)
        self.assertIn("GoalLockCreationViewModel.kt", analytics)
        self.assertIn("GoalLockDetailViewModel.kt", analytics)
        self.assertIn("GoalLockAnalytics.kt", analytics)
        self.assertIn("KeepAccessibilityService", analytics)
        self.assertIn("repo-internal `develop` 상태", analytics)
        self.assertNotIn("목표 잠금 구현 후보", analytics)
        self.assertNotIn("GoalLockPolicy` / 목표 잠금 model·repository·Home card ViewModel(구현 시 추가)", analytics)

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
        self.assertIn("creation ViewModel/analytics 계약이 `develop`에 반영됨", ga4_runbook)
        self.assertIn("detail duration·schedule·lock-mode update runtime call이 `develop`에 반영됨", ga4_runbook)
        self.assertNotIn("#417 code-lane 생성 ViewModel/analytics 계약 추가", ga4_runbook)
        self.assertNotIn("`goal_lock_created` 코드 계약 추가, detail 종료 path", ga4_runbook)

    def test_qa_checklist_defines_goal_lock_runtime_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        self.assertIn("목표 잠금 runtime QA baseline", qa_checklist)
        self.assertIn("GoalLockPolicyTest", qa_checklist)
        self.assertIn("시작일 당일 새벽", qa_checklist)
        self.assertIn("익일 새벽 spillover", qa_checklist)
        self.assertIn("FirebaseKeepAnalyticsTest.goalLockCreatedUsesSafeBucketedParamsOnly", qa_checklist)
        self.assertIn("GoalLockPersistenceMapperTest", qa_checklist)
        self.assertIn("GoalLockCreationViewModelTest", qa_checklist)
        self.assertIn("GoalLockSelectedAppUiItemTest", qa_checklist)
        self.assertIn("GoalLockCreationContentIntegrationTest", qa_checklist)
        self.assertIn("GoalLockAccessibilityDescriptionTest", qa_checklist)
        self.assertIn("summary contentDescription", qa_checklist)
        self.assertIn("compact-height", qa_checklist)
        self.assertIn("목표 잠금 시작", qa_checklist)
        self.assertIn("GoalLockDetailContentIntegrationTest", qa_checklist)
        self.assertIn("목표 잠금 종료", qa_checklist)
        self.assertIn("custom days/end date 기간 선택", qa_checklist)
        self.assertIn("KeepAppNavigationPolicyTest", qa_checklist)
        self.assertIn("GoalLockCreationRoute", qa_checklist)
        self.assertIn("목표별 선택 앱 편집", qa_checklist)
        self.assertIn("HomeViewModelActivationAnalyticsTest.activeGoalLockExposesHomeProgressCardState", qa_checklist)
        self.assertIn("HomeViewModelActivationAnalyticsTest.expiredActiveGoalLockIsCompletedFromHomeCardLoadAndTrackedOnce", qa_checklist)
        self.assertIn("GoalLockDetailViewModelTest", qa_checklist)
        self.assertIn("duration update recalculates end date", qa_checklist)
        self.assertIn("lock mode update tracks lock_mode vs schedule changed_field", qa_checklist)
        self.assertIn("FirebaseKeepAnalyticsTest.goalLockEndedEarlyUsesSafeBucketedParamsOnly", qa_checklist)
        self.assertIn("Goal lock QA evidence", qa_checklist)
        self.assertIn("all-day / scheduled / expiration", qa_checklist)
        self.assertIn("KeepAccessibilityServiceIntegrationTest.activeAllDayGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution", qa_checklist)
        self.assertIn("KeepAccessibilityServiceIntegrationTest.activeScheduledGoalLockWithoutManualKeep_launchesBlockActivityWithGoalLockAttribution", qa_checklist)
        self.assertIn("KeepAccessibilityServiceIntegrationTest.expiredGoalLockWithoutManualKeep_keepsTargetForegroundWithoutGoalLockAttribution", qa_checklist)
        self.assertIn("block_source=goal_lock", qa_checklist)

    def test_context_pack_does_not_describe_goal_lock_as_pre_implementation_only(self):
        product_context = PRODUCT_CONTEXT.read_text()
        metrics_context = METRICS_CONTEXT.read_text()

        for document in [product_context, metrics_context]:
            self.assertIn("policy/persistence/creation UI/navigation/Home/Accessibility blocking/detail/early-end/Home completion", document)
            self.assertIn("상세 앱/이름/기간/잠금 방식 수정", document)
            self.assertIn("goal_lock_updated changed_field=apps|name|duration|schedule|lock_mode", document)
            self.assertIn("compact-height creation/detail CTA", document)
            self.assertIn("PR #760", document)
            self.assertIn("summary", document)
            self.assertIn("contentDescription", document)
            self.assertIn("TalkBack", document)
            self.assertIn("실기기/release-candidate screenshot", document)
            self.assertNotIn("#417 목표 잠금 MVP 계약. 기간 기반 `all_day`/`scheduled` 장기 잠금, Home 진행 카드/섹션, privacy-safe analytics, runtime QA baseline을 구현 전 handoff", document)
            self.assertNotIn("기간/schedule/lock_mode 수정 UI, TalkBack 실기기 spot-check", document)

        dashboard = PRODUCT_DASHBOARD.read_text()
        self.assertIn("Home completion foothold", dashboard)
        self.assertIn("`develop` 반영 상태", dashboard)
        self.assertIn("상세 앱/이름/기간/잠금 방식 수정", dashboard)
        self.assertIn("compact-height 생성·상세 CTA", dashboard)

        docs_agents = DOCS_AGENTS.read_text()
        self.assertIn("PR #760", docs_agents)
        self.assertIn("summary TalkBack contentDescription", docs_agents)

    def test_goal_lock_creation_uses_picker_style_app_selection(self):
        screen = GOAL_LOCK_CREATION_SCREEN.read_text()

        self.assertIn("CategoryBottomSheetContent", screen)
        self.assertIn("ModalBottomSheet", screen)
        self.assertIn("onSelectApps", screen)
        self.assertIn("viewModel.setSelectedApps(selectedApps)", screen)
        self.assertNotIn("패키지 직접 추가", screen)
        self.assertNotIn("onAddSelectedAppPackage", screen)

    def test_goal_lock_creation_screen_uses_string_resources_for_user_visible_copy(self):
        screen = GOAL_LOCK_CREATION_SCREEN.read_text()

        self.assertIn("stringResource(id = R.string.goal_lock_creation_title)", screen)
        self.assertIn("R.string.goal_lock_creation_duration_range", screen)
        self.assertIn("R.string.goal_lock_creation_selected_apps_helper", screen)
        self.assertIn("stringResource(id = R.string.goal_lock_creation_app_label_missing)", screen)
        for hardcoded_copy in [
            "목표 잠금 만들기",
            "뒤로 가기",
            "목표 이름",
            "예: 시험 준비, SNS 줄이기",
            "시험 준비",
            "SNS 줄이기",
            "기간",
            "직접 일수 입력: 예: 21",
            "종료 날짜 입력: YYYY-MM-DD",
            "잠금 방식",
            "하루종일 잠금",
            "평일 저녁 잠금",
            "현재 방식:",
            "선택 앱",
            "홈 선택 다시 불러오기",
            "앱 선택 화면에서 조정",
            "빼기",
            "목표 잠금 시작",
            "앱 이름을 불러오지 못했어요",
        ]:
            self.assertNotIn(hardcoded_copy, screen)

    def test_goal_lock_detail_screen_uses_string_resources_for_app_update_copy(self):
        screen = GOAL_LOCK_DETAIL_SCREEN.read_text()

        self.assertIn("stringResource(id = R.string.goal_lock_detail_goal_name_label)", screen)
        self.assertIn("R.string.goal_lock_detail_update_name_confirmation", screen)
        self.assertIn("stringResource(id = R.string.goal_lock_detail_update_name_save)", screen)
        self.assertIn("stringResource(id = R.string.goal_lock_detail_update_apps_cta)", screen)
        self.assertIn("R.string.goal_lock_detail_update_apps_confirmation", screen)
        self.assertIn("stringResource(id = R.string.goal_lock_detail_update_apps_save)", screen)
        self.assertIn("R.string.goal_lock_detail_duration_label", screen)
        self.assertIn("R.string.goal_lock_detail_duration_option_7_days", screen)
        self.assertIn("R.string.goal_lock_detail_update_duration_confirmation", screen)
        self.assertIn("R.string.goal_lock_detail_lock_mode_label", screen)
        self.assertIn("R.string.goal_lock_detail_lock_mode_all_day", screen)
        self.assertIn("R.string.goal_lock_detail_lock_mode_weekday_evening", screen)
        self.assertIn("R.string.goal_lock_detail_update_lock_mode_confirmation", screen)
        for hardcoded_copy in [
            "목표 이름",
            "목표 잠금 이름을 ${state.pendingGoalName.trim()}(으)로 바꿀까요?",
            "이름 저장",
            "차단 앱 변경",
            "선택한 앱 ${state.pendingSelectedApps.size}개로 목표 잠금 대상을 바꿀까요?",
            "변경 저장",
            "기간",
            "7일",
            "14일",
            "30일",
            "잠금 방식",
            "하루종일 잠금",
            "평일 저녁 잠금",
        ]:
            self.assertNotIn(hardcoded_copy, screen)

    def test_goal_lock_detail_screen_uses_string_resources_for_status_and_end_flow_copy(self):
        screen = GOAL_LOCK_DETAIL_SCREEN.read_text()

        required_resources = [
            "R.string.goal_lock_detail_title",
            "R.string.cd_navigate_back",
            "R.string.goal_lock_detail_loading",
            "R.string.goal_lock_detail_summary",
            "R.string.goal_lock_detail_status_completed",
            "R.string.goal_lock_detail_status_ended",
            "R.string.goal_lock_detail_status_active",
            "R.string.goal_lock_detail_end_confirmation",
            "R.string.goal_lock_detail_end_cancel",
            "R.string.goal_lock_detail_end_confirm",
            "R.string.goal_lock_detail_end_cta",
        ]
        for resource in required_resources:
            self.assertIn(resource, screen)

        for hardcoded_copy in [
            "목표 잠금",
            "뒤로 가기",
            "목표 잠금을 불러오는 중입니다.",
            "개 앱",
            "완료된 목표 잠금입니다.",
            "종료된 목표 잠금입니다.",
            "진행 중인 목표 잠금입니다.",
            "목표 잠금을 끝내면 오늘부터 선택한 앱이 다시 열릴 수 있어요. 지금 종료할까요?",
            "계속 유지",
            "종료",
            "목표 잠금 종료",
        ]:
            self.assertNotIn(hardcoded_copy, screen)

        user_visible_string_literals = re.findall(r'text = "([^"]+)"', screen)
        self.assertEqual(
            [],
            user_visible_string_literals,
            "GoalLockDetailScreen user-visible Text copy must come from stringResource.",
        )

    def test_goal_lock_detail_viewmodel_does_not_expose_localized_ui_copy(self):
        viewmodel = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "uiery"
            / "keep"
            / "feature"
            / "goallock"
            / "GoalLockDetailViewModel.kt"
        ).read_text()

        for hardcoded_copy in [
            "하루종일 잠금",
            "특정 시간 잠금",
        ]:
            self.assertNotIn(hardcoded_copy, viewmodel)

        self.assertNotIn("detailLabel", viewmodel)
        self.assertNotIn("lockModeLabel", viewmodel)
        self.assertNotIn("pendingLockModeLabel", viewmodel)

    def test_goal_lock_detail_locale_resources_exist_in_every_shipped_locale(self):
        required_keys = {
            "goal_lock_detail_title",
            "goal_lock_detail_loading",
            "goal_lock_detail_summary",
            "goal_lock_detail_status_completed",
            "goal_lock_detail_status_ended",
            "goal_lock_detail_status_active",
            "goal_lock_detail_end_confirmation",
            "goal_lock_detail_end_cancel",
            "goal_lock_detail_end_confirm",
            "goal_lock_detail_end_cta",
        }

        for strings_file in sorted(RES_ROOT.glob("values*/strings.xml")):
            with self.subTest(locale=strings_file.parent.name):
                names = _string_resource_names(strings_file)
                self.assertTrue(
                    required_keys.issubset(names),
                    f"{strings_file.parent.name} is missing {sorted(required_keys - names)}",
                )

    def test_goal_lock_detail_default_locale_copy_is_english(self):
        required_keys = {
            "goal_lock_detail_title",
            "goal_lock_detail_loading",
            "goal_lock_detail_summary",
            "goal_lock_detail_status_completed",
            "goal_lock_detail_status_ended",
            "goal_lock_detail_status_active",
            "goal_lock_detail_end_confirmation",
            "goal_lock_detail_end_cancel",
            "goal_lock_detail_end_confirm",
            "goal_lock_detail_end_cta",
        }
        default_strings = RES_ROOT / "values" / "strings.xml"
        tree = ET.parse(default_strings)
        values_by_name = {
            element.attrib["name"]: "".join(element.itertext())
            for element in tree.getroot().findall("string")
            if element.attrib.get("name") in required_keys
        }

        self.assertEqual(required_keys, set(values_by_name))
        for name, value in values_by_name.items():
            with self.subTest(name=name):
                self.assertIsNone(
                    re.search(r"[가-힣]", value),
                    f"Default English locale must not include Korean copy: {name}={value}",
                )

    def test_runbook_points_future_lanes_to_contract_regression(self):
        runbook = RUNBOOK.read_text()

        self.assertIn(
            "python3 -m unittest scripts.tests.test_goal_lock_contract -v",
            runbook,
        )
        self.assertIn("goal-lock contract regression", runbook)


if __name__ == "__main__":
    unittest.main()
