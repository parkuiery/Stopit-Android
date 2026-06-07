import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (REPO_ROOT / relative_path).read_text(encoding="utf-8")


class UsageStatsPersonalizationContractTest(unittest.TestCase):
    def test_source_doc_tracks_current_issue_and_not_stale_title(self):
        doc = read("docs/USAGE_STATS_PERSONALIZATION_MVP.md")

        self.assertIn("#119 `[백로그] Usage Access 선택형 개인화 discovery 및 승격 게이트 정리`", doc)
        self.assertNotIn("Usage Access 기반 사용기록 리포트 MVP 실행 후보 재검토", doc)
        self.assertIn("backlog 유지", doc)
        self.assertIn("아직 구현 착수용 `ready` 이슈가 아니다", doc)
        self.assertIn("대표님 승격 판단", doc)
        self.assertIn("개인정보 처리방침/Play listing 실제 반영", doc)
        self.assertIn("구현/배포 후 14일·30일 재측정", doc)

    def test_usage_access_is_optional_and_post_core_action_guarded(self):
        doc = read("docs/USAGE_STATS_PERSONALIZATION_MVP.md")

        required_phrases = [
            "첫 실행에서 강제하지 않는다",
            "차단 기능의 기본 가치를 먼저 경험하게 하고",
            "`first_core_action_completed` 이전에는 선택형 설명/후순위 진입점",
            "권한 미허용 시에도 아래는 유지한다",
            "앱 선택 차단",
            "타이머 잠금",
            "루틴 잠금",
            "긴급해제",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_privacy_forbidden_payload_and_local_processing_are_locked(self):
        doc = read("docs/USAGE_STATS_PERSONALIZATION_MVP.md")

        required_phrases = [
            "raw usage history 외부 전송 금지(MVP)",
            "집계/추천은 로컬 처리 우선",
            "앱 이름/package는 analytics에 보내지 않는다",
            "메시지/콘텐츠 내용 조회",
            "외부 서버로 원시 사용기록 업로드",
            "production analytics에 raw app/package/usage history를 보내지 않는 이벤트 계약",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_child_issue_boundaries_prevent_thin_partial_prs(self):
        doc = read("docs/USAGE_STATS_PERSONALIZATION_MVP.md")

        required_phrases = [
            "Discovery/contract child issue 템플릿",
            "MVP implementation child issue 템플릿",
            "#119를 그대로 코드 lane에 넘기지 말고",
            "최소 구현 PR은 앱 runtime, analytics, 문서, QA를 한 번에 맞춘다",
            "이벤트명만 추가하거나 문서만 바꾸는 얇은 PR은 #119의 child implementation으로 보지 않는다",
        ]
        for phrase in required_phrases:
            self.assertIn(phrase, doc)

    def test_high_traffic_docs_and_context_pack_link_to_source(self):
        linked_docs = [
            "docs/AGENTS.md",
            "docs/METRICS_ANALYSIS.md",
            "docs/PRODUCT_METRICS_DASHBOARD.md",
            "docs/QA_RUNTIME_CHECKLIST.md",
            "docs/ROUTINE_RETENTION_COHORT_BASELINE.md",
            "docs/ops/stopit/product-context.md",
            "docs/ops/stopit/metrics-context.md",
        ]
        for relative_path in linked_docs:
            text = read(relative_path)
            self.assertIn("docs/USAGE_STATS_PERSONALIZATION_MVP.md", text, relative_path)
            self.assertIn("#119", text, relative_path)

    def test_docs_agents_points_to_contract_regression(self):
        docs_agents = read("docs/AGENTS.md")

        self.assertIn("USAGE_STATS_PERSONALIZATION_MVP.md", docs_agents)
        self.assertIn("scripts.tests.test_usage_stats_personalization_contract", docs_agents)


if __name__ == "__main__":
    unittest.main()
