(ns bnm.horizon-profile
  "Trial T1's horizon profile (bead bnm-3qu.2, docs/T1-STATES.md): for
  each named slow state and the kinds theory ties it to, at lead years
  h = 1..5, a logit of the kind's onset in year t + h exactly (not the
  year node's window), on the state as known at the origin t (the end of
  year t), clustered by year (bnm.relation-diag/clustered-test, the
  outcome years as clusters). The hypothesis (docs/LIT-NEXT-STEPS.md,
  ranked trial 1): slow stocks' skill rises with lead time, fast
  triggers' falls.

  Two offsets, on the same rows:
  - base: the year node's forecast at t from a run without relations
    (t44-world-norel). That forecast is for an onset within 24 months,
    (t, t + 2]; it enters as the yearly hazard a constant hazard implies
    (annual-logit) at every h, the same offset at h = 1 and h = 5. So
    beyond h = 2 the offset is an extrapolation, and the coefficient
    there is the state's increment over a stale base, not over a base for
    that year. forecasts.tsv also holds the quarter nodes (lead: months
    4-12; w2: months 13-24, year t + 2 exactly) and the half-decade node
    (only every fifth year end): none gives a base for t + 3..5, and
    mixing nodes across h would change the offset with h. The increment.
  - none: each kind's own rate in the rows: the total effect (no
    history, no gauges), what the state says on its own. The difference
    between the two is attribution against increment
    (docs/LIT-NEXT-STEPS.md, 3b).

  Each state is also read at another country (the next in sorted order
  of those with the state that year): the placebo, on the same rows.

  The pooled slope test, specified before it is run: per state, its
  kinds' rows pooled (one coefficient), its fitted coefficient per SD at
  each h (the table's pooled coef); the state's slope is the mean
  at h = 4, 5 less the mean at h = 1, 2; the statistic is the mean over
  states of slope x direction (the expected sign, times +1 for a slow
  state, -1 for a fast one). Its null: country labels permuted within
  each state's countries (each country reads another's series whole), the
  statistic recomputed, one-sided p = (1 + #{null >= observed}) / (1 +
  draws); and, for the record, the statistic on the placebo reads.

  Design years only: origins from 1960, and no outcome after `to` (2002),
  so at lead h the last origin is 2002 - h.

    jolt -A:tools -M -m bnm.horizon-profile [--run t44-world-norel] [--to 2002]
      [--draws 200] [--seed 1] [--out data/runs/sequencing/horizon-t1-design.tsv]"
  (:require [augur.logit :as lg]
            [augur.math :as m]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.relation-diag :as rd]
            [bnm.states :as states]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- the named states -----------------------------------------------------------

(def named
  "The theory-led list (docs/T1-STATES.md): each state's kinds, expected
  sign, speed (slow: skill expected to rise with lead; fast: to fall) and
  expected lead in years."
  [{:name "credit-gap" :kinds ["banking_crisis" "growth_collapse"] :sign 1 :speed :slow :lead [2 5]}
   {:name "keen-debt-gap" :kinds ["banking_crisis" "sovereign_default" "growth_collapse" "fiscal_consolidation"] :sign 1 :speed :slow :lead [3 5]}
   {:name "debt-service" :kinds ["banking_crisis" "growth_collapse"] :sign 1 :speed :fast :lead [1 2]}
   {:name "food-import-exposure" :kinds ["protest_surge" "food_price_shock"] :sign 1 :speed :fast :lead [1 1]}
   {:name "youth-bulge" :kinds ["protest_surge" "violence_surge" "conflict_onset"] :sign 1 :speed :slow :lead [3 10]}
   {:name "elite-overproduction" :kinds ["protest_surge" "violence_surge" "coup"] :sign 1 :speed :slow :lead [5 10]}
   {:name "technology-gap" :kinds ["conflict_onset" "coup"] :sign 1 :speed :slow :lead [5 10]}
   {:name "fuel-price-gap" :kinds ["growth_collapse" "sovereign_default" "currency_crisis"] :sign 1 :speed :slow :lead [2 4]}
   {:name "reserves" :kinds ["currency_crisis" "sovereign_default"] :sign -1 :speed :fast :lead [1 2]}
   {:name "anocracy" :kinds ["coup" "conflict_onset" "violence_surge"] :sign 1 :speed :slow :lead [2 5]}])

