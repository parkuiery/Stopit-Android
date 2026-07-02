# 사용정보 기반 개인화 솔루션 / 리포트 MVP

이 문서는 GitHub issue #82에서 처음 정리된 Usage Access 개인화 아이디어를 이어받아, 현재 열린 umbrella issue #119 `[백로그] Usage Access 선택형 개인화 discovery 및 승격 게이트 정리`의 실행 판단 기준을 정리한 docs-lane source of truth다.

현재 backlog follow-through source of truth는 open issue #119 `[백로그] Usage Access 선택형 개인화 discovery 및 승격 게이트 정리`로 본다. 이 문서는 #119가 승격 판단을 할 때 참조하는 제품/권한/가드레일 계약서다.

목표는 막연한 “리포트 기능”을 제안하는 것이 아니라, 실제 착수 전에 아래 여섯 가지를 닫고, #119를 그대로 코드 lane에 넘기지 말고 discovery package와 implementation package의 경계를 분리하는 것이다.

1. `UsageStatsManager`로 조회 가능한 데이터와 Android 제약을 명확히 한다.
2. 권한 요청 UX와 미허용 fallback을 정의한다.
3. MVP 리포트 항목을 3~5개로 제한한다.
4. 개인화 추천 로직 v1을 규칙 기반으로 정의한다.
5. 개인정보/스토어 정책 리스크와 완화책을 문서화한다.
6. backlog에서 실행 후보로 승격할지 판단하는 검증 기준을 남긴다.

## 현재 이슈 상태 (#119)

- 상태: `backlog` 유지. 아직 구현 착수용 `ready` 이슈가 아니다.
- 이 문서가 닫는 범위: repo 내부에서 정리 가능한 권한 UX, 로컬 집계 계약, privacy guardrail, 측정 taxonomy, QA 시나리오, child issue 분리 기준.
- 이 문서가 닫지 않는 범위: 대표님/정책 판단이 필요한 실제 기능 승격 결정, 개인정보 처리방침/Play listing 문구의 최종 외부 반영, 구현 후 14일/30일 지표 검증.
- 실행 원칙: #119 자체를 코드 lane에 직접 넘기지 말고, 아래 `Discovery/contract package` 또는 `MVP implementation package`가 충분히 구체화됐을 때 별도 child issue를 만든다.
- 2026-06-01 docs-lane closure-pass: 권한 UX, 정책/스토어 문구 초안, QA evidence template, code-lane 파일 표면, 측정 분자/분모까지 repo 안에서 더 밀 수 있는 문서 범위를 이 문서와 연결 문서에 고정했다. 남은 경계는 **대표님 승격 판단 + 개인정보 처리방침/Play listing 실제 반영 + 구현/배포 후 14일·30일 재측정**이다.

## 왜 지금 문서화하는가

