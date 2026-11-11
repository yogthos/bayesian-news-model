"""Foreign exchange reserves by country and year from Lane & Milesi-Ferretti's
External Wealth of Nations (the copy on disk), for the buffer model's
reserves record (bnm.buffers, resources/buffers/stocks.edn; bead bnm-56c.3).

Reads the xlsx with the standard library only (no openpyxl): the data
sheet is the one whose header row names "FX Reserves minus gold".

Writes data/derived/buffers/reserves.csv:
  ifs_code,country,year,known,reserves_usd_m,gdp_usd_m (country quoted where it has a comma)
known is year + 1: the year-end stock is published by the central bank
within months (EWN itself later; its values are the current vintage).

    python -I scripts/ewn-reserves.py
"""
import csv
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "data", "raw", "econ", "lmf-external-wealth", "EWN-dataset-year-end-2024_4.9.26.xlsx")
OUT = os.path.join(ROOT, "data", "derived", "buffers", "reserves.csv")
NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def col_index(ref):
    """The 0-based column of a cell reference such as "AB12"."""
    m = re.fullmatch(r"([A-Z]+)[0-9]+", ref or "")
    if m is None:
        raise ValueError(f"malformed cell reference {ref!r}")
    n = 0
    for ch in m.group(1):
        n = n * 26 + (ord(ch) - 64)
    return n - 1


def cell_value(c, ref, strings):
    """A cell's text: a shared string, an inline string, or the raw value."""
    v = c.find(NS + "v")
    if v is None:
        t = c.find(NS + "is")
        return "".join(t.itertext()) if t is not None else None
    if c.get("t") == "s":
        try:
            return strings[int(v.text or "")]
        except (ValueError, IndexError):
            raise ValueError(f"cell {ref}: bad shared-string index {v.text!r}") from None
    return v.text


def rows(z, name, strings):
    """{column: text} per row of sheet `name`. A cell without a reference
    (optional in OOXML) takes the column after the previous cell."""
    for _, el in ET.iterparse(z.open(name)):
        if el.tag == NS + "row":
            out = {}
            col = -1
            for c in el.findall(NS + "c"):
                r = c.get("r")
                col = col_index(r) if r is not None else col + 1
                ref = r or f"row {el.get('r')} column {col + 1}"
                out[col] = cell_value(c, ref, strings)
            el.clear()
            yield out


def convert(src, out_path):
    """Write the reserves CSV from the workbook at `src`; the rows written,
    or None when no sheet names the reserves column."""
    z = zipfile.ZipFile(src)
    strings = ["".join(si.itertext()) for si in ET.parse(z.open("xl/sharedStrings.xml")).getroot()]
    sheets = sorted(n for n in z.namelist() if re.match(r"xl/worksheets/sheet\d+\.xml$", n))
    for name in sheets:
        it = rows(z, name, strings)
        header = None
        for r in it:
            vals = set(r.values())
            if "FX Reserves minus gold" in vals and "Country" in vals and "Year" in vals:
                header = {v: k for k, v in r.items() if v}
                break
        if header is None:
            continue
        ci, yi, ri = header["Country"], header["Year"], header["FX Reserves minus gold"]
        gi = next((header[k] for k in header if k.startswith("GDP (US$)")), None)
        isoi = next((header[k] for k in header if k in ("IFS_Code", "ISO3", "IFS code")), None)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        n = 0
        with open(out_path, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["ifs_code", "country", "year", "known", "reserves_usd_m", "gdp_usd_m"])
            for r in it:
                c, y, v = r.get(ci), r.get(yi), r.get(ri)
                if not c or not y or v in (None, ""):
                    continue
                try:
                    y = int(float(y)); v = float(v)
                except ValueError:
                    continue
                g = r.get(gi) if gi is not None else None
                w.writerow([r.get(isoi, "") if isoi is not None else "", c, y, y + 1, v, g or ""])
                n += 1
        print(f"{name}: {n} rows -> {out_path}; columns {sorted(header)[:12]}...")
        return n
    return None


def main():
    if convert(SRC, OUT) is None:
        print("no sheet names FX Reserves minus gold", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
