"""The stock-flow consistent core's data (bead bnm-4hv.10;
docs/COUPLED-SYSTEMS.md 2a, 3 and 6d). Five sectors per country-year:
households and NPISH (hh, OECD S1M, else S14 + S15), non-financial
corporations (nfc, S11), financial corporations (fin, S12), general
government (gov, S13) and the rest of the world (row, S2). src/bnm/sfc.clj
learns the behaviour on them and runs the core.

Sources (each with its SOURCE.txt under data/raw/econ/):
  OECD Table 14 (T0800, non-financial accounts by sector), current prices,
    national currency: the flows. Known at y + 2 (doc 6b).
  OECD Table 720 (T0720, financial balance sheets, non-consolidated),
    national currency only (every series is there twice, XDC and USD),
    the 3,445 rows with no year dropped: the stocks by instrument. Known
    at y + 2.
  Fed Z.1 (US): transactions (FU), revaluations (FR) and levels (FL) of
    households' and NFCs' financial assets and liabilities, the one source
    on disk that measures revaluation, so the flow-stock identity can be
    closed rather than only measured. Known at y + 1.
  Lane & Milesi-Ferretti EWN: external assets and liabilities, for the rest
    of the world where Table 720 has none (China) and as a check of S2's
    net worth against the net IIP. Known at y + 2 (the year-end 2024 file
    came out in 2026).
  Not used: WID (household wealth there includes housing and is a ratio to
    national income, a different object from S1M's net financial worth);
    the IMF holdings files (COFER is world totals by currency, PIP/DIP
    bilateral portfolio and direct positions, finer than five sectors).

Instruments: money (F2 currency and deposits), debt (F3 debt securities +
F4 loans), equity (F5), other (F1 gold and SDRs, F6 insurance and
pensions, F7 derivatives, F8 other accounts). Monetary gold is an asset
with no liability, so the other row does not close by that much.

Countries without Table 720 (China, and Table 14's others) get their
domestic sectors' gross positions imputed from property income at that
year's pooled implicit rates (debt liabilities = interest paid / the
pooled rate on liabilities, and so on), marked stocks_source=imputed; the
rest of the world from EWN where it has the country. An imputed balance
sheet is a guess at the scale of the stocks that reproduces the year's
property income, not a measurement.

Writes, under data/derived/sfc/:
  flows.csv     long: iso, year, known, sector, item, value (millions,
                national currency)
  stocks.csv    long: iso, year, known, source, sector, instrument, side
                (a, l, n), value
  panel.csv     wide, one row per iso-year, what src/bnm/sfc.clj reads: y
                (GDP, S1 B1G), each sector's flows and stocks, known years
  residuals.csv per iso-year, the identities' residuals in the data (as
                shares of GDP)
  identities.txt and coverage.txt, the summaries.

  python -I scripts/sfc-data.py          # build
  python -I scripts/sfc-data.py test     # self-test on the built files
"""

import gzip
import io
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "data", "raw", "econ")
OUT = os.path.join(ROOT, "data", "derived", "sfc")

T14 = os.path.join(RAW, "oecd-sna-sector-accounts", "DF_TABLE14_all.csv.gz")
T720 = os.path.join(RAW, "oecd-financial-balance-sheets", "DF_T720R_A_all.csv.gz")
Z1 = os.path.join(RAW, "fed-z1", "z1_csv_files.zip")
EWN = os.path.join(RAW, "lmf-external-wealth", "EWN-dataset-year-end-2024_4.9.26.xlsx")

KNOWN = {"oecd": 2, "z1": 1, "ewn": 2}
SECTORS = ["hh", "nfc", "fin", "gov", "row"]
DOMESTIC = ["hh", "nfc", "fin", "gov"]
SECTOR_CODES = {"S1M": "hh", "S11": "nfc", "S12": "fin", "S13": "gov", "S2": "row", "S1": "tot", "S1N": "ns"}
INSTRUMENTS = {"money": ["F2"], "debt": ["F3", "F4"], "equity": ["F5"], "other": ["F1", "F6", "F7", "F8"]}
AGGREGATES = {"EU", "EA20", "EU27_2020"}

