(ns bnm.sequencing
  "Which kinds of event lead which (docs/WORLD-MODEL.md, step 1), on onsets
  before 2003 only, by year, every ordered pair of kinds (bnm.kinds):

  - lead-lag: the rate of j's onsets at each lag around k's onsets, against
    j's rate in k's calm years (more than five from any k onset), in the
    years both catalogues cover;
  - the lagged test: does j's past predict k given k's own past? A logit
    of k's onsets on k's own excursion kernels (one and five years) with
    and without j's, the likelihood ratio and j's coefficients; each kernel
    an excursion above the country's own rate so far, so a country prone to
    both kinds does not by that alone make one lead the other.

    jolt -A:tools -M -m bnm.sequencing

  Writes data/runs/sequencing/lead-lag.tsv, tests.tsv and graph.edn (the
  candidate edges), and prints the tables."
  (:require [augur.logit :as lg]
            [bnm.kinds :as kinds]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn kernel
  "{year excursion} over `years`: the onsets (a set of years) of the years
  before each, decaying exp(-(y - 1 - t)/tau), less what the place's own
  rate so far (its onsets over the years before y) would give,
  r / (1 - exp(-1/tau)). Causal: subtracting the place's mean over all its
  years would use the future (with a fixed total, a dense past means a
  sparse future) and make one kind seem to lead another."
  [onsets tau years]
  (let [lo (first years)
        level (/ 1.0 (- 1.0 (Math/exp (/ -1.0 tau))))]
    (into {} (for [y years
                   :let [past (filter #(and (<= lo %) (< % y)) onsets)
                         raw (reduce + (for [t past] (Math/exp (- (/ (- y 1 t) tau)))))
                         rate (/ (count past) (double (max 1 (- y lo))))]]
               [y (- raw (* rate level))]))))

(defn- both-covered [jc kc u]
  (let [[a b] (get-in jc [:cover u]) [c d] (get-in kc [:cover u])]
    (when (and a c) (let [lo (max a c) hi (min b d)] (when (<= lo hi) [lo hi])))))

(defn lead-lag
  "{lag ratio}: j's onset rate at each lag from k's onsets over its rate in
  k's calm years. Catalogues are {:onsets {u #{years}} :cover {u [from to]}}."
  [jc kc lags]
  (when (and jc kc)
    (let [us (keys (:cover kc))
          calm (for [u us :let [[lo hi] (both-covered jc kc u)] :when lo
                     y (range lo (inc hi))
                     :when (not-any? #(<= (Math/abs (long (- y %))) 5) (get-in kc [:onsets u]))]
                 (if (contains? (get-in jc [:onsets u]) y) 1.0 0.0))
          base (when (seq calm) (/ (reduce + calm) (count calm)))]
      (when (and base (pos? base))
        (into (sorted-map)
              (for [lag lags
                    :let [hits (for [u us :let [[lo hi] (both-covered jc kc u)] :when lo
                                     y (get-in kc [:onsets u]) :let [z (+ y lag)]
                                     :when (and (<= lo y hi) (<= lo z hi))]
                                 (if (contains? (get-in jc [:onsets u]) z) 1.0 0.0))]
                    :when (seq hits)]
                [lag (/ (/ (reduce + hits) (count hits)) base)]))))))

(defn- loglik [beta rows]
  (reduce + (for [{:keys [x y]} rows :let [p (lg/predict beta x)]]
              (Math/log (Math/max 1e-12 (if y p (- 1.0 p)))))))

(defn lagged-test
  "{:lr :coef :n :events}: the likelihood ratio of j's one- and five-year
  excursion kernels added to a logit of k's onsets on its own, at the years
  both cover (k at risk: no k onset the year before), and j's coefficients."
  [jc kc]
  (let [rows (vec (for [u (keys (:cover kc)) :let [[lo hi] (both-covered jc kc u)] :when lo
                        :let [ys (range lo (inc hi))
                              ko (get-in kc [:onsets u] #{}) jo (get-in jc [:onsets u] #{})
                              k1 (kernel ko 1.0 ys) k5 (kernel ko 5.0 ys) j1 (kernel jo 1.0 ys) j5 (kernel jo 5.0 ys)]
                        y ys :when (not (contains? ko (dec y)))]
                    {:x [1.0 (k1 y) (k5 y) (j1 y) (j5 y)] :y (contains? ko y) :w 1.0}))
        restricted (mapv #(update % :x (fn [x] (subvec x 0 3))) rows)]
    (if (< (count (filter :y rows)) 5)
      {:lr 0.0 :coef [0.0 0.0] :n (count rows) :events (count (filter :y rows))}
      (let [bf (lg/fit rows 5 {:prior 1e-3})
            br (lg/fit restricted 3 {:prior 1e-3})]
        {:lr (* 2.0 (- (loglik bf rows) (loglik br restricted)))
         :coef [(aget ^doubles bf 3) (aget ^doubles bf 4)]
         :n (count rows) :events (count (filter :y rows))}))))

(def theory
  "Edges the literature names (docs/WORLD-MODEL.md): economic shocks to
  unrest and coups (Ponticelli & Voth; Burke & Leigh; Ciccone), the crisis
  mutations (Laeven & Valencia; Candelon et al.), the political cycles."
  #{["growth_collapse" "coup"] ["growth_collapse" "conflict_onset"] ["growth_collapse" "protest_surge"]
    ["food_price_shock" "protest_surge"] ["food_price_shock" "violence_surge"]
    ["fiscal_consolidation" "protest_surge"] ["fiscal_consolidation" "violence_surge"]
    ["inflation_crisis" "protest_surge"] ["inflation_crisis" "coup"]
    ["banking_crisis" "currency_crisis"] ["banking_crisis" "sovereign_default"] ["currency_crisis" "banking_crisis"]
    ["sovereign_default" "banking_crisis"] ["banking_crisis" "growth_collapse"] ["currency_crisis" "inflation_crisis"]
    ["sovereign_default" "fiscal_consolidation"] ["market_crash" "banking_crisis"]
    ["coup" "conflict_onset"] ["conflict_onset" "coup"] ["violence_surge" "conflict_onset"] ["protest_surge" "coup"]
    ["war_onset" "growth_collapse"] ["conflict_onset" "growth_collapse"]})

(defn edge?
  "The rule: supported by the lagged test (LR of 13.8 or more, p < 0.001
  on two degrees of freedom), in either direction, its sign the edge's
  type (edge-sign); or named by theory and j standing above its calm rate
  in the three years before k; an edge from a higher tier to a lower one
  needs the test. (First fixed with the test's positive sign only; amended
  21 October 2026, before any training, to keep inhibiting edges.)"
  [[j k] {:keys [lr coef]} rr-lead]
  (let [tested (>= lr 13.8)
        down? (> (kinds/tier-order (kinds/tiers j)) (kinds/tier-order (kinds/tiers k)))]
    (cond tested :data
          (and (not down?) (contains? theory [j k]) rr-lead (> rr-lead 1.0)) :theory
          :else nil)))

(defn edge-sign
  "+1 when j's past raises k's hazard (an exciting relation), -1 when it
  lowers it (inhibiting: as real a signal, of another type): the sign of
  j's coefficients' sum for a tested edge, theory's (+1) otherwise."
  [source {:keys [coef]}]
  (if (= source :data) (if (neg? (reduce + coef)) -1 1) 1))

(defn -main [& _]
  (let [cats (update-vals (kinds/catalogues) kinds/annual)
        ;; before 2003 only
        cats (update-vals cats (fn [c] (update c :cover (fn [cv] (into {} (for [[u [a b]] cv :when (<= a 2002)] [u [a (min b 2002)]]))))))
        ks (sort-by (juxt (comp kinds/tier-order kinds/tiers) identity) (keys cats))
        lags (range -10 4)
        dir "data/runs/sequencing"
        rows (vec (for [j ks k ks :when (not= j k)]
                    (let [ll (lead-lag (cats j) (cats k) lags)
                          t (lagged-test (cats j) (cats k))
                          rr-lead (when ll (let [xs (keep ll [-3 -2 -1])] (when (seq xs) (/ (reduce + xs) (count xs)))))]
                      {:j j :k k :ll ll :test t :rr-lead rr-lead :edge (edge? [j k] t rr-lead)})))]
    (io/make-parents (str dir "/x"))
    (with-open [w (io/writer (str dir "/lead-lag.tsv"))]
      (.write w (str "j\tk\t" (str/join "\t" lags) "\n"))
      (doseq [{:keys [j k ll]} rows]
        (.write w (str j "\t" k "\t" (str/join "\t" (map #(if-let [x (get ll %)] (format "%.2f" x) "") lags)) "\n"))))
    (with-open [w (io/writer (str dir "/tests.tsv"))]
      (.write w "j\tk\tlr\tcoef1\tcoef5\tn\tevents\trr_lead\tedge\n")
      (doseq [{:keys [j k test rr-lead edge]} rows]
        (.write w (format "%s\t%s\t%.2f\t%.3f\t%.3f\t%d\t%d\t%s\t%s\n" j k (:lr test) (first (:coef test)) (second (:coef test))
                          (:n test) (:events test) (if rr-lead (format "%.2f" rr-lead) "") (if edge (name edge) "")))))
    (spit (str dir "/graph.edn")
          (pr-str (into (sorted-map) (for [{:keys [j k test rr-lead edge]} rows :when edge]
                                       [[j k] {:source edge :sign (edge-sign edge test) :lr (:lr test) :coef (:coef test) :rr-lead rr-lead}]))))
    (println (format "%-20s %-20s %7s %7s %7s %6s  %s" "j" "k" "lr" "c1" "c5" "rr-3..-1" "edge"))
    (doseq [{:keys [j k test rr-lead edge]} (sort-by #(- (:lr (:test %))) rows)
            :when (or edge (>= (:lr test) 6.0))]
      (println (format "%-20s %-20s %7.1f %7.3f %7.3f %6s  %s" j k (:lr test) (first (:coef test)) (second (:coef test))
                       (if rr-lead (format "%.2f" rr-lead) "-") (if edge (name edge) ""))))
    (println (count (filter :edge rows)) "edges of" (count rows) "pairs")))
