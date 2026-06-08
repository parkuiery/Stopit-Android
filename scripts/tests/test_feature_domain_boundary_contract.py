import pathlib
import re
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_MAIN = REPO_ROOT / "app/src/main/java/com/uiery/keep"
RUNBOOK = REPO_ROOT / "docs/FEATURE_DOMAIN_BOUNDARY.md"
DOCS_AGENTS = REPO_ROOT / "docs/AGENTS.md"
ENGINEERING_CONTEXT = REPO_ROOT / "docs/ops/stopit/engineering-context.md"

FEATURE_IMPORT_PATTERN = re.compile(
    r"^import\s+(com\.uiery\.keep\.feature\.[A-Za-z0-9_.]+)",
    re.MULTILINE,
)

EXPECTED_FEATURE_IMPORTS = {
    "app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt": [
        "com.uiery.keep.feature.goallock.GoalLockRepository",
        "com.uiery.keep.feature.parentmode.ParentModeSession",
        "com.uiery.keep.feature.parentmode.ParentModeSessionStore",
        "com.uiery.keep.feature.routine.RoutineRepository",
    ],
    "app/src/main/java/com/uiery/keep/service/KeepAccessibilityServiceBlockDecision.kt": [
        "com.uiery.keep.feature.parentmode.ParentModePolicy",
        "com.uiery.keep.feature.parentmode.ParentModeSession",
    ],
    "app/src/main/java/com/uiery/keep/receiver/BootReceiver.kt": [
        "com.uiery.keep.feature.routine.RoutineRepository",
    ],
    "app/src/main/java/com/uiery/keep/receiver/RoutineAlarmReceiver.kt": [
        "com.uiery.keep.feature.routine.RoutineRepository",
    ],
    "app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt": [
        "com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestion",
    ],
    "app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt": [
        "com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestion",
    ],
}


class FeatureDomainBoundaryContractTest(unittest.TestCase):
    def production_boundary_sources(self):
        for relative_root in ("database", "service", "receiver", "analytics"):
            yield from sorted((APP_MAIN / relative_root).rglob("*.kt"))

    def current_feature_import_inventory(self):
        inventory: dict[str, list[str]] = {}
        for source in self.production_boundary_sources():
            imports = sorted(FEATURE_IMPORT_PATTERN.findall(source.read_text()))
            if imports:
                inventory[str(source.relative_to(REPO_ROOT))] = imports
        return inventory

    def test_production_boundary_feature_imports_match_issue_651_inventory(self):
        self.assertEqual(
            EXPECTED_FEATURE_IMPORTS,
            self.current_feature_import_inventory(),
            "database/service/receiver/analytics feature.* imports must not grow; "
            "remove entries from the #651 inventory as code-lane migrates them to shared domain/data boundaries",
        )

    def test_runbook_documents_every_current_import_and_migration_boundary(self):
        runbook = RUNBOOK.read_text()
        self.assertIn("Issue: #651", runbook)
        self.assertIn("현재 production drift inventory", runbook)
        self.assertIn("Migration order", runbook)
        self.assertIn("Closes #651", runbook)

        for relative_path, imports in EXPECTED_FEATURE_IMPORTS.items():
            self.assertIn(relative_path, runbook)
            for import_name in imports:
                self.assertIn(import_name.replace("com.uiery.keep.", ""), runbook)

        for required_phrase in (
            "GoalLock shared domain boundary",
            "Routine runtime repository/use-case boundary",
            "RepeatBlock analytics DTO boundary",
            "LockHistory runtime recording boundary",
        ):
            self.assertIn(required_phrase, runbook)

    def test_high_traffic_docs_link_feature_domain_boundary_contract(self):
        docs_agents = DOCS_AGENTS.read_text()
        engineering_context = ENGINEERING_CONTEXT.read_text()

        self.assertIn("FEATURE_DOMAIN_BOUNDARY.md", docs_agents)
        self.assertIn("#651", docs_agents)
        self.assertIn("FEATURE_DOMAIN_BOUNDARY.md", engineering_context)
        self.assertIn("#651", engineering_context)
        self.assertIn("DAO_BOUNDARY_MAINTENANCE.md", engineering_context)


if __name__ == "__main__":
    unittest.main()
