"""Fast signals for the event model's quarter node (bead bnm-82j.2,
bnm.fast): monthly commodity prices, exchange rates, sovereign yield
spreads and port traffic, as series known point in time.

  venv/bin/python -I scripts/fast-data.py portwatch   # download PortWatch, monthly aggregates
  venv/bin/python -I scripts/fast-data.py build       # write data/fast/series.tsv

Sources (all open, no account; each download in its own directory under
data/raw with a SOURCE.txt):
  data/raw/fast-pinksheet/CMO-Historical-Data-Monthly.xlsx  World Bank Pink
      Sheet, "Monthly Indices" sheet: Energy, Food, Fertilizers (nominal
      USD, 2010 = 100), from 1960.
  data/raw/fast-fx/WS_XRU_csv_flat.zip  BIS US dollar exchange rates,
      monthly, end of period, LCU per USD (ISO2 areas).
  data/raw/fast-spreads/IRLTLT01<ISO2>M156N.csv  OECD Main Economic
      Indicators long-term government bond yields via FRED, monthly
      averages, percent; the US series is the benchmark.
  data/raw/fast-portwatch/{ports,chokepoints}-monthly.csv  IMF PortWatch
      daily port calls by country and daily chokepoint transits, summed
      by month here from the ArcGIS feature service (from 2019).

Output: data/fast/series.tsv, one value per line

  unit  var  freq  period-end  available  value

values already transformed (the Clojure side reads them as they are):
  world fast/energy, fast/food, fast/fertiliser   log index
  ISO3  fast/fx                                  log LCU per USD, end of month
  ISO3  fast/spread                              10-year yield less the US's, points
  ISO3  fast/portcalls                           log(1 + port calls in the month)
  world fast/chokepoints                         log transits through all chokepoints

Availability: one month after the period for every series. The Pink Sheet
comes out on the second business day of the next month; the BIS and OECD
monthly figures and PortWatch's weekly update all reach a month's end
within the next month. None is a real-time vintage (no ALFRED vintages for
these series): market prices and yields are not revised, the Pink Sheet
rarely and PortWatch when its method changes; each is one current download.

All downloads are untrusted data: run with -I, from outside their
directories; nothing here executes them.
"""
import csv
import io
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import zipfile

RAW = "data/raw"
OUT = "data/fast/series.tsv"
PW = os.path.join(RAW, "fast-portwatch")
PW_BASE = "https://services9.arcgis.com/weJ1QsnbMYJlCHdG/arcgis/rest/services"


