"""Stock and flow series for the buffer model (docs/PLAN.md 1.8, bead
bnm-56c.5), read from data/raw/buffers and earlier downloads into tidy CSVs
under data/derived/buffers/ for bnm.buffers. The files and their columns
are listed in data/derived/buffers/README (written here too).

    python -I scripts/buffers-data.py [group ...]

Groups: reserves irfcl imf-credit external energy-us energy-eu jodi grain
dsr us-investment us-fixed-assets us-construction us-generators sec.

Raw files are untrusted data: parsed as CSV, JSON or xlsx values only.
openpyxl is needed for the xlsx groups (us-construction, us-generators).
"""
import csv
import gzip
import io
import json
import os
import re
import sys
import zipfile
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA = os.path.join(ROOT, "data")
RAW = os.path.join(DATA, "raw")
OUT = os.path.join(DATA, "derived", "buffers")


def raw(*p):
    return os.path.join(RAW, *p)


def write(name, header, rows):
    os.makedirs(OUT, exist_ok=True)
    rows = sorted(rows, key=lambda r: tuple("" if x is None else str(x) for x in r))
    with open(os.path.join(OUT, name), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)
    print("%s: %d rows" % (name, len(rows)))
    return rows


def num(s):
    s = (s or "").strip().replace(",", "")
    if s in ("", "NaN", "-", "x", ":", "NA", "(D)", "(NA)", "(S)", "(X)", "(Z)"):
        return None
    try:
        return float(s.split(" ")[0])
    except ValueError:
        return None


def ym(period):
    """'2001-M03' or '2001-03' to '2001-03'."""
    return period.replace("-M", "-")[:7]


# ---------------------------------------------------------------- IMF

def sdmx_csv(p):
    with gzip.open(p, "rt", encoding="utf-8-sig", newline="") as f:
        for r in csv.DictReader(f):
            if r.get("TIME_PERIOD") and r.get("OBS_VALUE") not in (None, ""):
                yield r


IL_SERIES = {("RXF11_REVS", "USD"): "reserves_ex_gold_usd", ("TRGMV_REVS", "USD"): "reserves_total_usd",
             ("RXF11FX_REVS", "USD"): "fx_reserves_usd", ("RGOLDMV_REVS", "XDR"): "gold_sdr"}


def il_rows(p):
    """Monthly headline reserves from IMF International Liquidity:
    iso,month,{reserves_ex_gold_usd, reserves_total_usd, fx_reserves_usd}."""
    t = defaultdict(dict)
    for r in sdmx_csv(p):
        k = IL_SERIES.get((r["INDICATOR"], r["UNIT"]))
        if k and r["FREQUENCY"] == "M" and k != "gold_sdr":
            v = num(r["OBS_VALUE"])
            if v is not None:
                scale = 10 ** int(r["SCALE"]) if r.get("SCALE") else 1
                t[(r["COUNTRY"], ym(r["TIME_PERIOD"]))][k] = v * scale
    cols = ["reserves_ex_gold_usd", "reserves_total_usd", "fx_reserves_usd"]
    return [(iso, m) + tuple(d.get(c) for c in cols) for (iso, m), d in t.items()], cols


IRFCL_SERIES = {
    "IRFCLDT1_IRFCL65_USD": "official_reserve_assets",
    "IRFCLDT1_IRFCLCDCFC_USD": "fx_currency_and_deposits",
    "IRFCLDT1_IRFCL32_USD": "fx_securities",
    "IRFCLDT1_IRFCL56_USD": "gold",
    "IRFCLDT1_IRFCL34_USD": "other_fx_assets",
    "IRFCLDT2_USD": "predetermined_net_drains_1y",
    "IRFCLDT2_IRFCL1T_SHP_USD": "forwards_short_positions_1y",
    "IRFCLDT3_T_USD": "contingent_net_drains_1y",
    "IRFCLDT4_IRFCL150_USD": "pledged_in_reserves",
    "IRFCLDT4_IRFCL52_USD": "pledged_assets",
}


