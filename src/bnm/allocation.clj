(ns bnm.allocation
  "The allocation channel's measurement (bead bnm-4hv.3; docs/COUPLED-
  SYSTEMS.md 5.1 and 5.2): where credit goes by sector and where
  investment goes by industry, as known-year stocks, and the link from an
  industry's capital to the product graph (bnm.supply) and the mineral
  stages (bnm.stages) where a concordance exists.

  Input: scripts/allocation-data.py's long table (data/derived/allocation/
  panel.csv), rows {:iso :year :known :source :measure :sector :value}, :year
  the data year and :known = :year + the source's lag (`lags`).

  Credit (`credit-spec`, per source and measure): a group is a sum of the
  source's sectors, each part either a sector or a pair with the
  combination the source reports instead (Müller-Verner's BC for B and C).
  A group is measured only when every part is. Its share is over the
  source's total:
    mv        Müller-Verner, over total private credit: households (HH),
              non-financial corporations (NFC), the ISIC sections A, B, C
              (manufacturing), F, G, I, K, L (real estate), tradable (A +
              B + C) and non-tradable (F + L + G + I + H + J, the paper's
              FL + GI + HJ).
    bis       BIS, households and NFCs over their sum (both % of GDP).
    ecb       ECB MFI loans, households (and house purchase, consumption)
              and NFCs over their sum. In EUR: the states outside the
              euro (`ecb-floating`) are converted at current rates, so
              they get no new-credit share.
    ecb-nace  ECB loans to euro-area NFCs (iso EMU) by NACE group, over
              their sum: tradable (A + B + C), non-tradable (F + G + I +
              HJ + LMN: the paper's sections, with M and N because the ECB
              reports L only within LMN), C, F, LMN.
    pboc      PBoC loans to households (consumption, operating) and to
              enterprises (of which medium and long term) over their sum;
              pboc-rmb the same from the RMB-only table, a source of its
              own so no change is taken across the switch.
    pboc tsf  total social financing over its private instruments
              (loans, shadow: entrusted, trust and undiscounted acceptances,
              corporate bonds, equity).
  Flows (`flows`) between two consecutive data years of one source: the
  share change, 100 (s_t - s_t-1) in percentage points, and where the
  source is in currency, the share of new credit, the group's change over
  the total's when the total grew (undefined when it did not: the ratio's
  sign would flip). It is the share of the net new credit, so it is not
  bounded by 0 and 1: when the total barely grows, a group that grows
  while another shrinks takes a large multiple of it.

  Investment: shares of an industry in gross fixed capital formation and
  in the capital stock (EU KLEMS, WIOD SEA), the real growth of its
  capital (KLEMS volume), its capital's growth against the economy's
  (WIOD, nominal, so a common capital-goods price cancels), and the
  product shares of investment demand (LR-WIOD). Industries are EU KLEMS's
  NACE groups (`industries`).

  The link: an EXIOBASE product's industry is that of its CPA division
  (`industry-of-division`), the division with the most concordance weight
  (data/derived/supply/concordance.csv); a mineral stage's is its NACE
  group (`stage-industry`: mines B, smelters and refineries C24-C25,
  chemical and separation stages C20). A product carries its industry's
  capital growth in every country; a stage only in the countries the
  stage table has it in, from the first year that is known. That is the
  capacity growth the 5.2 model will learn from the credit and profits
  that finance it. One industry's growth is carried to many nodes, so a
  reader pooling the nodes counts it once per industry, not per node."
  (:require [bnm.csv :as csv]))

(def lags
  "Each source's publication lag in years (scripts/allocation-data.py)."
  {"mv" 2 "bis" 1 "pboc" 1 "pboc-rmb" 1 "ecb" 1 "ecb-nace" 1 "klems" 2 "wiod" 3 "lrwiod" 3})

;; -- the panel -------------------------------------------------------------------------

(defn read-panel
  "panel.csv as rows {:iso :year :known :source :measure :sector :value}."
  [path]
  (mapv (fn [r] {:iso (:iso r) :year (parse-long (:year r)) :known (parse-long (:known r))
                 :source (:source r) :measure (:measure r) :sector (:sector r) :value (parse-double (:value r))})
        (csv/read-maps path \,)))

(defn index
  "{source {measure {iso {year {sector value}}}}} of panel rows."
  [rows]
  (reduce (fn [m {:keys [source measure iso year sector value]}]
            (assoc-in m [source measure iso year sector] value))
          {} rows))

;; -- credit groups -------------------------------------------------------------------------

(defn- either [a b ab] {:pair [a b] :or ab})

(def ^:private mv-groups
  {"HH" ["HH"] "NFC" ["NFC"]
   "A" ["A"] "B" ["B"] "C" ["C"] "F" ["F"] "G" ["G"] "I" ["I"] "K" ["K"] "L" ["L"]
   "tradable" ["A" (either "B" "C" "BC")]
   "nontradable" [(either "F" "L" "FL") (either "G" "I" "GI") (either "H" "J" "HJ")]})

