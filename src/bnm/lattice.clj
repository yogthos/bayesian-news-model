(ns bnm.lattice
  "A model per place and per time scale (docs/LATTICE.md). Time is counted
  in months; each scale ({:name :per :horizon :discount}) issues forecasts
  at the end of each of its periods, finest first, and at each scale the
  levels of the place tree issue theirs bottom-up. A node is one level at
  one scale: a dynamic-model average (augur.dynamic) over groups of its
  inputs, pooled across the level's places, learning from each period's
  resolved outcomes and carrying what it learned forward.

  Every node has a state at each origin: the spec's :state of its gauges
  when given (a node may have no outcome of its own, a world energy gauge
  say), else the log odds of its forecast. A node's inputs at origin t, by
  group:
  - its gauges, as one group or as the spec's :gauge-groups;
  - :up, what finer nodes passed up: each finer scale's states for the
    place over the node's period (the last and the mean), and its
    children's at this origin (the chance any has an onset, and their
    mean state);
  - :down, what coarser nodes passed down, lagged: each coarser scale's
    last state for the place before t, and every ancestor's state at the
    previous origin of this scale;
  - :contradiction, the dialectic between levels and within a place: the
    gap between the place's own state and what comes down to it, its size,
    and that size accumulated through time (unresolved tension building),
    the tension finer nodes and its children have accumulated, and the
    spec's :tensions, pairs of opposed gauges (credit against investment,
    say), each gap and its accumulation. A place's accumulated tensions
    are resolved, reset, when an onset there becomes known (the spec's
    :resolutions, once per onset): the leap negates the contradiction that
    built it;
  - :exposure, with the spec's :exposure: everything that comes down,
    times the place's exposure to it, so a world shock lands where the
    place is open to it;
  - :feedback, errors passed across levels: the last resolved errors
    (outcome less forecast) of the place's coarser scales, its parent and
    its children, each known by t; unknown (nil) before any is.
  The node's own inputs (everything but the intercept and the spec's
  gauges, which the spec standardizes) are standardized by their running
  mean and spread at the node's earlier origins (with :flow-std-from
  :resolved, only those since its first resolved outcome, 0 before it,
  bnm-56c.33), capped at 5: strain, gaps
  and turns come in whatever units the states have, and a model reading
  them raw is wrecked by their scale.

  Each quantity flows one way at a time: down only from before t, errors
  only once known, so nothing is counted twice. A node learns, and its
  errors become readable, when it is next due after they are known: at a
  coarse scale that can be most of a period late, never early.

  With :coupling s, the levels learn from each other: after each period,
  a level's model of a gauge group and the next level's share their
  coefficients (augur.dynamic/couple at strength s), so what the regions
  learn about a gauge informs the countries and the countries' experience
  informs the regions.

  The models averaged at each node read the intercept and one group each,
  or all of them (:only picks a subset): the weights through time say
  which gauges, flows and contradictions mattered at that node. A group
  with no input at any of the node's places at an origin (every own input
  0, the unknown's mean: a source that ended, a signal gone stale, errors
  not yet known) drops out of that origin's forecasts, weight 0. :prior
  seeds coefficients with what is known."
  (:require [augur.dynamic :as d]
            [bnm.relations :as rel]
            [bnm.sheaf :as sh]
            [augur.math :as m]))

(defn finer?
  "Whether scale a is finer than scale b in `spec`."
  [spec a b]
  (let [ix (zipmap (map :name (:scales spec)) (range))]
    (< (ix a) (ix b))))

(defn- logit ^double [^double p] (m/logit (Math/max 1e-6 (Math/min (- 1.0 1e-6) p))))

(defn logit-of "Log odds, the probability kept off 0 and 1." ^double [^double p] (logit p))

(defn- mean [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))

(defn tension-gaps
  "The gaps between `pairs` ([i j], indices into the gauges) of `gauges`."
  [pairs gauges]
  (mapv (fn [[i j]] (- (double (nth gauges i)) (double (nth gauges j)))) pairs))

(defn strain
  "The strain of each of `pairs` ([i j]) in `gauges`: how far i runs ahead
  of j, nothing when it does not. A contradiction has a direction: credit
  ahead of wages strains a system, wages ahead of credit does not."
  [pairs gauges]
  (mapv (fn [[i j]] (Math/max 0.0 (- (double (nth gauges i)) (double (nth gauges j))))) pairs))

(defn strain-summary
  "A place's tensions summarized: the mean accumulated strain over the
  tensions it can measure (`measured`, one flag each), and the largest. A
  place with fewer sources is not a calmer one."
  [accs measured]
  (let [ms (keep (fn [[a m]] (when m a)) (map vector accs measured))]
    {:mean (if (seq ms) (/ (reduce + ms) (double (count ms))) 0.0)
     :max (if (seq ms) (double (apply max ms)) 0.0)}))

(defn turn-of
  "The turn of a boom in `history` (a flow's values, the latest last): its
  drawdown from its peak over the last `window` values, counted only while
  the `stock` is stretched (above zero), and that times the stock's
  stretch. A stretched stock whose flow reverses is the moment the
  quantity turns into a change of quality (docs/ANATOMY.md)."
  [history stock window]
  (let [recent (take-last window history)
        dd (if (seq recent) (Math/max 0.0 (- (double (apply max recent)) (double (last recent)))) 0.0)
        stretched (Math/max 0.0 (double stock))]
    [(if (pos? stretched) dd 0.0) (* stretched dd)]))

(defn window-log-odds
  "The log odds of an onset in a window a fraction `ratio` of a coarser
  node's, whose forecast for its own window is p, under a hazard constant
  across it: 1 - (1 - p)^ratio."
  ^double [^double p ^double ratio]
  (let [p (Math/max 1e-9 (Math/min (- 1.0 1e-9) p))
        q (- (Math/expm1 (* ratio (Math/log1p (- p)))))]
    (logit q)))

