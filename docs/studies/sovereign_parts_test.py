"""Tests for sovereign_parts.py (bnm-7yg.6).

  python3 -I docs/studies/sovereign_parts_test.py
"""
import importlib.util
import math
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("sovereign_parts", os.path.join(HERE, "sovereign_parts.py"))
sp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sp)

TSV = ("place\tyear\ty\to\tf_base\tf_history\tw_base\tw_history\tsince-last\town10\town30\n"
       "AAA\t2010\t1\t-3.0\t-3.1\t-2.0\t0.25\t0.75\t0.5\t\t1.0\n"
       "BBB\t2010\t0\t-4.0\t-4.2\t-4.5\t0.0\t1.0\t-1\t0\t0\n")


class SovereignParts(unittest.TestCase):
    def test_read_rows(self):
        rows = sp.read_rows(TSV.splitlines())
        self.assertEqual(len(rows), 2)
        a = rows[0]
        self.assertEqual((a["place"], a["year"], a["y"]), ("AAA", 2010, True))
        self.assertEqual(a["f"], {"base": -3.1, "history": -2.0})
        self.assertEqual(a["w"], {"base": 0.25, "history": 0.75})
        self.assertTrue(math.isnan(a["h"]["own10"]))
        self.assertEqual(a["h"]["own30"], 1.0)

    def test_parts(self):
        a = sp.read_rows(TSV.splitlines())[0]
        p = sp.parts(a)
        self.assertAlmostEqual(p["o"], -3.0)
        self.assertAlmostEqual(p["departure"], 1.0)
        # issued: the weighted probability's log odds
        pr = 0.25 * sp.expit(-3.1) + 0.75 * sp.expit(-2.0)
        self.assertAlmostEqual(p["issued"], math.log(pr / (1 - pr)))

    def test_month(self):
        self.assertEqual(sp.month(2010), 12 * 2010 + 11)


if __name__ == "__main__":
    unittest.main()
