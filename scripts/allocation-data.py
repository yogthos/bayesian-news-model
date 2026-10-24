"""The allocation channel's data (bead bnm-4hv.3; docs/COUPLED-SYSTEMS.md
5.1, 5.2): credit by sector and investment by industry, as known-year
stocks. src/bnm/allocation.clj builds the shares and growth from these
rows; this script only reads the sources and writes them long.

Writes data/derived/allocation/panel.csv:
  iso, year (the data year), known (the first year the value could be
  read: year + the source's lag), source, measure, sector, value, unit
and data/derived/allocation/coverage.txt (countries, years and rows per
source and measure).

Sources, each under data/raw with its SOURCE note, and the lag (the
project's rules, docs/PRINCIPLES.md 7 and COUPLED-SYSTEMS.md 4c):
  mv        Müller & Verner sectoral credit database (ReStud kit, Build/
            MuellerVerner.dta): year-end credit stocks by ISIC sector,
            households, NFCs and total private credit, local currency.
            Compiled from national sources: +2 (channel F1, national).
            Sector columns as the database has them, combinations (BC, FL,
            GI, ...) included; the groups are made in allocation.clj.
  bis       BIS total credit (WS_TC): credit to households (H) and
            non-financial corporations (N) from all lenders, % of GDP,
            adjusted for breaks, Q4: +1.
  pboc      People's Bank of China, sources and uses of credit funds by
            sector (RMB and foreign currency; RMB only, source pboc-rmb,
            where the combined table is a dead link): year-end loans to
            households (consumption, operating) and to non-financial
            enterprises and government organisations (of which medium and
            long term), CNY 100 million, 2007-2026: +1 (monthly tables,
            December out in January). Total social financing stocks by
            instrument (measure tsf, CNY trillion, 2015-2025; 2015 from its Q4
            column): +1.
  ecb       ECB BSI: MFI loans to households (and for consumption, house
            purchase, other) and NFCs by euro-area member and other EU
            states, December, EUR millions (the states outside the euro
            converted at current exchange rates): +1.
  ecb-nace  ECB BSI: loans to euro-area NFCs by NACE group (A, B, C, DE,
            F, G, HJ, I, LMN, Z), Q4, EUR millions, iso EMU: +1. An ECB
            estimate from national sources, in NACE Rev. 2 (adopted
            December 2006), so the years before 2008 are a back-estimate,
            not a +1 release; stocks are not adjusted for write-offs,
            reclassifications or securitisation.
  klems     EU KLEMS & INTANProd 2023: gross fixed capital formation
            (I_GFCF, current prices), capital stock (K_GFCF, current) and
            its volume index (Kq_GFCF) by NACE industry, 1995-2021: +2.
  wiod      WIOD 2016 Socio-Economic Accounts: nominal capital stock K by
            industry, 2000-2014, 43 countries, aggregated to the EU KLEMS
            industries (T and U, households as employers and
            extraterritorial bodies, only in TOT, as in EU KLEMS's R-S):
            +3 (an input-output release).
  lrwiod    Long-run WIOD 1.1 national tables: gross fixed capital
            formation by product (column xGFCF), USD millions, 1965-2000,
            25 countries: +3. The national tables exist only for benchmark
            years; the others are interpolated between benchmarks by GRAS
            (Woltjer, Gouma and Timmer 2021, GGDC memo 190), so a
            non-benchmark year's product shares use a later benchmark and
            +3 does not cover them (released 2021, so all of it is
            hindsight before then).

A value's known year is its data year plus the lag; the vintage is not
modelled (these are the latest vintages, revised since first release).
The lag is the national release's (docs/PRINCIPLES.md 7), not the
compilation's: Müller-Verner was compiled once (v1.0, 2023) from
national sources, with the breaks its country sheets adjust; EU KLEMS
began in 2007 (NACE Rev. 2 from 2012) and WIOD in 2012, so their years
before those releases were not known at +2 or +3 in any edition.

Usage: python -I scripts/allocation-data.py
       python -I scripts/allocation-data.py --selftest
"""
import csv
import html
import io
import os
import re
import zipfile

