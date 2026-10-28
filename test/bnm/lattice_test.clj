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

(deftest core-inputs-in-every-model-and-shrinkage
  (let [w (world 5)
        s (assoc (spec w) :end 0
                 :gauge-width {:month 2 :year 1}
                 :gauges (fn [scale _ _] (if (= scale :month) [0.3 -0.2] [0.1]))
                 :gauge-groups {:month {:known [0] :other [1]}}
                 :core {:month [0]}
                 :shrink 0.05)
        r (lt/run s)
        k (fn [g] (:inputs (lt/model r :month :country g)))]
    (testing "the known input sits beside the intercept in every model"
      (is (= [0 1] (k :base)))
      (is (= [0 1 2] (k :other)))
      (is (some #{1} (k :down))))
    (testing "the all-inputs model shrinks what is not core"
      (let [m (lt/model r :month :country :all)
            ix (:inputs m)
            v (fn [i] (let [j (.indexOf ix i) kk (:k m)] (aget ^doubles (:c m) (+ (* j kk) j))))]
        (is (< (v 2) 0.06) "a non-core input starts tight (one discounted period)")
        (is (> (v 1) 0.5) "the core input does not")))))

(deftest offsets-are-what-is-known
  ;; with an offset, a model with nothing else to say forecasts the offset;
  ;; without one, climatology: the rate among outcomes already resolved
  (let [g (m/rng 21)
        truth (fn [place t] (- (* 0.5 (Math/sin (/ t 10.0))) 2.0 (if (= place "a1") 1.0 0.0)))
        ys (into {} (for [c countries t (range months)] [[c t] (< (m/next-double! g) (m/expit (truth c t)))]))
        base {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}]
              :levels [[:country countries]]
              :parent {} :start 0 :end 300 :forget 0.99
              :target (fn [_ place t] {:y (ys [place (inc t)]) :known-at (inc t)})
              :gauges (fn [_ _ _] [])
              :gauge-width {:month 0}}
        known (lt/run (assoc base :offset (fn [_ place t] (truth place (inc t)))))
        clim (lt/run (assoc base :offset (fn [_ _ _] nil)))
        late (fn [r] (filter #(> (:origin %) 200) (:forecasts r)))
        err (fn [r] (/ (reduce + (map (fn [{:keys [place origin p]}] (Math/abs (- p (m/expit (truth place (inc origin)))))) (late r)))
                       (count (late r))))]
    (is (< (err known) 0.01) (err known))
    (testing "climatology: the pooled resolved rate, from the start"
      (let [first-p (:p (first (:forecasts clim)))
            rate (/ (count (filter true? (vals ys))) (double (count ys)))]
        (is (< (Math/abs (- first-p 0.5)) 0.01) "nothing resolved yet: an even prior")
        (is (< (Math/abs (- (:p (last (:forecasts clim))) rate)) 0.05))))))

(deftest strain-is-directional
  ;; credit running ahead of wages strains the system; wages ahead of credit does not
  (is (= [0.0 2.0] (lt/strain [[0 1] [1 0]] [1.0 3.0])))
  (let [acc (lt/accumulate {:decay 0.7} [[0 2.0 nil] [1 -5.0 nil] [2 1.0 nil]])]
    (is (< (Math/abs (- (acc 1) 1.4)) 1e-12) "a gap the other way adds nothing; the strain only decays")
    (is (< (Math/abs (- (acc 2) (+ 1.0 (* 0.7 1.4)))) 1e-12))))

(deftest the-turn-is-a-boom-receding-from-its-peak
  ;; flow rises to a peak, then falls: the drawdown, alone and times the stretched stock
  (let [hist [0.0 1.0 2.0 3.0 2.5 1.5]
        turns (map #(lt/turn-of (subvec hist 0 (inc %)) 2.0 4) (range (count hist)))]
    (is (= [0.0 0.0 0.0 0.0] (map first (take 4 turns))) "rising: no turn")
    (is (= [0.5 1.5] (map first (drop 4 turns))) "falling from its peak of 3")
    (is (= [1.0 3.0] (map second (drop 4 turns))) "times the stock's stretch, 2")
    (is (= [0.0 0.0] (lt/turn-of [3.0 2.0] -1.0 4)) "an unstretched stock: no turn")))

(deftest coarser-strain-comes-down
  (let [w (contradiction-world 9)
        sp (assoc (contradiction-spec w) :end 120 :trace true
                  :tensions {:year [[0 0]]})
        r (lt/run sp)
        month-reads (for [{:keys [scale reads]} (:trace r) :when (= scale :month) rd reads
                          :when (and (= :down (:dir rd)) (= :year (:scale rd)))] rd)]
    (is (seq month-reads) "the month reads the year's last state and strain")))

(deftest flows-are-standardized-by-their-past
  ;; strain and gaps come in whatever units the states have: a node must
  ;; see them standardized, or a model reading them is wrecked by scale
  (let [w (contradiction-world 7)
        late (fn [r] (filter #(and (= :year (:scale %)) (= 2 (count (:place %))) (> (:origin %) 240)) (:forecasts r)))
        base (contradiction-spec w)
        scaled (assoc base :state (fn [scale p t gs] (some-> ((:state base) scale p t gs) (* 100.0))))]
    (is (< (Math/abs (- (log-score (late (lt/run base))) (log-score (late (lt/run scaled))))) 0.02)))
  (testing "the running standardizer reads only the past"
    (let [s (lt/standardizer)
          s (reduce (fn [s x] (lt/observe s [x])) s (concat [1.0 2.0 3.0 4.0] [1.0 2.0 3.0 4.0] [1.0 2.0 3.0 4.0]))]
      (is (< (Math/abs (- (first (lt/standardize s [4.0])) (/ (- 4.0 2.5) (Math/sqrt 1.25)))) 1e-9)
          "against the mean and spread of what came before")
      (is (= [5.0] (lt/standardize s [1e6])) "capped")))
  (testing "an unknown input (nil) is the mean, z = 0, and does not count"
    (let [s (reduce (fn [s x] (lt/observe s x)) (lt/standardizer)
                    (concat (repeat 12 [1.0 nil]) (repeat 12 [3.0 10.0]) [[nil 20.0]]))]
      (is (= 0.0 (first (lt/standardize s [nil 0.0]))))
      (is (< (Math/abs (- (first (lt/standardize s [3.0 0.0])) 1.0)) 1e-9) "the first input's 24 values only")
      (is (< (Math/abs (- (second (lt/standardize s [0.0 15.0])) (/ (- 15.0 (/ 140.0 13)) (Math/sqrt (- (/ (+ 1200.0 400.0) 13) (* (/ 140.0 13) (/ 140.0 13)))))))) 1e-9)
          "the second input's 13 values, its 12 unknowns left out")))

(deftest contradictions-are-summarized
  ;; a place's strain as a few summaries, not one input per tension: its
  ;; mean over the tensions it can measure, its change, its largest
  (is (= {:mean 1.5 :max 2.0} (lt/strain-summary [1.0 2.0 0.0] [true true false])))
  (is (= {:mean 0.0 :max 0.0} (lt/strain-summary [] [])))
  (testing "the summaries start from theory: more strain, more risk"
    (let [w (contradiction-world 7)
          r (lt/run (assoc (contradiction-spec w) :end 30 :tensions {:year [[0 0]]} :contradiction-prior [0.3 0.1]))
          m (lt/model r :year :country :contradiction)]
      (is (some #(< (Math/abs (- % 0.3)) 0.2) (augur.dynamic/means m))))))

(deftest groups-can-differ-by-scale
  (let [w (world 5)
        r (lt/run (assoc (spec w) :end 30 :without-at {:year #{:feedback :up}}))]
    (is (not (contains? (lt/weights r :year :country) :feedback)))
    (is (not (contains? (lt/weights r :year :country) :up)))
    (is (contains? (lt/weights r :month :country) :down) "the month keeps its own")
    (is (thrown? Exception (lt/run (assoc (spec w) :end 3 :without-at {:year #{:bogus}}))))))

(deftest each-group-s-evidence-is-kept-when-traced
  ;; for the smoke checks: a group losing its weight on evidence is told
  ;; from a broken one by its own record against the base model
  (let [r (lt/run (assoc (spec (world 1)) :trace true))
        ev (get-in r [:evidence [:year :country]])]
    (is (contains? ev :base))
    (is (= (set (keys (lt/weights r :year :country))) (set (keys ev))) "every model in the average")
    (is (every? (fn [[ll n]] (and (pos? n) (neg? ll))) (vals ev)) "log scores of outcomes, counted")
    (is (apply = (map second (vals ev))) "every model scored on the same rows")
    (is (nil? (:evidence (lt/run (spec (world 1))))) "only when traced")))

(deftest a-coarser-forecast-scaled-to-a-window
  ;; constant hazard: a window of a fraction r of the coarser one carries r
  ;; of its cumulative hazard, 1 - (1 - p)^r
  (is (< (Math/abs (- (lt/window-log-odds 0.19 0.5) (Math/log (/ 0.1 0.9)))) 1e-12) "0.19 over two years is 0.1 a year")
  (is (< (Math/abs (- (lt/window-log-odds 0.3 1.0) (Math/log (/ 0.3 0.7)))) 1e-12) "the whole window, itself")
  (is (< -100.0 (lt/window-log-odds 1e-12 0.375) 0.0) "tiny probabilities stay finite")
  (testing "the coarser uncertainty carried down: quadrature over its linear predictor"
    (let [os (lt/offsets-under 0.0 0.0 0.5)]
      (is (< (Math/abs (- 1.0 (reduce + (map first os)))) 1e-9) "weights sum to one")
      (is (every? #(< (Math/abs (- (second %) (lt/window-log-odds 0.5 0.5))) 1e-12) os) "no uncertainty, one offset"))
    (let [os (lt/offsets-under -2.0 1.0 0.5)
          os0 (lt/offsets-under -2.0 0.0 0.5)]
      (is (> (apply max (map second os)) (second (first os0))) "uncertainty spreads the offsets")
      (is (= 5 (count os))))))

(deftest windows-reconciled-with-their-year
  ;; the windows' chance of any onset pooled with the year's (log odds,
  ;; equal weights) and their hazards scaled in proportion to meet it.
  ;; Conditioning (bottom-up importance sampling) would multiply the two
  ;; forecasts' odds, counting twice what both know
  (let [[a b] (lt/reconcile 0.1 0.1 0.0 0.19)]
    (is (< (Math/abs (- a 0.1)) 1e-12) "already coherent: unchanged")
    (is (< (Math/abs (- b 0.1)) 1e-12)))
  (let [[a b] (lt/reconcile 0.1 0.1 0.0 0.5)]
    (is (> a 0.1) "the year sees more risk: the windows rise")
    (is (< a 0.5)))
  (let [[a] (lt/reconcile 0.1 0.1 0.05 0.05)]
    (is (< a 0.1) "the year sees less: they fall")))

(deftest the-year-sets-the-months-offset
  ;; :downscale: a finer node's offset is the coarser node's latest forecast
  ;; for the same place, issued before the origin, scaled to its window
  (let [w (world 1)
        latest (fn [r place t] (some-> (get-in r [:book [:year place]]) (rsubseq < t) first val))
        check (fn [r f]
                (let [rows (filter #(and (= :month (:scale %)) (> (:origin %) 24)) (:trace r))]
                  (is (seq rows))
                  (is (every? (fn [{:keys [place origin o]}]
                                (let [y (latest r place origin)]
                                  (< (Math/abs (- o (f y))) 1e-9)))
                              rows))))
        r (lt/run (assoc (spec w) :trace :inputs :downscale {:month {:from :year}}))]
    (check r #(lt/window-log-odds (:p %) (/ 1.0 12.0)))
    (testing "its read is from before the origin"
      (is (every? (fn [{:keys [origin reads]}] (every? #(< (:origin %) origin) (filter #(= :downscale (:dir %)) reads)))
                  (filter #(= :month (:scale %)) (:trace r))))
      (is (some (fn [{:keys [reads]}] (some #(= :downscale (:dir %)) reads)) (:trace r))))
    (testing "pooled with what is known, in log odds"
      (let [r (lt/run (assoc (spec w) :trace :inputs :downscale {:month {:from :year}} :downscale-pool 0.5
                             :offset (fn [scale _ _] (when (= scale :month) -3.0))))]
        (check r #(+ (* 0.5 -3.0) (* 0.5 (lt/window-log-odds (:p %) (/ 1.0 12.0)))))))
    (testing "the year's uncertainty carried down: the forecast averages over it"
      (let [r (lt/run (assoc (spec w) :trace :inputs :downscale {:month {:from :year}} :downscale-uncertainty true))
            rows (filter #(and (= :month (:scale %)) (> (:origin %) 24)) (:trace r))]
        (check r (fn [{:keys [f q]}]
                   (lt/logit-of (reduce + (for [[wt o] (lt/offsets-under f q (/ 1.0 12.0))] (* wt (m/expit o)))))))
        (is (every? #(< 0.0 (:p %) 1.0) rows))))))

(deftest relations-are-their-own-groups
  ;; :relations (fn [scale place t]) -> {:x [...] :reads [...]}: inputs from
  ;; other kinds, a group per parent (:relation-groups), standardized as the
  ;; node's other flows; a node whose outcome another kind's state drives
  ;; learns it there
  (let [g (m/rng 5)
        drive (into {} (for [c countries t (range months)] [[c t] (m/normal! g)]))
        ys (into {} (for [c countries t (range months)] [[c t] (< (m/next-double! g) (m/expit (- (* 1.5 (drive [c t])) 2.0)))]))
        base {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}]
              :levels [[:country countries]]
              :parent {} :start 0 :end 400 :forget 0.99
              :target (fn [_ place t] {:y (ys [place (inc t)]) :known-at (inc t)})
              :gauges (fn [_ _ _] [(m/normal! g)])
              :gauge-width {:month 1}
              :trace :inputs}
        rel (fn [_ place t] {:x [(drive [place (inc t)]) (m/normal! g)]
                             :reads [{:dir :relation :kind "other" :place place :origin (dec t)}]})
        r (lt/run (assoc base :relations rel :relation-width {:month 2}
                         :relation-groups {:month {:rel/driver [0] :rel/noise [1]}}))
        ws (lt/weights r :month :country)]
    (is (contains? ws :rel/driver))
    (is (> (+ (get ws :rel/driver 0.0) (get ws :all 0.0)) 0.8) ws)
    (is (< (get ws :rel/noise 0.0) 0.1))
    (is (some #(= :relation (:dir %)) (mapcat :reads (:trace r))) "its reads recorded")
    (testing "dropped with :without"
      (let [r2 (lt/run (assoc base :relations rel :relation-width {:month 2} :without #{:relations}))]
        (is (not (contains? (lt/weights r2 :month :country) :rel/driver)))))))

(deftest the-principal-contradiction-is-recorded
  (let [g (m/rng 5)
        drive (into {} (for [c countries t (range months)] [[c t] (m/normal! g)]))
        ys (into {} (for [c countries t (range months)] [[c t] (< (m/next-double! g) (m/expit (- (* 1.5 (drive [c t])) 2.0)))]))
        r (lt/run {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}]
                   :levels [[:country countries]] :parent {} :start 0 :end 300 :forget 0.99
                   :target (fn [_ place t] {:y (ys [place (inc t)]) :known-at (inc t)})
                   :gauges (fn [_ _ _] [(m/normal! g)]) :gauge-width {:month 1}
                   :relations (fn [_ place t] {:x [(drive [place (inc t)]) (m/normal! g)] :reads []})
                   :relation-width {:month 2} :relation-groups {:month {:rel/driver [0] :rel/noise [1]}}
                   :principal :auto})
        late (for [c countries [o e] (get-in r [:book [:month c]]) :when (> o 200)] (first (:principal e)))]
    (is (every? some? late))
    (is (> (/ (count (filter #{:rel/driver} late)) (double (count late))) 0.8) "the driver leads")))

(deftest relation-effects-in-raw-units
  ;; each relation input's effect on the log odds per raw unit: the models'
  ;; coefficients weighted by the model averaging, over the input's spread
  (let [g (m/rng 5)
        drive (into {} (for [c countries t (range months)] [[c t] (* 4.0 (m/normal! g))]))
        ys (into {} (for [c countries t (range months)] [[c t] (< (m/next-double! g) (m/expit (- (* 0.5 (drive [c t])) 2.0)))]))
        spec {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}]
              :levels [[:country countries]] :parent {} :start 0 :end 300 :forget 0.99
              :target (fn [_ place t] {:y (ys [place (inc t)]) :known-at (inc t)})
              :gauges (fn [_ _ _] [(m/normal! g)]) :gauge-width {:month 1}
              :relations (fn [_ place t] {:x [(drive [place (inc t)]) (m/normal! g)] :reads []})
              :relation-width {:month 2} :relation-groups {:month {:rel/driver [0] :rel/noise [1]}}}
        state (loop [s (lt/start spec)] (if (> (:t s) (:end spec)) s (recur (lt/step spec s))))
        [eff noise] (lt/relation-effects spec state :month 0)]
    (is (< 0.3 eff 0.7) "about the true 0.5 per raw unit (the driver's spread is 4)")
    (is (< (Math/abs noise) 0.1))))

(deftest relations-nested-in-the-node-s-own-model
  ;; :relation-nest: each relation group's model reads everything the
  ;; all-inputs model reads and its own inputs, and the all-inputs model
  ;; reads no relation: the averaging sets the node with a link against
  ;; the node without it, and a link adopted is one that forecasts better
  ;; given all else (the nested comparison; a link alone, without the
  ;; node's gauges, would never win)
  (let [g (m/rng 7)
        drive (into {} (for [c countries t (range months)] [[c t] (m/normal! g)]))
        gauge (into {} (for [c countries t (range months)] [[c t] (m/normal! g)]))
        ys (into {} (for [c countries t (range (dec months))]
                      [[c t] (< (m/next-double! g) (m/expit (- (+ (* 1.5 (gauge [c t])) (* 1.0 (drive [c (inc t)]))) 2.0)))]))
        spec {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}]
              :levels [[:country countries]] :parent {} :start 0 :end 400 :forget 0.99
              :target (fn [_ place t] {:y (ys [place t]) :known-at (inc t)})
              :gauges (fn [_ place t] [(gauge [place t])]) :gauge-width {:month 1}
              :relations (fn [_ place t] {:x [(drive [place (inc t)]) (m/normal! g)] :reads []})
              :relation-width {:month 2} :relation-groups {:month {:rel/driver [0] :rel/noise [1]}}
              :relation-nest true :shrink 0.5 :principal :auto}
        r (lt/run spec)
        ins (fn [grp] (set (:inputs (lt/model r :month :country grp))))
        rel-ix (set (mapcat #(get-in (lt/layout-of spec :month 0) [:parts %]) [:rel/driver :rel/noise]))
        ws (lt/weights r :month :country)]
    (testing "the models' inputs"
      (is (= 2 (count rel-ix)))
      (is (empty? (filter rel-ix (ins :all))) "the all-inputs model reads no relation")
      (is (contains? (ins :rel/driver) 1) "a link's model reads the gauges")
      (is (= (disj (ins :rel/driver) (first (get-in (lt/layout-of spec :month 0) [:parts :rel/driver])))
             (ins :all)) "and otherwise what the all-inputs model reads"))
    (is (> (get ws :rel/driver 0.0) 0.8) (str "the link that forecasts better is adopted " ws))
    (testing "the principal contradiction and the effects read a link's own inputs"
      (let [late (for [c countries [o e] (get-in r [:book [:month c]]) :when (> o 300)] (first (:principal e)))]
        (is (every? #{:rel/driver :rel/noise :contradiction} late)))
      (let [state (loop [s (lt/start spec)] (if (> (:t s) (:end spec)) s (recur (lt/step spec s))))
            [eff noise] (lt/relation-effects spec state :month 0)]
        (is (< 0.6 eff 1.4) "about the true 1.0 per raw unit")
        (is (< (Math/abs noise) 0.15))))))

(deftest unknown-relation-inputs-are-the-mean
  ;; a relation input the source does not know (nil) reaches the models as
  ;; 0 after standardizing and does not move the input's running mean
  (let [g (m/rng 9)
        drive (into {} (for [c countries t (range months)] [[c t] (m/normal! g)]))
        ys (into {} (for [c countries t (range (dec months))] [[c t] (< (m/next-double! g) (m/expit (- (* 1.5 (drive [c (inc t)])) 2.0)))]))
        spec {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}]
              :levels [[:country countries]] :parent {} :start 0 :end 300 :forget 0.99
              :target (fn [_ place t] {:y (ys [place t]) :known-at (inc t)})
              :gauges (fn [_ _ _] [0.0]) :gauge-width {:month 1}
              :relations (fn [_ place t] {:x [(when (odd? t) (+ 100.0 (drive [place (inc t)])))] :reads []})
              :relation-width {:month 1} :relation-groups {:month {:rel/d [0]}} :relation-nest true
              :trace :inputs}
        r (lt/run spec)
        rel-ix (first (get-in (lt/layout-of spec :month 0) [:parts :rel/d]))
        even (filter #(and (even? (:origin %)) (> (:origin %) 20)) (:trace r))
        odd (filter #(and (odd? (:origin %)) (> (:origin %) 20)) (:trace r))]
    (is (every? #(= 0.0 (nth (:x %) rel-ix)) even) "unknown: z = 0")
    (is (< (Math/abs (/ (reduce + (map #(nth (:x %) rel-ix) odd)) (count odd))) 0.3)
        "the known ones centred on their own mean, near 100, not dragged toward 0")))

(deftest a-run-keeps-each-node-s-standardizer
  ;; the counts of known values per input, so a check can tell an input
  ;; still waiting for ten known values from a dead one
  (let [r (lt/run (assoc (spec (world 1)) :end 60))]
    (is (map? (:flow-std r)))
    (is (vector? (get-in r [:flow-std [:month :country] :ns])))))

(deftest a-nested-group-is-not-coupled-across-levels
  ;; a nested relation group's model reads the node's own inputs (its
  ;; flows among them) and the link; the coupling shares gauge groups'
  ;; coefficients between levels, never the all-inputs model's, so it must
  ;; not share the nested model's either, or the averaging would set the
  ;; node with a link and the levels' coupling against the node without
  ;; both. A link that is never known (nil, z = 0) leaves the nested model
  ;; the all-inputs model exactly.
  (let [g (m/rng 21)
        x (into {} (for [p (concat countries ["A" "B"]) t (range months)] [[p t] (m/normal! g)]))
        y (into {} (for [[k v] x] [k (< (m/next-double! g) (m/expit (- (* 1.5 v) 1.0)))]))
        sp {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}]
            :levels [[:country countries] [:region ["A" "B"]]]
            :parent parent :start 0 :end 200 :forget 0.99 :coupling 0.2
            :without-at {:month #{:up :down :contradiction :turn :exposure :feedback}}
            :target (fn [_ place origin] {:y (y [place (inc origin)]) :known-at (inc origin)})
            :gauges (fn [_ place origin] [(x [place (inc origin)]) (m/normal! g)])
            :gauge-width {:month 2}
            :gauge-groups {:month {:credit [0] :noise [1]}}
            :relations (fn [_ _ _] {:x [nil nil] :reads []})
            :relation-width {:month 2} :relation-groups {:month {:rel/never [0 1]}} :relation-nest true}
        r (lt/run sp)]
    (doseq [level [:country :region]
            :let [all (augur.dynamic/means (lt/model r :month level :all))
                  nested (augur.dynamic/means (lt/model r :month level :rel/never))
                  ws (lt/weights r :month level)]]
      (is (= all (subvec nested 0 (count all))) (str level ": the nested model is the all-inputs model"))
      (is (< (Math/abs (- (ws :all) (ws :rel/never))) 1e-9) (str level " weights " ws)))))
