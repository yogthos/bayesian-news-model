(ns bnm.world-run-test
  (:require [bnm.factors]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(deftest the-placebo-reads-another-country
  (let [src (wr/placebo-source ["CCC" "AAA" "BBB"])]
    (is (= ["BBB" "CCC" "AAA"] (map src ["AAA" "BBB" "CCC"])))
    (is (= "Western Africa" (src "Western Africa")) "a region reads itself")))

(deftest one-nested-input-per-parent
  (let [cat {:onsets {"AAA" (sorted-set 100)} :from {"AAA" 0} :known-lag 12}
        books (atom {"bank" {} "infl" {}})
        sp (wr/with-relations {:scales [{:name :year}]} ["bank" "infl"] books {"bank" cat "infl" cat}
                              {"bank" #{:year} "infl" #{:year}}
                              {"bank" {:form :post3 :sign 1} "infl" {:form :e1 :sign -1}}
                              identity)
        {:keys [x]} ((:relations sp) :year "AAA" 120)]
    (is (= 2 (count x)))
    (is (= 1.0 (first x)) "bank's onset known in the last three years")
    (is (:relation-nest sp))
    (is (= {:rel/bank [0] :rel/infl [1]} (get-in sp [:relation-groups :year])))
    (is (= {7 [0.2 0.1] 8 [-0.2 0.1]} ((:prior sp) :year :country {:parts {:rel/bank [7] :rel/infl [8]}})))))

(deftest a-region-reads-its-countries
  ;; above the countries, the mean of the members' inputs: the share of a
  ;; region's countries in the parent's post-crisis regime
  (let [cat {:onsets {"AAA" (sorted-set 100)} :from {"AAA" 0 "BBB" 0} :known-lag 12}
        sp (wr/with-relations {:scales [{:name :year}] :parent {"AAA" "Reg" "BBB" "Reg" "Reg" "world"}}
                              ["bank"] (atom {"bank" {}}) {"bank" cat} {"bank" #{:year}}
                              {"bank" {:form :post3 :sign 1}} identity)]
    (is (= [0.5] (:x ((:relations sp) :year "Reg" 120))))
    (is (= [0.5] (:x ((:relations sp) :year "world" 120))))
    (is (= [1.0] (:x ((:relations sp) :year "AAA" 120))))))

(deftest no-parent-no-relations
  (let [sp {:scales [{:name :year}] :prior nil}]
    (is (= sp (wr/with-relations sp [] (atom {}) {} {} {} identity))
        "a kind without a parent is the world without relations")))

(deftest an-own-part-along-an-edge
  ;; a form's own part (bnm.factors): the parent's input at each country
  ;; less what the world's and the region's countries share at t, its
  ;; factor model stepped once per origin
  (let [par {"AAA" "Reg" "BBB" "Reg" "CCC" "Reg2" "DDD" "Reg2" "Reg" "world" "Reg2" "world"}
        cat {:onsets {"AAA" (sorted-set 100)} :from {"AAA" 0 "BBB" 0 "CCC" 0 "DDD" 0} :known-lag 12}
        sp (wr/with-relations {:scales [{:name :year} {:name :half-decade}] :parent par}
                              ["bank"] (atom {"bank" {}}) {"bank" cat} {"bank" #{:year}}
                              {"bank" {:form :post3-own :sign 1}} identity)
        rel (:relations sp)
        raw (fn [t] (into {} (for [c ["AAA" "BBB" "CCC" "DDD"]] [c (if (and (= c "AAA") (< 112 t 148)) 1.0 0.0)])))
        want (loop [[t & ts] [107 119 131] st (bnm.factors/online par {:discount 0.99}) out nil]
               (if t (let [[parts st] (bnm.factors/observe st t (raw t))] (recur ts st parts)) out))]
    (doseq [t [107 119]] (rel :year "AAA" t))
    (is (= [(get-in want ["AAA" :own])] (:x (rel :year "AAA" 131))))
    (is (= (:x (rel :year "AAA" 131)) (:x (rel :half-decade "AAA" 131))) "a second read at the origin")
    (is (= [(get-in want ["BBB" :own])] (:x (rel :year "BBB" 131))))
    (is (not= [1.0] (:x (rel :year "AAA" 131))) "not the raw input")))

(deftest own-parts-by-scale-with-their-reads
  ;; each scale its own factor model (a :destab-own at the half-decade
  ;; reads the half-decade's book, whatever was read first); the reads
  ;; behind an own part recorded; a region reads its countries' mean of
  ;; the raw form (the common level), not their own parts
  (let [par {"AAA" "Reg" "BBB" "Reg" "CCC" "Reg2" "DDD" "Reg2" "Reg" "world" "Reg2" "world"}
        cs ["AAA" "BBB" "CCC" "DDD"]
        cat {:onsets {} :from (zipmap cs (repeat 0)) :known-lag 12}
        book (into {} (for [[i c] (map-indexed vector cs) [sc k] [[:year 1.0] [:half-decade -5.0]]]
                        [[sc c] (into (sorted-map) (for [n (range 8)] [(+ 11 (* 12 n)) {:f (* k (+ n (* 0.3 i) (if (odd? (+ n i)) 0.5 0.0)))}]))]))
        mk #(wr/with-relations {:scales [{:name :year} {:name :half-decade}] :parent par}
                               ["bank"] (atom {"bank" book}) {"bank" cat} {"bank" #{:year :half-decade}}
                               {"bank" {:form :destab-own :sign 1}} identity)
        a (:relations (mk)) b (:relations (mk))
        ts [59 71 83 95]]
    (doseq [t ts] (a :year "AAA" t) (a :half-decade "AAA" t))
    (doseq [t ts] (b :half-decade "AAA" t) (b :year "AAA" t))
    (is (= (:x (a :half-decade "AAA" 95)) (:x (b :half-decade "AAA" 95))) "the order of reads does not matter")
    (is (not= (:x (a :half-decade "AAA" 95)) (:x (a :year "AAA" 95))) "the scales differ")
    (is (some #(and (= "BBB" (:place %)) (= :half-decade (:scale %))) (:reads (a :half-decade "AAA" 95)))
        "the cross-section's reads recorded")
    (is (every? #(< (:origin %) 95) (:reads (a :half-decade "AAA" 95))))
    (testing "a region: its countries' mean of the raw form"
      (let [raw (fn [c] (:x (bnm.relations/raw-input :destab {:kind "bank" :book book :catalogue cat} :year c 95)))]
        (is (< (Math/abs (- (first (:x (a :year "Reg" 95))) (/ (+ (raw "AAA") (raw "BBB")) 2.0))) 1e-12))))))
