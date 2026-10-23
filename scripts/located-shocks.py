"""Located, dated shocks on A5 cells (bead bnm-1pq.27; docs/ESSENTIALS.md
section 8 step 10): earthquakes, tropical cyclones, floods and conflict
onsets, each the set of resolution-6 cells its footprint covers, for the
registered test of cell against country concentration (bnm.located-shocks).

Sources (data/raw/hazards/<source>/, each with a SOURCE.txt):
  usgs-comcat   USGS ComCat, M6 and above 1990-2024 (m6-1990-2024.csv), and
                for the events with a ShakeMap its MMI grid
                (shakemap/<id>.covjson, coverage_mmi_low_res: about 0.13
                degrees); `fetch` downloads them
  ibtracs       IBTrACS v04r01 since 1980, every agency's track, 3-hourly
  dfo-floods    Dartmouth Flood Observatory Global Flood Records (Zenodo
                10.5281/zenodo.19288171), 1985-2023, affected-area polygons
  conflict      the project's own GDELT event catalogue on cells
                (data/norm/gdelt-a5: events placed in A5 cells by
                bnm.ingest's gdelt-a5 source)

Footprints (chosen before any output was read):
  earthquake  ShakeMap MMI 7 or more (very strong shaking: damage to
              ordinary buildings); intensity the cell's highest MMI
  cyclone     within the hurricane-force (64 kt) wind radius of the track
              (USA_R64, the mean of the four quadrants given), the track
              densified to about 10 km; a 64 kt point without radii takes
              the median radius of the points with radii in its wind band;
              wind USA_WIND (1-minute), else WMO_WIND / 0.88; intensity the
              cell's highest wind, kt
  flood       DFO's affected-area polygon; intensity DFO's severity class
              (1 large, 1.5 very large, 2 extreme)
  conflict    a cell's assault events (CAMEO 18-20) in GDELT, as a rate per
              million of the year's located events (GDELT's volume grows
              about tenfold 1985-2005): an onset is a cell-year with at
              least ONSET_N events and a rate of at least ONSET_RATE whose
              five years before were all under a quarter of that rate;
              intensity the rate; coverage 1 (the cell)

A cell's coverage is the share of sample points in it (about 64 on a grid
inside its boundary) that the footprint covers. Only cells with land (the
crosswalk's land or disputed parts) are kept.

Writes data/derived/hazards/:
  shocks.csv      shock_id,hazard,date,year,cell,coverage,intensity
  catalogue.csv   shock_id,hazard,source,date,year,lon,lat,size,size_unit,
                  name,cells,cells_half (cells with coverage >= 0.5)
  conflict-cells.csv  year,cell,iso,n,rate,first_date (the GDELT aggregate)
  cement.csv      BGS finished cement production in stages.csv's columns
                  (commodity cement, stage plant), not in stages.csv

    python -I scripts/located-shocks.py fetch [data-root]   # ShakeMap grids
    python -I scripts/located-shocks.py fetch-reverse [data-root]  # the same from the latest event back
    python -I scripts/located-shocks.py build [data-root]
"""
import collections
import concurrent.futures as cf
import csv
import gzip
import json
import math
import os
import sqlite3
import struct
import subprocess
import sys
import time
import urllib.request

import a5
import numpy as np
import shapely

RES = 6
EARTH_KM = 6371.0088
NMI_KM = 1.852
MMI_MIN = 7.0
WIND_MIN = 64.0
ONSET_N = 5
ONSET_RATE = 2.0  # assault events per million located events in the year
QUIET_YEARS = 5
FIRST_YEAR, LAST_YEAR = 1990, 2024
BGS_LAG = 2


def log(*a):
    print(*a, file=sys.stderr, flush=True)


def paths(root):
    raw = os.path.join(root, "raw", "hazards")
    return {"quakes": os.path.join(raw, "usgs-comcat"),
            "ibtracs": os.path.join(raw, "ibtracs", "ibtracs.since1980.list.v04r01.csv"),
            "dfo": os.path.join(raw, "dfo-floods", "Global_Flood_Records.gpkg"),
            "gdelt": os.path.join(root, "norm", "gdelt-a5"),
            "xw": os.path.join(root, "derived", "cells", "crosswalk.csv"),
            "bgs": os.path.join(root, "raw", "material", "bgs-world-mineral-statistics",
                                "bgs_world_mineral_statistics_1970-2021.csv.gz"),
            "out": os.path.join(root, "derived", "hazards")}


