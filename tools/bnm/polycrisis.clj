(ns bnm.polycrisis
  "Joint crises beyond pairs (bnm.hypergraph): on the design years, for
  each country and three-year window, the kinds with an onset together;
  for every triple of kinds, how often all three met against what their
  pairs imply with no three-way term, with an interval from resampling
  countries. A triple whose interval sits above one is a polycrisis the
  pairwise links miss.

    jolt -A:tools -M -m bnm.polycrisis [--to 2002] [--reps 200]

  Prints the triples and writes data/runs/sequencing/polycrisis.tsv."
  (:require [augur.math :as m]
            [bnm.hypergraph :as hg]
            [bnm.kinds :as kinds]
            [clojure.string :as str]))

(defn windows
  "{country [simplex ...]}: each country's three-year windows from 1960 to
  `to`, the kinds covering it all window long, their onsets in it."
  [cats to]
  (let [countries (distinct (mapcat (comp keys :cover) (vals cats)))]
    (into {} (for [c countries]
               [c (vec (for [t (range 1960 (- to 1) 3)
                             :let [covered (filter #(when-let [[a b] (get-in cats [% :cover c])] (and (<= a t) (<= (+ t 2) b))) (keys cats))]
                             :when (>= (count covered) 3)]
                         {:covered (set covered)
                          :hit (hg/simplex (into {} (for [k covered] [k (get-in cats [k :onsets c] #{})])) t 3)}))]))))

(defn -main [& args]
  (let [{:keys [to reps]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        to (or (some-> to parse-long) 2002)
        reps (or (some-> reps parse-long) 200)
        cats (update-vals (kinds/catalogues) kinds/annual)
        ws (windows cats to)
        ks (sort (keys cats))
        g (m/rng 1)
        countries (vec (keys ws))
        obs-for (fn [cs triple] (for [c cs w (ws c) :when (every? (:covered w) triple)] (:hit w)))
        out (StringBuilder.)
        say (fn [s] (println s) (.append out (str s "\n")))]
    (say "triple\tn\tall-three\tlift\tlo\thi")
    (doseq [[a b c] (for [a ks b ks c ks :when (neg? (compare a b)) :when (neg? (compare b c))] [a b c])
            :let [obs (obs-for countries [a b c])
                  {:keys [lift observed n]} (hg/lift obs [a b c])]
            :when (and lift (>= (* observed n) 5))]
      (let [boots (sort (keep (fn [_] (:lift (hg/lift (obs-for (repeatedly (count countries) #(nth countries (long (* (m/next-double! g) (count countries))))) [a b c]) [a b c])))
                              (range reps)))
            q (fn [p] (nth boots (min (dec (count boots)) (long (* p (count boots))))))]
        (say (format "%s+%s+%s\t%d\t%d\t%.2f\t%.2f\t%.2f%s" a b c (long n) (Math/round (* observed n)) lift (q 0.05) (q 0.95)
                     (if (> (q 0.05) 1.0) "\t*" "")))))
    (spit "data/runs/sequencing/polycrisis.tsv" (str out))))
