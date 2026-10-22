(ns bnm.tension
  "Social tension and structural-demographic stress as stocks, per country
  and year as known (docs/STOCK-FLOW.md, \"What to adopt\" 8; bead
  bnm-62z.9).

  Tension (Earth4All, Davies 1962): perceived progress is the TREND of
  real income per head (bnm.stockflow/trend: income smoothed over tau-p
  against that smoothed again over tau-h), expected progress a slow
  SMOOTH of perceived progress over tau-e, and the tension their gap,
  expected less perceived: a long rise then a reversal (the J-curve)
  opens it. Earth4All's STE = 1 + PESTF (gap) leaves PESTF to be
  estimated where the stock is wired; here it is the gap itself. Social
  trust is a slow stock smoothed over tau-trust toward a target set by
  inequality (down) and public spending (up), each standardized within the
  country.

  Structural-demographic stress (Goldstone 1991; Turchin 2013), measured,
  not simulated, each component standardized within the country on its
  own past (an expanding z-score, clipped):
  - mass mobilization potential (MMP): income stagnation (five-year growth
    of income per head, negated), the wage (the labour share, negated:
    Turchin's w^-1), urbanization (log urban share), the youth bulge (a
    proxy: the population outside working age ten years before, the
    children then being the 10-24s now; no age-group shares are held);
  - elite mobilization potential (EMP): credentials (log tertiary
    enrolment growth against income growth over five years: Turchin's
    elite overproduction), the top decile's income share over five years;
  - state fiscal distress (SFD): government debt to GDP, the deficit.
  Each of MMP, EMP and SFD is the mean of its parts' scores, standardized
  again, and Psi their weighted sum: the product Psi = MMP EMP SFD in logs,
  each term on one scale, so none dominates (Stachowiak & Pasek 2024: EMP
  grows as e^2 and swamps the product). The weights are not set here:
  unit by default, to be estimated where the stocks are wired (principle
  2).

  Every input is a record map {data-year [known-year value]}: a value is
  read at year Y only when known by Y's end. Outputs at Y use nothing
  known after Y."
  (:require [bnm.stockflow :as sf]))

(def defaults
  "Priors, not fits: the smoothing times (years) are leverage points to be
  estimated at the wiring step."
  {:tau-p 1.0      ; income perceived within the year
   :tau-h 3.0      ; TREND's horizon
   :tau-e 5.0      ; expectations formed on five years
   :tau-trust 8.0  ; trust moves slowly
   :burn 3         ; years of a chain before its TREND is read
   :min-n 10       ; years of history before a score
   :clip 3.0       ; scores clipped to +-3 deviations
   :stale 3        ; how old (in data years) a level may be at Y
   :hold 0})       ; years a chain may go without a new data year

(def variables
  ["perceived-progress" "expected-progress" "tension" "trust-target" "trust"
   "mmp-stagnation" "mmp-wage" "mmp-urban" "mmp-youth" "emp-credentials" "emp-top" "sfd-debt" "sfd-deficit"
   "mmp" "emp" "sfd" "psi"])

(def header "iso,year,variable,value")

(defn ln [x] (when (and x (pos? (double x))) (Math/log (double x))))

;; ---------------------------------------------------------------- reading as known

(defn annual-records
  "{data-year [known-year value]} of `var` for `iso` from an annual map
  ({var {iso {year value}}}), each value known `lag` years after its year,
  transformed by `f` (dropped where f gives nil)."
  [annual var iso lag f]
  (into (sorted-map) (for [[y x] (get-in annual [var iso]) :let [v (f x)] :when (some? v)] [y [(+ y lag) v]])))

(defn- known [recs d y] (when-let [[k v] (get recs d)] (when (<= k y) v)))

(defn latest
  "[data-year value] of the latest record known by year `y`, its data year
  no older than y - stale; nil if none."
  [recs y stale]
  (some (fn [d] (when-let [v (known recs d y)] [d v])) (range y (dec (- y stale)) -1)))

(defn chain
  "Segments of an index ({known-year value}, in order) chained from
  `sources` (record maps in priority order, values in logs or levels),
  advancing at each known year Y through every data year it can: a step d
  from the first source with d and d-1 both known by Y, the change added.
  So a fresher source takes over without skipping or doubling a year. A
  segment ends when it goes more than :hold years without a new data year;
  the next starts from the freshest level known (no older than :stale),
  past where the last ended."
  [sources opts]
  (let [{:keys [stale hold from to]} (merge defaults opts)
        sources (vec (remove empty? sources))
        ds (for [s sources [d [k _]] s] [d k])]
    (if (empty? ds)
      []
      (let [from (or from (apply min (map second ds)))
            to (or to (apply max (map second ds)))
            step (fn [d y] (some (fn [s] (when-let [a (known s d y)] (when-let [b (known s (dec d) y)] (- a b)))) sources))
            fresh (fn [y after]
                    (first (sort-by (fn [[d _ i]] [(- d) i])
                                    (for [[i s] (map-indexed vector sources)
                                          :let [[d v] (latest s y stale)] :when (and d (> d after))]
                                      [d v i]))))]
        (loop [y from cur nil segs [] last-d Long/MIN_VALUE]
          (if (> y to)
            (cond-> segs (seq (:seg cur)) (conj (:seg cur)))
            (if cur
              (let [[through x] (loop [d (inc (:through cur)) x (:x cur)]
                                  (if-let [g (step d y)] (recur (inc d) (+ x g)) [(dec d) x]))
                    idle (if (> through (:through cur)) 0 (inc (:idle cur)))]
                (if (> idle hold)
                  (recur y nil (conj segs (:seg cur)) (:through cur))
                  (recur (inc y) {:through through :x x :idle idle :seg (assoc (:seg cur) y x)} segs last-d)))
              (if-let [[d v] (fresh y last-d)]
                (recur (inc y) {:through d :x v :idle 0 :seg (sorted-map y v)} segs last-d)
                (recur (inc y) nil segs last-d)))))))))

