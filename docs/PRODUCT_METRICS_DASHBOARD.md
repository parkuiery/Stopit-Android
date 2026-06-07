# 스탑잇 제품 지표 대시보드 정의

이 문서는 `pm-skills`의 metrics dashboard, cohort analysis, prioritization, monetization, growth loop 프레임워크를 스탑잇 운영 방식에 맞게 흡수한 제품 지표 정의서다.

첫 잠금 활성화 퍼널의 단계 의미, CTA 계약, legacy 이벤트명 정리는 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 source of truth로 본다. 홈 화면 상태/CTA 구조 개선은 `docs/HOME_STATUS_CTA_STRUCTURE.md`(#463)를 source of truth로 보고, `first_lock_configured`를 실제 차단 완료로 과장하지 않은 채 꺼짐/켜짐/타이머/목표 잠금/선택 앱 없음 상태와 단일 primary CTA 위계를 구현 전 계약으로 고정한다. 루틴 보유/미보유 반복 사용 코호트 기준선은 `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`를 source of truth로 본다. `routines_count` user property coverage 보강 계약은 `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`(#479)를 source of truth로 보고, `(not set)` coverage가 큰 동안에는 루틴 보유/미보유 retention 결론을 낮은 confidence로 둔다. 첫 차단 성공 이후 루틴 0개 사용자 대상 루틴 생성 CTA 실험은 `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`(#455)를 source of truth로 본다. 반복 차단 패턴 기반 자동 루틴 제안은 `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`(#531)를 source of truth로 보고, 기존 차단 기록의 privacy-safe bucket에서 로컬 추천 후보를 만들되 앱 이름/package/raw history를 지표 축으로 쓰지 않는다. 차단 화면 카피/액션 위계 개선은 `docs/BLOCK_SCREEN_COPY_HIERARCHY.md`(#464)를 source of truth로 보며, PR #487(`8fb1911c`)로 BlockScreen copy/helper 구현과 locale parity는 `develop`에 반영됐지만 실제 기기/screenshot/TalkBack QA, release/tag/Play deploy, 14일 readback 전까지는 live 성과 판단을 보류한다. 긴급해제 flow copy/step 개선은 `docs/EMERGENCY_UNLOCK_FLOW_COPY.md`(#467)를 source of truth로 보며, PR #517(`572eb559`)로 helper/validation copy와 locale parity가 `develop`에 반영됐고 PR #575(`1a7c677`)로 reason-required ON/OFF Compose UI flow baseline도 `develop`에 반영됐지만 기존 `emergency_unlock_completed.reason` enum key와 privacy-safe payload 경계를 유지하고 실제 기기/screenshot/TalkBack spot-check, release/tag/Play deploy, 14일 readback 전까지는 live 성과 판단을 보류한다. 루틴 템플릿 공유 루프의 privacy-safe MVP 계약은 `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`(#407)를 source of truth로 본다. LockHistory 성과 리포트 UX 계약은 `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`(#465)를 source of truth로 보고, 개인 성과 해석/재방문 동기는 공유·리뷰·광고 CTA와 분리해 판단한다. 목표 잠금 MVP 계약은 `docs/GOAL_LOCK_MVP.md`(#417)를 source of truth로 보고, policy/persistence/creation UI/navigation/Home/Accessibility blocking/detail/early-end/Home completion foothold는 `develop` 반영 상태로 해석하되 장기 잠금 지표는 release/GA4 Admin 등록/readback 이후에만 해석한다. 부모 모드 / 아이에게 폰 주기 MVP 계약은 `docs/PARENT_MODE_MVP.md`(#471)를 source of truth로 보고, same-device 부모 PIN flow와 privacy-safe analytics/QA 경계를 구현 전 handoff로 고정한다. 최신 코드가 live 지표에 반영됐는지 판정할 때는 `docs/VERSION_ADOPTION_METRICS_GATE.md`의 버전 채택률/최신 버전 cohort 게이트를 먼저 적용한다.

## 목적

스탑잇의 지표 관리는 “많이 쓰는가?”보다 “사용자가 실제로 앱 차단/집중 가치를 얻었는가?”를 중심으로 본다.

따라서 대시보드는 다음 질문에 답해야 한다.

1. 신규 사용자가 첫 가치를 경험하는가?
2. 사용자가 반복적으로 차단/집중 기능을 쓰는가?
3. 루틴과 긴급해제는 건강하게 작동하는가?
4. 광고/수익화가 제품 신뢰와 유지율을 해치지 않는가?
5. Play Store 유입과 리뷰 신뢰가 개선되고 있는가?

## North Star Metric 후보

### 추천 NSM

`주간 활성 차단 사용자 수`

정의:

- 최근 7일 동안 `app_block_intercepted`가 1회 이상 발생한 고유 사용자 수.

이유:

- 단순 실행/방문이 아니라 실제 차단 가치가 발생한 사용자다.
- 스탑잇의 핵심 약속인 “앱 사용을 막아 집중을 돕는다”와 직접 연결된다.
- 신규 유입, 권한 설정, 앱 선택, 첫 잠금 설정, 반복 사용이 모두 이 지표를 밀어 올린다.

주의:

- 접근성 서비스가 오작동해서 차단이 과도하게 발생하면 좋은 신호가 아니다.
- 사용자 불편/긴급해제 과다와 함께 해석해야 한다.

### 보조 NSM 후보

1. `주간 성공 집중 세션 사용자 수`
   - `lock_session_start`와 `lock_session_end`가 정상적으로 이어진 사용자.
   - 장점: 세션 단위 완결성을 본다.
   - 단점: 현재 이벤트 완결성과 세션 매칭이 충분한지 확인 필요.

2. `주간 활성 루틴 사용자 수`
   - 루틴 기반 차단 또는 루틴 수가 1개 이상인 주간 활성 사용자.
   - 장점: 반복 사용과 retention에 가깝다.
   - 단점: 신규 활성화보다 후행 지표다.

## 대시보드 레이어

| 레이어 | 지표 | 정의 | 데이터 소스 | 해석 |
|---|---|---|---|---|
| North Star | 주간 활성 차단 사용자 | 7일 내 `app_block_intercepted` 1회 이상 사용자 | GA4/Firebase | 핵심 가치 전달 규모 |
| Input | 첫 잠금 설정률 | `first_lock_configured` users / `first_open` users | GA4 | 신규 활성화 병목. 온보딩/홈 출처 모두 `selected_app_count >= 1` 이후만 유효 |
| Input | 첫 핵심 행동 완료율 | `first_core_action_completed` users / `first_open` users | GA4 | 첫 가치 경험률 |
| Input | 앱 선택 완료율 | `app_selection_completed` users / `first_open` users | GA4 | 온보딩 중간 전환. `selected_app_count >= 1` 계약을 전제로 해석 |
| Input | 루틴 생성 사용자 비율 | `routines_count >= 1` users / active users | GA4 customUser | 반복 사용 기반. #479 완료 전에는 `(not set)` activeUsers를 별도 coverage gap으로 분리하고 `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`의 release/readback gate를 따른다 |
| Input | 첫 차단 후 루틴 CTA 전환 | `routine_creation_cta_clicked` users / `routine_creation_cta_shown` users, `routine_created` users / clicked users | GA4 customEvent + customUser | #455 soft CTA 실험. `first_core_action_completed` 이후 + 루틴 0개 사용자만 분모로 해석 |
| Input | 부모 모드 시작 전환 | `parent_mode_started` users / `parent_mode_duration_selected` users, `parent_mode_started` users / `parent_mode_allowed_apps_selected` users | GA4 customEvent | #471 same-device 부모 모드 setup 완주. 보호자 PIN/허용 앱 선택 후 시작만 유효 |
| Input | 차단 빈도 | `app_block_intercepted` / active blocked users | GA4 | 실제 사용 강도 |
| Health | Crash-free users rate | crash-free users / active users | GA4/Crashlytics | 안정성 |
| Health | 긴급해제 사용률 | `emergency_unlock_completed` users / active blocked users | GA4 | 차단 강도/사용자 부담 |
| Health | 리뷰 평점/리뷰 수 | Play Store 평점 및 rating count | Play Console | 신뢰와 전환율 |
| Health | 화면명 미설정 비율 | `(not set)` screen views / total screen views | GA4 | 분석 가능성 |
| Business | 광고 ARPU | `totalAdRevenue` / active users | GA4/AdMob | 사용자당 광고 수익 |
| Business | 광고 eCPM | `totalAdRevenue` / impressions × 1000 | GA4/AdMob | 광고 효율 |
| Business | 광고 CTR | clicks / impressions | GA4/AdMob | 광고 반응 |
| Acquisition | 신규 사용자 | `newUsers` | GA4 | 성장 흐름 |
| Acquisition | Organic Search 신규 사용자 | `newUsers` by `firstUserDefaultChannelGroup` + Play Console Search/Explore | GA4 + Play Console | ASO 효과. Direct/Paid Search mix가 흔들리면 `docs/PLAY_STORE_ASO.md`의 #242 attribution gate를 먼저 적용하고, #581 `docs/INSTALL_REFERRER_ATTRIBUTION_CONTRACT.md`의 UTM/Install Referrer/외부 링크 기록 여부를 확인 |
| Quality gate | 최신 버전 active share | 최신 배포 버전 `activeUsers` / 전체 `activeUsers` | GA4 `appVersion` | #359 판독 게이트. 10% 미만이면 최신 코드 성과 판단 보류, 10~30%는 주의, 30% 이상이면 최신 cohort 결론 사용 가능 |

## 현재 기준선

### 2026-05-23 최근 30일 제품/비즈니스 기준선

| 지표 | 값 |
|---|---:|
| active users | 456 |
| total users | 507 |
| new users | 202 |
| sessions | 4,681 |
| engagement rate | 61.4% |
| total ad revenue | $2.168155 |
| publisher ad impressions | 18,731 |
| publisher ad clicks | 12 |
| `first_open` users | 201 |
| `first_lock_configured` users | 41 |
| `app_block_intercepted` users | 121 |

### 2026-05-29 최근 14일 screen 품질 / queryability 기준선

| 지표 | 값 |
|---|---:|
| total `screen_view` | 13,154 |
| `(not set)` `unifiedScreenName` | 9,473 |
| blank `unifiedScreenName` | 801 |
| combined screen quality gap | `10,274 / 13,154 = 78.1%` |
| 2026-05-29 metadata에서 확인된 custom dimension | `customUser:routines_count` |
| 2026-05-29 metadata에서 확인된 `customEvent:*` | 없음 |
| 2026-06-01 광고 metadata 보정 | `ad_unit_id`, `ad_placement`, `screen_context`, `ad_format`, `ad_value_micros`, `screen_name` 등록 확인 |

대표 해석:

- 대시보드의 오래된 `screen views 23,191 / (not set) 19,003` 표만 보고 현재 screen 품질을 판단하면 안 된다.
- 2026-05-29 `78.1%` screen 품질 gap은 PR #296의 `SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen` 및 PR #318의 dev/debug `DevToolScreen` 명시적 `screen_view` 보강 전 baseline이다. 같은 화면에 대한 추가 코드 작업은 PR #296/#318 포함 버전 배포 후 14일 재측정으로 실제 잔여 gap을 확인한 뒤 판단한다. `DevToolScreen`은 production 사용자 지표와 분리해서 해석한다.
- 2026-06-03 09:12 KST live smoke에서는 최근 14일 `screen_view`가 `22,584`, `(not set)` `11,793`, blank `1,987`, combined `13,780 / 22,584 = 61.0%`로 재조회됐다. 단 PR #296(`47e43784...`)과 PR #318(`8d2ee10...`)은 `origin/develop`에는 있지만 `origin/main`/production tag `v1.7.7`에는 없으므로, 이 값은 post-fix 14일 성과가 아니라 **release boundary 전 중간 smoke**로만 본다.
- 2026-05-29 live smoke 기준 당시 병목은 단순 no-data가 아니라 **GA4 Admin 미등록으로 인한 queryability gap**이었다.
- activation (`customEvent:permission_name`, `customEvent:source`) 분해 쿼리는 아직 별도 metadata 확인 전까지 낮은 confidence로 둔다. review `customEvent:reason`은 2026-06-02T18:06:45Z #307 재조회에서 등록/조회 가능해졌으므로 `review_prompt_skipped` reason breakdown에 사용할 수 있다. 단, `customEvent:error`는 여전히 미등록이다. 2026-06-04T21:24:42Z repo ancestry 재확인 기준 PR #308/#312는 아직 `origin/main` `20b8ff4a`와 최신 tag `v1.7.7` `f49e7de9`에 포함되지 않았으므로, 현재 `review_prompt_shown = 0`은 post-PR-308/#312 성과로 판독하지 않는다.
- 2026-06-01 #16 preflight에서 광고 custom metadata는 일부 복구 확인됐고, 이후 PR #293에서 앱 소유 배너 이벤트가 `ad_banner_impression` / `ad_banner_click` / `ad_banner_revenue`로 분리됐다. 다만 PR #293 포함 commit이 `main`/SemVer tag/Play deploy에 실제 포함된 뒤 14일 coverage 재조회 전까지 placement별 monetization 결론은 계속 낮은 confidence로 둔다. 2026-06-02/2026-06-03 확인 기준 최신 production tag `v1.7.7`과 현재 `origin/main`은 PR #293 split commit을 포함하지 않으므로, `v1.7.7` 광고 데이터는 post-split measurement가 아니라 legacy baseline이다. 2026-06-03 GA4 smoke에서 소량의 `ad_banner_*` 행이 보인 것은 source-split queryability 확인용이며, production 14일 placement 판단으로 승격하지 않는다. PR #402 CTA merge commit `de142bd34a2729bcbb1e932db70b34d6459ce3b0`도 2026-06-04 확인 기준 `origin/develop`에는 있지만 `origin/main`에는 없으므로, 수익화 관심도 CTA 이벤트 0건은 수요 없음이 아니라 release-boundary 전 상태로 해석한다. PR #461 merge commit `e6d4d70ada739c545672e95950fb6f82409fd10f`로 banner placement metadata source가 `AdPlacement.toMetadata(...)`에 중앙화됐지만, 2026-06-05 ancestry 확인 기준 `origin/main`/최신 production tag `v1.7.7`에는 없으므로 이 또한 release/tag/Play deploy 포함 전에는 post-fix placement measurement로 보지 않는다.

주의: 이 기준선은 고정값이 아니라 live snapshot이다. 다음 분석 시 GA4에서 다시 조회해 갱신한다.

## 핵심 퍼널

### 활성화 퍼널

1. `first_open`
2. `onboarding_step_view` / `onboarding_step_complete`
3. `permission_outcome`
4. `app_selection_completed`
5. `first_lock_configured`
6. `first_core_action_completed`
7. `app_block_intercepted`

분석 규칙:

- 버전별 이벤트 도입 시점이 다르면 전체 30일 합산 퍼널을 그대로 믿지 않는다.
- `appVersion`으로 나눠 이벤트 의미가 동일한 구간만 비교한다.
- 전환율은 항상 분자/분모를 같이 기록한다.

### 유지/반복 사용 퍼널

1. 첫 차단 성공
2. 다음날 재방문 또는 재차단
3. 7일 내 2회 이상 차단
4. 루틴 1개 이상 생성
5. 30일 내 반복 사용

권장 코호트:

- 설치 주차별 D1/D7/D30 유지율
- 첫 차단 성공 사용자 vs 미성공 사용자
- 루틴 생성 사용자 vs 미생성 사용자
- Organic Search 유입 vs Direct 유입
- appVersion별 활성화/유지율

## 우선순위 점수표

지표 기반 이슈는 기본적으로 ICE로 점수화한다.

> 아래 ICE 표는 2026-05-23/초기 지표 기반 historical prioritization이다. 현재 실행 상태는 각 issue runbook의 release/manual boundary를 우선한다. 특히 #65는 ASO 초안 부재가 아니라 Play Console 수동 반영 후 attribution/14일·30일 검증 단계이고, #16은 PR #293 포함 production release 후 14일 coverage 재조회와 PR #362 관심도 CTA의 GA4 Admin/metadata 확인 전까지 실험 결론을 보류한다.

| 항목 | Impact | Confidence | Ease | ICE | 근거 |
|---|---:|---:|---:|---:|---|
| GA4 계측 품질 개선 | 9 | 9 | 7 | 567 | 화면명 대부분 `(not set)`, 커스텀 차원 부족 |
| 첫 잠금 활성화 개선 | 9 | 7 | 6 | 378 | first_open 201명 대비 first_lock_configured 41명 |
| Play Store ASO 개선 | 8 | 8 | 7 | 448 | 신규 사용자 직전 30일 대비 -47%, Organic Search 의존 |
| 리뷰 프롬프트 개선 | 7 | 6 | 7 | 294 | 사용 신호 대비 리뷰 수 작음 |
| 광고 수익화 실험 | 6 | 7 | 5 | 210 | ARPU/eCPM 낮음, UX 리스크 존재 |

해석:

- 단기 실행은 `GA4 계측 품질 개선`과 `Play Store ASO 개선`이 가장 안전하다.
- `첫 잠금 활성화 개선`은 임팩트가 크지만 계측 정리 후 더 정확히 설계하는 편이 좋다.
- `광고 수익화`는 제품 신뢰/유지율 guardrail을 먼저 정해야 한다.
- 현재 #13의 docs/ops scope는 이벤트 계약 정의만이 아니라, `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`에 정리된 GA4 Admin 등록 ledger와 metadata 증적 포맷까지 포함한다.
- 2026-05-29 live smoke에서 activation/review/monetization `customEvent:*` 분해 쿼리가 모두 `400 INVALID_ARGUMENT` / `not a valid dimension`으로 실패했고, 2026-06-01 #16 preflight에서 광고 metadata가 일부 복구 확인됐다. 2026-06-02T18:06:45Z #307 재조회에서는 review `customEvent:reason`도 등록/조회 가능했다. 따라서 현재 #14류 activation 세부 파라미터와 review `customEvent:error`는 **GA4 Admin 미등록 queryability gap**, #16류 monetization은 **event-source split / coverage gap** 때문에 confidence가 낮다. review skip reason 자체는 이제 live breakdown으로 판단할 수 있다.
- 2026-05-29 `screen_view` gap `10,274 / 13,154 = 78.1%`는 PR #296의 `SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen` 및 PR #318의 dev/debug `DevToolScreen` 보강 전 baseline이다. #13의 다음 screen 품질 판단은 PR #296/#318 포함 버전 배포 후 14일 재측정 결과로 한다.
- 2026-06-03 screen 품질 smoke는 `13,780 / 22,584 = 61.0%`로 개선처럼 보이지만, PR #296/#318이 아직 `origin/main`/`v1.7.7` production tag에 포함되지 않았으므로 #13의 post-fix 판정으로 쓰지 않는다. 이 값은 현재 live 상태 확인용 중간 smoke이며, release/tag/Play deploy 포함 후 14일 창을 별도로 채운다.
- 2026-06-07T08:36:11Z 30일 metrics snapshot에서는 `screen_view` `40,156` 중 `(not set)+blank` gap이 `25,305`(`63.0%`)였다. 같은 snapshot에서 최신 관측 version `1.7.7` active share는 `181 / 783 = 23.1%`라 #359 기준 `주의`다. 따라서 14일 smoke와 30일 snapshot 모두 #13을 “개선 완료”로 닫는 근거가 아니라 release boundary 전 guardrail이다.
- 2026-06-06 버전 채택률 smoke에서는 최신 관측 버전 `1.7.7` activeUsers가 181명, 전체 activeUsers가 783명으로 `181 / 783 = 23.1%`였다. #359 기준 `주의`이지만 30% 미만이라 #13/#14/#16/#307 관련 최신 PR 성과를 전체 30일 합산 지표로 판정하지 않는다.
- 2026-06-03 루틴 반복 사용 기준선에서는 `customUser:routines_count >= 1` activeUsers가 150명, `routines_count = 0` activeUsers가 155명으로 규모가 비슷했지만, sessions / activeUsers는 루틴 보유자가 `2,152 / 150 = 14.35`, 루틴 미보유자가 `1,180 / 155 = 7.61`이었다. `app_block_intercepted` users / activeUsers도 루틴 보유자 `91 / 150 = 60.7%`, 루틴 미보유자 `62 / 155 = 40.0%`로 차이가 있다. 다만 `(not set)` activeUsers가 560명으로 가장 크므로, 루틴 CTA/템플릿 실험은 실행 후보로 두되 전체 retention 결론은 `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`의 queryability/버전 채택률 경계를 따른다.
- 현재 #65는 ASO 초안 부재 상태가 아니라, **대표님 수동 반영 완료 후 baseline/14일·30일 측정 복원 단계**로 이동해 있다. 자세한 follow-up 계약은 `docs/PLAY_STORE_ASO.md`를 source of truth로 본다.
- 2026-06-01/2026-06-07 스냅샷처럼 `Direct` 신규 비중이 커지거나 `Paid Search` 활성/세션만 남는 경우, ASO 효과 판정 전에 #242 attribution gate를 적용한다. 2026-06-07T08:36:11Z live readback 기준 전체 `newUsers`는 553명으로 직전 대비 +49.9%였지만 `Direct` 신규가 333명(60.2%)으로 과다 상태를 유지했고 `Organic Search` 신규는 221명으로 #65 기준선 178명을 넘은 상태다. `sessions`도 5,090회로 직전 6,226회 대비 -18.2%다. 즉 Play Console Search/Explore와 GA4 `Organic Search`가 같은 방향인지, external/campaign/UTM 누락이 아닌지 확인한 뒤 #65의 14일/30일 결론을 쓴다.
- 현재 #14는 홈 첫 잠금 CTA(PR #256), 첫 차단 성공 피드백(PR #279), 홈 Keep/타이머 시작 직후 안내(PR #283)가 `origin/develop`에 반영된 상태다. 다만 2026-06-02 확인 기준 이 세 PR은 `origin/main`/최신 production tag `v1.7.7`에는 아직 포함되지 않았으므로, `v1.7.7` live production activation 수치는 post-fix 결과가 아니라 pre-#256/#279/#283 baseline이다. 다음 활성화 판단은 “CTA를 또 만드는 것”이나 “첫 가치 피드백 미정의”가 아니라, 해당 commit 포함 release/tag/Play deploy 이후 14일 창에서 `first_lock_configured / first_open`, `first_core_action_completed / first_lock_configured`, `app_block_intercepted / first_core_action_completed`가 같이 개선됐는지 확인하는 것이다. 세부 출처/차단앱/권한별 분해는 #13의 GA4 Admin registration/materialization 확인 전까지 낮은 confidence로 둔다.

## 성장 루프 후보

### 1. 집중 성공 공유 루프

- 트리거: 사용자가 일정 시간 차단/집중을 성공적으로 마침.
- 공유물: “오늘 2시간 집중 성공” 카드.
- 유입 경로: 공유 카드 → Play Store 링크.
- 리스크: 사용자의 집중/중독 문제가 민감할 수 있으므로 공유는 완전 선택형이어야 한다.
- 지표: 공유 클릭률, 공유 후 설치, 공유 사용자의 유지율.
- 현재 상태: #211 MVP는 repo-internal 공유 CTA/analytics/privacy guardrail로 구현됐지만, #597로 공유 본문/duration의 locale resource-template debt가 남아 있다. CTA/share sheet title localization과 payload body localization을 동일 완료 상태로 보지 않는다.
- 실행 계약: `docs/FOCUS_SUMMARY_SHARE_MVP.md`, issue #211, localization follow-up #597

### 2. 루틴 템플릿 공유 루프

- 트리거: 사용자가 유용한 공부/업무 루틴을 만듦.
- 공유물: 앱 목록을 직접 노출하지 않는 루틴 템플릿. MVP는 Android share sheet 텍스트 공유이며 deep link/import는 별도 결정 게이트다.
- 유입 경로: 템플릿 공유문 → Play Store 링크 → 설치/활성화. 자동 import 전환은 아직 구현-ready가 아니다.
- 리스크: `lockApplications`, package name, 앱 이름, raw session history 등 민감 정보 노출 금지.
- 지표: `routine_template_share_tapped` users / 루틴 보유 active users, `routine_template_share_sheet_opened` users / tapped users, 실패율, 루틴 보유 cohort retention.
- 실행 계약: `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`, issue #407

### 3. 리뷰/신뢰 루프

- 트리거: 반복 차단 성공, 루틴 사용, 긴급해제 후 복귀 등 긍정 경험.
- 행동: 부드러운 만족도 확인 후 Play Review 요청.
- 유입 경로: 리뷰 증가 → Organic Search 전환 개선.
- 리스크: 과도한 프롬프트는 반감 유발.
- 지표: prompt eligible/shown/skipped/failed, rating count, Organic Search 신규 사용자.

## 개인화 리포트 / 추천 후보

### 반복 차단 기반 자동 루틴 제안

- 문제: 첫 차단/반복 차단을 경험한 사용자가 매번 수동으로 같은 시간대 잠금을 시작해야 하면 루틴 전환 기회가 늦어진다. 특히 `routines_count=0` 또는 `(not set)` coverage가 큰 구간에서는 반복 사용 의도와 루틴 생성 UX 사이의 연결이 약하다.
- 기회: 최근 차단 기록에서 반복되는 시간대·요일·앱 카테고리 bucket을 로컬에서 해석해 “이 시간대에 루틴으로 미리 도와드릴까요?” 수준의 prefill 제안을 만들면 반복 차단 사용자를 루틴 소유 cohort로 전환할 수 있다.
- 기본 원칙:
  - #531의 source of truth는 `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`다.
  - #455 루틴 생성 CTA는 루틴 0개 사용자를 향한 일반 soft CTA이고, #531은 충분한 반복 패턴이 관측된 후의 개인화 추천이다. 두 CTA가 동시에 같은 slot을 점유하면 UX 실패로 본다.
  - 앱 이름/package/raw history/raw timestamp를 analytics로 보내지 않고 `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant`만 사용한다.
- guardrail:
  - onboarding / pre-first-lock 사용자에게는 노출하지 않는다.
  - 기존 활성 루틴이 같은 패턴을 이미 커버하면 추천하지 않는다.
  - `또 실패했어요`, `중독 패턴`, `못 참음` 같은 blame/shame copy 금지.
  - 추천 prefill은 저장 전 사용자가 요일·시간·앱 범위를 수정할 수 있어야 한다.
- 성공 판단:
  - `repeat_block_routine_suggestion_clicked / repeat_block_routine_suggestion_shown`
  - `repeat_block_routine_suggestion_applied / repeat_block_routine_suggestion_clicked`
  - 추천 적용 cohort의 D7/D30 반복 차단/루틴 사용 유지율 vs eligible but not applied cohort
  - 긴급해제 사용률, dismiss율, 리뷰 불만이 악화되면 추천 빈도/문구를 재검토한다.
- 현재 상태: PR #537로 `RepeatBlockRoutineSuggestionPolicy`와 `repeat_block_routine_suggestion_*` local policy + analytics adapter 계약, PR #552로 RoutineRoute/RoutineBottomSheet prefill 적용 경로, PR #555로 `RepeatBlockRoutineSuggestionStore` dismiss persistence가 `develop`에 반영됐다. 아직 Home/LockHistory/성과 리포트 CTA card exposure, dismiss/apply store UI wiring, locale copy parity, release/tag/Play deploy, GA4 Admin 등록/metadata 확인, 14일/30일 readback 전이므로 live event 0건을 수요 없음이나 추천 실패로 해석하지 않는다.

### LockHistory 성과 리포트

- 문제: `LockHistory`가 총 시간/세션/top apps를 보여도, 사용자가 “내가 무엇을 지켰는지”를 긍정적으로 해석하는 경험은 아직 약하다.
- 기회: 기존 기록 화면을 유지하면서 summary card, empty/low-data copy, top apps heading을 성취형으로 정리하면 반복 방문과 자기효능감을 강화할 수 있다.
- 현재 상태:
  - #465의 source of truth는 `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`다.
  - #211 공유 CTA와 같은 화면을 쓰더라도, #465의 1차 지표는 외부 공유가 아니라 `LockHistoryScreen` 재방문과 반복 차단/세션이다.
  - PR #485로 read model/UI/string/test slice, 2026-06-05 code-lane instrumentation으로 `lock_history_performance_summary_viewed` / `lock_history_top_apps_viewed`, PR #566 summary/top apps TalkBack baseline, PR #579 Top Apps rank/app label/block count/duration contentDescription baseline이 `develop`에 반영됐다.
  - release/tag/Play deploy, GA4 Admin 등록/metadata 확인, 14일·30일 readback 전에는 `lock_history_*` 0건을 UX 실패로 해석하지 않는다.
- guardrail:
  - `중독`, `실패`, `못 참음`, `위험 사용자` 같은 shame/friction copy 금지.
  - 성취 copy가 과장되거나 압박으로 읽히는지 Play review/rating, 긴급해제 사용률, crash-free users와 함께 본다.
- PR #485로 read model/UI copy는 `develop`에 반영됐고, 2026-06-05 code-lane instrumentation으로 `lock_history_*` 이벤트 코드 계약도 추가됐다. 다만 release/tag/Play deploy, GA4 Admin 등록/metadata 확인, 14일/30일 readback 전에는 성과 결론을 보류한다. instrumentation 포함 버전이 배포되기 전의 이벤트 0건은 수요 없음으로 해석하지 않는다.

### Usage Access 기반 개인화 리포트

- 문제: 사용자는 어떤 앱/시간대 때문에 반복적으로 무너지는지 감에 의존해 차단을 설정한다.
- 기회: Usage Access를 선택적으로 활용하면 상위 방해 앱, 위험 시간대, 전주 대비 변화, 추천 루틴을 제안할 수 있다.
- 기본 원칙:
  - 핵심 차단 기능의 필수 권한으로 만들지 않는다.
  - 메시지/콘텐츠는 다루지 않고, 앱 사용 시간/빈도/시간대 집계만 사용한다.
  - 외부 전송보다 로컬 집계와 설명 가능한 규칙 기반 추천을 우선한다.
- 첫 MVP 범위:
  - 지난 7일 상위 방해 앱 Top 5
  - 위험 시간대
  - 전주 대비 변화
  - 추천 차단 시작점
- guardrail:
  - 권한 미허용 시 기존 차단/타이머/루틴 가치가 훼손되지 않아야 한다.
  - 민감한 앱 이름 노출과 감시 느낌을 피해야 한다.
  - 허용률/추천 클릭률뿐 아니라 `first_lock_configured`, `app_block_intercepted`, review/rating 악화 여부를 같이 본다.
- 상세 계약: `docs/USAGE_STATS_PERSONALIZATION_MVP.md`, issue #119. 기존 #82는 아이디어 정리 이력이고, 현재 실행 판단은 #119 discovery gate가 관리한다.

## 수익화 실험 후보

### 1. 광고 제거 일회성 구매

- 장점: 소비자 유틸리티 앱에 단순하고 신뢰를 해치기 적다.
- 리스크: 현재 광고 수익이 낮아도 구매 의향이 없을 수 있다.
- 검증: 설정/메뉴에 “광고 제거 준비 중” 관심 클릭 측정. PR #362로 `monetization_interest_shown` / `monetization_interest_clicked` 코드 계약이 생겼고, 2026-06-04 code-lane에서 메뉴/설정 CTA가 실제 배치됐다. PR #461 이후 배너 placement metadata는 `AdPlacement.toMetadata(...)` helper에서 `ad_placement`와 `ad_unit_id`를 함께 생성하므로, CTA 관심도와 배너 placement 성과를 비교할 때 call-site 수동 drift 가능성은 낮아졌다. 지표 분모는 CTA 포함 버전이 배포된 뒤의 `monetization_interest_shown` users이며, 기본 분자/분모는 `monetization_interest_clicked` users / `monetization_interest_shown` users다. GA4 `interest_context` / `interest_surface` metadata 확인 후 표면별로 본다.
- 선행 조건: #16의 AdMob 감사에서 `(not set)`/empty 광고 단위 원인이 분류되고, `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 placement 계약표 기준으로 최소 14일 재조회가 가능해야 한다. 계측 매핑이 불명확하면 수익화 실험보다 보정 PR/GA4 Admin 조치를 먼저 둔다.
- 이벤트/GA4 등록 계약: `docs/ANALYTICS_EVENT_DICTIONARY.md`와 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 따른다. 결제 구현 전 `purchase_available=false` 상태의 클릭은 구매 전환이 아니라 관심도 신호로만 해석한다.

### 2. 보상형 광고 기반 추가 긴급해제

- 장점: 광고와 사용자 니즈가 연결될 수 있다.
- 리스크: 긴급/안전 플로우를 광고가 방해하면 신뢰 손상.
- guardrail: 기본 긴급해제는 절대 광고 뒤에 두지 않는다. 추가 선택권만 광고와 연결한다.

### 3. 프리미엄 루틴/통계

- 장점: 반복 사용자에게 자연스럽다.
- 리스크: 핵심 차단 기능을 유료화하면 반감이 클 수 있다.
- 검증: 루틴 사용자 대상 프리미엄 기능 클릭/관심 이벤트.

## 리뷰 프롬프트 원칙

리뷰 요청은 긍정 경험 뒤에만 한다.

추천 eligibility:

- `app_block_intercepted` 누적 5회 이상
- 루틴이 1개 이상 있고 3일 이상 사용
- 긴급해제를 사용했지만 이후 다시 차단 세션으로 복귀
- crash-free 상태
- 최근 7일 내 리뷰 프롬프트 미노출

추적 이벤트:

- `review_prompt_eligible`
- `review_prompt_shown`
- `review_prompt_skipped` with reason
- `review_prompt_failed`

주의: Play In-App Review API는 사용자가 실제로 리뷰를 남겼는지, dismiss 했는지를 앱에 직접 알려주지 않는다. 그래서 현재 신뢰 가능한 lifecycle 신호는 `eligible / shown / skipped / failed`까지다.

## 운영 주기

### 매일

- crash-free users rate
- app_exception
- 신규 배포 버전 이벤트 이상치
- 핵심 권한/차단 이벤트 급락

### 매주

- North Star
- 활성화 퍼널
- Organic Search 신규 사용자
- 리뷰 수/평점
- 광고 ARPU/eCPM

### 매월

- 코호트 유지율
- Play Store ASO 전후 비교
- 수익화 실험 결과
- 우선순위 재산정

## 관련 GitHub Issues

- #13 GA4 계측 품질 개선
- #14 첫 잠금 활성화 퍼널 개선
- #65 Play Console ASO 시안 반영 및 14·30일 유입 회복 검증
- #16 AdMob 성과 및 수익화 실험 (`docs/ADMOB_MONETIZATION_RUNBOOK.md` 참조)
- #17 리뷰 프롬프트 생애주기 개선 (`docs/REVIEW_PROMPT_LIFECYCLE.md` 참조)
- #307 리뷰 프롬프트 shown 0 post-release follow-through (`docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md` 참조; PR #308/#312 포함 버전의 release/tag/Play deploy 후 14일·30일 관측 경계)
- #119 Usage Access 선택형 개인화 discovery gate (`docs/USAGE_STATS_PERSONALIZATION_MVP.md` 참조; #82는 기존 아이디어 정리 이력)
- #407 루틴 템플릿 공유 discovery / privacy-safe MVP 계약 (`docs/ROUTINE_TEMPLATE_SHARE_MVP.md` 참조)
- #417 목표 잠금 MVP 계약 (`docs/GOAL_LOCK_MVP.md` 참조; 기간 기반 `all_day`/`scheduled` 장기 잠금, Home card/section, enum/bucket analytics와 QA baseline)
- #471 부모 모드 / 아이에게 폰 주기 same-device MVP 계약 (`docs/PARENT_MODE_MVP.md` 참조; 보호자 PIN, 허용 앱, 시간 만료, privacy-safe analytics와 QA baseline)
- #531 반복 차단 기반 자동 루틴 제안 계약 (`docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md` 참조; 반복 시간대·요일·카테고리 bucket 기반 루틴 prefill, #455와 slot 충돌 방지, privacy-safe analytics와 QA baseline)
- #250 AdMob application/ad unit id flavor별 config 분리 (`docs/ADMOB_MONETIZATION_RUNBOOK.md`의 #250 handoff 참조)

## 관련 실행 문서

- `docs/PLAY_STORE_ASO.md`: #65용 Play Console ASO 실행 런북. 최종 copy, 스크린샷 구성, baseline, 반영 로그, 14일/30일 검증 포맷 포함
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: #13용 GA4 Admin 수동 등록, metadata 증적, 14일 재측정 런북
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`: #14용 activation 퍼널 canonical 계약, CTA, queryability guardrail
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`: #16용 광고 단위 감사, `(not set)` 점검, guardrail, 1차 수익화 실험 운영 기준
- `docs/REVIEW_PROMPT_LIFECYCLE.md`: #17용 리뷰 프롬프트 arm/drain 규칙, skip reason, queryability guardrail
- `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`: #307용 shown 0 post-release 재측정, 버전별 lifecycle 표, Play Console 후행 지표 추적. PR #308 launch-failure 재시도 계약과 PR #312 Home Activity unwrap 계약은 모두 develop에 merge됐으므로, 이제 코드 PR 대기가 아니라 PR #308/#312 포함 버전의 release/tag/Play deploy 확인과 배포 후 14일/30일 관측 경계로 본다.
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`: #119용 Usage Access 선택형 개인화 discovery gate. 권한 UX, MVP 리포트 4종, 규칙 기반 추천, 개인정보/정책 가드레일, QA evidence, child issue 분리 기준 포함.
- `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`: #407용 루틴 템플릿 공유 MVP 계약. Android share sheet 텍스트 공유, privacy-safe payload, analytics event 초안, deep link/import decision gate, 14일/30일 측정 기준 포함.
- `docs/GOAL_LOCK_MVP.md`: #417용 목표 잠금 MVP 계약. `preset_days`/`custom_days`/`end_date`, `all_day`/`scheduled`, Home 진행 카드/섹션, `goal_lock_*` analytics, runtime QA baseline, 구현 후 `Closes #417` 경계 포함.
- `docs/PARENT_MODE_MVP.md`: #471용 부모 모드 / 아이에게 폰 주기 same-device MVP 계약. 보호자 PIN, 허용 앱, 시간 만료, `parent_mode_*` analytics, runtime QA baseline, 원격 자녀 기기 관리 후속 gate 포함.
- `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`: #531용 반복 차단 기반 자동 루틴 제안 계약. 반복 시간대·요일·카테고리 bucket 기반 루틴 prefill, 기존 루틴 coverage guard, #455/#407/광고 CTA slot 충돌 방지, `repeat_block_routine_suggestion_*` analytics와 QA evidence template 포함.
