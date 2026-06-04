#!/usr/bin/env python3
"""Static guard for clickable Compose IconButton accessibility labels.

The guard is intentionally narrow: it flags `IconButton { Icon(... contentDescription = null) }`
blocks because icon-only buttons need an accessible label. Decorative icons outside of an
IconButton may still use `contentDescription = null`.
"""

from __future__ import annotations

from dataclasses import dataclass
import pathlib
import re
import sys


@dataclass(frozen=True)
class IconButtonAccessibilityViolation:
    path: pathlib.Path
    line: int
    problem: str


@dataclass(frozen=True)
class ClickableSemanticsViolation:
    path: pathlib.Path
    line: int
    problem: str


ICON_BUTTON_TOKEN = "IconButton"
NULL_CONTENT_DESCRIPTION = "contentDescription = null"
GUARDED_CLICKABLE_PATHS = {
    pathlib.Path("app/src/main/java/com/uiery/keep/feature/lockhistory/component/LockHistoryTab.kt"),
    pathlib.Path("app/src/main/java/com/uiery/keep/feature/lockhistory/component/LockHistoryWeekCalendar.kt"),
    pathlib.Path("app/src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt"),
    pathlib.Path("app/src/main/java/com/uiery/keep/feature/menu/component/MenuItem.kt"),
    pathlib.Path("app/src/main/java/com/uiery/keep/feature/menu/component/MenuToggleItem.kt"),
}


def _is_icon_button_start(line: str) -> bool:
    stripped = line.strip()
    return (
        f"{ICON_BUTTON_TOKEN}(" in stripped
        or stripped.startswith(f"{ICON_BUTTON_TOKEN}(")
        or stripped.startswith(f"{ICON_BUTTON_TOKEN} ")
    )


def find_icon_button_accessibility_violations(source_dir: pathlib.Path) -> list[IconButtonAccessibilityViolation]:
    violations: list[IconButtonAccessibilityViolation] = []
    for path in sorted(source_dir.rglob("*.kt")):
        lines = path.read_text(encoding="utf-8").splitlines()
        index = 0
        while index < len(lines):
            line = lines[index]
            if not _is_icon_button_start(line):
                index += 1
                continue

            start_line = index + 1
            body_start = index
            while body_start < len(lines):
                stripped = lines[body_start].strip()
                if ") {" in stripped or stripped.endswith("{"):
                    break
                body_start += 1

            depth = 0
            cursor = body_start
            block_lines: list[str] = []
            while cursor < len(lines):
                current = lines[cursor]
                block_lines.append(current)
                depth += current.count("{") - current.count("}")
                cursor += 1
                if depth <= 0:
                    break

            block_text = "\n".join(block_lines)
            if "Icon(" in block_text and NULL_CONTENT_DESCRIPTION in block_text:
                violations.append(
                    IconButtonAccessibilityViolation(
                        path=path,
                        line=start_line,
                        problem="icon_content_description_null",
                    )
                )
            index = max(cursor, index + 1)
    return violations


def _strip_block_comments(source: str) -> str:
    return re.sub(r"/\*.*?\*/", lambda match: "\n" * match.group(0).count("\n"), source, flags=re.DOTALL)


def _iter_clickable_guard_paths(source_dir: pathlib.Path) -> list[pathlib.Path]:
    root = source_dir.resolve()
    if root.name == "java" and root.parts[-3:] == ("src", "main", "java"):
        repo_root = root.parents[3]
        return [repo_root / path for path in sorted(GUARDED_CLICKABLE_PATHS)]
    return sorted(source_dir.rglob("*.kt"))


def _has_accessible_semantics(lines: list[str], line_index: int) -> bool:
    start = max(0, line_index - 10)
    end = min(len(lines), line_index + 4)
    modifier_context = "\n".join(lines[start:end])
    has_role = "role = Role." in modifier_context or "role =" in modifier_context
    has_state = (
        "stateDescription" in modifier_context
        or "selected =" in modifier_context
        or "this.selected" in modifier_context
        or "toggleable(" in modifier_context
        or "selectable(" in modifier_context
    )
    return has_role or has_state


def find_clickable_semantics_violations(source_dir: pathlib.Path) -> list[ClickableSemanticsViolation]:
    violations: list[ClickableSemanticsViolation] = []
    for path in _iter_clickable_guard_paths(source_dir):
        if not path.exists():
            continue
        source = _strip_block_comments(path.read_text(encoding="utf-8"))
        lines = source.splitlines()
        for index, line in enumerate(lines):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("import "):
                continue
            if ".clickable" not in stripped:
                continue
            if _has_accessible_semantics(lines, index):
                continue
            violations.append(
                ClickableSemanticsViolation(
                    path=path,
                    line=index + 1,
                    problem="clickable_missing_role_or_state_semantics",
                )
            )
    return violations


def main(argv: list[str] | None = None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    source_dir = pathlib.Path(argv[0]) if argv else pathlib.Path("app/src/main/java")
    icon_button_violations = find_icon_button_accessibility_violations(source_dir)
    clickable_semantics_violations = find_clickable_semantics_violations(source_dir)
    for violation in icon_button_violations:
        print(f"{violation.path}:{violation.line}: {violation.problem}")
    for violation in clickable_semantics_violations:
        print(f"{violation.path}:{violation.line}: {violation.problem}")
    return 1 if icon_button_violations or clickable_semantics_violations else 0


if __name__ == "__main__":
    raise SystemExit(main())
