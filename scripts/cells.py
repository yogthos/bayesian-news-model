"""The A5 cell crosswalk (docs/PRINCIPLES.md, principle 11): which country
and which first-level province each part of every resolution-6 cell lies in,
with area shares. Writes data/derived/cells/crosswalk.csv

    cell,iso,admin1,share,flag

  cell    A5 cell id at resolution 6 (about 8,300 km2), lowercase hex
  iso     ISO 3166 alpha-3 (XKX for Kosovo), as bnm.geo
  admin1  bnm.geo's province id ("UKR.12": GeoNames admin1), blank when the
          part is offshore or Natural Earth's province has no GeoNames match
  share   the part's share of the cell's area
  flag    land      inside Natural Earth's country
          disputed  land Natural Earth gives to no ISO country (Bir Tawil,
                    the Spratlys, ...): the nearest country's
          eez       inside the country's EEZ (Marine Regions v12)
          eez-shared  inside an overlapping claim or joint regime: the
                    claimant whose coast is nearest (the zone's own claimants
                    when Marine Regions names two or more, else any country),
                    not the first one named
          coast     at sea outside every EEZ but within 200 nautical miles
                    of the country's coast (the nearest)
High seas beyond that are not written, so a cell's shares sum to 1 unless it
reaches them; every cell with land sums to 1. data/derived/cells/crosswalk-fine.csv

    cell,part,iso,admin1,flag,res

says, for each cell the crosswalk splits, which part each of its samples
lies in: part is a compacted set of samples (an A5 cell at resolution 7 to
res standing for its descendants at res, the resolution the cell was sampled
at), flag "none" for the high seas and land of no country; the cell's
largest country part is left out. A point is located by the part holding
its sample, or else the largest part (bnm.cells/locate), to about 6 km at
resolution 10.

Method: each cell is sampled by the finer cells whose centres it holds
(A5's cell_to_subcells), which are equal in area, so a part's share is its
count of samples over the cell's. Cells wholly inside one province take
share 1 without sampling; cells on a border or coast are sampled at
resolution 10 (about 256 samples of 32 km2), the rest (open sea) at
resolution 9 (64), and cells touching a country or territory under 1,500
km2 finer, at up to resolution 16 (0.008 km2), enough for about eight
samples of it (the Vatican, 0.016 km2 in Natural Earth's outline against
its real 0.44, gets two). A5's children
are not inside their parent (a cell's own descendants cover about 64-70% of
it), so the samples are subcells, not id children.

Territories Natural Earth keeps inside their sovereign's admin-0 unit but
bnm.geo keys as countries (French Guiana, Guadeloupe, Martinique, Réunion,
Mayotte, Svalbard, the Caribbean Netherlands, Cocos, Christmas Island,
Tokelau, Bouvet) are read from their admin-1 unit, as Marine Regions keys
their EEZs. Where Natural Earth's admin-1 units are a level below GeoNames'
and say nothing that matches it (Ireland's counties, Sri Lanka's
districts), the province is looked up from the unit's county or region.

Borders are current (Natural Earth 5.1.2, its default de facto view: Crimea
is Russia's, Kashmir split on the line of control): reading a cell's
country for a past year is bnm.cells/country-in-year.

Inputs (untrusted data, read only): data/raw/geo/natural-earth/ (public
domain), data/raw/geo/marine-regions/eez.geojson (CC BY 4.0), and
resources/bnm/countries.edn, provinces.edn. Needs pya5 (A5 cells; checked
against dodeca on 20,000 random points: identical ids and centres) and
shapely 2.

    python -I scripts/cells.py [--root .]
"""

import argparse
import collections
import json
import math
import os
import re
import sys
import time
import unicodedata

import a5
import numpy as np
import shapely
import shapely.ops
from shapely.geometry import shape

RES = 6
FINE = 10   # border and coast cells
SEA = 9     # open-sea cells
NM200_KM = 370.4
EARTH_KM = 6371.0088

