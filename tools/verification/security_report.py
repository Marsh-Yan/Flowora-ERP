"""Fail closed on missing inventory, malformed scans or HIGH/CRITICAL findings.

All severities remain in the original JSON artifact. No unfixed CVEs are hidden.
"""
import argparse
import collections
import json
import sys
import subprocess
from pathlib import Path

from go_binary_evidence import classify


def verify(report, expected, binary_dir=None, evidence_dir=None):
    if report.get("SchemaVersion") != 2 or not report.get("Results"):
        raise ValueError("Missing Trivy results or unsupported schema")
    packages = [pkg for result in report["Results"] for pkg in result.get("Packages", [])]
    if not packages:
        raise ValueError("No package inventory; scan is not evidence of coverage")
    names = {pkg.get("Name", "") for pkg in packages}
    if not any(expected in name for name in names):
        raise ValueError(f"Expected component {expected!r} was not scanned")
    vulnerabilities = [v for result in report["Results"] for v in result.get("Vulnerabilities", [])]
    counts = collections.Counter(v.get("Severity", "UNKNOWN") for v in vulnerabilities)
    print(f"Inventory: {len(packages)} packages; findings: {dict(sorted(counts.items()))}")
    decisions = classify(report, binary_dir, evidence_dir) if binary_dir else set()
    blocking = [v for result in report["Results"] for v in result.get("Vulnerabilities", [])
                if v.get("Severity") in {"HIGH", "CRITICAL", "UNKNOWN", None}
                and (result.get("Target"), v.get("VulnerabilityID"), v.get("PkgName"),
                     v.get("InstalledVersion")) not in decisions]
    for vulnerability in blocking:
        print("BLOCK", vulnerability.get("VulnerabilityID"), vulnerability.get("PkgName"),
              vulnerability.get("InstalledVersion"), "fixed:", vulnerability.get("FixedVersion") or "unavailable")
    return 1 if blocking else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--expected", required=True, help="A component that must appear in the scanned inventory")
    parser.add_argument("--prometheus-binaries", type=Path, help="Actual image binaries for narrowly scoped OpenPGP triage")
    args = parser.parse_args()
    try:
        return verify(json.loads(args.report.read_text(encoding="utf-8-sig")), args.expected,
                      args.prometheus_binaries, args.report.parent)
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError) as error:
        print(f"BLOCK: incomplete security evidence: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
