# Stopit Backup / Restore Policy

이 문서는 issue #26의 QA/운영용 작은 slice로, Stopit이 자동 백업/기기 이전에서 무엇을 복원해도 안전한지 판단 기준을 정리한다.

현재 상태:
- `app/src/main/AndroidManifest.xml`에서 `android:allowBackup="true"`를 사용한다.
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- 위 XML은 아직 샘플 기본값에 가깝고, 실제 include/exclude 정책은 후속 구현이 필요하다.

이 문서는 **지금 당장 XML을 바꾸기 전에** 유지해야 할 복원 계약과 수동 QA 기준을 정의한다.
따라서 이 문서만으로 issue #26이 완전히 닫히지는 않는다. 후속 PR에서 XML 규칙과 실제 복원 동작을 이 계약에 맞춰 반영해야 한다.

## 1. 원칙

1. **사용자 의도는 복원 가능**해야 한다.
   - 차단 대상 앱 목록
   - 반복 루틴 설정
   - 긴급해제 설정값(제한 횟수, 사유 요구 여부 등)

2. **실행 중 런타임 상태는 복원에 보수적**이어야 한다.
   - 현재 잠금 진행 여부
   - 잠금 종료 시각
   - 긴급해제 진행 상태와 만료 시각
   - 앱이 최근 백그라운드로 갔던 순간 같은 세션성 값

3. **민감하거나 기기 종속적인 값은 복원하지 않는 쪽이 안전**하다.
   - FCM token
   - 첫 실행/리뷰 프롬프트/분석용 플래그
   - 잠금/긴급해제 이력처럼 개인 행동 흔적이 남는 기록

4. **복원 직후 차단이 우회되거나 갑자기 활성화되면 안 된다.**
   - 새 기기 복원 직후 사용자가 의도하지 않은 활성 잠금/긴급해제가 살아나면 신뢰가 크게 떨어진다.

## 2. 현재 저장 상태 인벤토리

### DataStore (`keep-datastore`)

정의 위치: `app/src/main/java/com/uiery/keep/datastore/DataStore.kt`

| 키 | 의미 | 권장 정책 | 이유 |
| --- | --- | --- | --- |
| `selected_app_packages` | 차단 대상 앱 목록 | **복원** | 사용자 의도 자체에 해당 |
| `prevent_uninstall` | 삭제 방지 설정 | **복원** | 사용자가 다시 켜기 번거로운 장기 설정 |
| `has_shown_alarm_permission` | 알람 권한 안내 노출 여부 | **복원 안 함 / 재평가** | 기기/OS 상태가 달라질 수 있음 |
| `emergency_unlock_daily_limit` | 긴급해제 일일 한도 | **복원** | 설정값 자체는 유지 가능 |
| `emergency_unlock_duration_options` | 긴급해제 옵션 | **복원** | 장기 설정 |
| `emergency_unlock_reason_required` | 사유 입력 요구 여부 | **복원** | 장기 설정 |
| `is_keep` | 현재 수동 잠금 활성 여부 | **복원 안 함** | 복원 직후 즉시 잠금이 걸리면 오동작처럼 느껴질 수 있음 |
| `start_time` | 현재 세션 시작 시각 | **복원 안 함** | 런타임 세션 값 |
| `lock_time` | 현재 timed lock 종료 시각 | **복원 안 함** | 과거 기기 시각/세션과 결합된 값 |
| `emergency_unlock_apps` | 현재 긴급해제 중인 앱 | **복원 안 함** | 복원 후 차단 우회 위험 |
| `emergency_unlock_expire_time` | 긴급해제 만료 시각 | **복원 안 함** | 복원 후 stale state 위험 |
| `emergency_unlock_enabled` | 긴급해제 진행 여부 | **복원 안 함** | stale state 위험 |
| `routines` | 런타임/보조 루틴 상태 문자열 | **후속 구현 전까지 정책 보류** | `BootReceiver`/`RoutineAlarmReceiver`/`KeepAccessibilityService`가 직접 읽으므로, 제외하려면 Room 기반 재생성/rehydration 경로를 먼저 보강해야 함 |
| `fcm_token` | FCM 등록 토큰 | **복원 안 함** | 기기 종속 값 |
| `has_tracked_first_open` | 분석 플래그 | **복원 안 함** | 분석용 메타데이터 |
| `has_tracked_first_lock_configured` | 분석 플래그 | **복원 안 함** | 분석용 메타데이터 |
| `first_open_timestamp` | 분석 기준 시각 | **복원 안 함** | 새 기기 기준으로 재측정하는 편이 안전 |
| `has_tracked_first_core_action` | 분석 플래그 | **복원 안 함** | 분석용 메타데이터 |
| `review_pending` | 리뷰 프롬프트 대기 상태 | **복원 안 함** | 새 기기에서 잘못된 prompt 타이밍 유발 가능 |
| `last_review_prompt_at_ms` | 최근 리뷰 프롬프트 시각 | **복원 안 함** | 기기 이전 후 stale state |
| `successful_session_count` | 누적 세션 카운트 | **복원 안 함** | 분석/UX 보조값 |
| `last_backgrounded_at_ms` | 최근 백그라운드 시각 | **복원 안 함** | 세션성 값 |
| `is_new` / `total_block_time` / `long_block_time` | UX/누적 상태 | **후속 결정 필요** | 사용자 가치와 분석 목적이 섞여 있어 실제 XML 반영 전 판단 필요 |

