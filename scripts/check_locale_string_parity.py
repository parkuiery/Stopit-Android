#!/usr/bin/env python3
"""Check localized Android string resources for key and placeholder parity.

The default source of truth is app/src/main/res/values/strings.xml. Every
values-* locale should define the same translatable string keys and keep the
same printf-style placeholders (for example %1$d or %1$s) so runtime UI does
not silently fall back or crash on localized surfaces.
"""

from __future__ import annotations

import argparse
import dataclasses
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


@dataclasses.dataclass(frozen=True, order=True)
class LocaleStringViolation:
    locale: str
    string_name: str
    problem: str
    expected: str
    actual: str

    def format(self) -> str:
        if self.problem == "missing":
            return f"{self.locale}: missing string '{self.string_name}' (expected placeholders: {self.expected or 'none'})"
        return (
            f"{self.locale}: placeholder mismatch for '{self.string_name}' "
            f"(expected: {self.expected or 'none'}, actual: {self.actual or 'none'})"
        )


def _string_text(element: ET.Element) -> str:
    return "".join(element.itertext())


def _extract_placeholders(value: str) -> tuple[str, ...]:
    return tuple(match.group(0) for match in PLACEHOLDER_RE.finditer(value) if match.group(0) != "%%")


def _load_translatable_strings(strings_xml: pathlib.Path) -> dict[str, tuple[str, ...]]:
    tree = ET.parse(strings_xml)
    strings: dict[str, tuple[str, ...]] = {}
    for element in tree.getroot().findall("string"):
        name = element.attrib.get("name")
        if not name:
            continue
        if element.attrib.get("translatable") == "false":
            continue
        strings[name] = _extract_placeholders(_string_text(element))
    return strings


def check_locale_string_parity(res_dir: pathlib.Path) -> list[LocaleStringViolation]:
    default_strings = _load_translatable_strings(res_dir / "values" / "strings.xml")
    violations: list[LocaleStringViolation] = []

    for locale_dir in sorted(res_dir.glob("values-*")):
        strings_xml = locale_dir / "strings.xml"
        if not strings_xml.exists():
            continue
        locale_strings = _load_translatable_strings(strings_xml)
        for string_name, expected_placeholders in sorted(default_strings.items()):
            if string_name not in locale_strings:
                violations.append(
                    LocaleStringViolation(
                        locale=locale_dir.name,
                        string_name=string_name,
                        problem="missing",
                        expected=", ".join(expected_placeholders),
                        actual="",
                    )
                )
                continue
            actual_placeholders = locale_strings[string_name]
            if actual_placeholders != expected_placeholders:
                violations.append(
                    LocaleStringViolation(
                        locale=locale_dir.name,
                        string_name=string_name,
                        problem="placeholder_mismatch",
                        expected=", ".join(expected_placeholders),
                        actual=", ".join(actual_placeholders),
                    )
                )
    return violations


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check Android locale string key/placeholder parity")
    parser.add_argument(
        "res_dir",
        nargs="?",
        default=pathlib.Path("app/src/main/res"),
        type=pathlib.Path,
        help="Android res directory containing values/ and values-* directories",
    )
    args = parser.parse_args(argv)

    violations = check_locale_string_parity(args.res_dir)
    if violations:
        for violation in violations:
            print(violation.format(), file=sys.stderr)
        return 1
    print("Locale string parity OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
