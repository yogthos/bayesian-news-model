(ns bnm.release-calendar-test
  "t150: a catalogue published once a year (:release {:month m}, UCDP/PRIO's
  armed conflicts) knows a year's onsets at month m of the next year, not
  each onset its :known-lag after its own month."
  (:require [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.world-run :as wr]
            [clojure.test :refer [deftest is testing]]))

(def ^:private aug-2010 (+ 7 (* 12 2010)))
(def ^:private jan-2010 (* 12 2010))
(def ^:private dec-2011 (+ 11 (* 12 2011)))

(def ^:private cat
  {:onsets {"AAA" (sorted-set (* 12 2005) aug-2010)}
   :from {"AAA" (* 12 1990)}
   :through {"AAA" (+ 11 (* 12 2030))}
   :known-lag 18})

(deftest the-month-an-onset-is-known
  (is (= (+ 18 aug-2010) (lb/known-month cat aug-2010)) "no calendar: its lag after the onset")
  (is (= (+ 9 (* 12 2011)) (lb/known-month (assoc cat :release {:month 9}) aug-2010)) "October of the next year")
  (is (= (+ 9 (* 12 2011)) (lb/known-month (assoc cat :release {:month 9}) jan-2010)) "the whole year at once"))

(deftest history-reads-the-calendar
  (testing "at the 18-month lag an August 2010 onset is unknown in December 2011"
    (let [[since c10 c30] (#'lb/history cat "AAA" dec-2011)]
      (is (= 1.0 c10))
      (is (= 1.0 c30))
      (is (< (Math/abs (- since (Math/log (+ 1.0 (/ (- dec-2011 (* 12 2005)) 12.0))))) 1e-12))))
  (testing "released in October 2011, it is known"
    (let [[since c10 c30] (#'lb/history (assoc cat :release {:month 9}) "AAA" dec-2011)]
      (is (= 2.0 c10))
      (is (= 2.0 c30))
      (is (< (Math/abs (- since (Math/log (+ 1.0 (/ (- dec-2011 aug-2010) 12.0))))) 1e-12))))
  (testing "not before the release"
    (is (= 1.0 (nth (#'lb/history (assoc cat :release {:month 9}) "AAA" (+ 8 (* 12 2011))) 1))))
  (testing "the onsets known are a prefix: nothing later is read early"
    (is (= 1.0 (nth (#'lb/history (assoc cat :release {:month 9}) "AAA" (+ 11 (* 12 2010))) 1)))))

(deftest counts-read-the-calendar
  (is (= 1.0 (#'lp/count-known cat "AAA" dec-2011 120)))
  (is (= 2.0 (#'lp/count-known (assoc cat :release {:month 9}) "AAA" dec-2011 120)))
  (is (= 1.0 (#'lp/count-known (assoc cat :release {:month 9}) "AAA" (+ 8 (* 12 2011)) 120))))

(deftest the-release-calendar-option
  (is (nil? (wr/release-calendar-choice {} ["conflict_onset"])))
  (is (= ["conflict_onset" "war_onset"] (wr/release-calendar-choice {:release-calendar "conflict_onset,war_onset"} ["conflict_onset" "war_onset" "coup"])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"--release-calendar: no calendar declared for coup"
                        (wr/release-calendar-choice {:release-calendar "coup"} ["coup"])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"--release-calendar names a kind not run: war_onset"
                        (wr/release-calendar-choice {:release-calendar "war_onset"} ["conflict_onset"])))
  (let [cats {"conflict_onset" cat "coup" cat}]
    (is (= {:month 9} (get-in (wr/with-release-calendar cats ["conflict_onset"]) ["conflict_onset" :release])))
    (is (= cat (get (wr/with-release-calendar cats ["conflict_onset"]) "coup")))
    (is (= cats (wr/with-release-calendar cats nil)))))

(deftest t151-the-calendar-on-the-own-history-alone
  (let [own (assoc cat :release {:month 9 :scope :own})]
    (testing "as another kind's count, a scoped catalogue reads the flat lag"
      (is (= cat (lb/as-other own)))
      (is (= (assoc cat :release {:month 9}) (lb/as-other (assoc cat :release {:month 9}))) "unscoped: the calendar everywhere (t150)")
      (is (= cat (lb/as-other cat))))
    (testing "its own history still reads the calendar"
      (is (= 2.0 (nth (#'lb/history own "AAA" dec-2011) 1))))
    (testing "the others' counts"
      (is (= [1.0] (lp/others-counts {"conflict_onset" own} ["conflict_onset"] "AAA" dec-2011)))
      (is (= [2.0] (lp/others-counts {"conflict_onset" (assoc cat :release {:month 9})} ["conflict_onset"] "AAA" dec-2011)))))
  (testing "the option"
    (is (nil? (wr/release-scope-choice {:release-calendar "conflict_onset"})))
    (is (nil? (wr/release-scope-choice {:release-calendar "conflict_onset" :release-calendar-scope "all"})))
    (is (= :own (wr/release-scope-choice {:release-calendar "conflict_onset" :release-calendar-scope "own"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"--release-calendar-scope own\|all"
                          (wr/release-scope-choice {:release-calendar "conflict_onset" :release-calendar-scope "x"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"needs --release-calendar"
                          (wr/release-scope-choice {:release-calendar-scope "own"}))))
  (is (= {:month 9 :scope :own}
         (get-in (wr/with-release-calendar {"conflict_onset" cat} ["conflict_onset"] :own) ["conflict_onset" :release]))))
