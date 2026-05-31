import importlib.util
import pathlib
import sys
import tempfile
import textwrap
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "check_locale_string_parity.py"
spec = importlib.util.spec_from_file_location("check_locale_string_parity", MODULE_PATH)
assert spec is not None
assert spec.loader is not None
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class LocaleStringParityTest(unittest.TestCase):
    def test_reports_missing_translation_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            res_dir = pathlib.Path(tmp)
            default_dir = res_dir / "values"
            locale_dir = res_dir / "values-pt-rBR"
            default_dir.mkdir()
            locale_dir.mkdir()
            (default_dir / "strings.xml").write_text(
                textwrap.dedent(
                    """
                    <resources>
                        <string name="lock_screen_routine_running">%1$s Routine in progress</string>
                        <string name="lock_screen_routines_running">%1$d routines in progress</string>
                    </resources>
                    """
                ).strip(),
                encoding="utf-8",
            )
            (locale_dir / "strings.xml").write_text(
                textwrap.dedent(
                    """
                    <resources>
                        <string name="lock_screen_routine_running">%1$s Rotina em execução</string>
                    </resources>
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.check_locale_string_parity(res_dir)

        self.assertIn(
            module.LocaleStringViolation(
                locale="values-pt-rBR",
                string_name="lock_screen_routines_running",
                problem="missing",
                expected="%1$d",
                actual="",
            ),
            violations,
        )

    def test_reports_placeholder_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            res_dir = pathlib.Path(tmp)
            default_dir = res_dir / "values"
            locale_dir = res_dir / "values-ko"
            default_dir.mkdir()
            locale_dir.mkdir()
            (default_dir / "strings.xml").write_text(
                textwrap.dedent(
                    """
                    <resources>
                        <string name="lock_screen_routines_running">%1$d routines in progress</string>
                    </resources>
                    """
                ).strip(),
                encoding="utf-8",
            )
            (locale_dir / "strings.xml").write_text(
                textwrap.dedent(
                    """
                    <resources>
                        <string name="lock_screen_routines_running">%1$s개의 루틴 실행 중</string>
                    </resources>
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.check_locale_string_parity(res_dir)

        self.assertEqual(
            violations,
            [
                module.LocaleStringViolation(
                    locale="values-ko",
                    string_name="lock_screen_routines_running",
                    problem="placeholder_mismatch",
                    expected="%1$d",
                    actual="%1$s",
                )
            ],
        )


if __name__ == "__main__":
    unittest.main()
