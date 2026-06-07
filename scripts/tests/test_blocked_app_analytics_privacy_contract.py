import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
PRIVACY_CONTRACT = REPO_ROOT / "docs" / "BLOCKED_APP_ANALYTICS_PRIVACY_CONTRACT.md"
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
FIRST_LOCK_RUNBOOK = REPO_ROOT / "docs" / "FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md"
VERSION_ADOPTION_GATE = REPO_ROOT / "docs" / "VERSION_ADOPTION_METRICS_GATE.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"


class BlockedAppAnalyticsPrivacyContractTest(unittest.TestCase):
    def test_source_of_truth_defines_raw_package_retirement_and_bucket_handoff(self):
        contract = PRIVACY_CONTRACT.read_text()

        required_phrases = [
            "`blocked_app_package` 원문 package name은 GA4/Firebase Analytics payload와 GA4 Admin custom dimension 등록 대상에서 **퇴역(deprecated)**",
            "`blocked_app_category_bucket`을 기본값",
            "GA4 Admin 등록은 `blocked_app_category_bucket`만 대상으로 한다",
            "`blocked_app_package`는 새로 등록하지 않는다",
            "Android code-lane에서 raw package payload 제거 또는 bucket 전환 구현",
            "배포 후 14일",
            "배포 후 30일",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, contract)

        forbidden_payloads = [
            "Android package name (`com.example.app` 형태)",
            "앱 label/name",
            "raw selected app list 또는 `lockApplications`",
            "raw LockHistory row/session list",
        ]
        for phrase in forbidden_payloads:
            self.assertIn(phrase, contract)

    def test_high_traffic_docs_do_not_require_raw_package_registration(self):
        docs = {
            "event_dictionary": EVENT_DICTIONARY.read_text(),
            "ga4_runbook": GA4_RUNBOOK.read_text(),
            "first_lock_runbook": FIRST_LOCK_RUNBOOK.read_text(),
            "version_gate": VERSION_ADOPTION_GATE.read_text(),
            "metrics_context": METRICS_CONTEXT.read_text(),
        }

        for name, text in docs.items():
            self.assertIn("blocked_app_category_bucket", text, name)

        bad_required_phrases = [
            "`blocked_app_package` 등록/metadata 확인",
            "`blocked_app_package`가 실제 `customEvent:*`로 등록",
            "`customEvent:blocked_app_package` 확인 필요",
            "app_block_intercepted` by `block_source/blocked_app_package`",
            "| `blocked_app_package` | Required dimension |",
            "| `blocked_app_package` | `app_block_intercepted`, `first_core_action_completed`, `core_action_completed` | 미확인/등록 필요 |",
        ]
        combined = "\n".join(docs.values())
        for phrase in bad_required_phrases:
            self.assertNotIn(phrase, combined)

        self.assertIn("`blocked_app_package` | Deprecated / 금지", docs["ga4_runbook"])
        self.assertIn("`blocked_app_package` | legacy/deprecated", docs["event_dictionary"])

    def test_navigation_surfaces_link_the_contract(self):
        for path, phrase in [
            (PRODUCT_DASHBOARD, "docs/BLOCKED_APP_ANALYTICS_PRIVACY_CONTRACT.md"),
            (METRICS_ANALYSIS, "docs/BLOCKED_APP_ANALYTICS_PRIVACY_CONTRACT.md"),
            (DOCS_AGENTS, "BLOCKED_APP_ANALYTICS_PRIVACY_CONTRACT.md"),
        ]:
            self.assertIn(phrase, path.read_text())


if __name__ == "__main__":
    unittest.main()
