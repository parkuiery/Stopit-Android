# Stopit AdMob 성과 감사 / 안전한 수익화 실험 런북

이 문서는 GitHub issue #16 `AdMob 성과 감사 및 안전한 수익화 실험 설계`를 위한 **문서/운영 slice**다.

목표는 광고 수익 숫자만 보는 것이 아니라,

1. 광고 단위별 성과를 반복 가능하게 감사하고
2. `(not set)` 계측 누락을 먼저 찾아내고
3. 활성화·유지·신뢰를 해치지 않는 **한 개의 안전한 수익화 실험**만 정의하는 것이다.

> 이 문서만으로 issue #16이 닫히지는 않는다. 실제 광고 단위 계측 보정, 성과표 작성, 실험 구현/배포는 후속 code/product PR에서 수행한다.

## 현재 기준선

issue #16에 기록된 최근 30일 기준선:

- 분석 기간: 최근 30일
- total ad revenue: `$2.168155`
- active users: `456`
- ARPU: `$0.004755`
- ad impressions: `18,731`
- ad clicks: `12`
- CTR: `0.1%`
- eCPM: `$0.116`

해석:

- 전체 광고 수익 규모가 작다.
- 노출 대비 클릭과 eCPM이 모두 낮아 **광고 위치 최적화, 광고 단위 계측 품질, 실험 우선순위**를 분리해서 봐야 한다.
- 지금 단계에서 “광고를 더 붙인다”는 접근은 위험하고, 먼저 **무엇이 벌어지는지 보이는 상태**를 만드는 편이 안전하다.

## 언제 이 문서를 쓰는가

다음 상황에서 기본 런북으로 사용한다.

- 광고 수익이 낮은데 어떤 광고 단위가 원인인지 불명확할 때
- GA4/AdMob에서 `adUnitName = (not set)` 또는 빈 값이 의심될 때
- 광고 실험이 활성화/유지/신뢰를 해칠 수 있어 guardrail이 먼저 필요할 때
- 수익화 아이디어가 생겼지만, 바로 구현 이슈로 만들기 전에 운영 기준이 필요할 때

## Source of truth

먼저 같이 보는 문서/소스:

- `docs/METRICS_ANALYSIS.md`
- `docs/PRODUCT_METRICS_DASHBOARD.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
- `docs/ops/stopit/metrics-context.md`
- 광고 화면/노출 문맥을 담는 analytics 및 UI 코드 (`TrackedBannerAd.kt` / `TrackedBannerAdTest.kt`)
- 광고 앱 ID / 광고 단위 ID 설정 표면 (`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/uiery/keep/analytics/AdPlacement.kt`)
- GA4 Analytics Data API / AdMob 보고서

운영 원칙:

- 과거 문서의 수치는 참고값일 뿐이고, 실제 판단은 **이번 분석에서 다시 조회한 수치**를 source of truth로 둔다.
- 계측 누락이 있으면 제품/수익화 결론 confidence를 낮춘다.
- 활성화/신뢰를 해치는 실험은 revenue가 좋아 보여도 기본안으로 채택하지 않는다.

## 현재 #13 queryability 경계

2026-05-29 live 확인 기준으로 광고/수익화 해석에 필요한 `customEvent:*` 축은 아직 GA4 Admin에 등록되지 않았다.

- metadata 결과: `customUser:routines_count`만 확인, `customEvent:*`는 없음
- monetization smoke (`ad_impression` / `ad_click` / `ad_revenue` by `customEvent:ad_placement`, `customEvent:screen_context`, `customEvent:ad_unit_id`):
  - `400 INVALID_ARGUMENT`
  - `Field customEvent:ad_placement is not a valid dimension.`

즉, 현재 `adUnitName` / `adFormat` 같은 AdMob 쪽 집계는 볼 수 있어도, Stopit 제품 문맥에서 중요한 `ad_placement`, `screen_context`, `ad_unit_id` 기준 해석은 아직 **미등록 queryability gap** 때문에 낮은 confidence 상태다.

추가 주의:

- 이번 smoke는 광고 문맥 축이 막혀 있다는 대표 증거이고, Required인 `ad_format`도 아직 live `customEvent:*` queryability가 확보됐다고 보지 않는다.
- Recommended인 `ad_value_micros`까지 포함한 placement/context별 수익 재집계도 같은 외부 경계 뒤에 있다.

운영 원칙:

- placement별 CTR/eCPM 결론을 강하게 내리기 전에 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 광고 파라미터 등록 상태를 먼저 확인한다.
- `adUnitName = (not set)` 문제와 `customEvent:*` 미등록 문제를 섞지 않는다. 전자는 AdMob 보고 축 문제일 수 있고, 후자는 GA4 Admin 등록 문제다.
- issue #16 follow-through에서는 revenue 표를 만들더라도, `ad_placement` / `screen_context`가 아직 미등록이면 "제품 문맥까지 포함한 위치 최적화 결론"은 보류라고 명시한다.

## 먼저 답해야 할 질문

광고/수익화 검토는 아래 질문 순서로 본다.

1. 어떤 광고 단위가 실제로 노출되고 있는가?
2. `(not set)` 또는 이름 없는 광고 단위가 있는가?
3. `ad_impression` / `ad_click` / `ad_revenue`와 `screen_context` / `ad_placement` / `ad_unit_id` 계약이 코드·문서·GA4 조회 가정에서 일치하는가?
4. 어떤 위치가 노출은 많은데 CTR/eCPM이 낮은가?
5. 그 위치가 정말 유지할 가치가 있는가?
6. 광고 최적화보다 먼저, 더 안전한 수익화 가설이 있는가?

## 광고 단위 감사 기본 표

후속 분석/PR에서는 최소한 아래 표를 채운다.

| adUnitName | adFormat | impressions | clicks | CTR | revenue | eCPM | 노출 문맥 | 판단 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 예: HomeBanner | banner | 0 | 0 | 0% | $0.00 | $0.00 | 홈 화면 하단 | 유지/축소/계측수정 |

## 2026-05-31 광고 단위 감사 스냅샷

조회 조건:

- 기간: `30daysAgo..yesterday`
- GA4 property: `properties/502544175`
- 쿼리: `adUnitName`, `adFormat` × `totalAdRevenue`, `publisherAdImpressions`, `publisherAdClicks`, `activeUsers`
- 실행 근거: `/Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py` 및 동일 credential/session을 사용한 `runReport`

전체 기준선:

| 지표 | 값 |
| --- | ---: |
| activeUsers | 516 |
| totalAdRevenue | $1.900233 |
| publisherAdImpressions | 20,939 |
| publisherAdClicks | 11 |
| ARPU | $0.003683 |
| CTR | 0.053% |
| eCPM | $0.091 |

광고 단위별 분해:

| adUnitName | adFormat | impressions | clicks | CTR | revenue | eCPM | impression share | activeUsers |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 블락 상단 배너 | Banner | 8,874 | 5 | 0.056% | $1.129637 | $0.127 | 42.4% | 207 |
| `(not set)` | banner | 8,198 | 0 | 0.000% | $0.000000 | $0.000 | 39.2% | 225 |
| 홈 하단 배너 | Banner | 1,565 | 3 | 0.192% | $0.363071 | $0.232 | 7.5% | 301 |
| 메뉴 하단 배너 | Banner | 844 | 0 | 0.000% | $0.157902 | $0.187 | 4.0% | 165 |
| 잠금 하단 배너 | Banner | 503 | 2 | 0.398% | $0.130988 | $0.260 | 2.4% | 75 |
| 루틴 목록 하단 배너 | Banner | 422 | 1 | 0.237% | $0.068327 | $0.162 | 2.0% | 70 |
| empty `adUnitName` | banner | 316 | 0 | 0.000% | $0.000000 | $0.000 | 1.5% | 41 |
| 루틴 공백 하단 배너 | Banner | 131 | 0 | 0.000% | $0.033163 | $0.253 | 0.6% | 65 |
| 사용 기록 하단 배너 | Banner | 86 | 0 | 0.000% | $0.017145 | $0.199 | 0.4% | 62 |

판단:

- `adUnitName = (not set)`와 empty `adUnitName`이 합쳐서 `8,514 / 20,939 = 40.7%`의 노출을 차지한다. 따라서 **placement 최적화보다 계측 매핑 보정이 먼저**다.
- 수익은 `블락 상단 배너`가 가장 크지만 CTR은 `5 / 8,874 = 0.056%`로 낮다. 이 위치가 실제 차단 경험과 겹치므로, 추가 노출 확대 후보가 아니라 **신뢰 guardrail 감사 대상**이다.
- `홈 하단 배너`, `잠금 하단 배너`, `루틴 목록 하단 배너`는 노출 규모는 작지만 CTR/eCPM이 상대적으로 높다. 다만 잠금/긴급해제 흐름에 가까운 위치는 수익보다 trust risk를 우선해서 본다.
- 이번 스냅샷만으로 issue #16 완료 기준을 닫을 수 없다. 완료 전에는 `(not set)`/empty 원인을 코드·GA4 등록·AdMob mapping 중 어디에서 해결할지 정하고, 수정 후 같은 표를 다시 조회해야 한다.

다음 실행 후보:

1. 아래 **코드 기준 광고 placement 계약**과 GA4 `adUnitName` 결과를 대조한다.
2. `TrackedBannerAd`/AdMob adapter 쪽에서 every impression/click/revenue payload가 동일한 `adUnitName`/`ad_unit_id` 계약을 갖는지 확인한다.
3. GA4 metadata에서 광고 관련 custom dimensions/metrics 등록 여부를 확인하고, 누락이면 #13 GA4 등록 runbook과 연결한다.
4. `(not set)` 노출이 특정 appVersion, screen, event, source 쪽에 집중되는지 `appVersion`, `unifiedScreenName`, `eventName` 분해 쿼리로 좁힌다.
5. 보정 후 14일 기준으로 이 표를 다시 채워 광고 단위별 성과표 completion 여부를 판단한다.

## 2026-06-01 GA4 AdMob 파라미터 등록/조회 preflight

이번 docs-lane closure pass에서 #16의 남은 경계가 “GA4 Admin 등록 누락”인지 “런타임 이벤트 소스/SDK 자동 이벤트 충돌”인지 분리하기 위해 metadata와 custom dimension breakdown을 추가 확인했다.

확인 명령:

- `properties/502544175/metadata`에서 `customEvent:ad_unit_id`, `customEvent:ad_placement`, `customEvent:screen_context`, `customEvent:ad_format`, `customEvent:ad_value_micros`, `customEvent:screen_name` 존재 여부 확인
- `eventName = ad_impression | ad_click | ad_revenue` 필터로 `customEvent:ad_placement`, `customEvent:ad_unit_id` 분해 조회

결과:

| 확인 항목 | 결과 |
| --- | --- |
| `customEvent:ad_unit_id` | 등록됨 (`Ad Unit ID`) |
| `customEvent:ad_placement` | 등록됨 (`Ad Placement`) |
| `customEvent:screen_context` | 등록됨 (`Screen Context`) |
| `customEvent:ad_format` | 등록됨 (`Ad Format`) |
| `customEvent:ad_value_micros` | 등록됨 (`Ad Value Micros`) |
| `customEvent:screen_name` | 등록됨 (`Screen Name`) |
| event-scoped custom dimensions | 22개 확인 |
| event-scoped custom metrics | 5개 확인 |

최근 30일 `ad_impression` × `customEvent:ad_placement` 조회:

| `customEvent:ad_placement` | eventCount | totalUsers |
| --- | ---: | ---: |
| `(not set)` | 19,821 | 317 |
| `block_top` | 546 | 45 |
| empty value | 340 | 46 |
| `home_bottom` | 112 | 55 |
| `menu_bottom` | 74 | 29 |
| `lock_bottom` | 61 | 17 |
| `routine_list_bottom` | 15 | 11 |
| `routine_empty_bottom` | 11 | 9 |
| `history_bottom` | 7 | 7 |

추가 확인:

- `ad_impression` × `customEvent:ad_unit_id`도 같은 모양이다. `(not set)`이 `19,823`건이고 코드 기준 ad unit id가 붙은 이벤트는 소수만 보인다.
- `ad_click` × `customEvent:ad_placement`은 `11`건 모두 `(not set)`으로 조회됐다.
- `ad_revenue` × `customEvent:ad_placement`은 `(not set)` `7,694`건 + 코드 placement별 이벤트가 함께 보인다.

해석:

- 광고 관련 custom dimension/metric은 이제 GA4 metadata에 등록되어 있다. 따라서 #16의 다음 repo-internal/ops 작업은 단순 “GA4 Admin 등록”이 아니라, **같은 이벤트명(`ad_impression`, `ad_click`, `ad_revenue`)으로 들어오는 SDK 자동 수집 이벤트와 앱 custom 이벤트를 어떻게 분리/명명/집계할지 결정하는 것**이다.
- 현재 `publisherAdImpressions` 기준 `adUnitName` 표와 앱 custom event 기준 `customEvent:ad_placement` 표를 같은 표처럼 합치면 안 된다. 전자는 AdMob/GA4 광고 단위 표시명 중심이고, 후자는 `TrackedBannerAd`가 직접 붙인 custom parameter 중심이다.
- `ad_click`이 모두 `(not set)`으로 조회된 것은 클릭 이벤트가 앱 custom event 파라미터 없이 들어오거나, 앱 custom click 이벤트가 SDK 자동 이벤트와 같은 이름으로 섞여서 dimension 해석이 희석됐을 가능성을 시사한다.

#16의 다음 실행 경계:

1. code-lane에서 `TrackedBannerAd` custom 이벤트명을 SDK/GA4 권장 자동 이벤트명과 충돌하지 않게 분리할지 검토한다. 예: `ad_impression`을 그대로 쓸지, 제품 분석용 이벤트를 `ad_unit_impression`/`ad_banner_impression`처럼 별도 명명할지 결정한다.
2. 만약 이름을 유지한다면, GA4 report query가 SDK 자동 이벤트와 앱 custom 이벤트를 분리할 수 있는 필터(`customEvent:ad_placement != (not set)` 등)를 갖도록 runbook/query template을 고정한다.
3. 보정 PR 또는 GA4 query 계약 변경 후 14일 재조회에서 `publisherAdImpressions` 표와 custom placement 표를 따로 보고, 둘을 합산하지 않는다.

## GA4 query template: SDK 자동 이벤트와 앱 custom 이벤트 분리

#16의 현재 경계는 “광고 custom dimension 등록 여부”가 아니라 **같은 이벤트명 아래 섞이는 SDK 자동 이벤트와 앱 custom 이벤트를 분리해서 볼 수 있느냐**다. 다음 template은 code-lane이 이벤트명을 바꾸기 전/후 모두에서 사용할 수 있는 최소 분리 조회 계약이다.

운영 규칙:

- `publisherAdImpressions` / `publisherAdClicks` / `totalAdRevenue` + `adUnitName` 표는 AdMob/GA4 광고 단위 성과표다.
- `eventCount` + `customEvent:ad_placement` / `customEvent:ad_unit_id` 표는 `TrackedBannerAd` custom parameter coverage 표다.
- 두 표를 합산하지 않는다. 첫 번째 표는 수익·노출 source of truth, 두 번째 표는 앱 custom event coverage/source 분리 진단용이다.
- 앱 custom event coverage를 볼 때는 `customEvent:ad_placement`가 `(not set)` 또는 empty가 아닌 행만 따로 계산한다. 이 비율이 낮으면 placement별 수익화 결론을 보류한다.

```python
# Run inside <repo-root> with the local Analytics service account path.
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
# Do not commit or paste the real service-account path/value.
# Set this to the local Analytics read-only service-account JSON path when running.
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
    response.raise_for_status()
    data = response.json()
    headers = [h['name'] for h in data.get('dimensionHeaders', [])]
    headers += [h['name'] for h in data.get('metricHeaders', [])]
    print('\t'.join(headers))
    for row in data.get('rows', []):
        values = row.get('dimensionValues', []) + row.get('metricValues', [])
        print('\t'.join(v.get('value', '') for v in values))


