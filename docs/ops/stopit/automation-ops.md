# Stopit 자동화 운영 토폴로지

이 문서는 GitHub issue #18 `스탑잇 지표 모니터링·이슈 실행·배포 자동화 운영`의 현재 운영 기준을 정리한다.

목표는 "자동화가 있다"는 말만 남기는 것이 아니라, **어떤 cron이 어떤 책임을 가지는지**, **무엇을 live source of truth로 확인해야 하는지**, **issue #18의 완료 기준이 현재 구조에서 어떻게 충족되는지**를 명확히 하는 것이다.

## 현재 운영 구조

Stopit 자동화는 더 이상 하나의 거대한 follow-through cron에만 의존하지 않는다.
현재는 아래처럼 역할이 분리된 cron topology를 기준으로 운영한다.

Branch Hygiene 정책상 `automation/*`는 PR head prefix가 아니다. 아래 `automation/stopit-*-lane` stable branches는 local lane worktree 기준선일 뿐이며, reviewable PR은 `docs/*`, `test/*`, `fix/*`, `feature/*`, `ci/*`, `chore/*` 같은 허용 prefix에서 만든다.

### 1) 제품/지표 분석 계층

- `stopit-daily-metrics-monitor`
  - GA4 스냅샷 수집
  - 열린 product/analytics 이슈와 중복 여부 확인
  - 신규 이상 신호를 이슈화할지 판단
  - 단, 스냅샷/`runReport` 호출 성공만으로 GA4 queryability가 건강하다고 보지 않는다. `customEvent:*`가 아직 Admin에 등록되지 않은 상태에서는 `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`이 **제품 no-data가 아니라 registration gap** 신호다.
- `stopit-feature-ideation-discord`
  - 지표 기반 신규 기능/실험 아이디어 정리
  - 가설 수준이면 GitHub Issue 대신 아이데이션 채널/보고로 남김
- `stopit-repo-health-issue-discovery`
  - 저장소 건강도, QA gap, 유지보수 이슈 발굴

### 2) 실행 계층

- `stopit-executor-docs-lane`
  - docs/ops/analytics/ASO/runbook 성격 이슈 처리
  - stable branch는 `automation/stopit-docs-lane` 같은 로컬 lane/worktree 전용 local lane 브랜치다. PR head로는 쓰지 않고, reviewable 작업은 `docs/issue-...` 또는 workflow/운영 변경이면 `ci/issue-...`처럼 Branch Hygiene 허용 prefix로 새 브랜치를 만든다.
- `stopit-executor-qa-lane`
  - QA 기준, 테스트, 재현, 저위험 회귀 방지 작업
  - stable branch는 `automation/stopit-qa-lane`이며 PR head는 `test/issue-...` 또는 `fix/issue-...`를 사용한다.
- `stopit-executor-code-lane`
  - Android 코드 변경 이슈 처리
  - stable branch는 `automation/stopit-code-lane`이며 PR head는 `fix/issue-...`, `feature/issue-...`, `refactor/issue-...`처럼 변경 성격에 맞춘다.
- `stopit-merge-controller`
  - lane PR의 check/merge 가능 여부 확인
  - 저위험·green PR만 순차 merge
  - `automation/stopit-merge-lane`는 merge controller 작업대 전용이며 PR head가 아니다.

### 3) 릴리즈 계층

- `stopit-release-orchestrator-internal`
  - release PR 판단
  - `main` merge 후 semver tag 생성
  - Google Play `internal` track deploy 흐름 확인
  - `automation/stopit-release-lane`는 release 판단 작업대 전용이고, release PR은 `release/<version>` head를 사용한다.
- `stopit-local-branch-cleanup`
  - lane/worktree 정리 보조

## legacy cron과 현재 기준의 관계

초기 자동화는 `stopit-issue-executor-and-release-followthrough` 단일 cron으로 시작했다.
이후 운영이 lane 기반으로 확장되면서, **실제 follow-through 책임은 docs/qa/code lane + merge controller + release orchestrator 조합**으로 분리됐다.

따라서 현재는 다음처럼 해석한다.

- "이슈 실행/배포 follow-through cron이 등록되어 있다"의 현재 source of truth는
  - 단일 legacy cron 1개 여부가 아니라,
  - lane 실행 cron들과 merge/release controller가 **활성 상태로 등록되어 있는지**다.
- legacy cron이 남아 있더라도 disabled 상태면 참고용 이력으로만 본다.
- 실제 운영 판단은 `~/.hermes/cron/jobs.json`의 현재 enabled job 집합을 우선한다.

## issue #18 완료 기준 매핑

### 1. 지표 스냅샷 스크립트가 로컬에서 성공한다

기준:

- 스크립트 경로: `/Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
- 성공 조건:
  - credential path가 존재한다.
  - GA4 `runReport` 호출이 실패 없이 JSON `ok: true`를 반환한다.
  - 결과가 `~/.hermes/state/stopit_metrics_history.jsonl`에 append 된다.

중요한 해석 경계:

- 위 성공 조건은 **snapshot transport/API 호출 성공**을 뜻할 뿐, open issue `#13`의 GA4 custom-event queryability가 해결됐다는 뜻은 아니다.
- 2026-05-29 live 기준 metadata에서 확인된 custom 축은 `customUser:routines_count`뿐이고, activation/review/monetization용 `customEvent:*`는 아직 Admin registration/manual follow-through가 남아 있다.
- 따라서 metrics/product cron은 `customEvent:*` 쿼리에서 `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`이 나오면 최근 데이터 부족이나 제품 이벤트 부재로 과해석하지 말고, 먼저 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 registration ledger와 수동 경계를 확인한다.
- #13의 남은 외부/manual 경계는 GA4 Admin 실제 등록, 등록 후 metadata/runReport 재확인, 배포 후 14일 재측정이다.

