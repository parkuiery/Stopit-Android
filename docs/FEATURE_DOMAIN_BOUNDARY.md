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
| database/service/receiver/analytics | 없음 | 없음 | PR #651 final boundary package에서 남은 `GoalLockRepository` / `ParentModeSessionStore` feature import를 각각 `data.goallock` / `data.parentmode` boundary로 이동해 debt inventory를 비웠다. 새 feature-private import가 생기면 static guard가 실패해야 한다. |

## Migration order

1. **GoalLock domain first — repo-internal foothold complete**
   - `GoalLock`, `GoalLockMode`, `GoalLockStoredStatus`, `GoalLockRuntimeStatus`, `GoalLockPolicy`는 `domain.goallock` shared domain boundary로 이동했다.
   - `GoalLockEntity` mapper, Home, AccessibilityService block decision, detail/creation ViewModel이 같은 shared model을 참조한다.
   - 완료: `GoalLockRepository`는 `data.goallock` boundary로 이동했고 app/service/runtime entrypoint는 feature package가 아니라 shared data repository에 의존한다.
   - focused 검증 후보: `GoalLockPolicyTest`, `GoalLockPersistenceMapperTest`, `KeepAccessibilityServiceBlockDecisionTest`, `KeepAccessibilityServiceIntegrationTest` 주변 JVM/androidTest.
2. **ParentMode runtime session/policy boundary — repo-internal foothold complete**
   - 완료: `ParentModeSession`, `ParentModeSessionState`, `ParentModeRuntimePolicy`는 `domain.parentmode` shared domain boundary로 이동했다.
   - 완료: `KeepAccessibilityServiceBlockDecision`은 feature package import 없이 parent-mode bypass/block state를 판정한다.
   - 완료: `ParentModeSessionStore`는 `data.parentmode` boundary로 이동했고 AccessibilityService는 feature package가 아니라 shared data store에 의존한다.
   - focused 검증 후보: `ParentModeRuntimePolicyTest`, `ParentModeSessionStoreTest`, `KeepAccessibilityServiceBlockDecisionTest`, parent-mode accessibility integration suites.
3. **Routine runtime repository boundary — repo-internal foothold complete**
   - `RoutineRepository` / `RoomRoutineRepository`는 `data.routine` shared data boundary로 이동했다.
   - Boot/Package/RoutineAlarm receiver와 AccessibilityService는 feature package import 없이 restore/reschedule/cache를 수행한다.
   - focused 검증 후보: `RoutineReceiverPolicyTest`, `ReceiverRuntimeIntegrationTest`, exact-alarm receiver suites.
4. **Analytics DTO boundary**
   - 완료: `KeepAnalytics` / `FirebaseKeepAnalytics`는 feature-local `RepeatBlockRoutineSuggestion` 대신 `RepeatBlockRoutineSuggestionAnalyticsPayload`를 받는다.
   - 완료: Routine feature는 prefill 앱/package를 로컬 UI/저장 경계에만 유지하고 analytics boundary에는 reason/time/day/category/repeat/coverage bucket DTO만 넘긴다.
   - 완료: `RepeatBlockRoutineSuggestionAnalyticsTest`, `RoutineBottomSheetViewModelTest`, static feature-domain guard가 이 경계를 검증한다.
5. **LockHistory recorder boundary**
   - 완료: `LockHistorySessionWriter`가 runtime Room ledger write boundary를 소유하고, `LockHistoryRepository`는 feature read-model repository로 남긴다.
   - 완료: service recording path가 feature-private repository를 import하지 않도록 data boundary로 이동했다.

## Static guard 계약

`python3 -m unittest scripts.tests.test_feature_domain_boundary_contract -v`는 아래를 고정한다.

- production `database/`, `service/`, `receiver/`, `analytics/` source의 `feature.*` imports가 비어 있음을 확인한다.
- issue #651 문서가 현재 inventory의 모든 파일과 migration 방향을 명시한다.
- `docs/AGENTS.md`와 `docs/ops/stopit/engineering-context.md`가 이 문서를 참조해 future docs/code lane이 #520 DAO boundary와 #651 feature-domain boundary를 구분한다.

## PR / Issue closure rule

- 초기 docs-lane PR은 migration 계약과 current-drift guard를 만드는 범위라 `Refs #651`이 맞았다.
- `Closes #651`는 아래가 모두 만족될 때만 사용한다.
  - `database/service/receiver/analytics` production source에서 feature-private domain/repository imports가 제거된다.
  - `GoalLockEntity` mapper와 AccessibilityService block decision이 shared domain contract를 사용한다. (2026-06 code-lane foothold 완료)
  - parent-mode session/policy가 shared runtime boundary를 사용한다. (2026-06 code-lane foothold 완료)
  - parent-mode session store가 shared data boundary를 사용한다. (2026-06 code-lane final boundary package 완료)
  - analytics API가 feature-local suggestion object 대신 shared analytics DTO/read-model contract를 받는다.
  - static guard가 더 이상 debt inventory allowlist에 의존하지 않고 새 역방향 의존을 차단한다. (현재 `EXPECTED_FEATURE_IMPORTS = {}`)
  - `./gradlew :app:testDevDebugUnitTest`와 관련 focused runtime/analytics verification이 통과한다.

## Handoff evidence template

```md
## #651 feature domain boundary evidence

- PR:
- Head SHA:
- 변경 범위:
  - [x] GoalLock shared domain boundary
  - [x] Routine runtime repository/use-case boundary
  - [x] ParentMode runtime session/policy boundary
  - [x] RepeatBlock analytics DTO boundary
  - [x] LockHistory runtime recording boundary
  - [x] static guard inventory 감소
- 검증:
  - [ ] `python3 -m unittest scripts.tests.test_feature_domain_boundary_contract -v`
  - [ ] `./gradlew :app:testDevDebugUnitTest ...`
  - [ ] runtime/receiver/analytics focused command
- 남은 경계:
```
