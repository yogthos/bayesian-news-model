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
  (:require [augur.logit :as lgt]
            [augur.score :as sc]
            [bnm.paths]
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
   :norm-short :norm-long :resonance :since-crisis :since-world-wave :wave-fresh :synchrony :world-correlation :flow-max :flow-norm :stock-x-flow
   :persistence :persistence-rise :synchrony-rise :world-correlation-rise :variance-rise :opening :real-rate-stretch])

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

(defn read-intensity
  "{year probability} from bnm.waves' world-intensity.tsv, or {} without one."
  [run]
  (let [f (io/file (str "data/runs/" run "/world-intensity.tsv"))]
    (if (and run (.exists f))
      (into {} (for [line (str/split-lines (slurp f)) :let [[y p] (str/split line #"\t")]]
                 [(parse-long y) (parse-double p)]))
      {})))

(defn read-credit-gap
  "{[iso year] gap}: the one-sided HP credit-to-GDP gap of
  scripts/jst-credit-gap.py, or {}."
  []
  (let [f (io/file (str bnm.paths/derived "jst-credit-gap.csv"))]
    (if (.exists f)
      (into {} (for [line (rest (str/split-lines (slurp f))) :let [[iso y g] (str/split line #",")]]
                 [[iso (parse-long y)] (parse-double g)]))
      {})))

(defn read-regimes
  "{[iso year] {:capital-openness :gold}}: Chinn-Ito capital account openness
  (0 closed to 1 open, from 1970) and the Reinhart-Rogoff gold standard flag."
  []
  (let [rows (fn [path] (with-open [r (io/reader path)]
                          (let [[h & ls] (line-seq r) cols (mapv keyword (str/split h #","))]
                            (mapv #(zipmap cols (str/split % #"," -1)) ls))))
        num (fn [v] (some-> v str/trim not-empty parse-double))
        ka (for [m (rows "data/raw/slow/kaopen/kaopen.csv") :let [v (num (:ka_open m))] :when (and v (num (:year m)))]
             [[(:ccode m) (long (num (:year m)))] {:capital-openness v}])
        gold (for [m (rows "data/raw/slow/rr/onsets.csv") :let [v (num (:gold m))] :when v]
               [[(:iso m) (parse-long (:year m))] {:gold v}])]
    (reduce (fn [acc [k v]] (update acc k merge v)) {} (concat ka gold))))

(defn build
  "Country-years with :specific (stretches), the invariants and targets;
  `intensity` is the world crisis process's probability by year (bnm.waves)."
  [data & [intensity regimes gaps]]
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
        ;; the flows: each dimension's one-year change against its own past changes
        flow-z (into {} (for [[c rows] by-c]
                          [c (let [series (into {} (for [[k f] dimensions]
                                                     [k (let [xs (mapv f rows)]
                                                          (mapv (fn [i] (let [a (get xs (dec i)) b (get xs i)] (when (and a b) (- b a))))
                                                                (range (count xs))))]))]
                               (mapv (fn [i] (keep #(iv/stretch (series %) i 10 6) (map first dimensions)))
                                     (range (count rows))))]))
        ;; the financial stocks against their own twenty years: financial cycles run 15-20 years
        ;; (Borio; the BIS credit gap's long trend), so a ten-year baseline absorbs a long boom
        long-specific (into {} (for [[c rows] by-c]
                                 [c (let [series (into {} (for [[k f] dimensions] [k (mapv f rows)]))]
                                      (mapv (fn [i] (into {} (for [k [:credit :mortgages :house-prices]]
                                                               [k (iv/stretch (series k) i 20 12)])))
                                            (range (count rows))))]))
        short-z (scaled 5 4)
        long-z (scaled 20 12)
        growth (into {} (for [[c rows] by-c] [c (mapv #(lg (:rgdpbarro %)) rows)]))
        dim-series (into {} (for [[c rows] by-c] [c (mapv (fn [[_ f]] (mapv f rows)) dimensions)]))
        ;; the world's mean real equity change by year, to measure each country's synchrony with it
        eq-change (fn [rows i] (let [a (:real-equity (get rows (dec i))) b (:real-equity (get rows i))] (when (and a b) (- b a))))
        world-eq (into {} (for [[yr cs] (group-by first (for [[_ rows] by-c i (range 1 (count rows)) :let [d (eq-change rows i)] :when d]
                                                         [(:year (rows i)) d]))]
                            [yr (/ (reduce + (map second cs)) (count cs))]))
        agg (fn [c i] (iv/aggregate (vals ((stretches c) i)) {:threshold 1.5 :min-n 5}))
        crisis? (fn [c yr] (some #(and (= yr (:year %)) (= 1.0 (:crisisJST %))) (by-c c)))
        years (sort (distinct (map :year data)))
        world-norm (into {} (for [yr years]
                              [yr (let [ns (keep (fn [[c rows]] (let [i (first (keep-indexed #(when (= yr (:year %2)) %1) rows))]
                                                                  (when i (:norm (agg c i)))))
                                                 by-c)]
                                    (when (seq ns) (/ (reduce + ns) (count ns))))]))
        ;; crisis starts: the world's as the share of countries, each country's own
        world-starts (into {} (for [yr years :let [n (count (filter #(crisis? % yr) (keys by-c)))] :when (pos? n)]
                                [yr (/ n (double (count by-c)))]))
        own-starts (into {} (for [c (keys by-c)] [c (into {} (for [yr years :when (crisis? c yr)] [yr 1.0]))]))
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
            ;; the credit gap in units of its own past spread, so the panel's coefficient
            ;; carries to a gap measured differently (the BIS's total credit for the US)
            :specific-long (assoc ((long-specific c) i)
                                  :credit-gap-z (when-let [g (get gaps [c yr])]
                                                  (let [past (keep #(get gaps [c %]) (range 1870 yr))]
                                                    (when (>= (count past) 10)
                                                      (let [m (/ (reduce + past) (count past))
                                                            sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) past)) (dec (count past))))]
                                                        (when (> sd 1e-9) (/ g sd)))))))
            :invariant {:max-displacement (:max a) :breadth (:breadth a) :norm (:norm a)
                        :variability (iv/variance-ratio (growth c) i 5 20)
                        :world-norm (world-norm yr) :world-crises (world-crises yr) :small-falls small
                        :present (if a 1.0 0.0)
                        :since-crisis (some-> since-own (+ 1.0) Math/log) :since-world-wave (some-> since-world (+ 1.0) Math/log)
                        :wave-fresh (some-> since-world (<= 1) (if 1.0 0.0))
                        :flow-max (:max (iv/aggregate ((flow-z c) i) {:threshold 2.0 :min-n 5}))
                        :flow-norm (:norm (iv/aggregate ((flow-z c) i) {:threshold 2.0 :min-n 5}))
                        ;; a stretched stock with a turning flow
                        :stock-x-flow (when-let [f (:norm (iv/aggregate ((flow-z c) i) {:threshold 2.0 :min-n 5}))]
                                        (some-> (:norm a) (* f)))
                        :dim-variance (let [vs (keep #(iv/variance-ratio % i 5 20) (dim-series c))]
                                        (when (>= (count vs) 5) (/ (reduce + vs) (count vs))))
                        :synchrony (iv/synchrony (dim-series c) i 10 6)
                        :world-correlation (let [pairs (keep (fn [j] (when-let [d (eq-change rows j)]
                                                                       (when-let [w (world-eq (:year (rows j)))] [d w])))
                                                             (range (max 1 (- i 9)) (inc i)))]
                                             (when (>= (count pairs) 6) (iv/correlation (map first pairs) (map second pairs))))
                        :norm-short (:norm (iv/aggregate ((short-z c) i) {:threshold 1.5 :min-n 5}))
                        :norm-long (:norm (iv/aggregate ((long-z c) i) {:threshold 1.5 :min-n 5}))
                        ;; displaced at every scale at once (resonance)
                        :resonance (let [ns [(:norm (iv/aggregate ((short-z c) i) {:threshold 1.5 :min-n 5}))
                                             (:norm a)
                                             (:norm (iv/aggregate ((long-z c) i) {:threshold 1.5 :min-n 5}))]]
                                     (when (every? some? ns) (apply min ns)))}
            ;; the crisis process: kernels over past starts, known at the end of the year
            :process (merge (into {} (for [tau [1 3 10 30]]
                                       [(keyword (str "world-k" tau)) (iv/kernel world-starts yr (double tau) {:through yr})]))
                            (into {} (for [tau [3 10 30]]
                                       [(keyword (str "own-k" tau)) (iv/kernel (own-starts c) yr (double tau) {:through yr})]))
                            {:world-intensity (some-> (get intensity yr) (as-> p (Math/log (/ p (- 1.0 p)))))})
            ;; the stabilizing loops: financial repression (a negative real rate), how banks fund
            ;; themselves, a peg, room to cut rates, public debt
            :stabilizer {:real-rate (when (and (:stir r) (:inflation r)) (- (:stir r) (* 100.0 (:inflation r))))
                         :noncore (:noncore r) :loans-to-deposits (:ltd r) :peg (:peg r)
                         :policy-room (:stir r) :public-debt-level (:debtgdp r)
                         :capital-openness (get-in regimes [[c yr] :capital-openness])
                         :gold (get-in regimes [[c yr] :gold])}
            ;; three-year growth of credit to GDP and of real asset prices, for `with-booms`
            :growth-3y (let [d (fn [f] (let [a (f (get rows (- i 3))) b (f r)] (when (and a b (>= i 3)) (- b a))))
                             credit #(div (:tloans %) (:gdp %))
                             house #(lg (div (:hpnom %) (:cpi %)))]
                         {:credit (d credit) :equity (d :real-equity) :house (d house)})
            :wave (let [n (count (filter (fn [c'] (some #(crisis? c' %) [(inc yr) (+ yr 2)])) (keys by-c)))] (>= n 3))
            :crisis crisis-ahead :fall fall-ahead}))))

(defn with-booms
  "Adds :boom {:zone :interaction :equity-zone :house-zone}: each
  country-year's three-year credit growth with its equity and its house-price
  growth, against every country's growth in the years before
  (`bnm.invariants/boom`), so what counts as high is what the panel had seen
  by then. The zone and interaction take whichever asset boomed more."
  [rows]
  (let [by-year (group-by :year rows)
        years (sort (keys by-year))
        past (reduce (fn [acc yr]
                       (let [before (mapcat by-year (filter #(< % yr) years))
                             vals-of (fn [k] (vec (keep #(get-in % [:growth-3y k]) before)))]
                         (assoc acc yr {:credit (vals-of :credit) :equity (vals-of :equity) :house (vals-of :house)})))
                     {} years)]
    (mapv (fn [{:keys [iso year growth-3y] :as r}]
            (let [{:keys [credit equity house]} growth-3y
                  p (past year)
                  ;; the same against the country's own past, which carries across units
                  own (let [before (filter #(and (= iso (:iso %)) (< (:year %) year)) rows)
                            vals-of (fn [k] (vec (keep #(get-in % [:growth-3y k]) before)))]
                        {:credit (vals-of :credit) :equity (vals-of :equity) :house (vals-of :house)})
                  oe (iv/boom credit equity (:credit own) (:equity own))
                  oh (iv/boom credit house (:credit own) (:house own))
                  own-both (remove nil? [oe oh])
                  be (iv/boom credit equity (:credit p) (:equity p))
                  bh (iv/boom credit house (:credit p) (:house p))
                  both (remove nil? [be bh])]
              (assoc r :boom (when (seq both)
                               {:zone (apply max (map :zone both))
                                :interaction (apply max (map :interaction both))
                                :equity-zone (:zone be) :house-zone (:zone bh)})
                     :boom-own (when (seq own-both)
                                 {:zone (apply max (map :zone own-both))
                                  :interaction (apply max (map :interaction own-both))
                                  :credit-z (let [xs (:credit own) n (count xs)]
                                              (when (and credit (>= n 30))
                                                (let [mu (/ (reduce + xs) n)
                                                      sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (dec n)))]
                                                  (when (> sd 1e-12) (/ (- credit mu) sd)))))}))))
          rows)))

(defn with-shifts
  "Adds the invariant forms of the stabilizing loops and of synchrony, each
  measured against the country's own past rather than as a level (a level's
  meaning changes between eras; a change from the country's baseline does
  not): persistence, the mean lag-1 autocorrelation of the dimensions'
  stretches over 15 years (a weak restoring force), and its rise against
  the previous ten years (critical slowing down); the rise in synchrony
  across dimensions (Kritzman et al.: changes in the absorption ratio, not
  its level) and with the world (Dakos et al.: rising spatial correlation);
  the rise in variability across all dimensions (Guttal et al.); the opening
  of the capital account over five years, and the real rate's stretch."
  [rows]
  (let [by-c (group-by :iso rows)]
    (vec (mapcat
          (fn [[_ rs]]
            (let [rs (vec (sort-by :year rs))
                  n (count rs)
                  col (fn [path] (mapv #(get-in % path) rs))
                  dims (map first dimensions)
                  zs (into {} (for [k dims] [k (col [:specific k])]))
                  persistence (mapv (fn [i] (let [acs (keep #(iv/autocorrelation (zs %) i 15 10) dims)]
                                              (when (>= (count acs) 5) (/ (reduce + acs) (count acs)))))
                                    (range n))
                  sync (col [:invariant :synchrony])
                  wcorr (col [:invariant :world-correlation])
                  openness (col [:stabilizer :capital-openness])
                  real-rate (col [:stabilizer :real-rate])
                  rise (fn [xs i] (iv/stretch xs i 10 6))]
              (map-indexed
               (fn [i r]
                 (update r :invariant merge
                         {:persistence (persistence i)
                          :persistence-rise (rise persistence i)
                          :synchrony-rise (rise sync i)
                          :world-correlation-rise (rise wcorr i)
                          :variance-rise (get-in r [:invariant :dim-variance])
                          :opening (when-let [a (openness i)] (when-let [b (get openness (- i 5))] (- a b)))
                          :real-rate-stretch (rise real-rate i)}))
               rs)))
          by-c))))

(defn demean-by-year
  "Rows with each of `names` replaced by its deviation from that year's mean
  across countries: the part that says which country, apart from the part
  that moves with time (Simpson's paradox: pooled over eras, the time part
  can reverse a relation that holds within every era). Known in real time,
  since a year's values for all countries are known at its end."
  [rows names]
  (let [means (into {} (for [[yr rs] (group-by :year rows)]
                         [yr (into {} (for [nm names :let [vs (keep #(get-in % nm) rs)] :when (seq vs)]
                                        [nm (/ (reduce + vs) (count vs))]))]))]
    (mapv (fn [r] (reduce (fn [r nm] (if-let [v (get-in r nm)] (assoc-in r nm (- v (get-in means [(:year r) nm]))) r))
                          r names))
          rows)))

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
                          beta (lgt/fit train k {:prior prior})]
                    r rows :when (= era (era-of (:year r)))]
                [era (lgt/predict beta (vectorize r names m)) (target r)])
        auc (fn [ps] (when (some #(nth % 2) ps) (sc/auc (map second ps) (map #(nth % 2) ps))))]
    (assoc (into {} (for [[era ps] (group-by first preds)] [era (auc ps)])) :all (auc preds))))

(defn prequential
  "Refit each year on the outcomes known by then (targets end two years
  after their year), with weights discounted by age, and predict the year:
  the regime enters only through what history teaches. AUC within each
  later era and pooled from 1950."
  [rows target names prior discount & {:keys [from] :or {from 1870}}]
  (let [rows (filterv #(and (some? (target %)) (era-of (:year %)) (>= (:year %) from)) rows)
        k (inc (count names))
        years (range 1890 2021)
        preds (loop [[yr & more] years beta nil out []]
                (if-not yr
                  out
                  (let [train-rows (filter #(< (+ (:year %) 2) yr) rows)
                        m (means train-rows names)
                        train (for [r train-rows]
                                {:x (vectorize r names m) :y (target r) :w (Math/pow discount (- yr (:year r)))})
                        beta (if (>= (count (filter :y train)) 10) (lgt/fit train k {:prior prior :start beta}) beta)
                        scored (for [r rows :when (and beta (= yr (:year r)))]
                                 [(:year r) (lgt/predict beta (vectorize r names m)) (target r) (:wave r)])]
                    (recur more beta (into out scored)))))
        auc (fn [ps] (when (and (some #(nth % 2) ps) (some #(not (nth % 2)) ps)) (sc/auc (map second ps) (map #(nth % 2) ps))))]
    (assoc (into {} (for [[era lo hi] eras] [era (auc (filter #(<= lo (first %) hi) preds))]))
           :post (auc (filter #(>= (first %) 1950) preds))
           ;; ranking countries within each year only, setting aside when the waves come
           :within (sc/auc-within (filter #(>= (first %) 1950) preds))
           ;; when the waves come: the year's mean probability against three or more
           ;; countries starting a crisis within two years
           :timing (let [by-year (for [[yr ps] (group-by first (filter #(>= (first %) 1950) preds))]
                                   [(/ (reduce + (map second ps)) (count ps)) (nth (first ps) 3)])]
                     (when (and (some second by-year) (some (complement second) by-year))
                       (sc/auc (map first by-year) (map second by-year)))))))

(def transfer-names
  "The banking-crisis hazard carried to the US monthly model: the credit gap
  (one-sided HP trend, in units of its own past spread), the signal stable in
  every era with crises, and the displacement of the whole state; and the
  credit-financed boom against the country's own past (three-year credit
  growth in units of its past spread, the boom zone and the interaction),
  which carries across units and lifted the real-time AUC over 2000-20 from
  0.69 to 0.75 and from 1950 from 0.52 to 0.62."
  [[:specific-long :credit-gap-z] [:invariant :norm]
   [:boom-own :credit-z] [:boom-own :zone] [:boom-own :interaction]])

(defn crisis-betas
  "Per year, the banking-crisis hazard on `transfer-names` fitted over every
  country on the outcomes known by then (year + 2 < the year): {year [beta
  means]}, the means standing in for an input a country lacks."
  [rows]
  (let [rows (filterv #(some? (:crisis %)) rows)
        k (inc (count transfer-names))]
    (into (sorted-map)
          (for [yr (range 1900 2028)
                :let [train-rows (filter #(< (+ (:year %) 2) yr) rows)
                      m (means train-rows transfer-names)
                      train (for [r train-rows] {:x (vectorize r transfer-names m) :y (:crisis r) :w 1.0})]
                :when (>= (count (filter :y train)) 10)]
            [yr [(vec (lgt/fit train k {:prior 10.0})) (mapv m transfer-names)]]))))

(defn- era-auc [rows target fam k]
  (for [[era lo hi] eras]
    (let [rs (filter #(and (<= lo (:year %) hi) (some? (target %)) (some? (get-in % [fam k]))) rows)]
      ;; an era with fewer than five positives says nothing (1950-72 has one crisis)
      (when (and (>= (count (filter target rs)) 5) (some (complement target) rs))
        (sc/auc (map #(get-in % [fam k]) rs) (map target rs))))))

(defn run [{:keys [name waves]}]
  (let [name (or name "macro")
        rows (with-booms (with-shifts (build (read-jst "data/raw/slow/jst/jst.csv") (read-intensity waves) (read-regimes) (read-credit-gap))))
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
        (doseq [[fam k] (concat specific [[:specific-long :credit-gap-z]] invariant)]
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
    (say)
    (say "The crisis process and the stabilizing loops, real time, rows from 1900 (kernels need 30 years")
    (say "of history). AUC by era, pooled from 1950, within years, and wave timing (the year's mean")
    (say "probability against three or more countries starting a crisis within two years).")
    (let [scales (into [[:specific :credit]] (into core [[:invariant :norm-short] [:invariant :norm-long] [:invariant :resonance]]))
          process (into (mapv #(vector :process (keyword (str "world-k" %))) [1 3 10 30])
                        (mapv #(vector :process (keyword (str "own-k" %))) [3 10 30]))
          stabilizer (mapv #(vector :stabilizer %) [:real-rate :noncore :loans-to-deposits :peg :policy-room :public-debt-level])
          regime [[:stabilizer :capital-openness] [:stabilizer :gold]]
          loops (mapv #(vector :invariant %) [:persistence-rise :synchrony-rise :world-correlation-rise :variance-rise :opening :real-rate-stretch])]
      (doseq [[label target] [["crisis" :crisis] ["fall" :fall]]
              [set-label names] [["credit+scales" scales] ["process" process] ["stabilizer" stabilizer]
                                 ["world intensity" [[:process :world-intensity]]]
                                 ["scales+intensity" (conj scales [:process :world-intensity])]
                                 ["scales+intensity+stab" (into (conj scales [:process :world-intensity]) stabilizer)]
                                 ["loops+sync, invariant form" loops]
                                 ["scales+intensity+loops" (into (conj scales [:process :world-intensity]) loops)]
                                 ["trigger" [[:invariant :flow-max] [:invariant :flow-norm] [:invariant :stock-x-flow]]]
                                 ["scales+intensity+trigger" (into (conj scales [:process :world-intensity]) [[:invariant :flow-norm] [:invariant :stock-x-flow]])]
                                 ["scales+trigger" (into scales [[:invariant :flow-norm] [:invariant :stock-x-flow]])]
                                 ["synchrony" [[:invariant :synchrony] [:invariant :world-correlation]]]
                                 ["scales+intensity+sync" (into (conj scales [:process :world-intensity]) [[:invariant :synchrony] [:invariant :world-correlation]])]
                                 ["regime: openness, gold" regime]
                                 ["scales+intensity+regime" (into (conj scales [:process :world-intensity]) regime)]
                                 ["process+stabilizer" (into process stabilizer)]
                                 ["scales+process" (into scales process)] ["scales+stabilizer" (into scales stabilizer)]
                                 ["scales+process+stab" (into scales (into process stabilizer))]]]
        (let [res (prequential rows target names 10.0 1.0 :from 1900)
              ;; the same inputs within years (deviations from the year's mean across countries),
              ;; with the world intensity, raw, as the only input that says when
              timeless (remove #{[:process :world-intensity]} names)
              res-w (prequential (demean-by-year rows timeless) target (conj (vec timeless) [:process :world-intensity]) 10.0 1.0 :from 1900)]
          (say (format "  %-6s %-22s %s   from 1950 %s  within %s  timing %s   [within years + intensity] %s  from 1950 %s  within %s  timing %s"
                       label set-label
                       (str/join " " (map #(fmt (res (first %))) (rest eras))) (fmt (:post res)) (fmt (:within res)) (fmt (:timing res))
                       (str/join " " (map #(fmt (res-w (first %))) (rest eras))) (fmt (:post res-w)) (fmt (:within res-w)) (fmt (:timing res-w))))
)))
    (say)
    (say "Credit-financed booms (Greenwood, Hanson, Shleifer & Sorensen 2022), real time from 1900:")
    (say "three-year credit growth, the boom zone (credit top fifth with an asset top third, against the")
    (say "panel's past) and the interaction, against the transfer inputs (credit gap, displacement).")
    (doseq [[label target] [["crisis" :crisis] ["fall" :fall]]
            [set-label names] [["transfer" transfer-names]
                               ["credit growth" [[:growth-3y :credit]]]
                               ["boom zone" [[:boom :zone]]]
                               ["boom" [[:boom :zone] [:boom :interaction]]]
                               ["credit+boom" [[:growth-3y :credit] [:boom :zone] [:boom :interaction]]]
                               ["transfer+boom" (into transfer-names [[:boom :zone] [:boom :interaction]])]
                               ["own credit z" [[:boom-own :credit-z]]]
                               ["own boom" [[:boom-own :credit-z] [:boom-own :zone] [:boom-own :interaction]]]
                               ["transfer+own boom" (into transfer-names [[:boom-own :credit-z] [:boom-own :zone] [:boom-own :interaction]])]]]
      (let [res (prequential rows target names 10.0 1.0 :from 1900)]
        (say (format "  %-6s %-14s %s   from 1950 %s  within %s  timing %s"
                     label set-label (str/join " " (map #(fmt (res (first %))) (rest eras)))
                     (fmt (:post res)) (fmt (:within res)) (fmt (:timing res))))))
    (let [dir (str "data/runs/" name)]
      (io/make-parents (str dir "/x"))
      ;; the crisis hazard by year, for the US monthly model (bnm.us-invariants)
      ;; per year the coefficients, then the inputs' means
      (let [betas (str/join (for [[yr [b m]] (crisis-betas rows)] (str yr "\t" (str/join "\t" (concat b m)) "\n")))]
        (spit (str dir "/crisis-betas.tsv") betas)
        (io/make-parents bnm.paths/crisis-betas)
        (spit bnm.paths/crisis-betas betas))
      (spit (str dir "/macrohistory.txt") (str out)))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
