(ns bnm.core-test
  (:require [bnm.core :as bnm]
            [bnm.math :as m]
            [bnm.time :as t]
            [bnm.store]
            [clojure.test :refer [deftest is testing]]))

(defn- daily-events
  "A year of daily events: RUS-UKR fighting that flares, protests in FRA."
  [seed]
  (let [g (m/rng seed)
        start (t/day "20200101")]
    (for [d (range 365)
          :let [date (t/date-str (+ start d))
                flare (if (< 150 d 200) 5.0 1.0)]
          e [{:date date :a1 "RUS" :a2 "UKR" :type :assault :mode :did
              :n (m/poisson! g (* flare 3.0))}
             {:date date :a1 "FRA" :s1 "POL" :a2 "FRA" :s2 "OPP" :adm "FRA.11" :type :coerce :mode :did
              :n (m/poisson! g 3.0)}
             {:date date :a1 "FRA" :a2 nil :type :protest :mode :did :n (m/poisson! g 4.0)}
             {:date date :a1 "USA" :a2 "CHN" :type :consult :mode :did :n (m/poisson! g 6.0)}
             {:date date :a1 "UKR" :a2 nil :type :threaten :mode :did :n 1}]
          :when (pos? (:n e))]
      e)))

(deftest learns-live-and-persists
  (let [events (daily-events 3)
        ;; arrives in daily batches, the way newsroom files a day
        bins (count (distinct (map #(t/bin-of (t/day (:date %)) 7) events)))
        mdl (reduce (fn [mdl day] (bnm/add-events mdl "newsroom" day))
                    (bnm/create {:structure-opts {:min-dyad-volume 5.0 :min-country-volume 5.0
                                                  :min-actor-volume 5.0 :min-pair-volume 5.0
                                                  :min-province-volume 5.0}})
                    (partition-by :date events))]
    (testing "bins close as later events arrive, and the structure grows"
      (is (= (dec bins) (get-in mdl [:stats :bins])) "all but the newest bin are learned")
      (is (contains? (get-in mdl [:structure :tracked :dyads]) "RUS|UKR"))
      (is (contains? (get-in mdl [:structure :tracked :pairs]) "FRA:OPP|FRA:POL"))
      (is (contains? (get-in mdl [:structure :tracked :provinces]) "FRA.11")))
    (testing "the outlook reads as data"
      (let [o (bnm/outlook mdl {:horizon 4 :sims 10})
            r (first (bnm/risks o))]
        (is (seq o))
        (is (<= 0.0 (:p-escalate r) 1.0))))
    (testing "what drives UKR's verbal conflict is visible"
      (is (seq (bnm/connections mdl "UKR" :verbal-conflict))))
    (testing "the model survives a round trip through a store, buffered events included"
      (let [st (bnm.store/memory-store)
            saved (bnm/save! st mdl)
            back (bnm/load st)]
        (is (= (:pending mdl) (:pending back)))
        (is (= bins (get-in (bnm/flush-bins back) [:stats :bins]))
            "flushing a restored model learns the buffered bin too")
        (is (= (map :source (bnm/connections mdl "UKR" :verbal-conflict))
               (map :source (bnm/drivers st "UKR" :verbal-conflict))))))))

(deftest scores-its-forecasts-live-as-the-backtest-does
  (let [events (sort-by :date (concat (daily-events 7)
                                      ;; a quiet country: an event every ten days
                                      (for [d (range 0 365 10)]
                                        {:date (t/date-str (+ (t/day "20200101") d)) :a1 "ISL" :a2 nil
                                         :type :protest :mode :did :n 1})))
        cfg {:period-bins 4 :structure-opts {:min-dyad-volume 5.0 :min-country-volume 5.0}}
        st (bnm.store/memory-store)
        mdl (reduce (fn [mdl day] (bnm/add-events mdl "newsroom" day))
                    (bnm/create cfg) (partition-by :date events))]
    (testing "an outlook is registered on each period's eve, by the model itself"
      (is (not (bnm/period-eve? mdl)))
      (is (< 5 (:periods (get (bnm/record mdl) [:country :violence]) 0))))
    (testing "the registered forecast covers quiet channels too, as the backtest scores them"
      (let [[_ {:keys [rows]}] (last (sort-by key (get-in mdl [:track :forecasts])))]
        (is (contains? (set (keep #(when (= :country (bnm.geo/kind (:id %))) (:id %)) (:nodes mdl))) "ISL"))
        (is (not-any? #(and (= "ISL" (:node %)) (= :violence (:channel %)))
                      (bnm/outlook mdl {:horizon 4 :sims 10}))
            "a reading skips Iceland's violence as too quiet")
        (is (contains? (get rows "ISL") :violence))))
    (testing "its ranges are the sample's quantiles, as the backtest takes them"
      (let [r (first (bnm/outlook mdl {:horizon 4 :sims 10}))]
        (is (<= (:low r) (:median r) (:high r)))))
    (testing "an outlook that doesn't cover a period exactly isn't"
      (let [o (bnm/outlook mdl {:horizon 3 :sims 5})]
        (is (identical? (:track mdl) (:track (bnm/register mdl o))))))
    (testing "the periods over were scored, and the history kept"
      (is (seq (get-in mdl [:track :hist "UKR"])))
      (is (<= (count (get-in mdl [:track :hist "UKR"])) 6))
      (is (seq (:calibration mdl)) "the outcomes recalibrated it")
      (is (seq (:spread mdl)) "and taught it how wide its ranges should be")
      (is (seq (get-in mdl [:track :clim])))
      (let [r (get (bnm/record mdl) [:country :violence])]
        (is (pos? (:n r)))
        (is (number? (:brier-cal r)))
        (is (number? (:brier-clim r)))
        (testing "with how often the ranges held the outcome, and which side missed"
          (is (every? #(<= 0.0 (% r) 1.0) [:cover80 :below :above]))
          (is (< (Math/abs (- 1.0 (+ (:cover80 r) (:below r) (:above r)))) 1e-9)))))
    (testing "an outlook measures escalation from the last period, with its benchmarks"
      (let [r (first (filter #(and (= "UKR" (:node %)) (= :violence (:channel %)))
                             (bnm/outlook mdl {:horizon 4 :sims 10})))]
        (is (= (peek (mapv #(nth % 5) (get-in mdl [:track :hist "UKR"]))) (:last r)))
        (is (#{:quiet :low :active} (:stratum r)))
        (is (<= 0.0 (:p-clim r) 1.0))
        (is (<= 0.0 (:p-hist r) 1.0))
        (is (= (:bin mdl) (:bin r)))))
    (testing "the track survives a store, and a registration alone is saved"
      (let [saved (bnm/save! st mdl)
            back (bnm/load st)]
        (is (= (bnm/record mdl) (bnm/record back)))
        (is (= (:spread mdl) (:spread back)))
        (is (= (mapv vec (get-in mdl [:track :hist "UKR"])) (mapv vec (get-in back [:track :hist "UKR"]))))
        (let [marked (assoc-in saved [:track :forecasts 999] {:rows {}})]
          (bnm/save! st marked)
          (is (contains? (get-in (bnm/load st) [:track :forecasts]) 999)))))))

(deftest catching-up-in-bulk-still-scores-each-period
  ;; a model brought up to date many bins at once (the pipeline's catch-up)
  ;; must forecast on each period's eve as it passes it and score those
  ;; forecasts, or its calibration and ranges never learn from live use
  (let [events (daily-events 7)
        cfg {:period-bins 4 :structure-opts {:min-dyad-volume 5.0 :min-country-volume 5.0}}
        warm (bnm/add-events (bnm/create cfg) "newsroom" (take-while #(neg? (compare (:date %) "20200301")) events))
        mdl (bnm/add-events warm "newsroom" (drop-while #(neg? (compare (:date %) "20200301")) events))]
    (is (seq (:calibration mdl)) "the outcomes recalibrated it")
    (is (seq (:spread mdl)) "and taught it how wide its ranges should be")
    (is (< 5 (:periods (get (bnm/record mdl) [:country :violence]) 0)))))
