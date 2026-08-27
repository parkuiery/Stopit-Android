#!/usr/bin/env python3
"""Source of truth for Stopit Android runtime instrumentation suites.

The workflow layer owns appops/install sequencing. This module owns only the
instrumentation class/method selectors that are passed to
`android.testInstrumentationRunnerArguments.class`.
"""

from __future__ import annotations

import argparse
import datetime
import os
import fnmatch
import hashlib
import json
import pathlib
import re
import shlex
import subprocess
import sys
from collections.abc import Iterable

REPO_ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID_TEST_SOURCE_ROOT = REPO_ROOT / "app" / "src" / "androidTest"
ANDROID_TEST_ROOT = ANDROID_TEST_SOURCE_ROOT / "java"
ANDROID_TEST_ROOTS = [
    ANDROID_TEST_SOURCE_ROOT / "java",
    ANDROID_TEST_SOURCE_ROOT / "kotlin",
]

# Android instrumentation tests that intentionally do not run in the default
# Android CI / Release QA runtime gates. Keep this list small and policy-based:
# anything not suite-covered or listed here fails the manifest inventory test.
INTENTIONALLY_EXCLUDED_ANDROID_TEST_CLASSES: dict[str, str] = {
    "com.uiery.keep.ExampleInstrumentedTest": "template smoke; not a Stopit runtime contract",
    "com.uiery.keep.BlockScreenContentIntegrationTest": "screen-local Compose regression; run from the owning issue/PR when Block copy/UI changes",
    "com.uiery.keep.feature.goallock.GoalLockCreationContentIntegrationTest": "screen-local Compose regression; run from Goal Lock UI/code PRs",
    "com.uiery.keep.feature.goallock.GoalLockDetailContentIntegrationTest": "screen-local Compose regression; run from Goal Lock UI/code PRs",
    "com.uiery.keep.feature.home.HomeStatusCtaCardIntegrationTest": "screen-local Compose regression; run from Home status/CTA UI PRs",
    "com.uiery.keep.feature.home.HomeGoalLockProgressCardAccessibilityTest": "screen-local accessibility regression; run from Goal Lock Home card UI/a11y PRs",
    "com.uiery.keep.feature.home.HomeKeepToggleTouchShortcutSemanticsTest": "screen-local Compose semantics regression; run from Home Keep toggle UI/a11y PRs",
    "com.uiery.keep.feature.lock.LockScreenLayoutTest": "screen-local Compose layout regression; run from Lock screen UI PRs",
    "com.uiery.keep.feature.lockhistory.component.LockHistoryPerformanceReportAccessibilityTest": "screen-local accessibility regression; run from LockHistory report UI/a11y PRs",
    "com.uiery.keep.feature.onboarding.OnboardingActionStackTest": "screen-local Compose layout regression; run from Onboarding action UI PRs",
    "com.uiery.keep.feature.onboarding.proposal.PromiseProposalEditActionsTest": "screen-local Compose regression; run from Promise proposal UI PRs",
    "com.uiery.keep.feature.parentmode.ParentModeSetupScreenAccessibilityTest": "screen-local accessibility regression; run from Parent Mode UI/a11y PRs",
    "com.uiery.keep.feature.routine.component.RoutineListContentIntegrationTest": "screen-local Compose regression; run from Routine card/list UI PRs",
    "com.uiery.keep.testing.AccessibilitySettingsDetailNavigatorTest": "test-helper/navigation utility regression; run from accessibility settings navigator PRs",
}

