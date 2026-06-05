#!/usr/bin/env python3
"""Generate and verify Stopit signed release AAB provenance manifests.

The manifest is intentionally secret-free: it records artifact identity,
checksum, version metadata, git/GitHub Actions context, and Play upload inputs,
but never records keystore paths, secret names/values, or service-account JSON.
"""

from __future__ import annotations

import argparse
import datetime as dt
import glob
import hashlib
import json
import os
import pathlib
import re
import sys
from typing import Any

SCHEMA_VERSION = 1
VARIANT = "prodRelease"
GRADLE_FILE = pathlib.Path("app/build.gradle.kts")


class ProvenanceError(RuntimeError):
    """Raised for operator-facing provenance validation failures."""


def _read_gradle_versions(path: pathlib.Path = GRADLE_FILE) -> tuple[str, int]:
    text = path.read_text(encoding="utf-8")
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    version_code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not version_name:
        raise ProvenanceError(f"Unable to resolve versionName from {path}")
    if not version_code:
        raise ProvenanceError(f"Unable to resolve versionCode from {path}")
    return version_name.group(1), int(version_code.group(1))


def _resolve_single_aab(pattern: str) -> pathlib.Path:
    matches = sorted(pathlib.Path(match) for match in glob.glob(pattern))
    if len(matches) != 1:
        rendered = ", ".join(str(match) for match in matches) or "none"
        raise ProvenanceError(
            f"Expected exactly one AAB for pattern {pattern!r}; found {len(matches)}: {rendered}"
        )
    return matches[0]


def _sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _nullable(value: str | None) -> str | None:
    if value is None or value == "":
        return None
    return value


def _run_url() -> str | None:
    server = os.environ.get("GITHUB_SERVER_URL")
    repo = os.environ.get("GITHUB_REPOSITORY")
    run_id = os.environ.get("GITHUB_RUN_ID")
    if server and repo and run_id:
        return f"{server}/{repo}/actions/runs/{run_id}"
    return None


def _build_manifest(args: argparse.Namespace) -> dict[str, Any]:
    aab = _resolve_single_aab(args.aab_glob)
    version_name, version_code = _read_gradle_versions()
    stat = aab.stat()
    return {
        "schema_version": SCHEMA_VERSION,
        "package_name": args.package_name,
        "artifact_name": args.artifact_name,
        "artifact": {
            "path": aab.as_posix(),
            "file_name": aab.name,
            "sha256": _sha256(aab),
            "size_bytes": stat.st_size,
        },
        "android": {
            "variant": VARIANT,
            "version_name": version_name,
            "version_code": version_code,
        },
        "git": {
            "sha": _nullable(os.environ.get("GITHUB_SHA")),
            "ref": _nullable(os.environ.get("GITHUB_REF")),
            "ref_name": _nullable(os.environ.get("GITHUB_REF_NAME")),
            "ref_type": _nullable(os.environ.get("GITHUB_REF_TYPE")),
        },
        "github_actions": {
            "workflow": _nullable(os.environ.get("GITHUB_WORKFLOW")),
            "run_id": _nullable(os.environ.get("GITHUB_RUN_ID")),
            "run_attempt": _nullable(os.environ.get("GITHUB_RUN_ATTEMPT")),
            "run_url": _run_url(),
        },
        "play": {
            "upload_mode": args.upload_mode,
            "track": _nullable(args.track),
            "release_status": _nullable(args.release_status),
            "rollout_fraction": _nullable(args.rollout_fraction),
        },
        "generated_at_utc": dt.datetime.now(dt.timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z"),
    }


def generate(args: argparse.Namespace) -> int:
    manifest = _build_manifest(args)
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote release provenance manifest: {output}")
    print(
        "AAB sha256={sha} size={size} versionName={name} versionCode={code}".format(
            sha=manifest["artifact"]["sha256"],
            size=manifest["artifact"]["size_bytes"],
            name=manifest["android"]["version_name"],
            code=manifest["android"]["version_code"],
        )
    )
    return 0


def _expect(label: str, actual: Any, expected: Any) -> None:
    if actual != expected:
        raise ProvenanceError(f"{label} mismatch: manifest={actual!r}, current={expected!r}")


def verify(args: argparse.Namespace) -> int:
    manifest_path = pathlib.Path(args.manifest)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    aab = _resolve_single_aab(args.aab_glob)
    version_name, version_code = _read_gradle_versions()
    stat = aab.stat()

    _expect("schema_version", manifest.get("schema_version"), SCHEMA_VERSION)
    _expect("package_name", manifest.get("package_name"), args.package_name)
    _expect("artifact.path", manifest.get("artifact", {}).get("path"), aab.as_posix())
    _expect("artifact.file_name", manifest.get("artifact", {}).get("file_name"), aab.name)
    _expect("artifact.sha256", manifest.get("artifact", {}).get("sha256"), _sha256(aab))
    _expect("artifact.size_bytes", manifest.get("artifact", {}).get("size_bytes"), stat.st_size)
    _expect("android.variant", manifest.get("android", {}).get("variant"), VARIANT)
    _expect("android.version_name", manifest.get("android", {}).get("version_name"), version_name)
    _expect("android.version_code", manifest.get("android", {}).get("version_code"), version_code)
    _expect("play.upload_mode", manifest.get("play", {}).get("upload_mode"), args.upload_mode)
    _expect("play.track", manifest.get("play", {}).get("track"), _nullable(args.track))
    _expect(
        "play.release_status",
        manifest.get("play", {}).get("release_status"),
        _nullable(args.release_status),
    )
    _expect(
        "play.rollout_fraction",
        manifest.get("play", {}).get("rollout_fraction"),
        _nullable(args.rollout_fraction),
    )
    print(f"Verified release provenance manifest: {manifest_path}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--aab-glob", required=True)
    common.add_argument("--package-name", required=True)
    common.add_argument("--upload-mode", required=True, choices=["none", "play-upload"])
    common.add_argument("--track", default="")
    common.add_argument("--release-status", default="")
    common.add_argument("--rollout-fraction", default="")

    gen = subparsers.add_parser("generate", parents=[common])
    gen.add_argument("--output", required=True)
    gen.add_argument("--artifact-name", required=True)
    gen.set_defaults(func=generate)

    ver = subparsers.add_parser("verify", parents=[common])
    ver.add_argument("--manifest", required=True)
    ver.set_defaults(func=verify)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except ProvenanceError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
