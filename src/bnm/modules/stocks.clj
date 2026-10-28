(ns bnm.modules.stocks
  "Today's models as engine modules (bnm.engine; docs/COUPLED-SYSTEMS.md
  6e, phase A; bead bnm-4hv.9): every model whose stock table bnm.states
  reads (data/derived/stocks/*.csv), declared with its group, place,
  reads (its data sources, and the other models' tables it reads), emits
  (mapped onto section 4c's typed channels) and known-year rules.

  Phase A wraps the models without changing them: each is still run by its
  tool (:tool, which writes :table), and its module's step at year t emits
  the rows its run made known at t, in the table's order. The engine steps
  them in causal order year by year and writes one coupled table
  (data/derived/coupled/state.csv) that must reproduce the stock tables
  exactly, per variable and known year (golden, `mismatches`): bnm.states
  reading it (read-states' :coupled) gives the same readers. A table's
  columns beyond the stocks convention (stages' and cells' top) are not
  carried; a data year (data_year) is, and a source column becomes the
  source (model:<source>).

  Food is two modules: its countries (R) and the world (W: storage
  pressure, W2, and the world balances bnm.cycles reads within the year),
  as section 4a puts the world node in W."
  (:refer-clojure :exclude [run!])
  (:require [bnm.engine :as eng]
            [bnm.factors :as fac]
            [bnm.states :as states]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def channels
  "Section 4c's channel ids, and :PL, politics' hazards to the event
  lattice (the diagram, 4b)."
  #{:C1 :C2 :C3 :C4 :F1 :F2 :F3 :F4 :F5 :F6 :R1 :R2 :R3 :R4 :R5 :R6 :R7 :R8 :R9 :R10
    :D1 :D2 :D3 :D4 :D5 :P1 :P2 :P3 :P4 :P5 :P6 :W1 :W2 :W3 :W4 :X1 :PL})

;; -- a stock table as a module ---------------------------------------------

(defn- field
  "The k-th comma-separated field of line l."
  [^String l k]
  (loop [start 0 i 0]
    (let [j (str/index-of l "," start)]
      (cond (= i k) (subs l start (or j (count l)))
            (nil? j) ""
            :else (recur (inc j) (inc i))))))

(defn load-table
  "{:ix {column index} :by-year {year [line ...]}}: the table's lines by
  their year (the year known), in file order, those whose iso passes
  `select`."
  [path select]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))
          yi (ix "year") ii (ix "iso")]
      {:ix ix
       :by-year (reduce (fn [m l]
                          (if (or (nil? select) (select (field l ii)))
                            (update m (parse-long (field l yi)) (fnil conj []) l)
                            m))
                        {} ls)})))

(defn- lines-emits
  "{variable [[iso value data-year source sd] ...]} of lines `ls` (header
  index `ix`); sd is the value of its companion variable <variable>-sd at
  the same iso and data year (the model's spread, kept as its own
  variable too), nil where the model gives none."
  [ix ls]
  (let [g (fn [f c] (when-let [i (ix c)] (nth f i "")))
        rows (reduce (fn [m l]
                       (let [f (str/split l #"," -1)]
                         (update m (g f "variable") (fnil conj []) [(g f "iso") (g f "value") (g f "data_year") (g f "source")])))
                     {} ls)
        sds (into {} (for [[v rs] rows :when (str/ends-with? v "-sd")
                           :let [base (subs v 0 (- (count v) 3))] :when (contains? rows base)
                           [iso value dy] rs :when (seq value)]
                       [[base iso dy] value]))]
    (if (empty? sds)
      rows
      (into {} (for [[v rs] rows]
                 [v (mapv (fn [[iso _ dy :as r]] (conj r (get sds [v iso dy]))) rs)])))))

(defn- emits-at
  "lines-emits of the lines known at t."
  [{:keys [ix by-year]} t]
  (lines-emits ix (get by-year t)))

(defn table-module
  "A module that steps a model's table (`decl` with :table, the path, and
  :select, a predicate on iso): at year t it emits the rows known at t."
  [{:keys [table select] :as decl}]
  (merge {:place :country :dt 1 :reads [] :stocks {} :params {}}
         (dissoc decl :select)
         {:init (fn [_] (load-table table select))
          :step (fn [state _ _ t _]
                  {:state (update state :by-year dissoc t)
                   :emits (emits-at state t)})}))

;; -- the registry ------------------------------------------------------------

(defn- src [v unit source known] {:var v :unit unit :source source :known {:data-year known}})
(defn- out
  ([v unit known] (out v unit known nil))
  ([v unit known channel] (cond-> {:var v :unit unit :known {:data-year known}} channel (assoc :channel channel))))

(def ^:private food-world-reads
  (vec (for [c ["wheat" "maize" "rice"] k ["production" "use"]]
         {:var (str "food/" c "-" k) :unit "tonnes" :from :food-world :known {:data-year 2}})))

(def declarations
  "Today's models, as modules (without their steps)."
  [{:module :keen :group :D :place :country :table "data/derived/stocks/keen.csv" :tool 'bnm.keen-run
    :doc "Keen's Goodwin-Minsky core per country (bnm.keen), recalibrated every few years"
    :reads [(src "slow/finance-credit-gdp" "% GDP" "data/slow/series.tsv (BIS total credit)" 1)
            (src "jst/tloans-gdp" "ratio to GDP" "data/raw/slow/jst/jst.csv" 1)
            (src "slow/labour-share" "share of GDP" "data/slow/series.tsv (PWT labsh)" 2)]
    :emits [(out "keen/omega" "share of value added" 1 :D1) (out "keen/lambda" "rate" 1 :D1)
            (out "keen/profit-share" "share of value added" 1 :R1)
            (out "keen/d" "ratio to GDP" 1 :F5) (out "keen/d-critical" "ratio to GDP" 1 :F5)
            (out "keen/d-distance" "ratio to GDP" 1 :F5) (out "keen/bad-basin-prob" "probability" 1 :F5)
            (out "keen/minsky" "index" 1 :F5) (out "keen/*" "mixed" 1)]
    :stocks {:omega {:unit "share"} :lambda {:unit "rate"} :d {:unit "ratio to GDP"}}
    :params {:phi-slope {:learned-by 'bnm.keen-run} :kappa-slope {:learned-by 'bnm.keen-run}
             :alpha {:learned-by 'bnm.keen-run} :beta {:learned-by 'bnm.keen-run}
             :delta {:learned-by 'bnm.keen-run} :nu {:learned-by 'bnm.keen-run} :r {:learned-by 'bnm.keen-run}}}
   {:module :credit :group :F :place :country :table "data/derived/stocks/credit.csv" :tool 'bnm.credit-run
    :doc "The one-sided credit-to-GDP gap (Basel's), as known"
    :reads [(src "slow/finance-credit-gdp" "% GDP" "data/slow/series.tsv (BIS total credit)" 1)
            (src "jst/tloans-gdp" "ratio to GDP" "data/raw/slow/jst/jst.csv" 1)]
    :emits [(out "credit/gap" "pp of GDP" 1 :F5) (out "credit/*" "mixed" 1)]
    :params {:lambda {:value 1562.5}}}
   {:module :food-world :group :W :place :world :table "data/derived/stocks/food.csv" :tool 'bnm.food-run
    :select #(= "WLD" %)
    :doc "World cereal balances and storage pressure (bnm.food; the WLD rows)"
    :reads [(src "faostat/fbs-cereals" "tonnes" "data/raw/material/faostat-fbs/fbs-cereals.csv" 2)
            (src "slow/cereal-prices" "USD/t" "data/slow/series.tsv (IMF prices)" 0)]
    :emits (into [(out "food/*-storage-pressure" "index" 2 :W2)]
                 (concat (for [{v :var} food-world-reads] (out v "tonnes" 2))
                         [(out "food/*" "mixed" 2)]))
    :stocks {:stock {:unit "tonnes"}}}
   {:module :food :group :R :place :country :table "data/derived/stocks/food.csv" :tool 'bnm.food-run
    :select #(not= "WLD" %)
    :doc "Cereal balances, import dependence and supplier concentration per country (bnm.food)"
    :reads [(src "faostat/fbs-cereals" "tonnes" "data/raw/material/faostat-fbs/fbs-cereals.csv" 2)
            (src "material/cereal-trade" "tonnes" "data/derived/material/cereal-trade.csv (BACI)" 2)]
    :emits [(out "food/*-import-dependence" "share of use" 2 :R4) (out "food/*-supplier-hhi" "HHI" 2 :R4)
            (out "food/*" "mixed" 2)]
    :stocks {:stock {:unit "tonnes"}}}
   {:module :cycles :group :W :place :world :table "data/derived/stocks/cycles.csv" :tool 'bnm.cycles-run
    :doc "Commodity capacity cycles and world real prices (bnm.cycles); producers' exposures"
    :reads (into [(src "pinksheet/prices" "USD, monthly" "data/raw/market/pinksheet/pinksheet-monthly.csv" 0)
                  (src "fred/wti" "USD/bbl, monthly" "data/raw/slow/market/WTISPLC.csv" 0)
                  (src "fred/cpi" "index" "data/raw/slow/market/CPIAUCNS.csv" 1)
                  (src "slow/world-energy" "physical units" "data/slow/series.tsv (OWID, Energy Institute)" 1)]
                 food-world-reads)
    :emits [(out "cycles/*-price-gap" "log gap" 1 :W1) (out "cycles/*-log-price" "log real price" 1 :W1)
            (out "cycles/*-capacity-gap" "log gap" 1 :R2)
            (out "cycles/*-exposure" "export share x log gap" 1 :W1) (out "cycles/*" "mixed" 1)]
    :stocks {:capacity {:unit "physical units"} :pipeline {:unit "physical units"}}}
   {:module :chokepoints :group :W :place :country :table "data/derived/stocks/chokepoints.csv" :tool 'bnm.chokepoints-run
    :doc "Shares of trade through each strait, by importer and the world (bnm.chokepoints)"
    :reads [(src "baci/routes" "USD" "data/derived/supply (BACI routed)" 2)
            (src "portwatch/daily" "transits" "data/raw/material/imf-portwatch/Daily_Chokepoints_Data.csv" 0)]
    :emits [(out "chokepoint/*" "share of trade value" 2 :W4)]}
   {:module :econ-factors :group :C :place :country :table "data/derived/stocks/econ-factors.csv" :tool 'bnm.econ-factors-run
    :doc "Control and financial-power factors from the econ indicators (bnm.econ-factors)"
    :reads [(src "econ/indicators" "mixed" "data/derived/econ/econ-indicators.csv" 1)]
    :emits [(out "econ/investment-regime" "SD units" 1 :C1) (out "econ/investment-regime-index" "index" 1 :C1)
            (out "econ/public-capital" "SD units" 1 :C1)
            (out "econ/financialisation" "SD units" 1 :C2) (out "econ/financialisation-cfa" "SD units" 1 :C2)
            (out "econ/financial-depth" "SD units" 1 :C2) (out "econ/market-size" "SD units" 1 :C2)
            (out "econ/*-n" "count" 1) (out "econ/*-sd" "SD units" 1)]}
   {:module :control :group :C :place :country :table "data/derived/stocks/control.csv" :tool 'bnm.control-run
    :doc "The control vector: state against private control of capital and credit, a factor with sign priors (bnm.control)"
    :reads [(src "control/indicators" "mixed"
                 "data/derived/control/control-indicators.csv (Panizza, BRSS, Bundesbank, V-Dem, ICSD, WID, KAOPEN, FKRSU, expropriation acts, OECD PMR, WDI)" 1)]
    :emits [(out "control/state" "SD units" 1 :C1) (out "control/ka-open" "index 0-1" 2 :C3)
            (out "control/*-sd" "SD units" 1) (out "control/n" "count" 1) (out "control/*" "mixed" 1)
            (out "control/ka-restrict" "share" 2) (out "control/bank-brss" "share" 2)
            (out "control/public-k-share" "share" 2) (out "control/public-wealth-ni" "ratio to national income" 2)
            (out "control/pmr-public-owner" "index 0-6" 2) (out "control/top1-wealth" "share" 2)]}
   {:module :econ-divergence :group :F :place :country :table "data/derived/stocks/econ-divergence.csv" :tool 'bnm.econ-divergence
    :doc "Asset prices against fundamentals (bnm.econ-divergence)"
    :reads [(src "econ/divergence-inputs" "mixed" "data/derived/econ/divergence-inputs.csv (JST, WID, WDI)" 1)]
    :emits [(out "econ/divergence*" "log gap" 1 :F4)]}
   {:module :econ-coupling :group :F :place :country :table "data/derived/stocks/econ-coupling.csv" :tool 'bnm.econ-coupling-run
    :doc "External liabilities to surplus partners (bnm.econ-coupling)"
    :reads [(src "econ/coupling-pairs" "USD" "data/derived/econ/coupling-pairs.csv (PIP, DIP, BACI)" 2)]
    :emits [(out "econ/coupling-liability-share" "share" 2 :X1) (out "econ/coupling-slope" "per unit surplus" 2 :X1)]}
   {:module :econ-structure :group :R :place :country :table "data/derived/stocks/econ-structure.csv" :tool 'bnm.econ-structure-run
    :doc "Finance and manufacturing shares of value added (bnm.econ-structure)"
    :reads [(src "econ/structure-inputs" "share of VA" "data/derived/econ/structure-inputs.csv (ICIO, KLEMS, WDI)" 1)]
    :emits [(out "econ/finance-va-share" "share of value added" 1 :R6) (out "econ/manufacturing-va-share" "share of value added" 1 :R6)]}
   {:module :migration :group :R :place :country :table "data/derived/stocks/migration.csv" :tool 'bnm.migration-run
    :doc "Capital-intensive production's migration between countries (bnm.migration)"
    :reads [(src "econ/migration" "world share" "data/derived/econ/migration.csv (EXIOBASE)" 2)]
    :emits [(out "migration/ci-share" "world share" 2 :R6) (out "migration/export-share" "world share" 2 :R6)
            (out "migration/*" "mixed" 2)]}
   {:module :stages :group :R :place :country :table "data/derived/stocks/stages.csv" :tool 'bnm.stages
    :doc "Shares and concentration of each mineral stage (mine, refinery, smelter) by country (bnm.stages)"
    :reads [(src "supply/stages" "tonnes" "data/derived/supply/stages.csv (BGS, USGS)" 1)
            (src "supply/stage-plants" "plants" "data/derived/supply/stage-plants.csv" 1)]
    :emits [(out "stages/*-hhi" "HHI" 1 :R6) (out "stages/*-share" "world share" 1 :R6)
            (out "stages/*-d" "displacement" 1 :R6) (out "stages/*" "mixed" 1)]}
   {:module :cells :group :R :place :cell :table "data/derived/stocks/cells.csv" :tool 'bnm.cell-profiles-run
    :doc "Concentration of assets over A5 cells, per country and the world (bnm.cell-profiles)"
    :reads [(src "cells/assets" "assets" "data/derived/cells/assets.csv" 1)]
    :emits [(out "cells/*-hhi" "HHI" 1 :R6) (out "cells/*-share" "share" 1 :R6) (out "cells/*-cells" "count" 1 :R6)]}
   {:module :tech-demand :group :R :place :country :table "data/derived/stocks/tech-demand.csv" :tool 'bnm.tech-demand-run
    :doc "Material demand by technology (bnm.tech-demand)"
    :reads [(src "supply/tech-trade" "USD" "data/derived/supply/tech-trade.csv (BACI)" 2)
            (src "material/capacity" "MW" "data/derived/material/capacity.csv" 2)
            (src "owid/energy" "share" "data/raw/material/owid-energy/owid-energy-data.csv" 1)]
    :emits [(out "tech-demand/*-growth5" "log growth over 5 years" 2 :R9) (out "tech-demand/*" "mixed" 2 :R9)]}
   {:module :technology :group :R :place :country :table "data/derived/stocks/technology.csv" :tool 'bnm.technology-run
    :doc "Technology adoption, lags behind the frontier and productivity inflows (bnm.technology)"
    :reads [(src "slow/wdi-technology" "mixed" "data/slow/series.tsv (WDI)" 1)
            (src "chat/adoption" "mixed" "data/raw/slow/chat/FinalCHAT_72909.csv" 2)]
    :emits [(out "technology/*" "mixed" 1 :R10)]
    :stocks {:adoption {:unit "share"}}}
   {:module :energy :group :R :place :country :table "data/derived/stocks/energy.csv" :tool 'bnm.energy-pilot
    :doc "Energy exposure and price pass-through through the I-O spine (bnm.energy-pilot)"
    :reads [(src "io/spine" "USD" "data/derived/io/spine-flows.csv (I-O tables)" 3)
            (src "io/prices" "index" "data/derived/io/spine-prices.csv" 1)]
    :emits [(out "energy/exposure" "share of output" 3 :R5) (out "energy/pass-through" "share of output" 3 :R5)
            (out "energy/shock" "share of output" 3 :R5)]}
   {:module :essential :group :R :place :region :table "data/derived/stocks/essential.csv" :tool 'bnm.essentiality-run
    :doc "Essentiality: value added at risk from each product's supply (bnm.essentiality)"
    :reads [{:var "stages/*-hhi" :unit "HHI" :known {:data-year 1}}
            {:var "tech-demand/*-growth5" :unit "log growth over 5 years" :known {:data-year 2}}
            {:var "chokepoint/*" :unit "share of trade value" :known {:data-year 2}}
            (src "supply/essentiality-loss" "EUR M" "data/derived/supply/essentiality-loss.csv (bnm.supply outage-path)" 2)
            (src "baci/world" "USD" "data/derived/supply (BACI)" 2)]
    :emits [(out "essential/*-risk" "EUR bn at risk" 2 :R8) (out "essential/*-risk-3m" "EUR bn at risk" 2 :R8)
            (out "essential/*-risk-12m" "EUR bn at risk" 2 :R8) (out "essential/*" "mixed" 2)]}
   {:module :tension :group :P :place :country :table "data/derived/stocks/tension.csv" :tool 'bnm.tension-run
    :doc "Social tension and structural-demographic stress (bnm.tension)"
    :reads [(src "slow/wdi" "mixed" "data/slow/series.tsv (WDI, PWT, WID, IMF GDD)" 2)
            (src "fiscal" "% GDP" "data/derived/fiscal.csv" 2)
            (src "lattice/annual" "events" "data/derived/lattice-annual.csv" 1)]
    :emits [(out "tension/perceived-progress" "log growth" 2 :PL) (out "tension/expected-progress" "log growth" 2 :PL)
            (out "tension/progress-gap" "log growth" 2 :PL) (out "tension/trust" "index" 2 :PL)
            (out "tension/mmp" "index" 2 :PL) (out "tension/emp" "index" 2 :PL) (out "tension/sfd" "index" 2 :PL)
            (out "tension/psi" "index" 2 :PL) (out "tension/*" "mixed" 2)]
    :stocks {:perceived-progress {:unit "log growth" :info true} :expected-progress {:unit "log growth" :info true}
             :trust {:unit "index" :info true}}}
   {:module :geo-gap :group :P :place :country :table "data/derived/stocks/geo-capability.csv" :tool 'bnm.geo-gap-run
    :doc "Material against financial world shares, the capability gap (bnm.geo-gap)"
    :reads [(src "econ/geo-inputs" "world share" "data/derived/econ/geo-inputs.csv" 1)]
    :emits [(out "geo/material-share" "world share" 1 :R6) (out "geo/financial-share" "world share" 1)
            (out "geo/capability-gap" "world share" 1 :PL)]}])

(defn modules
  "The registry as modules."
  [] (mapv table-module declarations))

;; -- running and the golden check ---------------------------------------------

(defn- spill!
  "Writes the lines of `table` passing `select` to `path`, header first,
  sorted by their year (stable: file order within a year). One table is
  in memory at a time."
  [table select path]
  (let [{:keys [by-year]} (load-table table select)
        h (with-open [r (io/reader table)] (first (line-seq r)))]
    (io/make-parents path)
    (with-open [w (io/writer path)]
      (.write w (str h "\n"))
      (doseq [y (sort (keys by-year)) l (by-year y)] (.write w (str l "\n"))))
    path))

(defn stream-module
  "Module `m` (a table-module) stepped from a copy of its table sorted by
  year in `spill-dir`, read as the years pass: what run! steps, so the
  tables are not all held at once. `opened` collects the readers."
  [{:keys [module table select] :as m} spill-dir opened]
  (assoc m
         :init (fn [_]
                 (let [p (spill! table select (str spill-dir "/" (name module) ".csv"))
                       r (io/reader p)
                       [h & ls] (line-seq r)
                       ix (zipmap (str/split h #",") (range))]
                   (swap! opened conj r)
                   {:ix ix :yi (ix "year") :lines ls :skipped 0}))
         :step (fn [{:keys [ix yi lines skipped] :as state} _ _ t _]
                 (let [yr #(parse-long (field % yi))
                       [before ls] (split-with #(< (yr %) t) lines)
                       [now ls] (split-with #(= (yr %) t) ls)]
                   {:state (assoc state :lines ls :skipped (+ skipped (count before)))
                    :emits (lines-emits ix now)}))))

(defn run!
  "Runs the modules from `from` to `to`, writing dir/state.csv (the
  coupled table), dir/graph.{edn,txt} and dir/tables.edn (the tables'
  stamps, bnm.states). A table module is stepped from a copy of its table
  sorted by year (dir/.spill, removed after), read as the years pass.
  {:rows {module n} :leftover {module rows not emitted}}."
  [ms dir {:keys [from to]}]
  (eng/write-graph dir ms)
  (states/write-stamps! dir (distinct (keep :table ms)))
  (let [{:keys [write close]} (eng/table-writer (str dir "/state.csv") ms)
        spill-dir (str dir "/.spill")
        opened (atom [])
        ms (mapv #(if (:table %) (stream-module % spill-dir opened) %) ms)
        rows (atom {})]
    (try
      (let [r (eng/run ms {:from from :to to :keep false
                           :on-step (fn [id t e]
                                      (swap! rows update id (fnil + 0) (reduce + 0 (map count (vals e))))
                                      (write id t e))})]
        {:rows (merge (zipmap (map :module ms) (repeat 0)) @rows)
         :leftover (into {} (for [[id s] (:states r)]
                              [id (cond (contains? s :lines) (+ (:skipped s) (count (:lines s)))
                                        (contains? s :by-year) (reduce + 0 (map count (vals (:by-year s))))
                                        :else 0)]))})
      (finally
        (close)
        (doseq [r @opened] (.close r))
        (let [d (io/file spill-dir)]
          (when (.exists d)
            (doseq [f (.listFiles d)] (.delete f))
            (.delete d)))))))

(defn mismatches
  "[[variable iso year a b] ...]: where two read-states results differ."
  [a b]
  (vec (for [v (sort (distinct (concat (keys a) (keys b))))
             iso (sort (distinct (concat (keys (a v)) (keys (b v)))))
             :let [x (get-in a [v iso]) y (get-in b [v iso])] :when (not= x y)
             yr (sort (distinct (concat (keys x) (keys y))))
             :let [p (get x yr) q (get y yr)] :when (not= p q)]
         [v iso yr p q])))

(defn same-readers?
  "Whether bnm.states' readers of two states maps agree: the same
  variables, and the same value for every variable at every `sample`-th
  country of the place tree in each of its years."
  [a b {:keys [sample] :or {sample 1}}]
  (let [isos (sort (disj (set (mapcat keys (concat (vals a) (vals b)))) "WLD"))
        tree (fac/place-tree isos)
        ra (states/readers a tree (states/default-pairs a))
        rb (states/readers b tree (states/default-pairs b))
        cs (take-nth sample (filter tree isos))
        years (sort (distinct (mapcat keys (mapcat vals (vals a)))))]
    (and (= (set (keys ra)) (set (keys rb)))
         (every? (fn [[v f]] (let [g (rb v)] (every? (fn [c] (every? #(= (f c %) (g c %)) years)) cs))) ra))))

(defn- batches
  "The tables in batches of at most `budget` bytes on disk (a larger table
  alone), in order."
  [tables budget]
  (reduce (fn [acc t]
            (let [n (.length (io/file t)) [b sz] (peek acc)]
              (if (and b (<= (+ sz n) budget)) (conj (pop acc) [(conj b t) (+ sz n)]) (conj acc [[t] n]))))
          [] tables))

(defn golden
  "Per table: {:table :vars :mismatches n :first [...]}, the table read by
  bnm.states from its file against from the coupled table in `dir`. The
  tables are checked in batches of about `budget` bytes (64 MB), one pass
  over the coupled table each, so a batch's states are all that is held;
  a table larger than the budget is checked in shares of its variables
  (by hash), one pass each."
  ([ms dir] (golden ms dir {}))
  ([ms dir {:keys [budget] :or {budget (* 64 1024 1024)}}]
   (vec (for [[ts sz] (batches (distinct (map :table ms)) budget)
              :let [k (max 1 (long (Math/ceil (/ (double sz) budget))))
                    parts (for [j (range k)
                                :let [var? (when (> k 1) #(= j (mod (hash %) k)))
                                      as (states/read-each ts {:var? var?}) bs (states/read-each ts {:coupled dir :var? var?})]]
                            (mapv (fn [a b] [(count a) (mismatches a b)]) as bs))]
              [i t] (map-indexed vector ts)
              :let [rs (map #(nth % i) parts)
                    mm (vec (mapcat second rs))]]
          {:table t :vars (reduce + (map first rs)) :mismatches (count mm) :first (vec (take 5 mm))}))))
