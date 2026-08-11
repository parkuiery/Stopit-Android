"""Website blocking ships to prod: the flag, both manifests, and the Play declaration move together.

Release 1.8.0 (versionCode 35) shipped FOREGROUND_SERVICE_SPECIAL_USE and the
KeepDnsVpnService declaration in the main manifest while WEBSITE_BLOCKING_ENABLED was
false in prod. Google Play rejected the upload edit with "You must let us know whether
your app uses any Foreground Service permissions." The AAB uploaded but the edit never
committed, so nothing reached the internal track. 1.8.1 moved the declaration to the dev
flavor and these tests pinned it there.

The feature is now promoted to prod, so the contract is inverted rather than deleted. A
special-use foreground service is only allowed to sit in the production AAB while users
can actually reach it, and Play only accepts it while the App content foreground service
declaration is filled in. These tests keep those three facts locked to each other: turning
the prod flag back off, or dropping the declaration from the main manifest, breaks here
first instead of at Play upload time.

The @SuppressLint("ForegroundServiceType") that guarded the split no longer has a reason
to exist, so its absence is asserted too — a stale suppression would hide a real missing
foregroundServiceType the next time this manifest changes.
"""

import pathlib
import re
import unittest
import xml.etree.ElementTree as ET
from typing import cast


ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_NAME = f"{{{ANDROID_NS}}}name"
ANDROID_VALUE = f"{{{ANDROID_NS}}}value"
ANDROID_EXPORTED = f"{{{ANDROID_NS}}}exported"
ANDROID_PERMISSION = f"{{{ANDROID_NS}}}permission"
ANDROID_FOREGROUND_SERVICE_TYPE = f"{{{ANDROID_NS}}}foregroundServiceType"

VPN_SERVICE_NAME = ".websiteblocking.KeepDnsVpnService"
SPIKE_ACTIVITY_NAME = ".websiteblocking.KeepDnsVpnSpikeActivity"
SPECIAL_USE_PERMISSION = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
FOREGROUND_SERVICE_PERMISSION = "android.permission.FOREGROUND_SERVICE"
ACCESS_NETWORK_STATE_PERMISSION = "android.permission.ACCESS_NETWORK_STATE"
SPECIAL_USE_SUBTYPE_PROPERTY = "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
SPIKE_DOC = "docs/WEBSITE_BLOCKING_VPN_SPIKE.md"


class WebsiteBlockingManifestBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.repo_root = pathlib.Path(__file__).resolve().parents[2]
        app_src = self.repo_root / "app" / "src"
        self.main_manifest_path = app_src / "main" / "AndroidManifest.xml"
        self.dev_manifest_path = app_src / "dev" / "AndroidManifest.xml"
        self.main_manifest = ET.parse(self.main_manifest_path).getroot()
        self.dev_manifest = ET.parse(self.dev_manifest_path).getroot()
        self.main_manifest_text = self.main_manifest_path.read_text(encoding="utf-8")
        self.gradle_text = (
            self.repo_root / "app" / "build.gradle.kts"
        ).read_text(encoding="utf-8")
        self.service_source_text = (
            app_src
            / "main"
            / "java"
            / "com"
            / "uiery"
            / "keep"
            / "websiteblocking"
            / "KeepDnsVpnService.kt"
        ).read_text(encoding="utf-8")
        self.spike_doc_text = (self.repo_root / SPIKE_DOC).read_text(encoding="utf-8")

    def permission_names(self, manifest: ET.Element) -> set:
        return {
            permission.attrib.get(ANDROID_NAME)
            for permission in manifest.findall("uses-permission")
        }

    def service_named(self, manifest: ET.Element, service_name: str):
        application = manifest.find("application")
        if application is None:
            return None
        for service in application.findall("service"):
            if service.attrib.get(ANDROID_NAME) == service_name:
                return service
        return None

    def activity_named(self, manifest: ET.Element, activity_name: str):
        application = manifest.find("application")
        if application is None:
            return None
        for activity in application.findall("activity"):
            if activity.attrib.get(ANDROID_NAME) == activity_name:
                return activity
        return None

    def website_blocking_flag(self, flavor: str) -> str:
        flavor_block = re.search(
            rf'create\("{flavor}"\)\s*{{(.*?)\n        }}',
            self.gradle_text,
            re.DOTALL,
        )
        self.assertIsNotNone(
            flavor_block,
            f"Could not locate the {flavor} product flavor block in app/build.gradle.kts",
        )
        flag = re.search(
            r'buildConfigField\(\s*"boolean",\s*"WEBSITE_BLOCKING_ENABLED",\s*"(true|false)"\s*\)',
            cast(re.Match, flavor_block).group(1),
        )
        self.assertIsNotNone(
            flag,
            f"{flavor} flavor does not set WEBSITE_BLOCKING_ENABLED",
        )
        return cast(re.Match, flag).group(1)

    def test_prod_enables_website_blocking(self):
        self.assertEqual(
            "true",
            self.website_blocking_flag("prod"),
            "Website blocking is shipped to prod. Turning this flag back off means the "
            "production AAB requests a special-use foreground service users cannot reach, "
            f"which is exactly what Play rejected in 1.8.0. See {SPIKE_DOC}.",
        )

    def test_dev_enables_website_blocking(self):
        self.assertEqual("true", self.website_blocking_flag("dev"))

    def test_main_manifest_declares_the_vpn_service_with_special_use_type(self):
        service = self.service_named(self.main_manifest, VPN_SERVICE_NAME)
        self.assertIsNotNone(
            service,
            "WEBSITE_BLOCKING_ENABLED is true in prod, so the production manifest must "
            f"declare KeepDnsVpnService. See {SPIKE_DOC}.",
        )
        service = cast(ET.Element, service)
        self.assertEqual("false", service.attrib.get(ANDROID_EXPORTED))
        self.assertEqual("specialUse", service.attrib.get(ANDROID_FOREGROUND_SERVICE_TYPE))
        self.assertEqual(
            "android.permission.BIND_VPN_SERVICE",
            service.attrib.get(ANDROID_PERMISSION),
            "Only the system may bind a VpnService",
        )
        self.assertEqual(
            {"android.net.VpnService"},
            {
                action.attrib.get(ANDROID_NAME)
                for intent_filter in service.findall("intent-filter")
                for action in intent_filter.findall("action")
            },
        )

    def test_main_manifest_vpn_service_declares_the_special_use_subtype(self):
        service = cast(ET.Element, self.service_named(self.main_manifest, VPN_SERVICE_NAME))

        properties = {
            prop.attrib.get(ANDROID_NAME): prop.attrib.get(ANDROID_VALUE)
            for prop in service.findall("property")
        }
        self.assertIn(
            SPECIAL_USE_SUBTYPE_PROPERTY,
            properties,
            "Play reviews the special-use subtype string; it must ship in the manifest and "
            "match the Play Console foreground service declaration",
        )
        self.assertTrue(
            properties[SPECIAL_USE_SUBTYPE_PROPERTY],
            "The special-use subtype justification must not be empty",
        )

        metadata = {
            meta.attrib.get(ANDROID_NAME): meta.attrib.get(ANDROID_VALUE)
            for meta in service.findall("meta-data")
        }
        self.assertEqual(
            "false",
            metadata.get("android.net.VpnService.SUPPORTS_ALWAYS_ON"),
            "Keep blocks during locks only; always-on VPN is not a supported mode",
        )

    def test_main_manifest_requests_the_foreground_service_permissions(self):
        permissions = self.permission_names(self.main_manifest)

        for permission in (
            FOREGROUND_SERVICE_PERMISSION,
            SPECIAL_USE_PERMISSION,
            ACCESS_NETWORK_STATE_PERMISSION,
        ):
            with self.subTest(permission=permission):
                self.assertIn(
                    permission,
                    permissions,
                    "The production manifest must request this explicitly rather than "
                    "inheriting it transitively from androidx.work or play-services-ads-api. "
                    "A dependency change must not be able to silently break the VPN.",
                )

    def test_dev_manifest_does_not_redeclare_the_shipped_vpn_entries(self):
        self.assertIsNone(
            self.service_named(self.dev_manifest, VPN_SERVICE_NAME),
            "KeepDnsVpnService now ships in the main manifest and the dev flavor inherits "
            "it through manifest merge. A second declaration only creates drift.",
        )
        self.assertNotIn(
            SPECIAL_USE_PERMISSION,
            self.permission_names(self.dev_manifest),
            "The dev flavor inherits the foreground service permissions from the main manifest",
        )

    def test_dev_manifest_still_declares_the_spike_activity(self):
        self.assertIsNotNone(
            self.activity_named(self.dev_manifest, SPIKE_ACTIVITY_NAME),
            "KeepDnsVpnSpikeActivity source lives in app/src/dev and is the adb entry point "
            f"for the runbook's manual regression checks. See {SPIKE_DOC}.",
        )

    def test_foreground_service_type_suppression_is_removed(self):
        self.assertNotIn(
            '@SuppressLint("ForegroundServiceType")',
            self.service_source_text,
            "The suppression existed only because the prodRelease variant compiled this "
            "class without declaring the service. The service is declared now, so a "
            "leftover suppression would hide a genuinely missing foregroundServiceType.",
        )

    def test_runbook_records_the_play_console_declaration_requirement(self):
        for required_text in (
            "Foreground service permissions",
            "versionCode 35",
        ):
            with self.subTest(required_text=required_text):
                self.assertIn(
                    required_text,
                    self.spike_doc_text,
                    "The runbook must keep recording why this declaration exists, so the "
                    "next person does not rediscover it through a rejected Play upload",
                )


if __name__ == "__main__":
    unittest.main()
