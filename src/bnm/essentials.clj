(ns bnm.essentials
  "Essential products found from the data (docs/ESSENTIALS.md, epic bnm-1pq).

  rise (bead bnm-1pq.3, section 8 step 2): which HS6 products are rising
  in world trade. For each product and data year Y, the Theil-Sen slope
  (the median of the pairwise slopes, robust to a few wild years) over the
  window Y-7..Y of log(share of world trade value) and of log(tonnes);
  the score is the smaller of the two, so a price spike (value up, tonnes
  flat) or a quantity artefact (tonnes up, value flat) does not count as a
  rise. A missing year is no trade: share 0, tonnes 0.

  Eligible: a window of 8 data years, a mean share over Y-2..Y of at least
  5 ppm of world trade, and no revision step. HS revisions (1996, 2002,
  2007, 2012, 2017, 2022) are converted back to the older edition and
  move goods between codes; a product is excluded while a revision year in
  its window carries a jump in either log larger than 0.5 and larger than
  half of the window's whole change. Once the revision year leaves the
  window it is ranked again.

  Eligible products are ranked per edition and year, 1 the fastest riser;
  :pct is rank / number ranked (top 1%: pct <= 0.01).

  Rows are hs6-world.csv's (scripts/baci.py): {:hs edition :year data
  year :known year known :hs6 :share :tonnes}. BACI for year Y is known in
  Y+2. rise at data year Y reads only rows with :year <= Y, which are
  known by Y+2 (the :known it reports); rise-known at year A reads only
  rows with :known <= A.")

(def revisions
  "HS revision years: the first year each new edition is in force."
  [1996 2002 2007 2012 2017 2022])

(def defaults
  {:window 8          ; years in the trend
   :min-share 5e-6    ; mean share of world trade over the last 3 years
   :eps 1e-9          ; log(share + eps): a product's absence is not -inf
   :jump 0.5          ; a revision step is a log jump above this...
   :carry 0.5})       ; ...carrying more than this share of the window's change

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
  ({year log-value}) over the window years `ys`."
  [logs ys {:keys [jump carry]}]
  (let [lo (first ys) hi (peek ys)
        total (+ 1e-9 (Math/abs (- (logs hi) (logs lo))))
        jumps (for [r revisions :when (and (< lo r) (<= r hi))]
                (Math/abs (- (logs r) (logs (dec r)))))]
    (boolean (when (seq jumps)
               (let [m (apply max jumps)]
                 (and (> m jump) (> (/ m total) carry)))))))

(defn- score-product
  "The rise entry of one product at data year `y`, from its rows by year
  `by-year` (already cut to years <= y)."
  [by-year y {:keys [window min-share eps] :as opts}]
  (let [ys (vec (range (- y (dec window)) (inc y)))
        share (fn [t] (double (or (:share (by-year t)) 0.0)))
        tonnes (fn [t] (double (or (:tonnes (by-year t)) 0.0)))
        ls (into {} (for [t ys] [t (Math/log (+ (share t) eps))]))
        lt (into {} (for [t ys] [t (Math/log (+ (tonnes t) 1.0))]))
        ss (theil-sen (map (juxt identity ls) ys))
        st (theil-sen (map (juxt identity lt) ys))
        level (/ (reduce + (map share (subvec ys (- window 3)))) 3.0)
        step (or (revision-step? ls ys opts) (revision-step? lt ys opts))]
    {:share-slope ss :tonnes-slope st :score (min ss st)
     :level level :revision-step step
     :eligible (and (>= level min-share) (not step))}))

(defn- rank-group
  "Rank the eligible entries of one edition, best score first."
  [entries]
  (let [scores (sort > (keep #(when (:eligible %) (:score %)) entries))
        n (count scores)]
    (for [e entries]
      (if (:eligible e)
        (let [r (inc (count (take-while #(> % (:score e)) scores)))]
          (assoc e :rank r :n n :pct (/ (double r) n)))
        (assoc e :rank nil :n n :pct nil)))))

(defn rise
  "Rise entries at data year `y` of every product in `rows` with a row in
  the window, ranked per edition: {:hs :hs6 :year :known :score
  :share-slope :tonnes-slope :level :revision-step :eligible :rank :n
  :pct}, sorted by edition and rank (unranked last). Reads only rows with
  :year <= y. Empty before a full window of data years exists."
  ([rows y] (rise rows y {}))
  ([rows y opts]
   (let [{:keys [window] :as o} (merge defaults opts)
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
          (mapcat (fn [[_ es]] (rank-group es)))
          (sort-by (juxt :hs #(or (:rank %) Long/MAX_VALUE) :hs6))
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
