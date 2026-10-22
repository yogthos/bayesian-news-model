(ns bnm.energy-pilot-test
  (:require [augur.math :as m]
            [bnm.energy-pilot :as ep]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-9))

(deftest the-leontief-inverse
  ;; L = (I - A)^-1 by its Neumann series; for A = [[0.1 0.2] [0.3 0.4]]
  ;; (a_rs: r's share of s's output), (I - A)^-1 = [[0.6 0.2] [0.3 0.9]]/det
  (let [a {["r" "r"] 0.1 ["r" "s"] 0.2 ["s" "r"] 0.3 ["s" "s"] 0.4}
        l (ep/leontief a ["r" "s"])
        det (- (* 0.9 0.6) (* 0.2 0.3))]
    (is (close? (l ["r" "r"]) (/ 0.6 det)))
    (is (close? (l ["r" "s"]) (/ 0.2 det)))
    (is (close? (l ["s" "r"]) (/ 0.3 det)))
    (is (close? (l ["s" "s"]) (/ 0.9 det)))))

(deftest the-energy-shock
  ;; refining's log price change less the output-weighted mean's
  (let [pc {"REF" 1.5 "AGR" 1.0 "BUS" 1.0}
        go {"REF" 10.0 "AGR" 45.0 "BUS" 45.0}
        mean (+ (* 0.1 1.5) (* 0.9 1.0))]
    (is (< (Math/abs (- (ep/shock pc go) (- (Math/log 1.5) (Math/log mean)))) 1e-12))))

(deftest least-squares-without-intercept
  (let [g (m/rng 1)
        rows (vec (for [_ (range 500)] (let [x1 (m/normal! g) x2 (m/normal! g)] {:x [x1 x2] :y (+ (* 2.0 x1) (* -1.0 x2) (* 0.1 (m/normal! g)))})))
        b (ep/ols rows)]
    (is (< (Math/abs (- 2.0 (first b))) 0.02))
    (is (< (Math/abs (+ 1.0 (second b))) 0.02))))

