(ns bnm.technology
  "Technology as productivity inflows (bead bnm-62z.11; docs/STOCK-FLOW.md:
  technology diffuses slowly, Comin & Hobijn 2010 find adoption lags of
  about 45 years, a logistic per technology).

  Each technology is a diffusion stock per country: adopters A filled by
  dA/dt = s A (1 - A/K), whose closed form is the logistic
  A(t) = K / (1 + exp(-s (t - tau))): K the ceiling (saturation), s the
  speed (per year; 10% to 90% of the ceiling takes ln 81 / s years), tau
  the midpoint (the year half the ceiling is reached). diffusion-model is
  that stock in bnm.stockflow; calibrate-diffusion learns its parameters
  by the ensemble smoother (bnm.assimilate/calibrate); fit-logistic is
  the fast least-squares fit the country-year panel uses, rolling: at each
  year only what was known by then.

  The country's lag behind the frontier (Comin & Hobijn 2010) is the
  horizontal distance between the curves: for each recent observation, the
  years since the frontier first reached the country's level (the
  frontier's known path, interpolated; beyond its range its fitted
  logistic, inverted, censored `horizon` years out, see reach-time), the
  median over the window. Negative: ahead.

  Data are entries {:t data-year :known year-known :x level}; at year Y
  only entries with :known <= Y are read (as-of): the frontier's curve and
  its fit, the ceiling used as saturation, the country's own fit, all
  refitted each year on what was known by then. Every output row's year
  is the year it is known at.

  Every variable is technology/<name>, kebab-case. Per country,
  technology <k> and year known:
    technology/<k>-level      the latest level known (data no older than
                              :stale years)
    technology/<k>-adoption   level / saturation, at most 1: the
                              technology's fixed saturation where it has
                              one (shares, subscriptions per person), else
                              the frontier's identified ceiling, else the
                              cap, else the frontier's highest level known
    technology/<k>-lag        years behind the frontier (0 at the frontier)
    technology/<k>-speed      s of the country's own logistic, known data
    technology/<k>-midpoint   tau of the same
    technology/<k>-ceiling    K of the same, only where identified
  and across the technologies of each era <e> (:eras, sets of technologies
  whose data run over the same years, so that an era's gap does not jump
  when another source ends):
    technology/gap-<e>        the frontier's adoption less the country's,
                              averaged with the frontier's adoption as
                              weights (what matters at the frontier now
                              counts most)
    technology/lag-<e>        the mean lag, where known
    technology/gap-<e>-count  technologies in the gap
    technology/gap-<e>-break  1 where the gap's technologies differ from
                              the year before's (one entered or went
                              stale), else 0: a level shift there is
                              composition, not catch-up
  and
    technology/productivity-inflow  the gap closed since the year before,
                              over the technologies known both years (a
                              constant set across each pair of years), the
                              earlier year's weights: the inflow to
                              productivity from catching up (negative:
                              falling behind)."
  (:require [bnm.assimilate :as asm]
            [bnm.stockflow :as sf]
            [clojure.string :as str]))

(defn logistic
  "K / (1 + exp(-s (t - tau)))."
  ^double [K s tau t]
  (/ (double K) (+ 1.0 (Math/exp (- (* (double s) (- (double t) (double tau))))))))

(defn- finite? [x] (< (Math/abs (double x)) Double/POSITIVE_INFINITY))

;; ---------------------------------------------------------------- the fit

(defn- sse [obs K s tau]
  (reduce + 0.0 (for [[t x] obs :let [d (- (double x) (logistic K s tau t))]] (* d d))))

