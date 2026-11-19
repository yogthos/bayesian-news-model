"""Tests for scripts/lbs-claims.py: the BIS locational banking statistics'
cross-border claims, reporting country on counterparty country.

  python3 -I scripts/lbs-claims-test.py
"""
import csv
import importlib.util
import io
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("lbs_claims", os.path.join(HERE, "lbs-claims.py"))
lc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lc)

HEADER = ("STRUCTURE,STRUCTURE_ID,ACTION,FREQ:Frequency,L_MEASURE:Measure,L_POSITION:Balance sheet position,"
          "L_INSTR:Type of instruments,L_DENOM:Currency denomination,L_CURR_TYPE:Currency type of reporting country,"
          "L_PARENT_CTY:Parent country,L_REP_BANK_TYPE:Type of reporting institutions,L_REP_CTY:Reporting country,"
          "L_CP_SECTOR:Counterparty sector,L_CP_COUNTRY:Counterparty country,L_POS_TYPE:Position type,"
          "TIME_PERIOD:Time period or range,OBS_VALUE:Observation Value,DECIMALS:Decimals,UNIT_MEASURE:Unit of measure,"
          "UNIT_MULT:Unit Multiplier,TIME_FORMAT:Time Format,COLLECTION:Collection Indicator,"
          "ORG_VISIBILITY:Organisation visibility,AVAILABILITY:Availability,TITLE_GRP:Title,"
          "OBS_STATUS:Observation Status,OBS_CONF:Observation confidentiality,OBS_PRE_BREAK:Pre-Break Observation")
BANKS = '"A: All reporting banks/institutions (domestic, foreign, consortium and unclassified)"'


def line(freq="Q: Quarterly", measure="S: Amounts outstanding / Stocks", pos="C: Total claims",
         denom="TO1: All currencies", rep="US: United States", sector="A: All sectors",
         cp="GB: United Kingdom", ptype="N: Cross-border", period="2007-Q2", value="9080.0", mult="6: Millions"):
    return (f"dataflow,BIS:WS_LBS_D_PUB(1.0): Locational banking,I,{freq},{measure},{pos},A: All instruments,{denom},"
            f"A: All currencies (=D+F+U),5J: All countries,{BANKS},{rep},{sector},{cp},{ptype},{period},{value},"
            f"3: Three,USD: US dollar,{mult},,E: End of period,E: Public,,,A: Normal value,F: Free,")


ISO2 = {"US": "USA", "GB": "GBR", "FR": "FRA"}


def parse(text):
    return list(lc.claims(io.StringIO(HEADER + "\n" + text + "\n"), ISO2))


class Claims(unittest.TestCase):
    def test_keeps_cross_border_total_claims_in_millions(self):
        self.assertEqual(parse(line()), [("USA", "GBR", 2007 * 12 + 5, 9080.0, 2007 * 12 + 5 + lc.LAG)])

    def test_quarter_end_months(self):
        self.assertEqual(parse(line(period="1977-Q4"))[0][2], 1977 * 12 + 11)
        self.assertEqual(parse(line(period="2001-Q1"))[0][2], 2001 * 12 + 2)

    def test_drops_everything_else(self):
        for kw in ({"freq": ""}, {"measure": "F: FX and break adjusted change (BIS calculated)"},
                   {"pos": "L: Total liabilities"}, {"denom": "USD: US dollar"}, {"sector": "B: Banks, total"},
                   {"ptype": "A: All"}, {"cp": "5J: All countries"}, {"rep": "5A: All reporting countries"},
                   {"value": "NaN"}, {"value": ""}, {"period": ""}):
            self.assertEqual(parse(line(**kw)), [], kw)

    def test_a_unit_other_than_millions_stops_the_build(self):
        with self.assertRaises(ValueError):
            parse(line(mult="9: Billions"))


if __name__ == "__main__":
    unittest.main()
