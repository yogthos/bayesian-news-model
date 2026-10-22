"""The product graph of the material economy (docs/STOCK-FLOW.md, "Product
graph"; epic bnm-1pq): what each product is made from and who uses it, per
region, in physical units where the source has them.

Reads the EXIOBASE hybrid MR-HSUT 2011 v3.3.18 (data/raw/exiobase/
hybrid-v3.3.18-2011: MR_HSUP, MR_HUSE, MR_HSUTs_FD; 200 products, 164
industries, 43 countries and 5 rest-of-world regions; goods in tonnes,
energy in TJ, services in M EUR; CC BY 4.0) and the monetary product-by-
product tables v3.8.2 for 1995, 2011 and 2022 (data/raw/exiobase/
v3.8.2-pxp/IOT_<year>_pxp.zip: A and x, M EUR at basic prices; CC BY-SA
4.0; 49 regions, Taiwan apart, folded into WA here as the hybrid tables
do). Writes data/derived/supply/:

  products.csv    code,name,unit,group
                  group: raw (crops, animals, forestry, fish, coal, crude,
                  gas, ores, quarrying), energy (coke, refined products,
                  nuclear fuel, electricity, gases, steam, biofuels),
                  material (textiles, wood, pulp and paper, basic
                  chemicals, plastics and fertiliser, glass, cement, basic
                  metals, secondary raw materials), manufactured (food,
                  apparel, printing, rubber and plastic products, metal
                  products, machinery, vehicles, furniture), service
                  (M EUR services and the "tonnes (service)" waste
                  treatment and re-processing services)
  industries.csv  code,name
  recipes.csv     region,output,input,phys,input_unit,output_unit,
                  share_1995,share_2011,share_2022,physical
                  phys: input per unit of output in the hybrid units
                  (tonnes per tonne, TJ per tonne, M EUR per tonne...), the
                  industry-technology assumption on the hybrid supply and
                  use tables: the inputs of the industries making the
                  product, each per unit of its output in the product's
                  unit, weighted by the industry's share of the product's
                  supply; all origins together. An industry making
                  products in more than one unit (waste treatment and
                  the secondary material it recovers, incineration and its
                  electricity, pulp and its heat) has its inputs split
                  between its units by the value of its output in each
                  (rough prices, as for supply.csv; equal parts where a
                  unit's output has no price), so no input is counted
                  whole against each unit. share_<year>: the input's
                  value per unit of the output's value in the monetary pxp
                  table of that year (the technical coefficient, all
                  origins summed; blank where the output is not made that
                  year). The monetary tables route some feedstocks through
                  other products (Germany's N fertiliser buys its gas as
                  gas distribution services, its plastics their naphtha as
                  chemicals nec), the hybrid ones do not: a feedstock can
                  be large in phys and small in share. Valuing phys at
                  monetary output over hybrid supply does not mend it (the
                  two releases disagree on some products' size: shares of
                  9 for gas in German N fertiliser). physical: 1 when both
                  units are tonnes or TJ.
                  Kept: rows with a 2011 share of at least 1e-4,
                  or physical rows carrying at least 1% of the output's
                  inputs in the input's unit.
  production.csv  region,product,quantity,unit,value_1995,value_2011,
                  value_2022   (hybrid supply 2011; M EUR output by year)
  supply.csv      region,industry,product,quantity,unit,market_share,
                  industry_share
                  what each industry makes: market_share of the product's
                  supply in the region, industry_share of the industry's
                  output value (at monetary output 2011 over hybrid supply,
                  rough where the releases disagree); industry shares of at
                  least 1e-3.
  use.csv         region,product,user,kind,quantity,unit,share,value_share,
                  value_rough
                  use in the region of the product (all origins) by each
                  industry (kind industry, user its code) and final demand
                  category (kind final); share: of the product's total use
                  in the region; value_share: of the industry's output
                  value, the use valued at origin prices (monetary output
                  2011 over hybrid supply; rough, as for supply.csv);
                  blank where that ratio is above 1 (about a thousand
                  rows, where the two releases disagree on a product's
                  size), the ratio then in value_rough: flagged, not a
                  share. Rows with share or value ratio of at least 1e-4.
  trade.csv       product,origin,dest,quantity,share
                  the product used in dest (by industries and final demand)
                  by origin, the MR tables' import shares (domestic
                  included); shares of at least 1e-3.
  balances.csv    region,check,input,output,unit,residual
                  product: a region's supply of a product less every use of
                  it (intermediate and final, at home and abroad), relative
                  to supply; refinery: tonnes of refined products out of
                  the refinery industry against tonnes of crude, NGL, other
                  hydrocarbons and refinery feedstocks in; refinery-all:
                  against every tonne of hydrocarbon, petroleum product,
                  additive and biofuel the refinery industry takes in
                  (natural gas, product backflows, blending components);
                  refinery-unscaled, refinery-all-unscaled: the same
                  before the scaling below.
  scaling.csv     region,products,kind,exiobase_kt,unsd_kt,target_kt,
                  fitted_kt,source
                  naphtha, LPG, bitumen, other products, and jet and other
                  kerosene together, run 1.5-3 times UNSD's 2011 figures in
                  the hybrid tables (their refinery output carries what the
                  refinery industry also takes in: gas processing, product
                  backflows). Each is scaled to UNSD (data/derived/supply/
                  oil-products.csv, from scripts/oil-products.py, which
                  runs first): its origin-by-destination uses (every
                  industry and final demand) fitted by biproportional
                  scaling to production by origin (flow 01; kind
                  production) and use by destination (total energy supply,
                  GA; kind use; not for kerosene, whose bunkers GA leaves
                  out), a region's supply scaled with its uses, so supply
                  still equals use. source unsd where both figures exceed
                  100 kt; median, the median ratio, for an origin without
                  one; a destination without one is left free. Every table
                  above is written after the scaling; without
                  oil-products.csv nothing is scaled.

    python -I scripts/product-graph.py [data-root]
"""
import csv
import os
import sys
import zipfile

