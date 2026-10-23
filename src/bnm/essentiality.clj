(ns bnm.essentiality
  "Essentiality as expected output at risk (bead bnm-1pq.9; docs/
  ESSENTIALS.md section 7b and section 8 step 8):

    R_i(Y, H) = Σ_disruptions P(disruption | data known by Y) x L_i(H)

  for each HS92 product i, each importing region (EXIOBASE's 49) and the
  world, at H = 3 and 12 months and the steady state. Everything is read
  as known by the year Y (stocks are dated by the year known); BACI's data
  year is Y - 2.

  Disruptions, each a cut of x (default 0.5) of what it reaches:

    supplier c of i   P_c = 1 - (1 - P_event,c)(1 - P_restrict,c,i)
      P_event     the project's own onset forecasts for c (run
                  t44-world-norel: conflict, war, coup, violence onsets),
                  each the year node's 24-month probability at the latest
                  origin by the end of Y as a yearly hazard (`annual`),
                  combined as independent (`union`); a country with no
                  forecast gets the median of those with one. The four
                  kinds overlap (a war onset is mostly a conflict onset),
                  so the union overstates; it is kept for simplicity.
      P_restrict  OECD export restrictions (`restriction-rates`): a
                  product under a measure in c the latest year known gets
                  the tightening rate (introductions and increases on
                  measures in force the year before, pooled); any other
                  product c's entry rate (new country-product listings
                  per unrestricted country-code-year in the inventory,
                  shrunk to the pooled rate). The inventory covers raw
                  materials only, so the entry rate is measured there and
                  applied to every product of c.
    strait s on region r's imports
      q_s         closures per strait-year, gamma-Poisson (`strait-rate`):
                  prior Gamma(0.5, 25 strait-years), mean 0.02 a year (a
                  closure every 50 strait-years, 90% range about 0.0001-
                  0.08), updated by the PortWatch disruptions on straits
                  known by Y (2019 on); each cuts d (default 0.5, PortWatch
                  saw transits fall 30-85%) of the imports routed through
                  it (bnm.chokepoints exposure), less what is rerouted or
                  replaced: `reallocation` with the strait's share of the
                  world's trade in the product in place of a supplier's.

  The loss of one disruption (`product-risk`), per importing region r:

    T_rc        r's imports of i from c (`slices`): BACI's bilateral
                flows from c to r's countries where essentiality-trade
                carries them (the essentials codes of scripts/baci.py),
                else r's imports x c's share of world exports, flagged
                (a world share credits Indonesia with 87% of every
                importer's lignite, Czech imports from Germany included);
                none from c's own region
    rho_c       import reallocation (`reallocation`): the others' spare
                capacity e_H (1 - s_c) over the lost supply x s_c, in
                [0, 1] (Moll, Schularick & Zachmann 2023: Germany replaced
                most Russian gas within a year by reallocating imports);
                e_H a prior: 0.05 at 3 months, 0.15 at 12, 0.30 steady
    shortfall   x (1 - rho_c) T_rc
    loss        lambda_H(f) x f x U_r, f the shortfall over r's use of
                i's EXIOBASE parent in the trade data (at most 1), U_r
                r's use of the parent in the loss model (EXIOBASE 2011),
                lambda the value added lost in every region per unit of
                use lost, from bnm.supply outage (steady) and outage-path
                (3 and 12 months), interpolated between the cut sizes
                computed (`interp-loss`). The loss of a cut f in r is
                r's share of the world's use times the world's loss at
                f: a region the loss model sees using little of the
                parent loses little, however much the trade data say it
                imports under the parent's codes (Indonesian \"lignite\"
                270210 bought by China, whose EXIOBASE lignite use is EUR
                1.4 M, had taken the world multiplier on 10 bn USD)

  Region r's R sums its disruptions' losses (wherever they fall); the
  world's is the sum over regions. Each region's cut is solved alone and
  the losses added: simultaneous cuts in several regions interact (the
  outage is convex), which this leaves out.

  Ranking and the rising-essential rule (section 7c, `flag?`): rising (the
  rise score's top 10%, bnm.essentials/rise), linked (a growing or large
  node downstream), concentrated (exporter or stage HHI at least 0.25, or
  up 0.05 in five years), and R up against its own past (`risk-up?`)."
  (:require [bnm.csv :as csv]
            [bnm.supply :as sup]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ---------------------------------------------------------------- hazards

(defn annual
  "The yearly hazard of a constant hazard whose probability over `months`
  is p."
  ^double [p months]
  (let [p (double p)]
    (if (<= p 0.0) 0.0 (- 1.0 (Math/pow (- 1.0 (min p 1.0)) (/ 12.0 (double months)))))))

(defn over-months
  "The probability within `months` of a yearly hazard `a`."
  ^double [a months]
  (- 1.0 (Math/pow (- 1.0 (double a)) (/ (double months) 12.0))))

(defn union
  "1 - Π(1 - p): at least one of independent events."
  ^double [ps]
  (- 1.0 (reduce (fn [acc p] (* acc (- 1.0 (double p)))) 1.0 ps)))

(defn known-by
  "The rows known by year `k` (:known <= k)."
  [rows k]
  (filter #(<= (long (:known %)) (long k)) rows))

(def event-kinds
  "The onset kinds whose forecasts are a supplier's event hazard."
  ["conflict_onset" "war_onset" "coup" "violence_surge"])

(defn load-forecasts
  "{kind {iso [[origin p] ...]}} from a run's <kind>/forecasts.tsv year
  nodes (origin the month index of the year end, p the probability of an
  onset within 24 months), countries only, sorted by origin."
  [dir kinds]
  (into {} (for [k kinds]
             [k (with-open [r (io/reader (str dir "/" k "/forecasts.tsv"))]
                  (->> (line-seq r)
                       (keep (fn [l] (let [[s iso o p] (str/split l #"\t")]
                                       (when (and (= s "year") (= 3 (count iso)))
                                         [iso [(parse-long o) (parse-double p)]]))))
                       (reduce (fn [m [iso e]] (update m iso (fnil conj []) e)) {})
                       (into {} (map (fn [[iso es]] [iso (vec (sort-by first es))])))))])))

(defn- latest-p
  "The p of the latest origin at or before month t, nil if none."
  [es t]
  (some (fn [[o p]] (when (<= o t) p)) (rseq es)))

(defn event-hazard
  "{iso P_event} as known at the end of year `y`: each kind's latest year
  forecast by month 12y + 11 as a yearly hazard, their union. {:default}
  holds the median over the countries, for the rest."
  [fc y]
  (let [t (+ (* 12 (long y)) 11)
        isos (distinct (mapcat keys (vals fc)))
        m (into {} (for [iso isos
                         :let [ps (keep #(some-> (get-in fc [% iso]) (latest-p t) (annual 24)) (keys fc))]
                         :when (seq ps)]
                     [iso (union ps)]))
        v (vec (sort (vals m)))]
    (assoc m :default (if (seq v) (v (quot (count v) 2)) 0.0))))

;; ---------------------------------------------------------------- straits

(def strait-prior
  "Gamma(a, b) prior on closures per strait-year: mean a/b = 0.02."
  {:a 0.5 :b 25.0})

(defn strait-rate
  "Gamma-Poisson posterior of a strait's closures a year from `n` closures
  in `exposure` strait-years: {:mean :sd :a :b}."
  [n exposure {:keys [a b]}]
  (let [a' (+ (double a) n) b' (+ (double b) exposure)]
    {:mean (/ a' b') :sd (/ (Math/sqrt a') b') :a a' :b b'}))

(def straits
  "The straits bnm.chokepoints resolves, with PortWatch's ids."
  {"suez" "chokepoint1" "panama" "chokepoint2" "bosporus" "chokepoint3"
   "bab-el-mandeb" "chokepoint4" "malacca" "chokepoint5" "hormuz" "chokepoint6"
   "cape-of-good-hope" "chokepoint7" "gibraltar" "chokepoint8" "dover" "chokepoint9"
   "oresund" "chokepoint10" "taiwan" "chokepoint11" "korea" "chokepoint12"
   "tsugaru" "chokepoint13" "luzon" "chokepoint14" "lombok" "chokepoint15"
   "sunda" "chokepoint19" "makassar" "chokepoint20" "magellan" "chokepoint21"
   "bering" "chokepoint26" "kerch" "chokepoint28"})

(defn strait-rates
  "{strait posterior} as known at year `y` from closure events [{:strait
  :year}] (those of years :from..y count, over y - :from + 1 years)."
  [events y {:keys [prior from] :or {prior strait-prior from 2019}}]
  (let [exposure (double (max 0 (inc (- (long y) (long from)))))
        n (frequencies (for [{:keys [strait year]} events :when (<= from year y)] strait))]
    (into {} (for [s (keys straits)] [s (strait-rate (get n s 0) exposure prior)]))))

(defn portwatch-closures
  "Closure events [{:strait :year :name}] from PortWatch's disruptions
  database (its csv): the events listing a resolved strait among the
  affected ports, the year from the event's start (the database's year
  field is wrong for HORMUZ-26). The Cape of Good Hope is left out: the
  Red Sea events list it as the detour that gained traffic."
  [path]
  (let [rows (csv/read-maps path \,)
        by-id (into {} (map (fn [[s id]] [id s])) straits)]
    (vec (for [r rows
               id (map str/trim (str/split (or (:affectedports r) "") #";"))
               :let [s (by-id id)]
               :when (and s (not= s "cape-of-good-hope"))]
           {:strait s :name (:eventname r)
            :year (+ 1970 (long (quot (parse-long (:fromdate r)) (* 1000 60 60 24 365.2425))))}))))

;; ---------------------------------------------------------------- restrictions

(defn restriction-rates
  "Export-restriction hazards from OECD inventory rows ({:iso :hs6 :year
  :known :in-force :onsets}) known by year `y`:
    :tighten  onsets per measure in force the year before (pooled)
    :entry    {iso rate}: country-codes newly listed per unrestricted
              country-code-year, from the inventory's second year on,
              (entries + strength x pooled) / (exposure + strength)
    :pooled   the pooled entry rate, for countries the inventory lacks
    :in-force #{[iso hs6]} under a measure in the latest year known
  :codes is the inventory's code count (the unrestricted exposure)."
  [rows y {:keys [codes strength] :or {strength 500.0}}]
  (let [rows (known-by rows y)
        forced (into #{} (for [r rows :when (pos? (:in-force r))] [(:iso r) (:hs6 r) (:year r)]))
        years (sort (distinct (map :year rows)))
        first-y (first years) last-y (last years)
        tight (for [r rows :when (forced [(:iso r) (:hs6 r) (dec (:year r))])] (pos? (:onsets r)))
        seen-first (reduce (fn [m r] (if (pos? (:in-force r)) (update m [(:iso r) (:hs6 r)] (fnil min Long/MAX_VALUE) (:year r)) m))
                           {} rows)
        isos (distinct (map :iso rows))
        restricted (frequencies (for [[iso _ yr] forced] [iso yr]))
        entries (frequencies (for [[[iso _] yr] seen-first :when (> yr first-y)] iso))
        exposure (into {} (for [iso isos]
                            [iso (double (reduce + 0 (for [t (range (inc (or first-y 0)) (inc (or last-y 0)))]
                                                       (max 0 (- codes (get restricted [iso (dec t)] 0))))))]))
        pooled (let [e (reduce + 0.0 (vals exposure))] (if (pos? e) (/ (reduce + 0.0 (vals entries)) e) 0.0))]
    {:tighten (if (seq tight) (/ (double (count (filter true? tight))) (count tight)) 0.0)
     :pooled pooled
     :entry (into {} (for [iso isos]
                       [iso (let [d (+ (exposure iso) strength)]
                              (if (pos? d) (/ (+ (get entries iso 0) (* strength pooled)) d) pooled))]))
     :in-force (into #{} (for [[iso hs6 yr] forced :when (= yr last-y)] [iso hs6]))}))

(defn restriction-p
  "The yearly probability of a new or tighter export restriction on
  `hs6` from `iso`."
  ^double [{:keys [tighten pooled entry in-force]} iso hs6]
  (if (contains? in-force [iso hs6]) tighten (get entry iso pooled)))

(defn load-restrictions
  "data/derived/supply/export-restrictions.csv (scripts/export-restrictions.py)."
  [path]
  (mapv (fn [r] {:iso (:iso r) :hs6 (:hs6 r) :year (parse-long (:year r)) :known (parse-long (:known r))
                 :in-force (parse-long (:in_force r)) :onsets (parse-long (:onsets r))})
        (csv/read-maps path \,)))

;; ---------------------------------------------------------------- loss

(def spare
  "Prior short-run spare capacity of the other suppliers, a share of
  their output, by horizon."
  {:m3 0.05 :m12 0.15 :steady 0.30})

(defn reallocation
  "The share of a supplier's lost exports the others replace: their
  spare capacity e (1 - s) over the loss x s, in [0, 1]. s the supplier's
  share of world supply, x the cut."
  ^double [s x e]
  (let [s (double s) lost (* (double x) s)]
    (cond (>= s 1.0) 0.0
          (<= lost 0.0) 1.0
          :else (max 0.0 (min 1.0 (/ (* (double e) (- 1.0 s)) lost))))))

(defn interp-loss
  "Loss at cut f from a table {:cuts [f1 f2 ...] :loss [L1 L2 ...]}
  (ascending cuts): linear between the points and from (0, 0); the last
  point's loss beyond it."
  ^double [{:keys [cuts loss]} f]
  (let [f (double f)]
    (loop [i 0 f0 0.0 l0 0.0]
      (if (>= i (count cuts))
        (double l0)
        (let [f1 (double (cuts i)) l1 (double (loss i))]
          (if (<= f f1)
            (if (<= f1 f0) l1 (+ l0 (* (- l1 l0) (/ (- f f0) (- f1 f0)))))
            (recur (inc i) f1 l1)))))))

(defn slices
  "{region {supplier T}}: each importing region's imports of the product
  by supplier. With :flows ({region {iso kUSD}}, BACI's bilateral flows
  summed over the region's countries) the slice is what the region bought
  from the supplier, so a region's slices add up to its imports; without,
  the fallback: its imports x the supplier's share of world exports (the
  listed :exporters only, the rest stay in :world)."
  [{:keys [world exporters importers flows]}]
  (if flows
    (into {} (for [[r _] importers] [r (get flows r {})]))
    (let [world (double world)]
      (into {} (for [[r m] importers]
                 [r (into {} (for [[c v] exporters] [c (* (double m) (/ (double v) world))]))])))))

(defn product-risk
  "Expected loss from one product's disruptions, at one horizon:
  {:world R :regions {region R} :suppliers {iso {:p :risk}} :straits
  {strait R}}. Keys of `m`:
    :world      world exports of the product
    :exporters  {iso exports}
    :importers  {region imports}
    :region-of  {iso region} (a supplier's own region does not import
                from it)
    :p          {iso P(disruption)}
    :use        {region use of the product's parent} (same unit): the
                shortfall over it is the cut f, at most 1
    :lambda     (fn [region f]) loss per unit of use lost at cut f of
                the region's use
    :model-use  {region use of the parent in the loss model} (the
                result's unit), optional: the loss is lambda x f x this,
                so a region the loss model sees using none of the parent
                loses nothing whatever it imports; without it, :use
    :flows      {region {iso imports}}, optional: the bilateral slices
                (`slices`); without, the world-share fallback
    :x :e       the cut and the spare capacity
    :straits    {region {strait share of its imports}}, :q {strait rate},
                :d the share of a closed strait's flow lost, :strait-world
                {strait share of world trade}: a closure is rerouted or
                replaced like a supplier's cut (`reallocation` with that
                share; the region's own share where it is missing)."
  [{:keys [world exporters importers region-of p use lambda x e straits q d strait-world model-use] :or {d 0.5} :as m}]
  (let [world (double world)
        loss (fn [r short] (if (pos? short)
                             (let [u (double (get use r 0.0))
                                   f (if (pos? u) (min 1.0 (/ short u)) 1.0)
                                   w (double (if model-use (get model-use r 0.0) (if (pos? u) u short)))]
                               (* (double (lambda r f)) f w))
                             0.0))
        sl (slices m)
        sup (into {} (for [[c v] exporters
                           :let [s (/ (double v) world) pc (double (get p c 0.0))
                                 keep (- 1.0 (reallocation s x e))
                                 own (get region-of c)
                                 by-r (into {} (for [[r _] importers :when (not= r own)]
                                                 [r (* pc (loss r (* (double x) keep (double (get-in sl [r c] 0.0)))))]))]]
                       [c {:p pc :risk (reduce + 0.0 (vals by-r)) :regions by-r}]))
        st (for [[r ex] straits [s share] ex
                 :let [qs (double (get q s 0.0)) m (double (get importers r 0.0))
                       keep (- 1.0 (reallocation (get strait-world s share) d e))]
                 :when (and (pos? qs) (pos? m))]
             [r s (* qs (loss r (* (double d) keep (double share) m)))])
        regions (merge-with + (apply merge-with + {} (map :regions (vals sup)))
                            (reduce (fn [acc [r _ v]] (update acc r (fnil + 0.0) v)) {} st))]
    {:world (reduce + 0.0 (vals regions))
     :regions regions
     :suppliers (into {} (for [[c v] sup] [c (dissoc v :regions)]))
     :straits (reduce (fn [acc [_ s v]] (update acc s (fnil + 0.0) v)) {} st)}))

;; ---------------------------------------------------------------- loss tables from bnm.supply

(defn value-added
  "{[region product] value added} (EUR M, 2011): output value (production
  rows' value_2011) times one less the recipe's input value shares."
  [g production]
  (into {} (for [r production
                 :let [k [(:region r) (:product r)]
                       v (some-> (:value_2011 r) not-empty parse-double)
                       ins (get-in g [:recipes k])]
                 :when (and v (pos? v))]
             [k (* v (max 0.0 (- 1.0 (reduce + 0.0 (keep :share (vals ins))))))])))

(defn use-values
  "{[region product] value of the region's use} (EUR M, 2011): its trade
  rows' quantities (origin to region, domestic included) at each origin's
  unit value."
  [trade production]
  (let [uv (into {} (for [r production
                          :let [q (some-> (:quantity r) not-empty parse-double)
                                v (some-> (:value_2011 r) not-empty parse-double)]
                          :when (and q v (pos? q))]
                      [[(:region r) (:product r)] (/ v q)]))]
    (reduce (fn [m {:keys [product origin dest quantity]}]
              (if-let [u (uv [origin product])]
                (update m [dest product] (fnil + 0.0) (* u (double (or quantity 0.0))))
                m))
            {} trade)))

(defn outage-loss
  "{:world L :regions {region L}}: value added lost (`va`) at the
  availabilities {[region product] a} of an outage's :products."
  [va products]
  (let [rs (reduce-kv (fn [m [r _ :as k] a] (let [l (* (- 1.0 (double a)) (get va k 0.0))]
                                               (if (pos? l) (update m r (fnil + 0.0) l) m)))
                      {} products)]
    {:world (reduce + 0.0 (vals rs)) :regions rs}))

(defn loss-row
  "Losses of a cut of `region`'s use of `product` by `f` (bnm.supply
  outage at :use, and outage-path over 12 months with `stocks`):
  {:steady :m3 :m12} each {:world :own}. `opts` go to bnm.supply (a
  built :hypergraph, :tol)."
  [g va stocks region product f opts]
  (let [cut {[region product] f}
        o (sup/outage g cut (assoc opts :at :use))
        path (when (seq stocks) (sup/outage-path g cut stocks (assoc opts :at :use :months 12)))
        at (fn [m] (if path (:products (nth path (dec m))) (:products o)))
        l (fn [ps] (let [{:keys [world regions]} (outage-loss va ps)] {:world world :own (get regions region 0.0)}))]
    {:steady (l (:products o)) :m3 (l (at 3)) :m12 (l (at 12))}))

;; ---------------------------------------------------------------- ranking and flags

(defn rank-risk
  "Entries ranked by :risk, largest first (ties by :hs6): :rank and :pct
  (rank over the count)."
  [entries]
  (let [n (count entries)]
    (vec (map-indexed (fn [i e] (assoc e :rank (inc i) :pct (/ (double (inc i)) n)))
                      (sort-by (juxt (comp - :risk) :hs6) entries)))))

(defn risk-up?
  "Whether R has risen against its own past: above the mean of the past
  values (at least three), nil with fewer."
  [r past]
  (when (>= (count past) 3)
    (> (double r) (/ (reduce + 0.0 past) (count past)))))

(def signals [:rising :linked :concentrated :risk-up])

(defn flag?
  "The rising-essential rule (ESSENTIALS 7c): all four signals."
  [m]
  (every? #(true? (get m %)) signals))

(defn shuffle-within
  "The rows with :risk permuted among the products of each HS2 chapter
  (the back-test's placebo)."
  [rows ^java.util.Random rng]
  (let [by (group-by #(subs (:hs6 %) 0 2) rows)]
    (vec (mapcat (fn [[_ rs]]
                   (let [rs (vec rs)
                         a (loop [a (mapv :risk rs) i (dec (count rs))]
                             (if (pos? i)
                               (let [j (.nextInt rng (inc i))]
                                 (recur (assoc a i (a j) j (a i)) (dec i)))
                               a))]
                     (map-indexed (fn [i r] (assoc r :risk (a i))) rs)))
                 by))))
