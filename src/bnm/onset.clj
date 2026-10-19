(ns bnm.onset
  "Onset rows: their labels, which rows are at risk, the hard cases, how far
  a catalogue is complete, and the presence column of an input group that
  covers only some rows. Onsets are sorted sets of quarters; a window [a b]
  asks for an onset in quarters q+a..q+b of the origin q.")

(defn label [onsets q [a b]]
  (boolean (seq (subseq onsets >= (+ q a) <= (+ q b)))))

(defn at-risk?
  "No onset in the year up to the origin (one under way is not a new one),
  nor between the origin and the window: a row whose window opens after an
  onset it saw coming is neither a hit nor a miss."
  [onsets q [a _]]
  (empty? (subseq onsets >= (- q 3) <= (+ q (dec a)))))

(defn hard-row?
  "No onset of the kind in the ten years up to q: the hard cases, where the
  country's history says nothing (Mueller & Rauh)."
  [onsets q]
  (empty? (subseq onsets >= (- q 40) <= q)))

(defn history-features
  "How many onsets of a kind a unit has had up to quarter q, log1p, and how
  recent the last was (exp of minus years since over five)."
  [onsets q]
  (let [past (subseq onsets <= q)
        n (count past)]
    [(Math/log1p n) (if (pos? n) (Math/exp (- (/ (- q (long (last past))) 20.0))) 0.0)]))

(defn histories
  "The unit's history of each of `kinds` (`history-features`) at q, from
  `events` ({kind {unit onsets}}): what one kind of upheaval says of
  another (Bazzi et al., REStat 2022: histories by type beat one total)."
  ^doubles [events kinds unit q]
  (double-array (mapcat #(history-features (get-in events [% unit] (sorted-set)) q) kinds)))

(def neighbourhood-window "Quarters a neighbour's onset counts for." 12)

(defn neighbourhood
  "Per kind and per level of `peers` ({level {unit [other units]}}, e.g.
  subregion and region, levels in key order), the peers' onsets known by q, each weighted
  exp(-quarters ago / 4) within `neighbourhood-window`, log1p. Upheaval
  next door, whatever its kind: how much it spreads is left to the fit."
  ^doubles [events kinds peers unit q]
  (double-array
   (for [k kinds [_ by-unit] (sort-by key peers)]
     (Math/log1p
      (reduce + (for [p (get by-unit unit) :when (not= p unit)
                      t (subseq (get-in events [k p] (sorted-set)) > (- q neighbourhood-window) <= q)]
                  (Math/exp (- (/ (- q t) 4.0)))))))))

(defn tail-cuts
  "[lo hi] for each column of `xs` (double arrays) from `keep` on: the q and
  1-q quantiles of its values; [:flag low] for a column of two values or
  fewer, which `tail-flags` passes through as above its lower value."
  [xs keep q]
  (let [xs (vec xs) n (count xs) width (alength ^doubles (first xs))]
    (vec (for [j (range keep width)
               :let [v (double-array (map #(aget ^doubles % (int j)) xs))]]
           (do (java.util.Arrays/sort v)
               (let [lo (aget v 0) hi (aget v (dec n))]
                 ;; two values or fewer: the input is a flag already
                 (if (every? #(or (== % lo) (== % hi)) v)
                   [:flag lo]
                   [(aget v (max 0 (dec (int (Math/ceil (* q n))))))
                    (aget v (min (dec n) (int (Math/ceil (* (- 1.0 q) n)))))])))))))

(defn tail-flags
  "x with its columns from `keep` on replaced by two flags each, above the
  column's hi cut and below its lo cut (`tail-cuts`): the at-risk
  transformation of Keilis-Borok's premonitory patterns, which outforecast
  the raw values out of sample (Billakanti & Shin, Philadelphia Fed 2026).
  Thresholds from the pooled rows let a country with a quiet past trip them."
  ^doubles [^doubles x keep cuts]
  (let [out (double-array (+ keep (* 2 (count cuts))))]
    (System/arraycopy x 0 out 0 keep)
    (doseq [[i [lo hi]] (map-indexed vector cuts)
            :let [v (aget x (int (+ keep i)))]]
      (if (= :flag lo)
        (aset out (+ keep (* 2 i)) (if (> v (double hi)) 1.0 0.0))
        (do (aset out (+ keep (* 2 i)) (if (> v (double hi)) 1.0 0.0))
            (aset out (+ keep (* 2 i) 1) (if (< v (double lo)) 1.0 0.0)))))
    out))

(defn unused-groups
  "The input groups that no model names: a model's inputs are its name
  without a gbt-, at-, knn-, hs- or mt- prefix, split on +, each part without
  -placebo."
  [models groups]
  (let [used (set (for [m models
                        part (clojure.string/split (clojure.string/replace (name m) #"^(gbt|at|knn|hs|mt)-" "") #"\+")]
                    (clojure.string/replace part #"-placebo$" "")))]
    (vec (remove used groups))))

(defn aggregate
  "Forecasts {[unit q] [p y]} for groups of units ({unit group}; a unit
  without one is its own): the probability of an onset in any of them, if
  independent, and whether one came."
  [rows group-of]
  (reduce-kv (fn [acc [unit q] [p y]]
               (update acc [(get group-of unit unit) q]
                       (fn [[p0 y0]] [(if p0 (- 1.0 (* (- 1.0 p0) (- 1.0 (double p)))) (double p)) (boolean (or y0 y))])))
             {} rows))

(defn wave-index
  "Onsets of every kind in `kinds` per group at each level of `groups`
  ({level {unit group}}) and in the world: {[level group] [[q unit] ...]}
  sorted by quarter, from `events` ({kind {unit quarters}}), for `waves`."
  [events kinds groups]
  (let [all (for [k kinds [unit qs] (get events k) q qs] [q unit])]
    (into {[:world :world] (vec (sort all))}
          (for [[level of] groups
                [g xs] (group-by (fn [[_ unit]] (of unit)) all)
                :when g]
            [[level g] (vec (sort xs))]))))

(defn- upto
  "How many of `xs` ([q ...] sorted by q) have q at or before `q`."
  ^long [xs ^long q]
  (loop [lo 0 hi (count xs)]
    (if (< lo hi)
      (let [mid (quot (+ lo hi) 2)]
        (if (<= (long (first (xs mid))) q) (recur (inc mid) hi) (recur lo mid)))
      lo)))

(defn waves
  "For each level of `groups` in key order and then the world, and each
  decay rate in `taus` (quarters): the onsets of every kind among the
  other units known by q, each weighted exp(-quarters ago / tau) within
  three taus, log1p. A sum of exponentials can rise and then turn negative
  in the fit: contagion, then a refractory period (a Hawkes kernel the
  logit learns)."
  ^doubles [index groups unit q taus]
  (double-array
   (for [key (concat (for [[level of] (sort-by key groups)] [level (of unit)]) [[:world :world]])
         tau taus]
     (let [xs (get index key [])]
       (Math/log1p
        (reduce + (for [[t u] (rseq (subvec xs 0 (upto xs q)))
                        :while (< (- q t) (* 3 tau))
                        :when (not= u unit)]
                    (Math/exp (- (/ (- q t) tau))))))))))

(defn dispersion
  "How much onsets cluster within groups beyond independent forecasts:
  over every group-quarter of `rows` ({[unit q] [p y]}, grouped by
  `group-of`), the squared gap between the onsets that came and the sum of
  their probabilities, over the Poisson-binomial variance sum p(1-p).
  {:ratio :groups}: about 1 when the units' onsets are independent given
  the forecasts, above when they come in waves."
  [rows group-of]
  (let [cells (reduce-kv (fn [acc [unit q] [p y]]
                           (update acc [(get group-of unit unit) q]
                                   (fn [[o e v]] [(+ (or o 0.0) (if y 1.0 0.0)) (+ (or e 0.0) p) (+ (or v 0.0) (* p (- 1.0 p)))])))
                         {} rows)
        num (reduce + (for [[o e _] (vals cells)] (let [d (- o e)] (* d d))))
        den (reduce + (map #(nth % 2) (vals cells)))]
    {:ratio (when (pos? den) (/ num den)) :groups (count cells)}))

(defn frailty
  "Forecasts `preds` ([q p y unit]) with a shared regional frailty (Duffie,
  Eckner, Horel & Saita, JF 2009, for clustered defaults): each group's
  hazards scaled by Z = (prior + O) / (prior + E), O the onsets and E the
  hazards (-log(1-p)) of its rows resolved by q (outcome window `[_ b]`:
  q' + b <= q), each discounted by `discount` per year of age. A region
  running hot lifts its members, a calm one relaxes them; with no
  clustering Z stays near 1. Rows without a label never count."
  [preds group-of [_ b] {:keys [discount prior]}]
  (let [by-q (group-by first preds)
        ;; rows resolve at q + b: per quarter, the rows that resolve then
        resolving (group-by #(+ (first %) b) (filter #(some? (nth % 2)) preds))
        d (Math/pow discount 0.25)
        haz #(- (Math/log (- 1.0 (min (double %) (- 1.0 1e-9)))))]
    (if (empty? preds)
      []
      (loop [q (apply min (keys by-q)) acc {} out (transient [])]
        (if (> q (apply max (keys by-q)))
          (persistent! out)
          ;; every quarter: age the record, add what resolves by then (all
          ;; before the first quarter at the start), then adjust its rows
          (let [acc (update-vals acc (fn [[o e]] [(* d o) (* d e)]))
                new (if (empty? out)
                      (mapcat val (filter #(<= (key %) q) resolving))
                      (get resolving q []))
                acc (reduce (fn [acc [_ p y unit]]
                              (update acc (get group-of unit unit)
                                      (fn [[o e]] [(+ (or o 0.0) (if y 1.0 0.0)) (+ (or e 0.0) (haz p))])))
                            acc new)
                out (reduce (fn [out [q0 p y unit]]
                              (let [[o e] (get acc (get group-of unit unit))
                                    z (if e (/ (+ prior o) (+ prior e)) 1.0)]
                                (conj! out [q0 (if (== z 1.0) p (- 1.0 (Math/exp (- (* z (haz p)))))) y unit])))
                            out (get by-q q []))]
            (recur (inc q) acc out)))))))

(defn resolves-at
  "The quarter a row's label is final: its window [_ b] closed, and the
  kind's reporting `lag` passed so every onset that could fall in it is
  published. Before then a fit that used the label would see onsets nobody
  knew of (the right truncation that nowcasting corrects, McGough et al.
  2020)."
  [q [_ b] lag]
  (+ q b lag))

(defn in-era?
  "Whether a row at q belongs to an era [from to] (years) given the year
  `lock` from which outcomes are held for confirmation: a decision era
  (confirm? false) takes rows from its years whose labels resolve before
  the lock (`resolves-at`), the confirmation era every row up to its last
  year whose label resolves at or after it. A row forecast in late 2022
  whose window reaches 2023 is confirmation, not decision."
  [q window lag [from to] lock confirm?]
  (let [year (quot q 4) resolved (resolves-at q window lag)]
    (if confirm?
      (and (<= year to) (>= resolved (* 4 lock)))
      (and (<= from year to) (< resolved (* 4 lock))))))

(defn event-sets
  "From events [kind unit quarter latest-quarter known-quarter]: {:onsets
  the earliest quarter each could be (labels), :spans every quarter each
  could be (whether a row is at risk), :known the quarter each became known
  (inputs)}, each {kind {unit sorted-set}}, and :lags {kind quarters}, the
  longest any onset of the kind took to be known (`resolves-at`)."
  [events]
  (reduce (fn [acc [kind unit q latest known]]
            (-> acc
                (update-in [:onsets kind unit] (fnil conj (sorted-set)) q)
                (update-in [:spans kind unit] (fnil into (sorted-set)) (range q (inc latest)))
                (update-in [:known kind unit] (fnil conj (sorted-set)) known)
                (update-in [:lags kind] (fnil max 0) (- known q))))
          {} events))

(defn last-complete-quarter
  "The last quarter wholly covered by a catalogue that runs through `month`
  (12y + m - 1)."
  [month]
  (dec (quot (inc month) 3)))

(defn with-presence
  "[width xs] for an input group of `width` that has values for only some of
  `rows` ({:unit :q}): a presence column appended, so the zeros filling the
  others say \"no data\" rather than a value. A group covering every row is
  returned as it is."
  [width xs rows]
  (if (every? #(contains? xs [(:unit %) (:q %)]) rows)
    [width xs]
    [(inc width) (update-vals xs (fn [^doubles x]
                                   (let [out (double-array (inc width))]
                                     (System/arraycopy x 0 out 0 width)
                                     (aset out width 1.0)
                                     out)))]))

(defn choose-by-year
  "One model's predictions from `runs` ({:prior :preds [[q p y unit]]}, one
  per prior), each year's taken from the run with the best `score` on the
  predictions whose outcomes (window [_ b]) were known when the year began,
  `default`'s while none were. {:prior the last year's choice, :preds}."
  [runs score [_ b] default]
  (let [by-year (fn [run] (group-by #(quot (first %) 4) (:preds run)))
        years (sort (distinct (map #(quot (first %) 4) (mapcat :preds runs))))
        indexed (mapv (fn [run] (assoc run :years (by-year run))) runs)]
    (loop [[yr & more] years prior (:prior default) preds []]
      (if-not yr
        {:prior prior :preds preds}
        (let [known (fn [run] (filter (fn [[q _ y]] (and (some? y) (<= (+ q b) (* 4 yr)))) (:preds run)))
              scored (keep (fn [run] (when-let [s (score (known run))] [s run])) indexed)
              pick (if (seq scored) (second (apply max-key first (reverse scored)))
                       (first (filter #(= (:prior %) (:prior default)) indexed)))]
          (recur more (:prior pick) (into preds (get (:years pick) yr))))))))
