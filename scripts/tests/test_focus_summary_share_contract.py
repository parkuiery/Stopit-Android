import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNBOOK = REPO_ROOT / "docs" / "FOCUS_SUMMARY_SHARE_MVP.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
ANALYTICS_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
FOCUS_PAYLOAD = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep" / "feature" / "lockhistory" / "FocusSummarySharePayload.kt"
RES_ROOT = REPO_ROOT / "app" / "src" / "main" / "res"
SHIPPED_STRING_DIRS = [
    "values",
    "values-de",
    "values-es",
    "values-fr",
    "values-it",
    "values-ja",
    "values-ko",
    "values-nl",
    "values-pt",
    "values-pt-rBR",
    "values-ru",
    "values-zh",
]


class FocusSummaryShareContractTest(unittest.TestCase):
    def test_runbook_locks_issue_597_localization_runtime_contract(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #211",
            "Follow-up debt: #597",
            "#211 repo-internal MVP 구현 완료 / #597 code-lane runtime 전환 완료",
            "공유문 locale/template 계약",
            "Android string/plural resource 또는 resource-backed text provider",
            "{sessionCount}",
            "{durationText}",
            "{playStoreUrl}",
            "FocusSummarySharePayload.kt",
            "AndroidFocusSummaryShareTextProvider",
            "focus_summary_share_payload_text",
            "FocusSummarySharePayloadTest.kt",
            "`Closes #597`는 runtime payload resource/template 전환",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        self.assertIn("앱 이름, package, topApps, raw session list, raw timestamp", runbook)
        self.assertIn("locale-aware resource/plural contract", runbook)

    def test_runtime_payload_uses_resource_backed_provider_without_korean_hardcode(self):
        payload_source = FOCUS_PAYLOAD.read_text()
        runbook = RUNBOOK.read_text()

        for hardcoded_snippet in [
            "이번 주 스탑잇으로",
            "${sessionCount}번",
            "${hours}시간",
            "${minutes}분",
        ]:
            self.assertNotIn(hardcoded_snippet, payload_source)

        for required_source in [
            "AndroidFocusSummaryShareTextProvider",
            "FocusSummaryShareTextProvider",
            "R.string.focus_summary_share_payload_text",
            "R.plurals.focus_summary_share_duration_hour",
            "R.plurals.focus_summary_share_duration_minute",
        ]:
            self.assertIn(required_source, payload_source)

        self.assertIn("#597 code-lane runtime 전환 완료", runbook)
        self.assertIn("focus_summary_share_payload_text", runbook)

    def test_runtime_payload_resources_are_present_in_every_shipped_locale(self):
        required_names = [
            'name="focus_summary_share_payload_text"',
            'name="focus_summary_share_duration_hours_minutes"',
            'name="focus_summary_share_duration_hour"',
            'name="focus_summary_share_duration_minute"',
        ]
        for directory in SHIPPED_STRING_DIRS:
            strings = (RES_ROOT / directory / "strings.xml").read_text()
            for required_name in required_names:
                self.assertIn(required_name, strings, f"{directory} missing {required_name}")

        default_strings = (RES_ROOT / "values" / "strings.xml").read_text()
        self.assertIn("%1$d", default_strings)
        self.assertIn("%2$s", default_strings)
        self.assertIn("%3$s", default_strings)

    def test_analytics_schema_is_explicitly_not_changed_by_localization(self):
        analytics = ANALYTICS_DICTIONARY.read_text()

        for event_name in [
            "focus_summary_share_tapped",
            "focus_summary_share_sheet_opened",
            "focus_summary_share_failed",
        ]:
            self.assertIn(event_name, analytics)

        for parameter in [
            "period_type",
            "session_count_bucket",
            "duration_minutes_bucket",
            "reason",
        ]:
            self.assertIn(parameter, analytics)

        for phrase in [
            "#597은 공유 payload의 locale resource/template debt이며 analytics schema 변경 이슈가 아니다",
            "raw rendered text",
            "raw duration string",
            "custom dimension registration 대상이 아니다",
        ]:
            self.assertIn(phrase, analytics)

    def test_high_traffic_docs_link_issue_597_to_focus_summary_source_of_truth(self):
        documents = [
            PRODUCT_DASHBOARD.read_text(),
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            DOCS_AGENTS.read_text(),
            QA_RUNTIME_CHECKLIST.read_text(),
        ]

        for document in documents:
            self.assertIn("FOCUS_SUMMARY_SHARE_MVP.md", document)
            self.assertIn("#597", document)

        dashboard = PRODUCT_DASHBOARD.read_text()
        self.assertIn("CTA/share sheet title localization", dashboard)
        self.assertIn("payload body localization", dashboard)

    def test_qa_checklist_defines_focus_summary_localization_evidence(self):
        qa_checklist = QA_RUNTIME_CHECKLIST.read_text()

        for phrase in [
            "Focus summary share localization QA evidence",
            "Issue: #597",
            "FocusSummarySharePayloadTest",
            "python3 -m unittest scripts.tests.test_focus_summary_share_contract -v",
            "app/package/topApps/raw session/raw timestamp absent",
            "payload body locale resource/template",
        ]:
            self.assertIn(phrase, qa_checklist)


if __name__ == "__main__":
    unittest.main()
