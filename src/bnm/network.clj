(ns bnm.network
  "The influence map as a multilayer network (Kivela et al. 2014;
  docs/STOCK-FLOW.md): nodes are [layer place dimension] (an event kind,
  a sector, a lattice cell), edges weighted influences within a layer (one
  kind on another, a sector on another) or between layers (a sector's
  slump on a kind of crisis, built with `couple`, or one kind's lattice
  reading another's). Its weight matrix W (W[i][j], i's influence on j; a
  shock x passes on as x W) gives who transmits most (Katz influence),
  whether shocks amplify or die out (the spectral radius) and how far one
  reaches (i's row of (I - W)^-1). Builders: the event graph
  (bnm.relation-diag, `from-graph`), the input-output tables (the spine;
  `from-io` for cost-push, `from-io-supply` for supply-push) and a lattice
  run's reads (`from-reads`). Trade between places is not a layer yet
  (planned, from the cereal trade data).")

(defn network "An empty network." [] {:out {} :nodes #{}})

(defn edge
  "The network with an edge from i to j of weight w (added to any there)."
  [n i j w]
  (-> n (update-in [:out i j] (fnil + 0.0) (double w)) (update :nodes conj i j)))

(defn nodes [n] (:nodes n))
(defn weight [n i j] (get-in n [:out i j] 0.0))

(defn layer
  "A node's layer: the first of [layer place dimension]; nil for a bare
  node (one outside the layered scheme)."
  [i]
  (when (vector? i) (first i)))

(defn layers "The distinct layers of the network's layered nodes." [n] (distinct (keep layer (:nodes n))))

(defn inter-layer
  "The edges between layers, [i j w]."
  [n]
  (for [[i js] (:out n) [j w] js
        :let [li (layer i) lj (layer j)]
        :when (and li lj (not= li lj))]
    [i j w]))

;; ---------------------------------------------------------------- algebra

(def ^:private tol 1e-13)
(def ^:private max-steps 100000)

(defn- l1 [x] (reduce + 0.0 (map #(Math/abs (double %)) (vals x))))

(defn- step
  "x W: what each node receives from x in one step."
  [n x]
  (let [out (:out n)]
    (reduce-kv (fn [acc i xi]
                 (if (zero? xi)
                   acc
                   (reduce-kv (fn [a j w] (assoc a j (+ (get a j 0.0) (* xi w)))) acc (get out i {}))))
               {} x)))

(defn- pull
  "W u: what each node sends on, weighted by u at its targets."
  [n u]
  (reduce-kv (fn [acc i js]
               (let [s (reduce-kv (fn [s j w] (+ s (* w (get u j 0.0)))) 0.0 js)]
                 (if (zero? s) acc (assoc acc i s))))
             {} (:out n)))

(defn- abs-weights [n]
  (update n :out (fn [o] (update-vals o (fn [js] (update-vals js #(Math/abs (double %))))))))

(defn- dfs
  "Depth-first from `root` over `succ`, skipping `seen`: [seen order],
  `order` extended with the nodes in the order they finish."
  [succ root seen order]
  (loop [stack [[root (succ root)]] seen (conj seen root) order order]
    (if-let [[v rs] (peek stack)]
      (let [rs (drop-while seen rs)]
        (if-let [w (first rs)]
          (recur (conj (pop stack) [v (rest rs)] [w (succ w)]) (conj seen w) order)
          (recur (pop stack) seen (conj order v))))
      [seen order])))

(defn- components
  "The strongly connected components of the edges of non-zero weight
  (Kosaraju), each a set of nodes."
  [n]
  (let [fwd (update-vals (:out n) (fn [js] (vec (keep (fn [[j w]] (when-not (zero? w) j)) js))))
        bwd (reduce-kv (fn [m i js] (reduce #(update %1 %2 (fnil conj []) i) m js)) {} fwd)
        order (second (reduce (fn [[seen order] v] (if (seen v) [seen order] (dfs #(get fwd % []) v seen order)))
                              [#{} []] (:nodes n)))]
    (second (reduce (fn [[seen comps] v]
                      (if (seen v)
                        [seen comps]
                        (let [[seen c] (dfs #(get bwd % []) v seen [])] [seen (conj comps (set c))])))
                    [#{} []] (rseq order)))))

(defn- irreducible-radius
  "The Perron root of a non-negative, irreducible |W| (one strongly
  connected component): power iteration on I + |W|, primitive, so its root
  1 + rho is the only eigenvalue of largest modulus even where |W| is
  periodic (a cycle, where plain power iteration oscillates). Converged
  when the vector stops moving: the estimate's own change can vanish by
  chance where the next eigenvalues are complex."
  [a ns]
  (loop [x (zipmap ns (repeat (/ 1.0 (Math/sqrt (count ns))))) k 0]
    (let [y (merge-with + x (step a x))
          norm (Math/sqrt (reduce + 0.0 (map #(* % %) (vals y))))
          y (update-vals y #(/ % norm))
          moved (Math/sqrt (reduce + 0.0 (map (fn [[i v]] (let [d (- v (get x i 0.0))] (* d d))) y)))]
      (cond (< moved 1e-12) (max 0.0 (- norm 1.0))
            (> k max-steps) (throw (ex-info "bnm.network: the spectral radius did not converge"
                                            {:steps k :estimate (- norm 1.0) :moved moved}))
            :else (recur y (inc k))))))

(defn spectral-radius
  "The spectral radius of |W| (the largest absolute eigenvalue; W's own
  for non-negative weights, an upper bound on it for signed ones): above 1
  a shock grows as it passes round, below it dies out. The largest over
  the strongly connected components, each by power iteration
  (irreducible-radius); 0 for an acyclic network (W nilpotent). Throws
  when an iteration does not converge."
  [n]
  (let [a (abs-weights n)]
    (reduce max 0.0
            (for [c (components a)
                  :let [ns (vec c)]
                  :when (or (> (count ns) 1) (pos? (weight a (first ns) (first ns))))]
              (if (= 1 (count ns))
                (weight a (first ns) (first ns))
                (irreducible-radius {:out (into {} (for [i ns] [i (select-keys (get-in a [:out i]) ns)]))} ns))))))

(defn- check-radius!
  "Throws unless alpha times the radius of |W| is below 1 (the sums over
  walks would diverge)."
  [n alpha what]
  (let [rho (spectral-radius n)]
    (when-not (< (* alpha rho) 1.0)
      (throw (ex-info (str "bnm.network: " what " diverges: alpha times the spectral radius is not below 1")
                      {:alpha alpha :spectral-radius rho})))))

(defn- series
  "x + f(x) + f(f(x)) + ... from x = f(x0), until a term's L1 norm is
  negligible; throws at the cap."
  [f x0 what]
  (loop [x (f x0) total {} k 0]
    (let [total (merge-with + total x) size (l1 x)]
      (cond (< size (* tol (max 1.0 (l1 total)))) total
            (> k max-steps) (throw (ex-info (str "bnm.network: " what " did not converge") {:steps k :last-term size}))
            :else (recur (f x) total (inc k))))))

(defn influence
  "{node influence}: the Katz influence of each node, sum over k >= 1 of
  alpha^k times the weight of the walks of length k from it (the row sums
  of (I - alpha W)^-1 less 1): everything it reaches downstream,
  attenuated by alpha per step. Throws unless alpha times the radius of
  |W| is below 1, where the sum converges."
  [n alpha]
  (check-radius! n alpha "the Katz influence")
  (let [ones (zipmap (nodes n) (repeat 1.0))
        total (series (fn [u] (update-vals (pull n u) #(* alpha %))) ones "the Katz influence")]
    (merge (zipmap (nodes n) (repeat 0.0)) total)))

(defn reach
  "{node effect}: what a unit shock at `i` adds at every node, summed over
  every path, i's row of (I - W)^-1 less the shock itself. Throws unless
  the radius of |W| is below 1, where shocks die out."
  [n i]
  (check-radius! n 1.0 "a shock's reach")
  (series #(step n %) {i 1.0} "a shock's reach"))

;; --------------------------------------------------------------- builders

(defn cost-shares
  "A, the technical coefficients {[r s] a}: intermediate flows ({[r s] z},
  r selling to s) over the buying sector's gross output ({s x}), a_rs =
  z_rs / x_s, the share of s's output paid to r."
  [flows go]
  (into {} (for [[[r s] z] flows :let [x (go s)] :when (and x (pos? x))] [[r s] (/ (double z) x)])))

(defn sales-shares
  "B, the allocation coefficients (Ghosh) {[r s] b}: flows over the
  selling sector's gross output, b_rs = z_rs / x_r, the share of r's output
  sold to s. B = X^-1 A X, so its spectral radius is A's."
  [flows go]
  (into {} (for [[[r s] z] flows :let [x (go r)] :when (and x (pos? x))] [[r s] (/ (double z) x)])))

(defn- sector-edges [n place coef]
  (reduce (fn [n [[r s] a]] (edge n [:sector place r] [:sector place s] a)) n coef))

(defn from-io
  "The network with place `place`'s technical coefficients (cost-shares,
  {[r s] a}) as the sector layer's edges r -> s. A shock passed on as x A
  is cost-push, the Leontief price model (p' = v' (I - A)^-1): a cost rise
  at r raises s's price by a_rs per unit, so `reach` is the price effect
  and `influence` (alpha 1) the summed price rise everywhere."
  [n place coef]
  (sector-edges n place coef))

(defn from-io-supply
  "The network with place `place`'s allocation coefficients (sales-shares,
  {[r s] b}) as the sector layer's edges r -> s: supply-push (Ghosh), a
  unit of primary input at r passed on downstream to its buyers.
  `influence` with alpha 1 is r's upstreamness less 1 (Antras et al.
  2012), how many stages r stands from final use."
  [n place coef]
  (sector-edges n place coef))

(defn graph-weight
  "An event-graph edge's weight from its data, signed: |log rr-lead| (the
  rise in k's risk after j), else |the sum of j's coefficients| (log-odds),
  else 1."
  [{:keys [sign rr-lead coef]}]
  (* (double (or sign 1))
     (cond (and rr-lead (pos? rr-lead)) (Math/abs (Math/log rr-lead))
           (seq coef) (Math/abs (double (reduce + coef)))
           :else 1.0)))

(defn from-graph
  "The network with the event graph's edges ({[j k] {:sign :rr-lead
  :coef}}) at every place in `places`, layer :kind, weighted by
  graph-weight. Options: :topology? (the sign alone), :scale (a factor on
  every weight, default 1)."
  ([n graph places] (from-graph n graph places {}))
  ([n graph places {:keys [topology? scale] :or {scale 1.0}}]
   (reduce (fn [n [[j k] e]]
             (let [w (* scale (if topology? (double (or (:sign e) 1)) (graph-weight e)))]
               (reduce #(edge %1 [:kind %2 j] [:kind %2 k] w) n places)))
           n graph)))

(defn couple
  "The network with inter-layer edges at `place`: {[a b] w} as
  [from-layer place a] -> [to-layer place b] (a sector's slump on a kind
  of crisis: (couple n \"USA\" :sector :kind {[\"MIN\" \"growth_collapse\"]
  0.3})). Throws when the layers are the same."
  [n place from-layer to-layer weights]
  (when (= from-layer to-layer)
    (throw (ex-info "bnm.network: couple joins two different layers" {:layer from-layer})))
  (reduce (fn [n [[a b] w]] (edge n [from-layer place a] [to-layer place b] w)) n weights))

(defn from-reads
  "The network with a lattice run's recorded reads (its :trace) as edges
  from what was read to the node that read it, nodes [kind place scale]:
  each kind its own layer, so a read of another kind's book (:kind) is an
  inter-layer edge. Each reader's in-weights are its share of that
  reader's reads over the whole trace (they sum to 1), so the weights do
  not grow with the run's length. Reads of resolved errors (:feedback)
  are dropped unless :feedback? is set."
  ([n kind trace] (from-reads n kind trace {}))
  ([n kind trace {:keys [feedback?]}]
   (let [counts (frequencies (for [{:keys [scale place reads]} trace
                                   {d :dir k :kind s :scale p :place} reads
                                   :when (or feedback? (not= d :feedback))]
                               [[(or k kind) p s] [kind place scale]]))
         totals (reduce-kv (fn [m [_ to] c] (update m to (fnil + 0) c)) {} counts)]
     (reduce-kv (fn [n [from to] c] (edge n from to (/ (double c) (totals to)))) n counts))))
