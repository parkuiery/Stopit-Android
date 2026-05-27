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
- GA4 Analytics Data API / AdMob 보고서

운영 원칙:

- 과거 문서의 수치는 참고값일 뿐이고, 실제 판단은 **이번 분석에서 다시 조회한 수치**를 source of truth로 둔다.
- 계측 누락이 있으면 제품/수익화 결론 confidence를 낮춘다.
- 활성화/신뢰를 해치는 실험은 revenue가 좋아 보여도 기본안으로 채택하지 않는다.

## 현재 #13 queryability 경계

2026-05-28 live 확인 기준으로 광고/수익화 해석에 필요한 `customEvent:*` 축은 아직 GA4 Admin에 등록되지 않았다.

- metadata 결과: `customUser:routines_count`만 확인, `customEvent:*`는 없음
- monetization smoke (`ad_impression` / `ad_click` / `ad_revenue` by `customEvent:ad_placement`, `customEvent:screen_context`, `customEvent:ad_unit_id`):
  - `400 INVALID_ARGUMENT`
  - `Field customEvent:ad_placement is not a valid dimension.`

즉, 현재 `adUnitName` / `adFormat` 같은 AdMob 쪽 집계는 볼 수 있어도, Stopit 제품 문맥에서 중요한 `ad_placement`, `screen_context`, `ad_unit_id` 기준 해석은 아직 **미등록 queryability gap** 때문에 낮은 confidence 상태다.

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