(defn- world
  "Synthetic sectors whose output falls with their supply-chain energy
  share times the shock (Hulten's world, unit pass-through)."
  []
  (let [g (m/rng 2)]
    (vec (for [c (range 20) s (range 10) t (range 1970 2010)
               :let [a (* 0.02 (inc s)) ell (* 1.5 a) e (if (#{1974 1979 1990 2008} t) 0.4 (* 0.05 (m/normal! g)))]
               h [0]]
           {:c (str "C" c) :s (str "S" s) :t t :h h :a a :ell ell :ell-ref 1.0 :e e :lag (* 0.02 (m/normal! g))
            :g (+ 0.02 (* -1.0 ell e) (* 0.02 (m/normal! g)))}))))

(deftest a-world-where-the-supply-chain-matters
  ;; the network model finds it, the trend cannot; Hulten's benchmark is
  ;; exact there, so its error is about the noise's (0.02^2)
  (let [r (ep/evaluate (world) [1974 1979 1990 2008])]
    (is (< (get-in r [:mse :network]) (get-in r [:mse :trend])))
    (is (< (get-in r [:mse :network]) (* 0.6 (get-in r [:mse :trend]))))
    (is (< (get-in r [:mse :hulten]) 0.0007))
    (is (< (get-in r [:mse :hulten]) (* 0.3 (get-in r [:mse :trend]))))
    (testing "the training rows per shock are those whose outcome year is before it"
      (is (= (* 200 (- 1974 1970)) (get-in r [:train 1974])))
      (is (= (* 200 (- 2008 1970)) (get-in r [:train 2008]))))))

(deftest no-leakage-from-the-future
  ;; poisoning every outcome in or after the shock year leaves the
  ;; predictions for the shock unchanged, in both modes
  (let [rows (world)
        poisoned (mapv #(if (>= (+ (:t %) (:h %)) 1990) (assoc % :g 100.0) %) rows)
        preds (fn [rs opts] (map (juxt :c :s :h :trend :hulten :network) (:rows (ep/evaluate rs [1990] opts))))]
    (is (= (preds rows {}) (preds poisoned {})))
    (is (= (preds rows {:corrected? true}) (preds poisoned {:corrected? true})))))

(deftest the-corrected-terms
  ;; deviation after review: the supply-chain share leaves out refining's
  ;; own output (the Leontief identity), and Hulten's pass-through is
  ;; L_REF,s / L_REF,REF of the observed refined-products price
  (is (close? 0.3 (ep/chain-share {:s "AGR" :a 0.1 :ell 0.4} false)))
  (is (close? 0.3 (ep/chain-share {:s "AGR" :a 0.1 :ell 0.4} true)))
  (is (close? 1.0 (ep/chain-share {:s "REF" :a 0.05 :ell 1.05} false)))
  (is (close? 0.0 (ep/chain-share {:s "REF" :a 0.05 :ell 1.05} true)))
  (let [w (mapv #(assoc % :ell-ref 1.25) (world))
        r (first (:rows (ep/evaluate w [2008] {:corrected? true})))
        r0 (first (:rows (ep/evaluate w [2008])))]
    (is (close? (- (:trend r) (:hulten r)) (* (/ (:ell r) 1.25) (:e r))))
    (is (close? (- (:trend r0) (:hulten r0)) (* (:ell r0) (:e r0))) "the registered Hulten: L_REF,s e")))

(deftest the-exposures
  ;; from A = [[0 0.2] [0.1 0.1]] over [AGR REF]: L = [[0.9 0.2] [0.1 1]]/0.88
  (let [a {["REF" "AGR"] 0.1 ["REF" "REF"] 0.1 ["AGR" "REF"] 0.2}
        ex (ep/exposures a (ep/leontief a ["AGR" "REF"]) ["AGR" "REF"])]
    (is (close? 0.1 (get-in ex ["AGR" :a])))
    (is (close? (/ 0.1 0.88) (get-in ex ["AGR" :ell])))
    (is (close? (/ 1.0 0.88) (get-in ex ["AGR" :ell-ref])))
    (is (close? 0.1 (get-in ex ["AGR" :colsum])))
    (is (close? 0.3 (get-in ex ["REF" :colsum])))))

(deftest flat-prices
  ;; a country-year whose sectors all share one deflator
  (is (ep/flat-prices? {"AGR" 1.02 "REF" 1.0200000000000001}))
  (is (not (ep/flat-prices? {"AGR" 1.02 "REF" 1.5})))
  (is (not (ep/flat-prices? nil))))

(def ^:private fixture
  ;; one country, two sectors; the table of 1966 only, so only t = 1969
  ;; (t - 3) has rows; 1968's prices are flat
  {:sectors ["AGR" "REF"]
   :go (into {} (for [y (range 1965 1971) [s v] [["AGR" 90.0] ["REF" 10.0]]] [["X" y s] v]))
   :flows {["X" 1966] {["REF" "AGR"] 9.0 ["REF" "REF"] 1.0 ["AGR" "REF"] 2.0 ["AGR" "AGR"] 0.0}}
   :prices {["X" 1968] {"AGR" 1.02 "REF" 1.02}
            ["X" 1969] {"AGR" 1.0 "REF" 1.5}
            ["X" 1970] {"AGR" 1.0 "REF" 1.1}}
   :vol {["X" 1968 "AGR"] 0.01 ["X" 1969 "AGR"] 0.02 ["X" 1970 "AGR"] 0.03
         ["X" 1968 "REF"] -0.01 ["X" 1969 "REF"] -0.02 ["X" 1970 "REF"] -0.03}
   :backcast {["X" 1966] 1.0}})

(deftest the-rows
  (let [rs (ep/build-rows fixture)
        by (into {} (map (juxt (juxt :s :h) identity) rs))
        agr0 (by ["AGR" 0])]
    (is (= 4 (count rs)) "t = 1969 only, horizons 0 and 1 (no 1971 volume)")
    (is (every? #(= 1969 (:t %)) rs))
    (is (close? (- (Math/log 1.5) (Math/log 1.05)) (:e agr0)) "the shock with the previous year's output weights")
    (is (close? 0.01 (:lag agr0)) "last year's growth")
    (is (close? 0.02 (:g agr0)))
    (is (close? 0.03 (:g (by ["AGR" 1]))) "the outcome at t + h")
    (is (close? 0.1 (:a agr0)) "the direct share from the table of t - 3")
    (is (close? (/ 0.1 0.88) (:ell agr0)))
    (is (close? (/ 1.0 0.88) (:ell-ref agr0)))
    (is (close? 0.9 (:share agr0)) "the sector's share of the economy's output at t - 1")
    (is (close? 0.1 (:share (by ["REF" 0]))))
    (is (close? 1.0 (:backcast agr0)))
    (is (close? 0.3 (:colsum (by ["REF" 0]))))
    (is (:flat agr0) "1968, the lag's year, has one deflator for all sectors")))

(deftest the-kept-rows
  (let [ok {:s "AGR" :share 0.01 :flat false :colsum 0.5 :backcast 0.0}]
    (is (ep/keep-row? ok {}))
    (is (not (ep/keep-row? (assoc ok :s "REF") {})))
    (is (not (ep/keep-row? (assoc ok :share 0.0005) {})))
    (is (not (ep/keep-row? (assoc ok :flat true) {})))
    (is (not (ep/keep-row? (assoc ok :colsum 1.0) {})))
    (is (ep/keep-row? (assoc ok :backcast 1.0) {}))
    (is (not (ep/keep-row? (assoc ok :backcast 1.0) {:backcast-max 0.5})))))

(deftest paired-rows
  ;; the registered intervals resample countries only (one block of
  ;; periods); the two-way extra resamples the shock years too
  (let [rs [{:t 1974 :c "A" :g 0.1 :network 0.2 :trend 0.3} {:t 2008 :c "B" :g 0.1 :network 0.2 :trend 0.3}]]
    (is (= [[0 0.2 0.3 0.1 "A"] [0 0.2 0.3 0.1 "B"]] (ep/pair-rows rs :network :trend false)))
    (is (= [[1974 0.2 0.3 0.1 "A"] [2008 0.2 0.3 0.1 "B"]] (ep/pair-rows rs :network :trend true)))))

(deftest the-economy-s-exposure
  ;; output-weighted supply-chain energy share across sectors
  (is (< (Math/abs (- (ep/economy-exposure {"A" [0.1 0.3] "B" [0.0 0.1]} {"A" 3.0 "B" 1.0}) (/ (+ (* 3.0 0.3) (* 1.0 0.1)) 4.0))) 1e-12)))
