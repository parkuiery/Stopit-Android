"""Website blocking must stay dev-only in the manifest, not just behind a build flag.

Release 1.8.0 (versionCode 35) shipped FOREGROUND_SERVICE_SPECIAL_USE and the
KeepDnsVpnService declaration in the main manifest while WEBSITE_BLOCKING_ENABLED was
false in prod. Google Play rejected the upload edit with "You must let us know whether
your app uses any Foreground Service permissions." The AAB uploaded but the edit never
committed, so nothing reached the internal track.

KeepDnsVpnService.startForegroundForSpike carries @SuppressLint("ForegroundServiceType")
because the prodRelease variant compiles the class without declaring the service. These
tests keep that suppression honest: the prod flag and the two manifests must move
together, so promoting website blocking to prod breaks here first and forces the Play
Console foreground service declaration into the same release.
"""

import pathlib
import re
import unittest
import xml.etree.ElementTree as ET
from typing import cast


ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_NAME = f"{{{ANDROID_NS}}}name"
ANDROID_EXPORTED = f"{{{ANDROID_NS}}}exported"
ANDROID_PERMISSION = f"{{{ANDROID_NS}}}permission"
ANDROID_FOREGROUND_SERVICE_TYPE = f"{{{ANDROID_NS}}}foregroundServiceType"

VPN_SERVICE_NAME = ".websiteblocking.KeepDnsVpnService"
SPECIAL_USE_PERMISSION = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
SPIKE_DOC = "docs/WEBSITE_BLOCKING_VPN_SPIKE.md"


class WebsiteBlockingManifestBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.repo_root = pathlib.Path(__file__).resolve().parents[2]
        app_src = self.repo_root / "app" / "src"
        self.main_manifest = ET.parse(app_src / "main" / "AndroidManifest.xml").getroot()
        self.dev_manifest = ET.parse(app_src / "dev" / "AndroidManifest.xml").getroot()
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

    def test_prod_keeps_website_blocking_disabled(self):
        self.assertEqual(
            "false",
            self.website_blocking_flag("prod"),
            "Enabling website blocking in prod also requires moving the VpnService "
            f"declaration back into the main manifest and completing the Play Console "
            f"foreground service declaration. See {SPIKE_DOC}.",
        )

    def test_dev_enables_website_blocking(self):
        self.assertEqual("true", self.website_blocking_flag("dev"))

    def test_main_manifest_does_not_declare_the_vpn_service(self):
        self.assertIsNone(
            self.service_named(self.main_manifest, VPN_SERVICE_NAME),
            "KeepDnsVpnService must not be declared in the main manifest while website "
            f"blocking is dev-only; Play rejected the 1.8.0 upload for it. See {SPIKE_DOC}.",
        )

    def test_main_manifest_does_not_request_special_use_foreground_service(self):
        self.assertNotIn(
            SPECIAL_USE_PERMISSION,
            self.permission_names(self.main_manifest),
            "The production AAB must not request a special-use foreground service that "
            f"users cannot reach. See {SPIKE_DOC}.",
        )

    def test_dev_manifest_declares_the_vpn_service_with_special_use_type(self):
        service = self.service_named(self.dev_manifest, VPN_SERVICE_NAME)
        self.assertIsNotNone(
            service,
            "The dev flavor must still declare KeepDnsVpnService so the spike keeps working",
        )
        service = cast(ET.Element, service)
        self.assertEqual("false", service.attrib.get(ANDROID_EXPORTED))
        self.assertEqual("specialUse", service.attrib.get(ANDROID_FOREGROUND_SERVICE_TYPE))
        self.assertEqual(
            "android.permission.BIND_VPN_SERVICE",
            service.attrib.get(ANDROID_PERMISSION),
        )

    def test_dev_manifest_requests_special_use_foreground_service(self):
        self.assertIn(SPECIAL_USE_PERMISSION, self.permission_names(self.dev_manifest))

    def test_foreground_service_type_suppression_documents_the_boundary(self):
        self.assertIn(
            '@SuppressLint("ForegroundServiceType")',
            self.service_source_text,
            "startForegroundForSpike needs the suppression while the prodRelease variant "
            "compiles this class without declaring the service",
        )
        self.assertIn(
            SPIKE_DOC,
            self.service_source_text,
            "The suppression must point at the runbook that explains the flavor boundary",
        )
        self.assertIn(
            pathlib.Path(__file__).name,
            self.service_source_text,
            "The suppression must name the contract test that keeps it honest",
        )


if __name__ == "__main__":
    unittest.main()
