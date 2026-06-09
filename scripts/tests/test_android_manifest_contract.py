import pathlib
import unittest
import xml.etree.ElementTree as ET
from typing import cast


ANDROID_NS = "http://schemas.android.com/apk/res/android"
TOOLS_NS = "http://schemas.android.com/tools"
ANDROID_NAME = f"{{{ANDROID_NS}}}name"
ANDROID_EXPORTED = f"{{{ANDROID_NS}}}exported"
ANDROID_PERMISSION = f"{{{ANDROID_NS}}}permission"
ANDROID_RESOURCE = f"{{{ANDROID_NS}}}resource"
ANDROID_ALLOW_BACKUP = f"{{{ANDROID_NS}}}allowBackup"
ANDROID_DATA_EXTRACTION_RULES = f"{{{ANDROID_NS}}}dataExtractionRules"
ANDROID_FULL_BACKUP_CONTENT = f"{{{ANDROID_NS}}}fullBackupContent"
TOOLS_IGNORE = f"{{{TOOLS_NS}}}ignore"


class AndroidManifestContractTest(unittest.TestCase):
    def setUp(self):
        self.repo_root = pathlib.Path(__file__).resolve().parents[2]
        self.manifest_path = self.repo_root / "app" / "src" / "main" / "AndroidManifest.xml"
        self.manifest_text = self.manifest_path.read_text(encoding="utf-8")
        self.manifest = ET.parse(self.manifest_path).getroot()
        self.application = self.manifest.find("application")
        self.assertIsNotNone(self.application)

    def component_named(self, tag_name: str, component_name: str):
        application = cast(ET.Element, self.application)
        for component in application.findall(tag_name):
            if component.attrib.get(ANDROID_NAME) == component_name:
                return component
        self.fail(f"{tag_name} {component_name} not declared in AndroidManifest.xml")

    def receiver_named(self, receiver_name: str):
        return self.component_named("receiver", receiver_name)

    def service_named(self, service_name: str):
        return self.component_named("service", service_name)

    def activity_named(self, activity_name: str):
        return self.component_named("activity", activity_name)

    def uses_permission_named(self, permission_name: str):
        matches = [
            permission
            for permission in self.manifest.findall("uses-permission")
            if permission.attrib.get(ANDROID_NAME) == permission_name
        ]
        self.assertEqual(
            1,
            len(matches),
            f"Expected exactly one <uses-permission> for {permission_name}",
        )
        return matches[0]

    def intent_actions(self, component) -> set[str]:
        return {
            action.attrib.get(ANDROID_NAME)
            for intent_filter in component.findall("intent-filter")
            for action in intent_filter.findall("action")
        }

    def metadata_named(self, component, metadata_name: str):
        for metadata in component.findall("meta-data"):
            if metadata.attrib.get(ANDROID_NAME) == metadata_name:
                return metadata
        self.fail(f"meta-data {metadata_name} not declared for {component.attrib.get(ANDROID_NAME)}")

    def xml_root(self, relative_path: str):
        return ET.parse(self.repo_root / relative_path).getroot()

    def include_scope(self, parent) -> set[tuple[str | None, str | None]]:
        return {
            (include.attrib.get("domain"), include.attrib.get("path"))
            for include in parent.findall("include")
        }

    def assert_no_state_file_includes(self, includes: set[tuple[str | None, str | None]]):
        forbidden_domains = {"file", "sharedpref", "external", "root", "datastore"}
        forbidden_path_fragments = {"keep-datastore", "preferences_pb"}
        for domain, path in includes:
            self.assertNotIn(domain, forbidden_domains)
            for fragment in forbidden_path_fragments:
                self.assertNotIn(fragment, path or "")

    def test_sensitive_permissions_are_declared_for_documented_runtime_paths(self):
        for permission in (
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
        ):
            with self.subTest(permission=permission):
                self.uses_permission_named(permission)

    def test_query_all_packages_has_documented_policy_purpose(self):
        permission = self.uses_permission_named("android.permission.QUERY_ALL_PACKAGES")

        self.assertEqual("QueryAllPackagesPermission", permission.attrib.get(TOOLS_IGNORE))
        for purpose_text in (
            "app-selection picker",
            "InstalledAppRepository",
            "SelectableAppPolicy",
        ):
            with self.subTest(purpose_text=purpose_text):
                self.assertIn(purpose_text, self.manifest_text)

    def test_receiver_exported_and_action_contracts(self):
        boot_receiver = self.receiver_named(".receiver.BootReceiver")
        routine_alarm_receiver = self.receiver_named(".receiver.RoutineAlarmReceiver")

        self.assertEqual("false", boot_receiver.attrib.get(ANDROID_EXPORTED))
        self.assertEqual(
            {
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            },
            self.intent_actions(boot_receiver),
        )
        self.assertEqual("false", routine_alarm_receiver.attrib.get(ANDROID_EXPORTED))
        self.assertEqual(set(), self.intent_actions(routine_alarm_receiver))

    def test_service_exported_and_action_contracts(self):
        messaging_service = self.service_named(".service.KeepMessagingService")

        self.assertEqual("false", messaging_service.attrib.get(ANDROID_EXPORTED))
        self.assertEqual({"com.google.firebase.MESSAGING_EVENT"}, self.intent_actions(messaging_service))

    def test_block_activity_is_not_exported(self):
        block_activity = self.activity_named(".BlockActivity")

        self.assertEqual("false", block_activity.attrib.get(ANDROID_EXPORTED))

    def test_accessibility_service_requires_framework_binding_and_metadata(self):
        accessibility_service = self.service_named(".service.KeepAccessibilityService")
        metadata = self.metadata_named(accessibility_service, "android.accessibilityservice")

        self.assertEqual("true", accessibility_service.attrib.get(ANDROID_EXPORTED))
        self.assertEqual(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            accessibility_service.attrib.get(ANDROID_PERMISSION),
        )
        self.assertEqual(
            {"android.accessibilityservice.AccessibilityService"},
            self.intent_actions(accessibility_service),
        )
        self.assertEqual("@xml/accessibility_service_config", metadata.attrib.get(ANDROID_RESOURCE))

    def test_application_points_to_static_backup_rule_files(self):
        self.assertEqual("true", self.application.attrib.get(ANDROID_ALLOW_BACKUP))
        self.assertEqual("@xml/backup_rules", self.application.attrib.get(ANDROID_FULL_BACKUP_CONTENT))
        self.assertEqual("@xml/data_extraction_rules", self.application.attrib.get(ANDROID_DATA_EXTRACTION_RULES))

    def test_legacy_backup_rules_include_only_room_database(self):
        backup_rules = self.xml_root("app/src/main/res/xml/backup_rules.xml")
        includes = self.include_scope(backup_rules)

        self.assertEqual("full-backup-content", backup_rules.tag)
        self.assertEqual({("database", "keep-database")}, includes)
        self.assert_no_state_file_includes(includes)

    def test_data_extraction_rules_include_only_room_database_for_cloud_and_transfer(self):
        data_extraction_rules = self.xml_root("app/src/main/res/xml/data_extraction_rules.xml")
        cloud_backup = data_extraction_rules.find("cloud-backup")
        device_transfer = data_extraction_rules.find("device-transfer")
        self.assertIsNotNone(cloud_backup)
        self.assertIsNotNone(device_transfer)

        for parent_name, parent in (
            ("cloud-backup", cloud_backup),
            ("device-transfer", device_transfer),
        ):
            with self.subTest(parent_name=parent_name):
                includes = self.include_scope(parent)
                self.assertEqual({("database", "keep-database")}, includes)
                self.assert_no_state_file_includes(includes)

    def test_backup_policy_intentionally_omits_datastore_file_paths(self):
        for relative_path in (
            "app/src/main/res/xml/backup_rules.xml",
            "app/src/main/res/xml/data_extraction_rules.xml",
        ):
            with self.subTest(relative_path=relative_path):
                xml_text = (self.repo_root / relative_path).read_text(encoding="utf-8")
                self.assertNotIn("keep-datastore", xml_text)
                self.assertNotIn("preferences_pb", xml_text)

    def test_release_qa_runs_manifest_contract_before_release_build(self):
        workflow_text = (self.repo_root / ".github" / "workflows" / "release-qa.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn("scripts.tests.test_android_manifest_contract", workflow_text)


if __name__ == "__main__":
    unittest.main()
