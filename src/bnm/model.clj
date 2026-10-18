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

  Priors are scale-free and centred: the baseline's prior is a fixed share
  of the node's typical rate, and the sources share the rest in proportion
  to the shares for their kind, so a node's prior mean rate is its typical
  rate whatever its number of sources, and the same prior works for a quiet
  dyad and the world.

  Values are normalized counts: events per `scale` events the source reported
  in a typical bin (see `bnm.series`), so sources of very different volume, and
  a source whose coverage grows, give comparable numbers.

  The model is a map of plain values and primitive arrays. `observe!` and
  `forecast` mutate or read it; `bnm.store` turns it into EDN for a database.
  The arithmetic lives in small functions over ^doubles parameters, which is
  what lets jolt compile it to unboxed flonum code."
  (:require [bnm.calibrate :as cal]
            [bnm.geo :as geo]
            [bnm.math :as m]
            [bnm.ontology :as ont]))

(set! *unchecked-math* true)

(def ^:const C 6)
(assert (= C ont/n-channels))

(def defaults
  {:bin-days 7
   :decays [0.6 0.97]  ; history kernels, fast and slow: h <- d*h + (1-d)*y
   :forget 0.99        ; per-bin discount of sufficient statistics (~2 years)
   :level-discount 0.95 ; per-bin discount of the baseline's statistics (~20 bins):
                        ; the baseline is a local level (West & Harrison's
                        ; discount), excitation the excursions around it
   :prior-weight 8.0   ; prior strength, in bins of evidence
   ;; prior share of a node's mean rate: :mu for the baseline; the rest is
   ;; split among the sources in proportion to these weights (see new-node),
   ;; and over the time scales by :scale-split
   ;; the baseline takes most of the prior: with no clustering in the data the
   ;; slow kernel and the baseline cannot be told apart, and the split then
   ;; follows the prior; excitation has to be earned by bursts the fast kernel
   ;; sees (Filimonov & Sornette 2015; docs/RESEARCH.md)
   :share {:mu 0.8
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
   :param-uncertainty true ; forecasts draw parameters from their posterior
   :pooling true       ; priors learned across nodes of a kind (see learn-pool)
   :shock nil          ; in a forecast, {:sd :volume}: one log-normal multiplier
                       ; per path, node and channel, held over the horizon, its
                       ; log-sd :sd for a node far quieter than :volume raw events
                       ; a bin and fading for busier ones (see `shock-sd`)
   :level-noise 0.0    ; in a forecast, the log-sd a node's level drifts by over
                       ; 52 bins beyond what its dynamics explain: shocks the
                       ; model has no way to see coming
   :bottom-up #{:country} ; kinds whose forecast is their children's summed
                          ; (bnm.reconcile: lower CRPS than their own blend)
   :spread-mode :coverage ; how forecasts are recalibrated (bnm.calibrate):
                          ; :coverage widens toward 80% of outcomes in the 80%
                          ; range; :symmetric from the log errors, which feeds
                          ; on itself and grew to 0.95 coverage over 2000-26
                          ; (v4-q); :sides each side by its own factor
   :scale 1000.0})

(defn scales ^long [cfg] (count (:decays cfg)))

;; --- parallelism ---------------------------------------------------------------------------

(def ^:dynamic *workers*
  "Threads a model spreads its work over: the machine's processors."
  (.availableProcessors (Runtime/getRuntime)))

(defn- in-parallel!
  "Calls (f from to) on up to `parts` contiguous slices of [0, n), each on a
  thread of its own, and waits for them all."
  [n parts f]
  (let [parts (max 1 (min parts n))
        step (quot (+ n parts -1) parts)]
    (if (= parts 1)
      (f 0 n)
      (run! deref (mapv (fn [p] (future (f (* p step) (min n (* (inc p) step)))))
                        (range parts))))
    nil))

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
  "The E step for one source's block of alpha: its sufficient statistics,
  discounted by `rho`. r[c] is observed/expected for the node's channel c.
  sa has a block per channel; ta, which does not depend on the channel, one
  per source. `refresh!` makes alpha from them."
  [^doubles alpha ^doubles sa ^doubles ta ^doubles h ^doubles r
   ^long ksc ^long jsc ^long sc ^double rho]
  (dotimes [q sc]
    (let [i (+ jsc q)]
      (aset ta i (+ (* rho (aget ta i)) (aget h q)))))
  (dotimes [c C]
    (let [base (+ (* c ksc) jsc)
          rc (aget r c)]
      (dotimes [q sc]
        (let [i (+ base q)]
          (aset sa i (+ (* rho (aget sa i)) (* rc (aget alpha i) (aget h q)))))))))

