# 루틴 템플릿 공유 MVP

Issue: #407

이 문서는 `RoutineModel` 기반 루틴을 privacy-safe한 선택형 공유 MVP로 운영하기 위한 제품/analytics/QA/implementation 계약을 고정한다. 구현 PR의 source of truth는 코드와 `docs/ANALYTICS_EVENT_DICTIONARY.md`이며, GA4 Admin 등록·배포 후 측정 전까지는 이 문서의 외부/manual 경계를 따른다.

## 한 줄 목표

반복 사용자가 만든 공부/업무/야간 집중 루틴의 **비민감 패턴**만 Android share sheet로 공유해, 앱 사용 문제를 노출하지 않는 작은 성장 루프를 검증한다.

## 왜 지금 이 기능인가

- 2026-06-03 루틴 반복 사용 기준선에서 `customUser:routines_count >= 1` 사용자는 미보유자보다 sessions / activeUsers와 `app_block_intercepted` users / activeUsers가 높았다.
- `docs/PRODUCT_METRICS_DASHBOARD.md`는 “루틴 템플릿 공유 루프”를 성장 후보로 정의했지만, #407 전에는 privacy-safe payload와 analytics/QA 계약이 없었다.
- Android share sheet 텍스트 MVP는 backend, 계정, 서버 링크 없이 시작할 수 있어 실험 비용이 낮다.
- 다만 `RoutineModel`은 `name`, `startTime`, `endTime`, `repeatDays`, `lockApplications`를 함께 갖기 때문에, `lockApplications`, package name, 앱 이름, raw session history를 기본 payload/analytics에서 제외하는 계약이 먼저 필요하다.

## MVP 범위

### 포함

- 루틴 상세/목록의 선택형 공유 CTA.
- Android share sheet 기반 plain text 공유.
- Play Store 링크 포함.
- 루틴의 비민감 패턴만 공유:
  - 템플릿 카테고리: `study`, `work`, `night_focus`, `custom`
  - 반복 요일 bucket
  - 시간대 bucket
  - 선택적으로 사용자가 직접 붙인 루틴 이름
- 공유 CTA/intent launch/실패 analytics 초안.
- formatter/helper 요구사항과 단위 테스트 기준.
- import/deep link 전환 전 decision gate.

### 제외

- deep link/import 자동 적용. deep link/import는 별도 결정 게이트를 통과한 뒤 새 child issue로 분리한다.
- 서버 저장 템플릿, 공개 피드, 랭킹, 추천인 코드.
- 이미지 카드 생성.
- `lockApplications`, package name, 앱 이름, raw session history, raw 사용 시간, 특정 차단 앱 수치.
- Usage Access 기반 리포트/추천 공유. 이 범위는 #119 discovery gate를 따른다.

## 사용자 경험 계약

### CTA 노출 조건

| 조건 | 동작 |
| --- | --- |
| 루틴이 저장되어 있고 `repeatDays` 또는 시간대가 유효함 | 공유 CTA 노출 가능 |
| 루틴 저장 전 임시 입력 상태 | CTA 숨김 |
| `lockApplications`만 있고 시간/요일이 비어 있음 | CTA 숨김 또는 “먼저 시간/요일을 설정하세요” 안내 |
| 긴급해제/잠금 중 | 실험 초기는 CTA 숨김. 공유 압박으로 보이면 안 됨 |

### 권장 CTA / share sheet 카피

- 버튼/텍스트: `루틴 템플릿 공유`
- 접근성 설명: `루틴 시간대와 요일 템플릿을 다른 앱으로 공유`
- share sheet title: `스탑잇 루틴 템플릿 공유`

### 기본 공유문

```text
스탑잇 집중 루틴 템플릿
{categoryLabel} · {repeatDaysText} · {timeWindowText}
나도 집중이 필요한 시간에 앱 사용을 잠깐 멈춰요.
{playStoreUrl}
```

예시:

```text
스탑잇 집중 루틴 템플릿
공부 · 평일 · 저녁 2시간
나도 집중이 필요한 시간에 앱 사용을 잠깐 멈춰요.
https://play.google.com/store/apps/details?id=com.uiery.keep
```

### 루틴 이름 처리

루틴 이름은 사용자가 직접 붙인 텍스트이므로 민감 정보가 섞일 수 있다. MVP 기본값은 루틴 이름을 공유문에 넣지 않는 것이다.

루틴 이름을 포함하는 variant를 실험하려면 아래 조건을 모두 만족해야 한다.

