#!/usr/bin/env python3
"""Static guard for clickable Compose IconButton accessibility labels.

The guard is intentionally narrow: it flags `IconButton { Icon(... contentDescription = null) }`
blocks because icon-only buttons need an accessible label. Decorative icons outside of an
IconButton may still use `contentDescription = null`.
"""

from __future__ import annotations

from dataclasses import dataclass
import pathlib
import sys


@dataclass(frozen=True)
class IconButtonAccessibilityViolation:
    path: pathlib.Path
    line: int
    problem: str


ICON_BUTTON_TOKEN = "IconButton"
NULL_CONTENT_DESCRIPTION = "contentDescription = null"


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


def main(argv: list[str] | None = None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    source_dir = pathlib.Path(argv[0]) if argv else pathlib.Path("app/src/main/java")
    violations = find_icon_button_accessibility_violations(source_dir)
    for violation in violations:
        print(f"{violation.path}:{violation.line}: {violation.problem}")
    return 1 if violations else 0


if __name__ == "__main__":
    raise SystemExit(main())
