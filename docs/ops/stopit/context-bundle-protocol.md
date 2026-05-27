# Stopit Context Bundle Protocol

이 문서는 cron 오케스트레이터가 전문 subagent에게 넘길 Context Bundle을 만드는 표준 절차다.

## 원칙

- Context Bundle은 “고정 운영 맥락 + 이번 run의 동적 상태”로 구성한다.
- 모든 subagent에게 전체 파일을 그대로 던지지 않는다. 역할에 필요한 요약만 전달한다.
- subagent는 Context Bundle 밖의 과거 맥락을 기억한다고 가정하지 않는다.
- 현재 상태의 source of truth는 GitHub Issues/PR/Actions, metrics snapshot, repo 파일이다.

## 공통 고정 맥락

모든 cron은 최소한 아래를 확인한다.

- `docs/ops/stopit/README.md`
- `docs/ops/stopit/issue-policy.md`
- `docs/ops/stopit/agent-roles.md`
- `docs/ops/stopit/recent-decisions.md`

## 공통 동적 상태

가능하면 다음을 포함한다.

```bash
git status --short --branch
git branch --show-current
gh issue list --state open --limit 100 --json number,title,labels,url,body,updatedAt
gh pr list --state open --limit 20 --json number,title,headRefName,baseRefName,url,isDraft
```

필요 시:

```bash
gh run list --limit 10 --json databaseId,status,conclusion,workflowName,createdAt,url
```

## 지표 cron Context Bundle

고정 파일:
- `product-context.md`
- `metrics-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- metrics snapshot script output
- open Issues
- recent PRs if relevant

Subagent별 전달:
- Metrics Analyst: product + metrics + open product/analytics issues + metrics snapshot
- Product/Growth Analyst: product + metrics highlights + open growth issues + deferred idea policy
- Monetization/Review Analyst: product + metrics revenue/review/ASO section + open monetization/review issues

## 아이디어 cron Context Bundle

고정 파일:
- `product-context.md`
- `metrics-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- open product/growth/monetization Issues
- recent metrics output if available
- web references if used

Subagent별 전달:
- Product/Growth Analyst: product goals, NSM, growth constraints, open ideas/issues
- Monetization/Review/ASO Analyst: metrics business/acquisition/review context, Play/ASO references
- Trust/Safety UX Analyst: product trust principles, engineering risk zones, issue policy

## 저장소 건강도 cron Context Bundle

고정 파일:
- `engineering-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- git status/branch
- open Issues
- recent Actions runs
- targeted static search results

Subagent별 전달:
- Bug Scout / QA Analyst: engineering risk zones, open bug/qa issues, CI/search evidence
- Tech Debt / Architecture Analyst: architecture/module context, open refactor/tech-debt issues
- Build/Release Maintenance Analyst: engineering + release context, CI workflow/run evidence

## 실행 cron Context Bundle

고정 파일:
- `engineering-context.md`
- `release-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

조건부 추가:
- 선택 이슈가 docs/ops/analytics/product-metrics 성격(#13, #14, #16, #65 같은 runbook/metrics/dashboard/funnel/ASO 문서 작업)이면 `product-context.md`, `metrics-context.md`도 함께 포함한다.
- 특히 `stopit-executor-docs-lane`는 analytics/product 문서 이슈를 engineering/release-only context로 처리하지 않는다.

동적 상태:
- git status/branch
- open Issues
- open PRs
- CI/check state
- latest outputs from metrics/health/ideation crons when available

Subagent별 전달:
- Issue Picker: open Issues/PRs, priority policy, dirty tree state, latest cron outputs
- Implementation Strategy: selected Issue body, relevant engineering/release context
- Test/PR Verifier: diff summary, verification outputs, PR body/check state

## 최종 통합 절차

1. subagent outputs를 합친다.
2. 기존 열린 이슈와 중복 제거한다.
3. 근거가 부족한 항목은 defer한다.
4. GitHub Issue/Discord/PR 등 side effect는 메인 오케스트레이터가 제한 규칙에 맞게 수행한다.
5. 최종 보고에는 확인한 소스, 전문 에이전트별 핵심 진단, 생성/보류/중복 항목, 다음 우선순위를 포함한다.
