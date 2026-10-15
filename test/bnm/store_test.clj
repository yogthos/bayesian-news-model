(ns bnm.store-test
  (:require [bnm.model :as model]
            [bnm.model-test :as mt]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.structure :as structure]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]))

(defn- trained [weeks]
  (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
          (model/create (#'mt/toy-structure))
          (map-indexed vector (#'mt/simulate-world weeks 5))))

(deftest round-trips-through-edn
  (let [mdl (trained 60)
        back (-> mdl store/snapshot pr-str edn/read-string store/restore)
        rows (store/->rows mdl)
        from-rows (store/rows->model (edn/read-string (pr-str (:meta rows))) (:nodes rows))]
    (testing "the restored model forecasts exactly as the original"
      (doseq [m [back from-rows]]
        (is (= (vec (aget (:paths (model/forecast mdl {:horizon 4 :sims 3})) 0))
               (vec (aget (:paths (model/forecast m {:horizon 4 :sims 3})) 0))))))
    (testing "and keeps learning from where it left off"
      (let [next-bin (first (#'mt/simulate-world 1 9))
            [a _] (series/observe-bin! mdl 60 next-bin)
            [b _] (series/observe-bin! back 60 next-bin)]
        (is (= (vec (:alpha (model/node a "UKR"))) (vec (:alpha (model/node b "UKR")))))))))

(deftest restructure-keeps-what-was-learned
  (let [mdl (trained 80)
        ;; rebuilt from what was observed: BLR and POL never appear in the data
        rebuilt (structure/build (series/volumes mdl) {:min-dyad-volume 0.0 :min-country-volume 0.0})
        grown (model/restructure mdl rebuilt)]
    (is (= #{"FRA" "RUS" "UKR"} (set (filter #(= 3 (count %)) (map :id (:nodes grown))))))
    (is (= (vec (:mu (model/node mdl "UKR"))) (vec (:mu (model/node grown "UKR")))))
    (let [top-old (first (model/connections mdl "UKR" mt/violence))
          top-new (first (model/connections grown "UKR" mt/violence))]
      (is (= (:source top-old) (:source top-new)))
      (is (= (:alpha top-old) (:alpha top-new))))))
