# Stopit Runtime QA Checklist

이 문서는 리시버/서비스 계층의 Android 런타임 검증을 반복 가능하게 만들기 위한 수동 QA 기준이다.

범위:
- `BootReceiver`
- `RoutineAlarmReceiver`
- `KeepAccessibilityService`
- 긴급해제 만료/차단 복귀
- release 전 device/emulator 검증 순서

백업/복원 정책 자체는 `docs/BACKUP_RESTORE_POLICY.md`를 source of truth로 본다. 이 문서는 복원 이후에도 receiver/service/runtime 상태가 안전하게 동작하는지 확인하는 실행 체크리스트다.

비범위:
- Room migration 세부 검증
- Play Console 수동 프로모션 절차
- 대규모 instrumented test 구현

> 현재 저장소의 `androidTest` 자동화는 제한적이다. 이 체크리스트는 `connectedAndroidTest`를 대체하는 것이 아니라, 자동화 공백이 남아 있는 동안 release 전에 반드시 반복해야 하는 최소 기준을 정의한다.

## 1. 사전 준비

### 로컬 필수 조건

- Android Studio 또는 Android SDK/ADB 사용 가능
- `local.properties`가 현재 worktree에 존재
- 필요 flavor의 `google-services.json`이 현재 worktree에 복원되어 있음
- 테스트 기기 또는 에뮬레이터 1대 이상 연결

### 권장 사전 명령

```bash
cd <repo-root>
./gradlew -q help --task :app:testDevDebugUnitTest
./gradlew -q help --task :app:connectedDevDebugAndroidTest
```

### 자동화 기본선

작은 코드 변경이 함께 있는 PR이라면 최소한 아래 중 하나를 같이 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest
./gradlew :app:connectedDevDebugAndroidTest
```

- `:app:testDevDebugUnitTest`: 빠른 JVM 회귀 확인
- `:app:connectedDevDebugAndroidTest`: device/emulator 기반 Android 통합 검증
- 로컬 prerequisite 부족으로 instrumentation을 못 돌리면, 막힌 이유를 PR 본문에 명시하고 아래 수동 QA evidence를 남긴다.

### receiver/service QA용 권장 focused JVM baseline

issue #27 계열처럼 receiver/service runtime 리스크를 다루지만 `connectedDevDebugAndroidTest`까지 즉시 돌리기 어려운 PR이라면, 최소한 아래 focused JVM baseline은 함께 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.receiver.RoutineReceiverPolicyTest" \
  --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest"
```

- `RoutineReceiverPolicyTest`: 저장된 루틴 JSON decode, enabled routine 재예약 선택 로직의 회귀를 빠르게 잡는다.
- `EmergencyUnlockPolicyTest`: 긴급해제 만료/허용 판단의 순수 로직 회귀를 빠르게 잡는다.
- 이 baseline은 Android runtime 자체를 대체하지 않는다. 아래 시나리오 evidence 또는 `:app:connectedDevDebugAndroidTest`와 함께 해석한다.

### 공통 evidence 수집 팁

가능하면 각 시나리오 전후로 아래를 같이 남긴다.

```bash
adb shell dumpsys alarm | grep com.uiery.keep
adb logcat -d | grep -E "RoutineAlarmReceiver|BootReceiver|KeepAccessibilityService|EmergencyUnlock"
```

- `dumpsys alarm`: receiver 이후 다음 알람/루틴 재예약 여부를 남길 때 유용하다.
- `logcat`: 런타임 크래시/경고를 함께 남길 때 유용하다.
- 로그 태그나 출력은 빌드에 따라 충분하지 않을 수 있으므로, 스크린샷/시각/루틴 이름 같은 사용자 관찰 evidence를 같이 보관한다.

## 2. BootReceiver 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/receiver/BootReceiver.kt`
- `app/src/main/AndroidManifest.xml`

### 목적

부팅 후 저장된 루틴이 다시 스케줄링되어야 한다.

### 시나리오

1. 루틴이 1개 이상 활성화된 상태를 만든다.
2. 앱을 완전히 종료한다.
3. 기기를 재부팅하거나, 에뮬레이터를 cold boot한다.
4. 부팅 후 앱을 열지 않은 상태에서도 다음 루틴 알림/스케줄이 유지되는지 확인한다.

> `BOOT_COMPLETED`는 protected broadcast라서 `adb shell am broadcast ...`만으로 안정적으로 재현되지 않을 수 있다. BootReceiver 검증은 실제 reboot/cold boot를 기준으로 남긴다.

### 확인 포인트

- [ ] `BOOT_COMPLETED` 이후 앱 크래시가 없다.
- [ ] 저장된 루틴이 사라지지 않는다.
- [ ] 다음 루틴 시각에 맞는 알림/동작이 다시 예약된다.
- [ ] 재부팅 직후 열어본 홈/루틴 화면에서 루틴 상태가 비정상으로 초기화되지 않는다.

### 실패 시 남길 evidence

- 기기/에뮬레이터 정보
- 재부팅 전후 루틴 이름/시간
- 재부팅 전후 `adb shell dumpsys alarm | grep com.uiery.keep` 차이
- logcat 핵심 라인
- 실제 누락된 알림 또는 스케줄 증상

## 3. RoutineAlarmReceiver 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/receiver/RoutineAlarmReceiver.kt`

### 목적

