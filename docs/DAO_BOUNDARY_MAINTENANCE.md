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

### Emergency unlock coordinator and lock-history recording boundary

#520의 네 번째 repo-internal QA 패키지는 긴급해제 요청 오케스트레이션과 완료된 잠금 세션 기록 경로에서 Room DAO 직접 접근을 분리했다.

#### 허용 경계

- `EmergencyUnlockRepository`가 emergency-unlock service 경계의 Room `EmergencyUnlockDao` 접근 허용 경계다.
- `EmergencyUnlockCoordinator`는 settings read/sanitize, daily-limit 정책 순서, DataStore runtime state, analytics, `EmergencyUnlockState` 업데이트 순서만 소유한다.
- Block/Lock/Settings test fixture는 coordinator에 DAO를 직접 넘기지 않고 repository 경계를 통해 같은 production constructor contract를 검증한다.
- `LockHistoryRepository`는 LockHistory 화면/read-model 조회의 Room `LockHistoryDao` 접근 허용 경계다.
- `LockHistorySessionWriter.recordSession(...)`이 완료된 Home/Lock 잠금 세션의 Room `LockHistoryDao` insert 허용 경계다.
- `LockHistoryRecorder`가 완료 세션 기록 orchestration의 허용 경계다. legacy DataStore summary cache 갱신과 `LockHistorySessionWriter.recordSession(...)` 호출 순서를 한곳에 모은다.
- `HomeViewModel`, `LockViewModel`은 session timing / review eligibility ordering만 소유하고, legacy cache와 Room entity 생성·insert는 recorder/repository 경계에 위임한다.
- `LockHistoryLedger`는 read-side 요약 전용 helper로 남아 완료 세션 저장 책임을 갖지 않는다.

### Menu routine read boundary

#520의 다섯 번째 repo-internal QA 패키지는 메뉴 화면의 현재 차단 상태 계산에서 `RoutineDao` 직접 접근을 분리했다.

#### 허용 경계

- `RoutineRepository`가 routine data boundary의 Room `RoutineDao` read 접근 허용 경계다.
- `MenuViewModel`은 수동 잠금/Keep 상태와 repository가 제공하는 routine domain model list만 조합해 `isBlocking`을 계산한다.
- `RoomRoutineRepository`는 Room entity → `RoutineModel` mapping을 소유하므로 메뉴 테스트 fixture는 DAO fake 대신 repository fake에 결합한다.

### Home `routines_count` analytics sync boundary — open #875

#875는 #520 closure audit 뒤에 새로 확인된 Home 진입 `routines_count` user property 동기화 예외다. PR #525로 coverage foothold가 들어오면서 Home 진입 사용자도 Routine 화면 없이 Room count를 GA4 user property에 set할 수 있게 됐지만, 현재 구현은 `HomeViewModel`과 `RoutineCountAnalyticsSync`가 `RoutineDao` / `RoutineEntity`에 직접 결합되어 있다.

#### 현재 상태

- `HomeViewModel`은 이미 `RoutineRepository`를 주입받으면서도 `RoutineDao`를 별도로 주입받아 Home init sync 경로에서 사용한다.
- `RoutineCountAnalyticsSync`는 analytics boundary인데 `RoutineDao.fetchAllOnce()`와 `syncFromRoutines(routines: List<RoutineEntity>)`를 직접 소유한다.
- 이 예외는 #479의 coverage/readback 경계와 다르다. #479는 `routines_count=(not set)` 감소를 release/readback으로 검증하는 지표 이슈이고, #875는 같은 coverage foothold를 유지하면서 DAO/Entity 결합을 repository/use-case 경계로 분리하는 maintenance 이슈다.

#### code-lane handoff

- `RoutineCountAnalyticsSync`는 `RoutineRepository`, 별도 `RoutineCountProvider`, 또는 count/domain-model 기반 use-case를 통해 숫자 count만 받아야 한다.
- `HomeViewModel`은 Home 상태/CTA/analytics orchestration만 소유하고 Room DAO/Entity import를 제거한다.
- `RoutineViewModel` collect 경로와 Splash restore-aftercare 경로는 기존 #479 coverage 의미를 유지해야 한다. 즉 DAO boundary cleanup이 `routines_count=0`, `>=1`, 삭제 후 감소, restore-aftercare count sync 회귀를 만들면 안 된다.
- focused 검증 후보: `RoutineCountAnalyticsSyncTest`, `HomeViewModelActivationAnalyticsTest.homeInitSyncsRoutinesCountFromRoomWithoutRoutineScreenEntry`, `SplashViewModelRestoreSchedulingTest.splashStartupReschedulesRestoredRoomRoutineBeforeOnboardingNavigation`, `scripts.tests.test_routines_count_coverage_contract`, 그리고 #875 후속으로 강화할 `scripts.tests.test_dao_boundary_contract`의 Home/RoutineCountAnalyticsSync DAO import guard.

