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

## 관련 문서

- `docs/PRODUCT_METRICS_DASHBOARD.md`
- `docs/METRICS_ANALYSIS.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
