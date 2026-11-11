(ns bnm.sync
  "Synchrony and departure in event streams, as onset inputs:
  - a dynamical network biomarker (Chen, Liu, Liu, Li & Aihara, Sci Rep
    2012): a group of units nearing a transition swings more widely, moves
    together, and comes apart from the rest;
  - the share of a cross-section's movement in one factor (the first
    eigenvalue of the correlations), synchrony across the world;
  - a unit's departure from its own baseline: how far its channels'
    correlations and spreads now sit from what they were (single-sample
    DNB, Liu et al. 2017), which needs no history of onsets.
  Series are vectors of numbers, oldest first.")

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))

(defn- sd [xs]
  (let [m (mean xs)] (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (count xs)))))

(defn corr
  "Pearson correlation, 0 when either series is flat."
  [xs ys]
  (let [mx (mean xs) my (mean ys)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        syy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
    (if (and (pos? sxx) (pos? syy)) (/ sxy (Math/sqrt (* sxx syy))) 0.0)))

(defn first-share
  "The share of the standardized series' total variance in their first
  principal component: near 1 when all move together. Flat series are left
  out; nil with fewer than two."
  [series]
  (let [zs (vec (for [s series :let [m (mean s) d (sd s)] :when (pos? d)] (mapv #(/ (- % m) d) s)))]
    (when (>= (count zs) 2)
      (let [w (count (first zs))
            ;; the w x w Gram matrix has the cross-section's nonzero eigenvalues
            gram (vec (for [a (range w)] (vec (for [b (range w)] (reduce + (map #(* (% a) (% b)) zs))))))
            trace (reduce + (map #(get-in gram [% %]) (range w)))
            mul (fn [v] (mapv (fn [row] (reduce + (map * row v))) gram))
            norm (fn [v] (Math/sqrt (reduce + (map * v v))))
            ;; not a flat start: standardized series sum to zero over
            ;; time, so a constant vector is in the Gram matrix's null space
            lam (loop [v (mapv #(Math/sin (+ 1.0 %)) (range w)) k 0 lam 0.0]
                  (let [u (mul v) l (norm u)]
                    (if (or (zero? l) (>= k 200) (< (Math/abs (- l lam)) (* 1e-10 l)))
                      l
                      (recur (mapv #(/ % l) u) (inc k) l))))]
        (when (pos? trace) (/ lam trace))))))

(defn biomarker
  "The composite index of a group of `members` against `outsiders` (each
  a series): mean spread of the members, times their mean absolute
  correlation with each other, over their mean absolute correlation with
  the outsiders (plus 0.05). nil with fewer than two members."
  [members outsiders]
  (when (>= (count members) 2)
    (let [inside (for [[i a] (map-indexed vector members) b (drop (inc i) members)] (Math/abs (corr a b)))
          across (for [a members b outsiders] (Math/abs (corr a b)))]
      (/ (* (mean (map sd members)) (mean inside))
         (+ 0.05 (if (seq across) (mean across) 0.0))))))

(defn departure
  "[correlation shift, spread shift] of a unit's channels over its last `w`
  quarters against all the quarters before (`quarters` holds one vector of
  channel values each, oldest first): the mean absolute change in each
  pair's correlation, and the mean log ratio of each channel's spread. nil
  without twice `w` quarters of baseline."
  [quarters w]
  (when (>= (count quarters) (* 3 w))
    (let [n (count quarters)
          base (subvec (vec quarters) 0 (- n w))
          now (subvec (vec quarters) (- n w))
          k (count (first quarters))
          col (fn [rows j] (mapv #(nth % j) rows))
          pairs (for [a (range k) b (range (inc a) k)] [a b])
          shift (if (seq pairs)
                  (mean (for [[a b] pairs]
                          (Math/abs (- (corr (col now a) (col now b)) (corr (col base a) (col base b))))))
                  0.0)
          spread (mean (for [j (range k)]
                         (Math/log (/ (+ 1e-3 (sd (col now j))) (+ 1e-3 (sd (col base j)))))))]
      [shift spread])))
