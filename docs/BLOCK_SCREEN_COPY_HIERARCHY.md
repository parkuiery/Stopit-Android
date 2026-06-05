# Block Screen Copy & Action Hierarchy Contract

Issue: #464

## 목적

차단 화면은 사용자가 습관적으로 차단된 앱을 열었을 때 가장 자주 보는 핵심 가치 화면이다. 이 문서는 `BlockScreen`을 처벌/제한 화면이 아니라 **잠깐 멈춤 + 자기 통제 보조** 경험으로 다듬기 위한 copy, action hierarchy, QA 계약을 고정한다.

이 문서는 #464의 source of truth다. 코드 변경은 `BlockScreen.kt`, `BlockViewModel`, `EmergencyUnlockBottomSheetContent`, `strings.xml` locale parity, screenshot/runtime QA를 포함해야 하며 PR body는 구현·검증·release/readback이 모두 끝나기 전까지 `Refs #464`를 사용한다.

## 현재 표면과 구현 상태

- 화면 파일: `app/src/main/java/com/uiery/keep/BlockScreen.kt`
- 주요 strings: `block_screen_title`, `block_screen_message`, `block_screen_close`, `block_screen_first_core_action_feedback`, `emergency_unlock_*`
- 화면 구조:
  - 상단 `TrackedBannerAd(AdPlacement.BlockTop)`
  - 중앙 app icon + title/message + 최초 차단 성공 feedback
  - 하단 `TextButton` emergency unlock + helper reason/status + primary `KeepButton` close
- code-lane 구현 기준:
  - title/message/primary CTA는 `잠깐 멈춤 + 하던 일로 돌아가기` 계열 코칭 톤이어야 한다.
  - emergency unlock은 기존 action label에 더해 helper reason/status string을 함께 노출해 색상 비활성만으로 상태를 설명하지 않는다.
  - `EmergencyUnlockBottomSheetContent`, unlock side effect, analytics event order는 변경하지 않는다.
  - 광고 영역은 본문/CTA보다 우선되어 보이면 신뢰 리스크다.

## 제품 원칙

1. **코칭 톤**: 사용자를 비난하거나 벌주는 표현을 쓰지 않는다.
2. **핵심 행동 우선**: primary CTA는 `닫기`보다 `하던 일로 돌아가기`에 가까운 의미를 준다.
3. **긴급해제는 보조 안전 장치**: 긴급해제는 숨기지 않되 primary CTA보다 낮은 위계로 유지하고, 남은 횟수/비활성 이유를 명확히 보여준다.
4. **첫 가치 피드백과 충돌 금지**: `block_screen_first_core_action_feedback`는 최초 `first_core_action_completed`에서만 성공 피드백으로 노출하고, 반복 차단에서는 일반 코칭 카피를 사용한다.
5. **광고 간섭 제한**: 광고가 있더라도 차단 안내, emergency unlock 상태, primary CTA 인지를 방해하면 안 된다.
6. **개인정보/수치 안전**: 앱 이름은 현재 차단된 앱 표시 목적 이상으로 쓰지 않고, analytics에는 앱 이름/raw session/raw timestamp를 추가하지 않는다.

## Copy hierarchy

| 영역 | 역할 | 권장 톤 | 금지/주의 |
| --- | --- | --- | --- |
| Title | 멈춤 신호 + 자기통제 보조 | `잠깐, 지금은 지키는 중이에요` 같은 짧은 코칭 | `사용 금지`, `실패`, `중독`처럼 처벌/낙인 톤 |
| Message | 어떤 앱이 차단됐는지 설명 | `%s는 현재 차단되어 있어요. 하던 일로 돌아가볼까요?` | 앱 사용자를 비난하거나 광고/긴급해제로 시선을 먼저 보내는 문장 |
| First core feedback | 첫 실제 차단 성공 인지 | `첫 차단이 작동했어요` + 앞으로도 선택 앱을 막는다는 설명 | `first_lock_configured`를 실제 차단 완료처럼 과장 |
| Emergency unlock link | 예외적 안전 action | `긴급 해제 (%1$d/%2$d)` + disabled reason | primary CTA보다 강한 색/크기, 남은 횟수 없이 모호한 링크 |
| Primary CTA | 화면 종료/복귀 | `하던 일로 돌아가기` | 단순 `닫기`만으로 행동 의미가 약한 상태 |

## Action hierarchy

1. Primary: 하던 일로 돌아가기 (`KeepButton`)
2. Secondary/safety: 긴급 해제 (`TextButton` 또는 낮은 위계 action)
3. Context: 현재 차단 앱, 차단 이유/상태, 최초 성공 피드백
4. Non-primary context: banner ad. 광고는 CTA와 emergency unlock status보다 높은 위계로 보이면 QA 실패다.

## Emergency unlock guardrails