def irfcl_rows(p):
    """The IMF reserves data template (IRFCL), monthly, USD:
    iso,month and the IRFCL_SERIES columns. Drains are as reported (the
    template reports outflows as negative numbers)."""
    t = defaultdict(dict)
    for r in sdmx_csv(p):
        k = IRFCL_SERIES.get(r["INDICATOR"])
        if k and r["FREQUENCY"] == "M":
            v = num(r["OBS_VALUE"])
            if v is not None:
                scale = 10 ** int(r["SCALE"]) if r.get("SCALE") else 1
                t[(r["COUNTRY"], ym(r["TIME_PERIOD"]))][k] = v * scale
    cols = list(IRFCL_SERIES.values())
    return [(iso, m) + tuple(d.get(c) for c in cols) for (iso, m), d in t.items()], cols


def fa_rows(p):
    """IMF credit outstanding by member, monthly, SDR: iso,month,
    gra_outstanding_sdr, prgt_outstanding_sdr, credit_outstanding_sdr, quota_sdr."""
    want = {"IMFOPGRA_XDR": "gra_outstanding_sdr", "F4PRGT_XDR": "prgt_outstanding_sdr",
            "CLIMF_XDR": "credit_outstanding_sdr", "IMFQ_XDR": "quota_sdr"}
    t = defaultdict(dict)
    for r in sdmx_csv(p):
        k = want.get(r["INDICATOR"])
        if k and r["FREQUENCY"] == "M" and len(r["COUNTRY"]) == 3:
            v = num(r["OBS_VALUE"])
            if v is not None:
                scale = 10 ** int(r["SCALE"]) if r.get("SCALE") else 1
                t[(r["COUNTRY"], ym(r["TIME_PERIOD"]))][k] = v * scale
    cols = list(want.values())
    return [(iso, m) + tuple(d.get(c) for c in cols) for (iso, m), d in t.items()], cols


# ---------------------------------------------------------------- WDI

WDI = {"FI.RES.TOTL.CD": "reserves_usd", "FI.RES.XGLD.CD": "reserves_ex_gold_usd",
       "FI.RES.TOTL.MO": "reserves_months_imports", "DT.DOD.DSTC.CD": "short_term_debt_usd",
       "DT.DOD.DSTC.IR.ZS": "short_term_debt_pct_reserves", "DT.DOD.DECT.CD": "external_debt_usd",
       "DT.TDS.DECT.CD": "external_debt_service_usd", "DT.TDS.DECT.EX.ZS": "debt_service_pct_exports",
       "BM.GSR.GNFS.CD": "imports_gs_usd", "NY.GNP.MKTP.CD": "gni_usd"}


def wdi_zip(zpath):
    """{(iso, year): value} and the set of country (not aggregate) codes."""
    vals, countries = {}, set()
    with zipfile.ZipFile(zpath) as z:
        for n in z.namelist():
            if n.startswith("Metadata_Country"):
                for r in csv.DictReader(io.TextIOWrapper(z.open(n), encoding="utf-8-sig")):
                    if (r.get("Region") or "").strip():
                        countries.add(r["Country Code"])
        for n in z.namelist():
            if n.startswith("API_"):
                lines = io.TextIOWrapper(z.open(n), encoding="utf-8-sig").read().splitlines()
                k = next(i for i, l in enumerate(lines) if l.startswith('"Country Name"'))
                for r in csv.DictReader(lines[k:]):
                    for y, v in r.items():
                        if y and y.isdigit() and num(v) is not None:
                            vals[(r["Country Code"], int(y))] = num(v)
    return vals, countries


def external_rows(d):
    t = defaultdict(dict)
    countries = set()
    for code, col in WDI.items():
        p = os.path.join(d, code + ".zip")
        if not os.path.exists(p):
            continue
        vals, cs = wdi_zip(p)
        countries |= cs
        for k, v in vals.items():
            t[k][col] = v
    cols = list(WDI.values())
    return [(iso, y) + tuple(v.get(c) for c in cols) for (iso, y), v in t.items() if iso in countries], cols


# ---------------------------------------------------------------- energy

