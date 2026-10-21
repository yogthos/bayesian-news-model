(ns bnm.lattice-banking
  "Banking crises on the lattice (docs/LATTICE.md, trial 36): nodes for each
  country, subregion, region and the world at three scales, learning from
  1870 on.

  - quarter: a crisis in quarters 2-4 (lead) and 5-8 (w2), two nodes, from
    the financial inputs of bnm.named (credit, debt service, asset prices, the curve,
    global credit and the VIX), in groups;
  - year: a crisis within two years, from the Macrohistory panel's stretches
    of fourteen stocks against each country's own decade, the displacement
    invariants (norm, breadth, resonance), the crisis process (world and own
    kernels) and the stabilizers, for its 18 countries, and from the
    catalogue's own history for every country;
  - half-decade: a crisis within five years, from the long stretches
    (credit, mortgages and house prices against twenty years, the credit
    gap), long displacement and resonance, and time since the country's
    and the world's last crises.

  The catalogue joins Reinhart-Rogoff (70 countries, 1800-2016), the
  Macrohistory crises (18, 1870-2020) and data/slow/events.tsv (from
  1974). An onset is known a year after its window closes; only onsets
  known by a node's origin enter its history inputs. Every gauge is
  standardized by the means and spreads of the origins before it, so
  nothing is scaled by the future.

    jolt -A:tools -M -m bnm.lattice-banking --out lattice-banking [--without contradiction,feedback] [--without-at quarter-lead:up+feedback] [--coupling 0] [--known r6-at]

  Writes data/runs/<out>/preds.tsv in bnm.onsets' format (the quarter
  nodes' country forecasts as kind banking_crisis, windows lead and w2,
  model lattice), forecasts.tsv (every node), and weights.tsv (each node's model
  averaging weights by year)."
  (:require [bnm.geo :as geo]
            [bnm.stocks]
            [bnm.lattice :as lt]
            [bnm.macrohistory :as mh]
            [bnm.named :as nm]
            [bnm.onsets :as on]
            [bnm.slow-train :as st]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def kind "banking_crisis")

(def start (* 12 1870))

(def known-lag "Months after a window closes before its outcome is known." 12)

(def scales
  "Each scale's cadence, and its window (origin + offset, + horizon] in
  months: the quarter's two windows are bnm.onsets' lead (quarters 2-4)
  and w2 (quarters 5-8), the counted cells of the acceptance test."
  [{:name :quarter-lead :per 3 :offset 3 :horizon 9 :discount 0.995}
   {:name :quarter-w2 :per 3 :offset 12 :horizon 12 :discount 0.995}
   {:name :year :per 12 :offset 0 :horizon 24 :discount 0.998}
   {:name :half-decade :per 60 :offset 0 :horizon 60 :discount 0.999}])

;; --- the catalogue -----------------------------------------------------------

