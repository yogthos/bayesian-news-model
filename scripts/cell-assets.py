"""Plant and resource sources on disk, read into one form for placing on A5
cells (docs/PRINCIPLES.md, principle 11; bnm.cell-assets places them).
Writes data/derived/cells/:

  asset-points.csv    source,id,asset_type,commodity,capacity,unit,
                      start_year,status,known_from,year_flag,country,
                      country_kind,lon,lat,dup_of,end_year,end_flag,
                      end_known
  asset-polygons.edn  one EDN map per line, the same keys and :polygons
                      [[ring ...] ...], each ring [[lon lat] ...], the
                      first ring of a polygon its outline and the rest holes

country is the source's own (country_kind iso3, iso2, fips or name), resolved
by bnm.geo on the Clojure side.

dup_of names the asset another source (or the same one) already has, so
that counting or summing assets counts it once: the sources overlap (SFI's
steel and cement plants are most of USGS's; OGIM's refineries half of
USGS's; PRIO's diamond deposits are mostly in MRDS). Within 2 km and of the
same kind and commodity, the asset of the source first in DUP_PRIORITY is
kept: sfi, ogim, prio-dia, usgs-minfac, mrds; within a source, the first
record at the same coordinates. An oil or gas field point (USGS) inside an
OGIM field polygon, and a PRIO PETRODATA polygon at least half covered by
OGIM's fields, are duplicates of the OGIM field covering most of them. Maus
mining areas are footprints, not counted assets, and are not deduplicated
against the mine points.

When an asset is known to exist (known_from, year_flag):
  start      the source's start year (commissioning, first production)
  discovery  the source's discovery year (a deposit known, not producing)
  snapshot   no year: the year the snapshot was published. Such an asset is
             not to be read into earlier years without this flag.

When an asset ends (end_year, end_flag, end_known; blank for none):
  last-production  MRDS past producers' yr_lst_prd: absent after end_year
  closed-unknown   closed by its status (OGIM retired, SFI shut down, USGS
                   closed, an MRDS past producer with no last year), no year
  idle-unknown     idle by its status (mothballed, care and maintenance,
                   suspended), no year
  cancelled        an OGIM project cancelled
end_known is the source's publication year: a closure is known once the
source reports it, so an asset counts as present until then, and one with
no end year is absent from end_known on (never backdated). GPPD and the
polygon sources carry no status that ends an asset.

Sources (data/raw/material/<dir>/SOURCE.txt for licences), and the year a
snapshot counts from:
  gppd           not here: bnm.cell-assets reads it with bnm.material (2021)
  ogim           EDF OGIM v3.0 (2026): Crude_Oil_Refineries, LNG_Facilities
                 points, INSTALL_DATE when not 1900-01-01;
                 Oil_and_Natural_Gas_Fields polygons, AREA_KM2
  sfi            SFI steel and cement (2021), ethylene (2025): 'year'
  mrds           USGS MRDS: yr_fst_prd, else disc_yr; systematic updates
                 stopped about 2011, so 2011
  usgs-minfac    USGS mineral facilities: Latin America (2016, CSV), Africa
                 (2021), Southwest Asia (2022), China (2023), Indo-Pacific
                 (2024) (File Geodatabases, read with pyogrio)
  prio-petro     PRIO PETRODATA v1.2 polygons (2009): PROD, else DISC
  prio-dia       PRIO DIADATA points with the 2008 ERRATA applied (2008):
                 PROD, else DISC
  maus           Maus et al. 2022 mining polygons v2 (2022): area only, no
                 commodity

Not placed: USGS "Mines, smelters, refineries, and recycling facilities in
the United States" has no coordinates (a place description only).

Downloaded files are untrusted data, read only.

    python -I scripts/cell-assets.py [--root .]
    python -I scripts/cell-assets.py --selftest
"""

import argparse
import collections
import csv
import datetime
import io
import json
import math
import os
import sqlite3
import sys
import warnings
import zipfile

import openpyxl
import shapefile
import shapely
import shapely.geometry

warnings.filterwarnings("ignore")