SUITES: dict[str, list[str]] = {
    "android_ci_focused_runtime_smoke": [
        "com.uiery.keep.qa.StopitReleaseSmokeTest",
        "com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest",
        "com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest",
        "com.uiery.keep.feature.onboarding.PromiseCoachOnboardingIntegrationTest",
        "com.uiery.keep.appselection.AndroidBlockExemptPackageProviderIntegrationTest",
        "com.uiery.keep.ui.component.EmergencyUnlockBottomSheetContentIntegrationTest",
        "com.uiery.keep.ui.component.CategoryBottomSheetContentIntegrationTest",
        "com.uiery.keep.ui.component.CategorySheetScrollBehaviorTest",
        "com.uiery.keep.ui.component.TimerPickerIntegrationTest",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForPackageAndClockChangeActions",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timezoneChangedDisablesEmptyRepeatDaysRoutineAndCancelsStaleAlarms",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedDisablesEmptyRepeatDaysRoutineAndCancelsStaleAlarms",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine",
        "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage",
        # 위 selector 는 자기 teardown lambda 를 넘기므로 서비스가 실제로 실행하는 배선을
        # 지나가지 않는다. 아래 세 개가 finishEmergencyUnlockWindow 실물을 돌린다. (#1167)
        "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#finishEmergencyUnlockWindow_clearsRuntimeStateAndDeliversCompletionOnce",
        "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#finishEmergencyUnlockWindow_deliversReservationThatSurvivedProcessRestart",
        "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#finishEmergencyUnlockWindow_withoutReservationSendsNothing",
        "com.uiery.keep.service.KeepMessagingServiceIntegrationTest",
        "com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest",
        # 화면 회귀가 아니라 저장 계약이다. 보호자 PIN 원문이 실제 DataStore 파일에 남지 않는지는
        # 가짜 store 로는 보일 수 없고, 그게 이 PIN 이 존재하는 이유다. (#1177)
        "com.uiery.keep.feature.parentmode.ParentModeGuardianPinDeviceTest",
    ],
    "android_ci_exact_alarm_default": [
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability",
    ],
    "android_ci_exact_alarm_denied": [
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt",
    ],
    "android_ci_exact_alarm_allowed": [
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm",
    ],
    "release_focused_ui_smoke": [
        "com.uiery.keep.qa.StopitReleaseSmokeTest",
    ],
    "release_prod_debug_smoke": [
        "com.uiery.keep.qa.StopitReleaseSmokeTest",
    ],
    "release_exact_alarm_default": [
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#defaultExactAlarmAppOpsFollowsAlarmManagerAvailability",
    ],
    "release_exact_alarm_denied": [
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt",
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addMultiDayRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedKeepsTriggeredRoutineEnabledAndLeavesNoNextPendingIntent",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedKeepsTriggeredMultiDayRoutineEnabledAndRevokesEveryRepeatDayAlarm",
    ],
    "release_exact_alarm_allowed": [
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm",
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingMultiDayRoutineWithExactAlarmPermissionSchedulesEveryRepeatDayAlarm",
        "com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#cancelRoutineAlarmRemovesEveryRepeatDayPendingIntent",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEnabledRoutineFromRoom",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#exactAlarmPermissionStateChangedWithPermissionAllowedReschedulesEveryRepeatDayAlarm",
    ],
    "release_remaining_runtime": [
        "com.uiery.keep.qa.StopitReleaseSmokeTest",
        "com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest",
        "com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest",
        "com.uiery.keep.feature.onboarding.PromiseCoachOnboardingIntegrationTest",
        "com.uiery.keep.database.KeepDatabaseMigrationTest",
        "com.uiery.keep.notification.RoutineStartNotificationTapIntegrationTest",
        "com.uiery.keep.notification.NotificationSmallIconIntegrationTest",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedDisablesEmptyRepeatDaysRoutineAndCancelsStaleAlarms",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresMultiDayRoutineAndSchedulesEveryRepeatDayAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEveryRepeatDayAlarmForMultiDayRoutine",
        "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage",
        "com.uiery.keep.service.KeepMessagingServiceIntegrationTest",
        "com.uiery.keep.manifest.ManifestContractIntegrationTest",
        "com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest",
    ],
    "notification_denied_receiver": [
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine",
    ],
    "notification_denied_emergency_unlock": [
        "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
    ],
    "notification_channel_disabled": [
        "com.uiery.keep.notification.NotificationChannelDisabledIntegrationTest",
    ],
}

RELEASE_QA_SEQUENCE = [
    "release_focused_ui_smoke",
    "release_prod_debug_smoke",
    "release_exact_alarm_default",
    "release_exact_alarm_denied",
    "release_exact_alarm_allowed",
    "release_remaining_runtime",
    "notification_denied_receiver",
    "notification_denied_emergency_unlock",
    "notification_channel_disabled",
]

