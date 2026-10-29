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
(def ^:private per-year (+ np ns' np 6))

(def fiscal
  "The fiscal levels in the analysed state: government consumption,
  investment and benefits, in the accounts module's state."
  [:cg :ig :ben])

(defn- layout
  "Indices of the window over years `ys`: the opening, then per year its
  transactions, net lending, closing balance sheet, w, lambda, Y and the
  fiscal levels cg, ig, ben."
  [ys]
  {:open 0
   :year (into {} (for [[j y] (map-indexed vector ys)]
                    (let [b (+ np (* j per-year))]
                      [y {:tx b :nl (+ b np) :end (+ b np ns') :w (+ b np ns' np) :lambda (+ b np ns' np 1) :Y (+ b np ns' np 2)
                          :cg (+ b np ns' np 3) :ig (+ b np ns' np 4) :ben (+ b np ns' np 5)}])))
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
    (doseq [y ys :let [r (mem y) {:keys [tx nl end w lambda Y] :as at} (year y)]]
      (put tx (bsm->v (:tx (:fof r))))
      (put nl (map #(double (get-in r [:fof :nl %] 0.0)) sfc/sectors))
      (put end (bsm->v (:end r)))
      (aset x w (double (:w r))) (aset x lambda (double (:lambda r))) (aset x Y (double (:Y r)))
      (doseq [k fiscal] (aset x (at k) (double (get r k 0.0)))))
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
               (let [{:keys [tx nl end w lambda] :as at} (year y)
                     r (mem y)
                     rho (double (get-in r [:fof :rho] 0.0))
                     endb (v->bsm (sub end np))
                     fof (assoc (:fof r) :tx (v->bsm (sub tx np))
                                :nl (zipmap sfc/sectors (sub nl ns'))
                                :reval (revaluation open rho))]
                 [(assoc mem y (merge (assoc r :open open :end endb :fof fof :w (nth x w) :lambda (nth x lambda))
                                      (into {} (for [k fiscal] [k (nth x (at k))]))))
                  endb]))
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

(defn observations
  "(fn [t]) -> [{:year y :what w :y value :sd sd} ...]: what becomes known
  at t of `iso` in the SFC `panel` (bnm.sfc/read-panel) and keen.csv `kn`
  (bnm.sfc/read-keen), w one of [:end position] (a position of the
  closing balance sheet, closed as bnm.sfc/init closes it, over output),
  [:nl sector] (net lending over output), [:w] (the wage share), [:lambda] (employment),
  [:cg] [:ig] [:ben] (government consumption, investment and benefits
  over output, Table 14 with the year's other flows), [:Y] (output,
  GDP in national currency, sd relative). opts
  :obs-sd {:bsm relative (0.05) :floor (0.002) :nl (0.005) :w
  (0.01) :lambda (0.005) :fiscal (0.003) :output relative (0.02)};
  :imputed (false) whether imputed stocks are observed."
  [panel kn iso {:keys [obs-sd imputed]}]
  (let [{:keys [bsm floor nl w lambda] fsd :fiscal ysd :output :or {bsm 0.05 floor 0.002 nl 0.005 w 0.01 lambda 0.005 fsd 0.003 ysd 0.02}} obs-sd
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
                               [{:year y :what [:w] :y (/ (:W x) (:Y x)) :sd w}
                                {:year y :what [:Y] :y (double (:Y x)) :sd (* ysd (double (:Y x)))}]
                               (for [[k v] {:cg (get-in x [:cons :gov]) :ig (get-in x [:inv :gov]) :ben (:ben x)}]
                                 {:year y :what [k] :y (/ (double v) (double (:Y x))) :sd fsd}))]
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

(defn- scale-bsm [b c] (when b (update-vals b (fn [m] (update-vals m (fn [ps] (update-vals ps #(* (double (or % 0.0)) c))))))))

(defn- scale-fof
  "A flow of funds with every money amount times c (rho, a rate, kept)."
  [fof c]
  (cond-> fof
    (:tx fof) (update :tx scale-bsm c)
    (:nl fof) (update :nl update-vals #(* (double %) c))
    (:reval fof) (update :reval update-vals #(* (double %) c))
    (:buffer fof) (update :buffer * c)
    (:Y fof) (update :Y * c)))

(defn- scale-record
  "A member's analysed year with its nominal scale times c: balance
  sheets, flow of funds, output and fiscal levels; the identities are
  homogeneous, so they still hold."
  [r c]
  (if (== c 1.0)
    r
    (cond-> (-> r (update :open scale-bsm c) (update :end scale-bsm c) (update :fof scale-fof c) (update :Y * c))
      true (merge (into {} (for [k fiscal :when (get r k)] [k (* (double (get r k)) c)]))))))

(defn- healthy?
  "Whether a member's memory is in the model's domain: every recorded
  year's output positive and its balance sheet finite."
  [mem]
  (every? (fn [[_ r]] (and (number? (:Y r)) (pos? (double (:Y r)))
                           (every? #(let [x (double %)] (and (== x x) (< (Math/abs x) 1e300))) (bsm->v (:end r)))))
          (:years mem)))

(defn- resample
  "[runners memories n]: each member out of the model's domain (healthy?)
  replaced by the next healthy member's runner and memory, its own
  model-error stream (the accounts state's :noise) kept, so it diverges
  again; n the number replaced. Unchanged with fewer than two healthy."
  [runners memories acc]
  (let [ok (filterv #(healthy? (nth memories %)) (range (count memories)))
        bad (remove (set ok) (range (count memories)))]
    (if (or (empty? bad) (< (count ok) 2))
      [runners memories 0]
      (reduce (fn [[rs ms n] i]
                (let [j (or (first (filter #(> % i) ok)) (first ok))
                      noise (get-in (eng/states (nth rs i)) [acc :noise])]
                  [(assoc rs i (cond-> (nth rs j) noise (eng/update-states #(assoc-in % [acc :noise] noise))))
                   (assoc ms i (assoc (nth ms j) :log (:log (nth ms i))))
                   (inc n)]))
              [runners memories 0] bad))))

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
  among them). `ids` are all three modules' ids."
  [{:keys [ids obs keep-years max-inflation relearn] :or {keep-years 6 max-inflation 3.0}}]
  (let [fin (:sfc-finance ids) acc (:sfc-accounts ids)]
    {:record (fn [mem rn t]
               (if-not ((:stepped rn) fin)
                 mem
                 (let [cur (:cur rn)
                       open (get-in cur [fin "sfc/bsm"]) fof (get-in cur [acc "sfc/fof"])
                       ys (into (sorted-map) (filter (fn [[y _]] (> y (- t keep-years)))) (:years mem))]
                   (assoc mem :years (assoc ys t (merge {:open open :fof fof :end (sfc/apply-fof open fof)
                                                         :w (get-in cur [acc "sfc/wage-share"]) :lambda (get-in cur [acc "sfc/employment"])
                                                         :Y (get-in cur [acc "sfc/output"])}
                                                        (select-keys (get (eng/states rn) acc) fiscal)))))))
     :analyse (fn [{:keys [runners memories t g]}]
                (let [[runners memories replaced] (resample runners memories acc)
                      mem0 (:years (first memories))
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
                          ;; an observation as a linear function of the window: a ratio
                          ;; observation is the level over the member's own output, held
                          ;; fixed through the update (each member's own coefficient), so the
                          ;; members' spread in output, which the update cannot move, is not
                          ;; taken for spread in what is observed
                          lin (fn [{:keys [year what]}]
                                (let [yl ((:year L) year)]
                                  (case (first what)
                                    :end {:i (+ (:end yl) (second what)) :Y (:Y yl)}
                                    :nl {:i (+ (:nl yl) (.indexOf ^java.util.List sfc/sectors (second what))) :Y (:Y yl)}
                                    :w {:i (:w yl)}
                                    :lambda {:i (:lambda yl)}
                                    (:cg :ig :ben) {:i ((first what) yl) :Y (:Y yl)}
                                    :Y {:i (:Y yl)})))
                          ls (mapv lin os)
                          ens0 (mapv #(to-vector (:years %) ys) memories)
                          ;; each member's output, fixed through the update
                          ymem (mapv (fn [x] (mapv #(when (:Y %) (nth x (:Y %))) ls)) ens0)
                          h (fn [x i] (mapv (fn [{:keys [i Y]} yv] (if Y (/ (nth x i) yv) (nth x i))) ls (ymem i)))
                          y (mapv :y os)
                          r (mapv (fn [o] (let [s (double (:sd o))] (* s s))) os)
                          lam (inflation ens0 h y r max-inflation)
                          ens (inflate ens0 lam)
                          prior-h (vec (map-indexed (fn [i x] (h x i)) ens))
                          ;; localised by block: the fiscal levels (policy, in no identity) are
                          ;; updated by the fiscal observations alone, and those update nothing
                          ;; else; with tens of members, the window's few hundred other
                          ;; observations otherwise move them through spurious covariances
                          ;; output likewise, by the output observations alone
                          fiscal? (fn [o] (contains? (set fiscal) (first (:what o))))
                          output? (fn [o] (= :Y (first (:what o))))
                          sel (fn [pred] (filterv #(pred (nth os %)) (range (count os))))
                          [mi fi oi] [(sel #(not (or (fiscal? %) (output? %)))) (sel fiscal?) (sel output?)]
                          sub (fn [v js] (mapv #(nth v %) js))
                          hs (fn [js] (fn [x i] (sub (h x i) js)))
                          fidx (vec (for [yy ys k fiscal] (get-in L [:year yy k])))
                          post (if (seq mi)
                                 (enkf/update-constrained ens (hs mi) (sub y mi) (sub r mi) g
                                                          {:indexed true :a a
                                                           :b-of (fn [i] (b-of (into {} (for [yy ys] [yy (get-in (nth memories i) [:years yy :fof :buffer] 0.0)]))))})
                                 ens)
                          post (if (seq fi)
                                 (let [fpost (enkf/update ens (hs fi) (sub y fi) (sub r fi) g {:indexed true})]
                                   (mapv (fn [x xf] (reduce (fn [x j] (assoc x j (nth xf j))) x fidx)) post fpost))
                                 (mapv (fn [x x0] (reduce (fn [x j] (assoc x j (nth x0 j))) x fidx)) post ens))
                          ;; output: the ratios above are of each member's output as it was; its
                          ;; own update (the output observations) moves the member's nominal
                          ;; scale, every money level by one factor (scaled), so every ratio and
                          ;; identity is kept
                          yi (mapv (comp :Y (:year L)) ys)
                          ypost (when (seq oi) (enkf/update ens (hs oi) (sub y oi) (sub r oi) g {:indexed true}))
                          ynow (:Y ((:year L) (long t)))
                          cs (mapv (fn [i x0] (let [y0 (nth x0 ynow) y1 (if ypost (nth (nth ypost i) ynow) y0)]
                                                (if (and (pos? y0) (pos? y1)) (max 0.2 (min 5.0 (/ y1 y0))) 1.0)))
                                   (range (count ens0)) ens0)
                          post (mapv (fn [x x0] (reduce (fn [x i] (assoc x i (nth x0 i))) x yi)) post ens)
                          mems (mapv (fn [m x c] (update (update m :years from-vector ys x) :years update-vals #(scale-record % c)))
                                     memories post cs)
                          [wlo whi] sfc/w-bounds [llo lhi] sfc/lambda-bounds
                          p-now (when relearn (relearn t))
                          firms (:sfc-firms ids)
                          rs (mapv (fn [rn mem c]
                                     (let [now (get-in mem [:years (long t)])
                                           w (max wlo (min whi (:w now))) l (max llo (min lhi (:lambda now)))]
                                       (cond-> (eng/update-states rn (fn [st] (cond-> (-> st
                                                                                          (update fin assoc :bsm (:open now) :open (:end now) :fof (:fof now) :Y (:Y now))
                                                                                          (update acc assoc :w w :lambda l :Y (:Y now))
                                                                                          (update acc merge (into {} (for [k fiscal :when (some? (get now k))] [k (max 0.0 (double (get now k)))]))))
                                                                                   (and firms (get st firms)) (update firms update :Y #(some-> % (* c))))))
                                         (not= 1.0 c) (eng/update-emits acc (fn [e] (cond-> e
                                                                                       (get e "sfc/output") (update "sfc/output" * c)
                                                                                       (get e "sfc/fof") (update "sfc/fof" scale-fof c))))
                                         p-now (eng/update-params (fn [ps] (reduce #(assoc-in %1 [%2 :sfc] p-now) ps (vals ids)))))))
                                   runners mems cs)
                          mean-h (fn [hs] (mapv #(/ (reduce + 0.0 %) (count hs)) (apply map vector hs)))
                          innov (vec (map - y (mean-h prior-h)))
                          resid (vec (map - y (mean-h (vec (map-indexed (fn [i x] (h x i)) post)))))
                          units (fn [ds js] (when (seq js) (/ (reduce + 0.0 (for [j js] (Math/abs (/ (nth ds j) (Math/sqrt (nth r j)))))) (count js))))
                          all (range (count os))]
                      {:runners rs
                       :memories (mapv #(assoc % :log (conj (vec (:log %)) {:t t :years ys :n (count os) :inflation lam
                                                                             :innovation (units innov all) :after (units resid all)
                                                                             :fiscal-innovation (units innov fi) :fiscal-after (units resid fi)
                                                                             :output-innovation (units innov oi) :scale (nth cs 0)
                                                                             :replaced replaced}))
                                       mems)}))))}))

;; -- the members ---------------------------------------------------------------------------

(defn seed-of
  "A generator seed from `seed` and the keys `ks` (whole numbers), mixed
  by SplitMix64's finaliser. augur.math/rng fills its state linearly
  from the seed, so seeds a fixed step apart give correlated streams
  (seed + 7919 i, as members were drawn: neighbouring members' draws
  correlated by up to 0.9, a lattice, not an ensemble)."
  [seed & ks]
  (let [mix (fn [x] (let [z (unchecked-add (long x) (unchecked-long -7046029254386353131))
                          z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 30)) (unchecked-long -4658895280553007687))
                          z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 27)) (unchecked-long -7723592293110705685))]
                      (bit-xor z (unsigned-bit-shift-right z 31))))]
    (reduce (fn [h k] (mix (bit-xor h (long k)))) (mix (long seed)) ks)))

(defn perturb
  "Member i's start and parameters: with one member (i nil) `st` and `p`
  as given; else the balance sheet's positions each scaled by 1 + init-sd
  N(0, 1) and closed again (bnm.sfc/close-init), the wage share and
  employment moved by w-sd and lambda-sd, trend growth n by n-sd, the
  fiscal levels each scaled by exp(sd N(0, 1)) at the parameters'
  :fiscal-sd, drawn from (seed-of seed i); and the member's own
  generator for its fiscal model error (:noise, bnm.modules.sfc/
  fiscal-error), (seed-of seed i 1). [st p]."
  [st p i {:keys [seed init-sd w-sd lambda-sd n-sd] :or {seed 1 init-sd 0.03 w-sd 0.01 lambda-sd 0.005 n-sd 0.005}}]
  (if (nil? i)
    [st p]
    (let [g (m/rng (seed-of seed i))
          b (reduce (fn [b [s k side]] (update-in b [s k side] #(* (double (or % 0.0)) (+ 1.0 (* init-sd (m/normal! g)))))) (:bsm st) positions)
          [wlo whi] sfc/w-bounds [llo lhi] sfc/lambda-bounds
          st (assoc st :bsm (:bsm (sfc/close-init b))
                    :w (max wlo (min whi (+ (double (:w st)) (* w-sd (m/normal! g)))))
                    :lambda (max llo (min lhi (+ (double (:lambda st)) (* lambda-sd (m/normal! g))))))
          p (update p :n #(+ (double %) (* n-sd (m/normal! g))))
          st (reduce (fn [st k] (let [z (m/normal! g)]
                                  (cond-> st (get st k) (update k #(* (double %) (Math/exp (* (double (get-in p [:fiscal-sd k] 0.0)) z)))))))
                     st fiscal)]
      [(assoc st :noise (m/rng-state (seed-of seed i 1))) p])))

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

(defn- summarise
  "{variable {:median [...] :sd [...]}} over the members' engine results
  `ms` (one t each), for the accounts module `acc`'s numeric emits."
  [ms nt acc]
  (let [vs (sort (distinct (mapcat #(keys (get-in % [:emits acc])) ms)))
        col (fn [v i] (keep #(let [x (get-in % [:emits acc v i])] (when (number? x) (double x))) ms))
        med (fn [xs] (let [v (vec (sort xs)) n (count v)] (when (pos? n) (if (odd? n) (v (quot n 2)) (/ (+ (v (dec (quot n 2))) (v (quot n 2))) 2.0)))))
        sd (fn [xs] (let [n (count xs)] (when (> n 1) (let [mu (/ (reduce + xs) n)] (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (dec n)))))))]
    (into {} (for [v vs :when (some number? (get-in (first ms) [:emits acc v]))]
               [v {:median (mapv #(med (col v %)) (range nt))
                   :sd (mapv #(sd (col v %)) (range nt))}]))))

(defn- first-measured [panel iso]
  (first (sort (for [[y ob] (get panel iso) :when (and (:flows-ok ob) (:b ob))] y))))

(defn- ensemble-of
  "The members and analysis of `iso`'s assimilated run from data year
  `start` (see free-run)."
  [panel kn iso start {:keys [members seed] :as opts}]
  (let [p (sfc/learn panel kn iso start {})
        st (sfc/init panel kn iso start p)
        ids (ids-of iso)]
    {:ids ids
     :ms-of (members-of iso st p (assoc opts :members members :seed seed))
     :an (analysis {:ids ids :obs (observations panel kn iso {})
                    :relearn (relearn-at panel kn iso)})}))

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
  [panel kn iso {:keys [start origin years members seed] :or {members 32 seed 1} :as opts}]
  (let [start (or start (first-measured panel iso))
        {:keys [ids ms-of an]} (ensemble-of panel kn iso start (assoc opts :members members :seed seed))
        r (cp/run-ensemble ms-of
                           {:members members :from (inc start) :to (+ origin years) :assimilate-through origin
                            :analyses (if (> members 1) [an] []) :keep true :seed (seed-of seed 2)})]
    {:t (:t r) :analysed (:analysed r) :result r
     :vars (summarise (:members r) (count (:t r)) (:sfc-accounts ids))}))

(defn given-path
  "{year {:cg :ig :ben :kappa}}: government consumption, investment and
  benefits and NFC investment over output in `iso`'s data years `ys`
  (those with flows), the observed path a conditional forecast is given
  (bnm.modules.sfc's :given)."
  [panel iso ys]
  (into {} (for [y ys :let [ob (get-in panel [iso y])] :when (and ob (:flows-ok ob) (:y ob))
                 :let [x (sfc/observed ob) Y (double (:Y x))]]
             [y {:cg (/ (double (get-in x [:cons :gov])) Y) :ig (/ (double (get-in x [:inv :gov])) Y)
                 :ben (/ (double (:ben x)) Y) :kappa (/ (double (get-in x [:inv :nfc] 0.0)) Y)}])))

(defn- with-given [ids given]
  (fn [rn] (eng/update-params rn (fn [ps] (reduce (fn [ps id] (assoc-in ps [id :sfc :given] given)) ps (vals ids))))))

(defn free-runs
  "free-run from each engine year in `origins` at once: one assimilated
  pass, a free run of :years branched off it after each origin's analysis
  (bnm.coupled's :forks), the same as free-run from that origin with the
  same opts. {origin {:t [...] :vars ... :members [engine results]}} and
  :log, the first member's analyses. :given (fn [origin]) -> a path
  (given-path): each origin also gets a conditional free run, its
  parameters given that path, under :conditional."
  [panel kn iso {:keys [start origins years members seed given] :or {members 32 seed 1} :as opts}]
  (let [start (or start (first-measured panel iso))
        os (sort origins)
        {:keys [ids ms-of an]} (ensemble-of panel kn iso start (assoc opts :members members :seed seed))
        r (cp/run-ensemble ms-of
                           {:members members :from (inc start) :to (+ (last os) years) :assimilate-through (last os)
                            :analyses (if (> members 1) [an] []) :keep true :seed (seed-of seed 2)
                            :forks (cond-> {:at (set os) :years years}
                                     given (assoc :variants {:free (fn [_ rs] rs)
                                                             :conditional (fn [t rs] (mapv (with-given ids (given t)) rs))}))})
        acc (:sfc-accounts ids)
        out (fn [ms] {:t (:t (first ms)) :members ms :vars (summarise ms (count (:t (first ms))) acc)})]
    (assoc (into {} (for [o os :let [f (get-in r [:forks o])]]
                      [o (if given (assoc (out (:free f)) :conditional (out (:conditional f))) (out f))]))
           :log (:log (first (first (:memories r)))))))

(defn restart
  "The restart (phase A's free run from the latest data): one member,
  parameters learned at data year `origin` on the panel as known at
  origin + vintage, started from origin's data (bnm.sfc/init), run to
  origin + years; `given` a path (given-path) for a conditional run.
  {:t [...] :vars {variable {:median [...]}}}."
  ([panel kn iso origin years] (restart panel kn iso origin years nil))
  ([panel kn iso origin years given]
  (let [kp (known-panel panel (+ origin sfc/vintage))
        p (cond-> (sfc/learn kp kn iso origin {}) given (assoc :given given))
        st (sfc/init kp kn iso origin p)
        ids (ids-of iso)
        r (eng/run (msfc/modules iso st p {:country-ids true}) {:from (inc origin) :to (+ origin years)})]
    {:t (:t r) :vars (summarise [r] (count (:t r)) (:sfc-accounts ids))})))
