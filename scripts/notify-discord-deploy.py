#!/usr/bin/env python3
"""Post Stopit Play deployment status to the Discord deploy channel.

This script is intentionally dependency-free so GitHub Actions can run it on
ubuntu-latest without installing Python packages.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request

DISCORD_API_BASE = "https://discord.com/api/v10"
GITHUB_API_BASE = "https://api.github.com"
PROMOTE_CUSTOM_ID_PREFIX = "stopit_promote_production"
DISCORD_CONTENT_LIMIT = 2000
CHANGE_SUMMARY_BUDGET = 950


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def run_url() -> str:
    server = env("GITHUB_SERVER_URL", "https://github.com")
    repo = env("GITHUB_REPOSITORY")
    run_id = env("GITHUB_RUN_ID")
    if repo and run_id:
        return f"{server}/{repo}/actions/runs/{run_id}"
    return ""


def git_output(*args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], text=True, stderr=subprocess.DEVNULL).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
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


def github_api_json(path: str) -> dict:
    token = env("GH_TOKEN") or env("GITHUB_TOKEN")
    repo = env("GITHUB_REPOSITORY")
    if not token or not repo:
        return {}

    request = urllib.request.Request(
        f"{GITHUB_API_BASE}/repos/{repo}{path}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "stopit-play-deploy/1.0",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError) as error:
        print(f"Unable to read GitHub metadata from {path}: {error}", file=sys.stderr)
        if shutil.which("gh"):
            try:
                output = subprocess.check_output(
                    ["gh", "api", f"repos/{repo}{path}"],
                    text=True,
                    stderr=subprocess.DEVNULL,
                )
                return json.loads(output)
            except (subprocess.CalledProcessError, json.JSONDecodeError) as gh_error:
                print(f"Unable to read GitHub metadata with gh api from {path}: {gh_error}", file=sys.stderr)
        return {}


def markdown_section(markdown: str, title: str) -> str:
    lines = markdown.splitlines()
    start = -1
    level = 0
    heading_pattern = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
    wanted = title.casefold()

    for index, line in enumerate(lines):
        match = heading_pattern.match(line)
        if match and match.group(2).strip().casefold() == wanted:
            start = index + 1
            level = len(match.group(1))
            break

    if start == -1:
        return ""

    end = len(lines)
    for index in range(start, len(lines)):
        match = heading_pattern.match(lines[index])
        if match and len(match.group(1)) <= level:
            end = index
            break

    return "\n".join(lines[start:end]).strip()


def clean_summary_text(text: str) -> str:
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL).strip()
    cleaned_lines: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.rstrip()
        if not line:
            if cleaned_lines and cleaned_lines[-1]:
                cleaned_lines.append("")
            continue
        cleaned_lines.append(line)
    return "\n".join(cleaned_lines).strip()


def truncate_text(text: str, limit: int) -> str:
    if len(text) <= limit:
        return text
    truncated = text[: max(0, limit - 20)].rstrip()
    if "\n" in truncated:
        truncated = truncated.rsplit("\n", 1)[0].rstrip()
    return f"{truncated}\n- …이하 생략"


def release_pr_number_from_commit() -> str:
    sha = env("GITHUB_SHA") or "HEAD"
    subject = git_output("show", "-s", "--format=%s", sha)
    subject_matches = re.findall(r"\(#(\d+)\)", subject)
    if subject_matches:
        return subject_matches[-1]

    message = git_output("show", "-s", "--format=%B", sha)
    matches = re.findall(r"\(#(\d+)\)", message)
    return matches[0] if matches else ""


def release_pr_change_summary() -> str:
    pr_number = release_pr_number_from_commit()
    if not pr_number:
        return ""

    pr = github_api_json(f"/pulls/{pr_number}")
    body = str(pr.get("body") or "")
    section = markdown_section(body, "Change summary")
    if not section:
        return ""

    summary = clean_summary_text(section)
    pr_url = pr.get("html_url") or ""
    if pr_url:
        summary = f"{summary}\n\nPR: {pr_url}"
    return summary


def previous_semver_tag(current_tag: str) -> str:
    if not re.fullmatch(r"v\d+\.\d+\.\d+", current_tag):
        return ""
    tags = git_output("tag", "--list", "v*.*.*", "--sort=-v:refname").splitlines()
    tags = [tag.strip() for tag in tags if re.fullmatch(r"v\d+\.\d+\.\d+", tag.strip())]
    for index, tag in enumerate(tags):
        if tag == current_tag and index + 1 < len(tags):
            return tags[index + 1]
    return ""


def fallback_commit_summary(tag: str) -> str:
    previous = previous_semver_tag(tag)
    if previous:
        rev_range = f"{previous}..{tag}"
    elif re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        rev_range = tag
    else:
        rev_range = env("GITHUB_SHA") or "HEAD"

    lines = git_output("log", "--first-parent", "--pretty=format:- %s", rev_range).splitlines()
    lines = [line for line in lines if line and not line.startswith("- release:")]
    if not lines:
        return ""

    heading = f"커밋 기준 변경사항 ({previous}..{tag})" if previous else "커밋 기준 변경사항"
    return "\n".join([heading, *lines[:10]])


def release_change_summary(tag: str) -> str:
    summary = release_pr_change_summary() or fallback_commit_summary(tag)
    return truncate_text(summary, CHANGE_SUMMARY_BUDGET) if summary else "변경사항 요약을 찾지 못했습니다. GitHub Actions 링크에서 release PR 내용을 확인해 주세요."


def fit_discord_content(lines: list[str]) -> str:
    content = "\n".join(lines)
    if len(content) <= DISCORD_CONTENT_LIMIT:
        return content
    return truncate_text(content, DISCORD_CONTENT_LIMIT)


def internal_payload(tag: str, url: str) -> dict:
    change_summary = release_change_summary(tag)
    lines = [
        "✅ **Stopit internal 배포 완료**",
        f"- 버전: `{tag}`",
        "- Play track: `internal`",
        f"- GitHub Actions: {url}" if url else "- GitHub Actions: unknown",
        "",
        "## 릴리즈 변경사항",
        change_summary,
        "",
        "내부 QA가 끝났고 문제가 없으면 아래 버튼으로 같은 태그를 `production` track에 배포하세요.",
    ]

    payload = {
        "content": fit_discord_content(lines),
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
    return {"content": fit_discord_content(lines), "allowed_mentions": {"parse": []}}


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
