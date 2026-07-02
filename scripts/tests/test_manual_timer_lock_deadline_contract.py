import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
MANUAL_TIMER_CONTRACT = REPO_ROOT / "docs" / "MANUAL_TIMER_LOCK_DEADLINE_CONTRACT.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"


class ManualTimerLockDeadlineContractTest(unittest.TestCase):
    def test_zero_duration_countdown_is_not_a_schedulable_lock_contract(self) -> None:
        contract = MANUAL_TIMER_CONTRACT.read_text()

        self.assertIn("0일 0시간 0분 countdown", contract)
        self.assertIn("`lockTime()`은 `LOCK_TIME` / `START_TIME` 저장과 `lock_scheduled` 계측을 하지 않는다", contract)
        self.assertIn("HomeViewModelActivationAnalyticsTest.lockTimeIgnoresZeroDurationCountdownWithoutPersistingOrTrackingSchedule", contract)

    def test_countdown_and_timer_schedule_duration_semantics_are_explicit(self) -> None:
        dictionary = ANALYTICS_DICTIONARY.read_text()
        runbook = GA4_RUNBOOK.read_text()

        for text in (dictionary, runbook):
            with self.subTest(document="analytics"):
                self.assertIn("countdown은 선택한 `day/hour/minute` duration", text)
                self.assertIn("timer는 예약 deadline까지 남은 시간", text)
                self.assertIn("0분 countdown은 `lock_scheduled`를 보내지 않는다", text)


if __name__ == "__main__":
    unittest.main()
