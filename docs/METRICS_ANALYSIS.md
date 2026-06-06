# 스탑잇 지표 분석 운영 가이드

이 문서는 스탑잇(Stopit / Keep Android)의 제품 지표를 분석하고, 개선 작업을 GitHub Issue로 전환하는 표준 절차를 정리한다.

첫 잠금 활성화 퍼널의 canonical 계약과 CTA/guardrail은 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 source of truth로 본다.

## 목적

지표 분석의 목적은 단순 리포트가 아니라 다음 실행을 정하는 것이다.

- 계측 품질이 충분한지 확인한다.
- 신규 유입, 활성화, 유지, 수익화, 신뢰 지표 중 병목을 찾는다.
- 병목을 하나의 실행 가능한 GitHub Issue 단위로 만든다.
- 개선 후 14일/30일 기준으로 전후 비교가 가능하게 한다.

## 기본 원칙

1. 먼저 계기판을 의심한다.
   - 화면명이 `(not set)`으로 많이 잡히거나, 이벤트 파라미터가 GA4 차원으로 등록되지 않았으면 퍼널 결론을 과신하지 않는다.
   - 이벤트 의미가 앱 버전별로 바뀐 경우 전후 비교를 분리해서 본다.

2. 절대 비율만 보지 않는다.
   - 항상 분자/분모와 기간을 같이 적는다.
   - 예: `first_lock_configured 41명 / first_open 201명 = 약 20%`.

3. 하나의 지표마다 이슈를 만들지 않는다.
   - 비즈니스 실행 단위로 묶는다.
   - 예: 계측 품질, 첫 잠금 활성화, Play Store ASO, 광고 수익화, 리뷰 프롬프트.

4. 개선 이슈는 한국어로 작성한다.
   - 제목, 문제 설명, 제안 작업, 완료 기준을 한국어로 쓴다.
   - 외부 도구명, 이벤트명, API명은 원문을 유지해도 된다.

## 데이터 소스

### GA4 Analytics Data API

주요 제품 지표는 GA4 Analytics Data API에서 확인한다.

현재 확인된 GA4 property:

- `properties/502544175`

서비스 계정 키는 로컬에 있을 수 있다.

- Analytics 조회용 예시 경로: `<analytics-service-account.json>`
- Play Console 조회용 예시 경로: `<play-service-account.json>`

주의:

- 키 파일은 절대 Git에 커밋하지 않는다.
- 문서에는 키 내용이나 private key를 기록하지 않는다.
- 경로가 바뀌면 로컬 환경에서만 수정해서 사용한다.

### 앱 코드

계측 정의와 이벤트명은 주로 아래 파일을 확인한다.

- `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`
- `app/src/main/java/com/uiery/keep/analytics/FirebaseAnalyticsBackend.kt`
- `app/src/main/java/com/uiery/keep/analytics/TrackedBannerAd.kt`
- 관련 테스트: `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`, `app/src/test/java/com/uiery/keep/analytics/TrackedBannerAdTest.kt`

### GitHub Issues

지표 분석 결과는 실행 단위로 GitHub Issue에 등록한다.

- 저장소: `parkuiery/Stopit-Android`
- 로컬 경로: `<repo-root>`

## 관련 문서

