"""bnm-7yg.8.8: R1's stack forecasts (docs/studies/combination.py's
stack_forecast_osz) written per kind, place<TAB>origin<TAB>p, for
docs/studies/stack_check.clj to compare with the lattice's :stack.

  python3 -I docs/studies/stack_forecasts.py <tap-dir> <out-dir> [k1,k2] [stack|reference]

With `reference`, R1's reference logit (reference_forecast_osz) instead.
"""
import importlib.util
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("combination", os.path.join(HERE, "combination.py"))
cb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cb)

if __name__ == "__main__":
    tap, out = sys.argv[1], sys.argv[2]
    kinds = sys.argv[3].split(",") if len(sys.argv) > 3 else ["conflict_onset", "war_onset"]
    os.makedirs(out, exist_ok=True)
    for k in kinds:
        d = cb.read(os.path.join(tap, k + ".tsv"))
        p = cb.reference_forecast_osz(d) if len(sys.argv) > 4 and sys.argv[4] == "reference" else cb.stack_forecast_osz(d)
        with open(os.path.join(out, k + ".tsv"), "w") as fh:
            for u, o, q in zip(d["place"], d["origin"], p):
                if not math.isnan(q):
                    fh.write(f"{u}\t{int(o)}\t{float(q)!r}\n")
        print(k, int((~__import__("numpy").isnan(p)).sum()))
