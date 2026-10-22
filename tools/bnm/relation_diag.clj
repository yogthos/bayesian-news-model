(ns bnm.relation-diag
  "Does each edge of the graph carry signal past the child's own forecast?
  On the design years only (year-end origins 1960-2002), for each edge j>k
  and each candidate form of what j passes, a logit of k's year outcome
  with k's forecast from a run without relations as its offset (so only
  what the forecast missed is left to explain) on the one input: the
  likelihood ratio against the offset alone, and the coefficient per
  spread. The candidates:

  - level: j's log odds at its latest origin before t (what trial 42 read);
  - destab: that level less j's mean over the five origins before it;
  - surprise: j's last resolved error known by t (outcome less forecast);
  - e1, e5: j's onsets as excursions (bnm.relations/excursion);
  - post1, post3: any onset of j known in the last one or three years;
  - and each again with j's series from another country (the countries
    rotated by one in sorted order): a placebo. A form that gains on its
    placebo absorbs what differs between countries, not what j does.

    jolt -A:tools -M -m bnm.relation-diag --run t42-world-norel [--to 2002] [--graph-out path]

  Prints a table and writes data/runs/<run>/relation-diag.tsv, and the
  edges kept, each with its form (choose-form), to
  data/runs/sequencing/graph-t43.edn."
  (:require [augur.logit :as lg]
            [augur.math :as m]
            [bnm.factors :as fac]
            [bnm.kinds :as kinds]
            [bnm.relations :as rl]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- logit [p] (let [p (max 1e-6 (min (- 1.0 1e-6) (double p)))] (Math/log (/ p (- 1.0 p)))))

