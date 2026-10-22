(ns bnm.hypergraph
  "Interactions beyond pairs (Battiston et al. 2020; docs/STOCK-FLOW.md).

  A sector's recipe as a hyperedge: its inputs needed together (critical,
  Leontief: output capped by the scarcest), perfect substitutes with no
  reallocation (substitutable: the share-weighted mean, the CES limit
  sigma -> infinity), or anything between (CES with elasticity sigma:
  Leontief as sigma -> 0, Cobb-Douglas at 1, linear as sigma -> infinity).
  A shortage propagates as an AND where a graph would add.

  Joint crises as simplices, the kinds with an onset at a place together,
  set against what their pairs imply: the log-linear model [AB][BC][AC],
  every pair's association and no three-way term, fitted by iterative
  proportional fitting. With the pair margins fixed the 2x2x2 table has
  one degree of freedom, so the test is G² on 1 df against that fit and
  the effect size is the three-way log ratio of odds ratios; the lift
  (observed over fitted all-three count) is descriptive only, pulled
  toward one because the fit's margins contain the triple's own count."
  (:require [augur.special :as sp]))

(defn hypergraph "An empty hypergraph of recipes." [] {:recipes {}})

(defn recipe
  "The hypergraph with `output` made from `inputs` ({input share}, shares
  positive): :critical (needed together), :substitutable (perfect
  substitutes, no reallocation), or :ces with elasticity `sigma` > 0."
  ([h output inputs kind] (recipe h output inputs kind nil))
  ([h output inputs kind sigma]
   (let [bad (fn [why] (throw (ex-info (str "recipe for " output ": " why)
                                       {:output output :inputs inputs :kind kind :sigma sigma})))]
     (when-not (#{:critical :substitutable :ces} kind) (bad "unknown kind"))
     (when (empty? inputs) (bad "no inputs"))
     (when-not (every? #(and (number? %) (pos? %)) (vals inputs)) (bad "shares must be positive"))
     (when (and (= kind :ces) (not (and (number? sigma) (pos? sigma)))) (bad "ces needs a positive sigma"))
     (assoc-in h [:recipes output] (cond-> {:inputs inputs :kind kind} (= kind :ces) (assoc :sigma (double sigma)))))))

(defn- ces
  "CES of availabilities `xs` with normalised weights `ws`, elasticity
  `sigma`: (Σ w x^ρ)^(1/ρ), ρ = (σ - 1)/σ; the weighted geometric mean at
  σ = 1. With σ < 1 (complements) a zero input gives zero."
  [xs ws sigma]
  (if (< (Math/abs (- sigma 1.0)) 1e-12)
    (Math/exp (reduce + (map (fn [x w] (* w (Math/log x))) xs ws)))
    (let [rho (/ (- sigma 1.0) sigma)]
      (Math/pow (reduce + (map (fn [x w] (* w (Math/pow x rho))) xs ws)) (/ 1.0 rho)))))

(defn availability
  "{node availability}, 1 normal: primary inputs as `given` (1.0 when not
  given), each output from its recipe's inputs, recursively: a critical
  recipe the minimum of its inputs', a substitutable one their
  share-weighted mean, a CES one the CES of them. A `given` value at a
  node with a recipe is its own outage: the node gets the lesser of it
  and the recipe's. A cycle of recipes throws ex-info naming it."
  [{:keys [recipes]} given]
  (let [memo (atom {})
        av (fn av [path node]
             (or (@memo node)
                 (let [x (if-let [{:keys [inputs kind sigma]} (recipes node)]
                           (do (when (some #{node} path)
                                 (let [cyc (vec (drop-while #(not= % node) path))]
                                   (throw (ex-info (str "recipe cycle: " cyc) {:cycle cyc}))))
                               (let [path' (conj path node)
                                     xs (mapv #(av path' %) (keys inputs))
                                     tot (reduce + (vals inputs))
                                     ws (mapv #(/ % tot) (vals inputs))
                                     r (case kind
                                         :critical (apply min xs)
                                         :substitutable (reduce + (map * xs ws))
                                         :ces (ces xs ws sigma))]
                                 (if-let [g (get given node)] (min g r) r)))
                           (get given node 1.0))]
                   (swap! memo assoc node x)
                   x)))]
    (into {} (for [n (distinct (concat (keys given) (keys recipes) (mapcat (comp keys :inputs) (vals recipes))))]
               [n (av [] n)]))))

(defn propagate
  "availability where recipes may form cycles (an economy's do: power
  needs coal, coal mines need power). {node availability}, 1 normal, from
  the same rules, solved by iteration: every node starts at 1 (`given`
  for a primary input) and a node is recomputed, first in first out,
  whenever one of its inputs moves by more than :tol (default 1e-9).
  Every rule is monotone, so from the top the values only fall and settle
  at the largest fixed point: a cut round a loop caps the loop at the
  cut, it does not spiral to zero. Throws past :max-updates (default
  1e8) recomputations."
  ([h given] (propagate h given {}))
  ([{:keys [recipes]} given {:keys [tol max-updates] :or {tol 1e-9 max-updates 100000000}}]
   (let [prep (into {} (for [[n {:keys [inputs kind sigma]}] recipes
                             :let [tot (double (reduce + (vals inputs)))]]
                         [n {:ins (vec (keys inputs)) :ws (mapv #(/ (double %) tot) (vals inputs))
                             :kind kind :sigma sigma}]))
         users (reduce-kv (fn [m out {:keys [ins]}] (reduce #(update %1 %2 (fnil conj []) out) m ins)) {} prep)
         nodes (distinct (concat (keys given) (keys recipes) (keys users)))
         x (transient (into {} (for [n nodes] [n (if (prep n) 1.0 (double (get given n 1.0)))])))
         start (distinct (concat (filter prep (keys given)) (mapcat users (remove prep (keys given)))))]
     (loop [x x queue (into clojure.lang.PersistentQueue/EMPTY start) queued (set start) k 0]
       (if-let [n (peek queue)]
         (let [{:keys [ins ws kind sigma]} (prep n)
               xs (mapv #(get x %) ins)
               r (case kind
                   :critical (apply min xs)
                   :substitutable (reduce + (map * xs ws))
                   :ces (ces xs ws sigma))
               v (if-let [g (get given n)] (min (double g) r) r)
               old (get x n)
               queue (pop queue)
               queued (disj queued n)]
           (when (> k max-updates)
             (throw (ex-info "bnm.hypergraph/propagate did not settle" {:updates k :node n})))
           (if (> (Math/abs (- old v)) tol)
             (let [fresh (remove queued (users n))]
               (recur (assoc! x n v) (into queue fresh) (into queued fresh) (inc k)))
             (recur x queue queued (inc k))))
         (persistent! x))))))

(def ^:private ks (vec (for [i [0 1] j [0 1] k [0 1]] [i j k])))

(defn- cells
  "The 2x2x2 table of `observations` over three kinds: {[ia ib ic] count}."
  [observations [a b c]]
  (frequencies (for [o observations] [(if (o a) 1 0) (if (o b) 1 0) (if (o c) 1 0)])))

(defn no-three-way
  "The table that matches every pair's margins of `table` ({[ia ib ic]
  count}) with no three-way interaction: the maximum-likelihood fit of the
  log-linear model [AB][BC][AC] by iterative proportional fitting from a
  uniform start. A zero pair margin gives zero fitted cells under it."
  [table]
  (let [obs (fn [f v] (reduce + (for [c ks :when (= (f c) v)] (get table c 0))))
        fit (fn [x f]
              (let [m (into {} (for [v (distinct (map f ks))] [v (reduce + (for [c ks :when (= (f c) v)] (x c)))]))]
                (into {} (for [c ks :let [e (m (f c))]]
                           [c (if (pos? e) (* (x c) (/ (obs f (f c)) e)) 0.0)]))))
        fs [(fn [[a b _]] [a b]) (fn [[_ b c]] [b c]) (fn [[a _ c]] [a c])]]
    (loop [x (zipmap ks (repeat 1.0)) i 0]
      (let [x' (reduce fit x fs)]
        (if (or (>= i 10000) (< (reduce max (for [c ks] (Math/abs (- (x' c) (x c))))) 1e-10))
          x'
          (recur x' (inc i)))))))

(defn- counts
  "The table's eight counts as doubles, 0.5 added to each when any is zero."
  [table]
  (let [n (mapv #(double (get table % 0)) ks)]
    (if (some zero? n) (mapv #(+ % 0.5) n) n)))

(defn log-ror
  "The three-way log ratio of odds ratios of `table`: log of the A-B odds
  ratio when C happened over the one when it did not, the log-linear
  three-way term (times 8); 0 when the pairs explain the table. 0.5 is
  added to every cell when one is zero."
  [table]
  (let [n (zipmap ks (counts table)) l #(Math/log (n %))]
    (- (+ (l [1 1 1]) (l [1 0 0]) (l [0 1 0]) (l [0 0 1]))
       (+ (l [1 1 0]) (l [1 0 1]) (l [0 1 1]) (l [0 0 0])))))

(defn log-ror-se
  "Woolf's standard error of log-ror: sqrt(Σ 1/n), with the same 0.5."
  [table]
  (Math/sqrt (reduce + (map #(/ 1.0 %) (counts table)))))

(defn g2
  "The likelihood-ratio statistic 2 Σ O ln(O/E) of `table` against
  `expected`."
  [table expected]
  (* 2.0 (reduce + (for [c ks :let [o (double (get table c 0))] :when (pos? o)]
                     (* o (Math/log (/ o (expected c))))))))

(defn lift-table
  "{:observed :expected :lift :n :log-ror :se :g2 :p} of a 2x2x2 `table`:
  the all-three share observed and fitted with no three-way term, their
  ratio (descriptive; nil when nothing is expected), the three-way log
  ratio of odds ratios and its standard error, and G² against the fit
  with its p on 1 df (two-sided)."
  [table]
  (let [n (double (reduce + (vals table)))
        e (no-three-way table)
        o (get table [1 1 1] 0)
        g (max 0.0 (g2 table e))]
    {:observed (/ o n) :expected (/ (e [1 1 1]) n) :n n :e111 (e [1 1 1])
     :lift (when (pos? (e [1 1 1])) (/ o (e [1 1 1])))
     :log-ror (log-ror table) :se (log-ror-se table)
     :g2 g :p (sp/chi2-sf g 1)}))

(defn lift
  "lift-table over `observations` (sets of what happened together) for
  three `kinds`: does the triple happen together more (or less) than its
  pairs imply?"
  [observations kinds]
  (lift-table (cells observations kinds)))

(defn bh
  "Benjamini-Hochberg adjusted p-values (q-values) of `ps`, in their order."
  [ps]
  (let [m (count ps)
        order (sort-by #(nth ps %) (range m))
        raw (map-indexed (fn [r i] [i (/ (* (nth ps i) m) (inc r))]) order)
        adj (reverse (reductions (fn [[_ acc] [i q]] [i (min acc q)]) [nil 1.0] (reverse raw)))]
    (reduce (fn [v [i q]] (assoc v i (min 1.0 q))) (vec (repeat m 1.0)) (butlast adj))))

(defn simplex
  "The kinds with an onset at a place in the window [t, t + w): the
  simplex of what happened together there. `onsets` {kind #{years}}."
  [onsets t w]
  (set (for [[k ys] onsets :when (some #(and (<= t %) (< % (+ t w))) ys)] k)))
