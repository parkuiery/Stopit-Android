import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class FcmDeviceRegistrationContractTest(unittest.TestCase):
    def test_source_of_truth_defines_initial_fetch_and_refresh_callback_boundary(self):
        doc = read("docs/FCM_DEVICE_REGISTRATION_CONTRACT.md")

        required_phrases = [
            "issue #1090",
            "MainActivity.fetchAndSaveFcmToken",
            "KeepMessagingService.onNewToken",
            "FcmTokenPersistenceRunner",
            "bounded retry",
            "sanitized Crashlytics reporting",
            "token fetch 실패",
            "token save 실패",
            "lifecycle scope 취소나 저장 예외가 조용히 사라지면 안 된다",
            "docs-lane은 이 계약을 고정하지만 Android wiring 자체를 완료하지 않는다",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_privacy_guardrails_for_fcm_observability_are_locked(self):
        doc = read("docs/FCM_DEVICE_REGISTRATION_CONTRACT.md")

        required_privacy_phrases = [
            "raw FCM token",
            "앱 package/list",
            "사용자 식별자",
            "원본 exception message",
            "Firebase 내부 payload 원문",
        ]
        for phrase in required_privacy_phrases:
            self.assertIn(phrase, doc)

    def test_high_traffic_docs_reference_fcm_contract_and_1090_boundary(self):
        dictionary = read("docs/ANALYTICS_EVENT_DICTIONARY.md")
        qa = read("docs/QA_RUNTIME_CHECKLIST.md")
        agents = read("docs/AGENTS.md")
        engineering = read("docs/ops/stopit/engineering-context.md")

        for text in [dictionary, qa, agents, engineering]:
            self.assertIn("FCM_DEVICE_REGISTRATION_CONTRACT.md", text)

        for text in [dictionary, qa, agents]:
            self.assertIn("#1090", text)

        self.assertIn("초기 token fetch", dictionary)
        self.assertIn("MainActivity.fetchAndSaveFcmToken", dictionary)
        self.assertIn("같은 bounded retry/reporting policy", qa)

    def test_completion_map_keeps_code_lane_boundaries_open(self):
        doc = read("docs/FCM_DEVICE_REGISTRATION_CONTRACT.md")

        open_boundaries = [
            "- [ ] `MainActivity.fetchAndSaveFcmToken()`의 token fetch 성공 후 save 실패가 `FcmTokenPersistenceRunner` 또는 동등한 shared helper의 bounded retry/reporting 경로를 탄다.",
            "- [ ] token fetch 실패와 token save 실패가 sanitized Crashlytics/logging에서 구분된다.",
            "- [ ] raw FCM token, 앱 package/list, 사용자 식별자, 원본 exception message가 Crashlytics/analytics/logcat에 남지 않는다.",
            "- [ ] `KeepMessagingService`와 `MainActivity`가 같은 persistence policy를 공유한다는 focused test와 PR CI evidence가 남는다.",
        ]
        for boundary in open_boundaries:
            self.assertIn(boundary, doc)


if __name__ == "__main__":
    unittest.main()
