(ns bnm.warm-up
  "The base's warm-up rows (bead bnm-0cf). A run's year node forecasts from
  its first origin, but until it has read a published outcome of the kind
  it sits at its prior: p near 0.5 for every country (t44-world-norel:
  food price shocks at origins 2003-2006, protest and violence surges
  1979-1981, currency crises 1961-1964, inflation crises 1960-1963, market
  crashes 1961-1963, the catalogues that start in 1870 to 1873). Those
  rows are not forecasts: a fit or recalibration of the base on them reads
  a constant, and a simulation drawing outcomes from them draws coin
  flips. Every fit leaves them out; scoring may keep them.

  An origin t is a warm-up origin when its fit-cut, the last origin whose
  outcome is published by the end of t (window (o, o+2] closed and the
  catalogue's known-lag passed), is before the kind's first origin, the
  first year-end origin with a resolved outcome."
  (:require [bnm.kinds :as kinds]))

(defn fit-cut
  "The last origin whose outcome is published by year end t: its window
  (o, o+2] closed and the catalogue's `lag` months passed."
  [t lag]
  (- t 2 (long (Math/ceil (/ (double lag) 12.0)))))

(defn warm-up?
  "Whether the base's forecast at origin `t` came before it had read any
  published outcome of a kind whose first origin is `first-origin`."
  [t lag first-origin]
  (< (fit-cut t lag) first-origin))

(defn first-origin
  "The first year-end origin (a year) of a year node ({place (sorted-map
  month [p y])}, bnm.relation-diag/read-year) with a resolved outcome; nil
  without one."
  [node]
  (let [ts (for [[_ byo] node [o [_ y]] byo :when (and (some? y) (= 11 (mod o 12)))] (quot o 12))]
    (when (seq ts) (reduce min ts))))

(defn warm-up-origins
  "The set of origin years of `node` that are warm-up origins at `lag`
  (months): every year of the node's months whose fit-cut is before its
  first origin."
  [node lag]
  (if-let [f (first-origin node)]
    (into #{} (for [[_ byo] node o (keys byo) :let [t (quot o 12)] :when (warm-up? t lag f)] t))
    #{}))

(defn drop-node
  "`node` without its warm-up origins, at every place."
  [node lag]
  (let [w (warm-up-origins node lag)]
    (into {} (for [[place byo] node
                   :let [kept (into (sorted-map) (remove (fn [[o _]] (w (quot o 12))) byo))]
                   :when (seq kept)]
               [place kept]))))

(defn mark
  "`rows` ({:t}) each with :warm-up, for a kind whose first origin is
  `first-origin` (the kind's, not the first of the rows passed)."
  [rows lag first-origin]
  (mapv #(assoc % :warm-up (warm-up? (:t %) lag first-origin)) rows))

(defn fit-rows
  "The rows a fit may read: those not marked :warm-up."
  [rows]
  (filterv (complement :warm-up) rows))

(def ^:private lags (delay (update-vals (kinds/catalogues) #(:known-lag % 0))))

(defn known-lag
  "A kind's catalogue known-lag in months (0 where absent), the catalogues
  read once."
  [kind]
  (get @lags kind 0))
