# 홈 화면 상태/CTA 구조 계약

> **⚠️ 2026-08-18: 이 문서는 superseded 다. #463은 not planned 로 종료됐다.**
>
> 홈은 PR #1099 이후 `HomeCardArbiter`가 카드 한 장만 고르는 구조로 다시 지어졌고, 이 문서가
> 정의한 "단일 primary CTA 위계"를 중재자가 대체한다. `HomeStatusCtaCard`와 read model은
> 삭제됐다. 상단에 카드를 더 붙이면 #1151(큰 글꼴에서 잠금 스위치가 스크롤 밖으로 밀림)이
> 악화되므로 이 문서의 설계를 그대로 되살리지 않는다. 루틴 생성 넛지는 #455에서 `HomeCard`
> variant로 재구현한다. 아래 내용은 **당시 설계 기록**으로만 남긴다.


Issue: #463 `[UX] 홈 화면 상태/CTA 구조 개선`

이 문서는 홈 화면 상태/CTA 구조의 product/design/analytics source of truth다. PR #500(`c73d7aa1`) 이후 Home 상태 read model/UI/resource/locale baseline이 구현됐고, PR #606(`82180c8`) 이후 선택 앱 없음·첫 잠금 준비·보호 중 Compose baseline이 추가됐으며, PR #948(`4844b7a`) 이후 활성 수동/타이머 잠금도 `TIMED_LOCK_ACTIVE` 상태 카드로 분리됐다. 다만 issue #463 closure는 실제 디바이스 screenshot/visual/TalkBack QA, release/tag/Play deploy, GA4 Admin/queryability, D+14/D+30 readback 경계가 끝난 뒤 판단한다. 새 docs-only 후속은 `Refs #463`를 사용하고, `Closes #463`는 남은 외부/manual/readback 경계까지 충족됐을 때만 사용한다.

## 목적

홈은 Stopit의 핵심 진입점이다. 사용자는 첫 화면에서 다음 네 가지를 텍스트만으로도 판단할 수 있어야 한다.

1. 지금 Stopit이 꺼져 있는지, 켜져 있는지, 타이머가 예약/실행 중인지.
2. 몇 개 앱이 차단 대상으로 선택되어 있는지와 앱 변경 진입점이 어디인지.
3. 지금 가장 중요한 primary action이 무엇인지.
4. 즉시 차단, 타이머 설정, 루틴/목표 잠금 같은 반복 사용 진입점이 어떤 차이가 있는지.

## 현재 구현 기준선

현재 Home 구현 기준선은 아래 파일에서 확인한다.

- `app/src/main/java/com/uiery/keep/feature/home/HomeStatusCtaReadModel.kt`
  - `HomeStatusKind`: 선택 앱 없음, 첫 잠금 준비, 반복 사용자 준비, 보호 중, 타이머 잠금 진행 중 상태를 분리한다. `TIMED_LOCK_ACTIVE`는 PR #948(`4844b7a`) 이후 active manual/timer lock deadline이 남아 있을 때 즉시 차단 CTA보다 우선하는 landed state다.
  - `buildHomeStatusCtaModel(...)`: 선택 앱 수, `showFirstLockActivationCta`, `hasActiveTimedLock`, 목표 잠금 card 존재 여부를 하나의 primary/secondary CTA 계약으로 만든다. `isKeep=true`는 `KEEP_ACTIVE`가 `TIMED_LOCK_ACTIVE`보다 우선하고, `hasActiveTimedLock=true`는 선택 앱 없음/첫 잠금/ready CTA보다 우선한다.
- `app/src/main/java/com/uiery/keep/feature/home/HomeScreen.kt`
  - `HomeStatusCtaCard`: 기존 `CategoryButton`/`FirstLockActivationCta` 의미를 하나의 상태 카드로 통합해 선택 앱 수, primary CTA, 보조 진입점을 함께 보여준다.
  - `GoalLockProgressCard`: 목표 잠금이 있을 때 Home 진행 상태를 보여주는 #417 표면으로 유지한다.
