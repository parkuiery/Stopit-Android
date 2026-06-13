# Emergency Unlock Flow Copy & Step Contract

Issue: #467

## 목적

긴급 해제 플로우는 StopIt의 안전장치이면서도 사용자가 습관적 해제를 한 번 멈춰 생각하게 만드는 자기통제 장치다. 이 문서는 `EmergencyUnlockBottomSheetContent`의 reason → app selection → duration → countdown 흐름을 **짧게 스캔 가능한 선택 + 신중히 쓰는 예외 기능** 경험으로 다듬기 위한 copy, 단계, analytics, locale, QA 계약을 고정한다.

이 문서는 #467의 source of truth다. PR #488은 문서/QA 계약을 고정했고, PR #517(`572eb559`)은 `EmergencyUnlockBottomSheetContent.kt`, `EmergencyUnlockBottomSheetState`, shipped `strings.xml` locale parity, 관련 JVM/lint/build 검증으로 reason/app/duration 단계 helper·validation copy 구현을 `develop`에 반영했다. PR #575(`1a7c677`)는 reason-required ON/OFF 실제 Compose flow를 `EmergencyUnlockBottomSheetContentIntegrationTest`로 고정해 `기타` custom reason validation, app selection, duration chip, countdown 완료 callback payload를 emulator baseline으로 추가했다. PR #593(`79fdee8`)는 countdown TalkBack baseline을 추가해 waiting copy, 남은 초, cancel affordance가 하나의 접근성 상태로 읽히는지 focused Compose instrumentation으로 고정했다. PR #604(`3e97f548`)는 selected reason reflection helper와 shipped locale parity를 실제 UI/resource/test baseline으로 보강하되 기존 reason enum payload key는 유지하도록 고정했다. PR #675(`d2fab054`)는 reason/app/duration 단계의 `step purpose` copy를 실제 UI/resource/test baseline으로 추가하고 countdown TalkBack baseline은 유지하도록 고정했다. 이제 이 문서는 code-lane 구현 전 handoff가 아니라 **develop에 반영된 구현+자동 UI QA baseline+countdown TalkBack baseline+selected reason reflection helper+step purpose copy 상태 + 남은 release/device/readback 경계**를 고정한다. `origin/main`/SemVer tag/Play deploy, 실제 기기/screenshot/TalkBack spot-check, 14일 readback이 끝나기 전까지 follow-up PR body는 `Refs #467`를 사용한다.

## 현재 구현 표면

- 화면 파일: `app/src/main/java/com/uiery/keep/feature/lock/component/EmergencyUnlockBottomSheetContent.kt`
- 상태 파일: `EmergencyUnlockBottomSheetState` / `EmergencyUnlockBottomSheetStep`
- 주요 strings: `emergency_unlock_reason_*`, `emergency_unlock_select_apps`, `emergency_unlock_select_duration`, `emergency_unlock_next`, `emergency_unlock_request`, `emergency_unlock_waiting`, `emergency_unlock_cancel`
- 현재 reason enum/payload key:
  - `work`
  - `contact`
  - `info`
  - `habit`
  - `boredom`
  - `other`
- PR #517로 반영된 구조:
  - reason step: 짧은 reason label + 상단 helper + 선택 후 의도별 reflection helper + `기타` 입력 validation helper + disabled `다음` 설명.
  - selected reason reflection helper는 표시 카피만 보강하며 기존 `work` / `contact` / `info` / `habit` / `boredom` / `other` payload key를 바꾸지 않는다.
  - app step: 필요한 앱만 선택한다는 helper + zero-selection validation helper + disabled `다음` 설명.
  - duration step: 필요한 만큼만 선택한다는 helper + `해제 요청`.
  - countdown step: 짧은 reconsideration window로 읽히는 countdown copy + 취소.
  - `reasonStepEnabled=false`: app selection부터 시작하되 예외 기능/필요 앱만 선택 원칙을 helper copy로 유지.
- PR #675(`d2fab054`)로 반영된 step purpose 구현 범위:
  - step indicator 아래에 `step purpose` copy를 노출해 각 단계가 왜 필요한지 한 줄로 설명한다.
  - reason step은 `이번 예외가 왜 필요한지`, app step은 `정말 필요한 앱만`, duration step은 `가능한 짧게`라는 목적을 명시한다.
  - countdown step에는 기존 TalkBack countdown baseline을 유지하고 별도 step purpose copy를 추가하지 않는다.
