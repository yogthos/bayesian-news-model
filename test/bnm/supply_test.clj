(ns bnm.supply-test
  (:require [bnm.supply :as sup]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

;; A toy economy: AA pumps crude, BB refines it (80% of its crude from
;; AA) into gasoline and naphtha, makes plastics from naphtha and moves
;; goods on gasoline; CC makes N fertiliser from gas and grows wheat.
(def toy-rows
  {:products [{:code "CRUD" :name "crude" :unit "tonnes" :group "raw"}
              {:code "GAS" :name "gas" :unit "tonnes" :group "raw"}
              {:code "GSL" :name "gasoline" :unit "tonnes" :group "energy"}
              {:code "NAP" :name "naphtha" :unit "tonnes" :group "energy"}
              {:code "PLA" :name "plastics" :unit "tonnes" :group "material"}
              {:code "NFE" :name "N fertiliser" :unit "tonnes" :group "material"}
              {:code "WHE" :name "wheat" :unit "tonnes" :group "raw"}
              {:code "TRN" :name "transport" :unit "Meuro" :group "service"}
              {:code "SRV" :name "services" :unit "Meuro" :group "service"}]
   :recipes [{:region "BB" :output "GSL" :input "CRUD" :phys 1.05 :share 0.6}
             {:region "BB" :output "GSL" :input "SRV" :phys 1e-4 :share 0.1}
             {:region "BB" :output "NAP" :input "CRUD" :phys 1.05 :share 0.6}
             ;; the monetary table routes naphtha elsewhere: small in
             ;; value, 0.9 t per t in mass, a feedstock all the same
             {:region "BB" :output "PLA" :input "NAP" :phys 0.9 :share 0.02}
             {:region "BB" :output "PLA" :input "SRV" :phys 1e-4 :share 0.2}
             {:region "BB" :output "TRN" :input "GSL" :phys 1e5 :share 0.15}
             {:region "BB" :output "TRN" :input "SRV" :phys 0.3 :share 0.3}
             {:region "AA" :output "CRUD" :input "SRV" :phys 1e-5 :share 0.1}
             {:region "CC" :output "GAS" :input "SRV" :phys 1e-5 :share 0.1}
             {:region "CC" :output "NFE" :input "GAS" :phys 0.7 :share 1e-5}
             {:region "CC" :output "NFE" :input "SRV" :phys 1e-4 :share 0.2}
             {:region "CC" :output "WHE" :input "NFE" :phys 0.05 :share 0.08}
             {:region "CC" :output "WHE" :input "SRV" :phys 1e-4 :share 0.3}]
   :trade [{:product "CRUD" :origin "AA" :dest "BB" :share 0.8}
           {:product "CRUD" :origin "BB" :dest "BB" :share 0.2}]
   :supply [{:region "BB" :industry "REFN" :product "GSL" :industry-share 0.7}
            {:region "BB" :industry "REFN" :product "NAP" :industry-share 0.3}
            {:region "BB" :industry "PLAS" :product "PLA" :industry-share 1.0}
            {:region "BB" :industry "TRAN" :product "TRN" :industry-share 1.0}
            {:region "AA" :industry "COIL" :product "CRUD" :industry-share 1.0}
            {:region "CC" :industry "NFER" :product "NFE" :industry-share 1.0}
            {:region "CC" :industry "WHEA" :product "WHE" :industry-share 1.0}]
   :use [{:region "BB" :product "GSL" :user "TRAN" :kind "industry" :quantity 60.0 :share 0.6 :value-share 0.15}
         {:region "BB" :product "GSL" :user "F_HOUS" :kind "final" :quantity 40.0 :share 0.4}]})

(def toy (sup/graph toy-rows))

(deftest recipes-are-classified
  (testing "a good with a large share of the output's value: critical"
    (is (= :critical (sup/classify toy "BB" "GSL" "CRUD"))))
  (testing "a mass feedstock, tonnes per tonne, whatever its value share"
    (is (= :critical (sup/classify toy "BB" "PLA" "NAP")))
    (is (= :critical (sup/classify toy "CC" "NFE" "GAS"))))
  (testing "fuel for transport, fertiliser for wheat: goods above the share"
    (is (= :critical (sup/classify toy "BB" "TRN" "GSL")))
    (is (= :critical (sup/classify toy "CC" "WHE" "NFE"))))
  (testing "services substitute (CES)"
    (is (= :substitutable (sup/classify toy "BB" "GSL" "SRV")))
    (is (= :substitutable (sup/classify toy "BB" "TRN" "SRV"))))
  (is (= {:critical 6 :substitutable 7} (frequencies (map :kind (sup/recipe-rows toy))))))

(deftest downstream-follows-recipes-and-trade
  (let [d (sup/downstream toy "AA" "CRUD")
        p (into {} (map (juxt (juxt :region :product) identity) (:products d)))
        i (into {} (map (juxt (juxt :region :industry) identity) (:industries d)))]
    (is (= 1 (:depth (p ["BB" "GSL"]))) "refined at once")
    (is (close? (* 0.8 0.6) (:dependence (p ["BB" "GSL"])) 1e-9) "AA's part of BB's crude times crude's share")
    (is (= 2 (:depth (p ["BB" "PLA"]))) "plastics two steps down")
    (is (close? 0.8 (:exposure (p ["BB" "PLA"])) 1e-9)
        "exposure: lost under a full cut by critical recipes alone, AA's 80% of BB's crude")
    (is (close? 0.8 (:exposure (p ["BB" "TRN"])) 1e-9))
    (is (close? (* 0.8 0.6 0.02) (:dependence (p ["BB" "PLA"])) 1e-9) "a small value share, a hard dependence")
    (is (= 2 (:depth (p ["BB" "TRN"]))))
    (is (nil? (p ["CC" "WHE"])) "nothing reaches CC")
    (is (nil? (p ["AA" "CRUD"])) "not the product itself")
    (is (= #{["BB" "REFN"] ["BB" "PLAS"] ["BB" "TRAN"]} (set (keys i))) "the industries making what it reaches")
    (is (close? (+ (* 0.7 0.48) (* 0.3 0.48)) (:dependence (i ["BB" "REFN"])) 1e-9)
        "an industry as the mix of its products")
    (is (close? 0.8 (:exposure (i ["BB" "REFN"])) 1e-9)))
  (testing "a node reached only through substitutable inputs has no exposure"
    (let [d (sup/downstream toy "BB" "SRV")
          p (into {} (map (juxt (juxt :region :product) identity) (:products d)))]
      (is (= 0.0 (:exposure (p ["BB" "TRN"]))))
      (is (close? (+ 0.3 (* 0.1 0.15)) (:dependence (p ["BB" "TRN"])) 1e-9) "directly and through gasoline"))))

(deftest users-are-the-direct-uses
  (is (= [{:region "BB" :user "TRAN" :kind "industry" :quantity 60.0 :share 0.6 :value-share 0.15}
          {:region "BB" :user "F_HOUS" :kind "final" :quantity 40.0 :share 0.4 :value-share nil}]
         (sup/users toy "BB" "GSL"))))

(deftest upstream-is-the-leontief-inverse-in-physical-terms
  (let [u (sup/upstream toy "BB" "PLA")
        p (into {} (map (juxt (juxt :region :product) identity) u))]
    (is (close? 0.9 (:quantity (p ["BB" "NAP"])) 1e-9) "t naphtha per t plastics")
    (is (close? (* 0.9 1.05 0.8) (:quantity (p ["AA" "CRUD"])) 1e-9) "AA's crude in it, through trade")
    (is (close? (* 0.9 1.05 0.2) (:quantity (p ["BB" "CRUD"])) 1e-9))
    (is (= "tonnes" (:unit (p ["AA" "CRUD"]))))
    (is (= 2 (:depth (p ["AA" "CRUD"]))))
    (is (close? (* 0.02 0.6 0.8) (:dependence (p ["AA" "CRUD"])) 1e-9) "the value side, alongside")
    (is (pos? (:quantity (p ["BB" "SRV"]))) "services, in M EUR"))
  (let [p (into {} (map (juxt (juxt :region :product) identity) (sup/upstream toy "CC" "NFE")))]
    (is (close? 0.7 (:quantity (p ["CC" "GAS"])) 1e-9) "gas in N fertiliser"))
  (testing "a cycle sums every round: x = a + a x"
    (let [g (sup/graph {:products [{:code "C" :unit "tonnes" :group "raw"} {:code "E" :unit "TJ" :group "energy"}]
                        :recipes [{:region "R" :output "E" :input "C" :phys 0.5 :share 0.5}
                                  {:region "R" :output "C" :input "E" :phys 0.2 :share 0.1}]})
          p (into {} (map (juxt :product identity) (sup/upstream g "R" "E")))]
      ;; one TJ needs 0.5 t coal; that coal needs 0.1 TJ, which needs 0.05 t...
      (is (close? (/ 0.5 (- 1 (* 0.5 0.2))) (:quantity (p "C")) 1e-9))
      (is (close? (/ 0.1 (- 1 0.1)) (:quantity (p "E")) 1e-9) "its own power, beyond the unit itself"))))

(deftest outage-propagates-across-regions
  (let [o (sup/outage toy {["AA" "CRUD"] 0.5})]
    (is (close? 0.5 (get-in o [:products ["AA" "CRUD"]]) 1e-9) "the cut itself")
    (is (close? 0.6 (get-in o [:products ["BB" "GSL"]]) 1e-6) "BB's crude: 0.8 x 0.5 + 0.2, refining capped by it")
    (is (close? 0.6 (get-in o [:products ["BB" "PLA"]]) 1e-6) "naphtha a feedstock: plastics follow")
    (is (close? 0.6 (get-in o [:products ["BB" "TRN"]]) 1e-6) "no fuel, no transport")
    (is (close? 0.6 (get-in o [:industries ["BB" "REFN"]]) 1e-6))
    (is (nil? (get-in o [:products ["CC" "WHE"]])) "CC untouched, not listed"))
  (testing "a cut where a product is used, not where it is made"
    (let [o (sup/outage toy {["CC" "GAS"] 0.4} {:at :use})]
      (is (close? 0.6 (get-in o [:products ["CC" "NFE"]]) 1e-6))
      (is (close? 0.6 (get-in o [:products ["CC" "WHE"]]) 1e-6) "fertiliser critical for wheat")))
  (testing "a substitutable input passes on a CES loss, less than the cut:
            it substitutes with the others and with labour and capital
            (value added, one less the input shares)"
    (let [o (sup/outage toy {["BB" "SRV"] 0.5})
          w (/ 0.3 0.85)
          ces (Math/pow (+ (* w (Math/pow 0.5 -4.0)) (- 1 w)) -0.25)]
      (is (close? ces (get-in o [:products ["BB" "TRN"]]) 1e-6) "sigma 0.2 by default")
      (is (< 0.5 ces 1.0))
      (is (< (get-in o [:products ["BB" "TRN"]])
             (get-in (sup/outage toy {["BB" "SRV"] 0.5} {:sigma 1.0}) [:products ["BB" "TRN"]]))
          "more substitution, less loss"))))

;; ---- the real graph, when scripts/product-graph.py has written it

(def dir "data/derived/supply")

(def real (delay (when (.exists (io/file dir "recipes.csv")) (sup/load-graph dir))))

(defn- products-of [rows] (set (map :product rows)))
(defn- industries-of [rows] (set (map :industry rows)))

(deftest validation-on-exiobase
  (if-not @real
    (println "bnm.supply-test: no" dir "- validation skipped (python -I scripts/product-graph.py)")
    (let [g @real]
      (testing "crude's downstream: the refined products, plastics, transport and chemicals"
        (let [d (sup/downstream g "US" "C_COIL")
              ps (products-of (:products d))
              is' (industries-of (:industries d))]
          (doseq [p ["C_MGSL" "C_KJET" "C_DOIL" "C_NAPT" "C_PLAS"]]
            (is (ps p) p))
          (is (some #{"A_TLND" "A_TAIR" "A_TWAS"} is') "transport")
          (is (some #{"A_CHEM" "A_PLAS"} is') "chemicals")))
      (testing "basic plastics need naphtha or crude, and natural gas"
        (let [ps (products-of (sup/upstream g "DE" "C_PLAS"))]
          (is (some #{"C_NAPT" "C_COIL"} ps))
          (is (ps "C_GASE"))))
      (testing "N fertiliser needs natural gas"
        (is ((products-of (sup/upstream g "DE" "C_NFER")) "C_GASE"))
        (is ((products-of (sup/upstream g "US" "C_NFER")) "C_GASE"))))))
