(ns bnm.us-loops
  "The stabilizing loops and synchrony in invariant form, at the frequency
  the early-warning literature needs (docs/INVARIANTS.md): monthly, so a
  correlation or an autocorrelation rests on 12-60 observations, not on 15
  annual ones.

    macro-sync           mean absolute correlation of the monthly changes of
                         nine US series (production, unemployment, payrolls,
                         prices, bill and bond rates, the default spread,
                         real oil, the market) over 60 months
    macro-sync-shift     the same over 12 months minus over 60: rising
                         coupling (Kritzman et al.: the absorption ratio's
                         shift, not its level)
    macro-persistence    mean lag-1 autocorrelation over 60 months of each
                         series' deviation from its own past year: how long
                         deviations linger, a weak restoring force
    macro-persistence-rise  its stretch against the previous ten years
                         (critical slowing down)
    market-sync, market-sync-shift  the same coupling across the 22 stock
                         markets of French's international files (1975 on)

  Everything is read a month late."
  (:require [bnm.invariants :as iv]
            [bnm.french :as fr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def names [:macro-sync :macro-sync-shift :macro-persistence :macro-persistence-rise
            :market-sync :market-sync-shift :loops-present])

(defn- read-monthly [path]
  (with-open [r (io/reader path)]
    (into {} (for [line (rest (line-seq r))
                   :let [[d v] (str/split line #",")
                         x (some-> v str/trim not-empty parse-double)]
                   :when (and x (re-matches #"\d{4}-\d{2}.*" d))]
               [(+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 5 7)))) x]))))

(defn- lg [m] (into {} (for [[k v] m :when (pos? v)] [k (Math/log v)])))

(defn inputs
  "f of month t giving the values for `names`; `market` is {month log
  wealth} for the US market."
  [market]
  (let [dir "data/raw/slow/market/"
        rd #(read-monthly (str dir % ".csv"))
        cpi (rd "CPIAUCNS")
        baa (rd "BAA") aaa (rd "AAA")
        series [(lg (rd "INDPRO")) (rd "UNRATE") (lg (rd "PAYEMS")) (lg cpi) (rd "TB3MS") (rd "GS10")
                (into {} (for [[m v] baa :let [a (aaa m)] :when a] [m (- v a)]))
                (into {} (for [[m v] (rd "WTISPLC") :let [c (cpi m)] :when c] [m (Math/log (/ v c))]))
                market]
        intl (->> (.listFiles (io/file (str dir "intl")))
                  (filter #(str/ends-with? (.getName %) ".Dat"))
                  (sort-by #(.getName %))
                  (mapv #(:ret (fr/read-country %))))
        ;; cumulative log wealth per market from its returns, so changes are returns
        intl-wealth (mapv (fn [rets] (let [ms (sort (keys rets))]
                                       (into {} (map vector ms (reductions + (map #(Math/log (+ 1.0 (rets %))) ms))))))
                          (conj intl (into {} (for [[m w] market] [m w]))))
        window-vec (fn [m s len] (mapv m (range (- s (dec len)) (inc s))))
        sync (fn [ms s len n] (iv/synchrony (mapv #(window-vec % s (inc len)) ms) len len n))
        ;; deviation of each series from its own trailing 12 months, by month
        dev (fn [m s] (when-let [x (m s)]
                        (let [prev (keep m (range (- s 12) s))]
                          (when (>= (count prev) 9) (- x (/ (reduce + prev) (count prev)))))))
        persistence (memoize
                     (fn [s] (let [acs (keep (fn [m] (iv/autocorrelation (mapv #(dev m %) (range (- s 60) (inc s))) 60 60 40)) series)]
                               (when (>= (count acs) 5) (/ (reduce + acs) (count acs))))))]
    (fn [t]
      (let [s (dec t)
            ms60 (sync series s 60 40) ms12 (sync series s 12 10)
            p (persistence s)
            p-rise (when p (iv/stretch (mapv persistence (range (- s 120) (inc s))) 120 120 60))
            mk60 (sync intl-wealth s 60 40) mk12 (sync intl-wealth s 12 10)
            vals [ms60 (when (and ms12 ms60) (- ms12 ms60)) p p-rise mk60 (when (and mk12 mk60) (- mk12 mk60))]]
        (conj vals (when (some some? vals) 1.0))))))
