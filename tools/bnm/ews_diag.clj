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
            [bnm.stocks :as sk]
            [clojure.string :as str]))

(def series ["economy/gdp_growth" "economy/inflation" "food/food_inflation"])
(def indicators [:ac1 :variance :ac1-trend :variance-trend])

(defn choose
  "A kind's early warning from its design-year results {[series indicator]
  {:lr :coef :placebo-lr}}: of those gaining past the forecast (LR >=
  3.84) and at least twice their placebo's, the one gaining most, as
  [series indicator sign], its sign the design years' (theory's, more
  slowing down more risk, did not hold); nil without any."
  [results]
  (when-let [ok (seq (filter (fn [[_ {:keys [lr placebo-lr]}]] (and (>= lr 3.84) (>= lr (* 2.0 placebo-lr)))) results))]
    (let [[[v ind] {:keys [coef]}] (apply max-key (comp :lr val) ok)]
      [v ind (if (neg? coef) -1 1)])))

(defn own-indicators
  "{country {data-year {indicator value}}}: the early warnings of each
  country's own part of a series, over the `window` data years ending at
  each year; the parts and windows causal."
  [panel tree window]
  (let [parts (fac/decompose panel tree {:discount 0.99})]
    (into {} (for [[c byy] parts
                   :let [ys (sort (keys byy))
                         sig (ews/indicators (map #(get-in byy [% :own]) ys) {:window window})]]
               [c (into {} (map (fn [y s] [y s]) ys sig))]))))

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
        ;; each series by country and data year, as the year gauges read it
        sigs (into {} (pmap (fn [v]
                              (let [panel (into {} (for [c cs :let [byy (into (sorted-map)
                                                                            (for [y (range 1950 2026)
                                                                                  :let [x (some-> (db [c v]) (sk/value (+ 11 (* 12 y)) {:stale 18}))]
                                                                                  :when x] [y (double x)]))]
                                                         :when (seq byy)] [c byy]))]
                                [v (own-indicators panel tree window)]))
                            series))
        out (StringBuilder.)
        results (atom {})
        say (fn [s] (println s) (.append out (str s "\n")))]
    (say "kind\tseries\tindicator\tn\tpos\tlr\tcoef\tplacebo-lr\tplacebo-coef")
    (doseq [k ks v series ind indicators
            :let [rows-of (fn [placebo?]
                            (for [c cs [o [p y]] (get-in years [k c])
                                  :when (and (some? y) (= 11 (mod o 12)) (<= (* 12 1960) o (+ 11 (* 12 to))))
                                  :let [src (if placebo? (other c) c)
                                        ;; the data year before the origin's
                                        x (get-in sigs [v src (dec (quot o 12)) ind])]
                                  :when x]
                              {:x [1.0 (double x)] :y y :o (Math/log (/ (max 1e-6 p) (max 1e-6 (- 1.0 p)))) :w 1.0}))
                  a (vec (rows-of false)) b (vec (rows-of true))]
            :when (>= (count (filter :y a)) 5)]
      (let [ra (rd/lr-test a) rb (rd/lr-test b)]
        (swap! results assoc-in [k [v ind]] {:lr (:lr ra) :coef (:coef ra) :placebo-lr (:lr rb)})
        (say (format "%s\t%s\t%s\t%d\t%d\t%.2f\t%+.3f\t%.2f\t%+.3f%s" k v (name ind) (:n ra) (:pos ra) (:lr ra) (:coef ra) (:lr rb) (:coef rb)
                     (if (and (> (:lr ra) 3.84) (>= (:lr ra) (* 2.0 (:lr rb)))) "\t*" "")))))
    (spit (str "data/runs/" run "/ews-diag.tsv") (str out))
    ;; each kind's warning (trial 44's)
    (let [kept (into (sorted-map) (for [[k rs] @results :let [c (choose rs)] :when c] [k c]))]
      (println "warnings kept:" kept)
      (spit (or ews-out "data/runs/sequencing/ews-t44.edn") (pr-str kept)))))
