(ns bnm.ews-diag
  "Do early warnings of a critical transition (bnm.ews) carry signal past
  each kind's own forecast? On the design years only (year-end origins
  1960-2002), for each kind, each series and each indicator, a logit of
  the kind's year outcome with its forecast from a run without relations
  as offset (bnm.relation-diag/lr-test), on the indicator of the
  country's own part (bnm.factors) of the series over the 12 data years
  before the origin, and the same read at another country (a placebo).

    jolt -A:tools -M -m bnm.ews-diag --run t42-world-norel [--to 2002] [--window 12] [--ews-out path]

  Prints the table and writes data/runs/<run>/ews-diag.tsv, and each
  kind's warning (choose) to data/runs/sequencing/ews-t44.edn."
  (:require [bnm.ews :as ews]
            [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.named :as nm]
            [bnm.relation-diag :as rd]
            [bnm.slow-train :as st]
            [clojure.string :as str]))

(def series ["economy/gdp_growth" "economy/inflation" "food/food_inflation"])
(def indicators [:ac1 :log-variance :ac1-trend :log-variance-trend])

(defn choose
  "A kind's early warning from its design-year results {[series indicator]
  {:lr :coef :placebo-lr}}: of those passing (bnm.relation-diag/passes?,
  the threshold corrected for the kind's candidates), the one gaining
  most, as [series indicator sign], its sign the design years' (theory's,
  more slowing down more risk, did not hold); nil without any."
  [results]
  (let [k (count results)]
    (when-let [ok (seq (filter (fn [[_ r]] (rd/passes? r k)) results))]
      (let [[[v ind] {:keys [coef]}] (apply max-key (comp :lr val) ok)]
        [v ind (if (neg? coef) -1 1)]))))

(defn rows
  "[[real placebo] ...] for kind `k`'s design-year origins: each country's
  logit row with the indicator at the country and at its placebo, kept
  only where both are known, so real and placebo are fitted on the same
  rows. `x-of` (fn [country origin]) the indicator or nil."
  [years cs other x-of to]
  (for [c cs [o [p y]] years
        :when (and (some? y) (= 11 (mod o 12)) (<= (* 12 1960) o (+ 11 (* 12 to))))
        :let [a (x-of c o) b (x-of (other c) o)]
        :when (and (some? a) (some? b))
        :let [r {:y y :o (Math/log (/ (max 1e-6 p) (max 1e-6 (- 1.0 p)))) :w 1.0}]]
    [(assoc r :x [1.0 (double a)]) (assoc r :x [1.0 (double b)])]))

(defn -main [& args]
  (let [{:keys [run to window ews-out]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        run (or run "t42-world-norel")
        to (or (some-> to parse-long) 2002)
        window (or (some-> window parse-long) 12)
        db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
        ks (sort (keys kinds/tiers))
        years (into {} (pmap (fn [k] [k (rd/read-year run k)]) ks))
        places (set (mapcat keys (vals years)))
        tree (fac/place-tree places)
        cs (sort (filter tree places))
        other (zipmap cs (concat (rest cs) [(first cs)]))
        sigs (into {} (pmap (fn [v] [v (ews/own-indicators (ews/series-panel db cs v) tree window)]) series))
        out (StringBuilder.)
        results (atom {})
        say (fn [s] (println s) (.append out (str s "\n")))]
    (say "kind\tseries\tindicator\tn\tpos\tlr\tcoef\tplacebo-lr\tplacebo-coef")
    (doseq [k ks v series ind indicators
            ;; as known at the origin's year end
            :let [rs (vec (rows (get years k) cs other (fn [c o] (get-in sigs [v c (quot o 12) ind])) to))
                  a (mapv first rs) b (mapv second rs)]
            :when (>= (count (filter :y a)) 5)]
      (let [ra (rd/lr-test a) rb (rd/lr-test b)
            r {:lr (:lr ra) :coef (:coef ra) :placebo-lr (:lr rb)}]
        (swap! results assoc-in [k [v ind]] r)
        (say (format "%s\t%s\t%s\t%d\t%d\t%.2f\t%+.3f\t%.2f\t%+.3f" k v (name ind) (:n ra) (:pos ra) (:lr ra) (:coef ra) (:lr rb) (:coef rb)))))
    (spit (str "data/runs/" run "/ews-diag.tsv") (str out))
    ;; each kind's warning, with the window it was chosen on
    (let [kept (into (sorted-map) (for [[k rs] @results :let [c (choose rs)] :when c] [k c]))]
      (println "warnings kept:" kept)
      (spit (or ews-out "data/runs/sequencing/ews-t44.edn") (pr-str {:window window :warnings kept})))))
