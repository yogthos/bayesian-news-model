"""Tests for fiscal_discretionary.py (bnm-7yg.6).

  python3 -I docs/studies/fiscal_discretionary_test.py
"""
import importlib.util
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("fiscal_discretionary", os.path.join(HERE, "fiscal_discretionary.py"))
fd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fd)


class Filter(unittest.TestCase):
    def test_hp_keeps_a_line(self):
        y = np.arange(30, dtype=float) * 0.02
        np.testing.assert_allclose(fd.hp(y, 100.0), y, atol=1e-9)

    def test_gap_of_a_bump(self):
        g = {y: 2.0 for y in range(1980, 2011)}
        g[1995] = 8.0
        gap = fd.output_gap(g)
        self.assertGreater(gap[1995], gap[1994])
        self.assertGreater(gap[1995], 0)
        self.assertAlmostEqual(sum(gap.values()) / len(gap), 0.0, delta=0.5)

    def test_gap_breaks_at_missing_years(self):
        g = {y: 2.0 for y in list(range(1980, 1990)) + list(range(1992, 2005))}
        gap = fd.output_gap(g)
        self.assertNotIn(1990, gap)
        self.assertIn(2000, gap)


class Labels(unittest.TestCase):
    def test_once_in_three(self):
        self.assertEqual(fd.once_in(3, [2000, 2001, 2002, 2003, 2010]), [2000, 2003, 2010])

    def test_current_rule(self):
        bal = {2000: -5.0, 2001: -3.4, 2002: -3.0}
        exp = {2000: 40.0, 2001: 38.9, 2002: 38.0}
        self.assertEqual(fd.current(bal, exp), [2001])

    def test_recovery_year_is_cyclical_not_discretionary(self):
        # the balance improves 2 points only because the gap closes 4 points
        bal = {2000: -5.0, 2001: -3.0}
        gap = {2000: -4.0, 2001: 0.0}
        rexp = {2000: 100.0, 2001: 99.0}
        self.assertEqual(fd.d1(bal, gap), [])
        self.assertEqual(fd.d2(bal, gap, rexp), [])

    def test_discretionary_at_a_closed_gap(self):
        bal = {2000: -5.0, 2001: -3.0}
        gap = {2000: 0.0, 2001: 0.0}
        self.assertEqual(fd.d1(bal, gap), [2001])
        self.assertEqual(fd.d2(bal, gap, {2000: 100.0, 2001: 99.0}), [2001])
        self.assertEqual(fd.d2(bal, gap, {2000: 100.0, 2001: 101.0}), [], "spending grew: not spending-led")


class Scores(unittest.TestCase):
    def test_auc(self):
        self.assertEqual(fd.auc(np.array([2.0, 1.0]), np.array([True, False])), 1.0)


if __name__ == "__main__":
    unittest.main()
