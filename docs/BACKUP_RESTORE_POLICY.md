# Stopit Backup / Restore Policy

이 문서는 issue #26의 최종 운영 계약이다. 이번 정책은 **잠금 상태 / 긴급해제 상태 / 리뷰·분석 플래그 같은 런타임 상태가 기기 이전 후 되살아나지 않도록 보수적으로 통제**하는 데 초점을 둔다.

현재 Stopit의 영속 상태는 크게 두 묶음이다.

- Room DB: `keep-database`
- Preferences DataStore: `keep-datastore`

문제는 `keep-datastore` 한 파일 안에 아래가 섞여 있다는 점이다.

- 장기 설정처럼 보이는 값
- 현재 잠금/긴급해제 진행 상태
- 리뷰/분석/토큰 같은 기기·세션 종속 값

이 구조에서 DataStore를 부분적으로만 안전하게 복원할 수 없기 때문에, 이번 정책은 **DataStore 전체를 백업 대상에서 제외**하고, **Room DB만 백업/복원**한다.

## 1. 이번 정책의 실제 계약

### 복원 대상

- `keep-database`
  - `routine` / `RoutineEntity`
  - `lock_history` / `LockHistoryEntity`
  - `emergency_unlock` / `EmergencyUnlockEntity`

### 복원 제외 대상

- `keep-datastore.preferences_pb`
  - 선택 앱 목록
  - 현재 수동 잠금 / timed lock 상태
  - 긴급해제 진행 상태와 만료 시각
  - 긴급해제 설정값
  - 리뷰 프롬프트 플래그
  - 분석용 최초 실행/세션 플래그
  - FCM token
  - 기타 DataStore 기반 상태 전부

## 2. 왜 이렇게 결정했는가

핵심 이유는 신뢰/안전이다.

1. **복원 직후 stale lock state가 즉시 살아나면 안 된다.**
   - 이전 기기에서 진행 중이던 잠금이 새 기기에서 갑자기 적용되면 오동작처럼 느껴질 수 있다.

2. **복원 직후 stale emergency unlock state가 남아 차단이 우회되면 안 된다.**
   - 이전 기기의 긴급해제 만료 시각/대상 앱 정보가 새 기기에서 살아나면 핵심 약속이 깨진다.

3. **DataStore는 파일 단위로만 다룰 수 있는데, 현재는 안전한 값과 위험한 값이 섞여 있다.**
   - `selected_app_packages`, `prevent_uninstall` 같은 값만 복원하고 싶어도, 같은 파일에 `is_keep`, `lock_time`, `emergency_unlock_expire_time`, `review_pending`, `fcm_token` 등이 함께 있다.

4. **이번 이슈의 우선순위는 “복원 편의”보다 “오복원 방지”다.**
   - 일부 설정을 다시 잡아야 하더라도, 잠금/긴급해제 상태가 잘못 복원되는 것보다 낫다.

## 3. XML 정책

정책 반영 파일:

- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

이번 PR 기준 정책은 다음과 같다.

- `database/keep-database`만 cloud backup / device transfer 대상으로 포함
- DataStore 파일은 포함하지 않는다. Android lint의 `FullBackupContent` 규칙상 `database/keep-database`만 include하면 `file/datastore/keep-datastore.preferences_pb` exclude는 중복이므로 XML에는 두지 않는다.

즉, **Room DB는 복원되고 DataStore는 복원되지 않는다.** 루틴의 authoritative source of truth는 Room이며, `PreferencesKey.ROUTINES`는 boot/routine alarm/accessibility 호환성을 위한 비권위(runtime compatibility) 캐시로만 취급한다. 따라서 boot/routine alarm 경로에서는 필요 시 Room에 복원된 routine 목록으로 `PreferencesKey.ROUTINES` 캐시를 다시 채우되, 후속 스케줄링/차단 판단은 항상 Room 기준과 일치해야 한다.

## 4. 저장 상태별 해석

### DataStore (`keep-datastore`)

정의 위치: `app/src/main/java/com/uiery/keep/datastore/DataStore.kt`

아래 키들은 현재 모두 같은 파일에 있으므로 **이번 정책에서 전부 복원 제외**다.

