# Keep-Android

Stopit / Keep Android는 선택한 앱 사용을 막아 사용자가 집중, 공부, 업무, 휴식 루틴을 지키도록 돕는 Android screen-time management 앱입니다.

## 빠른 문서 진입점

### 제품 / 지표 / 운영
- `docs/PRODUCT_METRICS_DASHBOARD.md` — North Star, 입력/건강/비즈니스 지표, 우선순위 기준
- `docs/METRICS_ANALYSIS.md` — GA4/Play/수익 지표 해석과 이슈화 절차
- `docs/ANALYTICS_EVENT_DICTIONARY.md` — 이벤트/파라미터 canonical 계약
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` — GA4 Admin custom definition 등록, metadata 확인, 14일 재측정 운영 런북
- `docs/ops/stopit/README.md` — Stopit cron / lane / context pack 진입점

### 활성화 / 리뷰 / 수익화 후속 문서
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md` — 첫 잠금 활성화 퍼널 계약과 CTA/측정 가드레일
- `docs/REVIEW_PROMPT_LIFECYCLE.md` — 리뷰 프롬프트 eligibility / analytics / post-release 확인 절차
- `docs/ADMOB_MONETIZATION_RUNBOOK.md` — 광고 단위 감사, guardrail, 안전한 수익화 실험 런북

### 릴리즈 / QA / 배포
- `docs/GIT_WORKFLOW.md` — 브랜치/커밋/릴리즈 작업 흐름
- `docs/RELEASE_CHECKLIST.md` — release/hotfix PR 체크리스트
- `docs/PLAY_DEPLOYMENT.md` — Play internal/production 운영 기준
- `docs/QA_RUNTIME_CHECKLIST.md` — Android runtime / receiver / accessibility / notification QA 기준

## 현재 analytics 해석 주의

- `customUser:routines_count` 조회 가능만으로 GA4 queryability가 해결된 것으로 보면 안 됩니다.
- `customEvent:*` 차원/지표가 GA4 Admin에 실제 등록되기 전까지 activation / review / monetization 세부 파라미터 해석 confidence는 낮게 유지해야 합니다.
- `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`은 기본적으로 no-data가 아니라 **GA4 Admin registration gap** 신호로 해석합니다.
- queryability follow-through의 현재 source of truth는 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`입니다.
