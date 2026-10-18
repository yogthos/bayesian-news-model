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

  Input groups (--groups, default mix) add a model each, the group with
  history, and one with the named inputs too: mix (the event-code mix,
  bnm.mix), ties (partners and cross-pressure, bnm.ties), event (the event
  model's view, with --events), structure (sector and export shares and the
  world price shocks reaching a country through them), institutions (state
  effectiveness and rents where it is weak), youth (the educated young out
  of work in the cities), nonlinear (income and inequality as curves),
  regime (V-Dem democracy as a curve, civil society, integrity), tot (the
  IMF's commodity terms of trade by country: change, falls, volatility), fx
  (exchange-rate regime and anchor currency); see
  bnm.named.
  Every group also runs as a placebo,
  <group>-placebo, its values shuffled across countries within each quarter:
  a group whose gain the placebo matches carries nothing about the country.

  Each model's prior precision is chosen from a grid each year by its log
  score on the forecasts resolved before the year (bnm.onset/choose-by-year),
  scored from 2000; 2008 on is held out.
  Catalogues are scored only as far as data/slow/coverage.tsv says they have
  looked, so the last rows are not all positives.

    jolt -A:tools -M -m bnm.onsets [--name onsets] [--from 1985] [--rows <run>]
                                   [--kinds a,b] [--models base,history,...] [--groups mix,ties]
                                   [--events <training run with rows/, for the event model's state>]

  Writes data/runs/<name>/onsets.txt, the out-of-sample predictions,
  preds.tsv (kind window model quarter unit p outcome), and the recorded
  rows, rows.tsv, which --rows reads back instead of running the slow layer."
  (:require [bnm.calm :as calm]
            [bnm.hazard :as hz]
            [bnm.mix :as mix]
            [bnm.named :as nm]
            [bnm.onset :as o]
            [bnm.slow :as slow]
            [bnm.slow-train :as st]
            [bnm.ties :as ties]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; w1 the next year; lead the year's last three quarters only, so a gain there
;; is anticipation, not the nowcast of an onset already brewing; w2 the year after
(def windows {:w1 [1 4] :lead [2 4] :w2 [5 8]})

(def mix-path
  "The event-code mix per country and quarter (scripts/event-mix.sh)."
  "data/slow/event-mix.tsv")

(def dyads-path
  "Events between countries per quarter (scripts/event-dyads.sh)."
  "data/slow/event-dyads.tsv")

(defn- named-group
  "[width loader] for a group built like the named inputs: `build` of (db
  units months) gives [names f], f of (unit month) the raw values, which
  `vectorize` turns into doubles with the family's presence indicators.
  The third element, the presence indicators of a row, keeps its placebo
  among the rows with the same sources present."
  [build names family]
  (let [p (count (get nm/presence family))]
    [(+ (count names) p)
     (fn [{:keys [db units rows]}]
       (let [months (distinct (map #(+ 2 (* 3 (:q %))) rows))
             [names f] (build db units months)
             [_ vf] (nm/vectorize names family)]
         (into {} (for [{:keys [unit q]} rows] [[unit q] (vf (f unit (+ 2 (* 3 q))))]))))
     (fn [^doubles x] (vec (take-last p x)))]))

(def history-kinds
  "The catalogues whose histories feed every kind (the histories group)."
  ["banking_crisis" "conflict_onset" "coup" "market_crash" "protest_surge" "violence_surge" "war_onset"])

(def group-loaders
  "Input groups by name: [width loader], the loader taking {:db :units :rows}
  (the slow series, the countries, the onset rows) and giving {[unit
  quarter] double[]}."
  {"mix" [(count mix/labels) (fn [_] (nm/load-event-mix mix-path))]
   "ties" [(count ties/labels) (fn [_] (nm/load-ties dyads-path mix-path))]
   "ties-un" [(count ties/labels) (fn [_] (nm/load-ties-un dyads-path mix-path "data/raw/slow/unga/AgreementScores.csv"
                                                          "data/raw/slow/unga/COW-country-codes.csv"))]
   "structure" (named-group nm/structure nm/structure-names :structure)
   "institutions" (named-group nm/institutions nm/institutions-names :institutions)
   "youth" (named-group nm/youth nm/youth-names :youth)
   "nonlinear" (named-group nm/nonlinear nm/nonlinear-names :nonlinear)
   "regime" (named-group nm/regime nm/regime-names :regime)
   "tot" (named-group nm/terms-of-trade nm/tot-names :terms-of-trade)
   "fx" (named-group nm/fx nm/fx-names :fx)
   "calm" [(count calm/labels) (fn [_] (nm/load-event-calm mix-path))]
   "volcalm" (named-group nm/market-calm nm/market-calm-names :market-calm)
   "histories" [(* 2 (count history-kinds))
                (fn [{:keys [events rows]}]
                  (into {} (for [{:keys [unit q]} rows] [[unit q] (o/histories events history-kinds unit q)])))]})

(defn load-groups
  "[[name width {[unit quarter] double[]}] ...] for `names`, each followed by
  its placebo; the event model's view first when `event-run` is given. A
  group without values for every row gets a presence column
  (bnm.onset/with-presence), and the placebo shuffles values only among
  rows with the same sources present, so it keeps who has data."
  [names event-run ctx]
  (vec (concat (when event-run [["event" (count nm/event-labels) (nm/load-event-state event-run)]])
               (for [g names
                     :let [[width load stratum] (or (group-loaders g) (throw (ex-info (str "unknown group " g) {})))
                           [width xs] (o/with-presence width (load ctx) (:rows ctx))]
                     entry [[g width xs] [(str g "-placebo") width (ties/placebo xs 1 (or stratum (constantly nil)))]]]
                 entry))))

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
             [kind (o/last-complete-quarter (st/month-of month))])))

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


(def hard-row? o/hard-row?)

(defn- feature-sets
  "Indices into a row's full input vector [1 hist hist inputs... named...
  groups...] for each model, given `m` named inputs of which the last `p`
  are the political economy ones, and the input `groups` ([name width _])."
  [labels m p groups]
  (let [n (count labels)
        named (range (+ 3 n) (+ 3 n (- m p)))
        polecon (range (+ 3 n (- m p)) (+ 3 n m))
        spans (second (reduce (fn [[from acc] [g width]] [(+ from width) (conj acc [g (range from (+ from width))])])
                              [(+ 3 n m) []] groups))
        idx (fn [pred] (keep-indexed (fn [i l] (when (pred l) (+ 3 i))) labels))
        politics (idx #(= "politics" (:domain %)))
        local (idx :domain)
        others (idx #(and (:feature %) (not= "politics" (:domain %))))]
    (merge
     {:base [0]
     :history [0 1 2]
     :politics (vec (concat [0 1 2] politics))
     :local (vec (concat [0 1 2] local))
     :domains (vec (concat [0 1 2] others))
     :all (vec (concat [0 1 2] (range 3 (+ 3 n))))
     :named (vec (concat [0 1 2] named))
     :named+politics (vec (concat [0 1 2] politics named))
     :polecon (vec (concat [0 1 2] polecon))
     :named+polecon (vec (concat [0 1 2] named polecon))}
    (into {} (for [[g span] spans
                   entry [[(keyword g) (vec (concat [0 1 2] span))]
                          [(keyword (str "named+" g)) (vec (concat [0 1 2] named span))]]]
               entry)))))

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
  chosen per model and year by log score on the years before."
  [rows onsets-by-unit units window score-from last-q models fam groups]
  (let [labels-q (fn [{:keys [unit q]}] (o/label (get onsets-by-unit unit (sorted-set)) q window))
        ;; rows past the catalogue's coverage are predicted too, with no label:
        ;; they are the live forecasts (bnm.readings), never fit or scored
        usable (filterv (fn [{:keys [unit q]}]
                          (and (units unit)
                               (o/at-risk? (get onsets-by-unit unit (sorted-set)) q window)))
                        rows)
        prepared (mapv (fn [{:keys [unit q ^doubles x] :as r}]
                         (let [[h1 h2] (o/history-features (get onsets-by-unit unit (sorted-set)) q)
                               ^doubles nx (get-in r [:named fam])
                               ;; a group's inputs, zeros where it has none for the row
                               gs (mapv (fn [[_ width xs]] (or (get xs [unit q]) (double-array width))) groups)
                               full (double-array (+ 3 (alength x) (alength nx) (reduce + (map second groups))))]
                           (aset full 0 1.0) (aset full 1 h1) (aset full 2 h2)
                           (System/arraycopy x 0 full 3 (alength x))
                           (System/arraycopy nx 0 full (+ 3 (alength x)) (alength nx))
                           (reduce (fn [off ^doubles g] (System/arraycopy g 0 full off (alength g)) (+ off (alength g)))
                                   (+ 3 (alength x) (alength nx)) gs)
                           (assoc r :full full :y (when (<= (+ q (second window)) last-q) (labels-q r)))))
                       usable)
        years (range (quot (apply min (map :q prepared)) 4) (inc (quot (apply max (map :q prepared)) 4)))]
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
                                      :when (and (some? y) (or y (keep-negative? unit q)))]
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
                          runs (vec (for [p (if (= model :base) [1.0] priors)]
                                      {:prior p :preds (run-model idx p)}))]]
                ;; each year's prior chosen on the years before it, so no
                ;; scored year has picked its own prior
                [model (o/choose-by-year runs log-score window (or (first (filter #(= 100.0 (:prior %)) runs)) (first runs)))]))))))

(defn combine
  "Adds :pool and :stack to `runs`, combining every model but :base and the
  placebos. Each
  year's combination is fit on the earlier forecasts whose outcomes were
  known by the year's start, discounted like the hazards, and scored on the
  year."
  [runs window]
  (let [members (vec (remove #(or (= :base %) (str/ends-with? (name %) "-placebo")) (sort (keys runs))))
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
              known (filterv #(and (some? (:y %)) (<= (+ (:q %) (second window)) q0)) rows)
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
     ;; 90% interval resampling whole years; Sarlin's usefulness for a policymaker weighing
     ;; missed onsets by 0.9 and by 0.95 (for events this rare, a balanced policymaker never acts)
     :ci (when (and (some #(true? (nth % 2)) preds) (some #(false? (nth % 2)) preds))
           (hz/block-bootstrap-auc (map (fn [[q p y]] [(quot q 4) p y]) preds) 200 1))
     :u50 (when (some true? ys) (hz/usefulness ps ys 0.9))
     :u80 (when (some true? ys) (hz/usefulness ps ys 0.95))
     :bss (when (seq base)
            (let [bb (hz/brier (map second base) (map #(nth % 2) base))]
              (when (pos? bb) (- 1.0 (/ (hz/brier ps ys) bb)))))}))

(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))

(defn run [{:keys [name from rows kinds models groups] event-run :events}]
  (let [run-name (or name "onsets")
        from (parse-long (or from "1985"))
        score-from (first tune-years)
        dir (str "data/runs/" run-name)
        _ (io/make-parents (str dir "/x"))
        events (read-events "data/slow/events.tsv")
        coverage-q (read-coverage "data/slow/coverage.tsv")
        ;; base is always fitted: it is the reference for Brier skill
        models (into [:base] (remove #{:base}) (if models (mapv keyword (str/split models #",")) [:history :politics :all]))
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
        db (nm/load-series "data/slow/series.tsv" variables)
        rows (let [wc (nm/world-credit db all-units (distinct (map #(+ 2 (* 3 (:q %))) rows)))
                   [fn* ff] (nm/financial db wc)
                   [pn* pf] (nm/political db)
                   [_ fv] (nm/vectorize fn* :financial)
                   [_ pv] (nm/vectorize pn* :political)
                   [en* ef] (nm/political-economy db all-units (distinct (map #(+ 2 (* 3 (:q %))) rows)))
                   [_ ev] (nm/vectorize en* :political-economy)
                   both (fn [^doubles a ^doubles b] (double-array (concat a b)))]
               (mapv (fn [{:keys [unit q] :as r}]
                       (let [t (+ 2 (* 3 q))]
                         (assoc r :named (let [e (ev (ef unit t))]
                                          {:financial (both (fv (ff unit t)) e) :political (both (pv (pf unit t)) e)}))))
                     rows))
        groups (load-groups (if groups (str/split groups #",") ["mix"]) event-run {:db db :units all-units :rows rows :events events})
        n-named (into {} (for [fam [:financial :political]] [fam (alength ^doubles (get-in (first rows) [:named fam]))]))
        n-polecon (count (first (nm/vectorize nm/political-economy-names :political-economy)))
        market-units (set (for [{:keys [unit ^doubles x]} rows
                                :let [i (first (keep-indexed #(when (and (= "markets" (:domain %2)) (= :present (:feature %2))) %1) labels))]
                                :when (and i (pos? (aget x (long i))))]
                            unit))
        ;; banking crises are catalogued for every country (Laeven & Valencia,
        ;; the ESRB); share-price crashes only where there are share prices
        coverage {"market_crash" market-units}
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        case-preds (atom {})
        preds-out (io/writer (str dir "/preds.tsv"))]
    (println (count rows) "country-quarters recorded")
    (say "Onsets, out of sample. Priors chosen each year on the years before; tune" (str/join "-" tune-years) "; 2008 on is held out.")
    (say "AUROC pooled and within quarters (auq), average precision, recall in the top 10% of each quarter,")
    (say "Brier skill against the base rate; AUC's 90% interval resampling whole years; Sarlin's relative")
    (say "usefulness for a policymaker weighing missed onsets by 0.9 and 0.95 (the share of the loss of")
    (say "ignoring the forecast that it saves). Models: base, history, politics, local (every domain of the")
    (say "country), domains (all but politics, and global drivers), all (domain averages), named (the")
    (say "literature's stocks for the kind, bnm.named); pool and stack combine all but base.")
    (doseq [kind (if kinds (str/split kinds #",") (sort (keys events)))
            [wname window] (sort windows)]
      (let [onsets (get events kind)
            units (get coverage kind all-units)
            last-q (coverage-q kind)
            fam (nm/family-of kind)
            runs (combine ((evaluate-kind rows onsets units window score-from last-q models fam groups)
                           (feature-sets labels (n-named fam) n-polecon groups))
                          window)
            preds (update-vals runs :preds)
            base (:base preds)]
        (swap! case-preds assoc [kind wname] preds)
        (doseq [[model ps] preds, [q p y unit] ps]
          (.write preds-out (str kind "\t" (clojure.core/name wname) "\t" (clojure.core/name model) "\t" q "\t" unit "\t" p "\t" y "\n")))
        (.flush preds-out)
        (say)
        (say (format "%s %s (to %s)  last priors: %s" kind (clojure.core/name wname) (st/month-str (+ 2 (* 3 last-q)))
                     (str/join " " (for [m (rest models)] (str (clojure.core/name m) "=" (long (:prior (runs m))))))))
        (doseq [[era lo hi hard] [["tune" 2000 2007] ["2008+" 2008 9999] ["08-14" 2008 2014] ["2015+" 2015 9999]
                                  ["hard" 2008 9999 true]]]
          (let [in-era (fn [ps] (cond->> (filter #(some? (nth % 2)) (in-years ps lo hi))
                                  hard (filter (fn [[q _ _ unit]] (hard-row? (get onsets unit (sorted-set)) q)))))]
            (doseq [model shown
                    :let [s (scores (in-era (preds model)) (in-era base))]
                    :when (pos? (:n s))]
              (say (format "  %-5s %-9s n=%6d pos=%4d  auc %s [%s %s]  auq %s  ap %s  r@10%% %s  bss %s  U(.9) %s U(.95) %s"
                           era (clojure.core/name model) (:n s) (:pos s) (f3 (:auc s))
                           (f3 (first (:ci s))) (f3 (second (:ci s))) (f3 (:auq s)) (f3 (:ap s))
                           (f3 (:r10 s)) (f3 (:bss s)) (f3 (:u50 s)) (f3 (:u80 s)))))))))
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
    (.close preds-out)
    (spit (str dir "/onsets.txt") (str out))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
