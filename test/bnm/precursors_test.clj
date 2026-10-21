(ns bnm.precursors-test
  (:require [augur.math :as m]
            [bnm.precursors :as p]
            [clojure.test :refer [deftest is testing]]))

(defn- steady
  "{q counts} over quarters 0..n-1: each of k types near `level`, Poisson-ish."
  [g n k level]
  (into {} (for [q (range n)]
             [q (vec (for [_ (range k)] (max 0.0 (Math/round (+ level (* (Math/sqrt level) (m/normal! g)))))))])))

(deftest a-broad-surge-is-displacement
  (let [g (m/rng 1)
        base (steady g 60 15 20.0)
        surged (assoc base 59 (vec (repeat 15 80.0)))
        before (p/features {:counts surged} 58)
        at (p/features {:counts surged} 59)]
    (is (< (:norm before) 2.0) before)
    (is (> (:norm at) 3.0) at)
    (is (> (:breadth at) 0.9) "every type stretched")
    (is (pos? (:resonance at)) "displaced at every scale")
    (is (<= (:resonance at) (:norm at)))))

(deftest one-stream-jumping-is-a-trigger-not-breadth
  (let [g (m/rng 2)
        base (steady g 60 15 20.0)
        jumped (assoc-in base [59 3] 200.0)
        f (p/features {:counts jumped} 59)]
    (is (> (:trigger f) 3.0) f)
    (is (< (:breadth f) 0.2) f)))

(deftest bursty-counts-raise-dispersion
  (let [g (m/rng 3)
        calm (steady g 60 15 20.0)
        bursty (merge calm (into {} (for [q (range 52 60)] [q (vec (repeat 15 (if (even? q) 2.0 60.0)))])))]
    (is (> (:dispersion (p/features {:counts bursty} 59))
           (+ 1.0 (:dispersion (p/features {:counts calm} 59)))))))

(deftest co-moving-with-the-neighbours-raises-the-range
  (let [g (m/rng 4)
        n 80
        common (vec (repeatedly n #(m/normal! g)))
        peer (fn [] (into {} (for [q (range n)] [q (vec (repeat 15 (Math/exp (+ 3.0 (* 0.8 (common q)) (* 0.2 (m/normal! g))))))])))
        peers [(peer) (peer) (peer) (peer)]
        ;; the unit moves on its own until quarter 64, then with the others
        unit (into {} (for [q (range n)]
                        [q (vec (repeat 15 (Math/exp (+ 3.0 (if (< q 64) (m/normal! g) (+ (* 0.8 (common q)) (* 0.2 (m/normal! g))))))))]))
        f (p/features {:counts unit :peers peers} 79)]
    (is (> (:range-shift f) 1.0) f)
    (is (= 0.0 (:range-shift (p/features {:counts unit} 79))) "no peers, no range")))

(deftest nothing-after-the-quarter-is-read
  (let [g (m/rng 5)
        base (steady g 70 15 20.0)
        changed (merge base (into {} (for [q (range 60 70)] [q (vec (repeat 15 500.0))])))]
    (is (= (p/features {:counts base} 59) (p/features {:counts changed} 59)))))

(deftest too-little-history-gives-nothing
  (let [g (m/rng 6)]
    (is (nil? (p/features {:counts (steady g 6 15 20.0)} 5)))))

(deftest vectors-in-a-fixed-order
  (let [g (m/rng 7)
        f (p/features {:counts (steady g 60 15 20.0)} 59)]
    (is (= (count p/names) (count (p/->vector f))))
    (is (every? number? (p/->vector f)))))

(deftest event-counts-fill-the-quiet-quarters
  (let [f (java.io.File/createTempFile "mix" ".tsv")]
    (spit f (str "AAA\t10\tprotest-did\t3\n" "AAA\t10\tassault-did\t1\n" "AAA\t12\tprotest-did\t5\n" "BBB\t11\tprotest-did\t2\n"))
    (let [c (p/load-counts (.getPath f))
          types p/streams
          at (fn [u q t] (nth (get-in c [u q]) (.indexOf types t)))]
      (is (= 3.0 (at "AAA" 10 "protest-did")))
      (is (= 1.0 (at "AAA" 10 "assault-did")))
      (is (= 0.0 (at "AAA" 11 "protest-did")) "a quarter between with no events counts zero")
      (is (= 5.0 (at "AAA" 12 "protest-did")))
      (is (nil? (get-in c ["AAA" 13])) "nothing past the unit's last quarter")
      (is (= (count types) (count (get-in c ["BBB" 11])))))))
