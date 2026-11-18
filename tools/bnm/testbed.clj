(ns bnm.testbed
  "The synthetic crisis testbed (docs/TESTBED.md): a world with known truth,
  read through the real signal layer (bnm.signals declarations) and the
  real lattice (bnm.lattice/run), scored as the base is scored, so a design
  can be tried on many worlds in minutes before it is tried once on the
  data.

    jolt -A:tools -M -m bnm.testbed [--scenario credit-waves] [--seeds 1,2,3]
                                    [--countries 60] [--years 100] [--set '{:late-entry :prior}']"
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [bnm.signals :as sg]
            [bnm.stat :as stat]
            [bnm.stocks :as sk]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def epoch "The world's first year." 1900)

(def defaults
  "The world's settings; a scenario overrides some."
  {:seed 1 :countries 60 :regions 6 :years 100
   :stocks ["credit" "house" "reserves" "tot"]
   :lag-months 6          ; a year's value is published this long after it ends
   :spell 3               ; a crisis lasts this many years: no new onset meanwhile
   :phi 0.8               ; each country's latent stretch is AR(1)
   :world-phi 0.9         ; the world cycle of each stock
   :world-lambda 0.6      ; how much the world cycle moves each country
   :noise-df 4            ; observation noise is Student t (heavy tails)
   :noise-scale [0.5 2.0] ; each country's own noise level, drawn uniformly
   :base -3.6             ; log odds of a crisis with no signal
   :strength 1.2          ; per unit of the planted turn
   :wave-boost 0.8        ; added in a world wave's years
   :refractory 0.0        ; added in the five years after the country's own crisis
   :scenario :credit-waves})