- `docs/PRODUCT_METRICS_DASHBOARD.md`: North Star, 입력/건강/비즈니스 지표, ICE 우선순위, 성장/수익화 실험 정의.
- `docs/ANALYTICS_EVENT_DICTIONARY.md`: 이벤트명, 파라미터, screen_view 계약, GA4 커스텀 차원/지표 등록 계약, 검증 명령.
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: #13용 GA4 Admin 수동 등록 절차, registration ledger, metadata 증적, 14일 재측정 포맷. 2026-06-02 기준 PR #296으로 `SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen`, PR #318로 dev/debug `DevToolScreen` screen_view 보강이 develop에 들어갔으므로, 2026-05-29 screen quality baseline은 pre-#296/#318 기준선으로 해석한다.
- `docs/PLAY_STORE_ASO.md`: #65용 Play Console ASO 실행 런북. 최종 copy, 스크린샷 구성, baseline, 반영 로그, 14일/30일 검증 포맷, #242 acquisition attribution gate, UTM/Install Referrer 운영 기준 포함. 현재 기준으로는 **대표님 수동 반영 완료 후 사후 복원/성과 추적 문서**다. 2026-06-06T08:33:25Z live readback에서는 전체 `newUsers`가 537명으로 반등했지만 `Direct` 신규가 335명(62.4%)으로 유지됐고 `Organic Search` 신규는 202명으로 #65 기준선 178명을 넘은 상태이며 `sessions`도 직전 30일 대비 -21.4%라, Play Console Search/Explore 확인 전에는 ASO 회복으로 표현하지 않는다.
- `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`: #380용 루틴 보유/미보유 반복 사용 코호트 기준선. `customUser:routines_count`로 `0` / `>=1` / `(not set)`을 분리하고, sessions, `app_block_intercepted`, `first_core_action_completed`, `emergency_unlock_completed`를 같은 분자/분모로 재조회한다.
- `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`: #479용 `routines_count` user property coverage 보강 계약. `customUser:routines_count`가 metadata에 보인다는 사실과 `(not set)` activeUsers가 큰 coverage gap이라는 사실을 분리하고, code-lane 구현/release/D+14·D+30 readback 경계를 고정한다.
- `docs/HOME_STATUS_CTA_STRUCTURE.md`: #463용 홈 화면 상태/CTA 구조 계약. Home의 꺼짐/켜짐/타이머/목표 잠금/선택 앱 없음 상태와 단일 primary CTA 위계를 고정하되, docs-lane 산출물은 구현 완료가 아니며 `first_lock_configured`를 실제 차단 완료로 해석하지 않는다.
- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`: #455용 첫 차단 성공 이후 루틴 0개 사용자 대상 루틴 생성 soft CTA 계약. `routine_creation_cta_*` 이벤트, privacy-safe 파라미터, Routine empty state/광고/#407 CTA 충돌 방지, 14일/30일 readback 경계를 정리한다.
- `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`: #531용 반복 차단 기반 자동 루틴 제안 계약. 반복 시간대·요일·앱 카테고리 bucket 기반 루틴 prefill, 기존 루틴 coverage guard, #455/#407/광고 CTA slot 충돌 방지, `repeat_block_routine_suggestion_*` analytics와 QA baseline을 정리한다.
- `docs/BLOCK_SCREEN_COPY_HIERARCHY.md`: #464용 차단 화면 카피/액션 위계 계약. `BlockScreen`을 처벌/제한 화면이 아니라 코칭 톤의 “잠깐 멈춤 + 자기 통제 보조” 경험으로 고정하고, emergency unlock 보조 액션/남은 횟수/disabled reason, 광고 간섭 제한, locale parity, QA evidence 기준을 정리한다. 이 문서는 구현 완료가 아니라 code-lane 구현·release/tag/Play deploy 후 14일 readback 전까지는 UX 계약으로만 해석한다.
- `docs/EMERGENCY_UNLOCK_FLOW_COPY.md`: #467용 긴급해제 reason/app/duration/countdown copy·step 계약. PR #517(`572eb559`)로 짧은 reason label, helper/disabled copy, reason-required-off guardrail, 기존 reason enum compatibility, shipped locale parity와 focused JVM/lint/build 검증은 `develop`에 반영됐다. 아직 실제 기기/screenshot/TalkBack QA, release/tag/Play deploy, 14일 readback 전까지는 live 성과 판단을 보류한다.
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`: #14용 첫 잠금 활성화 퍼널 source of truth. 홈 첫 잠금 CTA(PR #256), 첫 차단 성공 피드백(PR #279), 홈 Keep/타이머 시작 직후 안내(PR #283) 이후에는 “CTA/피드백 부재”가 아니라 post-release 14일 재측정과 #13 queryability 경계를 기준으로 본다. 2026-06-02 확인 기준 이 세 PR은 `origin/develop`에는 포함됐지만 `origin/main`/최신 production tag `v1.7.7`에는 아직 미포함이므로, live production activation 수치는 post-fix 결과가 아니라 pre-#256/#279/#283 baseline으로 해석한다.
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`: #16용 광고 단위 감사 절차, guardrail, 안전한 수익화 실험 운영 기준. PR #362 이후 `monetization_interest_*` 코드 계약이 생겼고 2026-06-04 code-lane에서 메뉴/설정 CTA UI까지 배치했다. GA4 `interest_context` / `interest_surface` 등록·metadata 확인, release/tag/Play 배포, 14일 관측 전까지는 관심도 실험 결과를 판단하지 않는다.
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`: #119용 Usage Access 선택형 개인화 discovery gate. #82 아이디어를 이어받아 권한 UX, MVP 리포트 4종, 규칙 기반 추천, analytics 금지 파라미터, child issue 분리 기준을 정리한다.
- `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`: #407용 루틴 템플릿 공유 privacy-safe MVP 계약. `lockApplications`, package name, 앱 이름, raw session history를 payload/analytics에서 제외하고, `routine_template_share_*` 이벤트와 14일/30일 측정 기준을 정리한다.
- `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`: #465용 LockHistory 성과 리포트 UX 계약. 개인 성과 해석/재방문 동기를 #211 공유 루프와 분리하고, empty/low-data 카피, top apps positive framing, privacy-safe `lock_history_*` analytics bucket, 14일/30일 readback 경계를 정리한다.
- `docs/GOAL_LOCK_MVP.md`: #417용 목표 잠금 MVP 계약. 기간 기반 `all_day`/`scheduled` 장기 잠금, Home card/section, `goal_lock_*` 이벤트, enum/bucket privacy guardrail, 구현·GA4 Admin·release·14일/30일 측정 경계를 정리한다.
- `docs/PARENT_MODE_MVP.md`: #471용 부모 모드 / 아이에게 폰 주기 same-device MVP 계약. 보호자 PIN, 허용 앱, 시간 만료, `parent_mode_*` 이벤트, privacy-safe enum/bucket, runtime QA baseline, 원격 자녀 기기 관리 후속 gate를 정리한다.
- `docs/SHARED_UI_OWNERSHIP_BOUNDARY.md`: #492용 공유 UI 소유권 / feature-private import 경계. 제품 지표 문서는 아니지만, Home/Onboarding/Routine 같은 고빈도 화면 UX drift를 줄이기 위한 엔지니어링 handoff이며 `PermissionSettingDialog`와 `TimerPicker` code-lane 정리 전까지는 구현 완료로 해석하지 않는다.
- `docs/REVIEW_PROMPT_LIFECYCLE.md`: #17용 리뷰 프롬프트 arm/drain 규칙, skip reason, Play In-App Review 한계 문서.
- `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`: #307용 `review_prompt_shown = 0` 재측정, 버전별 lifecycle 표, Play Console 14일/30일 후행 지표 런북. 2026-06-02 기준 PR #308 launch-failure 재시도 계약과 PR #312 Home Activity unwrap 계약은 모두 develop에 merge됐고, 2026-06-02T18:06:45Z live 재조회에서 skip reason용 `customEvent:reason`은 조회 가능해졌다. 2026-06-04T21:24:42Z 재확인 기준 `origin/main` `20b8ff4a`와 최신 tag `v1.7.7` `f49e7de9`는 아직 PR #308/#312 merge commit을 포함하지 않으므로, 다음 판단은 PR #308/#312 포함 버전 배포 여부와 배포 후 14일 관측 창을 먼저 확인하되, failure 원인용 `customEvent:error`는 아직 GA4 Admin 미등록 경계로 둔다.
- `docs/VERSION_ADOPTION_METRICS_GATE.md`: #359용 버전 채택률/최신 버전 cohort 판독 게이트. #13/#14/#16/#307처럼 release/tag/Play 배포 후에야 의미가 생기는 지표는 전체 30일 합산과 최신 배포 버전 cohort를 분리해 confidence를 `충분/주의/보류`로 표시한다.

