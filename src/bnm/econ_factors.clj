(ns bnm.econ-factors
  "Latent factors of economic character per country-year (beads bnm-3ka.4
  and bnm-3ka.5; docs/ECONOMIC-CHARACTER.md 8a, H3): econ/financialisation,
  econ/financial-depth and econ/investment-regime.

  Input: scripts/econ-factors.py's long table (data/derived/econ/
  econ-indicators.csv), rows {:iso :year :known :concept :indicator :value},
  :year the data year and :known the first year the value could be read.

  The model, per concept, is one-factor factor analysis with a diagonal
  noise (probabilistic PCA with a variance per indicator):

    z_j = l_j f + e_j,  f ~ N(0, 1),  e_j ~ N(0, psi_j)

  z_j is indicator j centred on its median, scaled by 1.4826 MAD, clipped
  to +-4 (offshore centres and tiny economies do not set the scale), then
  rescaled to mean 0 and SD 1 over the training sample, so a loading reads
  as a correlation. It is fitted by EM over the country-years with any
  indicators observed: a missing indicator drops out of that row's
  likelihood (missing at random), so a row's posterior uses only what it
  has. An indicator with fewer than :min-obs observations is left out.

  A :control indicator is partialled out first (doc 8a: financial
  development enters as a control, so fin/* is what is left after depth):
  each other indicator's z is regressed on the control's z over the
  training rows, and the factor is fitted on the residuals (robustly
  rescaled the same way). The control itself is not loaded, and rows
  without it are neither fitted nor scored.

  Causality: the fit is an expanding window. The fit for year Y uses the
  country-year rows whose :known is at most Y, with medians, scales and
  control regressions from those rows too; nothing known later enters.
  Each year's EM starts from the previous year's loadings (itself causal),
  so the sequence is deterministic. A country's score at Y is the
  posterior mean of f given its latest value of each indicator known by Y
  and at most :stale data years old, under the fit for Y; its posterior SD
  and the number of indicators used are written next to it.

  Sign: a factor is identified only up to sign. It is fixed by an anchor
  indicator, never by a country: the first of the concept's :anchors that
  loads at least 0.2 in absolute value is made positive (the anchor loading
  most if none does). Each concept has one anchor so that the orientation
  cannot switch between years:
    financialisation   market capitalisation / GDP. The doc's prior was the
                       NFC payout ratio, but payouts load under 0.1 from
                       2000 on, too weak to orient the factor.
    financial-depth    the same.
    investment-regime  net public wealth / national income (8a's prior;
                       its other prior, investment / GDP loading
                       positively, fails: it loads about -0.15).

  stock-rows writes, dated by the year known:
    econ/<concept>      the posterior mean (units of the factor's SD over
                        the training panel)
    econ/<concept>-sd   its posterior SD
    econ/<concept>-n    the number of indicators it used")

(def fin-indicators
  ["nfc-payout" "log-mcap-gdp" "log-traded-gdp" "log-credit-gdp" "fd-index"
   "nfc-net-lending" "nfc-inv-profit" "fin-va-share" "private-wealth-ni"])

(def concepts
  {;; what is left after financial development (the IMF FD index)
   "financialisation"
   {:indicators fin-indicators :control "fd-index" :anchors ["log-mcap-gdp"]}
   ;; the same candidates without the control: depth, for comparison
   "financial-depth"
   {:indicators fin-indicators :anchors ["log-mcap-gdp"]}
   "investment-regime"
   {:indicators ["inv-gdp" "public-k-share" "gov-inv-share" "public-wealth-ni" "irr"]
    :anchors ["public-wealth-ni"]}})

(def defaults {:min-obs 30 :min-k 1 :stale 5 :clip 4.0 :max-iter 2000 :tol 1e-6 :psi-floor 0.005})

;; ---------------------------------------------------------------- observations

(defn observations
  "{[iso year] {indicator value}} from `rows`."
  [rows]
  (reduce (fn [m {:keys [iso year indicator value]}] (assoc-in m [[iso year] indicator] value)) {} rows))

(defn- median [xs]
  (let [v (vec (sort xs)) n (count v) h (quot n 2)]
    (if (odd? n) (v h) (/ (+ (v (dec h)) (v h)) 2.0))))

(defn- moments
  "{j {:n :mean :sd :c :s}} of each indicator over `obs`. Robust: the value
  is centred on the median and scaled by 1.4826 x the median absolute
  deviation (the SD when the MAD is zero), clipped to +-clip, so a few wild
  values (offshore centres, tiny economies) do not set the scale; the
  clipped values are then centred and scaled to mean 0 and SD 1 (:c, :s),
  so a loading reads as a correlation."
  [obs inds clip]
  (into {}
        (for [j inds
              :let [xs (keep #(get % j) obs) n (count xs)]
              :when (> n 1)
              :let [m (median xs)
                    mad (* 1.4826 (median (map #(Math/abs (- % m)) xs)))
                    sd (if (pos? mad)
                         mad
                         (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (dec n))))]
              :when (pos? sd)
              :let [zs (map #(max (- clip) (min clip (/ (- % m) sd))) xs)
                    c (/ (reduce + zs) n)
                    s (Math/sqrt (/ (reduce + (map #(let [d (- % c)] (* d d)) zs)) (dec n)))]
              :when (pos? s)]
          [j {:n n :mean m :sd sd :c c :s s}])))

(defn- standardise [x stats clip]
  (into {} (for [[j v] x :let [{:keys [mean sd c s]} (stats j)] :when s]
             [j (/ (- (max (- clip) (min clip (/ (- v mean) sd))) c) s)])))

(defn- control-coefs
  "{j [a b s u]}: the OLS of each standardised indicator on the control `c`
  over the rows `zs` that hold both; s is the robust scale of its residual
  (1.4826 MAD) and u the SD of the residual so scaled and clipped."
  [zs c inds clip]
  (into {}
        (for [j inds :when (not= j c)
              :let [ps (keep #(when-let [y (get % j)] [(get % c) y]) zs)
                    n (count ps)]
              :when (> n 2)
              :let [mx (/ (reduce + (map first ps)) n) my (/ (reduce + (map second ps)) n)
                    sxx (reduce + (map #(let [d (- (first %) mx)] (* d d)) ps))
                    b (if (pos? sxx) (/ (reduce + (map #(* (- (first %) mx) (- (second %) my)) ps)) sxx) 0.0)
                    a (- my (* b mx))
                    rs (map (fn [[x y]] (- y a (* b x))) ps)
                    m (median rs)
                    mad (* 1.4826 (median (map #(Math/abs (- % m)) rs)))]
              :when (pos? mad)
              :let [cl (map #(max (- clip) (min clip (/ % mad))) rs)
                    u (Math/sqrt (/ (reduce + (map #(* % %) cl)) n))]
              :when (pos? u)]
          [j [a b mad u]])))

(defn- residualise
  "Row `z` with the control `c` taken out of every indicator; nil when the
  row lacks the control."
  [z c coefs clip]
  (when-let [x (get z c)]
    (into {} (for [[j y] z :let [[a b s u] (coefs j)] :when s]
               [j (/ (max (- clip) (min clip (/ (- y a (* b x)) s))) u)]))))

;; ---------------------------------------------------------------- EM
;; Rows are packed as [int-array of indicator positions, double-array of
;; values]; loadings and noise as double-arrays over the fit's indicators.

(defn- posterior
  "Posterior [m v k] of f given packed row `r` under loadings `l` and noise
  `psi` (double-arrays, NaN loading = not in the fit); nil when no indicator
  of the row is in the fit."
  [[^ints idx ^doubles xs] ^doubles l ^doubles psi]
  (let [n (alength idx)]
    (loop [i 0 a 0.0 b 0.0 k 0]
      (if (< i n)
        (let [j (aget idx i) w (aget l j)]
          (if (Double/isNaN w)
            (recur (inc i) a b k)
            (let [p (aget psi j)]
              (recur (inc i) (+ a (/ (* w w) p)) (+ b (/ (* w (aget xs i)) p)) (inc k)))))
        (when (pos? k)
          (let [v (/ 1.0 (+ 1.0 a))] [(* v b) v k]))))))

(defn- em-step
  "One EM step: new [l psi] double-arrays of length `m`."
  [rows m ^doubles l ^doubles psi psi-floor]
  (let [xm (double-array m) ff (double-array m) xx (double-array m) cnt (double-array m)]
    (doseq [[^ints idx ^doubles xs :as r] rows]
      (when-let [[mu v _] (posterior r l psi)]
        (let [ef2 (+ v (* mu mu)) n (alength idx)]
          (dotimes [i n]
            (let [j (aget idx i) x (aget xs i)]
              (aset xm j (+ (aget xm j) (* x mu)))
              (aset ff j (+ (aget ff j) ef2))
              (aset xx j (+ (aget xx j) (* x x)))
              (aset cnt j (+ (aget cnt j) 1.0)))))))
    (let [l' (double-array m) psi' (double-array m)]
      (dotimes [j m]
        (if (pos? (aget ff j))
          (let [w (/ (aget xm j) (aget ff j))]
            (aset l' j w)
            (aset psi' j (max psi-floor (/ (- (aget xx j) (* w (aget xm j))) (aget cnt j)))))
          (do (aset l' j Double/NaN) (aset psi' j 1.0))))
      [l' psi'])))

(defn- pack [z pos]
  (let [ks (vec (keys z))]
    [(int-array (map pos ks)) (double-array (map z ks))]))

(defn- normalise-sign
  "Flip the factor so the first anchor loading at least 0.2 in absolute
  value is positive (if none does, the anchor loading most)."
  [{:keys [loading] :as fit} anchors]
  (let [present (filter loading anchors)
        a (or (first (filter #(>= (Math/abs (double (loading %))) 0.2) present))
              (first (sort-by #(- (Math/abs (double (loading %)))) present)))]
    (if (and a (neg? (loading a)))
      (assoc fit :loading (update-vals loading -) :flipped true :anchor a)
      (assoc fit :anchor a))))

(defn fit-obs
  "The factor fitted on `obs`, a seq of {indicator raw-value} (one per
  country-year). opts: :anchors, :control (an indicator partialled out of
  the others and not itself loaded; rows without it are dropped), :min-obs,
  :clip, :max-iter, :tol, :start (an earlier fit to start from). Returns
  {:loading {j l} :psi {j psi} :stats {j {:n :mean :sd}} :control-coefs
  :n rows :iter}."
  [obs opts]
  (let [{:keys [anchors control min-obs clip max-iter tol start psi-floor]} (merge defaults opts)
        obs (if control (filter #(contains? % control) obs) obs)
        inds (sort (distinct (mapcat keys obs)))
        stats (into {} (filter #(>= (:n (val %)) min-obs) (moments obs inds clip)))
        zs (->> obs (map #(standardise % stats clip)) (filter seq))
        coefs (when (and control (stats control)) (control-coefs zs control (keys stats) clip))
        zs (if control (keep #(residualise % control coefs clip) zs) zs)
        inds (vec (filter (if control coefs stats) inds))
        pos (zipmap inds (range))
        m (count inds)
        rows (->> zs (filter seq) (mapv #(pack % pos)))
        l0 (double-array (for [j inds] (or (get-in start [:loading j]) 0.7)))
        p0 (double-array (for [j inds] (or (get-in start [:psi j]) 0.5)))]
    (when (and (pos? m) (seq rows))
      (loop [^doubles l l0 ^doubles psi p0 it 0]
        (let [[^doubles l' psi'] (em-step rows m l psi psi-floor)
              d (loop [j 0 d 0.0] (if (< j m) (recur (inc j) (max d (Math/abs (- (aget l' j) (aget l j))))) d))]
          (if (or (< d tol) (>= (inc it) max-iter))
            (normalise-sign (cond-> {:loading (into {} (for [[j i] pos :let [w (aget l' i)] :when (not (Double/isNaN w))] [j w]))
                                     :psi (into {} (for [[j i] pos] [j (aget ^doubles psi' i)]))
                                     :stats stats :n (count rows) :iter (inc it)}
                              control (assoc :control control :control-coefs coefs))
                            anchors)
            (recur l' psi' (inc it))))))))

(defn fit-rows
  "fit-obs over the country-years of long `rows`."
  [rows opts]
  (fit-obs (vals (observations rows)) opts))

(defn score
  "{:f :sd :k} for raw values `x` {indicator value} under `fit`; nil when
  none of x's indicators is in the fit, or the fit has a control x lacks."
  [{:keys [loading psi stats control control-coefs] :as fit} x]
  (when fit
    (let [inds (vec (keys loading))
          pos (zipmap inds (range))
          z (standardise (select-keys x (cond-> inds control (conj control))) stats (:clip defaults))
          z (if control (residualise z control control-coefs (:clip defaults)) z)]
      (when (seq z)
        (when-let [[m v k] (posterior (pack z pos) (double-array (map loading inds)) (double-array (map psi inds)))]
          {:f m :sd (Math/sqrt v) :k k})))))

;; ---------------------------------------------------------------- expanding window

(defn- snapshot
  "{iso {:x {indicator value} :data-year latest}} from `rows`: each country's
  latest value of each indicator known by `y` and at most `stale` data
  years old."
  [rows y stale]
  (reduce (fn [m {:keys [iso year known indicator value]}]
            (if (and (<= known y) (<= (- y year) stale)
                     (let [t (get-in m [iso :t indicator])] (or (nil? t) (> year t))))
              (-> m
                  (assoc-in [iso :x indicator] value)
                  (assoc-in [iso :t indicator] year))
              m))
          {} rows))

(defn expanding
  "Fits for each year in `years` (ascending) on the rows known by then, and
  each country's score at each year. Returns {:fits {y fit} :scores [{:iso
  :year :f :sd :k :data-year}]}."
  [rows years opts]
  (let [{:keys [stale min-k] :as opts} (merge defaults opts)
        by-known (sort-by :known rows)]
    (loop [[y & more] years start nil fits {} scores (transient [])]
      (if-not y
        {:fits fits :scores (persistent! scores)}
        (let [known (take-while #(<= (:known %) y) by-known)
              fit (fit-obs (vals (observations known)) (assoc opts :start start))
              snap (when fit (snapshot known y stale))
              scores (reduce (fn [acc [iso {:keys [x t]}]]
                               (let [s (score fit x)]
                                 (if (and s (>= (:k s) min-k))
                                   (conj! acc (assoc s :iso iso :year y
                                                     :data-year (apply max (vals (select-keys t (keys (:loading fit))))) ))
                                   acc)))
                             scores (sort-by key snap))]
          (recur more (or fit start) (cond-> fits fit (assoc y fit)) scores))))))

(defn stock-rows
  "Rows {:iso :year :variable :value} of the scores of `res` (from
  expanding) for `concept`."
  [concept res]
  (->> (:scores res)
       (mapcat (fn [{:keys [iso year f sd k]}]
                 [{:iso iso :year year :variable (str "econ/" concept) :value (double f)}
                  {:iso iso :year year :variable (str "econ/" concept "-sd") :value (double sd)}
                  {:iso iso :year year :variable (str "econ/" concept "-n") :value (double k)}]))
       (sort-by (juxt :iso :year :variable))
       vec))
