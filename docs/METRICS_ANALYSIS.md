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

- Analytics 조회용 예시 경로: `/Users/uiel/Downloads/stopit-be785-7bc36acc382d.json`
- Play Console 조회용 예시 경로: `/Users/uiel/Downloads/stopit-be785-e904760dc98c.json`

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
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: #13용 GA4 Admin 수동 등록 절차, registration ledger, metadata 증적, 14일 재측정 포맷.
- `docs/PLAY_STORE_ASO.md`: #65용 Play Console ASO 실행 런북. 최종 copy, 스크린샷 구성, baseline, 반영 로그, 14일/30일 검증 포맷 포함. 현재 기준으로는 **대표님 수동 반영 완료 후 사후 복원/성과 추적 문서**다.
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`: #16용 광고 단위 감사 절차, guardrail, 안전한 수익화 실험 운영 기준.
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`: #82용 Usage Access 범위, 권한 UX, MVP 리포트 4종, 규칙 기반 추천, 개인정보/정책 가드레일.
- `docs/REVIEW_PROMPT_LIFECYCLE.md`: #17용 리뷰 프롬프트 arm/drain 규칙, skip reason, Play In-App Review 한계 문서.

## 빠른 분석 명령

아래 명령은 로컬에서 GA4 주요 지표를 뽑는 최소 예시다.

```bash
cd <repo-root>

python3 - <<'PY'
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH = '/Users/uiel/Downloads/stopit-be785-7bc36acc382d.json'

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

위 필터를 `dimensionFilter`에 넣고 `appVersion` 차원으로 보면 버전별 이벤트 의미 변화 여부를 확인할 수 있다.

### 광고 단위별 수익

전제 확인:

- `TrackedBannerAd.kt`와 `TrackedBannerAdTest.kt` 기준으로 `ad_impression`, `ad_click`, `ad_revenue` 이벤트와 `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id`, `ad_value_micros` 계약이 현재 코드와 일치하는지 먼저 본다.
- 운영 가드레일과 해석 순서는 `docs/ADMOB_MONETIZATION_RUNBOOK.md`를 같이 본다.

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
CREDENTIAL_PATH = '/Users/uiel/Downloads/stopit-be785-7bc36acc382d.json'

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
- 광고 이벤트(`ad_impression`, `ad_click`, `ad_revenue`)와 `TrackedBannerAd` 파라미터 계약 일치 여부

판단 기준:

- 화면 조회 대부분이 `(not set)`이면 제품 퍼널 결론보다 계측 개선을 먼저 한다.
- 주요 이벤트 파라미터가 GA4 차원으로 조회되지 않으면 이벤트 딕셔너리와 커스텀 차원 등록 작업을 먼저 만든다.
- docs lane이 repo 안에서 할 수 있는 범위는 registration contract / ledger / 검증 포맷 정리까지이며, 실제 GA4 Admin 등록과 배포 후 14일 재측정은 외부/manual 경계로 분리해 기록한다.
- 광고 분석 전에는 `docs/ANALYTICS_EVENT_DICTIONARY.md`의 AdMob 파라미터 계약과 `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 guardrail을 같이 확인한다.

### 2. 획득 / 신규 유입

확인 지표:

- `newUsers`
- `first_open`
- `firstUserDefaultChannelGroup`
- 최근 30일 vs 직전 30일 변화

판단 기준:

- engagement rate가 유지되는데 newUsers만 크게 하락하면 제품 사용성보다 스토어/유입 문제를 우선 본다.
- Organic Search 비중이 높으면 Play Store ASO, 키워드, 스크린샷, 리뷰 수를 우선 개선한다.

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
- 차단/긴급해제 흐름을 방해하는 광고 실험은 하지 않는다.

### 6. 신뢰 / 리뷰

확인 지표:

- Play Store 평점과 리뷰 수
- `review_prompt_eligible`, `review_prompt_shown`, `review_prompt_skipped`, `review_prompt_failed`
- `review_prompt_skipped` reason 분포
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
- Usage Access는 리포트/추천용 선택형 확장으로 다루고, 핵심 차단 기능의 필수 권한으로 만들지 않는다.
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

## 현재 기준선 예시

2026-05-23 기준 최근 30일 분석에서 확인한 주요 기준선:

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

2026-05-28 live 확인 메모:

- 최근 14일 `screen_view` 총량: `12,095`
- `(not set)` `9,390` + 빈 `unifiedScreenName` `407` = `9,797 / 12,095 = 81.0%`
- GA4 metadata에서 현재 확인된 custom dimension은 `customUser:routines_count`만 보였고 `customEvent:*` 차원/지표는 아직 확인되지 않았다.
- activation (`customEvent:permission_name`, `customEvent:source`), review (`customEvent:reason`), monetization (`customEvent:ad_placement`) runReport smoke query는 모두 `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`으로 실패했다. 즉 현재 병목은 최근 데이터 부족이 아니라 **GA4 Admin 미등록으로 인한 queryability 부재**다.
- 실제 등록 우선순위, registration ledger, issue/PR handoff 형식은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 둔다.
