"""Tests for scripts/slow-normalize.py: a value the World Bank publishes as 0
where 0 is impossible (Venezuela's trade and sector shares 1991-2011,
Brazil's sector shares 1960-1980) is a gap, never a zero (bead bnm-as1).

  venv/bin/python -I scripts/slow-normalize-test.py
"""
import calendar
import csv
import importlib.util
import json
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("slow_normalize", os.path.join(HERE, "slow-normalize.py"))
sn = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sn)


def wb_file(d, code, rows):
    """A World Bank API response: [meta, [{countryiso3code, date, value}]]."""
    os.makedirs(os.path.join(d, "worldbank"), exist_ok=True)
    with open(os.path.join(d, "worldbank", code + ".json"), "w") as f:
        json.dump([{"page": 1}, [{"countryiso3code": c, "date": str(y), "value": v} for c, y, v in rows]], f)


class WorldBankZerosTest(unittest.TestCase):
    def run_wb(self, files):
        old = (sn.RAW, list(sn.ROWS))
        with tempfile.TemporaryDirectory() as d:
            for code, rows in files.items():
                wb_file(d, code, rows)
            sn.RAW, sn.ROWS[:] = d, []
            try:
                sn.worldbank()
                return {(r[0], r[1], r[3][:4]): r[5] for r in sn.ROWS}
            finally:
                sn.RAW, sn.ROWS[:] = old[0], old[1]

    def test_impossible_zero_is_a_gap(self):
        got = self.run_wb({
            "NE.TRD.GNFS.ZS": [("VEN", 1990, 57.7), ("VEN", 1991, 0), ("VEN", 2011, 0), ("VEN", 2012, 50.8)],
            "NV.AGR.TOTL.ZS": [("BRA", 1960, 0), ("BRA", 1980, 0), ("BRA", 1981, 10.0), ("VEN", 2000, 0)],
            "NV.IND.MANF.ZS": [("BRA", 1970, 0), ("COD", 1991, 0)],
            "NV.SRV.TOTL.ZS": [("VEN", 1995, 0)],
            "FS.AST.PRVT.GD.ZS": [("ROU", 1994, 0), ("ROU", 1996, 11.0)],
            "FM.LBL.BMNY.GD.ZS": [("CHL", 1960, 0)],
            "DT.DOD.DECT.GN.ZS": [("GNB", 1972, 0)],
            "BX.KLT.DINV.WD.GD.ZS": [("HUN", 1985, 0), ("HUN", 1990, 1.6)],
        })
        self.assertEqual(got[("VEN", "trade/openness", "1990")], 57.7)
        self.assertEqual(got[("VEN", "trade/openness", "2012")], 50.8)
        self.assertEqual(got[("BRA", "economy/agriculture_share", "1981")], 10.0)
        self.assertEqual(got[("ROU", "finance/private_credit_gdp", "1996")], 11.0)
        self.assertEqual(got[("HUN", "debt/fdi_gdp", "1990")], 1.6)
        zeros = [k for k, v in got.items() if v == 0]
        self.assertEqual(zeros, [], "missing values read as 0")

    def test_possible_zero_is_kept(self):
        # Costa Rica has no army, small islands have no oil rents, export
        # shares can be nil: these zeros are measurements
        got = self.run_wb({
            "MS.MIL.XPND.GD.ZS": [("CRI", 2000, 0)],
            "NY.GDP.PETR.RT.ZS": [("BHS", 2000, 0)],
            "TX.VAL.FUEL.ZS.UN": [("GMB", 2015, 0)],
            "NY.GDP.MKTP.KD.ZG": [("GRC", 1990, 0)],
            "IT.NET.USER.ZS": [("BDI", 1992, 0)],
            "IP.JRN.ARTC.SC": [("NRU", 1999, 0), ("NRU", 1998, 0.3), ("NRU", 1997, 0.2)],
        })
        for k in [("CRI", "state/military_gdp", "2000"), ("BHS", "energy/oil_rents_gdp", "2000"),
                  ("GMB", "energy/fuel_exports_share", "2015"), ("GRC", "economy/gdp_growth", "1990"),
                  ("BDI", "technology/internet_users", "1992"), ("NRU", "technology/articles", "1999")]:
            self.assertEqual(got.get(k), 0.0, k)

    def test_leading_zeros_of_a_count_are_a_gap(self):
        # Serbia's articles are 0 until 2007 (counted under Serbia and
        # Montenegro), South Sudan's until it exists: not none published
        got = self.run_wb({"IP.JRN.ARTC.SC": [("SRB", 1996, 0), ("SRB", 2006, 0), ("SRB", 2007, 2916.0),
                                               ("SRB", 2008, 0), ("SSD", 2000, 0)]})
        self.assertNotIn(("SRB", "technology/articles", "1996"), got)
        self.assertNotIn(("SRB", "technology/articles", "2006"), got)
        self.assertNotIn(("SSD", "technology/articles", "2000"), got)
        self.assertEqual(got[("SRB", "technology/articles", "2007")], 2916.0)
        # a zero after the series has begun is a count
        self.assertEqual(got[("SRB", "technology/articles", "2008")], 0.0)

    def test_known_year_unchanged(self):
        # WDI as known: nine months after the year ends
        got_rows = []
        old = (sn.RAW, list(sn.ROWS))
        with tempfile.TemporaryDirectory() as d:
            wb_file(d, "NE.TRD.GNFS.ZS", [("VEN", 2012, 50.8)])
            sn.RAW, sn.ROWS[:] = d, []
            try:
                sn.worldbank()
                got_rows = list(sn.ROWS)
            finally:
                sn.RAW, sn.ROWS[:] = old[0], old[1]
        self.assertEqual(got_rows, [("VEN", "trade/openness", "a", "2012-12-31", "2013-09-30", 50.8)])


