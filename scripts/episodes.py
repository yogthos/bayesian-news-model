"""The episode library (docs/PLAN.md 1.4, bead bnm-56c.5): known breaks of a
buffer, each encoded the same way, and the panel of place-years with and
without one.

    python -I scripts/episodes.py [out-dir] [panel-dir]

Defaults: resources/bnm/episodes (versioned in git) and
data/derived/episodes (rebuilt, not versioned).

Labels are not hindsight:
- an episode's existence and date come from an outside catalogue or a
  fixed mechanical rule (the RULE_* constants below, written before
  anything was fitted), never from a judgement of its cause;
- its family, candidate buffers, restoring flows and loss holder come only
  from resources/bnm/episodes/rules.csv, keyed by (catalogue, kind);
- what the literature says about a particular episode is in
  mechanisms.csv, copied into a free-text column the model does not train
  on.

Inputs (all under data/; the builder reads, never fetches):
  derived/lv2018.csv                 Laeven-Valencia 2018 (banking, currency, sovereign, restructuring), 1970-2017
  raw/slow/lv/lv2026.csv             Laeven-Valencia 2026 Table A1 banking starts (transcribed; scripts/lv-crises.py)
  raw/slow/rr/global_crisis_data.xlsx  Reinhart-Rogoff, read through scripts/rr-crises.py's onsets (not
                                     raw/slow/rr/onsets.csv, the forward ledger's pinned vintage, which
                                     reads currency crashes coded 2 as no crisis and has "HUN ")
  raw/slow/esrb/crises.xlsx          ESRB/ECB European financial crises database (systemic, not exogenous)
  slow/events.tsv                    market crashes by the slow layer's rule
  raw/slow/ucdp/iisystem.dat         Gleditsch-Ward independent states: a country-year is covered only
                                     while the state exists (else IMF membership, from FA quotas)
  raw/slow/jst/jst.csv               Jorda-Schularick-Taylor crisisJST
  derived/econ-events.csv            the project's rule kinds (tools/bnm/economic_events.clj)
  derived/econ-coverage.csv          their coverage by country, in months
  raw/market/pinksheet/pinksheet-monthly.csv   World Bank Pink Sheet monthly prices
  raw/buffers/imf-fa/FA.csv.gz       IMF Fund accounts: GRA credit outstanding and quota, monthly
  raw/econ/imf-sdmx-holdings-reserves/ICSD.csv.gz   IMF ICSD private investment, constant prices
  raw/buffers/bea-nipa/NipaDataA.txt BEA NIPA quantity indexes by asset type (tables 5.3.3, 5.4.3, 5.5.3)
  stock coverage for the panel: WDI reserves and debt service (raw/buffers/wdi-external), BIS DSR and
  credit (raw/slow/bis), USDA PSD (raw/material/usda-psd), JODI oil (raw/slow/jodi), share prices
  (slow/series.tsv), Eurostat gas storage, EIA gas storage (raw/buffers/...)

Outputs:
  <out>/episodes.csv   one row per catalogue entry that is a break, with its episode
  <out>/families.csv   episodes and rows by family and by catalogue
  <out>/hindcasts.csv  the plan's named hindcasts and the episodes that match them
  <out>/manifest.csv   sha256 of every input read (the library's version)
  <panel>/panel.csv    PANEL_COLUMNS: place,year,family,covered_by,break,episodes,ongoing,known,
                       stock_measured,stock_series

When a label is known: every row has known_date, the month its rule's
condition can first be seen (rules.csv known_lag_months after the dated
month; an investment bust is known when the fall reaches 20%, up to three
years after its onset). In the panel, known is the month the place-year's
label is final: for a break, when its episode is first known; for a
non-event, when the slowest covering rule could still have dated a break
in that year. A forecast at month t may train only on panel rows with
known <= t. ongoing names an earlier episode still running that year: such
a 0 is not a clean non-event. Catalogue publication lags (LV2018 was
published in 2018) are not in known: what they hold is in rules.csv
hindsight.

followed_by, next_buffers, end_date and mechanism_note look after the
break: they describe outcomes and are never model inputs.

Output is sorted and has no timestamps: rebuilding from the same inputs
gives the same bytes.
"""
import csv
import gzip
import hashlib
import io
import json
import os
import sys
import zipfile
from collections import defaultdict
import importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA = os.path.join(ROOT, "data")
RES = os.path.join(ROOT, "resources", "bnm", "episodes")


def _load(name, file):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, file))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


sn = _load("slow_normalize", "slow-normalize.py")  # its gazetteer, xlsx reader and ESRB codes
rr = _load("rr_crises", "rr-crises.py")

VERSION = "2"

# Fixed rules, written before fitting (bead bnm-56c.5).
SPIKE_RISE = 0.50        # Pink Sheet: price up 50% or more on twelve months before
SPIKE_HIGH_MONTHS = 36   # and at its highest of the last 36 months
SPIKE_GAP_MONTHS = 24    # once in 24 months
GRA_RISE_QUOTA = 0.25    # IMF: GRA credit outstanding up by 25% of quota in 12 months
GRA_GAP_MONTHS = 36      # once in 36 months
BUST_RISE = 1.40         # investment: the peak 40% or more above five years before it
BUST_FALL = 0.80         # then 20% or more below the peak within three years
BUST_GAP_YEARS = 8       # once in 8 years
CLUSTER_MONTHS = 24      # rows of a family at a place within 24 months of an episode's start join it
FOLLOW_MONTHS = 36       # what followed: other families' episodes starting within 36 months after

# market places for Pink Sheet series
SPIKE_PLACE = {"gas-europe": "EUR", "gas-us": "USA", "gas-asia": "JPN"}
# Pink Sheet series in groups: spikes of one group at one place within CLUSTER_MONTHS are one
# episode (a grain spike and a metals spike are different stocks, so different episodes)
SPIKE_GROUP = {"wheat": "food", "maize": "food", "rice": "food", "soybeans": "food", "sugar": "food",
               "aluminium": "metals", "copper": "metals", "nickel": "metals", "iron-ore": "metals",
               "oil-brent": "oil", "coal": "coal", "gas-us": "gas", "gas-europe": "gas", "gas-asia": "gas"}