(defn- learn-baseline!
  "The E step for mu: its sufficient statistics. `refresh!` makes mu."
  [^doubles mu ^doubles smu ^doubles tmu ^doubles r ^double rho]
  (dotimes [c C]
    (aset smu c (+ (* rho (aget smu c)) (* (aget r c) (aget mu c))))
    (aset tmu c (+ (* rho (aget tmu c)) 1.0))))

(defn- refresh-alpha!
  "The M step for one source's block of alpha, from its statistics and the
  current running means: the posterior mean under its Gamma prior. A quiet
  source is priced as if at a tenth of the node's own rate, so its alpha
  stays bounded and does not explode when it stirs."
  [^doubles alpha ^doubles sa ^doubles ta ^doubles prior ^doubles ybar ^doubles sybar
   ^long ksc ^long jsc ^long sc ^double w]
  (dotimes [c C]
    (let [base (+ (* c ksc) jsc)
          yb (aget ybar c)
          floor (+ 1e-6 (* 0.1 yb))]
      (dotimes [q sc]
        (let [i (+ base q)
              sb (Math/max (aget sybar (rem q C)) floor)]
          (aset alpha i (/ (+ (* (aget prior i) yb w) (aget sa i))
                           (+ (* w sb) (aget ta (+ jsc q))))))))))

(defn- refresh-mu!
  "The M step for mu: the posterior mean under a Gamma prior centred on the
  baseline's share of `level`, the node's local level (its fast history),
  so that a baseline whose statistics forget quickly is not pulled back to a
  long-run mean after the level moves."
  [^doubles mu ^doubles smu ^doubles tmu ^doubles level ^double w ^doubles share]
  (dotimes [c C]
    (aset mu c (/ (+ (* (aget share c) (aget level c) w) (aget smu c)) (+ w (aget tmu c))))))

(defn- local-level
  "The node's fast history, per channel: its local level. (The slow history
  remembers a level for most of a year after it moves, and pulls the
  baseline back with it.)"
  ^doubles [node ^long S]
  (let [out (double-array C)
        ^doubles h (:h node)
        off 0]
    (dotimes [c C] (aset out c (aget h (+ off c))))
    out))

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
   ^double rho ^double rb ^double eta ^double w ^double u has-children?]
  (dotimes [c C]
    (let [yc (aget y c)
          l (Math/max 1e-6 (aget lam c))
          ly (Math/log1p yc)
          dev (- yc l)]
      (aset ybar c (+ (* rho (aget ybar c)) (* (- 1.0 rho) yc)))
      ;; quasi-Poisson: variance over mean, in normalized units; at least u,
      ;; which is Poisson in raw counts
      (aset disp c (Math/max u (+ (* (- 1.0 eta) (aget disp c))
                                  (* eta (/ (* dev dev) (Math/max u l))))))
      (when has-children?
        (let [eo (- ly (Math/log1p (aget own c)))
              ec (- ly (Math/log1p (aget lch c)))]
          (aset lo c (+ (* rb (aget lo c)) (* eo eo)))
          (aset lc c (+ (* rb (aget lc c)) (* ec ec))))
        (aset sk c (+ (* rho (aget sk c)) yc))
        (aset tk c (+ (* rho (aget tk c)) (aget chy c)))
        ;; the prior's w pseudo-events at ratio one are raw events (u each):
        ;; in normalized units a small node's sums never outweighed it
        (aset kappa c (/ (+ (* w u) (aget sk c)) (+ (* w u) (aget tk c))))))))

;; --- nodes ------------------------------------------------------------------------------

(defn- kind-shares [cfg kind]
  (let [s (:share cfg)]
    (case kind
      :self [(:self-same s) (:self-cross s)]
      :parent [(:parent-same s) (:parent-cross s)]
      :neighbor [(:neighbor-same s) (:neighbor-cross s)])))

(defn- default-share
  "The hand-set prior share of an entry: source kind `kind`, time scale `s`,
  target channel `c`, source channel `c2`."
  ^double [cfg kind ^long s ^long c ^long c2]
  (let [[same cross] (kind-shares cfg kind)]
    (double (* (nth (:scale-split cfg) s) (if (= c c2) same cross)))))

