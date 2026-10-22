(ns bnm.sheaf
  "The lattice as a sheaf (Robinson 2017; Hansen & Ghrist 2019): every
  cross-node read in one place.

  The base is a poset of cells [scale place]: a cell lies under its
  parent place at its scale (a country under its region) and under the
  next coarser scale at its place (a month under its year). A node's book
  is a section over its cell, its entries by origin. The lattice's reads
  are restriction maps along the poset: a coarser scale's or an
  ancestor's section carried down (from before t), finer scales' and
  children's carried up (over the period, at the origin). Gluing is
  reconciliation; the coboundary, a section against what its neighbours
  say, is the lattice's contradiction (docs/LATTICE.md), and its size the
  section's inconsistency with them.

  A reader wraps a book and records every read it makes, so what a node
  read, and that it read nothing from after its origin, is checked in one
  place.")

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
  "A reader of `book` ({[scale place] (sorted-map origin entry)}) that
  records every read."
  [book]
  {:book book :reads (atom [])})

(defn- record! [{:keys [reads]} dir [scale place] origin]
  (swap! reads conj {:dir dir :scale scale :place place :origin origin}))

(defn latest-before
  "[origin entry] of the cell's latest section strictly before t, or nil;
  recorded as a read in direction `dir`."
  [r dir cell t]
  (when-let [[o e] (some-> (get (:book r) cell) (rsubseq < t) first)]
    (record! r dir cell o)
    [o e]))

(defn over
  "[[origin entry] ...] of the cell's sections at origins in [lo, hi],
  oldest first; each recorded."
  [r dir cell lo hi]
  (let [es (some-> (get (:book r) cell) (subseq >= lo <= hi))]
    (doseq [[o _] es] (record! r dir cell o))
    es))

(defn at
  "The cell's section at origin t, or nil; recorded when there is one."
  [r dir cell t]
  (when-let [e (get-in (:book r) [cell t])]
    (record! r dir cell t)
    e))

(defn reads "Every read the reader made, in order." [r] @(:reads r))

(defn coboundary
  "{neighbour gap}: a section's value less what each neighbour says (0
  where a neighbour has none)."
  [own neighbours]
  (into {} (for [[k v] neighbours] [k (if (some? v) (- (double own) (double v)) 0.0)])))

(defn inconsistency
  "The size of a coboundary: the sum of its gaps' sizes."
  [gaps]
  (reduce + 0.0 (map #(Math/abs (double %)) (vals gaps))))
