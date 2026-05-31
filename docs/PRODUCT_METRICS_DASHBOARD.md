# 스탑잇 제품 지표 대시보드 정의

이 문서는 `pm-skills`의 metrics dashboard, cohort analysis, prioritization, monetization, growth loop 프레임워크를 스탑잇 운영 방식에 맞게 흡수한 제품 지표 정의서다.

첫 잠금 활성화 퍼널의 단계 의미, CTA 계약, legacy 이벤트명 정리는 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 source of truth로 본다.

## 목적

스탑잇의 지표 관리는 “많이 쓰는가?”보다 “사용자가 실제로 앱 차단/집중 가치를 얻었는가?”를 중심으로 본다.

따라서 대시보드는 다음 질문에 답해야 한다.

1. 신규 사용자가 첫 가치를 경험하는가?
2. 사용자가 반복적으로 차단/집중 기능을 쓰는가?
3. 루틴과 긴급해제는 건강하게 작동하는가?
4. 광고/수익화가 제품 신뢰와 유지율을 해치지 않는가?
5. Play Store 유입과 리뷰 신뢰가 개선되고 있는가?

## North Star Metric 후보

### 추천 NSM

`주간 활성 차단 사용자 수`

정의:

- 최근 7일 동안 `app_block_intercepted`가 1회 이상 발생한 고유 사용자 수.

이유:

- 단순 실행/방문이 아니라 실제 차단 가치가 발생한 사용자다.
- 스탑잇의 핵심 약속인 “앱 사용을 막아 집중을 돕는다”와 직접 연결된다.
- 신규 유입, 권한 설정, 앱 선택, 첫 잠금 설정, 반복 사용이 모두 이 지표를 밀어 올린다.

주의:

- 접근성 서비스가 오작동해서 차단이 과도하게 발생하면 좋은 신호가 아니다.
- 사용자 불편/긴급해제 과다와 함께 해석해야 한다.

### 보조 NSM 후보

1. `주간 성공 집중 세션 사용자 수`
   - `lock_session_start`와 `lock_session_end`가 정상적으로 이어진 사용자.
   - 장점: 세션 단위 완결성을 본다.
   - 단점: 현재 이벤트 완결성과 세션 매칭이 충분한지 확인 필요.

2. `주간 활성 루틴 사용자 수`
   - 루틴 기반 차단 또는 루틴 수가 1개 이상인 주간 활성 사용자.
   - 장점: 반복 사용과 retention에 가깝다.
   - 단점: 신규 활성화보다 후행 지표다.

## 대시보드 레이어

| 레이어 | 지표 | 정의 | 데이터 소스 | 해석 |
|---|---|---|---|---|
| North Star | 주간 활성 차단 사용자 | 7일 내 `app_block_intercepted` 1회 이상 사용자 | GA4/Firebase | 핵심 가치 전달 규모 |
| Input | 첫 잠금 설정률 | `first_lock_configured` users / `first_open` users | GA4 | 신규 활성화 병목. 온보딩 출처는 `selected_app_count >= 1` 이후만 유효 |
| Input | 첫 핵심 행동 완료율 | `first_core_action_completed` users / `first_open` users | GA4 | 첫 가치 경험률 |
| Input | 앱 선택 완료율 | `app_selection_completed` users / `first_open` users | GA4 | 온보딩 중간 전환. `selected_app_count >= 1` 계약을 전제로 해석 |
| Input | 루틴 생성 사용자 비율 | `routines_count >= 1` users / active users | GA4 customUser | 반복 사용 기반 |
| Input | 차단 빈도 | `app_block_intercepted` / active blocked users | GA4 | 실제 사용 강도 |
| Health | Crash-free users rate | crash-free users / active users | GA4/Crashlytics | 안정성 |
| Health | 긴급해제 사용률 | `emergency_unlock_completed` users / active blocked users | GA4 | 차단 강도/사용자 부담 |
| Health | 리뷰 평점/리뷰 수 | Play Store 평점 및 rating count | Play Console | 신뢰와 전환율 |
| Health | 화면명 미설정 비율 | `(not set)` screen views / total screen views | GA4 | 분석 가능성 |
| Business | 광고 ARPU | `totalAdRevenue` / active users | GA4/AdMob | 사용자당 광고 수익 |
| Business | 광고 eCPM | `totalAdRevenue` / impressions × 1000 | GA4/AdMob | 광고 효율 |
| Business | 광고 CTR | clicks / impressions | GA4/AdMob | 광고 반응 |
| Acquisition | 신규 사용자 | `newUsers` | GA4 | 성장 흐름 |
| Acquisition | Organic Search 신규 사용자 | `newUsers` by `firstUserDefaultChannelGroup` | GA4 | ASO 효과 |

## 현재 기준선

2026-05-23 분석 기준 최근 30일:

