(ns bnm.control
  "The control layer (bead bnm-4hv.2; docs/COUPLED-SYSTEMS.md 4a, C): who
  controls finance and production, as a latent control vector per
  country-year, learned from measured stocks with sign priors.

  Input: scripts/control.py's long table (data/derived/control/
  control-indicators.csv), rows {:iso :year :known :indicator :value}, :year
  the data year and :known the first year the value could be read.

  The model is K-factor analysis (K = 1 or 2) with a diagonal noise:

    z_j = l_j . f + e_j,   f ~ N(0, I_K),   e_j ~ N(0, psi_j)

  z_j is indicator j standardised robustly as in bnm.econ-factors (median,
  1.4826 MAD, clipped, then mean 0 and SD 1 over the training rows), so a
  loading reads as a correlation. Missing indicators drop out of a row's
  likelihood (missing at random): the E-step uses what a row has.

  Sign priors (principle 4; doc 4a and 5.1). Each axis states the
  direction of its indicators before any fit:
    l_jk ~ N(0, tau^2) truncated to l_jk >= 0 (sign +1) or <= 0 (sign -1);
    free (N(0, tau^2)) where the axis states nothing;
    exactly 0 for the axis's :zero indicators.
  tau = 1 is weak against n observations: the prior acts at the bound. The
  M-step is the MAP under these priors (coordinate descent with projection,
  per indicator), so a loading the data push past its sign stays at zero,
  and `pinned` reports it. With K = 2 the zero of the first axis's anchor
  on the second, and the signs, identify the rotation; no post-hoc sign
  flip is needed, and none is made.

  Axes (`spec-1`, `spec-2`):
    state    state against private control of capital and credit:
             + state bank share, state ownership (V-Dem v2clstown negated),
               public capital share (ICSD), net public wealth, expropriation
               acts, OECD PMR public ownership; - capital-account openness.
             Capture and wealth concentration are free on it.
    capture  the second axis tried: + political corruption (v2x_corr) and
             top 1% wealth share; state-own fixed at 0 on it, the rest free.

  The second axis is kept only if it is stable, by a rule fixed before it
  was fitted (`stability`): Tucker congruence of its loadings of at least
  0.90 between every expanding fit from 1995 on and the last one, and
  between fits on two halves of the countries. Otherwise the stocks carry
  the one-axis fit (spec-1, the state indicators only).

  Causality: an expanding window, as bnm.econ-factors. The fit for year Y
  uses the country-year rows whose :known is at most Y, with medians and
  scales from those rows; each year's EM starts from the previous year's
  fit. A country's score at Y is the posterior mean given its latest value
  of each indicator known by Y and at most :stale data years old.

  stock-rows writes, dated by the year known, with the latest data year
  used:
    control/<axis>      the posterior mean (factor SD units over the panel)
    control/<axis>-sd   its posterior SD
    control/n           the number of indicators used
  indicator-rows writes control/<indicator>, the raw indicator at its year
  known."
  (:require [bnm.econ-factors :as ef]))

