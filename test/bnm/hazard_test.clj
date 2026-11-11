(ns bnm.hazard-test
  (:require [augur.math :as m]
            [bnm.buffers :as b]
            [bnm.hazard :as hz]
            [bnm.scanner :as scan]
            [clojure.test :refer [deftest is testing]]))

(defn- inv-rows
  "Invariant rows of a stock: a net cover (1 year), a gross cover and a
  displacement."
  [cover gross disp]
  [{:invariant :cover :window 1 :value cover} {:invariant :cover :window 3 :value cover}
   {:invariant :gross-cover :window 1 :value gross}
   {:invariant :displacement :window 5 :value disp}])

(defn- draw
  "n rows of `family`: cover, gross cover and noise drawn at random, the
  break's log odds from `eta` of [cover gross]."
  [g family n eta]
  (vec (for [_ (range n)
             :let [cover (Math/exp (* 1.5 (m/normal! g)))
                   gross (Math/exp (- (* 1.0 (m/normal! g)) 1.0))
                   disp (m/normal! g)]]
         {:f (hz/features (inv-rows cover gross disp)) :family family
          :y (< (m/next-double! g) (m/expit (eta cover gross))) :cover cover :gross gross})))

(def shared (fn [_ gross] (if (< gross 0.25) -0.5 -3.0)))
(def opposite (fn [_ gross] (if (< gross 0.25) -3.0 -1.0)))

(deftest features-are-the-registered-transforms
  (let [f (hz/features (inv-rows 0.4 0.2 1.0))]
    (is (= [0.0 1.0 1.0 1.0] (mapv f [:cover1<0.25 :cover1<0.5 :cover1<1 :cover1<2])))
    (is (= [0.0 1.0 1.0] (mapv f [:gross<0.125 :gross<0.25 :gross<0.5])))
    (is (< (Math/abs (- (f :log-gross) (Math/log 0.2))) 1e-12))
    (is (nil? (f :log-claim)) "not measured is nil, never zero")
    (is (hz/measured? f))
    (is (not (hz/measured? (hz/features []))))))

(deftest a-planted-cover-threshold-is-recovered
  (let [g (m/rng 1)
        rows (into (draw g "a" 1500 (fn [c _] (if (< c 0.5) -0.5 -3.5)))
                   (draw g "b" 1500 (fn [c _] (if (< c 0.5) -0.5 -3.5))))
        model (hz/fit rows)
        p (fn [c] (hz/pooled model (hz/features (inv-rows c 0.5 0.0))))]
    (is (> (p 0.3) (* 4.0 (p 3.0))) [(p 0.3) (p 3.0)])
    (is (< (Math/abs (- (p 0.7) (p 3.0))) (* 0.5 (- (p 0.3) (p 3.0)))) "the step is at the threshold, not above it")))

(deftest a-family-left-out-is-found-when-it-shares-the-form
  (let [g (m/rng 2)
        train (vec (mapcat #(draw g % 1000 shared) ["a" "b" "c"]))
        model (hz/fit train)
        same (draw g "d" 1500 shared)
        opp (draw g "e" 1500 opposite)
        auc (fn [rows] (hz/auc (map #(hz/predict model (:f %) (:family %)) rows) (map :y rows)))]
    (testing "a family never seen, with the shared form, ranks its breaks first"
      (is (> (auc same) 0.7) (auc same)))
    (testing "a family with the opposite form is not found"
      (is (< (auc opp) 0.5) (auc opp)))))

(deftest family-deviations-are-pooled
  (let [g (m/rng 3)
        rows (vec (concat (mapcat #(draw g % 800 shared) ["a" "b" "c"]) (draw g "e" 800 opposite)))
        model (hz/fit rows)
        f (hz/features (inv-rows 2.0 0.1 0.0))]
    (is (> (hz/predict model f "a") (hz/predict model f "e")) "the opposite family keeps its own form")))

(deftest labels-and-what-a-fit-may-read
  (let [panel [{:place "X" :year "2000" :family "r" :break "0" :episodes "" :ongoing "" :known "2000-12" :covered_by "c"}
               {:place "X" :year "2001" :family "r" :break "1" :episodes "e1" :ongoing "" :known "2001-12" :covered_by "c"}
               {:place "X" :year "2002" :family "r" :break "0" :episodes "" :ongoing "e1" :known "2002-12" :covered_by "c"}
               {:place "X" :year "2003" :family "r" :break "1" :episodes "e2" :ongoing "" :known "2005-03" :covered_by "c"}
               {:place "Y" :year "2003" :family "r" :break "0" :episodes "" :ongoing "" :known "2003-12" :covered_by ""}]
        rows (hz/label-rows panel {"e1" :main "e2" :gra})
        by (into {} (map (juxt (juxt :place :year) identity)) rows)]
    (is (= #{["X" 2000] ["X" 2001] ["X" 2003]} (set (keys by))) "ongoing and uncovered rows are left out")
    (is (true? (:y24 (by ["X" 2000]))) "an onset in the next year is in the 24-month window")
    (is (= (hz/month-of "2001-12") (:known24 (by ["X" 2000]))))
    (is (nil? (hz/label (by ["X" 2003]) 12 :main)) "a GRA-only break is dropped from the main labels")
    (is (true? (hz/label (by ["X" 2003]) 12 :all)))
    (testing "nothing known after the forecast date is trained on"
      (is (= [2000 2001] (mapv :year (hz/trainable rows (* 12 2004) 12 :all))) "2003 is within the gap")
      (is (= [2000] (mapv :year (hz/trainable rows (* 12 2002) 12 :all))) "2001 is within the 24-month gap")
      (is (= [] (mapv :year (hz/trainable rows (dec (* 12 2002)) 12 :all))))
      (is (= [2000 2001] (mapv :year (hz/trainable rows (* 12 2005) 12 :all)))
          "2003 is past the gap but its label is known only in March 2005")
      (is (= [2000 2001 2003] (mapv :year (hz/trainable rows (+ 2 (* 12 2005)) 12 :all)))))))

(deftest scanner-features-read-nothing-after-the-date
  (let [rec (b/record {:id "s/X" :name "s" :unit "u" :holder {:unit "h" :place "X"} :measure {:frequency 1 :lag 0}})
        monthly (fn [f] (vec (for [i (range 240) :let [t (+ 2000.0 (/ (inc i) 12.0))]] {:t t :v (f i) :known (+ t (/ 2.0 12.0))})))
        series {:stock (monthly #(- 1000.0 (* 2.0 %))) :outflow (monthly (constantly 1200.0))}
        as-of 2015.0
        junk (fn [os] (mapv #(if (> (:known %) as-of) (assoc % :v 1.0e9) %) os))
        a (scan/stock-features rec series as-of)
        p (scan/stock-features rec (update-vals series junk) as-of)]
    (is (hz/measured? a))
    (is (some? (:log-gross a)))
    (is (= a p))
    (testing "a monthly stock is read yearly at its latest known month"
      (let [v (scan/known-view (:stock series) as-of false)]
        (is (every? #(< (Math/abs (- (mod (- (:t %) (:t (peek v))) 1.0) 0.0)) 1e-6) (butlast v)))
        (is (<= (:known (peek v)) as-of))))))
