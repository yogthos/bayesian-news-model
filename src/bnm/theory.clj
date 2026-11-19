(ns bnm.theory
  "A theory forecast as a lattice node's offset (bnm-ris): known priors as
  the base, the lattice's groups learning only what departs from it. At
  each origin a pooled logit on the theory's inputs is refit on the rows
  whose outcomes are known by then and applied to the origin's inputs, so
  the offset is point in time. The political quarters already read a known
  forecast this way (bnm.onsets' named model); this builds one for a node
  that has none."
  (:require [augur.logit :as lgt]
            [augur.math :as m]))

(defn offsets
  "{[place origin] log odds}: at each origin, a logit fit on every earlier
  row whose outcome is known by it (`target` (fn [place origin]) ->
  {:y :known-at} or nil; `inputs` (fn [place origin]) -> vector, a nil
  entry the mean, 0), with prior precision `prior` (augur.logit/fit's),
  applied to the origin's inputs. An origin with no known outcome, or none
  of either kind, has no offset. :base (fn [place origin]) -> log odds or
  nil, optional: the fit holds it fixed as each row's offset and adds it
  back, so the theory learns the departures from it; a row with no base
  neither trains it nor takes an offset (t112)."
  [{:keys [places origins inputs target prior base]}]
  (let [xs (into {} (for [u places t origins]
                      [[u t] (double-array (cons 1.0 (map #(double (or % 0.0)) (inputs u t))))]))
        k (alength ^doubles (val (first xs)))
        bo (if base (fn [u t] (some-> (base u t) double)) (constantly 0.0))
        rows (vec (for [u places t origins :let [tg (target u t) o (bo u t)] :when (and tg o)]
                    {:x (xs [u t]) :y (boolean (:y tg)) :w 1.0 :o o :known-at (:known-at tg)}))]
    (into {}
          (for [t origins
                :let [train (filter #(<= (:known-at %) t) rows)]
                :when (and (some :y train) (some (complement :y) train))
                :let [beta (lgt/fit train k {:prior prior})]
                u places
                :let [o (bo u t)]
                :when o]
            [[u t] (m/logit (lgt/predict beta (xs [u t]) o))]))))

(defn any-of
  "The log odds that any of several places has an onset, from each one's
  log odds (independent), the chance kept within 1e-6 of 0 and 1 as the
  banking node's known offsets are; nil for none."
  [logits]
  (when (seq logits)
    (let [p (- 1.0 (reduce * (map #(- 1.0 (m/expit %)) logits)))]
      (m/logit (Math/max 1e-6 (Math/min (- 1.0 1e-6) p))))))

(defn place-z
  "(fn [place origin]) -> `raw`'s value standardized by the mean and sd of
  every place's values at the earlier `origins` (at least `min-n`),
  capped at 5; nil where raw is nil or the past too thin."
  [raw places origins min-n]
  (let [vals-at (memoize (fn [t] (vec (keep #(raw % t) places))))
        os (vec (sort origins))]
    (memoize
     (fn [u t]
       (when-let [x (raw u t)]
         (let [past (mapcat vals-at (take-while #(< % t) os))
               n (count past)]
           (when (>= n min-n)
             (let [m (/ (reduce + past) (double n))
                   sd (Math/sqrt (/ (reduce + (map #(Math/pow (- % m) 2) past)) (double n)))]
               (when (pos? sd) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd))))))))))))
