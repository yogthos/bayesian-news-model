(ns bnm.lattice-golden-test
  "A golden snapshot of the lattice: a spec that uses every read (up, down,
  across, contradictions, tensions, turns, exposure, feedback, coupling,
  the downscaled offset pooled with its uncertainty, nested relations, the
  principal contradiction), its forecasts and every traced input row
  exactly as the lattice made them before it was refactored onto
  bnm.sheaf. A refactor must reproduce them bit for bit.

  To regenerate (only when a change is meant to alter the numbers):
    jolt -A:test -e \"(require 'bnm.lattice-golden-test) (bnm.lattice-golden-test/write!)\""
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]))

(def path "test/resources/lattice-golden.edn")

(def countries ["a1" "a2" "a3" "b1" "b2" "b3"])
(def parent (merge (zipmap ["a1" "a2" "a3"] (repeat "A")) (zipmap ["b1" "b2" "b3"] (repeat "B")) {"A" "W" "B" "W"}))
(def months 240)

(defn spec []
  (let [g (m/rng 42)
        stress (into {} (for [c countries]
                          [c (vec (reductions (fn [s _] (+ (* 0.97 s) (* 0.35 (m/normal! g)))) 0.0 (range (dec months))))]))
        events (into {} (for [c countries] [c (mapv (fn [s] (< (m/next-double! g) (m/expit (- (* 1.5 s) 2.0)))) (stress c))]))
        noise (into {} (for [c (concat countries ["A" "B" "W"]) t (range months) k (range 4)] [[c t k] (m/normal! g)]))
        members (fn [p] (if (= 2 (count p)) [p] (filter #(or (= p (parent %)) (= p (parent (parent %)))) countries)))
        onsets (into {} (for [c countries] [c (into (sorted-set) (keep-indexed (fn [t e] (when e t)) (events c)))]))]
    {:scales [{:name :month :per 1 :horizon 1 :discount 0.99}
              {:name :quarter :per 3 :offset 0 :horizon 3 :discount 0.99}
              {:name :year :per 12 :horizon 12 :discount 0.995}]
     :levels [[:country countries] [:region ["A" "B"]] [:world ["W"]]]
     :parent parent :start 0 :end (dec months) :forget 0.99
     :target (fn [scale place origin]
               (let [h ({:month 1 :quarter 3 :year 12} scale)
                     hi (+ origin h)]
                 (when (< hi months)
                   {:y (boolean (some (fn [c] (some #(get-in events [c %]) (range (inc origin) (inc hi)))) (members place)))
                    :known-at (+ hi 1)})))
     :gauges (fn [scale place origin]
               (let [cs (members place)
                     s (/ (reduce + (map #(get-in stress [% origin]) cs)) (count cs))]
                 [(+ s (* 0.5 (noise [place origin 0]))) (noise [place origin 1]) (+ (* 0.5 s) (noise [place origin 2]))]))
     :gauge-width {:month 3 :quarter 3 :year 3}
     :gauge-groups {:year {:g/a [0] :g/b [1 2]}}
     :tensions {:month [[0 1]] :year [[0 2]]}
     :turns {:year [[0 2 3]]}
     :contradiction-prior [0.3 0.25]
     :resolutions (fn [place] (when-let [os (onsets place)] (into (sorted-set) (map #(+ % 2) os))))
     :exposure (fn [_ place t] [(if (= 2 (count place)) (Math/sin (/ (+ t (count place)) 7.0)) 0.5)])
     :exposure-width 1
     :coupling 0.3
     :shrink 0.2
     :downscale {:month {:from :quarter}}
     :downscale-pool 0.5
     :downscale-uncertainty true
     :offset (fn [scale place t] (when (= scale :month) (- (* 0.3 (noise [place t 3])) 1.5)))
     :relations (fn [_ place t] {:x [(noise [place t 3]) (if (zero? (mod t 5)) 1.0 0.0)]
                                 :reads [{:dir :relation :kind "other" :place place :origin (dec t)}]})
     :relation-width {:month 2 :quarter 2 :year 2}
     :relation-groups {:month {:rel/x [0] :rel/y [1]} :quarter {:rel/x [0] :rel/y [1]} :year {:rel/x [0] :rel/y [1]}}
     :relation-nest true
     :principal :auto
     :trace :inputs}))

(defn snapshot [r]
  {:forecasts (mapv (juxt :scale :place :origin :p) (:forecasts r))
   :inputs (mapv (fn [{:keys [scale place origin x o]}] [scale place origin (vec x) o]) (:trace r))
   ;; what each node read and when: the causality guard
   :reads (mapv (fn [{:keys [scale place origin reads]}]
                  ;; as a set: their order within a row is the order lazy sequences
                  ;; were forced in, not part of what was read
                  [scale place origin (vec (sort-by pr-str (map (juxt :dir :scale :place :origin :known-at) reads)))])
                (:trace r))
   :principal (into (sorted-map) (for [[k byo] (:book r) [o e] byo :when (:principal e)] [[k o] (:principal e)]))
   :strain (into (sorted-map) (for [[k byo] (:book r) [o e] byo] [[k o] [(:strain e) (:f e) (:q e)]]))})

(defn fingerprint
  "Each part's count, the hash of its printed form (doubles print exactly)
  and its first rows in full, to see where a difference starts."
  [snap]
  (into (sorted-map) (for [[k v] snap] [k {:n (count v) :hash (hash (pr-str v)) :head (vec (take 40 v))}])))

(def variants
  "The spec as given, and with groups dropped at some scales (the paths a
  dropped group skips)."
  {:full identity
   :ablated #(assoc % :without-at {:year #{:up :feedback} :quarter #{:down}})})

(defn write! []
  (spit path (pr-str (into (sorted-map) (for [[k f] variants] [k (fingerprint (snapshot (lt/run (f (spec)))))])))))

(deftest the-lattice-as-it-was
  (let [want (read-string (slurp (io/file path)))]
    (doseq [[v f] variants
            :let [got (fingerprint (snapshot (lt/run (f (spec)))))]
            k (keys (want v))]
      (is (= (:n (get-in want [v k])) (:n (k got))) (str v " " k ": count"))
      (is (= (:head (get-in want [v k])) (:head (k got))) (str v " " k ": first rows"))
      (is (= (:hash (get-in want [v k])) (:hash (k got))) (str v " " k ": differs")))))
