"""Tests for target_audit.py (bnm-7yg.8.11 step 1).

  python3 -I docs/studies/target_audit_test.py
"""
import importlib.util
import os
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("target_audit", os.path.join(HERE, "target_audit.py"))
ta = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ta)


class Sources(unittest.TestCase):
    def test_an_onset_is_dated_by_each_source_within_a_year(self):
        src = {"LV": {2001}, "RR": {2002, 2010}, "GMD": {2005}}
        self.assertEqual(ta.dated_by(2002, src), ["LV", "RR"])
        self.assertEqual(ta.dated_by(2005, src), ["GMD"])
        self.assertEqual(ta.dated_by(2008, src), [])

    def test_runs_start(self):
        self.assertEqual(ta.runs_start({1990, 1991, 1995}), {1990, 1995})


class Coverage(unittest.TestCase):
    def test_share_of_non_zero_inputs_leaves_out_the_intercept(self):
        X = np.array([[1.0, 0.0, 2.0, 0.0], [1.0, 0.0, 0.0, 0.0]])
        self.assertEqual(list(ta.nonzero_share(X)), [1 / 3, 0.0])

    def test_a_group_is_present_when_any_of_its_inputs_is(self):
        X = np.array([[1.0, 0.0, 2.0, 0.0], [1.0, 0.0, 0.0, 0.0]])
        self.assertEqual(list(ta.present(X, [0, 1, 2])), [True, False])


if __name__ == "__main__":
    unittest.main()
