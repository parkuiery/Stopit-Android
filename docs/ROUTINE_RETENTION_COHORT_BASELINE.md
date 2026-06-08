# 루틴 보유/미보유 반복 사용 코호트 기준선

이 문서는 issue #380의 source of truth다. 목적은 “루틴을 만든 사용자가 반복 사용과 핵심 차단 가치에서 실제로 더 강한 신호를 보이는가”를 감이 아니라 같은 분자/분모로 재조회할 수 있게 고정하는 것이다. `routines_count=(not set)` coverage 보강 실행 계약은 `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`(#479)를 source of truth로 본다.

## 현재 판정

- 상태: **실행 후보 / 추가 계측 주의**
- 분석 기간: GA4 `30daysAgo..yesterday`
- 비교 기간/방식: 같은 30일 window 안에서 `routines_count = 0` vs `>=1` vs `(not set)`를 비교한다. 전후 기간 비교는 아래 14일/30일 재측정 때 같은 표로 추가한다.
- 조회 시각: `2026-06-03T09:12:01Z` live readback
- GA4 property: `properties/502544175`
- 주요 차원: `customUser:routines_count`

최근 30일 기준으로 `routines_count >= 1` 사용자는 `routines_count = 0` 사용자보다 반복 사용 강도가 높다.

- active users는 유사하다: `>=1` 150명 vs `0` 155명.
- sessions / active user는 `>=1`이 `2,152 / 150 = 14.35`, `0`이 `1,180 / 155 = 7.61`이다.
- `app_block_intercepted` users / active users는 `>=1`이 `91 / 150 = 60.7%`, `0`이 `62 / 155 = 40.0%`이다.
- `app_block_intercepted` eventCount / blocked users는 `>=1`이 `6,099 / 91 = 67.0`, `0`이 `1,763 / 62 = 28.4`이다.

따라서 루틴 생성 유도는 반복 사용/핵심 가치 관점에서 유망한 다음 실험 후보로 둔다. 단, `customUser:routines_count = (not set)` 사용자가 activeUsers 560명으로 가장 커서, 이 기준선은 **루틴 보유자 vs 미보유자 비교의 초기 신호**이지 전체 사용자 retention 결론은 아니다. #479의 `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`가 닫히기 전까지 `0`과 `(not set)`을 모두 “루틴 없음”으로 합산하지 않는다.

## 코호트 정의

| 코호트 | 정의 | 해석 |
| --- | --- | --- |
| 루틴 미보유 | `customUser:routines_count = 0` | 루틴을 만들지 않은 active user |
| 루틴 보유 | `customUser:routines_count >= 1` | 루틴 1개 이상이 GA4 user property에 반영된 active user |
| 루틴 상태 미확인 | `customUser:routines_count = (not set)` 또는 blank | user property 미설정/구버전/초기 유저/계측 공백 가능성. 제품 결론에서 별도 분리 |

주의:

- `first_open`은 30일 window에서 전부 `(not set)`로 잡힌다. 신규 사용자의 첫 방문 시점에는 루틴 수 user property가 아직 반영되지 않는 것이 자연스럽기 때문에, `first_open`을 루틴 보유/미보유 코호트 분모로 쓰지 않는다.
- `customUser:routines_count`가 보인다고 해서 `customEvent:*` 파라미터 queryability가 모두 해결된 것은 아니다. activation 세부 `source`, `block_source`, `routine_id` 분해는 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 GA4 Admin 등록 상태를 먼저 확인한다.
- 최신 버전 `1.7.7` active share는 2026-06-08T03:23:04Z 기준 `205 / 795 = 25.8%`로 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 `주의`다. 30% 미만이므로 최신 develop PR의 효과로 과대해석하지 않는다.

## 2026-06-03 기준선 표

### 활동/사용 강도

| 코호트 | activeUsers | totalUsers | sessions | eventCount | sessions / activeUsers | eventCount / activeUsers |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 루틴 미보유 (`0`) | 155 | 163 | 1,180 | 22,289 | 7.61 | 143.8 |
| 루틴 보유 (`>=1`) | 150 | 153 | 2,152 | 57,525 | 14.35 | 383.5 |
| 루틴 상태 미확인 | 560 | 603 | 1,565 | 51,303 | 2.79 | 91.6 |

