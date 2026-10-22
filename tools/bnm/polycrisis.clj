(ns bnm.polycrisis
  "Joint crises beyond pairs (bnm.hypergraph): on the design years, for
  each country and three-year window, the kinds with an onset together;
  for every triple of kinds, the three-way log ratio of odds ratios and
  G² (1 df) against the log-linear fit of its pairs, Benjamini-Hochberg
  across triples, both tails, with a country-resampled interval for the
  log ratio and the same tests at the other two window phases.

  Triples are kept when the fit expects five or more all-three windows (a
  rule on the pairs, not on the triple's own count). Pairs of kinds whose
  onsets nest (one mostly comes with the other: a war onset is usually a
  conflict onset that escalated) are flagged: a triple holding one is
  near-definitional, its three-way term forced.

    jolt -A:tools -M -m bnm.polycrisis [--to 2002] [--reps 200] [--start 1960]

  Prints the triples and a summary; writes data/runs/sequencing/polycrisis.tsv."
  (:require [augur.math :as m]
            [bnm.hypergraph :as hg]
            [bnm.kinds :as kinds]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn windows
  "{country [{:covered #{kind} :hit #{kind}} ...]}: each country's
  three-year windows [t, t + 2] for t = start, start + 3, ... with t + 2
  <= `to`, kept when three or more kinds cover the country all window
  long; :hit, the covered kinds with an onset in it."
  [cats start to]
  (let [countries (distinct (mapcat (comp keys :cover) (vals cats)))]
    (into {} (for [c countries]
               [c (vec (for [t (range start (- to 1) 3)
                             :let [covered (filter #(when-let [[a b] (get-in cats [% :cover c])] (and (<= a t) (<= (+ t 2) b))) (keys cats))]
                             :when (>= (count covered) 3)]
                         {:covered (set covered)
                          :hit (hg/simplex (into {} (for [k covered] [k (get-in cats [k :onsets c] #{})])) t 3)}))]))))

(def nested-min "Joint onsets a pair needs before its nesting is read." 10)

(defn nesting
  "{#{a b} share}: for each pair of kinds, over windows covering both, the
  larger of P(b onset | a onset) and P(a onset | b onset); pairs with
  fewer than `nested-min` joint onsets are left out (a share of one from a
  single window says nothing)."
  [ws ks]
  (let [all (mapcat val ws)]
    (into {} (for [a ks b ks :when (neg? (compare a b))
                   :let [w (filter #(and ((:covered %) a) ((:covered %) b)) all)
                         na (count (filter #((:hit %) a) w)) nb (count (filter #((:hit %) b) w))
                         nab (count (filter #(and ((:hit %) a) ((:hit %) b)) w))]
                   :when (>= nab nested-min)]
               [#{a b} (max (/ nab (double na)) (/ nab (double nb)))]))))

(def nested-share
  "A pair whose onsets come together at least this often is flagged."
  0.5)

(defn- triples [ks] (for [a ks b ks c ks :when (neg? (compare a b)) :when (neg? (compare b c))] [a b c]))

(defn- obs-for [ws cs triple] (for [c cs w (ws c) :when (every? (:covered w) triple)] (:hit w)))

(defn- resample [g xs] (vec (repeatedly (count xs) #(nth xs (long (* (m/next-double! g) (count xs)))))))

(defn analyse
  "The kept triples at window phase `start`, each with its statistics and
  Benjamini-Hochberg q (G² p, across the kept triples)."
  [cats ks start to]
  (let [ws (windows cats start to)
        rows (for [t (triples ks) :let [r (hg/lift (obs-for ws (keys ws) t) t)] :when (>= (:e111 r) 5.0)]
               (assoc r :triple t))
        qs (hg/bh (mapv :p rows))]
    {:ws ws :rows (mapv #(assoc %1 :q %2) rows qs)}))

(defn -main [& args]
  (let [{:keys [to reps start]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        to (or (some-> to parse-long) 2002)
        reps (or (some-> reps parse-long) 200)
        start (or (some-> start parse-long) 1960)
        cats (update-vals (kinds/catalogues) kinds/annual)
        ks (sort (keys cats))
        {:keys [ws rows]} (analyse cats ks start to)
        nest (nesting ws ks)
        nested (set (for [[p s] nest :when (>= s nested-share)] p))
        nested-in (fn [[a b c]] (filter nested [#{a b} #{b c} #{a c}]))
        g (m/rng 1)
        countries (vec (keys ws))
        dropped (atom 0)
        out (StringBuilder.)
        say (fn [s] (println s) (.append out (str s "\n")))
        boot (fn [t] (let [bs (sort (keep (fn [_] (let [o (obs-for ws (resample g countries) t)]
                                                   (if (seq o) (:log-ror (hg/lift o t)) (do (swap! dropped inc) nil))))
                                          (range reps)))
                           q (fn [p] (nth bs (min (dec (count bs)) (long (* p (count bs))))))]
                       [(q 0.05) (q 0.95)]))
        rows (mapv (fn [r] (let [[lo hi] (boot (:triple r))] (assoc r :lo lo :hi hi))) rows)]
    (println "pairs whose onsets nest (share >=" nested-share "):"
             (str/join ", " (for [[p s] (sort-by (comp - val) nest) :when (>= s nested-share)] (format "%s %.2f" (str/join "/" (sort p)) s))))
    (say "triple\tn\tall-three\texpected\tlift\tlog-ror\tse\tlo\thi\tg2\tp\tq\tnested")
    (doseq [{:keys [triple n observed e111 lift log-ror se lo hi g2 p q]} rows]
      (say (format "%s\t%d\t%d\t%.1f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.4f\t%.3f\t%s"
                   (str/join "+" triple) (long n) (Math/round (* observed n)) e111 lift log-ror se lo hi g2 p q
                   (str/join "," (map #(str/join "/" (sort %)) (nested-in triple))))))
    (io/make-parents "data/runs/sequencing/polycrisis.tsv")
    (spit "data/runs/sequencing/polycrisis.tsv" (str out))
    (let [summ (fn [rows]
                 (let [up (filter #(pos? (:log-ror %)) rows) dn (filter #(neg? (:log-ror %)) rows)]
                   (format "%d triples; G² p<0.05: %d above, %d below (%.1f expected by chance); BH q<0.10: %s"
                           (count rows) (count (filter #(< (:p %) 0.05) up)) (count (filter #(< (:p %) 0.05) dn))
                           (* 0.05 (count rows))
                           (let [s (filter #(< (:q %) 0.10) rows)]
                             (if (seq s) (str/join ", " (map #(format "%s (%.2f, q %.3f)" (str/join "+" (:triple %)) (:log-ror %) (:q %)) s)) "none")))))
          mde (let [v (vec (sort (map #(* 2.8 (:se %)) rows)))] (nth v (quot (count v) 2)))]
      (println)
      (println (format "phase %d: %s" start (summ rows)))
      (println (format "  bootstrap 90%% interval of the log ratio excludes 0: %d above, %d below; %d of %d resamples dropped (no windows)"
                       (count (filter #(> (:lo %) 0) rows)) (count (filter #(< (:hi %) 0) rows)) @dropped (* reps (count rows))))
      (println (format "  without nested pairs: %s" (summ (vec (remove (comp seq nested-in :triple) rows)))))
      (println (format "  median detectable |log ratio of odds ratios| (80%% power, 5%% two-sided): %.2f (a factor of %.1f)" mde (Math/exp mde)))
      (doseq [s (remove #{start} [1960 1961 1962])]
        (println (format "phase %d: %s" s (summ (:rows (analyse cats ks s to)))))))))