FIELDS = ["source", "id", "asset_type", "commodity", "capacity", "unit", "start_year", "status",
          "known_from", "year_flag", "country", "country_kind", "lon", "lat", "dup_of",
          "end_year", "end_flag", "end_known"]

DUP_KM = 2.0
DUP_PRIORITY = ["sfi", "ogim", "prio-dia", "usgs-minfac", "mrds"]
EARTH_KM = 6371.0088
MRDS_PUB = 2011

# status words that end an asset, by source: closed for good, or idle
# (care and maintenance, mothballed, suspended), or a project cancelled
END_STATUS = {
    "ogim": {"retired": "closed-unknown", "mothballed": "idle-unknown", "idle": "idle-unknown",
             "suspended": "idle-unknown", "cancelled": "cancelled"},
    "sfi": {"shut down": "closed-unknown", "mothballed": "idle-unknown"},
    "usgs-minfac": {"closed": "closed-unknown", "c": "closed-unknown", "c&m": "idle-unknown",
                    "cm": "idle-unknown", "suspended": "idle-unknown", "s": "idle-unknown"},
    "mrds": {"past producer": "closed-unknown"},
}


def log(*a):
    print(*a, file=sys.stderr, flush=True)


def year_of(v):
    """A plausible year from a number, a date or a string, else None."""
    if v is None:
        return None
    if isinstance(v, (datetime.date, datetime.datetime)):
        y = v.year
    else:
        s = str(v).strip()
        try:
            y = int(float(s[:4])) if len(s) >= 4 and s[:4].isdigit() else int(float(s))
        except ValueError:
            return None
    return y if 1700 <= y <= 2100 else None


def known(start, discovery, snapshot):
    if start:
        return start, "start"
    if discovery:
        return discovery, "discovery"
    return snapshot, "snapshot"


def num(v):
    try:
        x = float(v)
    except (TypeError, ValueError):
        return None
    return x if x > 0 else None


def clean(v):
    return "" if v is None else str(v).strip()


# --- GeoPackage geometry ------------------------------------------------------------

def gpkg_geom(blob):
    """A shapely geometry from a GeoPackage binary blob: header, envelope, WKB."""
    flags = blob[3]
    env = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}[(flags >> 1) & 0b111]
    return shapely.from_wkb(bytes(blob[8 + env:]))


def polygons(g):
    """[[ring ...] ...] of a Polygon or MultiPolygon, coordinates rounded."""
    def ring(r):
        return [[round(x, 5), round(y, 5)] for x, y, *_ in r.coords]
    parts = list(g.geoms) if g.geom_type == "MultiPolygon" else [g] if g.geom_type == "Polygon" else []
    return [[ring(p.exterior)] + [ring(i) for i in p.interiors] for p in parts if not p.is_empty]


def edn(v):
    if v is None:
        return "nil"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return repr(v)
    if isinstance(v, str):
        return json.dumps(v)
    if isinstance(v, dict):
        return "{" + " ".join(f":{k} {edn(x)}" for k, x in v.items()) + "}"
    return "[" + " ".join(edn(x) for x in v) + "]"


# --- sources -----------------------------------------------------------------------

