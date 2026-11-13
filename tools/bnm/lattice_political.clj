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
            [bnm.releases :as rel]
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
  catalogue starts in 1930 or later: fewer than fifteen five-year origins
  before the test years, too few for it to learn from (currency crises'
  half-decade nodes, from 1962, overfit in the smoke run).
  `first-year`, the catalogue's first covered year, for kinds beyond the
  political ones."
  ([kind] (scales-for kind (pg/long-from kind)))
  ([_ first-year]
   (if (>= first-year 1930) (vec (remove #(= :half-decade (:name %)) lb/scales)) lb/scales)))

(defn first-year
  "A catalogue's first covered year, over its places."
  [cat]
  (quot (apply min (vals (:from cat))) 12))

(def quarter-groups
  "bnm.named's political inputs and its political economy, in groups."
  {:food [0 1 2 3] :fragility [4 9 10] :energy [5 6] :economy [7 8] :climate [11 12]
   :events [13 14 15 16 17] :political-economy [18 19 20 21 22 23]})

(def financial-kinds
  "Kinds whose quarter nodes read bnm.named's financial inputs (credit,
  asset prices, the curve, global credit and the VIX), as banking crises'
  do, rather than the political ones."
  #{"currency_crisis" "sovereign_default" "market_crash" "fiscal_consolidation"})

(def financial-quarter-groups
  {:credit [0 1 2 3] :assets [4 5 6 7 8 9 10] :curve [11] :global [12 13 14 15 16 17]})

(def year-names
  (into pg/year-names [:since-last :own10 :own30 :others-1 :others-2 :others-3 :others-4 :world3]))

(def year-groups
  {:regime [0 1 2 12 13] :violence [3 4 5 6] :society [7 10 11] :military [8 18 19] :patronage [9]
   :economy [14 15 23 24] :demography [16 17 20 21 22] :inequality [25 26]
   :fiscal [27 28] :history [29 30 31] :others [32 33 34 35] :world [36]})

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
                                                  [:urban-growth5 :growth3] [:tertiary5 :growth3])
                              ;; the economic kinds and market crashes: chosen by rule from their
                              ;; anatomy (bnm.anatomy-political economic: strain at -1 >= +0.15 and
                              ;; above its level at -10; data/runs/anatomy/anatomy-economic.txt)
                              "currency_crisis" (t [:urban-growth5 :growth3] [:inflation :growth3] [:participation :assembly]
                                                   [:population-growth3 :growth3] [:top10 :bottom50] [:polyarchy :rule-of-law])
                              "fiscal_consolidation" (t [:inflation :growth3] [:participation :assembly] [:top10 :bottom50]
                                                        [:polyarchy :rule-of-law])
                              "growth_collapse" (t [:participation :assembly])
                              "inflation_crisis" (t [:urban-growth5 :growth3] [:inflation :growth3] [:participation :assembly]
                                                    [:soldiers-per-capita :spending-per-soldier3] [:population-growth3 :growth3]
                                                    [:top10 :bottom50] [:polyarchy :rule-of-law])
                              ;; market crashes: their strain runs against the crashes on the design
                              ;; years (r -0.12 in the smoke run): a protective signal, kept with a
                              ;; negative prior (contradiction-sign)
                              "market_crash" (t [:working-age :growth3] [:polyarchy :rule-of-law])
                              "sovereign_default" (t [:inflation :growth3] [:population-growth3 :growth3] [:top10 :bottom50]
                                                     [:polyarchy :rule-of-law])
                              "food_price_shock" []}]
               ;; the financial kinds' quarters: banking's (credit growth against the
               ;; curve, the country's credit cycle against the world's)
               (let [q (if (financial-kinds k) [[0 11] [0 12]] q)]
                 [k {:year pairs :quarter-lead q :quarter-w2 q}])))))

