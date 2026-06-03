import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
FIRST_LOCK_RUNBOOK = REPO_ROOT / "docs" / "FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
METRICS_ANALYSIS = REPO_ROOT / "docs" / "METRICS_ANALYSIS.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
VERSION_ADOPTION_GATE = REPO_ROOT / "docs" / "VERSION_ADOPTION_METRICS_GATE.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"

ACTIVATION_SURFACES = {
    "PR #256": "bce1cda",
    "PR #279": "5c6331d",
    "PR #283": "35c13eb",
}


class FirstLockActivationContractTest(unittest.TestCase):
    def test_activation_release_boundary_is_consistent_across_high_traffic_docs(self):
        first_lock_runbook = FIRST_LOCK_RUNBOOK.read_text()
        release_checklist = RELEASE_CHECKLIST.read_text()
        metrics_analysis = METRICS_ANALYSIS.read_text()
        product_dashboard = PRODUCT_DASHBOARD.read_text()
        version_adoption_gate = VERSION_ADOPTION_GATE.read_text()
        product_context = PRODUCT_CONTEXT.read_text()
        metrics_context = METRICS_CONTEXT.read_text()
        docs_agents = DOCS_AGENTS.read_text()

        for pr, commit_prefix in ACTIVATION_SURFACES.items():
            for document in [
                first_lock_runbook,
                release_checklist,
                product_context,
                metrics_context,
            ]:
                self.assertIn(pr, document)
                self.assertIn(commit_prefix, document)
            self.assertIn(commit_prefix, version_adoption_gate)
        self.assertIn("#256/#279/#283", version_adoption_gate)

        for document in [
            first_lock_runbook,
            metrics_analysis,
            product_dashboard,
            version_adoption_gate,
            product_context,
            metrics_context,
        ]:
            self.assertIn("v1.7.7", document)
            self.assertIn("origin/main", document)
            self.assertIn("보류", document)
            self.assertIn("14일", document)
            self.assertIn("first_lock_configured", document)
            self.assertIn("first_core_action_completed", document)
            self.assertIn("app_block_intercepted", document)

        self.assertIn("FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md", docs_agents)
        self.assertIn("post-fix 결과가 아니라 pre-#256/#279/#283 baseline", first_lock_runbook)
        self.assertIn("CTA/첫 가치 피드백을 다시 만들지 않음", version_adoption_gate)
        self.assertIn("해당 commit 포함 release/tag/Play deploy 이후 14일 창", product_context)
        self.assertIn("해당 commit 포함 release/tag/Play deploy 이후 14일 창", metrics_context)

    def test_release_checklist_requires_numerators_denominators_and_ga4_boundary(self):
        release_checklist = RELEASE_CHECKLIST.read_text()

        required_release_evidence = [
            "whether the release/tag includes the activation surface commits being measured",
            "first_lock_configured` users / `first_open` users",
            "first_core_action_completed` users / `first_lock_configured` users",
            "app_block_intercepted` users / `first_core_action_completed` users",
            "whether #13 GA4 Admin registration still blocks source/app/permission-level breakdowns",
        ]
        for phrase in required_release_evidence:
            self.assertIn(phrase, release_checklist)

    def test_runbook_points_future_docs_lanes_to_this_regression(self):
        first_lock_runbook = FIRST_LOCK_RUNBOOK.read_text()

        self.assertIn("python3 -m unittest scripts.tests.test_first_lock_activation_contract -v", first_lock_runbook)
        self.assertIn("release-boundary contract regression", first_lock_runbook)
        self.assertIn("문서 lane이 #14를 다시 만질 때 이 테스트가 깨지면", first_lock_runbook)


if __name__ == "__main__":
    unittest.main()