- Stopit의 핵심 가치는 “실제 앱 차단/집중 가치”다.
- 사용정보 리포트는 활성화/리텐션/프리미엄 확장 가능성이 있지만, 민감한 사용 패턴을 다루므로 신뢰/정책 리스크가 크다.
- 따라서 바로 구현 이슈로 밀지 말고, **권한/데이터/가드레일 계약**부터 명확히 해야 한다.
- 활성화 guardrail과 권한 요청 타이밍 해석은 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`의 퍼널 계약을 함께 따른다.
- queryability/GA4 Admin 등록 follow-through는 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 본다.

## 문제/기회 요약

### 타깃 사용자
- 공부/업무 중 특정 앱을 반복적으로 여는 사용자
- 앱 차단은 쓰지만 “언제, 어떤 앱에서 무너지는지”까지는 잘 모르는 사용자
- 루틴 차단 추천이 있으면 더 빨리 첫 가치를 얻을 수 있는 사용자

### 해결하려는 Job
- 기능적 Job: 내 사용 패턴을 보고 어떤 앱/시간대를 막아야 할지 알고 싶다.
- 감정적 Job: 의지 부족이 아니라 패턴 문제라는 걸 이해하고 싶다.
- 사회적 Job: 과한 자기비난 없이 스스로 통제력을 회복하고 싶다.

### 현재 workaround
- 스크린타임/디지털 웰빙 화면을 수동으로 본다.
- 감으로 앱을 선택해 차단한다.
- 실제 과사용 시간대와 차단 루틴이 어긋난다.

## Android / UsageStatsManager 범위와 제약

## 데이터 범위

`PACKAGE_USAGE_STATS` 접근이 허용되면 MVP에서 현실적으로 다룰 수 있는 데이터는 아래 수준이다.

- 앱별 사용 시간 추정치
- 앱별 실행/포그라운드 전환 빈도
- 일/주 단위 usage 합계
- 시간대별 사용 집중 구간(예: 22:00~24:00)
- 최근 7일 기준 상위 방해 앱 후보

MVP 범위에서 **하지 않는 것**:
- 메시지/콘텐츠 내용 조회
- 알림 내용 파싱
- 정확한 사용자 의도 추론
- 외부 서버로 원시 사용기록 업로드
- 연락처/위치/민감 카테고리 결합 분석

## Android 버전/플랫폼 제약

- 권한명은 일반 runtime permission이 아니라 Usage Access 설정 허용 흐름에 가깝다.
- 사용자는 시스템 설정 화면으로 이동해 직접 허용해야 하므로 이탈 위험이 있다.
- OEM/Android 버전에 따라 집계 지연, 이벤트 정확도, 백그라운드 동작 차이가 있을 수 있다.
- 사용량 데이터는 “추정/집계” 성격이므로 분 단위 정확도를 과신하면 안 된다.
- 다중 기기/복원/재설치 이후에는 연속 데이터가 끊길 수 있다.
- 앱별 사용량이 민감 정보처럼 해석될 수 있으므로 공유/노출 UX를 매우 보수적으로 설계해야 한다.

## 데이터 계약 초안

| 항목 | MVP 사용 여부 | 비고 |
| --- | --- | --- |
| 앱 패키지명 | 사용 | 내부 매핑용, 사용자 노출은 앱 라벨 우선 |
| 앱 라벨 | 사용 | 리포트/추천 UI에 표시 |
| 일별 사용 시간 | 사용 | 분 단위 요약 |
| 앱 실행 횟수 | 사용 | 보조 신호 |
| 시간대 분포 | 사용 | 추천 루틴 생성용 |
| 주간 변화량 | 사용 | 전주 대비 비교 |
| 메시지/웹 콘텐츠 | 미사용 | 수집 금지 |
| 외부 전송용 raw history | 미사용 | MVP 금지 |

## 권한 요청 UX / fallback 정책

### 권한 요청 원칙

- 첫 실행에서 강제하지 않는다.
- 사용자가 “왜 필요한지”를 이해하는 순간에만 요청한다.
- 차단 기능의 기본 가치를 먼저 경험하게 하고, 리포트/추천은 선택형 확장으로 둔다.

### 권한 요청 타이밍

추천 타이밍:
1. 사용자가 리포트 탭/카드를 처음 열 때
2. “내 사용 패턴 기반 추천 받기” CTA를 탭했을 때
3. 첫 차단 성공 후, 리포트 가치가 이해되는 시점

운영 메모:
- `first_lock_configured` 직전의 핵심 활성화 CTA를 Usage Access 요청이 가로막지 않도록 한다.
- `first_core_action_completed` 이전에는 선택형 설명/후순위 진입점 위주로 두고, canonical 퍼널 해석은 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`를 따른다.

비추천 타이밍:
- 온보딩 초반 일괄 권한 요청
- 접근성/알림 권한 직후 연속 요청
- 긴급해제/차단 민감 플로우 중간

### 권한 설명 카피 계약

