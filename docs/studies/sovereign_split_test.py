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


if __name__ == "__main__":
    unittest.main()
