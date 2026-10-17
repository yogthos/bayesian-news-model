(ns bnm.panel
  "The trader's test over a panel of stock markets (docs/TRADING.md): the US
  and 21 developed markets, month-end total returns in dollars from Ken
  French's library, 1975 on. For each market at the end of each month, the
  probability that it falls 20% (or 10%) below today's level within 6 or 12
  months, from one hazard pooled over the markets, and what a dollar investor
  switching between each market and US bills on it would have earned.

  Inputs per market: its own trend, momentum, volatility and drawdown; its
  valuation (earnings and dividend yields, from French's annual ratios, used
  from June of the next year), level and gap from its own ten-year mean;
  the named credit and house price stocks of bnm.named; and global
  conditions (US CAPE, the GZ spread, the VIX, credit growth abroad, the US
  curve), with the event model's state where a training run is given.

  Inputs are kept if their univariate relation to the target has the same
  sign in 1978-1991 and 1992-2004. Prior, discount and trading threshold
  are chosen on 1980-2004; 2005 on is held out.

    jolt -A:tools -M -m bnm.panel [--name panel] [--events v3-q]"
  (:require [bnm.hazard :as hz]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [bnm.trade :as tr]
            [bnm.french :as fr]
            [bnm.trader :as us]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def countries
  {"Austria" "AUT" "Austrlia" "AUS" "Belgium" "BEL" "Canada" "CAN" "Denmark" "DNK" "Finland" "FIN"
   "France" "FRA" "Germany" "DEU" "HongKong" "HKG" "Ireland" "IRL" "Italy" "ITA" "Japan" "JPN"
   "Nethrlnd" "NLD" "NewZland" "NZL" "Norway" "NOR" "Singapor" "SGP" "Spain" "ESP" "Sweden" "SWE"
   "Swtzrlnd" "CHE" "UK" "GBR"})

(defn- ratio-at
  "The latest annual ratio usable at month t: year Y's from June of Y+1."
  [ratios k t]
  (let [y (dec (quot (- t 5) 12))]
    (some-> (ratios y) k)))

(def input-names
  [:mom12 :mom1 :sma10 :vol6 :vol-ratio :drawdown12
   :ep :ep-gap :yield :us-cape :gz :vix :us-curve :world-credit3
   :credit3 :hh-credit3 :credit-gap :dsr3 :house3 :rzone-hh :curve :vix-x-credit3 :world-credit-x-open])

(def financial-picks
  "Where the named financial inputs sit in bnm.named/financial's output."
  {:credit3 0 :hh-credit3 1 :credit-gap 2 :dsr3 3 :house3 4 :rzone-hh 5 :curve 11
   :vix-x-credit3 16 :world-credit-x-open 15})

(defn- market-inputs
  "Inputs for unit u at month t from its wealth (a map month -> wealth at the
  month's end)."
  [wealth ret ratios u t us-data ff named-f event]
  (let [ws (fn [m] (wealth m))
        hist? (and (ws (- t 36)) (ws t))]
    (when hist?
      (let [rets (fn [k] (keep #(ret %) (range (- t (dec k)) (inc t))))
            vol6 (us/sd (rets 6))
            ep (ratio-at ratios :ep t)
            eps (keep #(ratio-at ratios :ep (- t (* 12 %))) (range 1 11))
            {:keys [pe10 gz]} us-data
            fin (named-f u t)
            pickf (fn [k] (nth fin (financial-picks k)))]
        (into
         [(Math/log (/ (ws t) (ws (- t 12))))
          (Math/log (/ (ws t) (ws (- t 1))))
          (Math/log (/ (ws t) (/ (reduce + (map ws (range (- t 9) (inc t)))) 10.0)))
          vol6
          (/ vol6 (max 1e-6 (us/sd (rets 36))))
          (- 1.0 (/ (ws t) (apply max (map ws (range (- t 11) (inc t))))))
          ep
          (when (and ep (>= (count eps) 5)) (- ep (/ (reduce + eps) (count eps))))
          (ratio-at ratios :yld t)
          (us/lg (pe10 (- t 3)))
          (gz (dec t))
          (nth fin 13) (nth fin 14) (nth fin 12)]
         (concat (map pickf [:credit3 :hh-credit3 :credit-gap :dsr3 :house3 :rzone-hh :curve
                             :vix-x-credit3 :world-credit-x-open])
                 event))))))

(defn- auc-of [rows i label-key]
  (let [rs (filter #(and (some? (label-key %)) (some? (nth (:raw %) i))) rows)]
    (when (> (count (filter label-key rs)) 10)
      (hz/auc (map #(nth (:raw %) i) rs) (map label-key rs)))))

(defn run [{:keys [name events]}]
  (let [run-name (or name "panel")
        out-dir (str "data/runs/" run-name)
        _ (io/make-parents (str out-dir "/x"))
        fr (us/read-french)
        us-ret (into {} (map-indexed (fn [i r] [(+ (:start fr) i) r]) (:mkt fr)))
        rf (into {} (map-indexed (fn [i r] [(+ (:start fr) i) r]) (:rf fr)))
        markets (assoc (into {} (for [[f iso] countries
                                      :let [c (fr/read-country (str us/dir "intl/" f ".Dat"))]]
                                  [iso c]))
                       "USA" {:ret (into {} (filter #(>= (quot (key %) 12) 1975) us-ret)) :ratios {}})
        wealth (into {} (for [[u {:keys [ret]}] markets]
                          [u (let [ms (sort (keys ret))]
                               (into {(dec (first ms)) 1.0}
                                     (map vector ms (rest (reductions (fn [w m] (* w (+ 1.0 (ret m)))) 1.0 ms)))))]))
        us-data {:pe10 (us/read-cape)
                 :gz (us/read-csv-col "ebp.csv" 1)}
        variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        months (range (* 12 1978) (inc (apply max (keys rf))))
        wc (nm/world-credit db (distinct (map first (keys db))) months)
        [_ named-f] (nm/financial db wc)
        es (if events (nm/load-event-state events) {})
        n-event (if events (count nm/event-labels) 0)
        names (into input-names (when events nm/event-labels))
        targets {"20% within 12m" [12 0.2] "20% within 6m" [6 0.2] "10% within 6m" [6 0.1]}
        label (fn [u t h depth]
                (let [w (wealth u) w0 (w t)]
                  (when (and w0 (w (+ t h)))
                    (boolean (some #(when-let [x (w %)] (<= x (* (- 1.0 depth) w0))) (range (inc t) (+ t h 1)))))))
        rows (vec (for [u (sort (keys markets)) t months
                        :let [{:keys [ret ratios]} (markets u)
                              ev (when events (or (some-> (es [u (quot t 3)]) vec) (repeat n-event nil)))
                              raw (market-inputs (wealth u) ret ratios u t us-data fr named-f ev)]
                        :when (and raw (ret (inc t)))]
                    {:unit u :t t :raw raw
                     :labels (into {} (for [[k [h d]] targets] [k (label u t h d)]))}))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        in-years (fn [ps lo hi] (filter #(<= lo (quot (first %) 12) hi) ps))
        fmt (fn [x] (if x (format "%.3f" (double x)) "  -  "))
        ;; a portfolio holding each market equally, each switched on its own exposure
        portfolio (fn [ts ex]
                    (let [per-month (for [t ts
                                          :let [us (filter #(get-in markets [% :ret (inc t)]) (keys markets))
                                                rs (for [u us :let [e (double (ex u t))]]
                                                     (+ (* e (get-in markets [u :ret (inc t)])) (* (- 1.0 e) (rf (inc t)))))]
                                          :when (seq us)]
                                      [(/ (reduce + rs) (count rs)) (rf (inc t))
                                       (/ (reduce + (map #(double (ex % t)) us)) (count us))])]
                      (tr/backtest (map first per-month) (map second per-month) (map #(nth % 2) per-month) {:cost 0.001})))
        per-market (fn [ts ex u]
                     (let [ts (filter #(get-in markets [u :ret (inc %)]) ts)]
                       (when (> (count ts) 24)
                         (tr/backtest (map #(get-in markets [u :ret (inc %)]) ts) (map #(rf (inc %)) ts)
                                      (map #(ex u %) ts) {:cost 0.001}))))
        sma (into {} (for [u (keys markets)]
                       [u (let [w (wealth u)]
                            (fn [t] (let [xs (keep w (range (- t 9) (inc t)))]
                                      (if (and (w t) (>= (w t) (/ (reduce + xs) (count xs)))) 1.0 0.0))))]))
        sma-ex (fn [u t] ((sma u) t))
        line (fn [label r]
               (say (format "    %-22s annual %6.2f%%  vol %5.2f%%  sharpe %5.2f  maxdd %5.1f%%  invested %3.0f%%"
                            label (* 100 (:annual r)) (* 100 (:vol r)) (:sharpe r) (* 100 (:max-drawdown r))
                            (* 100 (:invested r)))))]
    (say "The trader's test over" (count markets) "markets: P(market falls X below today's level within h months),")
    (say "one hazard pooled over markets, out of sample. Inputs kept if their sign agrees in 1978-91 and")
    (say "1992-2004; prior, discount and threshold chosen on 1980-2004; 2005 on is held out. A dollar")
    (say "investor holds each market equally, switching each to US bills on its own signal, 10bp a switch.")
    (doseq [[tname [h _]] (sort targets)]
      (let [lk #(get-in % [:labels tname])
            keep-idx (vec (for [i (range (count names))
                                :let [a1 (auc-of (filter #(<= 1978 (quot (:t %) 12) 1991) rows) i lk)
                                      a2 (auc-of (filter #(<= 1992 (quot (:t %) 12) 2004) rows) i lk)]
                                :when (and a1 a2 (or (and (> a1 0.52) (> a2 0.52)) (and (< a1 0.48) (< a2 0.48))))]
                            i))
            prows (vec (for [r rows]
                         {:unit (:unit r) :t (:t r) :y (lk r)
                          :x (double-array (concat [1.0]
                                                   (for [i keep-idx] (if-let [v (nth (:raw r) i)] (double v) 0.0))
                                                   (for [i keep-idx] (if (nth (:raw r) i) 1.0 0.0))))}))
            grid (for [prior [10.0 100.0] discount [1.0 0.99]]
                   (let [ps (us/predictions prows h prior discount 1980)]
                     {:prior prior :discount discount :preds ps :ls (us/log-score (in-years ps 1980 2004))}))
            best (apply max-key :ls grid)
            preds (:preds best)
            p-at (into {} (for [[t p _ u] preds] [[u t] p]))
            known (fn [lo hi] (filter #(some? (nth % 2)) (in-years preds lo hi)))
            base (let [ys (map #(nth % 2) (known 1980 1989))] (/ (count (filter true? ys)) (double (count ys))))
            ex-model (fn [tau] (fn [u t] (if (< (get p-at [u t] 1.0) tau) 1.0 0.0)))
            tune-ts (filter #(<= 1980 (quot % 12) 2004) months)
            held-ts (filter #(<= 2005 (quot % 12)) (butlast months))
            taus (map #(* % base) [1.0 1.25 1.5 2.0 2.5 3.0 4.0])
            tau (apply max-key #(:sharpe (portfolio tune-ts (ex-model %))) taus)
            both (fn [tau] (fn [u t] (min ((ex-model tau) u t) (sma-ex u t))))
            tau-both (apply max-key #(:sharpe (portfolio tune-ts (both %))) taus)]
        (say)
        (say (format "%s  (prior %.0f, discount %.2f, base %.3f, threshold %.3f, with the 10-month rule %.3f)"
                     tname (:prior best) (:discount best) base tau tau-both))
        (say "  inputs:" (str/join " " (map #(clojure.core/name (names %)) keep-idx)))
        (doseq [[era lo hi] [["tune 1980-2004" 1980 2004] ["held out 2005-" 2005 2100]]]
          (let [ps (known lo hi) ys (map #(nth % 2) ps)
                b (let [ys0 (map #(nth % 2) (known 1975 (dec lo)))] (/ (count (filter true? ys0)) (double (max 1 (count ys0)))))
                b (if (zero? b) base b)]
            (say (format "  %-15s n=%5d pos=%4d  auc %s  within-month auc %s  ap %s  bss %s" era (count ps) (count (filter true? ys))
                         (fmt (hz/auc (map second ps) ys))
                         (fmt (hz/auc-within (map (fn [[t p y]] [t p y]) ps)))
                         (fmt (hz/avg-precision (map second ps) ys))
                         (fmt (- 1.0 (/ (hz/brier (map second ps) ys) (hz/brier (repeat (count ps) b) ys))))))))
        (doseq [[era ts] [["tune 1980-2004" tune-ts] ["held out 2005-" held-ts]]]
          (say (str "  equal-weight portfolio, " era))
          (line "buy and hold" (portfolio ts (constantly 1.0)))
          (line "10-month average" (portfolio ts sma-ex))
          (line "model" (portfolio ts (ex-model tau)))
          (line "model and 10-month" (portfolio ts (both tau-both))))
        (let [rs (for [u (sort (keys markets))
                       :let [bh (per-market held-ts (constantly 1.0) u)
                             sm (per-market held-ts sma-ex u)
                             mo (per-market held-ts (ex-model tau) u)
                             mb (per-market held-ts (both tau-both) u)]
                       :when bh]
                   [u bh sm mo mb])
              beats (fn [i j] (count (filter #(> (:sharpe (nth % i)) (:sharpe (nth % j))) rs)))]
          (say (format "  held out, per market (%d): model beats buy and hold on Sharpe in %d, the 10-month rule in %d;"
                       (count rs) (beats 3 1) (beats 3 2)))
          (say (format "    model and 10-month beats buy and hold in %d, the 10-month rule in %d; median max drawdown %s"
                       (beats 4 1) (beats 4 2)
                       (str/join " / " (for [i [1 2 3 4]]
                                         (format "%.0f%%" (* 100 (nth (sort (map #(:max-drawdown (nth % i)) rs)) (quot (count rs) 2)))))))))
        (say "  calibration, held out: mean predicted vs observed by fifth")
        (let [ps (vec (sort-by second (known 2005 2100))) n (count ps)]
          (doseq [qi (range 5) :let [c (subvec ps (quot (* qi n) 5) (quot (* (inc qi) n) 5))]]
            (say (format "    %.3f  %.3f  (n=%d)" (/ (reduce + (map second c)) (count c))
                         (/ (count (filter #(true? (nth % 2)) c)) (double (count c))) (count c)))))
        (say "  mean probability across markets, and share falling within h, before held-out episodes")
        (doseq [[ep t0] [["2008" (+ (* 12 2007) 5)] ["2011" (+ (* 12 2011) 0)] ["2020" (+ (* 12 2019) 8)] ["2022" (+ (* 12 2021) 8)]]]
          (say (str "    " ep "  "
                    (str/join "  " (for [t (range t0 (+ t0 12) 3)
                                         :let [ps (filter #(= t (first %)) preds)]
                                         :when (seq ps)]
                                     (format "%s %.2f/%.2f" (st/month-str t) (/ (reduce + (map second ps)) (count ps))
                                             (/ (count (filter #(true? (nth % 2)) ps)) (double (count ps)))))))))))
    (spit (str out-dir "/panel.txt") (str out))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