- PR #575로 반영된 자동 UI QA baseline:
  - `EmergencyUnlockBottomSheetContentIntegrationTest`가 reason-required ON 실제 Compose flow에서 `기타` reason 선택, custom reason validation, app selection, duration chip 선택, countdown 완료 후 `reason=other` payload와 selected app/duration 전달을 검증한다.
  - 같은 test class가 reason-required OFF flow를 유지해 reason step 없이 app selection → duration → countdown으로 이어지는 경로를 검증한다.
  - PR #593(`79fdee8`) 이후 countdown step은 TalkBack content description에서 waiting copy, 남은 초, cancel affordance를 함께 노출해야 하며, 이를 `countdownStepExposesTalkBackDescriptionWithRemainingSecondsAndCancelHint`가 검증한다.
  - PR #604(`3e97f548`) 이후 selected reason reflection helper는 선택한 reason의 display copy를 보강하지만 `emergency_unlock_completed.reason` payload는 기존 enum key(`work`, `contact`, `info`, `habit`, `boredom`, `other`)로 유지한다.
  - 이 baseline은 emulator/Compose 자동 증거이며, 실제 기기 screenshot·TalkBack spot-check를 대체하지 않는다.
- 남은 UX 리스크:
  - 실제 기기/screenshot QA에서 helper/validation copy가 광고 영역·CTA·bottom sheet height 안에서 읽히는지 확인해야 한다.
  - TalkBack/접근성에서 reason/app/duration helper와 disabled reason이 색상 의존 없이 전달되는지 확인해야 한다.
  - `origin/main`/SemVer tag/Play deploy 전까지 live 지표 0건 또는 변화 없음은 post-#517/#575/#593/#604 성과로 해석하면 안 된다.

## 제품 원칙

1. **필요하면 빠르게, 습관이면 한 번 멈춤**: 긴급 연락/업무처럼 필요한 이유는 빠르게 찾을 수 있어야 하고, 습관/지루함 이유는 비난 없이 자기인식을 돕는다.
2. **짧은 label + 보조 설명**: 첫 스캔 surface는 짧은 label을 우선하고, 긴 설명은 helper/caption 또는 접근성 label로 분리한다.
3. **기존 enum 의미 유지**: 표시 카피를 줄여도 `work`, `contact`, `info`, `habit`, `boredom`, `other` 저장/analytics 의미를 바꾸지 않는다.
4. **disabled reason은 보이는 copy**: reason/apps 미선택 때문에 `다음`이 비활성일 때는 색상만으로 전달하지 않고 helper text나 상태 copy를 제공한다.
5. **reason required off도 자연스럽게**: 설정에서 사유 선택을 끈 사용자는 app selection부터 시작하되, 상단 안내 문구로 “정말 필요한 앱만 잠깐 해제”하는 맥락을 제공한다.
6. **Countdown은 안전한 유예**: countdown은 벌이 아니라 다시 생각할 짧은 유예로 설명한다. 긴급해제 자체를 숨기거나 과도하게 어렵게 만들지 않는다.
7. **개인정보/수치 안전**: reason free text는 로컬 요청 문맥 이상의 분석 payload로 확장하지 않는다. analytics에는 enum/bucket만 남기고 custom reason 원문, 앱 이름, package list, raw timestamp/history를 추가하지 않는다.

## Step copy contract

| 단계 | 역할 | 권장 copy 방향 | 금지/주의 |
| --- | --- | --- | --- |
| Reason | 해제 의도 self-check | 제목: `정말 필요한 경우에만 잠깐 열 수 있어요` / step purpose: `이번 예외가 왜 필요한지 먼저 확인` / helper: `가장 가까운 이유를 골라주세요` | `왜 실패했나요?`, `중독`, `참지 못함` 같은 낙인 톤 |
| Reason option | 빠른 스캔 | `업무/공부`, `긴급 연락`, `정보 확인`, `습관`, `지루함/스트레스`, `기타` 같은 short label + 선택 후 짧은 reflection helper | 기존 enum key를 표시 카피와 함께 변경 |
| Other reason | 자유 입력 | optional short note. analytics 원문 전송 금지 명시 | 원문을 GA4 dimension으로 보낼 수 있다는 오해 |
| Apps | 범위 제한 | step purpose: `정말 필요한 앱만 예외로 열기` / `잠깐 열 앱만 선택하세요` / 선택 0개 helper | 모든 차단 앱을 기본 전체 해제로 오해시키는 copy |
| Duration | 시간 제한 | step purpose: `해제 시간은 가능한 짧게` / `필요한 만큼만 선택하세요` / duration option은 분 단위 명확 | 긴 시간을 권장하는 tone |
| Countdown | 마지막 확인 | `잠시 후 해제돼요. 필요 없으면 취소할 수 있어요.` | 처벌/기다리게 만들기/광고 노출 유도처럼 보이는 copy |

