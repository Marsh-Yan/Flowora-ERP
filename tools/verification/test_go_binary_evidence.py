"""Prevent incomplete or broadened Go evidence from bypassing the image gate."""
import copy
import json
import unittest

from go_binary_evidence import ADVISORY, MODULE, PACKAGE, package_absent, stream_messages


class GoBinaryEvidenceTest(unittest.TestCase):
    def evidence(self):
        return [{"config": {"protocol_version": "v1.0.0", "scanner_name": "govulncheck",
                            "scan_mode": "binary", "scan_level": "package", "db": "https://vuln.go.dev"}},
                {"SBOM": {"modules": [{"path": MODULE, "version": "v0.56.0"}]}},
                {"osv": {"id": ADVISORY, "affected": [{"package": {"name": MODULE},
                         "ecosystem_specific": {"imports": [{"path": PACKAGE}]}}]}},
                {"finding": {"osv": ADVISORY, "trace": [{"module": MODULE, "version": "v0.56.0"}]}}]

    def test_complete_package_scan_can_classify_module_only_finding(self):
        messages = self.evidence()
        self.assertTrue(package_absent(stream_messages("\n".join(map(json.dumps, messages))), "0.56.0"))

    def test_affected_package_or_stripped_binary_fallback_stays_blocking(self):
        messages = self.evidence()
        messages.append({"finding": {"osv": ADVISORY, "trace": [{"module": MODULE, "package": PACKAGE}]}})
        self.assertFalse(package_absent(messages, "0.56.0"))

    def test_module_scan_wrong_database_or_wrong_version_cannot_clear(self):
        for field, value in (("scan_level", "module"), ("scan_mode", "source"), ("db", "https://other.invalid")):
            with self.subTest(field=field):
                messages = self.evidence()
                messages[0]["config"][field] = value
                with self.assertRaises(ValueError):
                    package_absent(messages, "0.56.0")
        with self.assertRaises(ValueError):
            package_absent(self.evidence(), "0.55.0")

    def test_missing_advisory_or_findings_and_empty_scan_fail_closed(self):
        for messages in ([], self.evidence()[:2], self.evidence()[:3]):
            with self.assertRaises(ValueError):
                package_absent(messages, "0.56.0")

    def test_changed_advisory_scope_requires_review(self):
        for change in ({"path": MODULE + "/ssh"}, {}):
            messages = copy.deepcopy(self.evidence())
            messages[2]["osv"]["affected"][0]["ecosystem_specific"]["imports"] = [change]
            with self.assertRaises(ValueError):
                package_absent(messages, "0.56.0")

    def test_truncated_output_is_not_evidence(self):
        with self.assertRaises(ValueError):
            stream_messages('{"config":{}}\n{"finding":')


if __name__ == "__main__":
    unittest.main()
