(ns bnm.supply
  "The product graph of the material economy (docs/STOCK-FLOW.md, \"Product
  graph\"; epic bnm-1pq): what each product is made from, who uses it, and
  where, per region, from the EXIOBASE hybrid tables 2011 (physical units)
  and the monetary product-by-product tables (value shares), as written by
  scripts/product-graph.py to data/derived/supply/.

  A product made in a region is a node [region product]; its recipe's
  inputs are the products used in that region, each drawn from its
  origins in the shares the multi-regional tables give (trade, domestic
  included). Each recipe row is classified (`classify`): critical
  (Leontief, no substitute) or substitutable (CES).

    downstream  what a product reaches: products made from it, through
                recipes and trade, and the industries making them, with
                the depth (recipe steps) and the dependence share (the
                share of the node's value that traces back to the
                product, summed over every path: the cost-push Leontief
                inverse);
    upstream    what a product ultimately needs: the Leontief inverse in
                the hybrid units (tonnes, TJ, M EUR per unit), with the
                value dependence alongside;
    outage      a cut of a product in a region propagated through the
                recipes as a bnm.hypergraph (critical inputs cap their
                output, the substitutable ones pass on a CES loss, imports
                are not reallocated), solved by hypergraph/propagate;
    outage-path the same month by month with stocks (months of use at
                a region's use of a product): a stock drains at the
                shortfall before the cut passes on; outage is its limit.

  One year (2011) of structure: recipes do not change in it, and the
  value shares of 1995 and 2022 are kept to see how they moved."
  (:require [bnm.csv :as csv]
            [bnm.hypergraph :as hg]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ------------------------------------------------------------ the rule

(def critical-share
  "A good (not a service) whose share of the output's value is at least
  this is critical, unless the hybrid table records its flow as absent
  or negligible (`negligible-heat`)."
  0.05)

(def negligible-heat
  "Where the hybrid table records a good's flow into a product in
  physical units (both in tonnes or TJ), the physics decides, not the
  value share: a good at 0 per unit is absent, and a fuel (a good with an
  `ncv`, or a carrier in TJ: electricity, steam, nuclear fuel) whose heat
  is under this part of the heat of every fuel in the recipe is
  negligible. Either is the monetary tables routing a flow differently
  from the physical one (crude at 6-10% of the value of Italy's, the
  Netherlands' and Poland's N fertiliser, 0 t per t; natural gas at
  0.3-0.9% of nuclear power's value, 0.2 kg per TJ against 3 TJ of
  nuclear fuel): not critical whatever its share, and left with value
  added like gas distribution in a power station (`hypergraph`). A
  material at any tonnage above 0 is present: its tonnes per tonne
  cannot be read against the recipe's (an alloying metal, a catalyst).
  Electricity by source is read as its pool. A recipe the hybrid table
  records no tonne or TJ of keeps the value-share rule."
  0.01)

(def feedstock
  "A raw material or fuel going into an energy carrier or material at this
  much per unit or more is a feedstock, critical whatever its value share
  (the monetary tables route some feedstocks through other products):
  tonnes per tonne, or for an energy carrier in TJ, TJ of the fuel's heat
  (`ncv`) per TJ."
  0.1)

(def ncv
  "Net calorific values, TJ per tonne, of the fuels EXIOBASE's hybrid
  tables count in tonnes: the IPCC 2006 Guidelines' defaults (vol. 2,
  table 1.2). Other hydrocarbons and additives take crude's and
  gasoline's; non-energy oil products the 40.2 TJ/Gg of lubricants."
  {"C_ANTH" 0.0267 "C_COKC" 0.0282 "C_OTBC" 0.0258 "C_SUBC" 0.0189 "C_LIBC" 0.0119
   "C_PEAT" 0.00976 "C_PATF" 0.0207 "C_BKBP" 0.0207 "C_COKE" 0.0282 "C_GCOK" 0.0282
   "C_COTA" 0.028 "C_COIL" 0.0423 "C_GASE" 0.048 "C_GASL" 0.0442 "C_OGPL" 0.0423
   "C_REFF" 0.043 "C_ADDC" 0.0443 "C_RGAS" 0.0495 "C_ETHA" 0.0464 "C_LPGA" 0.0473
   "C_NAPT" 0.0445 "C_MGSL" 0.0443 "C_AGSL" 0.0443 "C_GJET" 0.0443 "C_KJET" 0.0441
   "C_KERO" 0.0438 "C_DOIL" 0.043 "C_FOIL" 0.0404 "C_WHSP" 0.0402 "C_LUBR" 0.0402
   "C_BITU" 0.0402 "C_PARW" 0.0402 "C_PETC" 0.0325 "C_NSPP" 0.0402 "C_CHAR" 0.0295
   "C_BIOG" 0.027 "C_BIOD" 0.027 "C_OBIO" 0.0274 "C_MBIO" 0.0504 "C_COOG" 0.0387
   "C_MGWG" 0.0387 "C_MBFG" 0.00247 "C_MOSG" 0.00706})

(def yield-inputs
  "Inputs whose loss a crop answers with a yield response, not a recipe's
  hard cap: N fertiliser, P and compound fertiliser, and chemicals nec
  (crop protection), substitutable (CES) in a crop's recipe whatever their
  value share. A field with less fertiliser or spray still yields, less:
  the yield curve is concave and soils carry nutrients over a season."
  #{"C_NFER" "C_PFER" "C_CHEM"})

(def gas-processing
  "The refined products natural gas is a feedstock of (gas processing sits
  in EXIOBASE's refinery industry); the industry-technology split spreads
  its gas over every refinery product, 0.1-0.4 t per t of diesel in
  eight regions, which no refinery makes diesel from."
  #{"C_ETHA" "C_LPGA" "C_RGAS"})

(def refined
  "EXIOBASE's refined oil products (the refinery industry's output)."
  #{"C_RGAS" "C_ETHA" "C_LPGA" "C_NAPT" "C_AGSL" "C_MGSL" "C_GJET" "C_KJET" "C_KERO" "C_DOIL"
    "C_FOIL" "C_WHSP" "C_LUBR" "C_BITU" "C_PARW" "C_PETC" "C_NSPP" "C_REFF"})

(def crops
  "EXIOBASE's crop products."
  #{"C_PARI" "C_WHEA" "C_OCER" "C_FVEG" "C_OILS" "C_SUGB" "C_FIBR" "C_OTCR"})

(def max-input-share
  "The most an output's inputs can be worth together, a share of its
  value: an output whose monetary coefficients add to 1 or more (a tiny
  output, or the two releases disagreeing) has them scaled down to this."
  0.99)

(def default-sigma
  "The elasticity of substitution of a recipe's substitutable inputs among
  themselves and with value added: near Leontief in the short run
  (Atalay 2017; Boehm, Flaaen & Pandalai-Nayar 2019)."
  0.2)

(def energy-sigma
  "The elasticity of substitution among the heat fuels of an output's
  energy bundle (gas, oil, coal, electricity, steam): the prior 0.5, half
  the interfuel elasticities of the medium-run energy models (GTAP-E's 1
  between gas and oil, 0.5 between coal and the rest; MIT EPPA's 1) for a
  horizon of months, between Moll et al.'s (2023) glass furnace that
  cannot switch overnight and the dual-fuel boilers and kilns their 36
  cases of German firms switched in 2022. Held at their availability, the
  other fuels cannot rise, so a bundle loses at least the cut fuel's share
  of the heat times its cut whatever sigma: sigma moves the loss within
  that (a 40% cut of half the heat: 20% linear, 25% at 0.5, 31% at 0.2)."
  0.5)

(def equipment
  "Machinery, equipment and vehicles: in a farm's or a mine's recipe they
  are its capital's replacement and repair, deferred in a short-run
  shortage, so substitutable whatever their value share (machinery at 7%
  of Germany's vegetables made them lose 14% under the gas cut)."
  #{"C_FABM" "C_MACH" "C_OFMA" "C_ELMA" "C_RATV" "C_MEIN" "C_MOTO" "C_OTRE"})

(def upkeep
  "A power station's upkeep: the machinery and equipment (`equipment`),
  metals (steel, aluminium, copper, lead/zinc/tin, other non-ferrous,
  precious metals, foundry work, fabricated metal products),
  construction and the renting of machinery in an electricity or heat
  carrier's recipe (`power-station?`), all substitutable there (a
  carrier is critical on its fuel alone, `kind-of`). Repair and
  replacement wait for the next planned outage, so in `outage-path` they
  are a stock at the station (`upkeep-months`), drawn at the shortfall
  before the loss passes on; in `outage`, the steady state, they pass on
  as before."
  (into equipment #{"C_STEL" "C_ALUM" "C_COPP" "C_LZTP" "C_ONFM" "C_PREM" "C_METC" "C_FABM" "C_CONS" "C_MARE"}))

(def upkeep-months
  "How long a power station can defer its `upkeep`, in months of its
  normal upkeep, a prior: maintenance is done at planned outages, yearly
  for a thermal plant and at refuelling for a nuclear one (12-24 months,
  a third to a quarter of the core each time), so a station meets a cut
  on average half a cycle from its next outage, about 6 months with a
  yearly one, and the work can wait for it. Deferring past it (a second
  outage put off) is not counted. A cut of 30% of an upkeep input draws
  it in 20 months."
  6.0)

(def petrochemical
  "Outputs whose fuels are all their feedstock (the rule as it is, no
  energy bundle): N fertiliser, ammonia from gas, coal, naphtha or fuel
  oil."
  #{"C_NFER"})

(def coals #{"C_ANTH" "C_COKC" "C_OTBC" "C_SUBC" "C_LIBC" "C_PEAT"})

(def heat-outputs
  "The energy and raw products made with process heat rather than by
  converting a fuel: coal products (coke ovens, briquettes), the
  steelworks' gases (their feedstock coke and coal), biofuels, and the
  ores and quarrying (drying, calcining, haulage). Material and
  manufactured goods are heat outputs too, but for `petrochemical`."
  #{"C_COKE" "C_GCOK" "C_COTA" "C_PATF" "C_BKBP" "C_CHAR" "C_COOG" "C_MBFG" "C_MOSG"
    "C_BIOG" "C_BIOD" "C_OBIO"
    "C_ORAN" "C_IRON" "C_COPO" "C_NIKO" "C_ALUO" "C_PREO" "C_LZTO" "C_ONFO" "C_STON" "C_SDCL" "C_CHMF"})

(def material-feedstock
  "{output fuels}: the fuels a heat output is made from, critical one by
  one by the rule (crude, NGL, other hydrocarbons and refinery feedstocks
  are everywhere: no one burns them for heat). Steel: coke and coal
  (reductants; gas in direct reduction, the Middle East's and Mexico's,
  is counted as heat, which understates them); aluminium: petroleum coke
  and coal tar (anodes); basic plastics: the oil and gas liquids (naphtha,
  ethane, LPG, refinery gas, gas oil, fuel oil; Germany's 0.37 t of fuel
  oil per t is its integrated sites'), its natural gas counted as the
  crackers' heat (ethane from gas processing is a refinery product, a
  feedstock where the tables record it); chemicals nec: the oil feedstocks (its gas,
  hydrogen and methanol as well as heat and steam, is counted as heat:
  Germany's ammonia is in N fertiliser); coal products and the
  steelworks' gases: coal and coke."
  (let [cokes (into coals #{"C_COKE" "C_GCOK"})]
    (merge
     {"C_STEL" (into cokes #{"C_CHAR" "C_PETC"})
      "C_ALUM" #{"C_PETC" "C_COTA"}
      "C_CHEM" #{"C_NAPT" "C_ETHA" "C_LPGA" "C_RGAS" "C_WHSP" "C_PARW" "C_COTA"}
      "C_PLAS" #{"C_NAPT" "C_ETHA" "C_LPGA" "C_RGAS" "C_WHSP" "C_PARW" "C_DOIL" "C_FOIL" "C_KERO" "C_NSPP"}}
     (into {} (for [o ["C_COKE" "C_GCOK" "C_COTA" "C_PATF" "C_BKBP" "C_COOG" "C_MBFG" "C_MOSG"]] [o cokes])))))

(def crude-like #{"C_COIL" "C_GASL" "C_OGPL" "C_REFF"})

(def dri-share
  "{region share}: the part of a region's steel made from gas-based
  direct-reduced iron, where it is large: DRI over crude steel in 2011,
  worldsteel's Steel Statistical Yearbook 2012 (table 42, DRI; table 1,
  crude steel; data/raw/material/worldsteel): Mexico 5,854 kt over
  18,101 kt, 0.32; the Middle East 20,056 kt (Iran 10,368, Saudi Arabia
  5,153, Oman 1,400 estimated, Qatar 1,335, the Emirates 1,800) over
  23,002 kt, 0.87 (0.81 without Oman's, an estimate from a country with
  no crude steel row, its DRI exported). The shares had been remembered
  as 0.3 and 0.8 (bnm-1pq.24); Midrex's World Direct Reduction
  Statistics, the other free source, could not be downloaded. EXIOBASE
  has one steel product and its gas rows cannot tell a shaft furnace's
  reductant from a reheating furnace's fuel (0.017 t of gas per t of
  steel in the Middle East, 0.019 in Mexico, no more than Spain's), so
  this part of the output goes with the gas one for one, the rest with
  the energy bundle. Gas DRI elsewhere (Venezuela and Trinidad in WL,
  Egypt and Libya in WF, Russia, India's few gas plants) is a smaller
  part of its region's steel and stays heat."
  {"MX" (/ 5854.0 18101.0) "WM" (/ 20056.0 23002.0)})

(def uncalorific-fuels
  "The fuels a carrier is made from that have no ncv to read their
  tonnes by: wood and crops in biomass power, uranium ore in nuclear
  fuel. A carrier is critical on these by their value share."
  #{"C_FORE" "C_WOOD" "C_OTCR" "C_ORAN"})

(def heat-critical
  "The heat per tonne, TJ, at which a heat output that is not a
  manufactured good (a material, an ore, a coal product) has its energy
  bundle critical whatever its fuels' tonnages and shares: 2 GJ/t, the
  heat of 0.04 t of gas or 0.1 t of hard coal, as the feedstock rule
  reads 0.1 t of one fuel. A kiln or a furnace stops without heat: cement
  (3 GJ/t in Germany), glass (3.5), ceramics (7), paper (12). A
  manufactured good's heat per tonne measures its weight, not its need,
  so there the rule's thresholds alone decide."
  0.002)

(declare pool)

(defn power-station?
  "Is `output` an electricity carrier (C_POW*, by source) or steam and
  hot water: a power station or a heat plant, whose `upkeep` is deferred
  in `outage-path`?"
  [output]
  (boolean (or (pool output "TJ") (= output "C_HWAT"))))

(defn pool
  "The carrier an input belongs to with its substitutes, or nil:
  :electricity for electricity by source (EXIOBASE's C_POW* in TJ), one
  carrier to its users whatever made it."
  [code unit]
  (when (and (= unit "TJ") (re-matches #"C_POW[A-Z]" code)) :electricity))

(defn- feedstock?
  "A raw material or fuel converted into an energy carrier or material at
  `feedstock` or more: tonnes per tonne into a product in tonnes; into an
  energy carrier in TJ, the fuel's heat per TJ (tonnes at their `ncv`, or
  TJ as they are). Natural gas feeds only the gas-processing products of
  a refinery."
  [{g :group u :unit} {og :group ou :unit} input output phys]
  (let [phys (or phys 0.0)]
    (boolean
     (and (#{"raw" "energy"} g)
          (not (and (= input "C_GASE") (refined output) (not (gas-processing output))))
          (or (and (= u "tonnes") (= ou "tonnes") (#{"energy" "material"} og) (>= phys feedstock))
              (and (= ou "TJ") (= og "energy") (not (pool input u))
                   (>= (* phys (case u "TJ" 1.0 "tonnes" (get ncv input 0.0) 0.0)) feedstock)))))))

(defn- fuel-heat
  "The heat of a row's input per unit of output, TJ, when it is a fuel:
  phys at its `ncv`, or phys for a carrier in TJ; else nil."
  [products {:keys [input phys]}]
  (let [{u :unit g :group} (products input)]
    (cond (ncv input) (* (double (or phys 0.0)) (ncv input))
          (and (= u "TJ") (= g "energy")) (double (or phys 0.0)))))

(defn- physical-pair?
  "Does the hybrid table record `input` into `output` in physical units,
  a good into a product in tonnes or TJ?"
  [products input output]
  (let [{iu :unit ig :group} (products input)
        ou (get-in products [output :unit])]
    (boolean (and ig (not= ig "service") (#{"tonnes" "TJ"} iu) (#{"tonnes" "TJ"} ou)))))

(defn- physically-negligible
  "Rows `rs` with :negligible true where the hybrid table records the
  good's flow as absent or negligible (`negligible-heat`): its phys 0, or
  a fuel's heat (an electricity pool's together) under negligible-heat of
  the recipe's, in a recipe with some tonne or TJ of a good recorded."
  [products rs]
  (let [pool-of #(pool % (get-in products [% :unit]))
        tot (reduce (fn [m {:keys [region output input phys] :as r}]
                      (if (physical-pair? products input output)
                        (let [k [region output] q (or (fuel-heat products r) 0.0)]
                          (cond-> (-> m (update-in [k :phys] (fnil + 0.0) (or phys 0.0))
                                      (update-in [k :heat] (fnil + 0.0) q))
                            (pool-of input) (update-in [k (pool-of input)] (fnil + 0.0) q)))
                        m))
                    {} rs)]
    (mapv (fn [{:keys [region output input phys] :as r}]
            (let [{p :phys h :heat :as t} (tot [region output])
                  k (pool-of input)
                  q (if k (get t k) (fuel-heat products r))]
              (if (and (physical-pair? products input output) (pos? (or p 0.0))
                       (or (if k (<= q 0.0) (<= (or phys 0.0) 0.0))
                           (and q (< q (* negligible-heat h)))))
                (assoc r :negligible true)
                r)))
          rs)))

(defn- kind-of
  "A row's kind: a good (not a service) at critical-share or more of the
  output's value, but where the hybrid table finds it absent or
  negligible (`negligible-heat`), or a feedstock (`feedstock?`): critical; else, and
  always for a yield input in a crop (a yield response) and for
  equipment in a raw product (its capital's upkeep), substitutable. An
  energy carrier in TJ (electricity, steam, nuclear fuel) is critical on
  its fuel alone: by the feedstock test, or by value share for the
  `uncalorific-fuels`; the other goods a power station buys, at any
  share, are upkeep (EXIOBASE's industry mix gives Bulgaria's wind power
  chemicals at 18% of its value and steam at 13%)."
  [products {:keys [input output phys share negligible]}]
  (let [{g :group :as ip} (products input)
        op (products output)
        carrier? (and (= "TJ" (:unit op)) (= "energy" (:group op)))]
    (if (and g (not= g "service")
             (not (and (yield-inputs input) (crops output)))
             (not (and (equipment input) (= "raw" (:group op))))
             (or (and (or (not carrier?) (uncalorific-fuels input)) (not negligible) (>= (or share 0.0) critical-share))
                 (feedstock? ip op input output phys)))
      :critical
      :substitutable)))

(defn- clean-shares
  "Recipe rows `rs` with their value shares fit for use: a share above 1
  (or below 0) dropped (:share nil, :share-flag :over-1), then an
  output's remaining shares scaled to sum to max-input-share where they
  add to 1 or more (:share-flag :scaled); :share-raw keeps the share as
  read."
  [rs]
  (let [rs (mapv (fn [{:keys [share] :as r}]
                   (cond (nil? share) r
                         (or (> share 1.0) (neg? share)) (assoc r :share nil :share-raw share :share-flag :over-1)
                         :else (assoc r :share-raw share)))
                 rs)
        tot (reduce (fn [m {:keys [region output share]}] (if share (update m [region output] (fnil + 0.0) share) m)) {} rs)]
    (mapv (fn [{:keys [region output share] :as r}]
            (let [t (tot [region output])]
              (if (and share t (>= t 1.0))
                (assoc r :share (* share (/ max-input-share t)) :share-flag :scaled)
                r)))
          rs)))

(defn- pooled
  "Rows `rs` with the pooled inputs (`pool`) of each recipe classified
  together: all critical when their shares add to critical-share (but a
  pool the hybrid table finds negligible), else all substitutable; each
  tagged with its :pool."
  [products rs]
  (let [pool-of #(pool % (get-in products [% :unit]))
        tot (reduce (fn [m {:keys [region output input share negligible]}]
                      (if-let [k (pool-of input)]
                        (update m [region output k] (fnil + 0.0) (if negligible 0.0 (or share 0.0)))
                        m))
                    {} rs)]
    (mapv (fn [{:keys [region output input] :as r}]
            (if-let [k (pool-of input)]
              (assoc r :pool k :kind (if (>= (tot [region output k]) critical-share) :critical :substitutable))
              r))
          rs)))

(defn- coal-pooled
  "Rows `rs` with a power station's or heat plant's coals
  (`power-station?`, `coals`) classified together: all critical, tagged
  :pool :coal, when one of them is critical by the rule (a feedstock at
  0.1 TJ per TJ, or 5% of the value); the negligible ones
  (`negligible-heat`) are no members. Coals each below the rule are not
  added up into a critical pool: in a gas or biomass station they are
  the industry mix's CHP (Germany's gas power 0.05 TJ of lignite and
  0.07 of hard coal per TJ), and adding them would make 49 rows critical
  that are not now.
  One coal stands in for another in a station's boilers (blending,
  switching mines), so in outage the pool is the share-weighted mean of
  its members, as electricity by source is (`critical-inputs`): a 10% cut
  to America's lignite, 3% of its coal power's coals by value and 5% by
  heat, had stopped 10% of that power, lignite a feedstock on its own."
  [products rs]
  (let [member? (fn [{:keys [output input negligible]}] (and (coals input) (power-station? output) (not negligible)))
        crit (reduce (fn [m {:keys [region output kind] :as r}]
                       (if (and (member? r) (= kind :critical)) (conj m [region output]) m))
                     #{} rs)]
    (mapv (fn [{:keys [region output] :as r}]
            (if (and (member? r) (crit [region output]))
              (assoc r :kind :critical :pool :coal)
              r))
          rs)))

(defn heat-output?
  "Is `output` made with process heat (its fuels, but its
  `material-feedstock`, an energy bundle)? A material or manufactured
  good in tonnes, but for `petrochemical`, or one of `heat-outputs`."
  [products output]
  (let [{g :group u :unit} (products output)]
    (boolean (and (= u "tonnes") (not (petrochemical output))
                  (or (#{"material" "manufactured"} g) (heat-outputs output))))))

(defn heat
  "The heat a row's fuel gives per unit of output, TJ (phys at the fuel's
  `ncv`, or phys for a carrier in TJ: electricity, steam), when `input`
  is a heat fuel of `output` (a fuel, steam or electricity, not its
  `material-feedstock` nor crude-like); else nil."
  [products {:keys [input output phys]}]
  (let [u (get-in products [input :unit])]
    (when (and (heat-output? products output)
               (not (crude-like input))
               (not (get-in material-feedstock [output input]))
               (or (ncv input) (= input "C_HWAT") (pool input u)))
      (* (double (or phys 0.0)) (if (= u "TJ") 1.0 (get ncv input 0.0))))))

(defn- energy-pooled
  "Rows `rs` with the heat fuels (`heat`) of each heat output's recipe
  tagged :pool :energy and :heat (TJ per unit), and classified together:
  all critical when one of them is (by the rule, electricity as its pool),
  their shares add to critical-share (the negligible ones'
  aside, `negligible-heat`), or (but in a manufactured good)
  their heat adds to heat-critical; else all substitutable as they were.
  A row's kind by the rule alone is kept as :kind-rule."
  [products rs]
  (let [rs (mapv (fn [r] (if-let [q (heat products r)] (assoc r :energy? true :heat q) r)) rs)
        crit (reduce (fn [m {:keys [region output kind share energy? heat negligible]}]
                       (if energy?
                         (update m [region output] (fn [[c t q]] [(or c (= kind :critical))
                                                                  (+ (or t 0.0) (if negligible 0.0 (or share 0.0)))
                                                                  (+ (or q 0.0) heat)]))
                         m))
                     {} rs)]
    (mapv (fn [{:keys [region output energy? kind] :as r}]
            (if energy?
              (let [[c t q] (crit [region output])
                    c (or c (>= t critical-share)
                          (and (not= "manufactured" (get-in products [output :group])) (>= q heat-critical)))]
                (-> (dissoc r :energy?)
                    (assoc :kind-rule kind :kind (if c :critical :substitutable))
                    (cond-> c (assoc :pool :energy :carrier (:pool r)))))
              r))
          rs)))

;; ------------------------------------------------------------ the graph

(declare graph* critical-hypergraph)

(defn graph
  "The product graph from rows (as the CSVs give them, keys keywordised):
  :products {:code :name :unit :group}, :recipes {:region :output :input
  :phys :share ...}, :trade {:product :origin :dest :share}, :supply
  {:region :industry :product :industry-share}, :use {:region :product
  :user :kind :quantity :share :value-share}. Recipes are classified on
  the way in."
  [{:keys [products recipes trade supply use]}]
  (let [ps (into {} (for [p products] [(:code p) (dissoc p :code)]))
        rs (energy-pooled ps (coal-pooled ps (pooled ps (mapv #(assoc % :kind (kind-of ps %)) (physically-negligible ps (clean-shares recipes))))))
        g (graph* ps rs trade supply use)]
    (assoc g :critical-h (delay (critical-hypergraph g)))))

(defn- graph* [ps rs trade supply use]
  {:products ps
   :recipes (reduce (fn [m {:keys [region output input] :as r}]
                      (assoc-in m [[region output] input] (dissoc r :region :output :input)))
                    {} rs)
   :inputs-of (reduce (fn [m {:keys [region output input] :as r}]
                        (assoc-in m [[region input] output] r))
                      {} rs)
   :imports (reduce (fn [m {:keys [product origin dest share]}] (assoc-in m [[dest product] origin] share)) {} trade)
   :exports (reduce (fn [m {:keys [product origin dest share]}] (assoc-in m [[origin product] dest] share)) {} trade)
   :makes (reduce (fn [m {:keys [region industry product industry-share]}]
                    (if (and industry-share (pos? industry-share))
                      (assoc-in m [[region industry] product] industry-share)
                      m))
                  {} supply)
   :use (group-by (juxt :region :product) use)})

(defn classify
  "The kind of `input` in `output`'s recipe in `region`: :critical or
  :substitutable; nil where the recipe has no such input."
  [g region output input]
  (get-in g [:recipes [region output] input :kind]))

(defn recipe-rows
  "Every recipe row, with :region :output :input and its :kind."
  [g]
  (for [[[r o] ins] (:recipes g) [i row] ins] (assoc row :region r :output o :input i)))

(defn share-flags
  "{flag rows}: the recipe rows whose value share was dropped (:over-1)
  or scaled (:scaled) by the graph (`clean-shares`)."
  [g]
  (frequencies (keep :share-flag (recipe-rows g))))

(defn users
  "The direct uses of `product` in `region`: industries and final demand,
  largest first. :value-share the use's value over the user's output
  value, nil where that comes out above 1 (rough prices: monetary output
  over hybrid supply), the ratio then flagged as :value-rough."
  [g region product]
  (->> (get-in g [:use [region product]])
       (map (fn [{vs :value-share rough :value-rough :as u}]
              (let [rough (or rough (when (and vs (> vs 1.0)) vs))]
                (cond-> (assoc (select-keys u [:region :user :kind :quantity :share])
                               :value-share (when (and vs (<= vs 1.0)) vs))
                  rough (assoc :value-rough rough)))))
       (sort-by (comp - :share))
       vec))

(defn- origins
  "{origin share} of `product` as used in `region`: the trade shares, or
  all domestic where the tables give none."
  [g region product]
  (or (get-in g [:imports [region product]]) {region 1.0}))

(defn- destinations
  "{dest share}: where `origin`'s `product` goes, as a share of each
  destination's use of it; home at 1 where the destination has no trade
  record of it."
  [g origin product]
  (let [ex (get-in g [:exports [origin product]] {})]
    (if (get-in g [:imports [origin product]]) ex (assoc ex origin 1.0))))

;; ------------------------------------------------------- walking the graph

(def ^:private tiny 1e-12)

(defn- series
  "Σ_k≥1 of x_k, x_{k+1} = (step x_k), from x_0: until a term's total is
  below tol of the sum. Throws past `cap` steps."
  [step x0 what]
  (loop [x (step x0) total {} k 0]
    (let [total (merge-with + total x)
          size (reduce + 0.0 (map #(Math/abs (double %)) (vals x)))]
      (cond (< size (* 1e-10 (max 1.0 (reduce + 0.0 (vals total))))) total
            (> k 5000) (throw (ex-info (str "bnm.supply: " what " did not converge") {:steps k :last size}))
            :else (recur (step x) total (inc k))))))

(defn- add [m k v] (if (< (Math/abs (double v)) tiny) m (assoc! m k (+ (get m k 0.0) v))))

(defn- push-down
  "One recipe step downstream of the value vector x ({[region product]
  value}): to the uses in every destination, then to the outputs made
  from them."
  [g x]
  (let [uses (persistent! (reduce-kv (fn [acc [o i] v]
                                       (reduce-kv (fn [acc s t] (add acc [s i] (* v t))) acc (destinations g o i)))
                                     (transient {}) x))]
    (persistent! (reduce-kv (fn [acc [s i] v]
                              (reduce-kv (fn [acc p {:keys [share]}] (add acc [s p] (* v (or share 0.0))))
                                         acc (get-in g [:inputs-of [s i]])))
                            (transient {}) uses))))

(defn- hops
  "{node depth}: breadth-first from `src` over (succ node) -> nodes."
  [succ src]
  (loop [frontier [src] seen {src 0} d 0]
    (if (empty? frontier)
      seen
      (let [nxt (distinct (for [n frontier m (succ n) :when (not (contains? seen m))] m))]
        (recur nxt (into seen (map #(vector % (inc d)) nxt)) (inc d))))))

(defn- down-succ
  "The nodes one recipe step down of [o i]: outputs of i in every
  destination, along edges that pass `keep?` [trade-share row]."
  [g keep?]
  (fn [[o i]]
    (for [[s t] (destinations g o i)
          [p row] (get-in g [:inputs-of [s i]])
          :when (keep? t row)]
      [s p])))

(defn- critical-inputs
  "The hypergraph `h` with the pools of output [s p]'s critical inputs
  `ins` as nodes: [:pool s p carrier] the share-weighted mean of its
  members; [:energy s p] the heat fuels' CES (elasticity `esigma`,
  weights their heat, electricity as its pool); [:dri s p] for steel in
  a `dri-share` region, the share-weighted mean of the gas used there
  and the rest of the output ([:dri-other s p], primary, never cut); and
  the critical recipe's inputs {node weight}."
  [h s p ins w esigma]
  (let [crit (for [[i r] ins :when (= :critical (:kind r))] [i r])
        pools (group-by (comp :pool second) crit)
        elec (group-by (comp :carrier second) (pools :energy))
        hw #(max 1e-6 (double (or % 0.0)))
        h (reduce-kv (fn [h k members]
                       (if (and k (not= k :energy))
                         (hg/recipe h [:pool s p k] (into {} (for [[i r] members] [[:use s i] (w (:share r))])) :substitutable)
                         h))
                     h pools)
        h (if-let [em (elec :electricity)]
            (hg/recipe h [:pool s p :electricity] (into {} (for [[i r] em] [[:use s i] (w (:share r))])) :substitutable)
            h)
        h (if (pools :energy)
            (hg/recipe h [:energy s p]
                       (into {} (concat (for [[i r] (elec nil)] [[:use s i] (hw (:heat r))])
                                        (when-let [em (elec :electricity)]
                                          [[[:pool s p :electricity] (hw (reduce + 0.0 (map (comp #(or % 0.0) :heat second) em)))]])))
                       :ces esigma)
            h)
        dri (when (= p "C_STEL") (dri-share s))
        h (if dri
            (hg/recipe h [:dri s p] {[:use s "C_GASE"] dri [:dri-other s p] (- 1.0 dri)} :substitutable)
            h)]
    [h (into {} (concat (for [[i r] (pools nil)] [[:use s i] (w (:share r))])
                        (for [k (keys pools) :when (and k (not= k :energy))] [[:pool s p k] 1.0])
                        (when (pools :energy) [[[:energy s p] 1.0]])
                        (when dri [[[:dri s p] 1.0]])))]))

(defn- critical-hypergraph
  "The recipes' critical rows alone as a bnm.hypergraph: [:make region
  product] the minimum of its critical inputs' [:use ...], each the
  trade-share mean of its origins' [:make ...]. Products with no critical
  input are primary (untouched)."
  [g]
  (let [h (reduce-kv (fn [h [s p] ins]
                       (let [[h crit] (critical-inputs h s p ins #(max 1e-6 (double (or % 0.0))) energy-sigma)]
                         (if (seq crit) (hg/recipe h [:make s p] crit :critical) h)))
                     (hg/hypergraph) (:recipes g))
        used (distinct (for [[_ ins] (:recipes h) i (keys (:inputs ins)) :when (= :use (first i))] (vec (rest i))))]
    (reduce (fn [h [s i]] (hg/recipe h [:use s i] (into {} (for [[o t] (origins g s i) :when (pos? t)] [[:make o i] t]))
                                     :substitutable))
            h used)))

(defn exposure
  "{[region product] share}: what each product would lose were `product`
  made in `region` cut entirely and its critical recipes Leontief (the
  substitutable inputs aside, imports not reallocated): the hard
  dependence however small the value share (plastics on crude). Only
  products that lose anything; to 1e-6."
  [g region product]
  (let [h (force (:critical-h g))
        a (hg/propagate h {[:make region product] 0.0} {:tol 1e-6})]
    (into {} (for [[[t r p] v] a :when (and (= t :make) (< v 1.0))] [[r p] (- 1.0 v)]))))

(defn downstream
  "What `product` made in `region` reaches: {:products [{:region :product
  :depth :dependence :exposure}] :industries [{:region :industry :depth
  :dependence :exposure}]}, largest dependence first. dependence: the
  share of the node's value that traces back to the product over every
  path (value shares times trade shares, summed: the cost-push Leontief
  inverse less the product itself). exposure: see `exposure`. depth:
  recipe steps on the shortest path along edges of weight at least
  :min-edge (default 0.01) or critical, else along any. Kept: dependence
  at least :min-share (default 0.01), or exposure at least :min-exposure
  (default 0.05). An industry is the mix of the products it makes (their
  dependence and exposure weighted by its output shares; its least
  depth)."
  ([g region product] (downstream g region product {}))
  ([g region product {:keys [min-edge min-share min-exposure] :or {min-edge 0.01 min-share 0.01 min-exposure 0.05}}]
   (let [src [region product]
         dep (series #(push-down g %) {src 1.0} "downstream dependence")
         ex (exposure g region product)
         d-strong (hops (down-succ g (fn [t row] (or (= :critical (:kind row)) (>= (* t (or (:share row) 0.0)) min-edge)))) src)
         d-any (delay (hops (down-succ g (fn [t row] (pos? (* t (or (:share row) 0.0))))) src))
         nodes (->> (concat (keys dep) (keys ex))
                    distinct
                    (remove #{src}))
         ps (for [[r p :as n] nodes]
              {:region r :product p :depth (or (d-strong n) (@d-any n)) :dependence (get dep n 0.0)
               :exposure (get ex n 0.0)})
         keep? #(or (>= (:dependence %) min-share) (>= (:exposure %) min-exposure))
         by-node (into {} (map (juxt (juxt :region :product) identity) ps))
         inds (for [[[r j] mix] (:makes g)
                    :let [hit (keep (fn [[p w]] (when-let [x (by-node [r p])] [x w])) mix)
                          tot (reduce + (vals mix))]
                    :when (seq hit)]
                {:region r :industry j
                 :depth (reduce min (map (comp #(or % Long/MAX_VALUE) :depth first) hit))
                 :dependence (/ (reduce + (for [[x w] hit] (* w (:dependence x)))) tot)
                 :exposure (/ (reduce + (for [[x w] hit] (* w (:exposure x)))) tot)})]
     {:products (vec (sort-by (comp - :dependence) (filter keep? ps)))
      :industries (vec (sort-by (comp - :dependence) (filter keep? inds)))})))

(defn- push-up
  "One recipe step upstream of x ({[region product] [quantity value]}):
  each output's inputs as used in its region, then to their origins."
  [g x]
  (let [uses (persistent! (reduce-kv (fn [acc [s p] [q v]]
                                       (reduce-kv (fn [acc i {:keys [phys share]}]
                                                    (let [k [s i] [q0 v0] (get acc k [0.0 0.0])]
                                                      (assoc! acc k [(+ q0 (* q (or phys 0.0))) (+ v0 (* v (or share 0.0)))])))
                                                  acc (get-in g [:recipes [s p]])))
                                     (transient {}) x))]
    (persistent! (reduce-kv (fn [acc [s i] [q v]]
                              (reduce-kv (fn [acc o t]
                                           (let [k [o i] [q0 v0] (get acc k [0.0 0.0])
                                                 q' (* q t) v' (* v t)]
                                             (if (and (< (Math/abs q') tiny) (< (Math/abs v') tiny))
                                               acc
                                               (assoc! acc k [(+ q0 q') (+ v0 v')]))))
                                         acc (origins g s i)))
                            (transient {}) uses))))

(defn upstream
  "What one unit of `product` made in `region` ultimately needs: [{:region
  :product :quantity :unit :dependence :depth}], largest dependence first.
  quantity: the Leontief inverse in the hybrid units (tonnes, TJ or M EUR
  of the input per unit of the product, every round of the supply chain
  summed, through the trade shares); dependence: the same in value shares;
  depth: the fewest recipe steps. The product itself appears where a cycle
  brings it back (beyond the unit). Kept: dependence at least :min-share
  (default 0.001), or a physical quantity of at least :min-quantity
  (default 0.01) where input and product are both in tonnes or TJ."
  ([g region product] (upstream g region product {}))
  ([g region product {:keys [min-share min-quantity] :or {min-share 0.001 min-quantity 0.01}}]
   (let [src [region product]
         step (fn [x] (push-up g x))
         total (loop [x (step {src [1.0 1.0]}) total {} k 0]
                 (let [total (merge-with (fn [[a b] [c d]] [(+ a c) (+ b d)]) total x)
                       size (reduce + 0.0 (map (fn [[q v]] (+ (Math/abs q) (Math/abs v))) (vals x)))
                       scale (reduce + 0.0 (map (fn [[q v]] (+ (Math/abs q) (Math/abs v))) (vals total)))]
                   (cond (< size (* 1e-10 (max 1.0 scale))) total
                         (> k 5000) (throw (ex-info "bnm.supply: upstream did not converge" {:steps k :last size}))
                         :else (recur (step x) total (inc k)))))
         depth (hops (fn [[s p]] (for [[i row] (get-in g [:recipes [s p]])
                                       :when (or (pos? (or (:phys row) 0.0)) (pos? (or (:share row) 0.0)))
                                       o (keys (origins g s i))]
                                   [o i]))
                     src)
         unit #(get-in g [:products % :unit])
         phys? #(#{"tonnes" "TJ"} (unit %))]
     (->> (for [[[r p :as n] [q v]] total
                :when (or (>= v min-share) (and (phys? p) (phys? product) (>= q min-quantity)))]
            {:region r :product p :quantity q :unit (unit p) :dependence v :depth (depth n)})
          (sort-by (comp - :dependence))
          vec))))

;; ---------------------------------------------------------------- outage

(defn hypergraph
  "The product graph as a bnm.hypergraph: [:make region product] is
  critical over its critical inputs and a bundle, the bundle [:bundle
  region product] a CES (elasticity `sigma`) of its substitutable inputs
  and [:factors region] (labour and capital, weighted by one less the
  input shares); a recipe with no critical input is the bundle itself.
  The heat fuels of a heat output, when critical, are one critical input,
  [:energy region product], their CES (elasticity `esigma`, default
  energy-sigma; weights their heat). [:use region product] is the share-weighted mean of its origins' [:make
  ...] (imports not reallocated); [:industry region code] the mean of what
  it makes, by output share. A power station's `upkeep` input enters its
  bundle through [:upkeep region product input], which passes [:use
  region input] on (the node `outage-path` holds its deferral at). Substitutable inputs with a share below
  `min-input` (default 1e-3) are left with value added: a CES bundle
  barely feels them, and they are half the rows. So is gas distribution
  (C_GASD) in an electricity or heat carrier's recipe: the monetary
  tables' route for the gas a power station buys, which its physical
  fuel row already holds (EXIOBASE's industry mix gives France's nuclear
  power gas distribution at 34% of its value, and with it a 26% loss
  under the gas cut). So is a good the hybrid table finds absent or
  negligible in the recipe (`negligible-heat`): the monetary route of a
  flow its physical rows hold elsewhere (natural gas at 0.87% of the
  Netherlands' nuclear power's value, 0.2 kg per TJ, had cost it 1.7%
  under the gas cut)."
  ([g] (hypergraph g default-sigma))
  ([g sigma] (hypergraph g sigma 1e-3))
  ([g sigma min-input] (hypergraph g sigma min-input energy-sigma))
  ([g sigma min-input esigma]
   (let [w #(max 1e-6 (double (or % 0.0)))
         h (reduce-kv
            (fn [h [s p] ins]
              (let [[h crit] (critical-inputs h s p ins w esigma)
                    critical? (into #{} (for [[i r] ins :when (= :critical (:kind r))] [:use s i]))
                    carrier? (= ["TJ" "energy"] ((juxt :unit :group) (get-in g [:products p])))
                    sub-rows (for [[i r] ins :when (and (= :substitutable (:kind r)) (>= (or (:share r) 0.0) min-input)
                                                        (not (and carrier? (= i "C_GASD")))
                                                        (not (:negligible r)))]
                               [i r])
                    sub-in (set (map first sub-rows))
                    upkeep? (fn [i] (and carrier? (power-station? p) (upkeep i)))
                    h (reduce (fn [h [i _]] (if (upkeep? i) (hg/recipe h [:upkeep s p i] {[:use s i] 1.0} :substitutable) h))
                              h sub-rows)
                    subs (into {} (for [[i r] sub-rows] [(if (upkeep? i) [:upkeep s p i] [:use s i]) (w (:share r))]))
                    va (max 0.01 (- 1.0 (reduce + 0.0 (for [[i r] ins :when (or (critical? [:use s i]) (sub-in i))]
                                                         (or (:share r) 0.0)))))
                    bundle (assoc subs [:factors s] va)]
                (if (seq crit)
                  (-> h
                      (hg/recipe [:bundle s p] bundle :ces sigma)
                      (hg/recipe [:make s p] (assoc crit [:bundle s p] 1.0) :critical))
                  (hg/recipe h [:make s p] bundle :ces sigma))))
            (hg/hypergraph) (:recipes g))
         used (distinct (for [[n ins] (:recipes h) i (keys (:inputs ins)) :when (= :use (first i))] (vec (rest i))))
         h (reduce (fn [h [s i]] (hg/recipe h [:use s i] (into {} (for [[o t] (origins g s i) :when (pos? t)] [[:make o i] t]))
                                            :substitutable))
                   h used)]
     (reduce-kv (fn [h [r j] mix] (hg/recipe h [:industry r j] (into {} (for [[p x] mix] [[:make r p] x])) :substitutable))
                h (:makes g)))))

(defn- cut-hypergraph
  "[h given]: the hypergraph for `outage`'s options (built, or :hypergraph)
  with a [:use region product] node for a cut at :use where it has none,
  and the cuts as given availabilities."
  [g cuts {:keys [at sigma] esig :energy-sigma :or {at :make sigma default-sigma esig energy-sigma} :as opts}]
  (let [h (or (:hypergraph opts) (hypergraph g sigma 1e-3 esig))
        tag (case at :make :make :use :use)
        h (reduce (fn [h [r p]] (if (and (= tag :use) (not (get-in h [:recipes [:use r p]])))
                                  (hg/recipe h [:use r p] {[:make r p] 1.0} :substitutable)
                                  h))
                  h (keys cuts))]
    [h (into {} (for [[[r p] x] cuts] [[tag r p] (- 1.0 (double x))]))]))

(defn- pick
  "{[region code] availability}: the nodes tagged `k` below 1 (by more than 1e-9)."
  [a k]
  (into {} (for [[[t & ks] v] a :when (and (= t k) (< v (- 1.0 1e-9)))] [(vec ks) v])))

(defn outage
  "Cut `cuts` ({[region product] fraction}, 0.5 a cut of half) and
  propagate: {:products {[region product] availability} :industries
  {[region code] availability} :settled {:updates :residual :order}}, the
  nodes below 1 (by more than 1e-9), and how hypergraph/propagate
  settled.
  Options: :at :make (default; the product's output in the region) or
  :use (what the region can use of it, all origins); :sigma the
  substitutable inputs' elasticity (default-sigma); :energy-sigma the heat
  fuels' (energy-sigma); :hypergraph one built already by `hypergraph`
  (with those); :tol (default 1e-6) for
  hypergraph/propagate."
  ([g cuts] (outage g cuts {}))
  ([g cuts {:keys [tol] :or {tol 1e-6} :as opts}]
   (let [[h given] (cut-hypergraph g cuts opts)
         a (hg/propagate h given {:tol tol})]
     {:products (pick a :make) :industries (pick a :industry) :settled (meta a)})))

;; ------------------------------------------------------ stocks and time

(defn- settle
  "hypergraph/propagate's chaotic iteration from `x` ({node value}, at or
  above the greatest fixed point), the queue seeded with `seeds`, every
  stocked node n (`stock` {node months}) at min(1, its recipe + its
  stock): a month's use drawn from the stock covers the shortfall, up to
  what is left. [settled {node value}, updates done]."
  [prep users given stock x seeds tol max-updates]
  (let [value (fn [x n] (let [r (hg/rule (prep n) #(get x %) (get given n))]
                          (if-let [s (stock n)] (min 1.0 (+ r s)) r)))]
    (loop [x (transient x) queue (into clojure.lang.PersistentQueue/EMPTY seeds) queued (set seeds) k 0]
      (if-let [n (peek queue)]
        (do (when (>= k max-updates)
              (throw (ex-info (str "bnm.supply/outage-path did not settle in " k " updates") {:updates k :node n})))
            (let [v (value x n) queue (pop queue) queued (disj queued n)]
              (if (> (Math/abs (- (get x n) v)) tol)
                (let [fresh (remove queued (users n))]
                  (recur (assoc! x n v) (into queue fresh) (into queued fresh) (inc k)))
                (recur x queue queued (inc k)))))
        [(persistent! x) k]))))

(defn outage-path
  "The outage month by month with stocks (bnm-1pq.23): an outage drains a
  stock before it cuts a flow. `stocks` {[region product] months}: what a
  region holds of a product, in months of its normal use, at [:use region
  product] (what the region uses of it, all origins). Each month t = 1..H
  (:months, default 24) a stocked node supplies its inflow (its recipe,
  the cut applied) plus what it draws from its stock, at most the
  shortfall and at most what is left: min(1, inflow + stock); the stock
  falls by what was drawn, and only once it is empty does the cut pass
  downstream whole. The cut holds every month and nothing refills a
  stock; imports from the origins a cut does not reach keep flowing, so
  a stock covers the cut origins' share alone. A power station's
  `upkeep` is a stock too, at [:upkeep region station input]: every
  station holds :upkeep-months (default `upkeep-months`; 0 for none) of
  each upkeep input unless `stocks` gives one under [region station
  input]. [{:month t :products {[region product] availability}
  :industries {[region code] availability} :stocks {[region product]
  months left} :drawn {[region product] months drawn in t} :upkeep
  {[region station input] months left, where any was drawn} :updates
  n}], stocks and upkeep at the month's end, so (merge (:stocks m)
  (:upkeep m)) restarts the path after month m;
  products and industries below 1 as in `outage`, whose answer is the
  limit once every stock the cut reaches is empty (and, with no stocks,
  every month's). Stocks on nodes the graph does not use are ignored.

  Each month's operator is below the last's (stocks only fall), so a
  month is solved from the last month's answer, at or above its greatest
  fixed point: only what the stocks running short move is recomputed
  (the tests check every month against the month solved afresh).
  A stock is drawn at the shortfall against normal use, even where its
  users are held back by another input and need less: it drains early,
  if anything. Options as for `outage`, and :months, :upkeep-months."
  ([g cuts stocks] (outage-path g cuts stocks {}))
  ([g cuts stocks {:keys [tol months] um :upkeep-months :or {tol 1e-6 months 24 um upkeep-months} :as opts}]
   (let [[h given] (cut-hypergraph g cuts opts)
         prep (hg/prepared (:recipes h))
         users (reduce-kv (fn [m out {:keys [ins]}] (reduce #(update %1 %2 (fnil conj []) out) m ins)) {} prep)
         nodes (distinct (concat (keys given) (keys prep) (keys users)))
         max-updates (max 1000000 (* 2000 (count nodes)))
         x0 (into {} (for [n nodes] [n (if (prep n) 1.0 (double (get given n 1.0)))]))
         seeds0 (distinct (concat (filter prep (keys given)) (mapcat users (remove prep (keys given)))))
         full (merge (into {} (for [n (keys prep) :when (= :upkeep (first n))] [n (double um)]))
                     (into {} (for [[k m] stocks
                                    :let [n (case (count k) 2 (into [:use] k) 3 (into [:upkeep] k) nil)]
                                    :when (and n (prep n))]
                                [n (double m)])))
         s0 (into {} (filter (comp pos? val) full))
         inflow (fn [x n] (hg/rule (prep n) #(get x %) (get given n)))
         key-of (fn [n] (vec (rest n)))
         use? #(= :use (first %))]
     (loop [t 1 x x0 s s0 seeds seeds0 out []]
       (if (> t months)
         out
         (let [[x k] (settle prep users given s x seeds tol max-updates)
               drawn (into {} (for [[n m] s :let [d (min m (max 0.0 (- (get x n) (inflow x n))))] :when (> d 1e-12)] [n d]))
               s' (reduce-kv (fn [s n d] (assoc s n (max 0.0 (- (s n) d)))) s drawn)]
           (recur (inc t) x s' (keys drawn)
                  (conj out {:month t :products (pick x :make) :industries (pick x :industry)
                             :stocks (into {} (for [[n m] s' :when (use? n)] [(key-of n) m]))
                             :drawn (into {} (for [[n d] drawn :when (use? n)] [(key-of n) d]))
                             :upkeep (into {} (for [[n m] s' :when (and (not (use? n)) (< m (full n)))] [(key-of n) m]))
                             :updates k}))))))))

;; ---------------------------------------------------------------- loading

(defn- num [s] (when (and s (not (str/blank? s))) (parse-double s)))

(defn- read-rows
  "The rows of a plain CSV (no quoted fields) as maps through `f`."
  [path f]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ks (mapv keyword (str/split h #","))]
      (mapv #(f (zipmap ks (str/split % #"," -1))) (doall ls)))))

(defn load-graph
  "The product graph from the CSVs in `dir` (data/derived/supply)."
  [dir]
  (let [f #(str (io/file dir %))]
    (graph
     {:products (mapv #(select-keys % [:code :name :unit :group]) (csv/read-maps (f "products.csv") \,))
      :recipes (read-rows (f "recipes.csv")
                          (fn [m] {:region (:region m) :output (:output m) :input (:input m)
                                   :phys (num (:phys m)) :share (num (:share_2011 m))
                                   :share-1995 (num (:share_1995 m)) :share-2022 (num (:share_2022 m))
                                   :physical? (= "1" (:physical m))
                                   ;; the source of a hybrid row product-graph.py
                                   ;; overrode (its OVERRIDES; overrides.csv)
                                   :override (not-empty (:override m))}))
      :trade (read-rows (f "trade.csv") (fn [m] (-> m (update :quantity num) (update :share num))))
      :supply (read-rows (f "supply.csv")
                         (fn [m] {:region (:region m) :industry (:industry m) :product (:product m)
                                  :industry-share (num (:industry_share m))}))
      :use (read-rows (f "use.csv")
                      (fn [m] {:region (:region m) :product (:product m) :user (:user m) :kind (:kind m)
                               :quantity (num (:quantity m)) :share (num (:share m))
                               :value-share (num (:value_share m)) :value-rough (num (:value_rough m))}))})))

(defn stock-rows
  "The rows of a stocks CSV (data/derived/supply/stocks.csv, written by
  scripts/supply-stocks.py): {:region :product :months :year :known :basis
  :source}, months the stock over a month of the region's normal use of
  the product, year the year it describes, known the year it is known,
  basis data (a series) or prior (a cited figure)."
  [path]
  (read-rows path (fn [m] {:region (:region m) :product (:product m) :months (num (:months m))
                           :year (parse-long (:year m)) :known (parse-long (:known m))
                           :basis (:basis m) :source (:source m)})))

(defn stocks
  "{[region product] months} from stock rows, the positive ones."
  [rows]
  (into {} (for [{:keys [region product months]} rows :when (and months (pos? months))] [[region product] months])))
