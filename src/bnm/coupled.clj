(ns bnm.coupled
  "The coupled engine, phase B (bead bnm-4hv.6; docs/COUPLED-SYSTEMS.md
  6c-6e): every subsystem's modules in one causal run, as an ensemble
  assimilating data each year, with sheaf consistency written as
  diagnostics.

  run-ensemble steps `members` copies of the modules in lockstep
  (bnm.engine/runner, advance), each by the engine's rules (levels only
  from :observe, rates from :step, whole-step lags, every emit checked
  against its known year). Modules named :shared are the same in every
  member (the stock tables, the allocation panel): member 0 steps them
  and the others are given their emits, so they are run once. After each
  step, each analysis (bnm.sfc-analysis/analysis) records what it needs
  of the members and, through :assimilate-through, updates their states
  with the observations known that year (augur.enkf, the identities kept
  by projection). One member and no analysis is the deterministic run:
  it reproduces phase A's tables exactly (golden-lines).

  What a step writes (on-step, the coupled table's rows): a shared
  module's emits as member 0 gave them; any other module's, with more
  than one member, as the members' median and spread (sd), a number at
  its place and a row per iso and data year; matrices are not written.

  free-run: from `from`, assimilating through `origin`, then `years`
  free (no data after the origin): what bnm-cvs scores at 3-5 years."
  (:require [augur.math :as m]
            [bnm.engine :as eng]
            [bnm.modules.stocks :as ms]
            [bnm.states :as states]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; -- the members' emits as one ------------------------------------------------------

(defn- median [xs]
  (let [xs (vec (sort xs)) n (count xs) h (quot n 2)]
    (if (odd? n) (nth xs h) (/ (+ (nth xs (dec h)) (nth xs h)) 2.0))))

(defn- spread [xs]
  (let [n (count xs) mu (/ (reduce + 0.0 xs) n)]
    (if (< n 2) 0.0 (Math/sqrt (/ (reduce + 0.0 (map #(let [d (- % mu)] (* d d)) xs)) (dec n))))))

(defn- rows? [x] (and (sequential? x) (seq x) (sequential? (first x))))

(defn aggregate
  "One emit map from the members' (`es`, one each): per variable, numbers
  as [[place median nil nil sd]] (`place` the module's iso), rows as one
  row per iso, data year and source with the members' median and sd;
  matrices and nils left out."
  [place es]
  (let [ks (distinct (mapcat keys es))]
    (into {} (for [k ks
                   :let [xs (keep #(get % k) es)]
                   :when (seq xs)
                   :let [v (cond (every? number? xs) [[place (str (median (map double xs))) nil nil (str (spread (map double xs)))]]
                                 (every? rows? xs)
                                 (let [g (group-by (fn [[iso _ dy src]] [iso dy src]) (apply concat xs))]
                                   (vec (for [[[iso dy src] rs] (sort-by (comp str key) g)
                                              :let [vs (keep #(some-> (second %) str not-empty parse-double) rs)]
                                              :when (seq vs)]
                                          [iso (str (median vs)) dy src (str (spread vs))])))
                                 :else nil)]
                   :when (seq v)]
               [k v]))))

;; -- the ensemble ---------------------------------------------------------------------

(defn run-ensemble
  "Runs the modules `(ms-of i)` of each member i < :members (1) from
  :from to :to (:dt the finest module's), stepped in lockstep. opts:
  :shared #{module} the same in every member (stepped by member 0 only;
  ms-of must give them identically), :analyses [{:record (fn [memory
  runner t]) -> memory, :analyse (fn [{:runners :memories :t :g}]) ->
  {:runners :memories}}], :assimilate-through (the last year an analysis
  updates the members; nil every year), :seed, :params, :on-step (fn
  [module t emits]) the coupled table's emits each step (see the
  namespace), :keep (false) whether to return each member's emits.
  {:t [...] :members [engine results when :keep] :memories [...]
  :states [each member's final states] :analysed [t ...]}."
  [ms-of {:keys [members from to dt shared analyses assimilate-through seed params on-step keep]
          :or {members 1 shared #{} keep false}}]
  (let [mss (vec (for [i (range members)]
                   (let [ms (vec (ms-of i))]
                     (if (zero? i) ms (mapv #(if (shared (:module %)) (assoc % :init nil) %) ms)))))
        by (into {} (map (juxt :module identity)) (first mss))
        g (m/rng (or seed 1))
        opts {:from from :to to :dt dt :params params :keep keep}]
    (loop [rs (mapv #(eng/runner % opts) mss)
           mems (vec (repeat members (vec (repeat (count analyses) nil))))
           ts []
           analysed []]
      (if (eng/done? (first rs))
        {:t ts :members (when keep (mapv eng/result rs)) :states (mapv eng/states rs) :memories mems :analysed analysed}
        (let [r0 (eng/advance (first rs))
              given (into {} (for [id shared :when ((:stepped r0) id)]
                               [id {:level (get (:level-cur r0) id) :rate (get (:cur r0) id)}]))
              rs (into [r0] (map #(eng/advance % {:given given}) (rest rs)))
              t (:t r0)
              _ (when on-step
                  (doseq [id (keys by) :when ((:stepped r0) id)]
                    (on-step id t (if (or (= 1 members) (shared id))
                                    (get (:cur r0) id)
                                    (aggregate (if (= :world (:place (by id))) "WLD" (name (:place (by id))))
                                               (map #(get (:cur %) id) rs))))))
              mems (vec (for [[i r] (map-indexed vector rs)]
                          (vec (for [[j a] (map-indexed vector analyses)]
                                 ((:record a) (get-in mems [i j]) r t)))))
              on? (and (seq analyses) (> members 1) (or (nil? assimilate-through) (<= (double t) (double assimilate-through))))
              [rs mems hit] (if on?
                              (reduce (fn [[rs mems hit] [j a]]
                                        (let [{rs' :runners ms' :memories} ((:analyse a) {:runners rs :memories (mapv #(nth % j) mems) :t t :g g})]
                                          [rs' (mapv #(assoc %1 j %2) mems ms') (or hit (not (identical? rs rs')))]))
                                      [rs mems false] (map-indexed vector analyses))
                              [rs mems false])]
          (recur rs mems (conj ts t) (cond-> analysed hit (conj t))))))))

(defn free-run
  "run-ensemble from :from, the analyses updating the members through
  :origin, then :years more with none (a free run: nothing known after
  the origin enters). The other opts as run-ensemble's."
  [ms-of {:keys [from origin years] :as opts}]
  (run-ensemble ms-of (assoc opts :from from :to (+ origin years) :assimilate-through origin)))

;; -- the coupled table ----------------------------------------------------------------

(defn- integral-t? [t] (or (integer? t) (== t (Math/floor (double t)))))

(defn run!
  "The coupled run written as one table: dir/state.csv (bnm.engine's
  columns; whole years only, so World3's half-year steps are not
  written), dir/graph.{edn,txt} and dir/tables.edn (the stock tables'
  stamps, bnm.states). Table modules are stepped from copies of their
  tables sorted by year (bnm.modules.stocks), read as the years pass.
  opts as run-ensemble's ({:from :to ...}); the ms-of's member 0 modules
  are the graph's. {:rows {module n} :leftover {module rows of a table
  not emitted} :result run-ensemble's}."
  [ms-of dir opts]
  (let [ms0 (vec (ms-of 0))
        _ (eng/write-graph dir ms0)
        _ (states/write-stamps! dir (distinct (keep :table ms0)))
        {:keys [write close]} (eng/table-writer (str dir "/state.csv") ms0)
        spill-dir (str dir "/.spill")
        opened (atom [])
        streamed (memoize (fn [id] (let [m (first (filter #(= id (:module %)) ms0))] (ms/stream-module m spill-dir opened))))
        ms-of* (fn [i] (mapv #(if (:table %) (streamed (:module %)) %) (ms-of i)))
        rows (atom {})
        last-states (atom nil)]
    (try
      (let [r (run-ensemble ms-of* (assoc opts :keep false
                                          :on-step (fn [id t e]
                                                     (when (integral-t? t)
                                                       (swap! rows update id (fnil + 0) (count (eng/table-rows (first (filter #(= id (:module %)) ms0)) t e)))
                                                       (write id t e)))))]
        {:rows (merge (zipmap (map :module ms0) (repeat 0)) @rows)
         :leftover (into {} (for [[id s] (first (:states r)) :when (map? s)]
                              [id (cond (contains? s :lines) (+ (:skipped s) (count (:lines s)))
                                        (contains? s :by-year) (reduce + 0 (map count (vals (:by-year s))))
                                        :else 0)]))
         :result (dissoc r :memories :states)})
      (finally
        (close)
        (doseq [rd @opened] (.close ^java.io.Closeable rd))
        (let [d (io/file spill-dir)]
          (when (.exists d)
            (doseq [f (.listFiles d)] (.delete f))
            (.delete d)))))))

;; -- golden checks --------------------------------------------------------------------

(defn table-lines
  "The lines (header dropped) of the coupled table at `path` whose module
  column is in `modules` (names), in file order."
  [path modules]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          mi ((zipmap (str/split h #",") (range)) "module")
          mods (set modules)]
      (vec (filter #(mods (nth (str/split % #"," -1) mi "")) ls)))))

(defn golden-lines
  "{:same bool :a n :b n :first-diff [i a b]}: the lines of table `a` of
  `modules` against those of table `b` (all of b's when `modules-b` is
  nil); `sorted?` compares them as sorted (a table written in another
  order, the same rows)."
  [a b modules {:keys [sorted? modules-b]}]
  (let [la (table-lines a modules)
        lb (if modules-b (table-lines b modules-b) (with-open [r (io/reader b)] (vec (rest (line-seq r)))))
        [la lb] (if sorted? [(vec (sort la)) (vec (sort lb))] [la lb])
        d (first (keep-indexed (fn [i [x y]] (when (not= x y) [i x y])) (map vector la lb)))]
    {:same (and (= (count la) (count lb)) (nil? d)) :a (count la) :b (count lb) :first-diff d}))
