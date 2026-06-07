# 버전 채택률 기반 제품 지표 판독 게이트

이 문서는 issue #359의 source of truth다. Stopit 지표를 볼 때 `develop`에 PR이 merge됐는지, SemVer tag가 존재하는지, 실제 사용자가 해당 버전을 충분히 쓰고 있는지는 서로 다른 경계다.

## 목적

최근 30일 합산 지표가 좋아 보이거나 나빠 보여도 최신 코드가 사용자에게 충분히 도달하지 않았으면 제품 결론을 내릴 수 없다. 이 게이트는 아래 질문에 답한다.

1. 최신 배포 버전 사용자가 전체 active users 중 충분한 비중인가?
2. 핵심 이벤트가 전체 합산과 최신 버전 cohort에서 같은 방향인가?
3. #13/#14/#16/#307 같은 지표 이슈를 코드/문서 미완료로 되돌릴지, release/Play/14일 관측 경계로 보류할지 판단 가능한가?

## 기본 판독 순서

1. **코드 경계 확인**
   - 관련 PR merge commit이 `origin/develop`에 있는지 확인한다.
   - 제품 결론을 live/prod로 말하려면 같은 commit이 `origin/main`, SemVer tag, Play 배포 버전에 포함됐는지도 확인한다.
2. **버전 채택률 확인**
   - 최근 30일 `appVersion`별 `activeUsers`, `sessions`, `eventCount`를 본다.
   - 최신 배포 버전 active share = 최신 배포 버전 `activeUsers` / 전체 `activeUsers`.
3. **핵심 이벤트를 버전별로 분해**
   - 전체 합산 지표와 최신 버전 cohort 지표를 나란히 기록한다.
   - 이벤트 의미가 바뀐 경우, 의미가 동일한 버전끼리만 전환율을 비교한다.
4. **confidence 판정**
   - 아래 기준에 따라 `충분`, `주의`, `보류` 중 하나를 붙인다.
5. **후속 경계 기록**
   - repo 내부 문서/코드가 끝났으면 추가 PR을 만들지 말고 release/tag/Play 배포/D+14/D+30 측정 경계를 기록한다.

## 최신 버전 active share 기준

| 상태 | 기준 | 해석 | 기본 액션 |
| --- | --- | --- | --- |
| `충분` | 최신 배포 버전 active share가 30% 이상이고, 해당 버전 이벤트 수가 주요 결론을 낼 만큼 존재 | 최신 버전 cohort 결론을 제품 판단에 사용할 수 있음 | 개선/회귀 여부를 이슈에 기록하고 다음 실행 결정 |
| `주의` | 10% 이상 30% 미만 | 방향성은 참고 가능하지만 전체 30일 합산과 섞이면 과신 위험 | 최신 버전 cohort와 전체 합산을 반드시 분리하고 D+14 재조회 예약 |
| `보류` | 10% 미만 또는 최신 버전 주요 이벤트 행이 거의 없음 | 최신 코드/계측/문구 수정의 성과로 해석 금지 | release/Play 채택 또는 D+14/D+30 관측 대기. 새 코드/문서 PR을 만들지 않음 |

운영 예시: 2026-06-07T16:13:51Z 스냅샷에서 최신 관측 버전 `1.7.7` activeUsers는 199명, 전체 activeUsers는 792명이므로 `199 / 792 = 25.1%`다. 이 상태는 `주의`이지만 30% 미만이므로, `newUsers +56.9%`나 screen/ad/review smoke를 최신 수정 성과로 승격하지 않는다.

## 필수 표준 표

### 1) 버전 채택률 표

| 기간 | appVersion | activeUsers | active share | sessions | eventCount | 판정 | 비고 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| 최근 30일 | 전체 |  | 100% |  |  | context | 전체 합산 기준 |
| 최근 30일 | 최신 배포 버전 |  |  |  |  | 충분/주의/보류 | SemVer tag / Play track 확인 필요 |
| 최근 30일 | 구버전 1 |  |  |  |  | context | pre-fix 또는 legacy cohort |

### 2) 핵심 이벤트 버전별 표