# ------------------------------------------------------------------ fetch

def get(url, tries=6):
    """The body at `url`; ComCat answers 429 above a few requests a second,
    so a retry waits longer each time."""
    for i in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - retried, then raised
            if i == tries - 1:
                raise
            log("retry", url, e)
            time.sleep(15 * (i + 1))


def fetch_one(qdir, eid):
    out = os.path.join(qdir, "shakemap", f"{eid}.covjson")
    if os.path.exists(out):
        return eid, "ok", ""
    d = json.loads(get(f"https://earthquake.usgs.gov/fdsnws/event/1/query?eventid={eid}&format=geojson"))
    time.sleep(FETCH_PAUSE)
    key = "download/coverage_mmi_low_res.covjson"
    for p in d["properties"]["products"].get("shakemap", []):
        if key in p["contents"]:
            body = get(p["contents"][key]["url"])
            tmp = out + ".part"
            with open(tmp, "wb") as f:
                f.write(body)
            os.replace(tmp, out)
            return eid, "ok", f"{p['source']} {p['properties'].get('version', '')}"
    return eid, "none", ""


FETCH_PAUSE = 1.0  # seconds after each ComCat query: it answers 429 to bursts
NEAR_LAND_RINGS = 3  # an epicentre this many cells (about 250 km) from land or nearer
MAX_DEPTH_KM = 300.0


def near_land(lon, lat, land):
    c = a5.lonlat_to_cell((lon, lat), RES)
    return any(a5.u64_to_hex(k) in land for k in a5.uncompact(a5.grid_disk(c, NEAR_LAND_RINGS))
               if not a5.is_compaction_marker(k))


def fetch(root, reverse=False):
    """ShakeMap MMI grids for the M6+ events with a ShakeMap, epicentre
    within about 250 km of land and above 300 km depth (deeper ones do not
    shake the surface to MMI 7), one ComCat query at a time."""
    p = paths(root)
    qdir = p["quakes"]
    land = land_cells(p["xw"])
    os.makedirs(os.path.join(qdir, "shakemap"), exist_ok=True)
    # a second fetch can run from the other end (`fetch-reverse`), writing
    # its own index; both are read here
    idx_path = os.path.join(qdir, "shakemap-index-reverse.csv" if reverse else "shakemap-index.csv")
    done = {}
    for name in ("shakemap-index.csv", "shakemap-index-reverse.csv"):
        path = os.path.join(qdir, name)
        if os.path.exists(path):
            with open(path) as f:
                done.update({r["id"]: r for r in csv.DictReader(f)})
    with open(os.path.join(qdir, "m6-1990-2024-shakemap.csv")) as f:
        evs = list(csv.DictReader(f))
    ids = [e["id"] for e in evs
           if float(e["depth"] or 0) <= MAX_DEPTH_KM and near_land(float(e["longitude"]), float(e["latitude"]), land)]
    todo = [i for i in ids if i not in done][::-1 if reverse else 1]
    log(len(evs), "ShakeMap events,", len(ids), "near land and shallow,", len(todo), "to fetch")
    with open(idx_path, "a", newline="") as f:
        w = csv.writer(f)
        if f.tell() == 0:
            w.writerow(["id", "status", "product"])
        for n, eid in enumerate(todo):
            try:
                w.writerow(fetch_one(qdir, eid))
            except Exception as e:  # noqa: BLE001 - logged, refetched next run
                log("failed", eid, e)
            if n % 50 == 0:
                f.flush()
                log(n, "of", len(todo))


# ------------------------------------------------------------- geometry

def haversine_km(lon1, lat1, lon2, lat2):
    p1, p2 = np.radians(lat1), np.radians(lat2)
    dl, dp = np.radians(lon2 - lon1), p2 - p1
    h = np.sin(dp / 2) ** 2 + np.cos(p1) * np.cos(p2) * np.sin(dl / 2) ** 2
    return 2 * EARTH_KM * np.arcsin(np.sqrt(np.minimum(h, 1.0)))


def cell_hex(lon, lat):
    return a5.u64_to_hex(a5.lonlat_to_cell((float(lon), float(lat)), RES))


_samples = {}


