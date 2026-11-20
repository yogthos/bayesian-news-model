"""Tests for scripts/trade-partners.py: each country's goods exports to
Russia, China and the EU as a share of its exports to the world, annual.

  python3 -I scripts/trade-partners-test.py
"""
import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("trade_partners", os.path.join(HERE, "trade-partners.py"))
tp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tp)


def doc(ref, ind, cp, periods, values):
    return {"dimensions": {"REF_AREA": ref, "INDICATOR": ind, "COUNTERPART_AREA": cp, "FREQ": "A"},
            "period": periods, "value": values}


class Shares(unittest.TestCase):
    def test_share_of_world_exports(self):
        docs = [doc("KZ", "TXG_FOB_USD", "RU", ["2013", "2014"], [10.0, 8.0]),
                doc("KZ", "TXG_FOB_USD", "W00", ["2013", "2014"], [80.0, 80.0]),
                doc("KZ", "TXG_FOB_USD", "CN", ["2013"], [20.0]),
                doc("KZ", "TMG_CIF_USD", "RU", ["2013"], [30.0]),
                doc("1C_080", "TXG_FOB_USD", "RU", ["2013"], [5.0])]
        rows = tp.shares(docs, {"KZ": "KAZ"})
        self.assertIn(["KAZ", "trade/export_share_ru", "a", "2013-12-31", "2014-06-30", repr(0.125)], rows)
        self.assertIn(["KAZ", "trade/export_share_ru", "a", "2014-12-31", "2015-06-30", repr(0.1)], rows)
        self.assertIn(["KAZ", "trade/export_share_cn", "a", "2013-12-31", "2014-06-30", repr(0.25)], rows)
        self.assertFalse([r for r in rows if r[1] == "trade/export_share_eu"], "no EU series given")
        self.assertEqual(3, len(rows), "imports and aggregates left out")

    def test_missing_or_zero_world_is_skipped(self):
        docs = [doc("KZ", "TXG_FOB_USD", "RU", ["2013"], [10.0]),
                doc("KZ", "TXG_FOB_USD", "W00", ["2013"], ["NA"])]
        self.assertEqual([], tp.shares(docs, {"KZ": "KAZ"}))


if __name__ == "__main__":
    unittest.main()
