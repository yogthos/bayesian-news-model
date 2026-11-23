;; bnm-7ny: is currency crises' 9+ AUC loss in the theory's offset or in the
;; year node on top of it? Fits the theory on t137's inputs with and without
;; the four decay columns (bnm.theory/offsets) and compares, over the
;; December origins 2003-2022, the offset change for rows 9+ years past the
;; last known crisis, onset rows against quiet, and the offset's own AUC
;; over all rows; and the change in the country's gap to its parent region
;; (its offset minus bnm.theory/any-of over the region's countries), the
;; input the year node's contradiction group is built on.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/currency/offset_change.clj
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.kind-theory :as kt] '[bnm.theory :as th])
(def k "currency_crisis")
(def opts (wr/options (into (vec (lg/command "diag" lg/base-end)) ["--theory-decay" k])))
(def cats (wr/run-catalogues opts))
(def ks ["growth_collapse" "market_crash" k])
(def sp (get (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}}) k))
(defn auc [rs]
  ;; the share of (onset, quiet) pairs the onset row outranks, ties half
  (let [pos (map first (filter second rs)) neg (vec (sort (map first (remove second rs))))
        below (fn [p] (+ (count (take-while #(< % p) neg)) (* 0.5 (count (filter #(= % p) neg)))))]
    (/ (reduce + (map below pos)) (* (count pos) (count neg) 1.0))))
(let [{:keys [levels start end target kind-theory]} sp
      countries (second (first levels))
      os (filter #(zero? (mod (inc (- % start)) 12)) (range start (inc end)))
      inputs (:inputs kind-theory)
      n-in (* 5 (count (kt/decls k)))
      args {:places countries :origins os :prior 1.0 :raw (:raw kind-theory) :target (fn [u t] (target :year u t))}
      with (th/offsets (assoc args :inputs inputs))
      without (th/offsets (assoc args :inputs (fn [u t] (subvec (inputs u t) 0 n-in))))
      ons (:onsets (cats k))
      parent (:parent sp)
      members (group-by parent countries)
      region (fn [off u t] (th/any-of (keep #(off [% t]) (members (parent u)))))
      rs (for [t os :when (<= 2003 (quot t 12) 2022) u countries
               :let [tg (target :year u t) a (without [u t]) b (with [u t])]
               :when (and tg (some? (:y tg)) a b)]
           (let [lo (some->> (get ons u) (filter #(<= % t)) seq (apply max))]
             {:y (:y tg) :a a :b b :nine (and lo (>= (- t lo) 108))
              :ga (- a (or (region without u t) a)) :gb (- b (or (region with u t) b))}))
      mean (fn [f xs] (/ (reduce + (map f xs)) (max 1 (count xs))))]
  (println "rows" (count rs) "offset AUC without decay" (format "%.4f" (auc (map (juxt :a :y) rs))) "with" (format "%.4f" (auc (map (juxt :b :y) rs))))
  (doseq [[lbl sel] [["9+ onset" #(and (:nine %) (:y %))] ["9+ quiet" #(and (:nine %) (not (:y %)))]
                     ["other onset" #(and (not (:nine %)) (:y %))] ["other quiet" #(and (not (:nine %)) (not (:y %)))]]]
    (let [xs (filter sel rs)]
      (println (format "%-12s rows %4d  offset without %.3f  with %.3f  change %+.4f  gap to region %+.3f -> %+.3f" lbl (count xs) (mean :a xs) (mean :b xs) (mean #(- (:b %) (:a %)) xs) (mean :ga xs) (mean :gb xs))))))