ANDROID_CI_SEQUENCE = [
    "android_ci_focused_runtime_smoke",
    "android_ci_exact_alarm_default",
    "android_ci_exact_alarm_denied",
    "android_ci_exact_alarm_allowed",
    "notification_denied_receiver",
    "notification_denied_emergency_unlock",
    "notification_channel_disabled",
]


def selectors_for(suite_names: Iterable[str]) -> list[str]:
    selectors: list[str] = []
    for suite_name in suite_names:
        try:
            selectors.extend(SUITES[suite_name])
        except KeyError as exc:
            raise SystemExit(f"Unknown suite: {suite_name}") from exc
    return selectors


def class_arg(suite_names: Iterable[str]) -> str:
    return ",".join(selectors_for(suite_names))


def android_test_roots() -> list[pathlib.Path]:
    # Keep ANDROID_TEST_ROOT for older tests/patches, but scan both canonical
    # Android source roots so Kotlin-only instrumentation tests cannot bypass
    # the runtime-suite inventory guard.
    roots = list(globals().get("ANDROID_TEST_ROOTS", [ANDROID_TEST_ROOT]))
    if ANDROID_TEST_ROOT not in roots:
        roots.insert(0, ANDROID_TEST_ROOT)
    return roots


def android_test_source_for(class_name: str) -> pathlib.Path:
    relative = pathlib.Path(*class_name.split(".")).with_suffix(".kt")
    candidates = [root / relative for root in android_test_roots()]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return candidates[0]


def android_test_class_name_for(source_path: pathlib.Path) -> str:
    for root in android_test_roots():
        try:
            relative = source_path.relative_to(root).with_suffix("")
        except ValueError:
            continue
        return ".".join(relative.parts)
    raise ValueError(f"Android test source is outside configured roots: {source_path}")


def covered_android_test_classes() -> set[str]:
    return {selector.partition("#")[0] for selectors in SUITES.values() for selector in selectors}


def all_android_test_classes() -> set[str]:
    return {
        android_test_class_name_for(path)
        for root in android_test_roots()
        for path in root.rglob("*Test.kt")
    }


def unclassified_android_test_classes() -> list[str]:
    covered = covered_android_test_classes()
    excluded = set(INTENTIONALLY_EXCLUDED_ANDROID_TEST_CLASSES)
    return sorted(all_android_test_classes() - covered - excluded)


def kotlin_method_exists(source: str, method_name: str) -> bool:
    return re.search(rf"\bfun\s+{re.escape(method_name)}\s*\(", source) is not None


def validate_sources() -> list[str]:
    missing: list[str] = []
    for suite_name, selectors in SUITES.items():
        for selector in selectors:
            class_name, _, method_name = selector.partition("#")
            source_path = android_test_source_for(class_name)
            if not source_path.exists():
                missing.append(f"{suite_name}: {selector} (missing class source: {source_path.relative_to(REPO_ROOT)})")
                continue
            if method_name and not kotlin_method_exists(source_path.read_text(), method_name):
                missing.append(f"{suite_name}: {selector} (missing method in {source_path.relative_to(REPO_ROOT)})")
    return missing


def render_markdown(suite_names: Iterable[str]) -> str:
    lines: list[str] = []
    for suite_name in suite_names:
        lines.append(f"### `{suite_name}`")
        lines.extend(f"- `{selector}`" for selector in SUITES[suite_name])
        lines.append("")
    return "\n".join(lines).rstrip()


def _run_selector_group(connected_task: str, selectors: list[str]) -> int:
    completed = subprocess.run(
        [
            "./gradlew",
            "--console=plain",
            connected_task,
            f"-Pandroid.testInstrumentationRunnerArguments.class={','.join(selectors)}",
        ],
        cwd=REPO_ROOT,
    )
    return completed.returncode


