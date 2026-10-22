(ns bnm.essentials
  "Essential products found from the data (docs/ESSENTIALS.md, epic bnm-1pq).

  rise (bead bnm-1pq.3, section 8 step 2): which HS6 products are rising
  in world trade. For each product and data year Y, the Theil-Sen slope
  (the median of the pairwise slopes, robust to a few wild years) over the
  window Y-7..Y of log(share of world trade value) and of log(tonnes);
  the score is the smaller of the two, so a price spike (value up, tonnes
  flat) or a quantity artefact (tonnes up, value flat) does not count as a
  rise. A missing year is no trade: share 0, tonnes 0. BACI leaves the
  quantity blank for some flows, so a year's world tonnes cover only part
  of its value (:tonnes-cover, from scripts/baci.py): they are scaled up
  by 1 / cover (the uncovered flows at the covered unit value), else a
  coverage that rises over the window reads as a tonnes rise (cobalt
  oxides 2010-14 have 64-87%). A year with a row but no tonnes, or with a
  cover under :min-cover, is unknown for the tonnes trend, not zero: the
  slope is taken over the other years. With fewer than :min-tonnes-years known
  years in the window there is no tonnes trend, so no guard against a price
  spike: the product is flagged :tonnes-gap and not ranked. Theil-Sen
  already shrugs off two bad points of eight (28 pairwise slopes, 13 touch
  them), which is why 6 of 8 is the floor; it was set a priori, not fitted.

  Eligible: a window of 8 data years, a mean share over Y-2..Y of at least
  5 ppm of world trade, and no revision step. HS revisions (1996, 2002,
  2007, 2012, 2017, 2022) are converted back to the older edition and
  move goods between codes; a product is excluded while a revision year in
  its window carries a jump in either log larger than 0.5 and larger than
  half of the window's whole change. Once the revision year leaves the
  window it is ranked again.

  One rank per data year: the eligible products of :rank-edition (HS92,
  the only edition that runs 1995-2024) are ranked, 1 the fastest riser;
  :pct is rank / number ranked (top 1%: pct <= 0.01). That is the rank the
  model reads. Rows of later editions (HS12 from 2012, HS17 from 2017) are
  sub-node detail for codes HS92 lumps (850760 Li-ion inside 850780,
  870380 battery cars inside 870390): they are scored the same way and get
  :detail-rank and :detail-n within their own edition, never :rank. Their
  ranks are not comparable with HS92's (different product sets and
  windows) and a product is never counted twice.

  Thresholds and what was chosen when (PRINCIPLES 9). The 8-year window,
  the 5 ppm floor and the revision filter (a jump above 0.5 in log carrying
  more than half the window's change) come from the exploratory scan of
  docs/ESSENTIALS.md section 6, run before the registered targets were
  written; the targets themselves (lithium, cobalt in the top 1% by 2018-19)
  were then set from that scan's observed ranks. So the targets are
  in-sample checks of this code, not validation. No record says the
  thresholds were searched, but they were fixed by someone who had seen
  the lithium and cobalt ranks; tools/bnm/essentials_check.clj prints the
  targets under other thresholds, and the held-out test is the precision
  against the EU 2023 and USGS 2022 critical lists (`precision`), which
  played no part in any choice.

  Rows are hs6-world.csv's (scripts/baci.py): {:hs edition :year data
  year :known year known :hs6 :share :tonnes}. BACI for year Y is known in
  Y+2. rise at data year Y reads only rows with :year <= Y, which are
  known by Y+2 (the :known it reports); rise-known at year A reads only
  rows with :known <= A.

  edges (bead bnm-1pq.8, section 8 step 7): which growing products each
  rising candidate may feed, from where it is imported and where they are
  exported (hs6-country.csv.gz): the candidate's importers overlap the
  output's exporters, more so than in the early window, and the
  candidate's import growth co-moves with the output's export growth
  across countries. Both are weighed against like-sized products standing
  in for the candidate (the placebo). EXIOBASE does not split the new
  products, so this is how a lithium -> battery link can come from the
  data; the recipes are the held-out check (tools/bnm/edges_check.clj).
  The downstream set ranks growth by value share alone: HS92's 850780
  (Li-ion inside all accumulators) loses tonnes 2015-19 while its value
  share rises, so the rise score's tonnes guard would drop it. That choice
  was made after seeing 850780's tonnes, so the Li-ion targets are not
  blind to it.")