def ogim(root, points, polys):
    db = sqlite3.connect(f"file:{os.path.join(root, 'data/raw/material/edf-ogim-v3/OGIM_v3.0.gpkg')}?mode=ro",
                         uri=True)
    pub = 2026
    for table, kind, cap, unit in [("Crude_Oil_Refineries", "refinery", "LIQ_CAPACITY_BPD", "bbl/d"),
                                   ("LNG_Facilities", "lng", "GAS_CAPACITY_MMCFD", "mmcf/d")]:
        q = f'select OGIM_ID, COUNTRY, FAC_TYPE, FAC_STATUS, INSTALL_DATE, {cap}, LONGITUDE, LATITUDE from "{table}"'
        for oid, country, ftype, status, inst, c, lon, lat in db.execute(q):
            start = None if (inst or "").startswith("1900") else year_of(inst)
            kf, flag = known(start, None, pub)
            commodity = "oil" if kind == "refinery" else ("lng_" + ftype.lower() if ftype not in (None, "N/A") else "lng")
            points.append(dict(source="ogim", id=f"ogim:{oid}", asset_type=kind, commodity=commodity,
                               capacity=num(c), unit=unit, start_year=start, status=clean(status).lower(),
                               known_from=kf, year_flag=flag, country=country, country_kind="name",
                               lon=lon, lat=lat, pub=pub))
    for oid, country, rtype, area, geom in db.execute(
            'select OGIM_ID, COUNTRY, RESERVOIR_TYPE, AREA_KM2, geom from "Oil_and_Natural_Gas_Fields"'):
        ps = polygons(gpkg_geom(geom))
        if not ps:
            continue
        r = clean(rtype).lower()
        commodity = "gas" if "gas" in r or "condensate" in r else ("oil" if r == "oil" else "oil_gas")
        polys.append(dict(source="ogim", id=f"ogim:{oid}", asset_type="field", commodity=commodity,
                          capacity=num(area), unit="km2", start_year=None, status="",
                          known_from=pub, year_flag="snapshot", country=country, country_kind="name",
                          polygons=ps))


def sheet(path, name):
    ws = openpyxl.load_workbook(path, read_only=True)[name]
    it = ws.iter_rows(values_only=True)
    head = [clean(h) for h in next(it)]
    for row in it:
        yield dict(zip(head, row))


def sfi(root, points):
    d = os.path.join(root, "data/raw/material/sfi-asset-databases")
    for f, s, kind, capf, unit, pub, commodity in [
            ("SFI-Global-Steel-Database-July-2021.xlsx", "SFI_ALD_Steel_Database", "steel_plant", "capacity", "Mt/y", 2021, None),
            ("SFI-Global-Cement-Database-July-2021.xlsx", "SFI_ALD_Cement_Database", "cement_plant", "capacity", "Mt/y", 2021, "cement"),
            ("Global_Ethylene_Production_Database_December_2025.xlsx", "Global Ethylene Production Data",
             "ethylene_plant", "capacity (Kt ethylene/year)", "kt/y", 2025, "ethylene")]:
        for r in sheet(os.path.join(d, f), s):
            if r.get("latitude") is None or r.get("longitude") is None or not r.get("uid"):
                continue
            start = year_of(r.get("year"))
            kf, flag = known(start, None, pub)
            com = commodity or ("steel_" + clean(r.get("primary_production_type")).lower().replace("/", "_")
                                if r.get("primary_production_type") else "steel")
            points.append(dict(source="sfi", id=f"sfi:{r['uid']}", asset_type=kind, commodity=com,
                               capacity=num(r.get(capf)), unit=unit, start_year=start,
                               status=clean(r.get("status")).lower(), known_from=kf, year_flag=flag,
                               country=r.get("iso3"), country_kind="iso3",
                               lon=r["longitude"], lat=r["latitude"], pub=pub))


def mrds(root, points):
    zf = zipfile.ZipFile(os.path.join(root, "data/raw/material/usgs-mrds/mrds-csv.zip"))
    rows = csv.DictReader(io.TextIOWrapper(zf.open("mrds.csv"), encoding="latin-1"))
    for r in rows:
        try:
            lon, lat = float(r["longitude"]), float(r["latitude"])
        except ValueError:
            continue
        start = year_of(r.get("yr_fst_prd"))
        kf, flag = known(start, year_of(r.get("disc_yr")), MRDS_PUB)
        stat = clean(r.get("dev_stat")).lower()
        kind = "mine" if stat in ("producer", "past producer") else "deposit"
        points.append(dict(source="mrds", id=f"mrds:{r['dep_id']}", asset_type=kind,
                           commodity=clean(r.get("commod1")).split(",")[0].strip().lower(),
                           capacity=None, unit="", start_year=start, status=stat,
                           known_from=kf, year_flag=flag, country=r.get("country"), country_kind="name",
                           lon=lon, lat=lat, pub=MRDS_PUB, last_year=year_of(r.get("yr_lst_prd"))))


