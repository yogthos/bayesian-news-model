(ns bnm.scanner
  "The scanner (docs/PLAN.md 1.6, 1.7; bead bnm-56c.6): every stock with
  an invariant measured, ranked each period by the pooled hazard
  (bnm.hazard), including types of stock with no break on record; and
  the hidden buffers, stocks whose accounts stop closing.

  A stock is a bnm.buffers record with its series {role [obs]}, obs {:t
  :v :known} (t in decimal years, the period's end). Everything as of a
  date reads only what was known by then (`known-view`), and a series
  measured more often than yearly is read on a yearly grid at its latest
  known period (the same month of earlier years; flows as the mean of
  the twelve months to each point, already rates per year), so that
  displacement windows are years and seasonal stocks compare."
  (:require [bnm.buffers :as b]
            [bnm.hazard :as hz]
            [bnm.invariants :as iv]))

(def flow-roles #{:outflow :inflow :income})

(defn- spacing
  "The median gap between successive t, or nil with fewer than 2."
  [os]
  (let [ts (map :t os) ds (sort (map - (rest ts) ts))]
    (when (seq ds) (nth ds (quot (count ds) 2)))))

(defn- frac-close? [a b] (let [d (Math/abs (- (double a) (double b))) f (- d (Math/floor d))] (or (< f 0.02) (> f 0.98))))

(defn known-view
  "The observations of one series known by `as-of`, by t, on a yearly
  grid when the series is sub-annual: stocks sampled at the latest known
  period's month, flows (`flow?`) the mean over the year to each grid
  point."
  [os as-of flow?]
  (let [k (vec (sort-by :t (filter #(and (some? (:v %)) (<= (double (:known %)) (double as-of))) os)))]
    (if (or (< (count k) 2) (> (double (spacing k)) 0.9))
      k
      (let [t* (:t (peek k))
            per (max 1 (Math/round (/ 1.0 (double (spacing k)))))
            ;; grid points sit whole years before t*, exactly, so a
            ;; one-year window holds two of them
            grid (mapv #(assoc % :t (- t* (Math/round (- t* (double (:t %))))))
                       (filter #(frac-close? (:t %) t*) k))]
        (if-not flow?
          grid
          (vec (keep (fn [g]
                       (let [w (filter #(and (> (:t %) (- (:t g) 1.0 1e-6)) (<= (:t %) (+ (:t g) 1e-6))) k)]
                         (when (>= (* 2 (count w)) per)
                           (assoc g :v (/ (reduce + 0.0 (map :v w)) (count w))
                                  :known (reduce max (map :known w))))))
                     grid)))))))

(defn view
  "Every role's `known-view` as of `as-of`."
  [series as-of]
  (into {} (for [[role os] series] [role (known-view os as-of (contains? flow-roles role))])))

(defn invariant-rows
  "bnm.buffers' invariants of a stock as of `as-of` (windows 1 and 3)."
  [rec series as-of]
  (b/invariants rec (view series as-of) as-of {:windows [1 3]}))

(defn stock-features
  "The registered features of a stock as of `as-of`."
  [rec series as-of]
  (hz/features (invariant-rows rec series as-of)))

(defn rank
  "Stocks [{:record :features ...}] with an invariant measured, scored
  by the pooled hazard of `model` and ranked: each with :p, :rank (1 the
  highest) and :pct (the share of stocks ranked at or above it)."
  [model stocks]
  (let [scored (->> stocks
                    (filter #(hz/measured? (:features %)))
                    (map #(assoc % :p (hz/pooled model (:features %))))
                    (sort-by (comp - :p))
                    vec)
        n (count scored)]
    (vec (map-indexed (fn [i s] (assoc s :rank (inc i) :pct (/ (inc i) (double n)))) scored))))

;; --- hidden buffers (1.7) ------------------------------------------------------------------

(defn gap-series
  "The relative gap between two measures of one stock, a and b ([obs]),
  at the t both have: (a - b) / mean(|a|, |b|)."
  [a b]
  (let [bm (into {} (map (juxt #(Math/round (* 12.0 (double (:t %)))) identity)) b)]
    (vec (for [o (sort-by :t a)
               :let [q (bm (Math/round (* 12.0 (double (:t o)))))]
               :when (and q (some? (:v o)) (some? (:v q)))
               :let [den (/ (+ (Math/abs (double (:v o))) (Math/abs (double (:v q)))) 2.0)]
               :when (pos? den)]
           {:t (:t o) :v (/ (- (double (:v o)) (double (:v q))) den) :known (max (:known o) (:known q))}))))

(defn residual-flag
  "A flag when a residual series (an identity's residual, or the gap
  between two measures, as shares) has stopped closing as of `as-of`:
  its latest known absolute value above `floor` and stretched past
  `z-cut` against its own past `w` values (bnm.invariants/stretch on the
  absolute residual). {:value :z :flag bool :t}, nil with no data."
  [os as-of {:keys [floor z-cut w] :or {floor 0.02 z-cut 2.0 w 10}}]
  (let [k (vec (sort-by :t (filter #(<= (double (:known %)) (double as-of)) os)))]
    (when (seq k)
      (let [xs (mapv #(Math/abs (double (:v %))) k)
            i (dec (count xs))
            z (when (>= i 3) (iv/stretch xs i w (min w (max 3 (quot w 2)))))
            v (peek xs)]
        {:t (:t (peek k)) :value (:v (peek k)) :z z
         :flag (boolean (and z (> v floor) (> z z-cut)))}))))
