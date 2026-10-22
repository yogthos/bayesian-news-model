(ns bnm.influence
  "The influence map built from what the system holds (bnm.network): each
  country's input-output tables for a year as its sector layer
  (data/derived/io/spine-*.csv), the event graph as the kinds' layer.

  For each economy, the spectral radius (the share of a shock that comes
  round again each pass of the supply chain; the same for A and B, which
  are similar matrices) and two rankings:
    cost-push reach: technical coefficients A (a_rs = z_rs / x_s, the
      share of s's output paid to r) passed on as x A, the Leontief price
      model; a sector's influence with alpha 1 is the summed price rise
      across sectors from a unit cost rise at it.
    supply-push upstreamness: allocation coefficients B (b_rs = z_rs /
      x_r, the share of r's output sold to s, Ghosh); 1 + a sector's
      influence with alpha 1 is its upstreamness (Antras et al. 2012),
      how many stages it stands from final use.
  The spine's flows hold domestic and imported inputs together, so both
  overstate domestic propagation where an economy imports much; the
  spine cannot separate them (LR-WIOD before 2000 never does; WIOD 2016
  and ICIO do in their raw tables). B suffers most: a sector whose
  intermediate sales exceed its own output (Japan's mining sells 25 times
  what it mines) has b row sums above 1, so it is listed, not ranked.

  The kinds' layer is weighted from the graph's data (bnm.network/
  graph-weight: |log rr-lead|, signed), its influence at half the alpha at
  which the sum diverges; --topology ranks it by the signs alone.

    jolt -A:tools -M -m bnm.influence [--year 2019] [--graph path] [--topology true]"
  (:require [bnm.network :as nw]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn tables
  "{country {:flows {[from to] z} :go {sector x}}} for `year`."
  [year]
  (let [y (str year)
        go (with-open [r (io/reader "data/derived/io/spine-sectors.csv")]
             (reduce (fn [acc l]
                       (let [[c yy s g] (str/split l #",")]
                         (if (and (= yy y) (seq g)) (assoc-in acc [c :go s] (parse-double g)) acc)))
                     {} (rest (line-seq r))))]
    (with-open [r (io/reader "data/derived/io/spine-flows.csv")]
      (reduce (fn [acc l]
                (let [[c yy from to v] (str/split l #",")]
                  (if (and (= yy y) (acc c)) (assoc-in acc [c :flows [from to]] (parse-double v)) acc)))
              go (rest (line-seq r))))))

(defn- top [inf k f]
  (str/join ", " (map #(format "%s %.2f" (last (key %)) (f (val %))) (take k (sort-by (comp - val) inf)))))

(defn -main [& args]
  (when (odd? (count args))
    (throw (ex-info "bnm.influence: options come in pairs, --name value" {:args args})))
  (let [opts (into {} (for [[k v] (partition 2 args)]
                        (if (str/starts-with? k "--")
                          [(keyword (subs k 2)) v]
                          (throw (ex-info "bnm.influence: an option starts with --" {:option k})))))
        year (or (some-> (:year opts) parse-long) 2019)
        ts (tables year)
        g (wr/read-graph (or (:graph opts) "data/runs/sequencing/graph-t44.edn"))]
    (println "economies" (count ts) "in" year "(flows domestic and imported together)")
    (doseq [c ["USA" "DEU" "CHN" "JPN" "IND" "BRA" "RUS"] :when (ts c)
            :let [{:keys [flows go]} (ts c)
                  na (nw/from-io (nw/network) c (nw/cost-shares flows go))
                  nb (nw/from-io-supply (nw/network) c (nw/sales-shares flows go))]]
      (let [b (nw/sales-shares flows go)
            sold (reduce (fn [m [[r _] v]] (update m r (fnil + 0.0) v)) {} b)
            over (into (sorted-map) (filter #(>= (val %) 1.0) sold))
            up (remove (fn [[node _]] (over (last node))) (nw/influence nb 1.0))]
        (println (format "%s spectral radius %.3f\n  cost-push reach %s\n  supply-push upstreamness %s%s" c (nw/spectral-radius na)
                         (top (nw/influence na 1.0) 4 identity) (top up 4 inc)
                         (if (seq over)
                           (str "\n    not ranked, selling more than they make (imports in the flows): "
                                (str/join ", " (map #(format "%s %.1fx" (key %) (val %)) over)))
                           "")))))
    (let [topology? (= "true" (:topology opts))
          kinds (nw/from-graph (nw/network) g ["any"] {:topology? topology?})
          rho (nw/spectral-radius kinds)
          alpha (if (pos? rho) (/ 0.5 rho) 1.0)]
      (println (format "kinds (%s): spectral radius %.2f; alpha %.2f; transmitting most %s"
                       (if topology? "signs only" "weighted by |log rr-lead|") rho alpha
                       (top (nw/influence kinds alpha) 5 identity))))))
