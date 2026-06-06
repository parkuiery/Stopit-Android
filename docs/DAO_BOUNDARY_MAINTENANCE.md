# DAO Boundary Maintenance

이 문서는 #520(`ViewModel/Receiver/Service의 DAO 직접 의존을 저장소 경계로 정리`)의 repo-internal QA 인벤토리와 회귀 방지 계약을 고정한다.

## 목적

Room DAO는 DB/source-of-truth 구현 세부사항이다. Feature ViewModel, Receiver, Service가 `Room DAO 직접 import`를 계속 늘리면 같은 잠금·루틴·이력 계약을 각 런타임 경로가 다르게 해석하기 쉽다. 따라서 한 번에 전면 재작성하지 않고, 위험도가 높은 feature 단위부터 저장소/read-model 경계를 두고 static guard로 역행을 막는다.

## 완료된 패키지

### Lock history feature boundary

#520의 첫 repo-internal QA 패키지는 잠금 이력 화면을 DAO 직접 접근에서 분리했다.

#### 허용 경계

- `LockHistoryRepository`가 lock-history feature의 Room `LockHistoryDao` 접근 허용 경계다.
- `LockHistoryViewModel`은 기간별 이력 조회와 성과 리포트 계산에 repository가 제공하는 domain model list를 사용한다.
- `BlockedAppsViewModel`은 전체 이력의 앱별 차단 빈도 read-model을 repository에서 받는다.

### Review eligibility boundary

#520의 두 번째 repo-internal QA 패키지는 리뷰 프롬프트 eligibility 판단을 DAO 직접 접근에서 분리했다.

#### 허용 경계

- `ReviewEligibilityRepository`가 review eligibility의 Room `EmergencyUnlockDao` / `LockHistoryDao` 접근 허용 경계다.
- `ReviewEligibilityEvaluator`는 kill switch, build flavor, accessibility, quiet hours, cooldown, recent emergency unlock, recent success policy ordering만 소유한다.
- `ReviewEligibilityEvaluator` 테스트는 fake repository 경계를 통해 최근 긴급해제/성공 세션 값을 주입하므로 Room DAO fake에 직접 결합하지 않는다.

### Goal lock feature boundary

#520의 세 번째 repo-internal QA 패키지는 목표 잠금 생성/상세 화면을 DAO 직접 접근에서 분리했다.

#### 허용 경계

- `GoalLockRepository`가 goal-lock feature의 Room `GoalLockDao` 접근 허용 경계다.
- `GoalLockCreationViewModel`은 목표 잠금 입력/검증, 생성 analytics, 생성 side effect만 소유하고 저장은 repository에 위임한다.
- `GoalLockDetailViewModel`은 상세 state, 조기 종료/완료 analytics 정책만 소유하고 조회/업데이트 persistence mapping은 repository에 위임한다.

### Emergency unlock coordinator boundary

#520의 네 번째 repo-internal QA 패키지는 긴급해제 요청 오케스트레이션에서 Room DAO 직접 접근을 분리했다.

#### 허용 경계

- `EmergencyUnlockRepository`가 emergency-unlock service 경계의 Room `EmergencyUnlockDao` 접근 허용 경계다.
- `EmergencyUnlockCoordinator`는 settings read/sanitize, daily-limit 정책 순서, DataStore runtime state, analytics, `EmergencyUnlockState` 업데이트 순서만 소유한다.
- Block/Lock/Settings test fixture는 coordinator에 DAO를 직접 넘기지 않고 repository 경계를 통해 같은 production constructor contract를 검증한다.

### 회귀 방지

- `scripts.tests.test_dao_boundary_contract`는 `LockHistoryViewModel` / `BlockedAppsViewModel` 아래에서 `LockHistoryDao` 직접 import가 재도입되지 않는지 검사한다.
- 같은 static guard가 `ReviewEligibilityEvaluator` 아래에서 `EmergencyUnlockDao` / `LockHistoryDao` 직접 import가 재도입되지 않고 `ReviewEligibilityRepository`가 허용 DAO 경계로 남는지 검사한다.
- 같은 static guard가 `GoalLockCreationViewModel` / `GoalLockDetailViewModel` 아래에서 `GoalLockDao` 직접 import가 재도입되지 않고 `GoalLockRepository`가 허용 DAO 경계로 남는지 검사한다.
- 같은 static guard가 `EmergencyUnlockCoordinator` 아래에서 `EmergencyUnlockDao` 직접 import가 재도입되지 않고 `EmergencyUnlockRepository`가 허용 DAO 경계로 남는지 검사한다.
- `scripts.tests.test_dao_boundary_maintenance_docs`는 이 문서가 #520 인벤토리와 검증 명령을 계속 담는지 검사한다.

## 남은 인벤토리

아래 직접 DAO 의존은 아직 #520의 후속 패키지 대상이다. 이번 PR은 emergency-unlock coordinator persistence 경계까지 안전하게 닫고, Receiver/AccessibilityService/잠금 실행 경로는 별도 focused test와 runtime QA 범위로 다룬다.

- `HomeViewModel`: `GoalLockDao`, `LockHistoryDao`
- `LockViewModel`: `RoutineDao`, `LockHistoryDao`, `EmergencyUnlockDao`
- `MenuViewModel`: `RoutineDao`
- `RoutineBottomSheetViewModel`, `RoutineViewModel`, `RoutineRestoreAftercare`: `RoutineDao`
- `BootReceiver`, `RoutineAlarmReceiver`: `RoutineDao`
- `KeepAccessibilityService`: `RoutineDao`, `GoalLockDao`
- `LockHistoryLedger`: service 경계에서 DAO를 사용하므로 별도 저장소·정책 경계 판단이 필요하다.
- `ReviewEligibilityRepository`, `LockHistoryRepository`, `GoalLockRepository`, `EmergencyUnlockRepository`: 현재 허용된 repository DAO 경계다.

DB 모듈(`KeepDatabase`, `database/di`, DAO 인터페이스 자체)과 테스트 fake DAO는 이 인벤토리에서 제외한다.

## 검증 명령

```bash
python3 -m unittest scripts.tests.test_dao_boundary_contract -v
python3 -m unittest scripts.tests.test_dao_boundary_maintenance_docs -v
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.lockhistory.LockHistoryRepositoryTest' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryViewModelShareTest' --tests 'com.uiery.keep.feature.lockhistory.blockedapps.BlockedAppsViewModelAnalyticsTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.review.ReviewEligibilityEvaluatorTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.goallock.GoalLockCreationViewModelTest' --tests 'com.uiery.keep.feature.goallock.GoalLockDetailViewModelTest' --tests 'com.uiery.keep.feature.goallock.GoalLockPersistenceMapperTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.service.EmergencyUnlockCoordinatorTest' --tests 'com.uiery.keep.BlockViewModelTest' --tests 'com.uiery.keep.feature.lock.LockViewModelTest' --tests 'com.uiery.keep.feature.emergencyunlocksettings.EmergencyUnlockSettingsViewModelAnalyticsTest'
./gradlew --console=plain :app:testDevDebugUnitTest
```

Receiver/AccessibilityService 경계를 건드리는 후속 PR은 관련 `connectedDevDebugAndroidTest` 또는 release runtime smoke 명령을 PR body에 별도로 명시한다.
