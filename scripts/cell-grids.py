"""Gridded economic data on A5 cells (docs/ECONOMIC-CHARACTER.md 8c step 1;
bead bnm-3ka.6): zonal sums per resolution-6 cell and country part, each
block given to the part it lies in. Writes
data/derived/cells/cell-grids.csv

    cell,iso,year,variable,value,known

  cell      A5 cell at resolution 6, lowercase hex
  iso       the country part (bnm.cells' crosswalk)
  year      the year the value describes
  variable  one of VARIABLES (all sums over the part)
  value     the part's sum
  known     the year the value is known (year + the grid's publication lag)

and data/derived/cells/cell-grids-coverage.json: per variable and year the
grid's total, the total written and their ratio (coverage).

Method. Each grid is summed into blocks (5 arcmin for the 30 arcsec
grids, 5 km for GHSL's 1 km Mollweide grid, EDGAR's 0.1 degree pixels as
they are), each block counted whole in the cell holding its centre
(pya5's lonlat_to_cell; GHSL's centres from the inverse Mollweide
projection on the sphere of ESRI:54009's semi-major axis). A block
straddling a cell edge goes to one side: at 5 arcmin (9 km) against cells
of about 91 km, a small error that conserves the total. In a cell the
crosswalk splits (a border or a coast), a block goes to the country part
its centre lies in, read from the fine crosswalk as bnm.cells/locate reads
it (FineParts: the part of the block's sample, about 6 km at resolution
10), so a border cell's GDP follows its pixels: Kummu calibrates GDP to
each country's (and province's) total, and an area split would hand one
side's GDP to the other. Only a block whose part is unknown (no country,
or one the cell does not reach) is split over the cell's parts by area
(the crosswalk's cell,iso,share renormalised to 1: for land variables
the land and disputed parts only, falling back to all parts in a cell
with none; for EDGAR every part, offshore platforms and terminals lying in
EEZs). Values in blocks on cells the crosswalk has no row for (the high
seas) are dropped and show in the coverage.

Pixel area. Kummu's GDP and population and EDGAR's tonnes are amounts per
pixel, and GHSL is on an equal-area grid: summed as they are. Night
lights are a level per pixel (a digital number) on a lon/lat grid, whose
pixels shrink with cos(latitude): an equal-area cell at 60 degrees holds
twice the pixels of one on the equator, so each row is weighted by
cos(latitude) before summing (ntl/dn is in equator-pixel units).

Variables, their grids and publication lags (known = year + lag):
  kummu/gdp        Kummu et al. 2025 (Zenodo 18429133, 2026 update)
                   rast_gdpTot_1990_2024_5arcmin, total GDP PPP, constant
                   2017 int. $, 1990-2024. Lag 2: the 2024 values came out
                   in March 2026, the 2022 ones in the 2025 paper.
  kummu/pop        the population implied by the same release: gdpTot over
                   rast_adm2_gdp_perCapita per pixel (so kummu/gdp over
                   kummu/pop is the population-weighted GDP per capita).
                   Lag 2.
  ntl/dn           Li et al. harmonised night lights (figshare 9828827
                   v10), the sum of digital numbers, 1992-2024 (DMSP to
                   2013, simulated VIIRS from 2014: a calibrated break
                   that leaves a 17% fall in the world's lights from 2013
                   to 2014 and zeroes dim pixels; written raw here,
                   spliced by bnm.cell-profiles/splice-lights. The
                   DN_NTL_2013_simVIIRS overlap file is from the 2020
                   calibration, not v10's (its world total is 1.49 times
                   DMSP 2013's and 1.78 times v10's 2014), so it cannot
                   splice the series). Lag 1: annual composites come out
                   the next year.
  ghsl/built-km2   GHSL R2023A built-up surface, km2, epochs 1975-2020
  ghsl/nres-km2    GHSL R2023A non-residential built-up surface, km2
  ghsl/pop         GHSL R2023A residential population
                   Lag 3 for the three (an epoch's imagery is read into a
                   release about three years on). The 2025 and 2030
                   epochs are projections and are left out. The R2023A
                   vintage back-casts 1975-2015 from 2018 imagery, so early
                   epochs carry later information: a vintage caveat, as
                   for EXIOBASE.
  edgar/<s>-co2    EDGAR 2026 GHG CO2 by sector, tonnes, 1990-2025, for the
                   sectors in EDGAR_SECTORS: ENE (power), IND (combustion
                   for manufacturing), IRO (iron and steel), NFE
                   (non-ferrous metals), NMM (non-metallic minerals,
                   cement), CHE (chemicals), REF_TRF (refineries and
                   transformation). Lag 1: each release ends the year
                   before. The industrial sectors are located partly from
                   plant inventories, so they partly re-read our assets.

Inputs (untrusted data, read only): data/raw/geo/{kummu-gdp-2026,
li-harmonised-ntl, ghsl-r2023a-1km, edgar-2026-co2-sectors}/ (CC BY 4.0
each, SOURCE.txt) and data/derived/cells/crosswalk.csv. Needs pya5,
rasterio, netCDF4, numpy.

    python -I scripts/cell-grids.py [--root .] [--only kummu,ntl,ghsl,edgar]
    python -I scripts/cell-grids.py --selftest
"""

