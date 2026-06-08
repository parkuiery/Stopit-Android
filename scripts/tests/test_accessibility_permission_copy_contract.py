import pathlib
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"
CONTRACT_DOC = REPO_ROOT / "docs" / "ACCESSIBILITY_PERMISSION_COPY_CONTRACT.md"
QA_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
PLAY_STORE_ASO = REPO_ROOT / "docs" / "PLAY_STORE_ASO.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"

FORBIDDEN_PHRASES = (
    "Screen Time permission",
    "screen time permission",
    "스크린타임 권한",
    "화면 시간 권한",
    "Bildschirmzeit-Berechtigung",
    "Temps d'écran",
    "Tempo di schermo",
    "Schermtijd toestemming",
    "Permissão de Tempo de Tela",
    "Время экрана",
    "スクリーンタイムの許可",
    "屏幕使用时间权限",
)

REQUIRED_DOC_PHRASES = (
    "Accessibility permission",
    "Android Accessibility Service",
    "Screen Time permission",
    "Play Console Accessibility declaration",
    "accessibility_permission_required",
    "accessibility_permission_description",
    "Refs #642",
)


def _strings_for_locale(locale_dir: pathlib.Path) -> dict[str, str]:
    tree = ET.parse(locale_dir / "strings.xml")
    return {
        elem.attrib["name"]: "".join(elem.itertext())
        for elem in tree.getroot().findall("string")
        if "name" in elem.attrib
    }


class AccessibilityPermissionCopyContractTest(unittest.TestCase):
    def test_permission_copy_uses_accessibility_not_screen_time_in_all_shipped_locales(self):
        checked = []
        for locale_dir in sorted(RES_DIR.glob("values*")):
            strings_file = locale_dir / "strings.xml"
            if not strings_file.exists():
                continue
            strings = _strings_for_locale(locale_dir)
            for key in ("accessibility_permission_required", "accessibility_permission_description"):
                value = strings.get(key, "")
                checked.append((locale_dir.name, key))
                with self.subTest(locale=locale_dir.name, key=key):
                    self.assertTrue(value.strip(), f"{locale_dir.name}/{key} must be translated")
                    self.assertFalse(
                        any(phrase in value for phrase in FORBIDDEN_PHRASES),
                        f"{locale_dir.name}/{key} still uses a Screen Time-style permission phrase: {value!r}",
                    )

        self.assertGreaterEqual(len(checked), 20)

    def test_contract_doc_links_play_policy_qa_and_locale_handoff(self):
        text = CONTRACT_DOC.read_text()
        for phrase in REQUIRED_DOC_PHRASES:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)

        qa_text = QA_CHECKLIST.read_text()
        self.assertIn("ACCESSIBILITY_PERMISSION_COPY_CONTRACT.md", qa_text)
        self.assertIn("Screen Time permission", qa_text)
        self.assertIn("접근성 권한 copy", qa_text)

        aso_text = PLAY_STORE_ASO.read_text()
        self.assertIn("ACCESSIBILITY_PERMISSION_COPY_CONTRACT.md", aso_text)
        self.assertIn("in-app Accessibility permission copy", aso_text)

        product_text = PRODUCT_CONTEXT.read_text()
        self.assertIn("ACCESSIBILITY_PERMISSION_COPY_CONTRACT.md", product_text)
        self.assertIn("#642", product_text)


if __name__ == "__main__":
    unittest.main()