def cell_samples(h, n=10):
    """About 64 points on a regular grid inside the cell's boundary (its
    centre alone across the antimeridian or a pole), as (lons, lats)."""
    if h in _samples:
        return _samples[h]
    c = a5.hex_to_u64(h)
    b = a5.cell_to_boundary(c)
    lons = [p[0] for p in b]
    if max(lons) - min(lons) > 180 or any(abs(p[1]) > 89.5 for p in b):
        lon, lat = a5.cell_to_lonlat(c)
        s = (np.array([lon]), np.array([lat]))
    else:
        poly = shapely.Polygon(b)
        x0, y0, x1, y1 = poly.bounds
        xs, ys = np.meshgrid(x0 + (np.arange(n) + 0.5) * (x1 - x0) / n, y0 + (np.arange(n) + 0.5) * (y1 - y0) / n)
        xs, ys = xs.ravel(), ys.ravel()
        k = shapely.contains_xy(poly, xs, ys)
        s = (xs[k], ys[k]) if k.any() else (np.array([a5.cell_to_lonlat(c)[0]]), np.array([a5.cell_to_lonlat(c)[1]]))
    _samples[h] = s
    return s


def coverage(cands, inside, value=None):
    """{cell: (coverage, intensity)} over candidate cells: the share of the
    cell's samples `inside` (a function of lons, lats giving a boolean
    array) covers, and the highest `value` (a function of the same) over
    the covered samples."""
    out = {}
    for h in cands:
        xs, ys = cell_samples(h)
        k = inside(xs, ys)
        if k.any():
            out[h] = (float(k.mean()), float(value(xs[k], ys[k]).max()) if value else None)
    return out


def land_cells(xw_path):
    with open(xw_path) as f:
        return {r["cell"] for r in csv.DictReader(f) if r["flag"] in ("land", "disputed")}


# ---------------------------------------------------------- earthquakes

def read_covjson(path):
    d = json.load(open(path))
    ax = d["domain"]["axes"]
    xs = np.linspace(ax["x"]["start"], ax["x"]["stop"], ax["x"]["num"])
    ys = np.linspace(ax["y"]["start"], ax["y"]["stop"], ax["y"]["num"])
    r = d["ranges"]["MMI"]
    v = np.array([np.nan if x is None else x for x in r["values"]], dtype=float).reshape(r["shape"])
    if r["axisNames"] == ["x", "y"]:
        v = v.T
    return xs, ys, v


def grid_lookup(xs, ys, v):
    """The grid's value at the nearest node, NaN outside the grid."""
    dx = (xs[-1] - xs[0]) / max(1, len(xs) - 1)
    dy = (ys[-1] - ys[0]) / max(1, len(ys) - 1)

    def f(lon, lat):
        i = np.rint((lat - ys[0]) / dy).astype(int)
        j = np.rint((lon - xs[0]) / dx).astype(int)
        ok = (i >= 0) & (i < len(ys)) & (j >= 0) & (j < len(xs))
        out = np.full(len(lon), np.nan)
        out[ok] = v[i[ok], j[ok]]
        return out
    return f


def quakes(p, land):
    qdir = p["quakes"]
    with open(os.path.join(qdir, "m6-1990-2024.csv")) as f:
        events = {r["id"]: r for r in csv.DictReader(f) if r["type"] == "earthquake"}
    shocks, cat = [], []
    n_grid = 0
    for eid, e in sorted(events.items(), key=lambda kv: kv[1]["time"]):
        path = os.path.join(qdir, "shakemap", f"{eid}.covjson")
        if not os.path.exists(path):
            continue
        n_grid += 1
        xs, ys, v = read_covjson(path)
        if not (np.nanmax(v) >= MMI_MIN):
            continue
        ii, jj = np.nonzero(v >= MMI_MIN)
        cands = {cell_hex(xs[j], ys[i]) for i, j in zip(ii, jj)} & land
        mmi = grid_lookup(xs, ys, v)
        cov = coverage(cands, lambda a, b: mmi(a, b) >= MMI_MIN, mmi)
        if not cov:
            continue
        sid = f"eq-{eid}"
        date = e["time"][:10]
        for h, (c, x) in sorted(cov.items()):
            shocks.append((sid, "earthquake", date, int(date[:4]), h, round(c, 4), round(x, 2)))
        cat.append((sid, "earthquake", "usgs-comcat shakemap", date, int(date[:4]), e["longitude"], e["latitude"],
                    e["mag"], "Mw", e["place"], len(cov), sum(1 for c, _ in cov.values() if c >= 0.5)))
    log("earthquakes:", len(events), "M6+,", n_grid, "with a ShakeMap grid,", len(cat), "reach land at MMI 7")
    return shocks, cat


# ------------------------------------------------------------- cyclones

def fnum(s):
    s = s.strip()
    return float(s) if s else None


