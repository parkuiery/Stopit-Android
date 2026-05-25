#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PACKAGE_NAME = "com.uiery.keep"
BUILD_FILE = Path("app/build.gradle.kts")
TOKEN_URL = "https://oauth2.googleapis.com/token"
PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
PUBLISHER_BASE_URL = "https://androidpublisher.googleapis.com/androidpublisher/v3"
SEMVER_PATTERN = re.compile(r"\d+\.\d+\.\d+")


class VersionGuardError(RuntimeError):
    pass


@dataclass(frozen=True)
class BuildVersionInfo:
    version_code: int
    version_name: str


@dataclass(frozen=True)
class ValidationResult:
    current_version_code: int
    current_version_name: str
    base_version_code: int
    play_max_version_code: int


@dataclass(frozen=True)
class PlayGuardInputs:
    play_max_version_code: int
    source: str



def parse_build_version_info(text: str) -> BuildVersionInfo:
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code or not name:
        raise VersionGuardError("versionCode/versionName not found")
    version_name = name.group(1)
    if not SEMVER_PATTERN.fullmatch(version_name):
        raise VersionGuardError(f"versionName must be MAJOR.MINOR.PATCH, got {version_name}")
    return BuildVersionInfo(version_code=int(code.group(1)), version_name=version_name)



def parse_build_file(path: Path) -> BuildVersionInfo:
    return parse_build_version_info(path.read_text())



def validate_version_values(
    *,
    version_code: int,
    version_name: str,
    minimum_main_version_code: int,
    minimum_play_version_code: int,
) -> ValidationResult:
    if not SEMVER_PATTERN.fullmatch(version_name):
        raise VersionGuardError(f"versionName must be MAJOR.MINOR.PATCH, got {version_name}")
    if version_code <= minimum_main_version_code:
        raise VersionGuardError(
            f"versionCode must exceed main branch: main={minimum_main_version_code}, "
            f"required>={minimum_main_version_code + 1}, candidate={version_code}"
        )
    if version_code <= minimum_play_version_code:
        raise VersionGuardError(
            f"versionCode must exceed Google Play used max: play={minimum_play_version_code}, "
            f"required>={minimum_play_version_code + 1}, candidate={version_code}"
        )
    return ValidationResult(
        current_version_code=version_code,
        current_version_name=version_name,
        base_version_code=minimum_main_version_code,
        play_max_version_code=minimum_play_version_code,
    )



def validate_build_file(
    build_file: Path,
    *,
    minimum_main_version_code: int,
    minimum_play_version_code: int,
) -> ValidationResult:
    build_info = parse_build_file(build_file)
    return validate_version_values(
        version_code=build_info.version_code,
        version_name=build_info.version_name,
        minimum_main_version_code=minimum_main_version_code,
        minimum_play_version_code=minimum_play_version_code,
    )



def extract_play_max_version_code(payload: dict[str, Any]) -> int:
    version_codes: list[int] = []
    for track in payload.get("tracks", []):
        for release in track.get("releases", []) or []:
            for raw_code in release.get("versionCodes", []) or []:
                version_codes.append(int(raw_code))
    if not version_codes:
        raise VersionGuardError("Google Play track response did not include any versionCodes")
    return max(version_codes)



def _base64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")



def _sign_rs256(signing_input: bytes, private_key_pem: str) -> bytes:
    with tempfile.NamedTemporaryFile("w", delete=False) as key_file:
        key_file.write(private_key_pem)
        key_path = key_file.name
    try:
        proc = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key_path],
            input=signing_input,
            capture_output=True,
            check=False,
        )
    finally:
        try:
            os.remove(key_path)
        except FileNotFoundError:
            pass
    if proc.returncode != 0:
        stderr = proc.stderr.decode("utf-8", "ignore").strip()
        raise VersionGuardError(f"openssl signing failed: {stderr or 'unknown error'}")
    return proc.stdout



