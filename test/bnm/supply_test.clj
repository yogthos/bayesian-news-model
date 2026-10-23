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
    (is (= :substitutable (sup/classify toy "CC" "C_WHEA" "C_NFER")))
    (let [g (sup/graph {:products [{:code "C_CHEM" :unit "tonnes" :group "material"}
                                   {:code "C_PFER" :unit "tonnes" :group "material"}
                                   {:code "C_OCER" :unit "tonnes" :group "raw"}
                                   {:code "C_PLAS" :unit "tonnes" :group "material"}]
                        :recipes [{:region "R" :output "C_OCER" :input "C_CHEM" :phys 0.002 :share 0.09}
                                  {:region "R" :output "C_OCER" :input "C_PFER" :phys 0.02 :share 0.06}
                                  {:region "R" :output "C_PLAS" :input "C_CHEM" :phys 0.2 :share 0.09}]})]
      (is (= :substitutable (sup/classify g "R" "C_OCER" "C_PFER")) "P and compound fertiliser too")
      (is (= :substitutable (sup/classify g "R" "C_OCER" "C_CHEM")) "and crop protection (chemicals nec)")
      (is (= :critical (sup/classify g "R" "C_PLAS" "C_CHEM")) "chemicals elsewhere by the rule")))
  (testing "natural gas in a refinery: a feedstock of what gas processing
            makes (ethane, LPG, refinery gas), not of diesel or gasoline,
            which get it only through the industry-technology split"
    (let [g (sup/graph {:products [{:code "C_GASE" :unit "tonnes" :group "raw"}
                                   {:code "C_DOIL" :unit "tonnes" :group "energy"}
                                   {:code "C_LPGA" :unit "tonnes" :group "energy"}]
                        :recipes [{:region "R" :output "C_DOIL" :input "C_GASE" :phys 0.107 :share 0.001}
                                  {:region "R" :output "C_LPGA" :input "C_GASE" :phys 0.6 :share 0.001}]})]
      (is (= :substitutable (sup/classify g "R" "C_DOIL" "C_GASE")))
      (is (= :critical (sup/classify g "R" "C_LPGA" "C_GASE")))))
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