# MINFAC_LAC's FACTYPE: mine, processing plant, oil or gas field, oil
# refinery, mine and plant, brine
FACTYPE = {"M": "mine", "P": "processing_plant", "OG": "field", "OR": "refinery", "MP": "mine",
           "B": "brine", "R": "refinery", "S": "smelter"}


def usgs_kind(ftype):
    """The asset type for a compilation's FeatureTyp ("Mines and Quarries",
    "Mineral Processing Plants", ...; the first of several). "Mineral" holds
    "mine", so processing plants are tested first."""
    f = clean(ftype).lower().split(";")[0].strip()
    if "processing" in f:
        return "processing_plant"
    if "refiner" in f:
        return "refinery"
    if "field" in f:
        return "field"
    if "mine" in f or "quarr" in f:
        return "mine"
    return f.replace(" ", "_") or "facility"


def minfac(root, points):
    import pyogrio
    d = os.path.join(root, "data/raw/material/usgs-gis-compilations")
    with open(os.path.join(d, "latin-america/MINFAC_LAC.csv"), encoding="latin-1") as f:
        for r in csv.DictReader(f):
            try:
                lon, lat = float(r["DDLONG"]), float(r["DDLAT"])
            except ValueError:
                continue
            points.append(dict(source="usgs-minfac", id=f"usgs-lac:{r['ROWID']}",
                               asset_type=FACTYPE.get(clean(r.get("FACTYPE")).upper(), "facility"),
                               commodity=clean(r.get("COMMODITY")).lower(), capacity=num(r.get("ANNCAP")),
                               unit=clean(r.get("UNITS")), start_year=None, status=clean(r.get("STATUS")).lower(),
                               known_from=2016, year_flag="snapshot", country=r.get("ISO_CC"),
                               country_kind="iso2", lon=lon, lat=lat, pub=2016))
    # the DsgAttr columns differ by region: (commodity, capacity, unit)
    for z, layer, pub, cols in [
            ("africa/Africa_GIS.gdb.zip", "AFR_Mineral_Facilities", 2021, ("DsgAttr02", "DsgAttr07", "DsgAttr08")),
            ("southwest-asia/SWAsia_GIS.gdb.zip", "SWA_Mineral_Facilties", 2022, ("DsgAttr02", "DsgAttr07", "DsgAttr08")),
            ("china/CHN_GIS.gdb.zip", "CHN_Mineral_Facilities", 2023, ("DsgAttr01", "DsgAttr06", "DsgAttr07")),
            ("indo-pacific/INDOPAC_GIS.gdb.zip", "INDOPAC_Mineral_Facilities", 2024, ("DsgAttr01", "DsgAttr06", "DsgAttr07"))]:
        meta, _, _, fields = pyogrio.raw.read("/vsizip/" + os.path.join(d, z), layer=layer, read_geometry=False)
        names = list(meta["fields"])
        col = {n: fields[i] for i, n in enumerate(names)}
        com, cap, unit = cols
        for k in range(len(fields[0])):
            lon, lat = col["Longitude"][k], col["Latitude"][k]
            if lon is None or lat is None or lon != lon or lat != lat:
                continue
            kind = usgs_kind(col["FeatureTyp"][k])
            points.append(dict(source="usgs-minfac", id=f"usgs:{col['FeatureUID'][k]}", asset_type=kind,
                               commodity=clean(col[com][k]).lower(), capacity=num(col[cap][k]),
                               unit=clean(col[unit][k]), start_year=None,
                               status=clean(col["LocOpStat"][k]).lower(), known_from=pub, year_flag="snapshot",
                               country=col["Country"][k], country_kind="name", lon=float(lon), lat=float(lat),
                               pub=pub))


def shp(zf, base, encoding="utf-8"):
    return shapefile.Reader(shp=io.BytesIO(zf.read(base + ".shp")), dbf=io.BytesIO(zf.read(base + ".dbf")),
                            shx=io.BytesIO(zf.read(base + ".shx")), encoding=encoding)


