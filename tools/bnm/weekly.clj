(ns bnm.weekly
  "The trader's test at weekly frequency (docs/TRADING.md): the US market from
  daily returns (Ken French, 1926 on), decisions at each week's close. Monthly
  rules see 1987 and 2020 only after the month is out; a week's close sees
  them days in.

  Inputs are the monthly test's stable set, measured from daily data: 52-week
  momentum, the distance from the 40-week average, realized volatility over
  21 days and its ratio to 252 days, the drawdown from the 52-week high, the
  deceleration of momentum, CAPE lagged three months, and valuation times the
  turn of the trend. Targets: the market falls 20% (10%) below this week's
  close within 13, 26 or 52 weeks. Prior, discount and thresholds are chosen
  on 1950-1989; 1990 on is held out.

    jolt -A:tools -M -m bnm.weekly [--name weekly]"
  (:require [bnm.hazard :as hz]
            [bnm.trade :as tr]
            [bnm.trader :as us]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- days-from-civil
  "Days since 1970-01-01 (Hinnant's algorithm)."
  ^long [^long y ^long m ^long d]
  (let [y (if (<= m 2) (dec y) y)
        era (quot (if (>= y 0) y (- y 399)) 400)
        yoe (- y (* era 400))
        doy (+ (quot (+ (* 153 (+ m (if (> m 2) -3 9))) 2) 5) (dec d))
        doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
    (+ (* era 146097) doe -719468)))

(defn read-daily
  "[[yyyymmdd market-return bill-return]] from French's daily factors."
  []
  (with-open [r (io/reader (str us/dir "F-F_Research_Data_Factors_daily.csv"))]
    (->> (line-seq r)
         (drop-while #(not (str/starts-with? % ",Mkt-RF")))
         rest
         (take-while #(re-matches #"\d{8},.*" %))
         (mapv (fn [l]
                 (let [[d mrf _ _ rf] (map str/trim (str/split l #","))]
                   [d (/ (+ (parse-double mrf) (parse-double rf)) 100.0) (/ (parse-double rf) 100.0)]))))))

(defn weeks
  "Daily data grouped into weeks starting Monday: [{:month :mkt :rf :days [r..]}],
  :month the calendar month of the week's last day."
  [daily]
  (->> daily
       (partition-by (fn [[d]] (quot (+ 3 (days-from-civil (parse-long (subs d 0 4)) (parse-long (subs d 4 6))
                                                         (parse-long (subs d 6 8))))
                                     7)))
       (mapv (fn [ds]
               (let [[d] (last ds)]
                 {:month (+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 4 6))))
                  :mkt (dec (reduce * (map #(+ 1.0 (second %)) ds)))
                  :rf (dec (reduce * (map #(+ 1.0 (nth % 2)) ds)))
                  :days (mapv second ds)})))))

(def names [:cape :mom52 :sma40 :vol21 :vol-ratio :drawdown52 :cape-x-drawdown :cape-x-decel :cape-x-below-sma])

(defn inputs
  "Inputs at the close of week k, or nil without a year of history."
  [wk w daily-before pe10 k]
  (when (>= k 104)
    (let [ws #(w (inc %))
          mom52 (Math/log (/ (ws k) (ws (- k 52))))
          prev52 (Math/log (/ (ws (- k 52)) (ws (- k 104))))
          sma (Math/log (/ (ws k) (/ (reduce + (map ws (range (- k 39) (inc k)))) 40.0)))
          ds (daily-before k)
          vol21 (us/sd (take-last 21 ds))
          vol252 (us/sd (take-last 252 ds))
          dd (- 1.0 (/ (ws k) (apply max (map ws (range (- k 51) (inc k))))))
          cape (us/lg (pe10 (- (:month (wk k)) 3)))]
      [cape mom52 sma vol21 (/ vol21 (max 1e-9 vol252)) dd
       (when cape (* cape dd)) (when cape (* cape (- prev52 mom52))) (when cape (if (neg? sma) cape 0.0))])))

(defn- predictions
  "Out-of-sample probabilities per week, refit each January on the weeks whose
  outcome is known: [[k p y]]."
  [rows year-of h prior discount from]
  (let [k (alength ^doubles (:x (first rows)))]
    (loop [[yr & more] (range from (inc (year-of (:k (peek rows))))) beta nil out []]
      (if-not yr
        out
        (let [k0 (first (keep #(when (= yr (year-of (:k %))) (:k %)) rows))
              train (for [{:keys [x y] kk :k} rows :when (and k0 (some? y) (< (+ kk h) k0))]
                      {:x x :y y :w (Math/pow discount (/ (- k0 kk) 52.0))})
              beta (if (and k0 (>= (count (filter :y train)) 5)) (hz/fit train k {:prior prior :start beta}) beta)]
          (recur more beta
                 (into out (for [{:keys [x y] kk :k} rows :when (and beta (= yr (year-of kk)))]
                             [kk (hz/predict beta x) y]))))))))

(defn run [{:keys [name]}]
  (let [run-name (or name "weekly")
        out-dir (str "data/runs/" run-name)
        _ (io/make-parents (str out-dir "/x"))
        daily (read-daily)
        wk (weeks daily)
        n (count wk)
        w (tr/wealth (map :mkt wk))
        ;; daily returns up to the close of each week
        offsets (vec (reductions + 0 (map #(count (:days %)) wk)))
        all-days (vec (map second daily))
        daily-before (fn [k] (subvec all-days (max 0 (- (offsets (inc k)) 260)) (offsets (inc k))))
        pe10 (us/read-cape)
        year-of (fn [k] (quot (:month (wk k)) 12))
        raw (mapv #(inputs wk w daily-before pe10 %) (range n))
        targets {"20% within 52w" [52 0.2] "20% within 26w" [26 0.2] "20% within 13w" [13 0.2] "10% within 13w" [13 0.1]}
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        ;; exposure decided at week k's close earns week k+1
        weeks-in (fn [lo hi] (filter #(and (raw %) (< (inc %) n) (<= lo (year-of %) hi)) (range n)))
        strat (fn [ks ex] (tr/backtest (mapv #(:mkt (wk (inc %))) ks) (mapv #(:rf (wk (inc %))) ks)
                                       (mapv ex ks) {:cost 0.001 :periods 52}))
        sma40 (fn [k] (if (neg? (nth (raw k) 2)) 0.0 1.0))
        month-end-sma (let [mw (into {} (for [k (range n)] [(:month (wk k)) (w (inc k))]))]
                        (fn [k] (let [m (dec (:month (wk k)))
                                      xs (keep mw (range (- m 9) (inc m)))]
                                  (if (and (mw m) (>= (mw m) (/ (reduce + xs) (count xs)))) 1.0 0.0))))
        vol-target (let [target (us/sd all-days)] (fn [k] (min 1.0 (/ target (max 1e-9 (nth (raw k) 3))))))
        line (fn [label r]
               (say (format "    %-26s annual %6.2f%%  vol %5.2f%%  sharpe %5.2f  maxdd %5.1f%%  invested %3.0f%%  switches %d"
                            label (* 100 (:annual r)) (* 100 (:vol r)) (:sharpe r) (* 100 (:max-drawdown r))
                            (* 100 (:invested r)) (:switches r))))]
    (say "The trader's test, weekly: P(US market falls X below this week's close within h weeks), out of")
    (say "sample. Prior, discount and thresholds chosen on 1950-1989; 1990 on is held out. 10bp a switch.")
    (doseq [[tname [h depth]] (sort targets)]
      (let [rows (vec (for [k (range n) :let [x (raw k)] :when x]
                        {:k k :x (double-array (cons 1.0 (map #(if % (double %) 0.0) x)))
                         :y (tr/drawdown-ahead? w (inc k) h depth)}))
            grid (for [prior [10.0 100.0] discount [1.0 0.99 0.98]]
                   (let [ps (predictions rows year-of h prior discount 1935)]
                     {:prior prior :discount discount :preds ps
                      :ls (us/log-score (filter #(<= 1950 (year-of (first %)) 1989) ps))}))
            best (apply max-key :ls grid)
            preds (:preds best)
            p-at (into {} (map (juxt first second) preds))
            in-yrs (fn [lo hi] (filter #(and (some? (nth % 2)) (<= lo (year-of (first %)) hi)) preds))
            base (let [ys (map #(nth % 2) (in-yrs 1935 1949))] (/ (count (filter true? ys)) (double (count ys))))
            ex-model (fn [tau] (fn [k] (if (< (p-at k 0.0) tau) 1.0 0.0)))
            veto (fn [tau] (fn [k] (max (sma40 k) ((ex-model tau) k))))
            both (fn [tau] (fn [k] (min (sma40 k) ((ex-model tau) k))))
            tune (weeks-in 1950 1989)
            held (weeks-in 1990 2100)
            taus (map #(* % base) [1.0 1.5 2.0 2.5 3.0 4.0])
            low-taus (map #(* % base) [0.25 0.5 0.75 1.0])
            pick (fn [f ts] (apply max-key #(:sharpe (strat tune (f %))) ts))
            tau (pick ex-model taus) tau-veto (pick veto low-taus) tau-both (pick both taus)]
        (say)
        (say (format "%s  (prior %.0f, discount %.2f, base %.3f; thresholds model %.3f, veto %.3f, both %.3f)"
                     tname (:prior best) (:discount best) base tau tau-veto tau-both))
        (doseq [[era lo hi] [["tune 1950-89" 1950 1989] ["held out 1990-" 1990 2100]]]
          (let [ps (in-yrs lo hi) ys (map #(nth % 2) ps)]
            (say (format "  %-15s weeks %5d pos %4d  auc %.3f  ap %.3f  bss %.3f" era (count ps) (count (filter true? ys))
                         (hz/auc (map second ps) ys) (hz/avg-precision (map second ps) ys)
                         (- 1.0 (/ (hz/brier (map second ps) ys) (hz/brier (repeat (count ps) base) ys)))))))
        (doseq [[era ks] [["tune 1950-89" tune] ["held out 1990-" held]]]
          (say (str "  strategies, " era))
          (line "buy and hold" (strat ks (constantly 1.0)))
          (line "10-month average (monthly)" (strat ks month-end-sma))
          (line "40-week average" (strat ks sma40))
          (line "volatility managed" (strat ks vol-target))
          (line "model" (strat ks (ex-model tau)))
          (line "model and 40-week" (strat ks (both tau-both)))
          (line "40-week unless low risk" (strat ks (veto tau-veto)))
          (say (str "      Sharpe by threshold: model " (str/join " " (for [t taus] (format "%.2f" (:sharpe (strat ks (ex-model t))))))
                    "; veto " (str/join " " (for [t low-taus] (format "%.2f" (:sharpe (strat ks (veto t)))))))))
        (let [[k-now p-now] (last preds)
              held (map second (in-yrs 1990 2100))
              m (:month (wk k-now))]
          (say (format "  now (week to %04d-%02d): P = %.3f, above %.0f%% of held-out weeks; base rate %.3f"
                       (quot m 12) (inc (rem m 12)) p-now
                       (* 100 (/ (count (filter #(< % p-now) held)) (double (count held)))) base)))
        (say "  calibration, held out: mean predicted vs observed by fifth")
        (let [ps (vec (sort-by second (in-yrs 1990 2100))) m (count ps)]
          (doseq [qi (range 5) :let [c (subvec ps (quot (* qi m) 5) (quot (* (inc qi) m) 5))]]
            (say (format "    %.3f  %.3f  (n=%d)" (/ (reduce + (map second c)) (count c))
                         (/ (count (filter #(true? (nth % 2)) c)) (double (count c))) (count c)))))
        (say "  held-out onsets (the first week a fall starts within h): probability 26, 13, 8, 4, 2, 1 weeks before")
        (doseq [[[k0 _ y0] [k1 _ y1]] (partition 2 1 (in-yrs 1989 2100))
                :when (and (true? y1) (false? y0)
                           (not-any? #(true? (nth % 2)) (filter #(< (- k1 26) (first %) k1) preds)))]
          (let [d (subs (str (quot (:month (wk k1)) 12) "-" (format "%02d" (inc (rem (:month (wk k1)) 12)))) 0 7)]
            (say (format "    %s  %s" d (str/join " " (for [b [26 13 8 4 2 1]] (format "%.2f" (double (p-at (- k1 b) 0.0)))))))))))
    (spit (str out-dir "/weekly.txt") (str out))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
