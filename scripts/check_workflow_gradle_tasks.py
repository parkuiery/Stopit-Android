#!/usr/bin/env python3
"""Reject unqualified app Gradle task calls in GitHub workflow run commands.

Stopit has dev/prod flavors and multiple Gradle modules, so workflow Gradle
calls must use explicit module-qualified app tasks such as
:app:testDevDebugUnitTest instead of root task-name inference such as
testDevDebugUnitTest or testDebugUnitTest.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import argparse
import re
import shlex
import sys


FORBIDDEN_UNQUALIFIED_APP_TASKS = {
    # Flavorless app tasks are ambiguous in a dev/prod flavored project.
    "testDebugUnitTest",
    "assembleDebug",
    "lintDebug",
    # Variant-specific app tasks still need the :app: module prefix in workflows
    # so root task inference cannot accidentally mask the intended app gate.
    "testDevDebugUnitTest",
    "lintDevDebug",
    "lintProdRelease",
    "assembleProdDebug",
    "connectedDevDebugAndroidTest",
    "testProdReleaseUnitTest",
    "bundleProdRelease",
}
_RUN_BLOCK_RE = re.compile(r"^(?P<indent>\s*)(?:-\s*)?run:\s*(?P<value>.*)$")
_GRADLEW_RE = re.compile(r"(?:^|[;&|]\s*)\./gradlew\b(?P<args>.*)")


@dataclass(frozen=True)
class GradleTaskViolation:
    path: Path
    line: int
    command: str

    def format(self) -> str:
        return f"{self.path}:{self.line}: {self.command}"


def find_flavorless_gradle_task_violations(workflow_dir: Path) -> list[GradleTaskViolation]:
    violations: list[GradleTaskViolation] = []
    for workflow in sorted(workflow_dir.glob("*.yml")) + sorted(workflow_dir.glob("*.yaml")):
        if workflow.name == "branch-hygiene.yml":
            continue
        violations.extend(_violations_in_workflow(workflow, workflow_dir))
    return violations


def _violations_in_workflow(workflow: Path, workflow_dir: Path) -> list[GradleTaskViolation]:
    lines = workflow.read_text(encoding="utf-8").splitlines()
    rel_path = workflow.relative_to(workflow_dir)
    violations: list[GradleTaskViolation] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        match = _RUN_BLOCK_RE.match(line)
        if match is None:
            index += 1
            continue

        base_indent = len(match.group("indent"))
        value = match.group("value").strip()
        if value.startswith(("|", ">")):
            index += 1
            while index < len(lines):
                block_line = lines[index]
                if block_line.strip() and _indent_width(block_line) <= base_indent:
                    break
                violations.extend(_violations_in_command(rel_path, index + 1, block_line))
                index += 1
            continue

        violations.extend(_violations_in_command(rel_path, index + 1, value))
        index += 1
    return violations


def _violations_in_command(path: Path, line_number: int, command: str) -> list[GradleTaskViolation]:
    stripped = command.strip()
    if "./gradlew" not in stripped:
        return []

    violations: list[GradleTaskViolation] = []
    for match in _GRADLEW_RE.finditer(stripped):
        if _contains_forbidden_task(match.group("args")):
            violations.append(GradleTaskViolation(path=path, line=line_number, command=stripped))
            break
    return violations


def _contains_forbidden_task(arguments: str) -> bool:
    try:
        tokens = shlex.split(arguments, comments=False, posix=True)
    except ValueError:
        tokens = arguments.split()

    for token in tokens:
        normalized = token.strip()
        if not normalized or normalized.startswith("-") or ":" in normalized:
            continue
        if normalized in FORBIDDEN_UNQUALIFIED_APP_TASKS:
            return True
    return False


def _indent_width(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "workflow_dir",
        nargs="?",
        default=".github/workflows",
        type=Path,
        help="Directory containing GitHub workflow YAML files",
    )
    args = parser.parse_args(argv)

    violations = find_flavorless_gradle_task_violations(args.workflow_dir)
    if violations:
        print("Unqualified app Gradle task found in workflow. Use module-qualified :app tasks instead.")
        for violation in violations:
            print(violation.format())
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
