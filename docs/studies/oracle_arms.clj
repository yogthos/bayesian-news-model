;; bnm-yc2, step 3: does a kind's theory layer miss for want of information
;; or for want of extraction? On each economic kind's theory inputs (its
;; own, bnm.kind-theory/decls, and the global layer, bnm.kind-theory/
;; global), refit at each December origin on t111-base's year country rows
;; (y as scored, known 24 months on), scored 2003-2022:
;;   A0 production: point-in-time reads, missing read as the mean, logit
;;   A2 lag oracle: every value as if published at its period's end
;;   A3 coverage split: A0 on rows with every input read vs some missing
;;   A4 extraction oracle: boosted trees (augur.trees) on the same reads
;;   A4s stumps (trees of depth 1): shapes for each input, no interactions
;;       (reports/Signal combinations before phase transitions.md, test (a):
;;       if stumps recover most of A4's gain over A0, the fix is the inputs'
;;       shapes, not their products)
;;   A6 shapes in the logit: each standardized input as z and hinges
;;       max(0, z - k) at k = -1, 0, 1, plus a missing flag (bnm-asp)
;;   A7 shapes and breadth: A6 plus the share of inputs stretched past 1 and
;;       past 2 (|z|), and the largest |z| (bnm-asp; graded breadth, NEWS-style)
;;   A5 step 2: logit on bnm.impute's filled inputs and the share missing
;;   and t111-base's own p beside them.
;; A1 (final vintage) is not run: data/slow/series.tsv holds one vintage,
;; close to the latest, so A0 already reads revised values at their real
;; publication lags.
;; jolt -A:tools -M docs/studies/oracle_arms.clj banking_crisis currency_crisis ...
(require '[bnm.kind-theory :as kt] '[bnm.named :as nm] '[bnm.slow-train :as st] '[bnm.signals :as sg]
         '[bnm.stocks :as stocks] '[bnm.theory :as th] '[bnm.impute :as im] '[bnm.geo :as geo]
         '[augur.logit :as lgt] '[augur.trees :as trees] '[clojure.java.io :as io] '[clojure.string :as str])

