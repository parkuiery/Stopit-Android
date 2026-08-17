#!/usr/bin/env python3
"""Read live product metrics from GA4 and Amplitude in one pass.

Stopit measures with two tools that do not see the same thing, so a number from one is
not a check on the other unless the comparison is set up deliberately. This script does
that setup once, in code, instead of leaving it to be re-derived by hand each analysis:

  * GA4 (Firebase) receives the **full** event catalog from **every** shipped version.
  * Amplitude receives a **23-event allowlist**, from the **prod flavor only**, from
    **v1.7.9 onward** (2026-07-07), capped at **180 events per device per calendar
    month** after which events are dropped silently with no marker event.

Four consequences drive the whole design here:

1. Amplitude totals are a floor, never a volume measurement. The cap truncates the
   heaviest users first -- exactly the users a retention question is about.
2. Comparing raw GA4 totals against Amplitude totals compares different populations.
   For any dual-read the GA4 side must be restricted to Amplitude-eligible versions,
   which is why the comparison block queries GA4 by (eventName, appVersion) and sums
   only versions >= AMPLITUDE_MIN_VERSION.
3. Only eventCount is additive across that version subset. Unique users are deduplicated
   per row by GA4, so summing users across versions overcounts; those cells are reported
   as GA4-total-only and flagged rather than silently added.
4. No shared user id exists (GA4 app-instance id vs Amplitude device id, and the app
   never calls setUserId), so the two tools can be compared in aggregate but never
   joined per user.

There are two ways to supply the Amplitude side, because the tools they need differ:

  * `--amplitude-json` takes numbers already pulled through the Amplitude MCP server
    (https://mcp.amplitude.com/mcp, OAuth). This is the normal path: MCP needs no secret
    key, and an agent can fetch segmentation/funnel/retention conversationally.
  * The Dashboard REST API lane runs unattended (cron, CI) where no OAuth session exists,
    and needs an API key + secret key pair.

Either way the comparison math below is the same, and that math -- not the fetching -- is
why this script exists.

Credentials -- never committed, resolved from env or ~/.secrets:

    GA4:        STOPIT_GA4_CREDENTIALS=/path/to/analytics-service-account.json
    Amplitude:  AMPLITUDE_ANALYTICS_API_KEY / AMPLITUDE_ANALYTICS_SECRET_KEY
                (or ~/.secrets/stopit-amplitude.json with api_key/secret_key/region)
                -- only needed for the unattended REST lane, not for MCP.

The Amplitude lane degrades cleanly: with neither source the script still runs and prints
the full GA4 analysis, marking the Amplitude column unavailable rather than failing.

Usage:
    python3 scripts/metrics_read.py                      # last 30 days
    python3 scripts/metrics_read.py --days 7
    python3 scripts/metrics_read.py --amplitude-json amp.json
    python3 scripts/metrics_read.py --json-out report.json

The --amplitude-json file is a flat event -> counts map, matching what the MCP server
returns for an event segmentation query over the same window:

    {"lock_session_start": {"totals": 1820, "uniques": 402}, ...}
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from pathlib import Path
from typing import Any

# --- Fixed identities -------------------------------------------------------------
# The account holds several similarly named GA4 properties (stopit-be785 production,
# stopit-dev, keep-dev-40446). Pinning the production id here keeps an analysis from
# silently reading the wrong one. Matches scripts/ga4_custom_dimension_registrar.py.
GA4_PROPERTY_ID = "502544175"
GA4_DATA_BASE = "https://analyticsdata.googleapis.com/v1beta"

AMPLITUDE_HOSTS = {"us": "https://amplitude.com", "eu": "https://analytics.eu.amplitude.com"}

# Amplitude shipped in v1.7.9 (tag 2026-07-07, commit 69ecef87e). Users on anything older
# never sent a single Amplitude event, so they belong in the GA4 denominator and not in
# the Amplitude one. Comparisons that ignore this read the version mix as a data loss.
AMPLITUDE_MIN_VERSION = (1, 7, 9)
AMPLITUDE_MONTHLY_DEVICE_CAP = 180

# The allowlist from analytics/amplitude/AmplitudeEventAllowlist.kt. Kept in the same
# order as the Kotlin source so a diff between the two is easy to eyeball.
# app_first_open is the custom first-open marker: Firebase auto-emits the reserved
# first_open, which the app must not log, so both names are queried on the GA4 side.
ALLOWLISTED_EVENTS = [
    "app_first_open",
    "onboarding_step_complete",
    "permission_outcome",
    "app_selection_completed",
    "first_lock_configured",
    "first_core_action_completed",
    "keep_mode_toggled",
    "lock_session_start",
    "lock_session_end",
    "lock_scheduled",
    "emergency_unlock_used",
    "emergency_unlock_completed",
    "goal_lock_created",
    "goal_lock_completed",
    "goal_lock_ended_early",
    "routine_saved",
    "routine_creation_cta_clicked",
    "parent_mode_started",
    "parent_mode_completed",
    "lock_history_performance_summary_viewed",
    "focus_summary_share_tapped",
    "review_prompt_shown",
    "monetization_interest_clicked",
]

# Activation funnel, in the order defined by docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md.
# first_open is GA4's reserved auto event; app_first_open is our custom equivalent that
# Amplitude receives. Keeping both lets each tool anchor the funnel on a real event.
ACTIVATION_FUNNEL_GA4 = [
    "first_open",
    "onboarding_step_complete",
    "permission_outcome",
    "app_selection_completed",
    "first_lock_configured",
    "first_core_action_completed",
    "app_block_intercepted",
]
ACTIVATION_FUNNEL_AMPLITUDE = [
    "app_first_open",
    "onboarding_step_complete",
    "permission_outcome",
    "app_selection_completed",
    "first_lock_configured",
    "first_core_action_completed",
]


class MetricsError(RuntimeError):
    pass


# --- Credential resolution --------------------------------------------------------


def resolve_ga4_credentials() -> Path:
    # ~/.secrets is the established convention here; the Play service account key already
    # lives there. No operator-specific path belongs in a committed script.
    candidates = [
        os.environ.get("STOPIT_GA4_CREDENTIALS"),
        "~/.secrets/stopit-ga4-analytics.json",
    ]
    for candidate in candidates:
        if not candidate:
            continue
        path = Path(candidate).expanduser()
        if path.is_file():
            return path
    raise MetricsError(
        "GA4 service account key not found. Set STOPIT_GA4_CREDENTIALS to the "
        "analytics-bot@stopit-be785 JSON key path."
    )


def resolve_amplitude_credentials() -> tuple[str, str, str] | None:
    """Return (api_key, secret_key, region) or None when Amplitude is not configured."""
    api_key = os.environ.get("AMPLITUDE_ANALYTICS_API_KEY")
    secret_key = os.environ.get("AMPLITUDE_ANALYTICS_SECRET_KEY")
    region = os.environ.get("AMPLITUDE_REGION", "us").lower()

    if not (api_key and secret_key):
        path = Path(
            os.environ.get("STOPIT_AMPLITUDE_CREDENTIALS", "~/.secrets/stopit-amplitude.json")
        ).expanduser()
        if path.is_file():
            data = json.loads(path.read_text())
            api_key = api_key or data.get("api_key")
            secret_key = secret_key or data.get("secret_key")
            region = data.get("region", region).lower()

    if not (api_key and secret_key):
        return None
    if region not in AMPLITUDE_HOSTS:
        raise MetricsError(f"Unknown Amplitude region {region!r}; expected us or eu.")
    return api_key, secret_key, region


# --- GA4 lane ---------------------------------------------------------------------


class GA4Client:
    def __init__(self, credentials_path: Path) -> None:
        try:
            from google.auth.transport.requests import AuthorizedSession
            from google.oauth2 import service_account
        except ImportError as exc:  # pragma: no cover - environment guard
            raise MetricsError(
                "google-auth is required: pip3 install google-auth"
            ) from exc

        creds = service_account.Credentials.from_service_account_file(
            str(credentials_path),
            scopes=["https://www.googleapis.com/auth/analytics.readonly"],
        )
        self._session = AuthorizedSession(creds)
        self.timezone: str | None = None

    def run_report(
        self,
        metrics: list[str],
        dimensions: list[str] | None = None,
        *,
        start: str,
        end: str,
        limit: int = 500,
        event_names: list[str] | None = None,
        order_by: str | None = None,
    ) -> list[dict[str, Any]]:
        body: dict[str, Any] = {
            "dateRanges": [{"startDate": start, "endDate": end}],
            "metrics": [{"name": m} for m in metrics],
            "limit": limit,
        }
        if dimensions:
            body["dimensions"] = [{"name": d} for d in dimensions]
        if event_names:
            body["dimensionFilter"] = {
                "filter": {
                    "fieldName": "eventName",
                    "inListFilter": {"values": event_names},
                }
            }
        if order_by:
            body["orderBys"] = [{"metric": {"metricName": order_by}, "desc": True}]

        response = self._session.post(
            f"{GA4_DATA_BASE}/properties/{GA4_PROPERTY_ID}:runReport", json=body
        )
        if response.status_code != 200:
            raise MetricsError(f"GA4 runReport {response.status_code}: {response.text[:400]}")

        payload = response.json()
        self.timezone = payload.get("metadata", {}).get("timeZone", self.timezone)

        dim_headers = [h["name"] for h in payload.get("dimensionHeaders", [])]
        metric_headers = [h["name"] for h in payload.get("metricHeaders", [])]

        rows = []
        for row in payload.get("rows", []):
            record: dict[str, Any] = {}
            for name, value in zip(dim_headers, row.get("dimensionValues", [])):
                record[name] = value.get("value", "")
            for name, value in zip(metric_headers, row.get("metricValues", [])):
                raw = value.get("value", "0")
                record[name] = float(raw) if "." in raw else int(raw)
            rows.append(record)
        return rows


# --- Amplitude lane ---------------------------------------------------------------


class AmplitudeClient:
    """Amplitude Dashboard REST API (Basic auth with API key + secret key)."""

    def __init__(self, api_key: str, secret_key: str, region: str) -> None:
        self._host = AMPLITUDE_HOSTS[region]
        token = base64.b64encode(f"{api_key}:{secret_key}".encode()).decode()
        self._auth_header = f"Basic {token}"

    def get(self, path: str, params: list[tuple[str, str]]) -> dict[str, Any]:
        url = f"{self._host}{path}?{urllib.parse.urlencode(params)}"
        request = urllib.request.Request(url, headers={"Authorization": self._auth_header})
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                return json.loads(response.read().decode())
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode(errors="replace")[:400]
            if exc.code in (401, 403):
                raise MetricsError(
                    f"Amplitude auth failed ({exc.code}). Check the API key / secret key "
                    f"pair and the region. Detail: {detail}"
                ) from exc
            raise MetricsError(f"Amplitude {path} {exc.code}: {detail}") from exc
        except urllib.error.URLError as exc:  # pragma: no cover - network guard
            raise MetricsError(f"Amplitude request failed: {exc}") from exc

    def event_segmentation(self, event: str, start: str, end: str, measure: str) -> int:
        """Total uniques ('uniques') or event count ('totals') over the window."""
        payload = self.get(
            "/api/2/events/segmentation",
            [
                ("e", json.dumps({"event_type": event})),
                ("start", start),
                ("end", end),
                ("m", measure),
                ("i", "30"),  # single bucket over the window; we only need the sum
            ],
        )
        series = payload.get("data", {}).get("series", [[]])
        return int(sum(series[0])) if series and series[0] else 0

    def active_users(self, start: str, end: str, measure: str) -> list[int]:
        payload = self.get(
            "/api/2/users", [("start", start), ("end", end), ("m", measure), ("i", "1")]
        )
        series = payload.get("data", {}).get("series", [[]])
        return [int(v) for v in (series[0] if series else [])]

    def funnel(self, events: list[str], start: str, end: str) -> list[dict[str, Any]]:
        params: list[tuple[str, str]] = [
            ("e", json.dumps({"event_type": event})) for event in events
        ]
        params += [("start", start), ("end", end), ("mode", "unordered")]
        payload = self.get("/api/2/funnels", params)
        data = payload.get("data", [])
        if not data:
            return []
        step_counts = data[0].get("cumulativeRaw") or data[0].get("stepTransRaw") or []
        return [
            {"step": event, "users": int(count)}
            for event, count in zip(events, step_counts)
        ]

    def retention(self, start_event: str, return_event: str, start: str, end: str) -> dict[str, Any]:
        payload = self.get(
            "/api/2/retention",
            [
                ("se", json.dumps({"event_type": start_event})),
                ("re", json.dumps({"event_type": return_event})),
                ("start", start),
                ("end", end),
                ("rm", "n-day"),
                ("i", "1"),
            ],
        )
        return payload.get("data", {})


class AmplitudeSnapshot:
    """Amplitude counts captured elsewhere -- normally via the Amplitude MCP server.

    Exposes the same read surface as [AmplitudeClient] so the comparison math does not
    care which route the numbers arrived by. Anything the snapshot does not carry is
    reported as absent rather than as zero: a missing funnel is not an empty funnel, and
    conflating the two is how a reporting gap gets read as a product collapse.
    """

    def __init__(self, data: dict[str, Any]) -> None:
        # Accept either the full shape or the bare event map, since a hand-pasted MCP
        # result is usually just the events.
        self._events: dict[str, Any] = data.get("events", data if "events" not in data else {})
        self._funnel = data.get("funnel")
        self._active = data.get("active_users")

    def event_segmentation(self, event: str, start: str, end: str, measure: str) -> int | None:
        entry = self._events.get(event)
        if entry is None:
            # Not queried is not zero. A snapshot usually covers a handful of events, and
            # rendering the rest as 0 would read as total ingestion loss.
            return None
        if isinstance(entry, (int, float)):
            # A single number is the event total; uniques were not supplied.
            return int(entry) if measure == "totals" else 0
        return int(entry.get(measure, 0))

    def active_users(self, start: str, end: str, measure: str) -> list[int]:
        if not self._active:
            return []
        values = self._active.get(measure, [])
        return [int(v) for v in values] if isinstance(values, list) else [int(values)]

    def funnel(self, events: list[str], start: str, end: str) -> list[dict[str, Any]]:
        return self._funnel or []

    def retention(self, start_event: str, return_event: str, start: str, end: str) -> dict:
        return {}


# --- Version helpers --------------------------------------------------------------


def parse_version(raw: str) -> tuple[int, ...] | None:
    """Parse an appVersion string into a comparable tuple, or None if unparseable."""
    cleaned = raw.strip().split("-")[0].split(" ")[0]
    if not cleaned:
        return None
    parts = cleaned.split(".")
    try:
        return tuple(int(part) for part in parts)
    except ValueError:
        return None


def is_amplitude_eligible(raw_version: str) -> bool:
    """True when this app version actually contains the Amplitude SDK."""
    parsed = parse_version(raw_version)
    if parsed is None:
        return False
    padded = parsed + (0,) * (3 - len(parsed)) if len(parsed) < 3 else parsed
    return padded >= AMPLITUDE_MIN_VERSION


# --- Report sections --------------------------------------------------------------


def build_overview(ga4: GA4Client, amp: AmplitudeClient | None, window: dict[str, str]) -> dict:
    current = ga4.run_report(
        [
            "activeUsers",
            "newUsers",
            "sessions",
            "engagedSessions",
            "eventCount",
            "screenPageViews",
            "engagementRate",
        ],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=1,
    )
    previous = ga4.run_report(
        ["activeUsers", "newUsers", "sessions", "eventCount"],
        start=window["ga4_prev_start"],
        end=window["ga4_prev_end"],
        limit=1,
    )

    section: dict[str, Any] = {
        "ga4_current": current[0] if current else {},
        "ga4_previous": previous[0] if previous else {},
    }

    if amp:
        # Amplitude's own active/new user counts, for reference only: they cover a
        # different population (prod + v1.7.9+) and cannot be diffed against GA4 directly.
        daily_active = amp.active_users(window["amp_start"], window["amp_end"], "active")
        daily_new = amp.active_users(window["amp_start"], window["amp_end"], "new")
        # Absent is not zero: a snapshot that never carried user counts must not render
        # as "0 active users".
        if daily_active or daily_new:
            section["amplitude"] = {
                "peak_daily_active": max(daily_active) if daily_active else 0,
                "mean_daily_active": round(sum(daily_active) / len(daily_active), 1)
                if daily_active
                else 0,
                "new_users_sum": sum(daily_new),
            }
    return section


def build_event_comparison(
    ga4: GA4Client, amp: AmplitudeClient | None, window: dict[str, str]
) -> dict:
    """The core dual-read: allowlisted events, GA4 restricted to Amplitude-eligible versions."""
    by_version = ga4.run_report(
        ["eventCount", "totalUsers"],
        ["eventName", "appVersion"],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=5000,
        event_names=ALLOWLISTED_EVENTS + ["first_open"],
    )
    totals = ga4.run_report(
        ["eventCount", "totalUsers"],
        ["eventName"],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=200,
        event_names=ALLOWLISTED_EVENTS + ["first_open"],
    )

    ga4_total = {row["eventName"]: row for row in totals}
    eligible_counts: dict[str, int] = {}
    ineligible_counts: dict[str, int] = {}
    for row in by_version:
        bucket = eligible_counts if is_amplitude_eligible(row["appVersion"]) else ineligible_counts
        bucket[row["eventName"]] = bucket.get(row["eventName"], 0) + row["eventCount"]

    rows = []
    for event in ALLOWLISTED_EVENTS:
        # GA4 has no app_first_open equivalent for pre-instrumented users; first_open is
        # the reserved auto event that fills the same slot on the GA4 side.
        ga4_row = ga4_total.get(event, {})
        record = {
            "event": event,
            "ga4_event_count": ga4_row.get("eventCount", 0),
            "ga4_users": ga4_row.get("totalUsers", 0),
            "ga4_event_count_eligible": eligible_counts.get(event, 0),
            "ga4_event_count_ineligible": ineligible_counts.get(event, 0),
        }
        if amp:
            totals = amp.event_segmentation(
                event, window["amp_start"], window["amp_end"], "totals"
            )
            record["amplitude_totals"] = totals
            record["amplitude_uniques"] = amp.event_segmentation(
                event, window["amp_start"], window["amp_end"], "uniques"
            )
            eligible = record["ga4_event_count_eligible"]
            # None totals means "not measured", which yields no capture rate at all --
            # distinct from a measured 0.
            record["capture_rate"] = (
                round(totals / eligible, 3) if totals is not None and eligible else None
            )
        rows.append(record)

    return {"rows": rows, "first_open_ga4": ga4_total.get("first_open", {})}


def build_activation_funnel(
    ga4: GA4Client, amp: AmplitudeClient | None, window: dict[str, str]
) -> dict:
    rows = ga4.run_report(
        ["totalUsers", "eventCount"],
        ["eventName"],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=100,
        event_names=ACTIVATION_FUNNEL_GA4,
    )
    ga4_users = {row["eventName"]: row["totalUsers"] for row in rows}
    section: dict[str, Any] = {
        "ga4": [{"step": step, "users": ga4_users.get(step, 0)} for step in ACTIVATION_FUNNEL_GA4]
    }
    if amp:
        section["amplitude"] = amp.funnel(
            ACTIVATION_FUNNEL_AMPLITUDE, window["amp_start"], window["amp_end"]
        )
    return section


def build_ga4_only(ga4: GA4Client, window: dict[str, str]) -> dict:
    """Axes Amplitude structurally cannot answer: screens, versions, acquisition, ads.

    Screen views and sessions are absent from Amplitude by construction (autocapture is
    disabled so the SDK cannot emit ungated events), and ad/acquisition data never
    reaches it at all.
    """
    screens = ga4.run_report(
        ["screenPageViews"],
        ["unifiedScreenName"],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=200,
        order_by="screenPageViews",
    )
    total_views = sum(row["screenPageViews"] for row in screens)
    unset_views = sum(
        row["screenPageViews"]
        for row in screens
        if row["unifiedScreenName"] in ("(not set)", "")
    )

    versions = ga4.run_report(
        ["activeUsers"],
        ["appVersion"],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=50,
        order_by="activeUsers",
    )
    total_version_users = sum(row["activeUsers"] for row in versions)
    eligible_users = sum(
        row["activeUsers"] for row in versions if is_amplitude_eligible(row["appVersion"])
    )

    acquisition = ga4.run_report(
        ["newUsers", "activeUsers"],
        ["firstUserDefaultChannelGroup"],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=20,
        order_by="newUsers",
    )
    revenue = ga4.run_report(
        [
            "totalAdRevenue",
            "publisherAdImpressions",
            "publisherAdClicks",
            "averageRevenuePerUser",
        ],
        start=window["ga4_start"],
        end=window["ga4_end"],
        limit=1,
    )

    return {
        "screen_quality": {
            "total_views": total_views,
            "unset_views": unset_views,
            "unset_share": round(unset_views / total_views, 3) if total_views else None,
        },
        "versions": versions[:10],
        # The share of GA4 active users on a build that can send to Amplitude. This is the
        # ceiling on any Amplitude coverage figure -- a low share explains a gap that would
        # otherwise read as data loss.
        "amplitude_eligible_user_share": round(eligible_users / total_version_users, 3)
        if total_version_users
        else None,
        "acquisition": acquisition,
        "revenue": revenue[0] if revenue else {},
    }


# --- Rendering --------------------------------------------------------------------


def pct(value: float | None) -> str:
    return "-" if value is None else f"{value * 100:.1f}%"


def render(report: dict[str, Any]) -> str:
    out: list[str] = []
    meta = report["meta"]
    amp_on = meta["amplitude_enabled"]

    out.append("=" * 78)
    out.append(f"Stopit 지표 판독  |  {meta['ga4_start']} ~ {meta['ga4_end']} ({meta['days']}일)")
    out.append(f"GA4 property {GA4_PROPERTY_ID} (TZ {meta.get('ga4_timezone', '?')})")
    out.append(
        "Amplitude: "
        + (f"연결됨 [{meta.get('amplitude_source')}]" if amp_on else "미연결 — GA4 단독 판독")
    )
    out.append("=" * 78)

    overview = report["overview"]
    cur, prev = overview["ga4_current"], overview["ga4_previous"]
    out.append("\n## 1. 전체 개요 (GA4)")
    out.append(f"{'지표':<22}{'이번 기간':>14}{'직전 기간':>14}{'변화':>12}")
    for label, key in [
        ("activeUsers", "activeUsers"),
        ("newUsers", "newUsers"),
        ("sessions", "sessions"),
        ("eventCount", "eventCount"),
    ]:
        now, before = cur.get(key, 0), prev.get(key, 0)
        delta = f"{(now - before) / before * 100:+.1f}%" if before else "-"
        out.append(f"{label:<22}{now:>14,}{before:>14,}{delta:>12}")
    out.append(f"{'engagementRate':<22}{pct(cur.get('engagementRate')):>14}")

    if amp_on and "amplitude" in overview:
        amp = overview["amplitude"]
        out.append(
            f"\nAmplitude 참고: 일 최대 active {amp['peak_daily_active']:,} / "
            f"평균 {amp['mean_daily_active']:,} / 신규 합 {amp['new_users_sum']:,}"
        )
        out.append("  ※ GA4와 모집단이 다름(prod + v1.7.9+). 차이를 손실로 읽지 말 것.")

    ga4_only = report["ga4_only"]
    share = ga4_only["amplitude_eligible_user_share"]
    out.append("\n## 2. Amplitude 판독 가능 범위 (GA4로 잰 상한)")
    out.append(f"v1.7.9+ active user share : {pct(share)}")
    out.append("  이 비율이 Amplitude coverage의 천장이다. 낮으면 gap은 손실이 아니라 버전 믹스다.")
    out.append(f"{'appVersion':<16}{'activeUsers':>14}{'Amplitude':>12}")
    for row in ga4_only["versions"]:
        mark = "O" if is_amplitude_eligible(row["appVersion"]) else "-"
        out.append(f"{row['appVersion']:<16}{row['activeUsers']:>14,}{mark:>12}")

    out.append("\n## 3. 활성화 퍼널")
    funnel = report["activation_funnel"]
    base = funnel["ga4"][0]["users"] if funnel["ga4"] else 0
    out.append(f"{'step':<38}{'GA4 users':>12}{'/ first_open':>14}")
    for step in funnel["ga4"]:
        rate = f"{step['users'] / base * 100:.1f}%" if base else "-"
        out.append(f"{step['step']:<38}{step['users']:>12,}{rate:>14}")
    if amp_on and funnel.get("amplitude"):
        out.append(f"\n{'step (Amplitude)':<38}{'users':>12}")
        for step in funnel["amplitude"]:
            out.append(f"{step['step']:<38}{step['users']:>12,}")

    out.append("\n## 4. Allowlist 이벤트 교차 판독")
    if amp_on:
        out.append("GA4 eligible = v1.7.9+ 버전만 합산한 eventCount (Amplitude와 같은 모집단)")
        out.append("capture = Amplitude totals / GA4 eligible. 1.0 미만은 대부분 기기별 월 180건 캡.")
        header = f"{'event':<42}{'GA4 all':>10}{'GA4 elig':>10}{'AMP':>9}{'capture':>9}"
    else:
        header = f"{'event':<42}{'GA4 all':>10}{'GA4 elig':>10}{'users':>9}"
    out.append(header)
    for row in report["event_comparison"]["rows"]:
        line = (
            f"{row['event']:<42}{row['ga4_event_count']:>10,}"
            f"{row['ga4_event_count_eligible']:>10,}"
        )
        if amp_on:
            capture = row.get("capture_rate")
            totals = row.get("amplitude_totals")
            # "n/a" = never measured; "0" = measured and genuinely zero.
            line += f"{('n/a' if totals is None else f'{totals:,}'):>9}"
            line += f"{('-' if capture is None else f'{capture:.2f}'):>9}"
        else:
            line += f"{row['ga4_users']:>9,}"
        out.append(line)

    out.append("\n## 5. GA4 전용 축 (Amplitude 구조상 불가)")
    quality = ga4_only["screen_quality"]
    out.append(
        f"screen_view (not set)/blank : {quality['unset_views']:,} / "
        f"{quality['total_views']:,} = {pct(quality['unset_share'])}"
    )
    out.append("획득 채널:")
    for row in ga4_only["acquisition"][:6]:
        out.append(
            f"  {row['firstUserDefaultChannelGroup']:<26}"
            f"newUsers {row['newUsers']:>7,}   activeUsers {row['activeUsers']:>7,}"
        )
    rev = ga4_only["revenue"]
    impressions = rev.get("publisherAdImpressions", 0)
    ecpm = rev.get("totalAdRevenue", 0) / impressions * 1000 if impressions else 0
    out.append(
        f"광고: revenue ${rev.get('totalAdRevenue', 0):.4f} / "
        f"impressions {impressions:,.0f} / clicks {rev.get('publisherAdClicks', 0):,.0f} / "
        f"eCPM ${ecpm:.3f}"
    )

    out.append("\n## 판독 주의")
    out.append("- Amplitude 수치는 하한이다. 캡은 헤비 유저부터 자르므로 volume 근거로 쓰지 않는다.")
    out.append("- 두 도구 수치를 더하지 않는다. 공유 user id가 없어 사용자 단위 조인도 불가능하다.")
    out.append("- Amplitude에는 세션/화면 지표가 없다(autocapture OFF). 해당 축은 GA4만 본다.")
    out.append("- 미배포 기능의 0건은 수요 없음이 아니라 미관측이다.")
    return "\n".join(out)


# --- Entry point ------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(description="Read Stopit metrics from GA4 + Amplitude.")
    parser.add_argument("--days", type=int, default=30, help="window length (default 30)")
    parser.add_argument("--json-out", type=Path, help="also write the raw report as JSON")
    parser.add_argument(
        "--amplitude-json",
        type=Path,
        help="Amplitude counts pulled via the MCP server (see module docstring for shape)",
    )
    parser.add_argument(
        "--skip-amplitude", action="store_true", help="force GA4-only even if keys exist"
    )
    args = parser.parse_args()

    end = date.today() - timedelta(days=1)
    start = end - timedelta(days=args.days - 1)
    prev_end = start - timedelta(days=1)
    prev_start = prev_end - timedelta(days=args.days - 1)
    window = {
        "ga4_start": start.isoformat(),
        "ga4_end": end.isoformat(),
        "ga4_prev_start": prev_start.isoformat(),
        "ga4_prev_end": prev_end.isoformat(),
        # Amplitude's Dashboard API takes YYYYMMDD, not ISO dates.
        "amp_start": start.strftime("%Y%m%d"),
        "amp_end": end.strftime("%Y%m%d"),
    }

    try:
        ga4 = GA4Client(resolve_ga4_credentials())
        amp: AmplitudeClient | AmplitudeSnapshot | None = None
        amp_source = None
        if not args.skip_amplitude:
            if args.amplitude_json:
                # An explicit snapshot wins: it is the MCP path, and it is what the
                # operator just looked at.
                amp = AmplitudeSnapshot(json.loads(args.amplitude_json.read_text()))
                amp_source = f"mcp-snapshot ({args.amplitude_json})"
            else:
                credentials = resolve_amplitude_credentials()
                if credentials:
                    amp = AmplitudeClient(*credentials)
                    amp_source = "dashboard-rest-api"

        report: dict[str, Any] = {"meta": dict(window, days=args.days)}
        report["overview"] = build_overview(ga4, amp, window)
        report["activation_funnel"] = build_activation_funnel(ga4, amp, window)
        report["event_comparison"] = build_event_comparison(ga4, amp, window)
        report["ga4_only"] = build_ga4_only(ga4, window)
        report["meta"]["ga4_timezone"] = ga4.timezone
        report["meta"]["amplitude_enabled"] = amp is not None
        report["meta"]["amplitude_source"] = amp_source
        report["meta"]["amplitude_monthly_device_cap"] = AMPLITUDE_MONTHLY_DEVICE_CAP
    except MetricsError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(render(report))

    if args.json_out:
        args.json_out.write_text(json.dumps(report, indent=2, ensure_ascii=False))
        print(f"\nJSON 저장: {args.json_out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
