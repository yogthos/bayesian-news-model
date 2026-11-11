(ns bnm.node
  "The node of the recursive world model (docs/RECURSIVE.md §1), plain EDN:
  one kind of object at every place and time scale. A node's state is what
  it knows about the world (stocks, flows, buffer invariants, a hazard per
  kind with its frailty, a regime and tensions) and what it read to know
  it. Its forecast is something it issues, kept apart in the ledger's book,
  which no node reader can reach (C6).

    {:id [level place scale] :t origin-month
     :stocks  {stock-id {:level :sd :unit :known :usable {:share :sd} :record}}
     :flows   {flow-id {:rate :sd :from :to :unit :known :rigid}}
     :buffers {stock-id {:cover {w v} :gross-cover :drain-rate :restoration
                         :claim-gap :band :rigidity :displacement {w z}
                         :regime :buffered|:constrained|:unknown}}
     :hazard  {kind {:z :z-var :rate :window :p}}   ; p computed from the rest
     :regime  {:probs {:calm :strained :depleted} :drivers {...}}
     :tension [{:pair [a b] :gap :acc :since :resolved-at}]
     :gaps    {[:parent] g [:coarser scale] g}
     :reads   [{:dir :scale :place :origin | :known-at}]  ; bnm.sheaf reads
     :known   latest input's known-at
     :flags   #{...}}

  The issued forecast, apart:

    {:id [level place scale] :t origin :forecast {kind {:p :f :q ...}}}

  Today's lattice gives a node its event part only: from a book entry
  (`from-lattice-entry`) its hazard per kind, as a rate per month over the
  scale's window with no frailty yet (z = 0; step 3 of the build plan
  passes z). bnm.buffers records give its stocks and buffers
  (`from-buffer-rows`)."
  (:require [bnm.buffers :as b]
            [bnm.compose :as c]))

;; -- the hazard ------------------------------------------------------------------

(def p->rate "The per-month rate giving chance p in w months." c/p->rate)

(defn hazard-p
  "A hazard's chance of an onset in its window: 1 - exp(-λ w E[e^z]), z ~
  N(:z, :z-var), E[e^z] = exp(z + v/2) exactly (the lognormal mean)."
  ^double [{:keys [z z-var rate window]}]
  (let [m (Math/exp (+ (double (or z 0.0)) (* 0.5 (double (or z-var 0.0)))))]
    (- (Math/expm1 (- (* (double rate) (double window) m))))))

;; -- the schema -------------------------------------------------------------------

