(ns bnm.ews-reframed
  "Trial T6 (bead bnm-3qu.7, docs/LIT-NEXT-STEPS.md section 2 and ranked
  trial 6): early warnings reframed. Trial 44's critical-slowing-down
  indicators mostly lowered risk on the design years, against theory's
  sign. Two readings are tested here.

  1. Post-crisis composition. High recent variance and autocorrelation
     mark a country just after a crisis, when the hazard of a new onset is
     low (the refractory effect). Rows whose origin falls in the year of an
     onset of the same kind at the country or the `k` years after it are
     dropped (k = 3: LIT-NEXT-STEPS trial 6, \"the last onset and three
     years after it\"; about the median length of a banking crisis and its
     output loss, Laeven & Valencia 2013, Reinhart & Rogoff 2009), only
     onsets at or before the origin counted. The indicators are tested
     with and without the exclusion.
  2. The volatility paradox (Danielsson, Valenzuela & Zer 2018; Minsky):
     long calm comes before crises. calm-duration counts the years in a
     row the country's own-part volatility of GDP growth, inflation and
     private credit has stayed below the median of its own past, with a
     positive prior.

  The named list (five indicators, Holm over them within each group):
  - ac1, log-variance, ac1-trend, log-variance-trend (bnm.ews, 12-year
    window of the own part, bnm.factors), each the mean over GDP growth,
    inflation and food inflation of the series' indicator standardized
    on the design years; theory's sign +;
  - calm-duration, +.
  Each pooled over the kinds of a group (the power audit: one coefficient
  per state across its kinds), with the kind's year-node forecast from a
  run without relations as offset (an onset in (t, t + 2], t the origin's
  year end), the year-clustered score test (bnm.relation-diag), the
  next-country placebo on the same rows (bnm.horizon-profile/placebo-read)
  and a null from permutations of whole country series. Per kind for the
  record.

  No outcome after --to is read: every catalogue is cut at the end of
  year `to` before any row is built, and the last origin is to - 2.

    jolt -A:tools -M -m bnm.ews-reframed [--run t44-world-norel] [--from 1960] [--to 2002]
      [--k 3] [--draws 200] [--seed 1] [--out data/runs/sequencing/ews-t6-design.tsv]"
  (:require [augur.logit :as lg]
            [augur.math :as m]
            [bnm.ews :as ews]
            [bnm.factors :as fac]
            [bnm.horizon-profile :as hp]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.named :as nm]
            [bnm.relation-diag :as rd]
            [bnm.slow-train :as st]
            [bnm.warm-up :as wu]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- the exclusion and the outcome -----------------------------------------------

(defn post-crisis?
  "Whether origin month `o` (a year end) lies in the year of an onset in
  `onsets` (months) or the `k` years after it, counting only onsets known
  by `o` (month + `lag` <= o)."
  [onsets o k lag]
  (let [t (quot o 12)]
    (boolean (some (fn [mo] (and (<= (+ mo lag) o) (<= (- t k) (quot mo 12) t))) onsets))))

