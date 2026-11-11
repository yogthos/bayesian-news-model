# Forward ledger issue 2026-Q4

Forecasts issued on 2026-11-11 by the frozen base (t48-world-norel, the world without
relations) for every kind, place and window at each scale's latest origin
at or before 2026-Q4, from the data on disk at the issue date. The registration
is in docs/PHASE-CHANGES.md ("Registered, 11 November 2026: the forward
ledger"). manifest.edn pins the code commit, the command, every input's
SHA-256 and the check that the run reproduces the base of record.

**These files must never be edited, reformatted or regenerated.** Nothing
is refit between issue and outcome. The scorer (tools/bnm/ledger.clj,
`score`) reads them from here and refuses any whose SHA-256 is not the
one registered in bnm.ledger/issued; the test suite hashes them too
(test/bnm/ledger_test.clj). Scores are written once, under scores/.

| file | bytes | SHA-256 |
|---|---|---|
| forecasts.tsv | 1290303 | 70e31c27c8bf7afe9d20fe4c6befcb3def80e32c19c99fd3b08c0ab0f5d4968d |
| manifest.edn | 4040 | 2eb4b6a5fe1e1fe4227983e8307e028243d24831f2abd9b0e439dc9a3e676d8a |

forecasts.tsv: one row per kind, scale and place. origin is a month
(year x 12 + month - 1), the window is (lo, hi] in months, opened is true
when the window opened before the issue date (those rows are for the
record and are not counted). p is the base's probability of an onset in
the window; clim, last12 and share are the benchmarks' probabilities and
pers and wclim the watch list's persistence and climatology scores, all
from what was known at the origin (country rows only).

To check by hand:

    shasum -a 256 registered/ledger/2026-Q4/*
