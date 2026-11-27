;; bnm-7yg.6: sovereign defaults' own-history inputs as the year node reads
;; them (bnm.lattice-banking/history on the run's catalogue), per country
;; at the December origins 2003-2022 with the year target: place, year, y,
;; the catalogue's from (month), the last onset known at the origin (month,
;; blank if none), since-last, own10, own30. On the ledger command. For
;; sovereign_history.py.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/sovereign_history.clj > rows.tsv
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice-banking :as lb] '[clojure.string :as str])

(let [kind "sovereign_default"
      opts (wr/options (vec (lg/command "diag" lg/base-end)))
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      {:keys [levels target]} (get specs kind)
      cat (get cats kind)
      countries (second (first levels))]
  (println (str/join "\t" ["place" "year" "y" "from" "last" "since_last" "own10" "own30"]))
  (doseq [u countries
          yr (range 2003 2023)
          :let [t (+ 11 (* 12 yr))
                tg (target :year u t)
                h (#'lb/history cat u t)]
          :when (and tg h)]
    (println (str/join "\t" [u yr (if (:y tg) 1 0) (get-in cat [:from u]) (or (last (lb/known-onsets cat u t)) "")
                             (nth h 0) (nth h 1) (nth h 2)]))))
