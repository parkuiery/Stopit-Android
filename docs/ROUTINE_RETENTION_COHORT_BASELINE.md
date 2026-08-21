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
- `customUser:routines_count`가 보인다고 해서 `customEvent:*` 파라미터 queryability가 모두 해결된 것은 아니다. activation 세부 `source`, `block_source`, `blocked_app_category_bucket` 분해는 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 GA4 Admin 등록 상태를 먼저 확인한다. #1079 이후 `routine_id` / `goal_lock_id` row id는 GA4 신규 등록 대상이 아니며 repo-internal debug/QA attribution으로만 다룬다.
- 최신 버전 `1.7.7` active share는 2026-06-19T01:07:53Z 기준 `395 / 874 = 45.2%`로 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 `충분`이다. 다만 최신 develop PR의 효과로 해석하려면 main/tag/Play 포함 여부와 D+14/D+30 같은 쿼리 창 재측정을 같이 확인한다.

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

## 2026-08-21 30일 재측정 (밀린 창 소급)

아래 30일 체크 기준(`2026-07-03 이후`)이 밀려 있었다. 2026-08-21 readback으로 소급한다. 전체 진단과 실행 계획은 `docs/RETENTION_DIAGNOSIS_2026_08.md`가 source of truth다.

GA4 `30daysAgo..yesterday` = `2026-07-22..2026-08-20`, property `502544175`.

### 잔존 축 추가 (이번 재측정에서 새로 본 것)

기존 기준선은 `sessions / activeUsers`와 차단 강도로 "반복 사용"을 봤다. 이번에는 **신규 유저 코호트의 N-day 잔존**을 직접 붙였다 (Amplitude `interval: 1` N-day retention, `startEvent = _new`, 세그먼트는 `routine_saved` 발생 여부).

| 코호트 | n | D1 | D7 | D14 |
| --- | ---: | ---: | ---: | ---: |
| 루틴 미생성 신규 | 333 | `48/322 = 14.9%` | `15/262 = 5.7%` | `9/185 = 4.9%` |
| 전체 신규 (기준선) | 631 | `149/611 = 24.4%` | `39/502 = 7.8%` | `24/370 = 6.5%` |
| 루틴 생성 신규 | 298 | `101/289 = 34.9%` | `24/240 = 10.0%` | `15/185 = 8.1%` |

**판정 수정.** 기존 문서는 루틴 생성 유도를 "유망한 다음 실험 후보"로 뒀다. 방향은 맞지만 **크기를 다시 봐야 한다.**

- D1 격차는 크다 (`14.9% → 34.9%`, 2.3배).
- **그 격차가 D7까지 살아남지 못한다** (`5.7% → 10.0%`, 격차 `4.3%p`).
- 같은 readback에서 `first_lock_configured` 완료자 D7은 `7.7%`로 전체 신규 `7.8%`와 **차이가 없다.**

즉 **루틴을 만드는 것 자체는 잔존을 거의 만들지 않는다.** 실험 대상을 "루틴 생성 유도"에서 **"첫 루틴 정착"**으로 옮긴다.

### 삭제 축 (신규)

`app_remove` 434건을 `customUser:routines_count`로 분해했다.

| 보유 루틴 | activeUsers | `app_remove` | 대략 비율 |
| --- | ---: | ---: | ---: |
| 0개 | 714 | 192 | `192/714 = 26.9%` |
| **1개** | 356 | **150** | `150/356 = 42.1%` |
| 2개 이상 | 267 | 67 | `67/267 = 25.1%` |
| `(not set)` | 657 | 25 | — |

비율이 `0 → 1 → 2+` 순으로 오르지 않고 **1에서 꺾인다.** 루틴 1개 층이 가장 많이 샌다.

주의: `routines_count`는 30일 동안 값이 변해 한 유저가 여러 구간에 잡힌다(activeUsers 합 2,051 > MAU 894). **비율의 절대값이 아니라 비단조 패턴만 읽는다.** #479가 닫히기 전까지 `(not set)`을 `0`과 합산하지 않는 기존 규칙은 그대로 유지한다.

### `(not set)` coverage 변화

| 시점 | `(not set)` activeUsers | 비고 |
| --- | ---: | --- |
| 2026-06-03 | 560 | 기존 기준선 |
| 2026-08-21 | 657 | 여전히 최대 구간. #479 미해결 |

`(not set)` 비중이 줄지 않았으므로 이 문서의 confidence 등급은 **낮음 유지**다.

### 기존 "주의할 신호" 2번의 후속

기존 문서는 "루틴 보유자는 긴급해제 사용자 비율도 높다. 차단 강도가 부담으로 이어지는 신호일 수도 있다"고 남겼다. 이번 readback에서 관련 증거가 나왔다.

| 차단 화면에서의 선택 | startEvent | D7 | D14 |
| --- | --- | ---: | ---: |
| 순응 (`first_core_action_completed`) | 신규 유저 | `27/281 = 9.6%` | `15/215 = 7.0%` |
| 우회 (`emergency_unlock_used`) | 반복 이벤트 | `68/236 = 28.8%` | `44/181 = 24.3%` |

우회 쪽 잔존이 높다. 다만 `emergency_unlock_used`는 반복 이벤트라 코호트에 기존 유저가 섞이므로 **인과로 읽지 않는다.** 결론은 "긴급해제 억제를 잔존 개선 수단으로 삼을 근거가 없다"까지다. 자세한 판단은 `docs/RETENTION_DIAGNOSIS_2026_08.md` 4장.

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
