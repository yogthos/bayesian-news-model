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

(defn- members
  "{place #{countries}}: every place above the countries, its countries."
  [parent countries]
  (reduce (fn [acc c] (reduce #(update %1 %2 (fnil conj #{}) c) acc (ancestors-of parent c))) {} countries))

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

(defn- left-out-mean
  "Place p's countries' mean at t from the sums, country c (value x) left
  out if it is one of them; nil where no other has a value."
  [s mem c x p]
  (let [[sm n] (s p) mine? (contains? (mem p) c)]
    (cond (nil? n) nil
          mine? (when (> n 1) (/ (- sm x) (dec n)))
          :else (/ sm n))))

(defn online
  "A factor model to feed one period at a time (observe): for a world run
  stepping through time, or any stream. `parent` the place tree, :discount
  (0.99) as decompose's."
  [parent {:keys [discount] :or {discount 0.99}}]
  {:parent parent :discount discount :models {} :scale nil :last-t nil :anc {}})

(defn- factor-of
  "A place's factor for country c (value x) from the sums: its countries'
  mean less its parent's, c left out of both; the root's, its mean."
  [s mem parent c x p]
  (when-let [mp (left-out-mean s mem c x p)]
    (if-let [up (parent p)]
      (when-let [mu (left-out-mean s mem c x up)] (- mp mu))
      mp)))

(defn observe
  "[{country {:level :common :own :by-level}} state'] for the countries'
  values `xs` ({country value}) at period t, after every earlier one: each
  country's parts under its level and loadings learned from the periods
  before, which then learn from this one."
  [{:keys [parent discount models scale last-t] :as st} t xs]
  (when (and last-t (<= (compare t last-t) 0))
    (throw (ex-info "bnm.factors/observe: periods must come in order" {:t t :last-t last-t})))
  (let [cs (sort (keys xs))
        ;; the scale of the series, from its first cross-section that varies
        scale (or scale (when (> (count cs) 1)
                          (let [v (vals xs) mx (/ (reduce + v) (count v))
                                var (/ (reduce + (map #(* (- % mx) (- % mx)) v)) (dec (count v)))]
                            (when (> var 1e-9) var))))
        anc (fn [c] (ancestors-of parent c))
        panel (into {} (for [[c x] xs] [c {t x}]))
        mem (members parent cs)
        s (sums panel parent t)
        model-of (fn [c]
                   (let [sc (or scale 1.0)]
                     (d/linear {:mean (into [0.0] (repeat (count (anc c)) 1.0))
                                :var (into [(* 10.0 sc)] (repeat (count (anc c)) 1.0))
                                :discount discount :s0 (* 0.1 sc) :n0 1.0})))
        ;; members must count every country seen, for the left-out means
        step (for [c cs :let [x (double (xs c))]]
               (let [fs (mapv #(or (factor-of s mem parent c x %) 0.0) (anc c))
                     mdl (or (models c) (model-of c))
                     [a & bs] (d/means mdl)
                     parts (mapv * bs fs)
                     common (reduce + 0.0 parts)]
                 [c {:level a :common common :own (- x a common) :by-level (zipmap (anc c) parts)}
                  (d/step mdl [{:x (into [1.0] fs) :y x}])]))]
    [(into {} (for [[c p _] step] [c p]))
     (assoc st :models (into models (for [[c _ m] step] [c m])) :scale scale :last-t t)]))

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
