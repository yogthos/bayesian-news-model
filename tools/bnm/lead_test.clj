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
  named (all thirteen without): the test in hindsight over [from, to], and
  the gate as bnm.world-gauges/with-world-once applies it at the year
  (bnm-56c.29: the one-sided Diebold-Mariano test of the online loss
  difference at 0.10/26, this gauge alone in its cell): at each origin in
  [from, to] on the origins from 1950 whose rates were known by then, how
  many it passes, the first, and the last."
  (:require [bnm.kinds :as kinds]
            [bnm.lattice-political :as lp]
            [bnm.lattice-banking :as lb]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.world-gauges :as wg]
            [bnm.world-state :as ws]
            [bnm.world-term :as wt]
            [clojure.string :as str]))

(def lead-test
  "{:lead :concurrent :online :clim :n :n-online :pass}: bnm.world-term/lead-test."
  wt/lead-test)

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
    (println "kind\tgauge\tn\tlead\tconcurrent\tonline\tclim\tpass\tgate-origins\tfirst-gate\tgate-at-to")
    (doseq [k ks
            :let [sp (if (= k lb/kind) (lb/build end "r6-at-8ul" {}) (lp/build k end "r6-at-8ul" cats (* 12 1870) {}))
                  rate (year-rate sp)]
            g ws/price-names
            :let [{:keys [n lead concurrent online clim pass]}
                  (lead-test {:gauge #(get (gauge-at %) g)
                              :window-mean (fn [t] (let [vs (keep #(get (gauge-at %) g) (range (inc t) (+ t 25)))]
                                                     (when (seq vs) (/ (reduce + vs) (count vs)))))
                              :rate rate :origins origins})
                  f (fn [x] (if x (format "%.3f" (double x)) "-"))
                  ;; the gate as the term applies it at the year (bnm.world-term/gate, bnm-56c.29):
                  ;; re-decided at each origin on what is known then, from 1950
                  gate-at (fn [t] (:pass (wt/gate {:gauges (fn [s] [(get (gauge-at s) g)])
                                                   :outcome (fn [s] (when-let [{r :rate ka :known-at} (rate s)] {:share r :known-at ka}))
                                                   :origins (for [y (range 1950 (inc to))] (+ 11 (* 12 y)))}
                                                  (wg/scale-gate wg/default-gate {:per 12 :horizon 24}) t 0)))
                  passed (filter gate-at origins)]]
      (println (str/join "\t" [k (name g) n (f lead) (f concurrent) (f online) (f clim) pass
                               (str (count passed) "/" (count origins))
                               (if (seq passed) (quot (first passed) 12) "-")
                               (boolean (gate-at (last origins)))])))))