| 키 | 의미 | 이번 정책 |
| --- | --- | --- |
| `selected_app_packages` | 차단 대상 앱 목록 | 복원 안 함 |
| `prevent_uninstall` | 삭제 방지 설정 | 복원 안 함 |
| `has_shown_alarm_permission` | 알람 권한 안내 노출 여부 | 복원 안 함 |
| `emergency_unlock_daily_limit` | 긴급해제 일일 한도 | 복원 안 함 |
| `emergency_unlock_duration_options` | 긴급해제 옵션 | 복원 안 함 |
| `emergency_unlock_reason_required` | 사유 입력 요구 여부 | 복원 안 함 |
| `is_keep` | 현재 수동 잠금 활성 여부 | 복원 안 함 |
| `start_time` | 현재 세션 시작 시각 | 복원 안 함 |
| `lock_time` | 현재 timed lock 종료 시각 | 복원 안 함 |
| `emergency_unlock_apps` | 현재 긴급해제 중인 앱 | 복원 안 함 |
| `emergency_unlock_expire_time` | 긴급해제 만료 시각 | 복원 안 함 |
| `emergency_unlock_enabled` | 긴급해제 진행 여부 | 복원 안 함 |
| `routines` | receiver/service 호환성용 루틴 JSON 캐시(비권위) | 복원 안 함 |
| `fcm_token` | FCM token 로컬 저장 값(백엔드 device registration 아님) | 복원 안 함 |
| `has_tracked_first_open` | 분석 플래그 | 복원 안 함 |
| `has_tracked_first_lock_configured` | 분석 플래그 | 복원 안 함 |
| `first_open_timestamp` | 분석 기준 시각 | 복원 안 함 |
| `has_tracked_first_core_action` | 분석 플래그 | 복원 안 함 |
| `review_pending` | 리뷰 프롬프트 대기 상태 | 복원 안 함 |
| `last_review_prompt_at_ms` | 최근 리뷰 프롬프트 시각 | 복원 안 함 |
| `successful_session_count` | 누적 세션 카운트 | 복원 안 함 |
| `last_backgrounded_at_ms` | 최근 백그라운드 시각 | 복원 안 함 |
| `is_new` / `total_block_time` / `long_block_time` | UX/누적 상태 | 복원 안 함 |

### Room (`keep-database`)

정의 위치:

- `app/src/main/java/com/uiery/keep/database/KeepDatabase.kt`
- `app/src/main/java/com/uiery/keep/database/di/DatabaseModule.kt`

이번 정책에서는 DB 파일을 통째로 복원한다.

| 테이블/엔티티 | 의미 | 이번 정책 | 메모 |
| --- | --- | --- | --- |
| `routine` / `RoutineEntity` | 반복 차단 루틴 정의 | 복원 | 장기 사용자 의도 |
| `lock_history` / `LockHistoryEntity` | 잠금 세션 이력 | 복원 | 히스토리 요약/상세 화면의 source of truth. `LONG_BLOCK_TIME`/`TOTAL_BLOCK_TIME`는 legacy compatibility cache로만 유지 |
| `emergency_unlock` / `EmergencyUnlockEntity` | 긴급해제 이력 | 복원 | DB 분리 전까지 routine과 같은 DB에 묶여 복원됨 |

> 주의: 장기적으로는 `routine`과 이력성 테이블을 분리하는 것이 더 깔끔할 수 있다. 하지만 이번 이슈의 목표는 **잠금/긴급해제의 active runtime state 복원 통제**이며, 그 위험은 DataStore 제외만으로 즉시 줄일 수 있다.

## 5. 사용자/운영 관점에서 기대해야 하는 동작

기기 이전/클라우드 복원 후 기대 동작은 다음과 같다.

- 루틴은 복원될 수 있다.
- 반면 아래는 **새 기기에서 다시 잡히거나 재생성**된다.
  - 선택 앱 목록
  - 수동 잠금 / timed lock 상태
  - 긴급해제 진행 상태와 설정값
  - 리뷰 프롬프트 관련 상태
  - FCM token
  - 분석용 최초 실행/세션 플래그

즉, 이번 정책은 **“새 기기에서 위험한 진행 상태는 들고 가지 않고, Room DB만 복원한다”**가 핵심이다.

## 6. 수동 QA 체크리스트

### 시나리오 A — Room 루틴 복원
1. 기존 기기에서 반복 루틴 1개 이상 생성
2. 백업/기기 이전 수행
3. 새 기기에서 앱 실행

