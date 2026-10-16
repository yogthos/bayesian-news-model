(ns bnm.onsets
  "The onset evaluation (docs/PHASE-CHANGES.md): can the model anticipate
  culminating events, and does seeing more domains help?

  The slow layer runs month by month over data/slow/, each value fed when it
  became available. At every quarter's end each country's inputs are kept: per
  domain its mean gap, slope and acceleration (politics, the event channels,
  is one domain), and the global drivers. Each row is labelled, per kind of
  event in data/slow/events.tsv, by whether an onset follows within quarters
  1-4 and 5-8. Rows where an onset of that kind is under way (one in the last
  four quarters) are left out: continuing is not anticipating.

  Five pooled logistic hazards are compared, each refit every year on the
  outcomes known by then and scored on the next year, out of sample:
    base      the rate so far
    history   the country's past onsets (how many, how recent)
    politics  history and the event channels
    domains   history, every other domain and the global drivers
    all       everything

    jolt -A:tools -M -m bnm.onsets [--name onsets] [--from 1985] [--score-from 2000]

  Writes data/runs/<name>/onsets.txt."
  (:require [bnm.hazard :as hz]
            [bnm.slow :as slow]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def windows {:w1 [1 4] :w2 [5 8]})

(def cases
  "Named cases, shown quarter by quarter before their onset. Illustration only:
  the scores pool every onset in the catalogues."
  [["protest_surge" "TUN" "2011-01"] ["protest_surge" "EGY" "2011-01"] ["protest_surge" "UKR" "2014-02"]
   ["protest_surge" "MMR" "2021-02"] ["protest_surge" "UKR" "2022-02"] ["conflict_onset" "UKR" "2022-02"]
   ["violence_surge" "SYR" "2011-04"] ["violence_surge" "LBY" "2011-02"] ["market_crash" "USA" "2008-10"]
   ["market_crash" "DEU" "2001-07"] ["banking_crisis" "GBR" "2007-08"] ["banking_crisis" "GRC" "2010-05"]])

(defn read-events [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[kind unit date] (str/split line #"\t" -1)]
                (update-in acc [kind unit] (fnil conj (sorted-set)) (quot (st/month-of date) 3))))
            {} (line-seq r))))

(defn- record-rows
  "Runs the slow layer and keeps each country's inputs at each quarter end
  from year `from`: [{:unit :q :x}], and the model."
  [cfg variables by-month from]
  (let [rows (java.util.ArrayList.)
        months (range (apply min (keys by-month)) (inc (apply max (keys by-month))))
        t0 (System/currentTimeMillis)
        mdl (reduce (fn [mdl t]
                      (let [mdl (slow/step! mdl t (get by-month t []))]
                        (when (zero? (rem t 120)) (println (st/month-str t) (- (System/currentTimeMillis) t0) "ms") (flush))
                        (when (and (= 2 (rem t 3)) (>= (quot t 12) from))
                          (doseq [unit (keys (:by-unit mdl))
                                  :when (and (not= unit "world") (= 3 (count unit)))]
                            (.add rows {:unit unit :q (quot t 3) :x (slow/unit-inputs mdl unit t)})))
                        mdl))
                    (slow/create cfg variables) months)]
    [(vec rows) mdl]))

(defn- history-features
  "How many onsets of this kind the unit has had up to quarter q, log1p, and
  how recent the last was (exp of minus years since over five)."
  [onsets q]
  (let [past (subseq onsets <= q)
        n (count past)]
    [(Math/log1p n) (if (pos? n) (Math/exp (- (/ (- q (long (last past))) 20.0))) 0.0)]))

(defn- label [onsets q [a b]]
  (boolean (seq (subseq onsets >= (+ q a) <= (+ q b)))))

(defn- ongoing? [onsets q]
  (boolean (seq (subseq onsets >= (- q 3) <= q))))