(defn direction
  "+1 when the state's coefficient should grow in its expected sign with
  lead (a slow state), -1 when it should shrink (a fast one)."
  [{:keys [sign speed]}]
  (* (long sign) (if (= speed :slow) 1 -1)))

;; --- reading ---------------------------------------------------------------------

(defn as-known
  "{year value} for each year end in `years` from `recs` ({data-year
  [year-known value]}): the latest data year published by then, no older
  than `max-age` years; nothing published later is read."
  [recs years max-age]
  (let [by-known (sort-by (comp first val) recs)]
    (into (sorted-map)
          (for [y years
                :let [ds (keep (fn [[d [k x]]] (when (and (<= k y) (some? x)) [d x])) by-known)
                      [d x] (when (seq ds) (apply max-key first ds))]
                :when (and d (>= d (- y max-age)))]
            [y x]))))

(defn outcome
  "Whether `unit` has an onset in year t + h, seen from the end of year t:
  months (12(t+h-1)+11, 12(t+h)+11]; nil where the catalogue does not
  cover them or the unit is not at risk (a crisis under way between the
  origin's year and the window), as the lattice's target
  (bnm.lattice-banking/onset-in?)."
  [cat unit t h]
  (let [o (+ (* 12 t) 11)]
    (#'lb/onset-in? cat unit o (+ o (* 12 (dec h))) (+ o (* 12 h)))))

(defn annual-logit
  "The log odds of an onset within a year, from p for a window of
  `window` months, under a constant hazard."
  ^double [p window]
  (let [p (max 1e-6 (min (- 1.0 1e-6) (double p)))
        q (- (Math/expm1 (* (/ 12.0 window) (Math/log1p (- p)))))]
    (Math/log (/ q (- 1.0 q)))))

;; --- rows and fits ----------------------------------------------------------------

(defn rows
  "Logit rows for one kind at lead `h`: for each cell [country origin-year]
  with an origin from `from` and its outcome year t + h no later than
  `to`, where the outcome, the offset and the state (read at the origin)
  are known."
  [{:keys [cells outcome offset read h from to kind]}]
  (vec (for [[c t] cells
             :when (and (>= t from) (<= (+ t h) to))
             :let [o (offset c t)]
             :when (some? o)
             :let [y (outcome c t h)]
             :when (some? y)
             :let [x (read c t)]
             :when (some? x)]
         {:y y :o (double o) :w 1.0 :g (+ t h) :t t :c c :kind kind :x [1.0 (double x)]})))

(defn total-offsets
  "The rows with each kind's offset its own rate in them (log odds)."
  [rows]
  (let [rate (into {} (for [[k rs] (group-by :kind rows)
                            :let [n (count rs) e (count (filter :y rs))
                                  p (max 1e-4 (min (- 1.0 1e-4) (/ (double e) n)))]]
                        [k (Math/log (/ p (- 1.0 p)))]))]
    (mapv #(assoc % :o (rate (:kind %))) rows)))

(defn fit
  "bnm.relation-diag/clustered-test on the rows, with :z its signed root."
  [rows]
  (let [r (rd/clustered-test rows)]
    (assoc r :z (* (if (neg? (:coef r)) -1.0 1.0) (Math/sqrt (max 0.0 (:lr r)))))))

(defn coef
  "The fitted coefficient per SD of the input (as
  bnm.relation-diag/lr-test's), from the one fit with the input."
  ^double [rows]
  (let [rows (vec rows)
        b (lg/fit rows 2 {:prior 1e-4})
        xs (map #(nth (:x %) 1) rows)
        n (max 1 (count rows))
        mx (/ (reduce + xs) n)
        sd (Math/sqrt (/ (reduce + (map #(* (- % mx) (- % mx)) xs)) n))]
    (* (aget ^doubles b 1) sd)))

(defn state-slope
  "{h coef} -> mean at h 4, 5 less mean at h 1, 2."
  [cs]
  (- (/ (+ (cs 4) (cs 5)) 2.0) (/ (+ (cs 1) (cs 2)) 2.0)))

(defn slope-stat
  "The mean over states of their slope times their direction."
  [coefs dirs]
  (let [ss (for [[s cs] coefs] (* (dirs s) (state-slope cs)))]
    (/ (reduce + ss) (count ss))))

(defn placebo-read
  "The state of `panel` ({iso {year value}}) read at another country: the
  next, in sorted order, of those with the state that year."
  [panel]
  (let [by-year (reduce (fn [m [c byy]] (reduce (fn [m [y _]] (update m y (fnil conj (sorted-set)) c)) m byy)) {} panel)
        next-of (into {} (for [[y cs] by-year :when (> (count cs) 1)
                               :let [v (vec cs)]]
                           [y (zipmap v (conj (subvec v 1) (v 0)))]))]
    (fn [c t] (when-let [c' (get-in next-of [t c])] (get-in panel [c' t])))))

(defn permuted
  "`read` with country labels shuffled among `cs`: each reads another's
  series whole."
  [read cs g]
  (let [cs (vec cs)
        a (double-array (range (count cs)))
        _ (doseq [i (reverse (range 1 (count cs)))]
            (let [j (long (Math/floor (* (m/next-double! g) (inc i))))
                  t (aget a i)]
              (aset a i (aget a j)) (aset a j t)))
        to (zipmap cs (map #(cs (long %)) a))]
    (fn [c t] (when-let [c' (to c)] (read c' t)))))

;; --- the states from disk -------------------------------------------------------

(def years (range 1950 2011))

(defn- table->recs
  "{var {iso {year [year value]}}} from stock tables (by the year known)."
  [paths]
  (let [st (states/read-states paths)]
    (update-vals st (fn [by-iso] (update-vals by-iso (fn [byy] (into {} (for [[y x] byy] [y [y x]]))))))))

(defn read-slow-annual
  "{var {iso {data-year [year-known value]}}} of annual slow-store series."
  [path vars]
  (let [vars (set vars) acc (java.util.HashMap.)]
    (with-open [r (io/reader path)]
      (doseq [line (line-seq r)
              :let [[unit v f pend avail value] (str/split line #"\t" -1)]
              :when (and (vars v) (= f "a"))
              :let [x (parse-double value)]
              :when x]
        (.put acc [v unit] (assoc (or (.get acc [v unit]) {}) (parse-long (subs pend 0 4)) [(parse-long (subs avail 0 4)) x]))))
    (reduce (fn [m [[v u] recs]] (assoc-in m [v u] recs)) {} acc)))

(defn- known-panel
  "{iso {year value}} as known at each year end."
  [by-iso max-age]
  (into {} (for [[iso recs] by-iso :when (not= iso "WLD") :let [k (as-known recs years max-age)] :when (seq k)] [iso k])))

(defn- combine
  "{iso {year (f a b)}} where both panels have the year."
  [pa pb f]
  (into {} (for [[iso ya] pa :let [yb (get pb iso)] :when yb
                 :let [z (into (sorted-map) (for [[y a] ya :let [b (get yb y)] :when (some? b)] [y (f a b)]))]
                 :when (seq z)]
             [iso z])))

(defn state-panels
  "{state {iso {year value}}}: each named state as known at each year end
  (docs/T1-STATES.md for the definitions and conventions)."
  []
  (let [stocks (table->recs (map #(str "data/derived/stocks/" % ".csv") ["keen" "tension" "technology" "cycles" "credit"]))
        slow (read-slow-annual "data/slow/series.tsv" ["debt/reserves_months" "regime/electoral_democracy"
                                                      "food/imports_share" "people/working_age_share"])
        sp (fn [v age] (known-panel (get stocks v) age))
        world (fn [v] (as-known (get-in stocks [v "WLD"]) years 1))
        grain-gap (let [ws (map world ["cycles/wheat-price-gap" "cycles/maize-price-gap" "cycles/rice-price-gap"])]
                    (into (sorted-map) (for [y years :let [xs (keep #(get % y) ws)] :when (= 3 (count xs))] [y (/ (reduce + xs) 3.0)])))
        wa (get slow "people/working_age_share")
        ;; the youth proxy: the share outside working age ten years
        ;; before (the children then are the 10-24s now), known when the
        ;; data year's own figure is (tension's convention)
        youth (into {} (for [[iso recs] wa]
                         [iso (into {} (for [[d [k _]] recs :let [old (get recs (- d 10))] :when old]
                                         [d [k (- 100.0 (second old))]]))]))]
    {"credit-gap" (sp "credit/gap" 0)
     "keen-debt-gap" (sp "keen/d-gap" 0)
     "debt-service" (combine (sp "keen/r" 0) (sp "keen/d" 0) *)
     "food-import-exposure" (into {} (for [[iso byy] (known-panel (get slow "food/imports_share") 3)
                                           :let [z (into (sorted-map) (for [[y s] byy :let [g (grain-gap y)] :when g] [y (* (/ s 100.0) g)]))]
                                           :when (seq z)]
                                       [iso z]))
     "youth-bulge" (known-panel youth 3)
     "elite-overproduction" (sp "tension/emp" 0)
     "technology-gap" (sp "technology/gap-historical" 0)
     "fuel-price-gap" (sp "cycles/fuel-exposure" 0)
     "reserves" (update-vals (known-panel (get slow "debt/reserves_months") 3)
                             (fn [byy] (update-vals byy #(Math/log (max 0.1 %)))))
     "anocracy" (update-vals (known-panel (get slow "regime/electoral_democracy") 3)
                             (fn [byy] (update-vals byy #(* 4.0 % (- 1.0 %)))))}))

;; --- the run ----------------------------------------------------------------------

(defn- cells-of
  "{kind {:cells [[c t]] :base (fn [c t])}} from a run's year node."
  [run kind]
  (let [yr (rd/read-year run kind)]
    {:cells (for [[c byo] yr [o _] byo :when (= 11 (mod o 12))] [c (quot o 12)])
     :base (fn [c t] (when-let [[p _] (get-in yr [c (+ (* 12 t) 11)])] (annual-logit p 24)))}))

(defn coverage
  "{:countries :rows :from :to} of a state on the design years 1960-to."
  [panel to]
  (let [cy (for [[iso byy] panel [y _] byy :when (<= 1960 y to)] [iso y])]
    {:countries (count (distinct (map first cy))) :rows (count cy)
     :from (when (seq cy) (apply min (map second cy))) :to (when (seq cy) (apply max (map second cy)))}))

(defn -main [& args]
  (let [{:keys [run to draws seed out]} (into {} (for [[a v] (partition 2 args)] [(keyword (subs a 2)) v]))
        run (or run "t44-world-norel")
        to (or (some-> to parse-long) 2002)
        draws (or (some-> draws parse-long) 200)
        seed (or (some-> seed parse-long) 1)
        out (or out "data/runs/sequencing/horizon-t1-design.tsv")
        cats (kinds/catalogues)
        panels (state-panels)
        ks (distinct (mapcat :kinds named))
        bases (into {} (for [k ks] [k (cells-of run k)]))
        buf (StringBuilder.)
        say (fn [s] (println s) (flush) (.append buf (str s "\n")))
        ;; the rows of a state, a kind, h and a reader
        ;; each kind's rows at h with the outcome and the base, once; a
        ;; state's are those where it is known
        frame (memoize (fn [kind h]
                         (let [{:keys [cells base]} (bases kind)]
                           (rows {:cells cells :outcome (fn [c t h] (outcome (cats kind) c t h)) :offset base
                                  :read (constantly 0.0) :h h :from 1960 :to to :kind kind}))))
        kind-rows (fn [read kind h]
                    (vec (for [r (frame kind h) :let [x (read (:c r) (:t r))] :when (some? x)]
                           (assoc r :x [1.0 (double x)]))))
        h-range (range 1 6)
        pooled (atom {})
        placebo-pooled (atom {})]
    (say (str "# horizon profile, run " run ", origins 1960-" (- to 1) ", outcomes in year t+h <= " to))
    (say "# offset base: the year node's 24-month forecast at t as a yearly hazard, the same at every h (an extrapolation past h = 2); none: each kind's own rate")
    (say "# coverage, design years: state\tcountries\tcountry-years\tfrom\tto")
    (doseq [{:keys [name]} named] (let [c (coverage (panels name) to)] (say (format "#  %s\t%d\t%d\t%s\t%s" name (:countries c) (:rows c) (:from c) (:to c)))))
    (say "state\tkind\th\toffset\tn\tpos\tcoef\tlr\tz\tplacebo-coef\tplacebo-lr\tplacebo-z")
    (doseq [{:keys [name kinds]} named
              :let [panel (panels name)
                    real (fn [c t] (get-in panel [c t]))
                    fake (placebo-read panel)]
              h h-range]
        (let [pairs (for [k kinds
                          :let [a (kind-rows real k h)
                                b (into {} (for [r (kind-rows fake k h)] [[(:c r) (:t r)] r]))]
                          r a :let [rb (b [(:c r) (:t r)])] :when rb]
                      [r rb])
              a (mapv first pairs) b (mapv second pairs)]
          (doseq [[label off] [["base" identity] ["none" total-offsets]]
                  [kd sel] (concat (map (fn [k] [k #(= k (:kind %))]) kinds) [["pooled" (constantly true)]])
                  :let [ra (vec (filter sel (off a))) rb (vec (filter sel (off b)))]
                  :when (>= (count (filter :y ra)) 5)]
            (let [fa (fit ra) fb (fit rb)]
              (when (= kd "pooled")
                (swap! pooled assoc-in [label name h] (:coef fa))
                (swap! placebo-pooled assoc-in [label name h] (:coef fb)))
              (say (format "%s\t%s\t%d\t%s\t%d\t%d\t%+.3f\t%.2f\t%+.2f\t%+.3f\t%.2f\t%+.2f"
                           name kd h label (:n fa) (:pos fa) (:coef fa) (:lr fa) (:z fa) (:coef fb) (:lr fb) (:z fb)))))))
      ;; the slope test
      (let [dirs (into {} (for [s named] [(:name s) (direction s)]))
            g (m/rng seed)
            null-of (fn [label]
                      (vec (for [_ (range draws)]
                             (let [coefs (into {} (for [{:keys [name kinds]} named
                                                        :let [panel (panels name)
                                                              read (permuted (fn [c t] (get-in panel [c t])) (sort (keys panel)) g)]]
                                                    [name (into {} (for [h h-range
                                                                         :let [rs (vec (mapcat #(kind-rows read % h) kinds))
                                                                               rs (if (= label "none") (total-offsets rs) rs)]]
                                                                     [h (if (seq rs) (coef rs) 0.0)]))]))]
                               (slope-stat coefs dirs)))))]
        (say "# slope test: per state, the pooled coef at h 4,5 less at h 1,2; the statistic the mean over states of that times direction")
        (say "slope\tstate\toffset\tdirection\tslope\tplacebo-slope\th1\th2\th3\th4\th5")
        (doseq [label ["base" "none"] {:keys [name]} named
                :let [cs (get-in @pooled [label name]) pc (get-in @placebo-pooled [label name])]
                :when (= 5 (count cs))]
          (say (format "slope\t%s\t%s\t%+d\t%+.3f\t%+.3f\t%+.3f\t%+.3f\t%+.3f\t%+.3f\t%+.3f"
                       name label (dirs name) (state-slope cs) (if (= 5 (count pc)) (state-slope pc) Double/NaN)
                       (cs 1) (cs 2) (cs 3) (cs 4) (cs 5))))
        (doseq [label ["base" "none"]
                :let [obs (slope-stat (get @pooled label) dirs)
                      plc (slope-stat (get @placebo-pooled label) dirs)
                      null (null-of label)
                      p (/ (+ 1.0 (count (filter #(>= % obs) null))) (+ 1.0 draws))
                      mean (/ (reduce + null) (count null))
                      sd (Math/sqrt (/ (reduce + (map #(* (- % mean) (- % mean)) null)) (count null)))]]
          (say (format "pooled-slope\t%s\tstatistic %+.4f\tplacebo %+.4f\tnull mean %+.4f sd %.4f\tp %.3f (%d permutations of country labels)"
                       label obs plc mean sd p draws))))
    (io/make-parents out)
    (spit out (str buf))
    (println "wrote" out)))
