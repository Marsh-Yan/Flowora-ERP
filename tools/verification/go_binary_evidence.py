"""Recheck GO-2026-5932 against binaries copied from the scanned image.

This is deliberately not a general vulnerability ignore mechanism. Trivy's raw
module findings and govulncheck's complete output are retained for review.
"""
import hashlib
import json
import subprocess

ADVISORY = "GO-2026-5932"
MODULE = "golang.org/x/crypto"
PACKAGE = MODULE + "/openpgp"


def stream_messages(content):
    decoder = json.JSONDecoder()
    messages = []
    remaining = content.strip()
    while remaining:
        message, end = decoder.raw_decode(remaining)
        messages.append(message)
        remaining = remaining[end:].lstrip()
    return messages


def package_absent(messages, version):
    if not messages:
        raise ValueError("Empty Go scan")
    config = messages[0].get("config", {})
    if (config.get("protocol_version") != "v1.0.0"
            or config.get("scanner_name") != "govulncheck"
            or config.get("scan_mode") != "binary"
            or config.get("scan_level") != "package"
            or config.get("db") != "https://vuln.go.dev"):
        raise ValueError("Go scan must use the official database and binary/package analysis")
    modules = [module for message in messages for module in message.get("SBOM", {}).get("modules", [])]
    if {"path": MODULE, "version": "v" + version} not in modules:
        raise ValueError("Go binary module version differs from Trivy inventory")
    advisories = [message["osv"] for message in messages if message.get("osv", {}).get("id") == ADVISORY]
    if len(advisories) != 1:
        raise ValueError("Missing or duplicate advisory in Go scan")
    affected = advisories[0].get("affected", [])
    # If the advisory changes scope, require a fresh review instead of reusing this decision.
    if not affected or any(entry.get("package", {}).get("name") != MODULE for entry in affected):
        raise ValueError("Advisory module scope changed")
    packages = [pkg.get("path", "") for entry in affected
                for pkg in entry.get("ecosystem_specific", {}).get("imports", [])]
    if not packages or any(path != PACKAGE and not path.startswith(PACKAGE + "/") for path in packages):
        raise ValueError("Advisory package scope changed")
    findings = [message["finding"] for message in messages if message.get("finding", {}).get("osv") == ADVISORY]
    if not findings or any(not finding.get("trace") for finding in findings):
        raise ValueError("Missing module finding; absence alone is not scan evidence")
    # Stripped/unreadable symbol tables produce conservative package findings in
    # govulncheck, so these remain blocking rather than being inferred unaffected.
    return not any(frame.get("package") for finding in findings for frame in finding["trace"])


def classify(report, binary_dir, evidence_dir):
    if report.get("ArtifactType") != "container_image":
        raise ValueError("Go classification requires the actual image scan")
    decisions = set()
    evidence_dir.mkdir(parents=True, exist_ok=True)
    provenance = {"image": report["ArtifactName"], "binaries": []}
    for name in ("prometheus", "promtool"):
        target = "bin/" + name
        results = [result for result in report["Results"] if result.get("Target") == target and result.get("Type") == "gobinary"]
        if len(results) != 1:
            raise ValueError("Missing Prometheus binary inventory: " + target)
        matches = [v for v in results[0].get("Vulnerabilities", [])
                   if v.get("VulnerabilityID") == ADVISORY and v.get("PkgName") == MODULE
                   and v.get("Severity") == "UNKNOWN"]
        if not matches:
            continue
        binary = binary_dir / name
        output = subprocess.run(["govulncheck", "-mode=binary", "-scan=package", "-format=json", str(binary)],
                                check=True, capture_output=True, text=True, timeout=300).stdout
        (evidence_dir / (name + "-govulncheck.json")).write_text(output, encoding="utf-8")
        messages = stream_messages(output)
        for vulnerability in matches:
            version = vulnerability["InstalledVersion"]
            absent = package_absent(messages, version)
            provenance["binaries"].append({"target": target, "sha256": hashlib.sha256(binary.read_bytes()).hexdigest(),
                                          "advisory": ADVISORY, "version": version, "package_absent": absent})
            if absent:
                decisions.add((target, ADVISORY, MODULE, version))
                print("NOT_AFFECTED", target, ADVISORY, "OpenPGP package absent from binary analysis")
    (evidence_dir / "prometheus-triage.json").write_text(json.dumps(provenance, indent=2), encoding="utf-8")
    return decisions