(def revisions
  "HS revision years: the first year each new edition is in force."
  [1996 2002 2007 2012 2017 2022])

(def defaults
  {:window 8          ; years in the trend
   :min-share 5e-6    ; mean share of world trade over the last 3 years
   :eps 1e-9          ; log(share + eps): a product's absence is not -inf
   :jump 0.5          ; a revision step is a log jump above this...
   :carry 0.5         ; ...carrying more than this share of the window's change
   :min-cover 0.5     ; a year's tonnes count when they cover this share of its value
   :min-tonnes-years 6 ; years with known tonnes needed for a tonnes trend
   :rank-edition "HS92"})

(defn median
  "The median of `xs` (the mean of the middle two when even), nil if empty."
  [xs]
  (let [v (vec (sort xs)) n (count v)]
    (when (pos? n)
      (if (odd? n)
        (double (v (quot n 2)))
        (/ (+ (double (v (dec (quot n 2)))) (double (v (quot n 2)))) 2.0)))))

(defn theil-sen
  "The Theil-Sen slope of points [[x y] ...]: the median of the slopes
  between every pair with different x. nil with fewer than two x."
  [pts]
  (let [v (vec pts) n (count v)]
    (median (for [i (range n) j (range (inc i) n)
                  :let [[xi yi] (v i) [xj yj] (v j)]
                  :when (not= xi xj)]
              (/ (- (double yj) (double yi)) (- (double xj) (double xi)))))))

