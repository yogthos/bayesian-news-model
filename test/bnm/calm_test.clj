(ns bnm.calm-test
  (:require [bnm.calm :as calm]
            [clojure.test :refer [deftest is testing]]))

(defn- at [^doubles x label] (aget x (.indexOf ^java.util.List calm/labels label)))

(defn- quiet [n] (into (sorted-map) (for [q (range n)] [q {"protest-did" 10.0 "consult-did" 90.0}])))

(deftest needs-a-trailing-window
  (let [fs (calm/features (quiet 20))]
    (is (nil? (fs (dec calm/window))))
    (is (some? (fs calm/window)))
    (is (= (count calm/labels) (alength ^doubles (fs 19))))))

(deftest a-steady-stream-is-calm
  (let [x ((calm/features (quiet 20)) 19)]
    (is (< (Math/abs (at x :protest-cv)) 1e-9) "no variation")
    (is (< (Math/abs (at x :protest-jump)) 1e-9))
    (is (zero? (at x :protest-since-spike)) "never a spike")))

(deftest a-spike-is-seen-and-fades
  (let [counts (assoc (quiet 30) 15 {"protest-did" 60.0 "consult-did" 40.0})
        fs (calm/features counts)]
    (is (> (at (fs 15) :protest-jump) 1.0) "the share jumps against the quarters before")
    (is (< (Math/abs (- 1.0 (at (fs 15) :protest-since-spike))) 1e-9))
    (is (> (at (fs 16) :protest-since-spike) (at (fs 25) :protest-since-spike) 0.0) "fading")
    (is (> (at (fs 16) :protest-cv) (at (fs 14) :protest-cv)) "variation rises with the spike in the window")))

(deftest variance-jump-compares-recent-spread-to-the-older
  (let [noisy (into (quiet 20) (for [q (range 16 20)] [q {"protest-did" (if (even? q) 30.0 5.0) "consult-did" 70.0}]))
        x ((calm/features noisy) 19)]
    (is (> (at x :protest-var-jump) 1.0))))

(deftest quarters-without-events-keep-the-last-values
  (let [counts (dissoc (quiet 20) 17)
        fs (calm/features counts)]
    (is (some? (fs 17)))
    (is (= (vec (fs 16)) (vec (fs 17))))))
