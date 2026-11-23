(ns bnm.world-run
  "The world model (docs/WORLD-MODEL.md, trials 42 and 43): every kind's
  lattice (bnm.kinds) stepped together, month by month from 1870, each
  reading its parents along the graph's edges, all from before t; then the
  learned world run forward as an artificial system (bnm.world).

  Each kind is built as on its own (bnm.lattice-banking, bnm.lattice-
  political, which builds the economic kinds and market crashes too) with
  the trial 41 design (the quarters on their own gauges, their offset the
  year's forecast pooled with named's, its uncertainty carried down) and,
  at the year and half-decade, one input per parent in its edge's form
  (trial 43's graph, data/runs/sequencing/graph-t43.edn, bnm.relation-
  diag), each nested in the node's own model (with-relations).

    jolt -A:tools -M -m bnm.world-run --out world --without-relations true [--end m] [--kinds a,b] [--smoke true]
                                      [--known r6-at-8ul]
    jolt -A:tools -M -m bnm.world-run --out world --graph path [--placebo true] [--ews path] [--states path]
                                      [--end m] [--kinds a,b] [--smoke true]

  Recursive step 2's options (bead bnm-56c.19), each off by default:
  --world-gauges true (the world state at the year and half-decade,
  bnm.world-gauges), --world-once true (with it: the world gauges once per
  origin as the countries' offset, only food and Brent, fix 3; each gauge
  only at the origins where the kind's lead test passes it, bnm-56c.28,
  --world-gate false for none), --world-extra noise|planted|false [--seed s]
  [--strength s] (the acceptance harness's extra world column; without
  --world-gauges, a group of its own),
  --pwt-stale true (bnm-h8d), --lag-stale true (bnm-x6o: each slow
  series read at its own publication lag, bnm.named/lag-stale),
  --gauge-std stable (bnm-9fg: the gauges' standardizer reads a flat
  history as no spread, not the cap),
  --catalogue-through true (Test 7),
  --year-delay n (bnm-pjg.6: the political and economic kinds' year rows
  read n years later; banking's own table is not delayed), --leak-fixes
  true (bnm-56c.23: Maddison, NMC and banking's PWT read by their
  releases, resources/bnm/releases.tsv; urban growth in one unit),
  --food-outcomes monthly (bnm-56c.30: food price shocks by month from
  FAOSTAT's monthly food inflation, known at its publication lag, nine
  months, not the annual catalogues' eighteen),
  --growth-outcomes quarterly (bnm-56c.37: growth collapses by quarter from
  quarterly GDP per head, data/derived/gdp-quarterly.csv, against the
  annual rule's line, known five months after the quarter; only the 103
  units the quarterly data cover are forecast for the kind, docs/
  GROWTH-QUARTERLY.md; annual-covered, the annual catalogue at those
  units, its comparison),
  --clim-prior kinds (bnm-56c.26: a node with no resolved outcome reads
  the rate the other kinds' nodes at its scale and level have resolved,
  not 0.5),
  --fresh-frailty quarter-now (bnm-56c.35, recursive step 4: every kind
  whose catalogue dates onsets within the year gets bnm.lattice-political/
  quarter-now, a frailty-only scale, window three months, known three
  months plus the lag after it; with --set's :frailty {:prior :finer
  :source :quarter-now} its coarser nodes take their prior from it, the
  other kinds their own), and
  --leading true (bnm-56c.36: the leading financial and food gauges,
  bnm.lattice-political/leading-sets, read at their availability date),
  --leading-priors true (with it: their coefficient priors from theory,
  bnm.lattice-political/leading-priors, docs/LEADING.md \"Priors\"),
  --leading-food2 true (bnm-5rn: with --leading, the second food set,
  bnm.lattice-political/food2-sets), --real-lags true (bnm-a1e: every slow
  series at its true publication, bnm.named/real-lags), --lead-std history
  (bnm-11y: the leading gauges standardized against their own history),
  --at-risk catalogue (bnm-96j: a row is at risk only where its kind's
  catalogue rule could date an onset in the window, bnm.kinds/at-risk-rules:
  not within a once-in-n rule's years, not off an annual catalogue's
  Januaries, not after a year of inflation's run),
  --frailty-gauges true (bnm-v1y: the year node's world gauges set its
  frailty's mean instead of entering its models; needs --leading-priors
  true and --set's :frailty), --dated-credit true (bnm-9vm option A:
  banking's credit-family inputs from the BIS from the December 2003
  origin, bnm.lattice-banking/dated-credit-inputs), --banking-coverage
  true (bnm-7bh: nine declared signals at banking's year node,
  bnm.lattice-banking/coverage-decls), --banking-gauges
  true|flat (bnm-cpv: the world's leading gauges at banking's year node,
  at theory's priors or the node's default ones), --wide-credit true
  (with it: the same and house prices for the countries outside the
  Macrohistory panel, bnm.lattice-banking/wide-credit-values),
  --next-quarter true (bnm-56c.38, recursive step 4b: the issuing
  next-quarter scale, window three months from the origin, a leaf no other
  node reads, at every kind whose catalogue for it is sub-annual; food by
  month, growth by quarter, the rest on the run's catalogues; with
  --frailty-gauges its world gauges set its frailty's mean; with
  --horizon-priors true its own leading priors, docs/LEADING.md \"Priors at
  the next quarter\", chosen in-sample),
  --write-reference true (also data/runs/<out>-ref, the forecasts with the
  reference state's p wherever a state is kept apart), --set '{...}' merged into every spec (:state-from :reference,
  :evidence-at :issue, :downscale-learn {:var 1.0}, :learn-offsets
  :mixture, :flow-std-from :resolved).

  --known names the bnm.onsets run whose named model the quarters pool
  with (r6-at when none is named; t50-world-norel reads r6-at-8ul, the
  base of record, t52-world-norel, r6-at-fx).

  A run names its model: --without-relations true is the world without
  relations, the base of record (t48-world-norel); --graph path turns on
  that graph's links (trial 43's is data/runs/sequencing/graph-t43.edn,
  which a run with neither flag used to read). A run with neither is
  refused, as is the base with a link option.

  With --placebo, each country reads its parents at the next country in
  sorted order: the links' placebo. With --ews, each kind's early warning
  (data/runs/sequencing/ews-t44.edn, bnm.ews-diag) is one more nested
  group.

  Writes data/runs/<out>/<kind>/{forecasts,preds,weights}.tsv as the
  lattice tools do, principal.tsv (the principal contradiction of every
  country's year node at every year end), relations.tsv (each relation
  group's weight by kind, scale, level and year), and, from the final
  state, world-baseline.tsv (each kind's chance of an onset in three years
  by country) and world-impulse.tsv (how much an onset of each kind raises
  each other's, averaged over countries), and world-joint.tsv (at every
  year end from 2003, for each edge and country, the simulated chances of
  each onset and of both within three years, from what the world had
  learned by then)."
  (:require [bnm.blocs :as blocs]
            [bnm.kind-theory :as kt]
            [bnm.cli :as cli]
            [bnm.economic-events :as ee]
            [bnm.ews :as ews]
            [bnm.states :as states]
            [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.lattice-banking :as lb]
            [bnm.aftermath :as af]
            [bnm.year-gauges :as yg]
            [bnm.signals :as sg]
            [bnm.lattice-political :as lp]
            [bnm.lattice-smoke :as smoke]
            [bnm.regime-filter :as rf]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [bnm.relations :as rl]
            [bnm.world :as w]
            [bnm.world-gauges :as wg]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def start (* 12 1870))

(def quarter-design
  "Trial 41's chosen design for the quarters."
  {:without-at {:quarter-lead #{:up :down :contradiction :turn :exposure :feedback :relations}
                :quarter-w2 #{:up :down :contradiction :turn :exposure :feedback :relations}}
   :downscale {:quarter-lead {:from :year} :quarter-w2 {:from :year}}
   :downscale-pool 0.5
   :downscale-uncertainty true})

(defn read-graph
  ([] (read-graph "data/runs/sequencing/graph.edn"))
  ([path] (read-string (slurp path))))

(defn parents-of
  "{kind [parent kinds]} from the graph, among `ks`."
  [graph ks]
  (let [in (set ks)]
    (into {} (for [k ks] [k (vec (sort (for [[[j k'] _] graph :when (and (= k k') (in j))] j)))]))))

(defn with-relations
  "`sp` with its :relations: one input per parent, in its edge's form
  (bnm.relations/edge-input), from `books` (an atom of {kind book}, the
  state before t); each link nested in the node's own model, so the
  averaging sets the node with it against the node without it. A link's
  coefficient starts at its edge's sign (mean 0.2 for an exciting edge,
  -0.2 for an inhibiting one, variance 0.1, in the input's spreads).
  Above the countries a place reads the mean of its countries' inputs. An
  edge whose form ends in -own passes the raw form's own part at the
  country (bnm.factors: less what the world's and the region's countries
  share at t), its factor model stepped once per origin and scale, and
  above the countries the countries' mean common part. A kind without
  a parent is left as it is.
  `source` maps the place to the one whose parents it reads (identity, or
  the placebo's other country)."
  ([sp parents books cats scales-of edges source] (with-relations sp parents books cats scales-of edges source {}))
  ([sp parents books cats scales-of edges source {:keys [warning states]}]
  (let [state-group (fn [{:keys [name]}] (keyword "rel" (str/replace name "/" "-")))
        groups (cond-> (into {} (for [[i j] (map-indexed vector parents)] [(keyword "rel" j) [i]]))
                 warning (assoc :rel/warning [(count parents)])
                 (seq states) (into (for [[i st] (map-indexed vector states)]
                                      [(state-group st) [(+ (count parents) (if warning 1 0) i)]])))
        prior (:prior sp)
        ;; above the countries, a place reads the mean of its countries' inputs
        par (:parent sp)
        leaves (remove (set (vals par)) (keys par))
        below (reduce (fn [m c] (reduce #(update %1 %2 (fnil conj []) c) m (take-while some? (iterate par c))))
                      {} leaves)
        members (fn [place] (get below place [place]))
        ;; an own form (:post3-own, ...): the raw form's own part at each
        ;; country (bnm.factors), a factor model per parent, form and scale
        ;; stepped once per origin over the countries whose raw value is
        ;; known then; the reads behind the whole cross-section recorded.
        ;; Above the countries, a place reads its countries' mean of the
        ;; raw form: what they share is what the region carries
        own? (fn [form] (str/ends-with? (name form) "-own"))
        base-of (fn [form] (keyword (subs (name form) 0 (- (count (name form)) 4))))
        owns (atom {})
        scale-for (fn [j scale] (if (contains? (scales-of j) scale) scale :year))
        raw-at (fn [bs j form scale c t] (rl/raw-input form {:kind j :book (bs j) :catalogue (cats j)} (scale-for j scale) c t))
        leaf? (set leaves)
        own-at (fn [bs j form scale t]
                 (let [k [j form (scale-for j scale)]]
                   (or (get-in @owns [k :at t])
                       (let [raws (into {} (for [c leaves :let [r (raw-at bs j (base-of form) scale c t)] :when (some? (:x r))] [c r]))
                             [parts st'] (fac/observe (or (get-in @owns [k :st]) (fac/online par {:discount 0.99}))
                                                      t (update-vals raws :x))
                             latest (reduce max Long/MIN_VALUE (keep :origin (mapcat :reads (vals raws))))
                             at {:own (update-vals parts :own)
                                 :common (update-vals parts :common)
                                 :reads (update-vals raws :reads)
                                 ;; one read standing for the cross-section the factors used
                                 :summary (when (> latest Long/MIN_VALUE)
                                            [{:dir :relation :kind j :scale (scale-for j scale) :place :cross-section :origin latest}])}]
                         (swap! owns assoc k {:st st' :at {t at}})
                         at))))
        ;; an own edge at a country: its own part; above the countries (the
        ;; members already through the placebo's source): their mean common
        ;; part, what they share, never their levels
        own-input (fn [bs j scale place t srcs]
                    (let [{:keys [own common reads summary]} (own-at bs j (:form (edges j)) scale t)]
                      (if (leaf? place)
                        {:x (get own (first srcs)) :reads (into (vec (get reads (first srcs))) summary)}
                        (let [vs (keep common srcs)]
                          {:x (when (seq vs) (/ (reduce + vs) (count vs))) :reads (vec summary)}))))
        input (fn [bs j scale place t]
                (rl/edge-input (:form (edges j)) {:kind j :book (bs j) :catalogue (cats j)} (scale-for j scale) place t))]
    (if (and (empty? parents) (nil? warning) (empty? states))
      sp
    (assoc sp
           :relations (fn [scale place t]
                        (let [bs @books
                              ;; a parent without this scale (no half-decade for the kinds from
                              ;; 1980) is read at its year node
                              ms (members place)
                              ins (for [j parents]
                                    (let [rs (if (own? (:form (edges j)))
                                               [(own-input bs j scale place t (map source ms))]
                                               (map #(input bs j scale (source %) t) ms))]
                                      ;; the members' mean over those known; unknown (nil) without any
                                      {:x (let [vs (keep :x rs)] (when (seq vs) (/ (reduce + vs) (double (count vs)))))
                                       :reads (mapcat :reads rs)}))]
                          {:x (cond-> (mapv :x ins)
                                ;; the kind's early warning: its countries' mean above them
                                warning (conj (let [vs (keep #((:at warning) (source %) t) ms)]
                                                (when (seq vs) (/ (reduce + vs) (double (count vs))))))
                                ;; the kind's stock-flow states, each its countries' mean above them
                                (seq states) (into (for [{:keys [at]} states]
                                                     (let [vs (keep #(at (source %) t) ms)]
                                                       (when (seq vs) (/ (reduce + vs) (double (count vs))))))))
                           :reads (vec (mapcat :reads ins))}))
           :relation-width (into {} (for [{n :name} (:scales sp)] [n (count groups)]))
           :relation-groups (into {} (for [{n :name} (:scales sp)] [n groups]))
           :relation-nest true
           :principal :auto
           :prior (fn [scale level lay]
                    (merge (when prior (prior scale level lay))
                           (into {} (for [j parents :let [ix (get-in lay [:parts (keyword "rel" j)])] :when (seq ix)]
                                      [(first ix) [(* 0.2 (:sign (edges j) 1)) 0.1]]))
                           (when-let [ix (seq (get-in lay [:parts :rel/warning]))]
                             {(first ix) [(* 0.2 (:sign warning)) 0.1]})
                           (into {} (for [st states :let [ix (get-in lay [:parts (state-group st)])] :when (seq ix)]
                                      [(first ix) [(* 0.2 (:sign st)) 0.1]])))))))))

(defn placebo-source
  "The placebo's map from each country to the next in sorted order (the
  last to the first), other places to themselves: a parent read from
  another country carries what is common to the period, not what the
  parent did at the place."
  [countries]
  (let [cs (vec (sort countries))
        m (zipmap cs (conj (subvec cs 1) (first cs)))]
    (fn [place] (get m place place))))

(defn warnings
  "{kind {:sign :at (fn [country t])}} of each kind's early warning
  ({:window w :warnings {kind [series indicator sign]}}, bnm.ews-diag):
  the indicator of the country's own part of the series over the window
  ending at the origin's year, as known then."
  [{:keys [window] ews :warnings :as spec} db countries]
  (when-not (and window ews)
    (throw (ex-info "bnm.world-run/warnings: {:window :warnings} expected (rerun bnm.ews-diag)" {:keys (keys spec)})))
  (let [tree (fac/place-tree countries)
        cs (filter tree countries)
        sigs (into {} (pmap (fn [v] [v (ews/own-indicators (ews/series-panel db cs v) tree window)])
                            (distinct (map first (vals ews)))))]
    (into {} (for [[k [v ind sign]] ews]
               [k {:sign sign :at (fn [c t] (get-in sigs [v c (lp/ended t) ind]))}]))))

(defn states-of
  "{kind [{:name :sign :at (fn [country t])}]} of each kind's chosen
  stock-flow states ({:paths [..] :states {kind [[name sign] ...]}},
  bnm.state-diag), each read as the diagnostic read it (bnm.states/
  readers: a world series' causal z, any other its own part at the
  country, exposures included) as known at the last year ended by the
  origin. :coupled (a coupled engine's directory, bnm.engine) reads the
  stock tables it reproduces from its one table instead."
  [{:keys [paths coupled] chosen :states} countries]
  (let [tree (fac/place-tree countries)
        st (states/read-states paths {:coupled coupled})
        read (states/readers st tree (states/default-pairs st))]
    (into {} (for [[k ss] chosen]
               [k (vec (for [[n sign] ss :let [f (read n)] :when f]
                         {:name n :sign sign :at (fn [c t] (f c (lp/ended t)))}))]))))

(defn known-run
  "The bnm.onsets run whose named model the quarters' offset pools with
  (--known; r6-at, as t48, t49 and the first ledger issue read, when none
  is named). t50-world-norel reads r6-at-8ul, refit on the catalogues
  rebuilt after the gazetteer fix with lapsed sources kept (bead bnm-8ul)."
  [{:keys [known]}]
  (or known "r6-at"))

(defn design-for
  "Trial 41's quarter design for spec `sp`, and with the next-quarter scale
  the same for it: every flow left out, the offset the year's forecast
  carried down, pooled with what is known."
  [sp]
  (if (some #(= :quarter-next (:name %)) (:scales sp))
    (-> quarter-design
        (assoc-in [:without-at :quarter-next] (get-in quarter-design [:without-at :quarter-lead]))
        (assoc-in [:downscale :quarter-next] {:from :year}))
    quarter-design))

(defn build-specs
  "{kind spec} for `ks` to month `end`, the quarters' offset pooled with
  run `known`'s named model. opts (recursive step 2, each off by default):
  :pwt-stale (bnm-h8d), :lag-stale (bnm-x6o), :gauge-std (bnm-9fg), :catalogue-through (Test 7; `cats` should carry
  it too, `catalogues-through`), :leak-fixes (bnm-56c.23), :fresh-frailty
  (a set of kinds given the frailty-only quarter scale, bnm-56c.35)."
  ([ks end cats] (build-specs ks end cats "r6-at"))
  ([ks end cats known] (build-specs ks end cats known {}))
  ([ks end cats known opts]
   (into {} (pmap (fn [k]
                    (let [opts (assoc opts :fresh-frailty (contains? (set (:fresh-frailty opts)) k)
                                      :next-quarter (contains? (set (:next-quarter opts)) k))]
                      (when (and (= k lb/kind) (:fresh-frailty opts))
                        (throw (ex-info "bnm.world-run: banking crises take no fresh frailty scale" {})))
                      (when (and (= k lb/kind) (:next-quarter opts))
                        (throw (ex-info "bnm.world-run: banking crises take no next-quarter scale" {})))
                      (let [sp (if (= k lb/kind) (lb/build end known opts) (lp/build k end known cats start opts))]
                        [k (merge sp (design-for sp))])))
                  ks))))

(defn sub-annual?
  "Whether a catalogue dates its onsets within the year: no month of the
  year holds more than 45% of them (an annual catalogue puts every onset in
  its year's first month; banking's, JST's years with a few modern
  quarters, holds 90% there, conflict onsets' COW years before UCDP 47%).
  Only such a kind's quarter windows resolve fresher than its year's."
  [{:keys [onsets]}]
  (let [ms (map #(mod (long %) 12) (mapcat val onsets))]
    (and (seq ms) (<= (apply max (vals (frequencies ms))) (* 0.45 (count ms))))))

(defn fresh-kinds
  "--fresh-frailty quarter-now (bnm-56c.35): the kinds among `ks` whose
  catalogue (`cats`, after --food-outcomes) is sub-annual."
  [choice ks cats]
  (case choice
    nil #{}
    "quarter-now" (set (filter #(and (not= % lb/kind) (sub-annual? (cats %))) ks))
    (throw (ex-info (str "bnm.world-run: --fresh-frailty takes quarter-now, not " choice) {}))))

(defn finer-where-fresh
  "The specs with a :frailty :prior :finer turned back to :own at a kind
  that has no frailty-only scale (its catalogue is annual: the quarter
  windows resolve no fresher than the year's), said once per kind."
  [specs]
  (into {} (for [[k sp] specs]
             [k (if (and (= :finer (get-in sp [:frailty :prior])) (not-any? :frailty-only (:scales sp)))
                  (do (println "  " k "has no fresh frailty scale: its frailty prior is its own")
                      (update sp :frailty #(-> % (assoc :prior :own) (dissoc :source :cross))))
                  sp)])))

(defn cross-kind-prior
  "bnm-56c.26: (fn [scale level]) -> [onsets rows], the outcomes the other
  kinds' nodes at that scale and level have resolved, read from `clims`
  ({kind {[scale level] [onsets rows]}}, the runner's as of the month), for
  bnm.lattice's :clim-prior: a node with nothing of its own resolved
  issues the rate of onsets of the tracked kinds over its window and level,
  not an even 0.5."
  [clims kind]
  (fn [scale level]
    (reduce (fn [[a b] [k c]]
              (if (= k kind) [a b]
                  (let [[x y] (get c [scale level] [0 0])] [(+ a x) (+ b y)])))
            [0 0] @clims)))

(defn food-outcomes
  "`cats` under --food-outcomes: monthly, food price shocks by month at the
  food CPI's publication lag (bnm.economic-events/with-monthly-food,
  bnm-56c.30); nil, the base's catalogue."
  [cats db choice]
  (case choice
    nil cats
    "monthly" (ee/with-monthly-food cats db)
    (throw (ex-info (str "bnm.world-run: --food-outcomes takes monthly, not " choice) {}))))

(defn at-risk
  "`cats` under --at-risk: catalogue, each kind's rows at risk only where
  its catalogue's rule could date an onset (bnm.kinds/with-catalogue-at-risk,
  bnm-96j; `food` is --food-outcomes, `growth` --growth-outcomes); nil, the
  base's busy months alone."
  ([cats db food choice] (at-risk cats db food choice nil))
  ([cats db food choice growth]
   (case choice
     nil cats
     "catalogue" (kinds/with-catalogue-at-risk cats db food growth)
     (throw (ex-info (str "bnm.world-run: --at-risk takes catalogue, not " choice) {})))))

(defn growth-outcomes
  "`cats` under --growth-outcomes: quarterly, growth collapses by quarter
  (bnm.economic-events/quarterly-growth, bnm-56c.37), dated at the quarter's
  last month and known five months on, at the units the quarterly data
  cover only (the kind's lattice is built over its catalogue's units, so
  the other units drop out of it); annual-covered, the annual catalogue at
  those units, the base the quarterly one is compared with on the same
  units; nil, the base's catalogue. `read` gives the quarterly catalogue
  from `cats`."
  [cats choice read]
  (case choice
    nil cats
    "quarterly" (ee/with-quarterly-growth cats (read cats))
    "annual-covered" (let [us (keys (:from (read cats)))]
                       (update cats "growth_collapse"
                               (fn [c] (reduce (fn [c' k] (update c' k select-keys us)) c [:onsets :busy :from :through]))))
    (throw (ex-info (str "bnm.world-run: --growth-outcomes takes quarterly, not " choice) {}))))

(defn catalogues-through
  "Every catalogue with its uncovered tail unknown (loop audit Test 7)."
  [cats]
  (update-vals cats #(assoc % :unknown-past-through true)))

(defn once-opts
  "bnm.world-gauges/with-world-once's options: the harness's column, and no
  gate under --world-gate false (822fea8's term, every gauge in it)."
  [{:keys [world-gate]} extra]
  (cond-> {:extra extra} (= "false" world-gate) (assoc :gate nil)))

(defn with-step2
  "The specs with recursive step 2's options (`opts`, from the command
  line): :world-gauges, the world state at the year and half-decade
  (bnm.world-gauges), :world-once with it, the world gauges entered once per
  origin (fix 3, bnm.world-gauges/with-world-once), with :world-extra (noise, planted or false: the
  acceptance harness's extra world column, :seed, :strength; without
  :world-gauges the column alone, as the year's :world-gauges group);
  :set, an EDN map merged into every spec (a nil value removes its key);
  :kind-set, an EDN map {kind map} merged after it into that kind's spec;
  :real-lags, the series read at their true publication (bnm-a1e)."
  [specs {:keys [world-gauges world-once world-extra seed strength real-lags] set-edn :set kind-set-edn :kind-set :as opts} end]
  (let [state (when world-gauges
                (let [db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv") {:real-lags real-lags})
                      units (set (filter #(= 3 (count %)) (map first (keys db))))]
                  (wg/load-state db units end {:real-lags real-lags})))
        exposure (when world-gauges (wg/exposure-from))
        merge-map (fn [sp m] (reduce-kv (fn [sp k v] (if (nil? v) (dissoc sp k) (assoc sp k v))) sp m))
        kind-sets (some-> kind-set-edn read-string)
        ;; --kind-set: a kind's own map after --set's
        merge-set (fn [k sp] (cond-> sp
                               set-edn (merge-map (read-string set-edn))
                               (get kind-sets k) (merge-map (get kind-sets k))))]
    (into {} (for [[k sp] specs]
               [k (merge-set k
                   (if (or world-gauges world-extra)
                     (let [countries (filter #(= 3 (count %)) (keys (:parent sp)))
                           year-origins (filter #(zero? (mod (inc (- % (:start sp))) 12)) (range (:start sp) (inc end)))
                           extra (wg/extra-column {:kind (some-> world-extra keyword) :seed (or (some-> seed parse-long) 1)
                                                   :strength (some-> strength parse-double)
                                                   :share (wg/onset-share sp countries) :year-origins year-origins})]
                       (cond
                         (and world-gauges world-once) (wg/with-world-once sp state exposure (once-opts opts extra))
                         world-gauges (wg/with-world sp state exposure {:extra extra})
                         :else
                         ;; without the world state (bnm-56c.23's t51b): the
                         ;; harness's column as a group of its own
                         (let [scales (set (filter #{:year :half-decade} (map :name (:scales sp))))]
                           (assoc sp
                                  :world-gauges (fn [_scale _place t] {:x [(extra t)] :reads []})
                                  :world-width (into {} (for [s scales] [s 1]))))))
                     sp))]))))

(defn reference-forecasts
  "`r`'s forecasts with each p that has a state apart from it (under
  bnm.lattice's :state-from) replaced by the state's p, the reference's
  forecast: the information without the weighting (the step-2 diagnosis's
  Q4)."
  [r]
  (update r :forecasts
          (fn [fs] (mapv (fn [{:keys [scale place origin] :as f}]
                           (let [e (get-in r [:book [scale place] origin])]
                             (if (:issued e) (assoc f :p (:p e)) f)))
                         fs))))

(defn- write-kind [dir kind r]
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
      (.write w (str (name scale) "\t" (name level) "\t" (quot t 12) "\t" (if (namespace g) (str (namespace g) "/" (name g)) (name g)) "\t" wt "\n"))))
  ;; under :regime-clim (step 5c): each year place's pi and the climatology's
  ;; log odds as issued at each origin
  (when-let [rows (seq (for [[[s place] byo] (sort-by key (:book r)) :when (= s :year)
                             [o e] byo :when (:rclim e)
                             :let [[pi lvl] (:rclim e)]]
                         (str place "\t" (quot o 12) "\t" (if (nil? pi) "" pi) "\t" lvl "\n")))]
    (with-open [w (io/writer (str dir "/rclim.tsv"))]
      (doseq [row rows] (.write w ^String row))))
  ;; under bnm.lattice's :frailty: each node's common shock, filtered per origin
  (when-let [fr (:frailty r)]
    (with-open [w (io/writer (str dir "/frailty.tsv"))]
      (doseq [[[scale level] hist] (sort-by key fr)
              [s {:keys [m v rho sd lsd usd rsd cross] :as e}] hist]
        ;; under :level-sds, the level's sd, state and variance as three more
        ;; columns; under :exposure-sds, the slope's sd and each column's
        ;; loading (comma-separated, empty while no slope is chosen); under
        ;; :regional-sds, the regional sd and each group's state
        (.write w (str (name scale) "\t" (name level) "\t" s "\t" m "\t" v "\t" rho "\t" sd
                       (when lsd (str "\t" lsd "\t" (:level e) "\t" (:level-v e)))
                       (when usd (str "\t" usd "\t" (str/join "," (:u e)) "\t" (str/join "," (:u-v e))))
                       ;; under :regional-sds, the regional sd and each group=state
                       (when rsd (str "\t" rsd "\t" (str/join "," (map #(str %1 "=" %2) (:r-groups e) (or (:rs e) (repeat 0.0))))
                                      ;; with their own persistence, the regional rho
                                      (when-let [q (:rrho e)] (str "\t" q))))
                       ;; under :prior :finer, the chosen kappa (0: the node's own prior)
                       (when cross (str "\t" cross))
                       ;; under :gauges (bnm-v1y), beta, its variance and the origin's gauges
                       (when-let [b (:beta e)] (str "\t" (str/join "," b) "\t" (str/join "," (:beta-v e)) "\t" (str/join "," (:g e))))
                       ;; under --regime (bnm-56c.40): whether delta is chosen (it is
                       ;; beta's last) and the origin's regime loading x
                       (when (contains? e :regime) (str "\tregime=" (:regime e) "\t" (:rx e) "\trgain=" (:rgain e)))
                       "\n"))))))

(defn world-of
  "The world for bnm.world from the final states: each kind's year-node
  forecasts by country (the issued ones, under bnm.lattice's :state-from) and, per parent, its edge's form, effect and lag."
  [specs states parents graph cats end]
  (into {} (for [[k sp] specs
                 :let [st (states k)
                       eff (if (:relations sp) (lt/relation-effects sp st :year 0) [])
                       book (lt/book st)]]
             [k {:f (into {} (for [[[s p] byo] book :when (and (= s :year) (= 3 (count p)))
                                   :let [[_ e] (first (rsubseq byo <= end))] :when e] [p (:f (:issued e e))]))
                 :q (into {} (for [[[s p] byo] book :when (and (= s :year) (= 3 (count p)))
                                   :let [[_ e] (first (rsubseq byo <= end))] :when e] [p (:q (:issued e e))]))
                 :window 24
                 :parents (into {} (for [[i j] (map-indexed vector (parents k)) :when (seq eff)]
                                     [j {:form (:form (graph [j k])) :coef (nth eff i) :lag (:known-lag (cats j) 12)}]))}])))

(def graph-t43
  "Trial 43's graph, what a run with no --graph read until 10 November 2026."
  "data/runs/sequencing/graph-t43.edn")

(defn model-choice
  "The model the options name, {:relations? :graph}: --without-relations
  true is the base of record, the world without relations (no graph is
  read); --graph path is the world with that graph's links. Neither is
  refused: a bare run used to turn on trial 43's links. The base with a
  link option (--graph, --placebo, --ews, --states) is refused too, since
  it would drop the option silently."
  [{:keys [without-relations graph placebo ews states]}]
  (let [norel (= "true" without-relations)
        links (seq (for [[k v] [["--graph" graph] ["--placebo" placebo] ["--ews" ews] ["--states" states]] :when v] k))]
    (cond (and norel links)
          (throw (ex-info (str "bnm.world-run: --without-relations true is the world without relations; it does not take "
                               (str/join ", " links))
                          {:options (vec links)}))
          norel {:relations? false :graph nil}
          graph {:relations? true :graph graph}
          :else (throw (ex-info (str "bnm.world-run: name the model: --without-relations true (the base of record, "
                                     "t48-world-norel) or --graph path (links; trial 43's is " graph-t43 ")")
                                {})))))

(defn- flag-choice
  "The true/false option `k` of `opts`: true when \"true\", false when
  \"false\" or absent, refused otherwise."
  [opts k]
  (let [v (get opts k)]
    (when (and v (not (#{"true" "false"} v)))
      (throw (ex-info (str "bnm.world-run: --" (name k) " takes true or false, not " v) {})))
    (= "true" v)))

(defn- enum-choice
  "The option `k` of `opts` looked up in `table` (string -> value), false
  when absent; a value outside it is refused, `takes` naming the ones
  allowed."
  [opts k table takes]
  (let [v (get opts k)]
    (when (and v (not (contains? table v)))
      (throw (ex-info (str "bnm.world-run: --" (name k) " takes " takes ", not " v) {})))
    (get table v false)))

(defn leading-choice
  "--leading true (bnm-56c.36): the leading gauges of bnm.lattice-political/
  leading-sets; --leading-priors true needs it."
  [{:keys [leading leading-priors] :as opts}]
  (when (and (= "true" leading-priors) (not= "true" leading))
    (throw (ex-info "bnm.world-run: --leading-priors true sets the priors of --leading true's gauges" {})))
  (flag-choice opts :leading))

(defn leading-food2-choice
  "--leading-food2 true (bnm-5rn): the second food set,
  bnm.lattice-political/food2-sets, with --leading-priors its priors.
  Needs --leading true; off by default."
  [{:keys [leading] :as opts}]
  (let [on (flag-choice opts :leading-food2)]
    (when (and on (not= "true" leading))
      (throw (ex-info "bnm.world-run: --leading-food2 true adds to --leading true's gauges" {})))
    on))

(defn lead-std-choice
  "--lead-std history (bnm-11y): the leading gauges standardized against
  their own history since 1990, not the node's few origins. Needs
  --leading true; off by default."
  [{:keys [leading lead-std]}]
  (case lead-std
    nil nil
    "history" (if (= "true" leading) :history
                  (throw (ex-info "bnm.world-run: --lead-std history standardizes --leading true's gauges" {})))
    (throw (ex-info (str "bnm.world-run: --lead-std takes history, not " lead-std) {}))))

(defn frailty-gauges-choice
  "--frailty-gauges true (bnm-v1y, recursive step 5a): the year node's
  world gauges set its frailty's mean, beta from their theory priors.
  Needs --leading true --leading-priors true; off by default."
  [{:keys [leading leading-priors] :as opts}]
  (let [on (flag-choice opts :frailty-gauges)]
    (when (and on (not (and (= "true" leading) (= "true" leading-priors))))
      (throw (ex-info "bnm.world-run: --frailty-gauges true moves --leading true's gauges, with --leading-priors true's priors" {})))
    on))

(defn with-frailty-gauges
  "Each spec whose year node reads world gauges (bnm.lattice-political/
  world-leading) with :frailty {:gauges {:year [those]}}, when `on?`. A
  spec with such gauges and no :frailty is refused."
  [specs on?]
  (if-not on?
    specs
    (into {} (for [[k sp] specs]
               [k (let [sp (if-let [ns (seq (filter lp/world-leading (get-in sp [:gauge-names :year])))]
                             (do (when-not (map? (:frailty sp))
                                   (throw (ex-info "bnm.world-run: --frailty-gauges true needs --set's :frailty" {:kind k})))
                                 (assoc-in sp [:frailty :gauges :year] (vec ns)))
                             sp)]
                    ;; bnm-56c.38: the next quarter's world gauges that carry a theory prior
                    (if-let [ns (seq (filter #(and (lp/world-leading %) (get-in sp [:named-prior :quarter-next %]))
                                             (get-in sp [:gauge-names :quarter-next])))]
                      (do (when-not (map? (:frailty sp))
                            (throw (ex-info "bnm.world-run: --frailty-gauges true needs --set's :frailty" {:kind k})))
                          (assoc-in sp [:frailty :gauges :quarter-next] (vec ns)))
                      sp))]))))

(defn banking-spans-choice
  "--banking-spans true (bnm-suj): banking crises busy from onset to end
  (data/derived/banking-spans.tsv, bnm.lattice-banking/with-spans), so the
  years inside a crisis are not rows at risk. Changes the target. Off by
  default."
  [opts]
  (flag-choice opts :banking-spans))

(defn real-lags-choice
  "--real-lags true (bnm-a1e): every slow series read at its true
  publication date (bnm.named/real-lags), the IMF commodity prices two
  months after their month, not series.tsv's one. Off by default."
  [opts]
  (flag-choice opts :real-lags))

(defn long-baseline-choice
  "--long-baseline N (docs/TESTBED.md \"The baseline absorbs the boom\"):
  banking's level stretches against N years (at least 10) in place of ten,
  so a slow boom is not taken into its own baseline. N at least 11; off
  by default."
  [{:keys [long-baseline]}]
  (when long-baseline
    (let [n (parse-long long-baseline)]
      (when-not (and n (> n 10))
        (throw (ex-info (str "bnm.world-run: --long-baseline takes a number of years above 10, not " long-baseline) {})))
      n)))

(defn banking-coverage-choice
  "--banking-coverage true (bnm-7bh step 1, docs/CALIBRATION.md \"Banking
  coverage from declared signals\"): nine signals declared through
  bnm.signals at banking's year node (bnm.lattice-banking/coverage-decls).
  Off by default; not with --banking-gauges (parked), one extra group at a
  time."
  [opts]
  (let [on (flag-choice opts :banking-coverage)]
    (when (and on (get #{"true" "flat"} (:banking-gauges opts)))
      (throw (ex-info "bnm.world-run: --banking-coverage and --banking-gauges add one group each; run one" {})))
    on))

(defn banking-gauges-choice
  "--banking-gauges true|flat (bnm-cpv, docs/LEADING.md \"Banking gauges\"):
  the world's leading gauges at banking's year node, at theory's priors
  (true) or the node's default ones (flat); nil, off, by default."
  [{:keys [banking-gauges]}]
  (case banking-gauges
    (nil "false") nil
    "true" :theory
    "flat" :flat
    (throw (ex-info (str "bnm.world-run: --banking-gauges takes true, flat or false, not " banking-gauges) {}))))

(defn lead-delay-choice
  "--lead-delay n (bnm-pjg.6): the leading gauges read n months late
  (bnm.lattice-political/delayed-lead), the leakage test of the adopted
  --leading. Needs --leading true; off by default."
  [{:keys [leading lead-delay]}]
  (when lead-delay
    (let [n (parse-long lead-delay)]
      (when-not (and n (not (neg? n)))
        (throw (ex-info (str "bnm.world-run: --lead-delay takes a count of months, not " lead-delay) {})))
      (when-not (= "true" leading)
        (throw (ex-info "bnm.world-run: --lead-delay delays --leading true's gauges" {})))
      n)))

(defn dated-credit-choice
  "--dated-credit true (bnm-9vm option A): the banking node's credit,
  mortgage and household credit inputs, and the displacement built from
  them, from the BIS's series from the December 2003 origin, each read once
  published (bnm.lattice-banking/dated-credit-inputs). Off by default."
  [opts]
  (flag-choice opts :dated-credit))

(defn theory-offset-choice
  "--theory-offset true (bnm-ris): banking's year node reads a theory
  forecast as its offset (bnm.lattice-banking/theory-inputs, bnm.theory).
  Off by default."
  [opts]
  (flag-choice opts :theory-offset))

(defn theory-contagion-choice
  "--theory-contagion true (bnm-ris), with --theory-offset: the theory also
  reads each country's exposure to bank-equity stress abroad (bnm.contagion:
  BIS cross-border claims x the fall in each counterparty's bank equity,
  over GDP). Off by default."
  [opts]
  (let [on (flag-choice opts :theory-contagion)]
    (when (and on (not (theory-offset-choice opts)))
      (throw (ex-info "bnm.world-run: --theory-contagion needs --theory-offset true" {})))
    on))

(defn theory-world-choice
  "--theory-world (bnm-5lr), with --theory-offset: funding, the theory also
  reads the panel's bank funding (bnm.lattice-banking/theory-world-inputs,
  t100); true, that and the world signals outside the panel
  (theory-world-signals: reserves). Off by default."
  [{v :theory-world}]
  (case v
    nil false
    "funding" :funding
    "true" :all
    (throw (ex-info (str "--theory-world: funding or true, not " v) {:value v}))))

(defn splice-credit-choice
  "--splice-credit bank (bnm-ris): under --dated-credit, JST's bank loans
  spliced with the BIS's bank credit (JST's concept) in place of its credit
  from all lenders. Off by default; needs --dated-credit true."
  [opts]
  (when-let [v (:splice-credit opts)]
    (when-not (= "bank" v)
      (throw (ex-info (str "bnm.world-run: --splice-credit takes bank, not " v) {})))
    (when-not (dated-credit-choice opts)
      (throw (ex-info "bnm.world-run: --splice-credit bank needs --dated-credit true" {})))
    :bank))

(defn recession-hold-choice
  "--recession-hold (bnm-5lr): true, with --theory-offset the theory reads
  credit from before a recession year (bnm.lattice-banking/held-origin,
  t103); rows, that and the year row's ratio columns held so for every
  group (bnm.lattice-banking/recession-held-columns); debt, the IMF debt
  group's ratios alone, the theory's row as read. Off by default."
  [{v :recession-hold}]
  (case v
    nil false
    "true" true
    "rows" :rows
    "debt" :debt
    (throw (ex-info (str "--recession-hold: true, rows or debt, not " v) {:value v}))))

(defn bloc-theory-choice
  "--bloc-theory kind=k,... (bnm-58f): each named kind's year node adds k x
  the bloc exposure (bnm.blocs) to its offset; nil without the flag."
  [{v :bloc-theory}]
  (when v
    (into {} (for [pair (str/split v #",")
                   :let [[kd k] (str/split pair #"=")
                         k (some-> k parse-double)]]
               (do (when-not k
                     (throw (ex-info (str "--bloc-theory takes kind=k pairs, not " pair) {:value v})))
                   (when-not (contains? kinds/tiers kd)
                     (throw (ex-info (str "--bloc-theory: no kind " kd) {:value v})))
                   [kd k])))))

(defn kind-theory-choice
  "--kind-theory kind,... (bnm-1dw): each kind's year node reads its
  declared theory (bnm.kind-theory) as its offset; nil without the flag.
  Banking has its own (--theory-offset)."
  [{v :kind-theory :as opts}]
  (when v
    (let [ks (for [w (str/split v #",")]
               (let [[k mode] (str/split w #":")]
                 (when (and mode (= lb/kind (kt/kind-of k)))
                   (throw (ex-info "bnm.world-run: --kind-theory: banking's variant replaces the offset; no :add" {:kind w})))
                 (when (and mode (not= "add" mode))
                   (throw (ex-info (str "bnm.world-run: --kind-theory: " w ": the only mode is :add") {:kind w})))
                 k))]
      (doseq [k ks]
        ;; banking's variants extend --theory-offset's theory (t120), replacing its offset
        (when (= (kt/kind-of k) lb/kind)
          (when (or (= k lb/kind) (not (theory-offset-choice opts)))
            (throw (ex-info "bnm.world-run: --kind-theory: banking crises take --theory-offset, and a variant extends it" {}))))
        (when-not (kt/decls k)
          (throw (ex-info (str "bnm.world-run: --kind-theory: no theory declared for " k) {:kind k}))))
      (vec ks))))

(defn impute-choice
  "--impute true|kind,... (bnm-s6m): the theory layers (--kind-theory,
  --global-theory) fill missing inputs (bnm.impute) and read the share
  missing: true for every layer's kinds, or the set of kinds named (t116:
  inflation crises only); false without the flag."
  [{v :impute :as opts}]
  (let [served (set (concat (some-> (:kind-theory opts) (str/split #",") (->> (map #(kt/kind-of (first (str/split % #":"))))))
                            (some-> (:global-theory opts) (str/split #","))))]
    (cond
      (nil? v) false
      (empty? served) (throw (ex-info "bnm.world-run: --impute fills a theory layer's inputs: give --kind-theory or --global-theory" {}))
      (= "true" v) true
      :else (let [ks (set (str/split v #","))]
              (doseq [k ks :when (not (served k))]
                (throw (ex-info (str "bnm.world-run: --impute: no theory layer serves " k) {:kind k})))
              ks))))

(defn shapes-choice
  "--shapes true|kind,... (bnm-asp): the kind theories read each input as
  bnm.kind-theory/shape's columns: true for all, or the set of kinds named;
  false without the flag."
  [{v :shapes :as opts}]
  (let [served (set (some-> (:kind-theory opts) (str/split #",") (->> (map #(kt/kind-of (first (str/split % #":")))))))]
    (cond
      (nil? v) false
      (empty? served) (throw (ex-info "bnm.world-run: --shapes shapes a kind theory's inputs: give --kind-theory" {}))
      (= "true" v) true
      :else (let [ks (set (str/split v #","))]
              (doseq [k ks :when (not (served k))]
                (throw (ex-info (str "bnm.world-run: --shapes: no kind theory for " k) {:kind k})))
              ks))))

(defn raw-prior-choice
  "--raw-prior kind,... (t121): those kind theories fit with every slope's
  prior on its input's own scale (bnm.theory/fits :raw); false without
  the flag."
  [{v :raw-prior :as opts}]
  (let [served (set (some-> (:kind-theory opts) (str/split #",") (->> (map #(kt/kind-of (first (str/split % #":")))))))]
    (cond
      (nil? v) false
      (empty? served) (throw (ex-info "bnm.world-run: --raw-prior sets a kind theory's priors: give --kind-theory" {}))
      :else (let [ks (set (str/split v #","))]
              (doseq [k ks :when (not (served k))]
                (throw (ex-info (str "bnm.world-run: --raw-prior: no kind theory for " k) {:kind k})))
              ks))))

(defn- theory-db
  "The theory layers' table: every path in bnm.kind-theory/series-paths,
  read for `vars`, the slow table with its real lags."
  [vars real-lags]
  (let [vs (st/read-variables "data/slow/variables.tsv")]
    (apply merge (for [p (kt/paths-for vars)]
                   (nm/load-series p vs (cond-> {:vars vars} (= p "data/slow/series.tsv") (assoc :real-lags real-lags)))))))

(defn global-shapes-choice
  "--global-shapes kind,... (t117): the global layer's inputs shaped
  (bnm.kind-theory/shape) for the kinds named; #{} without the flag."
  [{v :global-shapes g :global-theory}]
  (if-not v
    #{}
    (let [ks (set (str/split v #",")) served (set (some-> g (str/split #",")))]
      (doseq [k ks :when (not (served k))]
        (throw (ex-info (str "bnm.world-run: --global-shapes: the global layer does not serve " k) {:kind k})))
      ks)))

(defn global-raw-prior-choice
  "--global-raw-prior kind,... (t123): the global layer's slopes for the
  kinds named fit on priors on each input's own scale (bnm.theory/fits
  :raw); #{} without the flag."
  [{v :global-raw-prior g :global-theory}]
  (if-not v
    #{}
    (let [ks (set (str/split v #",")) served (set (some-> g (str/split #",")))]
      (doseq [k ks :when (not (served k))]
        (throw (ex-info (str "bnm.world-run: --global-raw-prior: the global layer does not serve " k) {:kind k})))
      ks)))

(defn global-driver-precision-choice
  "--global-driver-precision p (t124): the global layer's six world
  drivers fit at p times the default prior precision, the channels at the
  default; nil without the flag."
  [{v :global-driver-precision g :global-theory}]
  (when v
    (when-not g (throw (ex-info "bnm.world-run: --global-driver-precision needs --global-theory" {})))
    (let [p (Double/parseDouble v)]
      (when-not (pos? p) (throw (ex-info "bnm.world-run: --global-driver-precision must be positive" {:p p})))
      p)))

(defn global-theory-choice
  "--global-theory kind,... (bnm-1ft): the global layer (bnm.kind-theory/
  global) on top of each named kind's year offset; nil without the flag."
  [{v :global-theory}]
  (when v
    (let [ks (str/split v #",")]
      (doseq [k ks :when (not (kt/global-kinds k))]
        (throw (ex-info (str "bnm.world-run: --global-theory: the economic kinds only, not " k) {:kind k})))
      ks)))

(defn kind-theory-add
  "The kinds --kind-theory names as kind:add, their theory on top of the
  year offset (t112)."
  [{v :kind-theory}]
  (set (for [w (some-> v (str/split #",")) :let [[k mode] (str/split w #":")] :when (= "add" mode)] (kt/kind-of k))))

(defn with-bloc-theory
  "The named kinds' specs with :offset-add at the year scale (k is a
  year's: docs/ALIGNMENT-DATA.md, \"Bloc exposure offset\"), summed with
  an :offset-add already there."
  [specs choice data]
  (reduce-kv (fn [acc kd k]
               (if (contains? acc kd)
                 (let [f (blocs/offset-add data k)
                       prev (get-in acc [kd :offset-add])]
                   (assoc-in acc [kd :offset-add]
                             (fn [scale place t]
                               (let [o (when prev (prev scale place t))
                                     a (when (= :year scale) (f scale place t))]
                                 (if (and o a) (+ o a) (or a o))))))
                 acc))
             specs (or choice {})))

(defn credit-denominator-choice
  "--credit-denominator smooth (bnm-5lr): under --dated-credit, the BIS's
  credit over GDP with a fall in nominal GDP taken out
  (bnm.lattice-banking/gdp-fall-adjusted). Off by default."
  [opts]
  (when-let [v (:credit-denominator opts)]
    (when-not (= "smooth" v)
      (throw (ex-info (str "bnm.world-run: --credit-denominator takes smooth, not " v) {})))
    (when-not (dated-credit-choice opts)
      (throw (ex-info "bnm.world-run: --credit-denominator smooth needs --dated-credit true" {})))
    :smooth))

(defn regime-choice
  "--regime true (bnm-56c.40, recursive step 5b): the world families'
  calm/stress chains set the year node's level through the frailty. Off by
  default."
  [opts]
  (flag-choice opts :regime))

(def regime-sets
  "Each kind the regime layer reaches: its world family's chain and gauges
  (positions in bnm.named/leading's), and delta's theory prior [mean
  variance] (docs/RECURSIVE.md step 5b: 1.0 where financial stress is the
  kind's own domain, 0.5 where it reaches the kind by transmission, sd
  0.5). The other kinds read no world family."
  {"market_crash" {:chain rf/financial :gauges [:baa12 :us-curve :vix12] :prior [1.0 0.25]}
   "banking_crisis" {:chain rf/financial :gauges [:baa12 :us-curve :vix12] :prior [1.0 0.25]}
   "growth_collapse" {:chain rf/financial :gauges [:baa12 :us-curve :vix12] :prior [0.5 0.25]}
   "food_price_shock" {:chain rf/food :gauges [:world-food12] :prior [0.5 0.25]}})

(defn regime-drivers
  "(fn [t]) -> the family's gauges read at t (bnm.named/leading, at their
  availability dates), each standardized against its own history at the
  quarter ends from `from` before t (bnm.lattice-political/
  history-standardizer, as --lead-std history), nil where unread or with
  under 20 quarter ends of history. World gauges: the same at every place."
  [lead-f names from end]
  (let [ix (mapv #(.indexOf ^java.util.List nm/leading-names %) names)
        h (lp/history-standardizer lead-f ["USA"] ix from end nil)]
    (fn [t] (let [l (lead-f "USA" t)] (h t (mapv #(nth l %) ix))))))

(defn with-regime
  "Each spec of `regime-sets` with :regime at its year node (the chain's
  reader over its year origins, the window its year scale's), when `on?`;
  `lead-f` is bnm.named/leading's reader. A spec without :frailty is
  refused."
  [specs on? lead-f]
  (if-not on?
    specs
    (into {} (for [[k sp] specs]
               [k (if-let [{:keys [chain gauges prior]} (regime-sets k)]
                    (let [{:keys [per offset horizon]} (first (filter #(= :year (:name %)) (:scales sp)))
                          start (:start sp) end (:end sp)
                          origins (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))]
                      (when-not (map? (:frailty sp))
                        (throw (ex-info "bnm.world-run: --regime true needs --set's :frailty" {:kind k})))
                      (assoc sp :regime {:year {:at (rf/reader chain origins (regime-drivers lead-f gauges start end)
                                                               (/ (+ (double (or offset 0)) horizon) 12.0))
                                                :prior prior}}))
                    sp)]))))

(defn regime-clim-choice
  "--regime-clim true (bnm-56c.40, recursive step 5c): the year node's
  climatology by regime, each regime's resolved rate weighted by the chain's
  pi at issue. Off by default; not with --regime true (the same signal
  twice). --regime-clim uncentred: the same with the frailty's gauge mean
  reading the gauges as read, not net of their regime mean (added after the
  planted test, before any world run: docs/CALIBRATION.md 'Regime
  climatology'); :uncentred."
  [{:keys [regime regime-clim] :as opts}]
  (let [on (enum-choice opts :regime-clim {"true" true "uncentred" :uncentred "false" false} "true, uncentred or false")]
    (when (and on (= "true" regime))
      (throw (ex-info "bnm.world-run: --regime-clim and --regime true read the same regime twice" {})))
    on))

(defn with-regime-clim
  "Each spec of `regime-sets` with :regime-clim at its year node, when `on?`:
  the family chain's pi at the year origins, nil before its first gauge
  read (bnm.regime-filter/pi-reader), and, where the year node's frailty
  reads world gauges (--frailty-gauges), each one's regime mean at pi
  (its theory sign in the family; 0 for a gauge outside it), which the
  gauge mean reads net of. `lead-f` is bnm.named/leading's reader."
  [specs on? lead-f]
  (if-not on?
    specs
    (into {} (for [[k sp] specs]
               [k (if-let [{:keys [chain gauges]} (regime-sets k)]
                    (let [{:keys [per]} (first (filter #(= :year (:name %)) (:scales sp)))
                          start (:start sp) end (:end sp)
                          origins (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
                          at (rf/pi-reader chain origins (regime-drivers lead-f gauges start end))
                          sign (zipmap gauges (:signs chain))
                          fg (when-not (= :uncentred on?) (seq (get-in sp [:frailty :gauges :year])))]
                      (assoc sp :regime-clim
                             {:year (cond-> {:at at}
                                      fg (assoc :gauge-mean (fn [t] (when-let [p (at t)]
                                                                      (mapv #(if-let [s (sign %)] (rf/emission-mean chain s p) 0.0) fg)))))}))
                    sp)]))))

(defn wide-credit-choice
  "--wide-credit true (with --dated-credit true): the banking node's credit,
  household credit and house-price inputs for the countries outside the
  Macrohistory panel too, from the BIS by the same transforms and lag
  (bnm.lattice-banking/wide-credit-values); bank, the credit input from
  the BIS's bank credit (JST's concept) in place of all lenders'. Off by
  default."
  [{:keys [wide-credit] :as opts}]
  (let [on (enum-choice opts :wide-credit {"true" true "bank" :bank "false" false} "true, bank or false")]
    (when (and on (not (dated-credit-choice opts)))
      (throw (ex-info (str "bnm.world-run: --wide-credit " wide-credit " needs --dated-credit true") {})))
    on))

(defn options
  "The options of bnm.world-run `args`: {:out \"x\" ...}, values as given."
  [args]
  (cli/opts args))

(defn run-catalogues
  "The catalogues a run with `opts` fits and scores on: --food-outcomes,
  --at-risk and --catalogue-through applied to bnm.kinds/catalogues. The
  forward ledger scores an issue on its own command's."
  [opts]
  (let [cat-db (when (or (:food-outcomes opts) (:at-risk opts) (:growth-outcomes opts))
                 (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv") {:real-lags (real-lags-choice opts)}))]
    (cond-> (-> (food-outcomes (kinds/catalogues) cat-db (:food-outcomes opts))
                ;; bnm-56c.38: the merge of growth-q left this out of run-catalogues
                (growth-outcomes (:growth-outcomes opts) #(ee/quarterly-growth % cat-db))
                (at-risk cat-db (:food-outcomes opts) (:at-risk opts) (:growth-outcomes opts)))
      (= "true" (:catalogue-through opts)) catalogues-through
      (banking-spans-choice opts) (update lb/kind lb/with-spans (lb/read-spans lb/spans-path)))))

(defn fresh-inputs-choice
  "--fresh-inputs true: the political and economic kinds' year row reads
  log GDP per head, growth3 and population growth carried past
  Maddison's release by WDI's growth per head and population growth, each
  WDI year at its first edition in the WDI Database Archives
  (bnm.political-gauges/splice, docs/CALIBRATION.md \"Fresh inputs\").
  Off by default."
  [opts]
  (flag-choice opts :fresh-inputs))

(defn wdi-editions-choice
  "--wdi-editions true: every WDI annual series read no earlier than the
  first WDI Database Archives edition holding its year (bnm.named/
  load-series :wdi-editions): series.tsv's nine months ran a year ahead
  of publication at the Dec 2003-04 origins. Off by default."
  [opts]
  (flag-choice opts :wdi-editions))

(defn next-quarter-choice
  "--next-quarter true (bnm-56c.38, recursive step 4b): the issuing
  next-quarter scale, bnm.lattice-political/quarter-next, at every kind
  whose catalogue for it is sub-annual (`nq-kinds`). It replaces the
  frailty-only quarter-now, so --fresh-frailty is refused beside it. Off
  by default."
  [{:keys [fresh-frailty] :as opts}]
  (let [on (flag-choice opts :next-quarter)]
    (when (and on fresh-frailty)
      (throw (ex-info "bnm.world-run: --next-quarter true replaces --fresh-frailty's quarter-now" {})))
    on))

(defn horizon-priors-choice
  "--horizon-priors true (bnm-56c.38): the leading gauges' priors at the
  next quarter its own (bnm.lattice-political/horizon-priors, docs/LEADING.md
  \"Priors at the next quarter\", in-sample for the choice). Needs
  --next-quarter true and --leading-priors true; off by default."
  [{:keys [next-quarter leading-priors] :as opts}]
  (let [on (flag-choice opts :horizon-priors)]
    (when (and on (not (and (= "true" next-quarter) (= "true" leading-priors))))
      (throw (ex-info "bnm.world-run: --horizon-priors true sets the next quarter's priors: it needs --next-quarter true and --leading-priors true" {})))
    on))

(defn nq-catalogues
  "The next-quarter scale's catalogues (recursive step 4b): the run's
  `cats`, food price shocks by month (`food`, a fn of the catalogues, unless
  the run's already are, --food-outcomes monthly) and growth collapses by
  quarter (`growth`, unless --growth-outcomes quarterly), each with its
  at-risk rule under --at-risk catalogue (`db` for inflation's)."
  [cats {:keys [food growth at-risk db] fo :food-outcomes go :growth-outcomes}]
  (let [cs (cond-> cats
             (and (not= "monthly" fo) (cats "food_price_shock")) (assoc "food_price_shock" (food cats))
             (and (not= "quarterly" go) (cats "growth_collapse")) (assoc "growth_collapse" (growth cats)))]
    (bnm.world-run/at-risk cs db "monthly" at-risk "quarterly")))

(defn nq-kinds
  "The kinds among `ks` given the next-quarter scale: those whose catalogue
  for it (`nq`) dates onsets within the year (`sub-annual?`), never banking
  (its lattice is built apart)."
  [nq ks]
  (set (filter #(and (not= % lb/kind) (some-> (nq %) sub-annual?)) ks)))

(defn withdrawn-check
  "Refuses the withdrawn options: --aftermath and --aftermath-net (t130,
  t132) fit onset kernels beside the year node, from onsets before their
  catalogue's publication lag, the fit's intercept dropped and, where the
  node had no year offset, without its base (docs/CALIBRATION.md t132,
  t133). --aftermath-gauges replaces them."
  [opts]
  (doseq [flag [:aftermath :aftermath-net] :when (get opts flag)]
    (throw (ex-info (str "bnm.world-run: --" (name flag) " is withdrawn: its kernels read onsets before their publication lag (docs/CALIBRATION.md t133); use --aftermath-gauges") {}))))

(defn catalogue-onsets
  "{kind {unit [month ...]}} of the run's catalogues `cats`."
  [cats]
  (into {} (for [[k c] cats] [k (into {} (for [[u ms] (:onsets c)] [u (vec ms)]))])))

(defn aftermath-gauges-choice
  "--aftermath-gauges all or kinds (docs/CALIBRATION.md t133): the kinds
  among those run (`ks`) whose year node reads bnm.aftermath's kernels of
  known onsets as a gauge group of its own (af/with-gauges). nil when off;
  a kind not run is refused."
  [{:keys [aftermath-gauges]} ks]
  (when aftermath-gauges
    (let [named (if (= "all" aftermath-gauges) (vec ks) (str/split aftermath-gauges #","))
          bad (remove (set ks) named)]
      (when (seq bad)
        (throw (ex-info (str "bnm.world-run: --aftermath-gauges names a kind not run: " (str/join "," bad)) {})))
      named)))

(defn global-gauges-choice
  "--global-gauges kind,... (docs/CALIBRATION.md t134): the kinds among
  those run (`ks`) whose year node reads the global layer's inputs
  (bnm.kind-theory/global) as a gauge group of its own; nil when off. The
  economic kinds only, and not a kind --global-theory also serves."
  [{v :global-gauges g :global-theory} ks]
  (when v
    (let [named (str/split v #",")
          theory (set (some-> g (str/split #",")))]
      (doseq [k named :when (not (kt/global-kinds k))]
        (throw (ex-info (str "bnm.world-run: --global-gauges: the economic kinds only, not " k) {:kind k})))
      (when-let [bad (seq (remove (set ks) named))]
        (throw (ex-info (str "bnm.world-run: --global-gauges names a kind not run: " (str/join "," bad)) {})))
      (doseq [k named :when (theory k)]
        (throw (ex-info (str "bnm.world-run: --global-gauges and --global-theory both read the global inputs at " k) {:kind k})))
      named)))

(defn with-global-gauges
  "`specs` with `row`'s `width` columns (the global layer's inputs) as
  the year gauge group :global of `ks` (`global-gauges-choice`), named
  after bnm.kind-theory/global's declarations when `width` is theirs; the
  specs as they were when `ks` is nil."
  [specs ks row width gauge-std]
  (if-not ks
    specs
    (let [names (if (= width (count kt/global)) (mapv :name kt/global) (mapv #(keyword (str "global" %)) (range width)))]
      (reduce (fn [acc k] (update acc k yg/with-group row width :global names gauge-std)) specs ks))))

(defn direction-gauges-choice
  "--direction-gauges kind,... (docs/CALIBRATION.md t135): the kinds among
  those run (`ks`) whose year node reads the world's stress by its
  direction (bnm.named/direction) as a gauge group of its own; nil when
  off. Only kinds whose year node reads BAA and the VIX (--leading)."
  [{v :direction-gauges} ks]
  (when v
    (let [named (str/split v #",")]
      (doseq [k named :when (not (every? (set (get-in lp/leading-sets [k :year])) [:baa12 :vix12]))]
        (throw (ex-info (str "bnm.world-run: --direction-gauges: only kinds whose year node reads BAA and the VIX, not " k) {:kind k})))
      (when-let [bad (seq (remove (set ks) named))]
        (throw (ex-info (str "bnm.world-run: --direction-gauges names a kind not run: " (str/join "," bad)) {})))
      named)))

(defn with-aftermath-gauges
  "`specs` with bnm.aftermath's kernels as the year gauges of `ks`
  (`aftermath-gauges-choice`), each kind's onsets known after its
  catalogue's :known-lag; the specs as they were when `ks` is nil."
  ([specs ks cats] (with-aftermath-gauges specs ks cats :stable))
  ([specs ks cats gauge-std]
   (if-not ks
     specs
     (let [ons (catalogue-onsets cats)
           lags (into {} (for [[k c] cats] [k (:known-lag c 0)]))
           kinds (sort (keys cats))]
       (reduce (fn [acc k]
                 (let [countries (second (first (:levels (acc k))))]
                   (update acc k af/with-gauges (af/known-row ons lags k kinds countries) gauge-std)))
               specs ks)))))

(defn spec-opts
  "build-specs' options from the run's `opts`, with the fresh frailty kinds
  (`fresh`) and the next-quarter scale's kinds and catalogues (`nq`,
  `nq-cats`)."
  [{:keys [pwt-stale catalogue-through year-delay leak-fixes] :as opts} {:keys [fresh nq nq-cats]}]
  {:pwt-stale (= "true" pwt-stale)
   :lag-stale (= "true" (:lag-stale opts))
   :gauge-std (some-> (:gauge-std opts) keyword)
   :catalogue-through (= "true" catalogue-through)
   :year-delay (some-> year-delay parse-long)
   :leak-fixes (= "true" leak-fixes)
   :fresh-frailty fresh
   :next-quarter nq :nq-cats nq-cats
   :horizon-priors (horizon-priors-choice opts)
   :leading (leading-choice opts)
   :leading-priors (= "true" (:leading-priors opts))
   :real-lags (real-lags-choice opts)
   :leading-food2 (leading-food2-choice opts)
   :lead-std (lead-std-choice opts)
   :dated-credit (dated-credit-choice opts)
   :banking-gauges (banking-gauges-choice opts)
   :banking-coverage (banking-coverage-choice opts)
   :banking-spans (banking-spans-choice opts)
   :long-baseline (long-baseline-choice opts)
   :splice-credit (splice-credit-choice opts)
   :theory-offset (theory-offset-choice opts)
   :theory-world (theory-world-choice opts)
   :theory-contagion (theory-contagion-choice opts)
   :credit-denominator (credit-denominator-choice opts)
   :recession-hold (recession-hold-choice opts)
   :wide-credit (wide-credit-choice opts)
   :fresh-inputs (fresh-inputs-choice opts)
   :wdi-editions (wdi-editions-choice opts)
   :lead-delay (lead-delay-choice opts)})

(defn pipeline-specs
  "The run's specs for kinds `ks` through `end`: build-specs and every
  layer -main applies after it, in order (the last, the year gauge groups).
  Out of -main so one kind's spec can be built and read without a run."
  [opts ks end cats {:keys [fresh nq nq-cats]}]
  (withdrawn-check opts)
  (-> (build-specs ks end cats (known-run opts) (spec-opts opts {:fresh fresh :nq nq :nq-cats nq-cats}))
    (with-step2 {:world-gauges (= "true" (:world-gauges opts)) :world-once (= "true" (:world-once opts))
                 :world-extra (:world-extra opts) :world-gate (:world-gate opts)
                 :seed (:seed opts) :strength (:strength opts) :set (:set opts) :kind-set (:kind-set opts)
                 :real-lags (real-lags-choice opts)}
                end)
    finer-where-fresh
    (#(if-let [ks (kind-theory-choice opts)]
        (kt/with-kind-theory % ks (theory-db (kt/vars ks) (real-lags-choice opts))
                            (kind-theory-add opts) (impute-choice opts) (shapes-choice opts) (raw-prior-choice opts))
        %))
    (#(if-let [ks (global-theory-choice opts)]
        (kt/with-global-theory % ks (theory-db (kt/decl-vars kt/global) (real-lags-choice opts)) (impute-choice opts) (global-shapes-choice opts) (global-raw-prior-choice opts) (global-driver-precision-choice opts))
        %))
    (with-bloc-theory (bloc-theory-choice opts)
      (when (bloc-theory-choice opts)
        (blocs/read-data "data/derived/alignment-annual.csv" "data/derived/alignment-tie.csv")))
    (with-frailty-gauges (frailty-gauges-choice opts))
    (with-regime (regime-choice opts)
      (when (regime-choice opts)
        (second (nm/leading (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv")
                                            {:real-lags (real-lags-choice opts)})))))
    ;; step 5c: after --frailty-gauges, whose gauges it centres
    (with-regime-clim (regime-clim-choice opts)
      (when (regime-clim-choice opts)
        (second (nm/leading (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv")
                                            {:real-lags (real-lags-choice opts)})))))
    ;; last: its gauges wrap whatever gauges the earlier steps left
    (with-aftermath-gauges (aftermath-gauges-choice opts ks) cats (some-> (:gauge-std opts) keyword))
    (#(if-let [gks (global-gauges-choice opts ks)]
        (with-global-gauges % gks (:row (sg/assemble (theory-db (kt/decl-vars kt/global) (real-lags-choice opts)) kt/global))
          (count kt/global) (some-> (:gauge-std opts) keyword))
        %))
    (#(if-let [dks (direction-gauges-choice opts ks)]
        (let [[names row] (nm/direction (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv")
                                                        {:real-lags (real-lags-choice opts)}))]
          (reduce (fn [acc k] (update acc k yg/with-group row (count names) :direction names
                                      (some-> (:gauge-std opts) keyword) {:every 1 :min-periods 20}))
                  % dks))
        %))))

(defn -main [& args]
  (let [{:keys [out end kinds without-relations smoke graph placebo ews states catalogue-through world-gauges clim-prior] :as opts}
        (options args)
        _ (withdrawn-check opts)
        graph (:graph (model-choice opts))
        out (or out "world")
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        cats (run-catalogues opts)
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        graph (if graph (read-graph graph) {})
        parents (parents-of graph ks)
        warn (when ews
               (warnings (read-string (slurp ews))
                         (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv") {:real-lags (real-lags-choice opts)})
                         (set (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats))))))
        chosen (when states
                 (states-of (read-string (slurp states)) (set (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats))))))
        fresh (fresh-kinds (:fresh-frailty opts) ks cats)
        ;; --next-quarter true (bnm-56c.38): the scale's catalogues and kinds
        nq-cats (when (next-quarter-choice opts)
                  (let [db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv") {:real-lags (real-lags-choice opts)})]
                    (nq-catalogues cats {:food #(get (ee/with-monthly-food % db) "food_price_shock")
                                         :growth #(ee/quarterly-growth % db)
                                         :at-risk (:at-risk opts) :db db
                                         :food-outcomes (:food-outcomes opts) :growth-outcomes (:growth-outcomes opts)})))
        nq (if nq-cats (nq-kinds nq-cats ks) #{})
        _ (when (seq nq) (println "  the next-quarter scale at" (str/join "," (sort nq))))
        _ (println "building" (count ks) "kinds" (if (seq fresh) (str "; the fresh frailty scale at " (str/join "," (sort fresh))) ""))
        specs0 (pipeline-specs opts ks end cats {:fresh fresh :nq nq :nq-cats nq-cats})
        books (atom {})
        ;; each kind's resolved counts by node, as of the month (--clim-prior kinds)
        clims (atom {})
        _ (when (and clim-prior (not= "kinds" clim-prior))
            (throw (ex-info (str "bnm.world-run: --clim-prior takes kinds, not " clim-prior) {})))
        specs0 (if (= "kinds" clim-prior)
                 (into {} (for [[k sp] specs0] [k (assoc sp :clim-prior (cross-kind-prior clims k))]))
                 specs0)
        specs (into {} (for [[k sp] specs0]
                         [k (cond-> (if (= "true" without-relations) sp
                                      (with-relations sp (parents k) books cats
                                                      (into {} (for [[j sj] specs0] [j (set (map :name (:scales sj)))]))
                                                      ;; each parent's edge: its form and sign
                                                      (into {} (for [[[j k'] e] graph :when (= k' k)] [j e]))
                                                      (if (= "true" placebo)
                                                        (placebo-source (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats))))
                                                        identity)
                                                      {:warning (get warn k) :states (get chosen k)}))
                              (or (= "true" smoke) (:trace-groups opts)) (assoc :trace :inputs))]))
        ;; the runner owns the books atom the relations read
        _ (println "running the world")
        edges (vec (for [[[j k] _] graph :when (and (contains? (set ks) j) (contains? (set ks) k))] [j k]))
        joint-rows (atom [])
        results (let [ks (keys specs)]
                  (loop [states (into {} (for [k ks] [k (lt/start (specs k))]))]
                    (let [t (:t (first (vals states)))
                          y (quot t 12)]
                      (when (zero? (mod t 120)) (println "world at" y))
                      ;; the world's joint forecasts at each year end from 2003, from
                      ;; what it had learned by then: trial 42's second test
                      (when (and (not= "true" without-relations) (not= "true" smoke) (seq edges)
                                 (= 11 (mod t 12)) (<= 2003 y (- (quot end 12) 3)) (pos? (- t start)))
                        (let [wd (world-of specs states parents graph cats (dec t))]
                          (swap! joint-rows into (map #(assoc % :year y) (w/joints wd edges {:horizon 3 :draws 400 :seed y})))))
                      (if (> t end)
                        states
                        (do (reset! books (into {} (for [[k s] states] [k (lt/book s)])))
                            (reset! clims (into {} (for [[k s] states] [k (get-in s [:st :clim])])))
                            (recur (into {} (pmap (fn [[k s]] [k (lt/step (specs k) s)]) states))))))))
        finished (into {} (for [[k s] results] [k (lt/finish s)]))]
    (if (= "true" smoke)
      (let [fails (into {} (for [[k r] (sort-by key finished)]
                             (do (println "==" k) [k (smoke/checks (specs k) r k end)])))
            ;; the artificial world runs on what was learned: a small joint simulation
            fails (if (or (= "true" without-relations) (empty? edges))
                    fails
                    (try (w/joints (world-of specs results parents graph cats end) edges {:horizon 3 :draws 20 :seed 1})
                         (println "  ok   the artificial world simulates its edges")
                         fails
                         (catch Exception e
                           (println "  FAIL the artificial world:" (ex-message e))
                           (assoc fails "world" [(str "the artificial world: " (ex-message e))]))))]
        (doseq [[k fs] fails] (println k (if (empty? fs) "all checks passed" (str (count fs) " failed: " (str/join "; " fs)))))
        (System/exit (if (every? empty? (vals fails)) 0 1)))
      (do
        (doseq [[k r] finished] (write-kind (str dir "/" k) k r))
        ;; --trace-groups FILE (bnm-ris): each group's issued predictive at the
        ;; year country nodes, kind place origin group f q (log odds and their
        ;; variance as the averaging scores them)
        (when-let [f (:trace-groups opts)]
          (with-open [w (io/writer f)]
            (doseq [[k r] finished
                    {:keys [scale level place origin lp]} (:trace r)
                    :when (and (= :year scale) (= :country level) lp)
                    [g {fl :f q :q}] (sort-by key lp)]
              (.write w (str k "\t" place "\t" origin "\t" (name g) "\t" fl "\t" q "\n")))))
        ;; --regime (bnm-56c.40): the family's path at the year origins, year x pi
        (doseq [[k sp] specs :let [at (get-in sp [:regime :year :at])] :when at]
          (with-open [w (io/writer (str dir "/" k "/regime.tsv"))]
            (doseq [t (range (:start sp) (inc end)) :let [[x p] (at t)] :when p]
              (.write w (str (quot t 12) "\t" x "\t" p "\n")))))
        ;; --regime-clim (step 5c): pi at the year origins the chain had read, year pi
        (doseq [[k sp] specs :let [at (get-in sp [:regime-clim :year :at])] :when at]
          (with-open [w (io/writer (str dir "/" k "/regime.tsv"))]
            (doseq [t (range (:start sp) (inc end)) :let [p (at t)] :when p]
              (.write w (str (quot t 12) "\t" p "\n")))))
        ;; --write-reference true: the reference's forecasts as a run of
        ;; their own, <out>-ref
        (when (= "true" (:write-reference opts))
          (doseq [[k r] finished] (write-kind (str dir "-ref/" k) k (reference-forecasts r))))
        (with-open [wr (io/writer (str dir "/principal.tsv"))]
          (doseq [[k r] (sort-by key finished)
                  [[s p] byo] (:book r) :when (and (= s :year) (= 3 (count p)))
                  [o e] byo :when (:principal e)
                  :let [[g c] (:principal e)]]
            (.write wr (str k "\t" p "\t" (quot o 12) "\t" (if (namespace g) (str (namespace g) "/" (name g)) (name g)) "\t" c "\n"))))
        (with-open [wr (io/writer (str dir "/relations.tsv"))]
          (doseq [[k r] (sort-by key finished)
                  [[scale level] hist] (:weights r)
                  [t ws] hist :when (zero? (mod (- t 11) 12))
                  [g wt] ws :when (= "rel" (namespace g))]
            (.write wr (str k "\t" (name scale) "\t" (name level) "\t" (quot t 12) "\t" (name g) "\t" wt "\n"))))
        (when-not (= "true" without-relations)
          (let [world (world-of specs results parents graph cats end)
                base (w/simulate world {:horizon 3 :draws 2000 :seed 1})
                imp (w/impulse world {:horizon 3 :draws 1000 :seed 2})]
            (with-open [wr (io/writer (str dir "/world-baseline.tsv"))]
              (doseq [[k ps] (sort-by key (:p base)) [p x] (sort-by key ps)] (.write wr (str k "\t" p "\t" x "\n"))))
            (with-open [wr (io/writer (str dir "/world-impulse.tsv"))]
              (doseq [[[j k] x] (sort-by key imp)] (.write wr (str j "\t" k "\t" x "\n"))))
            (spit (str dir "/world.edn") (pr-str world)))
          (with-open [wr (io/writer (str dir "/world-joint.tsv"))]
            (doseq [{:keys [j k place year pj pk joint]} @joint-rows]
              (.write wr (str j "\t" k "\t" place "\t" year "\t" pj "\t" pk "\t" joint "\n")))))
        (println "wrote" dir)))))
