import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
NOTIFICATION_HELPER = REPO_ROOT / "app/src/main/java/com/uiery/keep/notification/NotificationHelper.kt"
EMERGENCY_UNLOCK_HELPER = REPO_ROOT / "app/src/main/java/com/uiery/keep/service/EmergencyUnlockNotificationHelper.kt"
NOTIFICATION_ICON = REPO_ROOT / "app/src/main/res/drawable/ic_notification_stopit.xml"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs/QA_RUNTIME_CHECKLIST.md"


class NotificationSmallIconContractTest(unittest.TestCase):
    def test_all_runtime_notifications_use_dedicated_small_icon_resource(self):
        expected = "R.drawable.ic_notification_stopit"
        forbidden = {"R.drawable.app_icon", "R.drawable.kepp_icon", "R.mipmap.ic_launcher"}

        for source in (NOTIFICATION_HELPER, EMERGENCY_UNLOCK_HELPER):
            text = source.read_text()
            small_icon_args = re.findall(r"\.setSmallIcon\(([^)]+)\)", text)
            self.assertGreater(small_icon_args, [], f"{source} should build at least one notification")
            self.assertTrue(
                all(arg.strip() == expected for arg in small_icon_args),
                f"{source} must use {expected} for every notification smallIcon: {small_icon_args}",
            )
            for forbidden_icon in forbidden:
                self.assertNotIn(
                    f".setSmallIcon({forbidden_icon})",
                    text,
                    f"{source} must not reuse launcher/full-color assets as notification small icons.",
                )

    def test_notification_small_icon_is_alpha_mask_vector_not_bitmap_or_launcher_asset(self):
        self.assertTrue(NOTIFICATION_ICON.exists(), "Dedicated notification small icon vector is missing")
        tree = ET.parse(NOTIFICATION_ICON)
        root = tree.getroot()
        android = "{http://schemas.android.com/apk/res/android}"

        self.assertEqual(root.tag, "vector")
        self.assertEqual(root.attrib.get(android + "width"), "24dp")
        self.assertEqual(root.attrib.get(android + "height"), "24dp")
        self.assertEqual(root.attrib.get(android + "viewportWidth"), "24")
        self.assertEqual(root.attrib.get(android + "viewportHeight"), "24")

        paths = list(root.iter("path"))
        self.assertGreaterEqual(len(paths), 1)
        for path in paths:
            fill = path.attrib.get(android + "fillColor")
            self.assertIn(
                fill,
                {"#FFFFFFFF", "#FFFFFF", "@android:color/white"},
                "Notification small icon paths should be a white alpha-mask glyph.",
            )
        xml_text = NOTIFICATION_ICON.read_text()
        self.assertNotIn("<bitmap", xml_text)
        self.assertNotIn("ic_launcher", xml_text)
        self.assertNotIn("app_icon", xml_text)
        self.assertNotIn("kepp_icon", xml_text)

    def test_runtime_qa_checklist_includes_small_icon_visual_evidence(self):
        checklist = QA_RUNTIME_CHECKLIST.read_text()
        self.assertIn("notification small icon", checklist)
        self.assertIn("ic_notification_stopit", checklist)
        self.assertIn("흰 사각형", checklist)


if __name__ == "__main__":
    unittest.main()
