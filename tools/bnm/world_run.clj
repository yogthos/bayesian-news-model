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
  --pwt-stale true (bnm-h8d), --catalogue-through true (Test 7),
  --year-delay n (bnm-pjg.6: the political and economic kinds' year rows
  read n years later; banking's own table is not delayed), --leak-fixes
  true (bnm-56c.23: Maddison, NMC and banking's PWT read by their
  releases, resources/bnm/releases.tsv; urban growth in one unit),
  --clim-prior kinds (bnm-56c.26: a node with no resolved outcome reads
  the rate the other kinds' nodes at its scale and level have resolved,
  not 0.5), and
  --write-reference true (also data/runs/<out>-ref, the forecasts with the
  reference state's p wherever a state is kept apart), --set '{...}' merged into every spec (:state-from :reference,
  :evidence-at :issue, :downscale-learn {:var 1.0}, :learn-offsets
  :mixture).

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
  (:require [bnm.ews :as ews]
            [bnm.states :as states]
            [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.lattice-smoke :as smoke]
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

(defn build-specs
  "{kind spec} for `ks` to month `end`, the quarters' offset pooled with
  run `known`'s named model. opts (recursive step 2, each off by default):
  :pwt-stale (bnm-h8d), :catalogue-through (Test 7; `cats` should carry
  it too, `catalogues-through`), :leak-fixes (bnm-56c.23)."
  ([ks end cats] (build-specs ks end cats "r6-at"))
  ([ks end cats known] (build-specs ks end cats known {}))
  ([ks end cats known opts]
   (into {} (pmap (fn [k]
                    [k (merge (if (= k lb/kind) (lb/build end known opts) (lp/build k end known cats start opts))
                              quarter-design)])
                  ks))))

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
  :set, an EDN map merged into every spec (a nil value removes its key)."
  [specs {:keys [world-gauges world-once world-extra seed strength] set-edn :set :as opts} end]
  (let [state (when world-gauges
                (let [db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
                      units (set (filter #(= 3 (count %)) (map first (keys db))))]
                  (wg/load-state db units end)))
        exposure (when world-gauges (wg/exposure-from))
        merge-set (fn [sp] (if set-edn
                             (reduce-kv (fn [sp k v] (if (nil? v) (dissoc sp k) (assoc sp k v))) sp (read-string set-edn))
                             sp))]
    (into {} (for [[k sp] specs]
               [k (merge-set
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
      (.write w (str (name scale) "\t" (name level) "\t" (quot t 12) "\t" (if (namespace g) (str (namespace g) "/" (name g)) (name g)) "\t" wt "\n")))))

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

(defn -main [& args]
  (let [{:keys [out end kinds without-relations smoke graph placebo ews states pwt-stale catalogue-through world-gauges year-delay leak-fixes clim-prior] :as opts}
        (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        graph (:graph (model-choice opts))
        out (or out "world")
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        cats (cond-> (kinds/catalogues) (= "true" catalogue-through) catalogues-through)
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        graph (if graph (read-graph graph) {})
        parents (parents-of graph ks)
        warn (when ews
               (warnings (read-string (slurp ews))
                         (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
                         (set (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats))))))
        chosen (when states
                 (states-of (read-string (slurp states)) (set (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats))))))
        _ (println "building" (count ks) "kinds")
        specs0 (-> (build-specs ks end cats (known-run opts) {:pwt-stale (= "true" pwt-stale)
                                                               :catalogue-through (= "true" catalogue-through)
                                                               :year-delay (some-> year-delay parse-long)
                                                               :leak-fixes (= "true" leak-fixes)})
                   (with-step2 {:world-gauges (= "true" world-gauges) :world-once (= "true" (:world-once opts))
                                :world-extra (:world-extra opts) :world-gate (:world-gate opts)
                                :seed (:seed opts) :strength (:strength opts) :set (:set opts)}
                               end))
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
                              (= "true" smoke) (assoc :trace :inputs))]))
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
