(ns bnm.reconcile
  "Coherent forecasts over the hierarchy (bnm-enb.11): do a country's
  children improve its forecast if the two are reconciled instead of blended?

  On a run's saved origins, each forecast is re-made, and for every country
  with tracked children (its rest leaf and dyads), three forecasts of the
  country's total are scored by CRPS in log space against what happened:

    base       the country's own simulated totals (the blend of its own rate
               and its children's, as the model forecasts it)
    bottom-up  the sum of its children's totals in the same simulation
    BUIS       the bottom-up draws reweighted by the base forecast's density
               at their sum and resampled: bottom-up importance sampling
               (Zambon, Azzimonti & Corani 2024, the bayesRecon package),
               which conditions the children's joint draws on the country's
               own forecast, so both levels agree

  The children's own CRPS before and after BUIS is scored as well.

    jolt -A:tools -M -m bnm.reconcile v3-q 20080221,20080522,... [sims]"
  (:require [bnm.geo :as geo]
            [bnm.math :as m]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [bnm.store :as store]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- L ^double [x] (Math/log1p (max 0.0 (double x))))

(defn- crps
  "CRPS of the sample xs (log1p units) for the outcome a."
  [xs a]
  (let [xs (double-array (sort (map L xs)))
        a (L a)
        n (alength xs)
        s1 (areduce xs i s 0.0 (+ s (Math/abs (- (aget xs i) a))))
        s2 (areduce xs i s 0.0 (+ s (* (aget xs i) (- (* 2 i) n -1))))]
    (- (/ s1 n) (/ s2 (* n (double n))))))

(defn buis-indices
  "Simulation indices resampled with weights: the density of the parent's
  sample `parent` (a Gaussian kernel density in log1p units, Silverman's
  bandwidth) at each simulation's children sum `sums`."
  [parent sums g]
  (let [lp (mapv L parent)
        n (count lp)
        mean (/ (reduce + lp) n)
        sd (Math/sqrt (/ (reduce + (map #(let [d (- % mean)] (* d d)) lp)) (max 1 (dec n))))
        bw (max 1e-3 (* 1.06 sd (Math/pow n -0.2)))
        dens (fn [x] (reduce + (map #(Math/exp (* -0.5 (let [z (/ (- x %) bw)] (* z z)))) lp)))
        ws (mapv #(dens (L %)) sums)
        total (reduce + ws)]
    (if (<= total 0.0)
      (vec (range (count sums)))
      (let [cum (vec (reductions + (map #(/ % total) ws)))]
        (vec (for [_ (range (count sums))]
               (let [u (m/next-double! g)]
                 (or (first (keep-indexed (fn [i c] (when (<= u c) i)) cum)) (dec (count cum))))))))))

(defn- recorded
  "{id actual-totals} from a run's rows file for a period."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[id _ ch _ _ actual] (str/split line #"\t")
                    c (.indexOf ^java.util.List ont/channels (keyword ch))
                    ^doubles a (or (acc id) (double-array model/C))]
                (aset a c (parse-double actual))
                (assoc acc id a)))
            {} (line-seq r))))

(defn -main [run labels & [sims]]
  (let [dir (str "data/runs/" run)
        sims (parse-long (or sims "100"))
        every (:every (clojure.edn/read-string (slurp (str dir "/args.edn"))))
        acc (atom {})]
    (doseq [lbl (str/split labels #",")]
      (let [mdl (store/read-file (str dir "/origin-" lbl ".edn"))
            rec (recorded (str dir "/rows/" lbl ".tsv"))
            fc (model/forecast mdl {:horizon every :sims sims :seed (parse-long lbl)})
            nodes (:nodes mdl)
            g (m/rng (parse-long lbl))]
        (doseq [[i node] (map-indexed vector nodes)
                :when (= :country (geo/kind (:id node)))
                :let [kids (vec (:children node))
                      act (rec (:id node))]
                :when (and act (seq kids))
                c (range model/C)
                :let [base (mapv #(model/total fc i % c) (range sims))
                      sums (mapv (fn [s] (reduce + (map #(model/total fc % s c) kids))) (range sims))
                      idx (buis-indices base sums g)
                      a (aget ^doubles act c)
                      ch (ont/channels c)]]
          (swap! acc update ch (fn [m] (merge-with + (or m {})
                                                   {:n 1 :base (crps base a) :bottom-up (crps sums a)
                                                    :buis (crps (mapv sums idx) a)})))
          ;; the children, before and after conditioning on the parent
          (doseq [k kids
                  :let [ka (some-> (rec (:id (nodes k))) (aget c))]
                  :when ka
                  :let [xs (mapv #(model/total fc k % c) (range sims))]]
            (swap! acc update [:children ch] (fn [m] (merge-with + (or m {})
                                                                 {:n 1 :base (crps xs ka) :buis (crps (mapv xs idx) ka)})))))
        (println lbl "done")))
    (println "Mean CRPS (log1p units) over countries with tracked children, and over their children")
    (doseq [[k {:keys [n base bottom-up buis]}] (sort-by (comp str key) @acc)]
      (println (format "  %-28s n=%6d  base %.4f  %s  BUIS %.4f" (str k) n (/ base n)
                       (if bottom-up (format "bottom-up %.4f" (/ bottom-up n)) "                ")
                       (/ buis n))))))
