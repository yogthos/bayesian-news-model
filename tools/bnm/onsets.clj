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
  (exchange-rate regime and anchor currency), calm and volcalm (long calm and
  breaks in event streams and markets), histories (the country's past onsets
  of every kind), neighbours (onsets of every kind in its subregion and
  region, recent and decayed), waves (onsets of every kind in the subregion,
  region and world decayed at a season, a year and four years, so the fit
  can learn contagion and a refractory period), sync (each channel's
  network biomarker in the subregion and the world's one-factor share),
  departure (a country's channels against their own past), breaks (online
  changepoints in each channel, bnm.changepoint), leaders (V-Dem: the military in the executive,
  the election cycle, how recently the regime type changed); see bnm.named
  and bnm.onset.
  Every group also runs as a placebo,
  <group>-placebo, its values shuffled across countries within each quarter:
  a group whose gain the placebo matches carries nothing about the country.

  Each model's prior precision is chosen from a grid each year by its log
  score on the forecasts resolved before the year (bnm.onset/choose-by-year),
  a forecast resolving only once its window has closed and the kind's
  reporting lag has passed (bnm.onset/resolves-at), as for every fit;
  scored from 2000; 2008 on is held out.
  Catalogues are scored only as far as data/slow/coverage.tsv says they have
  looked, so the last rows are not all positives.

    jolt -A:tools -M -m bnm.onsets [--name onsets] [--from 1985] [--rows <run>]
                                   [--kinds a,b] [--models base,history,...] [--groups mix,ties]
                                   [--events <training run with rows/, for the event model's state>]
                                   [--delay n] [--threads n]

  --delay n gives every input group's values from n quarters before each
  row: a gain that survives the delay cannot be same-quarter leakage.
  --threads n fits the models and priors of a kind on n threads (1); the
  forecasts are the same.

  A model named gbt-<inputs> is boosted trees (bnm.trees) on the inputs of
  <inputs>, its rounds chosen each year like the priors. One named
  at-<inputs> is the logit on those inputs as at-risk flags: each above its
  80th or below its 20th percentile over the rows known by the year
  (bnm.onset/tail-flags). One named knn-<inputs> is the analog forecast:
  the onset share among the hundred most similar resolved rows (bnm.analog);
  hs-<inputs> the logit with horseshoe-like shrinkage (lgt/fit-shrunk);
  mt-<inputs> the logit fit on the kind's rows and the other political
  kinds' rows, weighted by a share chosen each year, borrowing strength
  across kinds; hier-<inputs> the logit with nested random intercepts for
  region, subregion and country (augur.hier/intercepts), their variances
  estimated each year, so a country with few onsets is pulled toward its
  subregion and a subregion toward its region rather than all toward the
  world (bnm.dynamic adds the dynamic states over the same tree).

  Writes data/runs/<name>/onsets.txt, the out-of-sample predictions,
  preds.tsv (kind window model quarter unit p outcome), and the recorded
  rows, rows.tsv, which --rows reads back instead of running the slow layer."
  (:require [augur.hier :as hier]
            [augur.logit :as lgt]
            [augur.pool :as pl]
            [augur.score :as sc]
            [augur.analog :as analog]
            [bnm.calm :as calm]
            [bnm.geo :as geo]
            [bnm.mix :as mix]
            [bnm.named :as nm]
            [bnm.onset :as o]
            [bnm.slow :as slow]
            [bnm.slow-train :as st]
            [bnm.ties :as ties]
            [augur.trees :as trees]
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

(defn peers
  "{:region {unit [units]} :subregion {unit [units]}} over `units`, from the
  geography's M49 subregions and regions."
  [units]
  (let [sub (into {} (for [u units :let [s (first (geo/parents u))] :when s] [u s]))
        reg (into {} (for [[u s] sub] [u (first (geo/parents s))]))
        by (fn [m] (let [g (group-by m (keys m))] (into {} (for [[u k] m] [u (vec (remove #{u} (g k)))]))))]
    {:region (by reg) :subregion (by sub)}))

(defn unit-groups
  "{:region {unit region} :subregion {unit subregion}} over `units`."
  [units]
  (let [sub (into {} (for [u units :let [s (first (geo/parents u))] :when s] [u s]))]
    {:region (into {} (for [[u s] sub] [u (first (geo/parents s))])) :subregion sub}))

(def wave-taus
  "Decay rates (quarters) of the wave kernels: a season, a year, four years."
  [1.0 4.0 16.0])

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
   "leaders" (named-group nm/leaders nm/leaders-names :leaders)
   "tot" (named-group nm/terms-of-trade nm/tot-names :terms-of-trade)
   "fx" (named-group nm/fx nm/fx-names :fx)
   "calm" [(count calm/labels) (fn [_] (nm/load-event-calm mix-path))]
   "volcalm" (named-group nm/market-calm nm/market-calm-names :market-calm)
   "neighbours" [(* 2 (count history-kinds))
                 (fn [{:keys [events rows units]}]
                   (let [ps (peers units)]
                     (into {} (for [{:keys [unit q]} rows] [[unit q] (o/neighbourhood events history-kinds ps unit q)]))))]
   "waves" [(* 3 (count wave-taus))
            (fn [{:keys [events rows units]}]
              (let [groups (unit-groups units)
                    index (o/wave-index events history-kinds groups)]
                (into {} (for [{:keys [unit q]} rows] [[unit q] (o/waves index groups unit q wave-taus)]))))]
   "sync" [(count nm/sync-names) (fn [_] (nm/load-event-sync mix-path))
           ;; the placebo keeps which parts are present
           (fn [^doubles x] (vec (take-last 2 x)))]
   "departure" [(count nm/departure-names) (fn [_] (nm/load-event-departure mix-path))]
   "breaks" [(count nm/break-names) (fn [_] (nm/load-event-breaks mix-path))]
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
  (vec (concat (when event-run
                 (let [xs (cond->> (nm/load-event-state event-run)
                            (pos? (:delay ctx 0)) (into {} (map (fn [[[u q] x]] [[u (+ q (:delay ctx))] x]))))]
                   [["event" (count nm/event-labels) xs]
                    ;; shuffled among rows with the same presence (its last column)
                    ["event-placebo" (count nm/event-labels)
                     (ties/placebo xs 1 #(aget ^doubles % (dec (count nm/event-labels))))]]))
               (for [g names
                     :let [[width load stratum] (or (group-loaders g) (throw (ex-info (str "unknown group " g) {})))
                           ;; --delay n: each row sees the group as it was n quarters
                           ;; before, so a gain that survives is not same-quarter leakage
                           delayed (if (pos? (:delay ctx 0))
                                     (into {} (for [[[u q] x] (load ctx)] [[u (+ q (:delay ctx))] x]))
                                     (load ctx))
                           width0 width
                           [width xs] (o/with-presence width delayed (:rows ctx))
                           ;; strata read the group's own columns, not the
                           ;; presence column with-presence may append
                           strat (if stratum
                                   (fn [^doubles x] (stratum (java.util.Arrays/copyOf x (int width0))))
                                   (constantly nil))]
                     entry [[g width xs] [(str g "-placebo") width (ties/placebo xs 1 strat)]]]
                 entry))))

(def cases
  "Named cases, shown quarter by quarter before their onset. Illustration only:
  the scores pool every onset in the catalogues."
  [["protest_surge" "TUN" "2011-01"] ["protest_surge" "EGY" "2011-01"] ["protest_surge" "UKR" "2014-02"]
   ["protest_surge" "MMR" "2021-02"] ["protest_surge" "UKR" "2022-02"] ["conflict_onset" "UKR" "2022-02"]
   ["violence_surge" "SYR" "2011-04"] ["violence_surge" "LBY" "2011-02"] ["market_crash" "USA" "2008-10"]
   ["market_crash" "DEU" "2001-07"] ["banking_crisis" "GBR" "2007-08"] ["banking_crisis" "GRC" "2010-05"]])

(defn read-event-sets
  "data/slow/events.tsv as bnm.onset/event-sets: each onset's earliest
  quarter, every quarter it could be, and the quarter it became known."
  [path]
  (let [q #(quot (st/month-of %) 3)]
    (with-open [r (io/reader path)]
      (o/event-sets (for [line (line-seq r)
                          :let [[kind unit date _ latest known] (str/split line #"\t" -1)]]
                      [kind unit (q date) (q (or latest date)) (q (or known date))])))))

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
  outcomes are known by then: a linear pool and a log-linear stack, and
  members weighted by their own recent record (pl/hedge-weights, a
  three-year half-life). pool stops EM after a fixed number of steps from
  equal weights, which shrinks toward them; mlpool is the same pool at the
  maximum of its log score (pl/pool-weights-ml); median, untrained, the
  members' median log odds (pl/median-pool)."
  [:pool :mlpool :stack :hedge :median])

(def hedge-discount 0.8)

(def priors
  "Prior precisions tried, on the scale of each input's standard deviation."
  [10.0 100.0 1000.0])

(def tune-years [2000 2007])

(def tail-share
  "The share of rows in each tail an at-<inputs> model flags."
  0.2)

(def mt-weights
  "Weights tried for the other kinds' rows in mt-<inputs> models, chosen
  each year like the priors."
  [0.1 0.3 1.0])

(def political-kinds
  "The kinds that share the political inputs, which mt- models pool."
  #{"conflict_onset" "war_onset" "coup" "protest_surge" "violence_surge"})

(def hier-opts
  "Settings of the hier-<inputs> models, set in advance: the starting
  variances of the region, subregion and country intercepts and the
  starting prior precision of the coefficients (each year's start is the
  year before's estimate), the backfitting sweeps per round, the most
  rounds alternating the coefficients and the intercepts, and the scales of the
  half-normal priors on each level's sd (1 on the log odds, the weakly
  informative default of Gelman 2006)."
  {:tau2 [0.25 0.25 0.25] :prior 100.0 :sweeps 8 :rounds 10 :sd-prior [1.0 1.0 1.0]})

(defn hier-path
  "[region subregion unit] for a country, from the M49 tree."
  [unit]
  (let [s (first (geo/parents unit))]
    (if s [(first (geo/parents s)) s unit] [unit])))

(defn- dot ^double [^doubles beta ^doubles x]
  (loop [i 0 acc 0.0] (if (< i (alength beta)) (recur (inc i) (+ acc (* (aget beta i) (aget x i)))) acc)))

(defn country-means
  "The between part of the hier- models' inputs (Mundlak 1978; Bafumi &
  Gelman 2006; Bell & Jones 2015): each unit's mean of the inputs from
  column `from` on, over its rows ({:unit :q :x}) before quarter `cut`, and
  :all, the mean over every such row, for units with none. With them as
  inputs the slow covariates keep their between-country signal instead of
  the country intercepts absorbing it. {:by {unit means} :all means}."
  [rows from cut]
  (let [before (filter #(< (:q %) cut) rows)
        width (fn [^doubles x] (- (alength x) from))
        mean-of (fn [rs]
                  (when (seq rs)
                    (let [w (width (:x (first rs)))
                          acc (double-array w)]
                      (doseq [{:keys [^doubles x]} rs] (dotimes [j w] (aset acc j (+ (aget acc j) (aget x (+ from j))))))
                      (dotimes [j w] (aset acc j (/ (aget acc j) (count rs))))
                      acc)))]
    {:by (into {} (for [[u rs] (group-by :unit before)] [u (mean-of rs)]))
     :all (mean-of before)}))

(defn with-means
  "A function of a unit and its inputs giving the inputs followed by the
  unit's means from `country-means` (:all's for a unit it lacks)."
  [{:keys [by all]}]
  (fn [unit ^doubles x]
    (let [^doubles m (get by unit all)
          out (double-array (+ (alength x) (alength m)))]
      (System/arraycopy x 0 out 0 (alength x))
      (System/arraycopy m 0 out (alength x) (alength m))
      out)))

(defn fit-hier
  "Coefficients, their prior precision and nested intercepts for `train`
  ({:x :y :w :path}), alternating: the coefficients with the intercepts as
  offsets, then the intercepts with the coefficients' log odds as offsets.
  The ridge and the intercepts' variances compete for the same signal, so
  they are estimated together, each round one Laplace EM step for each
  (Wood 2011), rather than the ridge picked from a grid: picked on log
  score, the ridge went to the grid's strongest and the intercepts took
  over what the covariates carried. Up to :rounds rounds, fewer once the
  precision moves by under 1e-3 in log and the variances by under 1e-3. Warm from
  `start` ({:beta :fx :prior}). {:beta :fx :prior :rounds}."
  [train k {:keys [beta fx prior]} {:keys [tau2 sweeps rounds sd-prior] start-prior :prior}]
  (let [train (vec train)
        settled? (fn [p p' t t'] (and t (< (Math/abs (- (Math/log p') (Math/log p))) 1e-3)
                                      ;; absolute for the variances: one with no signal
                                      ;; drifts toward zero by a constant factor
                                      (every? #(< (Math/abs %) 1e-3) (map - t' t))))]
    (loop [r 0 beta beta fx fx prior (double (or prior start-prior))]
      (let [beta (lgt/fit (map #(assoc % :o (if fx (hier/effect fx (:path %)) 0.0)) train) k {:prior prior :start beta})
            fx' (hier/intercepts (map #(assoc % :o (dot beta (:x %))) train)
                                 {:tau2 (or (:tau2 fx) tau2) :sweeps sweeps :sd-prior sd-prior :start (:effects fx)})
            prior' (lgt/evidence-step (map #(assoc % :o (hier/effect fx' (:path %))) train) k beta prior)
            r (inc r)]
        (if (or (>= r rounds) (settled? prior prior' (:tau2 fx) (:tau2 fx')))
          {:beta beta :fx fx' :prior prior' :rounds r}
          (recur r beta fx' prior'))))))

(def analog-opts
  "Neighbours and shrinkage of the knn-<inputs> models, set in advance: more
  neighbours than weather's twenty since onsets are rare."
  {:k 100 :prior 10.0})

(def tree-rounds
  "Boosting rounds tried for the trees (gbt-<inputs> models), chosen each
  year like the priors."
  [25 50 100])

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

(defn checked
  "p, or an error naming `where` if it is not a finite number: a fit that
  diverged stops the run at once rather than poisoning the scores."
  [p where]
  (let [p (double p)]
    (if (or (Double/isNaN p) (Double/isInfinite p))
      (throw (ex-info (str "non-finite forecast " p " " (pr-str where)) where))
      p)))

(defn lagged
  "The window whose end is when a row's label is final: its own end plus the
  kind's reporting lag (bnm.onset/resolves-at), for the checks of what was
  resolved by a year's start (priors, rounds, combinations)."
  [[a b] lag]
  [a (+ b lag)])

(defn in-parallel
  "The results of `thunks`, in order, run on up to `n` threads at once: each
  worker takes the next thunk not yet started."
  [n thunks]
  (let [ts (vec thunks)
        out (object-array (count ts))
        next-i (atom -1)
        work (fn [] (loop []
                      (let [i (swap! next-i inc)]
                        (when (< i (count ts))
                          (aset out i ((ts i)))
                          (recur)))))]
    (if (<= n 1)
      (work)
      (run! deref (doall (for [_ (range (min n (count ts)))] (future (work))))))
    (vec out)))

(defn evaluate-kind
  "Out-of-sample predictions for one kind and window: a function of the
  feature sets giving {model {:prior p :preds [[q p y unit]]}}, the prior
  chosen per model and year by log score on the years before. The fits of
  each model and prior are independent and run on up to `threads` threads
  (1 when nil)."
  [rows {onsets-by-unit :onsets spans :spans known :known lag :lags} units window score-from last-q models fam groups
   & [aux threads]]
  (let [labels-q (fn [{:keys [unit q]}] (o/label (get onsets-by-unit unit (sorted-set)) q window))
        ;; rows past the catalogue's coverage are predicted too, with no label:
        ;; they are the live forecasts (bnm.readings), never fit or scored
        usable (filterv (fn [{:keys [unit q]}]
                          (and (units unit)
                               (o/at-risk? (get spans unit (sorted-set)) q window)))
                        rows)
        ;; a row's inputs: the constant, the kind's history [h1 h2], the
        ;; inputs, the named inputs and the groups'
        full-of (fn [{:keys [unit q ^doubles x] :as r} [h1 h2]]
                  (let [^doubles nx (get-in r [:named fam])
                        ;; a group's inputs, zeros where it has none for the row
                        gs (mapv (fn [[_ width xs]] (or (get xs [unit q]) (double-array width))) groups)
                        full (double-array (+ 3 (alength x) (alength nx) (reduce + (map second groups))))]
                    (aset full 0 1.0) (aset full 1 h1) (aset full 2 h2)
                    (System/arraycopy x 0 full 3 (alength x))
                    (System/arraycopy nx 0 full (+ 3 (alength x)) (alength nx))
                    (reduce (fn [off ^doubles g] (System/arraycopy g 0 full off (alength g)) (+ off (alength g)))
                            (+ 3 (alength x) (alength nx)) gs)
                    full))
        prepared (mapv (fn [{:keys [unit q] :as r}]
                         (assoc r :full (full-of r (o/history-features (get known unit (sorted-set)) q))
                                  :y (when (<= (+ q (second window)) last-q) (labels-q r))))
                       usable)
        ;; the other kinds' rows on the same inputs, for the mt- models; only
        ;; built when one is run
        aux-prepared (delay (vec (o/other-kind-rows rows units window aux full-of)))
        years (range (quot (apply min (map :q prepared)) 4) (inc (quot (apply max (map :q prepared)) 4)))]
    (fn [sets]
      (let [;; at-risk cuts depend on the inputs and the year, not the prior
            cuts-for (memoize (fn [idx q0] (o/tail-cuts (for [{:keys [q full]} prepared
                                                              :when (<= (+ q (second window)) q0)]
                                                          (pick full idx))
                                                        3 tail-share)))
            run-model
            (fn [idx prior flags? shrunk? hier?]
              ;; flags?: inputs past the constant and history as at-risk
              ;; flags (o/tail-flags), their cuts from the rows known each year;
              ;; hier?: the inputs then each country's means of those past the
              ;; constant and history (country-means), and the prior estimated
              ;; with the intercepts' variances (fit-hier), `prior` its start
              (let [k (cond flags? (+ 3 (* 2 (- (count idx) 3)))
                            hier? (- (* 2 (count idx)) 3)
                            :else (count idx))]
                (loop [[yr & more] (filter #(>= % (+ 10 (first years))) years)
                       beta nil
                       fx nil
                       prior prior
                       preds []]
                  (if-not yr
                    (with-meta preds {:prior prior})
                    (let [q0 (* 4 yr)
                          known (filter #(<= (o/resolves-at (:q %) window (or lag 0)) q0) prepared)
                          xf (cond
                               flags? (let [cuts (cuts-for idx q0)]
                                        (fn [_ full] (o/tail-flags (pick full idx) 3 cuts)))
                               hier? (let [ext (with-means (country-means (for [{:keys [unit q full]} prepared :when (< q q0)]
                                                                            {:unit unit :q q :x (pick full idx)})
                                                                          3 q0))]
                                       (fn [unit full] (ext unit (pick full idx))))
                               :else (fn [_ full] (pick full idx)))
                          train (for [{:keys [unit q y full]} known
                                      :when (and (some? y) (or y (keep-negative? unit q)))]
                                  {:x (xf unit full) :y y :q q :unit unit :path (when hier? (hier-path unit))
                                   :w (* (Math/pow 0.95 (/ (- q0 q) 4.0)) (if y 1.0 (/ 100.0 15.0)))})
                          [beta fx prior] (cond
                                            (not (some :y train)) [beta fx prior]
                                            hier? (let [f (fit-hier train k {:beta beta :fx fx :prior prior} hier-opts)]
                                                    [(:beta f) (:fx f) (:prior f)])
                                            shrunk? [(lgt/fit-shrunk train k {:prior prior :start beta}) fx prior]
                                            :else [(lgt/fit train k {:prior prior :start beta}) fx prior])
                          scored (for [{:keys [unit q y full]} prepared
                                       :when (and beta (= (quot q 4) yr) (>= yr score-from))]
                                   [q (checked (lgt/predict beta (xf unit full) (if fx (hier/effect fx (hier-path unit)) 0.0))
                                             {:year yr :prior prior :unit unit})
                                    y unit])]
                      (recur more beta fx prior (into preds scored)))))))
            ;; multi-task (mt-): the kind's rows and the other kinds' rows
            ;; weighted by lam, an extra column marking the others so the
            ;; kind keeps its own intercept; the coefficients are shared, so
            ;; lam pulls them toward what the kinds have in common
            run-mt
            (fn [idx lam]
              (let [k (inc (count idx))
                    x+ (fn [full other?] (let [x (pick full idx) out (double-array k)]
                                           (System/arraycopy x 0 out 0 (alength x))
                                           (aset out (dec k) (if other? 1.0 0.0))
                                           out))]
                (loop [[yr & more] (filter #(>= % (+ 10 (first years))) years)
                       beta nil
                       preds []]
                  (if-not yr
                    preds
                    (let [q0 (* 4 yr)
                          w (fn [q y] (* (Math/pow 0.95 (/ (- q0 q) 4.0)) (if y 1.0 (/ 100.0 15.0))))
                          own (for [{:keys [unit q y full]} prepared
                                    :when (and (<= (o/resolves-at q window (or lag 0)) q0) (some? y) (or y (keep-negative? unit q)))]
                                {:x (x+ full false) :y y :w (w q y)})
                          others (for [{:keys [unit q y full] alag :lag} @aux-prepared
                                       :when (and (<= (o/resolves-at q window alag) q0) (some? y) (or y (keep-negative? unit q)))]
                                   {:x (x+ full true) :y y :w (* lam (w q y))})
                          train (concat own others)
                          beta (if (some :y own) (lgt/fit train k {:prior 100.0 :start beta}) beta)
                          scored (for [{:keys [unit q y full]} prepared
                                       :when (and beta (= (quot q 4) yr) (>= yr score-from))]
                                   [q (lgt/predict beta (x+ full false)) y unit])]
                      (recur more beta (into preds scored)))))))
            ;; analogs (bnm.analog): fit on the resolved rows each scored
            ;; year, negatives thinned to a third of the usual sample
            run-analog
            (fn [idx]
              (vec (mapcat
                    (fn [yr]
                      (let [q0 (* 4 yr)
                            train (vec (for [{:keys [unit q y full]} prepared
                                             :when (and (<= (o/resolves-at q window (or lag 0)) q0) (some? y)
                                                        (or y (and (keep-negative? unit q) (zero? (mod (hash [q unit]) 3)))))]
                                         {:x (pick full idx) :y y
                                          :w (* (Math/pow 0.95 (/ (- q0 q) 4.0)) (if y 1.0 (/ 100.0 5.0)))}))]
                        (when (some :y train)
                          (let [mdl (analog/fit train analog-opts)]
                            (for [{:keys [unit q y full]} prepared :when (= (quot q 4) yr)]
                              [q (analog/predict mdl (pick full idx)) y unit])))))
                    (filter #(>= % (max score-from (+ 10 (first years)))) years))))
            ;; boosted trees (bnm.trees), one fit a year at the most rounds:
            ;; fewer rounds are its first trees, so each count is a run
            run-trees
            (fn [idx]
              ;; not warm-started, so no fit is needed before the scored years
              (loop [[yr & more] (filter #(>= % (max score-from (+ 10 (first years)))) years)
                     model nil
                     preds (zipmap tree-rounds (repeat []))]
                (if-not yr
                  (vec (for [r tree-rounds] {:prior (double r) :preds (preds r)}))
                  (let [q0 (* 4 yr)
                        train (vec (for [{:keys [unit q y full]} prepared
                                         :when (and (<= (o/resolves-at q window (or lag 0)) q0) (some? y) (or y (keep-negative? unit q)))]
                                     {:x (pick full idx) :y y
                                      :w (* (Math/pow 0.95 (/ (- q0 q) 4.0)) (if y 1.0 (/ 100.0 15.0)))}))
                        model (if (some :y train) (trees/fit train (assoc trees/defaults :rounds (apply max tree-rounds))) model)
                        this (when model (filterv #(and (= (quot (:q %) 4) yr) (>= yr score-from)) prepared))
                        cut (fn [r] (update model :trees #(subvec % 0 (min r (count %)))))]
                    (recur more model
                           (reduce (fn [acc r]
                                     (let [m (cut r)]
                                       (update acc r into (for [{:keys [unit q y full]} this]
                                                            [q (trees/predict m (pick full idx)) y unit]))))
                                   preds tree-rounds))))))]
        (let [specs (for [model models
                          :let [tree? (str/starts-with? (name model) "gbt-")
                                flags? (str/starts-with? (name model) "at-")
                                knn? (str/starts-with? (name model) "knn-")
                                shrunk? (str/starts-with? (name model) "hs-")
                                mt? (str/starts-with? (name model) "mt-")
                                hier? (str/starts-with? (name model) "hier-")
                                inputs (cond tree? (keyword (subs (name model) 4))
                                             flags? (keyword (subs (name model) 3))
                                             knn? (keyword (subs (name model) 4))
                                             shrunk? (keyword (subs (name model) 3))
                                             mt? (keyword (subs (name model) 3))
                                             hier? (keyword (subs (name model) 5))
                                             :else model)
                                idx (or (sets inputs)
                                        (throw (ex-info (str "no inputs for model " (name model)) {:model model})))]]
                      ;; each model's runs as independent tasks, one per prior
                      ;; (or rounds, or weight), each giving a vector of runs
                      {:model model
                       :default (cond tree? 50.0 knn? (double (:k analog-opts)) mt? 0.3 :else 100.0)
                       :tasks (cond
                                tree? [#(run-trees idx)]
                                knn? [#(vector {:prior (double (:k analog-opts)) :preds (run-analog idx)})]
                                mt? (vec (for [lam mt-weights] #(vector {:prior lam :preds (run-mt idx lam)})))
                                ;; a hier- model's prior is estimated, so one run, its
                                ;; :prior the last year's estimate
                                hier? [#(let [ps (run-model idx (:prior hier-opts) false false true)]
                                          (vector {:prior (:prior (meta ps)) :preds ps}))]
                                :else (vec (for [p (if (= model :base) [1.0] priors)]
                                             #(vector {:prior p :preds (run-model idx p flags? shrunk? hier?)}))))})
              results (in-parallel (or threads 1) (mapcat :tasks specs))
              by-model (loop [[spec & more] specs rs results acc {}]
                         (if-not spec
                           acc
                           (let [n (count (:tasks spec))]
                             (recur more (drop n rs) (assoc acc (:model spec) (vec (apply concat (take n rs))))))))]
          (into {}
                (for [{:keys [model default]} specs
                      :let [runs (by-model model)]]
                  ;; each year's prior (or rounds) chosen on the years before
                  ;; it, so no scored year has picked its own
                  [model (o/choose-by-year runs log-score (lagged window (or lag 0)) (or (first (filter #(= default (:prior %)) runs)) (first runs)))])))))))

(declare aligned combine-rows)

(defn combine
  "Adds the combinations (`combined`) to `runs`, combining every model but
  :base and the placebos. Each year's combination is fit on the earlier
  forecasts whose outcomes were known by the year's start, discounted like
  the hazards, and scored on the year. `only`, a subset of `combined`, skips
  the others (they are given empty)."
  ([runs window] (combine runs window (set combined)))
  ([runs window only]
   (let [[_ rows] (aligned runs)]
     (merge runs (combine-rows rows window only)))))

(defn aligned
  "[members rows] of `runs`: the members (all but :base, the combinations
  and placebos) and, for each row every member forecast, {:unit :q :y :ps}
  with :ps in member order. With `partial?`, every row any member forecast,
  missing forecasts nil (for sub-pools to take the rows their own members
  share)."
  ([runs] (aligned runs false))
  ([runs partial?]
   (let [members (vec (remove #(or (= :base %) ((set combined) %) (str/ends-with? (name %) "-placebo")) (sort (keys runs))))
         by-key (into {} (for [m members, [q p y unit] (:preds (runs m))] [[m unit q] p]))
         keys* (sort-by second (distinct (for [m members [q _ y unit] (:preds (runs m))] [unit q y])))]
     [members
      (vec (for [[unit q y] keys*
                 :let [ps (mapv #(by-key [% unit q]) members)]
                 :when (if partial? (some some? ps) (every? some? ps))]
             {:unit unit :q q :y y :ps ps}))])))

(defn combine-rows
  "`combine` on rows already `aligned`: {combination {:preds}}."
  [rows window only]
  (let [years (sort (distinct (map #(quot (:q %) 4) rows)))
         ;; each combination: from the known rows, their discounts and the
         ;; year's start, a function of a row's member probabilities
         fits {:pool (fn [ps ys ws _] (let [w (pl/pool-weights ps ys ws)] #(pl/pool w %)))
               :mlpool (fn [ps ys ws _] (let [w (pl/pool-weights-ml ps ys ws)] #(pl/pool w %)))
               :stack (fn [ps ys ws _] (let [b (pl/fit-stack ps ys ws {:prior 10.0})] #(pl/predict-stack b %)))
               :median (fn [_ _ _ _] pl/median-pool)
               :hedge (fn [ps ys _ qs] (let [h (pl/hedge-weights ps ys (map #(Math/pow hedge-discount (/ % 4.0)) qs) 1.0)]
                                         #(pl/pool h %)))}
         out (loop [[yr & more] years out {}]
               (if-not yr
                 out
                 (let [q0 (* 4 yr)
                       known (filterv #(and (some? (:y %)) (<= (+ (:q %) (second window)) q0)) rows)
                       this (filter #(= yr (quot (:q %) 4)) rows)]
                   (if (< (count (filter :y known)) 10)
                     (recur more out)
                     (let [ps (map :ps known) ys (map :y known)
                           ages (map #(- q0 (:q %)) known)
                           ws (map #(Math/pow 0.95 (/ % 4.0)) ages)]
                       (recur more
                              (reduce (fn [out c]
                                        (let [f ((fits c) ps ys ws ages)]
                                          (update out c (fnil into []) (for [{:keys [unit q y ps]} this] [q (f ps) y unit]))))
                                      out (filter only combined))))))))]
     (into {} (for [c combined] [c {:preds (get out c [])}]))))

(defn- scores [preds base]
  (let [ps (map second preds) ys (map #(nth % 2) preds)]
    {:n (count preds) :pos (count (filter true? ys))
     :auc (sc/auc ps ys) :auq (sc/auc-within (map (fn [[q p y]] [q p y]) preds))
     :ap (sc/avg-precision ps ys)
     :r10 (sc/recall-at-budget (map (fn [[q p y]] [q p y]) preds) 0.10)
     ;; 90% interval resampling whole years; Sarlin's usefulness for a policymaker weighing
     ;; missed onsets by 0.9 and by 0.95 (for events this rare, a balanced policymaker never acts)
     :ci (when (and (some #(true? (nth % 2)) preds) (some #(false? (nth % 2)) preds))
           (sc/block-bootstrap-auc (map (fn [[q p y]] [(quot q 4) p y]) preds) 200 1))
     :u50 (when (some true? ys) (sc/usefulness ps ys 0.9))
     :u80 (when (some true? ys) (sc/usefulness ps ys 0.95))
     :bss (when (seq base)
            (let [bb (sc/brier (map second base) (map #(nth % 2) base))]
              (when (pos? bb) (- 1.0 (/ (sc/brier ps ys) bb)))))}))

(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))

(defn run [{:keys [name from rows kinds models groups delay threads] event-run :events}]
  (let [run-name (or name "onsets")
        from (parse-long (or from "1985"))
        score-from (first tune-years)
        dir (str "data/runs/" run-name)
        _ (io/make-parents (str dir "/x"))
        event-sets (read-event-sets "data/slow/events.tsv")
        events (:onsets event-sets)
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
        _ (when groups
            (when-let [unused (seq (o/unused-groups models (str/split groups #",")))]
              (throw (ex-info (str "no model in --models uses group " (str/join ", " unused)
                                   " (name it: g, g-placebo, named+g, gbt-named+g, ...)") {:groups unused}))))
        groups (load-groups (if groups (str/split groups #",") ["mix"]) event-run {:db db :units all-units :rows rows :events (:known event-sets)
                                                                                      :delay (or (some-> delay parse-long) 0)})
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
            aux (when (and (political-kinds kind) (some #(str/starts-with? (clojure.core/name %) "mt-") models))
                  (for [k (sort political-kinds) :when (not= k kind)]
                    {:kind k :sets (update-vals event-sets #(get % k)) :last-q (coverage-q k)}))
            runs (combine ((evaluate-kind rows (update-vals event-sets #(get % kind)) units window score-from last-q models fam groups aux
                                          (some-> threads parse-long))
                           (feature-sets labels (n-named fam) n-polecon groups))
                          (lagged window (get-in event-sets [:lags kind] 0)))
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
