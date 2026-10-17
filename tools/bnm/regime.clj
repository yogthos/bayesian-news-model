(ns bnm.regime
  "Do political economy regimes explain why macro predictors of US bear
  markets flip sign between eras? (docs/REGIMES.md)

  For each input, its univariate AUC against a 20% fall within 12 months in
  1930-1949, 1950-1969, 1970-1989 and, held out, 1990 on. An input is stable
  if it is on the same side of 0.5 in all three early eras. Then the
  drawdown hazard on the stable base inputs, against the same plus the
  stable political economy inputs, both tuned on 1950-1989 and scored on
  1990 on.

    jolt -A:tools -M -m bnm.regime [--name regime-1]"
  (:require [bnm.hazard :as hz]
            [bnm.trade :as tr]
            [bnm.trader :as td]
            [bnm.us-polecon :as upc]
            [bnm.us-loops]
            [bnm.us-invariants]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def eras [["1930-49" 1930 1949] ["1950-69" 1950 1969] ["1970-89" 1970 1989] ["1990-" 1990 2100]])

(def candidates
  (-> [:term :inflation :tightening :default]
      (into (remove #{:polecon-present} upc/names))
      (into [:hmm-plain :hmm-tvtp :small-scale :lppls-bubble :lppls-antibubble :oil-stretch :oil12 :gpr-stretch :gpr12])
      (into (remove #{:loops-present} bnm.us-loops/names))
      (into (remove #{:us-invariants-present} bnm.us-invariants/names))))

(def profit-squeeze
  "A tight labour market matters through labour's share (Weisskopf 1979;
  Goodwin 1967): tightening and payroll growth times the wage share's
  deviation, and rising unemployment times the profit rate's. Hand-written
  regime terms: they did not replicate on UK data (scripts/uk-regime.py) and
  are not used by the model; kept to reproduce the failed test."
  [:tightening-x-wage :payrolls-x-wage :unemployment-x-profit :polecon-present])

(defn- era-auc [rows k lo hi]
  (let [rs (filter #(and (<= lo (quot (:t %) 12) hi) (some? (:y %)) (some? (get (:raw %) k))) rows)
        n (count (filter :y rs))]
    (when (and (>= n 5) (> (count rs) n))
      (hz/auc (map #(get (:raw %) k) rs) (map :y rs)))))

(defn stable?
  "Same side of 0.5 in the early eras (from `from`, an index into eras), all known."
  [aucs from]
  (let [early (subvec aucs from 3)]
    (and (every? some? early)
         (or (every? #(> % 0.5) early) (every? #(< % 0.5) early)))))

(defn- hazard [rows names]
  (let [pick (td/select-inputs names)
        rs (mapv #(assoc % :x (double-array (cons 1.0 (map (fn [v] (if v (double v) 0.0)) (pick (:vals %)))))) rows)
        grid (for [prior [10.0 100.0] discount [1.0 0.99 0.98]]
               (let [ps (td/predictions rs 12 prior discount 1940)]
                 {:ps ps :ls (td/log-score (filter #(<= 1950 (quot (first %) 12) 1989) ps))}))
        ps (:ps (apply max-key :ls grid))]
    (fn [lo hi]
      (let [ps (filter #(and (some? (nth % 2)) (<= lo (quot (first %) 12) hi)) ps)]
        (hz/auc (map second ps) (map #(nth % 2) ps))))))

(defn run [{:keys [name]}]
  (let [name (or name "regime")
        {:keys [fr w months raw-inputs]} (td/setup)
        start (:start fr)
        idx (into {} (map-indexed (fn [i n] [n i]) td/all-input-names))
        rows (vec (for [t months
                        :let [vals (raw-inputs t)]]
                    {:t t :vals vals :raw (into {} (for [[k i] idx] [k (nth vals i)]))
                     :y (tr/drawdown-ahead? w (inc (- t start)) 12 0.2)}))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        fmt (fn [x] (if x (format "%.2f" (double x)) "  - "))
        table (for [k candidates]
                (let [aucs (mapv (fn [[_ lo hi]] (era-auc rows k lo hi)) eras)]
                  {:k k :aucs aucs :stable (stable? aucs 0) :postwar (stable? aucs 1)}))
        chosen (mapv :k (filter :stable table))
        postwar (mapv :k (filter :postwar table))]
    (say "Univariate AUC against a 20% fall within 12 months, by era (stable: same side of 0.5 before 1990)")
    (say (format "  %-24s %s" "input" (str/join "  " (map first eras))))
    (doseq [{:keys [k aucs stable]} table]
      (say (format "  %-24s %s   %s" (clojure.core/name k) (str/join "     " (map fmt aucs)) (cond stable "stable" (:postwar (first (filter #(= k (:k %)) table))) "postwar" :else ""))))
    (say)
    (say "Drawdown hazard, AUC (tuned on 1950-89):")
    (doseq [[label names] [["base (stable inputs)" td/stable-inputs]
                           ["base + flipping macro" (into td/stable-inputs [:term :inflation :tightening])]
                           ["base + stable political economy" (into td/stable-inputs (conj chosen :polecon-present))]
                           ["base + postwar-stable" (into td/stable-inputs (conj postwar :polecon-present))]
                           ;; the profit squeeze in the form theory gives it (chosen after seeing the era table)
                           ["base + profit squeeze" (into td/stable-inputs profit-squeeze)]
                           ["base + squeeze + realization" (into td/stable-inputs (conj profit-squeeze :household-debt-x-low-wage))]
                           ;; controls: the same debt without the regime, and the regime terms without the squeeze
                           ["base + squeeze + debt alone" (into td/stable-inputs (conj profit-squeeze :household-debt3))]
                           ["base + realization" (into td/stable-inputs [:household-debt-x-low-wage :polecon-present])]
                           ["base + debt alone" (into td/stable-inputs [:household-debt3 :polecon-present])]
                           ["base + squeeze + realization, raw" (into td/stable-inputs [:tightening :payrolls12 :unemployment12 :household-debt3 :wage-share-dev :profit-rate-dev :polecon-present])]]]
      (let [score (hazard rows names)]
        ;; 2003 on leaves out the 2000-02 bear market, the held-out episode with most months
        (say (format "  %-34s 1950-89 %s   1990- %s   2000- %s   2003- %s" label
                     (fmt (score 1950 1989)) (fmt (score 1990 2100)) (fmt (score 2000 2100)) (fmt (score 2003 2100))))))
    (let [dir (str "data/runs/" name)]
      (io/make-parents (str dir "/x"))
      (spit (str dir "/regime.txt") (str out)))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
