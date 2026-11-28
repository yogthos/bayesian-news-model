"""Tests for sovereign_split.py (bnm-7yg.6).

  python3 -I docs/studies/sovereign_split_test.py
"""
import importlib.util
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("sovereign_split", os.path.join(HERE, "sovereign_split.py"))
ss = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ss)


class SovereignSplit(unittest.TestCase):
    def test_group(self):
        self.assertEqual(ss.group(None), "unknown")
        self.assertEqual(ss.group(49.9), "low")
        self.assertEqual(ss.group(50.0), "high")

    def test_features_read_only_what_is_published(self):
        series = {("ARG", "debt/external_gni"): [(100, 110, 40.0), (112, 130, 70.0)]}
        x = ss.features(series, "ARG", 120)
        self.assertEqual(x[ss.VARS.index("debt/external_gni")], 40.0, msg="the later period is not out by 120")
        self.assertTrue(np.isnan(x[ss.VARS.index("debt/reserves_months")]))

    def test_split_falls_back_to_pooled_when_a_group_is_thin(self):
        rng = np.random.default_rng(0)
        X = rng.normal(size=(200, 2))
        y = (X[:, 0] + rng.normal(size=200) > 1.5).astype(float)
        groups = np.array(["low"] * 199 + ["high"])
        Xte = rng.normal(size=(5, 2))
        pooled = ss.predict(X, y, Xte)
        split = ss.predict_split(X, y, groups, Xte, np.array(["high"] * 5))
        np.testing.assert_allclose(split, pooled)

    def test_split_fits_each_group_on_its_own_rows(self):
        rng = np.random.default_rng(1)
        n = 600
        X = rng.normal(size=(n, 1))
        groups = np.array(["low", "high"] * (n // 2))
        # the slope's sign differs by group
        z = np.where(groups == "low", 2.0, -2.0) * X[:, 0] - 1.0
        y = (rng.random(n) < 1 / (1 + np.exp(-z))).astype(float)
        Xte = np.array([[2.0], [2.0]])
        p = ss.predict_split(X, y, groups, Xte, np.array(["low", "high"]))
        self.assertGreater(p[0], 0.5)
        self.assertLess(p[1], 0.2)


class HalfLife(unittest.TestCase):
    def test_weights_halve_every_half_life(self):
        w = ss.weights(np.array([2000 * 12 + 11, 1990 * 12 + 11, 1980 * 12 + 11]), 2000 * 12 + 11, 10.0)
        np.testing.assert_allclose(w, [1.0, 0.5, 0.25])

    def test_unit_weights_match_the_reference_fit(self):
        rng = np.random.default_rng(2)
        X = rng.normal(size=(300, 2))
        y = (X[:, 0] + rng.normal(size=300) > 1.0).astype(float)
        b0, b = ss.wfit(X, y, np.ones(300))
        r0, r = ss.ref.fit(X, y)
        self.assertAlmostEqual(b0, r0, places=6)
        np.testing.assert_allclose(b, r, atol=1e-6)

    def test_history_level(self):
        """At the weighted mean of the slope terms the log odds are the
        logit of the unweighted rate."""
        X = np.array([[0.0], [1.0], [2.0], [3.0]])
        y = np.array([0.0, 0.0, 1.0, 0.0])
        w = np.array([0.1, 0.2, 0.3, 0.4])
        b = np.array([0.7])
        b0 = ss.history_intercept(X, y, w, b)
        m = float((w * (X @ b)).sum() / w.sum())
        self.assertAlmostEqual(b0 + m, np.log(0.25 / 0.75))

    def test_predict_with_half_life_runs_at_the_history_level(self):
        rng = np.random.default_rng(3)
        X = rng.normal(size=(400, 1))
        y = (rng.random(400) < 0.1).astype(float)
        p = ss.predict(X, y, np.zeros((1, 1)), w=np.linspace(0.1, 1, 400))
        self.assertAlmostEqual(float(p[0]), y.mean(), delta=0.03)


if __name__ == "__main__":
    unittest.main()