## 빠른 분석 명령

아래 명령은 로컬에서 GA4 주요 지표를 뽑는 최소 예시다.

```bash
cd <repo-root>

python3 - <<'PY'
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH = '<analytics-service-account.json>'

creds = service_account.Credentials.from_service_account_file(
    CREDENTIAL_PATH,
    scopes=['https://www.googleapis.com/auth/analytics.readonly'],
)
session = AuthorizedSession(creds)

def run_report(name, body):
    response = session.post(
        f'https://analyticsdata.googleapis.com/v1beta/properties/{PROPERTY_ID}:runReport',
        json=body,
    )
    print(f'\n--- {name} {response.status_code} ---')
    if response.status_code != 200:
        print(response.text)
        return

    data = response.json()
    headers = [h['name'] for h in data.get('dimensionHeaders', [])]
    headers += [h['name'] for h in data.get('metricHeaders', [])]
    print('\t'.join(headers))

    for row in data.get('rows', []):
        values = row.get('dimensionValues', []) + row.get('metricValues', [])
        print('\t'.join(v.get('value', '') for v in values))


def body(metrics, dimensions=None, start='30daysAgo', end='yesterday', limit=50, order_metric=None):
    payload = {
        'dateRanges': [{'startDate': start, 'endDate': end}],
        'metrics': [{'name': metric} for metric in metrics],
        'limit': limit,
    }
    if dimensions:
        payload['dimensions'] = [{'name': dimension} for dimension in dimensions]
    if order_metric:
        payload['orderBys'] = [{'metric': {'metricName': order_metric}, 'desc': True}]
    return payload

run_report(
    'overview_30d',
    body([
        'activeUsers',
        'totalUsers',
        'newUsers',
        'sessions',
        'engagedSessions',
        'screenPageViews',
        'eventCount',
        'engagementRate',
        'averageSessionDuration',
    ], limit=1),
)

run_report(
    'overview_prev30d',
    body([
        'activeUsers',
        'totalUsers',
        'newUsers',
        'sessions',
        'engagedSessions',
        'screenPageViews',
        'eventCount',
        'engagementRate',
        'averageSessionDuration',
    ], start='60daysAgo', end='31daysAgo', limit=1),
)

run_report(
    'events_30d',
    body(['eventCount', 'totalUsers', 'eventCountPerUser'], ['eventName'], limit=80, order_metric='eventCount'),
)

run_report(
    'screens_30d',
    body(['screenPageViews', 'activeUsers'], ['unifiedScreenName'], limit=50, order_metric='screenPageViews'),
)

run_report(
    'versions_30d',
    body(['activeUsers', 'sessions', 'eventCount'], ['appVersion'], limit=20, order_metric='activeUsers'),
)

run_report(
    'core_events_by_version_30d',
    body(['totalUsers', 'eventCount'], ['eventName', 'appVersion'], limit=500, order_metric='eventCount') | {
        'dimensionFilter': {
            'filter': {
                'fieldName': 'eventName',
                'inListFilter': {
                    'values': [
                        'first_open',
                        'first_lock_configured',
                        'first_core_action_completed',
                        'app_block_intercepted',
                        'review_prompt_eligible',
                        'review_prompt_shown',
                        'review_prompt_skipped',
                        'review_prompt_failed',
                        'ad_banner_impression',
                        'ad_banner_revenue',
                    ]
                },
            }
        }
    },
)

run_report(
    'acquisition_30d',
    body(['newUsers', 'sessions', 'activeUsers'], ['firstUserDefaultChannelGroup'], limit=20, order_metric='newUsers'),
)

run_report(
    'revenue_30d',
    body(['totalAdRevenue', 'totalRevenue', 'averageRevenuePerUser', 'publisherAdImpressions', 'publisherAdClicks'], limit=1),
)
PY
```