### Room (`keep-database`)

정의 위치: `app/src/main/java/com/uiery/keep/database/KeepDatabase.kt`

| 테이블/엔티티 | 의미 | 권장 정책 | 이유 |
| --- | --- | --- | --- |
| `routine` / `RoutineEntity` | 반복 차단 루틴 정의 | **복원** | 장기 사용자 의도 |
| `lock_history` / `LockHistoryEntity` | 잠금 세션 이력 | **복원 안 함** | 개인 사용 흔적 + 장기 설정 아님 |
| `emergency_unlock` / `EmergencyUnlockEntity` | 긴급해제 이력 | **복원 안 함** | 민감한 행동 이력 + 장기 설정 아님 |

## 3. 후속 XML 구현 계약

후속 PR에서 `backup_rules.xml` / `data_extraction_rules.xml`을 수정할 때는 아래 계약을 만족해야 한다.

### 반드시 포함해야 하는 것
- 사용자 의도성 설정
  - 차단 대상 앱 목록
  - 반복 루틴 정의
  - 긴급해제 설정값
  - 삭제 방지 같은 장기 설정

### 반드시 제외하거나 복원 후 초기화해야 하는 것
- 현재 진행 중인 잠금 상태
- 현재 진행 중인 긴급해제 상태
- 세션 시각 관련 값
- 기기 종속 token
- 리뷰/분석 플래그
- 잠금/긴급해제 이력

### 구현 후 검증 질문
- 복원 직후 대상 앱이 의도치 않게 즉시 차단되는가?
- 복원 직후 긴급해제 우회가 남아 있는가?
- 루틴은 복원되지만 현재 세션성 값은 깨끗하게 초기화되는가?
- 새 기기에서 FCM token/리뷰/분석 플래그가 부정확하게 이어지지 않는가?

## 4. 수동 QA 체크리스트

실제 XML 반영 이후에는 아래 시나리오를 최소 1회 수행한다.

### 시나리오 A — 사용자 의도 복원
1. 기존 기기에서 차단 앱 2개 이상 선택
2. 반복 루틴 1개 이상 생성
3. 긴급해제 설정(일일 한도/사유 요구 여부) 변경
4. 백업/기기 이전 수행
5. 새 기기에서 앱 실행

확인:
- [ ] 선택 앱 목록이 유지된다.
- [ ] 루틴 목록/활성 상태가 유지된다.
- [ ] 긴급해제 설정값이 유지된다.

### 시나리오 B — 런타임 잠금 상태 초기화
1. 기존 기기에서 수동 잠금 또는 timed lock을 활성화
2. 백업/기기 이전 수행
3. 새 기기에서 앱 실행 직후 대상 앱 열기

확인:
- [ ] 복원 직후 stale lock state 때문에 예상치 못한 즉시 차단이 발생하지 않는다.
- [ ] 필요한 경우 사용자가 다시 잠금을 시작해야 한다는 동작이 일관된다.

### 시나리오 C — 긴급해제 stale state 차단
1. 기존 기기에서 긴급해제를 활성화한 상태로 백업/기기 이전 수행
2. 새 기기에서 대상 앱 열기

확인:
- [ ] 이전 기기의 긴급해제 상태가 복원되어 차단이 계속 우회되지 않는다.
- [ ] 긴급해제 관련 UI/상태가 깨끗하게 재초기화된다.

### 시나리오 D — 분석/기기 종속 값 재생성
1. 복원 후 앱 실행
2. FCM 등록/리뷰 프롬프트/온보딩 관련 동작 확인

확인:
- [ ] 새 기기에서 FCM token이 정상 재생성된다.
- [ ] 리뷰 프롬프트가 복원 직후 부자연스럽게 즉시 뜨지 않는다.
- [ ] 분석용 최초 실행/세션 플래그가 새 기기 흐름을 왜곡하지 않는다.

## 5. Release/PR 연결 지점

관련 문서:
- `docs/RELEASE_CHECKLIST.md`
- `docs/QA_RUNTIME_CHECKLIST.md`
- `docs/PLAY_DEPLOYMENT.md`

릴리즈 전 또는 backup XML 변경 PR에서는 다음을 남긴다.

```md
## Backup / restore QA evidence
- Device/Emulator:
- Variant:
- Backup rules changed?: yes/no
- User-intent restore (selected apps / routines / settings): pass/fail
- Runtime-state reset (manual lock / timed lock / emergency unlock): pass/fail
- Token / review / analytics re-init: pass/fail
- Notes:
```

## 6. 이 문서로 해결한 것 / 아직 남은 것

이번 slice에서 해결한 것:
- 자동 백업 정책 판단 기준을 문서화했다.
- 복원 후 반드시 확인해야 할 QA 시나리오를 정의했다.
- release 체크리스트와 연결할 기준 문구를 제공했다.

아직 남은 것:
- `backup_rules.xml` 실제 include/exclude 작성
- `data_extraction_rules.xml` 실제 cloud/device-transfer 규칙 작성
- 필요 시 복원 후 초기화 코드 보강
- 실제 device/emulator 기반 복원 검증 evidence 축적