(defn- read-rr
  "{iso #{onset years}} and the set of covered countries, Reinhart-Rogoff."
  []
  (with-open [r (io/reader "data/raw/slow/rr/onsets.csv")]
    (let [[_ & lines] (line-seq r)
          rows (for [l lines :let [[iso y _ b known] (str/split l #",")]] [iso (parse-long y) (= "1" b) (= "1" known)])]
      [(reduce (fn [acc [iso y b]] (if b (update acc iso (fnil conj #{}) y) acc)) {} rows)
       (set (for [[iso _ _ known] rows :when known] iso))])))

(def modern
  "The first month of the modern catalogue (data/slow/events.tsv): from
  then on its onsets alone are the labels, as for bnm.onsets, and the long
  catalogues supply only the years before."
  (* 12 1974))

(defn join-years
  "The onset years of two catalogues of one country, a year of the second
  dropped when the first has one within a year of it: the same crisis
  dated a year apart counts once."
  [first-years second-years]
  (into (set first-years)
        (remove (fn [y] (some #(<= (Math/abs (long (- % y))) 1) first-years)) second-years)))

(defn catalogue
  "{:onsets {unit sorted-set of months} :busy {unit sorted-set of months}
  :from {unit month} :through {unit month}}: onset months (an annual onset
  at the year's first month), the months a crisis is under way (the modern
  catalogue's spans), and how far each unit's catalogue reaches."
  [jst-rows]
  (let [[rr rr-units] (read-rr)
        sets (on/read-event-sets "data/slow/events.tsv")
        ev (get-in sets [:onsets kind])
        spans (get-in sets [:spans kind])
        cov-q (some (fn [[k q]] (when (= k kind) q)) (on/read-coverage "data/slow/coverage.tsv"))
        jst (reduce (fn [acc r] (if (= 1.0 (:crisisJST r)) (update acc (:iso r) (fnil conj #{}) (:year r)) acc)) {} jst-rows)
        jst-units (set (map :iso jst-rows))
        long-run (into {} (for [u (into (set (keys rr)) (keys jst))] [u (join-years (get rr u #{}) (get jst u #{}))]))
        early (fn [m] (update-vals m #(set (keep (fn [y] (when (< (* 12 y) modern) (* 12 y))) %))))
        months (merge-with into (early long-run) (update-vals ev #(set (map (fn [q] (* 3 q)) %))))
        busy (merge-with into (early long-run)
                         (update-vals spans #(set (mapcat (fn [q] (range (* 3 q) (+ 3 (* 3 q)))) %))))
        units (into (into rr-units jst-units) (keys ev))]
    {:onsets (into {} (for [u units] [u (into (sorted-set) (get months u))]))
     :busy (into {} (for [u units] [u (into (sorted-set) (get busy u))]))
     :from (into {} (for [u units] [u (cond (rr-units u) (* 12 1800) (jst-units u) (* 12 1870) :else modern)]))
     :through (into {} (for [u units] [u (max (* 3 (inc cov-q)) (if (rr-units u) (* 12 2017) 0) (if (jst-units u) (* 12 2021) 0))]))}))

(defn- onset-in?
  "Whether `unit` has an onset in months (lo, hi] as seen from origin t, or
  nil when its catalogue does not cover them or it is not at risk: a crisis
  under way in the year up to the origin, or before the window opens, is
  not a new one (bnm.onset/at-risk?)."
  [{:keys [onsets busy from through]} unit t lo hi]
  (when (and (from unit) (<= (from unit) (inc lo)) (<= hi (through unit))
             (empty? (subseq (get busy unit (sorted-set)) >= (- t 11) <= lo)))
    (boolean (seq (subseq (onsets unit) > lo <= hi)))))

(defn- history
  "The own-history inputs at origin t from onsets known by then: log years
  since the last, and the counts in the last 10 and 30 years."
  [{:keys [onsets from]} unit t]
  (let [known (subseq (get onsets unit (sorted-set)) <= (- t known-lag))
        last-m (last known)]
    (when (and (from unit) (<= (from unit) t))
      [(if last-m (Math/log (+ 1.0 (/ (- t last-m) 12.0))) (Math/log (+ 1.0 (/ (- t (from unit)) 12.0))))
       (double (count (filter #(> % (- t 120)) known)))
       (double (count (filter #(> % (- t 360)) known)))])))

;; --- gauges --------------------------------------------------------------------

(defn- standardizer
  "A function of [t raw-vector] giving the vector standardized by the
  means and spreads of the values at origins before t, capped at 5;
  `samples` are [t vector] pairs over all units. nil entries are 0."
  [samples]
  (let [by-t (into (sorted-map) (group-by first samples))
        width (count (second (first samples)))
        ;; running sums through each origin
        stats (loop [[[t xs] & more] (seq by-t) n (double-array width) s (double-array width) ss (double-array width) acc (sorted-map)]
                (if-not t
                  acc
                  (let [acc (assoc acc t [(aclone n) (aclone s) (aclone ss)])]
                    (doseq [[_ v] xs]
                      (dotimes [j width]
                        (when-let [x (nth v j)]
                          (aset n j (inc (aget n j))) (aset s j (+ (aget s j) x)) (aset ss j (+ (aget ss j) (* x x))))))
                    (recur more n s ss acc))))]
    (fn [t v]
      (let [[^doubles n ^doubles s ^doubles ss] (or (some-> (rsubseq stats <= t) first val)
                                                    [(double-array width) (double-array width) (double-array width)])]
        (vec (for [j (range width)
                   :let [x (nth v j)
                         k (aget n j)]]
               (if (and x (> k 20))
                 (let [mu (/ (aget s j) k)
                       sd (Math/sqrt (Math/max 1e-12 (- (/ (aget ss j) k) (* mu mu))))]
                   (Math/max -5.0 (Math/min 5.0 (/ (- x mu) sd))))
                 0.0)))))))

(def quarter-groups
  "The financial inputs of bnm.named in groups (indices among them), and
  the OECD's house prices to incomes and to rents against their own ten
  years."
  {:credit [0 1 2 3] :assets [4 5 6 7 8 9 10] :curve [11] :global [12 13 14 15 16 17] :house-valuation [18 19]})

(defn- quarter-stretch
  "x at quarter q against the 40 quarters before (at least 20), or nil."
  [qs q]
  (when-let [x (get qs q)]
    (let [past (keep #(get qs %) (range (- q 40) q))]
      (when (>= (count past) 20)
        (let [m (/ (reduce + past) (count past))
              sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) past)) (dec (count past))))]
          (let [sd (Math/max sd (* 0.02 (Math/abs m)))]
            (when (> sd 1e-9) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd))))))))))

(def year-specific (mapv first mh/dimensions))
(def year-invariants [:norm :breadth :max-displacement :resonance :norm-long :flow-norm :stock-x-flow :world-norm :world-crises])
(def year-process [:world-k1 :world-k3 :world-k10 :world-k30 :own-k3 :own-k10 :own-k30])
(def year-stabilizers [:real-rate :loans-to-deposits :peg :public-debt-level])

(def year-groups
  (let [a (count year-specific) b (count year-invariants) c (count year-process) d (count year-stabilizers)]
    {:stretches (vec (range 0 a))
     :invariants (vec (range a (+ a b)))
     :process (vec (range (+ a b) (+ a b c)))
     :stabilizers (vec (range (+ a b c) (+ a b c d)))
     :history (vec (range (+ a b c d) (+ a b c d 3)))
     :booms (vec (range (+ a b c d 3) (+ a b c d 5)))
     ;; the extended dimensions, from 39
     :household (vec (range 39 41))
     :funding (vec (range 41 43))
     :valuation (vec (range 43 45))
     :fiscal [45]
     :production (vec (range 46 50))
     :credit-wide [50]
     :bank-equity [51 52 53]
     :sectoral [54 55]
     :profitability [56 57]
     :inequality [58 59 60]
     :debt [61 62 63]
     :distress [64 65]}))

(defn- by-country-year
  "{iso {year value}} from rows, keeping non-nil values of (f row)."
  [rows iso-of year-of f]
  (reduce (fn [acc r] (if-let [v (f r)] (assoc-in acc [(iso-of r) (year-of r)] (double v)) acc)) {} rows))

(defn- stretch-at
  "x in `year` against the country's previous ten years (at least six), or
  nil. The spread is floored at 2% of the series' level and the stretch
  capped at 5: a source's imputed years held nearly flat would otherwise
  make any change look enormous."
  [ys year]
  (when-let [x (get ys year)]
    (let [past (keep #(get ys %) (range (- year 10) year))]
      (when (>= (count past) 6)
        (let [m (/ (reduce + past) (count past))
              sd (Math/sqrt (/ (reduce + (map #(* (- % m) (- % m)) past)) (dec (count past))))
              sd (Math/max sd (* 0.02 (Math/abs m)))]
          (when (> sd 1e-9) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd)))))))))

(defn- change [ys year k]
  (when-let [a (get ys year)] (when-let [b (get ys (- year k))] (- a b))))

(defn- read-pwt
  "{column {iso {year value}}} from data/raw/slow/pwt/pwt110-full.csv
  (scripts/pwt-full.py)."
  []
  (with-open [r (io/reader "data/raw/slow/pwt/pwt110-full.csv")]
    (let [[h & ls] (line-seq r)
          cols (str/split h #",")
          rows (for [l ls] (zipmap cols (str/split l #"," -1)))
          num #(some-> % str/trim not-empty parse-double)]
      (into {} (for [c ["rgdpna" "rkna" "rtfpna" "labsh" "csh_i"]]
                 [c (by-country-year rows #(get % "countrycode") #(long (num (get % "year"))) #(num (get % c)))])))))

(defn read-annual
  "{var {iso {year value}}} from data/derived/lattice-annual.csv
  (scripts/lattice-data.py)."
  []
  (with-open [r (io/reader "data/derived/lattice-annual.csv")]
    (reduce (fn [acc l]
              (let [[iso y v x] (str/split l #",")]
                (assoc-in acc [v iso (parse-long y)] (parse-double x))))
            {} (rest (line-seq r)))))

(defn read-quarterly
  "{var {iso {q value}}} from data/derived/lattice-quarterly.csv."
  []
  (with-open [r (io/reader "data/derived/lattice-quarterly.csv")]
    (reduce (fn [acc l]
              (let [[iso q v x] (str/split l #",")]
                (assoc-in acc [v iso (parse-long q)] (parse-double x))))
            {} (rest (line-seq r)))))

(def extended-names
  "The year node's dimensions beyond bnm.macrohistory's: from the
  Macrohistory panel's unused columns, household and business credit,
  the loan-to-deposit ratio and non-core funding, the rent and dividend
  yields and the fiscal balance; from the Penn World Table for every
  country, TFP and capital per output over three years and the labour and
  investment shares; from WDI for every country, private credit. Each
  stock against the country's own decade."
  [:household-credit :business-credit :loans-to-deposits :noncore :rent-yield :dividend-yield :fiscal-balance
   :tfp3 :capital-output3 :labour-share :investment-share :credit-wide
   ;; the downloaded sources (scripts/lattice-data.py, data/raw/slow/DOWNLOADS-2026-10-21.md):
   ;; bank against nonfinancial equity and bank crashes (Baron, Verner &
   ;; Xiong); unproductive credit (Mueller & Verner); the profit rate
   ;; (Extended PWT); income and wealth shares (WID); household, corporate
   ;; and public debt (IMF); financial distress (Romer & Romer)
   :bank-return3 :nonfin-return3 :bank-crashes10 :unproductive-credit :unproductive-credit3
   :profit-rate :profit-rate3 :top10-income :bottom50-income :top1-wealth
   :household-debt :corporate-debt :public-debt-wide :distress :distress3])

(defn extended-table
  "{[iso year] [values in extended-names order]} for every country-year."
  [jst-rows pwt db units annual]
  (let [div (fn [a b] (when (and a b (not (zero? (double b)))) (/ (double a) (double b))))
        lg (fn [x] (when (and x (pos? x)) (Math/log x)))
        jst (fn [f] (by-country-year jst-rows :iso :year f))
        hh (jst #(div (:thh %) (:gdp %)))
        biz (jst #(div (:tbus %) (:gdp %)))
        ltd (jst :ltd) noncore (jst :noncore) rent (jst :housing_rent_yd) dp (jst :eq_dp)
        fiscal (jst #(when (and (:revenue %) (:expenditure %)) (div (- (:revenue %) (:expenditure %)) (:gdp %))))
        tfp (update-vals (pwt "rtfpna") #(update-vals % lg))
        ko (into {} (for [[c ys] (pwt "rkna")]
                      [c (into {} (for [[y k] ys :let [q (get-in pwt ["rgdpna" c y])] :when (and q (pos? k) (pos? q))] [y (Math/log (/ k q))]))]))
        credit (into {} (for [u units]
                          [u (into {} (for [y (range 1960 2027)
                                            :let [v (some-> (db [u "finance/private_credit_gdp"]) (bnm.stocks/value (+ (* 12 y) 11) {:stale 18}))]
                                            :when v] [y v]))]))
        a (fn [v] (get annual v {}))
        sum3 (fn [ys y] (let [xs (keep #(get ys %) [(- y 2) (dec y) y])] (when (= 3 (count xs)) (reduce + xs))))
        count10 (fn [ys y] (when (seq ys) (reduce + (keep #(get ys %) (range (- y 9) (inc y))))))
        years (range 1870 2027)]
    (into {} (for [u (into (set units) (map :iso jst-rows)) y years]
               [[u y] [(stretch-at (hh u) y) (stretch-at (biz u) y) (stretch-at (ltd u) y) (stretch-at (noncore u) y)
                       (stretch-at (rent u) y) (stretch-at (dp u) y) (stretch-at (fiscal u) y)
                       (change (tfp u) y 3) (change (ko u) y 3)
                       (stretch-at (get-in pwt ["labsh" u]) y) (stretch-at (get-in pwt ["csh_i" u]) y)
                       (stretch-at (credit u) y)
                       (sum3 (get-in annual ["bvx_bank_ret" u]) y) (sum3 (get-in annual ["bvx_nonfin_ret" u]) y)
                       (count10 (get-in annual ["bvx_bank_crash" u]) y)
                       (stretch-at (get-in annual ["mv_unproductive" u]) y) (change (get-in annual ["mv_unproductive" u]) y 3)
                       (stretch-at (get-in annual ["epwt_profit_rate" u]) y) (change (get-in annual ["epwt_profit_rate" u]) y 3)
                       (stretch-at (get-in annual ["wid_top10" u]) y) (stretch-at (get-in annual ["wid_bottom50" u]) y)
                       (stretch-at (get-in annual ["wid_top1_wealth" u]) y)
                       (stretch-at (get-in annual ["gdd_household" u]) y) (stretch-at (get-in annual ["gdd_corporate" u]) y)
                       (stretch-at (get-in annual ["gdd_government" u]) y)
                       (get-in annual ["romer_distress" u y]) (change (get-in annual ["romer_distress" u]) y 3)]]))))

(def half-groups {:long-stretch [0 1 2 3] :long-displacement [4 5] :memory [6 7 8 9 10]})

(defn- year-raw
  "The year node's raw gauges for a country at origin t, from its
  Macrohistory row and extended dimensions of the year before (published
  by then) and its known history."
  [jst ext cat unit t]
  (let [r (get jst [unit (dec (quot t 12))])
        e (get ext [unit (dec (quot t 12))] (repeat (count extended-names) nil))]
    (vec (concat (map #(get-in r [:specific %]) year-specific)
                 (map #(get-in r [:invariant %]) year-invariants)
                 (map #(get-in r [:process %]) year-process)
                 (map #(get-in r [:stabilizer %]) year-stabilizers)
                 (or (history cat unit t) [nil nil nil])
                 [(get-in r [:growth-3y :credit]) (get-in r [:growth-3y :house])]
                 e))))

(defn- half-raw [jst cat unit t]
  (let [r (get jst [unit (dec (quot t 12))])
        h (history cat unit t)]
    [(get-in r [:specific-long :credit]) (get-in r [:specific-long :mortgages]) (get-in r [:specific-long :house-prices])
     (get-in r [:specific-long :credit-gap-z])
     (get-in r [:invariant :norm-long]) (get-in r [:invariant :resonance])
     (first h) (second h) (get h 2) (get-in r [:invariant :since-world-wave]) (get-in r [:process :world-k10])]))

(def priors
  "What is known, as {scale {group {index-in-group [mean variance]}}}: credit
  booms (Schularick & Taylor 2012), credit with asset booms (Greenwood et al.
  2022), the credit gap (Drehmann & Yetman 2018), displacement in many
  stocks at once (docs/INVARIANTS.md). Everything else starts at zero."
  {:quarter-lead {:credit {0 [0.3 0.1] 2 [0.3 0.1]} :assets {1 [0.2 0.1] 2 [0.2 0.1]}}
   :quarter-w2 {:credit {0 [0.3 0.1] 2 [0.3 0.1]} :assets {1 [0.2 0.1] 2 [0.2 0.1]}}
   :year {:stretches {0 [0.3 0.1] 1 [0.2 0.1] 2 [0.2 0.1]} :invariants {0 [0.2 0.1] 1 [0.2 0.1] 3 [0.2 0.1]}
          :booms {0 [0.3 0.1] 1 [0.2 0.1]}}
   :half-decade {:long-stretch {0 [0.3 0.1] 3 [0.3 0.1]} :long-displacement {1 [0.2 0.1]}}})

(def tensions
  "Opposed aspects within a place, fixed from theory before any run, as
  pairs of gauge indices: at the year (the Macrohistory stretches, in
  bnm.macrohistory/dimensions' order), credit against investment (credit
  growing without productive accumulation), house prices against the wage
  share (asset prices outrunning wages), equity against investment, and
  credit against the wage share (debt standing in for wages); at the
  quarter, credit growth against the curve (credit expanding as the curve
  flattens) and the country's credit cycle against the world's."
  {:year [[0 4] [2 10] [3 4] [0 10]
          ;; household credit ahead of wages, house prices ahead of rents
          ;; (the rent yield's stretch falls as valuations rise), and credit
          ;; ahead of productivity (every country, from WDI and the PWT)
          [39 10] [2 43] [50 46]
          ;; bank equity ahead of nonfinancial (finance ahead of production),
          ;; unproductive credit ahead of profitability, household debt ahead
          ;; of the labour share, corporate debt ahead of profitability, the
          ;; top's income ahead of the bottom half's
          [51 52] [54 56] [61 48] [62 56] [58 59]]
   :quarter-lead [[0 11] [0 12]]
   :quarter-w2 [[0 11] [0 12]]})

(def turns
  "The turn of a boom while the stock is stretched, as [stock flow window]
  (docs/ANATOMY.md: the boom peaks seven to eight quarters before the
  break, house prices first): at the year, credit stretched while house
  prices or equity fall from their three-year peak; at the quarter, the
  credit gap stretched while house prices, the household boom or equity
  fall from their eight-quarter peak."
  {:year [[0 2 3] [0 3 3] [0 51 3] [50 51 3]]
   :quarter-lead [[2 4 8] [2 5 8] [2 7 8] [2 18 8]]
   :quarter-w2 [[2 4 8] [2 5 8] [2 7 8] [2 18 8]]})

(defn- exposure-of
  "A place's openness to what comes down to it at origin t: trade
  openness (exports and imports over GDP) and capital-account openness
  (Chinn-Ito, two years back), 0 where unknown."
  [db regimes unit t]
  [(or (some-> (db [unit "trade/openness"]) (bnm.stocks/value t {:stale 30}) (/ 100.0)) 0.0)
   ;; the Chinn-Ito index is published a year or more late
   (or (get-in regimes [[unit (- (quot t 12) 2)] :capital-openness]) 0.0)])

(defn read-known
  "What is known, as a forecast: bnm.onsets' named model for each window
  (`run`'s preds, refit each year on the years before it, so out of sample
  at every origin), {[window unit q] p}."
  [run]
  (with-open [r (io/reader (str "data/runs/" run "/preds.tsv"))]
    (reduce (fn [acc line]
              (let [[k w m q u p] (str/split line #"\t")]
                (if (and (= k kind) (= m "named") (#{"lead" "w2"} w))
                  (assoc acc [w u (parse-long q)] (parse-double p))
                  acc)))
            {} (line-seq r))))

(defn spec
  "The lattice spec for banking crises."
  [{:keys [db wc jst ext cat units end regimes known quarterly]}]
  (let [[_ fin] (nm/financial db wc)
        sub (into {} (for [u units :let [s (first (geo/parents u))] :when s] [u s]))
        reg (into {} (for [s (set (vals sub))] [s (first (geo/parents s))]))
        parent (merge sub reg (zipmap (set (vals reg)) (repeat "world")))
        countries (sort (keys sub))
        members (fn [place] (if (sub place) [place] (filter #(let [s (sub %)] (or (= s place) (= (reg s) place) (= "world" place))) countries)))
        origins (fn [{:keys [per]}] (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end))))
        ;; the OECD's house valuation, as of the quarter before (published late)
        valuation (fn [u t] (let [q (dec (quot (- t 2) 3))]
                              [(quarter-stretch (get-in quarterly ["oecd_price_income" u]) q)
                               (quarter-stretch (get-in quarterly ["oecd_price_rent" u]) q)]))
        raw {:quarter-lead (fn [u t] (into (vec (fin u t)) (valuation u t)))
             :quarter-w2 (fn [u t] (into (vec (fin u t)) (valuation u t)))
             :year (fn [u t] (year-raw jst ext cat u t))
             :half-decade (fn [u t] (half-raw jst cat u t))}
        ;; each scale's country gauges at its origins, then standardized by the past
        table (into {} (for [{:keys [name] :as s} scales]
                         [name (into {} (for [t (origins s) u countries] [[u t] ((raw name) u t)]))]))
        std (into {} (for [[name tb] table] [name (standardizer (for [[[_ t] v] tb] [t v]))]))
        ;; what is known: named's forecast for the window, as log odds (from
        ;; 2000); an aggregate's, the chance any member has a crisis. nil
        ;; elsewhere, where the node starts from climatology
        known-at (fn [scale place t]
                   (let [w ({:quarter-lead "lead" :quarter-w2 "w2"} scale)
                         ps (when w (keep #(get known [w % (quot (- t 2) 3)]) (members place)))]
                     (when (seq ps)
                       (let [p (- 1.0 (reduce * (map #(- 1.0 %) ps)))]
                         (Math/log (/ (max 1e-6 p) (max 1e-6 (- 1.0 p))))))))
        gauges (fn [scale place t]
                 (let [f (std scale)
                       vs (keep #(some->> (get-in table [scale [% t]]) (f t)) (members place))]
                   (if (seq vs)
                     (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs)
                     (vec (repeat (count (second (first (table scale)))) 0.0)))))
        widths (into {} (for [[name tb] table] [name (count (second (first tb)))]))]
    {:scales scales
     :levels [[:country countries]
              [:subregion (sort (set (vals sub)))]
              [:region (sort (set (vals reg)))]
              [:world ["world"]]]
     :offset known-at
     :parent parent
     :start start :end end
     :forget 0.995
     :target (fn [scale place t]
               (let [{:keys [offset horizon]} (first (filter #(= scale (:name %)) scales))
                     lo (+ t offset) hi (+ t offset horizon)
                     ys (keep #(onset-in? cat % t lo hi) (members place))]
                 (when (seq ys) {:y (boolean (some true? ys)) :known-at (+ hi known-lag)})))
     :gauges gauges
     :gauge-width widths
     :gauge-groups {:quarter-lead quarter-groups :quarter-w2 quarter-groups :year year-groups :half-decade half-groups}
     :shrink 0.1
     ;; theory's sign on the contradiction summaries: more strain, more
     ;; risk, held loosely enough for the data to revise
     :contradiction-prior [0.3 0.25]
     :tensions tensions
     :turns turns
     ;; a country's tensions resolve once per onset, when it is known; an
     ;; aggregate has an onset somewhere most of the time, so never
     :resolutions (fn [place]
                    (when (sub place)
                      (into (sorted-set) (map #(+ % known-lag) (get-in cat [:onsets place])))))
     :exposure (let [ex (memoize (fn [u t] (exposure-of db regimes u t)))]
                 (fn [_ place t]
                   (let [vs (map #(ex % t) (members place))]
                     (if (seq vs) (apply mapv (fn [& xs] (/ (reduce + xs) (count xs))) vs) [0.0 0.0]))))
     :exposure-width 2
     :coupling 0.1
     :prior (fn [scale _level {:keys [groups]}]
              (into {} (for [[g ixs] (get priors scale) [i mv] ixs] [(nth (groups g) i) mv])))}))

(defn -main [& args]
  (let [{:keys [out end without coupling known without-at]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        out (or out "lattice-banking")
        dir (str "data/runs/" out)
        end (or (some-> end parse-long) (+ (* 12 2025) 11))
        variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        jst-rows (mh/read-jst "data/raw/slow/jst/jst.csv")
        jst (into {} (for [r (mh/build jst-rows (mh/read-intensity nil) (mh/read-regimes) (mh/read-credit-gap))]
                       [[(:iso r) (:year r)] r]))
        cat (catalogue jst-rows)
        units (set (filter #(first (geo/parents %)) (keys (:onsets cat))))
        ;; the quarter nodes' origins: the last month of each quarter
        months (range (+ start 2) (inc end) 3)
        wc (nm/world-credit db units months)
        _ (println (count units) "countries; building gauges")
        ext (extended-table jst-rows (read-pwt) db units (read-annual))
        sp (cond-> (spec {:db db :wc wc :jst jst :ext ext :cat cat :units units :end end :regimes (mh/read-regimes)
                          :known (read-known (or known "r6-at")) :quarterly (read-quarterly)})
             ;; ablations: --without contradiction,feedback  --coupling 0
             without (assoc :without (set (map keyword (str/split without #","))))
             ;; per scale: --without-at quarter-lead:up+feedback,year:exposure
             without-at (assoc :without-at (into {} (for [part (str/split without-at #",")
                                                         :let [[sc gs] (str/split part #":")]]
                                                     [(keyword sc) (set (map keyword (str/split gs #"\+")))])))
             coupling (assoc :coupling (let [c (parse-double coupling)] (when (pos? c) c))))
        _ (println "running the lattice")
        r (lt/run sp)]
    (io/make-parents (str dir "/x"))
    (with-open [w (io/writer (str dir "/forecasts.tsv"))]
      (doseq [{:keys [scale place origin p y]} (:forecasts r)]
        (.write w (str (name scale) "\t" place "\t" origin "\t" p "\t" (if (nil? y) "" y) "\n"))))
    ;; the quarter nodes' country forecasts as onset rows: origin t, the
    ;; quarter q with t its last month, windows lead and w2
    (with-open [w (io/writer (str dir "/preds.tsv"))]
      (doseq [{:keys [scale place origin p y]} (:forecasts r)
              :let [wname ({:quarter-lead "lead" :quarter-w2 "w2"} scale)]
              :when (and wname (= 3 (count place)))]
        (.write w (str kind "\t" wname "\tlattice\t" (quot (- origin 2) 3) "\t" place "\t" p "\t" (if (nil? y) "nil" y) "\n"))))
    (with-open [w (io/writer (str dir "/weights.tsv"))]
      (doseq [[[scale level] hist] (sort-by key (:weights r))
              [t ws] hist
              :when (zero? (mod (- t 11) 12))
              [g wt] (sort-by key ws)]
        (.write w (str (name scale) "\t" (name level) "\t" (quot t 12) "\t" (name g) "\t" wt "\n"))))
    (println "wrote" dir)))
