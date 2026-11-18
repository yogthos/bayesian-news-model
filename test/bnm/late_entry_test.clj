(ns bnm.late-entry-test
  "bnm-1hu, :late-entry :prior: a group whose inputs first appear late
  enters the averaging at its prior share instead of the intercept's
  record. A planted world: the year's crisis depends on the stress built
  over three years; one group reads that stress, but only from month 240;
  another reads it through heavy noise from the start, so it gains a long
  record over the intercept, as banking's early groups did."
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [clojure.test :refer [deftest is testing]]))

(def ^:private countries (mapv #(str "c" %) (range 12)))
(def ^:private parent (merge (zipmap countries (repeat "R")) {"R" "W"}))
(def ^:private months 600)
(def ^:private from 240)

(defn- world [seed]
  (let [g (m/rng seed)
        stress (into {} (for [c countries]
                          [c (vec (reductions (fn [s _] (+ (* 0.97 s) (* 0.35 (m/normal! g)))) 0.0 (range (dec months))))]))
        built (fn [c t] (/ (reduce + (subvec (stress c) (max 0 (- t 35)) (inc t))) (double (min 36 (inc t)))))
        crisis (into {} (for [c countries]
                          [c (into {} (for [y (range 1 (quot months 12))]
                                        [y (< (m/next-double! g) (m/expit (- (* 3.0 (built c (dec (* 12 y)))) 2.5)))]))]))]
    {:built built :crisis crisis
     ;; noise as a function of where and when, the same in every run
     :noise (fn [k place origin] (m/normal! (m/rng (hash [seed k place origin]))))}))

(defn- spec [w]
  {:scales [{:name :year :per 12 :horizon 12 :discount 0.995}]
   :levels [[:country countries] [:region ["R"]]]
   :parent parent
   :start 0 :end (dec months)
   :forget 0.99
   :target (fn [_ place origin]
             (let [cs (if (= "R" place) countries [place])
                   y (quot (inc origin) 12)]
               (when-let [ys (seq (keep #(get-in w [:crisis % y]) cs))]
                 {:y (boolean (some true? ys)) :known-at (+ origin 12)})))
   ;; the late group's input is unknown until month 240: 0.0, the unknown's mean, as
   ;; the banking spec's standardizer gives for a missing value
   :gauges (fn [_ place origin]
             (let [cs (if (= "R" place) countries [place])
                   built (/ (reduce + (map #((:built w) % origin) cs)) (count cs))
                   late (when (>= origin from) built)]
               [(+ built (* 1.5 ((:noise w) :early place origin))) (or late 0.0)]))
   :gauge-width {:year 2}
   :gauge-groups {:year {:early [0] :late [1]}}})

(defn- late-weight
  "The late group's weight at the year country node at origin t (ten years
  after its entry by default): locked out it stays near the intercept's
  record for decades (here 3e-4 at ten years, 0.94 only at thirty)."
  ([r] (late-weight r 359))
  ([r t] (get (some (fn [[o ws]] (when (= o t) ws)) (get-in r [:weights [:year :country]])) :late 0.0)))

(defn- log-score-after [r t0]
  (let [fs (filter #(and (= :year (:scale %)) (not= "R" (:place %)) (some? (:y %)) (>= (:origin %) t0)) (:forecasts r))]
    (/ (reduce + (map (fn [{:keys [p y]}] (Math/log (if y p (- 1.0 p)))) fs)) (count fs))))

(deftest a-late-group-enters-at-its-prior-share
  (let [w (world 11)
        locked (lt/run (spec w))
        entered (lt/run (assoc (spec w) :late-entry :prior))]
    (testing "without it the late group carries no weight ten years on"
      (is (< (late-weight locked) 0.01) (late-weight locked)))
    (testing "with it the late group, which reads the true signal, carries weight at once"
      (is (> (late-weight entered 251) 0.05) (late-weight entered 251))
      (is (> (late-weight entered) (* 100 (late-weight locked))) [(late-weight entered) (late-weight locked)]))
    (testing "and the forecasts after the entry are no worse"
      (is (>= (log-score-after entered from) (- (log-score-after locked from) 1e-3))))
    (testing "the first origin is untouched: nothing has entered yet. (The rule is
              uniform: the error-based groups, dormant at the first origin by
              construction, enter at their prior shares a period later too.)"
      (is (= (filter #(= 11 (:origin %)) (:forecasts locked))
             (filter #(= 11 (:origin %)) (:forecasts entered)))))))

(deftest a-noise-group-entering-late-earns-nothing
  ;; a world of noise only: the late column enters at its prior share,
  ;; decays from it, and costs the forecasts nothing
  (let [w (world 13)
        sp (fn [x] (assoc (spec w) :late-entry x
                          :gauges (fn [_ place origin] [((:noise w) :a place origin) (if (>= origin from) ((:noise w) :b place origin) 0.0)])))
        on (lt/run (sp :prior))
        off (lt/run (sp nil))]
    (is (< (late-weight on 359) (late-weight on 251)) [(late-weight on 251) (late-weight on 359)])
    (is (>= (log-score-after on from) (- (log-score-after off from) 1e-3)))))

(deftest the-option-is-checked
  (is (thrown? Exception (lt/run (assoc (spec (world 1)) :late-entry :equal :end 30)))))