# (item, transaction, accounting entry): C resources/received, D uses/paid
FLOW_ITEMS = [
    ("va", "B1G", "C"), ("d21x31", "D21X31", "C"), ("comp_paid", "D1", "D"), ("comp_recv", "D1", "C"),
    ("os", "B2A3G", "C"), ("os_b2g", "B2G", "C"), ("mixed", "B3G", "C"),
    ("d2_recv", "D2", "C"), ("d2_paid", "D2", "D"), ("d3_recv", "D3", "C"), ("d3_paid", "D3", "D"),
    ("int_recv", "D41", "C"), ("int_paid", "D41", "D"),
    ("div_recv", "D42", "C"), ("div_paid", "D42", "D"),
    ("prop_recv", "D4", "C"), ("prop_paid", "D4", "D"),
    ("tax_recv", "D5", "C"), ("tax_paid", "D5", "D"),
    ("socc_recv", "D61", "C"), ("socc_paid", "D61", "D"),
    ("socb_recv", "D62", "C"), ("socb_paid", "D62", "D"),
    ("d7_recv", "D7", "C"), ("d7_paid", "D7", "D"),
    ("d8_recv", "D8", "C"), ("d8_paid", "D8", "D"),
    ("d9_recv", "D9", "C"), ("d9_paid", "D9", "D"),
    ("cons", "P3", "D"), ("gcf", "P5", "D"), ("gfcf", "P51G", "D"), ("np", "NP", "D"),
    ("exports", "P6", "D"), ("imports", "P7", "C"), ("b11", "B11", "D"),
    ("b5g", "B5G", "D"), ("b6g", "B6G", "D"), ("b8g", "B8G", "D"),
    ("nl", "B9", "D"), ("nl_fx", "B9FX9", "D"),
]


def code(s):
    return s.split(":")[0].strip() if isinstance(s, str) else s


# -- Table 14: flows ----------------------------------------------------------

def read_t14(path=T14):
    cols = ["REF_AREA", "SECTOR", "ACCOUNTING_ENTRY", "TRANSACTION", "INSTR_ASSET", "PRICE_BASE", "TIME_PERIOD", "OBS_VALUE"]
    df = pd.read_csv(path, dtype=str)
    df.columns = [c.split(":")[0] for c in df.columns]
    df = df[cols]
    for c in ["REF_AREA", "SECTOR", "ACCOUNTING_ENTRY", "TRANSACTION", "INSTR_ASSET", "PRICE_BASE"]:
        df[c] = df[c].map(code)
    df = df[(df.PRICE_BASE == "V") & (df.INSTR_ASSET == "_Z") & df.TIME_PERIOD.notna() & df.OBS_VALUE.notna()]
    df = df[~df.REF_AREA.isin(AGGREGATES)]
    df["year"] = df.TIME_PERIOD.astype(int)
    df["value"] = df.OBS_VALUE.astype(float)
    return df.drop(columns=["PRICE_BASE", "INSTR_ASSET", "TIME_PERIOD", "OBS_VALUE"])


def flows_long(t14):
    """iso, year, sector, item, value: households from S1M, else S14 + S15
    item by item."""
    want = {(t, e): k for k, t, e in FLOW_ITEMS}
    t14 = t14[[(t, e) in want for t, e in zip(t14.TRANSACTION, t14.ACCOUNTING_ENTRY)]].copy()
    t14["item"] = [want[(t, e)] for t, e in zip(t14.TRANSACTION, t14.ACCOUNTING_ENTRY)]
    base = t14[t14.SECTOR.isin(SECTOR_CODES)].copy()
    base["sector"] = base.SECTOR.map(SECTOR_CODES)
    # S14 + S15 where S1M is missing for an item
    hh2 = t14[t14.SECTOR.isin(["S14", "S15"])].groupby(["REF_AREA", "year", "item"]).value.sum().reset_index()
    hh2["sector"] = "hh"
    have = set(zip(base[base.sector == "hh"].REF_AREA, base[base.sector == "hh"].year, base[base.sector == "hh"].item))
    hh2 = hh2[[(a, y, i) not in have for a, y, i in zip(hh2.REF_AREA, hh2.year, hh2.item)]]
    out = pd.concat([base[["REF_AREA", "year", "sector", "item", "value"]], hh2[["REF_AREA", "year", "sector", "item", "value"]]])
    out = out.rename(columns={"REF_AREA": "iso"})
    out = out.groupby(["iso", "year", "sector", "item"], as_index=False).value.sum()
    out["known"] = out.year + KNOWN["oecd"]
    return out


# -- Table 720: stocks -----------------------------------------------------------

def read_t720(path=T720):
    keep = ["REF_AREA", "SECTOR", "ACCOUNTING_ENTRY", "INSTR_ASSET", "MATURITY", "UNIT_MEASURE", "CURRENCY_DENOM", "TIME_PERIOD", "OBS_VALUE"]
    parts = []
    for ch in pd.read_csv(path, dtype=str, chunksize=500_000):
        ch.columns = [c.split(":")[0] for c in ch.columns]
        ch = ch[keep]
        ch = ch[ch.TIME_PERIOD.notna() & ch.OBS_VALUE.notna()]  # the rows with no year
        for c in keep[:7]:
            ch[c] = ch[c].map(code)
        ch = ch[(ch.UNIT_MEASURE == "XDC") & (ch.CURRENCY_DENOM == "_T") & ch.MATURITY.isin(["T", "_Z"])]
        parts.append(ch)
    df = pd.concat(parts)
    df = df[~df.REF_AREA.isin(AGGREGATES)]
    df["year"] = df.TIME_PERIOD.astype(int)
    df["value"] = df.OBS_VALUE.astype(float)
    return df[["REF_AREA", "SECTOR", "ACCOUNTING_ENTRY", "INSTR_ASSET", "year", "value"]]


