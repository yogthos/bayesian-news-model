(ns bnm.lattice-political
  "The political kinds on the lattice (trial 40): conflict and war onsets,
  coups, protest and violence surges, with the design trial 39 chose on
  1980-2002 for banking crises carried over unchanged: the full lattice at
  the year and half-decade, the quarters on their own gauges with named's
  forecast as their offset.

  - quarter: an onset in quarters 2-4 (lead) and 5-8 (w2), from the
    political inputs of bnm.named (food and energy prices through
    exposure, growth, inflation, youth unemployment, climate, the event
    streams) and its political economy;
  - year: an onset within two years, from bnm.political-gauges' year row
    (regime, repression, exclusion, the military, growth, demography,
    inequality), the kind's own history, the other kinds' recent onsets
    in the place, and the world's share of states with a recent onset;
  - half-decade: an onset within five years, from the structural levels
    and the histories.

  The catalogue joins the long catalogues (Correlates of War intra-state
  wars from 1816, Archigos removals by the military from 1875) before the
  modern one (UCDP from 1946, Cline coups from 1945, the GDELT surges from
  1980). Tensions and turns are the ones the anatomy of onsets before 2003
  found building and released at the onset (docs/ANATOMY-POLITICAL.md).

    jolt -A:tools -M -m bnm.lattice-political --kind coup --out lp-coup [--without-at ...] [--end m]

  Writes data/runs/<out>/ as bnm.lattice-banking does."
  (:require [bnm.geo :as geo]
            [bnm.lattice :as lt]
            [bnm.lattice-banking :as lb]
            [bnm.macrohistory :as mh]
            [bnm.named :as nm]
            [bnm.onsets :as on]
            [bnm.political-gauges :as pg]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def kinds ["conflict_onset" "war_onset" "coup" "protest_surge" "violence_surge"])

(defn scales-for
  "The banking lattice's scales; without the half-decade for the kinds whose
  catalogue starts in 1980, too few origins for it to learn from."
  [kind]
  (if (>= (pg/long-from kind) 1980) (vec (remove #(= :half-decade (:name %)) lb/scales)) lb/scales))

(def quarter-groups
  "bnm.named's political inputs and its political economy, in groups."
  {:food [0 1 2 3] :fragility [4 9 10] :energy [5 6] :economy [7 8] :climate [11 12]
   :events [13 14 15 16 17] :political-economy [18 19 20 21 22 23]})

(def year-names
  (into pg/year-names [:since-last :own10 :own30 :others-1 :others-2 :others-3 :others-4 :world3]))

(def year-groups
  {:regime [0 1 2 12 13] :violence [3 4 5 6] :society [7 10 11] :military [8 18 19] :patronage [9]
   :economy [14 15 23 24] :demography [16 17 20 21 22] :inequality [25 26]
   :history [27 28 29] :others [30 31 32 33] :world [34]})

(def half-names [:polyarchy :anocracy :log-gdppc :physical-integrity :social-exclusion :military-executive
                 :since-last :own10 :own30 :world3])

(def half-groups {:structure [0 1 2 3 4 5] :memory [6 7 8 9]})

(def ^:private yi (zipmap year-names (range)))

(def tensions
  "Per kind, the year's tensions [a b] (a ahead of b strains) whose strain,
  accumulated without reset, the anatomy of onsets before 2003 found
  building or standing above the calm before onsets; at the quarter,
  mobilization ahead of coercion and prices ahead of growth."
  (let [t (fn [& pairs] (mapv (fn [[a b]] [(yi a) (yi b)]) pairs))
        q [[14 17] [2 7] [8 7]]]
    (into {} (for [[k pairs] {"conflict_onset" (t [:population-growth3 :growth3] [:urban-growth5 :growth3] [:inflation :growth3]
                                                  [:social-exclusion :polyarchy] [:polyarchy :rule-of-law])
                              "war_onset" (t [:social-exclusion :polyarchy] [:urban-growth5 :growth3] [:population-growth3 :growth3]
                                             [:inflation :growth3])
                              "coup" (t [:urban-growth5 :growth3] [:polyarchy :rule-of-law] [:population-growth3 :growth3]
                                        [:top10 :bottom50] [:social-exclusion :polyarchy] [:inflation :growth3])
                              "protest_surge" (t [:polyarchy :rule-of-law] [:tertiary5 :growth3]
                                                 [:soldiers-per-capita :spending-per-soldier3] [:participation :assembly])
                              "violence_surge" (t [:polyarchy :rule-of-law] [:inflation :growth3] [:population-growth3 :growth3]
                                                  [:urban-growth5 :growth3] [:tertiary5 :growth3])}]
               [k {:year pairs :quarter-lead q :quarter-w2 q}]))))

(def turns
  "Per kind, [stock flow window]: at the year, for coups, participation
  stretched while the electoral democracy index falls from its three-year
  peak (the anatomy's one turn standing above the calm); at the quarter,
  fragility and mobilization stretched while growth falls from its
  eight-quarter peak."
  (let [q [[4 7 8] [14 7 8]]]
    (into {} (for [k kinds]
               [k {:year (if (= k "coup") [[(yi :participation) (yi :polyarchy) 3]] [])
                   :quarter-lead q :quarter-w2 q}]))))

(def priors
  "What is known and the anatomy agrees with, {group {index [mean var]}}:
  past onsets (the conflict trap, Collier et al. 2003; the coup trap,
  Londregan & Poole 1990); poverty (Fearon & Laitin 2003) and infant
  mortality (Goldstone et al. 2010); exclusion (Cederman, Wimmer & Min
  2010); the military in the executive, for coups."
  (let [base {:history {1 [0.3 0.1]}}]
    {"conflict_onset" {:year (merge base {:economy {0 [-0.2 0.1]} :demography {2 [0.2 0.1]} :society {1 [0.2 0.1]}})}
     "war_onset" {:year (merge base {:economy {0 [-0.2 0.1]} :demography {2 [0.2 0.1]} :society {1 [0.2 0.1]}})}
     "coup" {:year (merge base {:economy {0 [-0.2 0.1]} :military {0 [0.3 0.1]}})}
     "protest_surge" {:year base}
     "violence_surge" {:year base}}))

(defn- count-known
  "Onsets of `cat` at `unit` known by t within the last `months`, or nil
  before its catalogue covers the unit."
  [cat unit t months]
  (let [from (get-in cat [:from unit])]
    (when (and from (<= (+ from months) t))
      (let [k (- t (:known-lag cat))]
        (double (count (subseq (get-in cat [:onsets unit] (sorted-set)) > (- k months) <= k)))))))

(defn spec
  [{:keys [kind db cats annual wdi units end known]}]
  (let [cat (cats kind)
        scales (scales-for kind)
        others (remove #{kind} kinds)
        start (* 12 (pg/long-from kind))
        [_ pol] (nm/political db)
        months (range (+ start 2) (inc end) 3)
        [_ pe] (nm/political-economy db units months)
        sub (into {} (for [u units :let [s (first (geo/parents u))] :when s] [u s]))
        reg (into {} (for [s (set (vals sub))] [s (first (geo/parents s))]))
        parent (merge sub reg (zipmap (set (vals reg)) (repeat "world")))
        countries (sort (keys sub))
        members (fn [place] (if (sub place) [place] (filter #(let [s (sub %)] (or (= s place) (= (reg s) place) (= "world" place))) countries)))
        origins (fn [{:keys [per]}] (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end))))
        in-system (fn [u t] (when-let [f (get-in cat [:from u])] (<= f t)))
        ;; the world's share of states in the system with an onset known in the last three years
        world3 (memoize (fn [t] (let [us (filter #(in-system % t) countries)
                                      n (count (filter #(some-> (count-known cat % t 36) pos?) us))]
                                  (when (seq us) (/ (double n) (count us))))))
        hist (fn [u t] (or (#'lb/history cat u t) [nil nil nil]))
        row (memoize (fn [u y] (pg/year-row annual wdi u y)))
        year-raw (fn [u t]
                   (when (in-system u t)
                     (vec (concat (row u (dec (quot t 12)))
                                  (hist u t)
                                  (map #(count-known (cats %) u t 120) others)
                                  [(world3 t)]))))
        half-raw (fn [u t]
                   (when (in-system u t)
                     (let [r (row u (dec (quot t 12)))
                           ix (zipmap pg/year-names (range))]
                       (vec (concat (map #(nth r (ix %)) (take 6 half-names)) (hist u t) [(world3 t)])))))
        raw {:quarter-lead (fn [u t] (when (in-system u t) (into (vec (pol u t)) (pe u t))))
             :quarter-w2 (fn [u t] (when (in-system u t) (into (vec (pol u t)) (pe u t))))
             :year year-raw
             :half-decade half-raw}
        widths (select-keys {:quarter-lead 24 :quarter-w2 24 :year (count year-names) :half-decade (count half-names)}
                            (map :name scales))
        table (into {} (for [{:keys [name] :as s} scales]
                         [name (into {} (for [t (origins s) u countries :let [v ((raw name) u t)] :when v] [[u t] v]))]))
        std (into {} (for [[name tb] table] [name (#'lb/standardizer (for [[[_ t] v] tb] [t v]))]))
        known-at (fn [scale place t]
                   (let [w ({:quarter-lead "lead" :quarter-w2 "w2"} scale)
                         ps (when w (keep #(get known [w % (quot (- t 2) 3)]) (members place)))]
                     (when (seq ps)
                       (let [p (- 1.0 (reduce * (map #(- 1.0 %) ps)))]
                         (Math/log (/ (max 1e-6 p) (max 1e-6 (- 1.0 p))))))))
        gauges (fn [scale place t]
                 (let [f (std scale)
                       vs (keep #(some->> (get-in table [scale [% t]]) (f t)) (members place))]
                   (if (seq vs)
                     (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs)
                     (vec (repeat (widths scale) 0.0)))))
        known-lag (:known-lag cat)]
    {:scales scales
     :levels [[:country countries]
              [:subregion (sort (set (vals sub)))]
              [:region (sort (set (vals reg)))]
              [:world ["world"]]]
     :offset known-at
     :parent parent
     :start start :end end
     :forget 0.995
     :target (fn [scale place t]
               (let [{:keys [offset horizon]} (first (filter #(= scale (:name %)) scales))
                     lo (+ t offset) hi (+ t offset horizon)
                     ys (keep #(#'lb/onset-in? cat % t lo hi) (members place))]
                 (when (seq ys) {:y (boolean (some true? ys)) :known-at (+ hi known-lag)})))
     :gauges gauges
     :gauge-width widths
     :gauge-groups {:quarter-lead quarter-groups :quarter-w2 quarter-groups :year year-groups :half-decade half-groups}
     :shrink 0.1
     :contradiction-prior [0.3 0.25]
     :tensions (tensions kind)
     :turns (turns kind)
     ;; no resolution: unlike a banking crisis, after which credit falls
     ;; back against wages, a political onset leaves its tensions as they
     ;; were or wider (docs/ANATOMY-POLITICAL.md); strain only decays
     :resolutions (fn [_] nil)
     :exposure (let [regimes (mh/read-regimes)
                     ex (memoize (fn [u t] (#'lb/exposure-of db regimes u t)))]
                 (fn [_ place t]
                   (let [vs (map #(ex % t) (members place))]
                     (if (seq vs) (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs) [0.0 0.0]))))
     :exposure-width 2
     :coupling 0.1
     :prior (fn [scale _level {:keys [groups]}]
              (into {} (for [[g ixs] (get-in priors [kind scale]) [i mv] ixs] [(nth (groups g) i) mv])))}))

(defn catalogues
  "{kind catalogue} for every political kind."
  []
  (let [sets (on/read-event-sets "data/slow/events.tsv")
        cov (into {} (on/read-coverage "data/slow/coverage.tsv"))
        long (pg/read-long)
        states (pg/read-states)]
    (into {} (for [k kinds]
               [k (pg/catalogue {:kind k :sets sets :coverage-q (cov k) :long (get long k {}) :states states
                                 :long-from (pg/long-from k) :modern (pg/modern k)})]))))

(defn build
  "The spec for `kind` to month `end`, from the data on disk."
  [kind end known-run]
  (let [variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        cats (catalogues)
        units (set (filter #(first (geo/parents %)) (keys (get-in cats [kind :onsets]))))]
    (spec {:kind kind :db db :cats cats :units units :end end
           :annual (merge (pg/read-annual) (lb/read-annual))
           :wdi (fn [iso v y] (some-> (db [iso v]) (sk/value (+ 11 (* 12 y)) {:stale 18})))
           :known (lb/read-known known-run kind)})))

(defn -main [& args]
  (let [{:keys [kind out end without-at known downscale pool uncertainty]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        out (or out (str "lattice-" kind))
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        _ (println "building" kind)
        sp (cond-> (build kind end (or known "r6-at"))
             without-at (assoc :without-at (into {} (for [part (str/split without-at #",")
                                                         :let [[sc gs] (str/split part #":")]]
                                                     [(keyword sc) (set (map keyword (str/split gs #"\+")))])))
             ;; the quarters' offset from the year's forecast (docs/DOWNSCALE.md):
             ;; --downscale year [--pool 0.5] [--uncertainty true]
             downscale (assoc :downscale {:quarter-lead {:from (keyword downscale)} :quarter-w2 {:from (keyword downscale)}})
             pool (assoc :downscale-pool (parse-double pool))
             (= "true" uncertainty) (assoc :downscale-uncertainty true))
        _ (println "running the lattice")
        r (lt/run sp)]
    (io/make-parents (str dir "/x"))
    (with-open [w (io/writer (str dir "/forecasts.tsv"))]
      (doseq [{:keys [scale place origin p y]} (:forecasts r)]
        (.write w (str (name scale) "\t" place "\t" origin "\t" p "\t" (if (nil? y) "" y) "\n"))))
    (with-open [w (io/writer (str dir "/preds.tsv"))]
      (doseq [{:keys [scale place origin p y]} (:forecasts r)
              :let [wname ({:quarter-lead "lead" :quarter-w2 "w2"} scale)]
              :when (and wname (= 3 (count place)))]
        (.write w (str kind "\t" wname "\tlattice\t" (quot (- origin 2) 3) "\t" place "\t" p "\t" (if (nil? y) "nil" y) "\n"))))
    (with-open [w (io/writer (str dir "/weights.tsv"))]
      (doseq [[[scale level] hist] (sort-by key (:weights r))
              [t ws] hist
              :when (zero? (mod (- t 11) 12))
              [g wt] (sort-by key ws)]
        (.write w (str (name scale) "\t" (name level) "\t" (quot t 12) "\t" (name g) "\t" wt "\n"))))
    (println "wrote" dir)))
