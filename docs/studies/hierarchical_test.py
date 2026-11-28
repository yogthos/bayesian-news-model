"""Tests for hierarchical.py (bnm-7yg.8.7, R2).

  python3 -I docs/studies/hierarchical_test.py
"""
import importlib.util
import math
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("hierarchical", os.path.join(HERE, "hierarchical.py"))
hz = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hz)


class Groups(unittest.TestCase):
    def test_each_group_shares_a_unit_prior_variance(self):
        groups = {"a": [0, 1, 2], "b": [0, 3], "all": [0, 1, 2, 3], "base": [0]}
        v = hz.input_variances(groups, 5)
        self.assertEqual(list(v), [0.5, 0.5, 1.0, 1.0])  # x1..x4; x4 in no group

    def test_an_input_read_by_every_group_is_core(self):
        groups = {"a": [0, 1, 2], "b": [0, 1, 3], "all": [0, 1, 2, 3]}
        v = hz.input_variances(groups, 4)
        self.assertEqual(list(v), [1.0, 1.0, 1.0])

    def test_an_input_in_two_groups_takes_the_smaller_share(self):
        groups = {"a": [0, 1, 2, 3], "b": [0, 3], "c": [0, 1]}
        v = hz.input_variances(groups, 4)
        self.assertAlmostEqual(v[2], 1 / 3)


class Decompose(unittest.TestCase):
    def test_within_and_between(self):
        X = np.array([[1.0, 2.0], [3.0, 4.0], [10.0, 0.0]])
        origin = np.array([5, 5, 6])
        W, B = hz.decompose(X, origin)
        self.assertEqual(B.tolist(), [[2.0, 3.0], [2.0, 3.0], [10.0, 0.0]])
        self.assertEqual(W.tolist(), [[-1.0, -1.0], [1.0, 1.0], [0.0, 0.0]])


class Fit(unittest.TestCase):
    def test_weights_act_as_repeated_rows(self):
        rng = np.random.default_rng(0)
        n = 400
        Z = np.column_stack([np.ones(n), rng.normal(size=n)])
        y = (rng.random(n) < hz.expit(-1 + Z[:, 1])).astype(float)
        pen = np.array([0.0, 1.0])
        b2 = hz.wridge(Z, y, np.full(n, 2.0), pen)
        bd = hz.wridge(np.vstack([Z, Z]), np.r_[y, y], np.ones(2 * n), pen)
        np.testing.assert_allclose(b2, bd, atol=1e-8)

    def test_the_offset_is_not_learned(self):
        rng = np.random.default_rng(1)
        n = 5000
        off = rng.normal(-2, 1, n)
        x = rng.normal(size=n)
        y = (rng.random(n) < hz.expit(off + 0.7 * x)).astype(float)
        b = hz.wridge(np.column_stack([np.ones(n), x]), y, np.ones(n), np.array([0.0, 1e-6]), offset=off)
        self.assertAlmostEqual(b[0], 0.0, delta=0.1)
        self.assertAlmostEqual(b[1], 0.7, delta=0.1)


class Frailty(unittest.TestCase):
    def test_shifts_are_the_offsets_about_the_node_offset(self):
        osz = [[(0.5, -1.0), (0.5, 1.0)]]
        self.assertEqual(hz.shifts(osz, np.array([0.5])), [[(0.5, -1.5), (0.5, 0.5)]])

    def test_the_fit_offset_is_the_mean_shift(self):
        s = hz.fit_offset([[(0.5, -1.0), (0.5, 1.0)]])
        self.assertAlmostEqual(s[0], 0.0)


def toy(seed=2, n_per=40, years=range(1975, 2023)):
    rng = np.random.default_rng(seed)
    rows = []
    for yr in years:
        lam = rng.normal(0, 0.5)
        for i in range(n_per):
            x = rng.normal(size=3)
            rows.append((f"C{i:02d}", yr * 12 + 11, yr * 12 + 23, x, lam))
    place = [r[0] for r in rows]
    origin = np.array([r[1] for r in rows])
    known = np.array([r[2] for r in rows])
    X = np.column_stack([np.ones(len(rows)), np.array([r[3] for r in rows])])
    o = np.full(len(rows), -2.5)
    osz = [[(0.5, -2.5 + r[4] - 0.3), (0.5, -2.5 + r[4] + 0.3)] for r in rows]
    y = rng.random(len(rows)) < hz.expit(-2.5 + np.array([r[4] for r in rows]) + 0.9 * X[:, 1])
    return {"place": place, "origin": origin, "known": known, "X": X, "o": o, "osz": osz, "y": y}


class Write(unittest.TestCase):
    def test_forecasts_are_plain_numbers_and_nan_rows_are_left_out(self):
        import tempfile
        path = os.path.join(tempfile.mkdtemp(), "k.tsv")
        hz.write_forecasts(path, ["AAA", "BBB"], np.array([24011, 24011]), np.array([np.float64(0.25), np.nan]))
        with open(path) as fh:
            self.assertEqual(fh.read(), "AAA\t24011\t0.25\n")


class Challenger(unittest.TestCase):
    def test_forecasts_only_the_registered_origins(self):
        d = toy()
        p = hz.challenger(d, {"g": [0, 1], "h": [0, 2, 3]})
        yrs = d["origin"] // 12
        self.assertTrue(np.isnan(p[yrs < 2003]).all())
        self.assertFalse(np.isnan(p[(yrs >= 2003) & (yrs <= 2022)]).any())

    def test_is_causal(self):
        d = toy()
        g = {"g": [0, 1], "h": [0, 2, 3]}
        p = hz.challenger(d, g)
        o = 2010 * 12 + 11
        e = dict(d)
        e["y"] = d["y"].copy()
        e["y"][d["known"] > o] = ~d["y"][d["known"] > o]
        q = hz.challenger(e, g)
        np.testing.assert_allclose(p[d["origin"] == o], q[d["origin"] == o])

    def test_ranks_by_the_informative_input(self):
        d = toy()
        p = hz.challenger(d, {"g": [0, 1], "h": [0, 2, 3]})
        m = ~np.isnan(p)
        self.assertGreater(np.corrcoef(p[m], d["X"][m, 1])[0, 1], 0.5)


if __name__ == "__main__":
    unittest.main()