WIND_BANDS = [64, 83, 96, 113, 137, 1000]


def band(w):
    return next(i for i, b in enumerate(WIND_BANDS[1:]) if w < b)


def read_tracks(path):
    """{sid: (name, [(time, lon, lat, wind kt, r64 km or None)])}, main tracks."""
    tracks = collections.defaultdict(list)
    names = {}
    with open(path, newline="") as f:
        r = csv.reader(f)
        h = next(r)
        next(r)
        ix = {k: h.index(k) for k in ["SID", "NAME", "ISO_TIME", "LAT", "LON", "USA_WIND", "WMO_WIND",
                                       "TRACK_TYPE", "USA_R64_NE", "USA_R64_SE", "USA_R64_SW", "USA_R64_NW"]}
        for row in r:
            if row[ix["TRACK_TYPE"]] != "main":
                continue
            w = fnum(row[ix["USA_WIND"]])
            if w is None:
                w0 = fnum(row[ix["WMO_WIND"]])
                w = w0 / 0.88 if w0 is not None else None
            if w is None:
                continue
            qs = [fnum(row[ix[k]]) for k in ["USA_R64_NE", "USA_R64_SE", "USA_R64_SW", "USA_R64_NW"]]
            qs = [q for q in qs if q is not None]
            r64 = (sum(qs) / len(qs)) * NMI_KM if qs and sum(qs) > 0 else None
            sid = row[ix["SID"]]
            names[sid] = row[ix["NAME"]]
            tracks[sid].append((row[ix["ISO_TIME"]], float(row[ix["LON"]]), float(row[ix["LAT"]]), w, r64))
    return {s: (names[s], sorted(pts)) for s, pts in tracks.items()}


def fallback_radii(tracks):
    by = collections.defaultdict(list)
    for _, pts in tracks.values():
        for _, _, _, w, r in pts:
            if w >= WIND_MIN and r:
                by[band(w)].append(r)
    return {b: float(np.median(v)) for b, v in by.items()}


def densify(pts, fb, step_km=10.0):
    """[(time, lon, lat, wind, radius)] every ~step_km along the segments
    with an end at hurricane force, radius 0 below it."""
    out = []
    for a, b in zip(pts, pts[1:] + pts[-1:]):
        if a[3] < WIND_MIN and b[3] < WIND_MIN:
            continue
        ra = (a[4] or fb.get(band(a[3]), 0.0)) if a[3] >= WIND_MIN else 0.0
        rb = (b[4] or fb.get(band(b[3]), 0.0)) if b[3] >= WIND_MIN else 0.0
        dlon = ((b[1] - a[1] + 180) % 360) - 180
        d = float(haversine_km(a[1], a[2], a[1] + dlon, b[2]))
        n = max(1, int(math.ceil(d / step_km)))
        for k in range(n + (1 if a is b else 0)):
            t = k / n
            w = a[3] + t * (b[3] - a[3])
            if w >= WIND_MIN:
                lon = ((a[1] + t * dlon + 180) % 360) - 180
                out.append((a[0], lon, a[2] + t * (b[2] - a[2]), w, ra + t * (rb - ra)))
    return out


def cyclones(p, land):
    tracks = read_tracks(p["ibtracs"])
    fb = fallback_radii(tracks)
    log("cyclone fallback R64 km by band:", {WIND_BANDS[b]: round(r) for b, r in sorted(fb.items())})
    shocks, cat = [], []
    for sid, (name, pts) in sorted(tracks.items()):
        d = densify(pts, fb)
        d = [x for x in d if x[4] > 0]
        if not d:
            continue
        T = np.array([[x[1], x[2], x[3], x[4]] for x in d])
        cands = set()
        for lon, lat, _, r in T:
            for f in (0.0, 0.5, 1.0):
                for ang in range(0, 360, 45) if f else [0]:
                    dy = f * r * math.cos(math.radians(ang)) / 111.195
                    dx = f * r * math.sin(math.radians(ang)) / (111.195 * max(0.05, math.cos(math.radians(lat))))
                    cands.add(cell_hex(((lon + dx + 180) % 360) - 180, max(-89.9, min(89.9, lat + dy))))
        cands &= land
        if not cands:
            continue

        def near(xs, ys):
            dist = haversine_km(xs[:, None], ys[:, None], T[None, :, 0], T[None, :, 1])
            return dist <= T[None, :, 3]

        cov = coverage(cands, lambda a, b: near(a, b).any(axis=1),
                       lambda a, b: np.where(near(a, b), T[None, :, 2], 0.0).max(axis=1))
        if not cov:
            continue
        date = d[0][0][:10]
        sid2 = f"tc-{sid}"
        for h, (c, x) in sorted(cov.items()):
            shocks.append((sid2, "cyclone", date, int(date[:4]), h, round(c, 4), round(x, 1)))
        peak = max(x[3] for x in d)
        cat.append((sid2, "cyclone", "ibtracs", date, int(date[:4]), round(d[0][1], 3), round(d[0][2], 3),
                    round(peak), "kt", name, len(cov), sum(1 for c, _ in cov.values() if c >= 0.5)))
    log("cyclones:", len(tracks), "tracks,", len(cat), "reach land at hurricane force")
    return shocks, cat


