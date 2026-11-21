"""Tests for scripts/banking-spans.py: each systemic banking crisis's span,
start to end, from the ESRB database and Laeven-Valencia, by the rules
scripts/slow-normalize.py dates their onsets with (bnm-suj).

  python3 -I scripts/banking-spans-test.py
"""
import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("banking_spans", os.path.join(HERE, "banking-spans.py"))
bs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bs)

HEAD = [["Country", "Event", "Start date", "End of crisis management date", 'System back to "normal" date', "Systemic crisis", "Accelerator"],
        [None, None, None, None, None, "0= NO 1= YES", None]]


class Esrb(unittest.TestCase):
    def test_start_to_the_end_of_crisis_management(self):
        rows = HEAD + [["IE", "1", "2008-09", "2013-12", "2013-12", "1", "credit"]]
        self.assertEqual([("IRL", "2008-09", "2013-12", "ESRB")], bs.esrb_spans(rows))

    def test_exogenous_and_non_systemic_left_out_as_for_onsets(self):
        rows = HEAD + [["AT", "2", "2020-03", "2021-04", "2021-04", "1", "Exogenous health crisis"],
                       ["AT", "3", "2001-01", "2002-01", "2002-01", "0", "x"]]
        self.assertEqual([], bs.esrb_spans(rows))

    def test_no_end_falls_back_to_back_to_normal_then_the_start(self):
        rows = HEAD + [["EL", "1", "2008-01", "n.a.", "2018-08", "1", "x"],
                       ["DK", "1", "2008-01", None, "", "1", "x"]]
        self.assertEqual([("DNK", "2008-01", "2008-01", "ESRB"), ("GRC", "2008-01", "2018-08", "ESRB")], sorted(bs.esrb_spans(rows)))

    def test_an_end_before_the_start_is_the_start(self):
        rows = HEAD + [["IE", "1", "2008-09", "2007-01", None, "1", "x"]]
        self.assertEqual([("IRL", "2008-09", "2008-09", "ESRB")], bs.esrb_spans(rows))


class LaevenValencia(unittest.TestCase):
    def test_whole_years_start_to_end(self):
        rows = [{"iso": "AGO", "start": "2016", "end": "2020", "borderline": "0"}]
        self.assertEqual([("AGO", "2016-01", "2020-12", "Laeven-Valencia")], bs.lv_spans(rows, []))

    def test_borderline_left_out(self):
        rows = [{"iso": "KAZ", "start": "2008", "end": "2008", "borderline": "1"}]
        self.assertEqual([], bs.lv_spans(rows, []))

    def test_the_esrbs_crisis_within_two_years_stands(self):
        rows = [{"iso": "IRL", "start": "2008", "end": "2012", "borderline": "0"},
                {"iso": "IRL", "start": "1985", "end": "1986", "borderline": "0"}]
        esrb = [("IRL", "2008-09", "2013-12", "ESRB")]
        self.assertEqual([("IRL", "1985-01", "1986-12", "Laeven-Valencia")], bs.lv_spans(rows, esrb))


if __name__ == "__main__":
    unittest.main()
