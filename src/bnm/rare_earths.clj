(ns bnm.rare-earths
  "The rare-earth chain as sub-nodes of the product graph (bead
  bnm-1pq.31; docs/STOCK-FLOW.md, \"Product graph\"): the specs
  bnm.supply/split-subnodes takes out of EXIOBASE's chemicals n.e.c.
  (C_CHEM), fabricated metal products (C_FABM) and electrical machinery
  (C_ELMA), 2011 as the graph is.

    C_REOX  rare-earth oxides and compounds, separated (HS 284610,
            284690): world output the separation stage's 2011 (data/
            derived/supply/stages.csv), at BACI's 2011 unit value;
    C_REMT  rare-earth metals (HS 280530): C_CHEM's world output split by
            BACI 2011 trade (bnm.concordance/split-parent), at BACI's unit
            value; made from 1.166 t of oxide per t (Nd2O3 per 2 Nd);
    C_NDFB  NdFeB magnets (in HS 850511): `magnet-of-metal` of the metal
            over `re-in-magnet`, valued cost-plus (bnm.supply/
            split-subnodes): 850511's 2011 unit value, 28 USD/kg, mixes
            in cheaper magnets and lies under the 27 USD of metal an
            NdFeB magnet held at 2011's price;
    C_EVMO  EV traction motors: battery-electric and plug-in cars sold in
            2011 (IEA via data/derived/supply/tech-installs.csv) x
            `ev-motor-t`, each with `ev-re-kg` of rare earths in NdFeB;
    C_WGEN  permanent-magnet wind generators: the rare earths of 2011's
            onshore and offshore additions (IRENA capacity, recipe-
            priors.edn's 14 and 239 t per GW) as magnets, in generators of
            `dd-magnet` t of magnet over `dd-generator` t per MW; capital,
            so final demand (gross fixed capital formation);
    C_PMMO  magnet motors and electronics n.e.c. (industrial motors, hard
            drives, speakers, appliances): the magnets the two above leave,
            at the EV motor's magnet per tonne, made where electrical
            machinery is.

  Supply shares come from production, not exports: oxides and metals by
  the separation stage's country shares (`separation-shares`: the
  balance, an upper bound on China, or its MIIT quota, a lower bound),
  magnets by `magnet-shares`, a cited prior; the user nodes by their own
  output. The latest data year with a quota (2024) gives the shares, the
  2011 graph the sizes: today's concentration on 2011's structure."
  (:require [bnm.cell-profiles :as cprof]
            [bnm.concordance :as conc]
            [bnm.csv :as csv]
            [bnm.stages :as stages]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ---------------------------------------------------------------- priors

(def year "The graph's year: sizes, unit values, sales and additions." 2011)

(def eur-per-usd
  "EUR per USD in 2011: the ECB's reference rate, 1.3920 USD per EUR on
  average (BACI is in USD, EXIOBASE in EUR)."
  (/ 1.0 1.3920))

(def reo-per-metal
  "t of oxide per t of rare-earth metal: Nd2O3 (336.48 g/mol) over two Nd
  (2 x 144.24), the stoichiometric minimum; La, Ce and Pr are within 2%."
  (/ 336.48 288.48))

(def re-in-magnet
  "Rare earths' part of an NdFeB magnet's mass: about 30% (Nd, Pr, Dy, Tb;
  Pavel et al. 2017, as resources/bnm/recipe-priors.edn's offshore wind
  note: 0.6-0.7 t of NdFeB per MW direct drive, about 30% rare earth)."
  0.3)

(def magnet-of-metal
  "Magnets' part of the rare-earth metal made, a prior with no source on
  disk (range 0.7-0.95): magnets take the Nd, Pr, Dy and Tb metal, most
  of it; the rest is mischmetal for NiMH batteries, alloys and steel. It
  sizes the magnet node (its tonnes, not its supply shares)."
  0.85)

(def ev-re-kg
  "kg of rare earths in an electric car's traction motor: 0.5 (IEA 2021,
  The Role of Critical Minerals in Clean Energy Transitions, minerals in
  an electric car; recipe-priors.edn :iea-minerals)."
  0.5)