EIA_SERIES = {
    "PET.WCESTUS1.W": ("crude_ex_spr", "kbbl"), "PET.WCSSTUS1.W": ("crude_spr", "kbbl"),
    "PET.WGTSTUS1.W": ("gasoline", "kbbl"), "PET.WDISTUS1.W": ("distillate", "kbbl"),
    "PET.WKJSTUS1.W": ("jet_fuel", "kbbl"), "PET.WTTSTUS1.W": ("total_stocks_incl_spr", "kbbl"),
    "PET.WRPUPUS2.W": ("product_supplied_total", "kbbl_per_day"), "PET.WCRFPUS2.W": ("crude_production", "kbbl_per_day"),
    "PET.WCRNTUS2.W": ("crude_net_imports", "kbbl_per_day"), "PET.WCRRIUS2.W": ("refinery_crude_input", "kbbl_per_day"),
    "PET.MCSSTUS1.M": ("crude_spr", "kbbl"), "PET.MCESTUS1.M": ("crude_ex_spr", "kbbl"),
    "PET.MTTUPUS2.M": ("product_supplied_total", "kbbl_per_day"),
    "NG.NW2_EPG0_SWO_R48_BCF.W": ("gas_working_storage_lower48", "bcf"),
    "NG.N5020US2.M": ("gas_working_storage", "mmcf"), "NG.N5030US2.M": ("gas_base_storage", "mmcf"),
    "NG.N9140US2.M": ("gas_consumption", "mmcf"), "NG.N9070US2.M": ("gas_production_dry", "mmcf"),
}


SERIES_ID = re.compile(r'"series_id"\s*:\s*"([^"]+)"')


def eia_bulk_rows(zpath):
    """date,frequency,series,value,unit,eia_id from an EIA bulk zip (one
    JSON object per line)."""
    out = []
    with zipfile.ZipFile(zpath) as z:
        for line in io.TextIOWrapper(z.open(z.namelist()[0]), encoding="utf-8"):
            m = SERIES_ID.search(line)
            sid = m.group(1) if m else None
            if sid not in EIA_SERIES:
                continue
            d = json.loads(line)
            name, unit = EIA_SERIES[sid]
            for t, v in d.get("data", []):
                t = str(t)
                date = "%s-%s-%s" % (t[:4], t[4:6], t[6:8]) if len(t) == 8 else "%s-%s" % (t[:4], t[4:6])
                if v is not None:
                    out.append((date, d["f"], name, float(v), unit, sid))
    return out


def eurostat_tsv(p):
    """[(dims dict, period, value)] from a Eurostat TSV (gzipped)."""
    out = []
    with gzip.open(p, "rt", encoding="utf-8") as f:
        head = f.readline().rstrip("\n").split("\t")
        names = head[0].split("\\")[0].split(",")
        periods = [h.strip() for h in head[1:]]
        for line in f:
            k = line.rstrip("\n").split("\t")
            dims = dict(zip(names, k[0].split(",")))
            for per, v in zip(periods, k[1:]):
                x = num(v.strip().split(" ")[0])
                if x is not None:
                    out.append((dict(dims), per, x))  # each row its own dict: callers may change it
    return out


EU_KEEP = {  # (table, dimension, codes kept): closing stocks, emergency stocks and the gas balance
    "nrg_stk_gasm": {"stk_flow": {"STKCL_NAT", "STKOP_NAT"}},
    "nrg_stk_oilm": {"stk_flow": {"STKCL_NAT", "STKCL_EUE", "STKCL_EUE_NAT_GOV", "STKCL_EUE_NAT_CSE", "STKCL_NAT_GOV"},
                     "siec": {"O4000", "O4100_TOT", "O4600", "O4652XR5210B", "O4671XR5220B", "O4661XR5230B"}},
    "nrg_cb_gasm": {"nrg_bal": {"IC_CAL_MG", "IC_OBS", "IMP", "EXP", "IPRD", "STK_CHG_MG"}},
}


def eu_rows(d):
    out = []
    for name, keep in EU_KEEP.items():
        p = os.path.join(d, name + ".tsv.gz")
        if not os.path.exists(p):
            continue
        for dims, per, x in eurostat_tsv(p):
            if any(dims.get(k) not in v for k, v in keep.items()):
                continue
            rest = {k: v for k, v in dims.items() if k not in ("geo", "freq")}
            out.append((name, dims.get("geo", ""), per, ";".join("%s=%s" % kv for kv in sorted(rest.items())), x))
    return out