- 사용자가 공유 직전 preview에서 이름 포함 여부를 명시적으로 확인할 수 있다.
- formatter 테스트가 이름을 제외한 기본 payload를 검증한다.
- analytics에는 이름 원문을 보내지 않고 `routine_name_included=true/false`만 기록한다.
- 이름이 비어 있거나 너무 길면 category/time window 기반 템플릿 문구로 fallback한다.

## Privacy / trust guardrail

| 항목 | 허용 | 금지 |
| --- | --- | --- |
| 루틴 패턴 | 카테고리, 요일 bucket, 시간대 bucket | `lockApplications`, package name, 앱 이름 |
| 루틴 이름 | 기본 제외, 사용자가 preview에서 명시 선택한 경우만 표시 | 이름 원문 analytics 전송 |
| 처리 위치 | 로컬 formatter + Android share sheet | 서버 업로드, 공개 템플릿 피드 |
| 문구 | 긍정적 자기 선언 | 실패/중독/감시/랭킹/비교 |
| 측정 | enum/bucket 파라미터 | raw session history, raw package/app data |

구현 PR은 `RoutineTemplateSharePayload` 같은 formatter/helper 테스트에서 다음을 명시적으로 검증해야 한다.

- payload에 `lockApplications`, package name, 앱 이름, raw session history가 들어가지 않는다.
- category/repeat/time window가 사람이 이해할 수 있는 텍스트로 변환된다.
- 루틴 이름은 기본 제외되고, 포함 variant에서도 analytics에 원문이 남지 않는다.
- Play Store 링크가 포함된다.
- 유효하지 않은 루틴 입력에서는 payload 생성이 실패하거나 CTA가 숨겨진다.

## Analytics 계약 초안

구현 PR에서 `KeepAnalytics.kt`, Firebase 구현, 테스트, `docs/ANALYTICS_EVENT_DICTIONARY.md`를 함께 업데이트한다.

| 이벤트 | 트리거 | 파라미터 | 민감 정보 정책 |
| --- | --- | --- | --- |
| `routine_template_share_tapped` | 사용자가 루틴 템플릿 공유 CTA를 누름 | `template_category`, `repeat_days_bucket`, `time_window_bucket`, `routine_name_included` | 앱 이름/package/lockApplications/raw session history 금지 |
| `routine_template_share_sheet_opened` | share sheet intent launch 시도/성공 | `template_category`, `repeat_days_bucket`, `time_window_bucket`, `routine_name_included` | enum/bucket/boolean만 허용 |
| `routine_template_share_failed` | ActivityNotFoundException 등으로 공유 실패 | `template_category`, `reason` | reason은 enum만 허용 |

권장 enum/bucket:

- `template_category`: `study`, `work`, `night_focus`, `custom`
- `repeat_days_bucket`: `weekday`, `weekend`, `daily`, `custom_days`, `none`
- `time_window_bucket`: `morning`, `afternoon`, `evening`, `night`, `overnight`, `custom_window`
- `routine_name_included`: `true` / `false`
- `routine_template_share_failed.reason`: `activity_not_found`, `invalid_template`

GA4 custom dimension 등록은 구현 완료 후 별도 수동/운영 단계가 필요하다. 구현 PR이 event dictionary를 갱신하더라도, GA4 Admin 등록과 실제 queryability 확인 전까지는 템플릿 공유 전환율 결론을 낮은 confidence로 둔다.

## 측정 계획

### 14일 확인

- 기간: 공유 CTA 포함 버전 배포 후 14일 vs 배포 전 동기간.
- 기본 분자/분모:
  - `routine_template_share_tapped` users / 루틴 보유 active users (`customUser:routines_count >= 1`)
  - `routine_template_share_sheet_opened` users / `routine_template_share_tapped` users
  - `routine_template_share_failed` users / `routine_template_share_tapped` users
- guardrail:
  - crash-free users
  - Play Store rating/review tone
  - `first_core_action_completed` users / active users
  - `app_block_intercepted` users / active users
- 판단:
  - tap이 거의 없으면 CTA 위치/카피보다 먼저 루틴 보유 사용자 분모와 version adoption을 확인한다.
  - share sheet 실패가 높으면 intent fallback과 invalid-template 방어를 우선한다.
  - 리뷰/평점 악화 또는 privacy 우려 리뷰가 보이면 실험을 중단하거나 CTA를 숨긴다.

### 30일 확인

- 기간: 공유 CTA 포함 버전 배포 후 30일 vs 배포 전 30일.
- 확인 지표:
  - 공유 이벤트 unique users와 repeat share 비율
  - 공유 사용자의 D7 repeat block/session 여부
  - `Organic Search` newUsers + Play Console Search/Explore + external/campaign/UTM 기록
  - 루틴 보유 cohort sessions / activeUsers 변화
