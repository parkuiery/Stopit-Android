import pathlib
import tempfile
import unittest
from unittest import mock

from scripts import android_runtime_suites


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"


class AndroidRuntimeSuitesManifestTest(unittest.TestCase):
    def test_required_suites_are_defined(self):
        required = {
            "android_ci_focused_runtime_smoke",
            "android_ci_exact_alarm_default",
            "android_ci_exact_alarm_denied",
            "android_ci_exact_alarm_allowed",
            "release_focused_ui_smoke",
            "release_exact_alarm_default",
            "release_exact_alarm_denied",
            "release_exact_alarm_allowed",
            "release_remaining_runtime",
            "notification_denied_receiver",
            "notification_denied_emergency_unlock",
            "notification_channel_disabled",
        }

        self.assertEqual(required, set(android_runtime_suites.SUITES))
        for suite_name in required:
            with self.subTest(suite=suite_name):
                self.assertTrue(android_runtime_suites.SUITES[suite_name])

    def test_all_selectors_exist_in_android_test_sources(self):
        self.assertEqual([], android_runtime_suites.validate_sources())

    def test_all_android_test_classes_are_suite_covered_or_intentionally_excluded(self):
        self.assertEqual([], android_runtime_suites.unclassified_android_test_classes())

    def test_inventory_includes_android_test_kotlin_source_root(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_root = pathlib.Path(temp_dir) / "app" / "src" / "androidTest"
            java_root = source_root / "java"
            kotlin_root = source_root / "kotlin"
            kotlin_test = kotlin_root / "com" / "uiery" / "keep" / "qa" / "KotlinOnlyRuntimeTest.kt"
            kotlin_test.parent.mkdir(parents=True)
            kotlin_test.write_text(
                "package com.uiery.keep.qa\n\n"
                "class KotlinOnlyRuntimeTest {\n"
                "    fun kotlinRootScenario() = Unit\n"
                "}\n"
            )

            with mock.patch.object(android_runtime_suites, "REPO_ROOT", pathlib.Path(temp_dir)), \
                    mock.patch.object(android_runtime_suites, "ANDROID_TEST_ROOT", java_root), \
                    mock.patch.object(android_runtime_suites, "ANDROID_TEST_ROOTS", [java_root, kotlin_root], create=True):
                self.assertEqual(
                    ["com.uiery.keep.qa.KotlinOnlyRuntimeTest"],
                    android_runtime_suites.unclassified_android_test_classes(),
                )

    def test_validate_sources_resolves_android_test_kotlin_selectors(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_root = pathlib.Path(temp_dir) / "app" / "src" / "androidTest"
            java_root = source_root / "java"
            kotlin_root = source_root / "kotlin"
            kotlin_test = kotlin_root / "com" / "uiery" / "keep" / "qa" / "KotlinCoveredRuntimeTest.kt"
            kotlin_test.parent.mkdir(parents=True)
            kotlin_test.write_text(
                "package com.uiery.keep.qa\n\n"
                "class KotlinCoveredRuntimeTest {\n"
                "    fun coveredScenario() = Unit\n"
                "}\n"
            )

            with mock.patch.object(android_runtime_suites, "REPO_ROOT", pathlib.Path(temp_dir)), \
                    mock.patch.object(android_runtime_suites, "ANDROID_TEST_ROOT", java_root), \
                    mock.patch.object(android_runtime_suites, "ANDROID_TEST_ROOTS", [java_root, kotlin_root], create=True), \
                    mock.patch.object(android_runtime_suites, "SUITES", {
                        "fixture": ["com.uiery.keep.qa.KotlinCoveredRuntimeTest#coveredScenario"],
                    }):
                self.assertEqual([], android_runtime_suites.validate_sources())

    def test_release_qa_covers_database_migration_and_routine_notification_tap_contracts(self):
        release_selectors = android_runtime_suites.selectors_for(android_runtime_suites.RELEASE_QA_SEQUENCE)

        self.assertIn("com.uiery.keep.database.KeepDatabaseMigrationTest", release_selectors)
        self.assertIn("com.uiery.keep.notification.RoutineStartNotificationTapIntegrationTest", release_selectors)

    def test_category_selection_compose_regression_runs_in_android_ci_smoke(self):
        self.assertIn(
            "com.uiery.keep.ui.component.CategoryBottomSheetContentIntegrationTest",
            android_runtime_suites.SUITES["android_ci_focused_runtime_smoke"],
        )

    def test_timer_picker_external_time_regression_runs_in_android_ci_smoke(self):
        self.assertIn(
            "com.uiery.keep.ui.component.TimerPickerIntegrationTest",
            android_runtime_suites.SUITES["android_ci_focused_runtime_smoke"],
        )

    def test_cli_class_arg_preserves_comma_separated_selector_contract(self):
        class_arg = android_runtime_suites.class_arg([
            "notification_denied_receiver",
            "notification_denied_emergency_unlock",
        ])

        self.assertIn(
            "ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine",
            class_arg,
        )
        self.assertIn(
            "EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
            class_arg,
        )
        self.assertNotIn("\n", class_arg)
        self.assertEqual(1, class_arg.count(","))

    def test_workflows_use_manifest_instead_of_hardcoded_selector_lists(self):
        android_ci = ANDROID_CI_WORKFLOW.read_text()
        release_qa = RELEASE_QA_WORKFLOW.read_text()

        self.assertIn("scripts/android_runtime_suites.py run-android-ci", android_ci)
        self.assertNotIn("scripts/android_runtime_suites.py run-connected android_ci_focused_runtime_smoke", android_ci)
        self.assertIn("scripts/android_runtime_suites.py run-connected release_exact_alarm_denied", release_qa)
        self.assertIn("scripts/android_runtime_suites.py run-connected release_exact_alarm_allowed", release_qa)
        self.assertIn("scripts/android_runtime_suites.py run-connected release_remaining_runtime", release_qa)
        self.assertIn("scripts/android_runtime_suites.py run-connected notification_denied_receiver notification_denied_emergency_unlock", release_qa)

    def test_run_connected_executes_each_selector_with_before_commands(self):
        completed = mock.Mock(returncode=0)
        with mock.patch.object(android_runtime_suites.subprocess, "run", return_value=completed) as run:
            result = android_runtime_suites.run_connected_tests(
                ["notification_denied_receiver", "notification_denied_emergency_unlock"],
                before=[
                    "./gradlew --console=plain :app:installDevDebug",
                    "adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore",
                ],
            )

        self.assertEqual(0, result)
        self.assertEqual(6, run.call_count)
        self.assertEqual(["./gradlew", "--console=plain", ":app:installDevDebug"], run.call_args_list[0].args[0])
        self.assertEqual(["adb", "shell", "appops", "set", "com.uiery.keep.dev", "POST_NOTIFICATION", "ignore"], run.call_args_list[1].args[0])
        self.assertEqual(
            [
                "./gradlew",
                "--console=plain",
                ":app:connectedDevDebugAndroidTest",
                "-Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine",
            ],
            run.call_args_list[2].args[0],
        )

    def test_run_connected_continue_on_failure_runs_later_selectors_and_returns_failure(self):
        first_failure = mock.Mock(returncode=7)
        later_success = mock.Mock(returncode=0)
        with mock.patch.object(
            android_runtime_suites.subprocess,
            "run",
            side_effect=[first_failure, later_success],
        ) as run:
            result = android_runtime_suites.run_connected_tests(
                ["notification_denied_receiver", "notification_denied_emergency_unlock"],
                continue_on_failure=True,
            )

        self.assertEqual(7, result)
        self.assertEqual(2, run.call_count)
        self.assertIn(
            "ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine",
            run.call_args_list[0].args[0][-1],
        )
        self.assertIn(
            "EmergencyUnlockExpiryIntegrationTest#emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification",
            run.call_args_list[1].args[0][-1],
        )

    def test_android_ci_aggregate_runner_keeps_notification_suites_after_first_suite_failure(self):
        completed = mock.Mock(returncode=0)
        first_failure = mock.Mock(returncode=9)
        calls: list[list[str]] = []

        def fake_run(command, cwd):
            calls.append(command)
            if any("StopitReleaseSmokeTest" in part for part in command):
                return first_failure
            return completed

        with mock.patch.object(android_runtime_suites.subprocess, "run", side_effect=fake_run):
            result = android_runtime_suites.run_android_ci_sequence()

        self.assertEqual(9, result)
        connected_commands = [call for call in calls if ":app:connectedDevDebugAndroidTest" in call]
        self.assertTrue(any("StopitReleaseSmokeTest" in call[-1] for call in connected_commands))
        self.assertTrue(any("defaultExactAlarmAppOpsFollowsAlarmManagerAvailability" in call[-1] for call in connected_commands))
        self.assertTrue(any("addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt" in call[-1] for call in connected_commands))
        self.assertTrue(any("enablingRoutineWithExactAlarmPermissionSchedulesAlarm" in call[-1] for call in connected_commands))
        self.assertTrue(any("routineAlarmReceiverWithoutPostNotificationsPermission" in call[-1] for call in connected_commands))
        self.assertTrue(any("NotificationChannelDisabledIntegrationTest" in call[-1] for call in connected_commands))
        self.assertTrue(any(call[:3] == ["./gradlew", "--console=plain", ":app:installDevDebug"] for call in calls))
        self.assertTrue(any(call[:5] == ["adb", "shell", "cmd", "appops", "reset"] for call in calls))
        self.assertTrue(any(call[:6] == ["adb", "shell", "appops", "set", "com.uiery.keep.dev", "SCHEDULE_EXACT_ALARM"] and call[-1] == "deny" for call in calls))
        self.assertTrue(any(call[:6] == ["adb", "shell", "appops", "set", "com.uiery.keep.dev", "SCHEDULE_EXACT_ALARM"] and call[-1] == "allow" for call in calls))
        self.assertTrue(any(call[:6] == ["adb", "shell", "appops", "set", "com.uiery.keep.dev", "POST_NOTIFICATION"] for call in calls))

    def test_runtime_suite_manifest_changes_materialize_ci_scopes(self):
        android_ci = ANDROID_CI_WORKFLOW.read_text()
        self.assertIn("scripts/android_runtime_suites.py", android_ci)
        self.assertIn("scripts/tests/test_android_runtime_suites_manifest.py", android_ci)

    def test_workflow_selector_loops_do_not_use_xargs(self):
        workflows = {
            "Android CI": ANDROID_CI_WORKFLOW.read_text(),
            "Release QA": RELEASE_QA_WORKFLOW.read_text(),
        }

        for name, content in workflows.items():
            with self.subTest(workflow=name):
                self.assertNotIn("xargs", content)
                self.assertNotIn("while IFS= read -r selector", content)
                self.assertRegex(content, r"scripts/android_runtime_suites.py run-(connected|android-ci)")


if __name__ == "__main__":
    unittest.main()
