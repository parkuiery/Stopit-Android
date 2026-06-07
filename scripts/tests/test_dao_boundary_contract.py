import pathlib
import re
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_MAIN = REPO_ROOT / "app/src/main/java/com/uiery/keep"


class DaoBoundaryContractTest(unittest.TestCase):
    def kotlin_sources(self, root: pathlib.Path):
        return sorted(root.rglob("*.kt"))

    def test_lock_history_feature_viewmodels_use_repository_boundary(self):
        offenders: list[str] = []
        feature_root = APP_MAIN / "feature/lockhistory"
        import_pattern = re.compile(r"^import\s+com\.uiery\.keep\.database\.dao\.LockHistoryDao\b", re.MULTILINE)

        for source in self.kotlin_sources(feature_root):
            if source.name.endswith("Repository.kt"):
                continue
            relative = source.relative_to(REPO_ROOT)
            if import_pattern.search(source.read_text()):
                offenders.append(str(relative))

        self.assertEqual(
            [],
            offenders,
            "lock-history UI must depend on LockHistoryRepository, not Room DAO directly",
        )

    def test_lock_history_repository_is_the_feature_allowlisted_dao_boundary(self):
        repository = APP_MAIN / "feature/lockhistory/LockHistoryRepository.kt"
        self.assertTrue(repository.exists(), "LockHistoryRepository owns feature lock-history DAO access")
        text = repository.read_text()
        self.assertIn("class LockHistoryRepository", text)
        self.assertIn("import com.uiery.keep.database.dao.LockHistoryDao", text)
        self.assertIn("fun sessionsInRange", text)
        self.assertIn("fun blockedAppsByFrequency", text)
        self.assertIn("suspend fun recordSession", text)

    def test_lock_history_recorder_uses_repository_boundary(self):
        recorder = APP_MAIN / "service/LockHistoryRecorder.kt"
        ledger = APP_MAIN / "service/LockHistoryLedger.kt"
        recorder_text = recorder.read_text()
        ledger_text = ledger.read_text()

        self.assertTrue(recorder.exists(), "LockHistoryRecorder owns completed-session recording orchestration")
        self.assertNotIn("import com.uiery.keep.database.dao.LockHistoryDao", recorder_text)
        self.assertNotIn("import com.uiery.keep.database.entity.LockHistoryEntity", recorder_text)
        self.assertIn("import com.uiery.keep.feature.lockhistory.LockHistoryRepository", recorder_text)
        self.assertIn("private val lockHistoryRepository: LockHistoryRepository", recorder_text)
        self.assertNotIn("import com.uiery.keep.database.dao.LockHistoryDao", ledger_text)
        self.assertNotIn("import com.uiery.keep.database.entity.LockHistoryEntity", ledger_text)

    def test_review_eligibility_evaluator_uses_repository_boundary(self):
        evaluator = APP_MAIN / "feature/review/ReviewEligibilityEvaluator.kt"
        repository = APP_MAIN / "feature/review/ReviewEligibilityRepository.kt"
        self.assertTrue(repository.exists(), "ReviewEligibilityRepository owns review DAO access")
        evaluator_text = evaluator.read_text()
        repository_text = repository.read_text()

        self.assertNotIn("import com.uiery.keep.database.dao.EmergencyUnlockDao", evaluator_text)
        self.assertNotIn("import com.uiery.keep.database.dao.LockHistoryDao", evaluator_text)
        self.assertIn("private val repository: ReviewEligibilityRepository", evaluator_text)
        self.assertIn("import com.uiery.keep.database.dao.EmergencyUnlockDao", repository_text)
        self.assertIn("import com.uiery.keep.database.dao.LockHistoryDao", repository_text)
        self.assertIn("fun countRecentEmergencyUnlocks", repository_text)
        self.assertIn("fun countRecentSuccessfulSessions", repository_text)

    def test_goal_lock_feature_viewmodels_use_repository_boundary(self):
        offenders: list[str] = []
        feature_root = APP_MAIN / "feature/goallock"
        import_pattern = re.compile(r"^import\s+com\.uiery\.keep\.database\.dao\.GoalLockDao\b", re.MULTILINE)

        for source in self.kotlin_sources(feature_root):
            if source.name.endswith("Repository.kt"):
                continue
            relative = source.relative_to(REPO_ROOT)
            if import_pattern.search(source.read_text()):
                offenders.append(str(relative))

        self.assertEqual(
            [],
            offenders,
            "goal-lock UI must depend on GoalLockRepository, not Room DAO directly",
        )

    def test_goal_lock_repository_is_the_feature_allowlisted_dao_boundary(self):
        repository = APP_MAIN / "feature/goallock/GoalLockRepository.kt"
        self.assertTrue(repository.exists(), "GoalLockRepository owns feature goal-lock DAO access")
        text = repository.read_text()
        self.assertIn("class GoalLockRepository", text)
        self.assertIn("import com.uiery.keep.database.dao.GoalLockDao", text)
        self.assertIn("fun create", text)
        self.assertIn("fun fetch", text)
        self.assertIn("fun fetchAll", text)
        self.assertIn("fun update", text)

    def test_home_viewmodel_uses_goal_lock_repository_boundary(self):
        home = APP_MAIN / "feature/home/HomeViewModel.kt"
        text = home.read_text()

        self.assertNotIn("import com.uiery.keep.database.dao.GoalLockDao", text)
        self.assertNotIn("import com.uiery.keep.database.entity.GoalLockEntity", text)
        self.assertIn("import com.uiery.keep.feature.goallock.GoalLockRepository", text)
        self.assertIn("import com.uiery.keep.service.LockHistoryRecorder", text)
        self.assertIn("private val goalLockRepository: GoalLockRepository", text)
        self.assertIn("private val lockHistoryRecorder: LockHistoryRecorder", text)

    def test_menu_viewmodel_uses_routine_repository_boundary(self):
        menu = APP_MAIN / "feature/menu/MenuViewModel.kt"
        repository = APP_MAIN / "feature/routine/RoutineRepository.kt"
        text = menu.read_text()

        self.assertTrue(repository.exists(), "RoutineRepository owns menu routine read DAO access")
        self.assertNotIn("import com.uiery.keep.database.dao.RoutineDao", text)
        self.assertNotIn("import com.uiery.keep.database.entity.RoutineEntity", text)
        self.assertIn("import com.uiery.keep.feature.routine.RoutineRepository", text)
        self.assertIn("private val routineRepository: RoutineRepository", text)

    def test_lock_viewmodel_uses_routine_repository_boundary(self):
        lock = APP_MAIN / "feature/lock/LockViewModel.kt"
        repository = APP_MAIN / "feature/routine/RoutineRepository.kt"
        text = lock.read_text()

        self.assertTrue(repository.exists(), "RoutineRepository owns lock routine read DAO access")
        self.assertNotIn("import com.uiery.keep.database.dao.RoutineDao", text)
        self.assertNotIn("import com.uiery.keep.database.dao.EmergencyUnlockDao", text)
        self.assertNotIn("import com.uiery.keep.database.entity.RoutineEntity", text)
        self.assertNotIn("import com.uiery.keep.database.entity.EmergencyUnlockEntity", text)
        self.assertIn("import com.uiery.keep.feature.routine.RoutineRepository", text)
        self.assertIn("import com.uiery.keep.service.LockHistoryRecorder", text)
        self.assertIn("private val routineRepository: RoutineRepository", text)
        self.assertIn("private val lockHistoryRecorder: LockHistoryRecorder", text)

    def test_routine_feature_non_repository_sources_use_repository_boundary(self):
        offenders: list[str] = []
        feature_root = APP_MAIN / "feature/routine"
        import_pattern = re.compile(r"^import\s+com\.uiery\.keep\.database\.dao\.RoutineDao\b", re.MULTILINE)

        for source in self.kotlin_sources(feature_root):
            if source.name.endswith("Repository.kt"):
                continue
            relative = source.relative_to(REPO_ROOT)
            if import_pattern.search(source.read_text()):
                offenders.append(str(relative))

        self.assertEqual(
            [],
            offenders,
            "routine feature sources must depend on RoutineRepository, not RoutineDao directly",
        )

    def test_routine_repository_is_the_feature_allowlisted_dao_boundary(self):
        repository = APP_MAIN / "feature/routine/RoutineRepository.kt"
        self.assertTrue(repository.exists(), "RoutineRepository owns routine DAO access")
        text = repository.read_text()
        self.assertIn("interface RoutineRepository", text)
        self.assertIn("class RoomRoutineRepository", text)
        self.assertIn("import com.uiery.keep.database.dao.RoutineDao", text)
        self.assertIn("fun fetchAll", text)
        self.assertIn("suspend fun fetch", text)
        self.assertIn("suspend fun fetchAllOnce", text)
        self.assertIn("suspend fun insert", text)
        self.assertIn("suspend fun update", text)
        self.assertIn("suspend fun deleteById", text)
        self.assertIn("suspend fun updateIsEnabledById", text)

    def test_routine_receivers_use_routine_repository_boundary(self):
        offenders: list[str] = []
        receiver_root = APP_MAIN / "receiver"
        receiver_names = {"BootReceiver.kt", "RoutineAlarmReceiver.kt"}
        import_pattern = re.compile(r"^import\s+com\.uiery\.keep\.database\.dao\.RoutineDao\b", re.MULTILINE)

        for source in self.kotlin_sources(receiver_root):
            if source.name not in receiver_names:
                continue
            relative = source.relative_to(REPO_ROOT)
            text = source.read_text()
            if import_pattern.search(text):
                offenders.append(str(relative))
            self.assertIn(
                "import com.uiery.keep.feature.routine.RoutineRepository",
                text,
                f"{relative} must use RoutineRepository for routine persistence",
            )

        self.assertEqual(
            [],
            offenders,
            "routine receivers must depend on RoutineRepository, not RoutineDao directly",
        )

    def test_accessibility_service_uses_repository_boundaries(self):
        service = APP_MAIN / "service/KeepAccessibilityService.kt"
        text = service.read_text()

        self.assertNotIn("import com.uiery.keep.database.dao.RoutineDao", text)
        self.assertNotIn("import com.uiery.keep.database.dao.GoalLockDao", text)
        self.assertNotIn("import com.uiery.keep.database.entity.RoutineEntity", text)
        self.assertNotIn("import com.uiery.keep.database.entity.GoalLockEntity", text)
        self.assertIn("import com.uiery.keep.feature.routine.RoutineRepository", text)
        self.assertIn("import com.uiery.keep.feature.goallock.GoalLockRepository", text)
        self.assertIn("fun routineRepository(): RoutineRepository", text)
        self.assertIn("fun goalLockRepository(): GoalLockRepository", text)

    def test_emergency_unlock_coordinator_uses_repository_boundary(self):
        coordinator = APP_MAIN / "service/EmergencyUnlockCoordinator.kt"
        repository = APP_MAIN / "service/EmergencyUnlockRepository.kt"
        self.assertTrue(repository.exists(), "EmergencyUnlockRepository owns emergency-unlock DAO access")
        coordinator_text = coordinator.read_text()
        repository_text = repository.read_text()

        self.assertNotIn("import com.uiery.keep.database.dao.EmergencyUnlockDao", coordinator_text)
        self.assertIn("private val repository: EmergencyUnlockRepository", coordinator_text)
        self.assertIn("import com.uiery.keep.database.dao.EmergencyUnlockDao", repository_text)
        self.assertIn("suspend fun insert", repository_text)
        self.assertIn("suspend fun countToday", repository_text)
        self.assertIn("suspend fun countSince", repository_text)