def stocks_long(t720):
    """iso, year, sector, instrument (F, F1-F8, BF90), side (a, l, n), value;
    households from S1M, else S14 + S15."""
    t = t720[t720.INSTR_ASSET.isin(["F", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "BF90"])].copy()
    t["side"] = t.ACCOUNTING_ENTRY.str.lower()
    base = t[t.SECTOR.isin(SECTOR_CODES)].copy()
    base["sector"] = base.SECTOR.map(SECTOR_CODES)
    hh2 = t[t.SECTOR.isin(["S14", "S15"])].groupby(["REF_AREA", "year", "INSTR_ASSET", "side"]).value.sum().reset_index()
    hh2["sector"] = "hh"
    have = set(zip(*[base[base.sector == "hh"][c] for c in ["REF_AREA", "year", "INSTR_ASSET", "side"]]))
    hh2 = hh2[[k not in have for k in zip(hh2.REF_AREA, hh2.year, hh2.INSTR_ASSET, hh2.side)]]
    cols = ["REF_AREA", "year", "sector", "INSTR_ASSET", "side", "value"]
    out = pd.concat([base[cols], hh2[cols]]).rename(columns={"REF_AREA": "iso", "INSTR_ASSET": "instrument"})
    out = out.groupby(["iso", "year", "sector", "instrument", "side"], as_index=False).value.sum()
    out["known"] = out.year + KNOWN["oecd"]
    out["source"] = "oecd-t720"
    return out


def aggregate_instruments(st):
    """{(iso, year, sector): {money_a, money_l, ..., nfw}} from the long
    stocks; an instrument with no rows on a side is 0 there when the sector
    has its total (F) on that side, else missing."""
    w = st.pivot_table(index=["iso", "year", "sector"], columns=["instrument", "side"], values="value", aggfunc="sum")
    out = pd.DataFrame(index=w.index)
    for side in ["a", "l"]:
        has_total = w[("F", side)].notna() if ("F", side) in w.columns else pd.Series(False, index=w.index)
        for k, fs in INSTRUMENTS.items():
            cols = [(f, side) for f in fs if (f, side) in w.columns]
            v = w[cols].sum(axis=1, min_count=1) if cols else pd.Series(np.nan, index=w.index)
            v = v.where(v.notna() | ~has_total, 0.0)
            out[f"{k}_{side}"] = v
        out[f"total_{side}"] = w[("F", side)] if ("F", side) in w.columns else np.nan
    out["nfw"] = w[("BF90", "n")] if ("BF90", "n") in w.columns else np.nan
    return out.reset_index()


# -- Z.1: the flow-stock identity with revaluation, US households and NFCs ----------

def z1_annual(path=Z1):
    """Per year: households' (S1M) and NFCs' (S11_1) financial assets and
    liabilities, levels at Q4 (FL), annual transactions (sum of the
    quarters' FU, not annualised), revaluations (FR), and the financial
    and non-financial net lending, millions of USD."""
    series = {
        "hh": {"fa": "FL154090005.Q", "fl": "FL154190005.Q", "ua": "FU154090005.Q", "ul": "FU154190005.Q",
               "ra": "FR154090005.Q", "rl": None, "nlf": "FA155000005.Q"},
        "nfc": {"fa": "FL104090005.Q", "fl": "FL104190005.Q", "ua": "FU104090005.Q", "ul": "FU104190005.Q",
                "ra": "FR104090005.Q", "rl": "FR104190005.Q", "nlf": "FA105000005.Q"},
    }
    files = {"hh": ["S1M_s", "S1M_r", "S1M_t"], "nfc": ["S11_1_s", "S11_1_r", "S11_1_t"]}
    z = zipfile.ZipFile(path)
    out = []
    for sec, fs in files.items():
        frames = []
        for f in fs:
            df = pd.read_csv(io.BytesIO(z.read(f"csv/{f}.csv")), dtype=str)
            frames.append(df.set_index("date"))
        df = pd.concat(frames, axis=1)
        df = df.loc[:, ~df.columns.duplicated()]
        q = df.apply(pd.to_numeric, errors="coerce")
        q["year"] = [int(d[:4]) for d in q.index]
        q["qtr"] = [d[-2:] for d in q.index]
        s = series[sec]
        for y, g in q.groupby("year"):
            if len(g) < 4:
                continue
            q4 = g[g.qtr == "Q4"]
            row = {"sector": sec, "year": y,
                   "fa": float(q4[s["fa"]].iloc[0]), "fl": float(q4[s["fl"]].iloc[0]),
                   "ua": g[s["ua"]].sum(min_count=4), "ul": g[s["ul"]].sum(min_count=4),
                   "ra": g[s["ra"]].sum(min_count=4),
                   "rl": g[s["rl"]].sum(min_count=4) if s["rl"] else 0.0,
                   # FA flows are seasonally adjusted annual rates: the year's is their mean
                   "nlf": g[s["nlf"]].mean() if s["nlf"] in g else np.nan}
            out.append(row)
    return pd.DataFrame(out)