(def scenarios
  "Each isolates a failure the reports or the base runs named."
  {:credit-waves {:active (constantly "credit")}
   :commodity-bust {:active (constantly "tot") :fall true}
   :mixed-channels {:active (fn [stocks y] (stocks (mod (quot y 20) (count stocks))))}
   :late-data {:active (constantly "credit") :published-from 60}
   :synchronized {:active (constantly "credit") :world-lambda 1.3}
   :null {:active (constantly "credit") :strength 0.0 :wave-boost 0.0}
   ;; the driving stock observed at 30% of countries (JST's share is 18 of 130)
   :partial-coverage {:active (constantly "credit") :coverage 0.3}})

(defn- student-t [g df]
  (let [z (m/normal! g)
        chi (reduce + (repeatedly df #(let [x (m/normal! g)] (* x x))))]
    (/ z (Math/sqrt (/ chi df)))))

(defn- turn
  "The planted turn of a latent path at year index i: how far the stock
  was stretched past 1 the year before, when it moved back this year; 0
  otherwise. `fall` reads the path's negative (a terms-of-trade bust)."
  [path i fall]
  (let [b (fn [j] (let [x (get path j 0.0)] (if fall (- x) x)))]
    (if (and (pos? i) (> (b (dec i)) 1.0) (< (b i) (b (dec i))))
      (- (b (dec i)) 1.0)
      0.0)))

(defn- settings
  "The world's settings: the defaults, the scenario's, then the caller's."
  [opts]
  (let [o (merge defaults opts)
        sc (merge (scenarios (:scenario o)) (select-keys opts [:strength :wave-boost :world-lambda]))]
    (merge o (select-keys sc [:strength :wave-boost :world-lambda]) {:sc sc})))

(defn- world-cycles
  "Each stock's world cycle, AR(1) at :world-phi."
  [g {:keys [stocks years world-phi]}]
  (into {} (for [k stocks] [k (vec (reductions (fn [c _] (+ (* world-phi c) (* 0.45 (m/normal! g)))) 0.0 (range (dec years))))])))

(defn- latent-paths
  "Each country's latent stretch of each stock: AR(1) at :phi, moved by the
  world cycle."
  [g cs cycle {:keys [stocks years phi world-lambda]}]
  (into {} (for [c cs]
             [c (into {} (for [k stocks]
                           [k (vec (reductions (fn [b i] (+ (* phi b) (* world-lambda (get-in cycle [k i])) (* 0.5 (m/normal! g))))
                                               0.0 (range 1 years)))]))])))

(defn- noise-scales
  "Each country's own observation noise level, uniform over :noise-scale."
  [g cs {[lo hi] :noise-scale}]
  (into {} (for [c cs] [c (+ lo (* (- hi lo) (m/next-double! g)))])))

(defn- observations
  "{country {stock (sorted-map year x)}}: the latent path plus a slow trend
  plus heavy-tailed noise at the country's own level; a scenario's
  :published-from leaves the driving stock's early years out."
  [g cs latent scale {:keys [stocks years noise-df sc]}]
  (let [{:keys [active published-from]} sc
        driver (active (vec stocks) 0)]
    (into {} (for [c cs]
               [c (into {} (for [k stocks]
                             [k (into (sorted-map)
                                      (for [i (range years)
                                            :when (or (nil? published-from) (not= k driver) (>= i published-from))]
                                        [(+ epoch i) (+ (* 0.02 i) (get-in latent [c k i]) (* (scale c) 0.3 (student-t g noise-df)))]))]))]))))

(defn- publish
  "The slow table's shape: each country's observed stocks as series, each
  year's value published :lag-months after it ends; a scenario's
  :coverage keeps the driving stock at that share of countries only."
  [cs observed {:keys [stocks countries lag-months sc]}]
  (let [{:keys [active coverage]} sc
        driver (active (vec stocks) 0)
        covered (set (take (long (Math/round (* (or coverage 1.0) countries))) cs))]
    (into {} (for [c cs k stocks :let [ys (get-in observed [c k])]
                   :when (and (seq ys) (or (nil? coverage) (not= k driver) (covered c)))]
               [[c k] (sk/series (for [[y x] ys] [(+ (* 12 y) 11) (+ (* 12 y) 11 lag-months) x]))]))))

(defn- draw-onsets
  "[hazard onsets]: each country's yearly hazard (`hz` of country, year
  index, a crisis in the last five years) and its onsets, none within the
  spell after one."
  [g cs hz {:keys [years spell]}]
  (reduce (fn [[hazard onsets] c]
            (loop [i 0 last nil hazard hazard os #{}]
              (if (= i years)
                [hazard (assoc onsets c os)]
                (let [at-risk (or (nil? last) (>= (- i last) spell))
                      p (hz c i (and last (<= (- i last) 5)))
                      on (and at-risk (< (m/next-double! g) p))]
                  (recur (inc i) (if on i last) (assoc hazard [c (+ epoch i)] p) (cond-> os on (conj (+ epoch i))))))))
          [{} {}] cs))

(defn world
  "A synthetic world (docs/TESTBED.md): {:countries :parent :db {[unit
  stock] series} :observed {unit {stock {year x}}} :latent :hazard {[unit
  year] p} :onsets {unit #{year}}} with years as calendar years from
  `epoch`. The hazard of year y reads the turn at y - 2, the latest a
  December forecast can see through the publication lag."
  [opts]
  (let [{:keys [seed countries regions stocks base strength wave-boost refractory sc] :as o} (settings opts)
        {:keys [active fall]} sc
        g (m/rng seed)
        cs (mapv #(format "c%03d" %) (range countries))
        rs (mapv #(str "r" %) (range regions))
        parent (merge (into {} (map-indexed (fn [i c] [c (rs (mod i regions))]) cs)) (zipmap rs (repeat "world")))
        cycle (world-cycles g o)
        waves (set (for [i (range (:years o)) :when (> (get-in cycle [(active (vec stocks) i) i]) 1.0)] i))
        latent (latent-paths g cs cycle o)
        scale (noise-scales g cs o)
        observed (observations g cs latent scale o)
        hz (fn [c i recent?]
             (m/expit (+ base
                         (* strength (turn (get-in latent [c (active (vec stocks) i)]) (- i 2) fall))
                         (if (waves i) wave-boost 0.0)
                         (if recent? refractory 0.0))))
        [hazard onsets] (draw-onsets g cs hz o)]
    {:opts (dissoc o :sc) :countries cs :regions rs :parent parent :db (publish cs observed o) :observed observed :latent latent
     :hazard hazard :onsets onsets :waves (set (map #(+ epoch %) waves))}))

(defn at-risk?
  "Whether `unit` could start a crisis in `year`: no onset within the spell before it."
  [w unit year]
  (let [spell (:spell (merge defaults (:opts w)))]
    (not-any? #(and (< % year) (< (- year %) spell)) (get-in w [:onsets unit] #{}))))

;; --- the lattice over the world --------------------------------------------------

(defn decls
  "The node's signals over the world's stocks, declared as on the data:
  each stock's stretch, its turn, and its change over a year. `mode`
  :raw (the default) reads the published values, :filtered their
  Kalman-filtered path (bnm.signals/kalman-level), :both each, :long
  the stretch and turn against a long baseline (60 years, at least 10)
  in place of the ten-year window, so a slow boom is not absorbed into its
  own baseline."
  ([stocks] (decls stocks :raw))
  ([stocks mode]
   (let [st (if (= :long mode) {:window 60 :min 10 :cap 5.0 :eps 1e-9} {:window 10 :min 6 :cap 5.0 :eps 1e-9})
         raw (fn [k] [[(str k "-stretch") [:stretch st]] [(str k "-turn") [:turn (assoc st :threshold 1.0)]]])
         flt (fn [k] [[(str k "-fstretch") [:filtered [:stretch st]]] [(str k "-fturn") [:filtered [:turn (assoc st :threshold 1.0)]]]])]
     (vec (for [k stocks
                [n rd] (concat (case mode (:raw :long) (raw k) :filtered (flt k) :both (concat (raw k) (flt k)))
                               [[(str k "-change") [:change 12]]])]
            {:name (keyword n) :var k :read rd :domain (keyword k)})))))

(defn spec
  "A lattice spec over world `w`: a year scale over countries, regions and
  the world, the declared signals standardized by everything before each
  origin (bnm.signals/running-z), the target an onset in the year ahead
  while the country is at risk, known a year after the window closes."
  [w extra]
  (let [{:keys [countries regions parent db opts]} w
        {:keys [years stocks]} (merge defaults opts)
        ds (decls stocks (:signals opts :raw))
        {:keys [row groups]} (sg/assemble db ds)
        start (* 12 epoch) end (+ (* 12 (+ epoch years -1)) 11)
        origins (range (+ start 11) (inc end) 12)
        table (into {} (for [t origins c countries] [[c t] (row c t)]))
        ;; :guard (docs/TESTBED.md, Farrington's down-weighted baseline): the
        ;; standardizer's baseline leaves out country-years in or within two
        ;; years after an onset, once known (24 months after their origin);
        ;; a row's sample time is when it may enter the baseline
        crisis-near? (fn [c y] (some #(<= 0 (- y %) 2) (get-in w [:onsets c] #{})))
        samples (if (:guard opts)
                  (for [[[c t] v] table :when (not (crisis-near? c (quot (inc t) 12)))] [(+ t 24) v])
                  (for [[[_ t] v] table] [t v]))
        std (sg/running-z samples {:count-by :values :min-exclusive 20 :stable true})
        members (fn [place] (cond (= "world" place) countries
                                  (some #{place} regions) (filter #(= place (parent %)) countries)
                                  :else [place]))
        width (count ds)]
    (merge {:scales [{:name :year :per 12 :horizon 12 :discount 0.995}]
            :levels [[:country countries] [:region regions] [:world ["world"]]]
            :parent parent
            :start start :end end
            :forget 0.995
            :target (fn [_ place origin]
                      (let [y (quot (inc origin) 12)
                            ys (for [c (members place) :when (at-risk? w c y)] (contains? (get-in w [:onsets c] #{}) y))]
                        (when (seq ys) {:y (boolean (some true? ys)) :known-at (+ origin 24)})))
            :gauges (fn [_ place origin]
                      (let [vs (for [c (members place) :let [v (table [c origin])] :when v] (std origin v))]
                        (if (seq vs) (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs) (vec (repeat width 0.0)))))
            :gauge-width {:year width}
            :gauge-groups {:year groups}}
           extra)))

;; --- scores -------------------------------------------------------------------------

(defn auc
  "The area under the ROC curve of rows {:p :y}: the share of onset-quiet
  pairs ordered, ties counted half."
  [rows]
  (let [pos (map :p (filter :y rows)) neg (vec (sort (map :p (remove :y rows))))
        n (count neg)
        below (fn [p] (loop [lo 0 hi n] (if (< lo hi) (let [mid (quot (+ lo hi) 2)] (if (< (neg mid) p) (recur (inc mid) hi) (recur lo mid))) lo)))
        at-or-below (fn [p] (loop [lo 0 hi n] (if (< lo hi) (let [mid (quot (+ lo hi) 2)] (if (<= (neg mid) p) (recur (inc mid) hi) (recur lo mid))) lo)))]
    (when (and (seq pos) (pos? n))
      (/ (reduce + (map #(+ (below %) (* 0.5 (- (at-or-below %) (below %)))) pos)) (* (count pos) n)))))

(defn within-year-auc
  "The AUC within each :year, pooled with each year weighed by its pairs."
  [rows]
  (let [parts (for [[_ rs] (group-by :year rows) :let [a (count (filter :y rs)) b (- (count rs) a)] :when (and (pos? a) (pos? b))]
                [(auc rs) (* a b)])]
    (when (seq parts) (/ (reduce + (map #(apply * %) parts)) (reduce + (map second parts))))))

(defn level-r
  "The correlation of the yearly mean p with the yearly rate, nil when
  either is flat."
  [rows]
  (let [by (sort-by key (group-by :year rows))]
    (stat/pearson-or-nil (map (fn [[_ rs]] (stat/mean (map :p rs))) by)
                         (map (fn [[_ rs]] (stat/mean (map #(if (:y %) 1.0 0.0) rs))) by))))

(defn alert-cost
  "(misses x miss-cost + false alarms) / rows, alerting at p >= threshold."
  [rows threshold miss-cost]
  (/ (reduce + (for [{:keys [p y]} rows :let [alert (>= p threshold)]]
                 (cond (and y (not alert)) miss-cost (and alert (not y)) 1.0 :else 0.0)))
     (count rows)))

(defn molchan-area
  "The area under the Molchan curve (miss rate against the share of rows
  alarmed, alarming the highest p first): 0.5 for a forecast unrelated to
  the outcomes, lower for a better one (Molchan 1991)."
  [rows]
  (let [rs (sort-by (comp - :p) rows) n (count rs) npos (count (filter :y rs))]
    (when (and (pos? n) (pos? npos))
      (let [miss (reductions (fn [m {:keys [y]}] (if y (dec m) m)) npos rs)
            nu (mapv #(/ % (double npos)) miss)]
        (/ (reduce + (map (fn [a b] (/ (+ a b) 2.0)) nu (rest nu))) n)))))

(defn- mean-ll [rows f]
  (stat/mean (map (fn [r] (let [p (Math/max 1e-9 (Math/min (- 1 1e-9) (f r)))] (Math/log (if (:y r) p (- 1.0 p))))) rows)))

(def scored-share
  "The share of a world's years scored, its last ones: the data are scored
  over 2003-2022 after learning from 1870, so the testbed leaves the
  model's warm-up out too."
  0.4)

(defn score
  "A run's year country forecasts scored against the outcomes and the
  world's true hazard (the oracle), over the world's last `scored-share`
  of years."
  [w result]
  (let [{:keys [years]} (merge defaults (:opts w))
        from (+ epoch (long (Math/ceil (* (- 1.0 scored-share) years))))
        rows (vec (for [{:keys [scale place origin p y]} (:forecasts result)
                        :when (and (= :year scale) (some? y) (some #{place} (:countries w)) (>= (quot (inc origin) 12) from))
                        :let [yr (quot (inc origin) 12)
                              true-p (get-in w [:hazard [place yr]])]
                        ;; a window past the world's last year has no truth to score against
                        :when true-p]
                    {:year yr :p p :y y :true-p true-p}))
        rate (/ (count (filter :y rows)) (double (count rows)))
        clim (mean-ll rows (constantly rate))
        dec (let [s (vec (sort-by :p rows)) n (count s)] (for [i (range 10) :let [b (subvec s (quot (* i n) 10) (quot (* (inc i) n) 10))] :when (seq b)] b))]
    {:rows (count rows) :rate rate
     :auc (auc rows) :within-auc (within-year-auc rows) :level-r (level-r rows)
     :brier (stat/mean (map #(let [d (- (if (:y %) 1.0 0.0) (:p %))] (* d d)) rows))
     :log-skill (- (mean-ll rows :p) clim)
     :oracle-log-skill (- (mean-ll rows :true-p) clim)
     :oracle-auc (auc (map #(assoc % :p (:true-p %)) rows))
     :ece (reduce + (for [b dec] (* (/ (count b) (double (count rows)))
                                    (Math/abs (- (stat/mean (map :p b)) (/ (count (filter :y b)) (double (count b))))))))
     :alert-cost (alert-cost rows 0.2 4.0)
     :constant-cost (min (* 4.0 rate) (- 1.0 rate))
     :molchan (molchan-area rows)
     ;; a flat forecast's correlations are noise: its spread beside them
     :p-sd (stat/sd-pop (map :p rows))
     :skill-share (let [o (- (mean-ll rows :true-p) clim)] (when (pos? o) (/ (- (mean-ll rows :p) clim) o)))}))

(defn evaluate
  "World `opts`, run through the lattice with `:lattice` merged into its
  spec, scored."
  [opts]
  (let [w (world opts)]
    (score w (lt/run (spec w (:lattice opts {}))))))

(defn -main [& args]
  (let [o (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        seeds (mapv parse-long (str/split (or (:seeds o) "1,2,3") #","))
        base (cond-> {:scenario (keyword (or (:scenario o) "credit-waves"))}
               (:countries o) (assoc :countries (parse-long (:countries o)))
               (:years o) (assoc :years (parse-long (:years o)))
               (:signals o) (assoc :signals (keyword (:signals o)))
               (:guard o) (assoc :guard (= "true" (:guard o)))
               (:set o) (assoc :lattice (edn/read-string (:set o))))
        ks [:auc :oracle-auc :within-auc :level-r :p-sd :log-skill :oracle-log-skill :skill-share :ece :alert-cost :constant-cost :molchan]
        results (vec (for [s seeds] (evaluate (assoc base :seed s))))]
    (println (str "scenario " (name (:scenario base)) ", signals " (name (:signals base :raw)) ", seeds " (str/join "," seeds) (when (:lattice base) (str ", lattice " (pr-str (:lattice base))))))
    (doseq [k ks]
      (let [xs (keep k results)]
        (println (format "  %-17s mean %+.4f  [%s]" (name k) (stat/mean xs) (str/join " " (map #(format "%.3f" %) xs))))))))
