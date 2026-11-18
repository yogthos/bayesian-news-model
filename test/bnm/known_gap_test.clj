(ns bnm.known-gap-test
  (:require [clojure.test :refer [deftest is testing]]
            [bnm.known-gap :as kg]))

(deftest known-gaps-are-found-run-and-reported
  (let [n (create-ns 'bnm.known-gap-test.fixture)]
    (intern n (with-meta 'still-open {:known-gap "a reason"}) (fn [] (is (= 1 2)) (is (= 1 1))))
    (intern n (with-meta 'now-closed {:known-gap "closed"}) (fn [] (is (= 1 1))))
    (intern n (with-meta 'throws {:known-gap "throws"}) (fn [] (throw (ex-info "boom" {}))))
    (intern n 'plain (fn [] (is (= 1 2))))
    (let [rs (into {} (map (juxt #(-> % :var meta :name) identity)) (map kg/run-gap (kg/gaps ['bnm.known-gap-test.fixture])))]
      (testing "only vars marked :known-gap are gaps, and clojure.test does not run them"
        (is (= #{'still-open 'now-closed 'throws} (set (keys rs))))
        (is (every? #(nil? (:test (meta (:var %)))) (vals rs))))
      (testing "a failing assertion keeps the gap open; its reason travels with it"
        (is (= :open (:status (rs 'still-open))))
        (is (= [1 1 0] ((juxt :pass :fail :error) (rs 'still-open))))
        (is (= "a reason" (:reason (rs 'still-open)))))
      (testing "an exception counts as an error, the gap stays open"
        (is (= :open (:status (rs 'throws))))
        (is (= 1 (:error (rs 'throws)))))
      (testing "a gap whose assertions all pass is reported as closed, and fails the suite"
        (is (= :closed (:status (rs 'now-closed))))
        (is (false? (kg/ok? (vals rs))))
        (is (true? (kg/ok? [(rs 'still-open) (rs 'throws)])))))
    (remove-ns 'bnm.known-gap-test.fixture)))

(deftest defgap-defines-a-known-gap-not-a-test
  (kg/defgap a-gap "why" (is (= 1 2)))
  (is (= "why" (:known-gap (meta #'a-gap))))
  (is (nil? (:test (meta #'a-gap))))
  ;; not left behind for the runner to report as a gap of this suite
  (ns-unmap 'bnm.known-gap-test 'a-gap))