def z1_identity(z, gdp):
    """Per sector-year: the change in net financial worth against
    transactions plus revaluation; the remainder is other volume changes
    (and Z.1's own discrepancies). Shares of GDP (`gdp`, {year: USD m})."""
    z = z.sort_values(["sector", "year"]).copy()
    z["nfw"] = z.fa - z.fl
    z["d_nfw"] = z.groupby("sector").nfw.diff()
    z["tx"] = z.ua - z.ul
    z["reval"] = z.ra - z.rl
    z["ocv"] = z.d_nfw - z.tx - z.reval
    z["gdp"] = z.year.map(gdp)
    for c in ["d_nfw", "tx", "reval", "ocv"]:
        z[c + "_y"] = z[c] / z.gdp
    return z.dropna(subset=["d_nfw", "gdp"])


# -- EWN: the rest of the world ---------------------------------------------------

def read_ewn(path=EWN):
    """iso, year and the country's external positions (millions USD) as the
    rest of the world's: its debt and equity assets (the country's
    liabilities: debt; portfolio equity and FDI) and liabilities (the
    country's assets: debt and FX reserves; portfolio equity and FDI),
    derivatives as other; gdp_usd; net_iip (excluding gold)."""
    df = pd.read_excel(path, sheet_name="Dataset")
    df = df[df.Country.isin(EWN_NAMES)].copy()
    f = lambda c: pd.to_numeric(df[c], errors="coerce").fillna(0.0)
    out = pd.DataFrame({
        "iso": df.Country.map(EWN_NAMES), "year": df.Year.astype(int),
        "ewn_debt_a": f("Debt liabilities (stock)"),
        "ewn_equity_a": f("Portfolio equity liabilities (stock)") + f("FDI liabilities (stock)"),
        "ewn_other_a": f("financial derivatives (liab)"),
        "ewn_debt_l": f("Debt assets (stock)") + f("FX Reserves minus gold"),
        "ewn_equity_l": f("Portfolio equity assets (stock)") + f("FDI assets (stock)"),
        "ewn_other_l": f("financial derivatives (assets)"),
        "gdp_usd": pd.to_numeric(df["GDP (US$)"], errors="coerce"),
        "net_iip": pd.to_numeric(df["Net IIP excl gold"], errors="coerce")})
    return out


EWN_NAMES = {"China,P.R.: Mainland": "CHN", "United States": "USA"}


# -- the panel ----------------------------------------------------------------------

def gdp(w):
    """GDP at market prices. S1's B1G is GDP for most countries, but for
    some (Chile) it is value added at basic prices and taxes less subsidies
    on products (D21X31) come on top: y is B1G + D21X31 where that is the
    nearer to final expenditure C + I + X - M, else B1G."""
    spend = (sum(w[c] for c in ["hh_cons", "gov_cons"] + [f"{s}_gcf" for s in DOMESTIC])
             + w["row_exports"] - w["row_imports"])
    va = w["tot_va"]
    with_tax = va + w["tot_d21x31"]
    use = w["tot_d21x31"].notna() & spend.notna() & ((with_tax - spend).abs() < (va - spend).abs())
    return with_tax.where(use, va)


def build_panel(fl, agg):
    """Wide: one row per iso-year with y (S1 B1G), each sector's flows
    (s_item) and stocks (s_money_a ...)."""
    w = fl.pivot_table(index=["iso", "year"], columns=["sector", "item"], values="value", aggfunc="sum")
    w.columns = [f"{s}_{i}" for s, i in w.columns]
    for s in SECTORS + ["tot"]:
        for k, _, _ in FLOW_ITEMS:
            if f"{s}_{k}" not in w:
                w[f"{s}_{k}"] = np.nan
    w = w.reset_index()
    w["y"] = gdp(w)
    st = agg.set_index(["iso", "year", "sector"])
    cols = [c for c in agg.columns if c not in ("iso", "year", "sector")]
    sw = st[cols].unstack("sector")
    sw.columns = [f"{s}_{c}" for c, s in sw.columns]
    sw = sw.reset_index()
    p = w.merge(sw, on=["iso", "year"], how="left")
    p["known_flows"] = p.year + KNOWN["oecd"]
    return p


def fix_row_sign(p):
    """Table 14 records S2's net lending from the rest of the world's side,
    so it is minus the total economy's. New Zealand from 1998 records it
    from the nation's side (the total's sign). A country with five or more
    years whose S2 B9 is within 10% of the total is taken to use the
    nation's side, and its years nearer the total than minus the total are
    flipped. Returns (panel, {iso: years flipped})."""
    both = p.row_nl.notna() & p.tot_nl.notna() & (p.tot_nl.abs() > 0)
    clear = ((p.row_nl - p.tot_nl).abs() <= 0.1 * p.tot_nl.abs()) & both
    nation = clear.groupby(p.iso).transform("sum") >= 5
    same = ((p.row_nl - p.tot_nl).abs() < (p.row_nl + p.tot_nl).abs()) & both & nation
    p = p.copy()
    p.loc[same, "row_nl"] = -p.loc[same, "row_nl"]
    return p, same.groupby(p.iso).sum().loc[lambda x: x > 0].to_dict()