(defn- pooled
  "The pool's value at `i` for group `k`, or nil."
  [pool k ^long i]
  (when-let [^doubles a (get-in pool [:shares k])] (aget a i)))

(defn- fill-prior!
  "Sets a node's prior: per target channel, its baseline's share (:mu-share)
  and its sources' shares, taken from `pool` (learned across nodes of the
  same kind, see `learn-pool`) or, failing that, from the config, then
  centred so the baseline and every source together account for exactly
  the node's typical rate, whatever its number of sources."
  [node cfg pool]
  (let [kind (geo/kind (:id node))
        kinds (:kinds node)
        k (count kinds)
        S (scales cfg)
        sc (* S C)
        ^doubles prior (:prior node)
        ^doubles mu-share (:mu-share node)]
    (dotimes [c C]
      (aset mu-share c (double (or (some-> (get-in pool [:mu kind]) (nth c))
                                   (get-in cfg [:share :mu]))))
      (dotimes [j k]
        (let [src-kind (kinds j)]
          (dotimes [s S]
            (dotimes [c2 C]
              (aset prior (+ (* c k sc) (* j sc) (* s C) c2)
                    (double (or (pooled pool [kind src-kind] (+ (* c sc) (* s C) c2))
                                (default-share cfg src-kind s c c2))))))))
      (let [row (* c k sc)
            total (loop [i 0 acc 0.0] (if (< i (* k sc)) (recur (inc i) (+ acc (aget prior (+ row i)))) acc))
            f (/ (- 1.0 (aget mu-share c)) (Math/max 1e-12 total))]
        (dotimes [i (* k sc)] (aset prior (+ row i) (* f (aget prior (+ row i)))))))
    node))

(defn- new-node [n index cfg pool]
  (let [srcs (vec (concat [[(:id n) :self]]
                          (map vector (:parents n) (repeat :parent))
                          (map vector (:neighbors n) (repeat :neighbor))))
        srcs (filterv (comp index first) srcs)
        k (count srcs)
        S (scales cfg)
        sc (* S C)
        prior (double-array (* C k sc))]
    (fill-prior!
     {:id (:id n)
      :level (:level n)
      :sources (mapv first srcs)
      :kinds (mapv second srcs)
      :src (int-array (map (comp index first) srcs))
      :children (int-array (keep index (:children n)))
      :prior prior
      :mu-share (double-array C)
      :h (double-array sc) :last (double-array C) :ybar (double-array C)
      :mu (double-array C) :smu (double-array C) :tmu (double-array C)
      :alpha (double-array (* C k sc)) :sa (double-array (* C k sc)) :ta (double-array (* k sc))
      :kappa (double-array C 1.0) :sk (double-array C) :tk (double-array C)
      :loss-own (double-array C 1.0) :loss-ch (double-array C 1.0)
      :disp (double-array C)
      :lam (double-array C) :lam-own (double-array C) :lam-ch (double-array C)
      :r (double-array C) :chsum (double-array C)}
     cfg pool)))

(defn- merge-config [config]
  (merge-with (fn [a b] (if (map? a) (merge a b) b)) defaults config))