def event_filter(event_name):
    return {
        'filter': {
            'fieldName': 'eventName',
            'stringFilter': {'matchType': 'EXACT', 'value': event_name},
        },
    }


def custom_placement_present_filter():
    # GA4 Data API cannot express "not (not set) and not empty" as a single
    # reusable named segment here, so keep the raw breakdown plus this rule:
    # count only rows where customEvent:ad_placement is neither '(not set)' nor ''.
    return ['(not set)', '']


# 1) AdMob/GA4 publisher surface: revenue/impression/click source of truth.
run_report('publisher_ad_units_30d', {
    'dateRanges': [{'startDate': '30daysAgo', 'endDate': 'yesterday'}],
    'dimensions': [{'name': 'adUnitName'}, {'name': 'adFormat'}],
    'metrics': [
        {'name': 'totalAdRevenue'},
        {'name': 'publisherAdImpressions'},
        {'name': 'publisherAdClicks'},
        {'name': 'activeUsers'},
    ],
    'orderBys': [{'metric': {'metricName': 'publisherAdImpressions'}, 'desc': True}],
    'limit': 50,
})

# 2) App custom-event coverage: do not use this as revenue source of truth.
for event_name in ['ad_impression', 'ad_click', 'ad_revenue']:
    run_report(f'{event_name}_custom_placement_breakdown_30d', {
        'dateRanges': [{'startDate': '30daysAgo', 'endDate': 'yesterday'}],
        'dimensions': [{'name': 'customEvent:ad_placement'}, {'name': 'customEvent:ad_unit_id'}],
        'metrics': [{'name': 'eventCount'}, {'name': 'totalUsers'}],
        'dimensionFilter': event_filter(event_name),
        'orderBys': [{'metric': {'metricName': 'eventCount'}, 'desc': True}],
        'limit': 50,
    })

