"""Tests for scripts/ewn-reserves.py: the standard-library xlsx reader gives
the values the sheet holds, and a malformed cell fails loudly with its
address rather than with an AttributeError or TypeError (bead bnm-56c.3).

  python3 -I scripts/ewn-reserves-test.py
"""
import csv
import importlib.util
import os
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("ewn_reserves", os.path.join(HERE, "ewn-reserves.py"))
ewn = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ewn)

MAIN = 'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
HEADER = ["Country", "IFS_Code", "Year", "FX Reserves minus gold", "GDP (US$)"]


def xlsx(d, rows_xml, strings):
    """An xlsx with one sheet whose rows are `rows_xml` and shared strings `strings`."""
    path = os.path.join(d, "t.xlsx")
    sst = "".join(f"<si><t>{s}</t></si>" for s in strings)
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("xl/sharedStrings.xml", f"<sst {MAIN}>{sst}</sst>")
        z.writestr("xl/worksheets/sheet1.xml", f"<worksheet {MAIN}><sheetData>{rows_xml}</sheetData></worksheet>")
    return path


def header_row():
    return '<row r="1">' + "".join(f'<c r="{c}1" t="s"><v>{i}</v></c>' for i, c in enumerate("ABCDE")) + "</row>"


def data_row(n, cells):
    return f'<row r="{n}">{cells}</row>'


class ReaderTest(unittest.TestCase):
    def convert(self, rows_xml, strings=None):
        with tempfile.TemporaryDirectory() as d:
            src = xlsx(d, header_row() + rows_xml, HEADER + (strings or ["Sri Lanka"]))
            out = os.path.join(d, "out.csv")
            ewn.convert(src, out)
            with open(out) as f:
                return list(csv.reader(f))

    def test_values(self):
        rows = self.convert(data_row(2, '<c r="A2" t="s"><v>5</v></c><c r="B2"><v>524</v></c><c r="C2"><v>2021</v></c>'
                                        '<c r="D2"><v>2962.5</v></c><c r="E2"><v>88609</v></c>'))
        self.assertEqual(rows[1], ["524", "Sri Lanka", "2021", "2022", "2962.5", "88609"])

    def test_cells_without_a_reference_take_the_next_column(self):
        rows = self.convert(data_row(2, '<c t="s"><v>5</v></c><c><v>524</v></c><c><v>2021</v></c>'
                                        '<c r="D2"><v>2962.5</v></c><c><v>88609</v></c>'))
        self.assertEqual(rows[1], ["524", "Sri Lanka", "2021", "2022", "2962.5", "88609"])

    def test_a_malformed_reference_names_the_cell(self):
        with self.assertRaisesRegex(ValueError, "1A"):
            self.convert(data_row(2, '<c r="1A" t="s"><v>5</v></c>'))

    def test_an_empty_shared_string_names_the_cell(self):
        with self.assertRaisesRegex(ValueError, "A2"):
            self.convert(data_row(2, '<c r="A2" t="s"><v/></c><c r="C2"><v>2021</v></c>'))

    def test_a_shared_string_out_of_range_names_the_cell(self):
        with self.assertRaisesRegex(ValueError, "A2"):
            self.convert(data_row(2, '<c r="A2" t="s"><v>99</v></c><c r="C2"><v>2021</v></c>'))


SRC = ewn.SRC


@unittest.skipUnless(os.path.exists(SRC), "EWN workbook not on disk")
class KnownValuesTest(unittest.TestCase):
    """Values read off the Dataset sheet's raw XML (cells L9843, L9844, Q9843)."""

    def test_sri_lanka(self):
        with tempfile.TemporaryDirectory() as d:
            out = os.path.join(d, "out.csv")
            ewn.convert(SRC, out)
            with open(out) as f:
                got = {(r[1], r[2]): r for r in csv.reader(f)}
        self.assertAlmostEqual(float(got[("Sri Lanka", "2021")][4]), 2962.48448637687, places=6)
        self.assertEqual(got[("Sri Lanka", "2021")][5], "88609")
        self.assertAlmostEqual(float(got[("Sri Lanka", "2022")][4]), 33.1518240465162, places=6)
        self.assertEqual(got[("Sri Lanka", "2021")][3], "2022")


if __name__ == "__main__":
    unittest.main()
