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

;; -- review fixes (bnm-4hv.10 review) -----------------------------------------------------

(defn- with-trade
  "The simulated panel with the rest of the world's exports and imports
  written in, their balance off the frame's net exports by `gap` of output
  (a statistical discrepancy, or product taxes outside B1G), imports `m`
  (0.3) of absorption."
  [panel gap & [m]]
  (let [m (or m 0.3)]
    (into {} (for [[y ob] panel :let [x (when (:f ob) (sfc/observed ob))]]
               [y (if x
                    (let [a (+ (reduce + (vals (:cons x))) (reduce + (vals (:inv x))))]
                      (-> ob (assoc-in [:f :row :imports] (* m a))
                          (assoc-in [:f :row :exports] (+ (:nx x) (* m a) (* gap (:Y x))))))
                    ob)]))))

(defn- import-shares
  "Each data year's imports over absorption plus exports in `panel`."
  [panel]
  (for [[_ ob] panel :when (:f ob)
        :let [x (sfc/observed ob) a (+ (reduce + (vals (:cons x))) (reduce + (vals (:inv x))))]]
    (/ (get-in ob [:f :row :imports]) (+ a (get-in ob [:f :row :exports])))))

(deftest exports-are-learned-in-the-frames-net-exports
  ;; X - mu A must give the frame's net exports at the origin, whatever the
  ;; trade data's own balance: else the first step jumps by the gap
  (let [panel {"SIM" (with-trade (sim-panel 30) 0.05)}
        p (sfc/learn panel {} "SIM" 2025 {})
        x (sfc/observed (get-in panel ["SIM" 2025]))
        a (/ (+ (reduce + (vals (:cons x))) (reduce + (vals (:inv x)))) (:Y x))]
    (is (<= (- (apply min (import-shares (panel "SIM"))) 1e-12) (:mu p) (+ (apply max (import-shares (panel "SIM"))) 1e-12))
        "imports over absorption plus exports (bnm-4hv.17)")
    (is (< (Math/abs (- (- (:x p) (* (:mu p) a)) (/ (:nx x) (:Y x)))) 2e-3)
        (str "x " (:x p) " gives net exports " (- (:x p) (* (:mu p) a)) ", the frame's " (/ (:nx x) (:Y x))))))

