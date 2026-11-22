import sys, os, collections
run, kind = sys.argv[1], sys.argv[2]
by = collections.defaultdict(lambda: [0, 0.0, 0])
for line in open(os.path.join("data/runs", run, kind, "forecasts.tsv")):
    s, place, o, p, y = line.rstrip("\n").split("\t")
    if len(place) != 3 or y not in ("true", "false"): continue
    o = int(o); yr = o // 12
    if not 2003 <= yr <= 2022: continue
    if s == "year" and o % 12 != 11: continue
    b = by[(s, "2003-09" if yr < 2010 else "2010-22")]; b[0] += 1; b[1] += float(p); b[2] += y == "true"
for k, (n, sp, c) in sorted(by.items()): print(k, n, "meanp %.3f rate %.3f ratio %.2f" % (sp / n, c / n, (sp / n) / max(c / n, 1e-9)))
