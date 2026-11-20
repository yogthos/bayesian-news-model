"""Tests for scripts/fiscal-series.py: the Global Macro Database's general
government balance, expenditure and revenue as declared series.

  python3 -I scripts/fiscal-series-test.py
"""
import importlib.util
import io
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("fiscal_series", os.path.join(HERE, "fiscal-series.py"))
fs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fs)

CSV = "\n".join([
    "iso,year,var,value",
    "GRC,2009,gov_bal_gdp,-15.1",
    "GRC,2010,gov_bal_gdp,-11.2",
    "GRC,2010,gov_exp_gdp,52.5",
    "GRC,2010,gov_rev_gdp,41.3",
    "GRC,2010,gov_exp_real,123.4",
    "GRC,2010,gmd_sovereign,1",
    "GRC,2011,gov_bal_gdp,",
    "GRC,2012,gov_bal_gdp,nan",
]) + "\n"


class Fiscal(unittest.TestCase):
    def setUp(self):
        self.rows = fs.series(io.StringIO(CSV))

    def test_the_three_ratios_named_under_fiscal(self):
        got = {(r[0], r[1], r[3]): float(r[5]) for r in self.rows}
        self.assertEqual(-15.1, got[("GRC", "fiscal/balance_gdp", "2009-12-31")])
        self.assertEqual(52.5, got[("GRC", "fiscal/expenditure_gdp", "2010-12-31")])
        self.assertEqual(41.3, got[("GRC", "fiscal/revenue_gdp", "2010-12-31")])

    def test_other_columns_and_blanks_are_left_out(self):
        self.assertEqual({"fiscal/balance_gdp", "fiscal/expenditure_gdp", "fiscal/revenue_gdp"}, {r[1] for r in self.rows})
        self.assertEqual(4, len(self.rows))

    def test_annual_and_known_nine_months_after_the_year(self):
        r = [r for r in self.rows if r[3] == "2010-12-31" and r[1] == "fiscal/balance_gdp"][0]
        self.assertEqual("a", r[2])
        self.assertEqual("2011-09-30", r[4])


if __name__ == "__main__":
    unittest.main()