import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "data", "raw")
OUT = os.path.join(ROOT, "data", "derived", "allocation")

LAG = {"mv": 2, "bis": 1, "pboc": 1, "pboc-rmb": 1, "ecb": 1, "ecb-nace": 1, "klems": 2, "wiod": 3, "lrwiod": 3}

edn = open(os.path.join(ROOT, "resources", "bnm", "countries.edn"), encoding="utf-8").read()
ISO2 = dict(re.findall(r':iso2 "([A-Z]{2})",\s*:iso3 "([A-Z]{3})"', edn))
ISO2.update({"UK": "GBR", "EL": "GRC", "U2": "EMU", "XK": "XKX"})

rows = []


def add(iso, year, source, measure, sector, value, unit):
    if iso is None or value is None or pd.isna(value):
        return
    year = int(year)
    rows.append((iso, year, year + LAG[source], source, measure, sector, repr(float(value)), unit))


# -- Müller-Verner -------------------------------------------------------------

def mv():
    path = os.path.join(RAW, "slow", "mullerverner", "ReStud_Replication_Kit_20230912", "Build", "MuellerVerner.dta")
    d = pd.read_stata(path)
    d = d.groupby(["country", "year"], as_index=False).last()
    cols = [c for c in d.columns if c not in ("country", "iso2c", "countryname", "date", "year")]
    for r in d.itertuples(index=False):
        for c in cols:
            add(r.country, r.year, "mv", "credit", c, getattr(r, c), "LCU")


# -- BIS total credit --------------------------------------------------------------

def bis():
    z = zipfile.ZipFile(os.path.join(RAW, "slow", "bis", "WS_TC.zip"))
    with z.open(z.namelist()[0]) as f:
        rd = csv.reader(io.TextIOWrapper(f, encoding="utf-8"))
        h = [x.split(":")[0] for x in next(rd)]
        for r in rd:
            r = dict(zip(h, r))
            code = lambda k: r[k].split(":")[0]
            if (code("FREQ") != "Q" or code("TC_LENDERS") != "A" or code("UNIT_TYPE") != "770"
                    or code("TC_ADJUST") != "A" or not r["TIME_PERIOD"].endswith("-Q4")):
                continue
            sector = {"H": "hh", "N": "nfc"}.get(code("TC_BORROWERS"))
            iso = ISO2.get(code("BORROWERS_CTY"))
            if sector and iso and iso != "EMU" and r["OBS_VALUE"]:
                add(iso, r["TIME_PERIOD"][:4], "bis", "credit", sector, float(r["OBS_VALUE"]), "% GDP")


# -- PBoC ------------------------------------------------------------------------------

def html_rows(path):
    b = open(path, "rb").read()
    try:
        s = b.decode("utf-8")
    except UnicodeDecodeError:
        # the older tables are GBK; a byte neither reads becomes U+FFFD in a
        # label, never a number, so a bad byte cannot pass as data
        s = b.decode("gb18030", errors="replace")
    out = []
    for tr in re.findall(r"<tr\b.*?</tr>", s, flags=re.S | re.I):
        out.append([re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]*>", "", c, flags=re.S))).strip()
                    for c in re.findall(r"<td\b.*?</td>", tr, flags=re.S | re.I)])
    return out


def excel_rows(path):
    d = pd.read_excel(path, header=None, dtype=str)
    return [["" if pd.isna(v) else re.sub(r"\s+", " ", str(v)).strip() for v in r] for r in d.itertuples(index=False)]


def december(table, year):
    """The column index of `year`.12 in the table's header row."""
    for r in table:
        for i, c in enumerate(r):
            if re.fullmatch(rf"{year}\.12", c):
                return i
    return None


def num(s):
    try:
        return float(s.replace(",", ""))
    except (ValueError, AttributeError):
        return None


