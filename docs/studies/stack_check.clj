;; bnm-7yg.8.8: the lattice's :stack against R1's study. Runs each kind's
;; lattice on bnm.ledger/shadow-command (relations off, as the world run) and
;; compares its forecasts with <check-run>'s (t162) and with the study's
;; stack (docs/studies/stack_forecasts.py): December year country rows
;; 2003-2022 against the study's p, every other row against the run's.
;; With a fourth argument `reference`, on bnm.ledger/reference-shadow-command.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/stack_check.clj <study-dir> <check-run> [k1,k2] [reference]
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str])

(defn- read-tsv [path f]
  (into {} (keep f (str/split-lines (slurp path)))))

(let [[study check only method] *command-line-args*
      opts (wr/options (vec ((if (= "reference" method) lg/reference-shadow-command lg/shadow-command) "diag" lg/base-end)))
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})]
  (doseq [k (if (seq only) (str/split only #",") ["conflict_onset" "war_onset"])
          :let [sp (get specs k)
                res (loop [s (lt/start sp)] (if (> (:t s) (:end sp)) (lt/finish s) (recur (lt/step sp s))))
                mine (into {} (for [{:keys [scale place origin p]} (:forecasts res)] [[(name scale) place origin] p]))
                run (read-tsv (str "data/runs/" check "/" k "/forecasts.tsv")
                              (fn [l] (let [[s u o p] (str/split l #"\t")] [[s u (parse-long o)] (parse-double p)])))
                want (read-tsv (str study "/" k ".tsv")
                               (fn [l] (let [[u o p] (str/split l #"\t")] [["year" u (parse-long o)] (parse-double p)])))
                stacked (filter (fn [[[s u o] _]] (and (= "year" s) (= 3 (count u)) (= 11 (mod o 12)) (<= 2003 (quot o 12) 2022))) mine)
                d-study (keep (fn [[key p]] (when-let [q (want key)] (Math/abs (- p q)))) stacked)
                missing (count (remove (fn [[key _]] (want key)) stacked))
                others (remove (fn [[key _]] (want key)) mine)
                d-run (keep (fn [[key p]] (when-let [q (run key)] (when (not= p q) key))) others)
                moved (frequencies (map (fn [[s u o]] (cond (not= "year" s) :other-scale (not= 3 (count u)) :region (= 11 (mod o 12)) :december-country :else :other-month-country)) d-run))]]
    (println k "study rows compared" (count d-study) "max |lattice - study|" (if (seq d-study) (apply max d-study) "none")
             "| december country rows without a study p" missing
             "| rows differing from" check (count d-run) moved)))
