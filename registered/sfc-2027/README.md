# Registered files of the 2027 rates score

The three files the later confirmation of the rate channel depends on
(bead bnm-4hv.16; docs/COUPLED-SYSTEMS.md 9h, the amendment to the later
confirmation and amendment A2). They were written under data/derived/sfc/,
which is outside git, and copied here byte for byte on 2026-11-10 with
unchanged hashes, before any target of data year 2025 or later was read.

**These files must never be edited, reformatted or regenerated.** The
scorer (tools/bnm/sfc_rates_confirm.clj) reads them from here, refuses
any of them whose SHA-256 is not the one below, and refuses if a copy
under data/derived/sfc/ differs from the one here. The test suite hashes
them too (test/bnm/sfc_rates_confirm_test.clj).

| file | bytes | SHA-256 | what it is |
|---|---|---|---|
| rates-frozen-v2.edn | 230144 | 6fa02f4f4d827d6c038dcbd7a1d21bfa3dcd47c5869f1ddcc44f18d8cad32446 | the primary frozen forecasts, 875 rows; carries the verdict |
| rates-frozen.edn | 230171 | 0339880a359941334046d651966facd9d2473811fd205c77143111997eb7ccaf | the first frozen forecasts (v1, pre-fix core), the same 875 rows; scored and reported, no verdict |
| rates-test.edn | 2635132 | 226f71e10ff406aa3f7e87af1231c383b2f3227ac0acdbd4c58d835aa402d52b | 9h's test output; its test split is what scripts/sfc-rates-null.py fits the null interval's error model to |

Where each was registered:

- rates-frozen.edn: frozen at 16e7ae9 (9h's result), the test registered
  in 6566f6d; its hash is in 9h's result.
- rates-test.edn: 9h's run on 6566f6d (result recorded in 16e7ae9); its
  hash pinned with the scoring rule in c3bd38e (the null-shift interval
  accepted in ddbaa58).
- rates-frozen-v2.edn: written by rates-freeze at 9b42691 on the first
  freeze's inputs; made primary by amendment A2, 38ee92d.
- The scorer checks the hashes since 3ba03db, both frozen files since
  19099b1.

To check them by hand:

    shasum -a 256 registered/sfc-2027/*.edn