(def ^:private issued-keys #{:issued :forecast :weights :per-model})

(defn holds-issued?
  "Whether an issued forecast (or the averaging's weights) is anywhere in
  `x`'s maps."
  [x]
  (cond (map? x) (or (boolean (some issued-keys (keys x))) (boolean (some holds-issued? (vals x))))
        (sequential? x) (boolean (some holds-issued? x))
        :else false))

(defn- num? [x] (and (number? x) (not (Double/isNaN (double x))) (not (Double/isInfinite (double x)))))

(def ^:private bands #{:hedge :speculative :ponzi})
(def ^:private regimes #{:buffered :constrained :unknown})

(defn problems
  "What is wrong with `node` against the contract, as strings; empty when
  nothing is."
  [{:keys [id t stocks flows buffers hazard regime reads known] :as node}]
  (let [[level place scale] id]
    (vec
     (concat
      (when-not (and (vector? id) (= 3 (count id)) (keyword? level) (string? place) (keyword? scale))
        [(str "id must be [level place scale] (keyword string keyword): " (pr-str id))])
      (when-not (integer? t) [(str ":t must be a month (an integer): " (pr-str t))])
      (when (holds-issued? node) ["an issued forecast (or weights) is held in the state; it belongs in the ledger's book"])
      (for [[k {:keys [level sd]}] stocks
            :when (not (and (or (nil? level) (num? level)) (or (nil? sd) (and (num? sd) (>= sd 0.0)))))]
        (str "stock " k ": :level a number, :sd a number >= 0"))
      (for [[k {:keys [rate sd]}] flows
            :when (not (and (num? rate) (or (nil? sd) (and (num? sd) (>= sd 0.0)))))]
        (str "flow " k ": :rate a number, :sd a number >= 0"))
      (for [[k {:keys [band regime]}] buffers
            :when (not (and (or (nil? band) (bands band)) (or (nil? regime) (regimes regime))))]
        (str "buffer " k ": :band in " bands ", :regime in " regimes))
      (mapcat (fn [[k {:keys [z z-var rate window p] :as h}]]
                (cond (not (and (num? rate) (>= rate 0.0) (num? window) (pos? window)
                                (or (nil? z) (num? z)) (or (nil? z-var) (and (num? z-var) (>= z-var 0.0)))))
                      [(str "hazard " k ": :rate >= 0, :window > 0, :z-var >= 0")]
                      (and (some? p) (> (Math/abs (- (double p) (hazard-p h))) 1e-9))
                      [(str "hazard " k ": :p " p " is not 1 - exp(-rate window E[e^z]) = " (hazard-p h))]))
              hazard)
      (when-let [ps (:probs regime)]
        (when-not (and (every? num? (vals ps)) (< (Math/abs (- 1.0 (reduce + (vals ps)))) 1e-9))
          ["regime probabilities must sum to 1"]))
      (for [{:keys [dir origin known-at] :as rd} reads
            :let [at (or origin known-at)]
            :when (or (= :issued dir) (not (num? at)) (and (integer? t) (> at t)))]
        (str "read " (pr-str rd) ": after the node's origin, or of an issued forecast"))
      (when (and (num? known) (integer? t) (> known t)) [(str ":known " known " after the origin " t)])))))

(defn validate
  "`node` when it meets the contract, else an ex-info listing what does not."
  [node]
  (if-let [ps (seq (problems node))]
    (throw (ex-info (str "bnm.node: " (first ps)) {:problems (vec ps) :id (:id node)}))
    node))

;; -- from the lattice ---------------------------------------------------------------

(defn with-kind
  "`node` with the hazard of `kind` from a lattice book `entry` ({:p :s :f
  :q :strain ...}, its state) over a window of w months. An entry without
  :issued is one whose state is its combined forecast (no :state-from: the
  self-loop of loop audit issue 1), flagged :state-is-issued. The strain the
  lattice accumulated joins :tension."
  [node kind {:keys [p strain] :as entry} w]
  (let [rate (p->rate p w)]
    (cond-> (assoc-in node [:hazard kind] {:z 0.0 :z-var 0.0 :rate rate :window w :p (hazard-p {:rate rate :window w})})
      (some? strain) (update :tension (fnil conj []) {:pair [:lattice kind] :acc strain})
      (not (contains? entry :issued)) (update :flags (fnil conj #{}) :state-is-issued))))

(defn from-lattice-entry
  "A node [level place scale] at origin t from one kind's lattice book
  entry over its window of w months; never its issued forecast
  (`issued-from-lattice-entry`). opts :reads, what the node read (a
  trace's)."
  ([id t kind entry w] (from-lattice-entry id t kind entry w {}))
  ([id t kind entry w {:keys [reads]}]
   (with-kind {:id id :t t :stocks {} :flows {} :buffers {} :hazard {} :tension []
               :reads (vec reads) :known t :flags #{}}
              kind entry w)))

(defn issued-from-lattice-entry
  "The ledger's record of what the node issued for `kind` at t: the entry's
  :issued, or the entry itself when its state is its forecast."
  [id t kind entry]
  {:id id :t t :forecast {kind (or (:issued entry) (select-keys entry [:p :f :q]))}})

(defn split-book
  "A lattice book {[scale place] (sorted-map t entry)} of one kind as two:
  :states {[scale place] (sorted-map t node)}, readable by nodes, and
  :issued {[scale place] (sorted-map t forecast)}, the ledger's, which no
  node reads. opts: :kind, :level-of {place level}, :window-of {scale
  months}."
  [book {:keys [kind level-of window-of]}]
  (reduce (fn [acc [[scale place :as cell] byo]]
            (reduce (fn [acc [t e]]
                      (let [id [(level-of place) place scale]]
                        (-> acc
                            (update-in [:states cell] (fnil assoc (sorted-map)) t (from-lattice-entry id t kind e (window-of scale)))
                            (update-in [:issued cell] (fnil assoc (sorted-map)) t (issued-from-lattice-entry id t kind e)))))
                    acc byo))
          {:states {} :issued {}} book))

;; -- from bnm.buffers records ----------------------------------------------------------

(defn- by-window [rows inv]
  (into (sorted-map) (for [r rows :when (and (= inv (:invariant r)) (:window r))] [(:window r) (:value r)])))

(defn- one [rows inv] (first (filter #(= inv (:invariant %)) rows)))

(defn from-buffer-rows
  "{:stocks :buffers} of a node from bnm.buffers records, each
  {:record rec :series series} (and :rows, its invariants, else computed
  as of `as-of` on the series' own time axis, which :known keeps). The
  regime is the engine's switch read off the stock: :constrained when
  the usable stock is empty (min(wanted, available) binds), :buffered
  while it holds, :unknown without a known stock."
  ([entries as-of] (from-buffer-rows entries as-of {}))
  ([entries as-of opts]
   (reduce
    (fn [acc {:keys [record series rows]}]
      (let [rows (or rows (b/invariants record series as-of opts))
            st (b/latest record (:stock series) as-of)
            cover (one rows :cover)
            id (:id record)
            first-window (fn [inv] (:value (first (filter #(= inv (:invariant %)) rows))))
            cg (one rows :claim-gap)]
        (-> acc
            (assoc-in [:stocks id]
                      (cond-> {:level (some-> st :v double) :sd (some-> st :sd double) :unit (:unit record)
                               :known (when st (b/known-at record st)) :record record}
                        (:usable record) (assoc :usable (:usable record))))
            (assoc-in [:buffers id]
                      {:cover (by-window rows :cover)
                       :gross-cover (:value (one rows :gross-cover))
                       :drain-rate (first-window :drain-rate)
                       :restoration (first-window :restoration)
                       :claim-gap (:value cg) :band (:band cg)
                       :rigidity (:value (one rows :rigidity))
                       :displacement (by-window rows :displacement)
                       :regime (cond (nil? st) :unknown
                                     (contains? (:flags cover) :empty) :constrained
                                     :else :buffered)}))))
    {:stocks {} :buffers {}} entries)))

;; -- C11: the read graph of one step ---------------------------------------------------

(defn read-graph
  "{origin {[scale place] #{[scale place] read at that same origin}}} from
  a lattice trace: the reads within one step. Reads of earlier origins
  (down, self, a parent at t - per) and errors fed back are not edges of
  the step: they come from before it."
  [trace]
  (reduce (fn [g {:keys [scale place origin reads]}]
            (let [same (set (for [{s :scale p :place o :origin} reads :when (= o origin)] [s p]))]
              (update-in g [origin [scale place]] (fnil into #{}) same)))
          (sorted-map) trace))

(defn cycle-of
  "A cycle in `edges` {node #{node}} as a vector of nodes, or nil."
  [edges]
  (let [state (atom {})
        found (atom nil)]
    (letfn [(visit [n path]
              (when-not @found
                (case (@state n)
                  :open (reset! found (conj (vec (drop-while #(not= n %) path)) n))
                  :done nil
                  (do (swap! state assoc n :open)
                      (doseq [m (get edges n)] (visit m (conj path n)))
                      (swap! state assoc n :done)))))]
      (doseq [n (keys edges)] (visit n []))
      @found)))
