import pathlib
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
DOC = REPO_ROOT / "docs/DAO_BOUNDARY_MAINTENANCE.md"


class DaoBoundaryMaintenanceDocsTest(unittest.TestCase):
    def test_doc_records_issue_520_inventory_and_lock_history_boundary(self):
        self.assertTrue(DOC.exists(), "#520 DAO boundary inventory/runbook should be documented")
        text = DOC.read_text(encoding="utf-8")
        for expected in (
            "#520",
            "LockHistoryRepository",
            "LockHistorySessionWriter",
            "LockHistoryRecorder",
            "LockHistoryViewModel",
            "BlockedAppsViewModel",
            "ReviewEligibilityRepository",
            "ReviewEligibilityEvaluator",
            "GoalLockRepository",
            "GoalLockCreationViewModel",
            "GoalLockDetailViewModel",
            "EmergencyUnlockRepository",
            "EmergencyUnlockCoordinator",
            "RoutineRepository",
            "RoutineBottomSheetViewModel",
            "RoutineViewModel",
            "HomeViewModel",
            "RoutineCountAnalyticsSync",
            "#875",
            "Room DAO 직접 import",
            "허용 경계",
            "Closure audit",
        ):
            self.assertIn(expected, text)

    def test_doc_names_verification_commands(self):
        text = DOC.read_text(encoding="utf-8")
        for expected in (
            "python3 -m unittest scripts.tests.test_dao_boundary_contract -v",
            "python3 -m unittest scripts.tests.test_dao_boundary_maintenance_docs -v",
            ":app:testDevDebugUnitTest",
            "MenuViewModel",
            "RoutineRepository",
            "scripts.tests.test_routines_count_coverage_contract",
        ):
            self.assertIn(expected, text)

    def test_doc_records_closure_audit_instead_of_stale_followup_inventory(self):
        text = DOC.read_text(encoding="utf-8")
        self.assertIn("Closure audit", text)
        self.assertIn("repo-internal DAO boundary package is complete for the original #520 inventory", text)
        self.assertIn("Open #875 exception", text)
        self.assertIn("repository/count-provider/use-case", text)
        self.assertIn("future regression 발견 시", text)
        self.assertNotIn("직접 DAO 의존은 아직 #520의 후속 패키지 대상", text)

    def test_doc_links_home_routines_count_exception_to_coverage_contract(self):
        text = DOC.read_text(encoding="utf-8")
        for expected in (
            "Home `routines_count` analytics sync boundary — open #875",
            "#479는 `routines_count=(not set)` 감소를 release/readback으로 검증하는 지표 이슈",
            "#875는 같은 coverage foothold를 유지하면서 DAO/Entity 결합을 repository/use-case 경계로 분리",
            "HomeViewModelActivationAnalyticsTest.homeInitSyncsRoutinesCountFromRoomWithoutRoutineScreenEntry",
            "SplashViewModelRestoreSchedulingTest.splashStartupReschedulesRestoredRoomRoutineBeforeOnboardingNavigation",
        ):
            self.assertIn(expected, text)
