(ns bnm.sfc-test
  (:require [bnm.engine :as eng]
            [bnm.keen :as keen]
            [bnm.modules.sfc :as msfc]
            [bnm.sfc :as sfc]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

;; -- a hand-built economy -----------------------------------------------------

(defn- balanced
  "A balance sheet whose financial sector holds what closes each
  instrument's row (money as its liability, the rest as its assets)."
  [bsm]
  (let [others (dissoc bsm :fin)
        tot (fn [k side] (reduce + 0.0 (for [[_ m] others] (get-in m [k side] 0.0))))
        fin (:fin bsm)]
    (assoc bsm :fin
           (merge-with merge fin
                       {:money {:l (- (+ (tot :money :a) (get-in fin [:money :a] 0.0)) (tot :money :l))}
                        :debt {:a (- (+ (tot :debt :l) (get-in fin [:debt :l] 0.0)) (tot :debt :a))}
                        :equity {:a (- (+ (tot :equity :l) (get-in fin [:equity :l] 0.0)) (tot :equity :a))}
                        :other {:a (- (+ (tot :other :l) (get-in fin [:other :l] 0.0)) (tot :other :a))}}))))

(def bsm0
  (balanced
   {:hh {:money {:a 60.0 :l 0.0} :debt {:a 20.0 :l 80.0} :equity {:a 40.0 :l 0.0} :other {:a 50.0 :l 5.0}}
    :nfc {:money {:a 20.0 :l 0.0} :debt {:a 10.0 :l 90.0} :equity {:a 30.0 :l 100.0} :other {:a 20.0 :l 20.0}}
    :fin {:money {:a 3.0} :debt {:l 20.0} :equity {:l 30.0} :other {:l 60.0}}
    :gov {:money {:a 10.0 :l 2.0} :debt {:a 5.0 :l 70.0} :equity {:a 10.0 :l 0.0} :other {:a 5.0 :l 3.0}}
    :row {:money {:a 5.0 :l 3.0} :debt {:a 30.0 :l 25.0} :equity {:a 20.0 :l 25.0} :other {:a 5.0 :l 5.0}}}))

(def keen-p (merge (keen/from-raw keen/gcl) {:ustar 0.06 :phi-slope 1.0}))

(def p0
  {:n 0.04 :half-life 3.0
   :v {:hh 0.10 :nfc 0.60 :fin 0.06}
   :omega {:hh 0.05 :nfc 0.70 :fin 0.07 :gov 0.18}
   :tp {:hh 0.0 :nfc 0.03 :fin 0.02 :gov 0.0}
   :tax {:hh 0.15 :nfc 0.20 :fin 0.20}
   :socc 0.15
   :other {:hh 0.01 :nfc -0.005 :fin 0.0 :row -0.002}
   :inv-share {:hh 0.06 :fin 0.01}
   :a1 0.85 :a2 0.03
   :mu 0.30 :x 0.33
   :i-l {:hh 0.04 :nfc 0.04 :fin 0.02 :gov 0.03 :row 0.03}
   :i-a {:hh 0.02 :nfc 0.02 :gov 0.02 :row 0.03}
   :q-l {:hh 0.0 :nfc 0.03 :fin 0.03 :gov 0.0 :row 0.03}
   :q-a {:hh 0.03 :nfc 0.03 :gov 0.03 :row 0.03}
   :target {:hh {:money {:l 0.0} :equity {:l 0.0} :other {:l 0.05}}
            :nfc {:money {:l 0.0} :equity {:l 1.0} :other {:l 0.2}}
            :fin {:money {:a 0.03} :debt {:l 0.2} :equity {:l 0.3} :other {:l 0.6}}
            :gov {:money {:a 0.1 :l 0.02} :debt {:a 0.05} :equity {:a 0.1 :l 0.0} :other {:a 0.05 :l 0.03}}
            :row {:money {:a 0.05 :l 0.03} :equity {:a 0.2 :l 0.25} :other {:a 0.05 :l 0.05} :debt {:l 0.25}}}
   :share {:hh {:debt 0.12 :equity 0.24 :other 0.3} :nfc {:debt 0.12 :equity 0.37 :other 0.25}}
   :debt-target {:hh 0.82 :nfc 0.92}
   :rho 0.02 :kappa-bar 0.12
   :phillips keen-p
   :invest (assoc keen-p :pistar 0.18 :kappa-slope 0.5)})

(def st0 {:year 2000 :Y 100.0 :w 0.6 :lambda 0.94 :pi 0.18 :cg 18.0 :ig 3.0 :ben 12.0 :bsm bsm0})

(defn- run-steps [st p n] (reductions (fn [[s _] _] (let [{s' :state o :out} (sfc/step s p)] [s' o])) [st nil] (range n)))

;; -- the transaction-flow matrix ------------------------------------------------

(deftest every-row-and-column-closes
  (let [{:keys [out]} (sfc/step st0 p0)
        {:keys [rows nl]} (:booked out)
        y (:Y out)]
    (is (= #{:consumption :investment :net-exports :value-added :wages :production-taxes :interest :dividends
             :income-taxes :social-contributions :social-benefits :other}
           (set (keys rows))))
    (doseq [[r m] rows]
      (is (< (Math/abs (reduce + 0.0 (vals m))) (* 1e-12 y)) (str r " sums to zero")))
    (is (< (Math/abs (reduce + 0.0 (vals nl))) (* 1e-12 y)) "net lending sums to zero")
    (is (< (Math/abs (reduce + 0.0 (for [[_ m] rows] (get m :prod 0.0)))) (* 1e-12 y)) "production's column balances")
    (is (< (sfc/tfm-gap (:booked out)) (* 1e-12 y)))
    (is (= sfc/sectors (vec (keys (sort-by (comp (zipmap sfc/sectors (range)) key) nl)))))))

;; -- the balance sheet -------------------------------------------------------------

(deftest the-starting-balance-sheet-closes-on-a-stated-buffer
  (let [bad (-> bsm0 (update-in [:hh :money :a] + 7.0) (update-in [:nfc :debt :l] + 3.0))
        {:keys [bsm buffer]} (sfc/close-init bad)]
    (is (< 1.0 (sfc/bsm-gap bad)))
    (is (< (sfc/bsm-gap bsm) 1e-12))
    (doseq [[k v] {:money 7.0 :debt -3.0 :equity 0.0 :other 0.0}]
      (is (< (Math/abs (- v (get buffer k))) 1e-9) (str "assets over liabilities, " k)))
    (is (< (Math/abs (- (+ 3.0 7.0) (get-in bsm [:row :money :l]))) 1e-9) "the rest of the world takes the money gap as a liability")
    (is (< (Math/abs (- (+ 30.0 3.0) (get-in bsm [:row :debt :a]))) 1e-9) "and the debt gap as an asset")
    (is (= (dissoc bad :row) (dissoc bsm :row)) "no domestic position moves")))

(deftest identities-hold-exactly-every-step
  (let [steps (rest (run-steps st0 p0 40))]
    (doseq [[s o] steps :let [y (:Y o)]]
      (is (< (sfc/tfm-gap (:booked o)) (* 1e-10 y)) "transaction-flow matrix")
      (is (< (sfc/bsm-gap (:bsm s)) (* 1e-10 y)) "every instrument's assets equal its liabilities")
      (is (< (:fof-gap o) (* 1e-10 y)) "each sector's net worth changes by its net lending and revaluation (and the stated buffer)")
      (is (< (Math/abs (:buffer o)) (* 1e-10 y)) "the redundant equation leaves nothing for the buffer but rounding"))
    (testing "the path is not trivial"
      (let [ys (map (comp :Y second) steps)]
        (is (every? pos? ys))
        (is (> (count (distinct (map #(Math/round (* 1e4 (/ (:w (first %)) 1.0))) steps))) 5) "the wage share moves")))))

(deftest the-buffer-takes-an-imposed-residual
  ;; a residual forced into the financial sector's books lands in government
  ;; deposits, reported, and the identities still close
  (let [{:keys [out]} (sfc/step st0 (assoc p0 :inject 0.5))]
    (is (< (Math/abs (- 0.5 (:buffer out))) 1e-9))
    (is (< (sfc/bsm-gap (:end out)) 1e-9))
    (is (< (:fof-gap out) 1e-9))))

;; -- Keen inside the frame ------------------------------------------------------------

(deftest keen-supplies-the-phillips-curve-and-investment
  (let [{s :state o :out} (sfc/step st0 p0)
        ye (* 100.0 1.04)]
    (is (< (Math/abs (- (get-in o [:inv :nfc]) (* ye (keen/investment (:invest p0) 0.18)))) 1e-12))
    (is (< (Math/abs (- (:w s) (* 0.6 (Math/exp (- (keen/phillips keen-p 0.94) (:alpha keen-p)))))) 1e-12))
    (is (< (Math/abs (- (:lambda s) (min 0.995 (* 0.94 (+ 1.0 (/ (- (/ (get-in o [:inv :nfc]) ye) (:kappa-bar p0)) (:nu keen-p))))))) 1e-12)
        "employment: dl/l = g - alpha - beta = (kappa - kappa-bar)/nu")
    (is (< (Math/abs (- (:wage-share o) (/ (reduce + (vals (get-in o [:x :comp]))) (:Y o)))) 1e-12)
        "the wage share is compensation over output")
    (is (< (Math/abs (- (:private-debt o) (/ (+ (get-in s [:bsm :hh :debt :l]) (get-in s [:bsm :nfc :debt :l])) (:Y o)))) 1e-12)
        "Keen's d is households' and firms' debt over output")))

;; -- data and learning ------------------------------------------------------------------

(defn- sim-panel
  "A country's panel made by the model itself, as the data layer writes it."
  [n]
  (let [steps (rest (run-steps st0 p0 n))]
    (into {(:year st0) (sfc/obs-of st0 nil)}
          (for [[s o] steps] [(:year s) (sfc/obs-of s o)]))))

(deftest observed-decomposition-inverts-the-books
  (let [{s :state o :out} (sfc/step st0 p0)
        ob (sfc/obs-of s o)
        x (sfc/observed ob)]
    (doseq [k [:Y :W :nx :socc :ben]]
      (is (< (Math/abs (- (get x k) (get-in o [:x k]))) 1e-9) (str k)))
    (doseq [sct [:hh :nfc :fin :row]]
      (is (< (Math/abs (- (get-in x [:other sct]) (get-in o [:x :other sct]))) 1e-9) (str "other " sct)))
    (doseq [[sct v] (:nl (sfc/book x))]
      (is (< (Math/abs (- v (get-in o [:booked :nl sct]))) 1e-9) (str "net lending " sct)))))

(deftest learning-recovers-behaviour-and-is-causal
  (let [panel {"SIM" (sim-panel 30)}
        p (sfc/learn panel {} "SIM" 2025 {})]
    (is (< (Math/abs (- (:a1 p) 0.85)) 0.03) (str "a1 " (:a1 p)))
    (is (< (Math/abs (- (:a2 p) 0.03)) 0.02) (str "a2 " (:a2 p)))
    (is (< (Math/abs (- (get-in p [:v :nfc]) 0.6)) 1e-6))
    (is (< (Math/abs (- (get-in p [:i-l :hh]) 0.04)) 1e-6) "implicit rates")
    (is (< (Math/abs (- (:socc p) 0.15)) 1e-6))
    (testing "investment through keen/investment: the fitted curve gives the simulated shares"
      (let [o (sfc/observed (get-in panel ["SIM" 2020]))
            pi (sfc/profit-share (sfc/observed (get-in panel ["SIM" 2019])))]
        (is (< (Math/abs (- (keen/investment (:invest p) pi) (/ (get-in o [:inv :nfc]) (/ (:Y o) 1.0)))) 0.02))))
    (testing "nothing after the origin is read"
      (let [cut {"SIM" (into {} (filter #(<= (key %) 2015)) (get panel "SIM"))}]
        (is (= (sfc/learn panel {} "SIM" 2015 {}) (sfc/learn cut {} "SIM" 2015 {})))))))

(defn- sim-keen
  "keen.csv's rows for the simulated country: at year y + 1 the employment
  rate of data year y and the Phillips curve the simulation used."
  [n]
  {"SIM" (into {} (for [[s _] (run-steps st0 p0 n)]
                    [(inc (:year s)) {"keen/lambda" (:lambda s) "keen/state-year" (double (:year s))
                                      "keen/phi-slope" (:phi-slope keen-p) "keen/l-star" (- 1.0 (:ustar keen-p))
                                      "keen/alpha" (:alpha keen-p) "keen/beta" (:beta keen-p)
                                      "keen/delta" (:delta keen-p) "keen/nu" (:nu keen-p)}]))})

(deftest one-year-ahead-on-a-simulated-panel
  ;; the model forecasting its own path: near-exact, and better than persistence
  (let [panel {"SIM" (sim-panel 30)}
        rows (sfc/one-year-ahead panel (sim-keen 30) {:min-years 8})
        s (sfc/score rows)]
    (is (seq rows))
    (is (every? #(<= (:origin %) (- (:year %) 1)) rows))
    (is (< (get-in s [:all :model]) (get-in s [:all :persistence])))))

;; -- the engine modules -----------------------------------------------------------------

(deftest modules-declare-and-reproduce-the-step
  (let [ms (msfc/modules "SIM" st0 p0)
        n 12
        direct (rest (run-steps st0 p0 n))
        r (eng/run ms {:from 2001 :to (+ 2000 n)})
        em (:emits r)]
    (is (empty? (eng/validate ms)))
    (is (= "F -> R -> D" (:text (eng/causal-order ms))))
    (is (= [:sfc-finance :sfc-firms :sfc-accounts] (:order (eng/causal-order ms))))
    (is (some #(= :F1 (:channel %)) (:emits (first ms))))
    (doseq [[i [s o]] (map-indexed vector (butlast direct))]
      (is (< (Math/abs (- (:Y o) (get-in em [:sfc-accounts "sfc/output" i]))) (* 1e-12 (:Y o))))
      (is (< (Math/abs (- (get-in o [:booked :nl :gov]) (* (:Y o) (get-in em [:sfc-accounts "sfc/net-lending-gov" i])))) 1e-9))
      (is (= (:bsm s) (get-in em [:sfc-finance "sfc/bsm" (inc i)]))))
    (testing "the engine's own books close"
      (is (every? #(< % 1e-10) (keep identity (get-in em [:sfc-finance "sfc/bsm-gap"]))))
      (is (every? #(< % 1e-10) (keep identity (get-in em [:sfc-accounts "sfc/tfm-gap"])))))))

;; -- the data on disk (skipped when the data layer has not been built) ---------------------

(def panel-path "data/derived/sfc/panel.csv")

(deftest the-data-on-disk
  (when (.exists (io/file panel-path))
    (let [panel (sfc/read-panel panel-path)
          us (get panel "USA")]
      (is (< 30 (count panel)))
      (is (contains? (get panel "CHN") 2019))
      (is (= :imputed (:stocks-source (get-in panel ["CHN" 2019]))))
      (testing "observed decomposition gives the data's net lending"
        (let [ob (get us 2009) x (sfc/observed ob) nl (:nl (sfc/book x))]
          (doseq [sct [:hh :nfc :fin :row]]
            (is (< (Math/abs (- (get nl sct) (get-in ob [:f sct :nl]))) 1e-6) (str sct)))))
      (testing "a run from data closes"
        (let [p (sfc/learn panel {} "USA" 2005 {})
              st (sfc/init panel {} "USA" 2005 p)
              steps (rest (run-steps st p 5))]
          (doseq [[s o] steps]
            (is (< (sfc/bsm-gap (:bsm s)) (* 1e-9 (:Y o))))
            (is (< (:fof-gap o) (* 1e-9 (:Y o))))))))))

(deftest modules-pass-the-engine-rules
  ;; balance-sheet stocks are levels from :observe, flows are rates from :step
  (let [ms (msfc/modules "SIM" st0 p0)
        levels (fn [m] (set (for [e (:emits m) :when (= :level (:phase e))] (:var e))))]
    (is (empty? (eng/validate ms)))
    (doseq [m ms :when (seq (levels m))]
      (is (fn? (:observe m)) (str (:module m) " declares levels, so has an observe")))
    (is (= #{"sfc/bsm" "sfc/bsm-gap" "sfc/fof-gap"} (levels (first ms))))
    (let [f (first ms)
          lv ((:observe f) (:init f) {} {:sfc p0} 2001)
          st ((:step f) (:init f) {} {:sfc p0} 2001 1)]
      (is (= (levels f) (set (keys lv))) "the observe emits its levels and nothing else")
      (is (empty? (filter (levels f) (keys (:emits st)))) "the step emits no level")
      (is (= (:bsm st0) (get lv "sfc/bsm")) "the first year's opening balance sheet is the start"))
    (is (every? #(empty? (levels %)) (rest ms)) "firms and accounts emit rates only")))
