import pathlib
import re
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"
COUNTDOWN_CONTENT = (
    REPO_ROOT
    / "app/src/main/java/com/uiery/keep/feature/lock/component/CountDownContent.kt"
)
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
SUPPORTED_STRING_FILES = sorted(RES_DIR.glob("values*/strings.xml"))


def load_strings(strings_xml: pathlib.Path) -> dict[str, str]:
    tree = ET.parse(strings_xml)
    strings: dict[str, str] = {}
    for element in tree.getroot().findall("string"):
        name = element.attrib.get("name", "")
        strings[name] = "".join(element.itertext())
    return strings


def placeholders(value: str) -> list[str]:
    return sorted(re.findall(r"%\d+\$[sd]", value))


def load_plural_items(strings_xml: pathlib.Path, name: str) -> dict[str, str]:
    tree = ET.parse(strings_xml)
    for plurals in tree.getroot().findall("plurals"):
        if plurals.attrib.get("name") == name:
            return {
                item.attrib.get("quantity", ""): "".join(item.itertext())
                for item in plurals.findall("item")
            }
    return {}


class CountdownDayLocaleContractTest(unittest.TestCase):
    def test_countdown_day_prefix_exists_in_every_locale_with_same_placeholder_contract(self) -> None:
        for strings_xml in SUPPORTED_STRING_FILES:
            with self.subTest(strings_xml=strings_xml.relative_to(REPO_ROOT)):
                plural_items = load_plural_items(strings_xml, "countdown_day_prefix")
                self.assertIn("one", plural_items)
                self.assertIn("other", plural_items)
                for quantity, value in plural_items.items():
                    with self.subTest(quantity=quantity):
                        self.assertEqual(["%1$d"], placeholders(value))
                        self.assertTrue(value.endswith(" "), "day prefix must preserve spacer before HH:mm:ss")

    def test_countdown_content_uses_plural_resource_day_prefix_instead_of_hardcoded_korean_suffix(self) -> None:
        source = COUNTDOWN_CONTENT.read_text(encoding="utf-8")
        self.assertIn("pluralStringResource", source)
        self.assertIn("R.plurals.countdown_day_prefix", source)
        self.assertNotIn('"${d}일 "', source)
        self.assertNotRegex(source, r'text\s*=\s*"\$\{d\}[^\"]*"')
    def test_qa_checklist_names_long_countdown_locale_evidence(self) -> None:
        checklist = QA_RUNTIME_CHECKLIST.read_text(encoding="utf-8")
        self.assertIn("Long countdown locale QA evidence", checklist)
        self.assertIn("scripts.tests.test_countdown_day_locale_contract", checklist)
        self.assertIn("1 day / 2 days", checklist)
        self.assertIn("24시간 미만", checklist)


if __name__ == "__main__":
    unittest.main()