def run_connected_tests(
    suite_names: Iterable[str],
    before: Iterable[str] = (),
    *,
    continue_on_failure: bool = False,
    variant: str = "devDebug",
    batch: bool = True,
) -> int:
    """Run runtime suites against a connected device.

    Host appops/install isolation is a *suite* boundary, not a selector
    boundary, so by default every selector in one suite shares a single
    instrumentation run and the `before` commands are applied once per suite.
    When a batched suite fails, its selectors are replayed one by one -- with
    `before` reapplied each time, exactly like `batch=False` -- so the failure
    report still names the offending selector instead of the whole suite.

    A batched failure that no single selector reproduces is reported as
    suspected cross-test interference rather than being silently swallowed.
    """
    before_commands = [shlex.split(command) for command in before]
    first_failure = 0
    failed_steps: list[str] = []
    connected_task = f":app:connected{variant[0].upper()}{variant[1:]}AndroidTest"

    def finish(returncode: int) -> int:
        if failed_steps:
            print("[android-runtime-suite] Aggregate failures:", file=sys.stderr)
            for failure in failed_steps:
                print(f"- {failure}", file=sys.stderr)
        return returncode

    def run_before() -> int:
        for command in before_commands:
            completed = subprocess.run(command, cwd=REPO_ROOT)
            if completed.returncode:
                failed_steps.append(f"before {shlex.join(command)} -> {completed.returncode}")
                return completed.returncode
        return 0

    for suite_name in suite_names:
        selectors = selectors_for([suite_name])
        batched = batch and len(selectors) > 1
        groups = [selectors] if batched else [[selector] for selector in selectors]

        suite_failure = 0
        before_aborted = False
        for group in groups:
            before_returncode = run_before()
            if before_returncode:
                if not first_failure:
                    first_failure = before_returncode
                if not continue_on_failure:
                    return finish(before_returncode)
                print(
                    f"[android-runtime-suite] SKIP after before failure: {suite_name}",
                    file=sys.stderr,
                )
                before_aborted = True
                break

            returncode = _run_selector_group(connected_task, group)
            if not returncode:
                continue
            suite_failure = suite_failure or returncode
            if batched:
                continue
            failed_steps.append(f"selector {group[0]} -> {returncode}")
            if not first_failure:
                first_failure = returncode
            if not continue_on_failure:
                return finish(returncode)

        if before_aborted or not suite_failure or not batched:
            continue

        if not first_failure:
            first_failure = suite_failure
        print(
            f"[android-runtime-suite] Suite {suite_name} failed as a batch; "
            "replaying its selectors individually to isolate the failure.",
            file=sys.stderr,
        )
        isolated: list[str] = []
        for selector in selectors:
            if run_before():
                break
            selector_returncode = _run_selector_group(connected_task, [selector])
            if selector_returncode:
                isolated.append(selector)
                failed_steps.append(f"selector {selector} -> {selector_returncode}")
        if not isolated:
            failed_steps.append(
                f"suite {suite_name} -> {suite_failure} "
                "(batched run failed but no single selector reproduced it; "
                "suspect cross-test interference -- rerun with --no-batch)"
            )
        if not continue_on_failure:
            return finish(first_failure)

    return finish(first_failure)


SEQUENCES: dict[str, list[str]] = {
    "android-ci": ANDROID_CI_SEQUENCE,
    "release": RELEASE_QA_SEQUENCE,
}

ANDROID_CI_BEFORE_COMMANDS: dict[str, list[str]] = {
    "android_ci_exact_alarm_default": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell cmd appops reset com.uiery.keep.dev",
    ],
    "android_ci_exact_alarm_denied": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny",
    ],
    "android_ci_exact_alarm_allowed": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow",
    ],
    "notification_denied_receiver": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore",
    ],
    "notification_denied_emergency_unlock": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore",
    ],
}


RELEASE_BEFORE_COMMANDS: dict[str, list[str]] = {
    "release_focused_ui_smoke": ["./gradlew --console=plain :app:installDevDebug"],
    "release_prod_debug_smoke": ["./gradlew --console=plain :app:installProdDebug"],
    "release_exact_alarm_default": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell cmd appops reset com.uiery.keep.dev",
    ],
    "release_exact_alarm_denied": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny",
    ],
    "release_exact_alarm_allowed": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow",
    ],
    "notification_denied_receiver": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore",
    ],
    "notification_denied_emergency_unlock": [
        "./gradlew --console=plain :app:installDevDebug",
        "adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore",
    ],
    "notification_channel_disabled": ["./gradlew --console=plain :app:installDevDebug"],
}