### Lock routine read boundary

#520의 여섯 번째 repo-internal QA 패키지는 루틴 기반 Lock 화면의 활성 루틴 조회에서 `RoutineDao` 직접 접근을 분리했다.

#### 허용 경계

- `RoutineRepository`가 lock feature의 routine read 접근 허용 경계다.
- `LockViewModel`은 repository가 제공하는 `RoutineModel` list로 현재 활성 루틴 잠금 상태, 차단 앱 집합, 세션 anchor time만 계산한다.
- `LockViewModelTest`는 DAO fake 대신 repository fake에 결합해 Lock 화면 테스트가 Room entity mapping 세부사항을 다시 끌어오지 않도록 한다.

### Routine ViewModel persistence boundary

#520의 일곱 번째 repo-internal QA 패키지는 루틴 생성/수정/목록 ViewModel의 `RoutineDao` 직접 접근을 `RoutineRepository` mutation/read 경계로 분리했다.

#### 허용 경계

- `RoutineRepository`가 routine feature ViewModel의 Room `RoutineDao` read/mutation 접근 허용 경계다.
- `RoutineBottomSheetViewModel`은 루틴 입력 state, exact-alarm 사전 검증, schedule side effect, analytics만 소유하고 insert/update persistence mapping은 repository에 위임한다.
- `RoutineViewModel`은 상세 state, 삭제, enable toggle, restore aftercare orchestration, template share side effect만 소유하고 fetch/fetchAll/delete/updateEnabled persistence mapping은 repository에 위임한다.
- `RoutineRepositoryTest`는 insert/fetch/update/delete/updateEnabled DAO mapping을 고정하고, routine ViewModel test fixture는 `RoomRoutineRepository` 경계를 통해 production constructor contract를 검증한다.

### Routine restore aftercare boundary

#520의 여덟 번째 repo-internal QA 패키지는 restore-after-backup 후속 reschedule 경로의 `RoutineDao` 직접 접근을 `RoutineRepository` 경계로 분리했다.

#### 허용 경계

- `RoutineRepository`가 `RoutineRestoreAftercare`의 Room `RoutineDao` read/update 접근 허용 경계다.
- `RoutineRestoreAftercare`는 restored routine list의 reschedule orchestration, exact-alarm permission prompt reset, `RoutineStore` compatibility cache rewrite만 소유하고, Room entity 조회·enabled update mapping은 repository에 위임한다.
- `RoutineRepositoryTest`는 `fetchAllOnce()` mapping을 추가로 고정하고, Routine/Splash restore scheduling test fixture는 `RoomRoutineRepository` 경계를 통해 production constructor contract를 검증한다.

### Routine receiver restore/reschedule boundary

#520의 아홉 번째 repo-internal QA 패키지는 boot/package/time-change restore와 routine alarm receiver 경로의 `RoutineDao` 직접 접근을 `RoutineRepository` 경계로 분리했다.

#### 허용 경계

- `RoutineRepository`가 `BootReceiver` / `RoutineAlarmReceiver`의 Room `RoutineDao` read/update 접근 허용 경계다.
- `BootReceiver`는 boot/package/time-change action filtering, restore orchestration, exact-alarm schedule 결과 반영, `RoutineStore` compatibility cache rewrite만 소유하고 Room entity 조회·enabled update mapping은 repository에 위임한다.
- `RoutineAlarmReceiver`는 routine alarm trigger parsing, start notification/fallback notice, selected routine reschedule orchestration만 소유하고 Room entity 조회·enabled update mapping은 repository에 위임한다.
- Receiver runtime/androidTest fixtures는 직접 receiver field에 DAO를 넣지 않고 `RoomRoutineRepository` 경계를 통해 production constructor contract를 검증한다.

### Accessibility service runtime read boundary

#520의 열 번째 repo-internal QA 패키지는 AccessibilityService의 routine/goal-lock foreground blocking cache에서 `RoutineDao` / `GoalLockDao` 직접 접근을 분리했다.

#### 허용 경계

