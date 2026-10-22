(ns bnm.stages-test
  (:require [bnm.stages :as stg]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (and a b (< (Math/abs (- (double a) (double b))) tol)))

;; ---------------------------------------------------------------- measures

(deftest shares-and-hhi
  (is (= {"A" 0.75 "B" 0.25} (stg/shares {"A" 3.0 "B" 1.0 "C" 0.0})) "zeros are not producers")
  (is (= {} (stg/shares {"A" 0.0})))
  (is (close? 1.0 (stg/hhi {"A" 1.0}) 1e-12) "a monopoly")
  (is (close? 0.25 (stg/hhi (stg/shares {"A" 1 "B" 1 "C" 1 "D" 1})) 1e-12) "four equal producers")
  (is (close? 0.625 (stg/hhi {"A" 0.75 "B" 0.25}) 1e-12))
  (is (= ["A" 0.75] (stg/top {"A" 0.75 "B" 0.25})))
  (is (= ["A" 0.5] (stg/top {"B" 0.5 "A" 0.5})) "ties go to the first code, so the output is stable"))

(deftest displacement-bounds
  (testing "0 when each country processes its own ore's share"
    (is (close? 0.0 (stg/displacement {"A" 0.6 "B" 0.4} {"A" 0.6 "B" 0.4}) 1e-12)))
  (testing "1 when none does"
    (is (close? 1.0 (stg/displacement {"A" 0.6 "B" 0.4} {"C" 0.7 "D" 0.3}) 1e-12)))
  (testing "in between: 1 - sum of the smaller share"
    ;; min(0.5,1) + min(0.5,0) = 0.5
    (is (close? 0.5 (stg/displacement {"A" 0.5 "B" 0.5} {"A" 1.0}) 1e-12))
    ;; Congo mines 70%, refines 10%; China mines 0, refines 80%; Finland 0/10, Australia 30/0: overlap .1
    (is (close? 0.9 (stg/displacement {"COD" 0.7 "AUS" 0.3} {"COD" 0.1 "CHN" 0.8 "FIN" 0.1}) 1e-12)))
  (testing "symmetric, and the same from quantities"
    (let [a {"A" 0.2 "B" 0.8} b {"A" 0.5 "C" 0.5}]
      (is (close? (stg/displacement a b) (stg/displacement b a) 1e-12)))))

;; ---------------------------------------------------------------- the table

(defn- row [c s iso y q & {:keys [unit known] :or {unit "t" known (+ y 2)}}]
  {:commodity c :stage s :iso iso :year y :quantity (double q) :unit unit :source "T" :known known})

(def synthetic
  "Cobalt-like: mined in A and B, refined in C. Each year's data known two
  years on."
  (concat
   (for [y [2000 2001 2002]] (row "x" "mine" "A" y 60))
   (for [y [2000 2001 2002]] (row "x" "mine" "B" y 40))
   (for [y [2000 2001 2002]] (row "x" "refinery" "A" y (if (= y 2002) 0 30)))
   (for [y [2000 2001 2002]] (row "x" "refinery" "C" y 70))))

(defn- value [out iso year variable]
  (some #(when (and (= iso (:iso %)) (= year (:year %)) (= variable (:variable %))) (:value %)) out))

(deftest stocks-on-a-synthetic-table
  (let [out (stg/stocks synthetic)]
    (testing "nothing before the first data are known"
      (is (empty? (filter #(< (:year %) 2002) out))))
    (testing "measures at the year known, for the data year two before"
      (is (close? 0.52 (value out "WLD" 2002 "stages/x-mine-hhi") 1e-12))
      (is (close? 0.6 (value out "WLD" 2002 "stages/x-mine-top-share") 1e-12))
      (is (= "A" (some #(when (and (= 2002 (:year %)) (= "stages/x-mine-top-share" (:variable %))) (:top %)) out)))
      (is (close? 0.58 (value out "WLD" 2002 "stages/x-refinery-hhi") 1e-12))
      (is (= 2000.0 (value out "WLD" 2002 "stages/x-state-year")))
      ;; mine A .6 B .4, refinery A .3 C .7: overlap .3
      (is (close? 0.7 (value out "WLD" 2002 "stages/x-d") 1e-12))
      (is (close? 0.7 (value out "WLD" 2002 "stages/x-d-mine-refinery") 1e-12))
      (is (close? 100.0 (value out "WLD" 2002 "stages/x-mine-world") 1e-12)))
    (testing "country shares as country series"
      (is (close? 0.6 (value out "A" 2002 "stages/x-mine-share") 1e-12))
      (is (close? 0.7 (value out "C" 2003 "stages/x-refinery-share") 1e-12))
      (is (nil? (value out "C" 2002 "stages/x-mine-share")) "no row where a country does not produce"))
    (testing "a refinery that closes leaves the processing stage to C"
      (is (close? 1.0 (value out "WLD" 2004 "stages/x-refinery-hhi") 1e-12))
      (is (close? 1.0 (value out "WLD" 2004 "stages/x-d") 1e-12)))
    (testing "only WLD for the commodity's own measures"
      (is (every? #(= "WLD" (:iso %)) (remove #(re-find #"-share$" (:variable %)) out))))))

(deftest causality
  (testing "no output at year K changes when data known after K are added or removed"
    (let [late (concat synthetic
                       [(row "x" "mine" "D" 2001 500 :known 2008)
                        (row "x" "refinery" "D" 2003 500)])
          a (stg/stocks synthetic)
          b (stg/stocks late)
          upto (fn [out k] (set (filter #(<= (:year %) k) out)))]
      (doseq [k [2002 2003 2004]]
        (is (= (upto a k) (upto b k)) (str "year " k)))
      (is (not= (set a) (set b)) "the late data do arrive")))
  (testing "every output year is at or after the year its data are known"
    (let [out (stg/stocks synthetic)]
      (doseq [r out :when (= "stages/x-state-year" (:variable r))]
        (is (>= (:year r) (+ 2 (long (:value r))))))))
  (testing "stale data are not carried forward without end"
    (let [out (stg/stocks synthetic {:stale 3})]
      (is (seq (filter #(= 2007 (:year %)) out)))
      (is (empty? (filter #(> (:year %) 2007) out))))))

(deftest units
  (testing "one unit per commodity and stage, else refused"
    (is (nil? (stg/check-units synthetic)))
    (is (thrown? Exception (stg/check-units (conj (vec synthetic) (row "x" "mine" "E" 2001 5 :unit "kg")))))
    (is (thrown? Exception (stg/stocks (conj (vec synthetic) (row "x" "mine" "E" 2001 5 :unit "kg"))))))
  (testing "different units across stages are fine: shares are within a stage"
    (is (seq (stg/stocks (map #(if (= "refinery" (:stage %)) (assoc % :unit "kg") %) synthetic))))))

(deftest chain-order
  (testing "D is mine against the last stage of the chain"
    (is (= ["mine" "refinery"] (stg/chain "x" #{"refinery" "mine"})))
    (is (= ["mine" "smelter" "refinery"] (stg/chain "copper" #{"refinery" "mine" "smelter"})))
    (is (= ["mine" "refinery" "smelter"] (stg/chain "aluminium" #{"refinery" "mine" "smelter"})) "alumina before metal")))

(deftest plants
  (let [ps [{:commodity "x" :stage "refinery" :iso "C" :facility "p1" :capacity 60.0 :unit "t" :known 2026}
            {:commodity "x" :stage "refinery" :iso "C" :facility "p2" :capacity 20.0 :unit "t" :known 2026}
            {:commodity "x" :stage "refinery" :iso "A" :facility "p3" :capacity 20.0 :unit "t" :known 2026}]
        out (stg/plant-stocks ps synthetic)]
    (is (close? 0.44 (value out "WLD" 2026 "stages/x-refinery-plant-hhi") 1e-12) ".36+.04+.04")
    (is (close? 3.0 (value out "WLD" 2026 "stages/x-refinery-plant-count") 1e-12))
    (testing "coverage: the stage's output share in countries with a plant listed, as known then"
      ;; data year 2002 (known 2004): refinery A 0, C 70 -> C's share 1.0
      (is (close? 1.0 (value out "WLD" 2026 "stages/x-refinery-plant-coverage") 1e-12)))
    (is (empty? (filter #(< (:year %) 2026) out)) "plants are a 2026 snapshot")))

;; ---------------------------------------------------------------- review fixes

(deftest displacement-stays-in-the-unit-interval
  (testing "rounding in the sum of the smaller shares cannot push D below 0 or above 1"
    (let [rng (java.util.Random. 42)]
      (doseq [_ (range 2000)]
        (let [n (inc (.nextInt rng 12))
              isos (map #(str "C" %) (range n))
              qa (zipmap isos (repeatedly #(.nextDouble rng)))
              qb (zipmap (shuffle isos) (repeatedly #(.nextDouble rng)))
              sa (stg/shares qa)]
          (doseq [d [(stg/displacement sa sa) (stg/displacement sa (stg/shares qb))
                     (stg/displacement qa qb)]]
            (is (<= 0.0 d 1.0) (str "D " d)))))))
  (testing "quantities are made shares first: world totals may differ between stages"
    ;; refined output above mine output (scrap): only the shares are compared
    (is (close? 0.0 (stg/displacement {"A" 60.0 "B" 40.0} {"A" 120.0 "B" 80.0}) 1e-12))
    (is (close? 0.7 (stg/displacement {"A" 60.0 "B" 40.0} {"A" 30.0 "C" 70.0}) 1e-12)))
  (testing "a country that processes without mining adds to D, one that mines without processing too"
    (is (close? 1.0 (stg/displacement {"A" 1.0} {"C" 1.0}) 1e-12)))
  (testing "no D without output at both stages"
    (is (nil? (stg/displacement {} {"A" 1.0})))
    (is (nil? (stg/displacement {"A" 0.0} {"A" 1.0})))))

(deftest non-resource-share
  (testing "processing share in countries with under 1% of the mine stage, for the same pair and year as D"
    (is (close? 0.7 (stg/non-resource {"A" 0.6 "B" 0.4} {"A" 0.3 "C" 0.7}) 1e-12))
    (is (close? 0.75 (stg/non-resource {"A" 0.995 "B" 0.005} {"A" 0.25 "B" 0.75}) 1e-12) "B mines 0.5%: non-resource")
    (let [out (stg/stocks synthetic)]
      (is (close? 0.7 (value out "WLD" 2002 "stages/x-non-resource") 1e-12))
      (is (close? 1.0 (value out "WLD" 2004 "stages/x-non-resource") 1e-12)))))

(deftest editions-of-one-data-year
  (testing "a later edition of a data year replaces the earlier one when it is known, never adds to it"
    (let [rows (concat
                ;; data year 2023: the 2024 edition's estimate, then the 2025 edition's revision
                [(assoc (row "li" "mine" "A" 2023 50 :known 2024) :source "MCS2024")
                 (assoc (row "li" "mine" "B" 2023 50 :known 2024) :source "MCS2024")
                 (assoc (row "li" "mine" "A" 2023 60 :known 2025) :source "MCS2025")
                 (assoc (row "li" "mine" "B" 2023 40 :known 2025) :source "MCS2025")])
          out (stg/stocks rows)]
      (is (close? 100.0 (value out "WLD" 2024 "stages/li-mine-world") 1e-12) "the estimate, at the edition year")
      (is (close? 0.5 (value out "A" 2024 "stages/li-mine-share") 1e-12))
      (is (close? 100.0 (value out "WLD" 2025 "stages/li-mine-world") 1e-12) "not 200: the revision replaces it")
      (is (close? 0.6 (value out "A" 2025 "stages/li-mine-share") 1e-12))
      (is (= 2023.0 (value out "WLD" 2024 "stages/li-mine-state-year")) "state-year is the data year")))
  (testing "rows of one source known later (a country added) are added, not replaced"
    (let [rows [(row "x" "mine" "A" 2000 60) (row "x" "mine" "B" 2000 40 :known 2003)]
          out (stg/stocks rows)]
      (is (close? 60.0 (value out "WLD" 2002 "stages/x-mine-world") 1e-12))
      (is (close? 100.0 (value out "WLD" 2003 "stages/x-mine-world") 1e-12)))))