# Pink Sheet series read (the three crude prices are one market: Brent stands for it)
SPIKE_SERIES = ["oil-brent", "coal", "gas-us", "gas-europe", "gas-asia", "aluminium", "copper", "iron-ore",
                "nickel", "wheat", "maize", "rice", "soybeans", "sugar"]
# BEA NIPA real private fixed investment, Fisher quantity indexes, by series code
# (tables 5.3.3, 5.4.3 and 5.5.3; the labels repeat across lines, the codes do not)
BEA_ASSETS = {
    "Y034RA": "information_processing_equipment",
    "B935RA": "computers",
    "B275RA": "communication_equipment",
    "B985RA": "software",
    "B009RA": "nonresidential_structures",
    "W001RA": "commercial_and_health_care_structures",
    "IB001282": "data_centers",
    "B307RA": "manufacturing_structures",
    "W003RA": "power_and_communication_structures",
    "E318RA": "mining_exploration_shafts_and_wells",
    "A680RA": "industrial_equipment",
    "A681RA": "transportation_equipment",
    "A011RA": "residential",
}
# catalogue spans. ESRB: the countries its workbook lists (EU and Norway, no Malta), 1970 to 2020 (the
# update's latest dates are 2021-04, so 2020 is its last whole year); the UK (starred) left the update
# that added crises from 2019, so to 2018. LV2026: "Systemic Banking Crises Database: 1970-2025".
ESRB_YEARS = (1970, 2020)
ESRB_STARRED_END = 2018
LV2018_YEARS = (1970, 2017)
LV2026_YEARS = (1970, 2025)

PLACE_ALIAS = {"KOS": "XKX", "WBG": "PSE"}  # IMF FA's Kosovo, ICSD's West Bank and Gaza
# Gleditsch-Ward codes whose names the gazetteer does not read, or reads as the successor
GW_CODES = {255: "DEU", 300: "AUT", 315: "CSK", 437: "CIV", 678: "YAR", 680: "YMD", 817: "RVN"}
GW_LAST = 2017  # the list's last year: a state then is taken to exist still
# codes that are places though not current countries (kept in coverage; other non-country FA codes,
# such as regional central banks and country groups, are not)
HISTORICAL = {"CSK", "YUG", "YAR", "YMD", "TWN"}

FAMILY_STOCKS = {  # the panel's stock_measured: which series measure a family's candidate buffer
    "reserve_crisis": ["wdi:FI.RES.TOTL.CD"],
    "price_level_break": ["wdi:FI.RES.TOTL.CD"],
    "sovereign_debt_break": ["wdi:DT.TDS.DECT.CD", "wdi:DT.DOD.DECT.CD"],
    "banking_break": ["bis:DSR", "bis:TC", "wdi:FS.AST.PRVT.GD.ZS"],
    "commodity_stockout": ["psd:ending_stocks"],
    "energy_break": ["jodi:crude_closing_stocks", "eurostat:gas_storage", "eia:gas_storage"],
    "investment_bust": ["icsd:private_capital_stock", "bea:fixed_assets"],
    "asset_price_break": ["slow:share_prices"],
}

HINDCASTS = [  # name, places, first month, last month, families (empty: any)
    ("asia_1997", ["THA", "IDN", "KOR", "MYS", "PHL"], "1997-01", "1998-12", []),
    ("food_2007_08", ["WLD"], "2006-07", "2008-12", ["commodity_stockout"]),
    ("food_2007_08_countries", ["*"], "2007-01", "2008-12", ["commodity_stockout"]),
    ("sri_lanka_2022", ["LKA"], "2020-01", "2023-12", []),
    ("european_gas_2021_22", ["EUR"], "2021-01", "2022-12", ["energy_break"]),
    ("telecom_1996_2002", ["USA"], "1996-01", "2003-12", ["investment_bust", "asset_price_break"]),
    ("us_shale_2014_16", ["USA"], "2014-01", "2017-12", ["investment_bust", "energy_break"]),
    ("china_property_2021", ["CHN"], "2021-01", "2025-12", []),
]

INPUTS = []  # paths read, for the manifest


def path(*p):
    return os.path.join(DATA, *p)


def opened(p):
    INPUTS.append(p)
    if p.endswith(".gz"):
        return gzip.open(p, "rt", encoding="utf-8-sig", newline="")
    return open(p, newline="", encoding="utf-8-sig")


# ---------------------------------------------------------------- months

def month(y, m=1):
    return y * 12 + (m - 1)


