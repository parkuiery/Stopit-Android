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


class FocusSummaryShareContractTest(unittest.TestCase):
    def test_runbook_locks_issue_597_localization_debt_boundary(self):
        runbook = RUNBOOK.read_text()

        required_phrases = [
            "Issue: #211",
            "Follow-up debt: #597",
            "#211 repo-internal MVP 구현 완료 / #597 share payload locale resource-template debt 남음",
            "공유문 locale/template 계약",
            "Android string/plural resource 또는 resource-backed text provider",
            "{sessionCount}",
            "{durationText}",
            "{playStoreUrl}",
            "FocusSummarySharePayload.kt",
            "Korean hardcode",
            "FocusSummarySharePayloadTest.kt",
            "`Closes #597`는 실제 runtime payload가 resource/template 기반으로 전환",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, runbook)

        self.assertIn("앱 이름, package, topApps, raw session list, raw timestamp", runbook)
        self.assertIn("locale-aware resource/plural contract", runbook)

    def test_source_still_has_runtime_debt_that_docs_must_not_close(self):
        payload_source = FOCUS_PAYLOAD.read_text()
        runbook = RUNBOOK.read_text()

        for hardcoded_snippet in [
            "이번 주 스탑잇으로",
            "시간",
            "분",
        ]:
            self.assertIn(hardcoded_snippet, payload_source)

        self.assertIn("Closes #597", runbook)
        self.assertIn("runtime payload가 resource/template 기반으로 전환", runbook)
        self.assertIn("Refs #597", runbook)

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
