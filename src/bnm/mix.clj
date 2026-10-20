(ns bnm.mix
  "The event-code mix of a place as a slow stock (docs/SHOCKS.md): what kind
  of events are reported there, not how many. The one measured edge on
  onsets where a country's history is silent is news composition (Mueller &
  Rauh, JEEA 2022: topic shares and their decayed stocks); the event types
  are the stand-in that reaches back to 1979. Shares are free of GDELT's
  growing volume.

  Per quarter, each type's share of the place's events, shrunk toward the
  stock by `pseudo` events so a handful says little; the stock, decayed by
  `decay` a quarter; and the shift, the share less the stock before it.")

(def types
  "The type-mode pairs that carry almost all of GDELT's volume, by volume."
  ["consult-did" "request-did" "assault-did" "cooperate-intend" "support-did"
   "accuse-did" "coerce-did" "concede-did" "reject-did" "aid-did"
   "cooperate-did" "threaten-threaten" "sanction-did" "protest-did" "mobilize-did"])

(def labels
  (vec (concat (map #(keyword (str "mix-" %)) types)
               (map #(keyword (str "shift-" %)) types))))

(def decay 0.8)

(def pseudo 20.0)

(defn features
  "{quarter double[2k]} from {quarter {type count}} (sorted by quarter):
  the stocks of the k `types`' shares, then their shifts, for every quarter
  from the first to the last. A quarter without events keeps the stock and
  shifts nothing."
  [counts]
  (let [k (count types)]
    (when (seq counts)
      (loop [q (key (first counts)) last-q (key (last counts)) stock nil acc (transient {})]
        (if (> q last-q)
          (persistent! acc)
          (let [c (get counts q)
                n (reduce + (map #(get c % 0.0) types))
                raw (mapv #(if (pos? n) (/ (get c % 0.0) n) 0.0) types)
                prior (or stock raw)
                share (if (pos? n)
                        (mapv (fn [r p] (/ (+ (* r n) (* pseudo p)) (+ n pseudo))) raw prior)
                        prior)
                ;; the first quarter starts the stock at its own mix
                stock' (if (and stock (pos? n)) (mapv #(+ (* decay %1) (* (- 1.0 decay) %2)) stock share) prior)
                out (double-array (* 2 k))]
            (dotimes [i k]
              (aset out i (double (stock' i)))
              (aset out (+ k i) (double (- (share i) (prior i)))))
            (recur (inc q) last-q stock' (assoc! acc q out))))))))
