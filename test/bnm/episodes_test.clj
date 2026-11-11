(ns bnm.episodes-test
  "The episode library's invariants (bead bnm-56c.5): every row has a
  source; dates parse; no family is assigned outside the rule table."
  (:require [bnm.episodes :as ep]
            [clojure.test :refer [deftest is testing]]))

(deftest csv-with-quotes
  (is (= [["a" "b,c" "say \"hi\""] ["1" "" ""]]
         (ep/parse-csv "a,\"b,c\",\"say \"\"hi\"\"\"\n1,,\n")))
  (is (= [["a" "b"]] (ep/parse-csv "a,b\r\n")))
  (is (= ["x" "" "\"q\"" "y"] (ep/parse-line "x,,\"\"\"q\"\"\",y"))))

(deftest every-row-from-a-rule
  (let [rules (ep/rules)
        idx (ep/rule-index rules)
        eps (ep/episodes)]
    (is (> (count eps) 1000))
    (is (= (count eps) (count (set (map :row_id eps)))) "row ids unique")
    (doseq [r eps]
      (let [rule (idx [(:catalogue r) (:kind r)])]
        (is (some? rule) (str "no rule for " (:row_id r)))
        (is (= (:family rule) (:family r)) (:row_id r))
        (is (= (:buffers rule) (:buffers r)) (:row_id r))
        (is (not= "-" (:family r)))
        (is (seq (:source r)) (:row_id r))
        (is (ep/date? (:date r)) (:row_id r))
        (is (or (= "" (:end_date r)) (ep/date? (:end_date r))) (:row_id r))
        (is (.startsWith ^String (:episode r) (str (:family r) ":" (:place r) ":")))))))

(deftest tables-well-formed
  (doseq [f ["rules.csv" "episodes.csv" "families.csv" "hindcasts.csv" "mechanisms.csv"]]
    (let [[h & rs] (ep/parse-csv (slurp (str ep/dir "/" f)))]
      (is (every? #(= (count h) (count %)) rs) (str f ": a row with more or fewer fields than the header")))))

(deftest known-no-earlier-than-the-date
  (doseq [r (ep/episodes)]
    (is (ep/date? (:known_date r)) (:row_id r))
    (is (<= (compare (:date r) (:known_date r)) 0) (:row_id r))
    (is (<= (compare (:episode_known r) (:known_date r)) 0) (:row_id r)))
  (is (every? (set (keys (first (ep/episodes)))) ep/outcome-columns)
      "the outcome columns are the episode table's"))

(deftest families-defined-and-counted
  (let [fams (ep/families-of-rules (ep/rules))
        counted (into {} (for [r (ep/families) :when (= "family" (:by r))] [(:name r) (parse-long (:episodes r))]))
        actual (ep/by-family (ep/episodes))]
    (testing "each family of the rule table has episodes, so it can be left out whole"
      (is (= fams (set (keys counted)))))
    (doseq [[f n] counted]
      (is (= n (count (actual f))) f))))

(deftest panel-has-non-events
  (if-let [p (ep/panel)]
    (let [breaks (count (filter #(= "1" (:break %)) p))]
      (is (> (- (count p) breaks) (* 10 breaks)))
      (is (every? #(or (seq (:covered_by %)) (= "1" (:break %))) p))
      (is (every? #(re-matches #"\d{4}-\d{2}" (:known %)) p))
      (is (every? #(<= (compare (str (:year %) "-12") (:known %)) 0) (filter #(= "0" (:break %)) p))
          "a non-event is known no earlier than its year's end"))
    (println "bnm.episodes-test: no data/derived/episodes/panel.csv - panel checks skipped (python -I scripts/episodes.py)")))
