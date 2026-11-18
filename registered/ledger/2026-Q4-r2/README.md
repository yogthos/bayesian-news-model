# Forward ledger issue 2026-Q4-r2

Forecasts issued on 2026-11-18 by the frozen base (the world without relations;
base of record t80-base) for every kind, place and window at each scale's
latest origin at or before 2026-Q4, from the data on disk at the issue date.
The registration is in docs/PHASE-CHANGES.md ("Registered, 11 November
2026: the forward ledger", and its amendment L1). manifest.edn pins the
code commit, the command, every input's SHA-256 (the inputs are archived
read-only in data/ledger-inputs/2026-Q4-r2/), the inputs that changed since the
previous issue, and the check that this code reproduces the previous
issue on its own archived inputs.

**These files must never be edited, reformatted or regenerated.** Nothing
is refit between issue and outcome. The scorer (tools/bnm/ledger.clj,
`score`) reads them from here and refuses any whose SHA-256 is not the
one registered in bnm.ledger/issued; the test suite hashes them too
(test/bnm/ledger_test.clj). Scores are written once, under scores/.

| file | bytes | SHA-256 |
|---|---|---|
| forecasts.tsv | 1594673 | 528b3ea81969b6aebf72fa9a33ac3179fef422642a9fed8e552a0ca14eff4452 |
| manifest.edn | 6522 | 2517fcd95dcb660c29bdab9845bb6c6001c0d566f9e7f7fda8343ee521816fb3 |

forecasts.tsv: one row per kind, scale and place. origin is a month
(year x 12 + month - 1), the window is (lo, hi] in months, opened is true
when the window opened before the issue date (those rows are for the
record and are not counted). p is the base's probability of an onset in
the window; clim, last12, share, rate and persist are the benchmarks'
probabilities and pers and wclim the watch list's persistence and
climatology scores, all from what was known at the origin (country rows
only).

To check by hand:

    shasum -a 256 registered/ledger/2026-Q4-r2/*
