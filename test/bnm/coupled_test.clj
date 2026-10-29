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
    (let [engine-rows (atom [])
          rows-of (fn [acc] (fn [id t e] (swap! acc into (eng/table-rows (first (filter #(= id (:module %)) ms)) t e))))]
      (eng/run ms {:from 2001 :to 2008 :on-step (rows-of engine-rows)})
      (is (= @engine-rows @rows) "the coupled table's rows are the engine's, in its order"))))

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
  sheet and net lending over output, and wage share."
  (fn [t]
    (let [y (- t 2)]
      (when (<= 2001 y 2014)
        (let [end (sfc/apply-fof (truth-at (:sfc-finance ids) "sfc/bsm" y) (truth-at (:sfc-accounts ids) "sfc/fof" y))]
          (vec (concat
                (for [[i p] (map-indexed vector sa/positions) :let [v (get-in end p)] :when (and v (not (zero? v)))
                      :let [x (/ v (truth-at (:sfc-accounts ids) "sfc/output" y))]]
                  {:year y :what [:end i] :y x :sd (max (* 0.02 (Math/abs x)) 0.002)})
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
        ;; the stochastic update (perturbed observations) need not reduce a
        ;; misfit already within the noise, but does on the whole
        ;; not every update: here trend growth is wrong and never relearned, so
        ;; the members drift from the truth, and with 24 members against some
        ;; 44 observations an update can move a misfit away through the
        ;; ensemble's sampling covariances (a-free-run-from-the-panel and the
        ;; relearned tests are the realistic case)
        (is (< (reduce + (map :after log)) (* 0.8 (reduce + (map :innovation log)))) "closer after the updates than before")))
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

(deftest the-spread-is-inflated-to-the-misfit
  (let [ens (vec (for [i (range 20)] [(+ 10.0 (* 0.1 (- i 9.5)))]))
        h (fn [x _] [(first x)])]
    (is (== 1.0 (sa/inflation ens h [10.0] [0.01] 3.0)) "a misfit within the spread and noise: none")
    (is (< 2.0 (sa/inflation ens h [13.0] [0.01] 3.0) 3.000001) "a misfit of ten spreads: up to the cap")))

(deftest relearned-parameters-carry-what-the-state-cannot
  ;; trend growth is a parameter: assimilating the state leaves output off
  ;; the truth, relearning (here, the truth's own) brings it back
  ;; output is never observed, so the years before the first analysis leave
  ;; a level gap; the mean error over the analysed years, not one year's
  (let [out (fn [r i] (/ (reduce + (map #(get-in % [:emits (:sfc-accounts ids) "sfc/output" i]) (:members r))) 24.0))
        err (fn [r] (/ (reduce + (for [y (range 2005 2013)] (Math/abs (- (out r (- y 2001)) (truth-at (:sfc-accounts ids) "sfc/output" y))))) 8.0))
        plain (ensemble {})
        relearned (ensemble {:analyses [(sa/analysis {:ids ids :obs truth-obs :relearn (fn [_] truth-p)})]})]
    (is (< (err relearned) (* 0.6 (err plain))) (str (err relearned) " vs " (err plain)))
    (is (every? #(< % 1e-10) (every-member relearned (:sfc-finance ids) "sfc/fof-gap")))))

(deftest a-free-run-from-the-panel
  (when (.exists (io/file "data/derived/sfc/panel.csv"))
    (let [panel (sfc/read-panel "data/derived/sfc/panel.csv")
          kn (sfc/read-keen "data/derived/stocks/keen.csv")
          r (sa/free-run panel kn "DEU" {:start 1995 :origin 2012 :years 4 :members 6})]
      (is (= (range 1996 2017) (:t r)))
      (is (= (range 1998 2013) (:analysed r)) "data years 1996 on, known two years later, through the origin")
      (is (every? number? (get-in r [:vars "sfc/output" :median])))
      (is (every? pos? (drop-last 0 (get-in r [:vars "sfc/output" :sd]))) "a spread every year")
      (is (every? #(< % 1e-10) (for [m (:members (:result r)) x (get-in m [:emits :sfc-finance-DEU "sfc/fof-gap"]) :when x] x))))))

;; -- a free run takes nothing known after its origin ----------------------------------------

(def ^:private sim-panel
  (delay {"SIM" (update-vals (@#'fx/sim-panel 24) #(assoc % :stocks-source :oecd-t720))}))

(def ^:private sim-keen (delay (@#'fx/sim-keen 24)))

(defn- scaled [ob k]
  (cond-> (update ob :y #(some-> % (* k)))
    (:f ob) (update :f update-vals #(update-vals % (fn [v] (* k v))))
    (:b ob) (update :b update-vals #(update-vals % (fn [m] (update-vals m (fn [v] (some-> v (* k)))))))))

(defn- after
  "The panel and keen.csv with everything first known after `origin`
  changed: rows whose flows or stocks are published later scaled, keen
  rows known later moved."
  [panel kn origin]
  (let [known (fn [ob k] (or (get ob k) (+ (:year ob) sfc/vintage)))]
    [(update-vals panel (fn [rows] (update-vals rows #(if (or (> (known % :known-flows) origin) (> (known % :known-stocks) origin)) (scaled % 1.25) %))))
     (update-vals kn (fn [rows] (into {} (for [[y r] rows] [y (if (> y origin) (update-vals r #(* 0.97 %)) r)]))))]))

(defn- free [panel kn] (sa/free-run panel kn "SIM" {:origin 2014 :years 4 :members 6 :seed 4}))

(deftest a-free-run-does-not-move-with-data-after-its-origin
  (let [panel @sim-panel kn @sim-keen
        base (free panel kn)
        [p2 k2] (after panel kn 2014)]
    (is (seq (:analysed base)))
    (is (= 2014 (last (:analysed base))))
    (is (not= panel p2) "the perturbation changes the data")
    (is (= (:vars base) (:vars (free p2 k2))) "every variable's median and spread, every year")
    (testing "a data year published late is unknown until it is published"
      ;; data year 2011's flows and stocks published in 2016, after the origin:
      ;; neither the analysis nor the relearned parameters may read them
      (let [late (assoc-in panel ["SIM" 2011 :known-flows] 2016)
            late (assoc-in late ["SIM" 2011 :known-stocks] 2016)
            [p3 k3] (after late kn 2014)]
        (is (not= (get-in late ["SIM" 2011 :y]) (get-in p3 ["SIM" 2011 :y])))
        (is (= (:vars (free late kn)) (:vars (free p3 k3))))))))

(deftest relearned-parameters-are-every-members
  ;; relearning sets the same parameters in every member and each of its
  ;; modules: the perturbed trend growth is not kept (no observation
  ;; corrects a member's own growth, so its draw would compound)
  (let [p-now (assoc truth-p :n 0.05)
        r (ensemble {:analyses [(sa/analysis {:ids ids :obs truth-obs :relearn (fn [_] p-now)})] :to 2006})]
    (is (= #{p-now} (set (for [ps (:params r) id (vals ids)] (get-in ps [id :sfc])))))))

(deftest free-runs-branch-the-same-free-runs
  ;; one assimilated pass with a free run branched off after each origin
  ;; gives what a separate free run from that origin gives
  (let [panel @sim-panel kn @sim-keen
        opts {:years 3 :members 6 :seed 4}
        fs (sa/free-runs panel kn "SIM" (assoc opts :origins [2010 2013]))]
    (doseq [o [2010 2013] :let [one (sa/free-run panel kn "SIM" (assoc opts :origin o))]]
      (is (= (:t one) (get-in fs [o :t])))
      (is (= (:vars one) (get-in fs [o :vars])) (str "origin " o)))
    (is (seq (:log fs)))))

(deftest a-restart-is-the-phase-a-run-from-the-origin
  (let [panel @sim-panel kn @sim-keen
        r (sa/restart panel kn "SIM" 2012 4)
        p (sfc/learn (sa/known-panel panel 2014) kn "SIM" 2012 {})
        st (sfc/init panel kn "SIM" 2012 p)
        direct (eng/run (msfc/modules "SIM" st p {:country-ids true}) {:from 2013 :to 2016})]
    (is (= [2013 2014 2015 2016] (:t r)))
    (is (= (get-in direct [:emits (:sfc-accounts ids) "sfc/net-lending-hh"]) (get-in r [:vars "sfc/net-lending-hh" :median])))))

;; -- the fiscal shares in the analysed state (bnm-2zk) ----------------------------------

(defn- truth-fiscal
  "The truth's government consumption, investment and benefits in data
  year y: levels grown at its trend from the start."
  [y]
  (let [g (Math/pow (+ 1.0 (:n truth-p)) (- y 2000))]
    {:cg (* (:cg truth-st) g) :ig (* (:ig truth-st) g) :ben (* (:ben truth-st) g)}))

(def truth-obs-fiscal
  "truth-obs and, known with the year's other flows, government
  consumption, investment and benefits over output (Table 14)."
  (fn [t]
    (let [y (- t 2) os (truth-obs t)]
      (when (seq os)
        (into os (for [[k v] (truth-fiscal y)]
                   {:year y :what [k] :y (/ v (truth-at (:sfc-accounts ids) "sfc/output" y)) :sd 0.002}))))))

(def ^:private fiscal-sd {:cg 0.01 :ig 0.03 :ben 0.01})

(def off-fiscal
  "Members started with the fiscal levels off the truth's (as FRA's were
  by 2018: consumption 0.18 and benefits 0.15 of output, against 0.24 and
  0.22) and some model error in them."
  (sa/members-of "SIM" (assoc truth-st :cg 13.0 :ben 8.0) (assoc truth-p :fiscal-sd fiscal-sd)
                 {:members 24 :seed 3}))

(defn- fiscal-share [r k y]
  (let [mem (first (first (:memories r)))] ; a member's analysed record
    (/ (get-in mem [:years y k]) (get-in mem [:years y :Y]))))

(deftest fiscal-shares-are-analysed
  ;; in the simulated economy the fiscal share returns to its multiplier
  ;; equilibrium (output follows spending), so the test is of the update:
  ;; the fiscal levels of the observed years move to the observations, and
  ;; the books still close
  (let [r (cp/run-ensemble off-fiscal {:members 24 :from 2001 :to 2012 :keep true :seed 5
                                       :analyses [(sa/analysis {:ids ids :obs truth-obs-fiscal :relearn (fn [_] (assoc truth-p :fiscal-sd fiscal-sd))})]})
        log (:log (first (first (:memories r))))]
    (testing "the fiscal misfit shrinks at the update"
      (is (every? :fiscal-innovation log))
      (is (< (reduce + (map :fiscal-after log)) (* 0.6 (reduce + (map :fiscal-innovation log))))
          (pr-str (map (juxt :fiscal-innovation :fiscal-after) log))))
    (testing "every member's books, after every update, close to 1e-10 of output"
      (doseq [mem (:memories r) :let [mem (first mem)] [y rec] (:years mem)]
        (is (< (sa/identity-gaps rec) 1e-10) (str "year " y)))
      (doseq [v ["sfc/bsm-gap" "sfc/fof-gap"]]
        (is (every? #(< % 1e-10) (every-member r (:sfc-finance ids) v)) v))
      (is (every? #(< % 1e-10) (every-member r (:sfc-accounts ids) "sfc/tfm-gap"))))
    (testing "the analysed record carries the fiscal levels"
      (is (number? (fiscal-share r :cg 2010))))))

(deftest fiscal-model-error-is-a-members-own-and-repeatable
  (let [ms (fn [seed] (sa/members-of "SIM" fx/st0 (assoc fx/p0 :fiscal-sd {:cg 0.02 :ig 0.02 :ben 0.02}) {:members 4 :seed seed}))
        cg (fn [mof i] (get-in (eng/run (mof i) {:from 2001 :to 2006}) [:states (:sfc-accounts ids) :cg]))]
    (is (= (cg (ms 3) 1) (cg (ms 3) 1)) "the same member and seed, the same path")
    (is (not= (cg (ms 3) 1) (cg (ms 3) 2)) "members differ")
    (is (< (Math/abs (- (* 18.0 (Math/pow 1.04 6))
           (get-in (eng/run (msfc/modules "SIM" fx/st0 (assoc fx/p0 :fiscal-sd {:cg 0.02 :ig 0.02 :ben 0.02}) {:country-ids true}) {:from 2001 :to 2006})
                   [:states (:sfc-accounts ids) :cg])))
           1e-9)
        "one member has no model error: phase A's run")))

(deftest learned-fiscal-error-is-the-shares-yearly-change
  (let [panel {"SIM" (@#'fx/sim-panel 30)}
        p (sfc/learn panel {} "SIM" 2025 {})]
    (doseq [k [:cg :ig :ben]]
      (is (number? (get-in p [:fiscal-sd k])))
      (is (<= 0.0 (get-in p [:fiscal-sd k]) 0.2)))
    (let [cut {"SIM" (into {} (filter #(<= (key %) 2015)) (get panel "SIM"))}]
      (is (= (:fiscal-sd (sfc/learn panel {} "SIM" 2015 {})) (:fiscal-sd (sfc/learn cut {} "SIM" 2015 {})))))))

(deftest france-does-not-rest-on-its-imputed-sheets
  ;; bnm-2zk: FRA's free run moved with its 1970-94 imputed balance sheets
  ;; (0.023 -> 0.142 when bnm-a0s changed them) through fiscal levels the
  ;; analysis never corrected
  (when (.exists (io/file "data/derived/sfc/panel.csv"))
    (let [panel (sfc/read-panel "data/derived/sfc/panel.csv")
          kn (sfc/read-keen "data/derived/stocks/keen.csv")
          pre (fn [f] (update panel "FRA" (fn [rows] (into {} (for [[y ob] rows] [y (if (and (< y 1995) (:b ob)) (f ob) ob)])))))
          run (fn [p] (sa/free-run p kn "FRA" {:start 1995 :origin 2021 :years 3 :members 16 :seed 1}))
          nl (fn [r] (for [s ["hh" "nfc" "gov"] i (range (- (count (:t r)) 5) (count (:t r)))] (get-in r [:vars (str "sfc/net-lending-" s) :median i])))
          base (nl (run panel))]
      (doseq [[label p] [["imputed sheets dropped" (pre #(dissoc % :b :stocks-source))]
                         ["imputed debt half again" (pre (fn [ob] (update ob :b (fn [b] (reduce (fn [b s] (update-in b [s :debt :l] #(some-> % (* 1.5)))) b [:hh :nfc :gov])))))]]]
        (let [d (/ (reduce + (map #(Math/abs (- %1 %2)) base (nl (run p)))) (count base))]
          (is (< d 0.01) (str label ": mean |change| in net lending " d)))))))

(deftest members-are-drawn-independently
  ;; augur.math/rng seeds its state linearly: seeds 7919 apart, as members
  ;; were drawn, gave neighbouring members correlated draws (0.71 on the
  ;; first, 0.9 on the third)
  (let [corr (fn [xs ys] (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
                               c (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))]
                           (/ c (Math/sqrt (* (reduce + (map #(* (- % mx) (- % mx)) xs)) (reduce + (map #(* (- % my) (- % my)) ys)))))))
        ws (vec (for [i (range 400)] (:w (first (sa/perturb fx/st0 fx/p0 i {:seed 1})))))
        ws3 (vec (for [i (range 400)] (:w (first (sa/perturb fx/st0 fx/p0 i {:seed 3})))))]
    (is (< (Math/abs (corr (butlast ws) (rest ws))) 0.15) "neighbouring members")
    (is (< (Math/abs (corr ws ws3)) 0.15) "the same member under seeds 1 and 3")))

(deftest a-conditional-free-run-is-given-the-path
  (let [panel @sim-panel kn @sim-keen
        path (sa/given-path panel "SIM" (range 2013 2018))
        fs (sa/free-runs panel kn "SIM" {:origins [2014] :years 3 :members 4 :seed 2 :given (fn [_] path)})
        plain (sa/free-runs panel kn "SIM" {:origins [2014] :years 3 :members 4 :seed 2})]
    (is (= (get-in plain [2014 :vars]) (get-in fs [2014 :vars])) "the free branch is the plain free run")
    (is (not= (get-in fs [2014 :vars]) (get-in fs [2014 :conditional :vars])))
    (is (every? (fn [m] (< (Math/abs (- (get-in m [:emits (:sfc-firms ids) "sfc/nfc-investment" (.indexOf ^java.util.List (:t m) 2016)])
                                        (get-in path [2016 :kappa])))
                           1e-12))
                (get-in fs [2014 :conditional :members]))
        "NFC investment at the given share")
    (let [r (sa/restart panel kn "SIM" 2012 4 path)]
      (is (< (Math/abs (- (get-in r [:vars "sfc/nfc-investment-share" :median 3]) (get-in path [2016 :kappa]))) 0.02)))))

;; -- output observed: the members' nominal scale ------------------------------------------

(def truth-obs-output
  "truth-obs and the year's output (Table 14 GDP, national currency)."
  (fn [t]
    (let [y (- t 2) os (truth-obs t)]
      (when (seq os)
        (let [Y (truth-at (:sfc-accounts ids) "sfc/output" y)]
          (conj os {:year y :what [:Y] :y Y :sd (* 0.02 Y)}))))))

(deftest output-is-observed-and-the-scale-follows
  ;; output was never observed, so a member's nominal scale drifted freely
  ;; (BGR, POL, LUX: output in the millions of times the data, or negative);
  ;; observing it rescales every money level of the member by one factor,
  ;; which keeps every ratio and identity
  (let [out (fn [r i] (/ (reduce + (map #(get-in % [:emits (:sfc-accounts ids) "sfc/output" i]) (:members r))) 24.0))
        err (fn [r] (/ (reduce + (for [y (range 2006 2013)] (Math/abs (/ (- (out r (- y 2001)) (truth-at (:sfc-accounts ids) "sfc/output" y))
                                                                           (truth-at (:sfc-accounts ids) "sfc/output" y)))))
                       7.0))
        plain (ensemble {})
        scaled (ensemble {:analyses [(sa/analysis {:ids ids :obs truth-obs-output})]})]
    ;; trend growth here is 3 points short and never relearned, and output is
    ;; known two years on, so some 6-9% of a gap is left whatever the update
    (is (< (err scaled) (* 0.5 (err plain))) (str (err scaled) " vs " (err plain)))
    (doseq [mem (:memories scaled) :let [mem (first mem)] [y rec] (:years mem)]
      (is (< (sa/identity-gaps rec) 1e-10) (str "year " y)))
    (doseq [v ["sfc/bsm-gap" "sfc/fof-gap"]]
      (is (every? #(< % 1e-10) (every-member scaled (:sfc-finance ids) v)) v))
    (is (every? #(< % 1e-10) (every-member scaled (:sfc-accounts ids) "sfc/tfm-gap")))))

(deftest a-collapsed-member-is-resampled
  ;; a member whose output has gone to zero or below has left the model's
  ;; domain: its ratios mean nothing and break the update (LUX, seed 5). At
  ;; the analysis it is replaced by a healthy member's state, keeping its own
  ;; model-error stream
  (let [an (sa/analysis {:ids ids :obs truth-obs-output})
        broken (assoc an :analyse (fn [{:keys [runners memories t] :as ctx}]
                                    (if (= t 2006)
                                      ((:analyse an) (assoc ctx :memories (assoc-in memories [3 :years 2005 :Y] -5.0)))
                                      ((:analyse an) ctx))))
        r (ensemble {:analyses [broken]})
        log (:log (first (first (:memories r))))]
    (is (= 1 (:replaced (first (filter #(= 2006 (:t %)) log)))))
    (is (every? #(pos? (get-in % [(:sfc-accounts ids) :Y])) (:states r)))
    (is (every? #(< % 1e-10) (every-member r (:sfc-finance ids) "sfc/fof-gap")))))
