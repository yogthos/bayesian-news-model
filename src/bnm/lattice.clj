(ns bnm.lattice
  "A model per place and per time scale (docs/LATTICE.md). Time is counted
  in months; each scale ({:name :per :horizon :discount}) issues forecasts
  at the end of each of its periods, finest first, and at each scale the
  levels of the place tree issue theirs bottom-up. A node is one level at
  one scale: a dynamic-model average (augur.dynamic) over groups of its
  inputs, pooled across the level's places, learning from each period's
  resolved outcomes and carrying what it learned forward.

  A node's inputs at origin t, by group:
  - :gauges, its own scale's, from the spec's :gauges function;
  - :up, what finer nodes passed up: each finer scale's forecasts for the
    place issued over the node's period (the last and the mean, as log
    odds), and its children's chance of any onset at this origin;
  - :down, what coarser nodes passed down, lagged: each coarser scale's
    last forecast for the place issued before t, and its parent's forecast
    at the previous origin of this scale.
  Each quantity flows one way, and down only from before t, so nothing is
  counted twice. Outcomes ({:y :known-at} from the spec's :target) are
  held back from learning until they are known.

  The models averaged at each node read the intercept and one group each,
  or all of them (:base :gauges :up :down :all; :only picks a subset): the
  weights through time say which flows mattered at that node."
  (:require [augur.dynamic :as d]
            [augur.math :as m]))

(def groups-order "The groups a node's models read." [:base :gauges :up :down :all])

(defn finer?
  "Whether scale a is finer than scale b in `spec`."
  [spec a b]
  (let [ix (zipmap (map :name (:scales spec)) (range))]
    (< (ix a) (ix b))))

(defn- logit ^double [^double p] (m/logit (Math/max 1e-6 (Math/min (- 1.0 1e-6) p))))

