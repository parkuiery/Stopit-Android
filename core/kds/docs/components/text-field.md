# KeepField, KeepTextInput, KeepTextField

Source: `KeepTextField.kt`

## SEED references

- [Field](https://v3.seed-design.io/components/field)
- [Text Input and Textarea](https://v3.seed-design.io/docs/components/text-input)
- [Components index](https://seed-design.io/components/llms.txt)

## Usage

- `KeepTextInput`: 한 줄 또는 여러 줄 텍스트를 실제로 입력받는 input shell입니다.
- `KeepField`: label, requirement mark, helper/error, character count와 임의의 input을
  조합하는 컨테이너입니다.
- `KeepTextField`: `KeepField`와 `KeepTextInput`을 조합하는 일반적인 편의 API입니다.

선택 dialog를 여는 값에는 text input을 흉내 내지 말고 input button 패턴을 사용합니다.

## Anatomy

`KeepField`는 Header, Input, Footer로 구성합니다.

- Header: 명사형 label, 필수 `*` 또는 현지화된 선택 표기, optional suffix action
- Input: value, container, optional leading/prefix/suffix/trailing, optional clear button
- Footer: helper 또는 error 중 하나와 optional character count

error와 helper가 동시에 있으면 error만 표시합니다. label을 placeholder로 대체하지
않습니다.

## Properties and states

- Variants: `Outline`, `Underline`
- States: enabled, focused, error, error-focused, disabled, read-only, has-value
- `Outline` mobile 기본 크기는 높이 52dp, 좌우 padding 16dp, radius 12dp입니다.
- `Underline`은 높이 40dp이며 화면에 입력이 하나뿐일 때 우선 사용합니다.
- focused stroke는 brand가 아니라 `stroke.neutralContrast`, error stroke는
  `stroke.criticalSolid`입니다.
- clear button은 값이 있고 enabled/read-write이며 현지화된
  `clearButtonContentDescription`이 제공된 경우에만 표시합니다.
- single/multi-line, keyboard options/actions, visual transformation을 지원합니다.
- 길이 제한이 있으면 `maxCharacterCount`로 입력 제한과 footer count를 함께 관리합니다.
- 일반적인 보조 문구는 `KeepFieldHelperTone.Subtle`을 사용합니다. 설정 결과나 동작
  제약처럼 반드시 읽어야 하는 설명은 `Muted`로 한 단계 높은 대비를 사용할 수 있습니다.

## StopIt adaptation

StopIt 팔레트의 semantic role을 사용하되 SEED의 중립 포커스 위계를 유지합니다.
Material `TextField`/`OutlinedTextField`의 floating label, filled indicator와 brand focus
기본값은 사용하지 않습니다. Android 앱은 mobile large `Outline`만 제공하고 SEED의
desktop medium 크기는 제공하지 않습니다.

루틴 이름처럼 화면 또는 sheet에 입력이 하나뿐이면 `Underline`과 외부 field label을
사용합니다. 여러 입력이 모인 설정 form과 검색에는 `Outline`을 사용합니다.

## Accessibility

- label은 최대 두 줄이며 입력 semantics에도 연결합니다.
- clear button은 48dp 터치 영역과 현지화된 content description을 가져야 합니다.
- 오류는 stroke 색상만으로 표현하지 않고 행동 지시형 error message를 함께 제공합니다.
- 선택 표기는 호출부가 현지화된 `optionalText`를 제공해야 합니다.
- read-only와 disabled를 구분하고 disabled 텍스트/placeholder에는 전용 role을 사용합니다.

## Verification

- `Outline` 52dp와 `Underline` 40dp geometry contract test
- helper/error 우선순위와 character count
- focus/IME, empty/filled/error/error-focused/disabled/read-only
- password transformation, multiline 최소 높이, font scale 200%
- TalkBack label, error announcement, clear button name과 48dp touch target
