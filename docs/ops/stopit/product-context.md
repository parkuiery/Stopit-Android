# Stopit Product Context

## 제품 정체성

Stopit / Keep Android는 선택한 앱 사용을 막아 사용자가 집중, 공부, 업무, 휴식 루틴을 지키도록 돕는 Android screen-time management 앱이다.

핵심 약속은 단순한 방문/트래픽이 아니라 “사용자가 실제로 앱 차단/집중 가치를 얻는 것”이다.

## 핵심 사용자와 상황

우선적으로 보는 사용자:
- 스마트폰 사용을 줄이고 싶은 공부/업무 사용자
- 특정 앱을 일정 시간 차단하고 싶은 사용자
- 반복 루틴으로 집중 시간을 만들고 싶은 사용자
- Android의 권한/접근성 설정을 감수할 만큼 문제를 느끼는 사용자

사용 상황:
- 공부/업무 시작 전 차단 앱을 고르고 타이머를 설정한다.
- 반복 루틴으로 특정 시간대 앱 사용을 막는다.
- 차단 중 긴급한 상황에서 안전하게 긴급해제를 사용한다.
- 차단이 실제로 작동하고, 이후에도 다시 사용할 만큼 신뢰를 얻는다.

## 제품 목표

1. 신규 사용자가 첫 차단 가치를 빠르게 경험한다.
2. 반복 사용자가 루틴/타이머를 통해 꾸준히 차단 가치를 얻는다.
3. 권한, 잠금, 긴급해제, 백업/복구 등 신뢰가 중요한 흐름에서 불안정성을 줄인다.
4. 광고/수익화가 제품 신뢰와 안전 흐름을 해치지 않는다.
5. Play Store 유입, 리뷰, ASO가 실제 활성 사용자 증가로 이어진다.

## North Star Metric

추천 North Star Metric:

`주간 활성 차단 사용자 수`

정의:
- 최근 7일 동안 `app_block_intercepted`가 1회 이상 발생한 고유 사용자 수.

해석:
- 앱이 실행되었는지보다 실제 차단 가치가 발생했는지를 본다.
- 접근성 서비스 오작동이나 과도한 차단으로 인한 불편도 함께 봐야 한다.
- 긴급해제, crash-free users, 리뷰/평점과 함께 해석한다.

## 제품 판단 원칙