# ---------------------------------------------------------------- floods

def gpkg_geom(blob):
    flags = blob[3]
    env = (flags >> 1) & 0x07
    n = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}[env]
    return shapely.from_wkb(bytes(blob[8 + n:]))


def floods(p, land):
    con = sqlite3.connect(p["dfo"])
    rows = con.execute("select ReportNumber, geom, Country, BeginDate, Severity, Area, MainCause "
                       "from combined_floods").fetchall()
    shocks, cat = [], []
    for rep, blob, country, begin, sev, area, cause in rows:
        if not blob or not begin:
            continue
        g = gpkg_geom(blob)
        if g.is_empty:
            continue
        shapely.prepare(g)
        x0, y0, x1, y1 = g.bounds
        xs, ys = np.meshgrid(np.arange(x0, x1 + 0.2, 0.2), np.arange(y0, y1 + 0.2, 0.2))
        xs, ys = xs.ravel(), ys.ravel()
        k = shapely.contains_xy(g, xs, ys)
        pts = list(zip(xs[k], ys[k])) or [tuple(g.representative_point().coords[0])]
        cands = {cell_hex(x, y) for x, y in pts} & land
        cov = coverage(cands, lambda a, b: shapely.contains_xy(g, a, b))
        if not cov:
            continue
        s = float(sev) if sev not in (None, "") else None
        sid = f"fl-{rep}"
        for h, (c, _) in sorted(cov.items()):
            shocks.append((sid, "flood", begin, int(begin[:4]), h, round(c, 4), s))
        cx, cy = g.centroid.coords[0]
        cat.append((sid, "flood", "dfo", begin, int(begin[:4]), round(cx, 3), round(cy, 3), area, "km2",
                    f"{country} ({cause})", len(cov), sum(1 for c, _ in cov.values() if c >= 0.5)))
    log("floods:", len(rows), "records,", len(cat), "on land")
    return shocks, cat


# -------------------------------------------------------------- conflict

AWK = r"""BEGIN{FS="\t"}
$6 ~ /\.A/ { y=substr($1,1,4); t[y]+=$9
  if ($7=="assault") { k=y "\t" $6; a[k]+=$9; if (!(k in m) || $1<m[k]) m[k]=$1 } }
END{ for (k in t) print "T\t" k "\t" t[k]; for (k in a) print "A\t" k "\t" a[k] "\t" m[k] }"""


def gdelt_cells(gdir, first, last):
    files = sorted(f for f in os.listdir(gdir) if f[:4].isdigit() and first <= int(f[:4]) <= last)

    def run(f):
        r = subprocess.run(["awk", AWK, os.path.join(gdir, f)], capture_output=True, text=True,
                           env={"LC_ALL": "C", "PATH": os.environ.get("PATH", "/usr/bin:/bin")}, check=True)
        return r.stdout
    tot = collections.Counter()
    n = collections.Counter()
    first_date = {}
    with cf.ThreadPoolExecutor(8) as ex:
        for i, out in enumerate(ex.map(run, files)):
            for line in out.splitlines():
                f = line.split("\t")
                if f[0] == "T":
                    tot[int(f[1])] += float(f[2])
                else:
                    k = (int(f[1]), f[2])
                    n[k] += float(f[3])
                    first_date[k] = min(first_date.get(k, f[4]), f[4])
            if i % 500 == 0:
                log("gdelt", i, "of", len(files))
    return tot, n, first_date


