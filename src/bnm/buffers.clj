(ns bnm.buffers
  "The general buffer model, its schema and invariants (docs/PLAN.md 1.1,
  1.2; docs/BUFFERS.md; bead bnm-56c.3). A crisis is hidden by a buffer:
  while a stock covers a flow imbalance nothing shows, and the break comes
  when cover runs short and the flow cannot be restored in time. Nothing
  here is specific to one kind of stock: reserves, fuel, grain, debt and
  capital pipelines are rows of data to the same functions.

  A stock record (`record`):

    {:id        \"sfc-finance/USA/bsm\"     ; unique
     :name      \"bsm\"  :unit \"USD m\"  :time-unit \"year\"  ; of t, rates, lag, terms
     :holder    {:unit \"central bank\" :place \"LKA\"}  ; who and where
     :kind      :material                 ; or :information (a perception), :state (a share or rate)
     :inflows   [{:var v :unit u :counterpart stock-id-or-nil :rigid share :rigid-sd sd}]
     :outflows  [...]                     ; the same; :rigid the share that cannot be cut
     :measure   {:frequency dt :lag n}    ; how often (time units), how late:
                                          ; a value for t is known at t + n
     :usable    {:share u :sd s}          ; usable against headline
     :life      years  or :decay rate     ; optional: asset life or decay
     :idle      {:share x :sd s :binding \"what binds instead\"}   ; optional
     :restore   {:time t :sd s}           ; time to restore the inflow
     :carry     {:cost r :yield y :cost-sd :yield-sd}  ; per time unit
     :claims    {:rate r :maturity m}     ; terms of claims on the flow
     :holders   {name share}  :leverage {:value x :sd s}
     :market    {:ref-price p :ref-cover c0 :c c}
     :source    :engine | :mapping | :supply   :flags #{...}}

  A record's series are {:stock [obs] :inflow [obs] :outflow [obs]
  :flows {var [obs]} :usable [obs] :claims [obs] :income [obs] :price
  [obs] :idle [obs] :leverage [obs] :restore [obs]}, an obs {:t time :v
  value :known time :sd sd}; flows are rates per time unit. An obs is
  known at :known, else at t plus the record's lag (`known`): every
  invariant as of a date reads only what was known by it, and of several
  vintages of one t the last published by then.

  Every invariant gives rows of one flat table, what the hazard and the
  scanner read: {:stock :holder :place :date :invariant :value :sd :known
  :flags} (plus :window, :exhaustion, :band where they apply), the flags
  including the record's own. Times are in years and rates per year
  (`per-year`), whatever the record's time unit, so stocks compare; a
  window is in years; :exhaustion is a date on the series' own axis. A
  missing input gives a nil value and a flag, never a zero."
  (:require [bnm.engine :as eng]
            [bnm.invariants :as iv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; -- the schema -----------------------------------------------------------------

(defn- num? [x] (and (number? x) (not (Double/isNaN (double x))) (not (Double/isInfinite (double x)))))

(def per-year
  "A record's :time-unit, the unit of its t, rates, lag and terms, as so
  many per year. Rows give times in years and rates per year, so that
  monthly, quarterly and yearly stocks compare."
  {"year" 1.0 "quarter" 4.0 "month" 12.0 "week" (/ 365.25 7.0) "day" 365.25})

(defn- tu "Time units per year." [rec] (per-year (:time-unit rec)))

(defn record
  "A stock record with its defaults (see the namespace), or an error when
  it lacks an id, a name, a unit or a holder with a place, has a usable
  or idle share outside [0, 1], or a :time-unit not in `per-year`."
  [m]
  (let [bad (fn [msg] (throw (ex-info (str "bnm.buffers/record: " msg " in " (pr-str (:id m))) {:record m})))
        share-ok? (fn [s] (or (nil? s) (and (num? s) (<= 0.0 (double s) 1.0))))]
    (when-not (:id m) (bad "no :id"))
    (when-not (:name m) (bad "no :name"))
    (when-not (:unit m) (bad "no :unit"))
    (when-not (and (map? (:holder m)) (contains? (:holder m) :place)) (bad "no :holder with a :place"))
    (when-not (share-ok? (get-in m [:usable :share])) (bad "a usable share outside [0, 1]"))
    (when-not (share-ok? (get-in m [:idle :share])) (bad "an idle share outside [0, 1]"))
    (let [r (merge {:time-unit "year" :kind :material :inflows [] :outflows [] :measure {} :flags #{}}
                   (update m :flags #(set (or % #{}))))]
      (when-not (per-year (:time-unit r)) (bad (str "a time unit " (pr-str (:time-unit r)) " not in bnm.buffers/per-year")))
      r)))

(defn lag-flags
  "#{:lag-assumed-zero} when the record declares no publication lag."
  [rec]
  (if (num? (get-in rec [:measure :lag])) #{} #{:lag-assumed-zero}))

(defn known-at
  "When obs `o` of `rec` is first known: its :known, else t + the lag (0
  when none is declared; lag-flags says so)."
  [rec o]
  (or (:known o) (+ (:t o) (or (get-in rec [:measure :lag]) 0))))

(defn known
  "The observations `xs` of `rec` known by `as-of`, with a value, by t;
  of several vintages of one t, the last published by `as-of`."
  [rec xs as-of]
  (->> xs
       (filter #(and (num? (:v %)) (num? (:t %)) (<= (known-at rec %) as-of)))
       (group-by #(double (:t %)))
       vals
       (map #(apply max-key (partial known-at rec) %))
       (sort-by :t)
       vec))

(defn latest "The last of `xs` known by `as-of`, or nil." [rec xs as-of] (peek (known rec xs as-of)))

;; -- small statistics ---------------------------------------------------------------

(defn- mean [xs] (/ (reduce + 0.0 xs) (count xs)))

(defn- sq [x] (* (double x) (double x)))

(defn- ratio-sd
  "The sd of a/b from the parts' sds (nil counted 0), to first order, in
  absolute terms so that it holds at a = 0."
  [a sa b sb]
  (let [a (double a) b (double b)]
    (Math/sqrt (+ (sq (/ (double (or sa 0.0)) b)) (sq (/ (* a (double (or sb 0.0))) (* b b)))))))

(defn- prod-sd
  "The sd of a*b from the parts' sds (nil counted 0), to first order."
  [a sa b sb]
  (Math/sqrt (+ (sq (* (double (or sa 0.0)) (double b))) (sq (* (double a) (double (or sb 0.0)))))))

(defn- slope
  "[slope sd] of the obs' values on t by least squares: the sd from the
  residuals (3 or more points) and from the obs' own :sd."
  [os]
  (let [n (count os) ts (map :t os) vs (map :v os)
        tb (mean ts) vb (mean vs)
        sxx (reduce + 0.0 (map #(sq (- % tb)) ts))]
    (when (and (>= n 2) (> sxx 1e-12))
      (let [b (/ (reduce + 0.0 (map #(* (- %1 tb) (- %2 vb)) ts vs)) sxx)
            a (- vb (* b tb))
            res (if (>= n 3) (/ (/ (reduce + 0.0 (map #(sq (- %2 (+ a (* b %1)))) ts vs)) (- n 2)) sxx) 0.0)
            meas (/ (reduce + 0.0 (map #(* (sq (- (:t %) tb)) (sq (or (:sd %) 0.0))) os)) (sq sxx))]
        [b (Math/sqrt (+ res meas))]))))

(defn- mean-sd
  "[mean sd-of-mean] of the obs' values: spread over sqrt n plus their own sds."
  [os]
  (let [n (count os) vs (map :v os) m (mean vs)
        spread (if (>= n 2) (/ (reduce + 0.0 (map #(sq (- % m)) vs)) (dec n)) 0.0)
        meas (/ (reduce + 0.0 (map #(sq (or (:sd %) 0.0)) os)) (sq n))]
    [m (Math/sqrt (+ (/ spread n) meas))]))

(defn- in-window [os t w] (filterv #(> (:t %) (- t w)) os))

(defn- max-known [rec os] (when (seq os) (reduce max (map #(known-at rec %) os))))

;; -- rows -------------------------------------------------------------------------

(defn- row
  "One row; the record's own flags (a prior, a revised vintage, ...) join
  the row's."
  [rec as-of inv m]
  (-> (merge {:stock (:id rec) :holder (get-in rec [:holder :unit]) :place (get-in rec [:holder :place])
              :date as-of :invariant inv :value nil :sd nil :known nil :flags #{}}
             m)
      (update :flags #(into (set %) (:flags rec)))))

;; -- cover ---------------------------------------------------------------------------

(defn- usable-of
  "[share sd known flags]: the latest known usable share (series), else the
  record's, else 1 (the headline) flagged."
  [rec series as-of]
  (if-let [o (latest rec (:usable series) as-of)]
    [(:v o) (or (:sd o) 0.0) (known-at rec o) #{}]
    (if-let [u (get-in rec [:usable :share])]
      [u (or (get-in rec [:usable :sd]) 0.0) nil #{}]
      [1.0 0.0 nil #{:usable-missing}])))

(defn- unit-flags
  "#{:flow-unit-mismatch} when a declared flow's unit does not begin with
  the stock's (a flow is the stock's unit per time unit)."
  [rec]
  (let [u (str (:unit rec))]
    (if (some #(and (:unit %) (not (str/starts-with? (str (:unit %)) u))) (concat (:inflows rec) (:outflows rec)))
      #{:flow-unit-mismatch}
      #{})))

(defn- drawdown
  "{:d net drawdown per time unit :sd :from :known} over the `w` time units
  ending at the latest flow (both flows measured within the window) or
  else stock observation; nil when neither has enough."
  [rec series as-of w]
  (let [out (known rec (:outflow series) as-of)
        in (known rec (:inflow series) as-of)
        flows (when (and (seq out) (seq in))
                (let [t (max (:t (peek out)) (:t (peek in)))
                      ow (in-window out t w) iw (in-window in t w)]
                  (when (and (seq ow) (seq iw))
                    (let [[mo so] (mean-sd ow) [mi si] (mean-sd iw)]
                      {:d (- mo mi) :sd (Math/sqrt (+ (sq so) (sq si))) :from :flows
                       :known (max (max-known rec ow) (max-known rec iw))}))))]
    (or flows
        (let [st (known rec (:stock series) as-of)]
          (when (seq st)
            (let [t (:t (peek st))
                  pts (filterv #(>= (:t %) (- t w)) st)]
              (when-let [[b s] (slope pts)]
                {:d (- b) :sd s :from :stock :known (max-known rec pts)})))))))

(defn cover
  "Rows of :cover (usable stock over net drawdown, in years, with its
  :exhaustion date, the last stock observation plus cover on the series'
  own time axis) and :drain-rate (net drawdown over usable stock, per
  year; negative when the stock grows, continuous through zero) for each
  window in opts :windows (years; [1 3 5]). A stock not draining has cover
  at opts :cap (100 years), flagged :not-draining, never negative; one
  above it is :capped. An empty stock has cover 0 and no drain rate."
  [rec series as-of {:keys [windows cap] :or {windows [1 3 5] cap 100.0}}]
  (let [k (tu rec)
        st (latest rec (:stock series) as-of)
        [u usd uk uflags] (usable-of rec series as-of)
        base-flags (-> (lag-flags rec) (into uflags) (into (unit-flags rec)))]
    (vec
     (mapcat
      (fn [w]
        (let [dd (when st (drawdown rec series as-of (* w k)))
              flags (cond-> base-flags (nil? st) (conj :missing-stock) (and st (nil? dd)) (conj :missing-drawdown))]
          (if (or (nil? st) (nil? dd))
            [(row rec as-of :cover {:window w :flags flags}) (row rec as-of :drain-rate {:window w :flags flags})]
            (let [s (* (double (:v st)) (double u))
                  ssd (prod-sd (:v st) (:sd st) u usd)
                  {:keys [d sd from]} dd
                  kn (reduce max (keep identity [(known-at rec st) uk (:known dd)]))
                  c (cond (<= s 0.0) {:value 0.0 :sd 0.0 :exhaustion (when (> d 0.0) (:t st)) :flags (conj flags :empty)}
                          (<= d 0.0) {:value cap :sd 0.0 :flags (conj flags :not-draining)}
                          (> (/ s d k) cap) {:value cap :sd 0.0 :flags (conj flags :capped)}
                          :else (let [c (/ s d)]
                                  {:value (/ c k) :sd (/ (ratio-sd s ssd d sd) k) :exhaustion (+ (:t st) c) :flags flags}))]
              [(row rec as-of :cover (merge {:window w :known kn :drawdown-from from} c))
               (row rec as-of :drain-rate (merge {:window w :known kn :drawdown-from from
                                                  :flags (cond-> flags (<= s 0.0) (conj :empty))}
                                                 (when (> s 0.0) {:value (* k (/ d s)) :sd (* k (ratio-sd d sd s ssd))})))]))))
      windows))))

;; -- gross cover --------------------------------------------------------------------------

(defn gross-cover
  "Row :gross-cover, usable stock over the gross outflow, in years: the
  mean :outflow over the first window of opts :windows ([1] years) up to
  its latest known value. What stocks-to-use and reserves in months of
  imports measure: how long the stock lasts if the inflow stops, where
  cover over net drawdown says how long it lasts at the current
  imbalance. nil, flagged, without a stock or an outflow; :no-outflow
  when the outflow is not positive."
  [rec series as-of {:keys [windows] :or {windows [1]}}]
  (let [k (tu rec)
        st (latest rec (:stock series) as-of)
        out (known rec (:outflow series) as-of)
        ow (when (seq out) (in-window out (:t (peek out)) (* (first windows) k)))
        [u usd uk uflags] (usable-of rec series as-of)
        flags (-> (lag-flags rec) (into uflags) (into (unit-flags rec))
                  (cond-> (nil? st) (conj :missing-stock) (empty? ow) (conj :missing-outflow)))]
    (if (or (nil? st) (empty? ow))
      (row rec as-of :gross-cover {:window (first windows) :flags flags})
      (let [[mo so] (mean-sd ow)
            s (* (double (:v st)) (double u))
            ssd (prod-sd (:v st) (:sd st) u usd)
            kn (reduce max (keep identity [(known-at rec st) uk (max-known rec ow)]))]
        (if (<= mo 0.0)
          (row rec as-of :gross-cover {:window (first windows) :known kn :flags (conj flags :no-outflow)})
          (row rec as-of :gross-cover {:window (first windows) :known kn :flags flags
                                       :value (/ (max 0.0 s) mo k) :sd (/ (ratio-sd s ssd mo so) k)}))))))

;; -- restoration ------------------------------------------------------------------------

(defn restoration
  "Rows of :restoration, the time to restore the inflow over cover, per
  window, as that time (in the record's time units) times the drain rate:
  the same as time over cover, but continuous past the cover's cap and
  negative when the stock grows. Above 1 the buffer runs out first
  (:runs-out-first). An empty stock has run out: nil, flagged :empty. The
  time is the latest known :restore obs, else the record's :restore."
  [rec series as-of opts]
  (let [o (latest rec (:restore series) as-of)
        [t tsd] (if o [(:v o) (:sd o)] [(get-in rec [:restore :time]) (get-in rec [:restore :sd])])
        rows (cover rec series as-of opts)
        drain (into {} (for [r rows :when (= :drain-rate (:invariant r))] [(:window r) r]))]
    (vec (for [c rows :when (= :cover (:invariant c))
               :let [dr (drain (:window c))
                     base (-> (dissoc c :exhaustion) (assoc :invariant :restoration :value nil :sd nil))]]
           (cond (not (num? t)) (update base :flags conj :missing-restore)
                 (nil? (:value dr)) base
                 :else (let [ty (/ (double t) (tu rec))
                             v (* ty (:value dr))]
                         (assoc base :value v
                                :sd (prod-sd ty (some-> tsd (/ (tu rec))) (:value dr) (:sd dr))
                                :known (if o (max (:known c) (known-at rec o)) (:known c))
                                :flags (cond-> (:flags c) (> v 1.0) (conj :runs-out-first)))))))))

;; -- rigidity ------------------------------------------------------------------------------

(defn rigidity
  "Row :rigidity, the share of the outflow that cannot be cut: each
  outflow's :rigid share weighted by its mean over the first window of
  opts :windows ([1] years) up to its latest known value; the record's
  {:rigidity {:share :sd}} when it gives no outflows. An outflow of
  unknown rigidity bounds the share between all cut and all rigid: the
  midpoint, with a uniform spread over the bounds, flagged
  :partial-rigidity. nil, flagged, when no outflow's rigidity or any
  outflow's level is known. With no outflows declared, the latest known
  :rigidity observation (a share measured directly, say the debt service
  ratio), else the record's {:rigidity {:share :sd}}."
  [rec series as-of {:keys [windows] :or {windows [1]}}]
  (let [w (* (first windows) (tu rec)) outs (:outflows rec)]
    (if (empty? outs)
      (if-let [o (latest rec (:rigidity series) as-of)]
        (row rec as-of :rigidity {:value (:v o) :sd (or (:sd o) 0.0) :known (known-at rec o) :flags (lag-flags rec)})
       (if-let [r (get-in rec [:rigidity :share])]
        (row rec as-of :rigidity {:value r :sd (or (get-in rec [:rigidity :sd]) 0.0) :flags (lag-flags rec)})
        (row rec as-of :rigidity {:flags #{:missing-outflows}})))
      (let [parts (for [o outs :let [os (known rec (get-in series [:flows (:var o)]) as-of)]]
                    {:rigid (:rigid o) :rsd (or (:rigid-sd o) 0.0)
                     :level (when (seq os) (first (mean-sd (in-window os (:t (peek os)) w))))
                     :known (max-known rec os)})
            flags (cond-> (lag-flags rec)
                    (every? (comp nil? :rigid) parts) (conj :missing-rigidity)
                    (some (comp nil? :level) parts) (conj :missing-flow))]
        (if (or (contains? flags :missing-rigidity) (contains? flags :missing-flow))
          (row rec as-of :rigidity {:flags flags})
          (let [tot (reduce + 0.0 (map :level parts))
                {kn true un false} (group-by (comp some? :rigid) parts)]
            (if (<= tot 0.0)
              (row rec as-of :rigidity {:flags (conj flags :no-outflow)})
              (let [q (/ (reduce + 0.0 (map :level un)) tot)]
                (row rec as-of :rigidity
                     {:value (+ (/ (reduce + 0.0 (map #(* (:rigid %) (:level %)) kn)) tot) (/ q 2.0))
                      :sd (Math/sqrt (+ (reduce + 0.0 (map #(sq (* (/ (:level %) tot) (:rsd %))) kn)) (/ (sq q) 12.0)))
                      :known (reduce max (map :known parts))
                      :flags (cond-> flags (seq un) (conj :partial-rigidity))})))))))))

;; -- carry, claims, idle, holder, market ---------------------------------------------------------

(defn carry
  "Row :carry, what the stock earns less what it loses by sitting, per
  year: :yield against :cost plus decay (:decay, or 1 / :life), all three
  in the record's time units. nil, flagged, without a yield or a cost."
  [rec _series as-of _opts]
  (let [{:keys [cost yield cost-sd yield-sd]} (:carry rec)
        k (tu rec)
        decay (or (:decay rec) (when (num? (:life rec)) (/ 1.0 (double (:life rec)))))
        lost (when (or cost decay) (+ (or cost 0.0) (or decay 0.0)))]
    (cond (nil? yield) (row rec as-of :carry {:flags #{:missing-yield}})
          (nil? lost) (row rec as-of :carry {:flags #{:missing-cost}})
          :else (row rec as-of :carry {:value (* k (- (double yield) lost))
                                       :sd (* k (Math/sqrt (+ (sq (or yield-sd 0.0)) (sq (or cost-sd 0.0)))))}))))

(defn claim-gap
  "Row :claim-gap, claims on a flow over the flow (years of income to
  repay), with Minsky's bands on the same ratio from the record's
  {:claims {:rate r :maturity m}} (per and in its time units): :hedge
  when the income covers interest and principal (ratio <= 1/(r + 1/m)),
  :speculative when it covers the interest (<= 1/r; any ratio at r = 0),
  else :ponzi. No income: :no-income, band :ponzi, no value. Claims and
  income of different dates: :dates-differ."
  [rec series as-of _opts]
  (let [c (latest rec (:claims series) as-of) f (latest rec (:income series) as-of)
        {r :rate m :maturity} (:claims rec)
        k (tu rec)
        flags (cond-> (lag-flags rec)
                (not (and (num? r) (>= (double r) 0.0) (num? m) (pos? (double m)))) (conj :missing-terms)
                (and c f (not= (double (:t c)) (double (:t f)))) (conj :dates-differ))]
    (cond (or (nil? c) (nil? f)) (row rec as-of :claim-gap {:flags (conj flags (if c :missing-income :missing-claims))})
          (<= (double (:v f)) 0.0) (row rec as-of :claim-gap {:flags (conj flags :no-income) :band :ponzi
                                                              :known (max (known-at rec c) (known-at rec f))})
          :else (let [v (/ (double (:v c)) (double (:v f)))]
                  (row rec as-of :claim-gap
                       {:value (/ v k) :sd (/ (ratio-sd (:v c) (:sd c) (:v f) (:sd f)) k)
                        :known (max (known-at rec c) (known-at rec f)) :flags flags
                        :band (when-not (contains? flags :missing-terms)
                                (cond (<= v (/ 1.0 (+ r (/ 1.0 m)))) :hedge
                                      (or (zero? (double r)) (<= v (/ 1.0 r))) :speculative
                                      :else :ponzi))})))))

(defn idle-share
  "Row :idle-share, installed but not earning, with :binding, what binds
  instead: the latest known :idle obs, else the record's :idle."
  [rec series as-of _opts]
  (let [o (latest rec (:idle series) as-of) i (:idle rec)]
    (cond o (row rec as-of :idle-share {:value (:v o) :sd (:sd o) :known (known-at rec o) :binding (:binding i)
                                        :flags (cond-> (lag-flags rec) (nil? (:sd o)) (conj :sd-unknown))})
          (:share i) (row rec as-of :idle-share {:value (:share i) :sd (:sd i) :binding (:binding i)
                                                 :flags (if (:sd i) #{} #{:sd-unknown})})
          :else (row rec as-of :idle-share {:flags #{:missing-idle}}))))

(defn holder
  "Rows :holder-hhi (concentration of the stock over its holders, the HHI
  of :holders' shares) and :holder-leverage (the latest known :leverage
  obs, else the record's): where a loss lands."
  [rec series as-of _opts]
  (let [hs (vals (:holders rec)) tot (reduce + 0.0 (filter num? hs))
        lo (latest rec (:leverage series) as-of) lr (:leverage rec)]
    [(if (and (seq hs) (every? num? hs) (pos? tot))
       (row rec as-of :holder-hhi {:value (reduce + 0.0 (map #(sq (/ % tot)) hs)) :sd (:holders-sd rec)
                                   :flags (if (:holders-sd rec) #{} #{:sd-unknown})})
       (row rec as-of :holder-hhi {:flags #{:missing-holders}}))
     (cond lo (row rec as-of :holder-leverage {:value (:v lo) :sd (:sd lo) :known (known-at rec lo)
                                               :flags (cond-> (lag-flags rec) (nil? (:sd lo)) (conj :sd-unknown))})
           (:value lr) (row rec as-of :holder-leverage {:value (:value lr) :sd (:sd lr) :flags (if (:sd lr) #{} #{:sd-unknown})})
           :else (row rec as-of :holder-leverage {:flags #{:missing-leverage}}))]))

(defn implied-cover
  "The cover a price implies, by competitive storage's convex price
  (bnm.food/storage-pressure: p/p_ref = (1 + c)/(cover/cover_ref + c))
  inverted: cover_ref ((1 + c)/(p/p_ref) - c)."
  [p ref-price ref-cover c]
  (* (double ref-cover) (- (/ (+ 1.0 c) (/ (double p) (double ref-price))) c)))

(defn market-reading
  "Row :market-reading, the cover the latest known :price implies
  (implied-cover, the record's :market {:ref-price :ref-cover :c}), in
  years. The cover is whatever :ref-cover measures, usually stock over
  gross use (stocks-to-use, as bnm.food's curve is fitted), not the net
  drawdown of :cover. At 0, flagged :below-floor, for a price past the
  convex curve's floor; nil, :bad-price, for a price at or below 0."
  [rec series as-of _opts]
  (let [o (latest rec (:price series) as-of) {:keys [ref-price ref-cover c]} (:market rec) k (tu rec)]
    (cond (nil? o) (row rec as-of :market-reading {:flags #{:missing-price}})
          (not (and (num? ref-price) (pos? (double ref-price)) (num? ref-cover) (num? c)))
          (row rec as-of :market-reading {:flags #{:missing-market-terms} :known (known-at rec o)})
          (<= (double (:v o)) 0.0) (row rec as-of :market-reading {:flags (conj (lag-flags rec) :bad-price) :known (known-at rec o)})
          :else (let [v (implied-cover (:v o) ref-price ref-cover c)
                      p (/ (double (:v o)) ref-price)
                      d (/ (* ref-cover (+ 1.0 c)) (* p p ref-price))]
                  (row rec as-of :market-reading {:value (/ (max 0.0 v) k) :sd (/ (* d (or (:sd o) 0.0)) k) :known (known-at rec o)
                                                  :flags (cond-> (lag-flags rec) (neg? v) (conj :below-floor))})))))

;; -- the dimension-free measures (bnm.invariants, docs/INVARIANTS.md) -----------------------------

(defn- z-sd
  "The sampling spread of a z-score against n past values."
  [z n]
  (when (and z (> n 1)) (Math/sqrt (+ (/ 1.0 n) (/ (sq z) (* 2.0 (dec n)))))))

(defn displacement
  "Rows of bnm.invariants' measures on the stock's known values (taken as
  equally spaced): :displacement, its stretch against the opts
  :disp-windows ([5 10 20] observations) before it; :resonance, the
  smallest absolute stretch of the three (displaced at every scale); and
  :stock-x-flow, the middle window's stretch of the stock times that of
  its net flow (the changes of the stock), both absolute. The record's
  :displace names another series to measure (say :claims, for a stock of
  debt that is a claim, not a buffer drawn)."
  [rec series as-of {:keys [disp-windows] :or {disp-windows [5 10 20]}}]
  (let [os (known rec (get series (or (:displace rec) :stock)) as-of)
        xs (mapv :v os) i (dec (count xs))
        kn (when (seq os) (known-at rec (peek os)))
        flows (vec (cons nil (map - (rest xs) xs)))
        minn (fn [w] (max 3 (quot w 2)))
        nprev (fn [w] (count (keep #(get xs %) (range (max 0 (- i w)) (max 0 i)))))
        zs (vec (for [w disp-windows] (when (>= i 0) (iv/stretch xs i w (minn w)))))
        fl (when (>= i 1) (iv/stretch flows i (second disp-windows) (minn (second disp-windows))))
        mid (second zs) wmid (second disp-windows)
        flags (cond-> (lag-flags rec) (empty? os) (conj :missing-stock))]
    (vec (concat
          (for [[w z] (map vector disp-windows zs)]
            (row rec as-of :displacement {:window w :value z :sd (z-sd z (nprev w)) :known (when z kn)
                                          :flags (cond-> flags (nil? z) (conj :short-history))}))
          [(if (every? some? zs)
             (let [[w z] (apply min-key #(Math/abs (double (second %))) (map vector disp-windows zs))]
               (row rec as-of :resonance {:value (Math/abs (double z)) :sd (z-sd z (nprev w)) :known kn :flags flags}))
             (row rec as-of :resonance {:flags (conj flags :short-history)}))
           (if (and mid fl)
             (let [v (* (Math/abs (double mid)) (Math/abs (double fl)))
                   s1 (z-sd mid (nprev wmid)) s2 (z-sd fl (nprev wmid))]
               (row rec as-of :stock-x-flow {:window wmid :value v :known kn :flags flags
                                             :sd (Math/sqrt (+ (sq (* (Math/abs (double fl)) s1)) (sq (* (Math/abs (double mid)) s2))))}))
             (row rec as-of :stock-x-flow {:window wmid :flags (conj flags :short-history)}))]))))

(defn breadth
  "Across stocks at one date: {:invariant :breadth :value share of the
  :displacement rows past opts :threshold (1.5) :norm :max :n}, from
  bnm.invariants/aggregate (nil values skipped; nil below :min-n, 2)."
  [rows {:keys [threshold min-n] :or {threshold 1.5 min-n 2}}]
  (let [a (iv/aggregate (map :value (filter #(= :displacement (:invariant %)) rows)) {:threshold threshold :min-n min-n})]
    {:invariant :breadth :value (:breadth a) :norm (:norm a) :max (:max a) :n (:n a)}))

;; -- all of them -------------------------------------------------------------------------------------

(defn- buffer-rows [rec series as-of opts]
  (concat (cover rec series as-of opts)
          [(gross-cover rec series as-of opts)]
          (restoration rec series as-of opts)
          [(rigidity rec series as-of opts)
           (carry rec series as-of opts)
           (claim-gap rec series as-of opts)
           (idle-share rec series as-of opts)]
          (holder rec series as-of opts)
          [(market-reading rec series as-of opts)]))

(defn invariants
  "Every invariant of `rec` as of `as-of` from its `series`, as flat rows
  (see the namespace); opts as cover's, rigidity's and displacement's.
  Only a :material stock is a buffer: an :information or :state stock (a
  perception, a wage share) has the buffer rows with nil values, flagged
  :not-a-buffer, and its displacement measures."
  [rec series as-of opts]
  (let [opts (merge {:windows [1 3 5]} opts)]
    (vec (concat (if (= :material (:kind rec))
                   (buffer-rows rec series as-of opts)
                   (for [r (buffer-rows rec {} as-of opts)]
                     (-> (assoc r :value nil :sd nil :known nil)
                         (dissoc :exhaustion :band :drawdown-from)
                         (update :flags conj :not-a-buffer))))
                 (displacement rec series as-of opts)))))

;; -- records from the engine's graph ---------------------------------------------------------------

(defn- vname [v] (if (keyword? v) (name v) (str v)))

(defn- module-label [m] (or (:label m) (name (:module m))))

(defn- unit-from-flows
  "The unit every flow's unit shares before \"/year\" or \" per year\" (any
  time unit), or nil."
  [flows]
  (let [us (distinct (map #(some->> (:unit %) str (re-matches #"(.+?)\s*(?:/|\bper\b)\s*(?:year|quarter|month|week|day)s?") second)
                          flows))]
    (when (and (seq flows) (= 1 (count us))) (first us))))

(defn stock-id [m k] (str (module-label m) "/" (vname (:place m)) "/" (vname k)))

(defn- module-lag
  "The module's publication lag: its level emits' known rule if they
  share one, else all its emits' if they share one; nil otherwise."
  [m]
  (let [one (fn [es] (let [ks (distinct (keep #(get-in % [:known :data-year]) es))] (when (= 1 (count ks)) (first ks))))]
    (or (one (filter #(= :level (:phase %)) (:emits m))) (one (:emits m)))))

(defn engine-stocks
  "{:records [...] :levels [...]}: a record for every stock the modules
  (bnm.engine declarations, or graph.edn's :modules) declare in :stocks,
  and their :level emits (measures of stocks). Flows attach from opts
  :models {module stock-flow model} (bnm.stockflow: a flow's :from and
  :to, the other end its counterpart, :source :model), else from the
  stock's own :inflows and :outflows (a stock the engine holds, the regime
  switch in bnm.engine, :source :declared), else, for a module with one
  material stock, from its lagged level reads of another
  module's emit (a stock integrates the flows it reads a step late,
  :source :inferred, :net true: the read is a net flow, its direction
  unknown, listed with the inflows). A stock with neither has no flows: it needs a
  mapping (resources/buffers/stocks.edn)."
  [modules {:keys [models]}]
  (let [records
        (vec (for [m modules
                   :let [model (get models (:module m))
                         material (remove (comp :info val) (:stocks m))
                         lag (module-lag m)
                         flow-unit (fn [f fl] (or (:unit fl) (:unit (eng/emit-for m f)) (:unit (eng/emit-for m (vname f)))))
                         inferred (when (and (nil? model) (= 1 (count material)))
                                    (vec (for [r (:reads m) :when (and (not (:source r)) (= :level (:phase r)) (pos? (or (:lag r) 0)))]
                                           {:var (:var r) :unit (:unit r) :counterpart nil :from (:from r) :source :inferred :net true})))]
                   [k s] (sort-by (comp vname key) (:stocks m))
                   :let [info? (boolean (:info s))
                         declared-flow (fn [fl] (merge {:unit (or (:unit (eng/emit-for m (:var fl)))
                                                                  ;; an inflow read from another module: the read's unit
                                                                  (some #(when (= (:var fl) (or (:as %) (:var %))) (:unit %)) (:reads m)))}
                                                       (select-keys fl [:var :unit :counterpart :rigid :rigid-sd :shortfall])
                                                       {:source :declared}))
                         [ins outs] (cond
                                      model
                                      [(vec (for [[f fl] (sort-by (comp vname key) (:flows model)) :when (= k (:to fl))]
                                              {:var f :unit (flow-unit f fl) :counterpart (when (:from fl) (stock-id m (:from fl))) :source :model}))
                                       (vec (for [[f fl] (sort-by (comp vname key) (:flows model)) :when (= k (:from fl))]
                                              {:var f :unit (flow-unit f fl) :counterpart (when (:to fl) (stock-id m (:to fl))) :source :model}))]
                                      ;; a stock the engine holds declares its flows
                                      (or (seq (:inflows s)) (seq (:outflows s)))
                                      [(mapv declared-flow (:inflows s)) (mapv declared-flow (:outflows s))]
                                      :else
                                      [(if (and (not info?) (= 1 (count material))) (or inferred []) []) []])
                         declared (or (:unit s) (get-in model [:stocks k :unit]))
                         inferred-unit (when-not declared (unit-from-flows (concat ins outs)))]]
               (record {:id (stock-id m k) :name (vname k) :unit (or declared inferred-unit "unknown")
                        :holder {:unit (module-label m) :place (vname (:place m))}
                        :kind (if info? :information :material)
                        :inflows ins :outflows outs
                        :measure (cond-> {:frequency (:dt m)} lag (assoc :lag lag))
                        :source :engine :module (:module m) :table (:table m)
                        :flags (cond-> #{} (nil? lag) (conj :lag-unknown) (= :country (:place m)) (conj :per-country)
                                 inferred-unit (conj :unit-from-flows)
                                 (not (or declared inferred-unit)) (conj :unit-unknown))})))
        levels (vec (for [m modules e (:emits m) :when (= :level (:phase e))]
                      {:module (:module m) :label (module-label m) :var (:var e) :unit (:unit e) :known (:known e)}))]
    {:records records :levels levels}))

(defn report
  "{:stocks n :material n :by-kind {kind n} :by-module {label n} :no-flows
  [id ...]}: what the records hold, and the material stocks with no
  declared flows."
  [records]
  {:stocks (count records)
   :material (count (filter #(= :material (:kind %)) records))
   :by-kind (frequencies (map :kind records))
   :by-module (frequencies (map #(keyword (get-in % [:holder :unit])) records))
   :no-flows (vec (for [r records :when (and (= :material (:kind r)) (empty? (:inflows r)) (empty? (:outflows r)))] (:id r)))})

(defn read-graph
  "The module declarations of a coupled graph (data/derived/coupled/graph.edn)."
  [path]
  (:modules (edn/read-string (slurp path))))

;; -- mappings: stocks the engine does not hold, and flows it does not declare -----------------------

(defn read-mappings
  "{:records [...] :extensions [...]} from an EDN mapping file
  (resources/buffers/stocks.edn)."
  [path]
  (let [m (edn/read-string (slurp (io/file path)))]
    {:records (vec (:records m)) :extensions (vec (:extensions m))}))

(defn apply-extensions
  "The records with each extension {:match \"glob\" key value ...} merged
  into those whose id its pattern matches (* any run of characters);
  :inflows and :outflows are added to, the rest replaced."
  [records exts]
  (mapv (fn [r]
          (reduce (fn [r e]
                    (if (eng/glob-match? (:match e) (:id r))
                      (let [e (dissoc e :match)]
                        (record (merge-with (fn [a b] (if (and (vector? a) (vector? b)) (into a b) b)) r
                                            (cond-> e (:inflows e) (update :inflows vec) (:outflows e) (update :outflows vec)))))
                      r))
                  r exts))
        records))

;; -- the supply model's inventories (data/derived/supply/stocks.csv) -------------------------------

(defn supply-records
  "{:records [...] :series {id {:stock [obs]}}} from the supply model's
  stock rows (bnm.supply/outage-path's stocks: region, product, months of
  normal use, year, known, basis, source). A stock there is already cover
  against gross use; its net drawdown under a cut of share f is f times
  use, so its cover under the cut is months / f (months / 12f years). t
  is the year, so the time unit is the year. A prior is flagged."
  [rows]
  (let [rs (for [{:keys [region product months year known basis source]} rows
                 :let [m (some-> months str not-empty parse-double)]
                 :when m]
             [(record {:id (str "supply/" region "/" product) :name product :unit "months of use" :time-unit "year"
                       :holder {:unit "region" :place region}
                       :outflows [{:var "use" :unit "months of use per year" :counterpart nil}]
                       :measure {:frequency 1 :lag (- (parse-long (str known)) (parse-long (str year)))}
                       :source :supply :note source
                       :flags (cond-> #{} (= "prior" basis) (conj :prior))})
              {:t (parse-long (str year)) :v m :known (parse-long (str known))}])]
    {:records (mapv first rs)
     :series (into {} (for [[r o] rs] [(:id r) {:stock [o]}]))}))

(defn read-csv-maps
  "Rows of a plain CSV (no quoted commas) as maps keyed by the header."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r) ks (mapv keyword (str/split h #","))]
      (mapv #(zipmap ks (str/split % #"," -1)) ls))))
