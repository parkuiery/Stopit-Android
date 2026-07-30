# KDS (Keep Design System)

Keep 앱의 공통 UI 컴포넌트와 테마 시스템.

구현 전 [KDS documentation](docs/README.md)에서 파운데이션과 해당 컴포넌트 가이드를
읽습니다. 각 가이드는 최신 SEED 원문, StopIt 적용 차이, 접근성 및 검증 항목을 연결합니다.

## 테마

### 사용법

```kotlin
KeepTheme {
    Text(color = KeepTheme.semanticColors.foreground.neutral)
}
```

시스템 다크 모드를 자동 감지하며, Material 3 컴포넌트에도 동일한 KDS 색상 스킴을 제공합니다.

### 색상 시스템

새 KDS 컴포넌트는 값이나 Material 슬롯이 아니라 UI 의도를 표현하는
`KeepTheme.semanticColors`를 사용합니다.

| 속성 | 역할 |
|------|------|
| `foreground.neutral` | 기본 제목, 본문, 아이콘 |
| `foreground.muted` | 보조 텍스트와 낮은 위계 아이콘 |
| `foreground.disabled` | 비활성 콘텐츠 |
| `foreground.onBrand` | 브랜드 배경 위 콘텐츠 |
| `background.layerBasement` | 화면 최하위 배경 |
| `background.layerDefault` | 기본 콘텐츠 표면 |
| `background.layerFloating` | 다이얼로그, 스낵바 등 작은 임시 표면 |
| `background.layerSheet` | 넓은 바텀시트 표면 |
| `background.brandSolid` | 주요 CTA, 선택/활성 상태 |
| `background.brandWeak` | 낮은 강조도의 브랜드 컨테이너 |
| `background.disabled` | 비활성 컨테이너 |
| `stroke.neutralWeak` | 낮은 강조도의 경계 |
| `stroke.brand` | 브랜드 선택/활성 경계 |
| `stroke.critical` | 오류·파괴 동작 경계 |

`KeepTheme.colors`는 기존 화면의 점진적 이전을 위한 호환 API입니다. 새 컴포넌트에서
`secondary`, `onSecondary`, `surfaceVariant` 같은 기존 슬롯으로 의미를 표현하지 않습니다.

### 레거시 색상 호환표

| 토큰 | Light | Dark | 용도 |
|------|-------|------|------|
| `primary` | `#FFA927` | `#FFB84A` | StopIt Brand Solid CTA, 선택/활성 상태 |
| `error` | `#FA342C` | `#F73526` | 에러, 경고 |
| `background` | `#F3F4F5` | `#000000` | SEED layer basement |
| `onBackground` | `#1A1C20` | `#F3F4F5` | 기본 전경 |
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

### Gray 스케일 (SEED)

| 이름 | Light | Dark |
|------|-------|------|
| gray00 | `#FFFFFF` | `#000000` |
| gray100 | `#F7F8F9` | `#16171B` |
| gray200 | `#F3F4F5` | `#1D2025` |
| gray300 | `#EEEFF1` | `#2B2E35` |
| gray400 | `#DCDEE3` | `#393D46` |
| gray500 | `#D1D3D8` | `#5B606A` |
| gray600 | `#B0B3BA` | `#868B94` |
| gray700 | `#868B94` | `#B0B3BA` |
| gray800 | `#555D6D` | `#DCDEE3` |
| gray900 | `#2A3038` | `#E9EAEC` |
| gray1000 | `#1A1C20` | `#F3F4F5` |

### Primary color 사용 위계

`primary`는 브랜드 강조색이지만 기본 icon/text 색이 아닙니다. 화면의 모든 action을 같은 amber로 칠하면 primary CTA, 현재 선택 상태, navigation icon의 위계가 무너집니다.

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

KDS는 SEED 원칙에 따라 Android 시스템 폰트와 t1–t14 크기/행간 scale을 사용합니다.

| 스타일 | 크기 | 두께 | 행간 | 자간 |
|--------|------|------|------|------|
| displayLarge | 48sp | Bold | 60sp | 0sp |
| displayMedium | 40sp | Bold | 52sp | 0sp |
| displaySmall | 32sp | Bold | 42sp | 0sp |
| headlineLarge | 28sp | Bold | 38sp | 0sp |
| headlineMedium | 26sp | Bold | 35sp | 0sp |
| headlineSmall | 24sp | Bold | 32sp | 0sp |
| titleLarge | 22sp | Bold | 30sp | 0sp |
| titleMedium | 18sp | Bold | 24sp | 0sp |
| titleSmall | 16sp | Bold | 22sp | 0sp |
| bodyLarge | 16sp | Normal | 22sp | 0sp |
| bodyMedium | 14sp | Normal | 19sp | 0sp |
| bodySmall | 12sp | Normal | 16sp | 0sp |
| labelLarge | 14sp | Bold | 19sp | 0sp |
| labelMedium | 13sp | Bold | 18sp | 0sp |
| labelSmall | 11sp | Bold | 15sp | 0sp |

화면 작업의 상세 사용 규칙은 루트 `DESIGN.md`를 기준으로 합니다.

## 컴포넌트

앱 feature에서는 Material 시각 컴포넌트의 색상 객체나 `*Defaults`를 직접 조합하지
않습니다. 아래 KDS 컴포넌트가 시각 상태와 접근성 기본값을 소유합니다.

