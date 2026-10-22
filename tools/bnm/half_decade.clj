(ns bnm.half-decade
  "Trial T5 of bnm-3qu (bead bnm-3qu.6, docs/T5-ERAS.md): do slow
  financial stocks predict banking crises at the half-decade, on the long
  JST sample (18 countries, 1870-2020), with the eras rotated?

  Outcome: a JST systemic banking crisis (crisisJST) starting in years
  t + 1 .. t + 5, at the end of year t; not at risk while a crisis is
  under way (an onset in t - 2 .. t). Lead profile: an onset in year t + h
  exactly, h = 1..5, on the same rows.

  States, read at the end of year t from data years up to t - 1 (a data
  year is out at the end of the next, as bnm.keen-run reads JST):
    credit-gap       loans to GDP less its one-sided HP trend
                     (bnm.credit-run/gap-at on JST's series alone), pp;
    house            three-year growth of real house prices (log);
    equity           three-year real equity price growth (log capital
                     gains less CPI inflation);
    current-account  the current account over GDP, mean of three years;
    debt-service     a debt service proxy, loans/GDP x i / (1 - (1+i)^-15)
                     with i the long rate, less its mean over the twenty
                     years before (Drehmann & Juselius 2014's deviation);
    credit-x-house   both booming: the product of the positive parts of the
                     standardized credit gap and house growth (Greenwood et
                     al. 2022's zone), with both main effects.
  Each state is standardized on the training rows and clipped at 3 SD
  (the German hyperinflation is 5 SD out); a missing state reads 0.

  Base, known at the end of t: the country's own crisis rate to t - 1
  (shrunk to 3% a year with ten years' weight), log years since its last
  crisis, the world's crisis rate in the current era so far (an era's
  world rate, as known), and the share of countries with a crisis in the
  last five years. No year effects: they cannot be scored out of era.

  Eras: development pre-WWI 1870-1913, interwar 1919-1938, Bretton Woods
  1946-1971, 1972-1995; the registered test era 1996-2020. In development
  every crisis flag from the test era's first year on is removed when the
  data are read (mask-outcomes), and only origins whose window ends before
  it are built (check-dev! refuses any other), so no 1996-2020 outcome is
  read. Leave one era out among the development eras; a training row whose
  window reaches the held-out era's outcome years is dropped.

  Scores per row: log score and Brier, gains of the states' model over
  the base (or, for the interaction, over its main effects), with 90%
  intervals from a moving-block bootstrap over origin years; permutation
  nulls of whole country series; a placebo, the next country's state.

    jolt -A:tools -M -m bnm.half-decade [--eras pre-WWI:1870-1913,...] [--test-era 1996-2020]
      [--draws 200] [--boot 1000] [--block 5] [--seed 1] [--prior 1.0] [--power 1,1.2,1.35,1.5,2]
      [--power-draws 100] [--out data/runs/sequencing/t5-dev]
    jolt -A:tools -M -m bnm.half-decade --mode test --confirm-registered yes [...]"
  (:require [augur.logit :as lg]
            [augur.math :as m]
            [bnm.credit-run :as cr]
            [bnm.macrohistory :as mh]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def H "The half-decade: the window's length and the longest lead." 5)

(def default-eras
  [{:name "pre-WWI" :from 1870 :to 1913}
   {:name "interwar" :from 1919 :to 1938}
   {:name "Bretton Woods" :from 1946 :to 1971}
   {:name "1972-1995" :from 1972 :to 1995}])

(def default-test-era {:name "1996-2020" :from 1996 :to 2020})

;; --- eras --------------------------------------------------------------------------

(defn parse-era
  "\"name:1870-1913\", or \"1870-1913\" named by its span."
  [s]
  (let [[nm span] (if (str/includes? s ":") (str/split s #":" 2) [s s])
        [a b] (str/split span #"-")]
    {:name nm :from (parse-long a) :to (parse-long b)}))

(defn parse-eras [s] (mapv parse-era (str/split s #",")))

(defn validate-eras!
  "Throws unless the development eras are ordered, disjoint, and end
  before the test era opens."
  [eras test-era]
  (doseq [{:keys [name from to]} (conj (vec eras) test-era)]
    (when (> from to) (throw (ex-info (str "era " name " ends before it starts") {}))))
  (doseq [[a b] (partition 2 1 eras)]
    (when (>= (:to a) (:from b)) (throw (ex-info (str "eras " (:name a) " and " (:name b) " overlap or are out of order") {}))))
  (when (some #(>= (:to %) (:from test-era)) eras)
    (throw (ex-info "a development era reaches the test era" {:test-era test-era})))
  nil)

(defn- era-of [eras t] (first (filter #(<= (:from %) t (:to %)) eras)))

;; --- the data, and what development may read ------------------------------------

(defn mask-outcomes
  "The JST rows with the crisis flag removed from `cutoff` on."
  [rows cutoff]
  (mapv #(if (>= (:year %) cutoff) (dissoc % :crisisJST) %) rows))

(defn countries
  "{iso {:by (sorted-map year row) :covered sorted-set :onsets sorted-set}}:
  the years with a crisis flag, and those with a crisis starting."
  [rows]
  (into (sorted-map)
        (for [[iso rs] (group-by :iso rows)]
          [iso {:by (into (sorted-map) (map (juxt :year identity)) rs)
                :covered (into (sorted-set) (keep #(when (some? (:crisisJST %)) (:year %))) rs)
                :onsets (into (sorted-set) (keep #(when (= 1.0 (some-> (:crisisJST %) double)) (:year %))) rs)}])))

(defn window-label
  "Whether the country has an onset in years t + lo .. t + hi; nil when any
  of them is not covered."
  [{:keys [covered onsets]} t lo hi]
  (let [ys (range (+ t lo) (+ t hi 1))]
    (when (every? covered ys)
      (boolean (some onsets ys)))))

(defn at-risk?
  "Covered at t and no onset in t - 2 .. t: a crisis under way is not a new one."
  [{:keys [covered onsets]} t]
  (and (contains? covered t) (not-any? onsets (range (- t 2) (inc t)))))

;; --- states ------------------------------------------------------------------------

(def state-names [:credit-gap :house :equity :current-account :debt-service])

(def signs
  "Each term's expected sign."
  {:credit-gap 1 :house 1 :equity 1 :current-account -1 :debt-service 1 :credit-x-house 1})

(defn- div [a b] (when (and a b (not (zero? (double b)))) (/ (double a) (double b))))
(defn- lg* [x] (when (and x (pos? (double x))) (Math/log (double x))))

(defn- credit-panel [by]
  {:d-jst (into (sorted-map) (for [[yr r] by :let [x (div (:tloans r) (:gdp r))] :when x] [yr [(inc yr) x]]))})

(defn- dsr [r]
  (when-let [d (div (:tloans r) (:gdp r))]
    (when-let [i (some-> (:ltrate r) double (/ 100.0))]
      (if (< (Math/abs i) 1e-6)
        (/ d 15.0)
        (* d (/ i (- 1.0 (Math/pow (+ 1.0 i) -15.0))))))))

(defn states-at
  "The raw states at the end of year t from data years up to t - 1 (nil
  where a state cannot be read)."
  [{:keys [by] :as c} t]
  (let [d (dec t)
        at (fn [yr f] (some-> (get by yr) f))
        real-house (fn [yr] (lg* (div (at yr :hpnom) (at yr :cpi))))
        infl (fn [yr] (when-let [a (lg* (at yr :cpi))] (when-let [b (lg* (at (dec yr) :cpi))] (- a b))))
        eq-real (fn [yr] (when-let [g (at yr :eq_capgain)] (when-let [p (infl yr)] (when (> g -1.0) (- (Math/log1p g) p)))))
        house (when-let [a (real-house d)] (when-let [b (real-house (- d 3))] (- a b)))
        equity (let [gs (map eq-real (range (- d 2) (inc d)))] (when (every? some? gs) (reduce + gs)))
        cas (keep #(div (at % :ca) (at % :gdp)) (range (- d 2) (inc d)))
        dsrs (fn [yrs] (keep #(some-> (get by %) dsr) yrs))
        ds-now (some-> (get by d) dsr)
        ds-past (dsrs (range (- d 20) d))]
    {:credit-gap (:gap (cr/gap-at (credit-panel (subseq by < t)) t {:sources [:d-jst] :source-min-years 20}))
     :house house
     :equity equity
     :current-account (when (>= (count cas) 2) (/ (reduce + cas) (count cas)))
     :debt-service (when (and ds-now (>= (count ds-past) 10)) (- ds-now (/ (reduce + ds-past) (count ds-past))))}))

;; --- the base: the country's history and the world's, as known ---------------------

(def ^:private r0 0.03)

(defn- world-features
  "{t [era-rate recent-share]} for every year t in an era."
  [cs eras]
  (let [years (for [e eras t (range (:from e) (inc (:to e)))] [t e])]
    (into {} (for [[t e] years]
               (let [cy (for [[_ c] cs y (range (:from e) t) :when (contains? (:covered c) y)] (if ((:onsets c) y) 1.0 0.0))
                     n (reduce + 0.0 cy) T (count cy)
                     recent (for [[_ c] cs :when (contains? (:covered c) (dec t))]
                              (if (some (:onsets c) (range (- t 5) t)) 1.0 0.0))]
                 [t [(Math/log (/ (+ n (* 18 r0)) (+ T 18.0)))
                     (if (seq recent) (/ (reduce + recent) (count recent)) 0.0)]])))))

(defn- base-at [{:keys [covered onsets]} t world]
  (let [past (subseq covered < t)
        n (count (filter onsets past))
        last-on (last (filter onsets past))
        first-y (or (first past) t)]
    (into [(Math/log (/ (+ n (* 10 r0)) (+ (count past) 10.0)))
           (Math/log (+ 1.0 (- t (or last-on first-y))))]
          world)))

;; --- rows ----------------------------------------------------------------------

(defn state-table
  "{[iso year] states} for every country-year in an era."
  [cs eras]
  (into {} (for [[iso c] cs t (keys (:by c)) :when (era-of eras t)] [[iso t] (states-at c t)])))

(defn- build-rows
  "Rows at origins in `eras` whose window (t, t + H] ends before `limit`."
  [cs eras limit]
  (let [world (world-features cs eras)]
    (vec (for [[iso c] cs
               t (keys (:by c))
               :let [era (era-of eras t)]
               :when (and era (< (+ t H) limit) (at-risk? c t))
               :let [y5 (window-label c t 1 H)
                     yh (mapv #(window-label c t % %) (range 1 (inc H)))]
               :when (and (some? y5) (every? some? yh))]
           {:iso iso :year t :era (:name era) :y5 y5 :yh yh
            :base (base-at c t (world t)) :states (states-at c t)}))))

(defn check-dev!
  "Throws if any row's window reaches the test era, or a row is in it."
  [rows test-era]
  (when-let [bad (first (filter #(or (>= (+ (:year %) H) (:from test-era)) (= (:era %) (:name test-era))) rows))]
    (throw (ex-info "a development row reads the test era" {:row (select-keys bad [:iso :year :era])})))
  rows)

(defn dev-rows
  "The development rows: the data masked from the test era's first year
  before anything is built."
  [jst {:keys [eras test-era]}]
  (validate-eras! eras test-era)
  (let [cs (countries (mask-outcomes jst (:from test-era)))]
    (check-dev! (build-rows cs eras (:from test-era)) test-era)))

(defn test-rows
  "{:train :score}: the development rows, and the test era's rows (window
  ending by its last year). Reads the test era: only with :confirm."
  [jst {:keys [eras test-era confirm]}]
  (when-not confirm
    (throw (ex-info "the test era is read only after registration: pass --confirm-registered yes" {})))
  (validate-eras! eras test-era)
  (let [cs (countries jst)
        rows (build-rows cs (conj (vec eras) test-era) (inc (:to test-era)))]
    {:train (check-dev! (filterv #(and (not= (:era %) (:name test-era)) (< (+ (:year %) H) (:from test-era))) rows) test-era)
     :score (filterv #(= (:era %) (:name test-era)) rows)}))

;; --- models ----------------------------------------------------------------------

(def term-models
  "The five registered terms, each against its own reference."
  [{:name "credit-gap" :terms [:credit-gap] :against []}
   {:name "house" :terms [:house] :against []}
   {:name "equity" :terms [:equity] :against []}
   {:name "current-account" :terms [:current-account] :against []}
   {:name "credit-x-house" :terms [:credit-gap :house :credit-x-house] :against [:credit-gap :house]}])

(def record-models [{:name "debt-service" :terms [:debt-service] :against []}])

(def joint
  {:name "joint" :terms [:credit-gap :house :equity :current-account :credit-x-house] :against []})

(defn- state-keys [terms]
  (distinct (mapcat #(if (= % :credit-x-house) [:credit-gap :house] [%]) terms)))

(defn- standardizer [rows names]
  (into {} (for [nm names
                 :let [vs (vec (keep #(get-in % [:states nm]) rows)) n (count vs)]]
             [nm (if (< n 2)
                   [0.0 1.0]
                   (let [mu (/ (reduce + vs) n)
                         sd (Math/sqrt (/ (reduce + (map #(let [e (- % mu)] (* e e)) vs)) (dec n)))]
                     [mu (if (> sd 1e-12) sd 1.0)]))])))

(def clip-sd "States are clipped at this many SDs of the training rows." 3.0)

(defn- z [std r nm]
  (when-let [x (get-in r [:states nm])]
    (let [[mu sd] (std nm)] (max (- clip-sd) (min clip-sd (/ (- x mu) sd))))))

(defn term-value
  "A term's value in a row under standardizer `std`: the clipped z of its
  state, 0 (the training mean) when missing; the interaction is the
  product of the credit gap's and house growth's positive parts, both
  booming (Greenwood et al. 2022's zone), so two busts are not a boom."
  [std r t]
  (double
   (or (if (= t :credit-x-house)
         (when-let [a (z std r :credit-gap)] (when-let [b (z std r :house)] (* (max a 0.0) (max b 0.0))))
         (z std r t))
       0.0)))

(defn- design [r terms std]
  (double-array (concat [1.0] (:base r) (for [t terms] (term-value std r t)))))

(defn- label-of [opts] (get opts :label :y5))

(defn fit-terms
  "Coefficients of the base and `terms` fitted on `train` (per SD of each state)."
  [train terms opts]
  (let [label (label-of opts)
        std (standardizer train (state-keys terms))
        k (+ 1 (count (:base (first train))) (count terms))]
    [(lg/fit (for [r train] {:x (design r terms std) :y (boolean (label r)) :w 1.0}) k {:prior (get opts :prior 1.0)})
     std]))

(defn- fit-predict [train score terms opts]
  (let [[beta std] (fit-terms train terms opts)]
    (mapv #(lg/predict beta (design % terms std)) score)))

(defn train-rows
  "The rows to fit when `era` is held out: outside it, and with windows
  (t, t + H] clear of its outcome years."
  [rows era]
  (filterv #(and (not= (:era %) (:name era))
                 (or (< (+ (:year %) H) (inc (:from era))) (> (inc (:year %)) (+ (:to era) H))))
           rows))

(defn- folds [rows opts]
  (for [era (:eras opts) :let [held (filterv #(= (:era %) (:name era)) rows)] :when (seq held)]
    [era (train-rows rows era) held]))

(defn- loeo-preds [rows terms opts]
  (vec (mapcat (fn [[_ train held]] (fit-predict train held terms opts)) (folds rows opts))))

(defn- held-rows [rows opts] (vec (mapcat #(nth % 2) (folds rows opts))))

(defn- recs [held p0 p1 opts]
  (let [label (label-of opts)]
    (mapv (fn [r a b] {:iso (:iso r) :year (:year r) :era (:era r) :y (boolean (label r)) :p0 a :p1 b}) held p0 p1)))

(defn loeo-scores
  "Leave-one-era-out records {:iso :year :era :y :p0 :p1}: p0 the model on
  the base and the model's :against terms, p1 with its :terms."
  [rows model opts]
  (recs (held-rows rows opts) (loeo-preds rows (:against model) opts) (loeo-preds rows (:terms model) opts) opts))

(defn test-scores
  "The same records for the test: fitted on `train`, scored on `score`."
  [train score model opts]
  (recs score (fit-predict train score (:against model) opts) (fit-predict train score (:terms model) opts) opts))

;; --- scores ----------------------------------------------------------------------

(defn- clip [p] (min (- 1.0 1e-12) (max 1e-12 p)))
(defn- logs [y p] (Math/log (clip (if y p (- 1.0 p)))))
(defn- sq [x] (* x x))

(defn row-gains
  "Per record, the log score gain and the Brier gain of p1 over p0."
  [recs]
  (mapv (fn [{:keys [y p0 p1] :as r}]
          (let [o (if y 1.0 0.0)]
            (assoc r :dlog (- (logs y p1) (logs y p0)) :dbrier (- (sq (- o p0)) (sq (- o p1))))))
        recs))

(defn- mean [xs] (if (seq xs) (/ (reduce + xs) (count xs)) Double/NaN))

(defn- shuffle-with [g xs]
  (let [a (object-array xs)]
    (doseq [i (range (dec (alength a)) 0 -1)]
      (let [j (long (* (m/next-double! g) (inc i))) t (aget a i)]
        (aset a i (aget a j)) (aset a j t)))
    (vec a)))

(defn block-bootstrap
  "[5% 95%] of f over moving-block resamples of the records' origin years."
  [recs f draws block g]
  (if (or (zero? draws) (empty? recs))
    [Double/NaN Double/NaN]
    (let [by (group-by :year recs)
          years (vec (sort (keys by)))
          n (count years) L (min block n)
          xs (double-array (sort (for [_ (range draws)]
                                   (let [picked (loop [acc []]
                                                  (if (>= (count acc) n) acc
                                                      (let [s (long (* (m/next-double! g) (inc (- n L))))]
                                                        (recur (into acc (subvec years s (+ s L)))))))]
                                     (f (mapcat by (take n picked)))))))]
      [(m/quantile xs 0.05) (m/quantile xs 0.95)])))

(defn holm
  "Holm-adjusted p values, in the input's order."
  [ps]
  (let [n (count ps)
        order (sort-by #(nth ps %) (range n))
        adj (loop [[i & more] order k 0 run 0.0 acc {}]
              (if (nil? i) acc
                  (let [a (max run (min 1.0 (* (- n k) (double (nth ps i)))))]
                    (recur more (inc k) a (assoc acc i a)))))]
    (mapv adj (range n))))

(defn permuted-table
  "Each country's states read from another's whole series: `perm` maps a
  country to the one it reads."
  [table perm]
  (into {} (for [[[iso y] _] table] [[iso y] (get table [(perm iso iso) y] {})])))

(defn placebo-table
  "Each state read at the next country (sorted, cyclic) with it that year."
  [table names]
  (let [by-year (group-by second (keys table))]
    (into {} (for [[y ks] by-year
                   :let [isos (vec (sort (map first ks))) n (count isos)]
                   [i iso] (map-indexed vector isos)]
               [[iso y] (into {} (for [nm names]
                                   [nm (some (fn [k] (get-in table [[(isos (mod (+ i k) n)) y] nm])) (range 1 n))]))]))))

(defn demeaned-table
  "Each state less its cross-country mean that year (known at year end)."
  [table names]
  (let [means (into {} (for [[y ks] (group-by second (keys table))]
                         [y (into {} (for [nm names :let [vs (keep #(get-in table [% nm]) ks)] :when (seq vs)] [nm (mean vs)]))]))]
    (into {} (for [[[iso y] s] table]
               [[iso y] (into {} (for [[nm v] s] [nm (when v (- v (get-in means [y nm])))]))]))))

(defn- with-table [rows table] (mapv #(assoc % :states (get table [(:iso %) (:year %)] {})) rows))

;; --- the run ----------------------------------------------------------------------

(defn- f4 [x] (if (and x (not (Double/isNaN (double x)))) (format "%+.4f" (double x)) "NA"))
(defn- f3 [x] (if (and x (not (Double/isNaN (double x)))) (format "%.3f" (double x)) "NA"))

(defn- summarize [gains opts g]
  (let [{:keys [boot block]} opts]
    {:n (count gains) :onsets (count (filter :y gains))
     :base-log (mean (map #(logs (:y %) (:p0 %)) gains))
     :dlog (mean (map :dlog gains)) :dlog-ci (block-bootstrap gains #(mean (map :dlog %)) boot block g)
     :dbrier (mean (map :dbrier gains)) :dbrier-ci (block-bootstrap gains #(mean (map :dbrier %)) boot block g)}))

(defn- scorer
  "f: (rows, model) -> gains, by leave-one-era-out in development and by
  train/score in the test."
  [opts]
  (if-let [score (:score-rows opts)]
    (fn [train model o] (row-gains (test-scores train (with-table score (:table-of-score o (:score-table opts))) model o)))
    (fn [rows model o] (row-gains (loeo-scores rows model o)))))

(defn- perm-p [obs nulls] (/ (+ 1.0 (count (filter #(>= % obs) nulls))) (+ 1.0 (count nulls))))

(defn- run-analysis
  "The development table, the terms with Holm, the horizon profile; `rows`
  the training rows (with :states from `table`); in the test opts carry
  :score-rows and :score-table."
  [rows table opts say]
  (let [g (m/rng (:seed opts 1))
        score (scorer opts)
        test? (some? (:score-rows opts))
        isos (vec (sort (distinct (map :iso rows))))
        perms (vec (for [_ (range (:draws opts))] (zipmap isos (shuffle-with g isos))))
        ;; in the test the scored rows read the permuted or placebo table too
        under (fn [tbl model o]
                (score (with-table rows tbl) model (assoc o :table-of-score tbl)))
        pooled-dlog (fn [gains] (mean (map :dlog gains)))
        all-names (into state-names [:synthetic])
        placebo (placebo-table table all-names)
        out (atom [])]
    ;; 1. the development table (or the test's one era)
    (say "")
    (say (str (if test? "Test era" "Development, leave one era out") ": the joint model (five terms) against the base, H = 5"))
    (say (format "  %-14s %5s %6s %9s   %-30s %-30s" "era" "rows" "onsets" "base log" "log gain [90%]" "Brier gain [90%]"))
    (let [gains (score rows joint opts)
          line (fn [nm gs]
                 (let [s (summarize gs opts g)]
                   (swap! out conj (merge {:section "joint" :name nm} s))
                   (say (format "  %-14s %5d %6d %9s   %s [%s, %s]   %s [%s, %s]" nm (:n s) (:onsets s) (f4 (:base-log s))
                                (f4 (:dlog s)) (f4 (first (:dlog-ci s))) (f4 (second (:dlog-ci s)))
                                (f4 (:dbrier s)) (f4 (first (:dbrier-ci s))) (f4 (second (:dbrier-ci s)))))))]
      (when-not test?
        (doseq [e (:eras opts)] (let [gs (filter #(= (:era %) (:name e)) gains)] (when (seq gs) (line (:name e) gs)))))
      (line "pooled" gains)
      (let [obs (pooled-dlog gains)
            nulls (mapv (fn [p] (pooled-dlog (under (permuted-table table p) joint opts))) perms)
            plc (pooled-dlog (under placebo joint opts))]
        (say (format "  joint: permutation p %s (null mean %s, %d draws); placebo (next country's states) %s"
                     (f3 (perm-p obs nulls)) (f4 (mean nulls)) (count nulls) (f4 plc)))
        (swap! out conj {:section "joint-null" :name "pooled" :dlog obs :p (perm-p obs nulls) :placebo plc}))
      ;; 2. the terms, Holm over the five
      (say "")
      (say "Terms (each over the base; the interaction over its main effects), pooled; coefficient per SD on all training rows")
      (say (format "  %-16s %-30s %-10s %6s %6s %6s %9s" "term" "log gain [90%]" "Brier" "p" "Holm" "coef" "placebo"))
      (let [res (vec (for [model (into term-models record-models)]
                       (let [gs (score rows model opts)
                             s (summarize gs opts g)
                             obs (:dlog s)
                             nulls (mapv (fn [p] (pooled-dlog (under (permuted-table table p) model opts))) perms)
                             [beta _] (fit-terms rows (:terms model) opts)
                             coef (aget ^doubles beta (+ 1 (count (:base (first rows))) (dec (count (:terms model)))))]
                         (assoc s :name (:name model) :p (perm-p obs nulls) :coef coef
                                :placebo (pooled-dlog (under placebo model opts))))))
            adj (holm (mapv :p (take (count term-models) res)))]
        (doseq [[i r] (map-indexed vector res)
                :let [h (get adj i)
                      term (last (:terms (if (< i (count term-models)) (term-models i) (record-models (- i (count term-models))))))]]
          (swap! out conj (assoc r :section "term" :holm h :sign (signs term)))
          (say (format "  %-16s %s [%s, %s] %s %6s %6s %+6.2f%s %9s" (:name r) (f4 (:dlog r)) (f4 (first (:dlog-ci r))) (f4 (second (:dlog-ci r)))
                       (f4 (:dbrier r)) (f3 (:p r)) (if h (f3 h) "(rec)") (:coef r)
                       (if (= (Math/signum (double (:coef r))) (double (signs term))) " " "!") (f4 (:placebo r))))))
      ;; 3. the horizon profile
      (say "")
      (say "Horizon profile: onset in year t + h exactly, same rows; joint log gain, and s x coef per SD by term")
      (say (format "  %-3s %6s %-30s %s" "h" "onsets" "joint log gain [90%]" (str/join " " (map #(format "%10s" (name %)) (:terms joint)))))
      (let [prof (fn [tbl o]
                   (vec (for [h (range 1 (inc H))]
                          (let [o (assoc o :label #(nth (:yh %) (dec h)))]
                            (score (with-table rows tbl) joint (assoc o :table-of-score tbl))))))
            obs-prof (prof table opts)
            rise (fn [gs-by-h] (let [gm (mapv pooled-dlog gs-by-h)] (- (mean [(gm 3) (gm 4)]) (mean [(gm 0) (gm 1)]))))
            coefs (vec (for [h (range 1 (inc H))]
                         (let [[beta _] (fit-terms rows (:terms joint) (assoc opts :label #(nth (:yh %) (dec h))))
                               k0 (+ 1 (count (:base (first rows))))]
                           (mapv (fn [i t] (* (signs t) (aget ^doubles beta (+ k0 i)))) (range) (:terms joint)))))]
        (doseq [h (range 1 (inc H))
                :let [gs (obs-prof (dec h)) s (summarize gs opts g)]]
          (swap! out conj (assoc s :section "horizon" :name (str "h" h) :coefs (coefs (dec h))))
          (say (format "  %-3d %6d %s [%s, %s]   %s" h (:onsets s) (f4 (:dlog s)) (f4 (first (:dlog-ci s))) (f4 (second (:dlog-ci s)))
                       (str/join " " (map #(format "%+10.3f" %) (coefs (dec h)))))))
        (let [obs (rise obs-prof)
              nulls (mapv (fn [p] (rise (prof (permuted-table table p) opts))) perms)
              slopes (mapv (fn [i] (- (mean [((coefs 3) i) ((coefs 4) i)]) (mean [((coefs 0) i) ((coefs 1) i)]))) (range (count (:terms joint))))]
          (swap! out conj {:section "horizon-rise" :name "joint" :dlog obs :p (perm-p obs nulls)})
          (say (format "  rise, mean gain h4-5 less h1-2: %s, permutation p %s; s(c45 - c12) by term: %s"
                       (f4 obs) (f3 (perm-p obs nulls)) (str/join " " (map #(format "%+.3f" %) slopes)))))))
    @out))

;; --- power: what these rows can detect -------------------------------------------------

(defn- simulate-jst
  "The JST rows with crisis flags redrawn from a synthetic own state x (an
  AR(1) at 0.9 within each country, unit variance) and a year shock (sd
  0.76, banking's in the power audit): an onset in year y with probability
  expit(a + u_y + beta x_{y-1}). Coverage is the data's, the test era
  included; no real flag from it is read."
  [jst cutoff rate beta g]
  (let [isos (sort (distinct (map :iso jst)))
        years (range 1869 2021)
        x (into {} (for [iso isos
                         [y v] (map vector years (reductions (fn [p _] (+ (* 0.9 p) (* (Math/sqrt 0.19) (m/normal! g))))
                                                             (m/normal! g) (rest years)))]
                     [[iso y] v]))
        u (into {} (for [y years] [y (* 0.76 (m/normal! g))]))
        a (m/logit rate)]
    [(mapv (fn [r] (if (or (some? (:crisisJST r)) (>= (:year r) cutoff))
                     (assoc r :crisisJST (if (< (m/next-double! g) (m/expit (+ a (u (:year r)) (* beta (x [(:iso r) (dec (:year r))]))))) 1.0 0.0))
                     r))
           jst)
     x]))

(defn power
  "Share of draws in which the synthetic state's pooled log gain has its
  90% interval above 0, in development (leave one era out) and in a test
  on the test era's rows (simulated outcomes); and the mean gains."
  [jst opts ors draws say]
  (let [g (m/rng (+ 1000 (:seed opts 1)))
        {:keys [eras test-era]} opts
        dev-jst (mask-outcomes jst (:from test-era))
        ;; the development years' annual onset rate, the simulation's level
        cs (countries dev-jst)
        rate (let [cy (for [[_ c] cs y (:covered c)] (if ((:onsets c) y) 1.0 0.0))] (mean cy))
        model {:name "synthetic" :terms [:synthetic] :against []}
        o (assoc opts :boot 200)]
    (say "")
    (say (format "Power: a synthetic own state, OR per SD on the annual hazard, year shocks sd 0.76, onset rate %.3f; %d draws" rate draws))
    (say (format "  %-6s %-28s %-28s" "OR" "development: power, mean gain" "test era: power, mean gain"))
    (vec (for [or* ors]
           (let [res (for [_ (range draws)]
                       (let [[sim x] (simulate-jst dev-jst (:from test-era) rate (Math/log or*) g)
                             syn (fn [rows] (mapv #(assoc-in % [:states :synthetic] (x [(:iso %) (dec (:year %))])) rows))
                             dev (syn (dev-rows sim opts))
                             dg (row-gains (loeo-scores dev model o))
                             {:keys [train score]} (test-rows sim (assoc opts :confirm true))
                             tg (row-gains (test-scores (syn train) (syn score) model o))
                             hit (fn [gs] (if (> (first (block-bootstrap gs #(mean (map :dlog %)) 200 (:block opts) g)) 0.0) 1.0 0.0))]
                         [(hit dg) (mean (map :dlog dg)) (hit tg) (mean (map :dlog tg))]))
                 col (fn [i] (mean (map #(nth % i) res)))
                 r {:or or* :dev-power (col 0) :dev-gain (col 1) :test-power (col 2) :test-gain (col 3)}]
             (say (format "  %-6.2f %5.2f  %s              %5.2f  %s" or* (:dev-power r) (f4 (:dev-gain r)) (:test-power r) (f4 (:test-gain r))))
             r)))))

;; --- main -----------------------------------------------------------------------

(defn parse-opts [args]
  (let [m (apply hash-map args)
        num (fn [k d f] (if-let [v (get m k)] (f v) d))]
    {:mode (keyword (get m "--mode" "dev"))
     :confirm (= "yes" (get m "--confirm-registered"))
     :eras (if-let [s (get m "--eras")] (parse-eras s) default-eras)
     :test-era (if-let [s (get m "--test-era")] (parse-era s) default-test-era)
     :draws (num "--draws" 200 parse-long)
     :boot (num "--boot" 1000 parse-long)
     :block (num "--block" 5 parse-long)
     :seed (num "--seed" 1 parse-long)
     :prior (num "--prior" 1.0 parse-double)
     :power (when-let [s (get m "--power")] (mapv parse-double (str/split s #",")))
     :power-draws (num "--power-draws" 100 parse-long)
     :demean (= "yes" (get m "--demean"))
     :jst (get m "--jst" "data/raw/slow/jst/jst.csv")
     :out (get m "--out")}))

(defn -main [& args]
  (let [opts (parse-opts args)
        {:keys [mode eras test-era]} opts
        out-base (or (:out opts) (str "data/runs/sequencing/t5-" (name mode)))
        text (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append text s) (.append text "\n")))
        jst (mh/read-jst (:jst opts))
        names (into state-names [:synthetic])]
    (validate-eras! eras test-era)
    (say (str "T5, the half-decade on JST, mode " (name mode) "; eras " (str/join ", " (map #(str (:name %) " " (:from %) "-" (:to %)) eras))
              "; test era " (:from test-era) "-" (:to test-era) (when (:demean opts) "; states demeaned by year")))
    (let [results
          (case mode
            :dev (let [;; the masked data, the only thing development reads
                       cs (countries (mask-outcomes jst (:from test-era)))
                       table0 (state-table cs eras)
                       table (if (:demean opts) (demeaned-table table0 names) table0)
                       rows (with-table (dev-rows jst opts) table)]
                   (say (format "rows %d, onsets within five years %d, countries %d; origins %d-%d"
                                (count rows) (count (filter :y5 rows)) (count (distinct (map :iso rows)))
                                (apply min (map :year rows)) (apply max (map :year rows))))
                   (say "per era: " (str/join "; " (for [e eras :let [rs (filter #(= (:era %) (:name e)) rows)]]
                                                     (format "%s %d rows, %d positive, %d onset years" (:name e) (count rs) (count (filter :y5 rs))
                                                             (count (distinct (for [r rs [i y] (map-indexed vector (:yh r)) :when y] [(:iso r) (+ (:year r) i 1)])))))))
                   (concat (run-analysis rows table opts say)
                           (when (:power opts) (map #(assoc % :section "power") (power jst opts (:power opts) (:power-draws opts) say)))))
            :test (let [{:keys [train score]} (test-rows jst opts)
                        cs (countries jst)
                        table0 (state-table cs (conj (vec eras) test-era))
                        table (if (:demean opts) (demeaned-table table0 names) table0)]
                    (say (format "training rows %d (%d positive); test rows %d, origins %d-%d"
                                 (count train) (count (filter :y5 train)) (count score)
                                 (apply min (map :year score)) (apply max (map :year score))))
                    (run-analysis (with-table train table) table (assoc opts :score-rows score :score-table table) say)))]
      (io/make-parents (str out-base ".txt"))
      (spit (str out-base ".txt") (str text))
      (spit (str out-base ".edn") (pr-str (vec results)))
      (println "wrote" (str out-base ".txt") "and .edn"))))
