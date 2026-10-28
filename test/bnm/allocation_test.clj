(ns bnm.allocation-test
  (:require [bnm.allocation :as al]
            [bnm.capacity :as cap]
            [bnm.engine :as eng]
            [bnm.modules.allocation :as mal]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b] (and (number? a) (number? b) (< (Math/abs (- (double a) (double b))) 1e-9)))

(defn- row [iso year source measure sector value]
  {:iso iso :year year :known (+ year (al/lags source)) :source source :measure measure :sector sector :value value})

;; -- groups and shares -----------------------------------------------------------------------

(deftest groups-sum-their-parts-and-take-a-combination-only-when-a-part-is-missing
  (let [spec (al/credit-spec ["mv" "credit"])]
    (is (close? 60.0 (al/group-value {"A" 10.0 "B" 20.0 "C" 30.0 "BC" 99.0} (get-in spec [:groups "tradable"]))))
    (is (close? 40.0 (al/group-value {"A" 10.0 "BC" 30.0} (get-in spec [:groups "tradable"]))))
    (is (nil? (al/group-value {"B" 20.0 "C" 30.0} (get-in spec [:groups "tradable"]))) "a missing part leaves the group unmeasured")
    (is (close? 0.25 (get (al/shares spec {"Total" 200.0 "C" 50.0 "HH" 80.0}) "C")))
    (is (nil? (get (al/shares spec {"C" 50.0}) "C")) "no total, no share")))