def load_service_account(path: str) -> dict[str, Any]:
    try:
        return json.loads(Path(path).read_text())
    except FileNotFoundError as exc:
        raise VersionGuardError(f"service account json not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise VersionGuardError(f"service account json is invalid: {path}") from exc



def fetch_access_token(service_account: dict[str, Any]) -> str:
    client_email = service_account.get("client_email")
    private_key = service_account.get("private_key")
    token_uri = service_account.get("token_uri", TOKEN_URL)
    if not client_email or not private_key:
        raise VersionGuardError("service account json must include client_email and private_key")

    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT"}
    claim = {
        "iss": client_email,
        "scope": PUBLISHER_SCOPE,
        "aud": token_uri,
        "iat": now,
        "exp": now + 3600,
    }
    signing_input = f"{_base64url(json.dumps(header, separators=(',', ':')).encode())}.{_base64url(json.dumps(claim, separators=(',', ':')).encode())}".encode()
    signature = _sign_rs256(signing_input, private_key)
    assertion = f"{signing_input.decode()}.{_base64url(signature)}"

    data = urllib.parse.urlencode(
        {
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": assertion,
        }
    ).encode()
    request = urllib.request.Request(token_uri, data=data, method="POST")
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.loads(response.read().decode())
    except Exception as exc:
        raise VersionGuardError(f"failed to obtain Google OAuth token: {exc}") from exc

    access_token = payload.get("access_token")
    if not access_token:
        raise VersionGuardError("Google OAuth response did not include access_token")
    return access_token



def _authorized_request(url: str, access_token: str, *, method: str = "GET", body: bytes | None = None) -> dict[str, Any]:
    request = urllib.request.Request(url, data=body, method=method)
    request.add_header("Authorization", f"Bearer {access_token}")
    request.add_header("Accept", "application/json")
    if body is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read().decode()
    except Exception as exc:
        raise VersionGuardError(f"Google Play API request failed for {url}: {exc}") from exc
    return json.loads(data) if data else {}



def fetch_play_max_version_code(*, package_name: str, service_account_json_path: str) -> int:
    service_account = load_service_account(service_account_json_path)
    access_token = fetch_access_token(service_account)
    edit_url = f"{PUBLISHER_BASE_URL}/applications/{package_name}/edits"
    edit = _authorized_request(edit_url, access_token, method="POST", body=b"{}")
    edit_id = edit.get("id")
    if not edit_id:
        raise VersionGuardError("Google Play edit creation did not return an edit id")
    try:
        tracks_url = f"{PUBLISHER_BASE_URL}/applications/{package_name}/edits/{edit_id}/tracks"
        tracks = _authorized_request(tracks_url, access_token)
        return extract_play_max_version_code(tracks)
    finally:
        delete_url = f"{PUBLISHER_BASE_URL}/applications/{package_name}/edits/{edit_id}"
        try:
            _authorized_request(delete_url, access_token, method="DELETE")
        except VersionGuardError:
            pass



def resolve_play_guard_inputs(*, package_name: str, service_account_json_path: str | None, fallback_play_max_version_code: int | None) -> PlayGuardInputs:
    if service_account_json_path:
        return PlayGuardInputs(
            play_max_version_code=fetch_play_max_version_code(
                package_name=package_name,
                service_account_json_path=service_account_json_path,
            ),
            source=f"google-play:{service_account_json_path}",
        )
    if fallback_play_max_version_code is not None:
        return PlayGuardInputs(
            play_max_version_code=fallback_play_max_version_code,
            source="fallback:STOPIT_PLAY_MAX_VERSION_CODE",
        )
    raise VersionGuardError(
        "Google Play versionCode guard requires either --service-account-json/GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH "
        "or --fallback-play-max-version-code/STOPIT_PLAY_MAX_VERSION_CODE"
    )



def positive_int(raw: str) -> int:
    value = int(raw)
    if value < 0:
        raise argparse.ArgumentTypeError("must be >= 0")
    return value



def add_common_play_guard_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--service-account-json",
        default=os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH"),
        help="Path to the Google Play service account JSON file. Defaults to GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH.",
    )
    parser.add_argument(
        "--fallback-play-max-version-code",
        type=positive_int,
        default=(
            positive_int(os.environ["STOPIT_PLAY_MAX_VERSION_CODE"])
            if os.environ.get("STOPIT_PLAY_MAX_VERSION_CODE")
            else None
        ),
        help="Fallback max used Play versionCode when live API access is unavailable. Defaults to STOPIT_PLAY_MAX_VERSION_CODE.",
    )
    parser.add_argument(
        "--package-name",
        default=os.environ.get("STOPIT_PACKAGE_NAME", PACKAGE_NAME),
        help=f"Android package name. Defaults to {PACKAGE_NAME} or STOPIT_PACKAGE_NAME.",
    )



