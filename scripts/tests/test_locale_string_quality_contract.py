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
    "home_status_timed_lock_active_description",
]

HIGH_TRAFFIC_NON_DEFAULT_ENGLISH_FORBIDDEN_KEYS = [
    *HOME_STATUS_DESCRIPTION_KEYS,
    "home_status_no_selected_apps_title",
    "home_status_timed_lock_active_title",
    "home_primary_status_timed_lock_active",
    "home_status_keep_active_title",
    "home_primary_status_keep_active",
    "home_primary_cta_select_apps",
    "home_primary_cta_start_now",
    "emergency_unlock_duration_step_purpose",
    "routine_template_share_payload_title",
    "goal_lock_detail_status_completed",
    "goal_lock_detail_status_ended",
    "goal_lock_detail_status_active",
    "home_goal_lock_card_title_pending",
    "home_goal_lock_card_title_active",
    "home_goal_lock_card_title_completed",
    "home_goal_lock_card_title_ended_early",
    "home_goal_lock_card_summary_pending",
    "home_goal_lock_card_summary_active",
    "home_goal_lock_card_summary_completed",
    "home_goal_lock_card_summary_ended_early",
    "home_goal_lock_card_lock_mode_all_day",
    "home_goal_lock_card_lock_mode_scheduled",
    "goal_lock_detail_end_confirmation",
    "goal_lock_detail_end_cancel",
    "goal_lock_detail_end_confirm",
    "goal_lock_detail_end_cta",
    "parent_mode_active_title",
    "parent_mode_active_accessibility_summary",
    "parent_mode_expired_title",
    "parent_mode_ended_title",
    "parent_mode_active_summary",
    "parent_mode_active_pin_notice",
    "parent_mode_active_extend_ten_minutes",
    "parent_mode_active_end_now",
    "emergency_unlock_duration_helper",
    "routine_template_share_repeat_weekday",
    "routine_template_share_repeat_weekend",
    "routine_template_share_repeat_daily",
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
            "#890",
            "#1007",
            "home_status_no_selected_apps_description",
            "home_status_first_lock_ready_description",
            "home_status_ready_description",
            "home_status_keep_active_description",
            "home_status_timed_lock_active_description",
            "home_status_timed_lock_active_title",
            "home_primary_status_timed_lock_active",
            "home_status_no_selected_apps_title",
            "home_status_keep_active_title",
            "home_primary_status_keep_active",
            "home_primary_cta_select_apps",
            "home_primary_cta_start_now",
            "emergency_unlock_duration_step_purpose",
            "routine_template_share_payload_title",
            "goal_lock_detail_status_completed",
            "goal_lock_detail_status_ended",
            "goal_lock_detail_status_active",
            "home_goal_lock_card_title_pending",
            "home_goal_lock_card_summary_active",
            "goal_lock_detail_end_confirmation",
            "parent_mode_active_pin_notice",
            "parent_mode_active_accessibility_summary",
            "emergency_unlock_duration_helper",
            "routine_template_share_repeat_weekend",
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
