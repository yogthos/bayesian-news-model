import sys, os, collections
run, kind = sys.argv[1], sys.argv[2]
yr, q = {}, {}
for line in open(os.path.join("data/runs", run, kind, "forecasts.tsv")):
    s, place, o, p, y = line.rstrip("\n").split("\t")
    if len(place) != 3: continue
    o = int(o)
    if s == "year" and o % 12 == 11: yr[(place, o)] = (float(p), y)
    if s == "quarter-lead": q[(place, o)] = (float(p), y)
agg = collections.defaultdict(lambda: [0, 0.0, 0, 0.0, 0, 0, 0])
for (u, o), (p, y) in yr.items():
    if y not in ("true", "false") or not 2003 <= o // 12 <= 2022: continue
    qs = [q.get((u, o + 3 * i)) for i in range(4)]
    known = [x for x in qs if x and x[1] in ("true", "false")]
    comp = 1.0
    for x in qs:
        if x: comp *= 1 - x[0]
    anyq = any(x[1] == "true" for x in known)
    e = "2003-09" if o // 12 < 2010 else "2010-22"
    a = agg[e]; a[0] += 1; a[1] += p; a[2] += y == "true"; a[3] += 1 - comp; a[4] += anyq; a[5] += len(known); a[6] += (y == "true") != anyq
for e, (n, sp, c, cp, aq, nk, dis) in sorted(agg.items()):
    print(e, "rows", n, "year p %.3f rate %.3f | composed quarter-lead p %.3f, any-quarter onset %.3f, quarters resolved/row %.2f, year vs quarter outcome disagree %d" % (sp/n, c/n, cp/n, aq/n, nk/n, dis))
