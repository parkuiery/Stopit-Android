import pathlib
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"
APP_SRC = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep"
SETUP_SCREEN = APP_SRC / "feature" / "parentmode" / "ParentModeSetupScreen.kt"
SETUP_VIEW_MODEL = APP_SRC / "feature" / "parentmode" / "ParentModeSetupViewModel.kt"
APP_SELECTION_SHEET = APP_SRC / "ui" / "component" / "CategoryBottomSheetContent.kt"


def load_strings(strings_xml: pathlib.Path) -> dict[str, str]:
    root = ET.parse(strings_xml).getroot()
    return {
        node.attrib["name"]: "".join(node.itertext()).strip()
        for node in root.findall("string")
    }


RUNBOOK = REPO_ROOT / "docs" / "PARENT_MODE_MVP.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class ParentModeContractTest(unittest.TestCase):
    def test_runbook_locks_same_device_mvp_scope(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #471",
            "부모 모드",
            "아이에게 폰 주기",
            "same-device MVP",
            "보호자 PIN",
            "허용 앱",
            "시간 만료",
            "원격 자녀 기기 관리",
            "MVP에서는 제외",
            "기존 긴급해제와 분리",
            "Refs #471",
            "Closes #471",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        forbidden_guidance = [
            "원격 제어를 MVP에 포함",
            "아이 이름 원문을 analytics",
            "앱 이름/package를 analytics",
            "긴급해제를 광고 뒤에",
        ]
        for phrase in forbidden_guidance:
            self.assertNotIn(phrase, runbook)

    def test_analytics_dictionary_contains_parent_mode_events_and_safe_parameters(self):
        analytics = ANALYTICS_DICTIONARY.read_text()

        for event_name in [
            "parent_mode_duration_selected",
            "parent_mode_allowed_apps_selected",
            "parent_mode_started",
            "parent_mode_completed",
            "parent_mode_unlocked_by_pin",
            "parent_mode_extended",
            "parent_mode_block_intercepted",
            "parent_mode_cancelled",
        ]:
            self.assertIn(event_name, analytics)

        for parameter in [
            "duration_minutes_bucket",
            "allowed_app_count_bucket",
            "pin_result",
            "end_reason",
            "extension_minutes_bucket",
            "block_context",
        ]:
            self.assertIn(parameter, analytics)

        self.assertIn("PARENT_MODE_MVP.md", analytics)
        self.assertIn("PR #897 merge commit `3d9830cddf2cbae7625996737cd2dfc4f4ee1c82`", analytics)
        self.assertIn("app_block_intercepted(block_source=parent_mode)", analytics)
        self.assertIn("parent_mode_block_intercepted(block_context=disallowed_app)", analytics)
        self.assertIn("아이 이름/앱 이름/package/raw session history 금지", analytics)
        self.assertIn("manual_keep / timed_lock / routine / goal_lock / parent_mode", analytics)

        stale_pre_wiring_phrases = [
            "아직 setup/active UI, PIN runtime flow",
            "Parent Mode-origin 차단 화면은 아직 dedicated",
            "parent_mode_block_intercepted 미구현",
        ]
        for phrase in stale_pre_wiring_phrases:
            self.assertNotIn(phrase, analytics)

    def test_runbook_tracks_session_store_and_accessibility_runtime_foothold(self):
        runbook = RUNBOOK.read_text()

        for phrase in [
            "ParentModeSessionStore",
            "PreferencesKey.PARENT_MODE_STARTED_AT",
            "BackupRestoreDataStoreKeyPolicy",
            "KeepAccessibilityServiceBlockDecisionTest",
            "block_source=parent_mode",
            "parent_mode_block_intercepted(block_context=disallowed_app)",
            "BlockViewModelTest.parentModeBlockTracksDedicatedPrivacySafeInterceptEvent",
            "시간 만료 후 허용 앱도 차단",
            "부모 제어 surface는 차단하지 않는다",
            "PR #519",
            "PR #584",
            "repo-internal foothold",
            "남은 범위는 MVP 전체 릴리스/실측 검증",
        ]:
            self.assertIn(phrase, runbook)

        stale_pre_implementation_phrases = [
            "이 표는 구현 전 계약이다",
            "이 문서/계약 PR은 구현 전 handoff",
        ]
        for phrase in stale_pre_implementation_phrases:
            self.assertNotIn(phrase, runbook)

    def test_runbook_tracks_session_controller_commit_boundary(self):
        runbook = RUNBOOK.read_text()
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        for phrase in [
            "ParentModeSessionController",
            "setup validation → session 저장 → privacy-safe analytics commit",
            "시작·연장·종료",
            "invalid setup",
            "PIN 없는 연장 거부",
            "PIN 성공 즉시 종료",
            "시간 만료 1회 commit",
            "ParentModeSessionControllerTest",
        ]:
            self.assertIn(phrase, runbook)

        for phrase in [
            "ParentModeSessionControllerTest",
            "setup validation 실패 시 저장/analytics를 하지 않고",
            "PIN 성공 후 연장/즉시 종료만 저장",
            "parent_mode_completed(end_reason=time_expired)`로 한 번만 commit",
        ]:
            self.assertIn(phrase, qa_checklist)

    def test_runbook_tracks_setup_entry_foothold_and_remaining_boundaries(self):
        runbook = RUNBOOK.read_text()
        product_context = PRODUCT_CONTEXT.read_text()
        metrics_context = METRICS_CONTEXT.read_text()

        for phrase in [
            "4차 code-lane foothold",
            "Menu의 `아이에게 폰 주기` entrypoint",
            "ParentModeSetupRoute",
            "setup 화면 foothold",
            # 4차 foothold의 seed 경계는 10차에서 걷어냈다. 문서는 그 전환을 남기고,
            # 계약은 되돌아간 상태가 아니라 현재 경계를 잠근다.
            "10차 code-lane allowlist 경계",
            "11차 code-lane 차단 이유 · 탈출 경로 · 긴급 전화 경계",
            "block_screen_parent_mode_expired_reason",
            "parent_mode_expired_end_and_unlock",
            "ParentModeBlockReasonSource",
            "자기통제 잠금(수동/타이머/루틴/목표)은 그대로\n  발신을 막는다",
            "BlockingStateStore",
            "parent_mode_setup_allowed_apps_scope_notice",
            "AppSelectionPurpose",
            "app_selection_allowed_apps_notice",
            "5차 code-lane foothold",
            "실제 PIN 입력 UI와 setup CTA enablement",
            "PIN 불일치/미충족 상태에서는 session 저장을 막는 경계",
            "ParentModeSetupViewModelTest",
            "6차 code-lane foothold",
            "markExpiredIfNeededPersistsExpiredSessionAndTracksCompletionOnce",
            "9차 code-lane active controls foothold",
            "PR #748 merge commit `d73dac88c2bab17b446f4a1b9cd3a9b26ad1134d`",
            "PR #873 merge commit `d1be39ae764b53386baeba8bfc1fa3c400ff941e`",
            "PR #870 merge commit `53e3d25c591c8fa8e2e444bff6636b046b2bd4eb`",
            "PR #897",
            "PR #913",
            "PR #946 merge commit `b3a6c7a121e88c56353372cbb97366b2a04c0bce`",
            "PR #883 merge commit `2ea625f3bdb082966332ac8d5e28ae870ad3838a`",
            "PR #970",
            "PR #980",
            "PR #1078",
            "1099043541598bbf0d82ce8fd1624c36c3eff8b9",
            "a0360ab6",
            "duration preset 선택 UI",
            "직접 분 입력 필드",
            "직접 입력한 custom duration",
            "setup/active/expired 화면의 접근성 요약",
            "TalkBack baseline 미정의",
            "issue #874의 stale Active 액션 경계",
            "stale expiry 기준으로 10분 연장되지 않고",
            "unlocked_by_pin`으로 오계측되지 않으며",
            "finished session 연장/종료 no-op",
            "재활성화와 completion analytics 중복 전송을 막는다",
            "finished session 재호출 guard 없음",
            "자동 refresh를 예약",
            "verified guardian PIN 상태로 10분 연장 또는 즉시 종료",
            "active controls fresh guardian PIN 입력/확인",
            "부모 모드 다시 시작",
            "clearFinishedSession",
            "finished session clear 후 다른 setup 재진입",
            "PIN 없는 active 연장/종료 허용",
            "ParentModeSetupViewModelTest",
            "release-candidate device UX spot-check",
        ]:
            self.assertIn(phrase, runbook)

        for document in [product_context, metrics_context]:
            self.assertIn("Menu", document)
            self.assertIn("setup 화면", document)
            self.assertIn("PIN 입력 UI", document)
            self.assertIn("active/expired", document)
            self.assertIn("PR #946", document)
            self.assertIn("fresh guardian PIN", document)

        self.assertNotIn("Home/Menu entrypoint + setup screen", runbook)
        self.assertNotIn("2026-06-09 QA-lane PR", runbook)
        self.assertNotIn("2026-06-14 QA-lane PR", runbook)
        self.assertNotIn("2026-06-15 code-lane follow-through", runbook)
        self.assertNotIn("2026-06-15 code-lane active-PIN follow-through", runbook)
        self.assertNotIn("2026-06-14 code-lane follow-through", runbook)
        self.assertNotIn("이번 follow-through", runbook)

    def test_product_context_tracks_parent_mode_foothold_not_pre_implementation_handoff(self):
        product_context = PRODUCT_CONTEXT.read_text()
        runbook = RUNBOOK.read_text()

        self.assertIn("PR #519", product_context)
        self.assertIn("PR #584", product_context)
        self.assertIn("policy/analytics foothold", product_context)
        self.assertIn("session persistence와 Accessibility decision foothold", product_context)
        self.assertIn("setup 화면/ViewModel foothold", product_context)
        self.assertIn("PIN 입력 UI와 setup CTA enablement", product_context)
        self.assertIn("PR #748 merge commit `d73dac88c2bab17b446f4a1b9cd3a9b26ad1134d`", product_context)
        self.assertIn("PR #873 merge commit `d1be39ae764b53386baeba8bfc1fa3c400ff941e`", product_context)
        self.assertIn("PR #946 merge commit `b3a6c7a121e88c56353372cbb97366b2a04c0bce`", product_context)
        self.assertIn("PR #897", product_context)
        self.assertIn("PR #913", product_context)
        self.assertIn("PR #970", product_context)
        self.assertIn("PR #980", product_context)
        self.assertIn("PR #1078", product_context)
        self.assertIn("10990435", product_context)
        self.assertIn("a0360ab6", product_context)
        self.assertIn("ParentModeSetupScreenAccessibilityTest", product_context)
        self.assertIn("active controls 미구현", product_context)
        self.assertIn("직접 설정 미구현", product_context)
        self.assertIn("TalkBack baseline 미정의", product_context)
        self.assertIn("fresh guardian PIN 입력/확인이 다시 완료되기 전까지 연장/즉시 종료 CTA를 disabled", product_context)
        self.assertIn("재활성화나 completion analytics 중복", product_context)
        self.assertIn("finished session 재호출 guard 없음", product_context)
        self.assertIn("상태로 되돌리지 않는다", product_context)
        self.assertIn("남은 경계는 release-candidate device UX spot-check", product_context)
        self.assertNotIn("2026-06-09 code-lane PR", runbook)
        self.assertNotIn("2026-06-09 code-lane PR", product_context)
        self.assertNotIn("원격 자녀 기기 관리 후속 gate를 구현 전 handoff로 고정한다", product_context)
        self.assertNotIn("이번 PR은 setup 화면", product_context)
        self.assertNotIn("2026-06-15 code-lane follow-through", product_context)
        self.assertNotIn("이번 follow-through", product_context)

    def test_high_traffic_docs_link_to_parent_mode_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]

        for document in documents:
            self.assertIn("PARENT_MODE_MVP.md", document)
            self.assertIn("#471", document)

    def test_ga4_registration_runbook_tracks_parent_mode_parameters(self):
        ga4_runbook = GA4_RUNBOOK.read_text()

        for parameter in [
            "customEvent:duration_minutes_bucket",
            "customEvent:allowed_app_count_bucket",
            "customEvent:pin_result",
            "customEvent:end_reason",
            "customEvent:extension_minutes_bucket",
            "customEvent:block_context",
        ]:
            self.assertIn(parameter, ga4_runbook)

        self.assertIn("부모 모드 조회성", ga4_runbook)
        self.assertIn("parent mode check", ga4_runbook)
        self.assertIn("아이 이름/앱 이름/package/raw session history", ga4_runbook)

    def test_qa_checklist_defines_parent_mode_runtime_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        self.assertIn("부모 모드 runtime QA baseline", qa_checklist)
        self.assertIn("PR #870", qa_checklist)
        self.assertIn("PR #946", qa_checklist)
        self.assertIn("ParentModeSetupScreenAccessibilityTest", qa_checklist)
        self.assertIn("TalkBack summary", qa_checklist)
        self.assertIn("시/분 휠 duration 다이얼", qa_checklist)
        self.assertIn(
            "ParentModeSetupViewModelTest.durationWheelStartsParentModeWithTheHourAndMinuteTheParentDialled",
            qa_checklist,
        )
        self.assertIn("direct duration spot-check", qa_checklist)
        self.assertIn(
            "ParentModeSetupViewModelTest.theGuardianSheetRoutesExtendAndEndThroughTheSamePinGate",
            qa_checklist,
        )
        self.assertIn("ParentModePolicyTest", qa_checklist)
        self.assertIn("BlockViewModelTest.parentModeBlockTracksDedicatedPrivacySafeInterceptEvent", qa_checklist)
        self.assertIn("FirebaseKeepAnalyticsTest.parentModeBlockInterceptedUsesSafeBlockContextOnly", qa_checklist)
        self.assertIn("ParentModePinPolicyTest", qa_checklist)
        self.assertIn("FirebaseKeepAnalyticsTest.parentModeStartedUsesSafeBucketedParamsOnly", qa_checklist)
        self.assertIn(
            "KeepAccessibilityServiceIntegrationTest#activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution",
            qa_checklist,
        )
        self.assertIn(
            "KeepAccessibilityServiceIntegrationTest#expiredActiveParentModeWithoutManualKeep_blocksPreviouslyAllowedAppWithExpiredEvidence",
            qa_checklist,
        )
        self.assertIn("observedParentModeState=active", qa_checklist)
        self.assertIn("observedParentModeState=expired", qa_checklist)
        self.assertIn("lastLaunchedBlockSource=parent_mode", qa_checklist)
        self.assertIn("nextParentModeExpirationReevaluationDelayReturnsDelayUntilActiveSessionExpiry", qa_checklist)
        self.assertIn("expiresAtMillis", qa_checklist)
        self.assertIn("0분/음수 extension은 거부", qa_checklist)
        self.assertIn("issue #874 stale Active guard", qa_checklist)
        self.assertIn("PR #1078 finished-session no-op guard", qa_checklist)
        self.assertIn("stale Active expiry spot-check", qa_checklist)
        self.assertIn("finished-session no-op spot-check", qa_checklist)
        self.assertIn("PIN_UNLOCKED", qa_checklist)
        self.assertIn("PR #519/#584/#748/#870/#873/#897/#913/#946/#970/#980/#1078", qa_checklist)
        self.assertIn("dedicated block analytics 미구현", qa_checklist)
        self.assertIn("setup screen_view 미계측", qa_checklist)
        self.assertIn("finished session 재호출 guard 없음", qa_checklist)
        self.assertIn("active controls fresh guardian PIN", qa_checklist)
        self.assertIn("PIN 없는 active 연장/종료 허용", qa_checklist)
        self.assertIn("active controls 미구현", qa_checklist)
        self.assertIn("직접 설정 미구현", qa_checklist)
        self.assertIn("TalkBack baseline 미정의", qa_checklist)
        self.assertIn("release-candidate device UX spot-check", qa_checklist)
        self.assertIn("Parent mode QA evidence", qa_checklist)
        self.assertIn("same-device / PIN / bypass", qa_checklist)

    def test_runbook_tracks_parent_mode_expiry_runtime_foothold(self):
        runbook = RUNBOOK.read_text()

        for phrase in [
            "7차 QA-lane runtime foothold",
            "PR #714 merge commit `1a55a4a0a5969cca3a69f158721224e27f37002d`",
            "activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution",
            "observedParentModeState=active",
            "lastLaunchedBlockSource=parent_mode",
            "8차 QA-lane expiry runtime foothold",
            "PR #716 merge commit `04c8d075bf84081c78ce17748f368c9965acbbb2`",
            "nextParentModeExpirationReevaluationDelayMillis",
            "nextTimeBasedBlockingStartReevaluationDelayMillis",
            "time-based 재평가",
            "observedParentModeState=expired",
            "expiredActiveParentModeWithoutManualKeep_blocksPreviouslyAllowedAppWithExpiredEvidence",
        ]:
            self.assertIn(phrase, runbook)

    def test_setup_screen_states_the_allowlist_scope_and_never_seeds_the_blocking_selection(self):
        """부모 모드는 허용목록이라 고르지 않은 앱이 전부 잠긴다.

        차단 앱 목록을 허용 앱으로 씨딩하면 부모가 평소 막아 두던 앱만 열리고 나머지 기기 전체가
        잠긴다. VOC "부모모드를 쓰면 모든 앱이 잠긴다"가 정확히 이 경로다. 씨딩 경로를 없애고,
        시작 전에 무엇이 잠기는지 화면이 직접 말하게 한다.
        """
        setup_screen = SETUP_SCREEN.read_text()
        setup_view_model = SETUP_VIEW_MODEL.read_text()

        for removed in [
            "parent_mode_setup_reload_current_selection",
            "onReloadCurrentSelection",
        ]:
            self.assertNotIn(removed, setup_screen)

        for removed in [
            "readSelectedAppPackages",
            "BlockingStateStore",
            "loadAllowedAppsFromCurrentSelection",
        ]:
            self.assertNotIn(removed, setup_view_model)

        self.assertIn("parent_mode_setup_allowed_apps_scope_notice", setup_screen)
        self.assertIn("AppSelectionPurpose.Allow", setup_screen)

    def test_app_selection_sheet_carries_allow_mode_copy(self):
        """같은 시트가 차단 대상 선택과 허용 앱 선택 양쪽에 쓰인다.

        허용 모드에서 "차단 대상 선택" 제목과 "차단하면 인증번호를 못 받아요" 확인 다이얼로그는
        의미가 정반대다. 특히 그 다이얼로그의 제외 버튼은 허용 목록에서는 오히려 그 앱을 잠근다.
        """
        sheet = APP_SELECTION_SHEET.read_text()
        default_strings = load_strings(RES_DIR / "values" / "strings.xml")

        self.assertIn("enum class AppSelectionPurpose", sheet)
        self.assertIn("appSelectionHeading(", sheet)
        self.assertIn("requiresSensitiveBlockConfirmation(", sheet)
        self.assertIn("app_selection_allowed_apps_title", sheet)
        self.assertIn("app_selection_allowed_apps_notice", sheet)

        for key in [
            "app_selection_allowed_apps_title",
            "app_selection_allowed_apps_notice",
            "parent_mode_setup_allowed_apps_scope_notice",
        ]:
            self.assertIn(key, default_strings)

        self.assertNotIn("parent_mode_setup_reload_current_selection", default_strings)

    def test_runbook_points_future_lanes_to_contract_regression(self):
        runbook = RUNBOOK.read_text()

        self.assertIn(
            "python3 -m unittest scripts.tests.test_parent_mode_contract -v",
            runbook,
        )
        self.assertIn("parent-mode contract regression", runbook)


if __name__ == "__main__":
    unittest.main()
