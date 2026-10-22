(ns bnm.archetypes
  "Feedback structure and leverage in a stock-flow model (Meadows 2008,
  1999; Sterman 2000): the stocks' Jacobian at a state (how each stock's
  rate of change moves with each stock), its loops (signed cycles among
  the stocks) and their polarity (reinforcing when the product of the
  edges' signs is positive, balancing when negative), which change as the
  state does (shifting loop dominance); system archetypes as patterns of
  loops; and leverage, each parameter's elasticity on an outcome grouped
  by its class (constant, buffer, delay, gain, goal, rule...). Loops are
  read from the model as estimated, so an archetype found is the data's,
  not assumed."
  (:require [bnm.stockflow :as sf]))

(defn- derivatives
  "{stock dS/dt} of `m` at time t with stock `levels`."
  [m t levels]
  (let [{:keys [flows]} (sf/at (update m :stocks (fn [ss] (into {} (for [[k s] ss] [k (assoc s :init (get levels k (:init s)))])))) t)]
    (reduce (fn [d [k x]] (let [{:keys [from to]} (get-in m [:flows k])]
                            (cond-> d from (update from (fnil - 0.0) x) to (update to (fnil + 0.0) x))))
            (zipmap (keys levels) (repeat 0.0)) flows)))

(defn jacobian
  "{[i j] d(dS_j/dt)/dS_i}: how stock j's rate of change moves with stock
  i, by central differences at `levels`: an edge from i to j."
  [m t levels]
  (let [ss (vec (keys levels))]
    (into {} (for [i ss :let [h (* 1e-6 (max 1.0 (Math/abs (double (levels i)))))
                              up (derivatives m t (update levels i + h))
                              down (derivatives m t (update levels i - h))]
                   j ss]
               [[i j] (/ (- (get up j 0.0) (get down j 0.0)) (* 2.0 h))]))))

(defn flow-jacobian
  "{[i j flow] w}: each flow's sensitivity to stock i, signed by its effect
  on stock j (+ into j, - out of it): the Jacobian resolved by flow, so a
  stock fed by growth and drained by crowding shows both loops."
  [m t levels]
  (let [ss (vec (keys levels))
        flows-at (fn [lv] (:flows (sf/at (update m :stocks (fn [st] (into {} (for [[k s] st] [k (assoc s :init (get lv k (:init s)))])))) t)))]
    (into {} (for [i ss :let [h (* 1e-6 (max 1.0 (Math/abs (double (levels i)))))
                              up (flows-at (update levels i + h))
                              down (flows-at (update levels i - h))]
                   [k {:keys [from to]}] (:flows m)
                   :let [d (/ (- (get up k 0.0) (get down k 0.0)) (* 2.0 h))]
                   :when (> (Math/abs d) 1e-12)
                   [j sign] [[to 1.0] [from -1.0]] :when j]
               [[i j k] (* sign d)]))))

(defn loops
  "[{:stocks [..] :flows [..] :gain g :polarity}]: the cycles among the
  stocks in a Jacobian, `jac` {[i j] w} or resolved by flow {[i j flow]
  w} (edges above 1e-12 in size), up to length 4, each once (from its
  smallest stock, a cycle through other flows another loop), its gain
  the product of its edges."
  [jac]
  (let [edges (vec (for [[k w] jac :when (> (Math/abs (double w)) 1e-12)] [k w]))
        out (group-by (comp first first) edges)
        ss (sort (distinct (map (comp first first) edges)))
        paths (fn [s]
                (loop [frontier [[[s] [] 1.0]] found []]
                  (if (empty? frontier)
                    found
                    (let [ext (for [[nodes labels g] frontier [[_ j f] w] (get out (peek nodes))
                                    :when (not (neg? (compare j s)))]
                                [(conj nodes j) (conj labels f) (* g w)])
                          closed (filter #(= s (peek (first %))) ext)
                          open (filter #(and (not= s (peek (first %))) (apply distinct? (first %)) (< (count (first %)) 5)) ext)]
                      (recur open (into found closed))))))]
    (vec (distinct (for [s ss [nodes labels g] (paths s)]
                     {:stocks (pop nodes) :flows labels :gain g :polarity (if (pos? g) :reinforcing :balancing)})))))

(defn archetypes
  "The system archetypes the loops show: :escalation (two stocks feeding
  each other, reinforcing), :limits-to-growth (a reinforcing loop on a
  stock met by a balancing one on it), :policy-resistance (two balancing
  loops through one stock), :success-to-the-successful (two reinforcing
  loops that draw on each other)."
  [loops]
  (let [on (fn [s pol] (filter #(and (= pol (:polarity %)) (some #{s} (:stocks %))) loops))
        stocks (distinct (mapcat :stocks loops))]
    (cond-> []
      (some #(and (= 2 (count (:stocks %))) (= :reinforcing (:polarity %))) loops) (conj :escalation)
      (some #(and (seq (on % :reinforcing)) (seq (on % :balancing))) stocks) (conj :limits-to-growth)
      (some #(>= (count (on % :balancing)) 2) stocks) (conj :policy-resistance)
      (some #(>= (count (on % :reinforcing)) 2) stocks) (conj :success-to-the-successful))))

(defn leverage
  "Each parameter's elasticity on `outcome` (a fn of a run), by a 10% rise,
  and the mean size of the elasticities in each class (`classes`
  {param class}: Meadows' leverage points, constants weakest, goals and
  rules strongest): {:by-param :by-class}."
  [m classes run-opts outcome]
  (let [base (outcome (sf/run m run-opts))
        el (into {} (for [[p _] classes
                          :let [v (get-in m [:params p])
                                up (outcome (sf/run m (assoc run-opts :params {p (* 1.1 v)})))]]
                      [p (/ (/ (- up base) base) 0.1)]))]
    {:by-param el
     :by-class (into {} (for [[c ps] (group-by val classes)]
                          [c (/ (reduce + (map #(Math/abs (double (el (key %)))) ps)) (count ps))]))}))