# The prod-flavour smoke must run against the production application id, so it is
# the one release suite that does not use the devDebug connected task.
RELEASE_VARIANTS: dict[str, str] = {"release_prod_debug_smoke": "prodDebug"}


def before_commands_for(sequence: str, suite_name: str) -> list[str]:
    if sequence == "release":
        return RELEASE_BEFORE_COMMANDS.get(suite_name, [])
    return ANDROID_CI_BEFORE_COMMANDS.get(suite_name, [])


def run_android_ci_suites(*, batch: bool = True, sequence: str = "android-ci") -> dict[str, dict]:
    """Run every suite in a sequence, recording each suite's outcome.

    Aggregate mode: an early suite failure never hides a later suite's result.
    Shared by the diagnostic entry point and the evidence-recording local gate so
    the two can never drift into running different things.
    """
    try:
        suite_names = SEQUENCES[sequence]
    except KeyError as exc:
        raise SystemExit(f"Unknown sequence: {sequence} (known: {', '.join(SEQUENCES)})") from exc

    results: dict[str, dict] = {}
    print(f"[android-runtime-suite] {sequence} aggregate mode: running all suites before final failure.")
    for suite_name in suite_names:
        print(f"[android-runtime-suite] Running suite: {suite_name}")
        returncode = run_connected_tests(
            [suite_name],
            before=before_commands_for(sequence, suite_name),
            continue_on_failure=True,
            batch=batch,
            variant=RELEASE_VARIANTS.get(suite_name, "devDebug") if sequence == "release" else "devDebug",
        )
        results[suite_name] = {
            "status": "passed" if not returncode else "failed",
            "selectors": len(SUITES[suite_name]),
            "returncode": returncode,
        }
    return results


def first_failure_of(results: dict[str, dict]) -> int:
    for result in results.values():
        if result["returncode"]:
            return result["returncode"]
    return 0


def run_android_ci_sequence(*, batch: bool = True) -> int:
    """Run Android CI runtime smoke suites in aggregate mode."""
    results = run_android_ci_suites(batch=batch)
    first_failure = first_failure_of(results)
    if first_failure:
        print(f"[android-runtime-suite] Android CI aggregate mode completed with failure: {first_failure}", file=sys.stderr)
    return first_failure


EVIDENCE_PATH = REPO_ROOT / ".runtime-evidence.json"
EVIDENCE_SCHEMA = 1

# Everything whose change could plausibly break a runtime contract that the
# Android CI suites cover. Deliberately broad: a digest that is too narrow lets a
# real regression reach main with stale evidence, while a digest that is too wide
# only costs a local rerun. Paths are matched against `git ls-files` output, so
# untracked build output and local scratch files can never move the digest.
RUNTIME_DIGEST_INPUTS: tuple[str, ...] = (
    "app/src/main/**",
    "app/src/androidTest/**",
    "app/src/dev/**",
    "app/src/prod/**",
    "app/build.gradle.kts",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradle/libs.versions.toml",
    "core/kds/src/main/**",
    "core/kds/build.gradle.kts",
    "scripts/android_runtime_suites.py",
)

# `.runtime-evidence.json` must never feed its own digest, or committing the
# evidence would immediately invalidate it and the gate would never converge.
# `google-services.json` is untracked secret material and is not reproducible
# across machines.
RUNTIME_DIGEST_EXCLUDES: tuple[str, ...] = (
    ".runtime-evidence.json",
    "**/google-services.json",
)


def _matches_any(path: str, patterns: Iterable[str]) -> bool:
    for pattern in patterns:
        if fnmatch.fnmatch(path, pattern):
            return True
        # fnmatch treats "/" as an ordinary character, so "a/**" already spans
        # nested directories; this extra check makes "a/**" also match "a" itself.
        if pattern.endswith("/**"):
            prefix = pattern[: -len("/**")]
            if path == prefix or path.startswith(f"{prefix}/"):
                return True
    return False