| 범주 | 컴포넌트 | 가이드 |
|------|----------|--------|
| Action | `KeepButton`, `KeepTextButton`, `KeepIconButton` | [Button](docs/components/button.md), [Text](docs/components/text-button.md), [Icon](docs/components/icon-button.md) |
| Menu | `KeepMenu`, `KeepMenuItem` | [Menu](docs/components/menu.md) |
| Surface | `KeepCard` | [Card](docs/components/card.md) |
| Navigation | `KeepTopAppBar`, `KeepCenterAlignedTopAppBar` | [Top app bar](docs/components/top-app-bar.md) |
| Selection | `KeepCheckbox`, `KeepSwitch`, `KeepRadioButton`, `KeepChip`, `KeepSelectableCard`, `KeepSegmentedControl` | [Component index](docs/components/README.md) |
| Status and metadata | `KeepBadge`, `KeepLabel` | [Badge](docs/components/badge.md), [Label](docs/components/label.md) |
| Input | `KeepField`, `KeepTextInput`, `KeepTextField`, `KeepInputButton`, `KeepDatePickerDialog`, `KeepTimeInput` | [Text field](docs/components/text-field.md), [Input button](docs/components/input-button.md), [Date/time](docs/components/date-time-picker.md) |
| Feedback | `KeepAlertDialog`, `KeepConfirmationDialog`, `KeepSnackbarHost`, `KeepSnackBar` | [Alert](docs/components/alert-dialog.md), [Snackbar](docs/components/snackbar.md) |
| Progress | `KeepCircularProgressIndicator`, `KeepLinearProgressIndicator`, `KeepStepIndicator` | [Progress](docs/components/progress-indicator.md) |
| Layout | `KeepDivider`, `KeepModalBottomSheet`, `KeepBottomSheetDragHandle` | [Divider](docs/components/divider.md), [Bottom sheet](docs/components/modal-bottom-sheet.md) |

### Compact selection and status

- `KeepChip`: SEED `Solid`, `OutlineStrong`, `OutlineWeak` variant와 `Small`, `Medium`,
  `Large` 크기를 제공합니다. `Action`, `Toggle`, `Radio` 역할에 따라 접근성 semantics를
  설정합니다.
- `KeepSelectableCard`: radio 방식의 선택 카드로 선택 배경, indicator, 설명,
  disabled 상태를 함께 관리합니다.
- `KeepSegmentedControl`: 짧은 동급 뷰 전환을 위한 tab semantics 기반 컨트롤입니다.
- `KeepBadge`: `Neutral`, `Brand`, `Critical` tone과 `Weak`, `Solid`, `Outline`
  variant를 제공합니다.
- `KeepLabel`: 작은 제목, metadata, caption, validation label의 크기·색상·강조를
  중앙에서 관리합니다.

화면 고유 컴포넌트는 남길 수 있지만 pill, badge, selectable surface, 반복 label
스타일을 직접 그리지 않고 위 컴포넌트를 조합해야 합니다.

### KeepButton

SEED Action Button 규격을 따르는 액션 버튼.

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

**스타일:** `BrandSolid`, `NeutralSolid`, `NeutralWeak`, `BrandOutline`,
`NeutralOutline`, `CriticalSolid`, `Ghost` variant와 `XSmall`, `Small`, `Medium`,
`Large` size를 제공합니다. Brand Solid 라벨과 아이콘은 StopIt의 CTA 방향에 따라
static-white `foreground.onBrand`를 사용합니다.

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
| `interactionSource` | MutableInteractionSource | 새 인스턴스 | 인터랙션 소스 |

**기본 색상:** 체크 시 `background.brandSolid`, 체크마크는 `foreground.onBrand`,
미체크 시 `stroke.neutralWeak`.

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
| `size` | KeepSwitchSize | Large | Small(26×16), Medium(38×24), Large(52×32) |
| `interactionSource` | MutableInteractionSource | 새 인스턴스 | 인터랙션 소스 |

SEED Switchmark 규격에 맞춰 기본 트랙은 52×32dp, thumb는 26dp이며 테두리를 사용하지
않습니다. 선택 여부와 관계없이 thumb는 흰색이고, 선택 트랙만 StopIt 브랜드 색상을
사용합니다. Small/Medium/Large의 시각 크기와 관계없이 실제 터치 영역은 최소
48×48dp를 보장하며, 비활성 상태는 전체 컨트롤에 38% opacity를 적용합니다.

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

**스타일:** 8dp radius, `background.neutralInverted` 배경,
`foreground.inverted` 텍스트, 14sp/19sp, 10dp 패딩.

---

### KeepModalBottomSheet

KDS의 sheet layer, foreground, overlay 역할을 적용하는 바텀시트.

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
| `dragHandle` | @Composable (() -> Unit)? | stroke.neutralWeak 드래그 핸들 | 핸들 컴포저블 |
| `content` | @Composable ColumnScope.() -> Unit | (필수) | 내용 |

**특징:** 상태바 인셋을 적용하고 라이트/다크 테마의 역할 기반 색상을 사용합니다.
큰 면적이 과도하게 밝아지지 않도록 라이트 테마의 시트는 `#F7F8F9`, 다크 테마는
`#1D2025`를 사용하며, 다이얼로그의 floating 표면과 역할을 분리합니다.
드래그 핸들의 시각 크기와 별개로 최소 44dp 상호작용 영역을 보장합니다.
최대 너비, shape, 색상, tonal elevation, scrim, system inset은 KDS가 고정해 화면별
시각 변형이 생기지 않도록 합니다.

---

### 광고/수익화 경계

KDS는 AdMob SDK 런타임을 직접 소유하지 않습니다. 배너 광고 UI와 노출/클릭/수익 callback은 앱의 monetization/analytics 경계(`app/src/main/java/com/uiery/keep/analytics/TrackedBannerAd.kt`)에서 관리합니다.

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
| `color` | Color | primary(StopIt amber) | 그라데이션 시작 색상 |

**애니메이션:** 0°→360° 무한 회전, 1400ms 주기, Linear 이징, 180° 호, Round 캡. 그라데이션은 color → 투명.