def credit_by_sector(table, year):
    """{sector value} of a sources-and-uses table by sector, December:
    total, hh, hh-consumption, hh-operating, enterprise, enterprise-mlt."""
    j = december(table, year)
    if j is None:
        return {}
    out, sect = {}, None
    for r in table:
        if len(r) <= j or not r[0]:
            continue
        label, v = r[0], num(r[j])
        if v is None:
            continue
        if "Total Loans" in label and "total" not in out:
            out["total"] = v
        elif re.search(r"Loans to (Resident Sector|Households)", label):
            out["hh"], sect = v, "hh"
        elif "Loans to Non-financial" in label or "Loans to Non-finan" in label:
            out["enterprise"], sect = v, "ent"
        elif re.search(r"Non-ban|Overseas|境外|非银行", label):
            sect = None
        elif sect == "hh" and "Consumpt" in label and not re.search(r"Short|Medium|Mid", label):
            out["hh-consumption"] = out.get("hh-consumption", 0.0) + v
        elif sect == "hh" and "Operating" in label and not re.search(r"Short|Medium|Mid|Farm", label):
            out["hh-operating"] = out.get("hh-operating", 0.0) + v
        elif sect == "ent" and re.search(r"(Medium|Mid) & Long", label) and "enterprise-mlt" not in out:
            out["enterprise-mlt"] = v
    return out


TSF = {"人民币贷款": "rmb-loans", "外币贷款": "fx-loans", "委托贷款": "entrusted-loans", "信托贷款": "trust-loans",
       "未贴现银行承兑汇票": "undiscounted-acceptances", "企业债券": "corporate-bonds", "政府债券": "government-bonds",
       "非金融企业境内股票": "equity", "存款类金融机构资产支持证券": "abs", "贷款核销": "write-offs",
       "社会融资规模存量": "total"}


PERIOD = re.compile(r"\d{4}\.(\d{1,2}|Q\d)")


def tsf_stock(table, year):
    """{instrument stock} at December (Q4 in 2015's table) from the table's
    main block: the rows under its first header of periods, up to the next
    header. The later blocks are notes restating other years, in other
    columns and units, and are not read."""
    heads = [i for i, r in enumerate(table) if any(PERIOD.fullmatch(c.strip()) for c in r)]
    if not heads:
        return {}
    j = next((i for i, c in enumerate(table[heads[0]]) if re.fullmatch(rf"{year}\.(12|Q4)", c.strip())), None)
    if j is None:
        return {}
    end = heads[1] if len(heads) > 1 else len(table)
    out = {}
    for r in table[heads[0] + 1:end]:
        if len(r) <= j:
            continue
        lab = re.sub(r"^其中[:：]", "", r[0])
        for k, name in TSF.items():
            if lab.startswith(k) and name not in out and num(r[j]) is not None:
                out[name] = num(r[j])
    return out


def pboc():
    base = os.path.join(RAW, "econ", "pbc-tsf-credit")
    man = pd.read_csv(os.path.join(base, "manifest.tsv"), sep="\t", dtype=str)
    man = man[man.status == "ok"]
    for year in range(2007, 2027):
        m = man[man.year == str(year)]
        both = m[m.title.str.contains("本外币信贷收支表") & ~m.title.str.contains("存款类")
                 & ((year >= 2015) | m.title.str.contains("按部门"))]
        rmb = m[m.title.str.startswith("金融机构人民币信贷收支表") & m.title.str.contains("按部门")]
        got = {}
        for src, cand in (("pboc", both), ("pboc-rmb", rmb)):
            for f in cand.file:
                p = os.path.join(base, "files", f)
                t = html_rows(p) if f.lower().endswith(".htm") else excel_rows(p)
                got = credit_by_sector(t, year)
                if got.get("hh") and got.get("enterprise"):
                    break
            if got.get("hh") and got.get("enterprise"):
                for k, v in got.items():
                    add("CHN", year, src, "credit", k, v, "CNY 100m")
                break
        for f in m[m.title.str.contains("存量")].file:
            for k, v in tsf_stock(excel_rows(os.path.join(base, "files", f)), year).items():
                add("CHN", year, "pboc", "tsf", k, v, "CNY tn")