def write_xlsx(path, sheet_xml):
    """A one-sheet workbook with no shared strings."""
    import zipfile
    m = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("xl/workbook.xml", '<workbook xmlns="%s" xmlns:r="%s"><sheets>'
                   '<sheet name="S" sheetId="1" r:id="rId1"/></sheets></workbook>' % (m, r))
        z.writestr("xl/_rels/workbook.xml.rels",
                   '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   '<Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>')
        z.writestr("xl/worksheets/sheet1.xml", '<worksheet xmlns="%s"><sheetData>%s</sheetData></worksheet>' % (m, sheet_xml))


class XlsxTest(unittest.TestCase):
    def read(self, sheet_xml):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "w.xlsx")
            write_xlsx(p, sheet_xml)
            return sn.xlsx(p)["S"]

    def test_cell_refs(self):
        self.assertEqual(self.read('<row><c r="A1"><v>1</v></c><c r="C1"><v>3</v></c></row>'), [["1", None, "3"]])

    def test_cells_without_refs_follow_in_order(self):
        # the r attribute of a cell is optional: without it a cell is the
        # one after the previous
        self.assertEqual(self.read('<row><c><v>1</v></c><c><v>2</v></c><c r="E1"><v>5</v></c><c><v>6</v></c></row>'),
                         [["1", "2", None, None, "5", "6"]])

    def test_empty_value_is_no_value(self):
        self.assertEqual(self.read('<row><c r="A1"><v/></c><c r="B1" t="s"><v/></c><c r="C1"><v>3</v></c></row>'),
                         [[None, None, "3"]])


class GazetteerTest(unittest.TestCase):
    """countries.edn pretty-prints a long :names vector on its own line; the
    entry before it must not take the next entry's names (Georgia was read
    as GBR, Burkina Faso as BES, the Republic of Congo as COD)."""

    def test_names_on_their_own_line(self):
        self.assertEqual(sn.iso3_of_name("Georgia"), "GEO")
        self.assertEqual(sn.iso3_of_name("Burkina Faso"), "BFA")
        self.assertEqual(sn.iso3_of_name("Republic of the Congo"), "COG")
        self.assertEqual(sn.iso3_of_name("Svalbard and Jan Mayen"), "SJM")
        self.assertEqual(sn.iso3_of_name("Bonaire, Sint Eustatius and Saba"), "BES")
        self.assertEqual(sn.iso3_of_name("United Kingdom"), "GBR")

    def test_esrb_codes(self):
        self.assertEqual(sn.esrb_iso("UK*"), "GBR", "the database stars the UK (left out after 2018)")
        self.assertEqual(sn.esrb_iso("EL"), "GRC")
        self.assertEqual(sn.esrb_iso("UK"), "GBR")
        self.assertEqual(sn.esrb_iso("DE"), "DEU")


