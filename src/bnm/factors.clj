(ns bnm.factors
  "Every series split into what it shares and what is its own, on the
  place tree (docs/STOCK-FLOW.md; principle 6 of docs/PRINCIPLES.md:
  within, not level). A country's value at t is

    x_ct = a_c + sum over its ancestors l of b_cl F_lt + e_ct

  its own level a_c, its loadings b_cl on the factor of each place above it
  (the world's: the mean of the countries; a region's: its countries' mean
  less its parent's), and its own part e_ct. The factors are proxied by
  cross-sectional means (Pesaran 2006, common correlated effects; the
  hierarchy as Moench, Ng & Potter 2013), each leaving the country itself
  out, so a country's own shock stays in its own part. The split between
  the world's factor and a region's is a convention (factors are known
  only up to rotation); here the tree's means define it: the world's is
  what all countries share, a region's its departure from that. The level and
  loadings are a dynamic linear model per country (augur.dynamic/linear,
  discounted), learned from its values before t: the parts at t use only
  what was known by t.

  What one node passes another is then its own part, not a level that
  carries what the period and the place share; the common parts are what
  the region and world nodes carry."
  (:require [augur.dynamic :as d]
            [bnm.geo :as geo]))

(defn place-tree
  "{place parent} of `countries` on the gazetteer: country, subregion,
  region, world."
  [countries]
  (into {} (for [c countries
                 :let [sub (first (geo/parents c)) reg (some-> sub geo/parents first)]
                 :when (and sub reg)
                 kv [[c sub] [sub reg] [reg "world"]]]
             kv)))

(defn- ancestors-of [parent place]
  (vec (take-while some? (rest (iterate parent place)))))

(defn- sums
  "{place [sum count]} of the countries' values at t."
  [panel parent t]
  (reduce (fn [acc [c byt]]
            (if-let [x (get byt t)]
              (reduce #(update %1 %2 (fn [[s n]] [(+ (or s 0.0) x) (inc (or n 0))])) acc (ancestors-of parent c))
              acc))
          {} panel))

(defn factors
  "{t {place factor}}: at each t, the world's mean over the countries
  with a value then, and each place below it its countries' mean less its
  parent's (no one left out: the factors as published)."
  [panel parent]
  (let [ts (sort (set (mapcat keys (vals panel))))]
    (into (sorted-map)
          (for [t ts :let [s (sums panel parent t)
                           mean (fn [p] (when-let [[sm n] (s p)] (/ sm n)))]]
            [t (into {} (for [p (keys s) :let [mp (mean p) up (parent p)]]
                          [p (if up (- mp (or (mean up) mp)) mp)]))]))))

(defn online
  "A factor model to feed one period at a time (observe): for a world run
  stepping through time, or any stream. `parent` the place tree, :discount
  (0.99) as decompose's."
  [parent {:keys [discount] :or {discount 0.99}}]
  {:parent parent :discount discount :models {} :scale nil :last-t nil})

(defn- factor-of
  "A place's factor for a country with departure dc from the sums of
  departures: its countries' mean less its parent's, the country left out
  of both; the root's, its mean; nil where no other country has a value."
  [s parent dc p]
  (let [mean (fn [q] (let [[sm n] (s q)] (when (and n (> n 1)) (/ (- sm dc) (dec n)))))]
    (when-let [mp (mean p)]
      (if-let [up (parent p)]
        (when-let [mu (mean up)] (- mp mu))
        mp))))

(defn observe
  "[{country {:level :common :own :by-level}} state'] for the countries'
  values `xs` ({country value}) at period t, after every earlier one: each
  country's parts under its level and loadings learned from the periods
  before, which then learn from this one. The factors are means of
  departures from each country's own level (learned before t; a country
  new at t departs by nothing), so a country entering with a level far
  from the others' does not move theirs. Until a cross-section varies (its
  variance scales every prior) no model is made and each value is its own
  level."
  [{:keys [parent discount models scale last-t] :as st} t xs]
  (when (and last-t (<= (compare t last-t) 0))
    (throw (ex-info "bnm.factors/observe: periods must come in order" {:t t :last-t last-t})))
  (let [cs (sort (keys xs))
        scale (or scale (when (> (count cs) 1)
                          (let [v (vals xs) mx (/ (reduce + v) (count v))
                                var (/ (reduce + (map #(* (- % mx) (- % mx)) v)) (dec (count v)))]
                            (when (> var 1e-9) var))))
        anc (fn [c] (ancestors-of parent c))
        level (fn [c] (if-let [m (models c)] (first (d/means m)) (double (xs c))))
        dep (into {} (for [c cs] [c (- (double (xs c)) (level c))]))
        s (sums (into {} (for [[c v] dep] [c {t v}])) parent t)]
    (if-not scale
      [(into {} (for [c cs] [c {:level (double (xs c)) :common 0.0 :own 0.0 :by-level (zipmap (anc c) (repeat 0.0))}]))
       (assoc st :last-t t)]
      (let [step (for [c cs :let [x (double (xs c))]]
                   (let [fs (mapv #(or (factor-of s parent (dep c) %) 0.0) (anc c))
                         mdl (or (models c)
                                 (d/linear {:mean (into [x] (repeat (count (anc c)) 1.0))
                                            :var (into [(* 10.0 scale)] (repeat (count (anc c)) 1.0))
                                            :discount discount :s0 (* 0.1 scale) :n0 1.0}))
                         [a & bs] (d/means mdl)
                         parts (mapv * bs fs)
                         common (reduce + 0.0 parts)]
                     [c {:level a :common common :own (- x a common) :by-level (zipmap (anc c) parts)}
                      (d/step mdl [{:x (into [1.0] fs) :y x}])]))]
        [(into {} (for [[c p _] step] [c p]))
         (assoc st :models (into models (for [[c _ m] step] [c m])) :scale scale :last-t t)]))))

(defn decompose
  "{country {t {:level :common :own :by-level {place part}}}} of `panel`
  ({country (sorted-map t value)}) on the place tree `parent` ({place
  parent}), for every country and t with a value: level + common + own =
  the value, common the sum of :by-level, one part per place above the
  country. Each loading starts at 1 (the value less the means above it).
  Options: :discount (0.99), how fast the level and loadings may drift.
  The periods observed in turn (online, observe)."
  [panel parent opts]
  (let [ts (sort (set (mapcat keys (vals panel))))]
    (loop [[t & more] ts st (online parent opts) out {}]
      (if (nil? t)
        out
        (let [[parts st] (observe st t (into {} (for [[c byt] panel :let [x (get byt t)] :when x] [c x])))]
          (recur more st (reduce (fn [o [c p]] (assoc-in o [c t] p)) out parts)))))))
