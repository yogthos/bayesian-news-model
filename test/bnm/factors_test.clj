(ns bnm.factors-test
  (:require [augur.math :as m]
            [bnm.factors :as f]
            [clojure.test :refer [deftest is testing]]))

(defn- corr [xs ys]
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sx (Math/sqrt (reduce + (map #(* (- % mx) (- % mx)) xs)))
        sy (Math/sqrt (reduce + (map #(* (- % my) (- % my)) ys)))]
    (/ sxy (* sx sy))))

;; three regions of eight countries under the world; each country loads on
;; a world factor and its region's, with its own level and its own part
(def regions ["R1" "R2" "R3"])
(def countries (vec (for [r regions i (range 8)] (str r "-" i))))
(def parent (merge (zipmap countries (mapcat #(repeat 8 %) regions)) (zipmap regions (repeat "world"))))

(defn- world [seed]
  (let [g (m/rng seed)
        T 120
        w (vec (reductions + (repeatedly T #(m/normal! g))))
        r0 (into {} (for [reg regions] [reg (vec (reductions (fn [a e] (+ (* 0.8 a) e)) (repeatedly T #(m/normal! g))))]))
        ;; the regions' factors as departures from the world's: centred
        ;; across the regions at each t (the convention the means define)
        r (into {} (for [reg regions] [reg (vec (for [t (range T)] (- ((r0 reg) t) (/ (reduce + (map #((r0 %) t) regions)) (count regions)))))]))
        load (into {} (for [c countries] [c [(* 5.0 (m/normal! g)) (+ 0.5 (m/next-double! g)) (+ 0.5 (m/next-double! g))]]))
        own (into {} (for [c countries] [c (vec (repeatedly T #(* 0.5 (m/normal! g))))]))
        common (into {} (for [c countries :let [[_ b d] (load c)]]
                          [c (vec (for [t (range T)] (+ (* b (w t)) (* d ((r (parent c)) t)))))]))
        panel (into {} (for [c countries :let [[a] (load c)]]
                         [c (into (sorted-map) (for [t (range T)] [t (+ a ((common c) t) ((own c) t))]))]))]
    {:panel panel :own own :common common :T T}))

(deftest splits-a-series-into-its-parts
  (let [{:keys [panel own common T]} (world 1)
        parts (f/decompose panel parent {:discount 0.99})
        late (for [c countries t (range 40 T)] [c t])]
    (testing "the own part is the country's own"
      (is (> (corr (map (fn [[c t]] (get-in parts [c t :own])) late) (map (fn [[c t]] ((own c) t)) late)) 0.85)))
    (testing "the common part is the world's and the region's"
      (is (> (corr (map (fn [[c t]] (get-in parts [c t :common])) late) (map (fn [[c t]] ((common c) t)) late)) 0.95)))
    (testing "the parts add up: level + common + own = the value"
      (is (every? (fn [[c t]] (let [{:keys [level common own]} (get-in parts [c t])]
                                (< (Math/abs (- (+ level common own) (get-in panel [c t]))) 1e-9)))
                  late)))
    (testing "the common part by level of the place tree"
      (let [{:keys [by-level common]} (get-in parts ["R1-0" 60])]
        (is (= #{"world" "R1"} (set (keys by-level))))
        (is (< (Math/abs (- common (reduce + (vals by-level)))) 1e-9))))))

(deftest causal
  ;; nothing at t depends on anything after it
  (let [{:keys [panel]} (world 2)
        a (f/decompose panel parent {:discount 0.99})
        b (f/decompose (update-vals panel #(into (sorted-map) (map (fn [[t x]] [t (if (>= t 80) (* 10.0 x) x)]) %))) parent {:discount 0.99})]
    (is (= (for [c countries t (range 80)] (get-in a [c t]))
           (for [c countries t (range 80)] (get-in b [c t]))))))

(deftest a-country-s-own-shock-stays-its-own
  ;; the factors leave the country out (its own mean is not in them), so a
  ;; spike at one country lands in its own part, not its common part
  (let [{:keys [panel]} (world 3)
        spiked (update-in panel ["R2-3" 70] + 50.0)
        a (f/decompose panel parent {:discount 0.99})
        b (f/decompose spiked parent {:discount 0.99})]
    (is (< (Math/abs (- (get-in a ["R2-3" 70 :common]) (get-in b ["R2-3" 70 :common]))) 1e-9))
    (is (< (Math/abs (- 50.0 (- (get-in b ["R2-3" 70 :own]) (get-in a ["R2-3" 70 :own])))) 1e-9))
    (testing "and reaches the others only through the factors, a little"
      (is (< (Math/abs (- (get-in b ["R2-4" 70 :common]) (get-in a ["R2-4" 70 :common]))) 10.0)))))

(deftest gaps-in-the-panel
  ;; a country missing some years, a region down to one country: its
  ;; factor is unknown there and counts as zero
  (let [{:keys [panel]} (world 4)
        holed (-> (update panel "R1-0" #(apply dissoc % (range 30 50)))
                  (as-> p (reduce (fn [p c] (update p c #(apply dissoc % (range 90 120)))) p (for [i (range 1 8)] (str "R3-" i)))))
        parts (f/decompose holed parent {:discount 0.99})]
    (is (nil? (get-in parts ["R1-0" 40])) "no value, no parts")
    (is (some? (get-in parts ["R1-0" 60])))
    (is (= 0.0 (get-in parts ["R3-0" 100 :by-level "R3"])) "alone in its region: no mean of the others")
    (is (every? #(not (Double/isNaN (double %))) (for [[_ byt] parts [_ {:keys [own]}] byt] own)))))

(deftest the-factors-themselves
  ;; each node's factor at t: the world's mean, a region's mean less the
  ;; world's, over the countries known then
  (let [panel {"A" (sorted-map 0 1.0) "B" (sorted-map 0 3.0) "C" (sorted-map 0 10.0) "D" (sorted-map 0 14.0)}
        par {"A" "X" "B" "X" "C" "Y" "D" "Y" "X" "world" "Y" "world"}
        fs (f/factors panel par)]
    (is (= 7.0 (get-in fs [0 "world"])))
    (is (= -5.0 (get-in fs [0 "X"])))
    (is (= 5.0 (get-in fs [0 "Y"])))))

(deftest one-period-at-a-time
  ;; the streaming form: observe each period's cross-section in turn, as a
  ;; world run steps; the same parts as decompose
  (let [{:keys [panel]} (world 5)
        batch (f/decompose panel parent {:discount 0.99})
        ts (sort (set (mapcat keys (vals panel))))
        streamed (loop [[t & more] ts st (f/online parent {:discount 0.99}) out {}]
                   (if (nil? t)
                     out
                     (let [[parts st] (f/observe st t (into {} (for [[c byt] panel :let [x (get byt t)] :when x] [c x])))]
                       (recur more st (reduce (fn [o [c p]] (assoc-in o [c t] p)) out parts)))))]
    (is (= batch streamed))
    (testing "periods must come in order"
      (let [[_ st] (f/observe (f/online parent {}) 5 {"R1-0" 1.0 "R1-1" 2.0})]
        (is (thrown? clojure.lang.ExceptionInfo (f/observe st 5 {"R1-0" 1.0})))))))

(deftest a-flat-start-does-not-set-the-scale
  ;; an indicator all zero at first: no model is made until a cross-section
  ;; varies (its scale sets the priors), so no country's prior is unscaled;
  ;; until then each value is its own level
  (let [par {"A" "X" "B" "X" "C" "Y" "D" "Y" "X" "world" "Y" "world"}
        [p0 st0] (f/observe (f/online par {}) 0 {"A" 0.0 "B" 0.0 "C" 0.0 "D" 0.0})
        [_ st] (f/observe st0 1 {"A" 1.0 "B" 0.0 "C" 0.0 "D" 0.0})]
    (is (= {:level 0.0 :common 0.0 :own 0.0} (select-keys (p0 "A") [:level :common :own])))
    (is (empty? (:models st0)) "no model before the scale is known")
    (is (< (Math/abs (- 0.25 (:scale st))) 1e-12))
    (is (= #{"A" "B" "C" "D"} (set (keys (:models st)))) "made once the scale is known")))

(deftest an-entrant-does-not-move-the-others
  ;; a country entering with a level far from the others': the means are
  ;; of departures from each country's own level, so the others' common
  ;; parts do not jump when it arrives
  (let [{:keys [panel]} (world 6)
        late (assoc panel "R1-new" (into (sorted-map) (for [t (range 60 120)] [t 1000.0])))
        par (assoc parent "R1-new" "R1")
        a (f/decompose panel par {:discount 0.99})
        b (f/decompose late par {:discount 0.99})]
    ;; (from raw means the region's moved by about 110 and another's by 20;
    ;; what is left is the entrant's level being learned)
    (doseq [t [60 61 70]]
      (is (< (Math/abs (- (get-in a ["R1-2" t :common]) (get-in b ["R1-2" t :common]))) 1.0) (str "its region's, at " t))
      (is (< (Math/abs (- (get-in a ["R2-2" t :common]) (get-in b ["R2-2" t :common]))) 1.0) (str "another region's, at " t)))))
