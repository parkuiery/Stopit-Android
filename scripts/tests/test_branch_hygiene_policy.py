import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
BRANCH_HYGIENE_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "branch-hygiene.yml"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
AUTOMATION_OPS_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "automation-ops.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
RECENT_DECISIONS_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "recent-decisions.md"
OPS_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "ops-ci.yml"


class BranchHygienePolicyTest(unittest.TestCase):
    def test_automation_lane_branches_are_not_pr_head_prefixes(self):
        workflow = BRANCH_HYGIENE_WORKFLOW.read_text()
        validate_step = self._step_block(workflow, "Validate PR routing")

        self.assertIn("feature|fix|refactor|docs|test|ci|chore", validate_step)
        routing_conditions = [
            line.strip()
            for line in validate_step.splitlines()
            if "HEAD_REF" in line and "=~" in line
        ]
        allowed_message = next(
            line for line in validate_step.splitlines() if "Allowed:" in line
        )
        self.assertTrue(routing_conditions)
        self.assertTrue(
            all("automation" not in line for line in routing_conditions),
            "automation/* must stay out of Branch Hygiene routing regexes",
        )
        self.assertNotIn("automation/*", allowed_message)
        self.assertIn("automation/* is reserved for local lane worktree branches", validate_step)
        self.assertIn("create a review branch with an allowed prefix", validate_step)

    def test_operator_docs_pin_review_branch_prefix_for_automation_lanes(self):
        git_workflow = GIT_WORKFLOW_DOC.read_text()
        automation_ops = AUTOMATION_OPS_DOC.read_text()
        release_context = RELEASE_CONTEXT_DOC.read_text()
        recent_decisions = RECENT_DECISIONS_DOC.read_text()

        for doc_name, doc in [
            ("GIT_WORKFLOW", git_workflow),
            ("automation-ops", automation_ops),
            ("release-context", release_context),
            ("recent-decisions", recent_decisions),
        ]:
            with self.subTest(doc=doc_name):
                self.assertIn("automation/*", doc)
                self.assertIn("local lane", doc)
                self.assertIn("docs/*", doc)
                self.assertIn("ci/*", doc)

        combined = "\n".join([git_workflow, automation_ops, release_context, recent_decisions])
        self.assertIn("automation/stopit-docs-lane", combined)
        self.assertIn("docs/issue-", combined)
        self.assertIn("ci/issue-", combined)
        self.assertIn("로컬 lane/worktree", combined)
        self.assertIn("PR head", combined)

    def test_dependabot_pr_heads_are_valid_dependency_automation_branches(self):
        workflow = BRANCH_HYGIENE_WORKFLOW.read_text()
        validate_step = self._step_block(workflow, "Validate PR routing")

        self.assertIn("dependabot/", validate_step)
        self.assertIn("Dependabot dependency automation", validate_step)
        self.assertIn("feature/*, fix/*, refactor/*, docs/*, test/*, ci/*, chore/*, dependabot/*", validate_step)

    def test_docs_contract_gate_covers_branch_hygiene_policy(self):
        workflow = OPS_CI_WORKFLOW.read_text()
        self.assertIn("scripts.tests.test_branch_hygiene_policy", workflow)
        self.assertIn("scripts/tests/test_branch_hygiene_policy.py", workflow)
        self.assertIn(".github/workflows/branch-hygiene.yml", workflow)

    def _step_block(self, workflow: str, step_name: str) -> str:
        pattern = rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare step {step_name}")
        if match is None:
            self.fail(f"workflow should declare step {step_name}")
        return match.group("body")