import argparse
import collections
import csv
import json
import math
import os
import sys
import time
import zipfile

import numpy as np

RES = 6
LAND_FLAGS = ("land", "disputed")
EDGAR_SECTORS = ["ENE", "IND", "IRO", "NFE", "NMM", "CHE", "REF_TRF"]
GHSL_EPOCHS = list(range(1975, 2021, 5))
LAG = {"kummu": 2, "ntl": 1, "ghsl": 3, "edgar": 1}
EDGAR_YEARS = range(1990, 2026)
MOLL_R = 6378137.0


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, file=sys.stderr, flush=True)


# --- the crosswalk -------------------------------------------------------------------

def read_crosswalk(path):
    """{cell: [(iso, share, flag)]}."""
    out = collections.defaultdict(list)
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            s = float(r["share"])
            if s > 0:
                out[r["cell"]].append((r["iso"], s, r["flag"]))
    return dict(out)


def split_shares(parts, land):
    """{iso: share} of a cell's parts, summing to 1: the land parts for a
    land variable (all parts when it has none), every part otherwise."""
    use = [p for p in parts if p[2] in LAND_FLAGS] if land else parts
    if not use:
        use = parts
    tot = sum(p[1] for p in use)
    out = collections.defaultdict(float)
    for iso, s, _ in use:
        out[iso] += s / tot
    return dict(out)


# --- cells of points ----------------------------------------------------------------

class FineParts:
    """Which country part of a split cell a point lies in, from the fine
    crosswalk (data/derived/cells/crosswalk-fine.csv, scripts/cells.py), as
    bnm.cells/locate reads it: the point's sample (its cell at the
    resolution its owner was sampled at, the owner the resolution-6 cell
    holding the sample's centre) lies in the part listed for it or for an
    id ancestor of it (parts are compacted), else in the owner's largest
    part (left out of the table). `part` gives the iso, or None when the
    cell is not split, the sample is in no country ("none") or in a country
    the point's cell does not reach: the caller then splits by area."""

    def __init__(self, fine, res, xw, cell_at=None, owner_of=None, parent=None):
        self.fine = fine          # {part id: (owner hex, iso, flag)}
        self.res = res            # {owner hex: sampling resolution}
        self.xw = xw              # {cell hex: [(iso, share, flag)]}
        if cell_at is None:
            import a5
            cell_at = lambda lon, lat, r: a5.lonlat_to_cell((lon, lat), r)
            owner_of = lambda f: a5.u64_to_hex(a5.lonlat_to_cell(a5.cell_to_lonlat(f), RES))
            parent = a5.cell_to_parent
        self.cell_at, self.owner_of, self.parent = cell_at, owner_of, parent

    def split(self, h):
        parts = self.xw.get(h)
        return bool(parts) and not (len(parts) == 1 and parts[0][1] >= 0.9999)

    def part(self, lon, lat, h):
        if not self.split(h):
            return None
        isos = {p[0] for p in self.xw[h]}
        s = self.res.get(h, 10)
        f = self.cell_at(lon, lat, s)
        o = self.owner_of(f)
        s = self.res.get(o, s)
        if s != self.res.get(h, 10):
            f = self.cell_at(lon, lat, s)
        found = None
        for r in range(s, 6, -1):
            p = self.fine.get(f if r == s else self.parent(f, r))
            if p and p[0] == o:
                found = p
                break
        if found is None:
            rows = self.xw.get(o)
            iso = max(rows, key=lambda p: p[1])[0] if rows else None
        elif found[2] == "none":
            return None
        else:
            iso = found[1]
        return iso if iso in isos else None


