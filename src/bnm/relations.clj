(ns bnm.relations
  "What one kind of event reads of the others at a place (docs/WORLD-MODEL.md):
  along each edge of the graph, one input in the edge's form (raw-input,
  edge-input): a parent's onsets known by t as a post-crisis regime or as
  an excursion above the place's own rate so far, or its state against
  its own past; its level only as its own part (bnm.factors). Every read
  is from before t and recorded. Also the coupling of crises (how many
  kinds are stressed together at a place) and the principal
  contradiction."
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

(defn raw-input
  "{:x value :reads [...]} of what a parent ({:kind :book :catalogue})
  passes along one edge in `form`, for `place` at `scale` and origin t
  (months); :x nil where it is not known: before the parent's catalogue
  covers the place, before any state is issued, with too few to
  destabilize against. The forms: :post1, :post3, an onset of the parent
  known in the last one or three years (a post-crisis regime, Bussiere &
  Fratzscher 2006); :e1, :e5, its onsets as excursions over a year or
  five; :destab, its state at its latest origin before t less its mean
  over the five origins before that (at least three), the parent
  destabilized against its own past; :level, its latest state, which
  passes only as its own part (bnm.factors), never raw."
  [form {:keys [kind book catalogue]} scale place t]
  (let [{:keys [onsets from known-lag]} catalogue
        lag (or known-lag 12)
        f (get from place)
        covered? (and f (<= f t))
        recent (fn [months] (if (seq (subseq (get onsets place (sorted-set)) > (- t lag months) <= (- t lag))) 1.0 0.0))
        none {:x nil :reads []}]
    (if-not covered?
      (if (#{:post1 :post3 :e1 :e5 :destab :level} form) none
          (throw (ex-info (str "bnm.relations/raw-input: no form " form) {:form form})))
      (case form
        :post1 {:x (recent 12) :reads []}
        :post3 {:x (recent 36) :reads []}
        :e1 {:x (excursion catalogue place t 12.0) :reads []}
        :e5 {:x (excursion catalogue place t 60.0) :reads []}
        :level (if-let [[o e] (some-> (get book [scale place]) (rsubseq < t) first)]
                 {:x (double (:f e)) :reads [{:dir :relation :kind kind :scale scale :place place :origin o}]}
                 none)
        :destab (let [past (some-> (get book [scale place]) (rsubseq < t))
                      [o e] (first past)
                      prior (map (comp :f val) (take 5 (rest past)))]
                  (if (and e (>= (count prior) 3))
                    {:x (- (double (:f e)) (/ (reduce + prior) (count prior)))
                     :reads [{:dir :relation :kind kind :scale scale :place place :origin o}]}
                    none))
        (throw (ex-info (str "bnm.relations/raw-input: no form " form) {:form form}))))))

(defn edge-input
  "raw-input's {:x :reads} along an edge, an unknown value read as 0
  (docs/WORLD-MODEL.md, trial 43); never the parent's level, which carries
  the place's lasting risk the child already sees."
  [form parent scale place t]
  (when (= :level form)
    (throw (ex-info "bnm.relations/edge-input: the level is no link's form" {:form form})))
  (update (raw-input form parent scale place t) :x #(or % 0.0)))

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
