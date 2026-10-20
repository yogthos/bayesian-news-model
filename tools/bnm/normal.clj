(ns bnm.normal
  "Ordinary moves (docs/TRADING.md, pre-registered): does the model forecast
  the next week's or month's US market return, its realized variance and its
  distribution, in the normal regime and out of sample, and do strategies
  built on those forecasts beat the simple ones after costs and the number
  of strategies tried?

  Each period's forecasts are made at its start from what was known: the
  historical mean, EWMA, GARCH(1,1) (Gaussian and Student t, refit each
  January on the years before) and HAR benchmarks; the model's return
  forecast, the mean of one-input regressions with the sign and
  non-negativity restrictions of Campbell & Thompson (2008), and a joint
  ridge regression; the model's variance, a log HAR with the state inputs;
  and Student t predictives. The regime is the drawdown hazard against the
  80th percentile of its tuning-year values, ex ante.

    jolt -A:tools -M -m bnm.normal [--name normal] [--political r6-mixties] [--confirm true]

  Writes data/runs/<name>/normal.txt and forecasts-<setting>.tsv. The
  locked years (2023 on) are scored only with --confirm true."
  (:require [bnm.fstats :as fs]
            [bnm.hazard :as hz]
            [bnm.math :as m]
            [bnm.trade :as tr]
            [bnm.trader :as us]
            [bnm.vol :as vol]
            [bnm.weekly :as wk]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- forecasts --------------------------------------------------------------------------

(defn regime-cut
  "The hazard above which a period counts as stressed: the 80th percentile of
  its values over the tuning years."
  [ps [lo hi]]
  (let [tuned (vec (sort (keep #(when (<= lo (:year %) hi) (:p %)) ps)))]
    (tuned (long (* 0.8 (count tuned))))))

(defn one-input-forecast
  "A one-input regression's forecast {:a :b} at input x; the historical mean
  hm when x is missing or the slope's sign disagrees with `sign`."
  [{:keys [a b]} sign x hm]
  (if (or (nil? x) (and sign (neg? (* sign b))))
    hm
    (+ a (* b x))))

(defn mean-variance
  "Exposure of a mean-variance investor with risk aversion 3: mean over three
  times variance, held between 0 and 1.5."
  ^double [^double mu ^double v]
  (min 1.5 (max 0.0 (/ mu (* 3.0 v)))))

(defn vol-target
  "Exposure that brings the forecast volatility to `target`, at most 1.5."
  ^double [^double target ^double v]
  (min 1.5 (/ target (Math/sqrt v))))

(defn- ols
  "Least squares of ys on rows xs (double arrays), with a small ridge for
  columns that never vary: the coefficients and the residual variance."
  [xs ys]
  (let [k (alength ^doubles (first xs))
        xtx (double-array (* k k)) xty (double-array k)]
    (doseq [[^doubles x y] (map vector xs ys)]
      (dotimes [i k]
        (aset xty i (+ (aget xty i) (* (aget x i) (double y))))
        (dotimes [j k] (aset xtx (+ (* i k) j) (+ (aget xtx (+ (* i k) j)) (* (aget x i) (aget x j)))))))
    (dotimes [i k] (aset xtx (+ (* i k) i) (+ (aget xtx (+ (* i k) i)) 1e-8)))
    (let [beta (m/solve-spd xtx xty k)
          res (map (fn [^doubles x y] (- (double y) (reduce + (map * beta x)))) xs ys)]
      {:beta (vec beta) :s2 (/ (reduce + (map #(* % %) res)) (max 1 (- (count ys) k)))})))

(defn- ridge
  "Ridge regression of ys on input vectors xs (nil for missing, read as the
  input's mean) with prior precision `lam` on standardized inputs and a free
  intercept: a function from an input vector to the forecast."
  [xs ys lam]
  (let [k (count (first xs))
        n (count ys)
        col (fn [j] (keep #(nth % j) xs))
        mus (mapv #(let [c (col %)] (if (seq c) (/ (reduce + c) (count c)) 0.0)) (range k))
        sds (mapv #(let [c (col %) mu (mus %)]
                     (if (> (count c) 1) (max 1e-12 (Math/sqrt (/ (reduce + (map (fn [v] (* (- v mu) (- v mu))) c)) (count c)))) 1.0))
                  (range k))
        z (fn [x] (double-array (map #(if-let [v (nth x %)] (/ (- v (mus %)) (sds %)) 0.0) (range k))))
        zs (mapv z xs)
        ybar (/ (reduce + ys) n)
        xtx (double-array (* k k)) xty (double-array k)]
    (doseq [[^doubles x y] (map vector zs ys)]
      (dotimes [i k]
        (aset xty i (+ (aget xty i) (* (aget x i) (- y ybar))))
        (dotimes [j k] (aset xtx (+ (* i k) j) (+ (aget xtx (+ (* i k) j)) (* (aget x i) (aget x j)))))))
    (dotimes [i k] (aset xtx (+ (* i k) i) (+ (aget xtx (+ (* i k) i)) lam)))
    (let [beta (m/solve-spd xtx xty k)]
      (fn [x] (+ ybar (reduce + (map * beta (z x))))))))

(defn- log-har-row
  "The model's variance regressors at period s: log realized variance over
  each HAR window, the inputs (0 when missing) and the hazard."
  ^doubles [rvs xs ps s lags]
  (double-array (concat [1.0]
                        (for [w lags] (Math/log (max 1e-12 (/ (reduce + (subvec rvs (- (inc s) w) (inc s))) (double w)))))
                        (map #(or % 0.0) (xs s))
                        [(or (ps s) 0.0)])))

(defn forecasts
  "For each period i, the forecasts for period i + 1 made at its end: :hm
  :combo (restricted) :combo-raw :ridge (returns), :ewma :garch :garch-t
  :har :model-var (variances), :nu (the t's degrees of freedom). Models are
  refit each January on the periods whose next period ended before it;
  nothing is forecast before year `from`."
  [ps {:keys [signs har-lags from]}]
  (let [n (count ps)
        rets (mapv :ret ps) rvs (mapv :rv ps) xs (mapv :x ps) hz (mapv :p ps)
        k (count (first xs))
        hm (vec (map-indexed (fn [i s] (/ s (inc i))) (reductions + rets)))
        ewma (vol/ewma rets 0.94)
        maxlag (apply max har-lags)
        first-of (into (sorted-map) (reverse (map-indexed (fn [i p] [(:year p) i]) ps)))]
    (loop [[[yr start] & more] (seq (subseq first-of >= from)) g nil gt nil out (vec (repeat n nil))]
      (if-not yr
        out
        (let [end (or (second (first more)) n)
              ;; pairs (s, s+1) resolved before the year: s + 1 < start
              fit-s (range 0 (dec start))
              mu (hm (dec start))
              resid (mapv #(- % mu) (subvec rets 0 start))
              g (vol/garch-fit resid (if g {:start g} {}))
              gt (vol/garch-fit resid (if gt {:dist :t :start gt} {:dist :t}))
              one (vec (for [j (range k)]
                         (let [pairs (for [s fit-s :let [x (nth (xs s) j)] :when x] [x (rets (inc s))])]
                           (when (> (count pairs) 30)
                             (let [{[a b] :beta} (ols (map (fn [[x _]] (double-array [1.0 x])) pairs) (map second pairs))]
                               {:a a :b b})))))
              rdg (ridge (mapv xs fit-s) (mapv #(rets (inc %)) fit-s) 100.0)
              har (vol/har-fit (subvec rvs 0 start) {:lags har-lags})
              vs (filter #(>= % (dec maxlag)) fit-s)
              mv (ols (map #(log-har-row rvs xs hz % har-lags) vs) (map #(Math/log (max 1e-12 (rvs (inc %)))) vs))
              ;; GARCH variances through the year, each from the returns before it
              hs (fn [{:keys [omega alpha beta backcast]}]
                   (let [e (mapv #(- % mu) (subvec rets 0 end))]
                     (vec (reductions (fn [h x] (+ omega (* alpha x x) (* beta h))) backcast e))))
              hg (hs g) hgt (hs gt)
              out (reduce (fn [out i]
                            (let [raw (/ (reduce + (map-indexed (fn [j f] (if f (one-input-forecast f nil (nth (xs i) j) (hm i)) (hm i))) one)) k)
                                  combo (/ (reduce + (map-indexed (fn [j f] (if f (one-input-forecast f (nth signs j) (nth (xs i) j) (hm i)) (hm i))) one)) k)]
                              (assoc out i {:hm (hm i) :combo (max 0.0 combo) :combo-raw raw :ridge (rdg (xs i))
                                            :ewma (ewma i) :garch (hg (inc i)) :garch-t (hgt (inc i)) :nu (:nu gt)
                                            :har (max 1e-12 (vol/har-forecast har (subvec rvs 0 (inc i))))
                                            :model-var (Math/exp (+ (reduce + (map * (:beta mv) (log-har-row rvs xs hz i har-lags)))
                                                                    (/ (:s2 mv) 2.0)))})))
                          out (range start end))]
          (recur more g gt out))))))

;; --- data ---------------------------------------------------------------------------------

(def political-kinds ["conflict_onset" "war_onset" "protest_surge" "violence_surge"])

(defn political-risk
  "{q [world usa]}: at each quarter's end, the chance of any political onset
  within four quarters by the onset model's named inputs (window w1), the mean
  over countries and the US's, from an onset run's preds.tsv."
  [run]
  (let [by (with-open [r (io/reader (str "data/runs/" run "/preds.tsv"))]
             (reduce (fn [acc line]
                       (let [[kind win model q unit p] (str/split line #"\t")]
                         (if (and (= win "w1") (= model "named") (some #{kind} political-kinds))
                           (assoc-in acc [(parse-long q) unit kind] (parse-double p))
                           acc)))
                     {} (line-seq r)))
        any (fn [m] (let [ps (map #(get m %) political-kinds)]
                      (when (every? some? ps) (- 1.0 (reduce * (map #(- 1.0 %) ps))))))]
    (into {} (for [[q units] by
                   :let [ps (keep (comp any val) units)]
                   :when (seq ps)]
               [q [(/ (reduce + ps) (count ps)) (any (get units "USA"))]]))))

(defn- quarter-known
  "The last quarter complete by the end of month t."
  ^long [^long t]
  (Math/floorDiv (- t 2) 3))

(def monthly-inputs
  "The trader's valuation, trend and volatility inputs and the slow layer's
  market series, as bnm.trader builds them."
  [:cape :mom12 :sma10 :vol6 :vol-ratio :drawdown12 :cape-x-drawdown :cape-x-decel :cape-x-below-sma
   :vix :nfci :oil12 :us-credit3 :us-house3])

(def return-signs
  "The sign theory gives an input's effect on the next return, where it gives
  one: rich valuations lower it, momentum and a price above its average raise
  it (Campbell & Thompson 2008; Moskowitz, Ooi & Pedersen 2012)."
  {:cape -1 :mom12 1 :sma10 1 :mom52 1 :sma40 1})

(defn- daily-rv
  "{key sum of squared daily market returns} with each day keyed by `key-of`."
  [daily key-of]
  (reduce (fn [acc [d r]] (update acc (key-of d) (fnil + 0.0) (* r r))) {} daily))

(defn- choose-hazard
  "Out-of-sample hazards {period p}, the prior and discount chosen by log
  score on 1950-1989 as the crash tests do; `predict` is (fn [prior discount])."
  [predict year-of]
  (let [grid (for [prior [10.0 100.0] discount [1.0 0.99 0.98]]
               (let [ps (predict prior discount)]
                 {:ps ps :ls (us/log-score (filter #(<= 1950 (year-of (first %)) 1989) ps))}))]
    (into {} (map (juxt first second) (:ps (apply max-key :ls grid))))))

(defn monthly-setting
  "The US monthly periods: {:t :year :ret :rf :rv :x :p}."
  [political]
  (let [{:keys [fr w months raw-inputs]} (us/setup)
        {:keys [start mkt rf]} fr
        pick (us/select-inputs monthly-inputs)
        rec (us/select-inputs us/record-inputs)
        vec1 (fn [xs] (double-array (cons 1.0 (map #(if % (double %) 0.0) xs))))
        rows (vec (for [t months :let [raw (raw-inputs t)]]
                    {:t t :x (vec1 (rec raw)) :y (tr/drawdown-ahead? w (inc (- t start)) 12 0.2)}))
        hazard (choose-hazard #(us/predictions rows 12 %1 %2 1940) #(quot % 12))
        rv (daily-rv (wk/read-daily) #(+ (* 12 (parse-long (subs % 0 4))) (dec (parse-long (subs % 4 6)))))]
    {:name "US monthly" :periods 12 :har-lags [1 3 12] :tune [1950 1989] :from 1950
     :inputs (into monthly-inputs [:hazard :political-world :political-us])
     :ps (vec (for [t months
                    :let [q (quarter-known t) [pw pu] (get political q)]]
                {:t t :year (quot t 12) :ret (- (mkt (- t start)) (rf (- t start))) :rf (rf (- t start))
                 :rv (get rv t 0.0) :p (hazard t)
                 :x (conj (pick (raw-inputs t)) (hazard t) pw pu)}))}))

(defn weekly-setting
  "The US weekly periods: {:t :year :ret :rf :rv :x :p}."
  [political]
  (let [daily (wk/read-daily)
        weeks (wk/weeks daily)
        n (count weeks)
        w (tr/wealth (map :mkt weeks))
        offsets (vec (reductions + 0 (map #(count (:days %)) weeks)))
        all-days (mapv second daily)
        daily-before (fn [k] (subvec all-days (max 0 (- (offsets (inc k)) 260)) (offsets (inc k))))
        pe10 (us/read-cape)
        year-of (fn [k] (quot (:month (weeks k)) 12))
        raw (mapv #(wk/inputs weeks w daily-before pe10 %) (range n))
        rows (vec (for [k (range n) :let [x (raw k)] :when x]
                    {:k k :x (double-array (cons 1.0 (map #(if % (double %) 0.0) x)))
                     :y (tr/drawdown-ahead? w (inc k) 52 0.2)}))
        hazard (choose-hazard #(#'wk/predictions rows year-of 52 %1 %2 1935) year-of)]
    {:name "US weekly" :periods 52 :har-lags [1 4 13] :tune [1950 1989] :from 1950
     :inputs (into wk/names [:hazard :political-world :political-us])
     :ps (vec (for [k (range n) :when (raw k)
                    :let [{:keys [month mkt rf days]} (weeks k)
                          [pw pu] (get political (quarter-known month))]]
                {:t k :year (year-of k) :ret (- mkt rf) :rf rf :rv (reduce + (map #(* % %) days))
                 :p (hazard k) :x (conj (raw k) (hazard k) pw pu)}))}))

;; --- scores ---------------------------------------------------------------------------------

(def eras [["1990-2007" 1990 2007] ["2008-2022" 2008 2022]])
(def locked ["2023-" 2023 2100])
(def alpha-forecast (/ 0.05 18))
(def trials 24)

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))

(defn- t-scale ^double [^double v ^double nu] (Math/sqrt (/ (* v (- nu 2.0)) nu)))

(defn- log-normal-density ^double [^double mu ^double v ^double x]
  (- (* -0.5 (Math/log (* 2.0 Math/PI v))) (/ (* (- x mu) (- x mu)) (* 2.0 v))))

(defn score-forecasts
  "The forecast tests over periods `idx` (each i forecasts i + 1)."
  [ps fc idx]
  (let [y (mapv #(:ret (ps (inc %))) idx)
        rv (mapv #(:rv (ps (inc %))) idx)
        f (mapv fc idx)
        col (fn [k] (mapv k f))
        lags (fs/nw-lags (count idx))
        hm (col :hm)
        q (fn [k] (mapv #(fs/qlike (max 1e-12 %1) %2) rv (col k)))
        nu (col :nu)
        mu-m (col :combo) vm (col :model-var)
        sm (mapv t-scale vm nu) sb (mapv t-scale (col :garch-t) nu)
        crps-m (mapv #(fs/crps-t %1 %2 %3 %4) mu-m sm nu y)
        crps-b (mapv #(fs/crps-t %1 %2 %3 %4) hm sb nu y)
        pit (mapv #(fs/t-cdf (/ (- %4 %1) %2) %3) mu-m sm nu y)
        miss (fn [a] (mapv #(if (or (< % (/ a 2)) (> % (- 1 (/ a 2)))) 1 0) pit))
        ;; mean-variance investors, the model's mean against the historical
        ;; mean, both with the GARCH variance, net of 10bp per unit turned over
        ce (fn [mus] (let [ws (mapv #(mean-variance %1 %2) mus (col :garch))
                           rs (mapv (fn [w y w0] (- (* w y) (* 0.001 (Math/abs (- w w0))))) ws y (cons (first ws) ws))
                           m (mean rs)]
                       (- m (* 1.5 (mean (map #(* (- % m) (- % m)) rs))))))]
    {:n (count idx)
     :r2 (fs/oos-r2 y (col :combo) hm) :r2-ridge (fs/oos-r2 y (col :ridge) hm)
     :cw (fs/clark-west y (col :combo) hm lags) :cw-ridge (fs/clark-west y (col :ridge) hm lags)
     :pt (fs/pesaran-timmermann y (col :combo-raw)) :pt-ridge (fs/pesaran-timmermann y (col :ridge))
     :qlike (into {} (for [k [:model-var :garch :har :ewma]] [k (mean (q k))]))
     :dm-garch (fs/diebold-mariano (q :model-var) (q :garch) lags)
     :dm-har (fs/diebold-mariano (q :model-var) (q :har) lags)
     :crps {:model (mean crps-m) :garch-t (mean crps-b)
            :gauss (mean (map #(fs/crps-normal %1 (Math/sqrt %2) %3) hm (col :garch) y))}
     :dm-crps (fs/diebold-mariano crps-m crps-b lags)
     :log {:model (mean (map #(fs/log-t %1 %2 %3 %4) mu-m sm nu y))
           :garch-t (mean (map #(fs/log-t %1 %2 %3 %4) hm sb nu y))
           :gauss (mean (map #(log-normal-density %1 %2 %3) hm (col :garch) y))}
     :cover (into {} (for [a [0.2 0.05] :let [ms (miss a)]]
                       [a {:rate (/ (reduce + ms) (double (count ms)))
                           :kupiec (fs/kupiec (reduce + ms) (count ms) a) :chr (fs/christoffersen ms)}]))
     :berkowitz (fs/berkowitz pit)
     :ce-gain (- (ce (col :combo)) (ce hm))}))

(defn- strategy-returns
  "Per-period excess returns, net of 10bp per unit of exposure changed, of
  exposures ex (ex[j] decided at idx[j], earning period idx[j] + 1)."
  [ps idx ex]
  (mapv (fn [i e e0] (- (* e (:ret (ps (inc i)))) (* 0.001 (Math/abs (- e e0)))))
        idx ex (cons (first ex) ex)))

(defn strategies
  "{name exposure-fn} for the fixed strategies and the benchmarks; each fn
  takes period i and gives the exposure for i + 1."
  [{:keys [ps tune periods]} fc]
  (let [[lo hi] tune
        tuning (filter #(<= lo (:year (ps (inc %))) hi) (range (dec (count ps))))
        target (let [rs (map #(:ret (ps (inc %))) tuning) m (mean rs)]
                 (Math/sqrt (mean (map #(* (- % m) (- % m)) rs))))
        ;; Moreira & Muir: scaled so the tuning years' volatility is buy and hold's
        mm-c (let [scaled (map #(/ (:ret (ps (inc %))) (max 1e-12 (:rv (ps %)))) tuning)
                   m (mean scaled)]
               (/ target (Math/sqrt (mean (map #(* (- % m) (- % m)) scaled)))))
        n-avg (if (= periods 52) 40 10)
        wealth (tr/wealth (map #(+ (:ret %) (:rf %)) ps))
        above-avg (fn [i] (if (and (>= i n-avg)
                                   (>= (wealth (inc i)) (/ (reduce + (subvec wealth (- (inc i) (dec n-avg)) (+ 2 i))) n-avg)))
                            1.0 0.0))
        f #(fc %)]
    {"1 sign" #(if (pos? (:combo-raw (f %))) 1.0 0.0)
     "2 mean-variance, GARCH" #(mean-variance (:combo (f %)) (:garch (f %)))
     "3 volatility target" #(vol-target target (:model-var (f %)))
     "4 mean-variance, model" #(mean-variance (:combo (f %)) (:model-var (f %)))
     "1r sign, ridge" #(if (pos? (:ridge (f %))) 1.0 0.0)
     "2r mean-variance, GARCH, ridge" #(mean-variance (max 0.0 (:ridge (f %))) (:garch (f %)))
     "4r mean-variance, model, ridge" #(mean-variance (max 0.0 (:ridge (f %))) (:model-var (f %)))
     :bench {"buy and hold" (constantly 1.0)
             (str n-avg "-period average") above-avg
             "volatility managed" #(min 1.5 (/ mm-c (max 1e-12 (:rv (ps %)))))
             "mean-variance, historical mean" #(mean-variance (:hm (f %)) (:garch (f %)))}}))

(defn- annual-sharpe [rs periods]
  (let [{:keys [sr]} (fs/sharpe-stats rs)] (* sr (Math/sqrt periods))))

(defn- utility [rs] (mapv #(- % (* 1.5 % %)) rs))

;; --- report -----------------------------------------------------------------------------------

(defn- pv [{:keys [stat p]}] (format "%6.2f (p %.4f)" (double stat) (double p)))

(defn- era-idx
  "Periods i whose next period falls in [lo hi], in `regime` (:all :normal
  :stressed) by the hazard at i against the cut."
  [ps fc cut lo hi regime]
  (filter #(and (fc %) (<= lo (:year (ps (inc %))) hi)
                (case regime
                  :all true
                  :normal (some-> (:p (ps %)) (< cut))
                  :stressed (some-> (:p (ps %)) (>= cut))))
          (range (dec (count ps)))))

(defn run [{:keys [name political confirm]}]
  (let [run-name (or name "normal")
        dir (str "data/runs/" run-name)
        _ (io/make-parents (str dir "/x"))
        pol (political-risk (or political "r6-mixties"))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        eras (cond-> eras (= confirm "true") (conj locked))
        settings (for [mk [monthly-setting weekly-setting]] (mk pol))
        results
        (vec (for [{:keys [name ps inputs periods tune] :as s} settings]
               (let [fc (forecasts ps (assoc s :signs (mapv return-signs inputs)))
                     cut (regime-cut ps tune)
                     strats (strategies s fc)]
                 (with-open [w (io/writer (str dir "/forecasts-" (str/replace (str/lower-case name) " " "-") ".tsv"))]
                   (.write w "t\tyear\tret\trv\tp\thm\tcombo\tcombo_raw\tridge\tewma\tgarch\tgarch_t\thar\tmodel_var\tnu\n")
                   (doseq [i (range (count ps)) :let [p (ps i) f (fc i)] :when f]
                     (.write w (str/join "\t" (concat [(:t p) (:year p) (:ret p) (:rv p) (:p p)]
                                                      (map f [:hm :combo :combo-raw :ridge :ewma :garch :garch-t :har :model-var :nu]))))
                     (.write w "\n")))
                 {:setting s :fc fc :cut cut :strats strats})))]
    (say "Ordinary moves: out-of-sample forecasts of the next period's US market excess return, realized")
    (say "variance and distribution, and strategies built on them (docs/TRADING.md, pre-registered).")
    (say (format "Forecast tests significant at %.4f (0.05 over 18 tests); strategies deflated for %d trials." alpha-forecast trials))
    (doseq [{{:keys [name ps periods]} :setting :keys [fc cut]} results]
      (say)
      (say (format "%s: stressed when the drawdown hazard is at or above %.3f (80th percentile, 1950-1989)" name cut))
      (doseq [[era lo hi] eras
              regime [:normal :stressed :all]
              :let [idx (vec (era-idx ps fc cut lo hi regime))]
              :when (> (count idx) 20)]
        (let [{:keys [n r2 r2-ridge cw cw-ridge pt pt-ridge qlike dm-garch dm-har crps dm-crps log cover berkowitz ce-gain]}
              (score-forecasts ps fc idx)]
          (say (format "  %s %-8s n=%d" era (clojure.core/name regime) n))
          (say (format "    returns   R2 %+.4f  CW %s | ridge R2 %+.4f  CW %s" r2 (pv cw) r2-ridge (pv cw-ridge)))
          (say (format "    signs     hit %.3f  PT %s | ridge hit %.3f  PT %s" (:hit pt) (pv pt) (:hit pt-ridge) (pv pt-ridge)))
          (say (format "    variance  QLIKE model %.4f  GARCH %.4f  HAR %.4f  EWMA %.4f | DM vs GARCH %s  vs HAR %s"
                       (:model-var qlike) (:garch qlike) (:har qlike) (:ewma qlike) (pv dm-garch) (pv dm-har)))
          (say (format "    dist      CRPS model %.5f  GARCH-t %.5f  Gaussian %.5f  DM %s | log %.3f %.3f %.3f"
                       (:model crps) (:garch-t crps) (:gauss crps) (pv dm-crps) (:model log) (:garch-t log) (:gauss log)))
          (say (format "    coverage  80%%: %.3f missed, Kupiec p %.3f, Christoffersen p %.3f | 95%%: %.3f, p %.3f, %.3f | Berkowitz p %.4f"
                       (get-in cover [0.2 :rate]) (get-in cover [0.2 :kupiec :p]) (get-in cover [0.2 :chr :p])
                       (get-in cover [0.05 :rate]) (get-in cover [0.05 :kupiec :p]) (get-in cover [0.05 :chr :p]) (:p berkowitz)))
          (say (format "    value     certainty-equivalent gain over the historical mean %+.2f%% a year" (* 100 ce-gain periods))))))
    ;; strategies: every series of every setting and era enters the spread of Sharpe ratios
    (doseq [[era lo hi] eras]
      (let [rows (vec (for [{{:keys [name ps periods]} :setting :keys [fc cut strats]} results
                            :let [idx (vec (era-idx ps fc cut lo hi :all))
                                  normal (set (era-idx ps fc cut lo hi :normal))]
                            [sname ex-fn] (concat (dissoc strats :bench) (map (fn [[k v]] [(str "bench: " k) v]) (:bench strats)))]
                        (let [rs (strategy-returns ps idx (mapv ex-fn idx))]
                          {:setting name :periods periods :name sname :rs rs :idx idx :normal normal
                           :bench? (str/starts-with? sname "bench: ")
                           :sr (annual-sharpe rs periods)
                           :bt (tr/backtest (mapv #(+ (:ret (ps (inc %))) (:rf (ps (inc %)))) idx)
                                            (mapv #(:rf (ps (inc %))) idx) (mapv ex-fn idx) {:cost 0.001 :periods periods})})))
            trial-rows (remove :bench? rows)
            var-sr (let [s (map #(/ (:sr %) (Math/sqrt (:periods %))) trial-rows) m (mean s)]
                     (mean (map #(* (- % m) (- % m)) s)))]
        (say)
        (say (format "Strategies %s (excess Sharpe, net of 10bp; DSR against %d trials; SPA on quadratic utility):" era trials))
        (doseq [[setting group] (group-by :setting rows)]
          (let [benches (filter :bench? group)
                best (apply max-key :sr benches)
                spa-p (apply max (for [b benches]
                                   (:p (fs/spa (for [r (remove :bench? group)] (mapv - (utility (:rs r)) (utility (:rs b))))
                                               {:block (if (= 52 (:periods b)) 10 6) :reps 999 :seed 7}))))]
            (say (format "  %s: SPA p %.3f against the benchmarks (largest over the four)" setting spa-p))
            (doseq [{:keys [name sr bt rs idx normal bench? periods]} group]
              (let [gain-normal (mean (keep identity (map (fn [i a b] (when (normal i) (- a b))) idx rs (:rs best))))]
                (say (format "    %-34s sharpe %5.2f  annual %6.2f%%  maxdd %5.1f%%  invested %3.0f%%%s"
                             name sr (* 100 (:annual bt)) (* 100 (:max-drawdown bt)) (* 100 (:invested bt))
                             (if bench? ""
                                 (format "  DSR %.3f  vs best benchmark in normal periods %+.2f%% a year"
                                         (fs/deflated-sharpe rs trials var-sr) (* 100 gain-normal periods)))))))))))
    (spit (str dir "/normal.txt") (str out))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
