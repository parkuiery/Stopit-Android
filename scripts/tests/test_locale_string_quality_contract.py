import pathlib
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"
CONTRACT_DOC = REPO_ROOT / "docs" / "LOCALE_STRING_QUALITY.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"

HOME_STATUS_DESCRIPTION_KEYS = [
    "home_status_no_selected_apps_description",
    "home_status_first_lock_ready_description",
    "home_status_ready_description",
    "home_status_keep_active_description",
]

HIGH_TRAFFIC_NON_DEFAULT_ENGLISH_FORBIDDEN_KEYS = [
    *HOME_STATUS_DESCRIPTION_KEYS,
    "home_status_no_selected_apps_title",
    "home_primary_cta_select_apps",
    "home_primary_cta_start_now",
    "goal_lock_detail_status_completed",
    "goal_lock_detail_status_ended",
    "goal_lock_detail_status_active",
]

FORBIDDEN_KOREAN_TYPOS = ["함꼐", "잠궈줘요"]


def load_strings(strings_xml: pathlib.Path) -> dict[str, str]:
    root = ET.parse(strings_xml).getroot()
    return {
        node.attrib["name"]: "".join(node.itertext()).strip()
        for node in root.findall("string")
    }


class LocaleStringQualityContractTest(unittest.TestCase):
    def test_high_traffic_home_and_goal_lock_strings_are_not_default_english_in_shipped_locales(self) -> None:
        default_strings = load_strings(RES_DIR / "values" / "strings.xml")
        default_values = {
            key: default_strings[key]
            for key in HIGH_TRAFFIC_NON_DEFAULT_ENGLISH_FORBIDDEN_KEYS
        }

        offenders: list[str] = []
        for strings_xml in sorted(RES_DIR.glob("values-*/strings.xml")):
            locale = strings_xml.parent.name
            locale_strings = load_strings(strings_xml)
            for key, default_text in default_values.items():
                localized = locale_strings.get(key, "")
                if localized == default_text:
                    offenders.append(f"{locale}:{key}: still default English")

        self.assertEqual(
            [],
            offenders,
            "High-traffic Home title/CTA/status and Goal Lock status strings must not ship as copied default English in localized values-* resources.",
        )

    def test_korean_confirmed_typos_do_not_regress(self) -> None:
        korean_text = (RES_DIR / "values-ko" / "strings.xml").read_text(encoding="utf-8")

        for typo in FORBIDDEN_KOREAN_TYPOS:
            with self.subTest(typo=typo):
                self.assertNotIn(typo, korean_text)

    def test_locale_quality_contract_is_linked_from_qa_checklist(self) -> None:
        contract = CONTRACT_DOC.read_text(encoding="utf-8")
        checklist = QA_RUNTIME_CHECKLIST.read_text(encoding="utf-8")

        required_contract_terms = [
            "#729",
            "#764",
            "home_status_no_selected_apps_description",
            "home_status_first_lock_ready_description",
            "home_status_ready_description",
            "home_status_keep_active_description",
            "home_status_no_selected_apps_title",
            "home_primary_cta_select_apps",
            "home_primary_cta_start_now",
            "goal_lock_detail_status_completed",
            "goal_lock_detail_status_ended",
            "goal_lock_detail_status_active",
            "StopIt",
            "스탑잇",
            "scripts.tests.test_locale_string_quality_contract",
        ]
        for term in required_contract_terms:
            with self.subTest(term=term):
                self.assertIn(term, contract)

        required_checklist_terms = [
            "Locale string quality / high-traffic Home status copy",
            "Issue: #729",
            "docs/LOCALE_STRING_QUALITY.md",
            "scripts.tests.test_locale_string_quality_contract",
        ]
        for term in required_checklist_terms:
            with self.subTest(term=term):
                self.assertIn(term, checklist)


if __name__ == "__main__":
    unittest.main()