루틴 시작 시 알림을 띄우고, 활성 루틴이면 다음 주차로 다시 예약해야 한다.

### 시나리오

1. 가까운 미래 시각에 반복 루틴을 만든다.
2. 대상 앱이 선택되어 있는지 확인한다.
3. 알림 시각까지 대기하거나 테스트 시간을 앞당겨 수신을 유도한다.
4. 알림 수신 직후 루틴이 다음 주차 기준으로 다시 예약되는지 확인한다.

권장 준비:

```bash
adb shell dumpsys alarm | grep com.uiery.keep
```

수신 전후의 예약 상태를 비교해 다음 회차가 실제로 다시 등록되었는지 남긴다.

### 확인 포인트

- [ ] 루틴 시작 알림이 정확한 루틴 이름으로 노출된다.
- [ ] 루틴이 enabled 상태면 다음 회차가 다시 예약된다.
- [ ] 루틴이 disabled 상태면 재예약되지 않는다.
- [ ] receiver 실행 후 중복 알림이 연속으로 뜨지 않는다.

### 실패 시 남길 evidence

- 루틴 ID/이름
- enabled 여부
- 기대한 알림 시각 vs 실제 시각
- 수신 전후 `adb shell dumpsys alarm | grep com.uiery.keep` 출력 차이
- 재예약 여부 스크린샷 또는 로그

## 4. KeepAccessibilityService 차단 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt`

### 목적

접근성 서비스가 저장된 잠금 상태와 루틴 상태를 반영해 실제 차단을 수행해야 한다.

### 시나리오 A — 수동 잠금

1. 접근성 권한을 켠다.
2. 차단 대상 앱을 1개 이상 선택한다.
3. 수동 잠금을 활성화한다.
4. 대상 앱을 연다.

확인:
- [ ] `BlockActivity`가 즉시 표시된다.
- [ ] 비대상 앱에서는 차단이 발생하지 않는다.
- [ ] 같은 앱 재진입 시 과도한 중복 차단/깜빡임이 없다.

### 시나리오 B — 시간 잠금

1. 가까운 미래까지 유지되는 timed lock을 설정한다.
2. 잠금 중 대상 앱을 연다.
3. 잠금 종료 후 같은 앱을 다시 연다.

확인:
- [ ] 잠금 시간 내에는 차단된다.
- [ ] 잠금 만료 후에는 정상 진입된다.
- [ ] 만료 직전/직후에 차단 상태가 뒤집히는 이상 동작이 없다.

### 시나리오 C — 루틴 차단

1. 현재 요일/시간에 활성화되도록 루틴을 만든다.
2. 대상 앱을 연다.

확인:
- [ ] 루틴 활성 구간에서만 차단된다.
- [ ] 루틴 비활성 구간에서는 차단되지 않는다.

## 5. 긴급해제 만료 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt`
- `app/src/main/java/com/uiery/keep/service/EmergencyUnlockState.kt`

### 목적

긴급해제가 활성 앱에는 일시적으로 통과를 허용하되, 만료 후에는 차단이 복구되어야 한다.

### 시나리오

1. 차단 중인 앱에서 긴급해제를 실행한다.
2. 긴급해제 유효 시간 동안 대상 앱을 사용한다.
3. 만료 시각이 지난 뒤 같은 앱을 다시 전면으로 가져온다.

### 확인 포인트

- [ ] 긴급해제 유효 시간 동안 대상 앱이 차단되지 않는다.
- [ ] 만료 후 다시 앱 전면 진입 시 차단이 복구된다.
- [ ] 만료 후 데이터가 남아 차단이 계속 우회되지 않는다.
- [ ] 긴급해제와 무관한 다른 대상 앱은 계속 차단된다.

## 6. Release 전 최소 QA 게이트

release PR 또는 internal 배포 전에는 아래를 모두 체크한다.

- [ ] `Branch Hygiene`
- [ ] `Version Guard`
- [ ] `Android CI`
- [ ] `Android Release Build`
- [ ] `:app:testDevDebugUnitTest` 또는 해당 PR의 focused JVM test 결과
- [ ] 가능하면 `:app:connectedDevDebugAndroidTest`, 불가하면 사유 기록
- [ ] 아래 수동 runtime 시나리오 evidence
  - [ ] BootReceiver
  - [ ] RoutineAlarmReceiver
  - [ ] Accessibility 차단
  - [ ] 긴급해제 만료

## 7. PR에 남길 검증 기록 템플릿

```md
## Runtime QA evidence
- Device/Emulator:
- Build variant:
- Commands:
  - `./gradlew :app:testDevDebugUnitTest`
  - `./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.receiver.RoutineReceiverPolicyTest" --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest"`
  - `./gradlew :app:connectedDevDebugAndroidTest` (or blocked: reason)
- Manual scenarios:
  - BootReceiver: pass/fail
  - RoutineAlarmReceiver: pass/fail
  - Accessibility blocking: pass/fail
  - Emergency unlock expiry: pass/fail
- Notes:
```

## 8. 현재 한계

- 이 문서는 수동/반수동 기준선이다.
- `BootReceiver`와 `RoutineAlarmReceiver`의 완전한 자동화는 별도 Android 통합 테스트 또는 Robolectric 전략이 추가되어야 한다.
- issue #27이 완전히 닫히려면 수동 QA 기준뿐 아니라 자동화 가능한 영역의 테스트 확대가 뒤따라야 한다.