권한 요청 전 사전 설명에 반드시 포함할 것:
- 무엇을 보는지: 앱별 사용 시간/빈도/시간대 패턴
- 무엇을 하지 않는지: 메시지 내용/개인 콘텐츠 조회 안 함
- 어디서 처리하는지: 기본은 기기 내 로컬 처리
- 사용자가 얻는 가치: 방해 앱 추천, 위험 시간대 파악, 루틴 제안
- 거절해도 가능한 것: 기존 앱 차단/타이머/루틴 기능 계속 사용 가능

예시 카피:

> 사용정보 접근을 허용하면, 자주 무너지는 앱과 시간대를 분석해 맞춤 차단 루틴을 제안할 수 있어요. 메시지 내용이나 개인 콘텐츠는 읽지 않으며, 기본 분석은 기기 안에서 처리됩니다.

### entry point별 권한 UX contract

| Entry point | 허용 activation stage | UX 원칙 | 금지 |
| --- | --- | --- | --- |
| 리포트 탭/카드 첫 진입 | `post_first_core_action`, `returning_user` 우선 | 리포트 가치 설명 → 사용자가 원할 때 설정 이동 | 온보딩 필수 권한처럼 노출 |
| “사용 패턴 기반 추천 받기” CTA | `post_first_core_action`, `returning_user` | 추천 결과가 어떤 데이터로 만들어지는지 먼저 설명 | 앱 목록 전체를 서버로 보내는 듯한 표현 |
| 첫 차단 성공 이후 soft prompt | `post_first_core_action` | “다음 루틴을 더 쉽게 만들 수 있음” 정도의 선택형 제안 | 성공 화면을 막거나 닫기 어렵게 만드는 modal |
| 설정/메뉴의 나중에 켜기 | `returning_user` | 사용자가 원할 때 재시도할 수 있는 보조 entry | badge/경고로 계속 압박 |

권한 복귀 상태 판별은 최소 세 상태로 둔다.

- `granted`: Usage Access 허용 확인. 리포트 카드 계산을 시도한다.
- `denied`: 설정에서 명시적으로 미허용 상태. fallback 카드와 수동 추천 템플릿을 유지한다.
- `unknown`: 설정 이동 후 앱 복귀가 없거나 OEM 화면에서 상태를 판별하지 못함. 오류가 아니라 “나중에 다시 확인” 상태로 처리한다.

### fallback 정책

권한 미허용 시에도 아래는 유지한다.
- 앱 선택 차단
- 타이머 잠금
- 루틴 잠금
- 긴급해제
- 잠금 기록

권한 미허용 상태에서 대체로 보여줄 것:
- 리포트 대신 “권한을 허용하면 지난 7일 사용 패턴을 볼 수 있어요” 안내 카드
- 추천 대신 수동 추천 템플릿(예: 공부용 SNS 차단 루틴)
- 설정에서 나중에 다시 활성화 가능한 entry point

## MVP 리포트 범위

MVP는 아래 4개 카드로 제한한다.

### 1. 지난 7일 상위 방해 앱 Top 5
- 정의: 사용 시간이 많거나 실행 빈도가 높은 앱 상위 5개
- 목적: 어떤 앱부터 차단 후보로 볼지 빠르게 이해

### 2. 위험 시간대
- 정의: 최근 7일 사용량이 가장 몰린 시간대 블록(예: 22:00~24:00)
- 목적: 타이머보다 루틴 차단이 더 적합한 구간 식별

### 3. 전주 대비 변화
- 정의: 이번 7일 vs 직전 7일 기준 총 방해 앱 사용 시간 변화
- 목적: 차단/루틴 적용 후 변화가 있었는지 확인

### 4. 추천 차단 시작점
- 정의: Top 앱 + 위험 시간대를 기반으로 한 “바로 차단하기” 제안 1~2개
- 목적: 리포트를 행동으로 연결

MVP에서 제외:
- 월간 장문 리포트
- 감정/중독 수준 점수화
- 앱 카테고리별 정교한 분류
- 친구 비교/랭킹/공유
- 서버 기반 개인화 모델

## 개인화 추천 로직 v1

