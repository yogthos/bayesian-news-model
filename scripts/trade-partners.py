"""Each country's goods exports to Russia, China and the EU as a share of
its exports to the world, annual (bnm-asp: trade-weighted contagion, Glick
and Rose 1999; the 2014-15 rouble fall reached Russia's neighbours through
their exports to it). IMF Direction of Trade Statistics as mirrored by
DBnomics (data/raw/slow/imf-dots, scripts/fetch-dots.py): exports FOB, US
dollars. A year's shares are taken as published by June of the next year.

  world/unit  trade/export_share_ru|cn|eu   share, 0-1

  python3 -I scripts/trade-partners.py > data/derived/trade-partners.tsv
"""
import csv
import glob
import json
import os
import sys

PARTNER = {"RU": "ru", "CN": "cn", "B0": "eu"}


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


def shares(docs, iso):
    exp = {}
    for d in docs:
        dm = d["dimensions"]
        if dm.get("INDICATOR") != "TXG_FOB_USD" or dm.get("FREQ") != "A" or dm["REF_AREA"] not in iso:
            continue
        cp = dm["COUNTERPART_AREA"]
        if cp not in PARTNER and cp != "W00":
            continue
        for p, v in zip(d["period"], d["value"]):
            if isinstance(v, (int, float)):
                exp[(dm["REF_AREA"], cp, int(p))] = float(v)
    out = []
    for (ref, cp, y), v in sorted(exp.items()):
        if cp == "W00":
            continue
        w = exp.get((ref, "W00", y))
        if w and w > 0:
            out.append([iso[ref], f"trade/export_share_{PARTNER[cp]}", "a", f"{y}-12-31", f"{y + 1}-06-30", repr(v / w)])
    return out


def main():
    docs = []
    for p in sorted(glob.glob(os.path.join("data/raw/slow/imf-dots", "partner-*.json"))):
        with open(p, encoding="utf-8") as f:
            docs += json.load(f)["series"]["docs"]
    w = csv.writer(sys.stdout, delimiter="\t", lineterminator="\n")
    for r in shares(docs, iso2_to_iso3()):
        w.writerow(r)


if __name__ == "__main__":
    main()
