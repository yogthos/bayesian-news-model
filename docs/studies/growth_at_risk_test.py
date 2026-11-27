"""Tests for growth_at_risk.py (bnm-7yg.6).

  python3 -I docs/studies/growth_at_risk_test.py
"""
import importlib.util
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("growth_at_risk", os.path.join(HERE, "growth_at_risk.py"))
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)


class QuantileRegression(unittest.TestCase):
    def test_recovers_a_conditional_quantile(self):
        rng = np.random.default_rng(1)
        x = rng.uniform(0, 2, 6000)
        y = 1.0 + 0.5 * x + (0.2 + 0.4 * x) * rng.normal(size=6000)
        b = g.qreg(x[:, None], y, 0.1)
        z = -1.2815515655446004
        self.assertAlmostEqual(b[0], 1.0 + 0.2 * z, delta=0.06)
        self.assertAlmostEqual(b[1], 0.5 + 0.4 * z, delta=0.06)

    def test_pinball(self):
        self.assertAlmostEqual(g.pinball(np.array([1.0]), np.array([0.0]), 0.1), 0.1)
        self.assertAlmostEqual(g.pinball(np.array([0.0]), np.array([1.0]), 0.1), 0.9)


class Timing(unittest.TestCase):
    def test_quarter_index_round_trip(self):
        self.assertEqual(g.qindex("2003Q1"), 2003 * 4)
        self.assertEqual(g.qindex("2003Q4"), 2003 * 4 + 3)

    def test_monthly_value_at_quarter_end(self):
        s = {g.mindex("2003-03-31"): 1.0, g.mindex("2003-06-30"): 2.0}
        self.assertEqual(g.at_quarter_end(s, 2003 * 4), 1.0)
        self.assertEqual(g.at_quarter_end(s, 2003 * 4 + 1), 2.0)
        self.assertIsNone(g.at_quarter_end(s, 2002 * 4))

    def test_credit_gap_two_quarters_back(self):
        s = {2002 * 4 + 3: 5.0, 2003 * 4: 6.0}
        self.assertEqual(g.lagged(s, 2003 * 4 + 1, 2), 5.0)
        self.assertEqual(g.lagged(s, 2003 * 4 + 2, 2), 6.0)
        self.assertIsNone(g.lagged(s, 2002 * 4 + 3, 2))

    def test_features_use_only_known_growth(self):
        gr = {q: float(q) for q in range(2000 * 4, 2004 * 4)}
        f = g.features(gr, {}, {}, 2003 * 4)
        self.assertEqual(f[0], float(2003 * 4 - 1))
        self.assertAlmostEqual(f[1], np.std([float(q) for q in range(2003 * 4 - 12, 2003 * 4)]))


class Bootstrap(unittest.TestCase):
    def test_interval_contains_a_clear_gain(self):
        d = np.full(400, 0.01) + np.random.default_rng(2).normal(0, 0.001, 400)
        years = np.repeat(np.arange(20), 20)
        m, lo, hi = g.block_ci(d, years, draws=500)
        self.assertGreater(lo, 0)
        self.assertLess(lo, m)
        self.assertLess(m, hi)


if __name__ == "__main__":
    unittest.main()
