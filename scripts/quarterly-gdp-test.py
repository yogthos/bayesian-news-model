"""Tests for scripts/quarterly-gdp.py: sources are spliced by year-on-year
growth, never by level, in order OECD QNA, IMF QNEA, IFS; a level break is a
gap the next source fills; per-capita growth takes the population growth of
the year before the quarter's (bnm-56c.37, docs/GROWTH-QUARTERLY.md).

  python3 -I scripts/quarterly-gdp-test.py
"""
import csv
import importlib.util
import json
import math
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("quarterly_gdp", os.path.join(HERE, "quarterly-gdp.py"))
qg = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qg)


def q(y, k):
    return y * 4 + k - 1


class SpliceTest(unittest.TestCase):
    def test_growth_not_levels(self):
        # OECD covers 2001-2002, QNEA 2000-2003 at ten times the level:
        # the spliced growth is OECD's where it has one, QNEA's elsewhere
        oecd = {q(y, k): 100.0 * 1.02 ** (y - 2000) for y in (2001, 2002) for k in range(1, 5)}
        qnea = {q(y, k): 1000.0 * 1.05 ** (y - 2000) for y in range(2000, 2004) for k in range(1, 5)}
        out = qg.splice([("oecd-qna", {"AAA": oecd}), ("imf-qnea", {"AAA": qnea})])["AAA"]
        self.assertAlmostEqual(out[q(2002, 1)][0], math.log(1.02))
        self.assertEqual(out[q(2002, 1)][1], "oecd-qna")
        self.assertAlmostEqual(out[q(2001, 3)][0], math.log(1.05), msg="OECD has no 2000: QNEA's growth")
        self.assertEqual(out[q(2001, 3)][1], "imf-qnea")
        self.assertAlmostEqual(out[q(2003, 4)][0], math.log(1.05))
        self.assertNotIn(q(2000, 4), out, "no year before: no growth")

    def test_level_break_is_a_gap(self):
        # a rebased series (x100 in 2002Q1): its growth there is dropped and
        # the next source fills it
        a = {q(y, k): 100.0 * (100.0 if y >= 2002 else 1.0) for y in range(2000, 2004) for k in range(1, 5)}
        b = {q(y, k): 50.0 * 1.01 ** (y - 2000) for y in range(2000, 2004) for k in range(1, 5)}
        out = qg.splice([("oecd-qna", {"AAA": a}), ("imf-ifs", {"AAA": b})])["AAA"]
        self.assertEqual(out[q(2002, 2)][1], "imf-ifs")
        self.assertAlmostEqual(out[q(2002, 2)][0], math.log(1.01))
        self.assertEqual(out[q(2003, 2)][1], "oecd-qna")
        self.assertAlmostEqual(out[q(2003, 2)][0], 0.0)


class PerCapitaTest(unittest.TestCase):
    def test_the_quarters_years_population(self):
        # the annual catalogue's per-head growth is the year's; the quarter
        # takes its year's population growth (latest vintage, as every
        # outcome here), the latest earlier year's when that one is missing,
        # never a later year's
        g = {"AAA": {q(2010, 2): (0.05, "oecd-qna"), q(2011, 1): (0.01, "imf-qnea"), q(1960, 1): (0.02, "oecd-qna")}}
        pop = {"AAA": {2009: 0.02, 2010: 0.03, 1961: 0.01}}
        rows = {(r[0], r[1]): r for r in qg.per_capita(g, pop)}
        r = rows[("AAA", q(2010, 2))]
        self.assertEqual(r[3], 2010)
        self.assertAlmostEqual(r[5], 0.02)
        self.assertEqual(r[6], "oecd-qna")
        self.assertEqual(rows[("AAA", q(2011, 1))][3], 2010)
        self.assertAlmostEqual(rows[("AAA", q(2011, 1))][5], -0.02)
        self.assertNotIn(("AAA", q(1960, 1)), rows, "no population growth by the quarter's year: no row")

    def test_latest_earlier_population_when_the_years_is_missing(self):
        rows = list(qg.per_capita({"AAA": {q(2012, 1): (0.05, "imf-ifs")}}, {"AAA": {2008: 0.01, 2013: 0.5}}))
        self.assertEqual(rows[0][3], 2008)
        self.assertAlmostEqual(rows[0][5], 0.04)

    def test_population_growth_from_wdi(self):
        # WDI's GDP growth (%) less its per-capita growth: the population's
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "series.tsv")
            with open(p, "w") as f:
                f.write("AAA\teconomy/gdp_growth\ta\t2001-12-31\t2002-09-30\t5.0\n")
                f.write("AAA\teconomy/gdp_per_capita\ta\t2000-12-31\t2001-09-30\t100.0\n")
                f.write("AAA\teconomy/gdp_per_capita\ta\t2001-12-31\t2002-09-30\t102.0\n")
                f.write("AAA\teconomy/inflation\ta\t2001-12-31\t2002-09-30\t9.0\n")
            pop = qg.population_growth(p)
        self.assertAlmostEqual(pop["AAA"][2001], math.log(1.05) - math.log(1.02))


