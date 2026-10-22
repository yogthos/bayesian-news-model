(ns bnm.world
  "The world model as an artificial system (docs/WORLD-MODEL.md; Wang
  Fei-Yue's parallel systems): every kind's node, as learned, run forward
  together so that what one kind does reaches the others through the
  relations the models learned. Computational experiments on it: set a
  kind's onset at a place and follow what follows.

  A world is {kind {:f {place linear predictor} :q {place variance}
  :window months :parents {parent {:form :coef :lag}}}}: each kind's last
  forecast for each place (its year node's f and q) and, per parent, the
  edge's form (bnm.relations/edge-input), its effect on the log odds per
  raw unit of the input (the models' coefficients, weighted by the model
  averaging, over the input's spread) and the months before the parent's
  onsets are known. Forward, year by year: each kind's linear predictor is
  its last one plus each edge's change in its input times the effect (a
  simulated onset of the parent entering once it would be known, as the
  lattice reads it; a destabilized parent, its simulated state less its
  last); a state is drawn from N(f, q), an onset in the year from its
  window's chance scaled to a year under a constant hazard. Everything else
  a node reads is held where it last was: the experiment isolates the
  relations."
  (:require [augur.math :as m]))

(defn- year-p
  "The chance of an onset in a year from the log odds for a window of
  `window` months, under a constant hazard."
  ^double [^double eta ^double window]
  (- (Math/expm1 (* (/ 12.0 window) (Math/log1p (- (m/expit eta)))))))

(defn edge-change
  "The change in an edge's input ({:form :lag}) at the origin starting
  simulated year h (month 12(h - 1)), from the parent's first simulated
  onset (in year `onset`, at its middle, or nil) and its simulated state
  less its last (`dj`)."
  ^double [{:keys [form lag]} h onset dj]
  (let [t (* 12.0 (dec h))
        m (when onset (+ 6.0 (* 12.0 (dec onset))))
        known? (and m (<= (+ m (double (or lag 12))) t))
        within (fn [w] (if (and known? (> m (- t (double (or lag 12)) w))) 1.0 0.0))]
    (case form
      :post1 (within 12.0)
      :post3 (within 36.0)
      :e1 (if known? (Math/exp (- (/ (- t m) 12.0))) 0.0)
      :e5 (if known? (Math/exp (- (/ (- t m) 60.0))) 0.0)
      :destab (double dj))))

(defn- one-draw
  "One path: {[kind place] first-year-of-onset or nil}, in years from 1."
  [world places horizon g forced]
  (loop [h 1 f-now (into {} (for [[k {:keys [f]}] world p places] [[k p] (get f p)]))
         onsets {}]
    (if (> h horizon)
      onsets
      (let [;; each kind's predictor: its last one plus what its parents changed
            eta (into {} (for [[k {:keys [f parents]}] world p places
                               :let [base (get f p)]
                               :when base]
                           [[k p] (+ base
                                     (reduce + 0.0
                                             (for [[j {:keys [coef] :as edge}] parents
                                                   :let [dj (- (or (f-now [j p]) 0.0) (or (get-in world [j :f p]) 0.0))]]
                                               (* (double coef) (edge-change edge h (onsets [j p]) dj)))))]))
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

(defn joints
  "[{:j :k :place :pj :pk :joint}] for each of `edges` ([j k]) and place:
  the simulated chances of each onset and of both within the horizon."
  [world edges opts]
  (let [{:keys [p joint]} (simulate world opts)]
    (vec (for [[j k] edges p' (sort (keys (get p j)))
               :when (get-in p [k p'])]
           {:j j :k k :place p' :pj (get-in p [j p']) :pk (get-in p [k p']) :joint (get-in joint [[j k] p'])}))))