## Reason taxonomy contract

기존 payload key는 유지한다. code-lane은 resource label만 줄일 수 있고 enum meaning을 바꾸면 안 된다.

| payload key | 짧은 label 후보 | 의미 | analytics 해석 |
| --- | --- | --- | --- |
| `work` | 업무/공부 | 생산적 목적을 위해 차단 앱을 잠깐 열어야 함 | legitimate interruption |
| `contact` | 긴급 연락 | 연락/메시지 확인이 필요함 | urgent communication |
| `info` | 정보 확인 | 짧은 정보 확인 목적 | quick lookup |
| `habit` | 습관 | 목적 없이 열려는 자기인식 | impulse/self-awareness |
| `boredom` | 지루함/스트레스 | boredom/stress relief 목적 | emotional trigger |
| `other` | 기타 | 위에 없는 이유 | custom reason은 원문 분석 금지 |

## Reason required off contract

`reasonStepEnabled=false`일 때도 플로우가 갑자기 “해제 앱 선택 도구”처럼만 보이면 안 된다.

- 첫 화면은 app selection이지만, 상단 helper copy에 아래 의미가 있어야 한다.
  - 긴급해제는 제한된 예외 기능이다.
  - 필요한 앱만 선택한다.
  - duration/countdown에서 다시 취소할 수 있다.
- 이 경우 `emergency_unlock_completed.reason`은 기존 코드의 default reason 계약을 따른다. reason required off를 새 reason enum으로 임의 확장하지 않는다.
- metrics 해석에서는 reason distribution이 reason-required-on 사용자에게만 대표성이 있다는 점을 기록한다.

## Disabled / validation copy contract

색상 비활성만으로 상태를 전달하지 않는다. 구현 후보:

- reason step: `이유를 하나 선택하면 다음으로 갈 수 있어요` / `기타 이유를 입력해주세요`
- apps step: `잠깐 열 앱을 하나 이상 선택해주세요`
- duration step: duration option이 없거나 선택 불가하면 `설정에서 긴급 해제 시간을 확인해주세요`
- TalkBack/accessibility: disabled button label 또는 nearby helper가 같은 의미를 읽을 수 있어야 한다.

## Analytics / measurement boundary

#467의 기본 범위는 copy/step simplification이며 새 이벤트를 요구하지 않는다. 기존 이벤트 의미를 유지한다. 단계별 이탈·검증 실패를 실제로 계측하는 후속 계약은 `docs/EMERGENCY_UNLOCK_STEP_ANALYTICS.md`(#779)를 source of truth로 본다.

- `emergency_unlock_used(source, unlock_count_remaining?)`: 긴급해제 플로우 진입
- `emergency_unlock_completed(reason, duration_minutes, remaining_unlocks)`: countdown 완료 후 실제 임시 해제 완료

새 이벤트를 추가한다면 별도 code-lane 판단으로 제한하고 privacy-safe enum만 허용한다. #779 계약의 canonical event는 아래 후보를 구체화한 `emergency_unlock_step_viewed`, `emergency_unlock_validation_blocked`, `emergency_unlock_cancelled`이며, PR #783으로 Android wiring은 `develop`에 반영됐다. GA4 Admin 등록·release/tag/Play deploy·readback 전에는 live 0건을 UX 병목 부재로 해석하지 않는다.

- 허용 후보: `emergency_unlock_step_viewed(step_name)`, `emergency_unlock_validation_blocked(step_name, validation_reason)`, `emergency_unlock_cancelled(step_name)`
- 금지: custom reason 원문, 앱 이름, package, raw selected app list, raw duration beyond configured option, raw timestamp/history

운영 해석:

- reason label copy가 바뀌어도 payload key가 유지되면 기존 reason trend는 같은 축으로 이어서 볼 수 있다.
- reason required off 사용자가 늘면 reason distribution confidence는 낮아진다. 분모를 `reason_step_enabled=true` 노출/완료 사용자와 전체 emergency unlock 완료 사용자로 분리한다.
- release/tag/Play deploy 후 14일 이상 지나기 전에는 copy 변경 성과를 live emergency unlock rate 변화로 단정하지 않는다.

## Locale / resource contract

- 주요 user-facing copy는 `app/src/main/res/values*/strings.xml`에 둔다.
- 최소 `values` / `values-ko` parity를 확인하고, 이미 같은 emergency unlock key가 있는 shipped locale에는 누락 없이 추가한다.
- 새 string key를 추가하면 `:app:lintProdRelease` 또는 전체 `values-*` surface inspection으로 `MissingTranslation`을 확인한다.
- 기존 StopIt/스탑잇 브랜드 기준을 유지하고 legacy `Keep` 사용자 노출 string을 추가하지 않는다.

## QA baseline

### 자동 검증 후보

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_emergency_unlock_flow_copy_contract -v
./gradlew --console=plain :app:testDevDebugUnitTest --tests '*EmergencyUnlock*'
./gradlew --console=plain :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContentIntegrationTest
./gradlew --console=plain :app:lintProdRelease
./gradlew --console=plain :app:assembleProdDebug
```

후속 QA PR부터 `EmergencyUnlockBottomSheetContentIntegrationTest`는 reason-required ON/OFF 양쪽의 실제 Compose flow를 고정한다. PR #575(`1a7c677`) 기준 이 테스트는 `기타` reason custom input validation, app selection disabled helper, duration chip selection, countdown 완료 후 `emergency_unlock_completed.reason` enum payload(`other` 또는 reason-not-required sentinel)와 duration/app set 전달을 emulator에서 검증한다. PR #593(`79fdee8`) 기준 countdown TalkBack baseline은 waiting copy, remaining seconds, cancel affordance가 하나의 content description으로 함께 노출되는지 검증한다. PR #604(`3e97f548`) 기준 selected reason reflection helper는 화면 copy/resource baseline에 포함되지만 analytics payload는 display label/custom text가 아니라 기존 enum key로 유지한다. 따라서 다음 docs/QA follow-up은 “자동 UI baseline 부재”가 아니라 실제 기기 screenshot·TalkBack spot-check, release inclusion, 14일 readback 경계를 대상으로 삼는다.

### Runtime / screenshot QA evidence template

```md
## Emergency unlock flow copy QA evidence
- Issue: #467
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / changed locales:
- Entry path:
  - BlockScreen emergency unlock action:
- Settings state:
  - reason required: on / off
  - daily limit / remaining:
  - duration options:
- Commands:
  - `python3 -m unittest scripts.tests.test_emergency_unlock_flow_copy_contract -v`
  - `./gradlew --console=plain :app:lintProdRelease`
  - `./gradlew --console=plain :app:assembleProdDebug`
- Reason required ON:
  - short reason labels scan quickly: pass / fail
  - helper copy frames emergency unlock as limited exception: pass / fail
  - disabled Next explains missing reason/custom reason: pass / fail
  - selected reason maps to existing enum key: pass / fail
- Reason required OFF:
  - app selection starts naturally without reason step: pass / fail
  - helper copy still limits scope to needed apps only: pass / fail
- App selection:
  - selected apps are explicit: pass / fail
  - disabled Next explains zero selected app: pass / fail
- Duration:
  - duration options are clear and bounded: pass / fail
  - request CTA does not imply permanent unlock: pass / fail
- Countdown:
  - countdown copy feels like a short reconsideration window, not punishment: pass / fail
  - cancel path remains visible: pass / fail
- Analytics/privacy:
  - `emergency_unlock_completed.reason` keeps enum key, not display label/custom text: pass / fail
  - no app name/package/custom reason/raw history added to analytics: pass / fail
- Accessibility/TalkBack:
  - reason/app/duration selection and disabled helper are understandable: pass / fail
