#!/usr/bin/env python3
"""Register GA4 custom dimensions from a declarative spec, idempotently.

GA4 custom dimensions cannot be deleted, only archived, and an event-scoped property
is capped at 50. That makes a wrong write expensive and a duplicate write impossible to
tidy away. Two guards follow from that:

1. **Identity check before any write.** The account holds several similarly named
   properties -- `stopit-be785` (com.uiery.keep, production), `stopit-dev`
   (com.uiery.keep.dev) and an older `keep-dev-40446`. Picking one by name or by a
   number copied out of a doc is how the wrong property gets written. This script
   refuses to run unless the property's Android data stream package matches what the
   caller declared.

2. **Dry run by default.** `--apply` is required to create anything.

Registration is not retroactive: a dimension starts collecting from the moment it
exists, so the window before registration stays permanently unobserved. Registering
early matters more than registering completely.

Auth: Application Default Credentials with the `analytics.edit` scope. gcloud's built-in
OAuth client cannot request that scope (Google blocks it by policy), so the operator
needs their own OAuth client:

    gcloud auth application-default login \\
      --client-id-file=<oauth-client.json> \\
      --scopes=https://www.googleapis.com/auth/analytics.edit,\\
https://www.googleapis.com/auth/cloud-platform

Add `https://www.googleapis.com/auth/analytics.readonly` too if you also want the
Data API metadata readback, which is what proves a dimension is actually queryable
rather than merely created. See docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

ADMIN_BASE_URL = "https://analyticsadmin.googleapis.com/v1beta"
DATA_BASE_URL = "https://analyticsdata.googleapis.com/v1beta"

# Event-scoped custom dimensions are capped per property. Refuse to push past it rather
# than discovering the limit halfway through a batch, leaving a partial registration.
EVENT_SCOPE_LIMIT = 50

PRODUCTION_PROPERTY = "502544175"
PRODUCTION_PACKAGE = "com.uiery.keep"


class RegistrarError(RuntimeError):
    pass


@dataclass(frozen=True)
class DimensionSpec:
    parameter_name: str
    description: str
    scope: str = "EVENT"

    @property
    def display_name(self) -> str:
        # Keep display name identical to the parameter so GA4 explorations, the runbook
        # ledger, and the analytics dictionary all refer to one string.
        return self.parameter_name


# Website blocking (1.9.0). Domains are never sent, so no domain-shaped axis exists here
# by design -- see the Privacy Rules in docs/WEBSITE_BLOCKING_VPN_SPIKE.md.
WEBSITE_BLOCKING_DIMENSIONS = (
    DimensionSpec(
        "website_blocking_status",
        "웹 차단 필터 상태 전환. WebsiteBlockingStatus enum "
        "(Active/Inactive/ConsentDenied/Unavailable/NetworkUnavailable)",
    ),
    DimensionSpec(
        "website_blocking_trigger",
        "루틴 웹 차단 세션을 세운 계기 (routine_alarm/boot/routine_edit)",
    ),
    DimensionSpec("is_granted", "시스템 VPN 동의창 결과"),
    DimensionSpec(
        "displaced_other_vpn",
        "타 VPN 충돌 시 사용자 선택. false면 잠금은 돌지만 웹사이트는 열려 있음",
    ),
    DimensionSpec("entry_surface", "event별 진입 표면. raw route/path 금지"),
)

DIMENSION_SETS: dict[str, tuple[DimensionSpec, ...]] = {
    "website-blocking": WEBSITE_BLOCKING_DIMENSIONS,
}


def access_token() -> str:
    result = subprocess.run(
        ["gcloud", "auth", "application-default", "print-access-token"],
        capture_output=True,
        text=True,
    )
    token = result.stdout.strip()
    if result.returncode != 0 or not token:
        raise RegistrarError(
            "Could not obtain an Application Default Credentials access token. "
            "See this file's docstring for the required gcloud login."
        )
    return token


def api_request(
    url: str,
    token: str,
    *,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(url, data=data)
    request.add_header("Authorization", f"Bearer {token}")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")
        try:
            message = json.loads(body)["error"]["message"]
        except Exception:
            message = body[:400]
        raise RegistrarError(f"HTTP {error.code} from {url}\n{message}") from error


def assert_property_identity(property_id: str, expected_package: str, token: str) -> str:
    """Refuse to write until the property is provably the intended one.

    Name matching is not enough: `stopit-be785` is production while `stopit-dev` is not,
    and neither name says so. The Android data stream package is the fact that does.
    """
    prop = api_request(f"{ADMIN_BASE_URL}/properties/{property_id}", token)
    streams = api_request(
        f"{ADMIN_BASE_URL}/properties/{property_id}/dataStreams", token
    ).get("dataStreams", [])

    packages = {
        stream.get("androidAppStreamData", {}).get("packageName")
        for stream in streams
        if stream.get("androidAppStreamData")
    }
    packages.discard(None)

    if expected_package not in packages:
        raise RegistrarError(
            f"properties/{property_id} ({prop.get('displayName')}) has Android packages "
            f"{sorted(packages) or '[]'}, which does not include the expected "
            f"'{expected_package}'. Refusing to write: custom dimensions cannot be "
            f"deleted, only archived."
        )
    return prop.get("displayName", "?")


def existing_dimensions(property_id: str, token: str) -> list[dict[str, Any]]:
    dimensions: list[dict[str, Any]] = []
    url = f"{ADMIN_BASE_URL}/properties/{property_id}/customDimensions?pageSize=200"
    while url:
        page = api_request(url, token)
        dimensions.extend(page.get("customDimensions", []))
        next_token = page.get("nextPageToken")
        url = (
            f"{ADMIN_BASE_URL}/properties/{property_id}/customDimensions"
            f"?pageSize=200&pageToken={next_token}"
            if next_token
            else ""
        )
    return dimensions


def plan(
    specs: tuple[DimensionSpec, ...], existing: list[dict[str, Any]]
) -> tuple[list[DimensionSpec], list[DimensionSpec]]:
    present = {dimension["parameterName"] for dimension in existing}
    missing = [spec for spec in specs if spec.parameter_name not in present]
    already = [spec for spec in specs if spec.parameter_name in present]
    return missing, already


def assert_within_event_limit(
    missing: list[DimensionSpec], existing: list[dict[str, Any]]
) -> None:
    used = sum(1 for dimension in existing if dimension.get("scope") == "EVENT")
    incoming = sum(1 for spec in missing if spec.scope == "EVENT")
    if used + incoming > EVENT_SCOPE_LIMIT:
        raise RegistrarError(
            f"EVENT-scoped dimensions would reach {used + incoming}/{EVENT_SCOPE_LIMIT}. "
            "Archive unused dimensions or narrow the set before registering."
        )


def create(property_id: str, spec: DimensionSpec, token: str) -> None:
    api_request(
        f"{ADMIN_BASE_URL}/properties/{property_id}/customDimensions",
        token,
        payload={
            "parameterName": spec.parameter_name,
            "displayName": spec.display_name,
            "description": spec.description,
            "scope": spec.scope,
        },
    )


def report_queryability(
    property_id: str, specs: tuple[DimensionSpec, ...], token: str
) -> None:
    """Created is not the same as queryable; the runbook insists on separating them."""
    try:
        metadata = api_request(f"{DATA_BASE_URL}/properties/{property_id}/metadata", token)
    except RegistrarError as error:
        print(f"\n조회 가능 여부 확인 생략: {error}".splitlines()[0])
        print(
            "  Data API 는 analytics.readonly 스코프를 따로 요구한다. "
            "등록은 끝났고 조회 확인만 남은 상태다."
        )
        return

    queryable = {dimension.get("apiName") for dimension in metadata.get("dimensions", [])}
    print("\n=== runReport 조회 가능 여부 ===")
    for spec in specs:
        api_name = f"customEvent:{spec.parameter_name}"
        print(f"  {'조회가능' if api_name in queryable else '아직':6} {api_name}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--property-id", default=PRODUCTION_PROPERTY)
    parser.add_argument(
        "--expected-package",
        default=PRODUCTION_PACKAGE,
        help="Android data stream package that proves this is the intended property.",
    )
    parser.add_argument("--set", dest="set_name", default="website-blocking", choices=sorted(DIMENSION_SETS))
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Actually create. Without it this is a dry run.",
    )
    args = parser.parse_args(argv)

    specs = DIMENSION_SETS[args.set_name]
    token = access_token()

    display_name = assert_property_identity(
        args.property_id, args.expected_package, token
    )
    print(
        f"대상: properties/{args.property_id} ({display_name}) "
        f"package={args.expected_package}"
    )

    existing = existing_dimensions(args.property_id, token)
    missing, already = plan(specs, existing)
    assert_within_event_limit(missing, existing)

    used = sum(1 for dimension in existing if dimension.get("scope") == "EVENT")
    print(f"현재 EVENT 범위 {used}/{EVENT_SCOPE_LIMIT} 사용\n")

    for spec in already:
        print(f"  건너뜀  {spec.parameter_name} (이미 등록됨)")
    for spec in missing:
        print(f"  {'생성' if args.apply else '생성예정'}  {spec.parameter_name}")

    if not missing:
        print("\n등록할 것이 없다.")
    elif not args.apply:
        print(f"\n드라이런이다. 실제로 만들려면 --apply 를 붙인다.")
        return 0
    else:
        for spec in missing:
            create(args.property_id, spec, token)
        print(f"\n{len(missing)}개 생성됨.")

    report_queryability(args.property_id, specs, token)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RegistrarError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
