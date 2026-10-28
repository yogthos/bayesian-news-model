(ns bnm.sfc-analysis
  "Assimilation into the SFC core's ensemble (bead bnm-4hv.6 phase B;
  docs/COUPLED-SYSTEMS.md 6c, 6d): each year, the data that became known
  that year update the members' balance sheets, flows of funds, wage
  share and employment, and the accounting identities hold after the
  update to rounding.

  The observations come with the publication lags of bnm.modules.sfc's
  observes: a data year's balance sheet (OECD Table 720), sector net
  lending (Table 14 B9) and wage share (D1 over GDP) two years later (or
  at the panel's known year where it gives one), Keen's employment rate
  at keen.csv's known year. They are of the past, so the state updated
  is a window: the opening balance sheet of the oldest data year
  observed, then for each year to now its flow of funds (transactions
  by sector and instrument, net lending), its closing balance sheet, wage
  share and employment (and its output, read for ratios, not written
  back). The update is the ensemble Kalman smoother over that window
  (augur.enkf; a member's past and present covary in the ensemble), so
  a past observation moves the present through the members' own
  dynamics.

  The identities, linear in the window, are constraints the update is
  projected onto (augur.enkf/update-constrained):
    - each year's closing balance sheet is the opening revalued (equity
      at rho) plus the transactions, position by position (so the
      balance sheet and its flow of funds agree);
    - each sector's transactions sum to its net lending (government's
      plus the redundant equation's buffer, its own per member);
    - each instrument's transactions sum to zero over the sectors (so
      the balance sheet stays closed, as the window's opening is);
    - the window's opening closes, instrument by instrument.
  Net lending summing to zero follows. The year's transaction-flow
  matrix is the accounts module's, booked from the updated state the
  next year, and closes by construction. The members then start the next
  year from the updated closing balance sheet (bnm.modules.sfc's :open),
  with its updated flow of funds, wage share and employment.

  Weakly coupled (6c): an SFC observation updates the SFC core's state
  only. No localisation: the window has a few hundred entries and the
  ensemble tens of members, so its spurious covariances are what a
  registered comparison would test against a smaller state."
  (:require [augur.enkf :as enkf]
            [augur.math :as m]
            [bnm.coupled :as cp]
            [bnm.engine :as eng]
            [bnm.modules.sfc :as msfc]
            [bnm.sfc :as sfc]))

;; -- the state as a vector -----------------------------------------------------------

(def positions
  "The balance sheet's 40 positions [sector instrument side]."
  (vec (for [s sfc/sectors k sfc/instruments side [:a :l]] [s k side])))

(defn- bsm->v [b] (mapv (fn [[s k side]] (sfc/pos b s k side)) positions))

(defn- v->bsm [xs]
  (reduce (fn [b [[s k side] x]] (assoc-in b [s k side] x)) {} (map vector positions xs)))

(def ^:private np (count positions))
(def ^:private ns' (count sfc/sectors))
(def ^:private per-year (+ np ns' np 3))

(defn- layout
  "Indices of the window over years `ys`: the opening, then per year its
  transactions, net lending, closing balance sheet, w, lambda, Y."
  [ys]
  {:open 0
   :year (into {} (for [[j y] (map-indexed vector ys)]
                    (let [b (+ np (* j per-year))]
                      [y {:tx b :nl (+ b np) :end (+ b np ns') :w (+ b np ns' np) :lambda (+ b np ns' np 1) :Y (+ b np ns' np 2)}])))
   :dim (+ np (* (count ys) per-year))})

(defn- row [dim kvs] (let [r (double-array dim)] (doseq [[i c] kvs] (aset r i (+ (aget r i) (double c)))) (vec r)))

(defn constraints
  "{:a rows :b-of (fn [buffers]) -> b}: the identities over the window
  of years `ys` (layout's), revaluation at `rho` (a number, or {year
  rate})."
  [ys rho]
  (let [{:keys [open year dim]} (layout ys)
        pi (into {} (map-indexed (fn [i p] [p i]) positions))
        chain (for [[j y] (map-indexed vector ys)
                    :let [{:keys [tx end]} (year y)
                          prev (if (zero? j) open (:end (year (nth ys (dec j)))))]
                    [i [_ k _]] (map-indexed vector positions)]
                (row dim [[(+ end i) 1.0] [(+ prev i) (- (if (= k :equity) (+ 1.0 (double (if (map? rho) (rho y) rho))) 1.0))] [(+ tx i) -1.0]]))
        sector (for [y ys :let [{:keys [tx nl]} (year y)] [si s] (map-indexed vector sfc/sectors)]
                 (row dim (cons [(+ nl si) -1.0]
                                (for [k sfc/instruments side [:a :l]] [(+ tx (pi [s k side])) (if (= side :a) 1.0 -1.0)]))))
        instr (for [y ys :let [{:keys [tx]} (year y)] k sfc/instruments]
                (row dim (for [s sfc/sectors side [:a :l]] [(+ tx (pi [s k side])) (if (= side :a) 1.0 -1.0)])))
        closed (for [k sfc/instruments] (row dim (for [s sfc/sectors side [:a :l]] [(+ open (pi [s k side])) (if (= side :a) 1.0 -1.0)])))
        gov (.indexOf ^java.util.List sfc/sectors :gov)]
    {:a (vec (concat chain sector instr closed))
     :b-of (fn [buffers]
             (vec (concat (repeat (count chain) 0.0)
                          (for [y ys [si _] (map-indexed vector sfc/sectors)] (if (= si gov) (double (get buffers y 0.0)) 0.0))
                          (repeat (+ (count instr) (count closed)) 0.0))))}))

(defn- to-vector
  "A member's window over `ys` from its memory {year record}."
  [mem ys]
  (let [{:keys [dim year]} (layout ys)
        x (double-array dim)
        put (fn [at xs] (doseq [[i v] (map-indexed vector xs)] (aset x (+ at i) (double v))))]
    (put 0 (bsm->v (:open (mem (first ys)))))
    (doseq [y ys :let [r (mem y) {:keys [tx nl end w lambda Y]} (year y)]]
      (put tx (bsm->v (:tx (:fof r))))
      (put nl (map #(double (get-in r [:fof :nl %] 0.0)) sfc/sectors))
      (put end (bsm->v (:end r)))
      (aset x w (double (:w r))) (aset x lambda (double (:lambda r))) (aset x Y (double (:Y r))))
    (vec x)))

(defn- revaluation [open rho] (into {} (for [s sfc/sectors] [s (* rho (- (sfc/pos open s :equity :a) (sfc/pos open s :equity :l)))])))

(defn- from-vector
  "The member's memory with the window over `ys` replaced by `x` (its
  output kept, its flows of funds' revaluation recomputed from the
  updated openings)."
  [mem ys x]
  (let [{:keys [year]} (layout ys)
        sub (fn [at n] (subvec x at (+ at n)))]
    (first
     (reduce (fn [[mem open] y]
               (let [{:keys [tx nl end w lambda]} (year y)
                     r (mem y)
                     rho (double (get-in r [:fof :rho] 0.0))
                     endb (v->bsm (sub end np))
                     fof (assoc (:fof r) :tx (v->bsm (sub tx np))
                                :nl (zipmap sfc/sectors (sub nl ns'))
                                :reval (revaluation open rho))]
                 [(assoc mem y (assoc r :open open :end endb :fof fof :w (nth x w) :lambda (nth x lambda))) endb]))
             [mem (v->bsm (sub 0 np))] ys))))

(defn identity-gaps
  "The largest identity gap of a member's memory record, over its output:
  the closing balance sheet's (bnm.sfc/bsm-gap) and its flow of funds'
  (bnm.sfc/fof-gap: the change in net worth against net lending and
  revaluation)."
  [{:keys [open end fof Y]}]
  (/ (max (sfc/bsm-gap end) (sfc/bsm-gap open) (sfc/fof-gap open end fof)) (double Y)))

;; -- observations ----------------------------------------------------------------------

(defn- known-year [ob k] (or (get ob k) (+ (:year ob) sfc/vintage)))

(defn known-panel
  "The SFC `panel` as known at year `t`: a data year's flows (and its
  output) only once :known-flows, its balance sheet only once
  :known-stocks (each the data year plus the vintage where the panel
  gives none), a year with neither left out."
  [panel t]
  (update-vals panel
               (fn [rows]
                 (into {} (keep (fn [[y ob]]
                                  (let [f? (<= (known-year ob :known-flows) t) s? (<= (known-year ob :known-stocks) t)]
                                    (when (or f? s?)
                                      [y (cond-> ob
                                           (not f?) (assoc :flows-ok false :f {})
                                           (not s?) (dissoc :b :stocks-source))]))))
                       rows))))

(defn relearn-at
  "(fn [t]) -> `iso`'s parameters relearned at t (bnm.sfc/learn, opts
  `opts`) on the panel as known at t (known-panel), data years to t less
  the vintage; keen.csv's rows known by t (bnm.sfc/keen-of)."
  ([panel kn iso] (relearn-at panel kn iso {}))
  ([panel kn iso opts]
   (fn [t] (sfc/learn (known-panel panel t) kn iso (- t sfc/vintage) opts))))

(defn fiscal-at
  "(fn [t]) -> {:cg :ig :ben} government consumption, investment and
  benefits over output in `iso`'s latest data year whose flows are known
  at t (known-panel), or nil."
  [panel iso]
  (fn [t]
    (when-let [ob (some->> (get (known-panel {iso (get panel iso)} t) iso)
                           (filter (fn [[_ ob]] (and (:flows-ok ob) (:y ob)))) seq (apply max-key key) val)]
      (let [x (sfc/observed ob) Y (double (:Y x))]
        {:cg (/ (double (get-in x [:cons :gov])) Y) :ig (/ (double (get-in x [:inv :gov])) Y) :ben (/ (double (:ben x)) Y)}))))

(defn observations
  "(fn [t]) -> [{:year y :what w :y value :sd sd} ...]: what becomes known
  at t of `iso` in the SFC `panel` (bnm.sfc/read-panel) and keen.csv `kn`
  (bnm.sfc/read-keen), w one of [:end position] (a position of the
  closing balance sheet, closed as bnm.sfc/init closes it, over output),
  [:nl sector] (net lending over output), [:w] (the wage share), [:lambda] (employment). opts
  :obs-sd {:bsm relative (0.05) :floor (0.002) :nl (0.005) :w
  (0.01) :lambda (0.005)}; :imputed (false) whether imputed stocks are
  observed."
  [panel kn iso {:keys [obs-sd imputed]}]
  (let [{:keys [bsm floor nl w lambda] :or {bsm 0.05 floor 0.002 nl 0.005 w 0.01 lambda 0.005}} obs-sd
        rows (get panel iso)
        known known-year
        keen (get kn iso)]
    (fn [t]
      (vec (concat
            (for [[y ob] rows
                  :when (and (:b ob) (:y ob) (= t (known ob :known-stocks))
                             (or imputed (= :oecd-t720 (:stocks-source ob))))
                  :let [b (:bsm (sfc/close-init (:b ob))) yy (double (:y ob))]
                  [i p] (map-indexed vector positions)
                  :let [v (get-in b p)] :when (and v (not (zero? (double v))))]
              (let [x (/ (double v) yy)] {:year y :what [:end i] :y x :sd (max (* bsm (Math/abs x)) floor)}))
            (for [[y ob] rows
                  :when (and (:flows-ok ob) (:y ob) (= t (known ob :known-flows)))
                  :let [x (sfc/observed ob)]
                  item (concat (for [s [:hh :nfc :gov :row] :let [v (get-in ob [:f s :nl])] :when v]
                                 {:year y :what [:nl s] :y (/ (double v) (double (:y ob))) :sd nl})
                               [{:year y :what [:w] :y (/ (:W x) (:Y x)) :sd w}])]
              item)
            (when-let [r (get keen t)]
              (let [l (get r "keen/lambda") sy (get r "keen/state-year")]
                (when (and l sy (< (long sy) t))
                  [{:year (long sy) :what [:lambda] :y l :sd lambda}]))))))))

;; -- the analysis ------------------------------------------------------------------------

(defn- column-moments [rows]
  (let [n (double (count rows)) cols (apply map vector rows)]
    [(mapv #(/ (reduce + 0.0 %) n) cols)
     (mapv (fn [c] (let [mu (/ (reduce + 0.0 c) n)] (/ (reduce + 0.0 (map #(let [d (- % mu)] (* d d)) c)) (max 1.0 (dec n))))) cols)]))

(defn inflation
  "The factor the members' spread is inflated by before an update, from
  the innovations (Wang & Bishop 2003; Anderson 2009): the spread in
  observation space that, with the noise, matches the squared
  innovations, sqrt((sum d^2/r - k) / sum var(h)/r), at least 1 and at
  most `cap`; h is called (h member index). A model drifting from the data has a spread far smaller
  than its misfit, and an update weighted by that spread barely moves
  it."
  [ens h y r cap]
  (let [hs (vec (map-indexed (fn [i x] (h x i)) ens))
        [mu var] (column-moments hs)
        k (count y)
        num (- (reduce + 0.0 (map (fn [yy m rr] (/ (let [d (- yy m)] (* d d)) rr)) y mu r)) k)
        den (reduce + 0.0 (map / var r))]
    (if (and (pos? den) (pos? num)) (min (double cap) (max 1.0 (Math/sqrt (/ num den)))) 1.0)))

(defn- inflate
  "The members' deviations from their mean scaled by `lam`."
  [ens lam]
  (if (== lam 1.0)
    ens
    (let [[mu _] (column-moments ens)]
      (mapv (fn [x] (mapv (fn [xi mi] (+ mi (* lam (- xi mi)))) x mu)) ens))))

(defn analysis
  "The SFC core's analysis for run-ensemble (bnm.coupled): `ids` the
  three modules' ({:sfc-finance id :sfc-accounts id}), `obs` (fn [t])
  as observations gives. Each step it records the members' year (its
  opening and closing balance sheet, flow of funds, wage share,
  employment, output) and, with observations known at t whose data years
  it has recorded, updates the members (see the namespace). A member's
  memory: :years {year record} over the last `keep-years` (6), :log the
  analyses (years, observations, the inflation, mean |innovation| in sd
  units before and after). The members' spread over the window is
  inflated first (inflation, at most :max-inflation 3), then updated and
  projected. :relearn (fn [t]) -> parameters (bnm.sfc/learn on what is
  known at t) or nil: with it the members step on from each analysis with
  the parameters relearned, the same in every member (their spread is
  the state's from then on: a member's own trend growth, which no
  observation corrects, kept its draw for decades and drove the
  innovations up threefold), the recalibration of 6c (the state alone
  cannot move what the parameters hold, the learned positions' targets
  among them). :fiscal (fn [t]) -> {:cg :ig :ben} or nil (fiscal-at):
  government consumption, investment and benefits set to those shares of
  each member's output, levels the accounts otherwise carry from the
  start grown at trend, which drift from the data for decades as output
  leaves its trend (bnm-2zk). `ids` are all three modules' ids."
  [{:keys [ids obs keep-years max-inflation relearn fiscal] :or {keep-years 6 max-inflation 3.0}}]
  (let [fin (:sfc-finance ids) acc (:sfc-accounts ids)]
    {:record (fn [mem rn t]
               (if-not ((:stepped rn) fin)
                 mem
                 (let [cur (:cur rn)
                       open (get-in cur [fin "sfc/bsm"]) fof (get-in cur [acc "sfc/fof"])
                       ys (into (sorted-map) (filter (fn [[y _]] (> y (- t keep-years)))) (:years mem))]
                   (assoc mem :years (assoc ys t {:open open :fof fof :end (sfc/apply-fof open fof)
                                 :w (get-in cur [acc "sfc/wage-share"]) :lambda (get-in cur [acc "sfc/employment"])
                                 :Y (get-in cur [acc "sfc/output"])})))))
     :analyse (fn [{:keys [runners memories t g]}]
                (let [mem0 (:years (first memories))
                      os (filter #(contains? mem0 (:year %)) (obs t))
                      y0 (when (seq os) (apply min (map :year os)))
                      ys (when y0 (vec (range y0 (inc (long t)))))]
                  (if-not (and ys (every? #(contains? mem0 %) ys) (every? (fn [mem] (every? #(contains? (:years mem) %) ys)) memories))
                    {:runners runners :memories memories}
                    (let [rhos (into {} (for [y ys :let [rs (distinct (for [mem memories] (double (get-in mem [:years y :fof :rho] 0.0))))]]
                                          (if (= 1 (count rs))
                                            [y (first rs)]
                                            (throw (ex-info "bnm.sfc-analysis: members revalue equity at different rates" {:year y :rho rs})))))
                          {:keys [a b-of]} (constraints ys rhos)
                          L (layout ys)
                          ;; an observation as a linear function of the window, against the
                          ;; member's own output where it is a ratio: [index coefficient-of-Y]
                          lin (fn [{:keys [year what] :as o}]
                                (let [yl ((:year L) year)]
                                  (case (first what)
                                    :end {:i (+ (:end yl) (second what)) :ratio (:y o) :Y (:Y yl)}
                                    :nl {:i (+ (:nl yl) (.indexOf ^java.util.List sfc/sectors (second what))) :ratio (:y o) :Y (:Y yl)}
                                    :w {:i (:w yl)}
                                    :lambda {:i (:lambda yl)})))
                          ls (mapv lin os)
                          ens0 (mapv #(to-vector (:years %) ys) memories)
                          ;; each member's output, fixed through the update
                          ymem (mapv (fn [x] (mapv #(when (:Y %) (nth x (:Y %))) ls)) ens0)
                          h (fn [x i] (mapv (fn [{:keys [i ratio Y]} yv] (if Y (- (nth x i) (* ratio yv)) (nth x i))) ls (ymem i)))
                          ;; ratio observations are of 0 (level less ratio times output), in levels
                          y (mapv (fn [o l] (if (:Y l) 0.0 (:y o))) os ls)
                          ybar (mapv (fn [j] (/ (reduce + 0.0 (map #(or (nth % j) 0.0) ymem)) (count ymem))) (range (count ls)))
                          r (mapv (fn [o l yb] (let [s (* (double (:sd o)) (if (:Y l) yb 1.0))] (* s s))) os ls ybar)
                          lam (inflation ens0 h y r max-inflation)
                          ens (inflate ens0 lam)
                          prior-h (vec (map-indexed (fn [i x] (h x i)) ens))
                          post (enkf/update-constrained ens h y r g
                                                        {:indexed true :a a
                                                         :b-of (fn [i] (b-of (into {} (for [yy ys] [yy (get-in (nth memories i) [:years yy :fof :buffer] 0.0)]))))})
                          ;; output is read for ratios, not updated: each member keeps its own
                          yi (mapv (comp :Y (:year L)) ys)
                          post (mapv (fn [x x0] (reduce (fn [x i] (assoc x i (nth x0 i))) x yi)) post ens)
                          mems (mapv #(update %1 :years from-vector ys %2) memories post)
                          [wlo whi] sfc/w-bounds [llo lhi] sfc/lambda-bounds
                          p-now (when relearn (relearn t))
                          f-now (when fiscal (fiscal t))
                          rs (mapv (fn [rn mem]
                                     (let [now (get-in mem [:years (long t)])
                                           w (max wlo (min whi (:w now))) l (max llo (min lhi (:lambda now)))]
                                       (cond-> (eng/update-states rn (fn [st] (-> st
                                                                                  (update fin assoc :bsm (:open now) :open (:end now) :fof (:fof now))
                                                                                  (update acc (fn [a] (cond-> (assoc a :w w :lambda l)
                                                                                                        f-now (assoc :cg (* (:cg f-now) (:Y a)) :ig (* (:ig f-now) (:Y a))
                                                                                                                     :ben (* (:ben f-now) (:Y a)))))))))
                                         p-now (eng/update-params (fn [ps] (reduce #(assoc-in %1 [%2 :sfc] p-now) ps (vals ids)))))))
                                   runners mems)
                          mean-h (fn [hs] (mapv #(/ (reduce + 0.0 %) (count hs)) (apply map vector hs)))
                          innov (map - y (mean-h prior-h))]
                      {:runners rs
                       :memories (mapv #(assoc % :log (conj (vec (:log %)) {:t t :years ys :n (count os) :inflation lam
                                                                             :innovation (/ (reduce + 0.0 (map (fn [d rr] (Math/abs (/ d (Math/sqrt rr)))) innov r)) (count os))
                                                                             :after (/ (reduce + 0.0 (map (fn [yv hv rr] (Math/abs (/ (- yv hv) (Math/sqrt rr)))) y (mean-h (vec (map-indexed (fn [i x] (h x i)) post))) r)) (count os))}))
                                       mems)}))))}))

;; -- the members ---------------------------------------------------------------------------

(defn perturb
  "Member i's start and parameters: with one member (i nil) `st` and `p`
  as given; else the balance sheet's positions each scaled by 1 + init-sd
  N(0, 1) and closed again (bnm.sfc/close-init), the wage share and
  employment moved by w-sd and lambda-sd, trend growth n by n-sd, drawn
  from seed + i. [st p]."
  [st p i {:keys [seed init-sd w-sd lambda-sd n-sd] :or {seed 1 init-sd 0.03 w-sd 0.01 lambda-sd 0.005 n-sd 0.005}}]
  (if (nil? i)
    [st p]
    (let [g (m/rng (+ (long seed) (* 7919 (long i))))
          b (reduce (fn [b [s k side]] (update-in b [s k side] #(* (double (or % 0.0)) (+ 1.0 (* init-sd (m/normal! g)))))) (:bsm st) positions)
          [wlo whi] sfc/w-bounds [llo lhi] sfc/lambda-bounds]
      [(assoc st :bsm (:bsm (sfc/close-init b))
              :w (max wlo (min whi (+ (double (:w st)) (* w-sd (m/normal! g)))))
              :lambda (max llo (min lhi (+ (double (:lambda st)) (* lambda-sd (m/normal! g))))))
       (update p :n #(+ (double %) (* n-sd (m/normal! g))))])))

(defn members-of
  "(fn [i]) -> member i's three modules for `iso` (bnm.modules.sfc with
  country ids, opts :span), started from `st` with parameters `p`
  perturbed by perturb (opts) when :members is over 1."
  [iso st p {:keys [members span] :or {members 1} :as opts}]
  (fn [i]
    (let [[st p] (perturb st p (when (> members 1) i) opts)]
      (msfc/modules iso st p (cond-> {:country-ids true} span (assoc :span span))))))

(defn ids-of "The three modules' ids for `iso` with country ids." [iso]
  (into {} (for [k [:sfc-finance :sfc-firms :sfc-accounts]] [k (keyword (str (name k) "-" iso))])))

;; -- free runs for scoring -----------------------------------------------------------------

(defn free-run
  "A free run of `iso`'s SFC core for scoring at horizons (bnm-cvs): its
  members start from data year :start (its first year with measured
  stocks and flows by default; bnm.sfc/init, parameters learned there),
  are assimilated each year through engine year :origin with the
  parameters relearned on what is known then (relearn-at), then run
  :years more with nothing known after the origin. :members (32) and
  :seed; with one member nothing is assimilated (the phase A free run
  from :start). Returns {:t [...]
  :analysed [...] :vars {variable {:median [...] :sd [...]}}} for the
  accounts module's numeric emits (sfc/output, sfc/net-lending-*, ...),
  and :result, run-ensemble's."
  [panel kn iso {:keys [start origin years members seed] :or {members 32 seed 1}}]
  (let [start (or start (first (sort (for [[y ob] (get panel iso) :when (and (:flows-ok ob) (:b ob))] y))))
        p (sfc/learn panel kn iso start {})
        st (sfc/init panel kn iso start p)
        ids (ids-of iso)
        an (analysis {:ids ids :obs (observations panel kn iso {})
                      :relearn (relearn-at panel kn iso) :fiscal (fiscal-at panel iso)})
        r (cp/run-ensemble (members-of iso st p {:members members :seed seed})
                        {:members members :from (inc start) :to (+ origin years) :assimilate-through origin
                         :analyses (if (> members 1) [an] []) :keep true :seed (inc seed)})
        acc (:sfc-accounts ids)
        vs (sort (distinct (mapcat #(keys (get-in % [:emits acc])) (:members r))))
        col (fn [v i] (keep #(let [x (get-in % [:emits acc v i])] (when (number? x) (double x))) (:members r)))
        med (fn [xs] (let [v (vec (sort xs)) n (count v)] (when (pos? n) (if (odd? n) (v (quot n 2)) (/ (+ (v (dec (quot n 2))) (v (quot n 2))) 2.0)))))
        sd (fn [xs] (let [n (count xs)] (when (> n 1) (let [mu (/ (reduce + xs) n)] (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (dec n)))))))]
    {:t (:t r) :analysed (:analysed r) :result r
     :vars (into {} (for [v vs :when (some number? (get-in (first (:members r)) [:emits acc v]))]
                      [v {:median (mapv #(med (col v %)) (range (count (:t r))))
                          :sd (mapv #(sd (col v %)) (range (count (:t r))))}]))}))