확인:
- [ ] 루틴 목록이 유지된다.
- [ ] 루틴 활성 여부가 비정상적으로 초기화되지 않는다.
- [ ] 자동 baseline: `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest`

### 시나리오 B — DataStore 상태 미복원
1. 기존 기기에서 차단 앱 선택, 수동 잠금 또는 timed lock 활성화
2. 긴급해제 설정을 바꾸고, 가능하면 긴급해제를 한 번 실행
3. 백업/기기 이전 수행
4. 새 기기에서 앱 실행 직후 대상 앱 열기

확인:
- [ ] 선택 앱 목록이 그대로 남아 있지 않다.
- [ ] 복원 직후 stale lock state 때문에 예상치 못한 즉시 차단이 발생하지 않는다.
- [ ] 이전 기기의 긴급해제 상태가 복원되어 차단이 계속 우회되지 않는다.
- [ ] 긴급해제 설정값도 새 기기 기준으로 다시 잡아야 하는 상태다.
- [ ] 자동 baseline: `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest`

### 시나리오 C — 리뷰/토큰/분석 재초기화
1. 복원 후 앱 실행
2. FCM token 로컬 저장/리뷰 프롬프트/최초 실행 관련 흐름 확인

확인:
- [ ] 새 기기에서 FCM token이 정상 재생성되고 로컬 DataStore에 다시 저장된다.
  - 이 확인은 제거된 backend device registration 성공을 의미하지 않는다. token 저장과 registration 이벤트 해석 경계는 `docs/FCM_DEVICE_REGISTRATION_CONTRACT.md`를 따른다.
  - 자동 baseline: `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
  - 수동 evidence 형식은 `docs/QA_RUNTIME_CHECKLIST.md`의 `FCM token regeneration evidence` 템플릿을 따른다.
- [ ] 리뷰 프롬프트가 복원 직후 부자연스럽게 즉시 뜨지 않는다.
- [ ] 분석용 최초 실행/세션 플래그가 새 기기 흐름을 그대로 오염시키지 않는다.
- [ ] 자동 baseline: `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest`

### 저장소 자동 baseline 범위

- `com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest`
  - 복원된 Room routine을 Boot/Routine alarm 진입에서 `PreferencesKey.ROUTINES`로 재수화하는지 확인
  - DataStore가 비어 있는 restored-device shape에서 선택 앱/lock/emergency/review/analytics session/FCM token 키를 되살리지 않는지 확인
- `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest`
  - Boot/Routine alarm의 notification + 재예약 contract 확인
- `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest`
  - 만료된 긴급해제 state 정리와 재차단 대상 판정 확인
- `com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
  - 복원 직후 새 FCM token 저장 경로가 stale token을 덮어쓰는지 확인

위 자동 baseline은 **정책상 DataStore 전체 제외 + Room DB 복원** shape를 에뮬레이터에서 재현한다. 실제 기기 이전/cold boot/Accessibility cross-app 증적은 release candidate에서 수동 또는 반자동 evidence를 계속 남긴다.

## 7. PR / release 기록 템플릿

```md
## Backup / restore QA evidence
- Device/Emulator:
- Variant:
- Commands:
  - `./gradlew :app:assembleDevDebug`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
- Room restore (routines): pass/fail
- DataStore reset (selected apps / lock / emergency / review / token): pass/fail
- Notes:
```

## 8. 이번 이슈로 닫는 범위

이번 이슈에서 해결한 것:
- `backup_rules.xml`에 실제 include/exclude 정책을 반영했다.
- `data_extraction_rules.xml`에 cloud backup / device transfer 정책을 반영했다.
- 잠금/긴급해제/리뷰/분석/토큰 상태가 복원되지 않도록 DataStore 전체 제외 정책을 명시했다.
- 수동 QA 기준을 “루틴 복원 + DataStore reset” 관점으로 고정했다.

이번 이슈에서 **의도적으로 하지 않은 것**:
- DataStore를 여러 파일로 분리해 일부 설정만 복원되게 만드는 리팩터링
- Room DB를 routine/history/emergency_unlock 용도로 분리하는 구조 변경

위 두 항목은 별도 설계/구현 작업이 필요하지만, issue #26의 핵심 acceptance인 **복원 대상/제외 대상 명시**와 **잠금/긴급해제 상태 복원 통제**는 이번 정책으로 충족된다.
