(ns bnm.anatomy-political
  "The anatomy of the known political onsets before 2003 (docs/
  ANATOMY-POLITICAL.md), as bnm.anatomy does for banking crises: every onset
  of a kind lined up at its year, each year gauge of bnm.political-gauges
  against the calm (years more than five from any onset of the kind in the
  country) in calm standard deviations; candidate tensions from theory as
  gaps between standardized gauges and as strain accumulated through time
  (never reset: a political onset does not close its gaps); candidate turns.

    jolt -A:tools -M -m bnm.anatomy-political

  Writes data/runs/anatomy/anatomy-political.txt."
  (:require [bnm.anatomy :as an]
            [bnm.lattice-banking :as lb]
            [bnm.named :as nm]
            [bnm.onsets :as on]
            [bnm.political-gauges :as pg]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [clojure.java.io :as io]))

(def kinds ["conflict_onset" "war_onset" "coup" "protest_surge" "violence_surge"])

(def tension-defs
  "Candidate contradictions, [a b]: a running ahead of b strains."
  [[:tertiary5 :growth3] [:population-growth3 :growth3] [:urban-growth5 :growth3] [:working-age :growth3]
   [:top10 :bottom50] [:inflation :growth3] [:food-inflation :growth3]
   [:soldiers-per-capita :spending-per-soldier3] [:participation :assembly] [:polyarchy :rule-of-law]
   [:repression :participation] [:social-exclusion :polyarchy]])

(def turn-defs
  "Candidate turns, [stock flow]: the stock stretched while the flow falls
  from its three-year peak."
  [[:growth3 :log-gdppc] [:anocracy :polyarchy] [:participation :assembly] [:participation :polyarchy]])

(defn- z-all
  "Each series standardized over all its country-years."
  [s]
  (let [vs (for [[_ ys] s [_ v] ys] v)
        n (count vs)]
    (if (< n 3)
      s
      (let [m (/ (reduce + vs) n)
            sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) vs)) (dec n)))]
        (update-vals s (fn [ys] (update-vals ys #(/ (- % m) (Math/max 1e-9 sd)))))))))

(defn -main [& _]
  (let [variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        annual (merge (pg/read-annual) (lb/read-annual) (pg/read-annual "data/derived/fiscal.csv"))
        wdi (fn [iso v y] (some-> (db [iso v]) (sk/value (+ 11 (* 12 y)) {:stale 18})))
        states (pg/read-states)
        long (pg/read-long)
        events (on/read-events "data/slow/events.tsv")
        years (range 1816 2003)
        rows (into {} (for [[iso [a b]] states y years :when (<= a y b)] [[iso y] (pg/year-row annual wdi iso y)]))
        series (into {} (for [[i k] (map-indexed vector pg/year-names)]
                          [k (reduce (fn [acc [[iso y] r]] (if-let [x (nth r i)] (assoc-in acc [iso y] (double x)) acc)) {} rows)]))
        zs (update-vals series z-all)
        out (StringBuilder.)]
    (.append out (str "The anatomy of known political onsets, before 2003 (superposed epochs, by year).\n"
                      "Each cell: the onset-window mean against the calm (more than 5 years from any onset of the kind), in\n"
                      "calm standard deviations; turn: the earliest lag from which it stays 0.3 above the calm.\n"
                      "Gauges are of the data year (V-Dem) or the year before (national accounts, WDI): lag 0 reads what\n"
                      "an origin at the end of the onset year would; lag -1 what one a year before it would.\n"))
    (doseq [kind kinds
            :let [modern (pg/modern kind)
                  crises (merge-with into
                                     (update-vals (get long kind {}) #(set (filter (fn [y] (< y modern)) %)))
                                     (update-vals (get events kind {}) #(set (keep (fn [q] (let [y (quot q 4)] (when (< y 2003) y))) %))))
                  crises (into {} (for [[u ys] crises :when (and (seq ys) (states u))] [u ys]))
                  gaps (into {} (for [[a b] tension-defs]
                                  [(str (name a) "-ahead-of-" (name b))
                                   (into {} (for [[iso ys] (zs a)]
                                              [iso (into {} (for [[y x] ys :let [z (get-in zs [b iso y])] :when z] [y (- x z)]))]))]))
                  strains (into {} (for [[n g] gaps]
                                     [(str n " (strain)")
                                      (into {} (for [[iso ys] g] [iso (#'an/accumulate-reset ys #{} 0.7)]))]))
                  turns (into {} (for [[s f] turn-defs]
                                   [(str "turn: " (name s) " stretched, " (name f) " off its peak")
                                    (into {} (for [[iso ys] (zs f) :let [dd (#'an/drawdown ys 3)]]
                                               [iso (into {} (for [[y d] dd :let [c (get-in zs [s iso y])] :when c]
                                                               [y (* (Math/max 0.0 c) d)]))]))]))
                  history (into {} (for [k kinds :let [c (get long k {}) e (get events k {})]]
                                     [(str k " in the ten years before")
                                      (into {} (for [[iso [a b]] states]
                                                 [iso (into {} (for [y (range (max a (pg/long-from k)) (min (inc b) 2003))]
                                                                 [y (double (count (filter #(< (- y 10) % y)
                                                                                           (concat (filter (fn [x] (< x (pg/modern k))) (get c iso))
                                                                                                   (map (fn [q] (quot q 4)) (get e iso))))))]))]))]))]]
      (.append out (format "\n%s: %d onsets in %d countries, %d-2002.\n" kind (reduce + (map count (vals crises))) (count crises)
                           (pg/long-from kind)))
      (#'an/table out "Year gauges, tensions and their strain, turns, history" (merge (update-keys series name) gaps strains turns history)
                  crises {:lags (range -10 6) :calm 5} "years"))
    (io/make-parents "data/runs/anatomy/x")
    (spit "data/runs/anatomy/anatomy-political.txt" (str out))
    (print (str out))))