(defn- children-of [spec level-ix place]
  (when (pos? level-ix)
    (let [[_ below] (nth (:levels spec) (dec level-ix))]
      (filter #(= place (get (:parent spec) %)) below))))

(defn- layout
  "The input layout of a node: {:width n :groups {group [index...]}}."
  [spec scale level-ix]
  (let [names (map :name (:scales spec))
        finer (take-while #(not= % scale) names)
        coarser (rest (drop-while #(not= % scale) names))
        gw (get-in spec [:gauge-width scale] 0)
        up-n (+ (* 2 (count finer)) (if (pos? level-ix) 1 0))
        down-n (+ (count coarser) (if (< level-ix (dec (count (:levels spec)))) 1 0))
        gauges (vec (range 1 (+ 1 gw)))
        up (vec (range (+ 1 gw) (+ 1 gw up-n)))
        down (vec (range (+ 1 gw up-n) (+ 1 gw up-n down-n)))]
    {:width (+ 1 gw up-n down-n) :finer finer :coarser coarser
     :groups {:base [] :gauges gauges :up up :down down :all (vec (concat gauges up down))}}))

(defn- node-model [spec {:keys [discount]} {:keys [groups]}]
  (d/averaged (into {} (for [g (or (:only spec) groups-order)
                             :let [ix (vec (cons 0 (groups g)))]
                             :when (or (= g :base) (seq (groups g)))]
                         [g (d/logistic {:mean (repeat (count ix) 0.0)
                                         :var (cons 4.0 (repeat (dec (count ix)) 1.0))
                                         :discount discount :inputs ix})]))
              {:forget (:forget spec)}))

(defn- issued
  "Forecasts for [scale place] issued at origins in [lo, hi], oldest first."
  [book scale place lo hi]
  (when-let [byo (get book [scale place])]
    (subseq byo >= lo <= hi)))

(defn- inputs
  "The node's inputs for `place` at origin t, and what it read."
  [spec book scale scale-spec level-ix place t gauges]
  (let [{:keys [finer coarser]} (layout spec scale level-ix)
        per (:per scale-spec)
        reads (transient [])
        read! (fn [dir s p o] (conj! reads {:dir dir :scale s :place p :origin o}))
        ;; finer scales: the forecasts they issued over this period
        up-time (mapcat (fn [f]
                          (let [fs (issued book f place (- t (dec per)) t)]
                            (doseq [[o _] fs] (read! :up f place o))
                            (if (seq fs)
                              [(logit (val (last fs))) (logit (/ (reduce + (map val fs)) (count fs)))]
                              [0.0 0.0])))
                        finer)
        ;; children at this scale and origin: the chance any has an onset
        kids (children-of spec level-ix place)
        up-space (when (pos? level-ix)
                   (let [ps (keep (fn [c] (when-let [p (get-in book [[scale c] t])] (read! :up scale c t) p)) kids)]
                     [(if (seq ps) (logit (- 1.0 (reduce * (map #(- 1.0 %) ps)))) 0.0)]))
        ;; coarser scales: their last forecast for the place before t
        down-time (map (fn [c]
                         (if-let [[o p] (last (issued book c place Long/MIN_VALUE (dec t)))]
                           (do (read! :down c place o) (logit p))
                           0.0))
                       coarser)
        ;; the parent at this scale, at the previous origin
        parent (get (:parent spec) place)
        down-space (when (< level-ix (dec (count (:levels spec))))
                     [(if-let [p (get-in book [[scale parent] (- t per)])]
                        (do (read! :down scale parent (- t per)) (logit p))
                        0.0)])]
    {:x (vec (concat [1.0] gauges up-time up-space down-time down-space))
     :reads (persistent! reads)}))

(defn run
  "Runs the lattice of `spec` from :start to :end (months):
  {:scales [{:name :per :horizon :discount}] finest first,
   :levels [[level [places]] ...] bottom-up, :parent {place parent},
   :target (fn [scale place origin]) -> {:y :known-at} or nil,
   :gauges (fn [scale place origin]) -> [inputs], :gauge-width {scale n},
   :forget, the model averaging's forgetting, :only, a subset of groups,
   :trace, to record what each node read and learned from}.
  {:forecasts [{:scale :place :origin :p :y}] :weights {[scale level]
  [[t weights]]} :trace [...]}."
  [spec]
  (let [{:keys [scales levels start end]} spec]
    (loop [t start book {} models {} pending {} forecasts (transient []) weights {} trace (transient [])]
      (if (> t end)
        {:forecasts (persistent! forecasts) :weights weights :trace (persistent! trace) :book book}
        (let [[book models pending forecasts weights trace]
              (reduce
               (fn [[book models pending forecasts weights trace] [scale-spec level-ix]]
                 (let [scale (:name scale-spec)
                       [level places] (nth levels level-ix)
                       k [scale level]
                       model (or (models k) (node-model spec scale-spec (layout spec scale level-ix)))
                       ;; learn from what is known by t
                       {ready true waiting false} (group-by #(<= (:known-at %) t) (pending k))
                       model (d/step model (map #(select-keys % [:x :y]) ready))
                       made (for [place places
                                  :let [{:keys [x reads]} (inputs spec book scale scale-spec level-ix place t
                                                                  ((:gauges spec) scale place t))
                                        p (d/predict model x)
                                        target ((:target spec) scale place t)]]
                              {:place place :x x :p p :target target :reads reads})
                       book (reduce (fn [b {:keys [place p]}]
                                      (update b [scale place] (fnil assoc (sorted-map)) t p))
                                    book made)]
                   [book
                    (assoc models k model)
                    (assoc pending k (into (vec waiting)
                                           (for [{:keys [x target]} made :when target]
                                             {:x x :y (:y target) :known-at (:known-at target)})))
                    (reduce conj! forecasts (for [{:keys [place p target]} made]
                                              {:scale scale :place place :origin t :p p :y (:y target)}))
                    (update weights k (fnil conj []) [t (d/weights model)])
                    (if (:trace spec)
                      (reduce conj! trace (for [{:keys [place reads]} made]
                                            {:scale scale :place place :origin t :reads reads
                                             :learned (map #(select-keys % [:known-at]) ready)}))
                      trace)]))
               [book models pending forecasts weights trace]
               (for [s scales
                     :when (zero? (mod (inc (- t start)) (:per s)))
                     level-ix (range (count levels))]
                 [s level-ix]))]
          (recur (inc t) book models pending forecasts weights trace))))))

(defn weights
  "The model-averaging weights of the node at `scale` and `level` after its
  last period, from the result of `run`."
  [result scale level]
  (second (last (get-in result [:weights [scale level]]))))
