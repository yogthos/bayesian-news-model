"""Tests for scripts/banking-series.py: WDI's bank NPLs, capital and liquid
reserves as declared series, each country's year known at the end of the
month of the first WDI edition that held it.

  python3 -I scripts/banking-series-test.py
"""
import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("banking_series", os.path.join(HERE, "banking-series.py"))
bs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bs)


def wb(rows):
    """A World Bank API v2 body: [page, rows]."""
    return [{"page": 1}, [{"countryiso3code": i, "date": str(y), "value": v} for i, y, v in rows]]


def edition(rows):
    """A WDI archive (source 57) body holding (iso, year, value) rows."""
    return {"source": {"data": [{"value": v, "variable": [{"concept": "Country", "id": i}, {"concept": "Time", "id": "YR%d" % y}]}
                                for i, y, v in rows]}}


class Current(unittest.TestCase):
    def test_values_by_country_and_year_blanks_and_aggregates_dropped(self):
        got = bs.current(wb([("KAZ", 2011, 20.7), ("KAZ", 2012, None), ("", 2011, 5.0)]))
        self.assertEqual({("KAZ", 2011): 20.7}, got)

    def test_an_empty_body(self):
        self.assertEqual({}, bs.current([{"page": 1}, None]))


class FirstPublished(unittest.TestCase):
    def setUp(self):
        self.when = {"201204": (2012, 4), "201209": (2012, 9), "201304": (2013, 4), "201804": (2018, 4)}
        self.eds = {"201204": edition([("KAZ", 2011, 20.7), ("KAZ", 2010, None)]),
                    "201209": edition([("KAZ", 2011, 20.9), ("KAZ", 2010, 21.0)]),
                    "201304": edition([("KAZ", 2012, 19.0)]),
                    "201804": edition([("AGO", 2015, 9.0)])}
        self.first, self.held = bs.first_editions(self.eds, self.when)

    def test_the_earliest_edition_holding_a_value(self):
        self.assertEqual((2012, 4), self.first[("KAZ", 2011)])
        self.assertEqual((2012, 9), self.first[("KAZ", 2010)], "a blank in an edition is not publication")

    def test_editions_holding_any_value(self):
        self.assertEqual([(2012, 4), (2012, 9), (2013, 4), (2018, 4)], self.held)

    def test_a_year_no_edition_held_in_its_window_is_out_four_years_on(self):
        # each edition is fetched for its own year and the three before: AGO
        # 2013 was not held by 2013-2016's editions, so no earlier than the
        # first edition of 2017 or later
        self.assertEqual((2018, 4), bs.known("AGO", 2013, self.first, self.held))

    def test_a_year_held_is_its_first_edition(self):
        self.assertEqual((2012, 4), bs.known("KAZ", 2011, self.first, self.held))

    def test_past_the_last_edition_the_month_after(self):
        self.assertEqual((2018, 5), bs.known("AGO", 2016, self.first, self.held))
        self.assertEqual((2019, 1), bs.known("X", 2016, self.first, [(2018, 12)]))

    def test_a_year_before_the_series_existed_is_its_first_edition(self):
        self.assertEqual((2012, 4), bs.known("KAZ", 2000, self.first, self.held))


class Series(unittest.TestCase):
    def test_rows_annual_dated_at_the_end_of_the_edition_month(self):
        rows = bs.series({("KAZ", 2011): 20.7, ("AGO", 2013): 8.0}, {("KAZ", 2011): (2012, 4)}, [(2012, 4), (2018, 4)], "banks/npl")
        self.assertEqual([["AGO", "banks/npl", "a", "2013-12-31", "2018-04-30", "8.0"],
                          ["KAZ", "banks/npl", "a", "2011-12-31", "2012-04-30", "20.7"]], rows)

    def test_month_ends(self):
        self.assertEqual("2012-02-29", bs.month_end(2012, 2))
        self.assertEqual("2013-02-28", bs.month_end(2013, 2))
        self.assertEqual("2014-12-31", bs.month_end(2014, 12))

    def test_never_known_before_its_year_ends(self):
        # an edition can hold a year before it ends (a projection): known no
        # earlier than the month after
        rows = bs.series({("KAZ", 2012): 19.0}, {("KAZ", 2012): (2012, 9)}, [(2012, 9)], "banks/npl")
        self.assertEqual("2013-01-31", rows[0][4])

    def test_the_three_series_named_under_banks(self):
        self.assertEqual({"FB.AST.NPER.ZS": "banks/npl", "FB.BNK.CAPA.ZS": "banks/capital_assets",
                          "FD.RES.LIQU.AS.ZS": "banks/liquid_reserves"}, bs.NAMES)


if __name__ == "__main__":
    unittest.main()
