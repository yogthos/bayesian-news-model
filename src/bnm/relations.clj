(ns bnm.relations
  "What one kind of event reads of the others at a place (docs/WORLD-MODEL.md):
  each parent's state, its linear predictor at its latest origin before t
  (its whole stress, whether or not it became an onset); its onsets known by
  t as an excursion above the place's own rate so far; its state through a
  moderator (a place's institutions buffering what reaches it); and how many
  kinds are stressed together at the place (the coupling of crises). Every
  read is from before t and recorded."
  (:require [clojure.core :as c]))

(defn excursion
  "The onsets of `cat` at `place` known by month t (onset + :known-lag <= t),
  each decaying exp(-(t - m)/tau), less what the place's rate of known
  onsets so far would give over the same kernel: r / (1 - exp(-1/tau)) per
  month. 0 where the catalogue does not cover the place."
  ^double [{:keys [onsets from known-lag]} place t tau]
  (if-let [f (get from place)]
    (let [k (- t (or known-lag 12))
          known (subseq (get onsets place (sorted-set)) >= f <= k)
          raw (reduce + 0.0 (for [m known] (Math/exp (- (/ (- t m) tau)))))
          months (max 1 (- k f))
          rate (/ (count known) (double months))]
      (if (< k f) 0.0 (- raw (* rate (/ 1.0 (- 1.0 (Math/exp (/ -1.0 tau))))))))
    0.0))

(defn parent-inputs
  "{:x [state onsets-1y onsets-5y state*moderator] :reads [...]} of a
  parent ({:kind :book :catalogue}) for `place` at `scale` and origin t,
  in months; the onset kernels at the year (12 months) and five years (60)."
  [{:keys [kind book catalogue]} scale place t moderator]
  (let [[o e] (some-> (get book [scale place]) (rsubseq < t) first)
        f (if e (double (:f e)) 0.0)]
    {:x [f (excursion catalogue place t 12.0) (excursion catalogue place t 60.0) (* f (double (or moderator 0.0)))]
     :reads (if e [{:dir :relation :kind kind :scale scale :place place :origin o}] [])}))

(defn- latest-f [book scale place t]
  (some-> (get book [scale place]) (rsubseq < t) first val :f))

(defn stress-cut
  "The top fifth's threshold of a kind's latest states issued before t at
  `scale`, across places, or nil with none."
  [book scale t]
  (let [fs (sort (keep (fn [[[s p] _]] (when (= s scale) (latest-f book scale p t))) book))]
    (when (seq fs) (nth fs (int (Math/floor (* 0.8 (dec (count fs)))))))))

(defn coupling
  "How many of `kinds` (their books in `books`) have the place in their top
  fifth across places of their latest states issued before t at `scale`;
  `cut` (fn [kind]) gives each kind's threshold (stress-cut, computed once
  per origin), by default computed here."
  ([books kinds scale place t] (coupling books kinds scale place t #(stress-cut (books %) scale t)))
  ([books kinds scale place t cut]
   (double (count (filter (fn [k] (let [mine (latest-f (books k) scale place t) c (cut k)]
                                    (and mine c (> mine c))))
                          kinds)))))

(defn principal
  "The principal contradiction: of the `candidates` among `groups` ({group
  [input indices]}), the one whose inputs contribute most to the log odds
  under coefficient means `means` (by size), as [group contribution]; nil
  without candidates. Which contradiction leads at a place, and when that
  changes, is the model's account of it (Mao, On Contradiction)."
  [means x groups candidates]
  (let [contrib (for [g candidates :let [ix (get groups g)] :when (seq ix)]
                  [g (reduce + (for [i ix] (* (double (nth means i)) (double (nth x i)))))])]
    (when (seq contrib) (apply max-key #(Math/abs (double (second %))) contrib))))
