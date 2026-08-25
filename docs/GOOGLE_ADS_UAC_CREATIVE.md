# Google Ads 앱 캠페인 소재 (수험생·취준생)

이 문서는 Google Ads **앱 캠페인(UAC) / 설치 목적** 소재의 저장소 기준 원본이다. Play Console listing 소재를 다루는 `docs/PLAY_STORE_ASO.md`와는 별개 채널이고, 소재도 별도로 관리한다.

작성 시각: `2026-08-21` · repo 기준: `fix/issue-1177-guardian-pin-persistence`

## 결정 사항

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 캠페인 유형 | 앱 캠페인(UAC), 설치 목적 | 대표님 지정 |
| 타깃 | 수험생 / 취준생 | 대표님 지정. `docs/RETENTION_DIAGNOSIS_2026_08.md`의 루틴 생성 코호트(D7 `10.0%`)가 전체 신규(`7.8%`)보다 높아 궁합도 근거가 있음 |
| 언어 | 한국어 단독 | 국내 타깃. 영문 확장 시 `public/screenshots/en/` 캡처가 이미 있음 |
| 소재 범위 | 텍스트 + 이미지 3규격 | 동영상은 이미지 성과 확인 후 판단 |

## 1. 텍스트 소재

### 글자수 규칙

Google Ads는 한국어·일본어·중국어 등 **전각 문자를 2자로 계산**한다. 따라서 표기상 한도(헤드라인 30자 / 설명 90자)는 한국어에서 **실질 15자 / 45자**다. 아래 표의 `단위`는 전각 2 / 반각 1로 계산한 실제 소비량이다.

검증 명령:

```bash
python3 - <<'PY'
import unicodedata
u=lambda s: sum(2 if unicodedata.east_asian_width(c) in ('W','F') else 1 for c in s)
print(u("공부할 때만 폰이 잠겨요"))
PY
```

### 헤드라인 (한도 30단위, 최대 5개 등록)

| # | 문구 | 단위 | 역할 |
| --- | --- | ---: | --- |
| H1 | 공부할 때만 폰이 잠겨요 | 23 | 핵심 베네핏 |
| H2 | 유혹 앱만 골라서 차단 | 21 | 선택 차단 = 전화·메시지는 유지 |
| H3 | 루틴 한 번이면 자동 차단 | 24 | 반복 사용 유도(리텐션 기여 기능) |
| H4 | 열어도 바로 막힙니다 | 20 | 작동 확신 |
| H5 | 버틴 시간이 남습니다 | 20 | 성취/기록 |

예비안 (교체 A/B용): `수험생을 위한 폰 차단` (21) · `의지 대신 잠금으로` (18)

### 설명 (한도 90단위, 최대 5개 등록)

| # | 문구 | 단위 |
| --- | --- | ---: |
| D1 | 공부 시작할 때 타이머만 누르세요. 정해둔 시간 동안 유튜브도 인스타도 열리지 않습니다. | 85 |
| D2 | 요일과 시간대를 정해두면 매일 그 시간에 알아서 잠깁니다. 매번 켤 필요가 없어요. | 79 |
| D3 | 차단할 앱만 직접 고르세요. 전화와 메시지는 그대로 쓰면서 유혹만 끊습니다. | 73 |
| D4 | 꼭 필요할 땐 긴급 해제가 있습니다. 대신 잠깐 기다려야 하니 습관처럼 풀지 않게 됩니다. | 85 |
| D5 | 오늘 몇 시간을 버텼는지 기록으로 남습니다. 쌓인 시간이 다음 날을 밀어줍니다. | 76 |

### 문구 ↔ 기능 대조

광고 문구는 전부 현재 코드에서 확인한 동작만 주장한다. 기능이 바뀌면 이 표부터 갱신한다.

