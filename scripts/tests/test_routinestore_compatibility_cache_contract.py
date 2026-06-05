import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
CONTRACT = REPO_ROOT / "docs" / "ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md"
BACKUP_RESTORE_POLICY = REPO_ROOT / "docs" / "BACKUP_RESTORE_POLICY.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
DATASTORE_AGENTS = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "datastore" / "AGENTS.md"
ENGINEERING_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "engineering-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
ROUTINE_STORE = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "datastore" / "RoutineStore.kt"
RECEIVER_POLICY = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "receiver" / "RoutineReceiverPolicy.kt"
BOOT_RECEIVER = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "receiver" / "BootReceiver.kt"
ROUTINE_ALARM_RECEIVER = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "receiver" / "RoutineAlarmReceiver.kt"
RESTORE_AFTERCARE = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "feature" / "routine" / "RoutineRestoreAftercare.kt"


class RoutineStoreCompatibilityCacheContractTest(unittest.TestCase):
    def test_contract_locks_issue_boundary_and_cache_status(self):
        contract = CONTRACT.read_text()

        for phrase in [
            "Issue: #511",
            "docs-lane 계약 고정 / code-lane 구현 전",
            "legacy compatibility cache",
            "**authoritative source:** Room `routine` table",
            "**compatibility cache:** `PreferencesKey.ROUTINES`",
            "Refs #511",
        ]:
            self.assertIn(phrase, contract)

        self.assertNotIn("Closes #511", contract)

    def test_contract_defines_room_wins_conflict_policy(self):
        contract = CONTRACT.read_text()

        for phrase in [
            "Room과 cache가 불일치하면 Room이 이긴다",
            "storedRoutines",
            "databaseRoutines",
            "stale cache를 authoritative routine으로 승격하지 않는다",
            "blank/malformed",
            "MissingExactAlarmPermission",
            "enabled=false",
        ]:
            self.assertIn(phrase, contract)

    def test_contract_names_current_code_boundaries(self):
        contract = CONTRACT.read_text()

        for phrase in [
            "RoutineStore",
            "RoutineReceiverPolicy.resolveRoutines",
            "BootReceiver.restoreRoutinesForBoot",
            "RoutineAlarmReceiver.handleRoutineAlarm",
            "RoutineRestoreAftercare.rescheduleRestoredEnabledRoutinesFromRoom",
            "BackupRestoreDataStoreKeyPolicy",
            "rehydratedCompatibilityCacheKeys",
        ]:
            self.assertIn(phrase, contract)

    def test_current_code_still_matches_documented_boundary_names(self):
        self.assertIn("PreferencesKey.ROUTINES", ROUTINE_STORE.read_text())
        self.assertIn("readCachedRoutines", ROUTINE_STORE.read_text())
        self.assertIn("writeCachedRoutines", ROUTINE_STORE.read_text())

        receiver_policy = RECEIVER_POLICY.read_text()
        self.assertIn("fun resolveRoutines", receiver_policy)
        self.assertIn("databaseRoutines", receiver_policy)
        self.assertIn("fun shouldRehydrateStoredRoutines", receiver_policy)
        self.assertIn("fun decodeStoredRoutines", receiver_policy)

        for source in [BOOT_RECEIVER.read_text(), ROUTINE_ALARM_RECEIVER.read_text()]:
            self.assertIn("RoutineStore", source)
            self.assertIn("resolveRoutines", source)
            self.assertIn("shouldRehydrateStoredRoutines", source)
            self.assertIn("writeCachedRoutines", source)

        self.assertIn("rescheduleRestoredEnabledRoutinesFromRoom", RESTORE_AFTERCARE.read_text())
        self.assertIn("writeCachedRoutines", RESTORE_AFTERCARE.read_text())

    def test_high_traffic_docs_link_to_contract(self):
        for document in [
            BACKUP_RESTORE_POLICY.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
            DATASTORE_AGENTS.read_text(),
            ENGINEERING_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
        ]:
            self.assertIn("ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md", document)
            self.assertIn("#511", document)


if __name__ == "__main__":
    unittest.main()
