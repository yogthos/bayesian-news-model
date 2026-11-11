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
  known by t: a table row's data year is at least n years before t.

  An emit's value is a number (one value at the module's place) or rows
  [[iso value data-year source sd] ...] (strings as written; source and
  sd optional, sd the model's spread where it has one).

  What the runner enforces (review of phase A, October 2026; the checks
  are stricter than the first version, the interface otherwise the same):
  - same-year causality: an :observe gets only its stocks (state) and its
    :phase :level reads, never a same-year rate; it may emit only its
    declared :level variables; a step that re-emits a level must give the
    observed value (a level is its stocks' alone); a module that declares
    a :level emit must have an :observe (validate's :observe), else its
    level would be read a step late;
  - lags: a number of years at least 0 (validate's :lag) and a whole
    number of the run's steps (run throws), so no lag rounds to 0;
  - known years: every row emit is checked against its declaration as it
    is emitted (check-known!), before any reader or the table sees it;
  - families: a family covers a variable only if its pattern matches it,
    and two families meet only if some variable matches both
    (glob-overlap?); of several declarations covering a variable the
    exact one wins, then the most specific family, whatever their order.

  Phase B (bead bnm-4hv.6, bnm.coupled) steps a run one step at a time
  (runner, advance, result; run is their loop) so an ensemble's members
  go in lockstep and an analysis can replace their states (update-states),
  parameters (update-params) and the last step's emits as later steps
  read them (update-emits) between steps, never within one. A
  module :given to advance is not stepped (a module shared by the
  members, stepped once); a module with a :span [from to] steps only
  within it, and is read only within it; a module whose :dt is whole sees whole-number years in a
  finer run. The rules above hold unchanged in every member.

  The break as a regime switch (docs/PLAN.md 1.3, docs/BUFFERS.md; bead
  bnm-56c.4). A stock that declares :outflows is held by the engine, not
  the module:

    :stocks {k {:unit u :level v :init x :floor f :sharing :rigid-first
                :inflows  [{:var v} ...]
                :outflows [{:var v :rigid r :counterpart id :shortfall b} ...]}}

  :level is the module's :level emit that the engine gives (from :init, a
  number or a parameter, then integrated); the module needs no :observe
  for it and sees it in its inputs. Each outflow is one of the module's
  own rate emits, what it wants to send; each inflow is an emit or a read.
  Each step, after the module's step and before anything reads its emits,
  the stock can supply its level plus the step's inflows less its floor
  (:floor, a number, a parameter or an emit or read: a minimum operating
  level; 0 by default). Outflows wanted beyond that are cut to it, shared
  as :sharing says (sharings: :pro-rata by default, or :rigid-first, by
  the buffer schema's :rigid shares), and the stock is integrated with
  what was delivered. The stock is :buffered when nothing is cut,
  :constrained when something is. Each cut is booked as its outflow's
  :shortfall says (bookings: :arrears, :forced-saving, :unmet-demand,
  the default, or :price), so every cut lands somewhere; no held stock
  moves but by what was delivered, so the accounts close. A step's record
  per stock (regimes; result's :regimes {[module stock] [...]}): level,
  inflow, floor, available, wanted, delivered, shortfall, regime, the
  outflows cut, each outflow's wanted, delivered, shortfall and booking,
  the next level; holdings gives the levels and the bookings' totals, and
  arrears the claims they make, the holder's negative and its
  counterpart's positive (an outflow booked as arrears must name its
  :counterpart). When an outflow's :counterpart (\"<module>/<place>/
  <stock>\") is a module of the run, that module must read the flow and a
  stock it holds there must count it as an inflow (validate's :holdings),
  so what one account delivers another receives.

  A missing value is never a zero. When the level, an inflow, an outflow
  or the floor is missing or NaN, the step is :unknown: what went and the
  level after are NaN, so the stock stays unknown, an outflow not emitted
  stays not emitted, and its readers see NaN. The one exception is a
  lagged inflow before the run's start, which is 0: the run starts with
  nothing in transit. An inflow run negative is not the stock's to cut,
  so it can take the level below its floor; the record flags
  :below-floor.

  Propagation is the wiring, never a written chain: readers see only the
  delivered value of an outflow, so a cut outflow is a cut inflow to
  whatever reads it. A reader at lag 0 (later in the causal order) gets it
  the same step and its own stock can switch the same step; a reader at
  lag n gets it n years later. The whole-step rule holds: the cut is made
  before any reader sees the emit, and a held level is given in the level
  pass and changes only between steps. Nothing is cut that the step does
  not hold: the level at t, inflows and the floor as read at t. A module
  whose stocks hold none of this runs exactly as before. An analysis may
  not change, with update-emits, what a held stock settled (its level, its
  outflows, its own inflows and floor); it corrects the stock with
  update-holdings between steps. A module :given to advance takes its
  holdings and records from the given :holdings and :regimes when they are
  given (bnm.coupled gives a shared module's from the member that steps
  it). Not handled: row-valued flows (a held stock's flows are numbers at
  its place)."
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

(defn glob-overlap?
  "Whether some string matches both patterns `a` and `b` (each * any run
  of characters): \"cycles/*\" and \"cycles/*-gap\" overlap; \"cycles/*\" and
  \"cycles-x/*\" do not, nor \"cycles/*-price-gap\" and \"cycles/*-exposure\"."
  [a b]
  (let [a (str a) b (str b) na (count a) nb (count b)
        memo (atom {})
        star? (fn [s i n] (and (< i n) (= \* (.charAt ^String s i))))]
    ((fn go [i j]
       (let [k [i j]]
         (if (contains? @memo k)
           (@memo k)
           (let [r (cond (and (= i na) (= j nb)) true
                         (star? a i na) (or (go (inc i) j) (and (< j nb) (go i (inc j))))
                         (star? b j nb) (or (go i (inc j)) (and (< i na) (go (inc i) j)))
                         (or (= i na) (= j nb)) false
                         :else (and (= (.charAt ^String a i) (.charAt ^String b j)) (go (inc i) (inc j))))]
             (swap! memo assoc k r)
             r))))
     0 0)))

(defn- var-match?
  "Whether an emitted variable (or family) `e` gives the read `r` (some
  variable is both)."
  [e r]
  (cond (and (glob? e) (glob? r)) (glob-overlap? e r)
        (glob? e) (and (string? r) (glob-match? e r))
        (glob? r) (and (string? e) (glob-match? r e))
        :else (= e r)))

(defn- specificity
  "Sort key of a declaration's variable: an exact one first, then the
  family with the most literal characters."
  [v]
  (if (glob? v) [1 (- (count (str/replace v "*" "")))] [0 0]))

(defn- norm-unit [u] (when u (str/replace (str/trim u) #"\s+" " ")))

(defn- lag-of [r] (or (:lag r) 0))
(defn- phase-of [x] (or (:phase x) :rate))
(defn- key-of [r] (or (:as r) (:var r)))

(defn emit-for
  "The declaration in module `m`'s emits that covers variable `v`: an exact
  one before a family, the most specific family (most literal characters)
  before a wider one, whatever their order."
  [m v]
  (or (first (filter #(= (:var %) v) (:emits m)))
      (first (sort-by (comp specificity :var) (filter #(and (glob? (:var %)) (var-match? (:var %) v)) (:emits m))))))

(defn- emitters
  "[[module emit] ...] of the modules (not `self`) whose emits give read
  `r`: exact matches if there are any, else families."
  [ms self r]
  (let [cands (for [m ms :when (not= (:module m) self)
                    :when (or (nil? (:from r)) (= (:from r) (:module m)))
                    e (:emits m) :when (var-match? (:var e) (:var r))]
                [m e])
        exact (filter (fn [[_ e]] (= (:var e) (:var r))) cands)
        pick (sort-by (comp specificity :var second) (if (seq exact) exact cands))]
    ;; one declaration per module: its exact one if any, else its most specific
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

;; -- stocks the engine holds: the break as a regime switch ---------------------------

(def bookings
  "Where the shortfall of a cut outflow is booked (an outflow's :shortfall,
  :unmet-demand when none is given): :arrears, a claim of the counterpart
  on the holder that accumulates (the holder's liability, the
  counterpart's asset); :forced-saving, the counterpart keeps what it
  would have paid for the flow (its saving above plan, the holder's
  receipts below it, by the same amount); :unmet-demand, the counterpart
  wanted it and went without; :price, the delivered amount cleared at a
  price higher by wanted over delivered. Only arrears are a stock; the
  others are records. None moves a held stock, so none breaks its
  accounts."
  #{:arrears :forced-saving :unmet-demand :price})

(def sharings
  "How a cut is shared among a stock's outflows (its :sharing, :pro-rata
  when none is given): :pro-rata, each by the same share; :rigid-first,
  each outflow's rigid share (its :rigid, the buffer schema's, 0 when none
  is given) before any flexible part, then the flexible parts pro rata."
  #{:pro-rata :rigid-first})

(defn- held-order
  "[ordered left]: module `m`'s held stocks in the order their flows between
  them need, and those that feed each other in a ring (cannot be ordered)."
  [m]
  (let [held (vec (filter (comp seq :outflows val) (sort-by (comp str key) (:stocks m))))
        feeds? (fn [[_ a] [_ b]] (some (set (map :var (:outflows a))) (map :var (:inflows b))))]
    (loop [left held out []]
      (if (empty? left)
        [out []]
        (let [ready (first (filter (fn [s] (not-any? #(and (not= % s) (feeds? % s)) left)) left))]
          (if ready
            (recur (vec (remove #(= % ready) left)) (conj out ready))
            [out left]))))))

(defn held-stocks
  "[[stock declaration] ...]: the stocks of module `m` that the engine
  holds (those that declare :outflows), in the order the module's own
  flows between them need (a stock before one its outflow feeds)."
  [m]
  (let [[out left] (held-order m)] (into out left)))

(defn- held-errors
  "Errors in module `m`'s held stocks (see the namespace), {:error :holdings}."
  [m]
  (let [err (fn [k msg] {:error :holdings :module (:module m) :var k :msg (str (:module m) "'s stock " (name k) ": " msg)})
        own (set (map :var (:emits m)))
        levels (set (keep #(when (= :level (phase-of %)) (:var %)) (:emits m)))
        rates (set (keep #(when (not= :level (phase-of %)) (:var %)) (:emits m)))
        read-keys (set (for [r (:reads m) :when (not (:source r))] (key-of r)))
        param? (fn [x] (and (keyword? x) (contains? (:params m) x)))
        share? (fn [x] (or (nil? x) (and (number? x) (<= 0.0 (double x) 1.0))))
        held (held-stocks m)
        outs (for [[_ s] held f (:outflows s)] (:var f))
        ;; a ring among the module's own stocks cannot be ordered
        cyc (seq (second (held-order m)))]
    (concat
     (for [[v c] (frequencies outs) :when (> c 1)] (err :outflows (str v " is the outflow of two stocks")))
     (when cyc [(err :inflows "its stocks feed each other within the step; read one a step late")])
     (for [[k s] held
           e (concat
              (when-not (levels (:level s)) [(str "its :level " (pr-str (:level s)) " is not a declared :level emit")])
              (let [i (:init s)] (when-not (or (number? i) (param? i) (fn? i)) [(str "its :init " (pr-str i) " is not a number or a parameter")]))
              (let [f (:floor s)]
                (when-not (or (nil? f) (and (number? f) (>= (double f) 0.0)) (param? f) (and (string? f) (or (own f) (read-keys f))))
                  [(str "its :floor " (pr-str f) " is not a number at least 0, a parameter, an emit or a read")]))
              (when-not (or (nil? (:sharing s)) (sharings (:sharing s))) [(str "its :sharing " (pr-str (:sharing s)) " is not one of " (sort sharings))])
              (for [f (:outflows s) :when (not (rates (:var f)))] (str "its outflow " (pr-str (:var f)) " is not one of its own rate emits"))
              (for [f (:inflows s) :when (not (or (own (:var f)) (read-keys (:var f))))] (str "its inflow " (pr-str (:var f)) " is neither an emit nor a read"))
              (for [f (:outflows s) :when (not (or (nil? (:shortfall f)) (bookings (:shortfall f))))]
                (str (:var f) "'s :shortfall " (pr-str (:shortfall f)) " is not one of " (sort bookings)))
              (for [f (:outflows s) :when (and (= :arrears (:shortfall f)) (not (and (string? (:counterpart f)) (seq (:counterpart f)))))]
                (str (:var f) " is booked as arrears but names no :counterpart to hold the claim"))
              (for [f (concat (:inflows s) (:outflows s)) :when (not (share? (:rigid f)))] (str (:var f) "'s :rigid is not a share in [0, 1]")))]
       (err k e)))))

(defn- counterpart-errors
  "Held outflows whose :counterpart (\"<module label>/<place>/<stock>\", as
  bnm.buffers/stock-id writes it) is a module of the run that does not
  read the flow, or reads it but holds the named stock without counting it
  as an inflow: what was delivered would leave one account and enter none."
  [ms]
  (let [vn (fn [x] (if (keyword? x) (name x) (str x)))
        label (fn [m] (or (:label m) (name (:module m))))]
    (for [m ms [_ s] (held-stocks m) f (:outflows s)
          :let [[lab place stock] (str/split (str (:counterpart f)) #"/")
                v (:var f)]
          c ms :when (and (= lab (label c)) (or (nil? place) (= place (vn (:place c)))))
          :let [rs (filter #(and (not (:source %)) (var-match? v (:var %))) (:reads c))
                held-there (when stock (some (fn [[k cs]] (when (= stock (vn k)) cs)) (held-stocks c)))
                msg (cond (empty? rs) (str (:module c) ", its counterpart, does not read it")
                          (and held-there (not-any? (set (map key-of rs)) (map :var (:inflows held-there))))
                          (str (:module c) ", its counterpart, reads it but its stock " stock " does not count it as an inflow"))]
          :when msg]
      {:error :holdings :module (:module m) :var v
       :msg (str (:module m) "'s outflow " v " goes to " (:counterpart f) ", but " msg ": what was delivered would vanish")})))

(defn share-cut
  "What each of the outflows `ws` [{:wanted w :rigid r} ...] delivers when
  at most `cap` can go (a rate), shared by `sharing` (see sharings); each
  wanted amount when they fit."
  [sharing ws cap]
  (let [w (mapv #(max 0.0 (double (:wanted %))) ws)
        total (reduce + 0.0 w)
        cap (max 0.0 (double cap))
        pro (fn [xs c] (let [s (reduce + 0.0 xs)] (if (pos? s) (mapv #(* % (/ c s)) xs) (mapv (constantly 0.0) xs))))]
    (cond (<= total cap) w
          (= :rigid-first sharing)
          (let [rig (mapv (fn [x f] (* x (double (or (:rigid f) 0.0)))) w ws)
                r (reduce + 0.0 rig)]
            (if (>= r cap)
              (pro rig cap)
              (mapv + rig (pro (mapv - w rig) (- cap r)))))
          :else (pro w cap))))

(defn- flow-of
  "The value of flow `v` this step: the module's own emit, else its read."
  [in e v]
  (let [x (if (contains? e v) (get e v) (get in v))] (when (number? x) (double x))))

(defn- settle
  "Stock `k` (declaration `s`, level `level`) through a step of `dt` at t:
  {:record r :emits e (outflows as delivered) :next level}. Available is
  the level plus the step's inflows less the floor; outflows wanted beyond
  it are cut by the stock's sharing, and each shortfall booked. `pre`:
  the read keys whose lag reaches before the run's start; nothing was sent
  then (the run starts with nothing in transit), so such an inflow is 0,
  while any other missing value leaves the step unknown."
  [id k s level in e ps t dt pre]
  (let [dt (double dt)
        known? (fn [x] (and (number? x) (not (Double/isNaN (double x)))))
        val-of (fn [x] (cond (number? x) (double x)
                             (keyword? x) (let [p (get ps x)] (when (number? p) (double p)))
                             (string? x) (flow-of in e x)))
        ins (for [f (:inflows s) :let [v (:var f) x (flow-of in e v)]]
              [v (if (and (nil? x) (contains? pre v) (not (contains? e v))) 0.0 x)])
        outs (for [f (:outflows s)] [f (flow-of in e (:var f))])
        floor (if (some? (:floor s)) (val-of (:floor s)) 0.0)
        flags (cond-> #{}
                (not-every? (comp known? second) ins) (conj :inflow-missing)
                (not-every? (comp known? second) outs) (conj :outflow-missing)
                (not (known? floor)) (conj :floor-missing)
                (not (known? level)) (conj :level-missing))]
    (if (seq flags)
      ;; a value the step needs is missing (nil) or not a number (NaN): what
      ;; could go is unknown, so what went and the level after are unknown
      ;; too (NaN), never a zero; an outflow not emitted stays not emitted,
      ;; one run backwards stays as emitted
      (let [nan Double/NaN]
        {:record {:t t :module id :stock k :level level :inflow nan :floor floor :available nan
                  :wanted nan :delivered nan :shortfall nan :regime :unknown :cut []
                  :flows (into {} (for [[f x] outs]
                                    [(:var f) (cond-> {:wanted (if (known? x) (max 0.0 x) nan) :delivered nan :shortfall nan}
                                                (:counterpart f) (assoc :counterpart (:counterpart f)))]))
                  :next nan :flags flags}
         :emits (reduce (fn [e [f x]] (if (or (nil? x) (and (known? x) (neg? x))) e (assoc e (:var f) nan))) e outs)
         :next nan})
      (let [;; an outflow run backwards is an inflow, never cut
            back (reduce + 0.0 (for [[_ x] outs :when (neg? x)] (- x)))
            inflow (+ back (reduce + 0.0 (map second ins)))
            wanted (mapv (fn [[f x]] {:wanted (max 0.0 x) :rigid (:rigid f)}) outs)
            avail (max 0.0 (- (+ level (* dt inflow)) floor))
            total (reduce + 0.0 (map :wanted wanted))
            binds (> (* dt total) (* avail (+ 1.0 1e-12)))
            given (if binds (share-cut (or (:sharing s) :pro-rata) wanted (/ avail dt)) (mapv :wanted wanted))
            flows (into {} (for [[[f x] w d] (map vector outs wanted given)
                                 :let [short (- (:wanted w) d)]]
                             [(:var f) (cond-> {:wanted (:wanted w) :delivered d :shortfall short}
                                         (:counterpart f) (assoc :counterpart (:counterpart f))
                                         (pos? short) (assoc :booked (or (:shortfall f) :unmet-demand) :amount (* dt short))
                                         (and (pos? short) (= :price (:shortfall f))) (assoc :price-factor (if (pos? d) (/ (:wanted w) d) Double/POSITIVE_INFINITY)))]))
            delivered (reduce + 0.0 given)
            cut (vec (for [[f] outs :when (pos? (get-in flows [(:var f) :shortfall]))] (:var f)))
            next (+ level (* dt (- inflow delivered)))]
        {:record {:t t :module id :stock k :level level :inflow inflow :floor floor :available avail
                  :wanted total :delivered delivered :shortfall (- total delivered)
                  :regime (if (seq cut) :constrained :buffered) :cut cut :flows flows :next next
                  ;; an inflow run negative is not the stock's to cut, and can
                  ;; take it below its floor
                  :flags (cond-> #{} (< next (- floor 1e-9)) (conj :below-floor))}
         ;; readers see what moved; an outflow run backwards is left as emitted
         :emits (reduce (fn [e [[f x] d]] (if (neg? x) e (assoc e (:var f) d))) e (map vector outs given))
         :next next}))))

(defn arrears
  "{party amount}: the claims that arrears booked in `holdings` (the
  runner's, or a result's :holdings) make, by the declarations `ms`: each
  holder's stock (\"<module>/<place>/<stock>\") owes a negative amount,
  each counterpart holds the same amount positive, so they sum to zero."
  [holdings ms]
  (let [by (into {} (map (juxt :module identity)) ms)
        cp (fn [id k v] (some #(when (= v (:var %)) (:counterpart %)) (get-in by [id :stocks k :outflows])))
        place (fn [m] (let [p (:place m)] (if (keyword? p) (name p) (str p))))]
    (reduce (fn [acc [[id k v b] x]]
              (if (= :arrears b)
                (let [m (by id)
                      holder (str (or (:label m) (name id)) "/" (place m) "/" (name k))]
                  (-> acc (update holder (fnil - 0.0) x) (update (cp id k v) (fnil + 0.0) x)))
                acc))
            {} (:books holdings))))

(defn validate
  "Errors in the declarations, each {:error :module :var :msg}: a read
  with no emitter or source (:no-emitter), with several (:ambiguous), a
  unit unlike its emitter's (:unit), a known-year rule shorter than its
  emitter's (:known), a level read of a rate emit (:phase), a lag that is
  not a number at least 0 (:lag), a :level emit of a module with no
  :observe (:observe; it would be read a step late) unless it is a held
  stock's level, a lag-0 edge against W C F R D P (:group-order), lag-0
  edges in a cycle (:cycle), a module named twice (:duplicate), a held
  stock declared wrongly (:holdings)."
  [ms]
  (let [err (fn [k m v msg] {:error k :module (:module m) :var v :msg msg})
        ids (map :module ms)
        g (into {} (map (juxt :module :group)) ms)
        dup (for [[id c] (frequencies ids) :when (> c 1)] {:error :duplicate :module id :msg (str "module " id " declared " c " times")})
        lags (for [m ms r (:reads m) :let [l (:lag r)] :when (and (some? l) (not (and (number? l) (>= l 0))))]
               (err :lag m (:var r) (str (:module m) " reads " (:var r) " at lag " (pr-str l) ": a lag is a number of years, at least 0")))
        holds (concat (mapcat held-errors ms) (counterpart-errors ms))
        observes (for [m ms :when (not (:observe m))
                       :let [held (set (map (comp :level val) (held-stocks m)))]
                       e (:emits m) :when (and (= :level (phase-of e)) (not (held (:var e))))]
                   (err :observe m (:var e) (str (:module m) " emits " (:var e) " as a level but has no :observe: it would be read a step late")))
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
    (vec (concat dup lags observes reads cycles holds))))

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

(def ^:private data-keys [:module :label :group :place :doc :reads :emits :stocks :params :dt :span :table :tool])

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
  "(fn [module v]) -> the declaration covering v; throws when module emits
  a variable it did not declare (cached per variable)."
  [ms]
  (let [by (into {} (map (juxt :module identity)) ms)
        ok (atom {})]
    (fn [id v]
      (or (get @ok [id v])
          (if-let [e (emit-for (by id) v)]
            (do (swap! ok assoc [id v] e) e)
            (throw (ex-info (str "bnm.engine: " id " emits " v ", not declared") {:module id :var v})))))))

(defn- rows? [x] (and (sequential? x) (seq x) (sequential? (first x))))

(defn check-known!
  "Throws when a row of `module`'s emit `v` at t ([iso value data-year
  ...]) reports a data year sooner than its declaration's known-year rule
  (`decl`, the emit covering v) allows: a value for data year y is first
  known at y + n. A number, or a row with no data year, is not checked."
  [module decl v t x]
  (when-let [k (and (rows? x) (get-in decl [:known :data-year]))]
    (doseq [[_ _ dy] x :when (and dy (seq (str dy)))]
      (when (< (- (double t) (double (parse-long (str dy)))) k)
        (throw (ex-info (str "bnm.engine: " (name module) " emits " v " for data year " dy " at " t ", sooner than its known rule (+" k ")")
                        {:module module :var v :data-year dy :t t}))))))

(defn- same-value? [a b] (or (= a b) (and (number? a) (number? b) (not= a a) (not= b b))))

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

(defn- whole-t
  "t as a whole number when it is one and module `m` steps in whole years:
  a yearly module sees the years its tables are keyed by, whatever the
  run's step."
  [m t]
  (if (and (not (integer? t)) (integral? (:dt m)) (integral? t)) (long t) t))

(defn- in-span? [m t] (let [[lo hi] (:span m)] (and (or (nil? lo) (>= (double t) (double lo))) (or (nil? hi) (<= (double t) (double hi))))))

(defn runner
  "A run of the modules (as run's arguments) that has not stepped yet:
  advance steps it once, result gives what run returns. Between steps
  its module states can be read (states) and replaced (update-states),
  which is where an analysis (bnm.coupled) updates a member."
  [ms {:keys [from to dt params on-step keep] :or {keep true}}]
  (check! ms)
  (let [dt (or dt (apply min (map :dt ms)))
        whole (and (integral? from) (integral? dt) (integer? from) (integer? dt))
        span (/ (- (double to) (double from)) (double dt))
        n (long (Math/round span))
        _ (when (> (Math/abs (- span n)) 1e-9)
            (throw (ex-info (str "bnm.engine: from " from " to " to " is not a whole number of steps of " dt) {})))
        every (into {} (for [m ms :let [k (/ (double (:dt m)) (double dt))]]
                         (do (when (> (Math/abs (- k (Math/round k))) 1e-9)
                               (throw (ex-info (str "bnm.engine: " (:module m) "'s dt " (:dt m) " is not a multiple of " dt) {})))
                             [(:module m) (Math/round k)])))
        plan (into {} (for [m ms]
                        [(:module m)
                         (vec (for [r (:reads m) :when (not (:source r))
                                    :let [[em] (first (emitters ms (:module m) r))]]
                                {:key (key-of r) :var (:var r) :emitter (:module em) :glob (glob? (:var r))
                                 :level (= :level (phase-of r))
                                 :lag-steps (let [k (/ (double (lag-of r)) (double dt))]
                                              (when (> (Math/abs (- k (Math/round k))) 1e-9)
                                                (throw (ex-info (str "bnm.engine: " (:module m) " reads " (:var r) " at lag " (lag-of r)
                                                                     ", not a whole number of steps of " dt) {:module (:module m) :var (:var r)})))
                                              (Math/round k))}))]))
        ps (into {} (for [m ms] [(:module m) (merge (param-values (:params m)) (get params (:module m)))]))
        held (into {} (for [m ms :let [h (held-stocks m)] :when (seq h)] [(:module m) h]))]
    {:held held
     :holdings {:levels (into {} (for [[id h] held]
                                 [id (into {} (for [[k s] h :let [i (:init s)]]
                                                [k (double (cond (number? i) i (keyword? i) (get-in ps [id i]) :else (i (ps id))))]))]))
              :books {}}
     :regimes {}
     :by (into {} (map (juxt :module identity)) ms)
     :order (order ms)
     :t-at (fn [i] (if whole (+ from (* i dt)) (+ (double from) (* i (double dt)))))
     :n n :every every :plan plan :ps ps
     :max-lag (reduce max 0 (map :lag-steps (mapcat val plan)))
     :declared! (declared-check ms)
     :on-step on-step :keep keep
     :i 0
     :states (into {} (for [m ms :let [init (:init m)]] [(:module m) (if (fn? init) (init (ps (:module m))) init)]))
     :hist {} :out {}}))

(defn done? "Whether the runner has stepped to its last time." [rn] (> (:i rn) (:n rn)))

(defn states "The modules' states, {module state}." [rn] (:states rn))

(defn update-states "The runner with its states (f states)." [rn f] (update rn :states f))

(defn params "The modules' parameters, {module {name value}}." [rn] (:ps rn))

(defn update-params
  "The runner with its modules' parameters (f {module {name value}}), as
  the steps after see them (a recalibration between steps)."
  [rn f] (update rn :ps f))

(defn update-emits
  "The runner with module `id`'s emits of the last step it took replaced by
  (f emits), as the next steps read them at a lag: an analysis that
  corrects a level must correct what its readers see of it (bnm.sfc-
  analysis rescales a member's nominal scale, the output among it). Throws
  when a variable it changes was read at lag 0 within that step: those
  readers have consumed it, and the step may not see two values of one
  emit (the whole-step rule)."
  [rn id f]
  (let [i (dec (:i rn))
        old (or (get-in rn [:hist i id]) (get-in rn [:cur id]))
        new (when old (f old))
        changed (when old (filter #(not (same-value? (get old %) (get new %))) (distinct (concat (keys old) (keys new)))))
        within (distinct (for [[reader es] (:plan rn) {:keys [var emitter lag-steps]} es
                               :when (and (= emitter id) (zero? lag-steps))
                               v changed :when (var-match? v var)]
                           [reader v]))
        ;; what its held stocks settled: their levels, the outflows they
        ;; delivered, and the inflows and floors of its own they read
        settled (set (for [[_ s] (get-in rn [:held id])
                           v (concat [(:level s) (when (string? (:floor s)) (:floor s))] (map :var (:inflows s)) (map :var (:outflows s)))
                           :when v]
                       v))
        held-changed (filter settled changed)]
    (when (seq held-changed)
      (throw (ex-info (str "bnm.engine: " id "'s emits " (pr-str (vec held-changed)) " are what its held stocks settled; correct the stock with update-holdings")
                      {:module id :settled (vec held-changed)})))
    (when (seq within)
      (throw (ex-info (str "bnm.engine: " id "'s emits " (pr-str (vec within)) " were read within their step; an analysis may not change them")
                      {:module id :read-by (vec within)})))
    (cond-> rn
      (get-in rn [:hist i id]) (update-in [:hist i id] f)
      (get-in rn [:cur id]) (update-in [:cur id] f))))

(defn holdings
  "The stocks the engine holds: {:levels {module {stock level}} :books
  {[module stock outflow booking] amount}}, the levels as the next step
  starts and each booking's cumulative amount (arrears outstanding)."
  [rn] (:holdings rn))

(defn update-holdings
  "The runner with its held stocks (f holdings): an analysis that corrects a
  held stock's level, between steps."
  [rn f] (update rn :holdings f))

(defn regimes
  "{[module stock] [record per step]}: each held stock's step, its regime
  and what was cut (see the namespace)."
  [rn] (:regimes rn))

(defn result
  "What run returns: {:t [...] :emits {module {var [value per t]}}
  :states {module state}}, and with stocks the engine holds :regimes
  {[module stock] [record per step]} and :holdings (see holdings)."
  [rn]
  (cond-> (assoc (:out rn) :states (:states rn))
    (seq (:held rn)) (assoc :regimes (:regimes rn) :holdings (:holdings rn))))

(defn advance
  "The runner stepped once (see run). opts :given {module {:level emits
  :rate emits}}: those modules are not stepped, their emits this step
  are the given ones (a module shared by an ensemble's members, stepped
  once); with :holdings {:levels {stock level} :books {key amount}} and
  :regimes {[module stock] records} its held stocks are those. The step's emits are left as :cur {module emits} (:level-cur
  after the level pass) and :stepped #{module}, and its time as :t."
  ([rn] (advance rn {}))
  ([{:keys [by order t-at every plan ps max-lag declared! on-step keep i states hist out held] :as rn} {:keys [given]}]
   (let [t (t-at i)
         {lo :level ro :rate} order
         steps? (fn [id] (and (zero? (mod i (every id))) (in-span? (by id) t)))
         ;; the held stocks' levels at t, as their modules' level emits
         held-levels (fn [id] (when-let [h (held id)]
                                (into {} (for [[k s] h] [(:level s) (get-in rn [:holdings :levels id k])]))))
         ;; levels
         cur (reduce (fn [cur id]
                       (let [m (by id) tm (whole-t m t) g (get given id) hl (held-levels id)]
                         (cond (and g (steps? id)) (assoc cur id (if (contains? g :level) (:level g) (get-in hist [(dec i) id])))
                               (and (steps? id) (:observe m))
                               (let [in (merge (gather (filter :level (plan id)) cur hist i) hl)
                                     e ((:observe m) (states id) in (ps id) tm)]
                                 (doseq [[v x] e :let [d (declared! id v)]]
                                   (when (not= :level (phase-of d))
                                     (throw (ex-info (str "bnm.engine: " id "'s observe gives " v ", not a declared level") {:module id :var v})))
                                   (when (and hl (contains? hl v) (not (same-value? x (hl v))))
                                     (throw (ex-info (str "bnm.engine: " id "'s observe gives " v ", a stock the engine holds, as " (pr-str x)
                                                          " (held at " (pr-str (hl v)) ")") {:module id :var v})))
                                   (check-known! id d v tm x))
                                 (assoc cur id (merge e hl)))
                               ;; outside its span a module has nothing to read
                               (not (in-span? m t)) cur
                               (and hl (steps? id)) (assoc cur id hl)
                               :else (assoc cur id (get-in hist [(dec i) id])))))
                     {} lo)
         level-cur cur
         ;; rates; a held stock's outflows cut to what it can supply before
         ;; anything reads them (the whole-step rule)
         [cur states hd regs]
         (reduce (fn [[cur states hd regs] id]
                   (let [m (by id) tm (whole-t m t) g (get given id)]
                     (cond (not (steps? id)) [cur states hd regs]
                           ;; a shared module's held stocks are the stepping member's
                           g [(assoc cur id (:rate g)) states
                              (cond-> hd (:holdings g) (-> (assoc-in [:levels id] (get-in g [:holdings :levels]))
                                                           (update :books #(merge (into {} (remove (fn [[[m]]] (= m id))) %) (get-in g [:holdings :books])))))
                              (if (:regimes g) (merge regs (:regimes g)) regs)]
                           :else
                           (let [hl (held-levels id)
                                 in (merge (gather (plan id) cur hist i) hl)
                                 pre (set (for [p (plan id) :when (> (:lag-steps p) i)] (:key p)))
                                 {s :state e :emits} ((:step m) (states id) in (ps id) tm (:dt m))
                                 lv (when (or (:observe m) hl) (cur id))
                                 _ (doseq [[v x] e :let [d (declared! id v)]]
                                     (when (and lv (= :level (phase-of d)) (not (and (contains? lv v) (same-value? x (lv v)))))
                                       (throw (ex-info (str "bnm.engine: " id "'s step changes the level " v " at " tm
                                                            " (observed " (pr-str (get lv v)) ", stepped " (pr-str x) "): a level is its stocks' alone")
                                                       {:module id :var v :t tm})))
                                     (check-known! id d v tm x))
                                 [e hd regs]
                                 (reduce (fn [[e hd regs] [k sd]]
                                           (let [{r :record e :emits nx :next} (settle id k sd (get-in hd [:levels id k]) in e (ps id) tm (:dt m) pre)]
                                             [e
                                              (-> (assoc-in hd [:levels id k] nx)
                                                  (update :books (fn [b] (reduce (fn [b [v f]] (if (:booked f) (update b [id k v (:booked f)] (fnil + 0.0) (:amount f)) b))
                                                                                 b (:flows r)))))
                                              (update regs [id k] (fnil conj []) r)]))
                                         [e hd regs] (held id))
                                 e (merge lv e)]
                             (when on-step (on-step id tm e))
                             [(assoc cur id e) (assoc states id s) hd regs]))))
                 [cur states (:holdings rn) (:regimes rn)] ro)
         stepped (set (filter steps? (keys by)))
         hist (cond-> (assoc hist i cur) true (dissoc (- i (max 1 max-lag))))
         out (if keep
               (-> (update out :t (fnil conj []) t)
                   (update :emits (fn [em] (into (or em {})
                                                 (for [id (keys by)]
                                                   [id (let [prev (get em id {}) e (when (stepped id) (cur id))
                                                             ks (into (set (keys prev)) (keys e))]
                                                         (into {} (for [k ks] [k (conj (get prev k (vec (repeat i nil))) (get e k))])))])))))
               out)]
     (assoc rn :i (inc i) :t t :cur cur :level-cur level-cur :stepped stepped :states states :hist hist :out out
            :holdings hd :regimes regs))))

(defn run
  "Steps the modules from `from` to `to` (both included) by `dt` (the
  finest module's by default), each module every its own :dt, in causal
  order: per step the level pass, then the rate pass. A module with a
  :span [from to] steps only within it (its state kept outside, its
  emits not read outside). A module
  whose :dt is whole sees an integral time as a whole number. opts:
  :params {module {name value}} over the declared values, :on-step (fn
  [module t emits]) called for every module's emits each step it steps,
  :keep (true) whether to return the emits. Returns {:t [...] :emits
  {module {var [value per t]}} :states {module final state}}; a value a
  module did not emit at a t is nil there. The declarations are checked
  first. runner and advance step the same run one step at a time."
  [ms opts]
  (loop [rn (runner ms opts)]
    (if (done? rn) (result rn) (recur (advance rn)))))

;; -- the coupled table ----------------------------------------------------------

(def table-header ["iso" "year" "module" "variable" "value" "sd" "data_year" "known" "source"])

(defn- place-iso [place] (if (= place :world) "WLD" (name place)))

(defn table-rows
  "The coupled table's rows of `module`'s emits at t: a number is one row
  at the module's place (WLD for :world); a vector of [iso value data-year
  source sd] rows (value and sd strings, as written; sd optional, empty
  where the model gives no spread) is those rows; a map (a matrix passed
  between modules) is none. The module column is its :label, else its
  name; an integral t is written as a whole year. Throws when a row's
  data year is published sooner than the emit's known-year rule allows
  (check-known!)."
  [m t emits]
  (let [id (or (:label m) (name (:module m)))
        t (if (and (not (integer? t)) (integral? t)) (long t) t)
        decl (memoize (fn [v] (emit-for m v)))]
    (doseq [[v x] emits] (check-known! (:module m) (decl v) v t x))
    (vec (for [[v x] emits :when (and (some? x) (not (map? x)))
               [iso value dy src sd] (if (number? x) [[(place-iso (:place m)) (str x) nil nil nil]] x)]
           [iso t id v value (or sd "") (or dy "") t (if (seq src) (str "model:" src) "model")]))))

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
