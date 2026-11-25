;; A kind's year node, row by row (bnm.lattice :tap), with and without
;; t137's theory decay. Runs the kind alone on the base command (the flags
;; that name other kinds taken out; --without-relations true, so it runs as
;; in the world) and writes, for country rows at December origins
;; 2003-2022: the outcome, the mean offset, and each model group's log odds
;; and weight. First checks the issued year forecasts against a world run's
;; forecasts.tsv. Generalizes docs/studies/currency/node_tap.clj.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/aftermath/node_tap.clj <kind> <base|decay> <out.tsv> <check-run>
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str])
(let [[k variant out check] *command-line-args*
      drop-flag (fn [args f] (let [i (.indexOf ^java.util.List args f)] (if (neg? i) args (into (subvec args 0 i) (subvec args (+ i 2))))))
      args (reduce drop-flag (vec (lg/command "diag" lg/base-end)) ["--aftermath-gauges" "--direction-gauges" "--direction-at" "--theory-decay"])
      args (cond-> args (= "decay" variant) (into ["--theory-decay" k]))
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      ks [k]
      sp0 (get (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}}) k)
      rows (atom [])
      sp (assoc sp0 :tap (fn [{:keys [scale level t] :as r}]
                           (when (and (= :year scale) (= :country level) (= 11 (mod t 12)) (<= 2003 (quot t 12) 2022))
                             (swap! rows conj (select-keys r [:place :t :o :f :w])))))
      end (:end sp)
      res (loop [s (lt/start sp)] (if (> (:t s) end) (lt/finish s) (recur (lt/step sp s))))
      issued (into {} (for [{:keys [scale place origin p]} (:forecasts res)
                            :when (and (= :year scale) (= 3 (count place)) (= 11 (mod origin 12)) (<= 2003 (quot origin 12) 2022))]
                        [[place origin] p]))
      world (into {} (for [l (str/split-lines (slurp (str "data/runs/" check "/" k "/forecasts.tsv")))
                           :let [[s u o p] (str/split l #"\t")]
                           :when (and (= "year" s) (= 3 (count u)))]
                       [[u (parse-long o)] (parse-double p)]))
      diffs (keep (fn [[key p]] (when-let [q (world key)] (Math/abs (- p q)))) issued)
      groups (sort (keys (:f (first @rows))))]
  (println k variant "rows" (count @rows) "matched" (count diffs) "max |p - world p|" (if (seq diffs) (apply max diffs) "none"))
  (with-open [wr (clojure.java.io/writer out)]
    (.write wr (str "place\tt\ty\to\tp\t" (str/join "\t" (mapcat (fn [g] [(str "f:" (name g)) (str "w:" (name g))]) groups)) "\n"))
    (doseq [{:keys [place t o f w]} @rows
            :let [y (:y ((:target sp) :year place t))]
            :when (some? y)]
      (.write wr (str place "\t" t "\t" y "\t" o "\t" (issued [place t]) "\t"
                      (str/join "\t" (mapcat (fn [g] [(f g) (get w g 0.0)]) groups)) "\n")))))