| 주장 | 코드 근거 |
| --- | --- |
| 타이머로 정해둔 시간 동안 잠금 | `docs/MANUAL_TIMER_LOCK_DEADLINE_CONTRACT.md` |
| 요일·시간대 자동 차단 | `RoutineEntity.repeatDays: List<DayOfWeek>`, `startTime`/`endTime` |
| 차단할 앱만 선택 | 온보딩 `select` 플로우 + 부모 모드 허용목록 분리(#1176) |
| 긴급 해제는 있으나 즉시가 아님 | `EmergencyUnlockPolicy.kt` — 카운트다운 기본 `true` / `30`초, 해제 시간 `3·5·10`분 |
| 버틴 시간이 기록으로 남음 | `LockHistoryEntity.durationMillis`, `lockedApps` |

## 2. 이미지 소재

Google Ads 앱 캠페인이 받는 3규격을 모두 채웠다. 컨셉 3 × 규격 3 = **9장**.

| 컨셉 | 헤드라인 | 씬 | 앱 캡처 |
| --- | --- | --- | --- |
| `focus` | 공부할 때만 / 폰이 잠겨요 | `a-dawn` 새벽 독서실 큐비클 | `02-timer.png` |
| `block` | 열어도 / 바로 막힙니다 | `b-facedown` 엎어둔 폰 | `04-block.png` |
| `record` | 버틴 시간이 / 남습니다 | `c-reward` 다 푼 문제집 더미 | `06-history.png` |

| 규격 | 크기 | 비율 |
| --- | --- | --- |
| 가로 | 1200×628 | 1.91:1 |
| 정사각 | 1200×1200 | 1:1 |
| 세로 | 1200×1500 | 4:5 |

### 2레이어 구조 — 왜 이렇게 나눴나

- **배경 씬**은 Higgsfield로 생성한다. 텍스트도, 앱 화면도 넣지 않는다.
- **앱 화면과 한글 카피**는 `tools/aso-screenshots`가 Pretendard와 KDS 팔레트로 조판한다.

두 가지 이유가 있다.

1. **앱 UI를 AI로 그리면 안 된다.** 없는 기능이 화면에 그려지면 Google Ads 허위 표시 정책과 Play 정책 양쪽에 걸린다. 그래서 화면은 항상 `public/screenshots/`의 실기기 캡처만 쓴다.
2. **한글을 이미지 모델에 맡기면 자소가 깨지고, 카피 A/B마다 재생성해야 한다.** 조판을 분리하면 문구만 바꿔 즉시 다시 뽑을 수 있다.

## 3. 재생성 절차

### 3.1 배경 씬 (Higgsfield)

`nano_banana_pro`, 2k, 컨셉당 `1:1` / `4:5` / `16:9` 3장. 장당 2크레딧.

```bash
higgsfield generate create nano_banana_pro \
  --aspect-ratio 4:5 --resolution 2k --wait --json \
  --prompt "$P_A" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['result_url'])"
```

결과 PNG를 받은 뒤 **JPEG로 변환해서** `tools/aso-screenshots/public/ads/scenes/<slug>-<비율>.jpg`로 저장한다 (`4:5` → `4x5`).

씬은 사진이라 PNG로 두면 장당 6~8MB, 9장에 약 58MB가 저장소에 들어간다. 최종 캔버스가 최대 1200×1500이므로 긴 변 1800px·JPEG q86이면 육안 손실 없이 **약 1.4MB**로 줄어든다. 긴 변 1800은 `object-fit: cover` 기준으로 1200×1500 캔버스가 소비할 수 있는 최대치라, 확대도 화질 부족도 생기지 않는다.

```bash
node -e "
const sharp=require('sharp');
sharp('scene.png').resize(1800,1800,{fit:'inside',withoutEnlargement:true})
  .jpeg({quality:86,mozjpeg:true}).toFile('scene.jpg');
"
```

공통 스타일 접미사 `$STYLE`:

```
Photorealistic editorial photography, modern contemporary South Korea 2020s. Warm cream and
soft amber color grading, natural soft light, shallow depth of field, minimalist composition
with plenty of clean empty copy space. Seamless continuous single exposure, evenly lit, no
banding, no visible seams, no split panels, no collage. Strictly no text, no letters, no
Hangul characters, no numbers, no logos, no watermarks, no screen content, no user interface
elements. Not traditional, no hanok, no antique furniture.
```

컨셉 프롬프트 (각각 뒤에 `$STYLE`을 붙인다):

- `a-dawn` — `A modern private study room cubicle at dawn. A clean pale desk with a slim LED bar lamp, an open exam prep workbook with blurred unreadable pages, a mechanical pencil, and a smartphone lying strictly face-down with its screen flat against the desk so no display is visible. Soft cream partition wall with generous clean empty space beside the desk. Quiet, focused, determined mood.`
- `b-facedown` — `Close-up photograph of a smartphone lying strictly face-down on a pale oak study desk. The screen is pressed flat against the wood and completely hidden; only the smooth back panel and camera bump of the phone are visible from above. Beside it sit a neat stack of closed pastel exam workbooks and a single highlighter. Soft diffused morning window light. Calm, deliberate, a decision just made.`
- `c-reward` — `A bright minimalist study desk bathed in soft morning sunlight. A small white digital study timer with a completely blank dark display stands beside a tall neat stack of finished workbooks and a warm ceramic mug. Pale wooden desktop, soft cream wall behind. A quiet sense of accomplishment after long focused hours.`

**프롬프트 함정 두 가지 (실제로 겪음).**

- `"the entire upper left third of the frame"`처럼 프레임을 분할해 지시하면 모델이 그 경계에 **실제 이음매**를 그려 넣는다. `"generous clean empty copy space"`처럼 분할 없이 표현한다.
- 한국식 공간을 요청하면 기본값이 **한옥·전통 서책·황동 램프**로 튄다. `modern contemporary 2020s` + `no hanok, no antique furniture`를 명시해야 독서실이 나온다.

### 3.2 조판 및 내보내기

```bash
cd tools/aso-screenshots
bun install --frozen-lockfile
bun dev --port 3100          # http://localhost:3100/ads
```

- **브라우저**: `/ads`에서 카드별 `PNG` 또는 상단 `전체 PNG 내보내기`
- **헤드리스**: 별도 셸에서 아래 실행. 결과는 `out/ads/` (gitignore 대상)

```bash
npm i --no-save playwright-core   # 프로젝트 lockfile을 건드리지 않으려고 --no-save
node scripts/capture-ads.mjs
```

`playwright-core`를 의도적으로 `package.json`에 넣지 않았다. `ASO screenshots build` CI 게이트는 `bun install --frozen-lockfile` + `bun run build`만 확인하므로, 캡처 도구를 의존성으로 올리면 게이트 표면만 넓어진다.

### 3.3 레이아웃 계약

`src/app/ads/page.tsx`의 `SIZES`가 규격별 기하를 **명시적으로** 들고 있다. 카피 블록과 기기가 절대 겹치지 않도록 `copy.top`과 `phone.top`을 캔버스 높이 비율로 직접 지정한 것이고, 값을 바꿀 때는 세로 충돌을 다시 확인해야 한다.

`PHONE_ASPECT`는 공용 `Phone` 프레임의 내부 화면 영역(가로 94% × 세로 96.4%)이 캡처 원본 비율 `1080:1920`과 맞도록 역산한 값이다. 이 보정이 없으면 `object-fit: contain`이 화면 위아래에 흰 띠를 남긴다.

## 4. 집행 전 확인

- `docs/RETENTION_DIAGNOSIS_2026_08.md` 기준 D7 잔존 `7.8%`(Amplitude) ~ `15.4%`(GA4), 신규 대비 `app_remove` `71.6%`다. **리텐션 루프가 없는 상태에서 설치 물량을 키우면 CPI 회수가 안 된다.** 이번 캠페인은 소재별 CTR/CVR 비교용 소액 집행으로 시작하고, 예산 확대는 D1–D7 개선 이후로 미루는 것을 권한다.
- 같은 문서의 `Paid Search` 신규 사용자는 30일 창 내내 `0명`이었다. 유료 채널 첫 가동이므로 **`docs/INSTALL_REFERRER_ATTRIBUTION_CONTRACT.md` 기준으로 install referrer가 캠페인을 제대로 물어오는지 첫 주에 반드시 확인**한다. 이게 깨지면 소재 성과를 아예 읽을 수 없다.
- 소재 성과는 헤드라인/설명 단위가 아니라 Google의 자동 조합 결과로 나온다. 컨셉 판정은 이미지 자산 단위 성과로 읽는다.
