(ns bnm.keen
  "The macro-financial core (docs/STOCK-FLOW.md, plan item 7): Keen's
  (1995) model of wages, employment and private debt in the form of
  Grasselli & Costa Lima (2012), as a bnm.stockflow model, learned per
  country from what was known at each year end (bnm.assimilate/calibrate).

  Stocks: the wage share w, the employment rate l, private debt to output
  d. With the profit share pi = 1 - w - r d, investment kappa(pi) (a share
  of output) and output growth g = kappa(pi)/nu - delta,

    dw/dt = w (Phi(l) - alpha)
    dl/dt = l (g - alpha - beta)
    dd/dt = kappa(pi) - pi - d g     = d (r - kappa/nu + delta) + kappa - (1 - w)

  each written as two flows: wage bargaining in and productivity out of w,
  hiring in and labour-force growth out of l, borrowing (investment not
  paid from profits) in and dilution by growth out of d.

  Phi(l) = phi1/(1 - l)^2 - phi0 and kappa(pi) = kappa0 + kappa1 exp(kappa2
  pi) (G&CL). They are parameterised by where and how steeply they cross
  the good equilibrium, which data place better than the raw constants:
  l* = 1 - u* with Phi(l*) = alpha and its slope there phi-slope; pi* with kappa(pi*)
  = nu (alpha + beta + delta) and its slope there kappa-slope. from-raw and
  to-raw convert. Two guards keep members of an ensemble finite: Phi reads
  l at most 0.995 and is at most phi-max, kappa is at most kappa-max (both
  far from the G&CL paths).

  The good equilibrium is (w, l, d) = (1 - pi* - r d*, l*, d*), d* = (nu
  (alpha + beta + delta) - pi*)/(alpha + beta); the bad one has w, l -> 0,
  d -> infinity, and its basin is the high-debt side of a surface the
  diagnostics measure (blowup-year, critical-debt).

  country-rows: a country's panel ({var {data-year [year-known value]}})
  read year by year, each year end y seeing data years before y published
  by y (known); calibrated every few years on a window ending at y - 1 and
  carried forward between; one row per year and diagnostic."
  (:require [bnm.assimilate :as as]
            [bnm.stockflow :as sf]))

;; ---------------------------------------------------------------------------
;; Functional forms

(def gcl
  "Grasselli & Costa Lima (2012), the baseline: Phi with phi0 = 0.04/(1 -
  0.04^2), phi1 = 0.04^3/(1 - 0.04^2); kappa0 = -0.0065, kappa1 = e^-5,
  kappa2 = 20."
  {:alpha 0.025 :beta 0.02 :delta 0.01 :nu 3.0 :r 0.03
   :phi0 (/ 0.04 (- 1.0 (* 0.04 0.04))) :phi1 (/ (Math/pow 0.04 3) (- 1.0 (* 0.04 0.04)))
   :kappa0 -0.0065 :kappa1 (Math/exp -5.0) :kappa2 20.0})

(def guards {:phi-max 5.0 :kappa-max 2.0})

(defn- k-star ^double [{:keys [alpha beta delta nu]}] (* (double nu) (+ alpha beta delta)))
(defn- k-span ^double [p] (max 1e-6 (- (k-star p) (double (:kappa0 p)))))

(defn from-raw
  "G&CL's constants (phi0 phi1 kappa0 kappa1 kappa2 and alpha beta delta nu
  r) as this model's parameters."
  [{:keys [alpha phi0 phi1 kappa0 kappa1 kappa2] :as raw}]
  (let [ustar (Math/sqrt (/ phi1 (+ alpha phi0)))
        span (- (k-star raw) kappa0)]
    (merge (select-keys raw [:alpha :beta :delta :nu :r :kappa0])
           guards
           {:ustar ustar :phi-slope (/ (* 2.0 phi1) (Math/pow ustar 3))
            :pistar (/ (Math/log (/ span kappa1)) kappa2) :kappa-slope (* kappa2 span)})))

(defn to-raw
  "The parameters as G&CL's constants."
  [{:keys [alpha ustar phi-slope pistar kappa-slope kappa0] :as p}]
  (let [phi1 (/ (* phi-slope (Math/pow ustar 3)) 2.0)
        span (k-span p)
        kappa2 (/ kappa-slope span)]
    {:phi0 (- (/ phi1 (Math/pow ustar 2)) alpha) :phi1 phi1
     :kappa0 kappa0 :kappa1 (* span (Math/exp (- (* kappa2 pistar)))) :kappa2 kappa2}))

