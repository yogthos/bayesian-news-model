(ns bnm.changepoint
  "Bayesian online changepoint detection (Adams & MacKay 2007) for a series
  whose level shifts: after each observation, the posterior over the run
  length since the last break, with a constant break hazard and a
  Normal-Gamma prior on each run's mean and precision, so a run's
  predictive is a Student t. For each step: the chance the level broke in
  the last `recent` steps, and the expected run length."
  (:require [bnm.math :as m]))

(def ^:private max-run 400)

(defn- log-t
  "Log density of a Student t at x: location mu, squared scale s2, dof nu."
  ^double [^double x ^double mu ^double s2 ^double nu]
  (let [z (/ (* (- x mu) (- x mu)) (* nu s2))]
    (- (+ (m/log-gamma (/ (+ nu 1.0) 2.0)) (* -0.5 (Math/log (* nu Math/PI s2))))
       (+ (m/log-gamma (/ nu 2.0)) (* (/ (+ nu 1.0) 2.0) (Math/log1p z))))))

(defn run
  "[{:recent :run}] for each value of `xs`. The prior is centred on the
  first value with the series' own spread unknown (kappa 1, alpha 1, beta
  from `prior-var`, 1 by default for standardized inputs)."
  [xs {:keys [hazard recent prior-var] :or {prior-var 1.0}}]
  (let [h (double hazard)
        mu0 (double (first xs))]
    (loop [[x & more] xs
           ;; per run length r: posterior mass and Normal-Gamma parameters
           ps [1.0] mus [mu0] kappas [1.0] alphas [1.0] betas [(double prior-var)]
           out (transient [])]
      (if (nil? x)
        (persistent! out)
        (let [x (double x)
              n (count ps)
              pred (mapv (fn [i]
                           (let [mu (mus i) k (kappas i) a (alphas i) b (betas i)]
                             (Math/exp (log-t x mu (/ (* b (+ k 1.0)) (* a k)) (* 2.0 a)))))
                         (range n))
              grow (mapv (fn [i] (* (ps i) (pred i) (- 1.0 h))) (range n))
              brk (reduce + (map (fn [i] (* (ps i) (pred i) h)) (range n)))
              joint (into [brk] grow)
              z (reduce + joint)
              post (if (pos? z) (mapv #(/ % z) joint) (vec (cons 1.0 (repeat n 0.0))))
              ;; each run's parameters after x; a new run starts from the prior
              upd (fn [i] (let [mu (mus i) k (kappas i) a (alphas i) b (betas i)]
                            [(/ (+ (* k mu) x) (+ k 1.0)) (+ k 1.0) (+ a 0.5)
                             (+ b (/ (* k (- x mu) (- x mu)) (* 2.0 (+ k 1.0))))]))
              grown (mapv upd (range n))
              keep (min (inc n) max-run)
              post (subvec post 0 keep)
              s (reduce + post)
              post (mapv #(/ % s) post)
              params (vec (take keep (cons [mu0 1.0 1.0 (double prior-var)] grown)))]
          (recur more post (mapv #(% 0) params) (mapv #(% 1) params) (mapv #(% 2) params) (mapv #(% 3) params)
                 (conj! out {:recent (reduce + (take (inc recent) post))
                             :run (reduce + (map-indexed (fn [r p] (* r p)) post))})))))))
