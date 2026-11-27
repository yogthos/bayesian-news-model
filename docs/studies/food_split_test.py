"""Tests for food_split.py (bnm-7yg.6).

  python3 -I docs/studies/food_split_test.py
"""
import importlib.util
import math
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("food_split", os.path.join(HERE, "food_split.py"))
fs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fs)


class FoodSplit(unittest.TestCase):
    def test_month_index(self):
        self.assertEqual(fs.month_index("2003-12-31"), 2003 * 12 + 11)

    def test_change_reads_only_what_is_published(self):
        # periods 0..24, value e^(p/12), each published 2 months after its period
        s = [(p, p + 2, math.exp(p / 12.0)) for p in range(25)]
        # at t = 20 the latest published period is 18; its change over 12 months is log(e^(18/12)/e^(6/12)) = 1
        self.assertAlmostEqual(fs.change(s, 20, 12), 1.0)
        self.assertIsNone(fs.change(s, 13, 12), msg="period 11 has no period -1")

    def test_oracle_shift_matches_the_rate(self):
        ps = [0.1, 0.2, 0.3]
        a = fs.shift_to_rate(ps, 0.5)
        self.assertAlmostEqual(sum(fs.shifted(ps, a)) / 3, 0.5, places=6)

    def test_level_fit_recovers_a_line(self):
        rows = [(x, 0.5 + 2.0 * x) for x in [-1.0, 0.0, 1.0, 2.0]]
        a, b = fs.level_fit(rows, ridge=0.0)
        self.assertAlmostEqual(a, 0.5)
        self.assertAlmostEqual(b, 2.0)

    def test_level_fit_shrinks_with_few_rows(self):
        a, b = fs.level_fit([], ridge=1.0)
        self.assertEqual((a, b), (0.0, 0.0))

    def test_excess_share(self):
        # every origin at the same rate: no level variance beyond binomial
        by = {o: [(0.1, y) for y in [1] + [0] * 9] for o in range(5)}
        self.assertLessEqual(fs.excess_share(by), 0.0 + 1e-12)
        # origins all-or-nothing: all the variance is level
        by = {o: [(0.5, o % 2)] * 10 for o in range(6)}
        self.assertGreater(fs.excess_share(by), 0.8)


if __name__ == "__main__":
    unittest.main()
