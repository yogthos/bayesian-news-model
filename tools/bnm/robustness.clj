(ns bnm.robustness
  "Trial T9 of bnm-3qu (bead bnm-3qu.10, docs/T9-ROBUSTNESS.md): do the
  extra dimensions hold skill up when other inputs fail or the regime
  shifts? Every trial so far measured the mean score, and found the
  stock-flow states, the material levels, the links and the warnings level
  with nothing over the base. A dimension can still earn its place if it
  makes the forecasts more robust. This tool measures four kinds of it.

  The rows are the year node of t44-world-norel, as bnm.attribution reads
  them (an onset within (t, t + 2] of the year-end origin t). Every model is
  bnm.attribution's logit with a fixed effect per origin year
  (attribution/fit-fe), scored within each kind-year (each forecast
  shifted to the year-effects-only model M0's level), skill being M0's
  mean loss less the model's, kinds weighted equally. Two routes:

  - lattice (primary for criteria 1, 3 and 4): B is the lattice's forecast
    recalibrated, a + b logit p (T4's M_B'); B+D adds the extra dimensions
    D, T4's thirteen states (T1's ten, the material levels arable land,
    grain use and power capacity, which are also the levels trial 45's
    states were cut from), each with its missing indicator, ridged
    (T4's M_B'S); B+P' adds T4's coverage-matched placebo instead (M_B'P');
  - gauges (criterion 2, the rest for the record): the lattice's forecast
    cannot have its inputs knocked out at scoring time (its nodes learn
    online, every origin scored and learned from, and a rerun takes
    hours), so the base is approximated by an attribution-style logit on
    the lattice year node's named gauges (bnm.lattice-political: the year
    row of bnm.political-gauges, the kind's own history, four political
    kinds' recent onsets and the world's share with an onset) plus the
    event streams (GDELT/ICEWS protest, violence and coercion as bnm.named
    reads them for the quarters, whose forecasts carry them into the year
    node). G is that logit, G+D and G+P' add the states or the placebo.

  Criteria, each a statistic of the scored rows with an interval from a
  bootstrap of two-year blocks of origins (the fits held):
  1. worst case: for every era and kind, skill(B+D) - skill(B); the
     minimum over the cells (minimax) and their 10th percentile; the worst
     era (kinds pooled) and the worst kind (eras pooled);
  2. input dropout: at scoring time a group of the base's inputs is
     knocked out (entered as unknown, the fill a missing value gets in the
     fit): (a) events, the event streams; (b) slow, the year row (V-Dem,
     national accounts, WDI); (c) dark, a random fifth of the countries'
     event and year-row gauges unknown for each scored year (averaged over
     several draws); (d) lag2, every publication lag doubled (V-Dem two
     years, the accounts four, each WDI and event value's own lag twice,
     the onset catalogues' known-lag twice). The states are left as they
     are: the question is whether they hold skill up when the base's inputs
     fail. The statistic is the degradation ratio, the skill G+D loses over
     the skill G loses; the placebo's the same;
  3. calibration stability: in each era the slope b of logit(y) =
     c(kind, year) + b eta, eta the model's log odds, pooled over kinds; the
     drift is the spread (population SD) of b over the eras, B+D's less B's;
  4. era spread: the SD over eras of the pooled skill, B+D's over B's.

  Eras. Design (no --fit-from): the origins from --from to the outcome
  year --to, cut at --eras into eras that each hold the origins whose
  window lies inside them; each era is scored by a fit on the others
  (leave one out), so no fitted outcome year is a scored one. Registered
  (--fit-from, --fit-to): one fit on that span, the scored origins (--from
  to --to - 2) sliced at --eras. Ridge precisions are chosen on the design
  rotation's pooled within log score and fixed from --penalties after.

    jolt -A:tools -M -m bnm.robustness [--run t44-world-norel] [--from 1960] [--to 2002]
      [--eras 1960,1971,1982,1993] [--fit-from 1960 --fit-to 2002] [--kinds a,b,...]
      [--lambdas 1,10,100,1000,10000] [--penalties data/runs/sequencing/robustness-t9-penalties.edn]
      [--dark-frac 0.2] [--dark-draws 10] [--reps 1000] [--seed 1]
      [--out data/runs/sequencing/robustness-t9-design.tsv]

  --from is the first scored origin and --to the last outcome year scored,
  as in bnm.attribution; no row whose window ends after --to is read."
  (:require [augur.math :as m]
            [bnm.attribution :as at]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.lattice-political :as lp]
            [bnm.named :as nm]
            [bnm.political-gauges :as pg]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [bnm.warm-up :as wu]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- eras ---------------------------------------------------------------------------

(defn- check! [fit eras]
  (let [fy (set (mapcat at/outcome-years fit))
        clash (filter fy (mapcat at/outcome-years (mapcat :score eras)))]
    (when (seq clash)
      (throw (ex-info (str "a fitted origin's window covers scored outcome years " (vec (distinct clash))) {:eras eras})))))

(defn- era-of [origins] {:era [(apply min origins) (apply max origins)] :score (vec origins)})

(defn design-splits
  "[{:fit #{origins} :eras [{:era [first last origin] :score [origins]}]}]:
  the eras [cut_i, cut_i+1 - 1] (the last to `to`), each holding the
  origins whose window lies inside it, each scored by a fit on the others."
  [cuts to]
  (let [bounds (map vector cuts (concat (map dec (rest cuts)) [to]))
        eras (mapv #(vec (at/era-origins %)) bounds)]
    (vec (for [i (range (count eras))
               :let [fit (set (mapcat eras (remove #{i} (range (count eras)))))
                     es [(era-of (eras i))]]]
           (do (check! fit es) {:fit fit :eras es})))))

(defn registered-splits
  "One fit on the origins of [fit-from fit-to], the scored origins from
  `from` to `to` - 2 sliced at `cuts`."
  [fit-from fit-to from to cuts]
  (at/splits {:fit-from fit-from :fit-to fit-to :from from :to to})
  (let [last-o (- to at/window)
        fit (set (at/era-origins [fit-from fit-to]))
        bounds (map vector cuts (concat (map dec (rest cuts)) [last-o]))
        es (vec (for [[a b] bounds :when (<= a b)] (era-of (range a (inc b)))))]
    (when (not= (set (range from (inc last-o))) (set (mapcat :score es)))
      (throw (ex-info "the eras must slice the scored origins" {:cuts cuts :from from :to to})))
    (check! fit es)
    [{:fit fit :eras es}]))

(defn in-span
  "The rows whose origin is in `origins`: nothing else is read."
  [rows origins]
  (filterv #(contains? origins (:t %)) rows))

(defn fit-rows-of
  "The rows a fit on `origins` reads: in the span, the base's warm-up rows
  (marked by attribution/year-rows, bnm.warm-up) left out."
  [rows origins]
  (wu/fit-rows (in-span rows origins)))

;; --- dropout --------------------------------------------------------------------------

(def year-row-sources
  "Indices of bnm.political-gauges/year-names by source: V-Dem (known a year
  after its data year), the accounts (Maddison, material capabilities,
  WID, the fiscal series: two years) and WDI (each value's own lag)."
  {:vdem (vec (range 0 14)) :accounts (into (vec (range 14 20)) (range 25 29)) :wdi (vec (range 20 25))})

(defn compose-row
  "A year row from three: V-Dem's entries from `vdem`, the accounts' from
  `accounts`, WDI's from `wdi`."
  [vdem accounts wdi]
  (let [src (merge (zipmap (:vdem year-row-sources) (repeat vdem))
                   (zipmap (:accounts year-row-sources) (repeat accounts))
                   (zipmap (:wdi year-row-sources) (repeat wdi)))]
    (mapv #(nth (src %) %) (range 29))))

(defn double-lag
  "The series with each value's publication lag doubled (available' =
  available + (available - period)), held so that no period is known before
  an earlier one."
  [s]
  (let [^longs ps (:period s) ^longs as (:available s) ^doubles vs (:value s)
        n (alength ps)
        a2 (reductions max (map (fn [i] (+ (aget as i) (- (aget as i) (aget ps i)))) (range n)))]
    (sk/series (map (fn [i a] [(aget ps i) a (aget vs i)]) (range n) a2))))

(defn double-known-lag
  "A catalogue whose onsets become known twice as late."
  [cat]
  (assoc cat :known-lag (* 2 (get cat :known-lag lb/known-lag))))

(defn dark-countries
  "The countries (a `frac` share of `cs`, rounded) whose data go dark in
  year t in draw `seed`."
  [seed t cs frac]
  (let [cs (vec (sort cs))
        g (m/rng (+ (* 1000003 (long seed)) (long t)))
        keyed (sort-by first (map (fn [c] [(m/next-double! g) c]) cs))]
    (set (map second (take (Math/round (* (double frac) (count cs))) keyed)))))

(defn knock
  "The vector with the inputs at `ix` unknown (nil)."
  [v ix]
  (reduce #(assoc %1 %2 nil) (vec v) ix))

;; --- statistics ----------------------------------------------------------------------

(defn worst-case
  "{:minimax :decile} of the cell differences `xs`."
  [xs]
  (let [a (double-array (sort (remove #(Double/isNaN (double %)) xs)))]
    (if (zero? (alength a))
      {:minimax Double/NaN :decile Double/NaN}
      {:minimax (aget a 0) :decile (m/quantile a 0.1)})))

(defn degradation
  "The degradation ratio: the skill B+D loses, (bd - bd-dropped), over the
  skill B loses, (b - b-dropped); NaN where B loses nothing."
  [b b-dropped bd bd-dropped]
  (let [lb (- b b-dropped)]
    (if (< (Math/abs (double lb)) 1e-12) Double/NaN (/ (- bd bd-dropped) lb))))

(defn sd
  "The population standard deviation of `xs`."
  [xs]
  (let [xs (map double xs) n (count xs)]
    (if (zero? n)
      Double/NaN
      (let [mu (/ (reduce + xs) n)] (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) xs)) n))))))

(defn slope-fe
  "The calibration slope b of logit(y) = c_g + b l over weighted `rows`
  ({:g :l :y}, weights `w`): a level per group :g (a weak prior, precision
  0.01, at the rows' rate), Newton's steps with the groups eliminated."
  [rows w]
  (let [rows (vec rows) n (count rows)
        gs (vec (distinct (map :g rows))) gix (zipmap gs (range)) ng (count gs)
        gi (int-array (map #(gix (:g %)) rows))
        ls (double-array (map #(double (:l %)) rows))
        ys (double-array (map #(if (:y %) 1.0 0.0) rows))
        ws (double-array w)
        tot (loop [r 0 a 0.0 s 0.0] (if (< r n) (recur (inc r) (+ a (aget ws r)) (+ s (* (aget ws r) (aget ys r)))) [a s]))
        mu0 (m/logit (max 1e-6 (min (- 1 1e-6) (/ (second tot) (max 1e-12 (first tot))))))
        tau 0.01 pb 1e-6
        c (double-array ng mu0)
        obj (fn [^doubles c b]
              (- (loop [r 0 acc 0.0]
                   (if (< r n)
                     (let [wr (aget ws r)]
                       (recur (inc r) (if (pos? wr)
                                        (let [e (+ (aget c (aget gi r)) (* b (aget ls r)))]
                                          (+ acc (* wr (- (* (aget ys r) e) (if (> e 0.0) (+ e (Math/log1p (Math/exp (- e)))) (Math/log1p (Math/exp e)))))))
                                        acc)))
                     acc))
                 (* 0.5 tau (loop [g 0 acc 0.0] (if (< g ng) (recur (inc g) (let [d (- (aget c g) mu0)] (+ acc (* d d)))) acc)))
                 (* 0.5 pb b b)))]
    (loop [it 0 b 0.0 o (obj c 0.0)]
      (if (>= it 50)
        b
        (let [gc (double-array ng) hc (double-array ng) hbc (double-array ng)
              [gb hb] (loop [r 0 gb 0.0 hb 0.0]
                        (if (< r n)
                          (let [wr (aget ws r)]
                            (if (pos? wr)
                              (let [g (aget gi r) l (aget ls r)
                                    p (m/expit (+ (aget c g) (* b l)))
                                    v (* wr (max 1e-9 (* p (- 1.0 p)))) res (* wr (- (aget ys r) p))]
                                (aset gc g (+ (aget gc g) res))
                                (aset hc g (+ (aget hc g) v))
                                (aset hbc g (+ (aget hbc g) (* v l)))
                                (recur (inc r) (+ gb (* res l)) (+ hb (* v l l))))
                              (recur (inc r) gb hb)))
                          [(- gb (* pb b)) (+ hb pb)]))
              _ (dotimes [g ng] (aset gc g (- (aget gc g) (* tau (- (aget c g) mu0)))) (aset hc g (+ (aget hc g) tau)))
              s (- hb (loop [g 0 acc 0.0] (if (< g ng) (recur (inc g) (+ acc (/ (* (aget hbc g) (aget hbc g)) (aget hc g)))) acc)))
              rhs (- gb (loop [g 0 acc 0.0] (if (< g ng) (recur (inc g) (+ acc (/ (* (aget hbc g) (aget gc g)) (aget hc g)))) acc)))
              db (/ rhs (max s 1e-12))
              dc (double-array ng)
              _ (dotimes [g ng] (aset dc g (/ (- (aget gc g) (* (aget hbc g) db)) (aget hc g))))
              [nc nb no] (loop [sc 1.0 tries 0]
                           (let [nc (double-array ng)]
                             (dotimes [g ng] (aset nc g (+ (aget c g) (* sc (aget dc g)))))
                             (let [nb (+ b (* sc db)) no (obj nc nb)]
                               (if (or (> no o) (>= tries 30)) [nc nb no] (recur (* 0.5 sc) (inc tries))))))]
          (if (> no o)
            (let [moved (Math/abs (- nb b))]
              (System/arraycopy nc 0 c 0 ng)
              (if (< moved 1e-9) nb (recur (inc it) nb no)))
            b))))))

;; --- models --------------------------------------------------------------------------

(def lattice-models ["B" "BD" "BP"])
(def gauge-models ["G" "GD" "GP"])
(def scenarios ["events" "slow" "dark" "lag2"])

(defn- logit [p] (m/logit (max 1e-6 (min (- 1.0 1e-6) (double p)))))

(defn- penalty-of
  "The ridge precisions of a model's inputs: the base slope free, the
  gauges at G's precision, the states at the model's."
  [model k-gauges k-states pens]
  (vec (concat (when (#{"B" "BD" "BP"} model) [1e-4])
               (when (#{"G" "GD" "GP"} model) (repeat k-gauges (double (pens "G"))))
               (when (#{"BD" "BP" "GD" "GP"} model) (repeat k-states (double (pens model)))))))

(defn- x-of
  "The input vector of a model for a row: the base's log odds or its
  scaled gauges `gz`, then the states' [z miss] pairs `sz`."
  [model row gz sz]
  (double-array (concat (when (#{"B" "BD" "BP"} model) [(logit (:p row))])
                        (when (#{"G" "GD" "GP"} model) gz)
                        (case model ("BD" "GD") (:real sz) ("BP" "GP") (:matched sz) nil))))

(defn- scale-gauges
  "(fn [raw-vector]) -> scaled vector from the fitted rows' raw vectors."
  [raws k]
  (let [fs (mapv (fn [j] (at/scaler (keep #(nth % j) raws))) (range k))]
    (fn [v] (mapv (fn [f x] (f x)) fs v))))

(defn- fit-kind
  "Scored rows of one kind in one split: {:kind :t :c :y :p0 :era :eta
  {[model scenario] log-odds} :dark {model [log-odds per draw]}}, the fits
  at penalties `pens` (models absent from `pens` are skipped)."
  [{:keys [kind fit eras gauges gauge-groups readers models pens dark? dark-draws]}]
  (let [k (count (:intact (val (first gauges))))
        scale (scale-gauges (map #(:intact (gauges [(:c %) (:t %)])) fit) k)
        st-scalers (into {} (for [[src rds] readers]
                              [src (into {} (for [[s rd] rds]
                                              [s (at/scaler (for [r fit :let [[v miss?] (rd (:c r) (:t r))] :when (not miss?)] v))]))]))
        sz (fn [r] (into {} (for [[src rds] readers]
                              [src (vec (mapcat (fn [[s rd]] (let [[v miss?] (rd (:c r) (:t r))]
                                                              [((get-in st-scalers [src s]) v) (if miss? 1.0 0.0)]))
                                                (sort-by key rds)))])))
        k-states (* 2 (count (:real readers)))
        attach (fn [r] (assoc r :sz (sz r) :raw (gauges [(:c r) (:t r)])))
        fit (mapv attach fit)
        dropped (fn [r] (let [{:keys [intact lag2]} (:raw r)]
                          {"intact" intact
                           "events" (knock intact (:events gauge-groups))
                           "slow" (knock intact (:slow gauge-groups))
                           "lag2" lag2}))
        dark-ix (vec (concat (:events gauge-groups) (:slow gauge-groups)))]
    (let [p0 (/ (count (filter :y fit)) (double (count fit)))
          fitted (into {} (for [md models :when (contains? pens md)]
                            (let [fr (mapv (fn [r] {:t (:t r) :y (:y r) :o 0.0 :x (x-of md r (scale (:intact (:raw r))) (:sz r))}) fit)
                                  f (at/fit-fe fr (alength ^doubles (:x (first fr))) (penalty-of md k k-states pens))]
                              [md [f (at/level f fr)]])))]
     (vec (for [{:keys [era score]} eras
               :let [score (mapv attach score)]
               :when (seq score)
               r score
               :let [eta (fn [md raw] (let [[f a] (fitted md)] (+ a (at/linear f {:o 0.0 :x (x-of md r (scale raw) (:sz r))}))))
                     ds (dropped r)]]
           {:kind kind :t (:t r) :c (:c r) :y (:y r) :p0 p0 :era era
            :eta (into {} (concat (for [md (keys fitted)] [[md "intact"] (eta md (ds "intact"))])
                                  (for [md (filter (set gauge-models) (keys fitted)) s ["events" "slow" "lag2"]]
                                    [[md s] (eta md (ds s))])))
            :dark (into {} (for [md (filter (set gauge-models) (keys fitted))]
                             [md (mapv (fn [d] (eta md (if (dark? d (:c r) (:t r)) (knock (ds "intact") dark-ix) (ds "intact"))))
                                       (range dark-draws))]))})))))

(defn- score-rows
  "Every kind's scored rows over the splits, at penalties `pens`."
  [{:keys [rows-by-kind splits] :as ctx} models pens]
  (vec (apply concat
              (pmap (fn [[kind {:keys [fit eras]}]]
                      (let [fit-rows (fit-rows-of (rows-by-kind kind) fit)]
                        (if (< (count (filter :y fit-rows)) 5)
                          []
                          (fit-kind (assoc ctx :kind kind :fit fit-rows :models models :pens pens
                                           :gauges (get-in ctx [:gauges kind])
                                           :eras (for [e eras] (assoc e :score (in-span (rows-by-kind kind) (set (:score e))))))))))
                    (for [kind (sort (keys rows-by-kind)) sp splits] [kind sp])))))

(defn- brier ^double [^double p y] (let [d (- p (if y 1.0 0.0))] (* d d)))
(defn- log-loss ^double [^double p y] (- (Math/log (Math/max 1e-12 (if y p (- 1.0 p))))))

(defn- within
  "Scored rows with :loss {score {key loss}}: each key's log odds shifted per
  kind-year (and era) to M0's level; the dark draws' losses averaged."
  [scored]
  (vec (mapcat (fn [[_ rs]]
                 (let [rs (vec rs) p0 (:p0 (first rs))
                       ks (keys (:eta (first rs)))
                       sh (into {} (for [k ks] [k (at/match-mean (mapv #(get-in % [:eta k]) rs) p0)]))
                       dms (keys (:dark (first rs)))
                       nd (if (seq dms) (count (val (first (:dark (first rs))))) 0)
                       dsh (into {} (for [md dms d (range nd)] [[md d] (at/match-mean (mapv #(get-in % [:dark md d]) rs) p0)]))]
                   (map-indexed
                    (fn [i r]
                      (let [y (:y r)
                            ls (fn [loss] (merge {["0" "intact"] (loss p0 y)}
                                                 (into {} (for [k ks] [k (loss (m/expit (nth (sh k) i)) y)]))
                                                 (into {} (for [md dms]
                                                            [[md "dark"] (/ (reduce + (map #(loss (m/expit (nth (dsh [md %]) i)) y) (range nd))) nd)]))))]
                        (-> r (assoc :loss {"log" (ls log-loss) "brier" (ls brier)}) (dissoc :dark))))
                    rs)))
               (group-by (juxt :kind :t :era) scored))))

;; --- the statistics of a weighting -----------------------------------------------------------

(defn- skills
  "{[era kind] {score {key skill}}} and {kind {score {key skill}}} (eras
  pooled by rows) under year weights `wt`, from cells {[era kind t] [n
  {score {key sum-of-skill}}]}."
  [cells wt]
  (let [acc (fn [groups]
              (into {} (for [[g cs] groups
                             :let [n (reduce + (map (fn [[[_ _ t] [cn _]]] (* (wt t) cn)) cs))]
                             :when (pos? n)]
                         [g (into {} (for [score ["log" "brier"]]
                                       [score (apply merge-with + (for [[[_ _ t] [_ sums]] cs :let [w (wt t)] :when (pos? w)]
                                                                    (update-vals (sums score) #(/ (* w %) n))))]))])))]
    [(acc (group-by (fn [[[e k _] _]] [e k]) cells))
     (acc (group-by (fn [[[_ k _] _]] k) cells))]))

(defn- pooled [per score key]
  (let [vs (keep #(get-in % [score key]) (vals per))] (if (seq vs) (/ (reduce + vs) (count vs)) Double/NaN)))

(defn- statistics
  "{[criterion route stat scenario score] value} under year weights `wt`."
  [{:keys [cells calib]} wt]
  (let [[by-ek by-k] (skills cells wt)
        eras (sort (distinct (map first (keys by-ek))))
        era-pooled (fn [e score k] (pooled (into {} (filter #(= e (first (key %))) by-ek)) score k))
        out (atom {})
        put! (fn [k v] (swap! out assoc k v))]
    (doseq [score ["log" "brier"]]
      ;; the skills and increments, eras pooled, kinds equally weighted
      (doseq [md (concat lattice-models gauge-models)]
        (put! ["skill" (if (#{"B" "BD" "BP"} md) "lattice" "gauges") md "-" score] (pooled by-k score [md "intact"])))
      (doseq [[route b d p] [["lattice" "B" "BD" "BP"] ["gauges" "G" "GD" "GP"]]
              :let [diff (fn [tbl x] (- (get-in tbl [score [x "intact"]] Double/NaN) (get-in tbl [score [b "intact"]] Double/NaN)))
                    cells-d (map #(diff % d) (vals by-ek)) cells-p (map #(diff % p) (vals by-ek))
                    wd (worst-case cells-d) wp (worst-case cells-p)
                    era-d (map #(- (era-pooled % score [d "intact"]) (era-pooled % score [b "intact"])) eras)
                    era-p (map #(- (era-pooled % score [p "intact"]) (era-pooled % score [b "intact"])) eras)
                    kind-d (map #(diff % d) (vals by-k)) kind-p (map #(diff % p) (vals by-k))
                    era-sk (fn [x] (map #(era-pooled % score [x "intact"]) eras))]]
        (put! ["skill" route "increment-D" "-" score] (- (pooled by-k score [d "intact"]) (pooled by-k score [b "intact"])))
        (put! ["skill" route "increment-P" "-" score] (- (pooled by-k score [p "intact"]) (pooled by-k score [b "intact"])))
        ;; 1. worst case
        (put! ["worst-case" route "minimax-D" "-" score] (:minimax wd))
        (put! ["worst-case" route "decile-D" "-" score] (:decile wd))
        (put! ["worst-case" route "minimax-P" "-" score] (:minimax wp))
        (put! ["worst-case" route "decile-P" "-" score] (:decile wp))
        (put! ["worst-case" route "minimax-D-less-P" "-" score] (- (:minimax wd) (:minimax wp)))
        (put! ["worst-case" route "decile-D-less-P" "-" score] (- (:decile wd) (:decile wp)))
        (put! ["worst-case" route "worst-era-D" "-" score] (:minimax (worst-case era-d)))
        (put! ["worst-case" route "worst-era-P" "-" score] (:minimax (worst-case era-p)))
        (put! ["worst-case" route "worst-kind-D" "-" score] (:minimax (worst-case kind-d)))
        (put! ["worst-case" route "worst-kind-P" "-" score] (:minimax (worst-case kind-p)))
        (put! ["worst-case" route "worst-era-D-less-P" "-" score] (- (:minimax (worst-case era-d)) (:minimax (worst-case era-p))))
        (put! ["worst-case" route "worst-kind-D-less-P" "-" score] (- (:minimax (worst-case kind-d)) (:minimax (worst-case kind-p))))
        (doseq [e eras]
          (put! ["worst-case" route "era-D" (str (first e) "-" (second e)) score] (- (era-pooled e score [d "intact"]) (era-pooled e score [b "intact"])))
          (put! ["worst-case" route "era-P" (str (first e) "-" (second e)) score] (- (era-pooled e score [p "intact"]) (era-pooled e score [b "intact"]))))
        (doseq [[kd tbl] (sort-by key by-k)]
          (put! ["worst-case" route "kind-D" kd score] (diff tbl d))
          (put! ["worst-case" route "kind-P" kd score] (diff tbl p)))
        ;; 4. era spread
        (let [sb (sd (era-sk b)) sdd (sd (era-sk d)) sp (sd (era-sk p))]
          (put! ["era-spread" route "sd-B" "-" score] sb)
          (put! ["era-spread" route "sd-D" "-" score] sdd)
          (put! ["era-spread" route "sd-P" "-" score] sp)
          (put! ["era-spread" route "sd-ratio-D" "-" score] (if (pos? sb) (/ sdd sb) Double/NaN))
          (put! ["era-spread" route "sd-ratio-P" "-" score] (if (pos? sb) (/ sp sb) Double/NaN))
          (put! ["era-spread" route "sd-D-less-P" "-" score] (- sdd sp))))
      ;; 2. dropout, on the gauges
      (doseq [s scenarios
              :let [sk (fn [md sc] (pooled by-k score [md sc]))
                    g (sk "G" "intact") gs (sk "G" s) gd (sk "GD" "intact") gds (sk "GD" s) gp (sk "GP" "intact") gps (sk "GP" s)
                    rd (degradation g gs gd gds) rp (degradation g gs gp gps)]]
        (put! ["dropout" "gauges" "loss-G" s score] (- g gs))
        (put! ["dropout" "gauges" "loss-D" s score] (- gd gds))
        (put! ["dropout" "gauges" "loss-P" s score] (- gp gps))
        (put! ["dropout" "gauges" "ratio-D" s score] rd)
        (put! ["dropout" "gauges" "ratio-P" s score] rp)
        (put! ["dropout" "gauges" "ratio-D-less-P" s score] (- rd rp))
        (put! ["dropout" "gauges" "increment-D" s score] (- gds gs))
        (put! ["dropout" "gauges" "increment-P" s score] (- gps gs))))
    ;; 3. calibration
    (doseq [[route b d p] [["lattice" "B" "BD" "BP"] ["gauges" "G" "GD" "GP"]]
            :let [slopes (into {} (for [md [b d p]]
                                    [md (vec (for [[e {:keys [rows ts]}] (sort-by key (calib md))
                                                   :let [w (mapv wt ts)]
                                                   :when (pos? (reduce + w))]
                                               [e (slope-fe rows w)]))]))
                  drift (fn [md] (sd (map second (slopes md))))
                  miss (fn [md] (let [bs (map second (slopes md))] (/ (reduce + (map #(Math/abs (- % 1.0)) bs)) (max 1 (count bs)))))
                  mean (fn [md] (let [bs (map second (slopes md))] (/ (reduce + bs) (max 1 (count bs)))))]]
      (doseq [[nm md] [["B" b] ["D" d] ["P" p]]]
        (put! ["calibration" route (str "drift-" nm) "-" "-"] (drift md))
        (put! ["calibration" route (str "miss-" nm) "-" "-"] (miss md))
        (put! ["calibration" route (str "mean-slope-" nm) "-" "-"] (mean md))
        (doseq [[e s] (slopes md)] (put! ["calibration" route (str "slope-" nm) (str (first e) "-" (second e)) "-"] s)))
      (put! ["calibration" route "drift-D-less-B" "-" "-"] (- (drift d) (drift b)))
      (put! ["calibration" route "drift-P-less-B" "-" "-"] (- (drift p) (drift b)))
      (put! ["calibration" route "drift-D-less-P" "-" "-"] (- (drift d) (drift p)))
      (put! ["calibration" route "miss-D-less-B" "-" "-"] (- (miss d) (miss b))))
    @out))

(defn- tables
  "The cells and calibration rows the statistics are computed from."
  [rows]
  (let [keys* (keys (get-in (first rows) [:loss "log"]))
        cells (into {} (for [[[e kd t] rs] (group-by (juxt :era :kind :t) rows)]
                         [[e kd t] [(count rs)
                                    (into {} (for [score ["log" "brier"]]
                                               [score (into {} (for [k keys*]
                                                                 [k (reduce + (map #(- (get-in % [:loss score ["0" "intact"]])
                                                                                       (get-in % [:loss score k])) rs))]))]))]]))
        calib (into {} (for [md (concat lattice-models gauge-models)]
                         [md (into {} (for [[e rs] (group-by :era rows)
                                            :let [rs (filter #(get-in % [:eta [md "intact"]]) rs)]
                                            :when (seq rs)]
                                        [e {:rows (mapv (fn [r] {:g [(:kind r) (:t r)] :l (get-in r [:eta [md "intact"]]) :y (:y r)}) rs)
                                            :ts (mapv :t rs)}]))]))]
    {:cells cells :calib calib}))

(defn- pooled-within-log
  "Kind-equal mean within log skill of `key` over scored rows."
  [rows key]
  (let [per (for [[_ rs] (group-by :kind rows)]
              (/ (reduce + (map #(- (get-in % [:loss "log" ["0" "intact"]]) (get-in % [:loss "log" key])) rs)) (count rs)))]
    (/ (reduce + per) (count per))))

(defn choose-penalties
  "{model lam}: G's precision first, then each state-bearing model's given
  it, each the best pooled within log score over the splits."
  [ctx lambdas]
  (let [score1 (fn [md pens] (pooled-within-log (within (score-rows ctx [md] pens)) [md "intact"]))
        best (fn [md base] (apply max-key #(score1 md (assoc base md %)) lambdas))
        g (best "G" {})
        pens {"G" g}]
    (into (sorted-map) (concat [["G" g]]
                               (for [md ["BD" "BP" "GD" "GP"]] [md (best md pens)])))))

(defn evaluate
  "The four criteria on `rows-by-kind` ({kind [{:c :t :y :p}]}), the base's
  `gauges` ({kind {[c t] {:intact v :lag2 v}}}, raw, nil unknown) in
  `gauge-groups` ({:events :slow :history} indices), the states `panels`,
  the `splits`; penalties fixed or chosen from `lambdas`.
  {:results [{:criterion :route :stat :scenario :score :value :lo :hi}] :penalties :n}."
  [{:keys [rows-by-kind gauges gauge-groups panels splits lambdas penalties reps seed dark-frac dark-draws]}]
  (let [cs (sort (distinct (map :c (mapcat val rows-by-kind))))
        csset (set cs)
        other (at/derange cs)
        readers {:real (into {} (for [[s p] panels] [s (at/reader (select-keys p csset))]))
                 :matched (into {} (for [[s p] panels] [s (at/matched-reader (select-keys p csset) other)]))}
        by-year (update-vals (group-by :t (mapcat val rows-by-kind)) #(sort (distinct (map :c %))))
        dark-sets (into {} (for [d (range dark-draws) [t cs] by-year] [[d t] (dark-countries (+ seed d) t cs dark-frac)]))
        ctx {:rows-by-kind rows-by-kind :splits splits :gauges gauges :gauge-groups gauge-groups :readers readers
             :dark? (fn [d c t] (contains? (dark-sets [d t]) c)) :dark-draws dark-draws}
        pens (or penalties (choose-penalties ctx lambdas))
        pens (merge {"B" 0.0} pens)
        rows (within (score-rows ctx (concat lattice-models gauge-models) pens))
        tb (tables rows)
        years (vec (sort (distinct (map :t rows))))
        blocks (vec (sort (distinct (map #(quot % 2) years))))
        nb (count blocks)
        point (statistics tb (constantly 1.0))
        g (m/rng seed)
        draws (vec (for [_ (range reps)]
                     (let [cnt (frequencies (repeatedly nb #(blocks (min (dec nb) (long (* nb (m/next-double! g)))))))]
                       (statistics tb (fn [t] (double (get cnt (quot t 2) 0)))))))
        q (fn [xs p] (let [a (double-array (sort (remove #(Double/isNaN (double %)) (keep identity xs))))]
                       (if (zero? (alength a)) Double/NaN (m/quantile a p))))
        order {"skill" 0 "worst-case" 1 "dropout" 2 "calibration" 3 "era-spread" 4}]
    {:results (vec (for [[[c route stat scen score :as k] v] (sort-by (fn [[[c r s sc so] _]] [(order c) r sc so s]) point)
                         :let [xs (map #(get % k) draws)]]
                     {:criterion c :route route :stat stat :scenario scen :score score :value v
                      :lo (q xs 0.05) :hi (q xs 0.95)}))
     :penalties (dissoc pens "B")
     :n (into (sorted-map) (for [[kd rs] (group-by :kind rows)] [kd [(count rs) (count (filter :y rs))]]))}))

;; --- the gauges from disk -----------------------------------------------------------------

(def design-kinds
  "T4's ten design kinds: protest and violence surges start in 1979 and
  cannot be scored in every design era; food price shocks have no design
  rows."
  ["banking_crisis" "conflict_onset" "coup" "currency_crisis" "fiscal_consolidation" "growth_collapse"
   "inflation_crisis" "market_crash" "sovereign_default" "war_onset"])

(defn- world3
  "The share of the catalogue's countries in the system at month t with an
  onset known in the last three years (bnm.lattice-political's :world)."
  [cat t]
  (let [us (filter #(and (= 3 (count %)) (<= (get-in cat [:from %]) t)) (keys (:from cat)))
        n (count (filter #(some-> (#'lp/count-known cat % t 36) pos?) us))]
    (when (seq us) (/ (double n) (count us)))))

(defn read-gauges
  "{kind {[c t] {:intact v :lag2 v}}} for the rows: the year row of
  bnm.political-gauges as the lattice year node reads it (data year t - 1),
  the event streams at the year end (bnm.named's protest3, protest12,
  violence3, violence12, coercion12), the kind's own history, four
  political kinds' recent onsets and the world's share; :lag2 with every
  publication lag doubled."
  [rows-by-kind]
  (let [variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        db2 (update-vals db double-lag)
        annual (merge (pg/read-annual) (lb/read-annual) (pg/read-annual "data/derived/fiscal.csv"))
        wdi-of (fn [d] (fn [iso v y] (some-> (d [iso v]) (sk/value (+ 11 (* 12 y)) {:stale 18}))))
        wdi (wdi-of db) wdi2 (wdi-of db2)
        row (memoize (fn [w c y] (pg/year-row annual (if (= w :2) wdi2 wdi) c y)))
        [_ pol] (nm/political db) [_ pol2] (nm/political db2)
        ev (memoize (fn [lag2? c m] (subvec (vec ((if lag2? pol2 pol) c m)) 13 18)))
        cats (kinds/catalogues)
        cats2 (update-vals cats double-known-lag)
        w3 (memoize (fn [lag2? kind t] (world3 ((if lag2? cats2 cats) kind) t)))
        hist (fn [cs kind c t]
               (let [cat (cs kind)]
                 (vec (concat (or (#'lb/history cat c t) [nil nil nil])
                              (map #(#'lp/count-known (cs %) c t 120) (take 4 (remove #{kind} lp/kinds)))))))]
    (into {} (for [[kind rs] rows-by-kind]
               [kind (into {} (pmap (fn [{:keys [c t]}]
                                      (let [mo (+ 11 (* 12 t)) y (dec t)
                                            intact (vec (concat (row :1 c y) (ev false c mo) (hist cats kind c mo) [(w3 false kind mo)]))
                                            slow2 (compose-row (row :1 c (dec y)) (row :1 c (- y 2)) (row :2 c y))
                                            lag2 (vec (concat slow2 (ev true c mo) (hist cats2 kind c mo) [(w3 true kind mo)]))]
                                        [[c t] {:intact intact :lag2 lag2}]))
                                    rs))]))))

(def gauge-groups
  "The gauge vector's groups: the year row (29), the event streams (5), the
  histories (8)."
  {:slow (vec (range 0 29)) :events (vec (range 29 34)) :history (vec (range 34 42))})

;; --- main -----------------------------------------------------------------------------------

(defn parse-opts [args]
  (let [o (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        n #(some-> (o %) parse-long)
        longs #(mapv parse-long (str/split % #","))
        fit-from (n :fit-from)]
    {:run (or (o :run) "t44-world-norel")
     :from (or (n :from) 1960) :to (or (n :to) 2002)
     :eras (if-let [e (o :eras)] (longs e) (if fit-from [2002 2007 2012 2017] [1960 1971 1982 1993]))
     :fit-from fit-from :fit-to (n :fit-to)
     :kinds (if-let [k (o :kinds)] (str/split k #",") design-kinds)
     :lambdas (if-let [l (o :lambdas)] (mapv parse-double (str/split l #",")) [1.0 10.0 100.0 1000.0 10000.0])
     :penalties (or (o :penalties) "data/runs/sequencing/robustness-t9-penalties.edn")
     :dark-frac (or (some-> (o :dark-frac) parse-double) 0.2)
     :dark-draws (or (n :dark-draws) 10)
     :reps (or (n :reps) 1000) :seed (or (n :seed) 1)
     :out (or (o :out) "data/runs/sequencing/robustness-t9-design.tsv")}))

(defn -main [& args]
  (let [{:keys [run from to eras fit-from fit-to kinds lambdas penalties dark-frac dark-draws reps seed out] :as opts} (parse-opts args)
        _ (when (not= (some? fit-from) (some? fit-to)) (throw (ex-info "--fit-from and --fit-to go together" opts)))
        registered? (some? fit-from)
        _ (when (and (not registered?) (not= from (first eras))) (throw (ex-info "--eras must start at --from" opts)))
        sp (if registered? (registered-splits fit-from fit-to from to eras) (design-splits eras to))
        span (set (concat (mapcat :fit sp) (mapcat #(mapcat :score (:eras %)) sp)))
        t0 (System/currentTimeMillis)
        rows-by-kind (into (sorted-map) (for [kd kinds :let [rs (in-span (at/year-rows run kd) span)] :when (seq rs)] [kd rs]))
        _ (println "rows" (update-vals rows-by-kind count))
        cs (set (map :c (mapcat val rows-by-kind)))
        panels (update-vals (at/state-panels) #(select-keys % cs))
        _ (println "states read" (format "%.0fs" (/ (- (System/currentTimeMillis) t0) 1000.0)))
        gauges (read-gauges rows-by-kind)
        _ (println "gauges read" (format "%.0fs" (/ (- (System/currentTimeMillis) t0) 1000.0)))
        fixed (when registered? (edn/read-string (slurp penalties)))
        res (evaluate {:rows-by-kind rows-by-kind :gauges gauges :gauge-groups gauge-groups :panels panels :splits sp
                       :lambdas lambdas :penalties fixed :reps reps :seed seed :dark-frac dark-frac :dark-draws dark-draws})
        buf (StringBuilder.)
        say (fn [s] (.append buf (str s "\n")))]
    (say (str "# T9 robustness, run " run ", " (if registered? (str "fitted on " fit-from "-" fit-to ", scored origins " from "-" (- to 2))
                                                   (str "design, each era scored by a fit on the others, origins " from "-" (- to 2)))
              "; outcome an onset in (t, t+2]"))
    (say (str "# eras (first and last origin) " (pr-str (mapv :era (mapcat :eras sp)))))
    (say (str "# kinds " (pr-str kinds)))
    (say "# rows scored and onsets per kind: kind\trows\tonsets")
    (doseq [[kd [n e]] (:n res)] (say (format "#  %s\t%d\t%d" kd n e)))
    (say (str "# D: the thirteen states of T4 (bnm.attribution/state-groups); P': T4's coverage-matched placebo"))
    (say (str "# lattice route: B = a + b logit p (the lattice's forecast); gauges route: G = the lattice year node's named gauges and the event streams"))
    (say (str "# dropout (gauges route): events, slow (year row), dark (a " dark-frac " share of countries' event and year-row gauges unknown each year, "
              dark-draws " draws), lag2 (publication lags doubled)"))
    (say (str "# ridge precision per standardized coefficient, " (if registered? (str "fixed from " penalties) "chosen on the rotation") ": " (pr-str (:penalties res))))
    (say (str "# within mode, skill = M0's mean loss less the model's, kinds equally weighted. Intervals 90%, " reps " draws of two-year blocks, seed " seed))
    (say "criterion\troute\tstat\tscenario\tscore\tvalue\tlo\thi")
    (doseq [{:keys [criterion route stat scenario score value lo hi]} (:results res)]
      (say (format "%s\t%s\t%s\t%s\t%s\t%.6f\t%.6f\t%.6f" criterion route stat scenario score (double value) (double lo) (double hi))))
    (io/make-parents out)
    (spit out (str buf))
    (when-not registered?
      (spit penalties (pr-str (:penalties res)))
      (println "wrote" penalties))
    (println "wrote" out (format "%.0fs" (/ (- (System/currentTimeMillis) t0) 1000.0)))))
