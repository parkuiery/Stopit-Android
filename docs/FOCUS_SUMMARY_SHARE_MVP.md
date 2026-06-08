# LockHistory 주간 집중 요약 공유 MVP

Issue: #211
Follow-up debt: #597

상태: **#211 repo-internal MVP 구현 완료 / #597 code-lane runtime 전환 완료**

이 문서는 `LockHistory`에 이미 존재하는 주간/월간 잠금 기록 요약을 기반으로, privacy-safe한 선택형 공유 MVP를 실행 가능한 제품/analytics/QA 계약으로 정리한다. 구현 PR의 source of truth는 코드와 `docs/ANALYTICS_EVENT_DICTIONARY.md`가 되며, 이 문서는 공유 CTA/analytics/privacy 계약과 #597 localization runtime 계약을 함께 고정한다. CTA/share sheet title뿐 아니라 `FocusSummarySharePayload.kt`의 공유 본문과 duration text도 `AndroidFocusSummaryShareTextProvider` + Android string/plural resources로 분리되어 locale-ready runtime 계약을 따른다.

## 한 줄 목표

반복 사용자가 “이번 주 집중을 지켰다”는 긍정 경험을 민감 정보 없이 공유할 수 있게 하여, 신뢰를 해치지 않는 organic growth loop를 작게 검증한다.

## 왜 지금 이 기능인가

- `LockHistory`는 이미 기간별 `sessionCount`, `totalDuration`, `durationByDate`, `topApps`를 계산한다.
- `docs/PRODUCT_METRICS_DASHBOARD.md`는 “집중 성공 공유 루프”를 성장 후보로 정의하지만, 아직 실행 계약이 얇다.
- 공유 MVP는 backend, 계정, deep link 없이 Android share sheet로 시작할 수 있어 실험 비용이 낮다.
- 다만 앱 차단/스마트폰 사용 문제는 민감할 수 있으므로, 앱 목록·package name·실패/중독 뉘앙스를 기본 공유물에서 배제해야 한다.

## MVP 범위

### 포함

- `LockHistory` 주간 요약 화면의 선택형 공유 CTA.
- 기록이 있는 주간에만 CTA 노출.
- Android `ACTION_SEND` 기반 plain text share sheet.
- Play Store 링크 포함.
- 공유 CTA와 share sheet open 시도에 대한 최소 analytics.
- formatter/ViewModel 단위 테스트와 share intent 실패/대상 앱 없음 방어.

### 제외

- 이미지 카드 생성.
- 서버 링크/deep link/친구 초대 코드.
- 랭킹, 비교, 챌린지, 공개 피드.
- 차단 앱 이름, package name, raw session, 날짜별 상세 기록 공유.
- Usage Access 기반 사용 리포트 공유. 이 범위는 #119의 별도 discovery gate를 따른다.

## 사용자 경험 계약

### CTA 노출 조건

| 조건 | 동작 |
| --- | --- |
| 주간 `sessionCount > 0` | 공유 CTA 노출 가능 |
| 주간 `sessionCount == 0` | 공유 CTA 숨김 |
| 월간 탭 | MVP에서는 숨김. 주간 실험이 유의미할 때 확장 검토 |
| 선택 날짜 필터 적용 중 | 선택된 하루가 아니라 현재 주간 전체 요약을 공유한다는 문구를 명확히 한다 |

### 권장 CTA 카피

- 버튼/텍스트: `이번 주 집중 기록 공유`
- 접근성 설명: `이번 주 집중 요약을 다른 앱으로 공유`
- share sheet title: `스탑잇 집중 기록 공유`

### 공유문 locale/template 계약

아래 Korean 문구는 #211 당시 기본 예시이며, #597 이후 runtime canonical source가 아니다. 구현은 Android string/plural resource 또는 resource-backed text provider에서 locale별 template을 읽고 다음 placeholder만 채워야 한다.

필수 placeholder:

- `{sessionCount}` 또는 Android format `%1$d`
- `{durationText}` 또는 Android format `%2$s`
- `{playStoreUrl}` 또는 Android format `%3$s`

공유 payload builder는 앱 이름, package, topApps, raw session list, raw timestamp를 template 입력으로 받지 않는다. Duration text도 Kotlin hardcoded `시간`/`분` 조합이 아니라 locale-aware resource/plural contract를 따른다.

### 기본 공유문 예시

```text
이번 주 스탑잇으로 {sessionCount}번, 총 {durationText} 집중을 지켰어요.
나도 집중이 필요할 때 앱 사용을 잠깐 멈춰요.
{playStoreUrl}
```

예시:

