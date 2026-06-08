import importlib.util
import pathlib
import sys
import tempfile
import textwrap
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULE_PATH = REPO_ROOT / "scripts" / "check_compose_icon_button_accessibility.py"
spec = importlib.util.spec_from_file_location("check_compose_icon_button_accessibility", MODULE_PATH)
assert spec is not None
assert spec.loader is not None
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class ComposeIconButtonAccessibilityTest(unittest.TestCase):
    def test_reports_icon_button_with_null_icon_content_description(self):
        with tempfile.TemporaryDirectory() as tmp:
            source_dir = pathlib.Path(tmp)
            screen = source_dir / "ExampleScreen.kt"
            screen.write_text(
                textwrap.dedent(
                    """
                    package com.example

                    @Composable
                    fun ExampleScreen(onNavigateBack: () -> Unit) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                                contentDescription = null,
                            )
                        }
                    }
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.find_icon_button_accessibility_violations(source_dir)

        self.assertEqual(
            violations,
            [
                module.IconButtonAccessibilityViolation(
                    path=screen,
                    line=5,
                    problem="icon_content_description_null",
                )
            ],
        )

    def test_allows_decorative_icon_outside_icon_button(self):
        with tempfile.TemporaryDirectory() as tmp:
            source_dir = pathlib.Path(tmp)
            (source_dir / "DecorativeIcon.kt").write_text(
                textwrap.dedent(
                    """
                    @Composable
                    fun DecorativeIcon() {
                        Icon(
                            painter = painterResource(R.drawable.shield),
                            contentDescription = null,
                        )
                    }
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.find_icon_button_accessibility_violations(source_dir)

        self.assertEqual(violations, [])

    def test_allows_labeled_icon_button(self):
        with tempfile.TemporaryDirectory() as tmp:
            source_dir = pathlib.Path(tmp)
            (source_dir / "LabeledIconButton.kt").write_text(
                textwrap.dedent(
                    """
                    @Composable
                    fun LabeledIconButton(onAdd: () -> Unit) {
                        IconButton(onClick = onAdd) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = stringResource(R.string.cd_routine_add),
                            )
                        }
                    }
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.find_icon_button_accessibility_violations(source_dir)

        self.assertEqual(violations, [])

    def test_main_source_has_no_unlabeled_icon_button_icons(self):
        source_dir = REPO_ROOT / "app" / "src" / "main" / "java"

        violations = module.find_icon_button_accessibility_violations(source_dir)

        self.assertEqual(violations, [])

    def test_reports_clickable_surface_missing_role_or_state_semantics(self):
        with tempfile.TemporaryDirectory() as tmp:
            source_dir = pathlib.Path(tmp)
            screen = source_dir / "ClickableSurface.kt"
            screen.write_text(
                textwrap.dedent(
                    """
                    @Composable
                    fun ClickableSurface(onClick: () -> Unit) {
                        Box(
                            modifier = Modifier
                                .clickable(onClick = onClick)
                        )
                    }
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.find_clickable_semantics_violations(source_dir)

        self.assertEqual(
            violations,
            [
                module.ClickableSemanticsViolation(
                    path=screen,
                    line=5,
                    problem="clickable_missing_role_or_state_semantics",
                )
            ],
        )

    def test_allows_clickable_surface_with_role_and_selected_state_semantics(self):
        with tempfile.TemporaryDirectory() as tmp:
            source_dir = pathlib.Path(tmp)
            (source_dir / "AccessibleClickableSurface.kt").write_text(
                textwrap.dedent(
                    """
                    @Composable
                    fun AccessibleClickableSurface(onClick: () -> Unit, selected: Boolean) {
                        Box(
                            modifier = Modifier
                                .semantics {
                                    role = Role.Tab
                                    this.selected = selected
                                    stateDescription = if (selected) "Selected" else "Not selected"
                                }
                                .clickable(onClick = onClick)
                        )
                    }
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.find_clickable_semantics_violations(source_dir)

        self.assertEqual(violations, [])

    def test_main_source_has_accessible_clickable_semantics_for_guarded_surfaces(self):
        source_dir = REPO_ROOT / "app" / "src" / "main" / "java"

        violations = module.find_clickable_semantics_violations(source_dir)

        self.assertEqual(violations, [])

    def test_reports_hardcoded_english_state_description_literals(self):
        with tempfile.TemporaryDirectory() as tmp:
            source_dir = pathlib.Path(tmp)
            screen = source_dir / "LocalizedStateDescription.kt"
            screen.write_text(
                textwrap.dedent(
                    """
                    @Composable
                    fun LocalizedStateDescription(selected: Boolean) {
                        Box(
                            modifier = Modifier.semantics {
                                stateDescription = if (selected) "Selected" else "Not selected"
                            }
                        )
                    }
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.find_hardcoded_state_description_violations(source_dir)

        self.assertEqual(
            violations,
            [
                module.StateDescriptionLocalizationViolation(
                    path=screen,
                    line=5,
                    problem="hardcoded_english_state_description",
                )
            ],
        )

    def test_allows_string_resource_state_description(self):
        with tempfile.TemporaryDirectory() as tmp:
            source_dir = pathlib.Path(tmp)
            (source_dir / "LocalizedStateDescription.kt").write_text(
                textwrap.dedent(
                    """
                    @Composable
                    fun LocalizedStateDescription(selected: Boolean) {
                        val selectedState = stringResource(R.string.cd_tab_selected)
                        val notSelectedState = stringResource(R.string.cd_tab_not_selected)
                        Box(
                            modifier = Modifier.semantics {
                                stateDescription = if (selected) selectedState else notSelectedState
                            }
                        )
                    }
                    """
                ).strip(),
                encoding="utf-8",
            )

            violations = module.find_hardcoded_state_description_violations(source_dir)

        self.assertEqual(violations, [])

    def test_main_source_has_no_hardcoded_english_state_descriptions(self):
        source_dir = REPO_ROOT / "app" / "src" / "main" / "java"

        violations = module.find_hardcoded_state_description_violations(source_dir)

        self.assertEqual(violations, [])


if __name__ == "__main__":
    unittest.main()