(defn- revision-step?
  "Whether a revision year in the window carries the change of `logs`
  ({year log-value}, unknown years absent) over the window years `ys`.
  A revision year counts when it and the year before are both known."
  [logs ys {:keys [jump carry]}]
  (let [known (filterv #(contains? logs %) ys)]
    (boolean
     (when (>= (count known) 2)
       (let [lo (first known) hi (peek known)
             total (+ 1e-9 (Math/abs (- (logs hi) (logs lo))))
             jumps (for [r revisions
                         :when (and (< lo r) (<= r hi) (contains? logs r) (contains? logs (dec r)))]
                     (Math/abs (- (logs r) (logs (dec r)))))]
         (when (seq jumps)
           (let [m (apply max jumps)]
             (and (> m jump) (> (/ m total) carry)))))))))

(defn- score-product
  "The rise entry of one product at data year `y`, from its rows by year
  `by-year` (already cut to years <= y)."
  [by-year y {:keys [window min-share eps min-cover min-tonnes-years] :as opts}]
  (let [ys (vec (range (- y (dec window)) (inc y)))
        share (fn [t] (double (or (:share (by-year t)) 0.0)))
        ;; tonnes of year t: 0 without a row (no trade), nil when unknown
        tonnes (fn [t] (if-let [r (by-year t)]
                         (let [q (:tonnes r) c (:tonnes-cover r)]
                           (when (and q (or (nil? c) (>= c min-cover)))
                             (/ (double q) (double (or c 1.0)))))
                         0.0))
        ls (into {} (for [t ys] [t (Math/log (+ (share t) eps))]))
        lt (into {} (for [t ys :let [q (tonnes t)] :when q] [t (Math/log (+ q 1.0))]))
        gap (< (count lt) min-tonnes-years)
        ss (theil-sen (map (juxt identity ls) ys))
        st (when-not gap (theil-sen (sort-by first lt)))
        level (/ (reduce + (map share (subvec ys (- window 3)))) 3.0)
        step (or (revision-step? ls ys opts) (and (not gap) (revision-step? lt ys opts)))]
    {:share-slope ss :tonnes-slope st :score (when st (min ss st))
     :level level :revision-step step :tonnes-gap gap
     :eligible (and (>= level min-share) (not step) (not gap))}))

(defn- rank-group
  "Rank the eligible entries of one edition, best score first, as :rank
  :n :pct when `ranked?`, else as :detail-rank :detail-n."
  [entries ranked?]
  (let [scores (sort > (keep #(when (:eligible %) (:score %)) entries))
        n (count scores)
        rank-of (fn [e] (when (:eligible e)
                          (inc (count (take-while #(> % (:score e)) scores)))))]
    (for [e entries :let [r (rank-of e)]]
      (if ranked?
        (assoc e :rank r :n n :pct (when r (/ (double r) n)))
        (assoc e :rank nil :n nil :pct nil :detail-rank r :detail-n n)))))

(defn rise
  "Rise entries at data year `y` of every product in `rows` with a row in
  the window: {:hs :hs6 :year :known :score :share-slope :tonnes-slope
  :level :revision-step :tonnes-gap :eligible :rank :n :pct}, ranked on
  :rank-edition; other editions' entries carry :detail-rank :detail-n
  instead (see the namespace doc). Sorted by edition and rank (unranked
  last). Reads only rows with :year <= y. Empty before a full window of
  data years exists."
  ([rows y] (rise rows y {}))
  ([rows y opts]
   (let [{:keys [window rank-edition] :as o} (merge defaults opts)
         lo (- y (dec window))
         seen (filter #(<= (:year %) y) rows)
         first-year (into {} (for [[hs rs] (group-by :hs seen)]
                               [hs (reduce min (map :year rs))]))
         known (reduce max 0 (map :known seen))]
     (->> seen
          (filter #(>= (:year %) lo))
          (group-by (juxt :hs :hs6))
          (keep (fn [[[hs hs6] rs]]
                  (when (<= (first-year hs) lo)
                    (assoc (score-product (into {} (map (juxt :year identity)) rs) y o)
                           :hs hs :hs6 hs6 :year y :known known))))
          (group-by :hs)
          (mapcat (fn [[hs es]] (rank-group es (= hs rank-edition))))
          (sort-by (juxt :hs #(or (:rank %) (:detail-rank %) Long/MAX_VALUE) :hs6))
          vec))))

(defn rise-known
  "rise as of year `a`: only rows with :known <= a are read, and the data
  year is the latest one among them."
  ([rows a] (rise-known rows a {}))
  ([rows a opts]
   (let [kn (filterv #(<= (:known %) a) rows)]
     (if (empty? kn)
       []
       (rise kn (reduce max (map :year kn)) opts)))))

;; ---------------------------------------------------------------- held-out precision

(defn- log-choose [n k]
  (if (or (neg? k) (> k n))
    Double/NEGATIVE_INFINITY
    (reduce + 0.0 (for [i (range k)] (- (Math/log (- n i)) (Math/log (inc i)))))))

(defn hypergeometric-tail
  "P(X >= x) for X the labelled count in `k` draws without replacement from
  `n` items of which `m` are labelled."
  [n m k x]
  (let [den (log-choose n k)]
    (min 1.0 (reduce + 0.0 (for [i (range (max x 0) (inc (min m k)))]
                             (Math/exp (- (+ (log-choose m i) (log-choose (- n m) (- k i))) den)))))))

(defn precision
  "Precision of the top `k` ranked `entries` (those with a :rank, ties
  broken by :hs6) against the set `labels` of hs6 codes, and what a random
  k drawn from the ranked products would give: {:k :hits :precision :n
  (products ranked) :labeled (labelled among them) :base (the expected
  precision of a random k) :p (P(hits or more) by chance, hypergeometric)}.
  Labels outside the ranked set (ineligible, absent) do not count."
  [entries labels k]
  (let [ranked (sort-by (juxt :rank :hs6) (filter :rank entries))
        n (count ranked)
        k (min k n)
        m (count (filter #(contains? labels (:hs6 %)) ranked))
        hits (count (filter #(contains? labels (:hs6 %)) (take k ranked)))]
    {:k k :hits hits :precision (if (pos? k) (/ (double hits) k) 0.0)
     :n n :labeled m :base (if (pos? n) (/ (double m) n) 0.0)
     :p (hypergeometric-tail n m k hits)}))

;; ---------------------------------------------------------------- edge proposal

(def edge-defaults
  "Edge proposal (bead bnm-1pq.8, section 8 step 7). Candidates are the
  rise top :cand-pct; the downstream set is the :down-pct (of the ranked
  count) fastest growers in world-trade value share; each candidate is
  weighed against :placebos like-sized products; an edge is proposed at
  placebo p <= :alpha."
  {:cand-pct 0.01 :down-pct 0.10 :placebos 39 :alpha 0.05 :cap 320})

(defn overlap
  "Sum over countries of min(x_c / sum x, y_c / sum y): the share of x's
  distribution that y's matches, 0-1. 0 when either is empty."
  ^double [^doubles x ^doubles y]
  (let [n (min (alength x) (alength y))
        sx (areduce x i s 0.0 (+ s (aget x i)))
        sy (areduce y i s 0.0 (+ s (aget y i)))]
    (if (or (<= sx 0.0) (<= sy 0.0))
      0.0
      (loop [i 0 s 0.0]
        (if (< i n)
          (recur (inc i) (+ s (Math/min (/ (aget x i) sx) (/ (aget y i) sy))))
          s)))))

(defn weighted-corr
  "The correlation of x and y with weights w (zero weights drop a point);
  nil when either has no weighted variance."
  [^doubles x ^doubles y ^doubles w]
  (let [n (alength w)
        sw (areduce w i s 0.0 (+ s (aget w i)))]
    (when (pos? sw)
      (let [mx (/ (areduce w i s 0.0 (+ s (* (aget w i) (aget x i)))) sw)
            my (/ (areduce w i s 0.0 (+ s (* (aget w i) (aget y i)))) sw)]
        (loop [i 0 sxy 0.0 sxx 0.0 syy 0.0]
          (if (< i n)
            (let [wi (aget w i) dx (- (aget x i) mx) dy (- (aget y i) my)]
              (recur (inc i) (+ sxy (* wi dx dy)) (+ sxx (* wi dx dx)) (+ syy (* wi dy dy))))
            (when (and (> sxx 1e-300) (> syy 1e-300))
              (/ sxy (Math/sqrt (* sxx syy))))))))))

(defn country-panel
  "Country x product rows {:hs6 :year :country :exports :imports} (one
  edition's hs6-country.csv.gz rows) as {:index {country i} :flows {[hs6
  year] doubles}}: exports at i, imports at cap + i. At most `cap`
  countries."
  ([rows] (country-panel rows (:cap edge-defaults)))
  ([rows cap]
   (let [index (volatile! {})
         flows (reduce
                (fn [m {:keys [hs6 year country exports imports]}]
                  (let [i (or (@index country)
                              (let [i (count @index)]
                                (when (>= i cap)
                                  (throw (ex-info "more countries than the panel holds" {:cap cap})))
                                (vswap! index assoc country i)
                                i))
                        k [hs6 year]
                        ^doubles a (or (get m k) (double-array (* 2 cap)))]
                    (aset a i (+ (aget a i) (double (or exports 0.0))))
                    (aset a (+ cap i) (+ (aget a (+ cap i)) (double (or imports 0.0))))
                    (if (contains? m k) m (assoc! m k a))))
                (transient {})
                rows)]
     {:index @index :cap cap :flows (persistent! flows)})))

(defn- window-mean
  "The mean over `years` of a product's exports (:ex) or imports (:im) by
  country, as an array over the panel's countries (zeros without data)."
  [{:keys [index cap flows]} hs6 years side]
  (let [n (count index)
        off (if (= side :ex) 0 cap)
        out (double-array n)
        k (double (count years))]
    (doseq [y years]
      (when-let [^doubles a (get flows [hs6 y])]
        (dotimes [i n] (aset out i (+ (aget out i) (/ (aget a (+ off i)) k))))))
    out))

(defn- normalized ^doubles [^doubles a]
  (let [s (areduce a i s 0.0 (+ s (aget a i)))
        out (double-array (alength a))]
    (when (pos? s)
      (dotimes [i (alength a)] (aset out i (/ (aget a i) s))))
    out))

(defn- prep
  "A side's windows with what pair-score needs: the windows as shares
  (:rn, :en), the log growth (+1 kUSD) and the sum of the two shares."
  [{:keys [^doubles early ^doubles recent] :as side}]
  (if (:growth side)
    side
    (let [n (alength early)
          rn (normalized recent) en (normalized early)
          g (double-array n) w (double-array n)]
      (dotimes [i n]
        (aset g i (Math/log (/ (+ 1.0 (aget recent i)) (+ 1.0 (aget early i)))))
        (aset w i (+ (aget rn i) (aget en i))))
      (assoc side :rn rn :en en :growth g :w w))))

(defn- min-sum ^double [^doubles x ^doubles y]
  (let [n (min (alength x) (alength y))]
    (loop [i 0 s 0.0]
      (if (< i n) (recur (inc i) (+ s (Math/min (aget x i) (aget y i)))) s))))

(defn pair-score
  "The two signals of input a -> output b from a's import windows and b's
  export windows ({:early :recent} arrays by country): :overlap (recent
  importers of a against recent exporters of b, as in `overlap`),
  :overlap-early, and :comove, the correlation across countries of a's
  import growth with b's export growth (log of recent over early, +1
  kUSD), weighted by the countries' shares in the four windows (0 when
  undefined)."
  [im ex]
  (let [im (prep im) ex (prep ex)
        ^doubles wa (:w im) ^doubles wb (:w ex)
        w (double-array (alength wa))]
    (dotimes [i (alength wa)] (aset w i (+ (aget wa i) (aget wb i))))
    {:overlap (min-sum (:rn im) (:rn ex)) :overlap-early (min-sum (:en im) (:en ex))
     :comove (or (weighted-corr (:growth im) (:growth ex) w) 0.0)}))

(defn placebo-pool
  "The `n` products of the edition nearest `code` in log world value at
  data year `y` (ties by code), other than `code` and its HS4 heading."
  ([world code y n] (placebo-pool world code y n {}))
  ([world code y n opts]
   (let [ed (:rank-edition (merge defaults opts))
         vals (into {} (for [r world
                             :when (and (= y (:year r)) (= ed (:hs r)) (:value r) (pos? (:value r)))]
                         [(:hs6 r) (double (:value r))]))]
     (if-let [v (vals code)]
       (let [h4 (subs code 0 4)
             lv (Math/log v)]
         (->> (dissoc vals code)
              (remove (fn [[c _]] (= h4 (subs c 0 4))))
              (sort-by (fn [[c x]] [(Math/abs (- (Math/log x) lv)) c]))
              (take n)
              (mapv key)))
       []))))

(defn- moments [xs]
  (let [n (count xs) m (/ (reduce + xs) (max n 1))
        sd (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (max 1 (dec n))))]
    [m sd]))

(defn edge-plan
  "What `edges` scores at data year `y`: {:candidates (set) :growing
  (output codes) :pools {candidate [placebo codes]} :known}, from `world`
  rows with :year <= y (see `edges`)."
  [world y opts]
  (let [{:keys [cand-pct down-pct placebos candidates min-share rank-edition]}
        (merge defaults edge-defaults opts)
        seen (filterv #(<= (:year %) y) world)
        r (filter #(= rank-edition (:hs %)) (rise seen y opts))
        n-ranked (count (filter :rank r))
        cands (or candidates
                  (set (keep #(when (and (:pct %) (<= (:pct %) cand-pct)) (:hs6 %)) r)))
        year-world (filterv #(= y (:year %)) seen)]
    {:candidates cands
     :growing (->> r
                   (filter #(and (>= (:level %) min-share) (not (:revision-step %))
                                 (pos? (:share-slope %))))
                   (sort-by (juxt (comp - :share-slope) :hs6))
                   (take (long (Math/ceil (* down-pct n-ranked))))
                   (mapv :hs6))
     :pools (into {} (for [a cands] [a (placebo-pool year-world a y placebos opts)]))
     :known (reduce max 0 (map :known seen))}))

(defn edges
  "Proposed edges at data year `y` from `world` (hs6-world rows with
  :value, the rise input) and `panel` (country-panel of the edition's
  country rows). For each candidate input a (the rise top :cand-pct, or
  :candidates) and each growing output b (the :down-pct fastest risers in
  value share among products above the share floor with no revision step,
  not a's HS4), pair-score over the early (Y-7..Y-5) and recent (Y-2..Y)
  windows. Each signal is standardised against the same score for a's
  placebo pool (placebo-pool: like-sized products standing in for a, their
  importers against b's exporters); :z is the mean of the overlap and
  comovement z-scores times sqrt 2; :p is the share of placebos scoring at
  least :z (with a +1, so the floor is 1/(placebos+1)). :proposed when the
  overlap grew (:overlap-growth > 0), :z > 0 and :p <= :alpha. :rank is b's
  place among a's outputs by :z. Reads only rows with :year <= y (rise and
  the windows); :known is the latest year known."
  ([world panel y] (edges world panel y {}))
  ([world panel y opts]
   (let [{:keys [alpha window]} (merge defaults edge-defaults opts)
         {:keys [candidates growing pools known]} (edge-plan world y opts)
         lo (- y (dec window))
         early (range lo (+ lo 3))
         recent (range (- y 2) (inc y))
         side (memoize (fn [hs6 s] (prep {:early (window-mean panel hs6 early s)
                                          :recent (window-mean panel hs6 recent s)})))
         score (memoize (fn [a b] (pair-score (side a :im) (side b :ex))))
         sqrt2 (Math/sqrt 2.0)]
     (vec
      (for [a (sort candidates)
            :let [pool (pools a)
                  outs (filterv #(and (not= a %) (not= (subs a 0 4) (subs % 0 4))) growing)
                  es (for [b outs]
                       (let [s (score a b)
                             ps (mapv #(score % b) pool)
                             [mo so] (moments (map :overlap ps))
                             [mc sc] (moments (map :comove ps))
                             z (fn [{:keys [overlap comove]}]
                                 (/ (+ (if (pos? so) (/ (- overlap mo) so) 0.0)
                                       (if (pos? sc) (/ (- comove mc) sc) 0.0))
                                    sqrt2))
                             za (z s)
                             p (/ (+ 1.0 (count (filter #(>= (z %) za) ps))) (+ 1.0 (count ps)))
                             growth (- (:overlap s) (:overlap-early s))]
                         (assoc s :input a :output b :year y :known known
                                :overlap-growth growth :z za :p p :placebos (count ps)
                                :proposed (boolean (and (pos? growth) (pos? za) (<= p alpha))))))]
            [i e] (map-indexed vector (sort-by (juxt (comp - :z) :output) es))]
        (assoc e :rank (inc i)))))))

;; ---------------------------------------------------------------- known edges (validation)

(defn recipe-links
  "#{[input output]} EXIOBASE pairs whose median input share over regions
  (rows {:region :output :input :share}) is at least `floor`."
  [rows floor]
  (into #{} (keep (fn [[k rs]] (when (>= (median (map :share rs)) floor) k)))
        (group-by (juxt :input :output) rows)))

(defn known-edge?
  "Whether a -> b is a recipe link between any of their EXIOBASE parents
  (`parents`: hs6 -> #{product})."
  [links parents a b]
  (boolean (some (fn [pa] (some #(contains? links [pa %]) (parents b))) (parents a))))

(defn edge-precision
  "Precision of the proposed edges against `known?` (fn a b), and the base
  rate over every scored pair: {:proposed :hits :precision :pairs :known
  :base :p (hypergeometric, P(hits or more) by chance) :top-n :top-hits
  :top-precision (each input's rank-1 output, proposed or not)}."
  [es known?]
  (let [k? (fn [e] (known? (:input e) (:output e)))
        n (count es) m (count (filter k? es))
        prop (filter :proposed es) kp (count prop) hits (count (filter k? prop))
        tops (filter #(= 1 (:rank %)) es) th (count (filter k? tops))]
    {:proposed kp :hits hits :precision (if (pos? kp) (/ (double hits) kp) 0.0)
     :pairs n :known m :base (if (pos? n) (/ (double m) n) 0.0)
     :p (hypergeometric-tail n m kp hits)
     :top-n (count tops) :top-hits th
     :top-precision (if (seq tops) (/ (double th) (count tops)) 0.0)}))
