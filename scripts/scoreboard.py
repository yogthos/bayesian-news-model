#!/usr/bin/env python3
"""The acceptance scoreboard (docs/PHASE-CHANGES.md), from anticipation files.

    scripts/scoreboard.py MODEL <run>/anticipation-tests25.txt [<run>/anticipation-vs-named-tests25.txt ...]

A cell (kind, window, era, hard rows) passes when MODEL gains over every
bench at r@5% or r@10% with the interval's lower bound above zero (score
with --tests m for the Bonferroni bound over the m groups and models
tried), the same lower bound stays above zero with each region left out
(loro), it loses nowhere with the interval below zero, and matched
concordance is above 0.5. Counted cells: lead (2-4 quarters) and w2 (5-8)
in 2003-14 and 2015-22; 2023-26 is the locked confirmation period, shown
when scored with --confirm true but never counted."""
import re, sys
from collections import defaultdict

num = r'([+-]?\d+\.\d+)'
cell = re.compile(r'(r@5%|r@10%|U) ' + num + ' ' + num + r' \[' + num + ' ' + num + r'\]')

def read(path, model):
    out = {}; block = None
    for line in open(path):
        if not line.startswith(' '):
            block = line.split()
            if len(block) > 2 and '/' in block[2]:
                sys.exit(f"{path}: blocks split by state ({block[2]}); score without --split for the scoreboard")
            continue
        if not block or block[-1] != 'hard': continue
        m = line.split()
        if m[0] != model: continue
        cs = {c.group(1): (float(c.group(2)), float(c.group(3)), float(c.group(4)), float(c.group(5)))
              for c in cell.finditer(line)}
        pos = int(re.search(r'pos= *(\d+)', line).group(1))
        mt = float(re.search(r'matched (\d\.\d+)', line).group(1))
        lo = re.search(r'loro ' + num + ' ' + num, line)
        loro = (float(lo.group(1)), float(lo.group(2))) if lo else None
        out[(block[0], block[1], block[2])] = (cs, pos, mt, loro)
    return out

model = sys.argv[1]
benches = [read(p, model) for p in sys.argv[2:]]
kinds = sorted({k for b in benches for (k, _, _) in b})
total = passed = 0
print(f"{model}: hard rows; cell = r@5/r@10 value, gain lo..hi per bench; PASS needs a gain over every bench")
for k in kinds:
    for w in ('lead', 'w2', 'w1'):
        for era in ('03-14', '15-22', '23-26'):
            parts = []; ok = True
            for b in benches:
                r = b.get((k, w, era))
                if not r: ok = False; parts.append('missing'); continue
                cs, pos, mt, loro = r
                # a gain at a budget counts only if it also holds with each region left out
                gain = any(cs[x][2] > 0 and (loro is None or loro[i] > 0) for i, x in enumerate(('r@5%', 'r@10%')))
                loss = any(v[3] < 0 for v in cs.values())
                ok = ok and gain and not loss and mt > 0.5
                parts.append(f"{cs['r@5%'][0]:.2f}/{cs['r@10%'][0]:.2f} "
                             f"[{cs['r@10%'][2]:+.2f},{cs['r@10%'][3]:+.2f}] m{mt:.2f}")
            pos = benches[0].get((k, w, era), (0, 0))[1]
            if w != 'w1' and era != '23-26':
                total += 1; passed += ok
            print(f"{k:15s} {w:4s} {era} n+={pos:3d} {'PASS' if ok else '    '} " + ' | '.join(parts))
print(f"passed {passed}/{total} counted cells (lead, w2)")