def month_str(mi):
    return "%04d-%02d" % (mi // 12, mi % 12 + 1)


def parse_month(s):
    """'1997', '1997-07', '1997-07-01' or '1997-Q3' to a month index."""
    s = s.strip()
    if "-Q" in s:
        y, q = s.split("-Q")
        return month(int(y), 3 * (int(q) - 1) + 1)
    parts = s.split("-")
    return month(int(parts[0]), int(parts[1]) if len(parts) > 1 else 1)


# ---------------------------------------------------------------- rules

def once_in(times, gap):
    """The starts of runs of consecutive times, with no two starts closer
    than gap (the earlier kept): a condition that holds for months is one
    onset, and a new onset needs the condition to lapse and return."""
    ts = set(times)
    out, last = [], None
    for t in sorted(t for t in ts if t - 1 not in ts):
        if last is None or t - last >= gap:
            out.append(t)
            last = t
    return out


def spikes(prices):
    """Onset months of price spikes from {month: price}: up SPIKE_RISE or
    more on twelve months before and at the highest of the last
    SPIKE_HIGH_MONTHS months (all of them present); once in
    SPIKE_GAP_MONTHS."""
    hits = []
    for t, p in prices.items():
        b = prices.get(t - 12)
        past = [prices.get(t - k) for k in range(1, SPIKE_HIGH_MONTHS)]
        if b is None or b <= 0 or any(x is None for x in past):
            continue
        if p >= (1 + SPIKE_RISE) * b and p >= max(past):
            hits.append(t)
    return once_in(hits, SPIKE_GAP_MONTHS)


def gra_drawings(outstanding, quota):
    """Onset months of large IMF GRA drawings from {month: credit
    outstanding} and {month: quota} (SDR): outstanding up by
    GRA_RISE_QUOTA of quota or more on twelve months before; once in
    GRA_GAP_MONTHS."""
    hits = []
    for t, o in outstanding.items():
        b, q = outstanding.get(t - 12), quota.get(t)
        if b is None or not q or q <= 0:
            continue
        if o - b >= GRA_RISE_QUOTA * q:
            hits.append(t)
    return once_in(hits, GRA_GAP_MONTHS)


def investment_busts(levels):
    """Investment busts from {year: real investment}, as [(onset year, year
    known)]: a peak year p, the highest of p-5..p and BUST_RISE times p-5 or
    more; the first year f in p+1..p+3 at or below BUST_FALL times the peak,
    with no year before f above the peak (else p was not the peak). The
    onset is p+1; the bust is known in f, not before. Once in
    BUST_GAP_YEARS of onsets."""
    hits = {}
    for p, x in levels.items():
        b = levels.get(p - 5)
        window = [levels.get(y) for y in range(p - 5, p + 1)]
        if b is None or b <= 0 or any(w is None for w in window):
            continue
        if not (x >= BUST_RISE * b and x >= max(window)):
            continue
        for f in range(p + 1, p + 4):
            v = levels.get(f)
            if v is None:
                continue
            if v > x:
                break
            if v <= BUST_FALL * x:
                hits[p + 1] = f
                break
    return [(t, hits[t]) for t in once_in(hits, BUST_GAP_YEARS)]


# ---------------------------------------------------------------- catalogues
# each reader yields dicts: catalogue, kind, place, month, end (month or None), subject, flag, source

def place(code):
    """A place code as the project joins it: stripped, ISO3 where the source differs."""
    c = code.strip().upper()
    return PLACE_ALIAS.get(c, c)


def row(catalogue, kind, where, mi, source, end=None, subject="", flag="", group="", known=None):
    """known: the month the row could first be known, when its rule says
    more than rules.csv known_lag_months (an investment bust)."""
    return {"catalogue": catalogue, "kind": kind, "place": place(where), "month": mi, "end": end,
            "subject": subject, "flag": flag, "source": source, "group": group, "known": known}


# ---------------------------------------------------------------- states

class StateYears:
    """Whether a place is a state in a year: from the Gleditsch-Ward
    intervals {iso: [(first, last)]} where it is listed, else from IMF
    membership {iso: first year}; a place neither knows (a market, a
    territory) always."""

    def __init__(self, intervals, joined):
        self.iv = {k: [(a, 9999 if b >= GW_LAST else b) for a, b in v] for k, v in intervals.items()}
        self.joined = dict(joined)

    def exists(self, iso, y):
        if iso in self.iv:
            return any(a <= y <= b for a, b in self.iv[iso])
        if iso in self.joined:
            return y >= self.joined[iso]
        return True

    def known(self, iso):
        return iso in self.iv or iso in self.joined


def state_years(joined):
    """StateYears from data/raw/slow/ucdp/iisystem.dat and {iso: IMF membership year}."""
    p = path("raw", "slow", "ucdp", "iisystem.dat")
    iv = defaultdict(list)
    INPUTS.append(p)
    with open(p, encoding="latin-1") as f:
        for line in f:
            k = line.rstrip("\n").split("\t")
            if len(k) < 5:
                continue
            iso = GW_CODES.get(int(k[0])) or sn.iso3_of_name(k[2])
            if iso:
                iv[iso].append((int(k[3][-4:]), int(k[4][-4:])))
    return StateYears(iv, joined)


def imf_joined(fa):
    """{iso: first year with an IMF quota} from imf_fa's output."""
    out = {}
    for iso, (_o, q) in fa.items():
        ms = [t for t, v in q.items() if v and v > 0]
        if ms:
            out[iso] = min(ms) // 12
    return out


def read_lv2018(p):
    with opened(p) as f:
        for r in csv.DictReader(f):
            yield row("lv2018", r["kind"], r["iso"], month(int(r["year"])),
                      "Laeven and Valencia (2018), IMF WP/18/206; data/derived/lv2018.csv")


def read_lv2026(p):
    with opened(p) as f:
        for r in csv.DictReader(f):
            kind = "banking_borderline" if r["borderline"].strip() == "1" else "banking"
            end = month(int(r["end"]), 12) if r["end"].strip() else None
            yield row("lv2026", kind, r["iso"], month(int(r["start"])),
                      "Laeven and Valencia (2026), IMF WP/26/94 Table A1; data/raw/slow/lv/lv2026.csv", end=end)


def rr_onsets(p):
    """Reinhart-Rogoff onsets (scripts/rr-crises.py's) from the workbook."""
    INPUTS.append(p)
    return rr.onsets(rr.read_xlsx(p))


def read_rr(onsets):
    for r in onsets:
        for kind in ("banking", "currency", "inflation", "default"):
            if r[kind] == 1:
                yield row("rr", kind, r["iso"], month(int(r["year"])),
                          "Reinhart and Rogoff, Global Crises Data (HBS BFFS); data/raw/slow/rr/global_crisis_data.xlsx")


def rr_coverage(onsets):
    cov = defaultdict(set)  # kind -> {(iso, year)}
    for r in onsets:
        for kind in ("banking", "currency", "inflation", "default"):
            if r[kind + "_known"] == 1:
                cov[kind].add((place(r["iso"]), int(r["year"])))
    return cov


def read_esrb(p):
    """(rows, codes): systemic crises that are not exogenous shocks (COVID-19
    is recorded everywhere as one), and the country codes the workbook lists."""
    INPUTS.append(p)
    rows, codes = [], set()
    for name, sheet in sn.xlsx(p).items():
        if not name.lower().startswith("systemic"):
            continue
        for r in sheet[2:]:
            if not r or not r[0] or len(r[0].strip()) > 3 or len(r) < 7:
                continue
            codes.add(r[0].strip())
            exogenous = (r[6] or "").strip().lower().startswith("exogenous")
            if r[5] == "1" and not exogenous and r[2] and r[2][:4].isdigit():
                rows.append(row("esrb", "banking", sn.esrb_iso(r[0]), parse_month(r[2][:7]),
                                "ESRB/ECB European financial crises database; data/raw/slow/esrb/crises.xlsx"))
    return rows, codes


def esrb_coverage(codes):
    """{iso: (first year, last year)} of the ESRB database."""
    return {sn.esrb_iso(c): (ESRB_YEARS[0], ESRB_STARRED_END if c.strip().endswith("*") else ESRB_YEARS[1])
            for c in codes if sn.esrb_iso(c)}


def read_slow_events(p):
    with opened(p) as f:
        for line in f:
            k = line.rstrip("\n").split("\t")
            if len(k) < 4:
                continue
            if k[0] == "market_crash":
                yield row("rule:slow-normalize", "market_crash", k[1], parse_month(k[2]),
                          "share prices 30% below their 24-month high (scripts/slow-normalize.py); data/slow/events.tsv")


def read_jst(p):
    with opened(p) as f:
        for r in csv.DictReader(f):
            if r.get("crisisJST") == "1":
                yield row("jst", "banking", r["iso"], month(int(float(r["year"]))),
                          "Jorda, Schularick and Taylor Macrohistory Database R6; data/raw/slow/jst/jst.csv")


def jst_coverage(p):
    cov = set()
    with opened(p) as f:
        for r in csv.DictReader(f):
            if r.get("crisisJST") not in (None, "", "NA"):
                cov.add((r["iso"], int(float(r["year"]))))
    return cov


def read_econ_events(p):
    with opened(p) as f:
        for r in csv.DictReader(f):
            yield row("rule:econ-events", r["kind"], r["iso"], int(r["month"]),
                      "tools/bnm/economic_events.clj; data/derived/econ-events.csv")


def econ_coverage(p):
    cov = defaultdict(set)
    with opened(p) as f:
        for r in csv.DictReader(f):
            a, b = int(r["from"]), int(r["through"])
            for y in range(-(-a // 12), (b + 1) // 12):  # whole years only
                cov[r["kind"]].add((r["iso"], y))
    return cov


def pinksheet(p):
    """{series: {month: price}}."""
    out = defaultdict(dict)
    with opened(p) as f:
        for r in csv.DictReader(f):
            mi = parse_month(r["month"])
            for s in SPIKE_SERIES:
                v = r.get(s, "").strip()
                if v:
                    out[s][mi] = float(v)
    return out


def read_spikes(series):
    for s in SPIKE_SERIES:
        for t in spikes(series.get(s, {})):
            yield row("rule:pinksheet-spike", "spike:" + s, SPIKE_PLACE.get(s, "WLD"), t,
                      "World Bank Pink Sheet monthly prices; data/raw/market/pinksheet/pinksheet-monthly.csv",
                      subject=s, group=SPIKE_GROUP[s])


def imf_fa(p):
    """{iso: ({month: GRA outstanding}, {month: quota})}, SDR, monthly."""
    o, q = defaultdict(dict), defaultdict(dict)
    with opened(p) as f:
        for r in csv.DictReader(f):
            ind = r["INDICATOR"]
            if r["FREQUENCY"] != "M" or ind not in ("IMFOPGRA_XDR", "IMFQ_XDR") or not r["OBS_VALUE"]:
                continue
            t = r["TIME_PERIOD"]  # 1990-M01
            mi = month(int(t[:4]), int(t[-2:]))
            (o if ind == "IMFOPGRA_XDR" else q)[place(r["COUNTRY"])][mi] = float(r["OBS_VALUE"])
    return {c: (o[c], q.get(c, {})) for c in o}


def gra_years(o, q):
    """Whole years in which a large drawing could be seen: every month a
    member (quota > 0) with the outstanding of twelve months before."""
    months = {t for t in o if q.get(t, 0) > 0 and (t - 12) in o}
    return sorted(y for y in {t // 12 for t in months} if all(month(y, m) in months for m in range(1, 13)))


def read_gra(fa):
    for iso, (o, q) in sorted(fa.items()):
        for t in gra_drawings(o, q):
            yield row("rule:imf-gra", "gra_drawing", iso, t,
                      "IMF Fund accounts (FA), GRA outstanding purchases and quota; data/raw/buffers/imf-fa/FA.csv.gz")


def icsd_private(p):
    out = defaultdict(dict)
    with opened(p) as f:
        for r in csv.DictReader(f):
            if r["INDICATOR"] == "P51G_PS_Q_PU_RY2017" and r["OBS_VALUE"]:
                out[place(r["COUNTRY"])][int(r["TIME_PERIOD"])] = float(r["OBS_VALUE"])
    return out


def read_icsd_busts(icsd):
    for iso, lv in sorted(icsd.items()):
        for y, k in investment_busts(lv):
            yield row("rule:investment-bust", "icsd_private", iso, month(y),
                      "IMF ICSD private investment, constant 2017 PPP; data/raw/econ/imf-sdmx-holdings-reserves/ICSD.csv.gz",
                      subject="private investment", known=month(k, 12))


def bea_assets(nipa_dir):
    """{asset: {year: quantity index}} for BEA_ASSETS from NipaDataA.txt."""
    data = os.path.join(nipa_dir, "NipaDataA.txt")
    if not os.path.exists(data):
        return {}
    out = defaultdict(dict)
    with opened(data) as f:
        for r in csv.DictReader(f):
            a = BEA_ASSETS.get(r["%SeriesCode"])
            if a:
                out[a][int(r["Period"])] = float(r["Value"].replace(",", ""))
    return out


def read_bea_busts(assets):
    for a, lv in sorted(assets.items()):
        for y, k in investment_busts(lv):
            yield row("rule:investment-bust", "bea_asset", "USA", month(y),
                      "BEA NIPA real private fixed investment by type (quantity index); data/raw/buffers/bea-nipa/NipaDataA.txt",
                      subject=a, known=month(k, 12))


# ---------------------------------------------------------------- library

def load_rules(p):
    with open(p, newline="", encoding="utf-8") as f:
        return {(r["catalogue"], r["kind"]): r for r in csv.DictReader(f)}


def load_mechanisms(p):
    with open(p, newline="", encoding="utf-8") as f:
        return [r for r in csv.DictReader(f)]


def classify(rows, rules):
    """Rows with their family and buffers from the rule table; a row whose
    (catalogue, kind) is not in the table is an error."""
    out = []
    for r in rows:
        rule = rules.get((r["catalogue"], r["kind"]))
        if rule is None:
            raise KeyError("no rule for %s %s" % (r["catalogue"], r["kind"]))
        known = r.get("known")
        if known is None:
            known = r["month"] + int(rule["known_lag_months"] or 0)
        out.append(dict(r, family=rule["family"], known=known))
    return out


def cluster(rows):
    """Episodes: per (family, place, group), rows sorted by month; a row
    within CLUSTER_MONTHS of the current episode's first month joins it.
    The group (a market's stock group, e.g. food or metals) is part of the
    id when there is one."""
    by = defaultdict(list)
    for r in rows:
        if r["family"] != "-":
            by[(r["family"], r["place"], r.get("group", ""))].append(r)
    episodes = {}
    for (fam, place, group), rs in by.items():
        rs.sort(key=lambda r: (r["month"], r["catalogue"], r["kind"], r["subject"]))
        start = None
        for r in rs:
            if start is None or r["month"] - start > CLUSTER_MONTHS:
                start = r["month"]
            eid = ":".join([fam, place] + ([group] if group else []) + [month_str(start)])
            r["episode"] = eid
            episodes.setdefault(eid, {"family": fam, "place": place, "month": start, "rows": []})["rows"].append(r)
    return episodes


def followed(episodes, outcomes):
    """{episode id: [family@month ...]} of other families' episodes and of
    outcome kinds at the same place starting 0..FOLLOW_MONTHS months after."""
    by_place = defaultdict(list)
    for eid, e in episodes.items():
        by_place[e["place"]].append((e["month"], e["family"], eid))
    for r in outcomes:
        by_place[r["place"]].append((r["month"], r["kind"], None))
    out = {}
    for eid, e in episodes.items():
        nxt = sorted({(m, f) for m, f, other in by_place[e["place"]]
                      if other != eid and f != e["family"] and 0 <= m - e["month"] <= FOLLOW_MONTHS})
        out[eid] = nxt
    return out


COLUMNS = ["episode", "family", "place", "stock_group", "episode_start", "row_id", "catalogue", "kind", "subject", "date",
           "date_precision", "known_date", "episode_known", "episode_catalogues", "end_date", "flag", "buffers",
           "restoring_flows", "loss_holder", "followed_by", "next_buffers", "source", "mechanism_note",
           "mechanism_source"]
# what happened after the break: outcomes, never model inputs
OUTCOME_COLUMNS = ["end_date", "followed_by", "next_buffers", "mechanism_note", "mechanism_source"]


def date_of(mi, precision):
    y, m = mi // 12, mi % 12 + 1
    if precision == "year":
        return "%04d-01-01" % y
    return "%04d-%02d-01" % (y, m)


def library(rows, rules, mechanisms):
    """The episode table's rows (dicts, sorted) and the episodes."""
    rows = classify(rows, rules)
    outcomes = [r for r in rows if r["family"] == "-"]
    episodes = cluster(rows)
    nxt = followed(episodes, outcomes)
    fam_buffers = {}
    for rule in rules.values():
        fam_buffers.setdefault(rule["family"], rule["buffers"])
    mech = {}
    for m in mechanisms:
        mech[(m["place"], m["family"], int(m["year"]))] = m
    out = []
    for eid, e in episodes.items():
        follow = nxt[eid]
        first_fam = next((f for _, f in follow if f in fam_buffers and f != "-"), None)
        mrow = mech.get((e["place"], e["family"], e["month"] // 12))
        e_known = min(r["known"] for r in e["rows"])
        e_cats = ";".join(sorted({r["catalogue"] for r in e["rows"]}))
        for r in e["rows"]:
            rule = rules[(r["catalogue"], r["kind"])]
            prec = rule["date_precision"]
            out.append({
                "episode": eid, "family": e["family"], "place": e["place"], "stock_group": r.get("group", ""),
                "episode_start": month_str(e["month"]),
                "row_id": "%s|%s|%s|%s|%s" % (r["catalogue"], r["kind"], r["place"], month_str(r["month"]), r["subject"]),
                "catalogue": r["catalogue"], "kind": r["kind"], "subject": r["subject"],
                "date": date_of(r["month"], prec), "date_precision": prec,
                "known_date": date_of(r["known"], "month"), "episode_known": date_of(e_known, "month"),
                "episode_catalogues": e_cats,
                "end_date": date_of(r["end"], "month") if r["end"] is not None else "",
                "flag": r["flag"], "buffers": rule["buffers"], "restoring_flows": rule["restoring_flows"],
                "loss_holder": rule["loss_holder"],
                "followed_by": ";".join("%s@%s" % (f, month_str(m)) for m, f in follow),
                "next_buffers": fam_buffers.get(first_fam, "") if first_fam else "",
                "source": r["source"],
                "mechanism_note": mrow["mechanism"] if mrow else "",
                "mechanism_source": mrow["source"] if mrow else "",
            })
    out.sort(key=lambda r: (r["family"], r["place"], r["episode_start"], r["date"], r["row_id"]))
    # the same entry twice in a catalogue (e.g. RR listing two kinds the same year is fine; exact duplicates are not)
    seen, dedup = set(), []
    for r in out:
        if r["row_id"] not in seen:
            seen.add(r["row_id"])
            dedup.append(r)
    return dedup, episodes


def family_counts(lib):
    eps = defaultdict(set)
    rows = defaultdict(int)
    by_cat = defaultdict(int)
    eps_cat = defaultdict(set)
    for r in lib:
        eps[r["family"]].add(r["episode"])
        rows[r["family"]] += 1
        by_cat[(r["catalogue"], r["kind"])] += 1
        eps_cat[(r["catalogue"], r["kind"])].add(r["episode"])
    out = [("family", f, "", len(eps[f]), rows[f]) for f in sorted(eps)]
    out += [("catalogue", c, k, len(eps_cat[(c, k)]), by_cat[(c, k)]) for c, k in sorted(by_cat)]
    return out


def hindcasts(lib):
    out = []
    for name, places, a, b, fams in HINDCASTS:
        lo, hi = parse_month(a), parse_month(b)
        hits = sorted({(r["episode"], r["catalogue"] + ":" + r["kind"]) for r in lib
                       if ("*" in places or r["place"] in places) and (not fams or r["family"] in fams)
                       and lo <= parse_month(r["date"][:7]) <= hi})
        eps = sorted({e for e, _ in hits})
        if "*" in places:  # countries only: a count, not a list
            n = len({e for e in eps if e.split(":")[1] not in ("WLD", "EUR")})
            out.append((name, " ".join(places), a, b, " ".join(fams), n, "%d country episodes" % n, ""))
        else:
            out.append((name, " ".join(places), a, b, " ".join(fams), len(eps),
                        ";".join(eps) if eps else "no catalogue row",
                        ";".join(sorted({c for _, c in hits}))))
    return out


# ---------------------------------------------------------------- panel

def wdi_years(zpath):
    """{(iso, year)} with a value, and the set of country codes (not aggregates)."""
    have, countries = set(), set()
    if not os.path.exists(zpath):
        return have, countries
    INPUTS.append(zpath)
    with zipfile.ZipFile(zpath) as z:
        for n in z.namelist():
            if n.startswith("Metadata_Country"):
                for r in csv.DictReader(io.TextIOWrapper(z.open(n), encoding="utf-8-sig")):
                    if r.get("Region", "").strip():
                        countries.add(r["Country Code"])
        for n in z.namelist():
            if n.startswith("API_"):
                lines = io.TextIOWrapper(z.open(n), encoding="utf-8-sig").read().splitlines()
                k = next(i for i, l in enumerate(lines) if l.startswith('"Country Name"'))
                for r in csv.DictReader(lines[k:]):
                    for y, v in r.items():
                        if y and y.isdigit() and v and v.strip():
                            have.add((r["Country Code"], int(y)))
    return have, countries


def wdi_json_years(p):
    """{(iso, year)} with a value from a World Bank API json (data/raw/slow/worldbank)."""
    have = set()
    if not os.path.exists(p):
        return have
    INPUTS.append(p)
    with open(p, encoding="utf-8") as f:
        d = json.load(f)
    for r in d[1] if len(d) > 1 and d[1] else []:
        if r.get("value") is not None and r.get("countryiso3code"):
            have.add((r["countryiso3code"], int(r["date"])))
    return have


def iso2to3():
    m = {}
    p = path("ref", "countryInfo.txt")
    INPUTS.append(p)
    with open(p, encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#"):
                k = line.split("\t")
                m[k[0]] = k[1]
    m["XK"] = "XKX"
    return m


def bis_years(zpath, i23):
    have = set()
    if not os.path.exists(zpath):
        return have
    INPUTS.append(zpath)
    with zipfile.ZipFile(zpath) as z:
        n = z.namelist()[0]
        r = csv.reader(io.TextIOWrapper(z.open(n), encoding="utf-8-sig"))
        head = [h.split(":")[0] for h in next(r)]
        ia, it, iv = head.index("BORROWERS_CTY") if "BORROWERS_CTY" in head else head.index("REF_AREA"), \
            head.index("TIME_PERIOD"), head.index("OBS_VALUE")
        for k in r:
            if k[iv] and k[iv] != "NaN":
                iso = i23.get(k[ia].split(":")[0].strip())
                if iso:
                    have.add((iso, int(k[it][:4])))
    return have


def psd_years():
    """{(iso, marketing year)} with ending stocks reported, and the world (WLD)."""
    p = path("raw", "material", "usda-psd", "psd_grains_pulses_csv.zip")
    have = set()
    if not os.path.exists(p):
        return have
    INPUTS.append(p)
    names = name_map()
    with zipfile.ZipFile(p) as z:
        for r in csv.DictReader(io.TextIOWrapper(z.open("psd_grains_pulses.csv"), encoding="utf-8-sig")):
            if r["Attribute_Description"] == "Ending Stocks":
                iso = names.get(r["Country_Name"].strip().lower())
                if iso:
                    have.add((iso, int(r["Market_Year"])))
                have.add(("WLD", int(r["Market_Year"])))
    return have


PSD_NAMES = {"burma": "MMR", "congo (brazzaville)": "COG", "congo (kinshasa)": "COD", "gambia, the": "GMB",
             "bahamas, the": "BHS", "korea, south": "KOR", "korea, north": "PRK", "cote d'ivoire": "CIV",
             "european union": "EUU", "eu-15": "EUU", "russia": "RUS", "iran": "IRN", "syria": "SYR",
             "vietnam": "VNM", "laos": "LAO", "taiwan": "TWN", "venezuela": "VEN", "bolivia": "BOL",
             "tanzania": "TZA", "macedonia": "MKD", "north macedonia": "MKD", "moldova": "MDA",
             "yemen (sanaa)": "YEM", "yemen": "YEM", "cabo verde": "CPV", "eswatini": "SWZ", "turkey": "TUR",
             "turkiye": "TUR", "czechia": "CZE", "czech republic": "CZE", "macau": "MAC", "kyrgyzstan": "KGZ", "brunei": "BRN", "hong kong": "HKG"}


def name_map():
    m = dict(PSD_NAMES)
    with open(path("ref", "countryInfo.txt"), encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#"):
                k = line.split("\t")
                m.setdefault(k[4].strip().lower(), k[1])
    return m


def jodi_years(i23):
    p = path("raw", "slow", "jodi", "oil_primary.zip")
    have = set()
    if not os.path.exists(p):
        return have
    INPUTS.append(p)
    with zipfile.ZipFile(p) as z:
        r = csv.reader(io.TextIOWrapper(z.open(z.namelist()[0]), encoding="utf-8-sig"))
        head = next(r)
        ia, it, ip, ifl, iv = (head.index(c) for c in ("REF_AREA", "TIME_PERIOD", "ENERGY_PRODUCT", "FLOW_BREAKDOWN", "OBS_VALUE"))
        for k in r:
            if k[ip] == "CRUDEOIL" and k[ifl] == "CLOSTLV" and k[iv] not in ("", "x", "-"):
                iso = i23.get(k[ia])
                if iso:
                    have.add((iso, int(k[it][:4])))
    return have


def share_price_years():
    p = path("slow", "series.tsv")
    have = set()
    if not os.path.exists(p):
        return have
    INPUTS.append(p)
    with open(p, encoding="utf-8") as f:
        for line in f:
            k = line.split("\t")
            if len(k) > 3 and k[1] == "markets/share_prices":
                have.add((k[0], int(k[3][:4])))
    return have


def eurostat_gas_years():
    p = path("raw", "buffers", "eurostat-energy-stocks", "nrg_stk_gasm.tsv.gz")
    have = set()
    if not os.path.exists(p):
        return have
    INPUTS.append(p)
    with gzip.open(p, "rt", encoding="utf-8") as f:
        head = f.readline().rstrip("\n").split("\t")
        for line in f:
            k = line.rstrip("\n").split("\t")
            geo = k[0].split(",")[-1]
            if geo in ("EU27_2020", "EU28", "EU"):
                for h, v in zip(head[1:], k[1:]):
                    if v.strip() not in (":", ""):
                        have.add(("EUR", int(h.strip()[:4])))
    return have


def eia_gas_years():
    p = path("raw", "buffers", "eia-bulk", "NG.zip")
    have = set()
    if not os.path.exists(p):
        return have
    INPUTS.append(p)
    with zipfile.ZipFile(p) as z:
        for line in io.TextIOWrapper(z.open(z.namelist()[0]), encoding="utf-8"):
            if '"NG.N5020US2.M"' not in line:  # US working gas in underground storage, monthly
                continue
            d = json.loads(line)
            for t, v in d.get("data", []):
                if v is not None:
                    have.add(("USA", int(str(t)[:4])))
    return have


def icsd_capital_years():
    p = path("raw", "econ", "imf-sdmx-holdings-reserves", "ICSD.csv.gz")
    have = set()
    with opened(p) as f:
        for r in csv.DictReader(f):
            if r["INDICATOR"] == "CAPSTCK_PS_Q_PU_RY2017" and r["OBS_VALUE"]:
                have.add((r["COUNTRY"], int(r["TIME_PERIOD"])))
    return have


def stock_coverage():
    i23 = iso2to3()
    res, countries = wdi_years(path("raw", "buffers", "wdi-external", "FI.RES.TOTL.CD.zip"))
    ds, _ = wdi_years(path("raw", "buffers", "wdi-external", "DT.TDS.DECT.CD.zip"))
    xd, _ = wdi_years(path("raw", "buffers", "wdi-external", "DT.DOD.DECT.CD.zip"))
    bea = {("USA", y) for y in range(1925, 2026)} if os.path.exists(
        path("raw", "buffers", "bea-fixed-assets", "FixedAssets.txt")) else set()
    return countries, {
        "wdi:FI.RES.TOTL.CD": res, "wdi:DT.TDS.DECT.CD": ds, "wdi:DT.DOD.DECT.CD": xd,
        "wdi:FS.AST.PRVT.GD.ZS": wdi_json_years(path("raw", "slow", "worldbank", "FS.AST.PRVT.GD.ZS.json")),
        "bis:DSR": bis_years(path("raw", "slow", "bis", "WS_DSR.zip"), i23),
        "bis:TC": bis_years(path("raw", "slow", "bis", "WS_TC.zip"), i23),
        "psd:ending_stocks": psd_years(), "jodi:crude_closing_stocks": jodi_years(i23),
        "eurostat:gas_storage": eurostat_gas_years(), "eia:gas_storage": eia_gas_years(),
        "icsd:private_capital_stock": icsd_capital_years(), "bea:fixed_assets": bea,
        "slow:share_prices": share_price_years(),
    }


def catalogue_coverage(countries, rrc, jstc, econc, series, fa, icsd, bea, esrb_cov, states):
    """{family: {(place, year): {(catalogue, kind)}}}: the place-years where a
    catalogue of the family could have recorded a break. A country-year
    counts only while the state exists (states: StateYears)."""
    rules = load_rules(os.path.join(RES, "rules.csv"))
    cov = defaultdict(lambda: defaultdict(set))

    def add(cat, kind, keys):
        fam = rules[(cat, kind)]["family"]
        if fam == "-":
            return
        for (where, y) in keys:
            if states.exists(where, y):
                cov[fam][(where, y)].add((cat, kind))

    allc = sorted(countries)
    for kind in ("banking", "currency", "sovereign", "restructuring"):
        add("lv2018", kind, [(c, y) for c in allc for y in range(LV2018_YEARS[0], LV2018_YEARS[1] + 1)])
    add("lv2026", "banking", [(c, y) for c in allc for y in range(LV2026_YEARS[0], LV2026_YEARS[1] + 1)])
    for kind in ("banking", "currency", "inflation", "default"):
        add("rr", kind, rrc.get(kind, ()))
    add("esrb", "banking", [(c, y) for c, (a, b) in sorted(esrb_cov.items()) for y in range(a, b + 1)])
    add("jst", "banking", jstc)
    for kind, keys in econc.items():
        if ("rule:econ-events", kind) in rules:
            add("rule:econ-events", kind, keys)
    add("rule:slow-normalize", "market_crash", series.get("slow:share_prices", ()))
    ps = pinksheet(path("raw", "market", "pinksheet", "pinksheet-monthly.csv"))
    for s in SPIKE_SERIES:
        ms = sorted(ps.get(s, {}))
        if ms:
            add("rule:pinksheet-spike", "spike:" + s,
                [(SPIKE_PLACE.get(s, "WLD"), y) for y in range((ms[0] + SPIKE_HIGH_MONTHS) // 12, ms[-1] // 12 + 1)])
    for iso, (o, q) in fa.items():
        add("rule:imf-gra", "gra_drawing", [(iso, y) for y in gra_years(o, q)])
    for iso, lv in icsd.items():
        ys = sorted(lv)
        if ys:
            add("rule:investment-bust", "icsd_private", [(iso, y) for y in range(ys[0] + 6, ys[-1] - 1)])
    for lv in bea.values():
        ys = sorted(lv)
        add("rule:investment-bust", "bea_asset", [("USA", y) for y in range(ys[0] + 6, ys[-1] - 1)])
    return cov


PANEL_COLUMNS = ["place", "year", "family", "covered_by", "break", "episodes", "ongoing", "known",
                 "stock_measured", "stock_series"]
LAST_DATED = {"year": 1, "quarter": 10, "month": 12}  # the last month of a year a row of that precision can carry


def label(cat, kind):
    return "%s/%s" % (cat, kind) if cat.startswith("rule:") else cat


def panel(lib, cov, series, rules):
    """Rows of PANEL_COLUMNS for every covered place-year of a family, and
    every break outside coverage (covered_by empty)."""
    starts, ongoing, known = defaultdict(set), defaultdict(set), {}
    last = defaultdict(int)
    for r in lib:
        last[r["episode"]] = max(last[r["episode"]], int(r["date"][:4]))
        known[r["episode"]] = r["episode_known"][:7]
    for r in lib:
        s = int(r["episode_start"][:4])
        starts[(r["family"], r["place"], s)].add(r["episode"])
        for y in range(s + 1, last[r["episode"]] + 1):
            ongoing[(r["family"], r["place"], y)].add(r["episode"])

    def nonevent_known(y, cats):
        return month_str(max(month(y, LAST_DATED[rules[c]["date_precision"]]) + int(rules[c]["known_lag_months"] or 0)
                             for c in cats))

    def line(fam, where, y, cats):
        eps = sorted(starts.get((fam, where, y), ()))
        measured = [s for s in FAMILY_STOCKS.get(fam, []) if (where, y) in series.get(s, ())]
        k = min(known[e] for e in eps) if eps else nonevent_known(y, cats)
        return (where, y, fam, ";".join(sorted({label(*c) for c in cats})), 1 if eps else 0, ";".join(eps),
                ";".join(sorted(ongoing.get((fam, where, y), ()))), k, 1 if measured else 0, ";".join(measured))

    out = [line(fam, where, y, cov[fam][(where, y)]) for fam in sorted(cov) for (where, y) in sorted(cov[fam])]
    # breaks outside any catalogue's stated coverage still belong in the panel
    seen = {(p, y, f) for p, y, f, *_ in out}
    for (fam, where, y) in sorted(starts):
        if (where, y, fam) not in seen:
            out.append(line(fam, where, y, set()))
    out.sort()
    return out


# ---------------------------------------------------------------- main

def write_csv(p, header, rows):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        for r in rows:
            w.writerow([r[h] for h in header] if isinstance(r, dict) else r)


def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def collect_rows():
    """(rows, fa, icsd, bea, rr onsets, esrb codes, states)."""
    fa = {iso: v for iso, v in imf_fa(path("raw", "buffers", "imf-fa", "FA.csv.gz")).items()}
    states = state_years(imf_joined(fa))
    onsets = rr_onsets(path("raw", "slow", "rr", "global_crisis_data.xlsx"))
    esrb_rows, esrb_codes = read_esrb(path("raw", "slow", "esrb", "crises.xlsx"))
    rows = []
    rows += read_lv2018(path("derived", "lv2018.csv"))
    rows += read_lv2026(path("raw", "slow", "lv", "lv2026.csv"))
    rows += read_rr(onsets)
    rows += esrb_rows
    rows += read_slow_events(path("slow", "events.tsv"))
    rows += read_jst(path("raw", "slow", "jst", "jst.csv"))
    rows += read_econ_events(path("derived", "econ-events.csv"))
    rows += read_spikes(pinksheet(path("raw", "market", "pinksheet", "pinksheet-monthly.csv")))
    rows += read_gra(fa)
    # investment levels only for years the state existed (ICSD back-casts the Soviet republics from 1960)
    icsd = {iso: {y: v for y, v in lv.items() if states.exists(iso, y)}
            for iso, lv in icsd_private(path("raw", "econ", "imf-sdmx-holdings-reserves", "ICSD.csv.gz")).items()}
    rows += read_icsd_busts(icsd)
    bea = bea_assets(path("raw", "buffers", "bea-nipa"))
    rows += read_bea_busts(bea)
    return rows, fa, icsd, bea, onsets, esrb_codes, states


def places_known(countries, states):
    """Places the panel may hold: current countries, states in the
    Gleditsch-Ward list, a few historical codes, and the markets. Not
    regional central banks or IMF country groups (FA's BCE, G001...)."""
    markets = set(SPIKE_PLACE.values()) | {"WLD"}
    return lambda p: p in countries or p in states.iv or p in HISTORICAL or p in markets


def build(out_dir, panel_dir):
    del INPUTS[:]
    rules = load_rules(os.path.join(RES, "rules.csv"))
    mech = load_mechanisms(os.path.join(RES, "mechanisms.csv"))
    rows, fa, icsd, bea, onsets, esrb_codes, states = collect_rows()
    countries, series = stock_coverage()
    ok = places_known(countries, states)
    rows = [r for r in rows if ok(r["place"])]
    fa = {iso: v for iso, v in fa.items() if ok(iso)}
    lib, _ = library(rows, rules, mech)
    write_csv(os.path.join(out_dir, "episodes.csv"), COLUMNS, lib)
    write_csv(os.path.join(out_dir, "families.csv"), ["by", "name", "kind", "episodes", "rows"], family_counts(lib))
    write_csv(os.path.join(out_dir, "hindcasts.csv"),
              ["hindcast", "places", "from", "through", "families", "episodes", "episode_ids", "catalogue_kinds"],
              hindcasts(lib))
    cov = catalogue_coverage(countries, rr_coverage(onsets), jst_coverage(path("raw", "slow", "jst", "jst.csv")),
                             econ_coverage(path("derived", "econ-coverage.csv")), series, fa, icsd, bea,
                             esrb_coverage(esrb_codes), states)
    pan = panel(lib, cov, series, rules)
    write_csv(os.path.join(panel_dir, "panel.csv"), PANEL_COLUMNS, pan)
    ins = sorted(set(os.path.relpath(p, ROOT) for p in INPUTS))
    write_csv(os.path.join(out_dir, "manifest.csv"), ["input", "sha256"],
              [("version", VERSION)] + [(p, sha256(os.path.join(ROOT, p))) for p in ins])
    return lib, pan


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else RES
    pdir = sys.argv[2] if len(sys.argv) > 2 else path("derived", "episodes")
    lib, pan = build(out, pdir)
    print("episodes.csv: %d rows, %d episodes; panel: %d place-year-family rows, %d breaks"
          % (len(lib), len({r["episode"] for r in lib}), len(pan), sum(r[4] for r in pan)))