- `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt`
  - `changeIsKeep(...)`: 선택 앱이 없으면 Keep 시작 대신 앱 선택 안내를 먼저 보여준다.
  - `hasActiveTimedLock`: 저장된 수동/타이머 잠금 deadline이 아직 유효하면 Home 상태 카드가 즉시 차단 CTA보다 타이머 진행 상태를 먼저 보여준다.
  - `showFirstLockActivationCta`: 첫 잠금 CTA 노출 조건.
  - `goalLockCard`: 목표 잠금 Home card read model.
- `app/src/test/java/com/uiery/keep/feature/home/HomeStatusCtaReadModelTest.kt`
  - 선택 앱 없음 / 첫 잠금 준비 / 보호 중 / 타이머 진행 중 / 목표 잠금 동시 노출 read-model 계약을 고정한다. PR #948 이후 `activeTimedLockPresentsTimerStatusBeforeStartCta`가 timer status priority를 고정한다.
- `app/src/androidTest/java/com/uiery/keep/feature/home/HomeStatusCtaCardIntegrationTest.kt`
  - `HomeStatusCtaCard`를 실제 Compose/KDS theme 안에서 렌더링해 선택 앱 없음 / 첫 잠금 준비 / 보호 중 / 타이머 진행 중 상태의 텍스트, primary CTA, secondary CTA 노출/비노출을 고정한다.
- `DESIGN.md`
  - KDS token, primary color 제한, 상태를 색상만으로 전달하지 않는 접근성 규칙.

이 문서는 기존 MVI/navigation side effect를 보존하면서 홈의 **상태 확인 + 다음 행동** 위계를 명확히 하는 계약이다. `CategoryButton`과 `FirstLockActivationCta`의 접근성 의미는 Home 상태 카드 안에서 동등하게 제공되어야 한다.

## 상태 모델

Home UI는 최소한 아래 상태를 분리해 보여줘야 한다.

| 상태 | 사용자에게 보여야 할 문장 방향 | Primary CTA | 보조 진입점 |
| --- | --- | --- | --- |
| 꺼짐 + 선택 앱 없음 | `차단할 앱을 먼저 선택해 주세요` | `차단 앱 선택` | 타이머/루틴은 비활성 또는 설명 우선 |
| 꺼짐 + 선택 앱 있음 + 첫 잠금 전 | `N개 앱을 막을 준비가 됐어요` | `지금 차단 시작` | `타이머 설정`, `차단 앱 변경` |
| 꺼짐 + 선택 앱 있음 + 반복 사용자 | `N개 앱을 선택했어요` | `지금 차단 시작` 또는 최근 사용 맥락 기반 CTA | `타이머 설정`, `루틴 관리`, `잠금 기록` |
| 켜짐 | `N개 앱을 막고 있어요` | `차단 끄기`보다 현재 상태 확인을 우선하고, 해제/변경은 보조 위계 | `잠금 기록` (잠금 활성 중에는 `차단 앱 변경`을 노출하지 않는다 — 차단 앱 변경은 우회 경로가 되므로 차단을 끈 뒤에만 가능) |
| 타이머 예약/실행 중 | `HH:MM까지 지키는 중` 또는 `남은 시간 ...` | 상태 카드 자체가 primary status가 된다. PR #948 이후 active timed lock은 `지금 차단 시작`이 아니라 `타이머 보호 중` 계열 status를 보여준다. | `시간 변경`, `차단 앱 변경`, `잠금 기록` |
| 목표 잠금 진행 중 | `목표 잠금이 진행 중이에요` + 기간/모드 bucket | 목표 card를 상태 표면으로 유지 | 상세/기록 진입 |

원칙:

- 상태는 색상만으로 전달하지 않는다. `켜짐`, `꺼짐`, `예약됨`, `진행 중` 같은 텍스트가 필요하다.
- 선택 앱 수는 항상 사용자에게 보이는 문장 또는 badge로 제공한다. 앱 이름/package/raw selected app list는 analytics payload나 공유 표면에 쓰지 않는다.
- 즉시 차단과 타이머 설정은 같은 행동처럼 보이면 안 된다. 즉시 차단은 지금부터 막는 행동이고, 타이머는 끝나는 시각/기간을 사용자가 정하는 행동이다.
- 루틴/목표 잠금/잠금 기록 진입점은 Home에서 숨기지 않되, 첫 방문 사용자의 primary CTA보다 강하게 보이면 안 된다.