(def ev-motor-t
  "t of traction motor per car: 0.07, the middle of DOE's 40-100 kg of
  electrical steel per vehicle motor (US DOE 2023, Critical Materials
  Assessment, table of material intensities, after voestalpine 2019), the
  core being most of a motor's mass. A prior: it sizes the node's tonnes
  and its share of C_ELMA, not the magnets."
  0.07)

(def dd-magnet
  "t of NdFeB per MW of a direct-drive generator: 0.65 (US DOE 2023, B5.2.3,
  after Imholte et al. 2018)."
  0.65)

(def dd-generator
  "t per MW of a permanent-magnet generator's active parts: its electrical
  steel, 2.45-3.47 t/MW offshore (US DOE 2023, table of material
  intensities), 3.0 taken, and its 0.65 t of magnet. A prior, as
  `ev-motor-t`."
  3.65)

(def magnet-shares
  "Where NdFeB magnets are made, by EXIOBASE region: China about 90%
  (IEA 2021; the European Commission's 2020 foresight study gives China
  85-90% of capacity). No country table is on disk: the rest is an
  assumption, Japan's sintered-magnet makers (Shin-Etsu, TDK, Proterial)
  0.07, Germany's Vacuumschmelze 0.01 and Japanese makers' plants in
  Vietnam and the Philippines 0.02."
  {"CN" 0.90 "JP" 0.07 "DE" 0.01 "WA" 0.02})

(def nodes
  "The sub-nodes: code, name, unit, group, parent, the HS92 codes in
  data/derived/supply/subnodes.csv (none for the user nodes) and where
  the output the chain does not take goes (bnm.supply/split-subnodes)."
  [{:code "C_REOX" :name "Rare-earth oxides and compounds (separated)" :unit "tonnes" :group "material"
    :parent "C_CHEM" :hs ["284610" "284690"] :demand :parent}
   {:code "C_REMT" :name "Rare-earth metals" :unit "tonnes" :group "material"
    :parent "C_CHEM" :hs ["280530"] :demand :parent}
   {:code "C_NDFB" :name "NdFeB permanent magnets" :unit "tonnes" :group "manufactured"
    :parent "C_FABM" :hs ["850511"] :demand :parent}
   {:code "C_EVMO" :name "EV traction motors (permanent magnet)" :unit "tonnes" :group "manufactured"
    :parent "C_ELMA" :demand {:product "C_MOTO"}}
   {:code "C_WGEN" :name "Permanent-magnet wind generators" :unit "tonnes" :group "manufactured"
    :parent "C_ELMA" :demand {:final "I_GFCF"}}
   {:code "C_PMMO" :name "Magnet motors and electronics n.e.c." :unit "tonnes" :group "manufactured"
    :parent "C_ELMA" :demand :parent}])

;; ---------------------------------------------------------------- shares

(defn region-shares
  "{iso3 x} summed into EXIOBASE regions and normalised: {region share}."
  [by-iso]
  (stages/shares (reduce-kv (fn [m iso x] (if-let [r (cprof/exio-region iso)] (update m r (fnil + 0.0) x) m)) {} by-iso)))