import numpy as np
import pandas as pd

ROOT = sys.argv[1] if len(sys.argv) > 1 else "data"
HY = os.path.join(ROOT, "raw/exiobase/hybrid-v3.3.18-2011")
PXP = os.path.join(ROOT, "raw/exiobase/v3.8.2-pxp")
OUT = os.path.join(ROOT, "derived/supply")
YEARS = [1995, 2011, 2022]
PHYS = {"tonnes", "TJ"}

RAW_PREFIX = ("p01.", "p02", "p05", "p10.a", "p10.b", "p10.c", "p10.d", "p10.f", "p10.h",
              "p11", "p12", "p13", "p14")
ENERGY_PREFIX = ("p10.e", "p10.g", "p23", "p40", "p24.e", "p24.f", "p24.g", "p24.h", "p24.i")
MATERIAL_PREFIX = ("p17", "p20", "p21", "p24", "p26", "p27", "p37")
REFINERY_IN = ["C_COIL", "C_GASL", "C_OGPL", "C_REFF"]
REFINERY_EXTRA = ["C_ADDC", "C_BIOG", "C_BIOD", "C_OBIO"]
# refined products whose hybrid tonnages run 1.5-3 times UNSD's 2011
# production, scaled to it; jet and other kerosene together (countries
# split them differently: Spain reports its jet as kerosene)
SCALED = [["C_NAPT"], ["C_LPGA"], ["C_BITU"], ["C_NSPP"], ["C_KJET", "C_KERO"]]
SCALE_MIN_KT = 100.0
ISO3 = {"AU": "AUS", "AT": "AUT", "BE": "BEL", "BR": "BRA", "BG": "BGR", "CA": "CAN", "CN": "CHN", "CY": "CYP",
        "CZ": "CZE", "DK": "DNK", "EE": "EST", "FI": "FIN", "FR": "FRA", "DE": "DEU", "GR": "GRC", "HU": "HUN",
        "HR": "HRV", "IN": "IND", "ID": "IDN", "IE": "IRL", "IT": "ITA", "JP": "JPN", "LV": "LVA", "LT": "LTU",
        "LU": "LUX", "MT": "MLT", "MX": "MEX", "NL": "NLD", "NO": "NOR", "PL": "POL", "PT": "PRT", "RO": "ROU",
        "RU": "RUS", "SK": "SVK", "SI": "SVN", "ZA": "ZAF", "KR": "KOR", "ES": "ESP", "SE": "SWE", "CH": "CHE",
        "TR": "TUR", "GB": "GBR", "US": "USA"}


