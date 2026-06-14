import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
REVIEW_FOLLOWTHROUGH = REPO_ROOT / "docs" / "REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md"
REVIEW_LIFECYCLE = REPO_ROOT / "docs" / "REVIEW_PROMPT_LIFECYCLE.md"
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
GA4_RUNBOOK = REPO_ROOT / "docs" / "GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md"
VERSION_ADOPTION_GATE = REPO_ROOT / "docs" / "VERSION_ADOPTION_METRICS_GATE.md"
DOCS_AGENTS = REPO_ROOT / "docs" / "AGENTS.md"
OPS_CI = REPO_ROOT / ".github" / "workflows" / "ops-ci.yml"

REVIEW_EVENTS = [
    "review_prompt_eligible",
    "review_prompt_shown",
    "review_prompt_skipped",
    "review_prompt_failed",
]


class ReviewPromptPostReleaseFollowthroughDocsTest(unittest.TestCase):
    def test_followthrough_runbook_preserves_release_and_measurement_boundary(self):
        followthrough = REVIEW_FOLLOWTHROUGH.read_text()

        required_boundaries = [
            "PR #308",
            "PR #312",
            "origin/main",
            "v1.7.7",
            "20b8ff4a",
            "f49e7de9",
            "cfff411898fbaac43a5c5bbafb48651091e66be2",
            "e920ea3049bb0a3e192de29d0011298ae9b0a2b5",
            "release/internal/production 배포",
            "14일",
            "30일",
            "Refs #307",
            "배포·14일 관측·Play Console 수동 기록",
        ]
        for phrase in required_boundaries:
            self.assertIn(phrase, followthrough)

        for event_name in REVIEW_EVENTS:
            self.assertIn(event_name, followthrough)

        self.assertIn("PR #308/#312 merge 여부", followthrough)
        self.assertIn("PR #308과 PR #312가 모두 포함된 버전", followthrough)
        self.assertIn("shown = 0", followthrough)
        self.assertIn("최신 코드 회귀로 단정하지 않는다", followthrough)
        self.assertIn("2026-06-14T06:07:23Z metrics snapshot smoke", followthrough)
        self.assertIn("review_prompt_skipped`는 `45 users / 69 events`", followthrough)
        self.assertIn("first_core_action_completed 443 users", followthrough)
        self.assertIn("post-PR-308/#312 회귀가 아니라 release/tag/Play deploy 전 baseline smoke", followthrough)

    def test_ga4_queryability_split_is_not_regressed(self):
        followthrough = REVIEW_FOLLOWTHROUGH.read_text()
        event_dictionary = EVENT_DICTIONARY.read_text()
        ga4_runbook = GA4_RUNBOOK.read_text()

        for document in [followthrough, event_dictionary, ga4_runbook]:
            self.assertIn("customEvent:reason", document)
            self.assertIn("customEvent:error", document)

        self.assertIn("조회 가능", followthrough)
        self.assertIn("review_prompt_skipped` by `customEvent:reason`: 조회 가능", followthrough)
        self.assertIn("review_prompt_failed` by `customEvent:error`: 아직 GA4 Admin/metadata 미등록 경계", followthrough)
        self.assertIn("Field customEvent:error is not a valid dimension", followthrough)
        self.assertIn("review_prompt_skipped.reason", followthrough)
        self.assertIn("review_prompt_failed.error", followthrough)

    def test_play_review_limit_and_version_adoption_guardrail_are_linked(self):
        followthrough = REVIEW_FOLLOWTHROUGH.read_text()
        lifecycle = REVIEW_LIFECYCLE.read_text()
        version_adoption_gate = VERSION_ADOPTION_GATE.read_text()

        self.assertIn("사용자가 실제로 리뷰를 작성했는지", followthrough)
        self.assertIn("사용자가 실제로 리뷰를 남겼는지", lifecycle)
        for document in [followthrough, lifecycle]:
            self.assertIn("Play Console", document)
            self.assertIn("rating count", document)
            self.assertIn("평균 평점", document)

        self.assertIn("#307", version_adoption_gate)
        self.assertIn("최신 버전 active share", followthrough)
        self.assertIn("보류", followthrough)

    def test_docs_entrypoints_and_ops_ci_run_this_regression(self):
        followthrough = REVIEW_FOLLOWTHROUGH.read_text()
        docs_agents = DOCS_AGENTS.read_text()
        ops_ci = OPS_CI.read_text()

        test_module = "scripts.tests.test_review_prompt_post_release_followthrough_docs"
        self.assertIn("REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md", docs_agents)
        self.assertIn(test_module, followthrough)
        self.assertIn(test_module, ops_ci)
        self.assertIn("review prompt post-release boundary regression", followthrough)


if __name__ == "__main__":
    unittest.main()