(defn truncate
  "A catalogue with nothing after the end of year `to`: onsets and busy
  months past it dropped, the cover ending there at the latest."
  [cat to]
  (let [cut (+ 11 (* 12 to))
        clip (fn [m] (update-vals m (fn [ms] (into (sorted-set) (filter #(<= % cut)) ms))))]
    (-> cat
        (update :onsets clip)
        (update :busy #(clip (or % {})))
        (update :through (fn [th] (update-vals th #(min % cut)))))))

(defn outcome
  "An onset at `unit` within 24 months after the end of year t (the year
  node's window), nil where not covered or not at risk."
  [cat unit t]
  (let [o (+ 11 (* 12 t))]
    (#'lb/onset-in? cat unit o o (+ o 24))))

;; --- calm duration ---------------------------------------------------------------

(defn- median [xs]
  (let [v (vec (sort xs)) n (count v)]
    (if (odd? n) (v (quot n 2)) (/ (+ (v (dec (quot n 2))) (v (quot n 2))) 2.0))))

(defn running-median-dev
  "Each x less the median of the known values before it, nil where x is
  missing or fewer than `min-n` came before."
  [xs min-n]
  (let [xs (vec xs)]
    (loop [i 0 past [] out []]
      (if (= i (count xs))
        out
        (let [x (xs i)]
          (recur (inc i) (if (some? x) (conj past x) past)
                 (conj out (when (and (some? x) (>= (count past) min-n)) (- x (median past))))))))))

(defn log-vol
  "The log sample spread over the `w` values ending at each index; nil
  before a full window, over a missing value, or for a flat window."
  [xs w]
  (let [xs (vec xs)]
    (vec (for [i (range (count xs))]
           (when (>= i (dec w))
             (let [win (subvec xs (- (inc i) w) (inc i))]
               (when (every? some? win)
                 (let [mu (/ (reduce + win) w)
                       v (/ (reduce + (map #(let [d (- % mu)] (* d d)) win)) (dec w))]
                   (when (> v 1e-12) (* 0.5 (Math/log v)))))))))))

(defn calm-run
  "Years in a row below zero: 0 at a deviation >= 0, one more than the
  year before at one below (a missing year before it, a fresh start), nil
  where unknown."
  [devs]
  (vec (rest (reductions (fn [prev d] (cond (nil? d) nil (neg? d) (inc (or prev 0)) :else 0)) nil devs))))

(defn calm-duration
  "{year years-calm} of one country from its series' own parts ({series
  {year own}}): per series the log spread over `vol-window` calendar years
  less the median of its own past (at least `min-past` of them); a year
  is calm when the mean deviation over the series known then is below
  zero. Causal: each year reads only that year and before."
  [by-series {:keys [vol-window min-past]}]
  (let [devs (for [[_ byy] by-series :when (seq byy)
                   :let [ys (range (apply min (keys byy)) (inc (apply max (keys byy))))
                         d (running-median-dev (log-vol (map #(get byy %) ys) vol-window) min-past)]]
               (into {} (keep (fn [[y x]] (when x [y x])) (map vector ys d))))
        yrs (sort (distinct (mapcat keys devs)))]
    (if (empty? yrs)
      {}
      (let [span (range (first yrs) (inc (last yrs)))
            mean-dev (for [y span :let [xs (keep #(get % y) devs)]] (when (seq xs) (/ (reduce + xs) (count xs))))]
        (into (sorted-map) (keep (fn [[y r]] (when (some? r) [y r])) (map vector span (calm-run mean-dev))))))))

;; --- rows and tests --------------------------------------------------------------

(defn kind-rows
  "Logit rows for one kind: each cell [country origin-year] with an origin
  from `from` and its window's end no later than `to`, where the offset,
  the outcome (from `cat` cut at `to`) and the read are known; :post
  whether the origin lies in the post-crisis window of the kind's onsets
  at the country (`k` years). Origins in `warm-up` (a set: the base's
  warm-up, bnm.warm-up) are no rows: the offset there is the prior."
  [{:keys [cat kind cells offset read from to k warm-up]}]
  (let [cat (truncate cat to)]
    (vec (for [[c t] cells
               :when (and (>= t from) (<= (+ t 2) to) (not (contains? warm-up t)))
               :let [o (offset c t)]
               :when (some? o)
               :let [y (outcome cat c t)]
               :when (some? y)
               :let [x (read c t)]
               :when (some? x)]
           {:y y :o (double o) :w 1.0 :g t :t t :c c :kind kind :x [1.0 (double x)]
            :post (post-crisis? (get-in cat [:onsets c]) (+ 11 (* 12 t)) k 0)}))))

(defn score-z
  "The year-clustered score statistic (bnm.relation-diag/clustered-test)
  as a signed z: the score at the offset-and-intercept fit over the root
  of its clustered variance; 0 without variance."
  ^double [rows]
  (let [rows (vec rows)
        r0 (mapv #(update % :x (fn [x] (subvec x 0 1))) rows)
        b0 (lg/fit r0 1 {:prior 1e-4})
        ps (mapv #(lg/predict b0 (:x %) (:o %)) r0)
        xs (mapv #(double (nth (:x %) 1)) rows)
        ws (mapv #(* % (- 1.0 %)) ps)
        sw (reduce + ws)
        xbar (if (pos? sw) (/ (reduce + (map * ws xs)) sw) 0.0)
        terms (mapv (fn [r p x] [(:g r) (* (- x xbar) (- (if (:y r) 1.0 0.0) p))]) rows ps xs)
        u (reduce + (map second terms))
        v (reduce + (for [[_ ts] (group-by first terms) :let [s (reduce + (map second ts))]] (* s s)))]
    (if (pos? v) (/ u (Math/sqrt v)) 0.0)))

(defn holm
  "Holm-adjusted p values, in the order given, capped at 1."
  [ps]
  (let [n (count ps)
        order (sort-by #(nth ps %) (range n))
        adj (reductions max (map-indexed (fn [r i] (min 1.0 (* (- n r) (nth ps i)))) order))]
    (reduce (fn [v [i a]] (assoc v i a)) (vec ps) (map vector order adj))))

(defn perm-p
  "One-sided permutation p of `obs` against `nulls` in the direction of
  `sign`: (1 + #{s null >= s obs}) / (1 + draws)."
  [obs nulls sign]
  (/ (+ 1.0 (count (filter #(>= (* sign %) (* sign obs)) nulls))) (+ 1.0 (count nulls))))

;; --- the named list --------------------------------------------------------------

(def ews-series ["economy/gdp_growth" "economy/inflation" "food/food_inflation"])
(def calm-series ["economy/gdp_growth" "economy/inflation" "credit"])

(def named
  "The pre-registered list: name, prior sign."
  [{:name "ac1" :sign 1} {:name "log-variance" :sign 1}
   {:name "ac1-trend" :sign 1} {:name "log-variance-trend" :sign 1}
   {:name "calm-duration" :sign 1}])

(def groups
  "The kinds pooled per group. Food price shocks are world shocks, with no
  design-year onsets."
  {"financial" ["banking_crisis" "currency_crisis" "sovereign_default" "fiscal_consolidation"
                "market_crash" "growth_collapse" "inflation_crisis"]
   "political" ["protest_surge" "violence_surge" "coup" "conflict_onset" "war_onset"]})

(def design-end
  "The last year whose values set each series indicator's scale (values,
  never outcomes): fixed, so the test years read the design years' scale."
  2002)

(def calm-opts {:vol-window 5 :min-past 5})

(defn standardize
  "{iso {year x}} centred and scaled by its values in years <= `end`."
  [panel end]
  (let [xs (for [[_ byy] panel [y x] byy :when (<= y end)] x)
        n (count xs)
        mu (if (pos? n) (/ (reduce + xs) n) 0.0)
        sd (if (> n 1) (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) xs)) n)) 1.0)
        sd (if (> sd 1e-12) sd 1.0)]
    (update-vals panel (fn [byy] (into (sorted-map) (for [[y x] byy] [y (/ (- x mu) sd)]))))))

(defn mean-panel
  "{iso {year mean}} over the panels known at each country-year."
  [panels]
  (let [acc (reduce (fn [acc p] (reduce (fn [acc [c byy]] (reduce (fn [acc [y x]] (update-in acc [c y] (fnil conj []) x)) acc byy)) acc p))
                    {} panels)]
    (update-vals acc (fn [byy] (into (sorted-map) (for [[y xs] byy] [y (/ (reduce + xs) (count xs))]))))))

(defn credit-panel
  "{iso {year change}}: the yearly change in private credit (% GDP) as
  known at each year end."
  [db cs]
  (into {} (for [[c byy] (ews/series-panel db cs "finance/private_credit_gdp")
                 :let [d (into (sorted-map) (for [[y x] byy :let [p (get byy (dec y))] :when p] [y (- x p)]))]
                 :when (seq d)]
             [c d])))

(defn indicator-panels
  "{indicator {iso {year value}}} for the named list."
  [db cs tree]
  (let [raw (into {} (for [v ews-series] [v (ews/series-panel db cs v)]))
        sigs (into {} (pmap (fn [v] [v (ews/own-indicators (raw v) tree 12)]) ews-series))
        csd (into {} (for [ind [:ac1 :log-variance :ac1-trend :log-variance-trend]]
                       [(name ind)
                        (mean-panel (for [v ews-series]
                                      (standardize (into {} (for [[c byy] (sigs v)
                                                                  :let [z (into (sorted-map) (keep (fn [[y s]] (when-let [x (get s ind)] [y x])) byy))]
                                                                  :when (seq z)]
                                                              [c z]))
                                                   design-end)))]))
        calm-raw (assoc (select-keys raw calm-series) "credit" (credit-panel db cs))
        owns (into {} (for [v calm-series] [v (fac/decompose (calm-raw v) tree {:discount 0.99})]))
        calm (into {} (for [c cs
                            :let [by-series (into {} (for [v calm-series
                                                           :let [byy (get-in owns [v c])
                                                                 ;; the first own part (its level not yet learned) left out
                                                                 y0 (when (seq byy) (apply min (keys byy)))]
                                                           :when y0]
                                                       [v (into {} (for [[y {:keys [own]}] byy :when (and (> y y0) (some? own))] [y own]))]))
                                  d (calm-duration by-series calm-opts)]
                            :when (seq d)]
                        [c d]))]
    (assoc csd "calm-duration" calm)))

;; --- the run ---------------------------------------------------------------------

(defn read-base
  "{place {origin-year log-odds}} of a kind's year node at year ends,
  countries only: the forecasts alone (the outcomes in the file are not
  kept)."
  [run kind]
  (with-open [r (io/reader (str "data/runs/" run "/" kind "/forecasts.tsv"))]
    (reduce (fn [acc l]
              (let [[s place o p] (str/split l #"\t" -1)
                    o (when (and (= s "year") (= 3 (count place))) (parse-long o))]
                (if (and o (= 11 (mod o 12)))
                  (let [p (max 1e-6 (min (- 1.0 1e-6) (parse-double p)))]
                    (assoc-in acc [place (quot o 12)] (Math/log (/ p (- 1.0 p)))))
                  acc)))
            {} (line-seq r))))

(defn parse-opts [args]
  (let [{:keys [run from to k draws seed out]} (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))]
    {:run (or run "t44-world-norel")
     :from (or (some-> from parse-long) 1960)
     :to (or (some-> to parse-long) 2002)
     :k (or (some-> k parse-long) 3)
     :draws (or (some-> draws parse-long) 200)
     :seed (or (some-> seed parse-long) 1)
     :out (or out "data/runs/sequencing/ews-t6-design.tsv")}))

(defn -main [& args]
  (let [{:keys [run from to k draws seed out]} (parse-opts args)
        db (nm/load-series "data/slow/series.tsv" (st/read-variables "data/slow/variables.tsv"))
        cats (kinds/catalogues)
        ks (mapcat val groups)
        bases (into {} (pmap (fn [kd] [kd (read-base run kd)]) ks))
        places (set (mapcat keys (vals bases)))
        tree (fac/place-tree places)
        cs (sort (filter tree places))
        panels (indicator-panels db cs tree)
        ;; each kind's rows with the read left open, once
        frame (into {} (for [kd ks]
                         [kd (kind-rows {:cat (cats kd) :kind kd :to to :from from :k k
                                         :warm-up (wu/warm-up-origins (rd/read-year run kd) (:known-lag (cats kd) 0))
                                         :cells (for [[c byt] (bases kd) [t _] byt] [c t])
                                         :offset (fn [c t] (get-in bases [kd c t]))
                                         :read (constantly 0.0)})]))
        rows-of (fn [read kds excl?]
                  (vec (for [kd kds r (frame kd)
                             :when (not (and excl? (:post r)))
                             :let [x (read (:c r) (:t r))]
                             :when (some? x)]
                         (assoc r :x [1.0 (double x)]))))
        matched (fn [panel kds excl?]
                  (let [real (fn [c t] (get-in panel [c t]))
                        fake (hp/placebo-read panel)
                        pairs (for [r (rows-of real kds excl?) :let [x (fake (:c r) (:t r))] :when (some? x)]
                                [r (assoc r :x [1.0 (double x)])])]
                    [(mapv first pairs) (mapv second pairs)]))
        buf (StringBuilder.)
        say (fn [s] (println s) (flush) (.append buf (str s "\n")))
        results (atom [])]
    (say (str "# T6 early warnings reframed, run " run ", origins " from "-" (- to 2) " (window (t, t+2], outcomes to " to "), post-crisis k " k ", " draws " permutations, seed " seed))
    (say "# coverage: indicator\tcountries\tcountry-years in the origins")
    (doseq [{:keys [name]} named
            :let [cy (for [[_ byy] (panels name) [y _] byy :when (<= from y (- to 2))] y)]]
      (say (format "#  %s\t%d\t%d" name (count (filter (fn [[_ byy]] (some #(<= from % (- to 2)) (keys byy))) (panels name))) (count cy))))
    (say "scope\tgroup\tkind\tindicator\tsign\texclusion\tn\tpos\tcoef/SD\tz\tplacebo-z\tperm-p")
    (let [g (m/rng seed)]
      (doseq [[gname kds] groups
              scope ["pooled" "kind"]
              kd (if (= scope "pooled") [gname] kds)
              :let [kds' (if (= scope "pooled") kds [kd])]
              excl? [false true]
              {:keys [name sign]} named
              :let [panel (panels name)
                    [a b] (matched panel kds' excl?)]
              :when (>= (count (filter :y a)) 5)]
        (let [z (score-z a) pz (score-z b) cf (hp/coef a)
              pcs (sort (keys panel))
              nulls (vec (for [_ (range draws)]
                           (let [rd (hp/permuted (fn [c t] (get-in panel [c t])) pcs g)
                                 rs (rows-of rd kds' excl?)]
                             (if (seq rs) (score-z rs) 0.0))))
              p (perm-p z nulls sign)
              r {:scope scope :group gname :kind kd :indicator name :sign sign :excl excl?
                 :n (count a) :pos (count (filter :y a)) :coef cf :z z :pz pz :p p}]
          (swap! results conj r)
          (say (format "%s\t%s\t%s\t%s\t%+d\t%s\t%d\t%d\t%+.3f\t%+.2f\t%+.2f\t%.3f"
                       scope gname kd name sign (if excl? "post-crisis out" "all rows") (:n r) (:pos r) cf z pz p)))))
    ;; Holm over the named list, per group and exclusion
    (say "# Holm over the named list, pooled per group")
    (say "holm\tgroup\texclusion\tindicator\tsign*z\tperm-p\tholm-p")
    (doseq [[gname _] groups excl? [false true]
            :let [rs (filter #(and (= "pooled" (:scope %)) (= gname (:group %)) (= excl? (:excl %))) @results)
                  adj (holm (mapv :p rs))]
            [r a] (map vector rs adj)]
      (say (format "holm\t%s\t%s\t%s\t%+.2f\t%.3f\t%.3f" gname (if excl? "post-crisis out" "all rows") (:indicator r)
                   (* (:sign r) (:z r)) (:p r) a)))
    (io/make-parents out)
    (spit out (str buf))
    (println "wrote" out)))
