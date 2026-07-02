import re
import unittest
from pathlib import Path


class QueryAllPackagesPolicyContractTest(unittest.TestCase):
    def setUp(self):
        self.repo_root = Path(__file__).resolve().parents[2]
        self.policy_doc = self.repo_root / "docs" / "QUERY_ALL_PACKAGES_POLICY.md"
        self.manifest = self.repo_root / "app" / "src" / "main" / "AndroidManifest.xml"
        self.main_sources = self.repo_root / "app" / "src" / "main" / "java" / "com" / "uiery" / "keep"

    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_policy_doc_defines_play_review_boundary_and_minimization(self):
        doc = self.read(self.policy_doc)

        for required in (
            "issue #904",
            "QUERY_ALL_PACKAGES",
            "app-selection picker",
            "InstalledAppRepository",
            "SelectableAppPolicy",
            "데이터 최소화",
            "Play Console 권한 선언 문안",
            "StopIt uses broad package visibility only so users can choose",
            "설치 앱 전체 목록",
            "analytics, 광고, 프로파일링, 서버 전송 또는 제3자 공유",
            "blocked_app_category_bucket",
        ):
            with self.subTest(required=required):
                self.assertIn(required, doc)

    def test_manifest_policy_comment_points_to_same_source_of_truth(self):
        manifest = self.read(self.manifest)
        doc = self.read(self.policy_doc)

        for required in (
            "app-selection picker",
            "InstalledAppRepository",
            "SelectableAppPolicy",
        ):
            with self.subTest(required=required):
                self.assertIn(required, manifest)
                self.assertIn(required, doc)

    def test_broad_installed_app_scan_stays_inside_installed_app_repository(self):
        offenders = []
        for source in self.main_sources.rglob("*.kt"):
            text = self.read(source)
            if "getInstalledApplications" in text and source.name != "InstalledAppRepository.kt":
                offenders.append(source.relative_to(self.repo_root).as_posix())
            if "queryIntentActivities" in text:
                offenders.append(source.relative_to(self.repo_root).as_posix())

        self.assertEqual([], offenders)

    def test_operator_docs_link_the_policy_contract(self):
        expected_docs = (
            "docs/AGENTS.md",
            "docs/QA_RUNTIME_CHECKLIST.md",
            "docs/RELEASE_CHECKLIST.md",
            "docs/PLAY_DEPLOYMENT.md",
            "docs/ops/stopit/release-context.md",
            "docs/ops/stopit/engineering-context.md",
        )
        for relative_path in expected_docs:
            with self.subTest(relative_path=relative_path):
                text = self.read(self.repo_root / relative_path)
                self.assertIn("QUERY_ALL_PACKAGES_POLICY.md", text)

    def test_play_review_note_keeps_forbidden_payloads_out(self):
        doc = self.read(self.policy_doc)
        forbidden_positive_claims = (
            "uses the full installed-app list for analytics",
            "uses the full installed-app list for advertising",
            "uses the full installed-app list for profiling",
        )
        for claim in forbidden_positive_claims:
            with self.subTest(claim=claim):
                self.assertNotIn(claim, doc.lower())

        self.assertRegex(
            doc,
            re.compile(r"does not send the full installed-app list", re.IGNORECASE),
        )


if __name__ == "__main__":
    unittest.main()
