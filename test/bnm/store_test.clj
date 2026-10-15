(ns bnm.store-test
  (:require [bnm.model :as model]
            [bnm.model-test :as mt]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.structure :as structure]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(defn- trained [weeks]
  (reduce (fn [mdl [t bin]] (first (series/observe-bin! mdl t bin)))
          (model/create (#'mt/toy-structure))
          (map-indexed vector (#'mt/simulate-world weeks 5))))

(defn- counting-store
  "A memory store that counts the node rows written to it."
  [written]
  (let [s (store/memory-store)]
    (reify store/ModelStore
      (read-part [_ p] (store/read-part s p))
      (read-nodes [_ ids] (store/read-nodes s ids))
      (node-ids [_] (store/node-ids s))
      (write! [_ parts rows deleted]
        (swap! written + (count rows))
        (store/write! s (update-vals parts (comp store/<-text store/->text)) rows deleted)))))

(deftest round-trips-through-stores
  (let [mdl (assoc (trained 60) :pending {61 {"toy" [["RUS" nil nil nil nil 5 3.0]]}})
        path (str (System/getProperty "java.io.tmpdir") "/bnm-store-test.bin")
        _ (store/write-file mdl path)
        mem (store/memory-store)
        _ (store/save-model! mem mdl)
        stores [(store/read-file path) (store/load-model mem)]]
    (testing "a restored model forecasts exactly as the original"
      (doseq [m stores]
        (is (= (vec (aget (:paths (model/forecast mdl {:horizon 4 :sims 3 :paths? true})) 0))
               (vec (aget (:paths (model/forecast m {:horizon 4 :sims 3 :paths? true})) 0))))
        (is (= (:pending mdl) (:pending m)))))
    (testing "and keeps learning from where it left off"
      (let [next-bin (first (#'mt/simulate-world 1 9))
            [a _] (series/observe-bin! mdl 60 next-bin)
            [b _] (series/observe-bin! (first stores) 60 next-bin)]
        (is (= (vec (:alpha (model/node a "UKR"))) (vec (:alpha (model/node b "UKR")))))))
    (io/delete-file path true)))

(deftest saves-only-what-changed
  (let [written (atom 0)
        st (counting-store written)
        mdl (store/save-model! st (trained 30))
        n (count (:nodes mdl))]
    (is (= n @written) "the first save writes every node")
    (let [mdl (store/save-model! st (assoc mdl :pending {31 {"toy" []}}))]
      (is (= n @written) "with no bin learned, only the buffer is written")
      (is (= {31 {"toy" []}} (store/read-part st :pending)))
      (let [[mdl _] (series/observe-bin! mdl 30 (first (#'mt/simulate-world 1 2)))
            _ (store/save-model! st mdl)]
        (is (= (* 2 n) @written) "after a bin, every node again")))))

(deftest reads-one-node
  (let [mdl (trained 80)
        st (store/memory-store)]
    (store/save-model! st mdl)
    (is (= (take 5 (model/connections mdl "UKR" mt/violence))
           (take 5 (store/node-drivers st "UKR" mt/violence)))
        "a node read on its own has the connections it has in the model")))

(deftest restructure-keeps-what-was-learned
  (let [mdl (trained 80)
        ;; rebuilt from what was observed: BLR and POL never appear in the data
        rebuilt (structure/build (series/volumes mdl) {:min-dyad-volume 0.0 :min-country-volume 0.0})
        grown (model/restructure mdl rebuilt)]
    (is (= #{"FRA" "RUS" "UKR"} (set (filter #(= 3 (count %)) (map :id (:nodes grown))))))
    (is (= (vec (:mu (model/node mdl "UKR"))) (vec (:mu (model/node grown "UKR")))))
    (testing "a connection both structures have keeps its weight"
      (let [self (fn [m] (first (filter #(= "UKR" (:source %)) (model/connections m "UKR" mt/violence))))]
        (is (= (select-keys (self mdl) [:scale :channel :alpha])
               (select-keys (self grown) [:scale :channel :alpha])))))))
