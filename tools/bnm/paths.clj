(ns bnm.paths
  "Where the derived artefacts the hazards read live: one place, written by
  the readings pipeline (scripts/readings.sh) and read by the tools, so no
  tool names another's run.")

(def derived "data/derived/")

(def trader-inputs (str derived "trader-inputs.tsv")) ; bnm.trader-export
(def regimes (str derived "regimes.tsv"))             ; scripts/regime-hmm.py
(def lppls (str derived "lppls.tsv"))                 ; scripts/lppls-confidence.py
(def small-fall-transfer (str derived "small-fall-transfer.tsv")) ; bnm.scales
(def crisis-betas (str derived "crisis-betas.tsv"))   ; bnm.macrohistory
(def world-intensity (str derived "world-intensity.tsv")) ; bnm.waves
