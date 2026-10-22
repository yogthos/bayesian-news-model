(ns bnm.network
  "The influence map as one multilayer network (Kivela et al. 2014;
  docs/STOCK-FLOW.md): nodes are [layer place dimension] (an event kind,
  a sector, a stock at a place), edges weighted influences within a
  layer (one kind on another, a sector supplying another) or between
  layers (a sector's slump on a kind of crisis). Its supra-adjacency W
  (W[i][j], i's influence on j) gives who transmits most (Katz
  influence), whether shocks amplify or die out (the spectral radius) and
  how far one reaches ((I - W')^-1). The same network holds the event
  graph (bnm.relation-diag), the input-output coefficients (the spine)
  and trade between places.")

(defn network "An empty network." [] {:out {} :nodes #{}})

(defn edge
  "The network with an edge from i to j of weight w (added to any there)."
  [n i j w]
  (-> n (update-in [:out i j] (fnil + 0.0) (double w)) (update :nodes conj i j)))

(defn nodes [n] (:nodes n))
(defn layers [n] (distinct (map #(if (vector? %) (first %) %) (:nodes n))))
(defn weight [n i j] (get-in n [:out i j] 0.0))

(defn inter-layer
  "The edges between layers, [i j w]."
  [n]
  (for [[i js] (:out n) [j w] js :when (and (vector? i) (vector? j) (not= (first i) (first j)))] [i j w]))

(defn- step
  "x W: what each node receives from x in one step."
  [n x]
  (reduce (fn [acc [i js]] (let [xi (get x i 0.0)] (if (zero? xi) acc (reduce (fn [a [j w]] (update a j (fnil + 0.0) (* xi w))) acc js))))
          {} (:out n)))

(defn influence
  "{node influence}: the Katz influence of each node, the sum over k >= 1
  of alpha^k times the weight of the walks of length k from it, everything
  it reaches downstream, attenuated by alpha per step (a sum that needs
  alpha times the spectral radius below 1)."
  [n alpha]
  (into {} (for [i (nodes n)]
             [i (loop [x {i 1.0} k 0 total 0.0]
                  (let [x' (update-vals (step n x) #(* alpha %))
                        s (reduce + 0.0 (vals x'))]
                    (if (or (< (Math/abs s) 1e-15) (> k 500)) total (recur x' (inc k) (+ total s)))))])))

(defn reach
  "{node effect}: what a unit shock at `i` adds at every node, summed over
  every path, i's row of (I - W)^-1 less the shock itself."
  [n i]
  (loop [x {i 1.0} acc {} k 0]
    (let [x' (step n x)]
      (if (or (empty? x') (> k 1000) (< (reduce + (map #(Math/abs (double %)) (vals x'))) 1e-15))
        acc
        (recur x' (merge-with + acc x') (inc k))))))

(defn spectral-radius
  "The largest absolute eigenvalue of W, by power iteration on the
  absolute weights (a bound for signed ones): above 1 a shock grows as it
  passes round, below it dies out."
  [n]
  (let [ns (vec (nodes n))
        abs-n (update n :out (fn [o] (update-vals o (fn [js] (update-vals js #(Math/abs (double %)))))))]
    (if (empty? ns)
      0.0
      (loop [x (zipmap ns (repeat 1.0)) k 0 lam 0.0]
        (let [y (step abs-n x)
              norm (Math/sqrt (reduce + 0.0 (map #(* % %) (vals y))))]
          (if (< norm 1e-300)
            0.0
            (let [y (update-vals y #(/ % norm))
                  lam' norm]
              (if (or (> k 2000) (and (pos? k) (< (Math/abs (- lam' lam)) 1e-12))) lam'
                  (recur y (inc k) lam')))))))))

(defn from-io
  "The network with place `place`'s input-output coefficients ({[from to]
  a}: the share of `to`'s output paid to `from`) as the sector layer's
  edges."
  [n place coef]
  (reduce (fn [n [[r s] a]] (edge n [:sector place r] [:sector place s] a)) n coef))

(defn from-graph
  "The network with the event graph's edges ({[j k] {:sign}}) at every
  place in `places`, each of weight `w` times its sign."
  [n graph places w]
  (reduce (fn [n [[j k] {:keys [sign]}]]
            (reduce #(edge %1 [:kind %2 j] [:kind %2 k] (* w (or sign 1))) n places))
          n graph))

(defn from-reads
  "The network with a lattice run's recorded reads (its :trace) as edges:
  from what was read to the node that read it, in layer :lattice with
  dimension [scale place], weighted by how often; a read of another kind
  (:kind) from that kind's node."
  [n kind trace]
  (reduce (fn [n {:keys [scale place reads]}]
            (reduce (fn [n {k :kind s :scale p :place}]
                      (if (and s p)
                        (edge n [:lattice (or k kind) [s p]] [:lattice kind [scale place]] 1.0)
                        n))
                    n reads))
          n trace))
