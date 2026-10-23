(ns bnm.located-shocks-run
  "The located-shock test of cell against country concentration (bead
  bnm-1pq.27; bnm.located-shocks; docs/ESSENTIALS.md section 8 step 10).

  Arms, each scored by bnm.located-shocks/compare-exposures:
    all            every hazard (the registered arm)
    natural        earthquakes, cyclones and floods
    earthquake, cyclone, flood, conflict   one hazard each
    time-placebo   every shock dated a year early
    space-1 ...    each country-year's hit cells moved to a random land
                   position in the country (seeds 1..--placebos; seed 1 the
                   registered one)

  Shock years --from to --to; their outcomes read output years from-3 to
  to+1, and outputs after 2010 are the registered test's: refused without
  --registered true (outputs past to+1 are dropped before anything is read).

    jolt -A:tools -M -m bnm.located-shocks-run --from 1991 --to 2009 --name located-dev
    jolt -A:tools -M -m bnm.located-shocks-run --from 2011 --to 2023 --name located-test --registered true

  Options: --draws 1000 (bootstrap), --placebos 19, --min-coverage 0.5,
  --threshold 0.05 (a unit's cell exposure that counts as exposed), --min-exposed 50,
  --clip 1.0 (the outcome held to plus or minus it; none for no clip),
  --unit-effects true ((key, country) effects as well as key-year ones),
  --backcast true (a sensitivity, not causal: assets read back from their
  start years, bnm.located-shocks/backcast).
  Writes data/runs/<name>/report.txt, results.edn and units.tsv (the all arm).

  --preview true reads no output: the exposed country-years a window would
  have at most (`preview`), for sizing the test before registering it:

    jolt -A:tools -M -m bnm.located-shocks-run --preview true --from 2011 --to 2023"
  (:require [bnm.cell-profiles :as cp]
            [bnm.cells :as cells]
            [bnm.located-shocks :as ls]
            [bnm.stages :as stages]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(defn- parse-args [args]
  (loop [[k v & more] args opts {}]
    (if k (recur more (assoc opts (keyword (subs k 2)) v)) opts)))

(def hazards ["earthquake" "cyclone" "flood" "conflict"])

(defn- fmt [x] (if (number? x) (format "%.4f" (double x)) "-"))
(defn- fmt-ci [[lo hi]] (if lo (format "[%+.4f, %+.4f]" (double lo) (double hi)) "-"))

(defn- outputs-through
  "The outputs of years up to `hi` only: the window's outcomes read no later
  year, and nothing else is read."
  [outputs hi]
  (into {} (for [[k q] outputs] [k (into {} (filter #(<= (key %) hi)) q)])))

(defn- coverage
  "Per hazard: shocks dated in the window, the cells they hit, the
  country-years with a hit, those with a plant of an outcome key known the
  year before."
  [shocks idx by-iso from to opts]
  (vec (for [h (concat hazards [nil])
             :let [ss (filter #(and (<= from (:year %) to) (or (nil? h) (= h (:hazard %)))) shocks)
                   hits (ls/country-hits idx (ls/hit-cells ss (assoc opts :hazards (when h #{h}))))
                   planted (filter (fn [[[iso y] _]] (seq (ls/known-keys by-iso iso y))) hits)]]
         {:hazard (or h "all") :shocks (count (distinct (map :shock ss)))
          :hit-shocks (count (distinct (keep #(when (>= (:coverage %) (:min-coverage opts 0.5)) (:shock %)) ss)))
          :cells (count (distinct (map :cell (filter #(>= (:coverage %) (:min-coverage opts 0.5)) ss))))
          :country-years (count hits) :with-plants (count planted)})))

(defn run
  [{:keys [from to name registered draws placebos min-coverage threshold min-exposed clip backcast unit-effects]
    :or {from "1991" to "2009" name "located-dev" draws "1000" placebos "19" min-coverage "0.5"
         threshold "0.05" min-exposed "50" clip "1.0"}}]
  (let [from (parse-long from) to (parse-long to)
        _ (ls/check-window from to (= registered "true"))
        t0 (System/currentTimeMillis)
        log (fn [& xs] (println (format "[%5ds]" (quot (- (System/currentTimeMillis) t0) 1000)) (str/join " " xs)) (flush))
        [lo-year hi-year] (ls/output-years from to)
        draws (parse-long draws) thr (parse-double threshold) min-exposed (parse-long min-exposed)
        opts {:min-coverage (parse-double min-coverage)}
        shocks (ls/read-shocks ls/shocks-path)
        assets (cp/read-assets "data/derived/cells/assets.csv")
        rows (ls/weigh (if (= backcast "true") (ls/backcast assets) assets))
        by-iso (ls/by-iso rows)
        idx (ls/land-index (cp/land-km2 (cells/load-crosswalk)))
        outputs (outputs-through (ls/read-outputs stages/in-path ls/cement-path ls/oil-path) hi-year)
        _ (log "shocks" (count (distinct (map :shock shocks))) "assets weighed" (count rows) "series" (count outputs)
               "output years" lo-year "-" hi-year)
        clip (when-not (= clip "none") (parse-double clip))
        panel (fn [hits] (ls/panel {:outputs outputs :by-iso by-iso :land-idx idx :hits hits :from from :to to
                                    :clip clip}))
        hits-of (fn [o] (ls/country-hits idx (ls/hit-cells shocks (merge opts o))))
        arm (fn [nm hits]
              (let [us (panel hits)
                    r (ls/compare-exposures us {:draws draws :seed 1 :unit-effects (= unit-effects "true")})]
                (log "arm" nm "units" (count us) "diff" (fmt (:diff r)))
                [nm (assoc (dissoc r :per-year) :units (count us)
                           :exposed (ls/exposed-country-years us thr)
                           :exposed-units (count (filter #(>= (:cell %) thr) us))
                           :hit-units (count (filter #(pos? (:country %)) us))
                           :per-year (:per-year r))
                 us]))
        main-hits (hits-of {})
        [_ main main-units] (arm "all" main-hits)
        arms (concat [["all" main]]
                     (map (comp vec (partial take 2))
                          (concat [(arm "natural" (hits-of {:hazards #{"earthquake" "cyclone" "flood"}}))]
                                  (for [h hazards] (arm h (hits-of {:hazards #{h}})))
                                  [(arm "time-placebo" (hits-of {:shift -1}))]
                                  (for [s (range 1 (inc (parse-long placebos)))]
                                    (arm (str "space-" s) (ls/move-hits idx main-hits s))))))
        results (into {} arms)
        spaces (keep (fn [[k v]] (when (str/starts-with? k "space-") v)) arms)
        cov (coverage shocks idx by-iso from to opts)
        per-key (into (sorted-map)
                      (for [[k us] (group-by :key main-units)]
                        [k {:units (count us) :hit (count (filter #(pos? (:country %)) us))
                            :exposed (count (filter #(>= (:cell %) thr) us))
                            :countries (count (distinct (map :iso us)))}]))
        powered? (>= (:exposed main) min-exposed)
        out (str "data/runs/" name)
        report
        (str "Located shocks: cell against country concentration (bnm-1pq.27)\n"
             "shock years " from "-" to ", output years " lo-year "-" hi-year
             (if (= registered "true") ", REGISTERED" ", development")
             (when (= backcast "true") ", BACKCAST (assets read back from their start years: not causal)") "\n"
             "hit: coverage >= " (:min-coverage opts) "; exposed: cell exposure >= " thr
             "; outcome clipped at " (or clip "none")
             (if (= unit-effects "true") "; key-year and (key, country) effects" "; key-year effects") "\n\n"
             "catalogue in the window       shocks  hitting  cells  country-years  with plants\n"
             (apply str (for [c cov]
                          (format "  %-26s %7d %8d %6d %14d %12d\n" (:hazard c) (:shocks c) (:hit-shocks c) (:cells c)
                                  (:country-years c) (:with-plants c))))
             "\nunits by key (all arm)   units   hit  exposed  countries\n"
             (apply str (for [[k v] per-key]
                          (format "  %-20s %8d %5d %8d %10d\n" k (:units v) (:hit v) (:exposed v) (:countries v))))
             "\nexposed country-years: " (:exposed main) (if powered? " (read)" (str " < " min-exposed ": underpowered, not read")) "\n\n"
             "arm             units  hit  exp-cy  mse-null  mse-cell  mse-country  cell-country  90% interval          verdict  slope-cell slope-country\n"
             (apply str (for [[k v] arms]
                          (format "  %-13s %6d %4d %6d   %s    %s    %s     %+.5f  %s  %-7s %+8.3f %+8.3f\n"
                                  k (:units v) (:hit-units v) (:exposed v) (fmt (:mse-null v)) (fmt (:mse-cell v))
                                  (fmt (:mse-country v)) (double (:diff v)) (fmt-ci (:interval v))
                                  (if (< (:exposed v) min-exposed) "under" (clojure.core/name (:verdict v)))
                                  (double (:slope-cell v)) (double (:slope-country v)))))
             (when (seq spaces)
               (format "\nspace placebos: %d of %d at or below the all arm's difference (%+.5f); mean %+.5f\n"
                       (count (filter #(<= (:diff %) (:diff main)) spaces)) (count spaces) (double (:diff main))
                       (/ (reduce + (map :diff spaces)) (count spaces))))
             "\nverdict \"under\": fewer than " min-exposed " exposed country-years in the arm, not read\n"
             "\nverdict (all arm): "
             (cond (not powered?) "underpowered, not read"
                   (= :cell (:verdict main)) "cell wins"
                   :else "cell does not win: keep countries")
             "\n")]
    (io/make-parents (str out "/x"))
    (spit (str out "/units.tsv")
          (apply str "key\tiso\tyear\ty\tcell\tcountry\n"
                 (for [u main-units] (str (str/join "\t" ((juxt :key :iso :year :y :cell :country) u)) "\n"))))
    (spit (str out "/results.edn") (with-out-str (pp/pprint {:from from :to to :registered (= registered "true")
                                                             :coverage cov :per-key per-key :arms results})))
    (spit (str out "/report.txt") report)
    (println report)
    (log "done")
    results))

(defn preview
  "Exposure in shock years `from`-`to` without reading any output: per key,
  the country-years with a plant known the year before and a hit, and those
  whose cell exposure reaches the threshold (an upper bound on the units:
  some will have no outcome). For sizing the test before it is registered."
  [{:keys [from to min-coverage threshold backcast] :or {min-coverage "0.5" threshold "0.05"}}]
  (let [from (parse-long from) to (parse-long to) thr (parse-double threshold)
        opts {:min-coverage (parse-double min-coverage)}
        shocks (ls/read-shocks ls/shocks-path)
        assets (cp/read-assets "data/derived/cells/assets.csv")
        by-iso (ls/by-iso (ls/weigh (if (= backcast "true") (ls/backcast assets) assets)))
        idx (ls/land-index (cp/land-km2 (cells/load-crosswalk)))
        hits (ls/country-hits idx (ls/hit-cells shocks opts))
        xs (for [[[iso y] cut] hits :when (<= from y to)
                 :let [known (ls/known-keys by-iso iso y) cx (ls/cell-exposure by-iso iso y cut)]
                 k known]
             {:key k :iso iso :year y :cell (get cx k 0.0)})
        by-key (group-by :key xs)
        report (str "Located shocks, exposure preview (no outputs read): shock years " from "-" to
                    (when (= backcast "true") ", BACKCAST") "\n"
                    "key              hit units  exposed units  exposed country-years\n"
                    (apply str (for [[k us] (sort-by key by-key)]
                                 (format "  %-16s %9d %14d %22d\n" k (count us) (count (filter #(>= (:cell %) thr) us))
                                         (ls/exposed-country-years us thr))))
                    (format "  %-16s %9d %14d %22d\n" "all" (count xs) (count (filter #(>= (:cell %) thr) xs))
                            (ls/exposed-country-years xs thr))
                    "\ncatalogue in the window       shocks  hitting  cells  country-years  with plants\n"
                    (apply str (for [c (coverage shocks idx by-iso from to opts)]
                                 (format "  %-26s %7d %8d %6d %14d %12d\n" (:hazard c) (:shocks c) (:hit-shocks c)
                                         (:cells c) (:country-years c) (:with-plants c)))))]
    (println report)
    report))

(defn -main [& args]
  (let [o (parse-args args)]
    (if (= "true" (:preview o)) (preview o) (run o)))
  (shutdown-agents))
