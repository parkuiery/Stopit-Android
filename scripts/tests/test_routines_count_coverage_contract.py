import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
CONTRACT = REPO_ROOT / "docs" / "ROUTINES_COUNT_COVERAGE_CONTRACT.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
RETENTION_BASELINE = REPO_ROOT / "docs" / "ROUTINE_RETENTION_COHORT_BASELINE.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
README = REPO_ROOT / "README.md"


class RoutinesCountCoverageContractTest(unittest.TestCase):
    def test_contract_locks_current_coverage_gap_and_issue_boundary(self):
        contract = CONTRACT.read_text()

        for phrase in [
            "Issue: #479",
            "PR #525 중앙 sync 구현 develop 반영 완료",
            "PR #525(`3246b088`)",
            "560 / 865 = 64.7%",
            "routines_count=(not set)",
            "RoutineCountAnalyticsSync",
            "KeepAnalyticsUserProperty.ROUTINES_COUNT",
            "Refs #479",
        ]:
            self.assertIn(phrase, contract)

        self.assertNotIn("Closes #479", contract)

    def test_contract_defines_implementation_and_privacy_guardrails(self):
        contract = CONTRACT.read_text()

        for phrase in [
            "KeepAnalytics",
            "routines_count=0",
            "앱 실행, Splash/Home 진입",
            "루틴 생성/수정/삭제",
            "backup/restore 또는 boot rehydrate",
            "루틴 이름, 앱 package/name, `lockApplications`, raw schedule/history",
            "숫자 count만 허용",
            "raw string `\"routines_count\"`",
        ]:
            self.assertIn(phrase, contract)

    def test_contract_defines_release_and_readback_gate(self):
        contract = CONTRACT.read_text()

        for phrase in [
            "origin/main",
            "SemVer tag",
            "Play deploy",
            "D+14/D+30",
            "최신 production version active share",
            "`(not set)` activeUsers 비중",
            "Crashlytics crash-free users",
            "Play Console rating/review",
        ]:
            self.assertIn(phrase, contract)

    def test_high_traffic_docs_link_to_routines_count_contract(self):
        documents = [
            ANALYTICS_DICTIONARY.read_text(),
            RETENTION_BASELINE.read_text(),
            PRODUCT_DASHBOARD.read_text(),
            METRICS_ANALYSIS.read_text(),
            GA4_RUNBOOK.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
            README.read_text(),
        ]

        for document in documents:
            self.assertIn("ROUTINES_COUNT_COVERAGE_CONTRACT.md", document)
            self.assertIn("#479", document)

    def test_code_lane_sync_owner_is_reflected_in_metrics_contexts(self):
        for document in [
            CONTRACT.read_text(),
            ANALYTICS_DICTIONARY.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
        ]:
            self.assertIn("RoutineCountAnalyticsSync", document)

        contract = CONTRACT.read_text()
        self.assertIn("HomeViewModelActivationAnalyticsTest.homeInitSyncsRoutinesCountFromRoomWithoutRoutineScreenEntry", contract)
        self.assertIn("SplashViewModelRestoreSchedulingTest.splashStartupReschedulesRestoredRoomRoutineBeforeOnboardingNavigation", contract)

    def test_stale_pre_implementation_wording_is_not_reintroduced(self):
        combined = "\n".join(
            document.read_text()
            for document in [
                CONTRACT,
                ANALYTICS_DICTIONARY,
                PRODUCT_DASHBOARD,
                METRICS_ANALYSIS,
            ]
        )

        for stale_phrase in [
            "code-lane 중앙 sync 구현 PR 준비",
            "#479 완료 전",
            "현재 구현은 `app/src/main/java/com/uiery/keep/feature/routine/RoutineViewModel.kt`",
            "#479 이후에는 앱 실행/Home 진입",
        ]:
            self.assertNotIn(stale_phrase, combined)


if __name__ == "__main__":
    unittest.main()
