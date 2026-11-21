(ns bnm.signals-test
  "bnm.signals, the shared transforms of stocks and flows: each must equal,
  to the bit, the copies it replaces (kept here as references, verbatim
  from before the refactor, bnm-cnf.4)."
  (:require [bnm.signals :as sg] [bnm.stocks]
            [clojure.test :refer [deftest is testing]]))

;; --- the copies replaced, verbatim -------------------------------------------

(defn- ref-mean [xs] (/ (reduce + xs) (count xs)))
(defn- ref-sd [xs]
  (let [m (ref-mean xs)]
    (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (dec (count xs))))))

(defn- ref-invariants-stretch
  "bnm.invariants/stretch"
  [xs i window n]
  (when-let [x (get xs i)]
    (let [prev (keep #(get xs %) (range (max 0 (- i window)) i))]
      (when (>= (count prev) (max n 2))
        (let [s (ref-sd prev)]
          (when (> s 1e-12) (/ (- x (ref-mean prev)) s)))))))

(defn- ref-stretch-at
  "bnm.lattice-banking/stretch-at"
  [ys year]
  (when-let [x (get ys year)]
    (let [past (keep #(get ys %) (range (- year 10) year))]
      (when (>= (count past) 6)
        (let [m (/ (reduce + past) (count past))
              sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) past)) (dec (count past))))
              sd (Math/max sd (* 0.02 (Math/abs m)))]
          (when (> sd 1e-9) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd)))))))))

(defn- ref-quarter-stretch
  "bnm.lattice-banking/quarter-stretch"
  [qs q]
  (when-let [x (get qs q)]
    (let [past (keep #(get qs %) (range (- q 40) q))]
      (when (>= (count past) 20)
        (let [m (/ (reduce + past) (count past))
              sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) past)) (dec (count past))))]
          (let [sd (Math/max sd (* 0.02 (Math/abs m)))]
            (when (> sd 1e-9) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd))))))))))

(defn- p-mean [xs] (/ (reduce + xs) (double (count xs))))
(defn- p-sd [xs]
  (let [mu (p-mean xs)]
    (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) xs)) (dec (count xs))))))
(defn- ref-precursors-z
  "bnm.precursors/z (sd floor 0.25, cap 10)"
  [x past n]
  (when (and x (>= (count past) (max n 2)))
    (Math/max -10.0 (Math/min 10.0 (/ (- x (p-mean past)) (Math/max 0.25 (p-sd past)))))))

