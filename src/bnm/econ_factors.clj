(ns bnm.econ-factors
  "Latent factors of economic character per country-year (beads bnm-3ka.4
  and bnm-3ka.5; docs/ECONOMIC-CHARACTER.md 8a, H3), one per entry of
  `concepts`: econ/financialisation (an index of the NFC measures),
  econ/market-size, econ/financial-depth, econ/investment-regime and the
  comparisons. `diagnostics` are balanced-panel fits kept for their
  loadings.

  Input: scripts/econ-factors.py's long table (data/derived/econ/
  econ-indicators.csv), rows {:iso :year :known :concept :indicator :value},
  :year the data year and :known the first year the value could be read.

  The model, per concept, is one-factor factor analysis with a diagonal
  noise (probabilistic PCA with a variance per indicator):

    z_j = l_j f + e_j,  f ~ N(0, 1),  e_j ~ N(0, psi_j)

  z_j is indicator j centred on its median, scaled by 1.4826 MAD, clipped
  to +-4 or to the 1st and 99th percentiles where those lie further out
  (offshore centres and tiny economies do not set the scale, and a skewed
  indicator keeps the order of its upper tail), then
  rescaled to mean 0 and SD 1 over the training sample, so a loading reads
  as a correlation. It is fitted by EM over the country-years with any
  indicators observed: a missing indicator drops out of that row's
  likelihood (missing at random), so a row's posterior uses only what it
  has. An indicator with fewer than :min-obs observations is left out.
  :balanced fits on the country-years holding all of the listed
  indicators only (scoring still uses what a row has).

  With :method :index the concept is not a factor but an equal-weight
  index (fit-index): the same robust z per indicator, times its prior sign
  (:signs), averaged over the indicators a row holds (at least :min-k),
  and scaled to SD 1 over the training rows. One-factor fits on these
  panels tend to a Heywood case (one indicator's noise at the floor, so
  the factor is that indicator); an index cannot.

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
    financialisation-cfa  the NFC payout ratio (8a's prior).
    market-size        market capitalisation / GDP.
    financial-depth    the same.
    investment-regime  net public wealth / national income (8a's prior),
    public-capital     the same.
  An index is signed by its :signs.

  stock-rows writes, dated by the year known:
    econ/<concept>      the posterior mean (units of the factor's SD over
                        the training panel)
    econ/<concept>-sd   its posterior SD (not for an index)
    econ/<concept>-n    the number of indicators it used")

(def nfc-indicators
  "The corporate-behaviour measures of 8a: what non-financial firms do with
  their profit (payouts, including US net buybacks; net lending;
  investment; property income received), each over gross operating
  surplus."
  ["nfc-payout" "nfc-net-lending" "nfc-inv-profit" "nfc-property-income"])

(def fin-indicators
  ["nfc-payout" "log-mcap-gdp" "log-traded-gdp" "log-credit-gdp" "fd-index"
   "nfc-net-lending" "nfc-inv-profit" "nfc-property-income" "fin-va-share" "private-wealth-ni"])

(def concepts
  {;; Krein's mechanism (8a): the NFC measures as an equal-weight index of
   ;; signed, standardised indicators. The one-factor fit on the balanced
   ;; NFC panel (financialisation-cfa) was the first choice; the rule set
   ;; before fitting was to use it if every indicator kept its prior sign
   ;; in every expanding fit from 2000 with no noise at the floor. It did
   ;; not (docs/ECONOMIC-CHARACTER.md, results), so the index is the
   ;; measure. Signs: payouts, net lending and property income up,
   ;; investment over profit down.
   "financialisation"
   {:indicators nfc-indicators :method :index :min-k 2
    :signs {"nfc-payout" 1 "nfc-net-lending" 1 "nfc-inv-profit" -1 "nfc-property-income" 1}}
   "financialisation-cfa"
   {:indicators nfc-indicators :balanced nfc-indicators :anchors ["nfc-payout"]}
   ;; the old econ/financialisation: what is left after the IMF FD index,
   ;; which is stock market size (market cap relative to depth loads 1.0).
   ;; The NFC measures are left out: with them in, and P51G read on total
   ;; fixed assets, the controlled fit swaps to investment over profit (psi
   ;; at the floor) and changes sign between years.
   "market-size"
   {:indicators (vec (remove (set nfc-indicators) fin-indicators)) :control "fd-index" :anchors ["log-mcap-gdp"]}
   ;; the same candidates without the control: depth, for comparison
   "financial-depth"
   {:indicators fin-indicators :anchors ["log-mcap-gdp"]}
   ;; 8a's regime list as far as it is on disk. One ICSD measure, the public
   ;; share of investment: ICSD's capital stocks are its investment flows
   ;; accumulated (perpetual inventory), so the stock and the flow shares
   ;; are one series twice, and as a pair they took the one-factor fit
   ;; (public-capital below, psi at the floor).
   "investment-regime"
   {:indicators ["inv-gdp" "gov-inv-share" "public-wealth-ni" "irr" "hh-cons-share" "current-account"]
    :anchors ["public-wealth-ni"]}
   ;; sensitivity, set before H3 was run on it: an equal-weight index of
   ;; 8a's regime priors (public wealth and investment up, the household
   ;; share down) and the public share of investment
   "investment-regime-index"
   {:indicators ["inv-gdp" "gov-inv-share" "public-wealth-ni" "hh-cons-share"] :method :index :min-k 2
    :signs {"inv-gdp" 1 "gov-inv-share" 1 "public-wealth-ni" 1 "hh-cons-share" -1}}
   ;; the old econ/investment-regime, kept for comparison
   "public-capital"
   {:indicators ["inv-gdp" "public-k-share" "gov-inv-share" "public-wealth-ni" "irr"]
    :anchors ["public-wealth-ni"]}})

(def diagnostics
  "Fits reported for their loadings only (no stocks): every financialisation
  candidate on the balanced panel, the country-years holding all of them
  (the NFC accounts set the panel: about 40 countries from 1995)."
  {"fin-balanced"
   {:indicators fin-indicators :balanced fin-indicators :anchors ["nfc-payout"]}
   "fin-balanced-fd-control"
   {:indicators fin-indicators :balanced (vec (remove #{"fd-index"} fin-indicators))
    :control "fd-index" :anchors ["nfc-payout"]}
   "fin-nfc-panel"
   {:indicators fin-indicators :balanced nfc-indicators :anchors ["nfc-payout"]}})

(def defaults {:min-obs 30 :min-k 1 :stale 5 :clip 4.0 :max-iter 2000 :tol 1e-6 :psi-floor 0.005})

;; ---------------------------------------------------------------- observations

(defn observations
  "{[iso year] {indicator value}} from `rows`."
  [rows]
  (reduce (fn [m {:keys [iso year indicator value]}] (assoc-in m [[iso year] indicator] value)) {} rows))

(defn- median [xs]
  (let [v (vec (sort xs)) n (count v) h (quot n 2)]
    (if (odd? n) (v h) (/ (+ (v (dec h)) (v h)) 2.0))))

(defn- quantile
  "The p-quantile of the sorted vector `v` (linear interpolation)."
  [v p]
  (let [h (* p (dec (count v))) i (int (Math/floor h)) f (- h i)]
    (if (< (inc i) (count v))
      (+ (* (- 1.0 f) (v i)) (* f (v (inc i))))
      (v i))))

(defn- moments
  "{j {:n :mean :sd :lo :hi :c :s}} of each indicator over `obs`. Robust:
  the value is centred on the median and scaled by 1.4826 x the median
  absolute deviation (the SD when the MAD is zero), then clipped to
  [:lo, :hi], so a few wild values (offshore centres, tiny economies) do
  not set the scale. The clip is +-clip robust SDs, widened to the 1st and
  99th percentiles where those lie further out: a skewed indicator (the
  IMF FD index, where the rich economies sit many MADs above the median)
  keeps the order of its upper tail, and only the outer 1% is held. The
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
              :let [raw (vec (sort (map #(/ (- % m) sd) xs)))
                    lo (min (- clip) (quantile raw 0.01))
                    hi (max clip (quantile raw 0.99))
                    zs (map #(max lo (min hi %)) raw)
                    c (/ (reduce + zs) n)
                    s (Math/sqrt (/ (reduce + (map #(let [d (- % c)] (* d d)) zs)) (dec n)))]
              :when (pos? s)]
          [j {:n n :mean m :sd sd :lo lo :hi hi :c c :s s}])))

(defn- standardise [x stats _clip]
  (into {} (for [[j v] x :let [{:keys [mean sd lo hi c s]} (stats j)] :when s]
             [j (/ (- (max lo (min hi (/ (- v mean) sd))) c) s)])))

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

(defn- balance
  "The observations holding every indicator of `required` (all of them
  when `required` is empty)."
  [obs required]
  (if (seq required) (filter (fn [o] (every? #(contains? o %) required)) obs) obs))

(defn fit-index
  "An equal-weight index over `obs` (a seq of {indicator raw-value}): each
  indicator of `signs` ({indicator +1|-1}, the prior direction) is
  standardised robustly as in the factor (moments), multiplied by its sign,
  and a row's index is the mean over the indicators it holds. The index is
  centred and scaled to SD 1 over the training rows that hold at least
  :min-k indicators. An indicator with fewer than :min-obs observations, or
  without a sign, is not used. :balanced restricts the training rows (the
  moments and the scale) to those holding all of its indicators."
  [obs opts]
  (let [{:keys [signs min-obs clip min-k balanced]} (merge defaults opts)
        obs (balance obs balanced)
        stats (into {} (filter #(>= (:n (val %)) min-obs) (moments obs (keys signs) clip)))
        signs (select-keys signs (keys stats))
        raw (fn [x] (let [z (standardise (select-keys x (keys signs)) stats clip)]
                      (when (>= (count z) (max 1 min-k))
                        [(/ (reduce + (for [[j v] z] (* (signs j) v))) (count z)) (count z)])))
        rs (keep #(first (raw %)) obs)
        n (count rs)]
    (when (> n 1)
      (let [m (/ (reduce + rs) n)
            sd (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) rs)) (dec n)))]
        (when (pos? sd)
          {:method :index :signs signs :stats stats :center m :scale sd :n n
           :min-k (max 1 min-k) :balanced balanced})))))

(defn fit-obs
  "The factor fitted on `obs`, a seq of {indicator raw-value} (one per
  country-year). opts: :anchors, :control (an indicator partialled out of
  the others and not itself loaded; rows without it are dropped),
  :balanced (indicators every training row must hold: the fit is on the
  balanced panel, scoring still uses what a row has), :min-obs, :clip,
  :max-iter, :tol, :start (an earlier fit to start from). With :method
  :index it is fit-index instead. Returns {:loading {j l} :psi {j psi}
  :stats {j {:n :mean :sd ...}} :control-coefs :n rows :iter}."
  [obs opts]
  (if (= :index (:method opts))
    (fit-index obs opts)
    (let [{:keys [anchors control min-obs clip max-iter tol start psi-floor balanced]} (merge defaults opts)
          obs (if control (filter #(contains? % control) obs) obs)
          obs (balance obs balanced)
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
                                control (assoc :control control :control-coefs coefs)
                                balanced (assoc :balanced balanced))
                              anchors)
              (recur l' psi' (inc it)))))))))

(defn fit-rows
  "fit-obs over the country-years of long `rows`."
  [rows opts]
  (fit-obs (vals (observations rows)) opts))

(defn fit-indicators
  "The indicators `fit` uses."
  [fit]
  (if (= :index (:method fit)) (keys (:signs fit)) (keys (:loading fit))))

(defn- score-index [{:keys [signs stats center scale min-k]} x]
  (let [z (standardise (select-keys x (keys signs)) stats (:clip defaults))
        k (count z)]
    (when (and (pos? k) (>= k min-k))
      {:f (/ (- (/ (reduce + (for [[j v] z] (* (signs j) v))) k) center) scale) :sd nil :k k})))

(defn score
  "{:f :sd :k} for raw values `x` {indicator value} under `fit`; nil when
  none of x's indicators is in the fit, or the fit has a control x lacks.
  An index (fit-index) has no posterior, so its :sd is nil."
  [{:keys [loading psi stats control control-coefs] :as fit} x]
  (cond
    (nil? fit) nil
    (= :index (:method fit)) (score-index fit x)
    :else
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
                                                     :data-year (apply max (vals (select-keys t (fit-indicators fit))))))
                                   acc)))
                             scores (sort-by key snap))]
          (recur more (or fit start) (cond-> fits fit (assoc y fit)) scores))))))

(defn stock-rows
  "Rows {:iso :year :variable :value} of the scores of `res` (from
  expanding) for `concept`."
  [concept res]
  (->> (:scores res)
       (mapcat (fn [{:keys [iso year f sd k]}]
                 (cond-> [{:iso iso :year year :variable (str "econ/" concept) :value (double f)}
                          {:iso iso :year year :variable (str "econ/" concept "-n") :value (double k)}]
                   sd (conj {:iso iso :year year :variable (str "econ/" concept "-sd") :value (double sd)}))))
       (sort-by (juxt :iso :year :variable))
       vec))
