"""Tests for scripts/episodes.py, the episode library (bead bnm-56c.5).

  python -I scripts/episodes-test.py

The rule tests use small fixtures. The library tests read the committed
resources/bnm/episodes files; the rebuild test needs data/ and is skipped
without it.
"""
import csv
import filecmp
import importlib.util
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("episodes", os.path.join(HERE, "episodes.py"))
ep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ep)
RES = ep.RES
spec = importlib.util.spec_from_file_location("rr_crises", os.path.join(HERE, "rr-crises.py"))
rr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rr)


def read(name):
    with open(os.path.join(RES, name), newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


class RulesTest(unittest.TestCase):
    def test_spike_needs_rise_and_high(self):
        flat = {t: 100.0 for t in range(0, 60)}
        up = {**flat, 60: 160.0, 61: 170.0, 62: 120.0}
        self.assertEqual(ep.spikes(up), [60], "60% on a year before, at a 36-month high; 61 is the same spike")
        self.assertEqual(ep.spikes({**flat, 60: 140.0}), [], "40% is not enough")
        gap = dict(flat)
        del gap[40]
        gap[60] = 200.0
        self.assertEqual(ep.spikes(gap), [], "a hole in the 36 months: no judgement")

    def test_spike_once_in_24_months(self):
        p = {t: 100.0 for t in range(0, 60)}
        p.update({60: 160.0})
        p.update({t: 160.0 for t in range(61, 80)})
        p.update({80: 300.0})
        p.update({t: 300.0 for t in range(81, 100)})
        p.update({100: 480.0})
        self.assertEqual(ep.spikes(p), [60, 100], "80 is within 24 months of 60; 84 is inside the run that began at 80")

    def test_gra(self):
        q = {t: 100.0 for t in range(0, 40)}
        o = {t: 0.0 for t in range(0, 20)}
        o.update({t: 30.0 for t in range(20, 40)})
        self.assertEqual(ep.gra_drawings(o, q), [20])
        o2 = {t: (10.0 if t >= 20 else 0.0) for t in range(0, 40)}
        self.assertEqual(ep.gra_drawings(o2, q), [], "10% of quota is not a large drawing")

    def test_investment_bust(self):
        lv = {1990 + i: 100.0 * 1.1 ** i for i in range(8)}  # to 1997: x1.95
        lv.update({1998: lv[1997] * 0.85, 1999: lv[1997] * 0.75, 2000: lv[1997] * 0.8})
        self.assertEqual(ep.investment_busts(lv), [(1998, 1999)],
                         "peak 1997, 25% down by 1999: onset 1998, known only in 1999")
        slow = {1990 + i: 100.0 * 1.03 ** i for i in range(8)}
        slow.update({1998: slow[1997] * 0.7})
        self.assertEqual(ep.investment_busts(slow), [], "no boom before the fall")
        mild = {**lv, 1998: lv[1997] * 0.9, 1999: lv[1997] * 0.85, 2000: lv[1997] * 0.95}
        self.assertEqual(ep.investment_busts(mild), [], "15% down is not a bust")

    def test_investment_bust_peak_is_the_peak(self):
        """A year that a later year tops before the fall is not the peak:
        the onset follows the real peak."""
        lv = {1990 + i: 100.0 * 1.1 ** i for i in range(8)}  # 1997 = 194.9
        lv.update({1998: 200.0, 1999: 150.0, 2000: 150.0})
        self.assertEqual(ep.investment_busts(lv), [(1999, 1999)], "peak 1998 (1997 is topped): onset 1999")

    def test_investment_bust_known_at_the_fall(self):
        lv = {1990 + i: 100.0 * 1.1 ** i for i in range(8)}
        lv.update({1998: lv[1997] * 0.95, 1999: lv[1997] * 0.9, 2000: lv[1997] * 0.78})
        self.assertEqual(ep.investment_busts(lv), [(1998, 2000)], "the fall reaches 20% in the third year")


class RRTest(unittest.TestCase):
    def table(self, cur):
        head = ["Case", "CC3", "Country", "Year", "Banking Crisis ", "Gold Standard",
                "SOVEREIGN EXTERNAL DEBT 1: DEFAULT", "Currency Crises", "Inflation Crises"]
        return [head, [None, None, None, None, "x"]] + [
            ["1", "RUS ", "Russia", str(1995 + i), "0", "0", "0", c, "0"] for i, c in enumerate(cur)]

    def test_two_is_a_crisis(self):
        rows = rr.onsets(self.table(["0", "0", "0", "2", "1", "0"]))
        self.assertEqual([r["year"] for r in rows if r["currency"]], [1998],
                         "a crash coded 2 (a year of currency reform) starts the run; 1999 continues it")
        self.assertEqual({r["iso"] for r in rows}, {"RUS"}, "codes stripped")

    def test_known(self):
        rows = rr.onsets(self.table(["0", "n/a", "1"]))
        self.assertEqual([r["currency_known"] for r in rows], [1, 0, 1])
        self.assertEqual([r["currency"] for r in rows], [0, 0, 1])


class PlacesTest(unittest.TestCase):
    def test_codes(self):
        self.assertEqual(ep.place("KOS"), "XKX")
        self.assertEqual(ep.place("WBG"), "PSE")
        self.assertEqual(ep.place("HUN "), "HUN")
        self.assertEqual(ep.place("THA"), "THA")

    def test_state_years(self):
        st = ep.StateYears({"EST": [(1918, 1940), (1991, 2017)], "AGO": [(1975, 2017)]}, {"DMA": 1978})
        self.assertFalse(st.exists("EST", 1980), "Estonia was not a state in 1980")
        self.assertTrue(st.exists("EST", 1995))
        self.assertTrue(st.exists("EST", 2024), "the list ends in 2017: a state then still is one")
        self.assertFalse(st.exists("AGO", 1968))
        self.assertFalse(st.exists("DMA", 1970), "not in the list: from IMF membership")
        self.assertTrue(st.exists("DMA", 1990))
        self.assertTrue(st.exists("WLD", 1900), "markets and places neither list knows")

    def test_states_file(self):
        st = ep.state_years({})
        self.assertFalse(st.exists("EST", 1970))
        self.assertTrue(st.exists("GEO", 1995))
        self.assertFalse(st.exists("CZE", 1980), "Czechoslovakia is CSK")
        self.assertTrue(st.exists("CSK", 1980))
        self.assertTrue(st.exists("DEU", 1900))
        self.assertFalse(st.exists("DEU", 1946))


class CoverageTest(unittest.TestCase):
    def test_gra_needs_membership(self):
        o = {ep.month(y, m): 0.0 for y in range(1945, 1995) for m in range(1, 13)}
        q = {t: (0.0 if t < ep.month(1975, 1) else 100.0) for t in o}
        self.assertEqual(ep.gra_years(o, q)[0], 1975, "a member from January 1975")
        self.assertEqual(ep.gra_years(o, q)[-1], 1994)
        q2 = {t: (0.0 if t < ep.month(1975, 7) else 100.0) for t in o}
        self.assertEqual(ep.gra_years(o, q2)[0], 1976, "a member from July 1975: 1975 is not a whole year")
        o3 = {t: v for t, v in o.items() if t < ep.month(1994, 10)}
        self.assertEqual(ep.gra_years(o3, q)[-1], 1993, "data to September 1994: 1994 is not a whole year")

    def test_catalogue_spans(self):
        self.assertEqual(ep.LV2026_YEARS, (1970, 2025), "WP/26/94 is 'Systemic Banking Crises Database: 1970-2025'")
        self.assertNotIn("MLT", ep.esrb_coverage({"AT", "UK*"}))
        cov = ep.esrb_coverage({"AT", "UK*"})
        self.assertEqual(cov["AUT"], (1970, 2020))
        self.assertEqual(cov["GBR"], (1970, 2018), "the UK left the update from 2019")


class PanelTest(unittest.TestCase):
    def rules(self):
        return ep.load_rules(os.path.join(RES, "rules.csv"))

    def test_ongoing_and_known(self):
        rows = [ep.row("lv2018", "currency", "THA", ep.month(1997), "s"),
                ep.row("rule:econ-events", "currency_crisis", "THA", ep.month(1998), "s")]
        lib, _ = ep.library(rows, self.rules(), [])
        cov = {"reserve_crisis": {("THA", y): {("lv2018", "currency")} for y in (1996, 1997, 1998, 1999)}}
        pan = {(r[0], r[1]): r for r in ep.panel(lib, cov, {}, self.rules())}
        h = ep.PANEL_COLUMNS
        get = lambda y, c: pan[("THA", y)][h.index(c)]
        self.assertEqual(get(1997, "break"), 1)
        self.assertEqual(get(1998, "break"), 0)
        self.assertEqual(get(1998, "ongoing"), "reserve_crisis:THA:1997-01",
                         "1998 is inside the 1997 episode: not a clean non-event")
        self.assertEqual(get(1999, "ongoing"), "")
        self.assertEqual(get(1997, "known"), "1997-12", "a year catalogue is known by the year's end")
        self.assertEqual(get(1996, "known"), "1996-12")

    def test_bust_known_late(self):
        rows = [ep.row("rule:investment-bust", "icsd_private", "ISL", ep.month(2007), "s", known=ep.month(2009, 12))]
        lib, _ = ep.library(rows, self.rules(), [])
        self.assertEqual(lib[0]["known_date"], "2009-12-01")
        cov = {"investment_bust": {("ISL", y): {("rule:investment-bust", "icsd_private")} for y in (2006, 2007)}}
        pan = {(r[0], r[1]): r for r in ep.panel(lib, cov, {}, self.rules())}
        k = ep.PANEL_COLUMNS.index("known")
        self.assertEqual(pan[("ISL", 2007)][k], "2009-12", "the break is known at the fall")
        self.assertEqual(pan[("ISL", 2006)][k], "2008-12", "a non-event is known when the 3-year window closes")

    def test_episode_catalogues(self):
        rows = [ep.row("rule:imf-gra", "gra_drawing", "FRA", ep.month(1969, 3), "s"),
                ep.row("rule:imf-gra", "gra_drawing", "ITA", ep.month(1974, 3), "s"),
                ep.row("rr", "currency", "ITA", ep.month(1974), "s")]
        lib, _ = ep.library(rows, self.rules(), [])
        n = {r["place"]: r["episode_catalogues"] for r in lib}
        self.assertEqual(n, {"FRA": "rule:imf-gra", "ITA": "rr;rule:imf-gra"})


class LibraryTest(unittest.TestCase):
    def rules(self):
        return ep.load_rules(os.path.join(RES, "rules.csv"))

    def test_unknown_kind_is_an_error(self):
        with self.assertRaises(KeyError):
            ep.classify([ep.row("lv2018", "nonsense", "THA", ep.month(1997), "x")], self.rules())

    def test_cluster_and_follow(self):
        rows = [ep.row("lv2018", "currency", "THA", ep.month(1997), "s"),
                ep.row("rule:econ-events", "currency_crisis", "THA", ep.month(1998), "s"),
                ep.row("rr", "currency", "THA", ep.month(2001), "s"),
                ep.row("lv2018", "banking", "THA", ep.month(1997, 7), "s"),
                ep.row("rule:econ-events", "growth_collapse", "THA", ep.month(1998), "s")]
        lib, eps = ep.library(rows, self.rules(), [])
        self.assertEqual(sorted(eps), ["banking_break:THA:1997-07", "reserve_crisis:THA:1997-01",
                                       "reserve_crisis:THA:2001-01"])
        cur = [r for r in lib if r["episode"] == "reserve_crisis:THA:1997-01"]
        self.assertEqual(len(cur), 2, "1998 joins the 1997 episode; 2001 starts another")
        self.assertEqual(cur[0]["followed_by"], "banking_break@1997-07;growth_collapse@1998-01")
        self.assertIn("bank funding", cur[0]["next_buffers"])
        self.assertFalse(any(r["kind"] == "growth_collapse" for r in lib), "outcomes are not episodes")

    def test_groups_split_market_episodes(self):
        rows = [ep.row("rule:pinksheet-spike", "spike:copper", "WLD", ep.month(2006, 1), "s", subject="copper", group="metals"),
                ep.row("rule:pinksheet-spike", "spike:wheat", "WLD", ep.month(2007, 5), "s", subject="wheat", group="food"),
                ep.row("rule:pinksheet-spike", "spike:rice", "WLD", ep.month(2008, 1), "s", subject="rice", group="food")]
        _, eps = ep.library(rows, self.rules(), [])
        self.assertEqual(sorted(eps), ["commodity_stockout:WLD:food:2007-05", "commodity_stockout:WLD:metals:2006-01"])

    def test_committed_rows(self):
        rules = self.rules()
        lib = read("episodes.csv")
        self.assertGreater(len(lib), 1000)
        ids = [r["row_id"] for r in lib]
        self.assertEqual(len(ids), len(set(ids)), "row ids unique")
        for r in lib:
            self.assertTrue(r["source"].strip(), r["row_id"])
            self.assertRegex(r["date"], r"^\d{4}-\d{2}-01$")
            self.assertRegex(r["episode_start"], r"^\d{4}-\d{2}$")
            if r["end_date"]:
                self.assertRegex(r["end_date"], r"^\d{4}-\d{2}-01$")
            rule = rules[(r["catalogue"], r["kind"])]
            self.assertEqual(r["family"], rule["family"], "family only from the rule table")
            self.assertEqual(r["buffers"], rule["buffers"])
            self.assertNotEqual(r["family"], "-")
            self.assertTrue(r["episode"].startswith(r["family"] + ":" + r["place"] + ":"))

    def test_rules_csv_well_formed(self):
        with open(os.path.join(RES, "rules.csv"), newline="", encoding="utf-8") as f:
            rows = list(csv.reader(f))
        self.assertTrue(all(len(r) == len(rows[0]) for r in rows), "a note with an unquoted comma splits its row")
        for r in self.rules().values():
            self.assertRegex(r["known_lag_months"], r"^\d+$", r["kind"])
            if r["family"] != "-":
                self.assertTrue(r["hindsight"].strip(), r["kind"])

    def test_mechanisms_have_sources(self):
        for m in read("mechanisms.csv"):
            self.assertTrue(m["source"].strip() and m["mechanism"].strip())
            self.assertIn(m["family"], {r["family"] for r in self.rules().values()})

    def test_families_counts_match(self):
        lib = read("episodes.csv")
        fams = {r["name"]: int(r["episodes"]) for r in read("families.csv") if r["by"] == "family"}
        for f, n in fams.items():
            self.assertEqual(n, len({r["episode"] for r in lib if r["family"] == f}))

    def test_rule_table_closed(self):
        """Families appear in the code only as data: no family name is
        written into scripts/episodes.py outside FAMILY_STOCKS and HINDCASTS'
        filters, which must name families from the rule table."""
        fams = {r["family"] for r in self.rules().values()} - {"-"}
        self.assertTrue(set(ep.FAMILY_STOCKS) <= fams)
        for h in ep.HINDCASTS:
            self.assertTrue(set(h[4]) <= fams, h[0])
        with open(os.path.join(HERE, "episodes.py"), encoding="utf-8") as f:
            src = f.read()
        self.assertNotRegex(src, r"\[\"family\"\]\s*=\s*\"")


@unittest.skipUnless(os.path.exists(os.path.join(ep.DATA, "derived", "lv2018.csv")), "no data/")
class RebuildTest(unittest.TestCase):
    def test_rebuild_is_identical(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            ep.build(a, a)
            ep.build(b, b)
            for n in ("episodes.csv", "families.csv", "hindcasts.csv", "manifest.csv", "panel.csv"):
                self.assertTrue(filecmp.cmp(os.path.join(a, n), os.path.join(b, n), shallow=False), n)
            for n in ("episodes.csv", "families.csv", "hindcasts.csv", "manifest.csv"):
                self.assertTrue(filecmp.cmp(os.path.join(a, n), os.path.join(RES, n), shallow=False),
                                n + " differs from the committed file: rebuild and commit")
            with open(os.path.join(a, "panel.csv"), newline="") as f:
                pan = list(csv.DictReader(f))
            self.assertTrue(all(r["covered_by"] or r["break"] == "1" for r in pan))
            self.assertGreater(sum(r["break"] == "0" for r in pan), 10 * sum(r["break"] == "1" for r in pan),
                               "the non-events outnumber the breaks")


if __name__ == "__main__":
    unittest.main()