### 핵심 이벤트 사용자

| 코호트 | `first_core_action_completed` users / activeUsers | `app_block_intercepted` users / activeUsers | `lock_session_start` users / activeUsers | `emergency_unlock_completed` users / blocked users |
| --- | ---: | ---: | ---: | ---: |
| 루틴 미보유 (`0`) | `58 / 155 = 37.4%` | `62 / 155 = 40.0%` | `66 / 155 = 42.6%` | `23 / 62 = 37.1%` |
| 루틴 보유 (`>=1`) | `68 / 150 = 45.3%` | `91 / 150 = 60.7%` | `42 / 150 = 28.0%` | `56 / 91 = 61.5%` |
| 루틴 상태 미확인 | `214 / 560 = 38.2%` | `222 / 560 = 39.6%` | `136 / 560 = 24.3%` | `37 / 222 = 16.7%` |

### 핵심 이벤트 빈도

| 코호트 | `app_block_intercepted` eventCount / blocked users | `emergency_unlock_completed` eventCount / emergency unlock users |
| --- | ---: | ---: |
| 루틴 미보유 (`0`) | `1,763 / 62 = 28.4` | `143 / 23 = 6.2` |
| 루틴 보유 (`>=1`) | `6,099 / 91 = 67.0` | `353 / 56 = 6.3` |
| 루틴 상태 미확인 | `4,075 / 222 = 18.4` | `153 / 37 = 4.1` |

### 안전/품질 guardrail

| 코호트 | `app_exception` users / activeUsers | `app_exception` eventCount |
| --- | ---: | ---: |
| 루틴 미보유 (`0`) | `0 / 155 = 0.0%` | `0` |
| 루틴 보유 (`>=1`) | `0 / 150 = 0.0%` | `0` |
| 루틴 상태 미확인 | `4 / 560 = 0.7%` | `40` |

`app_exception`은 루틴 보유/미보유 코호트에서는 관측되지 않았지만, Crashlytics crash-free users와 Play Console rating/review는 GA4 event table만으로 대체할 수 없다. 루틴 CTA/템플릿 실험을 실행할 때는 배포 후 crash-free users, review/rating, emergency unlock 비율을 별도 guardrail로 같이 기록한다.

## 해석

### 강한 신호

1. 루틴 보유자는 루틴 미보유자와 activeUsers 규모가 비슷한데 sessions가 약 1.8배 높다.
2. 루틴 보유자의 `app_block_intercepted` 사용자 비율이 더 높다.
3. 루틴 보유자의 차단 이벤트 빈도는 blocked user 기준으로도 더 높다.

### 주의할 신호

1. `routines_count = (not set)` activeUsers가 가장 크다. 루틴 보유/미보유 결론을 전체 사용자로 일반화하기 전에 `(not set)` 원인을 appVersion, 신규/기존 사용자, user property set 시점으로 분해해야 한다.
2. 루틴 보유자는 긴급해제 사용자 비율도 높다. 이는 “더 많이 차단해서 더 많이 긴급해제한다”일 수 있지만, 차단 강도가 부담으로 이어지는 신호일 수도 있다. 루틴 추천/템플릿 실험은 emergency unlock, review/rating, crash-free guardrail과 같이 봐야 한다.
3. `lock_session_start` users / activeUsers는 루틴 미보유자가 더 높지만, `app_block_intercepted` 빈도는 루틴 보유자가 더 높다. 타이머/수동 lock session과 실제 차단 이벤트의 의미가 다르므로 한쪽만 NSM 대리 지표로 쓰지 않는다.

## 다음 제품 결정

