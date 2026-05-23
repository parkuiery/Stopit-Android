# Stopit Agent Context Pack

이 디렉터리는 스탑잇(Stopit / Keep Android)을 운영하는 Hermes cron과 전문 subagent가 공유하는 장기 맥락이다.

목표는 한 파일에 모든 내용을 넣는 것이 아니라, 각 에이전트가 필요한 맥락만 빠르게 읽도록 역할별 컨텍스트를 분리하는 것이다.

## 읽기 원칙

모든 cron 오케스트레이터는 subagent를 부르기 전에 다음을 수행한다.

1. 이 `README.md`를 읽고 필요한 컨텍스트 파일을 고른다.
2. GitHub Issues/PR/CI/metrics snapshot처럼 매번 바뀌는 현재 상태를 별도로 조회한다.
3. 고정 컨텍스트와 현재 상태를 합쳐 `Context Bundle`을 만든다.
4. 각 subagent에게 해당 역할에 필요한 요약만 전달한다.
5. subagent는 분석만 수행하고, GitHub Issue 생성/Discord 발송/파일 수정/PR 생성은 메인 오케스트레이터가 최종 검토 후 수행한다.

## 파일 안내

| 파일 | 용도 | 주로 읽는 cron/agent |
| --- | --- | --- |
| `product-context.md` | 제품 목표, 타깃 사용자, 핵심 가치, 제품 판단 원칙 | 지표, 아이디어, Product/Growth, Trust/Safety |
| `metrics-context.md` | GA4/Play/수익 지표 정의, 퍼널, 해석 주의사항 | 지표, Metrics, Monetization/Review |
| `engineering-context.md` | Android 구조, flavor, 테스트/Gradle, 코드 변경 주의사항 | 건강도, 실행, Bug Scout, Tech Debt, Executor |
| `automation-ops.md` | 현재 Stopit cron topology, live source of truth, issue #18 acceptance mapping | 실행 lane, merge/release controller, 운영 점검 |
| `issue-policy.md` | GitHub Issue 라벨, 제목/본문 형식, 생성 제한, 중복 기준 | 모든 cron/agent |
| `release-context.md` | 브랜치/PR/CI/Play 배포 guardrail | 실행, Build/Release Maintenance, PR/CI Verifier |
| `agent-roles.md` | 전문 subagent 역할, 입력, 출력, 금지사항 | 모든 cron 오케스트레이터 |
| `recent-decisions.md` | 오래 유지해야 하는 최근 운영 결정 | 모든 cron에서 필요 시 |

## Cron별 권장 읽기 세트

### `stopit-daily-metrics-monitor`

필수:
- `product-context.md`
- `metrics-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- metrics snapshot script output
- open GitHub Issues
- recent PRs when relevant

### `stopit-feature-ideation-discord`

필수:
- `product-context.md`
- `metrics-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- open product/growth/monetization Issues
- recent metrics output if available
- external references from web search when useful

### `stopit-repo-health-issue-discovery`

필수:
- `engineering-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- git status and branch
- open GitHub Issues
- recent GitHub Actions runs
- targeted file/static searches

### `stopit-executor-docs-lane` / `stopit-executor-qa-lane` / `stopit-executor-code-lane`

필수:
- `automation-ops.md`
- `engineering-context.md`
- `release-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- git status and branch
- open GitHub Issues
- open PRs
- CI/check status
- latest outputs from metrics/health/ideation crons when available

### `stopit-merge-controller`

필수:
- `automation-ops.md`
- `engineering-context.md`
- `release-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- open PRs
- `gh pr checks` 결과
- worktree/branch overlap 상태

### `stopit-release-orchestrator-internal`

필수:
- `automation-ops.md`
- `engineering-context.md`
- `release-context.md`
- `issue-policy.md`
- `agent-roles.md`
- `recent-decisions.md`

동적 상태:
- open release PRs
- release/build/deploy workflow 상태
- current `versionName` / `versionCode`
- active lane PR 존재 여부

## 무엇을 여기에 넣지 말아야 하는가

넣지 않는다:
- 일회성 작업 진행상황
- 특정 PR/Issue가 오늘 생성/병합됐다는 로그
- 오래 지나면 틀릴 지표 수치
- secret, key, private JSON 내용
- 긴 빌드 로그 원문

그런 정보는 GitHub Issue/PR, cron output, metrics snapshot, CI 로그가 source of truth다.

넣는다:
- 반복해서 필요한 운영 원칙
- 제품/지표/릴리즈 정의
- 에이전트 역할 계약
- 중복 방지 기준
- 장기적으로 유지되는 대표님 선호와 guardrail