| 지표 | 값 |
|---|---:|
| active users | 456 |
| total users | 507 |
| new users | 202 |
| sessions | 4,681 |
| screen views | 23,191 |
| engagement rate | 61.4% |
| `unifiedScreenName = (not set)` | 19,003 views |
| total ad revenue | $2.168155 |
| publisher ad impressions | 18,731 |
| publisher ad clicks | 12 |
| `first_open` users | 201 |
| `first_lock_configured` users | 41 |
| `app_block_intercepted` users | 121 |

주의: 이 기준선은 고정값이 아니라 예시다. 다음 분석 시 GA4에서 새로 조회해야 한다.

## 핵심 퍼널

### 활성화 퍼널

1. `first_open`
2. `onboarding_step_view` / `onboarding_step_complete`
3. `permission_outcome`
4. `app_selection_completed`
5. `first_lock_configured`
6. `first_core_action_completed`
7. `app_block_intercepted`

분석 규칙:

- 버전별 이벤트 도입 시점이 다르면 전체 30일 합산 퍼널을 그대로 믿지 않는다.
- `appVersion`으로 나눠 이벤트 의미가 동일한 구간만 비교한다.
- 전환율은 항상 분자/분모를 같이 기록한다.

### 유지/반복 사용 퍼널

1. 첫 차단 성공
2. 다음날 재방문 또는 재차단
3. 7일 내 2회 이상 차단
4. 루틴 1개 이상 생성
5. 30일 내 반복 사용

권장 코호트:

- 설치 주차별 D1/D7/D30 유지율
- 첫 차단 성공 사용자 vs 미성공 사용자
- 루틴 생성 사용자 vs 미생성 사용자
- Organic Search 유입 vs Direct 유입
- appVersion별 활성화/유지율

## 우선순위 점수표

지표 기반 이슈는 기본적으로 ICE로 점수화한다.

| 항목 | Impact | Confidence | Ease | ICE | 근거 |
|---|---:|---:|---:|---:|---|
| GA4 계측 품질 개선 | 9 | 9 | 7 | 567 | 화면명 대부분 `(not set)`, 커스텀 차원 부족 |
| 첫 잠금 활성화 개선 | 9 | 7 | 6 | 378 | first_open 201명 대비 first_lock_configured 41명 |
| Play Store ASO 개선 | 8 | 8 | 7 | 448 | 신규 사용자 직전 30일 대비 -47%, Organic Search 의존 |
| 리뷰 프롬프트 개선 | 7 | 6 | 7 | 294 | 사용 신호 대비 리뷰 수 작음 |
| 광고 수익화 실험 | 6 | 7 | 5 | 210 | ARPU/eCPM 낮음, UX 리스크 존재 |

해석:

- 단기 실행은 `GA4 계측 품질 개선`과 `Play Store ASO 개선`이 가장 안전하다.
- `첫 잠금 활성화 개선`은 임팩트가 크지만 계측 정리 후 더 정확히 설계하는 편이 좋다.
- `광고 수익화`는 제품 신뢰/유지율 guardrail을 먼저 정해야 한다.
- 현재 #65는 ASO 초안 부재 상태가 아니라, **대표님 수동 반영 완료 후 baseline/14일·30일 측정 복원 단계**로 이동해 있다. 자세한 follow-up 계약은 `docs/PLAY_STORE_ASO.md`를 source of truth로 본다.

## 성장 루프 후보

### 1. 집중 성공 공유 루프

- 트리거: 사용자가 일정 시간 차단/집중을 성공적으로 마침.
- 공유물: “오늘 2시간 집중 성공” 카드.
- 유입 경로: 공유 카드 → Play Store 링크.
- 리스크: 사용자의 집중/중독 문제가 민감할 수 있으므로 공유는 완전 선택형이어야 한다.
- 지표: 공유 클릭률, 공유 후 설치, 공유 사용자의 유지율.

### 2. 루틴 템플릿 공유 루프

- 트리거: 사용자가 유용한 공부/업무 루틴을 만듦.
- 공유물: 앱 목록을 직접 노출하지 않는 루틴 템플릿.
- 유입 경로: 템플릿 링크 → 설치 → 루틴 적용.
- 리스크: 차단 앱 목록 등 민감 정보 노출 금지.
- 지표: 템플릿 생성 수, 공유 수, 템플릿 적용률.

### 3. 리뷰/신뢰 루프

- 트리거: 반복 차단 성공, 루틴 사용, 긴급해제 후 복귀 등 긍정 경험.
- 행동: 부드러운 만족도 확인 후 Play Review 요청.
- 유입 경로: 리뷰 증가 → Organic Search 전환 개선.
- 리스크: 과도한 프롬프트는 반감 유발.
- 지표: prompt eligible/shown/skipped/failed, rating count, Organic Search 신규 사용자.

## 개인화 리포트 / 추천 후보

