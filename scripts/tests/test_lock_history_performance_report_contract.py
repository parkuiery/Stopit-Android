import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


class LockHistoryPerformanceReportContractTest(unittest.TestCase):
    def test_source_of_truth_document_defines_scope_privacy_and_handoff(self):
        doc = read("docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md")

        required_phrases = [
            "Issue: #465",
            "LockHistory",
            "성과 리포트",
            "개인 성과 해석",
            "기록 없음",
            "low-data",
            "잘 막아낸 앱",
            "top apps",
            "앱 이름, package, raw session timestamp, raw app list",
            "lock_history_performance_summary_viewed",
            "lock_history_top_apps_viewed",
            "report_state",
            "session_count_bucket",
            "duration_minutes_bucket",
            "top_apps_count_bucket",
            "PR #485 read-model·UI 구현 develop 반영",
            "code-lane instrumentation 추가",
            "PR #566 TalkBack 자동 baseline develop 반영",
            "rank/app label/block count",
            "Refs #465",
            "Closes #465",
            "python3 -m unittest scripts.tests.test_lock_history_performance_report_contract -v",
            "LockHistoryPerformanceReportAccessibilityTest",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

        self.assertIn("#211", doc, "#465 must stay explicitly separated from the share MVP")
        self.assertIn("#119", doc, "Usage Access personalization must remain out of this MVP")
        self.assertIn("release/tag/Play deploy 후 14일/30일", doc)

    def test_analytics_dictionary_and_ga4_runbook_include_privacy_safe_contract(self):
        dictionary = read("docs/ANALYTICS_EVENT_DICTIONARY.md")
        ga4 = read("docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md")

        for text in (dictionary, ga4):
            self.assertIn("docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md", text)
            self.assertIn("#465", text)
            self.assertIn("lock_history_performance_summary_viewed", text)
            self.assertIn("lock_history_top_apps_viewed", text)
            self.assertIn("period_type", text)
            self.assertIn("report_state", text)
            self.assertIn("session_count_bucket", text)
            self.assertIn("duration_minutes_bucket", text)
            self.assertIn("top_apps_count_bucket", text)
            self.assertIn("앱 이름", text)
            self.assertIn("package", text)
            self.assertIn("raw session", text)

        self.assertIn("LockHistory 성과 리포트 조회성", ga4)
        self.assertIn("GA4 Admin 등록", ga4)
        self.assertIn("14일 관측 전", ga4)
        self.assertIn("PR #485", dictionary)
        self.assertIn("PR #485", ga4)
        self.assertIn("PR #566", dictionary)
        self.assertIn("PR #566", ga4)
        self.assertIn("code-lane instrumentation", dictionary)
        self.assertIn("code-lane instrumentation", ga4)

    def test_high_traffic_product_metrics_docs_link_the_contract(self):
        files = [
            "docs/AGENTS.md",
            "docs/METRICS_ANALYSIS.md",
            "docs/PRODUCT_METRICS_DASHBOARD.md",
            "docs/ops/stopit/product-context.md",
            "docs/ops/stopit/metrics-context.md",
        ]
        for relative_path in files:
            text = read(relative_path)
            self.assertIn("LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md", text, relative_path)
            self.assertIn("#465", text, relative_path)

        dashboard = read("docs/PRODUCT_METRICS_DASHBOARD.md")
        self.assertIn("LockHistory 성과 리포트", dashboard)
        self.assertIn("#211 공유 CTA", dashboard)
        self.assertIn("개인 성과 해석/재방문 동기", dashboard)
        self.assertIn("PR #566", dashboard)

    def test_runtime_qa_checklist_has_repeatable_evidence_template(self):
        checklist = read("docs/QA_RUNTIME_CHECKLIST.md")

        required_phrases = [
            "LockHistory 성과 리포트 QA baseline",
            "Issue: #465",
            "Empty state",
            "Low-data state",
            "Has-history weekly/monthly state",
            "Top apps section",
            "TalkBack",
            "#211 share CTA remains optional",
            "shame/friction wording absent",
            "python3 -m unittest scripts.tests.test_lock_history_performance_report_contract -v",
            "LockHistoryPerformanceReportAccessibilityTest",
            "top app rank/app label/block count",
            "focused contentDescription regression passed",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, checklist)

    def test_contract_records_implementation_without_claiming_release_or_measurement_done(self):
        source = read("docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md")
        high_traffic = "\n".join(
            read(path)
            for path in [
                "docs/ANALYTICS_EVENT_DICTIONARY.md",
                "docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md",
                "docs/PRODUCT_METRICS_DASHBOARD.md",
                "docs/ops/stopit/metrics-context.md",
            ]
        )

        self.assertIn("PR #485", source)
        self.assertIn("PR #566", source)
        self.assertIn("develop`에 반영", source)
        self.assertIn("release/tag/Play deploy", source)
        self.assertIn("14일/30일 readback", source)
        self.assertIn("code-lane instrumentation", high_traffic)
        self.assertNotIn("code-lane 구현 전", source)
        self.assertNotIn("#465 문서 계약 추가 / 코드 구현 전", high_traffic)
        self.assertNotIn("Closes #465를 사용한다", source)
        self.assertIn("문서/ops follow-through PR body는 계속 `Refs #465`를 사용한다", source)


if __name__ == "__main__":
    unittest.main()
