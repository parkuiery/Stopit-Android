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
