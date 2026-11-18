;; Testbed learnability: does a plain pooled logistic on the declared
;; signals (fit on the first 60% of years, scored on the last 40%) capture
;; more of the oracle's skill than the lattice does on the same world? If
;; it does, the lattice's learning is the bottleneck; if not, the signals.
;; jolt -A:tools -M docs/studies/testbed_learnability.clj
(require '[bnm.testbed :as tb] '[bnm.signals :as sg] '[bnm.lattice :as lt] '[augur.logit :as lgt] '[augur.math :as m])

(defn- ll [p y] (let [p (Math/max 1e-9 (Math/min (- 1 1e-9) p))] (Math/log (if y p (- 1.0 p)))))

(doseq [scenario [:credit-waves :synchronized :mixed-channels] seed [1 2 3]]
  (let [w (tb/world {:seed seed :scenario scenario})
        {:keys [years stocks]} (merge tb/defaults (:opts w))
        {:keys [row]} (sg/assemble (:db w) (tb/decls stocks))
        split (+ tb/epoch (long (* 0.6 years)))
        origins (range (+ (* 12 tb/epoch) 11) (* 12 (+ tb/epoch years -1)) 12)
        table (into {} (for [t origins c (:countries w)] [[c t] (row c t)]))
        std (sg/running-z (for [[[_ t] v] table] [t v]) {:count-by :values :min-exclusive 20 :stable true})
        rows (vec (for [t origins c (:countries w)
                        :let [yr (quot (inc t) 12)]
                        :when (and (tb/at-risk? w c yr) (get-in w [:hazard [c yr]]))]
                    {:yr yr :x (double-array (cons 1.0 (std t (table [c t])))) :y (contains? (get-in w [:onsets c] #{}) yr) :w 1.0
                     :true-p (get-in w [:hazard [c yr]])}))
        train (filter #(< (:yr %) split) rows) test (filter #(>= (:yr %) split) rows)
        k (alength ^doubles (:x (first rows)))
        beta (lgt/fit train k {:prior 1.0})
        rate (/ (count (filter :y train)) (double (count train)))
        mean (fn [f] (/ (reduce + (map f test)) (count test)))
        clim (mean #(ll rate (:y %)))
        logit (mean #(ll (lgt/predict beta (:x %)) (:y %)))
        oracle (mean #(ll (:true-p %) (:y %)))
        lattice (let [r (lt/run (tb/spec w {}))
                      ps (into {} (for [{:keys [scale place origin p]} (:forecasts r) :when (= :year scale)] [[place (quot (inc origin) 12)] p]))]
                  (tb/score w r))]
    (println (format "%-15s seed %d  oracle %+.4f  pooled logit %+.4f (share %.2f)  lattice %+.4f (share %.2f)   per row vs climatology"
                     (name scenario) seed (- oracle clim) (- logit clim) (/ (- logit clim) (- oracle clim))
                     (:log-skill lattice) (or (:skill-share lattice) Double/NaN)))))