class PeriodEndTest(unittest.TestCase):
    def test_quarters(self):
        self.assertEqual(sn.period_end("q", "2001-Q2"), "2001-06-30")
        self.assertEqual(sn.period_end("q", "2001Q4"), "2001-12-31")

    def test_bad_quarter_names_the_label(self):
        with self.assertRaisesRegex(ValueError, "2001-06"):
            sn.period_end("q", "2001-06")


class WdiLagTest(unittest.TestCase):
    """bnm-56c.23: UIS tertiary enrolment reaches WDI with UIS's September
    release, one to two years after the reference year, not WDI's 9 months."""

    def run_wb(self, files):
        old = (sn.RAW, list(sn.ROWS))
        with tempfile.TemporaryDirectory() as d:
            for code, rows in files.items():
                wb_file(d, code, rows)
            sn.RAW, sn.ROWS[:] = d, []
            try:
                sn.worldbank()
                return {(r[1], r[3][:4]): r[4] for r in sn.ROWS}
            finally:
                sn.RAW, sn.ROWS[:] = old[0], old[1]

    def test_tertiary_enrolment_waits_for_uis(self):
        got = self.run_wb({"SE.TER.ENRR": [("USA", 2014, 88.0)], "SP.DYN.IMRT.IN": [("USA", 2014, 6.0)]})
        self.assertGreaterEqual(got[("people/tertiary_enrolment", "2014")], "2016-09-30")
        self.assertEqual(got[("people/infant_mortality", "2014")], "2015-09-30", "the rest keep 9 months")


class FredPartialMonthTest(unittest.TestCase):
    """bnm-56c.23: a daily series' current month, downloaded part way through,
    is not a month's mean; it waits until the month is complete."""

    def run_fred(self, series):
        old = (sn.RAW, list(sn.ROWS), dict(sn.FRED))
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "fred"))
            for sid, days in series.items():
                with open(os.path.join(d, "fred", sid + ".csv"), "w") as f:
                    f.write("observation_date,%s\n" % sid)
                    for day, v in days:
                        f.write("%s,%s\n" % (day, v))
            sn.RAW, sn.ROWS[:] = d, []
            sn.FRED.clear()
            sn.FRED.update({sid: old[2][sid] for sid in series})
            try:
                sn.fred()
                return {(r[1], r[3][:7]): (r[4], r[5]) for r in sn.ROWS}
            finally:
                sn.RAW, sn.ROWS[:] = old[0], old[1]
                sn.FRED.clear()
                sn.FRED.update(old[2])

    @staticmethod
    def weekdays(y, m, last):
        import datetime
        return [datetime.date(y, m, d).isoformat() for d in range(1, last + 1)
                if datetime.date(y, m, d).weekday() < 5]

    def test_partial_month_excluded(self):
        days = self.weekdays(2026, 9, 30) + self.weekdays(2026, 10, 15)
        got = self.run_fred({"VIXCLS": [(d, 20.0) for d in days]})
        self.assertIn(("finance/vix", "2026-09"), got)
        self.assertEqual(got[("finance/vix", "2026-09")][0], "2026-09-30", "complete: known at its end")
        self.assertNotIn(("finance/vix", "2026-10"), got, "1-15 October is not October's mean")

    def test_month_complete_on_its_last_weekday_kept(self):
        days = self.weekdays(2026, 9, 30)
        got = self.run_fred({"VIXCLS": [(d, 20.0) for d in days]})
        self.assertIn(("finance/vix", "2026-09"), got)

    def test_monthly_series_keeps_its_last_month(self):
        got = self.run_fred({"CPIAUCSL": [("2026-08-01", 320.0), ("2026-09-01", 321.0)]})
        self.assertEqual(got[("economy/cpi", "2026-09")][0], "2026-10-31", "a monthly value, a month late")


