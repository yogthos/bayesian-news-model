(ns bnm.world-run-test
  (:require [bnm.factors]
            [bnm.lattice-political]
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
    (testing "the reads: the place's own and one for the cross-section, at its latest origin"
      (let [rs (:reads (a :half-decade "AAA" 95))]
        (is (some #(and (= "AAA" (:place %)) (= :half-decade (:scale %))) rs))
        (is (some #(= :cross-section (:place %)) rs))
        (is (<= (count rs) 7) "its six states and the cross-section's one")
        (is (every? #(< (:origin %) 95) rs))))
    (testing "a region: its countries' mean common part (what they share), never a level"
      (let [st (loop [[t & ts] [59 71 83 95] st (bnm.factors/online par {:discount 0.99}) out nil]
                 (if t
                   (let [xs (into {} (for [c cs :let [v (:x (bnm.relations/raw-input :destab {:kind "bank" :book book :catalogue cat} :year c t))] :when v] [c v]))
                         [parts st] (bnm.factors/observe st t xs)] (recur ts st parts))
                   out))]
        (is (< (Math/abs (- (first (:x (a :year "Reg" 95))) (/ (+ (get-in st ["AAA" :common]) (get-in st ["BBB" :common])) 2.0))) 1e-12))))))

(deftest own-parts-placebo-at-regions
  ;; the placebo region reads its members' placebo sources, not itself
  (let [par {"AAA" "Reg" "BBB" "Reg" "CCC" "Reg2" "DDD" "Reg2" "Reg" "world" "Reg2" "world"}
        cs ["AAA" "BBB" "CCC" "DDD"]
        cat {:onsets {} :from (zipmap cs (repeat 0)) :known-lag 12}
        book (into {} (for [[i c] (map-indexed vector cs)]
                        [[:year c] (into (sorted-map) (for [n (range 8)] [(+ 11 (* 12 n)) {:f (* (inc i) (+ n (if (odd? (+ n i)) 0.5 0.0)))}]))]))
        mk (fn [src] (:relations (wr/with-relations {:scales [{:name :year}] :parent par}
                                                     ["bank"] (atom {"bank" book}) {"bank" cat} {"bank" #{:year}}
                                                     {"bank" {:form :destab-own :sign 1}} src)))
        real (mk identity) fake (mk (wr/placebo-source cs))]
    (doseq [t [59 71 83]] (real :year "AAA" t) (fake :year "AAA" t))
    (is (not= (:x (real :year "Reg" 95)) (:x (fake :year "Reg" 95))))))

(deftest a-warning-as-a-nested-group
  ;; a kind's early warning (bnm.ews-diag) enters beside its links as one
  ;; more nested group, its prior at the design years' sign; a region
  ;; reads its countries' mean; a kind with a warning and no parent still
  ;; gets it
  (let [par {"AAA" "Reg" "BBB" "Reg" "Reg" "world"}
        warn {:sign -1 :at (fn [c t] ({["AAA" 23] 2.0 ["BBB" 23] 4.0} [c t]))}
        sp (wr/with-relations {:scales [{:name :year}] :parent par} [] (atom {}) {} {} {} identity {:warning warn})]
    (is (= {:rel/warning [0]} (get-in sp [:relation-groups :year])))
    (is (= [2.0] (:x ((:relations sp) :year "AAA" 23))))
    (is (= [3.0] (:x ((:relations sp) :year "Reg" 23))))
    (is (= [nil] (:x ((:relations sp) :year "AAA" 35))) "unknown: nil, the lattice's mean")
    (is (= {5 [-0.2 0.1]} ((:prior sp) :year :country {:parts {:rel/warning [5]}})))))

(deftest the-placebo-reads-another-country-s-warning
  (let [par {"AAA" "Reg" "BBB" "Reg" "Reg" "world"}
        warn {:sign 1 :at (fn [c _] ({"AAA" 2.0 "BBB" 4.0} c))}
        sp (wr/with-relations {:scales [{:name :year}] :parent par} [] (atom {}) {} {} {}
                              (wr/placebo-source ["AAA" "BBB"]) {:warning warn})]
    (is (= [4.0] (:x ((:relations sp) :year "AAA" 23))))))

(deftest a-warning-as-known-at-the-origin
  ;; at a year-end origin the warning is the window ending that year, as
  ;; known then; at an origin within a year, the last year ended
  (let [w (wr/warnings {:window 3 :warnings {"k" ["s" :ac1 1]}}
                       (fn [[c v]] (when (= v "s") ::series)) ["AAA"])]
    (is (fn? (get-in w ["k" :at]))))
  (is (= 2000 (bnm.lattice-political/ended (+ 11 (* 12 2000)))))
  (is (= 1999 (bnm.lattice-political/ended (+ 5 (* 12 2000)))) "mid-year: the year before, ended")
  (is (thrown? clojure.lang.ExceptionInfo (wr/warnings {"k" ["s" :ac1 1]} {} [])) "the old format refused"))

(deftest parents-and-a-warning-together
  (let [cat {:onsets {"AAA" (sorted-set 100)} :from {"AAA" 0} :known-lag 12}
        sp (wr/with-relations {:scales [{:name :year}]} ["bank" "infl"] (atom {"bank" {} "infl" {}})
                              {"bank" cat "infl" cat} {"bank" #{:year} "infl" #{:year}}
                              {"bank" {:form :post3 :sign 1} "infl" {:form :post3 :sign -1}}
                              identity {:warning {:sign -1 :at (fn [_ _] 7.0)}})]
    (is (= {:rel/bank [0] :rel/infl [1] :rel/warning [2]} (get-in sp [:relation-groups :year])))
    (is (= 3 (get-in sp [:relation-width :year])))
    (is (= [1.0 1.0 7.0] (:x ((:relations sp) :year "AAA" 120))))
    (is (= {4 [0.2 0.1] 5 [-0.2 0.1] 6 [-0.2 0.1]}
           ((:prior sp) :year :country {:parts {:rel/bank [4] :rel/infl [5] :rel/warning [6]}})))))

(deftest stock-flow-states-as-nested-groups
  ;; a kind's chosen states (bnm.state-diag) beside its links and warning:
  ;; one nested group each, signed by the design years, as known at the
  ;; origin, a region their countries' mean, unknown nil
  (let [par {"AAA" "Reg" "BBB" "Reg" "Reg" "world"}
        st [{:name "food/import_dependence" :sign 1 :at (fn [c t] ({["AAA" 2000] 0.4 ["BBB" 2000] 0.2} [c (bnm.lattice-political/ended t)]))}
            {:name "keen/debt" :sign -1 :at (fn [_ _] nil)}]
        sp (wr/with-relations {:scales [{:name :year}] :parent par} [] (atom {}) {} {} {} identity {:states st})]
    (is (= {:rel/food-import_dependence [0] :rel/keen-debt [1]} (get-in sp [:relation-groups :year])))
    (is (= [0.4 nil] (:x ((:relations sp) :year "AAA" (+ 11 (* 12 2000))))))
    (is (< (Math/abs (- 0.3 (first (:x ((:relations sp) :year "Reg" (+ 11 (* 12 2000))))))) 1e-12))
    (is (= {3 [0.2 0.1] 4 [-0.2 0.1]}
           ((:prior sp) :year :country {:parts {:rel/food-import_dependence [3] :rel/keen-debt [4]}})))))
