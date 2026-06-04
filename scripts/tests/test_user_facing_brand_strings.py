import pathlib
import re
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
LEGACY_BRAND_PATTERN = re.compile(r"(?<![A-Za-z])Keep(?![A-Za-z])")

# String resource names are allowed to keep historical keep_* identifiers; this
# contract only checks the user-visible text value. Add a string name here only
# when the displayed copy intentionally uses Keep as a product/mode name.
ALLOWED_LEGACY_BRAND_STRINGS: set[str] = set()


def iter_user_visible_strings(res_dir: pathlib.Path = RES_DIR):
    for strings_xml in sorted(res_dir.glob("values*/strings.xml")):
        tree = ET.parse(strings_xml)
        for element in tree.getroot().findall("string"):
            name = element.attrib.get("name", "")
            text = "".join(element.itertext())
            yield strings_xml.relative_to(REPO_ROOT), name, text


class UserFacingBrandStringsTest(unittest.TestCase):
    def test_user_visible_strings_do_not_expose_legacy_keep_brand(self):
        violations = []
        for path, name, text in iter_user_visible_strings():
            if name in ALLOWED_LEGACY_BRAND_STRINGS:
                continue
            if LEGACY_BRAND_PATTERN.search(text):
                violations.append(f"{path}:{name}: {text}")

        self.assertEqual(
            violations,
            [],
            "User-facing strings should use StopIt/스탑잇 instead of legacy Keep branding.",
        )

    def test_resource_key_names_do_not_count_as_user_visible_brand_copy(self):
        with self.subTest("legacy key names are safe when value copy is clean"):
            violations = []
            sample = [(pathlib.Path("app/src/main/res/values/strings.xml"), "keep_on_status", "StopIt is on")]
            for path, name, text in sample:
                if name in ALLOWED_LEGACY_BRAND_STRINGS:
                    continue
                if LEGACY_BRAND_PATTERN.search(text):
                    violations.append(f"{path}:{name}: {text}")

            self.assertEqual(violations, [])

    def test_runtime_qa_checklist_tracks_brand_copy_evidence_boundary(self):
        checklist = QA_RUNTIME_CHECKLIST.read_text(encoding="utf-8")

        required_phrases = [
            "StopIt user-facing brand copy QA evidence",
            "Issue: #404",
            "notification_permission_request",
            "block_screen_first_core_action_feedback",
            "legacy Keep brand absent",
            "scripts.tests.test_user_facing_brand_strings",
        ]

        for phrase in required_phrases:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, checklist)


if __name__ == "__main__":
    unittest.main()
