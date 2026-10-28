(ns bnm.supply-shocks
  "Country-level supply shocks as event triggers (bead bnm-82j.3): for each
  country and year end t, the share of its supply of food, fertiliser and
  energy lost to its suppliers' shortfalls and to disrupted straits, as
  known at t.

  A family f (food: wheat, rice, maize; fertiliser: N + P2O5 + K2O;
  energy: oil, gas) loses, for importer c at year end t,

    exporters  sum_p s_cp m_cp sum_e w_cep d_ep
    straits    sum_p s_cp m_cp sum_k x_ckp g_k

  and its loss is their sum, capped at 1, where
    s_cp   p's share of c's use within the family (food: FAOSTAT use of the
           three staples; energy: oil's and gas's shares of primary energy;
           fertiliser 1);
    m_cp   c's import dependence in p (food: FAOSTAT's import dependency
           ratio; fertiliser: imports over agricultural use; energy: use
           less production over use), clipped to [0, 1];
    w_cep  e's share of c's imports of p, tonnes over the latest three
           data years known at t (BACI and FAOSTAT matrices, known two
           years after their data year), none older than t - 5;
    d_ep   e's shortfall: 1 less its latest value over the mean of up to
           three years before (at least two), floored at 0 and counted
           only from 10% (ordinary year-to-year variation is no shock), when that mean
           is at least the product's minimum base and the latest data year
           is fresh enough (food: USDA PSD exports of the market year,
           known in the year; energy: production, known the year after;
           fertiliser: FAOSTAT exports, known two years after); an exporter
           without a series has none (the EU's members read the EU's PSD
           series);
    x_ckp  the share of c's imports of p whose sea route passes strait k
           (bnm.chokepoints, by the year known);
    g_k    the strait's disruption in t: 1 less its deadweight transits in
           t over their mean in the years before (PortWatch, from 2019),
           relative to the same ratio over all straits, floored at 0; dry
           bulk for food and fertiliser, tankers for energy.

  Every value read is one known by the end of t (its known year at most
  t); a family whose dependence is unknown for every product is missing,
  not zero. The panels are written by tools/bnm/shock_trial."
  (:require [augur.math :as m]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- known values ---------------------------------------------------------------------

(defn known-series
  "{data-year value} of a series (sorted-map data-year [known value]) known
  by the end of year t."
  [series t]
  (into (sorted-map) (for [[y [k v]] series :when (and (<= k t) (some? v))] [y v])))

(defn value-at
  "The value of the latest data year known by the end of t, if it is no
  more than `max-age` years before t."
  [series t max-age]
  (when-let [[y v] (last (known-series series t))]
    (when (>= y (- t max-age)) v)))

(defn shortfall
  "1 less the value of data year `y` over the mean of the three years before
  (at least two of them), floored at 0 and capped at 1; nil when y has no
  value, fewer than two years before do, or their mean is below `min-base`."
  [vals y min-base]
  (let [x (get vals y)
        prior (keep #(get vals %) (range (- y 3) y))]
    (when (and (some? x) (>= (count prior) 2))
      (let [base (/ (reduce + prior) (count prior))]
        (when (>= base min-base)
          (max 0.0 (min 1.0 (- 1.0 (/ x base)))))))))

(defn supplier-shares
  "{exporter share} of an importer's imports from `flows` (sorted-map
  data-year [known {exporter tonnes}]): tonnes summed over the latest three
  data years known by the end of t, none before t - 5."
  [flows t]
  (let [ys (take-last 3 (for [[y [k by]] flows :when (and (<= k t) (>= y (- t 5)))] by))
        tot (apply merge-with + {} ys)
        s (reduce + 0.0 (vals tot))]
    (if (pos? s) (update-vals tot #(/ % s)) {})))

(defn strait-disruption
  "The disruption of strait k in year t from `cap` ({strait sorted-map
  year [known capacity]}, WLD all straits): 1 less its capacity in t over
  its mean in up to three years before, relative to the same over all
  straits, floored at 0 and capped at 1; nil without t or a year before."
  [cap k t]
  (let [ratio (fn [s] (let [v (known-series (get cap s) t)
                            x (get v t)
                            prior (keep #(get v %) (range (- t 3) t))]
                        (when (and x (seq prior)) (/ x (/ (reduce + prior) (count prior))))))
        rk (ratio k) rw (ratio "WLD")]
    (when (and rk rw (pos? rw))
      (max 0.0 (min 1.0 (- 1.0 (/ rk rw)))))))

;; --- the families ----------------------------------------------------------------------

(def eu-members
  "Members whose PSD series is the EU's (PSD has none of their own): the 27
  and the United Kingdom."
  #{"AUT" "BEL" "BGR" "HRV" "CYP" "CZE" "DNK" "EST" "FIN" "FRA" "DEU" "GRC" "HUN" "IRL" "ITA" "LVA" "LTU"
    "LUX" "MLT" "NLD" "POL" "PRT" "ROU" "SVK" "SVN" "ESP" "SWE" "GBR"})

(def families
  "{family [product spec]}: :supply [source variable] of the exporters'
  series, :min-base, :fresh (the latest data year read is at least t -
  fresh), :flow the bilateral product, :choke [product class] of the strait
  shares and transits."
  {"food" (vec (for [p ["wheat" "rice" "maize"]]
                 {:product p :supply ["psd" "exports"] :min-base 1e5 :fresh 0 :flow p :choke ["food" "bulk"]}))
   "fert" [{:product "fert" :supply ["fao" "exports"] :min-base 1e4 :fresh 2 :flow "fert" :choke ["fert" "bulk"]}]
   "energy" (vec (for [p ["oil" "gas"]]
                   {:product p :supply ["owid" "production"] :min-base 10.0 :fresh 1 :flow p :choke [p "tanker"]}))})

(def family-order ["food" "fert" "energy"])

(def floor
  "A shortfall under this is ordinary variation, counted as none."
  0.1)

(def max-age "Years an exposure (dependence, shares, strait shares) is carried." 3)

(defn- series [db k] (get-in db [:series k]))

(defn- clip01 [x] (when (some? x) (max 0.0 (min 1.0 (double x)))))

(defn dependence
  "c's import dependence in product p at t, [0, 1], or nil."
  [db family p c t]
  (case family
    "food" (clip01 (value-at (series db ["fbs" c p "import-dependence"]) t max-age))
    "fert" (let [i (value-at (series db ["fao" c "fert" "imports"]) t max-age)
                 u (value-at (series db ["fao" c "fert" "use"]) t max-age)]
             (when (and i u (pos? u)) (clip01 (/ i u))))
    "energy" (let [u (value-at (series db ["owid" c p "use"]) t max-age)
                   q (or (value-at (series db ["owid" c p "production"]) t max-age) 0.0)]
               (when (and u (pos? u)) (clip01 (/ (- u q) u))))))

(defn- use-weight
  "p's unnormalized weight in c's use of the family at t."
  [db family p c t]
  (case family
    "food" (value-at (series db ["fbs" c p "use"]) t max-age)
    "fert" 1.0
    "energy" (value-at (series db ["owid" c p "share"]) t max-age)))

(defn exporter-shortfall
  "Exporter e's shortfall in a product spec at t (nil when unknown)."
  [db {:keys [product supply min-base fresh]} e t]
  (let [[src v] supply
        s (or (series db [src e product v])
              (when (and (= src "psd") (eu-members e)) (series db [src "EUN" product v])))
        k (known-series s t)
        [y] (last k)]
    (when (and y (>= y (- t fresh)))
      (when-let [d (shortfall k y min-base)]
        (if (< d floor) 0.0 d)))))

(defn family-parts
  "{:exporters :straits} of c's loss in a family at t, nil when c's
  dependence is unknown for every product."
  [db family c t]
  (let [specs (families family)
        known (for [sp specs
                    :let [p (:product sp)
                          mm (dependence db family p c t)
                          u (use-weight db family p c t)]
                    :when (and mm u (pos? u))]
                [sp mm u])
        tot (reduce + 0.0 (map #(nth % 2) known))]
    (when (and (seq known) (pos? tot))
      (reduce (fn [acc [sp mm u]]
                (let [s (/ u tot)
                      [cp cls] (:choke sp)
                      w (supplier-shares (get-in db [:flows [(:flow sp) c]]) t)
                      ex (reduce + 0.0 (for [[e we] w] (* we (or (exporter-shortfall db sp e t) 0.0))))
                      ck (reduce + 0.0 (for [[k xs] (get-in db [:choke [c cp]])
                                             :let [x (value-at xs t max-age)
                                                   g (strait-disruption (get-in db [:capacity cls]) k t)]
                                             :when (and x g)]
                                         (* x g)))]
                  (-> acc
                      (update :exporters + (* s mm ex))
                      (update :straits + (* s mm ck)))))
              {:exporters 0.0 :straits 0.0}
              known))))

(defn family-loss
  "c's share of the family's supply lost at t, capped at 1, or nil."
  [db family c t]
  (when-let [{:keys [exporters straits]} (family-parts db family c t)]
    (min 1.0 (+ exporters straits))))

(defn panel
  "{iso {t loss}} of a family over `isos` and `years`, the known values only."
  [db family isos years]
  (into {} (for [c isos
                 :let [byt (into (sorted-map) (for [t years :let [v (family-loss db family c t)] :when (some? v)] [t v]))]
                 :when (seq byt)]
             [c byt])))

;; --- placebos ------------------------------------------------------------------------------

(defn derangement
  "{c c'}: a random permutation of `isos` with no fixed point, from `seed`
  (the isos sorted first, so the order given never matters)."
  [isos seed]
  (let [cs (vec (sort (distinct isos)))
        n (count cs)
        g (m/rng seed)]
    (loop []
      (let [a (object-array cs)]
        (loop [i (dec n)]
          (when (pos? i)
            (let [j (long (* (inc i) (m/next-double! g)))
                  tmp (aget a i)]
              (aset a i (aget a j))
              (aset a j tmp)
              (recur (dec i)))))
        (let [p (zipmap cs (vec a))]
          (if (some (fn [[x y]] (= x y)) p) (recur) p))))))

(defn shift-panel
  "The panel with each value moved `lag` years later: at t the value of t -
  lag, which was known by then."
  [panel lag]
  (update-vals panel (fn [byt] (into (sorted-map) (for [[t v] byt] [(+ t lag) v])))))

;; --- reading --------------------------------------------------------------------------------

(defn index
  "The lookups from input rows ({:source :iso :product :year :known
  :variable :value}) and flow rows ({:product :importer :exporter :year
  :known :tonnes})."
  [rows flows]
  (let [add (fn [m k y kn v] (update m k (fnil assoc (sorted-map)) y [kn v]))]
    {:series (reduce (fn [m {:keys [source iso product year known variable value]}]
                       (if (#{"choke" "portwatch"} source) m (add m [source iso product variable] year known value)))
                     {} rows)
     :choke (reduce (fn [m {:keys [source iso product year known variable value]}]
                      (if (= source "choke") (update-in m [[iso product] variable] (fnil assoc (sorted-map)) year [known value]) m))
                    {} rows)
     :capacity (reduce (fn [m {:keys [source iso product year known value]}]
                         (if (= source "portwatch") (update-in m [product iso] (fnil assoc (sorted-map)) year [known value]) m))
                       {} rows)
     :flows (reduce (fn [m {:keys [product importer exporter year known tonnes]}]
                      (update m [product importer]
                              (fn [s] (let [s (or s (sorted-map))
                                            [_ by] (get s year [known {}])]
                                        (assoc s year [known (assoc by exporter tonnes)])))))
                    {} flows)}))

(defn- read-csv [path f]
  (with-open [r (io/reader path)]
    (let [[_ & lines] (line-seq r)]
      (into [] (map #(f (str/split % #","))) lines))))

(defn read-db
  "The index from data/derived/shocks (scripts/supply-shocks-data.py)."
  [dir]
  (index (read-csv (str dir "/inputs.csv")
                   (fn [[s iso p y k v x]] {:source s :iso iso :product p :year (parse-long y) :known (parse-long k)
                                            :variable v :value (parse-double x)}))
         (read-csv (str dir "/flows.csv")
                   (fn [[p i e y k t]] {:product p :importer i :exporter e :year (parse-long y) :known (parse-long k)
                                        :tonnes (parse-double t)}))))
