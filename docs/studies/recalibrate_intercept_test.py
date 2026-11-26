"""Tests for recalibrate_intercept.py (bnm-7yg.4).

  python3 -I docs/studies/recalibrate_intercept_test.py
"""
import importlib.util
import math
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("ri", os.path.join(HERE, "recalibrate_intercept.py"))
ri = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ri)


def o(y):
    return 12 * y + 11


class Intercept(unittest.TestCase):
    def test_moves_an_under_forecast_up(self):
        # issued 0.01 everywhere, realized 0.1: the shift is toward logit(0.1) - logit(0.01)
        rows = [(ri.logit(0.01), i % 10 == 0) for i in range(5000)]
        a = ri.intercept(rows)
        self.assertAlmostEqual(ri.logit(0.1) - ri.logit(0.01), a, delta=0.05)

    def test_the_prior_holds_a_thin_sample_near_zero(self):
        self.assertLess(abs(ri.intercept([(ri.logit(0.01), True)])), 2.0)
        self.assertEqual(0.0, ri.intercept([]))


class Causal(unittest.TestCase):
    def test_a_shift_reads_only_resolved_origins_in_the_window(self):
        rows = {}
        for yr in range(1990, 2023):
            for i in range(50):
                # calibrated before 2000, then under-forecast by 10x
                rows[(f"P{i}", o(yr))] = (0.01, i < (5 if yr >= 2000 else 0) or (yr < 2000 and i == 0 and yr % 2 == 0))
        a = ri.shifts(rows)
        self.assertLess(a[o(2003)], 1.5, "2003 reads origins 1990-1999 only")
        self.assertGreater(a[o(2012)], 2.0, "2012 reads 1999-2008")
        self.assertNotIn(o(2002), a)

    def test_ranking_within_an_origin_is_unchanged(self):
        with tempfile.TemporaryDirectory() as d:
            src, dst = os.path.join(d, "a.tsv"), os.path.join(d, "b.tsv")
            with open(src, "w") as w:
                for yr in range(1995, 2010):
                    for i, p in enumerate([0.01, 0.02, 0.05]):
                        w.write(f"year\tP{i}X\t{o(yr)}\t{p}\t{'true' if i == 2 else 'false'}\n")
                w.write(f"quarter-lead\tP0X\t{o(2005)}\t0.3\tfalse\n")
            ri.recalibrate(src, dst)
            out = [l.rstrip().split("\t") for l in open(dst)]
            self.assertEqual(["quarter-lead", "P0X", str(o(2005)), "0.3", "false"], out[-1], "other rows as issued")
            ps = [float(l[3]) for l in out if l[2] == str(o(2008)) and l[0] == "year"]
            self.assertEqual(sorted(ps), ps)
            self.assertNotEqual(0.05, ps[2])


if __name__ == "__main__":
    unittest.main()
