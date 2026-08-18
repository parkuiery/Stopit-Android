# GA4 + Amplitude 교차 지표 분석 런북

스탑잇은 GA4(Firebase)와 Amplitude 두 도구로 계측한다. 이 문서는 **두 도구의 실데이터를
실제로 읽어서** 하나의 판독으로 합치는 절차를 고정한다.

- 실행 도구: `scripts/metrics_read.py`
- 이벤트 계약: `docs/ANALYTICS_EVENT_DICTIONARY.md`
- Amplitude allowlist/예산 계약: `docs/analytics/AMPLITUDE_EVENT_SCHEMA.md`
- 분석 절차/이슈화: `docs/METRICS_ANALYSIS.md`

> **원칙 1 — 항상 직접 실측한다.** 이 문서와 다른 문서에 적힌 과거 수치는 근거가 아니다.
> 분석할 때마다 아래 명령으로 새로 조회한다. 이 런북은 *측정 방법*만 소유하고 *수치*는
> 소유하지 않는다.
>
> **원칙 2 — 두 도구는 같은 숫자를 주지 않는다. 주면 오히려 이상하다.**
> 차이의 크기가 아니라 *차이가 설명되는지*가 판독 기준이다.

---

## 1. 데이터 소스와 접근 경로

| | GA4 (Firebase) | Amplitude |
|---|---|---|
| 수집 범위 | 전체 이벤트 카탈로그 | **allowlist 23종만** |
| 대상 빌드 | 전 flavor / 전 버전 | **prod flavor + v1.7.9 이상** |
| 상한 | 없음 | **기기당 월 180건** 초과분 무음 drop |
| 세션/화면 | 있음 | **없음** (autocapture 전면 OFF) |
| 읽는 법 | Analytics Data API (서비스 계정) | **Amplitude MCP** (OAuth) 또는 Dashboard REST API |
| property/project | `properties/502544175` (TZ `Etc/GMT-9`) | prod 단일 프로젝트 |

### GA4 자격증명

서비스 계정 `analytics-bot@stopit-be785.iam.gserviceaccount.com`.

키는 `~/.secrets/stopit-ga4-analytics.json`에 둔다 (Play 서비스 계정 키와 같은 위치 규약).
스크립트가 기본으로 이 경로를 찾으므로 별도 설정 없이 실행된다. 다른 경로를 쓰려면:

```bash
export STOPIT_GA4_CREDENTIALS=/path/to/analytics-service-account.json
```

키 파일 내용은 절대 커밋하거나 출력하지 않는다.

### Amplitude 접근: MCP를 기본으로 쓴다

공식 원격 MCP 서버가 있고 **OAuth라서 Secret Key가 필요 없다**. 등록되어 있다:

```bash
claude mcp add --transport http amplitude https://mcp.amplitude.com/mcp   # 등록 완료
# 최초 1회 인증: Claude Code에서 /mcp → amplitude → Authenticate
```

MCP는 event segmentation / funnel / retention을 자연어로 조회한다. 탐색적 분석에 적합하다.

REST API 레인(`AMPLITUDE_ANALYTICS_API_KEY` + `AMPLITUDE_ANALYTICS_SECRET_KEY`)은
**OAuth 세션이 없는 무인 실행(cron/CI)** 용으로만 남겨둔다. `local.properties`의
`AMPLITUDE_API_KEY`는 **전송 전용 클라이언트 키라서 조회에 쓸 수 없다.**

---

## 2. 실행

```bash
# GA4 단독 (Amplitude 미인증 상태에서도 완전히 동작)
python3 scripts/metrics_read.py --days 30

# MCP로 뽑은 Amplitude 수치를 합쳐서 교차 판독
python3 scripts/metrics_read.py --days 30 --amplitude-json amp.json

# 기록 보관
python3 scripts/metrics_read.py --days 30 --json-out .omc/artifacts/metrics-$(date +%F).json
```