def runtime_digest_files() -> list[str]:
    """Tracked files that feed the runtime digest, in stable sorted order."""
    completed = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    tracked = [entry for entry in completed.stdout.split("\0") if entry]
    return sorted(
        path
        for path in tracked
        if _matches_any(path, RUNTIME_DIGEST_INPUTS)
        and not _matches_any(path, RUNTIME_DIGEST_EXCLUDES)
    )


def compute_runtime_digest() -> str:
    digest = hashlib.sha256()
    for path in runtime_digest_files():
        digest.update(path.encode())
        digest.update(b"\0")
        digest.update(hashlib.sha256((REPO_ROOT / path).read_bytes()).digest())
    return f"sha256:{digest.hexdigest()}"


def load_evidence() -> dict | None:
    if not EVIDENCE_PATH.exists():
        return None
    try:
        return json.loads(EVIDENCE_PATH.read_text())
    except json.JSONDecodeError:
        return None


def check_evidence(*, require_sequence: str | None = None) -> int:
    """Verify the committed evidence was produced from the current runtime sources.

    `require_sequence` additionally demands that the evidence came from a specific
    sequence: release gates need the wider release suite set, and android-ci
    evidence must not be mistaken for it.
    """
    evidence = load_evidence()
    if evidence is None:
        print(
            f"[runtime-gate] No usable {EVIDENCE_PATH.name} found.\n"
            "  Run the local runtime gate against a connected device and commit its evidence:\n"
            "    ./scripts/runtime-gate.sh",
            file=sys.stderr,
        )
        return 1

    if require_sequence:
        recorded_sequence = evidence.get("sequence", "android-ci")
        if recorded_sequence != require_sequence:
            print(
                f"[runtime-gate] Evidence was recorded from the '{recorded_sequence}' sequence, "
                f"but this gate requires '{require_sequence}'.\n"
                f"  Rerun the local gate for it and commit the refreshed evidence:\n"
                f"    ./scripts/runtime-gate.sh --sequence {require_sequence}",
                file=sys.stderr,
            )
            return 1

    recorded = evidence.get("runtime_digest")
    current = compute_runtime_digest()
    if recorded == current:
        suites = evidence.get("suites", {})
        print(
            f"[runtime-gate] OK - {len(suites)} suites verified locally at {current[:19]}... "
            f"on {evidence.get('device', {}).get('description', 'an unrecorded device')}"
        )
        return 0

    print(
        "[runtime-gate] Runtime sources changed since the evidence was recorded.\n"
        f"  recorded: {recorded}\n"
        f"  current:  {current}\n"
        "  Rerun the local runtime gate against a connected device and commit the refreshed evidence:\n"
        "    ./scripts/runtime-gate.sh",
        file=sys.stderr,
    )
    return 1


def _describe_device() -> dict:
    serial = os.environ.get("ANDROID_SERIAL")
    target = ["adb"] + (["-s", serial] if serial else [])

    def adb_prop(prop: str) -> str:
        try:
            completed = subprocess.run(
                target + ["shell", "getprop", prop],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
                timeout=30,
            )
        except (OSError, subprocess.SubprocessError):
            return ""
        return completed.stdout.strip() if completed.returncode == 0 else ""

    model = adb_prop("ro.product.model")
    sdk = adb_prop("ro.build.version.sdk")
    return {
        "model": model,
        "api_level": int(sdk) if sdk.isdigit() else None,
        "serial": serial,
        "description": f"{model or 'unknown'} (API {sdk or '?'})",
    }


def ensure_adb_on_path() -> str | None:
    """Make `adb` runnable for this process and its children.

    CI runners get adb from the emulator action, but a developer shell often does
    not have platform-tools on PATH. The suites shell out to bare `adb` (and so do
    the appops `before` commands), so resolve the SDK once here instead of making
    every caller care.
    """
    from shutil import which

    if which("adb"):
        return which("adb")

    candidates = []
    for env_var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk_root = os.environ.get(env_var)
        if sdk_root:
            candidates.append(pathlib.Path(sdk_root) / "platform-tools")
    candidates.append(pathlib.Path.home() / "Library" / "Android" / "sdk" / "platform-tools")
    candidates.append(pathlib.Path.home() / "Android" / "Sdk" / "platform-tools")

    for platform_tools in candidates:
        if (platform_tools / "adb").exists():
            os.environ["PATH"] = f"{platform_tools}{os.pathsep}{os.environ.get('PATH', '')}"
            return str(platform_tools / "adb")
    return None