v1은 설명 가능한 규칙 기반으로 시작한다.

### 추천 규칙 A: 상위 방해 앱 차단 제안
- 조건: 최근 7일 사용 시간이 높은 앱이 있고, 현재 차단 목록에 없음
- 제안: “가장 자주 열리는 앱 1~3개를 차단 목록에 추가”

### 추천 규칙 B: 위험 시간대 루틴 제안
- 조건: 특정 2시간 블록 사용량이 전체의 큰 비중을 차지함
- 제안: 해당 시간대 반복 루틴 생성 제안

### 추천 규칙 C: 야간 과사용 완화 제안
- 조건: 밤 시간대(예: 22:00 이후) 사용량이 높음
- 제안: 수면 전 차단 루틴 또는 짧은 타이머 루틴 제안

### 추천 규칙 D: 반복 실패 앱 우선 차단
- 조건: 실행 빈도는 높지만 현재 차단/루틴 적용 이력이 낮음
- 제안: 자주 열지만 아직 관리하지 않는 앱 우선 추천

### 추천 규칙 E: 가벼운 시작 제안
- 조건: 아직 루틴이 없는 신규 사용자
- 제안: 하루 1개 시간대, 1~2개 앱만 고르는 최소 설정 추천

### 추천 UX 원칙
- “당신은 중독입니다” 같은 낙인 표현 금지
- 추천 이유를 항상 설명 가능하게 보여준다
- 사용자가 추천을 바로 수정/거절할 수 있어야 한다
- 기본값은 공격적인 전면 차단보다 작은 시작점이어야 한다

## 개인정보 / 정책 / 신뢰 가드레일

### 개인정보 원칙
- raw usage history 외부 전송 금지(MVP)
- 집계/추천은 로컬 처리 우선
- 리포트 화면 스크린샷 공유를 기본 기능으로 넣지 않음
- 민감한 앱 이름이 노출될 수 있으므로 잠금 화면/공용 화면 노출 주의

### Play 정책 / 신뢰 리스크
- Usage Access는 사용자에게 민감 권한처럼 보일 수 있어 설명 책임이 크다.
- 권한 설명 없이 바로 설정 화면으로 보내면 허용률과 신뢰가 동시에 떨어질 수 있다.
- “사용자 감시”처럼 느껴지는 카피/그래픽을 피해야 한다.
- 앱 설명/개인정보 처리방침/인앱 카피에서 사용 목적을 일관되게 설명해야 한다.

### 개인정보 처리방침 / Play listing 반영 체크리스트

아래 문구는 최종 법무/대표님 승인 전 repo 내부 초안이다. 외부 반영 여부는 이 문서만으로 완료 처리하지 않는다.

| 표면 | 포함할 메시지 | 금지/주의 |
| --- | --- | --- |
| 인앱 사전 설명 | “앱별 사용 시간/실행 빈도/시간대 패턴을 기기에서 분석해 차단 루틴을 제안” | “모든 활동을 추적”, “감시”, “중독 진단” |
| Play Store 설명 | 선택형 개인화 기능이며 기본 차단 기능은 권한 없이도 사용 가능 | Usage Access가 필수 권한처럼 보이는 표현 |
| 개인정보 처리방침 | Usage Access 사용 목적, 처리 범위, 원시 사용기록 외부 전송 금지(MVP), 로컬 처리 원칙 | 앱 이름/package/raw history를 analytics나 서버 전송 항목으로 쓰는 표현 |
| QA/릴리즈 노트 | 권한 허용/거절/fallback, core activation 방해 없음, analytics 금지 payload 검증 | “권한 허용률 상승”만 성공으로 보는 해석 |

외부 반영 전 판단 질문:

1. 사용자가 Usage Access를 허용하지 않아도 Stopit의 차단/타이머/루틴/긴급해제 가치가 유지된다는 문구가 있는가?
2. “메시지 내용이나 개인 콘텐츠를 읽지 않는다”는 설명이 인앱과 외부 listing에서 충돌하지 않는가?
3. raw usage history를 외부 서버 또는 analytics payload로 전송하지 않는다는 MVP 경계가 개인정보 처리방침과 맞는가?
4. Play 심사자가 기능 목적을 “개인화 차단 루틴 추천”으로 이해할 만큼 구체적인가?

### 금지선
- 권한이 없으면 핵심 차단 기능을 막는 설계
- 메시지/콘텐츠를 읽는 것처럼 오해될 표현
- 사용 패턴을 외부 공유하게 만드는 성장 루프
- 미성년/민감 사용자에게 죄책감을 유도하는 문구

## 현재 #13 queryability 경계

2026-05-29 live 확인 기준으로 Stopit의 제품/지표 문서는 아직 **Usage Access MVP 자체를 측정할 새 `customEvent:*` 축까지 등록한 상태가 아니다.** 현재 확인된 live metadata는 `customUser:routines_count`만 보이고, activation/review/monetization 축조차 `customEvent:*` registration gap이 남아 있다.

따라서 이 문서는 Usage Access MVP를 "지금 바로 구현 가능한 계측-ready 기능"으로 간주하지 않는다. 실행 후보로 승격할 때는 아래를 먼저 별도 계약으로 닫아야 한다.