| 후보 | 판정 | 이유 | 선행 조건 |
| --- | --- | --- | --- |
| 루틴 생성 CTA 실험(#455) | 실행 후보 | 루틴 보유자의 반복 차단 강도가 높음 | `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md` 기준으로 post-first-core-action + 루틴 0개 사용자만 soft CTA 대상화. `routines_count=(not set)` 과대해석 금지 |
| 루틴 템플릿 공유 실험(#407) | 실행 후보 | 루틴을 이미 만든 사용자의 공유/성장 루프 후보 | `docs/ROUTINE_TEMPLATE_SHARE_MVP.md` privacy-safe payload/QA guardrail |
| Usage Access 기반 루틴 추천(#119) | 검증 필요 | 루틴 추천 가설과 맞지만 추가 권한/프라이버시 리스크가 큼 | #119 discovery gate, forbidden analytics payload 유지 |
| 리뷰 프롬프트 eligibility에 루틴 사용 강화 | 보류 | 루틴 보유자는 긍정 경험 후보지만 긴급해제 부담도 높음 | #307 post-release shown/skip 재측정 뒤 판단 |

## 14일/30일 재측정 기준

다음 live readback마다 같은 표를 유지한다.

- 14일 체크: `2026-06-17 KST 이후`
- 30일 체크: `2026-07-03 KST 이후`
- 필수 비교:
  - `routines_count = 0` vs `>=1` activeUsers, sessions, `eventCount`
  - `app_block_intercepted` users / activeUsers
  - `app_block_intercepted` eventCount / blocked users
  - `emergency_unlock_completed` users / blocked users
  - `app_exception` users / activeUsers, Crashlytics crash-free users, Play Console rating/review guardrail
  - `(not set)` activeUsers 비중
  - 최신 production version active share confidence (`보류/주의/충분`)

## GA4 query template

아래는 현재 기준선을 재조회할 때 쓰는 최소 쿼리 형태다. credential 경로는 로컬 환경에서만 채우고 문서/PR에 secret 내용을 남기지 않는다.

```python
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH = '<analytics-service-account.json>'
DIM = 'customUser:routines_count'
EVENTS = [
    'first_core_action_completed',
    'app_block_intercepted',
    'lock_session_start',
    'emergency_unlock_completed',
    'app_exception',
]

creds = service_account.Credentials.from_service_account_file(
    CREDENTIAL_PATH,
    scopes=['https://www.googleapis.com/auth/analytics.readonly'],
)
session = AuthorizedSession(creds)

def run_report(body):
    response = session.post(
        f'https://analyticsdata.googleapis.com/v1beta/properties/{PROPERTY_ID}:runReport',
        json=body,
        timeout=30,
    )
    response.raise_for_status()
    return response.json()

def body(metrics, dimensions=None, start='30daysAgo', end='yesterday', limit=1000, dimension_filter=None):
    payload = {
        'dateRanges': [{'startDate': start, 'endDate': end}],
        'metrics': [{'name': metric} for metric in metrics],
        'limit': limit,
    }
    if dimensions:
        payload['dimensions'] = [{'name': dimension} for dimension in dimensions]
    if dimension_filter:
        payload['dimensionFilter'] = dimension_filter
    return payload

# 1) activity by routines_count
run_report(body(['activeUsers', 'totalUsers', 'sessions', 'eventCount'], [DIM]))

# 2) event users by routines_count
for event_name in EVENTS:
    run_report(body(
        ['totalUsers', 'eventCount'],
        [DIM],
        dimension_filter={
            'filter': {
                'fieldName': 'eventName',
                'stringFilter': {'matchType': 'EXACT', 'value': event_name},
            }
        },
    ))
```

## 연결 이슈/문서

- GitHub issue: #380
- GitHub issue: #479 (`docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`) — `routines_count` user property coverage 보강 계약
- `docs/PRODUCT_METRICS_DASHBOARD.md`: North Star/Input/retention 해석
- `docs/METRICS_ANALYSIS.md`: 유지/반복 사용 분석 절차
- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`: #455 첫 차단 성공 후 루틴 생성 CTA 실험 계약
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`: 루틴 추천/Usage Access discovery gate
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: GA4 Admin 등록/metadata 경계
- `docs/VERSION_ADOPTION_METRICS_GATE.md`: 최신 버전 cohort confidence
