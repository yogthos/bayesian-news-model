(ns bnm.cycles
  "Commodity and capital cycles from delays (bead bnm-62z.10; docs/STOCK-
  FLOW.md): a generic commodity market as a bnm.stockflow model, learned per
  commodity from world prices and quantities by bnm.assimilate/calibrate.

  Stocks: inventory I (material), production capacity K (material), the
  capacity under construction, a third-order material delay (DELAY3) of
  construction time tau-c whose stages are the supply line SL, the price
  relative to equilibrium r, and two perceptions: demand Dp (SMOOTH over
  tau-d) and the expected relative price Pe (SMOOTH of r over tau-p).

    coverage c = I / Dp, against desired coverage c*
    indicated price f(c / c*), f a decreasing table (f(1) = 1), toward
      which r adjusts over tau-pa; P = P_eq r, P_eq = p-ref e^(pi (t - t0))
      the equilibrium price (Meadows 1970)
    demand D = e^(g (t - t0) + s) r^-eps, s a demand shock, one per :seg
      years of the calibration window, held after it
    utilization u = u-eq (P/P_eq)^u-slope (at most 1), production Q = u K
    dI/dt = Q - D;  dK/dt = completions - K/life
    desired capacity K* = Dp/u-eq Pe^sigma (expected profitability)
    starts = max(0, L + (K* - K)/tau-s + beta (SL* - SL)/tau-s)
      L = K (1/life + g) the replacement and growth anchor, SL* = L tau-c

  The shocks are what excites the cycle in history (the market alone
  damps or settles into its own cycle); the response to them carries the
  delays and beta. The investment rule is Sterman's (1989) anchoring and adjustment, alpha_S
  = 1/tau-s and alpha_SL = beta/tau-s: with beta < 1 the supply line is
  under-weighted, capacity is over-ordered while it is being built, and
  the market overshoots (Sterman 1985's long wave is the same rule with
  capital ordering capital). Its period scales with the delays.

  commodity-rows: a commodity's world state, year by year, as known by the
  end of each year y from data years before y published by y (as bnm.keen),
  calibrated on an expanding window every few years and carried forward
  between (the model's state in a year between is the calibrated members'
  path, not corrected by the data since; the price gap is the data's);
  each value the median over up to :forecast-members members; rows iso
  WLD, variable <commodity>_<name>:

    log_price        log of last year's mean price
    price_gap        log of last year's mean price over the model's P_eq
    model_price_gap  the model's own log P/P_eq then (the cycle's phase)
    coverage         c / c*, the model's, then
    pipeline         SL / SL*, the capacity pipeline against its anchor
    capacity_gap     log K / K*
    dir_1y .. dir_3y the model's expected change of the log price over 1-3
                     years from last year's
    beta, beta_sd    the supply-line weight (median, sd of its log)
    construction_time, construction_time_sd   tau-c likewise
    period           the period of the calibrated market's own cycle (years)
    calibrated       the year the parameters were learned at

  exposure-rows: a producer's exposure, its export share in the commodity
  group (latest known) times the group's price gap."
  (:require [bnm.assimilate :as as]
            [bnm.stockflow :as sf]))

;; ---------------------------------------------------------------------------
;; The model

(def defaults
  "Parameters, years the time unit; output and demand at t0 are 1."
  (merge
  {:tau-c 3.0 :beta 0.3 :tau-s 2.0 :tau-p 1.0 :tau-d 0.5 :life 15.0
   :eta 1.5 :eps 0.2 :sigma 1.0 :u-eq 0.85 :u-slope 0.5 :cover-d 0.25
   :tau-pa 0.5 :g 0.02 :pi 0.0 :p-ref 1.0 :k0 1.0 :i0 1.0 :q-scale 1.0 :d-scale 1.0 :t0 0.0
   :seg 2.0 :last-seg 1000.0}
  ;; demand shocks: the log of demand's departure in each segment of :seg
  ;; years from t0, held after segment :last-seg
  (into {} (for [i (range 32)] [(keyword (str "s" i)) 0.0]))))

