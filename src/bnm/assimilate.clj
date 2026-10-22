(ns bnm.assimilate
  "A stock-flow model's parameters learned from its history (docs/STOCK-
  FLOW.md; principle 2 of docs/PRINCIPLES.md: learned, not set), by the
  ensemble Kalman filter (augur.enkf) on the augmented state: an ensemble
  of members, each with its parameters drawn from their priors, runs
  forward to the next observation from where it stands; there its stocks
  and parameters are updated together by how they covary, across the
  ensemble, with what was observed. What the data cannot see keeps its
  prior's spread: identification shows in the posterior."
  (:require [augur.enkf :as enkf]
            [augur.math :as m]
            [bnm.stockflow :as sf]))

(defn- draw [{:keys [mean sd log]} g]
  (if log (+ (Math/log mean) (* sd (m/normal! g))) (+ mean (* sd (m/normal! g)))))

(defn- natural [{:keys [log]} x] (if log (Math/exp x) x))

(defn- moments [xs]
  (let [n (double (count xs)) mu (/ (reduce + xs) n)]
    {:mean mu :sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (max 1.0 (dec n))))}))

(defn assimilate
  "The posterior of `model`'s parameters given `observations` ({t {name
  value}}, a name a stock, flow or auxiliary): {:params {name {:mean
  :sd}} :stocks {name {:mean :sd}} at the last observation, :members}.
  Options: :from (the start), :dt, :members, :seed, :priors {name {:mean
  :sd :log}} (with :log, normal in the log, :mean its median), :noise
  {name sd} or :noise-at (fn [t]) -> {name sd}, the observations' noise."
  [model {:keys [from dt members seed priors observations noise noise-at]}]
  (let [g (m/rng (or seed 1))
        ss (vec (sort (keys (:stocks model))))
        nonneg (set (keep (fn [[k {:keys [nonneg]}]] (when nonneg k)) (:stocks model)))
        ps (vec (sort (keys priors)))
        params-of (fn [theta] (into {} (map (fn [p x] [p (natural (priors p) x)]) ps theta)))
        start (vec (for [_ (range members)]
                     (let [theta (mapv #(draw (priors %) g) ps)]
                       {:levels (:stocks (sf/at model from (params-of theta) dt)) :theta theta})))]
    (loop [[[t obs] & more] (seq observations) ens start now (double from)]
      (if (nil? t)
        {:params (into {} (for [[i p] (map-indexed vector ps)]
                            [p (moments (map #(natural (priors p) (nth (:theta %) i)) ens))]))
         :stocks (into {} (for [s ss] [s (moments (map #(get-in % [:levels s]) ens))]))
         :members ens}
        (let [names (vec (sort (keys obs)))
              sds ((or noise-at (constantly noise)) t)
              ran (pmap (fn [{:keys [levels theta]}]
                          (let [r (if (> t now)
                                    (sf/run model {:from now :to t :dt dt :levels levels :params (params-of theta)})
                                    (assoc (sf/at model now (params-of theta) dt) :stocks (update-vals levels vector)))
                                last-of (fn [k] (last (or (get-in r [:stocks k]) (get-in r [:aux k]) (get-in r [:flows k])
                                                          (throw (ex-info (str "bnm.assimilate: nothing named " k) {:name k})))))]
                            {:levels (into {} (for [s ss] [s (last-of s)])) :theta theta
                             :pred (mapv last-of names)}))
                        ens)
              ns (count ss) np (count ps)
              x (mapv (fn [{:keys [levels theta pred]}] (vec (concat (map levels ss) theta pred))) ran)
              post (enkf/update x #(subvec % (+ ns np)) (mapv obs names) (mapv #(let [s (sds %)] (* s s)) names) g)]
          (recur more
                 (mapv (fn [v] {:levels (into {} (map (fn [s i] [s (cond-> (nth v i) (nonneg s) (max 0.0))]) ss (range)))
                                :theta (subvec v ns (+ ns np))})
                       post)
                 (double t)))))))
