(ns bnm.lattice-test
  (:require [augur.dynamic]
            [augur.math :as m]
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
      (doseq [{s :scale o :origin dir :dir k :known-at} reads]
        (case dir
          ;; errors fed back: only those known by the origin
          :feedback (is (<= k origin) [scale place origin s k])
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

(deftest gauge-groups-and-priors
  ;; a node's gauges in named groups, each its own model in the average,
  ;; and priors that start the weights from what is known
  (let [w (world 4)
        s (assoc (spec w)
                 :end 240
                 ;; no discount at the month, so the prior is not forgotten
                 :scales [{:name :month :per 1 :horizon 1 :discount 1.0}
                          {:name :year :per 12 :horizon 12 :discount 0.995}]
                 :gauge-width {:month 2 :year 1}
                 :gauges (fn [scale place origin]
                           (if (= scale :month)
                             (if (= 2 (count place)) [(get-in w [:gauge place origin]) ((:noise w))] [0.0 0.0])
                             [((:noise w))]))
                 :gauge-groups {:month {:signal [0] :noise [1]}}
                 :prior (fn [scale level layout]
                          (when (and (= scale :month) (= level :country))
                            {(first (get-in layout [:groups :signal])) [1.5 0.01]})))
        r (lt/run s)
        ws (lt/weights r :month :country)]
    (is (contains? ws :signal))
    (is (contains? ws :noise))
    (is (> (:signal ws) (:noise ws)) ws)
    (testing "undiscounted, a tight prior holds the signal's coefficient near its mean"
      (let [m (lt/model r :month :country :signal)]
        (is (< (Math/abs (- 1.5 (second (augur.dynamic/means m)))) 0.3) (augur.dynamic/means m))))))

;; --- the dialectic: contradictions, their accumulation and resolution ---

(defn- contradiction-world
  "Countries whose monthly gauge says calm or stress, inside regions whose
  own gauge drifts on its own: a country's yearly crisis comes when the
  gap between it and its region has accumulated, and resets the tension."
  [seed]
  (let [g (m/rng seed)
        walk (fn [] (vec (reductions (fn [s _] (+ (* 0.97 s) (* 0.4 (m/normal! g)))) 0.0 (range (dec months)))))
        region (into {} (for [r ["A" "B"]] [r (walk)]))
        local (into {} (for [c countries] [c (walk)]))
        gap (into {} (for [c countries] [c (mapv - (region (parent c)) (local c))]))
        crisis (into {} (for [c countries]
                          [c (loop [y 3 acc 0.0 out {}]
                               (if (>= y (quot months 12))
                                 out
                                 (let [acc (+ (* 0.7 acc) (/ (reduce + (map #(Math/abs (double %)) (subvec (gap c) (- (* 12 y) 12) (* 12 y)))) 12.0))
                                       hit (< (m/next-double! g) (m/expit (- (* 1.5 acc) 4.0)))]
                                   (recur (inc y) (if hit 0.0 acc) (assoc out y hit)))))]))]
    {:region region :local local :crisis crisis :noise #(m/normal! g)}))

(defn- contradiction-spec [w]
  {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}
            {:name :year :per 12 :horizon 12 :discount 0.995}]
   :levels [[:country countries] [:region ["A" "B"]]]
   :parent parent :start 0 :end (dec months) :forget 0.99
   :target (fn [scale place origin]
             (when (= scale :year)
               (let [y (quot (inc origin) 12)
                     cs (if (= 2 (count place)) [place] (filter #(= place (parent %)) countries))
                     ys (keep #(get-in w [:crisis % y]) cs)]
                 (when (seq ys) {:y (boolean (some true? ys)) :known-at (+ origin 12)}))))
   ;; each place's month gauge is its own state: a country's local, a region's own
   :gauges (fn [scale place origin]
             (if (= scale :month)
               [(if (= 2 (count place)) (get-in w [:local place origin]) (get-in w [:region place origin]))]
               [((:noise w))]))
   :gauge-width {:month 1 :year 1}
   ;; the month node has no outcome: its state is its gauge
   :state (fn [scale _ _ gs] (when (= scale :month) (first gs)))
   ;; a country's crisis in year y is known at the year's end
   :resolutions (fn [place]
                  (into (sorted-set) (for [[y hit] (get-in w [:crisis place]) :when hit] (+ (* 12 y) 11))))})

(deftest accumulated-contradiction-between-levels
  ;; ablations: what each group adds, not which model the averaging favours
  (let [w (contradiction-world 7)
        late (fn [r] (filter #(and (= :year (:scale %)) (= 2 (count (:place %))) (> (:origin %) 240)) (:forecasts r)))
        score (fn [without] (log-score (late (lt/run (assoc (contradiction-spec w) :without without)))))
        full (score #{})]
    (is (> full (+ 0.1 (score #{:contradiction}))) "the contradiction carries the signal")
    (testing "errors fed across levels add to it, but only on top of it"
      (is (> full (+ 0.02 (score #{:feedback}))))
      (is (< (Math/abs (- (score #{:contradiction}) (score #{:contradiction :feedback}))) 0.02)))))

(deftest tension-resets-only-when-the-crisis-is-known
  (let [acc (lt/accumulate {:decay 0.7} [[0 1.0 nil] [1 1.0 nil] [2 1.0 {:resolved-at 5}] [3 1.0 nil] [5 1.0 nil] [6 1.0 nil]])]
    (is (< (Math/abs (- (acc 3) (+ 1.0 (* 0.7 (+ 1.0 (* 0.7 (+ 1.0 (* 0.7 1.0)))))))) 1e-9)
        "before the crisis is known, the tension keeps building")
    (is (= 1.0 (acc 5)) "known at 5: the tension resets, then this period's gap")
    (is (< (Math/abs (- (acc 6) (+ 1.0 0.7))) 1e-9))))

(deftest a-world-shock-lands-through-exposure
  ;; a world shock hits only the countries exposed to it (fuel importers)
  (let [g (m/rng 11)
        ;; a slow shock, as an energy shock is: months, not weeks
        shock (vec (reductions (fn [s _] (+ (* 0.98 s) (* 0.2 (m/normal! g)))) 0.0 (range (dec months))))
        exposed #{"a1" "b2"}
        crisis (into {} (for [c countries]
                          [c (into {} (for [y (range 3 (quot months 12))
                                            :let [s (/ (reduce + (subvec shock (- (* 12 y) 12) (* 12 y))) 12.0)]]
                                        [y (< (m/next-double! g) (m/expit (- (if (exposed c) (* 3.0 s) 0.0) 2.5)))]))]))
        sp {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}
                     {:name :year :per 12 :horizon 12 :discount 0.995}]
            :levels [[:country countries] [:region ["A" "B"]] [:world ["W"]]]
            :parent parent :start 0 :end (dec months) :forget 0.99
            :target (fn [scale place origin]
                      (when (and (= scale :year) (= 2 (count place)))
                        (when-some [y (get-in crisis [place (quot (inc origin) 12)])]
                          {:y y :known-at (+ origin 12)})))
            ;; only the world sees the shock
            :gauges (fn [scale place origin] [(if (= place "W") (shock origin) 0.0)])
            :gauge-width {:month 1 :year 1}
            ;; every node's state is its gauge: the world's is the shock
            :state (fn [_ _ _ gs] (first gs))
            :exposure (fn [_ place _] [(if (exposed place) 1.0 0.0)])
            :exposure-width 1}
        r (lt/run sp)
        cut (lt/run (assoc sp :without #{:exposure}))
        late (fn [r] (filter #(and (= :year (:scale %)) (= 2 (count (:place %))) (> (:origin %) 240)) (:forecasts r)))]
    ;; two countries of six are exposed: the gain is theirs
    (is (> (log-score (late r)) (+ 0.005 (log-score (late cut)))) [(log-score (late r)) (log-score (late cut))])
    (is (> (get (lt/weights r :year :country) :exposure 0.0) 0.5) (lt/weights r :year :country))))

(deftest levels-learn-from-each-other
  ;; countries see their gauge only in a short spell of outcomes; the
  ;; regions see theirs throughout: coupled, the countries' credit
  ;; coefficient learns from the regions' experience
  (let [g (m/rng 13)
        x (into {} (for [p (concat countries ["A" "B"]) t (range months)] [[p t] (m/normal! g)]))
        y (into {} (for [[k v] x] [k (< (m/next-double! g) (m/expit (- (* 1.5 v) 1.0)))]))
        sp {:scales [{:name :month :per 1 :horizon 1 :discount 1.0}]
            :levels [[:country countries] [:region ["A" "B"]]]
            :parent parent :start 0 :end 300 :forget 0.99
            :target (fn [_ place origin]
                      (when (or (= 1 (count place)) (< origin 30))
                        {:y (y [place (inc origin)]) :known-at (inc origin)}))
            :gauges (fn [_ place origin] [(x [place (inc origin)])])
            :gauge-width {:month 1}
            :gauge-groups {:month {:credit [0]}}}
        coupled (lt/run (assoc sp :coupling 0.2))
        alone (lt/run sp)
        coef (fn [r] (second (augur.dynamic/means (lt/model r :month :country :credit))))]
    (is (< (Math/abs (- 1.5 (coef coupled))) (Math/abs (- 1.5 (coef alone))))
        [(coef coupled) (coef alone)])))

(deftest internal-tensions-are-gaps-between-paired-gauges
  (is (= [2.0 -1.0] (lt/tension-gaps [[0 1] [2 0]] [3.0 1.0 2.0]))))

(deftest resolutions-come-once-per-onset
  (let [w (contradiction-world 7)
        r (lt/run (assoc (contradiction-spec w) :end 200 :trace true))
        resets (:resets r)]
    (testing "a country resets once per crisis, when it is known"
      (let [c "a1"
            known (set (filter #(<= % 200) ((:resolutions (contradiction-spec w)) c)))]
        (is (= known (set (keep (fn [[[s p] ts]] (when (and (= p c) (= s :month)) ts)) (for [[k ts] resets t ts] [k t]))))
            [known (get resets [:month c])])))
    (testing "an aggregate resets only when the spec says"
      (is (empty? (get resets [:year "A"]))))))

(deftest without-drops-what-it-names
  (let [w (world 5)]
    (is (thrown? Exception (lt/run (assoc (spec w) :end 30 :without #{:nonsense}))))
    (let [r (lt/run (assoc (spec w) :end 30 :without #{:gauges}))]
      (is (not (contains? (lt/weights r :year :country) :gauges))))))
