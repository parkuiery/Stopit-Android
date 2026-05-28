# 첫 잠금 활성화 퍼널 운영 런북

이 문서는 open issue #14 `첫 잠금 활성화 퍼널 개선`의 문서/운영 기준을 한곳에 모은 source of truth다.

목표는 단순히 퍼널 이름을 나열하는 것이 아니라, 아래를 한 번에 정리하는 것이다.

- 어떤 이벤트를 같은 퍼널로 봐야 하는가
- 각 단계가 실제로 무엇을 증명하는가
- 어떤 단계에서 어떤 CTA/후속 작업이 필요한가
- 어떤 경우에 지표를 믿지 말아야 하는가
- code lane / PM lane / metrics cron이 같은 계약을 재사용할 수 있는가

문서만으로 issue #14를 닫지는 않는다. 이 문서는 **repo 내부에서 더 남겨둘 수 있는 퍼널 계약·운영 기준·검증 템플릿**을 정리하고, 이후 코드/UI/배포 후 관측 작업의 기준선을 제공한다.

## 관련 문서

- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/METRICS_ANALYSIS.md`
- `docs/PRODUCT_METRICS_DASHBOARD.md`
- `docs/ops/stopit/product-context.md`
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`

## 왜 별도 런북이 필요한가

issue #14 코멘트 기준으로 현재 활성화 병목은 분명하지만, 숫자만으로 바로 UI 결론을 내리면 안 된다.

핵심 이유:

- `first_lock_configured` 전환율이 낮다.
- 동시에 `first_core_action_completed`가 `first_lock_configured`보다 높게 보인 기간이 있어, **퍼널 정의와 이벤트 의미를 먼저 맞춰야 한다.**
- 과거 메모/코멘트에는 `select_app_complete` 같은 구식 명칭이 섞여 있어, 현재 코드의 canonical 이벤트명과 바로 대응되지 않는다.

따라서 먼저 **이벤트 계약과 퍼널 해석 규칙**을 고정하고, 그 다음에 CTA/UI/측정 개선을 진행해야 한다.

### 현재 #13 queryability 경계

2026-05-29 live 확인 기준으로 활성화 퍼널 해석에 직접 필요한 `customEvent:*` 축은 아직 GA4 Admin에 materialize되지 않았다.

- `permission_outcome` by `customEvent:permission_name`, `customEvent:outcome` → `400 INVALID_ARGUMENT`
- `first_lock_configured` by `customEvent:source` → `400 INVALID_ARGUMENT`
- `app_block_intercepted` by `customEvent:block_source`, `customEvent:blocked_app_package` → 아직 registration gap 우선 정리 단계

따라서 현재는 `first_open`, `app_selection_completed`, `first_lock_configured`, `first_core_action_completed`, `app_block_intercepted` 같은 **상위 이벤트 count/users 비율**은 읽을 수 있어도, 어떤 권한/출처/차단 앱에서 병목이 생기는지 세부 분해 해석은 낮은 confidence로 둬야 한다.

추가 주의:

- 위 smoke는 activation 분해 축이 실제로 막혀 있다는 **대표 증거**다.
- activation follow-through에서 같이 필요한 `selected_app_count`, `is_onboarding`도 아직 `customEvent:*` registration/materialization 완료 전제로 취급하지 않는다.
- 따라서 앱 선택량 분포, 온보딩 vs 홈 진입 차이 같은 세부 결론도 GA4 Admin 등록 전에는 참고 수준으로만 다룬다.

운영 원칙:

- 퍼널 숫자가 이상해 보여도 곧바로 CTA/UI 결론으로 점프하지 않는다.
- 먼저 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` 기준으로 `permission_name`, `outcome`, `source`, `block_source`, `blocked_app_package` 등록/metadata 확인이 끝났는지 본다.
- 그 전까지 issue #14 후속 문서/PR은 "퍼널 해석 계약 정리"와 "manual/live registration 대기"를 분리해서 기록한다.

## canonical 퍼널 정의

현재 코드/문서 기준 첫 잠금 활성화 퍼널은 아래 7단계다.

1. `first_open`
2. `onboarding_step_view` / `onboarding_step_complete`
3. `permission_outcome`
4. `app_selection_completed`
5. `first_lock_configured`
6. `first_core_action_completed`
7. `app_block_intercepted`

### 단계별 의미

| 단계 | 이벤트 | 이 단계가 증명하는 것 | 아직 증명하지 못하는 것 |
| --- | --- | --- | --- |
| 1 | `first_open` | 앱이 신규 사용자에게 최초 실행되었다 | 온보딩 시작/완료, 권한 허용, 가치 경험 |
| 2 | `onboarding_step_view`, `onboarding_step_complete` | 사용자가 온보딩 스텝을 실제로 봤고 일부를 완료했다 | 권한이 실제로 허용되었는지, 앱 선택/잠금까지 갔는지 |
| 3 | `permission_outcome` | 접근성/알림 등 병목 권한의 결과가 남았다 | 차단 앱 선택/잠금 설정/실제 차단 성공 |
| 4 | `app_selection_completed` | 사용자가 차단 대상 앱을 실제로 골랐다 | 잠금/타이머/Keep 토글 등 실제 차단 준비가 끝났는지 |
| 5 | `first_lock_configured` | 사용자가 첫 잠금 진입점을 한 번이라도 구성했다 | 차단이 실제로 발생했는지 |
| 6 | `first_core_action_completed` | 사용자가 첫 가치 경험에 도달했다 | 접근성 서비스가 실제 차단 화면까지 정상 동작했는지 |
| 7 | `app_block_intercepted` | 스탑잇의 핵심 약속인 실제 차단이 발생했다 | 이후 반복 사용/루틴 정착 |

### 중요한 해석 원칙

- `first_lock_configured`는 **준비 완료 신호**다. 실제 가치 전달 자체는 아니다.
- `first_core_action_completed`는 **첫 가치 경험 신호**다. 다만 버전/경로별 의미가 바뀐 적이 있는지 먼저 확인해야 한다.
- `app_block_intercepted`는 **핵심 가치 전달의 최종 증거**다.
- 따라서 #14는 단순히 `first_lock_configured / first_open`만 올리는 문제가 아니라, **첫 준비 → 첫 가치 → 실제 차단**의 연결을 믿을 수 있게 만드는 문제다.

## legacy / drift 정리

아래 이름은 현재 canonical 이벤트명이 아니다.

| 구식/혼용 표현 | 현재 canonical 표현 | 메모 |
| --- | --- | --- |
| `select_app_complete` | `app_selection_completed` | 이슈 코멘트/과거 메모에서 혼용됨 |
| `onboarding_intro_started` | `onboarding_step_view(step_name=intro)` 또는 관련 `onboarding_step_complete` | 온보딩은 개별 step event 기준으로 해석 |
| `첫 잠금 -> 실제 차단`만 보는 단순 퍼널 | `first_lock_configured -> first_core_action_completed -> app_block_intercepted` | 준비/가치/실차단을 분리해서 봐야 함 |

운영 보고에서 legacy 이름을 다시 쓰지 않는다. 과거 스냅샷을 다시 인용할 때도 본문에 현재 canonical 이름으로 병기한다.

## 단계별 분자/분모 기준

### 기본 비율

| 지표 | 분자 | 분모 | 의미 |
| --- | --- | --- | --- |
| 앱 선택 완료율 | `app_selection_completed` users | `first_open` users | 신규 사용자가 차단 대상 앱까지 선택했는가 |
| 첫 잠금 설정률 | `first_lock_configured` users | `first_open` users | 신규 사용자가 첫 차단 준비를 끝냈는가 |
| 첫 가치 경험률 | `first_core_action_completed` users | `first_open` users | 신규 사용자가 첫 가치에 도달했는가 |
| 실제 차단 도달률 | `app_block_intercepted` users | `first_open` users | 신규 사용자가 실제 차단까지 경험했는가 |

### 보조 비율

| 지표 | 분자 | 분모 | 의미 |
| --- | --- | --- | --- |
| 앱 선택 후 첫 잠금 전환율 | `first_lock_configured` users | `app_selection_completed` users | 앱 선택 이후 CTA/권한/잠금 흐름이 막히는가 |
| 첫 잠금 후 첫 가치 전환율 | `first_core_action_completed` users | `first_lock_configured` users | 설정은 했지만 실제 가치로 못 이어지는가 |
| 첫 가치 후 실제 차단 전환율 | `app_block_intercepted` users | `first_core_action_completed` users | 접근성/런타임/타이밍 문제로 차단이 비는가 |

## 상태별 CTA 계약

이 문서의 CTA는 구현이 아니라 **제품/문서 계약**이다. code lane이 UI를 만들 때 이 표를 기준으로 삼는다.

| 마지막 확인 단계 | 사용자 상태 해석 | 다음 CTA 계약 | 측정해야 할 후속 신호 |
| --- | --- | --- | --- |
| `first_open`만 있음 | 앱을 켰지만 온보딩/권한/선택으로 진입하지 않음 | 온보딩 첫 가치 약속을 더 선명히 보여준다 | `onboarding_step_view`, `onboarding_step_complete` |
| `onboarding_step_view`는 있으나 `permission_outcome`이 약함 | 권한 단계에서 이탈 | 접근성/알림 권한의 왜 필요한지와 다음 행동을 명확히 보여준다 | `permission_outcome` (`permission_name`, `outcome`) |
| `permission_outcome`은 있으나 `app_selection_completed`가 낮음 | 권한 뒤 앱 선택에서 이탈 | 앱 선택 자체를 더 빠르고 덜 부담스럽게 만든다 | `app_selection_completed`, `selected_app_count` |
| `app_selection_completed`는 있으나 `first_lock_configured`가 낮음 | 앱은 골랐지만 첫 잠금/Keep 토글/타이머/루틴으로 이어지지 않음 | 다음 행동 CTA를 홈/온보딩 종료 상태에 명시한다 | `first_lock_configured`, `source` |
| `first_lock_configured`는 있으나 `first_core_action_completed`가 낮음 | 준비는 했지만 첫 가치 경험으로 이어지지 않음 | 실제 차단이 언제/어떻게 발생하는지 피드백을 분명히 한다 | `first_core_action_completed`, `elapsed_since_first_open_seconds`, `blocking_mode` |
| `first_core_action_completed`는 있으나 `app_block_intercepted`가 낮음 | 첫 가치/세션 계측과 실차단 계측이 어긋나거나 런타임 계약이 비어 있음 | 접근성 runtime/차단 화면/실차단 계측 연결을 검증한다 | `app_block_intercepted`, `block_source`, `blocked_app_package` |

## 해석 guardrail

### 1. 버전 혼합 금지

아래 상황이면 30일 합산 퍼널을 그대로 믿지 않는다.

- 이벤트가 최근 버전에서 새로 추가되었거나 의미가 바뀌었다.
- `first_core_action_completed`가 `first_lock_configured`보다 높게 나타난다.
- 특정 이벤트만 특정 build/flavor부터 안정화되었다.

기본 대응:

- `appVersion`으로 나눠 본다.
- 의미가 같은 버전 구간만 비교한다.
- 필요하면 “계측 안정화 전 / 후”를 따로 쓴다.

### 2. `first_open`은 Firebase 예약 이벤트다

- 앱 코드는 `first_open`을 직접 다시 찍지 않는다.
- `FirebaseKeepAnalytics.trackFirstOpen()`은 Firebase 예약 이벤트에 맞춰 no-op이다.
- 대신 앱 내부에서 `HAS_TRACKED_FIRST_OPEN`, `FIRST_OPEN_TIMESTAMP`를 보조 상태로 사용한다.

따라서 GA4의 `first_open`과 앱 내부 DataStore 플래그를 같은 것으로 가정해도 되지만, **로그 코드가 직접 reserved event를 내보낸다고 오해하면 안 된다.**

### 3. `first_core_action_completed`는 first lock의 단순 대체 지표가 아니다

- `first_lock_configured`는 준비/설정
- `first_core_action_completed`는 첫 가치 경험
- `app_block_intercepted`는 실제 차단

세 지표를 하나로 뭉개면 문제 위치를 잃는다.

### 4. Activation 개선은 신뢰 guardrail보다 앞서지 않는다

개선 후에도 아래가 악화되면 실패로 본다.

- crash-free users rate
- 긴급해제 사용률 급등
- 리뷰/평점 악화
- 권한 단계 불신/이탈 증가
- 실제 차단 실패나 runtime bug 증가

## 보고 템플릿

### issue / weekly report용 최소 포맷

```md
## 지표/근거
- 분석 기간:
- 비교 기간:
- appVersion 범위:
- 핵심 수치:
  - first_open:
  - app_selection_completed:
  - first_lock_configured:
  - first_core_action_completed:
  - app_block_intercepted:

## 해석
- 가장 큰 이탈 구간:
- 계측 신뢰 이슈 여부:
- 이번에 바꿔야 할 CTA/제품 계약:

## 후속 작업
- code/product:
- docs/ops:
- 14일 또는 다음 release 후 재측정 항목:
```

### PR body용 verification note 예시

```md
- 퍼널 canonical 문서를 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`에 추가
- `select_app_complete` 등 legacy 명칭을 canonical 이벤트명으로 정리
- docs/context-pack에서 활성화 퍼널 참조를 동일 계약으로 통일
```

## 로컬 검증 / 운영 확인 명령

### 이벤트 상수 확인

```bash
cd <repo-root>
rg -n 'ONBOARDING_STEP_VIEW|ONBOARDING_STEP_COMPLETE|PERMISSION_OUTCOME|APP_SELECTION_COMPLETED|FIRST_LOCK_CONFIGURED|FIRST_CORE_ACTION_COMPLETED|APP_BLOCK_INTERCEPTED' app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt
```

### 퍼널 문서 참조 일관성 확인

```bash
cd <repo-root>
rg -n 'FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK|first_core_action_completed|app_selection_completed' docs docs/ops/stopit
```

### cheap Gradle sanity check

```bash
cd <repo-root>
./gradlew -q help --task :app:testDevDebugUnitTest
./gradlew -q help --task :app:assembleProdDebug
```

## 이 문서가 닫지 않는 경계

이 문서 추가만으로는 아직 다음이 남는다.

- 앱 선택 후 미완료 사용자에게 실제 CTA를 어떤 화면/상태에 노출할지 구현
- 첫 차단/첫 가치 경험 UI 피드백 구현 또는 정교화
- 배포 후 14일 기준 재측정
- 필요 시 GA4 metadata/대시보드 쿼리 업데이트
- `permission_name` / `source` / `block_source` / `blocked_app_package`가 실제 `customEvent:*`로 등록됐는지 live 확인

따라서 docs lane 산출물은 기본적으로 `Refs #14`가 맞고, `Closes #14`는 code/product/manual measurement까지 충족될 때만 사용한다.
