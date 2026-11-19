"""Tests for scripts/alignment-tie.py: the western tie from public treaty
and membership dates, fixed before any check against an outcome.

  python3 -I scripts/alignment-tie-test.py
"""
import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("alignment_tie", os.path.join(HERE, "alignment-tie.py"))
at = importlib.util.module_from_spec(spec)
spec.loader.exec_module(at)


class Tie(unittest.TestCase):
    def test_members_from_their_accession_year(self):
        self.assertEqual(at.tie("POL", 1998), 0.0)
        self.assertEqual(at.tie("POL", 1999), 1.0)   # NATO
        self.assertEqual(at.tie("SWE", 1994), 0.0)
        self.assertEqual(at.tie("SWE", 1995), 1.0)   # EU before NATO
        self.assertEqual(at.tie("FIN", 2023), 1.0)

    def test_leaving_ends_a_membership_not_the_others(self):
        self.assertEqual(at.components("GBR", 2019)["eu"], 1)
        self.assertEqual(at.components("GBR", 2020)["eu"], 0)
        self.assertEqual(at.tie("GBR", 2020), 1.0)   # still NATO
        self.assertEqual(at.components("NZL", 1985)["us_treaty"], 1)
        self.assertEqual(at.components("NZL", 1986)["us_treaty"], 0)

    def test_major_non_nato_allies_are_half(self):
        self.assertEqual(at.tie("NZL", 1995), 0.0)
        self.assertEqual(at.tie("NZL", 1996), 0.5)
        self.assertEqual(at.tie("PAK", 2003), 0.0)
        self.assertEqual(at.tie("PAK", 2004), 0.5)
        self.assertEqual(at.tie("AFG", 2022), 0.0)   # rescinded July 2022

    def test_the_rio_treaty_alone_is_no_tie(self):
        for c in ("MEX", "VEN", "CUB", "PER"):
            self.assertEqual(at.tie(c, 2021), 0.0, c)

    def test_treaty_allies_outside_nato(self):
        for c in ("JPN", "KOR", "AUS", "PHL", "THA"):
            self.assertEqual(at.tie(c, 2021), 1.0, c)

    def test_known_in_its_own_year(self):
        rows = list(at.rows(["POL"], 1998, 1999))
        self.assertIn(("POL", 1999, "western_tie", 1.0, 1999), rows)
        self.assertIn(("POL", 1998, "nato", 0, 1998), rows)


if __name__ == "__main__":
    unittest.main()
