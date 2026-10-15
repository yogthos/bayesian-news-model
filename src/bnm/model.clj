(ns bnm.model
  "A hierarchical, self-exciting model of event intensity, learned online.

  Every node of the structure (dyads, countries, subregions, regions, the
  world) carries, per channel c, an expected event rate for the next bin:

    own(v,c) = mu(v,c) + sum over sources k of v, time scales s, channels c':
                           alpha(v,c,k,s,c') * h_s(k,c')

  where h_s(k,c') is source k's history at time scale s: a geometrically
  decaying average of its past bins, one fast (weeks) and one slow (months).
  A node's sources are itself, its parents (top-down context: a region heating
  up raises the odds for its countries) and its strongest same-level
  neighbours (the connections between places). This is a discrete-time
  multivariate Hawkes process with a two-exponential kernel; alpha(v,c,k,s,c')
  says how much activity of kind c' at k drives activity of kind c at v, and
  how quickly. The slow scale is what carries a forecast a year out: without
  it every node forgets its present within weeks and drifts to its long-run
  mean.

  A node with children also predicts bottom-up, from the sum of its children's
  predictions scaled by kappa (children overlap: one event between two
  countries of a region is in both countries but once in the region). The
  node's prediction blends the two, weighted by how well each has done
  lately, so a forecast is built recursively from the leaves up.

  Learning is online EM with forgetting: each bin's observation splits, in
  expectation, among the components that could have caused it (the E step),
  and the split is added to discounted sufficient statistics from which mu
  and alpha are re-estimated in closed form under Gamma priors (the M step).
  Blend weights, kappa and dispersion are tracked the same way. There is no
  separate training phase: feeding bins is training, and a model keeps tuning
  itself as long as it is fed.

  Priors are scale-free: a source's prior says what share of a node's typical
  rate it accounts for, so the same prior works for a quiet dyad and the
  world.

  Values are normalized counts: events per `scale` events the source reported
  in a typical bin (see `bnm.series`), so sources of very different volume, and
  a source whose coverage grows, give comparable numbers.

  The model is a map of plain values and primitive arrays. `observe!` and
  `forecast` mutate or read it; `bnm.store` turns it into EDN for a database.
  The arithmetic lives in small functions over ^doubles parameters, which is
  what lets jolt compile it to unboxed flonum code."
  (:require [bnm.math :as m]
            [bnm.ontology :as ont]))

(set! *unchecked-math* true)

(def ^:const C 6)
(assert (= C ont/n-channels))

(def defaults
  {:bin-days 7
   :decays [0.6 0.97]  ; history kernels, fast and slow: h <- d*h + (1-d)*y
   :forget 0.99        ; per-bin discount of sufficient statistics (~2 years)
   :prior-weight 8.0   ; prior strength, in bins of evidence
   ;; prior share of a node's mean rate each component accounts for, split
   ;; over the time scales by :scale-split
   :share {:mu 0.2
           :self-same 0.6 :self-cross 0.01
           :parent-same 0.05 :parent-cross 0.002
           :neighbor-same 0.03 :neighbor-cross 0.001}
   :scale-split [0.4 0.6]
   :blend-forget 0.95  ; memory of the bottom-up vs. own-level comparison
   :disp-rate 0.02     ; learning rate of the dispersion estimate
   :max-growth 20.0    ; a forecast rate never exceeds this multiple of the
                       ; node's typical rate plus one
   :max-branching 0.95 ; in a forecast, excitation explains at most this share
                       ; of a node's typical rate (see `stable-params`)
   :level-noise 0.0    ; in a forecast, the log-sd a node's level drifts by over
                       ; 52 bins beyond what its dynamics explain: shocks the
                       ; model has no way to see coming
   :scale 1000.0})

(defn scales ^long [cfg] (count (:decays cfg)))

;; --- array kernels ----------------------------------------------------------------------

(defn- dot-off
  "sum over q < n of a[off+q] * b[q]"
  ^double [^doubles a ^long off ^doubles b ^long n]
  (loop [q 0 acc 0.0]
    (if (< q n)
      (recur (inc q) (+ acc (* (aget a (+ off q)) (aget b q))))
      acc)))

(defn- accum-rate!
  "out[c] += alpha[c, j, .] . h for every channel."
  [^doubles out ^doubles alpha ^doubles h ^long ksc ^long jsc ^long sc]
  (dotimes [c C]
    (aset out c (+ (aget out c) (dot-off alpha (+ (* c ksc) jsc) h sc)))))

(defn- learn-source!
  "E and M steps for one source's block of alpha. r[c] is observed/expected
  for the node's channel c; sybar the source's running mean per channel."
  [^doubles alpha ^doubles sa ^doubles ta ^doubles prior ^doubles h
   ^doubles r ^doubles ybar ^doubles sybar
   ^long ksc ^long jsc ^long sc ^double rho ^double w]
  (dotimes [c C]
    (let [base (+ (* c ksc) jsc)
          rc (aget r c)
          yb (aget ybar c)
          ;; a quiet source is priced as if at a tenth of the node's own rate,
          ;; so its alpha stays bounded and does not explode when it stirs
          floor (+ 1e-6 (* 0.1 yb))]
      (dotimes [q sc]
        (let [i (+ base q)
              hv (aget h q)
              sb (Math/max (aget sybar (rem q C)) floor)
              s (+ (* rho (aget sa i)) (* rc (aget alpha i) hv))
              t (+ (* rho (aget ta i)) hv)]
          (aset sa i s)
          (aset ta i t)
          (aset alpha i (/ (+ (* (aget prior i) yb w) s) (+ (* w sb) t))))))))

(defn- learn-baseline!
  [^doubles mu ^doubles smu ^doubles tmu ^doubles r ^doubles ybar
   ^double rho ^double w ^double share]
  (dotimes [c C]
    (let [s (+ (* rho (aget smu c)) (* (aget r c) (aget mu c)))
          t (+ (* rho (aget tmu c)) 1.0)]
      (aset smu c s)
      (aset tmu c t)
      (aset mu c (/ (+ (* share (aget ybar c) w) s) (+ w t))))))

(defn- refresh-alpha!
  "The M step alone for one source's block of alpha, from its statistics and
  the current running means (see `learn-source!`)."
  [^doubles alpha ^doubles sa ^doubles ta ^doubles prior ^doubles ybar ^doubles sybar
   ^long ksc ^long jsc ^long sc ^double w]
  (dotimes [c C]
    (let [base (+ (* c ksc) jsc)
          yb (aget ybar c)
          floor (+ 1e-6 (* 0.1 yb))]
      (dotimes [q sc]
        (let [i (+ base q)
              sb (Math/max (aget sybar (rem q C)) floor)]
          (aset alpha i (/ (+ (* (aget prior i) yb w) (aget sa i)) (+ (* w sb) (aget ta i)))))))))

(defn- refresh-mu!
  [^doubles mu ^doubles smu ^doubles tmu ^doubles ybar ^double w ^double share]
  (dotimes [c C]
    (aset mu c (/ (+ (* share (aget ybar c) w) (aget smu c)) (+ w (aget tmu c))))))

(defn- ratios!
  "r[c] = y[c] / own[c]: how much of what was expected came about."
  [^doubles r ^doubles y ^doubles own]
  (dotimes [c C]
    (aset r c (/ (aget y c) (Math/max 1e-9 (aget own c))))))

(defn- add-into! [^doubles acc ^doubles x]
  (dotimes [c C] (aset acc c (+ (aget acc c) (aget x c)))))

(defn- push-history!
  "h[s, c] <- d_s h[s, c] + (1 - d_s) y[c]"
  [^doubles h ^doubles y ^doubles decays]
  (dotimes [s (alength decays)]
    (let [d (aget decays s)
          off (* s C)]
      (dotimes [c C]
        (aset h (+ off c) (+ (* d (aget h (+ off c))) (* (- 1.0 d) (aget y c))))))))

(defn- blend!
  "lam = w*own + (1-w)*kappa*children, w from recent losses; leaves keep own.
  Caps each rate at max-growth times the node's typical rate."
  [^doubles lam ^doubles own ^doubles lch ^doubles chsum ^doubles kappa
   ^doubles lo ^doubles lc ^doubles ybar ^double max-growth leaf?]
  (dotimes [c C]
    (let [o (aget own c)
          v (if leaf?
              o
              (let [b (* (aget kappa c) (aget chsum c))
                    l1 (aget lo c) l2 (aget lc c)
                    w (/ l2 (+ l1 l2 1e-12))]
                (aset lch c b)
                (+ (* w o) (* (- 1.0 w) b))))]
      (aset lam c (Math/min (Math/max 0.0 v) (* max-growth (+ 1.0 (aget ybar c))))))))

(defn- sq-log-err
  "sum over c of (log1p y - log1p p)^2"
  ^double [^doubles y ^doubles p]
  (loop [c 0 acc 0.0]
    (if (< c C)
      (let [e (- (Math/log1p (aget y c)) (Math/log1p (Math/max 0.0 (aget p c))))]
        (recur (inc c) (+ acc (* e e))))
      acc)))

(defn- track!
  [^doubles y ^doubles chy ^doubles ybar ^doubles disp ^doubles lam ^doubles own
   ^doubles lch ^doubles lo ^doubles lc ^doubles kappa ^doubles sk ^doubles tk
   ^double rho ^double rb ^double eta ^double w has-children?]
  (dotimes [c C]
    (let [yc (aget y c)
          l (Math/max 1e-6 (aget lam c))
          ly (Math/log1p yc)
          dev (- yc l)]
      (aset ybar c (+ (* rho (aget ybar c)) (* (- 1.0 rho) yc)))
      (aset disp c (Math/max 1.0 (+ (* (- 1.0 eta) (aget disp c)) (* eta (/ (* dev dev) (+ l 1.0))))))
      (when has-children?
        (let [eo (- ly (Math/log1p (aget own c)))
              ec (- ly (Math/log1p (aget lch c)))]
          (aset lo c (+ (* rb (aget lo c)) (* eo eo)))
          (aset lc c (+ (* rb (aget lc c)) (* ec ec))))
        (aset sk c (+ (* rho (aget sk c)) yc))
        (aset tk c (+ (* rho (aget tk c)) (aget chy c)))
        (aset kappa c (/ (+ w (aget sk c)) (+ w (aget tk c))))))))

;; --- nodes ------------------------------------------------------------------------------

(defn- kind-shares [cfg kind]
  (let [s (:share cfg)]
    (case kind
      :self [(:self-same s) (:self-cross s)]
      :parent [(:parent-same s) (:parent-cross s)]
      :neighbor [(:neighbor-same s) (:neighbor-cross s)])))

(defn- new-node [n index cfg]
  (let [srcs (vec (concat [[(:id n) :self]]
                          (map vector (:parents n) (repeat :parent))
                          (map vector (:neighbors n) (repeat :neighbor))))
        srcs (filterv (comp index first) srcs)
        k (count srcs)
        S (scales cfg)
        sc (* S C)
        split (:scale-split cfg)
        prior (double-array (* C k sc))]
    (dotimes [c C]
      (dotimes [j k]
        (let [[same cross] (kind-shares cfg (second (srcs j)))]
          (dotimes [s S]
            (dotimes [c2 C]
              (aset prior (+ (* c k sc) (* j sc) (* s C) c2)
                    (double (* (nth split s) (if (= c c2) same cross)))))))))
    {:id (:id n)
     :level (:level n)
     :sources (mapv first srcs)
     :kinds (mapv second srcs)
     :src (int-array (map (comp index first) srcs))
     :children (int-array (keep index (:children n)))
     :prior prior
     :h (double-array sc) :last (double-array C) :ybar (double-array C)
     :mu (double-array C) :smu (double-array C) :tmu (double-array C)
     :alpha (double-array (* C k sc)) :sa (double-array (* C k sc)) :ta (double-array (* C k sc))
     :kappa (double-array C 1.0) :sk (double-array C) :tk (double-array C)
     :loss-own (double-array C 1.0) :loss-ch (double-array C 1.0)
     :disp (double-array C 1.0)
     :lam (double-array C) :lam-own (double-array C) :lam-ch (double-array C)
     :r (double-array C) :chsum (double-array C)}))

(defn- merge-config [config]
  (merge-with (fn [a b] (if (map? a) (merge a b) b)) defaults config))

(defn create
  "A fresh model over `structure` (see `bnm.structure/build`)."
  ([structure] (create structure {}))
  ([structure config]
   (let [cfg (merge-config config)
         ids (mapv :id (:nodes structure))
         index (zipmap ids (range))]
     {:config cfg
      :structure structure
      :index index
      :nodes (mapv #(new-node % index cfg) (:nodes structure))
      :bin nil
      :exposure {}
      :stats {:bins 0 :sq-log-err 0.0 :sq-log-err-base 0.0 :points 0.0}})))

;; --- prediction -------------------------------------------------------------------------

(defn- own-rate!
  "Writes into `out` the node's own-level rate from histories `hs` (an object
  array of per-node history arrays)."
  [node ^objects hs ^doubles out ^long sc]
  (let [^ints src (:src node)
        k (alength src)
        ksc (* k sc)]
    (System/arraycopy (:mu node) 0 out 0 C)
    (dotimes [j k]
      (accum-rate! out (:alpha node) (aget hs (aget src j)) ksc (* j sc) sc))
    out))

(defn- children-sum!
  "out[c] = sum over children of (values child)[c]"
  [^doubles out ^ints ch ^objects values]
  (java.util.Arrays/fill out 0.0)
  (dotimes [i (alength ch)]
    (add-into! out (aget values (aget ch i))))
  out)

(defn- histories [model]
  (object-array (map :h (:nodes model))))

(defn- predict!
  "Fills :lam-own, :lam-ch and :lam of every node, leaves first."
  [model ^objects hs]
  (let [cfg (:config model)
        sc (* (scales cfg) C)
        max-growth (double (:max-growth cfg))
        nodes (:nodes model)
        lams (object-array (map :lam nodes))]
    (doseq [node nodes]
      (own-rate! node hs (:lam-own node) sc)
      (let [^ints ch (:children node)
            leaf? (zero? (alength ch))]
        (when-not leaf? (children-sum! (:chsum node) ch lams))
        (blend! (:lam node) (:lam-own node) (:lam-ch node) (:chsum node) (:kappa node)
                (:loss-own node) (:loss-ch node) (:ybar node) max-growth leaf?)))))

;; --- learning ----------------------------------------------------------------------------

(def ^:private zeros (double-array C))

(defn refresh!
  "Recomputes every node's mu and alpha from its sufficient statistics and
  the running means as they now stand: the M step on its own. `observe!` ends
  with it and a restored model starts with it, so neither is stored."
  [model]
  (let [cfg (:config model)
        nodes (:nodes model)
        sc (* (scales cfg) C)
        w (double (:prior-weight cfg))
        share (double (get-in cfg [:share :mu]))]
    (doseq [node nodes]
      (let [^ints src (:src node)
            k (alength src)]
        (refresh-mu! (:mu node) (:smu node) (:tmu node) (:ybar node) w share)
        (dotimes [j k]
          (refresh-alpha! (:alpha node) (:sa node) (:ta node) (:prior node) (:ybar node)
                          (:ybar (nodes (aget src j))) (* k sc) (* j sc) sc w))))
    model))

(defn observe!
  "Feeds one bin of observations to the model: `obs` maps node id to a double
  array of normalized values per channel; a node absent from it saw nothing.
  Predicts, scores the prediction, learns, then advances every history.
  Returns the model (whose arrays have changed)."
  [model bin obs]
  (let [cfg (:config model)
        nodes (:nodes model)
        index (:index model)
        n (count nodes)
        sc (* (scales cfg) C)
        ys (object-array n)
        _ (do (doseq [[id arr] obs]
                (when-let [i (index id)] (aset ys i arr)))
              (dotimes [i n] (when (nil? (aget ys i)) (aset ys i zeros))))
        hs (histories model)
        rho (double (:forget cfg))
        w (double (:prior-weight cfg))
        mu-share (double (get-in cfg [:share :mu]))
        rb (double (:blend-forget cfg))
        eta (double (:disp-rate cfg))
        decays (double-array (:decays cfg))
        chy (double-array C)]
    (predict! model hs)
    (let [[se seb]
          (loop [i 0 se 0.0 seb 0.0]
            (if (< i n)
              (let [node (nodes i)
                    y (aget ys i)]
                (recur (inc i)
                       (+ se (sq-log-err y (:lam node)))
                       (+ seb (sq-log-err y (:last node)))))
              [se seb]))]
      (dotimes [i n]
        (let [node (nodes i)
              ^doubles y (aget ys i)
              ^ints src (:src node)
              k (alength src)
              ^ints ch (:children node)
              has-children? (pos? (alength ch))]
          (ratios! (:r node) y (:lam-own node))
          (learn-baseline! (:mu node) (:smu node) (:tmu node) (:r node) (:ybar node) rho w mu-share)
          (dotimes [j k]
            (let [s (aget src j)]
              (learn-source! (:alpha node) (:sa node) (:ta node) (:prior node)
                             (aget hs s) (:r node) (:ybar node) (:ybar (nodes s))
                             (* k sc) (* j sc) sc rho w)))
          (when has-children? (children-sum! chy ch ys))
          (track! y chy (:ybar node) (:disp node) (:lam node) (:lam-own node) (:lam-ch node)
                  (:loss-own node) (:loss-ch node) (:kappa node) (:sk node) (:tk node)
                  rho rb eta w has-children?)))
      (dotimes [i n]
        (let [node (nodes i)]
          (push-history! (:h node) (aget ys i) decays)
          (System/arraycopy (aget ys i) 0 (:last node) 0 C)))
      (refresh! model)
      (-> model
          (assoc :bin bin)
          (update :stats #(-> %
                              (update :bins inc)
                              (update :sq-log-err + se)
                              (update :sq-log-err-base + seb)
                              (update :points + (* n C))))))))