### ⚠️ MCP 조회 시 `interval`은 반드시 `1`로 둔다

`eventsSegmentation`에 `interval: 30`을 주면 Amplitude가 **월 경계로 스냅**해서 요청한
`start`/`end`보다 넓은 구간을 반환한다. 그대로 capture rate를 내면 분자가 부풀어 1.0을
넘는다. 항상 `interval: 1`로 일 단위 시계열을 받아 **직접 합산**하고, 반환된 날짜 점의
개수와 첫/끝 날짜가 요청한 창과 일치하는지 확인한다.

### ⚠️ 두 도구의 날짜 경계가 9시간 다르다

GA4 property는 `Etc/GMT-9`(KST), Amplitude 프로젝트는 timezone 미설정(UTC)이다. 같은 날짜
문자열로 조회해도 실제로 담기는 구간이 9시간 어긋나므로, 창 양끝의 이벤트가 서로 다른 날에
배정된다. **capture rate가 1.0에서 몇 % 벗어나는 것은 이 경계 차이로 설명되는 정상 범위다.**
일 단위 시계열을 직접 겹쳐 보는 비교는 하지 않는다.

`--amplitude-json`은 MCP 조회 결과를 그대로 옮긴 평면 맵이다. **조회한 이벤트만** 넣으면 된다.
넣지 않은 이벤트는 `0`이 아니라 `n/a`로 렌더링된다.

```json
{"events": {"lock_session_start": {"totals": 1900, "uniques": 402},
            "first_core_action_completed": {"totals": 330, "uniques": 325}}}
```

---

## 3. 판독 순서

### 3-1. Amplitude 판독 가능 범위부터 잰다 (섹션 2)

`v1.7.9+ active user share`가 Amplitude coverage의 **천장**이다.
이 값이 낮으면 Amplitude가 적게 보이는 것은 유실이 아니라 버전 믹스다.

### 3-2. 절대 수치와 외부 축은 GA4로 본다 (섹션 1·5)

신규 유입, 획득 채널, 화면 품질, 광고, 버전 분포는 **GA4만** 답할 수 있다.
Amplitude에는 세션도 화면도 없다(autocapture OFF).

### 3-3. 사용자 흐름은 Amplitude로 본다

퍼널 단계별 전환, n-day 리텐션, 코호트 비교는 Amplitude MCP가 강하다.
GA4의 `totalUsers`는 이벤트별 고유 사용자일 뿐 **순서를 보장하는 퍼널이 아니다**
(섹션 3에서 `app_block_intercepted` users가 `first_core_action_completed`보다 큰 이유).

### 3-4. capture rate로 계측 건강을 본다 (섹션 4)

```
capture = Amplitude totals / GA4 eventCount(v1.7.9+ 버전만 합산)
```

GA4 쪽을 **같은 모집단으로 잘라서** 비교한다. 해석:

| capture | 해석 |
|---|---|
| 0.95~1.05 | 정상. 1.0을 살짝 넘는 것도 정상이다 — 위의 9시간 경계 차이와 GA4의 `appVersion` 미파싱 행 제외 때문이다 |
| 0.6~0.9 | 대체로 기기당 월 180건 캡. **고빈도 이벤트일수록 낮아지는 것이 설계대로 동작하는 증거다** |
| < 0.5 | 캡만으로 설명 안 됨. allowlist 누락 / prod 미배포 / SDK 초기화 실패 순으로 확인 |
| `n/a` | 조회하지 않음. **0이 아니다** |

capture가 이벤트 빈도와 **역상관**인지 먼저 본다. 저빈도 이벤트는 ~1.0, 고빈도 이벤트만
낮게 나오면 캡이 의도대로 작동하는 것이다. 저빈도 이벤트가 낮으면 그때가 진짜 문제다.

### 3-5. 양쪽 모두 0이면 파이프라인이 아니라 이벤트를 의심한다

