import importlib.util
import pathlib
import sys
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "verify_lint_registry.py"
spec = importlib.util.spec_from_file_location("verify_lint_registry", MODULE_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError(f"Failed to load module spec for {MODULE_PATH}")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)

FIXTURES_DIR = pathlib.Path(__file__).resolve().parent / "fixtures"


class VerifyLintRegistryTest(unittest.TestCase):
    def test_verify_report_rejects_skipped_navigation_registry(self):
        report = FIXTURES_DIR / "lint_registry_skipped.html"

        with self.assertRaisesRegex(
            module.LintRegistryVerificationError,
            "Forbidden text present: Requires newer lint; these checks will be skipped!",
        ):
            module.verify_report(
                report,
                required_sections=["Included Additional Checks"],
                required_identifiers=["androidx.navigation.compose"],
                required_issue_ids=[
                    "MissingSerializableAnnotation",
                    "MissingKeepAnnotation",
                    "WrongNavigateRouteType",
                ],
                forbidden_texts=[
                    "Requires newer lint; these checks will be skipped!",
                    "ObsoleteLintCustomCheck",
                ],
            )

    def test_verify_report_rejects_missing_required_navigation_issue(self):
        report = FIXTURES_DIR / "lint_registry_missing_issue.html"

        with self.assertRaisesRegex(
            module.LintRegistryVerificationError,
            "Missing required issue id: WrongNavigateRouteType",
        ):
            module.verify_report(
                report,
                required_sections=["Included Additional Checks"],
                required_identifiers=["androidx.navigation.compose"],
                required_issue_ids=[
                    "MissingSerializableAnnotation",
                    "MissingKeepAnnotation",
                    "WrongNavigateRouteType",
                ],
                forbidden_texts=[],
            )

    def test_verify_report_accepts_recovered_navigation_registry(self):
        report = FIXTURES_DIR / "lint_registry_ok.html"

        result = module.verify_report(
            report,
            required_sections=["Included Additional Checks"],
            required_identifiers=["androidx.navigation.compose"],
            required_issue_ids=[
                "MissingSerializableAnnotation",
                "MissingKeepAnnotation",
                "WrongNavigateRouteType",
            ],
            forbidden_texts=[
                "Requires newer lint; these checks will be skipped!",
                "ObsoleteLintCustomCheck",
            ],
        )

        self.assertEqual(result.report_path, report)
        self.assertIn("androidx.navigation.compose", result.matched_identifiers)
        self.assertIn("WrongNavigateRouteType", result.matched_issue_ids)

    def test_cli_exits_zero_for_recovered_report(self):
        report = FIXTURES_DIR / "lint_registry_ok.html"

        exit_code = module.main(
            [
                "--report",
                str(report),
                "--require-section",
                "Included Additional Checks",
                "--require-identifier",
                "androidx.navigation.compose",
                "--require-issue-id",
                "MissingSerializableAnnotation",
                "--require-issue-id",
                "MissingKeepAnnotation",
                "--require-issue-id",
                "WrongNavigateRouteType",
                "--forbid-text",
                "Requires newer lint; these checks will be skipped!",
                "--forbid-text",
                "ObsoleteLintCustomCheck",
            ]
        )

        self.assertEqual(exit_code, 0)


if __name__ == "__main__":
    unittest.main()