(defn separation-q
  "{iso t REO} of the rare-earth separation stage in data year `y`, as
  known by `k` (the latest edition then): :balance, stages.csv's (China
  the mass balance, an upper bound), or :quota, China at its MIIT quota
  (stages-chem.csv's quota rows, a lower bound) and the others as built."
  [stage-rows chem-rows bound y k]
  (let [rs (filter #(and (= "rare-earths" (:commodity %)) (= "separation" (:stage %)) (= y (:year %))) stage-rows)
        rs (case bound
             :balance rs
             :quota (filter #(= y (:year %))
                            (stages/alternative rs (filter #(= "quota" (:method %)) chem-rows) "quota")))
        v (stages/vintage rs k)]
    (reduce (fn [m r] (update m (:iso r) (fnil + 0.0) (:quantity r))) {} v)))

(defn quota-years
  "The data years with a quota row (stages-chem.csv) known by k."
  [chem-rows k]
  (sort (distinct (for [r chem-rows :when (and (= "quota" (:method r)) (= "rare-earths" (:commodity r)) (<= (:known r) k))]
                    (:year r)))))

;; ---------------------------------------------------------------- the specs

(defn- uv
  "EUR M per t of HS92 codes `hs` in BACI's world rows {hs6 {:value kUSD
  :tonnes t}}."
  [world hs]
  (let [v (reduce + 0.0 (keep #(get-in world [% :value]) hs))
        t (reduce + 0.0 (keep #(get-in world [% :tonnes]) hs))]
    (* (/ v t) 1e-3 eur-per-usd)))

(defn- spread [total shares] (into {} (for [[r s] shares :when (pos? s)] [r (* total s)])))

(defn specs
  "The sub-node specs for bnm.supply/split-subnodes from:
    :world       {hs6 {:value :tonnes}}, BACI HS92 world exports in `year`;
    :split       {code EUR M}, the sub-node codes' value in their parent's
                 output by split-parent (needs only C_REMT's);
    :separation  {:world t, :shares {region share}}: the stage's world
                 output in `year` and the supply shares;
    :ev          {region cars sold in year};
    :wind        {region {:onshore MW :offshore MW}} added in year;
    :intensity   {:onshore t RE per GW :offshore t RE per GW};
    :elma        {region t of C_ELMA made}.
  Also returns, under each spec, :basis (what sized it)."
  [{:keys [world split separation ev wind intensity elma]}]
  (let [by (into {} (map (juxt :code identity)) nodes)
        p-ox (uv world ["284610" "284690"])
        p-mt (uv world ["280530"])
        p-mg (uv world ["850511"])
        x-ox (:world separation)
        x-mt (/ (get split "C_REMT") p-mt)
        x-mg (/ (* magnet-of-metal x-mt) re-in-magnet)
        m-ev (/ (* ev-re-kg 1e-3) re-in-magnet ev-motor-t)
        m-wg (/ dd-magnet dd-generator)
        ev-t (into {} (for [[r n] ev :when (pos? n)] [r (* n ev-motor-t)]))
        wind-mg (into {} (for [[r {:keys [onshore offshore]}] wind
                               :let [t (/ (+ (* 1e-3 (or onshore 0.0) (:onshore intensity))
                                             (* 1e-3 (or offshore 0.0) (:offshore intensity)))
                                          re-in-magnet)]
                               :when (pos? t)]
                           [r t]))
        rest-mg (- x-mg (reduce + 0.0 (for [[_ t] ev-t] (* t m-ev))) (reduce + 0.0 (vals wind-mg)))
        _ (when (neg? rest-mg) (throw (ex-info "rare-earths: EV motors and wind take more magnet than is made" {:magnets x-mg})))
        elma-t (reduce + 0.0 (vals elma))
        x-pm (/ rest-mg m-ev)
        spec (fn [code & kvs] (merge (dissoc (by code) :hs) (apply hash-map kvs)))]
    [(spec "C_REOX" :output (spread x-ox (:shares separation)) :price p-ox
           :basis {:world-t x-ox :eur-m-per-t p-ox :split-eur-m (get split "C_REOX")
                   :shares-year (:year separation) :bound (:bound separation)})
     (spec "C_REMT" :output (spread x-mt (:shares separation)) :price p-mt :inputs {"C_REOX" reo-per-metal}
           :basis {:world-t x-mt :eur-m-per-t p-mt :split-eur-m (get split "C_REMT")})
     (spec "C_NDFB" :output (spread x-mg magnet-shares) :price :cost-plus :inputs {"C_REMT" re-in-magnet}
           :basis {:world-t x-mg :baci-eur-m-per-t p-mg})
     (spec "C_EVMO" :output ev-t :price :cost-plus :inputs {"C_NDFB" m-ev}
           :basis {:cars (reduce + 0.0 (vals ev)) :magnet-t (* m-ev (reduce + 0.0 (vals ev-t)))})
     (spec "C_WGEN" :output (into {} (for [[r t] wind-mg] [r (/ t m-wg)])) :price :cost-plus :inputs {"C_NDFB" m-wg}
           :basis {:magnet-t (reduce + 0.0 (vals wind-mg))})
     (spec "C_PMMO" :output (spread x-pm (update-vals elma #(/ % elma-t))) :price :cost-plus :inputs {"C_NDFB" m-ev}
           :basis {:magnet-t rest-mg})]))

;; ---------------------------------------------------------------- files

(defn- num [s] (when-not (str/blank? s) (parse-double s)))

(defn read-world
  "{hs6 {:value :tonnes}} of BACI HS92 world exports in data year `y`
  (hs6-world.csv)."
  [path y]
  (let [pre (str "HS92," y ",")]
    (with-open [r (io/reader path)]
      (into {} (for [l (line-seq r) :when (str/starts-with? l pre)
                     :let [[_ _ _ hs6 v t] (str/split l #",")]]
                 [hs6 {:value (num v) :tonnes (num t)}])))))

(defn wind-intensity
  "{:onshore :offshore} t of rare earths per GW: recipe-priors.edn's
  wind-onshore/rare-earths and wind-offshore/rare-earths means."
  [path]
  (let [rs (into {} (map (juxt :id :mean)) (:recipes (edn/read-string (slurp path))))]
    {:onshore (rs "wind-onshore/rare-earths") :offshore (rs "wind-offshore/rare-earths")}))

(defn read-installs
  "{:ev {region cars} :wind {region {:onshore MW :offshore MW}}} in year
  `y` from tech-installs.csv: battery-electric and plug-in cars sold, and
  wind capacity added (year end less the year before's, at least 0);
  countries only (WLD left out)."
  [path y]
  (let [rows (filter #(not= "WLD" (:iso %)) (csv/read-maps path \,))
        v (fn [s yy] (into {} (for [r rows :when (and (= s (:series r)) (= (str yy) (:year r)))] [(:iso r) (num (:value r))])))
        add (fn [s] (let [a (v s y) b (v s (dec y))] (into {} (for [[i x] a] [i (max 0.0 (- x (get b i 0.0)))]))))
        by-region (fn [m] (reduce-kv (fn [acc iso x] (if-let [r (cprof/exio-region iso)] (update acc r (fnil + 0.0) x) acc)) {} m))]
    {:ev (by-region (merge-with + (v "bev-sales" y) (v "phev-sales" y)))
     :wind (merge-with merge
                       (update-vals (by-region (add "wind-onshore")) #(hash-map :onshore %))
                       (update-vals (by-region (add "wind-offshore")) #(hash-map :offshore %)))}))

(defn load-specs
  "The specs from data/derived/supply in `dir` and graph rows `rows`
  (their :production), or nil when an input file is missing. Options:
  :bound :balance (default) or :quota; :shares-year (default the latest
  with a quota known by :known); :known (default 2026)."
  ([dir rows] (load-specs dir rows {}))
  ([dir rows {:keys [bound known shares-year] :or {bound :balance known 2026}}]
   (let [f #(io/file dir %)
         priors (io/resource "bnm/recipe-priors.edn")
         need ["hs6-world.csv" "concordance.csv" "subnodes.csv" "stages.csv" "stages-chem.csv" "tech-installs.csv"]]
     (when (and priors (every? #(.exists (f %)) need) (seq (:production rows)))
       (let [stage-rows (vec (stages/read-rows (str (f "stages.csv"))))
             chem-rows (vec (stages/read-rows (str (f "stages-chem.csv"))))
             sy (or shares-year (last (quota-years chem-rows known)))
             world (read-world (str (f "hs6-world.csv")) year)
             idx (conc/index (conc/load-concordance (str (f "concordance.csv"))))
             subs (conc/load-subnodes (str (f "subnodes.csv")))
             n #(if (number? %) (double %) (num %))
             total (fn [p k] (reduce + 0.0 (for [r (:production rows) :when (= p (:product r))] (or (n (k r)) 0.0))))
             split (:subnodes (conc/split-parent idx subs "HS92" (update-vals world :value) "C_CHEM" (total "C_CHEM" :value_2011)))
             {:keys [ev wind]} (read-installs (str (f "tech-installs.csv")) year)]
         (specs {:world world
                 :split {"C_REMT" (get split "rare_earth_metals") "C_REOX" (get split "rare_earth_compounds")}
                 :separation {:world (reduce + 0.0 (vals (separation-q stage-rows chem-rows :balance year known)))
                              :shares (region-shares (separation-q stage-rows chem-rows bound sy known))
                              :year sy :bound bound}
                 :ev ev :wind wind
                 :intensity (wind-intensity priors)
                 :elma (into {} (for [r (:production rows) :when (= "C_ELMA" (:product r))
                                      :let [q (n (:quantity r))] :when (and q (pos? q))]
                                  [(:region r) q]))}))))))
