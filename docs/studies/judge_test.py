"""Tests for judge.py (bnm-7yg.8.8, R3).

  python3 -I docs/studies/judge_test.py
"""
import importlib.util
import os
import tempfile
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("judge", os.path.join(HERE, "judge.py"))
jd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(jd)


class Costs(unittest.TestCase):
    def test_a_miss_costs_four_and_a_false_alarm_one(self):
        p = np.array([0.1, 0.3, 0.3, 0.1])
        y = np.array([True, True, False, False])
        self.assertEqual(list(jd.row_cost(p, y)), [4.0, 0.0, 1.0, 0.0])


class Align(unittest.TestCase):
    def test_reads_a_forecast_file_onto_the_rows(self):
        with tempfile.NamedTemporaryFile("w", suffix=".tsv", delete=False) as fh:
            fh.write("AAA\t24011\t0.25\nBBB\t24011\t0.5\n")
        p = jd.align(fh.name, ["BBB", "AAA", "CCC"], np.array([24011, 24011, 24011]))
        os.unlink(fh.name)
        self.assertEqual(p[:2].tolist(), [0.5, 0.25])
        self.assertTrue(np.isnan(p[2]))


def res(log_lo, brier, cost_lo, log=0.01):
    return {"log": (log, log_lo, log + 0.01), "brier": brier, "cost": (0.0, cost_lo, 0.01), "pooled log": -0.1 + log}


class Verdict(unittest.TestCase):
    def test_the_challenger_joins_when_it_passes(self):
        v = jd.verdict({"challenger": res(0.001, 0.001, -0.01)}, fallback="stack")
        self.assertEqual(v, "challenger")

    def test_a_significant_alert_cost_loss_bars_it(self):
        v = jd.verdict({"challenger": res(0.001, 0.001, 0.0001)}, fallback=None)
        self.assertIsNone(v)

    def test_the_r1_winner_joins_when_the_challenger_fails(self):
        v = jd.verdict({"challenger": res(-0.001, 0.001, -0.01), "stack": res(0.001, 0.001, -0.01)}, fallback="stack")
        self.assertEqual(v, "stack")

    def test_the_better_of_two_passing_joins(self):
        v = jd.verdict({"challenger": res(0.001, 0.001, -0.01, log=0.01),
                        "stack": res(0.001, 0.001, -0.01, log=0.02)}, fallback="stack")
        self.assertEqual(v, "stack")

    def test_nothing_joins_when_nothing_passes(self):
        v = jd.verdict({"challenger": res(-0.001, 0.001, -0.01), "stack": res(-0.001, 0.001, -0.01)}, fallback="stack")
        self.assertIsNone(v)


if __name__ == "__main__":
    unittest.main()
