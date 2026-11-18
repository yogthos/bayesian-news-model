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
  of either kind, has no offset."
  [{:keys [places origins inputs target prior]}]
  (let [xs (into {} (for [u places t origins]
                      [[u t] (double-array (cons 1.0 (map #(double (or % 0.0)) (inputs u t))))]))
        k (alength ^doubles (val (first xs)))
        rows (vec (for [u places t origins :let [tg (target u t)] :when tg]
                    {:x (xs [u t]) :y (boolean (:y tg)) :w 1.0 :known-at (:known-at tg)}))]
    (into {}
          (for [t origins
                :let [train (filter #(<= (:known-at %) t) rows)]
                :when (and (some :y train) (some (complement :y) train))
                :let [beta (lgt/fit train k {:prior prior})]
                u places]
            [[u t] (m/logit (lgt/predict beta (xs [u t])))]))))

(defn any-of
  "The log odds that any of several places has an onset, from each one's
  log odds (independent), the chance kept within 1e-6 of 0 and 1 as the
  banking node's known offsets are; nil for none."
  [logits]
  (when (seq logits)
    (let [p (- 1.0 (reduce * (map #(- 1.0 (m/expit %)) logits)))]
      (m/logit (Math/max 1e-6 (Math/min (- 1.0 1e-6) p))))))
