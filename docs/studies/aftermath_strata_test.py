"""Tests for aftermath_strata.py (rank 0 of reports/Crisis aftermath and
trend reversal.md): rows bucketed by years since the kind's last onset.

  python3 -I docs/studies/aftermath_strata_test.py
"""
import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("aftermath_strata", os.path.join(HERE, "aftermath_strata.py"))
af = importlib.util.module_from_spec(spec)
spec.loader.exec_module(af)


class YearsSince(unittest.TestCase):
    def test_the_last_onset_at_or_before_the_origin(self):
        ons = [12 * 2000, 12 * 2008 + 8]
        self.assertEqual(4, af.years_since(ons, 12 * 2012 + 11))   # Sep 2008 -> Dec 2012: 51 months
        self.assertEqual(0, af.years_since(ons, 12 * 2008 + 11))
        self.assertEqual(8, af.years_since(ons, 12 * 2008 + 7))  # Aug 2008: the 2000 onset, 8y8m on

    def test_none_before_any_onset(self):
        self.assertIsNone(af.years_since([12 * 2010], 12 * 2005 + 11))
        self.assertIsNone(af.years_since([], 12 * 2005 + 11))


class Bucket(unittest.TestCase):
    def test_the_reports_bands(self):
        self.assertEqual("0-1", af.bucket(0))
        self.assertEqual("0-1", af.bucket(1))
        self.assertEqual("2-4", af.bucket(2))
        self.assertEqual("2-4", af.bucket(4))
        self.assertEqual("5-8", af.bucket(8))
        self.assertEqual("9+", af.bucket(9))
        self.assertEqual("never", af.bucket(None))


class Table(unittest.TestCase):
    def test_rate_and_mean_p_by_band(self):
        rows = [(0, 0.4, False), (0, 0.2, True), (None, 0.1, False), (None, 0.1, False)]
        got = af.table(rows)
        n, p, r = got["0-1"]
        self.assertEqual(2, n)
        self.assertAlmostEqual(0.3, p)
        self.assertAlmostEqual(0.5, r)
        self.assertEqual((2, 0.1, 0.0), got["never"])
        self.assertNotIn("9+", got)


if __name__ == "__main__":
    unittest.main()
