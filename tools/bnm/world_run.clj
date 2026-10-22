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

    jolt -A:tools -M -m bnm.world-run --out world [--end m] [--kinds a,b] [--without-relations true]
                                      [--smoke true] [--graph path] [--placebo true]

  With --placebo, each country reads its parents at the next country in
  sorted order: the links' placebo.

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
  (:require [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.lattice-smoke :as smoke]
            [bnm.relations :as rl]
            [bnm.world :as w]
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
  above the countries the countries' mean of the raw form. A kind without
  a parent is left as it is.
  `source` maps the place to the one whose parents it reads (identity, or
  the placebo's other country)."
  [sp parents books cats scales-of edges source]
  (let [groups (into {} (for [[i j] (map-indexed vector parents)] [(keyword "rel" j) [i]]))
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
        own-at (fn [bs j form scale t]
                 (let [k [j form (scale-for j scale)]]
                   (or (get-in @owns [k :at t])
                       (let [raws (into {} (for [c leaves :let [r (raw-at bs j (base-of form) scale c t)] :when (some? (:x r))] [c r]))
                             [parts st'] (fac/observe (or (get-in @owns [k :st]) (fac/online par {:discount 0.99}))
                                                      t (update-vals raws :x))
                             at {:own (into {} (for [[c {:keys [own]}] parts] [c own]))
                                 :raw (update-vals raws :x)
                                 :reads (vec (mapcat :reads (vals raws)))}]
                         (swap! owns assoc k {:st st' :at {t at}})
                         at))))
        input (fn [bs j scale place t]
                (let [form (:form (edges j))]
                  (if (own? form)
                    (let [{:keys [own raw reads]} (own-at bs j form scale t)]
                      (if (contains? (set leaves) place)
                        {:x (double (get own place 0.0)) :reads reads}
                        (let [vs (keep raw (members place))]
                          {:x (if (seq vs) (/ (reduce + vs) (count vs)) 0.0) :reads reads})))
                    (rl/edge-input form {:kind j :book (bs j) :catalogue (cats j)} (scale-for j scale) place t))))]
    (if (empty? parents)
      sp
    (assoc sp
           :relations (fn [scale place t]
                        (let [bs @books
                              ;; a parent without this scale (no half-decade for the kinds from
                              ;; 1980) is read at its year node
                              ms (members place)
                              ins (for [j parents]
                                    (let [rs (if (and (own? (:form (edges j))) (not= ms [place]))
                                               [(input bs j scale (source place) t)]
                                               (map #(input bs j scale (source %) t) ms))]
                                      {:x (/ (reduce + 0.0 (map :x rs)) (count rs)) :reads (mapcat :reads rs)}))]
                          {:x (mapv :x ins) :reads (vec (mapcat :reads ins))}))
           :relation-width (into {} (for [{n :name} (:scales sp)] [n (count parents)]))
           :relation-groups (into {} (for [{n :name} (:scales sp)] [n groups]))
           :relation-nest true
           :principal :auto
           :prior (fn [scale level lay]
                    (merge (when prior (prior scale level lay))
                           (into {} (for [j parents :let [ix (get-in lay [:parts (keyword "rel" j)])] :when (seq ix)]
                                      [(first ix) [(* 0.2 (:sign (edges j) 1)) 0.1]]))))))))

(defn placebo-source
  "The placebo's map from each country to the next in sorted order (the
  last to the first), other places to themselves: a parent read from
  another country carries what is common to the period, not what the
  parent did at the place."
  [countries]
  (let [cs (vec (sort countries))
        m (zipmap cs (conj (subvec cs 1) (first cs)))]
    (fn [place] (get m place place))))

(defn build-specs
  "{kind spec} for `ks` to month `end`."
  [ks end cats]
  (into {} (pmap (fn [k]
                   [k (merge (if (= k lb/kind) (lb/build end "r6-at") (lp/build k end "r6-at" cats start))
                             quarter-design)])
                 ks)))

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
  forecasts by country and, per parent, its edge's form, effect and lag."
  [specs states parents graph cats end]
  (into {} (for [[k sp] specs
                 :let [st (states k)
                       eff (if (:relations sp) (lt/relation-effects sp st :year 0) [])
                       book (lt/book st)]]
             [k {:f (into {} (for [[[s p] byo] book :when (and (= s :year) (= 3 (count p)))
                                   :let [[_ e] (first (rsubseq byo <= end))] :when e] [p (:f e)]))
                 :q (into {} (for [[[s p] byo] book :when (and (= s :year) (= 3 (count p)))
                                   :let [[_ e] (first (rsubseq byo <= end))] :when e] [p (:q e)]))
                 :window 24
                 :parents (into {} (for [[i j] (map-indexed vector (parents k)) :when (seq eff)]
                                     [j {:form (:form (graph [j k])) :coef (nth eff i) :lag (:known-lag (cats j) 12)}]))}])))

(defn -main [& args]
  (let [{:keys [out end kinds without-relations smoke graph placebo]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        out (or out "world")
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        cats (kinds/catalogues)
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        graph (read-graph (or graph "data/runs/sequencing/graph-t43.edn"))
        parents (parents-of graph ks)
        _ (println "building" (count ks) "kinds")
        specs0 (build-specs ks end cats)
        books (atom {})
        specs (into {} (for [[k sp] specs0]
                         [k (cond-> (if (= "true" without-relations) sp
                                      (with-relations sp (parents k) books cats
                                                      (into {} (for [[j sj] specs0] [j (set (map :name (:scales sj)))]))
                                                      ;; each parent's edge: its form and sign
                                                      (into {} (for [[[j k'] e] graph :when (= k' k)] [j e]))
                                                      (if (= "true" placebo)
                                                        (placebo-source (filter #(= 3 (count %)) (mapcat (comp keys :from) (vals cats))))
                                                        identity)))
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
                            (recur (into {} (pmap (fn [[k s]] [k (lt/step (specs k) s)]) states))))))))
        finished (into {} (for [[k s] results] [k (lt/finish s)]))]
    (if (= "true" smoke)
      (let [fails (into {} (for [[k r] (sort-by key finished)]
                             (do (println "==" k) [k (smoke/checks (specs k) r k end)])))]
        (doseq [[k fs] fails] (println k (if (empty? fs) "all checks passed" (str (count fs) " failed: " (str/join "; " fs)))))
        (System/exit (if (every? empty? (vals fails)) 0 1)))
      (do
        (doseq [[k r] finished] (write-kind (str dir "/" k) k r))
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