(defn- ref-aggregate
  "bnm.invariants/aggregate"
  [zs {:keys [threshold min-n]}]
  (let [zs (keep identity zs)
        n (count zs)]
    (when (>= n min-n)
      {:max (apply max (map #(Math/abs (double %)) zs))
       :breadth (/ (count (filter #(> (Math/abs (double %)) threshold) zs)) (double n))
       :norm (Math/sqrt (/ (reduce + (map #(* % %) zs)) n))
       :n n})))

(defn- ref-banking-standardizer
  "bnm.lattice-banking's private standardizer"
  [samples mode]
  (let [by-t (into (sorted-map) (group-by first samples))
        width (count (second (first samples)))
        stable (= :stable mode)
        stats (loop [[[t xs] & more] (seq by-t) n (double-array width) s (double-array width) ss (double-array width) acc (sorted-map)]
                (if-not t
                  acc
                  (let [acc (assoc acc t [(aclone n) (aclone s) (aclone ss)])]
                    (doseq [[_ v] xs]
                      (dotimes [j width]
                        (when-let [x (nth v j)]
                          (aset n j (inc (aget n j)))
                          (if stable
                            (let [m (aget s j) m' (+ m (/ (- x m) (aget n j)))]
                              (aset s j m') (aset ss j (+ (aget ss j) (* (- x m) (- x m')))))
                            (do (aset s j (+ (aget s j) x)) (aset ss j (+ (aget ss j) (* x x))))))))
                    (recur more n s ss acc))))]
    (fn [t v]
      (let [[^doubles n ^doubles s ^doubles ss] (or (some-> (rsubseq stats <= t) first val)
                                                    [(double-array width) (double-array width) (double-array width)])]
        (vec (for [j (range width)
                   :let [x (nth v j)
                         k (aget n j)]]
               (if (and x (> k 20))
                 (if stable
                   (let [mu (aget s j)
                         sd (Math/sqrt (/ (aget ss j) k))]
                     (if (> sd (Math/max 1e-9 (* 1e-8 (Math/abs mu))))
                       (Math/max -5.0 (Math/min 5.0 (/ (- x mu) sd)))
                       0.0))
                   (let [mu (/ (aget s j) k)
                         sd (Math/sqrt (Math/max 1e-12 (- (/ (aget ss j) k) (* mu mu))))]
                     (Math/max -5.0 (Math/min 5.0 (/ (- x mu) sd)))))
                 0.0)))))))

(defn- ref-history-standardizer
  "bnm.lattice-banking/history-standardizer"
  ([lead-f units ix from end] (ref-history-standardizer lead-f units ix from end 0.0))
  ([lead-f units ix from end missing]
   (let [w (count ix)
         ts (range (+ from (mod (- 2 from) 3)) (inc end) 3)
         stats (loop [[t & more] ts n (double-array w) mu (double-array w) ss (double-array w) q (long-array w) acc (sorted-map)]
                 (if-not t
                   acc
                   (let [acc (assoc acc t [(aclone n) (aclone mu) (aclone ss) (aclone q)])
                         seen (boolean-array w)]
                     (doseq [u units :let [l (lead-f u t)]]
                       (dotimes [j w]
                         (when-let [x (nth l (nth ix j))]
                           (aset seen j true)
                           (aset n j (inc (aget n j)))
                           (let [m0 (aget mu j) m1 (+ m0 (/ (- x m0) (aget n j)))]
                             (aset mu j m1) (aset ss j (+ (aget ss j) (* (- x m0) (- x m1))))))))
                     (dotimes [j w] (when (aget seen j) (aset q j (inc (aget q j)))))
                     (recur more n mu ss q acc))))]
     (fn [t v]
       (let [[^doubles n ^doubles mu ^doubles ss ^longs q] (or (some-> (rsubseq stats <= t) first val)
                                                               [(double-array w) (double-array w) (double-array w) (long-array w)])]
         (vec (for [j (range w) :let [x (nth v j)]]
                (if (and x (>= (aget q j) 20))
                  (let [sd (Math/sqrt (/ (aget ss j) (aget n j)))]
                    (if (> sd (Math/max 1e-9 (* 1e-8 (Math/abs (aget mu j)))))
                      (Math/max -5.0 (Math/min 5.0 (/ (- x (aget mu j)) sd)))
                      missing))
                  missing))))))))

;; --- random series, with gaps, flats and levels far from zero -----------------

(defn- lcg [seed] (let [a (atom (long seed))] (fn [] (swap! a #(mod (+ (* 6364136223846793005 %) 1442695040888963407) 9223372036854775807)) (/ (double @a) 9.223372036854775807E18))))

(defn- series
  "{t x} over 0..n-1: a random walk around `level`, about a fifth missing,
  stretches of flat values."
  [seed n level]
  (let [r (lcg seed)]
    (loop [t 0 x level out {}]
      (if (= t n)
        out
        (let [u (r) x (if (< u 0.1) x (+ x (* level 0.05 (- (r) 0.5)) (- (r) 0.5)))]
          (recur (inc t) x (if (< (r) 0.2) out (assoc out t x))))))))

(def ^:private cases
  (for [seed (range 1 41) level [0.0 1.0 100.0 1e6]] (series seed 140 level)))

(deftest stretch-equals-the-copies
  (testing "bnm.invariants/stretch on vectors"
    (doseq [s cases :let [xs (mapv #(get s %) (range 140))] w [5 10 20] n [4 6 12] i [0 1 5 30 80 139]]
      (is (= (ref-invariants-stretch xs i w n) (sg/stretch xs i {:window w :min n :eps 1e-12})) [w n i])))
  (testing "lattice-banking's stretch-at and quarter-stretch on keyed series"
    (doseq [s cases t [3 12 40 90 139]]
      (is (= (ref-stretch-at s t) (sg/stretch s t {:window 10 :min 6 :floor-frac 0.02 :eps 1e-9 :cap 5.0})))
      (is (= (ref-quarter-stretch s t) (sg/stretch s t {:window 40 :min 20 :floor-frac 0.02 :eps 1e-9 :cap 5.0}))))))

(deftest z-against-equals-precursors
  (doseq [s cases t [3 25 60 139] n [2 4 10]]
    (let [x (get s t) past (keep #(get s %) (range (- t 20) t))]
      (is (= (ref-precursors-z x past n) (sg/z-against x past {:min n :floor 0.25 :cap 10.0 :eps 0.0})) [t n]))))

(deftest aggregate-equals-invariants
  (doseq [s cases :let [zs (map #(some-> (get s %) (/ 3.0)) (range 0 14))] mn [1 5 20] th [1.0 1.5]]
    (is (= (ref-aggregate zs {:threshold th :min-n mn}) (sg/aggregate zs {:threshold th :min-n mn})))))

(deftest running-z-equals-the-banking-standardizers
  (let [samples (fn [seed] (for [t (range 0 60 3) u (range 8)
                                 :let [r (lcg (+ (* 1000 seed) t u))]]
                             [t (vec (for [j (range 4)] (when (> (r) 0.2) (+ (* 50.0 j) (* 10.0 (r))))))]))]
    (testing "the year and quarter gauges' standardizer, both modes"
      (doseq [seed (range 1 6) mode [nil :stable]
              :let [old (ref-banking-standardizer (samples seed) mode)
                    new (sg/running-z (samples seed) {:count-by :values :min-exclusive 20 :stable (= :stable mode)})]
              t [0 9 30 57 70] :let [v [1.0 nil 60.0 175.0]]]
        (is (= (old t v) (new t v)) [seed mode t]))))
  (testing "the leading gauges' history standardizer: counted by quarter ends"
    (let [lead (fn [u t] (let [r (lcg (+ (* 31 (hash u)) t))] [(+ t (r)) :skip (when (> (r) 0.3) (* 2.0 (r))) (* 100.0 (r))]))
          units ["A" "B" "C"]]
      (doseq [missing [0.0 nil] from [0 1 2] t [10 50 80 130]]
        (let [old (ref-history-standardizer lead units [0 2 3] from 150 missing)
              new (sg/history-standardizer lead units [0 2 3] from 150 missing)
              v [71.0 0.5 33.0]]
          (is (= (old t v) (new t v)) [missing from t]))))))

(deftest a-declaration-reads-a-drawdown
  ;; bnm-g5t.29: [:drawdown n], the fall from the n months' peak
  (let [s (bnm.stocks/series (for [p (range 0 61)] [p (inc p) (double (if (<= p 40) p (- 80 p)))]))
        db {["AGO" "trade/commodity_tot"] s}]
    (is (contains? sg/reads :drawdown))
    (is (= 10.0 (sg/read-signal db {:var "trade/commodity_tot" :read [:drawdown 24] :stale 9} "AGO" 51)))
    (is (nil? (sg/read-signal db {:var "trade/commodity_tot" :read [:drawdown 24] :stale 9} "AGO" 75)))))
