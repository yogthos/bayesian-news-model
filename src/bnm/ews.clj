(ns bnm.ews
  "Early warnings of a critical transition (Scheffer et al. 2009; Dakos et
  al. 2012): as a system loses resilience near a tipping point it
  recovers more slowly from small shocks, so over a rolling window its
  fluctuations grow more autocorrelated and more variable, and the rate
  at which a departure decays falls (critical slowing down). Computed on a
  series' own part (bnm.factors), each window ending at the index it
  stands for, its linear trend removed first (a trend is not slowing
  down). With annual data a window is short (a dozen years, against the
  hundreds of points Dakos et al. use), and lag-1 autocorrelation over n
  detrended points is biased low by about (1 + 4 rho)/n + 2/n: levels
  compare across places only roughly, trends within a place better."
  (:require [bnm.factors :as fac]
            [bnm.stocks :as sk]))

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))

(defn- detrend
  "xs less their least-squares line."
  [xs]
  (let [n (count xs) ts (range n) mt (mean ts) mx (mean xs)
        stt (reduce + (map #(let [d (- % mt)] (* d d)) ts))
        b (if (pos? stt) (/ (reduce + (map #(* (- %1 mt) (- %2 mx)) ts xs)) stt) 0.0)]
    (mapv (fn [t x] (- x mx (* b (- t mt)))) ts xs)))

(defn kendall-tau
  "Kendall's tau-b of xs with time, ties counted: the trend of an
  indicator."
  [xs]
  (let [xs (vec xs) n (count xs)
        pairs (for [i (range n) j (range (inc i) n)] (compare (xs j) (xs i)))
        c (count (filter pos? pairs)) d (count (filter neg? pairs))
        n0 (/ (* n (dec n)) 2)
        ties (reduce + (for [k (vals (frequencies xs))] (/ (* k (dec k)) 2)))
        den (Math/sqrt (* (double n0) (- n0 ties)))]
    (if (pos? den) (/ (double (- c d)) den) 0.0)))

(defn indicators
  "One map per index of `xs` (nil for a missing value): {:ac1
  :log-variance :return-rate :ac1-trend :log-variance-trend} over the
  `:window` values ending there, or nil before a full window, over a
  missing value, or for a flat one: the lag-1 autocorrelation and the log
  variance (n - 2 degrees of freedom) of the detrended window, the return
  rate -log(ac1) (nil where ac1 is not positive), and the trends (Kendall's
  tau-b) once a full window of indicators is there (fewer make a trend of
  noise, two of them +-1 by construction)."
  [xs {:keys [window]}]
  (let [xs (vec xs) n (count xs)
        base (vec (for [i (range n)]
                    (when (>= i (dec window))
                      (let [w (subvec xs (- (inc i) window) (inc i))]
                        (when (every? some? w)
                          (let [rs (detrend w)
                                v (reduce + (map #(* % %) rs))]
                            (when (> v 1e-12)
                              (let [a (/ (reduce + (map * rs (rest rs))) v)]
                                {:ac1 a
                                 :log-variance (Math/log (/ v (- window 2)))
                                 :return-rate (when (pos? a) (- (Math/log a)))}))))))))]
    (vec (for [i (range n)]
           (when-let [b (base i)]
             (let [past (keep base (range (max 0 (- (inc i) window)) (inc i)))]
               (cond-> b
                 (>= (count past) window)
                 (assoc :ac1-trend (kendall-tau (map :ac1 past))
                        :log-variance-trend (kendall-tau (map :log-variance past))))))))))

(defn series-panel
  "{country (sorted-map year value)} of series `v` as known at each
  year's end (each value's publication applied: at the end of year Y,
  the figures for Y - 1)."
  [db cs v]
  (into {} (for [c cs :let [byy (into (sorted-map)
                                      (for [y (range 1950 2026)
                                            :let [x (some-> (db [c v]) (sk/value (+ 11 (* 12 y)) {:stale 18}))]
                                            :when x] [y (double x)]))]
                 :when (seq byy)] [c byy])))

(defn own-indicators
  "{country {year {indicator value}}}: the early warnings of each
  country's own part of a series (bnm.factors) over the `window` calendar
  years ending at each year (a missing year, nothing), each country's
  first own part (its level not yet learned) left out; causal."
  [panel tree window]
  (let [parts (fac/decompose panel tree {:discount 0.99})]
    (into {} (for [[c byy] parts
                   :let [ys (sort (keys byy))
                         y0 (first ys)
                         span (range (inc y0) (inc (last ys)))
                         sig (indicators (map #(get-in byy [% :own]) span) {:window window})]]
               [c (into {} (keep (fn [[y s]] (when s [y s])) (map vector span sig)))]))))

