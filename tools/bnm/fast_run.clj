(ns bnm.fast-run
  "The world without relations (bnm.world-run --without-relations true,
  the base t44-world-norel) with one family of fast signals at its quarter
  nodes (bnm.fast, bead bnm-82j.2).

    jolt -A:tools -M -m bnm.fast-run --out fs-a --family commodities [--placebo true]
                                     [--end m] [--kinds a,b] [--smoke true]

  --family none runs the base itself (a regression check against
  t44-world-norel). With --placebo, each country reads the family's inputs
  at the next country in sorted order (bnm.world-run/placebo-source).

  Without relations the kinds do not read each other, so each kind's
  lattice runs on its own (the same forecasts as stepping them together).
  Writes data/runs/<out>/<kind>/{forecasts,preds,weights}.tsv as
  bnm.world-run does."
  (:require [bnm.fast :as fast]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.lattice-smoke :as smoke]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [bnm.world-run :as wr]
            [clojure.string :as str]))

(def series-path "data/fast/series.tsv")

(defn exposure-fn
  "(fn [country t]) -> {:food :energy :fertiliser :open}: food imports, fuel
  imports less fuel exports (shares of merchandise trade), agriculture's
  share of output and trade over output, as fractions, read two years
  before the origin (bnm.named/structure's rule: fixed before the shock,
  past WDI's revisions); nil where unknown."
  [db]
  (fn [c t]
    (let [g (fn [v] (some-> (db [c v]) (sk/value (- t 24) {:stale 30}) (/ 100.0)))
          imp (g "energy/fuel_imports_share") exp (g "energy/fuel_exports_share")]
      {:food (g "food/imports_share")
       :energy (when (and imp exp) (- imp exp))
       :fertiliser (g "economy/agriculture_share")
       :open (g "trade/openness")})))

(def group-of {:commodities :fast/commodities :fx :fast/fx :ports :fast/ports})

(defn -main [& args]
  (let [{:keys [out family placebo end kinds smoke]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        family (keyword (or family "none"))
        _ (when-not (or (= family :none) (fast/families family))
            (throw (ex-info (str "bnm.fast-run: no family " family) {})))
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        cats (kinds/catalogues)
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        countries (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats)))
        source (if (= "true" placebo) (wr/placebo-source countries) identity)
        inputs (when (not= family :none)
                 (fast/family-inputs family (fast/read-series series-path)
                                     (exposure-fn (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv")))))
        _ (println "building" (count ks) "kinds, family" (name family) (if (= "true" placebo) "(placebo)" ""))
        specs (into {} (for [[k sp] (wr/build-specs ks end cats)]
                         [k (cond-> sp
                              inputs (fast/with-fast inputs (count (fast/families family)) (group-of family) source)
                              (= "true" smoke) (assoc :trace :inputs))]))
        _ (println "running")
        finished (into {} (pmap (fn [[k sp]] (let [r (lt/run sp)] (println "done" k) [k r])) specs))]
    (if (= "true" smoke)
      (let [fails (into {} (for [[k r] (sort-by key finished)] (do (println "==" k) [k (smoke/checks (specs k) r k end)])))]
        (doseq [[k fs] fails] (println k (if (empty? fs) "all checks passed" (str (count fs) " failed: " (str/join "; " fs)))))
        (System/exit (if (every? empty? (vals fails)) 0 1)))
      (do (doseq [[k r] finished] (#'wr/write-kind (str dir "/" k) k r))
          (println "wrote" dir)
          (shutdown-agents)))))
