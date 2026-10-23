(ns bnm.chokepoints-test
  (:require [bnm.chokepoints :as cp]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (and a b (< (Math/abs (- (double a) (double b))) tol)))

(def routes
  "Toy routes: Saudi crude to Korea through Hormuz (80% of its port pairs;
  the rest leave from the Red Sea) and Malacca; US crude through Panama
  for 30% of its weight; Russia is Korea's land neighbour here
  (non-maritime) though its lane passes Tsugaru; Ruritania has no port."
  {"SAU" {"KOR" {"tanker" {:maritime? true :routed 1.0 :through {"hormuz" 0.8 "malacca" 1.0}}}
          "JPN" {"tanker" {:maritime? true :routed 1.0 :through {"hormuz" 1.0 "malacca" 1.0}}}}
   "USA" {"KOR" {"tanker" {:maritime? true :routed 1.0 :through {"panama" 0.3}}}}
   "RUS" {"KOR" {"tanker" {:maritime? false :routed 1.0 :through {"tsugaru" 1.0}}}}
   "RUR" {"KOR" {"tanker" {:maritime? true :routed 0.0 :through {}}}}})

(defn- flow [e i v] {:product "crude-oil" :class "tanker" :exporter e :importer i :value v :tonnes (* 10 v)})

(def flows [(flow "SAU" "KOR" 60.0) (flow "USA" "KOR" 20.0) (flow "RUS" "KOR" 10.0) (flow "RUR" "KOR" 10.0)
            (flow "SAU" "JPN" 50.0)])

(deftest parse-the-route-list
  (is (= {"hormuz" 1.0 "malacca" 0.9525} (cp/parse-chokepoints "hormuz=1.0000;malacca=0.9525")))
  (is (= {} (cp/parse-chokepoints ""))))

(deftest ship-classes
  (is (= "tanker" (cp/hs2-class "27")))
  (is (= "bulk" (cp/hs2-class "26")))
  (is (= "general" (cp/hs2-class "85")))
  (is (= "tanker" (cp/hs6-class "270900")))
  (is (= "tanker" (cp/hs6-class "271111")))
  (is (= "bulk" (cp/hs6-class "270112")) "coal is bulk, though chapter 27")
  (is (= "bulk" (cp/hs6-class "260500")))
  (is (= "general" (cp/hs6-class "283691")))
  (is (= "general" (cp/hs6-class "850780"))))

(deftest exposure-shares
  (let [agg (cp/aggregate routes flows)
        kor (cp/shares (agg ["crude-oil" "KOR"]))]
    (testing "a strait's share is of all imports, land and unrouted included"
      (is (close? (/ (* 60 0.8) 100.0) (kor "hormuz") 1e-12))
      (is (close? 0.6 (kor "malacca") 1e-12))
      (is (close? (/ (* 20 0.3) 100.0) (kor "panama") 1e-12)))
    (testing "seaborne: maritime and routed only"
      (is (close? 0.8 (:seaborne kor) 1e-12)))
    (testing "a non-maritime pair passes no strait, whatever its lane"
      (is (nil? (kor "tsugaru"))))
    (testing "no strait above the seaborne share, every share in [0, 1]"
      (doseq [[k x] kor] (is (<= 0.0 x (:seaborne kor) 1.0) (str k))))
    (testing "the world is the importers summed: its share is their value-weighted mean"
      (let [w (cp/shares (agg ["crude-oil" "WLD"]))
            jpn (cp/shares (agg ["crude-oil" "JPN"]))]
        (is (close? (/ (+ (* 100 (kor "hormuz")) (* 50 (jpn "hormuz"))) 150.0) (w "hormuz") 1e-12))
        (is (close? (/ (+ 48.0 50.0) 150.0) (w "hormuz") 1e-12))
        (is (close? (:total (agg ["crude-oil" "WLD"])) 150.0 1e-12))
        (is (close? (get-in agg [["crude-oil" "WLD"] :through-tonnes "hormuz"]) 980.0 1e-9) "tonnes alongside")))))

(deftest all-is-the-chapters-summed
  (let [fl [{:product "hs27" :class "tanker" :exporter "SAU" :importer "KOR" :value 60.0 :tonnes nil}
            {:product "hs26" :class "bulk" :exporter "SAU" :importer "KOR" :value 40.0 :tonnes nil}]
        rt (assoc-in routes ["SAU" "KOR" "bulk"] {:maritime? true :routed 1.0 :through {"hormuz" 1.0}})
        all (cp/all-product (cp/aggregate rt fl))
        sh (cp/shares (all ["all" "KOR"]))]
    (is (close? 100.0 (:total (all ["all" "KOR"])) 1e-12))
    (is (close? (/ (+ 48.0 40.0) 100.0) (sh "hormuz") 1e-12))
    (is (close? 1.0 (:seaborne sh) 1e-12))))

