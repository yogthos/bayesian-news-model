(ns bnm.world3-test
  (:require [bnm.stockflow :as sf]
            [bnm.world3 :as w3]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) (* tol (max 1.0 (Math/abs (double b))))))

(defn- at-1900
  "Every value of the standard run at 1900."
  ([] (at-1900 {}))
  ([opts]
   (apply merge (vals (sf/at (w3/model opts) 1900.0)))))

(deftest tables-map-onto-tabhl
  ;; WorldDynamics' (ys, (lo, hi)) is an even grid over [lo hi]
  (is (close? 1.26667 (w3/lookup w3/tables :lymc (/ (* 5e9 0.96) 9e8)) 1e-4) "lymc at 5.33: 1 + 2 x 5.33/40")
  (is (= 1.0 (w3/lookup w3/tables :cuf 1.0)) "cuf at its low end")
  (is (= 0.1 (w3/lookup w3/tables :cuf 20.0)) "flat beyond the high end")
  (is (close? 7.2 (w3/lookup w3/tables :hsapc 90.0) 1e-12) "hsapc: 20 x 90/250")
  (is (close? 0.01 (w3/lookup w3/tables :fsafc 3.0) 1e-12) "halfway from 0.005 to 0.015")
  (testing "World3-03 revises some of World3's 1972 tables"
    (is (= [0.0 1.0 1.43 1.5 1.5 1.5] (first (:lmf w3/tables))))
    (is (= [0.0 1.0 1.2 1.3 1.35 1.4] (first (:lmf w3/tables-1972))))
    (is (= 1000.0 (:alln w3/parameters))) (is (= 3.8 (:dcfsn w3/parameters)))))

