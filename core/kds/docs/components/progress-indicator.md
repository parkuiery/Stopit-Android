# Keep progress indicators

Source: `KeepProgressIndicator.kt`

## SEED references

- [Progress Circle](https://seed-design.io/llms/components/progress-circle.txt)
- [Loading pattern](https://seed-design.io/llms/patterns/loading.txt)

## Usage

작업 진행 또는 단계 상태를 나타냅니다. progress가 실제로 측정 가능할 때 determinate를 사용합니다.

## Anatomy

circular/linear track와 indicator, step indicator의 completed/current/upcoming state로 구성합니다.

## Properties and states

indeterminate, determinate progress, enabled context와 완료 상태를 지원합니다.

## StopIt adaptation

활성 진행에는 StopIt brand role을 사용하고 track은 neutral low-emphasis 역할을 사용합니다.

## Accessibility

determinate 값 또는 indeterminate semantics를 제공하고 진행률을 색상만으로 설명하지 않습니다.

## Verification

0/중간/100%, indeterminate, reduced-motion context, TalkBack progress와 대비를 검사합니다.

