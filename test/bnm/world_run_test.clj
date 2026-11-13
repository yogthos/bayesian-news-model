(ns bnm.world-run-test
  (:require [bnm.factors]
            [bnm.kinds]
            [bnm.lattice-political]
            [bnm.named]
            [bnm.slow-train]
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

(deftest a-run-names-its-model
  ;; the base of record (t48-world-norel) is the world without relations;
  ;; a bare run used to turn on trial 43's links (graph-t43.edn) silently
  (testing "no choice: refused, naming both"
    (is (thrown-with-msg? Exception #"--without-relations true.*--graph" (wr/model-choice {})))
    (is (thrown-with-msg? Exception #"--without-relations true" (wr/model-choice {:out "x" :end "24000" :smoke "true"})))
    (is (thrown-with-msg? Exception #"--without-relations true" (wr/model-choice {:without-relations "false"}))))
  (testing "the base of record: no relations, no graph read"
    (is (= {:relations? false :graph nil} (wr/model-choice {:without-relations "true"})))
    (is (= {:relations? false :graph nil} (wr/model-choice {:without-relations "true" :smoke "true" :kinds "coup"}))))
  (testing "links: the graph named"
    (is (= {:relations? true :graph "data/runs/sequencing/graph-t43.edn"}
           (wr/model-choice {:graph "data/runs/sequencing/graph-t43.edn"})))
    (is (= {:relations? true :graph "g.edn"} (wr/model-choice {:graph "g.edn" :placebo "true" :ews "e.edn" :states "s.edn"})))
    (is (= {:relations? true :graph "g.edn"} (wr/model-choice {:graph "g.edn" :without-relations "false"}))))
  (testing "a link option without a graph is refused, not run on a default graph"
    (doseq [k [:placebo :ews :states]]
      (is (thrown-with-msg? Exception #"--graph" (wr/model-choice {k "x"})))))
  (testing "the base with a link option is refused: it would be dropped silently"
    (doseq [k [:graph :placebo :ews :states]]
      (is (thrown-with-msg? Exception #"without relations" (wr/model-choice {:without-relations "true" k "x"})))))
  (testing "the two recorded graphs and their commands"
    (is (= "data/runs/sequencing/graph-t43.edn" wr/graph-t43))))

(deftest the-onsets-run-the-quarters-read-is-named
  ;; the quarters' offset pools the year with bnm.onsets' named model from
  ;; this run; t48 and t49 read r6-at, t50 r6-at-8ul (bead bnm-8ul)
  (is (= "r6-at" (wr/known-run {})) "a run that names none reads r6-at, as t48 and the first issue did")
  (is (= "r6-at-8ul" (wr/known-run {:known "r6-at-8ul"})))
  (is (= {:relations? false :graph nil} (wr/model-choice {:without-relations "true" :known "r6-at-8ul"}))
      "the base takes it: it is not a link option"))

(deftest bnm-56c-23-the-harness-column-without-the-world-state
  ;; a base without the world group (t51b) takes the acceptance harness's
  ;; extra column as a group of its own at the year and half-decade
  (let [sp {:start 0 :parent {"AAA" "world"} :scales [{:name :year} {:name :quarter-lead}]}
        out (get (wr/with-step2 {"k" sp} {:world-extra "noise" :seed "1"} 240) "k")
        x (:x ((:world-gauges out) :year "AAA" 131))]
    (is (= {:year 1} (:world-width out)))
    (is (= 1 (count x)))
    (is (number? (first x)))
    (is (= x (:x ((:world-gauges out) :year "world" 131))) "the same at every place")
    (is (nil? (:world-gauges (get (wr/with-step2 {"k" sp} {} 240) "k"))) "no harness, no group")))

(deftest the-reference-forecasts
  (let [r {:forecasts [{:scale :year :place "KEN" :origin 11 :p 0.2 :y true}
                       {:scale :quarter-lead :place "KEN" :origin 11 :p 0.1 :y nil}]
           :book {[:year "KEN"] (sorted-map 11 {:p 0.35 :s 0.0 :issued {:p 0.2}})
                  [:quarter-lead "KEN"] (sorted-map 11 {:p 0.1 :s 0.0})}}]
    (is (= [0.35 0.1] (map :p (:forecasts (wr/reference-forecasts r)))) "the state's p where it is kept apart")
    (is (= [true nil] (map :y (:forecasts (wr/reference-forecasts r)))))))

(deftest bnm-56c-26-the-prior-rate-is-the-other-kinds'
  ;; a node with nothing resolved reads the onsets and rows the other kinds'
  ;; nodes at its scale and level have resolved by then
  (let [clims (atom {"food" {[:year :country] [0 0]}
                     "coup" {[:year :country] [3 100] [:year :region] [5 10]}
                     "war" {[:year :country] [7 200]}})
        prior (wr/cross-kind-prior clims "food")]
    (is (= [10 300] (prior :year :country)))
    (is (= [5 10] (prior :year :region)))
    (is (= [0 0] (prior :half-decade :country)) "nothing anywhere: no prior")
    (is (= [7 200] ((wr/cross-kind-prior clims "coup") :year :country)) "never its own")
    (swap! clims assoc-in ["war" [:year :country]] [8 300])
    (is (= [11 400] (prior :year :country)) "as of the month it is read")))

(deftest bnm-56c-28-the-world-term-is-gated-unless-told-not-to
  (let [extra (fn [_] 0.0)]
    (is (= {:extra extra} (wr/once-opts {} extra)) "the gate by default: bnm.world-gauges' own")
    (is (= {:extra extra :gate nil} (wr/once-opts {:world-gate "false"} extra)) "--world-gate false, 822fea8's ungated term")))

(deftest food-outcomes-option
  ;; --food-outcomes monthly swaps in the monthly food catalogue at its
  ;; publication lag (bnm-56c.30); without it the base's catalogue stands;
  ;; any other value is refused
  (let [db {["AAA" "food/food_inflation"] {:period (vec (range 24000 24100)) :value (vec (repeat 100 3.0))}}
        cats {"food_price_shock" {:known-lag 18} "coup" {:known-lag 12}}]
    (is (= cats (wr/food-outcomes cats db nil)))
    (is (= 9 (get-in (wr/food-outcomes cats db "monthly") ["food_price_shock" :known-lag])))
    (is (= {:known-lag 12} ((wr/food-outcomes cats db "monthly") "coup")))
    (is (thrown? Exception (wr/food-outcomes cats db "quarterly")))))

(deftest bnm-a1e-real-lags-option
  ;; --real-lags true reads every slow series at its true publication;
  ;; absent or false, the base's dating; anything else is refused
  (is (false? (wr/real-lags-choice {})))
  (is (false? (wr/real-lags-choice {:real-lags "false"})))
  (is (true? (wr/real-lags-choice {:real-lags "true"})))
  (is (thrown? Exception (wr/real-lags-choice {:real-lags "yes"}))))

(deftest fresh-inputs-option
  ;; --fresh-inputs true: the year row's Maddison growth carried on by WDI
  ;; as first published; absent or false, the base's reading
  (is (false? (wr/fresh-inputs-choice {})))
  (is (false? (wr/fresh-inputs-choice {:fresh-inputs "false"})))
  (is (true? (wr/fresh-inputs-choice {:fresh-inputs "true"})))
  (is (thrown? Exception (wr/fresh-inputs-choice {:fresh-inputs "yes"}))))

(deftest bnm-5rn-leading-food2-option
  (is (false? (wr/leading-food2-choice {:leading "true"})))
  (is (true? (wr/leading-food2-choice {:leading "true" :leading-food2 "true"})))
  (is (thrown? Exception (wr/leading-food2-choice {:leading-food2 "true"})) "needs --leading true")
  (is (thrown? Exception (wr/leading-food2-choice {:leading "true" :leading-food2 "1"}))))

(deftest bnm-96j-at-risk-option
  ;; --at-risk catalogue: each catalogue carries its rule's grid, refractory
  ;; and barred months (bnm.kinds/at-risk-rules); without it, the base's
  (let [db {["AAA" "economy/inflation"] {:period [(+ (* 12 1990) 11) (+ (* 12 1991) 11) (+ (* 12 1992) 11)]
                                         :value [10.0 50.0 20.0]}}
        cats {"growth_collapse" {:known-lag 18} "inflation_crisis" {:onsets {"AAA" (sorted-set (* 12 1991))} :known-lag 18}
              "coup" {:known-lag 12} "food_price_shock" {:known-lag 18}}
        out (wr/at-risk cats db nil "catalogue")]
    (is (= cats (wr/at-risk cats db nil nil)))
    (is (= {:known-lag 18 :grid [12 0] :refractory 60} (out "growth_collapse")))
    (is (= #{(* 12 1992)} (set (get-in out ["inflation_crisis" :barred "AAA"]))))
    (is (= {:known-lag 12} (out "coup")) "no rule beyond the busy months")
    (is (= [3 2] (get-in out ["food_price_shock" :grid])) "the quarterly catalogue's onsets at quarter ends")
    (is (= [1 0] (get-in (wr/at-risk cats db "monthly" "catalogue") ["food_price_shock" :grid])) "--food-outcomes monthly")
    (is (thrown? Exception (wr/at-risk cats db nil "busy")))))

(deftest bnm-96j-every-catalogue-keeps-its-declared-rule
  ;; on the data: no onset off its grid, within its refractory of the one
  ;; before, or on a barred month (the rules describe the catalogues)
  (when (.exists (java.io.File. "data/derived/econ-events.csv"))
    (let [db (bnm.named/load-series "data/slow/series.tsv" (bnm.slow-train/read-variables "data/slow/variables.tsv"))
          cats (wr/at-risk (bnm.kinds/catalogues) db nil "catalogue")]
      (doseq [[k {:keys [onsets grid refractory barred]}] cats :when grid
              [u os] onsets]
        (let [[g ph] grid]
          (is (every? #(= ph (mod % g)) os) (str k " " u " off its grid"))
          (when refractory
            (is (every? (fn [[a b]] (>= (- b a) refractory)) (partition 2 1 os)) (str k " " u " within its refractory")))
          (is (not-any? (get barred u #{}) os) (str k " " u " on a barred month")))))))

(deftest bnm-11y-lead-std-option
  (is (nil? (wr/lead-std-choice {:leading "true"})))
  (is (= :history (wr/lead-std-choice {:leading "true" :lead-std "history"})))
  (is (thrown? Exception (wr/lead-std-choice {:lead-std "history"})))
  (is (thrown? Exception (wr/lead-std-choice {:leading "true" :lead-std "node"}))))

(deftest bnm-v1y-frailty-gauges-option
  (is (false? (wr/frailty-gauges-choice {:leading "true" :leading-priors "true"})))
  (is (true? (wr/frailty-gauges-choice {:leading "true" :leading-priors "true" :frailty-gauges "true"})))
  (is (thrown? Exception (wr/frailty-gauges-choice {:leading "true" :frailty-gauges "true"})) "needs the theory priors")
  (is (thrown? Exception (wr/frailty-gauges-choice {:leading "true" :leading-priors "true" :frailty-gauges "yes"})))
  (testing "the world gauges of the year node go to its frailty; a country-varying gauge stays a model input"
    (let [specs {"market_crash" {:frailty {} :gauge-names {:year [:a :baa12 :us-curve :vix12] :quarter-lead [:baa12 :vix12]}}
                 "food_price_shock" {:frailty {:rhos [0.0]} :gauge-names {:year [:a :world-food12 :food12-x-imports]}}
                 "coup" {:frailty {}}}
          out (wr/with-frailty-gauges specs true)]
      (is (= {:gauges {:year [:baa12 :us-curve :vix12]}} (get-in out ["market_crash" :frailty])))
      (is (= {:rhos [0.0] :gauges {:year [:world-food12]}} (get-in out ["food_price_shock" :frailty])))
      (is (= {:frailty {}} (out "coup")))
      (is (= specs (wr/with-frailty-gauges specs false)))
      (is (thrown? Exception (wr/with-frailty-gauges {"market_crash" {:gauge-names {:year [:baa12]}}} true)) "needs the frailty"))))