(def ^:private nace ["A" "B" "C" "DE" "F" "G" "HJ" "I" "LMN" "Z"])
(defn- nfc [s] (str "nfc-" s))

(def ^:private specs
  {["mv" "credit"] {:label "mv" :unit "share of private credit" :flows true
                    :total ["Total"] :groups mv-groups}
   ["bis" "credit"] {:label "bis" :unit "share of household and NFC credit" :flows false
                     :total ["hh" "nfc"] :groups {"hh" ["hh"] "nfc" ["nfc"]}}
   ["ecb" "credit"] {:label "ecb" :unit "share of MFI loans to households and NFCs" :flows true
                     :total ["hh" "nfc"]
                     :groups {"hh" ["hh"] "nfc" ["nfc"] "hh-house" ["hh-house"] "hh-consumption" ["hh-consumption"]}}
   ["ecb-nace" "credit"] {:label "ecb-nace" :unit "share of MFI loans to NFCs" :flows true
                          :total (mapv nfc nace)
                          :groups {"tradable" (mapv nfc ["A" "B" "C"]) "nontradable" (mapv nfc ["F" "G" "HJ" "I" "LMN"])
                                   "C" [(nfc "C")] "F" [(nfc "F")] "LMN" [(nfc "LMN")]}}
   ["pboc" "credit"] {:label "pboc" :unit "share of loans to households and enterprises" :flows true
                      :total ["hh" "enterprise"]
                      :groups {"hh" ["hh"] "hh-consumption" ["hh-consumption"] "hh-operating" ["hh-operating"]
                               "enterprise" ["enterprise"] "enterprise-mlt" ["enterprise-mlt"]}}
   ["pboc" "tsf"] {:label "pboc-tsf" :unit "share of private social financing" :flows true
                   :total ["rmb-loans" "fx-loans" "entrusted-loans" "trust-loans" "undiscounted-acceptances"
                           "corporate-bonds" "equity"]
                   :groups {"loans" ["rmb-loans" "fx-loans"]
                            "shadow" ["entrusted-loans" "trust-loans" "undiscounted-acceptances"]
                            "bonds" ["corporate-bonds"] "equity" ["equity"]}}})

(def ecb-floating
  "The states in the ECB's country series whose currency floats against
  the euro over their span: their loans are in EUR at current rates, so a
  change between two years mixes new credit with the exchange rate."
  #{"CZE" "GBR" "HUN" "POL" "ROU" "SWE"})

(defn credit-spec
  "The groups of credit source and measure [source measure]: {:label (the
  variables' source part) :unit :flows (whether new-credit shares exist:
  a currency stock) :total parts :groups {name parts}}, nil for none."
  [[source measure]]
  (if (= source "pboc-rmb")
    (some-> (specs ["pboc" measure]) (assoc :label "pboc-rmb"))
    (specs [source measure])))

(def credit-keys
  "Every [source measure] with credit groups."
  (conj (vec (keys specs)) ["pboc-rmb" "credit"]))

(defn group-value
  "The sum of `parts` in sector map `m`, nil when a part is unmeasured. A
  pair part is its two sectors' sum when both are there, else their
  combination."
  [m parts]
  (reduce (fn [acc p]
            (let [v (if (map? p)
                      (let [[a b] (:pair p) x (get m a) y (get m b)]
                        (if (and x y) (+ x y) (get m (:or p))))
                      (get m p))]
              (if (and acc v) (+ acc (double v)) (reduced nil))))
          0.0 parts))

(defn- totals [spec m]
  (into {:total (group-value m (:total spec))}
        (for [[g ps] (:groups spec)] [g (group-value m ps)])))

(defn shares
  "{group share} of sector map `m` over the spec's total (positive)."
  [spec m]
  (let [t (totals spec m) tot (:total t)]
    (if (and tot (pos? tot))
      (into {} (for [[g v] (dissoc t :total) :when v] [g (/ v tot)]))
      {})))

(defn flows
  "{:share-change {group pp} :new-share {group share}} from sector maps
  `a` (year t-1) and `b` (year t) of one source (of country `iso`): the
  share's change in percentage points, and where the spec has :flows (and
  the country is not one of `ecb-floating` for the ECB), the group's share
  of new credit when the total grew."
  ([spec a b] (flows spec a b nil))
  ([spec a b iso]
  (let [sa (shares spec a) sb (shares spec b)
        ta (totals spec a) tb (totals spec b)
        dt (when (and (:total ta) (:total tb)) (- (:total tb) (:total ta)))]
    (cond-> {:share-change (into {} (for [[g s] sb :let [s0 (get sa g)] :when s0] [g (* 100.0 (- s s0))]))}
      (and (:flows spec) (not (and (= "ecb" (:label spec)) (ecb-floating iso))))
      (assoc :new-share (if (and dt (pos? dt))
                          (into {} (for [g (keys (:groups spec)) :let [x (get ta g) y (get tb g)] :when (and x y)]
                                     [g (/ (- y x) dt)]))
                          {}))))))

