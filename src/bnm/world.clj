(ns bnm.world
  "The world model as an artificial system (docs/WORLD-MODEL.md; Wang
  Fei-Yue's parallel systems): every kind's node, as learned, run forward
  together so that what one kind does reaches the others through the
  relations the models learned. Computational experiments on it: set a
  kind's onset at a place and follow what follows.

  A world is {kind {:f {place linear predictor} :q {place variance}
  :window months :parents {parent [c-state c-onsets-1y c-onsets-5y
  c-state*moderator]} :moderator {place m}}}: each kind's last forecast for
  each place (its year node's f and q) and, per parent, the effect on its
  log odds of a unit of each relation input in raw units (the models'
  coefficients, weighted by the model averaging, over the inputs' spreads).
  Forward, year by year: each kind's linear predictor is its last one plus
  its relations' change (its parents' simulated states less their last
  ones, and their simulated onsets as the kernels would count them); a
  state is drawn from N(f, q), an onset in the year from its window's
  chance scaled to a year under a constant hazard. Everything else a node
  reads is held where it last was: the experiment isolates the relations."
  (:require [augur.math :as m]))

(defn- year-p
  "The chance of an onset in a year from the log odds for a window of
  `window` months, under a constant hazard."
  ^double [^double eta ^double window]
  (- (Math/expm1 (* (/ 12.0 window) (Math/log1p (- (m/expit eta)))))))

(defn- one-draw
  "One path: {[kind place] first-year-of-onset or nil}, in years from 1."
  [world places horizon g forced]
  (loop [h 1 f-now (into {} (for [[k {:keys [f]}] world p places] [[k p] (get f p)]))
         onsets {}]
    (if (> h horizon)
      onsets
      (let [;; each kind's predictor: its last one plus what its parents changed
            eta (into {} (for [[k {:keys [f parents moderator]}] world p places
                               :let [base (get f p)]
                               :when base]
                           [[k p] (+ base
                                     (reduce + 0.0
                                             (for [[j [cs c1 c5 cm]] parents
                                                   :let [dj (- (or (f-now [j p]) 0.0) (or (get-in world [j :f p]) 0.0))
                                                         since (some-> (onsets [j p]) (#(- h %)))]]
                                               (+ (* cs dj) (* cm dj (double (get moderator p 0.0)))
                                                  ;; an onset s years ago, as the kernels count it
                                                  (if since (+ (* c1 (Math/exp (- (/ (* 12.0 since) 12.0))))
                                                               (* c5 (Math/exp (- (/ (* 12.0 since) 60.0)))))
                                                      0.0)))))]))
            drawn (into {} (for [[kp e] eta :let [[k p] kp q (get-in world [k :q p] 0.0)]]
                             [kp (+ e (* (Math/sqrt (Math/max 0.0 q)) (m/normal! g)))]))
            onsets (reduce (fn [acc [[k p] e]]
                             (if (acc [k p])
                               acc
                               (if (or (= h (forced [k p]))
                                       (< (m/next-double! g) (year-p e (double (get-in world [k :window] 24)))))
                                 (assoc acc [k p] h)
                                 acc)))
                           onsets drawn)]
        (recur (inc h) drawn onsets)))))

(defn simulate
  "{:p {kind {place chance of an onset within the horizon}} :joint {[j k]
  {place chance of both}} :first {kind {place mean first year}}} over
  :draws paths of :horizon years; :do {[kind place] year} forces onsets."
  [world {:keys [horizon draws seed] forced :do :or {seed 1}}]
  (let [g (m/rng seed)
        places (sort (set (mapcat (comp keys :f) (vals world))))
        kinds (sort (keys world))
        paths (vec (repeatedly draws #(one-draw world places horizon g (or forced {}))))
        rate (fn [pred] (/ (count (filter pred paths)) (double draws)))]
    {:p (into {} (for [k kinds] [k (into {} (for [p places :when (get-in world [k :f p])] [p (rate #(% [k p]))]))]))
     :joint (into {} (for [j kinds k kinds :when (not= j k)]
                       [[j k] (into {} (for [p places] [p (rate #(and (% [j p]) (% [k p])))]))]))}))

(defn impulse
  "{[j k] change}: how much an onset of j in the first year raises k's
  chance of one within the horizon, averaged over places: who transmits to
  whom (the world model's connectedness)."
  [world {:keys [horizon draws seed] :or {seed 1}}]
  (let [places (sort (set (mapcat (comp keys :f) (vals world))))
        base (:p (simulate world {:horizon horizon :draws draws :seed seed}))
        mean (fn [xs] (/ (reduce + xs) (double (max 1 (count xs)))))]
    (into {} (for [j (keys world)
                   :let [hit (:p (simulate world {:horizon horizon :draws draws :seed seed
                                                  :do (into {} (for [p places] [[j p] 1]))}))]
                   k (keys world) :when (not= j k)]
               [[j k] (mean (for [p places :when (get-in base [k p])] (- (get-in hit [k p]) (get-in base [k p]))))]))))
