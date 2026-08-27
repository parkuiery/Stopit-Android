"""Branch naming is a convention now, not a CI gate.

The Branch Hygiene workflow was retired: it cost a job on every PR to enforce a
naming rule, and the one routing rule that actually gates behaviour -- main-target
PRs must come from `release/*` or `hotfix/*` -- is independently enforced by
Version Guard, which runs on every main-target PR.

What remains worth pinning is that the convention stays coherent: the docs still
describe it, `scripts/branch-start.sh` still produces conforming names, and
`automation/*` stays reserved for local lane worktrees rather than PR heads.
"""

import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
BRANCH_START_SCRIPT = REPO_ROOT / "scripts" / "branch-start.sh"
VERSION_GUARD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "version-guard.yml"
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
AUTOMATION_OPS_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "automation-ops.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
RECENT_DECISIONS_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "recent-decisions.md"

CONVENTION_BRANCH_TYPES = ("feature", "fix", "refactor", "docs", "test", "ci", "chore")


class BranchHygienePolicyTest(unittest.TestCase):
    def test_branch_hygiene_workflow_is_retired(self):
        self.assertFalse(
            (WORKFLOW_DIR / "branch-hygiene.yml").exists(),
            "Branch Hygiene was retired; reintroducing it needs a deliberate decision, "
            "not a silent restore",
        )

    def test_main_target_routing_is_still_enforced_by_version_guard(self):
        # This is the load-bearing half of the old workflow. Losing it would let a
        # main-target PR skip the release gates that key off the release/* head.
        workflow = VERSION_GUARD_WORKFLOW.read_text()

        self.assertIn('branches: [main]', workflow)
        self.assertIn('head_ref.startswith("release/")', workflow)
        self.assertIn('head_ref.startswith("hotfix/")', workflow)
        self.assertIn("main-target PRs must come from release/* or hotfix/*", workflow)

    def test_branch_start_helper_still_produces_conforming_names(self):
        script = BRANCH_START_SCRIPT.read_text()

        self.assertIn("|".join(CONVENTION_BRANCH_TYPES), script)
        self.assertNotIn("automation)", script)
        self.assertIn("kebab-case", script)

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

    def test_docs_do_not_claim_branch_names_are_ci_enforced(self):
        for doc_path in (GIT_WORKFLOW_DOC, RELEASE_CONTEXT_DOC):
            with self.subTest(doc=doc_path.name):
                doc = doc_path.read_text()
                self.assertNotIn("branch-hygiene.yml", doc)
                self.assertIsNone(
                    re.search(r"Branch Hygiene(가|는)? .*실패해야 한다", doc),
                    f"{doc_path.name} still describes Branch Hygiene as an active gate",
                )


if __name__ == "__main__":
    unittest.main()