### Usage Access 기반 개인화 리포트

- 문제: 사용자는 어떤 앱/시간대 때문에 반복적으로 무너지는지 감에 의존해 차단을 설정한다.
- 기회: Usage Access를 선택적으로 활용하면 상위 방해 앱, 위험 시간대, 전주 대비 변화, 추천 루틴을 제안할 수 있다.
- 기본 원칙:
  - 핵심 차단 기능의 필수 권한으로 만들지 않는다.
  - 메시지/콘텐츠는 다루지 않고, 앱 사용 시간/빈도/시간대 집계만 사용한다.
  - 외부 전송보다 로컬 집계와 설명 가능한 규칙 기반 추천을 우선한다.
- 첫 MVP 범위:
  - 지난 7일 상위 방해 앱 Top 5
  - 위험 시간대
  - 전주 대비 변화
  - 추천 차단 시작점
- guardrail:
  - 권한 미허용 시 기존 차단/타이머/루틴 가치가 훼손되지 않아야 한다.
  - 민감한 앱 이름 노출과 감시 느낌을 피해야 한다.
  - 허용률/추천 클릭률뿐 아니라 `first_lock_configured`, `app_block_intercepted`, review/rating 악화 여부를 같이 본다.
- 상세 계약: `docs/USAGE_STATS_PERSONALIZATION_MVP.md`, issue #82

## 수익화 실험 후보

### 1. 광고 제거 일회성 구매

- 장점: 소비자 유틸리티 앱에 단순하고 신뢰를 해치기 적다.
- 리스크: 현재 광고 수익이 낮아도 구매 의향이 없을 수 있다.
- 검증: 설정/메뉴에 “광고 제거 준비 중” 관심 클릭 측정.

### 2. 보상형 광고 기반 추가 긴급해제

- 장점: 광고와 사용자 니즈가 연결될 수 있다.
- 리스크: 긴급/안전 플로우를 광고가 방해하면 신뢰 손상.
- guardrail: 기본 긴급해제는 절대 광고 뒤에 두지 않는다. 추가 선택권만 광고와 연결한다.

### 3. 프리미엄 루틴/통계

- 장점: 반복 사용자에게 자연스럽다.
- 리스크: 핵심 차단 기능을 유료화하면 반감이 클 수 있다.
- 검증: 루틴 사용자 대상 프리미엄 기능 클릭/관심 이벤트.

## 리뷰 프롬프트 원칙

리뷰 요청은 긍정 경험 뒤에만 한다.

추천 eligibility:

- `app_block_intercepted` 누적 5회 이상
- 루틴이 1개 이상 있고 3일 이상 사용
- 긴급해제를 사용했지만 이후 다시 차단 세션으로 복귀
- crash-free 상태
- 최근 7일 내 리뷰 프롬프트 미노출

추적 이벤트:

- `review_prompt_eligible`
- `review_prompt_shown`
- `review_prompt_skipped` with reason
- `review_prompt_failed`

주의: Play In-App Review API는 사용자가 실제로 리뷰를 남겼는지, dismiss 했는지를 앱에 직접 알려주지 않는다. 그래서 현재 신뢰 가능한 lifecycle 신호는 `eligible / shown / skipped / failed`까지다.

## 운영 주기

### 매일

- crash-free users rate
- app_exception
- 신규 배포 버전 이벤트 이상치
- 핵심 권한/차단 이벤트 급락

### 매주

- North Star
- 활성화 퍼널
- Organic Search 신규 사용자
- 리뷰 수/평점
- 광고 ARPU/eCPM

### 매월

- 코호트 유지율
- Play Store ASO 전후 비교
- 수익화 실험 결과
- 우선순위 재산정

## 관련 GitHub Issues

- #13 GA4 계측 품질 개선
- #14 첫 잠금 활성화 퍼널 개선
- #65 Play Console ASO 시안 반영 및 14·30일 유입 회복 검증
- #16 AdMob 성과 및 수익화 실험 (`docs/ADMOB_MONETIZATION_RUNBOOK.md` 참조)
- #17 리뷰 프롬프트 생애주기 개선
- #82 사용정보 기반 개인화 솔루션과 사용 리포트 제공 (`docs/USAGE_STATS_PERSONALIZATION_MVP.md` 참조)

## 관련 실행 문서

- `docs/PLAY_STORE_ASO.md`: #65용 Play Console ASO 실행 런북. 최종 copy, 스크린샷 구성, baseline, 반영 로그, 14일/30일 검증 포맷 포함
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`: #16용 광고 단위 감사, `(not set)` 점검, guardrail, 1차 수익화 실험 운영 기준
- `docs/USAGE_STATS_PERSONALIZATION_MVP.md`: #82용 Usage Access 범위, 권한 UX, MVP 리포트 4종, 규칙 기반 추천, 개인정보/정책 가드레일