class DisasterPartialYearTest(unittest.TestCase):
    """bnm-56c.23: EM-DAT downloaded in October holds the year to date; it is
    not a year's count."""

    def test_partial_year_dropped(self):
        import time
        header = ["Year", "ISO", "Disaster Group", "Total Events", "Total Affected", "Total Deaths"]
        rows = [header, ["units"]] + [[y, "USA", "Natural", 10, 100, 5] for y in (2024, 2025, 2026)]
        old = (sn.RAW, list(sn.ROWS), sn.xlsx)
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "disasters"))
            path = os.path.join(d, "disasters", "emdat-country-profiles.xlsx")
            open(path, "w").close()
            t = time.mktime((2026, 10, 14, 12, 0, 0, 0, 0, -1))
            os.utime(path, (t, t))
            sn.RAW, sn.ROWS[:] = d, []
            sn.xlsx = lambda _p: {"sheet": rows}
            try:
                sn.disasters()
                got = {(r[1], r[3][:4]) for r in sn.ROWS}
            finally:
                sn.RAW, sn.ROWS[:], sn.xlsx = old
        self.assertIn(("climate/disaster_events", "2025"), got)
        self.assertNotIn(("climate/disaster_events", "2026"), got, "January to October 2026 is not 2026")


class PwtReleaseTest(unittest.TestCase):
    """bnm-56c.23: a PWT year is known when the first release that covers it
    came out (resources/bnm/releases.tsv), not a fixed 24 months on; irr and
    labsh do not exist before PWT 9.0 and 8.0."""

    def run_pwt(self, rows):
        old = (sn.RAW, list(sn.ROWS))
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "pwt"))
            with open(os.path.join(d, "pwt", "pwt110.csv"), "w") as f:
                f.write("countrycode,year,labsh,irr,csh_i,rgdpna,rnna\n")
                for iso, y in rows:
                    f.write("%s,%d,0.5,0.1,0.2,100,300\n" % (iso, y))
            sn.RAW, sn.ROWS[:] = d, []
            try:
                sn.pwt()
                return {(r[1], r[3][:4]): r[4] for r in sn.ROWS}
            finally:
                sn.RAW, sn.ROWS[:] = old[0], old[1]

    def test_dated_by_first_release(self):
        got = self.run_pwt([("USA", y) for y in (1960, 2003, 2004, 2005, 2009, 2012, 2017, 2018, 2023)])
        inv = "economy/investment_share"
        self.assertEqual(got[(inv, "2004")], "2006-09-30", "PWT 6.2, September 2006")
        self.assertEqual(got[(inv, "2003")], "2006-09-30", "6.1 ended at 2000: 2003 waits for 6.2")
        self.assertEqual(got[(inv, "2005")], "2009-08-31", "PWT 6.3")
        self.assertEqual(got[(inv, "2012")], "2016-06-30", "8.0 and 8.1 ended at 2011: 9.0")
        self.assertEqual(got[(inv, "2018")], "2021-01-31", "PWT 10.0")
        self.assertEqual(got[(inv, "2023")], "2025-10-31", "PWT 11.0")
        self.assertEqual(got[(inv, "1960")], "1962-12-31", "before the first tabulated release: 24 months")

    def test_irr_and_labsh_absent_before_their_release(self):
        got = self.run_pwt([("USA", 1960), ("USA", 2005), ("USA", 2012), ("USA", 2017)])
        self.assertEqual(got[("economy/profit_rate", "1960")], "2016-06-30", "irr first published in 9.0")
        self.assertEqual(got[("economy/profit_rate", "2005")], "2016-06-30")
        self.assertEqual(got[("economy/labor_share", "2005")], "2013-07-31", "labsh first published in 8.0")
        self.assertEqual(got[("economy/capital_output", "1960")], "2013-07-31", "capital stocks from 8.0")
        self.assertEqual(got[("economy/labor_share", "2012")], "2016-06-30")
        self.assertEqual(got[("economy/profit_rate", "2017")], "2019-04-30", "PWT 9.1")


