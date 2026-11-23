;; bnm-7ny: currency crises' theory slopes with and without t137's decay.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/currency/slopes.clj [t138]
;; Builds the currency spec both ways (bnm.world-run/pipeline-specs on the
;; base command), refits the theory as bnm.kind-theory/with-theory does
;; (bnm.theory/fits) and prints the slopes on each input's z column (shape
;; column 0) and the decay columns, averaged over the December origins
;; 2003-2022.
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.kind-theory :as kt] '[bnm.theory :as th])
(def k "currency_crisis")
(defn spec [extra]
  (let [opts (wr/options (into (vec (lg/command "diag" lg/base-end)) extra))
        cats (wr/run-catalogues opts)
        ks ["growth_collapse" "market_crash" k]]
    (get (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}}) k)))
(def names (mapv :name (kt/decls k)))
(doseq [[label extra] (case (first *command-line-args*)
                        "t138" [["t138" ["--theory-decay" "currency_crisis" "--theory-decay-precision" "10"]]]
                        [["t136-base" []] ["t137" ["--theory-decay" "currency_crisis"]]])]
  (let [{:keys [scales levels start end target kind-theory]} (spec extra)
        countries (second (first levels))
        os (filter #(zero? (mod (inc (- % start)) 12)) (range start (inc end)))
        fits (th/fits {:places countries :origins os :prior 1.0 :inputs (:inputs kind-theory) :raw (:raw kind-theory)
                       :penalties (:penalties kind-theory) :target (fn [u t] (target :year u t))})
        sel (filter (fn [[t _]] (<= 2003 (quot t 12) 2022)) fits)
        mean (fn [i] (/ (reduce + (map (fn [[_ ^doubles b]] (aget b i)) sel)) (count sel)))
        width (dec (alength ^doubles (val (first sel))))
        zcol (fn [j] (inc (* 5 j)))]
    (println "==" label "columns" width "origins" (count sel) "intercept" (format "%.3f" (mean 0)))
    (println "   z slopes:" (apply str (for [[j n] (map-indexed vector names)] (format " %s %.3f" (name n) (mean (zcol j))))))
    (when (> width (* 5 (count names)))
      (println "   past the inputs:" (apply str (for [i (range (inc (* 5 (count names))) (inc width))] (format " %.3f" (mean i))))))
    (flush)))
