"""Tests for scripts/rer-annual.py: each country's real exchange rate
against the dollar, annual, from WDI's exchange rate and CPI inflation.

  python3 -I scripts/rer-annual-test.py
"""
import importlib.util
import io
import math
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("rer_annual", os.path.join(HERE, "rer-annual.py"))
ra = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ra)

TSV = "\n".join([
    "TUR\tfinance/fx_per_usd\ta\t2000-12-31\t2001-09-30\t0.625",
    "TUR\tfinance/fx_per_usd\ta\t2001-12-31\t2002-09-30\t1.2",
    "TUR\tfinance/fx_per_usd\ta\t2002-12-31\t2003-09-30\t1.5",
    "TUR\teconomy/inflation\ta\t2000-12-31\t2001-07-31\t50",
    "TUR\teconomy/inflation\ta\t2001-12-31\t2002-07-31\t50",
    "TUR\teconomy/inflation\ta\t2002-12-31\t2003-10-31\t20",
    "USA\teconomy/inflation\ta\t2000-12-31\t2001-07-31\t3",
    "USA\teconomy/inflation\ta\t2001-12-31\t2002-07-31\t2",
    "USA\teconomy/inflation\ta\t2002-12-31\t2003-07-31\t2",
    "GAP\tfinance/fx_per_usd\ta\t2000-12-31\t2001-09-30\t1",
    "GAP\tfinance/fx_per_usd\ta\t2001-12-31\t2002-09-30\t1",
    "GAP\tfinance/fx_per_usd\ta\t2002-12-31\t2003-09-30\t1",
    "GAP\teconomy/inflation\ta\t2000-12-31\t2001-07-31\t5",
    "GAP\teconomy/inflation\ta\t2002-12-31\t2003-07-31\t5",
]) + "\n"


class Rer(unittest.TestCase):
    def setUp(self):
        self.rows = ra.rer(io.StringIO(TSV))

    def test_log_rate_less_cumulated_prices_plus_the_dollar_side(self):
        tur = [r for r in self.rows if r[0] == "TUR"]
        self.assertEqual(["2000-12-31", "2001-12-31", "2002-12-31"], [r[3] for r in tur])
        v = [float(r[5]) for r in tur]
        # 2001 against 2000: the rate up log(1.2/0.625), prices up log 1.5 at home and log 1.02 in the US
        self.assertAlmostEqual(math.log(1.2 / 0.625) - math.log(1.5) + math.log(1.02), v[1] - v[0], 12)
        self.assertAlmostEqual(math.log(1.5 / 1.2) - math.log(1.2) + math.log(1.02), v[2] - v[1], 12)

    def test_available_when_its_last_input_is(self):
        tur = {r[3]: r[4] for r in self.rows if r[0] == "TUR"}
        self.assertEqual("2003-10-31", tur["2002-12-31"])

    def test_a_gap_in_prices_starts_again_from_the_latest_unbroken_run(self):
        self.assertEqual(["2002-12-31"], [r[3] for r in self.rows if r[0] == "GAP"])

    def test_no_us_row(self):
        self.assertFalse([r for r in self.rows if r[0] == "USA"])


if __name__ == "__main__":
    unittest.main()