# Natural Earth admin-0 units without an ISO code, to the country bnm.geo
# keys them under. None: no country (falls to the nearest, flagged).
NE_UNITS = {
    "KOS": "XKX", "SOL": "SOM", "CYN": "CYP", "CNM": "CYP", "ESB": "GBR",
    "WSB": "GBR", "USG": "CUB", "KAS": "IND", "KAB": "KAZ", "IOA": "AUS",
    "CSI": "AUS", "ATC": "AUS", "CLP": "FRA", "BRI": "BRA", "ALD": "ALA",
    "PSX": "PSE", "SDS": "SSD", "SAH": "ESH",
    "SPI": None, "BRT": None, "PGA": None, "BJN": None, "SER": None, "SCR": None,
}

EEZ_ALIAS = {"KOS": "XKX", "XKS": "XKX"}

# Natural Earth admin-1 units (adm1_code, else gn_a1_code) that bnm.geo keys
# as countries of their own
TERRITORY = {
    "FR.973": "GUF", "FR.971": "GLP", "FR.972": "MTQ", "FR.974": "REU", "FR.976": "MYT",
    "NO.21": "SJM", "BVT+00?": "BVT", "NL.BO": "BES", "NL.SE": "BES", "NL.SB": "BES",
    "IOA-1928": "CCK", "IOA-2652": "CXR", "NZL-5472": "TKL",
}

# Natural Earth units a level below GeoNames' admin 1 whose names do not
# match it: Ireland's counties to its provinces, Sri Lanka's districts by
# their province (Natural Earth's region, in Sinhala)
IRL_PROVINCE = {
    **{c: "U" for c in ("Donegal", "Cavan", "Monaghan")},
    **{c: "C" for c in ("Galway", "Mayo", "Sligo", "Leitrim", "Roscommon")},
    **{c: "M" for c in ("Clare", "Cork", "Kerry", "Limerick", "North Tipperary", "South Tipperary",
                        "Tipperary", "Waterford")},
}
LKA_PROVINCE = {"basnahira": "36", "madhyama": "29", "dakunu": "34", "uturu": "38", "ngenahira": "37",
                "vayamba": "32", "uturumda": "30", "uva": "35", "sabaragamuva": "33"}
TINY_KM2 = 1500.0
MAX_RES = 16


def log(*a):
    print(*a, file=sys.stderr, flush=True)


# --- the registry (resources/bnm/*.edn) -------------------------------------------

STR = re.compile(r'"((?:[^"\\]|\\.)*)"')


def edn_section(text, key, next_key):
    start = text.index(key)
    end = text.index(next_key, start) if next_key else len(text)
    return text[start + len(key):end]


def countries(root):
    text = open(os.path.join(root, "resources/bnm/countries.edn"), encoding="utf-8").read()
    rows = re.findall(r':iso2 "([^"]*)",\s*:iso3 "([^"]*)",\s*:fips "([^"]*)"', text)
    iso2 = {a: b for a, b, _ in rows if a}
    return iso2, {b for _, b, _ in rows}, {b: f for _, b, f in rows if f}