JODI_KEEP = {("CRUDEOIL", "CLOSTLV"), ("CRUDEOIL", "REFINOBS"), ("CRUDEOIL", "INDPROD"), ("CRUDEOIL", "TOTIMPSB"),
             ("CRUDEOIL", "TOTEXPSB"), ("CRUDEOIL", "STOCKCH"), ("TOTPRODS", "CLOSTLV"), ("TOTPRODS", "TOTDEMO"),
             ("GASDIES", "CLOSTLV"), ("GASDIES", "TOTDEMO"), ("GASOLINE", "CLOSTLV"), ("GASOLINE", "TOTDEMO")}


def jodi_rows(zpath, i23, unit="KBBL"):
    """iso,month,product,flow,value (thousand barrels; per month for flows)
    with the JODI assessment code (1 good, 2 consult metadata, 3 not assessed)."""
    out = []
    with zipfile.ZipFile(zpath) as z:
        r = csv.reader(io.TextIOWrapper(z.open(z.namelist()[0]), encoding="utf-8-sig"))
        head = next(r)
        ia, it, ip, ifl, iu, iv, ic = (head.index(c) for c in ("REF_AREA", "TIME_PERIOD", "ENERGY_PRODUCT",
                                                              "FLOW_BREAKDOWN", "UNIT_MEASURE", "OBS_VALUE",
                                                              "ASSESSMENT_CODE"))
        for k in r:
            if k[iu] != unit or (k[ip], k[ifl]) not in JODI_KEEP:
                continue
            v = num(k[iv])
            iso = i23.get(k[ia])
            if v is not None and iso:
                out.append((iso, k[it], k[ip], k[ifl], v, k[ic]))
    return out


