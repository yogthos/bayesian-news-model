(ns bnm.regime-filter
  "Recursive step 5b (bnm-56c.40, docs/RECURSIVE.md step 5b): a hidden
  two-state Markov chain, calm or stress, per world family, filtered once
  per origin (Hamilton 1989's forward step) from the family's world gauges
  as read at the origin, standardized against their own history. No
  outcome enters it: the outcomes reach the level only through the loading
  delta that bnm.lattice learns with the frailty. Pure.

  A chain is {:p-cc :p-ss :signs :r}: the annual probabilities of staying
  calm and staying in stress, each gauge's theory sign (+ when a high value
  reads as stress), and r, the gauges' assumed correlation, which tempers
  their joint likelihood to m/(1 + (m - 1) r) gauges' worth.")

(def financial
  "The global financial cycle: [:baa12 :us-curve :vix12] (LEADING.md's
  signs). Stress spells about 2.5 years, calm about 6-7 (docs/RECURSIVE.md
  step 5b, where the sources are)."
  {:p-cc 0.85 :p-ss 0.6 :signs [1.0 -1.0 1.0] :r 0.5})

(def food
  "The world food price regime: [:world-food12]. Spikes rare, about two
  years long."
  {:p-cc 0.9 :p-ss 0.5 :signs [1.0] :r 0.5})

(defn stationary
  "The chain's stationary stress share."
  ^double [{:keys [p-cc p-ss]}]
  (/ (- 1.0 (double p-cc)) (- 2.0 (double p-cc) (double p-ss))))

(defn persistence
  "The chain's second eigenvalue, p_cc + p_ss - 1: a departure from the
  stationary law decays by it a period."
  ^double [{:keys [p-cc p-ss]}]
  (- (+ (double p-cc) (double p-ss)) 1.0))

(defn window-factor
  "The mean of persistence^h over h in (0, w] years: the expected share of a
  window of w years spent in stress, less its stationary share, per unit
  of the departure at its start."
  ^double [chain w]
  (let [l (persistence chain) w (double w)]
    (cond (<= l 0.0) 0.0
          (>= l 1.0) 1.0
          :else (/ (- (Math/pow l w) 1.0) (* w (Math/log l))))))

(defn emission
  "The gauges' law given the regime, in units of the stress sign: calm
  N(-pbar delta, sw^2), stress N((1 - pbar) delta, sw^2), delta = 2 sw (the
  regimes two within-regime sds apart, Hamilton 1989's US growth), sw^2 =
  1 - pbar (1 - pbar) delta^2, so the mixture is a standardized gauge
  (mean 0, variance 1)."
  [chain]
  (let [pbar (stationary chain)
        sw (/ 1.0 (Math/sqrt (+ 1.0 (* 4.0 pbar (- 1.0 pbar)))))]
    {:pbar pbar :sw sw :delta (* 2.0 sw)}))

(defn step
  "The stress probability at an origin from the last one's, `alpha`, and
  the gauges `ys` read there (nil where a gauge is not read: it drops out;
  with none the chain only runs forward)."
  ^double [{:keys [p-cc p-ss signs r] :as chain} alpha ys]
  (let [alpha (double alpha)
        pred (+ (* alpha (double p-ss)) (* (- 1.0 alpha) (- 1.0 (double p-cc))))
        {:keys [pbar sw delta]} (emission chain)
        mc (- (* pbar delta)) ms (* (- 1.0 pbar) delta)
        two-var (* 2.0 sw sw)
        read (keep (fn [[y s]] (when (some? y) (* (double y) (double s)))) (map vector ys signs))
        m (count read)
        w (if (pos? m) (/ 1.0 (+ 1.0 (* (dec m) (double r)))) 0.0)
        llr (* w (reduce + 0.0 (for [y read] (/ (- (* (- y mc) (- y mc)) (* (- y ms) (- y ms))) two-var))))
        lo (+ (Math/log pred) llr)
        lc (Math/log (- 1.0 pred))
        top (Math/max lo lc)]
    (/ (Math/exp (- lo top)) (+ (Math/exp (- lo top)) (Math/exp (- lc top))))))

(defn path
  "The stress probabilities over a sequence of origins' gauges, from the
  stationary law."
  [chain yss]
  (vec (rest (reductions (fn [a ys] (step chain a ys)) (stationary chain) yss))))

(defn reader
  "(fn [t]) -> [x pi] at each of `origins` (sorted ascending inside), nil at
  any other month: pi the stress probability filtered from `drivers`
  ((fn [t]) -> the gauges read at t, nil where unread) at the origins up to
  t, x = f (pi - pbar) its loading over a window of `years`. Computed once."
  [chain origins drivers years]
  (let [os (vec (sort origins))
        ps (path chain (map drivers os))
        f (window-factor chain years)
        pbar (stationary chain)
        m (zipmap os (map (fn [p] [(* f (- (double p) pbar)) (double p)]) ps))]
    (fn [t] (get m t))))
