import pathlib
import re
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"

MANUAL_REFILL_STRING_NAMES = {
    "emergency_unlock_settings_manual_reset_button",
    "emergency_unlock_settings_count_management",
    "emergency_unlock_settings_daily_refill_badge",
    "emergency_unlock_settings_manual_refill_title",
    "emergency_unlock_settings_manual_refill_subtitle",
    "emergency_unlock_settings_remaining_count",
    "cd_emergency_unlock_daily_refill_mode",
    "cd_emergency_unlock_manual_refill_mode",
    "cd_emergency_unlock_auto_reset_switch",
    "cd_emergency_unlock_manual_reset_button",
}

FORBIDDEN_VISIBLE_VALUES = {
    "Refill emergency unlock count",
    "Emergency unlock count management",
    "Recommended for most users.",
    "Refill manually",
    "The count will not be refilled automatically after the date changes. Use this when you want a stronger self-limit.",
    "Current remaining count %1$d/%2$d",
    "daily_refill_mode",
    "manual_refill_mode",
    "emergency_unlock_auto_reset_switch",
    "refill_emergency_unlock_count_button",
}

SUPPORTED_NON_DEFAULT_LOCALES = {
    "values-de",
    "values-es",
    "values-fr",
    "values-it",
    "values-ja",
    "values-ko",
    "values-nl",
    "values-pt",
    "values-pt-rBR",
    "values-ru",
    "values-zh",
}


def load_strings(strings_xml: pathlib.Path) -> dict[str, str]:
    tree = ET.parse(strings_xml)
    values: dict[str, str] = {}
    for element in tree.getroot().findall("string"):
        name = element.attrib.get("name")
        if name:
            values[name] = "".join(element.itertext()).strip()
    return values


class EmergencyUnlockManualRefillLocaleContractTest(unittest.TestCase):
    def test_supported_locales_define_manual_refill_keys_with_same_placeholders(self) -> None:
        default_strings = load_strings(RES_DIR / "values" / "strings.xml")
        default_placeholders = {
            name: sorted(re.findall(r"%\d+\$[sd]", default_strings[name]))
            for name in MANUAL_REFILL_STRING_NAMES
        }

        for locale in sorted(SUPPORTED_NON_DEFAULT_LOCALES):
            with self.subTest(locale=locale):
                localized = load_strings(RES_DIR / locale / "strings.xml")
                for name in sorted(MANUAL_REFILL_STRING_NAMES):
                    self.assertIn(name, localized)
                    self.assertEqual(
                        default_placeholders[name],
                        sorted(re.findall(r"%\d+\$[sd]", localized[name])),
                        msg=f"{locale}:{name} placeholder drift",
                    )

    def test_manual_refill_accessibility_labels_do_not_expose_internal_keys(self) -> None:
        for locale in sorted(SUPPORTED_NON_DEFAULT_LOCALES):
            localized = load_strings(RES_DIR / locale / "strings.xml")
            for name in sorted(MANUAL_REFILL_STRING_NAMES):
                with self.subTest(locale=locale, name=name):
                    self.assertNotIn(localized[name], FORBIDDEN_VISIBLE_VALUES)
                    self.assertNotRegex(
                        localized[name],
                        r"^[a-z0-9]+(?:_[a-z0-9]+)+$",
                        msg="User-facing emergency unlock labels must be natural language, not resource-like keys.",
                    )


if __name__ == "__main__":
    unittest.main()