def iso2to3():
    m = {"XK": "XKX"}
    with open(os.path.join(DATA, "ref", "countryInfo.txt"), encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#"):
                k = line.split("\t")
                m[k[0]] = k[1]
    return m


# ---------------------------------------------------------------- grain

def psd_rows(zpath, names):
    """iso (or USDA name when unmapped, prefixed '?'),commodity,market_year,
    ending_stocks_kt,domestic_consumption_kt,stocks_to_use,estimate_month."""
    t = defaultdict(dict)
    with zipfile.ZipFile(zpath) as z:
        for r in csv.DictReader(io.TextIOWrapper(z.open("psd_grains_pulses.csv"), encoding="utf-8-sig")):
            a = r["Attribute_Description"]
            if a not in ("Ending Stocks", "Domestic Consumption"):
                continue
            nm = r["Country_Name"].strip()
            iso = names.get(nm.lower()) or "?" + nm
            k = (iso, r["Commodity_Description"].strip(), int(r["Market_Year"]))
            t[k]["s" if a == "Ending Stocks" else "u"] = num(r["Value"])
            t[k]["e"] = "%s-%s" % (r["Calendar_Year"], r["Month"])
    out = []
    for (iso, c, y), d in t.items():
        s, u = d.get("s"), d.get("u")
        out.append((iso, c, y, s, u, (s / u) if (s is not None and u) else None, d.get("e")))
    return out


PSD_NAMES = {"burma": "MMR", "congo (brazzaville)": "COG", "congo (kinshasa)": "COD", "gambia, the": "GMB",
             "bahamas, the": "BHS", "korea, south": "KOR", "korea, north": "PRK", "cote d'ivoire": "CIV",
             "european union": "EUU", "eu-15": "EUU", "russia": "RUS", "iran": "IRN", "syria": "SYR",
             "vietnam": "VNM", "laos": "LAO", "taiwan": "TWN", "venezuela": "VEN", "bolivia": "BOL",
             "tanzania": "TZA", "macedonia": "MKD", "north macedonia": "MKD", "moldova": "MDA",
             "yemen (sanaa)": "YEM", "yemen": "YEM", "cabo verde": "CPV", "eswatini": "SWZ", "turkey": "TUR",
             "turkiye": "TUR", "czechia": "CZE", "czech republic": "CZE", "macau": "MAC", "kyrgyzstan": "KGZ", "brunei": "BRN", "hong kong": "HKG",
             "world": "WLD"}


def psd_names():
    m = dict(PSD_NAMES)
    with open(os.path.join(DATA, "ref", "countryInfo.txt"), encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#"):
                k = line.split("\t")
                m.setdefault(k[4].strip().lower(), k[1])
    return m


# ---------------------------------------------------------------- BIS

def bis_flat(zpath):
    """Rows of a BIS flat CSV as {code: value} with 'CODE: label' cells cut to the code."""
    with zipfile.ZipFile(zpath) as z:
        r = csv.reader(io.TextIOWrapper(z.open(z.namelist()[0]), encoding="utf-8-sig"))
        head = [h.split(":")[0] for h in next(r)]
        for k in r:
            yield {h: v.split(":")[0].strip() if h not in ("OBS_VALUE",) else v for h, v in zip(head, k)}


def dsr_rows(zpath, i23):
    out = []
    for r in bis_flat(zpath):
        v = num(r["OBS_VALUE"])
        if v is None:
            continue
        a = r["BORROWERS_CTY"]
        out.append((i23.get(a, a), r["TIME_PERIOD"], {"H": "households", "N": "nonfinancial_corporations",
                                                      "P": "private_nonfinancial"}.get(r["DSR_BORROWERS"], r["DSR_BORROWERS"]), v))
    return out


# ---------------------------------------------------------------- BEA

def bea_rows(reg, data, tables):
    """table,line,series_code,label,metric,period,value from a BEA
    register and data file, for the given table ids."""
    meta = {}
    with open(reg, newline="", encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            for tl in r["TableId:LineNo"].split("|"):
                t, _, line = tl.partition(":")
                if t in tables:
                    meta.setdefault(r["%SeriesCode"], []).append((t, int(line), r["SeriesLabel"], r["MetricName"],
                                                                   int(r["DefaultScale"])))
    out = []
    with open(data, newline="", encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            for t, line, label, metric, scale in meta.get(r["%SeriesCode"], ()):
                v = num(r["Value"])
                if v is not None:
                    out.append((t, line, r["%SeriesCode"], label, metric, r["Period"], v * 10 ** -scale if scale else v))
    return out


# ---------------------------------------------------------------- Census, EIA-860M (xlsx)

def c30_month(d):
    """A C30 date cell (a datetime, an Excel serial number or text such as
    'Sep-26p') to ('2026-09', flag), flag p or r for preliminary or revised;
    (None, '') when the cell is not a date (notes, blank)."""
    import datetime
    if isinstance(d, datetime.datetime) or isinstance(d, datetime.date):
        return "%04d-%02d" % (d.year, d.month), ""
    if isinstance(d, (int, float)) and not isinstance(d, bool):
        if not 1 <= d < 2958466:  # Excel's serial range
            return None, ""
        dt = datetime.date(1899, 12, 30) + datetime.timedelta(days=int(d))
        return "%04d-%02d" % (dt.year, dt.month), ""
    s = str(d).strip()
    flag = s[-1] if s and s[-1] in "pr" else ""
    try:
        dt = datetime.datetime.strptime(s.rstrip("pr"), "%b-%y")
    except ValueError:
        return None, ""
    return "%04d-%02d" % (dt.year, dt.month), flag


def c30_rows(p, kind):
    import openpyxl
    wb = openpyxl.load_workbook(p, read_only=True)
    ws = wb.worksheets[0]
    head, out = None, []
    for r in ws.iter_rows(values_only=True):
        if r and r[0] == "Date":
            head = [str(h or "").replace("_x000D_", "").replace("\n", " ").strip().rstrip("0123456789") for h in r]
            continue
        if head is None or not r or not r[0]:
            continue
        m, flag = c30_month(r[0])
        if m is None:
            continue
        for h, v in zip(head[1:], r[1:]):
            if h and isinstance(v, (int, float)):
                out.append((kind, m, h, float(v), flag))
    return out


def generator_rows(p):
    """status,technology,state,year (operating, planned or retirement),generators,nameplate_mw,summer_mw."""
    import openpyxl
    wb = openpyxl.load_workbook(p, read_only=True)
    agg = defaultdict(lambda: [0, 0.0, 0.0])
    for ws in wb.worksheets:
        status = ws.title
        head = None
        for r in ws.iter_rows(values_only=True):
            if r and r[0] == "Entity ID":
                head = {str(h).strip(): i for i, h in enumerate(r) if h}
                continue
            if head is None or not r or not isinstance(r[0], (int, float)):
                continue
            ycol = next((c for c in ("Planned Operation Year", "Retirement Year", "Operating Year") if c in head), None)
            y = r[head[ycol]] if ycol else None
            k = (status, r[head["Technology"]] or "", r[head["Plant State"]] or "",
                 int(y) if isinstance(y, (int, float)) else "", r[head["Status"]] if "Status" in head else "")
            a = agg[k]
            a[0] += 1
            a[1] += float(r[head["Nameplate Capacity (MW)"]] or 0)
            s = r[head["Net Summer Capacity (MW)"]]
            a[2] += float(s) if isinstance(s, (int, float)) else 0.0
    return [k + (a[0], round(a[1], 3), round(a[2], 3)) for k, a in agg.items()]


# ---------------------------------------------------------------- SEC

def sic_map(sub_dir):
    """{cik: sic} from the financial statement data sets' sub.txt files, the
    latest file winning."""
    m = {}
    for n in sorted(os.listdir(sub_dir)):
        if n.endswith("-sub.txt"):
            with open(os.path.join(sub_dir, n), encoding="utf-8", errors="replace") as f:
                for r in csv.DictReader(f, delimiter="\t"):
                    if r.get("sic"):
                        m[int(r["cik"])] = r["sic"]
    return m


def frames(p):
    with open(p, encoding="utf-8") as f:
        d = json.load(f)
    return [(int(x["cik"]), float(x["val"])) for x in d.get("data", [])]


SEC_CONCEPTS = {
    "PaymentsToAcquirePropertyPlantAndEquipment": "capex",
    "DepreciationDepletionAndAmortization": "dda",
    "DepreciationAndAmortization": "da",
    "NetCashProvidedByUsedInOperatingActivities": "operating_cash_flow",
    "Revenues": "revenues",
    "LongTermDebt": "long_term_debt",
    "LongTermDebtNoncurrent": "long_term_debt_noncurrent",
    "Assets": "assets",
}


def sec_rows(d, sics):
    """sic2,year,concept,filers,value_usd: sums over filers by two-digit SIC.
    Depreciation is DepreciationDepletionAndAmortization where a filer
    reports it, else DepreciationAndAmortization (one per filer); debt is
    LongTermDebt, else LongTermDebtNoncurrent."""
    per = defaultdict(dict)  # (year, cik) -> {concept: value}
    for c, short in SEC_CONCEPTS.items():
        cd = os.path.join(d, c)
        if not os.path.isdir(cd):
            continue
        for n in sorted(os.listdir(cd)):
            y = int(n[2:6])
            for cik, v in frames(os.path.join(cd, n)):
                per[(y, cik)][short] = v
    agg = defaultdict(lambda: [0, 0.0])
    for (y, cik), vs in per.items():
        sic = sics.get(cik)
        s2 = sic[:2] if sic else "unknown"
        merged = dict(vs)
        dep = vs.get("dda", vs.get("da"))
        debt = vs.get("long_term_debt", vs.get("long_term_debt_noncurrent"))
        for k in ("dda", "da", "long_term_debt", "long_term_debt_noncurrent"):
            merged.pop(k, None)
        if dep is not None:
            merged["depreciation"] = dep
        if debt is not None:
            merged["long_term_debt"] = debt
        for k, v in merged.items():
            a = agg[(s2, y, k)]
            a[0] += 1
            a[1] += v
    return [k + (a[0], a[1]) for k, a in agg.items()]


# ---------------------------------------------------------------- main

def main(groups):
    i23 = iso2to3()
    B = raw("buffers")
    if "reserves" in groups:
        rows, cols = il_rows(os.path.join(B, "imf-il", "IL.csv.gz"))
        write("reserves-monthly.csv", ["iso", "month"] + cols, rows)
    if "irfcl" in groups:
        rows, cols = irfcl_rows(os.path.join(B, "imf-irfcl", "IRFCL.csv.gz"))
        write("irfcl-monthly.csv", ["iso", "month"] + cols, rows)
    if "imf-credit" in groups:
        rows, cols = fa_rows(os.path.join(B, "imf-fa", "FA.csv.gz"))
        write("imf-credit-monthly.csv", ["iso", "month"] + cols, rows)
    if "external" in groups:
        rows, cols = external_rows(os.path.join(B, "wdi-external"))
        write("external-annual.csv", ["iso", "year"] + cols, rows)
    if "energy-us" in groups:
        rows = eia_bulk_rows(os.path.join(B, "eia-bulk", "PET.zip")) + eia_bulk_rows(os.path.join(B, "eia-bulk", "NG.zip"))
        write("energy-us.csv", ["date", "frequency", "series", "value", "unit", "eia_id"], rows)
    if "energy-eu" in groups:
        write("energy-eu.csv", ["table", "geo", "period", "dims", "value"], eu_rows(os.path.join(B, "eurostat-energy-stocks")))
    if "jodi" in groups:
        rows = jodi_rows(raw("slow", "jodi", "oil_primary.zip"), i23) + jodi_rows(raw("slow", "jodi", "oil_secondary.zip"), i23)
        write("jodi-oil-monthly.csv", ["iso", "month", "product", "flow", "kbbl", "assessment"], rows)
    if "grain" in groups:
        write("grain-stocks.csv", ["iso", "commodity", "market_year", "ending_stocks_kt", "domestic_consumption_kt",
                                   "stocks_to_use", "estimate_month"],
              psd_rows(raw("material", "usda-psd", "psd_grains_pulses_csv.zip"), psd_names()))
    if "dsr" in groups:
        write("bis-dsr-quarterly.csv", ["iso", "quarter", "borrowers", "dsr_pct"], dsr_rows(raw("slow", "bis", "WS_DSR.zip"), i23))
    if "us-investment" in groups:
        nd = os.path.join(B, "bea-nipa")
        tabs = {"T50303", "T50305", "T50403", "T50405", "T50503", "T50505", "T50603", "T50605"}
        rows = bea_rows(os.path.join(nd, "SeriesRegister.txt"), os.path.join(nd, "NipaDataA.txt"), tabs)
        write("us-investment-annual.csv", ["table", "line", "series", "label", "metric", "year", "value"], rows)
        rows = bea_rows(os.path.join(nd, "SeriesRegister.txt"), os.path.join(nd, "NipaDataQ.txt"), tabs)
        write("us-investment-quarterly.csv", ["table", "line", "series", "label", "metric", "quarter", "value"], rows)
    if "us-fixed-assets" in groups:
        fd = os.path.join(B, "bea-fixed-assets")
        tabs = {"FAAt201", "FAAt204", "FAAt207", "FAAt401", "FAAt404", "FAAt407", "FAAt601", "FAAt604", "FAAt607"}
        rows = bea_rows(os.path.join(fd, "SeriesRegister.txt"), os.path.join(fd, "FixedAssets.txt"), tabs)
        write("us-fixed-assets-annual.csv", ["table", "line", "series", "label", "metric", "year", "value"], rows)
    if "us-construction" in groups:
        rows = []
        for k in ("privsatime", "pubsatime", "totsatime"):
            p = os.path.join(B, "census-c30", k + ".xlsx")
            if os.path.exists(p):
                rows += c30_rows(p, k)
        write("us-construction-monthly.csv", ["table", "month", "category", "musd_saar", "flag"], rows)
    if "us-generators" in groups:
        d = os.path.join(B, "eia-860m")
        p = os.path.join(d, sorted(n for n in os.listdir(d) if n.endswith(".xlsx"))[-1])
        write("us-generators.csv", ["sheet", "technology", "state", "year", "status", "generators", "nameplate_mw",
                                    "summer_mw"], generator_rows(p))
    if "sec" in groups:
        d = os.path.join(B, "sec-frames")
        write("sec-industry-annual.csv", ["sic2", "year", "concept", "filers", "value_usd"],
              sec_rows(d, sic_map(os.path.join(d, "sub"))))


ALL = ["reserves", "irfcl", "imf-credit", "external", "energy-us", "energy-eu", "jodi", "grain", "dsr",
       "us-investment", "us-fixed-assets", "us-construction", "us-generators", "sec"]

if __name__ == "__main__":
    main(set(sys.argv[1:]) or set(ALL))