- `RoutineRepository`가 AccessibilityService의 routine read 접근 허용 경계다.
- `GoalLockRepository`가 AccessibilityService의 goal-lock read 접근 허용 경계다.
- `KeepAccessibilityService`는 repository가 제공하는 domain model stream만 캐시하고, foreground block decision / emergency-unlock expiry / uninstall prevention orchestration만 소유한다.

### Shared model mapper boundary — open #877

#877는 #520 계열 DAO boundary 정리 이후 남은 shared model 역의존 예외다. `RoutineModel` / `LockHistoryModel`은 feature, receiver, service가 공유하는 domain/UI model인데 Room `RoutineEntity` / `LockHistoryEntity` mapper를 직접 소유하면 shared model 계층이 persistence 구현에 역의존한다.

#### 허용 경계

- `database.mapper.RoutineEntityMapper`가 `RoutineEntity <-> RoutineModel` 변환을 소유한다.
- `database.mapper.LockHistoryEntityMapper`가 `LockHistoryEntity <-> LockHistoryModel` 변환을 소유한다.
- `app/src/main/java/com/uiery/keep/model/**`은 Room entity import 없이 순수 shared model만 정의한다.
- repository, receiver/androidTest, JVM fixture가 entity/model 변환이 필요하면 `database.mapper.*`를 명시적으로 import한다.

#### 검증 후보

- `python3 -m unittest scripts.tests.test_dao_boundary_contract -v`
- `./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.model.RoutineModelMappingTest' --tests 'com.uiery.keep.feature.routine.RoutineRepositoryTest' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryRepositoryTest'`
- `./gradlew --console=plain :app:compileDevDebugAndroidTestKotlin :app:assembleProdDebug`

### 회귀 방지

- `scripts.tests.test_dao_boundary_contract`는 `LockHistoryViewModel` / `BlockedAppsViewModel` 아래에서 `LockHistoryDao` 직접 import가 재도입되지 않는지 검사한다.
- 같은 static guard가 `ReviewEligibilityEvaluator` 아래에서 `EmergencyUnlockDao` / `LockHistoryDao` 직접 import가 재도입되지 않고 `ReviewEligibilityRepository`가 허용 DAO 경계로 남는지 검사한다.
- 같은 static guard가 `GoalLockCreationViewModel` / `GoalLockDetailViewModel` 아래에서 `GoalLockDao` 직접 import가 재도입되지 않고 `GoalLockRepository`가 허용 DAO 경계로 남는지 검사한다.
- 같은 static guard가 `EmergencyUnlockCoordinator` 아래에서 `EmergencyUnlockDao` 직접 import가 재도입되지 않고 `EmergencyUnlockRepository`가 허용 DAO 경계로 남는지 검사한다.
- 같은 static guard가 `LockHistoryRecorder` 아래에서 `LockHistoryDao` / `LockHistoryEntity` / feature-private `LockHistoryRepository` 직접 import가 재도입되지 않고 `LockHistorySessionWriter.recordSession(...)`이 완료 세션 저장 허용 경계로 남는지 검사한다. `LockHistoryRepository`는 feature read-model 조회 경계로, `LockHistoryLedger`는 read-side summary helper로만 남는지도 함께 검사한다.
- 같은 static guard가 `MenuViewModel` 아래에서 `RoutineDao` / `RoutineEntity` 직접 import가 재도입되지 않고 `RoutineRepository`가 menu routine read 허용 경계로 남는지 검사한다.
- #875 code-lane cleanup 전까지 `HomeViewModel` / `RoutineCountAnalyticsSync`의 Routine DAO/Entity 직접 결합은 open exception으로 문서화한다. cleanup PR은 이 예외를 제거하고 `scripts.tests.test_dao_boundary_contract`에 Home/RoutineCountAnalyticsSync guard를 추가해야 한다.
- 같은 static guard가 `LockViewModel` 아래에서 `RoutineDao` / `RoutineEntity` / stale emergency-unlock DAO/entity import가 재도입되지 않고 `RoutineRepository`가 lock routine read 허용 경계로 남는지 검사한다.
- 같은 static guard가 routine feature non-repository source 아래에서 `RoutineDao` 직접 import가 재도입되지 않고 `RoutineRepository`가 routine read/mutation/restore-aftercare 허용 경계로 남는지 검사한다.
- 같은 static guard가 routine Receiver 아래에서 `RoutineDao` 직접 import가 재도입되지 않고 `RoutineRepository`가 boot/alarm receiver routine read/mutation 허용 경계로 남는지 검사한다.
- 같은 static guard가 `KeepAccessibilityService` 아래에서 `RoutineDao` / `GoalLockDao` 직접 import가 재도입되지 않고 `RoutineRepository` / `GoalLockRepository`가 accessibility foreground cache 허용 경계로 남는지 검사한다.
- 같은 static guard가 `app/src/main/java/com/uiery/keep/model/**` 아래에서 Room entity import가 재도입되지 않고 `database.mapper.*`가 entity/model 변환 허용 경계로 남는지 검사한다.
- `scripts.tests.test_dao_boundary_maintenance_docs`는 이 문서가 #520 인벤토리와 검증 명령을 계속 담는지 검사한다.

