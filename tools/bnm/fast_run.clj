(ns bnm.fast-run
  "The world without relations (bnm.world-run --without-relations true,
  the base t44-world-norel) with one family of fast signals at its quarter
  nodes (bnm.fast, bead bnm-82j.2).

    jolt -A:tools -M -m bnm.fast-run --out fs-a --family commodities [--placebo true]
                                     [--seed s] [--null derange|shift] [--end m] [--kinds a,b] [--smoke true]
                                     [--set '{:forget 1.0 :coupling nil}'] [--archive data/ledger-inputs/2026-Q4]

  --set merges an EDN map into every kind's spec after the family is put
  on it (a nil value removes the key): the diagnostics and the weighting
  candidates of bnm-56c.2 (bnm.lattice's :weighting).

  --archive root reads the base's inputs (bnm.ledger/inputs) from an
  issue's archive instead of data/ (the first issue's holds t48's): runs
  compared with t48-world-norel need its inputs, and the data on disk are
  rebuilt under them (bnm-8ul).

  --family planted [--strength s] is the noise group with its first
  column moved by s (default 1.0) where the quarter node's outcome happens
  (bnm.fast/planted-inputs: it reads the future, a diagnostic only): a
  link that carries something, for bnm-56c.2's check that a weighting
  which ignores noise still lets a real group earn weight.

  --family none runs the base itself (a regression check against
  t44-world-norel). --family noise (reported only, not a trial) puts a
  group of nine standard normal draws per country and origin at the
  quarter nodes, FSa's width: what a group of that width costs by itself;
  --seed s draws another such group (default 1: fs-noise-mixed; fs-noise
  and fs-noise-fix read the lattice-seeded noise before bnm-0z5), one draw of
  the watch-list rule's null (bnm.watchlist, v2). The rule's shuffled
  draws: --null derange (each country reads the family at another, a
  random derangement seeded by --seed, bnm.fast/derange-source) or --null
  shift (the family read `shift-months` of --seed before the origin).
  With --placebo, each country reads the family's inputs at the next
  country in sorted order (bnm.world-run/placebo-source).

  Without relations the kinds do not read each other, so each kind's
  lattice runs on its own (the same forecasts as stepping them together).
  Writes data/runs/<out>/<kind>/{forecasts,preds,weights}.tsv as
  bnm.world-run does."
  (:require [bnm.fast :as fast]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.ledger :as lg]
            [bnm.lattice-smoke :as smoke]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [bnm.world-gauges :as wg]
            [bnm.world-run :as wr]
            [clojure.string :as str]))

(def series-path "data/fast/series.tsv")

(defn exposure-fn
  "(fn [country t]) -> {:food :energy :fertiliser :open}: food imports, fuel
  imports less fuel exports (shares of merchandise trade), agriculture's
  share of output and trade over output, as fractions, read two years
  before the origin; nil where unknown (bnm.world-gauges/exposure-fn, which
  the world state's exposure reads too)."
  [db]
  (wg/exposure-fn db))

(def group-of {:commodities :fast/commodities :fx :fast/fx :ports :fast/ports :noise :fast/noise :planted :fast/planted})

(def noise-width "The noise group's width: FSa's nine." 9)

(defn shift-months
  "The time-shift null draw `seed`'s lag: 24, 36, ..., 120 months, whole
  years so a seasonal series keeps its season."
  [seed]
  (* 12 (+ 2 (mod (dec (long seed)) 9))))

(def short-history
  "Families whose series start too late for a time-shift null draw (the v2
  rule's amendment A1: derangements only). Port calls and chokepoint
  transits begin in January 2019, so read 24-60 months back at the scored
  origins they are unknown, the group drops out and the draw is the
  bench; 11 of the 33 countries' sovereign spreads start after 1995, so
  a shifted :fx draw loses them in the early test years."
  #{:fx :ports})

(defn null-allowed?
  "Whether `null` (nil, \"derange\" or \"shift\") is a null draw of `family`."
  [family null]
  (not (and (= "shift" null) (short-history family))))

(defn with-set
  "`sp` with the EDN map `m` merged in, a nil value removing its key."
  [sp m]
  (reduce-kv (fn [sp k v] (if (nil? v) (dissoc sp k) (assoc sp k v))) sp m))

(declare run-main)

(defn -main [& args]
  (let [opts (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))]
    (if-let [root (:archive opts)]
      (first (lg/recording #(run-main opts) (lg/redirect-to root lg/inputs)))
      (run-main opts))))

(defn- run-main [opts]
  (let [{:keys [out family placebo end kinds smoke seed null strength] set-edn :set} opts
        family (keyword (or family "none"))
        _ (when-not (or (#{:none :noise :planted} family) (fast/families family))
            (throw (ex-info (str "bnm.fast-run: no family " family) {})))
        _ (when-not (contains? #{nil "derange" "shift"} null)
            (throw (ex-info (str "bnm.fast-run: no null " null " (derange or shift)") {})))
        _ (when (and (= :planted family) null)
            (throw (ex-info "bnm.fast-run: a planted signal has no null draw" {})))
        _ (when-not (null-allowed? family null)
            (throw (ex-info (str "bnm.fast-run: no time-shift null for " (name family) ": its series start too late (derange)") {})))
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        cats (kinds/catalogues)
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        countries (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats)))
        seed (or (some-> seed parse-long) 1)
        source (cond (= "true" placebo) (wr/placebo-source countries)
                     (= "derange" null) (fast/derange-source countries seed)
                     :else identity)
        inputs (case family
                 :none nil
                 :noise (fast/noise-inputs noise-width seed)
                 :planted :per-kind
                 (fast/family-inputs family (fast/read-series series-path)
                                     (exposure-fn (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv")))))
        inputs (if (and inputs (= "shift" null)) (fast/shift-inputs inputs (shift-months seed)) inputs)
        width (if (#{:noise :planted} family) noise-width (count (fast/families family)))
        _ (println "building" (count ks) "kinds, family" (name family) (if (= "true" placebo) "(placebo)" ""))
        specs (into {} (for [[k sp] (wr/build-specs ks end cats)]
                         [k (cond-> sp
                              inputs (fast/with-fast (if (= :per-kind inputs)
                                                       (fast/planted-inputs (:target sp) (or (some-> strength parse-double) 1.0) noise-width seed)
                                                       inputs)
                                                     width (group-of family) source)
                              set-edn (with-set (read-string set-edn))
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
