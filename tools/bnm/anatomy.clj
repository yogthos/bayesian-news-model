(ns bnm.anatomy
  "The anatomy of known crises, before any training (docs/ANATOMY.md): how
  each dimension moved in the years before the crises we know, against the
  calm (periods far from any crisis), and in what order the dimensions
  turned. Superposed epochs: every crisis aligned at its onset, each lag's
  values pooled across crises.

  Only crises before 2003, so nothing here is learned from the years the
  models are scored on.

  - annual: the Macrohistory panel (18 countries, 1870-2002, its own
    crisis dates): the fourteen stocks as stretches against each country's
    previous decade, the displacement invariants, the opposed pairs of
    bnm.lattice-banking/tensions (credit against investment, house prices
    against the wage share, ...) as gaps, and each gap's size accumulated
    through time, reset at each crisis;
  - quarterly: bnm.named's financial inputs, for the modern catalogue's
    banking crises of 1974-2002.

    jolt -A:tools -M -m bnm.anatomy

  Writes data/runs/anatomy/anatomy.txt."
  (:require [bnm.lattice-banking :as lb]
            [bnm.macrohistory :as mh]
            [bnm.named :as nm]
            [bnm.onsets :as on]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- mean [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))

(defn- sd [xs]
  (when (> (count xs) 1)
    (let [m (mean xs)] (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) xs)) (dec (count xs)))))))

