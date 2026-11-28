"""Tests for combination.py (bnm-7yg.8.6, R1).

  python3 -I docs/studies/combination_test.py
"""
import importlib.util
import math
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("combination", os.path.join(HERE, "combination.py"))
cb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cb)


class Pools(unittest.TestCase):
    def test_pools_read_only_live_models(self):
        F = np.array([[0.0, 2.0, 50.0]])
        live = np.array([[True, True, False]])
        self.assertAlmostEqual(cb.log_pool(F, live)[0], cb.expit(1.0))
        self.assertAlmostEqual(cb.median_pool(F, live)[0], (cb.expit(0.0) + cb.expit(2.0)) / 2)

    def test_a_row_with_no_live_model_takes_the_selected(self):
        F = np.array([[1.0]])
        live = np.array([[False]])
        self.assertTrue(math.isnan(cb.log_pool(F, live)[0]))


class Offsets(unittest.TestCase):
    def test_integrate_over_the_offsets(self):
        osz = [[(0.5, -1.0), (0.5, 1.0)]]
        e = np.array([0.0])
        self.assertAlmostEqual(cb.integrate(e, osz)[0], 0.5 * cb.expit(-1.0) + 0.5 * cb.expit(1.0))

    def test_the_mean_offset(self):
        self.assertAlmostEqual(cb.mean_offset([(0.5, -1.0), (0.5, 1.0)]), 0.0)
        self.assertAlmostEqual(cb.mean_offset([(1.0, 0.3)]), 0.3)

    def test_parse_osz(self):
        self.assertEqual(cb.parse_osz("0.25:-1.0;0.75:2.0"), [(0.25, -1.0), (0.75, 2.0)])

    def test_pools_with_offsets_reduce_to_the_mixture_of_one_model(self):
        # one live model: both pools are that model's p integrated over the offsets
        E = np.array([[0.4]])
        live = np.array([[True]])
        osz = [[(0.5, -1.0), (0.5, 1.0)]]
        want = 0.5 * cb.expit(-0.6) + 0.5 * cb.expit(1.4)
        self.assertAlmostEqual(cb.log_pool_osz(E, live, osz)[0], want)
        self.assertAlmostEqual(cb.median_pool_osz(E, live, osz)[0], want)


class Stack(unittest.TestCase):
    def test_an_offset_is_not_a_weight(self):
        rng = np.random.default_rng(4)
        n = 4000
        off = rng.normal(-2, 1, n)
        f = rng.normal(size=n)
        y = (rng.random(n) < cb.expit(off + 0.8 * f)).astype(float)
        a, b = cb.fit_stack(f[:, None], y, offset=off)
        self.assertAlmostEqual(b[0], 0.8, delta=0.12)
        self.assertAlmostEqual(a, 0.0, delta=0.12)

    def test_weights_are_never_negative(self):
        rng = np.random.default_rng(0)
        n = 3000
        good = rng.normal(size=n)
        bad = -good + rng.normal(scale=0.5, size=n)  # anti-informative
        y = (rng.random(n) < cb.expit(-2 + 1.5 * good)).astype(float)
        a, b = cb.fit_stack(np.column_stack([good, bad]), y)
        self.assertGreater(b[0], 0.5)
        self.assertEqual(b[1], 0.0)

    def test_two_independent_signals_add(self):
        rng = np.random.default_rng(1)
        n = 6000
        f1, f2 = rng.normal(size=n), rng.normal(size=n)
        y = (rng.random(n) < cb.expit(-2 + f1 + f2)).astype(float)
        a, b = cb.fit_stack(np.column_stack([f1, f2]), y)
        self.assertAlmostEqual(b[0], 1.0, delta=0.15)
        self.assertAlmostEqual(b[1], 1.0, delta=0.15)


class Causal(unittest.TestCase):
    def test_trains_only_on_rows_known_by_the_origin(self):
        origin = np.array([2000 * 12 + 11, 2001 * 12 + 11, 2003 * 12 + 11])
        known = np.array([2001 * 12 + 11, 2003 * 12 + 11, 2005 * 12 + 11])
        m = cb.train_mask(origin, known, 2003 * 12 + 11)
        self.assertEqual(list(m), [True, True, False])


class Scores(unittest.TestCase):
    def test_block_interval_resamples_years(self):
        d = np.full(200, 0.01) + np.random.default_rng(2).normal(0, 0.001, 200)
        years = np.repeat(np.arange(20), 10)
        m, lo, hi = cb.block_ci(d, years, draws=300)
        self.assertGreater(lo, 0)
        self.assertLess(lo, m)
        self.assertLess(m, hi)

    def test_an_empty_set_has_no_interval_and_beats_nothing(self):
        c = cb.block_ci(np.array([]), np.array([]))
        self.assertTrue(all(math.isnan(v) for v in c))
        self.assertFalse(cb.beats(c, float("nan")))

    def test_effective_number(self):
        self.assertAlmostEqual(cb.effective_n(np.array([0.5, 0.5, 0.0])), 2.0)
        self.assertAlmostEqual(cb.effective_n(np.array([1.0, 0.0])), 1.0)

    def test_beats_needs_log_interval_above_zero_and_brier_point(self):
        self.assertTrue(cb.beats((0.01, 0.001, 0.02), 0.001))
        self.assertFalse(cb.beats((0.01, -0.001, 0.02), 0.001))
        self.assertFalse(cb.beats((0.01, 0.001, 0.02), -0.001))


class Flags(unittest.TestCase):
    def test_flags(self):
        yes, no = (0.01, 0.001, 0.02), (0.0, -0.01, 0.01)
        base = {"stack": (no, 0.0), "log pool": (no, 0.0), "median pool": (no, 0.0),
                "reference": (no, 0.0), "own": (no, 0.0)}
        f = cb.flags(dict(base, **{"log pool": (yes, 0.001)}), stable=True, increments_zero=False, halves_differ=False)
        self.assertEqual(f, ["combination", "estimation"], "a fixed pool beats where the stack does not: both, as registered")
        f = cb.flags(dict(base, **{"stack": (yes, 0.001)}), stable=True, increments_zero=False, halves_differ=False)
        self.assertEqual(f, ["combination"])
        f = cb.flags(dict(base, **{"stack": (yes, 0.001)}), stable=False, increments_zero=False, halves_differ=False)
        self.assertEqual(f, ["estimation"])
        f = cb.flags(base, stable=True, increments_zero=True, halves_differ=False)
        self.assertEqual(f, ["inputs"])
        f = cb.flags(dict(base, reference=(yes, 0.001)), stable=True, increments_zero=True, halves_differ=False)
        self.assertEqual(f, ["target/coverage"])
        f = cb.flags(base, stable=True, increments_zero=False, halves_differ=False)
        self.assertEqual(f, ["selection holds"])


if __name__ == "__main__":
    unittest.main()
