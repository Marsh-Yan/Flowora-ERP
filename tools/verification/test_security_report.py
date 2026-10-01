"""Check that release gates reject absent evidence and unfixed severe findings."""
import contextlib
import io
import unittest

from security_report import verify


class SecurityReportTest(unittest.TestCase):
    def report(self, vulnerabilities=None):
        return {"SchemaVersion": 2, "Results": [{"Packages": [{"Name": "org.springframework.boot:spring-boot"}],
                                               "Vulnerabilities": vulnerabilities or []}]}

    def run_gate(self, report):
        with contextlib.redirect_stdout(io.StringIO()):
            return verify(report, "spring-boot")

    def test_zero_findings_requires_expected_inventory(self):
        self.assertEqual(self.run_gate(self.report()), 0)
        with self.assertRaises(ValueError):
            verify(self.report(), "mysql")

    def test_empty_results_are_not_clean_evidence(self):
        with self.assertRaises(ValueError):
            self.run_gate({"SchemaVersion": 2, "Results": []})

    def test_missing_inventory_is_not_clean_evidence(self):
        with self.assertRaises(ValueError):
            self.run_gate({"SchemaVersion": 2, "Results": [{"Target": "api.jar"}]})

    def test_unfixed_high_and_critical_findings_block(self):
        for severity in ("HIGH", "CRITICAL"):
            with self.subTest(severity=severity):
                self.assertEqual(self.run_gate(self.report([{"Severity": severity, "FixedVersion": ""}])), 1)

    def test_unknown_severity_blocks(self):
        self.assertEqual(self.run_gate(self.report([{"Severity": "UNKNOWN"}])), 1)

    def test_unrecognized_or_missing_severity_blocks(self):
        for vulnerability in ({"Severity": "UNRATED"}, {}):
            self.assertEqual(self.run_gate(self.report([vulnerability])), 1)

    def test_medium_is_retained_but_not_blocking(self):
        self.assertEqual(self.run_gate(self.report([{"Severity": "MEDIUM"}])), 0)

    def test_unsupported_schema_blocks(self):
        report = self.report()
        report["SchemaVersion"] = 1
        with self.assertRaises(ValueError):
            self.run_gate(report)


if __name__ == "__main__":
    unittest.main()