;; --- forecasting ----------------------------------------------------------------------------

(defn- drift!
  "Advances a node's level drift, a random walk in log space with step sd
  `step`, and returns nothing; `level` holds the walk per channel."
  [^longs g ^doubles level ^double step]
  (dotimes [c C]
    (aset level c (+ (aget level c) (* step (m/normal! g))))))

(defn- draw-node!
  "This bin's simulated values for one node: draws around its own-level
  rate shifted by its level drift, blended for a parent with its children's
  drawn values."
  [^longs g ^doubles y ^doubles own ^doubles level ^double half-var ^doubles disp
   ^doubles chsum ^doubles kappa ^doubles lo ^doubles lc ^doubles ybar
   ^double max-growth leaf?]
  (dotimes [c C]
    (let [rate (Math/min (* (Math/max 0.0 (aget own c)) (Math/exp (- (aget level c) half-var)))
                         (* max-growth (+ 1.0 (aget ybar c))))
          d (m/overdispersed! g rate (aget disp c))]
      (aset y c
            (if leaf?
              d
              (let [l1 (aget lo c) l2 (aget lc c)
                    w (/ l2 (+ l1 l2 1e-12))]
                (+ (* w d) (* (- 1.0 w) (aget kappa c) (aget chsum c)))))))))

(defn- excitation-shares!
  "out[c] = the share of the node's typical rate in channel c its sources
  account for at their typical rates: its branching ratio."
  [^doubles out ^doubles alpha ^objects src-ybars ^doubles ybar ^long k ^long sc]
  (dotimes [c C]
    (let [total (loop [j 0 acc 0.0]
                  (if (< j k)
                    (let [^doubles sy (aget src-ybars j)
                          base (+ (* c k sc) (* j sc))]
                      (recur (inc j)
                             (loop [q 0 acc acc]
                               (if (< q sc)
                                 (recur (inc q) (+ acc (* (aget alpha (+ base q)) (aget sy (rem q C)))))
                                 acc))))
                    acc))]
      (aset out c (/ total (Math/max 1e-9 (aget ybar c)))))))