def read_fine(path, xw):
    """FineParts from the fine crosswalk at `path`."""
    import a5
    fine, res = {}, {}
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            fine[a5.hex_to_u64(r["part"])] = (r["cell"], r["iso"], r["flag"])
            res[r["cell"]] = int(r["res"] or 10)
    return FineParts(fine, res, xw)


class Cells:
    """Dense indices for the (cell, part) a point lies in: index -> (hex,
    iso), iso None when the point's part is not known (the cell is split
    over its parts by area later)."""

    def __init__(self, locate=None, part_of=None):
        self.hexes = []
        self.ix = {}
        if locate is None:
            import a5
            locate = lambda lon, lat: a5.u64_to_hex(a5.lonlat_to_cell((lon, lat), RES))
        self.locate = locate
        self.part_of = part_of

    def index(self, key):
        i = self.ix.get(key)
        if i is None:
            i = self.ix[key] = len(self.hexes)
            self.hexes.append(key)
        return i

    def of_points(self, lons, lats):
        out = np.full(len(lons), -1, dtype=np.int64)
        for k, (x, y) in enumerate(zip(lons.tolist(), lats.tolist())):
            if x == x and y == y:
                x, y = ((x + 180.0) % 360.0) - 180.0, max(-90.0, min(90.0, y))
                h = self.locate(x, y)
                out[k] = self.index((h, self.part_of(x, y, h) if self.part_of else None))
        return out


class Geometry:
    """A grid of blocks whose centres are (lon(i), lat(i)) for flat block
    index i; the cell of each block computed when first needed."""

    def __init__(self, cells, nblocks, centre):
        self.cells = cells
        self.cix = np.full(nblocks, -2, dtype=np.int64)
        self.centre = centre

    def cell_index(self, flat):
        need = flat[self.cix[flat] == -2]
        if len(need):
            lons, lats = self.centre(need)
            self.cix[need] = self.cells.of_points(lons, lats)
        return self.cix[flat]


def zonal(geom, flat, values, acc):
    """Add `values` at blocks `flat` into acc {cell index: sum}; returns the
    total of the values and the total with no cell (high seas or no
    centre)."""
    ok = np.isfinite(values) & (values != 0)
    flat, values = flat[ok], values[ok].astype(np.float64)
    ci = geom.cell_index(flat)
    lost = float(values[ci < 0].sum())
    good = ci >= 0
    s = np.bincount(ci[good], weights=values[good])
    for i in np.nonzero(s)[0]:
        acc[int(i)] = acc.get(int(i), 0.0) + float(s[i])
    return float(values.sum()), lost