```text
이번 주 스탑잇으로 3번, 총 2시간 10분 집중을 지켰어요.
나도 집중이 필요할 때 앱 사용을 잠깐 멈춰요.
https://play.google.com/store/apps/details?id=com.uiery.keep
```

### 카피 금지어/금지 패턴

- `중독`, `실패`, `못 참음`, `낭비`, `감시`처럼 수치심을 유발하는 표현.
- `{topApps}` 또는 package name을 기본 공유문에 넣는 패턴.
- “친구보다 더”, “랭킹”, “챌린지”처럼 비교를 유도하는 표현.
- 광고/리뷰 프롬프트와 같은 화면에서 공유를 압박하는 패턴.

## Privacy / trust guardrail

| 항목 | 허용 | 금지 |
| --- | --- | --- |
| 기간 | 주간 단위 집계 | 세션별 timestamp 공유 |
| 성과 | 세션 수 bucket, 총 시간 bucket 또는 표시용 총 시간 | raw session list |
| 앱 정보 | 없음 | 앱 이름, package name, 카테고리, topApps |
| 문구 | 긍정적 자기 선언 | 실패/중독/비교/랭킹 |
| 처리 위치 | 로컬 formatter + Android share sheet | 서버 업로드/공개 피드 |

구현 PR은 formatter 테스트에서 다음을 명시적으로 검증해야 한다.

- 공유문에 `lockedApps`, package name, raw 날짜/시간이 포함되지 않는다.
- `sessionCount`와 `durationText`가 0이 아닌 주간 기록에서만 생성된다.
- Play Store 링크가 포함된다.

## Analytics 계약 초안

구현 PR에서 `KeepAnalytics.kt`, Firebase 구현, 테스트, `docs/ANALYTICS_EVENT_DICTIONARY.md`를 함께 업데이트한다.

| 이벤트 | 트리거 | 파라미터 | 민감 정보 정책 |
| --- | --- | --- | --- |
| `focus_summary_share_tapped` | 사용자가 공유 CTA를 누름 | `period_type`, `session_count_bucket`, `duration_minutes_bucket` | 앱 이름/package/raw duration 금지 |
| `focus_summary_share_sheet_opened` | share sheet intent launch를 시도/성공 | `period_type`, `session_count_bucket`, `duration_minutes_bucket` | 앱 이름/package/raw duration 금지 |
| `focus_summary_share_failed` | ActivityNotFoundException 등으로 공유 실패 | `period_type`, `reason` | reason은 enum/bucket만 허용 |

권장 bucket:

- `session_count_bucket`: `1`, `2_3`, `4_6`, `7_plus`
- `duration_minutes_bucket`: `1_29`, `30_59`, `60_119`, `120_239`, `240_plus`

GA4 custom dimension 등록은 구현 완료 후 별도 수동/운영 단계가 필요하다. 구현 PR이 event dictionary를 갱신하더라도, GA4 Admin 등록과 실제 queryability 확인 전까지는 지표 결론을 낮은 confidence로 둔다.

## 측정 계획

### 14일 확인

- 기간: 배포 후 14일 vs 배포 전 동기간.
- 확인 지표:
  - `focus_summary_share_tapped` users / weekly LockHistory users
  - `focus_summary_share_sheet_opened` users / `focus_summary_share_tapped` users
  - crash-free users
  - Play Store Organic Search new users
- 판단:
  - 공유 CTA tap이 거의 없으면 CTA 위치/카피를 먼저 점검한다.
  - share sheet open 실패가 보이면 intent fallback/대상 앱 없음 처리를 우선한다.
  - crash-free users 또는 리뷰가 악화되면 실험을 중단하거나 CTA를 숨긴다.

### 30일 확인

- 기간: 배포 후 30일 vs 배포 전 30일.
- 확인 지표:
  - 공유 이벤트 unique users와 repeat share 비율
  - 공유 사용자의 D7 repeat block/session 여부
  - Organic Search new users와 store listing conversion 변화
  - 리뷰 평점/부정 리뷰 키워드
- 판단:
  - Organic Search 신호가 없더라도 공유 사용자 retention이 좋아지면 “성장”보다 “긍정 경험 강화” 기능으로 재분류할 수 있다.
  - retention/리뷰 guardrail이 나쁘면 공유 루프 확대를 중단한다.

## QA / 테스트 체크리스트

### 단위 테스트

- 공유 payload formatter:
  - 세션 수/총 시간/Play 링크 포함.
  - 앱 이름/package/raw session 미포함.
  - duration 0 또는 session 0이면 payload 생성 불가 또는 CTA 숨김 상태.