(def ^:private shock-keys (mapv #(keyword (str "s" %)) (range 32)))

(def ^:private grid-lo 0.2)
(def ^:private grid-hi 3.0)
(def ^:private grid (vec (for [i (range 15)] (+ grid-lo (* i (/ (- grid-hi grid-lo) 14.0))))))

(defn price-effect
  "f(x), x coverage over desired coverage: a table on an even grid over
  [0.2, 3] (flat beyond), its points x^-eta, so f(1) = 1 and eta its
  elasticity."
  ^double [x eta]
  (if (== 1.0 (double x))
    1.0
    (sf/table x (mapv #(Math/pow % (- (double eta))) grid) [grid-lo grid-hi])))

(defn- p [v k] (double (v k)))

(defn model
  "The commodity market (see the namespace) with `params` over defaults."
  ([] (model {}))
  ([params]
   (let [base (sf/model
               {:params (merge defaults params)
                :stocks {:inventory {:init (fn [v] (* (p v :i0) (p v :cover-d))) :nonneg true :unit "q"}
                         :capacity {:init (fn [v] (/ (p v :k0) (p v :u-eq))) :nonneg true :unit "q/year"}
                         ;; the price, relative to equilibrium, adjusts toward
                         ;; the one coverage indicates over tau-pa
                         :rel-price {:init (fn [v] (p v :indicated-price)) :info true}}
                :flows {:production {:to :inventory :rate (fn [v] (* (p v :utilization) (p v :capacity))) :unit "q/year"}
                        :shipments {:from :inventory :rate (fn [v] (p v :demand)) :unit "q/year"}
                        :retirement {:from :capacity :rate (fn [v] (/ (p v :capacity) (p v :life)))}
                        :price-adjust {:to :rel-price :rate (fn [v] (/ (- (p v :indicated-price) (p v :rel-price)) (p v :tau-pa)))}}
                :aux {:elapsed (fn [v] (- (p v :t) (p v :t0)))
                      :d-ref (fn [v] (Math/exp (* (p v :g) (p v :elapsed))))
                      :p-eq (fn [v] (* (p v :p-ref) (Math/exp (* (p v :pi) (p v :elapsed)))))
                      :coverage (fn [v] (/ (p v :inventory) (max 1e-9 (p v :perceived-demand))))
                      :coverage-ratio (fn [v] (/ (p v :coverage) (p v :cover-d)))
                      :indicated-price (fn [v] (price-effect (p v :coverage-ratio) (p v :eta)))
                      :shock (fn [v] (let [i (long (Math/floor (/ (p v :elapsed) (p v :seg))))]
                                       (p v (shock-keys (max 0 (min (dec (count shock-keys)) (long (p v :last-seg)) i))))))
                      :price (fn [v] (* (p v :p-eq) (p v :rel-price)))
                      :price-gap (fn [v] (Math/log (max 1e-6 (p v :rel-price))))
                      :log-price (fn [v] (Math/log (p v :price)))
                      :demand (fn [v] (* (p v :d-ref) (Math/exp (p v :shock)) (Math/pow (max 1e-6 (p v :rel-price)) (- (p v :eps)))))
                      :utilization (fn [v] (min 1.0 (* (p v :u-eq) (Math/pow (max 1e-6 (p v :rel-price)) (p v :u-slope)))))
                      :log-output (fn [v] (Math/log (max 1e-9 (/ (p v :production) (p v :q-scale)))))
                      :log-demand (fn [v] (Math/log (max 1e-9 (/ (p v :demand) (p v :d-scale)))))
                      :desired-capacity (fn [v] (* (/ (p v :perceived-demand) (p v :u-eq))
                                                   (Math/pow (max 1e-3 (p v :expected-price)) (p v :sigma))))
                      :anchor (fn [v] (* (p v :capacity) (+ (/ 1.0 (p v :life)) (p v :g))))
                      :supply-line (fn [v] (+ (p v :completion-1) (p v :completion-2) (p v :completion-3)))
                      :desired-supply-line (fn [v] (* (p v :anchor) (p v :tau-c)))
                      :starts (fn [v] (max 0.0 (+ (p v :anchor)
                                                  (/ (+ (- (p v :desired-capacity) (p v :capacity))
                                                        (* (p v :beta) (- (p v :desired-supply-line) (p v :supply-line))))
                                                     (p v :tau-s)))))
                      :pipeline-ratio (fn [v] (/ (p v :supply-line) (max 1e-9 (p v :desired-supply-line))))
                      :capacity-gap (fn [v] (Math/log (/ (max 1e-9 (p v :capacity)) (max 1e-9 (p v :desired-capacity)))))}})
         ;; the pipeline starts at the equilibrium's: each stage the anchor
         ;; at equilibrium capacity over a third of the construction time
         stage-init (fn [v] (* (/ 1.0 (p v :u-eq)) (+ (/ 1.0 (p v :life)) (p v :g)) (/ (p v :tau-c) 3.0)))
         pipe (-> (sf/delay-n :completion :starts :tau-c 3 {:to :capacity})
                  (update :stocks #(update-vals % (fn [s] (assoc s :init stage-init :nonneg true)))))]
     (sf/combine base
                 (sf/smooth :perceived-demand :demand :tau-d 1.0)
                 (sf/smooth :expected-price :rel-price :tau-p 1.0)
                 pipe))))

(defn simulate
  "A run of the model with `params` from :from (its t0) to :to by :dt."
  [params {:keys [from] :as opts}]
  (sf/run (model (assoc params :t0 (double from))) opts))

(defn period
  "The mean spacing of successive peaks (local maxima above 1e-4) of `xs`
  over times `ts`, or nil with fewer than two."
  [ts xs]
  (let [xs (vec xs) ts (vec ts)
        peaks (vec (for [i (range 1 (dec (count xs)))
                         :let [x (xs i)]
                         :when (and (> x 1e-4) (> x (xs (dec i))) (>= x (xs (inc i))))]
                     (ts i)))]
    (when (>= (count peaks) 2)
      (/ (- (peek peaks) (first peaks)) (dec (count peaks))))))

(defn own-period
  "The period of the market's own cycle under `params`: from capacity
  20% short with no growth or drift, over 80 years; nil when it does not
  cycle."
  [params]
  (let [r (simulate (merge params {:k0 0.8 :i0 1.0 :g 0.0 :pi 0.0 :s0 0.0 :last-seg 0.0}) {:from 0.0 :to 80.0 :dt 0.1})]
    (period (:t r) (get-in r [:aux :price-gap]))))

;; ---------------------------------------------------------------------------
;; Calibration

(def priors
  "Priors on what calibrate-window learns; a :log one is normal in the log,
  :mean its median. p-ref's centre is the window's geometric mean price;
  q-scale, d-scale and cover-d are learned only with output, demand or
  coverage observed."
  {:tau-c {:mean 3.0 :sd 0.5 :log true}
   :beta {:mean 0.4 :sd 0.7 :log true}
   :tau-s {:mean 2.0 :sd 0.5 :log true}
   :tau-p {:mean 1.0 :sd 0.5 :log true}
   :eta {:mean 1.5 :sd 0.5 :log true}
   :p-ref {:mean 1.0 :sd 0.5 :log true}
   :pi {:mean 0.0 :sd 0.03}
   :g {:mean 0.02 :sd 0.02}
   :k0 {:mean 1.0 :sd 0.2 :log true}
   :i0 {:mean 1.0 :sd 0.3 :log true}
   :q-scale {:mean 1.0 :sd 0.3 :log true}
   :d-scale {:mean 1.0 :sd 0.3 :log true}
   :cover-d {:mean 0.25 :sd 0.5 :log true}})

(def noise-defaults
  "Observation noise: annual mean log price, log output and demand, and
  coverage (years of use)."
  {:log-price 0.1 :log-output 0.05 :log-demand 0.05 :coverage 0.03})

(defn- finite? [x] (and (number? x) (== (double x) (double x)) (< (Math/abs (double x)) ##Inf)))

(defn- mean [xs] (when (seq xs) (/ (reduce + 0.0 xs) (count xs))))

(defn calibrate-window
  "The model learned from `obs` ({:price {year mean price} :output {year
  production} :demand {year use} :coverage {year stocks to use}}, data
  years; output and demand in any unit) by bnm.assimilate/calibrate,
  from the first price year (t0), annual means read at mid-year, with a
  demand shock per :seg (2) years (normal, sd :shock-sd 0.3), the last
  held after the data. Options :members (100; with 40 the ensemble collapses on some data) :iterations (4) :seed :dt
  (0.25) :noise. {:params (medians and means) :posterior :members [params]
  :from}."
  [{:keys [price output demand coverage]} {:keys [members iterations seed dt noise seg shock-sd]
                                           :or {members 100 iterations 4 seed 1 dt 0.25 seg 2.0 shock-sd 0.3}}]
  (let [y0 (first (keys (into (sorted-map) price)))
        _ (when-not y0 (throw (ex-info "bnm.cycles/calibrate-window: no prices" {})))
        noise (merge noise-defaults noise)
        geo (Math/exp (mean (map #(Math/log %) (vals price))))
        obs (reduce (fn [acc [k series f]]
                      (reduce (fn [acc [y x]] (if (and (>= y y0) (finite? x))
                                                (assoc-in acc [(+ y 0.5) k] (f x)) acc))
                              acc series))
                    {}
                    [[:log-price price #(Math/log %)]
                     [:log-output (when (get output y0) output) #(Math/log (/ % (get output y0)))]
                     [:log-demand (when (get demand y0) demand) #(Math/log (/ % (get demand y0)))]
                     [:coverage coverage double]])
        last-seg (min (dec (count shock-keys)) (long (Math/floor (/ (- (apply max (keys obs)) y0) seg))))
        learn (cond-> [:tau-c :beta :tau-s :tau-p :eta :p-ref :pi :g :k0 :i0]
                (get output y0) (conj :q-scale)
                (get demand y0) (conj :d-scale)
                (seq coverage) (conj :cover-d))
        pri (-> (select-keys priors learn)
                (assoc-in [:p-ref :mean] geo)
                (into (for [i (range (inc last-seg))] [(shock-keys i) {:mean 0.0 :sd shock-sd}])))
        fixed {:t0 (double y0) :seg (double seg) :last-seg (double last-seg)}
        post (as/calibrate (model fixed)
                           {:from (double y0) :dt dt :members members :seed seed :iterations iterations
                            :priors pri :observations (into {} (for [[t o] obs] [(double t) o]))
                            :noise (select-keys noise [:log-price :log-output :log-demand :coverage])})
        ps (vec (sort (keys pri)))
        natural (fn [k x] (if (:log (pri k)) (Math/exp x) x))
        base (merge defaults fixed)]
    {:params (merge base (into {} (for [[k s] (:params post)] [k (if (:log (pri k)) (:median s) (:mean s))])))
     :posterior (:params post)
     :members (vec (for [theta (:members post)] (merge base (zipmap ps (map natural ps theta)))))
     :from y0}))

;; ---------------------------------------------------------------------------
;; A commodity year by year

(defn known
  "{var {data-year value}}: what `panel` ({var {data-year [year-known
  value]}}) shows at the end of year y: data years before y, published by y."
  [panel y]
  (into {} (for [[v s] panel]
             [v (into (sorted-map) (for [[yr [avail x]] s :when (and (< yr y) (<= avail y) (some? x))] [yr x]))])))

(def run-defaults
  {:members 100 :iterations 4 :every 5 :min-years 8 :from 1960 :to 2026 :seed 1 :dt 0.25
   :forecast-members 12 :noise {}})

(defn- try-calibrate [k y {:keys [min-years seed] :as opts}]
  (when (>= (count (:price k)) min-years)
    (try (assoc (calibrate-window k (assoc opts :seed (+ seed y))) :origin y)
         (catch Exception _ nil))))

(defn- spread-members
  "Up to n of the members, evenly spaced."
  [members n]
  (let [c (count members)]
    (if (<= c n) members (vec (for [i (range n)] (members (quot (* i c) n)))))))

(defn- median [xs]
  (when (seq xs)
    (let [v (vec (sort xs)) n (count v) h (quot n 2)]
      (if (odd? n) (v h) (/ (+ (v (dec h)) (v h)) 2.0)))))

(def ^:private traced [:price-gap :coverage-ratio :pipeline-ratio :capacity-gap :log-price :p-eq])

(defn- trajectories
  "The calibrated members' runs (up to :forecast-members of them) from t0
  to `to`: [{aux [values]}]."
  [cal to {:keys [dt forecast-members]}]
  (vec (pmap (fn [prm] (select-keys (:aux (sf/run (model prm) {:from (double (:from cal)) :to (double to) :dt dt})) traced))
             (spread-members (:members cal) forecast-members))))

(defn- state-at
  "{variable value} of the calibrated model at year y, the medians over its
  members: its state at the middle of the last data year, and its expected
  price changes after."
  [cal y {:keys [dt]}]
  (let [last-mid (- y 0.5)
        idx (fn [t] (long (Math/round (/ (- t (double (:from cal))) dt))))
        per (fn [r]
              (let [a (fn [k t] (nth (r k) (idx t) nil))
                    lp0 (a :log-price last-mid)
                    d (fn [h] (some-> (a :log-price (+ last-mid h)) (- lp0)))]
                (when lp0
                  {"model_price_gap" (a :price-gap last-mid) "coverage" (a :coverage-ratio last-mid)
                   "pipeline" (a :pipeline-ratio last-mid) "capacity_gap" (a :capacity-gap last-mid)
                   "dir_1y" (d 1.0) "dir_2y" (d 2.0) "dir_3y" (d 3.0)
                   :log-p-eq (Math/log (a :p-eq last-mid))})))
        states (keep per (:runs cal))]
    (when (seq states)
      (into {} (for [k (keys (first states))] [k (median (filter finite? (keep #(get % k) states)))])))))

(defn commodity-rows
  "[{:iso \"WLD\" :year :variable :value}] for one commodity's panel
  ({:price :output :demand :coverage}, each {data-year [year-known
  value]}) from :from to :to, each year seeing only what was known by its
  end (see the namespace). Options: run-defaults."
  [commodity panel opts]
  (let [{:keys [from to every] :as opts} (merge run-defaults opts)]
    (loop [y from cal nil out (transient [])]
      (if (> y to)
        (persistent! out)
        (let [k (known panel y)
              cal (if (or (nil? cal) (>= (- y (:origin cal)) every))
                    (if-let [c (try-calibrate k y opts)]
                      (assoc c :period (own-period (:params c))
                             :runs (trajectories c (+ y every 3) opts))
                      cal)
                    cal)
              last-price (get-in k [:price (dec y)])
              st (when cal (state-at cal y opts))
              vs (when (and st last-price)
                   (let [post (:posterior cal)]
                     (-> (dissoc st :log-p-eq)
                         (assoc "price_gap" (- (Math/log last-price) (:log-p-eq st))
                                "log_price" (Math/log last-price)
                                "beta" (get-in post [:beta :median]) "beta_sd" (get-in post [:beta :log-sd])
                                "construction_time" (get-in post [:tau-c :median])
                                "construction_time_sd" (get-in post [:tau-c :log-sd])
                                "period" (:period cal) "calibrated" (double (:origin cal))))))
              rows (for [[v x] (sort-by key vs) :when (finite? x)]
                     {:iso "WLD" :year y :variable (str commodity "_" v) :value (double x)})]
          (recur (inc y) cal (reduce conj! out rows)))))))

(defn exposure-rows
  "[{:iso :year :variable :value}]: for each country in `shares` ({iso
  {data-year [year-known percent]}}) and year y of `phase` ({year price
  gap}), its latest share known by y (a data year before y, at most
  :max-lag (5) before) as a fraction, times the phase."
  [phase shares variable {:keys [max-lag] :or {max-lag 5}}]
  (vec (for [[iso s] (sort-by key shares)
             [y ph] (sort-by key phase)
             :let [share (some (fn [[yr [avail x]]] (when (and (< yr y) (<= avail y) (>= yr (- y max-lag 1)) (some? x)) x))
                               (rseq (into (sorted-map) s)))]
             :when (and share (finite? ph))]
         {:iso iso :year y :variable variable :value (* (/ (double share) 100.0) (double ph))})))

(defn csv-lines
  "The rows as CSV lines, the header first: iso,year,variable,value."
  [rows]
  (cons "iso,year,variable,value"
        (for [{:keys [iso year variable value]} rows] (str iso "," year "," variable "," value))))