- Decision: pass / fail / needs follow-up
- Notes:
```

## 구현 상태 및 남은 handoff

PR #517(`572eb559`)로 아래 repo-internal 구현 항목은 `develop`에 반영됐다.

- `EmergencyUnlockBottomSheetState`에 단계 helper/validation helper resource 정책 추가.
- `EmergencyUnlockBottomSheetContent.kt`에서 reason/app/duration helper와 disabled-next validation helper 노출.
- reason required ON/OFF, app selection validation을 `EmergencyUnlockBottomSheetStateTest`로 고정.
- `values`, `values-ko`, shipped `values-*` locale string parity 유지.
- 검증: focused JVM state test, 전체 `:app:testDevDebugUnitTest`, `:app:lintProdRelease`, `:app:assembleProdDebug`, `python3 -m unittest scripts.tests.test_emergency_unlock_flow_copy_contract -v`, `git diff --check`.

PR #575(`1a7c677`)로 아래 repo-internal QA baseline도 `develop`에 반영됐다.

- `EmergencyUnlockBottomSheetContentIntegrationTest`가 reason-required ON/OFF 실제 Compose flow를 자동화했다.
- reason-required ON에서 `기타` custom reason validation, app selection, duration chip, countdown 완료 callback payload(`reason=other`, duration, selected app set)를 검증한다.
- reason-required OFF에서 reason step 없이 app selection부터 시작해 duration/countdown/callback으로 이어지는 경로를 유지한다.
- 검증: focused `EmergencyUnlockBottomSheetContentIntegrationTest` 3개, focused state JVM test, androidTest compile, `:app:assembleProdDebug`, 문서 계약 테스트, `git diff --check`.

PR #593(`79fdee8`)로 countdown TalkBack baseline도 `develop`에 반영됐다.

- `EmergencyUnlockBottomSheetContentIntegrationTest#countdownStepExposesTalkBackDescriptionWithRemainingSecondsAndCancelHint`가 countdown content description을 focused Compose instrumentation으로 검증한다.
- TalkBack은 countdown waiting copy, 남은 초, cancel affordance를 한 상태로 읽어야 하며, 화면에 보이는 countdown copy와 취소 affordance를 접근성 label이 따로 누락하지 않아야 한다.
- 검증: focused countdown TalkBack instrumentation, 전체 `EmergencyUnlockBottomSheetContentIntegrationTest`, `:app:lintProdRelease`, 문서 계약 테스트, `git diff --check`.

PR #604(`3e97f548`)로 selected reason reflection helper baseline도 `develop`에 반영됐다.

- selected reason별 reflection helper가 reason step 선택 후 intentional use를 보강한다.
- helper copy/resource/locale parity는 UI 표시 계약이며, `emergency_unlock_completed.reason` payload key는 기존 enum key를 유지한다.
- 검증: focused Compose UI baseline, locale/resource parity, 문서 계약 테스트, `git diff --check`.

후속 code/QA/release가 #467을 이어갈 때는 다음 순서를 따른다.

1. 실제 기기/screenshot QA에서 reason required ON/OFF, app selection, duration, countdown 상태를 촬영/기록한다.
2. TalkBack/접근성에서 helper/disabled reason이 읽히는지 확인한다.
3. release PR/tag/Play deploy에 #517/#575/#593/#604 merge commit이 포함됐는지 확인한 뒤 14일 readback 창을 시작한다.
4. analytics 해석에서는 `emergency_unlock_completed.reason`이 enum key를 유지하고 custom reason 원문을 payload/query 축으로 확장하지 않는지 계속 확인한다.
5. PR body에는 `Refs #467`를 사용한다. `Closes #467`는 실제 기기/screenshot QA, release/tag/Play deploy 포함 여부, 14일 readback 또는 대표님이 인정한 closure boundary가 확인됐을 때만 사용한다.

## 남은 외부/후속 경계

- docs-lane 계약, PR #517 code-lane UI/resource/test 변경, PR #575 Compose UI QA baseline, PR #593 countdown TalkBack baseline, PR #604 selected reason reflection helper baseline, PR #675 step purpose copy baseline은 `develop`에 반영됐다.
- #467 완료에는 실제 기기/screenshot QA에서 bottom sheet visual hierarchy, countdown wording, TalkBack/disabled helper 전달을 확인해야 한다. 자동 countdown TalkBack baseline은 접근성 contract를 고정하지만 실제 기기/screenshot/TalkBack evidence를 대체하지 않는다.
- GA4/Play readback은 copy 변경과 UI/TalkBack QA baseline 포함 release/tag/Play deploy 후 14일 이상 지나야 해석한다.
- emergency unlock policy 자체(일일 횟수, strong/manual refill mode, duration option policy)를 바꾸는 결정은 #467 밖 follow-up으로 분리한다.
