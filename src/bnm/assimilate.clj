(ns bnm.assimilate
  "A stock-flow model's parameters learned from its history (docs/STOCK-
  FLOW.md; principle 2 of docs/PRINCIPLES.md: learned, not set), by
  ensemble methods on augur.enkf's analysis, no derivatives needed.

  calibrate, for a deterministic model's parameters: the ensemble
  smoother with multiple data assimilation (Emerick & Reynolds 2013).
  Members, each with its parameters drawn from their priors, run from the
  start; every observation updates the parameters at once, the noise
  inflated by the number of iterations; again from the start, so the
  stocks always follow from the parameters. Its posterior is close to
  the exact one (tested against a grid).

  assimilate, the ensemble Kalman filter on the augmented state: members
  run to each observation in turn, where stocks and parameters are
  updated together. It tracks the stocks as it goes, but moving them apart
  from the parameters acts as noise in the state, so its spread is that
  of a model with such noise: wider than calibrate's.

  What the data cannot see keeps its prior's spread: identification
  shows in the posterior."
  (:require [augur.enkf :as enkf]
            [augur.math :as m]
            [bnm.stockflow :as sf]))

(defn- draw [{:keys [mean sd log]} g]
  (if log (+ (Math/log mean) (* sd (m/normal! g))) (+ mean (* sd (m/normal! g)))))

(defn- natural [{:keys [log]} x] (if log (Math/exp x) x))

(defn- moments [xs]
  (let [n (double (count xs)) mu (/ (reduce + xs) n)]
    {:mean mu :sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (max 1.0 (dec n))))}))