;; the robust standardisation and the latest-known snapshot are
;; bnm.econ-factors' own (private there), reused so both layers scale alike
(def ^:private moments #'ef/moments)
(def ^:private standardise #'ef/standardise)
(def ^:private snapshot #'ef/snapshot)

(def state-signs
  {"state-bank-share" 1 "state-own" 1 "public-k-share" 1 "public-wealth-ni" 1
   "exprop-10y" 1 "pmr-public-owner" 1 "ka-open" -1})

(def spec-1
  {:indicators (vec (sort (keys state-signs)))
   :axes [{:name "state" :signs state-signs :min-k 2}]})

(def spec-2
  {:indicators (vec (sort (concat (keys state-signs) ["corruption" "top1-wealth"])))
   :axes [{:name "state" :signs state-signs :min-k 2}
          {:name "capture" :signs {"corruption" 1 "top1-wealth" 1} :zero #{"state-own"} :min-k 1}]})

(def indicator-stocks
  "The indicators written as control/<indicator> stocks."
  #{"state-bank-share" "bank-brss" "state-own" "public-k-share" "public-wealth-ni" "ka-open"
    "ka-restrict" "exprop-10y" "pmr-public-owner" "top1-wealth" "corruption" "execorr" "pubcorr"})

(def defaults {:min-obs 30 :stale 5 :clip 4.0 :max-iter 5000 :tol 1e-5 :psi-floor 0.005 :tau 1.0})

;; ---------------------------------------------------------------- small linear algebra

(defn- inverse
  "The inverse of the k x k matrix `a` (row-major double-array), by
  Gauss-Jordan with partial pivoting. `a` is positive definite here."
  ^doubles [^doubles a k]
  (let [m (double-array (* k 2 k))
        w (* 2 k)]
    (dotimes [i k]
      (dotimes [j k] (aset m (+ (* i w) j) (aget a (+ (* i k) j))))
      (aset m (+ (* i w) k i) 1.0))
    (dotimes [c k]
      (let [p (apply max-key #(Math/abs (aget m (+ (* % w) c))) (range c k))]
        (when (not= p c)
          (dotimes [j w]
            (let [t (aget m (+ (* c w) j))]
              (aset m (+ (* c w) j) (aget m (+ (* p w) j)))
              (aset m (+ (* p w) j) t))))
        (let [d (aget m (+ (* c w) c))]
          (dotimes [j w] (aset m (+ (* c w) j) (/ (aget m (+ (* c w) j)) d))))
        (dotimes [i k]
          (when (not= i c)
            (let [f (aget m (+ (* i w) c))]
              (when-not (zero? f)
                (dotimes [j w]
                  (aset m (+ (* i w) j) (- (aget m (+ (* i w) j)) (* f (aget m (+ (* c w) j))))))))))))
    (let [out (double-array (* k k))]
      (dotimes [i k] (dotimes [j k] (aset out (+ (* i k) j) (aget m (+ (* i w) k j)))))
      out)))

(defn congruence
  "Tucker's congruence coefficient of two loading vectors."
  [a b]
  (let [ab (reduce + (map * a b)) aa (reduce + (map * a a)) bb (reduce + (map * b b))]
    (if (and (pos? aa) (pos? bb)) (/ ab (Math/sqrt (* aa bb))) 0.0)))

;; ---------------------------------------------------------------- the model
;; Indicators are positions 0..m-1; loadings a flat double-array L[j*K+k];
;; constraint codes C[j*K+k]: 1 (>= 0), -1 (<= 0), 0 (free), 9 (fixed 0).

(defn- codes [spec inds]
  (let [axes (:axes spec) k (count axes) c (int-array (* (count inds) k))]
    (doseq [[j ind] (map-indexed vector inds) [a {:keys [signs zero]}] (map-indexed vector axes)]
      (aset c (+ (* j k) a) (int (cond (contains? zero ind) 9 (signs ind) (signs ind) :else 0))))
    c))

(defn- posterior
  "[mu Sigma n] of f for packed row [idx xs] under loadings `l` and noise
  `psi`; nil when the row has no indicator."
  [[^ints idx ^doubles xs] ^doubles l ^doubles psi k]
  (let [n (alength idx)]
    (when (pos? n)
      (let [mm (double-array (* k k)) h (double-array k)]
        (dotimes [a k] (aset mm (+ (* a k) a) 1.0))
        (dotimes [i n]
          (let [j (aget idx i) p (aget psi j) x (aget xs i) o (* j k)]
            (dotimes [a k]
              (let [la (aget l (+ o a))]
                (aset h a (+ (aget h a) (/ (* la x) p)))
                (dotimes [b k]
                  (aset mm (+ (* a k) b) (+ (aget mm (+ (* a k) b)) (/ (* la (aget l (+ o b))) p))))))))
        (let [s (inverse mm k) mu (double-array k)]
          (dotimes [a k]
            (aset mu a (loop [b 0 t 0.0] (if (< b k) (recur (inc b) (+ t (* (aget s (+ (* a k) b)) (aget h b)))) t))))
          [mu s n])))))

(defn- det
  "The determinant of the k x k matrix `a` (row-major), by elimination."
  [^doubles a k]
  (let [m (aclone a)]
    (loop [c 0 d 1.0]
      (if (= c k)
        d
        (let [p (apply max-key #(Math/abs (aget m (+ (* % k) c))) (range c k))
              d (if (not= p c)
                  (do (dotimes [j k]
                        (let [t (aget m (+ (* c k) j))]
                          (aset m (+ (* c k) j) (aget m (+ (* p k) j)))
                          (aset m (+ (* p k) j) t)))
                      (- d))
                  d)
              piv (aget m (+ (* c k) c))]
          (doseq [i (range (inc c) k)]
            (let [f (/ (aget m (+ (* i k) c)) piv)]
              (dotimes [j k] (aset m (+ (* i k) j) (- (aget m (+ (* i k) j)) (* f (aget m (+ (* c k) j))))))))
          (recur (inc c) (* d piv)))))))

(defn- row-loglik
  "log N(x_O; 0, L_O L_O' + Psi_O) of packed row [idx xs], by Woodbury:
  log|Sigma| = log|M| + sum log psi, x'Sigma^-1 x = sum x^2/psi - h'M^-1 h,
  M = I + L'Psi^-1 L, h = L'Psi^-1 x."
  [[^ints idx ^doubles xs] ^doubles l ^doubles psi k]
  (let [n (alength idx) mm (double-array (* k k)) h (double-array k)]
    (dotimes [a k] (aset mm (+ (* a k) a) 1.0))
    (let [[lp q] (loop [i 0 lp 0.0 q 0.0]
                   (if (< i n)
                     (let [j (aget idx i) p (aget psi j) x (aget xs i) o (* j k)]
                       (dotimes [a k]
                         (let [la (aget l (+ o a))]
                           (aset h a (+ (aget h a) (/ (* la x) p)))
                           (dotimes [b k]
                             (aset mm (+ (* a k) b) (+ (aget mm (+ (* a k) b)) (/ (* la (aget l (+ o b))) p))))))
                       (recur (inc i) (+ lp (Math/log p)) (+ q (/ (* x x) p))))
                     [lp q]))
          s (inverse mm k)
          hsh (loop [a 0 t 0.0]
                (if (< a k)
                  (recur (inc a) (+ t (loop [b 0 u 0.0] (if (< b k) (recur (inc b) (+ u (* (aget h a) (aget s (+ (* a k) b)) (aget h b)))) u))))
                  t))]
      (* -0.5 (+ (* n (Math/log (* 2.0 Math/PI))) (Math/log (det mm k)) lp (- q hsh))))))

(defn- m-step-row
  "The MAP loading row of indicator j: minimise
  (l'Al - 2b'l) / (2 psi) + |l|^2 / (2 tau^2) under its constraint codes,
  by coordinate descent with projection (exact for a convex quadratic on a
  box)."
  [^doubles a ^doubles b ^ints c ^doubles l0 j k psi tau2]
  (let [l (double-array k) o (* j k)]
    (dotimes [q k] (aset l q (aget l0 (+ o q))))
    (loop [sweep 0]
      (let [d (loop [q 0 d 0.0]
                (if (< q k)
                  (let [code (aget c (+ o q))
                        old (aget l q)
                        new (if (= 9 code)
                              0.0
                              (let [cross (loop [r 0 t 0.0]
                                            (if (< r k)
                                              (recur (inc r) (if (= r q) t (+ t (* (aget a (+ (* o k) (* q k) r)) (aget l r)))))
                                              t))
                                    v (/ (- (/ (aget b (+ o q)) psi) (/ cross psi))
                                         (+ (/ (aget a (+ (* o k) (* q k) q)) psi) (/ 1.0 tau2)))]
                                (case (int code) 1 (max 0.0 v) -1 (min 0.0 v) v)))]
                    (aset l q new)
                    (recur (inc q) (max d (Math/abs (- new old)))))
                  d))]
        (when (and (> d 1e-12) (< sweep 100)) (recur (inc sweep)))))
    l))

(defn- em-step
  "One EM step over packed `rows`: new [l psi]."
  [rows m k ^doubles l ^doubles psi ^ints c psi-floor tau2]
  (let [aa (double-array (* m k k)) bb (double-array (* m k)) xx (double-array m) cnt (double-array m)]
    (doseq [[^ints idx ^doubles xs :as r] rows]
      (when-let [[^doubles mu ^doubles s _] (posterior r l psi k)]
        (dotimes [i (alength idx)]
          (let [j (aget idx i) x (aget xs i)]
            (aset xx j (+ (aget xx j) (* x x)))
            (aset cnt j (+ (aget cnt j) 1.0))
            (dotimes [a k]
              (aset bb (+ (* j k) a) (+ (aget bb (+ (* j k) a)) (* x (aget mu a))))
              (dotimes [b k]
                (let [p (+ (* j k k) (* a k) b)]
                  (aset aa p (+ (aget aa p) (aget s (+ (* a k) b)) (* (aget mu a) (aget mu b)))))))))))
    (let [l' (double-array (* m k)) psi' (double-array m)]
      (dotimes [j m]
        (if (pos? (aget cnt j))
          (let [row (m-step-row aa bb c l j k (aget psi j) tau2)
                o (* j k)
                quad (loop [a 0 t 0.0]
                       (if (< a k)
                         (recur (inc a) (+ t (loop [b 0 u 0.0]
                                               (if (< b k)
                                                 (recur (inc b) (+ u (* (aget row a) (aget aa (+ (* j k k) (* a k) b)) (aget row b))))
                                                 u))))
                         t))
                lin (loop [a 0 t 0.0] (if (< a k) (recur (inc a) (+ t (* (aget row a) (aget bb (+ o a))))) t))]
            (dotimes [a k] (aset l' (+ o a) (aget row a)))
            (aset psi' j (max psi-floor (/ (+ (aget xx j) (* -2.0 lin) quad) (aget cnt j)))))
          (do (dotimes [a k] (aset l' (+ (* j k) a) (aget l (+ (* j k) a))))
              (aset psi' j (aget psi j)))))
      [l' psi'])))

(defn- pack [z pos]
  (let [ks (vec (filter pos (keys z)))]
    [(int-array (map pos ks)) (double-array (map z ks))]))

(defn- em
  "EM from loadings `l0` and noise `p0` to convergence: [l psi iter converged]."
  [rows m k l0 p0 c {:keys [max-iter tol psi-floor tau]}]
  (loop [^doubles l l0 ^doubles psi p0 it 0]
    (let [[^doubles l' psi'] (em-step rows m k l psi c psi-floor (* tau tau))
          d (loop [i 0 d 0.0] (if (< i (* m k)) (recur (inc i) (max d (Math/abs (- (aget l' i) (aget l i))))) d))]
      (if (or (< d tol) (>= (inc it) max-iter))
        [l' psi' (inc it) (< d tol)]
        (recur l' psi' (inc it))))))

(defn- objective
  "The log posterior of loadings `l` and noise `psi` on packed `rows`: the
  marginal likelihood plus the loadings' log prior (to a constant)."
  [rows ^doubles l psi k tau]
  (+ (reduce + (map #(row-loglik % l psi k) rows))
     (/ (- (areduce l i t 0.0 (+ t (* (aget l i) (aget l i))))) (* 2.0 tau tau))))

(defn fit-obs
  "The K-axis fit on `obs`, a seq of {indicator raw-value} (one per
  country-year), for `spec` ({:indicators :axes}). opts: :min-obs, :clip,
  :max-iter, :tol, :tau, :psi-floor, :start (an earlier fit).

  EM is run from two starts: the prior's (each signed loading at +-0.5,
  free ones at 0.1) and :start when given (last year's fit). The one with
  the higher log posterior is kept, so the fit does not depend on the path
  of earlier fits when the likelihood has more than one mode. Returns
  {:loading {j [l_1 .. l_K]} :psi {j psi} :stats :n rows :iter :converged
  :objective :objectives {start value} :start start-kept :axes names}."
  ([obs spec] (fit-obs obs spec {}))
  ([obs spec opts]
   (let [{:keys [min-obs clip start tau] :as opts} (merge defaults opts)
         axes (:axes spec) k (count axes)
         stats (into {} (filter #(>= (:n (val %)) min-obs) (moments obs (:indicators spec) clip)))
         inds (vec (filter stats (:indicators spec)))
         pos (zipmap inds (range))
         m (count inds)
         rows (->> obs (map #(standardise (select-keys % inds) stats clip)) (filter seq) (mapv #(pack % pos)))
         c (codes spec inds)
         init (fn [j a] (let [code (aget c (+ (* j k) a))]
                          (case (int code) 9 0.0 1 0.5 -1 -0.5 0.1)))
         prior-start [(double-array (for [j (range m) a (range k)] (init j a)))
                      (double-array (repeat m 0.5))]
         warm-start (when start
                      [(double-array (for [[j ind] (map-indexed vector inds) a (range k)]
                                       (if-let [s (get-in start [:loading ind])] (double (nth s a)) (init j a))))
                       (double-array (for [ind inds] (or (get-in start [:psi ind]) 0.5)))])]
     (when (and (pos? m) (seq rows))
       (let [runs (for [[nm [l0 p0]] (cond-> [[:prior prior-start]] warm-start (conj [:warm warm-start]))
                        :let [[l psi it conv] (em rows m k l0 p0 c opts)]]
                    {:start nm :l l :psi psi :iter it :converged conv :objective (objective rows l psi k tau)})
             best (apply max-key :objective (reverse runs))
             ^doubles l' (:l best) ^doubles psi' (:psi best)]
         {:loading (into {} (for [[ind j] pos] [ind (mapv #(aget l' (+ (* j k) %)) (range k))]))
          :psi (into {} (for [[ind j] pos] [ind (aget psi' j)]))
          :stats stats :n (count rows) :iter (:iter best) :converged (:converged best)
          :objective (:objective best) :start (:start best)
          :objectives (into {} (map (juxt :start :objective) runs))
          :axes (mapv :name axes)})))))

(defn fit-rows
  "fit-obs over the country-years of long `rows`."
  ([rows spec] (fit-rows rows spec {}))
  ([rows spec opts] (fit-obs (vals (ef/observations rows)) spec opts)))

(defn pinned
  "[indicator axis] pairs whose signed loading sits at its bound: the data
  pushed it past its prior sign."
  [fit spec]
  (for [[a {:keys [name signs]}] (map-indexed vector (:axes spec))
        [ind s] (sort signs)
        :let [l (get-in fit [:loading ind a])]
        :when (and l (< (Math/abs (double l)) 1e-9))]
    [ind name]))

(defn score
  "{:f [..] :sd [..] :k :ok [..]} for raw values `x` {indicator value}
  under `fit`: the posterior mean and SD per axis, the indicators used, and
  per axis whether x holds the axis's :min-k signed indicators. nil when
  none of x's indicators is in the fit."
  ([fit x] (score fit x nil))
  ([fit x spec]
   (when fit
     (let [{:keys [loading psi stats]} fit
           inds (vec (keys loading))
           k (count (val (first loading)))
           pos (zipmap inds (range))
           z (standardise (select-keys x inds) stats (:clip defaults))]
       (when (seq z)
         (when-let [[^doubles mu ^doubles s n]
                    (posterior (pack z pos) (double-array (mapcat loading inds)) (double-array (map psi inds)) k)]
           {:f (vec mu)
            :sd (mapv #(Math/sqrt (aget s (+ (* % k) %))) (range k))
            :k n
            :ok (if spec
                  (mapv (fn [{:keys [signs min-k]}] (>= (count (filter #(and (signs %) (z %)) (keys z))) (or min-k 1)))
                        (:axes spec))
                  (vec (repeat k true)))}))))))

;; ---------------------------------------------------------------- expanding window

(defn expanding
  "Fits for each year of `years` (ascending) on the rows known by then, and
  each country's scores at each year. Returns {:fits {y fit} :scores [{:iso
  :year :f :sd :k :ok :data-year}]}."
  ([rows years spec] (expanding rows years spec {}))
  ([rows years spec opts]
   (let [{:keys [stale] :as opts} (merge defaults opts)
         inds (set (:indicators spec))
         by-known (sort-by :known (filter #(inds (:indicator %)) rows))]
     (loop [[y & more] years start nil fits {} scores (transient [])]
       (if-not y
         {:fits fits :scores (persistent! scores)}
         (let [known (take-while #(<= (:known %) y) by-known)
               fit (fit-obs (vals (ef/observations known)) spec (assoc opts :start start))
               snap (when fit (snapshot known y stale))
               scores (reduce (fn [acc [iso {:keys [x t]}]]
                                (let [s (score fit x spec)]
                                  (if (and s (some true? (:ok s)))
                                    (conj! acc (assoc s :iso iso :year y
                                                      :data-year (apply max (vals (select-keys t (keys (:loading fit)))))))
                                    acc)))
                              scores (sort-by key snap))]
           (recur more (or fit start) (cond-> fits fit (assoc y fit)) scores)))))))

(defn stability
  "The second-axis rule's numbers for `spec`: {:over-time {axis {:min c
  :by-year {y c}}} :split-half {axis c}}, congruences of each axis's
  loadings between the fit at each of `years` and the fit at `ref`, and
  between fits at `ref` on the countries of two halves (by the parity of a
  stable hash of the ISO code)."
  [rows spec {:keys [years ref] :as opts}]
  (let [inds (:indicators spec)
        fit-at (fn [rs y] (fit-rows (filterv #(<= (:known %) y) rs) spec opts))
        r (fit-at rows ref)
        col (fn [fit a] (mapv #(get-in fit [:loading % a] 0.0) inds))
        half (fn [p] (filterv #(= p (even? (reduce + (map int (:iso %))))) rows))
        h0 (fit-at (half true) ref) h1 (fit-at (half false) ref)
        fits (into {} (for [y years] [y (fit-at rows y)]))]
    {:over-time (into {} (for [[a {:keys [name]}] (map-indexed vector (:axes spec))
                               :let [by (into (sorted-map) (for [[y f] fits :when f] [y (congruence (col f a) (col r a))]))]]
                           [name {:min (apply min (vals by)) :by-year by}]))
     :split-half (into {} (for [[a {:keys [name]}] (map-indexed vector (:axes spec))]
                            [name (congruence (col h0 a) (col h1 a))]))
     :ref r}))

;; ---------------------------------------------------------------- stocks

(defn stock-rows
  "Rows {:iso :year :variable :value :data-year} of the scores of `res`
  (from expanding) for `spec`; an axis is written where its :ok holds."
  [res spec]
  (->> (:scores res)
       (mapcat (fn [{:keys [iso year f sd k ok data-year]}]
                 (concat
                  (for [[a {:keys [name]}] (map-indexed vector (:axes spec)) :when (nth ok a)
                        [v x] [[(str "control/" name) (nth f a)] [(str "control/" name "-sd") (nth sd a)]]]
                    {:iso iso :year year :variable v :value (double x) :data-year data-year})
                  [{:iso iso :year year :variable "control/n" :value (double k) :data-year data-year}])))
       (sort-by (juxt :iso :year :variable))
       vec))

(defn indicator-rows
  "control/<indicator> rows for the indicators of `keep`: dated by the year
  known, the latest data year known in that year."
  [rows keep]
  (->> rows
       (filter #(keep (:indicator %)))
       (group-by (juxt :iso :known :indicator))
       (map (fn [[[iso known ind] rs]]
              (let [r (apply max-key :year rs)]
                {:iso iso :year known :variable (str "control/" ind) :value (double (:value r)) :data-year (:year r)})))
       (sort-by (juxt :iso :year :variable))
       vec))
