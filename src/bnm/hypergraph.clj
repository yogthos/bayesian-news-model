(ns bnm.hypergraph
  "Interactions beyond pairs (Battiston et al. 2020; docs/STOCK-FLOW.md):
  a sector's recipe as a hyperedge, its inputs needed together (critical,
  Leontief: output capped by the scarcest) or in part substitutable
  (output falls with the share-weighted shortfall), so a shortage
  propagates as an AND where a graph would add; and joint crises as
  simplices, the kinds with an onset at a place together, set against
  what their pairs imply (the log-linear model with every pair's
  association and no three-way term, fitted by iterative proportional
  fitting): a lift above one is a three-way interaction, a polycrisis
  the pairwise links miss.")

(defn hypergraph "An empty hypergraph of recipes." [] {:recipes {}})

(defn recipe
  "The hypergraph with `output` made from `inputs` ({input share}), the
  inputs :critical (needed together) or :substitutable."
  [h output inputs kind]
  (assoc-in h [:recipes output] {:inputs inputs :kind kind}))

(defn availability
  "{node availability}, 1 normal: primary inputs as `given`, each output
  from its recipe's inputs, recursively: a critical recipe the minimum
  of its inputs', a substitutable one their share-weighted mean."
  [{:keys [recipes]} given]
  (let [memo (atom {})
        av (fn av [node]
             (or (@memo node)
                 (let [x (if-let [{:keys [inputs kind]} (recipes node)]
                           (let [xs (for [[i w] inputs] [(av i) w])]
                             (case kind
                               :critical (apply min (map first xs))
                               :substitutable (/ (reduce + (map (fn [[a w]] (* a w)) xs)) (reduce + (map second xs)))))
                           (get given node 1.0))]
                   (swap! memo assoc node x)
                   x)))]
    (into {} (for [n (distinct (concat (keys given) (keys recipes) (mapcat (comp keys :inputs) (vals recipes))))]
               [n (av n)]))))

(defn- cells
  "The 2x2x2 table of `observations` over three kinds: {[ia ib ic] count}."
  [observations [a b c]]
  (frequencies (for [o observations] [(if (o a) 1 0) (if (o b) 1 0) (if (o c) 1 0)])))

(defn no-three-way
  "The table that matches every pair's margins of `table` ({[ia ib ic]
  count}) with no three-way interaction: the maximum-entropy fit by
  iterative proportional fitting (the log-linear model [AB][BC][AC])."
  [table]
  (let [ks (for [i [0 1] j [0 1] k [0 1]] [i j k])
        obs (fn [pred] (reduce + (for [c ks :when (pred c)] (get table c 0))))
        margin (fn [x f] (fn [c] (reduce + (for [d ks :when (= (f d) (f c))] (get x d 0.0)))))
        fit (fn [x f] (let [m (margin x f)] (into {} (for [c ks] [c (let [e (m c)] (if (pos? e) (* (x c) (/ (obs #(= (f %) (f c))) e)) 0.0))]))))]
    (loop [x (zipmap ks (repeat 1.0)) i 0]
      (let [x' (-> x (fit (fn [[a b _]] [a b])) (fit (fn [[_ b c]] [b c])) (fit (fn [[a _ c]] [a c])))]
        (if (or (> i 500) (< (reduce + (for [c ks] (Math/abs (- (x' c) (x c))))) 1e-9)) x' (recur x' (inc i)))))))

(defn lift
  "{:observed :expected :lift :n}: of `observations` (sets of what
  happened together), the share in which all three `kinds` happened,
  against the share expected from their pairs alone (no-three-way: the
  pairs' margins matched, no three-way term); a lift above one, a
  three-way interaction."
  [observations kinds]
  (let [t (cells observations kinds)
        n (double (count observations))
        e (no-three-way t)]
    {:observed (/ (get t [1 1 1] 0) n) :expected (/ (e [1 1 1]) n) :n n
     :lift (when (pos? (e [1 1 1])) (/ (get t [1 1 1] 0) (e [1 1 1])))}))

(defn simplex
  "The kinds with an onset at a place in the window [t, t + w): the
  simplex of what happened together there. `onsets` {kind #{years}}."
  [onsets t w]
  (set (for [[k ys] onsets :when (some #(and (<= t %) (< % (+ t w))) ys)] k)))
