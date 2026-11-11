(ns bnm.dev-folds-test
  (:require [bnm.dev-folds :as df]
            [clojure.set]
            [clojure.test :refer [deftest is testing]]))

(def rows
  "Year-end origins 1990-2009 for three places; label window (t, t+24];
  outcomes in runs of three years, so neighbouring years share them."
  (vec (for [y (range 1990 2010) c ["A" "B" "C"]
             :let [t (+ 11 (* 12 y))]]
         {:place c :t t :lo t :hi (+ t 24) :y (even? (quot (+ y (count c)) 3))})))

(def eps [{:name "gfc" :from 2007 :to 2009} {:name "asia" :from 1997 :to 1998}])

(deftest label-years-and-episodes
  (let [r {:t (+ 11 (* 12 2005)) :lo (+ 11 (* 12 2005)) :hi (+ 11 (* 12 2007))}]
    (is (= #{2006 2007} (df/label-years r)) "(Dec 2005, Dec 2007] touches 2006 and 2007")
    (is (= #{"gfc"} (df/episodes-of r eps)))))

(deftest rolling-folds-keep-a-gap-and-whole-episodes
  (let [folds (df/rolling-folds rows {:test-years (range 2000 2010) :gap 24 :window 24 :episodes eps})]
    (is (= (range 2000 2010) (map :test-year folds)))
    (doseq [{:keys [test-year train test]} folds
            :let [start (* 12 test-year)
                  test-eps (set (mapcat #(df/episodes-of % eps) test))]]
      (testing (str "fold " test-year)
        (is (every? #(= test-year (quot (:t %) 12)) test) "a year's rows are tested together")
        (is (every? #(<= (+ (:t %) 24) start) train) "the gap: training origins end a window before")
        (is (every? #(<= (:hi %) start) train) "no training label reaches into the test block")
        (is (not-any? #(seq (clojure.set/intersection test-eps (df/episodes-of % eps))) train)
            "an episode is never on both sides")))
    (testing "the fold of 2008 drops every training row whose label touches the crisis"
      (let [{:keys [train]} (first (filter #(= 2008 (:test-year %)) folds))]
        (is (seq train))
        (is (every? #(< (quot (:hi %) 12) 2007) (filter #(<= 2004 (quot (:t %) 12)) train)))))
    (testing "a gap shorter than the window is refused"
      (is (thrown? Exception (df/rolling-folds rows {:test-years [2005] :gap 12 :window 24 :episodes eps}))))))

(deftest naive-folds-partition-the-rows-by-seed
  (let [fs (df/naive-folds rows 5 1)
        tests (mapcat :test fs)]
    (is (= 5 (count fs)))
    (is (= (count rows) (count tests)) "each row tested once")
    (is (= (set rows) (set tests)))
    (is (every? (fn [{:keys [train test]}] (empty? (clojure.set/intersection (set train) (set test)))) fs))
    (is (= (map :test fs) (map :test (df/naive-folds rows 5 1))) "the same seed, the same folds")
    (is (not= (map :test fs) (map :test (df/naive-folds rows 5 2))) "another seed, other folds")))

(deftest the-last-fifth-by-time-is-a-development-diagnostic
  (let [{:keys [train test label]} (df/last-share-holdout rows {:share 0.2 :gap 24})
        origins (sort (distinct (map :t rows)))
        cut (nth origins (- (count origins) 4))]
    (is (= :development label) "development, not validation")
    (is (= (set (filter #(>= (:t %) cut) rows)) (set test)) "the last 20% of origins: 4 of 20")
    (is (every? #(<= (+ (:t %) 24) cut) train))))

(deftest the-drop-from-naive-to-grouped-folds
  ;; a fit that has seen a row's own outcome predicts it: naive folds let it
  ;; see its neighbours' years; a fit by place and year leaks under naive folds
  (let [fit (fn [train]
              (let [by (group-by (juxt :place #(quot (:t %) 12)) train)
                    near (fn [{:keys [place t]}]
                           (some #(get by [place %]) [(dec (quot t 12)) (inc (quot t 12))]))]
                (fn [r] (if-let [rs (near r)] (if (:y (first rs)) 0.9 0.1) 0.25))))
        loss (fn [p y] (let [d (- p (if y 1.0 0.0))] (* d d)))
        {:keys [naive grouped drop]} (df/fold-drop rows fit loss {:k 5 :seed 1 :test-years (range 2000 2010)
                                                                   :gap 24 :window 24 :episodes eps})]
    (is (number? naive))
    (is (number? grouped))
    (is (= drop (- grouped naive)))
    (is (pos? drop) "the leak makes naive folds look better")))
