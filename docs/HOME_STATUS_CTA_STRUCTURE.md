# 홈 화면 상태/CTA 구조 계약

Issue: #463 `[UX] 홈 화면 상태/CTA 구조 개선`

이 문서는 홈 화면을 code-lane에서 재구성할 때 따라야 할 product/design/analytics 계약이다. docs-lane 산출물이므로 **구현 완료가 아니다**. PR body는 구현이 실제로 들어가기 전까지 `Refs #463`를 사용하고, `Closes #463`는 Home UI/resource/test/locale parity/QA evidence가 acceptance criteria를 만족한 뒤에만 사용한다.

## 목적

홈은 Stopit의 핵심 진입점이다. 사용자는 첫 화면에서 다음 네 가지를 텍스트만으로도 판단할 수 있어야 한다.

1. 지금 Stopit이 꺼져 있는지, 켜져 있는지, 타이머가 예약/실행 중인지.
2. 몇 개 앱이 차단 대상으로 선택되어 있는지와 앱 변경 진입점이 어디인지.
3. 지금 가장 중요한 primary action이 무엇인지.
4. 즉시 차단, 타이머 설정, 루틴/목표 잠금 같은 반복 사용 진입점이 어떤 차이가 있는지.

## 현재 구현 기준선

현재 Home 구현 기준선은 아래 파일에서 확인한다.

- `app/src/main/java/com/uiery/keep/feature/home/HomeStatusCtaReadModel.kt`
  - `HomeStatusKind`: 선택 앱 없음, 첫 잠금 준비, 반복 사용자 준비, 보호 중 상태를 분리한다.
  - `buildHomeStatusCtaModel(...)`: 선택 앱 수, `showFirstLockActivationCta`, 목표 잠금 card 존재 여부를 하나의 primary/secondary CTA 계약으로 만든다.
- `app/src/main/java/com/uiery/keep/feature/home/HomeScreen.kt`
  - `HomeStatusCtaCard`: 기존 `CategoryButton`/`FirstLockActivationCta` 의미를 하나의 상태 카드로 통합해 선택 앱 수, primary CTA, 보조 진입점을 함께 보여준다.
  - `GoalLockProgressCard`: 목표 잠금이 있을 때 Home 진행 상태를 보여주는 #417 표면으로 유지한다.
- `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt`
  - `changeIsKeep(...)`: 선택 앱이 없으면 Keep 시작 대신 앱 선택 안내를 먼저 보여준다.
  - `showFirstLockActivationCta`: 첫 잠금 CTA 노출 조건.
  - `goalLockCard`: 목표 잠금 Home card read model.
- `app/src/test/java/com/uiery/keep/feature/home/HomeStatusCtaReadModelTest.kt`
  - 선택 앱 없음 / 첫 잠금 준비 / 보호 중 / 목표 잠금 동시 노출 read-model 계약을 고정한다.
- `app/src/androidTest/java/com/uiery/keep/feature/home/HomeStatusCtaCardIntegrationTest.kt`
  - `HomeStatusCtaCard`를 실제 Compose/KDS theme 안에서 렌더링해 선택 앱 없음 / 첫 잠금 준비 / 보호 중 상태의 텍스트, primary CTA, secondary CTA 노출/비노출을 고정한다.
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
| 타이머 예약/실행 중 | `HH:MM까지 지키는 중` 또는 `남은 시간 ...` | 상태 카드 자체가 primary status가 된다 | `시간 변경`, `차단 앱 변경` |
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
- #463 code-lane 구현이 merge되어도 release/tag/Play deploy와 14일 관측 전에는 activation 개선을 단정하지 않는다.
- GA4 Admin에서 `customEvent:source`, `customEvent:block_source`, `customEvent:selected_app_count`류 축이 queryable인지 확인하기 전에는 경로별 결론을 낮은 confidence로 둔다.
- 새 이벤트를 추가한다면 privacy-safe enum/bucket만 허용한다. 금지 payload/query 축: 앱 이름, package name, raw selected app list, raw session history, raw timestamp.

## Code-lane handoff

#463 구현 PR은 아래 범위를 한 package로 다루는 편이 안전하다.

1. Home 상태 read model을 명시한다.
   - 꺼짐/켜짐/타이머/목표 잠금/선택 앱 없음 상태를 텍스트 계약으로 분리한다.
   - ViewModel test에서 상태별 primary/secondary CTA 문구 또는 key를 검증한다.
2. UI hierarchy를 KDS token으로 구현한다.
   - raw color 추가를 피하고 `KeepTheme`/KDS component를 우선한다.
   - `CategoryButton`, `FirstLockActivationCta`, `GoalLockProgressCard`의 의미를 유지하거나 교체 시 동등한 접근 경로를 제공한다.
3. Locale parity를 맞춘다.
   - 새 string은 `values`, `values-ko` 및 유지 중인 locale 전체에 추가한다.
   - `:app:lintProdRelease` 또는 equivalent missing-translation 검증을 남긴다.
4. QA evidence를 남긴다.
   - 첫 방문/반복 사용자/선택 앱 없음/타이머/목표 잠금 상태 screenshot 또는 수동 evidence.
   - `:app:testDevDebugUnitTest` 또는 focused Home ViewModel/UI read-model test.

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

- Docs-only PR: `Refs #463`. 이 문서/정적 테스트는 code-lane이 구현할 기준이며 구현 완료가 아니다.
- Implementation PR: acceptance criteria 전체를 만족하고 Home UI/resource/test/locale/QA evidence가 준비된 경우에만 `Closes #463` 사용을 검토한다.
- External boundary: 구현 후에도 release/tag/Play deploy, latest-version adoption, GA4 Admin queryability, 14일/30일 readback은 별도 경계로 기록한다.
