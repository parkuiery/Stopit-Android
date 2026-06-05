import pathlib
import tempfile
import textwrap
import unittest

from scripts.check_workflow_gradle_tasks import find_flavorless_gradle_task_violations


class WorkflowGradleTaskGuardTest(unittest.TestCase):
    def test_rejects_flavorless_tasks_inside_multiline_run_blocks(self):
        with tempfile.TemporaryDirectory() as tmp:
            workflow = pathlib.Path(tmp) / "bad.yml"
            workflow.write_text(
                textwrap.dedent(
                    """\
                    name: bad
                    jobs:
                      validate:
                        steps:
                          - name: bad literal run block
                            run: |
                              ./gradlew --console=plain testDebugUnitTest
                              ./gradlew assembleDebug
                          - name: bad folded run block
                            run: >-
                              ./gradlew lintDebug
                    """
                ),
                encoding="utf-8",
            )

            violations = find_flavorless_gradle_task_violations(pathlib.Path(tmp))

        self.assertEqual(
            [
                "bad.yml:7: ./gradlew --console=plain testDebugUnitTest",
                "bad.yml:8: ./gradlew assembleDebug",
                "bad.yml:11: ./gradlew lintDebug",
            ],
            [violation.format() for violation in violations],
        )

    def test_rejects_flavorless_tasks_inside_inline_run_commands(self):
        with tempfile.TemporaryDirectory() as tmp:
            workflow = pathlib.Path(tmp) / "bad-inline.yml"
            workflow.write_text(
                textwrap.dedent(
                    """\
                    name: bad
                    jobs:
                      validate:
                        steps:
                          - run: ./gradlew bundleProdRelease
                    """
                ),
                encoding="utf-8",
            )

            violations = find_flavorless_gradle_task_violations(pathlib.Path(tmp))

        self.assertEqual(
            ["bad-inline.yml:5: ./gradlew bundleProdRelease"],
            [violation.format() for violation in violations],
        )

    def test_allows_variant_specific_app_and_module_qualified_tasks(self):
        with tempfile.TemporaryDirectory() as tmp:
            workflow = pathlib.Path(tmp) / "good.yml"
            workflow.write_text(
                textwrap.dedent(
                    """\
                    name: good
                    jobs:
                      validate:
                        steps:
                          - run: ./gradlew :app:testDevDebugUnitTest :app:assembleProdDebug
                          - run: |
                              ./gradlew --console=plain :app:lintDevDebug
                              ./gradlew :core:kds:testDebugUnitTest
                    """
                ),
                encoding="utf-8",
            )

            violations = find_flavorless_gradle_task_violations(pathlib.Path(tmp))

        self.assertEqual([], violations)

    def test_ignores_branch_hygiene_self_check_workflow(self):
        with tempfile.TemporaryDirectory() as tmp:
            workflow = pathlib.Path(tmp) / "branch-hygiene.yml"
            workflow.write_text("run: ./gradlew testDebugUnitTest\n", encoding="utf-8")

            violations = find_flavorless_gradle_task_violations(pathlib.Path(tmp))

        self.assertEqual([], violations)


if __name__ == "__main__":
    unittest.main()