def petro(root, polys):
    zf = zipfile.ZipFile(os.path.join(root, "data/raw/material/prio-petrodata/PETRODATA_v12_Data.zip"))
    for base in ["Petrodata_Onshore_V1.2", "Petrodata_offshore_V1.2"]:
        r = shp(zf, base, "latin-1")
        for sr in r.iterShapeRecords():
            rec = sr.record.as_dict()
            g = shapely.geometry.shape(sr.shape.__geo_interface__)
            ps = polygons(g)
            if not ps:
                continue
            start = year_of(rec["PROD"]) if rec["PROD"] and rec["PROD"] > 0 else None
            disc = year_of(rec["DISC"]) if rec["DISC"] and rec["DISC"] > 0 else None
            kf, flag = known(start, disc, 2009)
            res = clean(rec.get("RESINFO")).lower()
            polys.append(dict(source="prio-petro", id=f"prio-petro:{rec['PRIMKEY']}", asset_type="field",
                              commodity="gas" if res == "gas" else "oil" if res == "oil" else "oil_gas",
                              capacity=None, unit="", start_year=start,
                              status="offshore" if "offshore" in base else "onshore",
                              known_from=kf, year_flag=flag, country=rec["FIPSCODE"], country_kind="fips",
                              polygons=ps))


def dia(root, points):
    zf = zipfile.ZipFile(os.path.join(root, "data/raw/material/prio-diadata/DIADATA_Data.zip"))
    recs = {}
    for base in ["DIADATA", "ERRATA"]:   # the errata replace records by key
        for rec in shp(zf, base, "cp850").records():
            d = rec.as_dict()
            recs[d["PRIMKEY"]] = d
    for k, d in recs.items():
        if d.get("LAT") is None or d.get("LONG") is None or not k:
            continue
        start = year_of(d.get("PROD_YEAR")) or (year_of(d["PROD"]) if d.get("PROD") else None)
        disc = year_of(d.get("DISC_YEAR")) or (year_of(d["DISC"]) if d.get("DISC") else None)
        kf, flag = known(start, disc, 2008)
        points.append(dict(source="prio-dia", id=f"prio-dia:{k}", asset_type="deposit", commodity="diamonds",
                           capacity=None, unit="", start_year=start,
                           status=clean(d.get("RESINFO")).lower(), known_from=kf, year_flag=flag,
                           country=d.get("FIPSCODE"), country_kind="fips", lon=d["LONG"], lat=d["LAT"]))


def maus(root, polys):
    db = sqlite3.connect(
        f"file:{os.path.join(root, 'data/raw/material/maus2022-mining-polygons-v2/global_mining_polygons_v2.gpkg')}?mode=ro",
        uri=True)
    for fid, iso, area, geom in db.execute("select fid, ISO3_CODE, AREA, geom from mining_polygons"):
        ps = polygons(gpkg_geom(geom))
        if ps:
            polys.append(dict(source="maus", id=f"maus:{fid}", asset_type="mining_area", commodity="",
                              capacity=num(area), unit="km2", start_year=None, status="",
                              known_from=2022, year_flag="snapshot", country=iso, country_kind="iso3",
                              polygons=ps))


# --- duplicates across and within sources ---------------------------------------

def commodity_key(c):
    c = clean(c).lower()
    for a, b in [("iron and steel", "steel"), ("steel", "steel"), ("diamond", "diamond"),
                 ("petroleum", "oil"), ("crude", "oil"), ("iron ore", "iron"), ("natural gas", "gas")]:
        if c.startswith(a):
            return b
    return c.split(" ")[0].split(",")[0] if c else ""


def dup_class(p):
    """The kind of asset two sources can both hold, or None."""
    t, c = p["asset_type"], commodity_key(p["commodity"])
    if t == "refinery" and c == "oil":
        return "refinery/oil"
    if t == "steel_plant" or (t == "processing_plant" and c == "steel"):
        return "plant/steel"
    if t == "cement_plant" or (t == "processing_plant" and c == "cement"):
        return "plant/cement"
    if t in ("mine", "deposit") and c:
        return "mine/" + c
    return None


