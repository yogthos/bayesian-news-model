"""Tests for scripts/slow-normalize.py: a value the World Bank publishes as 0
where 0 is impossible (Venezuela's trade and sector shares 1991-2011,
Brazil's sector shares 1960-1980) is a gap, never a zero (bead bnm-as1).

  venv/bin/python -I scripts/slow-normalize-test.py
"""
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


class PeriodEndTest(unittest.TestCase):
    def test_quarters(self):
        self.assertEqual(sn.period_end("q", "2001-Q2"), "2001-06-30")
        self.assertEqual(sn.period_end("q", "2001Q4"), "2001-12-31")

    def test_bad_quarter_names_the_label(self):
        with self.assertRaisesRegex(ValueError, "2001-06"):
            sn.period_end("q", "2001-06")


SERIES = os.path.join(os.path.dirname(HERE), "data", "slow", "series.tsv")


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
