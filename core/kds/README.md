# KDS (Keep Design System)

Keep 앱의 공통 UI 컴포넌트와 테마 시스템.

## 테마

### 사용법

```kotlin
KeepTheme {
    // KeepTheme.colors로 색상 접근
    Text(color = KeepTheme.colors.primary)
}
```

시스템 다크 모드를 자동 감지합니다.

### 색상 시스템

| 토큰 | Light | Dark | 용도 |
|------|-------|------|------|
| `primary` | `#FFA927` | `#FFA927` | 주요 CTA, 선택/활성 상태, 중요한 진행/성과 강조 |
| `error` | `#F04452` | `#F04452` | 에러, 경고 |
| `background` | `#FFFFFF` | `#17171C` | 화면 배경 |
| `onBackground` | `#17171C` | `#8E000000` | 딤 배경 |
| `secondary` | gray50 | gray50 | 보조 배경 (밝음) |
| `onSecondary` | gray100 | gray100 | 카드/시트 배경 |
| `tertiary` | gray200 | gray200 | 구분선 (밝음) |
| `onTertiary` | gray300 | gray300 | 구분선 |
| `tertiaryContainer` | gray400 | gray400 | 비활성 버튼, 드래그 핸들 |
| `onTertiaryContainer` | gray500 | gray500 | 비활성 체크박스 |
| `surface` | gray600 | gray600 | 보조 텍스트 (어두움) |
| `onSurface` | gray700 | gray700 | 보조 텍스트 |
| `surfaceVariant` | gray800 | gray800 | 부제목 텍스트 |
| `onSurfaceVariant` | gray900 | gray900 | 본문 텍스트, 제목 |

### Gray 스케일

| 이름 | Light | Dark |
|------|-------|------|
| gray50 | `#F9FAFB` | `#202027` |
| gray100 | `#F2F4F6` | `#2C2C35` |
| gray200 | `#E5E8EB` | `#3C3C47` |
| gray300 | `#D1D6DB` | `#4D4D59` |
| gray400 | `#B0B8C1` | `#62626D` |
| gray500 | `#8B95A1` | `#7E7E87` |
| gray600 | `#6B7684` | `#9E9EA4` |
| gray700 | `#4E5968` | `#C3C3C6` |
| gray800 | `#333D4B` | `#E4E4E5` |
| gray900 | `#191F28` | `#FFFFFF` |

### Primary color 사용 위계

`primary`는 브랜드 강조색이지만 기본 icon/text 색이 아닙니다. 화면의 모든 action을 같은 orange로 칠하면 primary CTA, 현재 선택 상태, navigation icon의 위계가 무너집니다.

권장 사용:

- 화면/시트의 단일 primary CTA (`KeepButton`, 저장/시작/확인/선택 완료)
- 선택된 tab/day/chip/filter 같은 현재 선택 상태
- 활성 잠금/루틴/집중 상태, 카운트다운, 중요한 진행/성과 강조

낮은 위계 색상으로 처리할 후보:

- TopAppBar 뒤로가기/메뉴/닫기 icon
- 일반 추가/삭제/편집 icon-only action
- 보조 설명, caption, metadata
- 파괴/긴급 동작: `error` 또는 confirmation pattern 사용

선택/활성 상태는 색상만으로 전달하지 말고 텍스트, badge/chip shape, border/background, contentDescription/semantics 중 하나 이상을 함께 사용합니다. 앱 화면별 audit와 후속 체크리스트는 루트 `docs/DESIGN_PRIMARY_COLOR_HIERARCHY.md`를 기준으로 합니다.

### 타이포그래피

KDS는 Pretendard 폰트 패밀리 기반의 Material Typography scale을 정의합니다.

| 스타일 | 크기 | 두께 | 행간 | 자간 |
|--------|------|------|------|------|
| displayLarge | 57sp | Bold | 64sp | -0.25sp |
| displayMedium | 45sp | Bold | 52sp | 0sp |
| displaySmall | 36sp | Bold | 44sp | 0sp |
| headlineLarge | 32sp | Bold | 40sp | 0sp |
| headlineMedium | 28sp | SemiBold | 36sp | 0sp |
| headlineSmall | 24sp | SemiBold | 32sp | 0sp |
| titleLarge | 22sp | SemiBold | 28sp | 0sp |
| titleMedium | 16sp | Medium | 24sp | 0.15sp |
| titleSmall | 14sp | Medium | 20sp | 0.1sp |
| bodyLarge | 16sp | Normal | 24sp | 0.5sp |
| bodyMedium | 14sp | Normal | 20sp | 0.25sp |
| bodySmall | 12sp | Normal | 16sp | 0.4sp |
| labelLarge | 14sp | Medium | 20sp | 0.1sp |
| labelMedium | 12sp | Medium | 16sp | 0.5sp |
| labelSmall | 11sp | Medium | 16sp | 0.5sp |

화면 작업의 상세 사용 규칙은 루트 `DESIGN.md`를 기준으로 합니다.

## 컴포넌트

### KeepButton

Primary 액션 버튼.