def edn_maps(body):
    """{iso3 {k v}} from a two-level EDN map of strings."""
    out, depth, iso, pending, i = {}, 0, None, None, 0
    while i < len(body):
        c = body[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        elif c == '"':
            m = STR.match(body, i)
            s = m.group(1)
            i = m.end()
            if depth == 1:
                iso = s
                out.setdefault(iso, {})
            elif depth == 2:
                if pending is None:
                    pending = s
                else:
                    out[iso][pending] = s
                    pending = None
            continue
        i += 1
    return out


def provinces(root):
    """({iso3 {code name}}, {iso3 {match-key code}}) from provinces.edn."""
    text = open(os.path.join(root, "resources/bnm/provinces.edn"), encoding="utf-8").read()
    return (edn_maps(edn_section(text, ":names", "\n :keys")),
            edn_maps(edn_section(text, "\n :keys", None)))


PROVINCE_WORDS = set("""province provincia region oblast state governorate prefecture
    department departement county district territory municipality city
    capital national federal autonomous republic of the special
    administrative metropolitan krai kray voivodeship wilayah vilayet
    muhafazah muhafazat ostan division zone emirate canton land
    de del tinh changwat judetul kraj ken rayon wilaya
    obl regiao estado departamento shi sheng fu e""".split())


def province_key(s):
    """bnm.geo/province-key: lower case, runs of other than [a-z0-9] to one
    space, the words naming the kind of subdivision dropped."""
    k = re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()
    return " ".join(w for w in k.split(" ") if w not in PROVINCE_WORDS)


def ascii_fold(s):
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()


def match_province(p, iso, names, keys, fips_of):
    """bnm.geo's province id for a Natural Earth admin-1 feature, and how it
    was matched. Natural Earth's unit is often GeoNames' second level
    (gn_level 2: France's departments, Italy's provinces, England's
    counties), whose own codes must not be read as admin1 codes; those are
    matched by the name of the region or unit they lie in."""
    codes, ks = names.get(iso, {}), keys.get(iso, {})
    if iso == "IRL" and codes:
        return iso + "." + IRL_PROVINCE.get(p.get("name"), "L"), "county"
    if iso == "LKA" and codes:
        # ascii_fold drops the ae ligature: Næ̆gĕnahira -> ngenahira
        k = re.sub(r"[^a-z]", "", ascii_fold(p.get("region") or "").lower().split(" ")[0])
        if k in LKA_PROVINCE:
            return iso + "." + LKA_PROVINCE[k], "region"

    def by_name(*fields):
        for fld in fields:
            v = p.get(fld)
            if not v:
                continue
            short = re.sub(r"\s*\(.*\)\s*", " ", v).replace(" Is.", " Islands").strip()
            for s in (v, ascii_fold(v), short, ascii_fold(short)):
                k = province_key(s)
                if k and k in ks:
                    return ks[k]
        return None

    gn = p.get("gn_a1_code") or ""
    gcode = gn.split(".", 1)[1] if "." in gn else None
    fips = p.get("fips") or ""
    fcode = fips[2:] if len(fips) > 2 and fips[:2] == fips_of.get(iso) else None
    level = p.get("gn_level")
    if level == 2:
        c = by_name("region", "geonunit")
        if c:
            return iso + "." + c, "region-name"
        # a GeoNames code at this level is an admin2 code: only when it names
        # the same place as the admin1 code it spells (Finland's regions)
        if gcode in codes and province_key(codes[gcode]) in {
                province_key(p.get(fld) or "") for fld in ("name", "gn_name", "name_en")} - {""}:
            return iso + "." + gcode, "geonames"
    else:
        if gcode in codes:
            return iso + "." + gcode, "geonames"
        if fcode in codes:
            return iso + "." + fcode, "fips"
    c = by_name("name", "gn_name", "name_en", "woe_name", "name_alt")
    if c:
        return iso + "." + c, "name"
    # a city or borough (gn_level -1, -2): the region or unit it lies in
    c = by_name("region", "geonunit") if level in (-1, -2) else by_name("region")
    if c:
        return iso + "." + c, "region-name"
    return None, "none"


# --- geometry ------------------------------------------------------------------------

def load(path):
    log("reading", path)
    with open(path, encoding="utf-8") as f:
        return json.load(f)["features"]


def cell_polygon(c):
    """The cell's boundary as a planar polygon, or None across the
    antimeridian or a pole (those are sampled instead)."""
    b = a5.cell_to_boundary(c)
    lons = [p[0] for p in b]
    if max(lons) - min(lons) > 180 or any(abs(p[1]) > 89.5 for p in b):
        return None
    return shapely.Polygon(b)


def haversine_km(lon1, lat1, lon2, lat2):
    p1, p2 = np.radians(lat1), np.radians(lat2)
    dl, dp = np.radians(lon2 - lon1), p2 - p1
    h = np.sin(dp / 2) ** 2 + np.cos(p1) * np.cos(p2) * np.sin(dl / 2) ** 2
    return 2 * EARTH_KM * np.arcsin(np.sqrt(np.minimum(h, 1.0)))


def nearest(tree, geoms):
    """For each geometry, the index of its nearest tree geometry (the first
    on a tie: query_nearest returns every tied one)."""
    gi, ti = tree.query_nearest(geoms, return_distance=False)
    out = np.full(len(geoms), -1, dtype=np.int64)
    first = np.ones(len(gi), dtype=bool)
    first[1:] = gi[1:] != gi[:-1]
    out[gi[first]] = ti[first]
    return out


def contained(tree, pts):
    """For each point, the index of the first tree geometry containing it, or -1."""
    out = np.full(len(pts), -1, dtype=np.int64)
    if len(pts) == 0:
        return out
    geoms = shapely.points(pts)
    pi, gi = tree.query(geoms, predicate="within")
    # first hit per point
    order = np.argsort(pi, kind="stable")
    pi, gi = pi[order], gi[order]
    first = np.ones(len(pi), dtype=bool)
    first[1:] = pi[1:] != pi[:-1]
    out[pi[first]] = gi[first]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--limit", type=int, default=0, help="first N cells only (testing)")
    args = ap.parse_args()
    root = args.root
    t0 = time.time()

    iso2, isos, fips_of = countries(root)
    names, keys = provinces(root)

    # admin 0
    a0 = load(os.path.join(root, "data/raw/geo/natural-earth/ne_10m_admin_0_countries.geojson"))
    a0_geom, a0_iso, a0_unit = [], [], []
    unknown = collections.Counter()
    for f in a0:
        p = f["properties"]
        unit = p["ADM0_A3"]
        if unit in NE_UNITS:
            iso = NE_UNITS[unit]
        elif p["ISO_A3"] != "-99":
            iso = p["ISO_A3"]
        elif p["ISO_A3_EH"] != "-99":
            iso = p["ISO_A3_EH"]
        else:
            iso = None
        if iso is not None and iso not in isos:
            unknown[(unit, iso)] += 1
            iso = None
        a0_geom.append(shape(f["geometry"]))
        a0_iso.append(iso)
        a0_unit.append(unit)
    if unknown:
        log("admin-0 units with no bnm.geo country:", dict(unknown))
    a0_tree = shapely.STRtree(a0_geom)
    owned = [i for i, iso in enumerate(a0_iso) if iso]
    geoms_of = collections.defaultdict(list)
    for i in owned:
        geoms_of[a0_iso[i]].append(a0_geom[i])
    owned_tree = shapely.STRtree([a0_geom[i] for i in owned])
    for g in a0_geom:
        shapely.prepare(g)

    # admin 1: GeoNames code first (gn_a1_code "UA.12"), then FIPS ("UP12")
    a1 = load(os.path.join(root, "data/raw/geo/natural-earth/ne_10m_admin_1_states_provinces.geojson"))
    a1_geom, a1_id, a1_terr = [], [], []
    how = collections.Counter()
    for f in a1:
        p = f["properties"]
        terr = TERRITORY.get(p.get("adm1_code")) or TERRITORY.get(p.get("gn_a1_code"))
        iso = terr or iso2.get(p.get("iso_a2") or "")
        pid, kind = match_province(p, iso, names, keys, fips_of) if iso else (None, "none")
        how[kind] += 1
        a1_geom.append(shape(f["geometry"]))
        a1_id.append(pid)
        a1_terr.append(terr if terr in isos else None)
    for g in a1_geom:
        shapely.prepare(g)
    a1_tree = shapely.STRtree(a1_geom)
    matched = {x for x in a1_id if x}
    n_gn = sum(len(v) for v in names.values())
    log(f"admin-1: {len(a1)} Natural Earth provinces matched {dict(how)}; "
        f"{len(matched)} of {n_gn} GeoNames admin1 codes covered ({100 * len(matched) / n_gn:.1f}%)")

    # EEZ
    ez = load(os.path.join(root, "data/raw/geo/marine-regions/eez.geojson"))
    ez_geom, ez_iso, ez_flag, ez_claim = [], [], [], []
    for f in ez:
        p = f["properties"]
        ter, sov = p.get("iso_ter1"), p.get("iso_sov1")
        ter, sov = EEZ_ALIAS.get(ter, ter), EEZ_ALIAS.get(sov, sov)
        iso = ter if ter in isos else (sov if sov in isos else None)
        # an overlapping claim or joint regime: each claimant, by territory
        # where Marine Regions names one, else by sovereign
        claim = []
        for n in "123":
            t, v = p.get("iso_ter" + n), p.get("iso_sov" + n)
            t, v = EEZ_ALIAS.get(t, t), EEZ_ALIAS.get(v, v)
            c = t if t in isos else (v if v in isos else None)
            if c and c not in claim:
                claim.append(c)
        if iso is None and not claim:
            log("EEZ with no country:", p.get("geoname"))
            continue
        shared = p.get("pol_type") != "200NM"
        ez_geom.append(shape(f["geometry"]))
        ez_iso.append(iso or claim[0])
        ez_flag.append("eez-shared" if shared else "eez")
        ez_claim.append(claim if shared and len(claim) > 1 else None)
    for g in ez_geom:
        shapely.prepare(g)
    ez_tree = shapely.STRtree(ez_geom)
    log(f"loaded in {time.time() - t0:.0f}s")

    # every resolution-6 cell
    cells = [c for r0 in a5.get_res0_cells() for c in a5.cell_to_children(r0, RES)]
    if args.limit:
        cells = cells[:args.limit]
    log(len(cells), "cells")

    # cells wholly inside one province, or touching no land
    polys = [cell_polygon(c) for c in cells]
    ok = [i for i, p in enumerate(polys) if p is not None]
    pg = [polys[i] for i in ok]
    ci, gi = a1_tree.query(pg, predicate="within")
    inside = collections.defaultdict(list)
    for c, g in zip(ci, gi):
        inside[ok[c]].append(g)
    ci, _ = a0_tree.query(pg, predicate="intersects")
    touches = {ok[c] for c in ci}
    ci, _ = a1_tree.query(pg, predicate="intersects")
    touches |= {ok[c] for c in ci}

    def classify(pts):
        """(iso, admin1, flag) for each sample point."""
        land0 = contained(a0_tree, pts)
        prov1 = contained(a1_tree, pts)
        iso = np.array([a0_iso[g] if g >= 0 else None for g in land0], dtype=object)
        # land of no country (Antarctica) is dropped like the high seas
        flag = np.array(["" if g < 0 else ("nobody" if a0_unit[g] not in NE_UNITS and a0_iso[g] is None
                                           else "land") for g in land0], dtype=object)
        admin = np.array([a1_id[g] if g >= 0 else None for g in prov1], dtype=object)
        # admin-1 polygons a little outside Natural Earth's admin-0 coastline
        for k in np.where((land0 < 0) & (prov1 >= 0))[0]:
            pid = a1_id[prov1[k]]
            if pid:
                iso[k], flag[k] = pid[:3], "land"
        # territories inside their sovereign's admin-0 unit
        for k in np.where(prov1 >= 0)[0]:
            t = a1_terr[prov1[k]]
            if t and flag[k] in ("land", "nobody", ""):
                iso[k], flag[k] = t, "land"
        # land Natural Earth gives to no country (Bir Tawil, the Spratlys): the nearest
        dis = np.where((flag == "land") & (iso == None))[0]  # noqa: E711
        if len(dis):
            near = nearest(owned_tree, shapely.points(pts[dis]))
            for k, g in zip(dis, near):
                iso[k], flag[k] = a0_iso[owned[g]], "disputed"
        # at sea: the EEZ
        sea = np.where(flag == "")[0]
        if len(sea):
            e = contained(ez_tree, pts[sea])
            for k, g in zip(sea, e):
                if g >= 0:
                    iso[k], flag[k] = ez_iso[g], ez_flag[g]
                    if ez_flag[g] == "eez-shared":
                        # the claimant whose coast is nearest (a median line),
                        # or, for a zone naming one claimant, any country's
                        pt = shapely.Point(pts[k])
                        if ez_claim[g]:
                            iso[k] = min(ez_claim[g], key=lambda c: min(
                                (shapely.distance(pt, x) for x in geoms_of[c]), default=math.inf))
                        else:
                            iso[k] = a0_iso[owned[nearest(owned_tree, [pt])[0]]]
        # outside every EEZ: the nearest coast within 200 nm
        rest = np.where(flag == "")[0]
        if len(rest):
            geoms = shapely.points(pts[rest])
            near = nearest(owned_tree, geoms)
            for k, g, pt in zip(rest, near, geoms):
                poly = a0_geom[owned[g]]
                q = shapely.ops.nearest_points(pt, poly)[1]
                d = haversine_km(pts[k][0], pts[k][1], q.x, q.y)
                if d <= NM200_KM:
                    iso[k], flag[k] = a0_iso[owned[g]], "coast"
        out = []
        for k in range(len(pts)):
            if flag[k] in ("", "nobody"):
                out.append(("", "", "none"))
            else:
                # the province only where it is the sample's own country, on land
                a = admin[k] if flag[k] == "land" and admin[k] and admin[k][:3] == iso[k] else ""
                out.append((iso[k], a, flag[k]))
        return out

    # countries and territories too small for resolution 10: the cells they
    # touch are sampled finer, enough for about eight samples of them
    def km2(g):
        return g.area * 111.32 ** 2 * math.cos(math.radians(g.centroid.y))
    unit_geoms = collections.defaultdict(list)
    for i in owned:
        unit_geoms[a0_iso[i]].append(a0_geom[i])
    for g, t in zip(a1_geom, a1_terr):
        if t:
            unit_geoms[t].append(g)
    tiny_res = {}
    for iso, gs in unit_geoms.items():
        area = sum(km2(g) for g in gs)
        if area < TINY_KM2:
            r = FINE
            while r < MAX_RES and area / a5.cell_area(r) * 1e6 < 8:
                r += 1
            tiny_res[iso] = (shapely.union_all(gs), r)
    finer = {}
    for iso, (g, r) in tiny_res.items():
        # the cells its outline (cut to about 5 km) passes through: a unit
        # this small holds no whole cell
        parts = g.geoms if hasattr(g, "geoms") else [g]
        for part in parts:
            ring = getattr(part, "exterior", part)
            for x, y, *_ in shapely.segmentize(ring, 0.05).coords:
                c = a5.lonlat_to_cell((x, y), RES)
                finer[c] = max(finer.get(c, FINE), r)
    log(f"{len(tiny_res)} small units, {len(finer)} cells sampled finer:",
        {iso: r for iso, (_, r) in sorted(tiny_res.items())})

    rows = []
    stats = collections.Counter()
    kind = {}
    for i, c in enumerate(cells):
        if c in finer:
            kind[c] = finer[c]
            stats["finer"] += 1
            continue
        if len(inside.get(i, ())) == 1 and a1_id[inside[i][0]] is not None:
            pid = a1_id[inside[i][0]]
            # the province's country, as admin 0 says at the centre
            lon, lat = a5.cell_to_lonlat(c)
            g = contained(a0_tree, np.array([[lon, lat]]))[0]
            if g >= 0 and a0_iso[g] == pid[:3]:
                rows.append((a5.u64_to_hex(c), pid[:3], pid, 1.0, "land"))
                stats["inside"] += 1
                continue
        kind[c] = FINE if (polys[i] is None or i in touches) else SEA
        stats["fine" if kind[c] == FINE else "sea"] += 1
    log(dict(stats))

    # A5's children are not inside their parent (a cell's descendants cover
    # about 64-70% of it, the rest lying in its neighbours), so a cell is
    # sampled by its subcells, the finer cells whose centres it holds:
    # equal-area cells, about 256 at resolution 10 (64 at 9).
    keyix = {}
    s_owner, s_kid, s_key = [], [], []
    batch_pts, batch_owner, batch_kid = [], [], []

    def flush():
        if not batch_pts:
            return
        for o, k, key in zip(batch_owner, batch_kid, classify(np.array(batch_pts))):
            s_owner.append(o)
            s_kid.append(k)
            s_key.append(keyix.setdefault(key, len(keyix)))
        batch_pts.clear()
        batch_owner.clear()
        batch_kid.clear()

    for n, (o, res) in enumerate(sorted(kind.items())):
        for k in a5.uncompact(a5.cell_to_subcells(o, res)):
            if not a5.is_compaction_marker(k):
                batch_pts.append(a5.cell_to_lonlat(k))
                batch_owner.append(o)
                batch_kid.append(k)
        if len(batch_pts) > 400_000:
            flush()
            log(f"{n + 1} of {len(kind)} cells, {len(s_owner)} samples, {time.time() - t0:.0f}s")
    flush()

    keys = {v: k for k, v in keyix.items()}
    by_owner = collections.defaultdict(lambda: collections.defaultdict(list))
    for o, k, key in zip(s_owner, s_kid, s_key):
        by_owner[o][key].append(k)
    fine = []
    for o, parts in by_owner.items():
        h = a5.u64_to_hex(o)
        n = sum(len(v) for v in parts.values())
        named = [key for key in parts if keys[key][2] != "none"]
        main = max(named, key=lambda key: len(parts[key])) if named else None
        for key, kids in parts.items():
            i, a, fl = keys[key]
            if fl != "none":
                rows.append((h, i, a, len(kids) / n, fl))
            # where a cell is split, which part each sample is in, compacted,
            # but for its largest part
            if len(parts) > 1 and key != main:
                for x in a5.compact(kids):
                    if not a5.is_compaction_marker(x):
                        fine.append((h, a5.u64_to_hex(x), i, a, fl, kind[o]))
    stats["samples"] = len(s_owner)
    log(dict(stats))

    rows.sort(key=lambda r: (r[0], r[1], r[2], r[4]))
    out = os.path.join(root, "data/derived/cells")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "crosswalk.csv"), "w", encoding="utf-8") as f:
        f.write("cell,iso,admin1,share,flag\n")
        for c, i, a, s, fl in rows:
            f.write(f"{c},{i},{a},{s:.6g},{fl}\n")

    fine.sort()
    with open(os.path.join(out, "crosswalk-fine.csv"), "w", encoding="utf-8") as f:
        f.write("cell,part,iso,admin1,flag,res\n")
        for c, x, i, a, fl, r in fine:
            f.write(f"{c},{x},{i},{a},{fl},{r}\n")

    # coverage
    in_xw = {r[2] for r in rows if r[2]}
    land_cells = {r[0] for r in rows if r[4] in ("land", "disputed")}
    any_cells = {r[0] for r in rows}
    flags = collections.Counter(r[4] for r in rows)
    land_rows = [r for r in rows if r[4] == "land"]
    with_a1 = sum(r[3] for r in land_rows if r[2])
    tot_land = sum(r[3] for r in land_rows)
    report = {
        "cells": len(cells), "cells_written": len(any_cells), "land_cells": len(land_cells),
        "rows": len(rows), "rows_by_flag": dict(flags), "fine_rows": len(fine),
        "ne_admin1": len(a1), "ne_admin1_matched_by": dict(how),
        "geonames_admin1": n_gn, "geonames_admin1_matched": len(matched),
        "geonames_admin1_in_crosswalk": len(in_xw),
        "land_area_with_admin1": with_a1 / tot_land if tot_land else 0,
        "countries": len({r[1] for r in rows}),
        "unmatched_countries_with_provinces": sorted(
            iso for iso in names if names[iso] and not any(m.startswith(iso + ".") for m in matched)),
        "seconds": round(time.time() - t0),
    }
    with open(os.path.join(out, "crosswalk-coverage.json"), "w") as f:
        json.dump(report, f, indent=1)
    log(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
