(ns bnm.lead-test
  "The lead test a world gauge must pass before it is wired into the year
  nodes (fix 3 of the step-2 rescore). The step-2 diagnosis found the VIX
  and world credit concurrent with market crashes, not leading them: r
  +0.69 with the VIX averaged over the 24-month window, +0.04 with the VIX
  at the origin, and a fit on it worse than climatology out of sample.

  For a gauge g (its value known at each origin) and a kind's year rate
  (the share of countries with an onset in the window (t, t + 24], known
  when every country's row is), over the origins in [from, to]:
    :lead        r of g at the origin with the rate;
    :concurrent  r of g's mean over the window with the rate (hindsight,
                 a diagnostic only);
    :online      the squared error of rate ~ a + b g fitted at each origin
                 on the rows known by then, against :clim, the mean of the
                 rates known by then (both from 3 known rows on).
  A gauge passes when its online error is at most 0.9 of climatology's
  (the diagnosis: food momentum 0.78 at food price shocks, the VIX 1.14 at
  market crashes); a ratio near 1 is what a fit on noise gives over twenty
  origins.

    jolt -A:tools -M -m bnm.lead-test [--kinds food_price_shock,market_crash] [--from 2003] [--to 2022]

  prints a table for the world gauges of bnm.world-state at every kind
  named (all thirteen without)."
  (:require [bnm.kinds :as kinds]
            [bnm.lattice-political :as lp]
            [bnm.lattice-banking :as lb]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.world-gauges :as wg]
            [bnm.world-state :as ws]
            [clojure.string :as str]))

(defn- corr [xs ys]
  (let [n (count xs)]
    (when (> n 2)
      (let [mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
            c (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
            vx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
            vy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
        (when (and (pos? vx) (pos? vy)) (/ c (Math/sqrt (* vx vy))))))))

(defn- ols
  "[a b] of y ~ a + b x."
  [xs ys]
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        vx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        b (if (pos? vx) (/ (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys)) vx) 0.0)]
    [(- my (* b mx)) b]))

(defn lead-test
  "{:lead :concurrent :online :clim :n :pass}: `gauge` (fn [t]) -> the
  gauge known at t or nil, `window-mean` (fn [t]) -> its mean over the
  window (or nil), `rate` (fn [t]) -> {:rate :known-at} or nil, over
  `origins`."
  [{:keys [gauge window-mean rate origins]}]
  (let [rows (vec (for [t origins :let [g (gauge t) r (rate t)] :when (and g r)]
                    {:t t :g (double g) :r (double (:rate r)) :known-at (:known-at r) :w (some-> (window-mean t) double)}))
        online (vec (for [{:keys [t g r]} rows
                          :let [known (filter #(<= (:known-at %) t) rows)]
                          :when (>= (count known) 3)
                          :let [[a b] (ols (map :g known) (map :r known))
                                clim (/ (reduce + (map :r known)) (count known))]]
                      [(let [e (- r (+ a (* b g)))] (* e e)) (let [e (- r clim)] (* e e))]))
        mse (fn [k] (when (seq online) (/ (reduce + (map k online)) (count online))))
        cw (filter :w rows)]
    {:n (count rows)
     :lead (corr (map :g rows) (map :r rows))
     :concurrent (corr (map :w cw) (map :r cw))
     :online (mse first) :clim (mse second)
     :pass (boolean (and (seq online) (<= (mse first) (* 0.9 (mse second)))))}))

(defn year-rate
  "(fn [t]) -> {:rate :known-at} of spec `sp`'s year node over its
  countries: the share with an onset among those scored, known when the
  last of them is; nil when none is scored."
  [sp]
  (let [cs (filter #(= 3 (count %)) (keys (:parent sp)))]
    (memoize (fn [t]
               (let [tg (keep #((:target sp) :year % t) cs)
                     ys (filter (comp some? :y) tg)]
                 (when (seq ys)
                   {:rate (/ (count (filter :y ys)) (double (count ys)))
                    :known-at (reduce max (map :known-at ys))}))))))

(defn -main [& args]
  (let [{:keys [kinds from to]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        from (or (some-> from parse-long) 2003)
        to (or (some-> to parse-long) 2022)
        end (+ 11 (* 12 2025))
        cats (kinds/catalogues)
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
        units (set (filter #(= 3 (count %)) (map first (keys db))))
        state (wg/load-state db units end)
        gauge-at (memoize (fn [t] (zipmap ws/gauge-names (:x (ws/gauges state t nil)))))
        origins (for [y (range from (inc to))] (+ 11 (* 12 y)))]
    (println "kind\tgauge\tn\tlead\tconcurrent\tonline\tclim\tpass")
    (doseq [k ks
            :let [sp (if (= k lb/kind) (lb/build end "r6-at-8ul" {}) (lp/build k end "r6-at-8ul" cats (* 12 1870) {}))
                  rate (year-rate sp)]
            g ws/price-names
            :let [{:keys [n lead concurrent online clim pass]}
                  (lead-test {:gauge #(get (gauge-at %) g)
                              :window-mean (fn [t] (let [vs (keep #(get (gauge-at %) g) (range (inc t) (+ t 25)))]
                                                     (when (seq vs) (/ (reduce + vs) (count vs)))))
                              :rate rate :origins origins})
                  f (fn [x] (if x (format "%.3f" (double x)) "-"))]]
      (println (str/join "\t" [k (name g) n (f lead) (f concurrent) (f online) (f clim) pass])))))
