# Feature Domain Boundary Contract

Issue: #651

이 문서는 Stopit Android에서 `feature.*` 패키지가 UI/ViewModel 범위를 넘어 database/service/receiver/analytics의 공유 domain boundary가 되는 drift를 막기 위한 docs/ops source of truth다. 목표는 모든 타입을 한 번에 옮기는 것이 아니라, **feature-private 화면 소유권과 app-wide runtime/domain 소유권을 분리하는 migration 계약**을 고정하는 것이다.

## 왜 중요한가

- `feature.*`는 화면, navigation, ViewModel, feature-local policy를 빠르게 묶기 좋은 경계지만, Room entity, AccessibilityService, receiver, analytics API가 이 경계에 직접 결합되면 화면 리팩터링이 런타임 차단/저장/계측을 같이 흔든다.
- 목표 잠금, 루틴, 부모 모드처럼 잠금 정책이 늘어날수록 service/database/analytics가 특정 feature package를 import하는 방식은 테스트 fixture와 release QA를 brittle하게 만든다.
- #520의 DAO 직접 의존 정리는 “DAO를 repository로 감싼다”는 1차 경계였다. #651은 그 다음 단계로, repository/domain/read-model 타입 자체의 소유 위치를 feature-private에서 shared domain/data boundary로 올리는 범위다.

## 소유권 계층

| 계층 | 예시 경로 | 소유 책임 | import 방향 |
| --- | --- | --- | --- |
| Feature UI / ViewModel | `app/src/main/java/com/uiery/keep/feature/<feature>/` | 화면 state, navigation, user intent, feature-local copy/validation | shared domain/data/service/analytics를 사용할 수 있음 |
| Shared domain / policy | `app/src/main/java/com/uiery/keep/domain/...` 또는 명시된 app-level package | 여러 feature/service/receiver가 함께 쓰는 model, policy, read-model | Android framework/Room/Firebase 세부 구현에 의존하지 않음 |
| Data boundary | `app/src/main/java/com/uiery/keep/data/...` 또는 repository package | Room entity/DAO mapping, persistence contract | shared domain을 반환하고 feature UI 타입을 반환하지 않음 |
| Runtime boundary | `service/`, `receiver/`, `analytics/` | AccessibilityService, alarm/boot receiver, analytics payload adapter | feature-private 타입을 직접 import하지 않음 |

## Import 규칙

### 허용

- Feature UI/ViewModel → shared domain/data/service/analytics boundary.
- Data boundary → Room DAO/entity + shared domain mapper.
- Runtime boundary → shared domain/policy/repository contract.
- Navigation shell(`KeepApp.kt`) → feature navigation route/screen entrypoint.
- Test sources → production package boundary를 검증하기 위한 fixture/import. 단, production drift로 일반화하지 않는다.

### 금지(최종 목표)

- `database/**` → `com.uiery.keep.feature.*` domain/model import.
- `service/**` / `receiver/**` → feature repository/domain/policy import.
- `analytics/**` → feature-local experiment/read-model type import.
- feature-local repository가 app-wide service/receiver의 유일한 public contract가 되는 구조.

## 현재 production drift inventory

`python3 -m unittest scripts.tests.test_feature_domain_boundary_contract -v`는 아래 inventory를 source tree에서 다시 계산한다. 새 항목이 추가되면 실패해야 하며, 항목 제거는 code-lane에서 이 문서와 allowlist를 함께 줄인다.

| Layer | 파일 | 현재 feature import | code-lane migration 방향 |
| --- | --- | --- | --- |
| service | `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt` | `feature.goallock.GoalLockRepository`, `feature.routine.RoutineRepository` | AccessibilityService가 shared lock-state read repository 또는 runtime-facing use case에 의존하도록 분리한다. `GoalLock` model import는 `domain.goallock`으로 이동 완료. |
| service | `app/src/main/java/com/uiery/keep/service/LockHistoryRecorder.kt` | `feature.lockhistory.LockHistoryRepository` | 완료 세션 기록용 repository contract를 app data/runtime boundary로 승격하거나 shared interface로 분리한다. |
| receiver | `app/src/main/java/com/uiery/keep/receiver/BootReceiver.kt` | `feature.routine.RoutineRepository` | boot/package/time-change restore가 shared routine runtime repository/use case를 사용하게 한다. |
| receiver | `app/src/main/java/com/uiery/keep/receiver/RoutineAlarmReceiver.kt` | `feature.routine.RoutineRepository` | alarm receiver가 shared routine runtime repository/use case를 사용하게 한다. |
| analytics | `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt` | `feature.routine.RepeatBlockRoutineSuggestion` | analytics API는 feature-local suggestion object 대신 bucketed analytics DTO/interface를 받는다. |
| analytics | `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt` | `feature.routine.RepeatBlockRoutineSuggestion` | Firebase adapter는 shared analytics DTO를 payload로 변환한다. |