## 심화 분석 쿼리

### 특정 이벤트의 버전별 분포

```python
filter_payload = {
    'filter': {
        'fieldName': 'eventName',
        'stringFilter': {'matchType': 'EXACT', 'value': 'first_lock_configured'},
    }
}
```

위 필터를 `dimensionFilter`에 넣고 `appVersion` 차원으로 보면 버전별 이벤트 의미 변화 여부를 확인할 수 있다. #359 버전 채택률 판독 게이트는 `docs/VERSION_ADOPTION_METRICS_GATE.md`의 표준 표와 confidence 기준을 따른다.

### 광고 단위별 수익

전제 확인:

- `TrackedBannerAd.kt`, `AdPlacement.kt`, `AdPlacementContractTest.kt` 기준으로 Stopit 앱 소유 배너 이벤트(`ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue`)와 `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id`, `ad_value_micros` 계약이 현재 코드와 일치하는지 먼저 본다. PR #461 이후 active banner call site는 `AdPlacement.toMetadata(...)`로 `ad_placement`와 `ad_unit_id`를 같은 enum source에서 생성해야 한다.
- 운영 가드레일과 해석 순서는 `docs/ADMOB_MONETIZATION_RUNBOOK.md`를 같이 본다.
- PR #293 이전 legacy `ad_impression` / `ad_click` / `ad_revenue` coverage는 source-split baseline으로만 사용한다. 새 판단은 PR #293 포함 버전 배포 후 14일 창에서 `ad_banner_*` 이벤트를 재조회한 뒤 한다.

사용할 차원과 지표:

- dimensions: `adUnitName`, `adFormat`
- metrics: `totalAdRevenue`, `publisherAdImpressions`, `publisherAdClicks`, `activeUsers`

확인할 것:

- `(not set)` 또는 빈 광고 단위가 있는지
- 노출 대비 수익이 낮은 광고 위치가 있는지
- CTR/eCPM이 이전 기간보다 떨어졌는지

### 계측 메타데이터 확인

GA4에서 등록된 커스텀 차원/지표를 확인한다.
등록 필요 목록은 `docs/ANALYTICS_EVENT_DICTIONARY.md`의 `GA4 custom dimension / metric 등록 계약` 표를, 실제 Admin 등록 절차/registration ledger/외부 경계 구분은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 삼는다.

```bash
python3 - <<'PY'
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH = '<analytics-service-account.json>'

creds = service_account.Credentials.from_service_account_file(
    CREDENTIAL_PATH,
    scopes=['https://www.googleapis.com/auth/analytics.readonly'],
)
session = AuthorizedSession(creds)
response = session.get(f'https://analyticsdata.googleapis.com/v1beta/properties/{PROPERTY_ID}/metadata')
print(response.status_code)
metadata = response.json()

print('\nCustom dimensions')
for dimension in metadata.get('dimensions', []):
    api_name = dimension.get('apiName', '')
    if api_name.startswith('customEvent:') or api_name.startswith('customUser:'):
        print(api_name, '|', dimension.get('uiName'))

print('\nCustom metrics')
for metric in metadata.get('metrics', []):
    api_name = metric.get('apiName', '')
    if api_name.startswith('customEvent:'):
        print(api_name, '|', metric.get('uiName'))
PY
```

## 분석 체크리스트

### 1. 계측 품질

확인 지표:

- `screen_view` 수
- `unifiedScreenName = (not set)` 비중
- 빈 화면명 비중
- 등록된 커스텀 차원/지표 목록
- 이벤트명과 코드의 일치 여부
- Stopit 앱 소유 배너 이벤트(`ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue`)와 `TrackedBannerAd` 파라미터 계약 일치 여부

판단 기준:

