(ns bnm.geo-gap-run
  "H9 (bead bnm-3ka.9): the capability-gap stock and the dyad panel.

    python -I scripts/geo-gap.py
    jolt -A:tools -M -m bnm.geo-gap-run

  Writes data/derived/stocks/geo-capability.csv and
  data/derived/econ/geo-h9.txt."
  (:require [bnm.econ-coupling :as ec]
            [bnm.geo-gap :as gg]
            [clojure.string :as str]))

(def powers ["USA" "CHN" "RUS" "JPN" "DEU" "GBR" "FRA" "IND"])
(def out-path "data/derived/econ/geo-h9.txt")
(def stock-path "data/derived/stocks/geo-capability.csv")

(defn -main [& _]
  (let [inputs (gg/read-inputs "data/derived/econ/geo-inputs.csv")
        rows (gg/stock-rows inputs)
        sr (gg/series inputs)
        dyads (mapv #(assoc % :er (when (or (:er-ab %) (:er-ba %)) (Math/log1p (+ (or (:er-ab %) 0.0) (or (:er-ba %) 0.0)))))
                    (gg/read-dyads "data/derived/econ/geo-dyads.csv"))
        buf (StringBuilder.)
        say (fn [s] (println s) (.append buf (str s "\n")))]
    (gg/write-stock rows stock-path)
    (say "# H9: material share (steel, energy, manufacturing VA) less financial share (market cap, FX reserves), world shares")
    (say "# as known at each year end (latest data year published, at most 3 years old)")
    (doseq [v ["geo/material-share" "geo/financial-share" "geo/capability-gap"]]
      (say (str "# " v "\t" (str/join "\t" (for [t [1997 2002 2007 2012 2017 2022]] t))))
      (doseq [iso powers]
        (say (str iso "\t" (str/join "\t" (for [t [1997 2002 2007 2012 2017 2022]]
                                            (if-let [x (gg/as-known (get-in sr [v iso]) t)] (format "%+.3f" x) ".")))))))
    (say "# dyads: the 28 pairs of the eight; level = |gap_a - gap_b| known at t, outcome in t + 1")
    (say "# dyad and year fixed effects, SE clustered by dyad; permutation p: 999 draws handing each dyad another's series")
    (doseq [[label gapv k pred] [["MID ongoing ~ gap distance" "geo/capability-gap" :mid-any :level]
                                 ["MID onset ~ gap distance" "geo/capability-gap" :mid-onset :level]
                                 ["MID ongoing ~ 5y widening" "geo/capability-gap" :mid-any :change]
                                 ["restrictions ~ gap distance" "geo/capability-gap" :er :level]
                                 ["restrictions ~ 5y widening" "geo/capability-gap" :er :change]
                                 ["MID ongoing ~ material distance (record)" "geo/material-share" :mid-any :level]]]
      (let [panel (->> (gg/dyad-panel (get sr gapv) dyads k)
                       (keep #(when-let [x (get % pred)] (assoc % :x [x])))
                       vec)]
        (when (> (count panel) 30)
          (let [{:keys [b se t n groups]} (ec/fe-ols panel)
                varying (count (filter (fn [[_ rs]] (> (count (distinct (map :y rs))) 1)) (group-by :g panel)))
                pos (count (filter #(pos? (:y %)) panel))
                yrs (map :t panel)]
            (say (format "%-42s b %+.4f se %.4f t %+.2f  perm p %.3f  n %d  dyads %d (outcome varies in %d; %d dyad-years > 0)  origins %d-%d"
                         label (first b) (first se) (first t) (gg/permutation-p panel 999 1) n groups varying pos
                         (apply min yrs) (apply max yrs)))))))
    (say "# US-China: capability gaps, MIDs and restrictions by year (origin t, outcome t + 1)")
    (doseq [t (range 1997 2024)
            :let [r (first (filter #(and (= "CHN" (:a %)) (= "USA" (:b %)) (= (inc t) (:year %))) dyads))
                  gc (gg/as-known (get-in sr ["geo/capability-gap" "CHN"]) t)
                  gu (gg/as-known (get-in sr ["geo/capability-gap" "USA"]) t)]
            :when (and gc gu)]
      (say (format "%d\tCHN %+.3f\tUSA %+.3f\tdistance %.3f\tMID(t+1) %s\tCHN->USA %s\tUSA->CHN %s" t gc gu (Math/abs (- gc gu))
                   (or (some-> (:mid-any r) long str) ".") (or (some->> (:er-ab r) (format "%.2f")) ".") (or (some->> (:er-ba r) (format "%.2f")) "."))))
    (spit out-path (str buf))
    (println "wrote" out-path stock-path)))
