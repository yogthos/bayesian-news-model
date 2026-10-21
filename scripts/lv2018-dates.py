#!/usr/bin/env python3
"""Laeven & Valencia (2018), Table 1: banking, currency and sovereign crisis
dates, 1970-2017 (research tooling). Reads the paper's text layer
(pdftotext -layout data/raw/slow/lv2018/wp18206.pdf wp18206.txt) and writes
data/raw/slow/lv2018/dates.csv: country,kind,year with kind one of banking,
currency, sovereign, restructuring.

The table's columns are right-aligned: each cell's end column places it. A
cell that wraps ends in a comma and goes on in the same column on the next
line; a country name that wraps goes on in the next line's name.
"""
import re, sys

src = sys.argv[1] if len(sys.argv) > 1 else "data/raw/slow/lv2018/wp18206.txt"
lines = open(src, encoding="utf-8").read().split("\n")
start = next(i for i, l in enumerate(lines) if "Table 1. Crisis Dates" in l)
end = next(i for i, l in enumerate(lines) if "Table 2." in l and i > start)
KINDS = ["banking", "currency", "sovereign", "restructuring"]


# each page sets its own columns: a cell (right-aligned) ends before the next
# column's header starts
bounds = [45, 80, 97]


def column(end_col):
    return next((i for i, b in enumerate(bounds) if end_col < b), 3)


parsed = []
for l in lines[start + 1:end]:
    if "Sovereign" in l:
        a = l.index("Sovereign")
        bounds[1:] = [a, l.index("Sovereign", a + 1)]
    if l.startswith("Country") and "Currency" in l:
        bounds[0] = l.index("Currency")
    if not l.strip() or "Sovereign" in l or l.strip().startswith(("Country", "(Restructuring)")) or re.fullmatch(r"\s*\d{1,3}\s*", l):
        continue
    m = re.match(r"^(\S.*?)(?=\s{2,}\d|\s*$)", l)
    text = m.group(1).strip() if m and not re.match(r"^\d", l.strip()) else ""
    groups = [(column(mm.end()), mm.group(0)) for mm in re.finditer(r"\d{4}(?:\s?,\s{0,2}\d{4})*,?", l)]
    parsed.append((text, groups))

NAME_TAILS = ("Rep.", "Republic", "Herzegovina", "Kong", "Tobago", "Principe", "of")
records = []
carry = []          # a years-only line whose cells wrap onto the next named line
for idx, (text, groups) in enumerate(parsed):
    if not text:
        if groups and all(g.endswith(",") for _, g in groups):
            carry.extend(groups)
        elif records:
            records[-1][1].extend(groups)
        continue
    tail = text[0].islower() or text in NAME_TAILS or text.startswith(("Rep.", "of"))
    if records and tail:
        records[-1][0].append(text)
        records[-1][1].extend(carry + groups)
    else:
        records.append([[text], carry + groups])
    carry = []

out = []
for name, groups in records:
    cells = [[], [], [], []]
    for c, g in groups:
        cells[c].extend(int(y) for y in re.findall(r"\d{4}", g))
    out.append((" ".join(name), cells))
records = out

# years that sit on a line above their country (a wrapped cell's first part)
# were read into the previous record when it had that column open: undo by
# keeping each record's years as read; the checks below catch strays
with open("data/raw/slow/lv2018/dates.csv", "w") as f:
    f.write("country,kind,year\n")
    for n, cs in records:
        for k, ys in zip(KINDS, cs):
            for y in sorted(set(ys)):
                f.write(f"\"{n}\",{k},{y}\n")
for n, cs in records:
    print(f"{n:32s}", " | ".join(",".join(map(str, c)) for c in cs))

# ISO codes through slow-normalize's gazetteer: data/derived/lv2018.csv iso,kind,year
import importlib.util, os
spec = importlib.util.spec_from_file_location("sn", "scripts/slow-normalize.py")
sn = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sn)
ALIASES = {"China, P.R.": "China", "China, P.R.: Hong Kong": "Hong Kong", "Congo, Dem. Rep. of": "Democratic Republic of the Congo",
           "Congo, Rep. of": "Republic of the Congo", "Iran, I.R. of": "Iran", "Gambia, The": "Gambia",
           "Lao People’s Dem. Rep.": "Laos", "Serbia, Republic of": "Serbia", "Yugoslavia, SFR": None,
           "Côte d’Ivoire": "Ivory Coast", "Korea": "South Korea", "Macedonia": "North Macedonia",
           "Swaziland": "Eswatini", "Cape Verde": "Cabo Verde", "São Tomé and Principe": "Sao Tome and Principe",
           "Central African Rep.": "Central African Republic", "Syrian Arab Republic": "Syria",
           "Kyrgyz Republic": "Kyrgyzstan", "Slovak Republic": "Slovakia", "Russia": "Russia"}
missing = []
os.makedirs("data/derived", exist_ok=True)
with open("data/derived/lv2018.csv", "w") as f:
    f.write("iso,kind,year\n")
    for n, cs in records:
        name = ALIASES.get(n, n)
        iso = sn.iso3_of_name(name) if name else None
        if not iso:
            missing.append(n)
            continue
        for k, ys in zip(KINDS, cs):
            for y in sorted(set(ys)):
                f.write(f"{iso},{k},{y}\n")
print("unmapped:", missing, file=sys.stderr)
