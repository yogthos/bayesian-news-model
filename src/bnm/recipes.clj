(ns bnm.recipes
  "Recipe coefficients through time, from physics priors and data
  (docs/ESSENTIALS.md 7a and 8 step 5; bead bnm-1pq.6).

  Each coefficient in resources/bnm/recipe-priors.edn (input per unit of
  output: t of gas per t of NH3, GJ per MWh, kg of cobalt per kWh) is a
  local-level dynamic linear model (West & Harrison 1997) per region, on
  the log (or, for shares, the log odds):

    x(t) = x(t-1) + drift(t) + w,   w ~ N(0, step^2)
    y    = x(t) + e,                e ~ N(0, V_source)

  The prior is the file's: centre ln(mean), sd from the 90% range. By
  default the model is a local level, drift 0: the registered step-5 test
  (ESSENTIALS 8) found drift learned from the 1995-2011 tables forecast
  the 2022 shares worse than none (bnm-1pq.22), so no drift is learned.
  A recipe drifts only where the priors file cites a technology
  trajectory for it (:drift with :drift-cite and :drift-basis: PV silicon
  and silver, battery cobalt, polysilicon electricity); a :drift without
  a citation is refused. The state's variance never exceeds
  the prior's: without data a coefficient falls back to physics, not to
  ignorance. Each source's observation variance V is learned from the
  data (an inverse-gamma estimate updated by each standardized error, West
  & Harrison 4.6, as augur.dynamic/linear does), shared by the recipe's
  regions. An observation further than :screen-sds prior sds from the
  prior is a flag (a unit or routing error, PRINCIPLES 10): counted, not
  used.

  A source with :bias-sd (a country's reporting convention, the same every
  year) carries a bias state per region, b ~ N(0, bias-sd^2), constant in
  time: y = x + b + e. Its data then move x only so far, however many
  years there are.

  The floor (the thermodynamic or stoichiometric minimum) bounds the
  coefficient below. The filter runs unconstrained and every reported
  state is truncated at the floor, by its moments (`truncate`). That is
  the exact posterior for a constraint on the reported year alone; the
  physics holds every year, and conditioning on that too would also move
  the path, so this is an approximation. It matters only where data push
  the state toward the floor.

  The variance cap (the state's variance never above the prior's) binds
  only in long stretches without data; it caps uncertainty, not the mean,
  which the data move freely. The screen is against the prior, not the
  filter's prediction: a coefficient that really left its prior by more
  than :screen-sds would have its data flagged, so a recipe whose data
  are mostly flagged (recipes-check prints the share) is mostly prior.

  Causality: the posterior of year Y uses only the observations known by
  Y, each entering at its data year (the filter is rerun from the start
  for each Y), and of several vintages of one observation the latest
  known by Y (a later edition replaces an earlier one).

  The observation builders (`observations`) read data/derived/supply/:
  recipes.csv (EXIOBASE hybrid 2011), recipe-obs/ (scripts/recipe-obs.py:
  pxp value shares 1995-2022, UNSD energy, FAOSTAT fertiliser, USGS
  ammonia),
  hs6-world.csv (BACI unit values), stages.csv and production.csv."
  (:require [augur.math :as am]
            [bnm.stages :as stg]
            [bnm.supply :as supply]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def source-kinds
  #{:test :exio :exio-ammonia :exio-power-share :stage :bf-ore :bf-coal :unsd-heat-rate :unsd-ratio
    :unsd-ammonia :fao-nutrient})

(defn priors
  "The priors file, resources/bnm/recipe-priors.edn."
  []
  (edn/read-string (slurp (or (io/resource "bnm/recipe-priors.edn")
                              (io/file "resources/bnm/recipe-priors.edn")))))

