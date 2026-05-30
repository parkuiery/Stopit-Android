<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# documentation

## Purpose
Project documentation for workflow, plans, and historical design/spec artifacts.

## Key Files
| File | Description |
|------|-------------|
| `GIT_WORKFLOW.md` | Git branching, commit, and release workflow documentation. |
| `FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md` | #14용 첫 잠금 활성화 퍼널 canonical 계약, CTA, guardrail, 측정 템플릿. |
| `METRICS_ANALYSIS.md` | 스탑잇 제품 지표 분석, GA4 조회, 개선 이슈화 절차 문서. |
| `PRODUCT_METRICS_DASHBOARD.md` | 스탑잇 North Star, 입력/건강/비즈니스 지표, ICE 우선순위, 성장/수익화 실험 정의. |
| `ADMOB_MONETIZATION_RUNBOOK.md` | #16용 광고 단위 감사, `(not set)` 점검, guardrail, 안전한 수익화 실험 운영 런북. |
| `PLAY_DEPLOY_SECRETS_RUNBOOK.md` | Play 배포 secret ownership, helper 범위, workflow restore matrix, Firebase Functions 경계 런북. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `ops/stopit/` | Stopit 운영 cron과 전문 subagent가 공유하는 제품/지표/엔지니어링/릴리즈 컨텍스트 팩. |
| `superpowers/` | Superpowers-generated implementation planning and specification artifacts. |

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew test for repository-wide JVM tests when behavior changes.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
