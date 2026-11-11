(ns bnm.compose
  "Composition up the place and time trees (docs/RECURSIVE.md §2.1-2.2):
  the identity quantities, which survive a zoom exactly. Pure functions of
  nodes (bnm.node) and numbers; nothing here is learned.

  Place tree (children of one parent, same scale): stocks sum, external
  flows sum and flows between children cancel, Poisson rates add, and the
  chance of any onset is integrated over a frailty the children share.
  Time tree (fine windows inside a coarse one, same place): cumulative
  hazards add over consecutive windows that do not overlap; windows that
  overlap (the lattice's quarters, forecasting 3-12 and 12-24 months
  ahead from origins three months apart) are refused, and must be passed
  as per-month rates over the stretch each one covers."
  (:require [augur.enkf :as enkf]
            [bnm.lattice :as lt]))

(defn- fail [msg data] (throw (ex-info (str "bnm.compose: " msg) data)))

;; -- rates and window probabilities ------------------------------------------------

(defn rate->p
  "The chance of an onset in a window of w months at a constant rate lam
  per month: 1 - exp(-lam w)."
  ^double [lam w]
  (- (Math/expm1 (- (* (double lam) (double w))))))

(defn p->rate
  "The constant rate per month that gives chance p in w months:
  -log(1 - p) / w. p is kept below 1."
  ^double [p w]
  (/ (- (Math/log1p (- (Math/min (double p) (- 1.0 1e-15))))) (double w)))

;; -- stocks: sums, and the allocation that inverts them ----------------------------------

(defn aggregate "The sum of xs, the place tree's identity for a stock or a flow." ^double [xs] (reduce + 0.0 xs))

