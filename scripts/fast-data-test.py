"""Tests for scripts/fast-data.py: a missing sheet, file, series or
response must stop the build loudly, never return nothing.

  venv/bin/python -I scripts/fast-data-test.py
"""
import importlib.util
import io
import json
import math
import os
import sys
import tempfile
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("fast_data", os.path.join(HERE, "fast-data.py"))
fd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fd)


class Resp(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def respond(*bodies):
    """urlopen returning each body in turn (the last repeated)."""
    it = iter(bodies)
    last = [None]

    def urlopen(req, timeout=None):
        try:
            last[0] = next(it)
        except StopIteration:
            pass
        return Resp(json.dumps(last[0]).encode())
    return urlopen


class ArcgisTest(unittest.TestCase):
    def setUp(self):
        self.sleep = mock.patch.object(fd.time, "sleep", lambda s: None)
        self.sleep.start()

    def tearDown(self):
        self.sleep.stop()

    def test_features(self):
        with mock.patch.object(fd.urllib.request, "urlopen", respond({"features": [{"attributes": {"a": 1}}]})):
            self.assertEqual(fd.arcgis("L", {"where": "1=1"}), [{"a": 1}])

    def test_no_features_key_raises(self):
        with mock.patch.object(fd.urllib.request, "urlopen", respond({"something": "else"})):
            with self.assertRaises(RuntimeError):
                fd.arcgis("L", {"where": "1=1"})

    def test_persistent_error_raises_not_none(self):
        with mock.patch.object(fd.urllib.request, "urlopen", respond({"error": {"code": 400}})):
            with self.assertRaises(RuntimeError):
                fd.arcgis("L", {"where": "1=1"})


class PortwatchTest(unittest.TestCase):
    def test_an_empty_past_month_raises(self):
        calls = []

        def arcgis(layer, params):
            calls.append(params["where"])
            return [] if layer == "Daily_Ports_Data" else [{"portid": "c1", "s_n_total": 5, "n": 31}]
        with tempfile.TemporaryDirectory() as d, \
                mock.patch.object(fd, "PW", d), mock.patch.object(fd, "arcgis", arcgis):
            with self.assertRaises(RuntimeError):
                fd.portwatch()


class FetchMonthTest(unittest.TestCase):
    def test_a_finished_month_is_cached_and_not_queried_again(self):
        calls = []

        def mq(layer, y, m, group):
            calls.append(layer)
            return ([{"ISO3": "EGY", "s_portcalls": 5, "s_import": 1, "s_export": 2, "n": 31}] if group == "ISO3"
                    else [{"portid": "c1", "s_n_total": 7, "n": 31}])
        with tempfile.TemporaryDirectory() as d, mock.patch.object(fd, "PW", d), mock.patch.object(fd, "month_query", mq):
            a = fd.fetch_month(2021, 1, True)
            b = fd.fetch_month(2021, 1, True)
        self.assertEqual(a, b)
        self.assertEqual(len(calls), 2)
        self.assertEqual(a["ports"], [[2021, 1, "EGY", 5, 1, 2, 31]])

    def test_the_current_month_may_be_empty_and_is_not_cached(self):
        with tempfile.TemporaryDirectory() as d, mock.patch.object(fd, "PW", d), \
                mock.patch.object(fd, "month_query", lambda *a: []):
            self.assertEqual(fd.fetch_month(2026, 10, False), {"ports": [], "chokes": []})
            self.assertFalse(os.path.exists(os.path.join(d, "months")))


class MonthQueryTest(unittest.TestCase):
    def test_a_failing_month_is_summed_by_day(self):
        def arcgis(layer, params):
            if "day" not in params["where"]:
                raise RuntimeError("400")
            day = int(params["where"].split("day=")[1])
            z = {"s_import": 0, "s_export": 0}
            return [dict(z, ISO3="EGY", s_portcalls=day, n=1)] + ([dict(z, ISO3="DEU", s_portcalls=2, n=1)] if day == 3 else [])
        with mock.patch.object(fd, "arcgis", arcgis):
            got = {a["ISO3"]: a for a in fd.month_query("Daily_Ports_Data", 2021, 2, "ISO3")}
        self.assertEqual(got["EGY"]["s_portcalls"], sum(range(1, 29)))
        self.assertEqual(got["EGY"]["n"], 28)
        self.assertEqual(got["DEU"]["s_portcalls"], 2)

    def test_a_working_month_is_one_query(self):
        calls = []

        def arcgis(layer, params):
            calls.append(params["where"])
            return [{"ISO3": "EGY", "s_portcalls": 5, "n": 31}]
        with mock.patch.object(fd, "arcgis", arcgis):
            self.assertEqual(fd.month_query("Daily_Ports_Data", 2021, 1, "ISO3"), [{"ISO3": "EGY", "s_portcalls": 5, "n": 31}])
        self.assertEqual(calls, ["year=2021 AND month=1"])


class BuildTest(unittest.TestCase):
    def test_a_family_with_no_rows_raises(self):
        with tempfile.TemporaryDirectory() as d, \
                mock.patch.object(fd, "OUT", os.path.join(d, "series.tsv")), \
                mock.patch.object(fd, "iso2_to_iso3", lambda: {"DE": "DEU"}), \
                mock.patch.object(fd, "pinksheet", lambda: [fd.row("world", "fast/food", 2000, 1, 1.0)]), \
                mock.patch.object(fd, "fx", lambda iso3: [fd.row("DEU", "fast/fx", 2000, 1, 0.1)]), \
                mock.patch.object(fd, "spreads", lambda iso3: []), \
                mock.patch.object(fd, "portwatch_series", lambda: [fd.row("DEU", "fast/portcalls", 2020, 1, 3.0)]):
            with self.assertRaises(RuntimeError):
                fd.build()
            self.assertFalse(os.path.exists(os.path.join(d, "series.tsv")))

    def fake_book(self, rows):
        class WB:
            def __getitem__(self, k):
                class S:
                    def iter_rows(self, values_only=True):
                        return iter(rows)
                return S()
        fake = type(sys)("openpyxl")
        fake.load_workbook = lambda *a, **k: WB()
        return fake

    def test_pinksheet_header_over_several_rows(self):
        # the sheet's layout: names on three rows, marked with "**"
        rows = [("Updated on October 02, 2026", None, None, None, None),
                (None, "Energy", "Non-energy **", None, None),
                (None, None, None, "Fertilizers **", None),
                (None, None, None, None, "Food **"),
                (None, None, " ", " ", " "),
                ("2000M01", 40.0, 50.0, 70.0, 60.0),
                ("2000M02", 41.0, 51.0, 71.0, None)]
        with mock.patch.dict(sys.modules, {"openpyxl": self.fake_book(rows)}):
            got = {(r[1], r[3]): r[5] for r in fd.pinksheet("unused.xlsx")}
        import math
        self.assertEqual(got[("fast/energy", "2000-01-31")], repr(math.log(40.0)))
        self.assertEqual(got[("fast/fertiliser", "2000-02-29")], repr(math.log(71.0)))
        self.assertEqual(got[("fast/food", "2000-01-31")], repr(math.log(60.0)))
        self.assertNotIn(("fast/food", "2000-02-29"), got)

    def test_missing_pinksheet_column_raises(self):
        class WB:
            def __getitem__(self, k):
                class S:
                    def iter_rows(self, values_only=True):
                        return iter([(None, "Energy", "Food"), ("2000M01", 50.0, 60.0)])
                return S()
        fake = type(sys)("openpyxl")
        fake.load_workbook = lambda *a, **k: WB()
        with mock.patch.dict(sys.modules, {"openpyxl": fake}):
            with self.assertRaises(SystemExit):
                fd.pinksheet("unused.xlsx")


def bis_zip(path, rows):
    """A WS_XRU flat file with `rows` [(iso2, "YYYY-MM", value)], monthly,
    end of period."""
    import zipfile
    buf = io.StringIO()
    buf.write("FREQ:Frequency,REF_AREA:Reference area,COLLECTION:Collection,TIME_PERIOD:Time period,OBS_VALUE:Value\n")
    for a2, ym, v in rows:
        buf.write(f"M:Monthly,{a2}:Area,E:End of period,{ym},{v}\n")
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("WS_XRU_csv_flat.csv", buf.getvalue())


class FxTest(unittest.TestCase):
    def series(self, rows):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "x.zip")
            bis_zip(path, rows)
            out = fd.fx({"VE": "VEN", "AR": "ARG", "US": "USA"}, path)
        return {(r[0], r[3][:7]): float(r[5]) for r in out}

    def test_a_redenomination_is_not_an_appreciation(self):
        # the bolivar lost six zeros in October 2021: the series continues
        # in the new units, the months before shifted by the break
        s = self.series([("VE", "2021-08", 4000000.0), ("VE", "2021-09", 4100000.0),
                         ("VE", "2021-10", 4.3), ("VE", "2021-11", 4.6)])
        self.assertAlmostEqual(s[("VEN", "2021-10")] - s[("VEN", "2021-09")], 0.0)
        self.assertAlmostEqual(s[("VEN", "2021-09")] - s[("VEN", "2021-08")], math.log(4.1 / 4.0))
        self.assertAlmostEqual(s[("VEN", "2021-11")], math.log(4.6), msg="the latest units kept")

    def test_a_devaluation_and_an_appreciation_are_kept(self):
        s = self.series([("AR", "2001-12", 1.0), ("AR", "2002-01", 1.0), ("AR", "2002-02", 2.1),
                         ("AR", "2002-03", 3.0), ("AR", "2002-04", 1.2)])
        self.assertAlmostEqual(s[("ARG", "2002-02")] - s[("ARG", "2002-01")], math.log(2.1))
        self.assertAlmostEqual(s[("ARG", "2002-04")] - s[("ARG", "2002-03")], math.log(1.2 / 3.0))

    def test_the_us_has_none(self):
        self.assertFalse(any(k[0] == "USA" for k in self.series([("US", "2001-01", 1.0)])))


if __name__ == "__main__":
    unittest.main()
