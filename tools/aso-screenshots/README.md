# StopIt — Play Store ASO Screenshots

`docs/PLAY_STORE_ASO.md` 의 6 슬라이드 구성을 1080×1920 PNG로 렌더링하는 Next.js 생성기다.
디자인 토큰은 `core/kds/src/main/java/com/uiery/kds/theme/Color.kt` 의 Light 팔레트와 동기화되어 있다.

## 슬롯 구성

| # | 화면        | 파일명               | 카피                          | 배경  |
| - | ----------- | -------------------- | ----------------------------- | ----- |
| 1 | 앱 선택     | `01-select.png`      | 유혹 앱만 / 골라서 차단        | Light |
| 2 | 타이머 잠금 | `02-timer.png`       | 지금 바로 / 타이머 잠금        | Light |
| 3 | 루틴        | `03-routine.png`     | 요일·시간대로 / 자동 차단      | Light |
| 4 | 차단 화면   | `04-block.png`       | 앱을 열어도 / 바로 차단        | Dark  |
| 5 | 긴급 해제   | `05-emergency.png`   | 꼭 필요할 때만 / 긴급 해제     | Light |
| 6 | 잠금 기록   | `06-history.png`     | 버틴 시간이 / 기록으로 남아요  | Light |

## 실행

```bash
cd tools/aso-screenshots
bun install --frozen-lockfile
bun dev    # http://localhost:3000
```

각 카드 우측의 `PNG` 또는 상단의 `전체 PNG 내보내기` 로 export.
파일명은 자동으로 `01-select-1080x1920.png` 형태로 prefix.

## 빌드 검증 / CI 계약

`tools/aso-screenshots/**` 아래 템플릿, 캡처 이미지, `package.json`, `bun.lock`, Next 설정을 바꾸는 PR은 Android 앱 빌드와 분리된 `ASO screenshots build` gate를 통과해야 한다. 이 gate는 Play Console 업로드나 Android Gradle 작업을 하지 않고, lockfile 기반 설치와 Next production build만 확인한다.

```bash
cd tools/aso-screenshots
bun install --frozen-lockfile
bun run build
```

로컬에서 export UI만 확인한 경우에도 PR 본문에는 위 build 검증 결과를 별도로 남긴다. `ASO screenshots build` 실패는 앱 APK/AAB 문제가 아니라 스크린샷 생성기 문제로 진단한다.

## 1. 앱 캡처 흐름

`public/screenshots/` 에 위 파일명 그대로 떨구면 즉시 반영된다. 비어 있으면 placeholder 카드가 노출된다.

### 1.1 빌드 & 설치 (prodDebug)

```bash
# 리포 루트에서
./gradlew :app:installProdDebug
adb shell am start -n com.uiery.keep/.MainActivity
```

### 1.2 6개 화면 진입 시나리오

| # | 진입 경로                                                                        | 캡처 시점                                |
| - | -------------------------------------------------------------------------------- | ---------------------------------------- |
| 1 | 온보딩 → `select`                                                                 | 차단할 앱 체크 1~2개 선택 후 캡처        |
| 2 | 홈                                                                               | 타이머 다이얼 노출, 핵심 카테고리 보임   |
| 3 | 메뉴 → 루틴 → 루틴 1~2개 등록 후 목록                                            | 요일 칩 + 시간 범위가 보이는 상태        |
| 4 | 차단 중인 앱 실행 (예: 차단 등록한 SNS 앱 열기)                                  | 차단 오버레이가 떠 있는 순간             |
| 5 | 차단 오버레이의 `긴급 해제` → 시간 선택                                          | 카운트다운/시간 선택 화면                |
| 6 | 메뉴 → 기록                                                                      | 일별 그래프 + 누적 시간 카드             |

### 1.3 캡처 명령

```bash
# 디바이스에서 PNG로 캡처해 바로 public/screenshots/ 에 저장
# (각 라인 사이에 화면을 다음 시나리오로 이동시키며 실행)
adb exec-out screencap -p > tools/aso-screenshots/public/screenshots/01-select.png
adb exec-out screencap -p > tools/aso-screenshots/public/screenshots/02-timer.png
adb exec-out screencap -p > tools/aso-screenshots/public/screenshots/03-routine.png
adb exec-out screencap -p > tools/aso-screenshots/public/screenshots/04-block.png
adb exec-out screencap -p > tools/aso-screenshots/public/screenshots/05-emergency.png
adb exec-out screencap -p > tools/aso-screenshots/public/screenshots/06-history.png
```

`screencap -p` 는 디바이스 해상도의 PNG를 stdout으로 흘려 보낸다.

### 1.4 권장 디바이스 해상도

- 9:19.5 화면비 (대부분의 최신 안드로이드) 권장. 1080×2340 / 1440×3120 등.
- 캔버스(1080×1920)에서 자동으로 `object-fit: cover, object-position: top` 으로 잘리므로 상단 컨텐츠가 잘 보이도록 캡처할 것.
- 캡처 전 노티/배터리 정리: `adb shell settings put global sysui_demo_allowed 1` 후 데모 모드.

## 2. 최종 export

1. 6개 PNG를 `public/screenshots/` 에 채운다.
2. `bun dev` 페이지에서 6장 미리보기 확인 (카피 가독성, 폰 위치, 액센트 컬러).
3. `전체 PNG 내보내기` 클릭 → 6장 1080×1920 PNG 다운로드.
4. `docs/aso-screenshots/` 로 옮겨 커밋. Play Console 업로드 자산은 거기를 source of truth로 본다.

## 3. 디자인 토큰 동기화

| 토큰      | 출처                                | 값         |
| --------- | ----------------------------------- | ---------- |
| accent    | `KeepColor.Light.orange400`         | `#FFA927`  |
| danger    | `KeepColor.Light.red500`            | `#F04452`  |
| text      | `KeepColor.Light.gray900`           | `#191F28`  |
| textMuted | `KeepColor.Light.gray700`           | `#4E5968`  |
| border    | `KeepColor.Light.gray200`           | `#E5E8EB`  |
| bgDark    | `KeepColor.Light.dimmedBackground`  | `#17171C`  |

KDS 토큰이 바뀌면 `src/app/page.tsx` 상단의 `TOKENS` 도 같이 수정한다.

## 4. 폰트

`core/kds/src/main/res/font/pretendard_*.otf` 4종을 `src/app/fonts/` 에 복사해 `next/font/local` 로 로드한다.
KDS에서 폰트 파일을 갈면 한 번 더 복사.
