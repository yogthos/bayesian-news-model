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
                  supply; all origins together. share_<year>: the input's
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
  use.csv         region,product,user,kind,quantity,unit,share,value_share
                  use in the region of the product (all origins) by each
                  industry (kind industry, user its code) and final demand
                  category (kind final); share: of the product's total use
                  in the region; value_share: of the industry's output
                  value, the use valued at origin prices (monetary output
                  2011 over hybrid supply; rough, as for supply.csv, and
                  above 1 in about a thousand rows). Rows with share or
                  value_share of at least 1e-4.
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
                  (natural gas, product backflows, blending components).

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
            for u in units:
                cols = [p for p in range(P) if punit[p] == u]
                g = Vd[s][cols].sum(axis=0)  # (I,) output in unit u
                for p in cols:
                    if q[s, p] <= 0:
                        continue
                    with np.errstate(invalid="ignore", divide="ignore"):
                        M[:, p] = np.where(g > 0, Vd[s, p] / q[s, p] / g, 0.0)
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
        w.writerow(["region", "product", "user", "kind", "quantity", "unit", "share", "value_share"])
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
                    if vs > 1:
                        vs_over += 1
                    if sh >= 1e-4 or (np.isfinite(vs) and vs >= 1e-4):
                        w.writerow([reg, pcode[p], ind[j][0], "industry", fmt(qty), punit[p], fmt(sh), fmt(vs)])
                        nuse += 1
                for c in range(C):
                    qty = Yd[s, p, c]
                    if qty > 0 and qty / tot[p] >= 1e-4:
                        w.writerow([reg, pcode[p], fd[c], "final", fmt(qty), punit[p], fmt(qty / tot[p]), ""])
                        nuse += 1
    print(f"use: {nuse} rows; {vs_over} industry inputs valued above the industry's output")

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
    refn = [j for j, (c, _) in enumerate(ind) if c == "A_REFN"][0]
    refin = [pcode.index(c) for c in REFINERY_IN]
    refout = [p for p in range(P) if prod[p][3].startswith("p23.20") and punit[p] == "tonnes"]
    refall = [p for p in range(P) if punit[p] == "tonnes"
              and (prod[p][3].startswith(("p11", "p23.20")) or pcode[p] in REFINERY_EXTRA)]
    rows = []
    for s, reg in enumerate(regions):
        used = T[s].sum(axis=1)  # (P,) every use of s's output
        for p in range(P):
            if q[s, p] > 0:
                rows.append([reg, "product:" + pcode[p], used[p], q[s, p], punit[p], (q[s, p] - used[p]) / q[s, p]])
        cout = Vd[s][refout, refn].sum()
        for name, idx in [("refinery", refin), ("refinery-all", refall)]:
            cin = Ud[s][idx, refn].sum()
            if cin > 0 or cout > 0:
                rows.append([reg, name, cin, cout, "tonnes", (cout - cin) / cin if cin > 0 else np.nan])
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
    for name in ["refinery", "refinery-all"]:
        rs = [r for r in rows if r[1] == name and r[2] > 1e6]
        res = np.array([r[5] for r in rs])
        print(f"{name}: {len(rs)} regions refining over 1 Mt; residual (out - in) / in median {np.median(res):+.3f}, "
              f"range {res.min():+.3f} to {res.max():+.3f}")
        print("  " + ", ".join(f"{r[0]} {r[5]:+.2f}" for r in rs))


if __name__ == "__main__":
    main()