def connected_devices() -> list[str]:
    try:
        completed = subprocess.run(
            ["adb", "devices"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            timeout=60,
        )
    except (OSError, subprocess.SubprocessError):
        return []
    if completed.returncode:
        return []
    devices = []
    for line in completed.stdout.splitlines()[1:]:
        serial, _, state = line.partition("\t")
        if state.strip() == "device":
            devices.append(serial.strip())
    return devices


def run_local_gate(*, batch: bool = True, sequence: str = "android-ci") -> int:
    """Run a runtime suite sequence locally and record evidence on success."""
    if ensure_adb_on_path() is None:
        print(
            "[runtime-gate] `adb` not found on PATH and no Android SDK platform-tools "
            "directory could be located.\n"
            "  Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your SDK, or add platform-tools to PATH.",
            file=sys.stderr,
        )
        return 2

    devices = connected_devices()
    if not devices:
        print(
            "[runtime-gate] No connected device or emulator found (`adb devices` is empty).\n"
            "  Start an emulator or attach a device, then rerun.",
            file=sys.stderr,
        )
        return 2
    selected = os.environ.get("ANDROID_SERIAL")
    if selected:
        # A phone plus a running emulator is the normal desk setup, so support
        # picking one instead of demanding the other be unplugged. AGP's device
        # selection reads the same variable, so exporting it is enough to keep
        # Gradle from fanning out.
        if selected not in devices:
            print(
                f"[runtime-gate] ANDROID_SERIAL={selected} is not connected. "
                f"Connected: {', '.join(devices) or '(none)'}",
                file=sys.stderr,
            )
            return 2
        print(f"[runtime-gate] Targeting {selected} (ANDROID_SERIAL).")
    elif len(devices) > 1:
        print(
            f"[runtime-gate] {len(devices)} devices connected: {', '.join(devices)}.\n"
            "  Gradle connected tests would fan out across all of them.\n"
            "  Export ANDROID_SERIAL=<serial> to pick one, or leave a single device attached.",
            file=sys.stderr,
        )
        return 2

    # Pin the digest before running: if sources change mid-run the evidence would
    # describe a tree that was never actually verified end to end.
    digest_before = compute_runtime_digest()

    results = run_android_ci_suites(batch=batch, sequence=sequence)
    suites = {
        name: {"status": result["status"], "selectors": result["selectors"]}
        for name, result in results.items()
    }
    first_failure = first_failure_of(results)

    if first_failure:
        failed = [name for name, result in suites.items() if result["status"] == "failed"]
        print(
            f"[runtime-gate] FAILED - no evidence written. Failing suites: {', '.join(failed)}",
            file=sys.stderr,
        )
        return first_failure

    digest_after = compute_runtime_digest()
    if digest_after != digest_before:
        print(
            "[runtime-gate] Runtime sources changed while the gate was running; "
            "no evidence written. Rerun on a quiet tree.",
            file=sys.stderr,
        )
        return 1

    head = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    evidence = {
        "schema": EVIDENCE_SCHEMA,
        "sequence": sequence,
        "runtime_digest": digest_after,
        "suites": suites,
        "device": _describe_device(),
        "variant": "devDebug",
        "recorded_at": datetime.datetime.now(datetime.timezone.utc)
        .replace(microsecond=0)
        .isoformat(),
        "recorded_from_commit": head.stdout.strip() if head.returncode == 0 else None,
    }
    EVIDENCE_PATH.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
    print(
        f"[runtime-gate] PASSED - {len(suites)} suites. "
        f"Wrote {EVIDENCE_PATH.name}; commit it so CI can verify this run."
    )
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    class_arg_parser = subparsers.add_parser("class-arg", help="Print comma-separated instrumentation selector argument")
    class_arg_parser.add_argument("suite", nargs="+")

    lines_parser = subparsers.add_parser("lines", help="Print one selector per line")
    lines_parser.add_argument("suite", nargs="+")

    selector_parser = subparsers.add_parser("selector", help="Print one selector by suite and zero-based index")
    selector_parser.add_argument("suite")
    selector_parser.add_argument("index", type=int)

    markdown_parser = subparsers.add_parser("markdown", help="Print Markdown list for suites")
    markdown_parser.add_argument("suite", nargs="+")

    run_parser = subparsers.add_parser("run-connected", help="Run each suite as one connectedAndroidTest Gradle invocation (see --no-batch)")
    run_parser.add_argument("suite", nargs="+")
    run_parser.add_argument("--before", action="append", default=[], help="Command to run before each suite (and before each selector during a bisect replay); may be supplied multiple times")
    run_parser.add_argument(
        "--variant",
        default="devDebug",
        choices=["devDebug", "prodDebug"],
        help="Android variant for the connected test task (default: devDebug)",
    )
    run_parser.add_argument(
        "--continue-on-failure",
        action="store_true",
        help="Run remaining selectors and print an aggregate failure summary before returning non-zero",
    )
    run_parser.add_argument(
        "--no-batch",
        dest="batch",
        action="store_false",
        help="Run one Gradle invocation per selector instead of one per suite (diagnostic escalation)",
    )

    subparsers.add_parser("run-android-ci", help="Run Android CI runtime smoke suites in aggregate diagnostic mode")

    local_gate_parser = subparsers.add_parser(
        "run-local-gate",
        help="Run the Android CI runtime suites against a connected device and record .runtime-evidence.json",
    )
    local_gate_parser.add_argument(
        "--sequence",
        default="android-ci",
        choices=sorted(SEQUENCES),
        help="Which suite sequence to run (default: android-ci)",
    )
    local_gate_parser.add_argument(
        "--no-batch",
        dest="batch",
        action="store_false",
        help="Run one Gradle invocation per selector instead of one per suite (diagnostic escalation)",
    )

    check_parser = subparsers.add_parser(
        "check-evidence",
        help="Verify .runtime-evidence.json was recorded from the current runtime sources",
    )
    check_parser.add_argument(
        "--require-sequence",
        default=None,
        choices=sorted(SEQUENCES),
        help="Also require that the evidence came from this sequence",
    )
    subparsers.add_parser(
        "runtime-digest",
        help="Print the digest of the runtime sources the local gate covers",
    )
    subparsers.add_parser(
        "runtime-digest-files",
        help="Print the tracked files that feed the runtime digest",
    )
    subparsers.add_parser("list-suites", help="Print known suite names")
    subparsers.add_parser("validate-sources", help="Verify selectors point to existing androidTest classes/methods")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    if args.command == "class-arg":
        print(class_arg(args.suite))
    elif args.command == "lines":
        print("\n".join(selectors_for(args.suite)))
    elif args.command == "selector":
        selectors = selectors_for([args.suite])
        try:
            print(selectors[args.index])
        except IndexError:
            print(f"Index {args.index} out of range for {args.suite}", file=sys.stderr)
            return 2
    elif args.command == "markdown":
        print(render_markdown(args.suite))
    elif args.command == "run-connected":
        return run_connected_tests(
            args.suite,
            before=args.before,
            continue_on_failure=args.continue_on_failure,
            variant=args.variant,
            batch=args.batch,
        )
    elif args.command == "run-android-ci":
        return run_android_ci_sequence()
    elif args.command == "run-local-gate":
        return run_local_gate(batch=args.batch, sequence=args.sequence)
    elif args.command == "check-evidence":
        return check_evidence(require_sequence=args.require_sequence)
    elif args.command == "runtime-digest":
        print(compute_runtime_digest())
    elif args.command == "runtime-digest-files":
        print("\n".join(runtime_digest_files()))
    elif args.command == "list-suites":
        print("\n".join(SUITES.keys()))
    elif args.command == "validate-sources":
        missing = validate_sources()
        if missing:
            print("\n".join(missing), file=sys.stderr)
            return 1
        print("OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