## Closure audit

Fresh `origin/develop` 기준 #520 repo-internal DAO boundary package is complete for the original #520 inventory, but #875 reopened a narrower Home analytics sync exception. `app/src/main`의 Receiver / Service / AccessibilityService 경로에서 직접 DAO import는 더 이상 발견되지 않았고, 기존 #520 대상 ViewModel 경계(Menu/Lock/Routine/GoalLock/LockHistory/Review)는 repository boundary로 정리되어 있다. 다만 Home `routines_count` coverage foothold에는 아래 open exception이 남아 있다.

- 허용 repository DAO 경계: `ReviewEligibilityRepository`, `LockHistoryRepository`, `GoalLockRepository`, `EmergencyUnlockRepository`, `RoutineRepository`.
- 허용 DB wiring 경계: `KeepDatabase`, `database/di`, DAO 인터페이스 자체.
- Open #875 exception: `HomeViewModel` / `RoutineCountAnalyticsSync`의 Routine count analytics sync 경로는 coverage 보강을 위해 Room DAO/Entity에 직접 결합되어 있으며, 다음 code-lane package에서 repository/count-provider/use-case 경계로 분리해야 한다.
- 테스트 fixture/fake DAO는 production main-source 인벤토리에서 제외한다.

future regression 발견 시에는 새 직접 DAO import를 이 문서의 허용 경계에 추가하지 말고, 해당 ViewModel/Receiver/Service를 repository/read-model 경계로 되돌리는 focused test 패키지로 처리한다. #875 예외도 allowlist로 장기 보존하지 말고 제거 기준과 회귀 guard를 함께 추가한다.

## 검증 명령

```bash
python3 -m unittest scripts.tests.test_dao_boundary_contract -v
python3 -m unittest scripts.tests.test_dao_boundary_maintenance_docs -v
python3 -m unittest scripts.tests.test_routines_count_coverage_contract -v
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.lockhistory.LockHistoryRepositoryTest' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryViewModelShareTest' --tests 'com.uiery.keep.feature.lockhistory.blockedapps.BlockedAppsViewModelAnalyticsTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.review.ReviewEligibilityEvaluatorTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.goallock.GoalLockCreationViewModelTest' --tests 'com.uiery.keep.feature.goallock.GoalLockDetailViewModelTest' --tests 'com.uiery.keep.feature.goallock.GoalLockPersistenceMapperTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.service.LockHistoryRecorderTest' --tests 'com.uiery.keep.service.LockHistoryLedgerTest' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryRepositoryTest' --tests 'com.uiery.keep.feature.lock.LockViewModelTest' --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest' --tests 'com.uiery.keep.feature.home.HomeViewModelReviewTest' --tests 'com.uiery.keep.feature.home.HomeViewModelRoutineStartNoticeTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.lock.LockViewModelTest' --tests 'com.uiery.keep.feature.routine.RoutineRepositoryTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.routine.*'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.routine.RoutineRepositoryTest' --tests 'com.uiery.keep.feature.routine.RoutineViewModelRestoreSchedulingTest' --tests 'com.uiery.keep.feature.routine.RoutineViewModelTemplateShareTest' --tests 'com.uiery.keep.feature.splash.SplashViewModelAnalyticsTest' --tests 'com.uiery.keep.feature.splash.SplashViewModelRestoreSchedulingTest'
./gradlew --console=plain :app:compileDevDebugAndroidTestKotlin
./gradlew --console=plain :app:testDevDebugUnitTest
```

Receiver/AccessibilityService 경계를 건드리는 후속 PR은 관련 `connectedDevDebugAndroidTest` 또는 release runtime smoke 명령을 PR body에 별도로 명시한다.
