;; bnm-1dw t110: why currency crises' theory fails. Refits the declared
;; theory (bnm.kind-theory) on t110's year country rows (y as the run
;; scored them, known 24 months after the origin) and prints its
;; coefficients on the standardized inputs at December origins, and the
;; theory's own AUC against the lattice's.
;; jolt -A:tools -M docs/studies/currency_theory.clj currency_crisis 2005 2012 2019 2021
(require '[bnm.kind-theory :as kt] '[bnm.named :as nm] '[bnm.slow-train :as st] '[bnm.signals :as sg]
         '[bnm.theory :as th] '[augur.logit :as lgt] '[clojure.java.io :as io] '[clojure.string :as str])
(let [[kind & years] *command-line-args*
      ds (kt/decls kind)
      db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv") {:real-lags true :vars (kt/vars [kind])})
      row (kt/transformed ds (:row (sg/assemble db ds)))
      rows (with-open [r (io/reader (str "data/runs/t110/" kind "/forecasts.tsv"))]
             (into {} (for [l (line-seq r) :let [[s u o p y] (str/split l #"\t" -1)]
                            :when (and (= s "year") (= 3 (count u)) (#{"true" "false"} y) (= 11 (mod (parse-long o) 12)))]
                        [[u (parse-long o)] {:y (= y "true") :p (parse-double p)}])))
      countries (sort (distinct (map first (keys rows))))
      os (range (+ 11 (* 12 1960)) (+ 11 (* 12 2023)) 12)
      zs (vec (for [i (range (count ds))] (th/place-z (fn [u t] (nth (row u t) i)) countries os 20)))
      inputs (fn [u t] (mapv #(% u t) zs))
      xrow (fn [u t] (double-array (cons 1.0 (map #(double (or % 0.0)) (inputs u t)))))
      train (vec (for [[[u t] {:keys [y]}] rows] {:x (xrow u t) :y y :w 1.0 :known-at (+ t 24) :t t :u u}))
      k (inc (count ds))
      auc (fn [ps] (let [pos (keep #(when (:y %) (:s %)) ps) neg (keep #(when-not (:y %) (:s %)) ps)]
                     (/ (reduce + (for [a pos b neg] (cond (> a b) 1.0 (= a b) 0.5 :else 0.0))) (* (count pos) (count neg)))))]
  (println "inputs" (mapv :name ds))
  (println "missing share by input, 2003-2022 rows:"
           (mapv (fn [i] (format "%.2f" (double (/ (count (filter (fn [[[u t] _]] (and (>= t (* 12 2003)) (nil? (nth (row u t) i)))) rows))
                                               (max 1 (count (filter (fn [[[_ t] _]] (>= t (* 12 2003))) rows)))))))
                 (range (count ds))))
  (doseq [y (map parse-long years) :let [t (+ 11 (* 12 y)) tr (filter #(<= (:known-at %) t) train)]]
    (println y "n" (count tr) "onsets" (count (filter :y tr)) "beta [icpt" (str/join " " (map (comp name :name) ds)) "]"
             (mapv #(Double/parseDouble (format "%.2f" %)) (lgt/fit tr k {:prior 1.0}))))
  (let [scored (for [t (range (+ 11 (* 12 2003)) (+ 11 (* 12 2023)) 12)
                     :let [tr (filter #(<= (:known-at %) t) train) beta (lgt/fit tr k {:prior 1.0})]
                     [[u t'] {:keys [y p]}] rows :when (= t t')]
                 {:y y :s (lgt/predict beta (xrow u t)) :p p})]
    (println "2003-2022 pooled AUC: theory alone" (format "%.3f" (auc scored)) " t110 lattice" (format "%.3f" (auc (map #(assoc % :s (:p %)) scored))))))
