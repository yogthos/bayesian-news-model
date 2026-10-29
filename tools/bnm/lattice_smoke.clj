(ns bnm.lattice-smoke
  "Mechanical checks of the banking lattice before a training run: that it
  runs, forecasts probabilities, feeds every group live inputs, lets its
  weights move, uses its strain in theory's direction, reads nothing from
  the future, and exports rows that line up with the scoring tools.

    jolt -A:tools -M -m bnm.lattice-smoke [--end 23999] [--without-at ...] [--kind coup]
                                          [--downscale year --pool 0.5 --uncertainty true]

  With --kind, a political kind's lattice (bnm.lattice-political).

  Exits 1 when a check fails."
  (:require [bnm.geo :as geo]
            [bnm.lattice :as lt]
            [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.macrohistory :as mh]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [clojure.string :as str]))

(defn- corr [xs ys]
  (if (< (count xs) 2)
    0.0
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sx (Math/sqrt (reduce + (map #(* (- % mx) (- % mx)) xs)))
        sy (Math/sqrt (reduce + (map #(* (- % my) (- % my)) ys)))]
    (if (and (pos? sx) (pos? sy)) (/ sxy (* sx sy)) 0.0))))

(defn group-live
  "A group's inputs `ix` over a node's trace `rows` (every place at each
  origin): the share of values not zero, their spread, whether it had no
  input anywhere throughout (:dropped?), and the origins at which it had
  none anywhere yet kept weight in `weight-at` ({origin weight}): a group
  without inputs is an intercept-only model under another name, and must
  drop out of the averaging rather than stay dead and weighted."
  [rows ix weight-at]
  (let [vals (for [row rows i ix] (double (nth (:x row) i)))
        live (/ (count (filter #(not= 0.0 %) vals)) (double (count vals)))
        spread (let [m (/ (reduce + vals) (count vals))] (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) vals)) (count vals))))
        none-at (for [[o rs] (sort-by key (group-by :origin rows))
                      :when (every? (fn [row] (every? #(= 0.0 (double (nth (:x row) %))) ix)) rs)]
                  o)]
    {:live live :spread spread :dropped? (zero? live)
     :weighted (vec (filter #(pos? (get weight-at % 0.0)) none-at))}))

(defn checks
  "The smoke checks on a run `r` of spec `sp` for `kind` to month `end`;
  prints each and returns the failures."
  [sp r kind end]
  (let [fails (atom [])
        check (fn [ok? msg] (println (if ok? "  ok  " "  FAIL") msg) (when-not ok? (swap! fails conj msg)))]
    (println "smoke run to" (quot end 12) ":" (count (:forecasts r)) "forecasts")
    ;; 1. probabilities
    (check (every? #(let [p (:p %)] (and (number? p) (< 0.0 p 1.0))) (:forecasts r)) "every forecast a probability in (0, 1)")
    ;; 2. live inputs: per node and group, the share of rows where any input is nonzero, and its spread
    (doseq [[[scale level] rows] (group-by (juxt :scale :level) (:trace r))
            :let [lay (#'lt/layout sp scale (.indexOf (map first (:levels sp)) level))
                  late (filter #(>= (:origin %) (- end 120)) rows)]
            ;; a nested link's own input, not the node's inputs its model also reads
            [g ix] (sort-by key (merge (:groups lay) (:parts lay)))
            :when (and (seq ix) (not= g :all) (seq late))]
      (let [weight-at (into {} (for [[t ws] (get-in r [:weights [scale level]])] [t (get ws g 0.0)]))
            {:keys [live spread dropped? weighted]} (group-live late ix weight-at)
            ;; an own input (past the gauges) with fewer than ten known values is
            ;; still waiting for its scale (the standardizer reads it as 0), not dead
            g-width (get-in sp [:gauge-width scale] 0)
            {:keys [ns]} (get-in r [:flow-std [scale level]])
            waiting? (and ns (every? #(and (> % g-width) (< (nth ns (- % (inc g-width)) 0) 10)) ix))
            tag (format "%-12s %-9s %-14s" (name scale) (name level) (name g))]
        ;; at an origin without input anywhere a group must carry no weight
        (when (seq weighted)
          (check false (format "%s weighted at %d origins where it has no input" tag (count weighted))))
        (cond
          ;; no input in the window at all (a source that ended, errors not
          ;; yet known): dropped out of the averaging, not dead
          dropped? (println (format "        dropped out: %s no input in the last ten years, no weight" tag))
          (and waiting? (not (and (> live 0.01) (> spread 1e-6))))
          (println (format "        waiting: %s %s %s, fewer than ten known values" (name scale) (name level) (name g)))
          :else (check (and (> live 0.01) (> spread 1e-6))
                       (format "%s live %.2f spread %.2f" tag live spread)))))
    ;; 2b. every gauge input live somewhere in the run (a source that never
    ;; reaches the node, through a loader's whitelist say, reads as zeros)
    (doseq [[[scale level] rows] (group-by (juxt :scale :level) (:trace r))
            :when (= level :country)
            :let [w (get-in sp [:gauge-width scale] 0)
                  dead (for [i (range 1 (inc w)) :when (every? #(= 0.0 (double (nth (:x %) i))) rows)] (dec i))]]
      (check (empty? dead) (format "%s country: every gauge input live somewhere %s" (name scale) (vec dead))))
    ;; 3. no group's own model is broken: its log score per outcome over the
    ;; run against the intercept-only base model's. A broken one (forecasts
    ;; at the extremes) is a nat or more worse; past 0.25 fails, past 0.05 is
    ;; noted. A group whose weight stays below 1e-4 throughout lost on its
    ;; evidence, and fails only if its model is also broken
    (let [behind (into {} (for [[node ev] (:evidence r)
                                :let [[bl bn] (ev :base) base (/ bl bn)]]
                            [node (into {} (for [[g [ll n]] ev] [g (- base (/ ll n))]))]))]
      (doseq [[node gs] (sort-by key behind)
              :let [[g worst] (apply max-key val gs)]]
        (check (< worst 0.25) (format "%s %s: no group's model broken (worst %s, %.4f nats behind the base per outcome)"
                                      (name (first node)) (name (second node)) (name g) worst))
        (when (> worst 0.05) (println (format "        note: %s %s %s is %.4f nats behind the base" (name (first node)) (name (second node)) (name g) worst))))
      (doseq [[[scale level] hist] (sort-by key (:weights r))
              :let [gs (keys (second (last hist)))
                    dead (filter (fn [g] (every? #(< (get (second %) g 0.0) 1e-4) (drop 4 hist))) gs)
                    broken (filter #(>= (get-in behind [[scale level] %] 0.0) 0.25) dead)]]
        (check (empty? broken) (format "%s %s: no group both without weight and broken %s" (name scale) (name level) (vec broken)))
        (when (seq dead)
          (println (format "        lost on evidence (weight below 1e-4 throughout): %s %s %s"
                           (name scale) (name level)
                           (str/join ", " (for [g dead] (format "%s %.4f behind" (name g) (get-in behind [[scale level] g] 0.0)))))))))
    ;; 4. strain in theory's direction at the year: the countries' own mean
    ;; strain (the first contradiction input) against the outcomes; the
    ;; total strain they pass on (own strain and disagreement with the
    ;; parent either way) against the forecasts, for the record
    ;; on the design years' origins only (to 2002), and only for a kind with
    ;; tensions at the year (without them strain is zero throughout)
    (when (seq (get-in sp [:tensions :year]))
    (let [ys (into {} (for [{:keys [scale place origin y]} (:forecasts r) :when (and (= scale :year) (some? y))] [[place origin] y]))
          i (first (:strain-ix (#'lt/layout sp :year 0)))
          own (for [{:keys [scale level place origin x]} (:trace r)
                    :let [y (ys [place origin])]
                    :when (and (= scale :year) (= level :country) (>= origin (max (:start sp) (* 12 1960))) (<= origin (+ 11 (* 12 2002))) (some? y))]
                [(double (nth x i)) (if y 1.0 0.0)])
          r-y (corr (map first own) (map second own))
          rows (for [[[s place] byo] (:book r) :when (and (= s :year) (= 3 (count place)))
                     [o {:keys [p strain]}] byo :when (>= o (max (:start sp) (* 12 1960)))]
                 [(double strain) (Math/log (/ p (- 1.0 p)))])]
      ;; a relation in the direction the kind's prior states: more strain more
      ;; risk, or for a protective signal (a negative prior) less
      (let [sign (if (neg? (first (:contradiction-prior sp [0.3]))) -1.0 1.0)]
        (check (pos? (* sign r-y))
               (format "year: a country's own strain and its outcomes related in the prior's direction (%s, r = %.3f, n %d)"
                       (if (pos? sign) "more strain, more onsets" "more strain, fewer onsets") r-y (count own))))
      (println (format "        year: total strain and the forecast's log odds, r = %.2f" (corr (map first rows) (map second rows))))))
    ;; 5. nothing from the future
    (let [bad (for [{:keys [origin reads learned scale]} (:trace r)
                    rd reads
                    :when (case (:dir rd)
                            :down (>= (:origin rd) origin)
                            :downscale (>= (:origin rd) origin)
                            :relation (>= (:origin rd) origin)
                            :up (> (:origin rd) origin)
                            :feedback (> (:known-at rd) origin))]
                [scale origin rd])
          bad-learn (for [{:keys [origin learned]} (:trace r) {k :known-at} learned :when (> k origin)] origin)]
      (check (and (empty? bad) (empty? bad-learn)) (format "no read or learning from after the origin (%d bad reads)" (count bad))))
    ;; 6. the export lines up with the scoring tools' rows
    (let [known (lb/read-known "r6-at" kind)
          rows (for [{:keys [scale place origin y]} (:forecasts r)
                     :let [w ({:quarter-lead "lead" :quarter-w2 "w2"} scale)]
                     :when (and w (= 3 (count place)) (zero? (mod (- origin 2) 3)))]
                 [w place (quot (- origin 2) 3)])
          aligned (count (filter #(contains? known %) rows))]
      (check (every? (fn [{:keys [scale origin]}] (or (not (#{:quarter-lead :quarter-w2} scale)) (zero? (mod (- origin 2) 3))))
                     (:forecasts r))
             "quarter origins are quarter ends")
      (println (format "        %d of %d quarter rows have a named forecast at the same (window, unit, q)" aligned (count rows))))
    (when (:relations sp)
      ;; 7. relations: read only from before the origin, live, and the
      ;; principal contradiction recorded at every year node
      (let [rows (filter #(= :year (:scale %)) (:trace r))
            lay (#'lt/layout sp :year 0)
            rel-ix (vec (mapcat val (:parts lay)))
            late (filter #(and (= :country (:level %)) (>= (:origin %) (- end 120))) rows)
            vals (for [row late i rel-ix] (double (nth (:x row) i)))]
        (check (seq rel-ix) "year: relation groups present")
        (when (seq vals)
          (check (> (/ (count (filter #(not= 0.0 %) vals)) (double (count vals))) 0.05)
                 (format "year country: relation inputs live (%.2f nonzero)" (/ (count (filter #(not= 0.0 %) vals)) (double (count vals))))))
        (check (every? (fn [[[s p] byo]] (or (not= s :year) (not= 3 (count p)) (every? #(some? (:principal (val %))) (subseq byo >= (- end 120)))))
                       (:book r))
               "year country: a principal contradiction at every recent origin")))
    @fails))

(defn -main [& args]
  (let [{:keys [end without-at kind downscale pool uncertainty]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        end (or (some-> end parse-long) (+ (* 12 1999) 11))
        kind (or kind lb/kind)
        sp0 (if (not= kind lb/kind) (lp/build kind end "r6-at") (lb/build end "r6-at"))
        sp (cond-> sp0
             true (assoc :trace :inputs)
             downscale (assoc :downscale {:quarter-lead {:from (keyword downscale)} :quarter-w2 {:from (keyword downscale)}})
             pool (assoc :downscale-pool (parse-double pool))
             (= "true" uncertainty) (assoc :downscale-uncertainty true)
             without-at (assoc :without-at (into {} (for [part (str/split without-at #",") :let [[sc gs] (str/split part #":")]]
                                                      [(keyword sc) (set (map keyword (str/split gs #"\+")))]))))
        r (lt/run sp)
        fails (checks sp r kind end)]
    (println (if (empty? fails) "smoke: all checks passed" (str "smoke: " (count fails) " failed")))
    (System/exit (if (empty? fails) 0 1))))