print('Coverage rule: custom-covered rows exclude', custom_placement_present_filter())
```

판단 template:

```md
## AdMob event-source split
- Window:
- Publisher surface:
  - publisherAdImpressions:
  - publisherAdClicks:
  - totalAdRevenue:
  - `(not set)` + empty `adUnitName`:
- App custom-event coverage:
  - `ad_impression` total eventCount:
  - `customEvent:ad_placement` covered eventCount:
  - coverage = covered / total:
  - `ad_click` covered eventCount:
  - `ad_revenue` covered eventCount:
- Decision:
  - [ ] safe to compare placements
  - [ ] mapping/source split must be fixed first
- Follow-up:
```

2026-06-01 template smoke 결과:

- 실행: 위 template의 `customEvent:ad_placement` breakdown을 `30daysAgo..yesterday`로 실행.
- `ad_impression`: total `21,159`, custom-covered `912`, coverage `4.31%`.
- `ad_click`: total `11`, custom-covered `0`, coverage `0.00%`.
- `ad_revenue`: total `8,602`, custom-covered `908`, coverage `10.56%`.
- 판단: 현재 상태는 placement별 CTR/eCPM 결론을 내리기엔 custom coverage가 낮다. 새 광고 실험보다 SDK 자동 이벤트와 앱 custom 이벤트의 이름/필터 분리 또는 query contract 고정이 먼저다.

## code-lane handoff: 광고 custom event source 분리

#16을 다음 실행 lane으로 넘길 때는 아래 계약을 그대로 구현 후보 범위로 사용한다. 핵심은 “수익화 실험”이 아니라 **SDK 자동 광고 이벤트와 앱 custom 광고 이벤트를 분석에서 분리 가능하게 만드는 것**이다.

### 문제 계약

- 현재 `publisherAdImpressions`/`publisherAdClicks`/`totalAdRevenue`는 AdMob/GA4 publisher surface 기준 수익 지표다.
- 앱 코드의 `TrackedBannerAd`도 `ad_impression`, `ad_click`, `ad_revenue` 이름으로 custom parameter를 기록한다.
- GA4 breakdown에서 `customEvent:ad_placement` coverage가 낮기 때문에, 같은 이벤트명 아래 SDK 자동 이벤트와 앱 custom event가 섞여 보일 수 있다.
- 따라서 placement별 CTR/eCPM 최적화나 광고 제거 관심도 실험을 진행하기 전에, code-lane은 이벤트명/필터/문서 계약 중 하나를 선택해 source split을 고정해야 한다.

### 허용되는 해결 방향

1. **이벤트명 분리(권장)**
   - 앱 custom event를 SDK 자동 이벤트와 충돌하지 않는 이름으로 바꾼다.
   - 후보: `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue`.
   - `KeepAnalytics`, `FirebaseKeepAnalytics`, `TrackedBannerAd`, 관련 테스트, `docs/ANALYTICS_EVENT_DICTIONARY.md`, GA4 registration runbook을 같은 PR에서 동기화한다.
2. **이벤트명 유지 + query contract 고정(보수적 대안)**
   - 이벤트명은 유지하되, 모든 운영 쿼리가 `customEvent:ad_placement` / `customEvent:ad_unit_id` present 행만 앱 custom coverage로 해석하도록 고정한다.
   - 이 경우에도 PR body와 문서에 “publisher surface와 앱 custom-event coverage를 합산하지 않는다”를 명시한다.

### 완료 기준

- [ ] `TrackedBannerAdTest` 또는 동등한 analytics payload 테스트가 impression/click/revenue 이벤트명과 `ad_placement`, `ad_unit_id`, `screen_context`, `ad_format`, `screen_name` payload를 고정한다.
- [ ] `docs/ANALYTICS_EVENT_DICTIONARY.md`가 선택한 이벤트명/필터 계약을 source of truth로 설명한다.
- [ ] `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`가 새 이벤트명 또는 유지된 이벤트명의 custom dimension 조회 방식을 설명한다.
- [ ] #16 PR/이슈에는 보정 배포 후 14일 재조회 기준이 남는다.
- [ ] 배포 전에는 `Refs #16`가 맞고, 14일 재조회와 실험 선택까지 끝났을 때만 `Closes #16`를 사용한다.

