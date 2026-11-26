;; bnm-7yg.2: every kind's year country node, row by row, on a run's
;; command, for the reference models of docs/studies/reference.py. Builds the
;; specs as the world run does (bnm.world-run/pipeline-specs over all the
;; kinds, relations off), runs each kind's lattice with bnm.lattice's :tap and
;; writes, per kind, for country rows at every December origin: the outcome,
;; the month it became known, the mean offset, the issued p and the inputs the
;; node's models read (x). First checks the issued year forecasts against the
;; run's own forecasts.tsv, 2003-2022.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/reference_tap.clj <out-dir> <check-run> [k1,k2] [added args]
;; The base command is the ledger's (bnm.ledger/command); the fourth argument
;; adds arguments to it, space-separated.
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str] '[clojure.java.io :as io])
(let [[out check only added] *command-line-args*
      args (into (vec (lg/command "diag" lg/base-end)) (when (seq added) (str/split added #" ")))
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      run-ks (if (seq only) (str/split only #",") ks)]
  (.mkdirs (io/file out))
  (doseq [k run-ks
          :let [sp0 (get specs k)
                rows (atom [])
                sp (assoc sp0 :tap (fn [{:keys [scale level t] :as r}]
                                     (when (and (= :year scale) (= :country level) (= 11 (mod t 12)))
                                       (swap! rows conj (select-keys r [:place :t :x :o])))))
                end (:end sp)
                res (loop [s (lt/start sp)] (if (> (:t s) end) (lt/finish s) (recur (lt/step sp s))))
                issued (into {} (for [{:keys [scale place origin p]} (:forecasts res)
                                      :when (and (= :year scale) (= 3 (count place)) (= 11 (mod origin 12)))]
                                  [[place origin] p]))
                world (into {} (for [l (str/split-lines (slurp (str "data/runs/" check "/" k "/forecasts.tsv")))
                                     :let [[s u o p] (str/split l #"\t")]
                                     :when (and (= "year" s) (= 3 (count u)))]
                                 [[u (parse-long o)] (parse-double p)]))
                diffs (keep (fn [[[u o :as key] p]] (when (<= 2003 (quot o 12) 2022) (when-let [q (world key)] (Math/abs (- p q))))) issued)]]
    (println k "rows" (count @rows) "checked 2003-2022" (count diffs) "max |p - world p|" (if (seq diffs) (apply max diffs) "none"))
    (with-open [w (io/writer (str out "/" k ".tsv"))]
      (doseq [{:keys [place t x o]} @rows
              :let [{:keys [y known-at]} ((:target sp) :year place t)]
              :when (some? y)]
        (.write w (str place "\t" t "\t" y "\t" known-at "\t" (or o "") "\t" (issued [place t]) "\t"
                       (str/join "\t" (map #(if (some? %) (double %) "") x)) "\n"))))))