- 트래픽보다 핵심 가치 전달을 우선한다.
- 활성화 병목은 `first_open -> onboarding_step_view/onboarding_step_complete -> permission_outcome -> app_selection_completed -> first_lock_configured -> first_core_action_completed -> app_block_intercepted`로 본다.
- 첫 잠금 활성화 퍼널의 단계 의미, CTA 계약, legacy 이벤트명 정리, 해석 guardrail은 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 source of truth로 본다.
- #14의 홈 첫 잠금 CTA는 PR #256(`bce1cda`) 이후 구현됐고, 첫 차단 성공 피드백(PR #279 `5c6331d`)과 홈 Keep/타이머 시작 직후 안내(PR #283 `35c13eb`)도 develop에 반영됐다. 다만 2026-06-02 확인 기준 이 세 PR은 `origin/main`/최신 production tag `v1.7.7`에는 아직 포함되지 않았으므로 live production activation 수치는 pre-#256/#279/#283 baseline으로만 본다. 이후 제품 판단은 CTA/피드백을 다시 만드는 것이 아니라, 해당 commit 포함 release/tag/Play deploy 이후 14일 창에서 `first_lock_configured -> first_core_action_completed -> app_block_intercepted` 전환이 실제로 개선됐는지 검증하는 쪽을 우선한다.
- #14 후속 문서/실행 후보를 고를 때는 `first_lock_configured`를 실제 차단 완료로 과장하지 않는다. 이미 들어간 안내/피드백 표면을 기준선으로 두고, 남은 repo-internal 후보는 release/metrics 템플릿과 GA4 queryability handoff 보강 정도다.
- `customUser:routines_count`가 조회된다고 해서 activation/review/monetization의 `customEvent:*` queryability까지 해결됐다고 보지 않는다.
- 루틴 보유/미보유 반복 사용 판단은 `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`를 source of truth로 본다. 2026-06-03 기준 루틴 보유자는 sessions / activeUsers와 `app_block_intercepted` users / activeUsers가 더 높아 루틴 CTA/템플릿 실험은 실행 후보지만, `(not set)` activeUsers가 가장 커서 전체 retention 결론은 보류한다.
- 루틴 템플릿 공유 루프는 `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`(#407)를 source of truth로 본다. MVP는 Android share sheet 텍스트 공유이며, deep link/import는 별도 decision gate다. `lockApplications`, package name, 앱 이름, raw session history는 payload/analytics에 넣지 않는다.
- 목표 잠금은 `docs/GOAL_LOCK_MVP.md`(#417)를 source of truth로 본다. MVP는 7/14/30일 또는 종료 날짜까지 `all_day`/`scheduled`로 앱을 잠그고 Home card/section에서 진행 상태를 보여주는 장기 자기통제 계약이다. 강력 목표 잠금/해제 제한/결제 연결은 MVP 밖 후속 판단이며, analytics는 목표 이름 원문·app package·app label·raw 날짜 없이 enum/bucket만 사용한다.
- `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`은 제품 신호 부재보다 **GA4 Admin registration gap** 가능성을 먼저 의심하고, 최종 해석은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` 기준으로 묶는다.
- #65/#242 ASO 판단은 `docs/PLAY_STORE_ASO.md`의 attribution gate를 먼저 따른다. 2026-06-04T20:14:53Z live readback 기준 `newUsers` 506명, `Direct` 327명(64.6%), `Organic Search` 179명, `sessions` 4,897회(-21.9%)처럼 신규 유입은 반등했지만 Direct 신규 비중이 더 커졌고 Organic Search 신규가 #65 기준선을 간신히 넘은 정도일 때는 Play Console Search/Explore, external/campaign, UTM/Install Referrer 기록을 확인하기 전까지 ASO 회복으로 단정하지 않는다.
- #307 리뷰 프롬프트 follow-through는 PR #308 launch-failure 재시도 계약과 PR #312 Home Activity unwrap 계약이 develop에 merge된 상태를 기준으로 본다. 다음 판단은 같은 코드 PR을 다시 만드는 것이 아니라 PR #308/#312 포함 버전의 release/tag/Play deploy 여부, 조회 가능해진 GA4 `customEvent:reason` 기반 skip breakdown, 아직 미등록인 `customEvent:error` boundary, Play Console rating/review 14일·30일 관측이다.
- #16 AdMob 수익화 판단은 `docs/ADMOB_MONETIZATION_RUNBOOK.md`를 source of truth로 본다. PR #293의 `ad_banner_*` event-source split은 develop에 있지만 최신 production tag `v1.7.7`과 현재 `origin/main`에는 없으므로, PR #293 포함 release/tag/Play deploy 전까지 `v1.7.7` 광고 데이터는 post-split measurement가 아니라 legacy baseline으로만 본다. GA4에 소량의 `ad_banner_*` 행이 먼저 보여도 source-split queryability smoke로만 보고, production 14일 placement/실험 판단으로 승격하지 않는다. PR #362로 `monetization_interest_shown` / `monetization_interest_clicked` 코드 계약이 생겼고 PR #402 merge commit `de142bd34a2729bcbb1e932db70b34d6459ce3b0`으로 메뉴/설정 CTA UI도 develop에 들어갔다. 다만 2026-06-04 확인 기준 PR #402는 `origin/main`에는 없으므로, GA4 `interest_context` / `interest_surface` 등록·metadata 확인, CTA 포함 release/tag/Play deploy, 14일 관측 전까지 구매 전환이 아니라 관심도 실험 준비 상태로만 본다.
- 민감한 행동 정보, 차단 앱 목록, 집중 실패/중독 뉘앙스는 노출하지 않는다.
- 공유/성장 루프는 완전 선택형이어야 하며 사생활을 침해하면 안 된다.
- 긴급해제와 안전 플로우는 광고나 수익화 뒤에 숨기지 않는다.
- 리뷰 요청은 반복된 긍정 경험 뒤에만 부드럽게 노출한다.

## 기능/성장 아이디어 기준

좋은 아이디어:
- 첫 차단 성공률 또는 반복 사용률을 높인다.
- 사용자의 신뢰와 안전을 강화한다.
- 실행 단위가 작고 측정 가능하다.
- 개인정보/민감 정보 노출 위험이 낮다.
- 기존 앱 정체성인 차단/집중과 자연스럽게 맞는다.

주의할 아이디어:
- 사용자의 앱 사용 문제를 공개적으로 드러내는 공유 기능
- 긴급해제 기본권을 제한하는 수익화
- 핵심 차단 기능을 갑자기 유료화하는 실험
- 계측이 부족해 성공/실패를 판단할 수 없는 기능
- Usage Access처럼 민감 권한을 핵심 차단 가치 이전에 요구하거나, 권한 거절 시 기존 차단/타이머/루틴 가치를 약화시키는 개인화 기능

## 관련 문서

- `docs/PRODUCT_METRICS_DASHBOARD.md`
- `docs/METRICS_ANALYSIS.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
- `docs/PLAY_STORE_ASO.md`: #65/#242 Play Console ASO 반영 후 Search/Explore vs external/campaign attribution gate, 14일·30일 검증 런북.
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`: #16 AdMob event-source split, `ad_banner_*` post-release coverage 재조회, `monetization_interest_*` 관심도 CTA 계약, 수익화 guardrail 런북.
- `docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md`: #307 리뷰 프롬프트 shown 0 재측정, PR #308/#312 포함 버전 release/tag/Play deploy 및 14일·30일 후행 관측 런북.
- `docs/REVIEW_PROMPT_LIFECYCLE.md`: 리뷰 프롬프트 arm/drain, skip reason, Play In-App Review 한계 계약.
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`: #119 Usage Access 선택형 개인화 discovery gate. 구현 ready가 아니라 권한 UX, privacy guardrail, QA evidence, child issue 분리 기준을 관리한다.
- `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`: #407 루틴 템플릿 공유 privacy-safe MVP 계약. 앱 목록/패키지/원시 이력 없이 루틴의 비민감 패턴만 공유한다.
- `docs/GOAL_LOCK_MVP.md`: #417 목표 잠금 MVP 계약. 기간 기반 `all_day`/`scheduled` 장기 잠금, Home 진행 카드/섹션, privacy-safe analytics, runtime QA baseline을 구현 전 handoff로 고정한다.
- `docs/FOCUS_SUMMARY_SHARE_MVP.md`