### 검증 권장 명령

```bash
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.analytics.TrackedBannerAdTest'
./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest'
git diff --check
rg -n 'ad_banner_impression|ad_banner_click|ad_banner_revenue|ad_impression|customEvent:ad_placement|publisherAdImpressions' docs/ANALYTICS_EVENT_DICTIONARY.md docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md docs/ADMOB_MONETIZATION_RUNBOOK.md
```

## 코드 기준 광고 placement 계약

2026-05-31 문서 closure pass에서 main source의 `TrackedBannerAd` call site를 재확인한 코드 기준 계약이다. 이 표는 GA4/AdMob 결과의 `adUnitName`과 앱 코드의 `ad_placement`/`ad_unit_id`가 서로 같은 화면을 가리키는지 대조할 때 사용한다.

| 코드 위치 | `screen_name` | `screen_context` | `ad_placement` | `ad_unit_id` | 2026-05-31 GA4 표시명 | 운영 판단 |
| --- | --- | --- | --- | --- | --- | --- |
| `BlockScreen.kt` | `block` | `blocked_app` | `block_top` | `ca-app-pub-1537867411423705/5467753282` | `블락 상단 배너` | 차단 경험과 가장 가까운 신뢰 민감 위치. 노출 확대 금지, 우선 guardrail 감사 대상. |
| `HomeScreen.kt` | `home` | `main` | `home_bottom` | `ca-app-pub-1537867411423705/5120253017` | `홈 하단 배너` | 비핵심 하단 영역이지만 첫 잠금 CTA를 방해하면 안 됨. |
| `MenuScreen.kt` | `menu` | `settings` | `menu_bottom` | `ca-app-pub-1537867411423705/3270829732` | `메뉴 하단 배너` | 설정/피드백/삭제방지와 같은 신뢰 설정 흐름을 가리지 않는지 확인. |
| `LockScreen.kt` | `lock` | `routine` / `manual` | `lock_bottom` | `ca-app-pub-1537867411423705/7892727021` | `잠금 하단 배너` | 긴급해제와 인접한 안전 민감 위치. 수익보다 이탈/불만 guardrail 우선. |
| `RoutineListContent.kt` | `routine` | `list` | `routine_list_bottom` | `ca-app-pub-1537867411423705/7750072748` | `루틴 목록 하단 배너` | 반복 사용자 화면. 활성 루틴 설정 방해 여부 확인. |
| `RoutineNoContent.kt` | `routine` | `empty_state` | `routine_empty_bottom` | `ca-app-pub-1537867411423705/9271028233` | `루틴 공백 하단 배너` | 첫 루틴 생성 CTA와 충돌하면 안 됨. |

참고: `history_bottom`은 2026-06-01 code-lane 정리 후 더 이상 active main-source `TrackedBannerAd` call site가 아니다. legacy `HistoryScreen` route/screen은 제거됐고, 사용자는 `LockHistoryScreen` canonical surface로 진입한다.

해석:

