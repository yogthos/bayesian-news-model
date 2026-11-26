;; bnm-ris: banking's year node, row by row, on the base of record. Runs the
;; banking kind alone on the base command (the flags that name only other
;; kinds taken out; --without-relations true, so the kind runs as in the
;; world) with the lattice's :tap, and writes, for country rows at December
;; origins 2003-2022: the outcome, the mean offset, each group's log odds and
;; weight. First checks the issued year forecasts against the world run's.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/banking/node_tap.clj <out.tsv> <check-run>
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str])
(def k "banking_crisis")
(let [[out check] *command-line-args*
      drop-flag (fn [args f] (let [i (.indexOf ^java.util.List args f)] (if (neg? i) args (into (subvec args 0 i) (subvec args (+ i 2))))))
      args (reduce drop-flag (vec (lg/command "diag" lg/base-end))
                   ["--kind-theory" "--shapes" "--global-theory" "--global-driver-precision" "--impute" "--aftermath-gauges"
                    "--direction-gauges" "--direction-at" "--theory-decay" "--release-calendar"])
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      ks [k]
      sp0 (get (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}}) k)
      rows (atom [])
      sp (assoc sp0 :tap (fn [{:keys [scale level t] :as r}]
                           (when (and (= :year scale) (= :country level) (= 11 (mod t 12)) (<= 2003 (quot t 12) 2022))
                             (swap! rows conj (dissoc r :scale :level :x)))))
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
      gs (sort (distinct (mapcat #(keys (:f %)) @rows)))]
  (println "rows" (count @rows) "issued" (count issued) "matched" (count diffs) "max |p - world p|" (if (seq diffs) (apply max diffs) "none"))
  (println "groups" gs "tap keys" (sort (keys (first @rows))))
  (with-open [wr (clojure.java.io/writer out)]
    (.write wr (str "place\tt\ty\tp\to\t" (str/join "\t" (map #(str "f_" (name %)) gs)) "\t" (str/join "\t" (map #(str "w_" (name %)) gs)) "\n"))
    (doseq [{:keys [place t o f w]} @rows
            :let [y (:y ((:target sp) :year place t))]
            :when (some? y)]
      (.write wr (str place "\t" t "\t" y "\t" (issued [place t]) "\t" o "\t"
                     (str/join "\t" (map #(get f % "") gs)) "\t" (str/join "\t" (map #(get w % 0.0) gs)) "\n")))))
