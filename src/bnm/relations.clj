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
  onsets so far, r per month over the months f..k it covers, would give
  over the same kernel and months: r exp(-(t - k)/tau) (1 - exp(-n/tau)) /
  (1 - exp(-1/tau)), n = k - f + 1. Without the lag's discount the level
  is overstated, and a place prone to the parent reads as calm. 0 where
  the catalogue does not cover the place."
  ^double [{:keys [onsets from known-lag]} place t tau]
  (if-let [f (get from place)]
    (let [k (- t (or known-lag 12))
          known (subseq (get onsets place (sorted-set)) >= f <= k)
          raw (reduce + 0.0 (for [m known] (Math/exp (- (/ (- t m) tau)))))
          n (inc (- k f))
          rate (/ (count known) (double (max 1 n)))
          level (* (Math/exp (- (/ (- t k) tau))) (/ (- 1.0 (Math/exp (- (/ n tau)))) (- 1.0 (Math/exp (/ -1.0 tau)))))]
      (if (< k f) 0.0 (- raw (* rate level))))
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

(declare raw-input)

(defn edge-input
  "{:x value :reads [...]} of what a parent ({:kind :book :catalogue})
  passes along one edge, in the edge's `form`, for `place` at `scale` and
  origin t (months), 0 where its catalogue does not yet cover the place:
  :post1, :post3, an onset of the parent known in the last one or three
  years (a post-crisis regime, Bussiere & Fratzscher 2006); :e1, :e5, its
  onsets as excursions over a year or five; :destab, its state at its
  latest origin before t less its mean over the five origins before that
  (at least three), the parent destabilized against its own past. Never
  its level, which carries the place's lasting risk the child already sees
  (docs/WORLD-MODEL.md, trial 43)."
  [form parent scale place t]
  (when (= :level form)
    (throw (ex-info "bnm.relations/edge-input: the level is no link's form" {:form form})))
  (raw-input form parent scale place t))

(defn raw-input
  "edge-input's forms and the parent's :level (its latest state before t),
  which passes only as its own part (bnm.factors), never raw."
  [form {:keys [kind book catalogue]} scale place t]
  (let [{:keys [onsets from known-lag]} catalogue
        lag (or known-lag 12)
        f (get from place)
        covered? (and f (<= f t))
        recent (fn [months] (if (seq (subseq (get onsets place (sorted-set)) > (- t lag months) <= (- t lag))) 1.0 0.0))]
    (case form
      :post1 {:x (if covered? (recent 12) 0.0) :reads []}
      :post3 {:x (if covered? (recent 36) 0.0) :reads []}
      :e1 {:x (if covered? (excursion catalogue place t 12.0) 0.0) :reads []}
      :e5 {:x (if covered? (excursion catalogue place t 60.0) 0.0) :reads []}
      :level (let [[o e] (some-> (get book [scale place]) (rsubseq < t) first)]
               (if (and covered? e)
                 {:x (double (:f e)) :reads [{:dir :relation :kind kind :scale scale :place place :origin o}]}
                 {:x 0.0 :reads []}))
      :destab (let [past (some-> (get book [scale place]) (rsubseq < t))
                    [o e] (first past)
                    prior (map (comp :f val) (take 5 (rest past)))]
                (if (and covered? e (>= (count prior) 3))
                  {:x (- (double (:f e)) (/ (reduce + prior) (count prior)))
                   :reads [{:dir :relation :kind kind :scale scale :place place :origin o}]}
                  {:x 0.0 :reads []}))
      (throw (ex-info (str "bnm.relations/edge-input: no form " form) {:form form})))))

(defn- latest-f [book scale place t]
  (some-> (get book [scale place]) (rsubseq < t) first val :f))

(defn stress-cut
  "The top fifth's threshold of a kind's latest states issued before t at
  `scale`, across the places `peer?` keeps (every place by default; a
  country's peers are the countries, not the regions above them), or nil
  with none."
  ([book scale t] (stress-cut book scale t (constantly true)))
  ([book scale t peer?]
  (let [fs (sort (keep (fn [[[s p] _]] (when (and (= s scale) (peer? p)) (latest-f book scale p t))) book))]
    (when (seq fs) (nth fs (int (Math/floor (* 0.8 (dec (count fs))))))))))

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