(defn create
  "A fresh model over `structure` (see `bnm.structure/build`), its priors
  from `pool` (see `learn-pool`) where given."
  ([structure] (create structure {}))
  ([structure config] (create structure config nil))
  ([structure config pool]
   (let [cfg (merge-config config)
         ids (mapv :id (:nodes structure))
         index (zipmap ids (range))]
     {:config cfg
      :structure structure
      :index index
      :pool pool
      :nodes (mapv #(new-node % index cfg pool) (:nodes structure))
      :bin nil
      :exposure {}
      :stats {:bins 0 :sq-log-err 0.0 :sq-log-err-base 0.0 :points 0.0}})))

;; --- prediction -------------------------------------------------------------------------

(defn unit
  "What one raw event is worth in the model's normalized values: `:scale`
  over the sources' exposures (see `bnm.series`), or 1 before any."
  ^double [model]
  (let [e (reduce + 0.0 (vals (:exposure model)))]
    (if (pos? e) (/ (double (get-in model [:config :scale])) e) 1.0)))

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

(defn- own-rate-drawn!
  "`own-rate!` under drawn parameters: mu[c] scaled by g[c], and source j's
  block of alpha for channel c by g[C + c*k + j]."
  [node ^objects hs ^doubles out ^long sc ^doubles g]
  (let [^ints src (:src node)
        ^doubles mu (:mu node)
        ^doubles alpha (:alpha node)
        k (alength src)
        ksc (* k sc)]
    (dotimes [c C] (aset out c (* (aget mu c) (aget g c))))
    (dotimes [j k]
      (let [^doubles h (aget hs (aget src j))]
        (dotimes [c C]
          (aset out c (+ (aget out c)
                         (* (aget g (+ C (* c k) j))
                            (dot-off alpha (+ (* c ksc) (* j sc)) h sc)))))))
    out))

(defn- posterior-shapes
  "Effective Gamma shapes of a node's posterior, for drawing its parameters
  in a forecast: per channel for mu, then per channel and source for each
  block of alpha. A shape is the evidence behind the estimate, prior and
  data, in the quasi-Poisson units of the node's dispersion (counts over
  dispersion), which makes it independent of how values are normalized."
  ^doubles [node ^long sc ^double w ^double u]
  (let [^ints src (:src node)
        k (alength src)
        ksc (* k sc)
        ^doubles prior (:prior node)
        ^doubles sa (:sa node)
        ^doubles ybar (:ybar node)
        ^doubles smu (:smu node)
        ^doubles disp (:disp node)
        ^doubles mu-share (:mu-share node)
        out (double-array (+ C (* C k)))]
    (dotimes [c C]
      (let [yb (aget ybar c)
            d (Math/max u (aget disp c))]
        (aset out c (/ (+ (* (aget mu-share c) yb w) (aget smu c)) d))
        (dotimes [j k]
          (let [base (+ (* c ksc) (* j sc))]
            (aset out (+ C (* c k) j)
                  (/ (loop [q 0 acc 0.0]
                       (if (< q sc)
                         (recur (inc q) (+ acc (* (aget prior (+ base q)) yb w) (aget sa (+ base q))))
                         acc))
                     d))))))
    out))

(defn- draw-factors!
  "Fills `out` with mean-one Gamma draws of `shapes`: each parameter's
  posterior relative to its estimate. No evidence at all leaves it as is."
  [^doubles rng ^doubles out ^doubles shapes]
  (dotimes [i (alength shapes)]
    (let [a (aget shapes i)]
      (aset out i (if (< a 1e-9) 1.0 (m/gamma! rng a (/ 1.0 a)))))))

(defn- block-shares
  "Per channel and source, the share of the node's typical rate that
  source's block of alpha accounts for at its typical rates, laid out like
  the alpha factors of `posterior-shapes` (c*k + j): what a drawn factor
  adds to the node's branching ratio."
  ^doubles [node src-ybars ^long sc]
  (let [k (count (:sources node))
        ^doubles alpha (:alpha node)
        ^doubles ybar (:ybar node)
        out (double-array (* C k))]
    (dotimes [c C]
      (dotimes [j k]
        (let [^doubles sy (nth src-ybars j)
              base (+ (* c k sc) (* j sc))]
          (aset out (+ (* c k) j)
                (/ (loop [q 0 acc 0.0]
                     (if (< q sc)
                       (recur (inc q) (+ acc (* (aget alpha (+ base q)) (aget sy (rem q C)))))
                       acc))
                   (Math/max 1e-9 (aget ybar c)))))))
    out))

