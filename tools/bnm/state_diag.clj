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
  keeps the states that pass (bnm.relation-diag/passes?, the threshold
  corrected for every state tried), the strongest first, at most k, each
  with the design years' sign.

    jolt -A:tools -M -m bnm.state-diag --run t44-world-norel [--k 2] [--to 2002]
      [--states data/derived/stocks/food.csv,data/derived/stocks/keen.csv,...]
      [--out data/runs/sequencing/states-t45.edn]"
  (:require [bnm.ews-diag :as ed]
            [bnm.states :as states]
            [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.relation-diag :as rd]
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

(defn -main [& args]
  (let [{:keys [run to k states out]} (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        run (or run "t44-world-norel")
        to (or (some-> to parse-long) 2002)
        k (or (some-> k parse-long) 2)
        paths (if states (str/split states #",")
                  (filter #(.exists (io/file %))
                          (concat (map #(str "data/derived/stocks/" % ".csv") ["food" "keen" "tension"])
                                  ["data/derived/material/endowments.csv" "data/derived/material/capacity.csv"
                                   "data/derived/material/production.csv"])))
        st (states/read-states paths)
        ks (sort (keys kinds/tiers))
        years (into {} (pmap (fn [kd] [kd (rd/read-year run kd)]) ks))
        places (set (mapcat keys (vals years)))
        tree (fac/place-tree places)
        cs (sort (filter tree places))
        other (zipmap cs (concat (rest cs) [(first cs)]))
        own (states/own-parts st tree)
        results (atom {})
        buf (StringBuilder.)
        say (fn [s] (println s) (.append buf (str s "\n")))]
    (say (format "states from %s: %d variables" (str/join ", " paths) (count st)))
    (say "kind\tstate\tn\tpos\tlr\tcoef\tplacebo-lr\tplacebo-coef")
    (doseq [kd ks [v by-iso] (sort own)
            ;; as known at the origin's year end
            :let [rs (vec (ed/rows (get years kd) cs other (fn [c o] (get-in by-iso [c (quot o 12)])) to))
                  a (mapv first rs) b (mapv second rs)]
            :when (>= (count (filter :y a)) 5)]
      (let [ra (rd/lr-test a) rb (rd/lr-test b)]
        (swap! results assoc-in [kd v] {:lr (:lr ra) :coef (:coef ra) :placebo-lr (:lr rb)})
        (say (format "%s\t%s\t%d\t%d\t%.2f\t%+.3f\t%.2f\t%+.3f" kd v (:n ra) (:pos ra) (:lr ra) (:coef ra) (:lr rb) (:coef rb)))))
    (spit (str "data/runs/" run "/state-diag.tsv") (str buf))
    (let [kept (into (sorted-map) (for [[kd rs] @results :let [c (choose rs k)] :when (seq c)] [kd c]))]
      (println "states kept:" kept)
      (spit (or out "data/runs/sequencing/states-t45.edn") (pr-str {:paths (vec paths) :states kept})))))
