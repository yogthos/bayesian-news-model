(ns bnm.influence
  "The influence map built from what the system holds (bnm.network): each
  country's input-output coefficients for a year as its sector layer
  (data/derived/io/spine-*.csv: a_rs, the share of s's output paid to r),
  the event graph as the kinds' layer. Prints each economy's spectral
  radius (how far a sector shock is amplified round the supply chain)
  and the sectors and kinds that transmit most.

    jolt -A:tools -M -m bnm.influence [--year 2019] [--graph path]"
  (:require [bnm.network :as nw]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn coefficients
  "{country {[from to] a}} for `year`: intermediate flows over the using
  sector's gross output."
  [year]
  (let [y (str year)
        go (with-open [r (io/reader "data/derived/io/spine-sectors.csv")]
             (into {} (for [l (rest (line-seq r)) :let [[c yy s g] (str/split l #",")] :when (and (= yy y) (seq g))]
                        [[c s] (parse-double g)])))]
    (with-open [r (io/reader "data/derived/io/spine-flows.csv")]
      (reduce (fn [acc l]
                (let [[c yy from to v] (str/split l #",")]
                  (if-let [g (and (= yy y) (go [c to]))]
                    (if (pos? g) (assoc-in acc [c [from to]] (/ (parse-double v) g)) acc)
                    acc)))
              {} (rest (line-seq r))))))

(defn -main [& args]
  (let [{:keys [year graph]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        year (or (some-> year parse-long) 2019)
        coef (coefficients year)
        g (wr/read-graph (or graph "data/runs/sequencing/graph-t44.edn"))]
    (println "economies" (count coef) "in" year)
    (doseq [c ["USA" "DEU" "CHN" "JPN" "IND" "BRA" "RUS"] :when (coef c)
            :let [n (nw/from-io (nw/network) c (coef c))
                  inf (nw/influence n 1.0)]]
      (println (format "%s spectral radius %.3f; most influential sectors %s" c (nw/spectral-radius n)
                       (str/join ", " (map #(format "%s %.2f" (last (key %)) (val %)) (take 4 (sort-by (comp - val) inf)))))))
    ;; the kinds' loops (conflict and violence feed each other) have radius 1:
    ;; influence attenuated by half a step
    (let [kinds (nw/from-graph (nw/network) g ["any"] 1.0)]
      (println (format "kinds: spectral radius %.2f; transmitting most %s" (nw/spectral-radius kinds)
                       (str/join ", " (map #(format "%s %.2f" (last (key %)) (val %)) (take 5 (sort-by (comp - val) (nw/influence kinds 0.5))))))))))
