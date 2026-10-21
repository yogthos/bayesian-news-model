(ns bnm.precursors
  "Precursors of a first onset that do not depend on which channel carries
  them (docs/INVARIANTS.md, trial 34). Each onset in a calm country has its
  own concrete expression, so these ask how a country's event streams
  stand against its own past, not which stream moved:

  - displacement: each stream's log count as a z-score against the
    country's previous 8, 20 and 40 quarters, summarised across the streams
    as the root mean square (norm, at 20), the share past 1.5 (breadth) and
    the largest (max); resonance is the smallest of the three norms,
    displaced at every scale at once (the Macrohistory tests; the
    Keilis-Borok school's rising activity);
  - trigger: the sharpest one-quarter rise in any stream, against that
    stream's own past rises (the flow that turns);
  - dispersion: the burstiness of the country's total count over the last
    eight quarters, log variance over mean, against its own past
    (Keilis-Borok's clustering; the index of dispersion near criticality);
  - range-shift: how far the country's total moves with its neighbours'
    over the last twelve quarters, against how far it used to (the range of
    correlation of Keilis-Borok and Soloviev; spatial early warnings).

  Settings are the literatures' defaults, fixed before any scored run.
  Only quarters up to the row's are read.")

(def names [:norm :breadth :max :resonance :trigger :dispersion :range-shift])

(def ^:private windows [8 20 40])
(def ^:private threshold 1.5)

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))

(defn- sd [xs]
  (let [mu (mean xs)]
    (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) xs)) (dec (count xs))))))

(defn- z
  "x against `past` (at least n values): nil when too few or flat."
  [x past n]
  (when (and x (>= (count past) (max n 2)))
    (let [s (sd past)]
      (when (> s 1e-9) (/ (- x (mean past)) s)))))

(defn- logs [counts t] (some->> (get counts t) (mapv #(Math/log1p (double %)))))

(defn- displacement
  "{:norm :breadth :max} of the streams at q against the `w` quarters
  before, or nil with fewer than w/2 of them."
  [counts q w]
  (when-let [now (logs counts q)]
    (let [past (keep #(logs counts %) (range (- q w) q))
          zs (keep identity (for [k (range (count now))] (z (now k) (map #(% k) past) (quot w 2))))]
      (when (seq zs)
        {:norm (Math/sqrt (mean (map #(* % %) zs)))
         :breadth (/ (count (filter #(> (Math/abs (double %)) threshold) zs)) (double (count zs)))
         :max (reduce max (map #(Math/abs (double %)) zs))}))))

(defn- trigger
  "The largest one-quarter rise at q in any stream, in units of that
  stream's rises over the 20 quarters before."
  [counts q]
  (let [rise (fn [t k] (when-let [a (logs counts t)] (when-let [b (logs counts (dec t))] (- (a k) (b k)))))
        now (logs counts q)]
    (when now
      (let [zs (keep (fn [k] (z (rise q k) (keep #(rise % k) (range (- q 20) q)) 8)) (range (count now)))]
        (when (seq zs) (reduce max zs))))))

(defn- total [counts t] (some->> (get counts t) (reduce +) double))

(defn- burstiness
  "log((variance + 1/2) / (mean + 1/2)) of the total over the eight
  quarters to t, or nil with fewer than six."
  [counts t]
  (let [xs (keep #(total counts %) (range (- t 7) (inc t)))]
    (when (>= (count xs) 6)
      (let [mu (mean xs)
            v (/ (reduce + (map #(let [d (- % mu)] (* d d)) xs)) (dec (count xs)))]
        (Math/log (/ (+ v 0.5) (+ mu 0.5)))))))

(defn- dispersion [bf q]
  (z (bf q) (keep bf (range (- q 40) (- q 7))) 10))

(defn- corr [xs ys]
  (let [n (count xs)]
    (when (>= n 8)
      (let [mx (mean xs) my (mean ys)
            sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
            sx (Math/sqrt (reduce + (map #(let [d (- % mx)] (* d d)) xs)))
            sy (Math/sqrt (reduce + (map #(let [d (- % my)] (* d d)) ys)))]
        (when (and (> sx 1e-9) (> sy 1e-9)) (/ sxy (* sx sy)))))))

(defn- range-at
  "The mean correlation of the unit's log total with each peer's over the
  twelve quarters to t, or nil when no peer has eight quarters beside it."
  [counts peers t]
  (let [cs (keep (fn [pc]
                   (let [pairs (keep (fn [s] (when-let [a (total counts s)] (when-let [b (total pc s)] [(Math/log1p a) (Math/log1p b)])))
                                     (range (- t 11) (inc t)))]
                     (corr (map first pairs) (map second pairs))))
                 peers)]
    (when (seq cs) (mean cs))))

(defn- range-shift [rf peers q]
  (if (empty? peers)
    0.0
    (or (when-let [now (rf q)]
          (let [past (keep rf (range (- q 40) (- q 11)))]
            (when (>= (count past) 8)
              (/ (- now (mean past)) (Math/max 0.05 (sd past))))))
        0.0)))

(defn features-for
  "{quarter features} of a unit at each of `qs` from {:counts {quarter
  [count per stream]} :peers [peer counts ...]}, quarters without the
  20-quarter displacement left out. The burstiness and neighbour
  correlation of each quarter are computed once for all of them."
  [{:keys [counts peers]} qs]
  (let [bf (memoize #(burstiness counts %))
        rf (memoize #(range-at counts peers %))]
    (into {} (for [q qs
                   :let [d20 (displacement counts q 20)]
                   :when d20]
               [q (assoc d20
                         :resonance (reduce min (keep #(:norm (displacement counts q %)) windows))
                         :trigger (or (trigger counts q) 0.0)
                         :dispersion (or (dispersion bf q) 0.0)
                         :range-shift (range-shift rf peers q))]))))

(defn features
  "The precursors of a unit at quarter q (`features-for` at one quarter),
  or nil without the 20-quarter displacement."
  [unit q]
  (get (features-for unit [q]) q))

(defn ->vector [f] (mapv #(double (get f % 0.0)) names))

(def streams
  "The event-code streams of data/slow/event-mix.tsv (scripts/event-mix.sh)."
  ["accuse-did" "aid-did" "assault-did" "coerce-did" "concede-did" "consult-did" "cooperate-did"
   "cooperate-intend" "mobilize-did" "protest-did" "reject-did" "request-did" "sanction-did"
   "support-did" "threaten-threaten"])

(defn load-counts
  "{unit {quarter [count per stream]}} from an event-mix file (unit,
  quarter, stream, count), every quarter from the unit's first to its last;
  a quarter with no events counts zero in every stream."
  [path]
  (let [ix (zipmap streams (range))
        by-unit (with-open [r (clojure.java.io/reader path)]
                  (reduce (fn [acc line]
                            (let [[unit q t n] (clojure.string/split line #"\t")]
                              (if-let [i (ix t)]
                                (update-in acc [unit (parse-long q)] (fnil assoc (vec (repeat (count streams) 0.0))) i (parse-double n))
                                acc)))
                          {} (line-seq r)))]
    (into {} (for [[unit qs] by-unit
                   :let [q0 (apply min (keys qs)) q1 (apply max (keys qs))]]
               [unit (into {} (for [q (range q0 (inc q1))] [q (get qs q (vec (repeat (count streams) 0.0)))]))]))))