```kotlin
KeepButton(
    modifier = Modifier.fillMaxWidth(),
    text = "시작하기",
    enabled = true,
    onClick = { },
)
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `modifier` | Modifier | Modifier | 레이아웃 수정자 |
| `text` | String | (필수) | 버튼 텍스트 |
| `enabled` | Boolean | true | 활성화 상태 |
| `onClick` | () -> Unit | (필수) | 클릭 콜백 |

**스타일:** RoundedCornerShape(12dp), primary 배경, White 텍스트, Bold 18sp, 패딩 18x24dp, 하단 마진 24dp. 비활성 시 tertiaryContainer 배경.

---

### KeepCheckbox

커스텀 색상 체크박스.

```kotlin
KeepCheckbox(
    checked = isChecked,
    onCheckedChange = { isChecked = it },
)
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `checked` | Boolean | (필수) | 체크 상태 |
| `onCheckedChange` | ((Boolean) -> Unit)? | (필수) | 상태 변경 콜백 |
| `modifier` | Modifier | Modifier | 레이아웃 수정자 |
| `enabled` | Boolean | true | 활성화 상태 |
| `colors` | CheckboxColors | Blue/White/gray500 | 커스텀 색상 |
| `interactionSource` | MutableInteractionSource | 새 인스턴스 | 인터랙션 소스 |

**기본 색상:** 체크 시 Blue, 체크마크 White, 미체크 시 onTertiaryContainer(gray500).

---

### KeepSwitch

cross-feature 토글/설정 화면에서 쓰는 KDS 스위치.

```kotlin
KeepSwitch(
    checked = enabled,
    onCheckedChange = { enabled = it },
)
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `checked` | Boolean | (필수) | 선택 상태 |
| `onCheckedChange` | ((Boolean) -> Unit)? | (필수) | 상태 변경 콜백 |
| `modifier` | Modifier | Modifier | 레이아웃 수정자 |
| `thumbContent` | @Composable (() -> Unit)? | null | thumb 내부 콘텐츠 |
| `enabled` | Boolean | true | 활성화 상태 |
| `colors` | SwitchColors | KDS primary/onTertiary 색상 | 스위치 색상 |
| `interactionSource` | MutableInteractionSource | 새 인스턴스 | 인터랙션 소스 |

**소유권:** Home, Menu, Routine, Emergency Unlock Settings처럼 여러 feature에서 공유하는 switch는 home feature-private component가 아니라 KDS `com.uiery.kds.KeepSwitch`를 사용합니다.

---

### KeepSnackBar

앱 전용 스낵바.

```kotlin
SnackbarHost(hostState = snackBarHostState) {
    KeepSnackBar(snackbarData = it)
}
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `modifier` | Modifier | Modifier | 레이아웃 수정자 |
| `snackbarData` | SnackbarData | (필수) | Material3 스낵바 데이터 |

**스타일:** CircleShape, onSecondary(gray100) 배경, onSurfaceVariant(gray900) 텍스트, Bold, 패딩 12x24dp.

---

### KeepModalBottomSheet

시스템 UI 색상을 자동 관리하는 바텀시트.

```kotlin
KeepModalBottomSheet(
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest = { },
) {
    // 시트 내용
}
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `onDismissRequest` | () -> Unit | (필수) | 닫기 콜백 |
| `modifier` | Modifier | Modifier | 레이아웃 수정자 |
| `sheetState` | SheetState | rememberModalBottomSheetState() | 시트 상태 |
| `sheetMaxWidth` | Dp | BottomSheetDefaults.SheetMaxWidth | 최대 너비 |
| `shape` | Shape | BottomSheetDefaults.ExpandedShape | 테두리 모양 |
| `containerColor` | Color | onSecondary(gray100) | 배경색 |
| `contentColor` | Color | contentColorFor(container) | 내용 색상 |
| `tonalElevation` | Dp | BottomSheetDefaults.Elevation | 높이 |
| `scrimColor` | Color | BottomSheetDefaults.ScrimColor | 스크림 색상 |
| `dragHandle` | @Composable (() -> Unit)? | tertiaryContainer 드래그 핸들 | 핸들 컴포저블 |
| `windowInsets` | WindowInsets | BottomSheetDefaults.windowInsets | 시스템 인셋 |
| `properties` | ModalBottomSheetProperties | 기본값 | 추가 속성 |
| `content` | @Composable ColumnScope.() -> Unit | (필수) | 내용 |

**특징:** 시트 표시 시 상태바를 스크림 색상으로, 내비게이션 바를 onSecondary로 자동 변경. 숨김 시 배경색으로 복원.

---

### KeepBannerAd

Google AdMob 적응형 배너 광고.

```kotlin
KeepBannerAd(
    adUnitId = "ca-app-pub-xxx/yyy",
)
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `modifier` | Modifier | Modifier | 레이아웃 수정자 |
| `adUnitId` | String | (필수) | AdMob 단위 ID |

**특징:** 화면 너비에 맞는 적응형 배너 크기. 프리뷰 모드에서 로딩 생략. 라이프사이클 자동 관리 (resume/pause). `INTERNET` 권한 필요.

---

### RotatingCircleGradient

무한 회전하는 원형 그라데이션 애니메이션.

```kotlin
RotatingCircleGradient(
    size = 200.dp,
    color = KeepTheme.colors.primary,
)
```

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `modifier` | Modifier | Modifier | 레이아웃 수정자 |
| `strokeWidth` | Dp | 20.dp | 선 두께 |
| `size` | Dp | 200.dp | 원 지름 |
| `color` | Color | primary(orange400) | 그라데이션 시작 색상 |

**애니메이션:** 0°→360° 무한 회전, 1400ms 주기, Linear 이징, 180° 호, Round 캡. 그라데이션은 color → 투명.