- 판단:
  - Organic Search 신호가 없더라도 루틴 보유자 retention이 개선되면 “획득 루프”보다 “반복 사용 강화” 기능으로 재분류할 수 있다.
  - Play Console Search/Explore와 GA4 `Organic Search`가 같은 방향이 아니면 #65/#242 attribution gate를 먼저 적용한다.

## QA / 테스트 체크리스트

### 단위 테스트

- `RoutineTemplateSharePayload` formatter:
  - category/repeat/time window/Play 링크 포함.
  - `lockApplications`, package name, 앱 이름, raw session history 미포함.
  - 루틴 이름 기본 제외와 opt-in 포함 여부.
  - invalid routine이면 payload 생성 실패 또는 CTA disabled.
- analytics bucket mapper:
  - `repeat_days_bucket` 경계값.
  - `time_window_bucket` 경계값.
  - `template_category` fallback.
- ViewModel/state:
  - 저장된 루틴 있음 → CTA state 활성.
  - 저장 전/invalid 루틴 → CTA state 비활성.
  - share sheet 실패 → 크래시 없이 실패 side effect / analytics.

### Android/수동 QA

- 공유 가능한 앱이 있을 때 share sheet가 열린다.
- 공유 대상 앱이 없거나 intent launch가 실패해도 앱이 크래시하지 않는다.
- TalkBack/접근성 라벨이 의미를 전달한다.
- 잠금/긴급해제/safety flow 화면에서 공유 CTA가 사용자를 압박하지 않는다.
- 공유 preview가 있다면 민감 정보가 없는지 수동으로 확인한다.

## 구현 패키지 추천 범위

이 문서 PR은 discovery/contract 산출물이다. 구현 착수 시에는 #407을 바로 닫기보다 아래 패키지를 한 PR에서 끝까지 처리한다.

1. `RoutineTemplateSharePayload` 같은 작은 formatter/helper 추가.
2. helper 단위 테스트로 privacy guardrail RED/GREEN.
3. `RoutineViewModel` 또는 루틴 UI state에 공유 가능 여부/summary payload 연결.
4. 루틴 상세/목록 화면에 선택형 CTA와 share sheet side effect 추가.
5. `KeepAnalytics` 이벤트/구현/테스트 추가.
6. `docs/ANALYTICS_EVENT_DICTIONARY.md` 이벤트 반영.
7. `docs/QA_RUNTIME_CHECKLIST.md` 또는 관련 QA 문서에 수동 share sheet 확인 추가.

`Closes #407`는 위 구현+테스트+event dictionary+QA 문서+GA4 Admin handoff까지 완료했을 때만 사용한다. 이 문서-only PR은 `Refs #407`가 맞다.

## 외부/manual 경계

- GA4 Admin custom dimension 등록은 GitHub PR만으로 완료되지 않는다.
- Play Store / Organic Search 영향은 배포 후 14일/30일이 지나야 판단 가능하다.
- deep link/import는 UX/보안/중복 루틴 처리 결정을 먼저 해야 한다.
- 실제 공유 후 설치 attribution은 현재 backend/deep link 없이 강하게 연결하기 어렵다. MVP에서는 Organic Search, store listing conversion, 이벤트 추세를 낮은 confidence로 함께 본다.

## 회귀 방지

문서 lane이 #407을 다시 만질 때 이 테스트가 깨지면 루틴 템플릿 공유 계약/링크가 drift된 것이다.

```bash
python3 -m unittest scripts.tests.test_routine_template_share_contract -v
```

이 regression은 routine-template share contract regression으로, runbook·analytics dictionary·metrics/product context가 같은 source of truth를 보도록 고정한다.

## 중복/연계 이슈

- #119: Usage Access 기반 개인화 리포트 discovery. 민감한 앱 사용 데이터를 다루므로 이 공유 MVP와 합치지 않는다.
- #211: LockHistory 주간 집중 요약 공유 MVP. 이미 완료된 집중 성공 공유 계약이며, 루틴 템플릿은 별도 루프다.
- #13: GA4 queryability / custom dimension 등록 경계. 공유 이벤트 추가 후에도 GA4 Admin 등록과 metadata 확인이 필요하다.
- #65/#242: Organic Search / acquisition attribution gate. 공유 루프의 획득 효과를 ASO 성과로 단정하기 전에 Play Console Search/Explore와 external/campaign/UTM 경계를 확인한다.