(deftest nontradable-is-the-papers-and-the-same-sections-in-each-source
  (testing "Müller-Verner: FL + GI + HJ (construction, real estate, trade, hospitality, transport and communication)"
    (let [g (get-in (al/credit-spec ["mv" "credit"]) [:groups "nontradable"])]
      (is (close? 21.0 (al/group-value {"F" 1.0 "L" 2.0 "G" 3.0 "I" 4.0 "H" 5.0 "J" 6.0} g)))
      (is (close? 10.0 (al/group-value {"FL" 3.0 "GI" 3.0 "HJ" 4.0} g)))
      (is (nil? (al/group-value {"F" 1.0 "L" 2.0 "G" 3.0 "I" 4.0} g)) "no H and J, no group")))
  (testing "ECB: the same sections, L only as part of LMN"
    (let [g (get-in (al/credit-spec ["ecb-nace" "credit"]) [:groups "nontradable"])]
      (is (= #{"nfc-F" "nfc-G" "nfc-HJ" "nfc-I" "nfc-LMN"} (set g))))))

(deftest new-credit-and-share-changes-come-from-two-years-of-one-source
  (let [spec (al/credit-spec ["mv" "credit"])
        a {"Total" 100.0 "C" 30.0 "HH" 20.0}
        b {"Total" 120.0 "C" 32.0 "HH" 30.0}
        f (al/flows spec a b)]
    (is (close? 0.1 (get-in f [:new-share "C"])) "2 of the 20 of new credit")
    (is (close? 0.5 (get-in f [:new-share "HH"])))
    (is (close? (* 100 (- (/ 32.0 120) 0.3)) (get-in f [:share-change "C"])))
    (testing "no new-credit share when total credit does not grow"
      (is (nil? (get-in (al/flows spec a (assoc b "Total" 100.0)) [:new-share "C"])))
      (is (some? (get-in (al/flows spec a (assoc b "Total" 90.0)) [:share-change "C"]))))
    (testing "ECB loans of a state outside the euro are in EUR at current rates: no new-credit share"
      (let [ecb (al/credit-spec ["ecb" "credit"])
            a {"hh" 40.0 "nfc" 60.0} b {"hh" 50.0 "nfc" 70.0}]
        (is (close? 0.5 (get-in (al/flows ecb a b "DEU") [:new-share "hh"])))
        (is (empty? (:new-share (al/flows ecb a b "GBR"))))
        (is (some? (get-in (al/flows ecb a b "GBR") [:share-change "hh"])) "its share changes are ratios within a year")))
    (testing "a ratio-to-GDP source gives share changes, not new-credit shares"
      (let [bis (al/credit-spec ["bis" "credit"])
            f (al/flows bis {"hh" 40.0 "nfc" 60.0} {"hh" 50.0 "nfc" 50.0})]
        (is (close? 10.0 (get-in f [:share-change "hh"])))
        (is (nil? (:new-share f)))))))

;; -- investment and capacity -------------------------------------------------------------------

(deftest investment-shares-and-capital-growth
  (is (close? 0.25 (get (al/industry-shares {"TOT" 400.0 "C" 100.0 "B" 50.0}) "C")))
  (is (not (contains? (al/industry-shares {"TOT" 400.0 "C" 100.0}) "TOT")))
  (is (close? (Math/log 1.1) (al/log-growth 100.0 110.0)))
  (is (nil? (al/log-growth 0.0 110.0)))
  (is (close? (Math/log (/ (/ 30.0 300) (/ 20.0 250))) (al/relative-growth 20.0 250.0 30.0 300.0))))

(deftest products-take-the-industry-of-their-cpa-division
  (is (= "C24-C25" (al/industry-of-division "24")))
  (is (= "C24-C25" (al/industry-of-division "25")))
  (is (= "B" (al/industry-of-division "07")))
  (is (= "D-E" (al/industry-of-division "35")))
  (is (= "C10-C12" (al/industry-of-division "11")))
  (is (nil? (al/industry-of-division "99")))
  (let [rows [{:cpa "24.10.11" :product "C_STEL" :weight 1.0}
              {:cpa "24.42.11" :product "C_ALUM" :weight 1.0}
              {:cpa "07.10.10" :product "C_IRON" :weight 0.9}
              {:cpa "08.99.29" :product "C_IRON" :weight 0.1}
              {:cpa "20.13.11" :product "C_CHEM" :weight 0.4}
              {:cpa "24.45.30" :product "C_CHEM" :weight 0.6}]]
    (is (= {"C_STEL" "C24-C25" "C_ALUM" "C24-C25" "C_IRON" "B" "C_CHEM" "C24-C25"} (al/product-industries rows))
        "a product split across divisions takes the one with the most weight"))
  (is (= "B" (al/stage-industry "copper" "mine")))
  (is (= "C24-C25" (al/stage-industry "copper" "smelter")))
  (is (= "C20" (al/stage-industry "lithium" "chemical")))
  (is (= "C20" (al/stage-industry "rare-earths" "separation"))))

;; -- the modules ------------------------------------------------------------------------------

(def panel
  (vec (concat
        (for [[y tot c hh a b] [[2000 100.0 30.0 20.0 5.0 2.0] [2001 120.0 32.0 30.0 6.0 2.0] [2002 130.0 33.0 33.0 6.0 3.0]]
              [s v] [["Total" tot] ["C" c] ["HH" hh] ["A" a] ["B" b]]]
          (row "KOR" y "mv" "credit" s v))
        (for [[y hh nfc] [[2000 40.0 60.0] [2001 50.0 50.0]] [s v] [["hh" hh] ["nfc" nfc]]]
          (row "KOR" y "bis" "credit" s v))
        ;; China's combined table, then the RMB-only one for a year: no change across sources
        (for [[y src hh e] [[2010 "pboc" 10.0 40.0] [2011 "pboc-rmb" 12.0 41.0] [2012 "pboc" 15.0 50.0] [2013 "pboc" 18.0 55.0]]
              [s v] [["hh" hh] ["enterprise" e]]]
          (row "CHN" y src "credit" s v))
        (for [[y tot c b kt kc kb] [[2000 100.0 20.0 5.0 100.0 100.0 100.0] [2001 110.0 25.0 5.0 105.0 110.0 99.0]]
              [m s v] [["gfcf" "TOT" tot] ["gfcf" "C24-C25" c] ["gfcf" "B" b]
                       ["capital" "TOT" (* 10 tot)] ["capital" "C24-C25" (* 10 c)] ["capital" "B" (* 10 b)]
                       ["capital-volume" "TOT" kt] ["capital-volume" "C24-C25" kc] ["capital-volume" "B" kb]]]
          (row "KOR" y "klems" m s v))
        (for [[y k kt] [[2000 50.0 500.0] [2001 60.0 520.0]] [s v] [["C24-C25" k] ["TOT" kt]]]
          (row "KOR" y "wiod" "capital" s v))
        (for [[s v] [["D27t28" 30.0] ["F" 70.0]]] (row "KOR" 1990 "lrwiod" "gfcf-product" s v))
        ;; a pipeline: six years of basic-metals investment, the NFC accounts and credit
        (for [y (range 1995 2001) [m s v] [["gfcf" "TOT" 50.0] ["gfcf" "C24-C25" (- y 1990.0)]
                                           ["capital" "TOT" 1000.0] ["capital" "C24-C25" 100.0]]]
          (row "AUT" y "klems" m s v))
        (for [[s v] [["C24-C25" 30.0] ["D-E" 70.0] ["L" 1000.0]]] (row "AUT" 1999 "klems" "gos" s v))
        (for [[s v] [["b8g" 80.0] ["nl" -20.0] ["gcf" 95.0]]] (row "AUT" 1999 "sfc" "nfc" s v))
        (for [[s v] [["Total" 200.0] ["NFC" 100.0] ["C" 30.0]]] (row "AUT" 1999 "mv" "credit" s v)))))

(def links {:products {"C_STEL" "C24-C25" "C_IRON" "B"}
            :stages {["copper" "smelter"] {"KOR" 1990} ["copper" "mine"] {"KOR" 2010 "CHL" 1980}
                     ["aluminium" "smelter"] {"AUT" 1990}}})

(defn- ms [] (mal/modules (al/index panel) links))

(defn- run [] (eng/run (ms) {:from 1990 :to 2016}))

(defn- emitted
  "{var [[t row] ...]} of module id over the run."
  [r id]
  (let [ts (:t r)]
    (into {} (for [[v xs] (get-in r [:emits id])]
               [v (vec (for [[t x] (map vector ts xs) :when (seq x) rw x] [t rw]))]))))

(deftest modules-pass-the-engine-rules
  (let [ms (ms)
        levels (fn [m] (set (for [e (:emits m) :when (= :level (:phase e))] (:var e))))]
    (is (empty? (eng/validate ms)))
    (doseq [m ms]
      (is (seq (levels m)) (str (:module m) " has levels"))
      (is (fn? (:observe m)) (str (:module m) " declares levels, so has an observe")))
    (doseq [m ms :let [st (if (fn? (:init m)) ((:init m) {}) (:init m))
                       lv ((:observe m) st {} {} 2005)
                       sp ((:step m) st {} {} 2005 1)]]
      (is (every? (levels m) (keys lv)) "the observe emits only levels")
      (is (empty? (filter (levels m) (keys (:emits sp)))) "the step emits no level"))
    (is (some #(= {:from :allocation :to :investment :lag 1} (select-keys % [:from :to :lag])) (eng/edges ms))
        "the investment module reads the credit shares a year late")))

(deftest a-run-emits-every-row-at-its-known-year
  (let [r (run)
        al (emitted r :allocation)
        inv (emitted r :investment)]
    (testing "each source's rows appear at data year + its lag, no sooner"
      (is (= [[2002 ["KOR" (str 0.3) 2000 "mv"]]] (filter #(= 2000 (nth (second %) 2)) (al "allocation/mv/share-C"))))
      (is (= #{2001 2002} (set (map first (al "allocation/bis/share-hh")))))
      (doseq [[v rs] al [t [_ _ dy src]] rs]
        (is (>= (- t dy) (al/lags src)) (str v " " src " " dy " at " t)))
      (doseq [[v rs] inv [t [_ _ dy src]] rs
              :let [k (get-in (eng/emit-for (second (ms)) v) [:known :data-year])]]
        (is (>= (- t dy) k) (str v " " src " " dy " at " t))
        (when-not (or (re-find #"completion-rate" v) (= v "investment/credit-share-C") (re-find #"credit-share" v))
          (is (>= (- t dy) (al/lags src)) (str v " " src " " dy " at " t)))))
    (testing "rates"
      (is (= [[2003 ["KOR" (str 0.1) 2001 "mv"]] [2004 ["KOR" (str 0.1) 2002 "mv"]]] (al "allocation/mv/new-share-C")))
      (is (close? 10.0 (parse-double (second (second (first (al "allocation/bis/share-change-hh")))))))
      (is (nil? (al "allocation/bis/new-share-hh")))
      (is (= [2013] (map (comp #(nth % 2) second) (al "allocation/pboc/share-change-hh")))
          "no change across the switch of tables, nor from 2011 to 2012"))
    (testing "investment, capital and the link to products and stages"
      (is (= [[2003 ["KOR" (str (/ 25.0 110)) 2001 "klems"]]]
             (filter #(= 2001 (nth (second %) 2)) (inv "investment/klems/gfcf-share-C24-C25"))))
      (is (close? (Math/log 1.1) (parse-double (second (second (first (inv "investment/klems/capital-growth-C24-C25")))))))
      (let [cap (inv "capacity/growth/C_STEL")]
        (is (= (inv "investment/klems/capital-growth-C24-C25") cap) "a product carries its industry's growth"))
      (is (= (map second (inv "investment/klems/capital-growth-C24-C25")) (map second (inv "capacity/growth/stage/copper-smelter"))))
      (is (seq (inv "investment/klems/capital-growth-B")))
      (is (nil? (inv "capacity/growth/stage/copper-mine"))
          "a stage carries a country's growth only once the stage table knows the country has it (KOR's mine from 2010)")
      (is (close? (Math/log (/ (/ 60.0 520) (/ 50.0 500)))
                  (parse-double (second (second (first (inv "capacity/relative-growth/C_STEL")))))))
      (is (= [[1993 ["KOR" (str 0.3) 1990 "lrwiod"]]] (inv "investment/lrwiod/gfcf-product-share-D27t28"))))
    (testing "credit shares reach the investment module's industries a year later"
      (let [c (al "allocation/mv/share-C") ic (inv "investment/credit-share-C")]
        (is (seq ic))
        (is (= (map (fn [[t rw]] [(inc t) rw]) c) ic))
        (is (= 3 (get-in (eng/emit-for (second (ms)) "investment/credit-share-C") [:known :data-year]))
            "Müller-Verner's +2 and the year's lag")))))

;; -- capacity from investment (bead bnm-4hv.11) ----------------------------------------------------

(deftest investment-completes-into-capacity-through-the-time-to-build
  (let [r (run)
        inv (emitted r :investment)
        rows (fn [v] (filter #(= "AUT" (first (second %))) (inv v)))
        val-of (fn [[_ rw]] (parse-double (second rw)))]
    (testing "the investment rate: the year's GFCF over the opening capital stock, at the source's known year"
      (is (= [[1998 1996] [1999 1997] [2000 1998] [2001 1999] [2002 2000]]
             (map (fn [[t rw]] [t (nth rw 2)]) (rows "investment/klems/investment-rate-C24-C25"))))
      (is (close? 0.06 (val-of (first (rows "investment/klems/investment-rate-C24-C25"))))))
    (testing "the completion rate for year y is emitted once y-1's investment is known (y + 1), from investment before y"
      (let [cr (rows "investment/klems/completion-rate-C24-C25")
            rates (into {} (for [y (range 1996 2001)] [y (/ (- y 1990.0) 100.0)]))
            p (:p (cap/prior "C24-C25"))]
        (is (= [[2000 1999] [2001 2000] [2002 2001]] (map (fn [[t rw]] [t (nth rw 2)]) cr))
            "1998 would need 1995's rate, whose opening stock is not there")
        (is (close? (cap/completion-rate p rates 2001) (val-of (last cr))))
        (is (= 1 (get-in (eng/emit-for (second (ms)) "investment/klems/completion-rate-C24-C25") [:known :data-year])))))
    (testing "capacity: the industry's completion rate carried to its products and stages, the investment module's only"
      (is (= (map second (rows "investment/klems/completion-rate-C24-C25"))
             (map second (rows "capacity/completion-rate/stage/aluminium-smelter"))))
      (is (= (map second (rows "investment/klems/completion-rate-C24-C25"))
             (map second (rows "capacity/completion-rate/C_STEL")))))
    (testing "financed investment: retained profits by surplus plus external finance by credit"
      (let [f (rows "investment/financed-C24-C25")]
        (is (= [[2001 ["AUT" (str 30.0) 1999 "sfc"]]] f) "80 x 0.3 of surplus + 20 x 0.3 of NFC credit")
        (is (= [[2001 ["AUT" (str 70.0) 1999 "sfc"]]] (rows "investment/financed-D-E")))
        (is (close? 0.3 (val-of (first (rows "investment/financed-rate-C24-C25")))) "over the opening stock, 100")
        (is (empty? (rows "investment/financed-rate-D-E")) "no capital stock, no rate")
        (is (empty? (rows "investment/financed-L")) "real estate is not an NFC industry here")))))

(deftest only-the-investment-module-emits-capacity
  (let [cap? (fn [e] (str/starts-with? (str (:var e)) "capacity/"))]
    (is (= [:investment] (vec (for [m (ms) :when (some cap? (:emits m))] (:module m))))))
  (testing "no other module's source declares a capacity/ emit"
    (doseq [f (.listFiles (io/file "src/bnm/modules")) :when (not= "allocation.clj" (.getName f))]
      (is (not (re-find #"\"capacity/" (slurp f))) (.getName f)))))

(deftest stage-links-come-from-the-stage-table-by-country-and-known-year
  (let [dir (doto (io/file (System/getProperty "java.io.tmpdir") (str "bnm-alloc-" (System/nanoTime))) .mkdirs)
        st (io/file dir "stages.csv")]
    (spit st (str "commodity,stage,iso,year,quantity,unit,source,known\n"
                  "copper,smelter,CHL,1990,1,t,BGS,1992\n"
                  "copper,smelter,CHL,1985,1,t,BGS,1987\n"
                  "copper,mine,PER,2000,1,t,BGS,2002\n"
                  "gold,mine,ZAF,2000,1,t,BGS,2002\n"))
    (is (= {["copper" "smelter"] {"CHL" 1987} ["copper" "mine"] {"PER" 2002} ["gold" "mine"] {"ZAF" 2002}}
           (:stages (al/load-links "no-such-concordance.csv" (str st)))))))

;; -- the data on disk (skipped when the panel has not been built) ---------------------------

(def panel-path "data/derived/allocation/panel.csv")

(deftest the-data-on-disk
  (when (.exists (io/file panel-path))
    (let [rows (al/read-panel panel-path)
          idx (al/index rows)]
      (is (= #{"mv" "bis" "pboc" "pboc-rmb" "ecb" "ecb-nace" "klems" "wiod" "lrwiod" "sfc"} (set (map :source rows))))
      (is (every? #(= (:known %) (+ (:year %) (al/lags (:source %)))) rows) "every row's known year is its source's lag")
      (is (< 3000 (count (for [[_ ys] (get-in idx ["mv" "credit"]) [_ m] ys
                               :when (get (al/shares (al/credit-spec ["mv" "credit"]) m) "C")] 1)))
          "about 3,200 country-years with a manufacturing share")
      (is (empty? (eng/validate (mal/modules idx links)))))))