def month_end(y, m):
    """The last day of month m of year y, as YYYY-MM-DD."""
    days = [31, 29 if (y % 4 == 0 and (y % 100 != 0 or y % 400 == 0)) else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    return f"{y:04d}-{m:02d}-{days[m - 1]:02d}"


def next_month(y, m):
    return (y + 1, 1) if m == 12 else (y, m + 1)


def row(unit, var, y, m, value):
    """A series line: available one month after the period."""
    ay, am = next_month(y, m)
    return [unit, var, "m", month_end(y, m), month_end(ay, am), repr(float(value))]


def iso2_to_iso3(path="data/ref/countryInfo.txt"):
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            p = line.rstrip("\n").split("\t")
            if len(p) > 1 and len(p[0]) == 2 and len(p[1]) == 3:
                out[p[0]] = p[1]
    return out


# --- the Pink Sheet --------------------------------------------------------------

PINK = {"fast/energy": "Energy", "fast/food": "Food", "fast/fertiliser": "Fertilizers"}


def pinksheet(path=os.path.join(RAW, "fast-pinksheet", "CMO-Historical-Data-Monthly.xlsx")):
    import openpyxl
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    rows = list(wb["Monthly Indices"].iter_rows(values_only=True))
    # the column names are spread over the rows above the first month,
    # some marked "**"; a column takes the name found in it
    first = next((i for i, r in enumerate(rows) if r and re.fullmatch(r"\d{4}M\d{2}", str(r[0] or "").strip())), None)
    if first is None:
        sys.exit("fast-data: no monthly rows in the Monthly Indices sheet")
    names = {}
    for r in rows[:first]:
        for j, c in enumerate(r or ()):
            n = str(c or "").replace("*", "").strip()
            if n and j > 0:
                names.setdefault(n, j)
    col = {}
    for var, name in PINK.items():
        if name not in names:
            sys.exit(f"fast-data: no column {name!r} in the Monthly Indices sheet")
        col[var] = names[name]
    hi = first - 1
    out = []
    for r in rows[hi + 1:]:
        mt = re.fullmatch(r"(\d{4})M(\d{2})", str(r[0] or "").strip())
        if not mt:
            continue
        y, m = int(mt.group(1)), int(mt.group(2))
        for var, c in col.items():
            x = r[c]
            if isinstance(x, (int, float)) and not isinstance(x, bool) and x > 0:
                out.append(row("world", var, y, m, math.log(x)))
    return out


# --- exchange rates ----------------------------------------------------------------

def fx(iso3, path=os.path.join(RAW, "fast-fx", "WS_XRU_csv_flat.zip")):
    out = []
    z = zipfile.ZipFile(path)
    name = z.namelist()[0]
    with z.open(name) as f:
        r = csv.reader(io.TextIOWrapper(f, encoding="utf-8"))
        h = next(r)
        ix = {k.split(":")[0]: i for i, k in enumerate(h)}
        for p in r:
            if not (p[ix["FREQ"]].startswith("M:") and p[ix["COLLECTION"]].startswith("E:")):
                continue
            a2 = p[ix["REF_AREA"]].split(":")[0]
            a3 = iso3.get(a2)
            v = p[ix["OBS_VALUE"]]
            mt = re.fullmatch(r"(\d{4})-(\d{2})", p[ix["TIME_PERIOD"]])
            if not (a3 and v and mt) or a3 == "USA":
                continue
            x = float(v)
            if x > 0:
                out.append(row(a3, "fast/fx", int(mt.group(1)), int(mt.group(2)), math.log(x)))
    return out


# --- sovereign spreads -------------------------------------------------------------

def read_fred(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        r = csv.reader(f)
        next(r)
        for d, v in r:
            try:
                out[(int(d[:4]), int(d[5:7]))] = float(v)
            except ValueError:
                pass
    return out


def spreads(iso3, d=os.path.join(RAW, "fast-spreads")):
    us = read_fred(os.path.join(d, "IRLTLT01USM156N.csv"))
    out = []
    for fn in sorted(os.listdir(d)):
        mt = re.fullmatch(r"IRLTLT01([A-Z]{2})M156N\.csv", fn)
        if not mt or mt.group(1) == "US":
            continue
        a3 = iso3.get(mt.group(1))
        if not a3:
            continue
        for (y, m), v in sorted(read_fred(os.path.join(d, fn)).items()):
            if (y, m) in us:
                out.append(row(a3, "fast/spread", y, m, v - us[(y, m)]))
    return out


# --- PortWatch ---------------------------------------------------------------------

def arcgis(layer, params):
    q = urllib.parse.urlencode(dict(params, f="json"))
    req = urllib.request.Request(f"{PW_BASE}/{layer}/FeatureServer/0/query?{q}", headers={"User-Agent": "bnm-research"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                d = json.load(r)
            if "error" in d:
                raise RuntimeError(str(d["error"]))
            if d.get("exceededTransferLimit"):
                raise RuntimeError("transfer limit exceeded")
            if not isinstance(d.get("features"), list):
                raise RuntimeError(f"no features in the response: {str(d)[:200]}")
            return [f["attributes"] for f in d["features"]]
        except Exception as e:  # retried, then fatal
            if attempt == 4:
                raise
            print("  retry", layer, params.get("where"), e)
            time.sleep(5 * (attempt + 1))
    raise RuntimeError("unreachable: arcgis retries exhausted")


FIELDS = {"Daily_Ports_Data": ["portcalls", "import", "export"], "Daily_Chokepoints_Data": ["n_total"]}


def stats(fs):
    return json.dumps([{"statisticType": "sum", "onStatisticField": f, "outStatisticFieldName": "s_" + f} for f in fs]
                      + [{"statisticType": "count", "onStatisticField": "ObjectId", "outStatisticFieldName": "n"}])


def month_query(layer, y, m, group):
    """One month's sums grouped by `group` (each s_<field> and n, the days
    reported); when the service refuses the month (it does for some), the
    month's days queried one at a time and summed."""
    fs = FIELDS[layer]
    q = lambda where: arcgis(layer, {"where": where, "groupByFieldsForStatistics": group, "outStatistics": stats(fs)})
    try:
        return q(f"year={y} AND month={m}")
    except RuntimeError as e:
        print("  by day:", layer, y, m, e)
    acc = {}
    for d in range(1, int(month_end(y, m)[-2:]) + 1):
        for a in q(f"year={y} AND month={m} AND day={d}"):
            t = acc.setdefault(a[group], dict({group: a[group], "n": 0}, **{"s_" + f: 0 for f in fs}))
            t["n"] += a["n"]
            for f in fs:
                t["s_" + f] += a["s_" + f] or 0
    return list(acc.values())


def fetch_month(y, m, final):
    """One month's ports and chokepoints, cached in data/raw/fast-portwatch/
    months/ once the month is over (`final`); a finished month must have
    both, only the current (partial) month may still be empty."""
    path = os.path.join(PW, "months", f"{y:04d}-{m:02d}.json")
    if final and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    ports = [[y, m, a["ISO3"], a["s_portcalls"], a["s_import"], a["s_export"], a["n"]]
             for a in month_query("Daily_Ports_Data", y, m, "ISO3")]
    chokes = [[y, m, a["portid"], a["s_n_total"], a["n"]] for a in month_query("Daily_Chokepoints_Data", y, m, "portid")]
    if final and not (ports and chokes):
        raise RuntimeError(f"PortWatch returned no ports or no chokepoints for {y}-{m:02d}")
    got = {"ports": ports, "chokes": chokes}
    if final:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path + ".tmp", "w", encoding="utf-8") as f:
            json.dump(got, f)
        os.replace(path + ".tmp", path)
    print(y, m, len(ports), len(chokes), flush=True)
    return got


def portwatch(workers=6):
    """Monthly sums by country (port calls, import and export volume, days
    reported) and by chokepoint (transits), from January 2019, months
    fetched in parallel (the service is slow and refuses some queries),
    each finished month cached so a rerun resumes."""
    from concurrent.futures import ThreadPoolExecutor
    os.makedirs(PW, exist_ok=True)
    now = time.gmtime()
    months, ym = [], (2019, 1)
    while ym <= (now.tm_year, now.tm_mon):
        months.append(ym)
        ym = next_month(*ym)
    cur = (now.tm_year, now.tm_mon)
    with ThreadPoolExecutor(workers) as ex:
        res = list(ex.map(lambda k: fetch_month(k[0], k[1], k < cur), months))
    ports = [r for g in res for r in g["ports"]]
    chokes = [r for g in res for r in g["chokes"]]
    with open(os.path.join(PW, "ports-monthly.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["year", "month", "iso3", "portcalls", "import", "export", "port_days"])
        w.writerows(ports)
    with open(os.path.join(PW, "chokepoints-monthly.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["year", "month", "portid", "transits", "days"])
        w.writerows(chokes)


def portwatch_series():
    out = []
    with open(os.path.join(PW, "ports-monthly.csv"), encoding="utf-8") as f:
        ports = list(csv.DictReader(f))
    with open(os.path.join(PW, "chokepoints-monthly.csv"), encoding="utf-8") as f:
        chokes = list(csv.DictReader(f))
    # a month counts only when the chokepoints report on every day of it
    # (28 chokepoints x days in month); the last, partial month drops out
    days = {}
    for r in chokes:
        k = (int(r["year"]), int(r["month"]))
        days[k] = days.get(k, 0) + int(r["days"])
    full = {k for k, n in days.items() if n >= 28 * int(month_end(*k)[-2:])}
    for r in ports:
        k = (int(r["year"]), int(r["month"]))
        if k in full and len(r["iso3"]) == 3:
            out.append(row(r["iso3"], "fast/portcalls", k[0], k[1], math.log1p(float(r["portcalls"]))))
    tot = {}
    for r in chokes:
        k = (int(r["year"]), int(r["month"]))
        tot[k] = tot.get(k, 0.0) + float(r["transits"])
    for k in sorted(tot):
        if k in full and tot[k] > 0:
            out.append(row("world", "fast/chokepoints", k[0], k[1], math.log(tot[k])))
    return out


def build():
    iso3 = iso2_to_iso3()
    rows = pinksheet() + fx(iso3) + spreads(iso3) + portwatch_series()
    # every series the families read must be there, or nothing is written
    want = list(PINK) + ["fast/fx", "fast/spread", "fast/portcalls", "fast/chokepoints"]
    missing = [v for v in want if not any(r[1] == v for r in rows)]
    if missing:
        raise RuntimeError(f"fast-data: no rows for {missing}")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerows(sorted(rows))
    counts = {}
    for r in rows:
        c = counts.setdefault(r[1], [set(), r[3], r[3]])
        c[0].add(r[0])
        c[1] = min(c[1], r[3])
        c[2] = max(c[2], r[3])
    for v, (units, a, b) in sorted(counts.items()):
        print(f"{v:18s} {len(units):4d} units  {a[:7]}..{b[:7]}")
    print(len(rows), "rows ->", OUT)


if __name__ == "__main__":
    {"portwatch": portwatch, "build": build}[sys.argv[1]]()