def stocks_complete(p, sectors=SECTORS):
    ok = pd.Series(True, index=p.index)
    for s in sectors:
        for k in INSTRUMENTS:
            for side in ["a", "l"]:
                c = f"{s}_{k}_{side}"
                ok &= p[c].notna() if c in p else False
    return ok


CORE_FLOWS = (["y"] + [f"{s}_va" for s in DOMESTIC] + [f"{s}_nl" for s in SECTORS]
              + ["hh_cons", "gov_cons"] + [f"{s}_gcf" for s in DOMESTIC]
              + ["hh_comp_recv"] + [f"{s}_comp_paid" for s in ["nfc", "fin", "gov"]]
              + [f"{s}_{d}" for s in DOMESTIC for d in ["int_recv", "int_paid"]])


def flows_complete(p):
    ok = pd.Series(True, index=p.index)
    for c in CORE_FLOWS:
        ok &= p[c].notna() if c in p else False
    return ok


def impute_stocks(p):
    """Gross positions from property income at the year's pooled implicit
    rates, for country-years with complete flows and no balance sheet:
    each year's own pooled rate, else the latest earlier year's, never a
    later year's: a year before every measured interest rate is left
    unimputed, and a position whose rate has no year at or before it (gov
    equity, measured from 1992 and 2007) is missing, read as 0. The
    rest of the world from EWN when present (ewn_assets/ewn_liab in
    national currency), else from the same imputation."""
    have = p.stocks_ok & p.flows_ok
    rates = {}
    for s in SECTORS:
        for nm, num, den in [("il", "int_paid", ["money_l", "debt_l"]), ("ia", "int_recv", ["money_a", "debt_a"]),
                             ("ql", "div_paid", ["equity_l"]), ("qa", "div_recv", ["equity_a"])]:
            d = sum(p[f"{s}_{c}"].fillna(0) for c in den)
            r = (p[f"{s}_{num}"] / d).where(have & (d > 0))
            r = r[(r > 0) & (r < 0.5)]
            rates[(s, nm)] = p.loc[r.index].assign(r=r).groupby("year").r.median()
    out = p.copy()
    need = ~p.stocks_ok & p.flows_ok
    for i in out.index[need]:
        y = out.at[i, "year"]
        if not all((rates[(s, nm)].index <= y).any() for s in SECTORS for nm in ["il", "ia"]):
            continue
        for s in SECTORS:
            def rate(nm):
                ser = rates[(s, nm)]
                past = ser[ser.index <= y]
                return past.iloc[-1] if len(past) else np.nan
            ip, ir, dp, dr = (out.at[i, f"{s}_{c}"] if f"{s}_{c}" in out else np.nan
                              for c in ["int_paid", "int_recv", "div_paid", "div_recv"])
            vals = {"money_a": 0.0, "money_l": 0.0, "other_a": 0.0, "other_l": 0.0,
                    "debt_l": np.nan_to_num(ip) / rate("il"), "debt_a": np.nan_to_num(ir) / rate("ia"),
                    "equity_l": np.nan_to_num(dp) / rate("ql"), "equity_a": np.nan_to_num(dr) / rate("qa")}
            if s == "row" and pd.notna(out.at[i, "ewn_debt_a"]):
                # the country's external positions, the rest of the world's mirror (EWN)
                for k in ["debt", "equity", "other"]:
                    for side in ["a", "l"]:
                        vals[f"{k}_{side}"] = out.at[i, f"ewn_{k}_{side}"]
            for k, v in vals.items():
                out.at[i, f"{s}_{k}"] = v
            out.at[i, f"{s}_nfw"] = sum(vals[f"{k}_a"] - vals[f"{k}_l"] for k in INSTRUMENTS)
        out.at[i, "stocks_source"] = "imputed"
    return out


# -- identities in the data ------------------------------------------------------------

