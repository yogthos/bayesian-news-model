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
