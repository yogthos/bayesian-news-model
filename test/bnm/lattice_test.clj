(ns bnm.lattice-test
  (:require [augur.dynamic]
            [augur.math :as m]
            [bnm.lattice :as lt]
            [bnm.world-term :as wt]
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
          :down (is (and (< o origin) (not (lt/finer? (spec w) s scale))) [scale place origin s o])
          ;; its own state: before it, at its own scale
          :self (is (and (< o origin) (= s scale)) [scale place origin s o])))
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
    ;; the errors fed across levels carry nothing the contradiction does not
    ;; (an earlier assertion that they added 0.02 read the inputs one place
    ;; off: the parent map ran to a world the spec has no level for)
    (testing "errors fed across levels are no substitute for it"
      (is (< (Math/abs (- full (score #{:feedback}))) 0.03))
      (is (> full (+ 0.1 (score #{:contradiction :feedback})))))))

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

(deftest bnm-56c-26-a-node-with-nothing-resolved-reads-the-prior-rate
  ;; with :clim-prior, a node with no resolved outcome takes its climatology
  ;; from the prior's counts ([n1 n], the other kinds' at the node's scale
  ;; and level in bnm.world-run), not an even 0.5; once its own outcomes
  ;; resolve, its own rate as before
  (let [g (m/rng 21)
        ys (into {} (for [c countries t (range 400)] [[c t] (< (m/next-double! g) 0.3)]))
        base {:scales [{:name :year :per 12 :horizon 24 :discount 0.99}]
              :levels [[:country countries]]
              :parent {} :start 0 :end 240 :forget 0.99
              :offset (fn [_ _ _] nil)
              :target (fn [_ place t] {:y (ys [place (+ t 24)]) :known-at (+ t 24)})
              :gauges (fn [_ _ _] [])
              :gauge-width {:year 0}}
        early (fn [r] (filter #(< (:origin %) 24) (:forecasts r)))
        late (fn [r] (filter #(> (:origin %) 200) (:forecasts r)))
        mean-p (fn [fs] (/ (reduce + (map :p fs)) (count fs)))
        seen (atom #{})
        prior (lt/run (assoc base :clim-prior (fn [scale level] (swap! seen conj [scale level]) [10 100])))
        high (lt/run (assoc base :clim-prior (fn [_ _] [90 100])))
        none (lt/run base)
        empty-prior (lt/run (assoc base :clim-prior (fn [_ _] [0 0])))
        rate (/ (count (filter true? (vals ys))) (double (count ys)))]
    (is (< (Math/abs (- (mean-p (early none)) 0.5)) 0.01) "without the option, the even rate as before")
    ;; the issued p is the models' prior predictive about the offset (their
    ;; intercepts uncertain), so it sits between the prior rate and 0.5
    (is (< 0.104 (mean-p (early prior)) 0.25) (mean-p (early prior)))
    (is (< 0.75 (mean-p (early high)) 0.9) (mean-p (early high)))
    (is (= #{[:year :country]} @seen) "read at the node's scale and level")
    (is (< (Math/abs (- (mean-p (early empty-prior)) 0.5)) 0.01) "an empty prior is no prior")
    (doseq [r [prior high none]]
      (is (< (Math/abs (- (mean-p (late r)) rate)) 0.1) "its own outcomes once they resolve"))))

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
      ;; and, never known anywhere, it carries no weight (a group without inputs drops out)
      (is (= 0.0 (ws :rel/never)) (str level " weights " ws)))))

(deftest an-error-not-yet-known-is-unknown-not-zero
  ;; before a node's outcomes begin (a catalogue that starts late) there is
  ;; no error to feed back: the input is unknown, standardized to the mean
  ;; and left out of the running spread, not a perfect forecast of 0 that
  ;; shrinks the spread until the first real error lands at the cap
  (let [w (world 5)
        sp (assoc (spec w) :end 359 :trace true
                  :target (let [t0 (:target (spec w))]
                            (fn [scale place origin] (when (>= origin 240) (t0 scale place origin)))))
        r (lt/run sp)
        lay (lt/layout-of sp :year 0)
        j (- (first (get-in lay [:groups :feedback])) 2)
        {:keys [n ns]} (get-in r [:flow-std [:year :country]])
        fed (count (filter (fn [{:keys [scale level reads]}]
                             (and (= :year scale) (= :country level) (some #(= :feedback (:dir %)) reads)))
                           (:trace r)))]
    (is (pos? fed) "the countries read their regions' errors once known")
    (is (< fed n))
    (is (= fed (nth ns j)) "only the known errors count toward the spread")))

(deftest a-group-without-inputs-carries-no-weight
  ;; a source that ends (a dataset that stops, a signal gone stale) leaves
  ;; its group reading zeros, the unknown's mean, at every place: an
  ;; intercept-only model under another name. While it has no input it
  ;; drops out of the averaging, weight 0, and comes back when its source
  ;; resumes
  (let [w (world 6)
        gone? (fn [t] (<= 200 t 299))
        sp (assoc (spec w) :end 400
                  :gauge-width {:month 2 :year 1}
                  :gauges (fn [scale place origin]
                            (if (= scale :month)
                              [(if (= 2 (count place)) (get-in w [:gauge place origin]) 0.3)
                               (if (gone? origin) 0.0 ((:noise w)))]
                              [((:noise w))]))
                  :gauge-groups {:month {:signal [0] :faded [1]}})
        r (lt/run sp)
        hist (into (sorted-map) (get-in r [:weights [:month :country]]))]
    (is (every? #(pos? (get-in hist [% :faded])) (range 20 200)) "weighted while it reads something")
    (is (every? #(= 0.0 (get-in hist [% :faded])) (range 200 300)) "no weight while it reads nothing")
    (is (every? #(< (Math/abs (- 1.0 (reduce + (vals (hist %))))) 1e-9) (range 200 300)) "the others' weights renormalized")
    (is (pos? (get-in hist [320 :faded])) "back when its source resumes")
    (testing "a group known at some places is not dropped"
      (is (every? #(pos? (get-in hist [% :signal])) (range 20 400))))))

(deftest the-averaging-s-weighting-is-opt-in-and-by-scale
  ;; bnm-56c.2: posterior weights by default; :weighting for every node,
  ;; :weighting-at for one scale
  (let [run #(lt/run (merge (spec (world 7)) %))
        plain (run {})
        one (run {:weighting {:rule :temper :power 1.0}})
        stacked (run {:weighting-at {:year {:rule :stack :rate 0.02 :floor 0.1}}})
        n (count (lt/weights stacked :year :country))]
    (is (= (:forecasts plain) (:forecasts one)) "a power of one is the default")
    (is (= :stack (get-in stacked [:models [:year :country] :weighting :rule])))
    (is (nil? (get-in stacked [:models [:month :country] :weighting])) "only at the scale named")
    (is (every? #(>= % (- (/ 0.1 n) 1e-12)) (vals (lt/weights stacked :year :country))))
    (is (not= (:forecasts plain) (:forecasts stacked)))))

(deftest a-link-s-coefficients-can-be-held
  ;; :hold-links: a nested link's own coefficients are not discounted, the
  ;; rest of its model is; without it every coefficient is
  (let [spec {:scales [{:name :month :per 1 :horizon 1 :discount 0.9}]
              :levels [[:country countries]] :parent {} :start 0 :end 30 :forget 0.99
              :target (fn [_ place t] {:y (odd? (+ t (count place))) :known-at (inc t)})
              :gauges (fn [_ _ _] [0.0]) :gauge-width {:month 1}
              :relations (fn [_ _ _] {:x [0.0] :reads []})
              :relation-width {:month 1} :relation-groups {:month {:rel/x [0]}}
              :relation-nest true :shrink 0.5}
        lay (lt/layout-of spec :month 0)
        link (first (get-in lay [:parts :rel/x]))
        held (lt/run (assoc spec :hold-links true))
        plain (lt/run spec)
        var-of (fn [r grp i] (let [mdl (lt/model r :month :country grp)
                                   pos (.indexOf ^java.util.List (vec (:inputs mdl)) i)
                                   k (:k mdl)]
                               (aget ^doubles (:c mdl) (+ (* pos k) pos))))]
    ;; the link reads 0 everywhere: no row informs it, only the discount moves it
    (is (< (Math/abs (- (var-of held :rel/x link) 0.5)) 1e-9) "held: its prior variance")
    (is (> (var-of plain :rel/x link) 5.0) "discounted: grown")
    (is (< (Math/abs (- (var-of held :rel/x 0) (var-of plain :rel/x 0))) 1e-12) "the rest as before")
    (is (every? true? (map #(< (Math/abs (- (:p %1) (:p %2))) 1e-12) (:forecasts held) (:forecasts plain)))
        "an input that is 0 everywhere moves nothing")))

(deftest a-finer-state-can-leave-out-what-came-down
  ;; :up-net-of-downscale (bnm-56c.2): a node whose offset comes from a
  ;; coarser forecast passes up its log odds less the coarser part of that
  ;; offset (pooled with what is known, that part's share), so the coarser
  ;; node does not read its own forecast back through it; its forecast is
  ;; unchanged
  (let [w (world 1)
        base (assoc (spec w) :trace :inputs :downscale {:month {:from :year}} :downscale-pool 0.5
                    :offset (fn [scale _ _] (when (= scale :month) -3.0)))
        plain (lt/run base)
        net (lt/run (assoc base :up-net-of-downscale true))
        latest (fn [r place t] (some-> (get-in r [:book [:year place]]) (rsubseq < t) first val))
        months (fn [r] (for [[[s place] byo] (:book r) :when (= :month s) [t e] byo :when (> t 24)] [place t e]))]
    (testing "the month's own forecast at the first origin is the same"
      (is (= (first (:forecasts plain)) (first (:forecasts net)))))
    (testing "its state is its log odds less half the year's scaled forecast"
      (is (seq (months net)))
      (is (every? (fn [[place t {:keys [p s]}]]
                    (let [y (latest net place t)
                          down (* 0.5 (lt/window-log-odds (:p y) (/ 1.0 12.0)))]
                      (< (Math/abs (- s (- (lt/logit-of p) down))) 1e-9)))
                  (months net))))
    (testing "where nothing comes down, the state is the log odds"
      (is (every? (fn [[_ t {:keys [p s]}]] (or (> t 24) (< (Math/abs (- s (lt/logit-of p))) 1e-9)))
                  (for [[[sc place] byo] (:book net) :when (= :month sc) [t e] byo :when (<= t 11)] [place t e]))))
    (is (not= (:forecasts plain) (:forecasts net)) "the year reads other states, so the forecasts move")))

(deftest what-came-down-is-measured-at-the-offset-the-state-is-read-at
  ;; review of step 1, finding 6: under :downscale-uncertainty the node's
  ;; mean offset is logit(Σ w expit(o_i)), not Σ w o_i; the part that came
  ;; down is that mean offset less the known part's share, so the state
  ;; passed up (the combined forecast's, or the reference's) is net of
  ;; exactly what came down, with no Jensen gap
  (doseq [extra [{} {:state-from :reference}]]
    (let [w (world 1)
          r (lt/run (merge (spec w) {:trace :inputs :downscale {:month {:from :year}} :downscale-pool 0.5
                                     :downscale-uncertainty true :up-net-of-downscale true
                                     :offset (fn [scale _ _] (when (= scale :month) -3.0))}
                           extra))
          rows (filter #(and (= :month (:scale %)) (> (:origin %) 24)) (:trace r))]
      (is (seq rows))
      (is (every? (fn [{:keys [place origin o]}]
                    (let [{:keys [s p]} (get-in r [:book [:month place] origin])]
                      (< (Math/abs (- s (- (lt/logit-of p) (- o (* 0.5 -3.0))))) 1e-9)))
                  rows)
          extra))))

(deftest the-finer-states-can-be-read-a-period-late
  ;; :up-lag (bnm-56c.2's loop): a node reads its finer scales' states over
  ;; the period before its last, not its last, so the quarters a year's own
  ;; forecast came down to are not read back the next period; its children
  ;; at its own scale are read as before
  (let [w (world 5)
        reads-of (fn [r] (for [{:keys [scale origin reads]} (:trace r) :when (= :year scale)
                               {s :scale o :origin dir :dir} reads :when (and (= :up dir) (= :month s))]
                           [origin o]))
        plain (lt/run (assoc (spec w) :end 200 :trace true))
        lagged (lt/run (assoc (spec (world 5)) :end 200 :trace true :up-lag {:year 1}))]
    (is (some (fn [[t o]] (> o (- t 12))) (reads-of plain)) "without it, this period's months")
    (is (seq (reads-of lagged)))
    (is (every? (fn [[t o]] (<= (- t 23) o (- t 12))) (reads-of lagged)) "with it, the period before")
    (is (not= (:forecasts plain) (:forecasts lagged)))))

(deftest the-weights-can-have-a-prior
  ;; :weight-prior (bnm-56c.2): the averaging starts from the shares named
  ;; and fades toward them; without it, equal weights
  (let [run #(lt/run (merge (spec (world 6)) %))
        plain (run {})
        favoured (run {:weight-prior {:base 0.9}})
        first-ws (fn [r] (second (first (get-in r [:weights [:year :country]]))))]
    (is (= (:forecasts plain) (:forecasts (run {:weight-prior {}}))) "an empty prior is none")
    (is (> (:base (first-ws favoured)) 0.85) (first-ws favoured))
    (is (not= (:forecasts plain) (:forecasts favoured)))))

(deftest evidence-weighting-sets-links-against-the-node-without-them
  ;; bnm-56c.2's ranked increments (augur's :evidence): the base model is the
  ;; reference, a nested link's model is set against the all-inputs model
  (let [spec {:scales [{:name :month :per 1 :horizon 1 :discount 0.9}]
              :levels [[:country countries]] :parent {} :start 0 :end 40 :forget 0.99
              :target (fn [_ place t] {:y (odd? (+ t (count place))) :known-at (inc t)})
              :gauges (fn [_ _ _] [0.0]) :gauge-width {:month 1}
              :relations (fn [_ place t] {:x [(if (odd? (+ t (count place))) 1.0 -1.0)] :reads []})
              :relation-width {:month 1} :relation-groups {:month {:rel/x [0]}}
              :relation-nest true}
        r (lt/run (assoc spec :weighting {:rule :evidence :scale 0.005}))
        wt (get-in r [:models [:month :country] :weighting])
        ws (lt/weights r :month :country)]
    (is (= :base (:base wt)))
    (is (= {:rel/x :all} (:parents wt)))
    (is (< (Math/abs (- 1.0 (reduce + (vals ws)))) 1e-9))
    (is (> (:rel/x ws) 0.5) "a link that reads the outcome earns most of the weight")))

(deftest group-shrinkage-on-one-model
  ;; bnm-56c.2 (B): one all-inputs model, each group of its inputs shrunk by
  ;; its own scale (augur's :group-shrink), the scales recorded beside the
  ;; weights; opt-in
  (let [run #(lt/run (merge (spec (world 8)) {:only #{:all}} %))
        plain (run {})
        shrunk (run {:group-shrink {:tau 0.5}})
        lay (lt/layout-of (spec (world 8)) :year 0)
        mdl (lt/model shrunk :year :country :all)
        pos (zipmap (:inputs mdl) (range))
        ws (lt/weights shrunk :year :country)]
    (is (nil? (:gshrink (lt/model plain :year :country :all))))
    (is (= (set (for [[g ix] (:groups lay) :when (and (not (#{:base :all} g)) (seq ix))] (set (map pos ix))))
           (set (map set (get-in mdl [:gshrink :groups])))))
    (is (= 1.0 (:all ws)))
    (is (seq (filter #(= "scale" (namespace %)) (keys ws))) ws)
    (is (not= (:forecasts plain) (:forecasts shrunk)))))

;; Loop audit, Round 1 (bnm-56c): the node's combined forecast must not
;; feed its own models. Every route a node's state comes back by is on:
;; its own state, :up through the months' offset from the year (pooled
;; with what is known), :down from its ancestors and coarser scales,
;; exposure, and the feedback errors.
(defn- loop-spec [w]
  (assoc (spec w) :trace :inputs
         :downscale {:month {:from :year}} :downscale-pool 0.5 :downscale-uncertainty true
         :offset (fn [scale _ _] (when (= scale :month) -2.0))
         :exposure (fn [_ place t] [(Math/sin (/ (+ t (count place)) 7.0))]) :exposure-width 1
         :coupling 0.3))

(defn- by-row [r] (into {} (for [{:keys [scale place origin x o]} (:trace r)] [[scale place origin] (cons o x)])))

(defn- book-states [r]
  (into {} (for [[cell byo] (:book r) [t e] byo] [[cell t] [(:s e) (:p e) (:f e) (:q e)]])))

(defn- max-gap
  "The largest difference between two runs' traced inputs (or another
  {key [numbers]} view of them)."
  ([a b] (max-gap by-row a b))
  ([view a b]
   (let [a (view a) b (view b)]
     (assert (= (set (keys a)) (set (keys b))))
     (reduce max 0.0 (for [[k va] a d (map - va (b k))] (Math/abs (double d)))))))

(def ^:private tempered {:weighting {:rule :temper :power 0.01}})

(defn- loop-run [extra] (lt/run (merge (loop-spec (world 7)) {:end 300} extra)))

(deftest c7-states-and-inputs-are-the-same-under-any-weighting
  (testing "today the weighting changes the inputs its models read"
    (is (> (max-gap (loop-run {}) (loop-run tempered)) 1e-3)))
  (testing "with states from the reference model, every model reads the same inputs"
    (let [a (loop-run {:state-from :reference})
          b (loop-run (merge tempered {:state-from :reference}))]
      (is (< (max-gap a b) 1e-12) (max-gap a b))
      (is (< (max-gap book-states a b) 1e-12) (max-gap book-states a b))
      (is (not= (map :p (:forecasts a)) (map :p (:forecasts b))) "the combination is still what is issued")
      (testing "the book keeps the state apart from the issued forecast"
        (let [rows (filter #(and (= :year (:scale %)) (= :country (:level %))) (:trace a))
              {:keys [place origin x o]} (last rows)
              e (get-in a [:book [:year place] origin])
              issued (some #(when (and (= :year (:scale %)) (= place (:place %)) (= origin (:origin %))) (:p %)) (:forecasts a))]
          (is (= issued (get-in e [:issued :p])))
          (is (< (Math/abs (- (:s e) (lt/logit-of (:p e)))) 1e-12))
          (is (< (Math/abs (- (:p e) (augur.dynamic/predict (lt/model a :year :country :all) x o))) 1e-12))))))
  (testing "another reference gives other states"
    (is (> (max-gap (loop-run {:state-from :reference :end 200}) (loop-run {:state-from :base :end 200})) 1e-3)))
  (testing ":state-from is pluggable: a function of the node and its models gives its state, an open map"
    (let [by-fn (loop-run {:state-from (fn [{:keys [models x o os]}]
                                         (let [st (lt/model-state (:all models) x o os)]
                                           (assoc st :s (lt/logit-of (:p st)) :z 1.0)))})
          by-key (loop-run {:state-from :reference})]
      (is (< (max-gap by-fn by-key) 1e-12))
      (is (= (map :p (:forecasts by-fn)) (map :p (:forecasts by-key))))
      (is (every? #(= 1.0 (:z %)) (mapcat vals (vals (:book by-fn)))) "what else it returns is kept")))
  (is (thrown? clojure.lang.ExceptionInfo (loop-run {:state-from :nope :end 20})) "a reference the node does not average"))

(deftest c6-no-node-reads-its-own-issued-forecast
  ;; with the issued forecasts made as different as the weighting can make
  ;; them (nearly even weights against the posterior's selection), every
  ;; state, every error fed back and so every read is the same: nothing a
  ;; node reads is its issued forecast
  (let [a (loop-run {:state-from :reference})
        b (loop-run {:state-from :reference :weighting {:rule :temper :power 1e-6}})
        errs (fn [r] (into {} (for [{:keys [scale place origin y]} (:forecasts r) :when (some? y)]
                                [[scale place origin] [(- (if y 1.0 0.0) (get-in r [:book [scale place] origin :p]))]])))]
    (is (> (reduce max (map #(Math/abs (- (:p %1) (:p %2))) (:forecasts a) (:forecasts b))) 0.01) "the issued forecasts differ")
    (is (< (max-gap book-states a b) 1e-12))
    (is (< (max-gap errs a b) 1e-12))
    (is (< (max-gap a b) 1e-12))))

(deftest the-own-state-read-is-recorded
  ;; issue 19: the node's read of its own last state goes through the sheaf
  ;; (latest-before, :self), so the read audit sees the loop
  (let [r (lt/run (assoc (spec (world 2)) :end 60 :trace true))]
    (is (every? (fn [{:keys [scale place origin reads]}]
                  (let [own (filter #(= :self (:dir %)) reads)
                        before (some-> (get-in r [:book [scale place]]) (rsubseq < origin) first key)]
                    (if before
                      (= [{:dir :self :scale scale :place place :origin before}] (vec own))
                      (empty? own))))
                (:trace r)))
    (is (some (fn [{:keys [reads]}] (some #(= :self (:dir %)) reads)) (:trace r)))))

(defn- late-run
  "The synthetic run with the year's outcomes learned two years after its
  next period, as the real year rows are (learned at once, the state that
  scores a row is the one that issued it)."
  [extra]
  (let [sp (spec (world 3))
        late (fn [s p o] (when-let [tg ((:target sp) s p o)] (cond-> tg (= s :year) (update :known-at + 24))))]
    (lt/run (merge sp {:end 300 :target late} extra))))

(deftest c9-evidence-can-be-scored-as-issued
  ;; issue 2: each pending row carries every model's predictive at its
  ;; origin (:lp); with :evidence-at :issue the averaging scores it with that
  (let [learn (late-run {})
        issue (late-run {:evidence-at :issue :trace :inputs})]
    (is (= :issue (get-in issue [:models [:year :country] :evidence-at])))
    (is (nil? (get-in learn [:models [:year :country] :evidence-at])))
    (is (not= (lt/weights learn :year :country) (lt/weights issue :year :country)))
    (is (= (take 6 (:forecasts learn)) (take 6 (:forecasts issue))) "the same until a row is learned")
    (testing "each model's evidence is the log score of the forecast it issued"
      (let [ys (into {} (for [{:keys [scale place origin y]} (:forecasts issue)] [[scale place origin] y]))
            last-t (apply max (map :origin (filter #(= :year (:scale %)) (:forecasts issue))))
            learned (for [{:keys [scale level place origin lp]} (:trace issue)
                          :when (and (= :year scale) (= :country level) (some? (ys [:year place origin]))
                                     (<= (+ origin 36) last-t))]
                      [(ys [:year place origin]) lp])
            score (fn [g] (reduce + (for [[y lp] learned
                                          :let [{:keys [f q]} (lp g)
                                                p (m/expit (/ f (Math/sqrt (+ 1.0 (/ (* Math/PI q) 8.0)))))]]
                                      (Math/log (if y p (- 1.0 p))))))
            [ll n] (get-in issue [:evidence [:year :country] :all])]
        (is (= n (count learned)))
        (is (< (Math/abs (- ll (score :all))) 1e-9) [ll (score :all)])
        (is (< (Math/abs (- (first (get-in issue [:evidence [:year :country] :base])) (score :base))) 1e-9))))))

(defn- lp-p
  "The probability an issued predictive {:f :q} scores an onset at."
  [{:keys [f q]}]
  (m/expit (/ f (Math/sqrt (+ 1.0 (/ (* Math/PI q) 8.0))))))

(deftest c9-the-issued-predictive-is-the-forecast-issued
  ;; Round 1 review: under :downscale-uncertainty a month node issues the
  ;; mixture over the year's offsets, so each model's :lp must score that
  ;; mixture, not the one mean offset; weighted by the node's weights, the
  ;; models' lp give back the p it issued
  (let [r (loop-run {:state-from :reference :evidence-at :issue :end 120})
        ws (into {} (for [[k hist] (:weights r) [t w] hist] [[k t] w]))
        rows (filter #(and (:lp %) (#{:month :year} (:scale %))) (:trace r))
        gap (fn [{:keys [scale level origin p lp]}]
              (let [w (ws [[scale level] origin])]
                (Math/abs (- p (reduce + (for [[g pr] lp] (* (get w g 0.0) (lp-p pr))))))))]
    (is (some #(= :month (:scale %)) rows))
    (is (< (reduce max (map gap (filter #(= :month (:scale %)) rows))) 1e-9) "month nodes, offsets mixed")
    (is (< (reduce max (map gap (filter #(= :year (:scale %)) rows))) 1e-9) "year nodes, one offset")))

(deftest the-state-from-options-compose
  (testing ":up-net-of-downscale applies to a reference state: its log odds less what came down"
    (let [a (loop-run {:state-from :reference :end 60})
          b (loop-run {:state-from :reference :up-net-of-downscale true :end 60})
          month (fn [r] (for [[[s _] byo] (:book r) :when (= :month s) [t e] byo] [t e]))]
      (is (every? (fn [[_ e]] (< (Math/abs (- (:s e) (lt/logit-of (:p e)))) 1e-12)) (month a)) "without it, the state's log odds")
      (is (some (fn [[_ e]] (> (Math/abs (- (:s e) (lt/logit-of (:p e)))) 1e-6)) (month b))
          "with it, the log odds less the offset that came down")))
  (testing ":state-from :reference needs the all-inputs model"
    (let [e (try (loop-run {:state-from :reference :only #{:base :gauges} :end 20}) nil
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (some? e))
      (is (re-find #":all" (str (ex-message e)))))))

;; -- recursive step 2: the world state at the year node (loop audit Round 2) --------

(def ^:private wcountries (mapv #(str (if (< % 6) "a" "b") %) (range 12)))
(def ^:private wparent (merge (into {} (for [c wcountries] [c (str (first c))])) {"a" "W" "b" "W"}))

(defn- momentum-world
  "World price momentum in blocks of 24 months at a random level; every
  country's chance of an onset in the year after t rises with the
  momentum known at t, plus a little country noise."
  [seed]
  (let [g (m/rng seed)
        blocks (vec (repeatedly (inc (quot months 24)) #(- (* 3.0 (m/next-double! g)) 1.5)))
        mom (mapv (fn [t] (+ (blocks (quot t 24)) (* 0.2 (m/normal! g)))) (range months))
        crisis (into {} (for [c wcountries]
                          [c (into {} (for [t (range 11 months 12)]
                                        [t (< (m/next-double! g) (m/expit (+ -2.0 (* 1.8 (mom t)) (* 0.2 (m/normal! g)))))]))]))]
    {:mom mom :crisis crisis :noise (fn [] (m/normal! g))}))

(defn- world-gauge-spec [w]
  {:scales [{:name :year :per 12 :horizon 12 :discount 0.995}]
   :levels [[:country wcountries] [:region ["a" "b"]]]
   :parent wparent :start 0 :end (dec months) :forget 0.99
   :target (fn [_ place t]
             (let [cs (if (> (count place) 1) [place] (filter #(= place (wparent %)) wcountries))
                   ys (keep #(get-in w [:crisis % t]) cs)]
               (when (and (seq ys) (< (+ t 12) months)) {:y (boolean (some true? ys)) :known-at (+ t 12)})))
   :gauges (fn [_ _ _] [((:noise w))])
   :gauge-width {:year 1}})

(defn- with-world-group [sp w]
  (assoc sp :world-gauges (fn [_ place t] {:x [((:mom w) t) (* (if (= \a (first place)) 1.0 0.5) ((:mom w) t))]
                                           :reads [{:dir :world :series :mom :period t :known-at t}]})
         :world-width {:year 2}))

(defn- brier [fs] (let [fs (filter #(some? (:y %)) fs)] (/ (reduce + (map (fn [{:keys [p y]}] (let [d (- (if y 1.0 0.0) p)] (* d d))) fs)) (count fs))))

(deftest test-3a-the-year-node-tracks-a-planted-world-state
  ;; Without the world group the year node cannot track the world's level
  ;; (its own gauges are noise); with it, entered in the all-inputs model
  ;; (which the reference reads), it must, by a fixed margin
  (let [w (momentum-world 21)
        late (fn [r] (filter #(and (> (count (:place %)) 1) (> (:origin %) 240)) (:forecasts r)))]
    (doseq [extra [{} {:state-from :reference :evidence-at :issue}]]
      (let [sp (merge (world-gauge-spec w) extra)
            without (lt/run sp)
            with (lt/run (with-world-group sp w))]
        (is (< (+ (brier (late with)) 0.01) (brier (late without))) [extra (brier (late with)) (brier (late without))])
        (is (> (+ (get (lt/weights with :year :country) :world-gauges 0.0) (get (lt/weights with :year :country) :all 0.0)) 0.5)
            (lt/weights with :year :country))))
    (testing "the group is read by the all-inputs model, so a reference state carries it"
      (let [lay (lt/layout-of (with-world-group (world-gauge-spec w) w) :year 0)]
        (is (= 2 (count (get-in lay [:groups :world-gauges]))))
        (is (every? (set (get-in lay [:groups :all])) (get-in lay [:groups :world-gauges])))))
    (testing "its reads are the node's reads, none after the origin"
      (let [r (lt/run (assoc (with-world-group (world-gauge-spec w) w) :trace true :end 120))
            wr (for [{:keys [origin reads]} (:trace r) rd reads :when (= :world (:dir rd))] [origin rd])]
        (is (seq wr))
        (is (every? (fn [[o rd]] (<= (:known-at rd) o)) wr))))
    (testing "off, the layout is the one without it"
      (is (= (lt/layout-of (world-gauge-spec w) :year 0)
             (lt/layout-of (assoc (world-gauge-spec w) :world-width {:year 0}) :year 0))))))

;; -- Test 4: a quarter can depart from a stale year offset --------------------------

(defn- stale-offset-world
  "A world price that jumps to a new level every 60 months; each month's
  onsets, in twelve countries, follow the price now. The year node sees
  the price a year late, so after each jump its forecast, the months'
  offset, is stale for a year."
  [seed]
  (let [g (m/rng seed)
        blocks (vec (repeatedly (inc (quot months 60)) #(- (* 3.0 (m/next-double! g)) 1.5)))
        price (mapv #(blocks (quot % 60)) (range months))
        events (into {} (for [c wcountries]
                          [c (mapv (fn [t] (< (m/next-double! g) (m/expit (+ -3.0 (* 1.5 (price t)))))) (range months))]))]
    {:price price :events events}))

(defn- stale-spec [w]
  {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}
            {:name :year :per 12 :horizon 12 :discount 0.995}]
   :levels [[:country wcountries]]
   :parent {} :start 0 :end (dec months) :forget 0.99
   :target (fn [scale place t]
             (case scale
               :month (when (< (inc t) months) {:y (get-in w [:events place (inc t)]) :known-at (inc t)})
               :year (when (< (+ t 12) months)
                       {:y (boolean (some #(get-in w [:events place %]) (range (inc t) (+ t 13)))) :known-at (+ t 12)})))
   :gauges (fn [scale _ t] [(if (= scale :month) ((:price w) t) ((:price w) (max 0 (- t 12))))])
   :gauge-width {:month 1 :year 1}
   :offset (fn [scale _ _] (when (= scale :month) -3.0))
   :downscale {:month {:from :year}} :downscale-pool 0.5 :downscale-uncertainty true})

(deftest test-4-a-quarter-departs-from-a-stale-offset
  ;; :downscale-learn: the pool weight on the year's offset is learned (a
  ;; core input, read by every model) instead of fixed at 0.5, so after a
  ;; jump that only the finer node's gauge sees it can leave the stale
  ;; year. Measured, not large: over three worlds the months in the 15
  ;; after each jump gain +0.0018 nats a row on average (one world -0.0002)
  (let [after-shift (fn [r] (filter #(and (= :month (:scale %)) (> (:origin %) 240) (< (mod (:origin %) 60) 15)) (:forecasts r)))
        runs (vec (for [seed [5 6 7]
                        :let [w (stale-offset-world seed)]]
                    [(lt/run (stale-spec w)) (lt/run (assoc (stale-spec w) :downscale-learn {:var 1.0}))]))
        gains (for [[fixed learned] runs] (- (log-score (after-shift learned)) (log-score (after-shift fixed))))
        [_ learned] (first runs)]
    (is (> (/ (reduce + gains) 3.0) 0.001) (vec gains))
    (testing "the pool input is core: every model reads it"
      (let [pool (:pool (lt/layout-of (assoc (stale-spec (stale-offset-world 5)) :downscale-learn {:var 1.0}) :month 0))]
        (is (= 1 (count pool)))
        (is (every? (fn [g] (some #{(first pool)} (:inputs (lt/model learned :month :country g))))
                    (keys (get-in learned [:models [:month :country] :models]))))))
    (testing "only where a coarser node comes down"
      (is (empty? (:pool (lt/layout-of (assoc (stale-spec (stale-offset-world 5)) :downscale-learn {:var 1.0}) :year 0)))))))

;; -- bnm-56c.22: learning at the offsets issued, not their mean ----------------------

(deftest learning-rows-over-the-offset-mixture
  (testing "a row issued over several offsets is learned at each, weighted"
    (is (= [{:x [1.0] :y true :o -1.0 :w 0.25 :issued {:a 1}} {:x [1.0] :y true :o 1.0 :w 0.75 :issued {:a 1}}]
           (lt/learning-rows [{:x [1.0] :y true :o 0.3 :os [[0.25 -1.0] [0.75 1.0]] :issued {:a 1}}] true)))
    (is (= [{:x [1.0] :y false :o 0.3}] (lt/learning-rows [{:x [1.0] :y false :o 0.3}] true)) "one offset: as it is")
    (is (= [{:x [1.0] :y true :o 0.3 :issued {:a 1}}]
           (lt/learning-rows [{:x [1.0] :y true :o 0.3 :os [[0.25 -1.0] [0.75 1.0]] :issued {:a 1}}] false))
        "off: the mean offset, as the base learns"))
  (let [w (world 1)
        sp (assoc (spec w) :downscale {:month {:from :year}} :downscale-pool 0.5 :downscale-uncertainty true
                  :offset (fn [scale _ _] (when (= scale :month) -3.0)) :end 200)
        a (lt/run sp)
        b (lt/run (assoc sp :learn-offsets :mixture))]
    (is (not= (:forecasts a) (:forecasts b)) "the months learn other coefficients")
    (is (= (filter #(= :year (:scale %)) (take 30 (:forecasts a))) (filter #(= :year (:scale %)) (take 30 (:forecasts b)))))))

;; -- fix 2: states that hold when the loop is cut ------------------------------------
;; Under :state-from :reference a node passes on the all-inputs model's
;; predictive. With many inputs learned from few origins that model's
;; linear predictor is so uncertain that its predictive sits near 0.5 and
;; swings as the uncertainty falls (the food year node in 2005-2010: a
;; variance of 20-58 in log odds). The synthetic world: a planted world
;; momentum the year node reads, twenty gauges that are the same at every
;; place and carry nothing, rows learned three years late.

(defn- swing-world [seed]
  (let [g (m/rng seed)
        blocks (vec (repeatedly (inc (quot months 24)) #(- (* 3.0 (m/next-double! g)) 1.5)))
        mom (mapv (fn [t] (+ (blocks (quot t 24)) (* 0.2 (m/normal! g)))) (range months))
        tnoise (vec (for [_ (range months)] (vec (repeatedly 21 #(m/normal! g)))))
        crisis (into {} (for [c wcountries]
                          [c (into {} (for [t (range 11 months 12)]
                                        [t (< (m/next-double! g) (m/expit (+ -2.5 (* 1.2 (mom t)) (* 0.2 (m/normal! g)))))]))]))]
    {:mom mom :crisis crisis :tnoise tnoise}))

(defn- swing-spec [w extra?]
  {:scales [{:name :year :per 12 :horizon 12 :discount 0.998}]
   :levels [[:country wcountries] [:region ["a" "b"]] [:world ["W"]]]
   :parent wparent :start 0 :end (dec months) :forget 0.995 :shrink 0.1
   :target (fn [_ place t]
             (let [cs (cond (= place "W") wcountries (> (count place) 1) [place] :else (filter #(= place (wparent %)) wcountries))
                   ys (keep #(get-in w [:crisis % t]) cs)]
               (when (and (seq ys) (< (+ t 12) months)) {:y (boolean (some true? ys)) :known-at (+ t 36)})))
   :gauges (fn [_ _ t] (subvec ((:tnoise w) t) 0 20))
   :gauge-width {:year 20}
   ;; the planted momentum, and with extra? one more column that carries nothing
   :world-gauges (fn [_ _ t] {:x (cond-> [((:mom w) t)] extra? (conj (get-in w [:tnoise t 20]))) :reads []})
   :world-width {:year (if extra? 2 1)}
   :state-from :reference :evidence-at :issue})

(defn- swing-term
  "The planted momentum entered once per origin (bnm.world-term, fix 3),
  with the extra column in the term when extra?: the countries' offset."
  [w extra?]
  (let [term (wt/term-fn {:gauges (fn [t] (cond-> [((:mom w) t)] extra? (conj (get-in w [:tnoise t 20]))))
                          :origins (vec (range 11 months 12))
                          :outcome (fn [t] (when (< (+ t 12) months)
                                             {:share (/ (count (filter #(get-in w [:crisis % t]) wcountries)) (double (count wcountries)))
                                              :n (count wcountries) :known-at (+ t 36)}))})]
    {:world-offset (fn [_ place t] (when (= 2 (count place)) {:x (:x (term t)) :reads []}))}))

(defn- swing-measures
  "Over origins from year 10, the countries' states: their mean distance
  from the planted log odds, the correlation of their mean with it, and the
  mean change one extra column that carries nothing makes, in the
  all-inputs model's world group (:noise) and, with `term?`, in the world
  term (:noise-term)."
  [extra seeds & [term?]]
  (let [ms (for [seed seeds
                 :let [w (swing-world seed)
                       with-term (fn [sp extra?] (cond-> sp term? (merge (swing-term w extra?))))
                       a (lt/run (merge (with-term (swing-spec w false) false) extra))
                       b (lt/run (merge (with-term (swing-spec w true) false) extra))
                       c (when term? (lt/run (merge (with-term (swing-spec w false) true) extra)))
                       ts (range (+ 11 120) months 12)
                       st (fn [r t c] (get-in r [:book [:year c] t :s]))
                       truth (fn [t] (+ -2.5 (* 1.2 ((:mom w) t))))
                       mean-s (map (fn [t] (/ (reduce + (map #(st a t %) wcountries)) (count wcountries))) ts)
                       tr (map truth ts)
                       n (count ts) ma (/ (reduce + mean-s) n) mt (/ (reduce + tr) n)
                       cov (reduce + (map #(* (- %1 ma) (- %2 mt)) mean-s tr))
                       sd (fn [xs mu] (Math/sqrt (reduce + (map #(let [d (- % mu)] (* d d)) xs))))
                       gap (fn [b] (let [ds (for [t ts c wcountries] (Math/abs (- (st a t c) (st b t c))))] (/ (reduce + ds) (count ds))))]]
             (cond-> {:dist (/ (reduce + (for [t ts c wcountries] (Math/abs (- (st a t c) (truth t))))) (* n (count wcountries)))
                      :corr (/ cov (* (sd mean-s ma) (sd tr mt)))
                      :noise (gap b)}
               c (assoc :noise-term (gap c))))]
    (into {} (for [k (keys (first ms))] [k (/ (reduce + (map k ms)) (count ms))]))))

(deftest fix-2-states-hold-under-many-inputs
  ;; Chosen on this world and on the real food year node's states (no
  ;; outcome read) before any rescoring run: the base model as the
  ;; reference, with the world's term (fix 3) in its offset. Measured here
  ;; over ten worlds: the all-inputs reference correlates 0.32 with the
  ;; planted log odds, sits 1.08 from it and moves 0.15 for a column that
  ;; carries nothing; its prior scaled per input (:shrink-per-input 0.5,
  ;; the knee of 0.1 to 2) 0.35, 0.97, 0.06; the base with the term 0.94,
  ;; 0.56 and exactly 0 (0.16 when the column enters the term itself). An
  ;; equal pool of the models (0.17) and the all-inputs model shrunk toward
  ;; the base by its own variance (0.24) track less; damping toward the
  ;; parent was not tried: a parent's state is the chance of any onset
  ;; among its children (0.94-0.999 at the world node for food), not a
  ;; child's level.
  (let [seeds (range 11 21)
        wide (swing-measures {} seeds)
        scaled (swing-measures {:shrink-per-input 0.5} seeds)
        held (swing-measures {:state-from :base} seeds true)]
    (testing "the all-inputs model at :shrink 0.1 swings (the failure)"
      (is (> (:noise wide) 0.08) wide)
      (is (< (:corr wide) 0.5) wide))
    (testing "the base with the world's term holds: a column outside the term moves no state"
      (is (= 0.0 (:noise held)) held)
      (is (< (:dist held) (* 0.7 (:dist wide))) [held wide])
      (is (< (:noise-term held) 0.25) held))
    (testing "and tracks the planted signal"
      (is (> (:corr held) 0.85) held))
    (testing "the per-input prior alone halves the swing but tracks no better"
      (is (< (:noise scaled) (* 0.5 (:noise wide))) [scaled wide])
      (is (< (:corr scaled) 0.5) scaled))
    (testing "C7: the states do not read the weights"
      (doseq [extra [{:state-from :base :world-offset (fn [_ place t] (when (= 2 (count place)) {:x (Math/sin t) :reads []}))}
                     {:state-from :reference :shrink-per-input 0.5}]]
        (let [a (loop-run extra)
              b (loop-run (merge tempered extra))]
          (is (< (max-gap book-states a b) 1e-12))
          (is (< (max-gap a b) 1e-12)))))))

(deftest fix-2-the-shrinkage-is-by-the-node-s-width
  (let [w (swing-world 11)
        r (lt/run (merge (swing-spec w false) {:shrink-per-input 1.0 :end 11}))
        all (get-in r [:models [:year :country] :models :all])
        k (:k all)
        n (dec k)
        diag (fn [i] (nth (:c all) (+ (* i k) i)))]
    (is (= 31 k) "20 gauges, 10 flows and the world column")
    (testing "each non-core input's prior variance is 1/n (one period's discount on it)"
      (is (every? #(< (Math/abs (- (* n (diag %) 0.998) 1.0)) 1e-9) (range 1 k)) (mapv diag (range 1 k))))))

(defn- level-world
  "Six countries at a constant onset rate of 0.15 over 50 years, a year node
  whose offset is climatology and whose rows resolve 42 months after the
  origin (as food's year node does), the prior's counts `prior` before any
  is resolved."
  [prior extra]
  (let [g (m/rng 21)
        ys (into {} (for [c countries t (range 700)] [[c t] (< (m/next-double! g) 0.15)]))]
    (lt/run (merge {:scales [{:name :year :per 12 :horizon 24 :discount 0.998}]
                    :levels [[:country countries]]
                    :parent {} :start 0 :end 599 :forget 0.995
                    :offset (fn [_ _ _] nil)
                    :clim-prior (fn [_ _] prior)
                    :target (fn [_ place t] {:y (ys [place (+ t 24)]) :known-at (+ t 42)})
                    :gauges (fn [_ _ _] [])
                    :gauge-width {:year 0}}
                   extra))))

(deftest bnm-56c-27-a-change-of-climatology-decays
  ;; two runs that differ only in the climatology the node issues at before
  ;; its own outcomes resolve (the prior's 0.025 against 0.6, 4.1 in log
  ;; odds): once its own rate takes over, the gap between them is what the
  ;; initial condition still carries. Learned at the offset it was issued
  ;; at, the intercept keeps the old level and adds it to the new one; the
  ;; gain is the gap 10-30 years on over the initial one
  (let [late (fn [r] (let [fs (filter #(< 120 (:origin %) 360) (:forecasts r))]
                       (/ (reduce + (map #(lt/logit-of (:p %)) fs)) (count fs))))
        lo [2 100] hi [60 100]
        start-gap (- (lt/logit-of (/ 60.5 101)) (lt/logit-of (/ 2.5 101)))
        gain (fn [extra] (/ (- (late (level-world hi extra)) (late (level-world lo extra))) start-gap))]
    (testing "issue-time offsets (the base): the old level persists, and overshoots"
      (is (< (gain {}) -0.2) (gain {})))
    (testing ":clim-learn :current: the change decays, gain near 0"
      (is (< (Math/abs (gain {:clim-learn :current})) 0.02) (gain {:clim-learn :current})))
    (testing "and the level is the rate's"
      (is (< (Math/abs (- (late (level-world hi {:clim-learn :current})) (lt/logit-of 0.15))) 0.6)))
    (testing "an unknown value is refused"
      (is (thrown? clojure.lang.ExceptionInfo (level-world lo {:clim-learn :issue}))))))

(deftest bnm-56c-27-reanchor-moves-every-offset
  (is (= {:o 1.5 :os [[0.5 1.0] [0.5 2.0]] :clim -2.0}
         (lt/reanchor {:o 1.0 :os [[0.5 0.5] [0.5 1.5]] :clim -2.0} 0.5)))
  (is (= {:o -1.0} (lt/reanchor {:o -1.5} 0.5))))

(defn- pre-information-world
  "Six countries whose one flow input (a relation) reads `pre` before month
  120, when their outcomes start, and a signal after; the first rows
  resolve at 155."
  [pre]
  (let [g (m/rng 5)
        sig (into {} (for [c countries t (range 400)] [[c t] (m/normal! g)]))
        ys (into {} (for [c countries t (range 400)] [[c t] (< (m/next-double! g) (m/expit (- (sig [c t]) 1.5)))]))]
    (lt/run {:scales [{:name :year :per 12 :horizon 12 :discount 0.998}]
             :levels [[:country countries]]
             :parent {} :start 0 :end 359 :forget 0.995
             :target (fn [_ place t] (when (>= t 120) {:y (ys [place (+ t 12)]) :known-at (+ t 24)}))
             :gauges (fn [_ _ _] [])
             :gauge-width {:year 0}
             :relations (fn [_ place t] {:x [(if (< t 120) (pre place t) (sig [place t]))] :reads []})
             :relation-width {:year 1}
             :trace :inputs})))

(deftest bnm-56c-33-flows-standardized-since-the-first-outcome
  ;; two runs that differ only in what the node's flows read before it has
  ;; any outcome (a flat 0 against an uninformed level near 50): from its
  ;; first resolved outcome on, the standardized inputs are the same, the
  ;; uninformed era not counted in their mean and spread
  (let [after (fn [r] (vec (for [{:keys [origin x]} (:trace r) :when (>= origin 155)] x)))
        a (pre-information-world (fn [_ _] 0.0))
        b (pre-information-world (fn [_ t] (+ 50.0 (* 0.01 (mod t 7)))))]
    (is (= 108 (count (after a))))
    (is (= (after a) (after b)))
    (is (= (mapv :p (filter #(>= (:origin %) 155) (:forecasts a)))
           (mapv :p (filter #(>= (:origin %) 155) (:forecasts b)))))))
