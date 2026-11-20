"""Tests for scripts/onset-history.py: each country's onset history per
kind at each December, from the onsets known by then.

  python3 -I scripts/onset-history-test.py
"""
import importlib.util
import io
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("onset_history", os.path.join(HERE, "onset-history.py"))
oh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oh)

EV = "\n".join([
    "coup\tMLI\t2012-03-01\tcoup x\t2012-03-01\t2012-03-01",
    "coup\tMLI\t2020-08-01\tcoup y\t2020-08-01\t2020-08-01",
    "coup\tMLI\t2021-05-01\tcoup z\t2021-05-01\t2021-05-01",
    "coup\tMLI\t2021-11-01\tcoup w\t2021-11-01\t2021-11-01",
    "war_onset\tRUS\t2022-02-01\twar\t2022-12-31\t2022-12-31",
]) + "\n"


class History(unittest.TestCase):
    def setUp(self):
        self.rows = oh.history(io.StringIO(EV), ["MLI", "SEN", "RUS"], {"coup": 3, "war_onset": 18}, 2019, 2023)
        self.at = {(r[0], r[1], r[3][:4]): float(r[5]) for r in self.rows}

    def test_years_since_the_last_known(self):
        self.assertEqual(7.0, self.at[("MLI", "history/coup_since", "2019")])
        self.assertEqual(0.0, self.at[("MLI", "history/coup_since", "2020")])
        self.assertEqual(1.0, self.at[("MLI", "history/coup_since", "2022")])

    def test_an_onset_counts_only_once_its_catalogue_knows_it(self):
        # November 2021's coup is known (3 months on) only in February 2022
        self.assertEqual(1.0, self.at[("MLI", "history/coup_count3", "2021")] - 1.0)
        self.assertEqual(3.0, self.at[("MLI", "history/coup_count3", "2022")])
        # the war of February 2022 is known 18 months on, in August 2023
        self.assertEqual(60.0, self.at[("RUS", "history/war_onset_since", "2022")])
        self.assertEqual(1.0, self.at[("RUS", "history/war_onset_since", "2023")])

    def test_none_known_reads_sixty_years(self):
        self.assertEqual(60.0, self.at[("SEN", "history/coup_since", "2021")])
        self.assertEqual(0.0, self.at[("SEN", "history/coup_count10", "2021")])

    def test_counts_over_ten_years(self):
        self.assertEqual(2.0, self.at[("MLI", "history/coup_count10", "2020")])
        self.assertEqual(3.0, self.at[("MLI", "history/coup_count10", "2021")], "2012's, 2020's and May 2021's; November's not yet known")
        self.assertEqual(3.0, self.at[("MLI", "history/coup_count10", "2022")], "November 2021's known, March 2012's gone")

    def test_rows_are_known_in_their_december(self):
        r = [r for r in self.rows if r[0] == "MLI" and r[1] == "history/coup_since" and r[3] == "2020-12-31"][0]
        self.assertEqual(["MLI", "history/coup_since", "a", "2020-12-31", "2020-12-31"], r[:5])


if __name__ == "__main__":
    unittest.main()