(def turns
  "Per kind, [stock flow window]: at the year, for coups, participation
  stretched while the electoral democracy index falls from its three-year
  peak (the anatomy's one turn standing above the calm); at the quarter,
  fragility and mobilization stretched while growth falls from its
  eight-quarter peak."
  (let [q [[4 7 8] [14 7 8]]]
    (into {} (for [k (concat kinds ["currency_crisis" "inflation_crisis" "growth_collapse" "sovereign_default"
                                     "fiscal_consolidation" "market_crash" "food_price_shock"])]
               ;; the financial kinds' quarters: banking's turns, the credit gap
               ;; stretched while house prices, the household boom or equity fall
               (let [q (if (financial-kinds k) [[2 4 8] [2 5 8] [2 7 8]] q)]
                 [k {:year (if (= k "coup") [[(yi :participation) (yi :polyarchy) 3]] [])
                     :quarter-lead q :quarter-w2 q}])))))

(def contradiction-sign
  "The sign of the prior on each kind's contradiction summaries: more
  strain, more risk (+1), unless the design years say strain goes with
  fewer onsets (-1), a protective signal rather than none."
  {"market_crash" -1})

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
  before its catalogue covers the unit, or once t less the publication lag
  is past the catalogue's :through when it says :unknown-past-through
  (loop audit Test 7: the uncovered tail is unknown, not quiet; off in the
  bases that predate it)."
  [cat unit t months]
  (let [from (get-in cat [:from unit])
        through (get-in cat [:through unit])
        k (- t (:known-lag cat))]
    (when (and from (<= (+ from months) t) (or (not (:unknown-past-through cat)) (nil? through) (<= k through)))
      (double (count (subseq (get-in cat [:onsets unit] (sorted-set)) > (- k months) <= k))))))

