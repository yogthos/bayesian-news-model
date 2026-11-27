"""Tests for sovereign_theory_rows.py (bnm-7yg.6).

  python3 -I docs/studies/sovereign_theory_rows_test.py
"""
import importlib.util
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("sovereign_theory_rows", os.path.join(HERE, "sovereign_theory_rows.py"))
st = importlib.util.module_from_spec(spec)
spec.loader.exec_module(st)


class TheoryRows(unittest.TestCase):
    def test_fit_recovers_a_slope(self):
        rng = np.random.default_rng(1)
        x = rng.normal(size=4000)
        y = rng.random(4000) < 1 / (1 + np.exp(-(-1.0 + 1.5 * x)))
        X = np.column_stack([np.ones(4000), x])
        b = st.fit(X, y, np.ones(4000), prior=1.0)
        self.assertAlmostEqual(b[0], -1.0, delta=0.15)
        self.assertAlmostEqual(b[1], 1.5, delta=0.15)

    def test_prior_shrinks_more_when_weights_shrink(self):
        rng = np.random.default_rng(2)
        x = rng.normal(size=400)
        y = rng.random(400) < 1 / (1 + np.exp(-(1.0 * x)))
        X = np.column_stack([np.ones(400), x])
        full = st.fit(X, y, np.ones(400), prior=1.0)
        small = st.fit(X, y, np.full(400, 0.01), prior=1.0)
        self.assertLess(abs(small[1]), abs(full[1]))

    def test_weights(self):
        w = st.weights(np.array([100, 112, 124]), 124, 1.0)
        np.testing.assert_allclose(w, [0.25, 0.5, 1.0])
        np.testing.assert_allclose(st.weights(np.array([1, 2]), 5, None), [1.0, 1.0])

    def test_auc(self):
        self.assertEqual(st.auc(np.array([2.0, 1.0]), np.array([True, False])), 1.0)
        self.assertEqual(st.auc(np.array([1.0, 1.0]), np.array([True, False])), 0.5)


if __name__ == "__main__":
    unittest.main()