(defn- cap-factors!
  "Scales a node's drawn alpha factors down, per channel, so the branching
  ratio they give is at most `cap`, and moves what they lose, at typical
  rates, into its drawn baseline: the projection `stable-params` makes of
  the estimates, made of each draw. Without it a draw past one explodes and
  the forecast mean is dominated by the few that do; without the move into
  the baseline, capping only ever lowers the rate, and the draws drag the
  forecast down."
  [^doubles f ^doubles bs ^doubles mu ^doubles ybar ^long k ^double cap]
  (dotimes [c C]
    (let [n (loop [j 0 acc 0.0]
              (if (< j k)
                (recur (inc j) (+ acc (* (aget f (+ C (* c k) j)) (aget bs (+ (* c k) j)))))
                acc))]
      (when (> n cap)
        (let [s (/ cap n)]
          (dotimes [j k]
            (let [i (+ C (* c k) j)]
              (aset f i (* s (aget f i)))))
          (aset f c (+ (aget f c) (/ (* (- n cap) (aget ybar c)) (Math/max 1e-12 (aget mu c))))))))))

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
    ;; own rates depend only on the histories; the blend needs children first
    (in-parallel! (count nodes) *workers*
                  (fn [a b] (doseq [i (range a b)]
                              (let [node (nodes i)] (own-rate! node hs (:lam-own node) sc)))))
    (doseq [node nodes]
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
        w (double (:prior-weight cfg))]
    (in-parallel!
     (count nodes) *workers*
     (fn [a b]
       (doseq [i (range a b)]
         (let [node (nodes i)
               ^ints src (:src node)
               k (alength src)]
           (refresh-mu! (:mu node) (:smu node) (:tmu node) (local-level node (scales cfg)) w (:mu-share node))
           (dotimes [j k]
             (refresh-alpha! (:alpha node) (:sa node) (:ta node) (:prior node) (:ybar node)
                             (:ybar (nodes (aget src j))) (* k sc) (* j sc) sc w))))))
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
        level-rho (double (or (:level-discount cfg) (:forget cfg)))
        w (double (:prior-weight cfg))
        rb (double (:blend-forget cfg))
        eta (double (:disp-rate cfg))
        u (unit model)
        decays (double-array (:decays cfg))]
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
      ;; each node learns from fixed inputs into its own arrays, so nodes run
      ;; in parallel; the M step that needs other nodes' means is refresh!'s
      (in-parallel!
       n *workers*
       (fn [a b]
         (let [chy (double-array C)]
           (doseq [i (range a b)]
             (let [node (nodes i)
                   ^doubles y (aget ys i)
                   ^ints src (:src node)
                   k (alength src)
                   ^ints ch (:children node)
                   has-children? (pos? (alength ch))]
               (ratios! (:r node) y (:lam-own node))
               (learn-baseline! (:mu node) (:smu node) (:tmu node) (:r node) level-rho)
               (dotimes [j k]
                 (learn-source! (:alpha node) (:sa node) (:ta node) (aget hs (aget src j)) (:r node)
                                (* k sc) (* j sc) sc rho))
               (when has-children? (children-sum! chy ch ys))
               (track! y chy (:ybar node) (:disp node) (:lam node) (:lam-own node) (:lam-ch node)
                       (:loss-own node) (:loss-ch node) (:kappa node) (:sk node) (:tk node)
                       rho rb eta w u has-children?))))))
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
  [^doubles g ^doubles level ^double step]
  (dotimes [c C]
    (aset level c (+ (aget level c) (* step (m/normal! g))))))

