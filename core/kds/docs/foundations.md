# KDS foundations

## SEED references

- [Foundation index](https://seed-design.io/foundations/llms.txt)
- [Color roles](https://seed-design.io/llms/foundations/color/color-role.txt)
- [Design tokens](https://seed-design.io/llms/foundations/design-token.txt)
- [Elevation](https://seed-design.io/llms/foundations/elevation.txt)
- [Iconography usage](https://seed-design.io/llms/foundations/iconography/usage.txt)
- [Inclusive design](https://seed-design.io/llms/foundations/inclusive-design.txt)
- [International design](https://seed-design.io/llms/foundations/international-design.txt)
- [Layout](https://seed-design.io/llms/foundations/layout.txt)
- [Motion](https://seed-design.io/llms/foundations/motion.txt)
- [Radius](https://seed-design.io/llms/foundations/radius.txt)
- [Spacing](https://seed-design.io/llms/foundations/spacing.txt)
- [State](https://seed-design.io/llms/foundations/state.txt)
- [Typography](https://seed-design.io/llms/foundations/typography.txt)
- [Voice and tone](https://seed-design.io/llms/foundations/voice-and-tone.txt)
- [Writing](https://seed-design.io/llms/foundations/writing.txt)

## KDS rules

### Tokens

- 새 컴포넌트는 `KeepTheme.semanticColors`와 KDS typography를 사용합니다.
- raw `Color`, 임의의 Material color slot, feature-local `*Defaults` 조합을 금지합니다.
- StopIt은 SEED 역할 구조를 따르되 브랜드 팔레트는 amber 계열을 유지합니다.
- spacing은 2dp 기반 SEED scale을 사용하고, 화면 gutter는 기본 16dp, 컴포넌트 간
  기본 세로 간격은 12dp로 시작합니다.
- radius, elevation, motion 값이 반복되면 KDS 토큰으로 승격하고 문서화합니다.

### Layout and hierarchy

- `layerBasement` 위의 그룹 콘텐츠는 `layerDefault`로 분리합니다.
- elevation보다 layer 역할, stroke, spacing으로 위계를 먼저 표현합니다.
- 동급 요소의 간격과 정렬은 동일해야 하며, 긴 번역과 큰 글꼴에서 wrap/stack합니다.

### State

- 최소 enabled, pressed, focused, selected, disabled, loading, error 상태를 검토합니다.
- 상태는 색상 하나로만 전달하지 않고 라벨, 아이콘, 형태 또는 semantics를 병행합니다.
- 비동기 액션은 중복 실행을 막고 진행 상태를 TalkBack에 노출합니다.

### Accessibility and internationalization

- 기능성 터치 영역은 Android에서 최소 48dp를 기본으로 합니다.
- 기능성 이미지와 아이콘에는 맥락 중심 `contentDescription`을 제공하고 장식은 숨깁니다.
- 스크린 리더의 탐색 순서가 시각적 읽기 순서와 일치해야 합니다.
- 시스템 글꼴 크기, 다크 테마, 긴 한국어·영어 문구에서 잘림이 없어야 합니다.
- 오류는 색상 외에 구체적인 메시지와 해결 방법을 입력 요소 가까이에 제공합니다.
- 긴/반복 모션은 줄이거나 생략 가능한 경로를 제공하고 번쩍임을 사용하지 않습니다.

### Content

- StopIt의 문구는 간결하고 직접적이며 사용자가 다음 행동을 이해할 수 있어야 합니다.
- 당근의 지역 커뮤니티 브랜드 보이스를 복제하지 않습니다.
- 성별·연령·능력·가족 형태를 불필요하게 가정하지 않습니다.

## Verification

- 토큰 변경: light/dark 색상 contract test
- 컴포넌트 변경: enabled/pressed/selected/disabled/loading Preview 또는 screenshot
- 상호작용 변경: role, state, click label 및 focus order 검사
- 텍스트 변경: font scale과 긴 현지화 문자열 검사
