(ns bnm.supply-test
  "bnm.supply on synthetic graphs. The checks on the EXIOBASE graph itself
  (validation, the gas and crude shocks) are in tools/bnm/supply_check.clj:
  jolt -A:tools -M -m bnm.supply-check."
  (:require [bnm.supply :as sup]
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
              {:code "C_NFER" :name "N fertiliser" :unit "tonnes" :group "material"}
              {:code "C_WHEA" :name "wheat" :unit "tonnes" :group "raw"}
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
             {:region "CC" :output "C_NFER" :input "GAS" :phys 0.7 :share 1e-5}
             {:region "CC" :output "C_NFER" :input "SRV" :phys 1e-4 :share 0.2}
             {:region "CC" :output "C_WHEA" :input "C_NFER" :phys 0.05 :share 0.08}
             {:region "CC" :output "C_WHEA" :input "SRV" :phys 1e-4 :share 0.3}]
   :trade [{:product "CRUD" :origin "AA" :dest "BB" :share 0.8}
           {:product "CRUD" :origin "BB" :dest "BB" :share 0.2}]
   :supply [{:region "BB" :industry "REFN" :product "GSL" :industry-share 0.7}
            {:region "BB" :industry "REFN" :product "NAP" :industry-share 0.3}
            {:region "BB" :industry "PLAS" :product "PLA" :industry-share 1.0}
            {:region "BB" :industry "TRAN" :product "TRN" :industry-share 1.0}
            {:region "AA" :industry "COIL" :product "CRUD" :industry-share 1.0}
            {:region "CC" :industry "NFER" :product "C_NFER" :industry-share 1.0}
            {:region "CC" :industry "WHEA" :product "C_WHEA" :industry-share 1.0}]
   :use [{:region "BB" :product "GSL" :user "TRAN" :kind "industry" :quantity 60.0 :share 0.6 :value-share 0.15}
         {:region "BB" :product "GSL" :user "F_HOUS" :kind "final" :quantity 40.0 :share 0.4}]})

(def toy (sup/graph toy-rows))

(deftest recipes-are-classified
  (testing "a good with a large share of the output's value: critical"
    (is (= :critical (sup/classify toy "BB" "GSL" "CRUD"))))
  (testing "a mass feedstock, tonnes per tonne, whatever its value share"
    (is (= :critical (sup/classify toy "BB" "PLA" "NAP")))
    (is (= :critical (sup/classify toy "CC" "C_NFER" "GAS"))))
  (testing "fuel for transport: a good above the share"
    (is (= :critical (sup/classify toy "BB" "TRN" "GSL"))))
  (testing "fertiliser for a crop is a yield response, not a recipe: CES
            whatever its share (a crop loses far less than the fertiliser)"
    (is (= :substitutable (sup/classify toy "CC" "C_WHEA" "C_NFER"))))
  (testing "fuel burnt into electricity or heat: a feedstock by energy, the
            fuel's heat per unit of output (IPCC default net calorific
            values) at 0.1 or more, whatever the monetary table makes of it"
    (let [g (sup/graph {:products [{:code "C_GASE" :unit "tonnes" :group "raw"}
                                   {:code "C_LIBC" :unit "tonnes" :group "raw"}
                                   {:code "C_NUCF" :unit "TJ" :group "energy"}
                                   {:code "C_POWG" :unit "TJ" :group "energy"}
                                   {:code "C_POWN" :unit "TJ" :group "energy"}]
                        :recipes [;; 27.8 t of gas per TJ: 1.3 TJ of gas
                                  {:region "R" :output "C_POWG" :input "C_GASE" :phys 27.8 :share 4.5e-6}
                                  ;; 4.5 t of lignite: 0.05 TJ, a trace
                                  {:region "R" :output "C_POWG" :input "C_LIBC" :phys 4.5 :share 2e-4}
                                  {:region "R" :output "C_POWN" :input "C_NUCF" :phys 3.0 :share 0.01}]})]
      (is (= :critical (sup/classify g "R" "C_POWG" "C_GASE")))
      (is (= :substitutable (sup/classify g "R" "C_POWG" "C_LIBC")))
      (is (= :critical (sup/classify g "R" "C_POWN" "C_NUCF")) "TJ of fuel per TJ")))
  (testing "tonnes per tonne make a feedstock only where raw materials or
            fuels are converted into energy or materials: elsewhere the
            hybrid tonnage of a fuel is not an ingredient"
    (let [g (sup/graph {:products [{:code "GAS" :unit "tonnes" :group "raw"}
                                   {:code "BEV" :unit "tonnes" :group "manufactured"}]
                        :recipes [{:region "R" :output "BEV" :input "GAS" :phys 0.5 :share 0.002}]})]
      (is (= :substitutable (sup/classify g "R" "BEV" "GAS")))))
  (testing "electricity by source is one carrier: critical together when
            their shares add to the threshold, and pooled"
    (let [g (sup/graph {:products [{:code "C_POWC" :unit "TJ" :group "energy"}
                                   {:code "C_POWG" :unit "TJ" :group "energy"}
                                   {:code "ALU" :unit "tonnes" :group "material"}
                                   {:code "SRV" :unit "Meuro" :group "service"}]
                        :recipes [{:region "R" :output "ALU" :input "C_POWC" :phys 0.03 :share 0.03}
                                  {:region "R" :output "ALU" :input "C_POWG" :phys 0.02 :share 0.03}
                                  {:region "R" :output "ALU" :input "SRV" :phys 0.1 :share 0.2}]})
          o (sup/outage g {["R" "C_POWG"] 1.0})]
      (is (= :critical (sup/classify g "R" "ALU" "C_POWC")))
      (is (= :critical (sup/classify g "R" "ALU" "C_POWG")))
      (is (close? 0.5 (get-in o [:products ["R" "ALU"]]) 1e-6)
          "gas power gone: half the electricity, half the aluminium, not none")))
  (testing "services substitute (CES)"
    (is (= :substitutable (sup/classify toy "BB" "GSL" "SRV")))
    (is (= :substitutable (sup/classify toy "BB" "TRN" "SRV"))))
  (is (= {:critical 5 :substitutable 8} (frequencies (map :kind (sup/recipe-rows toy))))))

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
    (is (nil? (p ["CC" "C_WHEA"])) "nothing reaches CC")
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
  (let [p (into {} (map (juxt (juxt :region :product) identity) (sup/upstream toy "CC" "C_NFER")))]
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
    (is (nil? (get-in o [:products ["CC" "C_WHEA"]])) "CC untouched, not listed")
    (is (<= (:residual (:settled o)) 1e-6) "settled to the tolerance, and says so")
    (is (pos? (:updates (:settled o)))))
  (testing "a cut where a product is used, not where it is made"
    (let [o (sup/outage toy {["CC" "GAS"] 0.4} {:at :use})]
      (is (close? 0.6 (get-in o [:products ["CC" "C_NFER"]]) 1e-6))
      (let [w (/ 0.08 (+ 0.08 0.3 (- 1 0.08 0.3)))
            ces (Math/pow (+ (* w (Math/pow 0.6 -4.0)) (- 1 w)) -0.25)]
        (is (close? ces (get-in o [:products ["CC" "C_WHEA"]]) 1e-6)
            "wheat: a yield response to 40% less fertiliser, about 10% less")
        (is (< 0.85 ces 0.95)))))
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