(defn month-of [d] (let [[y m] (str/split d #"-")] (+ (* 12 (parse-long y)) (dec (parse-long m)))))

(defn zero-lag-db
  "The table with every value available at its period's end."
  [paths variables vs]
  (let [tr (into {} (for [v variables] [(:name v) (:transform v)]))
        acc (volatile! {})]
    (doseq [p paths]
      (with-open [r (io/reader p)]
        (doseq [l (line-seq r) :let [[u v _ pend _ x] (str/split l #"\t" -1)] :when (contains? vs v)]
          (when-let [x (parse-double x)]
            (let [z (case (tr v) "log" (Math/log (Math/max x 1e-12)) "log1p" (Math/log1p (Math/max x 0.0)) x)
                  pm (month-of pend)]
              (vswap! acc update [u v] (fnil assoc (sorted-map)) pm z))))))
    (into {} (for [[k m] @acc] [k (stocks/series (for [[p z] m] [p p z]))]))))

(defn auc [ps]
  (let [pos (vec (sort (keep #(when (:y %) (:s %)) ps))) neg (vec (sort (keep #(when-not (:y %) (:s %)) ps)))
        n- (count neg)]
    (if (or (empty? pos) (zero? n-)) Double/NaN
        (/ (reduce + (for [a pos] (let [lt (count (take-while #(< % a) neg)) le (count (take-while #(<= % a) neg))]
                                    (+ lt (* 0.5 (- le lt))))))
           (* (count pos) n-)))))
(defn brier [ps] (/ (reduce + (map #(Math/pow (- (:s %) (if (:y %) 1.0 0.0)) 2) ps)) (count ps)))
(defn fmt [ps] (if (seq ps) (format "AUC %.3f Brier %.5f (n %d, onsets %d)" (auc ps) (brier ps) (count ps) (count (filter :y ps))) "-"))

(let [kinds *command-line-args*
      variables (st/read-variables "data/slow/variables.tsv")]
  (doseq [kind kinds]
    (let [ds (vec (concat (kt/decls kind) (when (kt/global-kinds kind) kt/global)))
          vs (kt/decl-vars ds)
          db0 (merge (nm/load-series "data/slow/series.tsv" variables {:real-lags true :vars vs})
                     (nm/load-series "data/derived/global-monthly.tsv" variables {:vars vs}))
          db2 (zero-lag-db ["data/slow/series.tsv" "data/derived/global-monthly.tsv"] variables vs)
          rows (with-open [r (io/reader (str "data/runs/t111-base/" kind "/forecasts.tsv"))]
                 (into {} (for [l (line-seq r) :let [[s u o p y] (str/split l #"\t" -1)]
                                :when (and (= s "year") (= 3 (count u)) (#{"true" "false"} y) (= 11 (mod (parse-long o) 12)))]
                            [[u (parse-long o)] {:y (= y "true") :p (parse-double p)}])))
          countries (sort (distinct (map first (keys rows))))
          os (vec (sort (distinct (map second (keys rows)))))
          w (count ds)
          raw0 (kt/transformed ds (:row (sg/assemble db0 ds)))
          raw2 (kt/transformed ds (:row (sg/assemble db2 ds)))
          parent (into {} (for [u countries :let [s (first (geo/parents u))] :when s] [u s]))
          raw5 (im/impute raw0 w countries os parent)
          zs (fn [raw width] (let [z (vec (for [i (range width)] (th/place-z (fn [u t] (some-> (raw u t) (nth i))) countries os 20)))]
                               (fn [u t] (mapv #(% u t) z))))
          z0 (zs raw0 w) z2 (zs raw2 w) z5 (zs raw5 (inc w))
          hinge (fn [u t] (let [r (raw0 u t) zz (z0 u t)]
                            (double-array (cons 1.0 (mapcat (fn [x zv] (let [v (double (or zv 0.0))]
                                                                         [v (max 0.0 (+ v 1.0)) (max 0.0 v) (max 0.0 (- v 1.0)) (if (nil? x) 1.0 0.0)]))
                                                            (or r (repeat w nil)) zz)))))
          breadth (fn [u t] (let [zz (keep identity (z0 u t)) n (max 1 (count zz))]
                              [(/ (count (filter #(> (Math/abs (double %)) 1.0) zz)) (double n))
                               (/ (count (filter #(> (Math/abs (double %)) 2.0) zz)) (double n))
                               (if (seq zz) (apply max (map #(Math/abs (double %)) zz)) 0.0)]))
          hb (fn [u t] (double-array (concat (seq (hinge u t)) (breadth u t))))
          xrow (fn [z u t] (double-array (cons 1.0 (map #(double (or % 0.0)) (z u t)))))
          nanrow (fn [u t] (double-array (map #(if % (double %) Double/NaN) (or (raw0 u t) (repeat w nil)))))
          train-of (fn [f t] (vec (for [[[u t'] {:keys [y]}] rows :when (<= (+ t' 24) t)] {:x (f u t') :y y :w 1.0})))
          scored (vec (for [t os :when (<= (* 12 2003) t (+ 11 (* 12 2022)))
                            :let [tr0 (train-of #(xrow z0 %1 %2) t) tr2 (train-of #(xrow z2 %1 %2) t)
                                  tr5 (train-of #(xrow z5 %1 %2) t) trt (train-of nanrow t)
                                  ok (and (some :y tr0) (some (complement :y) tr0))
                                  b0 (when ok (lgt/fit tr0 (inc w) {:prior 1.0}))
                                  b2 (when ok (lgt/fit tr2 (inc w) {:prior 1.0}))
                                  b5 (when ok (lgt/fit tr5 (+ 2 w) {:prior 1.0}))
                                  trh (train-of hinge t)
                                  bh (when ok (lgt/fit trh (inc (* 5 w)) {:prior 1.0}))
                                  trb (train-of hb t)
                                  bb (when ok (lgt/fit trb (+ 4 (* 5 w)) {:prior 1.0}))
                                  gb (when ok (trees/fit trt trees/defaults))
                                  gs (when ok (trees/fit trt (assoc trees/defaults :depth 1 :rounds 200)))]
                            :when ok
                            [[u t'] {:keys [y p]}] rows :when (= t t')]
                        {:y y :p p :a0 (lgt/predict b0 (xrow z0 u t)) :a2 (lgt/predict b2 (xrow z2 u t))
                         :a5 (lgt/predict b5 (xrow z5 u t)) :a6 (lgt/predict bh (hinge u t)) :a7 (lgt/predict bb (hb u t)) :a4 (trees/predict gb (nanrow u t)) :a4s (trees/predict gs (nanrow u t))
                         :missing (boolean (some nil? (raw0 u t)))}))
          as (fn [k ps] (map #(assoc % :s (k %)) ps))]
      (println "==" kind (count ds) "inputs")
      (println "  lattice t111-base     " (fmt (as :p scored)))
      (println "  A0 production         " (fmt (as :a0 scored)))
      (println "  A2 lag oracle         " (fmt (as :a2 scored)))
      (println "  A4 boosted trees      " (fmt (as :a4 scored)))
      (println "  A4s stumps (depth 1)  " (fmt (as :a4s scored)))
      (println "  A5 imputed + missing  " (fmt (as :a5 scored)))
      (println "  A6 hinges + missing   " (fmt (as :a6 scored)))
      (println "  A7 A6 + breadth       " (fmt (as :a7 scored)))
      (println "  A3 A0, all read       " (fmt (as :a0 (remove :missing scored))))
      (println "  A3 A0, some missing   " (fmt (as :a0 (filter :missing scored))))
      (println "  A3 lattice, all read  " (fmt (as :p (remove :missing scored))))
      (println "  A3 lattice, missing   " (fmt (as :p (filter :missing scored)))))))
