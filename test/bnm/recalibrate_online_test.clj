(ns bnm.recalibrate-online-test
  (:require [augur.math :as m]
            [bnm.recalibrate-online :as ro]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(defn- expit [e] (/ 1.0 (+ 1.0 (Math/exp (- e)))))

(defn- synthetic
  "One kind, years 1960-2019, `n` countries a year. The base's log odds
  L ~ N(-2.5, 1); the outcome's log odds a(t) + b(t) L."
  [seed n ab]
  (let [g (m/rng seed)]
    (vec (for [t (range 1960 2020) i (range n)
               :let [L (+ -2.5 (m/normal! g))
                     [a b] (ab t)]]
           {:kind "k" :c (str "C" i) :t t :L L
            :y (< (m/next-double! g) (expit (+ a (* b L))))}))))

(def opts (ro/defaults))

(defn- log-score [rows k]
  (/ (reduce + (map #(Math/log (if (:y %) (k %) (- 1.0 (k %)))) rows)) (count rows)))

(deftest no-peek-outcomes-at-or-after-t-do-not-move-the-forecast-at-t
  ;; the forecast at origin t learns only from windows (t', t' + 2] closed
  ;; by t: flipping every outcome whose window is still open at t (origins
  ;; after t - 2), and removing them, changes nothing at or before t
  (let [rows (synthetic 3 80 (fn [t] [(- (* 0.02 (- t 1960)) 1.0) 0.6]))
        t 1990
        flipped (mapv #(if (> (:t %) (- t 2)) (update % :y not) %) rows)
        dropped (mapv #(if (> (:t %) (- t 2)) (assoc % :y nil) %) rows)
        at-t (fn [out] (into {} (for [r (out "k") :when (<= (:t r) t)] [[(:c r) (:t r)] (:p r)])))]
    (doseq [o [(assoc opts :pool 0.0) opts (assoc opts :pool 1.0 :revert 0.9)]]
      (let [a (:forecasts (ro/run-online {"k" rows "j" rows} o))
            b (:forecasts (ro/run-online {"k" flipped "j" flipped} o))
            c (:forecasts (ro/run-online {"k" dropped "j" dropped} o))]
        (is (= (at-t a) (at-t b)))
        (is (= (at-t a) (at-t c)))
        (is (not= (:p (last (a "k"))) (:p (last (b "k")))) "later forecasts do learn from them")))))

(deftest a-lag-holds-outcomes-back-further
  (let [rows (synthetic 4 60 (constantly [-0.5 0.7]))
        t 1990
        flipped (mapv #(if (> (:t %) (- t 3)) (update % :y not) %) rows)
        o (assoc opts :lag 1)
        at-t (fn [out] (into {} (for [r (out "k") :when (<= (:t r) t)] [[(:c r) (:t r)] (:p r)])))]
    (is (= (at-t (:forecasts (ro/run-online {"k" rows} o)))
           (at-t (:forecasts (ro/run-online {"k" flipped} o)))))))

(deftest a-drifting-recalibration-is-recovered
  ;; b drifts from 0.3 to 1.1 and a from -2 to 0 over sixty years
  (let [ab (fn [t] (let [s (/ (- t 1960) 59.0)] [(+ -2.0 (* 2.0 s)) (+ 0.3 (* 0.8 s))]))
        rows (synthetic 5 400 ab)
        {:keys [forecasts trace]} (ro/run-online {"k" rows} opts)
        tr (trace "k")]
    (doseq [t [1985 2000 2015]]
      (let [[a b] (tr t) [a0 b0] (ab (- t 3))]
        (is (close? b0 b 0.2) (str "b at " t ": " b " against " b0))
        (is (close? a0 a 0.5) (str "a at " t ": " a " against " a0))))
    (let [late (filterv #(>= (:t %) 1975) (forecasts "k"))]
      (is (> (log-score late :p) (+ 0.003 (log-score late #(expit (:L %))))) "beats the raw base")
      (is (> (log-score late :p) (log-score late #(expit (+ -1.0 (* 0.7 (:L %))))))
          "beats the fixed recalibration at the drift's midpoint"))))

(deftest a-calibrated-base-stays-near-the-identity
  (let [rows (synthetic 6 200 (constantly [0.0 1.0]))
        {:keys [forecasts trace]} (ro/run-online {"k" rows} opts)]
    (doseq [t (range 1970 2020)
            :let [[a b] ((trace "k") t)]]
      (is (close? 1.0 b 0.2) (str "b at " t))
      (is (close? 0.0 a 0.35) (str "a at " t)))
    (let [rs (forecasts "k")]
      (is (> (log-score rs :p) (- (log-score rs #(expit (:L %))) 0.002)) "costs next to nothing"))))

(deftest reversion-pulls-toward-the-identity
  ;; with no outcomes at all the state reverts to (0, 1) at rate phi
  (let [rows (mapv #(assoc % :y nil) (synthetic 8 10 (constantly [0.0 1.0])))
        start (ro/initial (assoc opts :revert 0.5) -1.0 0.4)
        out (ro/run-online {"k" rows} (assoc opts :revert 0.5 :pool 0.0 :start {"k" start}))
        [a b] (((:trace out) "k") 1970)]
    (is (close? 1.0 b 0.01))
    (is (close? 0.0 a 0.01))))

(deftest pooling-moves-each-kind-toward-the-others
  ;; two kinds, both slopes 0.5, one with few rows: pooled, the sparse
  ;; kind's slope is nearer 0.5 than alone
  (let [many (synthetic 9 300 (constantly [-1.2 0.5]))
        few (mapv #(assoc % :kind "f") (filterv #(< (Long/parseLong (subs (:c %) 1)) 15) (synthetic 10 300 (constantly [-1.2 0.5]))))
        alone (ro/run-online {"k" many "f" few} (assoc opts :revert 0.9 :pool 0.0))
        pooled (ro/run-online {"k" many "f" few} (assoc opts :revert 0.9 :pool 1.0))
        slope (fn [out] (second ((get-in out [:trace "f"]) 2015)))]
    (is (< (Math/abs (- (slope pooled) 0.5)) (Math/abs (- (slope alone) 0.5))))))

(deftest scores-and-recall
  (let [rows [{:kind "k" :t 1 :y true :p 0.8 :raw 0.5} {:kind "k" :t 1 :y false :p 0.1 :raw 0.5}]]
    (is (close? (/ (+ (Math/log 0.8) (Math/log 0.9)) 2) (ro/mean-log rows :p) 1e-12))
    (is (close? (/ (+ 0.04 0.01) 2) (ro/brier rows :p) 1e-12))
    (is (= 1.0 (ro/top-recall rows :p)))))

(deftest a-block-bootstrap-brackets-the-mean
  (let [g (m/rng 2)
        rows (vec (for [t (range 1981 2001) _ (range 30)] {:t t :d (+ 0.01 (* 0.05 (m/normal! g)))}))
        mean (/ (reduce + (map :d rows)) (count rows))
        [lo hi] (ro/block-bootstrap rows :d 2 500 1)]
    (is (< lo mean hi))
    (is (< (- hi lo) 0.02))))

(deftest outcomes-after-to-are-not-read
  (let [seen (atom 0)
        rows (ro/rows-of {:cells (for [c ["A" "B"] t (range 1950 2025)] [c t])
                          :kind "k" :from 1960 :to 2002
                          :base (fn [_ _] -2.0)
                          :outcome (fn [_ t] (swap! seen max (+ t 2)) true)})]
    (is (<= @seen 2002))
    (is (= 1960 (apply min (map :t rows))) "forecasts from the filter's start")
    (is (= 2024 (apply max (map :t rows))) "every later origin is forecast, its outcome unknown")
    (is (every? #(nil? (:y %)) (filter #(> (+ 2 (:t %)) 2002) rows)))))

(deftest forecasts-in-the-lattice-format
  (let [s (ro/forecast-lines [{:c "USA" :t 2000 :p 0.25 :y true} {:c "FRA" :t 2001 :p 0.5 :y nil}])]
    (is (= ["year\tUSA\t24011\t0.25\ttrue" "year\tFRA\t24023\t0.5\t"] (str/split-lines s)))))

(deftest options-from-the-command-line
  (let [o (ro/parse-opts [])]
    (is (= ["t44-world-norel" 1981 2002 1960] ((juxt :run :from :to :learn-from) o))))
  (let [o (ro/parse-opts ["--from" "2003" "--to" "2022" "--run" "x"])]
    (is (= ["x" 2003 2022 1960] ((juxt :run :from :to :learn-from) o)))))
