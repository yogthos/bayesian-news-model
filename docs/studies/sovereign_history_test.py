"""Tests for sovereign_history.py (bnm-7yg.6).

  python3 -I docs/studies/sovereign_history_test.py
"""
import importlib.util
import math
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("sovereign_history", os.path.join(HERE, "sovereign_history.py"))
sh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sh)


def row(year, frm, last, y=False):
    return {"place": "X", "year": year, "y": y, "from": frm, "last": last}


class Coding(unittest.TestCase):
    def test_current_counts_from_coverage_when_none(self):
        r = row(2010, 12 * 1800, None)
        t = 12 * 2010 + 11
        self.assertAlmostEqual(sh.since(r, "current"), math.log(1 + (t - 12 * 1800) / 12.0))

    def test_horizon_censors_both_coverage_and_old_onsets(self):
        t = 12 * 2010 + 11
        h = 12 * 1970
        never_1800 = sh.since(row(2010, 12 * 1800, None), "horizon")
        never_1970 = sh.since(row(2010, 12 * 1970, None), "horizon")
        old_1940 = sh.since(row(2010, 12 * 1800, 12 * 1940), "horizon")
        self.assertAlmostEqual(never_1800, math.log(1 + (t - h) / 12.0))
        self.assertEqual(never_1800, never_1970)
        self.assertEqual(never_1800, old_1940)
        recent = sh.since(row(2010, 12 * 1800, 12 * 2001), "horizon")
        self.assertAlmostEqual(recent, math.log(1 + (t - 12 * 2001) / 12.0))

    def test_unknown_is_nan_with_no_onset(self):
        self.assertTrue(math.isnan(sh.since(row(2010, 12 * 1800, None), "unknown")))
        self.assertFalse(math.isnan(sh.since(row(2010, 12 * 1800, 12 * 1990), "unknown")))

    def test_auc_shorter_ranks_higher(self):
        rs = [(1.0, True), (3.0, False)]
        self.assertEqual(sh.auc_short(rs), 1.0)
        self.assertTrue(math.isnan(sh.auc_short([(1.0, True)])))


if __name__ == "__main__":
    unittest.main()