권장 확인:

```bash
python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py
```

### 2. 지표 모니터링 cron이 등록되어 있다

현재 기준 job:

- `stopit-daily-metrics-monitor`

확인 소스:

```bash
python3 - <<'PY'
import json, os
path = os.path.expanduser('~/.hermes/cron/jobs.json')
with open(path) as f:
    jobs = json.load(f)['jobs']
for job in jobs:
    if job.get('name') == 'stopit-daily-metrics-monitor':
        print(job['id'], job['enabled'], job['schedule'])
PY
```

### 3. 이슈 실행/배포 follow-through cron이 등록되어 있다

현재 기준 job set:

- `stopit-executor-docs-lane`
- `stopit-executor-qa-lane`
- `stopit-executor-code-lane`
- `stopit-merge-controller`
- `stopit-release-orchestrator-internal`

주의:

- 이 기준은 운영 구조가 단일 executor에서 lane topology로 바뀌었기 때문에 필요하다.
- `stopit-issue-executor-and-release-followthrough`가 disabled여도, 위 job set이 enabled면 현재 운영 기준에서는 acceptance를 충족한 것으로 본다.

### 4. 중복 이슈가 정리되어 canonical issue만 남는다

현재 canonical 제품 이슈 묶음:

- `#13` GA4 계측 품질 및 이벤트 딕셔너리 개선
- `#14` 첫 잠금 활성화 퍼널 개선
- `#16` AdMob 성과 감사 및 안전한 수익화 실험 설계
- `#17` 리뷰 프롬프트 생애주기 개선으로 신뢰 리뷰 확보
- `#18` 자동화 운영

운영 원칙:

- 같은 문제를 다시 자동 생성하지 않는다.
- metrics cron은 열린 이슈를 먼저 읽고, 이미 흡수 가능한 문제면 새 이슈를 만들지 않는다.
- duplicate 정리 여부는 GitHub open issue list와 기존 정리 코멘트로 확인한다.

### 5. 자동화가 생성한 변경/PR/배포 결과는 현재 대화 또는 origin delivery로 보고된다

현재 기준:

- cron job output은 Hermes cron 실행 결과로 남는다.
- 이 lane 같은 실행 cron은 최종 보고를 현재 응답으로 남긴다.
- release/deploy는 실제 run/PR/tag/merge evidence를 동반해 보고한다.

중요:

- 실제 배포를 하지 않았으면 배포 완료라고 말하지 않는다.
- disabled job이나 stale 문서만으로 운영 완료를 주장하지 않는다.

## Live source of truth 우선순위

1. `~/.hermes/cron/jobs.json`
2. GitHub Issues / PRs / Actions
3. `~/.hermes/scripts/stopit_metrics_snapshot.py`
4. `docs/ops/stopit/*.md`

문서는 운영 기준을 설명하지만, **현재 enabled/paused 상태는 항상 live cron 설정 파일이 우선**이다.

## 운영 점검 체크리스트

### topology 확인

```bash
python3 - <<'PY'
import json, os
path = os.path.expanduser('~/.hermes/cron/jobs.json')
with open(path) as f:
    jobs = json.load(f)['jobs']
for job in jobs:
    name = job.get('name', '')
    if name.startswith('stopit-'):
        print(name, '| enabled=', job.get('enabled'), '| schedule=', job.get('schedule', {}).get('display'))
PY
```

### metrics script 확인

```bash
python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py
```

### GitHub canonical issue / PR 상태 확인

```bash
gh issue list --state open --limit 50 --json number,title,labels,url
gh pr list --state open --limit 30 --json number,title,headRefName,baseRefName,url,isDraft
```

### Ops CI docs-contract 연결 확인

workflow 변경 PR은 `actionlint-only green`만으로 운영 계약이 안전하다고 해석하지 않는다. YAML 문법 검증은 `Workflow syntax lint`가 담당하고, 릴리즈/CI/CD workflow와 운영 문서의 source-of-truth drift는 `Docs/runbook contract tests`가 담당한다. 특히 아래 기존 workflow contract 테스트는 docs/workflow-only 변경에서도 Ops CI `docs_contract` 경로로 materialize되어야 한다.

```bash
python3 -m unittest \
  scripts.tests.test_android_ci_artifact_retention \
  scripts.tests.test_android_ci_path_gating \
  scripts.tests.test_play_deploy_tag_governance \
  scripts.tests.test_release_gate_retarget_triggers \
  scripts.tests.test_ops_ci_workflow -v
```

이 묶음은 Android CI artifact retention, Android CI path gating, Play Deploy tag governance, release PR retarget trigger 계약을 고정한다. 새 workflow 계약 테스트를 추가할 때는 `.github/workflows/ops-ci.yml`의 `docs_contract` filter와 `Docs/runbook contract tests` 실행 목록, 그리고 `scripts.tests.test_ops_ci_workflow` meta-contract를 함께 업데이트한다.

## 이 문서가 다루지 않는 것

이 문서는 다음을 설명하지 않는다.

- 개별 Android 기능 구현 방법
- release PR의 상세 QA 절차
- Play production 승격 승인 정책
- 특정 lane의 일회성 진행 상황

그런 내용은 각각의 issue/PR, release context, CI run, cron output이 source of truth다.

## 관련 문서

- `docs/ops/stopit/README.md`
- `docs/ops/stopit/context-bundle-protocol.md`
- `docs/ops/stopit/release-context.md`
- `docs/METRICS_ANALYSIS.md`