(deftest rows-are-dated-by-the-year-known
  (let [rs (cp/rows (cp/aggregate routes flows) 2010 0.07)]
    (is (every? #(= 2012 (:year %)) rs) "BACI's 2010 is known in 2012")
    (is (some #(= "chokepoint/hormuz-crude-oil" (:variable %)) rs))
    (is (some #(and (= "KOR" (:iso %)) (= "chokepoint/seaborne-crude-oil" (:variable %))) rs))
    (is (not-any? #(= "chokepoint/panama-crude-oil" (:variable %)) rs) "Korea's Panama share, 6%, is under the floor; Japan and the world have none")
    (is (= #{"KOR" "JPN" "WLD"} (set (map :iso rs))))))

(defn- line [y e i v] (str "HS92," y "," (+ y 2) ",27," e "," i "," v ","))

(deftest causal-by-year
  (let [lines (concat [(line 2010 "SAU" "KOR" 60) (line 2010 "USA" "KOR" 40)]
                      [(str "HS12,2012,2014,27,SAU,KOR,999,")]
                      [(line 2011 "SAU" "KOR" 10) (line 2011 "USA" "KOR" 90)])
        run (fn [ls] (let [out (atom [])]
                       (cp/by-year routes ls cp/hs2-flow #(swap! out conj [%1 (cp/rows %2 %1)]))
                       @out))
        a (run lines)
        b (run (concat (butlast lines) [(line 2011 "USA" "KOR" 5000)]))]
    (is (= [2010 2011] (map first a)) "one call per data year, in order, other editions skipped")
    (is (= (second (first a)) (second (first b))) "a later year's flows do not move an earlier year's rows")
    (is (not= (second (second a)) (second (second b))))
    (is (every? #(= 2012 (:year %)) (second (first a))))
    (let [h (some #(when (and (= "KOR" (:iso %)) (= "chokepoint/hormuz-hs27" (:variable %))) (:value %))
                  (second (first a)))]
      (is (close? 0.48 h 1e-12) "only 2010's flows: 60 of 100, 80% through Hormuz"))))

(deftest product-names
  (is (= "lithium-carbonate" (cp/product-name "lithium_carbonate")))
  (is (= "crude-oil" (cp/energy-products "270900"))))

;; ---------------------------------------------------------------- on disk

(def ^:private routes-path "data/derived/supply/routes.csv")

(deftest the-real-routes
  (if-not (.exists (io/file routes-path))
    (println "bnm.chokepoints-test: no" routes-path "- skipped (python -I scripts/routes.py routes)")
    (let [rt (cp/read-routes routes-path)
          r #(get-in rt [%1 %2 %3])]
      (testing "land neighbours are not seaborne"
        (is (false? (:maritime? (r "USA" "CAN" "general"))))
        (is (false? (:maritime? (r "DEU" "FRA" "general"))))
        (is (= "border" (:reason (r "USA" "MEX" "general")))))
      (testing "islands and sea-only borders are"
        (is (true? (:maritime? (r "GBR" "IRL" "general"))))
        (is (true? (:maritime? (r "CHN" "IND" "general"))))
        (is (true? (:maritime? (r "JPN" "KOR" "general")))))
      (testing "Gulf crude to East Asia passes Hormuz and Malacca"
        (let [s (r "KWT" "KOR" "tanker")]
          (is (close? 1.0 (get-in s [:through "hormuz"]) 1e-3))
          (is (< 0.9 (get-in s [:through "malacca"] 0.0))))
        (is (< 0.5 (get-in (r "SAU" "KOR" "tanker") [:through "hormuz"] 0.0) 0.99)
            "Saudi Arabia also loads at Yanbu on the Red Sea"))
      (testing "Asia-Europe containers go by Suez and Bab-el-Mandeb"
        (let [s (r "CHN" "NLD" "general")]
          (is (< 0.9 (get-in s [:through "suez"] 0.0)))
          (is (< 0.9 (get-in s [:through "bab-el-mandeb"] 0.0)))))
      (testing "a strait's share never exceeds one"
        (is (every? #(<= 0.0 % 1.0001) (for [[_ m] rt [_ c] m [_ x] c [_ s] (:through x)] s)))))))
