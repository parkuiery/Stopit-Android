import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (REPO_ROOT / path).read_text(encoding="utf-8")


class HomeStatusCtaStructureContractTest(unittest.TestCase):
    def test_source_of_truth_defines_issue_scope_and_not_implementation_completion(self) -> None:
        doc = read("docs/HOME_STATUS_CTA_STRUCTURE.md")

        self.assertIn("Issue: #463", doc)
        self.assertIn("구현 완료가 아니다", doc)
        self.assertIn("Refs #463", doc)
        self.assertIn("Closes #463", doc)
        self.assertIn("Home UI/resource/test/locale parity/QA evidence", doc)
        self.assertNotIn("Docs-only PR: `Closes #463`", doc)

    def test_home_contract_keeps_status_and_cta_hierarchy_explicit(self) -> None:
        doc = read("docs/HOME_STATUS_CTA_STRUCTURE.md")

        required_terms = [
            "상태 확인 + 다음 행동",
            "꺼짐 + 선택 앱 없음",
            "꺼짐 + 선택 앱 있음 + 첫 잠금 전",
            "꺼짐 + 선택 앱 있음 + 반복 사용자",
            "켜짐",
            "타이머 예약/실행 중",
            "목표 잠금 진행 중",
            "Primary CTA는 한 화면에서 하나만 가장 강해야 한다",
            "상태는 색상만으로 전달하지 않는다",
            "선택 앱 수",
            "즉시 차단과 타이머 설정은 같은 행동처럼 보이면 안 된다",
        ]
        for term in required_terms:
            with self.subTest(term=term):
                self.assertIn(term, doc)

    def test_existing_home_surfaces_are_named_as_baseline_not_deleted(self) -> None:
        doc = read("docs/HOME_STATUS_CTA_STRUCTURE.md")

        for term in [
            "HomeScreen.kt",
            "HomeViewModel.kt",
            "CategoryButton",
            "FirstLockActivationCta",
            "GoalLockProgressCard",
            "changeIsKeep",
            "showFirstLockActivationCta",
            "DESIGN.md",
            "KeepTheme.colors.primary",
            "KeepButton",
        ]:
            with self.subTest(term=term):
                self.assertIn(term, doc)

    def test_activation_and_privacy_guardrails_are_preserved(self) -> None:
        doc = read("docs/HOME_STATUS_CTA_STRUCTURE.md")

        for term in [
            "first_lock_configured",
            "first_core_action_completed",
            "app_block_intercepted",
            "keep_mode_toggled",
            "lock_scheduled",
            "준비 완료 신호",
            "실제 차단 완료가 아니다",
            "pre-#256/#279/#283 baseline",
            "release/tag/Play deploy",
            "14일 관측",
            "앱 이름",
            "package name",
            "raw selected app list",
            "raw session history",
            "privacy-safe enum/bucket",
        ]:
            with self.subTest(term=term):
                self.assertIn(term, doc)

    def test_high_traffic_docs_link_the_contract(self) -> None:
        linked_docs = [
            "docs/AGENTS.md",
            "docs/PRODUCT_METRICS_DASHBOARD.md",
            "docs/METRICS_ANALYSIS.md",
            "docs/ANALYTICS_EVENT_DICTIONARY.md",
            "docs/QA_RUNTIME_CHECKLIST.md",
            "docs/ops/stopit/product-context.md",
            "docs/ops/stopit/metrics-context.md",
        ]

        for path in linked_docs:
            with self.subTest(path=path):
                text = read(path)
                self.assertIn("docs/HOME_STATUS_CTA_STRUCTURE.md", text)
                self.assertIn("#463", text)

    def test_qa_template_and_verification_command_are_discoverable(self) -> None:
        doc = read("docs/HOME_STATUS_CTA_STRUCTURE.md")
        qa = read("docs/QA_RUNTIME_CHECKLIST.md")

        for text in (doc, qa):
            with self.subTest(surface=text[:30]):
                self.assertIn("Home status/CTA QA evidence", text)
                self.assertIn("Text-only state clarity", text)
                self.assertIn("Primary CTA is visually strongest", text)
                self.assertIn(
                    "python3 -m unittest scripts.tests.test_home_status_cta_structure_contract -v",
                    text,
                )


if __name__ == "__main__":
    unittest.main()