- `emergencyUnlockActionUiState(...)`의 enabled/disabled 결과를 copy와 시각 상태가 함께 설명해야 한다.
- disabled 상태는 색상 비활성만으로 끝내지 말고, `오늘의 긴급 해제 횟수를 모두 사용했어요`, `긴급 해제가 꺼져 있어요`, `하루 0회로 설정되어 있어요`처럼 이유가 보이는 string을 유지/보강한다.
- strong/manual refill mode와 충돌하지 않게, `남은 횟수`는 현재 정책의 결과로만 표시한다.
- emergency unlock reason analytics 값은 기존 enum/저장 의미를 유지한다. 표시 카피를 줄여도 payload enum을 임의로 바꾸지 않는다.

## Locale / resource contract

- 주요 user-facing copy는 `app/src/main/res/values*/strings.xml`에 둔다.
- 최소 `values` / `values-ko` parity를 확인하고, 이미 같은 key가 있는 shipped locale에는 누락 없이 추가한다.
- 새 string key를 추가하면 release lint에서 `MissingTranslation`이 나지 않도록 전체 `values-*` surface를 확인한다.
- legacy `Keep` 브랜드가 사용자 노출 string에 다시 들어가면 안 된다. StopIt/스탑잇 기준을 유지한다.

## Analytics / measurement boundary

#464 자체는 새 이벤트를 요구하지 않는다. 우선 기존 `app_block_intercepted`, `first_core_action_completed`, `core_action_completed`, `emergency_unlock_used`, `emergency_unlock_completed`, `ad_banner_*` 계약을 깨지 않는 copy/action hierarchy 개선이다.

새 화면 실험 이벤트를 추가한다면 별도 code-lane 판단으로 제한하고, payload는 privacy-safe enum/bucket만 허용한다.

- 허용 후보: `block_screen_copy_variant`, `emergency_unlock_action_state`, `primary_cta_variant`
- 금지: 앱 이름, package, raw blocked app list, raw session timestamp, raw history, 사용자 입력 원문

## QA baseline

### 자동 검증 후보

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_block_screen_copy_hierarchy_contract -v
./gradlew --console=plain :app:testDevDebugUnitTest --tests '*BlockViewModel*' --tests '*EmergencyUnlock*'
./gradlew --console=plain :app:lintProdRelease
./gradlew --console=plain :app:assembleProdDebug
```

### 수동/screenshot QA evidence template

```md
## Block screen copy/action hierarchy QA evidence
- Issue: #464
- Build / variant:
- Device / Android version / OEM:
- Locale(s): ko / en / changed locales:
- Entry path:
  - manual Keep block / timer block / routine block / goal lock block:
- Commands:
  - `python3 -m unittest scripts.tests.test_block_screen_copy_hierarchy_contract -v`
  - `./gradlew --console=plain :app:lintProdRelease`
  - `./gradlew --console=plain :app:assembleProdDebug`
- Normal blocked state:
  - title/message coaching tone: pass / fail
  - primary CTA means return to previous work: pass / fail
  - banner ad does not outrank CTA/emergency status: pass / fail
- First core action state:
  - first success feedback appears once: pass / fail
  - repeated block does not repeat first-success copy: pass / fail
- Emergency unlock available:
  - remaining count visible: pass / fail
  - action is secondary, not hidden: pass / fail
- Emergency unlock disabled/limit reached:
  - disabled reason understandable: pass / fail
  - color-only state avoided: pass / fail
- Accessibility/TalkBack:
  - blocked app and main action meaning understandable: pass / fail
- Decision: pass / fail / needs follow-up
- Notes:
```

## 구현 handoff

Code-lane이 #464를 이어갈 때 권장 순서:

1. current string/locales inventory: `block_screen_*`, `emergency_unlock_*`, `cd_*`.
2. `BlockScreen` screenshot/Compose preview 또는 screenshot QA 기준으로 title/message/CTA 변경.
3. emergency unlock disabled reason helper text가 필요한지 확인하되, coordinator/side effect 동작은 바꾸지 않는다.
4. `BlockViewModel` analytics order (`app_block_intercepted` → 최초 1회 `first_core_action_completed`) 유지 테스트를 재실행한다.
5. `:app:lintProdRelease`로 locale parity 확인.
6. PR body에는 `Refs #464`를 사용한다. `Closes #464`는 copy/action hierarchy 구현, locale parity, affected tests/build/lint, screenshot/manual QA 또는 release-acceptable evidence가 모두 끝났을 때만 사용한다.

## 남은 외부/후속 경계

- 이 docs-lane 산출물은 구현 완료가 아니다.
- GA4/Play readback은 copy 변경 포함 release/tag/Play deploy 후 14일 이상 지나야 해석한다.
- 광고 placement, emergency unlock policy, first-core-action feedback 자체를 바꾸는 별도 제품 결정은 #464 밖 follow-up으로 분리한다.