class ReadersTest(unittest.TestCase):
    def test_qnea_prefers_seasonally_adjusted(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "qnea.csv")
            with open(p, "w", newline="") as f:
                w = csv.writer(f)
                w.writerow(["COUNTRY", "S_ADJUSTMENT", "TIME_PERIOD", "OBS_VALUE"])
                for i in range(10):
                    w.writerow(["AAA", "SA", f"{2000 + i // 4}-Q{i % 4 + 1}", 10 + i])
                    w.writerow(["AAA", "NSA", f"{2000 + i // 4}-Q{i % 4 + 1}", 99])
                    w.writerow(["BBB", "NSA", f"{2000 + i // 4}-Q{i % 4 + 1}", 5 + i])
                w.writerow(["BBB", "SA", "2000-Q1", 1])
            s = qg.load_qnea(p)
        self.assertEqual(s["AAA"][q(2000, 1)], 10.0)
        self.assertEqual(s["BBB"][q(2000, 1)], 5.0, "a seasonally adjusted series under eight quarters: the unadjusted")

    def test_oecd_real_local_currency_only(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "qna.csv")
            with open(p, "w", newline="") as f:
                w = csv.writer(f)
                w.writerow(["REF_AREA", "UNIT_MEASURE", "PRICE_BASE", "TABLE_IDENTIFIER", "TIME_PERIOD", "OBS_VALUE"])
                for i in range(8):
                    w.writerow(["AAA", "XDC", "LR", "T0102", f"{2000 + i // 4}-Q{i % 4 + 1}", 100 + i])
                    w.writerow(["AAA", "XDC", "V", "T0102", f"{2000 + i // 4}-Q{i % 4 + 1}", 999])
                    w.writerow(["AAA", "USD_PPP", "LR", "T0102", f"{2000 + i // 4}-Q{i % 4 + 1}", 999])
            s = qg.load_oecd(p)
        self.assertEqual(sorted(s["AAA"].values()), [100.0 + i for i in range(8)])

    def test_ifs_two_letter_codes(self):
        with tempfile.TemporaryDirectory() as d:
            doc = {"series": {"docs": [{"dimensions": {"REF_AREA": "AL"}, "period": [f"{2000 + i // 4}-Q{i % 4 + 1}" for i in range(8)],
                                        "value": [1.0 + i for i in range(7)] + ["NA"]}]}}
            sa, nsa = os.path.join(d, "sa.json"), os.path.join(d, "nsa.json")
            json.dump(doc, open(sa, "w"))
            json.dump({"series": {"docs": []}}, open(nsa, "w"))
            s = qg.load_ifs(sa, nsa, {"AL": "ALB"})
        self.assertNotIn("ALB", s, "seven quarters with data: too short")
        self.assertEqual(qg.quarter_name(q(2020, 2)), "2020Q2")


if __name__ == "__main__":
    unittest.main()
