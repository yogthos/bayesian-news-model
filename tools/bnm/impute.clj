(ns bnm.impute
  "Missing inputs (bnm-s6m; reports/Crisis mechanisms data and signals.md):
  the theory layers read a missing input as the cross-country mean, which
  says a country that does not report is average, when those that do not
  are mostly the small, poor and crisis-prone, and throws away that it was
  missing. Here a missing input is the country's last value read at an
  earlier origin, decayed toward its peers' mean at the origin (GRU-D's
  decay, Che et al. 2018: half of the way every 36 months); its peers'
  mean when it never had one (its region's countries, else every
  country's); and a last column gives the share of its inputs missing,
  read before any is filled, so the fit can learn what missing means.")

(def half-life "Months for a last value to decay half of the way to its peers' mean." 36.0)

(defn impute
  "`row` (fn [unit origin]) -> `width` values or nils, filled as above over
  `places` at `origins` (sorted); `parent` maps a place to its region.
  Returns (fn [unit origin]) -> width + 1 values, the last the share
  missing; nil where `row` gives nil, or at a month not in `origins`."
  [row width places origins parent]
  (let [raw (into {} (for [u places t origins :let [r (row u t)] :when r] [[u t] (vec r)]))
        mean (fn [xs] (when (seq xs) (/ (reduce + xs) (count xs))))
        peers (group-by parent places)
        peer-mean (memoize (fn [reg t i]
                             (or (mean (keep #(some-> (raw [% t]) (nth i)) (get peers reg)))
                                 (mean (keep #(some-> (raw [% t]) (nth i)) places)))))
        last-of (fn [u t i] (first (for [t' (reverse (take-while #(< % t) origins))
                                         :let [x (some-> (raw [u t']) (nth i))] :when x]
                                     [x (- t t')])))
        filled (memoize
                (fn [u t]
                  (when-let [r (raw [u t])]
                    (conj (vec (for [i (range width)]
                                 (or (nth r i)
                                     (let [m (peer-mean (parent u) t i)]
                                       (if-let [[x dt] (last-of u t i)]
                                         (if m
                                           (let [g (Math/pow 0.5 (/ (double dt) half-life))] (+ (* g x) (* (- 1 g) m)))
                                           x)
                                         m)))))
                          (/ (count (filter nil? r)) (double width))))))]
    filled))
