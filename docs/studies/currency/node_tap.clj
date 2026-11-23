;; bnm-7ny: currency crises' year node, row by row, with and without t137's
;; decay. Runs the currency kind alone on the base command (the flags that
;; name other kinds taken out; --without-relations true, so the kind runs as
;; in the world) with the lattice's :tap, and writes, for country rows at
;; December origins 2003-2022: the mean offset, each model's log odds, the
;; weights, the outcome and the contradiction group's inputs. First checks
;; the run's issued year forecasts against the world run's forecasts.tsv.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/currency/node_tap.clj <variant> <out.tsv> <check-run>
;;   variant: base (t137-base's currency) or decay (t137's)
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str])
(def k "currency_crisis")
(let [[variant out check] *command-line-args*
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
                             (swap! rows conj (dissoc r :scale :level)))))
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
      lay (lt/layout-of sp :year 0)
      cix (vec (sort (get-in lay [:groups :contradiction])))]
  (println variant "rows" (count @rows) "issued" (count issued) "matched" (count diffs) "max |p - world p|" (if (seq diffs) (apply max diffs) "none"))
  (println "contradiction inputs" cix "groups" (keys (:groups lay)))
  (with-open [wr (clojure.java.io/writer out)]
    (.write wr "place\tt\ty\to\tf_contradiction\tf_regime\tf_all\tw_contradiction\tw_regime\tcx\n")
    (doseq [{:keys [place t x o f w]} @rows
            :let [y (:y ((:target sp) :year place t))]
            :when (some? y)]
      (.write wr (str place "\t" t "\t" y "\t" o "\t" (f :contradiction) "\t" (f :regime) "\t" (f :all) "\t"
                     (get w :contradiction 0.0) "\t" (get w :regime 0.0) "\t" (str/join "," (map #(nth x %) cix)) "\n")))))
