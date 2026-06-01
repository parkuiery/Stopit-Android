import pathlib
import unittest
import xml.etree.ElementTree as ET


ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_NAME = f"{{{ANDROID_NS}}}name"
ANDROID_EXPORTED = f"{{{ANDROID_NS}}}exported"


class AndroidManifestContractTest(unittest.TestCase):
    def setUp(self):
        repo_root = pathlib.Path(__file__).resolve().parents[2]
        manifest_path = repo_root / "app" / "src" / "main" / "AndroidManifest.xml"
        self.manifest = ET.parse(manifest_path).getroot()

    def receiver_named(self, receiver_name: str):
        application = self.manifest.find("application")
        self.assertIsNotNone(application)
        for receiver in application.findall("receiver"):
            if receiver.attrib.get(ANDROID_NAME) == receiver_name:
                return receiver
        self.fail(f"Receiver {receiver_name} not declared in AndroidManifest.xml")

    def test_boot_receiver_is_not_exported_to_external_apps(self):
        receiver = self.receiver_named(".receiver.BootReceiver")

        self.assertEqual("false", receiver.attrib.get(ANDROID_EXPORTED))


if __name__ == "__main__":
    unittest.main()