def block_sum(a, f):
    """Sum of a 2-d array over f x f blocks (edges padded with zeros)."""
    h, w = a.shape
    H, W = -(-h // f) * f, -(-w // f) * f
    if (H, W) != (h, w):
        b = np.zeros((H, W), dtype=np.float64)
        b[:h, :w] = np.nan_to_num(a, nan=0.0)
        a = b
    else:
        a = np.nan_to_num(a.astype(np.float64), nan=0.0)
    return a.reshape(H // f, f, W // f, f).sum(axis=(1, 3))


def lonlat_geometry(cells, x0, y0, dx, dy, ncol, nrow, f):
    """Blocks of f x f pixels of a lon/lat grid with corner (x0, y0)."""
    bw = -(-ncol // f)
    bh = -(-nrow // f)

    def centre(flat):
        r, c = np.divmod(flat, bw)
        return x0 + (c * f + f / 2.0) * dx, y0 + (r * f + f / 2.0) * dy
    return Geometry(cells, bw * bh, centre), bw, bh


def inverse_mollweide(x, y, r=MOLL_R):
    """lon, lat in degrees of Mollweide x, y (metres) on a sphere of radius r;
    nan outside the ellipse."""
    s2 = math.sqrt(2.0)
    with np.errstate(invalid="ignore", divide="ignore"):
        t = np.arcsin(y / (r * s2))
        lat = np.arcsin((2 * t + np.sin(2 * t)) / math.pi)
        lon = np.degrees(math.pi * x / (2 * r * s2 * np.cos(t)))
    lat = np.degrees(lat)
    bad = ~np.isfinite(lat) | ~np.isfinite(lon) | (np.abs(lon) > 180.0)
    lon = np.where(bad, np.nan, lon)
    lat = np.where(bad, np.nan, lat)
    return lon, lat


def mollweide_geometry(cells, x0, y0, dx, dy, ncol, nrow, f):
    bw = -(-ncol // f)
    bh = -(-nrow // f)

    def centre(flat):
        r, c = np.divmod(flat, bw)
        return inverse_mollweide(x0 + (c * f + f / 2.0) * dx, y0 + (r * f + f / 2.0) * dy)
    return Geometry(cells, bw * bh, centre), bw, bh


# --- the grids ----------------------------------------------------------------------

class Out:
    def __init__(self):
        self.sums = {}       # (variable, year) -> {cell index: sum}
        self.totals = {}     # (variable, year) -> (grid total, lost)

    def add(self, var, year, acc, tot, lost):
        a = self.sums.setdefault((var, year), {})
        for k, v in acc.items():
            a[k] = a.get(k, 0.0) + v
        t0, l0 = self.totals.get((var, year), (0.0, 0.0))
        self.totals[(var, year)] = (t0 + tot, l0 + lost)


def lat_weights(y0, dy, r0, n):
    """cos(latitude) of rows r0 .. r0+n-1 of a lon/lat grid whose top edge
    is y0 and row height dy (negative going south): a pixel's area against
    one on the equator."""
    return np.cos(np.radians(y0 + (np.arange(r0, r0 + n) + 0.5) * dy))


# grids whose pixels hold a per-pixel level, not an amount: on a lon/lat
# grid a pixel's area is cos(latitude) of an equator pixel's, so an equal-
# area cell at 60 degrees holds twice the pixels it would at the equator
# and an unweighted sum of their digital numbers would double its lights.
# Kummu's GDP and population are amounts per pixel (summed as they are),
# GHSL is on an equal-area grid and EDGAR gives tonnes per pixel.
ROW_WEIGHT = {"ntl": True}


def raster_strips(path, band, f, geom_of, out, var, year, transform=None, rows=None, scale=1.0, nodata=None,
                  row_weight=False):
    """Read a raster band in strips of f rows multiple, sum blocks, add to
    out; with row_weight each row times cos(latitude) first (lat_weights)."""
    import rasterio
    from rasterio.windows import Window
    with rasterio.open(path) as d:
        nrow, ncol = d.height, d.width
        geom, bw, bh = geom_of(d)
        nd = d.nodata if nodata is None else nodata
        strip = f * max(1, 2400 // f)
        acc, tot, lost = {}, 0.0, 0.0
        for r0 in range(0, nrow, strip):
            h = min(strip, nrow - r0)
            a = d.read(band, window=Window(0, r0, ncol, h)).astype(np.float64)
            if nd is not None:
                a[a == nd] = np.nan
            a[a < 0] = np.nan
            if row_weight:
                a *= lat_weights(d.transform.f, d.transform.e, r0, h)[:, None]
            b = block_sum(a, f) * scale
            br0 = r0 // f
            flat = (np.arange(b.shape[0])[:, None] + br0) * bw + np.arange(b.shape[1])[None, :]
            t, l = zonal(geom, flat.ravel(), b.ravel(), acc)
            tot += t
            lost += l
        out.add(var, year, acc, tot, lost)


def kummu(root, cells, out):
    import rasterio
    d = os.path.join(root, "data/raw/geo/kummu-gdp-2026")
    tot_p = os.path.join(d, "rast_gdpTot_1990_2024_5arcmin.tif")
    pc_p = os.path.join(d, "rast_adm2_gdp_perCapita_1990_2024.tif")
    with rasterio.open(tot_p) as t, rasterio.open(pc_p) as p:
        assert (t.width, t.height, t.transform) == (p.width, p.height, p.transform), "Kummu grids differ"
        tr = t.transform
        geom, bw, _ = lonlat_geometry(cells, tr.c, tr.f, tr.a, tr.e, t.width, t.height, 1)
        flat = np.arange(t.width * t.height)
        for b, desc in enumerate(t.descriptions, start=1):
            year = int(desc.rsplit("_", 1)[1])
            g = t.read(b).astype(np.float64).ravel()
            pc = p.read(b).astype(np.float64).ravel()
            g[~np.isfinite(g) | (g < 0)] = np.nan
            with np.errstate(invalid="ignore", divide="ignore"):
                pop = np.where(np.isfinite(pc) & (pc > 0) & np.isfinite(g), g / pc, np.nan)
            for var, v in (("kummu/gdp", g), ("kummu/pop", pop)):
                acc = {}
                tt, ll = zonal(geom, flat, v, acc)
                out.add(var, year, acc, tt, ll)
            log("kummu", year)


def ntl(root, cells, out):
    d = os.path.join(root, "data/raw/geo/li-harmonised-ntl")
    geoms = {}

    def geom_of(ds):
        tr = ds.transform
        k = (tr, ds.width, ds.height)
        if k not in geoms:
            geoms[k] = lonlat_geometry(cells, tr.c, tr.f, tr.a, tr.e, ds.width, ds.height, 10)
        return geoms[k]
    for y in range(1992, 2025):
        name = f"Harmonized_DN_NTL_{y}_{'calDMSP' if y <= 2013 else 'simVIIRS'}.tif"
        raster_strips(os.path.join(d, name), 1, 10, geom_of, out, "ntl/dn", y, row_weight=ROW_WEIGHT["ntl"])
        log("ntl", y)


def ghsl(root, cells, out):
    d = os.path.join(root, "data/raw/geo/ghsl-r2023a-1km")
    geoms = {}

    def geom_of(ds):
        tr = ds.transform
        k = (tr, ds.width, ds.height)
        if k not in geoms:
            geoms[k] = mollweide_geometry(cells, tr.c, tr.f, tr.a, tr.e, ds.width, ds.height, 5)
        return geoms[k]
    for prod, var, scale in [("GHS_BUILT_S", "ghsl/built-km2", 1e-6), ("GHS_BUILT_S_NRES", "ghsl/nres-km2", 1e-6),
                             ("GHS_POP", "ghsl/pop", 1.0)]:
        for e in GHSL_EPOCHS:
            z = os.path.join(d, f"{prod}_E{e}_GLOBE_R2023A_54009_1000_V1_0.zip")
            tif = [n for n in zipfile.ZipFile(z).namelist() if n.endswith(".tif")][0]
            raster_strips(f"/vsizip/{z}/{tif}", 1, 5, geom_of, out, var, e, scale=scale)
            log("ghsl", var, e)


def edgar(root, cells, out):
    import netCDF4
    d = os.path.join(root, "data/raw/geo/edgar-2026-co2-sectors")
    geom = None
    for s in EDGAR_SECTORS:
        zf = zipfile.ZipFile(os.path.join(d, f"{s}_emi_nc.zip"))
        names = {n for n in zf.namelist()}
        for y in EDGAR_YEARS:
            n = f"EDGAR_2026_GHG_CO2_{y}_{s}_emi.nc"
            if n not in names:
                log("edgar: missing", n)
                continue
            ds = netCDF4.Dataset("mem", memory=zf.read(n))
            lat = np.asarray(ds["lat"][:], dtype=np.float64)
            lon = np.asarray(ds["lon"][:], dtype=np.float64)
            a = np.asarray(ds["emissions"][:].filled(np.nan), dtype=np.float64)
            ds.close()
            if geom is None:
                nlon = len(lon)

                def centre(flat, lat=lat, lon=lon, nlon=nlon):
                    r, c = np.divmod(flat, nlon)
                    return lon[c], lat[r]
                geom = Geometry(cells, len(lat) * len(lon), centre)
                shape = (len(lat), len(lon))
            assert a.shape == shape
            acc = {}
            t, l = zonal(geom, np.arange(a.size), a.ravel(), acc)
            out.add(f"edgar/{s.lower().replace('_', '-')}-co2", y, acc, t, l)
        log("edgar", s)


# --- writing ------------------------------------------------------------------------

def is_land(var):
    return not var.startswith("edgar/")


def lag(var):
    return LAG[var.split("/")[0]]


def cell_rows(out, cells, xw):
    """(cell, iso, year, variable, value, known) rows, and the coverage."""
    rows, cov = [], {}
    for (var, year), acc in sorted(out.sums.items()):
        tot, lost = out.totals[(var, year)]
        written, nocw = 0.0, 0.0
        by = collections.defaultdict(float)
        for ci, v in acc.items():
            h, part = cells.hexes[ci]
            parts = xw.get(h)
            if not parts:
                nocw += v
                continue
            if part is not None and any(p[0] == part for p in parts):
                by[(h, part)] += v
                continue
            for iso, s in split_shares(parts, is_land(var)).items():
                by[(h, iso)] += v * s
        for (h, iso), x in sorted(by.items()):
            if x != 0:
                rows.append((h, iso, year, var, x, year + lag(var)))
                written += x
        cov[f"{var}@{year}"] = {"grid": tot, "written": written, "no_centre": lost, "no_crosswalk": nocw,
                                "coverage": written / tot if tot else None}
    return rows, cov


def write(root, rows, cov):
    d = os.path.join(root, "data/derived/cells")
    with open(os.path.join(d, "cell-grids.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["cell", "iso", "year", "variable", "value", "known"])
        for h, iso, y, var, v, k in rows:
            w.writerow([h, iso, y, var, f"{v:.6g}", k])
    with open(os.path.join(d, "cell-grids-coverage.json"), "w") as f:
        json.dump(cov, f, indent=1, sort_keys=True)


def selftest():
    """Checks on toy grids: python -I scripts/cell-grids.py --selftest."""
    # cells: lon < 0 is cell "a", else "b"
    cells = Cells(locate=lambda lon, lat: "a" if lon < 0 else "b")
    # a 4 x 4 lon/lat grid, -2..2 by -2..2, in 2 x 2 blocks
    g, bw, bh = lonlat_geometry(cells, -2.0, 2.0, 1.0, -1.0, 4, 4, 2)
    a = np.arange(16, dtype=np.float64).reshape(4, 4)
    b = block_sum(a, 2)
    assert b.sum() == a.sum(), "blocks conserve the total"
    out = Out()
    acc = {}
    flat = (np.arange(bh)[:, None] * bw + np.arange(bw)[None, :]).ravel()
    t, lost = zonal(g, flat, b.ravel(), acc)
    out.add("ntl/dn", 2000, acc, t, lost)
    assert t == a.sum() and lost == 0.0
    assert abs(sum(acc.values()) - a.sum()) < 1e-9, "zonal sums conserve the total"
    left = a[:, :2].sum()
    assert acc[cells.ix[("a", None)]] == left and acc[cells.ix[("b", None)]] == a.sum() - left
    # odd sizes pad, still conserving
    assert block_sum(np.ones((5, 3)), 2).sum() == 15
    # the split: a border cell by its land shares; a sea-only cell by all
    xw = {"a": [("DEU", 0.5, "land"), ("POL", 0.3, "land"), ("DEU", 0.2, "eez")],
          "b": [("NOR", 0.6, "eez")]}
    sl = split_shares(xw["a"], True)
    assert abs(sl["DEU"] - 0.625) < 1e-12 and abs(sl["POL"] - 0.375) < 1e-12
    sa = split_shares(xw["a"], False)
    assert abs(sa["DEU"] - 0.7) < 1e-12 and abs(sum(sa.values()) - 1) < 1e-12
    assert split_shares(xw["b"], True) == {"NOR": 1.0}, "no land part: all parts"
    rows, cov = cell_rows(out, cells, xw)
    assert abs(sum(r[4] for r in rows) - a.sum()) < 1e-9, "the split conserves the total"
    assert abs(cov["ntl/dn@2000"]["coverage"] - 1.0) < 1e-12
    assert all(r[5] == 2001 for r in rows), "night lights known a year on"
    # a cell the crosswalk lacks is dropped and counted
    rows2, cov2 = cell_rows(out, cells, {"a": xw["a"]})
    assert abs(cov2["ntl/dn@2000"]["no_crosswalk"] - (a.sum() - left)) < 1e-9
    assert cov2["ntl/dn@2000"]["coverage"] < 1.0
    # known = year + lag, per grid
    assert [lag(v) for v in ("kummu/gdp", "ntl/dn", "ghsl/pop", "edgar/iro-co2")] == [2, 1, 3, 1]
    assert max(GHSL_EPOCHS) == 2020, "the projected epochs are left out"
    # nan and zero values are skipped, not summed
    acc3 = {}
    t3, _ = zonal(g, np.array([0, 1]), np.array([np.nan, 0.0]), acc3)
    assert t3 == 0.0 and acc3 == {}
    # inverse Mollweide: the origin, and a point back from the forward map
    lon, lat = inverse_mollweide(np.array([0.0]), np.array([0.0]))
    assert abs(lon[0]) < 1e-9 and abs(lat[0]) < 1e-9
    phi, lam = math.radians(45.0), math.radians(30.0)
    th = phi
    for _ in range(50):
        th -= (2 * th + math.sin(2 * th) - math.pi * math.sin(phi)) / (2 + 2 * math.cos(2 * th))
    x = 2 * math.sqrt(2) / math.pi * MOLL_R * lam * math.cos(th)
    y = math.sqrt(2) * MOLL_R * math.sin(th)
    lon, lat = inverse_mollweide(np.array([x]), np.array([y]))
    assert abs(lon[0] - 30.0) < 1e-6 and abs(lat[0] - 45.0) < 1e-6, (lon, lat)
    lon, lat = inverse_mollweide(np.array([2e7]), np.array([8.9e6]))
    assert np.isnan(lon[0]), "outside the ellipse"
    # a split cell's pixels go to the part they lie in, not by area: cell
    # "a" is 80% DEU by area, but its GDP lies in POL (lon < -1)
    cells = Cells(locate=lambda lon, lat: "a", part_of=lambda lon, lat, h: "POL" if lon < -1 else None)
    g, bw, bh = lonlat_geometry(cells, -2.0, 2.0, 1.0, -1.0, 4, 4, 1)
    vals = np.zeros(16)
    vals[0], vals[1] = 10.0, 6.0   # (lon -1.5: POL) and (lon -0.5: no part found)
    acc, out = {}, Out()
    t, lost = zonal(g, np.arange(16), vals, acc)
    out.add("kummu/gdp", 2000, acc, t, lost)
    xw = {"a": [("DEU", 0.8, "land"), ("POL", 0.2, "land")]}
    by = collections.defaultdict(float)
    for h, iso, y, var, v, k in cell_rows(out, cells, xw)[0]:
        by[iso] += v
    assert abs(by["POL"] - (10.0 + 6.0 * 0.2)) < 1e-9 and abs(by["DEU"] - 6.0 * 0.8) < 1e-9, dict(by)
    # a part outside the cell's countries is not trusted: the area split
    cells = Cells(locate=lambda lon, lat: "a", part_of=lambda lon, lat, h: "CZE")
    g, _, _ = lonlat_geometry(cells, -2.0, 2.0, 1.0, -1.0, 4, 4, 1)
    acc, out = {}, Out()
    out.add("kummu/gdp", 2000, acc, *zonal(g, np.arange(1), np.array([10.0]), acc))
    assert {r[1] for r in cell_rows(out, cells, xw)[0]} == {"DEU", "POL"}
    # the fine crosswalk's parts: a sample is in the part holding it (an id
    # ancestor stands for its descendants), else in its owner's largest part,
    # left out of the fine table; "none" (high seas, no country) is no part
    fine = FineParts({"p1": ("a", "POL", "land"), "p2": ("a", "", "none")}, {"a": 10},
                     {"a": [("DEU", 0.7, "land"), ("POL", 0.3, "land")], "b": [("DEU", 1.0, "land")]},
                     cell_at=lambda lon, lat, r: f"s{lon:g}",
                     owner_of=lambda f: "a",
                     parent=lambda f, r: {"s1": "p1", "s2": "p2"}.get(f, "q") if r == 9 else "q")
    assert fine.part(1.0, 0.0, "a") == "POL", "in a fine part, through its ancestor"
    assert fine.part(2.0, 0.0, "a") is None, "a none part: no country"
    assert fine.part(3.0, 0.0, "a") == "DEU", "no fine part: the owner's largest"
    assert fine.part(3.0, 0.0, "b") is None, "an unsplit cell needs no part"
    # night lights are digital numbers per pixel, and a lon/lat pixel's area
    # falls with latitude: each row weighs by cos(latitude), the pixel's
    # area against one on the equator
    w = lat_weights(60.5, -1.0, 0, 2)
    assert abs(w[0] - math.cos(math.radians(60.0))) < 1e-12 and abs(w[1] - math.cos(math.radians(59.0))) < 1e-12
    assert ROW_WEIGHT["ntl"] and "kummu" not in ROW_WEIGHT, "only the per-pixel brightness is weighted"
    print("cell-grids selftest ok", file=sys.stderr)


def main():
    if sys.argv[1:] == ["--selftest"]:
        return selftest()
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--only", default="kummu,ntl,ghsl,edgar")
    a = ap.parse_args()
    xw = read_crosswalk(os.path.join(a.root, "data/derived/cells/crosswalk.csv"))
    fine = read_fine(os.path.join(a.root, "data/derived/cells/crosswalk-fine.csv"), xw)
    cells = Cells(part_of=fine.part)
    out = Out()
    for name in a.only.split(","):
        {"kummu": kummu, "ntl": ntl, "ghsl": ghsl, "edgar": edgar}[name](a.root, cells, out)
    rows, cov = cell_rows(out, cells, xw)
    write(a.root, rows, cov)
    by = collections.defaultdict(list)
    for k, v in cov.items():
        by[k.split("@")[0]].append(v["coverage"] or 0.0)
    log(len(rows), "rows;", {k: (round(min(v), 4), round(max(v), 4)) for k, v in sorted(by.items())})


if __name__ == "__main__":
    main()
