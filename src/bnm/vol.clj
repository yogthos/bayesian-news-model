(ns bnm.vol
  "Volatility benchmarks: EWMA (RiskMetrics), GARCH(1,1) by Gaussian quasi
  likelihood or with standardized Student t innovations, set up as the arch
  package does (zero mean, the variance started from an exponentially
  weighted backcast), and HAR regressions of realized variance (Corsi 2009)."
  (:require [augur.math :as m]))

(defn backcast
  "The starting variance: squared returns over the first 75 periods,
  weighted 0.94^i and normalized (arch's backcast)."
  ^double [r]
  (let [xs (take 75 r)
        ws (take (count xs) (iterate #(* 0.94 %) 1.0))]
    (/ (reduce + (map #(* %1 %2 %2) ws xs)) (reduce + ws))))

(defn ewma-step
  "The next variance from the last one, h, and the last return, x."
  ^double [^double h ^double x ^double lambda]
  (+ (* lambda h) (* (- 1.0 lambda) x x)))

(defn ewma
  "The variance forecast made after each return of r: the next period's."
  [r lambda]
  (vec (rest (reductions #(ewma-step %1 %2 lambda) (backcast r) r))))

(defn- variances
  "GARCH(1,1) conditional variances of r, each given the returns before it."
  ^doubles [^doubles r ^double omega ^double alpha ^double beta ^double bc]
  (let [n (alength r) h (double-array n)]
    (loop [t 0 prev-x2 bc prev-h bc]
      (when (< t n)
        (let [ht (+ omega (* alpha prev-x2) (* beta prev-h))]
          (aset h t ht)
          (recur (inc t) (* (aget r t) (aget r t)) ht))))
    h))

(defn- loglik
  "Log likelihood of r with variances h: Gaussian, or standardized t with
  nu degrees of freedom when nu is given."
  ^double [^doubles r ^doubles h nu]
  (let [n (alength r)]
    (if nu
      (let [nu (double nu)
            c (- (m/log-gamma (/ (+ nu 1.0) 2.0)) (m/log-gamma (/ nu 2.0)) (* 0.5 (Math/log (* Math/PI (- nu 2.0)))))]
        (loop [t 0 s 0.0]
          (if (< t n)
            (let [ht (aget h t) x (aget r t)]
              (recur (inc t) (+ s c (* -0.5 (Math/log ht))
                                (* -0.5 (+ nu 1.0) (Math/log1p (/ (* x x) (* ht (- nu 2.0))))))))
            s)))
      (loop [t 0 s 0.0]
        (if (< t n)
          (let [ht (aget h t) x (aget r t)]
            (recur (inc t) (- s (* 0.5 (+ (Math/log (* 2.0 Math/PI)) (Math/log ht) (/ (* x x) ht))))))
          s)))))

(defn- unpack
  "Unconstrained [log omega, logit persistence, logit share of alpha
  (, log (nu - 2.05))] to parameters."
  [[lw lp la ln]]
  (let [p (m/expit lp) a (* p (m/expit la))]
    (cond-> {:omega (Math/exp lw) :alpha a :beta (- p a)}
      ln (assoc :nu (min 500.0 (+ 2.05 (Math/exp ln)))))))

(defn garch-fit
  "GARCH(1,1) on returns r (zero mean): {:omega :alpha :beta :llf}, with
  :nu for {:dist :t}. Nelder-Mead from a few starting points, or from
  `:start` (an earlier fit), restarted from its best until it stops
  improving."
  ([r] (garch-fit r {}))
  ([r {:keys [dist start]}]
   (let [ra (double-array r)
         bc (backcast r)
         v (/ (areduce ra i s 0.0 (+ s (* (aget ra i) (aget ra i)))) (alength ra))
         t? (= dist :t)
         nll (fn [p] (let [{:keys [omega alpha beta nu]} (unpack p)
                           ll (loglik ra (variances ra omega alpha beta bc) nu)]
                       (if (Double/isNaN ll) 1e300 (- ll))))
         start-at (fn [a b] (cond-> [(Math/log (* v (- 1.0 a b))) (m/logit (+ a b)) (m/logit (/ a (+ a b)))]
                           t? (conj (Math/log (- 8.0 2.05)))))
         pack (fn [{:keys [omega alpha beta nu]}]
                (cond-> [(Math/log omega) (m/logit (+ alpha beta)) (m/logit (/ alpha (+ alpha beta)))]
                  t? (conj (Math/log (- (or nu 8.0) 2.05)))))
         best (if start
                (m/nelder-mead nll (pack start) {:tol 1e-12 :max-iter 4000})
                (apply min-key :f (for [[a b] [[0.05 0.9] [0.1 0.85] [0.1 0.88] [0.03 0.95]]]
                                    (m/nelder-mead nll (start-at a b) {:tol 1e-12 :max-iter 4000}))))
         best (loop [b best]
                (let [b' (m/nelder-mead nll (:x b) {:tol 1e-14 :max-iter 4000})]
                  (if (< (- (:f b) (:f b')) 1e-10) b' (recur b'))))]
     (assoc (unpack (:x best)) :llf (- (:f best)) :backcast bc))))

(defn garch-forecast
  "Variance forecasts 1 to k periods after the end of r from a fit."
  [{:keys [omega alpha beta backcast]} r k]
  (let [ra (double-array r)
        h (variances ra omega alpha beta (or backcast (bnm.vol/backcast r)))
        n (alength ra)
        h1 (+ omega (* alpha (aget ra (dec n)) (aget ra (dec n))) (* beta (aget h (dec n))))]
    (vec (take k (iterate #(+ omega (* (+ alpha beta) %)) h1)))))

(defn- har-row
  "1, then the mean realized variance over the last w periods for each w in lags."
  [rv t lags]
  (double-array (cons 1.0 (for [w lags] (/ (reduce + (subvec rv (- (inc t) w) (inc t))) (double w))))))

(defn har-fit
  "HAR regression of the next period's realized variance on its means over
  the last `:lags` periods (1, 5 and 22 by default, days; weeks and months
  take their own), by least squares: {:beta :lags}."
  ([rv] (har-fit rv {}))
  ([rv {:keys [lags] :or {lags [1 5 22]}}]
   (let [rv (vec rv)
         k (inc (count lags))
         rows (for [t (range (dec (apply max lags)) (dec (count rv)))] [(har-row rv t lags) (rv (inc t))])
         xtx (double-array (* k k)) xty (double-array k)]
     (doseq [[^doubles x y] rows]
       (dotimes [i k]
         (aset xty i (+ (aget xty i) (* (aget x i) y)))
         (dotimes [j k] (aset xtx (+ (* i k) j) (+ (aget xtx (+ (* i k) j)) (* (aget x i) (aget x j)))))))
     {:beta (vec (m/solve-spd xtx xty k)) :lags lags})))

(defn har-forecast
  "The next period's realized variance after the end of rv."
  ^double [{:keys [beta lags] :or {lags [1 5 22]}} rv]
  (let [rv (vec rv) x (har-row rv (dec (count rv)) lags)]
    (reduce + (map * beta x))))
