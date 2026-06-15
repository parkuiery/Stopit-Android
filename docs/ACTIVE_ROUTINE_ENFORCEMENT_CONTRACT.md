# 활성 루틴 보호 UX 계약

Issue: #609

이 문서는 활성 루틴 시간대의 잠금 강제성 강화 범위를 제품/QA/운영 관점에서 고정하는 source of truth다. 공개 Play 리뷰에서 확인된 핵심 불만은 "루틴 시간이 되었는데 이미 사용 중인 앱이 즉시 막히지 않는다"와 "루틴을 바로 끄거나 수정해서 쉽게 우회할 수 있다"였다. 따라서 #609는 단순 루틴 UI polish가 아니라 Stopit의 핵심 가치인 **루틴 시간대에는 사용자가 스스로 정한 약속을 앱이 실제로 지켜준다**는 신뢰 계약이다.

## 현재 repo-internal 완료 상태

아래 범위는 `develop`에 반영된 repo-internal baseline으로 본다. 후속 문서/실행 lane은 이 범위를 다시 "구현 전"으로 되돌리지 않는다.

| 범위 | 완료 상태 | 근거 |
| --- | --- | --- |
| 루틴 시작 시 foreground 즉시 차단 / 앱 재평가 | 완료 | PR #807: `KeepAccessibilityServiceIntegrationTest#activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution`, `foregroundAppBecomesBlockedWhenRoutineStartTimeArrives` |
| 실행 중 루틴 상세/수정 진입 차단 | 완료 | `RoutineViewModelActiveRoutineGuardTest`, `RoutineBottomSheetViewModelTest` |
| 실행 중 루틴 OFF/toggle 우회 차단 | 완료 | PR #815: `RoutineListActionPolicyTest`, `RoutineListContentIntegrationTest#runningRoutineSwitchTapSurfacesBlockedActionFeedbackWithoutChangingEnabledState` |
| 실행 중 루틴 삭제 우회 차단 | 완료 | PR #825: 삭제 전 최신 routine state 재확인, `ShowActiveRoutineBlocked` 안내 유지 |
| 안내 copy/locale | 완료 | `routine_active_action_blocked_message` shipped locale parity, 비징벌적 안내 톤 |
| 차단 화면 루틴 사유 안내 | 완료 | `BlockScreenContentIntegrationTest#activeRoutineBlockExplainsRoutineReasonWhileKeepingEmergencyUnlockSecondary`, `block_screen_routine_active_reason` shipped locale parity |
| QA evidence template | 완료 | `docs/QA_RUNTIME_CHECKLIST.md#활성-루틴-보호-ux-qa-baseline` |

## 제품 정책

1. 루틴 시작 시 보호 대상 앱이 이미 foreground이면 추가 window-state event를 기다리지 않고 time-based 재평가로 차단한다.
2. 루틴 활성 시간대에는 사용자가 단순 toggle/off, 상세 수정, 삭제로 잠금을 우회할 수 없어야 한다.
3. 차단/보호 정책은 처벌이 아니라 사용자가 미리 정한 약속을 지켜주는 안내로 표현한다.
4. 허용된 임시 예외 경로는 긴급 해제다. 활성 루틴 보호 UX는 긴급 해제 자체를 막거나 의미를 바꾸지 않는다.
5. stale UI state 때문에 루틴이 이미 활성/변경잠금 상태가 되었는데도 이전 화면 상태로 update/delete/cancel/reschedule이 실행되면 실패로 본다. action 직전에 Room/repository 최신 상태를 다시 확인해야 한다.
6. 루틴 때문에 `BlockActivity`가 열린 경우 차단 화면은 generic 차단 copy만 보여주지 않고, 실행 중인 루틴이 현재 시간을 보호하고 있으며 긴급 해제는 짧은 예외 경로라는 점을 비징벌적으로 설명해야 한다.

## Analytics / 지표 해석