(defn epoch
  "Superposed epochs of `series` ({unit {period value}}) around `crises`
  ({unit #{periods}}): {lag {:mean :n :share-up :d :t}} for each of :lags,
  :d the mean's distance from the calm mean in calm standard deviations
  (the calm: periods more than :calm from any crisis of the unit), :t that
  times the root of n, :share-up the share of crises above the calm mean."
  [series crises {:keys [lags calm]}]
  (let [near? (fn [u p] (some #(<= (Math/abs (long (- p %))) calm) (get crises u)))
        base (for [[u ps] series [p v] ps :when (and (some? v) (not (near? u p)))] (double v))
        bm (or (mean base) 0.0)
        bs (Math/max 1e-9 (or (sd base) 0.0))]
    (into (sorted-map)
          (for [lag lags
                :let [vs (for [[u cs] crises c cs :let [v (get-in series [u (+ c lag)])] :when (some? v)] (double v))]
                :when (seq vs)]
            (let [m (mean vs) d (/ (- m bm) bs)]
              [lag {:mean m :n (count vs) :share-up (/ (count (filter #(> % bm) vs)) (double (count vs)))
                    :d d :t (* d (Math/sqrt (count vs)))}])))))

(defn turn
  "The earliest lag before the crisis from which `epochs` stay at least
  `threshold` calm deviations above the calm up to the crisis, or nil."
  [epochs threshold]
  (let [before (reverse (filter neg? (keys epochs)))]
    (last (take-while #(>= (get-in epochs [% :d]) threshold) before))))

(defn- accumulate-reset
  "{year acc}: |x| accumulated with `decay` a year, reset in crisis years."
  [by-year crisis-years decay]
  (loop [[y & ys] (sort (keys by-year)) acc 0.0 out {}]
    (if-not y
      out
      (let [x (by-year y)
            acc (if (crisis-years y) 0.0 acc)
            acc (if x (+ (Math/abs (double x)) (* decay acc)) (* decay acc))]
        (recur ys acc (assoc out y acc))))))

(defn- annual-series
  "{name {iso {year value}}} from the Macrohistory rows, before 2003."
  [rows crises]
  (let [rows (filter #(< (:year %) 2003) rows)
        dims (map first mh/dimensions)
        get-s (fn [r k] (get-in r [:specific k]))
        pairs (:year lb/tensions)
        pair-name (fn [[i j]] (str (name (nth dims i)) "-vs-" (name (nth dims j))))
        base (merge (into {} (for [k dims] [(name k) (fn [r] (get-s r k))]))
                    (into {} (for [k [:norm :breadth :resonance :norm-long :flow-norm :world-norm]]
                               [(name k) (fn [r] (get-in r [:invariant k]))]))
                    (into {} (for [[i j :as pr] pairs]
                               [(pair-name pr) (fn [r] (when-let [a (get-s r (nth dims i))]
                                                         (when-let [b (get-s r (nth dims j))] (- a b))))])))
        by (fn [f] (reduce (fn [acc r] (assoc-in acc [(:iso r) (:year r)] (f r))) {} rows))
        series (into {} (for [[n f] base] [n (by f)]))
        ;; each tension's size accumulated through time, reset at crises
        accumulated (into {} (for [pr pairs :let [n (pair-name pr)]]
                               [(str n " (accumulated)")
                                (into {} (for [[iso ys] (series n)]
                                           [iso (accumulate-reset ys (get crises iso #{}) 0.7)]))]))
        breadth-acc {"breadth (accumulated)" (into {} (for [[iso ys] (series "breadth")]
                                                        [iso (accumulate-reset ys (get crises iso #{}) 0.7)]))}]
    (merge series accumulated breadth-acc)))

(defn- table [out title series crises opts unit]
  (let [rows (for [[n s] series :let [e (epoch s crises opts)] :when (seq e)] [n e])
        lag-cols (filter #(<= -10 % 2) (:lags opts))
        fmt (fn [x] (if x (format "%+5.2f" (double x)) "   - "))]
    (.append out (str "\n" title "\n"))
    (.append out (format "%-38s %s   turn  up@-1  n@-1\n" (str "(d by lag, " unit ")")
                         (str/join " " (map #(format "%5d" %) lag-cols))))
    (doseq [[n e] (sort-by (fn [[_ e]] (- (Math/abs (double (or (get-in e [-1 :d]) 0.0))))) rows)]
      (.append out (format "%-38s %s  %5s  %4.2f  %4d\n" n
                           (str/join " " (map #(fmt (get-in e [% :d])) lag-cols))
                           (or (some-> (turn e 0.3) str) "-")
                           (double (or (get-in e [-1 :share-up]) 0.0))
                           (or (get-in e [-1 :n]) 0))))))

(defn -main [& _]
  (let [jst-rows (mh/read-jst "data/raw/slow/jst/jst.csv")
        built (mh/build jst-rows (mh/read-intensity nil) (mh/read-regimes) (mh/read-credit-gap))
        crises (reduce (fn [acc r] (if (and (= 1.0 (:crisisJST r)) (< (:year r) 2003))
                                     (update acc (:iso r) (fnil conj #{}) (:year r)) acc))
                       {} jst-rows)
        out (StringBuilder.)
        annual (annual-series built crises)
        ;; the modern catalogue's banking crises of 1974-2002, by quarter
        ev (get (on/read-events "data/slow/events.tsv") "banking_crisis")
        q-crises (into {} (for [[u qs] ev :let [qs (set (filter #(< % (* 4 2003)) qs))] :when (seq qs)] [u qs]))
        variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        units (set (keys q-crises))
        quarters (range (* 4 1970) (* 4 2003))
        wc (nm/world-credit db units (map #(+ 2 (* 3 %)) quarters))
        [names fin] (nm/financial db wc)
        quarterly (into {} (for [[i n] (map-indexed vector names)]
                             [(name n) (into {} (for [u units]
                                                  [u (into {} (for [q quarters :let [v (nth (fin u (+ 2 (* 3 q))) i)] :when v] [q v]))]))]))]
    (.append out (str "The anatomy of known banking crises, before 2003 (superposed epochs).\n"
                      "Each cell: the crisis-window mean against the calm (more than 5 periods from any crisis), in calm\n"
                      "standard deviations, at each lag (negative: before the onset). turn: the earliest lag from which it\n"
                      "stays 0.3 deviations or more above the calm up to the crisis; up@-1: the share of crises above the calm\n"
                      "mean a period before.\n"))
    (.append out (format "\nAnnual: %d Macrohistory crises, 1870-2002.\n" (reduce + (map count (vals crises)))))
    (table out "Stocks against their own decade, invariants, opposed pairs and their accumulation" annual crises
           {:lags (range -10 6) :calm 5} "years")
    (.append out (format "\nQuarterly: %d banking crises of the modern catalogue, 1974-2002.\n" (reduce + (map count (vals q-crises)))))
    (table out "Financial inputs (bnm.named), lags in quarters" quarterly q-crises
           {:lags (range -12 5) :calm 20} "quarters")
    (io/make-parents "data/runs/anatomy/x")
    (spit "data/runs/anatomy/anatomy.txt" (str out))
    (print (str out))))
