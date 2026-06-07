<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# documentation

## Purpose
Project documentation for workflow, plans, and historical design/spec artifacts.

## Key Files
| File | Description |
|------|-------------|
| `GIT_WORKFLOW.md` | Git branching, commit, and release workflow documentation. |
| `ANALYTICS_EVENT_DICTIONARY.md` | #13용 이벤트/파라미터 계약, screen_view 규칙, GA4 조회 기준. |
| `GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` | #13용 GA4 Admin 등록 ledger, metadata 증적, 14일 재측정 운영 런북. |
| `FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md` | #14용 첫 잠금 활성화 퍼널 canonical 계약, CTA, guardrail, 측정 템플릿. |
| `METRICS_ANALYSIS.md` | 스탑잇 제품 지표 분석, GA4 조회, 개선 이슈화 절차 문서. |
| `PRODUCT_METRICS_DASHBOARD.md` | 스탑잇 North Star, 입력/건강/비즈니스 지표, ICE 우선순위, 성장/수익화 실험 정의. |
| `ROUTINE_RETENTION_COHORT_BASELINE.md` | #380용 루틴 보유/미보유 반복 사용 코호트 기준선, GA4 query template, 14일/30일 재측정 계약. |
| `ROUTINES_COUNT_COVERAGE_CONTRACT.md` | #479용 `routines_count` user property coverage 보강 계약, code-lane 구현 handoff, release/readback 경계. |
| `ROUTINESTORE_COMPATIBILITY_CACHE_CONTRACT.md` | #511용 `RoutineStore` / `PreferencesKey.ROUTINES` legacy compatibility cache 유지·퇴역 계약, Room 우선 conflict-winner, receiver/restore QA handoff. |
| `ROUTINE_CREATION_CTA_EXPERIMENT.md` | #455용 첫 차단 성공 이후 루틴 0개 사용자 대상 루틴 생성 soft CTA 실험 계약, analytics/QA/14일·30일 readback 경계. |
| `REPEAT_BLOCK_ROUTINE_SUGGESTION.md` | #531용 반복 차단 기반 자동 루틴 제안 계약, privacy-safe bucket/prefill, 기존 루틴 coverage guard, analytics/QA evidence template. |
| `ADMOB_MONETIZATION_RUNBOOK.md` | #16용 광고 단위 감사, `(not set)` 점검, guardrail, 안전한 수익화 실험 운영 런북. `monetization_interest_*` 관심도 CTA 계약과 #250류 AdMob application/ad unit id config handoff도 여기서 추적한다. |
| `PLAY_DEPLOY_SECRETS_RUNBOOK.md` | Play 배포 secret ownership, helper 범위, workflow restore matrix, Firebase Functions 경계 런북. |
| `REVIEW_PROMPT_LIFECYCLE.md` | #17용 리뷰 프롬프트 eligibility/drain 계약과 queryability guardrail 문서. |
| `REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md` | #307용 리뷰 프롬프트 shown 0 재측정, 버전별 lifecycle 표, Play Console 후행 지표 추적 런북. `scripts.tests.test_review_prompt_post_release_followthrough_docs`가 release/GA4/Play Console 경계를 고정한다. |
| `VERSION_ADOPTION_METRICS_GATE.md` | #359용 버전 채택률/최신 버전 cohort 판독 게이트. #13/#14/#16/#307 live 지표를 최신 코드 성과로 해석하기 전 확인한다. |
| `USAGE_STATS_PERSONALIZATION_MVP.md` | #119용 `docs/USAGE_STATS_PERSONALIZATION_MVP.md` Usage Access 선택형 개인화 discovery gate, guardrail, 측정 조건 문서. `scripts.tests.test_usage_stats_personalization_contract`가 stale title, privacy/activation guardrail, high-traffic docs 링크를 고정한다. |
| `PLAY_STORE_ASO.md` | #65용 Play Console ASO 수동 반영 후 baseline/14일·30일 추적 런북. |
| `FOCUS_SUMMARY_SHARE_MVP.md` | #211용 LockHistory 주간 집중 요약 공유 MVP 계약, privacy guardrail, analytics/QA 계획. |
| `ROUTINE_TEMPLATE_SHARE_MVP.md` | #407용 루틴 템플릿 공유 MVP 계약, Android share sheet 범위, privacy-safe payload, analytics/QA 계획. |
| `LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md` | #465용 잠금 기록 성과 리포트 UX 계약, empty/low-data 성취 카피, top apps positive framing, analytics/QA 계획. |
| `GOAL_LOCK_MVP.md` | #417용 목표 잠금 MVP 계약, 기간 기반 all-day/scheduled lock, Home card, analytics/QA 계획. |
| `PARENT_MODE_MVP.md` | #471용 부모 모드 / 아이에게 폰 주기 same-device MVP 계약, 보호자 PIN, 허용 앱, analytics/QA 계획. |
| `HOME_STATUS_CTA_STRUCTURE.md` | #463용 홈 화면 상태/CTA 구조 계약(`docs/HOME_STATUS_CTA_STRUCTURE.md`), 상태 텍스트/primary CTA 위계, #14/#455/#417 충돌 방지, QA evidence template. |
| `DESIGN_PRIMARY_COLOR_HIERARCHY.md` | #468용 primary color 사용 위계 source of truth. PR #546 이후 TopAppBar/navigation icon 구현 상태, 남은 visual QA/release 경계를 함께 고정한다. |
| `BLOCK_SCREEN_COPY_HIERARCHY.md` | #464용 차단 화면 copy/action hierarchy source of truth. PR #487 이후 BlockScreen copy/helper 구현 상태, 남은 visual QA/release 경계를 함께 고정한다. |
| `EMERGENCY_UNLOCK_FLOW_COPY.md` | #467용 긴급해제 reason/app/duration/countdown copy·step 계약, enum compatibility, disabled/helper copy, PR #575 이후 reason-required ON/OFF Compose UI QA baseline, 남은 device/release/readback 경계. |
| `SHARED_UI_OWNERSHIP_BOUNDARY.md` | #492용 공유 UI 소유권 / feature-private component import 경계, PermissionSettingDialog/TimerPicker code-lane handoff, static guard 기준. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `ops/stopit/` | Stopit 운영 cron과 전문 subagent가 공유하는 제품/지표/엔지니어링/릴리즈 컨텍스트 팩. |
| `superpowers/` | Superpowers-generated implementation planning and specification artifacts. |

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.
- Analytics/product-metrics docs work should treat `ANALYTICS_EVENT_DICTIONARY.md` as the contract definition and `GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` as the live registration/queryability runbook; do not collapse repo 문서 정리와 GA4 Admin 수동 작업을 같은 상태로 보고 닫지 않는다.

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
