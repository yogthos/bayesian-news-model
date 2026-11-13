(ns bnm.lattice-regional-test
  "The frailty's regional shared states (recursive step 3e, bnm-56c.20.3,
  bnm-3be): a shock common to the countries of a region, filtered once per
  origin inside the country node."
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [clojure.test :refer [deftest is testing]]))

(defn- region-spec
  "`nr` regions of `k` places each under a world node. Each year every
  place's log odds is -1.5 plus a common shock z_t (AR(1), persistence
  0.6) plus its region's shock r_gt (independent, sd `sr`). The one flow
  input group (relations, `width` columns) is noise constant within a
  region at an origin, as the region's and subregion's states that `:down`
  reads are. Only the base and that model are averaged. 40 years, outcomes
  known 24 months on; `extra` merged in. [spec planted]"
  [nr k sr extra & [{:keys [seed width] :or {seed 41 width 3}}]]
  (let [g (m/rng seed)
        yrs 40
        z (vec (take yrs (iterate #(+ (* 0.6 %) (* 0.4 (m/normal! g))) 0.0)))
        r (into {} (for [gi (range nr) y (range yrs)] [[gi y] (* sr (m/normal! g))]))
        x (into {} (for [gi (range nr) y (range yrs)] [[gi y] (vec (repeatedly width #(m/normal! g)))]))
        regions (mapv #(str "G" %) (range nr))
        places (vec (for [i (range k) gi (range nr)] (str "P" gi "-" i)))
        reg-of (into {} (for [i (range k) gi (range nr)] [(str "P" gi "-" i) gi]))
        yr (fn [t] (quot (inc t) 12))
        ys (into {} (for [c places y (range yrs)]
                      [[c y] (< (m/next-double! g) (m/expit (+ -1.5 (z y) (r [(reg-of c) y]))))]))]
    [(merge {:scales [{:name :year :per 12 :horizon 12 :discount 0.998}]
             :levels [[:country places] [:region regions] [:world ["W"]]]
             :parent (merge (into {} (for [p places] [p (regions (reg-of p))])) (zipmap regions (repeat "W")))
             :start 0 :end (dec (* 12 (dec yrs))) :forget 0.995
             :target (fn [_ place t] (when (reg-of place) {:y (ys [place (yr t)]) :known-at (+ t 24)}))
             :gauges (fn [_ _ _] [])
             :gauge-width {:year 0}
             ;; the base starts at the rate's level, as climatology does in the
             ;; world: without it, the first informed origins' miss of the level
             ;; is taken up by whatever variance lowers the mean-one shift
             :offset (fn [_ _ _] -1.5)
             :relations (fn [_ place t] {:x (if-let [gi (reg-of place)] (x [gi (yr t)]) (vec (repeat width 0.0))) :reads []})
             :relation-width {:year width}
             :only #{:base :relations}
             :flow-std-from :resolved}
            extra)
     {:z z :r r :yr yr :reg-of reg-of}]))

(defn- region-world [nr k sr extra & [o]]
  (let [[spec planted] (region-spec nr k sr extra o)]
    (merge (lt/run spec) planted)))

(defn- evidence
  "The region-noise model's evidence less the base's, summed over the
  run, and its mean weight at the country node: how far the averaging
  rewards a model for reading a region-constant input."
  [nr k sr extra & [o]]
  (let [[spec] (region-spec nr k sr (assoc extra :trace true) o)
        r (lt/run spec)
        ev (get-in r [:evidence [:year :country]])
        ws (map (comp :relations second) (get-in r [:weights [:year :country]]))]
    {:gap (- (double (first (:relations ev))) (double (first (:base ev))))
     :w (/ (reduce + ws) (count ws))}))

(defn- over-seeds
  "`evidence` averaged over three worlds."
  [nr k sr extra]
  (let [es (for [s [41 42 43]] (evidence nr k sr extra {:seed s}))]
    {:gap (/ (reduce + (map :gap es)) 3.0) :w (/ (reduce + (map :w es)) 3.0)}))

(def ^:private frail {:frailty {:rhos [0.0 0.6] :sds [0.0 0.5 1.0]}})
(def ^:private sq {:evidence-at :sequential})
(def ^:private regional (assoc-in frail [:frailty :regional-sds] [0.0 0.5 1.0]))

(deftest bnm-56c-20-3-sequential-evidence-rewards-a-regional-input-once-per-country
  ;; the inference of docs/CALIBRATION.md 9, planted: under a regional shock
  ;; the node does not model, a model reading a region-constant input learns
  ;; each period's regional pattern from its first rows. Scored sequentially
  ;; that is rewarded once per country of the region; scored independently
  ;; the same learning is paid for once per country (the pattern is gone by
  ;; the next period)
  (let [s1 (over-seeds 5 1 1.0 (merge frail sq)) s16 (over-seeds 5 16 1.0 (merge frail sq)) s40 (over-seeds 5 40 1.0 (merge frail sq))
        i1 (over-seeds 5 1 1.0 frail) i40 (over-seeds 5 40 1.0 frail)]
    (testing "sequential: the region-noise model's evidence grows with the countries per region, past the base's"
      (is (< (:gap s1) (:gap s16) (:gap s40)) [(:gap s1) (:gap s16) (:gap s40)])
      (is (pos? (:gap s40)) (:gap s40))
      (is (> (:w s40) (* 2.0 (:w s1))) [(:w s1) (:w s40)]))
    (testing "independent: it does not win; it loses more with the countries"
      (is (neg? (:gap i40)) (:gap i40))
      (is (< (:gap i40) (:gap i1)) [(:gap i1) (:gap i40)]))
    (testing "without a regional shock the sequential rule does not reward it"
      (is (neg? (:gap (over-seeds 5 40 0.0 (merge frail sq))))))))

(defn- increments
  "region-spec's world stepped period by period: the region-noise model's
  evidence less the base's in each period that learns rows."
  [nr k sr extra seed]
  (let [[spec] (region-spec nr k sr (assoc extra :trace true) {:seed seed})
        kk [:year :country]]
    (loop [state (lt/start spec) prev 0.0 out []]
      (if (> (:t state) (:end spec))
        out
        (let [state (lt/step spec state)
              ev (get-in state [:st :evidence kk])
              g (if ev (- (double (first (:relations ev))) (double (first (:base ev)))) 0.0)]
          (recur state g (if (not= g prev) (conj out (- g prev)) out)))))))

(defn- mean-abs [xs] (/ (reduce + (map #(Math/abs (double %)) xs)) (max 1 (count xs))))

(deftest bnm-56c-20-3-regional-states-count-the-regional-shock-once
  ;; per origin, the region-noise model's evidence over the base. The first
  ;; informed origin's rows were issued before the frailty had filtered
  ;; anything, so with no shared state (sd 0 wins the tie): that origin is
  ;; the same with and without the option, and is left out below
  (let [seeds [41 42 43]
        one (map #(increments 5 1 1.0 (merge frail sq) %) seeds)
        off (map #(increments 5 40 1.0 (merge frail sq) %) seeds)
        on (map #(increments 5 40 1.0 (merge regional sq) %) seeds)
        per (fn [runs] (/ (reduce + (map (comp mean-abs rest) runs)) (count runs)))]
    (testing "the start-up origin is learned without a shared state either way"
      (is (= (map first off) (map first on))))
    (testing "with a state per region, 40 countries a region move the averaging per origin about as one country a region does"
      (is (< (per on) (* 3.0 (per one))) [(per one) (per on) (per off)])
      (is (< (per on) (* 0.5 (per off))) [(per one) (per on) (per off)]))
    (testing "and the region-noise model loses, as it does with one country a region"
      (is (every? neg? (map #(reduce + (rest %)) on)) (mapv #(reduce + (rest %)) on))
      (is (every? neg? (map #(reduce + (rest %)) one))))))

(deftest bnm-56c-20-3-the-regional-states-are-filtered
  (testing "the filtered regional states track the planted ones, their sd chosen above 0"
    (let [r (region-world 5 40 1.0 (merge regional sq))
          fl (get-in r [:frailty [:year :country]])
          pairs (for [[s {:keys [rs]}] fl :when (seq rs) gi (range 5)]
                  [(nth rs gi) ((:r r) [gi ((:yr r) s)])])
          xs (map first pairs) ys (map second pairs)
          n (count pairs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
          c (/ (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
               (Math/sqrt (* (reduce + (map #(let [d (- % mx)] (* d d)) xs)) (reduce + (map #(let [d (- % my)] (* d d)) ys)))))]
      (is (pos? (:rsd (val (last fl)))))
      (is (= ["G0" "G1" "G2" "G3" "G4"] (:r-groups (val (last fl)))))
      (is (> n 100) n)
      (is (> c 0.8) c)))
  (testing "regional states forced at every node (no sd 0 in the grid): a node with no regional group keeps the frailty alone"
    (let [a (region-world 5 4 1.0 frail)
          b (region-world 5 4 1.0 (assoc-in frail [:frailty :regional-sds] [0.5]))]
      (is (every? #(= 0.5 (:rsd (val %))) (get-in b [:frailty [:year :country]])))
      (is (= (get-in a [:book [:year "G0"]]) (get-in b [:book [:year "G0"]])))))
  (testing "the region node has no coarser region: its frailty is the frailty alone"
    (let [a (region-world 5 4 1.0 frail) b (region-world 5 4 1.0 regional)]
      (is (= (get-in a [:frailty [:year :region]]) (get-in b [:frailty [:year :region]])))
      (is (= (get-in a [:book [:year "G0"]]) (get-in b [:book [:year "G0"]]))))))

(deftest bnm-56c-20-3-no-regional-shock-no-regional-state
  ;; a world without a regional shock keeps sd_r at 0 at every origin and
  ;; issues exactly what the frailty without the option does. At a price of
  ;; 2 nats, 2 of 12 such worlds (5 regions of 8 or 20 places, seeds 41-46)
  ;; chose sd_r > 0 at one origin of 34 by chance; this one at none
  (let [off (region-world 5 8 0.0 frail {:seed 43})
        on (region-world 5 8 0.0 regional {:seed 43})]
    (is (every? zero? (map (comp :rsd val) (get-in on [:frailty [:year :country]]))))
    (is (= (:forecasts off) (:forecasts on)))
    (is (= (:book off) (:book on)))))

(deftest bnm-56c-20-3-a-regional-grid-of-0-changes-nothing
  (let [a (region-world 5 4 1.0 frail)
        b (region-world 5 4 1.0 (assoc-in frail [:frailty :regional-sds] [0.0]))]
    (is (= (:forecasts a) (:forecasts b)))
    (is (= (:frailty a) (:frailty b)))))

(deftest bnm-56c-20-3-the-place-s-shock-in-the-prior
  ;; a candidate with regional states: the place's shift is z + c.r, c its
  ;; membership centred across the node's places, less half its stationary
  ;; variance (sd^2 + rsd^2 |c|^2), so the expected rate is the base's at
  ;; every place; its variance a P a' with a = [1 c]
  (let [c {:rho 0.0 :sd 1.0 :rsd 0.5 :nu 0 :nr 2 :mu [0.2 0.4 -0.1] :P [[0.3 0.1 0.0] [0.1 0.2 0.0] [0.0 0.0 0.2]]
           :t 23 :score 0.0 :price 0.0}
        zp (lt/frailty-prior {:prior :own} {:cands [c]} nil 35 12)]
    (testing "one period on at rho 0 the state is its stationary prior"
      (is (= [0.0 0.0 0.0] (:mu zp)))
      (is (= [[1.0 0.0 0.0] [0.0 0.25 0.0] [0.0 0.0 0.25]] (:P zp))))
    (let [{:keys [m v load]} (lt/frailty-at zp nil [-0.4 0.6])]
      (is (= [1.0 -0.4 0.6] load))
      (is (< (Math/abs (- m (- (* 0.5 (+ 1.0 (* 0.25 0.52)))))) 1e-12) m)
      (is (< (Math/abs (- v (+ 1.0 (* 0.25 0.52)))) 1e-12) v))
    (testing "no loads: z alone, less z's half variance"
      (let [{:keys [m v]} (lt/frailty-at zp nil nil)]
        (is (< (Math/abs (- m -0.5)) 1e-12))
        (is (< (Math/abs (- v 1.0)) 1e-12))))
    (testing "the rate's mean is one in any region"
      (doseq [g [nil [0.6 -0.4] [-0.4 0.6]]]
        (let [{:keys [m v]} (lt/frailty-at zp nil g)
              rate (reduce + (for [[w o'] (lt/with-frailty [[1.0 -4.0]] {:m m :v v})] (* w (Math/exp o'))))]
          (is (< (Math/abs (- 1.0 (/ rate (Math/exp -4.0)))) 0.02) [g rate]))))))

(deftest bnm-56c-20-3-with-a-slope-and-a-level
  ;; the regional states beside the level and the slope on exposure: the
  ;; loads are [1 1 e onehot], and the run issues
  (let [[spec] (region-spec 3 20 1.0(merge {:frailty {:rhos [0.6] :sds [0.0 0.5] :level-sds [0.0 0.1]
                                                      :exposure-sds [0.0 0.5] :regional-sds [0.0 0.5]}
                                            :exposure (fn [_ place _] [(double (count place))])
                                            :exposure-width 1}
                                           sq))
        r (lt/run spec)]
    (is (every? #(< 0.0 (:p %) 1.0) (:forecasts r)))
    (is (some #(pos? (:rsd (val %) 0.0)) (get-in r [:frailty [:year :country]])))))

(deftest bnm-56c-20-3-bad-regional-options-are-refused
  (doseq [bad [{:regional-sds [-0.1]} {:regional-sds []} {:regional-sds :x} {:regional-sds ["0.5"]}
               {:regional-penalty -1.0} {:regional-penalty :big}
               {:regional-sds [0.0 0.5] :regional-levels []} {:regional-sds [0.0 0.5] :regional-levels [:continent]}
               {:regional-sds [0.0 0.5] :regional-levels :region} {:regional-sds [0.0 0.5] :regional-levels ["region"]}]]
    (is (thrown? clojure.lang.ExceptionInfo (region-world 2 1 1.0 {:frailty bad})) (pr-str bad))))