## Migration order

1. **GoalLock domain first — repo-internal foothold complete**
   - `GoalLock`, `GoalLockMode`, `GoalLockStoredStatus`, `GoalLockRuntimeStatus`, `GoalLockPolicy`는 `domain.goallock` shared domain boundary로 이동했다.
   - `GoalLockEntity` mapper, Home, AccessibilityService block decision, detail/creation ViewModel이 같은 shared model을 참조한다.
   - 남은 GoalLock 관련 feature import는 app/service/runtime entrypoint가 아직 `GoalLockRepository` feature repository에 의존하는 경계다.
   - focused 검증 후보: `GoalLockPolicyTest`, `GoalLockPersistenceMapperTest`, `KeepAccessibilityServiceBlockDecisionTest`, `KeepAccessibilityServiceIntegrationTest` 주변 JVM/androidTest.
2. **Routine runtime repository boundary**
   - `RoutineRepository`를 feature UI repository에서 app/runtime read/write contract로 분리하거나 runtime-facing use case를 둔다.
   - Boot/Package/RoutineAlarm receiver와 AccessibilityService가 feature package import 없이 restore/reschedule/cache를 수행하도록 한다.
   - focused 검증 후보: `RoutineReceiverPolicyTest`, `ReceiverRuntimeIntegrationTest`, exact-alarm receiver suites.
3. **Analytics DTO boundary**
   - `RepeatBlockRoutineSuggestion` analytics payload를 privacy-safe bucket DTO로 분리한다.
   - `KeepAnalytics` interface가 feature-local type을 받지 않도록 바꾼다.
   - focused 검증 후보: `RepeatBlockRoutineSuggestionAnalyticsTest`, `FirebaseKeepAnalyticsTest`, #531 docs contract tests.
4. **LockHistory recorder boundary**
   - `LockHistoryRepository`가 feature 화면 repository와 runtime recording repository 중 어느 소유권인지 정한다.
   - service recording path가 feature-private repository를 import하지 않도록 shared interface나 data boundary로 이동한다.

## Static guard 계약

`python3 -m unittest scripts.tests.test_feature_domain_boundary_contract -v`는 아래를 고정한다.

- production `database/`, `service/`, `receiver/`, `analytics/` source의 `feature.*` imports가 위 inventory와 정확히 일치한다.
- issue #651 문서가 현재 inventory의 모든 파일과 migration 방향을 명시한다.
- `docs/AGENTS.md`와 `docs/ops/stopit/engineering-context.md`가 이 문서를 참조해 future docs/code lane이 #520 DAO boundary와 #651 feature-domain boundary를 구분한다.

## PR / Issue closure rule

- 이 docs-lane PR은 migration 계약과 current-drift guard를 만드는 범위라 `Refs #651`이 맞다.
- `Closes #651`는 아래가 모두 만족될 때만 사용한다.
  - `database/service/receiver/analytics` production source에서 feature-private domain/repository imports가 제거되거나 명시된 shared boundary allowlist로 축소된다.
  - `GoalLockEntity` mapper와 AccessibilityService block decision이 shared domain contract를 사용한다. (2026-06 code-lane foothold 완료)
  - analytics API가 feature-local suggestion object 대신 shared analytics DTO/read-model contract를 받는다.
  - static guard가 더 이상 debt inventory allowlist에 의존하지 않고 새 역방향 의존을 차단한다.
  - `./gradlew :app:testDevDebugUnitTest`와 관련 focused runtime/analytics verification이 통과한다.

## Handoff evidence template

```md
## #651 feature domain boundary evidence

- PR:
- Head SHA:
- 변경 범위:
  - [x] GoalLock shared domain boundary
  - [ ] Routine runtime repository/use-case boundary
  - [ ] RepeatBlock analytics DTO boundary
  - [ ] LockHistory runtime recording boundary
  - [ ] static guard inventory 감소
- 검증:
  - [ ] `python3 -m unittest scripts.tests.test_feature_domain_boundary_contract -v`
  - [ ] `./gradlew :app:testDevDebugUnitTest ...`
  - [ ] runtime/receiver/analytics focused command
- 남은 경계:
```