| 이벤트 | 전체 users / events | 최신 버전 users / events | 최신 버전 share | GA4 queryability | 해석 |
| --- | ---: | ---: | ---: | --- | --- |
| `first_open` |  |  |  | 기본 이벤트 | 신규 유입 분모 |
| `first_lock_configured` |  |  |  | `customEvent:source`, `customEvent:selected_app_count` 확인 | 준비 완료. 실제 차단 완료로 과장 금지 |
| `first_core_action_completed` |  |  |  | `customEvent:blocking_mode`, `customEvent:blocked_app_category_bucket` 확인 | 첫 가치 경험. raw `blocked_app_package`는 #611 privacy 계약에 따라 등록/조회 대상에서 제외 |
| `app_block_intercepted` |  |  |  | `customEvent:block_source`, `customEvent:blocked_app_category_bucket` 확인 | 실제 차단 증거. raw `blocked_app_package`는 #611 privacy 계약에 따라 등록/조회 대상에서 제외 |
| `review_prompt_eligible` |  |  |  | 기본 이벤트 | 리뷰 arm 상태 |
| `review_prompt_shown` |  |  |  | 기본 이벤트 | sheet launch 성공. 리뷰 작성 완료 아님 |
| `review_prompt_skipped` |  |  |  | `customEvent:reason` 확인 | 2026-06-02 기준 reason 조회 가능. pre-fix cohort와 분리 |
| `review_prompt_failed` |  |  |  | `customEvent:error` 확인 | 2026-06-02 기준 error 미등록 경계 |
| `ad_banner_impression` |  |  |  | `customEvent:ad_placement` / `ad_unit_id` 확인 | PR #293 포함 release 후 14일 coverage 필요 |
| `ad_banner_revenue` |  |  |  | `customEvent:ad_value_micros` 확인 | placement 수익 판단 전 coverage 확인 |

### 3) 결론 판정 표

| 관련 이슈 | 관련 PR/commit | main/tag/Play 포함 여부 | 최신 버전 active share | 판정 | 다음 액션 |
| --- | --- | --- | ---: | --- | --- |
| #13 | #296/#318/#358 (`47e43784`, `8d2ee10`, `6ceaecc4`) | `origin/develop` 포함, `origin/main`/`v1.7.7` 미포함, Play 배포 미확인 | `199 / 792 = 25.1%` | 주의 | GA4 Admin + PR 포함 release/tag/Play deploy 후 D+14 screen quality 재조회 |
| #14 | #256/#279/#283 (`bce1cda1`, `5c6331da`, `35c13ebb`) | `origin/develop` 포함, `origin/main`/`v1.7.7` 미포함, Play 배포 미확인 | `199 / 792 = 25.1%` | 주의 | activation funnel D+14 재측정. CTA/첫 가치 피드백을 다시 만들지 않음 |
| #16 | #293 (`afcb5c8e`) | `origin/develop` 포함, `origin/main`/`v1.7.7` 미포함, Play 배포 미확인 | `199 / 792 = 25.1%` | 주의 | `ad_banner_*` post-split 14일 coverage 재조회. early smoke를 production placement 판단으로 승격하지 않음 |
| #307 | #308/#312/#353 (`cfff4118`, `e920ea30`, `dc0978f`) | `origin/develop` 포함, `origin/main`/`v1.7.7` 미포함, Play 배포 미확인 | `199 / 792 = 25.1%` | 주의 | review lifecycle + Play rating D+14/D+30 관측. `customEvent:reason`은 사용 가능, `customEvent:error`는 등록 경계 |

## GA4 query template

아래 snippet은 문서 템플릿이다. 실제 실행 시 서비스 계정 파일 경로는 로컬에서만 지정하고, key 내용은 출력/커밋하지 않는다.

