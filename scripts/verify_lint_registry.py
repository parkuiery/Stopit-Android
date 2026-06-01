#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


class LintRegistryVerificationError(RuntimeError):
    pass


@dataclass(frozen=True)
class VerificationResult:
    report_path: Path
    matched_sections: tuple[str, ...]
    matched_identifiers: tuple[str, ...]
    matched_issue_ids: tuple[str, ...]



def read_report(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise LintRegistryVerificationError(f"Lint report not found: {path}") from exc



def verify_report(
    report_path: Path,
    *,
    required_sections: Sequence[str],
    required_identifiers: Sequence[str],
    required_issue_ids: Sequence[str],
    forbidden_texts: Sequence[str],
) -> VerificationResult:
    text = read_report(report_path)

    for forbidden in forbidden_texts:
        if forbidden in text:
            raise LintRegistryVerificationError(f"Forbidden text present: {forbidden}")

    for section in required_sections:
        if section not in text:
            raise LintRegistryVerificationError(f"Missing required section: {section}")

    for identifier in required_identifiers:
        if identifier not in text:
            raise LintRegistryVerificationError(f"Missing required identifier: {identifier}")

    for issue_id in required_issue_ids:
        if issue_id not in text:
            raise LintRegistryVerificationError(f"Missing required issue id: {issue_id}")

    return VerificationResult(
        report_path=report_path,
        matched_sections=tuple(required_sections),
        matched_identifiers=tuple(required_identifiers),
        matched_issue_ids=tuple(required_issue_ids),
    )



def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Verify that an Android lint HTML report still includes required custom lint registries and issue ids."
    )
    parser.add_argument("--report", required=True, help="Path to lint HTML report")
    parser.add_argument(
        "--require-section",
        action="append",
        default=[],
        help="Section text that must appear in the report",
    )
    parser.add_argument(
        "--require-identifier",
        action="append",
        default=[],
        help="Identifier string that must appear in the report",
    )
    parser.add_argument(
        "--require-issue-id",
        action="append",
        default=[],
        help="Lint issue id that must appear in the report",
    )
    parser.add_argument(
        "--forbid-text",
        action="append",
        default=[],
        help="Text that must not appear in the report",
    )
    return parser



def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        result = verify_report(
            Path(args.report),
            required_sections=args.require_section,
            required_identifiers=args.require_identifier,
            required_issue_ids=args.require_issue_id,
            forbidden_texts=args.forbid_text,
        )
    except LintRegistryVerificationError as exc:
        print(f"lint registry verification failed: {exc}", file=sys.stderr)
        return 1

    print(
        "lint registry verification passed: "
        f"report={result.report_path} "
        f"identifiers={','.join(result.matched_identifiers) or '-'} "
        f"issue_ids={','.join(result.matched_issue_ids) or '-'}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