- 핵심 runtime evidence는 기존 `app_block_intercepted(block_source=routine, routine_id=...)` attribution이다.
- 새 GA4 이벤트를 요구하지 않는다. #609는 "루틴 강제성/우회 방지 신뢰" 개선이며, 별도 이벤트 없이 runtime QA와 Play 리뷰/지원 신호로 후행 판단한다.
- 배포 전 live 데이터 0건 또는 리뷰 변화 없음은 제품 효과 없음으로 해석하지 않는다. #609 관련 PR들이 release/tag/Play deploy에 포함되고 14일 이상 지나야 후행 판단을 시작한다.
- 후행 관측은 다음을 함께 본다.
  - 루틴 기반 `app_block_intercepted` users/count
  - 긴급해제 사용률이 비정상적으로 증가하지 않는지
  - Play 리뷰/지원 문의에서 "루틴이 안 막힘", "루틴을 바로 끌 수 있음"류 반복 피드백이 줄어드는지
  - Crashlytics/ANR에서 AccessibilityService/Routine 화면 관련 회귀가 없는지

## 자동 검증 baseline

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.service.RoutineStartReevaluationPolicyTest' \
  --tests 'com.uiery.keep.service.KeepAccessibilityServiceBlockDecisionTest' \
  --tests 'com.uiery.keep.feature.routine.RoutineViewModelActiveRoutineGuardTest' \
  --tests 'com.uiery.keep.feature.routine.RoutineBottomSheetViewModelTest' \
  --tests 'com.uiery.keep.feature.routine.RoutineListActionPolicyTest'
./gradlew --console=plain :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#foregroundAppBecomesBlockedWhenRoutineStartTimeArrives,com.uiery.keep.feature.routine.component.RoutineListContentIntegrationTest#runningRoutineSwitchTapSurfacesBlockedActionFeedbackWithoutChangingEnabledState,com.uiery.keep.BlockScreenContentIntegrationTest#activeRoutineBlockExplainsRoutineReasonWhileKeepingEmergencyUnlockSecondary
python3 -m unittest scripts.tests.test_active_routine_enforcement_contract -v
./gradlew --console=plain :app:lintProdRelease
```

## 수동 / release-candidate QA evidence

자동 테스트가 green이어도 #609를 완전히 닫으려면 release-candidate 또는 실제 기기에서 아래 증거를 남긴다.

- 루틴 시작 전 보호 대상 앱을 foreground에 둔 상태에서 시작 시간이 도래한다.
- `BlockActivity`가 즉시 뜨고 `block_source=routine`, `routine_id` attribution이 유지된다.
- 실행 중 루틴을 수정/삭제/OFF 하려고 하면 실제 상태가 바뀌지 않고 안내 snackbar가 보인다.
- 긴급 해제는 임시 예외로 계속 접근 가능하다.
- 루틴 때문에 열린 차단 화면은 `block_screen_routine_active_reason` copy를 보여주며 긴급 해제 CTA를 보조 action으로 유지한다.
- copy tone은 사용자를 비난하거나 겁주는 표현이 아니라 "지금은 실행 중인 루틴이라 변경할 수 없다"는 중립 안내다.
- light/dark, 주요 locale, TalkBack에서 안내가 이해 가능하다.

증거 템플릿은 `docs/QA_RUNTIME_CHECKLIST.md#활성-루틴-보호-ux-qa-baseline`을 사용한다.

## 완료/closure 기준

#609는 다음 조건이 모두 충족될 때만 `Closes #609`를 사용한다.

- repo-internal baseline(above)이 `develop`에 있고, 관련 static contract가 green이다.
- #609 관련 PR들이 release/tag/Play deploy에 포함됐다.
- release-candidate 또는 실제 기기에서 foreground 루틴 차단, 수정/삭제/OFF 우회 차단, 안내 copy, 긴급해제 예외가 evidence로 기록됐다.
- 배포 후 14일 관측에서 Play 리뷰/지원/Crashlytics/긴급해제 guardrail이 악화되지 않았다.

그 전까지는 `Refs #609`를 사용하고 남은 경계를 명시한다.