- Usage Access 설명/설정 진입/허용/거절/추천 적용 흐름에서 어떤 이벤트와 파라미터를 남길지 코드·문서 기준을 먼저 정의한다.
- 그 파라미터가 실제 분석에 필요하다면 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` 형식으로 registration ledger에 추가하고, GA4 Admin 등록/metadata 확인까지 추적한다.
- `runReport`가 `400 INVALID_ARGUMENT` / `Field customEvent:... is not a valid dimension`을 반환하면 no-data가 아니라 **미등록 쿼리 축**으로 분류한다.

즉 #119를 `ready`나 `priority:p1` 쪽으로 승격하기 전에, 이 문서의 제품/권한 계약과 #13의 queryability 계약을 함께 닫는 것이 기본값이다.

## 측정 계획

### 성공 판단 후보 지표
- Usage Access 설명 카드 노출 대비 설정 진입률
- 설정 진입 대비 권한 허용률
- 권한 허용 사용자 중 추천 CTA 클릭률
- 추천으로 생성된 차단/루틴 적용률
- 권한 허용 사용자 7일 반복 사용률

### 측정 분자/분모 계약

| 판단 | 분자 | 분모 | 기간 | 해석 |
| --- | --- | --- | --- | --- |
| 설명 → 설정 이동 | `usage_access_settings_opened` users | `usage_access_explainer_viewed` users | 14일 | 설명 카피/entry point가 설득력 있는지 |
| 설정 이동 → 허용 | `usage_access_permission_result(result=granted)` users | `usage_access_settings_opened` users | 14일 | 설정 이동 UX와 권한 설명 신뢰도 |
| 허용 → 리포트 가치 | `usage_report_viewed(has_recommendation=true)` users | granted users | 14일/30일 | 데이터 충분성/추천 생성률 |
| 추천 → 적용 | `usage_recommendation_applied` users | `usage_recommendation_tapped` users | 14일/30일 | 추천이 실제 차단/루틴 행동으로 이어지는지 |
| activation guardrail | `first_core_action_completed` users / `first_open` users | 동일 기간 신규 사용자 | 14일/30일 | Usage Access entry가 핵심 가치 경험을 방해하지 않는지 |

모든 지표는 `activation_stage`별로 나눠 본다. 특히 `pre_first_core_action`에서 허용률이 좋아 보여도 `first_core_action_completed`가 악화되면 실패로 본다.

해석 주의:
- 위 지표는 Usage Access 전용 이벤트/파라미터의 code contract와 GA4 queryability가 실제로 확보됐을 때만 실험 판단 근거로 쓴다.
- #13 registration follow-through가 끝나기 전에는 "계측값이 0/희박하다"와 "GA4 Admin 등록이 아직 안 됐다"를 구분해야 한다.

### guardrail
- `first_lock_configured / first_open` 악화 금지
- `app_block_intercepted` 사용자 급락 금지
- review/rating 악화 금지
- crash-free users 악화 금지
- 권한 연속 요청으로 onboarding 이탈 증가 금지

### 이벤트 taxonomy 초안

이 taxonomy는 구현 전 계약 초안이다. 실제 코드 반영 전에는 `docs/ANALYTICS_EVENT_DICTIONARY.md`의 production 이벤트 표에 추가하지 않는다.

| 이벤트 | 발생 시점 | 최소 파라미터 | 금지 파라미터 |
| --- | --- | --- | --- |
| `usage_access_explainer_viewed` | Usage Access 사전 설명 카드/화면 노출 | `entry_point`, `activation_stage` | 앱 이름, package, raw usage |
| `usage_access_settings_opened` | 사용자가 시스템 설정으로 이동 | `entry_point`, `activation_stage` | 앱별 사용량 원문 |
| `usage_access_permission_result` | 앱 복귀 후 허용/거절/미확인 상태 판별 | `result`, `entry_point`, `activation_stage` | 설치 앱 전체 목록 |
| `usage_report_viewed` | 권한 허용 후 리포트 요약 노출 | `report_period`, `has_recommendation`, `top_app_count_bucket` | 앱 이름, package, 정확한 분 단위 원문 |
| `usage_recommendation_tapped` | 추천 차단/루틴 CTA 탭 | `recommendation_type`, `report_period` | 추천 대상 앱 이름/package |
| `usage_recommendation_applied` | 추천으로 차단/루틴 생성 완료 | `recommendation_type`, `selected_app_count_bucket` | 선택 앱 목록 원문 |

원칙:
- 앱 이름/package는 analytics에 보내지 않는다. UI 표시와 로컬 추천 계산에만 쓴다.
- 시간/횟수는 가능한 bucket으로 보낸다. 예: `0`, `1`, `2-3`, `4-5`, `6+` 또는 `0-30m`, `31-60m`, `61-120m`, `120m+`.
- `activation_stage`는 `pre_first_core_action`, `post_first_core_action`, `returning_user`처럼 퍼널 방해 여부를 판단할 수 있는 값으로 제한한다.

## backlog → 실행 후보 승격 기준

아래 조건이 충족되면 `backlog`에서 실행 후보로 올린다.

### 승격 전 필수 확인
- [ ] 제품/정책 관점에서 Usage Access 사용 목적이 문서/앱/개인정보 고지에 일관되게 설명 가능하다.
- [ ] 권한 미허용 fallback이 명확하다.
- [ ] MVP 리포트 범위가 4개 카드 수준으로 제한되어 있다.
- [ ] 추천 로직이 규칙 기반으로 설명 가능하다.
- [ ] 민감 데이터 외부 전송 없이도 v1 검증이 가능하다.
- [ ] Usage Access 전용 이벤트/파라미터가 필요하면 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` 형식의 registration ledger와 metadata 확인 절차까지 정의한다.

