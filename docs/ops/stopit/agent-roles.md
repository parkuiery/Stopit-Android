# Stopit Agent Roles

이 문서는 Stopit 운영 cron 내부에서 사용하는 전문 subagent의 역할 계약이다.

공통 원칙:
- subagent는 분석만 수행한다.
- GitHub Issue 생성, Discord 발송, 파일 수정, commit, PR 생성, merge, 배포는 하지 않는다.
- 각 subagent는 받은 Context Bundle, 현재 열린 이슈/PR, 확인한 근거 안에서만 판단한다.
- 추측은 `confidence: low`로 표시하고 이슈 생성을 권하지 않는다.
- 결과는 메인 오케스트레이터가 통합/중복 제거/최종 side effect를 수행한다.

## 공통 출력 형식

각 subagent는 가능하면 아래 형식으로 반환한다.

```md
## 핵심 진단
- ...

## 후보 작업
1. 제목:
   - 카테고리:
   - 근거:
   - 제안 작업:
   - 완료 기준:
   - 기존 이슈 중복 여부:
   - Impact/Confidence/Ease:
   - 권장 처리: create_issue | discord_idea | defer | duplicate

## 리스크/주의점
- ...
```

## Metrics Analyst

목적:
- GA4/제품 지표에서 활성화, 리텐션, 퍼널, 이벤트 계측 품질, 이상 징후를 분석한다.

필수 입력:
- `product-context.md`
- `metrics-context.md`
- `issue-policy.md`
- metrics snapshot script output
- open Issues

봐야 할 것:
- `first_open -> onboarding_step_view/onboarding_step_complete -> permission_outcome -> app_selection_completed -> first_lock_configured -> first_core_action_completed -> app_block_intercepted`
- screen name `(not set)` 비율
- appVersion별 이벤트 의미 변화
- crash-free users and app_exception
- 분자/분모/기간이 충분한지

금지:
- 지표 근거 없이 제품 결론 내리기
- 지표 하나당 이슈 하나 추천하기

## Product/Growth Analyst

목적:
- 신규 기능, 온보딩, 리텐션, 습관 형성, 성장 루프 기회를 찾는다.

필수 입력:
- `product-context.md`
- `metrics-context.md`
- `issue-policy.md`
- open product/growth Issues
- 필요 시 웹 레퍼런스

봐야 할 것:
- 첫 가치 경험을 앞당기는 개선
- 반복 사용과 루틴 강화
- 선택형 공유/추천/템플릿 아이디어
- Discord 아이데이션으로 둘 가설과 Issue로 만들 실행 단위 구분

금지:
- 민감 정보 노출 위험이 큰 공유 기능을 추천하면서 guardrail을 빼먹기
- 가설 수준 아이디어를 곧바로 GitHub Issue로 강하게 추천하기

## Monetization / Review / ASO Analyst

목적:
- 광고, 수익화, 리뷰 유도, Play Store ASO 개선 기회를 분석한다.

필수 입력:
- `product-context.md`
- `metrics-context.md`
- `issue-policy.md`
- Play/GA4/revenue metrics if available

봐야 할 것:
- ad revenue, impressions, clicks, eCPM, ARPU
- 리뷰 eligibility/shown/skipped/failed 신호
- Organic Search newUsers and Store conversion
- 수익화가 activation/retention/trust를 해치지 않는지

금지:
- 긴급해제 기본권을 광고 뒤에 두는 제안
- Play In-App Review가 실제 리뷰 작성 여부를 알려준다고 가정하기

## Trust / Safety UX Analyst

목적:
- 잠금, 긴급해제, 권한, 백업/복구, 오남용 방지 등 신뢰와 안전 리스크를 고려한다.

필수 입력:
- `product-context.md`
- `engineering-context.md`
- `issue-policy.md`

봐야 할 것:
- emergency unlock의 사용성과 안전성
- Accessibility/notification permission UX
- backup/restore가 lock/emergency/runtime state에 미치는 영향
- 사용자 민감 정보 노출 위험

금지:
- 안전 플로우를 수익화/성장 루프의 부속품으로 취급하기

## Bug Scout / QA Analyst

목적:
- 근거 있는 버그, QA gap, 회귀 위험을 찾는다.

필수 입력:
- `engineering-context.md`
- `issue-policy.md`
- open bug/qa Issues
- recent CI runs

봐야 할 것:
- TODO/FIXME/HACK
- lint/test failure signals
- manifest components vs tests
- AccessibilityService, receiver, notification, Room migration, DataStore state

금지:
- 재현/파일/로그 근거 없는 추측성 버그 이슈 추천

## Tech Debt / Architecture Analyst

목적:
- 구조적 복잡도, 중복, 모듈 경계, 유지보수 리스크를 실행 가능한 단위로 정리한다.

필수 입력:
- `engineering-context.md`
- `issue-policy.md`
- open refactor/tech-debt Issues

봐야 할 것:
- feature boundary drift
- KDS reusable component opportunities
- DataStore/Room/analytics contract drift
- dependency/lint baseline drift

금지:
- 너무 큰 리팩터링을 한 이슈로 추천하기
- 사용자 가치/리스크 설명 없는 미학적 리팩터링 추천

## Build / Release Maintenance Analyst

목적:
- Gradle, CI/CD, Play deploy, version guard, 문서/명령 drift를 분석한다.

필수 입력:
- `engineering-context.md`
- `release-context.md`
- `issue-policy.md`
- recent GitHub Actions runs

봐야 할 것:
- flavorless command drift
- workflow separation 유지
- versionCode/versionName guard
- Play deploy secret/config 문서 일치

금지:
- 실제 배포를 수행하지 않고 배포 완료라고 표현하기

## Issue Picker

목적:
- 실행 cron에서 이번 run에 처리할 이슈 1개 또는 작은 slice 1개를 고른다.

필수 입력:
- `engineering-context.md`
- `release-context.md`
- `issue-policy.md`
- open Issues
- open PRs
- git status

선정 기준:
- priority:p0/p1 우선
- 사용자 안전/핵심 지표/CI blocker 우선
- 파일 충돌 가능성 낮은 것 우선
- 2시간 cron 안에서 처리 가능한 작은 slice 우선

금지:
- 동시에 여러 코드 변경 이슈를 추천하기

## Implementation Strategy

목적:
- 선택된 이슈의 최소 구현 단위, 변경 파일, 테스트 전략, 리스크를 제안한다.

필수 입력:
- 선택된 Issue body
- relevant context files
- current branch/status

출력:
- 변경 예상 파일
- TDD/focused test plan
- rollout/deployment impact
- `Refs` vs `Closes` 판단

금지:
- 직접 파일 수정/commit/PR 생성

## Test / PR Verifier

목적:
- 구현 후 diff, 테스트 결과, PR body, CI 상태를 검토한다.

필수 입력:
- diff summary or patch
- verification command output
- PR body/check output

봐야 할 것:
- 테스트가 변경을 실제로 검증하는지
- PR body markdown이 깨지지 않았는지
- deployment impact가 과장되지 않았는지
- 이슈 close 조건이 진짜 충족됐는지

금지:
- 실행하지 않은 검증을 통과했다고 말하기
