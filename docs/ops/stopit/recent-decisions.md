# Stopit Recent Decisions

이 파일은 전문 에이전트가 반복해서 알아야 하는 장기 운영 결정만 짧게 보관한다.

넣지 않는다:
- 특정 PR/Issue 진행상황
- 오늘의 지표 수치
- 완료 로그
- 오래 지나면 틀릴 상태

## 운영 결정

- 스탑잇 운영은 GitHub Issue 중심으로 관리한다.
- GitHub Issue는 한국어로 작성한다.
- 지표 개선뿐 아니라 버그, 리팩터링, 기술부채, QA, 성능, 유지보수, 문서 이슈도 자동 발굴 대상이다.
- 기능/실험 아이디어는 먼저 Discord 아이데이션 채널로 보내고, 실행 단위가 명확할 때만 GitHub Issue로 전환한다.
- 지표 기반 이슈는 하루 최대 3개, 저장소 건강도 이슈는 하루 최대 5개, 아이디어의 Issue 전환은 하루 최대 2개로 제한한다.
- 외부 side effect는 메인 오케스트레이터가 최종 검토 후 수행한다. 분석 subagent는 직접 이슈 생성, Discord 발송, 파일 수정, PR 생성을 하지 않는다.
- Claude 계열 런타임/에이전트는 사용하지 않는다.
- 일반 개발 PR base branch는 `develop`이다.
- Stopit Android는 `dev`/`prod` flavor가 있으므로 flavorless Gradle task를 피하고 variant-specific task를 우선한다.
- PR body나 Issue comment가 markdown/backtick/괄호/멀티라인을 포함하면 temp file과 `--body-file`을 사용한다.
- 이슈 실행/배포 follow-through의 현재 source of truth는 legacy 단일 cron이 아니라 `stopit-executor-{docs,qa,code}-lane` + `stopit-merge-controller` + `stopit-release-orchestrator-internal` 조합이다.
- 실행 lane은 "한 번에 닫히는 작은 slice"만 찾다가 멈추지 않는다. 같은 이슈에서 더 진행 가능한 코드/테스트/문서/QA/운영 준비가 남아 있으면 실제 외부 경계(배포 대기, Play Console 수동 반영, 대표님 승인, 디바이스·콘솔 증적 부족 등)를 만날 때까지 계속 밀어붙인다.
- 안전/잠금/긴급해제/권한/백업/복구 플로우는 신뢰 리스크가 커서 QA 기준을 높게 본다.
- 실제 Play 배포를 수행하지 않았으면 배포 완료라고 말하지 않는다. tag-triggered CD는 기본적으로 internal track이다.
- Play deploy secret ownership / helper scope / `GOOGLE_SERVICES_JSON` restore matrix / Firebase Functions promotion secret boundary의 장기 source of truth는 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`다.
- issue #13의 docs/ops 범위는 이벤트 딕셔너리만이 아니라 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`에 정리된 GA4 Admin 등록 ledger, metadata 증적 포맷, issue/PR handoff까지 포함한다.
- issue #13은 docs lane 문서화만으로 닫지 않는다. 실제 `customEvent:*` 등록, live metadata/runReport 확인, 배포 후 14일 재측정이 끝나기 전까지는 `Refs #13`과 외부/manual 경계를 명시하는 것이 기본값이다.
- 2026-06-03/04 screen quality smoke(`13,780 / 22,584 = 61.0%` 14일 query, `23,074 / 36,707 = 62.9%` 30일 metrics snapshot)처럼 live 수치가 흔들려도, 관련 screen-view 보강 PR이 `origin/main`/production tag에 없고 최신 version active share가 `30% 미만`이면 post-fix 성과로 승격하지 않고 release boundary 전 중간 smoke로만 기록한다.

## 업데이트 규칙

새로운 장기 결정이 생기면 이 파일에 한 줄로 추가한다.
임시 상태는 GitHub Issue/PR, cron output, metrics snapshot에 둔다.
