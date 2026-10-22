(ns bnm.half-decade-test
  (:require [augur.math :as m]
            [bnm.credit-run :as cr]
            [bnm.half-decade :as hd]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(defn- synthetic-jst
  "A JST-like panel, 1870-2020, for `isos`: random walks for credit, house
  and equity prices, crises at random (about 4% a year). `late-crisis`
  sets every crisis flag from 1996 on (a function of iso and year)."
  [isos seed & {:keys [late-crisis]}]
  (let [g (m/rng seed)]
    (vec (for [iso isos
               :let [state (atom {:d 0.4 :hp 100.0 :cpi 10.0})]
               yr (range 1870 2021)]
           (let [{:keys [d hp cpi]} @state
                 d' (max 0.05 (+ d (* 0.02 (m/normal! g))))
                 cpi' (* cpi (+ 1.02 (* 0.01 (m/normal! g))))
                 hp' (* hp (+ 1.03 (* 0.05 (m/normal! g))))
                 c (if (< (m/next-double! g) 0.04) 1.0 0.0)]
             (reset! state {:d d' :hp hp' :cpi cpi'})
             {:iso iso :year yr :gdp 100.0 :tloans (* 100.0 d') :hpnom hp' :cpi cpi'
              :eq_capgain (* 0.1 (m/normal! g)) :ca (* 3.0 (m/normal! g)) :ltrate (+ 4.0 (m/next-double! g))
              :crisisJST (if (and late-crisis (>= yr 1996)) (late-crisis iso yr) c)})))))

(def isos ["AAA" "BBB" "CCC" "DDD" "EEE" "FFF"])

;; --- eras --------------------------------------------------------------------

(deftest eras-parse-and-validate
  (is (= [{:name "a" :from 1870 :to 1913} {:name "1919-1938" :from 1919 :to 1938}]
         (hd/parse-eras "a:1870-1913,1919-1938")))
  (is (= {:name "t" :from 1996 :to 2020} (hd/parse-era "t:1996-2020")))
  (testing "development eras must end before the test era opens"
    (is (thrown? Exception (hd/validate-eras! [{:name "a" :from 1870 :to 2000}] {:name "t" :from 1996 :to 2020})))
    (is (thrown? Exception (hd/validate-eras! [{:name "a" :from 1870 :to 1913} {:name "b" :from 1900 :to 1938}]
                                              {:name "t" :from 1996 :to 2020}))
        "overlapping eras")
    (is (nil? (hd/validate-eras! hd/default-eras hd/default-test-era)))))

;; --- the test era stays unread -----------------------------------------------------

(deftest masking-removes-every-flag-from-the-cutoff
  (let [jst (synthetic-jst isos 3)
        masked (hd/mask-outcomes jst 1996)]
    (is (every? #(not (contains? % :crisisJST)) (filter #(>= (:year %) 1996) masked)))
    (is (= (filter #(< (:year %) 1996) jst) (filter #(< (:year %) 1996) masked)))))

(deftest development-never-reads-the-test-era
  ;; the same panel with every 1996-2020 crisis flag on, or off: the
  ;; development rows, their labels and every development score are the same
  (let [opts {:eras hd/default-eras :test-era hd/default-test-era :draws 0 :boot 0}
        on (synthetic-jst isos 5 :late-crisis (constantly 1.0))
        off (synthetic-jst isos 5 :late-crisis (constantly 0.0))
        rows-on (hd/dev-rows on opts)
        rows-off (hd/dev-rows off opts)]
    (is (seq rows-on))
    (is (= rows-on rows-off))
    (is (every? #(< (+ (:year %) hd/H) 1996) rows-on) "no outcome window reaches 1996")
    (is (every? #(not= "1996-2020" (:era %)) rows-on))
    (is (= (hd/loeo-scores rows-on hd/joint opts) (hd/loeo-scores rows-off hd/joint opts)))
    (testing "the guard: a row whose window reaches the test era is refused"
      (is (thrown? Exception (hd/check-dev! (conj (vec rows-on) (assoc (first rows-on) :year 1992)) hd/default-test-era))))
    (testing "the test mode's training rows are the development rows"
      (let [{:keys [train]} (hd/test-rows on (assoc opts :confirm true))]
        (is (= (set rows-on) (set train)))))
    (testing "test mode needs the registration confirmed"
      (is (thrown? Exception (hd/test-rows on opts))))))

(deftest folds-embargo-windows-that-reach-the-held-out-era
  ;; held out 1919-1938 with five-year windows: a training row's window
  ;; (t, t + 5] may not touch the held-out outcome years 1920-1943
  (let [era {:name "interwar" :from 1919 :to 1938}
        rows (for [y [1910 1914 1915 1918 1919 1938 1939 1942 1943 1946]] {:year y :era (if (<= 1919 y 1938) "interwar" "x")})
        train (hd/train-rows rows era)]
    ;; 1915's window ends in 1920, 1942's starts in 1943
    (is (= [1910 1914 1943 1946] (mapv :year train)))))

;; --- outcomes ------------------------------------------------------------------

(deftest labels-and-risk
  (let [jst (concat (for [y (range 1900 1931)] {:iso "AAA" :year y :crisisJST (if (#{1905 1917} y) 1.0 0.0)})
                    (for [y (range 1900 1931)] {:iso "BBB" :year y}))
        cs (hd/countries jst)
        a (cs "AAA")]
    (is (true? (hd/window-label a 1900 1 5)) "1905 is in (1900, 1905]")
    (is (false? (hd/window-label a 1905 1 5)) "the origin's own year is not ahead")
    (is (= [false false false false true] (mapv #(hd/window-label a 1900 % %) [1 2 3 4 5])))
    (is (nil? (hd/window-label a 1928 1 5)) "past the coverage: unknown")
    (is (nil? (hd/window-label (cs "BBB") 1900 1 5)) "no flags: unknown")
    (is (not (hd/at-risk? a 1906)) "a crisis in the last two years")
    (is (not (hd/at-risk? a 1905)))
    (is (hd/at-risk? a 1908))))

;; --- the states are known at the origin ------------------------------------------------

(deftest states-are-causal
  ;; changing anything in the origin's year or after changes no state
  (let [jst (synthetic-jst ["AAA"] 11)
        t 1950
        cs (hd/countries jst)
        scramble (fn [r] (if (>= (:year r) t)
                           (assoc r :tloans 999.0 :hpnom 1.0 :cpi 77.0 :eq_capgain 5.0 :ca -50.0 :ltrate 30.0 :gdp 3.0)
                           r))
        cs' (hd/countries (mapv scramble jst))
        s (hd/states-at (cs "AAA") t)
        s' (hd/states-at (cs' "AAA") t)]
    (is (= (set hd/state-names) (set (keys s))))
    (is (every? some? (vals s)))
    (is (= s s'))
    (testing "but the year before the origin is read"
      (let [cs'' (hd/countries (mapv #(if (= (:year %) (dec t)) (assoc % :hpnom 1.0) %) jst))]
        (is (not= (:house s) (:house (hd/states-at (cs'' "AAA") t))))))))

(deftest growth-states-are-three-year-real-changes
  (let [rows (for [y (range 1900 1911)] {:iso "A" :year y :crisisJST 0.0 :cpi (Math/pow 1.1 (- y 1900))
                                          :hpnom (* 2.0 (Math/pow 1.2 (- y 1900))) :eq_capgain 0.1
                                          :ca (- y 1900.0) :gdp 100.0})
        c ((hd/countries rows) "A")
        s (hd/states-at c 1910)]
    ;; data years 1906-1909: real house prices grow by log(1.2/1.1) a year
    (is (close? (* 3 (Math/log (/ 1.2 1.1))) (:house s) 1e-9))
    (is (close? (* 3 (- (Math/log 1.1) (Math/log 1.1))) (:equity s) 1e-9) "10% nominal gains at 10% inflation")
    (is (close? (/ (+ 7 8 9) 300.0) (:current-account s) 1e-12) "the mean of 1907-1909, over GDP")))

;; --- the credit gap ----------------------------------------------------------------

(defn- dense-hp
  "The HP trend by plain Gaussian elimination on (I + lambda D'D) tau = y."
  [ys lam]
  (let [n (count ys)
        dtd (fn [i j] (reduce + (for [r (range (- n 2))
                                      :let [ci (case (- i r) 0 1.0 1 -2.0 2 1.0 0.0)
                                            cj (case (- j r) 0 1.0 1 -2.0 2 1.0 0.0)]]
                                  (* ci cj))))
        a (vec (for [i (range n)] (conj (vec (for [j (range n)] (+ (if (= i j) 1.0 0.0) (* lam (dtd i j))))) (double (ys i)))))
        elim (reduce (fn [a k]
                       (reduce (fn [a i]
                                 (let [f (/ (get-in a [i k]) (get-in a [k k]))]
                                   (assoc a i (mapv - (a i) (map #(* f %) (a k))))))
                               a (range (inc k) n)))
                     a (range n))]
    (reduce (fn [x i]
              (let [s (reduce + (for [j (range (inc i) n)] (* (get-in elim [i j]) (x j))))]
                (assoc x i (/ (- (get-in elim [i n]) s) (get-in elim [i i])))))
            (vec (repeat n 0.0)) (reverse (range n)))))

(deftest the-gap-is-the-one-sided-hp-gap-of-jst-credit
  (let [jst (synthetic-jst ["AAA"] 17)
        t 1930
        c ((hd/countries jst) "AAA")
        ;; data years 1870-1929 (the latest is published at the end of the next year)
        xs (mapv #(/ (:tloans %) (:gdp %)) (filter #(< (:year %) t) (sort-by :year jst)))
        tau (dense-hp xs cr/annual-lambda)
        expected (* 100.0 (- (peek xs) (peek tau)))]
    (is (close? expected (:credit-gap (hd/states-at c t)) 1e-6))
    (testing "the banded solver agrees with the dense one"
      (is (every? true? (map #(close? %1 %2 1e-8) tau (cr/hp-trend xs cr/annual-lambda)))))
    (testing "a credit series on a straight line has no gap"
      (let [line (for [y (range 1870 1931)] {:iso "L" :year y :crisisJST 0.0 :gdp 100.0 :tloans (+ 20.0 (* 0.5 (- y 1870)))})]
        (is (close? 0.0 (:credit-gap (hd/states-at ((hd/countries line) "L") 1930)) 1e-6))))
    (testing "fewer than ten data years: no gap"
      (let [short (for [y (range 1920 1928)] {:iso "S" :year y :crisisJST 0.0 :gdp 100.0 :tloans 30.0})]
        (is (nil? (:credit-gap (hd/states-at ((hd/countries short) "S") 1928))))))))

;; --- scoring --------------------------------------------------------------------

(deftest gains-per-row
  (let [recs [{:y true :p0 0.5 :p1 0.8} {:y false :p0 0.5 :p1 0.2}]
        g (hd/row-gains recs)]
    (is (close? (Math/log 1.6) (:dlog (first g)) 1e-12))
    (is (close? (- 0.25 0.04) (:dbrier (first g)) 1e-12))
    (is (close? (Math/log 1.6) (:dlog (second g)) 1e-12))))

(deftest holm-adjusts-in-order
  ;; sorted 0.01 x 3, 0.02 x 2, 0.03 x 1, each at least the one before
  (is (= [0.03 0.04 0.04] (mapv #(/ (Math/round (* 1000 %)) 1000.0) (hd/holm [0.01 0.03 0.02])))))

(deftest permutation-moves-whole-country-series
  (let [table {["A" 1900] {:x 1.0} ["A" 1901] {:x 2.0} ["B" 1900] {:x 3.0} ["B" 1901] {:x 4.0}}
        p (hd/permuted-table table {"A" "B" "B" "A"})]
    (is (= {:x 3.0} (p ["A" 1900])))
    (is (= {:x 2.0} (p ["B" 1901])))))

(deftest placebo-reads-the-next-country-with-the-state
  (let [table {["A" 1900] {:x 1.0} ["B" 1900] {:x 2.0} ["C" 1900] {} ["D" 1900] {:x 4.0}}
        p (hd/placebo-table table [:x])]
    (is (= 2.0 (:x (p ["A" 1900]))))
    (is (= 4.0 (:x (p ["B" 1900]))) "C has no value: skipped")
    (is (= 4.0 (:x (p ["C" 1900]))))
    (is (= 1.0 (:x (p ["D" 1900]))) "wraps around")))

(deftest a-planted-state-is-found
  ;; crises driven by a known state: the model with it beats the base out of era
  (let [g (m/rng 7)
        base (synthetic-jst isos 23)
        x (into {} (for [iso isos y (range 1870 2021)] [[iso y] (m/normal! g)]))
        jst (mapv (fn [r] (let [z (get x [(:iso r) (dec (:year r))] 0.0)]
                            (assoc r :crisisJST (if (< (m/next-double! g) (m/expit (+ -3.0 (* 1.5 z)))) 1.0 0.0))))
                  base)
        opts {:eras hd/default-eras :test-era hd/default-test-era}
        ;; an onset in year y follows x of year y - 1: at origin t, x of t drives lead 1
        rows (mapv (fn [r] (assoc-in r [:states :synthetic] (x [(:iso r) (:year r)])))
                   (hd/dev-rows jst opts))
        recs (hd/loeo-scores rows {:name "synthetic" :terms [:synthetic] :against []} (assoc opts :label #(nth (:yh %) 0)))
        gain (/ (reduce + (map :dlog (hd/row-gains recs))) (count recs))]
    (is (> gain 0.01))))

(deftest terms-are-clipped-and-the-interaction-needs-both-booms
  ;; a hyperinflation's stretches are clipped at 3 SD; the interaction is
  ;; credit and house prices both above their means (Greenwood et al.'s
  ;; zone), so two busts do not read as a boom
  (let [std {:credit-gap [0.0 10.0] :house [0.0 0.1]}
        v (fn [s t] (hd/term-value std {:states s} t))]
    (is (= 3.0 (v {:credit-gap 80.0} :credit-gap)))
    (is (= -3.0 (v {:house -2.5} :house)))
    (is (= 0.0 (v {:credit-gap -38.0 :house -2.5} :credit-x-house)))
    (is (= 0.0 (v {:credit-gap 20.0 :house -0.1} :credit-x-house)))
    (is (close? 2.0 (v {:credit-gap 20.0 :house 0.1} :credit-x-house) 1e-12))
    (is (= 0.0 (v {:credit-gap 20.0} :credit-x-house)) "missing: the training mean, zero")
    (is (= 0.0 (v {} :equity)))))
