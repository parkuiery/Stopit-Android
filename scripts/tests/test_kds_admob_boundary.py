import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
KDS_ROOT = REPO_ROOT / "core" / "kds"
ADMOB_RUNBOOK = REPO_ROOT / "docs" / "ADMOB_MONETIZATION_RUNBOOK.md"
METRICS_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "metrics-context.md"
PRODUCT_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "product-context.md"
PRODUCT_DASHBOARD = REPO_ROOT / "docs" / "PRODUCT_METRICS_DASHBOARD.md"
EVENT_DICTIONARY = REPO_ROOT / "docs" / "ANALYTICS_EVENT_DICTIONARY.md"
VERSION_CATALOG = REPO_ROOT / "gradle" / "libs.versions.toml"
APP_BUILD = REPO_ROOT / "app" / "build.gradle.kts"


class KdsAdMobBoundaryTest(unittest.TestCase):
    def test_kds_main_source_does_not_import_admob_sdk(self):
        offenders = []
        for path in (KDS_ROOT / "src" / "main").rglob("*.kt"):
            text = path.read_text()
            if "com.google.android.gms.ads" in text:
                offenders.append(path.relative_to(REPO_ROOT).as_posix())

        self.assertEqual(
            [],
            offenders,
            "AdMob SDK runtime imports must stay in the app monetization boundary, not :core:kds.",
        )

    def test_kds_gradle_does_not_depend_on_play_services_ads(self):
        build_file = KDS_ROOT / "build.gradle.kts"
        self.assertNotIn(
            "libs.google.play.services.ads",
            build_file.read_text(),
            ":core:kds must not depend on Google Mobile Ads; keep it in :app/monetization.",
        )

    def test_meta_mediation_adapter_is_versioned_and_owned_by_app(self):
        version_catalog = VERSION_CATALOG.read_text()
        app_build = APP_BUILD.read_text()
        kds_build = (KDS_ROOT / "build.gradle.kts").read_text()

        self.assertIn('playServicesAds = "24.6.0"', version_catalog)
        self.assertIn('metaAudienceNetworkAdapter = "6.20.0.1"', version_catalog)
        self.assertIn('module = "com.google.ads.mediation:facebook"', version_catalog)
        self.assertIn("implementation(libs.meta.audience.network.adapter)", app_build)
        self.assertNotIn("libs.meta.audience.network.adapter", kds_build)

    def test_admob_runbook_documents_meta_mediation_release_gate(self):
        admob_runbook = ADMOB_RUNBOOK.read_text()

        self.assertIn("Meta Audience Network bidding 계약", admob_runbook)
        self.assertIn("Google Mobile Ads `24.6.0`", admob_runbook)
        self.assertIn("Meta adapter `6.20.0.1`", admob_runbook)
        self.assertIn("AdSize.BANNER", admob_runbook)
        self.assertIn("Ad Inspector single ad source test", admob_runbook)

    def test_high_traffic_docs_name_app_monetization_as_admob_owner(self):
        admob_runbook = ADMOB_RUNBOOK.read_text()
        for doc in [
            admob_runbook,
            METRICS_CONTEXT.read_text(),
            PRODUCT_CONTEXT.read_text(),
            PRODUCT_DASHBOARD.read_text(),
            EVENT_DICTIONARY.read_text(),
        ]:
            self.assertIn("PR #563", doc)
            self.assertIn("TrackedBannerAd", doc)

        self.assertIn("KDS / 앱 수익화 runtime ownership 경계", admob_runbook)
        self.assertIn("36cee46158f6b2f11f6b841b2eb191a0871ccf1c", admob_runbook)
        self.assertIn("origin/main`/latest production tag `v1.7.7`에는 없다", admob_runbook)


if __name__ == "__main__":
    unittest.main()
