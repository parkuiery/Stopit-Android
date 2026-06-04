import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
APP_BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
ANALYTICS_EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
ANDROID_SKILLS_TESTING_QA = REPO_ROOT / "docs" / "ANDROID_SKILLS_TESTING_QA.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class NotificationMinSdkContractTest(unittest.TestCase):
    def test_notification_docs_match_min_sdk_33_runtime_contract(self):
        build_gradle = APP_BUILD_GRADLE.read_text()
        self.assertRegex(build_gradle, r"minSdk\s*=\s*33\b")

        analytics = ANALYTICS_EVENT_DICTIONARY.read_text()
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()
        qa_strategy = ANDROID_SKILLS_TESTING_QA.read_text()
        release_context = RELEASE_CONTEXT.read_text()

        canonical_phrase = (
            "현재 지원 범위는 minSdk 33 / Android 13+ `POST_NOTIFICATIONS` runtime permission"
        )
        for doc_name, text in {
            "analytics event dictionary": analytics,
            "runtime QA checklist": qa_checklist,
            "Android skills testing QA": qa_strategy,
            "Stopit release context": release_context,
        }.items():
            with self.subTest(doc=doc_name):
                self.assertIn(canonical_phrase, text)
                self.assertIn("POST_NOTIFICATION ignore", text)
                self.assertIn("notification-denied", text)

        active_qa_section = qa_checklist.split("### notification onboarding permission baseline", 1)[1]
        active_qa_section = active_qa_section.split("### Crashlytics startup ANR", 1)[0]
        self.assertNotRegex(active_qa_section, r"^- .*Android 12L 이하.*검증", re.MULTILINE)
        self.assertNotIn("Android 12L 이하 settings round-trip", active_qa_section)

    def test_android_12l_legacy_mentions_are_historical_or_out_of_scope(self):
        docs = {
            "analytics event dictionary": ANALYTICS_EVENT_DICTIONARY.read_text().split("알림 권한 온보딩 계약:", 1)[1].split("| `app_selection_completed`", 1)[0],
            "runtime QA checklist": QA_RUNTIME_CHECKLIST.read_text().split("### notification onboarding permission baseline", 1)[1].split("### Crashlytics startup ANR", 1)[0],
            "Android skills testing QA": ANDROID_SKILLS_TESTING_QA.read_text().split("## Release QA gate", 1)[1].split("## Android CLI 활용", 1)[0],
            "Stopit release context": RELEASE_CONTEXT.read_text().split("- CI: `.github/workflows/android-ci.yml`", 1)[1].split("- Ops CI: `.github/workflows/ops-ci.yml`", 1)[0],
        }
        historical_markers = (
            "historical",
            "현재 검증 대상이 아니다",
            "minSdk를 다시 낮출 때만",
            "not current minSdk 33 QA targets",
        )
        for doc_name, text in docs.items():
            for match in re.finditer(r"Android 12L|legacy 설정|settings_opened", text):
                window = text[max(0, match.start() - 160): match.end() + 220]
                with self.subTest(doc=doc_name, match=match.group(0)):
                    self.assertTrue(
                        any(marker in window for marker in historical_markers),
                        f"Legacy notification mention must be marked historical/out-of-scope: {window}",
                    )


if __name__ == "__main__":
    unittest.main()
