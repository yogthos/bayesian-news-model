(ns bnm.attribution
  "Trial T4 of bnm-3qu (bead bnm-3qu.5, docs/T4-ATTRIBUTION.md): how much
  of the event forecasts' skill is attributable to the stock-flow states,
  as against what they add over a base that already knows each country's
  history (docs/LIT-NEXT-STEPS.md, 3b and ranked trial 4)?

  The rows are a run's year node (t44-world-norel): year-end origins t,
  country rows, the outcome an onset within the node's 24 months (years
  t + 1 and t + 2), the base its forecast p. Each model is a logit with a
  fixed effect per origin year, fitted on the same rows:
  - M0: the year effects only, each kind's yearly rate (no history);
  - M_S: M0 plus the named states (thirteen: T1's ten and trial 45's
    arable land, grain use and power capacity), each standardized, not
    demeaned, with a missing indicator; a ridge on their coefficients;
  - M_P: M0 plus the placebo states: each country reads the next
    country's (sorted order; a fixed derangement as in bnm.state-diag);
  - M_B': M0 plus the base recalibrated, a + b logit p (b free): the
    primary base, since the raw base is far from calibrated (slopes 0.1-0.8
    on the design years) and its miscalibration is not history;
  - M_B'S, M_B'P: M_B' plus the states, plus the placebo;
  - for the record, M_B, M_BS, M_BP: the base as a raw offset (b = 1).

  Scoring is out of sample, by era: fit on one era, score the other
  (design: 1960-1980 and 1981-2002, each way). An era [a b] holds the
  origins a..b-2, whose windows lie in it, so no fitted row's outcome
  year is a scored row's. A scored year has no fitted effect, so:
  - within (primary): each model's forecasts for a scored year are shifted
    by one log-odds constant to M0's level there (the fitted era's rate;
    no outcome of the scored year is read), so every model forecasts the
    same count each year and only where the onsets fall differs;
  - raw (record): the year effects replaced by the constant that
    reproduces the fitted era's rate; timing skill (the base's) included.

  Skill is the mean loss of M0 less a model's (log score and Brier), per
  kind and pooled with kinds weighted equally. The states' share of the
  base's skill is skill(S) / skill(B'); the Shapley split of skill(B'S)
  between the states and the base's history is
  states = (skill(S) + skill(B'S) - skill(B')) / 2, history the rest;
  the placebo gets the same numbers. Intervals (90%) from a bootstrap of
  blocks of two consecutive origin years (overlapping windows), the
  fits held.

  States (docs/T4-ATTRIBUTION.md): as known at the origin
  (bnm.horizon-profile/state-panels, the material tables two years after
  their data year, carried at most three years). Arable land, grain use
  and power capacity enter as log(1 + x). A missing value enters at the
  state's running mean (every value known by the origin, over the
  countries in the rows), with an indicator. Each is clipped at the
  fitted rows' 1st and 99th percentiles, then standardized on them. The
  ridge precision (per standardized coefficient) is chosen per model and
  state group by the design rotation's pooled within log score, and
  fixed from that file for a later run.

    jolt -A:tools -M -m bnm.attribution [--run t44-world-norel] [--from 1960] [--to 2002]
      [--split 1980] [--fit-from 1960 --fit-to 2002] [--kinds a,b,...]
      [--lambdas 1,3,10,30,100,300,1000,3000,10000]
      [--penalties data/runs/sequencing/attribution-t4-penalties.edn]
      [--reps 1000] [--seed 1] [--out data/runs/sequencing/attribution-t4-design.tsv]

  --from is the first scored origin, --to the last outcome year scored.
  Without --fit-from/--fit-to: the design rotation within from..to at
  --split, the penalties chosen and written to --penalties. With them:
  fit on that span, score from..to, the penalties read from --penalties.
  --kinds limits the kinds (default all; a kind is scored in a split only
  if its fitted era has five onsets: protest and violence surges start in
  1979, so the design rotation cannot score them)."
  (:require [augur.math :as m]
            [bnm.horizon-profile :as hp]
            [bnm.kinds]
            [bnm.relation-diag :as rd]
            [bnm.states :as states]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- states and groups --------------------------------------------------------------

(def state-groups
  "The thirteen states and their groups."
  [["credit-gap" "financial"] ["keen-debt-gap" "financial"] ["debt-service" "financial"] ["reserves" "financial"]
   ["youth-bulge" "social"] ["elite-overproduction" "social"] ["anocracy" "social"]
   ["food-import-exposure" "material"] ["arable-land" "material"] ["grain-use" "material"]
   ["power-capacity" "material"] ["fuel-price-gap" "material"]
   ["technology-gap" "technology"]])

(def group-order ["all" "financial" "social" "material" "technology" "where" "when"])

(def where-cut
  "A state whose between-country share of variance (year means removed) is
  at least this is a 'where' state; the rest are 'when' states."
  0.5)

;; --- eras ---------------------------------------------------------------------------

(def window "Years after the origin the year node's outcome covers." 2)

(defn outcome-years [t] (range (inc t) (+ t window 1)))

(defn era-origins
  "The origins of era [a b]: from a, their outcome window inside b."
  [[a b]]
  (range a (inc (- b window))))

(defn- check-split! [{:keys [fit score] :as s}]
  (let [fy (set (mapcat outcome-years (era-origins fit)))
        clash (filter fy (mapcat outcome-years (era-origins score)))]
    (when (seq clash)
      (throw (ex-info (str "fitted era " fit " covers outcome years scored in " score ": " (vec (distinct clash))) s)))
    s))

(defn splits
  "[{:fit [a b] :score [a b]}]: with fit-from and fit-to, one split; else
  the rotation within from..to at `split`, each era scored by a fit on the
  other. A fitted era that touches a scored outcome year is an error."
  [{:keys [from to split fit-from fit-to]}]
  (mapv check-split!
        (if fit-from
          [{:fit [fit-from fit-to] :score [from to]}]
          [{:fit [from split] :score [(inc split) to]} {:fit [(inc split) to] :score [from split]}])))

(defn split-rows
  "{:fit :score} of `rows` ({:t}) for a split."
  [rows {:keys [fit score]}]
  (let [f (set (era-origins fit)) s (set (era-origins score))]
    {:fit (filterv #(f (:t %)) rows) :score (filterv #(s (:t %)) rows)}))

;; --- reading --------------------------------------------------------------------------

(defn running-means
  "(sorted-map year mean): the mean of every value of `panel` ({c {t v}})
  at years up to the year."
  [panel]
  (let [by-year (reduce (fn [acc [_ byy]] (reduce (fn [acc [y v]] (update acc y (fn [[s n]] [(+ (or s 0.0) v) (inc (or n 0))]))) acc byy))
                        (sorted-map) panel)]
    (loop [[[y [s n]] & more] (seq by-year) tot 0.0 cnt 0 out (sorted-map)]
      (if (nil? y)
        out
        (let [tot (+ tot s) cnt (+ cnt n)] (recur more tot cnt (assoc out y (/ tot cnt))))))))

(defn reader
  "(fn [c t]) -> [value missing?]: the panel's value, or the state's
  running mean at t (nil before anything is known) and missing."
  [panel]
  (let [rm (running-means panel)]
    (fn [c t]
      (if-some [v (get-in panel [c t])]
        [v false]
        [(some-> (rsubseq rm <= t) first val) true]))))

(defn derange
  "{c c'}: each country to the next in sorted order, the last to the first."
  [cs]
  (let [cs (vec (sort cs))] (zipmap cs (conj (subvec cs 1) (cs 0)))))

(defn known-panel
  "{iso {year (f value)}} as known at each year end from {iso (sorted-map
  year-known value)}, carried at most `max-age` years."
  [by-iso max-age f]
  (into {} (for [[iso byy] by-iso
                 :let [k (hp/as-known (into {} (for [[y x] byy] [y [y x]])) hp/years max-age)]
                 :when (seq k)]
             [iso (update-vals k f)])))

(defn scaler
  "(fn [v]) -> z from the values `xs` (the fitted rows'): clipped at their
  1st and 99th percentiles, less their mean, over their spread; nil -> 0."
  [xs]
  (let [a (double-array (sort (remove nil? xs)))]
    (if (zero? (alength a))
      (fn [_] 0.0)
      (let [lo (m/quantile a 0.01) hi (m/quantile a 0.99)
            c (map #(min hi (max lo %)) a)
            n (double (alength a))
            mu (/ (reduce + c) n)
            sd (Math/sqrt (/ (reduce + (map #(* (- % mu) (- % mu)) c)) n))
            sd (if (> sd 1e-12) sd 1.0)]
        (fn [v] (if (nil? v) 0.0 (/ (- (min hi (max lo (double v))) mu) sd)))))))

(defn between-share
  "The share of a panel's variance (each year's mean removed) that lies
  between countries' means, over the years `from`-`to`."
  [panel from to]
  (let [cells (for [[c byy] panel [y v] byy :when (<= from y to)] [c y v])
        ym (update-vals (group-by second cells) #(/ (reduce + (map last %)) (count %)))
        d (map (fn [[c y v]] [c (- v (ym y))]) cells)
        n (count d)]
    (if (< n 2)
      Double/NaN
      (let [tot (/ (reduce + (map #(* (second %) (second %)) d)) n)
            cm (update-vals (group-by first d) #(/ (reduce + (map second %)) (count %)))
            btw (/ (reduce + (map (fn [[c _]] (* (cm c) (cm c))) d)) n)]
        (if (pos? tot) (/ btw tot) Double/NaN)))))

;; --- the fit ----------------------------------------------------------------------------

(defn- logit ^double [p] (let [p (max 1e-6 (min (- 1.0 1e-6) (double p)))] (Math/log (/ p (- 1.0 p)))))

(defn- softplus ^double [^double e]
  (if (> e 0.0) (+ e (Math/log1p (Math/exp (- e)))) (Math/log1p (Math/exp e))))

(defn fit-fe
  "A logit with a fixed effect per year (:t) and `k` inputs (:x, double
  arrays), an offset :o: the inputs standardized on the rows, each
  coefficient with a ridge of precision `penalties` (per input, on the
  standardized scale), the year effects a weak prior (precision 0.01) at
  the rows' rate. Newton's steps, the year block eliminated (an arrowhead
  Hessian), halved until the penalized likelihood improves."
  [rows k penalties]
  (let [rows (vec rows) n (count rows) k (long k)
        ys (double-array (map #(if (:y %) 1.0 0.0) rows))
        os (double-array (map #(double (or (:o %) 0.0)) rows))
        years (vec (sort (distinct (map :t rows))))
        yix (zipmap years (range))
        ny (count years)
        ti (int-array (map #(yix (:t %)) rows))
        mean (double-array k) sd (double-array k 1.0)
        z (double-array (* n k))
        _ (dotimes [r n] (System/arraycopy ^doubles (:x (rows r)) 0 z (* r k) k))
        _ (dotimes [j k]
            (let [mu (/ (loop [r 0 a 0.0] (if (< r n) (recur (inc r) (+ a (aget z (+ (* r k) j)))) a)) (max 1 n))
                  v (/ (loop [r 0 a 0.0] (if (< r n) (recur (inc r) (let [d (- (aget z (+ (* r k) j)) mu)] (+ a (* d d)))) a)) (max 1 n))
                  s (Math/sqrt v)]
              (aset mean j mu)
              (aset sd j (if (> s 1e-9) s 0.0))
              (dotimes [r n]
                (aset z (+ (* r k) j) (if (> s 1e-9) (/ (- (aget z (+ (* r k) j)) mu) s) 0.0)))))
        rate (/ (reduce + ys) (max 1.0 (double n)))
        mu0 (logit rate)
        tau 0.01
        pen (double-array penalties)
        eta (fn ^double [^doubles a ^doubles b ^long r]
              (loop [j 0 acc (+ (aget os r) (aget a (aget ti r)))]
                (if (< j k) (recur (inc j) (+ acc (* (aget b j) (aget z (+ (* r k) j))))) acc)))
        objective (fn ^double [^doubles a ^doubles b]
                    (- (loop [r 0 acc 0.0]
                         (if (< r n) (let [e (eta a b r)] (recur (inc r) (+ acc (- (* (aget ys r) e) (softplus e))))) acc))
                       (* 0.5 tau (loop [t 0 acc 0.0] (if (< t ny) (recur (inc t) (let [d (- (aget a t) mu0)] (+ acc (* d d)))) acc)))
                       (* 0.5 (loop [j 0 acc 0.0] (if (< j k) (recur (inc j) (+ acc (* (aget pen j) (aget b j) (aget b j)))) acc)))))
        a (double-array ny mu0)
        b (double-array k)]
    (loop [it 0 obj (objective a b)]
      (when (< it 60)
        (let [ga (double-array ny) dd (double-array ny) cc (double-array (* ny k))
              gb (double-array k) h (double-array (* k k))]
          (dotimes [r n]
            (let [e (eta a b r) p (m/expit e) w (Math/max 1e-9 (* p (- 1.0 p))) res (- (aget ys r) p)
                  t (aget ti r) o (* r k) to (* t k)]
              (aset ga t (+ (aget ga t) res))
              (aset dd t (+ (aget dd t) w))
              (dotimes [i k]
                (let [zi (aget z (+ o i)) wzi (* w zi)]
                  (aset cc (+ to i) (+ (aget cc (+ to i)) wzi))
                  (aset gb i (+ (aget gb i) (* res zi)))
                  (loop [j i] (when (< j k) (aset h (+ (* i k) j) (+ (aget h (+ (* i k) j)) (* wzi (aget z (+ o j))))) (recur (inc j))))))))
          (dotimes [t ny]
            (aset ga t (- (aget ga t) (* tau (- (aget a t) mu0))))
            (aset dd t (+ (aget dd t) tau)))
          (dotimes [i k]
            (aset gb i (- (aget gb i) (* (aget pen i) (aget b i))))
            (aset h (+ (* i k) i) (+ (aget h (+ (* i k) i)) (aget pen i))))
          ;; the Schur complement of the year block
          (let [s (double-array (* k k)) rhs (double-array k)]
            (dotimes [i k]
              (aset rhs i (aget gb i))
              (loop [j i] (when (< j k) (aset s (+ (* i k) j) (aget h (+ (* i k) j))) (recur (inc j)))))
            (dotimes [t ny]
              (let [to (* t k) dt (aget dd t) gt (aget ga t)]
                (dotimes [i k]
                  (let [ci (aget cc (+ to i))]
                    (aset rhs i (- (aget rhs i) (/ (* ci gt) dt)))
                    (loop [j i] (when (< j k) (aset s (+ (* i k) j) (- (aget s (+ (* i k) j)) (/ (* ci (aget cc (+ to j))) dt))) (recur (inc j))))))))
            (dotimes [i k] (loop [j (inc i)] (when (< j k) (aset s (+ (* j k) i) (aget s (+ (* i k) j))) (recur (inc j)))))
            (let [db (if (pos? k) (m/solve-spd s rhs k) (double-array 0))
                  da (double-array ny)
                  _ (dotimes [t ny]
                      (aset da t (/ (- (aget ga t) (loop [i 0 acc 0.0] (if (< i k) (recur (inc i) (+ acc (* (aget cc (+ (* t k) i)) (aget db i)))) acc)))
                                    (aget dd t))))
                  [na nb nobj step] (loop [sc 1.0 tries 0]
                                      (let [na (double-array ny) nb (double-array k)]
                                        (dotimes [t ny] (aset na t (+ (aget a t) (* sc (aget da t)))))
                                        (dotimes [i k] (aset nb i (+ (aget b i) (* sc (aget db i)))))
                                        (let [o (objective na nb)]
                                          (if (or (> o obj) (>= tries 30)) [na nb o sc] (recur (* 0.5 sc) (inc tries))))))
                  moved (* step (max (reduce max 0.0 (map #(Math/abs (double %)) da))
                                     (reduce max 0.0 (map #(Math/abs (double %)) db))))]
              (when (> nobj obj)
                (System/arraycopy na 0 a 0 ny)
                (System/arraycopy nb 0 b 0 k))
              (when (and (> nobj obj) (> moved 1e-8))
                (recur (inc it) nobj)))))))
    {:alpha (zipmap years (vec a)) :b b :mean mean :sd sd :k k :rate rate}))

(defn coefs
  "The coefficients on the inputs' own scale."
  ^doubles [{:keys [^doubles b ^doubles sd k]}]
  (let [out (double-array k)]
    (dotimes [j k] (aset out j (if (pos? (aget sd j)) (/ (aget b j) (aget sd j)) 0.0)))
    out))

(defn linear
  "The fit's log odds for a row without its year effect: the offset plus
  the inputs' part."
  ^double [{:keys [^doubles b ^doubles mean ^doubles sd k]} row]
  (let [^doubles x (:x row) k (long k)]
    (loop [j 0 acc (double (or (:o row) 0.0))]
      (if (< j k)
        (recur (inc j) (if (pos? (aget sd j)) (+ acc (* (aget b j) (/ (- (aget x j) (aget mean j)) (aget sd j)))) acc))
        acc))))

(defn- solve-shift
  "The constant d with the mean of expit(e + d) over `es` equal to `target`."
  ^double [es target]
  (let [es (double-array es) n (alength es)
        mean-at (fn [d] (/ (loop [i 0 acc 0.0] (if (< i n) (recur (inc i) (+ acc (m/expit (+ d (aget es i))))) acc)) n))]
    (loop [lo -40.0 hi 40.0 i 0]
      (let [mid (* 0.5 (+ lo hi))]
        (if (= i 80) mid (if (< (mean-at mid) target) (recur mid hi (inc i)) (recur lo mid (inc i))))))))

(defn level
  "The year level for a year the fit has no effect for: the constant that
  reproduces the fitted rows' rate."
  ^double [fit rows]
  (solve-shift (map #(linear fit %) rows) (:rate fit)))

(defn match-mean
  "`etas` (log odds) shifted by one constant so their mean probability is p0."
  [etas p0]
  (let [d (solve-shift etas p0)] (mapv #(+ d %) etas)))

;; --- shapley and the derived statistics --------------------------------------------------

(defn shapley
  "The Shapley split of v(states and history) = `bs` between the two
  players, from v(states) = `s`, v(history) = `b` and v(none) = 0."
  [s b bs]
  (let [st (* 0.5 (+ s (- bs b)))] {:states st :history (- bs st)}))

(defn- ratio [a b] (if (and (some? a) (some? b) (not (zero? b))) (/ a b) Double/NaN))

(defn derived
  "The statistics from the skills {\"S\" \"P\" \"B\" \"BS\" \"BP\"}."
  [{:strs [S P B BS BP]}]
  (let [sh (shapley S B BS) ph (shapley P B BP)]
    {"skill-B" B "skill-S" S "skill-P" P "skill-BS" BS "skill-BP" BP
     "share" (ratio S B) "placebo-share" (ratio P B)
     "shapley-states" (:states sh) "shapley-history" (:history sh) "shapley-share" (ratio (:states sh) BS)
     "placebo-shapley-states" (:states ph) "placebo-shapley-history" (:history ph) "placebo-shapley-share" (ratio (:states ph) BP)
     "states-less-placebo" (- S P) "increment" (- BS B) "placebo-increment" (- BP B)}))

(def stat-order
  ["skill-B" "skill-S" "skill-P" "skill-BS" "skill-BP" "share" "placebo-share"
   "shapley-states" "shapley-history" "shapley-share"
   "placebo-shapley-states" "placebo-shapley-history" "placebo-shapley-share"
   "states-less-placebo" "increment" "placebo-increment"])

;; --- models --------------------------------------------------------------------------------

(defn specs
  "The models: [{:id :base :group :states :placebo?}], base :none, :recal
  (a + b logit p) or :offset (logit p, b = 1)."
  [groups]
  (vec (concat [{:id "0" :base :none} {:id "B'" :base :recal} {:id "B" :base :offset}]
               (for [[g sts] groups [pre base placebo?] [["S" :none false] ["P" :none true] ["B'S" :recal false] ["B'P" :recal true]]]
                 {:id (str pre "/" g) :base base :group g :states sts :placebo? placebo?})
               (for [[g sts] (take 1 groups) [pre placebo?] [["BS" false] ["BP" true]]]
                 {:id (str pre "/" g) :base :offset :group g :states sts :placebo? placebo?}))))

(defn- design-row
  "The logit row of a scored or fitted row for a spec: :o the raw base
  for :offset, :x the base's log odds for :recal, then each state's value
  and missing indicator (`feats` {state [z miss]} for real or placebo)."
  [{:keys [base states placebo?]} row]
  (let [fs (if placebo? (:placebo row) (:real row))
        xs (concat (when (= base :recal) [(logit (:p row))])
                   (mapcat #(fs %) states))]
    {:t (:t row) :y (:y row) :o (if (= base :offset) (logit (:p row)) 0.0) :x (double-array xs)}))

(defn- spec-k [{:keys [base states]}] (+ (if (= base :recal) 1 0) (* 2 (count states))))

(defn- penalty-vector [{:keys [base states]} lam]
  (vec (concat (when (= base :recal) [1e-4]) (repeat (* 2 (count states)) lam))))

(defn- features
  "{state [z miss]} at row r from the readers and scalers."
  [readers scalers c t]
  (into {} (for [[s rd] readers :let [[v miss?] (rd c t)]] [s [((scalers s) v) (if miss? 1.0 0.0)]])))

(defn- fit-split
  "Scored rows of one split for every kind: each with :p0 (M0's level) and
  :eta {[spec-id lam] log odds without year matching}."
  [rows-by-kind real placebo split specs lambdas penalties]
  (let [parts (update-vals rows-by-kind #(split-rows % split))
        fit-cells (distinct (for [[_ {:keys [fit]}] parts r fit] [(:c r) (:t r)]))
        scalers-of (fn [readers] (into {} (for [[s rd] readers] [s (scaler (for [[c t] fit-cells :let [[v miss?] (rd c t)] :when (not miss?)] v))])))
        sr (scalers-of real) sp (scalers-of placebo)
        attach (fn [r] (assoc r :real (features real sr (:c r) (:t r)) :placebo (features placebo sp (:c r) (:t r))))]
    (vec (apply concat
                (pmap (fn [[kind {:keys [fit score]}]]
                        (if (or (< (count (filter :y fit)) 5) (empty? score))
                          []
                          (let [fit (mapv attach fit) score (mapv attach score)
                                p0 (/ (count (filter :y fit)) (double (count fit)))
                                etas (for [sp specs
                                           lam (if (:states sp) (if-let [pl (get penalties (:id sp))] [pl] lambdas) [nil])
                                           :let [fr (mapv #(design-row sp %) fit)
                                                 f (fit-fe fr (spec-k sp) (penalty-vector sp (or lam 0.0)))
                                                 a (level f fr)]]
                                       [[(:id sp) lam] (mapv #(+ a (linear f (design-row sp %))) score)])
                                by (into {} etas)]
                            (map-indexed (fn [i r] {:kind kind :t (:t r) :c (:c r) :y (:y r) :p0 p0 :era (:score split)
                                                    :eta (update-vals by #(nth % i))})
                                         score))))
                      parts)))))

(defn- with-modes
  "Scored rows with :pr {mode {[spec lam] p}}: raw and within (each
  kind-year's forecasts shifted to M0's level)."
  [scored]
  (vec (mapcat (fn [[_ rs]]
                 (let [rs (vec rs) p0 (:p0 (first rs)) ks (keys (:eta (first rs)))
                       within (into {} (for [k ks] [k (match-mean (mapv #(get-in % [:eta k]) rs) p0)]))]
                   (map-indexed (fn [i r]
                                  (assoc r :pr {"raw" (update-vals (:eta r) m/expit)
                                                "within" (into {} (for [k ks] [k (m/expit (nth (within k) i))]))}))
                                rs)))
               (group-by (juxt :kind :t :era) scored))))

(defn- brier ^double [^double p y] (let [d (- p (if y 1.0 0.0))] (* d d)))
(defn- log-loss ^double [^double p y] (- (Math/log (Math/max 1e-12 (if y p (- 1.0 p))))))
(def ^:private losses {"log" log-loss "brier" brier})

(defn- pooled-skill
  "Kind-equal mean of (loss M0 - loss model) for key k in mode and score."
  [rows mode score k]
  (let [loss (losses score)
        per (for [[_ rs] (group-by :kind rows)]
              (/ (reduce + (map #(- (loss (:p0 %) (:y %)) (loss (get-in % [:pr mode k]) (:y %))) rs)) (count rs)))]
    (/ (reduce + per) (count per))))

(defn choose-penalties
  "{spec-id lam}: for each model with states the precision with the best
  pooled within log score."
  [rows specs lambdas]
  (into (sorted-map) (for [sp specs :when (:states sp)]
                       [(:id sp) (apply max-key #(pooled-skill rows "within" "log" [(:id sp) %]) lambdas)])))

;; --- the bootstrap -----------------------------------------------------------------------------

(defn- bootstrap
  "Point values and `reps` draws of each kind's and the pooled skill per
  column (cols: [mode score spec-id]), resampling blocks of two
  consecutive origin years. [{kind {col v}} [draw {kind {col v}}]]."
  [rows cols chosen reps seed]
  (let [kinds (vec (sort (distinct (map :kind rows))))
        years (vec (sort (distinct (map :t rows))))
        ncol (count cols)
        ;; cells: [kind year] -> [n sums]
        cells (into {} (for [[[kd t] rs] (group-by (juxt :kind :t) rows)]
                         [[kd t] [(count rs)
                                  (double-array (for [[mode score id] cols
                                                      :let [loss (losses score) k [id (chosen id)]]]
                                                  (reduce + (map #(- (loss (:p0 %) (:y %)) (loss (get-in % [:pr mode k]) (:y %))) rs))))]]))
        blocks (vec (sort (distinct (map #(quot % 2) years))))
        nb (count blocks)
        stat (fn [wt]
               (let [per (into {} (for [kd kinds
                                        :let [[n s] (reduce (fn [[n ^doubles s] t]
                                                              (let [w (wt t)]
                                                                (if-let [[cn ^doubles cs] (when (pos? w) (cells [kd t]))]
                                                                  (do (dotimes [j ncol] (aset s j (+ (aget s j) (* w (aget cs j)))))
                                                                      [(+ n (* w cn)) s])
                                                                  [n s])))
                                                            [0.0 (double-array ncol)] years)]
                                        :when (pos? n)]
                                    [kd (zipmap cols (map #(/ % n) s))]))
                     pooled (zipmap cols (for [c cols] (/ (reduce + (map #(get % c) (vals per))) (count per))))]
                 (assoc per "pooled" pooled)))
        g (m/rng seed)]
    [(stat (constantly 1.0))
     (vec (for [_ (range reps)]
            (let [cnt (frequencies (repeatedly nb #(blocks (min (dec nb) (long (* nb (m/next-double! g)))))))]
              (stat (fn [t] (double (get cnt (quot t 2) 0)))))))]))

(defn evaluate
  "The attribution on `rows-by-kind` ({kind [{:c :t :y :p}]}, the year
  node), the states `panels` ({state {c {t v}}}, as known at each year),
  `groups` ([[name [state ...]]]), `splits`; the penalties fixed
  (`penalties`, {spec-id lam}) or chosen from `lambdas` on the splits.
  {:results [{:mode :base :group :kind :score :stat :value :lo :hi}]
  :penalties :n}."
  [{:keys [rows-by-kind panels groups splits lambdas penalties reps seed]}]
  (let [cs (sort (distinct (map :c (mapcat val rows-by-kind))))
        csset (set cs)
        other (derange cs)
        real (into {} (for [[s p] panels] [s (reader (select-keys p csset))]))
        placebo (into {} (for [[s rd] real] [s (fn [c t] (rd (other c) t))]))
        sps (specs groups)
        scored (with-modes (vec (mapcat #(fit-split rows-by-kind real placebo % sps lambdas penalties) splits)))
        chosen (merge {"0" nil "B'" nil "B" nil} (or penalties (choose-penalties scored sps lambdas)))
        cols (vec (for [mode ["within" "raw"] score ["log" "brier"] sp sps] [mode score (:id sp)]))
        [point draws] (bootstrap scored cols chosen reps seed)
        q (fn [xs p] (let [a (double-array (sort (remove #(Double/isNaN %) xs)))] (if (zero? (alength a)) Double/NaN (m/quantile a p))))
        results (vec (for [mode ["within" "raw"] [base gs] [["recal" (map first groups)] ["offset" (take 1 (map first groups))]]
                           g gs kind (concat (sort (remove #{"pooled"} (keys point))) ["pooled"]) score ["log" "brier"]
                           :let [ids (if (= base "recal")
                                       {"B" "B'" "S" (str "S/" g) "P" (str "P/" g) "BS" (str "B'S/" g) "BP" (str "B'P/" g)}
                                       {"B" "B" "S" (str "S/" g) "P" (str "P/" g) "BS" (str "BS/" g) "BP" (str "BP/" g)})
                                 skills (fn [tbl] (update-vals ids #(get-in tbl [kind [mode score %]])))
                                 st (derived (skills point))
                                 ds (map #(derived (skills %)) (filter #(get % kind) draws))]
                           s stat-order]
                       {:mode mode :base base :group g :kind kind :score score :stat s :value (st s)
                        :lo (q (map #(get % s) ds) 0.05) :hi (q (map #(get % s) ds) 0.95)}))]
    {:results results :penalties (dissoc chosen "0" "B'" "B")
     :n (into (sorted-map) (for [[kd rs] (group-by :kind scored)] [kd [(count rs) (count (filter :y rs))]]))}))

;; --- the states from disk ------------------------------------------------------------------------

(defn state-panels
  "{state {iso {year value}}}: T1's ten (bnm.horizon-profile/state-panels)
  and the three material levels trial 45 kept, as known at each year end
  (data years read two years late, carried at most three), as log(1 + x)."
  []
  (let [st (states/read-states [["data/derived/material/endowments.csv" 2] ["data/derived/material/capacity.csv" 2]
                                "data/derived/stocks/food.csv"])
        lg #(Math/log1p (max 0.0 %))
        kp (fn [v] (known-panel (dissoc (get st v) "WLD") 3 lg))]
    (merge (hp/state-panels)
           {"arable-land" (kp "arable_land") "grain-use" (kp "food/grains-use") "power-capacity" (kp "power_total")})))

(defn year-rows
  "[{:c :t :y :p}] of a kind's year node: countries, year-end origins with
  a resolved outcome."
  [run kind]
  (vec (for [[c byo] (rd/read-year run kind) [o [p y]] byo
             :when (and (some? y) (= 11 (mod o 12)))]
         {:c c :t (quot o 12) :y y :p p})))

(defn parse-opts [args]
  (let [o (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        n #(some-> (o %) parse-long)]
    {:run (or (o :run) "t44-world-norel")
     :from (or (n :from) 1960) :to (or (n :to) 2002) :split (or (n :split) 1980)
     :fit-from (n :fit-from) :fit-to (n :fit-to)
     :lambdas (if-let [l (o :lambdas)] (mapv parse-double (str/split l #",")) [1.0 3.0 10.0 30.0 100.0 300.0 1000.0 3000.0 10000.0])
     :penalties (or (o :penalties) "data/runs/sequencing/attribution-t4-penalties.edn")
     :kinds (some-> (o :kinds) (str/split #","))
     :reps (or (n :reps) 1000) :seed (or (n :seed) 1)
     :out (or (o :out) "data/runs/sequencing/attribution-t4-design.tsv")}))

(defn -main [& args]
  (let [{:keys [run from to fit-from fit-to lambdas penalties reps seed out kinds] :as opts} (parse-opts args)
        _ (when (not= (some? fit-from) (some? fit-to)) (throw (ex-info "--fit-from and --fit-to go together" opts)))
        sp (splits opts)
        registered? (some? fit-from)
        ;; every row the splits touch, nothing outside them
        span-origins (set (mapcat #(mapcat era-origins ((juxt :fit :score) %)) sp))
        ks (or kinds (sort (keys bnm.kinds/tiers)))
        rows-by-kind (into (sorted-map) (for [kd ks :let [rs (filterv #(span-origins (:t %)) (year-rows run kd))] :when (seq rs)] [kd rs]))
        cs (set (map :c (mapcat val rows-by-kind)))
        panels (update-vals (state-panels) #(select-keys % cs))
        [bf bt] (if registered? [fit-from fit-to] [from to])
        between (into (sorted-map) (for [[s _] state-groups] [s (between-share (panels s) bf bt)]))
        where (filterv #(>= (between %) where-cut) (map first state-groups))
        groups (vec (for [g group-order
                          :let [sts (case g "all" (mapv first state-groups)
                                          "where" where
                                          "when" (filterv #(< (between %) where-cut) (map first state-groups))
                                          (mapv first (filter #(= g (second %)) state-groups)))]
                          :when (seq sts)]
                      [g sts]))
        fixed (when registered? (edn/read-string (slurp penalties)))
        t0 (System/currentTimeMillis)
        res (evaluate {:rows-by-kind rows-by-kind :panels panels :groups groups :splits sp
                       :lambdas lambdas :penalties fixed :reps reps :seed seed})
        buf (StringBuilder.)
        say (fn [s] (.append buf (str s "\n")))]
    (say (str "# T4 attribution, run " run ", " (if registered? (str "fitted on " fit-from "-" fit-to ", scored " from "-" to)
                                                    (str "design rotation " from "-" (:split opts) " / " (inc (:split opts)) "-" to))
              "; eras by origin, the outcome window (t, t+2] inside the era"))
    (say (str "# splits " (pr-str sp)))
    (say "# rows scored and onsets per kind: kind\trows\tonsets")
    (doseq [[kd [n e]] (:n res)] (say (format "#  %s\t%d\t%d" kd n e)))
    (say "# states: state\tgroup\tcountries\tcountry-years\tbetween-country share (year means removed)\twhere/when")
    (doseq [[s g] state-groups :let [c (hp/coverage (panels s) bf bt)]]
      (say (format "#  %s\t%s\t%d\t%d\t%.3f\t%s" s g (:countries c) (:rows c) (between s) (if (>= (between s) where-cut) "where" "when"))))
    (say (str "# groups " (pr-str groups)))
    (say (str "# ridge precision per standardized coefficient, " (if registered? (str "fixed from " penalties) "chosen on the rotation") ": " (pr-str (:penalties res))))
    (say (str "# mode within (primary): each model's forecasts shifted per scored year to M0's level; raw: timing kept"))
    (say (str "# base recal (primary): a + b logit p fitted; offset: logit p with b = 1. Intervals 90%, " reps " draws of two-year blocks, seed " seed))
    (say "mode\tbase\tgroup\tkind\tscore\tstat\tvalue\tlo\thi")
    (doseq [{:keys [mode base group kind score stat value lo hi]} (:results res)]
      (say (format "%s\t%s\t%s\t%s\t%s\t%s\t%.6f\t%.6f\t%.6f" mode base group kind score stat (double value) (double lo) (double hi))))
    (io/make-parents out)
    (spit out (str buf))
    (when-not registered?
      (spit penalties (pr-str (:penalties res)))
      (println "wrote" penalties))
    (println "wrote" out (format "%.0fs" (/ (- (System/currentTimeMillis) t0) 1000.0)))))
