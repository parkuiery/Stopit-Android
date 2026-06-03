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
- `Direct` 비중 급증은 UTM/Install Referrer 누락, Discord/웹/QR 링크, redirect의 referrer 손실, 외부 캠페인 유입일 수 있으므로 먼저 분리한다.
- 실제 캠페인 집행이 확인되지 않은 `Paid Search` 활성/세션은 신규 획득 성과가 아니라 과거 사용자/재방문/분류 잔상으로 다룬다.
- Play Store 링크를 새로 배포하거나 캠페인을 시작할 때는 가능한 한 `utm_source`, `utm_medium`, `utm_campaign`과 게시 시각을 기록하고, #65 판정표에는 GA4 채널과 Play Console Search/Explore/external source를 함께 남긴다.
- 2026-06-02 최신 스냅샷(`2026-06-02T22:16:16Z`)에서는 전체 `newUsers`가 432명으로 직전 30일 대비 +18.0%였지만 `Direct` 신규가 263명(60.9%)까지 올라갔고 `Organic Search` 신규는 169명으로 #65 기준선 178명보다 낮다. 따라서 현재 신규 유입 반등은 #242 외부 확인 전까지 ASO 회복이 아니라 attribution 판정 보류 신호로 본다.

## 핵심 퍼널

첫 잠금 활성화 퍼널의 단계 의미/CTA/legacy 이벤트명 정리는 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 source of truth로 본다. 2026-06-02 기준 #14 홈 첫 잠금 CTA(PR #256), 첫 차단 성공 피드백(PR #279), 홈 Keep/타이머 시작 직후 안내(PR #283)가 develop에 반영됐으므로, 이후 활성화 분석은 “CTA 부재”나 “첫 가치 피드백 미정의”로 되돌리지 않는다. 단, 2026-06-02 확인 기준 이 세 PR은 `origin/main`/최신 production tag `v1.7.7`에는 아직 미포함이므로, live production activation 수치는 post-fix 결과가 아니라 pre-#256/#279/#283 baseline이다. 다음 판단은 해당 commit 포함 release/tag/Play deploy 이후 14일 창에서 `first_lock_configured / first_open`, `first_core_action_completed / first_lock_configured`, `app_block_intercepted / first_core_action_completed`를 함께 재측정하는 것이다.