(deftest value-shares-above-one-are-left-out-and-flagged
  ;; the monetary tables put a few coefficients above 1 and some outputs'
  ;; inputs above their value (tiny outputs, rough releases): such a share
  ;; is no share, and an output's shares are scaled to sum below 1
  (let [g (sup/graph {:products [{:code "A" :unit "tonnes" :group "material"}
                                 {:code "B" :unit "tonnes" :group "material"}
                                 {:code "C" :unit "tonnes" :group "material"}
                                 {:code "Y" :unit "tonnes" :group "manufactured"}
                                 {:code "Z" :unit "tonnes" :group "manufactured"}]
                      :recipes [{:region "R" :output "Y" :input "A" :share 1.5}
                                {:region "R" :output "Y" :input "B" :share 0.2}
                                {:region "R" :output "Z" :input "B" :share 0.9}
                                {:region "R" :output "Z" :input "C" :share 0.3}]})
        row (fn [o i] (first (filter #(and (= o (:output %)) (= i (:input %))) (sup/recipe-rows g))))]
    (is (nil? (:share (row "Y" "A"))) "a share above 1 is not used")
    (is (= 1.5 (:share-raw (row "Y" "A"))) "kept as read")
    (is (= :over-1 (:share-flag (row "Y" "A"))))
    (is (= :substitutable (sup/classify g "R" "Y" "A")) "nor does it make the input critical")
    (is (close? 0.2 (:share (row "Y" "B")) 1e-12) "the rest of the recipe as it was")
    (is (close? 0.99 (+ (:share (row "Z" "B")) (:share (row "Z" "C"))) 1e-12) "inputs worth 1.2 of the output: scaled to 0.99")
    (is (close? 3.0 (/ (:share (row "Z" "B")) (:share (row "Z" "C"))) 1e-9) "in proportion")
    (is (= :scaled (:share-flag (row "Z" "B"))))
    (is (= {:over-1 1 :scaled 2} (sup/share-flags g)))
    (let [d (into {} (map (juxt :product :dependence) (:products (sup/downstream g "R" "A"))))]
      (is (nil? (d "Y")) "no dependence through it"))))

(deftest rough-value-shares-of-use-are-flagged
  (let [g (sup/graph {:products [{:code "P" :unit "tonnes" :group "material"}]
                      :use [{:region "R" :product "P" :user "I1" :kind "industry" :quantity 5.0 :share 0.5 :value-share 1.7}
                            {:region "R" :product "P" :user "I2" :kind "industry" :quantity 5.0 :share 0.5 :value-share 0.1}]})
        u (into {} (map (juxt :user identity) (sup/users g "R" "P")))]
    (is (nil? (:value-share (u "I1"))) "above 1: no value share")
    (is (= 1.7 (:value-rough (u "I1"))) "the rough ratio, flagged")
    (is (= 0.1 (:value-share (u "I2"))))
    (is (nil? (:value-rough (u "I2"))))))