(defn recipe
  "The recipe with id in the priors."
  [pr id]
  (first (filter #(= id (:id %)) (:recipes pr))))

;; ------------------------------------------------------------------ priors

(def ^:private z90 3.29)

(defn- fwd ^double [scale x]
  (if (= :logit scale) (am/logit (double x)) (Math/log (double x))))

(defn- bound
  "The floor on the filter's scale, nil where there is none."
  [{:keys [scale floor]}]
  (when (and floor (pos? (double floor))) (fwd scale floor)))

(defn- prior-sd ^double [{:keys [scale lo hi]}]
  (/ (- (fwd scale hi) (fwd scale lo)) z90))

(defn- drift
  "The recipe's yearly drift on the filter's scale: 0 unless it cites a
  technology trajectory (:drift-cite); an uncited :drift is an error."
  ^double [{:keys [id drift drift-cite]}]
  (cond
    (nil? drift) 0.0
    (seq drift-cite) (double drift)
    :else (throw (ex-info (str id ": a :drift needs a :drift-cite (no default drift, bnm-1pq.22)")
                          {:id id :drift drift}))))

(defn- centre
  "The prior's centre in year t: ln(mean) at :ref-year, moved by a cited
  :drift a year from :drift-from on; flat otherwise."
  ^double [{:keys [scale mean ref-year drift-from] :as p} t]
  (let [c #(if drift-from (max (long %) (long drift-from)) (long %))]
    (+ (fwd scale mean) (* (drift p) (- (c t) (c (or ref-year t)))))))

(defn prior-state
  "The prior in year t, untruncated: {:m :v} on the filter's scale."
  [p t]
  {:m (centre p t) :v (let [s (prior-sd p)] (* s s))})

(defn- norm-pdf ^double [^double x] (/ (Math/exp (* -0.5 x x)) (Math/sqrt (* 2.0 Math/PI))))

(defn truncate
  "The moments of N(m, v) truncated below at lo (nil: none):
  m + s lambda, v (1 + a lambda - lambda^2), a = (lo - m)/s, lambda the
  inverse Mills ratio; far into the tail its asymptotic series."
  [{:keys [m v] :as st} lo]
  (if (nil? lo)
    st
    (let [m (double m) v (double v) s (Math/sqrt v) a (/ (- (double lo) m) s)]
      (cond
        (< a -8.0) st
        :else
        (let [lam (if (> a 8.0)
                    ;; phi(a)/(1-Phi(a)) ~ a + 1/a - 2/a^3
                    (+ a (/ 1.0 a) (/ -2.0 (* a a a)))
                    (/ (norm-pdf a) (max 1e-300 (- 1.0 (am/normal-cdf a)))))
              m' (+ m (* s lam))
              v' (* v (max 1e-12 (+ 1.0 (* a lam) (- (* lam lam)))))]
          {:m (max (double lo) m') :v v'})))))

(defn prior
  "The prior in year t, truncated at the floor."
  [p t]
  (truncate (prior-state p t) (bound p)))

;; ------------------------------------------------------------------ filter

(def ^:private n0 1.0)

(defn source-sd0
  "A source's observation sd about the coefficient, before the data teach
  it, for the screen: :sd0 (0.3 if absent) and, for a source with a
  persistent bias, its :bias-sd, in quadrature. (The filter holds the bias
  as a state and starts the source's noise at :sd0.)"
  ^double [{:keys [sd0 bias-sd]}]
  (let [s (double (or sd0 0.3)) b (double (or bias-sd 0.0))]
    (Math/sqrt (+ (* s s) (* b b)))))

(defn- visible
  "The observations known by year y (and of data years from the filter's
  first year on), the latest vintage of each [source region year]."
  [obs start y]
  (->> obs
       (filter #(and (<= (long (:known %)) y) (<= start (long (:year %)) y)))
       (group-by (juxt :source :region :year))
       vals
       (map #(apply max-key :known %))))

(defn- to-scale [scale v]
  (let [v (double v)]
    (when (and (pos? v) (< v 1e300) (or (not= :logit scale) (< v 1.0)))
      (fwd scale (if (= :logit scale) (am/clamp 1e-4 (- 1.0 1e-4) v) v)))))

(defn- prepare
  "Observations on the filter's scale, each marked :flag when it lies over
  `sds` predictive sds from the prior's centre in its year (the prior's
  variance plus the source's starting noise, :sd0); unusable values
  dropped."
  [p obs sds]
  (let [v0 (Math/pow (prior-sd p) 2)
        sd (fn [i] (Math/sqrt (+ v0 (Math/pow (source-sd0 (get (:sources p) i)) 2))))]
    (keep (fn [o]
            (when-let [y (to-scale (:scale p) (:value o))]
              (assoc o :y y :flag (> (Math/abs (- y (centre p (:year o)))) (* sds (sd (:source o)))))))
          obs)))

(defn- evolve [p st t]
  (let [v0 (:v (prior-state p t))
        step (double (or (:step p) 0.02))
        dm (- (centre p t) (centre p (dec t)))]
    (if-let [mu (:mu st)]
      ;; with bias states: the coefficient (index 0) walks, the biases stay;
      ;; the cap scales row and column 0 (a congruence: P stays a covariance)
      (let [P (:P st)
            v (+ (double (get-in P [0 0])) (* step step))
            f (if (> v v0) (Math/sqrt (/ v0 v)) 1.0)
            n (count mu)]
        {:mu (update mu 0 + dm)
         :P (vec (for [i (range n)]
                   (vec (for [j (range n)]
                     (cond (= 0 i j) (min v0 v)
                           (or (zero? i) (zero? j)) (* f (double (get-in P [i j])))
                           :else (get-in P [i j]))))))})
      {:m (+ (:m st) dm)
       :v (min v0 (+ (:v st) (* step step)))})))

(defn- kalman
  "[state noise] after observing y with the source's noise {:s :n}."
  [{:keys [m v]} {:keys [s n]} y]
  (let [m (double m) v (double v) s (double s) n (double n)
        q (+ v s)
        e (- (double y) m)
        k (/ v q)
        n' (+ n 1.0)
        s' (max 1e-8 (+ s (* (/ s n') (- (/ (* e e) q) 1.0))))]
    [{:m (+ m (* k e)) :v (* v (/ s q))} {:s s' :n n'}]))

(defn- kalman-biased
  "[state noise] after observing y = x + b_j + e, j the source's bias state
  (nil for an unbiased source), on the vector state {:mu :P}."
  [{:keys [mu P]} j {:keys [s n]} y]
  (let [h (if j [0 j] [0])
        s (double s) n (double n)
        q (+ s (reduce + (for [a h b h] (double (get-in P [a b])))))
        e (- (double y) (reduce + (map #(double (mu %)) h)))
        ph (vec (for [i (range (count mu))] (reduce + (map #(double (get-in P [i %])) h))))
        n' (+ n 1.0)
        s' (max 1e-8 (+ s (* (/ s n') (- (/ (* e e) q) 1.0))))]
    [{:mu (vec (map-indexed (fn [i m] (+ (double m) (/ (* (ph i) e) q))) mu))
      :P (vec (for [i (range (count mu))]
                (vec (for [k (range (count mu))] (- (double (get-in P [i k])) (/ (* (ph i) (ph k)) q))))))}
     {:s s' :n n'}]))

(defn- bias-states
  "{source index -> state index} of the sources read with a persistent bias
  (:bias-sd): a country's reporting convention or product mix is the same
  every year, so its bias is a state of the region, not fresh noise."
  [p]
  (into {} (map-indexed (fn [k i] [i (inc k)])
                        (keep-indexed (fn [i s] (when (:bias-sd s) i)) (:sources p)))))

(defn- init-state [p bi t]
  (let [{:keys [m v]} (prior-state p t)]
    (if (empty? bi)
      {:m m :v v}
      (let [n (inc (count bi))
            d (into {0 v} (for [[i k] bi] [k (Math/pow (double (:bias-sd (get (:sources p) i))) 2)]))]
        {:mu (vec (cons m (repeat (dec n) 0.0)))
         :P (vec (for [a (range n)] (vec (for [b (range n)] (if (= a b) (d a) 0.0)))))}))))

(defn- coefficient
  "The coefficient's marginal {:m :v} of a state."
  [st]
  (if-let [mu (:mu st)] {:m (mu 0) :v (get-in (:P st) [0 0])} st))

(defn- pass
  "States at year y from the observations known by y: {region state},
  the noise per source, and the counts."
  [p regions obs start y]
  (let [vis (visible obs start y)
        used (remove :flag vis)
        at (group-by (juxt :region :year) used)
        bi (bias-states p)
        ;; a biased source's bias is a state, so its noise starts at :sd0
        noise0 (vec (for [s (:sources p)]
                      (let [s (double (or (:sd0 s) 0.3))] {:s (* s s) :n n0})))]
    (loop [t start
           states (into {} (for [r regions] [r (init-state p bi start)]))
           noise noise0]
      (let [[states noise]
            (reduce (fn [[sts nz] r]
                      (let [st (if (= t start) (sts r) (evolve p (sts r) t))
                            [st nz] (reduce (fn [[st nz] o]
                                              (let [i (:source o)
                                                    [st' nz'] (if (:mu st)
                                                                (kalman-biased st (bi i) (nz i) (:y o))
                                                                (kalman st (nz i) (:y o)))]
                                                [st' (assoc nz (:source o) nz')]))
                                            [st nz]
                                            (sort-by :source (at [r t])))]
                        [(assoc sts r st) nz]))
                    [states noise] regions)]
        (if (>= t y)
          {:states states :noise noise
           :n (frequencies (map :region used))
           :flagged (frequencies (map :region (filter :flag vis)))}
          (recur (inc t) states noise))))))

(defn summary
  "A state on the coefficient's own scale: the truncated state's :m :v,
  and :median, :mean and :sd in the recipe's unit, at or above the floor."
  [p st]
  (let [{:keys [m v] :as st} (truncate st (bound p))
        floor (double (or (:floor p) 0.0))
        [med mean sd] (if (= :logit (:scale p))
                        (let [mu (am/expit (/ m (Math/sqrt (+ 1.0 (/ (* Math/PI v) 8.0)))))]
                          [(am/expit m) mu (* mu (- 1.0 mu) (Math/sqrt v))])
                        (let [mu (Math/exp (+ m (* 0.5 v)))]
                          [(Math/exp m) mu (* mu (Math/sqrt (Math/expm1 v)))]))]
    (assoc st :median (max floor med) :mean (max floor mean) :sd sd)))

(defn run
  "The recipe's posterior each year from :start to :end, as known that
  year: {:rows [{:recipe :region :year :m :v :median :mean :sd :n-obs
  :n-flagged}] :noise [{:s :n} per source, at :end] :flags n}. `obs` are
  {:region :year :known :value :source (index into :sources)}. Regions are
  the observations' and any listed in the recipe's :regions; WLD when
  there are none."
  [p obs {:keys [start end screen-sds from] :or {screen-sds 4.0}}]
  (let [from (min start (or from start))
        obs (vec (prepare p obs screen-sds))
        regions (sort (distinct (concat (map :region obs)
                                        (when (sequential? (:regions p)) (:regions p)))))
        regions (if (seq regions) regions ["WLD"])
        passes (for [y (range start (inc end))] [y (pass p regions obs from y)])]
    {:rows (vec (for [r regions
                      [y {:keys [states n flagged]}] passes]
                  (merge {:recipe (:id p) :region r :year y}
                         (summary p (coefficient (states r)))
                         {:n-obs (get n r 0) :n-flagged (get flagged r 0)})))
     :noise (:noise (second (last passes)))
     :flags (count (filter :flag obs))}))

(defn filter-recipe
  "The rows of `run`."
  [p obs opts]
  (:rows (run p obs opts)))

;; ------------------------------------------------------------ data: reading

(defn- num [s] (when (and s (not (str/blank? s))) (parse-double s)))

(defn- csv-rows
  "Rows of a plain CSV (no quoted fields) as maps, `keep?` on the raw
  split line first (fast filtering of big files)."
  ([path] (csv-rows path (constantly true)))
  ([path keep?]
   (with-open [r (io/reader path)]
     (let [[h & ls] (line-seq r)
           ks (mapv keyword (str/split h #","))]
       (into [] (comp (map #(str/split % #"," -1)) (filter keep?) (map #(zipmap ks %))) ls)))))

(defn- iso2-of
  "{iso3 iso2} from resources/bnm/countries.edn."
  []
  (into {} (for [c (:countries (edn/read-string (slurp (or (io/resource "bnm/countries.edn")
                                                           (io/file "resources/bnm/countries.edn")))))]
             [(:iso3 c) (:iso2 c)])))

(def pxp-years [1995 2000 2005 2008 2011 2015 2019 2022])
(def ^:private exio-lag 3)
(def ^:private hybrid-year 2011)

(defn load-data
  "What the observation builders read, from `dir` (data/derived/supply)."
  [dir pr]
  (let [f #(str (io/file dir %))
        exio-outputs (set (for [r (:recipes pr) s (:sources r)
                                :when (#{:exio :exio-ammonia} (:kind s))]
                            (or (:output s) "C_NFER")))
        hs (set (for [r (:recipes pr) s (:sources r) k [:out :in] c (get-in s [:price k])] c))
        recipes (csv-rows (f "recipes.csv") #(contains? exio-outputs (nth % 1)))
        shares (csv-rows (f "recipe-obs/pxp-shares.csv") #(contains? exio-outputs (nth % 2)))]
    {:phys (into {} (for [m recipes] [[(:region m) (:output m) (:input m)] (num (:phys m))]))
     :shares (reduce (fn [acc m]
                       (assoc-in acc [[(:region m) (:output m)] (parse-long (:year m)) (:input m)]
                                 (num (:share m))))
                     {} shares)
     :prices (reduce (fn [acc m]
                       (let [v (num (:value m)) t (num (:tonnes m))]
                         (if (and v t (pos? t))
                           (assoc-in acc [(:hs6 m) (parse-long (:year m))] [v t])
                           acc)))
                     {} (csv-rows (f "hs6-world.csv") #(and (= "HS92" (nth % 0)) (contains? hs (nth % 3)))))
     :production (csv-rows (f "production.csv"))
     :stages (stg/read-rows (f "stages.csv"))
     :unsd (reduce (fn [acc m]
                     (assoc-in acc [(:iso m) (parse-long (:year m)) (:series m)] (num (:value m))))
                   {} (csv-rows (f "recipe-obs/unsd.csv")))
     :fao (csv-rows (f "recipe-obs/fao.csv"))
     :nh3 (if (.exists (io/file (f "recipe-obs/nh3.csv")))
            (reduce (fn [acc m]
                      (update-in acc [(:iso m) (parse-long (:year m))] (fnil conj [])
                                 {:edition (parse-long (:edition m)) :known (parse-long (:known m))
                                  :value (num (:value m))}))
                    {} (csv-rows (f "recipe-obs/nh3.csv")))
            {})
     :iron (if (.exists (io/file (f "recipe-obs/iron.csv")))
             (reduce (fn [acc m]
                       (update-in acc [(:series m) (parse-long (:year m))] (fnil conj [])
                                  {:edition (parse-long (:edition m)) :known (parse-long (:known m))
                                   :value (num (:value m))}))
                     {} (csv-rows (f "recipe-obs/iron.csv")))
             {})
     :cereals (reduce (fn [acc m]
                        (if (= "production" (:element m))
                          (update-in acc [(:iso m) (parse-long (:year m))] (fnil + 0.0) (or (num (:value m)) 0.0))
                          acc))
                      {} (csv-rows (str (io/file dir "../../raw/material/faostat-fbs/fbs-cereals.csv"))))
     :iso2 (iso2-of)}))

;; ------------------------------------------------------- data: observations

(defn- exio-regions [data]
  (sort (distinct (map first (keys (:phys data))))))

(defn- exio-countries [data]
  (remove #(str/starts-with? % "W") (exio-regions data)))

(defn- region-set
  "The recipe's regions: a vector as given; :exio-all every EXIOBASE
  region; :unsd and :fao the EXIOBASE countries and WLD; less :exclude."
  [p data]
  (let [rs (case (:regions p)
             :exio-all (exio-regions data)
             (:unsd :fao) (cons "WLD" (exio-countries data))
             (:regions p))]
    (set (remove (set (:exclude p)) rs))))

(defn- unit-value
  "World unit value (kUSD per t) of HS6 codes in year y, nil without data."
  [data codes y]
  (let [vt (keep #(get-in data [:prices % y]) codes)
        v (reduce + 0.0 (map first vt)) t (reduce + 0.0 (map second vt))]
    (when (pos? t) (/ v t))))

(defn- relative-price
  "P_out(y)/P_out(2011) over P_in(y)/P_in(2011), nil without data."
  [data {:keys [out in]} y]
  (let [o (unit-value data out y) o0 (unit-value data out hybrid-year)
        i (unit-value data in y) i0 (unit-value data in hybrid-year)]
    (when (and o o0 i i0) (/ (/ o o0) (/ i i0)))))

(defn- share-sum [data region output inputs y]
  (reduce + 0.0 (keep #(get-in data [:shares [region output] y %]) inputs)))

(defn- implied
  "The 2011 coefficient a, carried to each pxp year by the value share of
  `inputs` and the relative price: [{:year :known :value}], 2011 itself
  included. An earlier year's value is known only with 2011's (2014)."
  [data region output inputs price a]
  (let [s0 (share-sum data region output inputs hybrid-year)]
    ;; every implied value carries a(2011), s(2011) and the 2011 prices:
    ;; none is known before the hybrid table is
    (cons {:year hybrid-year :known (+ hybrid-year exio-lag) :value a}
          (when (and price (pos? s0))
            (for [y pxp-years :when (not= y hybrid-year)
                  :let [s (share-sum data region output inputs y)
                        rp (relative-price data price y)]
                  :when (and (pos? s) rp)]
              {:year y :known (+ (max y hybrid-year) exio-lag) :value (* a (/ s s0) rp)})))))

(defn- phys-sum
  "Sum over inputs of phys x factor (x the fuel's NCV, TJ/t, when conv is
  :ncv)."
  [data region output inputs conv]
  (reduce + 0.0 (for [[i fct] inputs
                      :let [ph (get-in data [:phys [region output i]])]
                      :when ph]
                  (* ph (double fct) (if (= :ncv conv) (double (get supply/ncv i 0.0)) 1.0)))))

(defmulti source-obs
  "Observations of recipe p from source s: [{:region :year :known :value}]."
  (fn [_ s _ _] (:kind s)))

(defmethod source-obs :exio [p s data regions]
  (for [r (exio-regions data) :when (regions r)
        :let [a (* (phys-sum data r (:output s) (:inputs s) (:conv s)) (double (or (:factor s) 1.0)))]
        :when (pos? a)
        o (implied data r (:output s) (or (:share-inputs s) (keys (:inputs s))) (:price s) a)]
    (assoc o :region r)))

(def ^:private ammonia-coals ["C_ANTH" "C_COKC" "C_OTBC" "C_SUBC" "C_LIBC"])
(def ^:private gas-gj 48.0)
(def ^:private coal-gj 25.8)

(defmethod source-obs :exio-ammonia [p s data regions]
  (let [pr (priors)
        ig (* gas-gj (:mean (recipe pr "ammonia/gas")))
        ic (* coal-gj (:mean (recipe pr "ammonia/coal")))]
    (for [r (exio-regions data) :when (regions r)
          :let [gas-t (or (get-in data [:phys [r "C_NFER" "C_GASE"]]) 0.0)
                ec (* 1000.0 (phys-sum data r "C_NFER" (zipmap ammonia-coals (repeat 1.0)) :ncv))
                xg (/ (* gas-t gas-gj) ig) xc (/ ec ic)
                sc (if (pos? (+ xg xc)) (/ xc (+ xg xc)) 0.0)
                [a inputs] (case (:route s)
                             :gas [gas-t ["C_GASE" "C_GASD"]]
                             :coal [(when (>= sc 0.2) (/ (/ ec coal-gj) sc)) ammonia-coals]
                             :coal-share [(when (pos? (+ xg xc)) sc) nil])]
          :when (and a (pos? a))
          o (implied data r "C_NFER" inputs (when inputs (:price s)) a)]
      (assoc o :region r))))

(defmethod source-obs :exio-power-share [p s data regions]
  (let [q (reduce (fn [acc m]
                    (if (and (str/starts-with? (:product m) "C_POW") (= "TJ" (:unit m)))
                      (update acc (:region m) (fnil conj []) [(:product m) (or (num (:quantity m)) 0.0)])
                      acc))
                  {} (:production data))
        fossil #{"C_POWC" "C_POWG" "C_POWP"}
        share (fn [ps] (let [t (reduce + 0.0 (map second ps))
                             f (reduce + 0.0 (keep (fn [[c x]] (when (fossil c) x)) ps))]
                         (when (pos? t) (/ f t))))
        world (share (mapcat val q))]
    (concat
     (for [[r ps] q :when (regions r) :let [v (share ps)] :when v]
       {:region r :year hybrid-year :known (+ hybrid-year exio-lag) :value v})
     (when (and world (regions "WLD"))
       [{:region "WLD" :year hybrid-year :known (+ hybrid-year exio-lag) :value world}]))))

(defmethod source-obs :stage [p s data regions]
  (let [{:keys [commodity num den adjust factor]} s
        rows (filter #(= commodity (:commodity %)) (:stages data))
        by (group-by (juxt :stage :year) rows)
        total (fn [rs] (reduce + 0.0 (map #(* (:quantity %) (double (get adjust (:iso %) 1.0))) rs)))]
    (when (regions "WLD")
      (for [y (sort (distinct (map :year rows)))
            :let [nr (by [num y]) dr (by [den y])]
            :when (and (seq nr) (seq dr))
            k (sort (distinct (map :known (concat nr dr))))
            :let [nv (stg/vintage nr k) dv (stg/vintage dr k)]
            :when (and (seq nv) (seq dv))
            :let [a (total nv) b (reduce + 0.0 (map :quantity dv))]
            :when (and (pos? a) (pos? b))]
        {:region "WLD" :year y :known k :value (* (/ a b) (double (or factor 1.0)))}))))

(defn- iron-at
  "The latest edition of an iron.csv series' year known by k, nil if none."
  [data series y k]
  (let [vs (filter #(<= (long (:known %)) k) (get-in data [:iron series y]))]
    (when (seq vs) (apply max-key :edition vs))))

(defmethod source-obs :bf-ore [p s data regions]
  ;; World iron ore over pig iron, ore for direct reduction taken off:
  ;; [BGS ore outside China + China's ore - DRI x :dri-ore] / pig iron.
  ;; :china :usable is USGS's usable ore where it reports it (2010 on);
  ;; :china :scaled the years before, BGS crude ore x the first published
  ;; usable/crude ratio, known only once that ratio is.
  (when (regions "WLD")
    (let [rows (filter #(= "iron" (:commodity %)) (:stages data))
          by (group-by (juxt :stage :year) rows)
          usable (get-in data [:iron "china_usable"])
          y0 (when (seq usable) (apply min (keys usable)))
          ratio (when y0
                  (let [u (apply min-key :edition (usable y0))
                        c (filter #(= "CHN" (:iso %)) (by ["mine" y0]))]
                    (when (seq c)
                      (let [c0 (apply min-key :known c)]
                        {:r (/ (:value u) (:quantity c0)) :known (max (:known u) (:known c0))}))))
          dri-ore (double (or (:dri-ore s) 1.45))]
      (for [y (sort (distinct (map :year rows)))
            :let [mr (by ["mine" y]) sr (by ["smelter" y])
                  scaled? (= :scaled (:china s))]
            :when (and (seq mr) (seq sr) (if scaled? (and ratio (< y y0)) (contains? usable y)))
            k (sort (distinct (concat (map :known mr) (map :known sr)
                                      (map :known (get-in data [:iron "dri_world" y]))
                                      (when-not scaled? (map :known (usable y))))))
            :let [mv (stg/vintage mr k) sv (stg/vintage sr k)
                  d (iron-at data "dri_world" y k)
                  cu (when-not scaled? (iron-at data "china_usable" y k))
                  chn (filter #(= "CHN" (:iso %)) mv)
                  ex (reduce + 0.0 (map :quantity (remove #(= "CHN" (:iso %)) mv)))
                  china (if scaled?
                          (when (seq chn) (* (:r ratio) (reduce + 0.0 (map :quantity chn))))
                          (:value cu))
                  pig (reduce + 0.0 (map :quantity sv))]
            :when (and (seq mv) d china (pos? pig))
            :let [a (- (+ ex china) (* dri-ore (:value d)))]
            :when (pos? a)]
        {:region "WLD" :year y :known (if scaled? (max k (:known ratio)) k) :value (/ a pig)}))))

(defn- unsd-get [data iso y k] (get-in data [:unsd iso y k]))

(defn- by-country
  "Observations per EXIOBASE country and WLD from f: (iso3 year) -> nil or
  [numerator denominator known]; WLD sums the countries that report both."
  [data regions f]
  (let [iso2 (:iso2 data)
        isos (keys (:unsd data))
        years (sort (distinct (mapcat #(keys (get-in data [:unsd %])) isos)))
        cells (for [iso isos y years :let [v (f iso y)] :when v] [iso y v])]
    (concat
     (for [[iso y [a b k]] cells :let [r (iso2 iso)] :when (and r (regions r))]
       {:region r :year y :known k :value (/ a b)})
     (when (regions "WLD")
       (for [[y cs] (group-by second cells)
             :let [a (reduce + 0.0 (map #(first (nth % 2)) cs))
                   b (reduce + 0.0 (map #(second (nth % 2)) cs))]
             :when (pos? b)]
         {:region "WLD" :year y :known (apply max (map #(nth (nth % 2) 2) cs)) :value (/ a b)})))))

(def ^:private unsd-lag 2)

(defmethod source-obs :unsd-heat-rate [p s data regions]
  (let [f (:fuel s) g (double (or (:gross->net s) 1.0))]
    (by-country data regions
                (fn [iso y]
                  (let [fuel (unsd-get data iso y (str "fuel_" f))
                        elec (unsd-get data iso y (str "elec_" f))
                        heat (or (unsd-get data iso y (str "heat_" f)) 0.0)
                        hf (or (unsd-get data iso y (str "heatfuel_" f)) 0.0)]
                    (when (and fuel elec (pos? elec))
                      (let [chp-heat (max 0.0 (- heat (* 0.9 hf)))
                            fe (- fuel (/ chp-heat 0.9))]
                        (when (pos? fe) [(* fe g 3.6) elec (+ y unsd-lag)]))))))))

(defmethod source-obs :unsd-ratio [p s data regions]
  (by-country data regions
              (fn [iso y]
                (let [a (unsd-get data iso y (:num s)) b (unsd-get data iso y (:den s))]
                  (when (and a b (pos? a) (pos? b)) [a b (+ y unsd-lag)])))))

;; Ammonia's gas from UNSD natural gas to non-energy use (feedstock), TJ
;; gross x 0.9 to net, at 48 GJ/t, over USGS ammonia production (t NH3,
;; recipe-obs/nh3.csv), per country. Non-energy gas also feeds methanol and
;; other chemicals: countries where gas methanol is large beside ammonia
;; are left out (:exclude), and what remains is taken as biased up by
;; :bias on the log (divided out); what is left of the bias, sd :bias-sd,
;; is a persistent state per region in the filter (`bias-states`). Each USGS edition is a vintage, known at
;; the later of its own known year and UNSD's (year + 2); of editions
;; known the same year, the latest.
(defmethod source-obs :unsd-ammonia [p s data regions]
  (let [iso2 (:iso2 data)
        out (set (:exclude s))
        b (Math/exp (double (or (:bias s) 0.0)))]
    (for [[iso ys] (:nh3 data)
          :let [r (iso2 iso)]
          :when (and r (regions r) (not (out r)))
          [y eds] ys
          :let [g (unsd-get data iso y "gas_nonenergy")]
          :when (and g (pos? g))
          o (vals (into {} (for [e (sort-by :edition eds)
                                 :let [k (max (+ y unsd-lag) (long (:known e)))]
                                 :when (and (:value e) (pos? (:value e)))]
                             [k {:region r :year y :known k
                                 :value (/ (/ (* g 0.9 1000.0) gas-gj) (:value e) b)}])))]
      o)))

(defmethod source-obs :bf-coal [p s data regions]
  (let [pig (reduce (fn [acc m]
                      (if (and (= "iron" (:commodity m)) (= "smelter" (:stage m)))
                        (assoc acc [(:iso m) (:year m)] [(:quantity m) (:known m)])
                        acc))
                    {} (:stages data))]
    (by-country data regions
                (fn [iso y]
                  (let [cc (unsd-get data iso y "coal_coke") cbf (or (unsd-get data iso y "coal_bf") 0.0)
                        kb (unsd-get data iso y "coke_bf") kp (unsd-get data iso y "coke_prod")
                        [pq pk] (pig [iso y])]
                    (when (and cc kb kp pq (pos? kp) (pos? pq) (pos? kb))
                      ;; kt over t
                      [(* 1000.0 (+ (* cc (min 1.0 (/ kb kp))) cbf)) pq (max (+ y unsd-lag) pk)]))))))

(def ^:private fao-lag 2)

(defmethod source-obs :fao-nutrient [p s data regions]
  (let [iso2 (:iso2 data)
        use (for [m (:fao data) :when (and (= (:nutrient s) (:nutrient m)) (= "use" (:element m)))]
              [(:iso m) (parse-long (:year m)) (num (:tonnes m))])]
    ;; both FAOSTAT tables carry the world as WLD
    (for [[iso y u] use
          :let [r (if (= "WLD" iso) "WLD" (iso2 iso))
                c (get-in data [:cereals iso y])]
          :when (and r (regions r) u c (pos? c) (pos? u))]
      {:region r :year y :known (+ y fao-lag) :value (/ (* 1000.0 u (double (:factor s))) c)})))

(defmethod source-obs :test [_ _ _ _] [])

(defn with-regions
  "The recipe with a named region set (:exio-all, :unsd, :fao) resolved to
  its regions, so the regions without data keep their prior's rows."
  [p data]
  (if (keyword? (:regions p))
    (assoc p :regions (vec (sort (region-set p data))))
    p))

(defn observations
  "Every observation of recipe p: {:region :year :known :value :source}."
  [p data]
  (let [regions (region-set p data)]
    (vec (apply concat
                (map-indexed (fn [i s] (map #(assoc % :source i) (source-obs p s data regions)))
                             (:sources p))))))

;; ------------------------------------------------------------------- build

(defn build
  "Every recipe's run over the data in `dir`: {id run}."
  [dir]
  (let [pr (priors)
        data (load-data dir pr)
        opts (select-keys pr [:start :end :screen-sds :from])]
    (into (sorted-map)
          (for [p (:recipes pr) :let [obs (observations p data)]]
            [(:id p) (assoc (run (with-regions p data) obs opts) :n-total (count obs))]))))

(defn write-csv
  "data/derived/supply/recipes-filtered.csv: recipe,region,year,mean,sd,
  median,n_obs (the posterior known each year, in the recipe's unit)."
  [path runs]
  (with-open [w (io/writer path)]
    (.write w "recipe,region,year,mean,sd,median,n_obs\n")
    (doseq [[_ {:keys [rows]}] runs
            {:keys [recipe region year mean sd median n-obs]} rows]
      (.write w (str recipe "," region "," year "," (format "%.6g" (double mean)) ","
                     (format "%.6g" (double sd)) "," (format "%.6g" (double median)) "," n-obs "\n")))))

;; ----------------------------------- the step-5 test's cells (recipes-check)

(def ^:private step5-sources
  "Sources the registered step-5 test was run with that the recipes no
  longer read: ammonia/gas's EXIOBASE gas route (bnm-1pq.20), kept so the
  test's cells stay the registered ones."
  {"ammonia/gas" [{:kind :exio-ammonia :route :gas}]})

(defn share-cells
  "The value shares the EXIOBASE-read recipes rest on, as registered for
  step 5: for each :exio or :exio-ammonia source with inputs (and
  `step5-sources`), each of its regions, {:recipe :region :output :inputs
  :shares {year summed share}}."
  [pr data]
  (for [p (:recipes pr)
        s (concat (:sources p) (step5-sources (:id p)))
        :when (#{:exio :exio-ammonia} (:kind s))
        :let [output (or (:output s) "C_NFER")
              inputs (case (:route s)
                       :gas ["C_GASE" "C_GASD"]
                       :coal ammonia-coals
                       :coal-share nil
                       (or (:share-inputs s) (keys (:inputs s))))]
        :when inputs
        r (sort (region-set p data))
        :let [sh (into (sorted-map) (for [y pxp-years :let [v (share-sum data r output inputs y)]
                                         :when (pos? v)] [y v]))]
        :when (seq sh)]
    {:recipe (:id p) :region r :output output :inputs inputs :shares sh}))
