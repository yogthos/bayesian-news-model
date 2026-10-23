"""Gridded economic data on A5 cells (docs/ECONOMIC-CHARACTER.md 8c step 1;
bead bnm-3ka.6): zonal sums per resolution-6 cell, split over the cell's
countries by the crosswalk's area shares. Writes
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
of about 91 km, a small error that conserves the total. A cell's sum is
then split over its country parts by their area shares (the crosswalk's
cell,iso,share), renormalised to sum to 1: for land variables (GDP,
lights, built-up, population) the land and disputed parts only, falling
back to all parts in a cell with none; for EDGAR every part (offshore
platforms and terminals lie in EEZs). Values in blocks on cells the
crosswalk has no row for (the high seas) are dropped and show in the
coverage. The split is by area, not by where in the cell the pixels lie:
a border cell's GDP is shared by area (bnm.cells/aggregate does the same).

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
                   2013, simulated VIIRS from 2014: a calibrated break).
                   Lag 1: annual composites come out the next year.
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

class Cells:
    """Dense indices for cells: index -> hex, and the cell of points."""

    def __init__(self, locate=None):
        self.hexes = []
        self.ix = {}
        if locate is None:
            import a5
            locate = lambda lon, lat: a5.u64_to_hex(a5.lonlat_to_cell((lon, lat), RES))
        self.locate = locate

    def index(self, h):
        i = self.ix.get(h)
        if i is None:
            i = self.ix[h] = len(self.hexes)
            self.hexes.append(h)
        return i

    def of_points(self, lons, lats):
        out = np.full(len(lons), -1, dtype=np.int64)
        for k, (x, y) in enumerate(zip(lons.tolist(), lats.tolist())):
            if x == x and y == y:
                out[k] = self.index(self.locate(((x + 180.0) % 360.0) - 180.0, max(-90.0, min(90.0, y))))
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


def raster_strips(path, band, f, geom_of, out, var, year, transform=None, rows=None, scale=1.0, nodata=None):
    """Read a raster band in strips of f rows multiple, sum blocks, add to out."""
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
        raster_strips(os.path.join(d, name), 1, 10, geom_of, out, "ntl/dn", y)
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
        for ci, v in acc.items():
            h = cells.hexes[ci]
            parts = xw.get(h)
            if not parts:
                nocw += v
                continue
            for iso, s in sorted(split_shares(parts, is_land(var)).items()):
                x = v * s
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
    assert acc[cells.ix["a"]] == left and acc[cells.ix["b"]] == a.sum() - left
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
    print("cell-grids selftest ok", file=sys.stderr)


def main():
    if sys.argv[1:] == ["--selftest"]:
        return selftest()
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--only", default="kummu,ntl,ghsl,edgar")
    a = ap.parse_args()
    xw = read_crosswalk(os.path.join(a.root, "data/derived/cells/crosswalk.csv"))
    cells = Cells()
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