(defn- logit-fit
  "s and tau for a given ceiling K: weighted least squares of
  ln(x / (K - x)) on t, the weights (x (K - x) / K)^2 making it least
  squares in the level; nil without two usable points or a rising line."
  [obs K]
  (let [pts (for [[t x] obs :let [x (double x)] :when (and (> x (* 1e-3 K)) (< x (* 0.999 K)))]
              (let [w (Math/pow (/ (* x (- K x)) K) 2)]
                [(double t) (Math/log (/ x (- K x))) w]))]
    (when (>= (count pts) 2)
      (let [sw (reduce + (map #(nth % 2) pts))
            mt (/ (reduce + (map #(* (nth % 2) (nth % 0)) pts)) sw)
            mz (/ (reduce + (map #(* (nth % 2) (nth % 1)) pts)) sw)
            stt (reduce + (map (fn [[t _ w]] (* w (- t mt) (- t mt))) pts))
            stz (reduce + (map (fn [[t z w]] (* w (- t mt) (- z mz))) pts))]
        (when (pos? stt)
          (let [s (/ stz stt)]
            (when (pos? s) {:speed s :midpoint (- mt (/ mz s))})))))))

(defn- profile
  "The best {:ceiling :speed :midpoint :sse} over K in [lo hi]: a log grid,
  then golden-section search around its best point."
  [obs lo hi]
  (let [at (fn [K] (when-let [{:keys [speed midpoint]} (logit-fit obs K)]
                     {:ceiling K :speed speed :midpoint midpoint :sse (sse obs K speed midpoint)}))
        n 24
        llo (Math/log lo) lhi (Math/log hi)
        grid (vec (for [i (range (inc n))] (Math/exp (+ llo (* i (/ (- lhi llo) n))))))
        scored (keep-indexed (fn [i K] (when-let [r (at K)] (assoc r :i i))) grid)]
    (when (seq scored)
      (let [best (apply min-key :sse scored)
            i (:i best)
            a (Math/log (grid (max 0 (dec i)))) b (Math/log (grid (min n (inc i))))
            g 0.6180339887498949
            f #(or (some-> (at (Math/exp %)) :sse) Double/POSITIVE_INFINITY)]
        (loop [a a b b k 0]
          (if (or (> k 40) (< (- b a) 1e-6))
            (let [r (at (Math/exp (* 0.5 (+ a b))))]
              (if (and r (< (:sse r) (:sse best))) r (dissoc best :i)))
            (let [c (- b (* g (- b a))) d (+ a (* g (- b a)))]
              (if (< (f c) (f d)) (recur a d (inc k)) (recur c b (inc k))))))))))

(defn- solve3
  "x with A x = b for a 3x3 A (Gaussian elimination, partial pivoting), or nil."
  [A b]
  (let [m (mapv (fn [row bi] (conj (vec row) bi)) A b)
        m (reduce (fn [m c]
                    (when m
                      (let [p (apply max-key #(Math/abs (double (get-in m [% c]))) (range c 3))
                            m (assoc m c (m p) p (m c))
                            piv (get-in m [c c])]
                        (when (> (Math/abs (double piv)) 1e-300)
                          (reduce (fn [m r]
                                    (if (= r c)
                                      m
                                      (let [f (/ (get-in m [r c]) piv)]
                                        (assoc m r (mapv - (m r) (mapv #(* f %) (m c)))))))
                                  m (range 3))))))
                  m (range 3))]
    (when m (mapv (fn [r] (/ (get-in m [r 3]) (get-in m [r r]))) (range 3)))))

(defn- polish
  "Levenberg-Marquardt on all three parameters in the level, K kept in
  [kmin kmax] and s positive."
  [obs {:keys [ceiling speed midpoint]} kmin kmax]
  (loop [p [ceiling speed midpoint] lam 1e-3 k 0 cur (sse obs ceiling speed midpoint)]
    (if (>= k 60)
      p
      (let [[K s tau] p
            rows (for [[t x] obs
                       :let [t (double t) e (Math/exp (- (* s (- t tau)))) d (+ 1.0 e) f (/ K d)]]
                   [[(/ 1.0 d) (/ (* K e (- t tau)) (* d d)) (- (/ (* K e s) (* d d)))] (- (double x) f)])
            JtJ (vec (for [i (range 3)] (vec (for [j (range 3)] (reduce + 0.0 (map #(* ((first %) i) ((first %) j)) rows))))))
            Jtr (vec (for [i (range 3)] (reduce + 0.0 (map #(* ((first %) i) (second %)) rows))))
            A (vec (for [i (range 3)] (update (JtJ i) i #(* % (+ 1.0 lam)))))
            step (solve3 A Jtr)]
        (if (nil? step)
          p
          (let [[K' s' tau'] (mapv + p step)
                cand [(min kmax (max kmin K')) (max 1e-6 s') tau']
                new (apply sse obs cand)]
            (cond (< new cur) (if (< (- cur new) (* 1e-12 (max 1.0 cur))) cand (recur cand (* lam 0.3) (inc k) new))
                  (> lam 1e8) p
                  :else (recur p (* lam 10.0) (inc k) cur))))))))

(def ^:private identify-ratio
  "How much worse (in squared error) the best fit with twice the ceiling
  must be for the ceiling to count as identified."
  1.5)

(defn fit-logistic
  "The logistic through `obs` ([[t x]...]) by least squares in the level:
  {:ceiling :speed :midpoint :sse :n :identified}. The ceiling is searched
  from the highest level seen (a stock's ceiling is not below what it
  already holds) up to :cap (a share's 100) or, without one, three times
  the highest level. :identified when the data reach half the ceiling,
  the ceiling is off the search's upper bound, and doubling it (speed and
  midpoint refitted) worsens the fit by identify-ratio: data still on the
  exponential arm do not pin a ceiling. nil with fewer than :min-obs (5)
  points, no adoption, too little change (less than a tenth of the
  highest level) or no rise."
  [obs {:keys [cap min-obs] :or {min-obs 5}}]
  (let [obs (vec (sort-by first (filter (fn [[t x]] (and t x (finite? x))) obs)))
        xs (map #(double (second %)) obs)]
    (when (>= (count obs) min-obs)
      (let [xmax (reduce max xs) xmin (reduce min xs)]
        (when (and (pos? xmax) (>= (- xmax xmin) (* 0.1 xmax))
                   (>= (count (filter pos? xs)) 3))
          (let [lo (* 1.001 xmax)
                hi (if cap (max (double cap) (* 1.2 xmax)) (* 3.0 xmax))
                start (profile obs lo (max hi (* 1.01 lo)))]
            (when start
              (let [[K s tau] (polish obs start lo hi)
                    e (sse obs K s tau)
                    K2 (min (* 2.0 K) hi)
                    e2 (when (> K2 (* 1.05 K))
                         (when-let [{:keys [speed midpoint]} (logit-fit obs K2)]
                           (apply sse obs (polish obs {:ceiling K2 :speed speed :midpoint midpoint} K2 K2))))]
                {:ceiling K :speed s :midpoint tau :sse e :n (count obs)
                 :identified (boolean (and (>= xmax (* 0.5 K)) (< K (* 0.98 hi))
                                           e2 (> e2 (+ (* identify-ratio e) 1e-12))))}))))))))

;; ---------------------------------------------------------------- the stock

(defn diffusion-model
  "The diffusion stock (bnm.stockflow): adopters filled from outside by
  adoption = speed A (1 - A / ceiling), starting on the logistic at the
  run's start."
  [{:keys [ceiling speed midpoint]}]
  (sf/model {:params {:ceiling ceiling :speed speed :midpoint midpoint}
             :stocks {:adopters {:init (fn [v] (logistic (v :ceiling) (v :speed) (v :midpoint) (v :t)))}}
             :flows {:adoption {:from nil :to :adopters
                                :rate (fn [v] (* (v :speed) (v :adopters) (- 1.0 (/ (v :adopters) (v :ceiling)))))}}}))

(defn calibrate-diffusion
  "The posterior of the diffusion stock's parameters given `obs` ([[t x]],
  integer years) by bnm.assimilate/calibrate: priors :ceiling :speed
  :midpoint ({:mean :sd :log}), :noise the observation sd, :members,
  :seed, :dt (0.1)."
  [obs {:keys [ceiling speed midpoint noise members seed dt] :or {dt 0.1 members 100 seed 1}}]
  (let [obs (sort-by first obs)
        from (double (ffirst obs))]
    (asm/calibrate (diffusion-model {:ceiling (:mean ceiling) :speed (:mean speed) :midpoint (:mean midpoint)})
                   {:from from :dt dt :members members :seed seed
                    :priors {:ceiling ceiling :speed speed :midpoint midpoint}
                    :noise {:adopters noise}
                    :observations (into {} (for [[t x] obs] [(double t) {:adopters (double x)}]))})))

;; ---------------------------------------------------------------- the lag

(defn- invert
  "When the frontier's fitted logistic reached x, or nil."
  [{:keys [ceiling speed midpoint]} x]
  (when (and ceiling (< 0.0 x ceiling) (pos? speed))
    (- midpoint (/ (Math/log (- (/ ceiling x) 1.0)) speed))))

(def horizon
  "Years the frontier's fitted logistic is extrapolated beyond its data,
  before or after, to place a level it was not seen at."
  25)

(def reach-share
  "The highest share of the frontier's identified ceiling its fitted
  logistic is inverted at, ahead of its data."
  0.9)

(defn reach-time
  "The time the frontier ([[t x]] known) first reached level x: its path
  interpolated; before its first point, its fitted logistic inverted (no
  later than its first point) and censored at `horizon` years before it;
  beyond its highest, where the ceiling is identified, its fitted
  logistic inverted at the level but no higher than reach-share of the
  ceiling (near and above the ceiling the inverse runs off to infinity,
  and a ceiling identified on a plateau is often passed later: US
  internet at 74% in 2008), no earlier than its last point and censored
  at `horizon` years after it; where it is not identified, the last point
  (at least level with the frontier: an unidentified curve is no guide
  ahead of its data). nil without a fit outside the data. Censoring keeps
  the countries farthest from the frontier, either way, rather than
  dropping them; monotone in the level."
  [frontier fit x]
  (let [fr (vec (sort-by first frontier)) x (double x)]
    (when (seq fr)
      (let [i (first (keep-indexed (fn [i [_ v]] (when (>= (double v) x) i)) fr))
            t0 (double (first (fr 0))) t1 (double (first (peek fr)))]
        (cond (nil? i) (when fit
                         (if (:identified fit)
                           (min (+ t1 horizon)
                                (max t1 (or (invert fit (min x (* reach-share (:ceiling fit)))) t1)))
                           t1))
              (zero? i) (when fit
                          (when-let [t (invert fit x)]
                            (max (- t0 horizon) (min t t0))))
              :else (let [[ta va] (fr (dec i)) [tb vb] (fr i)
                          ;; the highest level before i is below x; interpolate from the last point
                          va (double va) vb (double vb)]
                      (+ (double ta) (* (- tb ta) (/ (- x va) (- vb va))))))))))

(defn- median [xs]
  (let [v (vec (sort xs)) n (count v)]
    (when (pos? n) (if (odd? n) (v (quot n 2)) (* 0.5 (+ (v (dec (quot n 2))) (v (quot n 2))))))))

(defn lag
  "Years the country ([[t x]] known) is behind the frontier: the median
  over its observations in the last :window (4) years before its latest
  of t - (when the frontier first reached its level); nil without
  adoption."
  [frontier fit country {:keys [window] :or {window 4}}]
  (let [c (filter #(pos? (double (second %))) country)]
    (when (seq c)
      (let [last-t (reduce max (map first c))]
        (median (keep (fn [[t x]] (when (>= t (- last-t window))
                                    (some->> (reach-time frontier fit x) (- (double t)))))
                      c))))))

;; ---------------------------------------------------------------- reading as of a year

(defn as-of
  "[[t x]] of the entries known by year y, oldest first."
  [entries y]
  (vec (for [{:keys [t known x]} (sort-by :t entries) :when (<= known y)] [t x])))

(defn latest
  "The latest value known by year y whose data year is no more than
  `stale` years before it, or nil."
  [entries y stale]
  (some->> entries
           (filter #(and (<= (:known %) y) (>= (:t %) (- y stale))))
           seq (apply max-key :t) :x))

(defn per-million
  "Entries of num per million of pop, matched on data year, known when both are."
  [num pop]
  (let [p (into {} (map (juxt :t identity)) pop)]
    (vec (for [{:keys [t known x]} (sort-by :t num)
               :let [q (p t)] :when (and q (pos? (double (:x q))))]
           {:t t :known (max known (:known q)) :x (/ (* (double x) 1e6) (double (:x q)))}))))

;; ---------------------------------------------------------------- states and rows

(defn- saturation
  "The level counted as full adoption: the technology's fixed saturation
  (shares, subscriptions per person), else the frontier's identified
  ceiling, else the cap (a share's 100), else the frontier's highest level
  known."
  [{fixed :saturation cap :cap} fit fobs]
  (or fixed
      (when (:identified fit) (:ceiling fit))
      cap
      (when (seq fobs) (reduce max (map #(double (second %)) fobs)))))

(defn- share [x sat] (min 1.0 (max 0.0 (/ (double x) sat))))

(defn tech-state
  "A technology's state at a country in year y, or nil where its level is
  not known: {:level :adoption :frontier-adoption :gap :lag :speed
  :midpoint :ceiling}, the frontier's data and fit as of y given."
  [{:keys [cfg fobs ffit cobs entries fentries year frontier? stale] :or {stale 4}}]
  (when-let [level (latest entries year stale)]
    (let [sat (saturation cfg ffit fobs)
          sat (when (and sat (pos? sat)) sat)
          flevel (latest fentries year stale)
          fit (fit-logistic cobs cfg)
          adoption (when sat (share level sat))
          fadoption (when (and sat flevel) (share flevel sat))]
      {:level level
       :adoption adoption
       :frontier-adoption fadoption
       :gap (when (and adoption fadoption) (if frontier? 0.0 (- fadoption adoption)))
       :lag (if frontier? 0.0 (lag fobs ffit cobs {}))
       :speed (:speed fit)
       :midpoint (:midpoint fit)
       :ceiling (when (:identified fit) (:ceiling fit))})))

(defn aggregate
  "{:gap :lag :count :techs} across a country's {tech state}, over the
  technologies in `era` (a set of names): the gap weighted by the
  frontier's adoption, the mean lag where known, how many technologies
  the gap is over and which."
  [states era]
  (let [st (select-keys states era)
        g (filter #(and (:gap (val %)) (:frontier-adoption (val %))) st)
        w #(max 0.0 (double (:frontier-adoption (val %))))
        sw (reduce + 0.0 (map w g))
        lags (keep :lag (vals st))]
    {:gap (when (pos? sw) (/ (reduce + 0.0 (map #(* (w %) (:gap (val %))) g)) sw))
     :lag (when (seq lags) (/ (reduce + lags) (count lags)))
     :count (when (seq g) (double (count g)))
     :techs (set (keys g))}))

(defn inflow
  "The gap closed from `prev` to `cur` ({tech state}), over the
  technologies with a gap in both, weighted by the earlier frontier's
  adoption; nil with none."
  [prev cur]
  (let [ks (filter #(and (get-in prev [% :gap]) (get-in cur [% :gap]) (get-in prev [% :frontier-adoption])) (keys cur))
        w #(max 0.0 (double (get-in prev [% :frontier-adoption])))
        sw (reduce + 0.0 (map w ks))]
    (when (pos? sw)
      (/ (reduce + 0.0 (map #(* (w %) (- (get-in prev [% :gap]) (get-in cur [% :gap]))) ks)) sw))))

(def ^:private state-vars
  [[:level "level"] [:adoption "adoption"] [:lag "lag"] [:speed "speed"] [:midpoint "midpoint"] [:ceiling "ceiling"]])

(defn var-name
  "technology/<parts joined by ->, kebab-case (bnm.states merges every
  stock file by variable name; food/ and tension/ read the same way)."
  [& parts]
  (str "technology/" (str/join "-" (map #(str/replace (str/lower-case (str %)) #"[_\s]+" "-") parts))))

(defn- row [iso year variable value]
  (when (and value (finite? value))
    {:iso iso :year year :variable variable :value (double value)}))

(defn states
  "{[iso year] {tech state}} for :techs {name cfg} over :data {tech {iso
  entries}}, :frontier the frontier's iso, :years; the frontier's fit once
  per technology and year, the countries in parallel."
  [{:keys [techs data frontier years stale] :or {stale 4}}]
  (let [fronts (into {} (pmap (fn [[k cfg]]
                                (let [fentries (get-in data [k frontier])]
                                  [k (into {} (for [y years :let [fobs (as-of fentries y)]]
                                                [y {:fobs fobs :ffit (fit-logistic fobs cfg)}]))]))
                              techs))
        parts (pmap (fn [[k cfg iso entries]]
                      (let [fentries (get-in data [k frontier])]
                        (into {} (for [y years
                                       :let [{:keys [fobs ffit]} (get-in fronts [k y])
                                             s (tech-state {:cfg cfg :fobs fobs :ffit ffit :cobs (as-of entries y)
                                                            :entries entries :fentries fentries :year y
                                                            :frontier? (= iso frontier) :stale stale})]
                                       :when s]
                                   [[iso y] {k s}]))))
                    (for [[k cfg] techs [iso entries] (get data k)] [k cfg iso entries]))]
    (apply merge-with merge {} parts)))

(defn technology-rows
  "Rows {:iso :year :variable :value} (see the namespace) from `states`'
  options and :eras {era #{tech}} (default one era \"all\" of every
  technology), sorted by iso, year and variable; nothing written where
  unknown."
  [{:keys [techs eras] :as opts}]
  (let [st (or (states opts) {})
        eras (or eras {"all" (set (keys techs))})]
    (->> (for [[[iso y] by] st
               r (concat (for [[k s] by [kw nm] state-vars] (row iso y (var-name k nm) (kw s)))
                         (for [[era ts] eras
                               :let [a (aggregate by ts)
                                     p (some-> (st [iso (dec y)]) (aggregate ts))]
                               r [(row iso y (var-name "gap" era) (:gap a))
                                  (row iso y (var-name "lag" era) (:lag a))
                                  (row iso y (var-name "gap" era "count") (:count a))
                                  (when (:gap a)
                                    (row iso y (var-name "gap" era "break")
                                         (if (and p (:gap p) (not= (:techs p) (:techs a))) 1.0 0.0)))]]
                           r)
                         [(when-let [p (st [iso (dec y)])] (row iso y (var-name "productivity-inflow") (inflow p by)))])
               :when r]
           r)
         (sort-by (juxt :iso :year :variable))
         vec)))

(defn latest-rows
  "Rows of each {iso {variable entries}} series' latest value known each
  year (data no older than `stale` years)."
  [series years stale]
  (vec (for [[iso by] series [v es] by y years
             :let [r (row iso y v (latest es y stale))] :when r]
         r)))
