(ns bnm.core-test
  (:require [bnm.core :as bnm]
            [bnm.math :as m]
            [bnm.time :as t]
            [clojure.edn :as edn]
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
                    (bnm/create {:structure-opts {:min-dyad-volume 5.0 :min-country-volume 5.0}})
                    (partition-by :date events))]
    (testing "bins close as later events arrive, and the structure grows"
      (is (= (dec bins) (get-in mdl [:stats :bins])) "all but the newest bin are learned")
      (is (contains? (get-in mdl [:structure :tracked]) "RUS|UKR")))
    (testing "the outlook reads as data"
      (let [o (bnm/outlook mdl {:horizon 4 :sims 10})
            r (first (bnm/risks o))]
        (is (seq o))
        (is (<= 0.0 (:p-escalate r) 1.0))))
    (testing "what drives UKR's verbal conflict is visible"
      (is (seq (bnm/connections mdl "UKR" :verbal-conflict))))
    (testing "the model survives a round trip through rows, buffered events included"
      (let [{:keys [meta nodes]} (bnm/->rows mdl)
            back (bnm/load-rows (edn/read-string (pr-str meta)) nodes)]
        (is (= (:pending mdl) (:pending back)))
        (is (= bins (get-in (bnm/flush-bins back) [:stats :bins]))
            "flushing a restored model learns the buffered bin too")))))
