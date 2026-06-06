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
        self.assertIn("fun update", text)
