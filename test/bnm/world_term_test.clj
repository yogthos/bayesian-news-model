(ns bnm.world-term-test
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [bnm.world-term :as wt]
            [clojure.test :refer [deftest is testing]]))

(deftest collinear-columns-are-dropped
  (is (= [0 2] (wt/independent-columns [[1.0 2.0 3.0 4.0] [3.0 5.0 7.0 9.0] [1.0 0.0 1.0 5.0]])) "2x+1 is the first again")
  (is (= [0 1] (wt/independent-columns [[1.0 2.0 3.0 4.0] [1.0 0.0 1.0 5.0] [3.0 2.0 5.0 14.0]])) "a sum of the two before")
  (is (= [1] (wt/independent-columns [[2.0 2.0 2.0] [1.0 0.0 3.0]])) "a constant carries nothing"))

;; A world momentum, the same at every place, raises every country's chance
;; of an onset in the year after the origin, and a common shock no gauge
;; sees moves every country together in each year (as food 2020-22 did):
;; the origins, not the rows, are the observations of a world effect. Rows
;; are learned two years late
(def months 480)
(def countries (mapv #(str (if (< % 6) "a" "b") %) (range 12)))
(def parent (merge (into {} (for [c countries] [c (str (first c))])) {"a" "W" "b" "W"}))
(def origins (vec (range 11 months 12)))

(defn- world [seed]
  (let [g (m/rng seed)
        blocks (vec (repeatedly (inc (quot months 24)) #(- (* 3.0 (m/next-double! g)) 1.5)))
        mom (mapv (fn [t] (+ (blocks (quot t 24)) (* 0.2 (m/normal! g)))) (range months))
        noise (mapv (fn [_] (m/normal! g)) (range months))
        common (mapv (fn [_] (* 0.8 (m/normal! g))) (range months))
        crisis (into {} (for [c countries]
                          [c (into {} (for [t origins]
                                        [t (< (m/next-double! g) (m/expit (+ -2.5 (* 1.2 (mom t)) (common t) (* 0.2 (m/normal! g)))))]))]))]
    {:mom mom :noise noise :crisis crisis :g g}))

(defn- target [w]
  (fn [_ place t]
    (let [cs (cond (= place "W") countries (> (count place) 1) [place] :else (filter #(= place (parent %)) countries))
          ys (keep #(get-in w [:crisis % t]) cs)]
      (when (and (seq ys) (< (+ t 12) months)) {:y (boolean (some true? ys)) :known-at (+ t 36)}))))

(defn- outcome [w]
  (fn [t] (when (< (+ t 12) months)
            {:share (/ (count (filter #(get-in w [:crisis % t]) countries)) (double (count countries)))
             :n (count countries) :known-at (+ t 36)})))

(defn- base-spec [w]
  {:scales [{:name :year :per 12 :horizon 12 :discount 0.998}]
   :levels [[:country countries] [:region ["a" "b"]] [:world ["W"]]]
   :parent parent :start 0 :end (dec months) :forget 0.995 :shrink 0.1
   :target (target w)
   :gauges (fn [_ _ t] [(nth (:noise w) (mod (* 7 t) months))])
   :gauge-width {:year 1}
   :state-from :reference :evidence-at :issue})

(defn- once
  "The world gauges `cols` (fn [t]) -> [values] entered once per origin:
  the world term, added to every node's offset."
  [sp w cols]
  (let [term (wt/term-fn {:gauges cols :outcome (outcome w) :origins origins})]
    (assoc sp :world-offset (fn [_ _ t] {:x (:x (term t)) :reads [{:dir :world :origin t :known-at t}]}))))

(defn- replicated
  "The same gauges as columns on every row (step 2's entry)."
  [sp cols n]
  (assoc sp :world-gauges (fn [_ _ t] {:x (cols t) :reads []}) :world-width {:year n}))

(defn- brier [fs] (let [fs (filter #(some? (:y %)) fs)] (/ (reduce + (map (fn [{:keys [p y]}] (let [d (- (if y 1.0 0.0) p)] (* d d))) fs)) (count fs))))
(defn- late [r] (filter #(and (= 2 (count (:place %))) (> (:origin %) 240)) (:forecasts r)))

(deftest test-fix-3-a-world-signal-entered-once-per-origin
  (let [runs (vec (for [seed [31 32 33 34 35 36 37 38]
                        :let [w (world seed)
                              mom (fn [t] [((:mom w) t)])
                              mom+noise (fn [t] [((:mom w) t) ((:noise w) t)])
                              sp (base-spec w)]]
                    {:without (brier (late (lt/run sp)))
                     :once (brier (late (lt/run (once sp w mom))))
                     :once+noise (brier (late (lt/run (once sp w mom+noise))))
                     :rep (brier (late (lt/run (replicated sp mom 1))))
                     :term (wt/term-fn {:gauges mom :outcome (outcome w) :origins origins})
                     :w w}))
        avg (fn [f] (/ (reduce + (map f runs)) (count runs)))]
    ;; measured over these eight worlds: without 0.1310, once 0.1189, as a
    ;; column on every row 0.1247, the oracle about 0.112
    (testing "the country year node tracks the planted world signal through the term"
      (is (< (+ (avg :once) 0.008) (avg :without)) [(avg :once) (avg :without)]))
    (testing "better than the same gauge as a column on every row, under a common shock"
      (is (< (+ (avg :once) 0.003) (avg :rep)) [(avg :once) (avg :rep)]))
    (testing "the term follows the signal once some origins are known"
      (doseq [{:keys [term w]} runs]
        (let [ts (filter #(> % 240) origins)
              xs (map #(:x (term %)) ts) ys (map #((:mom w) %) ts)
              n (count ts) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
              c (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))]
          (is (> (/ c (Math/sqrt (* (reduce + (map #(let [d (- % mx)] (* d d)) xs))
                                    (reduce + (map #(let [d (- % my)] (* d d)) ys)))))
                 0.75)))))
    (testing "a world gauge that carries nothing costs little"
      (is (< (Math/abs (- (avg :once+noise) (avg :once))) 0.002) [(avg :once+noise) (avg :once)]))
    (testing "each origin's row is learned once: the term's rows are the origins known by t"
      (let [{:keys [term]} (first runs)]
        (is (= (count (filter #(<= (+ % 36) 300) (filter #(< (+ % 12) months) origins))) (:n (term 300))))))))

(deftest test-fix-3-a-collinear-duplicate-changes-nothing
  (let [w (world 41)
        sp (base-spec w)
        a (lt/run (once sp w (fn [t] [((:mom w) t)])))
        b (lt/run (once sp w (fn [t] [((:mom w) t) (+ 1.0 (* 2.0 ((:mom w) t)))])))]
    (is (= (map :p (:forecasts a)) (map :p (:forecasts b))))
    (testing "as columns on every row it does"
      (let [c (lt/run (replicated sp (fn [t] [((:mom w) t)]) 1))
            d (lt/run (replicated sp (fn [t] [((:mom w) t) (+ 1.0 (* 2.0 ((:mom w) t)))]) 2))]
        (is (not= (map :p (:forecasts c)) (map :p (:forecasts d))))))))

;; The lead test as the term's gate (bnm-56c.28): a world gauge enters a
;; kind's term only at origins where it has passed bnm.lead-test on the
;; origins whose outcomes are known by then, so the gate never looks ahead
;; and a gauge that carries nothing (the harness's noise) is dropped by the
;; same evidence that admits food momentum
(deftest test-gate-a-gauge-enters-the-term-only-once-it-leads
  (let [w (world 31)
        gate {:ratio 0.9 :min-online 5}
        cols (fn [t] [((:mom w) t) ((:noise w) t)])
        both (wt/term-fn {:gauges cols :outcome (outcome w) :origins origins :gate gate})
        alone (wt/term-fn {:gauges (fn [t] [((:mom w) t)]) :outcome (outcome w) :origins origins :gate gate})
        ungated (wt/term-fn {:gauges cols :outcome (outcome w) :origins origins})
        late (filter #(> % 240) origins)
        share (fn [j] (/ (count (filter #(some #{j} (:kept (both %))) late)) (double (count late))))]
    (testing "the planted momentum passes at the late origins, the noise at almost none"
      (is (> (share 0) 0.9) (share 0))
      (is (< (share 1) 0.1) (share 1)))
    (testing "where the noise fails it changes nothing: it is dropped, not shrunk"
      (doseq [t late :when (= [0] (:kept (both t)))]
        (is (= (:x (alone t)) (:x (both t))))))
    (testing "before five online errors are known nothing passes and the term is 0"
      ;; at 107 six origins are known (11..71), so three online errors
      (is (= [] (:kept (both 107))))
      (is (= 0.0 (:x (both 107))))
      (is (= [0 1] (:kept (ungated 107))) "ungated, both enter"))
    (testing "the gate reads only the outcomes known at t"
      (let [t 300
            o (outcome w)
            flipped (fn [s] (let [r (o s)] (if (and r (> (:known-at r) t)) (assoc r :share (- 1.0 (:share r))) r)))]
        (is (= (both t) (wt/term-at {:gauges cols :outcome flipped :origins origins :gate gate} t)))))))
