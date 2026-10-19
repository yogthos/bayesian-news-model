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
  1-q quantiles of its values."
  [xs keep q]
  (let [xs (vec xs) n (count xs) width (alength ^doubles (first xs))]
    (vec (for [j (range keep width)
               :let [v (vec (sort (map #(aget ^doubles % (int j)) xs)))]]
           [(v (max 0 (dec (int (Math/ceil (* q n))))))
            (v (min (dec n) (int (Math/ceil (* (- 1.0 q) n)))))]))))

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
      (aset out (+ keep (* 2 i)) (if (> v (double hi)) 1.0 0.0))
      (aset out (+ keep (* 2 i) 1) (if (< v (double lo)) 1.0 0.0)))
    out))

(defn unused-groups
  "The input groups that no model names: a model's inputs are its name
  without a gbt- or at- prefix, split on +, each part without -placebo."
  [models groups]
  (let [used (set (for [m models
                        part (clojure.string/split (clojure.string/replace (name m) #"^(gbt|at)-" "") #"\+")]
                    (clojure.string/replace part #"-placebo$" "")))]
    (vec (remove used groups))))

(defn event-sets
  "From events [kind unit quarter latest-quarter known-quarter]: {:onsets
  the earliest quarter each could be (labels), :spans every quarter each
  could be (whether a row is at risk), :known the quarter each became known
  (inputs)}, each {kind {unit sorted-set}}."
  [events]
  (reduce (fn [acc [kind unit q latest known]]
            (-> acc
                (update-in [:onsets kind unit] (fnil conj (sorted-set)) q)
                (update-in [:spans kind unit] (fnil into (sorted-set)) (range q (inc latest)))
                (update-in [:known kind unit] (fnil conj (sorted-set)) known)))
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