#14 측정 전제:
- `first_lock_configured`는 준비 완료 신호이고, 실제 차단 완료가 아니다.
- `first_core_action_completed`는 첫 가치 경험 신호이며, 최초 차단 화면 진입의 피드백/계측과 같이 본다.
- `app_block_intercepted`는 실제 차단 증거다.
- GA4 Admin에서 activation customEvent 축(`source`, `selected_app_count`, `block_source`, `blocked_app_package` 등)이 등록/metadata 확인되기 전에는 상위 이벤트 users 비율까지만 high-confidence로 보고, 출처/앱/권한별 세부 분해는 #13 외부 경계로 남긴다.

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
- #13 계열 분석에서는 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 같이 보고, 실제 `customEvent:*` 등록/조회 가능 여부와 repo 문서 범위를 구분한다.
- 2026-05-29 screen 품질 gap `10,274 / 13,154 = 78.1%`는 PR #296의 `SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen` 및 PR #318의 dev/debug `DevToolScreen` 보강 전 baseline으로 본다. 같은 화면에 대해 추가 code-lane 작업을 다시 열기 전에는 PR #296/#318 포함 버전 배포 후 14일 창으로 재측정한다. `DevToolScreen`은 dev/debug 내부 진단 surface라 production 사용자 지표와 분리해서 해석한다.
- 2026-06-03 09:12 KST screen quality smoke는 `13,780 / 22,584 = 61.0%`였지만, PR #296/#318이 아직 `origin/main`/production tag `v1.7.7`에 포함되지 않았으므로 post-fix 성과가 아니라 release boundary 전 중간 smoke로만 본다. #13 closure 판단은 해당 PR 포함 release/tag/Play deploy 후 14일 재측정으로 한다.
- `customUser:routines_count`가 보인다고 해서 activation/review/ad 관련 `customEvent:*`까지 조회 가능하다고 가정하지 않는다.
- `runReport`에 `customEvent:*`를 넣었을 때 `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`이 나오면, 최근 데이터 부족이 아니라 **GA4 Admin 미등록**으로 해석한다.
- 이벤트 의미가 앱 버전별로 바뀐 경우 전체 30일 합산 퍼널을 그대로 믿지 않는다.
- 전환율은 항상 분자/분모/기간을 같이 기록한다.
- 지표 하나당 이슈 하나를 만들지 않는다. 실행 단위의 문제/기회로 묶는다.
- 광고/수익화 개선은 activation, retention, trust guardrail과 함께 판단한다.
- AdMob/광고 분석은 `docs/ADMOB_MONETIZATION_RUNBOOK.md`를 source of truth로 본다. 특히 `publisherAdImpressions`/`publisherAdClicks`/`totalAdRevenue` + `adUnitName` 표와 앱 custom-event `eventCount` + `customEvent:ad_placement` coverage 표를 합산하지 않는다. 2026-06-01 기준 legacy `ad_impression` custom coverage가 `912 / 21,159 = 4.31%`에 그쳤고, PR #293에서 Stopit 앱 소유 배너 이벤트(`ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue`)가 SDK 자동 이벤트와 분리됐다. 다만 2026-06-02/2026-06-03 확인 기준 PR #293 split commit은 `origin/develop`에는 포함됐지만 `origin/main`/최신 production tag `v1.7.7`에는 포함되지 않았다. 2026-06-03 GA4 smoke의 `appVersion=1.7.5` `ad_banner_*` 소량 행은 source-split queryability 확인용이며 production 14일 placement 판단으로 쓰지 않는다. 따라서 post-split 14일 재조회 창은 아직 시작되지 않았고, 다음 판단은 PR #293 포함 release/tag/Play deploy 확인 후 잡는다.
- 광고 설정/운영 안전성 이슈(#250류)는 성과표와 별도로 본다. production AdMob application/ad unit id는 Manifest/UI call site에 흩어 두지 말고 flavor별 config source에서 관리해야 하며, dev/debug가 production 광고 ID를 쓰지 않는 정적 가드가 필요하다.
- Play In-App Review API는 실제 리뷰 작성/취소 여부를 앱에 직접 알려주지 않는다. 신뢰 가능한 lifecycle 신호는 `eligible / shown / skipped / failed` 수준이다.
- #307 리뷰 프롬프트 shown 0 follow-through는 2026-06-02 기준 PR #308 launch-failure 재시도 계약과 PR #312 Home Activity unwrap 계약이 develop에 merge된 상태로 본다. 2026-06-02T18:06:45Z live 재조회에서 `review_prompt_skipped`는 `1.7.0`/`1.7.3`/`1.7.6`에서만 관측됐고, PR #308/#312 포함 버전 cohort 행은 아직 없다. `customEvent:reason`은 metadata 등록/조회 가능하므로 skip reason 분석에는 사용할 수 있지만, `customEvent:error`는 아직 미등록이다. 다음 판단은 코드 PR 대기가 아니라 PR #308/#312 포함 버전 배포 여부, Play Console rating/review baseline, 14일/30일 관측 경계다.
- Usage Access 개인화(#119)는 구현 ready 신호가 아니라 discovery gate로 본다. 권한 허용률만 보지 말고 `first_core_action_completed`, `app_block_intercepted`, review/rating, crash-free users guardrail을 함께 보며, 앱 이름/package/raw usage history는 analytics payload로 보내지 않는다.

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
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`
- `docs/PLAY_STORE_ASO.md`
- `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`
- `docs/FOCUS_SUMMARY_SHARE_MVP.md`