(deftest initial-conditions-as-worlddynamics
  ;; the perceptions and delays start from values worked out on World3's 1972
  ;; tables at a population of 1.61e9 (WorldDynamics' initialisations)
  (let [i (w3/initial)
        pop 1.61e9
        sopc (/ 1.5e11 pop) hsapc (* 20.0 (/ sopc 250.0))
        lmf (+ 1.0 (* 0.2 (- (/ (/ 4e11 pop) 230.0) 1.0)))
        lmhs (+ 1.0 (* 0.1 (/ hsapc 20.0)))
        iopc (/ 0.7e11 pop)
        cmi (+ 0.5 (* -0.45 (/ iopc 200.0)))
        fpu (* 0.2 (/ pop 2e9))
        le (* 28.0 lmf lmhs 0.999 (- 1.0 (* cmi fpu)))]
    (is (close? hsapc (:ehspc i) 1e-12))
    (is (close? le (:ple i) 1e-12) "le from lmf, lmhs, lmp at ppolx 1, lmc")
    (is (close? (/ 6.65e10 pop) (:diopc i) 1e-12)) (is (= (:diopc i) (:aiopc i)))
    (testing "the pollution delay passes on 1900's generation rate of WorldDynamics' stand-ins"
      (is (close? (+ (* 0.17 1.6e9 0.02 0.1 10.0) (* 6.6 9e8 0.001)) (:ppapr i) 1e-12)))
    (is (= 1e12 ((:nr i) {:nri 1e12}))) (is (= 1.6e9 (+ (:p1 i) (:p2 i) (:p3 i) (:p4 i))))))

(deftest capital-at-1900
  (let [v (at-1900)]
    (is (close? 1.0 (:cuf v) 1e-12) "lufd starts at 1")
    (is (close? 0.05 (:fcaor v) 1e-12) "nrfr 1")
    (is (close? (/ (* 2.1e11 0.95) 3.0) (:io v) 1e-12) "io = ic (1 - fcaor) cuf / icor")
    (is (close? (/ 6.65e10 1.6e9) (:iopc v) 1e-12))
    (is (close? 90.0 (:sopc v) 1e-12) "so = sc cuf / scor over 1.6e9")
    (is (close? (/ 2.1e11 14.0) (:icdr v) 1e-12))
    ;; fioaa1(269.33/ifpc1 281.95) 0.10896, fioas1(90/isopc1 94.03) 0.10857, fioac 0.43
    (is (close? 0.352468116846468 (:fioai v) 1e-12))))

(deftest agriculture-at-1900
  (let [v (at-1900)
        aiph (/ (* 5e9 0.96) 9e8)
        ly (* 600.0 (+ 1.0 (* 2.0 (/ aiph 40.0))))]
    (is (close? 0.04 (:falm v) 1e-12) "pfr 1")
    (is (close? aiph (:aiph v) 1e-12))
    (is (close? ly (:ly v) 1e-12) "ly = lyf lfert lymc lymap, lymap 1 at io/io70 < 10")
    (is (close? (* ly 9e8 0.7 0.9) (:f v) 1e-12))
    (is (close? 4.3092e11 (:f v) 1e-5) "Vensim's food at 1900")
    (is (close? (/ 9e8 (* 1000.0 (w3/lookup w3/tables :llmy1 (/ ly 600.0)))) (:ler v) 1e-12) "ler = al / (alln llmy)")
    (is (close? 0.0 (:lfr v) 1e-12) "lfert starts at ilf")))

(deftest population-at-1900
  ;; by hand: fpc 4.3092e11/1.6e9 = 269.325, sopc 90, iopc 41.5625, ppolx 0.18382
  (let [v (at-1900)
        lmf (+ 1.0 (* 0.43 (- (/ 269.325 230.0) 1.0)))   ; World3-03's lmf
        lmhs 1.036                                       ; lmhs1(hsapc(90) = 7.2), before 1940
        lmp 0.9998161764705882                           ; 1 - 0.01 x 0.18382/10
        lmc (- 1.0 (* 0.406484375 0.16))                 ; cmi(41.5625), fpu(1.6e9)
        le (* 28.0 lmf lmhs lmp lmc)]
    (is (close? 1.073520652173913 (:lmf v) 1e-12))
    (is (close? lmhs (:lmhs v) 1e-12))
    (is (close? lmp (:lmp v) 1e-12))
    (is (close? 0.9349625 (:lmc v) 1e-12))
    (is (close? 29.11002255148156 le 1e-12)) (is (close? le (:le v) 1e-12))
    (is (close? 24952755.536489345 (:d1 v) 1e-10) "p1 m1(29.11)")
    (is (close? 51627978.73533929 (:dr v) 1e-10))
    (testing "births: mtf 6.986, dtf 3.8 x frsn 0.7 x sfsn 1.1856 x cmple 1.6816 = 4.471, fce 0.7753"
      (is (close? 5.0363526870557935 (:tf v) 1e-10) "mtf (1 - fce) + dtf fce")
      (is (close? 58757448.01565092 (:br v) 1e-10) "tf p2 / 2 / rlt"))))

(deftest pollution-and-resources-at-1900
  (let [v (at-1900)]
    (is (close? (/ 2.5e7 1.36e8) (:ppolx v) 1e-12) "Vensim's 0.183824")
    (is (close? 2.82625e8 (:nrur v) 1e-12) "1.6e9 pop x 0.17664 pcrum x 1 nruf")
    (is (close? (* 0.85 (/ (/ 6.65e10 1.6e9) 200.0)) (:pcrum v) 1e-12) "World3-03's pcrum")
    (is (close? (/ 2.5e7 (* 1.4 1.5 1.0)) (:ppasr v) 1e-12) "ahlm 1 below ppolx 1")
    (is (close? (+ (* 0.17664 1.6e9 0.02 0.1 10.0) (* (/ 4.8e9 9e8) 9e8 0.001)) (:ppgr v) 1e-4)
        "industry's 5.65e6 and agriculture's 4.8e6, ppgf 1")
    (is (close? 1.04525e7 (:ppgr v) 1e-12))))

(def reference (edn/read-string (slurp "test/resources/world3-reference.edn")))

(defn- compare-runs
  "WorldDynamics' compare: max over i of |a_i - b_i| / (|b_i| + pepsi)."
  [a b pepsi]
  (reduce max (map (fn [x y] (/ (Math/abs (- (double x) (double y))) (+ (Math/abs (double y)) pepsi))) a b)))

(def ours {:food [:aux :f] :industrial-output [:aux :io] :nonrenewable-resources [:stocks :nr]
           :persistent-pollution [:aux :ppolx] :population [:aux :pop]})

(deftest the-standard-run-is-vensim-s
  ;; World3-03's scenario 1 against Vensim's Euler solution, dt 0.5
  (let [r (w3/run {})
        errs (into (sorted-map) (for [[k path] ours] [k (compare-runs (reference k) (get-in r path) 1.0)]))]
    (println "world3 max relative error vs Vensim:" errs)
    (is (= 401 (count (:t r))))
    (doseq [[k path] ours] (is (= 401 (count (reference k)) (count (get-in r path))) (str k " point for point")))
    (doseq [[k e] errs]
      (is (< e 0.1) (str k " within WorldDynamics' tolerance"))
      (is (< e 1e-5) (str k " as Vensim's, to its six printed digits")))
    (testing "each material stock's account closes: its change is what its flows moved"
      (doseq [s (:conserved r)
              :let [xs (get-in r [:stocks s])
                    moved (reduce + 0.0 (for [[k {:keys [from to]}] (:flows (w3/model))
                                              :when (or (= from s) (= to s))
                                              x (butlast (get-in r [:flows k]))]
                                          (* 0.5 (if (= to s) x (- x)))))]]
        (is (close? (- (last xs) (first xs)) moved 1e-9) (str s)))
      (is (< (/ (sf/imbalance r) 1e12) 1e-9)))))

(deftest units-agree
  (let [m (w3/model)]
    (is (empty? (sf/unit-errors m "year")))
    (is (every? :unit (for [[_ st] (:stocks m) :when (not (:info st))] st)) "every material stock has one")))

(deftest starts-as-vensim-s
  ;; SMOOTH, DLINF3 and DELAY3 start at their inputs' values at t0
  (let [v (at-1900)]
    (is (close? (:hsapc v) (:ehspc v) 1e-12))
    (is (close? (:le v) (:ple v) 1e-12)) (is (close? (:le v) (:ple-s1 v) 1e-12))
    (is (close? (:iopc v) (:diopc-s2 v) 1e-12)) (is (close? (:iopc v) (:aiopc v) 1e-12))
    (is (close? (:fcapc v) (:fcfpc v) 1e-12))
    (is (close? (:ppgr v) (:ppapr v) 1e-12) "the delay passes on what enters it")
    (is (close? (/ (* 20.0 (:ppgr v)) 3.0) (:ppapr-1 v) 1e-12) "each stage holds pptd/3 of it")
    (is (= 1.0 (:pfr v) (:lufd v)) "SMOOTHI's from 1")
    (is (= 5e9 (:ai v))))
  (testing "WorldDynamics' starts stay within its tolerance"
    (let [r (w3/run {:start :worlddynamics})]
      (doseq [[k path] ours]
        (is (< 1e-3 (compare-runs (reference k) (get-in r path) 1.0) 0.1) (str k))))))

(deftest scenarios-override-parameters
  (let [nr (fn [opts] (last (get-in (w3/run opts) [:stocks :nr])))]
    (is (> (nr {:params {:nri 2e12}}) (nr {})) "twice the resources leave more of them")
    (testing "World3's constants are the model's parameters: a run overrides them"
      (is (= (nr {:params {:nri 2e12}})
             (last (get-in (sf/run (w3/model) {:from 1900.0 :to 2100.0 :dt 0.5 :params {:nri 2e12}}) [:stocks :nr]))))
      (is (= 2e12 (first (get-in (sf/run (w3/model) {:from 1900.0 :to 1901.0 :dt 0.5 :params {:nri 2e12}}) [:stocks :nr])))))
    (testing "so under WorldDynamics' starts too"
      (let [m (w3/model {:start :worlddynamics})]
        (is (= 2e12 (first (get-in (sf/run m {:from 1900.0 :to 1901.0 :dt 0.5 :params {:nri 2e12}}) [:stocks :nr]))))
        (is (= 2e12 (first (get-in (w3/run {:start :worlddynamics :params {:nri 2e12} :to 1901.0}) [:stocks :nr]))))))))
