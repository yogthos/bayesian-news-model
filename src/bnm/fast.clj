(ns bnm.fast
  "Fast signals at the event model's quarter node (bead bnm-82j.2): monthly
  market and shipping series, aggregated to each quarter-end origin from
  what is published by then, as one nested group at the quarter nodes.

  Each family is a few inputs fixed in advance, from three operators on a
  monthly series known at origin t (only values whose availability is at
  or before t; the latest no more than `stale` months old):
  - `change`: the latest value less the one k months before it;
  - `surprise`: the latest value less the least-squares line through the
    w months before it, carried to the latest month (a break from trend);
  - `volatility`: the standard deviation of the monthly changes over the
    last twelve months.
  The families (docs/PHASE-CHANGES.md, bnm-82j.2):
  - :commodities, world food, energy and fertiliser prices (the Pink
    Sheet's indices, logs), each operator times the country's exposure:
    food imports, net fuel imports, agriculture (shares, read two years
    back, fixed before the shock and past revisions);
  - :fx, the exchange rate against the dollar (logs, end of month) and the
    10-year sovereign yield's spread over the US's;
  - :ports, the country's port calls (logs) against the same three months
    a year before, their surprise and volatility, and transits through the
    world's chokepoints against a year before times trade openness.
  Above the countries a place reads its countries' mean of each input over
  those known; an unknown input is nil (the lattice's running mean).

  `with-fast` puts a family on a world-norel spec (bnm.world-run): the
  quarter nodes read it as one nested group (the node with it against the
  node without it in the averaging, as trials 43-45 nested links); the
  year and half-decade nodes read nothing new, and see it only through
  the quarters' states passed up."
  (:require [bnm.stocks :as sk]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- series --------------------------------------------------------------------

(defn month-of
  "\"2008-09-30\" -> 2008 * 12 + 8."
  ^long [s]
  (+ (* 12 (parse-long (subs s 0 4))) (dec (parse-long (subs s 5 7)))))

(defn read-series
  "{[unit var] series} from lines `unit var freq period-end available value`
  (data/fast/series.tsv, scripts/fast-data.py), values as they are."
  [path]
  (with-open [r (io/reader path)]
    (let [acc (reduce (fn [acc line]
                        (let [[unit v _ pend avail value] (str/split line #"\t" -1)]
                          (update acc [unit v] (fnil conj []) [(month-of pend) (month-of avail) (parse-double value)])))
                      {} (line-seq r))]
      (update-vals acc sk/series))))

(def stale "Months a latest value may lag the origin." 6)

(defn change
  "The latest value known at t less the one `k` months before it."
  [s t k]
  (when s (sk/change s t k {:stale stale})))

(defn volatility
  "The standard deviation of the monthly changes over the twelve months to
  the latest value known at t."
  [s t]
  (when s (sk/volatility s t 12 {:stale stale})))

(defn trend-at
  "The least-squares line through points [x y] evaluated at x0, nil with
  fewer than `min-n` points or no spread in x."
  [pts x0 min-n]
  (let [n (count pts)]
    (when (>= n min-n)
      (let [mx (/ (reduce + (map first pts)) (double n))
            my (/ (reduce + (map second pts)) (double n))
            sxx (reduce + (map #(let [d (- (first %) mx)] (* d d)) pts))
            sxy (reduce + (map #(* (- (first %) mx) (- (second %) my)) pts))]
        (when (pos? sxx)
          (+ my (* (/ sxy sxx) (- x0 mx))))))))

(defn surprise
  "The latest value known at t less the trend through the `w` months
  before it (at least half of them known), carried to its month."
  [s t w]
  (when s
    (let [ks (sk/known s t)]
      (when-let [[l xl] (peek ks)]
        (when (<= (- t l) stale)
          (let [pts (filterv #(<= (- l w) (first %) (dec l)) ks)]
            (some->> (trend-at pts l (max 3 (quot w 2))) (- xl))))))))

(defn year-on-year
  "The mean of the latest three monthly values known at t less the mean of
  the same three months a year before, all six known (seasonal series)."
  [s t]
  (when s
    (let [ks (sk/known s t)]
      (when-let [[l] (peek ks)]
        (when (<= (- t l) stale)
          (let [by (into {} ks)
                now (keep by [l (- l 1) (- l 2)])
                ago (keep by [(- l 12) (- l 13) (- l 14)])]
            (when (and (= 3 (count now)) (= 3 (count ago)))
              (/ (- (reduce + now) (reduce + ago)) 3.0))))))))

(defn- x* [a b] (when (and a b) (* (double a) (double b))))

;; --- the families ----------------------------------------------------------------

(def commodities ["fast/food" "fast/energy" "fast/fertiliser"])

(def families
  "Each family's input names, in order: fixed in advance."
  {:commodities (vec (for [c ["food" "energy" "fertiliser"] op ["change3" "surprise" "vol12"]] (keyword (str c "-" op "-x-exposure"))))
   :fx [:fx-change3 :fx-surprise :fx-vol12 :spread-change3 :spread-surprise :spread-vol12]
   :ports [:calls-yoy3 :calls-surprise :calls-vol12 :chokepoints-yoy3-x-open]})

(defn operators
  "[change surprise volatility] of `s` at t: k = 3, w = 24."
  [s t]
  [(change s t 3) (surprise s t 24) (volatility s t)])

(defn family-inputs
  "(fn [country t]) -> the family's inputs (nil where unknown), from `fast`
  ({[unit var] series}) and `exposure` (fn [country t] -> {:food :energy
  :fertiliser :open}, shares as fractions, nil where unknown)."
  [family fast exposure]
  (case family
    :commodities
    (let [world (memoize (fn [t] (mapv #(operators (fast ["world" %]) t) commodities)))]
      (fn [c t]
        (let [e (exposure c t)]
          (vec (mapcat (fn [ops k] (map #(x* % (get e k)) ops))
                       (world t) [:food :energy :fertiliser])))))
    :fx
    (fn [c t]
      (into (operators (fast [c "fast/fx"]) t) (operators (fast [c "fast/spread"]) t)))
    :ports
    (let [choke (memoize (fn [t] (year-on-year (fast ["world" "fast/chokepoints"]) t)))]
      (fn [c t]
        (let [s (fast [c "fast/portcalls"])]
          [(year-on-year s t) (surprise s t 12) (volatility s t) (x* (choke t) (:open (exposure c t)))])))))

;; --- the quarter node's nested group ------------------------------------------

(def quarter-scales #{:quarter-lead :quarter-w2})

(defn members-of
  "(fn [place]) -> the countries under a place of the tree `parent`
  ({place parent}), a country itself."
  [parent]
  (let [leaves (remove (set (vals parent)) (keys parent))
        below (reduce (fn [m c] (reduce #(update %1 %2 (fnil conj []) c) m (take-while some? (iterate parent c))))
                      {} leaves)]
    (fn [place] (get below place [place]))))

(defn mean-known
  "Each position's mean over the vectors `vs` among the values known,
  nil where none is."
  [vs width]
  (vec (for [i (range width)]
         (let [xs (keep #(nth % i) vs)]
           (when (seq xs) (/ (reduce + xs) (double (count xs))))))))

(defn with-fast
  "`sp` (a kind's world-norel spec, no relations) with the family's inputs
  (`inputs`, (fn [country t]), `width` of them) as one nested group
  `group` at the quarter nodes; `source` maps a country to the one whose
  inputs it reads (identity, or the placebo's other country). Every input
  is cached by country and origin, so both quarter nodes and every level
  read the same values."
  [sp inputs width group source]
  (when (:relations sp)
    (throw (ex-info "bnm.fast/with-fast: the spec already has relations" {})))
  (let [members (members-of (:parent sp))
        at (memoize (fn [c t] (inputs (source c) t)))
        qs (filter quarter-scales (map :name (:scales sp)))]
    (-> sp
        (update :without-at (fn [w] (reduce #(update %1 %2 (fnil disj #{}) :relations) w qs)))
        (assoc :relations (fn [scale place t]
                            {:x (if (quarter-scales scale)
                                  (mean-known (map #(at % t) (members place)) width)
                                  [])
                             :reads []})
               :relation-width (into {} (for [{n :name} (:scales sp)] [n (if (quarter-scales n) width 0)]))
               :relation-groups (into {} (for [n qs] [n {group (vec (range width))}]))
               :relation-nest true))))