(defn world-share
  "The share of `countries` in the system at t (`in-system`) with an onset
  of `cat` known in the last three years, over those whose catalogue
  covers that span (count-known); nil when none does. Without the
  catalogue's :unknown-past-through, every place in the system counts in
  the denominator, as the bases before it read."
  [cat countries in-system t]
  (if (:unknown-past-through cat)
    (let [cs (keep #(when (in-system % t) (count-known cat % t 36)) countries)]
      (when (seq cs) (/ (double (count (filter pos? cs))) (count cs))))
    (let [us (filter #(in-system % t) countries)
          n (count (filter #(some-> (count-known cat % t 36) pos?) us))]
      (when (seq us) (/ (double n) (count us))))))

(defn political-economy-of
  "bnm.named/political-economy for the lattice's quarters: with
  :pwt-stale (bnm-h8d) the Penn World Table read at its publication lag
  (bnm.named/publication-stale, 36 months), else the default 30 that the
  bases to t50 read, under which the group went missing at every
  September origin."
  [db units months {:keys [pwt-stale lag-stale]}]
  (nm/political-economy db units months (cond-> {}
                                          pwt-stale (update :stale merge nm/publication-stale)
                                          ;; bnm-x6o: the table between its editions
                                          lag-stale (update :stale merge (select-keys nm/lag-stale ["economy/profit_rate" "economy/labor_share"])))))

(defn wdi-reader
  "(wdi iso var year): the WDI value known at the end of `year`, at most 18
  months old, the base's reading; with :lag-stale (bnm-x6o) each series at
  its own publication lag (bnm.named/lag-stale), so tertiary enrolment,
  21 months late, is read at all."
  [db {:keys [lag-stale]}]
  (fn [iso v y] (some-> (db [iso v]) (sk/value (+ 11 (* 12 y)) {:stale (if lag-stale (get nm/lag-stale v 18) 18)}))))

(defn ended
  "The last year ended by month t (a year-end origin's own year): data
  read as of its end are known at t."
  [t]
  (quot (- t 11) 12))

(defn year-row-year
  "The year whose annual row the year and half-decade nodes read at origin
  t: the year before the last one ended (an assumed one-year lag), `delay`
  years earlier still for bnm-pjg.6's delay test."
  [t delay]
  (- (ended t) 1 (long (or delay 0))))

(def ^:private release-table (delay (rel/read-releases)))

(defn year-row-opts
  "bnm.political-gauges/year-row's options at origin t: with `leak-fixes`
  (bnm-56c.23) the release table and the origin's year, so Maddison and NMC
  are read no later than published, and urban growth in one unit across
  2016; off, none (the base's reading)."
  [leak-fixes t _year-delay]
  (if leak-fixes {:releases @release-table :origin-year (ended t) :urban-log true} {}))

(def leading-sets
  "--leading (bnm-56c.36, docs/LEADING.md \"Priors\"): per kind, the leading
  gauges (bnm.named/leading) each scale reads as one more gauge group,
  :leading. Market crashes' quarters already read the US curve; food's
  quarters both food gauges (their :food group), so neither is read twice."
  {"market_crash" {:quarter-lead [:baa12 :vix12] :year [:baa12 :us-curve :vix12]}
   "growth_collapse" {:quarter-lead [:baa12 :us-curve :vix12] :year [:baa12 :us-curve :vix12]}
   "food_price_shock" {:year [:world-food12 :food12-x-imports]}})

(def leading-priors
  "--leading-priors: each leading gauge's coefficient prior [mean variance]
  on its standardized value, fixed from theory before any run (docs/
  LEADING.md \"Priors\", where the sources are), wherever a node reads it."
  {"market_crash" {:baa12 [0.5 0.0625] :us-curve [-0.4 0.0625] :vix12 [0.25 0.0625]}
   "growth_collapse" {:baa12 [0.4 0.0625] :us-curve [-0.3 0.0625] :vix12 [0.3 0.0625]}
   "food_price_shock" {:food12-x-imports [0.5 0.0625] :world-food12 [0.3 0.0625]}})

(def world-leading
  "The leading gauges that are one value for the world, read the same at
  every country (bnm-v1y): they can move the level, not rank. Food12 x
  imports varies by country and is not among them."
  #{:baa12 :us-curve :vix12 :world-food12})

(def food2-sets
  "--leading-food2 (bnm-5rn, docs/LEADING.md \"The second food set\"): the
  fertilizer index's 12-month change and the FAO food, cereals and oils
  indices' 6-month changes, at the food year node beside t65's two gauges
  and at the food quarter-lead as a group of their own."
  {"food_price_shock" {:year [:fert12 :fpi6 :cereals6 :oils6] :quarter-lead [:fert12 :fpi6 :cereals6 :oils6]}})

(def food2-priors
  "Their coefficient priors, from the pass-through literature and the
  gauges' own sds, fixed before any run (docs/LEADING.md)."
  {:fert12 [0.1 0.0625] :fpi6 [0.15 0.0625] :cereals6 [0.15 0.0625] :oils6 [0.1 0.0625]})

(defn history-standardizer
  "bnm-11y, :lead-std :history: (fn [t v]) standardizing the leading
  columns `v` (lead-f's entries at positions `ix`) against the gauges' own
  values at every quarter end from month `from` (the node's start) to
  `end` over `units`, each as known then, through the quarter ends before
  t; capped at 5, 0 for a missing value. A column is read once it has 20
  quarter ends of history: the node's standardizer counts rows, so a
  world gauge, one value per origin repeated at every country, was
  standardized against two or three origins."
  [lead-f units ix from end]
  (let [w (count ix)
        ts (range (+ from (mod (- 2 from) 3)) (inc end) 3)
        stats (loop [[t & more] ts n (double-array w) mu (double-array w) ss (double-array w) q (long-array w) acc (sorted-map)]
                (if-not t
                  acc
                  (let [acc (assoc acc t [(aclone n) (aclone mu) (aclone ss) (aclone q)])
                        seen (boolean-array w)]
                    (doseq [u units :let [l (lead-f u t)]]
                      (dotimes [j w]
                        (when-let [x (nth l (nth ix j))]
                          (aset seen j true)
                          (aset n j (inc (aget n j)))
                          (let [m0 (aget mu j) m1 (+ m0 (/ (- x m0) (aget n j)))]
                            (aset mu j m1) (aset ss j (+ (aget ss j) (* (- x m0) (- x m1))))))))
                    (dotimes [j w] (when (aget seen j) (aset q j (inc (aget q j)))))
                    (recur more n mu ss q acc))))]
    (fn [t v]
      (let [[^doubles n ^doubles mu ^doubles ss ^longs q] (or (some-> (rsubseq stats <= t) first val)
                                                              [(double-array w) (double-array w) (double-array w) (long-array w)])]
        (vec (for [j (range w) :let [x (nth v j)]]
               (if (and x (>= (aget q j) 20))
                 (let [sd (Math/sqrt (/ (aget ss j) (aget n j)))]
                   (if (> sd (Math/max 1e-9 (* 1e-8 (Math/abs (aget mu j)))))
                     (Math/max -5.0 (Math/min 5.0 (/ (- x (aget mu j)) sd)))
                     0.0))
                 0.0)))))))

(defn leading-sets-for
  "`leading-sets`, with the second food set when `food2`."
  [food2]
  (if food2 (merge-with #(merge-with into %1 %2) leading-sets food2-sets) leading-sets))

(defn named-prior-at
  "The kind's leading priors among the gauge `names` a scale reads, or nil;
  with `food2`, the second food set's at food price shocks too."
  ([kind names] (named-prior-at kind names false))
  ([kind names food2]
   (not-empty (select-keys (cond-> (leading-priors kind)
                             (and food2 (contains? food2-sets kind)) (merge food2-priors))
                           names))))

(def quarter-now
  "The frailty-only quarter scale (bnm.lattice :frailty-only, recursive step
  4, bnm-56c.35): window (t, t+3], known 3 months after it plus the
  catalogue's lag. It issues nothing; its nodes filter the common shock a
  coarser node's :frailty :prior :finer reads."
  {:name :quarter-now :per 3 :offset 0 :horizon 3 :discount 0.995 :frailty-only true})

(def quarter-next
  "The issuing next-quarter scale (bnm.lattice :leaf, recursive step 4b,
  bnm-56c.38): window (t, t+3], issued every quarter end, known 3 months
  after it plus its catalogue's lag. A leaf: it issues as the quarter-lead
  does and no other node reads it. It replaces quarter-now."
  {:name :quarter-next :per 3 :offset 0 :horizon 3 :discount 0.995 :leaf true})

(defn scales-of
  "The kind's scales, finest first: `scales-for`'s, with :fresh-frailty the
  frailty-only quarter-now before them, with :next-quarter the issuing
  quarter-next; the two are refused together (the second replaces the
  first)."
  [kind first-year {:keys [fresh-frailty next-quarter]}]
  (when (and fresh-frailty next-quarter)
    (throw (ex-info "bnm.lattice-political: the next-quarter scale replaces the frailty-only quarter-now" {:kind kind})))
  (cond->> (scales-for kind first-year)
    fresh-frailty (into [quarter-now])
    next-quarter (into [quarter-next])))

(def ^:private known-window
  "The named model's window each quarter scale's offset reads, and the
  share of it the scale's window is (a constant hazard carries it there)."
  {:quarter-lead ["lead" 1.0] :quarter-w2 ["w2" 1.0] :quarter-next ["w1" 0.25]})

(defn known-log-odds
  "What is known for `scale` at origin t over the places `members`: the log
  odds that any has an onset, from bnm.onsets' named model (`known`, {[window
  unit q] p}); the next quarter reads w1, (t, t+12], carried to (t, t+3]
  under a constant hazard. nil at other scales or with none known."
  [known scale members t]
  (when-let [[w share] (known-window scale)]
    (let [ps (keep #(get known [w % (quot (- t 2) 3)]) members)
          ps (if (= 1.0 share) ps (map #(- 1.0 (Math/pow (- 1.0 (double %)) share)) ps))]
      (when (seq ps)
        (let [p (- 1.0 (reduce * (map #(- 1.0 %) ps)))]
          (Math/log (/ (max 1e-6 p) (max 1e-6 (- 1.0 p)))))))))

(defn target-fn
  "The lattice's :target over `scales`: an onset of `cat` at any of the
  place's `members` in the scale's window, known at its end plus the
  catalogue's lag; the next quarter reads its own catalogue `nq-cat`
  (recursive step 4b: food by month, growth by quarter)."
  [scales cat nq-cat members]
  (fn [scale place t]
    (let [{:keys [offset horizon]} (first (filter #(= scale (:name %)) scales))
          c (if (= :quarter-next scale) (or nq-cat cat) cat)
          lo (+ t offset) hi (+ t offset horizon)
          ys (keep #(#'lb/onset-in? c % t lo hi) (members place))]
      (when (seq ys) {:y (boolean (some true? ys)) :known-at (+ hi (:known-lag c))}))))

(defn spec
  [{:keys [kind db cats annual wdi units end known start pwt-stale lag-stale gauge-std year-delay leak-fixes fresh-frailty
           leading leading-priors leading-food2 lead-std next-quarter nq-cat]}]
  (let [cat (cats kind)
        ;; :fresh-frailty: the frailty-only quarter scale first, so it runs
        ;; before every coarser node at a month they share; :next-quarter the
        ;; issuing next quarter (step 4b), a leaf
        scales (scales-of kind (or (pg/long-from kind) (first-year cat)) {:fresh-frailty fresh-frailty :next-quarter next-quarter})
        issuing (vec (remove :frailty-only scales))
        ;; four other political kinds' recent onsets (the :others group)
        others (take 4 (remove #{kind} kinds))
        start (or start (* 12 (pg/long-from kind)))
        [_ pol] (nm/political db)
        months (range (+ start 2) (inc end) 3)
        [_ pe] (political-economy-of db units months {:pwt-stale pwt-stale :lag-stale lag-stale})
        sub (into {} (for [u units :let [s (first (geo/parents u))] :when s] [u s]))
        reg (into {} (for [s (set (vals sub))] [s (first (geo/parents s))]))
        parent (merge sub reg (zipmap (set (vals reg)) (repeat "world")))
        countries (sort (keys sub))
        members (fn [place] (if (sub place) [place] (filter #(let [s (sub %)] (or (= s place) (= (reg s) place) (= "world" place))) countries)))
        origins (fn [{:keys [per]}] (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end))))
        in-system (fn [u t] (when-let [f (get-in cat [:from u])] (<= f t)))
        ;; the world's share of states in the system with an onset known in the last three years
        world3 (memoize (fn [t] (world-share cat countries in-system t)))
        hist (fn [u t] (or (#'lb/history cat u t) [nil nil nil]))
        row (memoize (fn [u t] (pg/year-row annual wdi u (year-row-year t year-delay) (year-row-opts leak-fixes t year-delay))))
        year-raw (fn [u t]
                   (when (in-system u t)
                     (vec (concat (row u t)
                                  (hist u t)
                                  (map #(count-known (cats %) u t 120) others)
                                  [(world3 t)]))))
        half-raw (fn [u t]
                   (when (in-system u t)
                     (let [r (row u t)
                           ix (zipmap pg/year-names (range))]
                       (vec (concat (map #(nth r (ix %)) (take 6 half-names)) (hist u t) [(world3 t)])))))
        fin (when (financial-kinds kind) (second (nm/financial db (nm/world-credit db units months))))
        q-raw (if fin
                (fn [u t] (when (in-system u t) (vec (fin u t))))
                (fn [u t] (when (in-system u t) (into (vec (pol u t)) (pe u t)))))
        ;; --leading (bnm-56c.36): the kind's leading gauges after a scale's own
        lead (when leading (get (leading-sets-for leading-food2) kind))
        ;; the next quarter reads the quarter-lead's leading gauges
        lead (if (and lead next-quarter (:quarter-lead lead)) (assoc lead :quarter-next (:quarter-lead lead)) lead)
        lead-f (when lead (second (nm/leading db)))
        lead-ix (zipmap nm/leading-names (range))
        with-lead (fn [scale f]
                    (if-let [ns (seq (get lead scale))]
                      (fn [u t] (when-let [v (f u t)]
                                  (let [l (lead-f u t)] (into v (map #(nth l (lead-ix %)) ns)))))
                      f))
        raw {:quarter-next (with-lead :quarter-next q-raw)
             :quarter-lead (with-lead :quarter-lead q-raw)
             :quarter-w2 q-raw
             :year (with-lead :year year-raw)
             :half-decade half-raw}
        qw (if fin 18 24)
        own-names {:quarter-lead (if fin (first (nm/financial nil nil)) (into (first (nm/political nil)) nm/political-economy-names))
                   :year year-names}
        own-names (assoc own-names :quarter-w2 (:quarter-lead own-names) :quarter-next (:quarter-lead own-names))
        widths (select-keys (merge-with + {:quarter-next qw :quarter-lead qw :quarter-w2 qw :year (count year-names) :half-decade (count half-names)}
                                        (update-vals lead count))
                            (map :name issuing))
        table (into {} (for [{:keys [name] :as s} issuing]
                         [name (into {} (for [t (origins s) u countries :let [v ((raw name) u t)] :when v] [[u t] v]))]))
        std (into {} (for [[name tb] table] [name (#'lb/standardizer (for [[[_ t] v] tb] [t v]) gauge-std)]))
        ;; bnm-11y, :lead-std :history: the leading columns against their own
        ;; history since 1990, the scale's own columns as before
        std (if (and lead (= :history lead-std))
              (into {} (for [[name f] std]
                         [name (if-let [ns (seq (get lead name))]
                                 (let [n0 (count (own-names name))
                                       own (#'lb/standardizer (for [[[_ t] v] (table name)] [t (subvec v 0 n0)]) gauge-std)
                                       h (history-standardizer lead-f countries (mapv lead-ix ns) start end)]
                                   (fn [t v] (into (own t (subvec v 0 n0)) (h t (subvec v n0)))))
                                 f)]))
              std)
        known-at (fn [scale place t] (known-log-odds known scale (members place) t))
        gauges (fn [scale place t]
                 (let [f (std scale)
                       vs (keep #(some->> (get-in table [scale [% t]]) (f t)) (members place))]
                   (if (seq vs)
                     (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs)
                     (vec (repeat (widths scale) 0.0)))))]
    (cond->
    {:scales scales
     :levels [[:country countries]
              [:subregion (sort (set (vals sub)))]
              [:region (sort (set (vals reg)))]
              [:world ["world"]]]
     :offset known-at
     :parent parent
     :start start :end end
     :forget 0.995
     :target (target-fn scales cat nq-cat members)
     :gauges gauges
     :gauge-width widths
     :gauge-groups (let [qg (if fin financial-quarter-groups quarter-groups)
                         ;; the leading gauges, one group after the scale's own
                         add (fn [scale gs] (if-let [ns (seq (get lead scale))]
                                              (let [n0 (count (own-names scale))]
                                                (assoc gs :leading (vec (range n0 (+ n0 (count ns))))))
                                              gs))]
                     (cond-> {:quarter-lead (add :quarter-lead qg) :quarter-w2 qg :year (add :year year-groups) :half-decade half-groups}
                       next-quarter (assoc :quarter-next (add :quarter-next qg))))
     :shrink 0.1
     :contradiction-prior [(* 0.3 (contradiction-sign kind 1)) 0.25]
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
              (into {} (for [[g ixs] (get-in priors [kind scale]) [i mv] ixs] [(nth (groups g) i) mv])))}
      ;; --leading: every gauge named, so --leading-priors can set the
      ;; leading gauges' priors by name (bnm.lattice :named-prior)
      lead (assoc :gauge-names (into {} (for [s [:quarter-next :quarter-lead :quarter-w2 :year] :when (widths s)]
                                          [s (into (vec (own-names s)) (get lead s))])))
      (and lead leading-priors)
      (assoc :named-prior (into {} (for [s [:quarter-next :quarter-lead :year] :when (widths s)
                                         :let [np (named-prior-at kind (into (vec (own-names s)) (get lead s)) leading-food2)]
                                         :when np]
                                     [s np]))))))

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
  "The spec for `kind` to month `end`, from the data on disk; `cats` (every
  kind's catalogue, bnm.kinds) and `start` (a month) for the world run,
  where any kind with a catalogue may be built this way."
  ([kind end known-run] (build kind end known-run nil nil))
  ([kind end known-run cats start] (build kind end known-run cats start {}))
  ([kind end known-run cats start {:keys [pwt-stale lag-stale gauge-std year-delay leak-fixes fresh-frailty leading leading-priors
                                         real-lags leading-food2 lead-std next-quarter nq-cats]}]
  (let [variables (st/read-variables "data/slow/variables.tsv")
        db (cond-> (nm/load-series "data/slow/series.tsv" variables {:real-lags real-lags})
             leading-food2 (merge (nm/load-food2)))
        cats (or cats (catalogues))
        units (set (filter #(first (geo/parents %)) (keys (get-in cats [kind :onsets]))))]
    (spec {:kind kind :db db :cats cats :units units :end end :start start :pwt-stale pwt-stale :lag-stale lag-stale :gauge-std gauge-std
           :year-delay year-delay :leak-fixes leak-fixes
           :fresh-frailty fresh-frailty
           :next-quarter next-quarter :nq-cat (get nq-cats kind)
           :leading leading :leading-priors leading-priors :leading-food2 leading-food2 :lead-std lead-std
           :annual (merge (pg/read-annual) (lb/read-annual) (pg/read-annual "data/derived/fiscal.csv"))
           :wdi (wdi-reader db {:lag-stale lag-stale})
           :known (lb/read-known known-run kind)}))))

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
