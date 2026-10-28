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
  of years `ys` (layout's), revaluation at `rho`."
  [ys rho]
  (let [{:keys [open year dim]} (layout ys)
        pi (into {} (map-indexed (fn [i p] [p i]) positions))
        chain (for [[j y] (map-indexed vector ys)
                    :let [{:keys [tx end]} (year y)
                          prev (if (zero? j) open (:end (year (nth ys (dec j)))))]
                    [i [_ k _]] (map-indexed vector positions)]
                (row dim [[(+ end i) 1.0] [(+ prev i) (- (if (= k :equity) (+ 1.0 rho) 1.0))] [(+ tx i) -1.0]]))
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

(defn observations
  "(fn [t]) -> [{:year y :what w :y value :sd sd} ...]: what becomes known
  at t of `iso` in the SFC `panel` (bnm.sfc/read-panel) and keen.csv `kn`
  (bnm.sfc/read-keen), w one of [:end position] (the closing balance sheet,
  closed as bnm.sfc/init closes it), [:nl sector] (net lending over
  output), [:w] (the wage share), [:lambda] (employment). opts
  :obs-sd {:bsm relative (0.05) :floor of output (0.002) :nl (0.005) :w
  (0.01) :lambda (0.005)}; :imputed (false) whether imputed stocks are
  observed."
  [panel kn iso {:keys [obs-sd imputed]}]
  (let [{:keys [bsm floor nl w lambda] :or {bsm 0.05 floor 0.002 nl 0.005 w 0.01 lambda 0.005}} obs-sd
        rows (get panel iso)
        known (fn [ob k] (or (get ob k) (+ (:year ob) sfc/vintage)))
        keen (get kn iso)]
    (fn [t]
      (vec (concat
            (for [[y ob] rows
                  :when (and (:b ob) (:y ob) (= t (known ob :known-stocks))
                             (or imputed (= :oecd-t720 (:stocks-source ob))))
                  :let [b (:bsm (sfc/close-init (:b ob))) yy (double (:y ob))]
                  [i p] (map-indexed vector positions)
                  :let [v (get-in b p)] :when (and v (not (zero? (double v))))]
              {:year y :what [:end i] :y (double v) :sd (max (* bsm (Math/abs (double v))) (* floor yy))})
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

(defn analysis
  "The SFC core's analysis for run-ensemble (bnm.coupled): `ids` the
  three modules' ({:sfc-finance id :sfc-accounts id}), `obs` (fn [t])
  as observations gives. Each step it records the members' year (its
  opening and closing balance sheet, flow of funds, wage share,
  employment, output) and, with observations known at t whose data years
  it has recorded, updates the members (see the namespace). A member's
  memory: :years {year record} over the last `keep-years` (6), :log the
  analyses (years, observations, mean |innovation| in sd units before
  and after)."
  [{:keys [ids obs keep-years] :or {keep-years 6}}]
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
                    (let [rhos (distinct (for [mem memories y ys] (double (get-in mem [:years y :fof :rho] 0.0))))
                          _ (when (> (count rhos) 1) (throw (ex-info "bnm.sfc-analysis: members revalue equity at different rates" {:rho rhos})))
                          {:keys [a b-of]} (constraints ys (first rhos))
                          idx (fn [{:keys [year what]}]
                                (let [L (layout ys) yl ((:year L) year)]
                                  (case (first what)
                                    :end (+ (:end yl) (second what))
                                    :nl [(+ (:nl yl) (.indexOf ^java.util.List sfc/sectors (second what))) (:Y yl)]
                                    :w (:w yl)
                                    :lambda (:lambda yl))))
                          ix (mapv idx os)
                          h (fn [x] (mapv (fn [i] (if (vector? i) (/ (nth x (first i)) (nth x (second i))) (nth x i))) ix))
                          ens (mapv #(to-vector (:years %) ys) memories)
                          y (mapv :y os)
                          r (mapv #(let [s (double (:sd %))] (* s s)) os)
                          prior-h (mapv h ens)
                          post (enkf/update-constrained ens h y r g
                                                        {:a a :b-of (fn [i] (b-of (into {} (for [yy ys] [yy (get-in (nth memories i) [:years yy :fof :buffer] 0.0)]))))})
                          mems (mapv #(update %1 :years from-vector ys %2) memories post)
                          [wlo whi] sfc/w-bounds [llo lhi] sfc/lambda-bounds
                          rs (mapv (fn [rn mem]
                                     (let [now (get-in mem [:years (long t)])
                                           w (max wlo (min whi (:w now))) l (max llo (min lhi (:lambda now)))]
                                       (eng/update-states rn (fn [st] (-> st
                                                                          (update fin assoc :bsm (:open now) :open (:end now) :fof (:fof now))
                                                                          (update acc assoc :w w :lambda l))))))
                                   runners mems)
                          mean-h (fn [hs] (mapv #(/ (reduce + 0.0 %) (count hs)) (apply map vector hs)))
                          innov (map - y (mean-h prior-h))]
                      {:runners rs
                       :memories (mapv #(assoc % :log (conj (vec (:log %)) {:t t :years ys :n (count os)
                                                                             :innovation (/ (reduce + 0.0 (map (fn [d o] (Math/abs (/ d (double (:sd o))))) innov os)) (count os))
                                                                             :after (/ (reduce + 0.0 (map (fn [yv hv o] (Math/abs (/ (- yv hv) (double (:sd o))))) y (mean-h (mapv h post)) os)) (count os))}))
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