(defn- summary
  "{:mean :sd :median} in natural units, and for a :log parameter its
  spread in the log, :log-sd."
  [prior thetas]
  (let [xs (sort (map #(natural prior %) thetas))]
    (cond-> (assoc (moments xs) :median (let [n (count xs) h (quot n 2)]
                                          (if (odd? n) (nth xs h) (/ (+ (nth xs (dec h)) (nth xs h)) 2.0))))
      (:log prior) (assoc :log-sd (:sd (moments thetas))))))

(defn- checked
  "The options, or an error naming what is missing."
  [{:keys [from members observations noise noise-at] :as opts}]
  (doseq [k [:from :dt :priors :observations] :when (nil? (opts k))]
    (throw (ex-info (str "bnm.assimilate: " k " is required") {:missing k})))
  (when-not (or noise noise-at) (throw (ex-info "bnm.assimilate: :noise or :noise-at is required" {})))
  (let [sds (or noise-at (constantly noise))]
    (doseq [[t obs] observations n (keys obs) :when (nil? (get (sds t) n))]
      (throw (ex-info (str "bnm.assimilate: no noise for " n " at " t) {:name n :t t})))
    (when (empty? observations) (throw (ex-info "bnm.assimilate: no observations" {})))
    (when-let [early (seq (filter #(< (double %) (double from)) (keys observations)))]
      (throw (ex-info "bnm.assimilate: observations before :from" {:times (vec early)})))
    (when-let [off (seq (remove #(let [k (/ (- (double %) (double from)) (double (:dt opts)))] (< (Math/abs (- k (Math/round k))) 1e-9))
                                (keys observations)))]
      (throw (ex-info "bnm.assimilate: observation times off the dt grid from :from" {:times (vec off)})))
    (when-let [na (:iterations opts)]
      (when-not (and (integer? na) (pos? na)) (throw (ex-info "bnm.assimilate: :iterations a positive integer" {:iterations na}))))
    (assoc opts :members (or members 100) :sds sds)))

(defn- value-at
  "A name's series in a run: a stock's, flow's or auxiliary's."
  [r k]
  (or (get-in r [:stocks k]) (get-in r [:aux k]) (get-in r [:flows k])
      (throw (ex-info (str "bnm.assimilate: nothing named " k) {:name k}))))

(defn calibrate
  "The posterior of `model`'s parameters given `observations` ({t {name
  value}}, a name a stock, flow or auxiliary, times on the dt grid from
  :from), by the ensemble smoother with multiple data assimilation:
  {:params {name {:mean :sd :median :log-sd}} :members [thetas]}.
  Options: :from, :dt, :members (100), :seed, :iterations (4), :priors
  {name {:mean :sd :log}} (with :log, normal in the log, :mean its
  median), :noise {name sd} or :noise-at (fn [t]) -> {name sd}."
  [model opts]
  (let [{:keys [from dt members seed iterations priors observations sds]} (checked opts)
        na (or iterations 4)
        g (m/rng (or seed 1))
        ps (vec (sort (keys priors)))
        params-of (fn [theta] (into {} (map (fn [p x] [p (natural (priors p) x)]) ps theta)))
        obs (vec (sort-by key observations))
        keys* (vec (for [[t o] obs n (sort (keys o))] [t n]))
        y (mapv (fn [[t n]] (get-in observations [t n])) keys*)
        r (mapv (fn [[t n]] (* na (Math/pow (get (sds t) n) 2))) keys*)
        last-t (double (key (peek obs)))
        idx (fn [t] (long (Math/round (/ (- (double t) (double from)) (double dt)))))
        predict (fn [theta]
                  (let [run (sf/run model {:from from :to last-t :dt dt :params (params-of theta)})]
                    (mapv (fn [[t n]] (nth (value-at run n) (idx t))) keys*)))
        np (count ps)
        thetas (loop [i 0 thetas (vec (for [_ (range members)] (mapv #(draw (priors %) g) ps)))]
                 (if (>= i na)
                   thetas
                   (let [x (mapv into thetas (pmap predict thetas))]
                     (recur (inc i) (mapv #(subvec % 0 np) (enkf/update x #(subvec % np) y r g))))))]
    {:params (into {} (for [[i p] (map-indexed vector ps)] [p (summary (priors p) (map #(nth % i) thetas))]))
     :members thetas}))

(defn assimilate
  "The ensemble Kalman filter on the augmented state, observations in
  time order (see the namespace): {:params {name {:mean :sd :median
  :log-sd}} :stocks {name {:mean :sd}} at the last observation,
  :members}. Options as calibrate's, less :iterations."
  [model opts]
  (let [{:keys [from dt members seed priors observations sds]} (checked opts)
        g (m/rng (or seed 1))
        ss (vec (sort (keys (:stocks model))))
        nonneg (set (keep (fn [[k {:keys [nonneg]}]] (when nonneg k)) (:stocks model)))
        ps (vec (sort (keys priors)))
        params-of (fn [theta] (into {} (map (fn [p x] [p (natural (priors p) x)]) ps theta)))
        start (vec (for [_ (range members)]
                     (let [theta (mapv #(draw (priors %) g) ps)]
                       {:levels (:stocks (sf/at model from (params-of theta) dt)) :theta theta})))]
    (loop [[[t obs] & more] (sort-by key observations) ens start now (double from)]
      (if (nil? t)
        {:params (into {} (for [[i p] (map-indexed vector ps)] [p (summary (priors p) (map #(nth (:theta %) i) ens))]))
         :stocks (into {} (for [s ss] [s (moments (map #(get-in % [:levels s]) ens))]))
         :members ens}
        (let [names (vec (sort (keys obs)))
              ran (pmap (fn [{:keys [levels theta]}]
                          ;; from where the member stands (a zero span: its state now)
                          (let [r (sf/run model {:from now :to (double t) :dt dt :levels levels :params (params-of theta)})]
                            {:levels (into {} (for [s ss] [s (last (value-at r s))])) :theta theta
                             :pred (mapv #(last (value-at r %)) names)}))
                        ens)
              ns (count ss) np (count ps)
              x (mapv (fn [{:keys [levels theta pred]}] (vec (concat (map levels ss) theta pred))) ran)
              post (enkf/update x #(subvec % (+ ns np)) (mapv obs names) (mapv #(Math/pow (get (sds t) %) 2) names) g)]
          (recur more
                 (mapv (fn [v] {:levels (into {} (map (fn [s i] [s (cond-> (nth v i) (nonneg s) (max 0.0))]) ss (range)))
                                :theta (subvec v ns (+ ns np))})
                       post)
                 (double t)))))))
