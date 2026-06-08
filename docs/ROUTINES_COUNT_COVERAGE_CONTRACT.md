# routines_count user property 커버리지 보강 계약

Issue: #479
상태: **code-lane 중앙 sync 구현 PR 준비 / release·Play deploy·D+14·D+30 readback 전**

이 문서는 `customUser:routines_count`가 조회 가능하다는 사실과 실제 사용자 커버리지가 충분하다는 사실을 분리하기 위한 source of truth다. 2026-06-03 루틴 보유/미보유 코호트 기준선에서 `routines_count=(not set)` activeUsers가 가장 큰 그룹으로 남았기 때문에, #455 루틴 생성 CTA, #407 루틴 템플릿 공유, 루틴 retention 판단은 user property set 시점 보강 전까지 낮은 confidence로 둔다.

## 현재 근거

- 기준선 문서: `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`
- 분석 기간: GA4 `30daysAgo..yesterday`
- 조회 시각: `2026-06-03T09:12:01Z` live readback
- GA4 property: `properties/502544175`
- 주요 차원: `customUser:routines_count`

| 코호트 | activeUsers | 전체 루틴 property cohort 내 비중 | 해석 |
| --- | ---: | ---: | --- |
| 루틴 상태 미확인 (`(not set)` / blank) | 560 | `560 / 865 = 64.7%` | 가장 큰 그룹. Routine 화면 미진입, 구버전, 앱 시작/Home 진입 시 미동기화, 복원/boot rehydrate 후 미갱신 가능성을 분리해야 한다. |
| 루틴 미보유 (`0`) | 155 | `155 / 865 = 17.9%` | 루틴 0개로 명시된 사용자. #455 CTA의 안전한 후보 분모지만 `(not set)` 사용자와 섞으면 안 된다. |
| 루틴 보유 (`>=1`) | 150 | `150 / 865 = 17.3%` | 루틴 1개 이상이 GA4 user property에 반영된 사용자. 반복 사용 신호가 강하지만 전체 사용자 결론으로 일반화하지 않는다. |

현재 구현 PR은 `KeepAnalyticsUserProperty.ROUTINES_COUNT`와 `RoutineCountAnalyticsSync`를 중앙 owner로 두고, `RoutineViewModel`의 루틴 목록 collect 경로와 Home 진입 경로가 모두 같은 sync helper를 호출한다. 또한 Splash restore-aftercare 경로가 Room 루틴 재수화/재스케줄 직후 같은 `KeepAnalytics.setRoutinesCount(...)` API로 restored count를 set한다. 즉 Routine 화면/ViewModel만 거치지 않은 active user도 앱 시작/Home 진입 시 Room count 기반으로 `0` 또는 실제 루틴 수를 명시적으로 set하도록 보강했다.

## 문제 정의

`routines_count`는 이미 GA4 metadata에서 보이는 `customUser:*` 축이지만, 커버리지가 낮으면 다음 제품 판단이 모두 흔들린다.

1. 루틴 보유자의 반복 사용 강도가 높다는 신호는 유망하지만, `(not set)` 64.7%가 남으면 전체 retention 결론은 보류해야 한다.
2. #455 루틴 생성 CTA는 루틴 0개 사용자를 대상으로 해야 하지만, `0`과 `(not set)`을 같은 무루틴 사용자로 취급하면 onboarding/pre-first-lock 사용자에게 CTA가 과노출될 수 있다.
3. #407 루틴 템플릿 공유나 향후 루틴 추천/Usage Access 판단은 루틴 보유 cohort를 분모로 쓰므로 user property coverage 개선 포함 버전의 release/adoption/readback을 분리해야 한다.

## 목표

`routines_count` 설정 책임을 Routine 화면 진입 부수효과에서 앱/루틴 상태 동기화 계약으로 승격한다.

### 필수 구현 계약

- `KeepAnalytics` 계층에 `routines_count` 설정 API 또는 상수를 둔다. 새 코드에서 raw string `"routines_count"`를 직접 흩뿌리지 않는다.
- 앱 실행, Splash/Home 진입 또는 공통 analytics sync 경로에서 Room routine count를 읽어 `routines_count=0` 또는 실제 루틴 수를 명시적으로 설정한다.
- 루틴 생성/수정/삭제 후 최신 count를 다시 설정한다.
- backup/restore 또는 boot rehydrate 이후 Room routine이 복원된 경우 user property가 실제 Room count와 맞도록 동기화한다.
- user property에는 루틴 이름, 앱 package/name, `lockApplications`, raw schedule/history를 절대 넣지 않는다. 값은 숫자 count만 허용한다.

### 권장 구현 위치

- Routine 화면 전용 `RoutineViewModel`은 여전히 화면 상태를 반영할 수 있지만, **유일한 owner가 되어서는 안 된다**.
- 공통 owner 후보:
  - 앱 시작/Home/Splash 진입 시 한 번 실행되는 analytics user-property sync use-case
  - Room routine repository 변경 후 호출되는 sync helper
  - boot/restore rehydrate 완료 후 호출되는 receiver/service-adjacent sync path
- `RoutineViewModel` 직접 호출을 유지하더라도 `KeepAnalytics.setRoutinesCount(count)` 같은 중앙 API를 통해 호출한다.

## 테스트 / 회귀 방지 계약

code-lane 구현 PR은 최소한 아래를 증명한다.

