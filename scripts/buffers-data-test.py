"""Tests for scripts/buffers-data.py's readers, on small fixtures (bead bnm-56c.5).

  python -I scripts/buffers-data-test.py
"""
import gzip
import importlib.util
import json
import os
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("buffers_data", os.path.join(HERE, "buffers-data.py"))
bd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bd)


class Tmp(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.d = self.t.name

    def tearDown(self):
        self.t.cleanup()

    def p(self, n):
        return os.path.join(self.d, n)


class WdiTest(Tmp):
    def test_values_and_aggregates(self):
        z = self.p("X.zip")
        with zipfile.ZipFile(z, "w") as f:
            f.writestr("API_X_DS2_en_csv_v2_1.csv", '"Data Source","WDI",\n\n"Last Updated Date","2026-10-01",\n\n'
                       '"Country Name","Country Code","Indicator Name","Indicator Code","1990","1991",\n'
                       '"Thailand","THA","x","X","10.5","",\n"World","WLD","x","X","99","100",\n')
            f.writestr("Metadata_Country_API_X.csv", '"Country Code","Region","IncomeGroup",\n'
                       '"THA","East Asia & Pacific","Upper middle income",\n"WLD","","",\n')
        vals, countries = bd.wdi_zip(z)
        self.assertEqual(vals[("THA", 1990)], 10.5)
        self.assertNotIn(("THA", 1991), vals, "an empty cell is missing, not zero")
        self.assertEqual(countries, {"THA"}, "aggregates have no region")


class BisTest(Tmp):
    def test_dsr(self):
        z = self.p("dsr.zip")
        with zipfile.ZipFile(z, "w") as f:
            f.writestr("WS_DSR_csv_flat.csv",
                       "STRUCTURE,FREQ:Frequency,BORROWERS_CTY:Borrowers' country,DSR_BORROWERS:Borrowers,"
                       "TIME_PERIOD:Time period or range,OBS_VALUE:Observation Value\n"
                       "dataflow,Q: Quarterly,IT: Italy,H: Households & NPISHs,1999-Q1,3.5\n"
                       "dataflow,Q: Quarterly,IT: Italy,P: Private,1999-Q2,NaN\n")
        self.assertEqual(bd.dsr_rows(z, {"IT": "ITA"}), [("ITA", "1999-Q1", "households", 3.5)])


class PsdTest(Tmp):
    def test_stocks_to_use(self):
        z = self.p("psd.zip")
        head = "Commodity_Code,Commodity_Description,Country_Code,Country_Name,Market_Year,Calendar_Year,Month,Attribute_ID,Attribute_Description,Unit_ID,Unit_Description,Value\n"
        rows = ['1,Wheat,EG,Egypt,2020,2024,05,1,Ending Stocks,8,(1000 MT),4000\n',
                '1,Wheat,EG,Egypt,2020,2024,05,2,Domestic Consumption,8,(1000 MT),20000\n',
                '1,Wheat,XX,Atlantis,2020,2024,05,1,Ending Stocks,8,(1000 MT),1\n']
        with zipfile.ZipFile(z, "w") as f:
            f.writestr("psd_grains_pulses.csv", head + "".join(rows))
        out = sorted(bd.psd_rows(z, {"egypt": "EGY"}))
        self.assertEqual(out[1], ("EGY", "Wheat", 2020, 4000.0, 20000.0, 0.2, "2024-05"))
        self.assertEqual(out[0][0], "?Atlantis", "an unmapped name is kept, marked")
        self.assertIsNone(out[0][5])


class SecTest(Tmp):
    def frame(self, concept, y, rows):
        os.makedirs(self.p(concept), exist_ok=True)
        with open(self.p(os.path.join(concept, "CY%d.json" % y)), "w") as f:
            json.dump({"data": [{"cik": c, "val": v} for c, v in rows]}, f)

    def test_industry_sums(self):
        self.frame("PaymentsToAcquirePropertyPlantAndEquipment", 2019, [(1, 10.0), (2, 5.0), (3, 7.0)])
        self.frame("DepreciationDepletionAndAmortization", 2019, [(1, 4.0)])
        self.frame("DepreciationAndAmortization", 2019, [(1, 99.0), (2, 3.0)])
        out = {(s, y, c): (n, v) for s, y, c, n, v in bd.sec_rows(self.d, {1: "4813", 2: "4899", 3: "7372"})}
        self.assertEqual(out[("48", 2019, "capex")], (2, 15.0))
        self.assertEqual(out[("48", 2019, "depreciation")], (2, 7.0), "DDA where reported, else D&A; never both")
        self.assertEqual(out[("73", 2019, "capex")], (1, 7.0))


class EurostatTest(Tmp):
    def test_tsv(self):
        p = self.p("x.tsv.gz")
        with gzip.open(p, "wt") as f:
            f.write("freq,unit,geo\\TIME_PERIOD\t2022-07 \t2022-08 \nM,MIO_M3,DE\t10.5 \t: \nM,MIO_M3,EU27_2020\t3 p\t4 \n")
        out = bd.eurostat_tsv(p)
        self.assertIn(({"freq": "M", "unit": "MIO_M3", "geo": "DE"}, "2022-07", 10.5), out)
        self.assertEqual(len(out), 3, "':' is missing; a flag after the value is dropped")
        out[0][0].pop("geo")
        self.assertEqual(out[1][0]["geo"], "EU27_2020", "rows do not share their dimension dicts")

    def test_eu_rows_keep_geo(self):
        os.makedirs(self.p("eu"))
        with gzip.open(self.p("eu/nrg_stk_gasm.tsv.gz"), "wt") as f:
            f.write("freq,stk_flow,siec,unit,geo\\TIME_PERIOD\t2022-07 \t2022-08 \n"
                    "M,STKCL_NAT,G3000,TJ_GCV,DE\t10 \t11 \nM,STKCL_ABR,G3000,TJ_GCV,DE\t1 \t1 \n")
        self.assertEqual(sorted(bd.eu_rows(self.p("eu"))),
                         [("nrg_stk_gasm", "DE", "2022-07", "siec=G3000;stk_flow=STKCL_NAT;unit=TJ_GCV", 10.0),
                          ("nrg_stk_gasm", "DE", "2022-08", "siec=G3000;stk_flow=STKCL_NAT;unit=TJ_GCV", 11.0)])


class EiaTest(Tmp):
    def test_bulk(self):
        z = self.p("PET.zip")
        lines = [json.dumps({"series_id": "PET.WCSSTUS1.W", "f": "W", "data": [["20260102", 400000.5], ["20251226", None]]}),
                 json.dumps({"series_id": "PET.OTHER.W", "f": "W", "data": [["20260102", 1]]})]
        with zipfile.ZipFile(z, "w") as f:
            f.writestr("PET.txt", "\n".join(lines) + "\n")
        self.assertEqual(bd.eia_bulk_rows(z), [("2026-01-02", "W", "crude_spr", 400000.5, "kbbl", "PET.WCSSTUS1.W")])


class JodiTest(Tmp):
    def test_filter(self):
        z = self.p("j.zip")
        with zipfile.ZipFile(z, "w") as f:
            f.writestr("p.csv", "REF_AREA,TIME_PERIOD,ENERGY_PRODUCT,FLOW_BREAKDOWN,UNIT_MEASURE,OBS_VALUE,ASSESSMENT_CODE\n"
                       "AE,2002-01,CRUDEOIL,CLOSTLV,KBBL,7596,3\nAE,2002-01,CRUDEOIL,CLOSTLV,CONVBBL,1,3\n"
                       "AE,2002-02,CRUDEOIL,CLOSTLV,KBBL,-,3\nAE,2002-01,CRUDEOIL,OSOURCES,KBBL,5,3\n")
        self.assertEqual(bd.jodi_rows(z, {"AE": "ARE"}), [("ARE", "2002-01", "CRUDEOIL", "CLOSTLV", 7596.0, "3")])


class C30Test(Tmp):
    def test_dates_in_every_form(self):
        import datetime
        import openpyxl
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.append(["Value of Private Construction Put in Place"])
        ws.append(["Date", "Total", "Data center"])
        ws.append(["Sep-26p", 10, 5])
        ws.append([datetime.datetime(2026, 8, 1), 9, 4])
        serial = (datetime.date(2026, 7, 1) - datetime.date(1899, 12, 30)).days
        ws.append([serial, 8, 3])  # an Excel date serial stored as a plain number
        ws.append(["Notes", None, None])
        p = self.p("c30.xlsx")
        wb.save(p)
        got = sorted(bd.c30_rows(p, "privsatime"))
        self.assertEqual([(m, c, v, fl) for _, m, c, v, fl in got if c == "Data center"],
                         [("2026-07", "Data center", 3.0, ""), ("2026-08", "Data center", 4.0, ""),
                          ("2026-09", "Data center", 5.0, "p")])


if __name__ == "__main__":
    unittest.main()
