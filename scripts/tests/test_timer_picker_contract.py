import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class TimerPickerContractDocsTest(unittest.TestCase):
    def test_home_timer_picker_receives_current_block_time(self):
        source = (ROOT / "app/src/main/java/com/uiery/keep/feature/home/component/TimeBottomSheetContent.kt").read_text()
        timer_case = re.search(r"1\s*->\s*TimerPicker\((?P<args>.*?)\)", source, re.DOTALL)
        self.assertIsNotNone(timer_case, "Timer mode should render shared TimerPicker")
        assert timer_case is not None
        self.assertIn("time = blockTime", timer_case.group("args"))

    def test_routine_start_and_end_pickers_receive_distinct_external_times(self):
        source = (ROOT / "app/src/main/java/com/uiery/keep/feature/routine/component/RoutineTimeContent.kt").read_text()
        self.assertIn("time = startTime", source)
        self.assertIn("time = endTime", source)


if __name__ == "__main__":
    unittest.main()
