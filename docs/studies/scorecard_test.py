"""Tests for scorecard.py (bnm-7yg.1).

  python3 -I docs/studies/scorecard_test.py
"""
import importlib.util
import math
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("scorecard", os.path.join(HERE, "scorecard.py"))
sc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sc)


def o(y):  # a December origin
    return 12 * y + 11


class Ranking(unittest.TestCase):
    def test_auc(self):
        self.assertEqual(1.0, sc.auc([(0.9, True), (0.1, False)]))
        self.assertEqual(0.5, sc.auc([(0.5, True), (0.5, False)]))
        self.assertTrue(math.isnan(sc.auc([(0.5, True)])))

    def test_within_origin_auc_pairs_only_rows_of_one_origin(self):
        # each origin ranks perfectly, but origin 2 is lower overall: pooled < 1, within = 1
        rows = [(o(1), "A", 0.9, True), (o(1), "B", 0.5, False),
                (o(2), "A", 0.4, True), (o(2), "B", 0.1, False)]
        self.assertEqual(1.0, sc.within_auc(rows))
        self.assertLess(sc.auc([(p, y) for _, _, p, y in rows]), 1.0)

    def test_average_precision(self):
        self.assertEqual(1.0, sc.avg_precision([(0.9, True), (0.8, True), (0.1, False)]))
        # ranked: F (0.9), T (0.8): precision at the one hit 1/2
        self.assertEqual(0.5, sc.avg_precision([(0.9, False), (0.8, True)]))


class Baselines(unittest.TestCase):
    def setUp(self):
        # A has an onset every year, B only in 2000; origins 2000-2010
        self.hist = {("A", o(y)): True for y in range(2000, 2011)}
        self.hist.update({("B", o(y)): y == 2000 for y in range(2000, 2011)})

    def test_climatology_reads_only_resolved_origins(self):
        # at 2010 the resolved origins are those up to 2006 (48 months back)
        c = sc.climatology(self.hist, o(2010))
        self.assertAlmostEqual((8 + 0.5) / (14 + 1), c)  # 2000-2006: A 7 onsets, B 1
        self.assertEqual(0.5, sc.climatology(self.hist, o(2002)), "nothing resolved: the even prior")

    def test_own_history_shrinks_to_climatology(self):
        c = sc.climatology(self.hist, o(2010))
        a = sc.own_history(self.hist, "A", o(2010), c)
        b = sc.own_history(self.hist, "B", o(2010), c)
        self.assertAlmostEqual((7 + sc.SHRINK * c) / (7 + sc.SHRINK), a)
        self.assertAlmostEqual((1 + sc.SHRINK * c) / (7 + sc.SHRINK), b)
        self.assertAlmostEqual(c, sc.own_history(self.hist, "C", o(2010), c), msg="no history: climatology")


class FastBaselines(unittest.TestCase):
    def test_baselines_match_the_plain_definitions(self):
        rows = {(u, o(y)): (0.1, (y + len(u)) % 3 == 0) for u in ["A", "BB"[:1] + "B", "C"] for y in range(1995, 2010)}
        sc.FROM, sc.TO = 2003, 2009
        clim, own = sc.baselines(rows)
        hist = {k: y for k, (_, y) in rows.items()}
        for (u, t), (p, _) in own.items():
            c = sc.climatology(hist, t)
            self.assertAlmostEqual(c, clim[(u, t)][0])
            self.assertAlmostEqual(sc.own_history(hist, u, t, c), p)
        self.assertEqual(3 * 7, len(own))
        sc.FROM, sc.TO = 2003, 2022


class SameRows(unittest.TestCase):
    def test_metrics_score_only_the_given_keys(self):
        clim = {("A", o(2005)): (0.1, True), ("B", o(2005)): (0.1, False), ("C", o(2005)): (0.1, False)}
        run = {("A", o(2005)): (0.9, True), ("B", o(2005)): (0.1, False)}
        m = sc.metrics(run, clim, clim, [("A", o(2005)), ("B", o(2005))])
        self.assertEqual(2, m["rows"])
        self.assertEqual(1.0, m["auc"])


class Decisions(unittest.TestCase):
    def test_recall_at_budget_alerts_the_top_share_of_each_origin(self):
        rows = [(o(1), u, p, y) for u, p, y in
                [("A", 0.9, True), ("B", 0.8, False), ("C", 0.1, True), ("D", 0.0, False)]]
        self.assertEqual(0.5, sc.recall_at_budget(rows, 0.25))   # alerts A only
        self.assertEqual(0.5, sc.recall_at_budget(rows, 0.5))    # A and B
        self.assertEqual(1.0, sc.recall_at_budget(rows, 0.75))   # A, B, C

    def test_cost_at_threshold(self):
        # one hit, one miss (cost 4), one false alarm (cost 1), one quiet: 5 / 4
        rows = [(0.5, True), (0.1, True), (0.3, False), (0.0, False)]
        self.assertEqual(5 / 4, sc.cost(rows, 0.2, 4))

    def test_skill_is_one_less_the_ratio(self):
        self.assertAlmostEqual(0.5, sc.skill(0.1, 0.2))
        self.assertAlmostEqual(-1.0, sc.skill(0.4, 0.2))


if __name__ == "__main__":
    unittest.main()