def cmd_fetch_play_max(args: argparse.Namespace) -> int:
    play_max = fetch_play_max_version_code(
        package_name=args.package_name,
        service_account_json_path=args.service_account_json,
    )
    print(play_max)
    return 0



def cmd_validate_build(args: argparse.Namespace) -> int:
    inputs = resolve_play_guard_inputs(
        package_name=args.package_name,
        service_account_json_path=args.service_account_json,
        fallback_play_max_version_code=args.fallback_play_max_version_code,
    )
    result = validate_build_file(
        Path(args.build_file),
        minimum_main_version_code=args.minimum_main_version_code,
        minimum_play_version_code=inputs.play_max_version_code,
    )
    print(
        "PASS "
        f"versionName={result.current_version_name} "
        f"versionCode={result.current_version_code} "
        f"main={result.base_version_code} "
        f"play={result.play_max_version_code} "
        f"source={inputs.source}"
    )
    return 0



def cmd_validate_values(args: argparse.Namespace) -> int:
    inputs = resolve_play_guard_inputs(
        package_name=args.package_name,
        service_account_json_path=args.service_account_json,
        fallback_play_max_version_code=args.fallback_play_max_version_code,
    )
    result = validate_version_values(
        version_code=args.version_code,
        version_name=args.version_name,
        minimum_main_version_code=args.minimum_main_version_code,
        minimum_play_version_code=inputs.play_max_version_code,
    )
    print(
        "PASS "
        f"versionName={result.current_version_name} "
        f"versionCode={result.current_version_code} "
        f"main={result.base_version_code} "
        f"play={result.play_max_version_code} "
        f"source={inputs.source}"
    )
    return 0



def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Stopit Google Play versionCode guard")
    subparsers = parser.add_subparsers(dest="command", required=True)

    fetch_parser = subparsers.add_parser(
        "fetch-play-max",
        help="Fetch the highest versionCode currently visible through Google Play tracks.",
    )
    fetch_parser.add_argument(
        "--service-account-json",
        default=os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH"),
        required=os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH") is None,
        help="Path to the Google Play service account JSON file.",
    )
    fetch_parser.add_argument(
        "--package-name",
        default=os.environ.get("STOPIT_PACKAGE_NAME", PACKAGE_NAME),
        help=f"Android package name. Defaults to {PACKAGE_NAME} or STOPIT_PACKAGE_NAME.",
    )
    fetch_parser.set_defaults(func=cmd_fetch_play_max)

    validate_build_parser = subparsers.add_parser(
        "validate-build",
        help="Validate the build file version values against main and Google Play maxima.",
    )
    validate_build_parser.add_argument(
        "--build-file",
        default=str(BUILD_FILE),
        help=f"Path to build.gradle.kts. Defaults to {BUILD_FILE}.",
    )
    validate_build_parser.add_argument(
        "--minimum-main-version-code",
        required=True,
        type=positive_int,
        help="versionCode currently on main/base branch.",
    )
    add_common_play_guard_args(validate_build_parser)
    validate_build_parser.set_defaults(func=cmd_validate_build)

    validate_values_parser = subparsers.add_parser(
        "validate-values",
        help="Validate explicit version values against main and Google Play maxima.",
    )
    validate_values_parser.add_argument("--version-code", required=True, type=positive_int)
    validate_values_parser.add_argument("--version-name", required=True)
    validate_values_parser.add_argument("--minimum-main-version-code", required=True, type=positive_int)
    add_common_play_guard_args(validate_values_parser)
    validate_values_parser.set_defaults(func=cmd_validate_values)

    return parser



def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except VersionGuardError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
