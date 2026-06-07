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
        ):
            self.assertIn(expected, text)

    def test_doc_records_closure_audit_instead_of_stale_followup_inventory(self):
        text = DOC.read_text(encoding="utf-8")
        self.assertIn("Closure audit", text)
        self.assertIn("repo-internal DAO boundary package is complete", text)
        self.assertIn("future regression 발견 시", text)
        self.assertNotIn("직접 DAO 의존은 아직 #520의 후속 패키지 대상", text)