### 실행 우선순위 판단 질문
1. 이 기능이 `first_core_action_completed` 또는 반복 사용률을 실제로 끌어올릴 가능성이 높은가?
2. 권한 허용률이 너무 낮아도 가치 검증이 가능한가?
3. 기존 계측 품질(#13), 활성화 퍼널(#14)보다 지금 먼저 할 이유가 충분한가?
4. 작은 MVP로 14일/30일 후 효과를 비교할 수 있는가?

### 권장 승격 형태

구현 시작 시에는 큰 하나의 기능 이슈로 바로 가지 말고, 아래 2단계 패키지 중 어디까지 할지 먼저 정한다.

1. **Discovery/contract package**
   - 권한 UX entry point와 사전 설명 카피
   - 설정 이동/복귀 후 권한 상태 판별 contract
   - 로컬 데이터 모델과 bucket 단위
   - 추천 규칙/측정 이벤트
   - 개인정보/정책 문구
   - QA 시나리오와 kill criteria
2. **MVP implementation package**
   - 리포트 카드 4종
   - 추천 CTA
   - 권한 허용/거절 instrumentation
   - formatter/ViewModel/permission-return 테스트

### Discovery/contract child issue 템플릿

#119를 승격할 때 첫 child issue는 아래 범위를 한 번에 닫을 수 있어야 한다. 이 템플릿 수준으로 파일/검증이 명확하지 않으면 아직 `ready`가 아니다.

```md
## 문제
Usage Access 개인화가 핵심 활성화 퍼널을 방해하지 않는 선택형 확장인지 검증할 권한 UX·측정·QA 계약이 필요하다.

## 제안 작업
- 권한 사전 설명 copy와 entry point를 `post_first_core_action` / returning user 중심으로 정의한다.
- 설정 이동/복귀 후 권한 상태 판별, 미허용 fallback, 재시도 entry point를 문서화한다.
- `UsageStatsManager` 집계 bucket, analytics 이벤트 초안, 금지 파라미터를 확정한다.
- 개인정보 처리방침/Play listing/인앱 고지 업데이트 필요 여부를 체크리스트화한다.
- QA 시나리오와 kill criteria를 `docs/QA_RUNTIME_CHECKLIST.md` 또는 별도 runbook에 연결한다.

## 완료 기준
- [ ] 권한 설명/거절 fallback/설정 복귀 contract가 문서화된다.
- [ ] production analytics에 raw app/package/usage history를 보내지 않는 이벤트 계약이 확정된다.
- [ ] 구현 child issue가 참조할 화면, 이벤트, 테스트, 문서 범위가 충분히 구체화된다.
- [ ] #13/#14 선행 조건과 충돌하지 않는 승격 판단이 남는다.
```

### MVP implementation child issue 템플릿

Discovery/contract package가 닫힌 뒤에만 아래 구현 issue를 `ready` 후보로 만든다.

```md
## 문제
사용자가 Usage Access를 허용했을 때 지난 7일 방해 앱/시간대 리포트와 작은 추천 CTA를 로컬에서 제공해 반복 사용과 루틴 생성을 검증한다.

## 제안 작업
- Usage Access 권한 상태 감지와 설정 이동/복귀 UI를 구현한다.
- Top 5, 위험 시간대, 전주 대비 변화, 추천 시작점 카드의 formatter/ViewModel contract를 구현한다.
- 추천 CTA를 차단 목록 또는 루틴 생성 흐름으로 연결한다.
- `usage_*` analytics 이벤트와 bucket 파라미터를 구현하고 이벤트 딕셔너리를 업데이트한다.
- privacy guardrail 단위 테스트와 권한 복귀 QA 기준을 추가한다.

## 완료 기준
- [ ] 권한 미허용 상태에서도 기존 차단/타이머/루틴/긴급해제가 유지된다.
- [ ] analytics에 앱 이름/package/raw usage history가 전송되지 않는 테스트가 있다.
- [ ] 권한 허용/거절, empty state, 추천 적용 경로가 검증된다.
- [ ] 배포 후 14일/30일 비교 지표가 문서에 남는다.
```

### QA 시나리오 초안

- 권한 없음: 리포트 entry point 진입 시 사전 설명과 fallback 카드가 보이고 기존 차단 기능은 계속 동작한다.
- 설정 이동 후 허용: 앱 복귀 시 `usage_access_permission_result(result=granted)`와 리포트 카드 노출이 가능하다.
- 설정 이동 후 거절/뒤로가기: `denied` 또는 `unknown` 상태로 처리하고 재시도 CTA는 압박 없이 남긴다.
- 데이터 없음/집계 지연: empty state를 보여주고 추천 CTA를 숨긴다.
- privacy regression: analytics payload와 공유/로그에 앱 이름, package, raw usage history가 포함되지 않는다.
- 활성화 guardrail: `pre_first_core_action` 사용자에게 권한 요청이 핵심 잠금 CTA보다 앞서 뜨지 않는다.

### QA evidence template

```md
## Usage Access discovery/QA evidence
- Build / appVersion:
- Device / Android version / OEM:
- Entry point: report_card / recommendation_cta / post_success_soft_prompt / settings
- Activation stage before prompt: pre_first_core_action / post_first_core_action / returning_user
- Permission state before test: not_allowed / allowed / unknown
- Steps:
  1. 사전 설명 노출 확인
  2. 시스템 설정 이동 확인
  3. 허용/거절/뒤로가기 후 앱 복귀 확인
  4. fallback 또는 리포트 카드 노출 확인
- Expected analytics without sensitive payload:
  - `usage_access_explainer_viewed(entry_point=..., activation_stage=...)`
  - `usage_access_settings_opened(entry_point=..., activation_stage=...)`
  - `usage_access_permission_result(result=granted|denied|unknown, entry_point=..., activation_stage=...)`
- Privacy checks:
  - 앱 이름/package/raw usage history가 analytics/log/share payload에 없음
  - 권한 거절 후에도 앱 차단/타이머/루틴/긴급해제 진입 가능
- Notes / screenshots:
```

## code-lane handoff 표면

이 이슈가 child implementation issue로 승격될 때 code-lane은 아래 표면을 먼저 확인한다. 파일명은 현재 repo 구조 기준의 예상 표면이며, 실제 구현 전 `search_files`로 최신 위치를 다시 확인한다.

| 표면 | 후보 작업 | 검증 |
| --- | --- | --- |
| Usage Access permission policy | 권한 상태 감지, 설정 intent, `granted/denied/unknown` 변환 helper | pure JVM policy test 또는 Robolectric/Android instrumentation |
| Report formatter | Top 5/위험 시간대/전주 대비 변화/추천 시작점 문구와 bucket 계산 | formatter 단위 테스트, privacy payload regression |
| ViewModel / screen | entry point, fallback card, settings return, recommendation CTA | ViewModel test + Compose/UI smoke |
| Analytics | `usage_*` 이벤트 구현, bucket 파라미터, 금지 payload 테스트 | `FirebaseKeepAnalyticsTest`, event dictionary sync |
| QA docs | Usage Access 권한 수동/자동 evidence 연결 | `docs/QA_RUNTIME_CHECKLIST.md`, PR body verification |

최소 구현 PR은 앱 runtime, analytics, 문서, QA를 한 번에 맞춘다. 이벤트명만 추가하거나 문서만 바꾸는 얇은 PR은 #119의 child implementation으로 보지 않는다.

현재 기준 판단:
- **상태: backlog 유지**
- 이유: 제품 방향성은 좋지만, 계측 품질(#13)과 활성화 퍼널(#14) 정리가 아직 선행 우선순위다.
- 승격 조건: 계측/활성화 기반선이 안정되고, Usage Access 허용률을 가늠할 권한 UX 초안 검토가 끝났을 때

## 구현 전 확인 체크리스트

- [ ] Android 설정 이동/복귀 흐름을 포함한 권한 UX 와이어 초안이 있다.
- [ ] `UsageStatsManager` 집계 정합성 확인용 QA 시나리오가 있다.
- [ ] 리포트에 노출할 앱 이름/시간대가 과도하게 민감하지 않은지 검토했다.
- [ ] 개인정보 처리방침/스토어 설명 업데이트 필요 여부를 확인했다.
- [ ] 실험 성공/실패를 판단할 이벤트와 비교 기간(14일/30일)을 정의했다.

## 관련 문서
- `docs/METRICS_ANALYSIS.md`
- `docs/PRODUCT_METRICS_DASHBOARD.md`
- `docs/PLAY_STORE_ASO.md`
- `docs/ANALYTICS_EVENT_DICTIONARY.md`
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
- `docs/ops/stopit/product-context.md`
- `docs/ops/stopit/metrics-context.md`
