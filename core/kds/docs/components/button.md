# KeepButton

Source: `KeepButton.kt`

## SEED references

- [Action Button](https://seed-design.io/llms/components/action-button.txt)

## Usage

명확한 단일 액션에 사용합니다. 한 화면에는 `BrandSolid` 핵심 CTA를 원칙적으로 하나만
두고, 파괴 동작은 `CriticalSolid`, 보조 동작은 neutral/outline/ghost를 사용합니다.

## Anatomy

Container와 label로 구성합니다. 로딩 시 label 자리를 progress indicator가 대체합니다.

## Properties and states

- Variants: `BrandSolid`, `NeutralSolid`, `NeutralWeak`, `BrandOutline`,
  `NeutralOutline`, `CriticalSolid`, `Ghost`
- Sizes: `XSmall` 32dp, `Small` 36dp, `Medium` 40dp, `Large` 52dp
- States: enabled, pressed, disabled, loading

## StopIt adaptation

브랜드 variant는 StopIt amber와 흰색 content를 사용합니다. `bottomSpacing`은 기존 API
호환용이며 새 조합에서는 부모 layout이 간격을 소유하는 것을 권장합니다.

## Accessibility

동사형 label을 사용하고 loading semantics를 노출하며 로딩 중 중복 실행을 막습니다.

## Verification

모든 size/variant의 light/dark, pressed, disabled, loading 및 긴 label을 검사합니다.
