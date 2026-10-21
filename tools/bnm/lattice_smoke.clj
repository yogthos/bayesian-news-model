(ns bnm.lattice-smoke
  "Mechanical checks of the banking lattice before a training run: that it
  runs, forecasts probabilities, feeds every group live inputs, lets its
  weights move, uses its strain in theory's direction, reads nothing from
  the future, and exports rows that line up with the scoring tools.

    jolt -A:tools -M -m bnm.lattice-smoke [--end 23999] [--without-at ...] [--kind coup]

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
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        sxy (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sx (Math/sqrt (reduce + (map #(* (- % mx) (- % mx)) xs)))
        sy (Math/sqrt (reduce + (map #(* (- % my) (- % my)) ys)))]
    (if (and (pos? sx) (pos? sy)) (/ sxy (* sx sy)) 0.0)))

(defn -main [& args]
  (let [{:keys [end without-at kind]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        end (or (some-> end parse-long) (+ (* 12 1999) 11))
        kind (or kind lb/kind)
        sp0 (if (not= kind lb/kind)
              (lp/build kind end "r6-at")
              (let [variables (st/read-variables "data/slow/variables.tsv")
                    db (nm/load-series "data/slow/series.tsv" variables)
                    jst-rows (mh/read-jst "data/raw/slow/jst/jst.csv")
                    jst (into {} (for [r (mh/build jst-rows (mh/read-intensity nil) (mh/read-regimes) (mh/read-credit-gap))] [[(:iso r) (:year r)] r]))
                    cat (lb/catalogue jst-rows)
                    units (set (filter #(first (geo/parents %)) (keys (:onsets cat))))
                    wc (nm/world-credit db units (range (+ lb/start 2) (inc end) 3))
                    ext (lb/extended-table jst-rows (#'lb/read-pwt) db units (lb/read-annual))]
                (lb/spec {:db db :wc wc :jst jst :ext ext :cat cat :units units :end end :regimes (mh/read-regimes)
                          :known (lb/read-known "r6-at") :quarterly (lb/read-quarterly)})))
        sp (cond-> sp0
             true (assoc :trace :inputs)
             without-at (assoc :without-at (into {} (for [part (str/split without-at #",") :let [[sc gs] (str/split part #":")]]
                                                      [(keyword sc) (set (map keyword (str/split gs #"\+")))]))))
        r (lt/run sp)
        fails (atom [])
        check (fn [ok? msg] (println (if ok? "  ok  " "  FAIL") msg) (when-not ok? (swap! fails conj msg)))]
    (println "smoke run to" (quot end 12) ":" (count (:forecasts r)) "forecasts")
    ;; 1. probabilities
    (check (every? #(let [p (:p %)] (and (number? p) (< 0.0 p 1.0))) (:forecasts r)) "every forecast a probability in (0, 1)")
    ;; 2. live inputs: per node and group, the share of rows where any input is nonzero, and its spread
    (doseq [[[scale level] rows] (group-by (juxt :scale :level) (:trace r))
            :let [lay (#'lt/layout sp scale (.indexOf (map first (:levels sp)) level))
                  late (filter #(>= (:origin %) (- end 120)) rows)]
            [g ix] (sort-by key (:groups lay))
            :when (and (seq ix) (not= g :all) (seq late))]
      (let [vals (for [row late i ix] (double (nth (:x row) i)))
            live (/ (count (filter #(not= 0.0 %) vals)) (double (count vals)))
            spread (let [m (/ (reduce + vals) (count vals))] (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) vals)) (count vals))))]
        (check (and (> live 0.01) (> spread 1e-6))
               (format "%-12s %-9s %-14s live %.2f spread %.2f" (name scale) (name level) (name g) live spread))))
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
    (let [ys (into {} (for [{:keys [scale place origin y]} (:forecasts r) :when (and (= scale :year) (some? y))] [[place origin] y]))
          i (first (:strain-ix (#'lt/layout sp :year 0)))
          own (for [{:keys [scale level place origin x]} (:trace r)
                    :let [y (ys [place origin])]
                    :when (and (= scale :year) (= level :country) (>= origin (max (:start sp) (* 12 1960))) (some? y))]
                [(double (nth x i)) (if y 1.0 0.0)])
          r-y (corr (map first own) (map second own))
          rows (for [[[s place] byo] (:book r) :when (and (= s :year) (= 3 (count place)))
                     [o {:keys [p strain]}] byo :when (>= o (max (:start sp) (* 12 1960)))]
                 [(double strain) (Math/log (/ p (- 1.0 p)))])]
      (check (> r-y 0.0) (format "year: a country's own strain and its outcomes move together (r = %.3f, n %d)" r-y (count own)))
      (println (format "        year: total strain and the forecast's log odds, r = %.2f" (corr (map first rows) (map second rows)))))
    ;; 5. nothing from the future
    (let [bad (for [{:keys [origin reads learned scale]} (:trace r)
                    rd reads
                    :when (case (:dir rd)
                            :down (>= (:origin rd) origin)
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
    (println (if (empty? @fails) "smoke: all checks passed" (str "smoke: " (count @fails) " failed")))
    (System/exit (if (empty? @fails) 0 1))))