(defn shares "Each x's share of the sum of xs." [xs]
  (let [s (aggregate xs)]
    (when (zero? s) (fail "no shares of a zero sum" {:xs xs}))
    (mapv #(/ (double %) s) xs)))

(defn allocate
  "x split in proportion to `ws` (non-negative weights, normalized here):
  the top-down inverse of aggregate."
  [x ws]
  (let [s (aggregate ws)]
    (when-not (pos? s) (fail "allocation weights must have a positive sum" {:weights ws}))
    (mapv #(* (double x) (/ (double %) s)) ws)))

(defn sum-stocks
  "{:level :sd :n :missing :flags} of `stock-id` summed over `children`
  (nodes): sd^2 = Σ sd_c^2 + 2 Σ cov, the covariances from opts :cov
  {[id-a id-b] cov} where an ensemble gives them, else independence,
  flagged :independence-assumed. A child without the stock is listed in
  :missing, never read as 0."
  ([children stock-id] (sum-stocks children stock-id {}))
  ([children stock-id {:keys [cov]}]
   (let [have (filter #(get-in % [:stocks stock-id :level]) children)
         var (reduce + 0.0 (map #(let [s (double (or (get-in % [:stocks stock-id :sd]) 0.0))] (* s s)) have))
         ids (set (map :id have))
         covs (reduce + 0.0 (for [[[a b] v] cov :when (and (ids a) (ids b) (not= a b))] (double v)))]
     {:level (aggregate (map #(get-in % [:stocks stock-id :level]) have))
      :sd (Math/sqrt (Math/max 0.0 (+ var (* 2.0 covs))))
      :n (count have)
      :missing (vec (map :id (remove #(get-in % [:stocks stock-id :level]) children)))
      :flags (if (seq cov) #{} #{:independence-assumed})})))

;; -- flows: internal pairs cancel -------------------------------------------------------

(defn- owner
  "The node an end of a flow belongs to: :outside, [node-id stock] (a
  sibling's or the child's own, named), or a bare stock id (the child's)."
  [child end]
  (cond (= :outside end) :outside
        (and (vector? end) (vector? (first end))) (first end)
        :else (:id child)))

(defn net-flows
  "The children's flows seen from their parent. Each child lists its flows
  {flow-id {:rate r :from end :to end}}, an end :outside, a stock of its
  own, or [node-id stock] for a sibling's; a flow between two children is
  booked by both under one id. internal? (fn [flow owners]) says whether a
  flow stays inside the parent (default: both ends are children).
  {:net Σ of each child's own net inflow, :external {id signed rate},
   :external-net, :internal {id rate}, :residual :net less :external-net}.
  Internal flows cancel: +r at one child, -r at the other. A flow booked by
  one side only does not, and shows as the residual, a hidden buffer
  (PLAN 1.7), never as an external flow."
  ([children] (net-flows children nil))
  ([children internal?]
   (let [ids (set (map :id children))
         internal? (or internal? (fn [_ [a b]] (and (ids a) (ids b))))
         own-net (fn [child]
                   (reduce + 0.0 (for [[_ {:keys [rate from to]}] (:flows child)
                                       :let [o-from (owner child from) o-to (owner child to)]]
                                   (- (if (= o-to (:id child)) (double rate) 0.0)
                                      (if (= o-from (:id child)) (double rate) 0.0)))))
         flows (into {} (for [child children [fid f] (:flows child)]
                          [fid [f [(owner child (:from f)) (owner child (:to f))]]]))
         {ext false int true} (group-by (fn [[_ [f os]]] (boolean (internal? f os))) flows)
         external (into {} (for [[fid [f [a b]]] ext]
                             [fid (- (if (ids b) (double (:rate f)) 0.0) (if (ids a) (double (:rate f)) 0.0))]))
         net (reduce + 0.0 (map own-net children))
         ext-net (aggregate (vals external))]
     {:net net :external external :external-net ext-net
      :internal (into {} (for [[fid [f _]] int] [fid (:rate f)]))
      :residual (- net ext-net)})))

;; -- hazards ------------------------------------------------------------------------------

(defn rate-sum
  "{:rate Σ λ_c :n :missing}: the children's onset rates of `kind` (per
  month) added, exact for expected counts. A child with no rate is
  listed, never read as 0."
  [children kind]
  (let [have (filter #(get-in % [:hazard kind :rate]) children)]
    {:rate (aggregate (map #(get-in % [:hazard kind :rate]) have))
     :n (count have)
     :missing (vec (map :id (remove #(get-in % [:hazard kind :rate]) children)))}))

(defn- frailty-points
  "[[weight z]] for z ~ N(z, v) by the lattice's Gauss-Hermite nodes."
  [{:keys [z z-var]}]
  (let [z (double (or z 0.0)) sd (Math/sqrt (Math/max 0.0 (double (or z-var 0.0))))]
    (if (zero? sd) [[1.0 z]] (mapv (fn [[x w]] [w (+ z (* sd x))]) lt/hermite))))

(defn frailty-mean
  "E[e^z] by the same quadrature: the factor on the expected count."
  [frailty]
  (reduce + (for [[w z] (frailty-points frailty)] (* w (Math/exp z)))))

(defn any-onset
  "P(any onset of `kind` among `children` in w months), the children
  independent given a frailty z ~ N(:z, :z-var) they share:
  1 - E_z[Π_c exp(-λ_c e^z w)], by Gauss-Hermite quadrature (the
  lattice's nodes). With :z-var 0 and :z 0 it is 1 - Π(1 - p_c)."
  ^double [children kind frailty w]
  (let [{:keys [rate missing]} (rate-sum children kind)]
    (when (seq missing) (fail "a child without a rate" {:kind kind :missing missing}))
    (- 1.0 (reduce + (for [[wt z] (frailty-points frailty)]
                       (* wt (Math/exp (- (* rate (Math/exp z) (double w))))))))))

;; -- the time tree --------------------------------------------------------------------------

(defn- cumhaz-of
  "A window's cumulative hazard: from its :p, or its per-month :rate times
  its length."
  [{:keys [p rate from to] :as win}]
  (cond (some? rate) (* (double rate) (- (double to) (double from)))
        (some? p) (- (Math/log1p (- (double p))))
        :else (fail "a window needs :p or :rate" {:window win})))

(defn survival-chain
  "{:p :cumhaz :from :to}: the chance of an onset in the union of
  consecutive fine windows [{:from :to (months, (from, to]) and :p or a
  per-month :rate}], 1 - Π(1 - p_f), i.e. cumulative hazards added.
  Windows that overlap are refused (they are not composable by product),
  as is a window without its bounds; a gap between windows is refused
  unless opts :gaps :allow (the result then is not the span's chance)."
  ([fine] (survival-chain fine {}))
  ([fine {:keys [gaps]}]
   (when (empty? fine) (fail "no windows" {}))
   (when-let [bad (seq (remove #(and (number? (:from %)) (number? (:to %)) (< (:from %) (:to %))) fine))]
     (fail "a window without its bounds" {:windows (vec bad)}))
   (let [ws (sort-by (juxt :from :to) fine)]
     (doseq [[a b] (map vector ws (rest ws))]
       (when (< (:from b) (:to a)) (fail "overlapping windows do not compose by survival" {:a a :b b}))
       (when (and (> (:from b) (:to a)) (not= :allow gaps)) (fail "a gap between windows" {:a a :b b})))
     (let [lam (reduce + 0.0 (map cumhaz-of ws))]
       {:p (- (Math/expm1 (- lam))) :cumhaz lam :from (:from (first ws)) :to (:to (last ws))}))))

(defn cumhaz-split
  "Top-down allocation of a coarse hazard to fine windows, in proportion to
  shares of its cumulative hazard. A number (Λ, or a per-month λ) and
  `shares` (weights) give the fine numbers; a window {:p :from :to} and
  `shares` [{:from :to :share}] that tile it give fine windows {:from :to
  :p :cumhaz}. survival-chain of the windows gives the coarse p back."
  [coarse shares]
  (if (number? coarse)
    (allocate coarse shares)
    (let [ws (sort-by :from shares)
          lam (cumhaz-of coarse)]
      (when-not (and (= (:from (first ws)) (:from coarse)) (= (:to (last ws)) (:to coarse))
                     (every? (fn [[a b]] (= (:to a) (:from b))) (map vector ws (rest ws))))
        (fail "the shares must tile the coarse window" {:coarse coarse :shares shares}))
      (mapv (fn [w l] {:from (:from w) :to (:to w) :cumhaz l :p (- (Math/expm1 (- l)))})
            ws (allocate lam (map :share ws))))))

;; -- reconciliation onto linear identities ------------------------------------------------------

(defn reconcile-project
  "`values` {id x} or {id {:level x :sd s}} projected onto the linear
  `identities` [{:coefs {id a} :b b}] (Σ a x = b): the nearest point,
  Euclidean (augur.enkf/project) when no value has an sd, else weighted by
  each value's variance (the projection in the scaled space x/sd; a value
  with sd 0 does not move). {:values {id x'} :residual [Σ a x - b before]}."
  [values identities]
  (let [ids (vec (sort-by str (keys values)))
        level #(let [v (values %)] (double (if (map? v) (:level v) v)))
        sds (when (some map? (vals values))
              (mapv #(let [v (values %)] (double (if (map? v) (or (:sd v) 0.0) 1.0))) ids))
        x (mapv level ids)
        a (mapv (fn [{:keys [coefs]}]
                  (when-let [bad (seq (remove (set ids) (keys coefs)))]
                    (fail "an identity names a value not given" {:ids (vec bad)}))
                  (mapv #(double (get coefs % 0.0)) ids))
                identities)
        b (mapv #(double (:b %)) identities)
        residual (mapv (fn [row bi] (- (reduce + (map * row x)) bi)) a b)
        x' (if sds
             ;; y = x / sd, A' = A diag(sd): Euclidean in y is weighted in x
             (let [fixed? (mapv zero? sds)
                   sc (mapv #(if (zero? %) 1.0 %) sds)
                   ;; fixed values leave the problem: their terms move to b
                   free (vec (keep-indexed (fn [i f] (when-not f i)) fixed?))
                   b' (mapv (fn [row bi] (- bi (reduce + (for [i (range (count ids)) :when (fixed? i)] (* (row i) (x i)))))) a b)
                   a' (mapv (fn [row] (mapv #(* (row %) (sc %)) free)) a)
                   y (enkf/project (mapv #(/ (x %) (sc %)) free) a' b')]
               (reduce (fn [v [k i]] (assoc v i (* (y k) (sc i)))) x (map-indexed vector free)))
             (enkf/project x a b))]
    {:values (zipmap ids x') :residual residual}))
