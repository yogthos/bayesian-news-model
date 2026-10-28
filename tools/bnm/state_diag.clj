(ns bnm.state-diag
  "Which stock-flow states carry signal past each kind's forecast (bead
  bnm-62z.13: events as threshold crossings of stocks)? The states are
  country-year tables (data/derived/stocks/*.csv, data/derived/material/
  *.csv: iso,year,variable,value, the year the value is known). On the
  design years (year-end origins 1960-2002), for each kind and state, a
  logit of the kind's year outcome with its forecast from a run without
  relations as offset, on the state's own part at the country
  (bnm.factors) as known at the origin, against the same read at another
  country (the placebo), on the same rows (bnm.ews-diag/rows). Each kind
  keeps the states that pass (bnm.relation-diag/clustered-test, years as
  clusters; bnm.relation-diag/passes?, the threshold
  corrected for every state tried), the strongest first, at most k, each
  with the design years' sign.

    jolt -A:tools -M -m bnm.state-diag --run t44-world-norel [--k 2] [--to 2002]
      [--states data/derived/stocks/food.csv,data/derived/stocks/keen.csv,...]
      [--out data/runs/sequencing/states-t45.edn]"
  (:require [bnm.states :as states]
            [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.relation-diag :as rd]
            [bnm.warm-up :as wu]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn choose
  "A kind's states from its design-year results {state {:lr :coef
  :placebo-lr}}: those passing (corrected for every state tried), the
  strongest first, at most `k`, as [[state sign] ...]."
  [results k]
  (let [n (count results)]
    (->> results
         (filter (fn [[_ r]] (rd/passes? r n)))
         (sort-by (comp - :lr val))
         (take k)
         (mapv (fn [[s {:keys [coef]}]] [s (if (neg? coef) -1 1)])))))

(defn rows
  "[[real placebo] ...] for a kind's design-year origins (`years` {country
  (sorted-map origin [p y])}): each country's logit row with the state as
  `real` reads it and as `placebo` does ((fn [country origin])), kept
  only where both are known, so both are fitted on the same rows."
  [years cs real placebo to]
  (for [c cs [o [p y]] (get years c)
        :when (and (some? y) (rd/design-origin? o to))
        :let [a (real c o) b (placebo c o)]
        :when (and (some? a) (some? b))
        :let [r {:y y :o (Math/log (/ (max 1e-6 p) (max 1e-6 (- 1.0 p)))) :w 1.0 :g (quot o 12)}]]
    [(assoc r :x [1.0 (double a)]) (assoc r :x [1.0 (double b)])]))

(def material-lag
  "Years after its data year a material table's value is known: FAOSTAT
  land, EIA capacity and energy production are published a year or two
  late (scripts/material.py)."
  2)

(defn default-paths
  "The state tables that exist: the stocks by the year known, the material
  base by data year, lagged ([path lag])."
  []
  (concat (filter #(.exists (io/file %)) (map #(str "data/derived/stocks/" % ".csv") ["food" "keen" "tension" "energy" "technology" "cycles"]))
          (for [f ["endowments" "capacity" "production"] :let [p (str "data/derived/material/" f ".csv")] :when (.exists (io/file p))]
            [p material-lag])))

(defn -main [& args]
  (let [{:keys [run to k states out]} (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        run (or run "t44-world-norel")
        to (or (some-> to parse-long) 2002)
        k (or (some-> k parse-long) 2)
        paths (if states (str/split states #",") (default-paths))
        st (states/read-states paths)
        ks (sort (keys kinds/tiers))
        ;; the base's warm-up rows (bnm.warm-up) are no offset to test a state past
        years (into {} (pmap (fn [kd] [kd (wu/drop-node (rd/read-year run kd) (wu/known-lag kd))]) ks))
        places (set (mapcat keys (vals years)))
        tree (fac/place-tree places)
        cs (sort (filter tree places))
        other (zipmap cs (concat (rest cs) [(first cs)]))
        pairs (states/default-pairs st)
        world (set (keys (states/world-series st)))
        read (states/readers st tree pairs)
        results (atom {})
        buf (StringBuilder.)
        say (fn [s] (println s) (.append buf (str s "\n")))]
    (say (format "states from %s: %d variables, %d world, exposures %s" (str/join ", " (map #(if (vector? %) (str (first %) " (lag " (second %) ")") %) paths)) (count read) (count world) pairs))
    (say "kind\tstate\tn\tpos\tlr\tcoef\tplacebo-lr\tplacebo-coef")
    (doseq [kd ks [v f] (sort-by key read)
            ;; as known at the origin's year end; the placebo another
            ;; country's, or for a world series the same ten years before
            :let [rs (vec (rows (get years kd) cs
                                (fn [c o] (f c (quot o 12)))
                                (if (world v) (fn [c o] (f c (- (quot o 12) 10))) (fn [c o] (f (other c) (quot o 12))))
                                to))
                  a (mapv first rs) b (mapv second rs)]
            :when (>= (count (filter :y a)) 5)]
      (let [ra (rd/clustered-test a) rb (rd/clustered-test b)]
        (swap! results assoc-in [kd v] {:lr (:lr ra) :coef (:coef ra) :placebo-lr (:lr rb)})
        (say (format "%s\t%s\t%d\t%d\t%.2f\t%+.3f\t%.2f\t%+.3f" kd v (:n ra) (:pos ra) (:lr ra) (:coef ra) (:lr rb) (:coef rb)))))
    (spit (str "data/runs/" run "/state-diag.tsv") (str buf))
    (let [kept (into (sorted-map) (for [[kd rs] @results :let [c (choose rs k)] :when (seq c)] [kd c]))]
      (println "states kept:" kept)
      (spit (or out "data/runs/sequencing/states-t45.edn") (pr-str {:paths (vec paths) :states kept})))))