def residuals(p):
    """Per iso-year, as shares of GDP:
      nl_sum        the five sectors' net lending summed (zero in theory)
      nl_dom_tot    the four domestic sectors against S1's own B9
      inst_<k>      instrument k: all sectors' assets less liabilities,
                    rest of the world included (zero but for gold in other)
      nfw_<s>       the sector's BF90 against its assets less liabilities
      fof_<s>       the change in net financial worth less net lending:
                    revaluations plus other changes plus the gap between
                    the financial and non-financial accounts' net lending,
                    which the data do not separate (Z.1 does for the US)."""
    r = p[["iso", "year"]].copy()
    y = p.y
    r["nl_sum"] = sum(p[f"{s}_nl"] for s in SECTORS) / y
    r["nl_dom_tot"] = (sum(p[f"{s}_nl"] for s in DOMESTIC) - p["tot_nl"]) / y if "tot_nl" in p else np.nan
    oecd = p.stocks_source.eq("oecd-t720")
    for k in INSTRUMENTS:
        a = sum(p[f"{s}_{k}_a"] for s in SECTORS)
        l = sum(p[f"{s}_{k}_l"] for s in SECTORS)
        r[f"inst_{k}"] = ((a - l) / y).where(oecd)
    for s in SECTORS:
        a = sum(p[f"{s}_{k}_a"] for k in INSTRUMENTS)
        l = sum(p[f"{s}_{k}_l"] for k in INSTRUMENTS)
        r[f"nfw_{s}"] = ((p[f"{s}_nfw"] - (a - l)) / y).where(oecd)
    p2 = p.sort_values(["iso", "year"])
    for s in SECTORS:
        prev = p2.groupby("iso")[f"{s}_nfw"].shift(1)
        consecutive = p2.groupby("iso").year.diff().eq(1)
        f = ((p2[f"{s}_nfw"] - prev - p2[f"{s}_nl"]) / p2.y).where(consecutive & p2.stocks_source.eq("oecd-t720"))
        r.loc[p2.index, f"fof_{s}"] = f
    return r


def q(s, x):
    s = s.dropna()
    return float(np.nanquantile(s, x)) if len(s) else float("nan")


def identities_text(p, r, z1id=None, ewn_check=None):
    L = []
    w = L.append
    ok = p.flows_ok
    nl = r.nl_sum[ok].abs()
    w("# Identities in the data (shares of GDP; docs/COUPLED-SYSTEMS.md 3 and 6d)")
    w("")
    w(f"1. Net lending sums to zero across the five sectors: {ok.sum()} country-years with complete flows,")
    w(f"   {p[ok].iso.nunique()} countries. |sum|: median {nl.median():.2e}, 90th pct {q(nl, .9):.4f},"
      f" within 0.28% of GDP in {(nl <= 0.0028).mean():.1%}, within 0.01% in {(nl <= 0.0001).mean():.1%}.")
    dt = r.nl_dom_tot[ok].abs()
    w(f"   The four domestic sectors against S1's own B9: median {dt.median():.2e}, 90th pct {q(dt, .9):.4f}.")
    w("   Largest by country (median |sum| over its years, then its worst year):")
    g = r[ok].assign(a=nl).groupby("iso").a.agg(["median", "max", "count"]).sort_values("median", ascending=False)
    for iso, row in g.head(8).iterrows():
        w(f"     {iso}  median {row['median']:.4f}  max {row['max']:.4f}  n {int(row['count'])}")
    w("")
    oecd = p.stocks_source.eq("oecd-t720")
    w(f"2. Each instrument's assets equal its liabilities across the sectors (rest of the world included):"
      f" {oecd.sum()} country-years with complete Table 720 balance sheets, {p[oecd].iso.nunique()} countries.")
    for k in INSTRUMENTS:
        s = r[f"inst_{k}"].abs()
        w(f"   {k:7s} |assets - liabilities|: median {s.median():.4f}, 90th pct {q(s, .9):.4f}, max {s.max():.4f}")
    w("   (other holds monetary gold, F11, an asset with no counterpart: its residual is gold plus the rest.)")
    w("   Net financial worth against assets less liabilities:"
      + "".join(f" {s} {r[f'nfw_{s}'].abs().median():.1e}" for s in SECTORS))
    w("")
    w("3. Flow of funds: the change in a sector's net financial worth equals its net lending plus revaluations")
    w("   and other changes. Table 720 has no revaluation account, so the residual below is revaluation plus")
    w("   other volume changes plus the financial/non-financial net-lending gap, |median| and 90th pct:")
    for s in SECTORS:
        f = r[f"fof_{s}"]
        w(f"   {s:4s} median {f.median():+.4f}  |.| median {f.abs().median():.4f}  |.| 90th pct {q(f.abs(), .9):.4f}  n {f.notna().sum()}")
    if z1id is not None and len(z1id):
        w("   Z.1 measures revaluation for the US, so there the identity closes up to other volume changes:")
        for sec, g2 in z1id.groupby("sector"):
            g2 = g2[g2.year >= 1952]
            w(f"   US {sec}: |d NFW - transactions - revaluation| median {g2.ocv_y.abs().median():.4f} of GDP,"
              f" 90th pct {q(g2.ocv_y.abs(), .9):.4f}; without revaluation {(g2.d_nfw_y - g2.tx_y).abs().median():.4f}"
              f" ({g2.year.min()}-{g2.year.max()})")
    if ewn_check is not None and len(ewn_check):
        e = ewn_check
        w("")
        w(f"4. The rest of the world's net financial worth (S2 BF90) against minus the net IIP (EWN), both in USD:"
          f" n {len(e)}, |gap| median {e.gap.abs().median():.4f} of GDP, 90th pct {q(e.gap.abs(), .9):.4f}.")
    w("")
    w("By country, medians of |residual| over its years, shares of GDP (flows: country-years with complete")
    w("flows; instruments and flow of funds: complete Table 720 balance sheets):")
    w("iso   n   net-lending-sum   money    debt     equity   other    fof-hh   fof-nfc  fof-gov")
    for iso, g in r.groupby("iso"):
        okg = ok[g.index]
        if not okg.any() and not oecd[g.index].any():
            continue
        m = lambda c, sel: g[c][sel].abs().median()
        w(f"{iso}  {int(okg.sum()):3d}  {m('nl_sum', okg):.5f}           "
          + "  ".join(f"{m('inst_' + k, oecd[g.index]):.4f}" for k in INSTRUMENTS) + "  "
          + "  ".join(f"{g['fof_' + s][oecd[g.index]].abs().median():.4f}" for s in ["hh", "nfc", "gov"]))
    return "\n".join(L) + "\n"


