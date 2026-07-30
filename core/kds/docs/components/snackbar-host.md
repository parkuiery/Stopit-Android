# KeepSnackbarHost

Source: `KeepSnackbarHost.kt`

## SEED references

- [Snackbar](https://seed-design.io/llms/components/snackbar.txt)

## Usage

화면의 모든 snackbar를 `KeepSnackBar`로 렌더링하는 host로 사용합니다.

## Anatomy

`SnackbarHostState`, placement modifier, KDS snackbar renderer로 구성합니다.

## Properties and states

queue, current snackbar, dismissal은 Material host state가 관리하고 시각 정책은 KDS가 소유합니다.

## StopIt adaptation

feature가 raw `SnackbarHost`와 local snackbar style을 조립하지 못하도록 단일 진입점을 제공합니다.

## Accessibility

Scaffold의 적절한 위치에서 system navigation 및 bottom action을 가리지 않아야 합니다.

## Verification

queue 순서, bottom inset, banner/bottom sheet와의 겹침 및 announcement를 검사합니다.
