import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
KOREAN_STRINGS = REPO_ROOT / "app/src/main/res/values-ko/strings.xml"
ASO_DOC = REPO_ROOT / "docs/PLAY_STORE_ASO.md"


_ANDROID_TAG_RE = re.compile(r"<[^>]+>")
_FORMAT_RE = re.compile(r"%\d*\$?[sd]")


def _visible_text(value: str) -> str:
    return _FORMAT_RE.sub("", _ANDROID_TAG_RE.sub("", value)).strip()


class KoreanBrandCopyContractTest(unittest.TestCase):
    def test_korean_visible_strings_use_hangul_brand_name(self) -> None:
        root = ET.parse(KOREAN_STRINGS).getroot()
        offenders: list[str] = []

        for string_node in root.findall("string"):
            name = string_node.attrib.get("name", "<unknown>")
            text = _visible_text("".join(string_node.itertext()))
            if "StopIt" in text or "Keep" in text:
                offenders.append(f"{name}: {text}")

        self.assertEqual(
            [],
            offenders,
            "values-ko user-visible strings should use '스탑잇'; resource keys/internal code names are out of scope.",
        )

    def test_aso_runbook_records_in_app_korean_brand_boundary(self) -> None:
        doc = ASO_DOC.read_text(encoding="utf-8")

        required_terms = [
            "인앱 한국어 사용자 노출 문자열",
            "`values-ko/strings.xml`",
            "`스탑잇`",
            "영문/다국어 locale은 `StopIt`",
            "python3 -m unittest scripts.tests.test_korean_brand_copy_contract -v",
            "#510",
        ]
        for term in required_terms:
            with self.subTest(term=term):
                self.assertIn(term, doc)


if __name__ == "__main__":
    unittest.main()