def coverage_text(p):
    L = []
    w = L.append
    both = p.flows_ok & p.stocks_ok
    w("# SFC data coverage (data/derived/sfc/panel.csv)")
    w("")
    w(f"flows complete (Table 14 core items, all five sectors' B9): {p.flows_ok.sum()} country-years, {p[p.flows_ok].iso.nunique()} countries")
    w(f"balance sheets complete (Table 720, five sectors x four instruments): {(p.stocks_source == 'oecd-t720').sum()} country-years")
    w(f"both (the core runs on measured stocks): {both.sum()} country-years, {p[both].iso.nunique()} countries")
    imp = p.stocks_source.eq("imputed")
    w(f"imputed stocks (flows complete, no Table 720): {imp.sum()} country-years: {', '.join(sorted(p[imp].iso.unique()))}")
    w("")
    w("iso   flows        stocks       years usable  stocks source")
    for iso, g in p.groupby("iso"):
        fy = g[g.flows_ok].year
        sy = g[g.stocks_source == "oecd-t720"].year
        u = g[g.flows_ok & g.stocks_source.notna()].year
        src = ",".join(sorted(g.stocks_source.dropna().unique()))
        f = f"{fy.min()}-{fy.max()}" if len(fy) else "-"
        s = f"{sy.min()}-{sy.max()}" if len(sy) else "-"
        w(f"{iso}   {f:11s}  {s:11s}  {len(u):5d}         {src}")
    return "\n".join(L) + "\n"


def main():
    os.makedirs(OUT, exist_ok=True)
    print("reading Table 14 ...", flush=True)
    fl = flows_long(read_t14())
    print("reading Table 720 ...", flush=True)
    st = stocks_long(read_t720())
    agg = aggregate_instruments(st)
    p = build_panel(fl, agg)
    p, flipped = fix_row_sign(p)
    print("rest of the world's B9 recorded from the nation's side, flipped:", flipped)
    p["flows_ok"] = flows_complete(p)
    p["stocks_ok"] = stocks_complete(p)
    p["stocks_source"] = np.where(p.stocks_ok, "oecd-t720", None)
    p["known_stocks"] = np.where(p.stocks_ok, p.year + KNOWN["oecd"], np.nan)

    # EWN for the rest of the world where Table 720 is missing, and the S2 check
    ewn_check = None
    ecols = [f"ewn_{k}_{side}" for k in ["debt", "equity", "other"] for side in ["a", "l"]]
    try:
        e = read_ewn()
        p = p.merge(e, on=["iso", "year"], how="left")
        # EWN positions and GDP are both millions of USD: national currency per USD from GDP
        fx = p.y / p.gdp_usd
        for c in ecols:
            p[c] = p[c] * fx
        p["known_ewn"] = np.where(p.ewn_debt_a.notna(), p.year + KNOWN["ewn"], np.nan)
        us = p[(p.iso == "USA") & p.stocks_ok & p.net_iip.notna()]
        ewn_check = pd.DataFrame({"year": us.year, "gap": (us.row_nfw + us.net_iip * fx[us.index]) / us.y})
        p = p.drop(columns=["gdp_usd", "net_iip"])
    except Exception as ex:  # noqa: BLE001 - EWN is optional
        print("EWN not read:", ex)
        for c in ecols:
            p[c] = np.nan

    p = impute_stocks(p)
    p["known_stocks"] = np.where(p.stocks_source.eq("imputed"), p.year + KNOWN["oecd"], p.known_stocks)

    z1id = None
    try:
        usy = p[p.iso == "USA"].set_index("year").y
        z1id = z1_identity(z1_annual(), usy.to_dict())
        z1id.assign(known=z1id.year + KNOWN["z1"]).to_csv(os.path.join(OUT, "z1-identity.csv"), index=False)
    except Exception as ex:  # noqa: BLE001
        print("Z.1 not read:", ex)

    r = residuals(p)
    fl.to_csv(os.path.join(OUT, "flows.csv"), index=False, columns=["iso", "year", "known", "sector", "item", "value"])
    st.to_csv(os.path.join(OUT, "stocks.csv"), index=False, columns=["iso", "year", "known", "source", "sector", "instrument", "side", "value"])
    p.sort_values(["iso", "year"]).to_csv(os.path.join(OUT, "panel.csv"), index=False)
    r.sort_values(["iso", "year"]).to_csv(os.path.join(OUT, "residuals.csv"), index=False)
    txt = identities_text(p, r, z1id, ewn_check)
    with open(os.path.join(OUT, "identities.txt"), "w") as f:
        f.write(txt)
    cov = coverage_text(p)
    with open(os.path.join(OUT, "coverage.txt"), "w") as f:
        f.write(cov)
    print(txt)
    print(cov)