def group(code, unit):
    if unit == "tonnes (service)" or unit == "Meuro" and not code.startswith(("p34", "p35", "p37")):
        return "service"
    if code.startswith(RAW_PREFIX):
        return "raw"
    if code.startswith(ENERGY_PREFIX):
        return "energy"
    if code.startswith(MATERIAL_PREFIX):
        return "material"
    return "manufactured"


def read_hybrid(name):
    """(row labels [region name code codetxt unit], column header rows, matrix)."""
    path = os.path.join(HY, name)
    with open(path, newline="") as fh:
        r = csv.reader(fh)
        hdr = [next(r) for _ in range(4)]
    df = pd.read_csv(path, header=None, skiprows=4, low_memory=False)
    labels = df.iloc[:, :5].astype(str).values.tolist()
    m = df.iloc[:, 5:].to_numpy(dtype=float)
    return labels, [h[5:] for h in hdr], m


def read_pxp(year, regions, products):
    """{'A': (R, P, P) summed over origins, 'x': (R, P)} in the hybrid's
    region order, Taiwan folded into WA by output weight."""
    z = zipfile.ZipFile(os.path.join(PXP, f"IOT_{year}_pxp.zip"))
    d = f"IOT_{year}_pxp/"
    x = pd.read_csv(z.open(d + "x.txt"), sep="\t")
    preg = list(dict.fromkeys(x["region"]))
    pprod = list(x["sector"][: len(x) // len(preg)])
    np_ = len(pprod)
    xv = x["indout"].to_numpy(dtype=float).reshape(len(preg), np_)
    a = pd.read_csv(z.open(d + "A.txt"), sep="\t", skiprows=3, header=None)
    am = a.iloc[:, 2:].to_numpy(dtype=float)
    del a
    agg = am.reshape(len(preg), np_, len(preg), np_).sum(axis=0).transpose(1, 0, 2).copy()  # (dest, input, output)
    del am
    if pprod != products:
        raise SystemExit(f"pxp {year}: products differ from the hybrid's")
    ix = {r: k for k, r in enumerate(preg)}
    A = np.zeros((len(regions), np_, np_))
    X = np.zeros((len(regions), np_))
    for k, r in enumerate(regions):
        if r == "WA" and "TW" in ix:
            w1, w2 = xv[ix["WA"]], xv[ix["TW"]]
            tot = w1 + w2
            with np.errstate(invalid="ignore", divide="ignore"):
                A[k] = np.where(tot > 0, (agg[ix["WA"]] * w1 + agg[ix["TW"]] * w2) / tot, 0.0)
            X[k] = tot
        else:
            A[k] = agg[ix[r]]
            X[k] = xv[ix[r]]
    return {"A": A, "x": X}


def unit_split(vd, pr, punit, units):
    """{unit: (I,)} the share of each industry's inputs that goes with its
    output in that unit: 1 for an industry making products in one unit; for
    one making them in several, the value of its output in each (vd (P, I)
    supply, pr (P,) prices), or equal parts where some unit's output has no
    value. The shares of an industry add to 1."""
    out = {}
    val = {}
    for u in units:
        cols = [p for p in range(len(punit)) if punit[p] == u]
        out[u] = vd[cols].sum(axis=0)  # physical output in u, (I,)
        val[u] = (vd[cols] * pr[cols][:, None]).sum(axis=0)
    has = {u: out[u] > 0 for u in units}
    n = sum(has[u].astype(float) for u in units)
    priced = np.all([~has[u] | (val[u] > 0) for u in units], axis=0)
    vtot = sum(val[u] for u in units)
    with np.errstate(invalid="ignore", divide="ignore"):
        return {u: np.where(has[u], np.where(priced & (vtot > 0), val[u] / vtot, 1.0 / np.maximum(n, 1.0)), 0.0)
                for u in units}


def unsd_2011(flow):
    """{(iso3, exiobase code): kt} of UNSD's 2011 figures for `flow` (01
    production, GA total energy supply), or None without oil-products.csv."""
    path = os.path.join(OUT, "oil-products.csv")
    if not os.path.exists(path):
        return None
    o = pd.read_csv(path, dtype={"flow": str, "iso": str})
    o = o[(o["year"] == 2011) & (o["flow"] == flow)]
    return o.groupby(["iso", "exiobase"])["value"].sum().to_dict()


def targets(regions, pcode, x, un, grp):
    """{region: (exiobase kt, unsd kt or None, target kt, source)} for the
    products `grp` together: x (R, P) EXIOBASE's tonnes (production or
    use), un UNSD's kt. Target UNSD's where both are above SCALE_MIN_KT;
    else the median ratio over those regions times EXIOBASE's (the
    rest-of-world regions, small or unreported producers)."""
    ix = [pcode.index(c) for c in grp]
    own = {}
    for s, reg in enumerate(regions):
        e = x[s, ix].sum() / 1000.0
        u = sum(un.get((ISO3[reg], c), 0.0) for c in grp) if reg in ISO3 else None
        own[reg] = (e, u)
    ok = {r: u / e for r, (e, u) in own.items() if u is not None and e > SCALE_MIN_KT and u > SCALE_MIN_KT}
    med = float(np.median(list(ok.values())))
    return {r: (e, u, u if r in ok else e * med, "unsd" if r in ok else "median") for r, (e, u) in own.items() if e > 0}


def scale_to_unsd(regions, pcode, V4, U4, Y4):
    """Scale V4, U4, Y4 in place, each group of SCALED to UNSD's 2011
    figures: for a product the origin-by-destination matrix of its uses
    (every industry and final demand) is fitted by biproportional scaling
    (RAS) to production by origin (UNSD's flow 01) and, for the
    non-kerosene products, to use by destination (UNSD's total energy
    supply, GA); a region's supply is scaled with its row, so its supply
    still equals its uses. Origins without a UNSD figure take the median
    ratio; destinations without one are free (they take up what the
    production and use totals leave). Returns [(region, group, kind, exiobase kt, unsd kt,
    target kt, fitted kt, source)], or None without oil-products.csv."""
    prod_un = unsd_2011("01")
    if prod_un is None:
        return None
    use_un = unsd_2011("GA")
    R = len(regions)
    report = []
    for grp in SCALED:
        ix = [pcode.index(c) for c in grp]
        q = np.stack([V4[r, :, r, :].sum(axis=1) for r in range(R)])
        M0 = sum(U4[:, p, :, :].sum(axis=2) + Y4[:, p, :, :].sum(axis=2) for p in ix)  # (origin, dest) tonnes
        use = np.zeros((R, len(pcode)))
        for p in ix:
            use[:, p] = U4[:, p, :, :].sum(axis=(0, 2)) + Y4[:, p, :, :].sum(axis=(0, 2))
        rt = targets(regions, pcode, q, prod_un, grp)
        ct = targets(regions, pcode, use, use_un, grp) if grp != ["C_KJET", "C_KERO"] else {}
        rows = np.array([rt[r][2] * 1000.0 if r in rt else 0.0 for r in regions])
        # destinations without a UNSD figure are left free: they take up
        # the difference between the production and use targets' totals
        cols = np.array([ct[r][2] * 1000.0 if r in ct and ct[r][3] == "unsd" else np.nan for r in regions])
        M = M0.copy()
        for _ in range(200):
            with np.errstate(invalid="ignore", divide="ignore"):
                rs = M.sum(axis=1)
                M *= np.where(rs > 0, rows / rs, 0.0)[:, None]
                cs = M.sum(axis=0)
                M *= np.where(np.isfinite(cols) & (cs > 0), cols / cs, 1.0)[None, :]
        with np.errstate(invalid="ignore", divide="ignore"):
            f = np.where(M0 > 0, M / M0, 1.0)  # (origin, dest)
            fv = np.where(M0.sum(axis=1) > 0, M.sum(axis=1) / M0.sum(axis=1), 1.0)
        for p in ix:
            U4[:, p, :, :] *= f[:, :, None]
            Y4[:, p, :, :] *= f[:, :, None]
            for r in range(R):
                V4[r, p, r, :] *= fv[r]
        fitted_rows = M.sum(axis=1) / 1000.0
        fitted_cols = M.sum(axis=0) / 1000.0
        for k, reg in enumerate(regions):
            if reg in rt:
                e, u, t, src = rt[reg]
                report.append((reg, grp, "production", e, u, t, fitted_rows[k], src))
            if reg in ct:
                e, u, t, src = ct[reg]
                report.append((reg, grp, "use", e, u, t, fitted_cols[k], src))
    return report


def refinery_balance(regions, prod, punit, pcode, ind, Vd, Ud):
    """{(region, check): (in, out, (out - in) / in)} for the refinery
    industry, as balances.csv has it."""
    refn = [j for j, (c, _) in enumerate(ind) if c == "A_REFN"][0]
    refin = [pcode.index(c) for c in REFINERY_IN]
    refout = [p for p in range(len(prod)) if prod[p][3].startswith("p23.20") and punit[p] == "tonnes"]
    refall = [p for p in range(len(prod)) if punit[p] == "tonnes"
              and (prod[p][3].startswith(("p11", "p23.20")) or pcode[p] in REFINERY_EXTRA)]
    out = {}
    for s, reg in enumerate(regions):
        cout = Vd[s][refout, refn].sum()
        for name, idx in [("refinery", refin), ("refinery-all", refall)]:
            cin = Ud[s][idx, refn].sum()
            if cin > 0 or cout > 0:
                out[(reg, name)] = (cin, cout, (cout - cin) / cin if cin > 0 else np.nan)
    return out


def fmt(v):
    return "" if v is None or not np.isfinite(v) else f"{v:.6g}"


def main():
    os.makedirs(OUT, exist_ok=True)
    plab, phdr, V = read_hybrid("MR_HSUP_2011_v3_3_18.csv")
    ulab, uhdr, U = read_hybrid("MR_HUSE_2011_v3_3_18.csv")
    flab, fhdr, Y = read_hybrid("MR_HSUTs_2011_v3_3_18_FD.csv")
    regions = list(dict.fromkeys(l[0] for l in plab))
    R = len(regions)
    P = len(plab) // R
    I = len(phdr[0]) // R
    C = len(fhdr[0]) // R
    assert ulab == plab and flab == plab and uhdr == phdr
    assert list(dict.fromkeys(phdr[0])) == regions and list(dict.fromkeys(fhdr[0])) == regions
    prod = [(l[3], l[1], l[4], l[2]) for l in plab[:P]]  # codetxt, name, unit, code
    pcode = [p[0] for p in prod]
    punit = [p[2] for p in prod]
    pnames = [p[1] for p in prod]
    ind = [(phdr[3][j], phdr[1][j]) for j in range(I)]
    fd = [fhdr[3][c] for c in range(C)]
    print(f"hybrid: {R} regions, {P} products, {I} industries, {C} final demand categories")

    V4 = V.reshape(R, P, R, I)
    U4 = U.reshape(R, P, R, I)
    Y4 = Y.reshape(R, P, R, C)
    del V, U, Y
    off = V4.sum() - sum(V4[r, :, r, :].sum() for r in range(R))
    print(f"supply off the regional diagonal: {off:.3g} (should be 0)")

    # light and non-fuel refined products scaled to UNSD's 2011 figures
    # (scale_to_unsd): supply still equals uses; the refinery's inputs are
    # not touched (the excess is what its refinery industry also takes in:
    # gas processing, product backflows), so the refinery balance moves
    # toward its crude
    before = refinery_balance(regions, prod, punit, pcode, ind, np.stack([V4[r, :, r, :] for r in range(R)]),
                              U4.sum(axis=0).transpose(1, 0, 2))
    scaling = scale_to_unsd(regions, pcode, V4, U4, Y4)
    if scaling is None:
        print("no oil-products.csv: refined products not scaled to UNSD (run scripts/oil-products.py first)")
    else:
        with open(os.path.join(OUT, "scaling.csv"), "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["region", "products", "kind", "exiobase_kt", "unsd_kt", "target_kt", "fitted_kt", "source"])
            for reg, grp, kind, e, u, t, fit, src in scaling:
                w.writerow([reg, "+".join(grp), kind, fmt(e), fmt(u) if u is not None else "", fmt(t), fmt(fit), src])
        for grp in SCALED:
            for kind in ["production", "use"]:
                rs = [x for x in scaling if x[1] == grp and x[2] == kind]
                if not rs:
                    continue
                un = [x for x in rs if x[7] == "unsd"]
                off = np.array([x[6] / x[5] - 1 for x in un if x[5] > 0])
                print(f"scaled {'+'.join(grp)} {kind}: {len(un)} regions to UNSD (EXIOBASE/UNSD median "
                      f"{np.median([x[3] / x[4] for x in un]):.2f}), {len(rs) - len(un)} at the median ratio; "
                      f"world {sum(x[3] for x in rs) / 1000:.0f} -> {sum(x[6] for x in rs) / 1000:.0f} Mt; "
                      f"fitted to UNSD within {np.abs(off).max():.1%} (median {np.median(np.abs(off)):.1%})")

    Vd = np.stack([V4[r, :, r, :] for r in range(R)])  # (R, P, I)
    q = Vd.sum(axis=2)  # (R, P) production
    Ud = U4.sum(axis=0).transpose(1, 0, 2).copy()  # (dest, P, I)
    Yd = Y4.sum(axis=0).transpose(1, 0, 2).copy()  # (dest, P, C)
    T = U4.sum(axis=3) + Y4.sum(axis=3)  # (origin, P, dest)

    # monetary tables
    mon_products = None
    with zipfile.ZipFile(os.path.join(PXP, "IOT_2011_pxp.zip")) as z:
        x = pd.read_csv(z.open("IOT_2011_pxp/x.txt"), sep="\t")
        mon_products = list(x["sector"][: len(x) // len(dict.fromkeys(x["region"]))])
    pxp_names = {}
    with zipfile.ZipFile(os.path.join(PXP, "IOT_2011_pxp.zip")) as z:
        pt = pd.read_csv(z.open("IOT_2011_pxp/products.txt"), sep="\t")
        pxp_names = dict(zip(pt["CodeTxt"], pt["Name"]))
    if [pxp_names.get(c) for c in pcode] != mon_products:
        raise SystemExit("pxp product order differs from the hybrid's")
    mon = {}
    for y in YEARS:
        mon[y] = read_pxp(y, regions, mon_products)
        print(f"pxp {y}: read")

    # products, industries
    with open(os.path.join(OUT, "products.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["code", "name", "unit", "group"])
        for c, n, u, k in prod:
            w.writerow([c, n.strip(), u, group(k, u)])
    with open(os.path.join(OUT, "industries.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["code", "name"])
        for c, n in ind:
            w.writerow([c, n.strip()])

    # prices: monetary output 2011 over hybrid supply, M EUR per unit. The
    # two releases (hybrid v3.3.18, monetary v3.8.2) do not always agree on
    # a product's size, so these value the use table only roughly
    with np.errstate(invalid="ignore", divide="ignore"):
        price = np.where(q > 0, mon[2011]["x"] / q, 0.0)  # (R, P)

    # recipes: industry technology in hybrid units
    units = sorted(set(punit))
    nrec = 0
    nphys = 0
    with open(os.path.join(OUT, "recipes.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "output", "input", "phys", "input_unit", "output_unit",
                    "share_1995", "share_2011", "share_2022", "physical"])
        for s, reg in enumerate(regions):
            M = np.zeros((I, P))
            theta = unit_split(Vd[s], price[s], punit, units)  # {unit: (I,)}
            for u in units:
                cols = [p for p in range(P) if punit[p] == u]
                g = Vd[s][cols].sum(axis=0)  # (I,) output in unit u
                for p in cols:
                    if q[s, p] <= 0:
                        continue
                    with np.errstate(invalid="ignore", divide="ignore"):
                        M[:, p] = np.where(g > 0, Vd[s, p] / q[s, p] / g * theta[u], 0.0)
            a = Ud[s] @ M  # (input P, output P)
            for p in range(P):
                if q[s, p] <= 0:
                    continue
                col = a[:, p]
                # the output's physical inputs by unit, for the 1% rule
                by_unit = {u: col[[i for i in range(P) if punit[i] == u]].sum() for u in PHYS}
                for i in range(P):
                    if i == p:
                        continue
                    sh = [mon[y]["A"][s, i, p] if mon[y]["x"][s, p] > 0 else np.nan for y in YEARS]
                    phys = punit[i] in PHYS and punit[p] in PHYS
                    keep = sh[1] >= 1e-4 or (phys and col[i] > 0 and col[i] >= 0.01 * by_unit[punit[i]])
                    if not keep or (col[i] <= 0 and not sh[1] > 0):
                        continue
                    w.writerow([reg, pcode[p], pcode[i], fmt(col[i]), punit[i], punit[p],
                                fmt(sh[0]), fmt(sh[1]), fmt(sh[2]), 1 if phys else 0])
                    nrec += 1
                    nphys += phys
    print(f"recipes: {nrec} rows, {nphys} physical")

    # production
    with open(os.path.join(OUT, "production.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "product", "quantity", "unit", "value_1995", "value_2011", "value_2022"])
        for s, reg in enumerate(regions):
            for p in range(P):
                if q[s, p] > 0 or mon[2011]["x"][s, p] > 0:
                    w.writerow([reg, pcode[p], fmt(q[s, p]), punit[p]] + [fmt(mon[y]["x"][s, p]) for y in YEARS])

    # what each industry makes
    nsup = 0
    with open(os.path.join(OUT, "supply.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "industry", "product", "quantity", "unit", "market_share", "industry_share"])
        for s, reg in enumerate(regions):
            val = Vd[s] * price[s][:, None]  # (P, I)
            tot = val.sum(axis=0)
            for j in range(I):
                for p in np.nonzero(Vd[s][:, j] > 0)[0]:
                    ish = val[p, j] / tot[j] if tot[j] > 0 else np.nan
                    if ish >= 1e-3 or not np.isfinite(ish):
                        w.writerow([reg, ind[j][0], pcode[p], fmt(Vd[s][p, j]), punit[p],
                                    fmt(Vd[s][p, j] / q[s, p]), fmt(ish)])
                        nsup += 1
    print(f"supply: {nsup} rows")

    # use by industry and final demand, valued at origin prices
    nuse = 0
    vs_over = 0
    with open(os.path.join(OUT, "use.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "product", "user", "kind", "quantity", "unit", "share", "value_share", "value_rough"])
        for s, reg in enumerate(regions):
            uval = np.einsum("rpj,rp->pj", U4[:, :, s, :], price)  # (P, I)
            outval = (Vd[s] * price[s][:, None]).sum(axis=0)  # (I,)
            tot = Ud[s].sum(axis=1) + Yd[s].sum(axis=1)
            for p in range(P):
                if tot[p] <= 0:
                    continue
                for j in range(I):
                    qty = Ud[s, p, j]
                    if qty <= 0:
                        continue
                    sh = qty / tot[p]
                    vs = uval[p, j] / outval[j] if outval[j] > 0 else np.nan
                    if sh >= 1e-4 or (np.isfinite(vs) and vs >= 1e-4):
                        over = np.isfinite(vs) and vs > 1
                        vs_over += over
                        w.writerow([reg, pcode[p], ind[j][0], "industry", fmt(qty), punit[p], fmt(sh),
                                    "" if over else fmt(vs), fmt(vs) if over else ""])
                        nuse += 1
                for c in range(C):
                    qty = Yd[s, p, c]
                    if qty > 0 and qty / tot[p] >= 1e-4:
                        w.writerow([reg, pcode[p], fd[c], "final", fmt(qty), punit[p], fmt(qty / tot[p]), "", ""])
                        nuse += 1
    print(f"use: {nuse} rows; {vs_over} industry inputs valued above the industry's output (value_rough)")

    # trade: origins of what each region uses
    ntr = 0
    with open(os.path.join(OUT, "trade.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["product", "origin", "dest", "quantity", "share"])
        for p in range(P):
            for s, reg in enumerate(regions):
                col = T[:, p, s]
                tot = col.sum()
                if tot <= 0:
                    continue
                for r in range(R):
                    if col[r] > 0 and col[r] / tot >= 1e-3:
                        w.writerow([pcode[p], regions[r], reg, fmt(col[r]), fmt(col[r] / tot)])
                        ntr += 1
    print(f"trade: {ntr} rows")

    # balances
    after = refinery_balance(regions, prod, punit, pcode, ind, Vd, Ud)
    rows = []
    for s, reg in enumerate(regions):
        used = T[s].sum(axis=1)  # (P,) every use of s's output
        for p in range(P):
            if q[s, p] > 0:
                rows.append([reg, "product:" + pcode[p], used[p], q[s, p], punit[p], (q[s, p] - used[p]) / q[s, p]])
        for name in ["refinery", "refinery-all"]:
            if (reg, name) in after:
                cin, cout, res = after[(reg, name)]
                rows.append([reg, name, cin, cout, "tonnes", res])
                if scaling is not None:
                    rows.append([reg, name + "-unscaled", before[(reg, name)][0], before[(reg, name)][1], "tonnes",
                                 before[(reg, name)][2]])
    with open(os.path.join(OUT, "balances.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["region", "check", "input", "output", "unit", "residual"])
        for r in rows:
            w.writerow(r[:2] + [fmt(r[2]), fmt(r[3]), r[4], fmt(r[5])])
    prodres = np.array([abs(r[5]) for r in rows if r[1].startswith("product:") and r[3] > 0])
    bygroup = {}
    for r in rows:
        if r[1].startswith("product:") and r[3] > 0 and abs(r[5]) > 0.01:
            g = group(prod[pcode.index(r[1][8:])][3], r[4])
            bygroup[g] = bygroup.get(g, 0) + 1
    print(f"product balances off by more than 1%, by group: {bygroup}")
    print(f"product balances: {len(prodres)}; |residual| median {np.median(prodres):.3g}, "
          f"90th pct {np.percentile(prodres, 90):.3g}, above 1%: {(prodres > 0.01).sum()}")
    for name in ["refinery-unscaled", "refinery", "refinery-all-unscaled", "refinery-all"]:
        rs = [r for r in rows if r[1] == name and r[2] > 1e6]
        if not rs:
            continue
        res = np.array([r[5] for r in rs])
        print(f"{name}: {len(rs)} regions refining over 1 Mt; residual (out - in) / in median {np.median(res):+.3f}, "
              f"range {res.min():+.3f} to {res.max():+.3f}")
        print("  " + ", ".join(f"{r[0]} {r[5]:+.2f}" for r in rs))


if __name__ == "__main__":
    main()
