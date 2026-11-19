"""2021 check for docs/ALIGNMENT-DATA.md: do 2021's dep_russia and
us_defence_pact separate the countries on Russia's "unfriendly countries"
list (Government of Russia order 430-r, 5 March 2022, as published 7 March
2022) from the rest? Reads 2021 values only.

  python3 -I scripts/alignment-check-2021.py data/derived/alignment-annual.csv
"""
import csv
import statistics
import sys

EU27 = "AUT BEL BGR HRV CYP CZE DNK EST FIN FRA DEU GRC HUN IRL ITA LVA LTU LUX MLT NLD POL PRT ROU SVK SVN ESP SWE".split()
# The list as published: the EU's members, the UK (with Jersey, Anguilla, the
# British Virgin Islands and Gibraltar, not countries here), and these.
OTHERS = "USA CAN GBR UKR MNE CHE ALB AND ISL LIE MCO NOR SMR MKD JPN KOR AUS FSM NZL SGP TWN".split()
UNFRIENDLY = set(EU27 + OTHERS)


def auc(pos, neg):
    """P(score of a random listed country > a random unlisted one), ties 1/2."""
    if not pos or not neg:
        return float("nan")
    s = sum((p > n) + 0.5 * (p == n) for p in pos for n in neg)
    return s / (len(pos) * len(neg))


def main(path):
    v = {}
    with open(path, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["year"] == "2021" and r["var"] in ("dep_russia", "us_defence_pact"):
                v.setdefault(r["iso3"], {})[r["var"]] = float(r["value"])
    v.pop("RUS", None)
    both = {i: d for i, d in v.items() if len(d) == 2}
    print("countries with both 2021 values:", len(both),
          "listed:", sum(i in UNFRIENDLY for i in both),
          "listed but missing a value:", sorted(UNFRIENDLY - set(both)))
    for name, grp in (("listed", [d for i, d in both.items() if i in UNFRIENDLY]),
                      ("not listed", [d for i, d in both.items() if i not in UNFRIENDLY])):
        dr = [d["dep_russia"] for d in grp]
        print("%-10s n=%3d dep_russia median %.4f mean %.4f p90 %.4f max %.4f | pact share %.3f"
              % (name, len(grp), statistics.median(dr), statistics.mean(dr),
                 sorted(dr)[int(0.9 * (len(dr) - 1))], max(dr),
                 statistics.mean(d["us_defence_pact"] for d in grp)))
    pos = [i for i in both if i in UNFRIENDLY]
    neg = [i for i in both if i not in UNFRIENDLY]
    print("AUC, low dep_russia:      %.3f" % auc([-both[i]["dep_russia"] for i in pos], [-both[i]["dep_russia"] for i in neg]))
    print("AUC, us_defence_pact:     %.3f" % auc([both[i]["us_defence_pact"] for i in pos], [both[i]["us_defence_pact"] for i in neg]))
    comb = {i: both[i]["us_defence_pact"] - both[i]["dep_russia"] for i in both}
    print("AUC, pact then low dep:   %.3f" % auc([comb[i] for i in pos], [comb[i] for i in neg]))
    tab = {(a, b): [] for a in (1, 0) for b in (True, False)}
    for i, d in both.items():
        tab[(int(d["us_defence_pact"]), i in UNFRIENDLY)].append(i)
    for (p, l), xs in sorted(tab.items(), reverse=True):
        print("pact=%d listed=%-5s n=%3d %s" % (p, l, len(xs), " ".join(sorted(xs)) if len(xs) < 45 else ""))
    print("listed, highest dep_russia:", [(i, round(both[i]["dep_russia"], 3)) for i in sorted(pos, key=lambda i: -both[i]["dep_russia"])[:6]])
    print("unlisted pact members, lowest dep_russia:",
          [(i, round(both[i]["dep_russia"], 4)) for i in sorted(tab[(1, False)], key=lambda i: both[i]["dep_russia"])[:8]])
    for cut in (0.01, 0.02, 0.03, 0.05):
        tp = sum(both[i]["dep_russia"] < cut for i in pos)
        fp = sum(both[i]["dep_russia"] < cut for i in neg)
        print("dep_russia < %.2f: %d of %d listed, %d of %d unlisted" % (cut, tp, len(pos), fp, len(neg)))


if __name__ == "__main__":
    main(sys.argv[1])