(def hermite
  "Five-point Gauss-Hermite quadrature for the standard normal: nodes and
  weights (also bnm.compose's, over a shared frailty)."
  [[0.0 0.5333333333333333] [1.3556261799742657 0.2220759220056126] [-1.3556261799742657 0.2220759220056126]
   [2.8569700138728056 0.011257411327720691] [-2.8569700138728056 0.011257411327720691]])

(defn offsets-under
  "[[weight offset]...]: a coarser node's linear predictor N(f, q) carried
  into a finer window (a fraction `ratio` of its own), by quadrature over
  it (Berry & West 2020: the aggregate's uncertainty passed down)."
  [f q ratio]
  (let [sd (Math/sqrt (Math/max 0.0 (double q)))]
    (vec (for [[z w] hermite] [w (window-log-odds (m/expit (+ f (* sd z))) ratio)]))))

(defn reconcile
  "The two windows' forecasts [a b] made coherent with a coarser forecast p
  of an onset anywhere in its window, of which they and a remainder (base
  probability r) are the parts: the windows' own chance of any onset and
  p pooled in log odds with equal weights, and every part's hazard scaled
  in proportion to meet it. Coherent forecasts are left as they are."
  [a b r p]
  (let [clamp #(Math/max 1e-9 (Math/min (- 1.0 1e-9) (double %)))
        a (clamp a) b (clamp b) r (Math/max 0.0 (Math/min (- 1.0 1e-9) (double r)))
        log-none (+ (Math/log1p (- a)) (Math/log1p (- b)) (Math/log1p (- r)))
        s (- (Math/expm1 log-none))
        target (m/expit (* 0.5 (+ (logit s) (logit (clamp p)))))
        k (/ (Math/log1p (- target)) log-none)]
    [(- (Math/expm1 (* k (Math/log1p (- a))))) (- (Math/expm1 (* k (Math/log1p (- b)))))]))

(defn accumulate
  "Strain accumulated through time, for the tests and the record: `entries`
  [t gap info] in time order, info {:resolved-at k} when a crisis seen at t
  becomes known at k. {t accumulated}: each period the strain decays by
  :decay and adds the gap's positive part, and resets to zero first in the
  first period at or after a crisis became known."
  [{:keys [decay]} entries]
  (loop [[[t gap info] & more] entries acc 0.0 resolve-at [] out {}]
    (if-not t
      out
      (let [due (filter #(<= % t) resolve-at)
            acc (if (seq due) 0.0 acc)
            acc (+ (Math/max 0.0 (double gap)) (* decay acc))
            resolve-at (cond-> (vec (remove #(<= % t) resolve-at)) (:resolved-at info) (conj (:resolved-at info)))]
        (recur more acc resolve-at (assoc out t acc))))))

(defn standardizer
  "An empty running standardizer: counts, sums and sums of squares per
  input."
  []
  {:n 0 :s nil :ss nil})

(defn observe
  "The standardizer after seeing the vector xs; an unknown input (nil)
  does not count toward its mean and spread."
  [{:keys [n s ss ns]} xs]
  (let [k (count xs)
        z (fn [v] (or v (vec (repeat k 0.0))))]
    {:n (inc n)
     :ns (mapv (fn [c x] (if (nil? x) c (inc c))) (or ns (vec (repeat k (long n)))) xs)
     :s (mapv (fn [a x] (if (nil? x) a (+ a x))) (z s) xs)
     :ss (mapv (fn [a x] (if (nil? x) a (+ a (* x x)))) (z ss) xs)}))

(defn standardize
  "xs against the mean and spread of everything observed before, capped at
  5; 0 until ten of an input have been seen (its scale is not known yet),
  and 0 for an unknown input (nil): the mean."
  [{:keys [n s ss ns]} xs]
  (if (< n 10)
    (mapv (constantly 0.0) xs)
    (mapv (fn [x si ssi ni]
            (let [ni (double ni)]
              (if (or (nil? x) (< ni 10))
                0.0
                (let [m (/ si ni)
                      sd (Math/sqrt (Math/max 0.0 (- (/ ssi ni) (* m m))))]
                  (if (> sd 1e-9) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd))) 0.0)))))
          xs s ss (or ns (repeat n)))))

(defn- without
  "The groups dropped at `scale`: the spec's :without everywhere, and its
  :without-at for this scale."
  [spec scale]
  (into (set (:without spec)) (get-in spec [:without-at scale])))


(defn- layout
  "The input layout of a node: {:width n :groups {group [index...]} :order}."
  [spec scale level-ix]
  (let [b (sh/base spec)
        finer (sh/finer b scale)
        coarser (sh/coarser b scale)
        kids? (pos? level-ix)
        n-anc (- (count (:levels spec)) level-ix 1)
        gw (get-in spec [:gauge-width scale] 0)
        n-tensions (count (get-in spec [:tensions scale]))
        ew (if (:exposure spec) (:exposure-width spec) 0)
        sizes [[:gauges gw]
               [:up (+ (* 2 (count finer)) (if kids? 2 0))]
               [:down (+ (count coarser) n-anc)]
               [:contradiction (+ 3 (if (pos? n-anc) 3 0) (if (seq coarser) 1 0) (if (seq finer) 1 0) (if kids? 1 0))]
               [:turn (* 2 (count (get-in spec [:turns scale])))]
               [:exposure (* ew (+ (count coarser) n-anc))]
               [:feedback (+ (count coarser) (if (pos? n-anc) 1 0) (if kids? 1 0))]
               ;; what other kinds pass in (bnm.world-run): their states, onsets
               [:relations (if (:relations spec) (get-in spec [:relation-width scale] 0) 0)]
               ;; the world's state as known at t, directly and through the
               ;; place's exposure (recursive step 2, bnm.world-state)
               [:world-gauges (if (:world-gauges spec) (get-in spec [:world-width scale] 0) 0)]
               ;; :downscale-learn: the year's offset less what is known, a core
               ;; input whose coefficient is the learned pool weight (Test 4)
               [:pool (if (and (:downscale-learn spec) (get-in spec [:downscale scale])) 1 0)]]
        ;; :without drops groups from the inputs altogether (ablations)
        sizes (map (fn [[g n]] [g (if (contains? (without spec scale) g) 0 n)]) sizes)
        spans (second (reduce (fn [[from acc] [g n]] [(+ from n) (assoc acc g (vec (range from (+ from n))))])
                              [1 {}] sizes))
        named (get-in spec [:gauge-groups scale])
        gauge-groups (cond (contains? (without spec scale) :gauges) {}
                           named (into {} (for [[g ix] named] [g (mapv #(inc (long %)) ix)]))
                           :else {:gauges (spans :gauges)})
        rel-groups (when-let [rs (seq (spans :relations))]
                     (if-let [named (get-in spec [:relation-groups scale])]
                       (into {} (for [[g ix] named] [g (mapv #(nth rs %) ix)]))
                       {:relations (vec rs)}))
        flows (filter #(seq (spans %)) [:up :down :contradiction :turn :exposure :feedback :world-gauges])
        ;; :relation-nest: a link's model reads all the node reads and the
        ;; link, and the all-inputs model no link, so the averaging sets the
        ;; node with each link against the node without it
        nest? (:relation-nest spec)
        own (vec (mapcat spans [:gauges :up :down :contradiction :turn :exposure :feedback :world-gauges]))
        all (if nest? own (vec (concat own (spans :relations))))]
    {:width (+ 1 (reduce + (map second sizes))) :finer finer :coarser coarser
     ;; the contradiction summaries theory signs (more strain, more risk):
     ;; the mean and largest strain, the coarser scales' and the finer ones'
     :strain-ix (let [c (spans :contradiction)]
                  (when (seq c)
                    (let [base (first c)
                          after-parent (+ base 3 (if (pos? n-anc) 3 0))]
                      (vec (concat [base (+ base 2)]
                                   (when (seq coarser) [after-parent])
                                   (when (seq finer) [(+ after-parent (if (seq coarser) 1 0))]))))))
     :order (vec (concat [:base] (sort (keys gauge-groups)) flows (sort (keys rel-groups)) [:all]))
     :groups (merge gauge-groups (select-keys spans flows)
                    (if nest? (update-vals rel-groups #(vec (concat own %))) rel-groups)
                    {:base [] :all all})
     ;; each relation group's own inputs, whatever its model reads
     :parts (or rel-groups {})
     ;; the learned pool weight's input, read by every model (:downscale-learn)
     :pool (vec (spans :pool))
     :nested (if nest? (set (keys rel-groups)) #{})}))

(defn layout-of
  "The input layout of the node at `scale` and level index `level-ix`:
  {:width :groups {group [model inputs]} :parts {relation group [its own
  inputs]} :order ...}."
  [spec scale level-ix]
  (layout spec scale level-ix))

(defn- shrink-groups
  "[[group positions] ...]: under :group-shrink, the groups of a node's
  layout (each relation group's own inputs) and where their inputs sit
  among the model inputs `ix`, the core inputs left out; by name."
  [{:keys [groups parts]} ix core?]
  (let [pos (zipmap ix (range))]
    (vec (sort-by (comp str first)
                  (for [[g gix] (merge groups parts)
                        :when (not (#{:base :all} g))
                        :let [ps (vec (keep pos (remove core? gix)))]
                        :when (seq ps)]
                    [g ps])))))

(defn- node-model
  "A node's model average: one dynamic logistic per group of its inputs and
  one over all of them, each reading the intercept and the spec's :core
  inputs (what is known, read by every model). A coefficient starts at zero
  with variance 1 (4 for the intercept), the all-inputs model's non-core
  ones at the spec's :shrink, unless the spec's :prior gives {input-index
  [mean variance]} for the node."
  [spec scale level {:keys [discount]} {:keys [groups order nested] :as lay}]
  (let [prior (merge (when-let [[m v] (:contradiction-prior spec)]
                       (into {} (for [i (:strain-ix lay)] [i [m v]])))
                     (or (when-let [f (:prior spec)] (f scale level lay)) {}))
        core (into (mapv #(inc (long %)) (get-in spec [:core scale])) (:pool lay))
        core? (set core)
        prior (merge prior (into {} (for [i (:pool lay)] [i [0.0 (double (get-in spec [:downscale-learn :var] 0.25))]])))
        shrink (:shrink spec)
        ;; :shrink-per-input v (fix 2): the all-inputs model's prior variance
        ;; per non-core input is v over their number, so its prior predictive
        ;; variance about the base stays near v however many inputs the node
        ;; reads (at :shrink 0.1, about 80 inputs gave a variance of 20-58 in
        ;; log odds at the food year node in 2005-2010, a state near 0.5)
        shrink (if-let [v (:shrink-per-input spec)]
                 (let [n (count (remove core? (groups :all)))]
                   (if (pos? n) (/ (double v) n) shrink))
                 shrink)]
    (as-> (into {} (for [g (if (:only spec) (filter (:only spec) order) order)
                               :let [ix (vec (distinct (concat [0] core (remove core? (groups g)))))]
                               :when (or (= g :base) (seq (groups g)))]
                           [g (d/logistic {:group-shrink (when-let [gs (when (= g :all) (:group-shrink spec))]
                                                           ;; bnm-56c.2 (B): each group of the
                                                           ;; all-inputs model shrunk by its own scale
                                                           (assoc gs :groups (mapv second (shrink-groups lay ix core?))))
                                           :hold (when (and (:hold-links spec) (contains? nested g))
                                                   ;; a nested link's own inputs, not discounted
                                                   (let [own (set (get (:parts lay) g))]
                                                     (vec (keep-indexed (fn [pos i] (when (own i) pos)) ix))))
                                           :mean (map #(first (get prior % [0.0])) ix)
                                           :var (map #(or (second (get prior % [nil nil]))
                                                          (cond (zero? %) 4.0
                                                                (and shrink (or (= g :all) (contains? nested g)) (not (core? %))) shrink
                                                                :else 1.0))
                                                     ix)
                                           :discount discount :inputs ix})])) models
      (d/averaged models
                  ;; :weighting (every node) or :weighting-at {scale ...}: how the
                  ;; averaging sets its weights (augur.dynamic/averaged), posterior
                  ;; weights without; :weight-prior {group share}, the shares
                  ;; they start from and fade toward, equal without (bnm-56c.2)
                  (let [wt (get-in spec [:weighting-at scale] (:weighting spec))
                        ;; :evidence (ranked increments): the base model is the
                        ;; reference, a nested link set against the all-inputs model
                        wt (if (= :evidence (:rule wt))
                             (merge {:base :base :parents (zipmap (filter models nested) (repeat :all))} wt)
                             wt)
                        pr (not-empty (select-keys (:weight-prior spec) (keys models)))]
                    (cond-> {:forget (:forget spec)} wt (assoc :weighting wt) pr (assoc :prior pr)
                      ;; :evidence-at :issue (loop audit issue 2): each row scored by
                      ;; the predictive each model issued for it, not at learn time
                      (:evidence-at spec) (assoc :evidence-at (:evidence-at spec))))))))

(defn model-state
  "{:p :f :q}: one model's forecast for inputs x, the probability averaged
  over the offsets `os` ([[weight offset] ...]) as the node's is, and its
  linear predictor's moments at the mean offset o."
  [mdl x o os]
  (let [{:keys [f q]} (d/predictive mdl x o)]
    {:p (if (= 1 (count os)) (d/predict mdl x o) (reduce + (for [[w o'] os] (* w (d/predict mdl x o')))))
     :f f :q q}))

(defn reference-state
  "The default reference state of a node (:state-from :reference): the
  all-inputs model's forecast, {:s log odds :p :f :q}. That model reads
  everything the node reads, so the states passed on carry what the node
  knows, and it is the model the posterior weights of the base of record
  (t48) settle on; it depends on no weight."
  [{:keys [models x o os scale level]}]
  (when-not (:all models)
    (throw (ex-info "bnm.lattice: :state-from :reference needs the all-inputs model (:all), which the node does not average"
                    {:state-from :reference :scale scale :level level :models (vec (keys models))})))
  (let [st (model-state (:all models) x o os)]
    (assoc st :s (logit (:p st)))))

(defn- state-fn
  "The spec's :state-from as a function of the node, or nil (the state is
  the combined forecast). A function is called with {:scale :level :place
  :t :models :x :o :os}, the node's models before they average, and
  returns a map with at least :s (and :p :f :q, else from :s): it must
  not read the weights. :reference is `reference-state`; another keyword
  names the model whose forecast is the state."
  [{sf :state-from}]
  (let [f (cond (nil? sf) nil
                (fn? sf) sf
                (= :reference sf) reference-state
                (keyword? sf) (fn [{:keys [models x o os scale level]}]
                                (if-let [mdl (models sf)]
                                  (let [st (model-state mdl x o os)] (assoc st :s (logit (:p st))))
                                  (throw (ex-info (str "bnm.lattice: :state-from names a model the node does not average " sf)
                                                  {:state-from sf :scale scale :level level :models (vec (keys models))}))))
                :else (throw (ex-info "bnm.lattice: :state-from must be :reference, a model's group or a function" {:state-from sf})))]
    (when f
      (fn [node]
        (let [{:keys [s] :as st} (f node)]
          (when-not (number? s)
            (throw (ex-info "bnm.lattice: a :state-from state needs a number :s" {:state st})))
          (merge {:p (m/expit s) :f s :q 0.0} st))))))

(defn- inputs
  "The node's inputs for `place` at origin t, what it read, its own state
  and its accumulated tensions after this period. Every read of another
  node goes through the sheaf's restrictions (bnm.sheaf), which record
  it."
  [spec {:keys [book errors tension resolutions]} scale scale-spec level-ix place t gauges & [{:keys [pool]}]]
  (let [{:keys [finer coarser width]} (layout spec scale level-ix)
        per (:per scale-spec)
        b (sh/base spec)
        ;; every read through the sheaf, none after the origin; each made
        ;; eagerly, so it is recorded whether or not its input is used
        r (sh/reader book errors {:origin t})
        ;; the ancestors among the spec's levels (a parent map may run past
        ;; them, to a world the spec has no level for): the layout's count
        ancestors (vec (take (- (count (:levels spec)) level-ix 1) (sh/ancestors b place)))
        ;; up: finer scales over this period (:up-lag {scale n}: n periods
        ;; before it, bnm-56c.2), children at this origin
        lag (* per (long (get-in spec [:up-lag scale] 0)))
        finer-entries (mapv (fn [f] [f (vec (sh/over r :up [f place] (- t (dec per) lag) (- t lag)))]) finer)
        up-time (mapcat (fn [[_ es]]
                          (if (seq es) [(:s (val (last es))) (mean (map (comp :s val) es))] [0.0 0.0]))
                        finer-entries)
        kids (sh/children b level-ix place)
        kid-entries (vec (keep (fn [c] (sh/at r :up [scale c] t)) kids))
        up-space (when (pos? level-ix)
                   (if (seq kid-entries)
                     [(logit (- 1.0 (reduce * (map #(- 1.0 (:p %)) kid-entries)))) (mean (map :s kid-entries))]
                     [0.0 0.0]))
        ;; down: coarser scales before t, every ancestor at the previous origin
        down-entries (mapv (fn [c] (some-> (sh/latest-before r :down [c place] t) second)) coarser)
        down-time (mapv :s down-entries)
        down-space (mapv (fn [a] (:s (sh/at r :down [scale a] (- t per)))) ancestors)
        down (mapv #(or % 0.0) (concat down-time down-space))
        ;; the place's own state: its gauges' when the spec says, else its
        ;; last forecast's (never this one's, which these inputs make)
        stated (when-let [f (:state spec)] (f scale place t gauges))
        ;; read through the sheaf, so the audit sees the loop (loop audit issue 19)
        own (or stated (some-> (sh/latest-before r :self [scale place] t) second :s) 0.0)
        ;; the dialectic, like against like: the place against its parent at
        ;; this scale, and against each coarser scale; each gap, and the
        ;; strain each way (the place above, the place below) accumulated
        ;; through time
        {:keys [rel fin tacc flows last-t]} (get tension [scale place] {:rel {} :fin {} :flows {} :last-t Long/MIN_VALUE})
        ;; resolutions: the onsets at the place that became known since the
        ;; node's last period (the spec's :resolutions, one per onset)
        resolved (seq (subseq (resolutions place) > last-t <= t))
        resolved? (boolean resolved)
        decay (double (get spec :tension-decay 0.7))
        carry (fn [a] (if resolved? 0.0 (* decay (double (or a 0.0)))))
        parent-state (first down-space)
        relations (concat (when (seq ancestors) [[:parent parent-state]])
                          (map vector coarser down-time))
        ;; the gaps are the coboundary: the place's state against each neighbour's
        gaps (sh/coboundary own (into {} relations))
        rel' (into {} (for [[k _] relations
                            :let [gap (gaps k)]]
                        [k {:gap gap
                            :above (+ (Math/max 0.0 gap) (carry (get-in rel [k :above])))
                            :below (+ (Math/max 0.0 (- gap)) (carry (get-in rel [k :below])))}]))
        ;; the strain each coarser scale has accumulated, carried down (from before t)
        coarser-strain (map #(or (:strain %) 0.0) down-entries)
        ;; strain built at finer scales, re-accumulated at this one's pace:
        ;; months of local strain become years of it
        fin' (into {} (for [[f es] finer-entries
                            :let [now (or (mean (keep (comp :strain val) es)) 0.0)]]
                        [f {:now now :acc (+ now (carry (get-in fin [f :acc])))}]))
        kid-strain (when (pos? level-ix) [(or (mean (keep :strain kid-entries)) 0.0)])
        pairs (get-in spec [:tensions scale])
        tgaps (tension-gaps pairs gauges)
        tacc (mapv (fn [g a] (+ (Math/max 0.0 g) (carry a))) tgaps (or tacc (repeat (count tgaps) 0.0)))
        ;; the contradictions summarized: the place's mean strain over the
        ;; tensions it can measure, its change, its largest; the place
        ;; against its parent; the strain the coarser scales carry down, the
        ;; finer scales built up, and its children hold
        {smean :mean smax :max} (strain-summary tacc (map (fn [[i j]] (or (not= 0.0 (double (nth gauges i))) (not= 0.0 (double (nth gauges j))))) pairs))
        contradiction (concat [smean (- smean (double (or (:prev-mean (get tension [scale place])) 0.0))) smax]
                              (when-let [{:keys [gap above below]} (rel' :parent)] [gap above below])
                              (when (seq coarser) [(or (mean coarser-strain) 0.0)])
                              (when (seq finer) [(or (mean (map (fn [[f _]] (get-in fin' [f :acc])) finer-entries)) 0.0)])
                              kid-strain)
        ;; the place's total accumulated strain, what it passes up and down:
        ;; its opposed aspects' strain (directional, from theory) and its
        ;; disagreement with its parent either way (a place out of step with
        ;; its parent is a contradiction whichever way it runs). Its gaps
        ;; against coarser scales stay its own inputs: they set a state
        ;; against another scale's, which other nodes cannot read like for like
        strain-total (+ smean (if-let [{:keys [above below]} (rel' :parent)] (+ above below) 0.0))
        ;; the turn: each boom's drawdown from its peak while its stock is stretched
        turn-specs (get-in spec [:turns scale])
        flows' (into {} (for [[_ f w] turn-specs]
                          [f (vec (take-last w (conj (get flows f []) (double (nth gauges f)))))]))
        turn (mapcat (fn [[stock f w]] (turn-of (flows' f) (nth gauges stock) w)) turn-specs)
        ;; exposure: what comes down, times the place's exposure to it
        exposure (when-let [f (:exposure spec)]
                   (let [e (f scale place t)] (for [dv down ev e] (* dv ev))))
        ;; feedback: errors of the coarser scales, the parent, the children, known by t;
        ;; none known yet (before the outcomes begin, a catalogue that starts
        ;; late) is unknown, nil, not a perfect forecast of 0: zeros would
        ;; shrink the running spread and put the first real errors at the cap
        err (fn [s p] (second (sh/latest-known r [s p] t)))
        parent (when (seq ancestors) (first ancestors))
        ;; read (eagerly) only when the group is used
        feedback (when-not ((without spec scale) :feedback)
                   (vec (concat (mapv #(err % place) coarser)
                                (when parent [(err scale parent)])
                                (when (pos? level-ix) [(mean (vec (keep #(err scale %) kids)))]))))
        ;; relations: other kinds' states and onsets for the place, from before t
        rel (when-let [f (when-not ((without spec scale) :relations) (:relations spec))]
              (let [{:keys [x] :as r} (f scale place t)
                    width (get-in spec [:relation-width scale] 0)]
                (when (not= width (count x))
                  (throw (ex-info "bnm.lattice: :relations gave the wrong width" {:scale scale :width width :got (count x)})))
                ;; an unknown input stays nil: standardized to the mean
                (update r :x #(mapv (fn [v] (some-> v double)) %))))
        ;; the world's state at t (recursive step 2): a function of the place
        ;; and t, read as of t with its reads (bnm.world-state), unknown nil
        world (when-let [f (when-not ((without spec scale) :world-gauges) (:world-gauges spec))]
                (let [width (get-in spec [:world-width scale] 0)]
                  (when (pos? width)
                    (let [{:keys [x] :as r} (f scale place t)]
                      (when (not= width (count x))
                        (throw (ex-info "bnm.lattice: :world-gauges gave the wrong width" {:scale scale :width width :got (count x)})))
                      (when-let [late (seq (filter #(> (or (:known-at %) (:origin %)) t) (:reads r)))]
                        (throw (ex-info "bnm.lattice: a world read after the origin" {:scale scale :place place :t t :reads (vec late)})))
                      (update r :x #(mapv (fn [v] (some-> v double)) %))))))
        pool-x (when (and (:downscale-learn spec) (get-in spec [:downscale scale])) [(some-> pool double)])
        x (let [w (without spec scale)]
            (vec (concat [1.0] (when-not (w :gauges) gauges)
                         (when-not (w :up) (concat up-time up-space))
                         (when-not (w :down) down)
                         (when-not (w :contradiction) contradiction)
                         (when-not (w :turn) turn)
                         (when-not (w :exposure) exposure)
                         (when-not (w :feedback) feedback)
                         (when-not (w :relations) (:x rel))
                         (:x world)
                         pool-x)))]
    (when (not= (count x) width)
      (throw (ex-info "bnm.lattice: a node's inputs do not match its layout"
                      {:scale scale :level-ix level-ix :place place :width width :got (count x)})))
    {:x x
     :reads (-> (sh/reads r)
                (into (when-not ((without spec scale) :relations) (:reads rel)))
                (into (:reads world)))
     :stated stated
     :resolved resolved
     :strain strain-total
     :tension {:rel rel' :fin fin' :tacc tacc :flows flows' :prev-mean smean :last-t t}}))

(def ^:private flow-groups #{:base :up :down :contradiction :turn :exposure :feedback :world-gauges :all})

(defn- couple-levels
  "Couples the gauge-group models of each pair of adjacent levels at one
  scale. Neither the flows' models nor the `nested` relation groups' are
  coupled: a nested group's model reads all the node reads, flows among
  them, so coupling it would set the node with a link and the levels'
  coupling against the all-inputs model without either."
  [models scale levels strength nested]
  (reduce (fn [models [la lb]]
            (let [ka [scale la] kb [scale lb]
                  a (models ka) b (models kb)]
              (if (and a b)
                (let [shared (filter #(and (not (flow-groups %))
                                           (not (contains? nested %))
                                           (get-in b [:models %])
                                           (= (:k (get-in a [:models %])) (:k (get-in b [:models %]))))
                                     (keys (:models a)))
                      [a b] (reduce (fn [[a b] g]
                                      (let [ma (get-in a [:models g]) mb (get-in b [:models g])
                                            [ma mb] (d/couple ma mb {:indices (range 1 (:k ma)) :strength strength})]
                                        [(assoc-in a [:models g] ma) (assoc-in b [:models g] mb)]))
                                    [a b] shared)]
                  (assoc models ka a kb b))
                models)))
          models (partition 2 1 levels)))

(defn dormant
  "The groups of a node's `layout` with no input at any place: every one of
  its own inputs (past the intercept and the `core` gauge indices, which
  every model reads) exactly 0, the unknown's mean, in every row of `xs`.
  The base and all-inputs models are never dormant."
  [{:keys [groups parts]} core xs]
  (let [core? (set (map #(inc (long %)) core))
        xs (vec xs)]
    (set (for [[g ix] (merge groups parts)
               :when (not (#{:base :all} g))
               :let [own (remove core? ix)]
               :when (and (seq own) (seq xs)
                          (every? (fn [x] (every? #(let [v (nth x %)] (or (nil? v) (zero? (double v)))) own)) xs))]
           g))))

(defn live-weights
  "Model-averaging weights `w` with the `out` groups at 0 and the rest
  renormalized; `w` as it is when nothing else carries weight."
  [w out]
  (let [kept (reduce + 0.0 (for [[g v] w :when (not (contains? out g))] v))]
    (if (pos? kept)
      (into {} (for [[g v] w] [g (if (contains? out g) 0.0 (/ v kept))]))
      w)))

(defn learning-rows
  "The rows a node's models learn from: each resolved row as it is, or,
  with `mixture?` (:learn-offsets :mixture, bnm-56c.22), a row issued
  over several offsets ([[w o] ...] under :downscale-uncertainty) as one
  row per offset, its likelihood weighted w (augur's row :w), so the
  models learn at the offsets the forecast was issued over rather than
  at their mean."
  [rows mixture?]
  (vec (mapcat (fn [{:keys [os] :as r}]
                 (if (and mixture? (< 1 (count os)))
                   (for [[w o] os] (-> r (dissoc :os) (assoc :o o :w (* (double w) (double (or (:w r) 1.0))))))
                   [(dissoc r :os)]))
               rows)))

(defn- clim-logit
  "The log odds of node k's climatology once the `ready` rows are counted:
  the rate among its resolved outcomes, or with none, the :clim-prior's
  counts (bnm-56c.26), else an even rate; nil without an :offset."
  [spec st k scale level ready]
  (when (:offset spec)
    (let [[n1 n] (get-in st [:clim k] [0 0])
          n1 (+ (or n1 0) (count (filter :y ready)))
          n (+ (or n 0) (count ready))
          [n1 n] (or (when (zero? n)
                       (when-let [cp (:clim-prior spec)]
                         (let [[p1 p] (cp scale level)]
                           (when (and p (pos? p)) [p1 p]))))
                     [n1 n])]
      (logit (/ (+ n1 0.5) (+ n 1.0))))))

(defn reanchor
  "A pending row with its offsets (:o, and each of :os) moved by `delta`:
  under :clim-learn :current (bnm-56c.27), the climatology it was issued at
  replaced by the one current when it is learned, so the intercept learns
  the departure from the climatology the forecasts are issued at, not
  from one the node has since left."
  [row delta]
  (let [delta (double delta)]
    (cond-> (update row :o + delta)
      (:os row) (update :os (fn [os] (mapv (fn [[w o]] [w (+ (double o) delta)]) os))))))

(declare start step finish)

(defn run
  "Runs the lattice of `spec` from :start to :end (months):
  {:scales [{:name :per :horizon :discount}] finest first,
   :levels [[level [places]] ...] bottom-up, :parent {place parent},
   :target (fn [scale place origin]) -> {:y :known-at} or nil,
   :gauges (fn [scale place origin]) -> [inputs], :gauge-width {scale n},
   :gauge-groups {scale {group [indices]}}, :prior (fn [scale level layout]),
   :core {scale [gauge indices]}, read by every model,
   :offset (fn [scale place origin]) -> the log odds of what is known (a
   calibrated forecast from elsewhere), or nil for climatology, the rate
   among the node's resolved outcomes; the node learns departures from it,
   :clim-prior (fn [scale level]) -> [onsets rows], the counts climatology
   reads while the node has no resolved outcome (bnm-56c.26; an even rate,
   0.5, without),
   :flow-std-from :resolved, the node's flows standardized over its
   origins from its first resolved outcome on, nothing observed (its
   flows 0) before it (bnm-56c.33: before it the states its flows read
   are uninformed, climatology 0.5 or a prior, and counting them made the
   first real moves read near the cap); the default, :all, every origin
   since the start, is the standardizer before the fix, kept only so the
   registered ledger issues, which ran on it, reproduce,
   :clim-learn :current, a row issued at climatology learned at the
   climatology current when it is learned (bnm-56c.27): otherwise the
   intercept learns the outcomes against the offset they were issued at,
   and when climatology moves (from the prior, or 0.5, to the node's own
   rate) carries the old level into every later forecast on top of the new;
   at its issue offset without,
   :shrink, the all-inputs model's prior variance on its non-core inputs,
   :shrink-per-input v, that variance as v over their number at each node
   (fix 2: the reference state's prior predictive variance about the base
   near v whatever the node's width; overrides :shrink),
   :state (fn [scale place origin gauges]) -> a node's state or nil,
   :tensions {scale [[i j] ...]}, each i's strain ahead of j, :tension-decay (0.7),
   :contradiction-prior [mean variance] for the strain summaries,
   :turns {scale [[stock flow window] ...]}, gauge indices: the flow's
   drawdown from its peak over window periods while the stock is stretched,
   :resolutions (fn [place]) -> sorted-set of the months at which each
   onset at the place became known (none without),
   :exposure (fn [scale place origin]) -> [exposures], :exposure-width n,
   :coupling, the strength levels share what they learn (none without),
   :forget, the model averaging's forgetting, :only, a subset of groups,
   :weighting, how the averaging sets its weights (augur.dynamic/averaged's
   :weighting: tempered posterior or stacked; posterior weights without),
   :weighting-at {scale weighting} the same at one scale, :hold-links, a
   nested link's own coefficients not discounted (augur.dynamic's :hold),
   :weight-prior {group share}, the averaging's prior weights (augur's
   :prior: what the weights start from and the forgetting fades them
   toward; the groups not named share the rest),
   :weighting {:rule :evidence :scale c} ranks the models by their proven
   increment over the base model (a nested link's over the all-inputs
   model; augur's :evidence, bnm-56c.2), {:rule :synth :discount d :var v}
   a dynamic synthesis of them,
   :group-shrink {:tau t}, the all-inputs model's groups each shrunk by
   their own horseshoe-type scale (augur's :group-shrink; with :only #{:all}
   one model and no averaging), the scales recorded in :weights as
   :scale/<group>,
   :up-lag {scale n}, the node at that scale reads its finer scales'
   states over the period n of its periods before its last (bnm-56c.2's
   loop through :downscale; this period's without),
   :state-from, where a node's state comes from (loop audit issue 1):
   by default the combined forecast, which its own models then read back
   (its own state, :up through a finer node's offset, :down, exposure, the
   errors fed back), so a weighting changes the models it weighs; a group's
   name (:all, the all-inputs model) takes the state, and the errors, from
   that model's forecast, :reference the default reference, the all-inputs
   model's (`reference-state`), and the combination is only issued (the
   book keeps it as :issued); a function of the node ({:scale :level
   :place :t :models :x :o :os}, never the weights) -> {:s ...} supplies
   any other, the map kept in the book,
   :evidence-at :issue, each row's evidence for the averaging the log score
   of the predictive each model issued for it at the origin, not of its
   state when the row is learned (augur's :evidence-at; loop audit issue 2),
   :up-net-of-downscale, a node with :downscale passes up its log odds
   less the part of its offset that came down (bnm-56c.2: otherwise the
   coarser node reads its own forecast back through the finer states),
   :world-offset (fn [scale place t]) -> {:x log odds :reads} or nil, the
   world's term (bnm.world-term, learned once per origin) added to the
   node's offsets, so every model reads it at a fixed coefficient of 1
   (fix 3); none without,
   :without, groups dropped from the inputs altogether, :without-at
   {scale #{groups}} the same at one scale,
   :trace, to record what each node read and learned from (:inputs, also
   each row's inputs and forecast)}.
  {:forecasts [{:scale :place :origin :p :y}] :weights {[scale level]
  [[t weights]]} :models :trace}."
  [spec]
  (loop [state (start spec)]
    (if (> (:t state) (:end spec))
      (finish state)
      (recur (step spec state)))))

(defn start
  "The lattice's state before its first month: what `step` advances."
  [spec]
  (when-let [bad (seq (remove #{:gauges :up :down :contradiction :turn :exposure :feedback :relations :world-gauges}
                              (concat (:without spec) (mapcat val (:without-at spec)))))]
    (throw (ex-info (str "bnm.lattice/run: :without names no group " (vec bad)) {:without (:without spec) :without-at (:without-at spec)})))
  (let [resolutions (memoize (fn [place] (if-let [f (:resolutions spec)] (or (f place) (sorted-set)) (sorted-set))))]
    {:t (:start spec)
     :st {:book {} :errors {} :tension {} :models {} :pending {} :resolutions resolutions :resets {}}
     :forecasts [] :weights {} :trace []}))

(defn finish
  "The result of a run from its last state."
  [{:keys [st forecasts weights trace]}]
  {:forecasts forecasts :weights weights :trace trace
   :book (:book st) :models (:models st) :resets (:resets st)
   :evidence (:evidence st) :flow-std (:flow-std st)})

(defn flows-informed?
  "Whether node `k` has a resolved outcome, from which on, under
  :flow-std-from :resolved, its flows' standardizer counts what it sees
  (bnm-56c.33)."
  [st k]
  (pos? (long (or (second (get-in st [:clim k])) 0))))

(defn book
  "The book of a lattice state: {[scale place] (sorted-map origin entry)}."
  [state]
  (get-in state [:st :book]))

(defn step
  "The lattice's state after month (:t state): every node due then learns
  what is known and issues its forecasts. A world of kinds steps each
  kind's lattice in turn (bnm.world-run), each reading the others' books
  from before t through its :relations."
  [spec {:keys [t st forecasts weights trace]}]
  (let [{:keys [scales levels start]} spec]
        (let [due (filter #(zero? (mod (inc (- t start)) (:per %))) scales)
              [st forecasts weights trace]
              (reduce
               (fn [[st forecasts weights trace] [scale-spec level-ix]]
                 (let [scale (:name scale-spec)
                       [level places] (nth levels level-ix)
                       k [scale level]
                       model (or (get-in st [:models k]) (node-model spec scale level scale-spec (layout spec scale level-ix)))
                       ;; learn from what is known by t; its errors become known now
                       {ready true waiting false} (group-by #(<= (:known-at %) t) (get-in st [:pending k]))
                       ;; each model's issue-time predictive, the averaging's evidence
                       ;; under :evidence-at :issue
                       ;; climatology as it stands once this period's outcomes are
                       ;; counted: what the node's forecasts at t are issued at
                       clim-now (clim-logit spec st k scale level ready)
                       rows (learning-rows (map #(cond-> (select-keys % [:x :y :o :os]) (:lp %) (assoc :issued (:lp %))
                                                   ;; bnm-56c.27: :clim-learn :current, a row issued at
                                                   ;; climatology is learned at the climatology current now
                                                   (and (= :current (:clim-learn spec)) (:clim %)) (reanchor (- clim-now (:clim %))))
                                                ready)
                                           (= :mixture (:learn-offsets spec)))
                       ;; traced: each model's evidence, the log score of the outcomes it
                       ;; learns from now, scored before it learns them
                       st (if (and (:trace spec) (seq rows))
                            (reduce (fn [st [g ll]]
                                      (update-in st [:evidence k g] (fn [[ll0 n]] [(+ (or ll0 0.0) ll) (+ (or n 0) (count rows))])))
                                    st (d/evidence model rows))
                            st)
                       model (d/step model rows)
                       ;; climatology: the rate among this node's resolved outcomes
                       st (update-in st [:clim k] (fn [[n1 n]] [(+ (or n1 0) (count (filter :y ready))) (+ (or n 0) (count ready))]))
                       _ (when (and (:clim-learn spec) (not= :current (:clim-learn spec)))
                           (throw (ex-info "bnm.lattice: :clim-learn takes :current" {:clim-learn (:clim-learn spec)})))
                       _ (when-not (#{:all :resolved} (:flow-std-from spec :all))
                           (throw (ex-info "bnm.lattice: :flow-std-from takes :all or :resolved" {:flow-std-from (:flow-std-from spec)})))
                       ;; the offset, and whether it is climatology (:clim, its log odds):
                       ;; with an :offset that has nothing to say, the rate among the
                       ;; node's resolved outcomes (or the prior's, before any)
                       offset-of (if-let [f (:offset spec)]
                                   (fn [place t] (if-let [o (f scale place t)]
                                                   {:o (double o)}
                                                   {:o clim-now :clim clim-now}))
                                   (constantly {:o 0.0}))
                       ;; :downscale: the offset from a coarser node's latest forecast for the
                       ;; place, issued before t, scaled to this window under a constant
                       ;; hazard (docs/DOWNSCALE.md); pooled in log odds with what is known
                       ;; (:downscale-pool, its weight), its uncertainty carried down by
                       ;; quadrature (:downscale-uncertainty). [[weight offset]...] and the read
                       offsets-of (let [ds (get-in spec [:downscale scale])
                                        horizon-of (fn [n] (:horizon (first (filter #(= n (:name %)) (:scales spec)))))
                                        ratio (when ds (/ (double (:horizon scale-spec)) (horizon-of (:from ds))))
                                        pool (:downscale-pool spec)]
                                    (fn [place t]
                                      (let [r (sh/reader (:book st) {} {:origin t})]
                                        (if-let [[_ {:keys [p f q]}] (when ds (sh/latest-before r :downscale [(:from ds) place] t))]
                                          (let [os (if (:downscale-uncertainty spec) (offsets-under f q ratio) [[1.0 (window-log-odds p ratio)]])
                                                known (when (and pool (:offset spec)) ((:offset spec) scale place t))
                                                os (if known (mapv (fn [[w o]] [w (+ (* pool known) (* (- 1.0 pool) o))]) os) os)
                                                ;; the part of the offset that came down: the mean
                                                ;; offset the node is read at (the one whose p is the
                                                ;; offsets' average, as `made` takes it) less the known
                                                ;; part's share; over several offsets not Σ w o, which
                                                ;; left a Jensen gap (review of step 1)
                                                om (if (= 1 (count os)) (second (first os)) (logit (reduce + (for [[w o] os] (* w (m/expit o))))))
                                                down (- om (if known (* pool known) 0.0))]
                                            {:os os :read (first (sh/reads r)) :down down
                                             ;; :downscale-learn's input: the offset less what is known
                                             :pool (when known (- om known))})
                                          (let [{:keys [o clim]} (offset-of place t)]
                                            {:os [[1.0 o]] :down 0.0 :clim clim})))))
                       st (reduce (fn [st {:keys [place y p known-at]}]
                                    (update-in st [:errors [scale place]] (fnil assoc (sorted-map)) known-at
                                               (- (if y 1.0 0.0) p)))
                                  st ready)
                       ;; the node's own inputs (everything past the intercept and the
                       ;; gauges, which the spec standardizes) against their past
                       g-width (if (contains? (without spec scale) :gauges) 0 (get-in spec [:gauge-width scale] 0))
                       flow-std (get-in st [:flow-std k] (standardizer))
                       ;; :principal, a set of groups (or :auto, every contradiction, turn
                       ;; and relation group): the one contributing most at each place under
                       ;; the all-inputs model's coefficients, recorded in the book
                       principal-of (when-let [pc (:principal spec)]
                                      (let [{:keys [groups parts]} (layout spec scale level-ix)
                                            groups (merge groups parts)
                                            all (get-in model [:models :all])
                                            cands (if (= :auto pc)
                                                    (set (filter #(or (#{:contradiction :turn :relations} %)
                                                                      (= "rel" (namespace %)))
                                                                 (keys groups)))
                                                    pc)]
                                        (when all
                                          (fn [x] (let [mx (reduce (fn [v [i j]] (assoc v j (nth (d/means all) i)))
                                                                   (vec (repeat (count x) 0.0))
                                                                   (map-indexed vector (:inputs all)))]
                                                    (rel/principal mx x groups cands))))))
                       seen (vec (for [place places
                                       :let [g ((:gauges spec) scale place t)
                                             {:keys [os read down pool clim]} (offsets-of place t)
                                             ;; :world-offset (fix 3): the world's term, learned once
                                             ;; per origin at the world node, added to every offset
                                             wo (when-let [f (:world-offset spec)] (f scale place t))
                                             _ (when-let [late (seq (filter #(> (or (:known-at %) (:origin %)) t) (:reads wo)))]
                                                 (throw (ex-info "bnm.lattice: a world offset read after the origin" {:scale scale :place place :t t :reads (vec late)})))
                                             os (if-let [x (:x wo)] (mapv (fn [[w o]] [w (+ o (double x))]) os) os)
                                             {:keys [x reads stated tension strain resolved]} (inputs spec st scale scale-spec level-ix place t g {:pool pool})
                                             raw-flows (subvec x (inc g-width))
                                             x (into (subvec x 0 (inc g-width)) (standardize flow-std raw-flows))]]
                                   {:place place :x x :raw-flows raw-flows :os os :down down :clim clim
                                    :reads (cond-> (into reads (:reads wo)) read (conj read))
                                    :stated stated :tension tension :strain strain :resolved resolved}))
                       ;; a group with no input anywhere at t (its source ended, a signal
                       ;; gone stale, errors not yet known) reads the unknown's mean, 0, at
                       ;; every place: an intercept-only model under another name. It drops
                       ;; out of this period's forecasts, weight 0, the others renormalized;
                       ;; the averaging keeps its record, so it comes back when its source
                       ;; resumes
                       live (let [out (dormant (layout spec scale level-ix) (get-in spec [:core scale]) (map :x seen))]
                              (if (seq out) (assoc model :w (live-weights (d/weights model) out)) model))
                       state-of (state-fn spec)
                       made (vec (for [{:keys [place x os] :as e} seen
                                       :let [one? (= 1 (count os))
                                             ;; the mean offset, learned from: the one whose
                                             ;; probability is the offsets' average
                                             o (if one? (second (first os)) (logit (reduce + (for [[w o] os] (* w (m/expit o))))))
                                             p (if one? (d/predict live x o) (reduce + (for [[w o] os] (* w (d/predict live x o)))))
                                             {pf :f pq :q} (d/predictive live x o)
                                             target ((:target spec) scale place t)]]
                                   (assoc e :o o :p p :target target :f pf :q pq :principal (when principal-of (principal-of x))
                                          ;; :state-from: the node's state, and the errors it feeds back,
                                          ;; from a fixed reference rather than the combination, which is
                                          ;; only issued (loop audit issue 1)
                                          :ref (when state-of
                                                 (state-of {:scale scale :level level :place place :t t
                                                            :models (:models model) :x x :o o :os os}))
                                          ;; each model's predictive as issued: over several
                                          ;; offsets (:downscale-uncertainty) the forecast issued
                                          ;; is their mixture, so each model's is too, carried as
                                          ;; the log odds of its mixed p with no spread (what
                                          ;; augur's issue-time score reads), its moments at the
                                          ;; mean offset beside it
                                          :issued (if one?
                                                    (d/issued model x o)
                                                    (into {} (for [[g mdl] (:models model)
                                                                   :let [pg (reduce + (for [[w o'] os] (* w (d/predict mdl x o'))))]]
                                                               [g (assoc (d/predictive mdl x o)
                                                                         :mean-offset (d/predictive mdl x o)
                                                                         :f (m/logit (Math/max 1e-300 (Math/min (- 1.0 1e-16) pg)))
                                                                         :q 0.0)]))))))
                       st (reduce (fn [st {:keys [place p stated tension strain resolved f q principal down ref]}]
                                    (-> st
                                        (update-in [:book [scale place]] (fnil assoc (sorted-map)) t
                                                   ;; a node's state: the spec's when it gives one, else its
                                                   ;; forecast's log odds; f and q its linear predictor's moments
                                                   ;; :up-net-of-downscale: less what came down from the
                                                   ;; coarser node, so it does not read its own forecast back
                                                   ;; an open map: with :state-from, the reference's state (and
                                                   ;; whatever else it gives) apart from the forecast issued
                                                   (let [{sp :p sf :f sq :q :as base} (or ref {:p p :f f :q q})
                                                         s (or stated
                                                               (cond-> (if ref (:s ref) (logit sp))
                                                                 (:up-net-of-downscale spec) (- down)))]
                                                     (cond-> (assoc (if ref base {}) :p sp :s s :strain strain :f sf :q sq)
                                                       ref (assoc :issued {:p p :f f :q q})
                                                       principal (assoc :principal principal))))
                                        (assoc-in [:tension [scale place]] tension)
                                        (cond-> resolved (update-in [:resets [scale place]] (fnil into []) resolved))))
                                  st made)
                       ;; bnm-56c.33, :flow-std-from :resolved: the flows are standardized
                       ;; over the span the node's inputs carry information, from its first
                       ;; resolved outcome on; before it the states it reads are uninformed
                       ;; (climatology 0.5 or a prior), and counting them made the first real
                       ;; moves read near the cap. Until then nothing is observed, so its
                       ;; flows read 0. The default, :all, counts every origin (the
                       ;; registered issues' code)
                       st (if (or (not= :resolved (:flow-std-from spec)) (flows-informed? st k))
                            (assoc-in st [:flow-std k] (reduce (fn [sd {:keys [raw-flows]}] (observe sd raw-flows)) flow-std made))
                            st)
                       st (-> st
                              (assoc-in [:models k] model)
                              (assoc-in [:pending k] (into (vec waiting)
                                                           ;; the error fed back is the state's (the reference's p
                                                           ;; under :state-from); :lp each model's predictive at issue;
                                                           ;; :regime-at-issue a slot for the regime layer
                                                           (for [{:keys [place x o os p target ref issued clim]} made :when target]
                                                             (cond-> {:place place :x x :o o :p (if ref (:p ref) p) :y (:y target) :known-at (:known-at target)
                                                                      :regime-at-issue nil}
                                                               issued (assoc :lp issued)
                                                               ;; bnm-56c.27: the climatology it was issued at
                                                               (and clim (:clim-learn spec)) (assoc :clim clim)
                                                               ;; bnm-56c.22: the offsets issued over, learned at
                                                               (and (= :mixture (:learn-offsets spec)) (< 1 (count os))) (assoc :os os))))))]
                   [st
                    (into forecasts (for [{:keys [place p target]} made]
                                              {:scale scale :place place :origin t :p p :y (:y target)}))
                    (update weights k (fnil conj [])
                            [t (cond-> (d/weights live)
                                 ;; :group-shrink: each group's scale beside the weights
                                 (and (:group-shrink spec) (get-in model [:models :all :scales]))
                                 (merge (zipmap (map #(keyword "scale" (name (first %)))
                                                     (shrink-groups (layout spec scale level-ix)
                                                                    (get-in model [:models :all :inputs])
                                                                    (set (map (fn [i] (inc (long i))) (get-in spec [:core scale])))))
                                                (d/group-scales (get-in model [:models :all])))))])
                    (if (:trace spec)
                      (into trace (for [{:keys [place reads x p o issued]} made]
                                            (cond-> {:scale scale :level level :place place :origin t :reads reads
                                                     :learned (map #(select-keys % [:known-at]) ready)}
                                              (= :inputs (:trace spec)) (assoc :x x :p p :o o)
                                              (and (= :inputs (:trace spec)) (= :issue (:evidence-at spec))) (assoc :lp issued))))
                      trace)]))
               [st forecasts weights trace]
               (for [s due level-ix (range (count levels))] [s level-ix]))
              ;; the levels share what they learned this period
              st (if-let [s (:coupling spec)]
                   (reduce (fn [st sc] (update st :models couple-levels (:name sc) (map first levels) s
                                               (:nested (layout spec (:name sc) 0))))
                           st due)
                   st)]
          {:t (inc t) :st st :forecasts forecasts :weights weights :trace trace})))

(defn relation-effects
  "Each relation input's effect on the node's log odds per raw unit, at
  `scale` and level index `level-ix`, from a lattice state: every model's
  coefficient on it weighted by the model averaging, over the input's
  running spread (what bnm.world runs forward). A vector over the
  relation inputs, in their order."
  [spec state scale level-ix]
  (let [[level _] (nth (:levels spec) level-ix)
        k [scale level]
        lay (layout spec scale level-ix)
        rel-ix (vec (sort (mapcat val (:parts lay))))
        g-width (if (contains? (without spec scale) :gauges) 0 (get-in spec [:gauge-width scale] 0))
        avg (get-in state [:st :models k])
        {:keys [n s ss ns]} (get-in state [:st :flow-std k])
        sd (fn [i] (let [j (- i (inc g-width))
                         nj (double (max 1 (if ns (nth ns j) n)))
                         m (/ (nth s j) nj)]
                     (Math/sqrt (Math/max 1e-12 (- (/ (nth ss j) nj) (* m m))))))]
    (vec (for [i rel-ix]
           (/ (reduce + 0.0 (for [[g mdl] (:models avg)
                                  :let [ins (:inputs mdl) pos (.indexOf ^java.util.List (vec ins) i)]
                                  :when (>= pos 0)]
                              (* (get (d/weights avg) g 0.0) (nth (d/means mdl) pos))))
              (sd i))))))

(defn weights
  "The model-averaging weights of the node at `scale` and `level` after its
  last period, from the result of `run`."
  [result scale level]
  (second (last (get-in result [:weights [scale level]]))))

(defn model
  "The model reading `group` in the average at `scale` and `level`, after
  the run."
  [result scale level group]
  (get-in result [:models [scale level] :models group]))