(defn- feature-sets
  "Indices into a row's full input vector [1 hist hist inputs...] for each model."
  [labels]
  (let [n (count labels)
        idx (fn [pred] (keep-indexed (fn [i l] (when (pred l) (+ 3 i))) labels))
        politics (idx #(= "politics" (:domain %)))
        others (idx #(and (:feature %) (not= "politics" (:domain %))))]
    {:base [0]
     :history [0 1 2]
     :politics (vec (concat [0 1 2] politics))
     :domains (vec (concat [0 1 2] others))
     :all (vec (concat [0 1 2] (range 3 (+ 3 n))))}))

(def model-order [:base :history :politics :domains :all])

(defn- pick ^doubles [^doubles full idx]
  (let [out (double-array (count idx))]
    (doseq [[j i] (map-indexed vector idx)] (aset out j (aget full (long i))))
    out))

(defn- keep-negative?
  "A fixed 15% of the negatives, chosen by hashing, for speed; weighted back up."
  [unit q]
  (< (mod (hash [unit q]) 100) 15))

(defn evaluate-kind
  "Out-of-sample predictions for one kind and window: {model [[q p y unit]]}."
  [rows onsets-by-unit units window score-from last-q]
  (let [labels-q (fn [{:keys [unit q]}] (label (get onsets-by-unit unit (sorted-set)) q window))
        usable (filterv (fn [{:keys [unit q]}]
                          (and (units unit)
                               (<= (+ q (second window)) last-q)
                               (not (ongoing? (get onsets-by-unit unit (sorted-set)) q))))
                        rows)
        prepared (mapv (fn [{:keys [unit q ^doubles x] :as r}]
                         (let [[h1 h2] (history-features (get onsets-by-unit unit (sorted-set)) q)
                               full (double-array (+ 3 (alength x)))]
                           (aset full 0 1.0) (aset full 1 h1) (aset full 2 h2)
                           (System/arraycopy x 0 full 3 (alength x))
                           (assoc r :full full :y (labels-q r))))
                       usable)
        years (range (quot (apply min (map :q prepared)) 4) (inc (quot last-q 4)))]
    (fn [sets]
      (reduce
       (fn [out model]
         (let [idx (sets model)
               k (count idx)]
           (loop [[y & more] (filter #(>= % (+ 10 (quot (:q (first prepared)) 4))) years)
                  beta nil
                  preds []]
             (if-not y
               (assoc out model preds)
               (let [q0 (* 4 y)
                     known (filter #(<= (+ (:q %) (second window)) q0) prepared)
                     train (for [{:keys [unit q y full]} known
                                 :when (or y (keep-negative? unit q))]
                             {:x (pick full idx) :y y
                              :w (* (Math/pow 0.95 (/ (- q0 q) 4.0)) (if y 1.0 (/ 100.0 15.0)))})
                     beta (if (some :y train)
                            (hz/fit train k {:prior 2.0 :start beta :iterations (if beta 2 8)})
                            beta)
                     scored (for [{:keys [unit q y full]} prepared
                                  :when (and beta (= (quot q 4) y) (>= (quot q 4) score-from))]
                              [q (hz/predict beta (pick full idx)) y unit])]
                 (recur more beta (into preds scored)))))))
       {} model-order))))

(defn- scores [preds base]
  (let [ps (map second preds) ys (map #(nth % 2) preds)]
    {:n (count preds) :pos (count (filter true? ys))
     :auc (hz/auc ps ys) :ap (hz/avg-precision ps ys)
     :r10 (hz/recall-at-budget (map (fn [[q p y]] [q p y]) preds) 0.10)
     :bss (when (seq base)
            (let [bb (hz/brier (map second base) (map #(nth % 2) base))]
              (when (pos? bb) (- 1.0 (/ (hz/brier ps ys) bb)))))}))

(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))

(defn run [{:keys [name from score-from]}]
  (let [run-name (or name "onsets")
        from (parse-long (or from "1985"))
        score-from (parse-long (or score-from "2000"))
        dir (str "data/runs/" run-name)
        variables (st/read-variables "data/slow/variables.tsv")
        by-month (st/read-observations "data/slow/series.tsv" 1950 2026)
        events (read-events "data/slow/events.tsv")
        [rows mdl] (record-rows {:globals st/default-globals} variables by-month from)
        labels (slow/input-labels mdl)
        sets (feature-sets labels)
        all-units (set (map :unit rows))
        market-units (set (for [{:keys [unit ^doubles x]} rows
                                :let [i (first (keep-indexed #(when (and (= "markets" (:domain %2)) (= :present (:feature %2))) %1) labels))]
                                :when (and i (pos? (aget x (long i))))]
                            unit))
        coverage {"banking_crisis" (set (keys (get events "banking_crisis")))
                  "market_crash" market-units}
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        case-preds (atom {})]
    (println (count rows) "country-quarters recorded")
    (say "Onsets, out of sample from" score-from "- AUROC, average precision, recall in the top 10% of each quarter,")
    (say "Brier skill against the base rate. Models: base, history, politics, domains (all other domains")
    (say "and global drivers), all.")
    (doseq [kind (sort (keys events))
            [wname window] (sort windows)]
      (let [onsets (get events kind)
            units (get coverage kind all-units)
            last-q (apply max (mapcat seq (vals onsets)))
            preds ((evaluate-kind rows onsets units window score-from last-q) sets)
            base (:base preds)]
        (swap! case-preds assoc [kind wname] preds)
        (say)
        (say (format "%s %s" kind (clojure.core/name wname)))
        (doseq [[era lo hi] [["all" 0 9999] ["<2015" 0 2014] [">=2015" 2015 9999]]]
          (let [in-era (fn [ps] (filter #(<= lo (quot (first %) 4) hi) ps))]
            (doseq [model model-order
                    :let [s (scores (in-era (preds model)) (in-era base))]
                    :when (pos? (:n s))]
              (say (format "  %-6s %-9s n=%6d pos=%4d  auc %s  ap %s  r@10%% %s  bss %s"
                           era (clojure.core/name model) (:n s) (:pos s) (f3 (:auc s)) (f3 (:ap s))
                           (f3 (:r10 s)) (f3 (:bss s)))))))))
    (say)
    (say "Cases: probability of onset within 1-4 quarters at each of the 8 quarters before, and rank among")
    (say "countries that quarter (history / domains / all)")
    (doseq [[kind unit date] cases
            :let [q (quot (st/month-of (str date "-01")) 3)
                  preds (get @case-preds [kind :w1])]
            :when preds]
      (say (str "  " kind " " unit " " date))
      (doseq [q0 (range (- q 8) q)]
        (say (str "    " (st/month-str (+ 2 (* 3 q0))) "  "
                  (str/join "  " (for [model [:history :domains :all]
                                       :let [in-q (filter #(= q0 (first %)) (preds model))
                                             mine (first (filter #(= unit (nth % 3)) in-q))
                                             rank (when mine (inc (count (filter #(> (second %) (second mine)) in-q))))]]
                                   (if mine (format "%.3f #%d/%d" (second mine) rank (count in-q)) "  -  ")))))))
    (io/make-parents (str dir "/x"))
    (spit (str dir "/onsets.txt") (str out))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