- 화면 조회 대부분이 `(not set)`이면 제품 퍼널 결론보다 계측 개선을 먼저 한다.
- 주요 이벤트 파라미터가 GA4 차원으로 조회되지 않으면 `docs/ANALYTICS_EVENT_DICTIONARY.md`의 등록 계약과 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 registration ledger / metadata 증적 / 외부 경계 정리를 먼저 맞춘다.
- docs lane이 repo 안에서 할 수 있는 범위는 registration contract / ledger / 검증 포맷 정리까지이며, 실제 GA4 Admin 등록과 배포 후 14일 재측정은 외부/manual 경계로 분리해 기록한다.
- 광고 분석 전에는 `docs/ANALYTICS_EVENT_DICTIONARY.md`의 AdMob 파라미터 계약과 `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 guardrail을 같이 확인한다.

### 2. 획득 / 신규 유입

확인 지표:

- `newUsers`
- `first_open`
- `firstUserDefaultChannelGroup`
- `Organic Search`, `Direct`, `Paid Search`별 `newUsers` / `activeUsers` / `sessions`
- Play Console Store performance / acquisition source의 Search/Explore/external/campaign 수치
- 최근 30일 vs 직전 30일 변화

판단 기준:

- engagement rate가 유지되는데 newUsers만 크게 하락하면 제품 사용성보다 스토어/유입 문제를 우선 본다.
- Organic Search 비중이 높으면 Play Store ASO, 키워드, 스크린샷, 리뷰 수를 우선 개선한다.
- ASO 성과를 판정할 때는 GA4 `firstUserDefaultChannelGroup`만 보지 않는다. Play Console Search/Explore와 같은 방향인지 확인한 뒤 #65의 14일/30일 판정에 쓴다.
- `Direct` 신규 사용자 비중이 갑자기 커지면 실제 direct 유입인지, Discord/웹/문서 링크 또는 캠페인 링크의 UTM/Install Referrer 누락인지 먼저 확인한다.
- `Paid Search`의 신규 사용자는 0명인데 활성 사용자/세션만 남아 있으면 신규 획득 성과로 계산하지 않는다. 실제 캠페인 집행 여부를 확인하고, 집행 중이 아니면 과거 유저/재방문/분류 잔상으로 분리한다.
- #65 ASO 검증의 획득 채널 판정 표와 판정 규칙은 `docs/PLAY_STORE_ASO.md`의 `acquisition attribution gate (#242)` 섹션을 source of truth로 본다.

### 3. 활성화 퍼널

권장 퍼널:

1. `first_open`
2. `onboarding_step_view` / `onboarding_step_complete`
3. `permission_outcome`
4. `app_selection_completed`
5. `first_lock_configured`
6. `first_core_action_completed`
7. `app_block_intercepted`

주의:

- 이벤트가 버전별로 새로 추가되었으면 전체 30일 합산 퍼널을 그대로 믿지 않는다.
- `appVersion`별로 나눠 보고, 이벤트가 동일한 의미로 찍히는 기간만 비교한다.
- 최신 배포 버전 active share가 10% 미만이면 #359 기준 `보류`다. 이때 전체 합산 activation 수치를 최신 코드/CTA/피드백 성과로 승격하지 않고 `docs/VERSION_ADOPTION_METRICS_GATE.md`의 D+14/D+30 재측정 표를 먼저 채운다.
- PR #256 이후 #14는 홈 첫 잠금 CTA가 구현된 상태이며, PR #279/#283 이후 첫 차단 성공 피드백과 홈 Keep/타이머 시작 직후 안내도 develop 반영 상태다. 이후 docs/metrics lane은 이 이슈를 다시 “앱 선택 후 CTA가 없음” 또는 “첫 가치 피드백 미정의”로 해석하지 않는다. 다만 2026-06-02 확인 기준 PR #256/#279/#283 commit은 `origin/main`과 최신 production tag `v1.7.7`에 아직 미포함이므로, live production 수치는 post-fix 효과가 아니라 pre-#256/#279/#283 baseline으로만 본다.
- 다음 repo 내부 후보는 새 CTA/피드백을 또 만드는 것이 아니라, 준비 완료(`first_lock_configured`)를 실제 차단 완료로 과장하지 않는 현재 안내 계약과 실제 `BlockViewModel.trackBlockShown(...)`의 `app_block_intercepted` → 최초 `first_core_action_completed` 계측 계약을 release/metrics handoff에서 유지하는 것이다.
- 측정은 `first_lock_configured / first_open`뿐 아니라 `first_core_action_completed / first_lock_configured`, `app_block_intercepted / first_core_action_completed`를 함께 본다. 세부 `source`, `blocking_mode`, `block_source` 분해는 #13 GA4 Admin registration 상태를 먼저 확인한다.

### 4. 유지 / 반복 사용

확인 지표:

- `activeUsers`
- `sessionsPerUser`
- `lock_session_start`
- `app_block_intercepted`
- `core_action_completed`
- `customUser:routines_count`

판단 기준:

- 반복 사용 이벤트가 특정 소수에게 몰려 있으면 파워유저와 신규 유저 경험을 분리해서 본다.
- 루틴 수가 있는 사용자의 세션/이벤트가 높다면 루틴 생성 유도를 활성화 개선 후보로 본다.
- 루틴 보유/미보유 비교는 `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`의 표준 코호트(`customUser:routines_count = 0`, `>=1`, `(not set)`)와 재측정 기준을 따른다. 2026-06-03 기준 루틴 보유자는 sessions / activeUsers와 `app_block_intercepted` users / activeUsers가 모두 높지만, `(not set)` activeUsers가 가장 커서 전체 retention 결론은 보류한다.
- #455 루틴 생성 CTA 실험은 `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`를 source of truth로 보고, `first_core_action_completed` 또는 `app_block_intercepted` 이후 + 루틴 0개 사용자에게만 soft CTA를 제안한다. onboarding / pre-first-lock 사용자는 제외하고, Routine empty state / 광고 배너 / #407 루틴 템플릿 공유 CTA와 같은 slot에서 압박하지 않는다.
- #531 반복 차단 기반 자동 루틴 제안은 `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`를 source of truth로 보고, 일반 CTA가 아니라 반복 차단 패턴이 충분한 사용자에게만 time/day/category bucket 기반 루틴 prefill을 제안한다. PR #537로 local policy + analytics adapter foothold는 `develop`에 반영됐지만, UI wiring/prefill navigation/release/GA4/readback 전에는 event 0건을 수요 없음으로 해석하지 않는다. 기존 활성 루틴이 같은 패턴을 커버하면 추천하지 않고, `repeat_block_routine_suggestion_*` 이벤트는 privacy-safe enum/bucket만 사용한다.
- 루틴 실험을 실행 후보로 올릴 때는 guardrail로 `emergency_unlock_completed` users / blocked users, review/rating, crash-free users를 함께 본다. 루틴 보유자의 차단 강도가 높다는 신호가 사용자 부담 증가로 이어질 수 있기 때문이다.

### 5. 수익화

확인 지표:

- `totalAdRevenue`
- `averageRevenuePerUser`
- `publisherAdImpressions`
- `publisherAdClicks`
- `adUnitName`
- `adFormat`

계산:

- CTR = `publisherAdClicks / publisherAdImpressions`
- eCPM = `totalAdRevenue / publisherAdImpressions * 1000`
- 30일 ARPU = `totalAdRevenue / activeUsers`

판단 기준:

- 노출은 증가했는데 수익/CTR/eCPM이 하락하면 광고 위치·광고 품질·계측 문제를 함께 의심한다.
- `adUnitName = (not set)` 또는 empty가 의미 있는 비중이면 placement 최적화나 새 광고 실험보다 `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 코드 기준 placement 표와 #16 closure-pass 게이트를 먼저 적용한다.
- `adUnitName`은 AdMob/GA4 표시명이고 앱 custom event의 `ad_unit_id`와 같은 필드가 아니므로, 둘을 연결하려면 `ad_unit_id`, `ad_placement`, `screen_context` custom dimension 등록 여부와 실제 이벤트 breakdown을 따로 확인한다.
- 2026-06-01 preflight 기준 광고 custom dimensions/metrics는 GA4 metadata에 등록되어 있고, PR #293에서 앱 소유 배너 이벤트명이 SDK 자동 이벤트와 분리됐다. 이후 판단은 `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 `GA4 query template: publisher surface와 Stopit 앱 custom 이벤트 분리` 및 `release boundary snapshot` 섹션을 따라 PR #293 포함 commit이 `main`/SemVer tag/Play deploy에 실제 포함된 뒤 14일 창에서 재조회한다. 2026-06-02/2026-06-03 확인 기준 최신 production tag `v1.7.7`과 현재 `origin/main`은 PR #293 split commit을 포함하지 않으므로, `v1.7.7` 광고 데이터는 post-split measurement로 쓰지 않는다. 2026-06-03 GA4 smoke에서 `ad_banner_impression`/`ad_banner_revenue`가 `appVersion=1.7.5`, `date=20260602`, `home_bottom 41`, `block_top 6`으로 보인 것은 source-split queryability 확인용이며 production 14일 placement 결론으로 쓰지 않는다.
- production AdMob application/ad unit id가 UI/Manifest에 분산된 상태(#250류)는 수익화 성과 저하 원인으로 단정하지 않는다. 이것은 광고 inventory 운영 안전성/환경 분리 문제이므로, `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 `issue #250: flavor별 광고 설정 계약 handoff`를 보고 config 중앙화와 dev/debug non-production guard를 code/maintenance lane으로 넘긴다.
- 광고 custom-event coverage를 계산할 때는 `ad_banner_impression`/`ad_banner_click`/`ad_banner_revenue`별 total `eventCount`와 `customEvent:ad_placement`가 `(not set)`/empty가 아닌 covered `eventCount`를 분리해 기록한다. coverage가 낮으면 placement별 CTR/eCPM 결론을 보류한다.
- 차단/긴급해제 흐름을 방해하는 광고 실험은 하지 않는다.

### 6. 신뢰 / 리뷰

확인 지표:

- Play Store 평점과 리뷰 수
- `review_prompt_eligible`, `review_prompt_shown`, `review_prompt_skipped`, `review_prompt_failed`
- `review_prompt_skipped` reason 분포 (`customEvent:reason`은 2026-06-02T18:06:45Z 기준 등록/조회 가능. 단, pre-fix cohort noise와 PR #308/#312 포함 버전 이후 신호를 분리)
- 성공적 사용 이벤트: `app_block_intercepted`, `lock_session_start`, `core_action_completed`

판단 기준:

- 사용 신호가 있는데 리뷰 수가 적으면 긍정 사용 순간 기반 리뷰 프롬프트를 만든다.
- Play in-app review 정책을 지키고 반복 요청을 제한한다.
- `shown`은 리뷰 작성 완료가 아니라 sheet launch 성공으로 해석하고, 실제 성과는 Play Console 평점/리뷰 수의 14일·30일 후행 비교로 본다.

## 이슈 작성 템플릿

지표 기반 개선 이슈는 아래 구조를 사용한다.

```markdown
## 지표 근거

- 분석 기간: 최근 30일, 비교 기간: 직전 30일
- 핵심 수치:
  - ...

## 문제

이 지표가 제품/운영 관점에서 의미하는 문제를 적는다.

## 제안 작업

- 구체적인 실행 항목 1
- 구체적인 실행 항목 2
- 구체적인 실행 항목 3

## 완료 기준

- [ ] 측정 가능한 완료 조건
- [ ] 구현/문서/테스트 조건
- [ ] 14일 또는 30일 후 전후 비교 조건
```

## 대표 이슈 분류

### 계측 품질

예시 문제:

- 화면명이 대부분 `(not set)`이다.
- 주요 이벤트 파라미터가 GA4에서 조회되지 않는다.
- 이벤트 의미가 버전별로 바뀌었는데 문서가 없다.

### 첫 잠금 활성화

예시 문제:

- 앱 선택은 완료하지만 첫 잠금 설정이나 첫 성공 차단까지 이어지지 않는다.
- 권한 설정 후 앱으로 돌아왔을 때 다음 CTA가 약하다.

### Play Store ASO

예시 문제:

- 신규 유입이 감소했는데 engagement rate는 유지된다.
- Organic Search 의존도가 높다.
- 스토어 문구/스크린샷이 현재 Android 앱 가치와 맞지 않는다.

현재 Stopit 운영 메모:

- issue #65의 저장소 문서화 범위는 이미 완료되었고, 대표님 수동 반영 기준으로 실제 Play Console copy/스크린샷도 반영 완료 상태다.
- 남은 일은 "문안 만들기"가 아니라 **반영 시각/노출값 사후 복원**과 **14일·30일 전후 비교 기록**이다.
- 따라서 이후 metrics/docs run에서 #65를 다시 보면 "ASO 초안 부재"로 해석하지 말고, 외부 수동 증적/시간 경과를 기다리는 follow-up 이슈로 다룬다.

### 광고 수익화

예시 문제:

- 노출은 늘었는데 CTR/eCPM/수익이 감소한다.
- `(not set)` 광고 단위가 많다.
- UX 비용 대비 수익이 낮은 광고 위치가 있다.

### 사용정보 기반 개인화

예시 문제:

- 사용 패턴 기반 차단 제안이 없어 신규/반복 사용자 모두 설정을 감에 의존한다.
- Usage Access 권한을 왜 요청하는지 설명/가드레일 없이 구현하면 허용률과 신뢰가 동시에 무너질 수 있다.
- 개인화 기능이 가치 있는지 판단할 MVP 범위와 측정 기준이 아직 없다.

운영 원칙:

- 먼저 `docs/USAGE_STATS_PERSONALIZATION_MVP.md` 기준으로 권한 UX, fallback, MVP 범위, 규칙 기반 추천, 개인정보 가드레일을 닫는다.
- #119는 아직 구현 착수용 `ready` 이슈가 아니라 discovery gate다. 실행 승격 시에는 discovery/contract child issue와 MVP implementation child issue를 분리한다.
- Usage Access는 리포트/추천용 선택형 확장으로 다루고, 핵심 차단 기능의 필수 권한으로 만들지 않는다.
- analytics에는 앱 이름/package/raw usage history를 보내지 않고 bucket 파라미터만 사용한다.
- 외부 전송보다 로컬 집계/설명 가능한 추천을 우선한다.

### 리뷰 / 신뢰

예시 문제:

- 실제 사용 신호는 있으나 리뷰 수가 적다.
- 리뷰 프롬프트가 거의 노출되지 않거나 skipped만 찍힌다.

## 보고 형식

분석 결과를 대표님께 보고할 때는 아래 순서를 따른다.

1. 이번 분석에서 확인한 데이터 소스
2. 핵심 진단 3~5개
3. 우선순위
4. 생성한 GitHub Issue 링크
5. 바로 다음 실행 제안

보고는 현재 대화에 먼저 정리한다. GitHub 댓글은 필요할 때만 보조 기록으로 사용한다.

## 과거 기준선 예시: 다음 분석 시 재조회 필요

2026-05-23 기준 최근 30일 분석에서 확인한 주요 기준선이다. 현재 source of truth가 아니며, 다음 분석에서는 반드시 GA4에서 새로 조회한 값과 `docs/ops/stopit/metrics-context.md`의 최신 release/manual boundary를 함께 기록한다.

- active users: 456
- total users: 507
- new users: 202
- sessions: 4,681
- screen views: 23,191
- engagement rate: 61.4%
- `unifiedScreenName = (not set)`: 19,003 views
- total ad revenue: $2.168155
- publisher ad impressions: 18,731
- publisher ad clicks: 12
- `first_open` users: 201
- `first_lock_configured` users: 41
- `app_block_intercepted` users: 121

이 기준선은 시간이 지나면 낡는다. 다음 분석에서는 반드시 GA4에서 새로 조회한 값으로 갱신한다.

2026-05-29 live 확인 메모:

- 최근 14일 `screen_view` 총량: `13,154`
- `(not set)` `9,473` + 빈 `unifiedScreenName` `801` = `10,274 / 13,154 = 78.1%`
- 이 screen 품질 수치는 PR #296의 `SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen` 및 PR #318의 dev/debug `DevToolScreen` 보강 전 baseline이다. 해당 네 화면은 develop에서 명시적 `screen_view`가 추가됐으므로, 동일 화면에 대한 추가 코드 작업은 PR #296/#318 포함 버전 배포 후 14일 재측정 결과를 보고 판단한다. 단, `DevToolScreen`은 dev/debug 내부 진단 route라 production 사용자 screen 품질 분모와 분리한다.
- 2026-05-29 live 확인 기준 GA4 metadata에서 확인된 custom dimension은 `customUser:routines_count`뿐이었고 activation/review용 `customEvent:*` 차원/지표는 아직 확인되지 않았다.
- 2026-06-01 #16 AdMob preflight 기준 광고 관련 `customEvent:ad_unit_id`, `customEvent:ad_placement`, `customEvent:screen_context`, `customEvent:ad_format`, `customEvent:ad_value_micros`, `customEvent:screen_name`은 metadata에 등록된 것으로 보정 확인됐다.
- activation (`customEvent:permission_name`, `customEvent:source`) runReport smoke query는 당시 `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`으로 실패했다. review `customEvent:reason`도 2026-05-29에는 실패했지만, 2026-06-02T18:06:45Z #307 live 재조회에서 등록/조회 가능해졌다. 따라서 현재 활성화 세부 축과 review `customEvent:error`는 **GA4 Admin 미등록으로 인한 queryability 부재**로 두고, review skip reason은 live breakdown으로 해석할 수 있다.
- 2026-06-06T08:33:25Z 최신 acquisition snapshot에서는 `newUsers` 537 / 직전 384 = `+39.8%`, `activeUsers` 767 / 직전 594 = `+29.1%`였지만, `sessions`는 4,951 / 직전 6,297 = `-21.4%`이고 `Direct` 신규가 335 / 537 = `62.4%`로 과다 상태를 유지했다. `Organic Search` 신규는 202명으로 #65 기준선 178명을 넘었지만, #65/#242 판단은 신규 유저 반등보다 Play Console Search/Explore, external/campaign, Paid Search 집행 여부 확인을 우선한다.
- monetization은 광고 metadata 일부가 복구됐고 PR #293에서 `ad_banner_impression` / `ad_banner_click` / `ad_banner_revenue`로 source split 구현이 끝났다. 다만 PR #293 포함 commit은 아직 `origin/main`/`v1.7.7`에 없으므로, PR #293 포함 production 배포 후 14일 재조회 전까지 placement별 결론은 보류한다. 2026-06-03 소량 `ad_banner_*` smoke는 queryability 확인용이다. 자세한 query template은 `docs/ADMOB_MONETIZATION_RUNBOOK.md`를 따른다.
- 2026-06-03 09:12 KST screen quality smoke에서는 최근 14일 `screen_view` `22,584`, `(not set)` `11,793`, blank `1,987`, combined `13,780 / 22,584 = 61.0%`가 확인됐다. 하지만 PR #296(`47e43784...`)과 PR #318(`8d2ee10...`)은 `origin/develop`에는 있고 `origin/main`/`v1.7.7`에는 없으므로, 이 값을 post-fix 14일 성과로 승격하지 않는다. #13 screen quality closure는 PR #296/#318 포함 release/tag/Play deploy 후 같은 쿼리 창으로 다시 판단한다.
- 2026-06-06T08:33:25Z metrics snapshot의 30일 합산에서도 `screen_view` `38,338` 중 `(not set)+blank` gap `24,627`(`64.2%`)가 남아 있고, 최신 관측 version `1.7.7` active share는 `145 / 767 = 18.9%`로 `주의`다. 이 30일 값은 위 14일 smoke와 같은 분모가 아니므로 추세 결론으로 합치지 않는다. 운영 판단만 고정한다: #13은 추가 screen_view 코드 PR이 아니라 PR #296/#318 포함 release/tag/Play deploy 후 D+14 재측정 전까지 open 유지한다.
- 실제 등록 우선순위, registration ledger, issue/PR handoff 형식은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 둔다.
