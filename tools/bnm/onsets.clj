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

  Pooled logistic hazards are compared, each refit every year on the
  outcomes known by then and scored on the next year, out of sample:
    base      the rate so far
    history   the country's past onsets (how many, how recent)
    politics  history and the event channels
    local     history and every domain of the country, no global drivers
    domains   history, every other domain and the global drivers
    all       everything

  Each model's prior precision is chosen from a grid by its log score on
  2000-2007; 2008 on is held out and scored with the chosen prior.
  Catalogues are scored only as far as data/slow/coverage.tsv says they have
  looked, so the last rows are not all positives.

    jolt -A:tools -M -m bnm.onsets [--name onsets] [--from 1985] [--rows <run>]
                                   [--kinds a,b] [--models base,history,...]
                                   [--events <training run with rows/, for the event model's state>]

  Writes data/runs/<name>/onsets.txt and the recorded rows, rows.tsv, which
  --rows reads back instead of running the slow layer."
  (:require [bnm.hazard :as hz]
            [bnm.named :as nm]
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

(defn read-coverage
  "kind -> last quarter its catalogue covers."
  [path]
  (into {} (for [line (str/split-lines (slurp path))
                 :let [[kind month] (str/split line #"\t")]]
             [kind (quot (st/month-of month) 3)])))

(defn- write-rows [path rows labels]
  (with-open [w (io/writer path)]
    (.write w (pr-str labels)) (.write w "\n")
    (doseq [{:keys [unit q ^doubles x]} rows]
      (.write w (str unit "\t" q "\t" (str/join "\t" (seq x)) "\n")))))

(defn- read-rows [path]
  (with-open [r (io/reader path)]
    (let [[head & lines] (line-seq r)]
      [(mapv (fn [line]
               (let [[unit q & xs] (str/split line #"\t")]
                 {:unit unit :q (parse-long q) :x (double-array (map parse-double xs))}))
             lines)
       (read-string head)])))

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

(defn hard-row?
  "No onset of the kind in the ten years up to q: the hard cases, where the
  country's history says nothing (Mueller & Rauh)."
  [onsets q]
  (empty? (subseq onsets >= (- q 40) <= q)))

(defn- ongoing? [onsets q]
  (boolean (seq (subseq onsets >= (- q 3) <= q))))

(defn- feature-sets
  "Indices into a row's full input vector [1 hist hist inputs... named...]
  for each model, given `m` named inputs."
  [labels m]
  (let [n (count labels)
        idx (fn [pred] (keep-indexed (fn [i l] (when (pred l) (+ 3 i))) labels))
        politics (idx #(= "politics" (:domain %)))
        local (idx :domain)
        others (idx #(and (:feature %) (not= "politics" (:domain %))))]
    {:base [0]
     :history [0 1 2]
     :politics (vec (concat [0 1 2] politics))
     :local (vec (concat [0 1 2] local))
     :domains (vec (concat [0 1 2] others))
     :all (vec (concat [0 1 2] (range 3 (+ 3 n))))
     :named (vec (concat [0 1 2] (range (+ 3 n) (+ 3 n m))))
     :named+politics (vec (concat [0 1 2] politics (range (+ 3 n) (+ 3 n m))))
     :event (vec (concat [0 1 2] (range (+ 3 n m) (+ 3 n m (count nm/event-labels)))))
     :named+event (vec (concat [0 1 2] (range (+ 3 n) (+ 3 n m (count nm/event-labels)))))}))

(def model-order [:base :history :politics :local :domains :all])

(def combined
  "Combinations of the fitted models, refit each year on the forecasts whose
  outcomes are known by then: a linear pool and a log-linear stack."
  [:pool :stack])

(def priors
  "Prior precisions tried, on the scale of each input's standard deviation."
  [10.0 100.0 1000.0])

(def tune-years [2000 2007])

(defn- pick ^doubles [^doubles full idx]
  (let [out (double-array (count idx))]
    (doseq [[j i] (map-indexed vector idx)] (aset out j (aget full (long i))))
    out))

(defn- keep-negative?
  "A fixed 15% of the negatives, chosen by hashing, for speed; weighted back up."
  [unit q]
  (< (mod (hash [unit q]) 100) 15))

(defn- log-score
  "Mean log probability of the outcomes."
  [preds]
  (when (seq preds)
    (/ (reduce + (for [[_ p y] preds
                       :let [p (Math/min (Math/max (double p) 1e-6) (- 1.0 1e-6))]]
                   (Math/log (if y p (- 1.0 p)))))
       (double (count preds)))))

(defn- in-years [preds lo hi] (filter #(<= lo (quot (first %) 4) hi) preds))

(defn evaluate-kind
  "Out-of-sample predictions for one kind and window: a function of the
  feature sets giving {model {:prior p :preds [[q p y unit]]}}, the prior
  chosen per model by log score over `tune-years`."
  [rows onsets-by-unit units window score-from last-q models fam]
  (let [labels-q (fn [{:keys [unit q]}] (label (get onsets-by-unit unit (sorted-set)) q window))
        usable (filterv (fn [{:keys [unit q]}]
                          (and (units unit)
                               (<= (+ q (second window)) last-q)
                               (not (ongoing? (get onsets-by-unit unit (sorted-set)) q))))
                        rows)
        prepared (mapv (fn [{:keys [unit q ^doubles x] :as r}]
                         (let [[h1 h2] (history-features (get onsets-by-unit unit (sorted-set)) q)
                               ^doubles nx (get-in r [:named fam])
                               ^doubles ev (or (:event r) (double-array (count nm/event-labels)))
                               full (double-array (+ 3 (alength x) (alength nx) (alength ev)))]
                           (aset full 0 1.0) (aset full 1 h1) (aset full 2 h2)
                           (System/arraycopy x 0 full 3 (alength x))
                           (System/arraycopy nx 0 full (+ 3 (alength x)) (alength nx))
                           (System/arraycopy ev 0 full (+ 3 (alength x) (alength nx)) (alength ev))
                           (assoc r :full full :y (labels-q r))))
                       usable)
        years (range (quot (apply min (map :q prepared)) 4) (inc (quot last-q 4)))]
    (fn [sets]
      (let [run-model
            (fn [idx prior]
              (let [k (count idx)]
                (loop [[yr & more] (filter #(>= % (+ 10 (quot (:q (first prepared)) 4))) years)
                       beta nil
                       preds []]
                  (if-not yr
                    preds
                    (let [q0 (* 4 yr)
                          known (filter #(<= (+ (:q %) (second window)) q0) prepared)
                          train (for [{:keys [unit q y full]} known
                                      :when (or y (keep-negative? unit q))]
                                  {:x (pick full idx) :y y
                                   :w (* (Math/pow 0.95 (/ (- q0 q) 4.0)) (if y 1.0 (/ 100.0 15.0)))})
                          beta (if (some :y train)
                                 (hz/fit train k {:prior prior :start beta})
                                 beta)
                          scored (for [{:keys [unit q y full]} prepared
                                       :when (and beta (= (quot q 4) yr) (>= yr score-from))]
                                   [q (hz/predict beta (pick full idx)) y unit])]
                      (recur more beta (into preds scored)))))))]
        (into {}
              (for [model models
                    :let [idx (sets model)
                          runs (for [p (if (= model :base) [1.0] priors)]
                                 {:prior p :preds (run-model idx p)})
                          [lo hi] tune-years]]
                [model (apply max-key #(or (log-score (in-years (:preds %) lo hi)) -1e9) (reverse runs))]))))))

(defn combine
  "Adds :pool and :stack to `runs`, combining every model but :base. Each
  year's combination is fit on the earlier forecasts whose outcomes were
  known by the year's start, discounted like the hazards, and scored on the
  year."
  [runs window]
  (let [members (vec (remove #{:base} (sort (keys runs))))
        by-key (into {} (for [m members, [q p y unit] (:preds (runs m))] [[m unit q] p]))
        keys* (sort-by second (distinct (for [[q _ y unit] (:preds (runs (first members)))] [unit q y])))
        rows (vec (for [[unit q y] keys*
                        :let [ps (mapv #(by-key [% unit q]) members)]
                        :when (every? some? ps)]
                    {:unit unit :q q :y y :ps ps}))
        years (sort (distinct (map #(quot (:q %) 4) rows)))]
    (loop [[yr & more] years pool [] stack []]
      (if-not yr
        (assoc runs :pool {:preds pool} :stack {:preds stack})
        (let [q0 (* 4 yr)
              known (filterv #(<= (+ (:q %) (second window)) q0) rows)
              this (filter #(= yr (quot (:q %) 4)) rows)]
          (if (< (count (filter :y known)) 10)
            (recur more pool stack)
            (let [ws (map #(Math/pow 0.95 (/ (- q0 (:q %)) 4.0)) known)
                  w (hz/pool-weights (map :ps known) (map :y known) ws)
                  beta (hz/fit-stack (map :ps known) (map :y known) ws {:prior 10.0})]
              (recur more
                     (into pool (for [{:keys [unit q y ps]} this] [q (hz/pool w ps) y unit]))
                     (into stack (for [{:keys [unit q y ps]} this] [q (hz/predict-stack beta ps) y unit]))))))))))

(defn- scores [preds base]
  (let [ps (map second preds) ys (map #(nth % 2) preds)]
    {:n (count preds) :pos (count (filter true? ys))
     :auc (hz/auc ps ys) :auq (hz/auc-within (map (fn [[q p y]] [q p y]) preds))
     :ap (hz/avg-precision ps ys)
     :r10 (hz/recall-at-budget (map (fn [[q p y]] [q p y]) preds) 0.10)
     :bss (when (seq base)
            (let [bb (hz/brier (map second base) (map #(nth % 2) base))]
              (when (pos? bb) (- 1.0 (/ (hz/brier ps ys) bb)))))}))

(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))

(defn run [{:keys [name from rows kinds models events]}]
  (let [run-name (or name "onsets")
        from (parse-long (or from "1985"))
        score-from (first tune-years)
        dir (str "data/runs/" run-name)
        _ (io/make-parents (str dir "/x"))
        events (read-events "data/slow/events.tsv")
        coverage-q (read-coverage "data/slow/coverage.tsv")
        models (if models (mapv keyword (str/split models #",")) [:base :history :politics :all])
        shown (into models combined)
        variables (st/read-variables "data/slow/variables.tsv")
        [rows labels] (if rows
                        (read-rows (str "data/runs/" rows "/rows.tsv"))
                        (let [by-month (st/read-observations "data/slow/series.tsv" 1950 2026)
                              [rows mdl] (record-rows {:globals st/default-globals} variables by-month from)
                              labels (slow/input-labels mdl)]
                          (write-rows (str dir "/rows.tsv") rows labels)
                          [rows labels]))
        all-units (set (map :unit rows))
        rows (let [db (nm/load-series "data/slow/series.tsv" variables)
                   wc (nm/world-credit db all-units (distinct (map #(+ 2 (* 3 (:q %))) rows)))
                   [fn* ff] (nm/financial db wc)
                   [pn* pf] (nm/political db)
                   [_ fv] (nm/vectorize fn* :financial)
                   [_ pv] (nm/vectorize pn* :political)]
               (let [es (if events (nm/load-event-state events) {})]
                 (mapv (fn [{:keys [unit q] :as r}]
                         (let [t (+ 2 (* 3 q))]
                           (assoc r :named {:financial (fv (ff unit t)) :political (pv (pf unit t))}
                                    :event (es [unit q]))))
                       rows)))
        n-named (into {} (for [fam [:financial :political]] [fam (alength ^doubles (get-in (first rows) [:named fam]))]))
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
    (say "Onsets, out of sample. Priors chosen on" (str/join "-" tune-years) "(tune); 2008 on is held out.")
    (say "AUROC pooled and within quarters (auq), average precision, recall in the top 10% of each quarter,")
    (say "Brier skill against the base rate. Models: base, history, politics, local (every domain of the")
    (say "country), domains (all but politics, and global drivers), all (domain averages), named (the")
    (say "literature's stocks for the kind, bnm.named); pool and stack combine all but base.")
    (doseq [kind (if kinds (str/split kinds #",") (sort (keys events)))
            [wname window] (sort windows)]
      (let [onsets (get events kind)
            units (get coverage kind all-units)
            last-q (coverage-q kind)
            fam (nm/family-of kind)
            runs (combine ((evaluate-kind rows onsets units window score-from last-q models fam)
                           (feature-sets labels (n-named fam)))
                          window)
            preds (update-vals runs :preds)
            base (:base preds)]
        (swap! case-preds assoc [kind wname] preds)
        (say)
        (say (format "%s %s (to %s)  priors: %s" kind (clojure.core/name wname) (st/month-str (+ 2 (* 3 last-q)))
                     (str/join " " (for [m (rest models)] (str (clojure.core/name m) "=" (long (:prior (runs m))))))))
        (doseq [[era lo hi hard] [["tune" 2000 2007] ["2008+" 2008 9999] ["08-14" 2008 2014] ["2015+" 2015 9999]
                                  ["hard" 2008 9999 true]]]
          (let [in-era (fn [ps] (cond->> (in-years ps lo hi)
                                  hard (filter (fn [[q _ _ unit]] (hard-row? (get onsets unit (sorted-set)) q)))))]
            (doseq [model shown
                    :let [s (scores (in-era (preds model)) (in-era base))]
                    :when (pos? (:n s))]
              (say (format "  %-5s %-9s n=%6d pos=%4d  auc %s  auq %s  ap %s  r@10%% %s  bss %s"
                           era (clojure.core/name model) (:n s) (:pos s) (f3 (:auc s)) (f3 (:auq s)) (f3 (:ap s))
                           (f3 (:r10 s)) (f3 (:bss s)))))))))
    (say)
    (say "Cases: probability of onset within 1-4 quarters at each of the 8 quarters before, and rank among")
    (say "countries that quarter (history / all / stack)")
    (doseq [[kind unit date] cases
            :let [q (quot (st/month-of (str date "-01")) 3)
                  preds (get @case-preds [kind :w1])]
            :when preds]
      (say (str "  " kind " " unit " " date))
      (doseq [q0 (range (- q 8) q)]
        (say (str "    " (st/month-str (+ 2 (* 3 q0))) "  "
                  (str/join "  " (for [model [:history :all :stack]
                                       :let [in-q (filter #(= q0 (first %)) (preds model))
                                             mine (first (filter #(= unit (nth % 3)) in-q))
                                             rank (when mine (inc (count (filter #(> (second %) (second mine)) in-q))))]]
                                   (if mine (format "%.3f #%d/%d" (second mine) rank (count in-q)) "  -  ")))))))
    (spit (str dir "/onsets.txt") (str out))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
