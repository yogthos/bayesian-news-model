(ns bnm.model-test
  (:require [bnm.events :as ev]
            [bnm.math :as m]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [bnm.series :as series]
            [bnm.structure :as structure]
            [clojure.test :refer [deftest is testing]]))

(def violence (ont/channel-index :violence))
(def protest (ont/channel-index :protest))

(defn- toy-structure []
  (-> (structure/volumes)
      (structure/tally "RUS" "UKR" 500.0)
      (structure/tally "RUS" "BLR" 200.0)
      (structure/tally "UKR" "POL" 300.0)
      (structure/tally "FRA" nil 400.0)
      (structure/build {:min-dyad-volume 100.0})))

(deftest structure-is-bottom-up
  (let [s (toy-structure)
        levels (map :level (:nodes s))]
    (is (= levels (sort levels)))
    (is (contains? (get-in s [:tracked :dyads]) "RUS|UKR"))
    (is (some #(= "world" (:id %)) (:nodes s)))
    (testing "a country's children are its rest leaf and its tracked dyads"
      (is (= #{"UKR:*" "RUS|UKR" "POL|UKR"}
             (set (:children (first (filter #(= "UKR" (:id %)) (:nodes s))))))))))

(deftest actors-pairs-and-provinces
  (let [s (-> (structure/volumes)
              (structure/tally "UKR" "GOV" "UKR" "OPP" "UKR.12" 300.0)
              (structure/tally "UKR" "POL" "UKR" "OPP" "UKR.12" 200.0)
              (structure/tally "UKR" "GOV" "RUS" "MIL" nil 400.0)
              (structure/tally "RUS" "GOV" "RUS" nil "RUS.48" 100.0)
              (structure/build {:min-dyad-volume 100.0 :min-actor-volume 100.0
                                :min-pair-volume 100.0 :min-province-volume 100.0}))
        node (fn [id] (first (filter #(= id (:id %)) (:nodes s))))
        tracked (:tracked s)]
    (is (= (map :level (:nodes s)) (sort (map :level (:nodes s)))))
    (is (= #{"UKR:GOV|UKR:OPP" "UKR:OPP|UKR:POL"} (:pairs tracked)))
    (is (= #{"UKR.12" "RUS.48"} (:provinces tracked)))
    (testing "a pair's parents are its actors, an actor's children its pairs"
      (is (= ["UKR:GOV" "UKR:OPP"] (:parents (node "UKR:GOV|UKR:OPP"))))
      (is (= #{"UKR:GOV|UKR:OPP" "UKR:OPP|UKR:POL"} (set (:children (node "UKR:OPP"))))))
    (testing "a country's children are its leaves; provinces are not among them"
      (is (= #{"UKR:*" "RUS|UKR" "UKR:GOV|UKR:OPP" "UKR:OPP|UKR:POL"} (set (:children (node "UKR")))))
      (is (= ["UKR"] (:parents (node "UKR.12")))))
    (testing "an event counts for its leaf, actors, province and places"
      (is (= #{"UKR:GOV|UKR:OPP" "UKR:GOV" "UKR:OPP" "UKR.12" "UKR" "s/eastern-europe" "r/europe" "world"}
             (ev/nodes "UKR" "GOV" "UKR" "OPP" "UKR.12" tracked)))
      (is (= #{"UKR:*" "UKR:GOV" "UKR" "s/eastern-europe" "r/europe" "world"}
             (ev/nodes "UKR" "GOV" "UKR" "BUS" nil tracked))
          "an untracked pair falls to the rest leaf"))))

(defn- simulate-world
  "Weekly records where RUS:* violence spikes at random and UKR:* violence
  follows a week later; FRA protests are steady noise."
  [weeks seed]
  (let [g (m/rng seed)]
    (loop [t 0 prev 0.0 out []]
      (if (< t weeks)
        (let [rus (if (< (m/next-double! g) 0.2) (+ 20.0 (m/poisson! g 30.0)) (double (m/poisson! g 2.0)))
              ukr (double (m/poisson! g (+ 1.0 (* 0.8 prev))))
              fra (double (m/poisson! g 10.0))
              background (double (m/poisson! g 200.0))]
          (recur (inc t) rus
                 (conj out {"toy" [["RUS" nil nil nil nil violence rus]
                                   ["UKR" nil nil nil nil violence ukr]
                                   ["FRA" nil nil nil nil protest fra]
                                   ["FRA" nil nil nil nil 0 background]]})))
        out))))

(deftest learns-a-lagged-connection
  (let [mdl (series/with-routes (model/create (toy-structure) {:decays [0.0 0.9]}))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    mdl (map-indexed vector (simulate-world 400 7)))
        {:keys [model persistence]} (model/skill mdl)]
    (testing "UKR violence is explained by last week's RUS violence"
      (doseq [[id src] [["UKR" "RUS"] ["UKR:*" "RUS:*"]]]
        (let [top (first (model/connections mdl id violence))]
          (is (= [src :violence] [(:source top) (:channel top)]))
          (is (> (:share top) 0.3)))))
    (testing "alphas stay bounded for sources that never fire"
      (is (every? #(< % 100.0) (:alpha (model/node mdl "UKR")))))
    (testing "one-step predictions beat carrying the last week forward"
      (is (< model persistence)))))

(deftest forecast-is-deterministic-and-shaped
  (let [mdl (series/with-routes (model/create (toy-structure)))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    mdl (map-indexed vector (simulate-world 100 3)))
        a (model/forecast mdl {:horizon 8 :sims 5 :seed 11 :paths? true})
        b (model/forecast mdl {:horizon 8 :sims 5 :seed 11})
        i ((:index mdl) "world")]
    (is (= (vec (aget (:totals a) i)) (vec (aget (:totals b) i))) "deterministic, paths or not")
    (is (= (* 5 8 model/C) (alength ^doubles (aget (:paths a) i))))
    (is (every? #(and (not (Double/isNaN %)) (>= % 0.0)) (aget (:paths a) i)))
    (testing "totals are the paths summed"
      (doseq [s (range 5) c (range model/C)]
        (is (< (Math/abs (- (model/total a i s c) (model/path-sum a i s c 0 8))) 1e-9))))))

(deftest prior-predicts-the-typical-rate
  ;; a prior predictive check: with no evidence yet and every node at its
  ;; typical rate, the prior alone predicts exactly that rate, whatever a
  ;; node's number of sources
  (let [s (-> (structure/volumes)
              (structure/tally "UKR" "GOV" "UKR" "OPP" "UKR.12" 300.0)
              (structure/tally "UKR" "GOV" "RUS" "MIL" nil 400.0)
              (structure/tally "UKR" nil "POL" nil nil 300.0)
              (structure/tally "RUS" nil "BLR" nil nil 300.0)
              (structure/build {:min-dyad-volume 100.0 :min-actor-volume 100.0
                                :min-pair-volume 100.0 :min-province-volume 100.0}))
        m (model/create s)
        sc (* (model/scales (:config m)) model/C)]
    (doseq [n (:nodes m)]
      (java.util.Arrays/fill ^doubles (:ybar n) 2.5)
      (java.util.Arrays/fill ^doubles (:h n) 2.5))
    (model/refresh! m)
    (let [hs (object-array (map :h (:nodes m)))]
      (doseq [n (:nodes m)]
        (let [out (double-array model/C)]
          (#'model/own-rate! n hs out sc)
          (is (every? #(< (Math/abs (- % 2.5)) 1e-9) out) (:id n)))))))

(deftest forecasts-carry-parameter-uncertainty
  ;; the toy's nodes all estimate branching past 0.95, and at the cap the
  ;; posterior has mass only below the estimate; above it, draws widen
  (let [mdl (series/with-routes (model/create (toy-structure) {:max-branching 2.0}))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    mdl (map-indexed vector (simulate-world 60 3)))
        i ((:index mdl) "UKR")
        c violence
        stats (fn [m]
                (let [fc (model/forecast m {:horizon 13 :sims 400 :seed 5})
                      xs (map #(model/total fc i % c) (range 400))
                      mean (/ (reduce + xs) 400.0)]
                  [mean (/ (reduce + (map #(let [d (- % mean)] (* d d)) xs)) 400.0)]))
        [m-draw v-draw] (stats mdl)
        [m-point v-point] (stats (assoc-in mdl [:config :param-uncertainty] false))]
    (is (> v-draw v-point) "drawing the parameters widens the forecast")
    (is (< (Math/abs (- 1.0 (/ m-draw m-point))) 0.2) "without moving its centre much")))

(deftest pooled-priors-stay-centred
  (let [mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    (series/with-routes (model/create (toy-structure)))
                    (map-indexed vector (simulate-world 80 4)))
        pool (model/learn-pool mdl)
        fresh (model/create (:structure mdl) {} pool)
        sc (* (model/scales (:config fresh)) model/C)]
    (testing "the pool has learned shares for each kind of node it saw"
      (is (contains? (:mu pool) :country))
      (is (contains? (:shares pool) [:country :self]))
      (is (every? #(<= 0.02 % 0.8) (get-in pool [:mu :country]))))
    (testing "and its priors still predict exactly the typical rate"
      (doseq [n (:nodes fresh)]
        (java.util.Arrays/fill ^doubles (:ybar n) 3.0)
        (java.util.Arrays/fill ^doubles (:h n) 3.0))
      (model/refresh! fresh)
      (let [hs (object-array (map :h (:nodes fresh)))]
        (doseq [n (:nodes fresh)]
          (let [out (double-array model/C)]
            (#'model/own-rate! n hs out sc)
            (is (every? #(< (Math/abs (- % 3.0)) 1e-9) out) (:id n))))))))

(deftest parameter-draws-do-not-inflate-the-mean
  ;; drawn branching ratios past one explode: the stationarity imposed on the
  ;; estimates holds for every draw, so the draws widen the forecast without
  ;; lifting its mean, even where evidence is thin
  (let [mdl (series/with-routes (model/create (toy-structure)))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    mdl (map-indexed vector (simulate-world 20 9)))
        sims 400
        total (fn [m]
                (let [fc (model/forecast m {:horizon 52 :sims sims :seed 3})]
                  (reduce + (for [i (range (count (:ids fc))) s (range sims) c (range model/C)]
                              (model/total fc i s c)))))
        drawn (total mdl)
        point (total (assoc-in mdl [:config :param-uncertainty] false))]
    (is (< (/ drawn point) 1.1) [drawn point])))

(defn- sparse-world
  "Weekly records: BLR protests a Poisson(0.3) count a week, against a steady
  background that sets the source's exposure."
  [weeks seed]
  (let [g (m/rng seed)]
    (vec (for [_ (range weeks)]
           {"toy" [["BLR" nil nil nil nil protest (double (m/poisson! g 0.3))]
                   ["FRA" nil nil nil nil 0 (double (m/poisson! g 500.0))]]}))))

(deftest counts-are-drawn-at-their-own-scale
  ;; a sparse series is Poisson in raw counts: its dispersion is learned as
  ;; such, and its forecast has quiet weeks as often as the data does
  (let [s (-> (structure/volumes)
              (structure/tally "BLR" nil 300.0)
              (structure/tally "FRA" nil 400.0)
              (structure/build {:min-dyad-volume 100.0}))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    (series/with-routes (model/create s))
                    (map-indexed vector (sparse-world 300 5)))
        u (model/unit mdl)
        i ((:index mdl) "BLR:*")
        node (model/node mdl "BLR:*")
        sims 200 horizon 13
        fc (model/forecast mdl {:horizon horizon :sims sims :seed 2 :paths? true})
        p (aget ^objects (:paths fc) i)
        weeks (for [s (range sims) t (range horizon)]
                (aget ^doubles p (+ (* s horizon model/C) (* t model/C) protest)))
        zeros (/ (count (filter zero? weeks)) (double (count weeks)))]
    (testing "one raw event is worth the unit"
      (is (< (Math/abs (- (/ 1.0 u) (/ 500.3 1000.0))) 0.05)))
    (testing "dispersion in raw counts is about Poisson"
      (is (< 0.6 (/ (aget ^doubles (:disp node) protest) u) 1.8)))
    (testing "quiet weeks come out about as often as Poisson(0.3) gives them"
      (is (< 0.6 zeros 0.85) zeros))
    (testing "and every week is a whole number of events"
      (is (every? #(< (Math/abs (- (/ % u) (Math/rint (/ % u)))) 1e-6) weeks)))))

(deftest forecasts-hold-the-local-level
  ;; protests halve and stay there: a quarter ahead is forecast at the new
  ;; level, not pulled back toward the two-year mean
  (let [g (m/rng 8)
        week (fn [rate] {"toy" [["FRA" nil nil nil nil protest (double (m/poisson! g rate))]
                                ["RUS" nil nil nil nil 0 (double (m/poisson! g 500.0))]]})
        bins (concat (repeatedly 150 #(week 40.0)) (repeatedly 40 #(week 20.0)))
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    (series/with-routes (model/create (toy-structure)))
                    (map-indexed vector bins))
        u (model/unit mdl)
        i ((:index mdl) "FRA:*")
        sims 200
        fc (model/forecast mdl {:horizon 13 :sims sims :seed 4})
        mean (/ (reduce + (map #(model/total fc i % protest) (range sims))) sims)]
    (is (< 0.85 (/ mean (* 13 20.0 u)) 1.15) (/ mean (* 13 20.0 u)))))

(deftest iid-counts-are-mostly-baseline
  ;; Poisson counts with no clustering at all: the level must be the baseline's,
  ;; not excitation's (Filimonov & Sornette 2015: a slow kernel otherwise
  ;; absorbs the level and the branching ratio comes out near one)
  (let [g (m/rng 11)
        week (fn [] {"toy" [["FRA" nil nil nil nil protest (double (m/poisson! g 40.0))]
                            ["RUS" nil nil nil nil 0 (double (m/poisson! g 500.0))]]})
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    (series/with-routes (model/create (toy-structure)))
                    (map-indexed vector (repeatedly 200 week)))
        i ((:index mdl) "FRA:*")
        node ((:nodes mdl) i)
        k (count (:sources node))
        sc (* (model/scales (:config mdl)) model/C)
        out (double-array model/C)]
    (#'model/excitation-shares! out (:alpha node)
                                (object-array (map #(:ybar ((:nodes mdl) (aget ^ints (:src node) %))) (range k)))
                                (:ybar node) k sc)
    (is (< (aget out protest) 0.5) (aget out protest))))

(deftest country-forecasts-are-their-children-summed
  ;; coherent bottom-up: a country's simulated total is the sum of its
  ;; children's in the same simulation (bnm.reconcile: lower CRPS than the
  ;; country's own blend in every channel)
  (let [mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    (series/with-routes (model/create (toy-structure)))
                    (map-indexed vector (simulate-world 30 5)))
        fc (model/forecast mdl {:horizon 8 :sims 20 :seed 3 :paths? true})
        i ((:index mdl) "UKR")
        kids (vec (:children ((:nodes mdl) i)))]
    (doseq [s (range 20) c (range model/C)]
      (is (< (Math/abs (- (model/total fc i s c) (reduce + (map #(model/total fc % s c) kids)))) 1e-9))
      (is (< (Math/abs (- (model/total fc i s c) (model/path-sum fc i s c 0 8))) 1e-9)))))

(deftest kappa-scales-children-up-to-the-parent
  ;; an actor's children are only its pairs within the country, here a tenth
  ;; of its events: kappa must learn that tenth whatever the node's volume,
  ;; not stay near one, or the blend pulls the actor's forecast down
  (let [s (-> (structure/volumes)
              (structure/tally "UKR" "GOV" "UKR" "OPP" nil 300.0)
              (structure/tally "UKR" "GOV" "RUS" "MIL" nil 3000.0)
              (structure/tally "USA" nil nil nil nil 5000000.0)
              (structure/build {:min-dyad-volume 100.0 :min-actor-volume 100.0 :min-pair-volume 100.0}))
        g (m/rng 3)
        week (fn [] {"toy" [["UKR" "GOV" "UKR" "OPP" nil violence (double (m/poisson! g 4.0))]
                            ["UKR" "GOV" "RUS" "MIL" nil violence (double (m/poisson! g 36.0))]
                            ["USA" nil nil nil nil 0 (double (m/poisson! g 500000.0))]]})
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    (series/with-routes (model/create s))
                    (map-indexed vector (repeatedly 200 week)))
        node (model/node mdl "UKR:GOV")]
    (is (= 1 (count (:children node))))
    (is (< 7.0 (aget ^doubles (:kappa node) violence) 13.0) (aget ^doubles (:kappa node) violence))))

(deftest a-persistent-shock-lengthens-quiet-nodes-upper-tails
  ;; one log-normal multiplier per path and node, held over the horizon, its
  ;; log-sd near :shock-sd for a node far quieter than :shock-volume raw events
  ;; a bin and fading for busier ones (a pooled variance trend)
  (is (< (Math/abs (- 0.5 (model/shock-sd 0.5 10.0 0.01))) 0.01))
  (is (< (model/shock-sd 0.5 10.0 10000.0) 0.02))
  (let [s (-> (structure/volumes)
              (structure/tally "UKR" "GOV" "UKR" "OPP" nil 300.0)
              (structure/tally "USA" nil nil nil nil 5000000.0)
              (structure/build {:min-dyad-volume 100.0 :min-actor-volume 100.0 :min-pair-volume 100.0}))
        g (m/rng 5)
        week (fn [] {"toy" [["UKR" "GOV" "UKR" "OPP" nil violence (double (m/poisson! g 2.0))]
                            ["USA" nil nil nil nil violence (double (m/poisson! g 500000.0))]]})
        mdl (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
                    (series/with-routes (model/create s))
                    (map-indexed vector (repeatedly 150 week)))
        quantiles (fn [mdl id]
                    (let [fc (model/forecast mdl {:horizon 13 :sims 400 :seed 9})
                          i (.indexOf ^java.util.List (:ids fc) id)
                          xs (double-array (sort (for [k (range 400)] (model/total fc i k violence))))]
                      (mapv #(m/quantile xs %) [0.1 0.5 0.9])))
        upper (fn [[_ q50 q90]] (Math/log (/ q90 q50)))
        plain (quantiles mdl "UKR:GOV")]
    (is (> (upper (quantiles (assoc-in mdl [:config :shock] {:sd 0.6 :volume 50.0}) "UKR:GOV"))
           (+ 0.2 (upper plain)))
        "the quiet node's upper tail lengthens")
    (is (= plain (quantiles (assoc-in mdl [:config :shock] {:sd 0.6 :volume 1e-12}) "UKR:GOV"))
        "a shock faded to nothing changes nothing")
    (is (= plain (quantiles (-> mdl
                                (assoc-in [:config :shock] {:sd 0.6 :volume 50.0})
                                (assoc :spread (into {} (for [k [:actor :pair :rest :country :subregion :region :world]]
                                                         [[:shock k :violence] {:sd 0.0}]))))
                            "UKR:GOV"))
        "a learned sd for each kind and channel overrides the configured one")))
