(ns bnm.releases-test
  "bnm-56c.23: each annual source read no later than its releases allow."
  (:require [bnm.releases :as rel]
            [clojure.test :refer [deftest is]]))

(def dec2014 (+ 11 (* 12 2014)))

(deftest the-latest-year-each-source-had-published
  (let [rs (rel/read-releases)]
    (is (= 2010 (rel/published rs "maddison" dec2014)) "MPD 2013 to 2010; MPD 2018 not out")
    (is (= 2007 (rel/published rs "nmc" dec2014)) "NMC 4.0 to 2007; 5.0 came in 2017")
    (is (= 2011 (rel/published rs "pwt" dec2014)) "PWT 8.0 to 2011; 9.0 came in June 2016")
    (is (= 2014 (rel/published rs "pwt" (+ 5 (* 12 2016)))) "9.0 in June 2016")
    (is (= 2011 (rel/published rs "pwt" (+ 4 (* 12 2016)))) "not in May")
    (is (nil? (rel/published rs "nmc" (* 12 1990))) "before the first tabulated release: nil")
    (is (= (+ 6 (* 12 2013)) (rel/introduced rs "pwt" "labsh")) "labour's share from PWT 8.0, July 2013")
    (is (= (+ 5 (* 12 2016)) (rel/introduced rs "pwt" "irr")))))

(deftest a-year-is-read-no-later-than-published
  (let [rs (rel/read-releases)]
    (is (= 2010 (rel/read-year rs "maddison" dec2014 2012)) "the fixed offset's 2012 was not out")
    (is (= 2018 (rel/read-year rs "maddison" (+ 11 (* 12 2020)) 2018)) "already published: the offset stands")
    (is (= 1988 (rel/read-year rs "nmc" (+ 11 (* 12 1990)) 1988)) "before the table: the offset")))
