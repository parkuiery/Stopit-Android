# Manual/Timer Lock Deadline Contract

이 문서는 수동/타이머 잠금의 만료 기준과 QA 증적을 고정한다. 루틴 알람의 `TIME_SET` / `TIMEZONE_CHANGED` 복구 계약과 별개로, 홈 타이머로 저장되는 `LOCK_TIME`은 사용자가 예약한 **실제 남은 잠금 시간**을 보존해야 한다.

## 저장 계약

- 신규 수동/타이머 잠금 deadline은 `Instant` ISO-8601 문자열(`2026-06-02T10:00:00Z` 형태)로 저장한다.
- 저장 key는 기존 호환성을 위해 `PreferencesKey.LOCK_TIME` / `lock_time`을 유지한다.
- 기존 설치에 남아 있을 수 있는 timezone-less `LocalDateTime` 문자열(`2026-06-02T19:00:00`)은 현재 device timezone 기준으로 fallback 해석한다.
- Splash, Menu, AccessibilityService 차단 판단, Lock 화면 route는 모두 `ManualLockTimePolicy`를 통해 같은 기준으로 해석한다.
- Home timer 예약 후 bottom sheet hide / navigation 사이에 시간이 지나거나 화면 state가 바뀌어도 Lock 화면 route는 `lockTime()`이 `LOCK_TIME`에 저장한 동일 encoded deadline을 재사용한다. `moveToLock()`에서 target을 다시 계산해 다른 만료 시각을 만들면 안 된다.

## 자동 회귀 증적

```bash
cd <repo-root>
./gradlew --console=plain :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.datastore.ManualLockTimePolicyTest' \
  --tests 'com.uiery.keep.service.KeepAccessibilityServiceBlockDecisionTest' \
  --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest'
```

검증 범위:

- `Instant` deadline은 timezone이 바뀌어도 같은 실제 시각까지 active로 남는다.
- legacy `LocalDateTime` deadline은 기존 저장값 호환을 위해 현재 timezone 기준으로 fallback 된다.
- AccessibilityService의 foreground 차단 판단은 신규 `Instant` deadline에서도 `TIMED_LOCK` source를 유지한다.
- 홈 타이머 예약은 `LOCK_TIME`에 active deadline을 저장한다.
- 홈 타이머 예약 뒤 navigation 전 timer state가 바뀌어도 Lock route deadline은 저장된 `LOCK_TIME`과 같은 문자열을 사용한다.

## 수동 QA 시나리오

기기/에뮬레이터 시간이 필요한 release QA에서는 아래를 기록한다.

1. 홈에서 차단 앱을 하나 이상 선택한다.
2. 현재 시각 기준 10~15분 뒤 타이머 잠금을 예약한다.
3. 잠금 화면 진입 또는 대상 앱 차단을 확인한다.
4. 가능하면 예약 직후 bottom sheet animation / navigation 구간에서 화면 전환 지연이 있어도 Lock 화면 countdown의 만료 시각이 예약 시각과 동일한지 확인한다.
5. Android Settings에서 timezone을 다른 지역으로 변경한다.
6. 즉시 Splash/Menu/AccessibilityService 경로에서 잠금이 풀리거나 과도하게 연장되지 않는지 확인한다.
7. 원래 deadline의 실제 시각이 지나면 홈으로 복귀하고 `lock_history`가 완료 session으로 기록되는지 확인한다.

기록 템플릿:

```md
- 앱 버전 / commit:
- 기기 / API level:
- 최초 timezone:
- 변경 timezone:
- 예약 deadline(표시값):
- 변경 직후 상태: 유지 / 조기해제 / 과다연장
- deadline 경과 후 상태:
- 비고:
```

## 남은 외부 경계

이 PR의 JVM 증적은 저장/해석/차단 결정 contract를 고정한다. 실제 Settings timezone 변경과 AccessibilityService foreground re-evaluation은 기기/에뮬레이터 기반 release QA에서 위 수동 시나리오로 추가 증적을 남긴다.