(defn- draw-node!
  "This bin's simulated values for one node: draws around its own-level
  rate shifted by its level drift, blended for a parent with its children's
  drawn values."
  [^doubles g ^doubles y ^doubles own ^doubles level ^double half-var ^doubles disp
   ^doubles chsum ^doubles kappa ^doubles lo ^doubles lc ^doubles ybar
   ^double max-growth ^double u leaf?]
  (dotimes [c C]
    (let [rate (Math/min (* (Math/max 0.0 (aget own c)) (Math/exp (- (aget level c) half-var)))
                         (* max-growth (+ 1.0 (aget ybar c))))
          ;; whole events: a negative binomial count at the raw scale, whose
          ;; variance is the node's dispersion in raw units times its mean
          d (* u (m/neg-binomial! g (/ rate u) (/ (Math/max u (aget disp c)) u)))]
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

(def forecast-chunks
  "How many independent streams a forecast's simulations are split over (and
  so how many threads it can use)."
  8)

(defn- record-path! [^doubles p ^long off ^doubles y]
  (System/arraycopy y 0 p off C))

(defn- add-total! [^doubles p ^long off ^doubles y]
  (dotimes [c C] (aset p (+ off c) (+ (aget p (+ off c)) (aget y c)))))

(defn shock-sd
  "The log-sd of a node's persistent forecast shock: `s` for a node far
  quieter than `v0` raw events a bin, fading as sqrt(v0 / (v0 + raw)) for
  busier ones. Gamma mixing (the local level, the negative binomial draws)
  makes a sparse node's log total left-skewed, its upper tail too short; a
  log-normal shock is symmetric in log space, so it lengthens that tail where
  series are sparse and leaves busy ones alone (Berry & West 2020's random
  effect, held over the horizon, with a variance pooled by volume)."
  ^double [^double s ^double v0 ^double raw]
  (* s (Math/sqrt (/ v0 (+ v0 (Math/max 0.0 raw))))))

(defn- draw-shocks!
  "Fills `out` (channels) with mean-one log-normal multipliers of log-sds `sds`."
  [g ^doubles out ^doubles sds]
  (dotimes [c C]
    (let [sd (aget sds c)]
      (aset out c (if (pos? sd) (Math/exp (- (* sd (m/normal! g)) (* 0.5 sd sd))) 1.0)))))

(defn forecast
  "Simulates `horizon` bins ahead `sims` times from the model's current state,
  without changing it. Returns {:ids [...] :horizon h :sims s :totals t} where
  (aget t node-index) is a double array of sims × channels totals over the
  horizon, laid out [sim][channel]; read it with `total`. With :paths? true it
  also keeps every bin, as :paths of sims × horizon × channels values laid out
  [sim][bin][channel] (read with `path-sum`), which takes horizon times the
  memory; :keep-paths, a set of node indices, keeps them for those nodes only.
  Parents are drawn from their children, so the higher levels are built from
  the lower ones.

  Each simulation draws the model's parameters from their posterior (see
  `posterior-shapes`) before it runs, so the spread of the forecast carries
  what the model doesn't know about itself as well as the world's noise;
  :param-uncertainty false in the config keeps the point estimates."
  [model {:keys [horizon sims seed paths? keep-paths] :or {horizon 52 sims 50 seed 1}}]
  (let [model (stable-params model)
        cfg (:config model)
        nodes (:nodes model)
        n (count nodes)
        sc (* (scales cfg) C)
        decays (double-array (:decays cfg))
        max-growth (double (:max-growth cfg))
        paths (when (or paths? keep-paths) (object-array n))
        totals (object-array n)
        stride (* horizon C)
        uncertain? (:param-uncertainty cfg true)
        u (unit model)
        shapes (when uncertain?
                 (let [w (double (:prior-weight cfg))]
                   (object-array (map #(posterior-shapes % sc w u) nodes))))
        cap (double (:max-branching cfg))
        bshares (when uncertain?
                  (object-array (map (fn [node]
                                       (block-shares node (mapv #(:ybar (nodes %)) (:src node)) sc))
                                     nodes)))
        ;; per-bin step of the level walk, so its sd after 52 bins is :level-noise
        step (/ (double (:level-noise cfg)) (Math/sqrt 52.0))
        ;; per node and channel, the log-sd of its persistent shock: its level
        ;; and channel's, as learned from the tails (bnm.calibrate/learn-shock),
        ;; faded by the node's volume
        shock-sds (when-let [{:keys [sd volume]} (:shock cfg)]
                    (object-array (map (fn [node]
                                         (let [^doubles yb (:ybar node) out (double-array C)]
                                           (dotimes [c C]
                                             (let [s (cal/shock-scale (:spread model) (geo/kind (:id node)) (ont/channels c) sd)]
                                               (aset out c (shock-sd (double s) (double volume) (/ (aget yb c) u)))))
                                           out))
                                       nodes)))]
    (dotimes [i n]
      (aset totals i (double-array (* sims C)))
      (when (or paths? (and keep-paths (contains? keep-paths i)))
        (aset paths i (double-array (* sims stride)))))
    ;; simulations run in a fixed number of chunks, each on its own random
    ;; stream, so the result depends on the seed and not on the machine
    (in-parallel!
     forecast-chunks forecast-chunks
     (fn [chunk _]
       (let [from (quot (* chunk sims) forecast-chunks)
             to (quot (* (inc chunk) sims) forecast-chunks)
             g (m/rng (+ (* 7919 (long seed)) chunk))
             ;; the shocks on a stream of their own, so the rest of a forecast is
             ;; the same with or without them
             gs (m/rng (+ (* 7919 (long seed)) chunk 104729))
             own (double-array C)
             chsum (double-array C)
             factors (when uncertain?
                       (object-array (map #(double-array (alength ^doubles %)) shapes)))]
         (doseq [s (range from to)]
           (when uncertain?
             (dotimes [i n]
               (draw-factors! g (aget factors i) (aget shapes i))
               (let [node (nodes i)]
                 (cap-factors! (aget factors i) (aget bshares i) (:mu node) (:ybar node)
                               (alength ^ints (:src node)) cap))))
           (let [hs (object-array (map #(aclone ^doubles (:h %)) nodes))
                 ys (object-array (repeatedly n #(double-array C)))
                 levels (object-array (repeatedly n #(double-array C)))
                 shocks (when shock-sds
                          (let [a (object-array n)]
                            (dotimes [i n]
                              (aset a i (doto (double-array C) (#(draw-shocks! gs % (aget shock-sds i))))))
                            a))]
             (dotimes [t horizon]
               ;; the walk's variance after t+1 steps; exp(level - var/2) has mean one
               (let [half-var (* 0.5 step step (inc t))]
                 (dotimes [i n]
                   (let [node (nodes i)
                         ^ints ch (:children node)
                         leaf? (zero? (alength ch))]
                     (if uncertain?
                       (own-rate-drawn! node hs own sc (aget factors i))
                       (own-rate! node hs own sc))
                     (when shocks
                       (let [^doubles sk (aget shocks i)]
                         (dotimes [c C] (aset own c (* (aget own c) (aget sk c))))))
                     (when-not leaf? (children-sum! chsum ch ys))
                     (when (pos? step) (drift! g (aget levels i) step))
                     (draw-node! g (aget ys i) own (aget levels i) half-var (:disp node) chsum
                                 (:kappa node) (:loss-own node) (:loss-ch node) (:ybar node)
                                 max-growth u leaf?))))
               (dotimes [i n]
                 (add-total! (aget totals i) (* s C) (aget ys i))
                 (when-let [p (and paths (aget paths i))]
                   (record-path! p (+ (* s stride) (* t C)) (aget ys i)))
                 (push-history! (aget hs i) (aget ys i) decays))))))))
    ;; coherent bottom-up: a node of a kind in :bottom-up forecasts the sum of its
    ;; children's simulated totals (and paths), which beat its own blend
    (let [kinds (set (:bottom-up cfg))]
      (doseq [i (range n)
              :let [node (nodes i)
                    ^ints ch (:children node)]
              :when (and (kinds (geo/kind (:id node))) (pos? (alength ch)))]
        (let [^doubles t (aget totals i)]
          (java.util.Arrays/fill t 0.0)
          (dotimes [j (alength ch)]
            (let [^doubles tc (aget totals (aget ch j))]
              (dotimes [x (alength t)] (aset t x (+ (aget t x) (aget tc x)))))))
        (when-let [^doubles p (and paths (aget paths i)
                                   (every? #(aget paths %) (seq ch)) (aget paths i))]
          (java.util.Arrays/fill p 0.0)
          (dotimes [j (alength ch)]
            (let [^doubles pc (aget paths (aget ch j))]
              (dotimes [x (alength p)] (aset p x (+ (aget p x) (aget pc x)))))))))
    (cond-> {:ids (mapv :id nodes) :horizon horizon :sims sims :totals totals}
      paths (assoc :paths paths))))

(defn total
  "The total of node `i`'s channel `c` over the whole horizon of simulation `s`."
  ^double [fc i s c]
  (aget ^doubles (aget ^objects (:totals fc) i) (+ (* s C) c)))

(defn path-sum
  "The total of node `i`'s channel `c` over bins [from, to) of simulation `s`,
  from a forecast made with :paths? true."
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
  `structure-node` as `bnm.structure/build` gives it, with exactly
  `sources`, its priors from `pool` where given."
  ([cfg structure-node sources] (standalone-node cfg nil structure-node sources))
  ([cfg pool structure-node sources]
   (new-node structure-node (zipmap sources (range)) (merge-config cfg) pool)))

(defn refresh-node!
  "`refresh!` for one standalone node, given its sources' running means in
  the order of its sources."
  [cfg node source-ybars]
  (let [cfg (merge-config cfg)
        sc (* (scales cfg) C)
        k (count (:sources node))
        w (double (:prior-weight cfg))]
    (refresh-mu! (:mu node) (:smu node) (:tmu node) (local-level node (scales cfg)) w (:mu-share node))
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

;; --- partial pooling --------------------------------------------------------------------

(def ^:private pool-prior-weight
  "Weight, in nodes' worth of evidence, of the hand-set shares in a pooled
  share: a kind with little evidence keeps close to them."
  1.0)

(defn learn-pool
  "The priors learned from every node's posterior (empirical Bayes): for
  each kind of node and channel, the share of its typical rate its baseline
  accounts for; for each kind of node, kind of source, time scale, target
  and source channel, the share a source's entry accounts for. Each node
  counts by how much of its estimate is data rather than prior, and the
  hand-set shares count as `pool-prior-weight` of evidence. A node with
  little evidence of its own then shrinks toward what nodes of its kind
  show, rather than toward hand-set numbers. Plain data:
  {:mu {kind [C shares]} :shares {[kind source-kind] double[C*S*C]}}."
  [model]
  (let [cfg (:config model)
        nodes (:nodes model)
        S (scales cfg)
        sc (* S C)
        w (double (:prior-weight cfg))
        mu-acc (java.util.HashMap.)
        sh-acc (java.util.HashMap.)
        acc! (fn [^java.util.HashMap m k n]
               (or (.get m k) (let [v [(double-array n) (double-array n)]] (.put m k v) v)))]
    (doseq [node nodes]
      (let [kind (geo/kind (:id node))
            ^doubles ybar (:ybar node)
            ^doubles mu (:mu node)
            ^doubles tmu (:tmu node)
            ^doubles alpha (:alpha node)
            ^doubles ta (:ta node)
            ^ints src (:src node)
            k (alength src)
            ksc (* k sc)
            [^doubles msum ^doubles mwt] (acc! mu-acc kind C)]
        (dotimes [c C]
          (let [yb (aget ybar c)]
            (when (> yb 1e-9)
              (let [wt (/ (aget tmu c) (+ (aget tmu c) w))]
                (aset msum c (+ (aget msum c) (* wt (/ (aget mu c) yb))))
                (aset mwt c (+ (aget mwt c) wt)))
              (let [floor (+ 1e-6 (* 0.1 yb))]
                (dotimes [j k]
                  (let [[^doubles ssum ^doubles swt] (acc! sh-acc [kind ((:kinds node) j)] (* C sc))
                        ^doubles sybar (:ybar (nodes (aget src j)))]
                    (dotimes [q sc]
                      (let [i (+ (* c ksc) (* j sc) q)
                            sb (Math/max (aget sybar (rem q C)) floor)
                            t (aget ta (+ (* j sc) q))
                            wt (/ t (+ t (* w sb)))
                            g (+ (* c sc) q)]
                        (aset ssum g (+ (aget ssum g) (* wt (/ (* (aget alpha i) sb) yb))))
                        (aset swt g (+ (aget swt g) wt))))))))))))
    (let [w0 pool-prior-weight
          dflt-mu (double (get-in cfg [:share :mu]))]
      {:mu (into {} (for [[kind [^doubles msum ^doubles mwt]] mu-acc]
                      [kind (mapv (fn [c] (-> (/ (+ (aget msum c) (* w0 dflt-mu)) (+ (aget mwt c) w0))
                                              (Math/max 0.02) (Math/min 0.8)))
                                  (range C))]))
       :shares (into {} (for [[[kind src-kind :as key] [^doubles ssum ^doubles swt]] sh-acc]
                          [key (let [out (double-array (* C sc))]
                                 (dotimes [c C]
                                   (dotimes [q sc]
                                     (let [g (+ (* c sc) q)
                                           d (default-share cfg src-kind (quot q C) c (rem q C))]
                                       (aset out g (/ (+ (aget ssum g) (* w0 d)) (+ (aget swt g) w0))))))
                                 out)]))})))

(defn pool->data
  "The pool as plain data, for storing."
  [pool]
  (when pool (update pool :shares #(into {} (for [[k a] %] [k (vec a)])))))

(defn data->pool [data]
  (when data (update data :shares #(into {} (for [[k v] %] [k (double-array v)])))))

;; --- growing and reshaping ---------------------------------------------------------------

(def ^:private carried
  [:h :last :ybar :mu :smu :tmu :kappa :sk :tk :loss-own :loss-ch :disp])

(defn restructure
  "A model over `structure` that keeps everything `old` learned about the
  nodes and connections the two share. New nodes start fresh; a connection
  that survives keeps its statistics, new ones start from the prior. Used as
  the world changes: dyads that become active get leaves of their own, and
  neighbours are re-ranked. The priors are learned anew from what every node
  has learned (see `learn-pool`) unless :pooling is false."
  [old structure]
  (let [mdl (create structure (:config old)
                    (if (:pooling (:config old) true) (learn-pool old) (:pool old)))
        sc (* (scales (:config old)) C)
        old-index (:index old)]
    ;; a new node's dispersion starts at Poisson
    (doseq [node (:nodes mdl)
            :when (not (old-index (:id node)))]
      (java.util.Arrays/fill ^doubles (:disp node) (unit old)))
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
            (doseq [arr [:alpha :sa]]
              (dotimes [c C]
                (System/arraycopy (get prev arr) (+ (* c k-old sc) (* j0 sc))
                                  (get node arr) (+ (* c k-new sc) (* j sc)) sc)))
            (System/arraycopy (:ta prev) (* j0 sc) (:ta node) (* j sc) sc)))))
    (refresh! (merge mdl (select-keys old [:bin :exposure :stats :volumes :calibration :spread])))))
