(ns bnm.world-run
  "The world model (docs/WORLD-MODEL.md, trial 42): every kind's lattice
  (bnm.kinds) stepped together, month by month from 1870, each reading its
  parents' states and onsets (the candidate graph of bnm.sequencing) and how
  many kinds are stressed together at the place, all from before t; then
  the learned world run forward as an artificial system (bnm.world).

  Each kind is built as on its own (bnm.lattice-banking, bnm.lattice-
  political, which builds the economic kinds and market crashes too) with
  the trial 41 design (the quarters on their own gauges, their offset the
  year's forecast pooled with named's, its uncertainty carried down) and a
  :relations group at the year and half-decade: per parent its state, its
  onsets as one- and five-year excursions, its state through the place's
  executive constraints (V-Dem's judicial and legislative constraints, the
  year before), and the coupling. A parent's state coefficient starts at
  theory's sign (mean 0.2, variance 0.1).

    jolt -A:tools -M -m bnm.world-run --out world [--end m] [--kinds a,b] [--without-relations true]
                                      [--smoke true]

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
  (:require [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.lattice-smoke :as smoke]
            [bnm.political-gauges :as pg]
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

(defn read-graph [] (read-string (slurp "data/runs/sequencing/graph.edn")))

(defn parents-of
  "{kind [parent kinds]} from the graph, among `ks`."
  [graph ks]
  (let [in (set ks)]
    (into {} (for [k ks] [k (vec (sort (for [[[j k'] _] graph :when (and (= k k') (in j))] j)))]))))

(defn- constraints
  "{[iso year] executive constraint}: the mean of V-Dem's judicial and
  legislative constraints on the executive."
  []
  (let [a (pg/read-annual)
        ju (get a "vdem_jucon") lg (get a "vdem_xlg_legcon")]
    (into {} (for [[u ys] ju [y x] ys :let [z (get-in lg [u y])] :when z] [[u y] (/ (+ x z) 2.0)]))))

(defn with-relations
  "`sp` with its :relations: the parents' inputs from `books` (an atom of
  {kind book}, the state before t), the coupling over every other kind."
  [sp kind parents all-kinds books cats moderator]
  (let [others (vec (remove #{kind} all-kinds))
        width (inc (* 4 (count parents)))
        groups (into {(keyword "rel" "coupling") [(* 4 (count parents))]}
                     (for [[i j] (map-indexed vector parents)] [(keyword "rel" j) (vec (range (* 4 i) (+ 4 (* 4 i))))]))
        cut (memoize (fn [k scale t] (rl/stress-cut (get @books k) scale t)))
        prior (:prior sp)]
    (assoc sp
           :relations (fn [scale place t]
                        (let [bs @books
                              m (get moderator [place (dec (quot t 12))] 0.0)
                              ins (map #(rl/parent-inputs {:kind % :book (bs %) :catalogue (cats %)} scale place t m) parents)]
                          {:x (vec (concat (mapcat :x ins) [(rl/coupling bs others scale place t #(cut % scale t))]))
                           :reads (vec (mapcat :reads ins))}))
           :relation-width (into {} (for [{n :name} (:scales sp)] [n width]))
           :relation-groups (into {} (for [{n :name} (:scales sp)] [n groups]))
           :principal :auto
           :prior (fn [scale level lay]
                    (merge (when prior (prior scale level lay))
                           (into {} (for [j parents :let [ix (get-in lay [:groups (keyword "rel" j)])] :when (seq ix)]
                                      [(first ix) [0.2 0.1]])))))))

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
  forecasts by country and its relation effects per parent."
  [specs states parents moderator end]
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
                                     [j (subvec eff (* 4 i) (+ 4 (* 4 i)))]))
                 :moderator (into {} (for [[[u y] m] moderator :when (= y (dec (quot end 12)))] [u m]))}])))

(defn -main [& args]
  (let [{:keys [out end kinds without-relations smoke]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        out (or out "world")
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        cats (kinds/catalogues)
        ks (if kinds (str/split kinds #",") (sort (keys kinds/tiers)))
        graph (read-graph)
        parents (parents-of graph ks)
        moderator (constraints)
        _ (println "building" (count ks) "kinds")
        specs0 (build-specs ks end cats)
        books (atom {})
        specs (into {} (for [[k sp] specs0]
                         [k (cond-> (if (= "true" without-relations) sp (with-relations sp k (parents k) ks books cats moderator))
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
                        (let [wd (world-of specs states parents moderator (dec t))]
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
          (let [world (world-of specs results parents moderator end)
                base (w/simulate world {:horizon 3 :draws 2000 :seed 1})
                imp (w/impulse world {:horizon 3 :draws 1000 :seed 2})]
            (with-open [wr (io/writer (str dir "/world-baseline.tsv"))]
              (doseq [[k ps] (sort-by key (:p base)) [p x] (sort-by key ps)] (.write wr (str k "\t" p "\t" x "\n"))))
            (with-open [wr (io/writer (str dir "/world-impulse.tsv"))]
              (doseq [[[j k] x] (sort-by key imp)] (.write wr (str j "\t" k "\t" x "\n"))))
            (spit (str dir "/world.edn") (pr-str (update-vals world #(dissoc % :moderator)))))
          (with-open [wr (io/writer (str dir "/world-joint.tsv"))]
            (doseq [{:keys [j k place year pj pk joint]} @joint-rows]
              (.write wr (str j "\t" k "\t" place "\t" year "\t" pj "\t" pk "\t" joint "\n")))))
        (println "wrote" dir)))))
