"""Tests for reference.py (bnm-7yg.2).

  python3 -I docs/studies/reference_test.py
"""
import importlib.util
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("reference", os.path.join(HERE, "reference.py"))
rf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rf)


def o(y):
    return 12 * y + 11


class Fit(unittest.TestCase):
    def test_recovers_a_strong_signal_and_shrinks_noise(self):
        rng = np.random.default_rng(0)
        X = rng.normal(size=(4000, 2))
        y = (rng.random(4000) < 1 / (1 + np.exp(-(-2 + 1.5 * X[:, 0])))).astype(float)
        b0, b = rf.fit(X, y)
        self.assertAlmostEqual(-2.0, b0, delta=0.2)
        self.assertAlmostEqual(1.5, b[0], delta=0.2)
        self.assertLess(abs(b[1]), 0.15)

    def test_standardize_fills_missing_with_the_mean(self):
        tr = np.array([[1.0, 5.0], [3.0, 5.0], [np.nan, 5.0]])
        a, b = rf.standardize(tr, np.array([[np.nan, 7.0]]))
        self.assertEqual([-1.0, 1.0, 0.0], list(a[:, 0]))
        self.assertEqual(0.0, b[0, 0])
        self.assertEqual(2.0, b[0, 1], "a constant column keeps scale 1")


class Causal(unittest.TestCase):
    def test_known_counts_only_outcomes_known_by_s(self):
        rows = [("A", o(2000), True, o(2003), []), ("A", o(2001), False, o(2004), []),
                ("B", o(2000), False, o(2003), [])]
        kn = rf.Known(rows)
        self.assertEqual(0.5, kn.base(o(2002)))
        self.assertAlmostEqual((1 + 0.5) / 3, kn.base(o(2003)))
        b = kn.base(o(2004))
        self.assertAlmostEqual((1 + rf.SHRINK * b) / (2 + rf.SHRINK), kn.own("A", o(2004)))
        self.assertAlmostEqual(b, kn.own("Z", o(2004)))

    def test_an_origin_trains_only_on_rows_known_by_it(self):
        # the signal flips after 2005; rows known later must not reach the 2004 fit
        rows = []
        for yr in range(1990, 2010):
            for i in range(40):
                x = float(i % 2)
                y = (x == 1.0) if yr < 2004 else (x == 0.0)
                rows.append((f"P{i}", o(yr), y, o(yr) + 36, [x]))
        rf.FROM, rf.TO = 2004, 2004
        fs = rf.forecasts(rows)["ref"]
        rf.FROM, rf.TO = 2003, 2022
        hi = [p for u, _, p, _ in fs if int(u[1:]) % 2 == 1]
        lo = [p for u, _, p, _ in fs if int(u[1:]) % 2 == 0]
        self.assertGreater(min(hi), max(lo), "2004 still reads the pre-2004 relation")


if __name__ == "__main__":
    unittest.main()