(defn- damp!
  "Scales channel c's alphas by f[c] and moves what they lose, at typical
  rates, into the baseline."
  [^doubles alpha ^doubles mu ^doubles shares ^doubles ybar ^long k ^long sc ^double cap]
  (dotimes [c C]
    (let [t (aget shares c)]
      (when (> t cap)
        (let [f (/ cap t)
              off (* c k sc)]
          (dotimes [i (* k sc)]
            (aset alpha (+ off i) (* f (aget alpha (+ off i)))))
          (aset mu c (+ (aget mu c) (* (- t cap) (aget ybar c)))))))))

(defn stable-params
  "The model with every node's excitation projected to a branching ratio of
  at most :max-branching, the excess moved into the baseline so each node's
  expected rate at typical levels is unchanged. Online EM fits one bin ahead
  and is happy near or past criticality (the data trend); simulated forward a
  year, a network whose branching ratios exceed one explodes, and a child
  that excites its parent which excites the child compounds. Below one, the
  spectral radius of the whole network is too, so forecasts stay bounded.
  Copies the parameters it changes; `model` is untouched."
  [model]
  (let [cfg (:config model)
        sc (* (scales cfg) C)
        cap (double (:max-branching cfg))
        nodes (:nodes model)
        shares (double-array C)]
    (assoc model :nodes
           (mapv (fn [node]
                   (let [k (count (:sources node))
                         ^ints src (:src node)
                         alpha (aclone ^doubles (:alpha node))
                         mu (aclone ^doubles (:mu node))]
                     (excitation-shares! shares alpha
                                         (object-array (map #(:ybar (nodes (aget src %))) (range k)))
                                         (:ybar node) k sc)
                     (damp! alpha mu shares (:ybar node) k sc cap)
                     (assoc node :alpha alpha :mu mu)))
                 nodes))))

(defn- record-path! [^doubles p ^long off ^doubles y]
  (System/arraycopy y 0 p off C))

(defn forecast
  "Simulates `horizon` bins ahead `sims` times from the model's current state,
  without changing it. Returns {:ids [...] :horizon h :sims s :paths p} where
  (aget p node-index) is a double array of sims × horizon × channels values,
  laid out [sim][bin][channel]. Parents are drawn from their children, so the
  higher levels are built from the lower ones."
  [model {:keys [horizon sims seed] :or {horizon 52 sims 50 seed 1}}]
  (let [model (stable-params model)
        cfg (:config model)
        nodes (:nodes model)
        n (count nodes)
        sc (* (scales cfg) C)
        g (m/rng seed)
        decays (double-array (:decays cfg))
        max-growth (double (:max-growth cfg))
        paths (object-array n)
        stride (* horizon C)
        own (double-array C)
        chsum (double-array C)
        ;; per-bin step of the level walk, so its sd after 52 bins is :level-noise
        step (/ (double (:level-noise cfg)) (Math/sqrt 52.0))]
    (dotimes [i n] (aset paths i (double-array (* sims stride))))
    (dotimes [s sims]
      (let [hs (object-array (map #(aclone ^doubles (:h %)) nodes))
            ys (object-array (repeatedly n #(double-array C)))
            levels (object-array (repeatedly n #(double-array C)))]
        (dotimes [t horizon]
          ;; the walk's variance after t+1 steps; exp(level - var/2) has mean one
          (let [half-var (* 0.5 step step (inc t))]
            (dotimes [i n]
              (let [node (nodes i)
                    ^ints ch (:children node)
                    leaf? (zero? (alength ch))]
                (own-rate! node hs own sc)
                (when-not leaf? (children-sum! chsum ch ys))
                (drift! g (aget levels i) step)
                (draw-node! g (aget ys i) own (aget levels i) half-var (:disp node) chsum
                            (:kappa node) (:loss-own node) (:loss-ch node) (:ybar node)
                            max-growth leaf?))))
          (dotimes [i n]
            (record-path! (aget paths i) (+ (* s stride) (* t C)) (aget ys i))
            (push-history! (aget hs i) (aget ys i) decays)))))
    {:ids (mapv :id nodes) :horizon horizon :sims sims :paths paths}))

(defn path-sum
  "The total of node `i`'s channel `c` over bins [from, to) of simulation `s`."
  ^double [fc i s c from to]
  (let [^doubles p (aget ^objects (:paths fc) i)
        stride (* (:horizon fc) C)]
    (loop [t from acc 0.0]
      (if (< t to)
        (recur (inc t) (+ acc (aget p (+ (* s stride) (* t C) c))))
        acc))))

;; --- inspection -------------------------------------------------------------------------

(defn node [model id] (get (:nodes model) ((:index model) id)))

(defn node-connections
  "The strongest influences on node `nd`, channel `c` (an index): a seq of
  {:source :kind :scale :channel :alpha :share} sorted by share, where share
  is the part of the node's typical rate the source accounts for, and scale
  is :fast or :slow. `ybar-of` gives a source's running means by id."
  [cfg nd ybar-of c]
  (let [S (scales cfg)
        sc (* S C)
        k (count (:sources nd))
        ^doubles alpha (:alpha nd)
        ybar (aget ^doubles (:ybar nd) c)]
    (->> (for [j (range k) s (range S) c2 (range C)
               :let [a (aget alpha (+ (* c k sc) (* j sc) (* s C) c2))
                     sbar (aget ^doubles (ybar-of ((:sources nd) j)) c2)]]
           {:source ((:sources nd) j) :kind ((:kinds nd) j)
            :scale (get [:fast :slow] s s)
            :channel (ont/channels c2) :alpha a
            :share (/ (* a sbar) (Math/max 1e-9 ybar))})
         (sort-by (comp - :share)))))

(defn connections
  "`node-connections` of node `id` in `model`."
  [model id c]
  (node-connections (:config model) (node model id) #(:ybar (node model %)) c))

(defn standalone-node
  "A fresh node outside any model, for reading one stored node on its own:
  `structure-node` as `bnm.structure/build` gives it, with exactly `sources`."
  [cfg structure-node sources]
  (new-node structure-node (zipmap sources (range)) (merge-config cfg)))

(defn refresh-node!
  "`refresh!` for one standalone node, given its sources' running means in
  the order of its sources."
  [cfg node source-ybars]
  (let [cfg (merge-config cfg)
        sc (* (scales cfg) C)
        k (count (:sources node))
        w (double (:prior-weight cfg))]
    (refresh-mu! (:mu node) (:smu node) (:tmu node) (:ybar node) w (double (get-in cfg [:share :mu])))
    (dotimes [j k]
      (refresh-alpha! (:alpha node) (:sa node) (:ta node) (:prior node) (:ybar node)
                      (nth source-ybars j) (* k sc) (* j sc) sc w))
    node))

(defn skill
  "Mean squared log error of one-bin-ahead predictions, the model's and the
  persistence baseline's (next bin = this bin), over everything observed."
  [model]
  (let [{:keys [sq-log-err sq-log-err-base points]} (:stats model)]
    (when (pos? points)
      {:model (/ sq-log-err points)
       :persistence (/ sq-log-err-base points)})))

;; --- growing and reshaping ---------------------------------------------------------------

(def ^:private carried
  [:h :last :ybar :mu :smu :tmu :kappa :sk :tk :loss-own :loss-ch :disp])

(defn restructure
  "A model over `structure` that keeps everything `old` learned about the
  nodes and connections the two share. New nodes start fresh; a connection
  that survives keeps its statistics, new ones start from the prior. Used as
  the world changes: dyads that become active get leaves of their own, and
  neighbours are re-ranked."
  [old structure]
  (let [mdl (create structure (:config old))
        sc (* (scales (:config old)) C)
        old-index (:index old)]
    (doseq [node (:nodes mdl)
            :let [prev (some->> (old-index (:id node)) ((:nodes old)))]
            :when prev]
      (doseq [k carried]
        (let [^doubles from (get prev k)]
          (System/arraycopy from 0 (get node k) 0 (alength from))))
      (let [k-new (count (:sources node))
            k-old (count (:sources prev))
            old-j (zipmap (:sources prev) (range))]
        (dotimes [j k-new]
          (when-let [j0 (old-j ((:sources node) j))]
            (doseq [arr [:alpha :sa :ta]]
              (dotimes [c C]
                (System/arraycopy (get prev arr) (+ (* c k-old sc) (* j0 sc))
                                  (get node arr) (+ (* c k-new sc) (* j sc)) sc)))))))
    (refresh! (merge mdl (select-keys old [:bin :exposure :stats :volumes :calibration])))))