```bash
cd <repo-root>
python3 - <<'PY'
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH = '<analytics-service-account.json>'
LATEST_VERSION = '<latest-play-version>'
CORE_EVENTS = [
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

creds = service_account.Credentials.from_service_account_file(
    CREDENTIAL_PATH,
    scopes=['https://www.googleapis.com/auth/analytics.readonly'],
)
session = AuthorizedSession(creds)


def run_report(name, payload):
    response = session.post(
        f'https://analyticsdata.googleapis.com/v1beta/properties/{PROPERTY_ID}:runReport',
        json=payload,
    )
    print(f'\n--- {name} {response.status_code} ---')
    if response.status_code != 200:
        print(response.text)
        return []
    data = response.json()
    headers = [h['name'] for h in data.get('dimensionHeaders', [])]
    headers += [h['name'] for h in data.get('metricHeaders', [])]
    print('\t'.join(headers))
    rows = []
    for row in data.get('rows', []):
        values = row.get('dimensionValues', []) + row.get('metricValues', [])
        rendered = [v.get('value', '') for v in values]
        rows.append(rendered)
        print('\t'.join(rendered))
    return rows


def base(metrics, dimensions=None, start='30daysAgo', end='yesterday', limit=100, order_metric=None):
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
    'versions_30d',
    base(['activeUsers', 'sessions', 'eventCount'], ['appVersion'], order_metric='activeUsers'),
)

run_report(
    'core_events_by_version_30d',
    base(
        ['totalUsers', 'eventCount'],
        ['eventName', 'appVersion'],
        order_metric='eventCount',
        limit=500,
    ) | {
        'dimensionFilter': {
            'filter': {
                'fieldName': 'eventName',
                'inListFilter': {'values': CORE_EVENTS},
            }
        }
    },
)

run_report(
    'latest_version_core_events_30d',
    base(
        ['totalUsers', 'eventCount'],
        ['eventName', 'appVersion'],
        order_metric='eventCount',
        limit=500,
    ) | {
        'dimensionFilter': {
            'andGroup': {
                'expressions': [
                    {'filter': {'fieldName': 'eventName', 'inListFilter': {'values': CORE_EVENTS}}},
                    {'filter': {'fieldName': 'appVersion', 'stringFilter': {'matchType': 'EXACT', 'value': LATEST_VERSION}}},
                ]
            }
        }
    },
)
PY
```

## 이슈별 적용 규칙

### #13 GA4 계측 품질

- `screen_view` 품질 개선 PR이 `develop`에 있어도 `origin/main`/SemVer tag/Play 배포에 포함되지 않았으면 live screen quality 개선으로 말하지 않는다.
- `customEvent:*` metadata 등록과 live `runReport` 성공 여부는 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 따른다.
- `보류` 상태에서는 새 screen_view 문서 PR보다 release boundary와 D+14 재측정 표를 남긴다.

### #14 첫 잠금 활성화

- `first_lock_configured`는 준비 완료, `first_core_action_completed`는 첫 가치 경험, `app_block_intercepted`는 실제 차단 증거다.
- 최신 버전 active share가 낮으면 `first_core_action_completed / first_open` 전체 합산을 post-CTA 성과로 보지 않는다.
- 다음 판단은 PR #256/#279/#283 포함 release 이후 14일 창에서 `first_lock_configured / first_open`, `first_core_action_completed / first_lock_configured`, `app_block_intercepted / first_core_action_completed`를 같이 보는 것이다.

### #16 AdMob 수익화

- PR #293의 `ad_banner_*` event-source split이 최신 production cohort에 충분히 포함되기 전까지 `ad_banner_*` 소량 행은 queryability smoke로만 본다.
- publisher surface(`publisherAdImpressions`, `adUnitName`)와 앱 custom event coverage(`ad_banner_*`, `customEvent:ad_placement`)를 섞지 않는다.
- 최신 버전 active share가 `보류`이면 placement CTR/eCPM 실험 결론을 내리지 않는다.

### #307 리뷰 프롬프트

- Play In-App Review API는 실제 리뷰 작성 여부를 알려주지 않는다. 앱 지표는 `eligible/shown/skipped/failed`, 성과 지표는 Play Console rating/review 후행 지표로 분리한다.
- `customEvent:reason`은 skip breakdown에 사용할 수 있지만, PR #308/#312 포함 버전 cohort가 충분히 쌓이기 전 pre-fix skip noise를 현재 blocker로 되살리지 않는다.
- `customEvent:error`가 미등록이면 failed reason breakdown은 GA4 Admin 외부 경계다.

## 최종 보고/이슈 코멘트 템플릿

```markdown
## 버전 채택률 판독 게이트

- 분석 기간:
- 최신 배포 버전:
- 전체 activeUsers:
- 최신 버전 activeUsers:
- 최신 버전 active share:
- 판정: 충분 / 주의 / 보류

## 핵심 이벤트 분해

| 이벤트 | 전체 users/events | 최신 버전 users/events | 해석 |
| --- | ---: | ---: | --- |
| `first_lock_configured` |  |  |  |
| `first_core_action_completed` |  |  |  |
| `app_block_intercepted` |  |  |  |
| `review_prompt_shown` |  |  |  |
| `ad_banner_impression` |  |  |  |

## 결론

- 전체 합산으로 말할 수 있는 것:
- 최신 코드/배포 성과로 아직 말할 수 없는 것:
- 다음 경계: release/main 반영 / SemVer tag / Play deploy / D+14 / D+30 / GA4 Admin 등록
```
