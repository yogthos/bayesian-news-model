"""Tests for scripts/fast-data.py: a missing sheet, file, series or
response must stop the build loudly, never return nothing.

  venv/bin/python -I scripts/fast-data-test.py
"""
import importlib.util
import io
import json
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


if __name__ == "__main__":
    unittest.main()
