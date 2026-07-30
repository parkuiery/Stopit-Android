# KeepSegmentedControl

Source: `KeepSegmentedControl.kt`

## SEED references

- [Segmented Control](https://seed-design.io/llms/components/segmented-control.txt)

## Usage

한 화면에서 동급 콘텐츠를 즉시 필터링하거나 전환할 때 사용합니다. navigation destination이나
서로 다른 작업 흐름에는 사용하지 않습니다.

## Anatomy

group container와 동일 폭 segment labels로 구성합니다.

## Properties and states

selected index, labels, enabled/disabled segment 및 pressed state를 관리합니다. label은 짧고
동일한 문법 구조를 사용합니다.

## StopIt adaptation

선택 segment는 brand selected 역할, group은 neutral low-emphasis surface를 사용합니다.

## Accessibility

각 segment는 tab semantics와 selected state를 제공하고 읽기 순서가 시각적 순서와 일치해야 합니다.

## Verification

2개 이상 segment, 긴 번역, selected state, keyboard/TalkBack 순서와 작은 화면 폭을 검사합니다.
