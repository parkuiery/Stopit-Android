# Stopit Metrics Context

## 데이터 소스

주요 지표 소스:
- GA4 Analytics Data API: property `properties/502544175`
- Firebase Analytics / Crashlytics
- Play Console: 평점, 리뷰, Store listing performance, release health
- AdMob/GA4 광고 지표: ad revenue, impressions, clicks
- GitHub Issues/PR/Actions: 실행 상태와 품질 신호

주의:
- 로컬 서비스 계정 JSON 경로는 문서화할 수 있지만 key 내용은 절대 출력하거나 커밋하지 않는다.
- 지표 수치는 매 분석 시 새로 조회한다. 과거 문서의 기준선은 참고값일 뿐 source of truth가 아니다.

## 핵심 지표 레이어

### North Star

`주간 활성 차단 사용자 수`
- 최근 7일 내 `app_block_intercepted`가 1회 이상 발생한 고유 사용자 수.

### Input metrics

- 첫 잠금 설정률 = `first_lock_configured` users / `first_open` users
- 첫 핵심 행동 완료율 = `first_core_action_completed` users / `first_open` users
- 앱 선택 완료율 = `app_selection_completed` users / `first_open` users
- 루틴 생성 사용자 비율 = 루틴 1개 이상 사용자 / active users
- 차단 빈도 = `app_block_intercepted` count / active blocked users

### Health / guardrail metrics

- crash-free users rate
- `app_exception`
- 긴급해제 사용률 = `emergency_unlock_completed` users / active blocked users
- 화면명 미설정 비율 = `(not set)` screen views / total screen views
- 리뷰 평점과 리뷰 수
- 권한 거절/이탈률

### Business metrics

- total ad revenue
- ARPU / ARPDAU
- eCPM = totalAdRevenue / impressions × 1000
- CTR = clicks / impressions
- 유료/광고 제거/후원 기능이 생기면 paid conversion, refund/churn

### Acquisition / ASO metrics

- newUsers
- Organic Search newUsers
- Direct newUsers share
- Paid Search newUsers / activeUsers / sessions
- Play Console Search/Explore vs external/campaign acquisition source
- Store listing visitors/conversion if available
- rating count and average rating

ASO 판정 주의:
- #65의 14일/30일 성과 판단은 `docs/PLAY_STORE_ASO.md`의 #242 acquisition attribution gate를 따른다.
- GA4 `Organic Search`와 Play Console Search/Explore가 같은 방향인지 확인하기 전에는 ASO 효과로 단정하지 않는다.
- `Direct` 비중 급증은 UTM/Install Referrer 누락 또는 외부 링크 유입일 수 있으므로 먼저 분리한다.
- 실제 캠페인 집행이 확인되지 않은 `Paid Search` 활성/세션은 신규 획득 성과가 아니라 과거 사용자/재방문/분류 잔상으로 다룬다.

## 핵심 퍼널

첫 잠금 활성화 퍼널의 단계 의미/CTA/legacy 이벤트명 정리는 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 source of truth로 본다.

활성화 퍼널:
1. `first_open`
2. `onboarding_step_view` / `onboarding_step_complete`
3. `permission_outcome`
4. `app_selection_completed`
5. `first_lock_configured`
6. `first_core_action_completed`
7. `app_block_intercepted`

반복 사용 퍼널:
1. 첫 차단 성공
2. 다음날 재방문 또는 재차단
3. 7일 내 2회 이상 차단
4. 루틴 1개 이상 생성
5. 30일 내 반복 사용

권장 cohort:
- install week
- appVersion
- acquisition channel
- first successful core action date
- routine-created vs no-routine users

## 해석 원칙

- 먼저 계기판을 의심한다. 화면명 `(not set)`이나 커스텀 차원 누락이 크면 제품 결론을 낮은 confidence로 둔다.
- 이벤트 의미가 앱 버전별로 바뀐 경우 전체 30일 합산 퍼널을 그대로 믿지 않는다.
- 전환율은 항상 분자/분모/기간을 같이 기록한다.
- 지표 하나당 이슈 하나를 만들지 않는다. 실행 단위의 문제/기회로 묶는다.
- 광고/수익화 개선은 activation, retention, trust guardrail과 함께 판단한다.
- AdMob/광고 분석은 `docs/ADMOB_MONETIZATION_RUNBOOK.md`를 source of truth로 본다. 특히 `publisherAdImpressions`/`publisherAdClicks`/`totalAdRevenue` + `adUnitName` 표와 앱 custom-event `eventCount` + `customEvent:ad_placement` coverage 표를 합산하지 않는다. 2026-06-01 기준 `ad_impression` custom coverage가 `912 / 21,159 = 4.31%`에 그쳤으므로, 광고 placement 최적화나 새 수익화 실험 전에는 SDK 자동 이벤트와 앱 custom 이벤트 source split을 먼저 고정한다.
- 광고 설정/운영 안전성 이슈(#250류)는 성과표와 별도로 본다. production AdMob application/ad unit id는 Manifest/UI call site에 흩어 두지 말고 flavor별 config source에서 관리해야 하며, dev/debug가 production 광고 ID를 쓰지 않는 정적 가드가 필요하다.
- Play In-App Review API는 실제 리뷰 작성/취소 여부를 앱에 직접 알려주지 않는다. 신뢰 가능한 lifecycle 신호는 `eligible / shown / skipped / failed` 수준이다.

## 지표 기반 이슈 생성 기준

생성 가능:
- 수치 근거가 있고 실행 단위가 명확하다.
- 개선 후 14일 또는 30일 비교가 가능하다.
- 계측 개선, 활성화 개선, ASO, 리뷰, 수익화 guardrail처럼 제품 의사결정과 연결된다.

보류:
- 분자/분모가 없다.
- 이벤트 의미가 불명확하다.
- 기존 이슈와 중복된다.
- 단순 관찰일 뿐 실행 작업으로 쪼개지지 않았다.

## 관련 문서

- `docs/METRICS_ANALYSIS.md`
- `docs/PRODUCT_METRICS_DASHBOARD.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`
- `docs/FOCUS_SUMMARY_SHARE_MVP.md`
