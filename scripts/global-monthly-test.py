"""Tests for scripts/global-monthly.py: the global drivers, monthly, each
available one month after its period, as series.tsv lines.

  python3 -I scripts/global-monthly-test.py
"""
import importlib.util
import io
import math
import os
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("global_monthly", os.path.join(HERE, "global-monthly.py"))
gm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gm)


class Monthly(unittest.TestCase):
    def test_daily_fred_averages_to_months(self):
        csv = "observation_date,VIXCLS\n1990-01-02,10\n1990-01-03,20\n1990-01-04,.\n1990-02-01,30\n"
        self.assertEqual({(1990, 1): 15.0, (1990, 2): 30.0}, gm.fred_monthly(io.StringIO(csv)))

    def test_lines_are_available_a_month_later(self):
        self.assertEqual(["world", "global/oil", "m", "1990-12-31", "1991-01-31", repr(math.log(20.0))],
                         gm.line("global/oil", 1990, 12, math.log(20.0)))

    def test_baa_spread_is_baa_less_ten_year(self):
        baa = {(1990, 1): 9.0, (1990, 2): 9.5}
        gs10 = {(1990, 1): 8.0}
        self.assertEqual({(1990, 1): 1.0}, gm.spread(baa, gs10))

    def test_us_eer_from_the_bis_flat_file(self):
        head = "STRUCTURE,STRUCTURE_ID,ACTION,FREQ:Frequency,EER_TYPE:Type,EER_BASKET:Basket,REF_AREA:Reference area,TIME_PERIOD:Time period or range,OBS_VALUE:Observation Value\n"
        rows = ['dataflow,BIS:WS_EER(1.0),I,M: Monthly,N: Nominal,N: Narrow (27 economies),US: United States,1964-01,80.5',
                'dataflow,BIS:WS_EER(1.0),I,M: Monthly,R: Real,N: Narrow (27 economies),US: United States,1964-01,99',
                'dataflow,BIS:WS_EER(1.0),I,M: Monthly,N: Nominal,B: Broad (64 economies),US: United States,1994-01,90',
                'dataflow,BIS:WS_EER(1.0),I,M: Monthly,N: Nominal,N: Narrow (27 economies),DK: Denmark,1964-01,70',
                'dataflow,BIS:WS_EER(1.0),I,D: Daily,N: Nominal,N: Narrow (27 economies),US: United States,1964-01-02,81']
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z:
            z.writestr("WS_EER_csv_flat.csv", head + "\n".join(rows) + "\n")
        buf.seek(0)
        self.assertEqual({(1964, 1): 80.5}, gm.us_eer(buf))

    def test_food_is_copied_from_the_fast_table(self):
        tsv = "world\tfast/food\tm\t1960-01-31\t1960-02-29\t3.05\nworld\tfast/energy\tm\t1960-01-31\t1960-02-29\t2.0\n"
        self.assertEqual([["world", "global/food", "m", "1960-01-31", "1960-02-29", "3.05"]], gm.food(io.StringIO(tsv)))


if __name__ == "__main__":
    unittest.main()
