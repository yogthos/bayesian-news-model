(ns bnm.slow
  "The slow layer: series of many domains (economy, finance, energy, food,
  trade, technology, climate, people...) per country and for the world,
  their trends at several time scales, and how each domain drives the others.
  See docs/SLOW-LAYER.md.

  Time is in months (year × 12 + month - 1). Each observation is a value of
  a variable for a unit (a country or \"world\") over a period ending in
  month :period, fed in the month it became available, so the layer only
  ever knows what was published by then. Frequencies mix freely: a series
  is updated whenever a value arrives, with its averages decayed by the
  months that passed.

  For every series the layer keeps exponential averages of its transformed
  value at 3, 12, 36 and 120 months. They give its gaps from the 36- and
  120-month paths, its slopes over three spans, its acceleration (the short
  slope minus the middle one: faster than its own trend), and the ratio of
  recent to long-run volatility. Each feature is scaled by its own running
  root mean square, so a feature says how unusual the series is against its
  own history, and series of any unit pool.

  Every variable's next change, scaled the same way, is a Bayesian linear
  regression, discounted over time and pooled over units, on the features
  known when the previous value arrived:
  - the series' own;
  - per domain, the mean gap, slope and acceleration of the unit's other
    series;
  - those of a set of global drivers (oil, rates, ENSO...).

  A second regression on the series' own features alone is the benchmark.
  Both are scored out of sample, so the layer always says whether knowing the
  other domains helps forecast a variable (`skill`). The full regression's
  coefficients are how the domains drive each other (`drivers`)."
  (:require [bnm.math :as m]
            [clojure.string :as str]))

(set! *unchecked-math* true)

(def defaults
  {:scales [3.0 12.0 36.0 120.0]
   :norm-scale 120.0   ; months over which features and changes are normalized
   :forget 0.995       ; per-month discount of the regressions' evidence
   :dma-forget 0.995   ; per-change discount of the models' records in the average
   :prior-weight 20.0  ; prior strength, in observations
   :cross-shrink 4.0   ; how much more the prior shrinks cross-domain coefficients
   :stale 36           ; months after its last period a series stops counting
   :clip 5.0
   :globals []})       ; [unit variable] pairs whose features every regression sees

(def feature-names
  [:gap-36 :gap-120 :trend-3-12 :trend-12-36 :trend-36-120 :accel :vol-ratio])

(def ^:const F 7)          ; own features
(def ^:const SF 3)         ; summary features per domain or global: gap-120, trend-12-36, accel
(def summary-idx [1 3 5])

;; series state layout
(def ^:const Z 0)          ; last transformed value
(def ^:const P 1)          ; its period
(def ^:const N 2)          ; values seen
(def ^:const E 3)          ; 4 exponential averages
(def ^:const V12 7)        ; volatility, short and long
(def ^:const V120 8)
(def ^:const RMS 9)        ; F feature scales, then the change scale
(def ^:const RY (+ RMS F))
(def ^:const YF (+ RY 1))  ; predictions made at the last value: full, own
(def ^:const YO (+ RY 2))
(def ^:const SNAP (+ RY 3)) ; 1 when a prediction is pending
(def ^:const YD (+ RY 4))   ; the averaged prediction
(def ^:const SLEN (+ RY 5))

(defn- transform ^double [t ^double v]
  (case t
    "log" (Math/log (Math/max v 1e-12))
    "log1p" (Math/log1p (Math/max v 0.0))
    v))

;; --- model ---------------------------------------------------------------------------------

(defn- regression [k]
  {:k k :a (double-array (* k k)) :b (double-array k) :t (long-array 1 Long/MIN_VALUE)
   :beta (double-array k) :beta-t (long-array 1 Long/MIN_VALUE)})