(defn- phillips* ^double [alpha ustar s lam phi-max]
  (let [lam (min 0.995 (double lam)) u* (max 1e-4 (min 1.0 (double ustar)))]
    (min (double phi-max)
         (+ (- (/ (* s u* u* u*) (* 2.0 (- 1.0 lam) (- 1.0 lam))) (/ (* s u*) 2.0)) alpha))))

(defn- investment* ^double [kappa0 span pistar ks pi kappa-max]
  (min (double kappa-max) (+ kappa0 (* span (Math/exp (min 50.0 (* (/ ks span) (- pi pistar))))))))

(defn phillips "Phi(l) for parameters p." ^double [p lam]
  (phillips* (:alpha p) (:ustar p) (:phi-slope p) lam (get p :phi-max (:phi-max guards))))

(defn investment "kappa(pi) for parameters p." ^double [p pi]
  (investment* (:kappa0 p) (k-span p) (:pistar p) (:kappa-slope p) pi (get p :kappa-max (:kappa-max guards))))

;; ---------------------------------------------------------------------------
;; The model

(def ^:private defaults
  {:keen (merge (from-raw gcl) {:omega0 0.8 :lambda0 0.9 :d0 0.1})
   :goodwin {:alpha 0.02 :beta 0.01 :delta 0.01 :nu 3.0 :r 0.0 :gamma 0.5 :rho 0.55
             :omega0 0.8 :lambda0 0.9 :d0 0.0}})

(defn finite?
  "Whether x is a number, not infinite (NaN fails every comparison)."
  [x] (< (Math/abs (double x)) 1e300))

(defn- clamp
  "x within [lo hi]; not a number at hi (a member run away)."
  ^double [x lo hi]
  (let [x (double x)] (if (== x x) (max (double lo) (min (double hi) x)) (double hi))))

