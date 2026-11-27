"""Tests for sovereign_backwards.py (bnm-7yg.6).

  python3 -I docs/studies/sovereign_backwards_test.py
"""
import importlib.util
import math
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("sovereign_backwards", os.path.join(HERE, "sovereign_backwards.py"))
sb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sb)


class SovereignBackwards(unittest.TestCase):
    def test_since_last_reads_only_known_onsets(self):
        onsets = {"ARG": [12 * 2001 + 11, 12 * 2020 + 4]}
        t = 12 * 2003 + 11
        self.assertEqual(sb.since_band(onsets, "ARG", t), "0-8")
        # the May 2020 onset is not known until 18 months later, November 2021
        self.assertEqual(sb.since_band(onsets, "ARG", 12 * 2021 + 9), "9+")
        self.assertEqual(sb.since_band(onsets, "ARG", 12 * 2021 + 10), "0-8")
        self.assertEqual(sb.since_band(onsets, "ARG", 12 * 2022 + 11), "0-8")
        self.assertEqual(sb.since_band(onsets, "BRA", t), "none")
        # onsets before 1970 do not count
        self.assertEqual(sb.since_band({"X": [12 * 1960]}, "X", t), "none")

    def test_signed_auc(self):
        ps = [(1.0, True), (0.0, False)]
        self.assertEqual(sb.signed_auc(ps, +1), 1.0)
        self.assertEqual(sb.signed_auc(ps, -1), 0.0)
        self.assertTrue(math.isnan(sb.signed_auc([(float("nan"), True), (1.0, False)], +1)))

    def test_age(self):
        s = [(100, 105, 1.0), (112, 130, 2.0)]
        self.assertEqual(sb.age(s, 120), 20)
        self.assertIsNone(sb.age(s, 50))


if __name__ == "__main__":
    unittest.main()
