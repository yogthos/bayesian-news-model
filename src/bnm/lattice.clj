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
            [augur.hier :as hier]
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

(defn hermite-rule
  "n-point Gauss-Hermite quadrature for the standard normal: [[node
  weight] ...], from Newton on the orthonormal Hermite recurrence
  (Numerical Recipes' gauher), the nodes scaled by sqrt 2 and the
  weights by 1/sqrt pi."
  [n]
  (let [n (long n)
        pim4 0.7511255444649425
        m (quot (inc n) 2)
        root (fn [z]
               (loop [z (double z) it 0]
                 (let [[p1 p2] (loop [j 1 p1 pim4 p2 0.0]
                                 (if (> j n) [p1 p2]
                                     (recur (inc j)
                                            (- (* z (Math/sqrt (/ 2.0 j)) p1) (* (Math/sqrt (/ (dec j) (double j))) p2))
                                            p1)))
                       pp (* (Math/sqrt (* 2.0 n)) (double p2))
                       z' (- z (/ (double p1) pp))]
                   (if (or (< (Math/abs (- z' z)) 1e-14) (> it 100))
                     [z' (/ 2.0 (* pp pp))]
                     (recur z' (inc it))))))
        xs (loop [i 0 acc []]
             (if (= i m) acc
                 (let [z (case i
                           0 (- (Math/sqrt (+ (* 2 n) 1)) (* 1.85575 (Math/pow (+ (* 2 n) 1) -0.16667)))
                           1 (let [z (first (acc 0))] (- z (/ (* 1.14 (Math/pow n 0.426)) z)))
                           2 (- (* 1.86 (first (acc 1))) (* 0.86 (first (acc 0))))
                           3 (- (* 1.91 (first (acc 2))) (* 0.91 (first (acc 1))))
                           (- (* 2.0 (first (acc (dec i)))) (first (acc (- i 2)))))]
                   (recur (inc i) (conj acc (root z))))))]
    (vec (for [[x w] (concat xs (map (fn [[x w]] [(- x) w]) (if (odd? n) (butlast xs) xs)))]
           [(* (Math/sqrt 2.0) x) (/ w (Math/sqrt Math/PI))]))))

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
  input. (standardizer :stable) also keeps each input's running mean and
  sum of squared deviations (Welford), and `standardize` reads those
  (bnm-9fg): the one-pass spread sqrt(ss/n - m^2) of an input constant
  over everything seen is rounding noise, about 1e-8 times its level,
  which the 1e-9 guard let through, so the next value landed on the cap."
  ([] {:n 0 :s nil :ss nil})
  ([mode]
   (case mode
     nil (standardizer)
     :stable {:n 0 :s nil :ss nil :stable true}
     (throw (ex-info "bnm.lattice: :flow-std takes :stable" {:flow-std mode})))))

(defn observe
  "The standardizer after seeing the vector xs; an unknown input (nil)
  does not count toward its mean and spread."
  [{:keys [n s ss ns stable mean m2]} xs]
  (let [k (count xs)
        z (fn [v] (or v (vec (repeat k 0.0))))
        ns' (mapv (fn [c x] (if (nil? x) c (inc c))) (or ns (vec (repeat k (long n)))) xs)]
    (cond-> {:n (inc n)
             :ns ns'
             :s (mapv (fn [a x] (if (nil? x) a (+ a x))) (z s) xs)
             :ss (mapv (fn [a x] (if (nil? x) a (+ a (* x x)))) (z ss) xs)}
      stable (assoc :stable true
                    :mean (mapv (fn [m x c] (if (nil? x) m (+ m (/ (- x m) (double c))))) (z mean) xs ns')
                    :m2 (mapv (fn [q m x c] (if (nil? x) q (let [m' (+ m (/ (- x m) (double c)))] (+ q (* (- x m) (- x m')))))) (z m2) (z mean) xs ns')))))

(defn spread
  "Input i's mean and spread in standardizer `sd` (ni values seen), or nil
  while it has none: under :stable from Welford's sums, with a spread
  under 1e-9 or under 1e-8 of the level counted as none."
  [{:keys [s ss stable mean m2]} i ni]
  (let [ni (double ni)]
    (if stable
      (let [m (nth mean i)
            sd (Math/sqrt (Math/max 0.0 (/ (nth m2 i) ni)))]
        (when (> sd (Math/max 1e-9 (* 1e-8 (Math/abs (double m))))) [m sd]))
      (let [m (/ (nth s i) ni)
            sd (Math/sqrt (Math/max 0.0 (- (/ (nth ss i) ni) (* m m))))]
        (when (> sd 1e-9) [m sd])))))

(defn standardize
  "xs against the mean and spread of everything observed before, capped at
  5; 0 until ten of an input have been seen (its scale is not known yet),
  and 0 for an unknown input (nil): the mean."
  [{:keys [n ns] :as sd} xs]
  (if (< n 10)
    (mapv (constantly 0.0) xs)
    (vec (map-indexed (fn [i x]
                        (let [ni (if ns (nth ns i) n)]
                          (if (or (nil? x) (< ni 10))
                            0.0
                            (if-let [[m d] (spread sd i ni)]
                              (Math/max -5.0 (Math/min 5.0 (/ (- x m) d)))
                              0.0))))
                      xs))))

(defn- without
  "The groups dropped at `scale`: the spec's :without everywhere, and its
  :without-at for this scale."
  [spec scale]
  (into (set (:without spec)) (get-in spec [:without-at scale])))


(defn frailty-only?
  "Whether `scale` is a :frailty-only scale of `spec` (recursive step 4,
  bnm-56c.35): its nodes filter the frailty and issue nothing."
  [spec scale]
  (boolean (some #(and (= scale (:name %)) (:frailty-only %)) (:scales spec))))

(defn- layout
  "The input layout of a node: {:width n :groups {group [index...]} :order}.
  A :frailty-only node reads the intercept alone: its base model is what
  its frailty is filtered against."
  [spec scale level-ix]
  (if (frailty-only? spec scale)
    {:width 1 :finer [] :coarser [] :strain-ix nil :order [:base] :groups {:base []} :parts {} :pool [] :nested #{}}
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
     :nested (if nest? (set (keys rel-groups)) #{})})))

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

(defn- per-place?
  "Whether a weighting is :temper at :power :per-place; any other power
  that is not a number is refused (augur checks a number's range)."
  [wt]
  (when (= :temper (:rule wt))
    (let [pw (:power wt)]
      (cond (= :per-place pw) true
            (number? pw) false
            :else (throw (ex-info "bnm.lattice: :temper's :power takes a number in (0, 1] or :per-place" wt))))))

(defn per-place-power
  "The power a period's evidence is tempered to under :power :per-place:
  the origins its resolved rows were issued at over their number, so each
  origin's rows (one per place) weigh as one row. 1 with none."
  [ready]
  (if (seq ready)
    (/ (double (count (distinct (map :origin ready)))) (count ready))
    1.0))

(defn named-prior
  "{input-index [mean variance]} of the spec's :named-prior at `scale`
  (bnm-56c.36): {scale {name [mean variance]}}, each name one of the
  scale's :gauge-names ({scale [name ...]}, one per gauge column), so a
  gauge's coefficient prior is set by what it is, not where it sits. Every
  model reading the input starts there (the all-inputs model's too, in
  place of :shrink). nil without one or with the gauges dropped; an
  unknown name, names that do not match the gauges' width, or a variance
  that is not positive are refused."
  [spec scale]
  (when-let [np (not-empty (get-in spec [:named-prior scale]))]
    (let [names (get-in spec [:gauge-names scale])
          width (get-in spec [:gauge-width scale] 0)]
      (when (not= (count names) width)
        (throw (ex-info "bnm.lattice: :gauge-names must name every gauge" {:scale scale :names names :width width})))
      (when-not (contains? (without spec scale) :gauges)
        (let [pos (zipmap names (range))]
          (into {} (for [[n [m v :as mv]] np]
                     (let [i (pos n)]
                       (when-not i
                         (throw (ex-info "bnm.lattice: :named-prior names no gauge" {:scale scale :name n :names names})))
                       (when-not (and (number? m) (number? v) (pos? (double v)))
                         (throw (ex-info "bnm.lattice: :named-prior takes [mean variance], the variance positive" {:scale scale :name n :prior mv})))
                       ;; the gauges sit after the intercept
                       [(inc (long i)) [(double m) (double v)]]))))))))

(defn- node-model
  "A node's model average: one dynamic logistic per group of its inputs and
  one over all of them, each reading the intercept and the spec's :core
  inputs (what is known, read by every model). A coefficient starts at zero
  with variance 1 (4 for the intercept), the all-inputs model's non-core
  ones at the spec's :shrink, unless the spec's :prior gives {input-index
  [mean variance]} for the node."
  [spec scale level {:keys [discount per]} {:keys [groups order nested] :as lay}]
  (let [prior (merge (when-let [[m v] (:contradiction-prior spec)]
                       (into {} (for [i (:strain-ix lay)] [i [m v]])))
                     (or (when-let [f (:prior spec)] (f scale level lay)) {})
                     (named-prior spec scale))
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
                        ;; :temper :power :per-place: built at 1, set each period
                        ;; by `per-place-power` before the node learns
                        wt (if (per-place? wt) (assoc wt :power 1.0) wt)
                        ;; :evidence (ranked increments): the base model is the
                        ;; reference, a nested link set against the all-inputs model
                        wt (if (= :evidence (:rule wt))
                             (merge {:base :base :parents (zipmap (filter models nested) (repeat :all))} wt)
                             wt)
                        pr (not-empty (select-keys (:weight-prior spec) (keys models)))]
                    (cond-> {:forget (if-let [h (:forget-half-life spec)]
                                       ;; bnm-e1y: the averaging's memory in calendar time,
                                       ;; a half-life of h years whatever the node's period
                                       (do (when-not (and (number? h) (pos? (double h)))
                                             (throw (ex-info "bnm.lattice: :forget-half-life takes a positive number of years" {:forget-half-life h})))
                                           (Math/pow 0.5 (/ (double per) (* 12.0 (double h)))))
                                       (:forget spec))}
                      wt (assoc :weighting wt) pr (assoc :prior pr)
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

(defn- shift-shock
  "A pending row's offsets (:o, each of :os) and its models' issued
  predictives moved by `delta`: under a slope on exposure the place's half
  variance, which the shared state's mean does not carry (it differs by
  place), goes into the row's own linear predictor."
  [row delta]
  (cond-> (reanchor row delta)
    (:issued row) (update :issued (fn [iss] (into {} (for [[g pr] iss] [g (update pr :f + delta)]))))))

;; --- the frailty: one common shock per node and origin (recursive step 3b) ----------

(def frailty-defaults
  "The frailty's grid and reference when the spec's :frailty leaves them out."
  {:rhos [0.0 0.5 0.8 0.95] :sds [0.0 0.25 0.5 1.0] :from :base :prior :own :penalty 2.0
   :level-sds [0.0] :level-penalty 2.0 :exposure-sds [0.0] :exposure-penalty 2.0
   :cross [0.0 0.5 1.0 1.5] :gauges-from :all})

(def frailty-wide-sds
  "The grid :sds :wide names (bnm-02u, docs/RECURSIVE.md step 3b): the
  default's carried past 1, where every year node chose its top, to 3,
  each non-zero sd at the same price."
  [0.0 0.25 0.5 1.0 1.5 2.0 3.0])

(defn frailty-config
  "The spec's :frailty with its defaults, checked; nil without one. A map
  of :rhos (persistences in [0, 1)), :sds (stationary sds, at least 0),
  :from (the model whose issue-time predictive the state is filtered
  against, the base by default) and :prior (:own, the node's filter run
  forward, or :parent, rho times the coarser level's state) and :penalty,
  the nats a state of non-zero size must earn before it is chosen (2.0,
  Akaike's price of its two parameters, as augur.hier/states).
  :level-sds (bnm-56c.20.1) adds a persistent level beside the transient
  state: a random walk whose sd a period is chosen from the grid with the
  rest, a non-zero one paying :level-penalty nats (2.0, a second state's
  price: at 1.0 a walk alone took the base's early learning for a level).
  The default, [0.0], is the transient alone.
  :exposure-sds (bnm-56c.20.2) adds a slope on the places' exposure (the
  spec's :exposure, each column standardized across the node's places at
  the origin): one loading u_j per column, AR(1) with the transient's
  persistence and a stationary sd chosen from the grid, a non-zero one
  paying :exposure-penalty nats (2.0). The place's shock is then z + u.e,
  filtered jointly once per origin. The default, [0.0], is no slope.
  :prior :finer with :source, a scale of the spec (bnm-56c.35, recursive
  step 4): every node at a scale coarser than the source issues with kappa
  times the source scale's state at its level (filtered before the node
  issues, from rows known by t, run forward at the source's own period),
  kappa chosen from :cross ([0.0 0.5 1.0 1.5]) with the rest of the grid,
  a non-zero one paying :penalty; kappa 0 is the node's own prior, so
  :cross [0.0] is :prior :own. The source and finer scales use their own.
  :gauges {scale [name ...]} (bnm-v1y, recursive step 5a): at that scale
  the named world gauges (of the scale's :gauge-names, each with its
  theory prior in :named-prior) leave every model and set the shock's
  mean: the rows of origin t see z_t + beta.g_t, beta a static state
  filtered with z once per origin from its theory prior
  (`frailty-gauges`). Only the candidates with an sd above 0 are kept
  there: with no shock beyond the gauges beta would learn once per place.
  Only under :prior :own, with no level or slope. There the state is
  filtered against :gauges-from's predictive (:all, the all-inputs model,
  by default): against the base, which omits what ranks the places, a
  common shift is attenuated on the average log odds (the logistic's
  omitted-heterogeneity bias), and beta with it.
  :centre (bnm-0fe, docs/RECURSIVE.md step 3f): :odds, the default, takes
  half the transient's stationary variance from its mean, so the expected
  odds multiplier is one (a rate's, for rare events); :rate solves each
  place's own shift so that at its stationary prior the transient leaves
  the probability issued as it is (`rate-shift`), in the forecast, the
  rows' offsets and the filter alike."
  [spec]
  (when-let [f (:frailty spec)]
    (when-not (map? f)
      (throw (ex-info "bnm.lattice: :frailty takes a map of :rhos :sds :from :prior" {:frailty f})))
    (when-let [bad (seq (remove #{:rhos :sds :from :prior :penalty :level-sds :level-penalty :exposure-sds :exposure-penalty
                                  :source :cross :gauges :gauges-from :centre} (keys f)))]
      (throw (ex-info (str "bnm.lattice: :frailty names no option " (vec bad)) {:frailty f})))
    (when-not (contains? #{nil :odds :rate} (:centre f))
      (throw (ex-info "bnm.lattice: :frailty :centre takes :odds or :rate" {:centre (:centre f)})))
    (when (and (keyword? (:sds f)) (not= :wide (:sds f)))
      (throw (ex-info "bnm.lattice: :frailty :sds takes sds of at least 0, or :wide" {:sds (:sds f)})))
    (let [c (cond-> (merge frailty-defaults f) (= :wide (:sds f)) (assoc :sds frailty-wide-sds))
          {:keys [rhos sds from prior penalty level-sds level-penalty exposure-sds exposure-penalty source cross]} c
          nums? (fn [xs ok?] (and (sequential? xs) (seq xs) (every? #(and (number? %) (ok? (double %))) xs)))]
      (when-not (nums? rhos #(and (<= 0.0 %) (< % 1.0)))
        (throw (ex-info "bnm.lattice: :frailty :rhos takes persistences in [0, 1)" {:rhos rhos})))
      (when-not (nums? sds #(<= 0.0 %))
        (throw (ex-info "bnm.lattice: :frailty :sds takes sds of at least 0" {:sds sds})))
      (when-not (keyword? from)
        (throw (ex-info "bnm.lattice: :frailty :from names a model's group" {:from from})))
      (when-not (and (number? penalty) (<= 0.0 (double penalty)))
        (throw (ex-info "bnm.lattice: :frailty :penalty takes nats of at least 0" {:penalty penalty})))
      (when-not (#{:own :parent :finer} prior)
        (throw (ex-info "bnm.lattice: :frailty :prior takes :own, :parent or :finer" {:prior prior})))
      (when (and source (not= :finer prior))
        (throw (ex-info "bnm.lattice: :frailty :source goes with :prior :finer" {:source source})))
      (when (= :finer prior)
        (let [names (mapv :name (:scales spec))
              ix (.indexOf ^java.util.List names source)]
          (when-not (and (keyword? source) (<= 0 ix) (< ix (dec (count names))))
            (throw (ex-info "bnm.lattice: :frailty :prior :finer takes a :source scale finer than another of the spec's" {:source source :scales names})))
          (when-not (nums? cross #(<= 0.0 %))
            (throw (ex-info "bnm.lattice: :frailty :cross takes coefficients of at least 0" {:cross cross})))
          (when (or (some pos? (map double level-sds)) (some pos? (map double exposure-sds)))
            (throw (ex-info "bnm.lattice: :frailty :prior :finer takes no level or slope" {:level-sds level-sds :exposure-sds exposure-sds})))))
      (when-not (nums? level-sds #(<= 0.0 %))
        (throw (ex-info "bnm.lattice: :frailty :level-sds takes sds of at least 0" {:level-sds level-sds})))
      (when-not (and (number? level-penalty) (<= 0.0 (double level-penalty)))
        (throw (ex-info "bnm.lattice: :frailty :level-penalty takes nats of at least 0" {:level-penalty level-penalty})))
      (when-not (nums? exposure-sds #(<= 0.0 %))
        (throw (ex-info "bnm.lattice: :frailty :exposure-sds takes sds of at least 0" {:exposure-sds exposure-sds})))
      (when-not (and (number? exposure-penalty) (<= 0.0 (double exposure-penalty)))
        (throw (ex-info "bnm.lattice: :frailty :exposure-penalty takes nats of at least 0" {:exposure-penalty exposure-penalty})))
      (when-let [gs (:gauges c)]
        (when-not (and (map? gs) (every? (fn [[sc ns]] (and (keyword? sc) (sequential? ns) (seq ns) (every? keyword? ns) (apply distinct? ns))) gs))
          (throw (ex-info "bnm.lattice: :frailty :gauges takes {scale [gauge name ...]}" {:gauges gs})))
        (when (seq gs)
          (when-not (= :own prior)
            (throw (ex-info "bnm.lattice: :frailty :gauges goes with :prior :own" {:prior prior})))
          (when (or (some pos? (map double level-sds)) (some pos? (map double exposure-sds)))
            (throw (ex-info "bnm.lattice: :frailty :gauges takes no level or slope" {:level-sds level-sds :exposure-sds exposure-sds})))
          (when-not (some pos? (map double sds))
            (throw (ex-info "bnm.lattice: :frailty :gauges needs an sd above 0 in :sds" {:sds sds})))
          (when-not (keyword? (:gauges-from c))
            (throw (ex-info "bnm.lattice: :frailty :gauges-from names a model's group" {:gauges-from (:gauges-from c)})))))
      (let [exposure? (boolean (some pos? (map double exposure-sds)))]
        (when (and exposure? (not (and (:exposure spec) (pos? (long (or (:exposure-width spec) 0))))))
          (throw (ex-info "bnm.lattice: :frailty :exposure-sds needs the spec's :exposure and :exposure-width" {:exposure-sds exposure-sds})))
        ;; the grid, no slope, no level and smallest sd first, so a tie keeps
        ;; the smaller state; a candidate with no level carries no :lsd and one
        ;; with no slope no :usd, each computed as before the option existed
        (assoc c :level? (boolean (some pos? (map double level-sds)))
               :exposure? exposure?
               :width (when exposure? (long (:exposure-width spec)))
               :grid (vec (concat
                           (for [u (sort (distinct (map double exposure-sds)))
                                 l (sort (distinct (map double level-sds)))
                                 s (sort (distinct (map double sds))) r (sort (distinct (map double rhos)))]
                             (cond-> {:rho r :sd s :price (if (pos? s) (double penalty) 0.0)}
                               (pos? l) (-> (assoc :lsd l) (update :price + (double level-penalty)))
                               (pos? u) (-> (assoc :usd u) (update :price + (double exposure-penalty)))))
                           ;; :prior :finer: the source's state times kappa, after the
                           ;; node's own candidates (kappa 0) so a tie keeps its own;
                           ;; its own persistence unused, rho 0
                           (when (= :finer prior)
                             (for [k (sort (distinct (map double cross))) :when (pos? k)
                                   s (sort (distinct (map double sds)))]
                               {:rho 0.0 :sd s :cross k :price (double penalty)})))))))))

(defn frailty-gauges
  "The world gauges that set the frailty's mean at `scale` (bnm-v1y,
  :frailty :gauges): {:names :ix (their positions in a node's inputs, after
  the intercept) :b0 :bv (their theory priors, from :named-prior)}, or nil.
  A name the scale's :gauge-names lacks, one with no :named-prior (the
  prior is what an origin's single observation is weighed against), or
  the gauges dropped, are refused."
  [spec scale]
  (when-let [ns (seq (get-in spec [:frailty :gauges scale]))]
    (let [names (get-in spec [:gauge-names scale])
          pos (zipmap names (range))
          np (get-in spec [:named-prior scale])]
      (when (contains? (without spec scale) :gauges)
        (throw (ex-info "bnm.lattice: :frailty :gauges with the gauges dropped" {:scale scale})))
      (when (not= (count names) (get-in spec [:gauge-width scale] 0))
        (throw (ex-info "bnm.lattice: :gauge-names must name every gauge" {:scale scale :names names})))
      (doseq [n ns]
        (when-not (pos n)
          (throw (ex-info "bnm.lattice: :frailty :gauges names no gauge" {:scale scale :name n :names names})))
        (when-not (np n)
          (throw (ex-info "bnm.lattice: :frailty :gauges takes a gauge with a :named-prior" {:scale scale :name n}))))
      {:names (vec ns)
       :ix (mapv #(inc (long (pos %))) ns)
       :b0 (mapv #(double (first (np %))) ns)
       :bv (mapv #(double (second (np %))) ns)})))

(def hermite-fine
  "Twenty-point Gauss-Hermite for the standard normal: a frailty state of
  sd above 1 (bnm-02u, the wide grid), where five points err by up to 10%
  in the forecast at sd 3 and twenty by under 0.1%."
  (hermite-rule 20))

(defn- fine-state?
  "Whether a frailty state's stationary sd is above 1, the default grid's
  top: the five-point rule and Laplace serve up to 1 (errors under 2e-4
  relative and 0.01 nats); above it the twenty-point rule (bnm-02u)."
  [sd]
  (> (double (or sd 0.0)) 1.0))

(defn with-frailty
  "Offsets [[w o] ...] with a shared state z ~ N(m, v) added, by the
  five-point Hermite rule, or the twenty-point one when the state's :sd
  is above 1 (bnm-02u); the offsets moved by m when v is 0, unchanged
  when both are."
  [os {:keys [m v sd]}]
  (let [m (double (or m 0.0)) v (double (or v 0.0))
        rule (if (fine-state? sd) hermite-fine hermite)]
    (cond (and (zero? m) (zero? v)) os
          (zero? v) (mapv (fn [[w o]] [w (+ (double o) m)]) os)
          :else (let [s (Math/sqrt v)]
                  (vec (for [[w o] os [z hw] rule] [(* (double w) hw) (+ (double o) m (* s z))]))))))

;; --- bnm-0fe, :frailty {:centre :rate}: the shock centred so its expected effect on the
;; rate (the probability) is one, not on the odds (docs/RECURSIVE.md step 3f) ---

(defn logistic-curve
  "The probability of a row whose linear predictor is N(f, q), moved by d:
  d -> [p dp/dd], MacKay's probit approximation as augur's (and
  `shared-ll`'s) logistic."
  [f q]
  (let [f (double f)
        k (/ 1.0 (Math/sqrt (+ 1.0 (/ (* Math/PI (double (or q 0.0))) 8.0))))]
    (fn [d] (let [p (m/expit (* k (+ f (double d))))] [p (* k p (- 1.0 p))]))))

(defn forecast-curve
  "The probability a node issues for inputs x over offsets `os` with its
  averaged models `live`, moved by d: d -> [p dp/dd]. Under a mixture of
  logistic models from each model's predictive moments, computed once
  (the offset moves only their mean, so this is augur's predict at o + d);
  otherwise (a pooled :evidence or :synth rule) augur's predict itself,
  its slope by a central difference."
  [live x os]
  (let [models (:models live)
        w (d/weights live)
        mixture? (and (= :averaged (:type live)) (not (#{:evidence :synth} (:rule (:weighting live))))
                      (every? #(= :logistic (:type %)) (vals models)))]
    (if mixture?
      (let [parts (vec (for [[ow o] os [g mdl] models
                             :let [wg (double (get w g 0.0))]
                             :when (pos? wg)
                             :let [{:keys [f q]} (d/predictive mdl x (double o))]]
                         [(* (double ow) wg) (double f) (/ 1.0 (Math/sqrt (+ 1.0 (/ (* Math/PI (double q)) 8.0))))]))]
        (fn [dd] (let [dd (double dd)]
                   (reduce (fn [[p dp] [wt f k]]
                             (let [pi (m/expit (* k (+ f dd)))]
                               [(+ p (* wt pi)) (+ dp (* wt k pi (- 1.0 pi)))]))
                           [0.0 0.0] parts))))
      (let [pr (fn [dd] (reduce + (for [[ow o] os] (* (double ow) (d/predict live x (+ (double o) (double dd)))))))]
        (fn [dd] [(pr dd) (/ (- (pr (+ dd 1e-5)) (pr (- dd 1e-5))) 2e-5)])))))

(defn rate-shift
  "The shift c that centres a frailty on the rate: with the row's
  probability `curve` (d -> [p dp]), a part of the shift not centred,
  N(rm, rv) (the gauges' loadings, a persistent level: means, their
  uncertainty integrated as the models' own), and the centred transient's
  stationary variance vz, E[curve(rm + c + sqrt(rv + vz) Z)] =
  E[curve(rm + sqrt(rv) Z)]: at its stationary prior the shock leaves the
  forecast as it is. Integrated by the rule the forecast is (`with-frailty`
  at sd `sd`); Newton kept inside [rm - vz/2 - 1, rm + vz/2 + 1], which
  holds the root (the odds' half variance and its mirror are the limits).
  0 when vz is."
  ^double [curve rm rv vz sd]
  (let [rm (double rm) rv (double rv) vz (double vz)]
    (if (<= vz 0.0)
      0.0
      (let [rule (if (fine-state? sd) hermite-fine hermite)
            mean-over (fn [mu v] (if (zero? v)
                                   (curve mu)
                                   (let [s (Math/sqrt v)]
                                     (reduce (fn [[p dp] [x hw]]
                                               (let [[pi di] (curve (+ mu (* s x)))]
                                                 [(+ p (* hw pi)) (+ dp (* hw di))]))
                                             [0.0 0.0] rule))))
            target (double (first (mean-over rm rv)))
            g (fn [c] (let [[p dp] (mean-over (+ rm c) (+ rv vz))] [(- (double p) target) (double dp)]))]
        (loop [lo (- (* -0.5 vz) 1.0) hi (+ (* 0.5 vz) 1.0) c 0.0 i 0]
          (let [[gc dc] (g c)
                [lo hi] (if (pos? gc) [lo c] [c hi])
                nc (- c (/ gc (Math/max dc 1e-300)))
                nc (if (and (< lo nc) (< nc hi)) nc (* 0.5 (+ lo hi)))]
            (if (or (< (Math/abs (double gc)) 1e-13) (>= i 100) (< (Math/abs (- nc c)) 1e-14))
              c
              (recur lo hi nc (inc i)))))))))

(defn shared-ll
  "The log marginal of logistic rows {:f :q :y :w} sharing a shock z ~
  N(m, v) added to each: augur.hier/shared-marginal's Laplace value for a
  state of `sd` 1 or less, as before; above 1 (bnm-02u) the integral by
  the twenty-point rule about Laplace's mode and curvature (adaptive
  Gauss-Hermite), each row's likelihood as augur's (MacKay's probit
  approximation over q). Laplace errs by up to 0.07 nats an origin at sd
  3, toward the larger sd on quiet origins."
  ^double [rows m v sd]
  (let [{z0 :z v0 :v ll :ll} (hier/shared-marginal :logistic rows m v)
        m (double m) v (double v)]
    (if (or (not (fine-state? sd)) (zero? v) (empty? rows))
      (double ll)
      (let [rows (mapv (fn [{:keys [f q y w]}]
                         [(double f) (/ 1.0 (Math/sqrt (+ 1.0 (/ (* Math/PI (double (or q 0.0))) 8.0))))
                          (if (if (number? y) (pos? (double y)) (boolean y)) 1.0 0.0) (double (or w 1.0))])
                       rows)
            lik (fn [z] (reduce (fn [acc [f k yy w]]
                                  (let [e (* k (+ f z))]
                                    (+ acc (* w (- (* yy e) (if (> e 0.0) (+ e (Math/log1p (Math/exp (- e)))) (Math/log1p (Math/exp e))))))))
                                0.0 rows))
            s (Math/sqrt (double v0))
            ;; z = z0 + s x: the integrand over the standard normal's weights
            terms (for [[x w] hermite-fine
                        :let [z (+ (double z0) (* s x))
                              d (- z m)]]
                    (+ (Math/log w) (lik z) (- (/ (* d d) (* 2.0 v))) (* -0.5 (Math/log (* 2.0 Math/PI v)))
                       (* 0.5 x x) (* 0.5 (Math/log (* 2.0 Math/PI))) (Math/log s)))
            top (apply max terms)]
        (+ top (Math/log (reduce + (map #(Math/exp (- % top)) terms))))))))

(declare slope-fresh)

(defn- frailty-fresh
  "A node's frailty before its first resolved origin: one filter per
  grid point at its stationary prior, scored 0. Without `cross?` (a node
  that takes no finer source) the :prior :finer candidates are left out.
  With `gp` (`frailty-gauges`, bnm-v1y) each candidate carries beta, the
  gauges' loadings, at their theory prior (:b0 :bv), and those with an sd
  of 0 are left out."
  ([cfg] (frailty-fresh cfg false nil))
  ([cfg cross?] (frailty-fresh cfg cross? nil))
  ([{:keys [grid width]} cross? gp]
  {:cands (mapv (fn [{:keys [sd lsd usd] :as g}]
                  (cond
                    ;; a slope on exposure or the gauges' loadings: the joint
                    ;; state as a vector and its variance
                    (or usd gp) (let [g (cond-> g gp (assoc :b0 (:b0 gp) :bv (:bv gp)))]
                                  (merge (assoc g :t nil :score 0.0) (slope-fresh g width)))
                    :else (cond-> (assoc g :m 0.0 :v (* sd sd) :t nil :score 0.0)
                            ;; the level starts at 0, known: the base's level is the start
                            lsd (assoc :lm 0.0 :lv 0.0 :c 0.0))))
                (cond->> (if cross? grid (vec (remove :cross grid)))
                  gp (filterv #(pos? (double (:sd %))))))
   :filtered (sorted-map)}))

(defn- vec-cand?
  "Whether a candidate's state is a vector: a slope on exposure or the
  gauges' loadings (bnm-v1y)."
  [c]
  (boolean (or (:usd c) (:b0 c))))

(defn- chosen
  "The index of the candidate with the best prequential score less its
  price (the first, smallest sd, on a tie)."
  [cands]
  (let [net (fn [c] (- (double (:score c)) (double (:price c 0.0))))]
    (first (reduce (fn [[bi bs] [i c]] (if (> (net c) (double bs)) [i (net c)] [bi bs]))
                   [0 (net (first cands))] (map-indexed vector cands)))))

(defn- half-var
  "Half a candidate's stationary variance: what the shift applied is less
  than its state, so the expected rate multiplier is one (augur.hier/states)."
  ^double [c]
  (* 0.5 (double (:sd c)) (double (:sd c))))

(defn- cand-ahead
  "A candidate's state run forward to origin t, `per` months a period."
  [{:keys [m v t] :as c} to per]
  (if (nil? t)
    {:m 0.0 :v (* (:sd c) (:sd c))}
    (hier/states-predict {:m m :v v} c (Math/max 0.0 (/ (double (- to t)) per)))))

(defn- level-ahead
  "A level candidate's joint state (transient :m :v, level :lm :lv, their
  covariance :c) run forward to origin t: the transient as `cand-ahead`,
  the level a random walk (its variance up lsd^2 a period), the covariance
  decaying with the transient."
  [{:keys [t lsd] :as c} to per]
  (let [zt (cand-ahead c to per)]
    (if (nil? t)
      (assoc zt :lm 0.0 :lv 0.0 :c 0.0)
      (let [h (Math/max 0.0 (/ (double (- to t)) per))]
        (assoc zt :lm (double (:lm c))
               :lv (+ (double (:lv c)) (* (double lsd) (double lsd) h))
               :c (* (Math/pow (double (:rho c)) h) (double (:c c))))))))

(defn- level-applied
  "The shift a level candidate's joint state applies, [m v]: the level as
  it is plus the transient less half its stationary variance; with
  `rate?` (bnm-0fe, :centre :rate) the transient as it is, each row
  centred on its own (`rate-shift`)."
  ([c j] (level-applied c j false))
  ([c {:keys [m v lm lv] cv :c} rate?]
   [(+ (double lm) (if rate? (double m) (- (double m) (half-var c)))) (+ (double lv) (double v) (* 2.0 (double cv)))]))

(defn- level-step
  "A level candidate's joint state updated by one origin's rows: the sum
  the rows see is updated by Laplace (augur.hier/states-step), and the
  level and transient each take their share of the move by their
  covariance with the sum (the Gaussian projection)."
  ([c prior obs] (level-step c prior obs false))
  ([c prior obs rate?]
  (let [[mu vu] (level-applied c prior rate?)]
    (if (zero? vu)
      prior
      (let [{u :m vu' :v} (hier/states-step {:m mu :v vu} obs {:kind :logistic})
            {:keys [m v lm lv] cv :c} prior
            kl (/ (+ (double lv) (double cv)) vu)
            kz (/ (+ (double v) (double cv)) vu)
            d (- (double u) mu)
            shrink (- vu (double vu'))]
        {:m (+ (double m) (* kz d)) :v (- (double v) (* kz kz shrink))
         :lm (+ (double lm) (* kl d)) :lv (- (double lv) (* kl kl shrink))
         :c (- (double cv) (* kl kz shrink))})))))

;; --- the slope on exposure (bnm-56c.20.2): the joint state [z (L) u_1 .. u_J] ---

(defn- slope-diag
  "A slope candidate's per-dimension [factor noise] over h periods: z and
  each u_j AR(1) at the candidate's rho (their stationary sds :sd and
  :usd), the level, when there is one, a random walk, the gauges'
  loadings (bnm-v1y) static."
  [{:keys [rho sd lsd usd b0]} width h]
  (let [rh (Math/pow (double rho) (double h))
        ar (fn [s] [rh (* (double s) (double s) (- 1.0 (* rh rh)))])]
    (vec (concat [(ar sd)]
                 (when lsd [[1.0 (* (double lsd) (double lsd) (double h))]])
                 (when usd (repeat width (ar usd)))
                 (repeat (count b0) [1.0 0.0])))))

(defn- slope-fresh
  "A slope candidate's joint state before anything is known: z and each
  u_j at their stationary priors, the level at 0 known."
  [{:keys [sd lsd usd b0 bv]} width]
  (let [vs (vec (concat [(* (double sd) (double sd))] (when lsd [0.0]) (when usd (repeat width (* (double usd) (double usd))))
                        (map double bv)))
        d (count vs)]
    {:mu (vec (concat (repeat (- d (count b0)) 0.0) (map double b0)))
     :P (vec (for [i (range d)] (vec (for [j (range d)] (if (= i j) (vs i) 0.0)))))}))

(defn- slope-ahead
  "A slope candidate's joint state {:mu :P} run forward to origin t (as
  it starts before it has filtered anything)."
  [{:keys [t mu P lsd b0] :as c} to per]
  (if (nil? t)
    {:mu mu :P P}
    (let [fq (slope-diag c (- (count mu) (if lsd 2 1) (count b0)) (Math/max 0.0 (/ (double (- to t)) per)))
          f (mapv first fq)]
      {:mu (mapv * f mu)
       :P (vec (for [i (range (count f))]
                 (vec (for [j (range (count f))]
                        (+ (* (f i) (f j) (double (get-in P [i j]))) (if (= i j) (second (fq i)) 0.0))))))})))

(defn- slope-load
  "The row's loading on a slope candidate's state: 1 on z (and the level),
  its standardized exposures on the u_j, the origin's gauges `g` on beta
  (bnm-v1y)."
  ([c e] (slope-load c e nil))
  ([{:keys [lsd b0]} e g]
   (when (and b0 (not= (count b0) (count g)))
     (throw (ex-info "bnm.lattice: :frailty :gauges read at the wrong width" {:want (count b0) :got (count g)})))
   (vec (concat [1.0] (when lsd [1.0]) (map double e) (when b0 (map double g))))))

(defn- slope-half
  "Half the stationary variance of the place's shift: what the shift
  applied is less than its state, so the expected rate multiplier is one
  at every exposure."
  ^double [{:keys [sd usd]} e]
  (let [usd (double (or usd 0.0))]
    (* 0.5 (+ (* (double sd) (double sd)) (* usd usd (reduce + 0.0 (map #(* (double %) (double %)) e)))))))

(defn frailty-at
  "The shock a place issues with under a frailty prior (`frailty-prior`'s)
  and its standardized exposures e: {:m :v}, and with a slope the loading
  :load and :h, the half variance taken from its mean. Without a slope the
  node's prior as it is."
  [{:keys [mu P cand g centre] :as zp} e]
  (cond
    (not mu) zp
    ;; bnm-0fe, :centre :rate: the state as it is; beside it the part not
    ;; centred (the level, the gauges' loadings: :rm :rv from the joint
    ;; state) and the transient's stationary variance, sd^2 + usd^2 |e|^2
    ;; (:vz), from which the row's own shift is solved (`rate-shift`)
    (= :rate centre)
    (let [a (slope-load cand e g)
          n (count a)
          quad (fn [ix] (reduce + 0.0 (for [i ix j ix] (* (a i) (a j) (double (get-in P [i j]))))))
          rest-ix (vec (concat (when (:lsd cand) [1]) (range (- n (count (:b0 cand))) n)))]
      {:m (reduce + 0.0 (map * a mu))
       :v (quad (range n))
       :rm (reduce + 0.0 (map #(* (a %) (double (mu %))) rest-ix))
       :rv (quad rest-ix)
       :vz (* 2.0 (slope-half cand e))
       :load a :h 0.0})
    :else
    (let [a (slope-load cand e g)
          h (slope-half cand e)]
      {:m (- (reduce + 0.0 (map * a mu)) h)
       :v (reduce + 0.0 (for [i (range (count a)) j (range (count a))] (* (a i) (a j) (double (get-in P [i j])))))
       :load a :h h})))

(defn- standardized-exposures
  "Each place's exposures, every column centred and scaled across the
  node's places at the origin (0 where a column does not vary): u reads
  log odds per sd of exposure, and the slope moves no place's mean."
  [spec scale places t]
  (let [raw (mapv #(mapv double ((:exposure spec) scale % t)) places)
        n (count raw)
        w (long (:exposure-width spec))]
    (when-let [bad (first (remove #(= w (count %)) raw))]
      (throw (ex-info "bnm.lattice: :exposure gave the wrong width" {:scale scale :width w :got (count bad)})))
    (let [mean (mapv (fn [j] (/ (reduce + (map #(% j) raw)) n)) (range w))
          sd (mapv (fn [j] (Math/sqrt (/ (reduce + (map #(let [d (- (% j) (mean j))] (* d d)) raw)) n))) (range w))]
      (zipmap places (for [r raw] (mapv (fn [j] (if (< (sd j) 1e-12) 0.0 (/ (- (r j) (mean j)) (sd j)))) (range w)))))))

(defn frailty-learn
  "A node's frailty after the `ready` rows (each with its :origin, its
  :zc, the prior each candidate issued it with, and its :lp, the models'
  issue-time predictives): origin by origin, each candidate scored by the
  rows' log marginal under the prior it issued them with (the horizon its
  forecasts serve), then filtered: its state run forward one period from
  the last origin it filtered, updated by the rows against the `from`
  model's predictive (augur.hier/states-step). Rows of an origin already
  passed update the current state. Rows issued before the node had a
  resolved outcome are neither scored nor filtered: the base's miss there
  is its uninformed start (bnm-56c.26), not a common shock."
  [{:keys [from level? exposure? prior centre]} fr ready per]
  (let [rate? (= :rate centre)
        ;; bnm-0fe, :centre :rate: each row's own shift under a candidate, from
        ;; the :from model's predictive (the reference the state is filtered
        ;; against), memoized: the base's rows of an origin share theirs
        cen (memoize (fn [f q rm rv vz sd] (rate-shift (logistic-curve f q) rm rv vz sd)))]
  (reduce (fn [{:keys [cands] :as fr} [s rows]]
            (let [obs (mapv (fn [r] (let [{:keys [f q]} (or (get-in r [:lp from])
                                                            (throw (ex-info "bnm.lattice: :frailty :from names a model the node does not average" {:from from})))]
                                      {:f f :q q :y (:y r)}))
                            rows)
                  cands (vec (map-indexed
                              (fn [i {:keys [t] :as c}]
                                (if (vec-cand? c)
                                  ;; a slope on exposure: each row loads [1 (1) e] on the joint
                                  ;; state and sees it less its own half variance; scored under the
                                  ;; prior issued, then filtered from the state run forward
                                  (let [[pm pv] (nth (:zc (first rows)) i)
                                        sobs (if rate?
                                               (mapv (fn [o r] (let [{:keys [rm rv vz load]} (frailty-at {:mu pm :P pv :cand c :g (:g r) :centre :rate} (:e r))]
                                                                 (assoc o :f (+ (double (:f o)) (cen (:f o) (:q o) rm rv vz (:sd c))) :load load)))
                                                     obs rows)
                                               (mapv (fn [o r] (assoc o :f (- (double (:f o)) (slope-half c (:e r))) :load (slope-load c (:e r) (:g r)))) obs rows))
                                        ;; bnm-02u: above sd 1 every row of the origin loading alike
                                        ;; (the gauges' [1 g_t]), the marginal is one-dimensional in
                                        ;; the shift a.s, taken by quadrature (`shared-ll`)
                                        a (:load (first sobs))
                                        ll (if (and (fine-state? (:sd c)) (every? #(= a (:load %)) sobs))
                                             (shared-ll sobs (reduce + 0.0 (map * a pm))
                                                        (reduce + 0.0 (for [i (range (count a)) j (range (count a))] (* (a i) (a j) (double (get-in pv [i j])))))
                                                        (:sd c))
                                             (:ll (hier/shared-marginal :logistic sobs pm pv)))
                                        prior (if (and t (<= s t)) {:m (:mu c) :v (:P c)} (let [{:keys [mu P]} (slope-ahead c s per)] {:m mu :v P}))
                                        post (hier/states-step prior sobs {:kind :logistic})]
                                    (assoc c :score (+ (double (:score c)) ll) :t (if t (max t s) s) :mu (:m post) :P (:v post)))
                                (let [[zm zv vz rm rv] (nth (:zc (first rows)) i)
                                      ;; :centre :rate: each row moved by its own shift, the state as it is
                                      obs (if rate? (mapv (fn [o] (update o :f #(+ (double %) (cen (:f o) (:q o) rm rv vz (:sd c))))) obs) obs)
                                      ll (shared-ll obs zm zv (:sd c))
                                      c (assoc c :score (+ (double (:score c)) ll) :t (if t (max t s) s))]
                                  (if (:lsd c)
                                    ;; the transient and the persistent level, filtered jointly
                                    (merge c (level-step c (if (and t (<= s t)) (select-keys c [:m :v :lm :lv :c]) (level-ahead (assoc c :t t) s per)) obs rate?))
                                    (let [prior (if (and t (<= s t)) {:m (:m c) :v (:v c)} (cand-ahead (assoc c :t t) s per))]
                                      (if rate?
                                        (let [post (hier/states-step prior obs {:kind :logistic})]
                                          (assoc c :m (:m post) :v (:v post)))
                                        ;; the rows see the state less its half variance
                                        (let [h (half-var c)
                                              post (hier/states-step (update prior :m - h) obs {:kind :logistic})]
                                          (assoc c :m (+ (:m post) h) :v (:v post)))))))))
                              cands))
                  ci (chosen cands)
                  pick (nth cands ci)
                  ;; a slope candidate's z, level and u_j from its joint state
                  pick (cond
                         (:usd pick)
                         (let [{:keys [mu P lsd]} pick
                               ul (if lsd 2 1)]
                           (cond-> (assoc pick :m (first mu) :v (get-in P [0 0])
                                          :u (subvec mu ul) :u-v (mapv #(get-in P [% %]) (range ul (count mu))))
                             lsd (assoc :lm (mu 1) :lv (get-in P [1 1]))))
                         ;; the gauges' loadings (bnm-v1y): z first, beta after
                         (:b0 pick)
                         (let [{:keys [mu P]} pick]
                           (assoc pick :m (first mu) :v (get-in P [0 0])
                                  :beta (subvec mu 1) :beta-v (mapv #(get-in P [% %]) (range 1 (count mu)))))
                         :else pick)]
              (assoc fr :cands cands
                     :filtered (assoc (:filtered fr) s (cond-> (assoc (select-keys pick [:m :v :rho :sd])
                                                                 :scores (mapv :score cands))
                                                         ;; :prior :finer: the chosen kappa (0, the node's own)
                                                         (= :finer prior) (assoc :cross (double (:cross pick 0.0)))
                                                         level? (assoc :lsd (double (:lsd pick 0.0))
                                                                       :level (double (:lm pick 0.0))
                                                                       :level-v (double (:lv pick 0.0)))
                                                         exposure? (assoc :usd (double (:usd pick 0.0))
                                                                          :u (:u pick) :u-v (:u-v pick))
                                                         ;; the gauges' loadings and the origin's gauges
                                                         (:b0 pick) (assoc :beta (:beta pick) :beta-v (:beta-v pick) :g (:g (first rows))))))))
          fr (sort-by key (group-by :origin (filter :informed ready))))))

(defn frailty-prior
  "The frailty a node issues with at origin t: {:m :v :rho :sd} from the
  chosen candidate, and :zc, each candidate's [m v]: the shift applied,
  its state less half its stationary variance (mean one in the rate, as
  augur.hier/states), and its variance. Under :prior :parent,
  rho times the coarser level's state at this scale (filtered before the
  node issues), else the node's own run forward. Under :prior :finer
  (`finer` {:src the source node's frailty :src-per its months a period}),
  a candidate with kappa (:cross) issues with kappa times the source's
  chosen state run forward to t at the source's own period, less half the
  stationary variance S = max(sd^2, kappa^2 sd_src^2), its variance kappa^2
  v_src plus S less kappa^2 sd_src^2 (the year's shock is kappa times the
  source's plus a part of its own); a candidate without kappa issues with
  the node's own."
  ([cfg fr parent-fr t per] (frailty-prior cfg fr parent-fr t per nil))
  ([{:keys [prior centre]} fr parent-fr t per {:keys [src src-per] :as finer}]
  (let [{:keys [cands]} fr
        ;; bnm-0fe, :centre :rate: no half variance in the mean; a scalar
        ;; candidate's entry is [m v vz rm rv], vz its transient's stationary
        ;; variance and N(rm, rv) the part not centred (a level as it is), from
        ;; which each place's own shift is solved as it issues (`rate-shift`)
        rate? (= :rate centre)
        ;; the source's chosen state at t, run forward at its own period
        ;; (not the node's: a quarter's state runs four periods to a year's one)
        [sm sv ss] (when finer
                     (if-let [pc (some->> (:cands src) not-empty (#(nth % (chosen %))))]
                       (let [{:keys [m v]} (cand-ahead pc t src-per)] [m v (double (:sd pc))])
                       [0.0 0.0 0.0]))
        ;; a candidate's transient z run forward, a slope candidate's from its joint state
        z-ahead (fn [c] (if (vec-cand? c)
                          (let [{:keys [mu P]} (slope-ahead c t per)] {:m (first mu) :v (get-in P [0 0])})
                          (cand-ahead c t per)))
        of (fn [c]
             (if (and (= :parent prior) parent-fr)
               (let [pc (nth (:cands parent-fr) (chosen (:cands parent-fr)))
                     {pm :m pv :v} (z-ahead pc)
                     r (double (:rho c)) s2 (* (double (:sd c)) (double (:sd c)))]
                 {:m (* r pm) :v (+ (* r r pv) (* s2 (- 1.0 (* r r))))})
               (cand-ahead c t per)))
        ;; the shift applied: the state less half its stationary variance,
        ;; plus a level candidate's own level as it is (under :parent the
        ;; transient is the parent's, the level the node's own)
        zc (mapv (fn [c]
                   (cond
                     ;; a slope candidate: its joint state's mean and variance, the
                     ;; half variance taken per place (`frailty-at`); under :parent
                     ;; z the parent's, uncorrelated with the node's own level and u
                     (vec-cand? c)
                     (let [{:keys [mu P]} (slope-ahead c t per)]
                       (if (and (= :parent prior) parent-fr)
                         (let [{zm :m zv :v} (of c)]
                           [(assoc mu 0 zm)
                            (vec (for [i (range (count mu))]
                                   (vec (for [j (range (count mu))]
                                          (cond (= 0 i j) zv (or (zero? i) (zero? j)) 0.0 :else (get-in P [i j]))))))])
                         [mu P]))
                     (:lsd c)
                     (let [j (level-ahead c t per)
                           j (if (and (= :parent prior) parent-fr) (assoc (merge j (of c)) :c 0.0) j)]
                       (if rate?
                         (let [[m v] (level-applied c j true)]
                           [m v (* (double (:sd c)) (double (:sd c))) (double (:lm j)) (double (:lv j))])
                         (level-applied c j)))
                     (:cross c)
                     (do (when-not finer
                           (throw (ex-info "bnm.lattice: a :prior :finer candidate without its source" {:cand c})))
                         (let [k (double (:cross c)) s2 (* (double (:sd c)) (double (:sd c)))
                               ks2 (* k k ss ss)
                               big (Math/max s2 ks2)]
                           (if rate?
                             [(* k sm) (+ (* k k sv) (- big ks2)) big 0.0 0.0]
                             [(- (* k sm) (* 0.5 big)) (+ (* k k sv) (- big ks2))])))
                     :else
                     (let [{:keys [m v]} (of c)]
                       (if rate?
                         [(double m) (double v) (* (double (:sd c)) (double (:sd c))) 0.0 0.0]
                         [(- m (half-var c)) v]))))
                 cands)
        ci (chosen cands)
        pick (nth cands ci)]
    (if (vec-cand? pick)
      ;; with a slope the node's prior is the joint state; each place's shock
      ;; is read from it with the place's exposures (`frailty-at`), and the
      ;; origin's gauges (bnm-v1y)
      (let [[mu P] (nth zc ci)]
        (cond-> {:mu mu :P P :cand (select-keys pick [:rho :sd :lsd :usd :b0]) :rho (:rho pick) :sd (:sd pick) :zc zc}
          rate? (assoc :centre :rate)))
      (let [[m v vz rm rv] (nth zc ci)]
        (cond-> {:m m :v v :rho (:rho pick) :sd (:sd pick) :zc zc}
          (= :finer prior) (assoc :cross (double (:cross pick 0.0)))
          rate? (assoc :centre :rate :vz vz :rm rm :rv rv)))))))

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
   :flow-std-min-origins n, the flows read 0 until the standardizer has
   seen n origins (bnm-56c.34): its own gate counts rows, so a node of 165
   countries passed it on one origin and its flows were scaled by one
   moment's spread across places; without it, that row gate (the
   registered issues'),
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
   :forget, the model averaging's forgetting, per period, :forget-half-life
   h, instead each node's forgetting 0.5^(per / 12h), a half-life of h
   years at every scale (bnm-e1y), :only, a subset of groups,
   :weighting, how the averaging sets its weights (augur.dynamic/averaged's
   :weighting: tempered posterior or stacked; posterior weights without),
   :weighting {:rule :temper :power :per-place} tempers each period's
   evidence to the number of origins its rows were issued at over the
   number of rows: one origin's rows at n places count as one row's
   evidence (a world input is the same at every place, so untempered one
   episode moved the weights n times and locked them; docs/CALIBRATION.md
   2, the fix until the frailty's per-origin shock replaces it),
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
   :evidence-at :sequential, at learn time with each row given the
   period's rows before it (bnm-56c.20.2): scored independently, every row
   integrates a model's coefficients on its own, so a model unsure of an
   input that varies across the places (an exposure input at its first
   reading) is paid or rewarded for it once per place,
   :evidence-at :issue-joint (bnm-e1y), the joint predictive each model
   issued for an origin's rows: its coefficients as they stood at the
   origin and the origin's shock as issued, integrated over the rows
   together (augur's joint-score), so the evidence is the issued
   forecast's prequential score, with no hindsight and one count per
   origin; each pending row keeps the models' state at issue (:ist),
   :up-net-of-downscale, a node with :downscale passes up its log odds
   less the part of its offset that came down (bnm-56c.2: otherwise the
   coarser node reads its own forecast back through the finer states),
   :world-offset (fn [scale place t]) -> {:x log odds :reads} or nil, the
   world's term (bnm.world-term, learned once per origin) added to the
   node's offsets, so every model reads it at a fixed coefficient of 1
   (fix 3); none without,
   :frailty {:rhos :sds :from :prior} (recursive step 3b, bnm-56c.20,
   bnm-3be; docs/RECURSIVE.md step 3), a common shock per node and origin:
   a log-odds shift z shared by every place of the node, AR(1) across its
   origins, filtered from each origin's rows once they are known
   (augur.hier/states-step, against the :from model's issue-time
   predictive, the base's by default), its persistence and sd chosen
   online from the grid by each one's prequential score at the horizon
   its prior serves (sd 0 in the grid: a node with no common shock gets
   none). Each forecast is integrated over the prior issued at t, and an
   origin's rows are learned jointly with its shock (augur's :shared), so
   a shock common to n places teaches the coefficients and the averaging's
   weights about what one observation does, not n. :prior :parent takes
   the prior as rho times the coarser level's state (lagged) instead of
   the node's own. :level-sds [0.0 ...] (bnm-56c.20.1) adds a persistent
   level beside z, a random walk filtered jointly with it, its sd a period
   chosen from the same grid at a price of :level-penalty nats; [0.0], the
   default, is z alone. :exposure-sds [0.0 ...] (bnm-56c.20.2) adds a
   slope on the places' exposure (the spec's :exposure, each column
   standardized across the node's places at the origin): u_j per column,
   AR(1) at z's persistence, so a place's shock is z + u.e, less half its
   stationary variance (mean one at every exposure); filtered jointly
   with z once per origin, the rows learned jointly with the vector
   (augur's :shared with each row's :load), its sd chosen from the grid at
   a price of :exposure-penalty nats; [0.0], the default, is no slope;
   none without,
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
  (frailty-config spec)
  (when (and (some :frailty-only (:scales spec)) (not (:frailty spec)))
    (throw (ex-info "bnm.lattice: a :frailty-only scale needs the spec's :frailty" {:scales (mapv :name (:scales spec))})))
  (let [resolutions (memoize (fn [place] (if-let [f (:resolutions spec)] (or (f place) (sorted-set)) (sorted-set))))]
    {:t (:start spec)
     :st {:book {} :errors {} :tension {} :models {} :pending {} :resolutions resolutions :resets {}}
     :forecasts [] :weights {} :trace []}))

(defn finish
  "The result of a run from its last state."
  [{:keys [st forecasts weights trace]}]
  {:forecasts forecasts :weights weights :trace trace
   :book (:book st) :models (:models st) :resets (:resets st)
   :evidence (:evidence st) :flow-std (:flow-std st)
   :frailty (not-empty (into {} (for [[k fr] (:frailty st)] [k (:filtered fr)])))})

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
                       ;; a :frailty-only node (recursive step 4) filters its frailty
                       ;; and issues nothing: no forecast, book entry, weights or errors
                       fo? (boolean (:frailty-only scale-spec))
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
                       ;; :frailty (recursive step 3b): the node's common shock, one per origin
                       fcfg (frailty-config spec)
                       rows (learning-rows (map #(cond-> (select-keys % [:x :y :o :os]) (:lp %) (assoc :issued (:lp %))
                                                   ;; bnm-e1y, :evidence-at :issue-joint: the models as they
                                                   ;; issued the row, one value for the origin's rows
                                                   (:ist %) (assoc :issue-state (:ist %))
                                                   ;; the origin's shared shock, as issued: the rows of one
                                                   ;; origin are learned jointly with it (augur's :shared)
                                                   (:shared %) (assoc :shared (:shared %))
                                                   ;; with a slope on exposure (bnm-56c.20.2) the shock is a
                                                   ;; joint state each row loads on, its place's half variance
                                                   ;; taken from its offsets and its models' predictives
                                                   (:load %) (-> (assoc :load (:load %)) (shift-shock (- (double (:zh %)))))
                                                   ;; bnm-0fe, :centre :rate: the place's own shift, as issued
                                                   (:zcen %) (shift-shock (double (:zcen %)))
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
                       ;; :temper :power :per-place: this period's evidence counted
                       ;; once per origin, not once per place (docs/CALIBRATION.md 2)
                       model (if (per-place? (get-in spec [:weighting-at scale] (:weighting spec)))
                               (assoc-in model [:weighting :power] (per-place-power ready))
                               model)
                       ;; bnm-jnw, :discount-from :data: a node with no rows yet, now or
                       ;; before, is not stepped, so its coefficients meet their first
                       ;; data at the declared prior, not discounted since the start
                       _ (when-not (contains? #{nil :data} (:discount-from spec))
                           (throw (ex-info "bnm.lattice: :discount-from takes :data" {:discount-from (:discount-from spec)})))
                       model (if (and (= :data (:discount-from spec)) (empty? rows)
                                      (zero? (long (or (second (get-in st [:clim k])) 0))))
                               model
                               (d/step model rows))
                       ;; the frailty filtered from the origins now known, then its prior at t:
                       ;; the node's own run forward, or (:prior :parent) the coarser level's,
                       ;; filtered at an earlier period
                       ;; :prior :finer: a node coarser than the source scale issues
                       ;; with the source's state at its level, filtered earlier in
                       ;; this step (the scales run finest first) from rows known by t
                       src (when (= :finer (:prior fcfg)) (:source fcfg))
                       cross? (boolean (and src (finer? spec src scale)))
                       ;; bnm-v1y, :frailty :gauges: the world gauges that set the shock's mean here
                       gp (when fcfg (frailty-gauges spec scale))
                       st (if fcfg
                            (assoc-in st [:frailty k] (frailty-learn (cond-> fcfg gp (assoc :from (:gauges-from fcfg))) (or (get-in st [:frailty k]) (frailty-fresh fcfg cross? gp)) ready (:per scale-spec)))
                            st)
                       zprior (when fcfg
                                (frailty-prior fcfg (get-in st [:frailty k])
                                               (when-let [[pl _] (get (vec levels) (inc level-ix))] (get-in st [:frailty [scale pl]]))
                                               t (:per scale-spec)
                                               (when cross?
                                                 {:src (get-in st [:frailty [src level]])
                                                  :src-per (:per (first (filter #(= src (:name %)) scales)))})))
                       ;; :exposure-sds: each place's exposures standardized across the node's places
                       ez (when (:exposure? fcfg) (standardized-exposures spec scale places t))
                       ;; climatology: the rate among this node's resolved outcomes
                       st (update-in st [:clim k] (fn [[n1 n]] [(+ (or n1 0) (count (filter :y ready))) (+ (or n 0) (count ready))]))
                       _ (when (and (:clim-learn spec) (not= :current (:clim-learn spec)))
                           (throw (ex-info "bnm.lattice: :clim-learn takes :current" {:clim-learn (:clim-learn spec)})))
                       _ (when-not (#{:all :resolved} (:flow-std-from spec :all))
                           (throw (ex-info "bnm.lattice: :flow-std-from takes :all or :resolved" {:flow-std-from (:flow-std-from spec)})))
                       min-origins (:flow-std-min-origins spec)
                       _ (when-not (or (nil? min-origins) (and (integer? min-origins) (pos? min-origins)))
                           (throw (ex-info "bnm.lattice: :flow-std-min-origins takes a positive integer" {:flow-std-min-origins min-origins})))
                       ;; the offset, and whether it is climatology (:clim, its log odds):
                       ;; with an :offset that has nothing to say, the rate among the
                       ;; node's resolved outcomes (or the prior's, before any)
                       offset-of (if-let [f (when-not fo? (:offset spec))]
                                   (fn [place t] (if-let [o (f scale place t)]
                                                   {:o (double o)}
                                                   {:o clim-now :clim clim-now}))
                                   (if (and fo? clim-now) (constantly {:o clim-now :clim clim-now}) (constantly {:o 0.0})))
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
                       st (if fo? st
                                (reduce (fn [st {:keys [place y p known-at]}]
                                          (update-in st [:errors [scale place]] (fnil assoc (sorted-map)) known-at
                                                     (- (if y 1.0 0.0) p)))
                                        st ready))
                       ;; the node's own inputs (everything past the intercept and the
                       ;; gauges, which the spec standardizes) against their past
                       g-width (if (contains? (without spec scale) :gauges) 0 (get-in spec [:gauge-width scale] 0))
                       flow-std (get-in st [:flow-std k] (standardizer (:flow-std spec)))
                       ;; :principal, a set of groups (or :auto, every contradiction, turn
                       ;; and relation group): the one contributing most at each place under
                       ;; the all-inputs model's coefficients, recorded in the book
                       principal-of (when-let [pc (when-not fo? (:principal spec))]
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
                                       :let [g (if fo? [] ((:gauges spec) scale place t))
                                             {:keys [os read down pool clim]} (offsets-of place t)
                                             ;; :world-offset (fix 3): the world's term, learned once
                                             ;; per origin at the world node, added to every offset
                                             wo (when-let [f (when-not fo? (:world-offset spec))] (f scale place t))
                                             _ (when-let [late (seq (filter #(> (or (:known-at %) (:origin %)) t) (:reads wo)))]
                                                 (throw (ex-info "bnm.lattice: a world offset read after the origin" {:scale scale :place place :t t :reads (vec late)})))
                                             os (if-let [x (:x wo)] (mapv (fn [[w o]] [w (+ o (double x))]) os) os)
                                             {:keys [x reads stated tension strain resolved]} (if fo?
                                                                                                {:x [1.0] :reads []}
                                                                                                (inputs spec st scale scale-spec level-ix place t g {:pool pool}))
                                             raw-flows (subvec x (inc g-width))
                                             ;; bnm-56c.34, :flow-std-min-origins n: 0 until the
                                             ;; standardizer has seen n origins, not just ten rows
                                             x (into (subvec x 0 (inc g-width))
                                                     (if (and min-origins (< (long (:origins flow-std 0)) (long min-origins)))
                                                       (mapv (constantly 0.0) raw-flows)
                                                       (standardize flow-std raw-flows)))]]
                                   {:place place :x x :raw-flows raw-flows :os os :down down :clim clim
                                    :reads (cond-> (into reads (:reads wo)) read (conj read))
                                    :stated stated :tension tension :strain strain :resolved resolved}))
                       ;; bnm-v1y, :frailty :gauges: the gauges read at t (the same at every
                       ;; place; their mean over the places with an outcome to come) set the
                       ;; shock's mean, and leave every model's inputs, so the averaging
                       ;; chooses among the models that rank
                       gt (when gp
                            (let [in (filter #((:target spec) scale (:place %) t) seen)
                                  in (if (seq in) in seen)]
                              (mapv (fn [i] (/ (reduce + 0.0 (map #(double (nth (:x %) i)) in)) (count in))) (:ix gp))))
                       seen (if gp
                              (mapv (fn [e] (update e :x (fn [x] (reduce #(assoc %1 %2 0.0) x (:ix gp))))) seen)
                              seen)
                       zprior (if gp (assoc zprior :g gt) zprior)
                       ;; a group with no input anywhere at t (its source ended, a signal
                       ;; gone stale, errors not yet known) reads the unknown's mean, 0, at
                       ;; every place: an intercept-only model under another name. It drops
                       ;; out of this period's forecasts, weight 0, the others renormalized;
                       ;; the averaging keeps its record, so it comes back when its source
                       ;; resumes
                       live (let [out (dormant (layout spec scale level-ix) (get-in spec [:core scale]) (map :x seen))]
                              (if (seq out) (assoc model :w (live-weights (d/weights model) out)) model))
                       state-of (when-not fo? (state-fn spec))
                       made (vec (for [{:keys [place x os] :as e} seen
                                       :let [one? (= 1 (count os))
                                             ;; the mean offset, learned from: the one whose
                                             ;; probability is the offsets' average
                                             o (if one? (second (first os)) (logit (reduce + (for [[w o] os] (* w (m/expit o))))))
                                             ;; with a slope on exposure, the place's own shock from the node's joint state
                                             zprior (if (:mu zprior)
                                                      (merge (frailty-at zprior (get ez place)) (select-keys zprior [:zc :mu :P :g :sd]))
                                                      zprior)
                                             ;; bnm-0fe, :centre :rate: the place's own shift c, so that at
                                             ;; the shock's stationary prior the forecast is the one issued
                                             ;; without it; the state's mean kept as :m0 (the rows' shared
                                             ;; effect), c added to the mean issued and to the row's offsets
                                             zprior (if (:vz zprior)
                                                      (let [c (rate-shift (forecast-curve live x os) (:rm zprior) (:rv zprior) (:vz zprior) (:sd zprior))]
                                                        (assoc zprior :m0 (:m zprior) :m (+ (double (:m zprior)) c) :c c))
                                                      zprior)
                                             ;; :frailty: the forecast integrated over the common shock's prior
                                             osz (if zprior (with-frailty os zprior) os)
                                             p (if (= 1 (count osz))
                                                 (d/predict live x (second (first osz)))
                                                 (reduce + (for [[w o] osz] (* w (d/predict live x o)))))
                                             {pf :f pq :q} (cond-> (d/predictive live x o)
                                                             zprior (-> (update :f + (:m zprior)) (update :q + (:v zprior))))
                                             target ((:target spec) scale place t)]]
                                   (assoc e :o o :p p :target target :f pf :q pq :principal (when principal-of (principal-of x))
                                          :frailty zprior :ez (when ez (get ez place))
                                          ;; :state-from: the node's state, and the errors it feeds back,
                                          ;; from a fixed reference rather than the combination, which is
                                          ;; only issued (loop audit issue 1)
                                          :ref (when state-of
                                                 (state-of {:scale scale :level level :place place :t t
                                                            :models (:models model) :x x
                                                            :o (if zprior (+ o (double (:m zprior))) o) :os osz}))
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
                       st (if fo? st
                           (reduce (fn [st {:keys [place p stated tension strain resolved f q principal down ref frailty]}]
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
                                                       principal (assoc :principal principal)
                                                       ;; the common shock issued with (§1.2 :hazard :z :z-var)
                                                       frailty (assoc :z (:m frailty) :z-var (:v frailty))
                                                       ;; bnm-v1y: the gauges it was issued at
                                                       (:g frailty) (assoc :zg (:g frailty)))))
                                        (assoc-in [:tension [scale place]] tension)
                                        (cond-> resolved (update-in [:resets [scale place]] (fnil into []) resolved))))
                                  st made))
                       ;; bnm-56c.33, :flow-std-from :resolved: the flows are standardized
                       ;; over the span the node's inputs carry information, from its first
                       ;; resolved outcome on; before it the states it reads are uninformed
                       ;; (climatology 0.5 or a prior), and counting them made the first real
                       ;; moves read near the cap. Until then nothing is observed, so its
                       ;; flows read 0. The default, :all, counts every origin (the
                       ;; registered issues' code)
                       st (if (and (not fo?) (or (not= :resolved (:flow-std-from spec)) (flows-informed? st k)))
                            (assoc-in st [:flow-std k] (cond-> (reduce (fn [sd {:keys [raw-flows]}] (observe sd raw-flows)) flow-std made)
                                                         ;; counted only under the option, so the state
                                                         ;; without it is the registered issues'
                                                         (and min-origins (seq made)) (assoc :origins (inc (long (:origins flow-std 0))))))
                            st)
                       st (-> st
                              (assoc-in [:models k] model)
                              (assoc-in [:pending k] (into (vec waiting)
                                                           ;; the error fed back is the state's (the reference's p
                                                           ;; under :state-from); :lp each model's predictive at issue;
                                                           ;; :regime-at-issue a slot for the regime layer
                                                           (for [{:keys [place x o os p target ref issued clim frailty ez]} made :when target
                                                                 :let [ist (d/issue-state model)]]
                                                             (cond-> {:place place :origin t :x x :o o :p (if ref (:p ref) p) :y (:y target) :known-at (:known-at target)
                                                                      :regime-at-issue nil}
                                                               issued (assoc :lp issued)
                                                               ;; bnm-e1y: the models' state at issue, for the
                                                               ;; origin's joint issued predictive
                                                               (= :issue-joint (:evidence-at spec)) (assoc :ist ist)
                                                               ;; bnm-56c.27: the climatology it was issued at
                                                               (and clim (:clim-learn spec)) (assoc :clim clim)
                                                               ;; bnm-56c.22: the offsets issued over, learned at
                                                               (and (= :mixture (:learn-offsets spec)) (< 1 (count os))) (assoc :os os)
                                                               ;; :frailty: the origin, its shock as issued, each candidate's prior
                                                               frailty (assoc :origin t :shared [t (if (:vz frailty) (:m0 frailty) (:m frailty)) (:v frailty)] :zc (:zc frailty)
                                                                              ;; issued once the node had a resolved outcome: before
                                                                              ;; it the base reads nothing of its own (climatology
                                                                              ;; 0.5 or a prior), and its miss is not a common shock
                                                                              :informed (flows-informed? st k))
                                                               ;; :exposure-sds: the place's standardized exposures, and
                                                               ;; under a slope the joint state it loads on as issued
                                                               ez (assoc :e ez)
                                                               ;; bnm-v1y: the origin's gauges, loaded on beta
                                                               (:g frailty) (assoc :g (:g frailty))
                                                               (:load frailty) (assoc :shared [t (:mu frailty) (:P frailty)] :load (:load frailty) :zh (:h frailty))
                                                               ;; bnm-0fe: the place's own shift, learned in its offsets
                                                               (:vz frailty) (assoc :zcen (:c frailty)))))))]
                   (if fo?
                     [st forecasts weights trace]
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
                      trace)])))
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
        {:keys [n s ss ns stable m2]} (get-in state [:st :flow-std k])
        sd (fn [i] (let [j (- i (inc g-width))
                         nj (double (max 1 (if ns (nth ns j) n)))
                         m (/ (nth s j) nj)]
                     (if stable
                       (Math/sqrt (Math/max 1e-12 (/ (nth m2 j) nj)))
                       (Math/sqrt (Math/max 1e-12 (- (/ (nth ss j) nj) (* m m)))))))]
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