한쪽만 비면 수집 경로 문제지만, **GA4와 Amplitude가 동시에 0이면 이벤트가 아예 발생하지
않는 것**이다. 이때는 계측 배선(호출부 존재 여부)과 기능 노출 조건을 코드에서 확인한다.
교차 판독의 가장 값싼 소득이 이 구분이다.

> `eventCount`만 버전 합산이 가능하다. `totalUsers`는 GA4가 행 단위로 중복 제거하므로
> 버전별로 더하면 과대 집계된다. 그래서 사용자 수는 전체값만 표기한다.

---

## 4. 금지 사항

- **두 도구 수치를 더하지 않는다.** 공유 user id가 없다(GA4 app instance id vs Amplitude
  device id, 앱이 `setUserId`를 호출하지 않음). 사용자 단위 조인은 불가능하다.
- **Amplitude 수치를 volume 근거로 쓰지 않는다.** 캡은 헤비 유저부터 자르므로 항상 하한이다.
- **Amplitude에서 세션/화면 지표를 찾지 않는다.** 구조적으로 없다.
- **미배포 기능의 0건을 수요 없음으로 읽지 않는다.** release/tag/Play deploy 경계를 먼저 확인한다
  (`docs/VERSION_ADOPTION_METRICS_GATE.md`).
- 원문 PII(앱 package/이름, custom reason, raw timestamp)는 어느 도구에도 보내지 않는다.

---

## 5. 수치는 이 문서에 적지 않는다

**이 문서에는 지표 수치를 기록하지 않는다.** 데이터는 계속 바뀌므로 문서에 박힌 숫자는
반드시 낡고, 낡은 숫자는 재조회를 건너뛰게 만든다. 분석할 때마다 위 명령으로 **직접 실측**한다.

- 수치가 필요하면 `--json-out`으로 스냅샷을 남긴다 (`.omc/artifacts/`, 커밋 대상 아님).
- 기간 비교가 필요하면 이전 스냅샷 JSON과 비교하지, 문서를 근거로 삼지 않는다.
- 특정 이슈의 전후 비교 기준선은 그 이슈 문서(`docs/<ISSUE>.md`)에 기간/분자/분모와 함께
  기록한다. 이 런북은 **측정 방법**만 소유한다.

기록할 때는 항상 분자 / 분모 / 기간 / 조회 시각을 함께 적는다.
포맷: `<이벤트> <분자>명 / <분모 이벤트> <분모>명 = <비율> (<기간>, 조회 <날짜>)`.
비율만 적힌 수치는 재현할 수 없으므로 판독 근거로 쓰지 않는다.

---

## 6. 알려진 계측 caveat (수치 아님 — 구조적 사실)

판독 전에 확인해야 하는 코드 레벨 사실이다. 코드가 바뀌면 이 절도 갱신한다.

### `emergency_unlock_completed`는 이제 실제 종료 시점이다 (#1167)

이전에는 `EmergencyUnlockCoordinator`가 `emergency_unlock_used`와 `emergency_unlock_completed`를
해제 **승인 시점**에 조건 없이 연달아 호출해 두 이벤트가 항상 동일한 수치였다. 완료율 지표가
승인율을 재고 있었고, allowlist 23종 중 2종이 같은 신호라 기기당 월 캡도 이중 소모했다.

현재는 승인 시점에 payload만 예약하고(`EmergencyUnlockCompletionCoordinator`), 해제 창
teardown 경로에서 1회 배달한다. **`completed / used`가 완료율**이 됐다.

판독 시 주의:
- 수정 포함 버전의 release/tag/Play deploy **이전 데이터는 승인율**이다. 전후 기간을 분리한다.
- 프로세스가 죽은 채 창이 만료되면 서비스 재시작 시점에 배달된다. 이벤트 시각과 실제 종료
  시각이 벌어질 수 있다(지연 기록 허용).