(deftest imports-above-absorption-keep-the-multiplier-positive
  ;; bnm-4hv.17: Ireland's and Luxembourg's imports exceed their absorption
  ;; (goods imported to be exported again). Taken over absorption alone the
  ;; import share was 1.7 and 2.4, so 1 - mu turned the multiplier's sign:
  ;; spending lowered output, and output fell through zero within twelve
  ;; years. Over absorption plus exports it stays below 1.
  (let [panel {"SIM" (with-trade (sim-panel 30) 0.0 1.6)}
        p (sfc/learn panel {} "SIM" 2025 {})
        x (sfc/observed (get-in panel ["SIM" 2025]))
        a (/ (+ (reduce + (vals (:cons x))) (reduce + (vals (:inv x)))) (:Y x))
        st (sfc/init panel {} "SIM" 2025 p)
        y (fn [p] (:Y (:out (sfc/step st p))))]
    (is (< (:mu p) 1.0) (str "mu " (:mu p)))
    (is (<= (- (apply min (import-shares (panel "SIM"))) 1e-12) (:mu p) (+ (apply max (import-shares (panel "SIM"))) 1e-12)))
    (is (< (Math/abs (- (- (:x p) (* (:mu p) a)) (/ (:nx x) (:Y x)))) 2e-3) "the origin's net exports")
    (is (> (y (assoc p :spend-shock {2026 0.01})) (y p)) "more spending raises output")
    (is (every? #(pos? (:Y %)) (map second (rest (run-steps st p 30)))) "output stays positive")))

(deftest keen-is-read-at-the-panels-vintage
  ;; the origin's flows are known at origin + 2: employment is the origin
  ;; year's, from keen.csv's row of that vintage, not origin - 1's
  (let [panel {"SIM" (sim-panel 12)}
        row (fn [sy lam] {"keen/lambda" lam "keen/state-year" (double sy) "keen/phi-slope" 1.0 "keen/l-star" 0.94
                          "keen/alpha" 0.02 "keen/beta" 0.01 "keen/delta" 0.05 "keen/nu" 3.0})
        kn {"SIM" {2006 (row 2004 0.90) 2007 (row 2005 0.93) 2008 (row 2006 0.99)}}
        p (sfc/learn panel kn "SIM" 2005 {})]
    (is (= 0.93 (:lambda (sfc/init panel kn "SIM" 2005 p))) "state-year 2005, known 2007")
    (is (= 0.90 (:lambda (sfc/init panel {"SIM" (dissoc (kn "SIM") 2007)} "SIM" 2005 p)))
        "an older state when the origin's is not out by origin + 2; never a later one")))

(defn- perturb-b
  "Every balance sheet in `panel` with households' debt assets raised by
  `g` of output and nothing else: a measured discrepancy in the debt row."
  [panel g]
  (into {} (for [[y ob] panel] [y (cond-> ob (:b ob) (update-in [:b :hh :debt :a] + (* g (:y ob))))])))

(deftest learning-uses-the-closed-balance-sheet-the-model-runs-on
  ;; a measured discrepancy is closed on the rest of the world at the start;
  ;; its rates must be learned on those closed stocks, else the closure
  ;; earns interest the data never paid
  (let [clean {"SIM" (sim-panel 30)}
        bad {"SIM" (perturb-b (clean "SIM") 0.5)}
        row-int (fn [panel]
                  (let [p (sfc/learn panel {} "SIM" 2025 {})
                        st (sfc/init panel {} "SIM" 2025 p)
                        ye (* (:Y st) (+ 1.0 (:n p)))]
                    (/ (get-in (sfc/property-income (:bsm st) p ye) [:int-paid :row]) ye)))]
    (let [st (sfc/init bad {} "SIM" 2025 (sfc/learn bad {} "SIM" 2025 {}))]
      (is (< 0.4 (/ (get-in st [:init-buffer :debt]) (:Y st)) 0.6)))
    (is (< (Math/abs (- (row-int bad) (row-int clean))) 1e-3)
        (str "rest of the world's interest paid " (row-int bad) " against " (row-int clean)))))

(deftest implicit-rates-are-robust-to-a-tiny-stock
  ;; one year whose opening stock is near zero (a break, a hyperinflation)
  ;; must not carry its ratio into the rate a decade on
  (let [panel {"SIM" (update-in (sim-panel 30) [2003 :b :hh :debt :l] * 1e-4)}
        p (sfc/learn panel {} "SIM" 2012 {})]
    (is (< (Math/abs (- 0.04 (get-in p [:i-l :hh]))) 2e-3) (str "hh rate " (get-in p [:i-l :hh])))))

(deftest one-year-ahead-reports-the-starting-closure
  (let [panel {"SIM" (perturb-b (sim-panel 30) 0.5)}
        rows (sfc/one-year-ahead panel (sim-keen 30) {:min-years 8})]
    (is (seq rows))
    (is (every? #(< 0.4 (:init-buffer %) 0.6) rows) "the discrepancy moved to the rest of the world, over output")))

;; -- the assumption audit: behavioural blocks replaced by observed values ------------

(deftest given-blocks-reproduce-the-observed-year
  ;; a wrong model handed every block of the true year reproduces its net
  ;; lending; handed none, it does not
  (let [{s1 :state o1 :out} (sfc/step st0 p0)
        x1 (sfc/observed (sfc/obs-of s1 o1))
        g (- (/ (:Y o1) (:Y st0)) 1.0)
        wrong (-> p0 (assoc :a1 0.6 :a2 0.0 :x 0.2 :mu 0.25 :socc 0.1 :n 0.0)
                  (assoc-in [:tax :hh] 0.3) (assoc-in [:v :nfc] 0.5) (assoc-in [:i-l :hh] 0.08)
                  (assoc-in [:other :row] 0.01) (assoc :div-share {:nfc 0.05}))
        nl (fn [o] (:net-lending o))
        gap (fn [o] (apply max (for [s sfc/sectors] (Math/abs (- ((nl o) s) ((nl o1) s))))))]
    (is (= (set sfc/blocks) (set (keys sfc/block-docs))))
    (is (< 1e-3 (gap (:out (sfc/step-given st0 wrong #{} x1 g)))) "the wrong model misses")
    (is (< (gap (:out (sfc/step-given st0 wrong (set sfc/blocks) x1 g))) 1e-9) "given everything, it is the data")
    (is (< (Math/abs (- (:Y o1) (:Y (:out (sfc/step-given st0 wrong (set sfc/blocks) x1 g))))) (* 1e-9 (:Y o1))))
    (testing "one block given sets that block only"
      (let [o (:out (sfc/step-given st0 wrong #{:consumption} x1 g))
            prop (fn [x] (/ (get-in x [:cons :hh]) (+ (get-in x [:cons :hh]) (get-in x [:inv :hh]) (get-in (sfc/book x) [:nl :hh]))))]
        (is (< (Math/abs (- (prop (:x o)) (prop x1))) 1e-9) "households consume the observed share of disposable income")
        (is (< 1e-3 (gap o)))))))

;; -- revised rules (the audit's replacements, selectable) ------------------------------

(def st1 (assoc st0 :pi-prev 0.17 :kappa 0.11
                :bsm-prev (into {} (for [[s m] bsm0] [s (into {} (for [[k v] m] [k (into {} (for [[sd x] v] [sd (* 0.9 x)]))]))]))))

(deftest wage-rule-reverts-to-its-norm
  (let [p (assoc p0 :wage-rule {:phi 0.25 :norm 0.5})
        {s :state} (sfc/step st1 p)]
    (is (< (Math/abs (- (:w s) (+ 0.6 (* 0.25 (- 0.5 0.6))))) 1e-12))
    (is (= 0.6 (:w (:state (sfc/step st1 (assoc p0 :wage-rule {:phi 0.0 :norm 0.5}))))) "phi 0 holds it")))

(deftest investment-in-differences-keeps-keens-slope
  (let [ir {:phi 0.2 :norm 0.12}
        p (assoc p0 :investment-rule ir)
        ye (* 100.0 1.04)
        slope (/ (- (keen/investment (:invest p0) (+ 0.18 1e-6)) (keen/investment (:invest p0) (- 0.18 1e-6))) 2e-6)
        want (+ 0.11 (* slope (- 0.18 0.17)) (* 0.2 (- 0.12 0.11)))
        {s :state o :out} (sfc/step st1 p)]
    (is (< (Math/abs (- (/ (get-in o [:inv :nfc]) ye) want)) 1e-6))
    (is (< (Math/abs (- (:kappa s) (/ (get-in o [:inv :nfc]) (:Y o)))) 1e-12) "the state carries the share")
    (is (= 0.18 (:pi-prev s)))
    (is (< (Math/abs (- (/ (get-in (sfc/step st0 p) [:out :inv :nfc]) ye) (keen/investment (:invest p0) 0.18))) 1e-12)
        "without last year's share, Keen's level")))

(deftest interest-passes-through-a-share-of-the-stock-change
  (let [ye 104.0
        il (fn [b] (+ (sfc/pos b :hh :money :l) (sfc/pos b :hh :debt :l)))
        paid (fn [theta] (get-in (sfc/property-income (:bsm st1) (assoc p0 :theta theta) ye (:bsm-prev st1)) [:int-paid :hh]))]
    (is (< (Math/abs (- (paid 1.0) (* 0.04 (il bsm0)))) 1e-12) "theta 1: the opening stock")
    (is (< (Math/abs (- (paid 0.0) (* 0.04 (il (:bsm-prev st1)) 1.04))) 1e-12) "theta 0: last year's stock grown at n")
    (is (< (Math/abs (- (paid 0.5) (* 0.5 (+ (paid 0.0) (paid 1.0))))) 1e-12))
    (testing "the identities hold under every rule"
      (let [p (assoc p0 :theta 0.3 :wage-rule {:phi 0.2 :norm 0.55} :investment-rule {:phi 0.1 :norm 0.12})
            steps (rest (run-steps st1 p 20))]
        (doseq [[s o] steps]
          (is (< (sfc/tfm-gap (:booked o)) (* 1e-10 (:Y o))))
          (is (< (sfc/bsm-gap (:bsm s)) (* 1e-10 (:Y o))))
          (is (< (:fof-gap o) (* 1e-10 (:Y o)))))))))

(deftest learning-the-revised-rules
  (let [panel {"SIM" (sim-panel 30)}
        p (sfc/learn panel {} "SIM" 2025 {:consumption {:phi 0.0} :wages {:phi 0.3} :investment {:phi 0.2} :theta 0.4})
        x (sfc/observed (get-in panel ["SIM" 2025]))
        prop (/ (get-in x [:cons :hh]) (+ (get-in x [:cons :hh]) (get-in x [:inv :hh]) (get-in (sfc/book x) [:nl :hh])))]
    (is (= 0.0 (:a2 p)))
    (is (< (Math/abs (- (:a1 p) prop)) 0.02) "the propensity, as last observed")
    (is (= 0.3 (get-in p [:wage-rule :phi])))
    (is (number? (get-in p [:wage-rule :norm])))
    (is (= 0.2 (get-in p [:investment-rule :phi])))
    (is (= 0.4 (:theta p)))
    (let [st (sfc/init panel {} "SIM" 2025 p)]
      (is (some? (:bsm-prev st)))
      (is (number? (:kappa st)))
      (is (number? (:pi-prev st))))))

(deftest modules-reproduce-the-step-under-the-revised-rules
  (let [p (assoc p0 :theta 0.3 :wage-rule {:phi 0.2 :norm 0.55} :investment-rule {:phi 0.1 :norm 0.12})
        ms (msfc/modules "SIM" st1 p)
        n 8
        direct (rest (run-steps st1 p n))
        r (eng/run ms {:from 2001 :to (+ 2000 n)})
        em (:emits r)]
    (is (empty? (eng/validate ms)))
    (doseq [[i [_ o]] (map-indexed vector direct)]
      (is (< (Math/abs (- (:Y o) (get-in em [:sfc-accounts "sfc/output" i]))) (* 1e-12 (:Y o))) (str "year " i)))))

;; -- phase B: several countries in one run, an analysed opening ------------------------------

(deftest countries-side-by-side-run-as-alone
  (let [st1 (assoc st0 :Y 50.0)
        alone-a (eng/run (msfc/modules "AAA" st0 p0) {:from 2001 :to 2006})
        alone-b (eng/run (msfc/modules "BBB" st1 p0) {:from 2003 :to 2006})
        ms (concat (msfc/modules "AAA" st0 p0 {:country-ids true})
                   (msfc/modules "BBB" st1 p0 {:country-ids true :span [2003 2006]}))
        r (eng/run ms {:from 2001 :to 2006})]
    (is (empty? (eng/validate ms)))
    (is (= #{:sfc-finance-AAA :sfc-firms-AAA :sfc-accounts-AAA :sfc-finance-BBB :sfc-firms-BBB :sfc-accounts-BBB} (set (map :module ms))))
    (is (every? #{"sfc-finance" "sfc-firms" "sfc-accounts"} (map :label ms)) "the table keeps the module's name")
    (is (= (get-in alone-a [:emits :sfc-accounts]) (get-in r [:emits :sfc-accounts-AAA])))
    (is (= (get-in alone-b [:emits :sfc-accounts "sfc/output"]) (drop 2 (get-in r [:emits :sfc-accounts-BBB "sfc/output"]))))
    (is (= [nil nil] (take 2 (get-in r [:emits :sfc-accounts-BBB "sfc/output"]))) "nothing before its span")))

(deftest an-analysed-opening-replaces-the-integrated-one
  (let [f (msfc/finance-module "SIM" st0 p0)
        {s :state} ((:step f) (:init f) {} {:sfc p0} 2001 1)
        {s2 :state} (sfc/step st0 p0)
        fof (:fof (:out (sfc/step st0 p0)))
        in {"sfc/fof" fof}
        plain ((:observe f) s in {:sfc p0} 2002)
        moved (sfc/apply-fof (:bsm s) fof)]
    (is (= (:bsm s2) (get plain "sfc/bsm")))
    (is (< (get plain "sfc/fof-gap") 1e-12))
    (let [lv ((:observe f) (assoc s :open moved :fof fof) in {:sfc p0} 2002)]
      (is (= moved (get lv "sfc/bsm")) "the analysed opening as given")
      (is (< (get lv "sfc/fof-gap") 1e-12) "with its own flow of funds the identity holds"))
    (testing "the step starts the year from it and forgets it"
      (let [{s3 :state e :emits} ((:step f) (assoc s :open (:bsm s2) :fof fof) in {:sfc p0} 2002 1)]
        (is (= (:bsm s2) (:bsm s3)))
        (is (not (contains? s3 :open)))
        (is (number? (get e "sfc/credit-hh")))))))

(deftest a-given-path-sets-the-fiscal-and-investment-shares
  ;; conditional forecasts (bnm-cvs): an observed government path and NFC
  ;; investment share given for some years, as step-given's :government and
  ;; :investment-nfc set them (shares of expected output)
  (let [given {2003 {:cg 0.25 :ig 0.05 :ben 0.2 :kappa 0.07}}
        ms (msfc/modules "SIM" st0 (assoc p0 :given given))
        r (eng/run ms {:from 2001 :to 2005})
        plain (eng/run (msfc/modules "SIM" st0 p0) {:from 2001 :to 2005})
        out (fn [r v i] (get-in r [:emits :sfc-accounts v i]))
        ye (* (out r "sfc/output" 1) (+ 1.0 (:n p0)))]
    (is (= (out plain "sfc/output" 1) (out r "sfc/output" 1)) "nothing before the given year")
    (is (< (Math/abs (- (get-in r [:emits :sfc-firms "sfc/nfc-investment" 2]) 0.07)) 1e-12))
    (is (< (Math/abs (- (get-in r [:states :sfc-accounts :cg]) (* 0.25 ye (Math/pow (+ 1.0 (:n p0)) 2)))) 1e-9)
        "consumption set to the share of expected output in 2003, grown at trend after")
    (is (not= (out plain "sfc/output" 2) (out r "sfc/output" 2)))
    (is (every? #(< % 1e-10) (keep identity (get-in r [:emits :sfc-accounts "sfc/tfm-gap"]))))))

;; -- rules estimated from data (bnm-4hv.14) --------------------------------------------------

(deftest least-squares-and-partial-pooling
  (let [rows (for [i (range 40) :let [x1 (Math/sin i) x2 (Math/cos (* 1.7 i))]] [x1 x2 (+ (* 0.3 x1) (* -0.2 x2))])
        f (sfc/ols rows)]
    (is (< (Math/abs (- 0.3 (get-in f [:b 0]))) 1e-9))
    (is (< (Math/abs (- -0.2 (get-in f [:b 1]))) 1e-9))
    (is (nil? (sfc/ols (take 2 rows))) "too few rows"))
  (testing "a country's own slope is pulled toward the pool, more when it has fewer rows"
    (let [mk (fn [b n off] (vec (for [i (range n) :let [x (Math/sin (+ off i))]] [x (+ (* b x) (* 0.01 (Math/cos (* 3.1 (+ off i)))))])))
          pp (sfc/partial-pool {"A" (mk 0.2 30 0) "B" (mk 0.4 30 1) "C" (mk 0.3 30 2) "D" (mk 0.6 4 3)})]
      (is (< 0.2 (first (:pooled pp)) 0.6))
      (is (< (Math/abs (- 0.2 (first (get-in pp [:by "A"])))) 0.05) "much data of its own: near its own")
      (is (pos? (first (:tau2 pp)))))))

(deftest error-correction-rows-read-nothing-after-the-origin
  (let [panel {"SIM" (sim-panel 30)}
        cut {"SIM" (into {} (filter #(<= (key %) 2015)) (get panel "SIM"))}]
    (doseq [k [:fiscal :investment :consumption]]
      (is (= (sfc/ec-rows panel 2015 (sfc/rule-specs k)) (sfc/ec-rows cut 2015 (sfc/rule-specs k))) (str k)))
    (let [rows (get (sfc/ec-rows panel 2015 (sfc/rule-specs :fiscal)) "SIM")]
      (is (seq rows))
      (is (every? #(= 3 (count %)) rows) "the spending gap, the balance gap, the change"))))

(deftest learning-the-estimated-rules
  (let [panel {"SIM" (sim-panel 30)}
        p (sfc/learn panel {} "SIM" 2025 sfc/estimated)
        x (sfc/observed (get-in panel ["SIM" 2025]))
        yd (+ (get-in x [:cons :hh]) (get-in x [:inv :hh]) (get-in (sfc/book x) [:nl :hh]))]
    (is (< (Math/abs (- (:a1 p) (/ (get-in x [:cons :hh]) yd))) 1e-3) "the propensity as last observed")
    (is (= #{:phi :spend-phi :norm :spend-norm} (set (keys (:fiscal-rule p)))))
    (is (every? number? (vals (:fiscal-rule p))))
    (is (every? number? (map (:investment-rule p) [:phi :b-pi :b-ds :norm :pi-norm :ds-norm])))
    (is (= 0.0 (get-in p [:investment-rule :slope])))
    (is (< (Math/abs (- (get-in p [:int-share :paid :hh]) (/ (get-in x [:int-paid :hh]) (:Y x)))) 0.01) "interest's share of output")
    (is (number? (get-in p [:div-recv-share :hh])))
    (is (= 0.0 (get-in p [:wage-rule :phi])) "the wage share held")
    (testing "nothing after the origin is read"
      (let [cut {"SIM" (into {} (filter #(<= (key %) 2015)) (get panel "SIM"))}]
        (is (= (sfc/learn panel {} "SIM" 2015 sfc/estimated) (sfc/learn cut {} "SIM" 2015 sfc/estimated)))))))

(def p-est
  (assoc p0 :int-share {:paid {:hh 0.03 :nfc 0.035 :fin 0.01 :gov 0.02 :row 0.008} :recv {:hh 0.012 :nfc 0.004 :gov 0.002 :row 0.01}}
         :div-recv-share {:hh 0.02 :nfc 0.01 :gov 0.002 :row 0.01}
         :fiscal-rule {:phi 0.25 :spend-phi 0.1 :norm -0.02 :spend-norm 0.33}
         :investment-rule {:phi 0.2 :slope 0.0 :norm 0.12 :b-pi 0.1 :b-ds -0.2 :pi-norm 0.17 :ds-norm 0.03}))

(def st-est (assoc st1 :nl-gov -0.05 :ds 0.035))

(deftest interest-and-dividends-hold-their-shares
  (let [ye 104.0 pinc (sfc/property-income bsm0 p-est ye bsm0 2001)]
    (is (< (Math/abs (- (get-in pinc [:int-paid :nfc]) (* 0.035 ye))) 1e-12))
    (is (< (Math/abs (- (get-in pinc [:int-recv :hh]) (* 0.012 ye))) 1e-12))
    (is (< (Math/abs (- (get-in pinc [:div-recv :row]) (* 0.01 ye))) 1e-12))))

(deftest the-fiscal-rule-closes-part-of-the-balance-gap
  (let [sp (/ (+ 18.0 3.0 12.0) 100.0)
        {s :state} (sfc/step st-est p-est)
        want (+ 1.0 (/ (+ (* 0.25 (- -0.05 -0.02)) (* 0.1 (- 0.33 sp))) sp))]
    (is (< (Math/abs (- (:cg s) (* 18.0 1.04 want))) 1e-9) "a deficit beyond its norm cuts spending")
    (is (number? (:nl-gov s)))))

(deftest investment-corrects-toward-its-norm-with-profits-and-debt-service
  (let [inv (sfc/investment p-est 104.0 0.18 st-est)]
    (is (< (Math/abs (- (:nfc inv) (* 104.0 (+ 0.11 (* 0.2 (- 0.12 0.11)) (* 0.1 (- 0.18 0.17)) (* -0.2 (- 0.035 0.03))))))
           1e-9))))

(deftest modules-reproduce-the-step-under-the-estimated-rules
  (let [ms (msfc/modules "SIM" st-est p-est)
        n 8
        direct (rest (run-steps st-est p-est n))
        r (eng/run ms {:from 2001 :to (+ 2000 n)})
        em (:emits r)]
    (is (empty? (eng/validate ms)))
    (doseq [[i [_ o]] (map-indexed vector direct)]
      (is (< (Math/abs (- (:Y o) (get-in em [:sfc-accounts "sfc/output" i]))) (* 1e-12 (:Y o))) (str "year " i))
      (is (< (Math/abs (- (get-in o [:net-lending :gov]) (get-in em [:sfc-accounts "sfc/net-lending-gov" i]))) 1e-12)))))

(deftest given-blocks-reproduce-the-observed-year-under-the-estimated-rules
  (let [{s1 :state o1 :out} (sfc/step st0 p0)
        x1 (sfc/observed (sfc/obs-of s1 o1))
        g (- (/ (:Y o1) (:Y st0)) 1.0)
        o (:out (sfc/step-given st-est p-est (set sfc/blocks) x1 g))]
    (is (< (apply max (for [s sfc/sectors] (Math/abs (- (get-in o [:net-lending s]) (get-in o1 [:net-lending s]))))) 1e-9)
        "every block given, the rules' own terms switched off")))

;; -- the estimated rules' gaps, norms and slopes (review of bnm-4hv.14) ---------------------

(defn- hl-mean
  "The weighted mean (half-life `hl`, anchored at `at`) of [[year value]]."
  [pairs at hl]
  (let [ws (map #(Math/pow 0.5 (/ (- (double at) (double (first %))) (double hl))) pairs)]
    (/ (reduce + (map * ws (map second pairs))) (reduce + ws))))

(defn- sim-panel-of
  "sim-panel for parameters `p` from state `st`."
  [st p n]
  (let [steps (rest (run-steps st p n))]
    (into {(:year st) (sfc/obs-of st nil)} (for [[s o] steps] [(:year s) (sfc/obs-of s o)]))))

(deftest error-correction-rows-recover-the-rule-the-step-runs
  ;; a series made by dx_y = b1 (norm_x - x_{y-1}) + b2 (z_{y-1} - norm_z),
  ;; each norm the 10-year weighted mean of the years to y - 1: the
  ;; convention bnm.sfc/investment and the fiscal rule step with
  (let [base (sim-panel 30)
        years (vec (sort (filter #(:flows-ok (base %)) (keys base))))
        b1 0.3 b2 0.5
        zs (into {} (map-indexed (fn [i y] [y (* 0.02 (Math/sin (* 1.3 i)))]) years))
        xs (reduce (fn [acc y]
                     (let [prior (vec (sort (filter #(< % y) (keys acc))))]
                       (if (< (count prior) 3)
                         (assoc acc y (+ 0.30 (* 0.01 (count prior))))
                         (let [x0 (acc (dec y))
                               nx (hl-mean (for [q prior] [q (acc q)]) (dec y) 10.0)
                               nz (hl-mean (for [q prior] [q (zs q)]) (dec y) 10.0)]
                           (assoc acc y (+ x0 (* b1 (- nx x0)) (* b2 (- (zs (dec y)) nz))))))))
                   {} years)
        panel {"SIM" (into {} (for [[y ob] base] [y (assoc ob :sx (xs y) :sz (zs y))]))}
        spec {:x (fn [_ ob] (:sx ob)) :zs [(fn [_ ob] (:sz ob))]}
        rows (get (sfc/ec-rows panel 2025 spec) "SIM")
        y0 (nth years 3)
        prior (subvec years 0 3)]
    (is (= [(- (hl-mean (for [q prior] [q (xs q)]) (dec y0) 10.0) (xs (dec y0)))
            (- (zs (dec y0)) (hl-mean (for [q prior] [q (zs q)]) (dec y0) 10.0))
            (- (xs y0) (xs (dec y0)))]
           (first rows))
        "the first row: the own gap (norm less x), the other gap (z less norm), the change")
    (let [{[c1 c2] :pooled} (sfc/partial-pool {"SIM" rows})]
      (is (< (Math/abs (- c1 b1)) 1e-9) (str "own gap " c1))
      (is (< (Math/abs (- c2 b2)) 1e-9) (str "other gap " c2)))))

(deftest learn-takes-the-pooled-slopes-in-order-and-the-countrys-own-norms
  (let [two (assoc p0 :a1 0.8 :other {:hh 0.02 :nfc 0.0 :fin 0.0 :row -0.004} :n 0.03)
        panel {"SIM" (sim-panel 30) "TWO" (sim-panel-of (assoc st0 :cg 20.0 :ben 10.0) two 30)}
        o 2025
        p (sfc/learn panel {} "SIM" o sfc/estimated)
        fis (sfc/estimate-rule panel o :fiscal)
        inv (sfc/estimate-rule panel o :investment)
        xs (for [[y ob] (get panel "SIM") :when (and (:flows-ok ob) (<= y o))] [y (sfc/observed ob)])
        norm (fn [f] (hl-mean (for [[y x] xs] [y (f x)]) o 10.0))
        close? (fn [a b] (< (Math/abs (- (double a) (double b))) 1e-12))]
    (testing "fiscal: the own gap's slope is :spend-phi, the balance's :phi"
      (is (close? (first (:pooled fis)) (get-in p [:fiscal-rule :spend-phi])))
      (is (close? (second (:pooled fis)) (get-in p [:fiscal-rule :phi])))
      (is (not (close? (first (:pooled fis)) (second (:pooled fis))))))
    (testing "investment: own gap, profit share, debt service"
      (is (= (mapv double (:pooled inv)) (mapv #(double (get-in p [:investment-rule %])) [:phi :b-pi :b-ds]))))
    (testing "the norms are the country's own, from the booked accounts, half-life 10"
      (is (close? (norm #(/ (get-in (sfc/book %) [:nl :gov]) (:Y %))) (get-in p [:fiscal-rule :norm])))
      (is (close? (norm #(/ (+ (get-in % [:cons :gov]) (get-in % [:inv :gov]) (:ben %)) (:Y %))) (get-in p [:fiscal-rule :spend-norm])))
      (is (close? (norm #(/ (get-in % [:inv :nfc]) (:Y %))) (get-in p [:investment-rule :norm])))
      (is (close? (norm sfc/profit-share) (get-in p [:investment-rule :pi-norm])))
      (is (close? (norm #(/ (get-in % [:int-paid :nfc]) (:Y %))) (get-in p [:investment-rule :ds-norm])))
      (is (not (close? (get-in p [:fiscal-rule :spend-norm])
                       (get-in (sfc/learn panel {} "TWO" o sfc/estimated) [:fiscal-rule :spend-norm])))))
    (testing "without :pool :complete, the country's shrunk slopes"
      (let [q (sfc/learn panel {} "SIM" o (assoc sfc/estimated :fiscal {:estimate true}))]
        (is (= (vec (get-in fis [:by "SIM"])) [(get-in q [:fiscal-rule :spend-phi]) (get-in q [:fiscal-rule :phi])]))))
    (testing "the pooled refit reads no other country's years after the origin"
      (let [cut (update panel "TWO" #(into {} (filter (fn [[y _]] (<= y 2015))) %))]
        (is (= (sfc/learn panel {} "SIM" 2015 sfc/estimated) (sfc/learn cut {} "SIM" 2015 sfc/estimated)))))))

(deftest the-fiscal-gap-starts-from-the-booked-balance
  ;; the rule's norm and slopes are on the booked balance (government's
  ;; net lending closing the matrix), and the model steps it; the start
  ;; must be the same measure, not the data's B9 with its discrepancy
  (let [panel {"SIM" (update-in (sim-panel 30) [2015 :f :gov :nl] + 2.0)}
        p (sfc/learn panel {} "SIM" 2015 sfc/estimated)
        st (sfc/init panel {} "SIM" 2015 p)
        x (sfc/observed (get-in panel ["SIM" 2015]))]
    (is (< (Math/abs (- (:nl-gov st) (/ (get-in (sfc/book x) [:nl :gov]) (:Y x)))) 1e-12))))

;; -- policy-rate pass-through and the bank margin (bead bnm-4hv.15) -----------------------

(def rr0
  {:from 2001 :r0 0.03 :policy {}
   :i0 {:hh 0.05 :nfc 0.045 :gov 0.04} :spread {:hh 0.025 :nfc 0.02 :gov 0.01}
   :a {:hh 0.3 :nfc 0.3 :gov 0.1} :lambda {:hh 0.3 :nfc 0.25 :gov 0.15}
   :nim0 0.01 :nim-norm 0.012 :nim-phi 0.2 :nim-beta 0.1})

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-12))

(deftest lending-rates-pass-the-policy-rate-through
  (testing "the policy rate held: each implicit rate closes lambda of its gap to policy plus spread a year"
    (let [y1 (sfc/rates-at rr0 2001) y2 (sfc/rates-at rr0 2002)
          i1 (+ 0.05 (* 0.3 (- (+ 0.03 0.025) 0.05)))]
      (is (close? i1 (get-in y1 [:paid :hh])))
      (is (close? (+ i1 (* 0.3 (- 0.055 i1))) (get-in y2 [:paid :hh])))
      (is (close? (+ 0.04 (* 0.15 (- 0.04 0.04))) (get-in y1 [:paid :gov])) "at its target, it stays")
      (is (close? 0.03 (:policy y2)))
      (is (= (sfc/rates-at rr0 2000) {:policy 0.03 :paid (:i0 rr0) :nim 0.01}) "the origin's own rates")))
  (testing "a policy move: a of it at once, the rest through the gap"
    (let [rr (assoc rr0 :policy {2001 0.04 2002 0.04 2003 0.04})
          i1 (+ 0.05 (* 0.3 0.01) (* 0.3 (- (+ 0.03 0.025) 0.05)))
          i2 (+ i1 (* 0.3 (- (+ 0.04 0.025) i1)))]
      (is (close? i1 (get-in (sfc/rates-at rr 2001) [:paid :hh])))
      (is (close? i2 (get-in (sfc/rates-at rr 2002) [:paid :hh])))
      (is (close? 0.04 (:policy (sfc/rates-at rr 2010))) "the last policy rate given is held")))
  (testing "the margin on interest-bearing assets reverts to its norm and moves with the policy rate"
    (is (close? (+ 0.01 (* 0.2 0.002)) (:nim (sfc/rates-at rr0 2001))))
    (is (close? (+ 0.01 (* 0.2 0.002) (* 0.1 0.01)) (:nim (sfc/rates-at (assoc rr0 :policy {2001 0.04}) 2001)))))
  (testing "no policy rate: lending rates held, the margin reverting"
    (let [rr (assoc rr0 :r0 nil)]
      (is (= (:i0 rr0) (:paid (sfc/rates-at rr 2004))))
      (is (close? (+ 0.01 (* 0.2 0.002)) (:nim (sfc/rates-at rr 2001)))))))

(def p-rates (-> p-est (dissoc :int-share) (assoc :rates rr0)))

(defn- ib [b s side] (+ (sfc/pos b s :money side) (sfc/pos b s :debt side)))

(deftest interest-is-rate-times-stock-and-the-margin-its-own-rule
  (let [ye 104.0
        pinc (sfc/property-income bsm0 p-rates ye nil 2001)
        rt (sfc/rates-at rr0 2001)
        x (assoc pinc :Y 100.0)]
    (doseq [s [:hh :nfc :gov]]
      (is (close? (* (get-in rt [:paid s]) (ib bsm0 s :l)) (get-in pinc [:int-paid s])) (str "paid " s)))
    (is (close? (* 0.03 (ib bsm0 :row :l)) (get-in pinc [:int-paid :row])) "the rest of the world at its implicit rate")
    (is (close? (* 0.03 (ib bsm0 :row :a)) (get-in pinc [:int-recv :row])))
    (is (close? (* (:nim rt) (ib bsm0 :fin :a)) (sfc/margin-of x)) "the financial sector's net interest: the margin times its assets")
    (is (close? (sfc/margin-of x)
                (get-in (sfc/book (merge {:Y 100.0 :va {} :comp {} :ptax {} :cons {} :inv {} :other {}} pinc)) [:rows :interest :fin]))
        "margin-of is the financial sector's net interest in the books")
    (is (close? (/ (get-in pinc [:int-recv :hh]) (get-in pinc [:int-recv :nfc]))
                (/ (* 0.02 (ib bsm0 :hh :a)) (* 0.02 (ib bsm0 :nfc :a))))
        "deposit rates move together: receipts in proportion to rate times stock")
    (testing "the identities hold every step"
      (doseq [[s o] (rest (run-steps st-est p-rates 20))]
        (is (< (sfc/tfm-gap (:booked o)) (* 1e-10 (:Y o))))
        (is (< (sfc/bsm-gap (:bsm s)) (* 1e-10 (:Y o))))
        (is (< (:fof-gap o) (* 1e-10 (:Y o))))))))

(deftest a-policy-shock-raises-the-interest-bill
  (let [shocked (sfc/policy-shock p-rates 2001 0.01)
        base (rest (run-steps st-est p-rates 10))
        shock (rest (run-steps st-est shocked 10))
        gov (fn [[_ o]] (get-in o [:x :int-paid :gov]))]
    (is (close? 0.04 (get-in shocked [:rates :policy 2001])))
    (is (close? 0.04 (get-in shocked [:rates :policy 2030])) "held from then on")
    (is (< (Math/abs (- (- (gov (first shock)) (gov (first base))) (* 0.1 0.01 (ib bsm0 :gov :l)))) 1e-12)
        "the first year: a of the move on the opening stock")
    (is (every? true? (map #(> (gov %1) (gov %2)) shock base)))
    (is (= base (rest (run-steps st-est (sfc/policy-shock p-rates 2001 0.0) 10))) "no move, no change")))

(deftest a-spending-shock-is-debt-financed-and-leaves-the-base
  (let [p (assoc p-rates :spend-shock {2001 0.01})
        [[s1 o1] [s0 o0]] [(second (run-steps st-est p 1)) (second (run-steps st-est p-rates 1))]
        ye (* 100.0 1.04)]
    (is (close? (+ (get-in o0 [:x :cons :gov]) (* 0.01 ye)) (get-in o1 [:x :cons :gov])))
    (is (close? (:cg s0) (:cg s1)) "next year's spending grows from the base, not the shock")
    (is (< (get-in o1 [:booked :nl :gov]) (get-in o0 [:booked :nl :gov])))
    (doseq [[s o] (rest (run-steps st-est p 10))]
      (is (< (max (sfc/tfm-gap (:booked o)) (:fof-gap o) (sfc/bsm-gap (:bsm s))) (* 1e-10 (:Y o)))))))

(deftest an-investment-shock-is-booked-and-leaves-the-base
  ;; bnm-4hv.19: a scenario's NFC investment (:invest-shock {year share of
  ;; expected output}), on top of the rule, financed through the flow of
  ;; funds, and not in the share the rule moves from next year
  (let [p (assoc p-rates :invest-shock {2001 0.01})
        [[s1 o1] [s0 o0]] [(second (run-steps st-est p 1)) (second (run-steps st-est p-rates 1))]
        ye (* 100.0 1.04)]
    (is (close? (+ (get-in o0 [:inv :nfc]) (* 0.01 ye)) (get-in o1 [:inv :nfc])))
    (is (close? (get-in o0 [:x :inv :nfc]) (- (get-in o1 [:x :inv :nfc]) (* 0.01 ye))) "booked")
    (is (close? (* (:kappa s0) (:Y o0)) (* (:kappa s1) (:Y o1))) "the rule's next share grows from the base, not the shock")
    (is (< (get-in o1 [:booked :nl :nfc]) (get-in o0 [:booked :nl :nfc])))
    (testing "new credit follows the debt target, not investment: NFCs' net worth takes it"
      (is (close? (sfc/pos (:bsm s0) :nfc :debt :l) (sfc/pos (:bsm s1) :nfc :debt :l)))
      (is (close? (- (sfc/nfw (:bsm s1) :nfc) (sfc/nfw (:bsm s0) :nfc))
                  (- (get-in o1 [:fof :nl :nfc]) (get-in o0 [:fof :nl :nfc])))))
    (doseq [[s o] (rest (run-steps st-est p 10))]
      (is (< (max (sfc/tfm-gap (:booked o)) (:fof-gap o) (sfc/bsm-gap (:bsm s))) (* 1e-10 (:Y o)))))))

(deftest a-policy-path-sets-each-years-rate-and-holds-the-last
  (let [p (sfc/policy-path p-rates {2001 0.01 2003 0.005 2005 0.0})
        pol (fn [y] (:policy (sfc/rates-at (:rates p) y)))]
    (is (close? 0.04 (pol 2001)))
    (is (close? 0.04 (pol 2002)) "held between entries")
    (is (close? 0.035 (pol 2004)))
    (is (close? 0.03 (pol 2040)) "the last held")
    (testing "pass-through applies each year's move from the path"
      (let [paid (fn [y] (get-in (sfc/rates-at (:rates p) y) [:paid :hh]))
            i1 (+ 0.05 (* 0.3 0.01) (* 0.3 (- (+ 0.03 0.025) 0.05)))
            i2 (+ i1 (* 0.3 (- (+ 0.04 0.025) i1)))
            i3 (+ i2 (* 0.3 -0.005) (* 0.3 (- (+ 0.04 0.025) i2)))
            i4 (+ i3 (* 0.3 (- (+ 0.035 0.025) i3)))
            i5 (+ i4 (* 0.3 -0.005) (* 0.3 (- (+ 0.035 0.025) i4)))]
        (is (close? i1 (paid 2001)))
        (is (close? i2 (paid 2002)))
        (is (close? i3 (paid 2003)) "a cut: a of it at once, from last year's rate")
        (is (close? i5 (paid 2005)))))
    (is (= (sfc/policy-shock p-rates 2001 0.01) (sfc/policy-path p-rates {2001 0.01})) "policy-shock is a one-step path")
    (is (= p0 (sfc/policy-path p0 {2001 0.01})) "no policy rate, no change")))

(deftest learns-default-is-still-keens-rules
  ;; bnm-4hv.19: the scenario entry point passes bnm.sfc/rates; learn with
  ;; no options keeps Keen's Phillips curve and investment function and none
  ;; of the estimated rules or the rate channel
  (let [panel {"SIM" (update-vals (sim-panel 24) #(assoc % :policy 0.03))}
        kn {"SIM" {2017 {"keen/state-year" 2015.0 "keen/phi-slope" 1.0 "keen/l-star" 0.94 "keen/alpha" 0.02 "keen/lambda" 0.94}}}
        p (sfc/learn panel kn "SIM" 2015 {})]
    (is (map? (:phillips p)))
    (is (map? (:invest p)))
    (doseq [k [:rates :fiscal-rule :investment-rule :int-share :div-recv-share :wage-rule :consumption-rule :other-rule :theta]]
      (is (nil? (get p k)) (str k)))
    (is (map? (:rates (sfc/learn panel {} "SIM" 2015 sfc/rates))))))

(deftest modules-reproduce-the-step-under-the-rates
  (let [p (sfc/policy-shock p-rates 2002 0.01)
        ms (msfc/modules "SIM" st-est p)
        n 8
        direct (rest (run-steps st-est p n))
        em (:emits (eng/run ms {:from 2001 :to (+ 2000 n)}))]
    (doseq [[i [_ o]] (map-indexed vector direct)]
      (is (< (Math/abs (- (get-in o [:net-lending :gov]) (get-in em [:sfc-accounts "sfc/net-lending-gov" i]))) 1e-12) (str "year " i)))))

(defn- with-policy-series [panel rs] (into {} (for [[y ob] panel] [y (if-let [r (rs y)] (assoc ob :policy r) ob)])))

(deftest rate-rows-recover-the-pass-through
  ;; implicit rates made by di_t = a dr_t + lambda (r_{t-1} + norm_{t-1} - i_{t-1}),
  ;; the spread's norm the 10-year weighted mean of i - r over the years to t - 1
  (let [base (sim-panel 30)
        years (vec (sort (keys base)))
        rs (into {} (map-indexed (fn [i y] [y (+ 0.03 (* 0.01 (Math/sin (* 0.9 i))))]) years))
        a 0.4 lam 0.25
        is* (reduce (fn [acc y]
                      (let [prior (vec (sort (filter #(< % y) (keys acc))))]
                        (if (< (count prior) 3)
                          (assoc acc y (+ (rs y) 0.02 (* 0.001 (count prior))))
                          (let [i0 (acc (dec y))
                                nd (hl-mean (for [q prior] [q (- (acc q) (rs q))]) (dec y) 10.0)]
                            (assoc acc y (+ i0 (* a (- (rs y) (rs (dec y)))) (* lam (- (+ (rs (dec y)) nd) i0))))))))
                    {} (rest years))
        panel {"SIM" (with-policy-series
                       (into {} (for [[y ob] base]
                                  [y (if-let [i (is* y)]
                                       (assoc-in ob [:f :hh :int_paid] (* i (ib (:bsm (sfc/close-init (:b (base (dec y))))) :hh :l)))
                                       ob)]))
                       rs)}
        rows (get (sfc/rate-rows panel 2025 10.0) ["SIM" :hh])
        y0 (nth years 4) prior (subvec years 1 4)]
    (is (= 3 (count (first rows))) "the policy change, the gap, the rate's change")
    (is (< (Math/abs (- (nth (first rows) 1) (- (+ (rs (dec y0)) (hl-mean (for [q prior] [q (- (is* q) (rs q))]) (dec y0) 10.0)) (is* (dec y0))))) 1e-12))
    (let [{[ca cl] :b} (sfc/ols rows)]
      (is (< (Math/abs (- ca a)) 1e-9) (str "a " ca))
      (is (< (Math/abs (- cl lam)) 1e-9) (str "lambda " cl)))
    (let [cut {"SIM" (into {} (filter #(<= (key %) 2015)) (get panel "SIM"))}]
      (is (= (sfc/rate-rows panel 2015 10.0) (sfc/rate-rows cut 2015 10.0)) "nothing after the origin"))))

(deftest margin-rows-recover-the-banks-rule
  (let [base (sim-panel 30)
        years (vec (sort (keys base)))
        rs (into {} (map-indexed (fn [i y] [y (+ 0.03 (* 0.01 (Math/sin (* 0.7 i))))]) years))
        phi 0.3 beta 0.2
        ms (reduce (fn [acc y]
                     (let [prior (vec (sort (filter #(< % y) (keys acc))))]
                       (if (< (count prior) 3)
                         (assoc acc y (+ 0.01 (* 0.0005 (count prior))))
                         (let [m0 (acc (dec y)) nm (hl-mean (for [q prior] [q (acc q)]) (dec y) 10.0)]
                           (assoc acc y (+ m0 (* phi (- nm m0)) (* beta (- (rs y) (rs (dec y))))))))))
                   {} (rest years))
        set-m (fn [y ob]
                (if-let [m (ms y)]
                  (let [cur (sfc/margin-of (sfc/observed ob))
                        want (* m (ib (:bsm (sfc/close-init (:b (base (dec y))))) :fin :a))]
                    (update-in ob [:f :row :int_paid] + (- want cur)))
                  ob))
        panel {"SIM" (with-policy-series (into {} (for [[y ob] base] [y (set-m y ob)])) rs)}
        rows (get (sfc/nim-rows panel 2025 10.0) "SIM")]
    (is (= 3 (count (first rows))) "the margin's gap to its norm, the policy change, the margin's change")
    (let [{[cp cb] :b} (sfc/ols rows)]
      (is (< (Math/abs (- cp phi)) 1e-9) (str "phi " cp))
      (is (< (Math/abs (- cb beta)) 1e-9) (str "beta " cb)))
    (let [cut {"SIM" (into {} (filter #(<= (key %) 2015)) (get panel "SIM"))}]
      (is (= (sfc/nim-rows panel 2015 10.0) (sfc/nim-rows cut 2015 10.0)) "nothing after the origin"))))

(deftest learning-the-rates
  (let [base (sim-panel 30)
        rs (into {} (map-indexed (fn [i y] [y (+ 0.03 (* 0.01 (Math/sin (* 0.9 i))))]) (sort (keys base))))
        panel {"SIM" (with-policy-series base rs)}
        o 2025
        p (sfc/learn panel {} "SIM" o (assoc sfc/rates :rates {:estimate true}))
        rr (:rates p)
        rate (fn [y s] (let [st (ib (:bsm (sfc/close-init (get-in panel ["SIM" (dec y) :b]))) s :l)]
                         (when (pos? st) (/ (get-in panel ["SIM" y :f s :int_paid]) st))))]
    (is (nil? (:int-share p)) "interest is no longer a held share")
    (is (map? (:div-recv-share p)) "dividends still are")
    (is (map? (:fiscal-rule p)))
    (is (= (inc o) (:from rr)))
    (is (close? (rs o) (:r0 rr)) "the origin's policy rate")
    (doseq [s [:hh :nfc :gov]]
      (is (close? (get-in p [:i-l s]) (get-in rr [:i0 s])) (str "the origin's rate " s))
      (let [ok (for [y (range 2001 (inc o)) :let [i (rate y s)] :when (and i (< 0.0 i 0.3))] [y (- i (rs y))])]
        (is (close? (if (seq ok) (hl-mean ok o 10.0) (- (get-in p [:i-l s]) (rs o))) (get-in rr [:spread s]))
            (str "spread " s ": rates on a positive stock within (0, 0.3); none, the origin's rate less its policy rate")))
      (let [{[a lam] :b} (sfc/ols (apply concat (for [[[_ s'] v] (sfc/rate-rows panel o 10.0) :when (= s s')] v)))]
        (is (close? a (get-in rr [:a s])))
        (is (close? lam (get-in rr [:lambda s])))))
    (is (every? number? (map rr [:nim0 :nim-norm :nim-phi :nim-beta])))
    (testing "nothing after the origin is read"
      (let [cut {"SIM" (into {} (filter #(<= (key %) 2015)) (get panel "SIM"))}]
        (is (= (sfc/learn panel {} "SIM" 2015 sfc/rates) (sfc/learn cut {} "SIM" 2015 sfc/rates)))))
    (testing "the selected form"
      (is (= {:estimate true :margin-base :output :anchor true :recv :share :row :share} (:rates sfc/rates)))
      (let [q (:rates (sfc/learn panel {} "SIM" o sfc/rates))]
        (is (= [:output :share :share] [(:margin-base q) (:recv q) (:row q)]))
        (is (= (:lambda rr) (:lambda q)) "the same pass-through")))))

(deftest the-margin-can-be-a-share-of-output
  (let [rr (assoc rr0 :margin-base :output)
        pinc (sfc/property-income bsm0 (assoc p-rates :rates rr) 104.0 nil 2001)]
    (is (close? (* (:nim (sfc/rates-at rr 2001)) 104.0) (sfc/margin-of pinc)) "the margin times expected output"))
  (let [base (sim-panel 30)
        panel {"SIM" (with-policy-series base (into {} (for [y (keys base)] [y 0.03])))}
        rows (get (sfc/nim-rows panel 2025 10.0 :output) "SIM")
        m (fn [y] (let [x (sfc/observed (get base y))] (/ (sfc/margin-of x) (:Y x))))
        y0 2004]
    (is (seq rows))
    (is (close? (- (m y0) (m (dec y0))) (last (first rows))) "the change in net interest over output, the first year with three before it for the norm")))

(deftest interest-on-last-years-stock-under-theta
  ;; :stock :mix: the lending and deposit bases as property-income's mix
  ;; (theta 0: last year's opening stock grown at n)
  (let [rr (assoc rr0 :stock :mix)
        p (assoc p-rates :rates rr :theta 0.0)
        pinc (sfc/property-income (:bsm st1) p 104.0 (:bsm-prev st1) 2001)
        rt (sfc/rates-at rr 2001)]
    (is (close? (* (get-in rt [:paid :gov]) (ib (:bsm-prev st1) :gov :l) 1.04) (get-in pinc [:int-paid :gov])))
    (is (close? (/ (get-in pinc [:int-recv :hh]) (get-in pinc [:int-recv :nfc]))
                (/ (ib (:bsm-prev st1) :hh :a) (ib (:bsm-prev st1) :nfc :a))))))

(deftest learning-the-rates-without-pass-through
  (let [base (sim-panel 30)
        panel {"SIM" (with-policy-series base (into {} (for [y (keys base)] [y 0.03])))}
        rr (:rates (sfc/learn panel {} "SIM" 2025 (assoc-in sfc/rates [:rates :pass-through] false)))]
    (is (= {:hh 0.0 :nfc 0.0 :gov 0.0} (:a rr) (:lambda rr)) "lending rates held at the origin's")))

(deftest anchored-rates-start-from-the-held-shares
  ;; :anchor: each rate is the effective one on the origin's stock that
  ;; gives the held share of output (estimated's first year); the
  ;; pass-through moves it from there (the spread shifted alike)
  (let [base (sim-panel 30)
        panel {"SIM" (with-policy-series base (into {} (for [y (keys base)] [y 0.03])))}
        o 2020
        opts (fn [ro] (update sfc/rates :rates merge ro))
        p (sfc/learn panel {} "SIM" o (opts {:anchor true :pass-through false}))
        held (sfc/learn panel {} "SIM" o sfc/estimated)
        free (sfc/learn panel {} "SIM" o (opts {:pass-through false}))
        st (sfc/init panel {} "SIM" o p)
        ye (* (:Y st) (+ 1.0 (:n p)))
        pinc (sfc/property-income (:bsm st) p ye (:bsm-prev st) (inc o))]
    (doseq [s [:hh :nfc]]
      (is (close? (* (get-in held [:int-share :paid s]) ye) (get-in pinc [:int-paid s])) (str "paid " s)))
    (is (close? (- (get-in p [:rates :spread :hh]) (get-in p [:rates :i0 :hh]))
                (- (get-in free [:rates :spread :hh]) (get-in free [:rates :i0 :hh])))
        "the gap to policy plus spread is the unanchored one")))

(deftest deposit-receipts-can-be-split-by-held-shares
  ;; :recv :share: the domestic receipts the margin leaves split by each
  ;; receiver's held share of output; :row :share: the rest of the world's
  ;; interest as held shares of output
  (let [rr (assoc rr0 :recv :share :row :share :margin-base :output
                  :recv-share {:hh 0.012 :nfc 0.004 :gov 0.002} :row-share {:paid 0.008 :recv 0.01})
        pinc (sfc/property-income bsm0 (assoc p-rates :rates rr) 104.0 nil 2001)]
    (is (close? (/ 0.012 0.004) (/ (get-in pinc [:int-recv :hh]) (get-in pinc [:int-recv :nfc]))))
    (is (close? (* 0.008 104.0) (get-in pinc [:int-paid :row])))
    (is (close? (* 0.01 104.0) (get-in pinc [:int-recv :row])))
    (is (close? (* (:nim (sfc/rates-at rr 2001)) 104.0) (sfc/margin-of pinc)))))

(deftest dividends-received-stay-bounded-when-holdings-turn-negative
  ;; bnm-4hv.17: under Keen's rules households' and firms' equity assets,
  ;; shares of total assets, turn negative once their net worth does
  ;; (Lithuania from 2024, about 2045). Split by yield-weighted holdings
  ;; over their signed total, the total neared zero and households received
  ;; -4.9 of output in dividends. Over the total of their absolute values a
  ;; negative holding still pays (a short position does), and no share
  ;; exceeds what is paid; with no negative holding nothing changes.
  (let [p {:div-share {:nfc 0.05 :fin 0.01} :q-a {:hh 0.30 :nfc 0.03 :fin 0.03 :gov 0.03 :row 0.03}}
        bsm (-> bsm0 (assoc-in [:hh :equity :a] -10.0) (assoc-in [:nfc :equity :a] -29.0))
        w {:hh -3.0 :nfc -0.87 :fin (* 0.03 (get-in bsm [:fin :equity :a])) :gov 0.3 :row 0.6}
        {:keys [div-paid div-recv]} (sfc/property-income bsm p 100.0)
        paid (reduce + (vals div-paid))]
    (is (< (Math/abs (- paid 6.0)) 1e-12))
    (is (neg? (:hh div-recv)) "a negative holding pays")
    (doseq [s [:hh :nfc :gov :row]]
      (is (<= (Math/abs (get div-recv s)) paid) (str s " within what is paid")))
    (is (< (Math/abs (- (:hh div-recv) (* paid (/ (:hh w) (reduce + (map #(Math/abs (double %)) (vals w))))))) 1e-9)))
  (testing "all holdings positive: shares of the yield-weighted total, as before"
    (let [p {:div-share {:nfc 0.05 :fin 0.01} :q-a {:hh 0.03 :nfc 0.03 :fin 0.03 :gov 0.03 :row 0.03}}
          {:keys [div-recv]} (sfc/property-income bsm0 p 100.0)
          tot (reduce + (for [s [:hh :nfc :fin :gov :row]] (get-in bsm0 [s :equity :a])))]
      (is (< (Math/abs (- (:hh div-recv) (* 6.0 (/ 40.0 tot)))) 1e-9)))))