def _toy_panel(rows):
    """A panel with the columns impute_stocks reads: rows of (iso, year,
    measured?, interest paid by hh, hh debt liabilities)."""
    recs = []
    for iso, year, measured, ip, debt in rows:
        r = {"iso": iso, "year": year, "stocks_ok": measured, "flows_ok": True, "ewn_debt_a": np.nan,
             "stocks_source": "oecd-t720" if measured else None}
        for s in SECTORS:
            for k in INSTRUMENTS:
                for side in ["a", "l"]:
                    r[f"{s}_{k}_{side}"] = (debt if (s, k, side) == ("hh", "debt", "l") else 10.0) if measured else np.nan
            for c in ["int_paid", "int_recv", "div_paid", "div_recv"]:
                r[f"{s}_{c}"] = 1.0
        r["hh_int_paid"] = ip
        recs.append(r)
    return pd.DataFrame(recs)


def test_impute_causal():
    """An imputed year uses pooled rates from that year or before, never a
    later one; a year before any measured one is not imputed."""
    p = _toy_panel([("A", 2000, True, 5.0, 100.0), ("A", 2001, True, 10.0, 100.0),
                    ("B", 1999, False, 4.0, 0.0), ("B", 2000, False, 4.0, 0.0), ("B", 2002, False, 4.0, 0.0)])
    out = impute_stocks(p).set_index(["iso", "year"])
    assert pd.isna(out.loc[("B", 1999), "stocks_source"]), "no rate at or before 1999"
    # hh's rate on liabilities is interest over money + debt liabilities (110 at A)
    assert abs(out.loc[("B", 2000), "hh_debt_l"] - 4.0 / (5.0 / 110.0)) < 1e-9, "2000's own rate"
    assert abs(out.loc[("B", 2002), "hh_debt_l"] - 4.0 / (10.0 / 110.0)) < 1e-9, "2001's, the latest before 2002"


def test_gdp():
    """y is GDP at market prices: B1G plus taxes less subsidies on products
    where S1's B1G is at basic prices (Chile), so it equals C + I + X - M."""
    t = pd.DataFrame({"iso": ["X", "Y"], "year": [2000, 2000], "tot_va": [90.0, 100.0], "tot_d21x31": [10.0, 9.0],
                      "hh_cons": [60.0, 60.0], "gov_cons": [20.0, 20.0], "hh_gcf": [5.0, 5.0], "nfc_gcf": [15.0, 15.0],
                      "fin_gcf": [0.0, 0.0], "gov_gcf": [3.0, 3.0], "row_exports": [30.0, 30.0], "row_imports": [33.0, 33.0]})
    y = gdp(t)
    assert list(y) == [100.0, 100.0], list(y)


def test():
    test_impute_causal()
    test_gdp()
    p = pd.read_csv(os.path.join(OUT, "panel.csv"))
    r = pd.read_csv(os.path.join(OUT, "residuals.csv"))
    assert (p.known_flows == p.year + 2).all(), "flows known at y + 2"
    ok = p.flows_ok
    assert ok.sum() > 1000, ok.sum()
    nl = r.nl_sum[ok].abs()
    assert (nl <= 0.0028).mean() > 0.85, (nl <= 0.0028).mean()
    oecd = p.stocks_source.eq("oecd-t720")
    for k in ["money", "debt", "equity"]:
        assert r[f"inst_{k}"][oecd].abs().median() < 0.05, k
    for s in SECTORS:
        assert r[f"nfw_{s}"].abs().median() < 1e-3, s
    assert "CHN" in set(p[p.stocks_source.eq("imputed")].iso), "China imputed"
    us = p[(p.iso == "USA") & (p.year == 2009)].iloc[0]
    assert us.gov_nl / us.y < -0.08 and us.hh_nl / us.y > 0.02
    f = p[ok & p.row_exports.notna() & p.row_imports.notna()]
    spend = sum(f[c] for c in ["hh_cons", "gov_cons"] + [f"{s}_gcf" for s in DOMESTIC]) + f.row_exports - f.row_imports
    gap = ((f.y - spend) / f.y).abs().groupby(f.iso).median()
    assert (gap < 0.01).all(), gap[gap >= 0.01].to_dict()
    print("sfc-data self-test: ok")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "test":
        test()
    else:
        main()
