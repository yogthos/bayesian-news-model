(ns bnm.attribution-test
  (:require [augur.math :as m]
            [bnm.attribution :as at]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

;; --- eras --------------------------------------------------------------------------

(deftest an-era-holds-the-origins-whose-window-it-covers
  ;; the year node's outcome is an onset within 24 months of the origin
  ;; (the end of year t): years t + 1 and t + 2
  (is (= (range 1960 1979) (at/era-origins [1960 1980])))
  (is (= (range 1981 2001) (at/era-origins [1981 2002])))
  (is (= [1981 1982] (at/outcome-years 1980))))

(deftest the-rotation-never-fits-on-a-scored-year
  (doseq [opts [{:from 1960 :to 2002 :split 1980}
                {:from 1960 :to 2002 :split 1970}
                {:from 2003 :to 2022 :fit-from 1960 :fit-to 2002}]]
    (let [ss (at/splits opts)]
      (is (seq ss))
      (doseq [{:keys [fit score]} ss
              :let [fy (set (mapcat at/outcome-years (at/era-origins fit)))
                    sy (set (mapcat at/outcome-years (at/era-origins score)))]]
        (is (seq sy))
        (is (empty? (filter fy sy)) (str "fit " fit " touches a year scored in " score)))))
  (testing "the design rotation scores both eras, each fitted on the other"
    (is (= [{:fit [1960 1980] :score [1981 2002]} {:fit [1981 2002] :score [1960 1980]}]
           (at/splits {:from 1960 :to 2002 :split 1980}))))
  (testing "a fitted span that overlaps the scored one is refused"
    (is (thrown? Exception (at/splits {:from 2001 :to 2022 :fit-from 1960 :fit-to 2002}))))
  (testing "the rows a split fits and scores"
    (let [rows (for [c ["A" "B"] t (range 1960 2003)] {:c c :t t :y false :p 0.1})
          {:keys [fit score]} (at/split-rows rows {:fit [1960 1980] :score [1981 2002]})
          fy (set (mapcat (comp at/outcome-years :t) fit))]
      (is (= #{1960 1978} (set [(apply min (map :t fit)) (apply max (map :t fit))])))
      (is (= #{1981 2000} (set [(apply min (map :t score)) (apply max (map :t score))])))
      (is (not-any? fy (mapcat (comp at/outcome-years :t) score))))))

;; --- reading ---------------------------------------------------------------------

(deftest no-state-is-read-after-its-origin
  ;; a missing value enters at the state's running mean over every value
  ;; known by the origin, with its indicator
  (let [panel {"A" {1990 1.0 1991 3.0 1995 100.0} "B" {1990 5.0 1993 7.0}}
        rd (at/reader panel)]
    (is (= [1.0 false] (rd "A" 1990)))
    (is (= [5.0 false] (rd "B" 1990)))
    (is (= [3.0 true] (rd "A" 1992)) "A misses 1992: the mean of 1, 5, 3")
    (is (= [3.0 true] (rd "B" 1991)) "1991's mean of 1, 5, 3 is 3")
    (is (= [4.0 true] (rd "A" 1993)) "A misses 1993: the mean of 1, 5, 3, 7")
    (testing "values after the origin change nothing at it"
      (doseq [t (range 1985 1997)
              :let [cut (update-vals panel (fn [byy] (into {} (filter #(<= (key %) t) byy))))
                    later (update-vals panel (fn [byy] (into {} (for [[y x] byy] [y (if (> y t) (* 1000.0 x) x)]))))]
              c ["A" "B"]]
        (is (= ((at/reader cut) c t) ((at/reader panel) c t) ((at/reader later) c t)) (str c " " t)))))
  (testing "nothing known yet: nil and missing"
    (is (= [nil true] ((at/reader {"A" {1990 1.0}}) "B" 1980))))
  (testing "a table by data year is read when published"
    ;; {iso (sorted-map year-known value)} as bnm.states/read-states gives a
    ;; lagged table: a data year d with lag 2 is known at d + 2
    (let [p (at/known-panel {"A" (sorted-map 1992 10.0 1993 11.0)} 3 identity)]
      (is (nil? (get-in p ["A" 1991])))
      (is (= 10.0 (get-in p ["A" 1992])))
      (is (= 11.0 (get-in p ["A" 1996])))
      (is (nil? (get-in p ["A" 1997])) "more than three years old"))))

(deftest the-placebo-is-a-fixed-derangement
  (let [d (at/derange ["A" "B" "C"])]
    (is (= {"A" "B" "B" "C" "C" "A"} d))
    (is (not-any? (fn [[a b]] (= a b)) d))))

(deftest long-tails-are-clipped-before-scaling
  (let [xs (concat (range 100) [1e6])
        f (at/scaler xs)]
    (is (< (f 1e6) 2.0) "the outlier is clipped at the 99th percentile")
    (is (close? 0.0 (/ (reduce + (map f xs)) (count xs)) 0.05))
    (is (= 0.0 (f nil)) "unknown: the fit rows' centre")))

;; the coverage-matched placebo: the country's own indicator, another
;; country's value where both are known, else the running mean
(deftest the-matched-placebo-keeps-the-country-s-coverage
  (let [panel {"A" {1990 1.0 1991 3.0} "B" {1990 5.0 1992 7.0}}
        other {"A" "B" "B" "A"}
        rd (at/matched-reader panel other)]
    (is (= [5.0 false] (rd "A" 1990)) "both known: the other's value, A's indicator")
    (is (= [1.0 false] (rd "B" 1990)))
    (is (= [3.0 false] (rd "A" 1991)) "only A known in 1991: the running mean of 1, 5, 3, not A's own 3 by chance")
    (is (= [3.0 true] (rd "B" 1991)) "B unknown: missing, at the running mean")
    (is (= [4.0 false] (rd "B" 1992)) "only B known: the running mean of 1, 5, 3, 7")
    (is (= [nil true] (rd "A" 1980)))
    (testing "no value after the origin"
      (doseq [t (range 1988 1995) c ["A" "B"]
              :let [later (update-vals panel (fn [byy] (into {} (for [[y x] byy] [y (if (> y t) (* 1000.0 x) x)]))))]]
        (is (= (rd c t) ((at/matched-reader later other) c t)))))))

;; --- the fit ----------------------------------------------------------------------

(defn- simulate
  "Rows over `nc` countries and the years 1960-2002: y from year effects
  plus `f` of the country and the year."
  [seed nc f]
  (let [g (m/rng seed)
        u (into {} (for [t (range 1960 2003)] [t (* 0.7 (m/normal! g))]))]
    (vec (for [c (range nc) t (range 1960 2003)
               :let [e (+ -2.8 (u t) (f c t))]]
           {:c (format "C%03d" c) :t t :y (< (m/next-double! g) (m/expit e)) :e e}))))

(deftest year-effects-and-a-ridge
  (let [g (m/rng 3)
        x (into {} (for [c (range 150)] [(format "C%03d" c) (m/normal! g)]))
        rows (simulate 4 150 (fn [c _] (* 0.8 (x (format "C%03d" c)))))
        rows (mapv #(assoc % :o 0.0 :x (double-array [(x (:c %))])) rows)
        f (at/fit-fe rows 1 [1e-4])]
    (is (close? 0.8 (aget ^doubles (at/coefs f) 0) 0.12) "recovers the coefficient past the year effects")
    (let [f' (at/fit-fe rows 1 [1e4])]
      (is (< (Math/abs (aget ^doubles (at/coefs f') 0)) 0.05) "a strong ridge shrinks it to zero"))
    (testing "the level for an unseen year reproduces the fitted rows' rate"
      (let [a (at/level f rows)
            ps (map #(m/expit (+ a (at/linear f %))) rows)]
        (is (close? (/ (count (filter :y rows)) (double (count rows))) (/ (reduce + ps) (count rows)) 1e-6))))))

(deftest within-year-matching
  (let [etas [-3.0 -2.0 -1.0 0.5]
        shifted (at/match-mean etas 0.1)]
    (is (close? 0.1 (/ (reduce + (map m/expit shifted)) 4.0) 1e-9))
    (is (apply < shifted))))

;; --- shapley ----------------------------------------------------------------------

(deftest shapley-shares-add-up
  (doseq [[s b bs] [[0.002 0.005 0.0055] [0.0 0.004 0.004] [-0.001 0.003 0.0025] [0.003 0.001 0.0035]]]
    (let [{:keys [states history]} (at/shapley s b bs)]
      (is (close? bs (+ states history) 1e-15))
      (is (close? states (* 0.5 (+ s (- bs b))) 1e-15))))
  (testing "in the stats of a result"
    (let [st (at/derived {"S" 0.002 "B" 0.005 "BS" 0.0055 "P" 0.0002 "BP" 0.0049})]
      (is (close? (st "skill-BS") (+ (st "shapley-states") (st "shapley-history")) 1e-15))
      (is (close? (st "skill-BP") (+ (st "placebo-shapley-states") (st "placebo-shapley-history")) 1e-15))
      (is (close? 0.4 (st "share") 1e-12))))
  (testing "the coverage-matched placebo's split adds up too"
    (let [st (at/derived {"S" 0.002 "B" 0.005 "BS" 0.0055 "P" 0.0002 "BP" 0.0049 "P'" 0.001 "BP'" 0.0052 "I" 0.0007})]
      (is (close? (st "skill-BP'") (+ (st "matched-shapley-states") (st "matched-shapley-history")) 1e-15))
      (is (close? 0.001 (st "states-less-matched") 1e-15))
      (is (close? 0.0007 (st "skill-I") 1e-15)))))

;; --- synthetic attribution ---------------------------------------------------------

(defn- synthetic-run
  "One kind on 160 countries x 1960-2002. A country level x (a persistent
  state) and a country effect h independent of it; `signal` picks what
  drives the onsets: :states (x) or :history (h). The base knows the true
  log odds (its country's history and the year), the states panel holds x."
  [signal seed]
  (let [g (m/rng seed)
        cs (mapv #(format "C%03d" %) (range 160))
        x (into {} (for [c cs] [c (m/normal! g)]))
        h (into {} (for [c cs] [c (m/normal! g)]))
        drive (case signal :states x :history h)
        rows (simulate (inc seed) 160 (fn [c _] (* 0.9 (drive (format "C%03d" c)))))
        rows (mapv (fn [r] {:c (:c r) :t (:t r) :y (:y r) :p (m/expit (:e r))}) rows)
        panel (into {} (for [c cs] [c (into {} (for [t (range 1950 2003)] [t (+ (x c) (* 0.1 (m/normal! g)))]))]))
        ;; a state of pure noise, known for half the countries only
        noise (into {} (for [c (take 80 cs)] [c (into {} (for [t (range 1950 2003)] [t (m/normal! g)]))]))]
    (at/evaluate {:rows-by-kind {"k" rows}
                  :panels {"s" panel "n" noise}
                  :groups [["all" ["s" "n"]] ["where" ["s"]] ["when" ["n"]]]
                  :splits (at/splits {:from 1960 :to 2002 :split 1980})
                  :lambdas [0.1 10.0]
                  :reps 50 :seed 1})))

(defn- point
  ([res stat] (point res stat "all"))
  ([res stat group]
   (some #(when (and (= "within" (:mode %)) (= "recal" (:base %)) (= group (:group %)) (= "pooled" (:kind %))
                     (= "log" (:score %)) (= stat (:stat %)))
            (:value %))
         (:results res))))

(deftest attribution-is-near-one-when-the-states-hold-the-signal
  (let [res (synthetic-run :states 21)]
    (is (pos? (point res "skill-B")))
    (is (close? 1.0 (point res "share") 0.2) (str "share " (point res "share")))
    (is (> (point res "shapley-share") 0.35) "the states and the base split their common skill")
    (is (< (Math/abs (point res "placebo-share")) 0.15) "another country's state carries nothing")
    (is (< (Math/abs (point res "matched-share")) 0.15) "nor with the country's own coverage")
    (is (> (point res "states-less-matched") 0.0))
    (testing "where less when, from the same draws"
      (let [w (point res "skill-S" "where") n (point res "skill-S" "when")]
        (is (close? (- w n) (point res "where-less-when" "where-when") 1e-12))
        (is (> (point res "where-less-when" "where-when") 0.0))
        (is (some? (point res "where-less-when-matched" "where-when")))))))

(deftest attribution-is-near-zero-when-the-states-hold-none
  (let [res (synthetic-run :history 22)]
    (is (pos? (point res "skill-B")))
    (is (< (Math/abs (point res "share")) 0.1) (str "share " (point res "share")))
    (is (< (Math/abs (point res "shapley-share")) 0.1))))
