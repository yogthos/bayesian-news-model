(ns bnm.coupled-test
  (:require [bnm.coupled :as cp]
            [bnm.engine :as eng]
            [bnm.modules.consistency :as mc]
            [bnm.modules.sfc :as msfc]
            [bnm.sfc :as sfc]
            [bnm.sfc-analysis :as sa]
            [bnm.sfc-test :as fx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ids (sa/ids-of "SIM"))

(deftest one-member-and-no-analysis-is-the-engine-run
  (let [ms (msfc/modules "SIM" fx/st0 fx/p0 {:country-ids true})
        direct (eng/run ms {:from 2001 :to 2008})
        r (cp/run-ensemble (fn [_] ms) {:from 2001 :to 2008 :keep true})
        rows (atom [])
        _ (cp/run-ensemble (fn [_] ms) {:from 2001 :to 2008 :on-step (fn [id t e] (swap! rows into (eng/table-rows (first (filter #(= id (:module %)) ms)) t e)))})]
    (is (= (:emits direct) (:emits (first (:members r)))))
    (is (= (:t direct) (:t r)))
    (is (= (set (for [m ms [i t] (map-indexed vector (:t direct))
                      :let [e (into {} (for [[v xs] (get-in direct [:emits (:module m)]) :let [x (nth xs i)] :when (some? x)] [v x]))]
                      r (eng/table-rows m t e)] r))
           (set @rows))
        "the coupled table's rows are the engine's")))

(defn- counter [id]
  {:module id :group :W :place :world :dt 1 :init 0.0 :reads [] :emits [{:var (str (name id) "/level") :unit "u"}]
   :step (fn [s _ _ _ dt] {:state (+ s dt) :emits {(str (name id) "/level") s}})})

(deftest shared-modules-step-once-and-members-are-summarised
  (let [calls (atom 0)
        shared (assoc (counter :w) :step (fn [s _ _ _ dt] (swap! calls inc) {:state (+ s dt) :emits {"w/level" s}}))
        member (fn [i] {:module :x :group :R :place :USA :dt 1 :init 0.0
                        :reads [{:var "w/level" :unit "u"}] :emits [{:var "x/y" :unit "u"} {:var "x/rows" :unit "u"}]
                        :step (fn [s in _ t _] {:state s :emits {"x/y" (+ (in "w/level") i)
                                                                 "x/rows" [["USA" (str (* 2.0 i)) (str (dec t)) "m"]]}})})
        out (atom {})
        r (cp/run-ensemble (fn [i] [shared (member i)]) {:members 3 :shared #{:w} :from 2000 :to 2002 :keep true
                                                         :on-step (fn [id t e] (swap! out assoc [id t] e))})]
    (is (= 3 @calls) "the shared module stepped once a year, not once per member")
    (is (= [2.0 3.0 4.0] (get-in (nth (:members r) 2) [:emits :x "x/y"])) "each member reads the shared emits")
    (is (= {"x/y" [["USA" "2.0" nil nil "1.0"]] "x/rows" [["USA" "2.0" "2000" "m" "2.0"]]} (@out [:x 2001])) "median and sd")
    (is (= {"w/level" 1.0} (@out [:w 2001])) "a shared module's own emits")))

;; -- assimilation ------------------------------------------------------------------------

(def truth-p (assoc fx/p0 :n 0.07))
(def truth-st (assoc fx/st0 :w 0.63 :Y 104.0))

(def truth (eng/run (msfc/modules "SIM" truth-st truth-p {:country-ids true}) {:from 2001 :to 2014}))

(defn- truth-at [mod v y] (get-in truth [:emits mod v (- y 2001)]))

(def truth-obs
  "What becomes known at t of the truth: data year t - 2's closing balance
  sheet, net lending over output and wage share."
  (fn [t]
    (let [y (- t 2)]
      (when (<= 2001 y 2014)
        (let [end (sfc/apply-fof (truth-at (:sfc-finance ids) "sfc/bsm" y) (truth-at (:sfc-accounts ids) "sfc/fof" y))]
          (vec (concat
                (for [[i p] (map-indexed vector sa/positions) :let [v (get-in end p)] :when (and v (not (zero? v)))]
                  {:year y :what [:end i] :y v :sd (max (* 0.02 (Math/abs v)) 0.2)})
                (for [s [:hh :nfc :gov :row]]
                  {:year y :what [:nl s] :y (truth-at (:sfc-accounts ids) (str "sfc/net-lending-" (name s)) y) :sd 0.003})
                [{:year y :what [:w] :y (truth-at (:sfc-accounts ids) "sfc/wage-share" y) :sd 0.005}])))))))

(defn- ensemble [opts]
  (cp/run-ensemble (sa/members-of "SIM" fx/st0 fx/p0 {:members 24 :seed 3})
                   (merge {:members 24 :from 2001 :to 2012 :keep true :seed 5
                           :analyses [(sa/analysis {:ids ids :obs truth-obs})]}
                          opts)))

(defn- every-member [r mod v] (for [m (:members r) x (get-in m [:emits mod v]) :when x] x))

(deftest identities-hold-after-assimilation
  (let [r (ensemble {})]
    (is (= (range 2003 2013) (:analysed r)) "an analysis each year from the first known data year")
    (testing "every member's books, after every update, close to 1e-10 of output"
      (doseq [mem (:memories r) :let [mem (first mem)] [y rec] (:years mem)]
        (is (< (sa/identity-gaps rec) 1e-10) (str "year " y)))
      (doseq [v ["sfc/bsm-gap" "sfc/fof-gap"]]
        (is (every? #(< % 1e-10) (every-member r (:sfc-finance ids) v)) v))
      (is (every? #(< % 1e-10) (every-member r (:sfc-accounts ids) "sfc/tfm-gap")))
      (is (every? #(< (Math/abs %) 1e-10)
                  (for [m (:members r) i (range (count (:t r)))
                        :let [nls (keep #(get-in m [:emits (:sfc-accounts ids) (str "sfc/net-lending-" %) i]) ["hh" "nfc" "fin" "gov" "row"])]
                        :when (= 5 (count nls))]
                    (reduce + nls)))
          "net lending sums to zero"))
    (testing "the analysis moves the members towards the data"
      (let [log (:log (first (first (:memories r))))]
        (is (seq log))
        (is (every? #(< (:after %) (:innovation %)) log) "closer after each update than before")))
    (testing "and the forecasts with it"
      ;; the wage share is observed, so the members track it; output is not
      ;; (nor trend growth, a parameter the state does not carry)
      (let [free (ensemble {:analyses []})
            err (fn [r] (/ (reduce + (for [y (range 2005 2013) :let [i (- y 2001)]]
                                       (Math/abs (- (/ (reduce + (map #(get-in % [:emits (:sfc-accounts ids) "sfc/wage-share" i]) (:members r))) 24.0)
                                                    (truth-at (:sfc-accounts ids) "sfc/wage-share" y)))))
                           8.0))]
        (is (< (err r) (* 0.5 (err free))) "the wage share 2005-2012 nearer the truth than without the data")))))

(deftest a-free-run-takes-no-data-after-its-origin
  (let [r (cp/free-run (sa/members-of "SIM" fx/st0 fx/p0 {:members 8 :seed 3})
                       {:members 8 :from 2001 :origin 2007 :years 5 :keep true
                        :analyses [(sa/analysis {:ids ids :obs truth-obs})]})]
    (is (= 2012 (last (:t r))))
    (is (= [2003 2004 2005 2006 2007] (:analysed r)))
    (is (every? #(< % 1e-10) (every-member r (:sfc-finance ids) "sfc/fof-gap")))))

;; -- consistency ---------------------------------------------------------------------------

(deftest the-sheaf-module-writes-gaps-not-fixes
  (let [sfc-ms (msfc/modules "SIM" fx/st0 fx/p0 {:country-ids true})
        keen {:module :keen :group :D :place :country :dt 1 :reads []
              :emits [{:var "keen/omega" :unit "share of value added" :known {:data-year 1}}
                      {:var "keen/lambda" :unit "rate" :known {:data-year 1}}
                      {:var "keen/d" :unit "ratio to GDP" :known {:data-year 1}}
                      {:var "keen/*" :unit "mixed" :known {:data-year 1}}]
              :step (fn [s _ _ t _] {:state s :emits {"keen/omega" [["SIM" "0.5" nil nil]] "keen/state-year" [["SIM" (str (dec t)) nil nil]]}})}
        idx {"klems" {"gfcf" {"SIM" {2003 {"A" 1.0 "C10-C12" 2.0 "K" 50.0}}}}
             "sfc" {"nfc" {"SIM" {2003 {"gcf" 4.0}}}}}
        c (mc/module {:sfc-ids {"SIM" (:sfc-accounts ids)} :keen? true :idx idx})
        ms (concat sfc-ms [keen c])
        r (eng/run ms {:from 2001 :to 2006})
        out (fn [v y] (first (filter #(= (str y) (nth % 2)) (apply concat (keep identity (get-in r [:emits :consistency v]))))))
        sfc-at (fn [v y] (get-in r [:emits (:sfc-accounts ids) v (- y 2001)]))]
    (is (empty? (eng/validate ms)))
    (is (= :consistency (last (:order (eng/causal-order ms)))))
    (is (< (Math/abs (- (parse-double (second (out "sheaf/wage-share" 2003))) (- (sfc-at "sfc/wage-share" 2003) 0.5))) 1e-9)
        "the SFC wage share less Keen's, at Keen's state year")
    (is (< (Math/abs (- (parse-double (second (out "sheaf/nfc-investment" 2003)))
                        (- (sfc-at "sfc/nfc-investment-share" 2003) (/ 3.0 (sfc-at "sfc/output" 2003)))))
           1e-9)
        "KLEMS's NFC industries only (not K), over the SFC core's output, known two years on")
    (is (< (Math/abs (- (parse-double (second (out "sheaf/nfc-investment-data" 2003))) (/ 1.0 (sfc-at "sfc/output" 2003)))) 1e-9))
    (is (= [2005] (keep-indexed (fn [i x] (when (seq x) (+ 2001 i))) (get-in r [:emits :consistency "sheaf/nfc-investment"])))
        "written when KLEMS's year becomes known")))