def onsets(counts, tot, first, last):
    """[(year, node, n, rate)]: cell-years at ONSET_N events and ONSET_RATE
    or more, the QUIET_YEARS before each under a quarter of the rate."""
    rate = {k: v / tot[k[0]] * 1e6 for k, v in counts.items() if tot.get(k[0])}
    out = []
    for (y, node), r in sorted(rate.items()):
        if not (first <= y <= last) or counts[(y, node)] < ONSET_N or r < ONSET_RATE:
            continue
        if all(rate.get((y - k, node), 0.0) < ONSET_RATE / 4 for k in range(1, QUIET_YEARS + 1)):
            out.append((y, node, counts[(y, node)], r))
    return out


def conflict(p, land, out_dir):
    cache = os.path.join(out_dir, "conflict-cells.csv")
    first = FIRST_YEAR - QUIET_YEARS
    if os.path.exists(cache):
        tot, n, fd = collections.Counter(), collections.Counter(), {}
        with open(cache) as f:
            for r in csv.DictReader(f):
                k = (int(r["year"]), r["node"])
                n[k] = float(r["n"])
                fd[k] = r["first_date"]
                tot[k[0]] = float(r["year_total"])
    else:
        tot, n, fd = gdelt_cells(p["gdelt"], first, LAST_YEAR)
        with open(cache, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["year", "node", "n", "first_date", "year_total"])
            for (y, node), v in sorted(n.items()):
                w.writerow([y, node, int(v), fd[(y, node)], int(tot[y])])
    shocks, cat = [], []
    for y, node, c, r in onsets(n, tot, FIRST_YEAR, LAST_YEAR):
        h = node.split(".A", 1)[1].lower()
        if h not in land:
            continue
        d = fd[(y, node)]
        date = f"{d[:4]}-{d[4:6]}-{d[6:8]}"
        sid = f"cf-{y}-{node}"
        shocks.append((sid, "conflict", date, y, h, 1.0, round(r, 3)))
        lon, lat = a5.cell_to_lonlat(a5.hex_to_u64(h))
        cat.append((sid, "conflict", "gdelt-a5", date, y, round(lon, 3), round(lat, 3), int(c), "events",
                    node, 1, 1))
    log("conflict:", len(cat), "onsets", FIRST_YEAR, "-", LAST_YEAR)
    return shocks, cat


# ---------------------------------------------------------------- cement

def cement(p):
    out = collections.Counter()
    with gzip.open(p["bgs"], "rt", newline="") as f:
        for r in csv.DictReader(f):
            if r["yearbook_table_trans"] != "Production of finished cement" or not r["country_iso3_code"]:
                continue
            try:
                q = float(r["quantity"])
            except ValueError:
                continue
            if r["units"] != "tonnes (metric)":
                sys.exit(f"BGS cement unit {r['units']}")
            out[(r["country_iso3_code"], int(r["year"][:4]))] += q
    return [("cement", "plant", i, y, q, "t cement", "BGS", y + BGS_LAG) for (i, y), q in sorted(out.items()) if q > 0]


# ----------------------------------------------------------------- main

def write(path, header, rows):
    tmp = path + ".part"
    with open(tmp, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)
    os.replace(tmp, path)


def build(root, only=None):
    p = paths(root)
    os.makedirs(p["out"], exist_ok=True)
    land = land_cells(p["xw"])
    parts = {"earthquake": lambda: quakes(p, land), "cyclone": lambda: cyclones(p, land),
             "flood": lambda: floods(p, land), "conflict": lambda: conflict(p, land, p["out"])}
    shocks, cat = [], []
    for k, f in parts.items():
        if only and k not in only:
            continue
        s, c = f()
        shocks += s
        cat += c
    write(os.path.join(p["out"], "shocks.csv"), ["shock_id", "hazard", "date", "year", "cell", "coverage", "intensity"],
          sorted(shocks, key=lambda r: (r[3], r[0], r[4])))
    write(os.path.join(p["out"], "catalogue.csv"),
          ["shock_id", "hazard", "source", "date", "year", "lon", "lat", "size", "size_unit", "name", "cells",
           "cells_half"], sorted(cat, key=lambda r: (r[4], r[0])))
    write(os.path.join(p["out"], "cement.csv"), ["commodity", "stage", "iso", "year", "quantity", "unit", "source",
                                                 "known"], cement(p))
    log("wrote", p["out"])


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "build"
    root = sys.argv[2] if len(sys.argv) > 2 else "data"
    if cmd in ("fetch", "fetch-reverse"):
        fetch(root, cmd == "fetch-reverse")
    elif cmd == "build":
        build(root, set(sys.argv[3].split(",")) if len(sys.argv) > 3 else None)
    else:
        sys.exit(__doc__)