;; ---------------------------------------------------------------- tension

(defn- run-by-year
  "Runs `m` (with :input an auxiliary reading `xs` by year, held past its
  end) over the years of xs: {year {stock level}} with each year's levels
  those after its step, so known at that year's end."
  [m xs]
  (let [ys (vec (keys xs)) y0 (first ys) y1 (peek ys)
        r (sf/run m {:from y0 :to (inc y1) :dt 1.0})]
    (into (sorted-map) (for [[i y] (map-indexed vector ys)]
                         [y (update-vals (:stocks r) #(nth % (inc i)))]))))

(defn progress
  "{year {:perceived :expected :tension}} from `segments` of a log income
  index: per segment, perceived progress the TREND of income (tau-p,
  tau-h, starting at no growth), expected progress perceived progress
  smoothed over tau-e, the tension expected less perceived; each read
  after the year's step, the first :burn years of a segment left out."
  [segments opts]
  (let [{:keys [tau-p tau-h tau-e burn]} (merge defaults opts)]
    (into (sorted-map)
          (for [seg segments :when (> (count seg) burn)
                :let [xs (into (sorted-map) (for [[y l] seg] [y (Math/exp l)]))
                      at (fn [t] (val (or (first (rsubseq xs <= (long (Math/floor t)))) (first xs))))
                      m (sf/combine (sf/model {:aux {:income (fn [v] (at (v :t)))}})
                                    (sf/trend :perceived :income tau-p tau-h 0.0)
                                    (sf/smooth :expected :perceived tau-e 0.0))
                      levels (run-by-year m xs)]
                [y lv] (drop burn levels)
                :let [p (:perceived-perceived lv) r (:perceived-reference lv)
                      perceived (/ (- p r) (* r tau-h))
                      expected (:expected lv)]]
            [y {:perceived perceived :expected expected :tension (- expected perceived)}]))))

(defn trust
  "{year trust}: a stock smoothed over tau-trust toward `target` ({year
  target}), from its first target in equilibrium; where the target is
  unknown it holds."
  [target opts]
  (let [{:keys [tau-trust to]} (merge defaults opts)]
    (if (empty? target)
      {}
      (let [y0 (first (keys target)) y1 (or to (last (keys target)))
            m (sf/combine (sf/model {:aux {:target (fn [v] (or (get target (long (v :t))) (v :trust)))}})
                          (sf/smooth :trust :target tau-trust))]
        (update-vals (run-by-year m (into (sorted-map) (for [y (range y0 (inc y1))] [y 0.0]))) :trust)))))

;; ---------------------------------------------------------------- scores

(defn zscore
  "{year score}: each value against the mean and deviation of the values
  up to its year (the country's own past, nothing later), once there are
  :min-n; clipped to +-:clip; none where the values do not vary."
  [xs opts]
  (let [{:keys [min-n clip]} (merge defaults opts)]
    (loop [[[y x] & more] (sort-by key xs) n 0 s 0.0 ss 0.0 out (sorted-map)]
      (if (nil? y)
        out
        (let [n (inc n) s (+ s x) ss (+ ss (* x x))
              m (/ s n) vr (when (> n 1) (/ (- ss (* n m m)) (dec n)))
              z (when (and (>= n min-n) vr (> vr 1e-12)) (max (- clip) (min clip (/ (- x m) (Math/sqrt vr)))))]
          (recur more n s ss (cond-> out z (assoc y z))))))))

(defn- mean-of [xs] (let [xs (remove nil? xs)] (when (seq xs) (/ (reduce + xs) (count xs)))))

(defn- combine-scores
  "{year mean of the scores present}."
  [scores]
  (into (sorted-map) (for [y (into (sorted-set) (mapcat keys scores))
                           :let [m (mean-of (map #(get % y) scores))] :when m]
                       [y m])))

(defn trust-target
  "{year target}: the mean of the country's inequality score, negated, and
  its public spending score, where known."
  [inequality spending opts]
  (combine-scores [(update-vals (zscore inequality opts) -) (zscore spending opts)]))

(defn psi
  "{year Psi}: the weighted sum of the MMP, EMP and SFD scores ({:mmp {year
  z} ...}), where all three are known; `weights` default 1 each."
  [{:keys [mmp emp sfd]} weights]
  (let [w #(double (get weights % 1.0))]
    (into (sorted-map) (for [[y a] mmp :let [b (get emp y) c (get sfd y)] :when (and b c)]
                         [y (+ (* (w :mmp) a) (* (w :emp) b) (* (w :sfd) c))]))))

;; ---------------------------------------------------------------- the country

(defn- index-at
  "{year value of the segment's index at year y less at y - k}, within each
  segment."
  [segs k]
  (into (sorted-map) (for [s segs [y x] s :let [b (get s (- y k))] :when b] [y (- x b)])))

(defn- as-known
  "{year f of the latest record known} for years from..to."
  [recs years stale f]
  (into (sorted-map) (for [y years :let [[d v] (latest recs y stale) x (when d (f y d v))] :when (some? x)] [y x])))

(defn country-stocks
  "{year {variable value}} for one country from its records (see the
  namespace): :income, :urban and :debt are vectors of sources for
  chain (income and urban share in logs, debt in % of GDP); :labour-share,
  :working-age (% of population aged 15-64), :tertiary (gross enrolment,
  %), :top10 and :bottom50 (income shares), :spending (government spending,
  % of GDP) and :balance (% of GDP) are record maps. Variables unknown in a
  year are absent. Options: defaults, :from and :to the known years."
  [recs opts]
  (let [o (merge defaults opts)
        {:keys [stale]} o
        inc-segs (chain (:income recs) o)
        all-ys (concat (mapcat keys inc-segs)
                       (for [k [:labour-share :working-age :tertiary :top10 :bottom50 :spending :balance]
                             [_ [y _]] (get recs k)] y)
                       (for [k [:urban :debt] s (get recs k) [_ [y _]] s] y))]
    (if (empty? all-ys)
      {}
      (let [from (or (:from opts) (apply min all-ys)) to (or (:to opts) (apply max all-ys))
            years (range from (inc to))
            o (assoc o :from from :to to)
            inc-segs (chain (:income recs) o)
            pr (progress inc-segs o)
            g5 (index-at inc-segs 5)
            k (fn [key] (get recs key {}))
            ;; the parts, raw
            stagnation (update-vals g5 #(- (/ % 5.0)))
            wage (as-known (k :labour-share) years stale (fn [_ _ v] (some-> (ln v) -)))
            urban (into (sorted-map) (apply concat (chain (:urban recs) o)))
            youth (as-known (k :working-age) years stale
                            (fn [y d _] (some->> (known (k :working-age) (- d 10) y) (- 100.0))))
            tert (as-known (k :tertiary) years stale
                           (fn [y d v] (when-let [a (ln v)] (when-let [b (ln (known (k :tertiary) (- d 5) y))] (- a b)))))
            credentials (into (sorted-map) (for [[y t] tert :let [g (get g5 y)] :when g] [y (- t g)]))
            top (as-known (k :top10) years stale (fn [y d v] (some->> (known (k :top10) (- d 5) y) (- v))))
            debt (into (sorted-map) (apply concat (chain (:debt recs) o)))
            deficit (as-known (k :balance) years stale (fn [_ _ v] (- v)))
            ineq (as-known (k :top10) years stale
                           (fn [y d v] (when-let [b (known (k :bottom50) d y)] (when (and (pos? v) (pos? b)) (Math/log (/ v b))))))
            spend (as-known (k :spending) years stale (fn [_ _ v] v))
            z #(zscore % o)
            parts {"mmp-stagnation" (z stagnation) "mmp-wage" (z wage) "mmp-urban" (z urban) "mmp-youth" (z youth)
                   "emp-credentials" (z credentials) "emp-top" (z top) "sfd-debt" (z debt) "sfd-deficit" (z deficit)}
            sub (fn [prefix] (z (combine-scores (for [[n s] parts :when (.startsWith ^String n prefix)] s))))
            subs {:mmp (sub "mmp-") :emp (sub "emp-") :sfd (sub "sfd-")}
            target (trust-target ineq spend o)
            tr (trust target o)
            ps (psi subs (:weights o {}))
            series (merge parts
                          {"perceived-progress" (update-vals pr :perceived) "expected-progress" (update-vals pr :expected)
                           "tension" (update-vals pr :tension) "trust-target" target "trust" tr
                           "mmp" (:mmp subs) "emp" (:emp subs) "sfd" (:sfd subs) "psi" ps})]
        (into (sorted-map)
              (for [y years
                    :let [row (into {} (for [[n s] series :let [x (get s y)] :when (some? x)] [n (double x)]))]
                    :when (seq row)]
                [y row]))))))

(defn rows
  "[iso year variable value] of a country's stocks, in variables' order,
  unknowns left out."
  [iso stocks]
  (vec (for [[y row] stocks v variables :let [x (get row v)] :when (some? x)] [iso y v x])))
