import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
KDS_ROOT = REPO_ROOT / "core" / "kds"


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


if __name__ == "__main__":
    unittest.main()
