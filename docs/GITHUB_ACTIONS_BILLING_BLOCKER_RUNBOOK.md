# GitHub Actions Billing Blocker Runbook

이 문서는 GitHub-hosted runner가 결제/지출 한도 문제로 시작되지 않을 때 Stopit PR/릴리즈 큐를 진단하고 복구하는 운영 절차다.

## 증상

대표적인 annotation:

```text
The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings
```

이 메시지는 Gradle, Android 테스트, workflow YAML, branch naming 문제가 아니다. GitHub Actions runner가 job을 시작하기 전에 계정/결제 경계에서 차단된 상태다.

## 영향 범위

Stopit의 주요 workflow는 GitHub-hosted runner에 의존한다.

- `Android CI`
- `Branch Hygiene`
- `Version Guard`
- `Android Release QA`
- `Android Release Build`
- `Play Deploy`

따라서 이 blocker가 발생하면 다음을 모두 보수적으로 중단 상태로 본다.

- `develop` 대상 일반 PR merge
- `main` 대상 release/hotfix PR merge
- SemVer tag 기반 Play internal deploy
- manual production promotion dispatch

## 빠른 판별 절차

PR 하나에서 code failure인지 billing blocker인지 분리한다.

```bash
gh pr checks <PR_NUMBER>
gh pr view <PR_NUMBER> --json headRefName,headRefOid,url
```

최신 branch run과 job annotation을 확인한다.

```bash
BRANCH=<headRefName>
gh run list --branch "$BRANCH" --limit 10 --json databaseId,attempt,workflowName,status,conclusion,url

gh run view <RUN_ID> --json jobs,attempt

gh api repos/parkuiery/Stopit-Android/check-runs/<CHECK_RUN_ID>/annotations
```

다음 조건을 만족하면 repo/code failure가 아니라 billing blocker로 분류한다.

- `Branch Hygiene`처럼 코드와 무관한 governance job도 같은 annotation으로 시작 실패한다.
- `Android CI`의 초기 job도 같은 annotation으로 시작 실패한다.
- 최신 head SHA 기준으로 rerun해도 run id/attempt 또는 check-run annotation이 같은 메시지를 유지한다.

## 큐 전체 판단

여러 PR이 같은 모양으로 실패 중이면 모든 PR을 하나씩 파지 않는다. 대표 샘플 하나를 고른다.

권장 샘플:

1. 최신 docs/ci/chore PR처럼 저위험 PR
2. 현재 head SHA가 명확하고 같은 head에 이미 stale comment가 적은 PR

샘플에서 다음 두 계열을 한 번만 rerun한다.

- `Branch Hygiene`
- `Android CI`

둘 다 같은 billing annotation이면 큐 전체를 외부 GitHub billing/account blocker로 보고한다. 이때는 branch rename, Gradle 수정, no-op commit을 만들지 않는다.

## 복구 작업

GitHub repository/account owner가 수행한다.

1. GitHub `Billing & plans`에서 결제 실패 또는 Actions spending limit 초과를 복구한다.
2. 차단 당시 실패한 대표 PR에서 `Branch Hygiene`와 `Android CI`를 rerun한다.
3. job이 실제로 시작되는지 확인한다.
4. release 후보가 있었으면 `Version Guard`, `Android Release QA`, `Android Release Build`도 새 run에서 materialize되는지 확인한다.
5. Play deploy 또는 production promotion이 pending이었다면 billing 복구 후 tag/manual workflow run을 새로 확인한다.

## 복구 후 확인 명령

```bash
# 일반 PR 대표 샘플
gh pr checks <PR_NUMBER>
gh run list --branch <headRefName> --limit 10 --json databaseId,attempt,workflowName,status,conclusion,url

# release/hotfix PR이라면 main 대상 required gate가 생겼는지 확인
gh pr checks <RELEASE_PR_NUMBER> --required
gh pr view <RELEASE_PR_NUMBER> --json statusCheckRollup,mergeable
```

성공 기준:

- `Branch Hygiene` job이 annotation 없이 실제 step을 실행한다.
- `Android CI`가 `Classify Android CI scope` 이후 Gradle/runtime smoke 단계까지 진행한다.
- release/hotfix PR이면 `Version Guard`, `Android Release QA`, `Android Release Build`가 required check로 materialize된다.

## PR/이슈 코멘트 템플릿

```md
현재 head `<SHA>` 기준 required check가 repo/code failure가 아니라 GitHub Actions billing/account blocker로 시작 실패했습니다.

- Branch Hygiene: <run/check URL>
- Android CI: <run/check URL>
- annotation: `The job was not started because recent account payments have failed or your spending limit needs to be increased...`

남은 실제 경계는 GitHub `Billing & plans` 복구 후 같은 head에서 required checks rerun입니다. 이 상태에서는 branch rename/no-op commit/Gradle 수정으로 해결되지 않습니다.
```

## 하지 말 것

- billing annotation을 branch hygiene 실패로 오해하고 replacement PR을 만들지 않는다.
- 같은 head SHA에 이미 최신 Korean hold comment가 있으면 중복 comment를 남기지 않는다.
- repo 내부 수정 없이 CI를 다시 자극하려고 no-op commit을 만들지 않는다.
- required checks가 시작 실패한 상태에서 PR을 merge하지 않는다.
- Play deploy가 실제로 실행되지 않았는데 internal/production 배포 완료라고 보고하지 않는다.

## 관련 운영 문서

- `docs/GIT_WORKFLOW.md`
- `docs/PLAY_DEPLOYMENT.md`
- `docs/ops/stopit/automation-ops.md`
- `docs/ops/stopit/release-context.md`
