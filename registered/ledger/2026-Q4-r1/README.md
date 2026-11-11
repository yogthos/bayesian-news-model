# Forward ledger issue 2026-Q4-r1

Forecasts issued on 2026-11-11 by the frozen base (the world without relations;
base of record t50-world-norel) for every kind, place and window at each scale's
latest origin at or before 2026-Q4, from the data on disk at the issue date.
The registration is in docs/PHASE-CHANGES.md ("Registered, 11 November
2026: the forward ledger", and its amendment L1). manifest.edn pins the
code commit, the command, every input's SHA-256 (the inputs are archived
read-only in data/ledger-inputs/2026-Q4-r1/), the inputs that changed since the
previous issue, and the check that this code reproduces the previous
issue on its own archived inputs.

2026-Q4-baselines.tsv holds the rate and persistence baselines for issue 2026-Q4's rows,
computed from 2026-Q4's archived inputs by the run that reproduced it.

**These files must never be edited, reformatted or regenerated.** Nothing
is refit between issue and outcome. The scorer (tools/bnm/ledger.clj,
`score`) reads them from here and refuses any whose SHA-256 is not the
one registered in bnm.ledger/issued; the test suite hashes them too
(test/bnm/ledger_test.clj). Scores are written once, under scores/.

| file | bytes | SHA-256 |
|---|---|---|
| 2026-Q4-baselines.tsv | 606751 | 432ea3cf6457771a075f56641f9fe1531930408b99d9b6ce7b4ffbfb03d234b8 |
| forecasts.tsv | 1599851 | bdb361c83ff63d11a7451e0c4cc6339fa0f82b1e671c3523722dae6524f0a576 |
| manifest.edn | 7420 | cf557fedb45171b1b40cdca1b28f15f4651520a9720ff6d39af974342acedaf3 |

forecasts.tsv: one row per kind, scale and place. origin is a month
(year x 12 + month - 1), the window is (lo, hi] in months, opened is true
when the window opened before the issue date (those rows are for the
record and are not counted). p is the base's probability of an onset in
the window; clim, last12, share, rate and persist are the benchmarks'
probabilities and pers and wclim the watch list's persistence and
climatology scores, all from what was known at the origin (country rows
only).

To check by hand:

    shasum -a 256 registered/ledger/2026-Q4-r1/*