;; -- investment and capital ----------------------------------------------------------------

(def industries
  "EU KLEMS's NACE groups, the finest the three sources share (WIOD's
  industries are summed into them), and C for manufacturing."
  ["A" "B" "C" "C10-C12" "C13-C15" "C16-C18" "C19" "C20" "C21" "C22-C23" "C24-C25" "C26" "C27" "C28"
   "C29-C30" "C31-C33" "D-E" "F" "G" "H" "I" "J" "K" "L" "M-N" "O-Q" "R-S"])

(def lrwiod-products
  "LR-WIOD's products (ISIC rev. 3 groups) in its national tables."
  ["AtB" "C" "D15t16" "D17t19" "D21t22" "D23" "D24" "D25" "D26" "D27t28" "D29" "D30t33" "D34t35" "Dnec"
   "E" "F" "G" "H" "I60t63" "I64" "J" "K" "LtQ"])

(defn industry-shares
  "{industry share} of a {industry value} map over its TOT, TOT left out."
  [m]
  (let [tot (get m "TOT")]
    (if (and tot (pos? tot))
      (into {} (for [[k v] m :when (and v (not= k "TOT"))] [k (/ (double v) tot)]))
      {})))

(defn log-growth
  "ln(b / a), nil unless both are positive."
  [a b]
  (when (and a b (pos? a) (pos? b)) (Math/log (/ (double b) (double a)))))

(defn relative-growth
  "The growth of k (from k0 to k1) against the total's (t0 to t1): ln of
  its share's ratio."
  [k0 t0 k1 t1]
  (when (and k0 t0 k1 t1 (pos? t0) (pos? t1))
    (log-growth (/ (double k0) t0) (/ (double k1) t1))))

(defn product-shares
  "{product share} of a {product value} map over its sum."
  [m]
  (let [tot (reduce + 0.0 (keep val m))]
    (if (pos? tot) (into {} (for [[k v] m :when v] [k (/ (double v) tot)])) {})))

;; -- the concordance -----------------------------------------------------------------------

(def ^:private division-ranges
  [[1 3 "A"] [5 9 "B"] [10 12 "C10-C12"] [13 15 "C13-C15"] [16 18 "C16-C18"] [19 19 "C19"] [20 20 "C20"]
   [21 21 "C21"] [22 23 "C22-C23"] [24 25 "C24-C25"] [26 26 "C26"] [27 27 "C27"] [28 28 "C28"]
   [29 30 "C29-C30"] [31 33 "C31-C33"] [35 39 "D-E"] [41 43 "F"] [45 47 "G"] [49 53 "H"] [55 56 "I"]
   [58 63 "J"] [64 66 "K"] [68 68 "L"] [69 82 "M-N"] [84 88 "O-Q"] [90 96 "R-S"]])

(defn industry-of-division
  "The industry (`industries`) of a two-digit NACE/CPA division, nil for
  none (households as employers, extraterritorial bodies)."
  [d]
  (when-let [n (some-> d (subs 0 2) parse-long)]
    (some (fn [[lo hi ind]] (when (<= lo n hi) ind)) division-ranges)))

(defn product-industries
  "{product industry} from concordance rows {:cpa :product :weight}: the
  industry of the CPA division with the most weight for the product."
  [rows]
  (let [w (reduce (fn [m {:keys [cpa product weight]}]
                    (if-let [ind (and (seq cpa) (industry-of-division cpa))]
                      (update-in m [product ind] (fnil + 0.0) (double (or weight 0.0)))
                      m))
                  {} rows)]
    (into {} (for [[p by] w] [p (key (apply max-key val (sort-by key by)))]))))

(defn stage-industry
  "The industry of a mineral stage: mines are B, smelters and refineries
  basic metals (C24-C25), chemical and separation stages chemicals (C20)."
  [_commodity stage]
  (case stage
    "mine" "B"
    ("smelter" "refinery") "C24-C25"
    ("chemical" "separation") "C20"
    nil))

(defn load-links
  "{:products {product industry} :stages {[commodity stage] {iso known}}}
  from the concordance (bnm.concordance's table) and the stage table,
  where they exist on disk: a stage's countries, each with the first year
  the table knows it has the stage."
  [concordance-path stages-path]
  (let [ex? #(.exists (java.io.File. ^String %))]
    {:products (if (ex? concordance-path)
                 (product-industries (map (fn [r] {:cpa (:cpa r) :product (:exiobase_product r) :weight (parse-double (:weight r))})
                                          (csv/read-maps concordance-path \,)))
                 {})
     :stages (if (ex? stages-path)
               (reduce (fn [m r]
                         (if-let [k (and (stage-industry (:commodity r) (:stage r)) (parse-long (str (:known r))))]
                           (update-in m [[(:commodity r) (:stage r)] (:iso r)] #(if % (min % k) k))
                           m))
                       {} (csv/read-maps stages-path \,))
               {})}))

(defn fmt
  "A value as the coupled table writes it."
  [x]
  (str (double x)))