- 복원된 기기에서는 예약이 리셋되므로, 열린 적 없는 창의 완료는 보고되지 않는다.
- `scripts/tests/test_emergency_unlock_completed_contract.py`가 승인 시점 로깅 복귀와
  teardown 경로 누락을 막는다.

### `*_shown` 이벤트는 "화면에 보였다"를 뜻하지 않을 수 있다

`shown` 계열이 **렌더링 시점이 아니라 ViewModel 상태 계산 시점**에 발생하도록 배선돼
있으면, UI가 실제로 그려지지 않아도 노출이 계속 기록된다. 실측으로 확인된 사례가
`routine_creation_cta_shown`이다(§ 아래).

판별법: `shown`은 많은데 짝이 되는 `clicked` / `dismissed`가 **둘 다 0**이면 사용자가
무시한 것이 아니라 배선을 의심한다. 실제 노출이 있으면 dismiss는 반드시 얼마간 발생한다.
그 다음 호출부를 코드에서 확인한다 — 컴포저블과 핸들러에 **production 호출부가 있는지**,
테스트에서만 호출되는지 본다.

### GA4의 이벤트별 users는 순서 퍼널이 아니다

`app_block_intercepted` users가 `first_core_action_completed`보다 큰 경우가 정상적으로 발생한다.
기존 사용자의 반복 차단이 섞이기 때문이다. **순서 있는 활성화 전환은 Amplitude 퍼널로만
확인한다.** GA4 단독 판독의 가장 큰 공백이 이 지점이다.

### `routine_creation_cta_shown`은 현재 아무것도 발생시키지 않는다 (#1166 / #463)

`HomeStatusCtaCard`는 PR #500이 넣고 PR #1099 `fix(home): restore v1.7.7 home UI`가
호출부만 지운 뒤 테스트에서만 렌더되고 있었다. 그동안 노출 계측은 상태 계산 경로에 남아
**보이지 않는 카드의 노출을 집계했다.**

두 단계로 정리됐다.

1. 노출 보고를 렌더 시점으로 옮겼다(#1166). 카드가 그려지지 않으면 이벤트도 없다.
2. 죽은 카드와 read model을 삭제했다(#463 superseded). 지금은 emission 자체가 없다.

**따라서 이 이벤트가 0인 것이 정확한 값이다.** 수요 부재로 읽지 않는다. 홈은 이제
`HomeCardArbiter`가 카드 한 장만 고르는 구조이고, 루틴 생성 넛지는 #455에서
`HomeCard` variant로 다시 만든다.

`scripts/tests/test_routine_creation_cta_shown_contract.py`는 emission이 없어도 통과하고,
누군가 상태 계산 경로에서 다시 로깅하면 실패한다. #455 재구현 시에도 같은 규칙이 걸린다.

### 화면 품질과 custom dimension 등록

`(not set)`/blank 비중이 크거나 `customEvent:*` 조회가 `400 INVALID_ARGUMENT`로 실패하면
제품 결론보다 계측을 먼저 고친다. `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` 참조.

---

## 7. 분석 체크리스트

- [ ] `/mcp`로 amplitude 인증 (최초 1회)
- [ ] `python3 scripts/metrics_read.py --days 30 --json-out .omc/artifacts/metrics-<날짜>.json`
- [ ] 섹션 2로 Amplitude 판독 가능 범위(v1.7.9+ share) 먼저 확인
- [ ] Amplitude MCP로 순서 있는 활성화 퍼널과 D1/D7/D30 리텐션 조회
- [ ] 퍼널/이벤트 수치를 `--amplitude-json`으로 합쳐 capture rate 확인
- [ ] capture < 0.5인 이벤트가 있으면 allowlist → 배포 → SDK 순으로 원인 분리
- [ ] 섹션 6의 caveat이 아직 유효한지 코드로 재확인
- [ ] 실행 단위로 묶어 이슈화 (`docs/METRICS_ANALYSIS.md`의 템플릿)