## CTA 위계

### 1. Primary CTA

Primary CTA는 한 화면에서 하나만 가장 강해야 한다.

- 첫 방문/첫 잠금 전: `지금 차단 시작` 또는 같은 의미의 Keep 시작 CTA.
- 선택 앱 없음: `차단 앱 선택`.
- 이미 차단 중: primary CTA를 새 행동으로 과장하지 말고 현재 보호 상태를 primary status로 보여준다.
- 타이머/목표 잠금 진행 중: 남은 시간/진행 상태가 가장 강한 정보다.

KDS 기준:

- `KeepButton`, `KeepTheme.colors.primary`, 기존 spacing/shape token을 우선한다.
- Primary color는 단일 primary CTA, selected/active state, lock/focus/routine active state에 제한적으로 사용한다.
- navigation icon, secondary action, 단순 정보 badge에 primary color를 남용하지 않는다.

### 2. Secondary CTA

Secondary action은 Home에서 찾을 수 있어야 하지만 primary action보다 강하면 안 된다.

- `타이머 설정`: 즉시 차단과 목적을 구분하는 보조 CTA.
- `차단 앱 변경`: 선택 앱 수 옆 또는 상태 카드 안에서 명확히 접근 가능.
- `루틴 관리`: 반복 사용/retention 진입점. 첫 차단 성공 이후 soft CTA(#455)와 충돌하지 않게 한다.
- `잠금 기록`: 성과/회고 표면. LockHistory 성과 리포트(#465)와 카피 톤을 맞춘다.

### 3. CTA 충돌 방지

- `FirstLockActivationCta`(#14)는 첫 잠금 전 사용자에게만 의미가 있다. 반복 사용자에게 같은 준비 CTA를 반복하지 않는다.
- `ROUTINE_CREATION_CTA_EXPERIMENT.md`(#455)는 `first_core_action_completed` 이후 루틴 0개 사용자 대상 soft CTA다. Home 구조 개편에서 onboarding/pre-first-lock 사용자에게 루틴 생성 압박으로 재해석하지 않는다.
- `GoalLockProgressCard`(#417)는 진행 상태 표면이다. 장기 목표 잠금 implementation/readback 전에는 Home card가 있다는 사실만으로 retention 성과를 주장하지 않는다.
- 광고/수익화 CTA는 Home의 보호 상태, 첫 차단 CTA, 긴급/안전 흐름보다 위에 오면 실패다.

## Analytics / metrics 해석 계약

Home status/CTA 구조 개선은 새 analytics 이벤트를 반드시 요구하지 않는다. 기존 이벤트 의미를 먼저 보존한다.

| 이벤트 | Home 구조에서의 의미 |
| --- | --- |
| `first_lock_configured` | 앱 1개 이상 선택 후 첫 잠금 준비가 완료됨. 준비 완료 신호이며 실제 차단 완료가 아니다. |
| `first_core_action_completed` | 첫 가치 경험. 차단 화면 진입/피드백 계약과 함께 해석한다. |
| `app_block_intercepted` | 실제 차단 발생. North Star `주간 활성 차단 사용자 수`의 핵심 증거. |
| `keep_mode_toggled` | Home Keep 토글 상태 변화. 선택 앱 없음 상태에서 성공 이벤트처럼 해석하지 않는다. |
| `lock_scheduled` | 타이머/루틴 예약. 즉시 차단 CTA와 분리해 해석한다. |

해석 guardrail:

- #14의 홈 첫 잠금 CTA(PR #256 `bce1cda`), 첫 차단 성공 피드백(PR #279 `5c6331d`), 홈 Keep/타이머 시작 안내(PR #283 `35c13eb`)는 `origin/develop`에는 있으나 2026-06-02 기준 `origin/main`/production tag `v1.7.7`에는 없다. 따라서 production 데이터는 post-fix 성과가 아니라 pre-#256/#279/#283 baseline으로 본다.
- #463 repo-internal 구현은 PR #500/PR #606/PR #948 기준으로 Home 상태 read model, KDS 상태 카드, shipped locale, focused JVM/Compose baseline까지 `develop`에 반영됐다. 그러나 release/tag/Play deploy와 14일 관측 전에는 activation 개선을 단정하지 않는다.
- **2026-08-18 결론: #463은 superseded로 종료됐다.** PR #1099가 홈 UI를 `v1.7.7`로 되돌리며 `HomeStatusCtaCard` 호출부를 제거한 뒤 컴포저블은 테스트에서만 렌더됐고, 노출 계측만 상태 계산 경로에 남아 유령 노출을 집계했다(#1166). 검토 결과 복원하지 않기로 했다 — 홈은 이후 `HomeCardArbiter`가 카드 한 장만 고르는 구조로 다시 지어졌고, 상단에 카드를 더 붙이면 #1151이 악화된다. 컴포저블·read model·정적 계약·QA 레인은 모두 삭제됐고 이 문서는 설계 기록으로만 남는다.
- GA4 Admin에서 `customEvent:source`, `customEvent:block_source`, `customEvent:selected_app_count`류 축이 queryable인지 확인하기 전에는 경로별 결론을 낮은 confidence로 둔다.
- 새 이벤트를 추가한다면 privacy-safe enum/bucket만 허용한다. 금지 payload/query 축: 앱 이름, package name, raw selected app list, raw session history, raw timestamp.

## 구현 완료 기준선과 남은 handoff

#463의 repo-internal 구현 기준선은 아래 범위를 이미 포함한다.

1. Home 상태 read model이 꺼짐/켜짐/타이머/목표 잠금/선택 앱 없음 상태를 텍스트 계약으로 분리한다.
2. `HomeStatusCtaCard`가 `KeepTheme`/KDS token 기반으로 선택 앱 수, primary status/CTA, 보조 진입점을 한 카드 안에 제공한다.
3. shipped locale 리소스가 Home 상태/CTA 문자열을 포함하고, PR #948 이후 active timed lock copy도 각 locale에 추가됐다.
4. 자동 baseline이 상태 read model과 Compose card를 고정한다.

남은 handoff는 repo-internal 구현이 아니라 release/manual/readback 증적이다.

- release-candidate 실제 기기 screenshot/visual/TalkBack spot-check.
- release/tag/Play deploy 포함 여부 확인.
- GA4 Admin/queryability 확인 뒤 D+14/D+30 activation/readback 비교.

## Manual QA evidence template

```md
## Home status/CTA QA evidence
- Issue: #463
- Build / variant:
- Device / Android version:
- Theme: light / dark
- User state:
  - selected app count: 0 / 1 / many
  - first lock recorded: yes / no
  - keep mode: on / off
  - timer: none / scheduled / running
  - goal lock: none / active / completed
- Screens checked:
  - Home initial state:
  - App selection/change entry:
  - Immediate lock CTA:
  - Timer CTA:
  - Routine / LockHistory entry:
- Text-only state clarity: pass / fail
- Primary CTA is visually strongest: pass / fail
- Primary color not overused: pass / fail
- Commands:
  - `python3 -m unittest scripts.tests.test_home_status_cta_structure_contract -v`
  - `./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.home.HomeStatusCtaReadModelTest'`
  - `./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.home.HomeStatusCtaCardIntegrationTest`
- Screenshot/video evidence:
- Notes:
```

## PR / issue closing discipline

- Docs-only PR: `Refs #463`. 문서가 PR #500/PR #606/PR #948 landed state를 반영하더라도 release/manual/readback 경계가 남으면 이슈를 닫지 않는다.
- Implementation PR: repo-internal code/resource/test/locale baseline이 추가로 바뀌는 경우 해당 PR이 acceptance를 얼마나 채우는지 명시한다.
- Closure boundary: release-candidate 실제 기기 visual/TalkBack evidence, release/tag/Play deploy, latest-version adoption, GA4 Admin queryability, 14일/30일 readback까지 확인된 뒤에만 `Closes #463`를 검토한다.