- 코드 기준 call site는 모두 `TrackedBannerAd`를 지나므로 앱 내부 이벤트(`ad_impression`, `ad_click`, `ad_revenue`)에는 `screen_name`, `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id`가 붙어야 한다.
- 그런데 GA4/AdMob의 `adUnitName` 기준으로 `(not set)` + empty가 40.7%라면, 우선순위는 **새 광고 실험**이 아니라 **AdMob 단위 이름/GA4 linkage/custom dimension/SDK 자동 수집 이벤트와 앱 custom 이벤트의 매핑 차이 진단**이다.
- `adUnitName`은 AdMob/GA4가 보여주는 광고 단위 표시명이고, `ad_unit_id`는 앱 custom event 파라미터다. 둘을 같은 필드처럼 해석하지 않는다. 두 표를 연결하려면 `ad_unit_id` custom dimension 등록 여부와 AdMob unit 이름 매핑을 먼저 확인한다.

## issue #250: flavor별 광고 설정 계약

#250은 #16의 성과 감사와 연결되지만, 문제의 핵심은 성과표가 아니라 **production AdMob application/ad unit id가 Manifest와 여러 Compose 화면에 분산된 설정 계약 drift**다. docs-lane PR #254에서 구현 handoff를 먼저 고정했고, code-lane PR은 아래 계약을 실제 Gradle/Manifest/Compose call site에 반영한다.

### 이전 분산 표면

2026-06-01 `origin/develop` 기준으로 production 광고 ID가 직접 박혀 있던 표면은 다음과 같다.

| 표면 | 현재 위치 | production ID 종류 | 문제 |
| --- | --- | --- | --- |
| AdMob application id | `app/src/main/AndroidManifest.xml` meta-data `com.google.android.gms.ads.APPLICATION_ID` | `ca-app-pub-1537867411423705~6734784292` | flavor/build type별 교체가 어렵고 dev/debug가 production app id를 쓰는지 정적 가드가 없다. |
| `block_top` | `BlockScreen.kt` | ad unit id | 차단 경험과 가장 가까운 신뢰 민감 위치인데 UI 코드가 production id까지 소유한다. |
| `home_bottom` | `HomeScreen.kt` | ad unit id | 첫 잠금 CTA 주변의 광고 설정 변경이 UI diff와 섞인다. |
| `menu_bottom` | `MenuScreen.kt` | ad unit id | 설정/피드백/삭제방지 흐름과 광고 inventory 변경이 분리되지 않는다. |
| `lock_bottom` | `LockScreen.kt` | ad unit id | 긴급해제 인접 위치라 dev/prod 혼동이 특히 위험하다. |
| `routine_list_bottom` | `RoutineListContent.kt` | ad unit id | 반복 사용 화면의 광고 설정을 한 곳에서 감사하기 어렵다. |
| `routine_empty_bottom` | `RoutineNoContent.kt` | ad unit id | 첫 루틴 생성 CTA 주변 광고 설정이 코드 곳곳에 흩어져 있다. |

참고: `history_bottom`은 active main-source call site가 아니다. 다시 추가하려면 이 계약표와 광고 config source에 먼저 등록해야 한다.

### 구현 계약

현재 코드 계약:

1. `app/build.gradle.kts`의 flavor별 `setAdMobConfig(...)`가 AdMob application id와 banner unit id의 source of truth다.
   - `prod`는 production application id와 placement별 production ad unit id를 쓴다.
   - `dev`는 Google sample app id와 sample banner ad unit id를 쓴다.
   - Manifest `com.google.android.gms.ads.APPLICATION_ID`는 literal이 아니라 `${adMobApplicationId}` placeholder로 resolve된다.
2. `AdPlacement`가 Compose call site와 analytics payload가 공유하는 placement inventory다.
   - placement key는 `block_top`, `home_bottom`, `menu_bottom`, `lock_bottom`, `routine_list_bottom`, `routine_empty_bottom`이다.
   - Compose call site는 production id 문자열을 직접 들고 있지 않고 `AdPlacement.*.adUnitId`만 전달한다.
3. production ad unit id는 prod config / inventory contract에만 존재해야 한다.
   - `app/src/main/java/**` UI 파일에서 `ca-app-pub-1537867411423705/` 문자열이 사라져야 한다.
   - test/dev config에는 Google sample banner id를 사용한다.
4. analytics 계약은 유지한다.
   - `TrackedBannerAd` payload의 `ad_placement`는 placement key와 동일해야 한다.
   - `ad_unit_id`는 실행 flavor의 resolved id를 기록하되, 운영 문서/테스트에서 dev/test id와 prod id를 구분한다.

### 정적/테스트 가드

#250을 닫는 PR에는 최소 아래 가드 중 하나 이상이 필요하다.

```text
- production id 문자열이 UI call site에 남아 있지 않음을 확인하는 JVM/정적 테스트
- dev/debug 광고 config가 production `ca-app-pub-1537867411423705/` ad unit id를 쓰지 않음을 확인하는 테스트
- Manifest application id가 flavor별 placeholder/resValue에서 resolve된다는 Gradle/manifest 검사
- placement inventory가 config source와 이 문서의 표를 같은 순서/키로 설명한다는 regression check
```

권장 검증 명령:

```bash
./gradlew --console=plain :app:testDevDebugUnitTest --tests '*Ad*Config*' --tests '*TrackedBannerAd*'
./gradlew --console=plain :app:testDevDebugUnitTest
./gradlew --console=plain :app:assembleProdDebug
rg -n 'ca-app-pub-1537867411423705/' app/src/main/java
rg -n 'com.google.android.gms.ads.APPLICATION_ID|manifestPlaceholders|adMob' app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/*/AndroidManifest.xml
```

