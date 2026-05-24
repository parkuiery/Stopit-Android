#!/usr/bin/env python3
"""Post Stopit Play deployment status to the Discord deploy channel.

This script is intentionally dependency-free so GitHub Actions can run it on
ubuntu-latest without installing Python packages.
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request

DISCORD_API_BASE = "https://discord.com/api/v10"
PROMOTE_CUSTOM_ID_PREFIX = "stopit_promote_production"


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def run_url() -> str:
    server = env("GITHUB_SERVER_URL", "https://github.com")
    repo = env("GITHUB_REPOSITORY")
    run_id = env("GITHUB_RUN_ID")
    if repo and run_id:
        return f"{server}/{repo}/actions/runs/{run_id}"
    return ""


def post_discord_message(token: str, channel_id: str, payload: dict) -> None:
    request = urllib.request.Request(
        f"{DISCORD_API_BASE}/channels/{channel_id}/messages",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bot {token}",
            "Content-Type": "application/json",
            "User-Agent": "stopit-play-deploy/1.0",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            if response.status < 200 or response.status >= 300:
                raise RuntimeError(f"Discord returned HTTP {response.status}")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")
        raise RuntimeError(f"Discord returned HTTP {error.code}: {body}") from error


def internal_payload(tag: str, url: str) -> dict:
    lines = [
        "✅ **Stopit internal 배포 완료**",
        f"- 버전: `{tag}`",
        "- Play track: `internal`",
        f"- GitHub Actions: {url}" if url else "- GitHub Actions: unknown",
        "",
        "내부 QA가 끝났고 문제가 없으면 아래 버튼으로 같은 태그를 `production` track에 배포하세요.",
    ]

    payload = {
        "content": "\n".join(lines),
        "allowed_mentions": {"parse": []},
    }

    if re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        payload["components"] = [
            {
                "type": 1,
                "components": [
                    {
                        "type": 2,
                        "style": 3,
                        "label": "프로덕션 배포",
                        "custom_id": f"{PROMOTE_CUSTOM_ID_PREFIX}:{tag}",
                    }
                ],
            }
        ]
    else:
        payload["content"] += "\n\n⚠️ SemVer 태그가 아니라 production 배포 버튼을 만들지 않았습니다."

    return payload


def production_payload(tag: str, url: str) -> dict:
    lines = [
        "🚀 **Stopit production 배포 workflow 시작/완료 알림**",
        f"- 버전/ref: `{tag}`",
        "- Play track: `production`",
        f"- GitHub Actions: {url}" if url else "- GitHub Actions: unknown",
        "",
        "Google Play Console에서 실제 노출/심사 상태를 최종 확인하세요.",
    ]
    return {"content": "\n".join(lines), "allowed_mentions": {"parse": []}}


def main() -> int:
    token = env("DISCORD_BOT_TOKEN")
    channel_id = env("DISCORD_DEPLOY_CHANNEL_ID")
    if not token or not channel_id:
        print("DISCORD_BOT_TOKEN or DISCORD_DEPLOY_CHANNEL_ID is missing; skipping Discord deploy notification")
        return 0

    track = env("DEPLOY_TRACK", "internal")
    tag = env("GITHUB_REF_NAME") or env("GITHUB_SHA", "unknown")
    url = run_url()

    if track == "internal":
        payload = internal_payload(tag, url)
    elif track == "production":
        payload = production_payload(tag, url)
    else:
        print(f"Track {track!r} does not require a deploy-channel notification")
        return 0

    post_discord_message(token, channel_id, payload)
    print(f"Posted {track} deploy notification for {tag} to Discord channel {channel_id}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
