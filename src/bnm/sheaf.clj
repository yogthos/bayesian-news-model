(ns bnm.sheaf
  "The lattice as a sheaf (Robinson 2017; Hansen & Ghrist 2019): the reads
  between nodes in one place.

  A node's book is a sheaf over time: its sections by origin, and the
  reads restrict it to an interval (latest-before t, over [lo, hi], at t),
  each recorded, and refused when after the reading node's origin. The
  base of the lattice is a poset of cells [scale place]: a cell lies under
  its parent place at its scale (a country under its region) and under
  the coarser scales at its place (a month under its year). Coarser and
  ancestor sections come down by those restrictions; finer and child
  sections come up aggregated (their last and mean state, the chance any
  child has an onset), which the lattice does inline. The coboundary sets
  a section against the comparable cells above it, each neighbour's value
  carried by its edge's map (identity unless given: a loading, a stock's
  integration), and is the lattice's contradiction; its size is the
  section's inconsistency.

  Every read within a kind's lattice goes through a reader; another
  kind's book is read through one that names the kind (bnm.relations), so
  what each node read, and when, is recorded in one shape.")

(defn base
  "The base poset of a lattice `spec` ({:scales :levels :parent})."
  [{:keys [scales levels parent]}]
  {:scales (mapv :name scales) :levels levels :parent parent})

(defn finer
  "The scales finer than `scale`, finest first."
  [{:keys [scales]} scale]
  (vec (take-while #(not= % scale) scales)))

(defn coarser
  "The scales coarser than `scale`, the next first."
  [{:keys [scales]} scale]
  (vec (rest (drop-while #(not= % scale) scales))))

(defn children
  "The places one level below `place` (at level index `level-ix`), or nil
  at the bottom level."
  [{:keys [levels parent]} level-ix place]
  (when (pos? level-ix)
    (let [[_ below] (nth levels (dec level-ix))]
      (filter #(= place (get parent %)) below))))

(defn ancestors
  "The places above `place`, nearest first."
  [{:keys [parent]} place]
  (vec (take-while some? (rest (iterate #(get parent %) place)))))

(defn cells
  "Every cell [scale place]."
  [{:keys [scales levels]}]
  (for [s scales [_ ps] levels p ps] [s p]))

(defn covers
  "The cells just above `cell`: its parent place at its scale, its place
  at the next coarser scale."
  [b [scale place]]
  (concat (when-let [p (get (:parent b) place)] [[scale p]])
          (when-let [c (first (coarser b scale))] [[c place]])))

(defn reader
  "A reader of `book` ({[scale place] (sorted-map origin entry)}) and of
  the cells' resolved `errors` ({[scale place] (sorted-map known-at
  error)}), recording every read. Options: :kind, named in each read (a
  reader of another kind's book); :origin, the reading node's origin,
  after which any read throws."
  ([book] (reader book {} {}))
  ([book errors] (reader book errors {}))
  ([book errors {:keys [kind origin]}]
   {:book book :errors errors :kind kind :origin origin :reads (atom [])}))

(defn- record!
  "Records a read; throws when it is after the reader's origin."
  [{:keys [reads kind origin]} dir [scale place] at k]
  (when (and origin (> at origin))
    (throw (ex-info "bnm.sheaf: a read after the node's origin"
                    {:dir dir :scale scale :place place :at at :node-origin origin})))
  (swap! reads conj (cond-> {:dir dir :scale scale :place place k at} kind (assoc :kind kind))))

(defn latest-before
  "[origin entry] of the cell's latest section strictly before t, or nil;
  recorded as a read in direction `dir`."
  [r dir cell t]
  (when-let [[o e] (some-> (get (:book r) cell) (rsubseq < t) first)]
    (record! r dir cell o :origin)
    [o e]))

(defn over
  "[[origin entry] ...] of the cell's sections at origins in [lo, hi],
  oldest first; each recorded."
  [r dir cell lo hi]
  (let [es (some-> (get (:book r) cell) (subseq >= lo <= hi))]
    (doseq [[o _] es] (record! r dir cell o :origin))
    es))

(defn at
  "The cell's section at origin t, or nil; recorded when there is one."
  [r dir cell t]
  (when-let [e (get-in (:book r) [cell t])]
    (record! r dir cell t :origin)
    e))

(defn latest-known
  "[known-at error] of the cell's latest error known by t, or nil;
  recorded as feedback."
  [r cell t]
  (when-let [[k v] (some-> (get (:errors r) cell) (rsubseq <= t) first)]
    (record! r :feedback cell k :known-at)
    [k v]))

(defn reads "Every read the reader made, in order." [r] @(:reads r))

(defn coboundary
  "{neighbour gap}: a section's value less what each neighbour says,
  carried by its edge's map in `maps` (identity where none is given); 0
  where a neighbour has no section."
  ([own neighbours] (coboundary own neighbours {}))
  ([own neighbours maps]
   (into {} (for [[k v] neighbours]
              [k (if (some? v) (- (double own) (double ((get maps k identity) v))) 0.0)]))))

(defn inconsistency
  "The size of a coboundary: the sum of its gaps' sizes."
  [gaps]
  (reduce + 0.0 (map #(Math/abs (double %)) (vals gaps))))
