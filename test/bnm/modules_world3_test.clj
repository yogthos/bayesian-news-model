(ns bnm.modules-world3-test
  (:require [bnm.engine :as eng]
            [bnm.modules.world3 :as mw]
            [bnm.world3 :as w3]
            [clojure.edn]
            [clojure.test :refer [deftest is testing]]))

(defn- rel [a b] (/ (Math/abs (- (double a) (double b))) (max 1e-30 (Math/abs (double b)))))

(deftest sectors-side-by-side-are-the-model
  (let [ms (mw/modules)]
    (is (= [:population :capital :agriculture :resources :pollution :indices] (mapv :module ms)))
    (is (empty? (eng/validate ms)))
    (testing "the cross-sector reads: levels first, then rates"
      (is (= {:level [:population :agriculture :resources :capital :pollution :indices]
              :rate [:agriculture :population :capital :resources :pollution :indices]}
             (eng/order ms)))
      (is (= #{[:population :capital :pop] [:resources :capital :fcaor]}
             (set (for [e (eng/edges ms) :when (= :level (:phase e))] [(:from e) (:to e) (:var e)])))
          "capital's output reads the population and the resource fraction at the start of the step"))
    (testing "every stock, flow and auxiliary is one sector's"
      (let [m (w3/model)
            names (set (concat (keys (:stocks m)) (keys (:flows m)) (keys (:aux m))))
            emitted (map :var (mapcat :emits ms))]
        (is (= names (set emitted)))
        (is (= (count emitted) (count (set emitted))))))))

(deftest the-engine-reproduces-the-standard-run
  ;; principle 9: the engine checked on a known answer; World3-03 in six
  ;; modules stepped at dt 0.5 from 1900 to 2100 against bnm.world3's one
  ;; model (the same equations; only the order of summing a stock's flows
  ;; differs)
  (let [mono (w3/run)
        r (eng/run (mw/modules) {:from 1900.0 :to 2100.0})
        series (apply merge (vals (:emits r)))
        worst (fn [part] (reduce max 0.0 (for [[k xs] (part mono) [a b] (map vector (series k) xs)] (rel a b))))]
    (is (= (:t mono) (:t r)))
    (doseq [part [:stocks :flows :aux]]
      (is (< (worst part) 1e-9) (str part " within 1e-9 of the monolithic run")))
    (println "engine World3 vs bnm.world3, max relative difference:"
             (into {} (for [p [:stocks :flows :aux]] [p (worst p)])))))

(deftest a-sectors-parameter-changes-the-coupled-run-as-the-model
  ;; capital's capital-output ratio raised: the other sectors feel it
  ;; through the channels only
  (let [mono (w3/run {:params {:icor1 3.5}})
        r (eng/run (mw/modules) {:from 1900.0 :to 2100.0 :params {:capital {:icor1 3.5}}})
        series (apply merge (vals (:emits r)))]
    (is (not= (last (get-in (w3/run) [:stocks :p1])) (last (get-in mono [:stocks :p1]))) "the change reaches the population")
    (is (< (reduce max 0.0 (for [[k xs] (:stocks mono) [a b] (map vector (series k) xs)] (rel a b))) 1e-9))))

(def golden
  "World3-03's run as it was before the sector refactor (test/resources/
  world3-golden.edn): a digest per variable, exact."
  (clojure.edn/read-string (slurp "test/resources/world3-golden.edn")))

(defn digest [xs]
  (let [xs (mapv double xs)]
    [(reduce + 0.0 xs) (reduce + 0.0 (map-indexed (fn [i x] (* (inc i) x)) xs)) (first xs) (peek xs)]))

(deftest both-runs-match-the-stored-golden
  ;; pinned to the run before the refactor, not to today's code: a change
  ;; to bnm.world3 that moved both runs together would still fail here
  (let [mono (w3/run)
        r (eng/run (mw/modules) {:from 1900.0 :to 2100.0})
        series (apply merge (vals (:emits r)))]
    (is (= (:n golden) (count (:t mono)) (count (:t r))))
    (doseq [[part ds] (:digest golden) [k d] ds]
      (is (= d (digest (get-in mono [part k]))) (str "bnm.world3 " part " " k))
      (is (= d (digest (series k))) (str "engine " part " " k)))))
