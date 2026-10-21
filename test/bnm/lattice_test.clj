(ns bnm.lattice-test
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [clojure.test :refer [deftest is testing]]))

;; A synthetic world: six countries in two regions over 40 years of months.
;; Each country carries a slowly drifting stress. Every month it shows as
;; small events (more stress, more events) and as a noisy gauge the month
;; node can read. A year's crisis depends on how much stress built up over
;; the three years before: quantity turning into quality. The year node's
;; own gauges are noise; only what the months learned can tell it.

(def countries ["a1" "a2" "a3" "b1" "b2" "b3"])
(def parent (merge (zipmap ["a1" "a2" "a3"] (repeat "A")) (zipmap ["b1" "b2" "b3"] (repeat "B"))
                   {"A" "W" "B" "W"}))
(def months 480)

(defn- world [seed]
  (let [g (m/rng seed)
        stress (into {} (for [c countries]
                          [c (vec (reductions (fn [s _] (+ (* 0.97 s) (* 0.35 (m/normal! g)))) 0.0 (range (dec months))))]))
        events (into {} (for [c countries]
                          [c (mapv (fn [s] (< (m/next-double! g) (m/expit (- (* 1.5 s) 2.0)))) (stress c))]))
        gauge (into {} (for [c countries] [c (mapv #(+ % (* 0.5 (m/normal! g))) (stress c))]))
        ;; a crisis in year y: the mean stress of the 36 months before it
        crisis (into {} (for [c countries]
                          [c (into {} (for [y (range 3 (quot months 12))
                                            :let [built (/ (reduce + (subvec (stress c) (- (* 12 y) 36) (* 12 y))) 36.0)]]
                                        [y (< (m/next-double! g) (m/expit (- (* 3.0 built) 2.5)))]))]))]
    {:events events :gauge gauge :crisis crisis :noise (fn [] (m/normal! g))}))

(defn- spec [w]
  {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}
            {:name :year :per 12 :horizon 12 :discount 0.995}]
   :levels [[:country countries] [:region ["A" "B"]]]
   :parent parent
   :start 0 :end (dec months)
   :forget 0.99
   ;; the outcome over (origin, origin + horizon]: for a region, any of its countries
   :target (fn [scale place origin]
             (let [cs (if (= 2 (count place)) [place] (filter #(= place (parent %)) countries))]
               (case scale
                 :month (when (< (inc origin) months)
                          {:y (boolean (some #(get-in w [:events % (inc origin)]) cs)) :known-at (inc origin)})
                 :year (let [y (quot (inc origin) 12)]
                         (when-let [ys (seq (keep #(get-in w [:crisis % y]) cs))]
                           {:y (boolean (some true? ys)) :known-at (+ origin 12)})))))
   :gauges (fn [scale place origin]
             (if (and (= scale :month) (= 2 (count place)))
               [(get-in w [:gauge place origin])]
               [((:noise w))]))
   :gauge-width {:month 1 :year 1}})

(defn- log-score [fs]
  (let [fs (filter #(some? (:y %)) fs)]
    (/ (reduce + (map (fn [{:keys [p y]}] (Math/log (if y p (- 1.0 p)))) fs)) (count fs))))

(deftest the-year-learns-what-the-months-built
  (let [w (world 1)
        full (lt/run (spec w))
        cut (lt/run (assoc (spec w) :only #{:base :gauges}))
        late (fn [r] (filter #(and (= :year (:scale %)) (= 2 (count (:place %))) (> (:origin %) 240)) (:forecasts r)))]
    (is (> (log-score (late full)) (+ 0.01 (log-score (late cut))))
        [(log-score (late full)) (log-score (late cut))])
    (testing "the year node's weights move to what the months pass up"
      (let [ws (lt/weights full :year :country)]
        (is (> (+ (get ws :up 0.0) (get ws :all 0.0)) 0.6) ws)))))

(deftest nothing-from-the-future
  ;; a node at origin t reads finer forecasts issued by t, coarser and
  ;; parent forecasts issued before t, and learns from outcomes known by t
  (let [w (world 2)
        r (lt/run (assoc (spec w) :end 200 :trace true))]
    (doseq [{:keys [scale place origin reads learned]} (:trace r)]
      (doseq [{s :scale o :origin dir :dir} reads]
        (case dir
          ;; finer scales and children: issued by the origin
          :up (is (and (<= o origin) (or (lt/finer? (spec w) s scale) (= s scale))) [scale place origin s o])
          ;; coarser scales and parents: issued before it
          :down (is (and (< o origin) (not (lt/finer? (spec w) s scale))) [scale place origin s o])))
      (doseq [{k :known-at} learned]
        (is (<= k origin))))))

(deftest regions-read-their-children
  (let [w (world 3)
        r (lt/run (assoc (spec w) :end 120 :trace true))
        region-reads (for [{:keys [place reads]} (:trace r) :when (= "A" place) {p :place} reads] p)]
    (is (some #{"a1"} region-reads) "a region hears its countries")
    (is (not-any? #{"b1"} region-reads) "and not another region's")))