(defn model
  "The Keen model as a bnm.stockflow model, :params overriding the
  defaults (G&CL's baseline); :form :goodwin for Goodwin's (1967): a linear
  Phillips curve Phi(l) = rho l - gamma, all profits invested (kappa(pi) =
  pi), r = 0, where d stays at d0 = 0."
  ([] (model {}))
  ([{:keys [form params] :or {form :keen}}]
   (let [base (defaults form)
         _ (when-let [bad (seq (remove (set (keys base)) (keys params)))]
             (throw (ex-info (str "bnm.keen/model: no parameters " (vec bad)) {:names (vec bad)})))
         aux (case form
               :keen {:phillips (fn [v] (phillips* (v :alpha) (v :ustar) (v :phi-slope) (v :lambda) (v :phi-max)))
                      :investment (fn [v] (let [span (max 1e-6 (- (* (v :nu) (+ (v :alpha) (v :beta) (v :delta))) (v :kappa0)))]
                                            (investment* (v :kappa0) span (v :pistar) (v :kappa-slope) (v :profit-share) (v :kappa-max))))}
               :goodwin {:phillips (fn [v] (- (* (v :rho) (v :lambda)) (v :gamma)))
                         :investment (fn [v] (v :profit-share))})]
     (sf/model
      {:params (merge base params)
       :stocks {:omega {:init (fn [v] (v :omega0)) :unit "1"}
                :lambda {:init (fn [v] (v :lambda0)) :unit "1"}
                :d {:init (fn [v] (v :d0)) :unit "1"}}
       :aux (merge aux
                   {:profit-share (fn [v] (- 1.0 (v :omega) (* (v :r) (v :d))))
                    :growth (fn [v] (- (/ (v :investment) (v :nu)) (v :delta)))
                    ;; what calibration sees: the stocks bounded, so a member
                    ;; gone astray stays finite in the ensemble's statistics
                    :omega-seen (fn [v] (clamp (v :omega) 0.0 1.5))
                    :lambda-seen (fn [v] (clamp (v :lambda) 0.0 1.0))
                    :d-seen (fn [v] (clamp (v :d) -1.0 10.0))})
       :flows {:wage-bargain {:to :omega :rate (fn [v] (* (v :omega) (v :phillips))) :unit "1/year"}
               :productivity {:from :omega :rate (fn [v] (* (v :omega) (v :alpha))) :unit "1/year"}
               :hiring {:to :lambda :rate (fn [v] (* (v :lambda) (v :growth))) :unit "1/year"}
               :labour-supply {:from :lambda :rate (fn [v] (* (v :lambda) (+ (v :alpha) (v :beta)))) :unit "1/year"}
               :borrowing {:to :d :rate (fn [v] (- (v :investment) (v :profit-share))) :unit "1/year"}
               :dilution {:from :d :rate (fn [v] (* (v :d) (v :growth))) :unit "1/year"}}}))))

(def ^:private keen-model (delay (model)))
(def ^:private param-names (delay (set (keys (:params @keen-model)))))
(defn- only-params [p] (select-keys p @param-names))

;; ---------------------------------------------------------------------------
;; Diagnostics

(defn field
  "The model's vector field under p as a function of (w l d) -> [dw dl dd]:
  the same flows as model's, without the engine's lookups, for the many
  runs the diagnostics make (tested equal to the engine's)."
  [p]
  (let [{:keys [alpha beta delta nu r ustar phi-slope pistar kappa-slope kappa0 phi-max kappa-max]}
        (merge guards p)
        alpha (double alpha) beta (double beta) delta (double delta) nu (double nu) r (double r)
        span (k-span p)]
    (fn [w l d]
      (let [w (double w) l (double l) d (double d)
            pi (- 1.0 w (* r d))
            phi (phillips* alpha ustar phi-slope l phi-max)
            kappa (investment* kappa0 span pistar kappa-slope pi kappa-max)
            g (- (/ kappa nu) delta)]
        [(- (* w phi) (* w alpha)) (- (* l g) (* l (+ alpha beta))) (- (- kappa pi) (* d g))]))))

(defn path
  "The model's path from `state` under p: [[t w l d] ...] every dt over
  :years, lazily (so a search can stop early), ending early where it stops
  being finite. :method :euler (the default) is the engine's own step, so
  the path is model's run; :rk4 the classical fourth-order step, for the
  continuous-time model where Euler needs impractically short steps (G&CL's
  baseline is stiff near full employment)."
  [p {:keys [omega lambda d]} {:keys [years dt method] :or {method :euler}}]
  (let [f (field p) dt (double dt) n (long (Math/round (/ (double years) dt)))
        euler (fn [w l x] (let [[a b c] (f w l x)] [(+ w (* dt a)) (+ l (* dt b)) (+ x (* dt c))]))
        rk4 (fn [w l x]
              (let [h (/ dt 2.0)
                    [a1 b1 c1] (f w l x)
                    [a2 b2 c2] (f (+ w (* h a1)) (+ l (* h b1)) (+ x (* h c1)))
                    [a3 b3 c3] (f (+ w (* h a2)) (+ l (* h b2)) (+ x (* h c2)))
                    [a4 b4 c4] (f (+ w (* dt a3)) (+ l (* dt b3)) (+ x (* dt c3)))
                    k (/ dt 6.0)]
                [(+ w (* k (+ a1 (* 2 a2) (* 2 a3) a4))) (+ l (* k (+ b1 (* 2 b2) (* 2 b3) b4)))
                 (+ x (* k (+ c1 (* 2 c2) (* 2 c3) c4)))]))
        advance (case method :euler euler :rk4 rk4)]
    (letfn [(step [i w l x]
              (lazy-seq
               (cons [(* i dt) w l x]
                     (when (and (< i n) (finite? w) (finite? l) (finite? x))
                       (let [[w' l' x'] (advance w l x)]
                         (step (inc i) w' l' x'))))))]
      (step 0 (double omega) (double lambda) (double d)))))

(defn rates
  "{:domega :dlambda :dd :pi :dpi} at state {:omega :lambda :d} under
  parameters p; dpi = -dw - r dd."
  [p {:keys [omega lambda d]}]
  (let [[dw dl dd] ((field p) omega lambda d)]
    {:domega dw :dlambda dl :dd dd
     :pi (- 1.0 omega (* (double (:r p)) d)) :dpi (- (- dw) (* (double (:r p)) dd))}))

(defn minsky?
  "The Minsky moment: debt rising while the profit share falls."
  [{:keys [dd dpi]}]
  (and (pos? dd) (neg? dpi)))

(defn good-equilibrium
  "{:omega :lambda :d :pi} of the good equilibrium."
  [{:keys [alpha beta r ustar pistar] :as p}]
  (let [d (/ (- (k-star p) pistar) (+ alpha beta))]
    {:omega (- 1.0 pistar (* r d)) :lambda (- 1.0 ustar) :d d :pi pistar}))

(defn good-stable?
  "Whether the good equilibrium is locally stable: the Jacobian's
  characteristic polynomial x^3 + a1 x^2 + a2 x + a3 by Routh-Hurwitz (a1,
  a3 > 0, a1 a2 > a3)."
  [p]
  (let [eq (good-equilibrium p)
        ks [:omega :lambda :d] ds [:domega :dlambda :dd]
        h 1e-6
        col (fn [k] (let [up (rates p (update eq k + h)) dn (rates p (update eq k - h))]
                      (mapv #(/ (- (up %) (dn %)) (* 2.0 h)) ds)))
        cols (mapv col ks)
        j (fn [i c] (get-in cols [c i]))          ; row i (equation), column c (variable)
        tr (+ (j 0 0) (j 1 1) (j 2 2))
        minor (fn [a b] (- (* (j a a) (j b b)) (* (j a b) (j b a))))
        det (+ (* (j 0 0) (- (* (j 1 1) (j 2 2)) (* (j 1 2) (j 2 1))))
               (- (* (j 0 1) (- (* (j 1 0) (j 2 2)) (* (j 1 2) (j 2 0)))))
               (* (j 0 2) (- (* (j 1 0) (j 2 1)) (* (j 1 1) (j 2 0)))))
        a1 (- tr) a2 (+ (minor 0 1) (minor 0 2) (minor 1 2)) a3 (- det)]
    (and (pos? a1) (pos? a3) (> (* a1 a2) a3))))

(def diverge-defaults {:years 20.0 :dt 0.1 :d-max 10.0 :lambda-min 0.5 :d-hi 8.0})

(defn blowup-year
  "Years until the path from `state` under p crosses into the bad basin
  (d above :d-max or l below :lambda-min, or no longer finite) within
  :years, or nil."
  [p state opts]
  (let [{:keys [d-max lambda-min] :as o} (merge diverge-defaults opts)]
    (first (keep (fn [[t w l d]] (when (or (not (finite? w)) (not (finite? l)) (not (finite? d))
                                           (> d d-max) (< l lambda-min))
                                   t))
                 (path p state o)))))

(defn critical-debt
  "The least debt ratio from which, at the state's w and l, the path
  crosses into the bad basin within :years (bisection on [0, :d-hi]); 0
  when even no debt does, nil when even :d-hi does not."
  [p {:keys [omega lambda]} opts]
  (let [{:keys [d-hi] :as o} (merge diverge-defaults opts)
        bad? (fn [d] (some? (blowup-year p {:omega omega :lambda lambda :d d} o)))]
    (cond (bad? 0.0) 0.0
          (not (bad? d-hi)) nil
          :else (loop [lo 0.0 hi (double d-hi) i 0]
                  (if (>= i 14)
                    hi
                    (let [mid (/ (+ lo hi) 2.0)]
                      (if (bad? mid) (recur lo mid (inc i)) (recur mid hi (inc i)))))))))

;; ---------------------------------------------------------------------------
;; Calibration

(defn- mean [xs] (when (seq xs) (/ (reduce + 0.0 xs) (count xs))))

(def calibrated
  "The parameters calibrate-window learns by default, with the initial state."
  [:ustar :phi-slope :pistar :kappa-slope :nu :delta])

(defn calibrate-window
  "The model calibrated on `obs` ({year {:omega :lambda :d}}, any subset
  per year) by bnm.assimilate/calibrate (ES-MDA), from the first year with
  all three observed, whose state is its prior's centre; `exog` ({:alpha
  :beta :delta :nu :r :kappa0}) gives what is held, and the centres of nu
  and delta's priors. Priors: u* log-normal at the window's mean
  unemployment (sd 0.4 in the log), pi* normal at its mean profit share (sd
  0.05), the slopes log-normal (phi-slope median 1.5, kappa-slope 2, sd 0.8),
  nu at exog's (sd 0.25 in the log), delta at exog's (sd 0.4). Options
  :calibrate (the names learned, by default `calibrated`), :members
  :iterations :seed :dt, :noise {:omega :lambda :d} (by default 0.015,
  0.015, 0.02 + 0.1 mean d), :prior-shift {param shift}. {:params
  (posterior: medians of log-normal parameters, means of the rest, with
  what was held) :members [params] :from}."
  [obs exog {:keys [members iterations seed dt noise prior-shift] :as opts
             :or {members 50 iterations 4 seed 1 dt 0.1}}]
  (let [full? (fn [o] (every? #(some? (o %)) [:omega :lambda :d]))
        from (first (for [[y o] (sort-by key obs) :when (full? o)] y))
        _ (when-not from (throw (ex-info "bnm.keen/calibrate-window: no year with every variable" {})))
        base (merge (only-params (:params @keen-model)) (only-params exog))
        r (double (:r base))
        rows (for [[y o] (sort-by key obs) :when (>= y from)] [y o])
        start (obs from)
        md (mean (keep #(:d (second %)) rows))
        noise (merge {:omega 0.015 :lambda 0.015 :d (+ 0.02 (* 0.1 (Math/abs md)))} noise)
        shift (fn [k x] (+ x (get prior-shift k 0.0)))
        learn (set (or (:calibrate opts) calibrated))
        priors (merge
                (select-keys
                 {:ustar {:mean (max 0.005 (shift :ustar (- 1.0 (mean (keep #(:lambda (second %)) rows))))) :sd 0.4 :log true}
                  :pistar {:mean (shift :pistar (mean (for [[_ o] rows :when (full? o)] (- 1.0 (:omega o) (* r (:d o)))))) :sd 0.05}
                  :phi-slope {:mean (shift :phi-slope 1.5) :sd 0.8 :log true}
                  :kappa-slope {:mean (shift :kappa-slope 2.0) :sd 0.8 :log true}
                  :nu {:mean (shift :nu (:nu base)) :sd 0.25 :log true}
                  :delta {:mean (shift :delta (:delta base)) :sd 0.4 :log true}}
                 learn)
                {:omega0 {:mean (:omega start) :sd (:omega noise)}
                 :lambda0 {:mean (:lambda start) :sd (:lambda noise)}
                 :d0 {:mean (:d start) :sd (:d noise)}})
        seen {:omega :omega-seen :lambda :lambda-seen :d :d-seen}
        observations (into {} (for [[y o] rows]
                                [(double y) (into {} (for [[k x] o :when (and (seen k) (some? x))] [(seen k) (double x)]))]))
        post (as/calibrate (model {:params base})
                           {:from (double from) :dt dt :members members :seed seed :iterations iterations
                            :priors priors :observations observations
                            :noise (into {} (for [[k s] seen] [s (noise k)]))})
        ps (vec (sort (keys priors)))
        natural (fn [k x] (if (:log (priors k)) (Math/exp x) x))]
    {:params (merge base (into {} (for [[k s] (:params post)] [k (if (:log (priors k)) (:median s) (:mean s))])))
     :members (vec (for [theta (:members post)] (merge base (zipmap ps (map natural ps theta)))))
     :from from}))

;; ---------------------------------------------------------------------------
;; A country year by year

(def variables
  "What country-rows writes."
  #{"omega" "lambda" "d" "state-year" "profit-share" "minsky-observed"
    "dd-dt" "dpi-dt" "minsky" "model-omega" "model-lambda" "model-d" "gap-omega" "gap-lambda" "gap-d"
    "bad-basin-prob" "years-to-blowup" "d-critical" "d-distance"
    "omega-bar" "lambda-bar" "d-bar" "d-gap" "good-stable"
    "lstar" "phi-slope" "pistar" "kappa-slope" "r" "alpha" "beta" "nu" "delta" "calibrated"})

(defn known
  "{var {data-year value}}: what `panel` ({var {data-year [year-known
  value]}}) shows at the end of year y: data years before y, published by y."
  [panel y]
  (into {} (for [[v s] panel]
             [v (into (sorted-map) (for [[yr [avail x]] s :when (and (< yr y) (<= avail y) (some? x))] [yr x]))])))

(def run-defaults
  {:members 50 :iterations 4 :window 20 :min-years 10 :every 5 :dt 0.1 :years 20.0
   :from 1950 :to 2026 :seed 1 :max-lag 5 :delta 0.05 :kappa0 -0.0065})

(defn- exog-at
  "The parameters held at year end y: r, alpha, beta as the window's means
  (bounded), nu the latest, delta and kappa0 as given."
  [k y {:keys [window delta kappa0]}]
  (let [in (fn [v] (for [[yr x] (k v) :when (>= yr (- y window))] x))
        m (fn [v dflt lo hi] (if-let [x (mean (in v))] (clamp x lo hi) dflt))]
    {:r (m :r 0.02 -0.02 0.08) :alpha (m :alpha 0.02 -0.02 0.08) :beta (m :beta 0.01 -0.01 0.04)
     :nu (if-let [x (some-> (k :nu) seq last val)] (clamp x 1.0 8.0) 3.0)
     :delta delta :kappa0 kappa0}))

(defn- state-of [k yr]
  (let [s {:omega (get-in k [:omega yr]) :lambda (get-in k [:lambda yr]) :d (get-in k [:d yr])}]
    (when (every? some? (vals s)) s)))

(defn- try-calibrate [k y {:keys [window min-years seed] :as opts}]
  (let [obs (into (sorted-map)
                  (for [yr (range (- y window) y)
                        :let [o (into {} (for [v [:omega :lambda :d] :let [x (get-in k [v yr])] :when (some? x)] [v x]))]
                        :when (seq o)]
                    [yr o]))
        full (count (filter #(= 3 (count (val %))) obs))]
    (when (>= full min-years)
      (try (assoc (calibrate-window obs (exog-at k y opts) (assoc opts :seed (+ seed y))) :origin y)
           (catch Exception _ nil)))))

(defn- diagnostics
  "{variable value} of the calibrated model at the state of data year t."
  [cal k t state opts]
  (let [p (:params cal)
        rt (rates p state)
        prev (state-of k (dec t))
        step (when prev
               (let [[_ w l d] (last (path p prev {:years 1.0 :dt (:dt opts)}))]
                 {:omega w :lambda l :d d}))
        blow (blowup-year p state opts)
        share (/ (count (filter #(blowup-year % state opts) (:members cal))) (double (count (:members cal))))
        dc (critical-debt p state opts)
        eq (good-equilibrium p)]
    (cond-> {"dd-dt" (:dd rt) "dpi-dt" (:dpi rt) "minsky" (if (minsky? rt) 1.0 0.0)
             "bad-basin-prob" share
             "omega-bar" (:omega eq) "lambda-bar" (:lambda eq) "d-bar" (:d eq) "d-gap" (- (:d state) (:d eq))
             "good-stable" (if (good-stable? p) 1.0 0.0)
             "lstar" (- 1.0 (:ustar p)) "phi-slope" (:phi-slope p) "pistar" (:pistar p) "kappa-slope" (:kappa-slope p)
             "r" (:r p) "alpha" (:alpha p) "beta" (:beta p) "nu" (:nu p) "delta" (:delta p) "calibrated" (double (:origin cal))}
      step (merge {"model-omega" (:omega step) "model-lambda" (:lambda step) "model-d" (:d step)
                   "gap-omega" (- (:omega state) (:omega step)) "gap-lambda" (- (:lambda state) (:lambda step))
                   "gap-d" (- (:d state) (:d step))})
      blow (assoc "years-to-blowup" (double blow))
      dc (assoc "d-critical" dc "d-distance" (- dc (:d state))))))

(defn country-rows
  "[{:iso :year :variable :value}] for one country's panel, year by year
  from :from to :to, each year end seeing only what was known then (see
  the namespace). Options: run-defaults."
  [iso panel opts]
  (let [{:keys [from to every max-lag] :as opts} (merge run-defaults opts)]
    (loop [y from cal nil out (transient [])]
      (if (> y to)
        (persistent! out)
        (let [k (known panel y)
              cal (if (or (nil? cal) (>= (- y (:origin cal)) every)) (or (try-calibrate k y opts) cal) cal)
              t (first (for [yr (range (dec y) (- y max-lag 1) -1) :when (state-of k yr)] yr))
              state (when t (state-of k t))
              r-now (:r (exog-at k y opts))
              pi-of (fn [s] (- 1.0 (:omega s) (* r-now (:d s))))
              earlier (when t (state-of k (- t 3)))
              vs (when state
                     (cond-> {"omega" (:omega state) "lambda" (:lambda state) "d" (:d state)
                              "state-year" (double t) "profit-share" (pi-of state)}
                       earlier (assoc "minsky-observed" (if (and (> (:d state) (:d earlier)) (< (pi-of state) (pi-of earlier))) 1.0 0.0))
                       cal (merge (diagnostics cal k t state opts))))
              rows (for [[v x] (sort-by key vs) :when (and (number? x) (finite? x))]
                     {:iso iso :year y :variable v :value (double x)})]
          (recur (inc y) cal (reduce conj! out rows)))))))

(defn csv-lines
  "The rows as CSV lines, the header first: iso,year,variable,value."
  [rows]
  (cons "iso,year,variable,value"
        (for [{:keys [iso year variable value]} rows] (str iso "," year "," variable "," value))))
