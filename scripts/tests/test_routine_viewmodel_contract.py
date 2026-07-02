import re
from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[2]
ROUTINE_VIEW_MODEL = REPO_ROOT / "app/src/main/java/com/uiery/keep/feature/routine/RoutineViewModel.kt"
ROUTINE_BOTTOM_SHEET_VIEW_MODEL = REPO_ROOT / "app/src/main/java/com/uiery/keep/feature/routine/RoutineBottomSheetViewModel.kt"
ROUTINE_BOTTOM_SHEET_CONTENT = REPO_ROOT / "app/src/main/java/com/uiery/keep/feature/routine/component/RoutineBottomSheetContent.kt"


class RoutineViewModelContractTest(unittest.TestCase):
    def test_routine_screen_viewmodel_does_not_own_routine_persistence_entrypoints(self):
        source = ROUTINE_VIEW_MODEL.read_text()

        forbidden_patterns = {
            "RoutineViewModel.addRoutine(routineModel)": r"internal\s+fun\s+addRoutine\s*\(\s*routineModel\s*:\s*RoutineModel\s*\)",
            "RoutineViewModel.updateRoutine(routineModel)": r"internal\s+fun\s+updateRoutine\s*\(\s*routineModel\s*:\s*RoutineModel\s*\)",
        }
        for description, pattern in forbidden_patterns.items():
            with self.subTest(description=description):
                self.assertIsNone(
                    re.search(pattern, source),
                    f"{description} must stay out of RoutineViewModel; routine creation/edit persistence belongs to RoutineBottomSheetViewModel.",
                )

    def test_bottom_sheet_remains_the_only_routine_create_edit_submitter(self):
        bottom_sheet_source = ROUTINE_BOTTOM_SHEET_VIEW_MODEL.read_text()
        content_source = ROUTINE_BOTTOM_SHEET_CONTENT.read_text()

        self.assertRegex(bottom_sheet_source, r"internal\s+fun\s+addRoutine\s*\(\s*\)")
        self.assertRegex(bottom_sheet_source, r"internal\s+fun\s+editRoutine\s*\(\s*id\s*:\s*Long\?\s*\)")
        self.assertIn("viewModel.addRoutine()", content_source)
        self.assertIn("viewModel.editRoutine(routine?.id)", content_source)


if __name__ == "__main__":
    unittest.main()