- 루틴 0개 사용자가 Routine 화면에 들어가지 않아도 앱 실행 또는 Home 진입 후 `routines_count=0`이 설정된다.
- 루틴 1개 이상 사용자는 Room count가 user property 값으로 설정된다.
- 루틴 삭제 후 count 감소가 반영된다.
- 복원/boot rehydrate 후 Room routine count가 user property와 일치한다.
- 새 `"routines_count"` raw string 호출은 `KeepAnalytics`/상수 계층 밖에 추가되지 않는다.

권장 검증 명령:

```bash
./gradlew --console=plain :app:testDevDebugUnitTest --tests '*Routine*Analytics*' --tests '*RoutinesCount*'
./gradlew --console=plain :app:testDevDebugUnitTest
python3 -m unittest scripts.tests.test_routines_count_coverage_contract -v
git diff --check
```

정확한 JVM test class 이름은 구현 PR에서 만든 계약 테스트 이름에 맞춘다. flavorless `testDebugUnitTest`는 사용하지 않는다.

## 현재 구현 PR의 repo-internal 완료 범위

- `app/src/main/java/com/uiery/keep/analytics/RoutineCountAnalyticsSync.kt`가 `routines_count` raw string을 중앙 상수로 고정하고 `RoutineDao.fetchAllOnce()` 또는 이미 collect된 `RoutineEntity` 목록에서 숫자 count만 set한다.
- `HomeViewModel` init 경로가 Room count를 읽어 Home 진입 사용자에게 `routines_count=0` 또는 실제 count를 set한다.
- `SplashViewModel` restore-aftercare 경로가 Room routine 재수화/재스케줄 직후 restored routine count를 같은 중앙 API로 set한다.
- `RoutineViewModel` collect 경로가 루틴 생성/수정/삭제/restore-aftercare 반영 이후 같은 중앙 sync helper로 count를 재설정한다.
- focused JVM regression은 `RoutineCountAnalyticsSyncTest`에서 `0`, `>=1`, 삭제 후 감소를 고정하고, `HomeViewModelActivationAnalyticsTest.homeInitSyncsRoutinesCountFromRoomWithoutRoutineScreenEntry`가 Routine 화면 진입 없이 Home init에서 Room count를 set하는 경로를 고정한다. `SplashViewModelRestoreSchedulingTest.splashStartupReschedulesRestoredRoomRoutineBeforeOnboardingNavigation`는 restore-aftercare 재스케줄 직후 restored count가 set되는 경로를 고정한다.

남은 경계는 코드가 `origin/main`/SemVer tag/Play deploy에 포함된 뒤 최신 production version adoption을 확인하고 D+14/D+30 GA4 readback으로 `(not set)` 감소를 검증하는 것이다.

## GA4 readback 계약

배포 후 판단은 “코드가 develop에 있다”가 아니라 아래 경계를 지난 뒤 시작한다.

1. `routines_count` coverage 개선 PR이 `origin/main`에 포함된다.
2. 해당 commit이 SemVer tag와 Play deploy에 포함된다.
3. 최신 production version active share가 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 `충분` 또는 최소 `주의`로 해석 가능한 수준인지 기록한다.
4. D+14/D+30 window에서 같은 쿼리를 재조회한다.

필수 readback 표:

| 지표 | 분자/분모 |
| --- | --- |
| `(not set)` activeUsers 비중 | `(not set)` activeUsers / (`0` + `>=1` + `(not set)` activeUsers) |
| 명시 루틴 0 사용자 비중 | `routines_count=0` activeUsers / 전체 루틴 property cohort activeUsers |
| 루틴 보유 사용자 비중 | `routines_count>=1` activeUsers / 전체 루틴 property cohort activeUsers |
| 루틴 보유 반복 사용 강도 | `sessions / activeUsers`, `app_block_intercepted users / activeUsers`, `app_block_intercepted eventCount / blocked users` |
| guardrail | `emergency_unlock_completed users / active blocked users`, `app_exception users / activeUsers`, Crashlytics crash-free users, Play Console rating/review |

성공 신호 예시:

- `(not set)` activeUsers 비중이 2026-06-03 기준 `560 / 865 = 64.7%`에서 유의미하게 감소한다.
- `0`과 `>=1` cohort 규모가 늘어도 crash-free users, review/rating, emergency unlock guardrail이 악화되지 않는다.
- #455/#407 실험 해석에서 “루틴 상태 확인 사용자” 분모와 “전체 active user” 분모를 별도로 보고한다.

## 연결 문서 / 이슈

- GitHub issue: #479
- `docs/ANALYTICS_EVENT_DICTIONARY.md`: `routines_count` user property 의미와 source-of-truth 표
- `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`: 2026-06-03 루틴 보유/미보유 기준선과 재측정 표
- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`: #455 CTA 대상/제외 조건
- `docs/PRODUCT_METRICS_DASHBOARD.md`: 루틴 input metric과 retention/growth 판단
- `docs/METRICS_ANALYSIS.md`: GA4 재조회 운영 절차
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: GA4 metadata/readback 경계
- `docs/ops/stopit/metrics-context.md`: cron/subagent용 장기 지표 맥락

## PR/이슈 연결 규칙

이 code-lane 구현 PR은 repo-internal 중앙 sync 계약을 고정하므로 PR body는 `Refs #479`를 사용한다. #479를 닫으려면 PR merge 후 release/tag/Play deploy 포함 여부, 최신 production version adoption, D+14/D+30 readback에서 `(not set)` 감소와 guardrail 이상 없음이 확인되어야 한다.
