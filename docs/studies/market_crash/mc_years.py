import sys, os, collections
run, kind = sys.argv[1], sys.argv[2]
by = collections.defaultdict(lambda: [0, 0.0, 0])
for line in open(os.path.join("data/runs", run, kind, "forecasts.tsv")):
    s, place, o, p, y = line.rstrip("\n").split("\t")
    o = int(o)
    if s != "year" or len(place) != 3 or y not in ("true", "false") or o % 12 != 11: continue
    b = by[o // 12]; b[0] += 1; b[1] += float(p); b[2] += y == "true"
dec = collections.defaultdict(lambda: [0, 0.0, 0])
for yr, (n, sp, k) in sorted(by.items()):
    d = dec[yr // 10 * 10]; d[0] += n; d[1] += sp; d[2] += k
print("decade rows meanp rate")
for d, (n, sp, k) in sorted(dec.items()): print(d, n, "%.3f %.3f" % (sp / n, k / n))
print("year rows meanp rate")
for yr, (n, sp, k) in sorted(by.items()):
    if yr >= 1995: print(yr, n, "%.3f %.3f" % (sp / n, k / n))
