;; bnm-7yg.3: the year node's averaging weights by group model, mean over
;; country rows, at the December origins asked for, per kind, on the ledger
;; command plus added arguments (bnm.lattice's :tap).
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/year_weights.clj <k1,k2> <y1,y2> [added args] [EDN merged into --set]
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str])
(let [[only years added extra] *command-line-args*
      base (vec (lg/command "diag" lg/base-end))
      i (inc (.indexOf base "--set"))
      base (cond-> base (seq extra) (assoc i (pr-str (merge (read-string (base i)) (read-string extra)))))
      args (into base (when (seq added) (str/split added #" ")))
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      want (set (map #(+ 11 (* 12 (parse-long %))) (str/split years #",")))]
  (doseq [k (str/split only #",")
          :let [sp0 (get specs k)
                rows (atom [])
                sp (assoc sp0 :tap (fn [{:keys [scale level t w]}]
                                     (when (and (= :year scale) (= :country level) (want t))
                                       (swap! rows conj [t w]))))
                end (:end sp)]]
    (loop [s (lt/start sp)] (when (<= (:t s) end) (recur (lt/step sp s))))
    (doseq [[t rs] (sort-by key (group-by first @rows))
            :let [n (count rs)
                  mean (apply merge-with + (map second rs))
                  top (take 4 (sort-by (comp - val) mean))]]
      (println k (quot t 12) (str/join "  " (for [[m v] top] (format "%s %.2f" (name m) (/ v n))))))))
