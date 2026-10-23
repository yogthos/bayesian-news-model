(ns bnm.stages
  "Stage concentration and processing displacement as stocks (beads
  bnm-1pq.4, bnm-3ka.3; docs/ESSENTIALS.md section 8 step 3,
  docs/ECONOMIC-CHARACTER.md 7d).

  Reads data/derived/supply/stages.csv (scripts/minerals.py: commodity,
  stage, iso, year, quantity, unit, source, known; one source per commodity
  and stage) and stage-plants.csv (plant capacities), and writes
  data/derived/stocks/stages.csv: iso,year,variable,value,top, the year a
  value is known at.

  Per commodity <c> and stage <s> (mine, smelter, refinery, chemical,
  separation), at iso WLD:
    stages/<c>-<s>-hhi          Herfindahl index of country shares of world
                                output
    stages/<c>-<s>-top-share    the largest country's share; its iso is in
                                the column top
    stages/<c>-<s>-world        world output, in the stage's unit
    stages/<c>-<s>-count        producing countries (bookkeeping)
    stages/<c>-<s>-state-year   the data year read (bookkeeping)
    stages/<c>-d                processing displacement, mine against the
                                last stage of the chain:
                                D = 1 - sum_c min(s_mine, s_proc), 0 when
                                each country processes its own ore's share,
                                1 when none does
    stages/<c>-d-<a>-<b>        D between stages a and b (mine against each
                                later stage, and each processing step
                                against the next)
    stages/<c>-non-resource     the last stage's share in countries with
                                under 1% of the mine stage (7d), same
                                data year as D
    stages/<c>-state-year       the data year D is read for (bookkeeping)
    stages/<c>-<s>-plant-hhi    Herfindahl index across plants by capacity
    stages/<c>-<s>-plant-count  plants (bookkeeping)
    stages/<c>-<s>-plant-coverage  the stage's world output share in the
                                countries with a plant listed
  and per country:
    stages/<c>-<s>-share        the country's share of world output

  The chemical stages (bead bnm-1pq.15, scripts/minerals.py) are built, not
  reported: lithium-chemical (t LCE) and rare-earths-separation (t REO) from
  net exports plus domestic use, China closing the mass balance against the
  mine stage, and a facility year from plant capacities; one edition per
  plant-list year (source BNM-CHEM<year>), so they read like any other.
  Their D is mine against that stage. China's share there is partly a
  residual (stages-chem.csv column mine: its own mine output; the rest is
  the others' unprocessed ore, stock changes and losses).

  An alternative, from data/derived/supply/stages-chem.csv rows of method
  quota: rare-earth separation with China at its MIIT smelting and
  separation quota and the others as built, written as the stage
  separation-quota (stages/rare-earths-separation-quota-hhi, -top-share,
  -share, ...; no D). The balance (stage separation) is primary: the quota
  covers domestic ore only and leaves out the imported feed China
  separates, so it is a lower bound; the balance, which takes stock
  changes and losses, an upper one.

  D compares shares, each stage's of its own world total, so stages whose
  world totals differ (refined copper, lead and tin include scrap; stocks)
  are comparable; the scrap refiners (Japan, Germany, the US) read as
  processing without mining, which raises D. D is in [0, 1].

  Causal: at year K only rows known by K are read, from the latest data
  year that has any, and of a data year only its latest edition known by K
  (an MCS estimate, then its revision: vintage); state-year is that data
  year, the row's year K. D reads the latest data year both stages have. A
  stage whose data were last known more than :stale years before K is not
  written. The source lag is the table's known column (BGS: data year + 2)."
  (:require [bnm.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def in-path "data/derived/supply/stages.csv")
(def plants-path "data/derived/supply/stage-plants.csv")
(def out-path "data/derived/stocks/stages.csv")
(def chem-path "data/derived/supply/stages-chem.csv")

(def default-stale "Years past its known year a stage's data are still read." 3)

;; ---------------------------------------------------------------- measures

(defn shares
  "{iso share} of world output from {iso quantity}; non-producers dropped."
  [qs]
  (let [qs (into {} (filter (fn [[_ q]] (and q (pos? q)))) qs)
        tot (reduce + 0.0 (vals qs))]
    (if (pos? tot) (update-vals qs #(/ (double %) tot)) {})))

(defn hhi
  "Herfindahl-Hirschman index of a share map (0-1)."
  [sh]
  (reduce + 0.0 (map #(* % %) (vals sh))))

(defn top
  "[iso share] of the largest share; ties to the first iso."
  [sh]
  (when (seq sh)
    (first (sort-by (fn [[iso s]] [(- s) iso]) sh))))

(defn displacement
  "Dissimilarity of two stages' country distributions: 1 - sum over
  countries of the smaller share. 0 when each country processes its own
  ore's share, 1 when none does. a and b are {iso quantity} or {iso share}:
  each is made shares of its own total first, so world totals that differ
  between stages (refined copper and tin above mine output: scrap and
  stocks) do not enter. A country that processes without mining, or mines
  without processing, contributes its whole share to D. nil when either
  stage has no output. Clamped to [0, 1] against rounding."
  [a b]
  (let [a (shares a) b (shares b)]
    (when (and (seq a) (seq b))
      (-> (- 1.0 (reduce + 0.0 (for [iso (into (set (keys a)) (keys b))]
                                 (min (get a iso 0.0) (get b iso 0.0)))))
          (max 0.0)
          (min 1.0)))))

(def resource-floor
  "Mine share below which a country counts as having no resource (7d)."
  0.01)

(defn non-resource
  "Processing share in countries with under 1% of the mine stage
  (docs/ECONOMIC-CHARACTER.md 7d): the part of D from countries that
  process ore they do not mine. a and b as in displacement; nil when either
  stage has no output."
  [a b]
  (let [a (shares a) b (shares b)]
    (when (and (seq a) (seq b))
      (min 1.0 (reduce + 0.0 (for [[iso s] b :when (< (get a iso 0.0) resource-floor)] s))))))

(def stage-order
  "Upstream first. Lithium's chemical stage (carbonate, hydroxide) and rare
  earths' separation both follow the mine (bead bnm-1pq.15)."
  {"mine" 0 "smelter" 1 "refinery" 2 "chemical" 3 "separation" 3})

(def chain-order
  "Stage order where it differs from mine, smelter, refinery, chemical:
  alumina is refined before aluminium is smelted."
  {"aluminium" {"mine" 0 "refinery" 1 "smelter" 2 "chemical" 3}})

(defn chain
  "The commodity's stages present, upstream first."
  [commodity stages]
  (vec (sort-by (get chain-order commodity stage-order) stages)))

;; ---------------------------------------------------------------- the table

(defn check-units
  "nil when every commodity and stage has one unit; throws otherwise
  (shares within a stage need one unit; across stages they need not)."
  [rows]
  (doseq [[[c s] us] (reduce (fn [acc r] (update acc [(:commodity r) (:stage r)] (fnil conj #{}) (:unit r)))
                             {} rows)
          :when (> (count us) 1)]
    (throw (ex-info (str "stages: " c " " s " has several units " (pr-str us)) {:commodity c :stage s :units us}))))

(defn read-rows
  "Rows of a scripts/minerals.py table as maps; numbers parsed."
  [path]
  (for [m (csv/read-maps path \,)]
    (cond-> (assoc m
                   :known (parse-long (:known m))
                   :iso (:iso m))
      (:year m) (assoc :year (parse-long (:year m)))
      (:quantity m) (assoc :quantity (parse-double (:quantity m)))
      (:capacity m) (assoc :capacity (parse-double (:capacity m))))))

(defn- index
  "{[commodity stage] {year [rows]}}."
  [rows]
  (reduce (fn [acc r] (update-in acc [[(:commodity r) (:stage r)] (:year r)] (fnil conj []) r)) {} rows))

(defn vintage
  "The rows of one data year as known by k: those known by k, from the
  edition (source) of the latest of them. A later edition of the year
  (MCS2026 after MCS2025) replaces the earlier one once it is known, and is
  never added to it; rows of one source known at different years (a
  country added later) are all kept."
  [rs k]
  (let [rs (filter #(<= (:known %) k) rs)]
    (when (seq rs)
      (let [src (:source (apply max-key :known (reverse rs)))]
        (filter #(= src (:source %)) rs)))))

(defn as-of
  "The stage's data known by k: {:year y :known kn :q {iso quantity}} for
  the latest data year with any row known by k (its vintage then), or nil."
  [by-year k]
  (some (fn [y]
          (when-let [rs (seq (vintage (get by-year y) k))]
            {:year y :known (apply max (map :known rs))
             :q (reduce (fn [m r] (update m (:iso r) (fnil + 0.0) (:quantity r))) {} rs)}))
        (sort > (keys by-year))))

(defn- as-of-year
  "{iso quantity} of the stage's data year y known by k, or nil."
  [by-year y k]
  (when-let [rs (seq (vintage (get by-year y) k))]
    (reduce (fn [m r] (update m (:iso r) (fnil + 0.0) (:quantity r))) {} rs)))

(defn- wld [k v x & [t]]
  (cond-> {:iso "WLD" :year k :variable v :value (double x)} t (assoc :top t)))

(defn- stage-stocks [c s snap k]
  (let [sh (shares (:q snap))
        [ti ts] (top sh)
        p (str "stages/" c "-" s)]
    (when (seq sh)
      (concat
       [(wld k (str p "-hhi") (hhi sh))
        (wld k (str p "-top-share") ts ti)
        (wld k (str p "-world") (reduce + 0.0 (vals (:q snap))))
        (wld k (str p "-count") (count sh))
        (wld k (str p "-state-year") (:year snap))]
       (for [[iso v] (sort sh)]
         {:iso iso :year k :variable (str p "-share") :value v})))))

(defn- pairs
  "Stage pairs D is written for: mine against each later stage, and each
  processing step against the next."
  [ch]
  (distinct
   (concat (when (= "mine" (first ch)) (for [b (rest ch)] ["mine" b]))
           (partition 2 1 (remove #{"mine"} ch)))))

(defn- d-stocks [c ch idx k stale]
  (let [common (fn [a b]
                 ;; the latest data year both stages have known by k, fresh enough
                 (let [ya (set (keep #(when (as-of-year (idx [c a]) % k) %) (keys (idx [c a]))))
                       yb (set (keep #(when (as-of-year (idx [c b]) % k) %) (keys (idx [c b]))))
                       ys (filter ya yb)]
                   (when (seq ys) (apply max ys))))
        fresh? (fn [st y] (let [rs (vintage (get (idx [c st]) y) k)]
                            (and (seq rs) (<= (- k (apply max (map :known rs))) stale))))
        ds (for [[a b] (pairs ch)
                 :let [y (common a b)]
                 :when (and y (fresh? a y) (fresh? b y))
                 :let [qa (as-of-year (idx [c a]) y k)
                       qb (as-of-year (idx [c b]) y k)
                       d (displacement qa qb)]
                 :when d]
             [[a b] y d (non-resource qa qb)])
        main (when (and (= "mine" (first ch)) (> (count ch) 1))
               (some (fn [[ab y d nr]] (when (= ab ["mine" (peek ch)]) [y d nr])) ds))]
    (concat
     (for [[[a b] _ d] ds] (wld k (str "stages/" c "-d-" a "-" b) d))
     (when-let [[y d nr] main]
       [(wld k (str "stages/" c "-d") d)
        (wld k (str "stages/" c "-non-resource") nr)
        (wld k (str "stages/" c "-state-year") y)]))))

(defn stocks
  "Stock rows {:iso :year :variable :value [:top]} from stage rows, each
  year from the first a value is known to the last. Throws on mixed units."
  ([rows] (stocks rows {}))
  ([rows {:keys [stale] :or {stale default-stale}}]
   (check-units rows)
   (let [rows (filter #(and (:quantity %) (:known %) (:year %)) rows)
         idx (index rows)
         ks (range (apply min (map :known rows)) (inc (+ stale (apply max (map :known rows)))))
         by-c (group-by first (keys idx))]
     (vec
      (for [[c cs] (sort by-c)
            :let [ch (chain c (map second cs))]
            k ks
            r (concat
               (mapcat (fn [s]
                         (let [snap (as-of (idx [c s]) k)]
                           (when (and snap (<= (- k (:known snap)) stale))
                             (stage-stocks c s snap k))))
                       ch)
               (d-stocks c ch idx k stale))]
        r)))))

;; ---------------------------------------------------------------- alternatives

(defn alternative
  "Stage rows of an alternative: for each edition (data year and known) of
  a stage that has an alternative value for one country, that edition's
  rows with the country's quantity replaced, as the stage <stage>-<tag>.
  Editions with no alternative value are left out. alts are maps of
  :commodity :stage :iso :year :known :quantity."
  [rows alts tag]
  (let [alt (into {} (map (juxt (juxt :commodity :stage :year :known) identity)) alts)]
    (vec
     (for [[[c s y k] rs] (group-by (juxt :commodity :stage :year :known) rows)
           :let [a (alt [c s y k])]
           :when a
           r (conj (vec (remove #(= (:iso a) (:iso %)) rs))
                   (assoc (first rs) :iso (:iso a) :quantity (:quantity a)))]
       (assoc r :stage (str s "-" tag))))))

(defn quota-alternative
  "Stage rows of China at its separation quota (stages-chem.csv, method
  quota), or nil when the table is not built."
  [rows path]
  (when (.exists (io/file path))
    (let [alts (filter #(= "quota" (:method %)) (read-rows path))]
      (when (seq alts) (alternative rows alts "quota")))))

;; ---------------------------------------------------------------- plants

(defn plant-stocks
  "Plant concentration rows at the plants' known year (a snapshot): HHI by
  capacity, plant count, and coverage, the stage's latest world output
  share known then in the countries with a plant listed. Coverage is
  written only for stages in `rows`."
  [plants rows]
  (let [idx (index (filter :quantity rows))]
    (vec
     (for [[[c s] ps] (sort (group-by (juxt :commodity :stage) plants))
           :let [k (apply max (map :known ps))
                 sh (shares (reduce (fn [m p] (update m (:facility p) (fnil + 0.0) (:capacity p))) {} ps))
                 snap (some-> (idx [c s]) (as-of k))
                 out-sh (some-> snap :q shares)
                 isos (set (map :iso ps))
                 p (str "stages/" c "-" s)]
           :when (seq sh)
           r (cond-> [(wld k (str p "-plant-hhi") (hhi sh))
                      (wld k (str p "-plant-count") (count sh))]
               (seq out-sh) (conj (wld k (str p "-plant-coverage")
                                       (reduce + 0.0 (keep (fn [[iso v]] (when (isos iso) v)) out-sh)))))]
       r))))

;; ---------------------------------------------------------------- write

(defn- fmt [x]
  (if (== x (Math/rint x)) (str (long x) ".0") (str x)))

(defn write-stocks [path out]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value,top\n")
    (doseq [r (sort-by (juxt :variable :iso :year) out)]
      (.write w (str (str/join "," [(:iso r) (:year r) (:variable r) (fmt (:value r)) (or (:top r) "")]) "\n")))))

(defn -main [& _]
  (let [rows (read-rows in-path)
        plants (when (.exists (io/file plants-path)) (read-rows plants-path))
        alt (quota-alternative rows chem-path)
        out (concat (stocks rows) (when (seq alt) (stocks alt)) (plant-stocks plants rows))]
    (write-stocks out-path out)
    (println (str out-path ": " (count out) " rows, "
                  (count (distinct (map :variable out))) " variables, "
                  (count (filter #(= "WLD" (:iso %)) out)) " at WLD"))))
