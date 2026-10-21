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
    its children, each known by t.
  The node's own inputs (everything but the intercept and the spec's
  gauges, which the spec standardizes) are standardized by their running
  mean and spread at the node's earlier origins, capped at 5: strain, gaps
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
  which gauges, flows and contradictions mattered at that node. :prior
  seeds coefficients with what is known."
  (:require [augur.dynamic :as d]
            [bnm.relations :as rel]
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

(def ^:private hermite
  "Five-point Gauss-Hermite quadrature for the standard normal: nodes and
  weights."
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
  "The standardizer after seeing the vector xs."
  [{:keys [n s ss]} xs]
  {:n (inc n)
   :s (mapv + (or s (repeat (count xs) 0.0)) xs)
   :ss (mapv + (or ss (repeat (count xs) 0.0)) (map #(* % %) xs))})

(defn standardize
  "xs against the mean and spread of everything observed before, capped at
  5; 0 until ten have been seen (the scale is not known yet)."
  [{:keys [n s ss]} xs]
  (if (< n 10)
    (mapv (constantly 0.0) xs)
    (mapv (fn [x si ssi]
            (let [m (/ si n)
                  sd (Math/sqrt (Math/max 0.0 (- (/ ssi n) (* m m))))]
              (if (> sd 1e-9) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd))) 0.0)))
          xs s ss)))

(defn- without
  "The groups dropped at `scale`: the spec's :without everywhere, and its
  :without-at for this scale."
  [spec scale]
  (into (set (:without spec)) (get-in spec [:without-at scale])))

(defn- children-of [spec level-ix place]
  (when (pos? level-ix)
    (let [[_ below] (nth (:levels spec) (dec level-ix))]
      (filter #(= place (get (:parent spec) %)) below))))

(defn- ancestors-of [spec place]
  (take-while some? (rest (iterate #(get (:parent spec) %) place))))

(defn- layout
  "The input layout of a node: {:width n :groups {group [index...]} :order}."
  [spec scale level-ix]
  (let [names (map :name (:scales spec))
        finer (take-while #(not= % scale) names)
        coarser (rest (drop-while #(not= % scale) names))
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
               [:relations (if (:relations spec) (get-in spec [:relation-width scale] 0) 0)]]
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
        flows (filter #(seq (spans %)) [:up :down :contradiction :turn :exposure :feedback])]
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
     :groups (merge gauge-groups (select-keys spans flows) rel-groups
                    {:base [] :all (vec (mapcat spans [:gauges :up :down :contradiction :turn :exposure :feedback :relations]))})}))

(defn- node-model
  "A node's model average: one dynamic logistic per group of its inputs and
  one over all of them, each reading the intercept and the spec's :core
  inputs (what is known, read by every model). A coefficient starts at zero
  with variance 1 (4 for the intercept), the all-inputs model's non-core
  ones at the spec's :shrink, unless the spec's :prior gives {input-index
  [mean variance]} for the node."
  [spec scale level {:keys [discount]} {:keys [groups order] :as lay}]
  (let [prior (merge (when-let [[m v] (:contradiction-prior spec)]
                       (into {} (for [i (:strain-ix lay)] [i [m v]])))
                     (or (when-let [f (:prior spec)] (f scale level lay)) {}))
        core (mapv #(inc (long %)) (get-in spec [:core scale]))
        core? (set core)
        shrink (:shrink spec)]
    (d/averaged (into {} (for [g (if (:only spec) (filter (:only spec) order) order)
                               :let [ix (vec (distinct (concat [0] core (remove core? (groups g)))))]
                               :when (or (= g :base) (seq (groups g)))]
                           [g (d/logistic {:mean (map #(first (get prior % [0.0])) ix)
                                           :var (map #(or (second (get prior % [nil nil]))
                                                          (cond (zero? %) 4.0
                                                                (and shrink (= g :all) (not (core? %))) shrink
                                                                :else 1.0))
                                                     ix)
                                           :discount discount :inputs ix})]))
                {:forget (:forget spec)})))

(defn- issued
  "Entries for [scale place] issued at origins in [lo, hi], oldest first."
  [book scale place lo hi]
  (when-let [byo (get book [scale place])]
    (subseq byo >= lo <= hi)))

(defn- last-error
  "The last error of [scale place] known by t, as [known-at error]."
  [errors scale place t]
  (some-> (get errors [scale place]) (rsubseq <= t) first))

(defn- inputs
  "The node's inputs for `place` at origin t, what it read, its own state
  and its accumulated tensions after this period."
  [spec {:keys [book errors tension resolutions]} scale scale-spec level-ix place t gauges]
  (let [{:keys [finer coarser]} (layout spec scale level-ix)
        per (:per scale-spec)
        reads (transient [])
        read! (fn [r] (conj! reads r))
        ;; up: finer scales over this period, children at this origin
        finer-entries (for [f finer] [f (issued book f place (- t (dec per)) t)])
        up-time (mapcat (fn [[f es]]
                          (doseq [[o _] es] (read! {:dir :up :scale f :place place :origin o}))
                          (if (seq es) [(:s (val (last es))) (mean (map (comp :s val) es))] [0.0 0.0]))
                        finer-entries)
        kids (children-of spec level-ix place)
        kid-entries (keep (fn [c] (when-let [e (get-in book [[scale c] t])] (read! {:dir :up :scale scale :place c :origin t}) e)) kids)
        up-space (when (pos? level-ix)
                   (if (seq kid-entries)
                     [(logit (- 1.0 (reduce * (map #(- 1.0 (:p %)) kid-entries)))) (mean (map :s kid-entries))]
                     [0.0 0.0]))
        ;; down: coarser scales before t, every ancestor at the previous origin
        down-time (mapv (fn [c]
                          (if-let [[o e] (last (issued book c place Long/MIN_VALUE (dec t)))]
                            (do (read! {:dir :down :scale c :place place :origin o}) (:s e))
                            nil))
                        coarser)
        down-space (mapv (fn [a]
                           (if-let [e (get-in book [[scale a] (- t per)])]
                             (do (read! {:dir :down :scale scale :place a :origin (- t per)}) (:s e))
                             nil))
                         (ancestors-of spec place))
        down (mapv #(or % 0.0) (concat down-time down-space))
        ;; the place's own state: its gauges' when the spec says, else its
        ;; last forecast's (never this one's, which these inputs make)
        stated (when-let [f (:state spec)] (f scale place t gauges))
        own (or stated (some-> (get-in book [[scale place] (- t per)]) :s) 0.0)
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
        relations (concat (when (seq (ancestors-of spec place)) [[:parent parent-state]])
                          (map vector coarser down-time))
        rel' (into {} (for [[r other] relations
                            :let [gap (if other (- own other) 0.0)]]
                        [r {:gap gap
                            :above (+ (Math/max 0.0 gap) (carry (get-in rel [r :above])))
                            :below (+ (Math/max 0.0 (- gap)) (carry (get-in rel [r :below])))}]))
        ;; the strain each coarser scale has accumulated, carried down (from before t)
        coarser-strain (map (fn [c] (or (some-> (last (issued book c place Long/MIN_VALUE (dec t))) val :strain) 0.0)) coarser)
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
        ;; feedback: errors of the coarser scales, the parent, the children, known by t
        err (fn [s p] (when-let [[k v] (last-error errors s p t)] (read! {:dir :feedback :scale s :place p :known-at k}) v))
        parent (get (:parent spec) place)
        feedback (concat (map #(or (err % place) 0.0) coarser)
                         (when parent [(or (err scale parent) 0.0)])
                         (when (pos? level-ix) [(or (mean (keep #(err scale %) kids)) 0.0)]))
        ;; relations: other kinds' states and onsets for the place, from before t
        rel (when-let [f (:relations spec)]
              (let [{:keys [x] :as r} (f scale place t)
                    width (get-in spec [:relation-width scale] 0)]
                (when (not= width (count x))
                  (throw (ex-info "bnm.lattice: :relations gave the wrong width" {:scale scale :width width :got (count x)})))
                (update r :x #(mapv double %))))]
    {:x (let [w (without spec scale)]
          (vec (concat [1.0] (when-not (w :gauges) gauges)
                       (when-not (w :up) (concat up-time up-space))
                       (when-not (w :down) down)
                       (when-not (w :contradiction) contradiction)
                       (when-not (w :turn) turn)
                       (when-not (w :exposure) exposure)
                       (when-not (w :feedback) feedback)
                       (when-not (w :relations) (:x rel)))))
     :reads (into (persistent! reads) (when-not ((without spec scale) :relations) (:reads rel)))
     :stated stated
     :resolved resolved
     :strain strain-total
     :tension {:rel rel' :fin fin' :tacc tacc :flows flows' :prev-mean smean :last-t t}}))

(def ^:private flow-groups #{:base :up :down :contradiction :turn :exposure :feedback :all})

(defn- couple-levels
  "Couples the gauge-group models of each pair of adjacent levels at one
  scale."
  [models scale levels strength]
  (reduce (fn [models [la lb]]
            (let [ka [scale la] kb [scale lb]
                  a (models ka) b (models kb)]
              (if (and a b)
                (let [shared (filter #(and (not (flow-groups %))
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
   :shrink, the all-inputs model's prior variance on its non-core inputs,
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
  (when-let [bad (seq (remove #{:gauges :up :down :contradiction :turn :exposure :feedback :relations}
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
   :evidence (:evidence st)})

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
                       rows (map #(select-keys % [:x :y :o]) ready)
                       ;; traced: each model's evidence, the log score of the outcomes it
                       ;; learns from now, scored before it learns them
                       st (if (and (:trace spec) (seq rows))
                            (reduce (fn [st [g m]]
                                      (update-in st [:evidence k g] (fn [[ll n]] [(+ (or ll 0.0) (d/log-score m rows)) (+ (or n 0) (count rows))])))
                                    st (:models model))
                            st)
                       model (d/step model rows)
                       ;; climatology: the rate among this node's resolved outcomes
                       st (update-in st [:clim k] (fn [[n1 n]] [(+ (or n1 0) (count (filter :y ready))) (+ (or n 0) (count ready))]))
                       offset-of (if-let [f (:offset spec)]
                                   (let [[n1 n] (get-in st [:clim k] [0 0])
                                         clim (logit (/ (+ n1 0.5) (+ n 1.0)))]
                                     (fn [place t] (double (or (f scale place t) clim))))
                                   (constantly 0.0))
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
                                      (if-let [[o-t {:keys [p f q]}] (when ds (some-> (get-in st [:book [(:from ds) place]]) (rsubseq < t) first))]
                                        (let [os (if (:downscale-uncertainty spec) (offsets-under f q ratio) [[1.0 (window-log-odds p ratio)]])
                                              known (when (and pool (:offset spec)) ((:offset spec) scale place t))
                                              os (if known (mapv (fn [[w o]] [w (+ (* pool known) (* (- 1.0 pool) o))]) os) os)]
                                          {:os os :read {:dir :downscale :scale (:from ds) :place place :origin o-t}})
                                        {:os [[1.0 (offset-of place t)]]})))
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
                                      (let [{:keys [groups]} (layout spec scale level-ix)
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
                       made (vec (for [place places
                                       :let [g ((:gauges spec) scale place t)
                                             {:keys [x reads stated tension strain resolved]} (inputs spec st scale scale-spec level-ix place t g)
                                             raw-flows (subvec x (inc g-width))
                                             x (into (subvec x 0 (inc g-width)) (standardize flow-std raw-flows))
                                             {:keys [os read]} (offsets-of place t)
                                             reads (cond-> reads read (conj read))
                                             one? (= 1 (count os))
                                             ;; the mean offset, learned from: the one whose
                                             ;; probability is the offsets' average
                                             o (if one? (second (first os)) (logit (reduce + (for [[w o] os] (* w (m/expit o))))))
                                             p (if one? (d/predict model x o) (reduce + (for [[w o] os] (* w (d/predict model x o)))))
                                             {pf :f pq :q} (d/predictive model x o)
                                             target ((:target spec) scale place t)]]
                                   {:place place :x x :raw-flows raw-flows :o o :p p :target target :reads reads :stated stated :tension tension :strain strain
                                    :resolved resolved :f pf :q pq :principal (when principal-of (principal-of x))}))
                       st (reduce (fn [st {:keys [place p stated tension strain resolved f q principal]}]
                                    (-> st
                                        (update-in [:book [scale place]] (fnil assoc (sorted-map)) t
                                                   ;; a node's state: the spec's when it gives one, else its
                                                   ;; forecast's log odds; f and q its linear predictor's moments
                                                   (cond-> {:p p :s (or stated (logit p)) :strain strain :f f :q q}
                                                     principal (assoc :principal principal)))
                                        (assoc-in [:tension [scale place]] tension)
                                        (cond-> resolved (update-in [:resets [scale place]] (fnil into []) resolved))))
                                  st made)
                       st (assoc-in st [:flow-std k] (reduce (fn [sd {:keys [raw-flows]}] (observe sd raw-flows)) flow-std made))
                       st (-> st
                              (assoc-in [:models k] model)
                              (assoc-in [:pending k] (into (vec waiting)
                                                           (for [{:keys [place x o p target]} made :when target]
                                                             {:place place :x x :o o :p p :y (:y target) :known-at (:known-at target)}))))]
                   [st
                    (into forecasts (for [{:keys [place p target]} made]
                                              {:scale scale :place place :origin t :p p :y (:y target)}))
                    (update weights k (fnil conj []) [t (d/weights model)])
                    (if (:trace spec)
                      (into trace (for [{:keys [place reads x p o]} made]
                                            (cond-> {:scale scale :level level :place place :origin t :reads reads
                                                     :learned (map #(select-keys % [:known-at]) ready)}
                                              (= :inputs (:trace spec)) (assoc :x x :p p :o o))))
                      trace)]))
               [st forecasts weights trace]
               (for [s due level-ix (range (count levels))] [s level-ix]))
              ;; the levels share what they learned this period
              st (if-let [s (:coupling spec)]
                   (reduce (fn [st sc] (update st :models couple-levels (:name sc) (map first levels) s)) st due)
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
        rel-ix (vec (sort (mapcat val (filter #(or (= :relations (key %)) (= "rel" (some-> (key %) namespace))) (:groups lay)))))
        g-width (if (contains? (without spec scale) :gauges) 0 (get-in spec [:gauge-width scale] 0))
        avg (get-in state [:st :models k])
        {:keys [n s ss]} (get-in state [:st :flow-std k])
        sd (fn [i] (let [j (- i (inc g-width))
                         m (/ (nth s j) n)]
                     (Math/sqrt (Math/max 1e-12 (- (/ (nth ss j) n) (* m m))))))]
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