`rg -n 'ca-app-pub-1537867411423705/' app/src/main/java`는 #250 완료 PR에서는 **결과가 없어야 한다**. production ID는 config source 또는 prod flavor boundary에서만 보이게 둔다.

### `Refs` / `Closes` 판단

- 이 docs-lane 반영은 #250의 implementation handoff이므로 `Refs #250`가 맞다.
- `Closes #250`는 Manifest application id, ad unit config 중앙화, dev/debug non-production guard, placement inventory, `:app:testDevDebugUnitTest`까지 충족한 code/maintenance PR에서만 사용한다.

## #16 closure-pass 게이트

#16을 `Closes`로 전환하려면 다음 repo-internal/외부 경계를 분리해서 모두 확인한다.

1. **문서/계약 완료**
   - 위 코드 기준 placement 표가 최신 call site와 일치한다.
   - `docs/ANALYTICS_EVENT_DICTIONARY.md`의 AdMob 파라미터 계약과 이 문서의 audit 절차가 같은 필드명을 쓴다.
   - `docs/METRICS_ANALYSIS.md`와 `docs/PRODUCT_METRICS_DASHBOARD.md`에서 수익화 분석이 이 런북으로 이어진다.
2. **계측/매핑 보정 완료**
   - `ad_unit_id`, `ad_placement`, `screen_context`가 GA4 custom dimension으로 조회 가능하다.
   - `adUnitName = (not set)` 또는 empty의 원인이 AdMob 단위 naming, GA4 linkage, SDK 자동 이벤트, 앱 custom event 중 어디인지 분류됐다.
   - 원인별 보정 PR 또는 Play/GA4/Admin 수동 조치가 기록됐다.
3. **14일 재조회 완료**
   - 보정 배포 또는 Admin 반영 시각을 기준으로 `14daysAgo..yesterday` 재조회 표를 남긴다.
   - `(not set)` + empty 비중이 분자/분모로 기록되고, 줄지 않았으면 placement 최적화/실험을 진행하지 않는다.
4. **실험 선택 완료**
   - guardrail을 포함한 단 하나의 수익화 실험을 선택한다.
   - 차단/긴급해제/권한/첫 루틴 CTA에 광고를 추가하는 실험은 기본 후보에서 제외한다.
   - 실험 전후 비교 기간, appVersion, 분자/분모가 명시된다.

판단 규칙:

- `adUnitName`이 `(not set)`이면 **계측 보정 우선**이다.
- `adUnitName`은 보이는데 `ad_placement` / `screen_context` / `ad_unit_id` 계약이 문서·코드와 어긋나면 placement 결론을 보류한다.
- impressions는 큰데 CTR/eCPM이 모두 낮으면 **UX 비용 대비 가치가 낮은 위치** 후보로 본다.
- revenue는 작아도 사용자 신뢰를 거의 해치지 않는 위치면 유지 후보가 될 수 있다.
- 차단/긴급해제/권한/루틴 설정의 핵심 흐름에서 광고가 방해되면 revenue와 무관하게 위험 신호다.

## GA4/AdMob 감사 절차

### 1. 전체 기준선 다시 확인

최소 확인 지표:

- `totalAdRevenue`
- `averageRevenuePerUser`
- `publisherAdImpressions`
- `publisherAdClicks`
- `activeUsers`

재계산:

- CTR = `publisherAdClicks / publisherAdImpressions`
- eCPM = `totalAdRevenue / publisherAdImpressions * 1000`
- 30일 ARPU = `totalAdRevenue / activeUsers`

### 2. 광고 단위별 분해

사전 계약 확인:

- `TrackedBannerAd.kt` / `TrackedBannerAdTest.kt` 기준으로 현재 앱은 `ad_impression`, `ad_click`, `ad_revenue`를 기록한다.
- placement 분석 전 `screen_name`, `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id`, `ad_value_micros`가 `docs/ANALYTICS_EVENT_DICTIONARY.md`와 동일한지 먼저 본다.

기본 차원/지표:

- dimensions: `adUnitName`, `adFormat`
- metrics: `totalAdRevenue`, `publisherAdImpressions`, `publisherAdClicks`, `activeUsers`

확인할 것:

- `(not set)` 또는 빈 광고 단위 존재 여부
- 노출 상위 광고 단위와 수익 상위 광고 단위가 같은지
- CTR/eCPM이 유난히 낮은 광고 위치가 있는지
- 광고 형식별 성과 차이

### 3. 제품 문맥과 함께 해석

광고 성과표만 보면 안 된다. 아래 문맥을 같이 본다.

- 홈/메뉴/기록 같은 비핵심 화면인지
- 차단 화면/긴급해제/권한 같은 신뢰 민감 구간인지
- 신규 활성화 퍼널 초반인지, 기존 사용자 반복 사용 구간인지
- `(not set)` screen view 또는 analytics drift와 겹치는지

## 광고를 두면 안 되는 구간

기본 금지/매우 보수적으로 볼 구간:

- 접근성 권한 설정 흐름
- 첫 잠금 활성화의 핵심 CTA 흐름
- 실제 차단 화면을 해제/우회하게 보이게 하는 위치
- 긴급해제 의사결정 직전/직후의 안전 민감 구간
- 루틴/차단 신뢰를 훼손하는 과한 전면 개입

운영 원칙:

- 스탑잇의 핵심 가치는 “실제 차단”과 “신뢰 가능한 통제”다.
- 광고가 그 약속을 흐리면 단기 수익보다 장기 손해가 크다.

## 기본 guardrail

수익화 실험은 아래 guardrail을 항상 같이 둔다.

### 제품/신뢰 guardrail

- `first_lock_configured / first_open` 전환율 악화 금지
- `first_core_action_completed / first_open` 전환율 악화 금지
- `app_block_intercepted` 사용자 수 급락 금지
- 위 guardrail의 단계 의미와 분자/분모 기준은 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 따른다.
- 긴급해제/차단 핵심 흐름에서 추가 이탈 증가 금지
- review/rating 관련 부정 신호 증가 금지

### 품질 guardrail

- crash-free users rate 하락 금지
- 광고 관련 ANR/크래시 증가 금지
- 광고 단위 `(not set)` 증가 금지

### 운영 guardrail

- 14일/30일 비교 기간 명시
- 분자/분모 없는 퍼센트 주장 금지
- 버전/배포 시점을 기록하지 않은 실험 결과는 낮은 confidence로 취급

## issue #16용 기본 실험 제안

### 추천 1차 실험: `광고 제거 일회성 구매 관심도 측정`

이유:

- 현재 광고 ARPU가 매우 낮다.
- 차단/긴급해제 흐름에 광고를 더 붙이는 실험은 신뢰 리스크가 크다.
- 먼저 “광고 없는 경험에 돈을 낼 의향이 있는가”를 가볍게 측정하는 편이 더 안전하다.

실험 아이디어:

- 홈/메뉴의 비핵심 영역에 `광고 제거 예정` 또는 `광고 없이 사용하고 싶으신가요?` 수준의 **관심도 CTA**를 둔다.
- 실제 결제 구현 전, 클릭/탭 관심도만 측정한다.
- 안전/차단/긴급해제 흐름에는 넣지 않는다.

추천 추적 이벤트:

- `monetization_interest_shown`
- `monetization_interest_clicked`
- `monetization_interest_context`

실험 성공 판단 예시:

- 충분한 노출 대비 관심 클릭률이 확인된다.
- 활성화/유지/리뷰/신뢰 지표가 악화되지 않는다.
- 광고 최적화보다 유료 제거가 더 맞는 방향인지 판단 근거가 생긴다.

실험 실패 신호:

- 관심 클릭이 거의 없다.
- 홈 핵심 CTA 방해나 리뷰/유지 저하가 보인다.
- 계측 품질이 낮아 어떤 화면/사용자군 반응인지 해석할 수 없다.

## 후속 구현/분석 이슈에 남겨야 할 evidence

```md
## AdMob / monetization evidence
- Analysis window:
- Comparison window:
- Baseline metrics:
  - totalAdRevenue:
  - activeUsers:
  - publisherAdImpressions:
  - publisherAdClicks:
  - ARPU:
  - CTR:
  - eCPM:
- Ad unit breakdown:
  - top units:
  - `(not set)` presence:
  - low-value placements:
- Guardrails:
  - activation:
  - retention:
  - crash-free users:
  - review/rating:
- Experiment decision:
  - chosen experiment:
  - why this one:
  - why not riskier alternatives:
- Follow-up window:
  - 14 days:
  - 30 days:
```

## docs-lane에서 허용되는 작은 slice 예시

이 lane에서는 아래만 안전하다.

- 광고 단위 감사 절차 문서화
- 광고 application/ad unit id의 flavor별 config 계약과 code-lane handoff 정리
- 수익화 실험 우선순위/guardrail 문서화
- metrics 문서에서 광고 분석 문서를 발견 가능하게 연결

이 lane에서 하지 않는 것:

- 실제 광고 SDK/노출 로직 수정
- 전면 광고 배치 변경
- 결제 구현
- Play Console 또는 원격 설정 실험 실제 실행
- GA4 Admin에서 `ad_placement` / `screen_context` / `ad_unit_id`를 직접 등록하는 수동 작업

## 흔한 실수

- revenue만 보고 activation/trust guardrail을 빼먹기
- `(not set)` 광고 단위를 무시한 채 placement 결론을 내리기
- 차단/긴급해제 구간에 광고를 붙여도 된다고 가정하기
- 분석 기간/비교 기간 없이 CTR/eCPM만 단편적으로 비교하기
- 광고 최적화와 유료화 실험을 한 번에 섞어 원인 분리를 어렵게 만들기

## 관련 문서

- `docs/METRICS_ANALYSIS.md`
- `docs/PRODUCT_METRICS_DASHBOARD.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
- `docs/PLAY_STORE_ASO.md`
- `docs/REVIEW_PROMPT_LIFECYCLE.md`
- `docs/ops/stopit/metrics-context.md`
