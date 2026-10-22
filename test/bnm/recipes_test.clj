(ns bnm.recipes-test
  (:require [augur.math :as am]
            [bnm.recipes :as rc]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (and a b (< (Math/abs (- (double a) (double b))) tol)))

(def ^:private p
  {:id "x/y" :unit "t per t" :mean 2.0 :lo 1.6 :hi 2.5 :floor 1.5
   :step 0.02 :ref-year 2011 :sources [{:kind :test :sd0 0.2}]})

(def ^:private opts {:start 2000 :end 2010 :screen-sds 4.0})

(defn- at [rows region year] (first (filter #(and (= region (:region %)) (= year (:year %))) rows)))

;; ------------------------------------------------------------------ priors

(deftest priors-file-is-complete
  (let [{:keys [recipes literature] :as pr} (rc/priors)
        ids (map :id recipes)]
    (is (= (count ids) (count (set ids))) "ids are unique")
    (is (>= (count recipes) 30))
    (doseq [r recipes]
      (testing (:id r)
        (is (string? (:unit r)) "a unit convention")
        (is (< (:lo r) (:mean r) (:hi r)) "lo < mean < hi")
        (is (<= 0.0 (:floor r) (:lo r)) "the floor is at or below the 90% range")
        (is (string? (:floor-basis r)))
        (is (seq (:cite r)) "a citation")
        (is (every? literature (:cite r)) "every citation is in :literature")
        (is (every? rc/source-kinds (map :kind (:sources r))) "known source kinds")))
    (testing "the named set is covered"
      (doseq [id ["ammonia/gas" "ammonia/coal" "urea/ammonia" "steel-bf/iron-ore" "steel-bf/coal"
                  "steel-eaf/scrap" "steel-eaf/electricity" "cement/clinker" "clinker/heat"
                  "aluminium/alumina" "aluminium/electricity" "copper/mined" "polysilicon/electricity"
                  "li-ion/lithium" "li-ion/cobalt" "li-ion/nickel" "li-ion/graphite" "ev/battery"
                  "ev/copper" "wind-onshore/steel" "wind-onshore/copper" "wind-onshore/rare-earths"
                  "pv/silicon" "pv/silver" "grain/n-applied" "grain/p-applied" "grain/k-applied"
                  "power/coal-heat-rate" "power/gas-heat-rate" "power/fossil-share"]]
        (is (some #(= id (:id %)) recipes) id)))
    (is (= 1995 (:start pr)))))

(deftest prior-path-and-drift
  (testing "the prior's centre is the mean, its sd from the 90% range"
    (let [{:keys [m v]} (rc/prior-state p 2011)]
      (is (close? (Math/log 2.0) m 1e-12))
      (is (close? (/ (Math/log (/ 2.5 1.6)) 3.29) (Math/sqrt v) 1e-12))))
  (testing "drift moves the centre from :drift-from on, flat before it"
    (let [q (assoc p :drift -0.1 :drift-from 2005 :ref-year 2010)]
      (is (close? (+ (Math/log 2.0) 0.5) (:m (rc/prior-state q 2000)) 1e-9) "flat before 2005")
      (is (close? (+ (Math/log 2.0) 0.5) (:m (rc/prior-state q 2005)) 1e-9))
      (is (close? (- (Math/log 2.0) 0.2) (:m (rc/prior-state q 2012)) 1e-9)))))

;; -------------------------------------------------------------- truncation

(deftest truncation-moments
  (testing "a standard normal truncated at 0: mean sqrt(2/pi), variance 1 - 2/pi"
    (let [{:keys [m v]} (rc/truncate {:m 0.0 :v 1.0} 0.0)]
      (is (close? (Math/sqrt (/ 2.0 Math/PI)) m 1e-6))
      (is (close? (- 1.0 (/ 2.0 Math/PI)) v 1e-6))))
  (testing "far above the bound nothing moves"
    (is (= {:m 10.0 :v 1.0} (rc/truncate {:m 10.0 :v 1.0} 0.0))))
  (testing "far below it the mass piles at the bound, stably"
    (let [{:keys [m v]} (rc/truncate {:m -30.0 :v 1.0} 0.0)]
      (is (<= 0.0 m 0.1))
      (is (< 0.0 v 0.01))))
  (testing "no bound, no change"
    (is (= {:m -3.0 :v 1.0} (rc/truncate {:m -3.0 :v 1.0} nil)))))

;; ------------------------------------------------------------------ floors

(deftest floors-are-never-violated
  (testing "observations at the edge of the screen pull hard below the floor"
    (let [obs (for [y (range 2000 2011)]
                {:region "A" :year y :known y :value 1.3 :source 0})
          rows (rc/filter-recipe p obs opts)]
      (is (seq rows))
      (doseq [r rows]
        (is (>= (:mean r) 1.5) (str "mean at " (:year r)))
        (is (>= (:median r) 1.5) (str "median at " (:year r))))
      (is (< (:mean (at rows "A" 2010)) 1.6) "but the data do pull it to the floor")))
  (testing "every recipe in the file, with random observations, stays at or above its floor"
    (let [rng (am/rng 7)]
      (doseq [r (:recipes (rc/priors))
              :let [r (assoc r :sources [{:kind :test :sd0 0.3}])
                    obs (for [y (range 2000 2011)]
                          {:region "A" :year y :known y :source 0
                           :value (* (:mean r) (Math/exp (* 1.5 (am/normal! rng))))})
                    obs (if (= :logit (:scale r))
                          (map #(update % :value (fn [x] (min 0.99 (/ x (+ 1.0 x))))) obs)
                          obs)]]
        (doseq [row (rc/filter-recipe r obs opts)]
          (is (>= (:mean row) (:floor r)) (str (:id r) " " (:year row))))))))

;; --------------------------------------------------------------- causality

(deftest no-observation-before-its-known-year
  (let [obs [{:region "A" :year 2001 :known 2004 :value 2.4 :source 0}
             {:region "A" :year 2003 :known 2005 :value 2.3 :source 0}
             {:region "A" :year 2006 :known 2009 :value 1.7 :source 0}]
        rows (rc/filter-recipe p obs opts)]
    (testing "years before the first known one are the prior"
      (doseq [y (range 2000 2004)]
        (is (zero? (:n-obs (at rows "A" y))) (str y))
        (is (close? (:m (rc/prior p y)) (:m (at rows "A" y)) 1e-12))))
    (testing "an observation counts from its known year on, not its data year"
      (is (= 1 (:n-obs (at rows "A" 2004))))
      (is (= 2 (:n-obs (at rows "A" 2005))))
      (is (= 3 (:n-obs (at rows "A" 2009)))))
    (testing "removing what is not yet known leaves every earlier year unchanged"
      (doseq [cut (range 2000 2011)]
        (let [rows' (rc/filter-recipe (assoc p :regions ["A"]) (filter #(<= (:known %) cut) obs) opts)]
          (doseq [y (range 2000 (inc cut))]
            (is (= (at rows "A" y) (at rows' "A" y)) (str "cut " cut " year " y))))))
    (testing "a late observation of an early year enters at the early year's state"
      (let [late [{:region "A" :year 2001 :known 2008 :value 2.4 :source 0}]
            r (rc/filter-recipe p late opts)]
        (is (zero? (:n-obs (at r "A" 2007))))
        (is (= 1 (:n-obs (at r "A" 2008))))
        (is (> (:m (at r "A" 2008)) (:m (rc/prior p 2008))))))))

(deftest a-later-vintage-replaces-an-earlier-one
  (let [obs [{:region "A" :year 2003 :known 2005 :value 2.4 :source 0}
             {:region "A" :year 2003 :known 2007 :value 1.7 :source 0}]
        rows (rc/filter-recipe p obs opts)]
    (is (= 1 (:n-obs (at rows "A" 2006))))
    (is (= 1 (:n-obs (at rows "A" 2007))) "replaced, not added")
    (is (> (:median (at rows "A" 2006)) 2.0))
    (is (< (:median (at rows "A" 2007)) 2.0))))

(deftest the-screen-flags-wild-observations
  (let [obs [{:region "A" :year 2002 :known 2002 :value 2.1 :source 0}
             {:region "A" :year 2003 :known 2003 :value 200.0 :source 0}]
        rows (rc/filter-recipe p obs opts)]
    (is (= 1 (:n-obs (at rows "A" 2005))))
    (is (= 1 (:n-flagged (at rows "A" 2005))))))

;; ---------------------------------------------------------------- recovery

(deftest synthetic-recovery
  (let [rng (am/rng 11)
        q {:id "syn" :unit "u" :mean 1.0 :lo 0.5 :hi 2.0 :floor 0.1 :step 0.05 :ref-year 1995
           :sources [{:kind :test :sd0 0.3} {:kind :test :sd0 0.3}]}
        years (range 1995 2026)
        truth (into {} (map vector years
                            (reductions (fn [x _] (+ x (* 0.04 (am/normal! rng))))
                                        (Math/log 1.3) (rest years))))
        obs (concat
             (for [y years] {:region "A" :year y :known (+ y 2) :source 0
                             :value (Math/exp (+ (truth y) (* 0.05 (am/normal! rng))))})
             (for [y years :when (even? y)]
               {:region "A" :year y :known (+ y 1) :source 1
                :value (Math/exp (+ (truth y) (* 0.25 (am/normal! rng))))}))
        res (rc/run q obs {:start 1995 :end 2025 :screen-sds 6.0})
        rows (:rows res)
        z (for [y (range 2000 2026) :let [r (at rows "A" y)]]
            (/ (Math/abs (- (:m r) (truth y))) (Math/sqrt (:v r))))]
    (testing "the posterior covers the truth"
      (is (>= (count (filter #(< % 2.0) z)) (* 0.8 (count z))) (str "z " (vec z))))
    (testing "and ends near it"
      (let [r (at rows "A" 2025)]
        (is (< (Math/abs (- (:m r) (truth 2025))) 0.15))))
    (testing "the observation noise is learned per source, the sharp one sharper"
      (let [[s0 s1] (map #(Math/sqrt (:s %)) (:noise res))]
        (is (< s0 0.15) (str "s0 " s0))
        (is (< s0 s1) (str s0 " " s1))))))

(deftest regions-are-filtered-apart
  (let [obs [{:region "A" :year 2002 :known 2002 :value 2.4 :source 0}
             {:region "B" :year 2002 :known 2002 :value 1.7 :source 0}]
        rows (rc/filter-recipe p obs opts)]
    (is (> (:median (at rows "A" 2005)) 2.0))
    (is (< (:median (at rows "B" 2005)) 2.0))
    (is (= #{"A" "B"} (set (map :region rows))))))

(deftest a-recipe-without-data-keeps-its-prior-in-its-regions
  (let [rows (rc/filter-recipe (assoc p :regions ["WLD"]) [] opts)]
    (is (= 11 (count rows)))
    (is (every? #(= "WLD" (:region %)) rows))
    (is (close? (Math/exp (:m (rc/prior p 2005))) (:median (at rows "WLD" 2005)) 1e-9))
    (is (close? 2.0 (:median (at rows "WLD" 2005)) 0.05) "the truncated prior, near the mean")))

(deftest logit-shares
  (let [q {:id "s" :unit "share" :scale :logit :mean 0.6 :lo 0.15 :hi 0.95 :floor 0.0 :step 0.15
           :ref-year 2000 :sources [{:kind :test :sd0 0.15}]}
        obs (for [y (range 2000 2011)] {:region "A" :year y :known y :value 0.3 :source 0})
        rows (rc/filter-recipe q obs opts)]
    (is (close? 0.3 (:mean (at rows "A" 2010)) 0.03))
    (is (every? #(< 0.0 (:mean %) 1.0) rows))))

(deftest extreme-inputs-give-sane-outputs
  (testing "zero ore and tenfold gas (Barlas, PRINCIPLES 8): dropped or flagged, never believed"
    (let [pr (rc/priors)
          gas (rc/recipe pr "ammonia/gas")
          ore (rc/recipe pr "steel-bf/iron-ore")
          gas-obs (for [y (range 1995 2011)] {:region "DE" :year y :known y :source 0 :value 6.7})
          ore-obs (for [y (range 1995 2011)] {:region "WLD" :year y :known y :source 0 :value 0.0})
          g (rc/run gas gas-obs opts)
          o (rc/run ore ore-obs opts)]
      (is (= 16 (:flags g)) "every tenfold observation is flagged")
      (is (every? #(<= 0.5 (:mean %) 0.9) (:rows g)))
      (is (every? zero? (map :n-obs (:rows o))) "a zero cannot be a coefficient on the log")
      (is (every? #(<= 1.4 (:mean %) 1.7) (:rows o))))))

;; ------------------------------------------------- review (bnm-1pq.6 audit)

(deftest a-corrupted-future-observation-changes-nothing-before-it-is-known
  (let [base [{:region "A" :year 2001 :known 2002 :value 2.1 :source 0}
              {:region "A" :year 2004 :known 2005 :value 1.9 :source 0}
              {:region "B" :year 2003 :known 2004 :value 2.2 :source 0}]
        honest (conj base {:region "A" :year 2003 :known 2008 :value 2.0 :source 0})
        ;; the same data year, a later vintage, wild but inside the screen;
        ;; and a corrupted new vintage of a year already known
        corrupt (conj base
                      {:region "A" :year 2003 :known 2008 :value 3.9 :source 0}
                      {:region "A" :year 2001 :known 2009 :value 3.9 :source 0}
                      {:region "B" :year 2003 :known 2009 :value 1.55 :source 0})
        r1 (rc/run p honest opts) r2 (rc/run p corrupt opts)]
    (doseq [y (range 2000 2008) reg ["A" "B"]]
      (is (= (at (:rows r1) reg y) (at (:rows r2) reg y)) (str reg " " y)))
    (testing "and the shared noise known by 2007 is the same"
      (is (= (:noise (rc/run p honest (assoc opts :end 2007)))
             (:noise (rc/run p corrupt (assoc opts :end 2007))))))
    (testing "the corrupted vintage replaces the earlier one when known"
      (is (= 3 (:n-obs (at (:rows r2) "A" 2009))) "2001, 2003 and 2004, not a fourth")
      (is (> (:median (at (:rows r2) "A" 2009)) (:median (at (:rows r1) "A" 2009)))))))

(deftest the-hybrid-coefficient-is-not-used-before-it-is-known
  (testing "a pxp year's implied coefficient carries a(2011): known no earlier than 2014"
    (let [data {:shares {["A" "OUT"] {1995 {"IN" 0.1} 2011 {"IN" 0.1} 2019 {"IN" 0.12}}}
                :prices {"o" {1995 [1.0 1.0] 2011 [1.0 1.0] 2019 [1.0 1.0]}
                         "i" {1995 [1.0 1.0] 2011 [1.0 1.0] 2019 [1.0 1.0]}}}
          obs (#'rc/implied data "A" "OUT" ["IN"] {:out ["o"] :in ["i"]} 2.0)
          by (into {} (map (juxt :year :known) obs))]
      (is (= {1995 2014 2011 2014 2019 2022} by)))))

(deftest truncation-is-of-the-reported-state-only
  (testing "rows are the unconstrained filter's state truncated at the floor (exact for the
            constraint on that year's coefficient, an approximation to one on every year)"
    (let [obs (for [y (range 2000 2011)] {:region "A" :year y :known y :value 1.3 :source 0})
          q (assoc p :floor 0.001)
          free (rc/filter-recipe q obs opts)
          rows (rc/filter-recipe p obs opts)]
      (doseq [y [2003 2010]]
        (let [f (at free "A" y) r (at rows "A" y)]
          (is (close? (:m (rc/truncate (select-keys f [:m :v]) (Math/log 1.5))) (:m r) 1e-9)))))))

(deftest the-screen-is-against-the-prior-and-sd0
  (testing "at :screen-sds predictive sds of the prior plus sd0: just inside is used, just outside flagged"
    (let [sd (Math/sqrt (+ (Math/pow (/ (Math/log (/ 2.5 1.6)) 3.29) 2) 0.04))
          inside (* 2.0 (Math/exp (* 3.9 sd))) outside (* 2.0 (Math/exp (* 4.1 sd)))
          r (rc/run p [{:region "A" :year 2002 :known 2002 :value inside :source 0}
                       {:region "B" :year 2002 :known 2002 :value outside :source 0}] opts)]
      (is (= 1 (:flags r)))
      (is (= 1 (:n-obs (at (:rows r) "A" 2005))))
      (is (= 1 (:n-flagged (at (:rows r) "B" 2005)))))))

(deftest blast-furnace-ore-from-usable-ore-less-dri
  (let [st (fn [stage iso y q k] {:commodity "iron" :stage stage :iso iso :year y :quantity q :known k :source "BGS"})
        data {:stages [(st "mine" "AUS" 2005 1000.0 2007) (st "mine" "CHN" 2005 600.0 2007)
                       (st "smelter" "AUS" 2005 500.0 2007)
                       (st "mine" "AUS" 2012 1000.0 2014) (st "mine" "CHN" 2012 600.0 2014)
                       (st "smelter" "AUS" 2012 500.0 2014)]
              :iron {"china_usable" {2010 [{:edition 2014 :known 2016 :value 200.0}]
                                     2012 [{:edition 2014 :known 2016 :value 180.0}
                                           {:edition 2016 :known 2018 :value 150.0}]}
                     "dri_world" {2005 [{:edition 2007 :known 2009 :value 40.0}]
                                  2012 [{:edition 2014 :known 2016 :value 60.0}]}}}
        crude2010 {:stages (conj (:stages data) (st "mine" "CHN" 2010 800.0 2012))}
        data (merge data crude2010)
        usable (rc/source-obs {} {:kind :bf-ore :china :usable :dri-ore 1.5} data #{"WLD"})
        scaled (rc/source-obs {} {:kind :bf-ore :china :scaled :dri-ore 1.5} data #{"WLD"})]
    (testing "China's usable ore replaces its crude ore, each edition from its known year; DRI ore is taken off"
      (is (= #{[2012 2016 (/ (- (+ 1000.0 180.0) 90.0) 500.0)]
               [2012 2018 (/ (- (+ 1000.0 150.0) 90.0) 500.0)]}
             (set (map (juxt :year :known :value) usable)))))
    (testing "before USGS reports usable ore, crude ore at the first published ratio, known from then"
      (is (= [[2005 2016 (/ (- (+ 1000.0 (* 600.0 (/ 200.0 800.0))) 60.0) 500.0)]]
             (map (juxt :year :known :value) scaled))))))

(deftest drift-learning
  (testing "OLS slope and its variance on the log"
    (let [f (rc/ols-drift [[1995 0.0] [2000 0.5] [2005 1.0] [2008 1.3] [2011 1.6]])]
      (is (close? 0.1 (:slope f) 1e-12))
      (is (close? 0.0 (:se2 f) 1e-12))))
  (testing "empirical Bayes shrinks noisy slopes to the pooled mean, none when they agree"
    (let [fits [{:slope 0.1 :se2 0.0001} {:slope -0.1 :se2 0.0001}
                {:slope 0.5 :se2 1.0} {:slope -0.5 :se2 1.0}]
          s (map :drift (rc/eb-drift fits))]
      (is (close? 0.0 (reduce + s) 1e-9))
      (is (< (Math/abs (nth s 2)) 0.25) "the noisy one is shrunk")
      (is (close? 0.1 (first s) 0.01) "the sharp one is kept")))
  (testing "a lone fit keeps its own slope"
    (is (= [0.3] (map :drift (rc/eb-drift [{:slope 0.3 :se2 0.01}]))))))
