#!/usr/bin/env python3
"""Source of truth for Stopit Android runtime instrumentation suites.

The workflow layer owns appops/install sequencing. This module owns only the
instrumentation class/method selectors that are passed to
`android.testInstrumentationRunnerArguments.class`.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import shlex
import subprocess
import sys
from collections.abc import Iterable

REPO_ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID_TEST_ROOT = REPO_ROOT / "app" / "src" / "androidTest" / "java"

SUITES: dict[str, list[str]] = {
    "android_ci_focused_runtime_smoke": [
        "com.uiery.keep.qa.StopitReleaseSmokeTest",
        "com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest",
        "com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest",
        "com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContentIntegrationTest",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForPackageAndClockChangeActions",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine",
        "com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest#handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage",
        "com.uiery.keep.service.KeepMessagingServiceIntegrationTest",
        "com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest",
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
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent",
        "com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesMultiDayRoutineAndRevokesEveryRepeatDayAlarm",
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
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesMultiDayStoredRoutineAndSchedulesEveryRepeatDayAlarm",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestMarksBootReceiverNotExported",
        "com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm",
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


def android_test_source_for(class_name: str) -> pathlib.Path:
    return ANDROID_TEST_ROOT / pathlib.Path(*class_name.split(".")).with_suffix(".kt")


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


def run_connected_tests(
    suite_names: Iterable[str],
    before: Iterable[str] = (),
    *,
    continue_on_failure: bool = False,
) -> int:
    selectors = selectors_for(suite_names)
    before_commands = [shlex.split(command) for command in before]
    first_failure = 0
    failed_steps: list[str] = []

    for selector in selectors:
        before_failed = False
        for command in before_commands:
            completed = subprocess.run(command, cwd=REPO_ROOT)
            if completed.returncode:
                if not first_failure:
                    first_failure = completed.returncode
                failed_steps.append(f"before {shlex.join(command)} -> {completed.returncode}")
                before_failed = True
                if not continue_on_failure:
                    return completed.returncode
                break
        if before_failed:
            print(f"[android-runtime-suite] SKIP selector after before failure: {selector}", file=sys.stderr)
            continue

        completed = subprocess.run(
            [
                "./gradlew",
                "--console=plain",
                ":app:connectedDevDebugAndroidTest",
                f"-Pandroid.testInstrumentationRunnerArguments.class={selector}",
            ],
            cwd=REPO_ROOT,
        )
        if completed.returncode:
            if not first_failure:
                first_failure = completed.returncode
            failed_steps.append(f"selector {selector} -> {completed.returncode}")
            if not continue_on_failure:
                return completed.returncode

    if failed_steps:
        print("[android-runtime-suite] Aggregate failures:", file=sys.stderr)
        for failure in failed_steps:
            print(f"- {failure}", file=sys.stderr)
    return first_failure


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


def run_android_ci_sequence() -> int:
    """Run Android CI runtime smoke suites in aggregate mode."""
    first_failure = 0
    print("[android-runtime-suite] Android CI aggregate mode: running all runtime smoke suites before final failure.")
    for suite_name in ANDROID_CI_SEQUENCE:
        print(f"[android-runtime-suite] Running suite: {suite_name}")
        result = run_connected_tests(
            [suite_name],
            before=ANDROID_CI_BEFORE_COMMANDS.get(suite_name, []),
            continue_on_failure=True,
        )
        if result and not first_failure:
            first_failure = result
    if first_failure:
        print(f"[android-runtime-suite] Android CI aggregate mode completed with failure: {first_failure}", file=sys.stderr)
    return first_failure


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

    run_parser = subparsers.add_parser("run-connected", help="Run each selector as a separate connectedDevDebugAndroidTest Gradle invocation")
    run_parser.add_argument("suite", nargs="+")
    run_parser.add_argument("--before", action="append", default=[], help="Command to run before each selector; may be supplied multiple times")
    run_parser.add_argument(
        "--continue-on-failure",
        action="store_true",
        help="Run remaining selectors and print an aggregate failure summary before returning non-zero",
    )

    subparsers.add_parser("run-android-ci", help="Run Android CI runtime smoke suites in aggregate diagnostic mode")
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
        return run_connected_tests(args.suite, before=args.before, continue_on_failure=args.continue_on_failure)
    elif args.command == "run-android-ci":
        return run_android_ci_sequence()
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
