(ns bnm.macrohistory
  "Invariants across eras (docs/INVARIANTS.md), on the Jordà-Schularick-
  Taylor Macrohistory Database: 18 advanced economies, annual, 1870-2020,
  88 systemic banking crises (crisisJST).

  Each country-year's state is a set of dimensions, each as its stretch
  against the country's own previous ten years: credit, mortgages, house
  prices, equity prices, investment, the current account, public debt,
  inflation, the short rate, the term spread, the wage share (wages over
  output per head), unemployment, bank leverage and the exchange rate.

  Two kinds of input are compared:
    specific    each dimension's signed stretch: the channels, which differ
                from one crisis to the next;
    invariant   what does not depend on which dimension is stretched: the
                largest displacement, the breadth (share of dimensions
                stretched), the root mean square; rising variability of
                growth (Guttal et al. 2016); and the cross-scale and global
                terms: the world's mean displacement, the share of countries
                in crisis in the last two years, and equity falls of 10-25%
                in the last three years (small crashes before big ones).

  Targets: a crisis starting within two years; a real equity fall of 25%
  or more within two years. Scored by leaving out one era at a time and
  fitting on the others, so each era is predicted by the rest of history:
  an input that only works in its own era fails here.

    jolt -A:tools -M -m bnm.macrohistory [--name macro-1]"
  (:require [bnm.hazard :as hz]
            [bnm.invariants :as iv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def eras [["1870-1913" 1870 1913] ["1919-1938" 1919 1938] ["1950-1972" 1950 1972]
           ["1973-1999" 1973 1999] ["2000-2020" 2000 2020]])

(defn read-jst [path]
  (with-open [r (io/reader path)]
    (let [[header & lines] (line-seq r)
          cols (mapv keyword (str/split header #","))]
      (vec (for [line lines]
             (let [cells (str/split line #"," -1)]
               (into {} (for [[k v] (map vector cols cells)]
                          [k (cond (#{:country :iso} k) v
                                   (= :year k) (some-> v str/trim not-empty parse-double long)
                                   :else (some-> v str/trim not-empty parse-double))]))))))))

(defn- lg [x] (when (and x (pos? x)) (Math/log x)))
(defn- div [a b] (when (and a b (not (zero? b))) (/ a b)))

(def dimensions
  "name and the series per country-year it is computed from."
  [[:credit #(div (:tloans %) (:gdp %))]
   [:mortgages #(div (:tmort %) (:gdp %))]
   [:house-prices #(lg (div (:hpnom %) (:cpi %)))]
   [:equity-prices :real-equity]
   [:investment :iy]
   [:current-account #(div (:ca %) (:gdp %))]
   [:public-debt :debtgdp]
   [:inflation :inflation]
   [:short-rate :stir]
   [:term-spread #(when (and (:ltrate %) (:stir %)) (- (:ltrate %) (:stir %)))]
   [:wage-share #(lg (div (:wage %) (div (:gdp %) (:pop %))))]
   [:unemployment :unemp]
   [:bank-leverage :lev]
   [:exchange-rate #(lg (:xrusd %))]])

(def invariant-names
  [:max-displacement :breadth :norm :variability :world-norm :world-crises :small-falls :present
   :norm-short :norm-long :resonance :since-crisis :since-world-wave :wave-fresh])

(defn- with-derived
  "Adds :real-equity (log real price index from capital gains) and
  :inflation to one country's rows, in year order."
  [rows]
  (let [rows (vec (sort-by :year rows))]
    (loop [i 0 eq 0.0 out []]
      (if (= i (count rows))
        out
        (let [r (rows i)
              prev (when (pos? i) (rows (dec i)))
              infl (when (and prev (:cpi r) (:cpi prev)) (- (lg (:cpi r)) (lg (:cpi prev))))
              g (:eq_capgain r)
              eq' (if (and g infl) (+ eq (- (Math/log (+ 1.0 g)) infl)) eq)
              known? (and g infl)]
          (recur (inc i) eq' (conj out (assoc r :inflation infl :real-equity (when known? eq')))))))))

(defn build
  "Country-years with :specific (stretches), the invariants and targets."
  [data]
  (let [by-c (update-vals (group-by :iso data) with-derived)
        stretches (into {} (for [[c rows] by-c]
                             [c (let [series (into {} (for [[k f] dimensions] [k (mapv f rows)]))]
                                  (mapv (fn [i] (into {} (for [[k _] dimensions] [k (iv/stretch (series k) i 10 6)])))
                                        (range (count rows))))]))
        ;; the same dimensions against the previous 5 and 20 years: displacement at three scales
        scaled (fn [window n]
                 (into {} (for [[c rows] by-c]
                            [c (let [series (into {} (for [[k f] dimensions] [k (mapv f rows)]))]
                                 (mapv (fn [i] (keep #(iv/stretch (series %) i window n) (map first dimensions)))
                                       (range (count rows))))])))
        short-z (scaled 5 4)
        long-z (scaled 20 12)
        growth (into {} (for [[c rows] by-c] [c (mapv #(lg (:rgdpbarro %)) rows)]))
        agg (fn [c i] (iv/aggregate (vals ((stretches c) i)) {:threshold 1.5 :min-n 5}))
        crisis? (fn [c yr] (some #(and (= yr (:year %)) (= 1.0 (:crisisJST %))) (by-c c)))
        years (sort (distinct (map :year data)))
        world-norm (into {} (for [yr years]
                              [yr (let [ns (keep (fn [[c rows]] (let [i (first (keep-indexed #(when (= yr (:year %2)) %1) rows))]
                                                                  (when i (:norm (agg c i)))))
                                                 by-c)]
                                    (when (seq ns) (/ (reduce + ns) (count ns))))]))
        world-crises (into {} (for [yr years]
                                [yr (/ (count (filter (fn [c] (or (crisis? c yr) (crisis? c (dec yr)))) (keys by-c)))
                                       (double (count by-c)))]))]
    (vec (for [[c rows] by-c
               i (range (count rows))
               :let [r (rows i) yr (:year r)
                     a (agg c i)
                     eq (fn [j] (:real-equity (get rows j)))
                     ;; a 10-25% real fall in any of the last three years
                     small (when (eq i)
                             (if (some (fn [j] (when-let [b (eq (dec j))] (when-let [e (eq j)] (<= (Math/log 0.75) (- e b) (Math/log 0.9)))))
                                       (range (- i 2) (inc i)))
                               1.0 0.0))
                     ;; years since the country's last crisis, and since the world's last wave
                     ;; (a quarter of countries in crisis within two years), capped at 60
                     ;; unknown, not calm, when the data starts before any crisis is seen
                     since (fn [pred] (first (filter #(pred (- yr %)) (range 0 (inc (- yr 1870))))))
                     since-own (since #(crisis? c %))
                     since-world (since #(>= (get world-crises % 0.0) 0.25))
                     ongoing (some #(crisis? c %) (range (- yr 2) (inc yr)))
                     crisis-ahead (when-not ongoing (boolean (some #(crisis? c %) [(inc yr) (+ yr 2)])))
                     fall-ahead (when-let [e (eq i)]
                                  (let [fs (keep eq [(inc i) (+ i 2)])]
                                    (when (= 2 (count fs)) (boolean (some #(<= (- % e) (Math/log 0.75)) fs)))))]]
           {:iso c :year yr :specific ((stretches c) i)
            :invariant {:max-displacement (:max a) :breadth (:breadth a) :norm (:norm a)
                        :variability (iv/variance-ratio (growth c) i 5 20)
                        :world-norm (world-norm yr) :world-crises (world-crises yr) :small-falls small
                        :present (if a 1.0 0.0)
                        :since-crisis (some-> since-own (+ 1.0) Math/log) :since-world-wave (some-> since-world (+ 1.0) Math/log)
                        :wave-fresh (some-> since-world (<= 1) (if 1.0 0.0))
                        :norm-short (:norm (iv/aggregate ((short-z c) i) {:threshold 1.5 :min-n 5}))
                        :norm-long (:norm (iv/aggregate ((long-z c) i) {:threshold 1.5 :min-n 5}))
                        ;; displaced at every scale at once (resonance)
                        :resonance (let [ns [(:norm (iv/aggregate ((short-z c) i) {:threshold 1.5 :min-n 5}))
                                             (:norm a)
                                             (:norm (iv/aggregate ((long-z c) i) {:threshold 1.5 :min-n 5}))]]
                                     (when (every? some? ns) (apply min ns)))}
            :crisis crisis-ahead :fall fall-ahead}))))

(defn- era-of [yr] (first (keep (fn [[n lo hi]] (when (<= lo yr hi) n)) eras)))

(defn- means
  "Each input's mean over the rows that have it, to stand in for missing values."
  [rows names]
  (into {} (for [nm names :let [vs (keep #(get-in % nm) rows)]] [nm (if (seq vs) (/ (reduce + vs) (count vs)) 0.0)])))

(defn- vectorize [r names m]
  (double-array (cons 1.0 (for [nm names] (or (get-in r nm) (m nm))))))

(defn leave-era-out
  "AUC per era for a hazard fitted on the other eras: {era auc}, plus :all
  for the pooled out-of-era predictions."
  [rows target names prior]
  (let [rows (filter #(and (some? (target %)) (era-of (:year %))) rows)
        k (inc (count names))
        preds (for [[era] eras
                    :let [train-rows (filter #(not= era (era-of (:year %))) rows)
                          m (means train-rows names)
                          train (for [r train-rows] {:x (vectorize r names m) :y (target r) :w 1.0})
                          beta (hz/fit train k {:prior prior})]
                    r rows :when (= era (era-of (:year r)))]
                [era (hz/predict beta (vectorize r names m)) (target r)])
        auc (fn [ps] (when (some #(nth % 2) ps) (hz/auc (map second ps) (map #(nth % 2) ps))))]
    (assoc (into {} (for [[era ps] (group-by first preds)] [era (auc ps)])) :all (auc preds))))

(defn prequential
  "Refit each year on the outcomes known by then (targets end two years
  after their year), with weights discounted by age, and predict the year:
  the regime enters only through what history teaches. AUC within each
  later era and pooled from 1950."
  [rows target names prior discount]
  (let [rows (filterv #(and (some? (target %)) (era-of (:year %))) rows)
        k (inc (count names))
        years (range 1890 2021)
        preds (loop [[yr & more] years beta nil out []]
                (if-not yr
                  out
                  (let [train-rows (filter #(< (+ (:year %) 2) yr) rows)
                        m (means train-rows names)
                        train (for [r train-rows]
                                {:x (vectorize r names m) :y (target r) :w (Math/pow discount (- yr (:year r)))})
                        beta (if (>= (count (filter :y train)) 10) (hz/fit train k {:prior prior :start beta}) beta)
                        scored (for [r rows :when (and beta (= yr (:year r)))]
                                 [(:year r) (hz/predict beta (vectorize r names m)) (target r)])]
                    (recur more beta (into out scored)))))
        auc (fn [ps] (when (and (some #(nth % 2) ps) (some #(not (nth % 2)) ps)) (hz/auc (map second ps) (map #(nth % 2) ps))))]
    (assoc (into {} (for [[era lo hi] eras] [era (auc (filter #(<= lo (first %) hi) preds))]))
           :post (auc (filter #(>= (first %) 1950) preds))
           ;; ranking countries within each year only, setting aside when the waves come
           :within (hz/auc-within (filter #(>= (first %) 1950) preds)))))

(defn- era-auc [rows target fam k]
  (for [[era lo hi] eras]
    (let [rs (filter #(and (<= lo (:year %) hi) (some? (target %)) (some? (get-in % [fam k]))) rows)]
      (when (and (some target rs) (some (complement target) rs))
        (hz/auc (map #(get-in % [fam k]) rs) (map target rs))))))

(defn run [{:keys [name]}]
  (let [name (or name "macro")
        rows (build (read-jst "data/raw/slow/jst/jst.csv"))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        fmt (fn [x] (if x (format "%.2f" (double x)) "  - "))
        specific (mapv (fn [[k _]] [:specific k]) dimensions)
        invariant (mapv (fn [k] [:invariant k]) invariant-names)
        ;; the displacement of the country and of the world, with a flag for a known state
        core [[:invariant :norm] [:invariant :breadth] [:invariant :world-norm] [:invariant :present]]]
    (doseq [[label target] [["systemic crisis within 2 years" :crisis] ["real equity fall of 25% within 2 years" :fall]]]
      (let [usable (filter #(some? (target %)) rows)]
        (say)
        (say (format "%s: %d country-years, %d positive" label (count usable) (count (filter target usable))))
        (say (format "  %-18s %s   agree" "input" (str/join " " (map first eras))))
        (doseq [[fam k] (concat specific invariant)]
          (let [aucs (era-auc rows target fam k)
                known (keep identity aucs)
                above (count (filter #(> % 0.5) known))]
            (say (format "  %-18s %s   %d/%d" (clojure.core/name k) (str/join "      " (map fmt aucs))
                         (max above (- (count known) above)) (count known)))))
        (say "  leave one era out (fitted on the other four), AUC:")
        (say "  positives by era:" (str/join " " (for [[era lo hi] eras] (count (filter #(and (target %) (<= lo (:year %) hi)) usable)))))
        (doseq [[set-label names] [["specific" specific] ["invariant" invariant] ["both" (into specific invariant)]
                                   ["core" core] ["credit" [[:specific :credit]]] ["credit+core" (into [[:specific :credit]] core)]
                                   ["scales" (into core [[:invariant :norm-short] [:invariant :norm-long] [:invariant :resonance]])]
                                   ["credit+scales" (into [[:specific :credit]] (into core [[:invariant :norm-short] [:invariant :norm-long] [:invariant :resonance]]))]
                                   ["memory" [[:invariant :since-crisis] [:invariant :since-world-wave]]]
                                   ["credit+scales+memory" (into [[:specific :credit] [:invariant :since-crisis] [:invariant :since-world-wave]]
                                                                 (into core [[:invariant :norm-short] [:invariant :norm-long] [:invariant :resonance]]))]]
                prior [10.0 100.0]]
          (let [res (leave-era-out rows target names prior)]
            (say (format "    %-10s prior %4.0f  %s   pooled %s" set-label prior
                         (str/join " " (map #(fmt (res (first %))) eras)) (fmt (:all res))))))))
    (say)
    (say "Real time: refit yearly on known outcomes, weights discounted by age; AUC by era and from 1950.")
    (doseq [[label target] [["crisis" :crisis] ["fall" :fall]]
            [set-label names] [["specific" specific] ["credit" [[:specific :credit]]]
                               ["scales" (into core [[:invariant :norm-short] [:invariant :norm-long] [:invariant :resonance]])]
                               ["credit+scales" (into [[:specific :credit]] (into core [[:invariant :norm-short] [:invariant :norm-long] [:invariant :resonance]]))]
                               ["memory" [[:invariant :since-crisis] [:invariant :since-world-wave]]]
                               ;; a wave spreads (contagion) before memory protects, then fades
                               ["memory+contagion" [[:invariant :since-world-wave] [:invariant :world-crises] [:invariant :wave-fresh]]]
                               ["credit+memory+contagion" [[:specific :credit] [:invariant :since-world-wave] [:invariant :world-crises] [:invariant :wave-fresh]]]
                               ["credit+scales+memory" (into [[:specific :credit] [:invariant :since-crisis] [:invariant :since-world-wave]]
                                                             (into core [[:invariant :norm-short] [:invariant :norm-long] [:invariant :resonance]]))]
                               ["all" (into specific invariant)]]
            discount [1.0]]
      (let [res (prequential rows target names 10.0 discount)]
        (say (format "  %-6s %-22s discount %.2f  %s   from 1950 %s  within years %s" label set-label discount
                     (str/join " " (map #(fmt (res (first %))) (rest eras))) (fmt (:post res)) (fmt (:within res))))))
    (let [dir (str "data/runs/" name)]
      (io/make-parents (str dir "/x"))
      (spit (str dir "/macrohistory.txt") (str out)))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