(deftest heat-fuels-are-an-energy-bundle
  ;; fuel burnt for process heat is not an ingredient: a kiln or a boiler
  ;; can burn less gas and more oil, and the heat fuels together are one
  ;; input (CES, sup/energy-sigma, weights by heat content); material
  ;; feedstocks stay critical one by one
  (let [ps [{:code "C_GASE" :unit "tonnes" :group "raw"}
            {:code "C_FOIL" :unit "tonnes" :group "energy"}
            {:code "C_COKE" :unit "tonnes" :group "energy"}
            {:code "C_COKC" :unit "tonnes" :group "raw"}
            {:code "C_MBFG" :unit "tonnes" :group "energy"}
            {:code "C_IRON" :unit "tonnes" :group "raw"}
            {:code "C_POWC" :unit "TJ" :group "energy"}
            {:code "C_PAPE" :unit "tonnes" :group "material"}
            {:code "C_STEL" :unit "tonnes" :group "material"}
            {:code "C_NFER" :unit "tonnes" :group "material"}
            {:code "SRV" :unit "Meuro" :group "service"}]
        ;; paper: 0.1 t of gas (4.8 GJ) and 0.119 t of fuel oil (4.8 GJ)
        ;; per t, equal heat; steel: coke and ore its feedstocks, gas at 7%
        ;; of its value burnt in reheating furnaces; fertiliser: gas the
        ;; feedstock; coke: coking coal its feedstock, blast furnace gas
        ;; heating the ovens
        rs [{:region "R" :output "C_PAPE" :input "C_GASE" :phys 0.1 :share 1e-5}
            {:region "R" :output "C_PAPE" :input "C_FOIL" :phys (/ 0.1 (/ 0.0404 0.048)) :share 0.001}
            {:region "R" :output "C_PAPE" :input "SRV" :phys 1e-4 :share 0.2}
            {:region "R" :output "C_STEL" :input "C_COKE" :phys 0.4 :share 0.1}
            {:region "R" :output "C_STEL" :input "C_IRON" :phys 1.5 :share 0.1}
            {:region "R" :output "C_STEL" :input "C_GASE" :phys 0.01 :share 0.07}
            {:region "R" :output "C_STEL" :input "C_POWC" :phys 0.0005 :share 0.02}
            {:region "R" :output "C_NFER" :input "C_GASE" :phys 0.6 :share 1e-5}
            {:region "R" :output "C_COKE" :input "C_COKC" :phys 1.1 :share 0.8}
            {:region "R" :output "C_COKE" :input "C_MBFG" :phys 0.6 :share 0.002}]
        g (sup/graph {:products ps :recipes rs})
        row (fn [o i] (first (filter #(and (= o (:output %)) (= i (:input %))) (sup/recipe-rows g))))
        cut (sup/outage g {["R" "C_GASE"] 0.4} {:at :use})
        loss #(- 1.0 (get-in cut [:products ["R" %]] 1.0))
        ces (fn [xs ws s] (let [rho (/ (- s 1.0) s)] (Math/pow (reduce + (map #(* %2 (Math/pow %1 rho)) xs ws)) (/ 1.0 rho))))]
    (testing "heat fuels: critical together, as one energy bundle"
      (is (= :energy (:pool (row "C_PAPE" "C_GASE"))))
      (is (= :energy (:pool (row "C_PAPE" "C_FOIL"))))
      (is (= :critical (sup/classify g "R" "C_PAPE" "C_GASE")))
      (is (close? (- 1.0 (ces [0.6 1.0] [0.5 0.5] sup/energy-sigma)) (loss "C_PAPE") 1e-6)
          "40% less gas, half the heat: a CES loss of the bundle, not 40%")
      (is (< 0.15 (loss "C_PAPE") 0.3)))
    (testing "steel: coke and ore stay critical one by one; the gas of its
              furnaces joins the electricity in the energy bundle"
      (is (nil? (:pool (row "C_STEL" "C_COKE"))))
      (is (= :critical (sup/classify g "R" "C_STEL" "C_COKE")))
      (is (= :energy (:pool (row "C_STEL" "C_GASE"))))
      (is (= :energy (:pool (row "C_STEL" "C_POWC"))))
      (let [wg (* 0.01 0.048) we 0.0005
            e (ces [0.6 1.0] [(/ wg (+ wg we)) (/ we (+ wg we))] sup/energy-sigma)]
        (is (close? (- 1.0 e) (loss "C_STEL") 1e-6) "the gas's part of the heat")
        (is (< (loss "C_STEL") 0.4))))
    (testing "a material feedstock is critical: gas into N fertiliser"
      (is (nil? (:pool (row "C_NFER" "C_GASE"))))
      (is (close? 0.4 (loss "C_NFER") 1e-6)))
    (testing "coke: coking coal its feedstock, blast furnace gas its heat"
      (is (nil? (:pool (row "C_COKE" "C_COKC"))))
      (is (= :energy (:pool (row "C_COKE" "C_MBFG")))))
    (testing "a larger elasticity, a smaller loss"
      (is (< (- 1.0 (get-in (sup/outage g {["R" "C_GASE"] 0.4} {:at :use :energy-sigma 2.0}) [:products ["R" "C_PAPE"]]))
             (loss "C_PAPE"))))
    (testing "a material made with heat: its bundle critical once its heat
              is heat-critical per tonne, whatever any one fuel's tonnage
              (a cement kiln's 3 GJ/t from gas, lignite and oil)"
      (let [g (sup/graph {:products ps
                          :recipes [{:region "R" :output "C_PAPE" :input "C_GASE" :phys 0.03 :share 1e-5}
                                    {:region "R" :output "C_PAPE" :input "C_FOIL" :phys 0.03 :share 1e-4}]})]
        (is (= :critical (sup/classify g "R" "C_PAPE" "C_GASE")))
        (is (= :energy (:pool (first (sup/recipe-rows g)))))))
    (testing "heat fuels below the rule's thresholds stay substitutable, one by one"
      (let [g (sup/graph {:products ps
                          :recipes [{:region "R" :output "C_PAPE" :input "C_GASE" :phys 0.01 :share 0.001}
                                    {:region "R" :output "C_PAPE" :input "SRV" :phys 1e-4 :share 0.2}]})]
        (is (= :substitutable (sup/classify g "R" "C_PAPE" "C_GASE"))))
      (let [g (sup/graph {:products [{:code "C_GASE" :unit "tonnes" :group "raw"}
                                     {:code "C_MACH" :unit "tonnes" :group "manufactured"}]
                          :recipes [{:region "R" :output "C_MACH" :input "C_GASE" :phys 0.04 :share 1e-5}]})]
        (is (= :substitutable (sup/classify g "R" "C_MACH" "C_GASE"))
            "a manufactured good's heat per tonne is no measure of need: by share alone")))))

(deftest equipment-is-not-a-crops-ingredient
  ;; a farm's or a mine's purchases of machinery and vehicles are its
  ;; capital's replacement and repair, deferred in a short-run shortage:
  ;; substitutable whatever their value share (Germany's vegetables lost
  ;; 14% under the gas cut through machinery at 7% of their value)
  (let [g (sup/graph {:products [{:code "C_MACH" :unit "tonnes" :group "manufactured"}
                                 {:code "C_MOTO" :unit "tonnes" :group "manufactured"}
                                 {:code "C_FVEG" :unit "tonnes" :group "raw"}
                                 {:code "C_IRON" :unit "tonnes" :group "raw"}]
                      :recipes [{:region "R" :output "C_FVEG" :input "C_MACH" :phys 0.02 :share 0.07}
                                {:region "R" :output "C_IRON" :input "C_MOTO" :phys 0.001 :share 0.06}
                                {:region "R" :output "C_MACH" :input "C_MOTO" :phys 0.001 :share 0.06}]})]
    (is (= :substitutable (sup/classify g "R" "C_FVEG" "C_MACH")))
    (is (= :substitutable (sup/classify g "R" "C_IRON" "C_MOTO")))
    (is (= :critical (sup/classify g "R" "C_MACH" "C_MOTO")) "a component of a manufactured good: by the rule")))

(deftest the-rule-where-the-review-found-artefacts
  (let [g (sup/graph {:products [{:code "C_GASE" :unit "tonnes" :group "raw"}
                                 {:code "C_NAPT" :unit "tonnes" :group "energy"}
                                 {:code "C_PLAS" :unit "tonnes" :group "material"}
                                 {:code "C_CHMF" :unit "tonnes" :group "raw"}
                                 {:code "C_CHEM" :unit "tonnes" :group "material"}
                                 {:code "C_HWAT" :unit "TJ" :group "energy"}
                                 {:code "C_POWW" :unit "TJ" :group "energy"}
                                 {:code "C_POWG" :unit "TJ" :group "energy"}]
                      :recipes [{:region "R" :output "C_PLAS" :input "C_NAPT" :phys 0.9 :share 0.02}
                                {:region "R" :output "C_PLAS" :input "C_GASE" :phys 0.14 :share 1e-5}
                                {:region "R" :output "C_CHMF" :input "C_GASE" :phys 0.035 :share 0.15}
                                {:region "R" :output "C_POWW" :input "C_CHEM" :phys 0.04 :share 0.18}
                                {:region "R" :output "C_POWW" :input "C_HWAT" :phys 0.0 :share 0.13}
                                {:region "R" :output "C_POWG" :input "C_GASE" :phys 27.8 :share 1e-5}]})
        row (fn [o i] (first (filter #(and (= o (:output %)) (= i (:input %))) (sup/recipe-rows g))))]
    (testing "plastics: naphtha the feedstock, natural gas the crackers' heat
              (ethane from gas processing is a feedstock where the tables
              record it)"
      (is (nil? (:pool (row "C_PLAS" "C_NAPT"))))
      (is (= :critical (sup/classify g "R" "C_PLAS" "C_NAPT")))
      (is (= :energy (:pool (row "C_PLAS" "C_GASE")))))
    (testing "mining and quarrying burn their fuel for heat and haulage: an
              energy bundle"
      (is (= :energy (:pool (row "C_CHMF" "C_GASE")))))
    (testing "a power station's goods other than its fuel are upkeep, not
              ingredients: wind power does not stop without chemicals"
      (is (= :substitutable (sup/classify g "R" "C_POWW" "C_CHEM")))
      (is (= :substitutable (sup/classify g "R" "C_POWW" "C_HWAT")))
      (is (= :critical (sup/classify g "R" "C_POWG" "C_GASE")) "its fuel by the rule")))
  (testing "gas distribution in a power station's recipe is the monetary
            route of its gas, not an input of its own: nuclear power keeps
            running when gas is cut"
    (let [g (sup/graph {:products [{:code "C_GASE" :unit "tonnes" :group "raw"}
                                   {:code "C_GASD" :unit "Meuro" :group "service"}
                                   {:code "C_NUCF" :unit "TJ" :group "energy"}
                                   {:code "C_POWN" :unit "TJ" :group "energy"}]
                        :recipes [{:region "R" :output "C_GASD" :input "C_GASE" :phys 1000.0 :share 0.6}
                                  {:region "R" :output "C_POWN" :input "C_GASD" :phys 0.0025 :share 0.34}
                                  {:region "R" :output "C_POWN" :input "C_NUCF" :phys 3.0 :share 0.01}]})
          o (sup/outage g {["R" "C_GASE"] 0.4})]
      (is (close? 0.6 (get-in o [:products ["R" "C_GASD"]]) 1e-6))
      (is (nil? (get-in o [:products ["R" "C_POWN"]]))))))


(deftest physical-evidence-decides
  ;; where the hybrid table records a good's flow in physical units, a
  ;; value share alone does not make it critical when the flow is absent
  ;; (0 per unit) or, for a fuel, negligible (under sup/negligible-heat of
  ;; the recipe's heat): the monetary tables route it differently, as they
  ;; route a power station's gas through gas distribution
  (let [ps [{:code "C_GASE" :unit "tonnes" :group "raw"}
            {:code "C_COIL" :unit "tonnes" :group "raw"}
            {:code "C_NAPT" :unit "tonnes" :group "energy"}
            {:code "C_COKE" :unit "tonnes" :group "energy"}
            {:code "C_DOIL" :unit "tonnes" :group "energy"}
            {:code "C_IRON" :unit "tonnes" :group "raw"}
            {:code "C_CHEM" :unit "tonnes" :group "material"}
            {:code "C_PULP" :unit "tonnes" :group "material"}
            {:code "C_PLAS" :unit "tonnes" :group "material"}
            {:code "C_STEL" :unit "tonnes" :group "material"}
            {:code "C_NFER" :unit "tonnes" :group "material"}
            {:code "C_FISH" :unit "tonnes" :group "raw"}
            {:code "C_NUCF" :unit "TJ" :group "energy"}
            {:code "C_POWN" :unit "TJ" :group "energy"}
            {:code "C_POWC" :unit "TJ" :group "energy"}
            {:code "C_MACH" :unit "tonnes" :group "manufactured"}
            {:code "C_MOTO" :unit "Meuro" :group "manufactured"}
            {:code "C_TLND" :unit "Meuro" :group "service"}
            {:code "SRV" :unit "Meuro" :group "service"}]
        ;; Italy's N fertiliser: 0.415 t of gas per t, at 8% of its value;
        ;; crude at 10% of its value, 0 t per t. The Netherlands' nuclear
        ;; power: 3.03 TJ of nuclear fuel per TJ; natural gas at 0.87% of
        ;; its value, 0.18 kg per TJ (9e-6 TJ of heat)
        rs [{:region "IT" :output "C_NFER" :input "C_GASE" :phys 0.415 :share 0.082}
            {:region "IT" :output "C_NFER" :input "C_COIL" :phys 0.0 :share 0.104}
            {:region "IT" :output "C_NFER" :input "SRV" :phys 1e-4 :share 0.2}
            {:region "NL" :output "C_POWN" :input "C_NUCF" :phys 3.03 :share 2.2e-5}
            {:region "NL" :output "C_POWN" :input "C_GASE" :phys 0.00018 :share 0.0087}
            {:region "NL" :output "C_POWN" :input "SRV" :phys 1e-4 :share 0.05}
            ;; plastics: naphtha its feedstock at 2% of its value, the
            ;; monetary route through chemicals nec at 20%, 0 t per t
            {:region "R" :output "C_PLAS" :input "C_NAPT" :phys 0.58 :share 0.02}
            {:region "R" :output "C_PLAS" :input "C_CHEM" :phys 0.0 :share 0.2}
            {:region "R" :output "C_STEL" :input "C_COKE" :phys 0.4 :share 0.1}
            {:region "R" :output "C_STEL" :input "C_IRON" :phys 1.5 :share 0.1}
            ;; a fuel physically present, by its value share
            {:region "R" :output "C_FISH" :input "C_DOIL" :phys 0.3 :share 0.1}
            ;; a manufactured good: its electricity at 6% of its value but
            ;; 1e-9 TJ per t, against 4e-4 TJ of fuel oil's heat
            {:region "R" :output "C_MACH" :input "C_POWC" :phys 1e-9 :share 0.06}
            {:region "R" :output "C_MACH" :input "C_DOIL" :phys 0.01 :share 0.001}
            ;; no physical row: a good in money, a service output, and a
            ;; recipe the hybrid table records no tonne of
            {:region "R" :output "C_MACH" :input "C_MOTO" :phys 0.001 :share 0.06}
            {:region "R" :output "C_TLND" :input "C_DOIL" :phys 1e5 :share 0.15}
            {:region "R" :output "C_PULP" :input "C_CHEM" :phys 0.0 :share 0.1}]
        g (sup/graph {:products ps :recipes rs})]
    (testing "crude in Italy's N fertiliser: 0 t per t, not critical, and
              no input at all (a crude cut leaves the fertiliser whole)"
      (is (= :substitutable (sup/classify g "IT" "C_NFER" "C_COIL")))
      (is (nil? (get-in (sup/outage g {["IT" "C_COIL"] 0.5} {:at :use}) [:products ["IT" "C_NFER"]]))))
    (testing "natural gas in the Netherlands' nuclear power: a millionth of
              its heat, not critical, and nuclear power loses nothing when
              gas is cut"
      (is (= :substitutable (sup/classify g "NL" "C_POWN" "C_GASE")))
      (is (nil? (get-in (sup/outage g {["NL" "C_GASE"] 0.4} {:at :use}) [:products ["NL" "C_POWN"]]))))
    (testing "the real critical inputs stay critical"
      (is (= :critical (sup/classify g "IT" "C_NFER" "C_GASE")) "gas into N fertiliser")
      (is (close? 0.6 (get-in (sup/outage g {["IT" "C_GASE"] 0.4} {:at :use}) [:products ["IT" "C_NFER"]]) 1e-6))
      (is (= :critical (sup/classify g "R" "C_PLAS" "C_NAPT")) "naphtha into plastics")
      (is (= :substitutable (sup/classify g "R" "C_PLAS" "C_CHEM")) "not its monetary route")
      (is (= :critical (sup/classify g "R" "C_STEL" "C_COKE")) "coke into steel")
      (is (= :critical (sup/classify g "R" "C_STEL" "C_IRON")) "and ore")
      (is (= :critical (sup/classify g "NL" "C_POWN" "C_NUCF")) "nuclear fuel into nuclear power")
      (is (= :critical (sup/classify g "R" "C_FISH" "C_DOIL")) "a fuel present: its value share decides"))
    (testing "electricity a billionth of the heat: its 6% share does not
              make it, nor the energy bundle, critical"
      (is (= :substitutable (sup/classify g "R" "C_MACH" "C_POWC")))
      (is (= :substitutable (sup/classify g "R" "C_MACH" "C_DOIL"))))
    (testing "no physical row: the value share decides"
      (is (= :critical (sup/classify g "R" "C_MACH" "C_MOTO")) "a good recorded in money")
      (is (= :critical (sup/classify g "R" "C_TLND" "C_DOIL")) "a service's fuel")
      (is (= :critical (sup/classify g "R" "C_PULP" "C_CHEM")) "a recipe with no tonne recorded"))))

(deftest a-carrier-is-critical-on-its-fuel-even-without-a-calorific-value
  ;; a carrier's other goods are upkeep, but wood and crops are the fuel
  ;; of biomass power and uranium ore that of nuclear fuel: with no ncv to
  ;; read them by, their value share decides
  (let [g (sup/graph {:products [{:code "C_FORE" :unit "tonnes" :group "raw"}
                                 {:code "C_ORAN" :unit "tonnes" :group "raw"}
                                 {:code "C_COPO" :unit "tonnes" :group "raw"}
                                 {:code "C_CHEM" :unit "tonnes" :group "material"}
                                 {:code "C_POWB" :unit "TJ" :group "energy"}
                                 {:code "C_NUCF" :unit "TJ" :group "energy"}
                                 {:code "C_POWS" :unit "TJ" :group "energy"}]
                      :recipes [{:region "R" :output "C_POWB" :input "C_FORE" :phys 13.5 :share 0.06}
                                {:region "R" :output "C_POWB" :input "C_CHEM" :phys 0.01 :share 0.08}
                                {:region "R" :output "C_NUCF" :input "C_ORAN" :phys 0.002 :share 0.1}
                                {:region "R" :output "C_POWS" :input "C_COPO" :phys 0.001 :share 0.06}]})]
    (is (= :critical (sup/classify g "R" "C_POWB" "C_FORE")) "wood into biomass power")
    (is (= :critical (sup/classify g "R" "C_NUCF" "C_ORAN")) "uranium ore into nuclear fuel")
    (is (= :substitutable (sup/classify g "R" "C_POWB" "C_CHEM")) "upkeep")
    (is (= :substitutable (sup/classify g "R" "C_POWS" "C_COPO")) "an ore is no fuel of solar power")
    (is (close? 0.6 (get-in (sup/outage g {["R" "C_ORAN"] 0.4}) [:products ["R" "C_NUCF"]]) 1e-6))))

(deftest direct-reduction-gas-is-a-reductant
  ;; EXIOBASE has one steel product: the gas of a direct-reduction shaft,
  ;; its reductant, is not told from a reheating furnace's. Where gas-based
  ;; DRI makes a large part of the steel (sup/dri-share), that part of the
  ;; output goes with the gas one for one; elsewhere the gas is heat
  (let [ps [{:code "C_GASE" :unit "tonnes" :group "raw"}
            {:code "C_COKE" :unit "tonnes" :group "energy"}
            {:code "C_IRON" :unit "tonnes" :group "raw"}
            {:code "C_POWC" :unit "TJ" :group "energy"}
            {:code "C_STEL" :unit "tonnes" :group "material"}]
        rs (fn [r] [{:region r :output "C_STEL" :input "C_COKE" :phys 0.08 :share 0.1}
                    {:region r :output "C_STEL" :input "C_IRON" :phys 0.5 :share 0.1}
                    {:region r :output "C_STEL" :input "C_GASE" :phys 0.02 :share 0.002}
                    {:region r :output "C_STEL" :input "C_POWC" :phys 0.001 :share 0.01}])
        g (sup/graph {:products ps :recipes (concat (rs "MX") (rs "R"))})
        cut (sup/outage g {["MX" "C_GASE"] 0.4 ["R" "C_GASE"] 0.4} {:at :use})
        loss #(- 1.0 (get-in cut [:products [% "C_STEL"]] 1.0))
        d (sup/dri-share "MX")]
    (is (close? (/ 5854.0 18101.0) d 1e-3) "Mexico: DRI over crude steel, worldsteel 2011")
    (is (close? (/ 20056.0 23002.0) (sup/dri-share "WM") 1e-3) "the Middle East: the same")
    (is (nil? (sup/dri-share "R")))
    (is (close? (* d 0.4) (loss "MX") 1e-6) "the DRI part of the output loses the cut")
    (is (< (loss "R") 0.05) "elsewhere the gas is heat, a small part of it")
    (is (close? (* d 0.4) (- 1.0 (get-in (sup/outage g {["MX" "C_GASE"] 0.4} {:at :use}) [:products ["MX" "C_STEL"]])) 1e-6))
    (testing "and in the critical recipes alone (exposure): the DRI part"
      (let [e (:exposure (first (filter #(= ["MX" "C_STEL"] [(:region %) (:product %)])
                                        (:products (sup/downstream g "MX" "C_GASE")))))]
        (is (close? d e 1e-6))))))

(deftest energy-bundle-branches
  (let [ps [{:code "C_GASE" :unit "tonnes" :group "raw"}
            {:code "C_FOIL" :unit "tonnes" :group "energy"}
            {:code "C_COIL" :unit "tonnes" :group "raw"}
            {:code "C_POWC" :unit "TJ" :group "energy"}
            {:code "C_PAPE" :unit "tonnes" :group "material"}
            {:code "C_MACH" :unit "tonnes" :group "manufactured"}]
        row (fn [g o i] (first (filter #(and (= o (:output %)) (= i (:input %))) (sup/recipe-rows g))))]
    (testing "critical when the fuels' shares add to 5%, none of them alone,
              their heat under 2 GJ/t, in a material and a manufactured good"
      (doseq [o ["C_PAPE" "C_MACH"]]
        (let [g (sup/graph {:products ps
                            :recipes [{:region "R" :output o :input "C_GASE" :phys 0.01 :share 0.03}
                                      {:region "R" :output o :input "C_FOIL" :phys 0.005 :share 0.03}]})]
          (is (= :critical (sup/classify g "R" o "C_GASE")) o)
          (is (= :critical (sup/classify g "R" o "C_FOIL")) o)
          (is (= :energy (:pool (row g o "C_GASE")))))))
    (testing "the electricity pool joins the bundle as one member; a bundle
              below every threshold leaves it as it was"
      (let [g (sup/graph {:products ps
                          :recipes [{:region "R" :output "C_MACH" :input "C_GASE" :phys 0.001 :share 0.001}
                                    {:region "R" :output "C_MACH" :input "C_POWC" :phys 0.0001 :share 0.01}]})]
        (is (= :substitutable (sup/classify g "R" "C_MACH" "C_GASE")))
        (is (= :electricity (:pool (row g "C_MACH" "C_POWC"))))))
    (testing "crude is no heat fuel: its row stays out of the bundle"
      (let [g (sup/graph {:products ps
                          :recipes [{:region "R" :output "C_PAPE" :input "C_COIL" :phys 0.2 :share 0.01}
                                    {:region "R" :output "C_PAPE" :input "C_GASE" :phys 0.1 :share 0.001}]})]
        (is (nil? (:pool (row g "C_PAPE" "C_COIL"))))
        (is (= :critical (sup/classify g "R" "C_PAPE" "C_COIL")) "a feedstock by the rule")))
    (testing "no fuel is bought beyond its normal use: whatever sigma, the
              bundle loses at least the cut fuel's heat share times the cut"
      (let [g (sup/graph {:products ps
                          :recipes [{:region "R" :output "C_PAPE" :input "C_GASE" :phys 0.1 :share 0.001}
                                    {:region "R" :output "C_PAPE" :input "C_FOIL" :phys 0.1 :share 0.001}]})
            hg (* 0.1 0.048) ho (* 0.1 0.0404)
            floor (* 0.4 (/ hg (+ hg ho)))]
        (doseq [s [0.1 0.5 1.0 2.0 20.0]]
          (let [l (- 1.0 (get-in (sup/outage g {["R" "C_GASE"] 0.4} {:at :use :energy-sigma s}) [:products ["R" "C_PAPE"]]))]
            (is (<= (- floor 1e-6) l) (str "sigma " s))
            (is (< l 0.4) (str "sigma " s))))))))

;; ------------------------------------------------------------ stocks and time

(defn- month-of [path t] (nth path (dec t)))

(defn- avail [m k] (get-in m [:products k] 1.0))

(deftest an-outage-drains-a-stock-before-it-cuts-a-flow
  (testing "no stocks: every month is the steady state, outage"
    (let [cut {["AA" "CRUD"] 0.5}
          o (sup/outage toy cut)
          p (sup/outage-path toy cut {} {:months 3})]
      (is (= 3 (count p)))
      (doseq [m p [k v] (:products o)]
        (is (close? v (avail m k) 1e-6) (str "month " (:month m) " " k)))))
  (testing "a stock of crude where it is used holds the refinery whole until
            it is drawn down at the shortfall, then the cut passes on"
    ;; BB's crude: 0.8 from AA, cut by half: short 0.4 of a month's use a
    ;; month; 3 months of stock last 7.5 months
    (let [cut {["AA" "CRUD"] 0.5}
          p (sup/outage-path toy cut {["BB" "CRUD"] 3.0} {:months 10})
          gsl #(avail (month-of p %) ["BB" "GSL"])]
      (doseq [t (range 1 8)]
        (is (close? 1.0 (gsl t) 1e-6) (str "month " t ": the stock covers it")))
      (is (close? 0.8 (gsl 8) 1e-6) "month 8: the last 0.2 of a month's use, drawn")
      (is (close? 0.6 (gsl 9) 1e-6) "then the flow alone")
      (is (close? 0.5 (avail (month-of p 1) ["AA" "CRUD"]) 1e-9) "the cut itself is not buffered: AA holds none")
      (is (close? (- 3.0 0.4) (get-in (month-of p 1) [:stocks ["BB" "CRUD"]]) 1e-6) "stock left after month 1")
      (is (close? 0.4 (get-in (month-of p 1) [:drawn ["BB" "CRUD"]]) 1e-6) "drawn at the shortfall")
      (is (close? 0.0 (get-in (month-of p 9) [:stocks ["BB" "CRUD"]]) 1e-9))
      (testing "and once the stock is gone the path agrees with outage, the steady-state limit"
        (doseq [[k v] (:products (sup/outage toy cut))]
          (is (close? v (avail (month-of p 10) k) 1e-6) (str k))))))
  (testing "a cut where a product is used drains the stock there first"
    (let [cut {["CC" "GAS"] 0.4}
          p (sup/outage-path toy cut {["CC" "GAS"] 2.0} {:at :use :months 7})
          nf #(avail (month-of p %) ["CC" "C_NFER"])]
      (doseq [t (range 1 6)] (is (close? 1.0 (nf t) 1e-6) (str "month " t)))
      (is (close? 0.6 (nf 6) 1e-6) "2 months of stock at 0.4 a month: five months, then the cut")
      (is (close? (get-in (sup/outage toy cut {:at :use}) [:products ["CC" "C_WHEA"]])
                  (avail (month-of p 7) ["CC" "C_WHEA"]) 1e-6))))
  (testing "a stock larger than the horizon's shortfall: nothing lost in the horizon"
    (let [p (sup/outage-path toy {["AA" "CRUD"] 0.5} {["BB" "CRUD"] 18.0} {:months 24})]
      (is (every? #(close? 1.0 (avail % ["BB" "PLA"]) 1e-6) p))
      (is (close? (- 18.0 (* 24 0.4)) (get-in (last p) [:stocks ["BB" "CRUD"]]) 1e-6))))
  (testing "a partial stock downstream adds to one upstream: each drains only what reaches it"
    ;; naphtha stock at BB: the plastics hold on while crude's stock lasts
    ;; and then for as long as their own naphtha lasts
    (let [cut {["AA" "CRUD"] 0.5}
          p (sup/outage-path toy cut {["BB" "CRUD"] 0.6 ["BB" "NAP"] 0.4} {:months 6})
          pla #(avail (month-of p %) ["BB" "PLA"])]
      (is (close? 1.0 (pla 1) 1e-6) "crude covers month 1 and half of 2")
      (is (close? 1.0 (pla 2) 1e-6) "naphtha covers the rest of 2 (0.2 of 0.4 drawn) ...")
      (is (close? 0.8 (pla 3) 1e-6) "... and 0.2 in month 3")
      (is (close? 0.6 (pla 4) 1e-6))))
  (testing "stocks on nodes the graph does not use are ignored, not an error"
    (is (= 2 (count (sup/outage-path toy {["AA" "CRUD"] 0.5} {["ZZ" "NONE"] 5.0} {:months 2}))))))

(deftest only-the-cut-origins-share-is-drawn
  ;; nothing refills a stock, but imports from the origins the cut does not
  ;; reach keep flowing: the stock covers the cut origin's share alone
  (doseq [[x share] [[0.5 0.4] [1.0 0.8]]]
    (let [p (sup/outage-path toy {["AA" "CRUD"] x} {["BB" "CRUD"] 4.0} {:months 2})]
      (is (close? share (get-in (month-of p 1) [:drawn ["BB" "CRUD"]]) 1e-6)
          (str "AA cut by " x ": its 0.8 of BB's crude times the cut, BB's own 0.2 still flows"))
      (is (close? (- 4.0 (* 2 share)) (get-in (month-of p 2) [:stocks ["BB" "CRUD"]]) 1e-6))))
  (testing "a cut at use takes every origin, so the stock covers the cut"
    (let [p (sup/outage-path toy {["BB" "CRUD"] 0.5} {["BB" "CRUD"] 4.0} {:at :use :months 1})]
      (is (close? 0.5 (get-in (month-of p 1) [:drawn ["BB" "CRUD"]]) 1e-6)))))

(deftest a-month-solved-from-the-last-is-a-month-solved-afresh
  ;; each month starts from the last month's answer, valid only because
  ;; stocks only fall (each month's operator below the last's): the
  ;; availabilities and stocks never rise, and every month equals the
  ;; month solved from the top with the stocks it started with
  (doseq [[cut stocks opts] [[{["AA" "CRUD"] 0.5} {["BB" "CRUD"] 0.6 ["BB" "NAP"] 0.4} {}]
                             [{["AA" "CRUD"] 0.9} {["BB" "CRUD"] 1.3 ["BB" "GSL"] 0.7 ["BB" "NAP"] 2.0} {}]
                             [{["CC" "GAS"] 0.4} {["CC" "GAS"] 1.1 ["CC" "C_NFER"] 0.5} {:at :use}]]]
    (let [p (sup/outage-path toy cut stocks (assoc opts :months 8))
          ks (distinct (mapcat (comp keys :products) p))]
      (doseq [[a b] (partition 2 1 p)]
        (doseq [k ks]
          (is (<= (avail b k) (+ (avail a k) 1e-9)) (str "month " (:month b) " " k " never rises")))
        (doseq [[k m] (:stocks b)]
          (is (<= m (+ (get-in a [:stocks k]) 1e-12)) (str "month " (:month b) " stock " k " never rises")))
        (let [fresh (first (sup/outage-path toy cut (:stocks a) (assoc opts :months 1)))]
          (doseq [k (distinct (concat ks (keys (:products fresh))))]
            (is (close? (avail fresh k) (avail b k) 1e-5) (str "month " (:month b) " " k " as solved afresh")))
          (is (= (keys (:drawn fresh)) (keys (:drawn b))))
          (doseq [[k d] (:drawn b)] (is (close? d (get-in fresh [:drawn k]) 1e-5))))))))

;; A power station: nuclear fuel its fuel (critical), construction and
;; non-ferrous metals its upkeep; a machine maker uses construction too
(def plant
  (sup/graph {:products [{:code "C_NUCF" :unit "TJ" :group "energy"}
                         {:code "C_POWN" :unit "TJ" :group "energy"}
                         {:code "C_CONS" :unit "Meuro" :group "service"}
                         {:code "C_ONFM" :unit "tonnes" :group "material"}
                         {:code "C_MACH" :unit "tonnes" :group "manufactured"}]
              :recipes [{:region "R" :output "C_POWN" :input "C_NUCF" :phys 3.0 :share 0.01}
                        {:region "R" :output "C_POWN" :input "C_CONS" :phys 1e-4 :share 0.06}
                        {:region "R" :output "C_POWN" :input "C_ONFM" :phys 1e-5 :share 0.03}
                        {:region "R" :output "C_MACH" :input "C_CONS" :phys 1e-4 :share 0.06}]}))

(deftest a-power-stations-upkeep-is-deferred
  ;; machinery, metals and construction in a power station's recipe are
  ;; its upkeep, which can wait months for the next planned outage: a
  ;; stock of sup/upkeep-months at the station, drawn at the shortfall
  ;; like any other, and the steady state once it is gone
  (let [cut {["R" "C_CONS"] 0.3}
        steady (:products (sup/outage plant cut))
        p (sup/outage-path plant cut {} {:months 18 :upkeep-months 5.0})
        pown #(avail (month-of p %) ["R" "C_POWN"])]
    (is (pos? sup/upkeep-months))
    (is (every? sup/upkeep #{"C_MACH" "C_STEL" "C_ONFM" "C_CONS"}))
    (is (< (get steady ["R" "C_POWN"]) 0.999) "the steady state: the upkeep lost passes on (CES)")
    (doseq [t (range 1 17)]
      (is (close? 1.0 (pown t) 1e-6) (str "month " t ": 5 months of upkeep at 0.3 a month last 16.7")))
    (is (close? (get steady ["R" "C_POWN"]) (pown 18) 1e-6) "then the steady loss")
    (is (close? (- 5.0 0.3) (get-in (month-of p 1) [:upkeep ["R" "C_POWN" "C_CONS"]]) 1e-6)
        "the upkeep stock left, by station and input")
    (is (nil? (get-in (month-of p 1) [:stocks ["R" "C_CONS"]])) "no stock of construction where it is used")
    (is (close? (get steady ["R" "C_MACH"]) (avail (month-of p 1) ["R" "C_MACH"]) 1e-6)
        "a machine maker's construction is no upkeep of a power station: lost at once")
    (testing "the default"
      (let [p (sup/outage-path plant cut {} {:months 12})]
        (is (every? #(close? 1.0 (avail % ["R" "C_POWN"]) 1e-6) p)
            "a few months of upkeep hold a station through a year of a 30% cut")))
    (testing "no deferral: the steady state at once"
      (doseq [m (sup/outage-path plant cut {} {:months 2 :upkeep-months 0})]
        (is (close? (get steady ["R" "C_POWN"]) (avail m ["R" "C_POWN"]) 1e-6))))
    (testing "an upkeep stock given as [region output input] overrides the default, and round-trips"
      (let [q (sup/outage-path plant cut {["R" "C_POWN" "C_CONS"] 0.15} {:months 2 :upkeep-months 5.0})]
        (is (close? (get-in (sup/outage plant {["R" "C_CONS"] 0.15}) [:products ["R" "C_POWN"]])
                    (avail (month-of q 1) ["R" "C_POWN"]) 1e-6)
            "half the shortfall covered in month 1"))
      (let [m10 (month-of p 10)
            fresh (first (sup/outage-path plant cut (merge (:stocks m10) (:upkeep m10)) {:months 1 :upkeep-months 5.0}))]
        (is (close? (get-in (month-of p 11) [:upkeep ["R" "C_POWN" "C_CONS"]])
                    (get-in fresh [:upkeep ["R" "C_POWN" "C_CONS"]]) 1e-6))))
    (testing "the steady state is the recipe's: the upkeep node passes its input on"
      (let [ws [(/ 0.06 0.99) (/ 0.03 0.99) (/ 0.9 0.99)]
            ces (Math/pow (reduce + (map #(* %2 (Math/pow %1 -4.0)) [0.7 1.0 1.0] ws)) -0.25)]
        (is (close? ces (get steady ["R" "C_POWN"]) 1e-6))))))

(deftest stocks-load-from-csv
  (let [f (java.io.File/createTempFile "stocks" ".csv")]
    (spit f (str "region,product,months,year,known,basis,source\n"
                 "DE,C_GASE,1.5,2011,2026,data,GIE x fill\n"
                 "US,C_NUCF,18,2011,2026,prior,refuelling cycle\n"))
    (let [rows (sup/stock-rows (str f))]
      (is (= 2 (count rows)))
      (is (= {:region "DE" :product "C_GASE" :months 1.5 :year 2011 :known 2026 :basis "data" :source "GIE x fill"}
             (first rows)))
      (is (= {["DE" "C_GASE"] 1.5 ["US" "C_NUCF"] 18.0} (sup/stocks rows))))))

(deftest overridden-hybrid-rows-decide-as-the-physics
  ;; scripts/product-graph.py overrides hybrid rows an independent source
  ;; contradicts (bnm-1pq.28): Lithuania's N fertiliser at 0.0028 t of gas
  ;; per t where USGS's ammonia needs 0.73, a quarry's electricity at a
  ;; thousandth of a percent of its heat where UNSD gives mining 43%
  (let [ps [{:code "C_GASE" :unit "tonnes" :group "raw"}
            {:code "C_NFER" :unit "tonnes" :group "material"}
            {:code "C_SDCL" :unit "tonnes" :group "raw"}
            {:code "C_DOIL" :unit "tonnes" :group "energy"}
            {:code "C_POWC" :unit "TJ" :group "energy"}
            {:code "C_POWG" :unit "TJ" :group "energy"}
            {:code "SRV" :unit "Meuro" :group "service"}]
        rows (fn [gas e]
               [{:region "LT" :output "C_NFER" :input "C_GASE" :phys gas :share 0.004}
                {:region "LT" :output "C_NFER" :input "SRV" :phys 1e-4 :share 0.2}
                {:region "DE" :output "C_SDCL" :input "C_DOIL" :phys 1e-4 :share 0.02}
                {:region "DE" :output "C_SDCL" :input "C_POWC" :phys (* 0.75 e) :share 0.04}
                {:region "DE" :output "C_SDCL" :input "C_POWG" :phys (* 0.25 e) :share 0.015}
                {:region "DE" :output "C_SDCL" :input "SRV" :phys 1e-4 :share 0.3}])
        hybrid (sup/graph {:products ps :recipes (rows 0.00283659 3.6e-9)})
        s (/ 6912.0 16220.0)
        fixed (sup/graph {:products ps :recipes (rows 0.733 (/ (* 1e-4 0.043 s) (- 1.0 s)))})]
    (testing "as the hybrid table has them: gas not a feedstock, electricity negligible"
      (is (= :substitutable (sup/classify hybrid "LT" "C_NFER" "C_GASE")))
      (is (= :substitutable (sup/classify hybrid "DE" "C_SDCL" "C_POWC")))
      (is (= :substitutable (sup/classify hybrid "DE" "C_SDCL" "C_DOIL"))))
    (testing "overridden: gas a feedstock, electricity a third of the heat,
              its 5.5% share critical, and with it the energy bundle"
      (is (= :critical (sup/classify fixed "LT" "C_NFER" "C_GASE")))
      (is (close? 0.6 (get-in (sup/outage fixed {["LT" "C_GASE"] 0.4} {:at :use}) [:products ["LT" "C_NFER"]]) 1e-6))
      (is (= :critical (sup/classify fixed "DE" "C_SDCL" "C_POWC")))
      (is (= :critical (sup/classify fixed "DE" "C_SDCL" "C_POWG")))
      (is (= :critical (sup/classify fixed "DE" "C_SDCL" "C_DOIL"))))))

(deftest scripts-calorific-values-match
  ;; scripts/product-graph.py reads a recipe's heat with its own copy of
  ;; ncv (the mining electricity override); the two must agree
  (let [src (slurp "scripts/product-graph.py")
        blk (subs src (.indexOf src "NCV = {") (.indexOf src "}" (.indexOf src "NCV = {")))
        py (into {} (for [[_ k v] (re-seq #"\"(C_\w+)\": ([0-9.]+)" blk)] [k (parse-double v)]))]
    (is (= sup/ncv py))))

(deftest recipes-load-their-override
  (let [d (doto (java.io.File/createTempFile "graph" "") (.delete) (.mkdirs))
        w (fn [f s] (spit (java.io.File. d f) s))]
    (w "products.csv" "code,name,unit,group\nC_GASE,gas,tonnes,raw\nC_NFER,N fertiliser,tonnes,material\n")
    (w "recipes.csv" (str "region,output,input,phys,input_unit,output_unit,share_1995,share_2011,share_2022,physical,override\n"
                          "LT,C_NFER,C_GASE,0.733,tonnes,tonnes,0,0.004,0.0006,1,usgs-ammonia\n"
                          "DE,C_NFER,C_GASE,0.61,tonnes,tonnes,0,0.08,0.05,1,\n"))
    (w "trade.csv" "product,origin,dest,quantity,share\n")
    (w "supply.csv" "region,industry,product,quantity,unit,market_share,industry_share\n")
    (w "use.csv" "region,product,user,kind,quantity,unit,share,value_share,value_rough\n")
    (let [g (sup/load-graph (str d))]
      (is (= "usgs-ammonia" (get-in g [:recipes ["LT" "C_NFER"] "C_GASE" :override])))
      (is (nil? (get-in g [:recipes ["DE" "C_NFER"] "C_GASE" :override])))
      (is (= :critical (sup/classify g "LT" "C_NFER" "C_GASE"))))))

(deftest a-power-stations-coals-are-one-pool
  ;; America's coal power: 1.19 TJ of sub-bituminous coal, 1.26 of other
  ;; bituminous and 0.12 of lignite per TJ, each a feedstock on its own
  ;; (at least 0.1 TJ per TJ); lignite 3% of the coals' value, 5% of
  ;; their heat. One coal stands in for another in a station's boilers
  ;; (blending, switching mines), so they are one pool, its availability
  ;; the share-weighted mean of its members', as electricity by source is
  (let [ps [{:code "C_SUBC" :unit "tonnes" :group "raw"}
            {:code "C_OTBC" :unit "tonnes" :group "raw"}
            {:code "C_LIBC" :unit "tonnes" :group "raw"}
            {:code "C_ANTH" :unit "tonnes" :group "raw"}
            {:code "C_GASE" :unit "tonnes" :group "raw"}
            {:code "C_POWC" :unit "TJ" :group "energy"}
            {:code "C_HWAT" :unit "TJ" :group "energy"}
            {:code "C_STEL" :unit "tonnes" :group "material"}
            {:code "C_COKC" :unit "tonnes" :group "raw"}
            {:code "SRV" :unit "Meuro" :group "service"}]
        rs [{:region "US" :output "C_POWC" :input "C_SUBC" :phys 63.04 :share 0.0156}
            {:region "US" :output "C_POWC" :input "C_OTBC" :phys 49.0 :share 0.0077}
            {:region "US" :output "C_POWC" :input "C_LIBC" :phys 9.92 :share 0.0008}
            ;; a coal at a thousandth of the heat: negligible, no member
            {:region "US" :output "C_POWC" :input "C_ANTH" :phys 0.13 :share 0.0011}
            {:region "US" :output "C_POWC" :input "SRV" :phys 1e-4 :share 0.3}
            ;; gas power with two coals each under 0.1 TJ per TJ and 5%
            ;; of its value (the industry mix's CHP): no pool is made
            ;; critical by adding them up, only by a member critical alone
            {:region "US" :output "C_POWG" :input "C_OTBC" :phys 2.6 :share 0.03}
            {:region "US" :output "C_POWG" :input "C_SUBC" :phys 3.5 :share 0.03}
            {:region "US" :output "C_POWG" :input "SRV" :phys 1e-4 :share 0.3}
            ;; a heat plant on one coal: a pool of one
            {:region "US" :output "C_HWAT" :input "C_OTBC" :phys 40.0 :share 0.05}
            {:region "US" :output "C_HWAT" :input "SRV" :phys 1e-4 :share 0.3}
            ;; not a power station: steel's coking coal and its other coal
            ;; stay critical each on its own
            {:region "US" :output "C_STEL" :input "C_COKC" :phys 0.5 :share 0.06}
            {:region "US" :output "C_STEL" :input "C_OTBC" :phys 0.15 :share 0.01}
            {:region "US" :output "C_STEL" :input "SRV" :phys 1e-4 :share 0.3}]
        g (sup/graph {:products ps :recipes rs})
        row (fn [o i] (get-in g [:recipes ["US" o] i]))
        avail (fn [cut k] (get-in (sup/outage g cut {}) [:products ["US" k]] 1.0))]
    (testing "the coals classified together, critical, tagged :coal"
      (doseq [i ["C_SUBC" "C_OTBC" "C_LIBC"]]
        (is (= :critical (:kind (row "C_POWC" i))) i)
        (is (= :coal (:pool (row "C_POWC" i))) i))
      (is (= :substitutable (:kind (row "C_POWC" "C_ANTH"))) "negligible: no member")
      (is (nil? (:pool (row "C_POWC" "C_ANTH")))))
    (testing "a 10% lignite cut takes its share of the pool, not 10% of the power"
      (is (close? (- 1.0 (* 0.1 (/ 0.0008 (+ 0.0156 0.0077 0.0008)))) (avail {["US" "C_LIBC"] 0.1} "C_POWC") 1e-6))
      (is (close? (- 1.0 (* 0.4 (/ 0.0156 (+ 0.0156 0.0077 0.0008)))) (avail {["US" "C_SUBC"] 0.4} "C_POWC") 1e-6)))
    (testing "every coal cut: the power falls with it"
      (is (close? 0.6 (avail {["US" "C_SUBC"] 0.4 ["US" "C_OTBC"] 0.4 ["US" "C_LIBC"] 0.4} "C_POWC") 1e-6)))
    (testing "coals each below the rule stay substitutable together"
      (is (= :substitutable (:kind (row "C_POWG" "C_OTBC"))))
      (is (= :substitutable (:kind (row "C_POWG" "C_SUBC"))))
      (is (nil? (:pool (row "C_POWG" "C_OTBC")))))
    (testing "a pool of one is its coal"
      (is (= :coal (:pool (row "C_HWAT" "C_OTBC"))))
      (is (close? 0.7 (avail {["US" "C_OTBC"] 0.3} "C_HWAT") 1e-6)))
    (testing "steel's coals are not pooled"
      (is (nil? (:pool (row "C_STEL" "C_OTBC"))))
      (is (= :critical (:kind (row "C_STEL" "C_OTBC"))))
      (is (close? 0.7 (avail {["US" "C_OTBC"] 0.3} "C_STEL") 1e-6)))))
