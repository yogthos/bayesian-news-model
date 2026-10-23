(ns bnm.engine
  "The coupled engine (docs/COUPLED-SYSTEMS.md section 6; bead bnm-4hv.9,
  phase A): modules declared as data with step functions, the coupling
  graph built from their declarations, and a runner that steps them in
  causal order and writes one table.

  A module is

    {:module   :keen                 ; its name
     :group    :D                    ; W C F R D P (section 4a)
     :place    :country              ; or :world, :cell, :region
     :reads    [{:var v :unit u :lag 0 :known {:data-year n} :phase :rate
                 :from module :source s :as key} ...]
     :emits    [{:var v :unit u :known {:data-year n} :phase :rate
                 :channel :D1} ...]
     :stocks   {name {:unit u :info bool}}
     :params   {name value-or-{:value x :prior p :pool level}}
     :dt       1                     ; its time step, in years
     :init     state-or-(fn [params])
     :observe  (fn [state inputs params t]) -> {var value}  ; optional
     :step     (fn [state inputs params t dt]) -> {:state s :emits {var value}}}

  Variables are strings (\"keen/w\", the stock tables' names) or keywords
  (World3's); a string with * names a family (\"chokepoint/*\"). A read is
  of another module's emit, or of a data source (:source, the file or
  series it comes from). Its :lag is in years: 0 is this step's value, only
  from a module earlier in the causal order; a back edge must be lagged.
  :from says which module when two emit the variable for different places;
  :as is the key the step sees (to read one variable at two lags).

  Each step has two passes, as system dynamics orders its equations
  (levels, then auxiliaries and rates): first every module's :observe gives
  its :level emits, functions of its stocks at t (and of other level emits
  read with :phase :level); then every :step, which may read any level emit
  and the rate emits of modules before it. Lag-0 edges form a DAG in each
  pass. A module that has no :observe emits only in the second pass.

  Known years: an emit's :known {:data-year n} is the publication lag of
  what it reports (a value for data year y is first known at y + n); a
  reader may not assume one shorter. A step at year t emits only what is
  known by t: a table row's data year is at least n years before t."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def groups "The causal order within the year (section 6b)." [:W :C :F :R :D :P])

(def ^:private group-index (zipmap groups (range)))

;; -- variables -------------------------------------------------------------

(defn- glob? [v] (and (string? v) (str/includes? v "*")))

(defn glob-match?
  "Whether `s` matches `pattern`, whose * stands for any run of characters."
  [pattern s]
  (let [s (str s) ps (vec (str/split pattern #"\*" -1))]
    (if (= 1 (count ps))
      (= pattern s)
      (let [head (first ps) tail (peek ps) end (- (count s) (count tail))]
        (and (str/starts-with? s head) (str/ends-with? s tail) (<= (count head) end)
             (loop [i (count head) [p & more] (subvec ps 1 (dec (count ps)))]
               (if (nil? p)
                 (<= i end)
                 (let [j (str/index-of s p i)]
                   (and j (<= (+ j (count p)) end) (recur (+ j (count p)) more))))))))))

(defn- prefix [v] (first (str/split v #"\*" -1)))

(defn- var-match?
  "Whether an emitted variable (or family) `e` gives the read `r`."
  [e r]
  (cond (and (glob? e) (glob? r)) (let [a (prefix e) b (prefix r)] (or (str/starts-with? a b) (str/starts-with? b a)))
        (glob? e) (and (string? r) (glob-match? e r))
        (glob? r) (and (string? e) (glob-match? r e))
        :else (= e r)))

(defn- norm-unit [u] (when u (str/replace (str/trim u) #"\s+" " ")))

(defn- lag-of [r] (or (:lag r) 0))
(defn- phase-of [x] (or (:phase x) :rate))
(defn- key-of [r] (or (:as r) (:var r)))

(defn emit-for
  "The declaration in module `m`'s emits that covers variable `v`: an exact
  one before a family."
  [m v]
  (or (first (filter #(= (:var %) v) (:emits m)))
      (first (filter #(and (glob? (:var %)) (var-match? (:var %) v)) (:emits m)))))

(defn- emitters
  "[[module emit] ...] of the modules (not `self`) whose emits give read
  `r`: exact matches if there are any, else families."
  [ms self r]
  (let [cands (for [m ms :when (not= (:module m) self)
                    :when (or (nil? (:from r)) (= (:from r) (:module m)))
                    e (:emits m) :when (var-match? (:var e) (:var r))]
                [m e])
        exact (filter (fn [[_ e]] (= (:var e) (:var r))) cands)
        pick (if (seq exact) exact cands)]
    ;; one declaration per module: its exact one if any
    (vals (reduce (fn [acc [m e]] (if (contains? acc (:module m)) acc (assoc acc (:module m) [m e]))) {} pick))))

;; -- the graph ---------------------------------------------------------------

(defn edges
  "The coupling graph's edges, one per read of a module's emit:
  {:from :to :var :lag :phase (the read's pass) :emit-phase :unit :channel}.
  Reads of data sources and reads with no single emitter are left out
  (validate reports the latter)."
  [ms]
  (vec (for [m ms r (:reads m) :when (not (:source r))
             :let [es (emitters ms (:module m) r)] :when (= 1 (count es))
             :let [[em e] (first es)]]
         {:from (:module em) :to (:module m) :var (:var r) :lag (lag-of r) :phase (phase-of r)
          :emit-phase (phase-of e) :unit (:unit e) :channel (:channel e)})))

(defn- same-year
  "The lag-0 edges that order pass `pass` (:level or :rate)."
  [es pass]
  (filter #(and (zero? (:lag %)) (= pass (:phase %)) (= pass (:emit-phase %))) es))

(defn- topo
  "[order remaining]: modules `ids` in topological order of `es` (from ->
  to), ties broken by group, then by their place in `ids`; `remaining` the
  modules on or behind a cycle."
  [ids group-of es]
  (let [pos (zipmap ids (range))
        rank (fn [id] [(get group-index (group-of id) -1) (pos id)])
        es (distinct (for [{:keys [from to]} es :when (not= from to)] [from to]))
        succ (reduce (fn [m [a b]] (update m a (fnil conj []) b)) {} es)
        indeg (reduce (fn [m [_ b]] (update m b inc)) (zipmap ids (repeat 0)) es)]
    (loop [indeg indeg out []]
      (let [ready (sort-by rank (for [[id d] indeg :when (zero? d)] id))]
        (if-let [id (first ready)]
          (recur (reduce (fn [m b] (if (contains? m b) (update m b dec) m)) (dissoc indeg id) (succ id)) (conj out id))
          [out (vec (sort-by rank (keys indeg)))])))))

(defn order
  "{:level [...] :rate [...]}: the modules in the order of each pass."
  [ms]
  (let [ids (mapv :module ms) g (into {} (map (juxt :module :group)) ms) es (edges ms)]
    {:level (first (topo ids g (same-year es :level)))
     :rate (first (topo ids g (same-year es :rate)))}))

(defn validate
  "Errors in the declarations, each {:error :module :var :msg}: a read
  with no emitter or source (:no-emitter), with several (:ambiguous), a
  unit unlike its emitter's (:unit), a known-year rule shorter than its
  emitter's (:known), a level read of a rate emit (:phase), a lag-0 edge
  against W C F R D P (:group-order), lag-0 edges in a cycle (:cycle), a
  module named twice (:duplicate)."
  [ms]
  (let [err (fn [k m v msg] {:error k :module (:module m) :var v :msg msg})
        ids (map :module ms)
        g (into {} (map (juxt :module :group)) ms)
        dup (for [[id c] (frequencies ids) :when (> c 1)] {:error :duplicate :module id :msg (str "module " id " declared " c " times")})
        reads (for [m ms r (:reads m) :when (not (:source r))
                    :let [es (emitters ms (:module m) r) v (:var r)]
                    e (cond (empty? es) [(err :no-emitter m v (str (:module m) " reads " v ": no module emits it and no :source"))]
                            (> (count es) 1) [(err :ambiguous m v (str (:module m) " reads " v " from " (vec (map (comp :module first) es)) ": say :from"))]
                            :else (let [[em e] (first es)
                                        ru (norm-unit (:unit r)) eu (norm-unit (:unit e))
                                        rk (get-in r [:known :data-year]) ek (get-in e [:known :data-year])]
                                    (cond-> []
                                      (not= ru eu) (conj (err :unit m v (str (:module m) " reads " v " in " (pr-str (:unit r)) ", " (:module em) " emits " (pr-str (:unit e)))))
                                      (and rk ek (< rk ek)) (conj (err :known m v (str (:module m) " reads " v " known at +" rk ", " (:module em) " publishes it at +" ek)))
                                      (and (zero? (lag-of r)) (= :level (phase-of r)) (not= :level (phase-of e)))
                                      (conj (err :phase m v (str (:module m) " observes " v ", a rate of " (:module em) ": not known before the rates")))
                                      (and (zero? (lag-of r)) (g (:module em)) (g (:module m))
                                           (> (group-index (g (:module em)) -1) (group-index (g (:module m)) 99)))
                                      (conj (err :group-order m v (str (:module em) " (" (name (g (:module em))) ") -> " (:module m) " (" (name (g (:module m)))
                                                                      ") within the year runs against W C F R D P: lag it"))))))]
                e)
        es (edges ms)
        cycles (for [pass [:level :rate]
                     :let [[_ left] (topo (vec ids) g (same-year es pass))] :when (seq left)]
                 {:error :cycle :module (first left) :msg (str "lag-0 " (name pass) " edges in a cycle through " left)})]
    (vec (concat dup reads cycles))))

(defn check!
  "The modules, or an error listing what is wrong with their declarations."
  [ms]
  (let [es (validate ms)]
    (when (seq es)
      (throw (ex-info (str "bnm.engine: " (str/join "; " (map :msg es))) {:errors es})))
    ms))

(defn causal-order
  "{:order [module ...] :groups [[group [module ...]] ...] :text
  \"W -> C -> ...\" :violations [...]}: the step order (the rate pass) and
  the groups it passes through; a violation is a group stepped after a
  later one, or a lag-0 edge against the order."
  [ms]
  (let [o (:rate (order ms))
        g (into {} (map (juxt :module :group)) ms)
        runs (reduce (fn [acc id] (let [k (g id)] (if (= k (first (peek acc))) (conj (pop acc) [k (conj (second (peek acc)) id)]) (conj acc [k [id]]))))
                     [] o)
        seen (map first runs)
        ixs (keep group-index seen)]
    {:order o
     :groups runs
     :text (str/join " -> " (map #(if % (name %) "?") seen))
     :violations (vec (concat (when (not= ixs (sort ixs)) [(str "groups out of order: " (vec seen))])
                              (when (not= (count seen) (count (distinct seen))) [(str "a group stepped in two runs: " (vec seen))])
                              (map :msg (filter #(= :group-order (:error %)) (validate ms)))))}))

(def ^:private data-keys [:module :group :place :doc :reads :emits :stocks :params :dt :table :tool])

(defn graph-data
  "The declarations (data only), the edges and the orders, for
  data/derived/coupled/graph.edn."
  [ms]
  {:modules (mapv #(select-keys % data-keys) ms)
   :edges (edges ms)
   :order (order ms)
   :causal (select-keys (causal-order ms) [:order :text :violations])})

(defn dump
  "The coupling graph as text: each module's group, place, reads and emits,
  the edges, and the causal step order."
  [ms]
  (let [es (edges ms)
        co (causal-order ms)
        by (into {} (map (juxt :module identity)) ms)
        fmt-var (fn [v] (if (keyword? v) (name v) v))]
    (str/join "\n"
              (concat
               ["# modules (group, place, dt): reads <- emitter [lag], emits [channel]"]
               (for [id (:order co) :let [m (by id)]]
                 (str (name id) " (" (some-> (:group m) name) ", " (some-> (:place m) name) ", dt " (:dt m) ")"
                      "\n  reads: " (str/join ", " (for [r (:reads m)]
                                                     (str (fmt-var (:var r))
                                                          (if (:source r) (str " <- source " (:source r))
                                                              (let [e (first (filter #(and (= (:to %) id) (= (:var %) (:var r)) (= (:lag %) (lag-of r))) es))]
                                                                (str " <- " (some-> (:from e) name))))
                                                          (when (pos? (lag-of r)) (str " [" (lag-of r) "]")))))
                      "\n  emits: " (str/join ", " (for [e (:emits m)] (str (fmt-var (:var e)) (when-let [c (:channel e)] (str " [" (name c) "]")))))))
               ["" "# edges: from -> to  variable  lag  pass  channel"]
               (for [e es] (str (name (:from e)) " -> " (name (:to e)) "  " (fmt-var (:var e)) "  " (:lag e) "  " (name (:phase e)) "  " (some-> (:channel e) name)))
               ["" (str "# causal step order: " (:text co))]
               (for [[k ids] (:groups co)] (str (if k (name k) "?") ": " (str/join ", " (map name ids))))
               (when (seq (:violations co)) (cons "# violations" (:violations co)))))))

;; -- running -----------------------------------------------------------------

(defn- param-values [ps] (update-vals (or ps {}) #(if (map? %) (:value %) %)))

(defn- integral? [x] (or (integer? x) (== x (Math/floor (double x)))))

(defn- declared-check
  "(fn [module v]) that throws when module emits a variable it did not
  declare (cached per variable)."
  [ms]
  (let [by (into {} (map (juxt :module identity)) ms)
        ok (atom #{})]
    (fn [id v]
      (when-not (contains? @ok [id v])
        (if (emit-for (by id) v)
          (swap! ok conj [id v])
          (throw (ex-info (str "bnm.engine: " id " emits " v ", not declared") {:module id :var v})))))))

(defn- gather
  "The step's inputs from `cur` (this step's emits by module) and `hist`
  (past steps' emits, by step index): {key value}, a family read as {var
  value} of every variable it matches."
  [plan cur hist i]
  (reduce (fn [acc {:keys [key var emitter lag-steps glob]}]
            (let [src (if (zero? lag-steps) cur (get hist (- i lag-steps)))
                  em (get src emitter)]
              (cond (nil? em) acc
                    glob (assoc acc key (into {} (filter (fn [[k _]] (var-match? var k))) em))
                    (contains? em var) (assoc acc key (get em var))
                    :else acc)))
          {} plan))

(defn run
  "Steps the modules from `from` to `to` (both included) by `dt` (the
  finest module's by default), each module every its own :dt, in causal
  order: per step the level pass, then the rate pass. opts: :params
  {module {name value}} over the declared values, :on-step (fn [module t
  emits]) called for every module's emits each step it steps, :keep (true)
  whether to return the emits. Returns {:t [...] :emits {module {var
  [value per t]}} :states {module final state}}; a value a module did not
  emit at a t is nil there. The declarations are checked first."
  [ms {:keys [from to dt params on-step keep] :or {keep true}}]
  (check! ms)
  (let [dt (or dt (apply min (map :dt ms)))
        whole (and (integral? from) (integral? dt) (integer? from) (integer? dt))
        n (long (Math/round (/ (- (double to) (double from)) (double dt))))
        t-at (fn [i] (if whole (+ from (* i dt)) (+ (double from) (* i (double dt)))))
        every (into {} (for [m ms :let [k (/ (double (:dt m)) (double dt))]]
                         (do (when (> (Math/abs (- k (Math/round k))) 1e-9)
                               (throw (ex-info (str "bnm.engine: " (:module m) "'s dt " (:dt m) " is not a multiple of " dt) {})))
                             [(:module m) (Math/round k)])))
        by (into {} (map (juxt :module identity)) ms)
        {lo :level ro :rate} (order ms)
        plan (into {} (for [m ms]
                        [(:module m)
                         (vec (for [r (:reads m) :when (not (:source r))
                                    :let [[em] (first (emitters ms (:module m) r))]]
                                {:key (key-of r) :var (:var r) :emitter (:module em) :glob (glob? (:var r))
                                 :level (= :level (phase-of r))
                                 :lag-steps (Math/round (/ (double (lag-of r)) (double dt)))}))]))
        max-lag (reduce max 0 (map :lag-steps (mapcat val plan)))
        ps (into {} (for [m ms] [(:module m) (merge (param-values (:params m)) (get params (:module m)))]))
        declared! (declared-check ms)]
    (loop [i 0
           states (into {} (for [m ms :let [init (:init m)]] [(:module m) (if (fn? init) (init (ps (:module m))) init)]))
           hist {}
           out {}]
      (let [t (t-at i)
            steps? (fn [id] (zero? (mod i (every id))))
            ;; levels
            cur (reduce (fn [cur id]
                          (let [m (by id)]
                            (if-let [obs (and (steps? id) (:observe m))]
                              (let [in (gather (filter :level (plan id)) cur hist i)
                                    e (obs (states id) in (ps id) t)]
                                (doseq [v (keys e)] (declared! id v))
                                (assoc cur id e))
                              (assoc cur id (get-in hist [(dec i) id])))))
                        {} lo)
            ;; rates
            [cur states] (reduce (fn [[cur states] id]
                                   (if (steps? id)
                                     (let [m (by id)
                                           in (gather (plan id) cur hist i)
                                           {s :state e :emits} ((:step m) (states id) in (ps id) t (:dt m))
                                           e (merge (when (:observe m) (cur id)) e)]
                                       (doseq [v (keys e)] (declared! id v))
                                       (when on-step (on-step id t e))
                                       [(assoc cur id e) (assoc states id s)])
                                     [cur states]))
                                 [cur states] ro)
            hist (cond-> (assoc hist i cur) true (dissoc (- i (max 1 max-lag))))
            out (if keep
                  (-> (update out :t (fnil conj []) t)
                      (update :emits (fn [em] (into (or em {})
                                                    (for [id (keys by)]
                                                      [id (let [prev (get em id {}) e (when (steps? id) (cur id))
                                                                ks (into (set (keys prev)) (keys e))]
                                                            (into {} (for [k ks] [k (conj (get prev k (vec (repeat i nil))) (get e k))])))])))))
                  out)]
        (if (>= i n)
          (assoc out :states states)
          (recur (inc i) states hist out))))))

;; -- the coupled table ----------------------------------------------------------

(def table-header ["iso" "year" "module" "variable" "value" "sd" "data_year" "known" "source"])

(defn- place-iso [place] (if (= place :world) "WLD" (name place)))

(defn table-rows
  "The coupled table's rows of `module`'s emits at t: a number is one row
  at the module's place (WLD for :world); a vector of [iso value data-year
  source] rows (value a string, as written) is those rows. Throws when a
  row's data year is published sooner than the emit's known-year rule
  allows."
  [m t emits]
  (let [id (name (:module m))
        rule (memoize (fn [v] (get-in (emit-for m v) [:known :data-year])))]
    (vec (for [[v x] emits :when (some? x)
               [iso value dy src] (if (number? x) [[(place-iso (:place m)) (str x) nil nil]] x)]
           (do (when-let [k (and dy (seq dy) (rule v))]
                 (when (< (- (double t) (double (parse-long dy))) k)
                   (throw (ex-info (str "bnm.engine: " id " emits " v " for data year " dy " at " t ", sooner than its known rule (+" k ")")
                                   {:module (:module m) :var v :data-year dy :t t}))))
               [iso t id v value "" (or dy "") t (if (seq src) (str "model:" src) "model")])))))

(defn- csv-field [x] (cond (nil? x) "" (keyword? x) (name x) :else (str x)))

(defn table-writer
  "{:write (fn [module t emits]) :close (fn [])}: the coupled table at
  `path`, rows written as the modules step (an :on-step for run)."
  [path ms]
  (io/make-parents path)
  (let [w (io/writer path)
        by (into {} (map (juxt :module identity)) ms)]
    (.write w (str (str/join "," table-header) "\n"))
    {:write (fn [id t emits]
              (doseq [r (table-rows (by id) t emits)]
                (.write w (str (str/join "," (map csv-field r)) "\n"))))
     :close (fn [] (.close w))}))

(defn write-graph
  "dir/graph.edn (graph-data) and dir/graph.txt (dump)."
  [dir ms]
  (io/make-parents (str dir "/x"))
  (spit (str dir "/graph.edn") (pr-str (graph-data ms)))
  (spit (str dir "/graph.txt") (str (dump ms) "\n")))