(defn read-year
  "{place (sorted-map origin [p y])} of a kind's year node, countries only."
  [run kind]
  (with-open [r (io/reader (str "data/runs/" run "/" kind "/forecasts.tsv"))]
    (reduce (fn [acc l]
              (let [[s place o p y] (str/split l #"\t" -1)]
                (if (and (= s "year") (= 3 (count place)))
                  (update acc place (fnil assoc (sorted-map)) (parse-long o) [(parse-double p) (case y "true" true "false" false nil)])
                  acc)))
            {} (line-seq r))))

(defn forms
  "{form value} of what parent j (its year forecasts `fj` for the place, its
  catalogue `cj`) passes at origin t, nil where unknown."
  [fj cj place t]
  (let [past (some-> fj (rsubseq < t))
        level (some-> (first past) val first logit)
        prior (map (comp logit first val) (take 5 (rest past)))
        lag (:known-lag cj 12)
        resolved (first (filter (fn [[o [_ y]]] (and (some? y) (<= (+ o 24 lag) t))) past))
        ;; as the world run reads them: nothing before the catalogue covers the place
        covered? (when-let [f (get-in cj [:from place])] (<= f t))
        known (when covered? (subseq (get-in cj [:onsets place] (sorted-set)) <= (- t lag)))]
    {:level (when covered? level)
     :destab (when (and covered? level (>= (count prior) 3)) (- level (/ (reduce + prior) (count prior))))
     :surprise (when-let [[_ [p y]] resolved] (- (if y 1.0 0.0) p))
     :e1 (when covered? (rl/excursion cj place t 12.0))
     :e5 (when covered? (rl/excursion cj place t 60.0))
     :post1 (when covered? (if (some #(> % (- t lag 12)) known) 1.0 0.0))
     :post3 (when covered? (if (some #(> % (- t lag 36)) known) 1.0 0.0))}))

(defn- loglik [beta rows]
  (reduce + (for [{:keys [x y o]} rows :let [p (lg/predict beta x o)]]
              (Math/log (Math/max 1e-12 (if y p (- 1.0 p)))))))

(defn lr-test
  "{:lr :coef :n :pos} of one input against the offset alone; coef per
  the input's spread."
  [rows]
  (let [rows (vec rows)
        r0 (mapv #(update % :x (fn [x] (subvec x 0 1))) rows)
        b0 (lg/fit r0 1 {:prior 1e-4})
        b1 (lg/fit rows 2 {:prior 1e-4})
        xs (map #(nth (:x %) 1) rows)
        m (/ (reduce + xs) (max 1 (count xs)))
        sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) xs)) (max 1 (count xs))))]
    {:lr (* 2.0 (- (loglik b1 rows) (loglik b0 r0)))
     :coef (* (aget ^doubles b1 1) sd)
     :n (count rows) :pos (count (filter :y rows))}))

(def form-names [:level :destab :surprise :e1 :e5 :post1 :post3])

(def own-forms
  "The forms again as their own part at the place (bnm.factors): less what
  the world's and the region's countries share at t."
  [:level :destab :e1 :e5 :post3])

(defn with-own
  "Each place's forms {place {t forms}} with their own parts added as
  :<form>-own, decomposed across the places at each t."
  [by-place tree]
  (reduce (fn [acc f]
            (let [panel (into {} (for [[p byt] by-place :when (tree p)]
                                   [p (into (sorted-map) (for [[t fs] byt :let [v (f fs)] :when v] [t (double v)]))]))
                  parts (fac/decompose panel tree {:discount 0.99})
                  k (keyword (str (name f) "-own"))]
              (reduce (fn [acc [p byt]] (reduce (fn [acc [t {:keys [own]}]] (assoc-in acc [p t k] own)) acc byt)) acc parts)))
          by-place own-forms))

(defn critical
  "The likelihood ratio a one-input gain must reach when the best of `k`
  candidates is kept: chi-squared (1 df) at 5%/k (Bonferroni)."
  [k]
  (let [z (m/normal-quantile (- 1.0 (/ 0.05 (* 2.0 (max 1 k)))))] (* z z)))

(defn passes?
  "A candidate's design-year result {:lr :placebo-lr} passes: its gain
  past the child's forecast beyond the corrected threshold for `k`
  candidates, and at least twice its placebo's on the same rows."
  [{:keys [lr placebo-lr]} k]
  (and (>= lr (critical k)) (>= lr (* 2.0 placebo-lr))))

(def link-forms
  "The forms the lattice can read along an edge: each raw, and as its own
  part (bnm.factors), never the raw level."
  #{:post1 :post3 :e1 :e5 :destab :level-own :destab-own :e1-own :e5-own :post3-own})

(defn choose-form
  "An edge's form from its design-year results {form {:lr :coef
  :placebo-lr}} and its sign: of the forms the lattice reads, those in
  the edge's direction that pass (passes?, the threshold corrected for
  the forms tried); the post-crisis regime (:post3 or its
  own part, the larger) when among them, else the one that gains most;
  nil, no edge, without any."
  [results sign]
  (let [k (count (filter (comp link-forms key) results))
        ok (for [[f {:keys [lr coef] :as r}] results
                 :when (and (link-forms f) (= (pos? coef) (pos? sign)) (passes? r k))]
             [f lr])]
    (if-let [post (seq (filter #(#{:post3 :post3-own} (first %)) ok))]
      (first (apply max-key second post))
      (when (seq ok) (first (apply max-key second ok))))))

(defn -main [& args]
  (let [{:keys [run to graph-out]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        run (or run "t42-world-norel")
        to (or (some-> to parse-long) 2002)
        cats (kinds/catalogues)
        graph (wr/read-graph)
        ks (set (map first (keys graph)))
        ks (into ks (map second (keys graph)))
        years (into {} (pmap (fn [k] [k (read-year run k)]) ks))
        tree (fac/place-tree (set (mapcat keys (vals years))))
        out (StringBuilder.)
        results (atom {})
        say (fn [s] (println s) (.append out (str s "\n")))]
    (say (str "edge\tform\tn\tpos\tlr\tcoef\tplacebo-lr\tplacebo-coef"))
    (doseq [[[j k] e] (sort-by key graph)
            :let [fk (years k) fj (years j) cj (cats j)
                  places (vec (sort (filter tree (keys fk))))
                  other (zipmap places (concat (rest places) [(first places)]))
                  ;; the places on the gazetteer's tree only, so raw and own forms
                  ;; are tested on the same rows
                  base (for [place places :when (tree place) [o [p y]] (fk place)
                             :when (and (some? y) (= 11 (mod o 12)) (<= (* 12 1960) o (+ 11 (* 12 to))))]
                         [place o p y])
                  ;; every place's forms at every origin, then their own parts
                  origins (sort (set (map second base)))
                  fv (with-own (into {} (for [place places]
                                          [place (into (sorted-map) (for [o origins] [o (forms (fj place) cj place o)]))]))
                               tree)
                  rows-of (fn [placebo?]
                            (for [[place o p y] base
                                  :let [src (if placebo? (other place) place)]]
                              [(get-in fv [src o]) {:y y :o (logit p) :w 1.0}]))
                  real (vec (rows-of false)) fake (vec (rows-of true))]
            f (into form-names (map #(keyword (str (name %) "-own")) own-forms))
            ;; real and placebo on the same rows: where the form is known at both
            :let [pairs (for [[[fa r] [fb _]] (map vector real fake)
                              :let [va (f fa) vb (f fb)] :when (and (some? va) (some? vb))]
                          [(assoc r :x [1.0 (double va)]) (assoc r :x [1.0 (double vb)])])
                  a (mapv first pairs) b (mapv second pairs)]
            :when (>= (count (filter :y a)) 5)]
      (let [ra (lr-test a) rb (lr-test b)]
        (swap! results assoc-in [[j k] f] {:lr (:lr ra) :coef (:coef ra) :placebo-lr (:lr rb)})
        (say (format "%s>%s\t%s\t%d\t%d\t%.2f\t%+.3f\t%.2f\t%+.3f%s" j k (name f) (:n ra) (:pos ra) (:lr ra) (:coef ra) (:lr rb) (:coef rb) ""))))
    (spit (str "data/runs/" run "/relation-diag.tsv") (str out))
    ;; the edges kept, each with its form (trial 43's graph)
    (let [kept (into (sorted-map) (for [[edge e] graph :let [f (choose-form (@results edge) (:sign e 1))] :when f]
                                    [edge (assoc e :form f)]))]
      (println "kept" (count kept) "of" (count graph) "edges:")
      (doseq [[[j k] e] kept] (println (format "  %s>%s %s" j k (name (:form e)))))
      (spit (or graph-out "data/runs/sequencing/graph-t43.edn") (pr-str kept)))))