# -- ECB -----------------------------------------------------------------------------------

def ecb():
    base = os.path.join(RAW, "econ", "ecb-mfi-loans-by-sector")
    d = pd.read_csv(os.path.join(base, "bsi_loans_hh_nfc_m.csv.gz"), dtype=str)
    d = d[d.TIME_PERIOD.str.endswith("-12")]
    name = {("A20", "2240"): "nfc", ("A20", "2250"): "hh", ("A21", "2250"): "hh-consumption",
            ("A22", "2250"): "hh-house", ("A23", "2250"): "hh-other"}
    for r in d.itertuples(index=False):
        s = name.get((r.BS_ITEM, r.BS_COUNT_SECTOR))
        iso = ISO2.get(r.REF_AREA)
        if s and iso and r.OBS_VALUE:
            add(iso, r.TIME_PERIOD[:4], "ecb", "credit", s, float(r.OBS_VALUE), r.UNIT)
    n = pd.read_csv(os.path.join(base, "bsi_loans_nace_ea_q.csv"), dtype=str)
    n = n[n.TIME_PERIOD.str.endswith("-Q4")]
    for r in n.itertuples(index=False):
        if r.OBS_VALUE:
            add("EMU", r.TIME_PERIOD[:4], "ecb-nace", "credit", "nfc-" + r.BS_COUNT_SECTOR[4:], float(r.OBS_VALUE), "EUR m")


# -- investment and capital by industry ---------------------------------------------------

KLEMS_IND = ["A", "B", "C", "C10-C12", "C13-C15", "C16-C18", "C19", "C20", "C21", "C22-C23", "C24-C25", "C26",
             "C27", "C28", "C29-C30", "C31-C33", "D-E", "F", "G", "H", "I", "J", "K", "L", "M-N", "O-Q", "R-S", "TOT"]


def klems():
    path = os.path.join(RAW, "econ", "euklems-intanprod-2023", "capital_accounts.csv")
    d = pd.read_csv(path, usecols=["nace_r2_code", "geo_code", "year", "I_GFCF", "K_GFCF", "Kq_GFCF"])
    d = d[d.nace_r2_code.isin(KLEMS_IND)]
    for r in d.itertuples(index=False):
        iso = ISO2.get(r.geo_code)
        if not iso:
            continue  # EU and euro-area aggregates
        add(iso, r.year, "klems", "gfcf", r.nace_r2_code, r.I_GFCF, "LCU m")
        add(iso, r.year, "klems", "capital", r.nace_r2_code, r.K_GFCF, "LCU m")
        add(iso, r.year, "klems", "capital-volume", r.nace_r2_code, r.Kq_GFCF, "index")


WIOD_TO_KLEMS = {
    "A01": "A", "A02": "A", "A03": "A", "B": "B", "C10-C12": "C10-C12", "C13-C15": "C13-C15",
    "C16": "C16-C18", "C17": "C16-C18", "C18": "C16-C18", "C19": "C19", "C20": "C20", "C21": "C21",
    "C22": "C22-C23", "C23": "C22-C23", "C24": "C24-C25", "C25": "C24-C25", "C26": "C26", "C27": "C27",
    "C28": "C28", "C29": "C29-C30", "C30": "C29-C30", "C31_C32": "C31-C33", "C33": "C31-C33",
    "D35": "D-E", "E36": "D-E", "E37-E39": "D-E", "F": "F", "G45": "G", "G46": "G", "G47": "G",
    "H49": "H", "H50": "H", "H51": "H", "H52": "H", "H53": "H", "I": "I", "J58": "J", "J59_J60": "J",
    "J61": "J", "J62_J63": "J", "K64": "K", "K65": "K", "K66": "K", "L68": "L", "M69_M70": "M-N",
    "M71": "M-N", "M72": "M-N", "M73": "M-N", "M74_M75": "M-N", "N": "M-N", "O84": "O-Q", "P85": "O-Q",
    "Q": "O-Q", "R_S": "R-S", "T": None, "U": None}