def haversine_km(lon1, lat1, lon2, lat2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    h = (math.sin((p2 - p1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2)
    return 2 * EARTH_KM * math.asin(math.sqrt(min(h, 1.0)))


def coords(p):
    """(lon, lat), or None where a source has none ("Unknown")."""
    try:
        lon, lat = float(p["lon"]), float(p["lat"])
    except (TypeError, ValueError):
        return None
    return (lon, lat) if -180 <= lon <= 180 and -90 <= lat <= 90 else None


def mark_duplicates(points, polys):
    """Set dup_of on the points and polygons another asset already counts
    (see the module doc). Returns {(source, kept-source) n}."""
    counts = collections.Counter()
    rank = {s: i for i, s in enumerate(DUP_PRIORITY)}
    for p in points:
        p["dup_of"] = ""
    for p in polys:
        p["dup_of"] = ""
    # the same record twice in one source
    seen = {}
    points = [p for p in points if coords(p)]
    for p in points:
        lon, lat = coords(p)
        k = (p["source"], dup_class(p), commodity_key(p["commodity"]), round(lon, 5), round(lat, 5))
        if k[1] is None:
            continue
        if k in seen:
            p["dup_of"] = seen[k]["id"]
            counts[(p["source"], p["source"])] += 1
        else:
            seen[k] = p
    # across sources, by class: each point against the higher-priority
    # sources' points within DUP_KM
    by_class = collections.defaultdict(list)
    for p in points:
        c = dup_class(p)
        if c and not p["dup_of"] and p["source"] in rank:
            by_class[c].append(p)
    for c, ps in by_class.items():
        ps.sort(key=lambda p: rank[p["source"]])
        kept = []
        for src in DUP_PRIORITY:
            mine = [p for p in ps if p["source"] == src]
            if kept and mine:
                tree = shapely.STRtree(shapely.points([coords(q) for q in kept]))
                for p in mine:
                    lon, lat = coords(p)
                    deg = DUP_KM / 111.32 / max(math.cos(math.radians(lat)), 0.05)
                    best, bd = None, DUP_KM
                    for j in tree.query(shapely.Point(lon, lat).buffer(deg)):
                        q = kept[j]
                        if q["source"] == src:
                            continue
                        d = haversine_km(lon, lat, *coords(q))
                        if d <= bd:
                            best, bd = q, d
                    if best is not None:
                        p["dup_of"] = best["id"]
                        counts[(src, best["source"])] += 1
            kept += [p for p in mine if not p["dup_of"]]
    # fields: OGIM's polygons first
    ogim = [p for p in polys if p["source"] == "ogim" and p["asset_type"] == "field"]
    if ogim:
        def geom(p):
            return shapely.make_valid(shapely.MultiPolygon([shapely.Polygon(r[0], r[1:]) for r in p["polygons"]]))
        og = [geom(p) for p in ogim]
        tree = shapely.STRtree(og)
        for p in points:
            if p["asset_type"] == "field" and not p["dup_of"]:
                hits = tree.query(shapely.Point(coords(p)), predicate="within")
                if len(hits):
                    p["dup_of"] = ogim[hits[0]]["id"]
                    counts[(p["source"], "ogim")] += 1
        for p in polys:
            if p["source"] != "prio-petro":
                continue
            g = geom(p)
            if g.area <= 0:
                continue
            hits = tree.query(g, predicate="intersects")
            if not len(hits):
                continue
            inter = [(og[j].intersection(g).area, j) for j in hits]
            cover = shapely.union_all([og[j] for j in hits]).intersection(g).area / g.area
            if cover >= 0.5:
                p["dup_of"] = ogim[max(inter)[1]]["id"]
                counts[("prio-petro", "ogim")] += 1
    return counts


def end_of(p):
    """(end_year, end_flag, end_known) of an asset record: when it stopped,
    how that is known, and the year it is known (the source's publication,
    pub: a closure is known once reported, never earlier). MRDS past
    producers give their last production year (last-production); a closure
    read from a status word has no year (closed-unknown, idle-unknown,
    cancelled) and counts from the snapshot on. A last year after the
    publication is not one the source could report and is dropped.
    (None, "", None) for an asset with no end."""
    flag = END_STATUS.get(p.get("source"), {}).get(clean(p.get("status")).lower())
    if not flag:
        return None, "", None
    pub = p.get("pub")
    ly = p.get("last_year")
    if flag == "closed-unknown" and ly and (pub is None or ly <= pub):
        return ly, "last-production", pub
    return None, flag, pub


def set_ends(records):
    for p in records:
        p["end_year"], p["end_flag"], p["end_known"] = end_of(p)


def selftest():
    """Checks on the end-year rule: python -I scripts/cell-assets.py --selftest."""
    # MRDS: a past producer's last production year, known when MRDS published it
    assert end_of(dict(source="mrds", status="past producer", last_year=1950, pub=2011)) == (1950, "last-production", 2011)
    assert end_of(dict(source="mrds", status="past producer", last_year=None, pub=2011)) == (None, "closed-unknown", 2011)
    assert end_of(dict(source="mrds", status="producer", last_year=2005, pub=2011)) == (None, "", None), \
        "a producer's last year is the latest reported, not a closure"
    # status-only closures: no year, known from the snapshot, never backdated
    assert end_of(dict(source="ogim", status="retired", pub=2026)) == (None, "closed-unknown", 2026)
    assert end_of(dict(source="ogim", status="mothballed", pub=2026)) == (None, "idle-unknown", 2026)
    assert end_of(dict(source="ogim", status="cancelled", pub=2026)) == (None, "cancelled", 2026)
    assert end_of(dict(source="ogim", status="operating", pub=2026)) == (None, "", None)
    assert end_of(dict(source="sfi", status="shut down", pub=2021)) == (None, "closed-unknown", 2021)
    assert end_of(dict(source="sfi", status="operating", pub=2021)) == (None, "", None)
    assert end_of(dict(source="usgs-minfac", status="closed", pub=2023)) == (None, "closed-unknown", 2023)
    assert end_of(dict(source="usgs-minfac", status="c&m", pub=2021)) == (None, "idle-unknown", 2021)
    assert end_of(dict(source="usgs-minfac", status="assumed active", pub=2021)) == (None, "", None)
    assert end_of(dict(source="prio-dia", status="alluvial", pub=None)) == (None, "", None)
    # a last year after the source's publication is not a closure it could report
    assert end_of(dict(source="mrds", status="past producer", last_year=2015, pub=2011)) == (None, "closed-unknown", 2011)
    ps = [dict(source="sfi", status="shut down", pub=2021)]
    set_ends(ps)
    assert (ps[0]["end_year"], ps[0]["end_flag"], ps[0]["end_known"]) == (None, "closed-unknown", 2021)
    assert all(k in FIELDS for k in ("end_year", "end_flag", "end_known"))
    print("cell-assets selftest ok", file=sys.stderr)


def main():
    if sys.argv[1:] == ["--selftest"]:
        return selftest()
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    root = ap.parse_args().root
    points, polys = [], []
    for name, f in [("ogim", lambda: ogim(root, points, polys)), ("sfi", lambda: sfi(root, points)),
                    ("mrds", lambda: mrds(root, points)), ("usgs-minfac", lambda: minfac(root, points)),
                    ("prio-petro", lambda: petro(root, polys)), ("prio-dia", lambda: dia(root, points)),
                    ("maus", lambda: maus(root, polys))]:
        n0, p0 = len(points), len(polys)
        f()
        log(f"{name}: {len(points) - n0} points, {len(polys) - p0} polygons")
    set_ends(points)
    set_ends(polys)
    log("ends (source, flag):", dict(sorted(collections.Counter(
        (p["source"], p["end_flag"]) for p in points + polys if p["end_flag"]).items())))
    dups = mark_duplicates(points, polys)
    log("duplicates (source, of source):", dict(sorted(dups.items())))
    out = os.path.join(root, "data/derived/cells")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "asset-points.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(FIELDS)
        for p in points:
            w.writerow(["" if p[k] is None else p[k] for k in FIELDS])
    with open(os.path.join(out, "asset-polygons.edn"), "w", encoding="utf-8") as f:
        for p in polys:
            f.write(edn(p) + "\n")
    log(f"{len(points)} points, {len(polys)} polygons")


if __name__ == "__main__":
    main()