class FaostatReleaseTest(unittest.TestCase):
    """bnm-56c.31: FAOSTAT publishes its monthly consumer price indices
    quarterly, 6 to 9 months late (faostat_cp rows of resources/bnm/
    releases.tsv). A month is not read before a release that carries it,
    and at the lag bnm.economic-events/food-publication-lag reads the food
    outcomes at, so the two paths agree."""

    def run_faostat(self, months):
        import zipfile
        old = (sn.RAW, list(sn.ROWS))
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "faostat"))
            with zipfile.ZipFile(os.path.join(d, "faostat", "cpi.zip"), "w") as z:
                lines = ['"Area Code (M49)","Item Code","Year","Months","Value"']
                for y, m in months:
                    for item in ("23012", "23013", "23014"):
                        lines.append("'840,%s,%d,%s,101.5" % (item, y, calendar.month_name[m]))
                z.writestr("ConsumerPriceIndices_E_All_Data_(Normalized).csv", "\n".join(lines) + "\n")
            sn.RAW, sn.ROWS[:] = d, []
            try:
                sn.faostat()
                return {(r[1], r[3][:7]): r[4] for r in sn.ROWS}
            finally:
                sn.RAW, sn.ROWS[:] = old[0], old[1]

    def test_same_lag_as_the_food_outcomes(self):
        import re
        src = open(os.path.join(os.path.dirname(HERE), "tools", "bnm", "economic_events.clj")).read()
        lag = int(re.search(r"\(def food-publication-lag\s+\"(?:[^\"\\]|\\.)*\"\s+(\d+)\)", src).group(1))
        got = self.run_faostat([(2023, 3), (2023, 4)])
        for v in ("food/food_cpi", "food/food_inflation", "economy/cpi_general"):
            self.assertEqual(got[(v, "2023-03")], sn.add_months("2023-03-31", lag), v)
            self.assertEqual(got[(v, "2023-04")], sn.add_months("2023-04-30", lag), v)

    def test_not_before_its_release(self):
        rels = []
        with open(sn.RELEASES, encoding="utf-8") as f:
            for r in csv.DictReader(f, delimiter="\t"):
                if r["source"] == "faostat_cp":
                    rels.append((r["published"], r["last_month"]))
        self.assertTrue(rels)
        months = []
        for _, last in rels:
            y, m = int(last[:4]), int(last[5:7])
            months += [(y, m), ((y * 12 + m) // 12, (y * 12 + m) % 12 + 1)]
        got = self.run_faostat(months)
        for published, last in rels:
            pub_end = sn.month_end(int(published[:4]), int(published[5:7]))
            y, m = int(last[:4]), int(last[5:7])
            nxt = "%04d-%02d" % ((y * 12 + m) // 12, (y * 12 + m) % 12 + 1)
            self.assertGreaterEqual(got[("food/food_cpi", last)], pub_end, "%s: not before the release" % last)
            self.assertGreater(got[("food/food_cpi", nxt)], pub_end, "%s: not in the release of %s" % (nxt, published))


SERIES =os.path.join(os.path.dirname(HERE), "data", "slow", "series.tsv")


@unittest.skipUnless(os.path.exists(SERIES), "data/slow/series.tsv not built")
class SeriesTest(unittest.TestCase):
    """The built series: no WDI variable where 0 is impossible holds a 0."""

    def test_no_impossible_zeros(self):
        names = {sn.WB[c][0] for c in sn.WB_ZERO_IS_GAP}
        bad = []
        with open(SERIES) as f:
            for line in f:
                u, v, _, pend, _, x = line.rstrip("\n").split("\t")
                if v in names and float(x) == 0:
                    bad.append((u, v, pend))
        self.assertEqual(bad[:10], [], "%d impossible zeros" % len(bad))


if __name__ == "__main__":
    unittest.main()