def wiod():
    d = pd.read_excel(os.path.join(RAW, "io", "wiod2016", "Socio_Economic_Accounts.xlsx"), sheet_name="DATA")
    d = d[d.variable == "K"]
    years = [c for c in d.columns if isinstance(c, int)]
    missing = set(d.code) - set(WIOD_TO_KLEMS)
    assert not missing, missing
    d = d.assign(ind=d.code.map(WIOD_TO_KLEMS))
    g = d.dropna(subset=["ind"]).groupby(["country", "ind"])[years].sum(min_count=1)
    manuf = d[d.ind.str.startswith("C", na=False)].groupby("country")[years].sum(min_count=1)
    tot = d.groupby("country")[years].sum(min_count=1)
    for (iso, ind), r in g.iterrows():
        for y in years:
            add(iso, y, "wiod", "capital", ind, r[y], "LCU m")
    for name, frame in (("C", manuf), ("TOT", tot)):
        for iso, r in frame.iterrows():
            for y in years:
                add(iso, y, "wiod", "capital", name, r[y], "LCU m")


def lrwiod():
    d = pd.read_csv(os.path.join(RAW, "io", "lrwiod", "lr_wiod_niot_final.csv"),
                    usecols=["countrycode", "year", "row_id", "row_isic3", "col_isic3", "value"])
    d = d[(d.col_isic3 == "xGFCF") & (d.row_id <= 23)]
    for r in d.itertuples(index=False):
        add(r.countrycode, r.year, "lrwiod", "gfcf-product", r.row_isic3, r.value, "USD m")


def coverage():
    p = pd.DataFrame(rows, columns=["iso", "year", "known", "source", "measure", "sector", "value", "unit"])
    lines = ["source measure: countries, years, rows, lag"]
    for (s, m), g in p.groupby(["source", "measure"]):
        lines.append(f"{s} {m}: {g.iso.nunique()} countries, {g.year.min()}-{g.year.max()}, {len(g)} rows, +{LAG[s]}")
    return "\n".join(lines) + "\n"


def main():
    for f in (mv, bis, pboc, ecb, klems, wiod, lrwiod):
        f()
    os.makedirs(OUT, exist_ok=True)
    rows.sort()
    with open(os.path.join(OUT, "panel.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["iso", "year", "known", "source", "measure", "sector", "value", "unit"])
        w.writerows(rows)
    cov = coverage()
    open(os.path.join(OUT, "coverage.txt"), "w").write(cov)
    print(cov, end="")


def selftest():
    """Checks on toy tables: python -I scripts/allocation-data.py --selftest."""
    # a TSF stock table whose notes restate other years below the main block,
    # with this year's December in another column: only the main block counts
    t = [["", "2018.1", "", "2018.12", ""],
         ["社会融资规模存量", "180.0", "1", "190.0", "1"],
         ["其中:人民币贷款", "121.7", "1", "134.7", "1"],
         ["委托贷款", "13.9", "1", "12.4", "1"],
         ["注：1. restated", "", "", "", ""],
         ["", "2018.7", "2018.12", "", ""],
         ["人民币贷款", "128.0", "4805", "", ""],
         ["贷款核销", "1.0", "15406", "", ""]]
    got = tsf_stock(t, 2018)
    assert got == {"total": 190.0, "rmb-loans": 134.7, "entrusted-loans": 12.4}, got
    # December found only in a notes block: the main block has no December
    assert tsf_stock([["", "2019.1"], ["人民币贷款", "1.0"], ["", "2019.12"], ["人民币贷款", "9.0"]], 2019) == {}
    # WIOD: households as employers and extraterritorial bodies are not R-S
    # (EU KLEMS's R-S is R and S); they stay in the total
    assert WIOD_TO_KLEMS["T"] is None and WIOD_TO_KLEMS["U"] is None
    print("allocation-data selftest ok")


if __name__ == "__main__":
    import sys
    if sys.argv[1:] == ["--selftest"]:
        selftest()
    else:
        main()
