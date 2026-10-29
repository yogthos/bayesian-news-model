(ns bnm.fast-test
  (:require [augur.math :as m]
            [bnm.fast :as f]
            [bnm.fast-run :as fr]
            [bnm.lattice :as lt]
            [bnm.stocks :as sk]
            [clojure.test :refer [deftest is testing]]))

(defn- monthly
  "A series of `xs` from month `from`, each available `lag` months after
  its period."
  [from xs lag]
  (sk/series (map-indexed (fn [i x] [(+ from i) (+ from i lag) (double x)]) xs)))

(defn- close? [a b] (and a b (< (Math/abs (- (double a) (double b))) 1e-9)))

(deftest only-what-is-published-by-the-origin
  ;; a value published a month after its period is not read at its own
  ;; month's end: at t the latest is t-1's
  (let [s (monthly 0 (range 40) 1)]
    (is (close? 3.0 (f/change s 30 3)) "29 less 26")
    (is (= 29.0 (sk/value s 30)))
    (is (nil? (f/change s 0 3)) "nothing published yet")
    (testing "stale: nothing when the latest is more than six months old"
      (is (some? (f/change s 45 3)) "39's value, six months old")
      (is (nil? (f/change s 46 3))))))

(deftest a-surprise-is-a-break-from-trend
  (let [line (monthly 0 (map #(* 0.5 %) (range 40)) 0)
        jump (monthly 0 (concat (map #(* 0.5 %) (range 39)) [25.0]) 0)]
    (is (close? 0.0 (f/surprise line 39 24)) "on the trend: no surprise")
    (is (close? (- 25.0 19.5) (f/surprise jump 39 24)) "the jump above the line carried to its month")
    (testing "the trend uses only the w months before the latest"
      (is (close? (- 25.0 19.5) (f/surprise jump 39 4))))
    (testing "too few months before it: nothing"
      (is (nil? (f/surprise (monthly 0 [1 2 3] 0) 2 24))))
    (testing "what is not yet published does not move it"
      (let [late (sk/series (concat (map (fn [i] [i i (* 0.5 i)]) (range 39)) [[39 41 25.0]]))]
        (is (close? 0.0 (f/surprise late 40 24)) "at 40 the latest known is 38's, on its line")))))

(deftest volatility-of-monthly-changes
  (let [s (monthly 0 (map #(if (even? %) 0.0 1.0) (range 30)) 0)]
    ;; the twelve months to the latest hold eleven changes, six of one sign
    (is (close? (Math/sqrt (- 1.0 (/ 1.0 121))) (f/volatility s 29)) "changes +1, -1")
    (is (close? 0.0 (f/volatility (monthly 0 (range 30) 0) 29)))))

(deftest a-seasonal-change-against-a-year-before
  (let [s (monthly 0 (for [i (range 30)] (+ (if (< (mod i 12) 3) 5.0 0.0) (* 0.1 i))) 0)]
    ;; months 24-26 against 12-14: the same season, 1.2 of trend apart
    (is (close? 1.2 (f/year-on-year s 26)))
    (is (nil? (f/year-on-year (monthly 0 (range 10) 0) 9)) "no year before")))

(deftest commodities-land-through-exposure
  (let [food (monthly 0 (map #(* 0.01 %) (range 60)) 0)
        fast {["world" "fast/food"] food}
        exposure (fn [c _] ({"AAA" {:food 0.2 :energy -0.1 :fertiliser 0.3} "BBB" {}} c))
        inputs (f/family-inputs :commodities fast exposure)
        a (inputs "AAA" 50) b (inputs "BBB" 50)]
    (is (= 9 (count a) (count (:commodities f/families))))
    (is (close? (* 0.2 0.03) (first a)) "food's three-month change times food imports")
    (is (close? 0.0 (second a)) "on its trend")
    (is (nil? (nth a 3)) "no energy prices: unknown")
    (is (every? nil? b) "no exposure: unknown, not zero")))

(deftest fx-and-spreads-per-country
  (let [fast {["AAA" "fast/fx"] (monthly 0 (range 40) 1) ["AAA" "fast/spread"] (monthly 0 (repeat 40 2.0) 1)}
        xs ((f/family-inputs :fx fast (constantly {})) "AAA" 30)]
    (is (= 6 (count xs)))
    (is (close? 3.0 (first xs)))
    (is (close? 0.0 (nth xs 3)) "a flat spread")
    (is (every? nil? ((f/family-inputs :fx fast (constantly {})) "BBB" 30)))))

(deftest ports-read-their-own-series
  (let [fast {["AAA" "fast/portcalls"] (monthly 0 (map #(* 0.1 %) (range 30)) 1)
              ["world" "fast/chokepoints"] (monthly 0 (repeat 30 5.0) 1)}
        xs ((f/family-inputs :ports fast (constantly {:open 0.5})) "AAA" 29)]
    (is (= 4 (count xs)))
    (is (close? 1.2 (first xs)))
    (is (close? 0.0 (peek xs)) "flat transits")))

(def ^:private tree {"AAA" "Reg" "BBB" "Reg" "CCC" "Reg2" "Reg" "world" "Reg2" "world"})

(def ^:private sp
  {:scales [{:name :quarter-lead} {:name :quarter-w2} {:name :year}]
   :parent tree
   :without-at {:quarter-lead #{:up :relations} :quarter-w2 #{:relations}}})

(deftest a-nested-group-at-the-quarters-only
  (let [inputs (fn [c t] ({"AAA" [1.0 nil] "BBB" [3.0 nil] "CCC" [5.0 2.0]} c))
        s (f/with-fast sp inputs 2 :fast/x identity)
        rel (:relations s)]
    (is (= {:quarter-lead #{:up} :quarter-w2 #{}} (:without-at s)) "the quarters read relations, nothing else changes")
    (is (= {:quarter-lead 2 :quarter-w2 2 :year 0} (:relation-width s)))
    (is (= {:quarter-lead {:fast/x [0 1]} :quarter-w2 {:fast/x [0 1]}} (:relation-groups s)))
    (is (:relation-nest s))
    (is (= [1.0 nil] (:x (rel :quarter-lead "AAA" 5))))
    (is (= [] (:x (rel :year "AAA" 11))) "the year reads nothing new")
    (is (= [2.0 nil] (:x (rel :quarter-w2 "Reg" 5))) "a region: its countries' mean over those known")
    (is (= [3.0 2.0] (:x (rel :quarter-lead "world" 5))))
    (testing "the layout: one nested group at the quarter, none at the year"
      (let [q (lt/layout-of (assoc s :gauge-width {:quarter-lead 3}) :quarter-lead 0)
            y (lt/layout-of s :year 0)]
        (is (contains? (:groups q) :fast/x))
        (is (= #{:fast/x} (:nested q)))
        (is (not (contains? (:groups y) :fast/x)))))))

(deftest the-placebo-reads-another-country
  (let [inputs (fn [c _] ({"AAA" [1.0] "BBB" [3.0] "CCC" [5.0]} c))
        src {"AAA" "BBB" "BBB" "CCC" "CCC" "AAA"}
        s (f/with-fast sp inputs 1 :fast/x #(get src % %))]
    (is (= [3.0] (:x ((:relations s) :quarter-lead "AAA" 5))))
    (is (= [4.0] (:x ((:relations s) :quarter-lead "Reg" 5))) "a region through its members' sources")))

(deftest no-spec-with-relations
  (is (thrown? clojure.lang.ExceptionInfo (f/with-fast (assoc sp :relations identity) (constantly []) 0 :fast/x identity))))

;; --- honesty: nothing published after the origin moves a forecast -------------

(defn- noisy
  "A monthly series from month 0 to `n`, each value available `lag` months
  after its period, a random walk from `seed`."
  [seed n lag]
  (let [g (m/rng seed)]
    (vec (map-indexed (fn [i x] [i (+ i lag) x]) (reductions + (repeatedly n #(* 0.05 (m/normal! g))))))))

(defn- perturb-after
  "Entries [period available value] with every value available after `t`
  replaced by a random one."
  [es t seed]
  (let [g (m/rng seed)]
    (mapv (fn [[p a v]] [p a (if (> a t) (* 10.0 (m/normal! g)) v)]) es)))

(def ^:private units ["AAA" "BBB" "CCC"])

(defn- raw-fast [n]
  (merge {["world" "fast/food"] (noisy 1 n 1) ["world" "fast/energy"] (noisy 2 n 1)
          ["world" "fast/fertiliser"] (noisy 3 n 1) ["world" "fast/chokepoints"] (noisy 4 n 1)}
         (into {} (for [[i c] (map-indexed vector units) v ["fast/fx" "fast/spread" "fast/portcalls"]]
                    [[c v] (noisy (+ 10 (* 3 i) (count v)) n 1)]))))

(defn- raw-slow
  "Annual shares (percent) by year end, published nine months later, as
  data/slow/series.tsv has them."
  [n]
  (into {} (for [[i c] (map-indexed vector units)
                 v ["food/imports_share" "energy/fuel_imports_share" "energy/fuel_exports_share"
                    "economy/agriculture_share" "trade/openness"]]
             [[c v] (vec (for [y (range (quot n 12))] [(+ 11 (* 12 y)) (+ 20 (* 12 y)) (+ 5.0 i (* 0.7 y) (count v))]))])))

(defn- as-series [m] (update-vals m sk/series))

(def ^:private honest-spec
  {:scales [{:name :quarter-lead :per 1 :horizon 1 :discount 0.99}]
   :levels [[:country units] [:region ["Reg"]]]
   :parent {"AAA" "Reg" "BBB" "Reg" "CCC" "Reg"}
   :start 0 :forget 0.99
   :gauges (fn [_ place t] [(Math/sin (+ t (count place)))]) :gauge-width {:quarter-lead 1}})

(deftest nothing-after-the-origin-moves-the-forecasts
  ;; every series and exposure perturbed after the origin T: every input
  ;; and every forecast at or before T is the same
  (let [n 160 T 110
        target (fn [_ place t] (when (< (inc t) n) {:y (odd? (+ t (count place) (mod t 7))) :known-at (inc t)}))
        fast (raw-fast n) slow (raw-slow n)
        fast' (into {} (for [[k es] fast] [k (perturb-after es T (hash k))]))
        ;; the exposures are read two years back: what came after T-24
        slow' (into {} (for [[k es] slow] [k (perturb-after es (- T 24) (hash k))]))]
    (doseq [family (keys f/families)
            :let [width (count (f/families family))
                  run (fn [fs sl]
                        (let [inputs (f/family-inputs family (as-series fs) (fr/exposure-fn (as-series sl)))]
                          [inputs (lt/run (f/with-fast (assoc honest-spec :end (dec n) :target target)
                                                       inputs width :fast/x identity))]))
                  [in r] (run fast slow)
                  [in' r'] (run fast' slow')
                  upto (fn [r] (filterv #(<= (:origin %) T) (:forecasts r)))]]
      (testing (name family)
        (is (every? (fn [[c t]] (= (in c t) (in' c t))) (for [c units t (range (inc T))] [c t])) "the inputs")
        (is (some (fn [[c t]] (not= (in c t) (in' c t))) (for [c units t (range (inc T) n)] [c t]))
            "the perturbation reaches the inputs after T")
        (is (pos? (count (upto r))))
        (is (= (upto r) (upto r')) "the forecasts to T")
        (is (not= (:forecasts r) (:forecasts r')) "and moves them after")))))

(deftest a-noise-group-for-the-cost-of-a-group
  ;; the reported-only diagnostic: inputs of pure noise, known everywhere,
  ;; the same at every read (every level and both quarters read one value)
  (let [noise (f/noise-inputs 9 1)
        a (noise "AAA" 120)]
    (is (= 9 (count a)))
    (is (every? double? a))
    (is (= a (noise "AAA" 120)) "the same draw at every read")
    (is (not= a (noise "AAA" 123)))
    (is (not= a (noise "AAB" 120)))
    (is (not= a ((f/noise-inputs 9 2) "AAA" 120)) "another seed, other noise")
    (let [xs (for [c ["AAA" "BBB" "CCC" "DDD"] t (range 0 1200 3)] (first (noise c t)))
          mu (/ (reduce + xs) (count xs))
          sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) xs)) (count xs)))]
      (is (< (Math/abs mu) 0.1))
      (is (< 0.9 sd 1.1)))))

(deftest shuffled-nulls-for-the-watch-list
  ;; the watch-list rule's shuffled null draws (bnm.watchlist, v2): a
  ;; seeded random derangement of the countries, and a shift back in time
  (let [cs ["AAA" "BBB" "CCC" "DDD" "EEE" "FFF" "GGG"]]
    (doseq [seed (range 1 30)
            :let [src (f/derange-source cs seed)]]
      (is (= (set cs) (set (map src cs))) "a permutation")
      (is (every? #(not= % (src %)) cs) "no country reads itself")
      (is (= "Reg" (src "Reg")) "other places read themselves"))
    (is (= (map (f/derange-source cs 3) cs) (map (f/derange-source cs 3) cs)) "the same draw for a seed")
    (is (not= (map (f/derange-source cs 3) cs) (map (f/derange-source cs 4) cs)) "another draw for another seed")
    (is (= (map (f/derange-source (reverse cs) 3) cs) (map (f/derange-source cs 3) cs)) "whatever order the countries come in"))
  (let [inputs (fn [c t] [(double t) (count c)])
        shifted (f/shift-inputs inputs 36)]
    (is (= [84.0 3] (shifted "AAA" 120)) "read 36 months earlier")
    (is (nil? ((f/shift-inputs (fn [_ t] (when (>= t 0) [1.0])) 36) "AAA" 10)) "unknown before the series")))

(deftest the-time-shift-draws-whole-years-back
  (is (= [24 36 48 60 72 84 96 108 120 24] (map fr/shift-months (range 1 11)))))

(defn- corr [xs ys]
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sxx (reduce + (map #(* (- % mx) (- % mx)) xs))
        syy (reduce + (map #(* (- % my) (- % my)) ys))]
    (/ sxy (Math/sqrt (* sxx syy)))))

(deftest noise-is-independent-across-time-countries-and-seeds
  ;; augur.math/rng fills its state linearly from the seed, so seeds a
  ;; fixed step apart (t every quarter, neighbouring codes, seed + 1) gave
  ;; lattice-correlated streams (bnm-0z5): the seeds are mixed first
  (let [ts (range 0 1200 3)
        col (fn [noise c j] (mapv #(nth (noise c %) j) ts))
        n1 (f/noise-inputs 9 1)
        bound (/ 4.0 (Math/sqrt (count ts)))]
    (doseq [j (range 9) c ["FRA" "DEU" "USA"]]
      (let [x (col n1 c j)]
        (is (< (Math/abs (corr (butlast x) (rest x))) bound) (str c " column " j ": no autocorrelation from one quarter to the next"))
        (is (< (Math/abs (corr (drop-last 4 x) (drop 4 x))) bound) (str c " column " j ": nor a year on"))))
    (doseq [j (range 9) [a b] [["FRA" "DEU"] ["AAA" "AAB"] ["USA" "CAN"]]]
      (is (< (Math/abs (corr (col n1 a j) (col n1 b j))) bound) (str a "-" b " column " j ": countries independent")))
    (doseq [j (range 9)]
      (is (< (Math/abs (corr (col n1 "FRA" j) (col (f/noise-inputs 9 2) "FRA" j))) bound) (str "column " j ": seeds 1 and 2 independent")))
    (doseq [j (range 8)]
      (is (< (Math/abs (corr (col n1 "FRA" j) (col n1 "FRA" (inc j)))) bound) (str "columns " j " and " (inc j) " independent")))))

(deftest derangements-from-adjacent-seeds-are-independent
  ;; two independent uniform derangements of n agree at about one place
  (let [cs (mapv #(format "C%03d" %) (range 150))
        agree (for [s (range 1 201)
                    :let [a ((juxt identity) (mapv (f/derange-source cs s) cs))
                          b (mapv (f/derange-source cs (inc s)) cs)]]
                (count (filter true? (map = (first a) b))))
        mean (/ (reduce + agree) (double (count agree)))]
    (is (< 0.75 mean 1.25) (str "mean agreement " mean " (independent: about 1)"))))
