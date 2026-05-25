import importlib.util
import pathlib
import sys
import tempfile
import textwrap
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "play_version_code_guard.py"
spec = importlib.util.spec_from_file_location("play_version_code_guard", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class ValidateVersionCodeTest(unittest.TestCase):
    def test_validate_version_code_rejects_code_not_above_play_max(self):
        with tempfile.TemporaryDirectory() as tmp:
            build_file = pathlib.Path(tmp) / "build.gradle.kts"
            build_file.write_text(
                textwrap.dedent(
                    """
                    android {
                        defaultConfig {
                            versionCode = 27
                            versionName = \"1.7.5\"
                        }
                    }
                    """
                ).strip()
            )

            with self.assertRaisesRegex(
                module.VersionGuardError,
                "versionCode must exceed Google Play used max: play=27, required>=28, candidate=27",
            ):
                module.validate_build_file(
                    build_file,
                    minimum_main_version_code=26,
                    minimum_play_version_code=27,
                )

    def test_validate_version_code_accepts_code_above_main_and_play(self):
        with tempfile.TemporaryDirectory() as tmp:
            build_file = pathlib.Path(tmp) / "build.gradle.kts"
            build_file.write_text(
                textwrap.dedent(
                    """
                    android {
                        defaultConfig {
                            versionCode = 28
                            versionName = \"1.7.5\"
                        }
                    }
                    """
                ).strip()
            )

            result = module.validate_build_file(
                build_file,
                minimum_main_version_code=26,
                minimum_play_version_code=27,
            )

            self.assertEqual(result.current_version_code, 28)
            self.assertEqual(result.current_version_name, "1.7.5")
            self.assertEqual(result.base_version_code, 26)
            self.assertEqual(result.play_max_version_code, 27)


class TrackParsingTest(unittest.TestCase):
    def test_extract_play_max_version_code_uses_all_track_releases(self):
        payload = {
            "tracks": [
                {"track": "production", "releases": [{"versionCodes": ["24", "25"]}]},
                {"track": "internal", "releases": [{"versionCodes": ["26", "30"]}]},
                {"track": "beta", "releases": [{"versionCodes": ["29"]}]},
            ]
        }

        self.assertEqual(module.extract_play_max_version_code(payload), 30)


class GoogleAuthDefaultsTest(unittest.TestCase):
    def test_fetch_access_token_uses_google_oauth_token_default(self):
        service_account = {
            "client_email": "robot@example.iam.gserviceaccount.com",
            "private_key": "dummy-private-key",
        }

        with mock.patch.object(module, "_sign_rs256", return_value=b"signature") as sign_mock:
            with mock.patch.object(module.urllib.request, "urlopen") as urlopen_mock:
                urlopen_mock.return_value.__enter__.return_value.read.return_value = b'{"access_token":"token-123"}'

                token = module.fetch_access_token(service_account)

        self.assertEqual(token, "token-123")
        self.assertEqual(sign_mock.call_count, 1)
        request = urlopen_mock.call_args.args[0]
        self.assertEqual(request.full_url, "https://oauth2.googleapis.com/token")


if __name__ == "__main__":
    unittest.main()