(defn create
  "A slow layer over `variables`: maps of :name (\"domain/name\"), :domain and
  :transform (\"log\", \"log1p\" or anything else for none)."
  [config variables]
  (let [cfg (merge defaults config)
        variables (vec variables)
        domains (vec (distinct (map :domain variables)))
        k (+ 1 F (* SF (count domains)) (* SF (count (:globals cfg))))]
    {:config cfg
     :variables variables
     :var-index (zipmap (map :name variables) (range))
     :domains domains
     :domain-index (zipmap domains (range))
     :k k
     :full (mapv (fn [_] (regression k)) variables)
     :own (mapv (fn [_] (regression (inc F))) variables)
     :skill (mapv (fn [_] (double-array 5)) variables)     ; n, sse zero, own, full, averaged
     ;; per variable, each model's discounted log score and squared error, for the average
     :dma (mapv (fn [_] (double-array [0.0 0.0 1.0 1.0])) variables)
     :series (java.util.ArrayList.)                        ; {:unit :var :vi :s state :x snapshot}
     :index (java.util.HashMap.)                           ; "unit\tvar" -> series
     :by-unit (java.util.HashMap.)                         ; unit -> ArrayList of series
     :month nil}))

(defn- skey [unit v] (str unit "\t" v))

(defn- series-of [mdl unit v]
  (let [^java.util.HashMap idx (:index mdl)]
    (or (.get idx (skey unit v))
        (when-let [vi ((:var-index mdl) v)]
          (let [s {:unit unit :var v :vi vi
                   :domain-i ((:domain-index mdl) (:domain ((:variables mdl) vi)))
                   :transform (:transform ((:variables mdl) vi))
                   :s (double-array SLEN) :x (double-array (:k mdl))}
                ^java.util.HashMap bu (:by-unit mdl)]
            (.add ^java.util.ArrayList (:series mdl) s)
            (.put idx (skey unit v) s)
            (.add ^java.util.ArrayList (or (.get bu unit)
                                           (let [l (java.util.ArrayList.)] (.put bu unit l) l))
                  s)
            s)))))

;; --- trends --------------------------------------------------------------------------------

(defn- raw-features!
  "The series' raw features into out[0..F)."
  [^doubles s ^doubles out]
  (let [z (aget s Z)
        e3 (aget s E) e12 (aget s (+ E 1)) e36 (aget s (+ E 2)) e120 (aget s (+ E 3))
        s1 (/ (- e3 e12) 9.0) s2 (/ (- e12 e36) 24.0) s3 (/ (- e36 e120) 84.0)]
    (aset out 0 (- z e36))
    (aset out 1 (- z e120))
    (aset out 2 s1)
    (aset out 3 s2)
    (aset out 4 s3)
    (aset out 5 (- s1 s2))
    (aset out 6 (Math/log (/ (+ (aget s V12) 1e-12) (+ (aget s V120) 1e-12))))
    out))

(defn- scaled-features!
  "The series' features scaled by their running RMS and clipped, into out[off..off+F)."
  [^doubles s ^doubles out ^long off ^double clip]
  (let [raw (raw-features! s (double-array F))]
    (dotimes [i F]
      (let [r (Math/sqrt (aget s (+ RMS i)))
            v (if (> r 1e-12) (/ (aget raw i) r) 0.0)]
        (aset out (+ off i) (Math/max (- clip) (Math/min clip v)))))
    out))

(defn- update-series!
  "Advances a series' state with a transformed value z for period p."
  [cfg ^doubles s ^double z ^long p]
  (let [n (aget s N)]
    (if (zero? n)
      (do (dotimes [i 4] (aset s (+ E i) z))
          (aset s Z z) (aset s P (double p)) (aset s N 1.0))
      (let [dt (Math/max 1.0 (- (double p) (aget s P)))
            ^doubles sc (double-array (:scales cfg))
            slope (/ (- (aget s (+ E 1)) (aget s (+ E 2))) 24.0)
            innov (- (/ (- z (aget s Z)) dt) slope)
            dn (Math/exp (- (/ dt (double (:norm-scale cfg)))))]
        (dotimes [i 4]
          (let [d (Math/exp (- (/ dt (aget sc i))))]
            (aset s (+ E i) (+ (* d (aget s (+ E i))) (* (- 1.0 d) z)))))
        (let [d12 (Math/exp (- (/ dt 12.0)))]
          (aset s V12 (+ (* d12 (aget s V12)) (* (- 1.0 d12) innov innov)))
          (aset s V120 (+ (* dn (aget s V120)) (* (- 1.0 dn) innov innov))))
        (aset s Z z) (aset s P (double p)) (aset s N (inc n))
        (let [raw (raw-features! s (double-array F))]
          (dotimes [i F]
            (let [r2 (* (aget raw i) (aget raw i))]
              (aset s (+ RMS i) (if (< n 2.0) r2 (+ (* dn (aget s (+ RMS i))) (* (- 1.0 dn) r2)))))))))))

;; --- regressions ---------------------------------------------------------------------------

(defn- decay! [reg ^long t ^double forget]
  (let [^longs tt (:t reg)
        last (aget tt 0)]
    (when (and (not= last Long/MIN_VALUE) (> t last))
      (let [d (Math/pow forget (- t last))
            ^doubles a (:a reg) ^doubles b (:b reg)]
        (dotimes [i (alength a)] (aset a i (* d (aget a i))))
        (dotimes [i (alength b)] (aset b i (* d (aget b i))))))
    (aset tt 0 t)))

(defn- learn! [reg ^doubles x ^double y ^long t ^double forget]
  (decay! reg t forget)
  (let [k (long (:k reg)) ^doubles a (:a reg) ^doubles b (:b reg)]
    (dotimes [i k]
      (let [xi (aget x i)]
        (when-not (zero? xi)
          (aset b i (+ (aget b i) (* xi y)))
          (dotimes [j k]
            (aset a (+ (* i k) j) (+ (aget a (+ (* i k) j)) (* xi (aget x j))))))))))

(defn- prior-precision ^double [cfg ^long i]
  ;; the intercept is nearly free, the series' own features shrink a little,
  ;; the other domains and the globals more
  (* (double (:prior-weight cfg))
     (cond (zero? i) 0.01
           (<= i F) 1.0
           :else (double (:cross-shrink cfg)))))

(defn- beta! ^doubles [cfg reg ^long t]
  (let [^longs bt (:beta-t reg)]
    (if (= t (aget bt 0))
      (:beta reg)
      (let [k (long (:k reg))
            a (aclone ^doubles (:a reg))]
        (dotimes [i k] (aset a (+ (* i k) i) (+ (aget a (+ (* i k) i)) (prior-precision cfg i))))
        (let [beta (m/solve-spd a (:b reg) k)]
          (System/arraycopy beta 0 (:beta reg) 0 k)
          (aset bt 0 t)
          (:beta reg))))))

(defn- dot ^double [^doubles a ^doubles b ^long n]
  (loop [i 0 acc 0.0] (if (< i n) (recur (inc i) (+ acc (* (aget a i) (aget b i)))) acc)))

;; --- features of a unit and the world --------------------------------------------------------

(defn- unit-summary
  "Per domain, sums of the scaled summary features of the unit's fresh series,
  and how many: [sums (D × SF) counts (D)]."
  [mdl unit ^long t]
  (let [cfg (:config mdl)
        nd (count (:domains mdl))
        sums (double-array (* nd SF))
        counts (double-array nd)
        buf (double-array F)
        clip (double (:clip cfg))
        stale (long (:stale cfg))]
    (when-let [^java.util.ArrayList l (.get ^java.util.HashMap (:by-unit mdl) unit)]
      (dotimes [i (.size l)]
        (let [sr (.get l i) ^doubles s (:s sr)]
          (when (and (>= (aget s N) 3.0) (<= (- t (long (aget s P))) stale))
            (scaled-features! s buf 0 clip)
            (let [d (long (:domain-i sr))]
              (dotimes [q SF]
                (aset sums (+ (* d SF) q) (+ (aget sums (+ (* d SF) q)) (aget buf (long (summary-idx q))))))
              (aset counts d (inc (aget counts d))))))))
    [sums counts]))

(defn- fill-x!
  "The regression inputs for series `sr` as things stand: intercept, own
  features, the unit's other series per domain (leaving this one out), globals."
  [mdl sr summary global-feats ^long t]
  (let [cfg (:config mdl)
        clip (double (:clip cfg))
        ^doubles x (:x sr)
        ^doubles s (:s sr)
        nd (count (:domains mdl))
        [^doubles sums ^doubles counts] summary
        own (scaled-features! s (double-array F) 0 clip)
        mine (long (:domain-i sr))
        fresh? (and (>= (aget s N) 3.0) (<= (- t (long (aget s P))) (long (:stale cfg))))]
    (aset x 0 1.0)
    (System/arraycopy own 0 x 1 F)
    (dotimes [d nd]
      (let [c (- (aget counts d) (if (and fresh? (= d mine)) 1.0 0.0))]
        (dotimes [q SF]
          (let [v (- (aget sums (+ (* d SF) q))
                     (if (and fresh? (= d mine)) (aget own (long (summary-idx q))) 0.0))]
            (aset x (+ 1 F (* d SF) q) (if (pos? c) (/ v c) 0.0))))))
    (System/arraycopy ^doubles global-feats 0 x (+ 1 F (* nd SF)) (alength ^doubles global-feats))
    x))

(defn- global-features ^doubles [mdl ^long t]
  (let [cfg (:config mdl)
        gs (:globals cfg)
        out (double-array (* SF (count gs)))
        buf (double-array F)]
    (doseq [[g [unit v]] (map-indexed vector gs)]
      (when-let [sr (.get ^java.util.HashMap (:index mdl) (skey unit v))]
        (let [^doubles s (:s sr)]
          (when (and (>= (aget s N) 3.0) (<= (- t (long (aget s P))) (long (:stale cfg))))
            (scaled-features! s buf 0 (double (:clip cfg)))
            (dotimes [q SF] (aset out (+ (* g SF) q) (aget buf (long (summary-idx q)))))))))
    out))

;; --- a month ---------------------------------------------------------------------------------

(defn- resolve-change!
  "A new value for a series with a pending prediction: score the prediction,
  and learn from the change it predicted."
  [mdl sr ^double z ^long p ^long t]
  (let [^doubles s (:s sr)
        cfg (:config mdl)]
    (when (and (pos? (aget s SNAP)) (>= (aget s N) 3.0))
      (let [dt (Math/max 1.0 (- (double p) (aget s P)))
            raw (/ (- z (aget s Z)) dt)
            r (Math/sqrt (aget s RY))
            clip (+ 1.0 (double (:clip cfg)))
            y (if (> r 1e-12) (Math/max (- clip) (Math/min clip (/ raw r))) 0.0)
            vi (long (:vi sr))
            ^doubles x (:x sr)
            xo (double-array (inc F))
            ^doubles sk ((:skill mdl) vi)
            forget (double (:forget cfg))]
        (System/arraycopy x 0 xo 0 (inc F))
        (let [ef (- y (aget s YF)) eo (- y (aget s YO)) ed (- y (aget s YD))
              ^doubles dm ((:dma mdl) vi)
              a (double (:dma-forget cfg))]
          (aset sk 0 (inc (aget sk 0)))
          (aset sk 1 (+ (aget sk 1) (* y y)))
          (aset sk 2 (+ (aget sk 2) (* eo eo)))
          (aset sk 3 (+ (aget sk 3) (* ef ef)))
          (aset sk 4 (+ (aget sk 4) (* ed ed)))
          ;; each model's record: a Gaussian log score at its own recent error
          (aset dm 0 (+ (* a (aget dm 0)) (- (* -0.5 (/ (* eo eo) (aget dm 2))) (* 0.5 (Math/log (aget dm 2))))))
          (aset dm 1 (+ (* a (aget dm 1)) (- (* -0.5 (/ (* ef ef) (aget dm 3))) (* 0.5 (Math/log (aget dm 3))))))
          (aset dm 2 (+ (* a (aget dm 2)) (* (- 1.0 a) (Math/max 1e-4 (* eo eo)))))
          (aset dm 3 (+ (* a (aget dm 3)) (* (- 1.0 a) (Math/max 1e-4 (* ef ef))))))
        (learn! ((:full mdl) vi) x y t forget)
        (learn! ((:own mdl) vi) xo y t forget)))
    ;; the change scale learns from every change
    (when (>= (aget s N) 1.0)
      (let [dt (Math/max 1.0 (- (double p) (aget s P)))
            raw (/ (- z (aget s Z)) dt)
            dn (Math/exp (- (/ dt (double (:norm-scale cfg)))))]
        (aset s RY (if (< (aget s N) 2.0) (* raw raw) (+ (* dn (aget s RY)) (* (- 1.0 dn) raw raw))))))))

(defn- predict!
  "Makes and keeps the series' prediction of its next change, from what is
  known in month t."
  [mdl sr summary gfeats ^long t]
  (let [^doubles s (:s sr)
        cfg (:config mdl)
        vi (long (:vi sr))
        x (fill-x! mdl sr summary gfeats t)
        full ((:full mdl) vi)
        own ((:own mdl) vi)]
    (aset s YF (dot (beta! cfg full t) x (:k mdl)))
    (aset s YO (dot (beta! cfg own t) x (inc F)))
    ;; the two, weighted by their records (dynamic model averaging)
    (let [^doubles dm ((:dma mdl) vi)
          d (- (aget dm 1) (aget dm 0))
          wf (/ 1.0 (+ 1.0 (Math/exp (- (Math/max -50.0 (Math/min 50.0 d))))))]
      (aset s YD (+ (* wf (aget s YF)) (* (- 1.0 wf) (aget s YO)))))
    (aset s SNAP 1.0)))

(defn step!
  "Feeds month `t`'s newly available observations: maps of :unit, :var,
  :period (the month its period ends) and :value. Series whose variable is
  unknown are ignored. Returns the model."
  [mdl ^long t observations]
  (let [cfg (:config mdl)
        touched (java.util.ArrayList.)]
    (doseq [o (sort-by :period observations)
            :let [sr (series-of mdl (:unit o) (:var o))]
            :when sr
            :let [^doubles s (:s sr)
                  p (long (:period o))]
            :when (or (zero? (aget s N)) (> p (long (aget s P))))]
      (let [z (transform (:transform sr) (double (:value o)))]
        (resolve-change! mdl sr z p t)
        (update-series! cfg s z p)
        (.add touched sr)))
    (when (pos? (.size touched))
      (let [gfeats (global-features mdl t)
            summaries (java.util.HashMap.)]
        (dotimes [i (.size touched)]
          (let [sr (.get touched i)
                u (:unit sr)
                summary (or (.get summaries u)
                            (let [x (unit-summary mdl u t)] (.put summaries u x) x))]
            (when (>= (aget ^doubles (:s sr) N) 3.0)
              (predict! mdl sr summary gfeats t))))))
    (assoc mdl :month t)))

;; --- reading ----------------------------------------------------------------------------------

(defn features
  "The scaled features of a series, by name."
  [mdl unit v]
  (when-let [sr (.get ^java.util.HashMap (:index mdl) (skey unit v))]
    (zipmap feature-names (scaled-features! (:s sr) (double-array F) 0 (double (get-in mdl [:config :clip]))))))

(defn last-period [mdl unit v]
  (some-> (.get ^java.util.HashMap (:index mdl) (skey unit v)) :s (aget P) long))

(defn skill
  "Out-of-sample mean squared error of a variable's scaled changes predicted
  as no change (:zero), from the series' own history (:own), with the other
  domains and the globals (:full), and by the two averaged by their records
  (:dma, Raftery, Karny & Ettler 2010), over :n changes; and the weight the
  average now gives the full model (:w-full)."
  [mdl v]
  (when-let [vi ((:var-index mdl) v)]
    (let [^doubles sk ((:skill mdl) vi)
          n (aget sk 0)]
      (when (pos? n)
        {:n (long n) :zero (/ (aget sk 1) n) :own (/ (aget sk 2) n) :full (/ (aget sk 3) n)
         :dma (/ (aget sk 4) n)
         :w-full (let [^doubles dm ((:dma mdl) vi)]
                   (/ 1.0 (+ 1.0 (Math/exp (- (Math/max -50.0 (Math/min 50.0 (- (aget dm 1) (aget dm 0)))))))))}))))

(defn- input-names
  "What each input of the full regression is."
  [mdl]
  (vec (concat [{:source :intercept}]
               (for [f feature-names] {:source :own :feature f})
               (for [d (:domains mdl) q [:gap-120 :trend-12-36 :accel]] {:source d :feature q})
               (for [g (get-in mdl [:config :globals]) q [:gap-120 :trend-12-36 :accel]]
                 {:source g :feature q}))))

(defn drivers
  "What drives variable `v` from outside its own series, strongest first:
  {:source (a domain of the same unit, or a global [unit var]) :feature :beta}."
  [mdl v]
  (when-let [vi ((:var-index mdl) v)]
    (let [beta (beta! (:config mdl) ((:full mdl) vi) (long (or (:month mdl) 0)))]
      (->> (map (fn [nm b] (assoc nm :beta b)) (input-names mdl) beta)
           (remove #(#{:intercept :own} (:source %)))
           (sort-by #(- (Math/abs (double (:beta %)))))))))

(defn unit-inputs
  "What the slow layer says about `unit` in month `t`, for the hazard layer:
  per domain the mean gap, slope and acceleration of its fresh series and
  whether it has any, then the globals' features. Labelled by `input-labels`."
  ^doubles [mdl unit ^long t]
  (let [[^doubles sums ^doubles counts] (unit-summary mdl unit t)
        g (global-features mdl t)
        nd (count (:domains mdl))
        out (double-array (+ (* nd (inc SF)) (alength g)))]
    (dotimes [d nd]
      (let [c (aget counts d)]
        (dotimes [q SF]
          (aset out (+ (* d (inc SF)) q) (if (pos? c) (/ (aget sums (+ (* d SF) q)) c) 0.0)))
        (aset out (+ (* d (inc SF)) SF) (if (pos? c) 1.0 0.0))))
    (System/arraycopy g 0 out (* nd (inc SF)) (alength g))
    out))

(defn input-labels
  "{:domain :feature} or {:global [unit var] :feature} for each of `unit-inputs`."
  [mdl]
  (vec (concat (for [d (:domains mdl) q [:gap-120 :trend-12-36 :accel :present]] {:domain d :feature q})
               (for [g (get-in mdl [:config :globals]) q [:gap-120 :trend-12-36 :accel]] {:global g :feature q}))))
