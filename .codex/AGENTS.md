# ECC for Codex CLI

This supplements the root `AGENTS.md` with a repo-local ECC baseline for AI-assisted work in Stopit Android.

## Repo Skill

- Codex/general agent skill: `.agents/skills/Stopit-Android/SKILL.md`
- Legacy ECC-managed mirror: `.claude/skills/Stopit-Android/SKILL.md`
- Keep user-specific credentials and private MCPs in the user's local config, not in this repository.

## MCP Baseline

Treat `.codex/config.toml` as the default ECC-safe baseline for work in this repository.
The generated baseline enables GitHub, Context7, Exa, Memory, Playwright, and Sequential Thinking.

## Multi-Agent Support

- Explorer: read-only evidence gathering
- Reviewer: correctness, security, and regression review
- Docs researcher: API and release-note verification

## Stopit-Specific Guardrails

- Do not generate Cursor-only `.cursorrules` unless explicitly requested.
- Use flavor-qualified Gradle commands such as `:app:testDevDebugUnitTest` and `:app:assembleProdDebug`.
- Check the nearest nested `AGENTS.md` before editing files.
- For Play Deploy, release-secret, Firebase, Crashlytics, or GA4 changes, verify the matching runbook/docs contract before editing.
- Keep generated output, local secrets, and flavor `google-services.json` changes out of commits unless explicitly requested.

## Workflow Files

- No dedicated workflow command files are currently generated for this repo.
- Use the repo skill and root/nested `AGENTS.md` files as reusable task scaffolds when recurring workflows appear.
