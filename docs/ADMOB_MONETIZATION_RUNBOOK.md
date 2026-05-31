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
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
- `docs/ops/stopit/metrics-context.md`
- 광고 화면/노출 문맥을 담는 analytics 및 UI 코드 (`TrackedBannerAd.kt` / `TrackedBannerAdTest.kt`)
- GA4 Analytics Data API / AdMob 보고서

운영 원칙:

- 과거 문서의 수치는 참고값일 뿐이고, 실제 판단은 **이번 분석에서 다시 조회한 수치**를 source of truth로 둔다.
- 계측 누락이 있으면 제품/수익화 결론 confidence를 낮춘다.
- 활성화/신뢰를 해치는 실험은 revenue가 좋아 보여도 기본안으로 채택하지 않는다.

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
| `HistoryScreen.kt` | `history` | `summary` | `history_bottom` | `ca-app-pub-1537867411423705/5324044368` | `사용 기록 하단 배너` | 비교적 안전한 비핵심 화면이지만 history/성과 회고 경험을 방해하지 않는지 확인. |

해석:

- 코드 기준 call site는 모두 `TrackedBannerAd`를 지나므로 앱 내부 이벤트(`ad_impression`, `ad_click`, `ad_revenue`)에는 `screen_name`, `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id`가 붙어야 한다.
- 그런데 GA4/AdMob의 `adUnitName` 기준으로 `(not set)` + empty가 40.7%라면, 우선순위는 **새 광고 실험**이 아니라 **AdMob 단위 이름/GA4 linkage/custom dimension/SDK 자동 수집 이벤트와 앱 custom 이벤트의 매핑 차이 진단**이다.
- `adUnitName`은 AdMob/GA4가 보여주는 광고 단위 표시명이고, `ad_unit_id`는 앱 custom event 파라미터다. 둘을 같은 필드처럼 해석하지 않는다. 두 표를 연결하려면 `ad_unit_id` custom dimension 등록 여부와 AdMob unit 이름 매핑을 먼저 확인한다.

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
- 수익화 실험 우선순위/guardrail 문서화
- metrics 문서에서 광고 분석 문서를 발견 가능하게 연결

이 lane에서 하지 않는 것:

- 실제 광고 SDK/노출 로직 수정
- 전면 광고 배치 변경
- 결제 구현
- Play Console 또는 원격 설정 실험 실제 실행

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