- analytics bucket mapper:
  - session count bucket 경계값.
  - duration minutes bucket 경계값.
- ViewModel/state:
  - 주간 기록 있음 → CTA state 활성.
  - 주간 기록 없음 → CTA state 비활성.
  - 월간 탭 → MVP CTA 비활성.

### Android/수동 QA

- 공유 가능한 앱이 있을 때 share sheet가 열린다.
- 공유 대상 앱이 없거나 intent launch가 실패해도 앱이 크래시하지 않는다.
- TalkBack/접근성 라벨이 의미를 전달한다.
- 날짜 필터 선택 중에도 공유문이 “이번 주” 요약임을 혼동시키지 않는다.

## #597 localization/resource runtime contract

현재 구현 표면:

- `FocusSummarySharePayload.kt`: `FocusSummaryShareTextProvider`를 통해 공유 본문 생성을 주입받고, Android runtime 구현은 `AndroidFocusSummaryShareTextProvider`가 담당한다.
- `AndroidFocusSummaryShareTextProvider`: `focus_summary_share_payload_text`, `focus_summary_share_duration_hours_minutes`, `focus_summary_share_duration_hour`, `focus_summary_share_duration_minute` resource/plural key를 사용한다.
- `FocusSummarySharePayloadTest.kt`: Korean literal을 canonical expectation으로 고정하지 않고 provider injection request, privacy guardrail, analytics bucket을 검증한다.
- `strings.xml` shipped locale files는 payload body template/duration placeholder key parity를 유지한다.

#597 code-lane 완료 조건:

1. 공유 본문 template을 Android resources 또는 resource-backed provider로 이동한다. ✅
2. session count, duration text, Play Store URL placeholder 순서를 문서화하고 모든 shipped locale에서 key parity를 유지한다. ✅
3. duration/session grammar는 locale-aware string/plural contract를 사용한다. ✅
4. formatter/helper tests는 Korean literal을 유일한 canonical expectation으로 고정하지 않고, resource/provider injection으로 locale별 rendering과 privacy guardrail을 검증한다. ✅
5. `focus_summary_share_*` analytics event name, bucket, privacy policy는 변경하지 않는다. ✅

`Closes #597`는 runtime payload resource/template 전환, shipped locale parity, formatter/privacy tests, static docs contract가 모두 통과한 code-lane PR에서 사용한다.

## 구현 패키지 추천 범위

이 문서 PR은 discovery/contract 산출물이다. 구현 착수 시에는 #211을 바로 닫기보다 아래 패키지를 한 PR에서 끝까지 처리한다.

1. `FocusSummarySharePayload` 같은 작은 formatter/helper 추가.
2. helper 단위 테스트로 privacy guardrail RED/GREEN.
3. `LockHistoryViewModel` 또는 UI state에 공유 가능 여부/요약 payload 연결.
4. `LockHistoryScreen`에 주간 CTA와 share sheet side effect 추가.
5. `KeepAnalytics` 이벤트/구현/테스트 추가.
6. `docs/ANALYTICS_EVENT_DICTIONARY.md` 이벤트 반영.
7. `docs/QA_RUNTIME_CHECKLIST.md` 또는 관련 QA 문서에 수동 share sheet 확인 추가.

`Closes #211`는 위 구현+테스트+event dictionary+QA 문서까지 완료했을 때만 사용한다. 이 문서-only PR은 `Refs #211`가 맞다. #211 repo-internal MVP 구현 이후 #597 같은 localization/resource debt를 다루는 문서/ops/static-contract PR은 `Refs #597`를 사용하고, runtime resource/template 전환이 끝난 code-lane PR에서만 `Closes #597`를 사용한다.

## 외부/manual 경계

- GA4 Admin custom dimension 등록은 GitHub PR만으로 완료되지 않는다.
- Play Store Organic Search 영향은 배포 후 14일/30일이 지나야 판단 가능하다.
- 실제 공유 후 설치 attribution은 현재 backend/deep link 없이 강하게 연결하기 어렵다. MVP에서는 Organic Search, store listing conversion, 이벤트 추세를 낮은 confidence로 함께 본다.

## 중복/연계 이슈

- #119: Usage Access 기반 개인화 리포트 discovery. 민감한 앱 사용 데이터를 다루므로 이 공유 MVP와 합치지 않는다.
- #160: canonical LockHistory 표면 정리. 공유 CTA 구현 위치는 canonical LockHistory 표면을 따른다.
- #13: GA4 queryability / custom dimension 등록 경계. 공유 이벤트 추가 후에도 GA4 Admin 등록과 metadata 확인이 필요하다.
